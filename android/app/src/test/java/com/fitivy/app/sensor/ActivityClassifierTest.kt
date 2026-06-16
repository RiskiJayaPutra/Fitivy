package com.fitivy.app.sensor

import com.fitivy.app.sensor.dsp.WindowFeatures
import com.fitivy.app.sensor.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests untuk ActivityClassifier versi terbaru (Fase 1 DSP).
 *
 * Test strategy:
 *   1. classifyFromFeatures() — test decision tree dengan known inputs
 *   2. Edge cases: vehicle protection, warmup state
 */
class ActivityClassifierTest {

    @Before
    fun setUp() {
        // Reset state dengan mensimulasikan sesi diam
        val resetFeatures = WindowFeatures(
            meanSvm = 0.0,
            variance = 0.0,
            zeroCrossingRate = 0.0,
            peakToPeakAmplitude = 0.0,
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 1_000L,
            isWarmup = false
        )
        // Panggil 2x untuk bypass hysteresis
        ActivityClassifier.classifyFromFeatures(resetFeatures)
        ActivityClassifier.classifyFromFeatures(resetFeatures)
    }

    private fun classifyBypassHysteresis(features: WindowFeatures): ActivityType {
        ActivityClassifier.classifyFromFeatures(features)
        return ActivityClassifier.classifyFromFeatures(features)
    }

    @Test
    fun `STATIONARY - very low variance device diam di meja`() {
        val features = WindowFeatures(
            meanSvm = 0.1,
            variance = 0.05,
            zeroCrossingRate = 0.0,
            peakToPeakAmplitude = 0.2, // < 0.5
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 2_000_000_000L,
            isWarmup = false
        )
        assertEquals(ActivityType.STATIONARY, classifyBypassHysteresis(features))
    }

    @Test
    fun `STATIONARY - vehicle protection (high mag, low freq)`() {
        val features = WindowFeatures(
            meanSvm = 1.2,
            variance = 0.8,
            zeroCrossingRate = 0.4, // estimatedFreq = 0.2 < 0.8
            peakToPeakAmplitude = 1.0, // > 0.5
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 2_000_000_000L,
            isWarmup = false
        )
        assertEquals(ActivityType.STATIONARY, classifyBypassHysteresis(features))
    }

    @Test
    fun `WALKING - typical walking pattern 5kmh`() {
        val features = WindowFeatures(
            meanSvm = 1.5, // < 2.5
            variance = 0.6,
            zeroCrossingRate = 3.6, // estimatedFreq = 1.8 Hz
            peakToPeakAmplitude = 2.0, // > 0.5
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 2_000_000_000L,
            isWarmup = false
        )
        assertEquals(ActivityType.WALKING, classifyBypassHysteresis(features))
    }

    @Test
    fun `RUNNING - typical jogging 8kmh`() {
        val features = WindowFeatures(
            meanSvm = 3.5, // > 2.5
            variance = 2.0,
            zeroCrossingRate = 5.0, // estimatedFreq = 2.5 Hz
            peakToPeakAmplitude = 5.0, // > 0.5
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 2_000_000_000L,
            isWarmup = false
        )
        assertEquals(ActivityType.RUNNING, classifyBypassHysteresis(features))
    }
    
    @Test
    fun `WARMUP - returns last activity without classification`() {
        val features = WindowFeatures(
            meanSvm = 10.0,
            variance = 10.0,
            zeroCrossingRate = 10.0,
            peakToPeakAmplitude = 10.0,
            sampleCount = 100,
            windowStartNs = 0L,
            windowEndNs = 2_000_000_000L,
            isWarmup = true // WARMUP!
        )
        
        // Pastikan current state STATIONARY
        assertEquals(ActivityType.STATIONARY, ActivityClassifier.getCurrentActivity())
        
        // Panggil dengan fitur extrim tapi status warmup
        val result = ActivityClassifier.classifyFromFeatures(features)
        
        // Seharusnya tetap STATIONARY karena diabaikan
        assertEquals(ActivityType.STATIONARY, result)
    }
}
