package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.data.PageDataManager
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.ai.ExtendedCalendarContext
import com.boox.day.data.ai.ExtendedTaskContext
import com.boox.day.data.ai.MemoContextResult
import com.boox.day.data.ai.SuggestionContextV2
import com.boox.day.data.ai.UserPatternAnalysis
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ocr.semantic.model.DateSemanticContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds the complete context for Smart Suggestions V2.
 *
 * Orchestrates parallel loading of:
 * - User preferences
 * - Extended calendar context (-5 to +10 days)
 * - Extended task context (-7 to +7 days)
 * - 30-day pattern analysis
 * - Memo OCR context (optional, slower)
 *
 * Supports progressive loading with callbacks for UI feedback.
 */
object SuggestionContextBuilder {
    private const val TAG = "SuggestionContextBuilder"

    /**
     * Build complete suggestion context with parallel loading.
     *
     * @param context Android context
     * @param currentDate Date to generate suggestions for
     * @param pageDataManager For memo page rendering (optional, for memo OCR)
     * @param includeMemoContext Whether to include memo OCR (adds 5-15s)
     * @param includePatternAnalysis Whether to include 30-day patterns
     * @param ocrResults Pre-computed OCR results for handwritten tasks
     * @param onStageChange Callback for progress updates
     * @return SuggestionContextV2 with all available context
     */
    suspend fun buildContext(
        context: Context,
        currentDate: LocalDate,
        pageDataManager: PageDataManager? = null,
        includeMemoContext: Boolean = false,
        includePatternAnalysis: Boolean = true,
        ocrResults: Map<String, String> = emptyMap(),
        onStageChange: (String) -> Unit = {}
    ): SuggestionContextV2 = coroutineScope {
        Log.d(TAG, "Building suggestion context for $currentDate")
        Log.d(TAG, "Options: memoContext=$includeMemoContext, patterns=$includePatternAnalysis")

        val currentHour = LocalTime.now().hour

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1: Load preferences (fast, synchronous)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Loading your preferences...")
        val preferencesRepo = AiPreferencesRepository(context)
        val preferences = preferencesRepo.getPreferences()
        Log.d(TAG, "Preferences loaded: setupComplete=${preferences.setupComplete}")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2: Parallel context loading
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Gathering context from your calendar and tasks...")

        // Launch parallel loaders
        val calendarDeferred = async {
            try {
                ExtendedContextLoaders.loadExtendedCalendarContext(context, currentDate)
            } catch (e: Exception) {
                Log.e(TAG, "Calendar context loading failed", e)
                ExtendedCalendarContext(
                    recentPastEvents = emptyList(),
                    todayEvents = emptyList(),
                    tomorrowEvents = emptyList(),
                    thisWeekEvents = emptyList(),
                    nextWeekEvents = emptyList(),
                    upcomingDeadlines = emptyList()
                )
            }
        }

        val taskDeferred = async {
            try {
                ExtendedContextLoaders.loadExtendedTaskContext(context, currentDate, ocrResults)
            } catch (e: Exception) {
                Log.e(TAG, "Task context loading failed", e)
                ExtendedTaskContext(
                    recentCompletedTasks = emptyList(),
                    recentIncompleteTasks = emptyList(),
                    todayTasks = emptyList(),
                    upcomingTasks = emptyList(),
                    overdueCount = 0,
                    completionRateLast7Days = 0f
                )
            }
        }

        val patternsDeferred = if (includePatternAnalysis) {
            async {
                try {
                    onStageChange("Analyzing your task patterns...")
                    ExtendedContextLoaders.analyzeUserPatterns(context, currentDate)
                } catch (e: Exception) {
                    Log.e(TAG, "Pattern analysis failed", e)
                    null
                }
            }
        } else null

        val semanticDeferred = async(Dispatchers.IO) {
            loadSemanticContexts(context, currentDate)
        }

        val semanticContexts = semanticDeferred.await()
        val semanticAvailable = semanticContexts?.any { hasSemanticSignals(it) } ?: false
        if (includeMemoContext && pageDataManager != null && semanticAvailable) {
            Log.d(TAG, "Skipping memo OCR; semantic context available")
        }

        val memoDeferred = if (includeMemoContext && pageDataManager != null && !semanticAvailable) {
            async {
                try {
                    onStageChange("Reading your recent memos...")
                    MemoContextExtractor.extractMemoContext(
                        context = context,
                        currentDate = currentDate,
                        pageDataManager = pageDataManager,
                        onProgress = { current, total ->
                            onStageChange("Reading memos ($current/$total)...")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Memo context extraction failed", e)
                    null
                }
            }
        } else null

        // Await all results
        val calendarContext = calendarDeferred.await()
        val taskContext = taskDeferred.await()
        val patternAnalysis = patternsDeferred?.await()
        val memoContext = memoDeferred?.await()

        Log.d(TAG, "Context loading complete:")
        Log.d(TAG, "  Calendar: ${calendarContext.totalEventCount} events")
        Log.d(TAG, "  Tasks: ${taskContext.todayTasks.size} today, ${taskContext.overdueCount} overdue")
        Log.d(TAG, "  Patterns: ${patternAnalysis?.overallCompletionRate ?: "N/A"}")
        Log.d(TAG, "  Semantic: ${semanticContexts?.size ?: 0} day contexts")
        Log.d(TAG, "  Memos: ${memoContext?.pagesScanned ?: 0} pages")

        SuggestionContextV2(
            preferences = preferences,
            calendarContext = calendarContext,
            taskContext = taskContext,
            patternAnalysis = patternAnalysis,
            semanticContexts = semanticContexts,
            memoContext = memoContext,
            currentDate = currentDate,
            currentHour = currentHour
        )
    }

    /**
     * Build context prompt section from SuggestionContextV2.
     * Formats context for AI consumption.
     */
    suspend fun buildContextPromptSection(
        context: Context,
        ctx: SuggestionContextV2
    ): String {
        val taskScopeSection = buildTaskScopeSection(ctx.taskContext)
        val calendarSection = buildCalendarSection(ctx.calendarContext)
        val patternSection = buildPatternSection(ctx.patternAnalysis)
        val memoFallbackSection = buildMemoFallbackSection(ctx.memoContext)

        val contextBlock = TaskAiContextBuilderV1.buildContextBlock(
            context = context,
            date = ctx.currentDate,
            rangeStart = ctx.currentDate.minusDays(3),
            rangeEnd = ctx.currentDate.plusDays(3),
            includeBooxNotes = true,
            includeSourceTypes = setOf(OcrSource.MEMO, OcrSource.TASK),
            taskScopeSection = taskScopeSection,
            calendarSection = calendarSection,
            patternSection = patternSection,
            semanticFallbackSection = memoFallbackSection,
            semanticContextsOverride = ctx.semanticContexts
        )

        return contextBlock.asPromptBlock()
    }

    /**
     * Overdue, today's, upcoming and recently completed tasks for the prompt. A task without
     * words is counted but not listed (29 Oct 2026: it showed as an empty "- [x] " line).
     */
    @androidx.annotation.VisibleForTesting
    internal fun buildTaskScopeSection(tasks: ExtendedTaskContext): String {
        val lines = mutableListOf<String>()
        var remaining = TaskAiContextBuilderV1.MAX_TASK_LINES

        if (tasks.overdueCount > 0 && remaining > 0) {
            lines.add("Overdue (${tasks.overdueCount}):")
            tasks.recentIncompleteTasks.forEach { task ->
                if (remaining <= 0 || task.text.isBlank()) return@forEach
                val text = truncateLine(task.text)
                lines.add("- [${task.daysFromToday}d] $text")
                remaining--
            }
        }

        if (tasks.todayTasks.isNotEmpty() && remaining > 0) {
            lines.add("Today (${tasks.todayTasks.size}):")
            tasks.todayTasks.forEach { task ->
                if (remaining <= 0 || task.text.isBlank()) return@forEach
                val status = if (task.isCompleted) "x" else "o"
                val text = truncateLine(task.text)
                lines.add("- [$status] $text")
                remaining--
            }
        }

        if (tasks.upcomingTasks.any { it.text.isNotBlank() } && remaining > 0) {
            lines.add("Upcoming:")
            tasks.upcomingTasks.forEach { task ->
                if (remaining <= 0 || task.text.isBlank()) return@forEach
                val text = truncateLine(task.text)
                lines.add("- [+${task.daysFromToday}d] $text")
                remaining--
            }
        }

        if (tasks.recentCompletedTasks.any { it.text.isNotBlank() } && remaining > 0) {
            lines.add("Recently Completed:")
            tasks.recentCompletedTasks.forEach { task ->
                if (remaining <= 0 || task.text.isBlank()) return@forEach
                val text = truncateLine(task.text)
                lines.add("- [${task.daysFromToday}d] $text")
                remaining--
            }
        }

        lines.add("7-day completion rate: ${(tasks.completionRateLast7Days * 100).toInt()}%")
        return lines.joinToString("\n")
    }

    /**
     * Format a single event with enriched detail (location, attendees, description).
     */
    private fun formatEventRich(event: com.boox.day.data.ai.CalendarEventWithDate, prefix: String): List<String> {
        val result = mutableListOf<String>()
        val time = if (event.isAllDay) "(all day)" else event.startTime?.let {
            val end = event.endTime?.let { e -> "-$e" } ?: ""
            "at $it$end"
        } ?: ""
        val locationPart = event.location?.takeIf { it.isNotBlank() }?.let { " @ ${it.take(50)}" } ?: ""
        val attendeePart = event.attendees?.takeIf { it.isNotBlank() }?.let { " | w/ ${it.take(80)}" } ?: ""
        result.add("$prefix${event.title} $time$locationPart$attendeePart")
        event.description?.takeIf { it.isNotBlank() }?.let { desc ->
            result.add("  ${desc.take(120)}")
        }
        return result
    }

    private fun buildCalendarSection(calendar: ExtendedCalendarContext): String {
        val lines = mutableListOf<String>()
        if (calendar.recentPastEvents.isNotEmpty()) {
            lines.add("Recent events:")
            calendar.recentPastEvents.take(5).forEach { event ->
                lines.addAll(formatEventRich(event, "- [${event.daysFromToday}d] "))
            }
        }
        if (calendar.todayEvents.isNotEmpty()) {
            lines.add("Today:")
            calendar.todayEvents.forEach { event ->
                lines.addAll(formatEventRich(event, "- "))
            }
        }
        if (calendar.tomorrowEvents.isNotEmpty()) {
            lines.add("Tomorrow:")
            calendar.tomorrowEvents.forEach { event ->
                lines.addAll(formatEventRich(event, "- "))
            }
        }
        if (calendar.thisWeekEvents.isNotEmpty()) {
            lines.add("This week (${calendar.thisWeekEvents.size} events):")
            calendar.thisWeekEvents.take(5).forEach { event ->
                lines.addAll(formatEventRich(event, "- [+${event.daysFromToday}d] "))
            }
        }
        if (calendar.nextWeekEvents.isNotEmpty()) {
            lines.add("Next week (${calendar.nextWeekEvents.size} events):")
            calendar.nextWeekEvents.take(3).forEach { event ->
                lines.addAll(formatEventRich(event, "- [+${event.daysFromToday}d] "))
            }
        }
        if (calendar.upcomingDeadlines.isNotEmpty()) {
            lines.add("Upcoming deadlines:")
            calendar.upcomingDeadlines.forEach { deadline ->
                lines.add("- ${deadline.title} in ${deadline.daysUntil} days (${deadline.urgency})")
            }
        }
        return lines.joinToString("\n")
    }

    private fun buildPatternSection(patterns: UserPatternAnalysis?): String? {
        patterns ?: return null
        return buildString {
            appendLine("Pattern insights:")
            appendLine("- Overall completion: ${(patterns.overallCompletionRate * 100).toInt()}%")
            appendLine("- Current streak: ${patterns.currentStreak} days")
            appendLine("- Work-life balance: ${patterns.workLifeBalance}")
            if (patterns.neglectedCategories.isNotEmpty()) {
                appendLine("- Neglected areas: ${patterns.neglectedCategories.joinToString(", ")}")
            }
            if (patterns.busiestDays.isNotEmpty()) {
                appendLine("- Busiest days: ${patterns.busiestDays.joinToString(", ")}")
            }
        }.trim()
    }

    private fun buildMemoFallbackSection(memos: MemoContextResult?): String? {
        memos ?: return null
        if (!memos.hasContent) return null

        return buildString {
            appendLine("Recent memo highlights:")
            if (memos.actionItems.isNotEmpty()) {
                appendLine("- Action items: ${memos.actionItems.take(5).joinToString("; ")}")
            }
            if (memos.keyTopics.isNotEmpty()) {
                appendLine("- Topics: ${memos.keyTopics.take(10).joinToString(", ")}")
            }
            if (memos.mentionedPeople.isNotEmpty()) {
                appendLine("- People: ${memos.mentionedPeople.take(10).joinToString(", ")}")
            }
        }.trim()
    }

    private fun truncateLine(text: String): String {
        val trimmed = text.trim()
        if (trimmed.length <= TaskAiContextBuilderV1.MAX_TASK_LINE_CHARS) return trimmed
        return trimmed.take(TaskAiContextBuilderV1.MAX_TASK_LINE_CHARS - 3) + "..."
    }

    private suspend fun loadSemanticContexts(
        context: Context,
        currentDate: LocalDate
    ): List<DateSemanticContext>? {
        return try {
            val service = SemanticGeniusService(context)
            val sources = setOf(OcrSource.TASK, OcrSource.MEMO)

            val hasData = sources.any { service.hasAnyOcrData(it) }
            if (!hasData) return null

            val dates = (-2..3).map { currentDate.minusDays(it.toLong()) }
            dates.mapNotNull { date ->
                try {
                    service.getDateContext(date.toString(), sources)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load semantic contexts: ${e.message}")
            null
        }
    }

    private fun hasSemanticSignals(context: DateSemanticContext): Boolean {
        return context.dominantDomains.isNotEmpty() ||
            context.dominantIntents.isNotEmpty() ||
            context.allKeywords.isNotEmpty() ||
            context.allEntities.isNotEmpty() ||
            context.pendingActionItems.isNotEmpty()
    }
}
