package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskContext
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskWithDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CapacityScorerTest {

    private lateinit var scorer: CapacityScorer

    @Before
    fun setup() {
        scorer = CapacityScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DURATION ESTIMATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `quick task keyword scores 0_60`() = runBlocking {
        // Create neutral workload (3 incomplete tasks = threshold, no bonus/penalty)
        val existingTasks = (1..3).map { createTask(id = "existing_$it", text = "Task $it") }
        val taskContext = createTaskContext(
            todayTasks = existingTasks.map { createTaskWithDate(it) }
        )

        val task = createTask(text = "Quick call to client")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10, taskContext = taskContext)

        val result = scorer.score(input)

        assertEquals(0.60f, result.scores[task.id]!!, 0.05f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Quick task") })
    }

    @Test
    fun `email task detected as quick`() = runBlocking {
        val task = createTask(text = "Send email to team")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.55f)
    }

    @Test
    fun `project task detected as long`() = runBlocking {
        val task = createTask(text = "Work on project documentation")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10)

        val result = scorer.score(input)

        // Long task in morning gets bonus
        assertTrue(result.scores[task.id]!! >= 0.40f)
    }

    @Test
    fun `deep work task detected as long`() = runBlocking {
        val task = createTask(text = "Deep work session on report")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10)

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("Long task") })
    }

    @Test
    fun `short text detected as quick task`() = runBlocking {
        val task = createTask(text = "Buy milk")  // < 30 chars
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10)

        val result = scorer.score(input)

        // Quick tasks score higher
        assertTrue(result.scores[task.id]!! >= 0.55f)
    }

    @Test
    fun `very long text detected as long task`() = runBlocking {
        val longText = "This is a very long task description that has many " +
                "details and requirements that will take significant time"
        val task = createTask(text = longText)  // > 80 chars
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 10)

        val result = scorer.score(input)

        // Long tasks in morning get bonus
        assertTrue(result.scores[task.id]!! >= 0.40f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TIME SLOT TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `long task in morning gets bonus`() = runBlocking {
        val task = createTask(text = "Deep work project planning")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 9)  // Morning

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("morning focus") })
    }

    @Test
    fun `long task in evening gets penalty`() = runBlocking {
        val task = createTask(text = "Deep work project planning")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 20)  // Evening

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("evening") })
    }

    @Test
    fun `quick task late night gets bonus`() = runBlocking {
        val task = createTask(text = "Quick text to mom")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), currentHour = 23)  // Late night

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("late-night") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKLOAD TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `light workload gives bonus`() = runBlocking {
        val existingTask = createTask(id = "existing_1", text = "Task 1")
        val taskContext = createTaskContext(
            todayTasks = listOf(
                createTaskWithDate(existingTask)
            )
        )
        val task = createTask(text = "New task to do")
        val item = createTaskItem(task)
        val input = createScoringInput(
            listOf(item),
            currentHour = 10,
            taskContext = taskContext
        )

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("Light workload") })
    }

    @Test
    fun `heavy workload gives penalty for non-quick tasks`() = runBlocking {
        val existingTasks = (1..10).map { createTask(id = "existing_$it", text = "Task $it") }
        val taskContext = createTaskContext(
            todayTasks = existingTasks.map { createTaskWithDate(it) }
        )
        val task = createTask(text = "Long project work session")
        val item = createTaskItem(task)
        val input = createScoringInput(
            listOf(item),
            currentHour = 14,  // Afternoon
            taskContext = taskContext
        )

        val result = scorer.score(input)

        assertTrue(result.signals[task.id]!!.any { it.contains("Heavy workload") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCORE RANGE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `score is within valid range 0 to 1`() = runBlocking {
        val tasks = listOf(
            createTask(id = "1", text = "Quick call"),
            createTask(id = "2", text = "Long project session deep work"),
            createTask(id = "3", text = "Medium task")
        )
        val items = tasks.map { createTaskItem(it) }
        val input = createScoringInput(items, currentHour = 10)

        val result = scorer.score(input)

        result.scores.values.forEach { score ->
            assertTrue("Score $score out of range", score in 0f..1f)
        }
    }
}
