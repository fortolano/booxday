package com.boox.day.ai.intelligence

import android.content.Context
import android.util.Log
import com.boox.day.ai.ExtendedContextLoaders
import com.boox.day.data.ai.*
import com.boox.day.data.db.Task
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Main orchestrator for the Contextual Intelligence Engine.
 * Provides scored context for all AI features.
 */
class IntelligenceEngine(private val context: Context) {
    private val TAG = "IntelligenceEngine"

    private val scorerEngine = ScorerEngine()
    private val cache = IntelligenceCache()

    /**
     * Get scored context for a feature with full context loading.
     *
     * @param feature Which AI feature is requesting context
     * @param tasks Tasks to score (if applicable)
     * @param suggestions Suggestions to score (if applicable)
     * @param forceRefresh Bypass cache
     * @return ScoredContext ready for prompt building
     */
    suspend fun getScoredContext(
        feature: IntelligenceFeature,
        tasks: List<Task>? = null,
        suggestions: List<String>? = null,
        forceRefresh: Boolean = false,
        referenceDate: LocalDate = LocalDate.now(),
        referenceHour: Int = LocalTime.now().hour
    ): ScoredContext = withContext(Dispatchers.Default) {
        val currentDate = referenceDate
        val currentHour = referenceHour.coerceIn(0, 23)

        // Check cache first
        val cacheKey = buildCacheKey(feature, currentDate, currentHour, tasks?.size, suggestions?.size)
        if (!forceRefresh) {
            cache.getScoredContext(cacheKey)?.let { cached ->
                Log.d(TAG, "Cache hit for $feature")
                return@withContext cached
            }
        }

        Log.d(TAG, "Building scored context for $feature")

        // Load all context in parallel
        val contextBundle = loadContextBundle(currentDate)

        // Build scorable items
        val itemsToScore = buildScorableItems(
            tasks = tasks,
            suggestions = suggestions,
            calendarContext = contextBundle.calendarContext,
            currentDate = currentDate
        )

        if (itemsToScore.isEmpty()) {
            Log.d(TAG, "No items to score")
            return@withContext emptyScoredContext(feature)
        }

        // Build scoring input
        val scoringInput = ScoringInput(
            feature = feature,
            currentDate = currentDate,
            currentHour = currentHour,
            preferences = contextBundle.preferences,
            calendarContext = contextBundle.calendarContext,
            taskContext = contextBundle.taskContext,
            patternAnalysis = contextBundle.patternAnalysis,
            semanticContexts = contextBundle.semanticContexts,
            itemsToScore = itemsToScore
        )

        // Get feature weights
        val weights = FeatureWeightProfiles.getWeights(feature)

        // Run scoring
        val scoredItems = scorerEngine.scoreItems(scoringInput, weights)

        // Build aggregate metrics
        val metrics = buildAggregateMetrics(scoredItems)

        // Build context summary for LLM
        val contextSummary = buildContextSummary(feature, scoredItems, metrics)

        val result = ScoredContext(
            feature = feature,
            scoredItems = scoredItems,
            aggregateMetrics = metrics,
            contextSummary = contextSummary,
            cacheKey = cacheKey
        )

        // Cache result
        cache.putScoredContext(cacheKey, result)

        Log.d(TAG, "Scored ${scoredItems.size} items, avg score: ${metrics.avgFinalScore}")
        result
    }

