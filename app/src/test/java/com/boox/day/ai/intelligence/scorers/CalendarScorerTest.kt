package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarContext
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarEvent
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.data.ai.CalendarEventWithDate
import com.boox.day.data.ai.DeadlineEvent
import com.boox.day.data.ai.DeadlineUrgency
import com.boox.day.data.ai.ExtendedCalendarContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalendarScorerTest {

    private lateinit var scorer: CalendarScorer

    @Before
    fun setup() {
        scorer = CalendarScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREP TASK SCORING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `prep task for today event scores 0_90`() = runBlocking {
        val calendarContext = createCalendarContext(
            todayEvents = listOf(
                createCalendarEvent("Client meeting presentation", daysFromToday = 0)
            )
        )
        val task = createTask(text = "Prepare slides for client meeting")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.90f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Prep for") && it.contains("TODAY") })
    }

    @Test
    fun `prep task for tomorrow event scores 0_80`() = runBlocking {
        val calendarContext = createCalendarContext(
            tomorrowEvents = listOf(
                createCalendarEvent("Team standup review", daysFromToday = 1)
            )
        )
        val task = createTask(text = "Prepare notes for team standup")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.80f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("tomorrow") })
    }

    @Test
    fun `prep task for event in 2 days scores 0_65`() = runBlocking {
        val calendarContext = createCalendarContext(
            thisWeekEvents = listOf(
                createCalendarEvent("Board meeting", daysFromToday = 2)
            )
        )
        val task = createTask(text = "Prepare report for board meeting")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.65f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `prep task for event in 3 days scores 0_50`() = runBlocking {
        val calendarContext = createCalendarContext(
            thisWeekEvents = listOf(
                createCalendarEvent("Conference call", daysFromToday = 3)
            )
        )
        val task = createTask(text = "Research for conference call")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.50f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FOLLOW-UP TASK SCORING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `follow-up task for yesterday event scores 0_75`() = runBlocking {
        val calendarContext = createCalendarContext(
            recentPastEvents = listOf(
                createCalendarEvent("Client call discussion", daysFromToday = -1)
            )
        )
        val task = createTask(text = "Send follow-up email after client call")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Follow-up") })
    }

    @Test
    fun `follow-up task for event 2-3 days ago scores 0_60`() = runBlocking {
        val calendarContext = createCalendarContext(
            recentPastEvents = listOf(
                createCalendarEvent("Strategy session", daysFromToday = -3)
            )
        )
        val task = createTask(text = "Document notes from strategy session")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0.60f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO MATCH TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task without event match scores 0`() = runBlocking {
        val calendarContext = createCalendarContext(
            todayEvents = listOf(
                createCalendarEvent("Dentist appointment", daysFromToday = 0)
            )
        )
        val task = createTask(text = "Buy groceries from store")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        assertEquals(0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO CALENDAR TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `no calendar context returns zero scores`() = runBlocking {
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = null)

        val result = scorer.score(input)

        assertEquals(0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // APPLICABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isApplicable returns true with calendar events`() {
        val calendarContext = createCalendarContext(
            todayEvents = listOf(createCalendarEvent("Meeting", daysFromToday = 0))
        )
        val input = createScoringInput(emptyList(), calendarContext = calendarContext)

        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false without calendar`() {
        val input = createScoringInput(emptyList(), calendarContext = null)

        assertFalse(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false with empty calendar`() {
        val calendarContext = createCalendarContext()  // Empty events
        val input = createScoringInput(emptyList(), calendarContext = calendarContext)

        assertFalse(scorer.isApplicable(input))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BEST MATCH SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `closest event match is selected when multiple match`() = runBlocking {
        val calendarContext = createCalendarContext(
            todayEvents = listOf(
                createCalendarEvent("Meeting with John", daysFromToday = 0)
            ),
            thisWeekEvents = listOf(
                createCalendarEvent("Meeting with team", daysFromToday = 3)
            )
        )
        val task = createTask(text = "Prepare notes for meeting")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), calendarContext = calendarContext)

        val result = scorer.score(input)

        // Should match today's event (closer) with score 0.90
        assertEquals(0.90f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("TODAY") })
    }
}
