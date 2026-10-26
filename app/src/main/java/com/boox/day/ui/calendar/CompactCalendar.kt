package com.boox.day.ui.calendar

import androidx.compose.foundation.background
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.Divider
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.Task
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Compact calendar pieces for narrow screens (plan Phase 1.5, "writing first"; layout B, Oct 2026):
 * a two-week strip and one-line Events/Due boxes stay above the page; the month grid opens as a
 * popup over the page.
 * Used only when LayoutProfiles.isCompact; the Regular layout does not use this file.
 */

/** Fixed heights of the compact rows (the Tasks canvas height must never change at runtime). */
object CompactCalendarDimens {
    val weekStripHeight: Dp = 102.dp     // layout B: weekday letters (14dp) + two weeks (2 × 44dp finger targets)
    val chipRowHeight: Dp = 56.dp        // Events / Due boxes: 46dp boxes, comfortable for a finger
    val popupTopOffset: Dp = 58.dp       // below the compact header row (53dp buttons + padding)
    val popupHeaderButton: Dp = 52.dp    // Events/Due popup: ‹ › + touch size (Oct 2026, finger-friendly)
    val popupHeaderIcon: Dp = 32.dp      // ... and their icons
}

/** First day of the two-week window whose first row holds [anchorDate]. */
fun compactWindowStart(anchorDate: LocalDate, firstDayOfWeekSetting: AppSettings.FirstDayOfWeek): LocalDate {
    return firstDayOfWeekSetting.weekStart(anchorDate)
}

/**
 * Two weeks (layout B, Oct 2026): the week containing [anchorDate] and the next one, under one row
 * of single-letter weekdays, with event dots. Tapping a day selects it; the header ‹ › move the
 * window two weeks at a time.
 */
