package com.fitivy.app.sensor

import android.location.Location
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk

/**
 * Unit Tests untuk AdaptiveKalmanFilter dengan Full 4x4 Covariance Matrix.
 */
class AdaptiveKalmanFilterTest {

    private lateinit var kalmanFilter: AdaptiveKalmanFilter
    private val METERS_PER_DEG_LAT = 111320.0

    @BeforeEach
    fun setup() {
        kalmanFilter = AdaptiveKalmanFilter()
    }

    private fun createMockLocation(
        lat: Double,
        lng: Double,
        elapsedNanos: Long,
        accuracy: Float,
        speed: Float = 0f
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lng
        every { loc.elapsedRealtimeNanos } returns elapsedNanos
        every { loc.hasAccuracy() } returns true
        every { loc.accuracy } returns accuracy
        every { loc.hasSpeed() } returns true
        every { loc.speed } returns speed
        return loc
    }

    @Test
    fun `TEST 1 - Cross-covariance tidak nol setelah predict`() {
        // Inisialisasi awal (Internal P02 awal = 0.0)
        val initialLoc = createMockLocation(0.0, 0.0, 1_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(initialLoc)

        // Verifikasi P02 awal 0
        var state = kalmanFilter.getInternalStateForTesting()!!
        assertEquals(0.0, state.p02, 1e-9, "P02 awal harus 0")

        // Proses titik kedua setelah 1 detik (dt = 1.0)
        // Kita gunakan mock reflection (karena predict adalah operasi private, 
        // namun kita bisa mellihat dampaknya di internal state sesudah proses)
        // Dengan memberikan titik GPS yang sama, Inovasi = 0, sehingga perubahan murni dari F*P*F^T + Q
        val secondLoc = createMockLocation(0.0, 0.0, 2_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(secondLoc)
        
        state = kalmanFilter.getInternalStateForTesting()!!

        // Rumus P_pred[0][2] = P02 + dt * P22 + Q02
        // dt = 1.0, P22 = 1e-4. Sehingga P02 harus lebih besar dari 0
        assertTrue(state.p02 > 0.0, "Cross-covariance (P02) harus positif setelah predict")
        assertTrue(state.p13 > 0.0, "Cross-covariance (P13) harus positif setelah predict")
    }

    @Test
    fun `TEST 2 - Kecepatan terkoreksi ke nol setelah update pada posisi diam`() {
        // Simulasikan state awal lari ke utara (vLat positif)
        // Hal ini tidak bisa disuntik langsung karena enkapsulasi. 
        // Namun kita bisa memancing Kalman Filter dengan memberikan 2 titik bergerak (untuk mendapatkan vLat > 0)
        
        val loc1 = createMockLocation(0.0, 0.0, 1_000_000_000L, 5.0f, 5.0f)
        kalmanFilter.process(loc1)
        
        val loc2 = createMockLocation(0.0001, 0.0, 2_000_000_000L, 5.0f, 5.0f)
        kalmanFilter.process(loc2)
        
        var state = kalmanFilter.getInternalStateForTesting()!!
        val initialVelocity = state.vLat
        assertTrue(initialVelocity > 0.0, "Velocity harus positif setelah bergerak ke Utara")

        // Sekarang berikan 10 pengukuran diam di posisi yang sama (0.0001)
        var currentNanos = 2_000_000_000L
        for (i in 1..10) {
            currentNanos += 1_000_000_000L
            val stationaryLoc = createMockLocation(0.0001, 0.0, currentNanos, 5.0f, 0.0f)
            kalmanFilter.process(stationaryLoc)
        }

        state = kalmanFilter.getInternalStateForTesting()!!
        val finalVelocity = state.vLat
        
        // vLat harus mendekati 0 karena 10 titik terakhir mengukur posisi diam.
        // Jika cross-covariance cacat (P02 = 0), vLat tidak akan turun serendah ini.
        assertTrue(finalVelocity < initialVelocity, "Velocity harus berkurang drastis menuju 0")
        assertEquals(0.0, finalVelocity, 1e-5, "Velocity harus nyaris nol")
    }

    @Test
    fun `TEST 3 - GPS loss recovery menyebabkan P divergence lalu konvergen`() {
        val startLoc = createMockLocation(0.0, 0.0, 1_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(startLoc)

        val initialState = kalmanFilter.getInternalStateForTesting()!!
        val initialP00 = initialState.p00

        // Simulasikan GPS loss 60 detik (sistem mencoba memberikan lokasi setelah 60 detik gap)
        // Note: AdaptiveKalmanFilter memiliki guard if (dt > 30.0) -> reset timestamp tanpa predict.
        // Oleh karena itu, kita akan memberikan 25 detik gap untuk memicu ekstrapolasi predict yang sah.
        val lossLoc = createMockLocation(0.0, 0.0, 26_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(lossLoc)
        
        val afterLossState = kalmanFilter.getInternalStateForTesting()!!
        
        // Proses predict dengan dt=25s akan memicu Q-matrix ditambahkan secara besar besaran, 
        // lalu di-update dengan R (5.0f). Uncertainty setelah diproses tetap lebih besar dari inisial.
        assertTrue(afterLossState.p00 > initialP00, "Uncertainty harus membesar setelah gap sinyal (GPS loss)")

        // Kemudian berikan titik akurat 3m
        val recoveryLoc = createMockLocation(0.0, 0.0, 27_000_000_000L, 3.0f, 0f)
        kalmanFilter.process(recoveryLoc)

        val finalState = kalmanFilter.getInternalStateForTesting()!!
        assertTrue(finalState.p00 < afterLossState.p00, "Uncertainty harus mengecil konvergen setelah update dengan sinyal bagus")
    }

    @Test
    fun `TEST 4 - Konsistensi elapsedRealtimeNanos untuk negative deltaTime`() {
        // Init 
        val loc1 = createMockLocation(0.0, 0.0, 5_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(loc1)

        val stateBefore = kalmanFilter.getInternalStateForTesting()!!

        // Berikan deltaTime negatif (Invalid NTP sync analog)
        val locInvalid = createMockLocation(0.0, 0.0, -1_000_000_000L, 5.0f, 0f)
        val result = kalmanFilter.process(locInvalid)
        
        val stateAfter = kalmanFilter.getInternalStateForTesting()!!

        // State tidak boleh berubah koordinat dan variancenya (karena di-skip predict & update)
        // Tapi timestampnya harus di-reset ke nilai nanos yang baru
        assertEquals(stateBefore.lat, stateAfter.lat, 1e-9)
        assertEquals(stateBefore.p00, stateAfter.p00, 1e-9)
        assertEquals(-1_000_000_000L, stateAfter.timestampNanos)
        
        // Tidak ada crash (NullPointerException/ArithmeticException)
        assertNotNull(result)
    }

    @Test
    fun `TEST 5 - Predict berulang tanpa update memicu P divergence tanpa batas`() {
        val startLoc = createMockLocation(0.0, 0.0, 1_000_000_000L, 5.0f, 0f)
        kalmanFilter.process(startLoc)

        var currentState = kalmanFilter.getInternalStateForTesting()!!
        val initialP00 = currentState.p00

        // Akses metode private predict via reflection
        val predictMethod = AdaptiveKalmanFilter::class.java.getDeclaredMethod(
            "predict",
            KalmanState::class.java,
            Double::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        )
        predictMethod.isAccessible = true

        // Lakukan ekstrapolasi (predict) selama 10 detik tanpa update
        for (i in 1..10) {
            currentState = predictMethod.invoke(kalmanFilter, currentState, 1.0, 5.0f) as KalmanState
        }

        // P00 (Variance posisi Latitude) harus meroket tinggi (Divergence)
        assertTrue(currentState.p00 > initialP00 * 10, "P00 harus divert secara eksponensial/linear jika tidak ada update GPS")
    }
}
