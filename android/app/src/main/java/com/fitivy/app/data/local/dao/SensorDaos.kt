package com.fitivy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitivy.app.data.local.entity.GpsRouteEntity
import com.fitivy.app.data.local.entity.StepCounterBaselineEntity
import com.fitivy.app.data.local.entity.StepLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepLog: StepLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stepLogs: List<StepLogEntity>)

    @Query("SELECT * FROM step_logs WHERE session_id = :sessionId ORDER BY recorded_at ASC")
    fun getBySessionId(sessionId: String): Flow<List<StepLogEntity>>

    @Query("SELECT SUM(step_count) FROM step_logs WHERE session_id = :sessionId")
    suspend fun getTotalStepsForSession(sessionId: String): Int?

    @Query("SELECT SUM(calories_burned) FROM step_logs WHERE session_id = :sessionId")
    suspend fun getTotalCaloriesForSession(sessionId: String): Double?

    @Query("SELECT * FROM step_logs WHERE is_synced = 0 ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnsyncedLogs(limit: Int = 500): List<StepLogEntity>

    @Query("UPDATE step_logs SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)
}

@Dao
interface GpsRouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gpsRoute: GpsRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gpsRoutes: List<GpsRouteEntity>)

    @Query("SELECT * FROM gps_routes WHERE session_id = :sessionId ORDER BY sequence ASC")
    fun getBySessionId(sessionId: String): Flow<List<GpsRouteEntity>>

    @Query("SELECT COUNT(*) FROM gps_routes WHERE session_id = :sessionId")
    suspend fun getPointCountForSession(sessionId: String): Int

    @Query("SELECT * FROM gps_routes WHERE is_synced = 0 ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnsyncedRoutes(limit: Int = 500): List<GpsRouteEntity>

    @Query("UPDATE gps_routes SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)
}

@Dao
interface StepCounterBaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baseline: StepCounterBaselineEntity)

    @Query("SELECT * FROM step_counter_baseline WHERE id = 1")
    suspend fun getBaseline(): StepCounterBaselineEntity?

    @Query("DELETE FROM step_counter_baseline")
    suspend fun clear()
}
