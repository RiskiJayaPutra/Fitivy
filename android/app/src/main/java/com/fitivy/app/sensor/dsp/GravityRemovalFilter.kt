package com.fitivy.app.sensor.dsp

/**
 * GravityRemovalFilter — Isolasi akselerasi linear dari komponen gravitasi.
 *
 * MASALAH yang dipecahkan:
 *   Raw accelerometer selalu mengandung gravitasi (~9.81 m/s² ke arah pusat bumi).
 *   Jika HP dipegang tegak, gravitasi dominan di sumbu Y.
 *   Jika HP datar (layar ke atas), gravitasi dominan di sumbu Z.
 *   Pendekatan lama (|√(x²+y²+z²) - 9.81|) SALAH karena menghilangkan informasi
 *   arah — akselerasi horizontal 1.0 m/s² saat HP miring hanya terbaca 0.05 m/s².
 *
 * SOLUSI: Complementary filter (high-pass) per-axis.
 *   gravity_estimate[i] = α × gravity_prev[i] + (1 - α) × raw[i]
 *   linear[i] = raw[i] - gravity_estimate[i]
 *
 * α = 0.95:
 *   - Convergence time ~1 detik (acceptable karena sliding window = 2 detik)
 *   - Cukup stabil untuk tidak bocorkan rotasi lambat ke linear acceleration
 *   - Cukup responsif untuk adaptasi saat HP dikeluarkan dari saku
 *
 * CATATAN: Jika device memiliki TYPE_LINEAR_ACCELERATION (gyroscope-assisted),
 * gunakan itu sebagai gantinya. Filter ini adalah fallback software.
 */
class GravityRemovalFilter(private val alpha: Float = 0.95f) {

    private val gravity = FloatArray(3) { 0f }
    private var isInitialized = false

    /**
     * Filter satu sample [x, y, z] dan return akselerasi linear [lx, ly, lz].
     *
     * @param rawAccel Raw accelerometer values dari SensorEvent.values
     * @return FloatArray[3] akselerasi linear (gravitasi sudah dihilangkan per-axis)
     */
    fun filter(rawAccel: FloatArray): FloatArray {
        require(rawAccel.size >= 3) { "Input harus minimal 3 elemen [x, y, z]" }

        if (!isInitialized) {
            // Seed gravity estimate dengan pembacaan pertama.
            // Asumsi: saat pertama kali dipanggil, device relatif diam,
            // jadi raw ≈ gravitasi murni.
            for (i in 0..2) {
                gravity[i] = rawAccel[i]
            }
            isInitialized = true
            // Return zero — belum ada data linear yang valid
            return FloatArray(3) { 0f }
        }

        // Low-pass filter untuk estimasi gravitasi.
        // α tinggi (0.95) = gravitasi estimate bergerak sangat lambat,
        // hanya responsif terhadap perubahan orientasi yang sustained.
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1f - alpha) * rawAccel[i]
        }

        // Linear acceleration = raw - gravity (per-axis, BUKAN magnitude!)
        // Ini mempertahankan informasi arah akselerasi.
        return FloatArray(3) { i -> rawAccel[i] - gravity[i] }
    }

    /**
     * Reset state — dipanggil saat mulai tracking session baru.
     */
    fun reset() {
        gravity.fill(0f)
        isInitialized = false
    }
}
