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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
        Box(
            modifier = Modifier
                .width(280.dp)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
            ) {
                // Title
                Text(
                    text = "Subtask",
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.titleColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message
                Text(
                    text = "This is a subtask. To move it, use the parent task's Move option.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons: [Cancel] [Delete] [Edit?] [Copy?]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cancel button (leftmost - escape)
                    Box(
                        modifier = Modifier
                            .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Cancel", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodySmall)
                    }

                    // Delete button (middle - destructive)
                    Box(
                        modifier = Modifier
                            .background(Color.Red, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onDelete() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Delete", color = Color.White, fontSize = EInkTheme.Typography.bodySmall)
                    }

                    // Edit button (secondary) - only if onEdit is provided
                    if (onEdit != null) {
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .noRippleClickable { onEdit() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Edit", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodySmall)
                        }
                    }

                    // Copy button (rightmost constructive) - only if onCopy is provided
                    if (onCopy != null) {
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .noRippleClickable { onCopy() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Copy", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple confirmation dialog for destructive actions.
 * E-ink optimized with no animations or gray overlays.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
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
        Box(
            modifier = Modifier
                .width(280.dp)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.titleColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message
                Text(
                    text = message,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(cancelText, color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Confirm button
                    Box(
                        modifier = Modifier
                            .background(
                                if (isDestructive) Color.Red else EInkTheme.ModernDialog.PrimaryButton.background,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable { onConfirm() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            confirmText,
                            color = EInkTheme.ModernDialog.PrimaryButton.textColor,
                            fontSize = EInkTheme.Typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
