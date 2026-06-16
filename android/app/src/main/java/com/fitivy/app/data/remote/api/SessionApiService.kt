package com.fitivy.app.data.remote.api

import com.fitivy.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface untuk session sync endpoints.
 *
 * Semua endpoint di sini dipakai oleh SyncWorker (background),
 * bukan langsung oleh UI. Return Response<> agar error code bisa di-handle.
 */
interface SessionApiService {

    /**
     * Sync session ke server.
     * Server logic:
     *   - Jika id belum ada → INSERT (return is_new = true)
     *   - Jika id sudah ada → UPDATE hanya jika client updated_at > server updated_at
     *   - Response selalu return server updated_at untuk conflict tracking
     */
    @POST("api/sessions")
    suspend fun syncSession(
        @Body request: SessionSyncRequest
    ): Response<ApiResponse<SessionSyncResponse>>

    /**
     * Batch upload step logs untuk session tertentu.
     * Server skip log yang id-nya sudah ada (idempotent).
     */
    @POST("api/sessions/{sessionId}/step-logs")
    suspend fun syncStepLogs(
        @Path("sessionId") sessionId: String,
        @Body request: StepLogBatchRequest
    ): Response<ApiResponse<BatchSyncResponse>>

    /**
     * Batch upload GPS points untuk session tertentu.
     * Server skip points yang id-nya sudah ada (idempotent).
     */
    @POST("api/sessions/{sessionId}/gps-batch")
    suspend fun syncGpsPoints(
        @Path("sessionId") sessionId: String,
        @Body request: GpsBatchRequest
    ): Response<ApiResponse<BatchSyncResponse>>
}
