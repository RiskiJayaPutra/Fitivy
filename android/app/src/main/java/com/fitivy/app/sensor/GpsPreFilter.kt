package com.fitivy.app.sensor

import android.location.Location
import android.os.Build
import kotlin.math.abs

/**
 * Hasil dari pipeline pre-filter GPS.
 */
sealed class FilterResult {
    data class Valid(val location: Location) : FilterResult()
    data class Rejected(val reason: String) : FilterResult()
}

/**
 * Statistik penolakan titik GPS (untuk monitoring/logging).
 */
data class GpsRejectionStats(
    var totalProcessed: Int = 0,
    var rejectedByMock: Int = 0, // FIX 3: Tambahan counter untuk Mock Provider
    var rejectedByAccuracy: Int = 0,
    var rejectedBySpeed: Int = 0,
    var rejectedByBearing: Int = 0,
    var rejectedByProvider: Int = 0,
    var rejectedByGap: Int = 0
) {
    val totalRejected: Int
        get() = rejectedByMock + rejectedByAccuracy + rejectedBySpeed + rejectedByBearing + rejectedByProvider + rejectedByGap
}

/**
 * GpsPreFilter — Multi-Layer Pre-Filter Pipeline untuk membuang anomali GPS (Spiderwebbing).
 */
class GpsPreFilter {

    val stats = GpsRejectionStats()

    /**
     * Eksekusi keempat layer filter secara berurutan.
     */
    fun filter(curr: Location, prev: Location?, isOutdoorSession: Boolean): FilterResult {
        stats.totalProcessed++

        // FIX 3: LAYER 0 — Mock Location Detection (Anti-Cheat Fake GPS)
        // Harus menjadi yang pertama dicek agar Fake GPS tidak bisa bypass filter
        // meskipun mereka merekayasa parameter speed & bearing dengan valid.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            if (curr.isMock) {
                stats.rejectedByMock++
                return FilterResult.Rejected("Layer 0: mock provider detected")
            }
        } else {
            @Suppress("DEPRECATION")
            if (curr.isFromMockProvider) {
                stats.rejectedByMock++
                return FilterResult.Rejected("Layer 0: mock provider detected")
            }
        }

        // LAYER 1: Accuracy Radius Filter
        if (!isAccuracyValid(curr)) {
            stats.rejectedByAccuracy++
            return FilterResult.Rejected("Layer 1: Accuracy > 20m (${curr.accuracy}m)")
        }

        // LAYER 4: Provider Reliability
        if (!isProviderReliable(curr, isOutdoorSession)) {
            stats.rejectedByProvider++
            return FilterResult.Rejected("Layer 4: Network provider with accuracy > 50m")
        }

        // Layer 2, 3, dan Edge Case butuh titik sebelumnya
        if (prev != null) {
            val distanceMeters = prev.distanceTo(curr)
            val timeDeltaSec = (curr.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0

            if (timeDeltaSec > 0.0) {
                // EDGE CASE KAMPUS: Tunnel/Underpass Gap
                if (timeDeltaSec > 5.0) {
                    stats.rejectedByGap++
                    return FilterResult.Rejected("Edge Case: Gap > 5s (${String.format("%.1f", timeDeltaSec)}s), rejecting first point")
                }

                // LAYER 2: Speed Sanity Check
                if (!isSpeedPhysicallyValid(distanceMeters.toFloat(), timeDeltaSec)) {
                    stats.rejectedBySpeed++
                    return FilterResult.Rejected(
                        "Layer 2: Speed > 12m/s (${String.format("%.1f", distanceMeters / timeDeltaSec)}m/s)"
                    )
                }

                // LAYER 3: Bearing Change Rate Limiter
                if (distanceMeters > 2.0 && curr.hasBearing() && prev.hasBearing()) {
                    if (!isBearingChangeValid(prev, curr, timeDeltaSec)) {
                        stats.rejectedByBearing++
                        return FilterResult.Rejected("Layer 3: Bearing jump > 120 deg/sec")
                    }
                }
            }
        }

        return FilterResult.Valid(curr)
    }

    fun resetStats() {
        stats.totalProcessed = 0
        stats.rejectedByMock = 0
        stats.rejectedByAccuracy = 0
        stats.rejectedBySpeed = 0
        stats.rejectedByBearing = 0
        stats.rejectedByProvider = 0
        stats.rejectedByGap = 0
    }

    private fun isAccuracyValid(location: Location): Boolean {
        if (!location.hasAccuracy()) return false
        return location.accuracy <= 20.0f
    }

    private fun isSpeedPhysicallyValid(distanceMeters: Float, timeDeltaSec: Double): Boolean {
        val speedMps = distanceMeters / timeDeltaSec
        return speedMps <= 12.0
    }

    private fun isBearingChangeValid(prev: Location, curr: Location, timeDeltaSec: Double): Boolean {
        val prevBearing = prev.bearing
        val currBearing = curr.bearing
        
        var diff = abs(currBearing - prevBearing)
        if (diff > 180.0f) {
            diff = 360.0f - diff
        }

        val rateOfChange = diff / timeDeltaSec
        return rateOfChange <= 120.0
    }

    private fun isProviderReliable(location: Location, isOutdoorSession: Boolean): Boolean {
        val provider = location.provider ?: return false
        
        if (isOutdoorSession && provider.contains("network", ignoreCase = true)) {
            if (location.hasAccuracy() && location.accuracy > 50.0f) {
                return false
            }
        }
        return true
    }
}
