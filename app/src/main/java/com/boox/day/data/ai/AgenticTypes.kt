package com.boox.day.data.ai

import com.boox.day.ai.TaskEventMatcher
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Task
import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.LocalTime

/**
 * Data models for the Agentic Planning System ("The Bridge").
 *
 * This system transforms BooxDay from a passive data viewer into an
 * intelligent "Chief of Staff" that proposes actionable optimizations
 * for the user's day.
 */

// ═══════════════════════════════════════════════════════════════════════════
// PROPOSAL ACTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Types of actions the Agentic Planning System can propose.
 */
enum class ProposalAction(val displayName: String, val emoji: String) {
    /** Reschedule a task to a different day */
    MOVE_TASK("Move Task", ">>>"),

    /** Create a calendar event for focused work time */
    CREATE_TIMEBLOCK("Block Time", "[+]"),

    /** Add a new task to the user's list */
    ADD_TASK("Add Task", "[+]"),

    /** Suggest breaking a complex task into subtasks */
    BREAK_DOWN_TASK("Break Down", "[/]"),

    /** Highlight a task that has been deferred 3+ times */
    FLAG_ZOMBIE("Zombie Alert", "[!]"),

    /** Add buffer time between events */
    SCHEDULE_BUFFER("Add Buffer", "~~~"),

    /** Suggest task may not be user's responsibility */
    DELEGATE_OR_DROP("Delegate/Drop", "[?]"),

    /** Group similar small tasks for efficiency */
    BATCH_SIMILAR("Batch Tasks", "[=]")
}

/**
 * Priority level for proposals.
 */
enum class ProposalPriority(val displayName: String) {
    HIGH("High Priority"),
    MEDIUM("Medium"),
    LOW("Low Priority")
}

/**
 * View mode for the Optimize My Day dialog.
 */
enum class OptimizeViewMode {
    /** Card-based list of proposals */
    LIST,
    /** Chronological timeline with time slots */
    TIMELINE
}

// ═══════════════════════════════════════════════════════════════════════════
// CORE PROPOSAL MODEL
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A single actionable proposal from the Agentic Planning System.
 * Each proposal represents one optimization the user can apply.
 */
data class AgenticProposal(
    /** Unique identifier for this proposal */
    val id: String,

    /** The type of action being proposed */
    @SerializedName("action") val action: ProposalAction,

    /** Short title describing the action */
    @SerializedName("title") val title: String,

    /** Detailed description of what will happen */
    @SerializedName("description") val description: String,

    /** AI's reasoning for this suggestion */
    @SerializedName("reason") val reason: String,

    /** Confidence level (0.0 - 1.0) */
    @SerializedName("confidence") val confidence: Float,

    /** Priority order (1 = highest) */
    @SerializedName("priority") val priority: Int,

    // ========== Action-specific data ==========

    /** Task ID for task-related actions */
    @SerializedName("taskId") val taskId: String? = null,

    /** Task text content for display */
    @SerializedName("taskText") val taskText: String? = null,

    /** Target date for MOVE_TASK (yyyy-MM-dd) */
    @SerializedName("targetDate") val targetDate: String? = null,

    /** Time block details for CREATE_TIMEBLOCK */
    @SerializedName("suggestedTimeBlock") val suggestedTimeBlock: TimeBlock? = null,

    /** Subtask suggestions for BREAK_DOWN_TASK */
    @SerializedName("suggestedSubtasks") val suggestedSubtasks: List<String>? = null,

    /** Zombie info for FLAG_ZOMBIE */
    @SerializedName("zombieInfo") val zombieInfo: ZombieInfo? = null,

    /** Alternative actions (for multi-option proposals like zombies) */
    @SerializedName("alternativeActions") val alternativeActions: List<AlternativeAction>? = null,

    /** Estimated duration in minutes (AI-inferred) */
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int? = null
) {
    /** Get priority enum based on integer value */
    fun getPriorityLevel(): ProposalPriority = when {
        priority <= 2 -> ProposalPriority.HIGH
        priority <= 4 -> ProposalPriority.MEDIUM
        else -> ProposalPriority.LOW
    }
}

/**
 * Alternative action for multi-option proposals (e.g., zombie tasks).
 */
