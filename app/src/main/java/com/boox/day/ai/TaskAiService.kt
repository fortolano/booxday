package com.boox.day.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.ai.AiPreferences.Companion.keptOptions
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.ai.ScoredContext
import com.boox.day.data.db.Task
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Stroke
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.TaskOcrService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * AI service for Task-specific operations.
 * Leverages existing AI infrastructure (AiClient, aiRequest, models).
 *
 * Features:
 * - Voice to Task: Audio → transcription → TextInferenceService → structured task data
 * - Recognize Handwriting: Vision → TextInferenceService → text conversion for task pages
 * - Sort by Priority: AI-powered task reordering across all pages
 *
 * Uses [TextInferenceService] for intelligent text correction and semantic inference
 * after audio transcription and handwriting recognition.
 */
object TaskAiService {
    private const val TAG = "TaskAiService"
    private val gson = Gson()
    private val multiWhitespaceRegex = Regex("\\s+")
    private val suggestionCategoriesByName = SuggestionCategory.values().associateBy { it.name }
    private val allowedDailyInsightTypes = setOf("productivity", "pattern", "suggestion", "schedule", "tip")
    private val allowedWeeklyInsightTypes = setOf("overview", "streak", "pattern", "suggestion", "achievement", "tip")
    private val allowedBalanceValues = setOf("balanced", "work-heavy", "personal-heavy")
    private val allowedWeekdayNames = setOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    private val weeklyCategoryKeywords = listOf(
        SuggestionCategory.FINANCE to listOf(
            "budget", "bill", "pay", "save", "invest", "bank", "money",
            "expense", "tax", "insurance", "invoice", "account"
        ),
        SuggestionCategory.WORK to listOf(
            "work", "meeting", "email", "project", "client", "deadline",
            "presentation", "report", "office", "boss", "colleague", "job"
        ),
        SuggestionCategory.HEALTH to listOf(
            "exercise", "gym", "run", "walk", "doctor", "dentist", "health",
            "meditation", "yoga", "therapy", "sleep", "vitamin"
        ),
        SuggestionCategory.ERRANDS to listOf(
            "buy", "shop", "grocery", "pick up", "pickup", "return",
            "appointment", "pharmacy", "store", "mail", "drop off", "deliver"
        ),
        SuggestionCategory.SOCIAL to listOf(
            "call", "friend", "dinner", "lunch", "party", "birthday",
            "meet", "coffee", "visit", "catch up"
        ),
        SuggestionCategory.LEARNING to listOf(
            "study", "read", "learn", "course", "class", "practice",
            "research", "book", "tutorial", "lesson"
        ),
        SuggestionCategory.PERSONAL to listOf(
            "home", "family", "clean", "organize", "personal", "house",
            "kids", "parent", "spouse"
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // MODEL CONFIGURATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun getReasoningModels(): List<AiModel> =
        ModelConfigProvider.getReasoningFallbackChain()

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parsed result from Voice to Task.
     * Supports multiple tasks in a single voice command.
     */
    data class VoiceTaskResult(
        val tasks: List<ParsedTask>,
        val confidence: Float,
        val originalIntent: String,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && tasks.isNotEmpty()
        val hasValidTasks: Boolean get() = tasks.isNotEmpty() && tasks.any { it.text.isNotBlank() }

        companion object {
            fun empty(error: String) = VoiceTaskResult(
                tasks = emptyList(),
                confidence = 0f,
                originalIntent = "",
                error = error
            )
        }
    }

    private fun resolveSuggestionCount(preferences: com.boox.day.data.ai.AiPreferences?): Int =
        preferences.keptOptions().suggestionCount.count

    /**
     * A single parsed task from voice input.
     */
    data class ParsedTask(
        @SerializedName("text") val text: String,
        @SerializedName("date") val date: String?, // yyyy-MM-dd format
        @SerializedName("priority") val priority: String?, // "high", "medium", "low"
        @SerializedName("isFollowUp") val isFollowUp: Boolean = false
    )

    /**
     * JSON structure expected from LLM for voice input.
     */
    private data class VoiceTaskResponse(
        @SerializedName("tasks") val tasks: List<ParsedTask>,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("originalIntent") val originalIntent: String
    )

    /**
     * Result from handwriting recognition.
     */
    data class RecognitionResult(
        val recognizedTasks: List<RecognizedTask>,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && recognizedTasks.isNotEmpty()
    }

    /**
     * A single recognized task from handwriting.
     */
    data class RecognizedTask(
        val taskIndex: Int, // 0-based index in the task list
        val pageNumber: Int,
        val originalStrokes: Boolean, // true if had handwritten strokes
        val recognizedText: String
    )

    /**
     * Stats for handwriting recognition transparency.
     */
    data class OcrStats(
        val detected: Int,    // Rows with ink found
        val recognized: Int,  // Successfully read as text
        val skipped: Int      // Could not read / unclear
    )

    /**
     * Result from Sort by Priority.
     */
    data class SortResult(
        val sortedTasks: List<SortedTask>,
        val reasoning: String,
        val ocrStats: OcrStats? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && sortedTasks.isNotEmpty()
    }

    /**
     * A task with its AI-assigned priority.
     * Includes original task metadata for proper apply logic.
     */
    data class SortedTask(
        val originalIndex: Int,       // Original rowIndex within the page
        val originalPage: Int,        // taskPageIndex
        val text: String,             // Task text content (typed or OCR'd)
        val suggestedPriority: Int,   // 1 = highest, higher number = lower priority
        val reason: String,           // AI's reasoning for this priority
        val userPriority: String = "",// User's original priority (A/B/C or empty)
        val isCompleted: Boolean = false, // Original completion status
        val taskId: String = "",      // Original task ID for direct updates
        val isHandwritten: Boolean = false, // True if text was OCR'd from handwriting
        val isChild: Boolean = false  // True if this is a subtask (child)
    )

    /**
     * Processing state for UI feedback.
     */
    sealed class ProcessingState {
        data object Idle : ProcessingState()
        data object Recording : ProcessingState()
        data object Transcribing : ProcessingState()
        data object Processing : ProcessingState()
        data class Success(val message: String) : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DAILY SUMMARY DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A single insight from the daily summary AI analysis.
     */
    data class DailySummaryInsight(
        @SerializedName("type") val type: String, // "productivity", "pattern", "suggestion"
        @SerializedName("text") val text: String
    )

    /**
     * Complete daily summary with tasks and AI insights.
     * Enhanced with OCR support for handwritten tasks.
     * Works intelligently even with no tasks - uses events, preferences, etc.
     */
    data class DailySummary(
        val date: String,
        val completedTasks: List<Task>,
        val incompleteTasks: List<Task>,
        val completionRate: Float, // 0.0 - 1.0
        val aiInsights: List<DailySummaryInsight>,
        val handwrittenOcrMap: Map<String, String> = emptyMap(), // taskId -> OCR text for handwritten tasks
        val eventsCount: Int = 0, // Number of calendar events for context
        val error: String? = null
    )

    /**
     * JSON structure expected from LLM for insights.
     */
    private data class InsightsResponse(
        @SerializedName("insights") val insights: List<DailySummaryInsight>
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // WEEKLY SUMMARY DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Complete weekly summary with 7-day analysis and AI insights.
     * Focuses on patterns, trends, and recurring issues.
     */
    data class WeeklySummary(
        val weekRange: String,                    // "Dec 6 - Dec 12, 2025"
        val totalCompleted: Int,
        val totalTasks: Int,
        val completionRate: Float,                // 0.0 - 1.0
        val dailyStats: List<DayStats>,           // Stats per day
        val bestDay: DayStats?,                   // Highest completion rate
        val worstDay: DayStats?,                  // Lowest completion rate
        val recurringIncompleteTasks: List<RecurringTask>, // Tasks incomplete 2+ times
        val categoryBreakdown: List<CategoryStats>,        // Work-life balance
        val aiInsights: List<WeeklyInsight>,
        val warning: String? = null,              // Parsing or normalization warnings
        val weeklyEventsCount: Int = 0,           // Total events in the week (for context)
        val error: String? = null
    )

    /**
     * Stats for a single day in the weekly summary.
     */
    data class DayStats(
        val date: String,                         // "2025-12-09"
        val dayOfWeek: String,                    // "Monday"
        val completed: Int,
        val total: Int,
        val completionRate: Float
    )

    /**
     * A task that appeared incomplete multiple times during the week.
     */
    data class RecurringTask(
        val text: String,
        val occurrences: Int,                     // How many days it appeared incomplete
        val dates: List<String>                   // Which dates it was incomplete
    )

    /**
     * Category stats for work-life balance analysis.
     */
    data class CategoryStats(
        val category: String,                     // "WORK", "PERSONAL", etc.
        val displayName: String,                  // "Work", "Personal", etc.
        val count: Int,
        val percentage: Float                     // Of total tasks
    )

    /**
     * A single insight from the weekly summary AI analysis.
     */
    data class WeeklyInsight(
        @SerializedName("type") val type: String, // "trend", "pattern", "balance", "suggestion"
        @SerializedName("text") val text: String
    )

    /**
     * JSON structure expected from LLM for weekly insights.
     */
    private data class WeeklyInsightsResponse(
        @SerializedName("insights") val insights: List<WeeklyInsight>,
        @SerializedName("categoryBreakdown") val categoryBreakdown: List<CategoryJson>? = null
    )

    private data class WeeklyInsightsResult(
        val insights: List<WeeklyInsight>,
        val categories: List<CategoryStats>,
        val warning: String? = null
    )

    private data class CategoryJson(
        @SerializedName("category") val category: String,
        @SerializedName("count") val count: Int
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Category for task suggestions to help with work-life balance.
     */
    enum class SuggestionCategory(val displayName: String, val emoji: String) {
        WORK("Work", "💼"),
        PERSONAL("Personal", "🏠"),
        HEALTH("Health & Wellness", "🏃"),
        ERRANDS("Errands", "🛒"),
        SOCIAL("Social", "👥"),
        LEARNING("Learning", "📚"),
        FINANCE("Finance", "💰"),
        OTHER("Other", "📝")
    }

    /**
     * A single task suggestion from the AI.
     */
    data class TaskSuggestion(
        @SerializedName("text") val text: String,
        @SerializedName("reason") val reason: String, // Why this was suggested
        @SerializedName("confidence") val confidence: Float = 0.7f,
        @SerializedName("category") val category: String = "OTHER", // Category for work-life balance
        @SerializedName("isFollowUp") val isFollowUp: Boolean = false // Whether this follows up on a completed task
    ) {
        /** Get the category enum, defaulting to OTHER if invalid */
        fun categoryEnum(): SuggestionCategory {
            return try {
                SuggestionCategory.valueOf(category.uppercase())
            } catch (e: Exception) {
                SuggestionCategory.OTHER
            }
        }
    }

    /**
     * Result from smart suggestions generation.
     */
    data class SuggestionsResult(
        val suggestions: List<TaskSuggestion>,
        val patternInsights: PatternInsights? = null,
        val error: String? = null
    )

    /**
     * Insights about user's task patterns from 10-day history analysis.
     */
    data class PatternInsights(
        val completionRate: Float, // 0.0-1.0 completion rate over 10 days
        val busiestDay: String?, // Day of week with most tasks
        val commonCategories: List<String>, // Most common task categories
        val workLifeBalance: String?, // "balanced", "work-heavy", "personal-heavy"
        val streakDays: Int // Days in a row with completed tasks
    )

    /**
     * JSON structure expected from LLM for suggestions.
     */
    private data class SuggestionsResponse(
        @SerializedName("suggestions") val suggestions: List<TaskSuggestion>,
        @SerializedName("patternInsights") val patternInsights: PatternInsightsJson? = null
    )

    private data class PatternInsightsJson(
        @SerializedName("completionRate") val completionRate: Float = 0f,
        @SerializedName("busiestDay") val busiestDay: String? = null,
        @SerializedName("commonCategories") val commonCategories: List<String> = emptyList(),
        @SerializedName("workLifeBalance") val workLifeBalance: String? = null,
        @SerializedName("streakDays") val streakDays: Int = 0
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK BREAKDOWN DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A single subtask from task breakdown.
     */
    data class SubTask(
        @SerializedName("text") val text: String,
        @SerializedName("order") val order: Int // Suggested sequence
    )

    /**
     * Result from task breakdown.
     */
    data class BreakdownResult(
        val originalTask: String,
        val subtasks: List<SubTask>,
        val keepOriginal: Boolean, // AI recommendation
        val error: String? = null
    )

    /**
     * JSON structure expected from LLM for breakdown.
     */
    private data class BreakdownResponse(
        @SerializedName("subtasks") val subtasks: List<SubTask>,
        @SerializedName("keepOriginal") val keepOriginal: Boolean = false
    )

    /**
     * A task eligible for breakdown, with support for handwritten content.
     * Used in the selection dialog to show both typed and OCR'd tasks.
     */
    data class TaskForBreakdown(
        val task: Task,
        val displayText: String,       // typedText or OCR'd text
        val isHandwritten: Boolean,    // true if text was OCR'd from strokes
        val ocrConfidence: Float = 1.0f
    )

    /**
     * Result from grouped task breakdown with placement information.
     * Includes the target location for the parent + subtasks group.
     */
    data class GroupedBreakdownResult(
        val originalTask: String,
        val subtasks: List<SubTask>,
        val placementPageIndex: Int,   // Page where the group will be placed
        val placementStartRow: Int,    // Starting row index for the group
        val movedToNewPage: Boolean,   // True if moved from current page
        val error: String? = null
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lightweight version of DailySummary for caching.
     * Stores task IDs instead of full Task objects to allow fresh data lookup.
     */
    data class CachedDailySummary(
        val date: String,
        val completedTaskIds: List<String>,
        val incompleteTaskIds: List<String>,
        val completionRate: Float,
        val aiInsights: List<DailySummaryInsight>,
        val handwrittenOcrMap: Map<String, String>
    )

    /**
     * Lightweight version of WeeklySummary for caching.
     */
    data class CachedWeeklySummary(
        val weekRange: String,
        val totalCompleted: Int,
        val totalTasks: Int,
        val completionRate: Float,
        val dailyStats: List<DayStats>,
        val bestDay: DayStats?,
        val worstDay: DayStats?,
        val recurringIncompleteTasks: List<RecurringTask>,
        val categoryBreakdown: List<CategoryStats>,
        val aiInsights: List<WeeklyInsight>,
        val warning: String? = null
    )

    /**
     * Serialize a DailySummary to JSON for caching.
     */
    fun serializeDailySummary(summary: DailySummary): String {
        val cached = CachedDailySummary(
            date = summary.date,
            completedTaskIds = summary.completedTasks.map { it.id },
            incompleteTaskIds = summary.incompleteTasks.map { it.id },
            completionRate = summary.completionRate,
            aiInsights = summary.aiInsights,
            handwrittenOcrMap = summary.handwrittenOcrMap
        )
        return gson.toJson(cached)
    }

    /**
     * Deserialize a DailySummary from cached JSON.
     * Requires task lookup function to reconstruct Task objects.
     */
    fun deserializeDailySummary(
        json: String,
        taskLookup: (List<String>) -> List<Task>
    ): DailySummary? {
        return try {
            val cached = gson.fromJson(json, CachedDailySummary::class.java)
            val completedTasks = taskLookup(cached.completedTaskIds)
            val incompleteTasks = taskLookup(cached.incompleteTaskIds)
            val totalCount = completedTasks.size + incompleteTasks.size
            val completionRate = if (totalCount > 0) {
                completedTasks.size.toFloat() / totalCount
            } else 0f
            DailySummary(
                date = cached.date,
                completedTasks = completedTasks,
                incompleteTasks = incompleteTasks,
                completionRate = completionRate,
                aiInsights = cached.aiInsights,
                handwrittenOcrMap = cached.handwrittenOcrMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize DailySummary: ${e.message}")
            null
        }
    }

    /**
     * Compute a stable hash of daily task content for cache validation.
     */
    fun computeDailySummaryContentHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sortedTasks = tasks.sortedWith(
            compareBy<Task> { it.taskPageIndex }
                .thenBy { it.rowIndex }
                .thenBy { it.id }
        )

        val buffer = StringBuilder()
        for (task in sortedTasks) {
            buffer.append(task.dayDate).append('|')
            buffer.append(task.taskPageIndex).append('|')
            buffer.append(task.rowIndex).append('|')
            buffer.append(task.id).append('|')
            buffer.append(if (task.isCompleted) '1' else '0').append('|')
            buffer.append(task.priority).append('|')
            buffer.append(task.dueDay ?: "").append('|')
            buffer.append(task.parentTaskId ?: "").append('|')
            buffer.append(task.typedText ?: "").append('|')
            buffer.append(task.convertedText ?: "").append('|')
            buffer.append(task.recognizedText ?: "").append('\n')
        }

        val hashBytes = digest.digest(buffer.toString().toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute a stable hash of weekly task and stroke content for cache validation.
     */
    fun computeWeeklySummaryContentHash(
        tasks: List<Task>,
        strokesByPage: Map<String, List<Stroke>>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sortedTasks = tasks.sortedWith(
            compareBy<Task> { it.dayDate }
                .thenBy { it.taskPageIndex }
                .thenBy { it.rowIndex }
                .thenBy { it.id }
        )

        val buffer = StringBuilder()
        for (task in sortedTasks) {
            buffer.append(task.dayDate).append('|')
            buffer.append(task.taskPageIndex).append('|')
            buffer.append(task.rowIndex).append('|')
            buffer.append(task.id).append('|')
            buffer.append(if (task.isCompleted) '1' else '0').append('|')
            buffer.append(task.priority).append('|')
            buffer.append(task.dueDay ?: "").append('|')
            buffer.append(task.parentTaskId ?: "").append('|')
            buffer.append(task.typedText ?: "").append('|')
            buffer.append(task.convertedText ?: "").append('|')
            buffer.append(task.recognizedText ?: "").append('\n')
        }

        val sortedPages = strokesByPage.toSortedMap()
        for ((pageId, strokes) in sortedPages) {
            buffer.append("page|").append(pageId).append('\n')
            strokes.sortedBy { it.id }.forEach { stroke ->
                buffer.append(stroke.id).append('|')
                buffer.append(stroke.updatedAt.time).append('|')
                buffer.append(stroke.top).append(',')
                buffer.append(stroke.bottom).append(',')
                buffer.append(stroke.left).append(',')
                buffer.append(stroke.right).append('\n')
            }
        }

        val hashBytes = digest.digest(buffer.toString().toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Serialize a WeeklySummary to JSON for caching.
     * WeeklySummary doesn't contain Task objects, so it serializes directly.
     */
    fun serializeWeeklySummary(summary: WeeklySummary): String {
        val cached = CachedWeeklySummary(
            weekRange = summary.weekRange,
            totalCompleted = summary.totalCompleted,
            totalTasks = summary.totalTasks,
            completionRate = summary.completionRate,
            dailyStats = summary.dailyStats,
            bestDay = summary.bestDay,
            worstDay = summary.worstDay,
            recurringIncompleteTasks = summary.recurringIncompleteTasks,
            categoryBreakdown = summary.categoryBreakdown,
            aiInsights = summary.aiInsights,
            warning = summary.warning
        )
        return gson.toJson(cached)
    }

    /**
     * Deserialize a WeeklySummary from cached JSON.
     */
    fun deserializeWeeklySummary(json: String): WeeklySummary? {
        return try {
            val cached = gson.fromJson(json, CachedWeeklySummary::class.java)
            WeeklySummary(
                weekRange = cached.weekRange,
                totalCompleted = cached.totalCompleted,
                totalTasks = cached.totalTasks,
                completionRate = cached.completionRate,
                dailyStats = cached.dailyStats,
                bestDay = cached.bestDay,
                worstDay = cached.worstDay,
                recurringIncompleteTasks = cached.recurringIncompleteTasks,
                categoryBreakdown = cached.categoryBreakdown,
                aiInsights = cached.aiInsights,
                warning = cached.warning
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize WeeklySummary: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VOICE TO TASK
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW UNIFIED API - Used by TranscriptionService integration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse pre-transcribed text into structured task data.
     *
     * This is the NEW primary API for TranscriptionService integration.
     * Skips Stage 1 (audio transcription) since TranscriptionService already handled it.
     *
     * Pipeline:
     * - Stage 1: SKIPPED (text already transcribed by TranscriptionService)
     * - Stage 2: TextInferenceService (semantic correction)
     * - Stage 3: Structured JSON parsing (GPT models with fallback)
     *
     * @param transcribedText Pre-transcribed text from SpeechRecognizer or Gemini
     * @param currentDate The currently selected date in the calendar
     * @param onStageChange Callback for UI progress feedback
     * @return VoiceTaskResult with parsed tasks
     */
    suspend fun parseTranscribedText(
        transcribedText: String,
        currentDate: LocalDate = LocalDate.now(),
        onStageChange: (String) -> Unit = {}
    ): VoiceTaskResult {
        if (transcribedText.isBlank()) {
            return VoiceTaskResult.empty("No speech detected")
        }

        Log.d(TAG, "parseTranscribedText: '${transcribedText.take(100)}...'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1: SKIPPED - TranscriptionService already transcribed
        // ─────────────────────────────────────────────────────────────────────

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2: Raw Text → Corrected Text (TextInferenceService)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Understanding what you said...")
        Log.d(TAG, "Stage 2: Refining transcript with TextInferenceService...")

        val inferenceResult = TextInferenceService.refineText(
            rawText = transcribedText,
            context = InferenceContext.TASK,
            config = InferenceConfig.DEFAULT
        )

        val correctedText = inferenceResult.refinedText
        Log.d(TAG, "Stage 2 complete: Corrected = '$correctedText'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3: Corrected Text → Structured Task (GPT models with fallback)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Creating your tasks...")
        Log.d(TAG, "Stage 3: Parsing corrected text to structured task...")

        // Uses 3×3 retry via AiRetryExecutor
        val modelsToTry = getReasoningModels()
        val taskSystemPrompt = buildVoiceTaskSystemPrompt(currentDate)
        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = null,
            date = currentDate,
            rangeStart = currentDate.minusDays(3),
            rangeEnd = currentDate,
            includeBooxNotes = false,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO)
        )
        // Oct 2026: the context is labelled as reference, so the AI doesn't turn a recent task or
        // memo line into a new task; tasks come only from what the user said
        val userPrompt = if (contextBlock.isNotBlank()) {
            buildString {
                appendLine(TaskAiContextBuilderV1.REFERENCE_ONLY_HEADER)
                appendLine(contextBlock)
                appendLine()
                appendLine("## WHAT THE USER SAID (make tasks only from this)")
                append(correctedText)
            }
        } else {
            correctedText
        }

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(taskSystemPrompt)
                    userMessage(userPrompt)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "Stage 3 complete (${model.displayName}): ${response.data.take(500)}...")
                val parsed = parseVoiceTaskResponse(response.data)
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> VoiceTaskResult.empty(result.message)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL: VOICE TO TASK PROMPTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * System prompt for Stage 3: Parse corrected text into structured task JSON.
     *
     * Note: Input text has already been cleaned by Stage 2 (TextInferenceService),
     * so filler words and basic corrections are already handled.
     */
    private fun buildVoiceTaskSystemPrompt(currentDate: LocalDate): String {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentYear = today.year
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        return """
You are a task parser. Extract task(s) from text and return structured JSON.

TODAY: ${today.format(dateFormatter)}
TOMORROW: ${tomorrow.format(dateFormatter)}
CURRENT YEAR: $currentYear
CURRENTLY VIEWING: ${currentDate.format(dateFormatter)}

## EXTRACTION RULES

1. MULTIPLE TASKS: Split when each part is a distinct action
   - "Buy milk and call mom" → 2 tasks (different actions)
   - "Buy milk and eggs" → 1 task (single shopping trip)
   - "Finish report and email it to boss" → 2 tasks (distinct steps)
   - Split by "and", "also", "then" only when actions are independent

2. DATE PARSING:
   - "today" → ${today.format(dateFormatter)}
   - "tomorrow" → ${tomorrow.format(dateFormatter)}
   - "next Monday" → calculate from today
   - "this Friday" → closest upcoming Friday
   - "December 20th" or "Dec 20" → $currentYear-12-20 (or ${currentYear + 1} if date has passed)
   - "in 3 days" → add to today
   - No date mentioned → null

3. PRIORITY DETECTION:
   - "urgent", "important", "asap", "critical", "must do", "top priority" → "high"
   - "when you can", "eventually", "low priority", "maybe", "not urgent" → "low"
   - No priority indicator → "medium"

4. FOLLOW-UP DETECTION:
   - "follow up on", "follow up with", "check on", "check back" → isFollowUp: true
   - "remind me to [action]" → isFollowUp: false (this is a command prefix, not a follow-up)
   - "remind me about [topic]" → isFollowUp: true (checking on something existing)

5. TEXT CLEANUP:
   - Remove command prefixes: "add task", "create task", "new task", "add a"
   - Remove polite prefixes: "please", "can you", "I need to", "remind me to", "don't forget to"
   - Capitalize first letter: "buy milk" → "Buy milk"
   - Preserve the user's wording - do not rephrase

## NON-TASK INPUT

If input contains no actionable task (greetings, questions, random statements):
- Return empty tasks array
- Set confidence below 0.3
- Explain in originalIntent

Example: "Hello how are you" → {"tasks": [], "confidence": 0.1, "originalIntent": "Greeting, no task detected"}

## OUTPUT FORMAT

Return ONLY raw JSON (no markdown code blocks):

{
  "tasks": [
    {
      "text": "Task description",
      "date": "yyyy-MM-dd" or null,
      "priority": "high" | "medium" | "low",
      "isFollowUp": true | false
    }
  ],
  "confidence": 0.0-1.0,
  "originalIntent": "Brief summary of what was understood"
}

## CONFIDENCE SCORING
- 0.9-1.0: Clear task with explicit details
- 0.7-0.9: Task understood, minor inference needed
- 0.5-0.7: Understood intent but some ambiguity
- 0.3-0.5: Uncertain interpretation
- 0.0-0.3: No task detected or couldn't understand
""".trimIndent()
    }

    // Note: User prompt is not needed - correctedText is passed directly to the model.
    // The system prompt is comprehensive enough for task parsing.

    private fun parseVoiceTaskResponse(json: String): VoiceTaskResult {
        val cleanJson = JsonExtractor.extract(json)
        return try {
            val parsed = gson.fromJson(cleanJson, VoiceTaskResponse::class.java)
            VoiceTaskResult(
                tasks = parsed.tasks,
                confidence = parsed.confidence,
                originalIntent = parsed.originalIntent
            )
        } catch (e: JsonSyntaxException) {
            // Length only (Oct 2026): AI answers carry the user's tasks and events, and error
            // lines stay in release builds and in crash reports
            Log.e(TAG, "Failed to parse voice task response (${json.length} chars)", e)
            VoiceTaskResult.empty("Could not understand response")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing voice task", e)
            VoiceTaskResult.empty("Processing error")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SORT BY PRIORITY
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // DAILY SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════

    private fun parseInsightsResponse(json: String): List<DailySummaryInsight> {
        return try {
            val cleanJson = JsonExtractor.extract(json)
            val parsed = gson.fromJson(cleanJson, InsightsResponse::class.java)
            val seen = mutableSetOf<String>()
            parsed.insights
                .mapNotNull { insight ->
                    val text = collapseWhitespace(insight.text)
                    if (text.isBlank()) return@mapNotNull null

                    val key = normalizeAiTextKey(text)
                    if (!seen.add(key)) return@mapNotNull null

                    DailySummaryInsight(
                        type = normalizeDailyInsightType(insight.type),
                        text = text
                    )
                }
                .take(3)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse insights response (${json.length} chars)", e)
            emptyList()
        }
    }

    /**
     * Generate intelligent insights when there are no tasks.
     * Uses events, AI preferences, day of week patterns, and general productivity tips.
     * This makes the Daily Summary feature useful even with no tasks.
     */
    private suspend fun generateNoTasksInsights(
        currentDate: LocalDate,
        events: List<LocalEvent>,
        onStageChange: (String) -> Unit,
        context: Context?
    ): List<DailySummaryInsight> {
        val dayOfWeek = currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val isWeekend = currentDate.dayOfWeek.value >= 6

        // Build context for AI
        val eventsSummary = if (events.isNotEmpty()) {
            val eventsList = events.take(5).joinToString("\n") { event ->
                val timeStr = if (event.isAllDay) "All day" else {
                    val hour = java.time.Instant.ofEpochMilli(event.startTime)
                        .atZone(ZoneId.systemDefault()).hour
                    "${String.format("%02d", hour)}:00"
                }
                "- $timeStr: ${event.title}"
            }
            "Today's events (${events.size}):\n$eventsList"
        } else {
            "No events scheduled"
        }

        val contextBlock = if (context != null) {
            TaskAiContextBuilderV1.buildContextBlock(
                context = context,
                date = currentDate,
                includeBooxNotes = false,
                includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO),
                taskScopeSection = null,
                calendarSection = null,
                patternSection = null,
                semanticFallbackSection = null
            ).asPromptBlock()
        } else ""

        val prompt = buildString {
            appendLine("Today is $dayOfWeek, ${currentDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}")
            appendLine()
            appendLine("The user has NO TASKS recorded for today, but here's their context:")
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine(contextBlock)
            }
            appendLine()
            appendLine("## Schedule")
            appendLine(eventsSummary)
            appendLine()
            appendLine("## Request")
            appendLine("Generate 2-3 helpful, personalized insights that:")
            appendLine("1. Acknowledge the current situation (${if (events.isNotEmpty()) "busy schedule" else "open day"})")
            appendLine("2. Provide value based on their priorities, day of week, and available time")
            appendLine("3. Suggest how they might use the app to plan effectively")
            appendLine()
            appendLine("Be a helpful 'Chief of Staff' - don't just say 'no tasks'. Provide real value!")
            if (isWeekend) appendLine("Note: It's a weekend - consider work-life balance.")
        }

        onStageChange("Generating personalized insights...")

        // Try to get AI-generated insights using 3×3 retry pattern
        val models = getReasoningModels()
        val systemPromptText = """
You are an intelligent day planning assistant.
Generate helpful, personalized daily insights in JSON format.
Be encouraging and practical. Provide VALUE even when there are no tasks.
Analyze their schedule, preferences, and suggest how they can make the most of their day.
Return raw JSON only. Match the schema exactly. Do not add extra keys or explanatory text.

OUTPUT FORMAT (JSON only, no markdown):
{
  "insights": [
    {"type": "schedule", "text": "Analysis of their day/events"},
    {"type": "suggestion", "text": "Actionable recommendation"},
    {"type": "tip", "text": "Productivity tip based on context"}
  ]
}
        """.trimIndent()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPromptText)
                    userMessage(prompt)
                    jsonSchema("daily_summary_insights", StructuredOutputSchemas.dailyInsightsSchema)
                    temperature(0.6f)
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "No-tasks insights (${model.displayName}): ${response.data.take(300)}...")
                val parsed = parseInsightsResponse(response.data)
                parsed.takeIf { it.isNotEmpty() }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> generateFallbackNoTasksInsights(events, isWeekend, dayOfWeek)
        }
    }

    /**
     * Fallback insights when AI is unavailable.
     * Still provides intelligent, context-aware insights.
     */
    private fun generateFallbackNoTasksInsights(
        events: List<LocalEvent>,
        isWeekend: Boolean,
        dayOfWeek: String
    ): List<DailySummaryInsight> {
        val insights = mutableListOf<DailySummaryInsight>()

        // Event-based insight
        if (events.isNotEmpty()) {
            insights.add(DailySummaryInsight(
                type = "schedule",
                text = "You have ${events.size} event${if (events.size > 1) "s" else ""} scheduled. " +
                    "Consider adding tasks to prepare for or follow up on these appointments."
            ))
        } else {
            insights.add(DailySummaryInsight(
                type = "schedule",
                text = if (isWeekend) {
                    "Your $dayOfWeek looks open! A good time for rest, hobbies, or catching up on personal projects."
                } else {
                    "Your $dayOfWeek is wide open. Perfect for tackling that project you've been putting off!"
                }
            ))
        }

        // Actionable suggestion
        insights.add(DailySummaryInsight(
            type = "suggestion",
            text = "Try using Voice to Task to quickly capture what's on your mind, or Optimize My Day for AI-powered scheduling suggestions."
        ))

        // Day-specific tip
        val dayTip = when (dayOfWeek) {
            "Monday" -> "Mondays are great for weekly planning. Set your intentions for the week!"
            "Tuesday", "Wednesday", "Thursday" -> "Mid-week is ideal for deep work. Block focused time for your priorities."
            "Friday" -> "End the week strong! Review what you've accomplished and plan for next week."
            "Saturday" -> "Weekend time! Balance rest with light tasks you've been meaning to get to."
            "Sunday" -> "Perfect day for weekly review and mental preparation for the week ahead."
            else -> "Make today count!"
        }
        insights.add(DailySummaryInsight(type = "tip", text = dayTip))

        return insights
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEEKLY SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate weekly summary analyzing the past 7 days of tasks.
     * Focuses on patterns, trends, recurring issues, and work-life balance.
     *
     * @param weeklyTasks Map of date string to tasks for that day (7 days)
     * @param startDate The start date of the week
     * @param endDate The end date of the week
     * @param onStageChange Callback for progress updates
     */
    private fun resolveTaskText(task: Task, textMap: Map<String, String?>): String? {
        return task.typedText?.takeIf { it.isNotBlank() }
            ?: task.convertedText?.takeIf { it.isNotBlank() }
            ?: task.recognizedText?.takeIf { it.isNotBlank() }
            ?: textMap[task.id]?.takeIf { it.isNotBlank() }
    }

    private fun normalizeRecurringTaskText(text: String): String {
        val lower = text.lowercase()
        val cleaned = lower.replace(Regex("[^a-z0-9\\s]"), " ")
        val collapsed = cleaned.replace(Regex("\\s+"), " ").trim()
        return collapsed.replace(
            Regex("^(buy|get|do|make|call|email|send|schedule|plan|finish|start|pay|read|review|check|write|draft|reply|follow up|follow-up)\\s+"),
            ""
        ).trim()
    }

    /**
     * Generate intelligent weekly insights when there are no tasks.
     * Uses events, AI preferences, and general productivity patterns.
     */
    private suspend fun generateNoTasksWeeklyInsights(
        startDate: LocalDate,
        endDate: LocalDate,
        weeklyEvents: List<LocalEvent>,
        onStageChange: (String) -> Unit,
        context: Context?
    ): List<WeeklyInsight> {
        val weekDateFormatter = DateTimeFormatter.ofPattern("MMM d")
        val weekRange = "${startDate.format(weekDateFormatter)} - ${endDate.format(weekDateFormatter)}"

        // Build event summary by day
        val eventsByDay = weeklyEvents.groupBy { event ->
            java.time.Instant.ofEpochMilli(event.startTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }

        val eventsSummary = if (weeklyEvents.isNotEmpty()) {
            buildString {
                appendLine("Week's events (${weeklyEvents.size} total):")
                eventsByDay.entries.sortedBy { it.key }.take(5).forEach { (date, events) ->
                    val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                    appendLine("- $dayName: ${events.size} event${if (events.size > 1) "s" else ""}")
                }
            }
        } else {
            "No events scheduled this week"
        }

        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = context,
            date = endDate,
            rangeStart = startDate,
            rangeEnd = endDate,
            includeBooxNotes = true,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO)
        )

        val prompt = buildString {
            appendLine("Week: $weekRange, ${endDate.year}")
            appendLine()
            appendLine("The user had NO TASKS TRACKED this week, but here's their context:")
            appendLine()
            if (contextBlock.isNotBlank()) {
                appendLine(contextBlock)
                appendLine()
            }
            appendLine("## Events")
            appendLine(eventsSummary)
            appendLine()
            appendLine("## Request")
            appendLine("Generate 2-3 helpful weekly insights that:")
            appendLine("1. Acknowledge the situation constructively (maybe they're new, or use the app differently)")
            appendLine("2. Highlight value from events if present")
            appendLine("3. Encourage engagement with the app's task features")
            appendLine("4. Provide actionable suggestions for next week")
            appendLine()
            appendLine("Be a supportive 'Chief of Staff' - don't criticize, inspire!")
        }

        onStageChange("Generating weekly insights...")

        val models = getReasoningModels()
        val systemPromptText = """
You are an intelligent weekly review assistant.
Generate helpful, encouraging weekly insights in JSON format.
Even without task data, provide VALUE by analyzing events and giving actionable suggestions.
Be positive and forward-looking.
Return raw JSON only. Match the schema exactly. Omit optional fields instead of null. Do not add extra keys.

OUTPUT FORMAT (JSON only):
{
  "insights": [
    {"type": "overview", "title": "Week Summary", "text": "High-level observation"},
    {"type": "suggestion", "title": "For Next Week", "text": "Actionable recommendation"},
    {"type": "tip", "title": "Pro Tip", "text": "How to get more from the app"}
  ]
}
        """.trimIndent()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPromptText)
                    userMessage(prompt)
                    jsonSchema("weekly_summary_insights", StructuredOutputSchemas.weeklyInsightsSchema)
                    temperature(0.6f)
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "No-tasks weekly insights (${model.displayName}): ${response.data.take(300)}...")
                parseWeeklyInsightsResponse(response.data)
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> generateFallbackNoTasksWeeklyInsights(weeklyEvents, weekRange)
        }
    }

    /**
     * Fallback weekly insights when AI is unavailable.
     */
    private fun generateFallbackNoTasksWeeklyInsights(
        weeklyEvents: List<LocalEvent>,
        weekRange: String
    ): List<WeeklyInsight> {
        val insights = mutableListOf<WeeklyInsight>()

        // Event-based insight
        if (weeklyEvents.isNotEmpty()) {
            insights.add(WeeklyInsight(
                type = "overview",
                text = "Busy Week! You had ${weeklyEvents.size} event${if (weeklyEvents.size > 1) "s" else ""} this week ($weekRange). " +
                    "Consider adding related tasks next week to prepare for or follow up on your appointments."
            ))
        } else {
            insights.add(WeeklyInsight(
                type = "overview",
                text = "Fresh Start: This week ($weekRange) was untracked. That's okay! " +
                    "Start fresh next week by capturing tasks as they come to mind."
            ))
        }

        // Actionable suggestion
        insights.add(WeeklyInsight(
            type = "suggestion",
            text = "Next Week's Game Plan: Try the 'Optimize My Day' feature each morning for AI-powered scheduling suggestions, " +
                "or use 'Voice to Task' to quickly capture thoughts throughout the day."
        ))

        // Encouragement
        insights.add(WeeklyInsight(
            type = "tip",
            text = "Build the Habit: Even tracking 1-2 tasks daily can reveal powerful insights about your productivity patterns over time. Small steps lead to big gains!"
        ))

        return insights
    }

    /**
     * Parse weekly insights JSON response.
     */
    private fun parseWeeklyInsightsResponse(json: String): List<WeeklyInsight> {
        return try {
            val cleanJson = JsonExtractor.extract(json)
            val response = gson.fromJson(cleanJson, WeeklyInsightsJsonResponseV3::class.java)
            sanitizeWeeklyInsights(response?.insights)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse weekly insights: ${e.message}")
            emptyList()
        }
    }

    /**
     * JSON structure for weekly insights response.
     */
    private data class WeeklyInsightsJsonResponse(
        @SerializedName("insights") val insights: List<WeeklyInsightJson>?
    )

    private data class WeeklyInsightJson(
        @SerializedName("type") val type: String?,
        @SerializedName("title") val title: String?,
        @SerializedName("text") val text: String?
    )

    private data class WeeklyInsightsJsonResponseV3(
        @SerializedName("insights") val insights: List<WeeklyInsightJson>?,
        @SerializedName("categories") val categories: List<CategoryJson>? = null,
        @SerializedName("categoryBreakdown") val categoryBreakdown: List<CategoryJson>? = null
    )

    /**
     * Normalize category counts to total tasks and generate percentages.
     */
    private fun normalizeCategoryBreakdown(
        rawCategories: List<CategoryJson>?,
        totalTasks: Int
    ): Pair<List<CategoryStats>, String?> {
        if (rawCategories.isNullOrEmpty() || totalTasks <= 0) {
            val warning = if (totalTasks > 0 && rawCategories.isNullOrEmpty()) {
                "Category breakdown missing from AI response."
            } else null
            return Pair(emptyList(), warning)
        }

        val counts = mutableMapOf<SuggestionCategory, Int>()
        rawCategories.forEach { cat ->
            val category = try {
                SuggestionCategory.valueOf(cat.category.uppercase())
            } catch (e: Exception) {
                SuggestionCategory.OTHER
            }
            val count = cat.count.coerceAtLeast(0)
            counts[category] = (counts[category] ?: 0) + count
        }

        val sum = counts.values.sum()
        if (sum == 0) {
            return Pair(emptyList(), "Category breakdown returned zero counts.")
        }

        var warning: String? = null
        if (sum < totalTasks) {
            val remainder = totalTasks - sum
            counts[SuggestionCategory.OTHER] = (counts[SuggestionCategory.OTHER] ?: 0) + remainder
            warning = "Category counts below total tasks; assigned remainder to OTHER."
        } else if (sum > totalTasks) {
            val ratio = totalTasks.toFloat() / sum.toFloat()
            val normalized = counts.mapValues { (_, count) ->
                (count * ratio).toInt()
            }.toMutableMap()
            var normalizedSum = normalized.values.sum()
            var remainder = totalTasks - normalizedSum
            val sortedCategories = counts.entries
                .sortedByDescending { it.value }
                .map { it.key }
            for (category in sortedCategories) {
                if (remainder <= 0) break
                normalized[category] = (normalized[category] ?: 0) + 1
                remainder--
            }
            counts.clear()
            counts.putAll(normalized)
            warning = "Category counts exceeded total tasks; normalized."
        }

        val categoryStats = counts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { (category, count) ->
                CategoryStats(
                    category = category.name,
                    displayName = category.displayName,
                    count = count,
                    percentage = if (totalTasks > 0) count.toFloat() / totalTasks else 0f
                )
            }

        return Pair(categoryStats, warning)
    }

    private fun computeLocalWeeklyCategoryBreakdown(
        weeklyTasks: Map<String, List<Task>>,
        textMap: Map<String, String?>
    ): List<CategoryStats> {
        val allTasks = weeklyTasks.values.flatten()
        if (allTasks.isEmpty()) return emptyList()

        val counts = mutableMapOf<SuggestionCategory, Int>()
        allTasks.forEach { task ->
            val category = detectLocalWeeklyCategory(resolveTaskText(task, textMap))
            counts[category] = (counts[category] ?: 0) + 1
        }

        return counts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { (category, count) ->
                CategoryStats(
                    category = category.name,
                    displayName = category.displayName,
                    count = count,
                    percentage = count.toFloat() / allTasks.size.coerceAtLeast(1)
                )
            }
    }

    private fun detectLocalWeeklyCategory(text: String?): SuggestionCategory {
        val normalized = collapseWhitespace(text).lowercase(Locale.ROOT)
        if (normalized.isBlank()) return SuggestionCategory.OTHER

        for ((category, keywords) in weeklyCategoryKeywords) {
            if (keywords.any { normalized.contains(it) }) {
                return category
            }
        }

        return SuggestionCategory.OTHER
    }

    /**
     * Parse weekly insights response including category breakdown.
     */
    private fun parseWeeklyInsightsResponse(json: String, totalTasks: Int): WeeklyInsightsResult {
        return try {
            val cleanJson = JsonExtractor.extract(json)
            val parsed = gson.fromJson(cleanJson, WeeklyInsightsJsonResponseV3::class.java)

            val insights = sanitizeWeeklyInsights(parsed?.insights)

            val categoriesRaw = parsed?.categories ?: parsed?.categoryBreakdown
            val (categoryBreakdown, warning) = normalizeCategoryBreakdown(categoriesRaw, totalTasks)

            WeeklyInsightsResult(
                insights = insights,
                categories = categoryBreakdown,
                warning = warning
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse weekly insights response (${json.length} chars)", e)
            WeeklyInsightsResult(emptyList(), emptyList(), "Failed to parse weekly insights response.")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun parseSuggestionsResponse(
        json: String,
        maxCount: Int? = null
    ): SuggestionsResult {
        return try {
            val cleanJson = JsonExtractor.extract(json)
            val parsed = gson.fromJson(cleanJson, SuggestionsResponse::class.java)

            val suggestions = sanitizeSuggestions(parsed.suggestions, maxCount)
            val patternInsights = sanitizePatternInsights(parsed.patternInsights)

            SuggestionsResult(
                suggestions = suggestions,
                patternInsights = patternInsights
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse suggestions response (${json.length} chars)", e)
            SuggestionsResult(
                suggestions = emptyList(),
                error = "Could not parse suggestions"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENHANCED SMART SUGGESTIONS (with 10-day history, OCR, calendar context)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Data class for a day's task history used in enhanced suggestions.
     */
    data class DayTaskHistory(
        val date: LocalDate,
        val tasks: List<Task>,
        val completedCount: Int,
        val totalCount: Int
    )

    /**
     * Simplified calendar event for context in suggestions.
     */
    data class CalendarEventContext(
        val title: String,
        val startTime: String, // "HH:mm"
        val isToday: Boolean,
        val isTomorrow: Boolean
    )

    /**
     * Handwritten task requiring OCR before suggestions analysis.
     */
    data class HandwrittenTaskForOcr(
        val task: Task,
        val strokes: List<com.boox.day.data.db.Stroke>
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS V2 (with the AI options)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Smart Suggestions' option rules (29 Oct 2026): how many, and the kept options that differ from
     * the defaults (Health or Social suggestions off, Vacation). The other old AI Preferences answers
     * didn't change the suggestions in a measured test and are no longer sent.
     */
    internal fun buildSuggestionPreferenceGuidance(
        preferences: com.boox.day.data.ai.AiPreferences?,
        targetCount: Int
    ): String {
        val options = preferences.keptOptions()
        return buildString {
            appendLine("## PREFERENCES")
            appendLine("Target suggestion count: $targetCount")
            if (!options.includeHealthReminders) {
                appendLine("- Avoid health suggestions unless the context makes them clearly necessary.")
            }
            if (!options.includeSocialReminders) {
                appendLine("- Avoid social suggestions unless the context makes them clearly necessary.")
            }
            if (options.vacationMode) {
                appendLine("- Vacation mode is active. Keep suggestions light and restorative.")
            }
        }.trim()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK BREAKDOWN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get tasks eligible for breakdown with OCR support for handwritten content.
     * Uses TaskOcrService (Digital Ink API) for on-device recognition.
     *
     * @param tasks List of tasks on the current page
     * @param density Screen density for dp-to-px conversion
     * @param onStageChange Callback for progress updates
     * @param parentTaskIds Set of task IDs that already have children (will be excluded)
     * @return List of tasks eligible for breakdown (typed + handwritten)
     */
    suspend fun getTasksForBreakdownWithOcr(
        tasks: List<Task>,
        density: Float,
        onStageChange: (String) -> Unit = {},
        parentTaskIds: Set<String> = emptySet()
    ): List<TaskForBreakdown> {
        val result = mutableListOf<TaskForBreakdown>()

        // Add tasks with typed text (not completed, not a child task, not already a parent)
        // Exclude child tasks to prevent creating deep hierarchy chains
        // Exclude parent tasks to prevent re-breaking down already broken tasks
        val typedTasks = tasks.filter {
            it.typedText?.isNotBlank() == true &&
            !it.isCompleted &&
            it.parentTaskId == null &&
            it.id !in parentTaskIds
        }
        for (task in typedTasks) {
            result.add(TaskForBreakdown(
                task = task,
                displayText = task.typedText!!,
                isHandwritten = false
            ))
        }

        // ═══════════════════════════════════════════════════════════════════════
        // Get text for handwritten tasks via TaskOcrService (replaces Vision AI)
        // ═══════════════════════════════════════════════════════════════════════
        val tasksWithoutText = tasks.filter {
            it.typedText.isNullOrBlank() &&
            !it.isCompleted &&
            it.parentTaskId == null &&
            it.id !in parentTaskIds
        }

        if (tasksWithoutText.isNotEmpty()) {
            onStageChange("Detecting handwritten tasks...")

            val ocrResult = TaskOcrService.getTextForTasks(tasksWithoutText, density)

            for ((taskId, ocrData) in ocrResult.results) {
                if (ocrData.text.isNotBlank()) {
                    val task = tasksWithoutText.find { it.id == taskId } ?: continue
                    result.add(TaskForBreakdown(
                        task = task,
                        displayText = ocrData.text,
                        isHandwritten = ocrData.isHandwritten
                    ))
                }
            }
        }

        Log.d(TAG, "Tasks for breakdown: ${result.size} (${typedTasks.size} typed + ${result.size - typedTasks.size} handwritten)")
        return result
    }

    private fun parseBreakdownResponse(json: String, originalTask: String): BreakdownResult {
        return try {
            val cleanJson = JsonExtractor.extract(json)
            val parsed = gson.fromJson(cleanJson, BreakdownResponse::class.java)
            val subtasks = parsed?.subtasks
                ?.sortedBy { it.order }
                ?.mapNotNull { subTask ->
                    val text = collapseWhitespace(subTask.text)
                    if (text.isBlank()) return@mapNotNull null
                    SubTask(
                        text = text,
                        order = subTask.order.coerceAtLeast(1)
                    )
                }
                ?.distinctBy { normalizeAiTextKey(it.text) }
                ?.take(7)
                ?.mapIndexed { index, subTask ->
                    SubTask(
                        text = subTask.text,
                        order = index + 1
                    )
                }
                .orEmpty()
            val keepOriginal = parsed?.keepOriginal ?: false

            if (subtasks.size < 3) {
                return BreakdownResult(
                    originalTask = originalTask,
                    subtasks = emptyList(),
                    keepOriginal = keepOriginal,
                    error = "AI generated too few subtasks. Please try again."
                )
            }
            BreakdownResult(
                originalTask = originalTask,
                subtasks = subtasks,
                keepOriginal = keepOriginal
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse breakdown response (${json.length} chars)", e)
            BreakdownResult(
                originalTask = originalTask,
                subtasks = emptyList(),
                keepOriginal = false,
                error = "Could not parse breakdown"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 METHODS - INTELLIGENCE ENGINE INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate smart suggestions using pre-scored context from the Intelligence Engine.
     *
     * This V3 method leverages algorithmic pre-scoring to provide more consistent,
     * explainable prioritization. The pre-computed scores guide the LLM's suggestions.
     *
     * @param context Android context for data access
     * @param scoredContext Pre-scored context from IntelligenceEngine
     * @param suggestionContext Full suggestion context (from SuggestionContextBuilder)
     * @param onStageChange Callback for progress updates
     * @return SuggestionsResult with prioritized suggestions
     */
    suspend fun generateSmartSuggestionsV3(
        context: android.content.Context,
        scoredContext: com.boox.day.data.ai.ScoredContext,
        suggestionContext: com.boox.day.data.ai.SuggestionContextV2,
        onStageChange: (String) -> Unit = {}
    ): SuggestionsResult {
        Log.d(TAG, "Generating Smart Suggestions V3 with ${scoredContext.scoredItems.size} pre-scored items")
        Log.d(TAG, "Feature: ${scoredContext.feature}, Avg score: ${scoredContext.aggregateMetrics.avgFinalScore}")

        onStageChange("Creating personalized suggestions with pre-analysis...")

        // Build the base context section (existing V2 logic)
        val existingContextSection = SuggestionContextBuilder.buildContextPromptSection(
            context = context,
            ctx = suggestionContext
        )

        // Add the pre-scored context section
        val preScoreSection = scoredContext.asPromptSection()

        // Tasks the user already has, to avoid duplicates: today's (ticked too), overdue and the
        // coming days' (29 Oct 2026: today's with words only, so ticked handwritten tasks were missing)
        val existingTaskTexts = SuggestionDuplicates.tasksToAvoid(suggestionContext.taskContext)

        // Build enhanced prompt with pre-scores
        val prompt = buildV3SuggestionsPrompt(
            existingContextSection = existingContextSection,
            preScoreSection = preScoreSection,
            existingTasks = existingTaskTexts,
            preferences = suggestionContext.preferences,
            scoredContext = scoredContext
        )

        // Use same model chain as V2
        val modelsToTry = getReasoningModels()
        val systemPromptText = buildV3SystemPrompt(suggestionContext.preferences, scoredContext)
        val targetCount = resolveSuggestionCount(suggestionContext.preferences)

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                Log.d(TAG, "V3 Generation: Trying ${model.displayName}...")
                aiRequest {
                    model(model)
                    systemPrompt(systemPromptText)
                    userMessage(prompt)
                    jsonSchema("smart_suggestions_v3", StructuredOutputSchemas.suggestionsSchema)
                    temperature(0.4f)
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, _ ->
                Log.d(TAG, "V3 suggestions response: ${response.data.take(500)}...")
                onStageChange("Organizing your suggestions...")
                // An answer that can't be read goes to the next model (29 Oct 2026: it ended the
                // feature with "Could not parse suggestions", no other model tried)
                parseSuggestionsResponse(response.data).takeIf { it.error == null }?.let { parsed ->
                    // A suggestion that repeats a task the user has is dropped (29 Oct 2026)
                    val unique = SuggestionDuplicates.withoutRepeats(parsed.suggestions, existingTaskTexts) { it.text }
                    if (unique.size < parsed.suggestions.size) {
                        Log.i(TAG, "Dropped ${parsed.suggestions.size - unique.size} suggestion(s) repeating existing tasks")
                    }
                    // Health or Social suggestions off (AI options): those ideas are dropped too
                    val kept = SuggestionOptionsFilter.keep(unique, suggestionContext.preferences.keptOptions()) { it.category }
                    if (kept.size < unique.size) {
                        Log.i(TAG, "Dropped ${unique.size - kept.size} suggestion(s) the AI options switch off")
                    }
                    parsed.copy(suggestions = kept.take(targetCount))
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> SuggestionsResult(
                suggestions = emptyList(),
                // What went wrong (no Wi-Fi, no credit, ...); was always "temporarily unavailable" (Oct 2026)
                error = result.message
            )
        }
    }

    /**
     * Build V3 system prompt with pre-score awareness.
     */
    private fun buildV3SystemPrompt(
        preferences: com.boox.day.data.ai.AiPreferences?,
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): String {
        val targetCount = resolveSuggestionCount(preferences)

        return buildString {
            appendLine("You suggest high-value tasks for today by deeply analyzing ALL provided context.")
            appendLine()
            appendLine("JOB")
            appendLine("- Ingest every context section: USER CONTEXT, TASK SCOPE, SEMANTIC MEMORY, CALENDAR CONTEXT, BOOX NOTES CONTEXT, and PRE-COMPUTED SCORES.")
            appendLine("- Cross-reference these sections to produce suggestions no generic assistant could make.")
            appendLine("- Favor high-scored items unless surrounding context clearly argues otherwise.")
            appendLine("- Convert strong signals into concrete next actions, not generic advice.")
            appendLine()
            appendLine("HARD RULES")
            appendLine("- Do not repeat or lightly reword existing tasks.")
            appendLine("- Do not invent events, deadlines, or motivations not present in the input.")
            appendLine("- If a high-scored item already exists on today's list, suggest the next useful step instead.")
            appendLine("- If only a few strong suggestions exist, return fewer rather than padding with filler.")
            appendLine("- Every reason MUST cite specific named evidence from the input (a task name, event title, keyword, entity, note title, or score signal).")
            appendLine("- Return raw JSON only, match the schema exactly, and use only allowed category values.")
            appendLine()
            appendLine("HOW TO USE EACH CONTEXT SECTION")
            appendLine()
            appendLine("SEMANTIC MEMORY (domains, intents, keywords, entities, action items):")
            appendLine("- This section captures what the user has been writing about in memos and tasks — their active projects, recurring topics, key people, and pending actions.")
            appendLine("- Keywords reveal the user's current focus areas (e.g., 'budget', 'migration', 'hiring').")
            appendLine("- Entities are people, places, and things the user frequently mentions — use them to personalize suggestions.")
            appendLine("- Pending action items from memos are things the user wrote down but hasn't turned into tasks yet — suggest turning them into tasks.")
            appendLine("- Domains and intents reveal WHAT the user works on and WHY — match suggestions to these themes.")
            appendLine("- Example: If keywords include 'quarterly report' and 'deadline', suggest 'Finalize Q4 quarterly report draft'.")
            appendLine()
            appendLine("BOOX NOTES CONTEXT (note titles, synopses, keywords):")
            appendLine("- These are the user's handwritten or typed notes from their Boox device — meeting notes, brainstorms, journals.")
            appendLine("- Note synopses reveal what the user discussed or planned recently.")
            appendLine("- If a note mentions decisions, action items, or follow-ups, suggest concrete tasks derived from them.")
            appendLine("- Example: If a note titled 'Product Roadmap' mentions 'finalize Q2 features', suggest 'Review and finalize Q2 feature list from Product Roadmap notes'.")
            appendLine()
            appendLine("CALENDAR CONTEXT (events with description, location, attendees):")
            appendLine("- Events include rich detail: descriptions (what the meeting is about), locations (where to go), and attendees (who's involved).")
            appendLine("- Use descriptions to suggest SPECIFIC prep work (e.g., 'Review Q4 mockups before Design Review at 14:00').")
            appendLine("- Use attendees for follow-up suggestions (e.g., 'Send meeting notes to sarah@co.com from yesterday's Sprint Review').")
            appendLine("- Use location for travel prep (e.g., 'Pack laptop for offsite at Convention Center').")
            appendLine("- Past events (negative days) → suggest follow-ups. Future events → suggest preparation.")
            appendLine()
            appendLine("TASK SCOPE (overdue, today, upcoming, recently completed):")
            appendLine("- Overdue tasks need urgent attention — suggest concrete next steps, not 'catch up'.")
            appendLine("- Recently completed tasks may imply follow-ups (e.g., completed 'Draft proposal' → suggest 'Send proposal to client').")
            appendLine("- Upcoming tasks for later this week may need prep work today.")
            appendLine()
            appendLine("PATTERN INSIGHTS (completion rate, streaks, neglected areas):")
            appendLine("- If completion rate is low, suggest small quick wins to rebuild momentum.")
            appendLine("- If certain categories are neglected, suggest one task in that area.")
            appendLine("- If the user has a streak, acknowledge it and suggest maintaining it.")
            appendLine()
            appendLine("USER PREFERENCES (priorities, interests, lifestyle, energy pattern):")
            appendLine("- Honor the priority ranking — work-first users get work suggestions first.")
            appendLine("- Match interests to suggest enjoyable tasks when appropriate.")
            appendLine("- Respect energy pattern — don't suggest deep work for a night owl at 7am.")
            appendLine()
            appendLine("## PRE-SCORED CONTEXT")
            appendLine("- Total items analyzed: ${scoredContext.aggregateMetrics.totalItems}")
            appendLine("- High priority items: ${scoredContext.aggregateMetrics.scoreDistribution.high}")
            appendLine("- Average score: ${String.format("%.2f", scoredContext.aggregateMetrics.avgFinalScore)}")
            appendLine()
            append(buildSuggestionPreferenceGuidance(preferences, targetCount))
        }.trim()
    }

    /**
     * Build V3 suggestions prompt incorporating pre-scores.
     */
    private fun buildV3SuggestionsPrompt(
        existingContextSection: String,
        preScoreSection: String,
        existingTasks: List<String>,
        preferences: com.boox.day.data.ai.AiPreferences?,
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): String = buildString {
        // Existing context
        appendLine(existingContextSection)
        appendLine()

        // Pre-scored items section
        appendLine(preScoreSection)
        appendLine()

        // Top signals summary
        if (scoredContext.aggregateMetrics.topSignals.isNotEmpty()) {
            appendLine("## KEY SIGNALS FROM PRE-ANALYSIS")
            scoredContext.aggregateMetrics.topSignals.take(5).forEach { signal ->
                appendLine("- ${signal.signal} (${signal.count} items)")
            }
            appendLine()
        }

        // Existing tasks to avoid (SuggestionDuplicates.tasksToAvoid: at most 30)
        if (existingTasks.isNotEmpty()) {
            appendLine("## EXISTING TASKS (avoid duplicates)")
            existingTasks.forEach { task ->
                appendLine("- $task")
            }
            appendLine()
        }

        // Instructions
        appendLine("## INSTRUCTIONS")
        appendLine()
        appendLine("Generate task suggestions that:")
        appendLine("1. Align with the pre-computed priority scores — high-scored items first")
        appendLine("2. Are NOT duplicates or light rewords of existing tasks")
        appendLine("3. Are specific and immediately actionable — not vague advice")
        appendLine("4. Cross-reference CALENDAR CONTEXT with tasks to suggest prep work, follow-ups, and scheduling based on event descriptions, attendees, and locations")
        appendLine("5. Mine SEMANTIC MEMORY for the user's active keywords, entities, and pending action items — turn unfinished action items into suggested tasks")
        appendLine("6. Mine BOOX NOTES CONTEXT for recent note topics, synopses, and keywords — suggest follow-up tasks from meeting notes and brainstorms")
        appendLine("7. Leverage USER CONTEXT preferences: honor focus style, routine preference, and priority rankings")
        // The reason is shown to the user (Oct 2026): it quoted "[0.70]" scores when asked for a "score signal"
        appendLine("8. Keep each reason short and grounded in NAMED evidence from the input (cite the specific keyword, entity, note title or event name). The user reads the reason: never mention scores, numbers in brackets or section names from these instructions")

        val targetCount = resolveSuggestionCount(preferences)
        appendLine("9. Target count: up to $targetCount suggestions")

        // Priority guidance from high-scored items
        val highScoreItems = scoredContext.scoredItems.filter { it.finalScore >= 0.7f }
        if (highScoreItems.isNotEmpty()) {
            appendLine()
            appendLine("**High-priority areas to consider (pre-score ≥ 0.7):**")
            highScoreItems.take(5).forEach { item ->
                appendLine("- ${item.itemText.take(50)} [${String.format("%.2f", item.finalScore)}]")
            }
        }
    }

    /**
     * Sort tasks using pre-computed V3 scores WITH parent-child grouping preserved.
     *
     * Unlike sortTasksByPriorityV3() which sorts all tasks individually, this function:
     * 1. Identifies top-level tasks (parents and standalones)
     * 2. Sorts top-level by score
     * 3. Expands each parent with its children following immediately (in row order)
     *
     * This ensures parent-child groups stay together after sorting.
     *
     * @param tasks Tasks to sort (should be pre-filtered to content tasks only)
     * @param scoredContext Pre-scored context from IntelligenceEngine
     * @return List of tasks sorted by priority with groups preserved
     */
    fun sortTasksByPriorityV3WithGrouping(
        tasks: List<Task>,
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): List<Task> {
        Log.d(TAG, "Sorting ${tasks.size} tasks using V3 with parent-child grouping")

        if (tasks.isEmpty()) return emptyList()

        // Build set of task IDs for efficient lookup
        val taskIds = tasks.map { it.id }.toSet()

        // Separate top-level (parents/standalone) from children
        // A task is "effective top-level" if it has no parent, OR its parent isn't in the task list
        val effectiveTopLevel = tasks.filter {
            it.parentTaskId == null || it.parentTaskId !in taskIds
        }

        // Group children by their parent (only if parent is in the list)
        val childrenByParent = tasks
            .filter { it.parentTaskId != null && it.parentTaskId in taskIds }
            .groupBy { it.parentTaskId!! }

        Log.d(TAG, "Top-level tasks: ${effectiveTopLevel.size}, " +
                "child groups: ${childrenByParent.size}")

        // Build task ID → score map from pre-computed scores
        val taskScores = scoredContext.scoredItems
            .filter { it.itemType == "task" }
            .associate { it.itemId to it.finalScore }

        // Sort top-level tasks by score (descending)
        val sortedTopLevel = effectiveTopLevel.sortedByDescending { task ->
            taskScores[task.id] ?: 0f
        }

        // Expand: each top-level task followed by its children (in row order)
        val result = mutableListOf<Task>()
        for (topLevelTask in sortedTopLevel) {
            result.add(topLevelTask)
            val children = childrenByParent[topLevelTask.id] ?: emptyList()
            // Sort children by row index to maintain visual order within group
            result.addAll(children.sortedBy { it.rowIndex })
        }

        Log.d(TAG, "V3 grouped sort complete. Result: ${result.size} tasks, " +
                "top task: ${result.firstOrNull()?.id}")

        return result
    }

    // ========== AUTO-PRIORITY ASSIGNMENT ==========

    /**
     * Map Intelligence Engine scores to A/B/C priority levels.
     *
     * Thresholds aligned with IntelligenceEngine score distribution:
     * - A: >= 0.70 (high urgency/relevance)
     * - B: 0.45 - 0.70 (moderate)
     * - C: < 0.45 (low urgency)
     */
    fun mapScoreToPriority(score: Float): String {
        return when {
            score >= 0.70f -> "A"
            score >= 0.45f -> "B"
            else -> "C"
        }
    }

    /**
     * Build priority assignments for sorted tasks based on their scores.
     * Used by Smart Prioritization to auto-assign A/B/C priorities after reordering.
     *
     * @param scoredContext The scored context from Intelligence Engine
     * @return Map of taskId to priority (A/B/C)
     */
    fun buildPriorityAssignments(
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): Map<String, String> {
        return scoredContext.scoredItems
            .filter { it.itemType == "task" }
            .associate { it.itemId to mapScoreToPriority(it.finalScore) }
    }

    /**
     * Break down a task using pre-scored context from the Intelligence Engine.
     *
     * This V3 method uses ComplexityScorer signals to inform the LLM about
     * why the task needs breakdown and how to approach the decomposition.
     *
     * @param taskText Text of the task to break down
     * @param task The Task object for ID lookup
     * @param existingTasks Context of other tasks on the same day
     * @param currentDate The date for context
     * @param scoredContext Pre-scored context from IntelligenceEngine
     * @return BreakdownResult with 3-7 subtasks
     */
    suspend fun breakDownTaskV3(
        taskText: String,
        task: Task,
        existingTasks: List<Task>,
        currentDate: LocalDate,
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): BreakdownResult {
        if (taskText.isBlank()) {
            return BreakdownResult(
                originalTask = taskText,
                subtasks = emptyList(),
                keepOriginal = false,
                error = "No task text provided"
            )
        }

        Log.d(TAG, "Breaking down task (V3): $taskText")

        // Get complexity signals from pre-computed scores
        val scoredItem = scoredContext.scoredItems.find { it.itemId == task.id }
        val complexitySignals = scoredItem?.componentScores
            ?.get(com.boox.day.data.ai.ScorerIds.COMPLEXITY)?.signals ?: emptyList()
        val complexityScore = scoredItem?.componentScores
            ?.get(com.boox.day.data.ai.ScorerIds.COMPLEXITY)?.rawScore ?: 0f

        Log.d(TAG, "Complexity score: $complexityScore, signals: $complexitySignals")

        // Build enhanced prompt with complexity analysis
        val prompt = buildBreakdownPromptV3(
            taskText = taskText,
            existingTasks = existingTasks,
            currentDate = currentDate,
            complexitySignals = complexitySignals,
            scoredContext = scoredContext
        )
        val systemPrompt = buildBreakdownSystemPromptV3(complexitySignals, complexityScore)

        // Models to try using 3×3 retry pattern
        val modelsToTry = getReasoningModels()

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                Log.d(TAG, "Breaking down task (V3) with ${model.displayName}...")
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(prompt)
                    jsonSchema("task_breakdown_v3", StructuredOutputSchemas.breakdownSchema)
                    temperature(0.4f) // Lower for consistent structure
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "Breakdown V3 response (${model.displayName}): ${response.data.take(500)}...")
                // An unreadable answer or too few subtasks goes to the next model (29 Oct 2026:
                // it ended the feature, no other model tried)
                parseBreakdownResponse(response.data, taskText).takeIf { it.error == null }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> BreakdownResult(
                originalTask = taskText,
                subtasks = emptyList(),
                keepOriginal = false,
                // What went wrong (no Wi-Fi, no credit, ...); was always "temporarily unavailable" (Oct 2026)
                error = result.message
            )
        }
    }

    private fun buildBreakdownSystemPromptV3(
        complexitySignals: List<String>,
        complexityScore: Float
    ): String {
        val basePrompt = """
You are a task breakdown assistant that helps users split complex tasks into actionable subtasks.

GUIDELINES:
1. Create 3-7 specific, actionable subtasks
2. Each subtask should be completable in a reasonable time
3. Order subtasks logically (prerequisites first)
4. Keep subtask text concise (3-10 words)
5. Be practical - real actions, not abstract steps

OUTPUT FORMAT:
Return JSON with subtasks:
{
  "subtasks": [
    {"text": "First actionable step", "order": 1},
    {"text": "Second actionable step", "order": 2},
    ...
  ],
  "keepOriginal": false
}

Set "keepOriginal" to true ONLY if the original task is a category/project name that should remain as a header.
Return raw JSON only, match the schema exactly, and do not add extra keys.
""".trimIndent()

        // Add complexity-aware guidance if signals exist
        val complexityGuidance = if (complexitySignals.isNotEmpty()) {
            """

## PRE-ANALYZED COMPLEXITY (Score: ${String.format("%.2f", complexityScore)})

The Intelligence Engine has identified the following complexity signals:
${complexitySignals.joinToString("\n") { "- $it" }}

Use these signals to guide your breakdown:
- If "Multiple actions detected" → ensure each action becomes a separate subtask
- If "Broad scope indicated" → break down the scope into manageable chunks
- If "Project-level task" → consider phases or major milestones
- If "Vague terms" → clarify with specific, concrete subtasks
""".trimIndent()
        } else ""

        return basePrompt + complexityGuidance
    }

    private fun buildBreakdownPromptV3(
        taskText: String,
        existingTasks: List<Task>,
        currentDate: LocalDate,
        complexitySignals: List<String>,
        scoredContext: com.boox.day.data.ai.ScoredContext
    ): String {
        val dayOfWeek = currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val normalizedTaskText = normalizeAiTextKey(taskText)

        val basePrompt = """
## Task to Break Down

"$taskText"

**Day**: $dayOfWeek
**Date**: $currentDate
""".trimIndent()

        val existingTaskTexts = existingTasks
            .mapNotNull { task ->
                listOf(task.typedText, task.convertedText, task.recognizedText)
                    .firstOrNull { !it.isNullOrBlank() }
            }
            .map(::collapseWhitespace)
            .filter { it.isNotBlank() && normalizeAiTextKey(it) != normalizedTaskText }
            .distinctBy(::normalizeAiTextKey)
            .take(8)

        val existingSection = if (existingTaskTexts.isNotEmpty()) {
            """

## Already On Today's List (do not duplicate or lightly reword)
${existingTaskTexts.joinToString("\n") { "- $it" }}
""".trimIndent()
        } else ""

        // Add related tasks from scored context for better understanding
        val relatedTasks = scoredContext.scoredItems
            .filter { it.itemType == "task" && it.itemText != taskText }
            .take(5)
            .mapNotNull { scoredItem ->
                val text = collapseWhitespace(scoredItem.itemText)
                if (text.isBlank() || normalizeAiTextKey(text) == normalizedTaskText) {
                    null
                } else {
                    "${text.take(50)} [score: ${String.format("%.2f", scoredItem.finalScore)}]"
                }
            }

        val relatedSection = if (relatedTasks.isNotEmpty()) {
            """

## Related Tasks Today
${relatedTasks.joinToString("\n") { "- $it" }}
""".trimIndent()
        } else ""

        val instructions = """

Break this task into 3-7 specific, actionable subtasks that can each be completed independently.
Order them logically (do prerequisites first).
Do not repeat or lightly reword tasks that already exist today.
If the task is already mostly represented by existing tasks, return the smallest missing steps only.
""".trimIndent()

        return basePrompt + existingSection + relatedSection + instructions
    }

    /**
     * Generate daily summary using pre-scored context from the Intelligence Engine.
     *
     * This V3 method uses pre-computed priority scores to highlight important
     * completed/incomplete tasks and provide more insightful analysis.
     *
     * @param tasksForDay All tasks for the day
     * @param currentDate The date being summarized
     * @param density Screen density for dp-to-px conversion
     * @param scoredContext Pre-scored context from IntelligenceEngine
     * @param onStageChange Callback for progress updates
     * @param context Context for event loading (optional)
     * @return DailySummary with enhanced insights
     */
    suspend fun generateDailySummaryV3(
        tasksForDay: List<Task>,
        currentDate: LocalDate,
        density: Float,
        scoredContext: com.boox.day.data.ai.ScoredContext,
        onStageChange: (String) -> Unit,
        context: Context? = null
    ): DailySummary {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        val dateString = currentDate.format(dateFormatter)

        onStageChange("Gathering your tasks...")

        // ═══════════════════════════════════════════════════════════════════════
        // Get text for all tasks via TaskOcrService (same as V2)
        // ═══════════════════════════════════════════════════════════════════════
        val tasksWithoutUserText = tasksForDay.filter {
            it.typedText.isNullOrBlank() && it.convertedText.isNullOrBlank()
        }
        val ocrResult = TaskOcrService.getTextForTasks(tasksWithoutUserText, density)

        // Build handwritten OCR map for the dialog
        val handwrittenOcrMap = ocrResult.results
            .filter { it.value.isHandwritten && it.value.text.isNotBlank() }
            .mapValues { it.value.text }

        // All tasks with content = typed/converted + recognized handwriting
        val textTasks = tasksForDay.filter {
            it.typedText?.isNotBlank() == true || it.convertedText?.isNotBlank() == true
        }
        val recognizedTasks = tasksWithoutUserText.filter { handwrittenOcrMap.containsKey(it.id) }
        val allTasksWithContent = textTasks + recognizedTasks

        if (allTasksWithContent.isEmpty()) {
            // INTELLIGENT FALLBACK: Generate insights based on events and preferences
            onStageChange("No tasks found - analyzing your schedule...")

            // Synced and tablet events (AiCalendarEvents, Oct 2026: tablet events only before)
            val events = if (context != null) AiCalendarEvents.forDay(context, currentDate) else emptyList()

            // Generate intelligent insights even with no tasks
            val aiInsights = generateNoTasksInsights(
                currentDate = currentDate,
                events = events,
                onStageChange = onStageChange,
                context = context
            )

            return DailySummary(
                date = dateString,
                completedTasks = emptyList(),
                incompleteTasks = emptyList(),
                completionRate = 0f,
                aiInsights = aiInsights,
                handwrittenOcrMap = emptyMap(),
                eventsCount = events.size
            )
        }

        val textCount = textTasks.size
        val handwrittenCount = handwrittenOcrMap.size
        Log.d(TAG, "Daily summary V3: $textCount text + $handwrittenCount handwritten tasks")

        onStageChange("Analyzing ${allTasksWithContent.size} tasks with priority scores...")

        // Separate completed vs incomplete
        val completedTasks = allTasksWithContent.filter { it.isCompleted }
        val incompleteTasks = allTasksWithContent.filter { !it.isCompleted }

        val completionRate = if (allTasksWithContent.isNotEmpty()) {
            completedTasks.size.toFloat() / allTasksWithContent.size
        } else 0f

        Log.d(TAG, "Generating daily summary V3: ${completedTasks.size} completed, ${incompleteTasks.size} incomplete")

        onStageChange("Generating insights with priority analysis...")

        // Generate AI insights with scored context
        val insights = generateInsightsV3(
            completedTasks = completedTasks,
            incompleteTasks = incompleteTasks,
            handwrittenOcrMap = handwrittenOcrMap,
            scoredContext = scoredContext,
            currentDate = currentDate,
            context = context
        )

        return DailySummary(
            date = dateString,
            completedTasks = completedTasks,
            incompleteTasks = incompleteTasks,
            completionRate = completionRate,
            aiInsights = insights,
            handwrittenOcrMap = handwrittenOcrMap
        )
    }

    /**
     * Generate AI insights using pre-computed scores from Intelligence Engine.
     */
    private suspend fun generateInsightsV3(
        completedTasks: List<Task>,
        incompleteTasks: List<Task>,
        handwrittenOcrMap: Map<String, String>,
        scoredContext: com.boox.day.data.ai.ScoredContext,
        currentDate: LocalDate,
        context: Context?
    ): List<DailySummaryInsight> {
        val totalCount = completedTasks.size + incompleteTasks.size
        if (totalCount == 0) return emptyList()

        // Helper to get task text
        fun getTaskText(task: Task): String {
            return handwrittenOcrMap[task.id] ?: task.typedText ?: "[task]"
        }

        // Helper to get task score
        fun getTaskScore(task: Task): Float {
            return scoredContext.scoredItems.find { it.itemId == task.id }?.finalScore ?: 0f
        }

        // Build completed list with priority scores
        val completedList = completedTasks
            .sortedByDescending { getTaskScore(it) }
            .joinToString("\n") { task ->
                val score = getTaskScore(task)
                "- [${String.format("%.2f", score)}] ${getTaskText(task)}" +
                    if (task.isCompleted) " ✓" else ""
            }

        // Build incomplete list with priority scores and signals
        val incompleteList = incompleteTasks
            .sortedByDescending { getTaskScore(it) }
            .joinToString("\n") { task ->
                val scoredItem = scoredContext.scoredItems.find { it.itemId == task.id }
                val score = scoredItem?.finalScore ?: 0f
                val signal = scoredItem?.primarySignal?.take(40) ?: ""
                "- [${String.format("%.2f", score)}] ${getTaskText(task)}" +
                    if (signal.isNotEmpty()) " ($signal)" else ""
            }

        // Get high-priority incomplete tasks
        val highPriorityIncomplete = incompleteTasks
            .filter { getTaskScore(it) >= 0.7f }
            .size

        // Load today's events for schedule context: synced and tablet events (AiCalendarEvents, Oct 2026)
        val todayEvents = if (context != null) AiCalendarEvents.forDay(context, currentDate) else emptyList()

        val eventsSection = if (todayEvents.isNotEmpty()) {
            buildString {
                appendLine("### Today's Schedule (${todayEvents.size} events)")
                todayEvents.take(6).forEach { event ->
                    val timeLabel = if (event.isAllDay) "All day" else {
                        val zone = java.time.ZoneId.systemDefault()
                        val start = java.time.Instant.ofEpochMilli(event.startTime).atZone(zone)
                        String.format("%02d:%02d", start.hour, start.minute)
                    }
                    append("- [$timeLabel] ${event.title}")
                    event.location?.takeIf { it.isNotBlank() }?.let { append(" @ ${it.take(30)}") }
                    appendLine()
                }
            }.trimEnd()
        } else null

        // Build V3 prompt with scored context
        val prompt = buildDailySummaryInsightsPromptV3(
            completedList = completedList,
            incompleteList = incompleteList,
            totalCompleted = completedTasks.size,
            totalIncomplete = incompleteTasks.size,
            highPriorityIncomplete = highPriorityIncomplete,
            scoredContext = scoredContext,
            currentDate = currentDate,
            eventsSection = eventsSection
        )

        val modelsToTry = getReasoningModels()

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(buildDailySummarySystemPromptV3())
                    userMessage(prompt)
                    jsonSchema("daily_summary_insights_v3", StructuredOutputSchemas.dailyInsightsSchema)
                    temperature(0.5f)
                }
            },
            processResponse = { response, _ ->
                val parsed = parseInsightsResponse(response.data)
                parsed.takeIf { it.isNotEmpty() }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> listOf(
                DailySummaryInsight(
                    type = "productivity",
                    text = "Completed ${completedTasks.size} of ${totalCount} tasks today " +
                        "(${(completedTasks.size * 100 / totalCount)}% completion rate)"
                )
            )
        }
    }

    private fun buildDailySummarySystemPromptV3(): String = """
You are a productivity coach providing daily task review insights.

CONTEXT: Tasks have been pre-scored by an Intelligence Engine with 7 factors:
- Urgency (deadlines, overdue status)
- Preference (user's category priorities)
- Calendar (event relationships)
- Pattern (historical completion patterns)
- Semantic (contextual relevance)
- Capacity (time/workload fit)
- Complexity (task difficulty)

Scores range 0.0-1.0. High scores (>= 0.7) indicate high priority.
Return raw JSON only, match the schema exactly, and do not add extra keys or prose outside JSON.

OUTPUT FORMAT (JSON only, no markdown):
{
  "insights": [
    {"type": "productivity", "text": "Brief insight about completion"},
    {"type": "pattern", "text": "Brief insight about patterns noticed"},
    {"type": "suggestion", "text": "Brief actionable suggestion"}
  ]
}

GUIDELINES:
- Provide 2-3 insights
- Anchor insights to specific tasks or signals from the input (avoid generic platitudes)
- If high-priority incomplete tasks exist, call them out by name at least once
- Include 1 actionable next step that references a specific task
- If you mention counts, percentages, or completion rates, copy the exact values from the input instead of recalculating them
- Do not relabel tasks with broader labels like "errands" or "admin" unless those labels appear in the input
- Do not invent tasks or events that are not in the input
- If a schedule section is provided, factor meeting load into your analysis (e.g., "4 meetings today left limited time for deep work — completing 3 tasks despite the heavy schedule shows strong prioritization")
- Keep insights concise (1-2 sentences each)
- Be encouraging but honest
""".trimIndent()

    private fun buildDailySummaryInsightsPromptV3(
        completedList: String,
        incompleteList: String,
        totalCompleted: Int,
        totalIncomplete: Int,
        highPriorityIncomplete: Int,
        scoredContext: com.boox.day.data.ai.ScoredContext,
        currentDate: LocalDate,
        eventsSection: String? = null
    ): String {
        val dayOfWeek = currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val totalCount = totalCompleted + totalIncomplete
        val completionRate = if (totalCount > 0) (totalCompleted.toFloat() / totalCount * 100).toInt() else 0
        val scoreDistribution = scoredContext.aggregateMetrics.scoreDistribution
        val averageScore = String.format("%.2f", scoredContext.aggregateMetrics.avgFinalScore)
        val topSignals = scoredContext.aggregateMetrics.topSignals
            .take(5)
            .joinToString("\n") { "- ${it.signal} (${it.count}x)" }
            .ifBlank { "- (none)" }
        val topFactors = scoredContext.aggregateMetrics.componentContributions
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString("\n") { "- ${it.key}: ${String.format("%.2f", it.value)}" }
            .ifBlank { "- (none)" }
        val contextSummary = scoredContext.contextSummary.ifBlank { "(none)" }

        return """
## Daily Summary - $dayOfWeek

**Completion**: $totalCompleted completed, $totalIncomplete incomplete
**Completion Rate**: $completionRate%
**High Priority Incomplete**: $highPriorityIncomplete tasks with score >= 0.7
**Score Distribution**: high ${scoreDistribution.high}, medium ${scoreDistribution.medium}, low ${scoreDistribution.low}
**Average Score**: $averageScore

### Top Scoring Factors
$topFactors

### Completed Tasks (sorted by priority)
$completedList

### Incomplete Tasks (sorted by priority)
$incompleteList

### Key Signals from Intelligence Engine
$topSignals

### Context Summary
$contextSummary
${eventsSection?.let { "\n$it\n" } ?: ""}
Provide 2-3 insights about this day's task completion, patterns, and suggestions.
${if (eventsSection != null) "Consider how today's schedule may have affected task completion (e.g., heavy meetings = less task time)." else ""}
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEEKLY SUMMARY V3 - Using Intelligence Engine
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate weekly summary using pre-computed Intelligence Engine scores.
     *
     * This V3 version leverages PatternScorer for trend analysis and
     * uses pre-scored context for enhanced insights generation.
     */
    suspend fun generateWeeklySummaryV3(
        weeklyTasks: Map<String, List<Task>>,
        startDate: LocalDate,
        endDate: LocalDate,
        scoredContext: ScoredContext,
        onStageChange: (String) -> Unit,
        context: Context? = null
    ): WeeklySummary {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val weekRange = "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}, ${endDate.year}"

        // Get text for all tasks via TaskOcrService
        val density = context?.resources?.displayMetrics?.density ?: 2.75f

        onStageChange("Reading handwritten tasks...")

        val allTasks = weeklyTasks.values.flatten()
        val ocrResult = TaskOcrService.getTextForTasks(allTasks, density)
        val textMap = ocrResult.results.mapValues { it.value.text }

        onStageChange("Gathering 7 days of tasks...")

        // Calculate daily stats
        val dailyStats = mutableListOf<DayStats>()
        var totalCompleted = 0
        var totalTasks = 0

        for (i in 0..6) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString()
            val tasksForDay = weeklyTasks[dateStr] ?: emptyList()
            val completed = tasksForDay.count { it.isCompleted }
            val total = tasksForDay.size

            totalCompleted += completed
            totalTasks += total

            dailyStats.add(DayStats(
                date = dateStr,
                dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                completed = completed,
                total = total,
                completionRate = if (total > 0) completed.toFloat() / total else 0f
            ))
        }

        if (totalTasks == 0) {
            // Fallback for no tasks
            onStageChange("No tasks found - analyzing your week...")
            // Synced and tablet events, day by day (AiCalendarEvents, Oct 2026: tablet events only before)
            val weeklyEvents = if (context != null) {
                (0..6).flatMap { AiCalendarEvents.forDay(context, startDate.plusDays(it.toLong())) }
            } else emptyList()

            val aiInsights = generateNoTasksWeeklyInsights(
                startDate = startDate,
                endDate = endDate,
                weeklyEvents = weeklyEvents,
                onStageChange = onStageChange,
                context = context
            )

            return WeeklySummary(
                weekRange = weekRange,
                totalCompleted = 0,
                totalTasks = 0,
                completionRate = 0f,
                dailyStats = dailyStats,
                bestDay = null,
                worstDay = null,
                recurringIncompleteTasks = emptyList(),
                categoryBreakdown = emptyList(),
                aiInsights = aiInsights,
                weeklyEventsCount = weeklyEvents.size
            )
        }

        onStageChange("Analyzing ${dailyStats.size} days...")

        // Find best and worst days
        val bestDay = dailyStats.filter { it.total > 0 }.maxByOrNull { it.completionRate }
        val worstDay = dailyStats.filter { it.total > 0 }.minByOrNull { it.completionRate }

        onStageChange("Detecting recurring incomplete tasks...")

        // Find recurring incomplete tasks
        val incompleteTaskTexts = mutableMapOf<String, MutableSet<String>>()
        val displayTextByNormalized = mutableMapOf<String, String>()
        for ((dateStr, tasks) in weeklyTasks) {
            tasks.filter { !it.isCompleted }.forEach { task ->
                val rawText = resolveTaskText(task, textMap) ?: return@forEach
                val normalized = normalizeRecurringTaskText(rawText)
                if (normalized.isBlank()) return@forEach
                displayTextByNormalized.putIfAbsent(normalized, rawText.trim())
                incompleteTaskTexts.getOrPut(normalized) { mutableSetOf() }.add(dateStr)
            }
        }

        val recurringIncompleteTasks = incompleteTaskTexts
            .filter { it.value.size >= 2 }
            .map { (normalized, dates) ->
                val displayText = displayTextByNormalized[normalized]?.ifBlank { normalized } ?: normalized
                RecurringTask(
                    text = displayText.replaceFirstChar { it.uppercase() },
                    occurrences = dates.size,
                    dates = dates.sorted()
                )
            }
            .sortedByDescending { it.occurrences }
            .take(5)

        Log.d(TAG, "Weekly summary V3: $totalCompleted/$totalTasks completed, ${recurringIncompleteTasks.size} recurring incomplete")

        onStageChange("Generating insights with Intelligence Engine...")

        // Generate V3 insights with pre-computed scores
        val insightsResult = generateWeeklyInsightsV3(
            dailyStats = dailyStats,
            weeklyTasks = weeklyTasks,
            textMap = textMap,
            totalCompleted = totalCompleted,
            totalTasks = totalTasks,
            bestDay = bestDay,
            worstDay = worstDay,
            recurringIncompleteTasks = recurringIncompleteTasks,
            startDate = startDate,
            endDate = endDate,
            scoredContext = scoredContext,
            context = context
        )

        return WeeklySummary(
            weekRange = weekRange,
            totalCompleted = totalCompleted,
            totalTasks = totalTasks,
            completionRate = if (totalTasks > 0) totalCompleted.toFloat() / totalTasks else 0f,
            dailyStats = dailyStats,
            bestDay = bestDay,
            worstDay = worstDay,
            recurringIncompleteTasks = recurringIncompleteTasks,
            categoryBreakdown = insightsResult.categories,
            aiInsights = insightsResult.insights,
            warning = insightsResult.warning
        )
    }

    /**
     * Generate weekly insights using Intelligence Engine scores.
     */
    private suspend fun generateWeeklyInsightsV3(
        dailyStats: List<DayStats>,
        weeklyTasks: Map<String, List<Task>>,
        textMap: Map<String, String?>,
        totalCompleted: Int,
        totalTasks: Int,
        bestDay: DayStats?,
        worstDay: DayStats?,
        recurringIncompleteTasks: List<RecurringTask>,
        startDate: LocalDate,
        endDate: LocalDate,
        scoredContext: ScoredContext,
        context: Context? = null
    ): WeeklyInsightsResult {
        // Build context block
        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = context,
            date = endDate,
            rangeStart = startDate,
            rangeEnd = endDate,
            includeBooxNotes = true,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO)
        )
        val localCategoryBreakdown = computeLocalWeeklyCategoryBreakdown(weeklyTasks, textMap)

        // Load weekly event counts per day for schedule context: synced and tablet events (Oct 2026)
        val weeklyEventCounts = if (context != null) {
            dailyStats.associate { day ->
                day.date to AiCalendarEvents.forDay(context, LocalDate.parse(day.date)).size
            }
        } else emptyMap()

        val systemPrompt = buildWeeklySummarySystemPromptV3()
        val userPrompt = buildWeeklySummaryPromptV3(
            dailyStats = dailyStats,
            weeklyTasks = weeklyTasks,
            textMap = textMap,
            totalCompleted = totalCompleted,
            totalTasks = totalTasks,
            bestDay = bestDay,
            worstDay = worstDay,
            recurringIncompleteTasks = recurringIncompleteTasks,
            startDate = startDate,
            endDate = endDate,
            scoredContext = scoredContext,
            contextBlock = contextBlock,
            weeklyEventCounts = weeklyEventCounts
        )

        val models = getReasoningModels()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(userPrompt)
                    jsonSchema("weekly_summary_insights_v3", StructuredOutputSchemas.weeklyInsightsSchema)
                    temperature(0.3f)
                }
            },
            processResponse = { response, _ ->
                parseWeeklyInsightsResponse(response.data, totalTasks)
                    .takeIf { it.insights.isNotEmpty() }
                    ?.copy(categories = localCategoryBreakdown, warning = null)
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> {
                Log.e(TAG, "Weekly insights V3 generation failed: ${result.message}")
                WeeklyInsightsResult(
                    insights = listOf(WeeklyInsight("overview", "Week Complete: You completed $totalCompleted of $totalTasks tasks.")),
                    categories = localCategoryBreakdown
                )
            }
        }
    }

    /**
     * System prompt for V3 weekly insights.
     */
    private fun buildWeeklySummarySystemPromptV3(): String = """
You are an intelligent weekly review assistant powered by a pre-computed Intelligence Engine.
Generate insightful weekly summaries based on task completion patterns and pre-analyzed data.

The Intelligence Engine has already analyzed all tasks with:
- Pattern signals (completion streaks, recurring behaviors)
- Urgency and deadline tracking
- Task complexity assessment
- Calendar alignment scores

The daily breakdown may include event counts per day. Use this to correlate meeting load with task completion.
Use these pre-computed insights to provide deeper, more actionable weekly insights.
Return raw JSON only, match the schema exactly, omit optional fields instead of null, and do not add extra keys.
The app computes weekly category totals locally, so focus on insight quality instead of estimating category counts.

OUTPUT FORMAT (JSON only, no markdown):
{
  "insights": [
    {"type": "overview", "title": "Short title", "text": "Detailed insight text"},
    {"type": "pattern", "title": "Short title", "text": "Pattern-based observation"},
    {"type": "suggestion", "title": "Short title", "text": "Actionable improvement suggestion"}
  ]
}

Types: overview (general summary), streak (completion patterns), pattern (behavioral observation), suggestion (actionable tip), achievement (positive recognition)
- If you mention counts or rates in the insight text, copy the exact values from the input.

CATEGORIES:
- WORK: Job tasks, meetings, projects, professional development
- PERSONAL: Home tasks, family, hobbies, entertainment
- HEALTH: Exercise, medical, wellness, self-care
- ERRANDS: Shopping, appointments, bills, chores
- SOCIAL: Friends, events, gatherings
- LEARNING: Study, courses, reading, skills
- FINANCE: Budget, investments, banking
- OTHER: Anything else
    """.trimIndent()

    /**
     * User prompt for V3 weekly insights with scored context.
     */
    private fun buildWeeklySummaryPromptV3(
        dailyStats: List<DayStats>,
        weeklyTasks: Map<String, List<Task>>,
        textMap: Map<String, String?>,
        totalCompleted: Int,
        totalTasks: Int,
        bestDay: DayStats?,
        worstDay: DayStats?,
        recurringIncompleteTasks: List<RecurringTask>,
        startDate: LocalDate,
        endDate: LocalDate,
        scoredContext: ScoredContext,
        contextBlock: String,
        weeklyEventCounts: Map<String, Int> = emptyMap()
    ): String {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val weekRange = "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
        val completionRate = if (totalTasks > 0) (totalCompleted.toFloat() / totalTasks * 100).toInt() else 0

        val scoreDistribution = scoredContext.aggregateMetrics.scoreDistribution
        val averageScore = String.format("%.2f", scoredContext.aggregateMetrics.avgFinalScore)
        val topSignals = scoredContext.aggregateMetrics.topSignals
            .take(6)
            .joinToString("\n") { "- ${it.signal} (${it.count}x)" }
            .ifBlank { "- (none)" }
        val topFactors = scoredContext.aggregateMetrics.componentContributions
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .joinToString("\n") { "- ${it.key}: ${String.format("%.2f", it.value)}" }
            .ifBlank { "- (none)" }
        val contextSummary = scoredContext.contextSummary.ifBlank { "(none)" }

        // Build task summary by day (include event counts if available)
        val taskSummaryByDay = dailyStats.joinToString("\n") { day ->
            val eventCount = weeklyEventCounts[day.date]
            val eventSuffix = if (eventCount != null && eventCount > 0) " | $eventCount events" else ""
            "${day.dayOfWeek}: ${day.completed}/${day.total} (${(day.completionRate * 100).toInt()}%)$eventSuffix"
        }
        val totalEvents = weeklyEventCounts.values.sum()

        val allTasks = weeklyTasks.values.flatten()
        val allTaskTexts = allTasks.mapNotNull { task ->
            resolveTaskText(task, textMap)
        }
        val missingTextCount = allTasks.count { task ->
            resolveTaskText(task, textMap).isNullOrBlank()
        }
        val maxTaskLines = 50
        val taskLines = allTaskTexts.take(maxTaskLines)
        val omittedCount = allTaskTexts.size - taskLines.size
        val tasksText = if (taskLines.isNotEmpty()) {
            buildString {
                taskLines.forEach { appendLine("- $it") }
                if (omittedCount > 0) {
                    appendLine("- ... and $omittedCount more")
                }
            }.trimEnd()
        } else {
            "(none)"
        }
        val missingTextLine = if (missingTextCount > 0) {
            "Tasks without text: $missingTextCount"
        } else ""

        // Get sample incomplete tasks with scores
        val incompleteWithScores = allTasks
            .filter { !it.isCompleted }
            .mapNotNull { task ->
                val scoredItem = scoredContext.scoredItems.find { it.itemId == task.id }
                val text = resolveTaskText(task, textMap) ?: return@mapNotNull null
                Triple(text, scoredItem?.finalScore ?: 0f, scoredItem?.primarySignal ?: "")
            }
            .sortedByDescending { it.second }
            .take(5)

        return """
## Weekly Review: $weekRange

### Statistics
- Total: $totalCompleted / $totalTasks tasks completed ($completionRate%)
- Best day: ${bestDay?.let { "${it.dayOfWeek} (${(it.completionRate * 100).toInt()}%)" } ?: "N/A"}
- Worst day: ${worstDay?.let { "${it.dayOfWeek} (${(it.completionRate * 100).toInt()}%)" } ?: "N/A"}
${if (totalEvents > 0) "- Total calendar events this week: $totalEvents" else ""}

### Intelligence Engine Summary
$contextSummary

### Score Distribution
- High: ${scoreDistribution.high}
- Medium: ${scoreDistribution.medium}
- Low: ${scoreDistribution.low}
- Avg score: $averageScore

### Top Scoring Factors
$topFactors

### Daily Breakdown
$taskSummaryByDay

### Recurring Incomplete Tasks (appeared 2+ times)
${recurringIncompleteTasks.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- ${it.text} (${it.occurrences}x)" } ?: "None detected"}

### Pre-Computed Intelligence Signals
$topSignals

### Top Incomplete Tasks by Score
${incompleteWithScores.takeIf { it.isNotEmpty() }?.joinToString("\n") { (text, score, signal) ->
    "- [${String.format("%.2f", score)}] $text${if (signal.isNotBlank()) " -> $signal" else ""}"
} ?: "None"}

### All Task Texts This Week
$tasksText
${if (missingTextLine.isNotBlank()) "\n$missingTextLine" else ""}

${if (contextBlock.isNotBlank()) "### Additional Context\n$contextBlock\n" else ""}

Generate 3-4 insightful observations and actionable suggestions for next week.
Focus on patterns, achievements, and areas for improvement.
${if (totalEvents > 0) "Consider how meeting load correlated with task completion — days with many events often have lower completion rates." else ""}
        """.trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    private fun collapseWhitespace(text: String?): String {
        return text?.replace(multiWhitespaceRegex, " ")?.trim().orEmpty()
    }

    private fun normalizeAiTextKey(text: String): String {
        return collapseWhitespace(text).lowercase(Locale.ROOT)
    }

    private fun normalizeSuggestionCategory(category: String?): String {
        val normalized = collapseWhitespace(category).uppercase(Locale.ROOT)
        return suggestionCategoriesByName[normalized]?.name ?: SuggestionCategory.OTHER.name
    }

    private fun normalizeDailyInsightType(type: String?): String {
        val normalized = collapseWhitespace(type).lowercase(Locale.ROOT)
        return normalized.takeIf { it in allowedDailyInsightTypes } ?: "suggestion"
    }

    private fun normalizeWeeklyInsightType(type: String?): String {
        val normalized = collapseWhitespace(type).lowercase(Locale.ROOT)
        return normalized.takeIf { it in allowedWeeklyInsightTypes } ?: "overview"
    }

    private fun sanitizeSuggestions(
        suggestions: List<TaskSuggestion>,
        maxCount: Int?
    ): List<TaskSuggestion> {
        val seen = mutableSetOf<String>()
        val sanitized = suggestions.mapNotNull { suggestion ->
            val text = collapseWhitespace(suggestion.text)
            if (text.isBlank()) return@mapNotNull null

            val key = normalizeAiTextKey(text)
            if (!seen.add(key)) return@mapNotNull null

            TaskSuggestion(
                text = text,
                reason = collapseWhitespace(suggestion.reason).ifBlank {
                    "Suggested from your current context."
                },
                confidence = suggestion.confidence.coerceIn(0f, 1f),
                category = normalizeSuggestionCategory(suggestion.category),
                isFollowUp = suggestion.isFollowUp
            )
        }
        return if (maxCount != null) sanitized.take(maxCount) else sanitized
    }

    private fun sanitizePatternInsights(patternInsights: PatternInsightsJson?): PatternInsights? {
        if (patternInsights == null) return null

        val busiestDay = collapseWhitespace(patternInsights.busiestDay)
            .takeIf { it in allowedWeekdayNames }
        val commonCategories = patternInsights.commonCategories
            .map(::normalizeSuggestionCategory)
            .distinct()
            .take(4)
        val workLifeBalance = collapseWhitespace(patternInsights.workLifeBalance)
            .lowercase(Locale.ROOT)
            .takeIf { it in allowedBalanceValues }

        val sanitized = PatternInsights(
            completionRate = patternInsights.completionRate.coerceIn(0f, 1f),
            busiestDay = busiestDay,
            commonCategories = commonCategories,
            workLifeBalance = workLifeBalance,
            streakDays = patternInsights.streakDays.coerceAtLeast(0)
        )

        val hasSignal = sanitized.completionRate > 0f ||
            sanitized.busiestDay != null ||
            sanitized.commonCategories.isNotEmpty() ||
            sanitized.workLifeBalance != null ||
            sanitized.streakDays > 0

        return sanitized.takeIf { hasSignal }
    }

    private fun sanitizeWeeklyInsights(insights: List<WeeklyInsightJson>?): List<WeeklyInsight> {
        val seen = mutableSetOf<String>()
        return insights.orEmpty()
            .mapNotNull { insight ->
                val title = collapseWhitespace(insight.title)
                val body = collapseWhitespace(insight.text)
                val text = when {
                    title.isNotBlank() && body.isNotBlank() -> "$title: $body"
                    body.isNotBlank() -> body
                    else -> ""
                }
                if (text.isBlank()) return@mapNotNull null

                val key = normalizeAiTextKey(text)
                if (!seen.add(key)) return@mapNotNull null

                WeeklyInsight(
                    type = normalizeWeeklyInsightType(insight.type),
                    text = text
                )
            }
            .take(4)
    }

}

