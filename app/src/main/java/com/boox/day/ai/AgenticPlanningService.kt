package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.ai.AgenticContext
import com.boox.day.data.ai.AgenticPlanResponse
import com.boox.day.data.ai.AgenticPlanResult
import com.boox.day.data.ai.AgenticProposal
import com.boox.day.data.ai.PreferenceConstraints
import com.boox.day.data.ai.ProposalAction
import com.boox.day.data.ai.ScoredContext
import com.boox.day.data.ai.ScoredItem
import com.boox.day.data.ai.ScorerIds
import com.boox.day.data.ai.SpecialMode
import com.boox.day.data.db.Task
import com.boox.day.ocr.model.OcrSource
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Agentic Planning Service - "The Bridge"
 *
 * Transforms the BooxDay app from a passive data viewer into an intelligent
 * "Chief of Staff" that proposes actionable optimizations for the user's day.
 *
 * This service:
 * 1. Analyzes the user's context (tasks, events, patterns, preferences)
 * 2. Generates actionable proposals (move tasks, block time, flag zombies)
 * 3. Returns structured proposals the user can execute with one tap
 *
 * Uses ModelConfigProvider.getReasoningFallbackChain() for model selection.
 */
object AgenticPlanningService {

    private const val TAG = "AgenticPlanning"
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val multiWhitespaceRegex = Regex("\\s+")
    private val proposalNormalizationRegex = Regex("[^\\p{L}\\p{N}]+")
    private val planMyDayAllowedActions = setOf(
        ProposalAction.ADD_TASK,
        ProposalAction.BREAK_DOWN_TASK,
        ProposalAction.FLAG_ZOMBIE,
        ProposalAction.DELEGATE_OR_DROP,
        ProposalAction.BATCH_SIMILAR,
        ProposalAction.CREATE_TIMEBLOCK,
        ProposalAction.SCHEDULE_BUFFER
    )

    /**
     * Format a task's due date relative to the planning date.
     * Returns null if task has no due date.
     */
    private fun formatDueDate(task: Task, planningDate: LocalDate): String? {
        val dueLocalDate = com.boox.day.data.db.TaskDueDates.toLocalDate(task.dueDay) ?: return null
        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(planningDate, dueLocalDate)
        return when {
            daysUntil < 0 -> "OVERDUE ${-daysUntil}d"
            daysUntil == 0L -> "DUE TODAY"
            daysUntil == 1L -> "DUE TOMORROW"
            daysUntil <= 7 -> "DUE in ${daysUntil}d"
            else -> "DUE ${dueLocalDate.format(dateFormatter)}"
        }
    }

    private fun resolveTaskText(task: Task, taskOcrMap: Map<String, String>): String? {
        return taskOcrMap[task.id]?.takeIf { it.isNotBlank() }
            ?: task.typedText?.takeIf { it.isNotBlank() }
            ?: task.recognizedText?.takeIf { it.isNotBlank() }
            ?: task.convertedText?.takeIf { it.isNotBlank() }
    }

    private fun hasTaskContent(task: Task, taskOcrMap: Map<String, String>): Boolean {
        return task.isCompleted || task.priority.isNotBlank() || !resolveTaskText(task, taskOcrMap).isNullOrBlank()
    }

    /**
     * Format a scored item's component scores into human-readable explanation.
     * Used to provide AI with detailed scoring context for better reason generation.
     */
    private fun formatScoreExplanation(scoredItem: ScoredItem): String {
        val parts = mutableListOf<String>()

        // Urgency component
        scoredItem.componentScores[ScorerIds.URGENCY]?.let { score ->
            when {
                score.rawScore >= 0.8f -> parts.add("HIGH urgency")
                score.rawScore >= 0.5f -> parts.add("MEDIUM urgency")
            }
            if (score.signals.isNotEmpty()) {
                parts.add("(${score.signals.first()})")
            }
        }

        // Preference alignment
        scoredItem.componentScores[ScorerIds.PREFERENCE]?.let { score ->
            if (score.rawScore >= 0.6f && score.signals.isNotEmpty()) {
                parts.add("matches ${score.signals.first()}")
            }
        }

        // Calendar context
        scoredItem.componentScores[ScorerIds.CALENDAR]?.let { score ->
            if (score.rawScore >= 0.5f && score.signals.isNotEmpty()) {
                parts.add(score.signals.first())
            }
        }

        // Capacity fit
        scoredItem.componentScores[ScorerIds.CAPACITY]?.let { score ->
            if (score.rawScore >= 0.6f && score.signals.isNotEmpty()) {
                parts.add(score.signals.first())
            }
        }

        // Complexity
        scoredItem.componentScores[ScorerIds.COMPLEXITY]?.let { score ->
            if (score.rawScore >= 0.7f && score.signals.isNotEmpty()) {
                parts.add("complex: ${score.signals.first()}")
            }
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(" + ")
        } else {
            ""
        }
    }

    /**
     * Generate agentic planning proposals for a given context.
     *
     * @param context The complete agentic context (assembled by AgenticContextBuilder)
     * @param onStageChange Callback for progress updates
     * @return AgenticPlanResult with proposals or error
     */
    suspend fun generatePlan(
        context: AgenticContext,
        onStageChange: (String) -> Unit = {}
    ): AgenticPlanResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting agentic planning for ${context.planningDate}")

        onStageChange("Analyzing your day...")

        // Build the AI request
        val systemPrompt = buildSystemPrompt(context)
        val userPrompt = buildUserPrompt(context)

        // Model fallback chain (text + JSON output) - uses 3×3 retry via AiRetryExecutor
        val models = ModelConfigProvider.getReasoningFallbackChain()

        onStageChange("Generating personalized proposals...")

