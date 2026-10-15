package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPreferences
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createSuggestionItem
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.data.ai.LifeSituation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PreferenceScorerTest {

    private lateinit var scorer: PreferenceScorer

    @Before
    fun setup() {
        scorer = PreferenceScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CATEGORY DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `work task matches work category`() = runBlocking {
        val prefs = createPreferences(workPriority = 5)
        val task = createTask(text = "Finish project report for client")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Priority 5 → score = (5-1)/4 = 1.0
        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("work") })
    }

    @Test
    fun `health task matches health category`() = runBlocking {
        val prefs = createPreferences(healthPriority = 5)
        val task = createTask(text = "Go to the gym for exercise")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("health") })
    }

    @Test
    fun `family task matches family category`() = runBlocking {
        val prefs = createPreferences(familyPriority = 4)
        val task = createTask(text = "Pick up kids from school")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Priority 4 → score = (4-1)/4 = 0.75
        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `education task matches education category`() = runBlocking {
        val prefs = createPreferences(educationPriority = 5)
        val task = createTask(text = "Study for the exam tomorrow")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `social task matches social category`() = runBlocking {
        val prefs = createPreferences(socialPriority = 3)
        val task = createTask(text = "Call friend for coffee catch up")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Priority 3 → score = (3-1)/4 = 0.5
        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `finance task matches finance category`() = runBlocking {
        val prefs = createPreferences(financePriority = 4)
        val task = createTask(text = "Pay bills and check budget")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `hobby task matches hobby category`() = runBlocking {
        val prefs = createPreferences(hobbyPriority = 5)
        val task = createTask(text = "Play guitar and practice music")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIORITY LEVEL TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `high priority category scores 1_0`() = runBlocking {
        val prefs = createPreferences(workPriority = 5)
        val task = createTask(text = "Work project prep")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("HIGH priority") })
    }

    @Test
    fun `low priority category scores 0_0 to 0_25`() = runBlocking {
        // Use text that only matches "work" category - avoid "meeting" which has "meet" substring
        // that would match "social" category with default priority 3
        val prefs = createPreferences(workPriority = 1).copy(lifeSituations = setOf(LifeSituation.RETIRED))
        val task = createTask(text = "Work report draft")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Priority 1 → score = (1-1)/4 = 0.0
        assertEquals(0.0f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("LOW priority") })
    }

    @Test
    fun `medium priority category scores 0_5`() = runBlocking {
        val prefs = createPreferences(workPriority = 3).copy(lifeSituations = setOf(LifeSituation.RETIRED))
        val task = createTask(text = "Work project prep")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODE MODIFIER TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `crunch mode boosts work tasks`() = runBlocking {
        val prefs = createPreferences(workPriority = 3).copy(
            crunchMode = true,
            lifeSituations = setOf(LifeSituation.RETIRED)
        )
        val task = createTask(text = "Work project deadline")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Base score 0.5 + crunch boost 0.25 = 0.75
        assertEquals(0.75f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("CRUNCH MODE") })
    }

    @Test
    fun `crunch mode penalizes non-work tasks`() = runBlocking {
        val prefs = createPreferences(healthPriority = 5).copy(crunchMode = true)
        val task = createTask(text = "Go to the gym")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Base score 1.0 - penalty 0.1 = 0.9
        assertEquals(0.9f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `vacation mode deprioritizes work tasks`() = runBlocking {
        val prefs = createPreferences(workPriority = 5).copy(
            vacationMode = true,
            lifeSituations = setOf(LifeSituation.WORKING_PROFESSIONAL)
        )
        val task = createTask(text = "Work project prep")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Base score 1.0 - vacation penalty 0.3 + life situation boost 0.1 = 0.8
        assertEquals(0.8f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("VACATION MODE") })
    }

    @Test
    fun `holiday mode boosts family tasks`() = runBlocking {
        val prefs = createPreferences(familyPriority = 3).copy(holidayMode = true)
        val task = createTask(text = "Family time together")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        // Base score 0.5 + holiday boost 0.2 = 0.7
        assertEquals(0.7f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("HOLIDAY MODE") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO CATEGORY MATCH TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `unmatched task gets neutral score 0_5`() = runBlocking {
        val prefs = createPreferences()
        val task = createTask(text = "Generic task without keywords")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO PREFERENCES TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `no preferences returns neutral score for all`() = runBlocking {
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item), preferences = null)

        val result = scorer.score(input)

        assertEquals(0.5f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("No preferences") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // APPLICABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isApplicable returns true when preferences are setup`() {
        val prefs = createPreferences(setupComplete = true)
        val input = createScoringInput(emptyList(), preferences = prefs)

        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false when preferences not setup`() {
        val prefs = createPreferences(setupComplete = false)
        val input = createScoringInput(emptyList(), preferences = prefs)

        assertFalse(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false when no preferences`() {
        val input = createScoringInput(emptyList(), preferences = null)

        assertFalse(scorer.isApplicable(input))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUGGESTION ITEM TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `suggestion items are scored based on text`() = runBlocking {
        val prefs = createPreferences(healthPriority = 5)
        val suggestion = createSuggestionItem(text = "Go for a morning run")
        val input = createScoringInput(listOf(suggestion), preferences = prefs)

        val result = scorer.score(input)

        assertEquals(1.0f, result.scores[suggestion.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MULTIPLE ITEM TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple items scored with correct priority ordering`() = runBlocking {
        val prefs = createPreferences(
            workPriority = 2,
            healthPriority = 5
        )

        val workTask = createTask(id = "work", text = "Work meeting")
        val healthTask = createTask(id = "health", text = "Go to gym")

        val items = listOf(
            createTaskItem(workTask),
            createTaskItem(healthTask)
        )
        val input = createScoringInput(items, preferences = prefs)

        val result = scorer.score(input)

        // Health (priority 5) should score higher than work (priority 2)
        assertTrue(result.scores["health"]!! > result.scores["work"]!!)
    }
}
