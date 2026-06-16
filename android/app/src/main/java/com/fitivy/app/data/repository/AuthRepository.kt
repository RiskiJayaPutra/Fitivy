package com.fitivy.app.data.repository

import com.fitivy.app.data.local.TokenManager
import com.fitivy.app.domain.model.AuthResult
import com.fitivy.app.domain.model.UserDomain
import com.fitivy.app.data.remote.dto.LoginRequest
import com.fitivy.app.data.remote.dto.RegisterRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepository — single source of truth untuk auth operations.
 *
 * Responsibilities:
 *   1. Memanggil Retrofit API
 *   2. Parse response (success & error)
 *   3. Simpan/hapus token di encrypted storage
 *   4. Map DTO → domain model
 *
 * KENAPA ada try-catch di setiap method?
 *   - Network call bisa throw IOException (no internet)
 *   - Retrofit bisa throw HttpException
 *   - JSON parsing bisa throw JsonSyntaxException
 *   - Semua di-catch dan di-wrap jadi AuthResult.Error agar ViewModel clean
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val tokenManager: TokenManager,
) {

    // =========================================================================
    // REGISTER
    // =========================================================================

    // =========================================================================
    // REGISTER
    // =========================================================================

    suspend fun register(request: RegisterRequest): AuthResult {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(request.email, request.password).await()
            val user = authResult.user ?: return AuthResult.Error("Gagal mendapatkan data user dari Firebase")

            // Simpan data tambahan ke Firestore
            val userMap = hashMapOf(
                "id" to user.uid,
                "name" to request.name,
                "email" to request.email,
                "nimNip" to request.nimNip,
                "role" to "student",
                "isActive" to true
            )

            firestore.collection("users").document(user.uid).set(userMap).await()

            val token = user.getIdToken(true).await().token ?: ""

            // Simpan ke local storage
            tokenManager.saveToken(token)
            tokenManager.saveUserInfo(
                id = user.uid,
                name = request.name,
                email = request.email,
                role = "student"
            )

            val userDomain = UserDomain(
                id = user.uid,
                name = request.name,
                email = request.email,
                nimNip = request.nimNip,
                role = "student",
                isActive = true,
                avatarUrl = null,
                heightCm = null,
                weightKg = null,
                birthDate = null,
                gender = null
            )

            AuthResult.Success(userDomain, token)

        } catch (e: Exception) {
            AuthResult.Error("Registrasi gagal: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    suspend fun login(request: LoginRequest): AuthResult {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(request.login, request.password).await()
            val user = authResult.user ?: return AuthResult.Error("Login gagal")

            // Ambil data user dari Firestore
            val doc = firestore.collection("users").document(user.uid).get().await()

            val name = doc.getString("name") ?: "User"
            val role = doc.getString("role") ?: "student"
            val nimNip = doc.getString("nimNip") ?: ""

            val token = user.getIdToken(true).await().token ?: ""

            tokenManager.saveToken(token)
            tokenManager.saveUserInfo(
                id = user.uid,
                name = name,
                email = user.email ?: "",
                role = role
            )

            val userDomain = UserDomain(
                id = user.uid,
                name = name,
                email = user.email ?: "",
                nimNip = nimNip,
                role = role,
                isActive = true,
                avatarUrl = null,
                heightCm = null,
                weightKg = null,
                birthDate = null,
                gender = null
            )

            AuthResult.Success(userDomain, token)

        } catch (e: Exception) {
            AuthResult.Error("Login gagal: Email atau password salah.")
        }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    suspend fun logout(): AuthResult {
        return try {
            firebaseAuth.signOut()
            tokenManager.clearAll()
            AuthResult.LoggedOut
        } catch (e: Exception) {
            tokenManager.clearAll()
            AuthResult.LoggedOut
        }
    }

    // =========================================================================
    // REFRESH TOKEN
    // =========================================================================

    suspend fun refreshToken(): AuthResult {
        val user = firebaseAuth.currentUser ?: return AuthResult.LoggedOut
        return try {
            val token = user.getIdToken(true).await().token ?: ""
            tokenManager.saveToken(token)

            val doc = firestore.collection("users").document(user.uid).get().await()
            val name = doc.getString("name") ?: "User"
            val role = doc.getString("role") ?: "student"
            val nimNip = doc.getString("nimNip") ?: ""

            val userDomain = UserDomain(
                id = user.uid,
                name = name,
                email = user.email ?: "",
                nimNip = nimNip,
                role = role,
                isActive = true,
                avatarUrl = null,
                heightCm = null,
                weightKg = null,
                birthDate = null,
                gender = null
            )

            AuthResult.Success(userDomain, token)
        } catch (e: Exception) {
            tokenManager.clearAll()
            AuthResult.Error("Sesi telah berakhir. Silakan login kembali.")
        }
    }

    // =========================================================================
    // GET CURRENT USER
    // =========================================================================

    suspend fun getCurrentUser(): AuthResult {
        val user = firebaseAuth.currentUser ?: return AuthResult.LoggedOut
        return try {
            val doc = firestore.collection("users").document(user.uid).get().await()
            val name = doc.getString("name") ?: "User"
            val role = doc.getString("role") ?: "student"
            val nimNip = doc.getString("nimNip") ?: ""

            val userDomain = UserDomain(
                id = user.uid,
                name = name,
                email = user.email ?: "",
                nimNip = nimNip,
                role = role,
                isActive = true,
                avatarUrl = null,
                heightCm = null,
                weightKg = null,
                birthDate = null,
                gender = null
            )

            AuthResult.Success(userDomain, tokenManager.getToken() ?: "")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    // =========================================================================
    // LOCAL STATE
    // =========================================================================

    // =========================================================================
    // UPDATE PROFILE
    // =========================================================================

    suspend fun updateProfile(weightKg: Double?, heightCm: Double?): AuthResult {
        val user = firebaseAuth.currentUser ?: return AuthResult.LoggedOut
        return try {
            val updates = mutableMapOf<String, Any>()
            if (weightKg != null) updates["weightKg"] = weightKg
            if (heightCm != null) updates["heightCm"] = heightCm

            if (updates.isNotEmpty()) {
                firestore.collection("users").document(user.uid).update(updates).await()
            }
            
            // Re-fetch to return updated domain model
            getCurrentUser()
        } catch (e: Exception) {
            AuthResult.Error("Gagal menyimpan profil: ${e.localizedMessage}")
        }
    }

    // =========================================================================
    // LOCAL STATE
    // =========================================================================

    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    fun getCachedUserRole(): String? = tokenManager.getUserRole()
}
