package com.fitivy.app.sensor.dsp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlidingWindowProcessorTest {

    private lateinit var processor: SlidingWindowProcessor
    private lateinit var testScope: TestScope

    @BeforeEach
    fun setup() {
        processor = SlidingWindowProcessor(windowSize = 10, hopSize = 5) // Kecilkan ukuran untuk test cepat
        testScope = TestScope(StandardTestDispatcher())
    }

    private fun pushSamples(count: Int) {
        for (i in 0 until count) {
            processor.submitSample(
                ProcessedSample(1.0f, 0.0f, 0.0f, i * 20_000_000L)
            )
        }
    }

    @Test
    fun `TEST 1 - Collector baru tidak menerima replay data dari sesi lama (No State Leak)`() = testScope.runTest {
        // SESI 1: Start -> Push data -> Stop
        processor.startProcessing(this)
        pushSamples(20) // Akan menghasilkan 3 window (10, 15, 20)
        advanceUntilIdle()
        processor.stop()

        // SESI 2: Collector baru attach ke flow setelah sesi 1 mati
        val emittedFeatures = mutableListOf<WindowFeatures>()
        val job = launch {
            processor.windowFeatures.toList(emittedFeatures)
        }

        // Jalankan processor lagi tanpa nge-push data baru
        processor.startProcessing(this)
        advanceUntilIdle()

        // Collector HANYA akan berisi 0 elemen karena replay=0
        assertTrue(emittedFeatures.isEmpty(), "Collector tidak boleh menerima stale data dari sesi sebelumnya")

        job.cancel()
    }

    @Test
    fun `TEST 2 - 3 Window pertama memiliki flag isWarmup = true, setelahnya false`() = testScope.runTest {
        val emittedFeatures = mutableListOf<WindowFeatures>()
        
        processor.startProcessing(this)
        
        val job = launch {
            processor.windowFeatures.toList(emittedFeatures)
        }

        // Submit cukup sample untuk menghasilkan 5 window
        // window 1: sample 10
        // window 2: sample 15
        // window 3: sample 20
        // window 4: sample 25
        // window 5: sample 30
        pushSamples(30)
        advanceUntilIdle()

        assertEquals(5, emittedFeatures.size)

        // Verifikasi warmup flags
        assertTrue(emittedFeatures[0].isWarmup, "Window 1 harus warmup")
        assertTrue(emittedFeatures[1].isWarmup, "Window 2 harus warmup")
        assertTrue(emittedFeatures[2].isWarmup, "Window 3 harus warmup")
        
        assertFalse(emittedFeatures[3].isWarmup, "Window 4 BUKAN warmup")
        assertFalse(emittedFeatures[4].isWarmup, "Window 5 BUKAN warmup")

        job.cancel()
    }

    @Test
    fun `TEST 3 - Channel Full Behavior (Konsumen Lambat)`() = testScope.runTest {
        // Jangan start processing (sehingga consumer tidak menguras channel)
        // Submit sample melebihi kapasitas (200)
        var exceptionThrown = false
        try {
            for (i in 0 until 250) {
                processor.submitSample(
                    ProcessedSample(1.0f, 0.0f, 0.0f, i * 20_000_000L)
                )
            }
        } catch (e: Exception) {
            exceptionThrown = true
        }

        // Pastikan tidak ada crash (Channel.trySend aman saat penuh)
        assertFalse(exceptionThrown, "submitSample tidak boleh crash ketika channel penuh")
    }
}
