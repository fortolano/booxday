package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.TaskAiService
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable

/**
 * Task Breakdown Dialog - Expand complex tasks into subtasks.
 *
 * Flow:
 * 1. User selects a task to break down (typed OR handwritten with OCR)
 * 2. AI generates 3-7 actionable subtasks
 * 3. User can toggle subtasks on/off before applying
 * 4. Apply places parent + subtasks in a contiguous group
 *
 * Features:
 * - Supports both typed and handwritten tasks (OCR'd)
 * - Shows ✍ indicator for handwritten tasks
 * - Grouped placement: parent task stays as header, subtasks below with indentation
 * - Tap subtask to toggle include/exclude
 *
 * E-ink optimized: black/white, large touch targets, no animations, no Material icons.
 */
@Composable
fun TaskBreakdownDialog(
    availableTasks: List<TaskAiService.TaskForBreakdown>,
    breakdownResult: TaskAiService.BreakdownResult?,
    processingState: TaskBreakdownProcessingState,
    onSelectTask: (TaskAiService.TaskForBreakdown, convertHandwrittenToText: Boolean) -> Unit,
    onApplyBreakdown: (TaskAiService.BreakdownResult) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .heightIn(max = EInkTheme.PopupOverlay.maxHeight)  // Oct 2026: was 620 dp
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header with close button (always visible - allows canceling at any point)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Break Down Task",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )

                    // E-ink friendly close button
                    // Always visible - allows user to cancel at any point including during processing
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

                Spacer(Modifier.height(16.dp))

                when (processingState) {
                    is TaskBreakdownProcessingState.SelectTask -> {
                        TaskSelectionContent(
                            modifier = Modifier.weight(1f, fill = false),
                            tasks = availableTasks,
                            onSelectTask = { task, convertHandwritten ->
                                onSelectTask(task, convertHandwritten)
                            },
                            onCancel = onDismiss
                        )
                    }

                    is TaskBreakdownProcessingState.ProcessingOcr -> {
                        // Phase 1: Scanning for handwritten tasks
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomOcrMessage() }
                            )

                            // Technical Context (Secondary)
                            Text(
                                text = "Scanning for handwritten tasks...",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is TaskBreakdownProcessingState.ProcessingBreakdown -> {
                        // Phase 2: Breaking down the selected task
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomBreakdownMessage() }
                            )

                            // Technical Context (Secondary)
                            Text(
                                text = "AI is creating actionable subtasks...",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is TaskBreakdownProcessingState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Could not break down task",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(EInkTheme.Button.height)
                                    .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .noRippleClickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Close", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                            }
                        }
                    }

                    is TaskBreakdownProcessingState.Ready -> {
                        if (breakdownResult == null || breakdownResult.subtasks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Could not generate subtasks",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.gray
                                )

                                Spacer(Modifier.height(20.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(EInkTheme.Button.height)
                                        .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .noRippleClickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Close", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                                }
                            }
                        } else {
                            BreakdownPreviewContent(
                                modifier = Modifier.weight(1f, fill = false),
                                result = breakdownResult,
                                onApply = onApplyBreakdown,
                                onCancel = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSelectionContent(
    modifier: Modifier = Modifier,
    tasks: List<TaskAiService.TaskForBreakdown>,
    onSelectTask: (TaskAiService.TaskForBreakdown, convertHandwritten: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // Check if there are any handwritten tasks
    val hasHandwrittenTasks = tasks.any { it.isHandwritten }
    val handwrittenCount = tasks.count { it.isHandwritten }
    var convertHandwritten by remember { mutableStateOf(true) }  // Default to checked

    // Scroll state
    val scrollState = rememberLazyListState()
    val showScrollIndicator by remember {
        derivedStateOf {
            val lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = scrollState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index < totalItems - 1
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header with instruction
        Text(
            text = "Select a task to break down:",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )

        // Option toggle for handwritten conversion - appears inline below header
        if (hasHandwrittenTasks && tasks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { convertHandwritten = !convertHandwritten }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Convert $handwrittenCount handwritten → typed text",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )

                // Toggle switch style (visually different from checkbox)
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(24.dp)
                        .background(
                            if (convertHandwritten) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (convertHandwritten) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = if (convertHandwritten) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(20.dp)
                            .background(EInkTheme.Colors.white, RoundedCornerShape(10.dp))
                            .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(10.dp))
                    )
                }
            }

            // Thin separator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EInkTheme.Colors.lightGray)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)  // Oct 2026: keeps its height where it fits, shrinks so the buttons stay on a small screen
                    .height(360.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tasks with content to break down",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
        } else {
            // Task list with clear visual treatment
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)  // Oct 2026: keeps its height where it fits, shrinks so the buttons stay on a small screen
                    .height(360.dp) // Fixed height
            ) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (showScrollIndicator) 24.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(tasks) { _, taskForBreakdown ->
                        SelectableTaskItem(
                            taskForBreakdown = taskForBreakdown,
                            onSelect = { onSelectTask(taskForBreakdown, convertHandwritten) }
                        )
                    }
                }

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
        }

        Spacer(Modifier.height(16.dp))

        // Cancel button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .noRippleClickable { onCancel() },
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
        }
    }
}

