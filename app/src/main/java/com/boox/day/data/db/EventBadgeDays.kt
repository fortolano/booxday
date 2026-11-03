package com.boox.day.data.db

import com.boox.day.ui.state.CalendarSourceFilter
import java.time.YearMonth

/**
 * Calendar dots for events (Oct 2026). The month queries used to return each event's start day only,
 * so a week-long event had a dot on its first day, and one that started last month had none at all.
 * They now return the first and last day of every event overlapping the month, with the same
 * condition the day's event list uses (dayId <= day AND endDayId >= day), so a dot appears exactly on
 * the days that list the event.
 */
data class EventDaySpan(val dayId: String, val endDayId: String, val providerId: String)

/**
 * Days of [yearMonth] whose event list shows something (Nov 2026): the dots follow the source filter,
 * like the list and the Events count. They used to count every event, so a day with only Google
 * events kept its dot with Google switched off. Synced events count when their service is shown; the
 * tablet's own events when "This device" is shown ([localSpans] leave out copies shown from a synced
 * calendar, as the list does).
 */
internal fun shownEventDays(
    cloudSpans: List<EventDaySpan>,
    localSpans: List<EventDaySpan>,
    filter: CalendarSourceFilter,
    yearMonth: String
): Set<String> = daysCoveredInMonth(
    cloudSpans.filter { filter.showsSynced(it.providerId) } + if (filter.showLocal) localSpans else emptyList(),
    yearMonth
)

/** [shownEventDays] read from the database for [yearMonth]. */
internal suspend fun shownEventDaysInMonth(
    cloud: CalendarEventRepository,
    local: LocalEventRepository,
    yearMonth: String,
    filter: CalendarSourceFilter
): Set<String> = shownEventDays(cloud.getDaySpansForMonth(yearMonth), local.getShownDaySpansForMonth(yearMonth), filter, yearMonth)

/** First and last day ("yyyy-MM-dd") of [yearMonth] ("yyyy-MM"). */
internal fun monthDayRange(yearMonth: String): Pair<String, String> {
    val month = YearMonth.parse(yearMonth)
    return month.atDay(1).toString() to month.atEndOfMonth().toString()
}

/** Days of [yearMonth] covered by [spans]; each span is cut to the month first, so a long event adds at most 31 days. */
internal fun daysCoveredInMonth(spans: List<EventDaySpan>, yearMonth: String): Set<String> {
    val (firstDay, lastDay) = monthDayRange(yearMonth)
    return buildSet {
        for (span in spans) {
            val start = maxOf(span.dayId, firstDay)
            val end = minOf(span.endDayId.ifBlank { span.dayId }, lastDay)
            if (start <= end) addAll(expandCoveredDayIds(start, end))
        }
    }
}
