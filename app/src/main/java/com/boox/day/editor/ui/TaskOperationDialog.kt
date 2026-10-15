package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

/**
 * Type of bulk task operation.
 */
enum class TaskOperationType {
    MOVE,
    COPY,
    DELETE
}

/**
 * Scope for bulk task operations.
 */
enum class TaskOperationScope {
    CURRENT_PAGE,
    ALL_PAGES
}

/**
 * Unified dialog for bulk task operations (Move, Copy, Delete).
 *
 * Features a scope toggle allowing users to choose between:
 * - Current Page: Operates on tasks from the current task page only
 * - All Pages: Operates on tasks from all task pages for the day
 *
 * For Move/Copy operations, displays a date picker to select target date.
 * For Delete operations, displays a confirmation message.
 *
 * @param operationType The type of operation (MOVE, COPY, DELETE)
 * @param currentDate The source date (yyyy-MM-dd format)
 * @param currentPageIndex The current task page index (for display in messages)
 * @param onConfirm Callback when user confirms the operation.
 *                  For MOVE/COPY: receives (scope, targetDate)
 *                  For DELETE: receives (scope, null)
 * @param onDismiss Callback to dismiss the dialog
 */
@Composable
fun TaskOperationDialog(
    operationType: TaskOperationType,
    currentDate: String,
    currentPageIndex: Int,
    onConfirm: (scope: TaskOperationScope, targetDate: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = EInkTheme.TaskDatePickerDialog

    // State for scope selection - default to CURRENT_PAGE
    var selectedScope by remember { mutableStateOf(TaskOperationScope.CURRENT_PAGE) }

    // State for date selection (Move/Copy only)
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // Dialog title based on operation type
    val title = when (operationType) {
        TaskOperationType.MOVE -> "Move Tasks"
        TaskOperationType.COPY -> "Copy Tasks"
        TaskOperationType.DELETE -> "Delete Tasks"
    }

    // Action button text
    val actionButtonLabel = when (operationType) {
        TaskOperationType.MOVE -> "Move"
        TaskOperationType.COPY -> "Copy"
        TaskOperationType.DELETE -> "Delete"
    }

    // Can perform action?
    val canConfirm = when (operationType) {
        TaskOperationType.MOVE, TaskOperationType.COPY -> {
            selectedDate != null && selectedDate != currentDate
        }
        TaskOperationType.DELETE -> true
    }

    // Position 35 pixels higher to avoid being too low on screen
    Popup(
        alignment = Alignment.Center,
        offset = IntOffset(0, -35),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .width(theme.dialogWidth)
                .background(theme.dialogBackground, RoundedCornerShape(theme.dialogCornerRadius))
                .border(theme.dialogBorderWidth, theme.dialogBorderColor, RoundedCornerShape(theme.dialogCornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(theme.dialogPadding)
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = theme.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = theme.titleColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scope toggle section
                Text(
                    text = "Scope:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scope toggle buttons
                ScopeToggle(
                    selectedScope = selectedScope,
                    onScopeChange = { selectedScope = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on operation type
                when (operationType) {
                    TaskOperationType.MOVE, TaskOperationType.COPY -> {
                        // Date picker for Move/Copy
                        TaskDatePickerContent(
                            currentDate = currentDate,
                            selectedDate = selectedDate,
                            onDateSelected = { selectedDate = it }
                        )
                    }
                    TaskOperationType.DELETE -> {
                        // Confirmation message for Delete
                        DeleteConfirmationContent(
                            currentDate = currentDate,
                            currentPageIndex = currentPageIndex,
                            scope = selectedScope
                        )
                    }
                }

                Spacer(modifier = Modifier.height(theme.gridToButtonsSpacing))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.background, RoundedCornerShape(theme.buttonCornerRadius))
                            .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(theme.buttonCornerRadius))
                            .noRippleClickable { onDismiss() }
                            .padding(horizontal = theme.buttonHorizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            color = EInkTheme.Button.textColor,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(theme.buttonRowSpacing))

                    // Action button
                    val buttonBackground = when {
                        !canConfirm -> EInkTheme.Colors.gray
                        operationType == TaskOperationType.DELETE -> Color.Red
                        else -> EInkTheme.Button.primaryBackground
                    }

                    Box(
                        modifier = Modifier
                            .height(EInkTheme.Button.height)
                            .background(buttonBackground, RoundedCornerShape(theme.buttonCornerRadius))
                            .noRippleClickable(enabled = canConfirm) {
                                onConfirm(selectedScope, selectedDate)
                            }
                            .padding(horizontal = theme.buttonHorizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            actionButtonLabel,
                            color = if (canConfirm) EInkTheme.Button.primaryTextColor else EInkTheme.Colors.darkGray,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scope toggle component with two buttons: "Current Page" and "All Pages".
 * Uses segmented button styling optimized for E-Ink displays.
 */
@Composable
private fun ScopeToggle(
    selectedScope: TaskOperationScope,
    onScopeChange: (TaskOperationScope) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.black, RoundedCornerShape(6.dp))
    ) {
        // Current Page button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .background(
                    color = if (selectedScope == TaskOperationScope.CURRENT_PAGE)
                        EInkTheme.Colors.black else EInkTheme.Colors.white,
                    shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                )
                .noRippleClickable { onScopeChange(TaskOperationScope.CURRENT_PAGE) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Current Page",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (selectedScope == TaskOperationScope.CURRENT_PAGE)
                    FontWeight.Bold else FontWeight.Normal,
                color = if (selectedScope == TaskOperationScope.CURRENT_PAGE)
                    EInkTheme.Colors.white else EInkTheme.Colors.black
            )
        }

        // Divider between buttons
        Box(
            modifier = Modifier
                .width(EInkTheme.Global.borderWidth)
                .height(44.dp)
                .background(EInkTheme.Colors.black)
        )

        // All Pages button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .background(
                    color = if (selectedScope == TaskOperationScope.ALL_PAGES)
                        EInkTheme.Colors.black else EInkTheme.Colors.white,
                    shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
                )
                .noRippleClickable { onScopeChange(TaskOperationScope.ALL_PAGES) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "All Pages",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (selectedScope == TaskOperationScope.ALL_PAGES)
                    FontWeight.Bold else FontWeight.Normal,
                color = if (selectedScope == TaskOperationScope.ALL_PAGES)
                    EInkTheme.Colors.white else EInkTheme.Colors.black
            )
        }
    }
}

/**
 * Confirmation content for delete operation.
 * Shows a warning message that varies based on scope selection.
 */
@Composable
private fun DeleteConfirmationContent(
    currentDate: String,
    currentPageIndex: Int,
    scope: TaskOperationScope
) {
    val message = when (scope) {
        TaskOperationScope.CURRENT_PAGE ->
            "Delete all tasks on page ${currentPageIndex + 1} for $currentDate?\n\nThis cannot be undone."
        TaskOperationScope.ALL_PAGES ->
            "Delete all tasks for $currentDate?\n\nThis will remove tasks from all pages and cannot be undone."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = message,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            lineHeight = EInkTheme.Typography.bodyLarge
        )
    }
}
