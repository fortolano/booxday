package com.boox.day.data.db

import java.time.YearMonth

/**
 * Calendar dots for events (Oct 2026). The month queries used to return each event's start day only,
 * so a week-long event had a dot on its first day, and one that started last month had none at all.
 * They now return the first and last day of every event overlapping the month, with the same
 * condition the day's event list uses (dayId <= day AND endDayId >= day), so a dot appears exactly on
 * the days that list the event.
 */
data class EventDaySpan(val dayId: String, val endDayId: String)

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
