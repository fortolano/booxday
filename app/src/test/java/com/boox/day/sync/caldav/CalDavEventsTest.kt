package com.boox.day.sync.caldav

import com.boox.day.data.model.ProviderEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * CalDAV event files turned into BooxDay rows (plan step 3.4): repeats in the event's own time zone
 * across a summer-time change, removed, moved and cancelled days, all-day events, ids that stay the
 * same. The tablet is in Toronto, the meeting in Berlin: their clocks change a week apart.
 */
class CalDavEventsTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val berlin = ZoneId.of("Europe/Berlin")
    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(toronto))
    }

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    @Test
    fun aWeeklyBerlinMeetingStaysAtNineBerlinTimeWithItsExceptions() {
        val meeting = BERLIN_TZ + """
            BEGIN:VEVENT
            UID:team-sync
            DTSTART;TZID=Europe/Berlin:20261006T090000
            DTEND;TZID=Europe/Berlin:20261006T100000
            RRULE:FREQ=WEEKLY;BYDAY=TU;COUNT=8
            EXDATE;TZID=Europe/Berlin:20261020T090000
            SUMMARY:Team sync
            END:VEVENT
            BEGIN:VEVENT
            UID:team-sync
            RECURRENCE-ID;TZID=Europe/Berlin:20261103T090000
            DTSTART;TZID=Europe/Berlin:20261103T110000
            DTEND;TZID=Europe/Berlin:20261103T120000
            SUMMARY:Team sync (moved)
            END:VEVENT
            BEGIN:VEVENT
            UID:team-sync
            RECURRENCE-ID:20261110T080000Z
            DTSTART;TZID=Europe/Berlin:20261110T090000
            DTEND;TZID=Europe/Berlin:20261110T100000
            STATUS:CANCELLED
            END:VEVENT
            """
        val rows = rows(meeting).sortedBy { it.startTime }

        // 8 Tuesdays from 6 Oct, less 20 Oct (EXDATE) and 10 Nov (cancelled, its RECURRENCE-ID in UTC)
        assertEquals(listOf("2026-10-06", "2026-10-13", "2026-10-27", "2026-11-03", "2026-11-17", "2026-11-24"), rows.map { it.dayId })
        for (row in rows.filter { it.dayId != "2026-11-03" }) {
            assertEquals("09:00 in Berlin on ${row.dayId}", berlinTime(row.dayId, 9, 0), row.startTime)
            assertEquals(row.startTime + HOUR, row.endTime)
            assertEquals("Team sync", row.title)
        }
        val moved = rows.single { it.dayId == "2026-11-03" }
        assertEquals(berlinTime("2026-11-03", 11, 0), moved.startTime)
        assertEquals("Team sync (moved)", moved.title)

        // Each day has its own id, built from the day's original start, so it is the same at every sync
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        assertTrue(moved.id.endsWith(":20261103T080000Z"))
        assertEquals(rows.map { it.id }, rows(meeting).sortedBy { it.startTime }.map { it.id })
    }

    @Test
    fun allDayEventsKeepTheirDaysAndTimedOnesTheirClock() {
        val rows = rows(
            """
            BEGIN:VEVENT
            UID:trip
            DTSTART;VALUE=DATE:20261102
            DTEND;VALUE=DATE:20261105
            SUMMARY:Trip
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:birthday
            DTSTART;VALUE=DATE:19851112
            RRULE:FREQ=YEARLY
            SUMMARY:Ana's birthday
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:conference
            DTSTART;VALUE=DATE:20260929
            DTEND;VALUE=DATE:20261003
            SUMMARY:Conference
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:floating
            DTSTART:20261105T140000
            DTEND:20261105T150000
            SUMMARY:Dentist
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:utc
            DTSTART:20261106T190000Z
            DURATION:PT30M
            END:VEVENT
            """
        ).associateBy { it.title }

        val trip = rows.getValue("Trip")
        assertTrue(trip.isAllDay)
        assertEquals("2026-11-02" to "2026-11-04", trip.dayId to trip.endDayId)
        assertEquals(midnight("2026-11-02"), trip.startTime)
        assertEquals(midnight("2026-11-05"), trip.endTime)

        // A yearly all-day repeat from 1985: this year's day only, keyed by its date
        val birthday = rows.getValue("Ana's birthday")
        assertEquals("2026-11-12" to "2026-11-12", birthday.dayId to birthday.endDayId)
        assertTrue(birthday.id.endsWith(":20261112"))

        // Starts before the window, ends inside it: kept, as Google's window does
        assertEquals("2026-09-29" to "2026-10-02", rows.getValue("Conference").let { it.dayId to it.endDayId })

        // No time zone written: the tablet's clock. UTC with a duration; no title: as Google's
        assertEquals(ZonedDateTime.of(LocalDate.parse("2026-11-05"), LocalTime.of(14, 0), toronto).toInstant().toEpochMilli(), rows.getValue("Dentist").startTime)
        val untitled = rows.getValue("Untitled Event")
        assertEquals(ZonedDateTime.parse("2026-11-06T19:00Z").toInstant().toEpochMilli(), untitled.startTime)
        assertEquals(untitled.startTime + HOUR / 2, untitled.endTime)
    }

    // ── Helpers ──

    /** Each text is one event file (href /cal/<n>.ics), read for October and November. */
    private fun rows(vararg files: String): List<ProviderEvent> = files.withIndex().flatMap { (n, body) ->
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n" + lines.joinToString("\r\n") + "\r\nEND:VCALENDAR\r\n"
        CalDavEvents.occurrences("acc-1", "/cal/", "/cal/$n.ics", ics, FROM, TO, readOnly = false, zone = toronto)
    }

    private fun berlinTime(day: String, hour: Int, minute: Int) =
        ZonedDateTime.of(LocalDate.parse(day), LocalTime.of(hour, minute), berlin).toInstant().toEpochMilli()

    private fun midnight(day: String) = LocalDate.parse(day).atStartOfDay(toronto).toInstant().toEpochMilli()

    private companion object {
        val FROM: LocalDate = LocalDate.parse("2026-10-01")
        val TO: LocalDate = LocalDate.parse("2026-11-30")
        const val HOUR = 3_600_000L

        val BERLIN_TZ = """
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:DAYLIGHT
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            TZNAME:CEST
            DTSTART:19700329T020000
            RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
            END:DAYLIGHT
            BEGIN:STANDARD
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            TZNAME:CET
            DTSTART:19701025T030000
            RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
            END:STANDARD
            END:VTIMEZONE
            """.trimIndent() + "\n"
    }
}
