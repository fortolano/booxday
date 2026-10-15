package com.boox.day.ocr.semantic.classification

import android.content.Context
import android.util.Log
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Per-user domain/intent priors using exponential moving average.
 *
 * Usage:
 * - Applied when note is short (< 50 chars) OR confidence is in uncertain zone (0.30-0.60)
 * - Capped to prevent over-personalization (max boost 0.35)
 * - Logged in explainability for debugging
 *
 * This solves the "small dataset" problem - even with universal dictionaries,
 * users are not universal. Each user has strong habits.
 */
class UserPriors(context: Context) {

    companion object {
        private const val TAG = "UserPriors"
        private const val PREFS_NAME = "user_classification_priors"
        private const val KEY_DOMAIN_COUNTS = "domain_counts"
        private const val KEY_INTENT_COUNTS = "intent_counts"
        private const val KEY_TOTAL_CLASSIFICATIONS = "total_classifications"

        /** Minimum classifications before priors kick in */
        private const val MIN_CLASSIFICATIONS = 10

        /** EMA decay factor (0.95 = recent ~20 notes dominate) */
        private const val EMA_DECAY = 0.95f

        /** Maximum prior boost (increased from 0.25 for stronger personalization) */
        private const val MAX_PRIOR_BOOST = 0.35f

        /** Confidence zone where priors are applied (expanded from 0.45 to catch more cases) */
        private const val UNCERTAIN_ZONE_LOW = 0.30f
        private const val UNCERTAIN_ZONE_HIGH = 0.60f

        /** Short text threshold */
        private const val SHORT_TEXT_CHARS = 50
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // In-memory cache
    private var domainCounts: MutableMap<Domain, Float> = loadDomainCounts()
    private var intentCounts: MutableMap<Intent, Float> = loadIntentCounts()
    private var totalClassifications: Int = prefs.getInt(KEY_TOTAL_CLASSIFICATIONS, 0)

    /**
     * Record a classification result to update priors.
     */
    fun recordClassification(domain: Domain, intents: List<Intent>) {
        totalClassifications++

        // Update domain EMA
        for (d in Domain.entries) {
            val current = domainCounts[d] ?: 0f
            val target = if (d == domain) 1f else 0f
            domainCounts[d] = (EMA_DECAY * current) + ((1 - EMA_DECAY) * target)
        }

        // Update intent EMA (for top intents only)
        for (intent in intents.take(2)) {
            val current = intentCounts[intent] ?: 0f
            intentCounts[intent] = (EMA_DECAY * current) + ((1 - EMA_DECAY) * 1f)
        }
        // Decay other intents
        for (i in Intent.entries) {
            if (i !in intents.take(2)) {
                val current = intentCounts[i] ?: 0f
                intentCounts[i] = EMA_DECAY * current
            }
        }

        // Persist
        savePriors()
    }

    /**
     * Get prior boosts for domains.
     *
     * @param textLength Length of input text
     * @param rawConfidence Raw classification confidence before priors
     * @return Map of domain to prior boost, or null if priors shouldn't be applied
     */
    fun getDomainPriors(
        textLength: Int,
        rawConfidence: Float
    ): PriorResult? {
        // Check if priors should be applied
        if (totalClassifications < MIN_CLASSIFICATIONS) {
            return null
        }

        val isShortText = textLength < SHORT_TEXT_CHARS
        val isUncertain = rawConfidence in UNCERTAIN_ZONE_LOW..UNCERTAIN_ZONE_HIGH

        if (!isShortText && !isUncertain) {
            return null
        }

        // Compute normalized priors
        val total = domainCounts.values.sum()
        if (total < 0.1f) return null

        val normalizedPriors = domainCounts.mapValues { (_, count) ->
            // Boost = (frequency - average) * scaling
            val frequency = count / total
            val averageFrequency = 1f / Domain.entries.size
            val boost = (frequency - averageFrequency) * 2f  // Scale up difference
            boost.coerceIn(-MAX_PRIOR_BOOST, MAX_PRIOR_BOOST)
        }

        // Filter to only significant boosts
        val significantPriors = normalizedPriors
            .filter { (_, boost) -> abs(boost) > 0.05f }
            .mapKeys { it.key.name }

        if (significantPriors.isEmpty()) return null

        return PriorResult(
            boosts = significantPriors,
            reason = when {
                isShortText -> "shortText"
                isUncertain -> "lowConfidence"
                else -> "unknown"
            }
        )
    }

    /**
     * Apply priors to raw domain scores.
     */
    fun applyPriors(
        rawScores: Map<Domain, Float>,
        priorResult: PriorResult
    ): Map<Domain, Float> {
        return rawScores.mapValues { (domain, score) ->
            val boost = priorResult.boosts[domain.name] ?: 0f
            (score + boost).coerceIn(0f, 1.5f)  // Allow slight overflow for ranking
        }
    }

    /**
     * Get statistics for debugging.
     */
    fun getStats(): PriorStats {
        val total = domainCounts.values.sum()
        val topDomains = if (total > 0) {
            domainCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { "${it.key.name}: ${((it.value / total) * 100).toInt()}%" }
        } else {
            emptyList()
        }

        return PriorStats(
            totalClassifications = totalClassifications,
            topDomains = topDomains,
            isActive = totalClassifications >= MIN_CLASSIFICATIONS
        )
    }

    /**
     * Reset priors (for testing or user request).
     */
    fun reset() {
        domainCounts.clear()
        intentCounts.clear()
        totalClassifications = 0
        prefs.edit().clear().apply()
        Log.d(TAG, "User priors reset")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════

    private fun loadDomainCounts(): MutableMap<Domain, Float> {
        val jsonStr = prefs.getString(KEY_DOMAIN_COUNTS, null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Float>>(jsonStr)
                .mapNotNull { (key, value) ->
                    Domain.fromString(key)?.let { it to value }
                }
                .toMap()
                .toMutableMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load domain counts: ${e.message}")
            mutableMapOf()
        }
    }

    private fun loadIntentCounts(): MutableMap<Intent, Float> {
        val jsonStr = prefs.getString(KEY_INTENT_COUNTS, null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Float>>(jsonStr)
                .mapNotNull { (key, value) ->
                    Intent.fromString(key)?.let { it to value }
                }
                .toMap()
                .toMutableMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load intent counts: ${e.message}")
            mutableMapOf()
        }
    }

    private fun savePriors() {
        try {
            prefs.edit()
                .putString(KEY_DOMAIN_COUNTS, json.encodeToString(domainCounts.mapKeys { it.key.name }))
                .putString(KEY_INTENT_COUNTS, json.encodeToString(intentCounts.mapKeys { it.key.name }))
                .putInt(KEY_TOTAL_CLASSIFICATIONS, totalClassifications)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save priors: ${e.message}")
        }
    }

    data class PriorResult(
        val boosts: Map<String, Float>,
        val reason: String
    )

    data class PriorStats(
        val totalClassifications: Int,
        val topDomains: List<String>,
        val isActive: Boolean
    )
}
