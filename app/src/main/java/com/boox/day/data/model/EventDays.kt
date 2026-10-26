package com.boox.day.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Last day (inclusive) a timed event shows on: the day its end falls on, except an end at exactly
 * local midnight, which closes the day before (Oct 2026: a 22:00–00:00 event also showed on the next
 * day). Never before the start day. All-day events have their own rule: their end date is exclusive.
 *
 * Used by every writer of endDayId for timed events (Google sync, provider sync, local create), so
 * they agree.
 */
internal fun timedEventLastDay(startMillis: Long, endMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    val endsAtMidnight = end.toLocalTime() == LocalTime.MIDNIGHT && end.isAfter(start)
    val lastDay = if (endsAtMidnight) end.toLocalDate().minusDays(1) else end.toLocalDate()
    return maxOf(lastDay, start.toLocalDate())
}
