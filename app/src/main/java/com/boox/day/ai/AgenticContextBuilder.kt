package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.data.ai.AgenticContext
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.ai.CapacityAnalysis
import com.boox.day.data.ai.PatternInsights
import com.boox.day.data.ai.PreferenceConstraints
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.ocr.TaskOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Assembles the complete AgenticContext from multiple data sources.
 *
 * This is the entry point for gathering all context needed by the
 * Agentic Planning Service to generate proposals.
 */
class AgenticContextBuilder(private val context: Context) {

    private val taskRepository = TaskRepository(context)
    private val localEventRepository = LocalEventRepository(context)
    private val calendarEventRepository = CalendarEventRepository(context)
    private val preferencesRepository = AiPreferencesRepository(context)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private const val TAG = "AgenticContextBuilder"

        /** Number of days to look back for overdue tasks */
        private const val OVERDUE_LOOKBACK_DAYS = 14

        /** Number of days for pattern analysis */
        private const val PATTERN_ANALYSIS_DAYS = 7

        /** Context window for nearby tasks/events */
        private const val PLAN_CONTEXT_PAST_DAYS = 2
        private const val PLAN_CONTEXT_FUTURE_DAYS = 5

        internal fun resolvePlanningHour(
            planningDate: LocalDate,
            preferences: PreferenceConstraints,
            today: LocalDate = LocalDate.now(),
            currentHour: Int = LocalTime.now().hour
        ): Int {
            return if (planningDate == today) {
                currentHour.coerceIn(0, 23)
            } else {
                preferences.workHoursStart.coerceIn(0, 23)
            }
        }
    }

    /**
     * Build complete context for agentic planning.
     *
     * @param planningDate The date to plan (usually today)
     * @param onStageChange Callback for progress updates
     * @return Complete AgenticContext
     */
    suspend fun buildContext(
        planningDate: LocalDate = LocalDate.now(),
        onStageChange: (String) -> Unit = {}
    ): AgenticContext = withContext(Dispatchers.IO) {
        val planningDateStr = planningDate.format(dateFormatter)
        val rangeStart = planningDate.minusDays(PLAN_CONTEXT_PAST_DAYS.toLong())
        val rangeEnd = planningDate.plusDays(PLAN_CONTEXT_FUTURE_DAYS.toLong())

        // Load preferences
        onStageChange("Loading your preferences...")
        val preferences = loadPreferences()
        val constraints = PreferenceInterpreter.interpret(preferences)
        val currentHour = resolvePlanningHour(planningDate, constraints)

        // Load events from BOTH repositories (synced calendar + local) in parallel with tasks
        onStageChange("Loading your schedule...")
        val calendarEventsDeferred = async { loadCalendarEventsForRange(rangeStart, rangeEnd) }
        val localEventsDeferred = async { loadEventsForRange(rangeStart, rangeEnd) }
        val rangeTasksDeferred = async { loadTasksForRange(rangeStart, rangeEnd) }

        val calendarEvents = calendarEventsDeferred.await()
        val localEvents = localEventsDeferred.await()
        val rangeTasks = rangeTasksDeferred.await()

        // Merge: calendar events (Google/Outlook) + local events, deduplicated by providerEventId
        // Synced events appear in BOTH calendarEvents (via CalendarEvent table) and localEvents
        // (via LocalEvent table with providerEventId set). Dedup using providerEventId to avoid double-counting.
        val syncedProviderIds = calendarEvents.mapNotNull { it.providerEventId }.toSet()
        val uniqueLocalEvents = localEvents.filter { event ->
            event.providerEventId == null || event.providerEventId !in syncedProviderIds
        }
        val rangeEvents = (calendarEvents + uniqueLocalEvents)
            .sortedWith(compareByDescending<LocalEvent> { it.isAllDay }.thenBy { it.startTime })

        Log.d(TAG, "Events loaded: ${calendarEvents.size} synced + ${uniqueLocalEvents.size} local = ${rangeEvents.size} total for $rangeStart..$rangeEnd")

        val tomorrowDateStr = planningDate.plusDays(1).format(dateFormatter)
        // Use range check to include multi-day events that span the planning/tomorrow date
        val todayEvents = rangeEvents.filter { event ->
            val normalizedEnd = event.endDayId.ifEmpty { event.dayId }
            event.dayId <= planningDateStr && normalizedEnd >= planningDateStr
        }
        val tomorrowEvents = rangeEvents.filter { event ->
            val normalizedEnd = event.endDayId.ifEmpty { event.dayId }
            event.dayId <= tomorrowDateStr && normalizedEnd >= tomorrowDateStr
        }

        // Analyze capacity
        onStageChange("Analyzing your capacity...")
        val taskLoadEstimate = taskRepository.getTotalEstimatedMinutesForDate(planningDateStr)
        val timedTodayEvents = todayEvents.filterNot { it.isAllDay }
        val timedTomorrowEvents = tomorrowEvents.filterNot { it.isAllDay }

        val todayCapacity = CapacityAnalyzer.analyze(
            date = planningDate,
            events = timedTodayEvents,
            preferences = constraints,
            taskLoadEstimate = taskLoadEstimate
        )

        val tomorrowCapacity = CapacityAnalyzer.analyze(
            date = planningDate.plusDays(1),
            events = timedTomorrowEvents,
            preferences = constraints
        )

        // Load tasks
        onStageChange("Gathering your tasks...")
        val todayTasks = loadTasksForDate(planningDate)
        val overdueTasks = loadOverdueTasks(planningDate)
        val zombieTasks = loadZombieTasks()

        // Build OCR map for handwritten tasks
        onStageChange("Processing handwritten tasks...")
        val tasksForOcr = (todayTasks + overdueTasks + zombieTasks + rangeTasks).distinctBy { it.id }
        val taskOcrMap = buildTaskOcrMap(tasksForOcr)

        // Calculate pattern insights
        onStageChange("Analyzing your patterns...")
        val patternInsights = calculatePatternInsights(planningDate)

        // Build nearby context summary (past 2 / next 5 days)
        onStageChange("Summarizing surrounding days...")
        val nearbyContextSummary = buildNearbyContextSummary(
            planningDate = planningDate,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            rangeEvents = rangeEvents,
            rangeTasks = rangeTasks,
            taskOcrMap = taskOcrMap
        )

        // Find task-event relationships for scheduling hints
        onStageChange("Finding task-event relationships...")
        val taskEventRelations = findTaskEventRelationships(
            tasks = todayTasks + overdueTasks,
            events = todayEvents,
            taskOcrMap = taskOcrMap
        )

        // Collect upcoming day events (next 1-5 days) for "What's Coming" section
        val upcomingDayEvents = rangeEvents
            .filter { event ->
                try {
                    val eventDate = LocalDate.parse(event.dayId, dateFormatter)
                    eventDate.isAfter(planningDate) && !eventDate.isAfter(planningDate.plusDays(5))
                } catch (e: Exception) { false }
            }
            .groupBy { it.dayId }

        Log.d(TAG, "Context built: ${todayTasks.size} today, ${overdueTasks.size} overdue, ${zombieTasks.size} zombies, ${taskEventRelations.size} relations, ${upcomingDayEvents.size} upcoming days")

        AgenticContext(
            planningDate = planningDate,
            currentHour = currentHour,
            preferences = constraints,
            todayCapacity = todayCapacity,
            tomorrowCapacity = tomorrowCapacity,
            todayEvents = todayEvents,
            todayTasks = todayTasks,
            overdueTasks = overdueTasks,
            zombieTasks = zombieTasks,
            taskOcrMap = taskOcrMap,
            patternInsights = patternInsights,
            nearbyContextSummary = nearbyContextSummary,
            taskEventRelations = taskEventRelations,
            upcomingDayEvents = upcomingDayEvents
        )
    }

    /**
     * Load user preferences.
     */
    private suspend fun loadPreferences(): AiPreferences? {
        return try {
            preferencesRepository.getPreferences()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load preferences", e)
            null
        }
    }

    /**
     * Load events for a specific date.
     */
    private suspend fun loadEventsForDate(date: LocalDate): List<LocalEvent> {
        return try {
            localEventRepository.getEventsForDay(date.format(dateFormatter))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load events for $date", e)
            emptyList()
        }
    }

    /**
     * Load local events for a date range (inclusive).
     */
    private suspend fun loadEventsForRange(startDate: LocalDate, endDate: LocalDate): List<LocalEvent> {
        return try {
            localEventRepository.getEventsForRange(startDate, endDate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local events for range $startDate to $endDate", e)
            emptyList()
        }
    }

    /**
     * Load synced calendar events (Google/Outlook) for a date range and convert to LocalEvent.
     *
     * INTENTIONAL: Loads ALL synced events regardless of user's calendar selection filter.
     * The AI planner needs full schedule visibility for accurate capacity/conflict analysis.
     * A user hiding a work calendar from the grid should not cause the AI to double-book that time.
     */
    private suspend fun loadCalendarEventsForRange(startDate: LocalDate, endDate: LocalDate): List<LocalEvent> {
        return try {
            val startStr = startDate.format(dateFormatter)
            val endStr = endDate.format(dateFormatter)
            val dao = com.boox.day.data.db.AppDatabase.getDatabase(context).calendarEventDao()
            dao.getByDateRange(startStr, endStr).map { it.toLocalEvent() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load calendar events for range $startDate to $endDate", e)
            emptyList()
        }
    }

    /**
     * Load all tasks for a specific date.
     */
    private fun loadTasksForDate(date: LocalDate): List<Task> {
        val dateStr = date.format(dateFormatter)
        val allTasks = mutableListOf<Task>()

        // Get max page index for this date
        val maxPageIndex = taskRepository.getMaxPageIndexForDate(dateStr)

        // Load all pages
        for (pageIndex in 0..maxPageIndex) {
            val (tasks, _) = taskRepository.getOrCreateTasksForPage(dateStr, pageIndex)
            allTasks.addAll(tasks)
        }

        return allTasks
    }

    /**
     * Load tasks for a date range (inclusive).
     */
    private fun loadTasksForRange(startDate: LocalDate, endDate: LocalDate): List<Task> {
        return try {
            val startStr = startDate.format(dateFormatter)
            val endStr = endDate.format(dateFormatter)
            taskRepository.getTasksForDateRange(startStr, endStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load tasks for range $startDate to $endDate", e)
            emptyList()
        }
    }

    /**
     * Load overdue incomplete tasks from previous days.
     */
    private fun loadOverdueTasks(today: LocalDate): List<Task> {
        val todayStr = today.format(dateFormatter)
        return taskRepository.getOverdueTasks(todayStr)
    }

    /**
     * Load zombie tasks (migrated 3+ times).
     */
    private fun loadZombieTasks(): List<Task> {
        return taskRepository.getZombieTasks()
    }

    /**
     * Build OCR text map for handwritten tasks.
     * Uses TaskOcrService to get text for both typed and handwritten tasks.
     */
    private suspend fun buildTaskOcrMap(tasks: List<Task>): Map<String, String> {
        // Use TaskOcrService for both typed and handwritten text
        val density = context.resources.displayMetrics.density
        return TaskOcrService.buildOcrMap(tasks, density)
    }

    /**
     * Calculate pattern insights from recent task history.
     */
    private fun calculatePatternInsights(today: LocalDate): PatternInsights? {
        return try {
            val startDate = today.minusDays(PATTERN_ANALYSIS_DAYS.toLong())
            val startStr = startDate.format(dateFormatter)
            val endStr = today.format(dateFormatter)

            // Get tasks for the date range
            val tasks = taskRepository.getTasksForDateRange(startStr, endStr)
            if (tasks.isEmpty()) return null

            val completedTasks = tasks.filter { it.isCompleted }
            val tasksWithContent = tasks.filter {
                it.isCompleted || it.typedText?.isNotBlank() == true || it.priority.isNotBlank()
            }

            // Completion rate
            val completionRate = if (tasksWithContent.isNotEmpty()) {
                completedTasks.size.toFloat() / tasksWithContent.size
            } else 0f

            // Calculate completions by day of week
            val completionsByDayOfWeek = completedTasks
                .groupBy { task ->
                    try {
                        LocalDate.parse(task.dayDate, dateFormatter).dayOfWeek
                    } catch (e: Exception) {
                        DayOfWeek.MONDAY
                    }
                }
                .mapValues { it.value.size }

            val bestDay = completionsByDayOfWeek.maxByOrNull { it.value }?.key?.name?.lowercase()?.capitalize()
            val worstDay = completionsByDayOfWeek.minByOrNull { it.value }?.key?.name?.lowercase()?.capitalize()

            // Current streak
            val streak = calculateCurrentStreak(today)

            // Average completed per day
            val daysWithTasks = tasks.map { it.dayDate }.distinct().size
            val avgCompleted = if (daysWithTasks > 0) {
                completedTasks.size.toFloat() / daysWithTasks
            } else 0f

            PatternInsights(
                weeklyCompletionRate = completionRate,
                bestDay = bestDay,
                worstDay = worstDay,
                currentStreak = streak,
                avgCompletedPerDay = avgCompleted
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate pattern insights", e)
            null
        }
    }

    /**
     * Calculate current completion streak (consecutive days with completed tasks).
     */
    private fun calculateCurrentStreak(today: LocalDate): Int {
        val datesWithCompleted = taskRepository.getDatesWithCompletedTasks()

        var streak = 0
        var checkDate = today

        for (dateStr in datesWithCompleted) {
            val date = try {
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: Exception) { continue }

            // Allow today to be missing (day isn't over yet)
            if (date == today) {
                streak++
                checkDate = today.minusDays(1)
                continue
            }

            if (date == checkDate) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (date < checkDate) {
                // Gap in streak
                break
            }
        }

        return streak
    }

    /**
     * Find semantic relationships between tasks and events.
     * Used to provide scheduling hints in the prompt.
     */
    private suspend fun findTaskEventRelationships(
        tasks: List<Task>,
        events: List<LocalEvent>,
        taskOcrMap: Map<String, String>
    ): List<TaskEventMatcher.TaskEventRelation> {
        return try {
            val matcher = TaskEventMatcher(context)
            matcher.findRelationships(
                tasks = tasks,
                events = events,
                threshold = 0.4f,
                taskTextOverrides = taskOcrMap
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find task-event relationships", e)
            emptyList()
        }
    }

    private fun buildNearbyContextSummary(
        planningDate: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        rangeEvents: List<LocalEvent>,
        rangeTasks: List<Task>,
        taskOcrMap: Map<String, String>
    ): String? {
        val eventsByDay = rangeEvents.groupBy { it.dayId }
        val tasksByDay = rangeTasks.groupBy { it.dayDate }

        val lines = mutableListOf<String>()
        var day = rangeStart
        while (!day.isAfter(rangeEnd)) {
            if (day != planningDate) {
                val dayStr = day.format(dateFormatter)
                val dayEvents = eventsByDay[dayStr].orEmpty()
                val dayTasks = tasksByDay[dayStr].orEmpty().filter { isTaskRelevant(it, taskOcrMap) }
                if (dayEvents.isNotEmpty() || dayTasks.isNotEmpty()) {
                    val completedCount = dayTasks.count { it.isCompleted }
                    val isFuture = day.isAfter(planningDate)

                    // Build enriched event summaries with time, location, and description
                    val eventSummaries = dayEvents.take(4).map { event ->
                        buildString {
                            // Time prefix
                            val timePrefix = if (event.isAllDay) "[All day]" else {
                                val startTime = java.time.Instant.ofEpochMilli(event.startTime)
                                    .atZone(java.time.ZoneId.systemDefault())
                                String.format("[%02d:%02d]", startTime.hour, startTime.minute)
                            }
                            append("$timePrefix \"${event.title}\"")

                            // Location suffix
                            event.location?.takeIf { it.isNotBlank() }?.let {
                                append(" @ ${it.take(40)}")
                            }
                        }
                    }

                    // For future days, include brief descriptions of key events
                    val eventDescriptions = if (isFuture) {
                        dayEvents.take(3).mapNotNull { event ->
                            event.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                "    ${event.title}: ${desc.take(80)}"
                            }
                        }
                    } else emptyList()

                    val taskTitles = dayTasks
                        .sortedBy { it.isCompleted }
                        .mapNotNull { task ->
                            val text = resolveTaskText(task, taskOcrMap)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val status = if (task.isCompleted) "[x]" else "[ ]"
                            "$status ${text.take(40)}"
                        }
                        .take(3)

                    val line = buildString {
                        append("- ${day.format(dateFormatter)} (${day.dayOfWeek.name.lowercase().capitalize()})")
                        append(": ${dayEvents.size} events, ${dayTasks.size} tasks")
                        if (dayTasks.isNotEmpty()) {
                            append(" (${completedCount} completed)")
                        }
                        if (eventSummaries.isNotEmpty()) {
                            append("\n  Events: ")
                            append(eventSummaries.joinToString(", "))
                        }
                        eventDescriptions.forEach { desc ->
                            append("\n$desc")
                        }
                        if (taskTitles.isNotEmpty()) {
                            append("\n  Tasks: ")
                            append(taskTitles.joinToString(", "))
                        }
                    }

                    lines.add(line)
                }
            }
            day = day.plusDays(1)
        }

        if (lines.isEmpty()) return null

        return buildString {
            appendLine("## SURROUNDING DAYS (past 2 to next 5)")
            lines.forEach { appendLine(it) }
        }.trim()
    }

    private fun isTaskRelevant(task: Task, taskOcrMap: Map<String, String>): Boolean {
        return task.isCompleted ||
            task.priority.isNotBlank() ||
            !resolveTaskText(task, taskOcrMap).isNullOrBlank()
    }

    private fun resolveTaskText(task: Task, taskOcrMap: Map<String, String>): String? {
        return taskOcrMap[task.id]?.takeIf { it.isNotBlank() }
            ?: task.typedText?.takeIf { it.isNotBlank() }
            ?: task.recognizedText?.takeIf { it.isNotBlank() }
            ?: task.convertedText?.takeIf { it.isNotBlank() }
    }

    /**
     * Get a simplified context summary for debugging.
     */
    fun getContextSummary(ctx: AgenticContext): String {
        return buildString {
            appendLine("=== Agentic Context Summary ===")
            appendLine("Planning: ${ctx.planningDate} at ${ctx.currentHour}:00")
            appendLine("Capacity: ${ctx.todayCapacity.getCapacityStatus()} (${ctx.todayCapacity.capacityPercentage.toInt()}%)")
            appendLine("Events today: ${ctx.todayEvents.size}")
            appendLine("Tasks today: ${ctx.todayTasks.size}")
            appendLine("Overdue: ${ctx.overdueTasks.size}")
            appendLine("Zombies: ${ctx.zombieTasks.size}")
            ctx.patternInsights?.let {
                appendLine("Streak: ${it.currentStreak} days")
                appendLine("Week completion: ${(it.weeklyCompletionRate * 100).toInt()}%")
            }
        }
    }

    // Extension to capitalize first letter
    private fun String.capitalize(): String =
        this.lowercase().replaceFirstChar { it.uppercase() }
}
