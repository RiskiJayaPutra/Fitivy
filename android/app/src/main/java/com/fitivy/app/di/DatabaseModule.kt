package com.fitivy.app.di

import android.content.Context
import androidx.room.Room
import com.fitivy.app.data.local.AppDatabase
import com.fitivy.app.data.local.dao.ActivitySessionDao
import com.fitivy.app.data.local.dao.GpsRouteDao
import com.fitivy.app.data.local.dao.StepCounterBaselineDao
import com.fitivy.app.data.local.dao.StepLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)   // Register semua migration
            // JANGAN pakai fallbackToDestructiveMigration() — data offline hilang!
            .build()
    }

    @Provides fun provideActivitySessionDao(db: AppDatabase): ActivitySessionDao = db.activitySessionDao()
    @Provides fun provideStepLogDao(db: AppDatabase): StepLogDao = db.stepLogDao()
    @Provides fun provideGpsRouteDao(db: AppDatabase): GpsRouteDao = db.gpsRouteDao()
    @Provides fun provideStepCounterBaselineDao(db: AppDatabase): StepCounterBaselineDao = db.stepCounterBaselineDao()
}
