package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDueDates
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Task card (plan T3 / 13.3, Oct 2026): one place for a task's text, priority, due date,
 * repeat, reminder time, subtasks, move/copy and delete — opened from the row's chevron.
 * Replaces hunting across the chevron's type/move dialogs, the due button and the priority
 * popup (those quick controls stay). Only shows values and calls back; MainScreen does the
 * work through the existing dialogs and repository calls.
 *
 * E-ink: Popup (no Dialog), no ripples, ≥44 dp targets, buttons per DIALOG_RULEBOOK
 * ([Close] [Delete] [Move]).
 */
@Composable
fun TaskCardPopup(
    task: Task,
    /** Typed or AI-converted text shown as the task's title (null for handwriting). */
    titleText: String?,
    /** Text recognized from the row's handwriting, when any. */
    recognizedText: String?,
    /** "Every week" style label of the repeat series, or null when the task doesn't repeat. */
    repeatLabel: String?,
    subtaskCount: Int,
    subtasksDone: Int,
    defaultReminderMinuteOfDay: Int,
    onEditText: () -> Unit,
    /** Turn recognized handwriting into typed text; null hides the button. */
    onConvertToText: (() -> Unit)?,
    onPriorityChange: (String) -> Unit,
    /** Opens the due date / repeat dialog. */
    onEditDueAndRepeat: () -> Unit,
    onClearDue: () -> Unit,
    /** Minutes after midnight, or null for the shared default. */
    onReminderChange: (Int?) -> Unit,
    /** AI break-down into subtasks; null hides the button. */
    onBreakDown: (() -> Unit)?,
    onDelete: () -> Unit,
    /** Opens the move / copy dialog. */
    onMove: () -> Unit,
    onDismiss: () -> Unit,
    /** Primary button label: "Move", or "Copy" for subtasks (they move with their parent). */
    moveLabel: String = "Move"
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = EInkTheme.PopupOverlay.width)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .noRippleClickable { }  // taps inside don't dismiss
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header: "Task" + day and row
                Text(
                    text = "Task",
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = remember_dayLabel(task),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = EInkTheme.PopupOverlay.maxHeight - 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ---- Text -------------------------------------------------------------------
                    if (!titleText.isNullOrBlank()) {
                        Text(text = titleText, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary)
                    } else {
                        Text(
                            text = "Handwritten",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                        if (!recognizedText.isNullOrBlank()) {
                            Text(
                                text = recognizedText,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EinkSecondaryButton(text = "Edit text", onClick = onEditText, width = 120.dp)
                        if (onConvertToText != null) {
                            EinkSecondaryButton(text = "Convert to text", onClick = onConvertToText, width = 150.dp)
                        }
                    }

                    CardDivider()

                    // ---- Priority -----------------------------------------------------------------
                    CardRow(label = "Priority") {
                        listOf("" to "–", "A" to "A", "B" to "B", "C" to "C").forEach { (value, label) ->
                            val selected = task.priority == value
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(EInkTheme.Global.touchTarget)
                                    .background(
                                        if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                                    )
                                    .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .noRippleClickable { if (!selected) onPriorityChange(value) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
                                )
                            }
                        }
                    }

                    // ---- Due date + repeat -------------------------------------------------------------
                    val dueDate = TaskDueDates.toLocalDate(task.dueDay)
                    val overdue = TaskDueDates.isOverdue(task.dueDay, task.isCompleted)
                    CardRow(label = "Due") {
                        Text(
                            text = dueDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                                ?.let { if (overdue) "$it (overdue)" else it }
                                ?: "None",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                            color = EInkTheme.Colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        EinkSecondaryButton(text = "Change", onClick = onEditDueAndRepeat, width = 90.dp)
                        if (dueDate != null) {
                            Spacer(Modifier.width(6.dp))
                            EinkSecondaryButton(text = "Clear", onClick = onClearDue, width = 80.dp)
                        }
                    }
                    CardRow(label = "Repeat") {
                        Text(
                            text = repeatLabel ?: "No",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        EinkSecondaryButton(text = "Change", onClick = onEditDueAndRepeat, width = 90.dp)
                    }

                    // ---- Reminder time (only meaningful with a due date) --------------------------------
                    // Plan §13.6A: Remind at: shared default · 8:00 · 12:00 · 15:00 · Pick time
                    if (dueDate != null) {
                        val custom = task.reminderMinuteOfDay
                        val minuteOfDay = custom ?: defaultReminderMinuteOfDay
                        val presets = listOf(8 * 60, 12 * 60, 15 * 60)
                        var picking by remember(task.id) { mutableStateOf(custom != null && custom !in presets) }
                        CardRow(label = "Reminder") {
                            Text(
                                text = formatMinuteOfDay(minuteOfDay) + if (custom == null) " (default)" else "",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReminderChip("Default", selected = custom == null && !picking, modifier = Modifier.weight(1.3f)) {
                                picking = false
                                onReminderChange(null)
                            }
                            presets.forEach { preset ->
                                ReminderChip(
                                    formatMinuteOfDay(preset),
                                    selected = custom == preset && !picking,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    picking = false
                                    onReminderChange(preset)
                                }
                            }
                            ReminderChip("Pick", selected = picking, modifier = Modifier.weight(1f)) { picking = true }
                        }
                        if (picking) {
                            // Pick time: 30-minute steps from the current reminder time
                            CardRow(label = "") {
                                StepButton("−") { onReminderChange(Math.floorMod(minuteOfDay - 30, 24 * 60)) }
                                Text(
                                    text = formatMinuteOfDay(minuteOfDay),
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                StepButton("+") { onReminderChange(Math.floorMod(minuteOfDay + 30, 24 * 60)) }
                            }
                        }
                    }

                    // ---- Subtasks ----------------------------------------------------------------------
                    if (subtaskCount > 0 || onBreakDown != null) {
                        CardRow(label = "Subtasks") {
                            Text(
                                text = if (subtaskCount > 0) "$subtaskCount ($subtasksDone done)" else "None",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (onBreakDown != null) {
                                EinkSecondaryButton(text = "Break down", onClick = onBreakDown, width = 120.dp)
                            }
                        }
                    }
                }

                CardDivider()

                // ---- Buttons: [Close] [Delete] [Move] (DIALOG_RULEBOOK 3-button layout) ------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EinkSecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.weight(1f))
                    EinkSecondaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
                    ActionButton(text = moveLabel, onClick = onMove, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CardRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray,
            modifier = Modifier.width(76.dp)
        )
        content()
    }
}

@Composable
private fun ReminderChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(EInkTheme.Global.touchTarget)
            .background(
                if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary)
    }
}

@Composable
private fun CardDivider() {
    Divider(
        color = EInkTheme.Colors.borderStrong,
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

/** What the task card shows besides the task row itself (loaded when the card opens). */
data class TaskCardData(
    val task: Task,
    val repeatLabel: String?,
    val subtaskCount: Int,
    val subtasksDone: Int
)

/** Short description of a repeat series for the task card. */
fun taskRepeatLabel(template: com.boox.day.data.db.RecurringTaskTemplate): String {
    val base = when (template.recurrenceType) {
        "DAILY" -> if (template.intervalDays > 1) "Every ${template.intervalDays} days" else "Daily"
        "WEEKLY" -> "Weekly"
        "CUSTOM_WEEKDAYS" -> "Some weekdays"
        "MONTHLY" -> template.dayOfMonth?.let { "Monthly on day $it" } ?: "Monthly"
        else -> template.recurrenceType.lowercase().replaceFirstChar { it.uppercase() }
    }
    return if (template.isActive) base else "$base (paused)"
}

private fun remember_dayLabel(task: Task): String {
    val day = TaskDueDates.toLocalDate(task.dayDate)
        ?.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        ?: task.dayDate
    val row = task.taskPageIndex * 10 + task.rowIndex + 1
    return "$day · row $row"
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
