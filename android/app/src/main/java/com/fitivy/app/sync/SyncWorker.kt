package com.fitivy.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fitivy.app.data.local.dao.ActivitySessionDao
import com.fitivy.app.data.local.dao.GpsRouteDao
import com.fitivy.app.data.local.dao.StepLogDao
import com.fitivy.app.data.remote.api.SessionApiService
import com.fitivy.app.data.remote.dto.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SyncWorker — WorkManager job yang sync data offline ke server.
 *
 * FLOW:
 *   1. Trigger otomatis saat koneksi internet tersedia (Constraint)
 *   2. Query sessions dengan sync_status = PENDING atau FAILED
 *   3. Untuk setiap session:
 *      a. Upload session metadata → POST /api/sessions
 *      b. Upload step logs batch → POST /api/sessions/{id}/step-logs
 *      c. Upload GPS points batch → POST /api/sessions/{id}/gps-batch
 *      d. Mark as SYNCED jika semua sukses
 *   4. Retry policy: exponential backoff, max 5 attempts
 *
 * KENAPA WorkManager dan bukan simple coroutine?
 *   - WorkManager survive app kill, device reboot, Doze mode
 *   - Constraint-aware: hanya jalankan saat ada koneksi
 *   - Built-in retry with backoff
 *   - Guaranteed execution: data PASTI sampai ke server (eventually)
 *
 * IDEMPOTENCY:
 *   Semua endpoint server harus idempotent. Jika SyncWorker crash di tengah
 *   dan retry, server harus handle duplicate gracefully (upsert by UUID).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionDao: ActivitySessionDao,
    private val stepLogDao: StepLogDao,
    private val gpsRouteDao: GpsRouteDao,
    private val sessionApi: SessionApiService,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "fitivy_sync_worker"
        private const val MAX_SYNC_ATTEMPTS = 5
        private const val BATCH_SIZE = 200              // GPS/step logs per batch request

        private val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Enqueue one-time sync request.
         * Dipanggil setelah endSession() atau manual by user.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)  // Wajib ada internet
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,     // 10 detik → 20 → 40 → 80 → 160 detik
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,         // Replace yang sedang berjalan (hindari duplikat)
                    request
                )

            Log.d(TAG, "Sync work enqueued")
        }

        /**
         * Enqueue periodic sync (setiap 30 menit saat ada koneksi).
         * Dipanggil saat app launch.
         */
        fun enqueuePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                30, TimeUnit.MINUTES,                   // Repeat interval
                10, TimeUnit.MINUTES                    // Flex interval
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.KEEP,    // Jangan replace yang sudah ada
                    request
                )

            Log.d(TAG, "Periodic sync work enqueued (30 min interval)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker started (attempt ${runAttemptCount + 1})")

        // Reset sessions yang stuck di SYNCING (misal worker crash sebelumnya)
        val staleThreshold = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        sessionDao.resetStaleSyncingSessions(staleThreshold)

        // Ambil sessions yang perlu di-sync
        val sessionsToSync = sessionDao.getSessionsToSync(
            maxAttempts = MAX_SYNC_ATTEMPTS
        )

        if (sessionsToSync.isEmpty()) {
            Log.i(TAG, "No sessions to sync")
            return Result.success()
        }

        Log.i(TAG, "Found ${sessionsToSync.size} sessions to sync")

        var allSuccess = true
        var hasRetryable = false

        for (session in sessionsToSync) {
            try {
                val success = syncOneSession(session.id)
                if (!success) {
                    allSuccess = false
                    hasRetryable = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing session ${session.id}", e)
                sessionDao.markAsFailed(session.id, e.message)
                allSuccess = false
                hasRetryable = true
            }
        }

        return when {
            allSuccess -> {
                Log.i(TAG, "All sessions synced successfully")
                Result.success()
            }
            hasRetryable && runAttemptCount < MAX_SYNC_ATTEMPTS -> {
                Log.w(TAG, "Some sessions failed, will retry (attempt ${runAttemptCount + 1})")
                Result.retry()   // WorkManager akan retry dengan exponential backoff
            }
            else -> {
                Log.e(TAG, "Max retry attempts reached, giving up")
                Result.failure()
            }
        }
    }

    /**
     * Sync satu session lengkap (metadata + step logs + GPS points).
     * Return true jika semua sukses.
     */
    private suspend fun syncOneSession(sessionId: String): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false

        // Mark as SYNCING
        sessionDao.markAsSyncing(sessionId)

        // === STEP 1: Sync session metadata ===
        val sessionRequest = SessionSyncRequest(
            id = session.id,
            userId = session.userId,
            activityType = session.activityType,
            status = session.status,
            startedAt = formatTimestamp(session.startedAt),
            endedAt = session.endedAt?.let { formatTimestamp(it) },
            durationSeconds = session.durationSeconds,
            totalSteps = session.totalSteps,
            distanceMeters = session.distanceMeters,
            caloriesBurned = session.caloriesBurned,
            avgSpeedKmh = session.avgSpeedKmh,
            maxSpeedKmh = session.maxSpeedKmh,
            deviceModel = session.deviceModel,
            batteryStart = session.batteryStart,
            batteryEnd = session.batteryEnd,
        )

        val sessionResponse = sessionApi.syncSession(sessionRequest)
        if (!sessionResponse.isSuccessful) {
            val errorMsg = "Session sync failed: HTTP ${sessionResponse.code()}"
            Log.e(TAG, errorMsg)
            sessionDao.markAsFailed(sessionId, errorMsg)
            return false
        }

        val serverData = sessionResponse.body()?.data
        val serverUpdatedAt = serverData?.updatedAt?.let { parseTimestamp(it) }

        // === STEP 2: Sync step logs (batched) ===
        val stepLogsSynced = syncStepLogsBatch(sessionId)
        if (!stepLogsSynced) {
            sessionDao.markAsFailed(sessionId, "Step logs sync failed")
            return false
        }

        // === STEP 3: Sync GPS points (batched) ===
        val gpsSynced = syncGpsPointsBatch(sessionId)
        if (!gpsSynced) {
            sessionDao.markAsFailed(sessionId, "GPS sync failed")
            return false
        }

        // === ALL SUCCESS ===
        sessionDao.markAsSynced(
            sessionId = sessionId,
            serverUpdatedAt = serverUpdatedAt,
        )

        Log.i(TAG, "Session $sessionId fully synced")
        return true
    }

    /**
     * Sync step logs in batches of BATCH_SIZE.
     * Setiap batch yang sukses langsung di-mark sebagai synced.
     */
    private suspend fun syncStepLogsBatch(sessionId: String): Boolean {
        var offset = 0
        while (true) {
            val unsyncedLogs = stepLogDao.getUnsyncedLogs(limit = BATCH_SIZE)
                .filter { it.sessionId == sessionId }

            if (unsyncedLogs.isEmpty()) break

            val batchRequest = StepLogBatchRequest(
                stepLogs = unsyncedLogs.map { log ->
                    StepLogSyncItem(
                        id = log.id,
                        sessionId = log.sessionId,
                        stepCount = log.stepCount,
                        cadence = log.cadence,
                        caloriesBurned = log.caloriesBurned,
                        confidence = log.confidence,
                        accelerometerMagnitude = log.accelerometerMagnitude,
                        recordedAt = formatTimestamp(log.recordedAt),
                    )
                }
            )

            val response = sessionApi.syncStepLogs(sessionId, batchRequest)
            if (!response.isSuccessful) {
                Log.e(TAG, "Step logs batch sync failed: HTTP ${response.code()}")
                return false
            }

            // Mark batch as synced
            stepLogDao.markAsSynced(unsyncedLogs.map { it.id })
            Log.d(TAG, "Step logs batch synced: ${unsyncedLogs.size} logs")

            offset += unsyncedLogs.size
            if (unsyncedLogs.size < BATCH_SIZE) break   // Last batch
        }

        return true
    }

    /**
     * Sync GPS points in batches of BATCH_SIZE.
     */
    private suspend fun syncGpsPointsBatch(sessionId: String): Boolean {
        while (true) {
            val unsyncedPoints = gpsRouteDao.getUnsyncedRoutes(limit = BATCH_SIZE)
                .filter { it.sessionId == sessionId }

            if (unsyncedPoints.isEmpty()) break

            val batchRequest = GpsBatchRequest(
                gpsPoints = unsyncedPoints.map { point ->
                    GpsPointSyncItem(
                        id = point.id,
                        sessionId = point.sessionId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitude,
                        accuracy = point.accuracy,
                        speed = point.speed,
                        bearing = point.bearing,
                        sequence = point.sequence,
                        recordedAt = formatTimestamp(point.recordedAt),
                    )
                }
            )

            val response = sessionApi.syncGpsPoints(sessionId, batchRequest)
            if (!response.isSuccessful) {
                Log.e(TAG, "GPS batch sync failed: HTTP ${response.code()}")
                return false
            }

            gpsRouteDao.markAsSynced(unsyncedPoints.map { it.id })
            Log.d(TAG, "GPS batch synced: ${unsyncedPoints.size} points")

            if (unsyncedPoints.size < BATCH_SIZE) break
        }

        return true
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun formatTimestamp(timestampMs: Long): String = iso8601.format(Date(timestampMs))

    private fun parseTimestamp(iso: String): Long? {
        return try { iso8601.parse(iso)?.time } catch (e: Exception) { null }
    }
}
