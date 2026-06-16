package com.fitivy.app.sensor

import com.fitivy.app.sensor.dsp.WindowFeatures
import com.fitivy.app.sensor.model.ActivityType
import java.util.concurrent.atomic.AtomicInteger

/**
 * ActivityClassifier — Mengklasifikasikan aktivitas berdasarkan fitur dari SlidingWindow.
 * Dijalankan di Dispatchers.Default (background coroutine).
 */
object ActivityClassifier {

    private const val STATIONARY_MAG_THRESHOLD = 0.5
    private const val WALKING_MAG_THRESHOLD = 2.5

    // FIX 2: Menggunakan @Volatile untuk menjamin visibilitas thread saat read/write.
    // JVM menjamin operasi write/read ke reference type dengan @Volatile adalah atomic.
    // Mutex berlebihan karena kita hanya menimpa (overwrite) reference terbaru, 
    // bukan melakukan compound operation (check-then-act) yang butuh lock.
    @Volatile
    private var lastClassifiedActivity: ActivityType = ActivityType.STATIONARY

    // FIX 2: Menggunakan AtomicInteger untuk proteksi multi-threading counter.
    private val dspWarmupSamplesRemaining = AtomicInteger(0)

    /**
     * Entry point klasifikasi berdasarkan fitur yang diekstrak dari sinyal DSP.
     * Dipanggil oleh ActivityDetectionService setiap kali WindowFeatures baru di-emit.
     */
    fun classifyFromFeatures(features: WindowFeatures): ActivityType {
        // Abaikan klasifikasi jika window ini ditandai sebagai warmup
        // (menghindari klasifikasi prematur saat buffer belum stabil).
        if (features.isWarmup) {
            return lastClassifiedActivity
        }

        // Estimasi frekuensi langkah (Hz) berdasarkan Zero Crossing Rate.
        // Setiap siklus penuh (1 Hz) melewati garis nol sebanyak 2 kali.
        val estimatedFreq = features.zeroCrossingRate / 2.0

        val newActivity = when {
            // Jika fluktuasi sangat rendah -> Diam
            features.peakToPeakAmplitude < STATIONARY_MAG_THRESHOLD -> {
                ActivityType.STATIONARY
            }
            
            // Evaluasi pergerakan aktif
            else -> {
                val isHumanCadence = estimatedFreq in 0.8..4.5
                
                if (isHumanCadence) {
                    if (features.meanSvm < WALKING_MAG_THRESHOLD) {
                        ActivityType.WALKING
                    } else {
                        ActivityType.RUNNING
                    }
                } else {
                    // Proteksi Kendaraan (Vehicle Protection):
                    // Sinyal bergetar sangat kuat tapi frekuensi di luar batas langkah manusia
                    // (misalnya naik motor di jalan berbatu). Tetap anggap stationary/idle.
                    ActivityType.STATIONARY
                }
            }
        }

        val finalizedActivity = applyHysteresis(newActivity)
        lastClassifiedActivity = finalizedActivity
        return finalizedActivity
    }

    /**
     * Meminta state aktivitas terakhir secara thread-safe.
     */
    fun getCurrentActivity(): ActivityType {
        return lastClassifiedActivity
    }

    /**
     * Hysteresis sederhana:
     * Mengharuskan 2 kali konfirmasi aktivitas yang sama sebelum state berubah,
     * untuk mencegah UI flip-flop berkedip cepat di batas threshold.
     */
    @Volatile
    private var pendingActivity: ActivityType? = null
    
    private fun applyHysteresis(newActivity: ActivityType): ActivityType {
        if (newActivity == lastClassifiedActivity) {
            pendingActivity = null
            return newActivity
        }

        if (pendingActivity == newActivity) {
            // Konfirmasi kedua
            pendingActivity = null
            return newActivity
        } else {
            // Konfirmasi pertama
            pendingActivity = newActivity
            return lastClassifiedActivity
        }
    }

    /**
     * Set jumlah sample tambahan yang diabaikan saat DSP restart.
     */
    fun setWarmup(samples: Int) {
        dspWarmupSamplesRemaining.set(samples)
    }

    fun consumeWarmup(): Boolean {
        return dspWarmupSamplesRemaining.getAndUpdate { if (it > 0) it - 1 else 0 } > 0
    }
}
