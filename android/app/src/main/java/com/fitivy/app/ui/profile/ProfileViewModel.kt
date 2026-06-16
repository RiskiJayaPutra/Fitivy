package com.fitivy.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitivy.app.domain.model.AuthResult
import com.fitivy.app.domain.model.UserDomain
import com.fitivy.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableLiveData<UserDomain?>()
    val user: LiveData<UserDomain?> = _user

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _saveSuccess = MutableLiveData<Boolean>(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = authRepository.getCurrentUser()) {
                is AuthResult.Success -> {
                    _user.value = result.user
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.LoggedOut -> {
                    _user.value = null
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun saveProfile(weightKgStr: String, heightCmStr: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _saveSuccess.value = false
            
            val weight = weightKgStr.toDoubleOrNull()
            val height = heightCmStr.toDoubleOrNull()

            when (val result = authRepository.updateProfile(weight, height)) {
                is AuthResult.Success -> {
                    _user.value = result.user
                    _saveSuccess.value = true
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _saveSuccess.value = false
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
