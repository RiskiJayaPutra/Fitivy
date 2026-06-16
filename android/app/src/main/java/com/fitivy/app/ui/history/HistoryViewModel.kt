package com.fitivy.app.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitivy.app.data.local.TokenManager
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.repository.ActivitySessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val activityRepository: ActivitySessionRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _historyList = MutableLiveData<List<ActivitySessionEntity>>()
    val historyList: LiveData<List<ActivitySessionEntity>> = _historyList

    private val _totalDistance = MutableLiveData<Double>(0.0)
    val totalDistance: LiveData<Double> = _totalDistance

    private val _totalCalories = MutableLiveData<Double>(0.0)
    val totalCalories: LiveData<Double> = _totalCalories

    private val _totalSessions = MutableLiveData<Int>(0)
    val totalSessions: LiveData<Int> = _totalSessions

    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val currentUserId: String? = tokenManager.getUserId()

    init {
        loadHistory()
    }

    fun loadHistory() {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                activityRepository.observeAllByUser(userId).collectLatest { sessions ->
                    // SessionStatus.COMPLETED = "completed" (lowercase) — sesuai Room entity
                    val validSessions = sessions.filter { it.status == "completed" }
                    _historyList.value = validSessions
                    calculateTotals(validSessions)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun filterHistoryByDate(startMs: Long, endMs: Long) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                activityRepository.observeSessionsBetweenDates(userId, startMs, endMs).collectLatest { sessions ->
                    _historyList.value = sessions
                    calculateTotals(sessions)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun calculateTotals(sessions: List<ActivitySessionEntity>) {
        _totalSessions.value = sessions.size
        _totalDistance.value = sessions.sumOf { it.distanceMeters }
        _totalCalories.value = sessions.sumOf { it.caloriesBurned }
    }


}
