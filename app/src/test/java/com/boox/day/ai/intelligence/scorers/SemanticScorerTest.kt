package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.IntelligenceTestUtils.TODAY
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createScoringInput
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTaskItem
import com.boox.day.data.ai.IntelligenceFeature
import com.boox.day.data.ai.ScoringInput
import com.boox.day.ocr.model.ActionItem
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.EntityType
import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.KeywordEntry
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.model.ActionItemWithSource
import com.boox.day.ocr.semantic.model.DateSemanticContext
import com.boox.day.ocr.semantic.model.DomainScore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SemanticScorerTest {

    private lateinit var scorer: SemanticScorer

    @Before
    fun setup() {
        scorer = SemanticScorer()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN MATCHING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task matching top domain scores 0_60`() = runBlocking {
        val semanticContext = createSemanticContext(
            domains = listOf(
                DomainScore(Domain.PROFESSIONAL, 10, 0.9f),  // Top domain
                DomainScore(Domain.HEALTH, 5, 0.5f)
            )
        )
        val task = createTask(text = "Schedule meeting with client")  // Matches PROFESSIONAL
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0.60f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("domain") })
    }

    @Test
    fun `task matching secondary domain scores 0_40`() = runBlocking {
        val semanticContext = createSemanticContext(
            domains = listOf(
                DomainScore(Domain.FINANCE, 10, 0.9f),  // Top domain
                DomainScore(Domain.PROFESSIONAL, 5, 0.5f)  // Secondary
            )
        )
        val task = createTask(text = "Prepare for work meeting")  // Matches PROFESSIONAL
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0.40f, result.scores[task.id]!!, 0.01f)
    }

    @Test
    fun `task not matching any domain scores 0`() = runBlocking {
        val semanticContext = createSemanticContext(
            domains = listOf(DomainScore(Domain.FINANCE, 10, 0.9f))
        )
        val task = createTask(text = "Go to the gym for exercise")  // No match to FINANCE
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTITY MATCHING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task mentioning known entity scores 0_50`() = runBlocking {
        val semanticContext = createSemanticContext(
            entities = listOf(
                ExtractedEntity(text = "John Smith", type = EntityType.PERSON)
            )
        )
        val task = createTask(text = "Call John Smith about the project")
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0.50f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("entity") })
    }

    @Test
    fun `task not mentioning any entity gets no entity bonus`() = runBlocking {
        val semanticContext = createSemanticContext(
            entities = listOf(
                ExtractedEntity(text = "John Smith", type = EntityType.PERSON)
            )
        )
        val task = createTask(text = "Buy groceries")  // No entity match
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYWORD MATCHING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task matching keywords gets bonus`() = runBlocking {
        val semanticContext = createSemanticContext(
            keywords = listOf(
                KeywordEntry(term = "budget", score = 0.8f, frequency = 5),
                KeywordEntry(term = "quarterly", score = 0.6f, frequency = 3)
            )
        )
        val task = createTask(text = "Review quarterly budget report")
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        // Should have keyword bonus
        assertTrue(result.scores[task.id]!! > 0f)
        assertTrue(result.signals[task.id]!!.any { it.contains("keyword") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTION ITEM MATCHING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `task matching action item scores 0_55`() = runBlocking {
        val semanticContext = createSemanticContext(
            actionItems = listOf(
                ActionItemWithSource(
                    actionItem = ActionItem(verb = "call", objectText = "dentist"),
                    sourceType = OcrSource.MEMO,
                    sourceId = "test_memo_1",
                    sourceDate = TODAY.toString()
                )
            )
        )
        val task = createTask(text = "Call the dentist tomorrow")
        val item = createTaskItem(task)
        val input = createScoringInputWithSemantic(listOf(item), listOf(semanticContext))

        val result = scorer.score(input)

        assertEquals(0.55f, result.scores[task.id]!!, 0.01f)
        assertTrue(result.signals[task.id]!!.any { it.contains("action item") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // APPLICABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isApplicable returns true with semantic contexts`() {
        val semanticContext = createSemanticContext()
        val input = createScoringInputWithSemantic(emptyList(), listOf(semanticContext))

        assertTrue(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false without semantic contexts`() {
        val input = createScoringInput(emptyList())  // No semantic contexts

        assertFalse(scorer.isApplicable(input))
    }

    @Test
    fun `isApplicable returns false with empty semantic contexts`() {
        val input = createScoringInputWithSemantic(emptyList(), emptyList())

        assertFalse(scorer.isApplicable(input))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMPTY SEMANTIC DATA TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `no semantic contexts returns zero scores`() = runBlocking {
        val task = createTask(text = "Any task")
        val item = createTaskItem(task)
        val input = createScoringInput(listOf(item))  // No semantic contexts

        val result = scorer.score(input)

        assertEquals(0f, result.scores[task.id]!!, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun createSemanticContext(
        domains: List<DomainScore> = emptyList(),
        entities: List<ExtractedEntity> = emptyList(),
        keywords: List<KeywordEntry> = emptyList(),
        actionItems: List<ActionItemWithSource> = emptyList()
    ): DateSemanticContext = DateSemanticContext(
        date = TODAY.toString(),
        dominantDomains = domains,
        dominantIntents = emptyList(),
        allKeywords = keywords,
        allEntities = entities,
        pendingActionItems = actionItems,
        memoCount = 1,
        taskCount = 1,
        booxNoteCount = 0,
        coveragePercent = 0.5f
    )

    private fun createScoringInputWithSemantic(
        items: List<com.boox.day.data.ai.ScorableItem>,
        semanticContexts: List<DateSemanticContext>
    ): ScoringInput = ScoringInput(
        feature = IntelligenceFeature.SMART_SUGGESTIONS,
        currentDate = TODAY,
        currentHour = 10,
        preferences = null,
        calendarContext = null,
        taskContext = null,
        patternAnalysis = null,
        semanticContexts = semanticContexts,
        itemsToScore = items
    )
}
