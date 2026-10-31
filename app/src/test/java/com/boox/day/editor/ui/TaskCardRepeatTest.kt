package com.boox.day.editor.ui

import com.boox.day.data.db.RecurringTaskTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** The task card's Repeat screen (31 Oct 2026, owner's decisions in the task dialogs review). */
class TaskCardRepeatTest {

    private val sat31Oct = LocalDate.of(2026, 10, 31)

    @Test
    fun offSavesNothingSoARepeatingTaskIsStoppedInstead() {
        val series = RecurringTaskTemplate(title = "Water plants", recurrenceType = "WEEKLY", anchorDate = "2026-10-03")
        val form = RepeatForm.of(series, sat31Oct).copy(choice = RepeatChoice.OFF)
        assertNull(form.toDraft())
    }

    @Test
    fun monthlyFromThe31stUsesThe28thAndSaysSo() {
        val form = RepeatForm.of(null, sat31Oct).copy(choice = RepeatChoice.MONTHLY)
        assertEquals(28, form.toDraft()!!.dayOfMonth)
        assertEquals("Every month on the 28th", repeatChoiceLabel(RepeatChoice.MONTHLY, form, Locale.ENGLISH))
    }

    @Test
    fun anOlderEveryFewDaysSeriesKeepsItsInterval() {
        // The old dialog always saved intervalDays = 1, turning "every 3 days" into "every day"
        val series = RecurringTaskTemplate(title = "Stretch", recurrenceType = "DAILY", intervalDays = 3, anchorDate = "2026-10-01")
        val draft = RepeatForm.of(series, sat31Oct).toDraft()!!
        assertEquals(3, draft.intervalDays)
        assertEquals("[1,3,5]", RepeatForm.of(
            series.copy(recurrenceType = "CUSTOM_WEEKDAYS", daysOfWeek = "[1,3,5]"), sat31Oct
        ).toDraft()!!.daysOfWeek.joinToString(",", "[", "]"))
    }
}
