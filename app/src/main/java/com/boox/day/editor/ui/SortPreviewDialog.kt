package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.TaskAiService
import com.boox.day.data.ai.ScoredContext
import com.boox.day.data.ai.ScoredItem
import com.boox.day.data.ai.ScorerIds
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable

/**
 * Represents a task in the AI-sorted order with its target placement.
 *
 * @param sortedTask The AI's sorting result for this task (priority, reasoning)
 * @param targetPage The page index where this task will be placed (0-indexed)
 * @param targetRow The row index where this task will be placed (0-indexed)
 * @param targetTaskId The Task entity ID of the target slot - CRITICAL for reorder execution.
 *                     This is the database ID of the Task entity that will receive this content,
 *                     NOT the source task's ID. Without this, the reorder writes to wrong slots.
 */
data class PlacedSortedTask(
    val sortedTask: TaskAiService.SortedTask,
    val targetPage: Int,
    val targetRow: Int,
    val targetTaskId: String
)

/**
 * Sort Preview Dialog - Shows AI-suggested task ordering before applying.
 *
 * Displays:
 * - Rich progress feedback during AI processing stages
 * - Tasks grouped by page with suggested priority order
 * - AI reasoning for the prioritization
 * - Apply/Cancel actions
 *
 * When applied, tasks are consolidated to Page 1 in priority order.
 * Handwritten strokes are migrated with proper Y-coordinate transformation.
 * E-ink optimized: black/white, large touch targets, no animations.
 */
@Composable
fun SortPreviewDialog(
    sortResult: TaskAiService.SortResult?,
    placementPlan: List<PlacedSortedTask>?,
    scoredContext: ScoredContext?,
    priorityAssignments: Map<String, String> = emptyMap(),
    processingState: SortProcessingState,
    onApplySort: (TaskAiService.SortResult) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            if (processingState !is SortProcessingState.Processing) {
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = processingState !is SortProcessingState.Processing,
            dismissOnClickOutside = processingState !is SortProcessingState.Processing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Prioritization",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )

                    if (processingState !is SortProcessingState.Processing) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EInkTheme.Colors.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                when (processingState) {
                    is SortProcessingState.Processing -> {
                        // Processing state - show rich AI progress feedback
                        val stageMessage = processingState.stageMessage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingLarge)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - Rotates every 5 seconds
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomPrioritizationMessage() },
                                intervalMs = 5000L
                            )

                            // Technical Stage (Secondary)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Text(
                                    text = stageMessage,
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.gray,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                            // Cancel button during processing
                            EinkSecondaryButton(
                                text = "Cancel",
                                onClick = onDismiss
                            )
                        }
                    }

                    is SortProcessingState.Error -> {
                        // Error state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.paddingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                        ) {
                            Text(
                                text = "Could not sort tasks",
                                fontSize = EInkTheme.Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(EInkTheme.Global.spacing))

                            EinkSecondaryButton(
                                text = "Close",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    is SortProcessingState.Ready -> {
                        if (sortResult == null || sortResult.sortedTasks.isEmpty() || placementPlan == null) {
                            // No tasks to sort
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = EInkTheme.Global.paddingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No tasks to sort",
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.gray
                                )

                                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                                EinkSecondaryButton(
                                    text = "Close",
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Show sorted tasks
                            SortPreviewContent(
                                sortResult = sortResult,
                                placementPlan = placementPlan,
                                scoredContext = scoredContext,
                                priorityAssignments = priorityAssignments,
                                onApply = { onApplySort(sortResult) },
                                onCancel = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get a helpful hint based on the current processing stage.
 */
private fun getStageHint(stageMessage: String): String {
    return when {
        stageMessage.contains("Reading", ignoreCase = true) ->
            "Gathering your tasks and their current priorities..."
        stageMessage.contains("handwriting", ignoreCase = true) || stageMessage.contains("OCR", ignoreCase = true) ->
            "Converting handwritten tasks to text..."
        stageMessage.contains("Analyzing", ignoreCase = true) || stageMessage.contains("prioritizing", ignoreCase = true) ->
            "Evaluating urgency, deadlines, and dependencies..."
        stageMessage.contains("Organizing", ignoreCase = true) ->
            "Building the optimal task order..."
        else ->
            "AI is analyzing by urgency and importance"
    }
}

@Composable
private fun SortPreviewContent(
    sortResult: TaskAiService.SortResult,
    placementPlan: List<PlacedSortedTask>,
    scoredContext: ScoredContext?,
    priorityAssignments: Map<String, String>,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val totalTasks = sortResult.sortedTasks.size
    val scrollState = rememberScrollState()

    // Sort plan by suggested priority (1, 2, 3...)
    val sortedPlan = remember(placementPlan) {
        placementPlan.sortedBy { it.sortedTask.suggestedPriority }
    }

    // Build lookup map from taskId to ScoredItem for rich signals
    val scoredItemMap = remember(scoredContext) {
        scoredContext?.scoredItems?.associateBy { it.itemId } ?: emptyMap()
    }

    Column {
        // Scrollable content area - takes remaining space, buttons stay visible
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Handwriting Summary (if any)
            sortResult.ocrStats?.let { stats ->
                if (stats.detected > 0) {
                    HandwritingSummary(stats)
                    Spacer(Modifier.height(EInkTheme.Global.spacing))
                }
            }

            // Intelligence Summary Card - replaces raw reasoning dump
            scoredContext?.let { ctx ->
                IntelligenceSummaryCard(scoredContext = ctx, totalTasks = totalTasks)
                Spacer(Modifier.height(EInkTheme.Global.spacing))
            }

            // Priority Legend - explains the ranking system
            PriorityLegend()
            Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

            // Suggested order label with task count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Proposed New Order:",
                    fontSize = 17.sp, // Larger for e-ink
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = "$totalTasks task${if (totalTasks != 1) "s" else ""}",
                    fontSize = 15.sp, // Larger for e-ink
                    color = EInkTheme.Colors.darkGray
                )
            }

            Spacer(Modifier.height(EInkTheme.Global.spacing))

            // Tasks list - regular Column since parent is scrollable
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp) // Tighter spacing
            ) {
                sortedPlan.forEach { placedTask ->
                    val scoredItem = scoredItemMap[placedTask.sortedTask.taskId]
                    val aiPriority = priorityAssignments[placedTask.sortedTask.taskId]
                    SortedTaskItem(
                        placedTask = placedTask,
                        scoredItem = scoredItem,
                        aiAssignedPriority = aiPriority
                    )
                }
            }
        }

        Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

        // Action buttons - ALWAYS VISIBLE (outside scrollable area)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EinkSecondaryButton(
                text = "Cancel",
                onClick = onCancel
            )

            Spacer(Modifier.width(EInkTheme.Button.spacing))

            EinkPrimaryButton(
                text = "Apply Order",
                onClick = onApply
            )
        }
    }
}

