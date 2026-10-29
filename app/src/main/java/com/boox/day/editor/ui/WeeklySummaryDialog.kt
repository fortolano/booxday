package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
 * Weekly Summary Dialog - 7-day review showing:
 * - Overall completion stats
 * - Best/worst day identification
 * - Daily breakdown
 * - Recurring incomplete tasks
 * - Work-life balance by category
 * - AI-generated weekly insights
 *
 * E-ink optimized: black/white, large touch targets (48dp min), large fonts.
 */
@Composable
fun WeeklySummaryDialog(
    summary: TaskAiService.WeeklySummary?,
    processingState: WeeklySummaryProcessingState,
    cacheInfo: WeeklySummaryCacheInfo? = null,
    onRefresh: (() -> Unit)? = null,
    onBreakDownTask: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            if (processingState !is WeeklySummaryProcessingState.Processing) {
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = processingState !is WeeklySummaryProcessingState.Processing,
            dismissOnClickOutside = processingState !is WeeklySummaryProcessingState.Processing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.ReportDialog.width)
                .heightIn(max = EInkTheme.ReportDialog.maxHeight)
                .background(EInkTheme.ReportDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ReportDialog.borderWidth, EInkTheme.ReportDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ReportDialog.padding)
            ) {
                // Header with proper touch target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Summary",
                        fontSize = EInkTheme.Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )

                    if (processingState !is WeeklySummaryProcessingState.Processing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                        ) {
                            if (onRefresh != null) {
                                Box(
                                    modifier = Modifier
                                        .height(EInkTheme.Global.touchTarget)
                                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .padding(horizontal = EInkTheme.Global.paddingSmall)
                                        .noRippleClickable { onRefresh() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Refresh",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.primary
                                    )
                                }
                            }

                            // Close button with proper 48dp touch target
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
                    }
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                when (processingState) {
                    is WeeklySummaryProcessingState.Processing -> {
                        // Processing state with large readable text
                        val stageMessage = processingState.stageMessage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.paddingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingLarge)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomReviewMessage() }
                            )

                            // Technical Stage (Secondary)
                            Text(
                                text = stageMessage,
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.darkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is WeeklySummaryProcessingState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.paddingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                        ) {
                            Text(
                                text = "Could not generate weekly summary",
                                fontSize = EInkTheme.Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(EInkTheme.Button.height)
                                    .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                    .noRippleClickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Close",
                                    color = EInkTheme.Button.textColor,
                                    fontSize = EInkTheme.Typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    is WeeklySummaryProcessingState.Ready -> {
                        if (summary == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = EInkTheme.Global.paddingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No tasks in the past 7 days",
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.darkGray
                                )

                                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(EInkTheme.Button.height)
                                        .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                        .noRippleClickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Close",
                                        color = EInkTheme.Button.textColor,
                                        fontSize = EInkTheme.Typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            WeeklySummaryContent(
                                modifier = Modifier.weight(1f, fill = false),
                                summary = summary,
                                cacheInfo = cacheInfo,
                                onBreakDownTask = onBreakDownTask,
                                onDone = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStageHint(stageMessage: String): String {
    return when {
        stageMessage.contains("Gathering", ignoreCase = true) ->
            "Loading tasks from the past week..."
        stageMessage.contains("Analyzing", ignoreCase = true) ->
            "Calculating completion rates per day..."
        stageMessage.contains("Detecting", ignoreCase = true) || stageMessage.contains("recurring", ignoreCase = true) ->
            "Finding tasks that keep appearing..."
        stageMessage.contains("Generating", ignoreCase = true) || stageMessage.contains("insights", ignoreCase = true) ->
            "AI is analyzing patterns and trends..."
        else ->
            "Preparing your weekly review..."
    }
}

@Composable
private fun WeeklySummaryContent(
    modifier: Modifier = Modifier,
    summary: TaskAiService.WeeklySummary,
    cacheInfo: WeeklySummaryCacheInfo?,
    onBreakDownTask: ((String) -> Unit)?,
    onDone: () -> Unit
) {
    // Scroll state
    val scrollState = rememberLazyListState()
    val showScrollIndicator by remember {
        derivedStateOf {
            val lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = scrollState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index < totalItems - 1
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Fixed height content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)  // Oct 2026: keeps its height where it fits, shrinks so the buttons stay on a small screen
                .height(520.dp) // Fixed height for consistency (increased for ReportDialog)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (showScrollIndicator) 24.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                if (!summary.warning.isNullOrBlank()) {
                    item {
                        WarningBanner(summary.warning!!)
                    }
                }

                // Stats bar
                item {
                    WeeklyStatsBar(summary, cacheInfo)
                }

                // Best/Worst day indicators
                item {
                    BestWorstDayRow(summary)
                }

                // Daily breakdown
                if (summary.dailyStats.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                        CollapsibleSection(title = "Daily Breakdown") {
                            DailyBreakdownContent(summary.dailyStats)
                        }
                    }
                }

                // Recurring incomplete tasks
                if (summary.recurringIncompleteTasks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                        CollapsibleSection(
                            title = "Needs Attention",
                            subtitle = "${summary.recurringIncompleteTasks.size} recurring"
                        ) {
                            RecurringTasksContent(summary.recurringIncompleteTasks, onBreakDownTask)
                        }
                    }
                }

                // Category breakdown
                if (summary.categoryBreakdown.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                        CollapsibleSection(title = "Work-Life Balance") {
                            CategoryBreakdownContent(summary.categoryBreakdown)
                        }
                    }
                }

                // AI Insights
                if (summary.aiInsights.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(EInkTheme.Global.spacing))
                        Text(
                            text = "Insights",
                            fontSize = EInkTheme.Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    items(summary.aiInsights) { insight ->
                        InsightItem(insight)
                    }
                }
            }

            // Scroll indicator - E-Ink optimized
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

        // Done button
        Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.Button.primaryBorderWidth, EInkTheme.Button.primaryBorderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .noRippleClickable { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Done",
                color = EInkTheme.Button.primaryTextColor,
                fontSize = EInkTheme.Typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeeklyStatsBar(
    summary: TaskAiService.WeeklySummary,
    cacheInfo: WeeklySummaryCacheInfo?
) {
    val percentage = (summary.completionRate * 100).toInt()
    val cacheLabel = cacheInfo?.let {
        val base = if (it.isCached) "Cached" else "Generated"
        "$base ${formatTimeAgo(it.generatedAt)}"
    }
    val dataLabel = cacheInfo?.lastUpdatedAt?.let {
        "Data updated ${formatTimeAgo(it)}"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.padding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = summary.weekRange,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                if (cacheLabel != null) {
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = cacheLabel,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                if (dataLabel != null) {
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = dataLabel,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                Text(
                    text = "${summary.totalCompleted} of ${summary.totalTasks} completed",
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }

            // Percentage badge - large and readable
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        if (percentage >= 80) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$percentage%",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.white
                )
            }
        }
    }
}

@Composable
private fun BestWorstDayRow(summary: TaskAiService.WeeklySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        // Best day
        summary.bestDay?.let { best ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(EInkTheme.Global.padding)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Best",
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Best Day",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = best.dayOfWeek,
                        fontSize = EInkTheme.Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "${best.completed}/${best.total} (${(best.completionRate * 100).toInt()}%)",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }
        }

        // Worst day
        summary.worstDay?.let { worst ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(EInkTheme.Global.padding)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Toughest",
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Toughest Day",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = worst.dayOfWeek,
                        fontSize = EInkTheme.Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "${worst.completed}/${worst.total} (${(worst.completionRate * 100).toInt()}%)",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column {
        // Header row with proper touch target (48dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .noRippleClickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                Text(
                    text = title,
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                subtitle?.let {
                    Box(
                        modifier = Modifier
                            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = it,
                            fontSize = EInkTheme.Typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }
            }

            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(EInkTheme.Global.iconSize),
                tint = EInkTheme.Colors.darkGray
            )
        }

        if (expanded) {
            Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
            content()
        }
    }
}

