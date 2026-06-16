package com.fitivy.app.sensor.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * SyntheticSignalGenerator — Utilitas "Pro-Grade" untuk merancang dan mensintesis
 * sinyal akselerometer palsu demi kebutuhan Unit Testing dan TDD (Test-Driven Development).
 * 
 * Filosofi desain standar Strava/Garmin:
 * Kita mensimulasikan hukum fisika di mana pergerakan langkah manusia terdiri dari
 * osilasi harmonik fundamental (~2 Hz) yang ditumpangi noise getaran frekuensi tinggi 
 * dari pergerakan perangkat atau kendaraan (~20 Hz), serta komponen DC statis (Gravitasi).
 */
object SyntheticSignalGenerator {

    const val SAMPLE_RATE_HZ = 50.0
    const val DELTA_T_NANOS = 20_000_000L // 1/50 detik dalam nanoseconds

    /**
     * Membangkitkan deret sampel 3D berdasarkan parameter fisika kinematika.
     * 
     * @param durationSecs Durasi simulasi dalam detik.
     * @param gravity Komponen DC gravitasi (biasanya 9.81 m/s^2), didistribusikan ke sumbu Z.
     * @param stepFreq Frekuensi osilasi langkah/lari manusia (1.5 - 3.5 Hz).
     * @param stepAmp Amplitudo dorongan lari manusia (m/s^2).
     * @param noiseFreq Frekuensi gangguan motorik / guncangan mekanis kantong (> 15 Hz).
     * @param noiseAmp Amplitudo noise guncangan.
     * @param addWhiteNoise Menambahkan noise acak Gaussian (Random Walk micro-jitter).
     */
    fun generate(
        durationSecs: Double,
        gravity: Double = 9.81,
        stepFreq: Double = 2.0,
        stepAmp: Double = 3.0,
        noiseFreq: Double = 20.0,
        noiseAmp: Double = 5.0,
        addWhiteNoise: Boolean = false
    ): List<ProcessedSample> {
        val totalSamples = (durationSecs * SAMPLE_RATE_HZ).toInt()
        val samples = mutableListOf<ProcessedSample>()
        
        var currentNanos = 1_000_000_000_000L // Dimulai di timestamp arbiter

        for (i in 0 until totalSamples) {
            val t = i / SAMPLE_RATE_HZ

            // 1. Fundamental Human Motion (Sinyal yang INGIN kita pertahankan)
            // Lari menghasilkan osilasi vertikal yang kuat dan sway lateral kecil.
            val stepSignal = stepAmp * sin(2.0 * PI * stepFreq * t)

            // 2. High Frequency Mechanical Noise (Sinyal yang INGIN kita bunuh)
            // Guncangan mekanis relatif cepat dan tidak stabil.
            val noiseSignal = noiseAmp * sin(2.0 * PI * noiseFreq * t)

            // 3. White Noise (Micro-jitter komponen kelistrikan MEMS Sensor)
            val whiteNoise = if (addWhiteNoise) (Random.nextDouble(-0.5, 0.5)) else 0.0

            // Superposisi Linear
            // X-Axis (Kiri-Kanan): Sway langkah kecil + Noise seragam
            val x = (0.2 * stepSignal + noiseSignal + whiteNoise).toFloat()

            // Y-Axis (Depan-Belakang): Momentum dorongan dominan + Noise
            val y = (0.5 * stepSignal + noiseSignal + whiteNoise).toFloat()

            // Z-Axis (Atas-Bawah): Gravitasi Statis + Loncatan Penuh + Noise
            val z = (gravity + stepSignal + noiseSignal + whiteNoise).toFloat()

            samples.add(ProcessedSample(x, y, z, currentNanos))
            currentNanos += DELTA_T_NANOS
        }

        return samples
    }

    /**
     * Helper untuk murni mengekstrak data 1D (misal Z-axis) agar mudah dites oleh fungsi filter primitif.
     */
    fun extractAxisAsFloatArray(samples: List<ProcessedSample>, axis: String): List<FloatArray> {
        return samples.map { sample ->
            when (axis.uppercase()) {
                "X" -> floatArrayOf(sample.linearX, 0f, 0f)
                "Y" -> floatArrayOf(0f, sample.linearY, 0f)
                "Z" -> floatArrayOf(0f, 0f, sample.linearZ)
                "ALL" -> floatArrayOf(sample.linearX, sample.linearY, sample.linearZ)
                else -> floatArrayOf(0f, 0f, 0f)
            }
        }
    }
}
