package com.boox.day.ai

import com.boox.day.data.ai.ZombieInfo
import com.boox.day.data.ai.ZombieSuggestion
import com.boox.day.data.db.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Tracks task rollovers and detects "zombie" tasks.
 *
 * A zombie task is one that has been migrated (moved to a different day)
 * 3 or more times without being completed. These tasks need special attention
 * as they represent blocked or avoided work.
 */
object RolloverTracker {

    /** Minimum migrations to be considered a zombie */
    const val ZOMBIE_THRESHOLD = 3

    /** Age in days after which a low-priority zombie should be dropped */
    private const val DROP_AGE_DAYS = 14

    /** Task text length suggesting it might need breaking down */
    private const val LONG_TASK_THRESHOLD = 40

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Detect zombie tasks from a list of tasks.
     *
     * @param tasks List of tasks to analyze
     * @param today Current date for age calculation
     * @return List of tasks that qualify as zombies
     */
    fun detectZombies(tasks: List<Task>, today: LocalDate = LocalDate.now()): List<Task> {
        return tasks.filter { isZombie(it) }
    }

    /**
     * Check if a task is a zombie.
     */
    fun isZombie(task: Task): Boolean {
        return !task.isCompleted &&
               task.migrationCount >= ZOMBIE_THRESHOLD &&
               hasContent(task)
    }

    /**
     * Check if task has meaningful content.
     */
    private fun hasContent(task: Task): Boolean {
        return task.typedText?.isNotBlank() == true ||
               task.priority.isNotBlank()
    }

    /**
     * Generate zombie info with AI suggestion for a task.
     *
     * @param task The zombie task
     * @param taskText Display text (typed or OCR'd)
     * @param today Current date
     * @return ZombieInfo with analysis and suggestion
     */
    fun analyzeZombie(task: Task, taskText: String, today: LocalDate = LocalDate.now()): ZombieInfo {
        val daysSince = calculateDaysSinceCreation(task, today)
        val suggestion = suggestZombieAction(task, taskText, daysSince)
        val reason = buildSuggestionReason(task, taskText, daysSince, suggestion)

        return ZombieInfo(
            migrationCount = task.migrationCount,
            originalDate = task.originalDate,
            daysSinceCreation = daysSince,
            suggestion = suggestion,
            suggestionReason = reason
        )
    }

    /**
     * Calculate days since task was originally created.
     */
    private fun calculateDaysSinceCreation(task: Task, today: LocalDate): Int {
        val originalDate = task.originalDate?.let {
            try { LocalDate.parse(it, dateFormatter) } catch (e: Exception) { null }
        } ?: try {
            LocalDate.parse(task.dayDate, dateFormatter)
        } catch (e: Exception) {
            return 0
        }

        return ChronoUnit.DAYS.between(originalDate, today).toInt().coerceAtLeast(0)
    }

    /**
     * Suggest an action for a zombie task based on its characteristics.
     */
    private fun suggestZombieAction(task: Task, taskText: String, daysSince: Int): ZombieSuggestion {
        // Old + low priority = suggest dropping
        if (daysSince > DROP_AGE_DAYS && task.priority.isEmpty()) {
            return ZombieSuggestion.DROP
        }

        // High priority = schedule immediately
        if (task.priority == "A") {
            return ZombieSuggestion.SCHEDULE_TODAY
        }

        // Long text = might be too complex
        if (taskText.length > LONG_TASK_THRESHOLD) {
            return ZombieSuggestion.BREAK_DOWN
        }

        // Medium age + low priority = might delegate
        if (daysSince > 7 && task.priority.isEmpty()) {
            return ZombieSuggestion.DELEGATE
        }

        // Very high migration count = needs commitment
        if (task.migrationCount >= 5) {
            return ZombieSuggestion.COMMIT_DATE
        }

        // Default: break it down
        return ZombieSuggestion.BREAK_DOWN
    }

    /**
     * Build human-readable reason for the suggestion.
     */
    private fun buildSuggestionReason(
        task: Task,
        taskText: String,
        daysSince: Int,
        suggestion: ZombieSuggestion
    ): String {
        return when (suggestion) {
            ZombieSuggestion.DROP -> {
                "This task is ${daysSince} days old with no priority set. " +
                "It may no longer be relevant."
            }
            ZombieSuggestion.SCHEDULE_TODAY -> {
                "This is a priority A task that's been deferred ${task.migrationCount} times. " +
                "Blocking time today will help you tackle it."
            }
            ZombieSuggestion.BREAK_DOWN -> {
                if (taskText.length > LONG_TASK_THRESHOLD) {
                    "This task seems complex. Breaking it into smaller steps " +
                    "often makes it easier to start."
                } else {
                    "This task has been moved ${task.migrationCount} times. " +
                    "Breaking it down might reveal what's blocking you."
                }
            }
            ZombieSuggestion.DELEGATE -> {
                "This task has been lingering for ${daysSince} days. " +
                "Consider if someone else could handle it."
            }
            ZombieSuggestion.COMMIT_DATE -> {
                "You've moved this task ${task.migrationCount} times. " +
                "Pick a specific date and commit to completing it."
            }
        }
    }

    /**
     * Get urgency level based on migration count and age.
     */
    fun getZombieUrgency(task: Task, today: LocalDate): ZombieUrgency {
        val daysSince = calculateDaysSinceCreation(task, today)

        return when {
            task.priority == "A" || daysSince > 30 -> ZombieUrgency.CRITICAL
            task.migrationCount >= 5 || daysSince > 14 -> ZombieUrgency.HIGH
            task.migrationCount >= 4 || daysSince > 7 -> ZombieUrgency.MEDIUM
            else -> ZombieUrgency.LOW
        }
    }

    /**
     * Get summary of zombie situation for prompt building.
     */
    fun getZombieSummary(zombies: List<Task>, today: LocalDate): String {
        if (zombies.isEmpty()) return "No zombie tasks detected."

        val criticalCount = zombies.count { getZombieUrgency(it, today) == ZombieUrgency.CRITICAL }
        val highCount = zombies.count { getZombieUrgency(it, today) == ZombieUrgency.HIGH }

        return buildString {
            append("${zombies.size} zombie task(s) detected")
            if (criticalCount > 0) append(" ($criticalCount critical)")
            else if (highCount > 0) append(" ($highCount high priority)")
            append(". These have been moved 3+ times and need attention.")
        }
    }

    /**
     * Urgency level for zombie tasks.
     */
    enum class ZombieUrgency {
        CRITICAL,  // Priority A or very old
        HIGH,      // Moved 5+ times or 2+ weeks old
        MEDIUM,    // Moved 4 times or 1+ week old
        LOW        // Minimum zombie threshold
    }
}
