package com.fitivy.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * PermissionHelper — utility untuk permission check sensor & location.
 *
 * Permission yang dibutuhkan Fitivy:
 *   - ACCESS_FINE_LOCATION: GPS tracking (wajib untuk FusedLocation HIGH_ACCURACY)
 *   - ACCESS_COARSE_LOCATION: fallback jika fine ditolak
 *   - ACTIVITY_RECOGNITION: step counter (API 29+)
 *   - BODY_SENSORS: accelerometer (API 33+)
 *   - FOREGROUND_SERVICE: foreground service notification (API 28+)
 *   - FOREGROUND_SERVICE_LOCATION: foreground service dengan GPS (API 34+)
 *   - POST_NOTIFICATIONS: notifikasi (API 33+)
 *
 * KENAPA banyak?
 *   Android memperketat permission setiap major version.
 *   API 29 tambah ACTIVITY_RECOGNITION, API 33 tambah POST_NOTIFICATIONS,
 *   API 34 tambah FOREGROUND_SERVICE_LOCATION. Harus handle semua.
 */
object PermissionHelper {

    /**
     * Permission yang dibutuhkan untuk tracking.
     * Return list sesuai API level device.
     */
    fun getRequiredTrackingPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        // API 29+ (Android 10): ACTIVITY_RECOGNITION untuk step counter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // API 33+ (Android 13): POST_NOTIFICATIONS untuk foreground service notif
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // API 34+ (Android 14): FOREGROUND_SERVICE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        return permissions.toTypedArray()
    }

    /**
     * Cek apakah semua permission yang dibutuhkan sudah granted.
     */
    fun hasAllTrackingPermissions(context: Context): Boolean {
        return getRequiredTrackingPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get daftar permission yang belum granted (untuk request ke user).
     */
    fun getMissingPermissions(context: Context): Array<String> {
        return getRequiredTrackingPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    /**
     * Cek apakah location permission granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Cek apakah activity recognition permission granted.
     */
    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true  // Pre-Q tidak butuh permission ini
        }
    }
}
