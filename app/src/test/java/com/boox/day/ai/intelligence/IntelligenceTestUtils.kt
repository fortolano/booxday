package com.boox.day.ai.intelligence

import com.boox.day.data.ai.*
import com.boox.day.data.db.Task
import java.time.LocalDate
import java.util.Date

/**
 * Test utilities for Intelligence Engine tests.
 */
object IntelligenceTestUtils {

    val TODAY: LocalDate = LocalDate.now()

    /**
     * Create a test task with common defaults.
     */
    fun createTask(
        id: String = "task_${System.nanoTime()}",
        text: String = "Test task",
        dayDate: String = TODAY.toString(),
        priority: String = "",
        isCompleted: Boolean = false,
        dueDate: Long? = null,
        migrationCount: Int = 0,
        parentTaskId: String? = null,
        estimatedMinutes: Int? = null
    ): Task = Task(
        id = id,
        dayDate = dayDate,
        taskPageIndex = 0,
        rowIndex = 0,
        pageId = "page_$id",
        isCompleted = isCompleted,
        priority = priority,
        typedText = text,
        recognizedText = null,
        convertedText = null,
        parentTaskId = parentTaskId,
        migrationCount = migrationCount,
        originalDate = if (migrationCount > 0) dayDate else null,
        lastMigratedAt = if (migrationCount > 0) System.currentTimeMillis() else null,
        estimatedMinutes = estimatedMinutes,
        dueDay = com.boox.day.data.db.TaskDueDates.fromMillis(dueDate),
        createdAt = Date(),
        updatedAt = Date()
    )

    /**
     * Create a TaskItem for scoring.
     */
    fun createTaskItem(
        task: Task,
        daysFromToday: Int = 0,
        isOverdue: Boolean = false
    ): ScorableItem.TaskItem = ScorableItem.TaskItem(
        id = task.id,
        text = task.typedText ?: task.recognizedText ?: task.convertedText ?: "",
        task = task,
        daysFromToday = daysFromToday,
        isOverdue = isOverdue
    )

    /**
     * Create a SuggestionItem for scoring.
     */
    fun createSuggestionItem(
        text: String,
        category: String? = null,
        source: String? = null
    ): ScorableItem.SuggestionItem = ScorableItem.SuggestionItem(
        id = "suggestion_${System.nanoTime()}",
        text = text,
        category = category,
        source = source
    )

    /**
     * Create a minimal ScoringInput with given items.
     */
    fun createScoringInput(
        items: List<ScorableItem>,
        feature: IntelligenceFeature = IntelligenceFeature.SMART_SUGGESTIONS,
        currentDate: LocalDate = TODAY,
        currentHour: Int = 10,
        preferences: AiPreferences? = null,
        calendarContext: ExtendedCalendarContext? = null,
        taskContext: ExtendedTaskContext? = null,
        patternAnalysis: UserPatternAnalysis? = null
    ): ScoringInput = ScoringInput(
        feature = feature,
        currentDate = currentDate,
        currentHour = currentHour,
        preferences = preferences,
        calendarContext = calendarContext,
        taskContext = taskContext,
        patternAnalysis = patternAnalysis,
        semanticContexts = null,
        itemsToScore = items
    )

    /**
     * Create a test AiPreferences with defaults.
     */
    fun createPreferences(
        workPriority: Int = 3,
        healthPriority: Int = 3,
        familyPriority: Int = 3,
        socialPriority: Int = 3,
        educationPriority: Int = 3,
        financePriority: Int = 3,
        hobbyPriority: Int = 3,
        setupComplete: Boolean = true
    ): AiPreferences = AiPreferences(
        setupComplete = setupComplete,
        workPriority = workPriority,
        healthPriority = healthPriority,
        familyPriority = familyPriority,
        socialPriority = socialPriority,
        educationPriority = educationPriority,
        financePriority = financePriority,
        hobbyPriority = hobbyPriority
    )

