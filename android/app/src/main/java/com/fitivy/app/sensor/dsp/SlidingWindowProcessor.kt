package com.fitivy.app.sensor.dsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * SlidingWindowProcessor — mengumpulkan sample ke window dan mengekstrak fitur.
 *
 * ARSITEKTUR:
 *   Producer (onSensorChanged) → Channel → Consumer (coroutine) → Feature Flow
 */
class SlidingWindowProcessor(
    private val windowSize: Int = WINDOW_SIZE,
    private val hopSize: Int = HOP_SIZE,
) {
    companion object {
        const val WINDOW_SIZE = 100     // 2 detik @ 50Hz
        const val HOP_SIZE = 50         // 50% overlap = update setiap 1 detik
        const val CHANNEL_CAPACITY = 200 // Buffer 4 detik kalau consumer lambat
    }

    private var sampleChannel = Channel<ProcessedSample>(capacity = CHANNEL_CAPACITY)

    // FIX 1: Hapus replay = 1 agar tidak membocorkan (leak) state antar session
    private val _windowFeatures = MutableSharedFlow<WindowFeatures>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val windowFeatures: SharedFlow<WindowFeatures> = _windowFeatures

    private val buffer = arrayOfNulls<ProcessedSample>(windowSize)
    private var writeIndex = 0
    private var totalSamplesReceived = 0
    private var nextHopAt = windowSize
    
    // Counter untuk menandai warmup windows
    private var warmupWindowsEmitted = 0

    private var processingJob: Job? = null
    private val magnitudeBuffer = DoubleArray(windowSize)

    fun submitSample(sample: ProcessedSample) {
        sampleChannel.trySend(sample)
    }

    fun startProcessing(scope: CoroutineScope) {
        if (processingJob != null) return

        // Reset state sebelum mulai agar bersih dari sesi sebelumnya
        reset()

        processingJob = scope.launch(Dispatchers.Default) {
            for (sample in sampleChannel) {
                buffer[writeIndex % windowSize] = sample
                writeIndex++
                totalSamplesReceived++

                if (totalSamplesReceived >= nextHopAt) {
                    val features = extractFeatures()
                    if (features != null) {
                        _windowFeatures.emit(features)
                    }
                    nextHopAt += hopSize
                }
            }
        }
    }

    fun stop() {
        processingJob?.cancel()
        processingJob = null
        sampleChannel.close()
    }

    fun reset() {
        buffer.fill(null)
        writeIndex = 0
        totalSamplesReceived = 0
        nextHopAt = windowSize
        magnitudeBuffer.fill(0.0)
        warmupWindowsEmitted = 0
        
        // Recreate channel untuk membersihkan sisa queue lama
        if (sampleChannel.isClosedForSend || sampleChannel.isClosedForReceive) {
            sampleChannel = Channel(capacity = CHANNEL_CAPACITY)
        } else {
            // Drain existing
            var drained = sampleChannel.tryReceive().getOrNull()
            while (drained != null) {
                drained = sampleChannel.tryReceive().getOrNull()
            }
        }
    }

    private fun extractFeatures(): WindowFeatures? {
        val startIndex = writeIndex - windowSize
        if (startIndex < 0) return null

        var validCount = 0
        var sumSvm = 0.0
        var firstTimestamp = 0L
        var lastTimestamp = 0L

        for (i in 0 until windowSize) {
            val sample = buffer[(startIndex + i) % windowSize]
            if (sample != null) {
                if (validCount == 0) firstTimestamp = sample.timestampNs
                lastTimestamp = sample.timestampNs

                val mag = sample.magnitude
                magnitudeBuffer[validCount] = mag
                sumSvm += mag
                validCount++
            }
        }

        if (validCount == 0) return null

        val meanSvm = sumSvm / validCount

        var sumSqDiff = 0.0
        var zeroCrossings = 0

        for (i in 0 until validCount) {
            val dev = magnitudeBuffer[i] - meanSvm
            sumSqDiff += dev * dev

            if (i > 0) {
                val prev = magnitudeBuffer[i - 1] - meanSvm
                val curr = magnitudeBuffer[i] - meanSvm
                if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                    zeroCrossings++
                }
            }
        }

        val variance = sumSqDiff / validCount
        val windowDurationSec = (lastTimestamp - firstTimestamp) / 1_000_000_000.0
        val zcrRate = if (windowDurationSec > 0) zeroCrossings / windowDurationSec else 0.0

        var minVal = Double.MAX_VALUE
        var maxVal = Double.MIN_VALUE
        for (i in 0 until validCount) {
            if (magnitudeBuffer[i] < minVal) minVal = magnitudeBuffer[i]
            if (magnitudeBuffer[i] > maxVal) maxVal = magnitudeBuffer[i]
        }
        val peakToPeak = maxVal - minVal

        // FIX 1: Tandai 3 window pertama sebagai warmup
        val isWarmupWindow = warmupWindowsEmitted < 3
        warmupWindowsEmitted++

        return WindowFeatures(
            meanSvm = meanSvm,
            variance = variance,
            zeroCrossingRate = zcrRate,
            peakToPeakAmplitude = peakToPeak,
            sampleCount = validCount,
            windowStartNs = firstTimestamp,
            windowEndNs = lastTimestamp,
            isWarmup = isWarmupWindow
        )
    }
}

data class ProcessedSample(
    val linearX: Float,
    val linearY: Float,
    val linearZ: Float,
    val timestampNs: Long,
) {
    val magnitude: Double
        get() = sqrt((linearX * linearX + linearY * linearY + linearZ * linearZ).toDouble())
}

data class WindowFeatures(
    val meanSvm: Double,
    val variance: Double,
    val zeroCrossingRate: Double,
    val peakToPeakAmplitude: Double,
    val sampleCount: Int,
    val windowStartNs: Long,
    val windowEndNs: Long,
    val isWarmup: Boolean // FIX 1: Flag warmup untuk mencegah salah klasifikasi awal
)
