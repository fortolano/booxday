package com.boox.day.ai

import com.boox.day.data.ai.PreferenceConstraints
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AgenticContextBuilderTest {

    @Test
    fun `resolvePlanningHour keeps current hour when planning today`() {
        val preferences = PreferenceConstraints.default().copy(workHoursStart = 8)
        val today = LocalDate.of(2026, 3, 18)

        val hour = AgenticContextBuilder.resolvePlanningHour(
            planningDate = today,
            preferences = preferences,
            today = today,
            currentHour = 16
        )

        assertEquals(16, hour)
    }

    @Test
    fun `resolvePlanningHour uses work start for future day`() {
        val preferences = PreferenceConstraints.default().copy(workHoursStart = 8)
        val today = LocalDate.of(2026, 3, 18)

        val hour = AgenticContextBuilder.resolvePlanningHour(
            planningDate = today.plusDays(2),
            preferences = preferences,
            today = today,
            currentHour = 16
        )

        assertEquals(8, hour)
    }
}
