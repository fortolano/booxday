package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.TaskAiService
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * Task Preview Dialog - Review parsed voice tasks before creating them.
 *
 * Features:
 * - Default dates card for all tasks (target date + due date)
 * - Expandable task rows with per-task date customization
 * - Checkbox to select/deselect tasks
 * - Priority dropdown (A/B/C/none)
 * - Delete button to remove individual tasks
 *
 * UI pattern copied from MemoToTasksDialog.kt for consistency.
 * E-ink optimized: black/white, large touch targets, no animations.
 */
@Composable
fun TaskPreviewDialog(
    parsedResult: TaskAiService.VoiceTaskResult,
    currentDate: LocalDate,
    onCreateTasks: (List<TaskToCreate>) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    // Default dates (shared across all tasks)
    var defaultTargetDate by remember { mutableStateOf(currentDate.toString()) }  // yyyy-MM-dd
    var defaultDueDate by remember { mutableStateOf<Long?>(null) }

    // Which task is expanded (only one at a time)
    var expandedTaskIndex by remember { mutableStateOf<Int?>(null) }

    // Task list with selection state
    var tasks by remember {
        mutableStateOf(
            parsedResult.tasks.map { parsed ->
                TaskToCreate(
                    text = parsed.text,
                    date = parsed.date?.let {
                        try { LocalDate.parse(it) } catch (e: Exception) { null }
                    } ?: currentDate,
                    priority = parsed.priority?.uppercase()?.firstOrNull()?.toString() ?: "",
                    isSelected = true,
                    targetDateOverride = null,
                    dueDateOverride = null
                )
            }
        )
    }

    // Calculate selected count
    val selectedCount = tasks.count { it.isSelected }

    // Track scroll state for the content list
    val scrollState = rememberLazyListState()

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)  // Match MemoToTasksDialog width (Oct 2026: was 560 dp)
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
                        text = "Voice to Tasks",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Review and customize tasks from your voice input",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(Modifier.height(16.dp))

                if (tasks.isEmpty()) {
                    // No tasks state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks to create",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    EinkSecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // ═══════════════════════════════════════════════════════════════
                    // FIXED HEADER: Default Dates Card (always visible)
                    // ═══════════════════════════════════════════════════════════════
                    DefaultDatesCard(
                        targetDate = defaultTargetDate,
                        dueDate = defaultDueDate,
                        onTargetDateSelected = { defaultTargetDate = it },
                        onDueDateSelected = { defaultDueDate = it },
                        dateFormat = dateFormat,
                        displayDateFormat = displayDateFormat
                    )

                    Spacer(Modifier.height(12.dp))

                    // Tasks list header (fixed)
                    Text(
                        text = "Select tasks to add ($selectedCount selected)",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )

                    Spacer(Modifier.height(6.dp))

                    // ═══════════════════════════════════════════════════════════════
                    // SCROLLABLE SECTION: Task items only
                    // ═══════════════════════════════════════════════════════════════
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Task items with expandable per-task date customization
                        itemsIndexed(tasks) { index, task ->
                            ExpandableTaskItem(
                                task = task,
                                isExpanded = expandedTaskIndex == index,
                                defaultTargetDate = defaultTargetDate,
                                defaultDueDate = defaultDueDate,
                                onToggleSelection = {
                                    tasks = tasks.toMutableList().also {
                                        it[index] = it[index].copy(isSelected = !it[index].isSelected)
                                    }
                                },
                                onToggleExpand = {
                                    expandedTaskIndex = if (expandedTaskIndex == index) null else index
                                },
                                onTargetDateChange = { date ->
                                    tasks = tasks.toMutableList().also {
                                        it[index] = it[index].copy(targetDateOverride = date)
                                    }
                                },
                                onDueDateChange = { date ->
                                    tasks = tasks.toMutableList().also {
                                        it[index] = it[index].copy(dueDateOverride = date)
                                    }
                                },
                                onPriorityChange = { priority ->
                                    tasks = tasks.toMutableList().also {
                                        it[index] = it[index].copy(priority = priority)
                                    }
                                },
                                onDelete = {
                                    tasks = tasks.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                    // Adjust expanded index if needed
                                    if (expandedTaskIndex == index) {
                                        expandedTaskIndex = null
                                    } else if (expandedTaskIndex != null && expandedTaskIndex!! > index) {
                                        expandedTaskIndex = expandedTaskIndex!! - 1
                                    }
                                },
                                dateFormat = dateFormat,
                                displayDateFormat = displayDateFormat
                            )
                        }

                        // Bottom padding
                        item {
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // ═══════════════════════════════════════════════════════════════
                    // FIXED FOOTER: Action buttons
                    // ═══════════════════════════════════════════════════════════════
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EInkTheme.Colors.background)
                            .drawBehind {
                                drawLine(
                                    color = EInkTheme.Colors.lightGray,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EinkSecondaryButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )

                            EinkPrimaryButton(
                                text = "Add $selectedCount Task${if (selectedCount != 1) "s" else ""}",
                                onClick = {
                                    // Apply default dates to tasks without overrides
                                    val finalTasks = tasks.map { task ->
                                        task.copy(
                                            date = task.targetDateOverride?.let {
                                                try { LocalDate.parse(it) } catch (e: Exception) { null }
                                            } ?: LocalDate.parse(defaultTargetDate),
                                            // Apply due date: use override if set, otherwise use default
                                            dueDateOverride = task.dueDateOverride ?: defaultDueDate
                                        )
                                    }
                                    onCreateTasks(finalTasks.filter { it.isSelected && it.text.isNotBlank() })
                                },
                                enabled = selectedCount > 0,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing default dates that apply to all tasks.
 * Contains compact dropdowns for target date and due date.
 */
@Composable
private fun DefaultDatesCard(
    targetDate: String,
    dueDate: Long?,
    onTargetDateSelected: (String) -> Unit,
    onDueDateSelected: (Long?) -> Unit,
    dateFormat: SimpleDateFormat,
    displayDateFormat: SimpleDateFormat
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
            .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "DEFAULT DATES",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.darkGray,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Applies to all tasks unless customized",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                DateDropdown(
                    dateString = targetDate,
                    onDateSelected = { dateStr -> dateStr?.let { onTargetDateSelected(it) } },
                    dateFormat = dateFormat,
                    displayDateFormat = displayDateFormat,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Due:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                DateDropdown(
                    dateString = dueDate?.let { dateFormat.format(java.util.Date(it)) },
                    onDateSelected = { dateStr ->
                        if (dateStr == null) {
                            onDueDateSelected(null)
                        } else {
                            try {
                                val date = dateFormat.parse(dateStr)
                                onDueDateSelected(date?.time)
                            } catch (e: Exception) {
                                onDueDateSelected(null)
                            }
                        }
                    },
                    dateFormat = dateFormat,
                    displayDateFormat = displayDateFormat,
                    allowNone = true,
                    minDateString = targetDate,  // Due date must be >= target date
                    baseDateString = targetDate, // Show the week of target date, with same day selected
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Tip: Tap a task below to set different dates",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

/**
 * Compact date dropdown that shows a popup picker when tapped.
 * @param minDateString If set, dates before this are not selectable (for due date validation)
 * @param baseDateString For due date pickers, this is the target/"Add to" date.
 *        The picker will initially show the week containing this date.
 */
@Composable
private fun DateDropdown(
    dateString: String?,  // null = "No due date"
    onDateSelected: (String?) -> Unit,
    dateFormat: SimpleDateFormat,
    displayDateFormat: SimpleDateFormat,
    allowNone: Boolean = false,
    minDateString: String? = null,  // Minimum selectable date (yyyy-MM-dd format)
    baseDateString: String? = null, // Date to anchor week display (for due date pickers)
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(false) }

    // Helper to check if a date is valid (not before minDate)
    val isDateValid: (String) -> Boolean = { selectedDate ->
        if (minDateString == null) {
            true
        } else {
            try {
                val selected = dateFormat.parse(selectedDate)
                val min = dateFormat.parse(minDateString)
                selected != null && min != null && !selected.before(min)
            } catch (e: Exception) {
                true  // Allow on parse error
            }
        }
    }

    // Format display text
    val displayText = if (dateString == null) {
        "None"
    } else {
        try {
            val date = dateFormat.parse(dateString)
            if (date != null) displayDateFormat.format(date) else dateString
        } catch (e: Exception) {
            dateString
        }
    }

    Box(modifier = modifier) {
        // Dropdown button - larger touch target and font for e-ink
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.white, RoundedCornerShape(4.dp))
                .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(4.dp))
                .noRippleClickable { showPopup = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayText,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = if (dateString == null) EInkTheme.Colors.gray else EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select date",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(18.dp)
            )
        }

        // Date picker popup
        if (showPopup) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(340.dp)  // 7 days × 44dp + 24dp padding + spacing = 340dp
                        .background(EInkTheme.Colors.white, RoundedCornerShape(8.dp))
                        .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        // "None" option for due dates
                        if (allowNone) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable {
                                        onDateSelected(null)
                                        showPopup = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            width = if (dateString == null) 6.dp else 2.dp,
                                            color = if (dateString == null) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "No due date",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = if (dateString == null) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(EInkTheme.Colors.lightGray)
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        CompactWeekDatePicker(
                            selectedDate = dateString ?: baseDateString ?: dateFormat.format(java.util.Date()),
                            baseDateString = baseDateString,  // For due date pickers, show the target date's week
                            minDateString = minDateString,
                            onDateSelected = { selected ->
                                // Only accept if date is valid (not before minDate)
                                if (isDateValid(selected)) {
                                    onDateSelected(selected)
                                    showPopup = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expandable task item with per-task date and priority customization.
 * Includes trash icon for deletion (different from MemoToTasksDialog).
 */
@Composable
private fun ExpandableTaskItem(
    task: TaskToCreate,
    isExpanded: Boolean,
    defaultTargetDate: String,
    defaultDueDate: Long?,
    onToggleSelection: () -> Unit,
    onToggleExpand: () -> Unit,
    onTargetDateChange: (String?) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onPriorityChange: (String) -> Unit,
    onDelete: () -> Unit,
    dateFormat: SimpleDateFormat,
    displayDateFormat: SimpleDateFormat
) {
    val hasCustomDates = task.targetDateOverride != null || task.dueDateOverride != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.Colors.white,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                if (task.isSelected) 2.dp else 1.dp,
                if (task.isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            // Main row: checkbox, priority dropdown, content, expand arrow, delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .noRippleClickable { onToggleSelection() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (task.isSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (task.isSelected) "Selected" else "Not selected",
                        tint = if (task.isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Priority dropdown
                PriorityDropdown(
                    currentPriority = task.priority ?: "",
                    onPriorityChange = onPriorityChange
                )

                // Task content
                Column(modifier = Modifier.weight(1f)) {
                    // Task text
                    Text(
                        text = task.text,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Status hint
                    Text(
                        text = if (hasCustomDates) {
                            "Custom dates set"
                        } else {
                            "Using defaults \u00B7 Tap to customize"
                        },
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = if (hasCustomDates) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expand/collapse arrow
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .noRippleClickable { onToggleExpand() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Delete button (voice tasks only - not in MemoToTasksDialog)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = EInkTheme.Colors.gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded section: per-task date pickers
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add to:",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )

                        DateDropdown(
                            dateString = task.targetDateOverride ?: defaultTargetDate,
                            onDateSelected = { date ->
                                // If same as default, clear override
                                onTargetDateChange(if (date == defaultTargetDate) null else date)
                            },
                            dateFormat = dateFormat,
                            displayDateFormat = displayDateFormat,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "Due:",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )

                        DateDropdown(
                            dateString = (task.dueDateOverride ?: defaultDueDate)?.let {
                                dateFormat.format(java.util.Date(it))
                            },
                            onDateSelected = { dateStr ->
                                if (dateStr == null) {
                                    // User selected "None" - clear override to use default
                                    onDueDateChange(null)
                                } else {
                                    try {
                                        val date = dateFormat.parse(dateStr)
                                        onDueDateChange(date?.time)
                                    } catch (e: Exception) {
                                        onDueDateChange(null)
                                    }
                                }
                            },
                            dateFormat = dateFormat,
                            displayDateFormat = displayDateFormat,
                            allowNone = true,
                            minDateString = task.targetDateOverride ?: defaultTargetDate,  // Due date >= target date
                            baseDateString = task.targetDateOverride ?: defaultTargetDate, // Show target date's week
                            modifier = Modifier.weight(1f)
                        )

                        // Reset button
                        if (hasCustomDates) {
                            Text(
                                text = "Reset",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.noRippleClickable {
                                    onTargetDateChange(null)
                                    onDueDateChange(null)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Priority dropdown for selecting task priority (A, B, C, or none).
 */
@Composable
private fun PriorityDropdown(
    currentPriority: String,
    onPriorityChange: (String) -> Unit
) {
    var showPopup by remember { mutableStateOf(false) }

    val priorities = listOf("A", "B", "C", "")
    val displayText = if (currentPriority.isBlank()) "-" else currentPriority

    val backgroundColor = when (currentPriority) {
        "A" -> EInkTheme.Colors.primary
        "B" -> EInkTheme.Colors.darkGray
        "C" -> EInkTheme.Colors.gray
        else -> EInkTheme.Colors.lightGray
    }

    val textColor = if (currentPriority.isBlank()) EInkTheme.Colors.darkGray else EInkTheme.Colors.white

    Box {
        // Priority button
        Row(
            modifier = Modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(4.dp))
                .noRippleClickable { showPopup = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayText,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select priority",
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
        }

        // Priority picker popup - compact size
        if (showPopup) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(70.dp)  // Compact width for priority options
                        .background(EInkTheme.Colors.white, RoundedCornerShape(4.dp))
                        .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    priorities.forEach { priority ->
                        val isSelected = priority == currentPriority
                        val itemBg = when (priority) {
                            "A" -> EInkTheme.Colors.primary
                            "B" -> EInkTheme.Colors.darkGray
                            "C" -> EInkTheme.Colors.gray
                            else -> if (isSelected) EInkTheme.Colors.offWhite else Color.Transparent
                        }
                        val itemTextColor = if (priority.isBlank()) EInkTheme.Colors.darkGray else EInkTheme.Colors.white

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBg, RoundedCornerShape(4.dp))
                                .noRippleClickable {
                                    onPriorityChange(priority)
                                    showPopup = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (priority.isBlank()) "None" else priority,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = itemTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact week strip date picker.
 * Shows 7 days with navigation arrows to move week forward/backward.
 *
 * Layout: ← [S] [M] [T] [W] [T] [F] [S] →
 *
 * @param selectedDate The currently selected date (yyyy-MM-dd format)
 * @param baseDateString The date to use for initial week display. For due date pickers,
 *        this should be the target/"Add to" date so users see the relevant week.
 *        Defaults to selectedDate if not specified.
 * @param minDateString Dates before this are disabled (for due date validation)
 * @param onDateSelected Callback when a date is selected
 */
@Composable
private fun CompactWeekDatePicker(
    selectedDate: String,
    baseDateString: String? = null,  // Date to anchor week display (defaults to selectedDate)
    minDateString: String? = null,   // Dates before this are disabled
    onDateSelected: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dayNameFormat = remember { SimpleDateFormat("EEE", Locale.US) }
    val dayNumFormat = remember { SimpleDateFormat("d", Locale.US) }

    // Parse minDate once
    val minDate = remember(minDateString) {
        minDateString?.let {
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        }
    }

    // Determine the anchor date for week display:
    // - For due date pickers: use baseDateString (the target/"Add to" date)
    // - For regular pickers: use selectedDate
    val anchorDateString = baseDateString ?: selectedDate

    // Capture the INITIAL date once - this is the anchor for week navigation.
    // Important: Do NOT recompute when selectedDate changes, otherwise
    // tapping a day causes the calendar to jump (weekOffset applied to new base).
    val initialAnchorDate = remember(anchorDateString) {
        Calendar.getInstance().apply {
            try {
                time = dateFormat.parse(anchorDateString) ?: time
            } catch (e: Exception) { }
            // Move to start of week (Sunday) for consistent anchor
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
    }

    // Track which week we're displaying (offset from initial anchor)
    var weekOffset by remember { mutableStateOf(0) }

    // Calculate the start of the week for display - based on FIXED anchor, not selectedDate
    val weekStartCalendar = remember(weekOffset) {
        Calendar.getInstance().apply {
            time = initialAnchorDate.time
            // Apply week offset from the fixed anchor
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }
    }

    // Generate 7 days for the week
    val weekDays = remember(weekStartCalendar.timeInMillis, minDate) {
        (0..6).map { dayOffset ->
            val dayCal = Calendar.getInstance().apply {
                time = weekStartCalendar.time
                add(Calendar.DAY_OF_MONTH, dayOffset)
            }
            val dayDate = dayCal.time
            val isDisabled = minDate != null && dayDate.before(minDate)
            WeekDayInfo(
                dateString = dateFormat.format(dayDate),
                dayName = dayNameFormat.format(dayDate).first().uppercaseChar().toString(),
                dayNum = dayNumFormat.format(dayDate),
                isToday = isSameDay(dayCal, Calendar.getInstance()),
                isDisabled = isDisabled
            )
        }
    }

    // Month/Year label for current week
    val monthYearLabel = remember(weekStartCalendar.timeInMillis) {
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)
        monthFormat.format(weekStartCalendar.time)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Month/year label with navigation - larger touch targets for e-ink
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { weekOffset-- },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = monthYearLabel,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )

            IconButton(
                onClick = { weekOffset++ },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Week days row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekDays.forEach { dayInfo ->
                WeekDayCell(
                    dayInfo = dayInfo,
                    isSelected = dayInfo.dateString == selectedDate,
                    onSelect = { onDateSelected(dayInfo.dateString) }
                )
            }
        }
    }
}

private data class WeekDayInfo(
    val dateString: String,
    val dayName: String,
    val dayNum: String,
    val isToday: Boolean,
    val isDisabled: Boolean = false  // Dates before minDate
)

@Composable
private fun WeekDayCell(
    dayInfo: WeekDayInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = when {
        dayInfo.isDisabled -> Color.Transparent
        isSelected -> EInkTheme.Colors.primary
        dayInfo.isToday -> EInkTheme.Colors.offWhite
        else -> Color.Transparent
    }

    // Use EInkTheme.Colors.gray (#6A6A6A) for disabled - visible on e-ink
    // NOT lightGray (#CFCFCF) which is invisible on e-ink displays
    val textColor = when {
        dayInfo.isDisabled -> EInkTheme.Colors.gray
        isSelected -> EInkTheme.Colors.white
        else -> EInkTheme.Colors.primary
    }

    // Larger cells for better e-ink touch accuracy (minimum 44dp)
    Column(
        modifier = Modifier
            .width(44.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .then(
                if (dayInfo.isToday && !isSelected && !dayInfo.isDisabled) {
                    Modifier.border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                } else Modifier
            )
            .then(
                if (dayInfo.isDisabled) Modifier else Modifier.noRippleClickable { onSelect() }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day name (S, M, T, etc.)
        Text(
            text = dayInfo.dayName,
            fontSize = EInkTheme.Typography.bodySmall,
            color = if (dayInfo.isDisabled) EInkTheme.Colors.gray
                    else if (isSelected) EInkTheme.Colors.white
                    else EInkTheme.Colors.darkGray
        )

        Spacer(Modifier.height(4.dp))

        // Day number - strikethrough when disabled
        Text(
            text = dayInfo.dayNum,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = if (dayInfo.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textDecoration = if (dayInfo.isDisabled) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Data class for a task to be created.
 */
data class TaskToCreate(
    val text: String,
    val date: LocalDate,
    val priority: String? = null,           // "A", "B", "C", or null/empty
    val isSelected: Boolean = true,         // Checkbox state
    val targetDateOverride: String? = null, // Per-task "Add to" date (yyyy-MM-dd)
    val dueDateOverride: Long? = null       // Per-task due date (epoch ms)
)
