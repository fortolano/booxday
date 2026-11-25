package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme

/**
 * Dialog for moving a task to a different date.
 *
 * Uses the shared TaskDatePickerDialog component for consistent styling.
 * E-ink optimized with Popup (no gray overlay).
 *
 * @param currentDate The current date of the task (yyyy-MM-dd format)
 * @param onDateSelected Callback when user selects a date to move to
 * @param onCopy Optional callback when user wants to copy the task to selected date. If null, Copy button is hidden.
 * @param onDelete Optional callback when user wants to delete the task. If null, Delete button is hidden.
 * @param onEdit Optional callback when user wants to edit the task (typed tasks only). If null, Edit button is hidden.
 * @param onDismiss Callback to dismiss the dialog
 */
@Composable
fun MoveTaskDialog(
    currentDate: String,
    onDateSelected: (String) -> Unit,
    onCopy: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    TaskDatePickerDialog(
        title = "Task Management",
        currentDate = currentDate,
        actionButtonLabel = "Move",
        onDateSelected = onDateSelected,
        onDelete = onDelete,
        onEdit = onEdit,
        onCopy = onCopy,
        onDismiss = onDismiss
    )
}

/**
 * Simplified dialog for child tasks (subtasks).
 *
 * Child tasks cannot be moved individually - they must stay with their parent.
 * This dialog offers Edit (if task has text), Delete, and Copy options.
 *
 * Button layout per Dialog Rulebook: [Cancel] [Delete] [Edit?] [Copy?]
 * - Cancel: leftmost (escape)
 * - Delete: destructive (middle)
 * - Edit: secondary (conditional)
 * - Copy: constructive (rightmost when visible, conditional)
 *
 * @param onDelete Callback when user confirms deletion
 * @param onEdit Optional callback when user wants to edit the task. If null, Edit button is hidden.
 * @param onCopy Optional callback when user wants to copy the task. If null, Copy button is hidden.
 * @param onDismiss Callback to dismiss the dialog
 */
@Composable
fun ChildTaskDialog(
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
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
        ChildTaskDialogContent(
            onDelete = onDelete,
            onEdit = onEdit,
            onCopy = onCopy,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun ChildTaskDialogContent(
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogWidth = EInkTheme.PopupOverlay.width
    Box(
        modifier = modifier
            .width(dialogWidth)
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
        ) {
            Text(
                text = "Subtask",
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.ModernDialog.titleColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "This is a subtask. To move it, use the parent task's Move option.",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dialog Rulebook: [Cancel] (left, escape), [Delete] (middle, destructive, monochrome outlined),
            // [Edit?] (secondary or primary if rightmost), [Copy?] (primary rightmost when present). All 44dp.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EinkSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    width = 0.dp,
                    modifier = Modifier.weight(1f)
                )
                EinkSecondaryButton(
                    text = "Delete",
                    onClick = onDelete,
                    width = 0.dp,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    if (onCopy == null) {
                        EinkPrimaryButton(
                            text = "Edit",
                            onClick = onEdit,
                            width = 0.dp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        EinkSecondaryButton(
                            text = "Edit",
                            onClick = onEdit,
                            width = 0.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (onCopy != null) {
                    EinkPrimaryButton(
                        text = "Copy",
                        onClick = onCopy,
                        width = 0.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Simple confirmation dialog following the Dialog Rulebook (Phase 4 / Decision D3 P6):
 * 44dp buttons, no Color.Red, specific action verb required (never "Confirm"), Cancel on left.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
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
        ConfirmationDialogContent(
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            isDestructive = isDestructive,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun ConfirmationDialogContent(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogWidth = minOf(EInkTheme.PopupPanel.width, EInkTheme.PopupOverlay.width)
    Box(
        modifier = modifier
            .width(dialogWidth)
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
        ) {
            Text(
                text = title,
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.ModernDialog.titleColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2-button Dialog Rulebook layout: escape left, action verb right (monochrome E-Ink, 44dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EinkSecondaryButton(
                    text = cancelText,
                    onClick = onDismiss,
                    width = 0.dp,
                    modifier = Modifier.weight(1f)
                )
                EinkPrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    width = 0.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
