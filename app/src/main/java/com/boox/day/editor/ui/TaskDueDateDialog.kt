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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.RecurringTaskDraft
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Task
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TaskDueDateDialog(
    task: Task,
    recurringTemplate: RecurringTaskTemplate? = null,
    recurringBlockedReason: String? = null,
    onDueDateSelected: (Long?) -> Unit,
    onRecurringSave: ((RecurringTaskDraft) -> Unit)? = null,
    onRecurringToggleActive: (() -> Unit)? = null,
    onRecurringDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onNavigateToDueDate: ((String) -> Unit)? = null
) {
    // Due day (v27) as the start of that day in ms — the picker below works in ms
    val currentDueDate = com.boox.day.data.db.TaskDueDates.startOfDayMillis(task.dueDay)
    val hasDueDate = currentDueDate != null
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val defaultTaskDate = remember(task.dayDate) { parseLocalDate(task.dayDate) ?: LocalDate.now() }
    val currentDraft = remember(recurringTemplate?.id, recurringTemplate?.updatedAt) {
        recurringTemplate?.let { RecurringTaskDraft.fromTemplate(it) }
    }
    var selectedDueDateString by remember(task.id, currentDueDate) {
        mutableStateOf(currentDueDate?.let { dateFormat.format(Date(it)) } ?: dateFormat.format(Date()))
    }
    var dueDateWasTouched by remember(task.id, currentDueDate) { mutableStateOf(false) }
    var repeatEnabled by remember(task.id, recurringTemplate?.id) { mutableStateOf(recurringTemplate != null) }
    var repeatType by remember(task.id, recurringTemplate?.id) { mutableStateOf(currentDraft?.recurrenceType ?: "DAILY") }
    var repeatStartDate by remember(task.id, recurringTemplate?.id) { mutableStateOf(currentDraft?.anchorDate ?: task.dayDate) }
    var repeatWeekdays by remember(task.id, recurringTemplate?.id) {
        mutableStateOf(currentDraft?.daysOfWeek?.ifEmpty { listOf(defaultTaskDate.dayOfWeek.value) } ?: listOf(defaultTaskDate.dayOfWeek.value))
    }
    var repeatDayOfMonth by remember(task.id, recurringTemplate?.id) {
        mutableStateOf(currentDraft?.dayOfMonth ?: defaultTaskDate.dayOfMonth.coerceAtMost(28))
    }
    var repeatEndDate by remember(task.id, recurringTemplate?.id) { mutableStateOf(currentDraft?.endDate) }
    var showStartPicker by remember(task.id) { mutableStateOf(false) }
    var showEndPicker by remember(task.id) { mutableStateOf(false) }

    val repeatBlocked = recurringBlockedReason != null && recurringTemplate == null
    val remainingDays = currentDueDate?.let {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val due = Calendar.getInstance().apply {
            timeInMillis = it
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        ((due - now) / (1000 * 60 * 60 * 24)).toInt()
    }

    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, dismissOnBackPress = true)) {
        Box(
            modifier = Modifier.fillMaxSize().noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.TaskDatePickerDialog.dialogWidth)
                    .background(EInkTheme.TaskDatePickerDialog.dialogBackground, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(
                        EInkTheme.TaskDatePickerDialog.dialogBorderWidth,
                        EInkTheme.TaskDatePickerDialog.dialogBorderColor,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable { }
                    .padding(EInkTheme.TaskDatePickerDialog.dialogPadding)
            ) {
                Text(
                    text = if (hasDueDate) "Due Date" else "Set Due Date",
                    fontSize = EInkTheme.TaskDatePickerDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.TaskDatePickerDialog.titleColor
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                if (currentDueDate != null) {
                    DueDateInfoSection(
                        dueDate = currentDueDate,
                        remainingDays = remainingDays ?: 0,
                        onTap = onNavigateToDueDate?.let { navigate ->
                            {
                                navigate(dateFormat.format(Date(currentDueDate)))
                                onDismiss()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                    Text(
                        text = "Change Date",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                }

                TaskDatePickerContent(
                    currentDate = "",
                    selectedDate = selectedDueDateString,
                    onDateSelected = {
                        selectedDueDateString = it
                        dueDateWasTouched = true
                    }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                Divider(color = EInkTheme.Colors.lightGray, thickness = EInkTheme.Global.borderWidth)
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "Repeat",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                    ToggleButton(
                        label = "Off",
                        selected = !repeatEnabled,
                        enabled = recurringTemplate == null && !repeatBlocked,
                        modifier = Modifier.weight(1f)
                    ) { repeatEnabled = false }
                    ToggleButton(
                        label = "On",
                        selected = repeatEnabled,
                        enabled = !repeatBlocked,
                        modifier = Modifier.weight(1f)
                    ) { repeatEnabled = true }
                }

                if (repeatBlocked) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(recurringBlockedReason ?: "", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
                } else if (recurringTemplate != null) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = "This dialog edits the linked repeating series.",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )

                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        SecondaryButton(
                            label = if (recurringTemplate.isActive) "Pause Series" else "Resume Series",
                            modifier = Modifier.weight(1f)
                        ) {
                            onRecurringToggleActive?.invoke()
                        }
                        SecondaryButton(
                            label = "Delete Series",
                            modifier = Modifier.weight(1f)
                        ) {
                            onRecurringDelete?.invoke()
                        }
                    }
                }

                if (repeatEnabled && !repeatBlocked) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                    LabeledValueButton("Start Date", formatScheduleDate(repeatStartDate)) { showStartPicker = true }
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                    Text("Type", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.darkGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleButton("Daily", repeatType == "DAILY", true, Modifier.weight(1f)) { repeatType = "DAILY" }
                        ToggleButton("Weekly", repeatType == "WEEKLY", true, Modifier.weight(1f)) { repeatType = "WEEKLY" }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleButton("Weekdays", repeatType == "CUSTOM_WEEKDAYS", true, Modifier.weight(1f)) {
                            repeatType = "CUSTOM_WEEKDAYS"
                            if (repeatWeekdays.isEmpty()) repeatWeekdays = listOf(defaultTaskDate.dayOfWeek.value)
                        }
                        ToggleButton("Monthly", repeatType == "MONTHLY", true, Modifier.weight(1f)) {
                            repeatType = "MONTHLY"
                            repeatDayOfMonth = (parseLocalDate(repeatStartDate) ?: defaultTaskDate).dayOfMonth.coerceAtMost(28)
                        }
                    }

                    when (repeatType) {
                        "WEEKLY" -> {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                            Text(
                                text = "Repeats every ${weekdayLabel(repeatStartDate)}.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        "CUSTOM_WEEKDAYS" -> {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                WEEKDAYS.forEach { (value, label) ->
                                    ToggleButton(
                                        label = label,
                                        selected = repeatWeekdays.contains(value),
                                        enabled = true,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        repeatWeekdays = if (repeatWeekdays.contains(value)) {
                                            repeatWeekdays.filterNot { it == value }
                                        } else {
                                            (repeatWeekdays + value).distinct().sorted()
                                        }
                                    }
                                }
                            }
                        }
                        "MONTHLY" -> {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                            Text(
                                text = "Repeats on day $repeatDayOfMonth each month.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                    EndDateButtons(repeatEndDate, { repeatEndDate = null }, { showEndPicker = true })
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                    // DIALOG_RULEBOOK: escape left, destructive in the middle, primary right
                    SecondaryButton("Cancel", Modifier.weight(if (hasDueDate) 0.3f else 0.35f)) { onDismiss() }
                    if (hasDueDate) {
                        SecondaryButton("Remove", Modifier.weight(0.25f)) {
                            onDueDateSelected(null)
                            onDismiss()
                        }
                    }
                    PrimaryButton("Save", Modifier.weight(if (hasDueDate) 0.45f else 0.65f)) {
                        val selectedMillis = try { dateFormat.parse(selectedDueDateString)?.time } catch (_: Exception) { null }
                        val shouldSaveDueDate = currentDueDate != null || !repeatEnabled || dueDateWasTouched
                        if (shouldSaveDueDate) selectedMillis?.let { onDueDateSelected(it) }
                        if (repeatEnabled && onRecurringSave != null) {
                            onRecurringSave(
                                RecurringTaskDraft(
                                    recurrenceType = repeatType,
                                    anchorDate = repeatStartDate,
                                    daysOfWeek = repeatWeekdays.sorted(),
                                    dayOfMonth = if (repeatType == "MONTHLY") repeatDayOfMonth else null,
                                    intervalDays = 1,
                                    endDate = repeatEndDate
                                )
                            )
                        }
                        onDismiss()
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        TaskDatePickerDialog(
            title = "Repeat Start",
            currentDate = "",
            actionButtonLabel = "Use Date",
            onDateSelected = {
                repeatStartDate = it
                val newDate = parseLocalDate(it) ?: defaultTaskDate
                repeatDayOfMonth = newDate.dayOfMonth.coerceAtMost(28)
                if (repeatType == "CUSTOM_WEEKDAYS" && repeatWeekdays.isEmpty()) {
                    repeatWeekdays = listOf(newDate.dayOfWeek.value)
                }
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        TaskDatePickerDialog(
            title = "Repeat End",
            currentDate = "",
            actionButtonLabel = "Use Date",
            onDateSelected = {
                repeatEndDate = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun ToggleButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(EInkTheme.Button.height)
            .background(
                when {
                    selected -> EInkTheme.Colors.primary
                    enabled -> EInkTheme.Colors.background
                    else -> EInkTheme.Colors.offWhite
                },
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                EInkTheme.Button.borderWidth,
                if (enabled || selected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .then(if (enabled) Modifier.noRippleClickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}

@Composable
private fun LabeledValueButton(label: String, value: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.darkGray)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(
                    EInkTheme.Button.borderWidth,
                    EInkTheme.Colors.primary,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .noRippleClickable { onClick() }
                .padding(horizontal = EInkTheme.Global.paddingMedium),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(value, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
        }
    }
}

@Composable
private fun EndDateButtons(endDate: String?, onClear: () -> Unit, onPickDate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("End Date", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.darkGray)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            ToggleButton("None", endDate == null, true, Modifier.weight(0.35f)) { onClear() }
            Box(
                modifier = Modifier
                    .weight(0.65f)
                    .height(EInkTheme.Button.height)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(
                        EInkTheme.Button.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable { onPickDate() }
                    .padding(horizontal = EInkTheme.Global.paddingMedium),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = endDate?.let { formatScheduleDate(it) } ?: "Set Date",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )
            }
        }
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(EInkTheme.Button.height)
            .border(
                EInkTheme.Button.borderWidth,
                EInkTheme.Colors.primary,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = EInkTheme.Typography.button, color = EInkTheme.Colors.primary)
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(EInkTheme.Button.height)
            .background(EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = EInkTheme.Typography.button, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.white)
    }
}

private val WEEKDAYS = listOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat",
    7 to "Sun"
)

private fun parseLocalDate(rawValue: String?): LocalDate? = try {
    if (rawValue.isNullOrBlank()) null else LocalDate.parse(rawValue)
} catch (_: Exception) {
    null
}

private fun formatScheduleDate(rawValue: String?): String {
    val localDate = parseLocalDate(rawValue) ?: return "Set Date"
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    return localDate.format(formatter)
}

private fun weekdayLabel(rawValue: String): String {
    val weekday = parseLocalDate(rawValue)?.dayOfWeek?.value ?: return "this day"
    return WEEKDAYS.firstOrNull { it.first == weekday }?.second ?: "this day"
}

@Composable
private fun DueDateInfoSection(
    dueDate: Long,
    remainingDays: Int,
    onTap: (() -> Unit)? = null
) {
    val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    val formattedDate = displayDateFormat.format(Date(dueDate))
    val remainingText = when {
        remainingDays < 0 -> "Overdue by ${-remainingDays} day${if (-remainingDays != 1) "s" else ""}"
        remainingDays == 0 -> "Due today!"
        remainingDays == 1 -> "Due tomorrow"
        else -> "$remainingDays days remaining"
    }
    val isOverdue = remainingDays < 0
    val isDueSoon = remainingDays in 0..2
    val isTappable = onTap != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isOverdue) EInkTheme.Colors.offWhite else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                if (isTappable) EInkTheme.Global.borderWidth * 2 else EInkTheme.Global.borderWidth,
                if (isOverdue) EInkTheme.Colors.primary else if (isTappable) EInkTheme.Colors.darkGray else EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .then(if (onTap != null) Modifier.noRippleClickable { onTap() } else Modifier)
            .padding(EInkTheme.Global.paddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = if (isTappable) "Tap to go to this date" else null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.height(20.dp)
            )
            Text(
                text = formattedDate,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        Text(
            text = remainingText,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (isOverdue || isDueSoon) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.darkGray
        )

        if (isTappable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to go to this date →",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }
    }
}
