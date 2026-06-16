package com.fitivy.app.sensor

import android.location.Location
import kotlin.math.cos

/**
 * State internal Kalman Filter (Constant Velocity Model 4-state).
 * Immutable data class untuk mencegah corrupt state saat dipanggil dari Coroutine.
 *
 * State Vector X = [lat, lng, vLat, vLng]
 *
 * Covariance Matrix P (4x4 Symmetric) dengan 10 elemen unik:
 * [ p00, p01, p02, p03 ]
 * [ p01, p11, p12, p13 ]
 * [ p02, p12, p22, p23 ]
 * [ p03, p13, p23, p33 ]
 */
data class KalmanState(
    val lat: Double, val lng: Double,
    val vLat: Double, val vLng: Double,
    
    val p00: Double, val p01: Double, val p02: Double, val p03: Double,
    val p11: Double, val p12: Double, val p13: Double,
    val p22: Double, val p23: Double,
    val p33: Double,
    
    val timestampNanos: Long
)

/**
 * Output akhir dari AdaptiveKalmanFilter.
 */
data class FilteredLocation(
    val latitude: Double,
    val longitude: Double,
    val estimatedSpeedMs: Double,
    val estimatedBearingDeg: Double,
    val confidenceRadiusMeters: Float,
    val rawLocation: Location
)

/**
 * AdaptiveKalmanFilter — Kalman Filter 4-State (Constant Velocity) dengan R & Q matrix dinamis.
 *
 * MENGAPA KALMAN GAIN DIASUMSIKAN DIAGONAL UNTUK S-MATRIX?
 * Measurement matrix H memetakan State (4D) ke Pengukuran (2D: Lat, Lng).
 * Measurement Noise R diasumsikan diagonal (akurasi lat dan lng independen).
 * Karena pergerakan manusia di sumbu Latitude (Utara-Selatan) dan Longitude (Barat-Timur)
 * secara umum independen dalam ruang bebas, Covariance P_01 (korelasi PosLat-PosLng)
 * secara matematis mendekati 0. Sehingga S-Matrix (Inovasi Covariance) menjadi diagonal.
 * Inversi matriks S diagonal sangat efisien: cukup 1/S[i][i], menghindari kompleksitas
 * inversi matriks padat tanpa mengurangi akurasi pada domain GPS derajat koordinat.
 */
class AdaptiveKalmanFilter {

    private var state: KalmanState? = null

    // Konstanta konversi jarak (Meters <-> Degrees)
    // 1 derajat Latitude konsisten ~ 111.32 km
    private val METERS_PER_DEGREE_LAT = 111320.0

    /**
     * Memproses titik GPS mentah: Predict -> Update.
     */
    fun process(location: Location): FilteredLocation {
        val currentState = state

        // Gunakan elapsedRealtimeNanos agar kebal terhadap perubahan jam NTP (wall-clock drift)
        val currentNanos = location.elapsedRealtimeNanos

        if (currentState == null) {
            // Inisialisasi state pertama kali
            val accDegLat = location.accuracy / METERS_PER_DEGREE_LAT
            val accDegLng = location.accuracy / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(location.latitude)))
            