    /**
     * Get scored context using pre-loaded SuggestionContextV2.
     * More efficient when context is already available.
     */
    suspend fun getScoredContextFromExisting(
        feature: IntelligenceFeature,
        existingContext: SuggestionContextV2,
        tasks: List<Task>? = null,
        suggestions: List<String>? = null,
        taskTextOverrides: Map<String, String> = emptyMap()
    ): ScoredContext = withContext(Dispatchers.Default) {
        val currentDate = existingContext.currentDate
        val currentHour = existingContext.currentHour

        // Use preloaded semantic context when available
        val semanticContexts = existingContext.semanticContexts ?: loadSemanticContexts(currentDate)

        // Build scorable items
        val itemsToScore = buildScorableItems(
            tasks = tasks,
            suggestions = suggestions,
            calendarContext = existingContext.calendarContext,
            currentDate = currentDate,
            taskTextOverrides = taskTextOverrides
        )

        if (itemsToScore.isEmpty()) {
            return@withContext emptyScoredContext(feature)
        }

        val scoringInput = ScoringInput(
            feature = feature,
            currentDate = currentDate,
            currentHour = currentHour,
            preferences = existingContext.preferences,
            calendarContext = existingContext.calendarContext,
            taskContext = existingContext.taskContext,
            patternAnalysis = existingContext.patternAnalysis,
            semanticContexts = semanticContexts,
            itemsToScore = itemsToScore
        )

        val weights = FeatureWeightProfiles.getWeights(feature)
        val scoredItems = scorerEngine.scoreItems(scoringInput, weights)
        val metrics = buildAggregateMetrics(scoredItems)
        val contextSummary = buildContextSummary(feature, scoredItems, metrics)

        ScoredContext(
            feature = feature,
            scoredItems = scoredItems,
            aggregateMetrics = metrics,
            contextSummary = contextSummary
        )
    }

    private suspend fun loadContextBundle(currentDate: LocalDate): ContextBundle = coroutineScope {
        val prefsDeferred = async(Dispatchers.IO) {
            try {
                AiPreferencesRepository(context).getPreferences()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load preferences: ${e.message}")
                null
            }
        }

        val calendarDeferred = async(Dispatchers.IO) {
            try {
                ExtendedContextLoaders.loadExtendedCalendarContext(context, currentDate)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load calendar: ${e.message}")
                null
            }
        }

        val taskDeferred = async(Dispatchers.IO) {
            try {
                ExtendedContextLoaders.loadExtendedTaskContext(context, currentDate)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load tasks: ${e.message}")
                null
            }
        }

        val patternDeferred = async(Dispatchers.IO) {
            try {
                ExtendedContextLoaders.analyzeUserPatterns(context, currentDate)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load patterns: ${e.message}")
                null
            }
        }

        val semanticDeferred = async(Dispatchers.IO) {
            loadSemanticContexts(currentDate)
        }

        ContextBundle(
            preferences = prefsDeferred.await(),
            calendarContext = calendarDeferred.await(),
            taskContext = taskDeferred.await(),
            patternAnalysis = patternDeferred.await(),
            semanticContexts = semanticDeferred.await()
        )
    }

