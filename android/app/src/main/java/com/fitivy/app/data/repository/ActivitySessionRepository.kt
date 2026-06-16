package com.fitivy.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.fitivy.app.data.local.TokenManager
import com.fitivy.app.data.local.dao.ActivitySessionDao
import com.fitivy.app.data.local.dao.GpsRouteDao
import com.fitivy.app.data.local.dao.StepLogDao
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.local.entity.SessionStatus
import com.fitivy.app.data.local.entity.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ActivitySessionRepository — single source of truth untuk session lifecycle.
 *
 * OFFLINE-FIRST FLOW:
 *   1. startSession()   → INSERT ke Room lokal (sync_status = PENDING)
 *   2. updateSession()  → UPDATE Room setiap 5 detik (lokal)
 *   3. endSession()     → FINALIZE di Room, set sync_status = PENDING
 *   4. SyncWorker picks up PENDING sessions saat ada koneksi
 *   5. Sync sukses → sync_status = SYNCED
 *
 * SEMUA operasi menulis ke Room dulu (local-first).
 * Tidak ada satu pun operasi yang butuh internet untuk berhasil.
 */
@Singleton
class ActivitySessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: ActivitySessionDao,
    private val stepLogDao: StepLogDao,
    private val gpsRouteDao: GpsRouteDao,
    private val tokenManager: TokenManager,
) {
    companion object {
        private const val TAG = "SessionRepository"
    }

    // =========================================================================
    // SESSION LIFECYCLE
    // =========================================================================

    /**
     * Mulai session baru.
     * Return sessionId (UUID) yang dipakai oleh semua sensor components.
     *
     * @param activityType "walking", "running", atau "cycling"
     * @return UUID session yang baru dibuat
     */
    suspend fun startSession(activityType: String = "walking"): String {
        // Guard: pastikan tidak ada session yang masih active
        val existingActive = sessionDao.getActiveSession()
        if (existingActive != null) {
            Log.w(TAG, "Ending existing active session: ${existingActive.id}")
            endSession(existingActive.id, isAbandoned = true)
        }

        val sessionId = UUID.randomUUID().toString()
        val userId = tokenManager.getUserId() ?: "unknown"
        val now = System.currentTimeMillis()

        val session = ActivitySessionEntity(
            id = sessionId,
            userId = userId,
            activityType = activityType,
            status = SessionStatus.ACTIVE,
            startedAt = now,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryStart = getBatteryLevel(),
            syncStatus = SyncStatus.PENDING,
        )

        sessionDao.insert(session)
        Log.i(TAG, "Session started: $sessionId (type=$activityType)")

        return sessionId
    }

    /**
     * Update metrics session secara real-time (dipanggil tiap 5 detik).
     *
     * KENAPA tiap 5 detik bukan tiap detik?
     *   - SQLite write = I/O disk, tiap detik terlalu boros
     *   - 5 detik cukup granular untuk recovery jika app crash
     *   - UI tetap real-time via StateFlow dari sensor components
     */
    suspend fun updateSession(
        sessionId: String,
        totalSteps: Int,
        distanceMeters: Double,
        caloriesBurned: Double,
        durationSeconds: Long,
        avgSpeedKmh: Double?,
        maxSpeedKmh: Double?,
    ) {
        sessionDao.updateMetrics(
            sessionId = sessionId,
            totalSteps = totalSteps,
            distanceMeters = distanceMeters,
            caloriesBurned = caloriesBurned,
            durationSeconds = durationSeconds,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
        )
    }

    /**
     * Pause session — catat timestamp pause untuk hitung total pause time.
     */
    suspend fun pauseSession(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.status != SessionStatus.ACTIVE) return

        sessionDao.updateStatus(
            sessionId = sessionId,
            status = SessionStatus.PAUSED,
            pausedAt = System.currentTimeMillis(),
            totalPausedMs = session.totalPausedMs,
        )

        Log.d(TAG, "Session paused: $sessionId")
    }

    /**
     * Resume session — hitung durasi pause dan akumulasikan.
     */
    suspend fun resumeSession(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.status != SessionStatus.PAUSED) return

        val pauseDuration = if (session.pausedAt != null) {
            System.currentTimeMillis() - session.pausedAt
        } else 0L

        sessionDao.updateStatus(
            sessionId = sessionId,
            status = SessionStatus.ACTIVE,
            pausedAt = null,
            totalPausedMs = session.totalPausedMs + pauseDuration,
        )

        Log.d(TAG, "Session resumed: $sessionId (paused ${pauseDuration}ms)")
    }

    /**
     * End session — finalize semua metrics dan set sync_status = PENDING.
     *
     * Setelah ini, SyncWorker akan otomatis pickup session untuk sync
     * begitu ada koneksi internet (bisa 1 detik atau 3 hari kemudian).
     *
     * @param isAbandoned true jika session di-force-stop (crash, user force close)
     */
    suspend fun endSession(
        sessionId: String,
        totalSteps: Int? = null,
        distanceMeters: Double? = null,
        caloriesBurned: Double? = null,
        durationSeconds: Long? = null,
        avgSpeedKmh: Double? = null,
        maxSpeedKmh: Double? = null,
        isAbandoned: Boolean = false,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        val now = System.currentTimeMillis()

        // Hitung durasi final (exclude total pause time)
        val totalElapsed = now - session.startedAt
        val finalDuration = durationSeconds
            ?: ((totalElapsed - session.totalPausedMs) / 1000)

        // Kalau ada pause yang belum di-resume, hitung juga
        val unaccountedPause = if (session.pausedAt != null) {
            now - session.pausedAt
        } else 0L
        val adjustedDuration = finalDuration - (unaccountedPause / 1000)

        sessionDao.finalizeSession(
            sessionId = sessionId,
            status = if (isAbandoned) SessionStatus.ABANDONED else SessionStatus.COMPLETED,
            endedAt = now,
            durationSeconds = adjustedDuration.coerceAtLeast(0),
            totalSteps = totalSteps ?: session.totalSteps,
            distanceMeters = distanceMeters ?: session.distanceMeters,
            caloriesBurned = caloriesBurned ?: session.caloriesBurned,
            avgSpeedKmh = avgSpeedKmh ?: session.avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh ?: session.maxSpeedKmh,
            batteryEnd = getBatteryLevel(),
            syncStatus = SyncStatus.PENDING,
        )

        Log.i(TAG, "Session ended: $sessionId (${if (isAbandoned) "ABANDONED" else "COMPLETED"}, " +
                "steps=${totalSteps ?: session.totalSteps}, duration=${adjustedDuration}s)")
    }

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    /** Observe session saat ini (real-time di tracking screen). */
    fun observeSession(sessionId: String): Flow<ActivitySessionEntity?> =
        sessionDao.observeById(sessionId)

    /** Observe semua sessions user (history screen). */
    fun observeAllByUser(userId: String): Flow<List<ActivitySessionEntity>> =
        sessionDao.observeAllByUser(userId)

    /** Observe sessions di rentang waktu tertentu. */
    fun observeSessionsBetweenDates(userId: String, startMs: Long, endMs: Long): Flow<List<ActivitySessionEntity>> =
        sessionDao.observeSessionsBetweenDates(userId, startMs, endMs)

    /** Observe sessions hari ini (dashboard). */
    fun observeTodaySessions(userId: String): Flow<List<ActivitySessionEntity>> {
        val todayStart = getTodayStartMs()
        return sessionDao.observeTodaySessions(userId, todayStart)
    }

    /** Get session by ID (one-shot). */
    suspend fun getSession(sessionId: String): ActivitySessionEntity? =
        sessionDao.getById(sessionId)

    /** Cek apakah ada session yang sedang berjalan. */
    suspend fun getActiveSession(): ActivitySessionEntity? =
        sessionDao.getActiveSession()

    /** Observe jumlah session yang belum sync (untuk UI indicator). */
    fun observeUnsyncedCount(): Flow<Int> =
        sessionDao.observeUnsyncedCount()

    // =========================================================================
    // DAILY AGGREGATION
    // =========================================================================

    fun observeTodaySteps(userId: String): Flow<Int> =
        sessionDao.observeTodayTotalSteps(userId, getTodayStartMs())

    fun observeTodayCalories(userId: String): Flow<Double> =
        sessionDao.observeTodayTotalCalories(userId, getTodayStartMs())

    fun observeTodayDistance(userId: String): Flow<Double> =
        sessionDao.observeTodayTotalDistance(userId, getTodayStartMs())

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun getBatteryLevel(): Int? {
        return try {
            val batteryStatus = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
