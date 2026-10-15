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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.AppRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Export scope options for task export.
 */
enum class TaskExportScope {
    CURRENT_PAGE,   // Tasks from the current task page only
    TODAY,          // All tasks for today
    THIS_MONTH,     // All tasks for the current month
    ALL_TASKS       // All tasks across all dates
}

/**
 * Export format options for task export.
 */
enum class TaskExportFormat {
    TEXT,   // Plain text with checkboxes
    CSV     // Spreadsheet-compatible
}

/**
 * Export action options.
 */
enum class TaskExportAction {
    CLIPBOARD,  // Copy to clipboard
    SHARE       // Share via Android share sheet
}

/**
 * Redesigned single-step export dialog for tasks.
 *
 * Features:
 * - 2x2 scope grid with task counts
 * - Large option cards with Material icons for Format and Export To
 * - Live preview line showing exactly what will be exported
 * - Smart defaults (Today, Text, Clipboard)
 * - Full accessibility support with contentDescription on all icons
 *
 * @param dayDate Current day date in "yyyy-MM-dd" format
 * @param currentTaskPageIndex Current task page index (for "Current Page" scope)
 * @param canvasHeight Canvas height in pixels (for stroke detection in handwriting-only tasks)
 * @param onExport Callback when export is confirmed with scope, format, and action
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun TaskExportDialog(
    dayDate: String,
    currentTaskPageIndex: Int,
    canvasHeight: Float,
    onExport: (scope: TaskExportScope, format: TaskExportFormat, action: TaskExportAction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Default selections for best UX
    var selectedScope by remember { mutableStateOf(TaskExportScope.TODAY) }
    var selectedFormat by remember { mutableStateOf(TaskExportFormat.TEXT) }
    var selectedAction by remember { mutableStateOf(TaskExportAction.CLIPBOARD) }

    // Scope summaries (loaded asynchronously) - null means loading
    var scopeCounts by remember { mutableStateOf<Map<TaskExportScope, Int?>>(
        mapOf(
            TaskExportScope.CURRENT_PAGE to null,
            TaskExportScope.TODAY to null,
            TaskExportScope.THIS_MONTH to null,
            TaskExportScope.ALL_TASKS to null
        )
    )}

    // Load summaries on mount
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val taskRepo = AppRepository(context).taskRepository
            val today = LocalDate.now().toString()
            val monthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

            val currentPage = taskRepo.getExportSummaryForPage(dayDate, currentTaskPageIndex, canvasHeight)
            val todaySummary = taskRepo.getExportSummaryForDay(today, canvasHeight)
            val monthSummary = taskRepo.getExportSummaryForMonth(monthPrefix, canvasHeight)
            val allSummary = taskRepo.getExportSummaryForAll(canvasHeight)

            scopeCounts = mapOf(
                TaskExportScope.CURRENT_PAGE to currentPage?.taskCount,
                TaskExportScope.TODAY to todaySummary?.taskCount,
                TaskExportScope.THIS_MONTH to monthSummary?.taskCount,
                TaskExportScope.ALL_TASKS to allSummary?.taskCount
            )
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.TaskExport.dialogWidthFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.sectionSpacing)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.export_tasks_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )

                // Section: What to Export (2x2 grid)
                SectionLabel(stringResource(R.string.export_section_scope))
                ScopeGrid(
                    selected = selectedScope,
                    onSelect = { selectedScope = it },
                    counts = scopeCounts
                )

                // Section: Format
                SectionLabel(stringResource(R.string.export_section_format))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.optionCardGap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptionCard(
                        icon = Icons.Default.Description,
                        iconContentDescription = stringResource(R.string.export_icon_text_format),
                        label = stringResource(R.string.export_format_text),
                        subtitle = stringResource(R.string.export_format_text_desc),
                        selected = selectedFormat == TaskExportFormat.TEXT,
                        onClick = { selectedFormat = TaskExportFormat.TEXT },
                        modifier = Modifier.weight(1f)
                    )
                    OptionCard(
                        icon = Icons.Default.TableChart,
                        iconContentDescription = stringResource(R.string.export_icon_csv_format),
                        label = stringResource(R.string.export_format_csv),
                        subtitle = stringResource(R.string.export_format_csv_desc),
                        selected = selectedFormat == TaskExportFormat.CSV,
                        onClick = { selectedFormat = TaskExportFormat.CSV },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Format hint text
                Text(
                    text = stringResource(R.string.export_section_format_hint),
                    fontSize = EInkTheme.TaskExport.sectionLabelFontSize,
                    color = EInkTheme.TaskExport.sectionLabelColor,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Section: Destination
                SectionLabel(stringResource(R.string.export_section_destination))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.optionCardGap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptionCard(
                        icon = Icons.Default.ContentCopy,
                        iconContentDescription = stringResource(R.string.export_icon_clipboard),
                        label = stringResource(R.string.export_action_clipboard),
                        subtitle = stringResource(R.string.export_action_clipboard_desc),
                        selected = selectedAction == TaskExportAction.CLIPBOARD,
                        onClick = { selectedAction = TaskExportAction.CLIPBOARD },
                        modifier = Modifier.weight(1f)
                    )
                    OptionCard(
                        icon = Icons.Default.Share,
                        iconContentDescription = stringResource(R.string.export_icon_share),
                        label = stringResource(R.string.export_action_share),
                        subtitle = stringResource(R.string.export_action_share_desc),
                        selected = selectedAction == TaskExportAction.SHARE,
                        onClick = { selectedAction = TaskExportAction.SHARE },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Preview line
                PreviewLine(
                    scope = selectedScope,
                    format = selectedFormat,
                    taskCount = scopeCounts[selectedScope]
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Button row: Cancel (40%) - gap (20%) - Export Tasks (40%)
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Cancel button (outlined style) - 40%
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .border(
                                EInkTheme.ModernDialog.OptionCard.borderWidth,
                                EInkTheme.ModernDialog.CancelLink.textColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = EInkTheme.ModernDialog.CancelLink.textColor,
                            fontWeight = FontWeight.Normal,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }

                    // Gap in the middle - 20%
                    Spacer(modifier = Modifier.weight(0.2f))

                    // Export Tasks button (filled dark gray) - 40%
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .background(
                                EInkTheme.ModernDialog.PrimaryButton.background,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onExport(selectedScope, selectedFormat, selectedAction) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.export_tasks_button),
                            color = EInkTheme.ModernDialog.PrimaryButton.textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gray section label above each section.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.TaskExport.sectionLabelFontSize,
        color = EInkTheme.TaskExport.sectionLabelColor,
        fontWeight = FontWeight.Medium
    )
}

/**
 * 2x2 grid for scope selection with radio indicators.
 */
