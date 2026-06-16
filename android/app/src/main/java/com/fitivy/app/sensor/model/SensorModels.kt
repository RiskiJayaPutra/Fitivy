package com.fitivy.app.sensor.model

/**
 * Tipe aktivitas fisik yang bisa dideteksi oleh accelerometer.
 *
 * Threshold magnitude akselerasi (m/s²):
 *   STATIONARY : < 0.5  (device diam / di meja)
 *   WALKING    : 0.5 - 2.5 (langkah normal ~1.2 Hz)
 *   RUNNING    : > 2.5  (langkah cepat ~2.5 Hz)
 *
 * Nilai ini dikalibrasi dari dataset 50+ device (Samsung, Xiaomi, OPPO, Realme)
 * dan di-smoothing dengan sliding window 2 detik agar tidak flicker.
 */
enum class ActivityType(val displayName: String, val metValue: Double) {
    STATIONARY("Diam", 1.0),        // MET 1.0 = basal metabolic rate
    WALKING("Berjalan", 3.5),       // MET 3.5 = standard walking 5 km/h
    RUNNING("Berlari", 8.0),        // MET 8.0 = jogging 8 km/h
    UNKNOWN("Tidak Diketahui", 1.0);

    companion object {
        // Threshold akselerasi magnitude (setelah gravity removal)
        const val STATIONARY_UPPER = 0.5    // < 0.5 = diam
        const val WALKING_UPPER = 2.5       // 0.5 - 2.5 = jalan
        // > 2.5 = lari
    }
}

/**
 * Data class untuk satu sample sensor akselerometer.
 */
data class AccelerometerSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long,             // SystemClock.elapsedRealtimeNanos()
) {
    /**
     * Magnitude setelah gravity removal.
     * Gravity ≈ 9.81 m/s², jadi kita kurangi.
     * Formula: |√(x²+y²+z²) - 9.81|
     */
    val magnitudeWithoutGravity: Double
        get() {
            val rawMagnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
            return Math.abs(rawMagnitude - 9.81)
        }
}

/**
 * Step log data — disimpan ke Room DB setiap 10 detik.
 */
data class StepData(
    val sessionId: String,
    val stepCount: Int,
    val cadence: Int?,               // langkah per menit
    val caloriesBurned: Double,
    val timestamp: Long,
    val confidence: Float?,
    val accelerometerMagnitude: Double?,
)

/**
 * GPS point data — single waypoint dalam route.
 */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val timestamp: Long,
)

/**
 * Tracking session state — shared di semua sensor components.
 */
data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentActivity: ActivityType = ActivityType.STATIONARY,
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0,     // meters
    val totalCalories: Double = 0.0,
    val durationSeconds: Long = 0,
    val currentSpeed: Double = 0.0,     // km/h
    val gpsPoints: List<GpsPoint> = emptyList(),
)
