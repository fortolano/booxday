package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.data.ai.IntelligenceFeature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ComplexityScorerTest {

    private lateinit var scorer: ComplexityScorer

    @Before
    fun setup() {
        scorer = ComplexityScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MULTI-VERB TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `two action verbs scores 0_70`() = runBlocking {
        val task = createTask(text = "Write report and send email")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertEquals(0.70f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Two-step") })
    }

    @Test
    fun `three action verbs scores above 0_70`() = runBlocking {
        val task = createTask(text = "Write, review, and send the report")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! > 0.70f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Multiple actions") })
    }

    @Test
    fun `single action verb scores low`() = runBlocking {
        val task = createTask(text = "Call mom")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! < 0.50f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCOPE WORD TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `scope word all triggers complexity`() = runBlocking {
        val task = createTask(text = "Clean all the rooms")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.55f)
        assertTrue(result.signals[task.id]!!.any { it.contains("scope") })
    }

    @Test
    fun `scope word comprehensive triggers complexity`() = runBlocking {
        val task = createTask(text = "Do a comprehensive review")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.55f)
    }

    @Test
    fun `multiple scope words increase score`() = runBlocking {
        val task = createTask(text = "Complete thorough review of all documents")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        // Should score higher with multiple scope words
        assertTrue(result.scores[task.id]!! >= 0.60f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONJUNCTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple conjunctions indicate steps`() = runBlocking {
        val task = createTask(text = "Go to store and buy groceries then cook dinner")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.50f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VAGUE TERM TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `vague term stuff triggers complexity`() = runBlocking {
        val task = createTask(text = "Organize stuff in garage")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertEquals(0.60f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("vague") })
    }

    @Test
    fun `vague term etc triggers complexity`() = runBlocking {
        val task = createTask(text = "Pack clothes, toiletries, etc")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.60f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LENGTH-BASED TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `long description triggers complexity`() = runBlocking {
        val longText = "This is a very long task description that contains " +
                "many words and should trigger the length-based complexity " +
                "detection threshold which starts at 80 characters"
        val task = createTask(text = longText)
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.45f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Long description") })
    }

    @Test
    fun `short description does not trigger length complexity`() = runBlocking {
        val task = createTask(text = "Short task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertFalse(result.signals[task.id]?.any { it.contains("Long description") } == true)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROJECT INDICATOR TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `project keyword scores 0_75`() = runBlocking {
        val task = createTask(text = "Start new project planning")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Project-level") })
    }

    @Test
    fun `milestone keyword scores 0_75`() = runBlocking {
        val task = createTask(text = "Complete Q4 milestone deliverables")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `launch keyword scores 0_75`() = runBlocking {
        val task = createTask(text = "Prepare for product launch")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // APPLICABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isApplicable returns true for BREAK_DOWN_TASK`() {
        val input = createScoringInput(emptyList(), feature = IntelligenceFeature.BREAK_DOWN_TASK)
        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns true for SMART_PRIORITIZATION`() {
        val input = createScoringInput(emptyList(), feature = IntelligenceFeature.SMART_PRIORITIZATION)
        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false for SMART_SUGGESTIONS`() {
        val input = createScoringInput(emptyList(), feature = IntelligenceFeature.SMART_SUGGESTIONS)
        assertFalse(scorer.isApplicable(input))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMPLE TASK TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `simple task without complexity indicators scores low`() = runBlocking {
        val task = createTask(text = "Buy milk")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), feature = IntelligenceFeature.BREAK_DOWN_TASK)

        val result = scorer.score(input)

        // Simple task should have low complexity score
        assertTrue(result.scores[task.id]!! < 0.30f)
    }
}
