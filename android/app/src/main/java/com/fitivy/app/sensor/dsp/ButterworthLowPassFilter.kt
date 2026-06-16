package com.fitivy.app.sensor.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/**
 * ButterworthLowPassFilter — Orde 4, diimplementasikan sebagai kaskade
 * dua biquad section (Second-Order Sections / SOS).
 *
 * KENAPA Butterworth?
 *   Maximally flat passband — tidak ada ripple di frekuensi langkah (1.5-3.5 Hz).
 *   Chebyshev punya ripple (distorsi sinyal langkah), Bessel terlalu gradual
 *   (noise 6-7 Hz masih lolos).
 *
 * KENAPA orde 4?
 *   Rolloff 80 dB/decade. Pada 10 Hz (2× cutoff): atenuasi ~24 dB.
 *   Pada 20 Hz (motor vibration): atenuasi ~48 dB. Cukup agresif
 *   tanpa phase distortion berlebihan.
 *
 * KENAPA cutoff 5 Hz?
 *   Frekuensi langkah manusia: walking 1.5-2.2 Hz, running 2.5-3.5 Hz,
 *   sprint 3.5-4.5 Hz. Di atas 5 Hz = noise (motor, kantong gemetar, meja).
 *
 * KENAPA SOS dan bukan Direct Form?
 *   Direct Form orde 4 memiliki 4 pole — koefisien denominator peka
 *   terhadap floating-point quantization error. SOS membagi jadi 2 biquad
 *   orde 2 yang masing-masing stabil secara numerik.
 *
 * KENAPA 3 instance independen (x, y, z)?
 *   Setiap sumbu akselerometer adalah sinyal independen. Jika difilter
 *   setelah digabung ke magnitude, informasi arah hilang.
 *
 * @param sampleRateHz  Sampling rate sensor (50 Hz untuk SENSOR_DELAY_GAME)
 * @param cutoffHz      Frekuensi cutoff (-3dB point). Default 5 Hz.
 */
