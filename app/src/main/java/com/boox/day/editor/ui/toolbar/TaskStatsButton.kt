package com.boox.day.editor.ui.toolbar

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.db.TaskRepository
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Get appropriate symbol and message based on streak count.
 * E-Ink optimized: returns text symbols instead of Material icons.
 */
private fun getStreakFeedback(streak: Int): Pair<String, String> {
    return when {
        streak == 0 -> "❄" to "Complete a task today!"
        streak == 1 -> "🔥" to "Keep it up!"
        streak in 2..4 -> "🔥" to "Building momentum!"
        streak in 5..9 -> "🔥" to "On fire!"
        streak in 10..19 -> "⚡" to "Unstoppable!"
        streak in 20..29 -> "★" to "Productivity star!"
        else -> "🏆" to "Legendary!"
    }
}

/**
 * Mini stat row for the right column (Best Streak, Total Done, Days Active).
 * E-Ink optimized: enlarged text, uses text symbol instead of Material icon.
 */
@Composable
private fun MiniStatRow(
    symbol: String,
    label: String,
    value: String,
    theme: EInkTheme.StatsDialog
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.miniStatHeight)
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(theme.miniStatCornerRadius))
            .padding(horizontal = theme.miniStatPaddingH, vertical = theme.miniStatPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = symbol,
                fontSize = theme.miniStatIconSize,
                color = EInkTheme.Colors.primary
            )
            Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))
            Text(
                text = label,
                fontSize = theme.miniStatLabelFontSize,
                color = EInkTheme.Colors.darkGray
            )
        }
        Text(
            text = value,
            fontSize = theme.miniStatValueFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
    }
}

/**
 * Period stats row with progress bar (Today, This Week, This Month).
 * E-Ink optimized: thicker progress bar for visibility, enlarged fonts.
 */
@Composable
private fun CompactPeriodRow(
    title: String,
    completed: Int,
    total: Int,
    theme: EInkTheme.StatsDialog
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = theme.labelFontSize,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.width(theme.periodLabelWidth)
        )

        // Progress bar - thicker for E-Ink visibility
        Box(
            modifier = Modifier
                .weight(1f)
                .height(theme.progressBarHeight)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(theme.progressBarCornerRadius))
        ) {
            if (total > 0) {
                val progress = (completed.toFloat() / total).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(theme.progressBarHeight)
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(theme.progressBarFillCornerRadius))
                )
            }
        }

        Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))

        Text(
            text = if (total > 0) "$completed/$total" else "—",
            fontSize = theme.labelFontSize,
            fontWeight = FontWeight.Bold,
            color = if (total > 0) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
            modifier = Modifier.width(theme.periodCountWidth),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Personal best record row (Best Day, Week, Month).
 * E-Ink optimized: enlarged badge and fonts, uses text symbol instead of Material icon.
 */
@Composable
private fun CompactBestRow(
    symbol: String,
    category: String,
    label: String?,
    count: Int?,
    theme: EInkTheme.StatsDialog
) {
    val hasData = label != null && count != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.bestRowHeight)
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(theme.cornerRadius))
            .padding(horizontal = theme.bestRowPaddingH, vertical = theme.bestRowPaddingV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(theme.bestBadgeSize)
                .background(if (hasData) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                fontSize = theme.bestBadgeIconSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.white
            )
        }

        Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Best $category",
                fontSize = theme.bestLabelFontSize,
                color = EInkTheme.Colors.gray
            )
            Text(
                text = label ?: "—",
                fontSize = theme.bestDateFontSize,
                fontWeight = FontWeight.Medium,
                color = if (hasData) EInkTheme.Colors.primary else EInkTheme.Colors.gray
            )
        }

        Text(
            text = count?.toString() ?: "—",
            fontSize = theme.bestCountFontSize,
            fontWeight = FontWeight.Bold,
            color = if (hasData) EInkTheme.Colors.primary else EInkTheme.Colors.gray
        )
    }
}