@Composable
private fun ScopeGrid(
    selected: TaskExportScope,
    onSelect: (TaskExportScope) -> Unit,
    counts: Map<TaskExportScope, Int?>
) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.scopeGridGap)) {
        // Row 1: This Page | Today
        Row(
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.scopeGridGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            ScopeCard(
                label = stringResource(R.string.export_scope_current_page),
                taskCount = counts[TaskExportScope.CURRENT_PAGE],
                selected = selected == TaskExportScope.CURRENT_PAGE,
                onClick = { onSelect(TaskExportScope.CURRENT_PAGE) },
                modifier = Modifier.weight(1f)
            )
            ScopeCard(
                label = stringResource(R.string.export_scope_today),
                taskCount = counts[TaskExportScope.TODAY],
                selected = selected == TaskExportScope.TODAY,
                onClick = { onSelect(TaskExportScope.TODAY) },
                modifier = Modifier.weight(1f)
            )
        }
        // Row 2: This Month | All Tasks
        Row(
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.TaskExport.scopeGridGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            ScopeCard(
                label = stringResource(R.string.export_scope_this_month),
                taskCount = counts[TaskExportScope.THIS_MONTH],
                selected = selected == TaskExportScope.THIS_MONTH,
                onClick = { onSelect(TaskExportScope.THIS_MONTH) },
                modifier = Modifier.weight(1f)
            )
            ScopeCard(
                label = stringResource(R.string.export_scope_all),
                taskCount = counts[TaskExportScope.ALL_TASKS],
                selected = selected == TaskExportScope.ALL_TASKS,
                onClick = { onSelect(TaskExportScope.ALL_TASKS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Single scope card with radio indicator and task count.
 */
@Composable
private fun ScopeCard(
    label: String,
    taskCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderWidth = if (selected) EInkTheme.TaskExport.scopeCardSelectedBorderWidth
                      else EInkTheme.TaskExport.scopeCardBorderWidth
    val borderColor = if (selected) EInkTheme.TaskExport.scopeCardSelectedBorderColor
                      else EInkTheme.TaskExport.scopeCardBorderColor
    val background = if (selected) EInkTheme.TaskExport.scopeCardSelectedBackground
                     else EInkTheme.Colors.background

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(EInkTheme.TaskExport.scopeCardHeight)
            .background(background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.TaskExport.scopeCardPadding)
    ) {
        // Radio indicator
        RadioIndicator(selected = selected)
        Spacer(modifier = Modifier.width(8.dp))
        // Label and count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = EInkTheme.TaskExport.scopeLabelFontSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = EInkTheme.Colors.primary
            )
            // Task count or loading placeholder
            Text(
                text = taskCount?.let { stringResource(R.string.export_tasks_count, it) }
                       ?: stringResource(R.string.export_loading_placeholder),
                fontSize = EInkTheme.TaskExport.scopeCountFontSize,
                color = EInkTheme.TaskExport.scopeCountColor
            )
        }
    }
}

/**
 * Custom radio indicator (square with fill when selected).
 */
@Composable
private fun RadioIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(EInkTheme.TaskExport.radioSize)
            .border(
                EInkTheme.TaskExport.radioBorderWidth,
                EInkTheme.ModernDialog.RadioIndicator.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
            )
            .padding(EInkTheme.TaskExport.radioInnerPadding)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        EInkTheme.ModernDialog.RadioIndicator.fillColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
                    )
            )
        }
    }
}

