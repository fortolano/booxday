package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.KeywordMatch
import com.boox.day.data.ai.*
import com.boox.day.data.ai.AiPreferences.Companion.keptOptions

/**
 * Scores items against the AI options (AI Settings → AI options). Since 29 Oct 2026 only the kept
 * options count: Vacation lowers work items, and Health or Social suggestions off lower those items.
 * Everything else scores a neutral 0.5, so this scorer no longer reorders tasks by the removed AI
 * Preferences answers (life priorities, interests, lifestyle, home), which didn't change the AI's
 * answers in a measured test.
 */
class PreferenceScorer : Scorer {
    override val id = ScorerIds.PREFERENCE

    companion object {
        private const val NEUTRAL = 0.5f
        private const val LOWERED = 0.2f

        // Whole words (KeywordMatch): "meeting" isn't "meet", "bread" isn't "read"
        private val WORK_KEYWORDS = listOf("work", "meeting", "email", "project", "client", "deadline",
            "presentation", "report", "office", "boss", "colleague", "job")
        private val HEALTH_KEYWORDS = listOf("exercise", "gym", "run", "walk", "yoga", "meditation",
            "doctor", "health", "sleep", "water", "vitamin", "stretch")
        private val SOCIAL_KEYWORDS = listOf("friend", "call", "meet", "lunch", "dinner", "party",
            "birthday", "coffee", "hang out", "catch up", "visit")
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val options = input.preferences.keptOptions()
        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, List<String>>()

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var score = NEUTRAL
            if (options.vacationMode && KeywordMatch.containsAny(item.text, WORK_KEYWORDS)) {
                score = LOWERED
                itemSignals.add("VACATION MODE: work tasks deprioritized")
            }
            if (!options.includeHealthReminders && KeywordMatch.containsAny(item.text, HEALTH_KEYWORDS)) {
                score = LOWERED
                itemSignals.add("Health suggestions are off")
            }
            if (!options.includeSocialReminders && KeywordMatch.containsAny(item.text, SOCIAL_KEYWORDS)) {
                score = LOWERED
                itemSignals.add("Social suggestions are off")
            }
            scores[item.id] = score
            signals[item.id] = itemSignals
        }

        return ScorerResult(scorerId = id, scores = scores, signals = signals)
    }

    /** Only when an option is set; otherwise the engine shares this scorer's weight among the others. */
    override fun isApplicable(input: ScoringInput): Boolean {
        val options = input.preferences.keptOptions()
        return options.vacationMode || !options.includeHealthReminders || !options.includeSocialReminders
    }
}
