package com.fitivy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity untuk step log — disimpan setiap 10 detik selama tracking.
 *
 * Index pada session_id + recorded_at karena query utama:
 * "SELECT * FROM step_logs WHERE session_id = ? ORDER BY recorded_at"
 */
@Entity(
    tableName = "step_logs",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "recorded_at"]),
        Index(value = ["is_synced"]),  // Untuk batch sync ke server
    ]
)
data class StepLogEntity(
    @PrimaryKey
    val id: String,                              // UUID

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "step_count")
    val stepCount: Int,                          // Langkah dalam interval ini

    @ColumnInfo(name = "cadence")
    val cadence: Int?,                           // Langkah per menit

    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Double,

    @ColumnInfo(name = "confidence")
    val confidence: Float?,                      // Confidence level sensor

    @ColumnInfo(name = "accelerometer_magnitude")
    val accelerometerMagnitude: Double?,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,                        // Unix timestamp (ms)

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,               // Flag: sudah sync ke server?

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Room Entity untuk GPS route points.
 */
@Entity(
    tableName = "gps_routes",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "sequence"]),
        Index(value = ["is_synced"]),
    ]
)
data class GpsRouteEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "altitude")
    val altitude: Double?,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float?,

    @ColumnInfo(name = "speed")
    val speed: Float?,

    @ColumnInfo(name = "bearing")
    val bearing: Float?,

    @ColumnInfo(name = "sequence")
    val sequence: Int,                           // Urutan dalam polyline

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Room Entity untuk menyimpan referensi step counter baseline.
 * Dibutuhkan karena TYPE_STEP_COUNTER reset ke 0 setelah device reboot.
 */
@Entity(tableName = "step_counter_baseline")
data class StepCounterBaselineEntity(
    @PrimaryKey
    val id: Int = 1,                             // Singleton row

    @ColumnInfo(name = "sensor_value_at_session_start")
    val sensorValueAtSessionStart: Int,          // Nilai sensor saat session dimulai

    @ColumnInfo(name = "last_known_sensor_value")
    val lastKnownSensorValue: Int,               // Nilai sensor terakhir (detect reboot)

    @ColumnInfo(name = "accumulated_steps")
    val accumulatedSteps: Int,                   // Total steps yang sudah dihitung

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
