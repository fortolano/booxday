package com.boox.day.ai.intelligence

import android.util.Log
import com.boox.day.data.ai.*
import com.boox.day.data.db.Task
import com.boox.day.ai.intelligence.scorers.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates running all scorers and combining results.
 */
class ScorerEngine {
    private val TAG = "ScorerEngine"

    // All available scorers
    private val scorers: List<Scorer> = listOf(
        UrgencyScorer(),
        PreferenceScorer(),
        CalendarScorer(),
        PatternScorer(),
        SemanticScorer(),
        CapacityScorer(),
        ComplexityScorer()
    )

    /**
     * Run all applicable scorers in parallel and combine results.
     *
     * @param input Scoring input with items and context
     * @param weights Feature-specific weight profile
     * @return List of ScoredItems sorted by final score
     */
    suspend fun scoreItems(
        input: ScoringInput,
        weights: Map<String, Float>
    ): List<ScoredItem> = coroutineScope {
        Log.d(TAG, "Scoring ${input.itemsToScore.size} items for ${input.feature}")

        // Filter to applicable scorers
        val applicableScorers = scorers.filter { it.isApplicable(input) }
        Log.d(TAG, "Running ${applicableScorers.size} applicable scorers")

        // Run scorers in parallel
        val results = applicableScorers.map { scorer ->
            async {
                try {
                    scorer.score(input)
                } catch (e: Exception) {
                    Log.e(TAG, "Scorer ${scorer.id} failed: ${e.message}")
                    ScorerResult(scorer.id, emptyMap(), emptyMap())
                }
            }
        }.awaitAll()

        // Combine results into ScoredItems
        combineResults(input.itemsToScore, results, weights)
    }

    private fun combineResults(
        items: List<ScorableItem>,
        results: List<ScorerResult>,
        weights: Map<String, Float>
    ): List<ScoredItem> {
        // Build lookup: scorerId → result
        val resultsByScorer = results.associateBy { it.scorerId }
        val totalWeight = resultsByScorer.keys.sumOf { weights[it]?.toDouble() ?: 0.0 }.toFloat()

        // Score each item
        val scoredItems = items.map { item ->
            val componentScores = mutableMapOf<String, ComponentScore>()
            var totalWeightedScore = 0f

            // Process each scorer result
            resultsByScorer.forEach { (scorerId, result) ->
                val weight = weights[scorerId] ?: 0f
                val rawScore = result.scores[item.id] ?: 0f
                val signals = result.signals[item.id] ?: emptyList()
                val metadata = result.metadata[item.id] ?: emptyMap()

                val weightedScore = rawScore * weight
                totalWeightedScore += weightedScore

                componentScores[scorerId] = ComponentScore(
                    scorerId = scorerId,
                    rawScore = rawScore,
                    weight = weight,
                    weightedScore = weightedScore,
                    signals = signals,
                    metadata = metadata
                )
            }

            // Determine primary signal
            val primarySignal = componentScores.values
                .filter { it.signals.isNotEmpty() }
                .maxByOrNull { it.weightedScore }
                ?.signals?.firstOrNull() ?: "No specific signals"

            val normalizedScore = if (totalWeight > 0f) {
                totalWeightedScore / totalWeight
            } else {
                0f
            }

            ScoredItem(
                itemId = item.id,
                itemText = item.text,
                itemType = item.type,
                componentScores = componentScores,
                finalScore = normalizedScore.coerceIn(0f, 1f),
                rank = 0,  // Set after sorting
                primarySignal = primarySignal,
                sourceData = when (item) {
                    is ScorableItem.TaskItem -> item.task
                    is ScorableItem.EventItem -> item.event
                    is ScorableItem.SuggestionItem -> null
                }
            )
        }

        // Sort by final score (descending) with deterministic tie-breaking by rowIndex
        // This ensures identical results when scores are equal
        return scoredItems
            .sortedWith(
                compareByDescending<ScoredItem> { it.finalScore }
                    .thenBy { (it.sourceData as? Task)?.rowIndex ?: Int.MAX_VALUE }
                    .thenBy { it.itemId }  // Final fallback for non-task items
            )
            .mapIndexed { index, item -> item.copy(rank = index + 1) }
    }
}