@Composable
private fun SelectableTaskItem(
    taskForBreakdown: TaskAiService.TaskForBreakdown,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onSelect() }
            .padding(16.dp)  // Increased padding for better touch
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Handwritten indicator
            if (taskForBreakdown.isHandwritten) {
                Box(
                    modifier = Modifier
                        .background(EInkTheme.Colors.lightGray, RoundedCornerShape(4.dp))
                        .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✍",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            // Task text
            Text(
                text = taskForBreakdown.displayText,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BreakdownPreviewContent(
    modifier: Modifier = Modifier,
    result: TaskAiService.BreakdownResult,
    onApply: (TaskAiService.BreakdownResult) -> Unit,
    onCancel: () -> Unit
) {
    // Track which subtasks are included
    var includedSubtasks by remember { mutableStateOf(result.subtasks.toSet()) }

    // Scroll state
    val scrollState = rememberLazyListState()
    val showScrollIndicator by remember {
        derivedStateOf {
            val lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = scrollState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index < totalItems - 1
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Original task (will be the parent/header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Parent task:",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = result.originalTask,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Subtasks label with count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subtasks:",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "${includedSubtasks.size} of ${result.subtasks.size} selected",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        Spacer(Modifier.height(10.dp))

        // Subtasks list - tap to toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)  // Oct 2026: keeps its height where it fits, shrinks so the buttons stay on a small screen
                .height(300.dp) // Fixed height
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (showScrollIndicator) 24.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(result.subtasks) { index, subtask ->
                    SubtaskItem(
                        subtask = subtask,
                        index = index + 1,
                        isIncluded = includedSubtasks.contains(subtask),
                        onToggle = {
                            // Toggle include/exclude
                            includedSubtasks = if (includedSubtasks.contains(subtask)) {
                                includedSubtasks.minus(subtask)
                            } else {
                                includedSubtasks.plus(subtask)
                            }
                        }
                    )
                }
            }

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

        Spacer(Modifier.height(10.dp))

        // Info text
        Text(
            text = "Tap subtask to include/exclude. Parent and subtasks will be grouped together.",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.gray
        )

        Spacer(Modifier.height(14.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Button.height)
                    .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .noRippleClickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Button.height)
                    .background(
                        if (includedSubtasks.isNotEmpty()) EInkTheme.Button.primaryBackground else EInkTheme.Button.disabledBackground,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .border(
                        EInkTheme.Button.borderWidth,
                        if (includedSubtasks.isNotEmpty()) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .then(
                        if (includedSubtasks.isNotEmpty()) {
                            Modifier.noRippleClickable {
                                val filteredResult = result.copy(
                                    subtasks = result.subtasks.filter { includedSubtasks.contains(it) }
                                )
                                onApply(filteredResult)
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Apply (${includedSubtasks.size})",
                    color = if (includedSubtasks.isNotEmpty()) EInkTheme.Button.primaryTextColor else EInkTheme.Colors.gray,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SubtaskItem(
    subtask: TaskAiService.SubTask,
    index: Int,
    isIncluded: Boolean,
    onToggle: () -> Unit
) {
    // Entire row is clickable to toggle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isIncluded) EInkTheme.Colors.offWhite else EInkTheme.Colors.white,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                EInkTheme.Global.borderWidth,
                if (isIncluded) EInkTheme.Colors.darkGray else EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable { onToggle() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox indicator (e-ink friendly, no Material)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isIncluded) EInkTheme.Colors.primary else EInkTheme.Colors.white,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        2.dp,
                        if (isIncluded) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isIncluded) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Included",
                        tint = EInkTheme.Colors.white,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Index number
            Text(
                text = "$index.",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isIncluded) EInkTheme.Colors.primary else EInkTheme.Colors.gray
            )

            // Subtask text
            Text(
                text = subtask.text,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = if (isIncluded) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Processing state for the task breakdown dialog.
 */
sealed class TaskBreakdownProcessingState {
    /** Initial OCR phase - scanning for handwritten tasks */
    object ProcessingOcr : TaskBreakdownProcessingState()
    /** User selects which task to break down */
    object SelectTask : TaskBreakdownProcessingState()
    /** AI is generating subtasks for the selected task */
    object ProcessingBreakdown : TaskBreakdownProcessingState()
    /** Subtasks ready for user review */
    object Ready : TaskBreakdownProcessingState()
    /** Error occurred */
    data class Error(val message: String) : TaskBreakdownProcessingState()
}
