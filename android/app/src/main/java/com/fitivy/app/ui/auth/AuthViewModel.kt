package com.fitivy.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitivy.app.data.remote.dto.LoginRequest
import com.fitivy.app.data.remote.dto.RegisterRequest
import com.fitivy.app.data.repository.AuthRepository
import com.fitivy.app.domain.model.AuthResult
import com.fitivy.app.domain.model.UserDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AuthViewModel — mengelola UI state untuk Login & Register screens.
 *
 * Menggunakan StateFlow (bukan LiveData) karena:
 *   - Null-safe by default (selalu punya initial value)
 *   - Coroutine-native, tidak perlu postValue() dari background thread
 *   - Compose-ready (collectAsState())
 *   - Lifecycle-aware saat di-collect di Fragment/Activity
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // =========================================================================
    // UI STATE
    // =========================================================================

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserDomain?>(null)
    val currentUser: StateFlow<UserDomain?> = _currentUser.asStateFlow()

    // =========================================================================
    // REGISTER
    // =========================================================================

    fun register(
        name: String,
        email: String,
        password: String,
        nimNip: String,
        gender: String? = null,
        birthDate: String? = null,
        heightCm: Float? = null,
        weightKg: Float? = null,
        deviceId: String? = null,
        deviceModel: String? = null,
        fcmToken: String? = null,
    ) {
        // Client-side validation sebelum hit API
        val validationError = validateRegistrationInput(name, email, password, nimNip)
        if (validationError != null) {
            _authState.value = AuthUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading

            val request = RegisterRequest(
                name        = name.trim(),
                email       = email.trim().lowercase(),
                password    = password,
                nimNip      = nimNip.trim(),
                gender      = gender,
                birthDate   = birthDate,
                heightCm    = heightCm,
                weightKg    = weightKg,
                deviceId    = deviceId,
                deviceModel = deviceModel,
                fcmToken    = fcmToken,
            )

            when (val result = authRepository.register(request)) {
                is AuthResult.Success -> {
                    _currentUser.value = result.user
                    _authState.value = AuthUiState.Success(result.user)
                }
                is AuthResult.Error -> {
                    _authState.value = AuthUiState.Error(result.message)
                }
                is AuthResult.LoggedOut -> {
                    // Tidak seharusnya terjadi saat register
                    _authState.value = AuthUiState.Error("Unexpected state")
                }
            }
        }
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    fun login(
        login: String,
        password: String,
        deviceId: String? = null,
        deviceModel: String? = null,
        fcmToken: String? = null,
    ) {
        // Client-side validation
        if (login.isBlank()) {
            _authState.value = AuthUiState.Error("Email atau NIM/NIP tidak boleh kosong")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthUiState.Error("Password tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading

            val request = LoginRequest(
                login       = login.trim(),
                password    = password,
                deviceId    = deviceId,
                deviceModel = deviceModel,
                fcmToken    = fcmToken,
            )

            when (val result = authRepository.login(request)) {
                is AuthResult.Success -> {
                    _currentUser.value = result.user
                    _authState.value = AuthUiState.Success(result.user)
                }
                is AuthResult.Error -> {
                    _authState.value = AuthUiState.Error(result.message)
                }
                is AuthResult.LoggedOut -> {
                    _authState.value = AuthUiState.Error("Unexpected state")
                }
            }
        }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading

            authRepository.logout()
            _currentUser.value = null
            _authState.value = AuthUiState.LoggedOut
        }
    }

    // =========================================================================
    // REFRESH TOKEN
    // =========================================================================

    fun refreshToken() {
        viewModelScope.launch {
            when (val result = authRepository.refreshToken()) {
                is AuthResult.Success -> {
                    _currentUser.value = result.user
                    // Tidak update UI state — refresh terjadi di background
                }
                is AuthResult.Error -> {
                    // Token refresh gagal — force logout
                    _currentUser.value = null
                    _authState.value = AuthUiState.LoggedOut
                }
                is AuthResult.LoggedOut -> {
                    _currentUser.value = null
                    _authState.value = AuthUiState.LoggedOut
                }
            }
        }
    }

    // =========================================================================
    // CHECK AUTH STATE — dipanggil saat app launch
    // =========================================================================

    fun checkAuthState() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (authRepository.isLoggedIn()) {
                _authState.value = AuthUiState.Loading

                when (val result = authRepository.getCurrentUser()) {
                    is AuthResult.Success -> {
                        _currentUser.value = result.user
                        _authState.value = AuthUiState.Success(result.user)
                    }
                    is AuthResult.Error -> {
                        // Token ada tapi tidak valid — clear dan redirect login
                        _authState.value = AuthUiState.LoggedOut
                    }
                    is AuthResult.LoggedOut -> {
                        _authState.value = AuthUiState.LoggedOut
                    }
                }
            } else {
                _authState.value = AuthUiState.LoggedOut
            }
        }
    }

    // =========================================================================
    // RESET STATE — dipanggil setelah error message ditampilkan
    // =========================================================================

    fun clearError() {
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    // =========================================================================
    // CLIENT-SIDE VALIDATION
    // =========================================================================

    private fun validateRegistrationInput(
        name: String,
        email: String,
        password: String,
        nimNip: String,
    ): String? {
        if (name.isBlank()) return "Nama tidak boleh kosong"
        if (email.isBlank()) return "Email tidak boleh kosong"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "Format email tidak valid"
        }
        if (password.length < 8) return "Password minimal 8 karakter"
        if (!password.any { it.isUpperCase() }) return "Password harus mengandung huruf besar"
        if (!password.any { it.isDigit() }) return "Password harus mengandung angka"
        if (nimNip.isBlank()) return "NIM/NIP tidak boleh kosong"
        if (!nimNip.all { it.isDigit() }) return "NIM/NIP harus berupa angka"
        return null
    }
}

// =============================================================================
// UI STATE — sealed class untuk representasi state UI
// =============================================================================

sealed class AuthUiState {
    /** State awal, belum ada aksi */
    data object Idle : AuthUiState()

    /** Sedang proses (API call) — tampilkan loading indicator */
    data object Loading : AuthUiState()

    /** Berhasil login/register — navigate ke dashboard */
    data class Success(val user: UserDomain) : AuthUiState()

    /** Error — tampilkan pesan error ke user */
    data class Error(val message: String) : AuthUiState()

    /** User sudah logout — navigate ke login screen */
    data object LoggedOut : AuthUiState()
}
