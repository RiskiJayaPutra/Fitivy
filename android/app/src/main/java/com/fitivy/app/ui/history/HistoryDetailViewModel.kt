package com.fitivy.app.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.repository.ActivitySessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    private val activityRepository: ActivitySessionRepository
) : ViewModel() {

    private val _session = MutableLiveData<ActivitySessionEntity?>()
    val session: LiveData<ActivitySessionEntity?> = _session

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadSessionDetail(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val detail = activityRepository.getSession(sessionId)
                _session.value = detail
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