/**
 * Large option card with icon, label, and subtitle.
 * Used for Format and Export To sections.
 */
@Composable
private fun OptionCard(
    icon: ImageVector,
    iconContentDescription: String,
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderWidth = if (selected) EInkTheme.TaskExport.scopeCardSelectedBorderWidth
                      else EInkTheme.TaskExport.scopeCardBorderWidth
    val borderColor = if (selected) EInkTheme.TaskExport.scopeCardSelectedBorderColor
                      else EInkTheme.TaskExport.scopeCardBorderColor
    val background = if (selected) EInkTheme.TaskExport.scopeCardSelectedBackground
                     else EInkTheme.Colors.background

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            // At least the card height, taller when the text needs it (Oct 2026: the subtitle was cut
            // off at the Go 6's larger font scale)
            .heightIn(min = EInkTheme.TaskExport.optionCardHeight)
            .background(background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(EInkTheme.TaskExport.optionCardPadding)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(EInkTheme.TaskExport.optionIconSize),
            tint = EInkTheme.Colors.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = EInkTheme.TaskExport.optionLabelFontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = subtitle,
            fontSize = EInkTheme.TaskExport.optionSubtitleFontSize,
            color = EInkTheme.TaskExport.optionSubtitleColor
        )
    }
}

/**
 * Preview line showing what will be exported.
 * Example: "8 tasks from Today, formatted as Text"
 */
@Composable
private fun PreviewLine(
    scope: TaskExportScope,
    format: TaskExportFormat,
    taskCount: Int?
) {
    val scopeLabel = when (scope) {
        TaskExportScope.CURRENT_PAGE -> stringResource(R.string.export_scope_current_page)
        TaskExportScope.TODAY -> stringResource(R.string.export_scope_today)
        TaskExportScope.THIS_MONTH -> stringResource(R.string.export_scope_this_month)
        TaskExportScope.ALL_TASKS -> stringResource(R.string.export_scope_all)
    }
    val formatLabel = when (format) {
        TaskExportFormat.TEXT -> stringResource(R.string.export_format_text)
        TaskExportFormat.CSV -> stringResource(R.string.export_format_csv)
    }

    val previewText = if (taskCount != null) {
        stringResource(R.string.export_preview_template, taskCount, scopeLabel, formatLabel)
    } else {
        stringResource(R.string.export_loading_placeholder)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.TaskExport.previewBackground,
                RoundedCornerShape(EInkTheme.TaskExport.previewCornerRadius)
            )
            .padding(
                horizontal = EInkTheme.TaskExport.previewPaddingHorizontal,
                vertical = EInkTheme.TaskExport.previewPaddingVertical
            )
    ) {
        Text(
            text = previewText,
            fontSize = EInkTheme.TaskExport.previewFontSize,
            color = EInkTheme.TaskExport.previewColor
        )
    }
}
