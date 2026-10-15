package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Shared date picker content for task management dialogs (Move/Copy).
 *
 * This composable provides a reusable calendar picker with:
 * - Month/year navigation
 * - Touch-friendly day cells (40dp)
 * - Visual distinction for today, current date, and selected date
 *
 * Used by MoveTaskDialog (individual task) and TaskOperationDialog (bulk actions).
 *
 * @param currentDate The task's current date (yyyy-MM-dd format) - shown as disabled
 * @param selectedDate Currently selected target date, or null if none selected
 * @param onDateSelected Callback when user taps a valid date
 */
@Composable
fun TaskDatePickerContent(
    currentDate: String,
    selectedDate: String?,
    onDateSelected: (String) -> Unit
) {
    val theme = EInkTheme.TaskDatePickerDialog
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    val initialCalendar = remember {
        Calendar.getInstance().apply {
            try {
                time = dateFormat.parse(currentDate) ?: time
            } catch (e: Exception) { }
        }
    }

    var displayedYear by remember { mutableStateOf(initialCalendar.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableStateOf(initialCalendar.get(Calendar.MONTH)) }

    Column {
        // Month/Year navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (displayedMonth == 0) {
                        displayedMonth = 11
                        displayedYear--
                    } else {
                        displayedMonth--
                    }
                },
                modifier = Modifier.size(theme.navButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = theme.navIconColor,
                    modifier = Modifier.size(theme.navIconSize)
                )
            }

            val displayCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, displayedYear)
                set(Calendar.MONTH, displayedMonth)
            }
            Text(
                text = monthYearFormat.format(displayCalendar.time),
                fontSize = theme.monthYearFontSize,
                fontWeight = FontWeight.Medium,
                color = theme.monthYearColor
            )

            IconButton(
                onClick = {
                    if (displayedMonth == 11) {
                        displayedMonth = 0
                        displayedYear++
                    } else {
                        displayedMonth++
                    }
                },
                modifier = Modifier.size(theme.navButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = theme.navIconColor,
                    modifier = Modifier.size(theme.navIconSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(theme.navToWeekdaySpacing))

        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    fontSize = theme.weekdayFontSize,
                    fontWeight = FontWeight.Medium,
                    color = theme.weekdayColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(theme.weekdayToGridSpacing))

        // Calendar grid
        val calendarDays = remember(displayedYear, displayedMonth) {
            generateCalendarDays(displayedYear, displayedMonth)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(theme.gridHeight),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items(calendarDays.size) { index ->
                val dayInfo = calendarDays[index]
                TaskDateCell(
                    dayInfo = dayInfo,
                    currentDate = currentDate,
                    selectedDate = selectedDate,
                    onSelect = onDateSelected
                )
            }
        }
    }
}

/**
 * Data class representing a day in the calendar grid.
 */