@Composable
private fun DailyBreakdownContent(dailyStats: List<TaskAiService.DayStats>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        dailyStats.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget)
                    .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(horizontal = EInkTheme.Global.padding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day name - readable size
                Text(
                    text = day.dayOfWeek.take(3), // Mon, Tue, etc.
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.width(48.dp)
                )

                // Progress bar - visible on E-Ink
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = EInkTheme.Global.spacing)
                ) {
                    // Background bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(EInkTheme.Colors.lightGray, RoundedCornerShape(10.dp))
                    )
                    // Fill bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(day.completionRate)
                            .height(20.dp)
                            .background(EInkTheme.Colors.primary, RoundedCornerShape(10.dp))
                    )
                }

                // Count - readable size
                Text(
                    text = "${day.completed}/${day.total}",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun RecurringTasksContent(
    recurringTasks: List<TaskAiService.RecurringTask>,
    onBreakDownTask: ((String) -> Unit)?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        recurringTasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(EInkTheme.Global.padding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.text,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = "Incomplete ${task.occurrences} times",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }

                if (onBreakDownTask != null) {
                    Spacer(Modifier.width(EInkTheme.Global.spacing))
                    // Break Down button with proper touch target
                    Box(
                        modifier = Modifier
                            .height(EInkTheme.Global.touchTarget)
                            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onBreakDownTask(task.text) }
                            .padding(horizontal = EInkTheme.Button.horizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Break Down",
                            fontSize = EInkTheme.Typography.labelLarge,
                            color = EInkTheme.Colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownContent(categories: List<TaskAiService.CategoryStats>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        categories.take(5).forEach { cat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget)
                    .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(horizontal = EInkTheme.Global.padding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category name - readable size
                Text(
                    text = cat.displayName,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.width(80.dp)
                )

                // Progress bar - visible on E-Ink
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = EInkTheme.Global.spacing)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(EInkTheme.Colors.lightGray, RoundedCornerShape(10.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cat.percentage.coerceIn(0f, 1f))
                            .height(20.dp)
                            .background(EInkTheme.Colors.primary, RoundedCornerShape(10.dp))
                    )
                }

                // Stats - readable size
                Text(
                    text = "${cat.count} (${(cat.percentage * 100).toInt()}%)",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun InsightItem(insight: TaskAiService.WeeklyInsight) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.padding)
    ) {
        Text(
            text = insight.text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            lineHeight = EInkTheme.Typography.bodyLarge
        )
    }
}

@Composable
private fun WarningBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Divider.thickness, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.paddingSmall)
    ) {
        Text(
            text = message,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )
    }
}

data class WeeklySummaryCacheInfo(
    val isCached: Boolean,
    val generatedAt: Long,
    val lastUpdatedAt: Long? = null,
    val taskCount: Int? = null,
    val completedCount: Int? = null
)

private fun formatTimeAgo(timestampMs: Long): String {
    val deltaMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = deltaMs / 60_000L
    if (minutes < 1) return "just now"
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

/**
 * Processing state for the weekly summary dialog.
 */
sealed class WeeklySummaryProcessingState {
    data class Processing(val stageMessage: String = "Gathering 7 days of tasks...") : WeeklySummaryProcessingState()
    data object Ready : WeeklySummaryProcessingState()
    data class Error(val message: String) : WeeklySummaryProcessingState()
}
