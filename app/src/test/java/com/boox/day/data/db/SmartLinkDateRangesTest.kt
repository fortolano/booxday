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
