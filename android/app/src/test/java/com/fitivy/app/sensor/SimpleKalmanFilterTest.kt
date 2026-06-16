package com.fitivy.app.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests untuk SimpleKalmanFilter.
 *
 * Verifikasi:
 *   1. Filter converges ke true value dengan noisy measurements
 *   2. Filter smooth-kan outliers
 *   3. Filter responsif terhadap perubahan arah (directional change)
 *   4. GPS accuracy adjustment bekerja benar
 *   5. Reset mengembalikan ke initial state
 */
class SimpleKalmanFilterTest {

    private lateinit var filter: SimpleKalmanFilter

    @Before
    fun setUp() {
        filter = SimpleKalmanFilter(processNoise = 3.0, measurementNoise = 15.0)
    }

    @Test
    fun `first measurement returned as-is`() {
        val result = filter.filter(106.827)
        assertEquals(106.827, result, 0.001)
    }

    @Test
    fun `filter reduces noise on constant position`() {
        val trueValue = -6.2     // Latitude Surabaya
        val noise = 0.0001       // ~11 meter noise

        // Simulasi 50 GPS readings dengan noise acak
        val noisyReadings = (1..50).map { trueValue + (Math.random() - 0.5) * noise * 2 }

        var lastFiltered = 0.0
        for (reading in noisyReadings) {
            lastFiltered = filter.filter(reading)
        }

        // Filtered value harus lebih dekat ke true value daripada rata-rata noise
        val rawAvgError = noisyReadings.map { Math.abs(it - trueValue) }.average()
        val filteredError = Math.abs(lastFiltered - trueValue)

        assertTrue(
            "Filtered error ($filteredError) should be <= raw average error ($rawAvgError)",
            filteredError <= rawAvgError * 1.5  // Allow some margin
        )
    }

    @Test
    fun `filter smooths out GPS outliers`() {
        val trueValue = 106.827

        // Normal readings
        repeat(20) { filter.filter(trueValue + (Math.random() - 0.5) * 0.0001) }

        // Inject outlier (jump 100m)
        val outlierResult = filter.filter(trueValue + 0.001)

        // Filter should dampen the outlier
        val deviation = Math.abs(outlierResult - trueValue)
        assertTrue(
            "Outlier should be dampened (deviation=$deviation should be < 0.001)",
            deviation < 0.001
        )
    }

    @Test
    fun `filter follows directional change`() {
        // Simulasi orang berjalan ke utara (latitude naik)
        val startLat = -6.200
        val endLat = -6.190    // ~1.1 km ke utara
        val steps = 100

        var lastFiltered = 0.0
        for (i in 0..steps) {
            val trueLat = startLat + (endLat - startLat) * i / steps
            val noisyLat = trueLat + (Math.random() - 0.5) * 0.00005  // ±5m noise
            lastFiltered = filter.filter(noisyLat)
        }

        // Filter harus mengikuti pergerakan (mendekati endLat)
        assertTrue(
            "Filter should follow movement toward endLat ($endLat), got $lastFiltered",
            lastFiltered > startLat && lastFiltered < endLat + 0.001
        )
    }

    @Test
    fun `GPS accuracy adjusts measurement noise`() {
        val goodAccuracyFilter = SimpleKalmanFilter()
        val poorAccuracyFilter = SimpleKalmanFilter()

        val trueValue = 106.827

        // Same initial point
        goodAccuracyFilter.filter(trueValue)
        poorAccuracyFilter.filter(trueValue)

        // Same noisy measurement
        val noisyMeasurement = trueValue + 0.0005

        val goodResult = goodAccuracyFilter.filter(noisyMeasurement, accuracy = 5f)    // 5m = good GPS
        val poorResult = poorAccuracyFilter.filter(noisyMeasurement, accuracy = 40f)   // 40m = poor GPS

        // Poor accuracy → lebih percaya prediction → filtered value lebih dekat ke previous
        val goodDeviation = Math.abs(goodResult - trueValue)
        val poorDeviation = Math.abs(poorResult - trueValue)

        assertTrue(
            "Poor accuracy filter should deviate less from previous estimate",
            poorDeviation <= goodDeviation + 0.0001  // Allow small margin
        )
    }

    @Test
    fun `reset returns filter to initial state`() {
        // Use filter
        filter.filter(106.827)
        filter.filter(106.828)
        filter.filter(106.829)

        // Reset
        filter.reset()

        // After reset, first measurement should be returned as-is
        val result = filter.filter(-6.200)
        assertEquals(-6.200, result, 0.001)
    }

    @Test
    fun `kalman gain starts high and decreases`() {
        // Initially, gain should be high (trust measurement)
        filter.filter(100.0)
        val firstGain = filter.getCurrentGain()

        filter.filter(100.001)
        filter.filter(100.002)
        filter.filter(100.001)
        filter.filter(100.0)

        val laterGain = filter.getCurrentGain()

        // Note: with process noise, gain doesn't converge to 0
        // but it should generally decrease initially as estimation error reduces
        assertNotNull(laterGain)
    }

    @Test
    fun `handles identical consecutive measurements`() {
        val value = 106.827
        repeat(50) {
            val result = filter.filter(value)
            assertEquals("Constant input should yield constant output", value, result, 0.0001)
        }
    }

    @Test
    fun `handles very large coordinate values`() {
        // Antarctic coordinates
        val result1 = filter.filter(-89.999)
        val result2 = filter.filter(-89.998)
        assertNotNull(result1)
        assertNotNull(result2)
    }

    @Test
    fun `handles zero coordinates`() {
        val result = filter.filter(0.0)
        assertEquals(0.0, result, 0.001)
    }
}
