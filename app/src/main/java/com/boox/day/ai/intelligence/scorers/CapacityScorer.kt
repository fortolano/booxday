package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*

/**
 * Scores items based on capacity and time slot availability:
 * - Fits available time slots
 * - Duration estimation (short tasks when little time)
 * - Current workload consideration
 * - Time-of-day appropriateness
 */
class CapacityScorer : Scorer {
    override val id = ScorerIds.CAPACITY

    companion object {
        // Time slot scoring
        private const val SCORE_FITS_MORNING_SLOT = 0.70f
        private const val SCORE_FITS_AFTERNOON_SLOT = 0.65f
        private const val SCORE_FITS_EVENING_SLOT = 0.60f

        // Duration-based scoring
        private const val SCORE_QUICK_TASK = 0.60f   // < 15 min
        private const val SCORE_MEDIUM_TASK = 0.50f  // 15-45 min
        private const val SCORE_LONG_TASK = 0.40f    // 45+ min

        // Workload thresholds
        private const val LIGHT_WORKLOAD_BONUS = 0.15f
        private const val HEAVY_WORKLOAD_PENALTY = -0.10f
        private const val LIGHT_WORKLOAD_THRESHOLD = 3
        private const val HEAVY_WORKLOAD_THRESHOLD = 8

        // Energy pattern alignment
        private const val ENERGY_PATTERN_MATCH_BOOST = 0.07f

        // Duration keywords
        private val QUICK_KEYWORDS = listOf("quick", "brief", "5 min", "10 min", "call", "text", "email")
        private val LONG_KEYWORDS = listOf("deep work", "project", "research", "write", "plan", "review all")
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        // Calculate current workload
        val todayTaskCount = input.taskContext?.todayTasks?.size ?: 0
        val incompleteTodayCount = input.taskContext?.todayTasks?.count { !it.isCompleted } ?: 0

        // Determine available time based on hour
        val timeSlot = when (input.currentHour) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "late_night"
        }

        val energyPreferredSlot = when (input.preferences?.energyPattern) {
            EnergyPattern.MORNING_PERSON -> "morning"
            EnergyPattern.AFTERNOON_PEAK -> "afternoon"
            EnergyPattern.NIGHT_OWL -> "evening"
            EnergyPattern.STEADY, null -> null
        }

        // Check calendar for free time
        val todayEvents = input.calendarContext?.todayEvents ?: emptyList()
        val busyHoursEstimate = todayEvents.size * 1.5f  // Rough estimate

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var score = 0.5f

            // 1. Estimate task duration
            val duration = estimateDuration(item.text)

            when (duration) {
                TaskDuration.QUICK -> {
                    score = SCORE_QUICK_TASK
                    itemSignals.add("Quick task - fits short gaps")
                }
                TaskDuration.MEDIUM -> {
                    score = SCORE_MEDIUM_TASK
                }
                TaskDuration.LONG -> {
                    if (timeSlot == "morning") {
                        score = SCORE_LONG_TASK + 0.10f
                        itemSignals.add("Long task - best in morning focus time")
                    } else if (timeSlot == "evening") {
                        score = SCORE_LONG_TASK - 0.10f
                        itemSignals.add("Long task - limited evening time")
                    } else {
                        score = SCORE_LONG_TASK
                    }
                }
            }

            // 1.5. Energy pattern alignment
            if (energyPreferredSlot != null) {
                val slotMatches = timeSlot == energyPreferredSlot ||
                    (energyPreferredSlot == "evening" && timeSlot == "late_night")
                if (slotMatches) {
                    score = (score + ENERGY_PATTERN_MATCH_BOOST).coerceAtMost(1f)
                    itemSignals.add("Matches energy pattern timing")
                }
            }

            // 2. Adjust based on workload
            when {
                incompleteTodayCount < LIGHT_WORKLOAD_THRESHOLD -> {
                    score += LIGHT_WORKLOAD_BONUS
                    itemSignals.add("Light workload - capacity available")
                }
                incompleteTodayCount > HEAVY_WORKLOAD_THRESHOLD -> {
                    if (duration != TaskDuration.QUICK) {
                        score += HEAVY_WORKLOAD_PENALTY
                        itemSignals.add("Heavy workload - consider quick wins")
                    }
                }
            }

            // 3. Time slot appropriateness
            if (duration == TaskDuration.QUICK && timeSlot == "late_night") {
                score += 0.10f
                itemSignals.add("Good fit for late-night quick task")
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

    private enum class TaskDuration { QUICK, MEDIUM, LONG }

    private fun estimateDuration(text: String): TaskDuration {
        val lowerText = text.lowercase()

        // Check for quick task indicators
        if (QUICK_KEYWORDS.any { lowerText.contains(it) }) {
            return TaskDuration.QUICK
        }

        // Check for long task indicators
        if (LONG_KEYWORDS.any { lowerText.contains(it) }) {
            return TaskDuration.LONG
        }

        // Estimate by text length
        return when {
            text.length < 30 -> TaskDuration.QUICK
            text.length > 80 -> TaskDuration.LONG
            else -> TaskDuration.MEDIUM
        }
    }
}