class ButterworthLowPassFilter(
    sampleRateHz: Double = 50.0,
    cutoffHz: Double = 5.0
) {
    // Koefisien dihitung di init block menggunakan bilinear transform
    private val sections: Array<BiquadCoefficients>

    // State per axis — masing-masing punya delay history sendiri
    // Index 0..1 = dua biquad sections per axis
    private val stateX = Array(2) { BiquadState() }
    private val stateY = Array(2) { BiquadState() }
    private val stateZ = Array(2) { BiquadState() }

    init {
        sections = computeCoefficients(sampleRateHz, cutoffHz)
    }

    /**
     * Filter satu sample [x, y, z] dan return [x_filtered, y_filtered, z_filtered].
     *
     * HARUS dipanggil per-sample secara berurutan (time-series).
     * TIDAK thread-safe — panggil dari satu thread saja (sensor callback thread).
     *
     * Execution time: ~2μs (6 multiply + 6 add per biquad × 2 sections × 3 axes = 72 FLOPs)
     */
    fun filter(input: FloatArray): FloatArray {
        require(input.size >= 3) { "Input harus minimal [x, y, z]" }

        val xf = applyCascade(input[0].toDouble(), stateX)
        val yf = applyCascade(input[1].toDouble(), stateY)
        val zf = applyCascade(input[2].toDouble(), stateZ)

        return floatArrayOf(xf.toFloat(), yf.toFloat(), zf.toFloat())
    }

    /**
     * Cascade processing: input melewati Section 0, output-nya menjadi
     * input Section 1. Setiap section adalah biquad IIR filter.
     */
    private fun applyCascade(input: Double, states: Array<BiquadState>): Double {
        var signal = input
        for (i in sections.indices) {
            signal = applyBiquad(signal, sections[i], states[i])
        }
        return signal
    }

    /**
     * Single biquad (Direct Form II Transposed):
     *   y[n] = b0*x[n] + w1
     *   w1   = b1*x[n] - a1*y[n] + w2
     *   w2   = b2*x[n] - a2*y[n]
     *
     * KENAPA Direct Form II Transposed?
     *   Membutuhkan hanya 2 unit delay (w1, w2) dan memiliki
     *   numerical stability terbaik untuk float.
     */
    private fun applyBiquad(
        input: Double,
        coeff: BiquadCoefficients,
        state: BiquadState
    ): Double {
        val output = coeff.b0 * input + state.w1
        state.w1 = coeff.b1 * input - coeff.a1 * output + state.w2
        state.w2 = coeff.b2 * input - coeff.a2 * output
        return output
    }

    /**
     * Reset semua delay state — dipanggil saat mulai tracking session baru.
     * Filter membutuhkan ~0.5-1 detik setelah reset untuk konvergen (transient).
     */
    fun reset() {
        stateX.forEach { it.reset() }
        stateY.forEach { it.reset() }
        stateZ.forEach { it.reset() }
    }

    // =========================================================================
    // KOEFISIEN BILINEAR TRANSFORM
    // =========================================================================

    /**
     * Menghitung koefisien Butterworth orde 4 menggunakan bilinear transform.
     * Orde 4 = 2 biquad sections (pair of conjugate poles each).
     *
     * Langkah:
     * 1. Tentukan posisi pole pada Butterworth circle (s-plane unit circle)
     * 2. Pair conjugate poles menjadi biquad sections
     * 3. Bilinear transform setiap biquad dari s-domain ke z-domain
     */
    private fun computeCoefficients(fs: Double, fc: Double): Array<BiquadCoefficients> {
        // Pre-warp cutoff frequency untuk kompensasi nonlinearitas bilinear transform.
        // Tanpa pre-warping, cutoff digital akan bergeser lebih rendah dari yang diinginkan.
        val wd = 2.0 * fs * tan(Math.PI * fc / fs)

        // Butterworth orde 4: 4 poles pada s-plane unit circle.
        // Sudut pole: θ_k = π(2k + N + 1) / (2N) untuk k = 0..N-1, N = 4
        // Ini menghasilkan 2 pasang conjugate poles:
        //   Pair 1: θ = 5π/8 → damping ratio |cos(5π/8)| = 0.3827
        //   Pair 2: θ = 7π/8 → damping ratio |cos(7π/8)| = 0.9239
        val zeta1 = abs(cos(5.0 * Math.PI / 8.0)) // ≈ 0.3827
        val zeta2 = abs(cos(7.0 * Math.PI / 8.0)) // ≈ 0.9239

        return arrayOf(
            bilinearTransformBiquad(wd, zeta1, fs),
            bilinearTransformBiquad(wd, zeta2, fs)
        )
    }

    /**
     * Bilinear transform dari satu analog Butterworth biquad ke digital.
     *
     * Analog prototype: H(s) = ωd² / (s² + 2ζωd·s + ωd²)
     * Substitusi bilinear: s = (2/T) × (z-1)/(z+1)
     * Menghasilkan digital transfer function H(z) = (b0 + b1*z⁻¹ + b2*z⁻²) / (1 + a1*z⁻¹ + a2*z⁻²)
     */
    private fun bilinearTransformBiquad(
        wd: Double, zeta: Double, fs: Double
    ): BiquadCoefficients {
        val t = 1.0 / fs   // Sampling period
        val wd2 = wd * wd

        // Koefisien dari substitusi bilinear
        val k2 = 4.0 / (t * t)              // (2/T)²
        val k3 = 4.0 * zeta * wd / t        // 2 × (2/T) × ζ × ωd

        // Denominator normalisasi: a0 = (2/T)² + 2ζωd(2/T) + ωd²
        val a0 = k2 + k3 + wd2

        // Numerator (all-pole Butterworth: numerator analog = ωd²)
        val b0 = wd2 / a0
        val b1 = 2.0 * wd2 / a0
        val b2 = wd2 / a0

        // Denominator (normalized so a0 = 1)
        val a1 = (2.0 * wd2 - 2.0 * k2) / a0
        val a2 = (k2 - k3 + wd2) / a0

        return BiquadCoefficients(b0, b1, b2, a1, a2)
    }
}

/**
 * Koefisien untuk satu biquad section (Second-Order Section).
 * Immutable — dihitung sekali saat filter construction.
 */
data class BiquadCoefficients(
    val b0: Double, val b1: Double, val b2: Double,
    val a1: Double, val a2: Double
)

/**
 * State (delay elements) untuk satu biquad section.
 * Mutable — berubah setiap kali sample baru diproses.
 * w1 dan w2 adalah internal state Direct Form II Transposed.
 */
class BiquadState {
    var w1: Double = 0.0
    var w2: Double = 0.0

    fun reset() {
        w1 = 0.0
        w2 = 0.0
    }
}