internal data class CalendarDayInfo(
    val dayOfMonth: Int,
    val dateString: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

/**
 * Generate calendar days for a given month.
 * Returns 42 days (6 weeks) to fill the grid consistently.
 */
internal fun generateCalendarDays(year: Int, month: Int): List<CalendarDayInfo> {
    val days = mutableListOf<CalendarDayInfo>()
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val today = Calendar.getInstance()
    val todayString = dateFormat.format(today.time)

    calendar.set(year, month, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    calendar.add(Calendar.MONTH, -1)
    val daysInPrevMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevMonthDaysToShow = firstDayOfWeek - 1

    // Previous month days
    for (i in prevMonthDaysToShow downTo 1) {
        val day = daysInPrevMonth - i + 1
        calendar.set(year, month - 1, day)
        if (month == 0) {
            calendar.set(year - 1, 11, day)
        }
        days.add(CalendarDayInfo(day, dateFormat.format(calendar.time), false, false))
    }

    // Current month days
    for (day in 1..daysInMonth) {
        calendar.set(year, month, day)
        val dateString = dateFormat.format(calendar.time)
        days.add(CalendarDayInfo(day, dateString, true, dateString == todayString))
    }

    // Next month days to fill 42 cells (6 weeks)
    val remainingCells = 42 - days.size
    for (day in 1..remainingCells) {
        calendar.set(year, month + 1, day)
        if (month == 11) {
            calendar.set(year + 1, 0, day)
        }
        days.add(CalendarDayInfo(day, dateFormat.format(calendar.time), false, false))
    }

    return days
}

/**
 * Individual day cell for the task date picker.
 * Touch-optimized with 40dp size and clear visual states.
 */
@Composable
private fun TaskDateCell(
    dayInfo: CalendarDayInfo,
    currentDate: String,
    selectedDate: String?,
    onSelect: (String) -> Unit
) {
    val theme = EInkTheme.TaskDatePickerDialog
    val isCurrentDate = dayInfo.dateString == currentDate
    val isSelected = dayInfo.dateString == selectedDate

    val backgroundColor = when {
        isSelected -> theme.cellSelectedBackground
        isCurrentDate -> theme.cellCurrentDateBackground
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> theme.cellSelectedForeground
        !dayInfo.isCurrentMonth -> theme.cellColorOutsideMonth
        isCurrentDate -> theme.cellCurrentDateTextColor
        dayInfo.isToday -> theme.cellTodayTextColor
        else -> theme.cellColor
    }

    Box(
        modifier = Modifier
            .size(theme.cellSize)
            .padding(theme.cellPadding)
            .background(backgroundColor, RoundedCornerShape(theme.cellCornerRadius))
            .then(
                if (isCurrentDate && !isSelected) {
                    Modifier.border(
                        theme.cellCurrentDateBorderWidth,
                        theme.cellCurrentDateBorderColor,
                        RoundedCornerShape(theme.cellCornerRadius)
                    )
                } else Modifier
            )
            .noRippleClickable(enabled = !isCurrentDate && dayInfo.isCurrentMonth) {
                onSelect(dayInfo.dateString)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayInfo.dayOfMonth.toString(),
            fontSize = theme.cellFontSize,
            fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Wrapper composable that provides the full dialog popup container.
 * Used by MoveTaskDialog (individual task) and TaskOperationDialog (bulk actions).
 *
 * @param title Dialog title (e.g., "Task Management", "Copy Tasks")
 * @param currentDate The task's current date
 * @param actionButtonLabel Text for the primary action button (e.g., "Move", "Copy")
 * @param onDateSelected Callback when user confirms with selected date
 * @param onDelete Optional delete callback - shows Delete button if provided
 * @param onEdit Optional edit callback - shows Edit button if provided
 * @param onCopy Optional copy callback - shows Copy button if provided. Receives selected date.
 * @param onDismiss Callback to dismiss the dialog
 */
@Composable
fun TaskDatePickerDialog(
    title: String,
    currentDate: String,
    actionButtonLabel: String,
    onDateSelected: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = EInkTheme.TaskDatePickerDialog
    var selectedDate by remember { mutableStateOf<String?>(null) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        // Scrim: tapping outside the dialog dismisses it (matches TaskDueDateDialog exactly)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(theme.dialogWidth)
                    .background(theme.dialogBackground, RoundedCornerShape(theme.dialogCornerRadius))
                    .border(theme.dialogBorderWidth, theme.dialogBorderColor, RoundedCornerShape(theme.dialogCornerRadius))
                    .noRippleClickable { /* consume click, don't dismiss */ }
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

                Spacer(modifier = Modifier.height(theme.headerToNavSpacing))

                // Date picker content
                TaskDatePickerContent(
                    currentDate = currentDate,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it }
                )

                Spacer(modifier = Modifier.height(theme.gridToButtonsSpacing))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (onDelete != null || onEdit != null) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left-side buttons (Delete + Edit)
                    Row {
                        // Delete button
                        if (onDelete != null) {
                            Box(
                                modifier = Modifier
                                    .height(EInkTheme.Button.height)
                                    .background(theme.deleteButtonBackground, RoundedCornerShape(theme.buttonCornerRadius))
                                    .noRippleClickable { onDelete() }
                                    .padding(horizontal = theme.buttonHorizontalPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Delete",
                                    color = theme.deleteButtonTextColor,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Edit button
                        if (onEdit != null) {
                            if (onDelete != null) {
                                Spacer(modifier = Modifier.width(theme.buttonRowSpacing))
                            }
                            Box(
                                modifier = Modifier
                                    .height(EInkTheme.Button.height)
                                    .background(EInkTheme.Button.background, RoundedCornerShape(theme.buttonCornerRadius))
                                    .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(theme.buttonCornerRadius))
                                    .noRippleClickable { onEdit() }
                                    .padding(horizontal = theme.buttonHorizontalPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Edit",
                                    color = EInkTheme.Button.textColor,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Right-side buttons (Cancel + [Copy] + Action)
                    Row {
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

                        // Copy button (optional, outlined style between Cancel and primary action)
                        val canAct = selectedDate != null && selectedDate != currentDate
                        if (onCopy != null) {
                            Spacer(modifier = Modifier.width(theme.buttonRowSpacing))

                            Box(
                                modifier = Modifier
                                    .height(EInkTheme.Button.height)
                                    .background(
                                        if (canAct) EInkTheme.Button.background else EInkTheme.Colors.lightGray,
                                        RoundedCornerShape(theme.buttonCornerRadius)
                                    )
                                    .border(EInkTheme.Divider.thickness, EInkTheme.Button.borderColor, RoundedCornerShape(theme.buttonCornerRadius))
                                    .noRippleClickable(enabled = canAct) {
                                        selectedDate?.let { onCopy(it) }
                                    }
                                    .padding(horizontal = theme.buttonHorizontalPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Copy",
                                    color = if (canAct) EInkTheme.Button.textColor else EInkTheme.Colors.darkGray,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(theme.buttonRowSpacing))

                        // Primary action button (Move/Copy)
                        Box(
                            modifier = Modifier
                                .height(EInkTheme.Button.height)
                                .background(
                                    if (canAct) EInkTheme.Button.primaryBackground else EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(theme.buttonCornerRadius)
                                )
                                .noRippleClickable(enabled = canAct) {
                                    selectedDate?.let { onDateSelected(it) }
                                }
                                .padding(horizontal = theme.buttonHorizontalPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                actionButtonLabel,
                                color = if (canAct) EInkTheme.Button.primaryTextColor else EInkTheme.Colors.darkGray,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
