package com.fitivy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitivy.app.data.local.dao.ActivitySessionDao
import com.fitivy.app.data.local.dao.GpsRouteDao
import com.fitivy.app.data.local.dao.StepCounterBaselineDao
import com.fitivy.app.data.local.dao.StepLogDao
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.local.entity.GpsRouteEntity
import com.fitivy.app.data.local.entity.StepCounterBaselineEntity
import com.fitivy.app.data.local.entity.StepLogEntity

@Database(
    entities = [
        ActivitySessionEntity::class,
        StepLogEntity::class,
        GpsRouteEntity::class,
        StepCounterBaselineEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun activitySessionDao(): ActivitySessionDao
    abstract fun stepLogDao(): StepLogDao
    abstract fun gpsRouteDao(): GpsRouteDao
    abstract fun stepCounterBaselineDao(): StepCounterBaselineDao

    companion object {
        const val DATABASE_NAME = "fitivy_database"
        val ALL_MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}
