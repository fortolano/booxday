package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.TaskAiService
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.data.db.Task
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable
import kotlinx.coroutines.launch

/**
 * Daily Summary Dialog - Enhanced end-of-day review showing:
 * - Completed tasks with priority labels and handwritten indicators
 * - Incomplete tasks with migration options
 * - AI-generated productivity insights (daily-focused)
 * - Completion statistics with 100% celebration
 * - OCR support for handwritten tasks
 *
 * E-ink optimized: black/white, large touch targets (48dp min), large fonts.
 */
@Composable
fun DailySummaryDialog(
    summary: TaskAiService.DailySummary?,
    processingState: DailySummaryProcessingState,
    onMigrateTask: suspend (Task) -> Boolean,
    onBreakDownTask: ((Task) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            if (processingState !is DailySummaryProcessingState.Processing) {
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = processingState !is DailySummaryProcessingState.Processing,
            dismissOnClickOutside = processingState !is DailySummaryProcessingState.Processing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header with proper touch target for close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Summary",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )

                    if (processingState !is DailySummaryProcessingState.Processing) {
                        // Close button with proper 48dp touch target
                        Box(
                            modifier = Modifier
                                .size(EInkTheme.Global.touchTarget)
                                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .noRippleClickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(EInkTheme.Global.iconSize)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                when (processingState) {
                    is DailySummaryProcessingState.Processing -> {
                        // Processing state with large readable text
                        val stageMessage = processingState.stageMessage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.paddingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingLarge)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomSummarizationMessage() }
                            )

                            // Technical Stage (Secondary)
                            Text(
                                text = stageMessage,
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.darkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is DailySummaryProcessingState.Error -> {
                        // Error state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.paddingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                        ) {
                            Text(
                                text = "Could not generate summary",
                                fontSize = EInkTheme.Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                            // Close button with proper touch target (E-Ink: no ripple)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(EInkTheme.Button.height)
                                    .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .noRippleClickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Close",
                                    color = EInkTheme.Button.textColor,
                                    fontSize = EInkTheme.Typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    is DailySummaryProcessingState.Ready -> {
                        if (summary == null) {
                            // No summary available
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = EInkTheme.Global.paddingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No tasks for this day",
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.darkGray
                                )

                                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(EInkTheme.Button.height)
                                        .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .noRippleClickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Close",
                                        color = EInkTheme.Button.textColor,
                                        fontSize = EInkTheme.Typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            // Show enhanced summary content
                            DailySummaryContent(
                                summary = summary,
                                onMigrateTask = onMigrateTask,
                                onBreakDownTask = onBreakDownTask,
                                onDone = onDismiss
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
        stageMessage.contains("Gathering", ignoreCase = true) ->
            "Collecting tasks and their completion status..."
        stageMessage.contains("Detecting", ignoreCase = true) ->
            "Looking for handwritten content..."
        stageMessage.contains("Recognizing", ignoreCase = true) || stageMessage.contains("handwritten", ignoreCase = true) ->
            "Converting handwriting to text..."
        stageMessage.contains("Analyzing", ignoreCase = true) ->
            "Reviewing your task completion..."
        stageMessage.contains("Generating", ignoreCase = true) || stageMessage.contains("insights", ignoreCase = true) ->
            "AI is crafting personalized feedback..."
        else ->
            "Preparing your daily review..."
    }
}

@Composable
private fun DailySummaryContent(
    summary: TaskAiService.DailySummary,
    onMigrateTask: suspend (Task) -> Boolean,
    onBreakDownTask: ((Task) -> Unit)?,
    onDone: () -> Unit
) {
    // Track which tasks have been migrated
    var migratedTaskIds by remember { mutableStateOf(setOf<String>()) }

    // Track expanded tasks (for full text view)
    var expandedTaskIds by remember { mutableStateOf(setOf<String>()) }

    val coroutineScope = rememberCoroutineScope()

    // Scroll state
    val scrollState = rememberLazyListState()
    val showScrollIndicator by remember {
        derivedStateOf {
            val lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = scrollState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index < totalItems - 1
        }
    }
    val contentMaxHeight = EInkTheme.PopupOverlay.maxHeight -
        (EInkTheme.ModernDialog.padding * 2) -
        EInkTheme.Button.height -
        (EInkTheme.Global.spacingLarge * 2) -
        EInkTheme.Global.touchTarget

    Column(modifier = Modifier.fillMaxWidth()) {
        // Bound content height to the popup overlay.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = contentMaxHeight)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (showScrollIndicator) 24.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                // Enhanced stats bar with celebration
                item {
                    SummaryStatsBar(summary)
                }

                // Completed section
                if (summary.completedTasks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacing))
                        SectionHeader(
                            title = "Completed",
                            count = summary.completedTasks.size,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    items(summary.completedTasks) { task ->
                        CompletedTaskItem(
                            task = task,
                            ocrText = summary.handwrittenOcrMap[task.id],
                            isExpanded = expandedTaskIds.contains(task.id),
                            onToggleExpand = {
                                expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                    expandedTaskIds.minus(task.id)
                                } else {
                                    expandedTaskIds.plus(task.id)
                                }
                            }
                        )
                    }
                }

                // Incomplete section with enhanced migration options
                if (summary.incompleteTasks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacing))
                        SectionHeader(
                            title = "Still Open",
                            count = summary.incompleteTasks.size,
                            color = EInkTheme.Colors.darkGray
                        )
                    }

                    items(summary.incompleteTasks) { task ->
                        IncompleteTaskItem(
                            task = task,
                            ocrText = summary.handwrittenOcrMap[task.id],
                            isMigrated = migratedTaskIds.contains(task.id),
                            isExpanded = expandedTaskIds.contains(task.id),
                            showBreakDown = onBreakDownTask != null,
                            onMigrate = {
                                coroutineScope.launch {
                                    val moved = onMigrateTask(task)
                                    if (moved) {
                                        migratedTaskIds = migratedTaskIds.plus(task.id)
                                    }
                                }
                            },
                            onBreakDown = { onBreakDownTask?.invoke(task) },
                            onToggleExpand = {
                                expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                    expandedTaskIds.minus(task.id)
                                } else {
                                    expandedTaskIds.plus(task.id)
                                }
                            }
                        )
                    }
                }

                // AI Insights section
                if (summary.aiInsights.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacing))
                        Text(
                            text = "Insights",
                            fontSize = EInkTheme.Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    items(summary.aiInsights) { insight ->
                        InsightItem(insight)
                    }
                }
            }

            // Scroll indicator - E-Ink optimized
            if (showScrollIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(EInkTheme.Colors.background)
                        .drawBehind {
                            drawLine(
                                color = EInkTheme.Colors.lightGray,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "more",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }

        // Done button with proper touch target (E-Ink: no ripple)
        Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.Button.primaryBorderWidth, EInkTheme.Button.primaryBorderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .noRippleClickable { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Done",
                color = EInkTheme.Button.primaryTextColor,
                fontSize = EInkTheme.Typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SummaryStatsBar(summary: TaskAiService.DailySummary) {
    val completedCount = summary.completedTasks.size
    val totalCount = completedCount + summary.incompleteTasks.size
    val percentage = if (totalCount > 0) (completedCount * 100) / totalCount else 0
    val isPerfect = percentage == 100 && totalCount > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.padding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = summary.date,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                Text(
                    text = "$completedCount of $totalCount completed",
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                // Celebration message for 100%
                if (isPerfect) {
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = "Perfect day!",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            // Completion percentage badge - large and readable
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        if (isPerfect || percentage >= 80) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPerfect) {
                    // Star icon for perfect score
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Perfect score",
                        tint = EInkTheme.Colors.white,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        text = "$percentage%",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.white
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Text(
            text = title,
            fontSize = EInkTheme.Typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Box(
            modifier = Modifier
                .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
    }
}

@Composable
private fun CompletedTaskItem(
    task: Task,
    ocrText: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val displayText = ocrText ?: task.typedText ?: "[No text]"
    val isHandwritten = ocrText != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onToggleExpand() }
            .padding(EInkTheme.Global.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        // Checkmark icon - E-Ink friendly vector
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Completed",
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(EInkTheme.Global.iconSize)
        )

        // Priority badge (if set) - large enough to read
        if (task.priority.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = task.priority,
                    fontSize = EInkTheme.Typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
        }

        // Handwritten indicator
        if (isHandwritten) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Handwritten",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(18.dp)
            )
        }

        // Task text - readable size
        Text(
            text = displayText,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IncompleteTaskItem(
    task: Task,
    ocrText: String?,
    isMigrated: Boolean,
    isExpanded: Boolean,
    showBreakDown: Boolean,
    onMigrate: () -> Unit,
    onBreakDown: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val displayText = ocrText ?: task.typedText ?: "[No text]"
    val isHandwritten = ocrText != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.padding)
    ) {
        // Task row (E-Ink: no ripple)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            // Empty circle icon - incomplete task indicator
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Incomplete",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(20.dp)
            )

            // Priority badge (if set)
            if (task.priority.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .border(EInkTheme.Divider.thickness, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = task.priority,
                        fontSize = EInkTheme.Typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            // Handwritten indicator
            if (isHandwritten) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Handwritten",
                    tint = EInkTheme.Colors.darkGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Task text
            Text(
                text = displayText,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Action buttons row with proper spacing
        Spacer(Modifier.height(EInkTheme.Global.spacing))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMigrated) {
                Text(
                    text = "Moved to tomorrow",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
            } else {
                // Tomorrow button - proper touch target (E-Ink: icon)
                EInkActionButton(
                    text = "Tomorrow",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = onMigrate
                )

                // Break Down button (if callback provided)
                if (showBreakDown) {
                    Spacer(Modifier.width(EInkTheme.Global.spacing))
                    EInkActionButton(
                        text = "Break Down",
                        icon = null,
                        onClick = onBreakDown
                    )
                }
            }
        }
    }
}

/**
 * E-Ink optimized action button with optional icon.
 */
@Composable
private fun EInkActionButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(EInkTheme.Global.touchTarget)
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.Button.horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                )
            }
            Text(
                text = text,
                fontSize = EInkTheme.Typography.labelLarge,
                color = EInkTheme.Colors.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InsightItem(insight: TaskAiService.DailySummaryInsight) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.padding)
    ) {
        Text(
            text = insight.text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            lineHeight = EInkTheme.Typography.bodyLarge
        )
    }
}

/**
 * Processing state for the daily summary dialog.
 * Includes stage message for rich progress feedback (matching SortProcessingState pattern).
 */
sealed class DailySummaryProcessingState {
    /**
     * Processing with detailed stage message for user feedback.
     * @param stageMessage Human-readable message about current stage
     */
    data class Processing(val stageMessage: String = "Gathering your tasks...") : DailySummaryProcessingState()
    data object Ready : DailySummaryProcessingState()
    data class Error(val message: String) : DailySummaryProcessingState()
}