        when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(userPrompt)
                    temperature(0.4f)  // Some creativity for suggestions
                }
            },
            processResponse = { response, model ->
                // Length only (Oct 2026): the answer names the user's meetings and people
                Log.d(TAG, "${model.displayName} succeeded: ${response.data.length} chars")
                val parsed = parseResponse(response.data, context)
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> {
                onStageChange("Processing recommendations...")
                Log.d(TAG, "Generated ${result.data.proposals.size} proposals")
                result.data
            }
            is AiRetryExecutor.RetryResult.Failure -> {
                AgenticPlanResult.error(result.message)
            }
        }
    }

    /**
     * Build the system prompt that defines the AI's role and constraints.
     */
    private fun buildSystemPrompt(context: AgenticContext): String {
        val prefs = context.preferences

        return buildString {
            appendLine("You are an intelligent day planning assistant for a task management app.")
            appendLine("Your role is to propose task-focused, actionable suggestions for the selected day.")
            appendLine()
            appendLine("## YOUR CAPABILITIES")
            appendLine("You can suggest the following types of proposals:")
            appendLine("- ADD_TASK: Propose a new task the user can add")
            appendLine("- BREAK_DOWN_TASK: Suggest breaking a complex task into subtasks")
            appendLine("- FLAG_ZOMBIE: Highlight tasks that have been deferred 3+ times")
            appendLine("- DELEGATE_OR_DROP: Question if a lingering task is still needed")
            appendLine("- BATCH_SIMILAR: Group similar small tasks together")
            appendLine("- CREATE_TIMEBLOCK: Block calendar time for focused work on a task")
            appendLine("- SCHEDULE_BUFFER: Add buffer/break time between back-to-back meetings")
            appendLine()

            // User preferences
            appendLine("## USER PREFERENCES")
            appendLine("Priority ranking: ${prefs.priorityRanking.joinToString(" > ")}")
            appendLine("Work hours: ${prefs.workHoursStart}:00 - ${prefs.workHoursEnd}:00")
            appendLine("Energy pattern: ${prefs.energyPattern.name.lowercase().replace("_", " ")}")

            if (prefs.lifestyleContext.isNotEmpty()) {
                appendLine("Lifestyle: ${prefs.lifestyleContext.joinToString(", ")}")
            }

            if (prefs.blockedSlots.isNotEmpty()) {
                appendLine("Blocked times: ${prefs.blockedSlots.joinToString(", ") { "${it.startHour}:00-${it.endHour}:00 (${it.reason})" }}")
            }

            prefs.activeMode?.let {
                appendLine()
                appendLine("## SPECIAL MODE: ${it.displayName.uppercase()}")
                appendLine(it.description)
                when (it) {
                    SpecialMode.VACATION -> appendLine("Generate minimal suggestions focused on rest.")
                    SpecialMode.CRUNCH -> appendLine("Maximize productivity with high-impact tasks.")
                    SpecialMode.HOLIDAY -> appendLine("Balance family time with light tasks.")
                }
            }

            appendLine()
            appendLine("## CONSTRAINTS")
            appendLine("- Generate 3-7 high-value proposals (fewer in vacation mode)")
            appendLine("- Each proposal must be actionable with a single tap")
            appendLine("- Prioritize proposals by impact (highest first)")
            appendLine("- Do NOT move tasks to other days")
            appendLine("- For FLAG_ZOMBIE: Always include alternative actions")
            appendLine("- Use ADD_TASK for new tasks (they are only added on user tap)")
            appendLine("- If today's tasks are empty or sparse, propose new tasks using ADD_TASK")
            appendLine("- Reference specific tasks by their ID")
            appendLine()
            appendLine("## TIME BLOCKING GUIDELINES")
            appendLine("- For CREATE_TIMEBLOCK: Use free slots from CAPACITY ANALYSIS for tasks needing 30+ min focus")
            appendLine("- For SCHEDULE_BUFFER: Suggest 10-15 min breaks when detecting back-to-back meetings")
            appendLine("- Always include suggestedTimeBlock with startTime, endTime, title, durationMinutes")
            appendLine("- Only suggest time blocks that fit within available free slots")
            if (!prefs.includeHealthReminders) {
                appendLine("- Do NOT suggest health/wellness tasks")
            }
            if (!prefs.includeSocialReminders) {
                appendLine("- Do NOT suggest social activities")
            }
            appendLine()

            appendLine("## OUTPUT FORMAT")
            appendLine("Return ONLY a JSON object with this structure:")
            appendLine("**IMPORTANT: Do NOT wrap the JSON in markdown code blocks. Output raw JSON only.**")
            appendLine()
            appendLine("""
{
  "summary": "Brief 1-sentence summary of the day's situation",
  "capacityStatus": "Light|Moderate|Busy|Overbooked|Severely Overbooked",
  "proposals": [
    {
      "action": "ADD_TASK|BREAK_DOWN_TASK|FLAG_ZOMBIE|DELEGATE_OR_DROP|BATCH_SIMILAR|CREATE_TIMEBLOCK|SCHEDULE_BUFFER",
      "title": "Short action title (e.g., 'Add a task')",
      "description": "What will happen if executed",
      "reason": "Why this is suggested",
      "confidence": 0.0-1.0,
      "priority": 1-7 (1 = highest),
      "estimatedMinutes": 30,
      "suggestedTimeBlock": { "startTime": "09:00", "endTime": "09:30", "title": "Task name", "durationMinutes": 30 },
      "taskId": "task ID if task-related",
      "taskText": "task text for display",
      "suggestedSubtasks": ["subtask 1", "subtask 2"],
      "zombieInfo": { "migrationCount": 3, "originalDate": "yyyy-MM-dd", "daysSinceCreation": 7, "suggestion": "BREAK_DOWN|SCHEDULE_TODAY|COMMIT_DATE|DELEGATE|DROP", "suggestionReason": "Why this action" },
      "alternativeActions": [{ "label": "Break it down", "action": "BREAK_DOWN_TASK" }]
    }
  ]
}

DURATION ESTIMATION GUIDELINES for "estimatedMinutes":
- ADD_TASK: Estimate based on task complexity (15-60 min typical)
- BREAK_DOWN_TASK: 10-15 min for the breakdown activity itself
- FLAG_ZOMBIE: 5 min for review/decision
- DELEGATE_OR_DROP: 5 min for decision
- BATCH_SIMILAR: Sum of batched tasks (typically 30-60 min)

TIME SCHEDULING GUIDELINES for "suggestedTimeBlock":
- ALWAYS include suggestedTimeBlock for every proposal when free slots are available
- Use the "Available time slots" from CAPACITY ANALYSIS to pick appropriate times
- startTime/endTime format: "HH:mm" (24-hour, e.g., "09:00", "14:30")
- Match task duration to slot duration (don't suggest 2h task for 30min slot)
- Schedule complex/focus tasks in morning slots (before 12:00)
- Schedule quick tasks, reviews, decisions in afternoon slots (12:00-17:00)
- If no suitable slot exists, omit suggestedTimeBlock (task goes to "Anytime")
- durationMinutes should match estimatedMinutes
""".trimIndent())
        }
    }

    /**
     * Build the user prompt with the day's data.
     */
    private suspend fun buildUserPrompt(context: AgenticContext): String {
        val calendarSection = if (context.todayEvents.isNotEmpty()) {
            buildString {
                appendLine("Today's events (${context.todayEvents.size}):")
                context.todayEvents.take(8).forEach { event ->
                    val timeLabel = if (event.isAllDay) {
                        "All day"
                    } else {
                        val startHour = java.time.Instant.ofEpochMilli(event.startTime)
                            .atZone(java.time.ZoneId.systemDefault()).hour
                        when (startHour) {
                            in 5..11 -> "Morning"
                            in 12..16 -> "Afternoon"
                            in 17..20 -> "Evening"
                            else -> "Night"
                        }
                    }
                    appendLine("- [$timeLabel] ${event.title}")
                }
            }.trim()
        } else null

        val patternSection = context.patternInsights?.let { insights ->
            buildString {
                appendLine("## USER PATTERNS (last 7 days)")
                appendLine("Completion rate: ${(insights.weeklyCompletionRate * 100).toInt()}%")
                appendLine("Current streak: ${insights.currentStreak} days")
                insights.bestDay?.let { appendLine("Best day: $it") }
            }.trim()
        }

        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = null,
            date = context.planningDate,
            rangeStart = context.planningDate.minusDays(2),
            rangeEnd = context.planningDate.plusDays(5),
            includeBooxNotes = true,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO),
            calendarSection = calendarSection,
            patternSection = patternSection
        )

        return buildString {
            val today = context.planningDate.format(dateFormatter)
            val tomorrow = context.planningDate.plusDays(1).format(dateFormatter)

            if (contextBlock.isNotBlank()) {
                appendLine(contextBlock)
                appendLine()
            }

            appendLine("## TODAY'S DATE: $today (${context.planningDate.dayOfWeek})")
            val timeOfDay = when (context.currentHour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
            appendLine("Time of day: $timeOfDay")
            appendLine()

            // Capacity analysis
            appendLine("## CAPACITY ANALYSIS")
            appendLine("Status: ${context.todayCapacity.getCapacityStatus()}")
            appendLine("Committed: ${context.todayCapacity.committedMinutes} min (${context.todayCapacity.capacityPercentage.toInt()}%)")
            appendLine("Free time: ${context.todayCapacity.freeMinutes} min")
            if (context.todayCapacity.freeSlots.isNotEmpty()) {
                appendLine("Available time slots:")
                context.todayCapacity.freeSlots.forEach { slot ->
                    appendLine("  - ${slot.startTime}-${slot.endTime} (${slot.durationMinutes} min, ${slot.label})")
                }
            }
            appendLine()

            // Today's tasks
            val tasksWithContent = context.todayTasks.filter { task ->
                task.typedText?.isNotBlank() == true ||
                context.taskOcrMap.containsKey(task.id)
            }

            if (tasksWithContent.isNotEmpty()) {
                appendLine("## TODAY'S TASKS (${tasksWithContent.size})")
                tasksWithContent.forEach { task ->
                    val text = task.typedText ?: context.taskOcrMap[task.id] ?: "[unknown]"
                    val status = if (task.isCompleted) "[x]" else "[ ]"
                    val priority = if (task.priority.isNotBlank()) "(${task.priority})" else ""
                    appendLine("  - ID:${task.id} $status $priority $text")
                }
                appendLine()
            }

            // Overdue tasks
            if (context.overdueTasks.isNotEmpty()) {
                appendLine("## OVERDUE TASKS (${context.overdueTasks.size})")
                context.overdueTasks.take(5).forEach { task ->
                    val text = task.typedText ?: context.taskOcrMap[task.id] ?: "[unknown]"
                    val daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.parse(task.dayDate, dateFormatter),
                        context.planningDate
                    ).toInt()
                    appendLine("  - ID:${task.id} [${daysOverdue}d overdue] $text")
                }
                appendLine()
            }

            // Zombie tasks
            if (context.zombieTasks.isNotEmpty()) {
                appendLine("## ZOMBIE TASKS (deferred 3+ times)")
                context.zombieTasks.forEach { task ->
                    val text = task.typedText ?: context.taskOcrMap[task.id] ?: "[unknown]"
                    val originalAge = task.originalDate?.let { orig ->
                        try {
                            java.time.temporal.ChronoUnit.DAYS.between(
                                LocalDate.parse(orig, dateFormatter),
                                context.planningDate
                            ).toInt()
                        } catch (e: Exception) { null }
                    } ?: 0
                    appendLine("  - ID:${task.id} [moved ${task.migrationCount}x, ${originalAge}d old] $text")
                }
                appendLine()
            }

            // Tomorrow capacity
            context.tomorrowCapacity?.let { tomorrowCap ->
                appendLine("## TOMORROW ($tomorrow)")
                appendLine("Capacity: ${tomorrowCap.getCapacityStatus()} (${tomorrowCap.capacityPercentage.toInt()}%)")
                appendLine("Free time: ${tomorrowCap.freeMinutes} min")
                appendLine()
            }

            appendLine("## INSTRUCTIONS")
            appendLine("Analyze this context and generate actionable proposals.")
            appendLine("Focus on high-impact improvements the user can execute immediately.")
            if (context.isOverloaded) {
                appendLine("NOTE: User appears overloaded. Prioritize load reduction.")
            }
            if (context.zombieTasks.isNotEmpty()) {
                appendLine("NOTE: Address zombie tasks with FLAG_ZOMBIE proposals.")
            }
        }
    }

    /**
     * Parse the AI response into structured proposals.
     */
    private fun parseResponse(
        json: String,
        context: AgenticContext,
        allowedActions: Set<ProposalAction>? = null,
        minProposals: Int? = null,
        maxProposals: Int? = null
    ): AgenticPlanResult {
        val cleanJson = JsonExtractor.extract(json)

        return try {
            val parsed = gson.fromJson(cleanJson, AgenticPlanResponse::class.java)
            val summary = collapseWhitespace(parsed.summary).ifBlank { "A focused plan for today." }
            val capacityStatus = collapseWhitespace(parsed.capacityStatus)
                .ifBlank { context.todayCapacity.getCapacityStatus() }
            val existingTaskTexts = (context.todayTasks + context.overdueTasks + context.zombieTasks)
                .mapNotNull { task -> resolveTaskText(task, context.taskOcrMap) }

            // Convert JSON proposals to proper AgenticProposal objects, deduplicated by content
            val proposals = dedupeAgenticProposals(
                parsed.proposals.mapNotNull { jsonProposal ->
                jsonProposal.toProposal(NanoIdUtils.randomNanoId())
                }.mapNotNull(::sanitizeProposal),
                existingTaskTexts = existingTaskTexts
            )

            // Validate proposals (ensure task IDs exist, dates are valid, etc.)
            val validatedProposals = proposals.mapNotNull { proposal ->
                if (allowedActions != null && proposal.action !in allowedActions) {
                    Log.w(TAG, "Skipping unsupported proposal action: ${proposal.action}")
                    null
                } else {
                    validateProposal(proposal, context)
                }
            }

            val cappedProposals = if (maxProposals != null) {
                validatedProposals.take(maxProposals)
            } else {
                validatedProposals
            }

            if (minProposals != null && cappedProposals.size < minProposals) {
                Log.w(TAG, "Insufficient proposals: ${cappedProposals.size} < $minProposals")
                return AgenticPlanResult.error("AI response did not meet proposal count requirements")
            }

            // Calculate total estimated commitment time
            val totalEstimated = cappedProposals.sumOf { proposal ->
                proposal.estimatedMinutes ?: when (proposal.action) {
                    // Default estimates if AI didn't provide one
                    ProposalAction.ADD_TASK -> 30
                    ProposalAction.BREAK_DOWN_TASK -> 15
                    ProposalAction.FLAG_ZOMBIE -> 5
                    ProposalAction.DELEGATE_OR_DROP -> 5
                    ProposalAction.BATCH_SIMILAR -> 45
                    ProposalAction.CREATE_TIMEBLOCK -> 60
                    ProposalAction.SCHEDULE_BUFFER -> 30
                    ProposalAction.MOVE_TASK -> 5
                }
            }

            AgenticPlanResult(
                summary = summary,
                capacityStatus = capacityStatus,
                proposals = cappedProposals,
                freeSlots = context.todayCapacity.freeSlots,
                totalEstimatedMinutes = totalEstimated,
                availableFreeMinutes = context.todayCapacity.freeMinutes
            )
        } catch (e: JsonSyntaxException) {
            // Length only (Oct 2026): error lines stay in release builds and in crash reports
            Log.e(TAG, "JSON parse error (${cleanJson.length} chars)", e)
            AgenticPlanResult.error("Could not parse AI response")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected parse error", e)
            AgenticPlanResult.error("Processing error: ${e.message}")
        }
    }

    /**
     * Validate a proposal to ensure it's executable.
     */
    private fun validateProposal(proposal: AgenticProposal, context: AgenticContext): AgenticProposal? {
        val allTasks = context.todayTasks + context.overdueTasks + context.zombieTasks

        when (proposal.action) {
            ProposalAction.MOVE_TASK -> {
                // Validate task ID
                if (proposal.taskId.isNullOrBlank()) {
                    Log.w(TAG, "Proposal MOVE_TASK missing taskId")
                    return null
                }

                val task = allTasks.find { it.id == proposal.taskId }
                if (task == null) {
                    Log.w(TAG, "Task ${proposal.taskId} not found in context")
                    return null
                }

                if (proposal.targetDate.isNullOrBlank()) {
                    Log.w(TAG, "MOVE_TASK missing targetDate")
                    return null
                }

                // Validate target date is not past due date
                val targetDate = proposal.targetDate?.let {
                    try { LocalDate.parse(it, dateFormatter) } catch (e: Exception) { null }
                }
                if (targetDate == null) {
                    Log.w(TAG, "Invalid target date: ${proposal.targetDate}")
                    return null
                }
                val dueDate = com.boox.day.data.db.TaskDueDates.toLocalDate(task.dueDay)
                if (targetDate != null && dueDate != null) {
                    if (targetDate.isAfter(dueDate)) {
                        Log.w(TAG, "MOVE_TASK rejected: target $targetDate is after due date $dueDate")
                        return null
                    }
                }
            }

            ProposalAction.ADD_TASK -> {
                // Models often put the task text in title rather than taskText — fall back
                if (proposal.taskText.isNullOrBlank() && proposal.title.isNotBlank()) {
                    return proposal.copy(taskText = proposal.title)
                }
                if (proposal.taskText.isNullOrBlank()) {
                    Log.w(TAG, "Proposal ADD_TASK missing taskText and title")
                    return null
                }
            }

            ProposalAction.BREAK_DOWN_TASK, ProposalAction.FLAG_ZOMBIE,
            ProposalAction.DELEGATE_OR_DROP -> {
                // These require a valid task ID
                if (proposal.taskId.isNullOrBlank()) {
                    Log.w(TAG, "Proposal ${proposal.action} missing taskId")
                    return null
                }

                // Verify task exists in context
                val taskExists = allTasks.any { it.id == proposal.taskId }
                if (!taskExists) {
                    Log.w(TAG, "Task ${proposal.taskId} not found in context")
                    return null
                }
            }

            ProposalAction.CREATE_TIMEBLOCK, ProposalAction.SCHEDULE_BUFFER -> {
                // These require a time block
                val timeBlock = proposal.suggestedTimeBlock
                if (timeBlock == null) {
                    Log.w(TAG, "Proposal ${proposal.action} missing timeBlock")
                    return null
                }

                // Validate time block time format
                val blockStart = try {
                    java.time.LocalTime.parse(timeBlock.startTime)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid time block start: ${timeBlock.startTime}")
                    return null
                }
                val blockEnd = try {
                    java.time.LocalTime.parse(timeBlock.endTime)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid time block end: ${timeBlock.endTime}")
                    return null
                }

                // Reject inverted or zero-duration timeblocks
                if (blockEnd <= blockStart) {
                    Log.w(TAG, "CREATE_TIMEBLOCK rejected: end time $blockEnd <= start time $blockStart")
                    return null
                }

                val targetDate = proposal.targetDate?.let {
                    try { LocalDate.parse(it, dateFormatter) } catch (e: Exception) { null }
                }
                val eventDate = targetDate ?: context.planningDate
                if (eventDate != context.planningDate) {
                    Log.w(TAG, "Skipping time block validation for non-today date: $eventDate")
                    return proposal
                }

                // Validate time block doesn't overlap with existing events
                // Check for conflicts with today's events
                val conflicts = context.todayEvents.any { event ->
                    if (event.isAllDay) return@any false
                    try {
                        val eventStart = java.time.Instant.ofEpochMilli(event.startTime)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                        val eventEnd = event.endTime?.let {
                            java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalTime()
                        } ?: eventStart.plusHours(1)

                        // Check for overlap: !(blockEnd <= eventStart || blockStart >= eventEnd)
                        !(blockEnd <= eventStart || blockStart >= eventEnd)
                    } catch (e: Exception) {
                        false
                    }
                }

                if (conflicts) {
                    Log.w(TAG, "CREATE_TIMEBLOCK rejected: conflicts with existing event")
                    return null
                }
            }

            ProposalAction.BATCH_SIMILAR -> {
                // No specific validation needed
            }
        }

        // Validate target date format if present
        proposal.targetDate?.let { dateStr ->
            try {
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid target date: $dateStr")
                return null
            }
        }

        return proposal
    }

    // ========================================================================
    // V3 Methods - Using Unified Contextual Intelligence Engine
    // ========================================================================

    /**
     * Generate agentic planning proposals using pre-computed Intelligence Engine scores.
     *
     * This V3 version leverages the Unified Contextual Intelligence Engine for:
     * - Pre-computed task prioritization (UrgencyScorer, PreferenceScorer)
     * - Complexity analysis for BREAK_DOWN_TASK proposals
     * - Calendar awareness for time-blocking suggestions
     * - Pattern recognition for zombie task handling
     *
     * @param context The complete agentic context
     * @param scoredContext Pre-computed scores from IntelligenceEngine
     * @param onStageChange Callback for progress updates
     * @return AgenticPlanResult with proposals or error
     */
    suspend fun generatePlanV3(
        context: AgenticContext,
        scoredContext: ScoredContext,
        onStageChange: (String) -> Unit = {}
    ): AgenticPlanResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting agentic planning V3 for ${context.planningDate}")

        onStageChange("Analyzing your day...")

        // Build enhanced AI request with pre-computed scores
        val systemPrompt = buildSystemPromptV3(context, scoredContext)
        val userPrompt = buildUserPromptV3(context, scoredContext)

        Log.d(TAG, "Prompts built: system=${systemPrompt.length} chars, user=${userPrompt.length} chars")

        // Model fallback chain (text + JSON output)
        val models = ModelConfigProvider.getReasoningFallbackChain()

        onStageChange("Generating personalized proposals...")

        when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(userPrompt)
                    temperature(0.5f)
                    maxTokens(8192)
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "${model.displayName} succeeded: ${response.data.length} chars")
                val parsed = parseResponse(
                    response.data,
                    context,
                    allowedActions = planMyDayAllowedActions,
                    minProposals = proposalRange(context.preferences.activeMode).first,
                    maxProposals = proposalRange(context.preferences.activeMode).last
                )
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> {
                onStageChange("Processing recommendations...")
                Log.d(TAG, "Generated ${result.data.proposals.size} proposals")
                result.data
            }
            is AiRetryExecutor.RetryResult.Failure -> {
                AgenticPlanResult.error(result.message)
            }
        }
    }

    /**
     * Build system prompt with Intelligence Engine context.
     * Unified prompt designed for capable reasoning models (Gemini 3+).
     */
    /**
     * How many proposals Optimize My Day asks for (Oct 2026). Vacation asks for "minimal" ones, so it
     * allows 1-3: with the fixed minimum of 3, a short vacation answer failed as an error.
     */
    internal fun proposalRange(mode: SpecialMode?): IntRange = if (mode == SpecialMode.VACATION) 1..3 else 3..7

    private fun buildSystemPromptV3(context: AgenticContext, scoredContext: ScoredContext): String {
        val prefs = context.preferences

        // Time-of-day strategy
        val timeStrategy = if (context.planningDate != LocalDate.now()) {
            "This is advance planning for the selected day. Optimize the full selected day from the start, not the user's real-world clock time."
        } else {
            when (context.currentHour) {
                in 5..9 -> "It's early morning. Plan the full day ahead. Front-load high-urgency and deep-focus work."
                in 10..11 -> "It's mid-morning. The day is underway. Focus on what's most impactful before lunch."
                in 12..14 -> "It's early afternoon. Half the day is gone. Prioritize what MUST happen today."
                in 15..17 -> "It's late afternoon. Time is limited. Only propose what's realistically achievable before end of day, plus quick wins."
                // Oct 2026: someone most productive in the evening isn't told the day is over
                in 18..20 -> if (prefs.bestFocusTime == "evening") {
                    "It's evening, the user's most productive time. Focused work is fine now; also prep tomorrow."
                } else {
                    "It's evening. The workday is over. Suggest wrapping up loose ends and prepping tomorrow."
                }
                else -> "It's late. Suggest only light review or tomorrow-prep tasks."
            }
        }

        // Situational awareness
        val situationNotes = buildString {
            if (context.overdueTasks.size >= 5) {
                appendLine("This user has ${context.overdueTasks.size} overdue tasks. They may be overwhelmed. Be empathetic and help triage — don't pile on.")
            }
            if (context.zombieTasks.size >= 3) {
                appendLine("Multiple zombie tasks detected. Address these directly but constructively — help the user make decisions, not feel guilty.")
            }
            context.patternInsights?.let { insights ->
                if (insights.currentStreak > 0) {
                    appendLine("The user is on a ${insights.currentStreak}-day completion streak. Acknowledge momentum and encourage continuing it.")
                }
                if (insights.weeklyCompletionRate < 0.3f) {
                    appendLine("Low completion rate (${(insights.weeklyCompletionRate * 100).toInt()}%) this week. Suggest fewer, achievable tasks to rebuild confidence.")
                }
            }
            if (context.todayTasks.count { !it.isCompleted } == 0 && context.overdueTasks.isEmpty()) {
                appendLine("Today is a blank slate. Propose useful new tasks grounded in upcoming events, overdue items, or user priorities.")
            }
        }.trim()

        val dist = scoredContext.aggregateMetrics.scoreDistribution

        return buildString {
            // Role and thinking instruction
            appendLine("You are a personal day-planning strategist. You help real people — students, professionals, parents, freelancers — make the most of their day.")
            appendLine()
            appendLine("Before generating proposals, silently reason through:")
            appendLine("1. What is the hardest thing about this person's day?")
            appendLine("2. What is the biggest risk if they do nothing?")
            appendLine("3. What would make this day feel successful for them?")
            appendLine("Then generate proposals that address these three questions.")
            appendLine()

            // Time-of-day strategy
            appendLine("## TIME AWARENESS")
            appendLine(timeStrategy)
            appendLine()

            // Situational awareness
            if (situationNotes.isNotBlank()) {
                appendLine("## SITUATIONAL AWARENESS")
                appendLine(situationNotes)
                appendLine()
            }

            // User profile
            // Oct 2026: no invented work hours or blocked times; only the AI options (Vacation, Health and Social)
            appendLine("## USER PROFILE")
            if (prefs.priorityRanking.isNotEmpty()) {
                appendLine("Most important areas (user's picks): ${prefs.priorityRanking.joinToString(", ")}")
            }
            prefs.bestFocusTime?.let { appendLine("Best time for focused work: $it") }
            if (prefs.priorityRanking.isEmpty() && prefs.bestFocusTime == null && prefs.lifestyleContext.isEmpty()) {
                appendLine("No preferences shared. Don't assume the user's job, family or schedule.")
            }
            if (prefs.lifestyleContext.isNotEmpty()) {
                appendLine("Lifestyle: ${prefs.lifestyleContext.joinToString(", ")}")
            }
            if (prefs.blockedSlots.isNotEmpty()) {
                appendLine("Blocked times: ${prefs.blockedSlots.joinToString(", ") { "${it.startHour}:00-${it.endHour}:00 (${it.reason})" }}")
            }
            prefs.activeMode?.let {
                appendLine()
                appendLine("SPECIAL MODE: ${it.displayName.uppercase()}")
                appendLine(it.description)
                when (it) {
                    SpecialMode.VACATION -> appendLine("Generate minimal suggestions focused on rest and recharging.")
                    SpecialMode.CRUNCH -> appendLine("Maximize output. Focus on deadlines and deliverables.")
                    SpecialMode.HOLIDAY -> appendLine("Balance family/social time with only essential tasks.")
                }
            }
            if (!prefs.includeHealthReminders) appendLine("Do NOT suggest health/wellness tasks.")
            if (!prefs.includeSocialReminders) appendLine("Do NOT suggest social activities.")
            appendLine()

            // Actions and rules
            appendLine("## ALLOWED ACTIONS")
            appendLine("- ADD_TASK: Suggest a new task (only added on user tap)")
            appendLine("- BREAK_DOWN_TASK: Split a complex task into subtasks")
            appendLine("- FLAG_ZOMBIE: Address a task deferred 3+ times (always include alternativeActions)")
            appendLine("- DELEGATE_OR_DROP: Question if a lingering task is still needed")
            appendLine("- BATCH_SIMILAR: Group similar small tasks into one block")
            appendLine("- CREATE_TIMEBLOCK: Reserve a calendar slot for focused work (only when free slots exist)")
            appendLine("- SCHEDULE_BUFFER: Add a break between back-to-back meetings (only when events justify it)")
            appendLine()

            appendLine("## RULES")
            appendLine("1. Generate ${proposalRange(prefs.activeMode).first}-${proposalRange(prefs.activeMode).last} proposals, sorted by priority (1 = highest impact)")
            appendLine("2. Optimize ONLY the selected planning date ${context.planningDate}; do not let the real current date or time override it")
            appendLine("3. Use nearby days only as supporting evidence for work that should happen on the selected planning date")
            appendLine("4. Do NOT propose moving tasks to other days")
            appendLine("5. Every task reference must use the exact ID from the task list")
            appendLine("6. Do not propose actions on already-completed [x] tasks")
            appendLine("7. Vary proposal types — no more than 2 proposals of the same action type")
            appendLine("8. If the selected day is sparse, use ADD_TASK grounded in events, overdue items, or priorities")
            appendLine("9. For time blocks, use free slots from CAPACITY ANALYSIS; omit suggestedTimeBlock if no slot fits")
            appendLine(
                "10. Schedule focus tasks in " +
                    (prefs.bestFocusTime?.let { "the user's best focus time ($it)" } ?: "the earliest free slot that fits") +
                    ", quick tasks around them"
            )
            appendLine("11. Never output duplicate or lightly reworded proposals")
            appendLine("12. Do not output ADD_TASK proposals that already exist in the task list")
            appendLine()

            // Scoring context
            appendLine("## PRE-COMPUTED TASK SCORES")
            appendLine("${scoredContext.scoredItems.size} items scored: ${dist.high} high, ${dist.medium} medium, ${dist.low} low (avg ${String.format("%.2f", scoredContext.aggregateMetrics.avgFinalScore)})")
            appendLine("Each task below has a SCORING line. Use these signals directly in your reasons.")
            appendLine()
            appendLine("Score interpretation:")
            appendLine("- >=0.7: Strong candidate for action today")
            appendLine("- [DUE TODAY] / [DUE TOMORROW]: Immediate attention required")
            appendLine("- [OVERDUE]: Address urgently — triage or complete")
            appendLine("- Complexity signals: Consider BREAK_DOWN_TASK")
            appendLine("- Low-scored + overdue: Candidate for DELEGATE_OR_DROP")
            appendLine("- Zombie (deferred 3x+): Address firmly with FLAG_ZOMBIE")
            appendLine()

            // Cross-referencing instruction
            appendLine("## CROSS-REFERENCE THE CONTEXT")
            appendLine("The user prompt contains multiple data sections. Connect them:")
            appendLine()
            appendLine("SEMANTIC MEMORY → CALENDAR:")
            appendLine("- Match keywords and entities from SEMANTIC MEMORY to upcoming event titles and descriptions.")
            appendLine("- If a memo mentions \"budget\" and a calendar event is \"Q2 Budget Review\", suggest a prep task.")
            appendLine("- If action items from memos relate to an event, propose completing them before that event.")
            appendLine()
            appendLine("SEMANTIC MEMORY → TASKS:")
            appendLine("- Pending action items from SEMANTIC MEMORY that don't already exist as tasks are candidates for ADD_TASK.")
            appendLine("- Keywords from memos can clarify vague handwritten tasks — use them to write better proposal descriptions.")
            appendLine()
            appendLine("WHAT'S COMING → SELECTED DAY:")
            appendLine("- Events in the next 1-3 days that require preparation should generate ADD_TASK proposals for the selected day.")
            appendLine("- Travel, presentations, deadlines, and meetings with external attendees are high-prep signals.")
            appendLine()
            appendLine("USER PROFILE → EVERYTHING:")
            appendLine("- Use priority ranking to weight which cross-references matter most (a work-first user cares more about work-related connections).")
            appendLine("- Use energy pattern to suggest WHEN to do prep work (morning person → prep early, night owl → prep in evening slot).")
            appendLine("- Use lifestyle flags to shape ADD_TASK proposals, without inventing times the user didn't give.")
            appendLine()

            // Reason quality
            appendLine("## REASON AND SUMMARY QUALITY")
            appendLine()
            appendLine("The \"summary\" field is the user's day briefing. Make it a sharp, specific, 1-2 sentence assessment:")
            appendLine("- Name the key tension or opportunity (e.g., back-to-back meetings, open afternoon, overdue pile)")
            appendLine("- Mention the best window if one exists (e.g., \"2-hour free slot after lunch\")")
            appendLine("- Tone: direct and helpful, like a trusted assistant")
            appendLine()
            appendLine("Each proposal \"reason\" must cite specific evidence:")
            appendLine("- SCORING signals (urgency, preference match, calendar context)")
            appendLine("- Capacity data (free slots, overload status)")
            appendLine("- Event relationships (prep for meeting, follow-up)")
            appendLine("Structure: WHY NOW (urgency/timing) + HOW IT FITS (capacity/preferences) + WHAT TO DO (action/consequence)")
            appendLine()
            appendLine("Good: \"Due tomorrow, HIGH urgency. You have a 2h free slot at 14:00 — ideal for focused work.\"")
            appendLine("Good: \"Deferred 4 times over 12 days. Break into 15-min steps or drop it — it's blocking your momentum.\"")
            appendLine("Good: \"Board Meeting at 15:00 discusses Q2 — prep the slides in this morning's free slot.\"")
            appendLine("Bad: \"This is important.\" / \"Consider doing this today.\"")
            appendLine()

            // Output format with realistic example
            appendLine("## OUTPUT FORMAT")
            appendLine("Return ONLY raw JSON (no markdown code blocks). Structure:")
            appendLine("""
{
  "summary": "Busy morning with 3 meetings, but a clear 14:00-16:30 window. 2 overdue items need triage.",
  "capacityStatus": "Light|Moderate|Busy|Overbooked|Severely Overbooked",
  "proposals": [
    {
      "action": "ADD_TASK|BREAK_DOWN_TASK|FLAG_ZOMBIE|DELEGATE_OR_DROP|BATCH_SIMILAR|CREATE_TIMEBLOCK|SCHEDULE_BUFFER",
      "title": "Short action title",
      "description": "What happens when the user taps this",
      "reason": "Specific evidence-based reason (WHY NOW + HOW IT FITS + WHAT TO DO)",
      "confidence": 0.0-1.0,
      "priority": 1-7,
      "estimatedMinutes": 30,
      "suggestedTimeBlock": { "startTime": "14:00", "endTime": "15:00", "title": "Deep work: Budget report", "durationMinutes": 60 },
      "taskId": "exact task ID from the list",
      "taskText": "task text for display",
      "suggestedSubtasks": ["subtask 1", "subtask 2"],
      "zombieInfo": { "migrationCount": 4, "originalDate": "2026-02-25", "daysSinceCreation": 12, "suggestion": "BREAK_DOWN", "suggestionReason": "Too large as a single task" },
      "alternativeActions": [{ "label": "Break it down", "action": "BREAK_DOWN_TASK" }, { "label": "Drop it", "action": "DELEGATE_OR_DROP" }]
    }
  ]
}""".trim())
            appendLine()
            appendLine("Duration guidelines: ADD_TASK 15-60min, BREAK_DOWN_TASK 10-15min, FLAG_ZOMBIE 5min, DELEGATE_OR_DROP 5min, BATCH_SIMILAR 30-60min.")
            appendLine("Include suggestedTimeBlock only when a free slot clearly fits. Omit optional fields rather than setting null.")
        }.trim()
    }

    private fun collapseWhitespace(text: String?): String {
        return text?.replace(multiWhitespaceRegex, " ")?.trim().orEmpty()
    }

    internal fun normalizeProposalText(text: String?): String {
        val collapsed = collapseWhitespace(text)
        if (collapsed.isBlank()) return ""
        return collapsed
            .lowercase()
            .replace(proposalNormalizationRegex, " ")
            .replace(multiWhitespaceRegex, " ")
            .trim()
    }

    private fun normalizedAddTaskText(proposal: AgenticProposal): String? {
        if (proposal.action != ProposalAction.ADD_TASK) return null
        val fallbackText = proposal.taskText?.takeIf { it.isNotBlank() } ?: proposal.title
        return normalizeProposalText(fallbackText).ifBlank { null }
    }

    private fun proposalDedupKey(proposal: AgenticProposal): String? {
        return when (proposal.action) {
            ProposalAction.ADD_TASK -> normalizedAddTaskText(proposal)?.let { "${proposal.action}:$it" }
            ProposalAction.CREATE_TIMEBLOCK, ProposalAction.SCHEDULE_BUFFER -> {
                val block = proposal.suggestedTimeBlock ?: return null
                "${proposal.action}:${block.startTime}-${block.endTime}:${normalizeProposalText(block.title)}"
            }
            ProposalAction.MOVE_TASK -> "${proposal.action}:${proposal.taskId}:${proposal.targetDate}"
            else -> "${proposal.action}:${proposal.taskId}"
        }
    }

    internal fun dedupeAgenticProposals(
        proposals: List<AgenticProposal>,
        existingTaskTexts: List<String> = emptyList()
    ): List<AgenticProposal> {
        val seenKeys = mutableSetOf<String>()
        val seenTaskTexts = existingTaskTexts
            .map(::normalizeProposalText)
            .filter { it.isNotBlank() }
            .toMutableSet()

        return proposals
            .sortedBy { it.priority }
            .mapNotNull { proposal ->
                val dedupKey = proposalDedupKey(proposal) ?: return@mapNotNull null
                if (proposal.action == ProposalAction.ADD_TASK) {
                    val normalizedText = normalizedAddTaskText(proposal) ?: return@mapNotNull null
                    if (normalizedText in seenTaskTexts) {
                        return@mapNotNull null
                    }
                    if (!seenKeys.add(dedupKey)) {
                        return@mapNotNull null
                    }
                    seenTaskTexts.add(normalizedText)
                } else if (!seenKeys.add(dedupKey)) {
                    return@mapNotNull null
                }
                proposal
            }
    }

    private fun sanitizeProposal(proposal: AgenticProposal): AgenticProposal? {
        val title = collapseWhitespace(proposal.title)
        val description = collapseWhitespace(proposal.description)
        val reason = collapseWhitespace(proposal.reason)

        if (title.isBlank() || description.isBlank() || reason.isBlank()) {
            Log.w(TAG, "Skipping malformed proposal with blank title/description/reason")
            return null
        }

        return proposal.copy(
            title = title,
            description = description,
            reason = reason,
            taskText = when (proposal.action) {
                ProposalAction.ADD_TASK -> collapseWhitespace(proposal.taskText)
                    .ifBlank { title }
                    .ifBlank { null }
                else -> collapseWhitespace(proposal.taskText).ifBlank { null }
            },
            targetDate = collapseWhitespace(proposal.targetDate).ifBlank { null },
            suggestedSubtasks = proposal.suggestedSubtasks
                ?.map(::collapseWhitespace)
                ?.filter { it.isNotBlank() }
                ?.distinctBy { it.lowercase() }
                ?.take(7)
        )
    }

    /**
     * Build user prompt with enhanced task display using scores.
     */
    private suspend fun buildUserPromptV3(context: AgenticContext, scoredContext: ScoredContext): String {
        val calendarSection = if (context.todayEvents.isNotEmpty()) {
            buildString {
                appendLine("Today's events (${context.todayEvents.size}):")
                var charBudget = 2000
                context.todayEvents.take(8).forEach { event ->
                    val zone = java.time.ZoneId.systemDefault()
                    val timeLabel = if (event.isAllDay) {
                        "All day"
                    } else {
                        val start = java.time.Instant.ofEpochMilli(event.startTime).atZone(zone)
                        val startStr = String.format("%02d:%02d", start.hour, start.minute)
                        val endTime = event.endTime
                        if (endTime != null && endTime > 0) {
                            val end = java.time.Instant.ofEpochMilli(endTime).atZone(zone)
                            "$startStr-${String.format("%02d:%02d", end.hour, end.minute)}"
                        } else {
                            startStr
                        }
                    }

                    // Build enriched event line: time + title + location + attendees
                    val titleLine = buildString {
                        append("- [$timeLabel] ${event.title}")
                        event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                            append(" @ ${loc.take(50)}")
                        }
                        event.attendees?.takeIf { it.isNotBlank() }?.let { att ->
                            append(" | w/ ${att.take(80)}")
                        }
                    }
                    charBudget -= titleLine.length + 1
                    appendLine(titleLine)

                    // Add description on next line if budget allows
                    if (charBudget > 100) {
                        event.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            val descLine = "  ${desc.take(150)}"
                            charBudget -= descLine.length + 1
                            appendLine(descLine)
                        }
                    }
                }
            }.trim()
        } else null

        val patternSection = context.patternInsights?.let { insights ->
            buildString {
                appendLine("## USER PATTERNS (last 7 days)")
                appendLine("Completion rate: ${(insights.weeklyCompletionRate * 100).toInt()}%")
                appendLine("Current streak: ${insights.currentStreak} days")
                insights.bestDay?.let { appendLine("Best day: $it") }
            }.trim()
        }

        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = null,
            date = context.planningDate,
            rangeStart = context.planningDate.minusDays(2),
            rangeEnd = context.planningDate.plusDays(5),
            includeBooxNotes = false,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO),
            calendarSection = calendarSection,
            patternSection = patternSection,
            referenceHour = context.currentHour,
            trimPolicy = TaskAiContextBuilderV1.ContextTrimPolicy.PLAN_MY_DAY
        )

        return buildString {
            val today = context.planningDate.format(dateFormatter)

            if (contextBlock.isNotBlank()) {
                appendLine(contextBlock)
                appendLine()
            }

            context.nearbyContextSummary?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }

            appendLine("## SELECTED PLANNING DATE: $today (${context.planningDate.dayOfWeek})")
            val timeOfDay = when (context.currentHour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
            appendLine("Time of day: $timeOfDay")
            appendLine()

            // Capacity analysis
            appendLine("## CAPACITY ANALYSIS")
            appendLine("Status: ${context.todayCapacity.getCapacityStatus()}")
            appendLine("Committed: ${context.todayCapacity.committedMinutes} min (${context.todayCapacity.capacityPercentage.toInt()}%)")
            appendLine("Free time: ${context.todayCapacity.freeMinutes} min")
            if (context.todayCapacity.freeSlots.isNotEmpty()) {
                appendLine("Available time slots:")
                context.todayCapacity.freeSlots.forEach { slot ->
                    appendLine("  - ${slot.startTime}-${slot.endTime} (${slot.durationMinutes} min, ${slot.label})")
                }
            }
            appendLine()

            // Today's tasks WITH PRE-COMPUTED SCORES
            val scoreById = scoredContext.scoredItems.associateBy { it.itemId }
            val tasksWithContent = context.todayTasks.filter { task ->
                hasTaskContent(task, context.taskOcrMap)
            }
            val cappedTasks = tasksWithContent
                .sortedByDescending { scoreById[it.id]?.finalScore ?: 0f }
                .take(25)

            if (cappedTasks.isNotEmpty()) {
                val countLabel = if (tasksWithContent.size > cappedTasks.size) {
                    "${cappedTasks.size} of ${tasksWithContent.size}"
                } else {
                    "${cappedTasks.size}"
                }
                appendLine("## TODAY'S TASKS WITH SCORES ($countLabel)")
                cappedTasks.forEach { task ->
                    val text = resolveTaskText(task, context.taskOcrMap) ?: "[unknown]"
                    val status = if (task.isCompleted) "[x]" else "[ ]"
                    val priority = if (task.priority.isNotBlank()) "(${task.priority})" else ""
                    val dueInfo = formatDueDate(task, context.planningDate)?.let { " [$it]" } ?: ""

                    // Get pre-computed score and signals
                    val scoredItem = scoreById[task.id]
                    val score = scoredItem?.finalScore?.let { String.format("%.2f", it) } ?: "N/A"
                    val scoreExplanation = scoredItem?.let { formatScoreExplanation(it) } ?: ""

                    appendLine("  - ID:${task.id} $status $priority$dueInfo [$score] $text")
                    if (scoreExplanation.isNotBlank()) {
                        appendLine("    SCORING: $scoreExplanation")
                    }
                }
                appendLine()
            }

            // Overdue tasks with scores
            if (context.overdueTasks.isNotEmpty()) {
                appendLine("## OVERDUE TASKS WITH SCORES (${context.overdueTasks.size})")
                context.overdueTasks.forEach { task ->
                    val text = resolveTaskText(task, context.taskOcrMap) ?: "[unknown]"
                    val daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.parse(task.dayDate, dateFormatter),
                        context.planningDate
                    ).toInt()
                    val dueInfo = formatDueDate(task, context.planningDate)?.let { " [$it]" } ?: ""

                    val scoredItem = scoreById[task.id]
                    val score = scoredItem?.finalScore?.let { String.format("%.2f", it) } ?: "N/A"
                    val scoreExplanation = scoredItem?.let { formatScoreExplanation(it) } ?: ""

                    appendLine("  - ID:${task.id} [${daysOverdue}d overdue]$dueInfo [$score] $text")
                    if (scoreExplanation.isNotBlank()) {
                        appendLine("    SCORING: $scoreExplanation")
                    }
                }
                appendLine()
            }

            // Zombie tasks with scores
            if (context.zombieTasks.isNotEmpty()) {
                appendLine("## ZOMBIE TASKS WITH SCORES (deferred 3+ times)")
                context.zombieTasks.forEach { task ->
                    val text = resolveTaskText(task, context.taskOcrMap) ?: "[unknown]"
                    val originalAge = task.originalDate?.let { orig ->
                        try {
                            java.time.temporal.ChronoUnit.DAYS.between(
                                LocalDate.parse(orig, dateFormatter),
                                context.planningDate
                            ).toInt()
                        } catch (e: Exception) { null }
                    } ?: 0
                    val dueInfo = formatDueDate(task, context.planningDate)?.let { " [$it]" } ?: ""

                    val scoredItem = scoreById[task.id]
                    val score = scoredItem?.finalScore?.let { String.format("%.2f", it) } ?: "N/A"
                    val scoreExplanation = scoredItem?.let { formatScoreExplanation(it) } ?: ""

                    appendLine("  - ID:${task.id} [moved ${task.migrationCount}x, ${originalAge}d old]$dueInfo [$score] $text")
                    if (scoreExplanation.isNotBlank()) {
                        appendLine("    SCORING: $scoreExplanation")
                    }
                }
                appendLine()
            }

            // Task-event relationships (scheduling hints)
            if (context.taskEventRelations.isNotEmpty()) {
                appendLine("## TASK-EVENT RELATIONSHIPS")
                appendLine("These tasks appear related to today's events - consider them in today's plan:")
                context.taskEventRelations.forEach { rel ->
                    val relType = when (rel.relationshipType) {
                        TaskEventMatcher.REL_PREP -> "PREP for"
                        TaskEventMatcher.REL_FOLLOWUP -> "FOLLOW-UP to"
                        else -> "related to"
                    }
                    appendLine("  - \"${rel.taskText}\" (ID:${rel.taskId}) $relType \"${rel.eventTitle}\" [affinity: ${String.format("%.2f", rel.affinity)}]")
                }
                appendLine()
                appendLine("Hint: Prep tasks should happen before their events; follow-up tasks after.")
                appendLine()
            }

            // Tomorrow context for defer/urgency reasoning
            context.tomorrowCapacity?.let { tomorrowCap ->
                val tomorrow = context.planningDate.plusDays(1).format(dateFormatter)
                appendLine("## TOMORROW ($tomorrow)")
                appendLine("Capacity: ${tomorrowCap.getCapacityStatus()} (${tomorrowCap.capacityPercentage.toInt()}%)")
                appendLine("Free time: ${tomorrowCap.freeMinutes} min")
                appendLine()
            }

            // What's Coming: upcoming days with event details for prep-task suggestions
            if (context.upcomingDayEvents.isNotEmpty()) {
                appendLine("## WHAT'S COMING (next few days)")
                var sectionBudget = 2500
                context.upcomingDayEvents.entries
                    .sortedBy { it.key }
                    .take(5)
                    .forEach { (dateStr, events) ->
                        if (sectionBudget <= 0) return@forEach
                        val dayDate = try {
                            LocalDate.parse(dateStr, dateFormatter)
                        } catch (e: Exception) { return@forEach }
                        val dayName = dayDate.dayOfWeek.name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                        val headerLine = "$dayName $dateStr (${events.size} events):"
                        sectionBudget -= headerLine.length + 1
                        appendLine(headerLine)

                        events.take(4).forEach eventLoop@{ event ->
                            if (sectionBudget <= 0) return@eventLoop
                            val zone = java.time.ZoneId.systemDefault()
                            val timeLabel = if (event.isAllDay) "All day" else {
                                val start = java.time.Instant.ofEpochMilli(event.startTime).atZone(zone)
                                String.format("%02d:%02d", start.hour, start.minute)
                            }
                            val eventLine = buildString {
                                append("  - [$timeLabel] ${event.title}")
                                event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                                    append(" @ ${loc.take(40)}")
                                }
                                event.attendees?.takeIf { it.isNotBlank() }?.let { att ->
                                    append(" | w/ ${att.take(60)}")
                                }
                            }
                            sectionBudget -= eventLine.length + 1
                            appendLine(eventLine)

                            // Add brief description for upcoming events
                            if (sectionBudget > 50) {
                                event.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    val descLine = "    ${desc.take(100)}"
                                    sectionBudget -= descLine.length + 1
                                    appendLine(descLine)
                                }
                            }
                        }
                    }
                appendLine()
            }

            appendLine("## INSTRUCTIONS")
            appendLine("Analyze this context and generate actionable proposals.")
            appendLine("Focus on high-impact improvements the user can execute immediately.")
            appendLine("Use the pre-computed scores as strong guidance for task prioritization.")
            appendLine("Optimize only the selected planning date shown above.")
            appendLine("Use surrounding days only when they directly support work on the selected planning date.")
            appendLine("Do not repeat the same task idea twice, even with different wording.")
            if (context.isOverloaded) {
                appendLine("NOTE: User appears overloaded. Prioritize load reduction.")
            }
            if (context.zombieTasks.isNotEmpty()) {
                appendLine("NOTE: Address zombie tasks with FLAG_ZOMBIE proposals.")
            }
        }
    }
}
