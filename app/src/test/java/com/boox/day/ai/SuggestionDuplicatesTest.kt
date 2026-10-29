package com.boox.day.ai

import com.boox.day.ai.intelligence.IntelligenceTestUtils
import com.boox.day.data.ai.ExtendedTaskContext
import com.boox.day.data.ai.TaskWithDate
import com.boox.day.data.db.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart Suggestions and the tasks the user already has (29 Oct 2026, Go 10.3: "Call mom" and
 * "Send mockups to Sam" were suggested while both were on the day's list, handwritten and ticked).
 */
class SuggestionDuplicatesTest {

    private fun handwritten(words: String?, done: Boolean = false, id: String = "t-${words.hashCode()}"): Task =
        IntelligenceTestUtils.createTask(id = id, isCompleted = done).copy(typedText = null, recognizedText = words)

    private fun withDate(task: Task, text: String, days: Int) = TaskWithDate(
        task = task, date = IntelligenceTestUtils.TODAY.plusDays(days.toLong()), text = text,
        isCompleted = task.isCompleted, priority = task.priority, daysFromToday = days
    )

    // ─── Where a task's words come from ─────────────────────────────────────

    @Test
    fun `a ticked handwritten task gets the words BooxDay stored`() {
        assertEquals("Call mom", ExtendedContextLoaders.taskText(handwritten("Call mom", done = true), emptyMap()))
    }

    @Test
    fun `words come from the reading just now, then typed, converted and stored text`() {
        val task = IntelligenceTestUtils.createTask(id = "t1", text = "Typed").copy(convertedText = "Converted", recognizedText = "Stored")
        assertEquals("Read now", ExtendedContextLoaders.taskText(task, mapOf("t1" to "Read now")))
        assertEquals("Typed", ExtendedContextLoaders.taskText(task, emptyMap()))
        assertEquals("Converted", ExtendedContextLoaders.taskText(task.copy(typedText = " "), emptyMap()))
        assertEquals("Stored", ExtendedContextLoaders.taskText(task.copy(typedText = null, convertedText = null), mapOf("t1" to "")))
        assertEquals("", ExtendedContextLoaders.taskText(handwritten(null), emptyMap()))
    }

    // ─── The prompt's task lists ────────────────────────────────────────────

    private val context = ExtendedTaskContext(
        recentCompletedTasks = listOf(withDate(handwritten("Pay taxes", done = true), "Pay taxes", -3)),
        recentIncompleteTasks = listOf(withDate(handwritten("Renew passport"), "Renew passport", -2)),
        todayTasks = listOf(
            withDate(handwritten("Call mom", done = true), "Call mom", 0),
            withDate(handwritten(null, done = true), "", 0),
            withDate(handwritten("Finish slides  for Friday"), "Finish slides  for Friday", 0)
        ),
        upcomingTasks = listOf(
            withDate(handwritten("Prepare release notes"), "Prepare release notes", 3),
            withDate(handwritten("call MOM"), "call MOM", 4)
        ),
        overdueCount = 1,
        completionRateLast7Days = 0.5f
    )

    @Test
    fun `tasks to avoid are today's (ticked too), overdue and upcoming, each once`() {
        assertEquals(
            listOf("Call mom", "Finish slides for Friday", "Renew passport", "Prepare release notes"),
            SuggestionDuplicates.tasksToAvoid(context)
        )
    }

    @Test
    fun `the task section lists no task without words`() {
        val section = SuggestionContextBuilder.buildTaskScopeSection(context)
        assertTrue(section, section.contains("- [x] Call mom"))
        assertTrue(section, section.contains("- [-3d] Pay taxes"))
        assertFalse(section, section.lines().any { it.trim() == "- [x]" || it.trim() == "- [o]" })
        // The count still says how many tasks the day has
        assertTrue(section, section.contains("Today (3):"))
    }

    // ─── The last check on the answer ───────────────────────────────────────

    @Test
    fun `repeats of a task are caught`() {
        assertTrue(SuggestionDuplicates.repeats("Call mom", "Call mom"))
        assertTrue(SuggestionDuplicates.repeats("Call Mom!", "call mom"))
        assertTrue(SuggestionDuplicates.repeats("Call mom", "Call mom at 3PM"))
        assertTrue(SuggestionDuplicates.repeats("Call Mom at 3pm", "call mom at 3PM"))
        assertTrue(SuggestionDuplicates.repeats("Send mockups to Sam during or after lunch", "Send mockups to Sam"))
        assertTrue(SuggestionDuplicates.repeats("Email Johnny", "Email Johnny the ClickUp adoption summary"))
        assertTrue(SuggestionDuplicates.repeats("Buy milk", "Buy milk and eggs"))
        assertTrue(SuggestionDuplicates.repeats("Réserver le café", "reserver le cafe"))
    }

    @Test
    fun `follow-ups and other tasks are kept`() {
        // A follow-up with more than two new words
        assertFalse(SuggestionDuplicates.repeats("Call mom about the Lisbon trip dates", "Call mom"))
        assertFalse(SuggestionDuplicates.repeats("Review tax documents to pay pending taxes", "Pay taxes"))
        // Shared words only
        assertFalse(SuggestionDuplicates.repeats("Review slides with Priya before Friday", "Finish slides for Friday"))
        assertFalse(SuggestionDuplicates.repeats("Review the ClickUp adoption summary before 15:00", "Email Johnny the ClickUp adoption summary"))
        // One word is too little to call a repeat, unless it's the whole task
        assertFalse(SuggestionDuplicates.repeats("Gym", "Gym session at 6"))
        assertFalse(SuggestionDuplicates.repeats("Buy konks", "Konks"))
        assertTrue(SuggestionDuplicates.repeats("Konks", "konks"))
        // Small words alone don't make a repeat
        assertFalse(SuggestionDuplicates.repeats("Go to the gym", "Go to the bank"))
        assertFalse(SuggestionDuplicates.repeats("", "Call mom"))
    }

    @Test
    fun `only repeats are dropped from the answer`() {
        val suggestions = listOf(
            "Call mom", "Send mockups to Sam during or after lunch", "Print boarding passes for the Lisbon trip",
            "Pay electricity bill"
        )
        assertEquals(
            listOf("Print boarding passes for the Lisbon trip", "Pay electricity bill"),
            SuggestionDuplicates.withoutRepeats(suggestions, listOf("Send mockups to Sam", "Call mom", "Water the plants")) { it }
        )
    }
}