data class AlternativeAction(
    @SerializedName("label") val label: String,
    @SerializedName("action") val action: ProposalAction,
    @SerializedName("targetDate") val targetDate: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// TIME BLOCK
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A suggested time block for focused work.
 * Used by CREATE_TIMEBLOCK and SCHEDULE_BUFFER actions.
 */
data class TimeBlock(
    /** Start time (HH:mm format) */
    @SerializedName("startTime") val startTime: String,

    /** End time (HH:mm format) */
    @SerializedName("endTime") val endTime: String,

    /** Suggested title for the calendar event */
    @SerializedName("title") val title: String,

    /** Duration in minutes */
    @SerializedName("durationMinutes") val durationMinutes: Int,

    /** Whether this is for deep work vs shallow tasks */
    @SerializedName("isDeepWork") val isDeepWork: Boolean = true
) {
    /** Parse start time as LocalTime */
    fun getStartTimeLocal(): LocalTime? = try {
        LocalTime.parse(startTime)
    } catch (e: Exception) { null }

    /** Parse end time as LocalTime */
    fun getEndTimeLocal(): LocalTime? = try {
        LocalTime.parse(endTime)
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════════════════════
// ZOMBIE TASK INFO
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Information about a "zombie" task that keeps getting deferred.
 */
data class ZombieInfo(
    /** Number of times this task has been moved */
    @SerializedName("migrationCount") val migrationCount: Int,

    /** Original creation date (yyyy-MM-dd) */
    @SerializedName("originalDate") val originalDate: String?,

    /** Days since task was created */
    @SerializedName("daysSinceCreation") val daysSinceCreation: Int,

    /** AI-suggested action for this zombie */
    @SerializedName("suggestion") val suggestion: ZombieSuggestion,

    /** Reasoning for the suggestion */
    @SerializedName("suggestionReason") val suggestionReason: String
)

/**
 * Suggested actions for zombie tasks.
 */
enum class ZombieSuggestion(val displayName: String) {
    /** Task seems too big - break it down */
    BREAK_DOWN("Break it down into smaller steps"),

    /** Schedule specific time to tackle it today */
    SCHEDULE_TODAY("Block time to do it today"),

    /** Move to a specific future date with commitment */
    COMMIT_DATE("Commit to a specific date"),

    /** Consider delegating to someone else */
    DELEGATE("Consider delegating"),

    /** The task may no longer be relevant */
    DROP("Consider dropping it")
}

// ═══════════════════════════════════════════════════════════════════════════
// CAPACITY ANALYSIS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Analysis of a day's capacity (available time vs committed time).
 */
data class CapacityAnalysis(
    /** The date being analyzed */
    val date: LocalDate,

    /** Total available minutes (work hours) */
    val totalMinutes: Int,

    /** Minutes committed to calendar events */
    val committedMinutes: Int,

    /** Minutes free (unscheduled) */
    val freeMinutes: Int,

    /** Available time slots (min 30 minutes each) */
    val freeSlots: List<FreeSlot>,

    /** Capacity percentage (committed/total, can exceed 100%) */
    val capacityPercentage: Float,

    /** Whether overbooked (>100% capacity) */
    val isOverbooked: Boolean,

    /** Estimated minutes needed for pending tasks */
    val taskLoadEstimate: Int
) {
    companion object {
        /** Create empty analysis for a day with no data */
        fun empty(date: LocalDate) = CapacityAnalysis(
            date = date,
            totalMinutes = 960,  // 16 hours (6am-10pm)
            committedMinutes = 0,
            freeMinutes = 960,
            freeSlots = emptyList(),
            capacityPercentage = 0f,
            isOverbooked = false,
            taskLoadEstimate = 0
        )
    }

    /** Get capacity status label */
    fun getCapacityStatus(): String = when {
        // Oct 2026: over the whole remaining day (6:00-22:00, from now for today), not an 8-hour
        // "work day"; half that day in events is busy
        capacityPercentage >= 100 -> "Fully booked"
        capacityPercentage >= 50 -> "Busy"
        capacityPercentage >= 25 -> "Moderate"
        else -> "Light"
    }

    /** Check if there's enough free time for a task duration */
    fun hasTimeFor(durationMinutes: Int): Boolean =
        freeSlots.any { it.durationMinutes >= durationMinutes }
}

/**
 * A free time slot in the day.
 */
data class FreeSlot(
    /** Start time (HH:mm) */
    val startTime: String,

    /** End time (HH:mm) */
    val endTime: String,

    /** Duration in minutes */
    val durationMinutes: Int,

    /** Label for the slot (e.g., "Morning", "Early Afternoon") */
    val label: String
) {
    /** Parse start time as LocalTime */
    fun getStartTimeLocal(): LocalTime? = try {
        LocalTime.parse(startTime)
    } catch (e: Exception) { null }

    /** Parse end time as LocalTime */
    fun getEndTimeLocal(): LocalTime? = try {
        LocalTime.parse(endTime)
    } catch (e: Exception) { null }

    companion object {
        /** Get label for a time slot based on start hour */
        fun getLabelForHour(hour: Int): String = when {
            hour < 9 -> "Early Morning"
            hour < 12 -> "Morning"
            hour < 14 -> "Early Afternoon"
            hour < 17 -> "Afternoon"
            hour < 19 -> "Late Afternoon"
            else -> "Evening"
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PREFERENCE CONSTRAINTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Interpreted constraints from user's AiPreferences.
 * Transforms raw preferences into actionable rules for planning.
 */
data class PreferenceConstraints(
    /** Priority ranking (highest to lowest): ["work", "family", "health", ...] */
    val priorityRanking: List<String>,

    /** Time slots blocked due to lifestyle factors */
    val blockedSlots: List<BlockedSlot>,

    /** Optimal work hours start (0-23) */
    val workHoursStart: Int,

    /** Optimal work hours end (0-23) */
    val workHoursEnd: Int,

    /** Whether in a special mode that affects suggestions */
    val activeMode: SpecialMode?,

    /** Suggestion aggressiveness level */
    val aggressiveness: SuggestionAggressiveness,

    /** Include health reminders */
    val includeHealthReminders: Boolean,

    /** Include social reminders */
    val includeSocialReminders: Boolean,

    /** User's energy pattern */
    val energyPattern: EnergyPattern,

    /** Key lifestyle context */
    val lifestyleContext: List<String>,

    /** Best time for focused work (morning, afternoon, evening); null when the user didn't say */
    val bestFocusTime: String? = null
) {
    companion object {
        /**
         * Constraints when preferences are not set up (Oct 2026): nothing assumed. No ranking, and the
         * whole day (6:00-22:00) as the planning window; it was "work first" and 9-17.
         */
        fun default() = PreferenceConstraints(
            priorityRanking = emptyList(),
            blockedSlots = emptyList(),
            workHoursStart = 6,
            workHoursEnd = 22,
            activeMode = null,
            aggressiveness = SuggestionAggressiveness.BALANCED,
            includeHealthReminders = true,
            includeSocialReminders = true,
            energyPattern = EnergyPattern.STEADY,
            lifestyleContext = emptyList()
        )
    }
}

/**
 * A blocked time slot (not available for scheduling).
 */
data class BlockedSlot(
    /** Start hour (0-23) */
    val startHour: Int,

    /** End hour (0-23) */
    val endHour: Int,

    /** Reason for blocking */
    val reason: String
)

/**
 * Special modes that affect planning behavior.
 */
enum class SpecialMode(val displayName: String, val description: String) {
    VACATION("Vacation Mode", "Minimal suggestions, relaxation focus"),
    CRUNCH("Crunch Mode", "Maximize work, minimize breaks"),
    HOLIDAY("Holiday Mode", "Balance family time with light tasks")
}

/**
 * How aggressive the AI should be with suggestions.
 */
enum class SuggestionAggressiveness {
    GENTLE,     // Fewer suggestions, more conservative
    BALANCED,   // Default balance
    PROACTIVE,  // More suggestions, helpful but not pushy
    AGGRESSIVE  // Many suggestions, bold recommendations
}

// ═══════════════════════════════════════════════════════════════════════════
// AGENTIC CONTEXT (Full input to the Agentic Brain)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Complete context for the Agentic Planning Service.
 * Assembled by AgenticContextBuilder from multiple sources.
 */
data class AgenticContext(
    /** The date being planned */
    val planningDate: LocalDate,

    /** Current hour (for time-aware suggestions) */
    val currentHour: Int,

    /** User preference constraints */
    val preferences: PreferenceConstraints,

    /** Today's capacity analysis */
    val todayCapacity: CapacityAnalysis,

    /** Tomorrow's capacity analysis (for overflow) */
    val tomorrowCapacity: CapacityAnalysis?,

    /** Today's events */
    val todayEvents: List<LocalEvent>,

    /** Today's tasks (all pages) */
    val todayTasks: List<Task>,

    /** Overdue tasks from previous days */
    val overdueTasks: List<Task>,

    /** Zombie tasks (migrated 3+ times) */
    val zombieTasks: List<Task>,

    /** OCR text for handwritten tasks (taskId -> text) */
    val taskOcrMap: Map<String, String>,

    /** Pattern insights (completion rate, busy days, etc.) */
    val patternInsights: PatternInsights?,

    /** Summary of nearby days (past 2, next 5) for added context */
    val nearbyContextSummary: String?,

    /** Detected task-event relationships for scheduling hints */
    val taskEventRelations: List<TaskEventMatcher.TaskEventRelation> = emptyList(),

    /** Events for upcoming days (next 1-5), keyed by "yyyy-MM-dd" */
    val upcomingDayEvents: Map<String, List<LocalEvent>> = emptyMap()
) {
    /** Count of actionable items that may need proposals */
    val actionableItemCount: Int get() =
        overdueTasks.size + zombieTasks.size +
        todayTasks.count { !it.isCompleted && (it.typedText != null || taskOcrMap.containsKey(it.id)) }

    /** Whether user is overloaded (overbooked + overdue tasks) */
    val isOverloaded: Boolean get() =
        todayCapacity.isOverbooked || overdueTasks.size >= 3 || zombieTasks.isNotEmpty()
}

/**
 * Pattern insights from historical task data.
 */
data class PatternInsights(
    /** Completion rate over last 7 days */
    val weeklyCompletionRate: Float,

    /** Day of week with most completions */
    val bestDay: String?,

    /** Day of week with fewest completions */
    val worstDay: String?,

    /** Current streak of days with completed tasks */
    val currentStreak: Int,

    /** Average tasks completed per day */
    val avgCompletedPerDay: Float
)

// ═══════════════════════════════════════════════════════════════════════════
// PROCESSING STATE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Processing state for the Agentic Plan Dialog UI.
 */
sealed class AgenticPlanProcessingState {
    /**
     * Currently processing with stage feedback.
     */
    data class Processing(val stageMessage: String) : AgenticPlanProcessingState()

    /**
     * Ready to display proposals.
     */
    data object Ready : AgenticPlanProcessingState()

    /**
     * Error during processing.
     */
    data class Error(val message: String) : AgenticPlanProcessingState()

    /**
     * Currently executing a specific proposal.
     */
    data class Executing(val proposalId: String) : AgenticPlanProcessingState()
}

// ═══════════════════════════════════════════════════════════════════════════
// AI RESPONSE MODELS (for JSON parsing)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Expected JSON response structure from the AI model.
 */
data class AgenticPlanResponse(
    @SerializedName("summary") val summary: String,
    @SerializedName("capacityStatus") val capacityStatus: String,
    @SerializedName("proposals") val proposals: List<AgenticProposalJson>
)

/**
 * Proposal as parsed from AI JSON response.
 * Converted to AgenticProposal after validation.
 */
data class AgenticProposalJson(
    @SerializedName("action") val action: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("priority") val priority: Int,
    @SerializedName("taskId") val taskId: String? = null,
    @SerializedName("taskText") val taskText: String? = null,
    @SerializedName("targetDate") val targetDate: String? = null,
    @SerializedName("suggestedTimeBlock") val suggestedTimeBlock: TimeBlockJson? = null,
    @SerializedName("suggestedSubtasks") val suggestedSubtasks: List<String>? = null,
    @SerializedName("zombieInfo") val zombieInfo: ZombieInfoJson? = null,
    @SerializedName("alternativeActions") val alternativeActions: List<AlternativeActionJson>? = null,
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int? = null
) {
    /** Convert to proper AgenticProposal with validated action type */
    fun toProposal(id: String): AgenticProposal? {
        val actionType = try {
            ProposalAction.valueOf(action.uppercase().replace(" ", "_"))
        } catch (e: Exception) { return null }

        return AgenticProposal(
            id = id,
            action = actionType,
            title = title,
            description = description,
            reason = reason,
            confidence = confidence.coerceIn(0f, 1f),
            priority = priority.coerceAtLeast(1),
            taskId = taskId,
            taskText = taskText,
            targetDate = targetDate,
            suggestedTimeBlock = suggestedTimeBlock?.toTimeBlock(),
            suggestedSubtasks = suggestedSubtasks,
            zombieInfo = zombieInfo?.toZombieInfo(),
            alternativeActions = alternativeActions?.mapNotNull { it.toAlternativeAction() },
            estimatedMinutes = estimatedMinutes
        )
    }
}

data class TimeBlockJson(
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("title") val title: String,
    @SerializedName("durationMinutes") val durationMinutes: Int,
    @SerializedName("isDeepWork") val isDeepWork: Boolean = true
) {
    fun toTimeBlock() = TimeBlock(startTime, endTime, title, durationMinutes, isDeepWork)
}

data class ZombieInfoJson(
    @SerializedName("migrationCount") val migrationCount: Int,
    @SerializedName("originalDate") val originalDate: String?,
    @SerializedName("daysSinceCreation") val daysSinceCreation: Int,
    @SerializedName("suggestion") val suggestion: String,
    @SerializedName("suggestionReason") val suggestionReason: String
) {
    fun toZombieInfo(): ZombieInfo? {
        val suggestionType = try {
            ZombieSuggestion.valueOf(suggestion.uppercase().replace(" ", "_"))
        } catch (e: Exception) { ZombieSuggestion.BREAK_DOWN }

        return ZombieInfo(
            migrationCount = migrationCount,
            originalDate = originalDate,
            daysSinceCreation = daysSinceCreation,
            suggestion = suggestionType,
            suggestionReason = suggestionReason
        )
    }
}

data class AlternativeActionJson(
    @SerializedName("label") val label: String,
    @SerializedName("action") val action: String,
    @SerializedName("targetDate") val targetDate: String? = null
) {
    fun toAlternativeAction(): AlternativeAction? {
        val actionType = try {
            ProposalAction.valueOf(action.uppercase().replace(" ", "_"))
        } catch (e: Exception) { return null }

        return AlternativeAction(label, actionType, targetDate)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Result of the agentic planning operation.
 */
data class AgenticPlanResult(
    /** Summary of the day's situation */
    val summary: String,

    /** Capacity status label */
    val capacityStatus: String,

    /** List of proposals (sorted by priority) */
    val proposals: List<AgenticProposal>,

    /** Error message if failed */
    val error: String? = null,

    /** Free time slots from capacity analysis */
    val freeSlots: List<FreeSlot> = emptyList(),

    /** Total estimated commitment in minutes */
    val totalEstimatedMinutes: Int = 0,

    /** Available free time in minutes */
    val availableFreeMinutes: Int = 0
) {
    val isSuccess: Boolean get() = error == null
    val hasProposals: Boolean get() = proposals.isNotEmpty()

    /** Get formatted commitment summary for display */
    fun getCommitmentSummary(): String? {
        if (totalEstimatedMinutes <= 0) return null
        val hours = totalEstimatedMinutes / 60
        val minutes = totalEstimatedMinutes % 60
        val timeStr = if (hours > 0) {
            if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        } else {
            "${minutes}m"
        }

        return if (availableFreeMinutes > 0) {
            val availHours = availableFreeMinutes / 60
            val availMins = availableFreeMinutes % 60
            val availStr = if (availHours > 0) {
                if (availMins > 0) "${availHours}h ${availMins}m" else "${availHours}h"
            } else {
                "${availMins}m"
            }
            "${proposals.size} tasks (est. $timeStr of $availStr free)"
        } else {
            "${proposals.size} tasks (est. $timeStr)"
        }
    }

    companion object {
        fun error(message: String) = AgenticPlanResult(
            summary = "",
            capacityStatus = "",
            proposals = emptyList(),
            error = message
        )
    }
}

/**
 * Result of executing a single proposal.
 */
data class ProposalExecutionResult(
    val proposalId: String,
    val success: Boolean,
    val message: String,
    val error: String? = null,
    val resultPageIndex: Int? = null,
    val affectedDate: String? = null,
    val createdTaskId: String? = null,
    val createdEventId: String? = null
)
