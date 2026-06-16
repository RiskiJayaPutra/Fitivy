package com.fitivy.app.sensor

/**
 * SimpleKalmanFilter — filter noise GPS sederhana 1-dimensi.
 *
 * Kalman filter standar membutuhkan matriks state & measurement, tapi untuk
 * GPS lat/lng kita bisa pakai simplified 1D version yang cukup efektif.
 *
 * KENAPA Kalman filter?
 *   - GPS accuracy bervariasi 3-50m tergantung kondisi (indoor, gedung tinggi, cuaca)
 *   - Raw GPS path tampak "zig-zag" padahal user jalan lurus
 *   - Kalman filter smooth-kan path sambil tetap responsif terhadap perubahan arah
 *
 * Parameter:
 *   - processNoise (Q): seberapa cepat kita expect posisi berubah. Tinggi = lebih responsif.
 *   - measurementNoise (R): seberapa noisy GPS. Tinggi = lebih smooth tapi lambat.
 *   - estimationError (P): uncertainty estimate awal.
 *
 * Tuning untuk fitness tracking (orang jalan/lari):
 *   Q = 3.0  (moderate, orang bisa berbelok cukup cepat)
 *   R = 15.0 (GPS cukup noisy di perkotaan)
 */
class SimpleKalmanFilter(
    private val processNoise: Double = 3.0,
    private val measurementNoise: Double = 15.0,
    initialEstimationError: Double = 100.0,
) {
    private var estimationError: Double = initialEstimationError
    private var lastEstimate: Double = 0.0
    private var kalmanGain: Double = 0.0
    private var isInitialized: Boolean = false

    /**
     * Filter satu measurement baru dan return estimated value.
     *
     * @param measurement Raw GPS coordinate (latitude atau longitude)
     * @param accuracy GPS accuracy dalam meter (opsional, digunakan untuk adjust R)
     * @return Filtered coordinate
     */
    fun filter(measurement: Double, accuracy: Float? = null): Double {
        // Pertama kali — langsung pakai measurement sebagai initial estimate
        if (!isInitialized) {
            lastEstimate = measurement
            isInitialized = true
            return measurement
        }

        // Adjust measurement noise berdasarkan GPS accuracy
        val adjustedR = if (accuracy != null && accuracy > 0) {
            // Accuracy tinggi (angka besar) = lebih noisy = R lebih besar
            measurementNoise * (accuracy / 10.0).coerceIn(0.5, 5.0)
        } else {
            measurementNoise
        }

        // === PREDICT ===
        // Estimation error bertambah seiring waktu (process noise)
        estimationError += processNoise

        // === UPDATE ===
        // Kalman gain: seberapa banyak kita percaya measurement vs prediction
        kalmanGain = estimationError / (estimationError + adjustedR)

        // Update estimate: blend prediction dengan measurement
        lastEstimate += kalmanGain * (measurement - lastEstimate)

        // Update estimation error
        estimationError *= (1.0 - kalmanGain)

        return lastEstimate
    }

    /**
     * Reset filter state — dipanggil saat mulai session baru.
     */
    fun reset() {
        isInitialized = false
        estimationError = 100.0
        lastEstimate = 0.0
        kalmanGain = 0.0
    }

    /**
     * Get current Kalman gain — useful untuk debugging.
     * Gain mendekati 1.0 = lebih percaya measurement.
     * Gain mendekati 0.0 = lebih percaya prediction.
     */
    fun getCurrentGain(): Double = kalmanGain
}