/**
 * Standalone Task Statistics Popup.
 * Extracted from TaskStatsButton for reuse in:
 * - Toolbar streak badge tap
 * - Hamburger menu "Statistics" item
 *
 * Shows comprehensive task statistics including:
 * - Current streak with motivational feedback
 * - Mini stats (best streak, total done, days active)
 * - Period progress (day, week, month)
 * - Personal bests (best day, week, month)
 */
@Composable
fun TaskStatsPopup(
    selectedDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Stats state
    var todayStats by remember { mutableStateOf<TaskRepository.PeriodStats?>(null) }
    var weekStats by remember { mutableStateOf<TaskRepository.PeriodStats?>(null) }
    var monthStats by remember { mutableStateOf<TaskRepository.PeriodStats?>(null) }
    var engagementStats by remember { mutableStateOf<TaskRepository.EngagementStats?>(null) }

    val today = LocalDate.now()
    val monthsFromNow = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), selectedDate.withDayOfMonth(1))
    val isCurrentMonth = monthsFromNow == 0L
    val isFuture = selectedDate.isAfter(today)
    val isFarFuture = monthsFromNow >= 3
    val isFarPast = monthsFromNow <= -12

    // Load stats when popup opens or date changes
    LaunchedEffect(selectedDate) {
        withContext(Dispatchers.IO) {
            val taskRepo = TaskRepository(context)

            // Day label - contextual
            val dayLabel = when {
                selectedDate == today -> "Today"
                selectedDate == today.minusDays(1) -> "Yesterday"
                selectedDate == today.plusDays(1) -> "Tomorrow"
                else -> selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
            }
            todayStats = taskRepo.getStatsForDateRange(
                selectedDate.toString(),
                selectedDate.toString(),
                dayLabel
            )

            // Week stats with contextual label
            weekStats = taskRepo.getWeekStatsForDate(selectedDate)

            // Month stats with contextual label
            monthStats = taskRepo.getMonthStatsForDate(selectedDate)

            // Engagement stats (always global)
            engagementStats = taskRepo.getEngagementStats()
        }
    }

    val theme = EInkTheme.StatsDialog  // Shorthand for readability

    Popup(
        alignment = Alignment.TopStart,  // Anchor at top-left
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Full-screen Box to achieve true screen-center positioning
        // (Popup alignment is relative to anchor, not screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .noRippleClickable { onDismiss() },  // Dismiss on background click
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(theme.width)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(theme.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(theme.cornerRadius))
                    .padding(theme.padding)
                    .noRippleClickable { /* Consume clicks on dialog content */ }
            ) {
            // Loading state
            if (todayStats == null || engagementStats == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading statistics...",
                        fontSize = theme.labelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            } else {
                // Header with contextual title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val headerSymbol = when {
                        isFarFuture -> "◷"
                        isFarPast -> "↺"
                        else -> "🏆"
                    }
                    Text(
                        text = headerSymbol,
                        fontSize = theme.titleFontSize,
                        color = EInkTheme.Colors.primary
                    )
                    Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))
                    Text(
                        text = when {
                            isCurrentMonth -> "Your Progress"
                            isFarFuture -> "Planning Ahead"
                            isFarPast -> "Looking Back"
                            isFuture -> "Future Tasks"
                            else -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                        },
                        fontSize = theme.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                // Contextual hint for non-current dates
                if (!isCurrentMonth) {
                    Spacer(modifier = Modifier.height(theme.headerSpacing))
                    Text(
                        text = when {
                            isFarFuture -> "Tasks you're planning for ${selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
                            isFarPast -> "Your task history from ${selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
                            isFuture -> "Upcoming tasks for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                            else -> "Viewing ${selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
                        },
                        fontSize = theme.hintFontSize,
                        color = EInkTheme.Colors.gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(theme.sectionSpacing))

                // ═══════════════════════════════════════════════════════════
                // STREAK + STATS ROW (enlarged E-Ink layout)
                // ═══════════════════════════════════════════════════════════
                engagementStats?.let { stats ->
                    val (streakSymbol, streakMessage) = getStreakFeedback(stats.currentStreak)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(theme.columnSpacing)
                    ) {
                        // Streak box (left)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(theme.cornerRadius))
                                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(theme.cornerRadius))
                                .padding(theme.sectionSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(theme.streakBadgeSize)
                                    .background(EInkTheme.Colors.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = streakSymbol,
                                    fontSize = theme.streakBadgeIconSize,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.Colors.white
                                )
                            }
                            Spacer(modifier = Modifier.width(theme.columnSpacing))
                            Column {
                                Text(
                                    text = if (stats.currentStreak > 0) "${stats.currentStreak} Day Streak" else "No Streak",
                                    fontSize = theme.streakTitleFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.Colors.primary
                                )
                                Text(
                                    text = streakMessage,
                                    fontSize = theme.streakMessageFontSize,
                                    color = EInkTheme.Colors.darkGray,
                                    maxLines = 2
                                )
                            }
                        }

                        // Mini stat boxes (right)
                        Column(
                            modifier = Modifier.width(theme.miniStatColumnWidth),
                            verticalArrangement = Arrangement.spacedBy(theme.miniStatRowSpacing)
                        ) {
                            MiniStatRow("🔥", "Best Streak", "${stats.longestStreak}", theme)
                            MiniStatRow("✓", "Total Done", "${stats.totalCompleted}", theme)
                            MiniStatRow("⚡", "Days Active", "${stats.totalDaysActive}", theme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(theme.dividerSpacing))
                Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
                Spacer(modifier = Modifier.height(theme.dividerSpacing))

                // ═══════════════════════════════════════════════════════════
                // TWO-COLUMN LAYOUT: Period Stats | Personal Bests
                // ═══════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(theme.columnSpacing)
                ) {
                    // LEFT COLUMN: Period Stats
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↗",
                                fontSize = theme.sectionTitleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))
                            Text(
                                text = if (isCurrentMonth) "Current Progress" else "Period Stats",
                                fontSize = theme.sectionTitleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(theme.sectionSpacing))

                        todayStats?.let { stats ->
                            CompactPeriodRow(stats.periodLabel, stats.completedCount, stats.totalWithContent, theme)
                        }
                        Spacer(modifier = Modifier.height(theme.periodRowSpacing))

                        weekStats?.let { stats ->
                            val weekLabel = if (isCurrentMonth) "This Week" else stats.periodLabel
                            CompactPeriodRow(weekLabel, stats.completedCount, stats.totalWithContent, theme)
                        }
                        Spacer(modifier = Modifier.height(theme.periodRowSpacing))

                        monthStats?.let { stats ->
                            val monthLabel = if (isCurrentMonth) "This Month" else
                                selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                            CompactPeriodRow(monthLabel, stats.completedCount, stats.totalWithContent, theme)
                        }
                    }

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(theme.verticalDividerWidth)
                            .height(180.dp)
                            .background(theme.verticalDividerColor)
                    )

                    // RIGHT COLUMN: Personal Bests
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🏅",
                                fontSize = theme.sectionTitleFontSize,
                                color = EInkTheme.Colors.primary
                            )
                            Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))
                            Text(
                                text = "Personal Bests",
                                fontSize = theme.sectionTitleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(theme.sectionSpacing))

                        engagementStats?.let { stats ->
                            CompactBestRow("★", "Day", stats.bestDay?.label, stats.bestDay?.count, theme)
                            Spacer(modifier = Modifier.height(theme.bestRowSpacing))
                            CompactBestRow("↗", "Week", stats.bestWeek?.label, stats.bestWeek?.count, theme)
                            Spacer(modifier = Modifier.height(theme.bestRowSpacing))
                            CompactBestRow("🎉", "Month", stats.bestMonth?.label, stats.bestMonth?.count, theme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(theme.buttonTopSpacing))

                // Close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(theme.buttonHeight)
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(theme.buttonCornerRadius))
                        .noRippleClickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        fontSize = theme.buttonFontSize,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.white
                    )
                }
            }
        }  // End Column
        }  // End Box (full-screen wrapper)
    }
}
