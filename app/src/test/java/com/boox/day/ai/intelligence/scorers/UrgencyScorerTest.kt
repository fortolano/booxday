package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.ai.intelligence.IntelligenceTestUtils.toEpochMillis
import com.boox.day.data.ai.IntelligenceFeature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class UrgencyScorerTest {

    private lateinit var scorer: UrgencyScorer
    private val today = LocalDate.now()

    @Before
    fun setup() {
        scorer = UrgencyScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DUE DATE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task due today scores 0_95`() = runBlocking {
        val task = createTask(
            text = "Due today task",
            dueDate = today.toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 0, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.95f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("TODAY") })
    }

    @Test
    fun `task due tomorrow scores 0_85`() = runBlocking {
        val task = createTask(
            text = "Due tomorrow task",
            dueDate = today.plusDays(1).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 1, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.85f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("tomorrow") })
    }

    @Test
    fun `task due in 2 days scores 0_70`() = runBlocking {
        val task = createTask(
            text = "Due in 2 days",
            dueDate = today.plusDays(2).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 2, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.70f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `task due in 3 days scores 0_55`() = runBlocking {
        val task = createTask(
            text = "Due in 3 days",
            dueDate = today.plusDays(3).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 3, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.55f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `task due this week scores 0_40`() = runBlocking {
        val task = createTask(
            text = "Task in five days",  // Avoid "this week" keyword which triggers medium urgency
            dueDate = today.plusDays(5).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 5, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.40f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `task due next week scores 0_25`() = runBlocking {
        val task = createTask(
            text = "Due next week",
            dueDate = today.plusDays(10).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = 10, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.25f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `task without due date scores 0_10`() = runBlocking {
        val task = createTask(text = "No due date")
        val item = createTaskItem(task, daysFromToday = 0, isOverdue = false)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.10f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERDUE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `overdue 1 day scores 0_90`() = runBlocking {
        val task = createTask(
            text = "Overdue 1 day",
            dueDate = today.minusDays(1).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = -1, isOverdue = true)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.90f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Overdue") })
    }

    @Test
    fun `overdue 2-3 days scores 0_95`() = runBlocking {
        val task = createTask(
            text = "Overdue 3 days",
            dueDate = today.minusDays(3).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = -3, isOverdue = true)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.95f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `overdue 4+ days scores 1_0`() = runBlocking {
        val task = createTask(
            text = "Very overdue task",
            dueDate = today.minusDays(5).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = -5, isOverdue = true)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("OVERDUE") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIORITY TAG TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `priority A task scores 0_80`() = runBlocking {
        val task = createTask(text = "Priority A task", priority = "A")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.80f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Priority A") })
    }

    @Test
    fun `priority B task scores 0_50`() = runBlocking {
        val task = createTask(text = "Priority B task", priority = "B")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.50f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `priority C task scores 0_30`() = runBlocking {
        // Use text without "priority" keyword which triggers medium urgency scoring
        val task = createTask(text = "Low level task marked C", priority = "C")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.30f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZOMBIE TASK TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `zombie task with migration count 3 scores 0_75`() = runBlocking {
        val task = createTask(text = "Zombie task", migrationCount = 3)
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Zombie") })
    }

    @Test
    fun `zombie task with migration count 4 scores 0_75`() = runBlocking {
        val task = createTask(text = "Very zombie task", migrationCount = 4)
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `non-zombie task with migration count 2 does not get zombie score`() = runBlocking {
        val task = createTask(text = "Not zombie yet", migrationCount = 2)
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        // Should get base score only (no due date = 0.10)
        assertEquals(0.10f, result.scores[task.id]!!, 0.01f)
        assertFalse(result.signals[task.id]!!.any { it.contains("Zombie") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // URGENCY KEYWORD TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `urgent keyword scores 0_65`() = runBlocking {
        val task = createTask(text = "URGENT: Call client immediately")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.65f)
        assertTrue(result.signals[task.id]!!.any { it.contains("urgent") })
    }

    @Test
    fun `asap keyword scores 0_65`() = runBlocking {
        val task = createTask(text = "Need this done ASAP")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.65f)
    }

    @Test
    fun `important keyword scores 0_45`() = runBlocking {
        val task = createTask(text = "Important meeting prep")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.45f)
        assertTrue(result.signals[task.id]!!.any { it.contains("priority keyword") })
    }

    @Test
    fun `task without urgency keywords gets base score`() = runBlocking {
        val task = createTask(text = "Regular task without keywords")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        // No due date, no priority, no keywords = base score
        assertEquals(0.10f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCORE PRECEDENCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `highest score wins when multiple factors present`() = runBlocking {
        // Task with overdue (1.0), priority A (0.80), and urgent keyword (0.65)
        val task = createTask(
            text = "URGENT critical task",
            priority = "A",
            dueDate = today.minusDays(5).toEpochMillis()
        )
        val item = createTaskItem(task, daysFromToday = -5, isOverdue = true)
        val input = createScoringInput(listOf(item))

        val result = scorer.score(input)

        // Overdue 4+ days = 1.0 should win
        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `multiple items scored independently`() = runBlocking {
        val urgentTask = createTask(id = "urgent", text = "Urgent task", priority = "A")
        val normalTask = createTask(id = "normal", text = "Normal task")

        val items = listOf(
            createTaskItem(urgentTask),
            createTaskItem(normalTask)
        )
        val input = createScoringInput(items)

        val result = scorer.score(input)

        assertTrue(result.scores["urgent"]!! > result.scores["normal"]!!)
    }
}
