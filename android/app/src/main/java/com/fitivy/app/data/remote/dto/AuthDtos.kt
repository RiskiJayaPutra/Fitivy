package com.fitivy.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// =============================================================================
// REQUEST DTOs
// =============================================================================

data class RegisterRequest(
    @SerializedName("name")         val name: String,
    @SerializedName("email")        val email: String,
    @SerializedName("password")     val password: String,
    @SerializedName("nim_nip")      val nimNip: String,
    @SerializedName("gender")       val gender: String? = null,
    @SerializedName("birth_date")   val birthDate: String? = null,
    @SerializedName("height_cm")    val heightCm: Float? = null,
    @SerializedName("weight_kg")    val weightKg: Float? = null,
    @SerializedName("device_id")    val deviceId: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("fcm_token")    val fcmToken: String? = null,
)

data class LoginRequest(
    @SerializedName("login")        val login: String,           // Email atau NIM/NIP
    @SerializedName("password")     val password: String,
    @SerializedName("device_id")    val deviceId: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("fcm_token")    val fcmToken: String? = null,
)

// =============================================================================
// RESPONSE DTOs
// =============================================================================

/**
 * Wrapper response standar dari Laravel API.
 * Semua endpoint return format: { status, message, data?, errors? }
 */
data class ApiResponse<T>(
    @SerializedName("status")  val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data: T? = null,
    @SerializedName("errors")  val errors: Map<String, List<String>>? = null,
)

data class AuthData(
    @SerializedName("user")  val user: UserDto,
    @SerializedName("token") val token: TokenDto,
)

data class UserData(
    @SerializedName("user") val user: UserDto,
)

data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type")   val tokenType: String,
    @SerializedName("abilities")    val abilities: List<String>? = null,
)

data class UserDto(
    @SerializedName("id")                val id: String,
    @SerializedName("name")              val name: String,
    @SerializedName("email")             val email: String,
    @SerializedName("nim_nip")           val nimNip: String?,
    @SerializedName("role")              val role: String,
    @SerializedName("is_active")         val isActive: Boolean,
    @SerializedName("avatar_url")        val avatarUrl: String?,
    @SerializedName("height_cm")         val heightCm: Float?,
    @SerializedName("weight_kg")         val weightKg: Float?,
    @SerializedName("birth_date")        val birthDate: String?,
    @SerializedName("gender")            val gender: String?,
    @SerializedName("device_id")         val deviceId: String?,
    @SerializedName("email_verified_at") val emailVerifiedAt: String?,
    @SerializedName("created_at")        val createdAt: String?,
    @SerializedName("updated_at")        val updatedAt: String?,
)