    private suspend fun loadSemanticContexts(
        currentDate: LocalDate
    ): List<com.boox.day.ocr.semantic.model.DateSemanticContext>? {
        return try {
            val service = SemanticGeniusService(context)
            val sources = setOf(OcrSource.TASK, OcrSource.MEMO)

            // Check if any semantic data exists
            val hasData = sources.any { service.hasAnyOcrData(it) }
            if (!hasData) return null

            // Load 4 days of context
            val dates = (0..3).map { currentDate.minusDays(it.toLong()) }
            dates.mapNotNull { date ->
                try {
                    service.getDateContext(date.toString(), sources)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load semantic contexts: ${e.message}")
            null
        }
    }

    private fun buildScorableItems(
        tasks: List<Task>?,
        suggestions: List<String>?,
        calendarContext: ExtendedCalendarContext?,
        currentDate: LocalDate,
        taskTextOverrides: Map<String, String> = emptyMap()
    ): List<ScorableItem> {
        val items = mutableListOf<ScorableItem>()

        // Add tasks
        tasks?.forEach { task ->
            val taskDate = try {
                LocalDate.parse(task.dayDate)
            } catch (e: Exception) {
                currentDate
            }
            val dueDaysFromToday = com.boox.day.data.db.TaskDueDates.daysUntil(task.dueDay, currentDate)?.toInt()
            val daysFromToday = dueDaysFromToday ?: ChronoUnit.DAYS.between(currentDate, taskDate).toInt()
            val isOverdue = dueDaysFromToday?.let { it < 0 && !task.isCompleted } ?: false
            val overrideText = taskTextOverrides[task.id]
            val taskText = if (!overrideText.isNullOrBlank()) {
                overrideText
            } else {
                task.typedText ?: task.recognizedText ?: task.convertedText ?: ""
            }

            items.add(ScorableItem.TaskItem(
                id = task.id,
                text = taskText,
                task = task,
                daysFromToday = daysFromToday,
                isOverdue = isOverdue
            ))
        }

        // Add suggestions
        suggestions?.forEachIndexed { index, suggestionText ->
            items.add(ScorableItem.SuggestionItem(
                id = "suggestion_$index",
                text = suggestionText,
                category = null,
                source = null
            ))
        }

        // Add calendar events for prep task generation
        calendarContext?.let { cal ->
            (cal.todayEvents + cal.tomorrowEvents + cal.thisWeekEvents.take(5))
                .forEachIndexed { index, event ->
                    items.add(ScorableItem.EventItem(
                        id = "event_$index",
                        text = event.title,
                        event = event,
                        daysUntil = event.daysFromToday
                    ))
                }
        }

        return items
    }

    private fun buildAggregateMetrics(items: List<ScoredItem>): AggregateMetrics {
        val avgScore = if (items.isNotEmpty()) {
            items.map { it.finalScore }.average().toFloat()
        } else 0f

        val distribution = ScoreDistribution(
            high = items.count { it.finalScore >= 0.7f },
            medium = items.count { it.finalScore in 0.4f..0.7f },
            low = items.count { it.finalScore < 0.4f }
        )

        // Count signal occurrences
        val signalCounts = items
            .flatMap { it.getTopSignals(3) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { SignalCount(it.key, it.value) }

        // Calculate component contributions
        val componentContributions = mutableMapOf<String, Float>()
        if (items.isNotEmpty()) {
            items.first().componentScores.keys.forEach { scorerId ->
                val avgContribution = items.map { item ->
                    item.componentScores[scorerId]?.weightedScore ?: 0f
                }.average().toFloat()
                componentContributions[scorerId] = avgContribution
            }
        }

        return AggregateMetrics(
            totalItems = items.size,
            avgFinalScore = avgScore,
            scoreDistribution = distribution,
            topSignals = signalCounts,
            componentContributions = componentContributions
        )
    }

    private fun buildContextSummary(
        feature: IntelligenceFeature,
        items: List<ScoredItem>,
        metrics: AggregateMetrics
    ): String = buildString {
        appendLine("## Intelligence Engine Pre-Analysis")
        appendLine()
        appendLine("**Feature**: ${feature.name}")
        appendLine("**Items Analyzed**: ${metrics.totalItems}")
        appendLine("**Average Score**: ${String.format("%.2f", metrics.avgFinalScore)}")
        appendLine()

        if (items.isNotEmpty()) {
            appendLine("### Top Scored Items")
            items.take(10).forEach { item ->
                appendLine("- [${String.format("%.2f", item.finalScore)}] ${item.itemText.take(60)}...")
                item.getTopSignals(2).forEach { signal ->
                    appendLine("  * $signal")
                }
            }
        }

        appendLine()
        appendLine("### Key Signals")
        metrics.topSignals.take(5).forEach { signal ->
            appendLine("- ${signal.signal} (${signal.count}x)")
        }
    }

    private fun emptyScoredContext(feature: IntelligenceFeature): ScoredContext {
        return ScoredContext(
            feature = feature,
            scoredItems = emptyList(),
            aggregateMetrics = AggregateMetrics(
                totalItems = 0,
                avgFinalScore = 0f,
                scoreDistribution = ScoreDistribution(0, 0, 0),
                topSignals = emptyList(),
                componentContributions = emptyMap()
            ),
            contextSummary = "No items to analyze."
        )
    }

    private fun buildCacheKey(
        feature: IntelligenceFeature,
        date: LocalDate,
        referenceHour: Int,
        taskCount: Int?,
        suggestionCount: Int?
    ): String {
        val timeBucket = timeBucketForHour(referenceHour)
        return "${feature.name}_${date}_${taskCount ?: 0}_${suggestionCount ?: 0}_$timeBucket"
    }

    internal fun timeBucketForHour(hour: Int): String {
        return when (hour.coerceIn(0, 23)) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
    }

    private data class ContextBundle(
        val preferences: AiPreferences?,
        val calendarContext: ExtendedCalendarContext?,
        val taskContext: ExtendedTaskContext?,
        val patternAnalysis: UserPatternAnalysis?,
        val semanticContexts: List<com.boox.day.ocr.semantic.model.DateSemanticContext>?
    )
}
