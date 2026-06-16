package com.fitivy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk activity_sessions.
 *
 * Query design decisions:
 *   - Flow<> untuk real-time UI observation (dashboard, tracking screen)
 *   - suspend untuk one-shot operations (sync, session lifecycle)
 *   - Composite queries untuk SyncWorker (status + attempts filter)
 */
@Dao
interface ActivitySessionDao {

    // =========================================================================
    // INSERT / UPDATE
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ActivitySessionEntity)

    @Update
    suspend fun update(session: ActivitySessionEntity)

    /**
     * Atomic update hanya kolom yang berubah saat tracking (tiap 5 detik).
     * Lebih efisien daripada full @Update karena tidak rewrite semua kolom.
     */
    @Query("""
        UPDATE activity_sessions SET 
            total_steps = :totalSteps,
            distance_meters = :distanceMeters,
            calories_burned = :caloriesBurned,
            duration_seconds = :durationSeconds,
            avg_speed_kmh = :avgSpeedKmh,
            max_speed_kmh = :maxSpeedKmh,
            updated_at = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun updateMetrics(
        sessionId: String,
        totalSteps: Int,
        distanceMeters: Double,
        caloriesBurned: Double,
        durationSeconds: Long,
        avgSpeedKmh: Double?,
        maxSpeedKmh: Double?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Update status session (active → paused → completed).
     */
    @Query("""
        UPDATE activity_sessions SET 
            status = :status,
            paused_at = :pausedAt,
            total_paused_ms = :totalPausedMs,
            updated_at = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun updateStatus(
        sessionId: String,
        status: String,
        pausedAt: Long?,
        totalPausedMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Finalize session saat tracking selesai.
     */
    @Query("""
        UPDATE activity_sessions SET
            status = :status,
            ended_at = :endedAt,
            duration_seconds = :durationSeconds,
            total_steps = :totalSteps,
            distance_meters = :distanceMeters,
            calories_burned = :caloriesBurned,
            avg_speed_kmh = :avgSpeedKmh,
            max_speed_kmh = :maxSpeedKmh,
            battery_end = :batteryEnd,
            sync_status = :syncStatus,
            updated_at = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun finalizeSession(
        sessionId: String,
        status: String,
        endedAt: Long,
        durationSeconds: Long,
        totalSteps: Int,
        distanceMeters: Double,
        caloriesBurned: Double,
        avgSpeedKmh: Double?,
        maxSpeedKmh: Double?,
        batteryEnd: Int?,
        syncStatus: String = SyncStatus.PENDING,
        updatedAt: Long = System.currentTimeMillis(),
    )

    // =========================================================================
    // READ — Observation (Flow)
    // =========================================================================

    /** Real-time observation sesi saat ini (untuk tracking screen). */
    @Query("SELECT * FROM activity_sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<ActivitySessionEntity?>

    /** Semua sesi user, terbaru dulu (untuk history screen). */
    @Query("SELECT * FROM activity_sessions WHERE user_id = :userId ORDER BY started_at DESC")
    fun observeAllByUser(userId: String): Flow<List<ActivitySessionEntity>>

    /** Sesi hari ini (untuk dashboard daily summary). */
    @Query("""
        SELECT * FROM activity_sessions 
        WHERE user_id = :userId AND started_at >= :todayStartMs
        ORDER BY started_at DESC
    """)
    fun observeTodaySessions(userId: String, todayStartMs: Long): Flow<List<ActivitySessionEntity>>

    // =========================================================================
    // READ — One-shot (suspend)
    // =========================================================================

    @Query("SELECT * FROM activity_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): ActivitySessionEntity?

    /** Session yang sedang active (harusnya max 1). */
    @Query("SELECT * FROM activity_sessions WHERE status = 'active' OR status = 'paused' LIMIT 1")
    suspend fun getActiveSession(): ActivitySessionEntity?

    /** Cek apakah ada session yang sedang berjalan. */
    @Query("SELECT COUNT(*) FROM activity_sessions WHERE status = 'active' OR status = 'paused'")
    suspend fun hasActiveSession(): Int

    // =========================================================================
    // SYNC QUERIES — Dipakai oleh SyncWorker
    // =========================================================================

    /**
     * Ambil sessions yang perlu di-sync:
     *   - Status: PENDING atau FAILED
     *   - Attempts < maxAttempts (default 5)
     *   - Session sudah completed/abandoned (jangan sync yang masih active)
     * Ordered by oldest first (FIFO sync).
     */
    @Query("""
        SELECT * FROM activity_sessions 
        WHERE sync_status IN ('pending', 'failed') 
          AND sync_attempts < :maxAttempts
          AND status IN ('completed', 'abandoned')
        ORDER BY started_at ASC
        LIMIT :limit
    """)
    suspend fun getSessionsToSync(maxAttempts: Int = 5, limit: Int = 20): List<ActivitySessionEntity>

    /** Update sync status saat mulai sync. */
    @Query("UPDATE activity_sessions SET sync_status = 'syncing', updated_at = :now WHERE id = :sessionId")
    suspend fun markAsSyncing(sessionId: String, now: Long = System.currentTimeMillis())

    /** Update sync status setelah sync sukses. */
    @Query("""
        UPDATE activity_sessions SET 
            sync_status = 'synced', 
            synced_at = :syncedAt, 
            server_updated_at = :serverUpdatedAt,
            updated_at = :now
        WHERE id = :sessionId
    """)
    suspend fun markAsSynced(
        sessionId: String,
        syncedAt: Long = System.currentTimeMillis(),
        serverUpdatedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    )

    /** Update sync status setelah sync gagal. */
    @Query("""
        UPDATE activity_sessions SET 
            sync_status = 'failed', 
            sync_attempts = sync_attempts + 1,
            last_sync_error = :errorMessage,
            updated_at = :now
        WHERE id = :sessionId
    """)
    suspend fun markAsFailed(
        sessionId: String,
        errorMessage: String?,
        now: Long = System.currentTimeMillis(),
    )

    /** Hitung jumlah session yang belum di-sync (untuk badge/indicator di UI). */
    @Query("SELECT COUNT(*) FROM activity_sessions WHERE sync_status != 'synced'")
    fun observeUnsyncedCount(): Flow<Int>

    /** Reset session yang stuck di SYNCING (misal app crash saat sync). */
    @Query("""
        UPDATE activity_sessions SET sync_status = 'pending' 
        WHERE sync_status = 'syncing' AND updated_at < :staleThresholdMs
    """)
    suspend fun resetStaleSyncingSessions(staleThresholdMs: Long)

    // =========================================================================
    // AGGREGATION — Untuk reporting & dashboard
    // =========================================================================

    /** Total langkah hari ini. */
    @Query("""
        SELECT COALESCE(SUM(total_steps), 0) FROM activity_sessions 
        WHERE user_id = :userId AND started_at >= :todayStartMs AND status = 'completed'
    """)
    fun observeTodayTotalSteps(userId: String, todayStartMs: Long): Flow<Int>

    /** Total kalori hari ini. */
    @Query("""
        SELECT COALESCE(SUM(calories_burned), 0.0) FROM activity_sessions 
        WHERE user_id = :userId AND started_at >= :todayStartMs AND status = 'completed'
    """)
    fun observeTodayTotalCalories(userId: String, todayStartMs: Long): Flow<Double>

    /** Total jarak hari ini. */
    @Query("""
        SELECT COALESCE(SUM(distance_meters), 0.0) FROM activity_sessions 
        WHERE user_id = :userId AND started_at >= :todayStartMs AND status = 'completed'
    """)
    fun observeTodayTotalDistance(userId: String, todayStartMs: Long): Flow<Double>

    // =========================================================================
    // ANALYTICS (Charts)
    // =========================================================================

    /** 
     * Ambil semua session di rentang waktu tertentu.
     * Digunakan oleh DashboardViewModel untuk kalkulasi:
     * - Weekly BarChart (group by day)
     * - Monthly LineChart (group by week)
     * - RadarChart (group by activity_type)
     * - Streak calculations
     */
    @Query("""
        SELECT * FROM activity_sessions 
        WHERE user_id = :userId 
          AND started_at >= :startMs 
          AND started_at <= :endMs 
          AND status = 'completed'
        ORDER BY started_at ASC
    """)
    fun observeSessionsBetweenDates(userId: String, startMs: Long, endMs: Long): Flow<List<ActivitySessionEntity>>
}