@Composable
fun CompactTwoWeekStrip(
    anchorDate: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(anchorDate, firstDayOfWeekSetting) {
        val start = compactWindowStart(anchorDate, firstDayOfWeekSetting)
        (0L..13L).map { start.plusDays(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CompactCalendarDimens.weekStripHeight)
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.take(7).forEach { day ->
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                week.forEach { day ->
                    CompactDayCell(
                        day = day,
                        selectedDate = selectedDate,
                        today = today,
                        badges = badges,
                        onDateSelected = onDateSelected,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDayCell(
    day: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    onDateSelected: (String) -> Unit,
    modifier: Modifier
) {
    val dateString = day.toString()  // ISO yyyy-MM-dd (locale independent)
    val isSelected = dateString == selectedDate
    val isToday = dateString == today
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .background(
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                } else Modifier
            )
            .noRippleClickable { onDateSelected(dateString) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val textColor = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 17.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        // Event dot (space kept when empty so dates don't shift)
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(4.dp)
                .background(
                    if (dateString in badges.datesWithEvents) textColor else Color0,
                    CircleShape
                )
        )
    }
}

/** Transparent fill for the empty event-dot slot. */
private val Color0 = androidx.compose.ui.graphics.Color.Transparent

/**
 * "Events n · first event" and "Due n · first task" (layout B, Oct 2026): two one-line boxes side by
 * side; each opens that list over the page. The whole row height is the touch target.
 */
@Composable
fun CompactPreviewBoxes(
    eventsCount: Int,
    eventsPreview: String?,
    dueCount: Int,
    duePreview: String?,
    onEventsClick: () -> Unit,
    onDueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CompactCalendarDimens.chipRowHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactPreviewBox(label = "Events $eventsCount", preview = eventsPreview, onClick = onEventsClick, modifier = Modifier.weight(1f))
        CompactPreviewBox(label = "Due $dueCount", preview = duePreview, onClick = onDueClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactPreviewBox(label: String, preview: String?, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .noRippleClickable { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1
            )
            if (!preview.isNullOrBlank()) {
                Text(text = " · ", fontSize = 15.sp, color = EInkTheme.Colors.gray, maxLines = 1)
                Text(
                    text = preview,
                    fontSize = 15.sp,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Month grid over the page. ‹ › change the shown month; tapping a day selects it and closes.
 */
@Composable
fun CompactMonthPopup(
    year: Int,
    month: Int,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    showWeekNumbers: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val offsetY = with(LocalDensity.current) { CompactCalendarDimens.popupTopOffset.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .background(EInkTheme.PopupOverlay.background)
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor)
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = EInkTheme.Colors.primary
                    )
                }
                Text(
                    text = "${monthNames[month]} $year",
                    fontSize = EInkTheme.CalendarToolbar.monthFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = EInkTheme.Colors.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = EInkTheme.Colors.primary
                    )
                }
            }
            DayOfWeekHeader(
                selectedDate = selectedDate,
                firstDayOfWeekSetting = firstDayOfWeekSetting,
                showWeekNumbers = showWeekNumbers
            )
            CalendarGrid(
                year = year,
                month = month,
                selectedDate = selectedDate,
                today = today,
                badges = badges,
                onDateSelected = onDateSelected,
                firstDayOfWeekSetting = firstDayOfWeekSetting,
                showWeekNumbers = showWeekNumbers,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(288.dp)  // 6 week rows × 48 dp
            )
        }
    }
}

/**
 * Text for the Events box (layout B): on today the next event that hasn't ended, on other days the
 * first one; timed events start with their time. Null when the day has no events.
 */
fun compactEventPreview(events: List<CalendarEvent>, dayId: String, now: Long = System.currentTimeMillis()): String? {
    if (events.isEmpty()) return null
    val timed = events.filter { !it.isAllDay }.sortedBy { it.startTime }
    val upcoming = if (dayId == LocalDate.now().toString()) {
        timed.firstOrNull { (it.endTime ?: it.startTime) >= now }
    } else {
        timed.firstOrNull()
    }
    val pick = upcoming ?: events.firstOrNull { it.isAllDay } ?: timed.first()
    if (pick.isAllDay) return pick.title
    val time = Instant.ofEpochMilli(pick.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    return "$time ${pick.title}"
}

/** Text for the Due box (layout B): the task's typed, converted or recognized text. */
fun compactTaskPreview(task: Task): String =
    task.typedText?.takeIf { it.isNotBlank() }
        ?: task.convertedText?.takeIf { it.isNotBlank() }
        ?: task.recognizedText?.takeIf { it.isNotBlank() }
        ?: "Handwritten task"

/** Two-week strip + Events/Due boxes (the fixed-height rows above the page; layout B). */
@Composable
fun CompactCalendarRows(
    anchorDate: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    eventsCount: Int,
    eventsPreview: String?,
    dueCount: Int,
    duePreview: String?,
    onDateSelected: (String) -> Unit,
    onOpenList: (tab: String) -> Unit
) {
    CompactTwoWeekStrip(
        anchorDate = anchorDate,
        selectedDate = selectedDate,
        today = today,
        badges = badges,
        firstDayOfWeekSetting = firstDayOfWeekSetting,
        onDateSelected = onDateSelected
    )
    CompactPreviewBoxes(
        eventsCount = eventsCount,
        eventsPreview = eventsPreview,
        dueCount = dueCount,
        duePreview = duePreview,
        onEventsClick = { onOpenList("events") },
        onDueClick = { onOpenList("due") }
    )
}

/**
 * Events/Due day list over the page: ‹ day › and + (new event on that day) on top, [content] (the
 * list) in the middle and a Close button at the bottom. It covers only part of the screen (Oct 2026)
 * so a tap outside also closes it.
 */
@Composable
fun CompactDayListPopup(
    listDate: String,
    onListDateChange: (String) -> Unit,
    onAddEvent: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val offsetY = with(LocalDensity.current) { CompactCalendarDimens.popupTopOffset.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.72f)  // Oct 2026: 20% taller (was 0.6) to show more events and due items
                .background(EInkTheme.EventsPanel.rowBackground)
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor)
        ) {
            // Header ‹ day › +: finger-size buttons (52dp, 32dp icons) with a gap before +
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { parseIsoDateOrNull(listDate)?.let { onListDateChange(it.minusDays(1).toString()) } },
                    modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous day",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
                Text(
                    text = remember(listDate) {
                        parseIsoDateOrNull(listDate)
                            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                            ?: listDate
                    },
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { parseIsoDateOrNull(listDate)?.let { onListDateChange(it.plusDays(1).toString()) } },
                    modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onAddEvent, modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add event",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
            }
            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            EinkSecondaryButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

/** Parses a yyyy-MM-dd key, or null. */
internal fun parseIsoDateOrNull(date: String): LocalDate? =
    runCatching { LocalDate.parse(date) }.getOrNull()
