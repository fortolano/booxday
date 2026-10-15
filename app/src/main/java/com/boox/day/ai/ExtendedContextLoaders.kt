package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.data.ai.CalendarEventWithDate
import com.boox.day.data.ai.DeadlineEvent
import com.boox.day.data.ai.DeadlineUrgency
import com.boox.day.data.ai.ExtendedCalendarContext
import com.boox.day.data.ai.ExtendedTaskContext
import com.boox.day.data.ai.TaskWithDate
import com.boox.day.data.ai.UserPatternAnalysis
import com.boox.day.data.ai.WorkLifeBalance
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Loaders for extended context data used in Smart Suggestions V2.
 * Provides calendar context (-5 to +10 days) and task context (-7 to +7 days).
 */
object ExtendedContextLoaders {
    private const val TAG = "ExtendedContextLoaders"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ═══════════════════════════════════════════════════════════════════════════
    // CALENDAR CONTEXT LOADER (-5 to +10 days)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load extended calendar context spanning -5 to +10 days.
     *
     * @param context Android context
     * @param currentDate The reference date (usually today)
     * @return ExtendedCalendarContext with categorized events
     */
    suspend fun loadExtendedCalendarContext(
        context: Context,
        currentDate: LocalDate
    ): ExtendedCalendarContext {
        Log.d(TAG, "Loading extended calendar context for $currentDate")

        val eventRepo = LocalEventRepository(context)

        // Load events for the full range (-5 to +10)
        val startDate = currentDate.minusDays(5)
        val endDate = currentDate.plusDays(10)

        val allEvents = mutableListOf<CalendarEventWithDate>()

        // Iterate through each day and collect events
        var date = startDate
        while (!date.isAfter(endDate)) {
            val dateStr = date.format(dateFormatter)
            val dayEvents = eventRepo.getEventsForDay(dateStr)

            dayEvents.forEach { event ->
                val daysFromToday = ChronoUnit.DAYS.between(currentDate, date).toInt()

                allEvents.add(
                    CalendarEventWithDate(
                        date = date,
                        title = event.title,
                        startTime = event.startTime?.let { formatTime(it) },
                        endTime = event.endTime?.let { formatTime(it) },
                        location = event.location,
                        description = event.description?.take(200),
                        attendees = event.attendees,
                        isAllDay = event.isAllDay,
                        daysFromToday = daysFromToday
                    )
                )
            }
            date = date.plusDays(1)
        }

        Log.d(TAG, "Found ${allEvents.size} events in range")

        // Categorize events by time period
        val recentPast = allEvents.filter { it.daysFromToday in -5..-1 }
        val today = allEvents.filter { it.daysFromToday == 0 }
        val tomorrow = allEvents.filter { it.daysFromToday == 1 }
        val thisWeek = allEvents.filter { it.daysFromToday in 2..7 }
        val nextWeek = allEvents.filter { it.daysFromToday in 8..10 }

        // Extract deadlines from upcoming events
        val deadlines = extractDeadlines(allEvents.filter { it.daysFromToday >= 0 })

        return ExtendedCalendarContext(
            recentPastEvents = recentPast,
            todayEvents = today,
            tomorrowEvents = tomorrow,
            thisWeekEvents = thisWeek,
            nextWeekEvents = nextWeek,
            upcomingDeadlines = deadlines
        )
    }

    /**
     * Extract deadline-type events from calendar.
     */
    private fun extractDeadlines(events: List<CalendarEventWithDate>): List<DeadlineEvent> {
        val deadlineKeywords = listOf(
            "deadline", "due", "submit", "deliver", "presentation",
            "review", "meeting", "appointment", "exam", "test"
        )

        return events
            .filter { event ->
                deadlineKeywords.any { keyword ->
                    event.title.contains(keyword, ignoreCase = true)
                }
            }
            .map { event ->
                DeadlineEvent(
                    title = event.title,
                    date = event.date,
                    daysUntil = event.daysFromToday,
                    urgency = when (event.daysFromToday) {
                        0, 1 -> DeadlineUrgency.CRITICAL
                        2, 3 -> DeadlineUrgency.HIGH
                        in 4..7 -> DeadlineUrgency.MEDIUM
                        else -> DeadlineUrgency.LOW
                    }
                )
            }
            .sortedBy { it.daysUntil }
    }

