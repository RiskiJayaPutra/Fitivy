package com.fitivy.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitivy.app.data.local.TokenManager
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.repository.ActivitySessionRepository
import com.fitivy.app.sensor.PassiveStepTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DailySteps(val timestamp: Long, val dayName: String, val steps: Int)
data class WeeklyAvg(val timestamp: Long, val weekLabel: String, val avgSteps: Int)
data class ActivityRatio(val walking: Float = 0f, val running: Float = 0f, val cycling: Float = 0f)

data class DashboardUiState(
    val isLoading: Boolean = true,

    // Today Summary
    val todaySteps: Int = 0,
    val todayTarget: Int = 10000,
    val todayCalories: Double = 0.0,
    val todayDuration: Long = 0,
    val todayDistance: Double = 0.0,

    // Charts Data
    val weeklyData: List<DailySteps> = emptyList(),
    val monthlyData: List<WeeklyAvg> = emptyList(),
    val activityRatio: ActivityRatio = ActivityRatio(),

    // Gamification
    val currentStreak: Int = 0,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityRepository: ActivitySessionRepository,
    private val tokenManager: TokenManager,
    private val passiveStepTracker: PassiveStepTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        passiveStepTracker.startListening()
        loadRealData()
    }

    private fun loadRealData() {
        val userId = tokenManager.getUserId() ?: return
        
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = getStartOfDay(now) - (86400000L * 30)

        viewModelScope.launch {
            combine(
                passiveStepTracker.passiveStepsToday,
                activityRepository.observeTodayCalories(userId),
                activityRepository.observeTodayDistance(userId),
                activityRepository.observeTodaySessions(userId),
                activityRepository.observeSessionsBetweenDates(userId, thirtyDaysAgo, now)
            ) { passiveSteps, calories, distance, todaySessions, allSessions ->
                val duration = todaySessions.sumOf { it.durationSeconds }
                // Langkah hari ini digabungkan (passive steps + hitungan manual jika tidak overlap).
                // Karena passive steps berasal dari TYPE_STEP_COUNTER hardware, nilainya sudah mencakup langkah saat sesi aktif.
                
                val activeSteps = todaySessions.sumOf { it.totalSteps }
                val passiveOnlySteps = (passiveSteps - activeSteps).coerceAtLeast(0)
                
                // Estimasi jarak langkah pasif: rata-rata 0.762 meter per langkah
                val passiveDistance = passiveOnlySteps * 0.762
                val totalDistance = distance + passiveDistance
                
                // Estimasi kalori langkah pasif: langkah * 65kg * 0.0005
                val passiveCalories = passiveOnlySteps * 65.0 * 0.0005
                val totalCalories = calories + passiveCalories
                
                DashboardUiState(
                    isLoading = false,
                    todaySteps = passiveSteps,
                    todayTarget = 10000,
                    todayCalories = totalCalories,
                    todayDuration = duration,
                    todayDistance = totalDistance,
                    weeklyData = calculateWeeklyData(allSessions),
                    monthlyData = calculateMonthlyData(allSessions),
                    activityRatio = calculateActivityRatio(allSessions),
                    currentStreak = calculateStreak(allSessions, 10000)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateActivityRatio(sessions: List<ActivitySessionEntity>): ActivityRatio {
        if (sessions.isEmpty()) return ActivityRatio()
        
        val total = sessions.size.toFloat()
        val walking = sessions.count { it.activityType == "walking" } / total
        val running = sessions.count { it.activityType == "running" } / total
        val cycling = sessions.count { it.activityType == "cycling" } / total
        
        return ActivityRatio(walking, running, cycling)
    }

    private fun calculateStreak(sessions: List<ActivitySessionEntity>, target: Int): Int {
        val stepsByDay = sessions.groupBy { getStartOfDay(it.startedAt) }
            .mapValues { entry -> entry.value.sumOf { it.totalSteps } }
        
        var streak = 0
        var currentDay = getStartOfDay(System.currentTimeMillis())
        
        while (true) {
            val steps = stepsByDay[currentDay] ?: 0
            if (steps >= target) {
                streak++
                currentDay -= 86400000L
            } else {
                break
            }
        }
        return streak
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun calculateWeeklyData(sessions: List<ActivitySessionEntity>): List<DailySteps> {
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val days = mutableListOf<DailySteps>()
        val formatter = java.text.SimpleDateFormat("EEE", java.util.Locale("id", "ID"))
        
        for (i in 6 downTo 0) {
            val dayStart = todayStart - (i * 86400000L)
            val dayEnd = dayStart + 86400000L
            val daySteps = sessions.filter { it.startedAt in dayStart until dayEnd }.sumOf { it.totalSteps }
            
            // Format hari: Sen, Sel, dsb. Jika hari ini, gunakan "Hri Ini"
            val dayName = if (i == 0) "Hari Ini" else formatter.format(java.util.Date(dayStart))
            days.add(DailySteps(dayStart, dayName, daySteps))
        }
        return days
    }

    private fun calculateMonthlyData(sessions: List<ActivitySessionEntity>): List<WeeklyAvg> {
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val weeks = mutableListOf<WeeklyAvg>()
        
        // 4 Minggu terakhir
        for (i in 3 downTo 0) {
            val weekStart = todayStart - (i * 7 * 86400000L) - (6 * 86400000L)
            val weekEnd = todayStart - (i * 7 * 86400000L) + 86400000L
            
            val weekSessions = sessions.filter { it.startedAt in weekStart until weekEnd }
            
            // Avg per minggu = total step dibagi jumlah hari (7)
            val totalSteps = weekSessions.sumOf { it.totalSteps }
            val avg = totalSteps / 7
            
            weeks.add(WeeklyAvg(weekStart, "M-${4-i}", avg))
        }
        return weeks
    }
}
