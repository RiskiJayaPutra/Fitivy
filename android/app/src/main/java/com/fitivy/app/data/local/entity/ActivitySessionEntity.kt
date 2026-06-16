package com.fitivy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity untuk activity_sessions — mirror dari Laravel migration.
 *
 * OFFLINE-FIRST DESIGN:
 *   - Session SELALU dibuat lokal dulu (Room), baru di-sync ke server
 *   - sync_status track state sync: PENDING → SYNCING → SYNCED / FAILED
 *   - sync_attempts track berapa kali sudah coba sync (max 5, exponential backoff)
 *   - Jika user offline saat tracking, semua data tersimpan aman di Room
 *   - SyncWorker (WorkManager) otomatis sync saat koneksi kembali
 *
 * CONFLICT RESOLUTION:
 *   - server_updated_at diisi dari response server setelah sync
 *   - Jika session_id sudah ada di server (duplikat), server timestamp menang
 *   - Client merge: ambil max(local, server) untuk metrics
 */
@Entity(
    tableName = "activity_sessions",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["status"]),
        Index(value = ["sync_status"]),                    // Query: "semua session PENDING sync"
        Index(value = ["user_id", "started_at"]),
        Index(value = ["user_id", "status"]),
        Index(value = ["sync_status", "sync_attempts"]),   // SyncWorker: PENDING/FAILED + attempts < max
    ]
)
data class ActivitySessionEntity(
    @PrimaryKey
    val id: String,                                        // UUID — dibuat di client, dikirim ke server apa adanya

    @ColumnInfo(name = "user_id")
    val userId: String,                                    // FK ke user (dari local auth cache)

    // === SESSION METADATA ===
    @ColumnInfo(name = "activity_type")
    val activityType: String = "walking",                  // walking, running, cycling

    @ColumnInfo(name = "status")
    val status: String = SessionStatus.ACTIVE,             // active, paused, completed, abandoned

    // === TIMING ===
    @ColumnInfo(name = "started_at")
    val startedAt: Long,                                   // Unix timestamp (ms) — waktu mulai tracking

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,                             // Null saat masih active

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long = 0,                         // Total durasi AKTIF (exclude pause time)

    @ColumnInfo(name = "paused_at")
    val pausedAt: Long? = null,                            // Timestamp saat terakhir di-pause (null jika tidak pause)

    @ColumnInfo(name = "total_paused_ms")
    val totalPausedMs: Long = 0,                           // Akumulasi total waktu pause (ms)

    // === SUMMARY METRICS ===
    @ColumnInfo(name = "total_steps")
    val totalSteps: Int = 0,

    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double = 0.0,

    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Double = 0.0,

    @ColumnInfo(name = "avg_speed_kmh")
    val avgSpeedKmh: Double? = null,

    @ColumnInfo(name = "max_speed_kmh")
    val maxSpeedKmh: Double? = null,

    // === DEVICE INFO ===
    @ColumnInfo(name = "device_model")
    val deviceModel: String? = null,

    @ColumnInfo(name = "battery_start")
    val batteryStart: Int? = null,                         // Level baterai saat mulai (%)

    @ColumnInfo(name = "battery_end")
    val batteryEnd: Int? = null,                           // Level baterai saat selesai (%)

    // === SYNC STATE ===
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SyncStatus.PENDING,           // PENDING, SYNCING, SYNCED, FAILED

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int = 0,                             // Berapa kali sudah coba sync

    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String? = null,                     // Error message dari sync terakhir yang gagal

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null,                            // Kapan terakhir sync sukses

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: Long? = null,                     // Timestamp dari server — untuk conflict resolution

    // === TIMESTAMPS ===
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Session status constants — match dengan Laravel enum.
 */
object SessionStatus {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
    const val ABANDONED = "abandoned"
}

/**
 * Sync status constants — lifecycle state machine:
 *
 *   PENDING ──→ SYNCING ──→ SYNCED
 *                  │
 *                  └──→ FAILED ──→ PENDING (retry)
 *                          │
 *                          └──→ ABANDONED (max retries exceeded)
 */
object SyncStatus {
    const val PENDING = "pending"       // Belum pernah di-sync (baru dibuat atau ada perubahan)
    const val SYNCING = "syncing"       // Sedang dalam proses sync
    const val SYNCED = "synced"         // Sudah berhasil sync
    const val FAILED = "failed"         // Sync gagal (akan di-retry)
}
