package com.fitivy.app.data.remote.api

import com.fitivy.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface untuk auth endpoints.
 * Base URL dikonfigurasi di NetworkModule (Hilt DI).
 *
 * Catatan: semua method return Response<T> bukan langsung T,
 * agar bisa handle HTTP error codes di repository layer.
 */
interface AuthApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @POST("api/auth/refresh")
    suspend fun refreshToken(): Response<ApiResponse<AuthData>>

    @GET("api/auth/me")
    suspend fun me(): Response<ApiResponse<UserData>>
}
