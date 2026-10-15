package com.boox.day.data.ai

import com.boox.day.data.db.Task
import com.boox.day.ocr.semantic.model.DateSemanticContext
import java.time.LocalDate

/**
 * Extended context models for Smart Suggestions V2.
 * Provides richer context windows for AI-powered suggestions.
 */

// ═══════════════════════════════════════════════════════════════════════════
// CALENDAR CONTEXT (-5 to +10 days)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Extended calendar context spanning -5 to +10 days from current date.
 * Provides temporal awareness for follow-ups, preparation, and upcoming events.
 */
data class ExtendedCalendarContext(
    /** Events from -5 to -1 days (for follow-up suggestions) */
    val recentPastEvents: List<CalendarEventWithDate>,
    /** Today's events */
    val todayEvents: List<CalendarEventWithDate>,
    /** Tomorrow's events */
    val tomorrowEvents: List<CalendarEventWithDate>,
    /** Events from +2 to +7 days (this week) */
    val thisWeekEvents: List<CalendarEventWithDate>,
    /** Events from +8 to +10 days (next week preview) */
    val nextWeekEvents: List<CalendarEventWithDate>,
    /** High-priority upcoming deadlines extracted from events */
    val upcomingDeadlines: List<DeadlineEvent>
) {
    val totalEventCount: Int get() = recentPastEvents.size + todayEvents.size +
            tomorrowEvents.size + thisWeekEvents.size + nextWeekEvents.size

    val hasUpcomingEvents: Boolean get() = todayEvents.isNotEmpty() ||
            tomorrowEvents.isNotEmpty() || thisWeekEvents.isNotEmpty()
}

/**
 * Calendar event with associated date for context.
 */
data class CalendarEventWithDate(
    val date: LocalDate,
    val title: String,
    val startTime: String?,  // HH:mm format or null for all-day
    val endTime: String?,
    val location: String?,
    val description: String? = null,  // Event description (truncated at load time)
    val attendees: String? = null,    // Comma-separated attendee list
    val isAllDay: Boolean,
    val daysFromToday: Int  // Negative = past, positive = future
)

/**
 * Extracted deadline from calendar events.
 */
data class DeadlineEvent(
    val title: String,
    val date: LocalDate,
    val daysUntil: Int,
    val urgency: DeadlineUrgency
)

enum class DeadlineUrgency {
    CRITICAL,   // Today or tomorrow
    HIGH,       // 2-3 days
    MEDIUM,     // 4-7 days
    LOW         // 8+ days
}

// ═══════════════════════════════════════════════════════════════════════════
// TASK CONTEXT (-7 to +7 days)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Extended task context spanning -7 to +7 days.
 * Provides completion patterns, overdue tracking, and upcoming task awareness.
 */
data class ExtendedTaskContext(
    /** Completed tasks from past 7 days (for follow-up detection) */
    val recentCompletedTasks: List<TaskWithDate>,
    /** Incomplete tasks from past 7 days (overdue) */
    val recentIncompleteTasks: List<TaskWithDate>,
    /** Today's tasks */
    val todayTasks: List<TaskWithDate>,
    /** Tasks scheduled for next 7 days */
    val upcomingTasks: List<TaskWithDate>,
    /** Count of overdue tasks */
    val overdueCount: Int,
    /** Completion rate for last 7 days */
    val completionRateLast7Days: Float
) {
    val hasPendingWork: Boolean get() = overdueCount > 0 || todayTasks.any { !it.isCompleted }
}

/**
 * Task with associated date and text content.
 */
data class TaskWithDate(
    val task: Task,
    val date: LocalDate,
    val text: String,  // Typed text or OCR result
    val isCompleted: Boolean,
    val priority: String?,
    val daysFromToday: Int
)

// ═══════════════════════════════════════════════════════════════════════════
// PATTERN ANALYSIS (30-day window)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 30-day pattern analysis for personalized suggestions.
 */
data class UserPatternAnalysis(
    /** Overall completion rate (0.0-1.0) */
    val overallCompletionRate: Float,
    /** Completion rate by day of week */
    val completionByDayOfWeek: Map<String, Float>,
    /** Distribution of task categories */
    val categoryDistribution: Map<String, Float>,
    /** Categories that are underrepresented */
    val neglectedCategories: List<String>,
    /** Days with highest task count */
    val busiestDays: List<String>,
    /** Current streak of days with completed tasks */
    val currentStreak: Int,
    /** Average tasks per day */
    val avgTasksPerDay: Float,
    /** Work-life balance indicator */
    val workLifeBalance: WorkLifeBalance
)

enum class WorkLifeBalance {
    BALANCED,
    WORK_HEAVY,
    PERSONAL_HEAVY,
    UNKNOWN
}

// ═══════════════════════════════════════════════════════════════════════════
// MEMO CONTEXT (OCR from recent memo pages)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Result of memo OCR extraction for context.
 */
data class MemoContextResult(
    /** Combined normalized text from all scanned pages */
    val normalizedText: String,
    /** Extracted action items ("Review proposal", "Call John") */
    val actionItems: List<String>,
    /** Key topics mentioned ("budget", "Q1 planning") */
    val keyTopics: List<String>,
    /** People mentioned ("John", "Sarah") */
    val mentionedPeople: List<String>,
    /** Date references found ("Friday", "Dec 20") */
    val mentionedDates: List<String>,
    /** Project references ("Project Alpha") */
    val projectReferences: List<String>,
    /** Number of pages successfully scanned */
    val pagesScanned: Int,
    /** Any errors during OCR */
    val errors: List<String>
) {
    val hasContent: Boolean get() = normalizedText.isNotBlank() || actionItems.isNotEmpty()
}

/**
 * Selection of a memo page for OCR.
 */
data class MemoPageSelection(
    val pageId: String,
    val date: LocalDate,
    val pageIndex: Int,  // 0-based index within the day
    val hasStrokes: Boolean
)

// ═══════════════════════════════════════════════════════════════════════════
// COMBINED SUGGESTION CONTEXT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Full context for V2 smart suggestions generation.
 * Combines all context sources with user preferences.
 */
data class SuggestionContextV2(
    val preferences: AiPreferences?,
    val calendarContext: ExtendedCalendarContext,
    val taskContext: ExtendedTaskContext,
    val patternAnalysis: UserPatternAnalysis?,
    val semanticContexts: List<DateSemanticContext>?,
    val memoContext: MemoContextResult?,
    val currentDate: LocalDate,
    val currentHour: Int
) {
    /** Effective preferences (explicit or defaults) */
    val effectivePreferences: AiPreferences get() = preferences ?: AiPreferences()

    /** Whether we have rich context for better suggestions */
    val hasRichContext: Boolean get() =
        (preferences?.setupComplete == true) ||
        (!semanticContexts.isNullOrEmpty()) ||
        (memoContext?.hasContent == true) ||
        (patternAnalysis != null)
}
