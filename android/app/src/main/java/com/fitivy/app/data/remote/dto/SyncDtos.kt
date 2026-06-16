package com.fitivy.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// =============================================================================
// SYNC REQUEST DTOs — sent from Android to Laravel
// =============================================================================

/**
 * POST /api/sessions — create or update session on server.
 * Semua field di-kirim agar server punya data lengkap.
 */
data class SessionSyncRequest(
    @SerializedName("id")                val id: String,
    @SerializedName("user_id")           val userId: String,
    @SerializedName("activity_type")     val activityType: String,
    @SerializedName("status")            val status: String,
    @SerializedName("started_at")        val startedAt: String,          // ISO 8601
    @SerializedName("ended_at")          val endedAt: String?,
    @SerializedName("duration_seconds")  val durationSeconds: Long,
    @SerializedName("total_steps")       val totalSteps: Int,
    @SerializedName("distance_meters")   val distanceMeters: Double,
    @SerializedName("calories_burned")   val caloriesBurned: Double,
    @SerializedName("avg_speed_kmh")     val avgSpeedKmh: Double?,
    @SerializedName("max_speed_kmh")     val maxSpeedKmh: Double?,
    @SerializedName("device_model")      val deviceModel: String?,
    @SerializedName("battery_start")     val batteryStart: Int?,
    @SerializedName("battery_end")       val batteryEnd: Int?,
)

/**
 * POST /api/sessions/{id}/step-logs — batch upload step logs.
 */
data class StepLogBatchRequest(
    @SerializedName("step_logs") val stepLogs: List<StepLogSyncItem>,
)

data class StepLogSyncItem(
    @SerializedName("id")                       val id: String,
    @SerializedName("session_id")               val sessionId: String,
    @SerializedName("step_count")               val stepCount: Int,
    @SerializedName("cadence")                  val cadence: Int?,
    @SerializedName("calories_burned")          val caloriesBurned: Double,
    @SerializedName("confidence")               val confidence: Float?,
    @SerializedName("accelerometer_magnitude")  val accelerometerMagnitude: Double?,
    @SerializedName("recorded_at")              val recordedAt: String,
)

/**
 * POST /api/sessions/{id}/gps-batch — batch upload GPS points.
 * Batch karena 1 session bisa punya 1000+ GPS points — kirim sekaligus.
 */
data class GpsBatchRequest(
    @SerializedName("gps_points") val gpsPoints: List<GpsPointSyncItem>,
)

data class GpsPointSyncItem(
    @SerializedName("id")          val id: String,
    @SerializedName("session_id")  val sessionId: String,
    @SerializedName("latitude")    val latitude: Double,
    @SerializedName("longitude")   val longitude: Double,
    @SerializedName("altitude")    val altitude: Double?,
    @SerializedName("accuracy")    val accuracy: Float?,
    @SerializedName("speed")       val speed: Float?,
    @SerializedName("bearing")     val bearing: Float?,
    @SerializedName("sequence")    val sequence: Int,
    @SerializedName("recorded_at") val recordedAt: String,
)

// =============================================================================
// SYNC RESPONSE DTOs — received from Laravel
// =============================================================================

/**
 * Response dari POST /api/sessions.
 * Server mengembalikan updated_at agar client tahu timestamp server.
 */
data class SessionSyncResponse(
    @SerializedName("id")          val id: String,
    @SerializedName("updated_at")  val updatedAt: String,        // Server timestamp — untuk conflict resolution
    @SerializedName("is_new")      val isNew: Boolean,           // true = baru dibuat, false = update existing
)

data class BatchSyncResponse(
    @SerializedName("synced_count") val syncedCount: Int,
    @SerializedName("skipped_ids")  val skippedIds: List<String>, // IDs yang sudah ada di server (duplicate)
)
