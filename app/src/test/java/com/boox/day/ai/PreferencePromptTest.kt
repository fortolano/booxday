package com.boox.day.ai

import com.boox.day.ai.intelligence.KeywordMatch
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferences.Companion.keptOptions
import com.boox.day.data.ai.EnergyPattern
import com.boox.day.data.ai.SpecialMode
import com.boox.day.data.ai.SuggestionCount
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the AI options put into AI requests (29 Oct 2026, after the evidence test): only how many
 * suggestions, Vacation, and the Health and Social switches; none of the removed AI Preferences
 * answers, even when an older install still has them saved. Also: real tasks in the completion rate,
 * whole-word keyword matching and every event of the day counted.
 */
class PreferencePromptTest {

    /** An older install's saved answers: everything the removed questions could set. */
    private val oldAnswers = AiPreferences(
        workPriority = 5, homePriority = 5, growthPriority = 5, financePriority = 5,
        hasKids = true, isCareGiver = true, isSocialButterfly = true,
        energyPattern = EnergyPattern.MORNING_PERSON, crunchMode = true, holidayMode = true,
        setupComplete = true
    )

    // ── Nothing is assumed ──

    @Test
    fun nothingIsAssumedAboutTheUser() {
        val lines = TaskAiContextBuilderV1.preferenceLines(null)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("not shared"))
        val guidance = TaskAiService.buildSuggestionPreferenceGuidance(AiPreferences(), targetCount = 6)
        assertFalse(guidance.contains("priorit", ignoreCase = true))
        assertTrue(guidance.contains("Target suggestion count: 6"))
    }

    @Test
    fun defaultsAreNotPresentedAsAnswers() {
        val text = TaskAiContextBuilderV1.preferenceLines(AiPreferences(setupComplete = true)).joinToString("\n")
        for (default in listOf("Working Professional", "Suburban", "Steady", "hybrid", "get organized")) {
            assertFalse("$default sent as an answer", text.contains(default, ignoreCase = true))
        }
    }

    // ── The removed answers reach no prompt ──

    @Test
    fun removedAnswersReachNoPrompt() {
        val userContext = TaskAiContextBuilderV1.preferenceLines(oldAnswers).joinToString("\n")
        val suggestions = TaskAiService.buildSuggestionPreferenceGuidance(oldAnswers, 6)
        for (text in listOf(userContext, suggestions)) {
            for (word in listOf("Work & Career", "Home & Organization", "Personal Growth", "Finance", "children", "caregiver", "morning", "Crunch", "Holiday", "social butterfly")) {
                assertFalse("$word in: $text", text.contains(word, ignoreCase = true))
            }
        }
        val plan = PreferenceInterpreter.interpret(oldAnswers)
        assertTrue(plan.priorityRanking.isEmpty())
        assertTrue(plan.lifestyleContext.isEmpty())
        assertNull(plan.bestFocusTime)
        assertNull("crunch and holiday are gone", plan.activeMode)
    }

    // ── The kept options reach every prompt ──

    @Test
    fun keptOptionsReachEveryPrompt() {
        val options = oldAnswers.copy(vacationMode = true, includeHealthReminders = false, includeSocialReminders = false)
        val userContext = TaskAiContextBuilderV1.preferenceLines(options).joinToString("\n")
        assertTrue(userContext.contains("Vacation mode: on"))
        assertTrue(userContext.contains("Health and wellness suggestions: off"))
        assertTrue(userContext.contains("Social suggestions: off"))
        val suggestions = TaskAiService.buildSuggestionPreferenceGuidance(options, 4)
        assertTrue(suggestions.contains("Target suggestion count: 4"))
        assertTrue(suggestions.contains("Vacation mode is active"))
        assertTrue(suggestions.contains("Avoid health suggestions"))
        assertTrue(suggestions.contains("Avoid social suggestions"))
        val plan = PreferenceInterpreter.interpret(options)
        assertEquals(SpecialMode.VACATION, plan.activeMode)
        assertFalse(plan.includeHealthReminders)
        assertFalse(plan.includeSocialReminders)
    }

    @Test
    fun onlyTheKeptOptionsAreSaved() {
        val kept = oldAnswers.copy(suggestionCount = SuggestionCount.MINIMAL, vacationMode = true).keptOptions()
        assertEquals(AiPreferences(suggestionCount = SuggestionCount.MINIMAL, vacationMode = true, setupComplete = true), kept)
    }

    @Test
    fun comprehensiveFitsTheAnswerFormat() {
        @Suppress("UNCHECKED_CAST")
        val suggestions = (StructuredOutputSchemas.suggestionsSchema["properties"] as Map<String, Any>)["suggestions"] as Map<String, Any>
        assertTrue((suggestions["maxItems"] as Int) >= SuggestionCount.COMPREHENSIVE.count)
    }

    // ── Optimize My Day: nothing invented ──

    @Test
    fun noInventedHoursOrBlockedTimes() {
        val parent = AiPreferences(hasKids = true, isCareGiver = true, hasPets = true, isActiveLifestyle = true,
            energyPattern = EnergyPattern.MORNING_PERSON, setupComplete = true)
        val constraints = PreferenceInterpreter.interpret(parent)
        assertTrue(constraints.blockedSlots.isEmpty())
        assertEquals(6, constraints.workHoursStart)
        assertEquals(22, constraints.workHoursEnd)
        assertNull("best focus time was removed", constraints.bestFocusTime)
    }

    @Test
    fun everyEventOfTheDayCounts() {
        val day = LocalDate.of(2026, 10, 27)
        fun event(h: Int, m: Int, minutes: Int): LocalEvent {
            val start = day.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return LocalEvent(title = "e$h", dayId = day.toString(), startTime = start, endTime = start + minutes * 60_000L)
        }
        val events = listOf(event(9, 0, 60), event(15, 0, 30), event(18, 0, 30), event(19, 30, 15))
        val constraints = PreferenceInterpreter.interpret(AiPreferences(energyPattern = EnergyPattern.MORNING_PERSON, setupComplete = true))
        val whole = CapacityAnalyzer.analyze(day, events, constraints)
        assertEquals(135, whole.committedMinutes)  // 60 + 30 + 30 + 15: the evening events count too
        // Today at 17:00: only what is left, and no free slot before now
        val fromFive = CapacityAnalyzer.analyze(day, events, constraints, fromMinute = 17 * 60)
        assertEquals(5 * 60, fromFive.totalMinutes)
        assertEquals(45, fromFive.committedMinutes)
        assertTrue(fromFive.freeSlots.all { it.startTime >= "17:00" })
    }

    @Test
    fun vacationAllowsAShortPlan() {
        assertEquals(1..3, AgenticPlanningService.proposalRange(SpecialMode.VACATION))
        assertEquals(3..7, AgenticPlanningService.proposalRange(null))
    }

    // ── Completion rate and matching ──

    @Test
    fun emptyRowsAreNotTasks() {
        fun row(i: Int, text: String? = null, done: Boolean = false, recognized: String? = null) =
            Task(dayDate = "2026-10-27", taskPageIndex = 0, rowIndex = i, pageId = "p", typedText = text,
                isCompleted = done, recognizedText = recognized)
        assertFalse(ExtendedContextLoaders.isRealTask(row(0)))
        assertTrue(ExtendedContextLoaders.isRealTask(row(1, text = "Pay bill")))
        assertTrue(ExtendedContextLoaders.isRealTask(row(2, recognized = "Call mom")))  // handwritten
        assertTrue(ExtendedContextLoaders.isRealTask(row(3, done = true)))
    }

    @Test
    fun keywordsMatchWholeWords() {
        assertFalse(KeywordMatch.contains("Team meeting at 10", "meet"))
        assertFalse(KeywordMatch.contains("Start quarterly report", "art"))
        assertFalse(KeywordMatch.contains("Buy bread", "read"))
        assertFalse(KeywordMatch.contains("New notebook", "book"))
        assertTrue(KeywordMatch.contains("Read two books", "book"))
        assertTrue(KeywordMatch.contains("Pick up the kids", "pick up"))
        assertTrue(KeywordMatch.contains("Pay the bills", "bill"))
    }
}
