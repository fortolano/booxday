package com.boox.day.ai.intelligence

import com.boox.day.ai.intelligence.IntelligenceTestUtils.TODAY
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarContext
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarEvent
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPatternAnalysis
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPreferences
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.ai.intelligence.IntelligenceTestUtils.toEpochMillis
import com.boox.day.data.ai.*
import com.boox.day.data.db.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Integration tests for the scoring pipeline.
 *
 * These tests verify the complete scoring pipeline works correctly,
 * with proper weight application and scoring behavior.
 */
class IntelligenceEngineTest {

    private lateinit var scorerEngine: ScorerEngine

    @Before
    fun setup() {
        scorerEngine = ScorerEngine()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART PRIORITIZATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `smart prioritization ranks overdue task highest`() = runBlocking {
        val tasks = listOf(
            createTask(id = "overdue", text = "Very overdue task", dueDate = TODAY.minusDays(5).toEpochMillis()),
            createTask(id = "today", text = "Due today task", dueDate = TODAY.toEpochMillis()),
            createTask(id = "normal", text = "Normal task", priority = "C")
        )

        val result = scoreTasksForPrioritization(tasks)

        // Overdue task should have highest score
        val overdueScore = result.find { it.itemId == "overdue" }?.finalScore ?: 0f
        val todayScore = result.find { it.itemId == "today" }?.finalScore ?: 0f
        val normalScore = result.find { it.itemId == "normal" }?.finalScore ?: 0f

        assertTrue("Overdue should score highest", overdueScore > todayScore)
        assertTrue("Due today should score higher than normal", todayScore > normalScore)
    }

    @Test
    fun `smart prioritization considers priority tags`() = runBlocking {
        val tasks = listOf(
            createTask(id = "priority_a", text = "Priority A task", priority = "A"),
            createTask(id = "priority_b", text = "Priority B task", priority = "B"),
            createTask(id = "priority_c", text = "Priority C task", priority = "C")
        )

        val result = scoreTasksForPrioritization(tasks)

        val scoreA = result.find { it.itemId == "priority_a" }?.finalScore ?: 0f
        val scoreB = result.find { it.itemId == "priority_b" }?.finalScore ?: 0f
        val scoreC = result.find { it.itemId == "priority_c" }?.finalScore ?: 0f

        assertTrue("Priority A should score higher than B", scoreA > scoreB)
        assertTrue("Priority B should score higher than C", scoreB > scoreC)
    }

    @Test
    fun `smart prioritization detects zombie tasks`() = runBlocking {
        val tasks = listOf(
            createTask(id = "zombie", text = "Zombie task", migrationCount = 5),
            createTask(id = "normal", text = "Normal task", migrationCount = 0)
        )

        val result = scoreTasksForPrioritization(tasks)

        val zombieItem = result.find { it.itemId == "zombie" }
        val normalItem = result.find { it.itemId == "normal" }

        assertNotNull("Zombie task should be scored", zombieItem)
        assertNotNull("Normal task should be scored", normalItem)
        assertTrue("Zombie should score higher", zombieItem!!.finalScore > normalItem!!.finalScore)
        assertTrue(zombieItem.getTopSignals(10).any { it.contains("Zombie") || it.contains("rolled over") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `smart suggestions follow the AI options`() = runBlocking {
        // Health suggestions off (AI options, 29 Oct 2026): the gym task falls below the work task
        val preferences = com.boox.day.data.ai.AiPreferences(includeHealthReminders = false)
        val tasks = listOf(
            createTask(id = "health", text = "Go to the gym for exercise"),
            createTask(id = "work", text = "Work on project deadline")
        )

        val result = scoreTasksForSuggestions(tasks, preferences)

        val healthScore = result.find { it.itemId == "health" }?.finalScore ?: 0f
        val workScore = result.find { it.itemId == "work" }?.finalScore ?: 0f

        assertTrue("Health should score lower with health suggestions off", healthScore < workScore)
    }

    @Test
    fun `smart suggestions considers calendar context`() = runBlocking {
        val calendarContext = createCalendarContext(
            todayEvents = listOf(
                createCalendarEvent("Client meeting presentation", daysFromToday = 0)
            )
        )
        val tasks = listOf(
            createTask(id = "prep", text = "Prepare slides for client meeting"),
            createTask(id = "unrelated", text = "Buy groceries from store")
        )

        val result = scoreTasksForSuggestionsWithCalendar(tasks, calendarContext)

        val prepScore = result.find { it.itemId == "prep" }?.finalScore ?: 0f
        val unrelatedScore = result.find { it.itemId == "unrelated" }?.finalScore ?: 0f

        // Prep task matching calendar event should score higher
        assertTrue("Prep task should score higher", prepScore > unrelatedScore)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGREGATE METRICS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `scores all items correctly`() = runBlocking {
        val tasks = (1..10).map { i ->
            createTask(id = "task_$i", text = "Task $i")
        }

        val result = scoreTasksForPrioritization(tasks)

        assertEquals("Should score all tasks", 10, result.size)
    }

    @Test
    fun `scored items include signals`() = runBlocking {
        val tasks = listOf(
            createTask(id = "urgent", text = "URGENT: Call client immediately"),
            createTask(id = "priority", text = "Priority A task", priority = "A")
        )

        val result = scoreTasksForPrioritization(tasks)

        // Should have signals
        val urgentItem = result.find { it.itemId == "urgent" }
        assertNotNull("Should find urgent item", urgentItem)
        assertTrue("Should have signals", urgentItem!!.getTopSignals(5).isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PERFORMANCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `scoring 50 items completes under 500ms`() = runBlocking {
        val tasks = (1..50).map { i ->
            createTask(
                id = "task_$i",
                text = "Task $i with some description",
                priority = listOf("", "A", "B", "C").random()
            )
        }

        val startTime = System.currentTimeMillis()
        val result = scoreTasksForPrioritization(tasks)
        val duration = System.currentTimeMillis() - startTime

        assertEquals("Should score all items", 50, result.size)
        assertTrue("Scoring took ${duration}ms, expected <500ms", duration < 500)
    }

    @Test
    fun `scoring 100 items completes under 1000ms`() = runBlocking {
        val tasks = (1..100).map { i ->
            createTask(
                id = "task_$i",
                text = "Task $i with some longer description for testing",
                priority = listOf("", "A", "B", "C").random(),
                dueDate = if (i % 3 == 0) TODAY.plusDays((i % 7).toLong()).toEpochMillis() else null
            )
        }

        val startTime = System.currentTimeMillis()
        val result = scoreTasksForPrioritization(tasks)
        val duration = System.currentTimeMillis() - startTime

        assertEquals("Should score all items", 100, result.size)
        assertTrue("Scoring took ${duration}ms, expected <1000ms", duration < 1000)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSISTENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `scoring same tasks twice produces consistent results`() = runBlocking {
        val tasks = (1..20).map { i ->
            createTask(id = "task_$i", text = "Task $i")
        }

        val result1 = scoreTasksForPrioritization(tasks)
        val result2 = scoreTasksForPrioritization(tasks)

        // Scores should be identical
        result1.forEachIndexed { index, item ->
            assertEquals(
                "Scores should be consistent",
                item.finalScore,
                result2[index].finalScore,
                0.001f
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun tasksToScorableItems(tasks: List<Task>): List<ScorableItem> {
        return tasks.map { task ->
            // Calculate daysFromToday based on the due day if present, otherwise dayDate
            val daysFromToday = if (task.dueDay != null) {
                ChronoUnit.DAYS.between(TODAY, LocalDate.parse(task.dueDay)).toInt()
            } else {
                val taskDate = try {
                    LocalDate.parse(task.dayDate)
                } catch (e: Exception) {
                    TODAY
                }
                ChronoUnit.DAYS.between(TODAY, taskDate).toInt()
            }

            ScorableItem.TaskItem(
                id = task.id,
                text = task.typedText ?: task.recognizedText ?: task.convertedText ?: "",
                task = task,
                daysFromToday = daysFromToday,
                isOverdue = daysFromToday < 0 && !task.isCompleted && task.dueDay != null
            )
        }
    }

    private suspend fun scoreTasksForPrioritization(tasks: List<Task>): List<ScoredItem> {
        val input = ScoringInput(
            feature = IntelligenceFeature.SMART_PRIORITIZATION,
            currentDate = TODAY,
            currentHour = 10,
            preferences = null,
            calendarContext = null,
            taskContext = null,
            patternAnalysis = null,
            semanticContexts = null,
            itemsToScore = tasksToScorableItems(tasks)
        )
        val weights = FeatureWeightProfiles.getWeights(IntelligenceFeature.SMART_PRIORITIZATION)
        return scorerEngine.scoreItems(input, weights)
    }

    private suspend fun scoreTasksForSuggestions(
        tasks: List<Task>,
        preferences: AiPreferences
    ): List<ScoredItem> {
        val input = ScoringInput(
            feature = IntelligenceFeature.SMART_SUGGESTIONS,
            currentDate = TODAY,
            currentHour = 10,
            preferences = preferences,
            calendarContext = null,
            taskContext = null,
            patternAnalysis = null,
            semanticContexts = null,
            itemsToScore = tasksToScorableItems(tasks)
        )
        val weights = FeatureWeightProfiles.getWeights(IntelligenceFeature.SMART_SUGGESTIONS)
        return scorerEngine.scoreItems(input, weights)
    }

    private suspend fun scoreTasksForSuggestionsWithCalendar(
        tasks: List<Task>,
        calendarContext: ExtendedCalendarContext
    ): List<ScoredItem> {
        val input = ScoringInput(
            feature = IntelligenceFeature.SMART_SUGGESTIONS,
            currentDate = TODAY,
            currentHour = 10,
            preferences = null,
            calendarContext = calendarContext,
            taskContext = null,
            patternAnalysis = null,
            semanticContexts = null,
            itemsToScore = tasksToScorableItems(tasks)
        )
        val weights = FeatureWeightProfiles.getWeights(IntelligenceFeature.SMART_SUGGESTIONS)
        return scorerEngine.scoreItems(input, weights)
    }
}
