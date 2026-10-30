package com.boox.day.ai.intelligence.scorers

import com.boox.day.ai.intelligence.KeywordMatch

import com.boox.day.data.ai.*
import java.time.LocalDate

/**
 * Scores items based on historical patterns:
 * - Streak maintenance (completing something maintains momentum)
 * - Day-of-week patterns (busier days get more tasks)
 * - Work-life balance (suggests items in neglected categories)
 * - Category trends
 */
class PatternScorer : Scorer {
    override val id = ScorerIds.PATTERN

    companion object {
        // Streak bonuses
        private const val STREAK_BONUS_SHORT = 0.10f  // 1-3 day streak
        private const val STREAK_BONUS_MEDIUM = 0.20f // 4-7 day streak
        private const val STREAK_BONUS_LONG = 0.30f   // 8+ day streak

        // Day pattern modifiers
        private const val BUSY_DAY_PENALTY = -0.10f
        private const val LIGHT_DAY_BONUS = 0.10f

        // Work-life balance
        private const val NEGLECTED_CATEGORY_BONUS = 0.25f
        private const val WORK_HEAVY_PERSONAL_BOOST = 0.15f
        private const val PERSONAL_HEAVY_WORK_BOOST = 0.15f

        // Completion rate thresholds
        private const val HIGH_COMPLETION_THRESHOLD = 0.7f
        private const val LOW_COMPLETION_THRESHOLD = 0.4f
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val patterns = input.patternAnalysis ?: return emptyResult(input)

        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        val todayDayOfWeek = input.currentDate.dayOfWeek.name
        val isBusyDay = patterns.busiestDays.contains(todayDayOfWeek)

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var score = 0.5f  // Base neutral score

            // 1. Streak maintenance bonus
            if (patterns.currentStreak > 0) {
                val streakBonus = when {
                    patterns.currentStreak >= 8 -> STREAK_BONUS_LONG
                    patterns.currentStreak >= 4 -> STREAK_BONUS_MEDIUM
                    else -> STREAK_BONUS_SHORT
                }
                score += streakBonus
                itemSignals.add("Maintain ${patterns.currentStreak}-day streak")
            }

            // 2. Day-of-week pattern
            if (isBusyDay) {
                // On busy days, prioritize to help user stay on track
                score += 0.05f
                itemSignals.add("Today is typically a busy day")
            }

            // 3. Category-based scoring (for work-life balance)
            val detectedCategory = detectPrimaryCategory(item.text)

            when (patterns.workLifeBalance) {
                WorkLifeBalance.WORK_HEAVY -> {
                    if (detectedCategory in listOf("health", "family", "social", "hobby")) {
                        score += WORK_HEAVY_PERSONAL_BOOST
                        itemSignals.add("Balance suggestion: you've been work-heavy")
                    }
                }
                WorkLifeBalance.PERSONAL_HEAVY -> {
                    if (detectedCategory == "work") {
                        score += PERSONAL_HEAVY_WORK_BOOST
                        itemSignals.add("Balance suggestion: catch up on work tasks")
                    }
                }
                else -> { /* Balanced - no adjustment */ }
            }

            // 4. Neglected category bonus
            if (detectedCategory != null && patterns.neglectedCategories.any {
                it.lowercase().contains(detectedCategory) || detectedCategory.contains(it.lowercase())
            }) {
                score += NEGLECTED_CATEGORY_BONUS
                itemSignals.add("Addresses neglected area: $detectedCategory")
            }

            // 5. Low completion rate adjustment
            if (patterns.overallCompletionRate < LOW_COMPLETION_THRESHOLD) {
                // When completion rate is low, boost simpler/quicker tasks
                // Oct 2026: tasks with words only (it was every item, events and empty rows included)
                val textLength = item.text.trim().length
                if (item is ScorableItem.TaskItem && textLength in 1 until 50) {
                    score += 0.10f
                    itemSignals.add("Quick win helps rebuild momentum")
                }
            }

            scores[item.id] = score.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals
        )
    }

    private fun detectPrimaryCategory(text: String): String? {
        val categoryPatterns = mapOf(
            "work" to listOf("work", "meeting", "email", "project", "client", "deadline"),
            "health" to listOf("exercise", "gym", "run", "walk", "yoga", "doctor", "health"),
            "family" to listOf("family", "kids", "spouse", "parent", "home"),
            "social" to listOf("friend", "call", "meet", "lunch", "dinner", "party"),
            "hobby" to listOf("hobby", "game", "play", "watch", "read")
        )

        return categoryPatterns.entries
            .filter { (_, keywords) -> KeywordMatch.containsAny(text, keywords) }
            .maxByOrNull { (_, keywords) -> keywords.count { KeywordMatch.contains(text, it) } }
            ?.key
    }

    private fun emptyResult(input: ScoringInput): ScorerResult {
        return ScorerResult(
            scorerId = id,
            scores = input.itemsToScore.associate { it.id to 0.5f },
            signals = input.itemsToScore.associate { it.id to emptyList() }
        )
    }

    override fun isApplicable(input: ScoringInput): Boolean {
        return input.patternAnalysis != null
    }
}
