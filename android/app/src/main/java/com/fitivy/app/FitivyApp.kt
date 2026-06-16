package com.fitivy.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fitivy.app.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * FitivyApp — Application class.
 *
 * @HiltAndroidApp: trigger Hilt code generation.
 * Configuration.Provider: agar WorkManager bisa inject dependencies via Hilt.
 */
@HiltAndroidApp
class FitivyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Enqueue periodic sync (setiap 30 menit saat ada koneksi)
        SyncWorker.enqueuePeriodicSync(this)
    }
}
