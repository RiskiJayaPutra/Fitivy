package com.fitivy.app.ui.dashboard

import com.fitivy.app.data.local.TokenManager
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.data.repository.ActivitySessionRepository
import com.fitivy.app.sensor.PassiveStepTracker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var repository: ActivitySessionRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var passiveStepTracker: PassiveStepTracker
    private lateinit var viewModel: DashboardViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        passiveStepTracker = mockk(relaxed = true)

        every { tokenManager.getUserId() } returns "user-123"
        every { passiveStepTracker.passiveStepsToday } returns kotlinx.coroutines.flow.MutableStateFlow(5000)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDashboardData updates state correctly for today summary`() = runTest {
        // Mock Repository responses
        every { repository.observeTodaySteps(any()) } returns flowOf(5000)
        every { repository.observeTodayCalories(any()) } returns flowOf(250.0)
        every { repository.observeTodayDistance(any()) } returns flowOf(3500.0)
        
        val todaySessions = listOf(
            createMockSession(System.currentTimeMillis(), "walking", 2000, 1800),
            createMockSession(System.currentTimeMillis(), "running", 3000, 1200)
        )
        every { repository.observeTodaySessions(any()) } returns flowOf(todaySessions)
        every { repository.observeSessionsBetweenDates(any(), any(), any()) } returns flowOf(emptyList())

        viewModel = DashboardViewModel(repository, tokenManager, passiveStepTracker)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(5000, state.todaySteps)
        assertEquals(250.0, state.todayCalories, 0.0)
        assertEquals(3500.0, state.todayDistance, 0.0)
        assertEquals(3000L, state.todayDuration) // 1800 + 1200
    }

    @Test
    fun `streak is calculated correctly`() = runTest {
        // Setup past 3 days meeting the target (10000 steps)
        val today = getStartOfDay(System.currentTimeMillis())
        val yesterday = today - 86400000L
        val twoDaysAgo = today - 86400000L * 2
        val threeDaysAgo = today - 86400000L * 3

        val sessions = listOf(
            createMockSession(today, "walking", 10000, 3600),
            createMockSession(yesterday, "running", 12000, 3600),
            createMockSession(twoDaysAgo, "cycling", 11000, 3600),
            // Missed 3 days ago
            createMockSession(threeDaysAgo, "walking", 5000, 3600)
        )

        every { repository.observeTodaySteps(any()) } returns flowOf(10000)
        every { repository.observeTodayCalories(any()) } returns flowOf(0.0)
        every { repository.observeTodayDistance(any()) } returns flowOf(0.0)
        every { repository.observeTodaySessions(any()) } returns flowOf(emptyList())
        
        every { repository.observeSessionsBetweenDates(any(), any(), any()) } returns flowOf(sessions)

        viewModel = DashboardViewModel(repository, tokenManager, passiveStepTracker)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.currentStreak) // Today, yesterday, 2 days ago = 3
    }

    @Test
    fun `streak breaks if yesterday missed target`() = runTest {
        val today = getStartOfDay(System.currentTimeMillis())
        val yesterday = today - 86400000L
        val twoDaysAgo = today - 86400000L * 2

        val sessions = listOf(
            createMockSession(today, "walking", 10000, 3600),
            createMockSession(yesterday, "running", 5000, 3600), // Missed
            createMockSession(twoDaysAgo, "cycling", 11000, 3600)
        )

        every { repository.observeTodaySteps(any()) } returns flowOf(10000)
        every { repository.observeTodayCalories(any()) } returns flowOf(0.0)
        every { repository.observeTodayDistance(any()) } returns flowOf(0.0)
        every { repository.observeTodaySessions(any()) } returns flowOf(emptyList())
        
        every { repository.observeSessionsBetweenDates(any(), any(), any()) } returns flowOf(sessions)

        viewModel = DashboardViewModel(repository, tokenManager, passiveStepTracker)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentStreak) // Only today
    }

    @Test
    fun `activity ratio calculated correctly`() = runTest {
        val sessions = listOf(
            createMockSession(System.currentTimeMillis(), "walking", 1000, 600),
            createMockSession(System.currentTimeMillis(), "walking", 1000, 600),
            createMockSession(System.currentTimeMillis(), "running", 1000, 600),
            createMockSession(System.currentTimeMillis(), "cycling", 1000, 600)
        )
        
        every { repository.observeTodaySteps(any()) } returns flowOf(0)
        every { repository.observeTodayCalories(any()) } returns flowOf(0.0)
        every { repository.observeTodayDistance(any()) } returns flowOf(0.0)
        every { repository.observeTodaySessions(any()) } returns flowOf(emptyList())
        every { repository.observeSessionsBetweenDates(any(), any(), any()) } returns flowOf(sessions)

        viewModel = DashboardViewModel(repository, tokenManager, passiveStepTracker)
        advanceUntilIdle()

        val ratio = viewModel.uiState.value.activityRatio
        assertEquals(0.5f, ratio.walking, 0.01f) // 2/4
        assertEquals(0.25f, ratio.running, 0.01f) // 1/4
        assertEquals(0.25f, ratio.cycling, 0.01f) // 1/4
    }

    private fun createMockSession(timestamp: Long, type: String, steps: Int, duration: Long): ActivitySessionEntity {
        return ActivitySessionEntity(
            id = UUID.randomUUID().toString(),
            userId = "user-123",
            startedAt = timestamp,
            activityType = type,
            totalSteps = steps,
            durationSeconds = duration
        )
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
}
