package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.TODAY
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPatternAnalysis
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.data.ai.WorkLifeBalance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PatternScorerTest {

    private lateinit var scorer: PatternScorer

    @Before
    fun setup() {
        scorer = PatternScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STREAK BONUS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `short streak 1-3 days gives 0_10 bonus`() = runBlocking {
        val patterns = createPatternAnalysis(currentStreak = 2)
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + streak bonus 0.10 = 0.60
        assertEquals(0.60f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("streak") })
    }

    @Test
    fun `medium streak 4-7 days gives 0_20 bonus`() = runBlocking {
        val patterns = createPatternAnalysis(currentStreak = 5)
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + streak bonus 0.20 = 0.70
        assertEquals(0.70f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `long streak 8+ days gives 0_30 bonus`() = runBlocking {
        val patterns = createPatternAnalysis(currentStreak = 10)
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + streak bonus 0.30 = 0.80
        assertEquals(0.80f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `no streak gives no bonus`() = runBlocking {
        val patterns = createPatternAnalysis(currentStreak = 0)
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5, no streak bonus
        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORK-LIFE BALANCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `work heavy balance boosts personal tasks`() = runBlocking {
        val patterns = createPatternAnalysis(
            workLifeBalance = WorkLifeBalance.WORK_HEAVY,
            currentStreak = 0
        )
        val task = createTask(text = "Go to the gym for exercise")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + work-heavy personal boost 0.15 = 0.65
        assertEquals(0.65f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("work-heavy") })
    }

    @Test
    fun `work heavy balance boosts family tasks`() = runBlocking {
        val patterns = createPatternAnalysis(
            workLifeBalance = WorkLifeBalance.WORK_HEAVY,
            currentStreak = 0
        )
        val task = createTask(text = "Spend time with family at home")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        assertTrue(result.scores[task.id]!! >= 0.60f)
    }

    @Test
    fun `personal heavy balance boosts work tasks`() = runBlocking {
        val patterns = createPatternAnalysis(
            workLifeBalance = WorkLifeBalance.PERSONAL_HEAVY,
            currentStreak = 0
        )
        val task = createTask(text = "Work on project deadline")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + personal-heavy work boost 0.15 = 0.65
        assertEquals(0.65f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("catch up on work") })
    }

    @Test
    fun `balanced work-life gives no boost`() = runBlocking {
        val patterns = createPatternAnalysis(
            workLifeBalance = WorkLifeBalance.BALANCED,
            currentStreak = 0
        )
        val task = createTask(text = "Go to the gym")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Just base 0.5
        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEGLECTED CATEGORY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `neglected category gives 0_25 bonus`() = runBlocking {
        val patterns = createPatternAnalysis(
            neglectedCategories = listOf("health"),
            currentStreak = 0
        )
        val task = createTask(text = "Go to the gym for exercise")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + neglected bonus 0.25 = 0.75
        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("neglected area") })
    }

    @Test
    fun `non-neglected category gets no bonus`() = runBlocking {
        val patterns = createPatternAnalysis(
            neglectedCategories = listOf("hobby"),  // hobby is neglected, not health
            currentStreak = 0
        )
        val task = createTask(text = "Go to the gym for exercise")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Just base 0.5
        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOW COMPLETION RATE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `low completion rate boosts quick tasks`() = runBlocking {
        val patterns = createPatternAnalysis(
            overallCompletionRate = 0.3f,  // Below 0.4 threshold
            currentStreak = 0
        )
        val task = createTask(text = "Quick call")  // Short text < 50 chars
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + quick win bonus 0.10 = 0.60
        assertEquals(0.60f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("Quick win") })
    }

    @Test
    fun `low completion rate does not boost long tasks`() = runBlocking {
        val patterns = createPatternAnalysis(
            overallCompletionRate = 0.3f,
            currentStreak = 0
        )
        val longText = "This is a very long task description that needs significant time and effort to complete"
        val task = createTask(text = longText)  // > 50 chars
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Just base 0.5
        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // APPLICABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isApplicable returns true with pattern analysis`() {
        val patterns = createPatternAnalysis()
        val input = createScoringInput(emptyList(), patternAnalysis = patterns)

        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false without pattern analysis`() {
        val input = createScoringInput(emptyList(), patternAnalysis = null)

        assertFalse(scorer.isApplicable(input))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO PATTERN DATA TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `no pattern analysis returns neutral score`() = runBlocking {
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = null)

        val result = scorer.score(input)

        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBINED BONUS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple bonuses accumulate`() = runBlocking {
        val patterns = createPatternAnalysis(
            currentStreak = 5,  // +0.20
            workLifeBalance = WorkLifeBalance.WORK_HEAVY,  // personal +0.15
            neglectedCategories = listOf("health")  // +0.25
        )
        val task = createTask(text = "Go to gym for exercise")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), patternAnalysis = patterns)

        val result = scorer.score(input)

        // Base 0.5 + streak 0.20 + work-heavy 0.15 + neglected 0.25 = 1.10, capped at 1.0
        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
    }
}
