package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*

/**
 * Scores items based on calendar context:
 * - Prep tasks for upcoming events (0-3 days)
 * - Follow-up tasks for past events (-5 to -1 days)
 * - Conflicts with scheduled events
 * - Event keyword matching
 */
class CalendarScorer : Scorer {
    override val id = ScorerIds.CALENDAR

    companion object {
        // Prep task scoring
        private const val SCORE_PREP_TODAY = 0.90f
        private const val SCORE_PREP_TOMORROW = 0.80f
        private const val SCORE_PREP_2_DAYS = 0.65f
        private const val SCORE_PREP_3_DAYS = 0.50f

        // Follow-up scoring
        private const val SCORE_FOLLOWUP_YESTERDAY = 0.75f
        private const val SCORE_FOLLOWUP_2_3_DAYS = 0.60f
        private const val SCORE_FOLLOWUP_4_5_DAYS = 0.40f

        // Minimum word overlap for matching
        private const val MIN_MATCH_WORDS = 2
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val calendar = input.calendarContext ?: return emptyResult(input)

        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        // Build event lookup structures
        val upcomingEvents = (calendar.todayEvents + calendar.tomorrowEvents +
                            calendar.thisWeekEvents).take(20)
        val recentEvents = calendar.recentPastEvents.take(15)

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var maxScore = 0f

            // 1. Check for prep task match
            val prepMatch = findBestEventMatch(item.text, upcomingEvents)
            if (prepMatch != null) {
                val prepScore = when (prepMatch.daysFromToday) {
                    0 -> SCORE_PREP_TODAY
                    1 -> SCORE_PREP_TOMORROW
                    2 -> SCORE_PREP_2_DAYS
                    3 -> SCORE_PREP_3_DAYS
                    else -> 0.35f
                }
                if (prepScore > maxScore) {
                    maxScore = prepScore
                    val eventTime = prepMatch.startTime?.let { " at $it" } ?: ""
                    val daysLabel = when (prepMatch.daysFromToday) {
                        0 -> "TODAY"
                        1 -> "tomorrow"
                        else -> "in ${prepMatch.daysFromToday} days"
                    }
                    itemSignals.add("Prep for '${prepMatch.title}'$eventTime ($daysLabel)")
                }
            }

            // 2. Check for follow-up match
            val followUpMatch = findBestEventMatch(item.text, recentEvents)
            if (followUpMatch != null) {
                val followUpScore = when (followUpMatch.daysFromToday) {
                    -1 -> SCORE_FOLLOWUP_YESTERDAY
                    -2, -3 -> SCORE_FOLLOWUP_2_3_DAYS
                    else -> SCORE_FOLLOWUP_4_5_DAYS
                }
                if (followUpScore > maxScore) {
                    maxScore = followUpScore
                    val daysAgo = -followUpMatch.daysFromToday
                    itemSignals.add("Follow-up from '${followUpMatch.title}' ($daysAgo day${if (daysAgo > 1) "s" else ""} ago)")
                }
            }

            // 3. Check deadline events
            calendar.upcomingDeadlines.forEach { deadline ->
                if (textMatchesEvent(item.text, deadline.title)) {
                    val deadlineScore = when (deadline.urgency) {
                        DeadlineUrgency.CRITICAL -> 0.95f
                        DeadlineUrgency.HIGH -> 0.80f
                        DeadlineUrgency.MEDIUM -> 0.60f
                        DeadlineUrgency.LOW -> 0.40f
                    }
                    if (deadlineScore > maxScore) {
                        maxScore = deadlineScore
                        itemSignals.add("Matches deadline: '${deadline.title}' (${deadline.urgency})")
                    }
                }
            }

            scores[item.id] = maxScore.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals
        )
    }

    private fun findBestEventMatch(
        itemText: String,
        events: List<CalendarEventWithDate>
    ): CalendarEventWithDate? {
        return events
            .filter { textMatchesEvent(itemText, it.title, it.description) }
            .minByOrNull { kotlin.math.abs(it.daysFromToday) }
    }

    private fun textMatchesEvent(
        itemText: String,
        eventTitle: String,
        eventDescription: String? = null
    ): Boolean {
        val itemWords = itemText.lowercase().split(Regex("\\s+")).filter { it.length >= 3 }
        // Combine title and description for broader matching
        val eventText = buildString {
            append(eventTitle)
            if (!eventDescription.isNullOrBlank()) {
                append(" ")
                append(eventDescription.take(200))
            }
        }
        val eventWords = eventText.lowercase().split(Regex("\\s+")).filter { it.length >= 3 }

        val matchCount = itemWords.count { word ->
            eventWords.any { eventWord ->
                eventWord.contains(word) || word.contains(eventWord)
            }
        }

        return matchCount >= MIN_MATCH_WORDS ||
               itemWords.any { eventText.lowercase().contains(it) }
    }

    private fun emptyResult(input: ScoringInput): ScorerResult {
        return ScorerResult(
            scorerId = id,
            scores = input.itemsToScore.associate { it.id to 0f },
            signals = input.itemsToScore.associate { it.id to emptyList() }
        )
    }

    override fun isApplicable(input: ScoringInput): Boolean {
        return input.calendarContext != null && input.calendarContext.totalEventCount > 0
    }
}