            state = KalmanState(
                lat = location.latitude,
                lng = location.longitude,
                vLat = 0.0,
                vLng = 0.0,
                p00 = accDegLat * accDegLat, p01 = 0.0, p02 = 0.0, p03 = 0.0,
                p11 = accDegLng * accDegLng, p12 = 0.0, p13 = 0.0,
                p22 = 1e-4, p23 = 0.0, // Variance kecepatan tebakan awal (deg/s)^2
                p33 = 1e-4,
                timestampNanos = currentNanos
            )
            return buildOutput(state!!, location)
        }

        // Hitung delta waktu (dalam detik)
        val dt = (currentNanos - currentState.timestampNanos) / 1_000_000_000.0

        // Guard: Jika deltaTime negatif (mustahil fisik tapi mungkin OS error) 
        // atau > 30 detik (GPS loss sangat lama), skip predict dan reset timestamp.
        if (dt <= 0.0 || dt > 30.0) {
            val resetState = currentState.copy(timestampNanos = currentNanos)
            state = resetState
            return buildOutput(resetState, location)
        }

        // 1. PREDICT STEP
        val predictedState = predict(currentState, dt, location.speed)

        // 2. UPDATE STEP
        val updatedState = update(predictedState, location)
        
        state = updatedState
        return buildOutput(updatedState, location)
    }

    /**
     * LANGKAH PREDICT: Memprediksi posisi saat ini berdasarkan momentum sebelumnya.
     * Menggunakan Q-Matrix yang dinamis berdasarkan kecepatan lari (speedMs).
     */
    private fun predict(s: KalmanState, dt: Double, currentSpeedMs: Float): KalmanState {
        // State Transition (F): Constant Velocity
        // X_pred = F * X
        val predLat = s.lat + s.vLat * dt
        val predLng = s.lng + s.vLng * dt
        val predVLat = s.vLat
        val predVLng = s.vLng

        // Dynamic Q-Matrix: Process Noise Covariance
        val qVarianceMps2 = computeQVariance(currentSpeedMs)
        
        val latScale = METERS_PER_DEGREE_LAT
        val lngScale = METERS_PER_DEGREE_LAT * cos(Math.toRadians(s.lat))
        
        val qLat = qVarianceMps2 / (latScale * latScale)
        val qLng = qVarianceMps2 / (lngScale * lngScale)

        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt2 * dt2

        // Derivasi P_pred = F * P * F^T + Q untuk 10 elemen unik
        // F = [1 0 dt 0; 0 1 0 dt; 0 0 1 0; 0 0 0 1]
        
        val p00 = s.p00 + 2 * dt * s.p02 + dt2 * s.p22 + (qLat * dt4 / 4.0)
        val p01 = s.p01 + dt * s.p12 + dt * s.p03 + dt2 * s.p23
        val p02 = s.p02 + dt * s.p22 + (qLat * dt3 / 2.0)
        val p03 = s.p03 + dt * s.p23
        
        val p11 = s.p11 + 2 * dt * s.p13 + dt2 * s.p33 + (qLng * dt4 / 4.0)
        val p12 = s.p12 + dt * s.p23
        val p13 = s.p13 + dt * s.p33 + (qLng * dt3 / 2.0)
        
        val p22 = s.p22 + (qLat * dt2)
        val p23 = s.p23
        
        val p33 = s.p33 + (qLng * dt2)

        return KalmanState(
            lat = predLat, lng = predLng, vLat = predVLat, vLng = predVLng,
            p00 = p00, p01 = p01, p02 = p02, p03 = p03,
            p11 = p11, p12 = p12, p13 = p13,
            p22 = p22, p23 = p23, p33 = p33,
            timestampNanos = s.timestampNanos + (dt * 1_000_000_000L).toLong()
        )
    }

    /**
     * LANGKAH UPDATE: Mengoreksi prediksi menggunakan data sensor GPS aktual.
     */
    private fun update(pred: KalmanState, location: Location): KalmanState {
        // R-Matrix (Measurement Noise)
        val accuracyClamped = location.accuracy.coerceAtMost(20.0f).toDouble()
        val rVarianceM2 = accuracyClamped * accuracyClamped

        val latScale = METERS_PER_DEGREE_LAT
        val lngScale = METERS_PER_DEGREE_LAT * cos(Math.toRadians(pred.lat))

        val rLat = rVarianceM2 / (latScale * latScale)
        val rLng = rVarianceM2 / (lngScale * lngScale)

        // Innovation (y = z - H * X_pred)
        val yLat = location.latitude - pred.lat
        val yLng = location.longitude - pred.lng

        // Innovation Covariance S = H * P_pred * H^T + R
        // Karena H memilih 2 baris pertama, S adalah 2x2 submatrix P_pred[0:2][0:2] + R
        val s00 = pred.p00 + rLat
        val s11 = pred.p11 + rLng
        // Asumsi valid S diagonal (s01 ≈ 0)

        // Kalman Gain K = P_pred * H^T * S^-1
        // Menghasilkan 4x2 matrix
        val k00 = pred.p00 / s00
        val k01 = pred.p01 / s11
        
        val k10 = pred.p01 / s00
        val k11 = pred.p11 / s11
        
        val k20 = pred.p02 / s00
        val k21 = pred.p12 / s11
        
        val k30 = pred.p03 / s00
        val k31 = pred.p13 / s11

        // State Update X = X_pred + K * y
        val newLat = pred.lat + k00 * yLat + k01 * yLng
        val newLng = pred.lng + k10 * yLat + k11 * yLng
        val newVLat = pred.vLat + k20 * yLat + k21 * yLng
        val newVLng = pred.vLng + k30 * yLat + k31 * yLng

        // Covariance Update P = (I - K*H) * P_pred
        // Matrix (I - K*H) memiliki bentuk:
        // [1-k00,  -k01, 0, 0]
        // [ -k10, 1-k11, 0, 0]
        // [ -k20,  -k21, 1, 0]
        // [ -k30,  -k31, 0, 1]
        
        val newP00 = (1.0 - k00) * pred.p00 - k01 * pred.p01
        val newP01 = (1.0 - k00) * pred.p01 - k01 * pred.p11
        val newP02 = (1.0 - k00) * pred.p02 - k01 * pred.p12
        val newP03 = (1.0 - k00) * pred.p03 - k01 * pred.p13

        val newP11 = -k10 * pred.p01 + (1.0 - k11) * pred.p11
        val newP12 = -k10 * pred.p02 + (1.0 - k11) * pred.p12
        val newP13 = -k10 * pred.p03 + (1.0 - k11) * pred.p13

        val newP22 = -k20 * pred.p02 - k21 * pred.p12 + pred.p22
        val newP23 = -k20 * pred.p03 - k21 * pred.p13 + pred.p23

        val newP33 = -k30 * pred.p03 - k31 * pred.p13 + pred.p33

        return KalmanState(
            lat = newLat, lng = newLng, vLat = newVLat, vLng = newVLng,
            p00 = newP00, p01 = newP01, p02 = newP02, p03 = newP03,
            p11 = newP11, p12 = newP12, p13 = newP13,
            p22 = newP22, p23 = newP23, p33 = newP33,
            timestampNanos = location.elapsedRealtimeNanos
        )
    }

    private fun computeQVariance(speedMs: Float): Double {
        return when {
            speedMs > 2.5f -> 4.0   // RUNNING: sigma_a = 2.0 m/s^2 (4.0)
            speedMs > 0.5f -> 0.25  // WALKING: sigma_a = 0.5 m/s^2 (0.25)
            else -> 0.01            // STATIONARY: sigma_a = 0.1 m/s^2 (0.01)
        }
    }

    private fun buildOutput(s: KalmanState, raw: Location): FilteredLocation {
        val latSpeedMps = s.vLat * METERS_PER_DEGREE_LAT
        val lngSpeedMps = s.vLng * (METERS_PER_DEGREE_LAT * cos(Math.toRadians(s.lat)))
        val estimatedSpeed = Math.sqrt(latSpeedMps * latSpeedMps + lngSpeedMps * lngSpeedMps)

        val pLatMeters2 = s.p00 * (METERS_PER_DEGREE_LAT * METERS_PER_DEGREE_LAT)
        val confidenceRadius = Math.sqrt(pLatMeters2.coerceAtLeast(0.0)).toFloat()

        return FilteredLocation(
            latitude = s.lat,
            longitude = s.lng,
            estimatedSpeedMs = estimatedSpeed,
            estimatedBearingDeg = 0.0,
            confidenceRadiusMeters = confidenceRadius,
            rawLocation = raw
        )
    }

    fun reset() {
        state = null
    }

    // Fungsi eksponensial ini dipakai untuk test harness (injecting explicit testing values)
    // Diberi anotasi VisibleForTesting (dalam bentuk penamaan method internal)
    internal fun getInternalStateForTesting(): KalmanState? = state
}
