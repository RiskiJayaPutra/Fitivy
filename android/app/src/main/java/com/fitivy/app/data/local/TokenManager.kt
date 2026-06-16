package com.fitivy.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TokenManager — penyimpanan token yang aman menggunakan EncryptedSharedPreferences.
 *
 * KENAPA bukan plain SharedPreferences?
 *   - Plain SharedPreferences tersimpan sebagai XML di /data/data/<pkg>/
 *   - Pada rooted device, file ini bisa dibaca langsung
 *   - EncryptedSharedPreferences menggunakan AES-256 GCM untuk encrypt value
 *     dan AES-256 SIV untuk encrypt key
 *
 * KENAPA bukan DataStore?
 *   - Token auth bersifat synchronous-critical (dibutuhkan di interceptor)
 *   - DataStore async-first, butuh coroutine context
 *   - EncryptedSharedPreferences lebih tepat untuk credential storage
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "fitivy_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // =========================================================================
    // TOKEN
    // =========================================================================

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    val isLoggedIn: Boolean
        get() = getToken() != null

    // =========================================================================
    // USER INFO CACHE — untuk akses cepat tanpa API call
    // =========================================================================

    fun saveUserInfo(id: String, name: String, email: String, role: String) {
        prefs.edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    // =========================================================================
    // CLEAR ALL — dipanggil saat logout
    // =========================================================================

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun getSharedPreferences(): SharedPreferences = prefs
}
