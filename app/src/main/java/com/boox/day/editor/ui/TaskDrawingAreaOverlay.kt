package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import com.boox.day.data.db.Task
import com.boox.day.ui.theme.EInkTheme

/**
 * Overlay for rendering typed text on task rows in the drawing area.
 *
 * IMPORTANT: This overlay does NOT handle any touch events.
 * The drawing area must remain free for stylus input via Onyx TouchHelper.
 * All touch interactions (tap, long-press) are handled in TasksControlColumn.
 *
 * @param tasks List of tasks for current page
 * @param rowCount Number of task rows (typically 10)
 * @param canvasHeightPx Canvas height in pixels (for row height calculation)
 * @param rowsWithStrokes Set of row indices that have strokes (handwritten content)
 * @param modifier Modifier for the overlay
 */
@Composable
fun TaskTypedTextOverlay(
    tasks: List<Task>,
    rowCount: Int,
    canvasHeightPx: Int,
    rowsWithStrokes: Set<Int> = emptySet(),
    recurringStateForTask: (Task) -> Boolean? = { null },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Calculate row height in dp
    val rowHeightDp: Dp = with(density) {
        (canvasHeightPx.toFloat() / rowCount).toDp()
    }

    // Pre-compute which tasks are parents (have children on this page)
    // A task is a parent if any other task has this task's ID as parentTaskId
    val parentTaskIds = remember(tasks) {
        val childParentIds = tasks.mapNotNull { it.parentTaskId }.toSet()
        tasks.filter { it.id in childParentIds }.map { it.id }.toSet()
    }

    // Connector line color for child tasks
    val connectorColor = EInkTheme.TasksTab.Subtask.textColor

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        for (rowIndex in 0 until rowCount) {
            val task = tasks.getOrNull(rowIndex)
            val typedText = task?.typedText
            val convertedText = task?.convertedText
            val displayText = when {
                !typedText.isNullOrBlank() -> typedText
                !convertedText.isNullOrBlank() -> convertedText
                // recognizedText is NOT displayed — it's internal data for search, AI, and
                // semantic indexing. Displaying it caused dual-display bugs where OCR text
                // overlapped handwritten strokes due to fragile rowsWithStrokes mapping.
                else -> ""
            }
            val hasTypedText = displayText.isNotBlank()
            val isCompleted = task?.isCompleted == true

            // Detect if this is a child task (has a parent)
            // Child tasks are identified by parentTaskId in database
            val isChildTask = task?.parentTaskId != null

            // Detect if this is a parent task (has children on this page)
            val isParentTask = task?.id in parentTaskIds
            val recurringState = task?.let { recurringStateForTask(it) }

            // Add ▼ indicator for parent tasks to show they have subtasks
            val parentDisplayText = when {
                isParentTask && hasTypedText -> "▼ $displayText"
                else -> displayText
            }

            TaskTypedTextRow(
                rowHeight = rowHeightDp,
                hasTypedText = hasTypedText,
                displayText = parentDisplayText,
                isCompleted = isCompleted,
                isChildTask = isChildTask,
                isParentTask = isParentTask,
                recurringState = recurringState,
                connectorColor = connectorColor
            )
        }
    }
}

/**
 * Extracted row composable with stable parameters to enable skipping recomposition.
 * The Task object itself is unstable due to Date fields, so we pass primitives.
 */
@Composable
private fun TaskTypedTextRow(
    rowHeight: Dp,
    hasTypedText: Boolean,
    displayText: String,
    isCompleted: Boolean,
    isChildTask: Boolean,
    isParentTask: Boolean,
    recurringState: Boolean?,
    connectorColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            // Completed tasks: NO overlay (E-Ink rule: no alpha on containers)
            // Visual distinction provided by grayed text/strokes via completedTextColor, completedStrokeColor
            // Draw connector line for child tasks (visual grouping)
            .then(
                if (isChildTask && !isCompleted) {
                    Modifier.drawBehind {
                        // Draw vertical connector line on left edge
                        val lineX = 24.dp.toPx()  // Position after the padding
                        drawLine(
                            color = connectorColor,
                            start = Offset(lineX, 0f),
                            end = Offset(lineX, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hasTypedText || recurringState != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isChildTask) EInkTheme.TasksTab.Subtask.paddingStart else EInkTheme.TasksTab.typedTextPaddingStart,
                        end = EInkTheme.TasksTab.typedTextPaddingEnd
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recurringState != null) {
                    Icon(
                        painter = painterResource(R.drawable.refresh_40px),
                        contentDescription = if (recurringState) "Recurring task" else "Paused recurring task",
                        tint = when {
                            isCompleted -> EInkTheme.TasksTab.completedTypedTextColor
                            recurringState -> EInkTheme.TasksTab.typedTextColor
                            else -> EInkTheme.Colors.gray
                        },
                        modifier = Modifier.size(15.dp)
                    )

                    if (hasTypedText) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                if (hasTypedText) {
                    Text(
                        text = displayText,
                        fontSize = if (isChildTask) EInkTheme.TasksTab.Subtask.fontSize else EInkTheme.TasksTab.typedTextFontSize,
                        color = when {
                            isCompleted -> EInkTheme.TasksTab.completedTypedTextColor
                            isChildTask -> EInkTheme.TasksTab.Subtask.textColor
                            else -> EInkTheme.TasksTab.typedTextColor
                        },
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        // Plan T3 / 13.3 (Oct 2026): long text (e.g. AI-written) wraps to two
                        // lines where rows are tall enough; elsewhere it ends with "…" and the
                        // task card shows it in full
                        maxLines = if (rowHeight >= 52.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Strikethrough line for completed tasks (covers handwritten content too)
        if (isCompleted) {
            Divider(
                color = EInkTheme.TasksTab.completedStrikethroughColor,
                thickness = EInkTheme.TasksTab.completedStrikethroughThickness,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
