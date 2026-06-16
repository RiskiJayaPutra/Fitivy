package com.fitivy.app.data.remote.interceptor

import android.content.Context
import android.content.Intent
import com.fitivy.app.data.local.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UnauthorizedInterceptor — handle 401 response secara global.
 *
 * Ketika server return 401 (token expired/invalid):
 *   1. Hapus token dari encrypted storage
 *   2. Broadcast event agar UI redirect ke login screen
 *
 * KENAPA interceptor bukan di ViewModel?
 *   - 401 bisa terjadi di endpoint manapun
 *   - Handle di interceptor = single point of handling
 *   - ViewModel hanya perlu subscribe ke auth state, bukan check setiap response
 *
 * CATATAN: Interceptor ini harus ditambahkan SETELAH AuthInterceptor di OkHttpClient chain.
 */
@Singleton
class UnauthorizedInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        // Action untuk LocalBroadcast — UI listen event ini untuk redirect ke login
        const val ACTION_UNAUTHORIZED = "com.fitivy.app.ACTION_UNAUTHORIZED"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            // Token sudah tidak valid — hapus dari storage
            tokenManager.clearAll()

            // Broadcast event agar Activity/Fragment yang aktif bisa redirect ke login
            val intent = Intent(ACTION_UNAUTHORIZED)
            context.sendBroadcast(intent)
        }

        return response
    }
}
