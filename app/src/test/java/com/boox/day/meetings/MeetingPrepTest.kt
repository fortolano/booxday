package com.boox.day.meetings

import com.boox.day.data.db.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding the right earlier meeting, and what Brief Me lets leave the tablet (Nov 2026). */
class MeetingPrepTest {
    private val day = 86_400_000L

    private fun event(id: String, title: String, calendar: String, start: Long, dayId: String, attendees: String? = null) = CalendarEvent(
        id = id, calendarId = calendar, calendarName = calendar, title = title, startTime = start, endTime = start + 3_600_000, dayId = dayId, attendees = attendees
    )

    private val now = event("e3", "Team sync", "work", 20 * day, "2026-11-10")

    @Test
    fun `same name in the same calendar is the same meeting, accents and case aside`() {
        assertTrue(EarlierMeetings.sameMeeting(now, event("e2", "TEAM SYNC", "work", 13 * day, "2026-11-03")))
        assertTrue(EarlierMeetings.sameMeeting(event("e4", "Réunion", "work", 20 * day, "2026-11-10"), event("e1", "Reunion", "work", 13 * day, "2026-11-03")))
    }

    @Test
    fun `same name in another calendar is not`() {
        assertFalse(EarlierMeetings.sameMeeting(now, event("e2", "Team sync", "home", 13 * day, "2026-11-03")))
    }

    @Test
    fun `a renamed meeting is not, and neither is a later one`() {
        assertFalse(EarlierMeetings.sameMeeting(now, event("e2", "Team sync (budget)", "work", 13 * day, "2026-11-03")))
        assertFalse(EarlierMeetings.sameMeeting(now, event("e5", "Team sync", "work", 27 * day, "2026-11-17")))
    }

    @Test
    fun `CalDAV occurrences of one file match even when renamed`() {
        val later = event("abc.ics#20261110", "Team sync", "work", 20 * day, "2026-11-10")
        assertTrue(EarlierMeetings.sameMeeting(later, event("abc.ics#20261103", "Weekly", "work", 13 * day, "2026-11-03")))
    }

    @Test
    fun `newest first, at most three`() {
        val noted = (1..5).map { event("old$it", "Team sync", "work", it * day, "2026-10-0$it") }
        assertEquals(listOf("2026-10-05", "2026-10-04", "2026-10-03"), EarlierMeetings.pick(now, noted, emptyList()).map { it.date })
    }

    @Test
    fun `notes whose event is gone are found through their header`() {
        val found = EarlierMeetings.pick(now, emptyList(), listOf("2026-11-03" to "page1", "2026-11-17" to "later"))
        assertEquals(listOf(EarlierMeeting("2026-11-03", "page1", null)), found)
    }

    @Test
    fun `people are names, never addresses`() {
        assertEquals("With Anna, Tom and 3 others", MeetingPrepLoader.people("anna@x.com, tom.lee@x.com,sam@x.com,mailto:ian@x.com, jo@x.com"))
        assertEquals("With Anna and Tom", MeetingPrepLoader.people("anna@x.com,tom@x.com"))
        assertEquals("With Anna", MeetingPrepLoader.people("anna@x.com"))
        assertNull(MeetingPrepLoader.people(null))
    }

    @Test
    fun `Brief Me sends no attendee address`() {
        val meeting = event("e3", "Team sync", "work", 20 * day, "2026-11-10", attendees = "anna@x.com,tom@x.com").copy(description = "Budget")
        val sent = MeetingBriefService.material(meeting, listOf("Budget agreed"), listOf("Send budget"))
        assertFalse(sent.contains("@"))
        assertTrue(sent.contains("Team sync") && sent.contains("Budget agreed") && sent.contains("Send budget"))
    }
}
