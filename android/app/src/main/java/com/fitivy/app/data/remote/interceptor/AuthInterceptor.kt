package com.fitivy.app.data.remote.interceptor

import com.fitivy.app.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthInterceptor — auto-attach Bearer token ke setiap outgoing HTTP request.
 *
 * Ditambahkan ke OkHttpClient di NetworkModule (Hilt DI).
 * Skip header untuk endpoint publik (login, register) yang tidak butuh auth.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        // Endpoint yang tidak perlu auth header
        private val PUBLIC_PATHS = listOf(
            "api/auth/login",
            "api/auth/register",
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestPath = originalRequest.url.encodedPath

        // Skip auth header untuk public endpoints
        if (PUBLIC_PATHS.any { requestPath.contains(it) }) {
            return chain.proceed(originalRequest)
        }

        // Attach Bearer token jika tersedia
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
