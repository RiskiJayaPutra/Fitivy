package com.fitivy.app.domain.model

/**
 * AuthResult — sealed class untuk merepresentasikan hasil operasi auth.
 *
 * Sealed class lebih safe daripada generic Result<T> karena:
 *   - Compiler enforce exhaustive when() check
 *   - Setiap state punya data yang berbeda (Success punya user+token, Error punya message)
 *   - LoggedOut adalah state distinct, bukan Success tanpa data
 */
sealed class AuthResult {
    data class Success(
        val user: UserDomain,
        val token: String,
    ) : AuthResult()

    data class Error(
        val message: String,
    ) : AuthResult()

    data object LoggedOut : AuthResult()
}

/**
 * UserDomain — domain model yang decoupled dari API DTO dan Room Entity.
 *
 * Ini yang dipakai di UI layer. Perubahan di API response format
 * tidak langsung impact UI, cukup update mapper di repository.
 */
data class UserDomain(
    val id: String,
    val name: String,
    val email: String,
    val nimNip: String?,
    val role: String,
    val isActive: Boolean,
    val avatarUrl: String?,
    val heightCm: Float?,
    val weightKg: Float?,
    val birthDate: String?,
    val gender: String?,
) {
    val isMahasiswa: Boolean get() = role == "mahasiswa"
    val isDosen: Boolean get() = role == "dosen"
    val isAdminProdi: Boolean get() = role == "admin_prodi"
    val isSuperAdmin: Boolean get() = role == "super_admin"

    val displayRole: String
        get() = when (role) {
            "mahasiswa"   -> "Mahasiswa"
            "dosen"       -> "Dosen"
            "admin_prodi" -> "Admin Prodi"
            "super_admin" -> "Super Admin"
            else          -> role.replaceFirstChar { it.uppercase() }
        }
}
