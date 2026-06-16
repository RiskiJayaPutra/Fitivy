package com.fitivy.app.sensor.dsp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Uji Coba Unit Kelas Berat untuk menguji atenuasi Filter Butterworth.
 * Menggunakan SyntheticSignalGenerator untuk menyuntikkan Harmonic Wave.
 */
class ButterworthLowPassFilterTest {

    private lateinit var filter: ButterworthLowPassFilter

    @BeforeEach
    fun setup() {
        // Cutoff Frequency: 5.0 Hz
        // Sampling Rate: 50.0 Hz
        filter = ButterworthLowPassFilter()
    }

    private fun calculatePeakToPeak(data: List<FloatArray>, axisIndex: Int = 2): Double {
        var minVal = Double.MAX_VALUE
        var maxVal = Double.MIN_VALUE
        // Skip 50 sample pertama (1 detik) untuk membiarkan transient state pada filter stabil.
        val steadyState = data.drop(50)
        for (sample in steadyState) {
            val v = sample[axisIndex].toDouble()
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        return if (steadyState.isEmpty()) 0.0 else (maxVal - minVal)
    }

    @Test
    fun `TEST 1 - Passband 2 Hz Lolos Tanpa Distorsi Berlebihan`() {
        // Sinyal 2Hz murni (Human Running), Amplitudo 3.0 m/s^2, Tanpa Noise
        val rawSamples = SyntheticSignalGenerator.generate(
            durationSecs = 5.0,
            gravity = 0.0,
            stepFreq = 2.0, stepAmp = 3.0,
            noiseFreq = 0.0, noiseAmp = 0.0
        )
        val rawZ = SyntheticSignalGenerator.extractAxisAsFloatArray(rawSamples, "Z")
        
        // Cek peak-to-peak sinyal asli (Harusnya mendekati 6.0 = 3.0 ke atas, -3.0 ke bawah)
        val rawP2P = calculatePeakToPeak(rawZ)
        assertTrue(rawP2P > 5.8)

        val filteredZ = rawZ.map { filter.filter(it) }
        val filteredP2P = calculatePeakToPeak(filteredZ)

        // Pada 2 Hz (Jauh di bawah cutoff 5 Hz), sinyal harus dipertahankan.
        // Toleransi redaman ringan diperbolehkan, harus tetap di atas 80% kekuatan aslinya (0.8 rasio).
        val amplitudeRatio = filteredP2P / rawP2P
        assertTrue(amplitudeRatio > 0.8, "Atenuasi 2Hz terlalu parah: Ratio = $amplitudeRatio")
    }

    @Test
    fun `TEST 2 - Stopband 20 Hz Diblokir Mutlak (Redaman lebih dari 40dB)`() {
        // Sinyal Getaran Motor 20Hz murni, Amplitudo Gila 10.0 m/s^2
        val rawSamples = SyntheticSignalGenerator.generate(
            durationSecs = 5.0,
            gravity = 0.0,
            stepFreq = 0.0, stepAmp = 0.0,
            noiseFreq = 20.0, noiseAmp = 10.0
        )
        val rawZ = SyntheticSignalGenerator.extractAxisAsFloatArray(rawSamples, "Z")
        
        val rawP2P = calculatePeakToPeak(rawZ)
        assertTrue(rawP2P > 19.0) // 10 up, 10 down

        val filteredZ = rawZ.map { filter.filter(it) }
        val filteredP2P = calculatePeakToPeak(filteredZ)

        // Pada 20 Hz (2 oktaf di atas cutoff 5Hz), Butterworth Orde 4 menembakkan atenuasi ~48 dB.
        // Ratio Amplitudo harus sangat kecil (< 0.01). Sinyal beringas 20Hz praktis dibunuh menjadi nol.
        val amplitudeRatio = filteredP2P / rawP2P
        assertTrue(amplitudeRatio < 0.01, "Filter gagal membunuh frekuensi tinggi: Ratio = $amplitudeRatio")
        assertTrue(filteredP2P < 0.2, "Getaran 20Hz masih merembes masuk > 0.2 m/s^2")
    }

    @Test
    fun `TEST 3 - Superposisi Linear (Sinyal Campuran Diekstrak Sempurna)`() {
        // Real-world scenario: Langkah 2.5Hz + Getaran 20Hz + White Noise
        val rawSamples = SyntheticSignalGenerator.generate(
            durationSecs = 5.0,
            gravity = 0.0, // Hilangkan gravitasi agar lebih mudah ngecek
            stepFreq = 2.5, stepAmp = 3.0,
            noiseFreq = 20.0, noiseAmp = 5.0,
            addWhiteNoise = true
        )
        val rawZ = SyntheticSignalGenerator.extractAxisAsFloatArray(rawSamples, "Z")
        
        val rawP2P = calculatePeakToPeak(rawZ)
        // Amplitudo gabungan akan sangat beringas
        assertTrue(rawP2P > 15.0)

        val filteredZ = rawZ.map { filter.filter(it) }
        val filteredP2P = calculatePeakToPeak(filteredZ)

        // Setelah di-filter, noise beringas dibuang dan hanya menyisakan bentuk sinus mulus langkah (2.5 Hz).
        // Sehingga peak-to-peak harus turun drastis menormalisasi ke nilai ~6.0 (karena stepAmp = 3.0).
        assertTrue(filteredP2P > 4.5 && filteredP2P < 6.5, 
            "Filter gagal memisahkan step dari noise. P2P output = $filteredP2P")
    }

    @Test
    fun `TEST 4 - Pemanggilan reset() secara paksa menghancurkan state buffer (delay line)`() {
        // Suntikkan DC Offset besar secara mendadak (mensimulasikan HP dilempar ke atas)
        for (i in 1..100) {
            filter.filter(floatArrayOf(0f, 0f, 100f))
        }

        // Ambil 1 sampel nol, filter pastinya butuh waktu untuk mengejar turun kembali (karena IIR)
        val tail1 = filter.filter(floatArrayOf(0f, 0f, 0f))
        assertTrue(abs(tail1[2]) > 10.0, "Filter IIR seharusnya punya efek buntut panjang")

        // Paksa RESET
        filter.reset()

        // Suntik 1 sampel nol lagi, hasilnya harus MUTLAK nol karena memori masa lalunya sudah dihapus
        val tail2 = filter.filter(floatArrayOf(0f, 0f, 0f))
        assertEquals(0.0f, tail2[2], 1e-6f, "State filter membocorkan sisa kalkulasi setelah reset()")
    }
}