    private fun formatTime(epochMillis: Long): String {
        return try {
            val instant = java.time.Instant.ofEpochMilli(epochMillis)
            val localTime = LocalTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            localTime.format(timeFormatter)
        } catch (e: Exception) {
            null
        } ?: ""
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK CONTEXT LOADER (-7 to +7 days)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load extended task context spanning -7 to +7 days.
     *
     * @param context Android context
     * @param currentDate The reference date (usually today)
     * @param ocrResults Map of taskId to OCR text for handwritten tasks
     * @return ExtendedTaskContext with categorized tasks
     */
    suspend fun loadExtendedTaskContext(
        context: Context,
        currentDate: LocalDate,
        ocrResults: Map<String, String> = emptyMap()
    ): ExtendedTaskContext {
        Log.d(TAG, "Loading extended task context for $currentDate")

        val taskRepo = TaskRepository(context)

        val allTasks = mutableListOf<TaskWithDate>()

        // Load tasks for -7 to +7 days
        for (daysOffset in -7..7) {
            val targetDate = currentDate.plusDays(daysOffset.toLong())
            val dateStr = targetDate.format(dateFormatter)

            val dayTasks = taskRepo.getTasksForDay(dateStr)

            dayTasks.forEach { task ->
                val text = ocrResults[task.id] ?: task.typedText ?: ""
                if (text.isNotBlank() || task.isCompleted) {
                    allTasks.add(
                        TaskWithDate(
                            task = task,
                            date = targetDate,
                            text = text,
                            isCompleted = task.isCompleted,
                            priority = task.priority,
                            daysFromToday = daysOffset
                        )
                    )
                }
            }
        }

        Log.d(TAG, "Found ${allTasks.size} tasks in range")

        // Categorize tasks
        val recentCompleted = allTasks.filter { it.daysFromToday < 0 && it.isCompleted }
        val recentIncomplete = allTasks.filter { it.daysFromToday < 0 && !it.isCompleted }
        val today = allTasks.filter { it.daysFromToday == 0 }
        val upcoming = allTasks.filter { it.daysFromToday > 0 }

        // Calculate completion rate for last 7 days
        val last7Days = allTasks.filter { it.daysFromToday in -7..-1 }
        val completionRate = if (last7Days.isNotEmpty()) {
            last7Days.count { it.isCompleted }.toFloat() / last7Days.size
        } else {
            0f
        }

        return ExtendedTaskContext(
            recentCompletedTasks = recentCompleted,
            recentIncompleteTasks = recentIncomplete,
            todayTasks = today,
            upcomingTasks = upcoming,
            overdueCount = recentIncomplete.size,
            completionRateLast7Days = completionRate
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN ANALYZER (30-day window)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Analyze 30-day task patterns for personalized suggestions.
     *
     * @param context Android context
     * @param currentDate The reference date
     * @return UserPatternAnalysis with completion patterns and insights
     */
    suspend fun analyzeUserPatterns(
        context: Context,
        currentDate: LocalDate
    ): UserPatternAnalysis {
        Log.d(TAG, "Analyzing 30-day user patterns")

        val taskRepo = TaskRepository(context)

        // Collect 30 days of task data
        val tasksByDay = mutableMapOf<String, List<Task>>()
        val completionByDayOfWeek = mutableMapOf<String, MutableList<Boolean>>()

        for (daysBack in 0..29) {
            val targetDate = currentDate.minusDays(daysBack.toLong())
            val dateStr = targetDate.format(dateFormatter)
            val dayOfWeek = targetDate.dayOfWeek.name

            val dayTasks = taskRepo.getTasksForDay(dateStr)
            tasksByDay[dateStr] = dayTasks

            // Track completion by day of week
            if (dayTasks.isNotEmpty()) {
                completionByDayOfWeek.getOrPut(dayOfWeek) { mutableListOf() }
                    .addAll(dayTasks.map { it.isCompleted })
            }
        }

        // Calculate overall stats
        val allTasks = tasksByDay.values.flatten()
        val totalTasks = allTasks.size
        val completedTasks = allTasks.count { it.isCompleted }

        val overallCompletionRate = if (totalTasks > 0) {
            completedTasks.toFloat() / totalTasks
        } else {
            0f
        }

        // Completion rate by day of week
        val completionRateByDay = completionByDayOfWeek.mapValues { (_, completions) ->
            if (completions.isNotEmpty()) {
                completions.count { it }.toFloat() / completions.size
            } else {
                0f
            }
        }

        // Find busiest days
        val taskCountByDay = completionByDayOfWeek.mapValues { it.value.size }
        val busiestDays = taskCountByDay.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        // Analyze category distribution (based on typed text patterns)
        val categoryDistribution = analyzeCategoryDistribution(allTasks)

        // Find neglected categories
        val expectedCategories = setOf("WORK", "PERSONAL", "HEALTH", "SOCIAL")
        val neglectedCategories = expectedCategories
            .filter { (categoryDistribution[it] ?: 0f) < 0.1f }

        // Calculate current streak
        val currentStreak = calculateStreak(tasksByDay, currentDate)

        // Average tasks per day
        val avgTasksPerDay = if (tasksByDay.isNotEmpty()) {
            totalTasks.toFloat() / tasksByDay.size
        } else {
            0f
        }

        // Work-life balance
        val workLifeBalance = determineWorkLifeBalance(categoryDistribution)

        return UserPatternAnalysis(
            overallCompletionRate = overallCompletionRate,
            completionByDayOfWeek = completionRateByDay,
            categoryDistribution = categoryDistribution,
            neglectedCategories = neglectedCategories,
            busiestDays = busiestDays,
            currentStreak = currentStreak,
            avgTasksPerDay = avgTasksPerDay,
            workLifeBalance = workLifeBalance
        )
    }

    /**
     * Analyze task text to estimate category distribution.
     */
    private fun analyzeCategoryDistribution(tasks: List<Task>): Map<String, Float> {
        if (tasks.isEmpty()) return emptyMap()

        val categoryKeywords = mapOf(
            "WORK" to listOf("meeting", "email", "report", "project", "client", "deadline", "presentation", "review"),
            "PERSONAL" to listOf("home", "family", "clean", "organize", "personal", "house"),
            "HEALTH" to listOf("exercise", "gym", "run", "walk", "doctor", "health", "meditation", "yoga"),
            "SOCIAL" to listOf("call", "friend", "dinner", "lunch", "party", "birthday", "meet"),
            "ERRANDS" to listOf("buy", "shop", "grocery", "pick up", "return", "appointment"),
            "LEARNING" to listOf("study", "read", "learn", "course", "class", "practice")
        )

        val categoryCounts = mutableMapOf<String, Int>()

        tasks.forEach { task ->
            val text = (task.typedText ?: "").lowercase()
            categoryKeywords.forEach { (category, keywords) ->
                if (keywords.any { text.contains(it) }) {
                    categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
                }
            }
        }

        val total = categoryCounts.values.sum().coerceAtLeast(1)
        return categoryCounts.mapValues { it.value.toFloat() / total }
    }

    /**
     * Calculate current streak of days with completed tasks.
     */
    private fun calculateStreak(
        tasksByDay: Map<String, List<Task>>,
        currentDate: LocalDate
    ): Int {
        var streak = 0
        var checkDate = currentDate.minusDays(1)  // Start from yesterday

        while (true) {
            val dateStr = checkDate.format(dateFormatter)
            val dayTasks = tasksByDay[dateStr] ?: break

            if (dayTasks.isEmpty() || dayTasks.none { it.isCompleted }) {
                break
            }

            streak++
            checkDate = checkDate.minusDays(1)

            if (streak >= 30) break  // Cap at 30
        }

        return streak
    }

    /**
     * Determine work-life balance from category distribution.
     */
    private fun determineWorkLifeBalance(distribution: Map<String, Float>): WorkLifeBalance {
        val workPct = distribution["WORK"] ?: 0f
        val personalPct = (distribution["PERSONAL"] ?: 0f) +
                (distribution["HEALTH"] ?: 0f) +
                (distribution["SOCIAL"] ?: 0f)

        return when {
            distribution.isEmpty() -> WorkLifeBalance.UNKNOWN
            workPct > 0.6f -> WorkLifeBalance.WORK_HEAVY
            personalPct > 0.6f -> WorkLifeBalance.PERSONAL_HEAVY
            else -> WorkLifeBalance.BALANCED
        }
    }
}
