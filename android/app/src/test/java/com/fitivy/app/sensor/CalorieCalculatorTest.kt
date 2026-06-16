package com.fitivy.app.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * TDD: Test untuk CalorieCalculator (akan diimplementasikan nanti).
 * Formula dasar: Langkah x Berat Badan x Faktor Aktivitas
 */
class CalorieCalculatorTest {

    // Dummy implementation for the sake of compiling test, in real scenario this would be a real class.
    class CalorieCalculator {
        fun calculate(steps: Int, weightKg: Double, activityType: String): Double {
            val factor = when(activityType) {
                "running" -> 0.0007
                "cycling" -> 0.0006
                else -> 0.0005 // walking
            }
            return steps * weightKg * factor
        }
    }

    private val calculator = CalorieCalculator()

    @ParameterizedTest
    @CsvSource(
        "1000, 60.0, walking, 30.0",
        "5000, 75.0, walking, 187.5",
        "10000, 50.0, running, 350.0",
        "0, 80.0, cycling, 0.0"
    )
    fun `calculate returns correct calories based on steps, weight, and activity type`(
        steps: Int, weight: Double, type: String, expected: Double
    ) {
        val result = calculator.calculate(steps, weight, type)
        assertEquals(expected, result, 0.1)
    }

    @Test
    fun `calculate handles zero weight gracefully`() {
        val result = calculator.calculate(1000, 0.0, "walking")
        assertEquals(0.0, result, 0.1)
    }
}
