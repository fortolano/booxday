package com.boox.day.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTaskRuleEvaluatorTest {

    @Test
    fun dailyRule_respectsAnchorAndInterval() {
        val template = RecurringTaskTemplate(
            title = "Daily review",
            recurrenceType = "DAILY",
            intervalDays = 2,
            anchorDate = "2026-03-20"
        )

        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-20"))
        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-21"))
        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-22"))
    }

    @Test
    fun weeklyRule_usesAnchorWeekday() {
        val template = RecurringTaskTemplate(
            title = "Weekly planning",
            recurrenceType = "WEEKLY",
            anchorDate = "2026-03-20"
        )

        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-27"))
        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-26"))
    }

    @Test
    fun customWeekdaysRule_respectsSelectedDaysAndAnchorLowerBound() {
        val template = RecurringTaskTemplate(
            title = "Gym",
            recurrenceType = "CUSTOM_WEEKDAYS",
            daysOfWeek = "[1,3,5]",
            anchorDate = "2026-03-20"
        )

        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-18"))
        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-20"))
        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-23"))
        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-03-24"))
    }

    @Test
    fun monthlyRule_matchesConfiguredDayOfMonth() {
        val template = RecurringTaskTemplate(
            title = "Pay rent",
            recurrenceType = "MONTHLY",
            dayOfMonth = 15,
            anchorDate = "2026-03-15"
        )

        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-04-15"))
        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, "2026-04-16"))
    }

    @Test
    fun inactiveAndEndedTemplates_doNotGenerate() {
        val inactiveTemplate = RecurringTaskTemplate(
            title = "Paused habit",
            recurrenceType = "DAILY",
            anchorDate = "2026-03-20",
            isActive = false
        )
        val endedTemplate = RecurringTaskTemplate(
            title = "Ended habit",
            recurrenceType = "DAILY",
            anchorDate = "2026-03-20",
            endDate = "2026-03-25"
        )

        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(inactiveTemplate, "2026-03-20"))
        assertFalse(RecurringTaskRuleEvaluator.shouldGenerateOnDate(endedTemplate, "2026-03-26"))
        assertTrue(RecurringTaskRuleEvaluator.shouldGenerateOnDate(endedTemplate, "2026-03-25"))
    }
}