/**
 * Intelligence Summary Card - Shows a user-friendly summary of the AI analysis.
 * Replaces the raw "## Intelligence Engine Pre-Analysis" text dump with
 * meaningful insights the user can understand.
 */
@Composable
private fun IntelligenceSummaryCard(
    scoredContext: ScoredContext,
    totalTasks: Int
) {
    val metrics = scoredContext.aggregateMetrics
    val distribution = metrics.scoreDistribution

    // Extract top signals for display (skip generic ones)
    val topSignals = metrics.topSignals
        .filter { !it.signal.contains("No specific") }
        .take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
            .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "Intelligence Analysis",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )

        // Summary stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Priority distribution
            Column {
                Text(
                    text = "Priority Distribution",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (distribution.high > 0) {
                        PriorityBadge(
                            label = "High",
                            count = distribution.high,
                            isHighPriority = true
                        )
                    }
                    if (distribution.medium > 0) {
                        PriorityBadge(
                            label = "Med",
                            count = distribution.medium,
                            isHighPriority = false
                        )
                    }
                    if (distribution.low > 0) {
                        PriorityBadge(
                            label = "Low",
                            count = distribution.low,
                            isHighPriority = false
                        )
                    }
                }
            }
        }

        // Key signals detected
        if (topSignals.isNotEmpty()) {
            Divider(
                color = EInkTheme.Colors.gray,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Key Factors Detected:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                topSignals.forEach { signalCount ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            color = EInkTheme.Colors.primary
                        )
                        Text(
                            text = formatSignalForDisplay(signalCount.signal),
                            fontSize = 14.sp,
                            color = EInkTheme.Colors.darkGray
                        )
                        if (signalCount.count > 1) {
                            Text(
                                text = "(${signalCount.count} tasks)",
                                fontSize = 12.sp,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format a signal string for user-friendly display.
 */
private fun formatSignalForDisplay(signal: String): String {
    return when {
        signal.contains("OVERDUE", ignoreCase = true) -> signal
        signal.contains("Due TODAY", ignoreCase = true) -> "Due today - highest urgency"
        signal.contains("Due tomorrow", ignoreCase = true) -> "Due tomorrow"
        signal.contains("Priority A", ignoreCase = true) -> "User-marked high priority (A)"
        signal.contains("Priority B", ignoreCase = true) -> "User-marked medium priority (B)"
        signal.contains("CRUNCH MODE", ignoreCase = true) -> "Crunch mode: work tasks boosted"
        signal.contains("VACATION MODE", ignoreCase = true) -> "Vacation mode: work deprioritized"
        signal.contains("HOLIDAY MODE", ignoreCase = true) -> "Holiday mode: family tasks boosted"
        signal.contains("Zombie", ignoreCase = true) -> "Zombie task (repeatedly postponed)"
        signal.contains("Prep for", ignoreCase = true) -> signal.replace("Prep for", "Prepare for")
        signal.contains("Follow-up", ignoreCase = true) -> signal
        signal.contains("urgent", ignoreCase = true) -> "Contains urgency keywords"
        else -> signal.take(50)
    }
}

@Composable
private fun PriorityBadge(
    label: String,
    count: Int,
    isHighPriority: Boolean
) {
    Row(
        modifier = Modifier
            .background(
                if (isHighPriority) EInkTheme.Colors.primary else EInkTheme.Colors.offWhite,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (isHighPriority) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$count",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighPriority) EInkTheme.Colors.white else EInkTheme.Colors.darkGray
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isHighPriority) EInkTheme.Colors.white else EInkTheme.Colors.gray
        )
    }
}

/**
 * Priority Legend - Explains how the ranking system works.
 */
@Composable
private fun PriorityLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(6.dp))
            .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "How Ranking Works",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.darkGray
        )
        Text(
            text = "Tasks are ranked #1, #2, #3... by urgency. Lower number = do first. " +
                   "Factors: due dates, your A/B/C priorities, calendar events, keywords, and patterns.",
            fontSize = 12.sp,
            color = EInkTheme.Colors.gray,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun SortedTaskItem(
    placedTask: PlacedSortedTask,
    scoredItem: ScoredItem? = null,
    aiAssignedPriority: String? = null
) {
    val task = placedTask.sortedTask
    val isChild = task.isChild

    // Check if task is moving
    val isMoving = task.originalPage != placedTask.targetPage || task.originalIndex != placedTask.targetRow

    // Get the primary signal from scored context, or fall back to task.reason
    val primarySignal = scoredItem?.primarySignal?.takeIf {
        it.isNotBlank() && !it.contains("No specific")
    } ?: task.reason

    // AI-assigned priority (A/B/C) - this is what will be applied
    // Falls back to user's original priority if AI didn't assign one
    val displayPriority = aiAssignedPriority ?: task.userPriority.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(if (isChild) {
                Modifier
                    .padding(start = 24.dp)
                    .drawBehind {
                        val strokeWidth = 1.5.dp.toPx()
                        val color = Color.Gray
                        val xPos = -12.dp.toPx()
                        val centerY = size.height / 2
                        drawLine(color, Offset(xPos, -10.dp.toPx()), Offset(xPos, centerY), strokeWidth)
                        drawLine(color, Offset(xPos, centerY), Offset(xPos + 8.dp.toPx(), centerY), strokeWidth)
                    }
            } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number badge
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = task.suggestedPriority.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.white
            )
        }

        Spacer(Modifier.width(8.dp))

        // Task text and signal - takes available space
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Task text row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (task.isHandwritten) {
                    Text(
                        text = "\u270D",
                        fontSize = 15.sp,
                        color = EInkTheme.Colors.gray
                    )
                }
                Text(
                    text = task.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Signal row - compact
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (primarySignal.isNotBlank()) {
                    Text(
                        text = formatSignalForDisplay(primarySignal),
                        fontSize = 13.sp,
                        color = EInkTheme.Colors.darkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isMoving) {
                    Text(
                        text = "→#${placedTask.targetRow + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }

        // AI-assigned priority badge (A/B/C) - ALWAYS shown
        // This is the priority the AI decided, shown for ALL tasks
        displayPriority?.let { priority ->
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        when (priority) {
                            "A" -> EInkTheme.Colors.primary
                            else -> EInkTheme.Colors.offWhite
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.5.dp,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = priority,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (priority) {
                        "A" -> EInkTheme.Colors.white
                        else -> EInkTheme.Colors.primary
                    }
                )
            }
        }
    }
}

/**
 * Processing state for the sort dialog.
 * Includes stage message for rich progress feedback.
 */
sealed class SortProcessingState {
    /**
     * Processing with detailed stage message for user feedback.
     * @param stageMessage Human-readable message about current stage
     */
    data class Processing(val stageMessage: String = "Reading your task list...") : SortProcessingState()
    data object Ready : SortProcessingState()
    data class Error(val message: String) : SortProcessingState()
}

@Composable
private fun HandwritingSummary(stats: TaskAiService.OcrStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(6.dp))
            .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u270D", // ✍ Writing hand
                fontSize = 16.sp,
                color = EInkTheme.Colors.darkGray,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Handwriting:",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.darkGray
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${stats.detected} found",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = " • ",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
            Text(
                text = "${stats.recognized} read",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.primary
            )
            
            if (stats.skipped > 0) {
                Text(
                    text = " • ",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Text(
                    text = "${stats.skipped} skipped",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray, // Can use warning color if available, but stick to E-Ink theme
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
