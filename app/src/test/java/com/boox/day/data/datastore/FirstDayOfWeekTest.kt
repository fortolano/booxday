package com.boox.day.data.datastore

import com.boox.day.data.datastore.AppSettings.FirstDayOfWeek
import com.boox.day.ui.calendar.compactWindowStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The week starts on Sunday, Monday or Saturday (Oct 2026, Saturday added). */
class FirstDayOfWeekTest {

    // Wed 28 Oct 2026
    private val wednesday = LocalDate.of(2026, 10, 28)

    @Test
    fun theWeekStartsOnTheChosenDay() {
        assertEquals(LocalDate.of(2026, 10, 25), FirstDayOfWeek.Sunday.weekStart(wednesday))
        assertEquals(LocalDate.of(2026, 10, 26), FirstDayOfWeek.Monday.weekStart(wednesday))
        assertEquals(LocalDate.of(2026, 10, 24), FirstDayOfWeek.Saturday.weekStart(wednesday))
        // A day that is the start day starts its own week
        assertEquals(LocalDate.of(2026, 10, 24), FirstDayOfWeek.Saturday.weekStart(LocalDate.of(2026, 10, 24)))
        // The day before the start day belongs to the week before... Friday ends a Saturday week
        assertEquals(LocalDate.of(2026, 10, 24), FirstDayOfWeek.Saturday.weekStart(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun aSundayFirstListTurnsToStartOnTheChosenDay() {
        val sundayFirst = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val expectedFirst = mapOf(FirstDayOfWeek.Sunday to "Sun", FirstDayOfWeek.Monday to "Mon", FirstDayOfWeek.Saturday to "Sat")
        for (start in FirstDayOfWeek.entries) {
            val turned = sundayFirst.drop(start.shiftFromSunday) + sundayFirst.take(start.shiftFromSunday)
            assertEquals(start.name, expectedFirst.getValue(start), turned.first())
            assertEquals(start.name, 7, turned.toSet().size)
        }
    }

    @Test
    fun aDaysColumnFollowsTheStart() {
        // Column = (day - start + 7) % 7 with Sunday = 0, as the month grid computes it
        val wednesdayFromSunday = DayOfWeek.WEDNESDAY.value % 7
        assertEquals(3, (wednesdayFromSunday - FirstDayOfWeek.Sunday.shiftFromSunday + 7) % 7)
        assertEquals(2, (wednesdayFromSunday - FirstDayOfWeek.Monday.shiftFromSunday + 7) % 7)
        assertEquals(4, (wednesdayFromSunday - FirstDayOfWeek.Saturday.shiftFromSunday + 7) % 7)
    }

    @Test
    fun theTwoWeekStripStartsOnTheChosenDay() {
        assertEquals(LocalDate.of(2026, 10, 24), compactWindowStart(wednesday, FirstDayOfWeek.Saturday))
        assertEquals(LocalDate.of(2026, 10, 26), compactWindowStart(wednesday, FirstDayOfWeek.Monday))
    }
}
