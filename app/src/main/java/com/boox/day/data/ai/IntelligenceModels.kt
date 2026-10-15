package com.boox.day.data.ai

import java.time.LocalDate

/**
 * Feature types supported by the Intelligence Engine.
 */
enum class IntelligenceFeature {
    SMART_SUGGESTIONS,
    SMART_PRIORITIZATION,
    PLAN_MY_DAY,
    BREAK_DOWN_TASK,
    DAILY_SUMMARY,
    WEEKLY_SUMMARY
}

/**
 * Scorer identifiers for component tracking.
 */
object ScorerIds {
    const val URGENCY = "urgency"
    const val PREFERENCE = "preference"
    const val CALENDAR = "calendar"
    const val PATTERN = "pattern"
    const val SEMANTIC = "semantic"
    const val CAPACITY = "capacity"
    const val COMPLEXITY = "complexity"
}

/**
 * Score from a single scoring component.
 *
 * @param scorerId Identifier of the scorer (e.g., "urgency")
 * @param rawScore Unweighted score from 0.0 to 1.0
 * @param weight Weight applied for this feature (from FeatureWeightProfiles)
 * @param weightedScore rawScore × weight
 * @param signals Human-readable explanations for the score
 * @param metadata Additional scorer-specific data (optional)
 */
data class ComponentScore(
    val scorerId: String,
    val rawScore: Float,
    val weight: Float,
    val weightedScore: Float,
    val signals: List<String>,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun zero(scorerId: String, weight: Float) = ComponentScore(
            scorerId = scorerId,
            rawScore = 0f,
            weight = weight,
            weightedScore = 0f,
            signals = emptyList()
        )
    }
}

/**
 * Fully scored item (task, suggestion, etc.) with all component scores.
 *
 * @param itemId Unique identifier (task.id or generated ID)
 * @param itemText Display text for the item
 * @param itemType Type of item ("task", "suggestion", "event_prep", etc.)
 * @param componentScores Map of scorerId → ComponentScore
 * @param finalScore Sum of all weightedScores (0.0 to 1.0)
 * @param rank Position in sorted list (1-based)
 * @param primarySignal Most impactful signal explaining the score
 * @param sourceData Reference to original data (Task, suggestion text, etc.)
 */
data class ScoredItem(
    val itemId: String,
    val itemText: String,
    val itemType: String,
    val componentScores: Map<String, ComponentScore>,
    val finalScore: Float,
    val rank: Int,
    val primarySignal: String,
    val sourceData: Any? = null
) {
    /**
     * Get the top N signals across all components, sorted by contribution.
     */
    fun getTopSignals(n: Int = 3): List<String> {
        return componentScores.values
            .filter { it.signals.isNotEmpty() && it.rawScore > 0f }
            .sortedByDescending { it.weightedScore }
            .flatMap { it.signals }
            .take(n)
    }

    /**
     * Get score for a specific component.
     */
    fun getComponentScore(scorerId: String): Float {
        return componentScores[scorerId]?.rawScore ?: 0f
    }
}

/**
 * Aggregate metrics across all scored items.
 */
data class AggregateMetrics(
    val totalItems: Int,
    val avgFinalScore: Float,
    val scoreDistribution: ScoreDistribution,
    val topSignals: List<SignalCount>,
    val componentContributions: Map<String, Float>
)

data class ScoreDistribution(
    val high: Int,      // finalScore >= 0.7
    val medium: Int,    // 0.4 <= finalScore < 0.7
    val low: Int        // finalScore < 0.4
)

data class SignalCount(
    val signal: String,
    val count: Int
)

/**
 * Complete scored context for a feature.
 *
 * @param feature Which feature this context is for
 * @param scoredItems All items with scores and rankings
 * @param aggregateMetrics Summary statistics
 * @param contextSummary Pre-formatted text for LLM prompts
 * @param generatedAt Timestamp of generation
 * @param cacheKey Cache key for invalidation
 */
data class ScoredContext(
    val feature: IntelligenceFeature,
    val scoredItems: List<ScoredItem>,
    val aggregateMetrics: AggregateMetrics,
    val contextSummary: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val cacheKey: String = ""
) {
    /**
     * Get top N items by final score.
     */
    fun getTopItems(n: Int): List<ScoredItem> = scoredItems.take(n)

    /**
     * Get items above a score threshold.
     */
    fun getItemsAboveThreshold(threshold: Float): List<ScoredItem> =
        scoredItems.filter { it.finalScore >= threshold }

    /**
     * Format as prompt section for LLM.
     */
    fun asPromptSection(): String = buildString {
        appendLine("## PRE-COMPUTED SCORES")
        appendLine()
        appendLine("The following items have been algorithmically scored. Use these scores as strong guidance:")
        appendLine()
        scoredItems.take(15).forEach { item ->
            appendLine("- [${String.format("%.2f", item.finalScore)}] ${item.itemText}")
            item.getTopSignals(2).forEach { signal ->
                appendLine("  - $signal")
            }
        }
        if (scoredItems.size > 15) {
            appendLine("- ... and ${scoredItems.size - 15} more items")
        }
    }
}

/**
 * Input context for scoring operations.
 */
data class ScoringInput(
    val feature: IntelligenceFeature,
    val currentDate: LocalDate,
    val currentHour: Int,
    val preferences: AiPreferences?,
    val calendarContext: ExtendedCalendarContext?,
    val taskContext: ExtendedTaskContext?,
    val patternAnalysis: UserPatternAnalysis?,
    val semanticContexts: List<com.boox.day.ocr.semantic.model.DateSemanticContext>?,
    val itemsToScore: List<ScorableItem>
)

/**
 * Item that can be scored by the engine.
 */
sealed class ScorableItem {
    abstract val id: String
    abstract val text: String
    abstract val type: String

    /**
     * Existing task to be scored.
     */
    data class TaskItem(
        override val id: String,
        override val text: String,
        val task: com.boox.day.data.db.Task,
        val daysFromToday: Int,
        val isOverdue: Boolean
    ) : ScorableItem() {
        override val type: String = "task"
    }

    /**
     * Suggested task (not yet created).
     */
    data class SuggestionItem(
        override val id: String,
        override val text: String,
        val category: String?,
        val source: String?  // "calendar_prep", "follow_up", "pattern_based", etc.
    ) : ScorableItem() {
        override val type: String = "suggestion"
    }

    /**
     * Calendar event for prep task generation.
     */
    data class EventItem(
        override val id: String,
        override val text: String,
        val event: CalendarEventWithDate,
        val daysUntil: Int
    ) : ScorableItem() {
        override val type: String = "event"
    }
}

/**
 * Result from a single scorer.
 */
data class ScorerResult(
    val scorerId: String,
    val scores: Map<String, Float>,           // itemId → rawScore
    val signals: Map<String, List<String>>,   // itemId → signals
    val metadata: Map<String, Map<String, Any>> = emptyMap()  // itemId → metadata
)
