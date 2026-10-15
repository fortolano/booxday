package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*

/**
 * Scores items based on urgency signals:
 * - Deadline proximity (due date)
 * - Overdue status (past due)
 * - Urgency keywords in text
 * - Zombie tasks (high migration count)
 * - Priority tags (A/B/C)
 */
class UrgencyScorer : Scorer {
    override val id = ScorerIds.URGENCY

    companion object {
        // Deadline scoring thresholds
        private const val SCORE_DUE_TODAY = 0.95f
        private const val SCORE_DUE_TOMORROW = 0.85f
        private const val SCORE_DUE_IN_2_DAYS = 0.70f
        private const val SCORE_DUE_IN_3_DAYS = 0.55f
        private const val SCORE_DUE_THIS_WEEK = 0.40f
        private const val SCORE_DUE_NEXT_WEEK = 0.25f
        private const val SCORE_NO_DEADLINE = 0.10f

        // Overdue scoring
        private const val SCORE_OVERDUE_1_DAY = 0.90f
        private const val SCORE_OVERDUE_2_3_DAYS = 0.95f
        private const val SCORE_OVERDUE_4_PLUS_DAYS = 1.0f

        // Urgency keywords
        private val URGENCY_KEYWORDS_HIGH = listOf(
            "urgent", "asap", "emergency", "critical", "immediately",
            "today", "now", "deadline", "overdue"
        )
        private val URGENCY_KEYWORDS_MEDIUM = listOf(
            "important", "priority", "soon", "this week", "time-sensitive"
        )
        private const val SCORE_KEYWORD_HIGH = 0.65f
        private const val SCORE_KEYWORD_MEDIUM = 0.45f

        // Zombie task threshold (rollover count)
        private const val ZOMBIE_THRESHOLD = 3
        private const val SCORE_ZOMBIE = 0.75f

        // Priority tag scores
        private const val SCORE_PRIORITY_A = 0.80f
        private const val SCORE_PRIORITY_B = 0.50f
        private const val SCORE_PRIORITY_C = 0.30f
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()
        val metadata = mutableMapOf<String, MutableMap<String, Any>>()

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            val itemMeta = mutableMapOf<String, Any>()
            var maxScore = 0f

            when (item) {
                is ScorableItem.TaskItem -> {
                    // 1. Check deadline/due date
                    val task = item.task
                    val dueDateScore = scoreDueDate(task, item.daysFromToday, item.isOverdue, itemSignals)
                    if (dueDateScore > maxScore) maxScore = dueDateScore

                    // 2. Check priority tag
                    val priorityScore = scorePriorityTag(task.priority, itemSignals)
                    if (priorityScore > maxScore) maxScore = priorityScore

                    // 3. Check for zombie (high migration count)
                    val zombieScore = scoreZombieTask(task.migrationCount, itemSignals)
                    if (zombieScore > maxScore) maxScore = zombieScore

                    // 4. Check urgency keywords in text
                    val keywordScore = scoreUrgencyKeywords(item.text, itemSignals)
                    if (keywordScore > maxScore) maxScore = keywordScore

                    itemMeta["migrationCount"] = task.migrationCount
                    itemMeta["priority"] = task.priority
                }

                is ScorableItem.SuggestionItem -> {
                    // For suggestions, only check keywords
                    val keywordScore = scoreUrgencyKeywords(item.text, itemSignals)
                    maxScore = keywordScore
                }

                is ScorableItem.EventItem -> {
                    // For events, score by proximity
                    maxScore = when (item.daysUntil) {
                        0 -> { itemSignals.add("Event is TODAY"); SCORE_DUE_TODAY }
                        1 -> { itemSignals.add("Event is tomorrow"); SCORE_DUE_TOMORROW }
                        2 -> { itemSignals.add("Event in 2 days"); SCORE_DUE_IN_2_DAYS }
                        3 -> { itemSignals.add("Event in 3 days"); SCORE_DUE_IN_3_DAYS }
                        in 4..7 -> { itemSignals.add("Event this week"); SCORE_DUE_THIS_WEEK }
                        else -> SCORE_NO_DEADLINE
                    }
                }
            }

            scores[item.id] = maxScore.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
            metadata[item.id] = itemMeta
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals,
            metadata = metadata
        )
    }

    private fun scoreDueDate(
        task: com.boox.day.data.db.Task,
        daysFromToday: Int,
        isOverdue: Boolean,
        signals: MutableList<String>
    ): Float {
        // Check if task has a due date
        if (task.dueDay == null) return SCORE_NO_DEADLINE

        return when {
            isOverdue && daysFromToday <= -4 -> {
                signals.add("OVERDUE by ${-daysFromToday} days")
                SCORE_OVERDUE_4_PLUS_DAYS
            }
            isOverdue && daysFromToday in -3..-2 -> {
                signals.add("Overdue by ${-daysFromToday} days")
                SCORE_OVERDUE_2_3_DAYS
            }
            isOverdue && daysFromToday == -1 -> {
                signals.add("Overdue by 1 day")
                SCORE_OVERDUE_1_DAY
            }
            daysFromToday == 0 -> {
                signals.add("Due TODAY")
                SCORE_DUE_TODAY
            }
            daysFromToday == 1 -> {
                signals.add("Due tomorrow")
                SCORE_DUE_TOMORROW
            }
            daysFromToday == 2 -> {
                signals.add("Due in 2 days")
                SCORE_DUE_IN_2_DAYS
            }
            daysFromToday == 3 -> {
                signals.add("Due in 3 days")
                SCORE_DUE_IN_3_DAYS
            }
            daysFromToday in 4..7 -> {
                signals.add("Due this week")
                SCORE_DUE_THIS_WEEK
            }
            daysFromToday in 8..14 -> {
                signals.add("Due next week")
                SCORE_DUE_NEXT_WEEK
            }
            else -> SCORE_NO_DEADLINE
        }
    }

    private fun scorePriorityTag(priority: String?, signals: MutableList<String>): Float {
        return when (priority?.uppercase()) {
            "A" -> { signals.add("Priority A task"); SCORE_PRIORITY_A }
            "B" -> { signals.add("Priority B task"); SCORE_PRIORITY_B }
            "C" -> { signals.add("Priority C task"); SCORE_PRIORITY_C }
            else -> 0f
        }
    }

    private fun scoreZombieTask(migrationCount: Int, signals: MutableList<String>): Float {
        return if (migrationCount >= ZOMBIE_THRESHOLD) {
            signals.add("Zombie task (rolled over ${migrationCount}x)")
            SCORE_ZOMBIE
        } else 0f
    }

    private fun scoreUrgencyKeywords(text: String, signals: MutableList<String>): Float {
        val lowerText = text.lowercase()

        // Check high-urgency keywords
        URGENCY_KEYWORDS_HIGH.forEach { keyword ->
            if (lowerText.contains(keyword)) {
                signals.add("Contains urgent keyword: '$keyword'")
                return SCORE_KEYWORD_HIGH
            }
        }

        // Check medium-urgency keywords
        URGENCY_KEYWORDS_MEDIUM.forEach { keyword ->
            if (lowerText.contains(keyword)) {
                signals.add("Contains priority keyword: '$keyword'")
                return SCORE_KEYWORD_MEDIUM
            }
        }

        return 0f
    }

    override fun isApplicable(input: ScoringInput): Boolean {
        val prefs = input.preferences
        if (input.feature == IntelligenceFeature.SMART_SUGGESTIONS &&
            prefs != null &&
            !prefs.includeDeadlineWarnings) {
            return false
        }
        return true
    }
}
