package com.boox.day.editor.ui

import com.boox.day.data.datastore.AppSettings.FirstDayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The task date picker follows the week start setting, like the main calendar (Oct 2026). */
class TaskDatePickerDaysTest {

    @Test
    fun aMonthStartingOnSunday() {
        // 1 Nov 2026 is a Sunday
        assertEquals("2026-11-01", generateCalendarDays(2026, 10, FirstDayOfWeek.Sunday).first().dateString)
        assertEquals("2026-10-26", generateCalendarDays(2026, 10, FirstDayOfWeek.Monday).first().dateString)
    }

    @Test
    fun aMonthStartingOnMonday() {
        // 1 Jun 2026 is a Monday
        assertEquals("2026-05-31", generateCalendarDays(2026, 5, FirstDayOfWeek.Sunday).first().dateString)
        assertEquals("2026-06-01", generateCalendarDays(2026, 5, FirstDayOfWeek.Monday).first().dateString)
    }

    @Test
    fun everyMonthFillsSixWeeksOfConsecutiveDaysFromTheWeekStart() {
        for (start in FirstDayOfWeek.entries) {
            val firstColumn = if (start == FirstDayOfWeek.Monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            for (month in 0..11) {
                val days = generateCalendarDays(2026, month, start)
                val dates = days.map { LocalDate.parse(it.dateString) }
                assertEquals(42, days.size)
                assertEquals("$start ${month + 1}", firstColumn, dates.first().dayOfWeek)
                dates.zipWithNext { a, b -> assertEquals(a.plusDays(1), b) }
                assertEquals(1, days.first { it.isCurrentMonth }.dayOfMonth)
            }
        }
    }

    @Test
    fun theHeaderStartsOnTheSameDay() {
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), weekdayInitials(FirstDayOfWeek.Sunday))
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), weekdayInitials(FirstDayOfWeek.Monday))
    }
}
