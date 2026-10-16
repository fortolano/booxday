package com.boox.day.data.db

import java.time.LocalDate

internal fun expandCoveredDayIds(
    startDayId: String,
    endDayId: String = startDayId
): Set<String> {
    if (startDayId.isBlank()) return emptySet()

    val normalizedEnd = endDayId.ifBlank { startDayId }

    return try {
        val start = LocalDate.parse(startDayId)
        val end = LocalDate.parse(normalizedEnd)
        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)
        buildSet {
            var current = rangeStart
            while (!current.isAfter(rangeEnd)) {
                add(current.toString())
                current = current.plusDays(1)
            }
        }
    } catch (_: Exception) {
        setOf(startDayId, normalizedEnd).filter { it.isNotBlank() }.toSet()
    }
}

internal fun CalendarEvent.coveredDayIds(): Set<String> = expandCoveredDayIds(dayId, endDayId)

internal fun LocalEvent.coveredDayIds(): Set<String> = expandCoveredDayIds(dayId, endDayId)

internal fun collectCoveredDayIds(events: Iterable<CalendarEvent>): Set<String> =
    events.flatMap { it.coveredDayIds() }.toSet()

internal fun collectLocalCoveredDayIds(events: Iterable<LocalEvent>): Set<String> =
    events.flatMap { it.coveredDayIds() }.toSet()

/**
 * Whether a synced event changed anything Smart Link matching reads (days, times, title, description,
 * location); a new event ([existing] = null) counts. Calendar sync re-links only these events' days,
 * not every fetched event on every sync (Oct 2026).
 */
internal fun smartLinkFieldsChanged(existing: CalendarEvent?, updated: CalendarEvent): Boolean =
    existing == null ||
        existing.dayId != updated.dayId ||
        existing.endDayId != updated.endDayId ||
        existing.startTime != updated.startTime ||
        existing.endTime != updated.endTime ||
        existing.isAllDay != updated.isAllDay ||
        existing.title != updated.title ||
        existing.description != updated.description ||
        existing.location != updated.location
