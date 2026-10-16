package com.boox.day.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartLinkDateRangesTest {

    @Test
    fun `expandCoveredDayIds returns single day when end is blank`() {
        assertEquals(
            setOf("2026-03-07"),
            expandCoveredDayIds("2026-03-07", "")
        )
    }

    @Test
    fun `expandCoveredDayIds expands inclusive multi day ranges`() {
        assertEquals(
            setOf("2026-03-07", "2026-03-08", "2026-03-09"),
            expandCoveredDayIds("2026-03-07", "2026-03-09")
        )
    }

    @Test
    fun `calendar event coveredDayIds uses endDayId when present`() {
        val event = CalendarEvent(
            id = "cloud-1",
            calendarId = "calendar",
            calendarName = "Calendar",
            title = "Trip",
            dayId = "2026-03-10",
            endDayId = "2026-03-12",
            startTime = 10L
        )

        assertEquals(
            setOf("2026-03-10", "2026-03-11", "2026-03-12"),
            event.coveredDayIds()
        )
    }

    @Test
    fun `smartLinkFieldsChanged is false when sync saves the same event again`() {
        val saved = syncedEvent()

        assertEquals(false, smartLinkFieldsChanged(saved, saved.copy(syncedAt = saved.syncedAt + 1, calendarName = "Renamed")))
    }

    @Test
    fun `smartLinkFieldsChanged is true for a new event and for each matching field`() {
        val saved = syncedEvent()

        assertEquals(true, smartLinkFieldsChanged(null, saved))
        listOf(
            saved.copy(dayId = "2026-03-11"),
            saved.copy(endDayId = "2026-03-11"),
            saved.copy(startTime = 11L),
            saved.copy(endTime = 21L),
            saved.copy(isAllDay = true),
            saved.copy(title = "Ward council (moved)"),
            saved.copy(description = "Agenda"),
            saved.copy(location = "Room 2")
        ).forEach { updated ->
            assertEquals(updated.toString(), true, smartLinkFieldsChanged(saved, updated))
        }
    }

    private fun syncedEvent() = CalendarEvent(
        id = "cloud-1",
        calendarId = "calendar",
        calendarName = "Calendar",
        title = "Ward council",
        dayId = "2026-03-10",
        endDayId = "2026-03-10",
        startTime = 10L,
        endTime = 20L,
        syncedAt = 100L
    )

    @Test
    fun `local event coveredDayIds falls back to dayId when endDayId is blank`() {
        val event = LocalEvent(
            title = "Local planning",
            dayId = "2026-03-15",
            endDayId = "",
            startTime = 20L
        )

        assertEquals(
            setOf("2026-03-15"),
            event.coveredDayIds()
        )
    }
}