    /**
     * Create a test ExtendedTaskContext.
     */
    fun createTaskContext(
        todayTasks: List<TaskWithDate> = emptyList(),
        upcomingTasks: List<TaskWithDate> = emptyList(),
        recentCompletedTasks: List<TaskWithDate> = emptyList(),
        recentIncompleteTasks: List<TaskWithDate> = emptyList(),
        overdueCount: Int = 0,
        completionRateLast7Days: Float = 0.7f
    ): ExtendedTaskContext = ExtendedTaskContext(
        recentCompletedTasks = recentCompletedTasks,
        recentIncompleteTasks = recentIncompleteTasks,
        todayTasks = todayTasks,
        upcomingTasks = upcomingTasks,
        overdueCount = overdueCount,
        completionRateLast7Days = completionRateLast7Days
    )

    /**
     * Create a TaskWithDate for context.
     */
    fun createTaskWithDate(
        task: Task,
        date: LocalDate = TODAY,
        daysFromToday: Int = 0
    ): TaskWithDate = TaskWithDate(
        task = task,
        date = date,
        text = task.typedText ?: task.recognizedText ?: task.convertedText ?: "",
        isCompleted = task.isCompleted,
        priority = task.priority,
        daysFromToday = daysFromToday
    )

    /**
     * Create a test calendar event context.
     */
    fun createCalendarContext(
        todayEvents: List<CalendarEventWithDate> = emptyList(),
        tomorrowEvents: List<CalendarEventWithDate> = emptyList(),
        thisWeekEvents: List<CalendarEventWithDate> = emptyList(),
        nextWeekEvents: List<CalendarEventWithDate> = emptyList(),
        recentPastEvents: List<CalendarEventWithDate> = emptyList(),
        upcomingDeadlines: List<DeadlineEvent> = emptyList()
    ): ExtendedCalendarContext = ExtendedCalendarContext(
        recentPastEvents = recentPastEvents,
        todayEvents = todayEvents,
        tomorrowEvents = tomorrowEvents,
        thisWeekEvents = thisWeekEvents,
        nextWeekEvents = nextWeekEvents,
        upcomingDeadlines = upcomingDeadlines
    )

    /**
     * Create a test CalendarEventWithDate.
     */
    fun createCalendarEvent(
        title: String,
        daysFromToday: Int = 0,
        isAllDay: Boolean = false,
        location: String? = null
    ): CalendarEventWithDate = CalendarEventWithDate(
        date = TODAY.plusDays(daysFromToday.toLong()),
        title = title,
        startTime = if (isAllDay) null else "10:00",
        endTime = if (isAllDay) null else "11:00",
        location = location,
        isAllDay = isAllDay,
        daysFromToday = daysFromToday
    )

    /**
     * Create a test UserPatternAnalysis.
     */
    fun createPatternAnalysis(
        overallCompletionRate: Float = 0.6f,
        completionByDayOfWeek: Map<String, Float> = mapOf(
            "MONDAY" to 0.7f,
            "TUESDAY" to 0.65f,
            "WEDNESDAY" to 0.6f,
            "THURSDAY" to 0.55f,
            "FRIDAY" to 0.5f
        ),
        categoryDistribution: Map<String, Float> = mapOf(
            "work" to 0.4f,
            "health" to 0.3f,
            "family" to 0.2f,
            "hobby" to 0.1f
        ),
        neglectedCategories: List<String> = emptyList(),
        busiestDays: List<String> = listOf("MONDAY", "TUESDAY"),
        currentStreak: Int = 3,
        avgTasksPerDay: Float = 5f,
        workLifeBalance: WorkLifeBalance = WorkLifeBalance.BALANCED
    ): UserPatternAnalysis = UserPatternAnalysis(
        overallCompletionRate = overallCompletionRate,
        completionByDayOfWeek = completionByDayOfWeek,
        categoryDistribution = categoryDistribution,
        neglectedCategories = neglectedCategories,
        busiestDays = busiestDays,
        currentStreak = currentStreak,
        avgTasksPerDay = avgTasksPerDay,
        workLifeBalance = workLifeBalance
    )

    /**
     * Convert LocalDate to epoch milliseconds.
     */
    fun LocalDate.toEpochMillis(): Long =
        this.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
