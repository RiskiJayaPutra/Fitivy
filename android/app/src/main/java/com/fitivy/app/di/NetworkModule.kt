package com.fitivy.app.di

import com.fitivy.app.data.remote.api.AuthApiService
import com.fitivy.app.data.remote.api.SessionApiService
import com.fitivy.app.data.remote.interceptor.AuthInterceptor
import com.fitivy.app.data.remote.interceptor.UnauthorizedInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule — Hilt module untuk Retrofit, OkHttp, Gson.
 *
 * Interceptor chain order matters:
 *   1. HttpLoggingInterceptor — log request/response (debug only)
 *   2. AuthInterceptor — attach Bearer token
 *   3. UnauthorizedInterceptor — handle 401 response
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // TODO: Ganti dengan URL server production
    private const val BASE_URL = "http://10.0.2.2:8000/"  // 10.0.2.2 = localhost dari Android emulator

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")  // ISO 8601 dari Laravel
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        unauthorizedInterceptor: UnauthorizedInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                // Hanya log body saat debug — JANGAN di production (token terexpose di log)
                level = if (com.fitivy.app.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSessionApiService(retrofit: Retrofit): SessionApiService {
        return retrofit.create(SessionApiService::class.java)
    }
}
