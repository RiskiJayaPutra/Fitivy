package com.fitivy.app.gamification

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BadgeEvaluatorTest {

    interface BadgeDao {
        fun hasBadge(userId: String, badgeId: String): Boolean
        fun awardBadge(userId: String, badgeId: String)
    }

    class BadgeEvaluator(private val badgeDao: BadgeDao) {
        fun evaluateSessionEnd(userId: String, totalStepsToday: Int, currentStreak: Int) {
            // Volume Badge
            if (totalStepsToday >= 10000 && !badgeDao.hasBadge(userId, "badge_10k")) {
                badgeDao.awardBadge(userId, "badge_10k")
            }
            
            // Streak Badge
            if (currentStreak >= 7 && !badgeDao.hasBadge(userId, "badge_streak_7")) {
                badgeDao.awardBadge(userId, "badge_streak_7")
            }
        }
    }

    private lateinit var badgeDao: BadgeDao
    private lateinit var evaluator: BadgeEvaluator

    @BeforeEach
    fun setup() {
        badgeDao = mockk(relaxed = true)
        evaluator = BadgeEvaluator(badgeDao)
    }

    @Test
    fun `evaluator awards 10k badge if steps reach 10000`() {
        every { badgeDao.hasBadge(any(), "badge_10k") } returns false

        evaluator.evaluateSessionEnd("user-1", 10500, 1)

        verify(exactly = 1) { badgeDao.awardBadge("user-1", "badge_10k") }
    }

    @Test
    fun `evaluator does not award 10k badge if already awarded`() {
        every { badgeDao.hasBadge(any(), "badge_10k") } returns true

        evaluator.evaluateSessionEnd("user-1", 15000, 1)

        verify(exactly = 0) { badgeDao.awardBadge("user-1", "badge_10k") }
    }

    @Test
    fun `evaluator awards streak badge if streak reaches 7`() {
        every { badgeDao.hasBadge(any(), "badge_streak_7") } returns false

        evaluator.evaluateSessionEnd("user-1", 5000, 7)

        verify(exactly = 1) { badgeDao.awardBadge("user-1", "badge_streak_7") }
    }
}
