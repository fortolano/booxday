package com.boox.day.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** An event ending at exactly midnight belongs to the day it started on (Oct 2026). */
class EventDaysTest {

    private val zone = ZoneId.of("America/Vancouver")
    private val day = LocalDate.of(2026, 11, 7)

    @Test
    fun anEventEndingAtMidnightStaysOnItsDay() {
        assertEquals(day, timedEventLastDay(millis(day, 22), millis(day.plusDays(1), 0), zone))
    }

    @Test
    fun anEventEndingAfterMidnightReachesTheNextDay() {
        assertEquals(day.plusDays(1), timedEventLastDay(millis(day, 22), millis(day.plusDays(1), 0, 30), zone))
    }

    @Test
    fun aMultiDayEventEndingAtMidnightEndsTheDayBefore() {
        assertEquals(day.plusDays(1), timedEventLastDay(millis(day, 23), millis(day.plusDays(2), 0), zone))
    }

    @Test
    fun ordinaryAndOddEndsKeepTheStartDay() {
        assertEquals(day, timedEventLastDay(millis(day, 9), millis(day, 10), zone))
        // Zero length at midnight, and an end before the start (bad data): the start day
        assertEquals(day, timedEventLastDay(millis(day, 0), millis(day, 0), zone))
        assertEquals(day, timedEventLastDay(millis(day, 10), millis(day.minusDays(1), 23), zone))
    }

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
