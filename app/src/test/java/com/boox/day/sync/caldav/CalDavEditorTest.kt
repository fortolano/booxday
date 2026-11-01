package com.boox.day.sync.caldav

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Tablet edits to CalDAV event files (plan step 3.5): only BooxDay's fields change (alarms,
 * attendees and other apps' data stay), one day of a series changes or goes alone and keeps its row
 * id, an all-day trip keeps its length when moved.
 */
class CalDavEditorTest {

    private val zone = ZoneId.of("America/Toronto")
    private lateinit var savedZone: TimeZone

    /** Rows take their days from the device's clock: the tablet is in Toronto. */
    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    @Test
    fun anEditChangesOnlyBooxDaysFieldsAndATripKeepsItsLength() {
        val meeting = file(
            "BEGIN:VEVENT", "UID:m", "DTSTART:20261102T150000Z", "DTEND:20261102T160000Z", "SUMMARY:Planning",
            "ORGANIZER:mailto:boss@example.com", "ATTENDEE;CN=Ana:mailto:ana@example.com", "X-APPLE-TRAVEL-ADVISORY-BEHAVIOR:AUTOMATIC",
            "SEQUENCE:3", "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Reminder", "TRIGGER:-PT15M", "END:VALARM", "END:VEVENT"
        )
        val edited = CalDavEditor.edit(meeting, null, fields("Planning (moved)", "2026-11-03", 9, 30, 10, 0, location = "Room 4"), zone, NOW)

        val row = rows(edited).single()
        assertEquals("Planning (moved)" to "Room 4", row.title to row.location)
        assertEquals(ZonedDateTime.of(LocalDate.parse("2026-11-03"), LocalTime.of(9, 30), zone).toInstant().toEpochMilli(), row.startTime)
        assertEquals(row.startTime + 30 * MINUTE, row.endTime)
        assertEquals("ana@example.com", row.attendees)
        for (kept in listOf("ORGANIZER:mailto:boss@example.com", "mailto:ana@example.com", "X-APPLE-TRAVEL-ADVISORY-BEHAVIOR:AUTOMATIC", "TRIGGER:-PT15M")) {
            assertTrue("kept: $kept", kept in unfold(edited))
        }
        assertTrue("other apps see a newer version", "SEQUENCE:4" in edited)

        // A three-day trip moved by a day stays three days (the dialog has no end date)
        val trip = file("BEGIN:VEVENT", "UID:t", "DTSTART;VALUE=DATE:20261102", "DTEND;VALUE=DATE:20261105", "SUMMARY:Trip", "END:VEVENT")
        val moved = rows(CalDavEditor.edit(trip, null, fields("Trip", "2026-11-03", allDay = true), zone, NOW)).single()
        assertEquals("2026-11-03" to "2026-11-05", moved.dayId to moved.endDayId)
    }

    @Test
    fun oneDayOfASeriesChangesOrGoesAloneAndKeepsItsId() {
        val yoga = file(
            "BEGIN:VEVENT", "UID:y", "DTSTART:20261103T230000Z", "DURATION:PT1H", "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:Yoga",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Yoga", "TRIGGER:-PT10M", "END:VALARM", "END:VEVENT"
        )
        val before = rows(yoga).sortedBy { it.startTime }
        val second = before[1]
        val occurrence = second.id.substringAfterLast(':')

        // That day only: renamed and an hour later; the other two as they were
        val edited = CalDavEditor.edit(yoga, occurrence, fields("Yoga (gym)", second.dayId, 20, 0, 21, 0), zone, NOW)
        val after = rows(edited).sortedBy { it.startTime }
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(listOf("Yoga", "Yoga (gym)", "Yoga"), after.map { it.title })
        assertEquals(before[0].startTime to before[2].startTime, after[0].startTime to after[2].startTime)
        assertEquals("the day keeps its alarm", 2, Regex("TRIGGER:-PT10M").findAll(edited).count())

        // Deleting that day leaves the other two; deleting a plain event leaves nothing
        val left = rows(CalDavEditor.removeOccurrence(edited, occurrence)!!).sortedBy { it.startTime }
        assertEquals(listOf(before[0].id, before[2].id), left.map { it.id })
        assertFalse("Yoga (gym)" in left.map { it.title })
        assertNull(CalDavEditor.removeOccurrence(yoga, null))
    }

    // ── Helpers ──

    private fun file(vararg lines: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN") + lines + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n")

    private fun rows(ics: String) =
        CalDavEvents.occurrences("acc-1", "/cal/", "/cal/a.ics", ics, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-30"), readOnly = false, zone = zone)

    private fun fields(
        title: String, day: String, startHour: Int = 0, startMinute: Int = 0, endHour: Int? = null, endMinute: Int? = null,
        allDay: Boolean = false, location: String? = null
    ) = CalDavEditor.Fields(
        title, LocalDate.parse(day), LocalTime.of(startHour, startMinute),
        endHour?.let { LocalTime.of(it, endMinute ?: 0) }, allDay, location, description = null
    )

    /** iCalendar folds long lines at 75 characters. */
    private fun unfold(ics: String) = ics.replace("\r\n ", "")

    private companion object {
        const val NOW = 1_793_000_000_000L
        const val MINUTE = 60_000L
    }
}
