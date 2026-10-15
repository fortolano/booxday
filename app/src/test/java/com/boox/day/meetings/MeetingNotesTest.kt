package com.boox.day.meetings

import com.boox.day.data.db.CalendarEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingNotesTest {
    private val minute = 60_000L
    private val start = 1_000_000_000_000L

    private fun event(startTime: Long = start, endTime: Long? = start + 60 * minute, allDay: Boolean = false) = CalendarEvent(
        id = "e1", calendarId = "c", calendarName = "Calendar", title = "Budget review", dayId = "2026-10-07",
        startTime = startTime, endTime = endTime, isAllDay = allDay
    )

    @Test
    fun `a meeting is on from 10 minutes before it starts until it ends`() {
        val meeting = event()
        assertFalse(MeetingNotes.isHappeningNow(meeting, start - 11 * minute))
        assertTrue(MeetingNotes.isHappeningNow(meeting, start - 10 * minute))
        assertTrue(MeetingNotes.isHappeningNow(meeting, start + 30 * minute))
        assertFalse(MeetingNotes.isHappeningNow(meeting, start + 60 * minute))
    }

    @Test
    fun `all-day events are never the meeting happening now, and one without an end lasts an hour`() {
        assertFalse(MeetingNotes.isHappeningNow(event(endTime = null, allDay = true), start + 5 * minute))
        val open = event(endTime = null)
        assertTrue(MeetingNotes.isHappeningNow(open, start + 59 * minute))
        assertFalse(MeetingNotes.isHappeningNow(open, start + 61 * minute))
    }
}
