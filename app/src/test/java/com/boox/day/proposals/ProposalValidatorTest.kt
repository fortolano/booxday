package com.boox.day.proposals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A wrong date must never pass quietly, and the memo's day is the anchor (Nov 2026). */
class ProposalValidatorTest {
    private val memoDay = LocalDate.of(2026, 11, 2)  // a Monday
    private val today = LocalDate.of(2026, 11, 5)

    private fun check(answer: ScheduleItemAnswer) = ProposalValidator.check(answer, "1", memoDay, today)

    private fun event(date: String, source: String = "", start: String = "13:00", end: String = "") =
        check(ScheduleItemAnswer(kind = "event", title = "Lunch", date = date, startTime = start, endTime = end, sourceText = source)) as Proposal.Event

    @Test
    fun aWrittenWeekdayMustMatchTheDate() {
        assertNull(event("2026-11-10", "Lunch Tue 1pm").needsCheck)  // the 10th is a Tuesday
        assertEquals("Check the date: you wrote Wednesday", event("2026-11-12", "dentist Wed 3").needsCheck)
    }

    @Test
    fun aPastDateIsFlagged() {
        assertEquals("Check the date: it's in the past", event("2026-11-04").needsCheck)
    }

    @Test
    fun aSlashDateIsFlaggedBecauseItReadsTwoWays() {
        assertEquals(ProposalValidator.CHECK_DATE, event("2026-12-11", "lunch 12/11").needsCheck)
    }

    @Test
    fun noDateFallsBackToTheMemosDayAndAsksForALook() {
        val result = check(ScheduleItemAnswer(kind = "event", title = "Lunch", startTime = "13:00")) as Proposal.Event
        assertEquals(memoDay.toString(), result.date)
        assertEquals(ProposalValidator.CHECK_DATE, result.needsCheck)
    }

    @Test
    fun anEndBeforeTheStartOrNoEndGetsTheDefaultLength() {
        assertEquals(840, event("2026-11-10", end = "11:00").endMinute)
        assertEquals(840, event("2026-11-10").endMinute)
        assertEquals(870, event("2026-11-10", end = "14:30").endMinute)
        assertEquals("never past midnight", 1439, ProposalValidator.endOf(1420, null))
    }

    @Test
    fun anEventWithoutATimeIsAllDay() {
        val result = event("2026-11-10", start = "")
        assertTrue(result.allDay)
        assertNull(result.startMinute)
    }

    @Test
    fun aTaskTimeBecomesAReminderOnItsDueDay() {
        val task = check(ScheduleItemAnswer(kind = "task", title = "Pay insurance", date = "2026-11-13", startTime = "09:00")) as Proposal.Task
        assertEquals("2026-11-13", task.dueDay)
        assertEquals(540, task.reminderMinute)
        val plain = check(ScheduleItemAnswer(kind = "task", title = "Call Sam")) as Proposal.Task
        assertEquals(memoDay.toString(), plain.day)
        assertNull(plain.dueDay)
        assertNull("no date to ask about on a plain to-do", plain.needsCheck)
    }

    @Test
    fun overlapsAreFoundAndAllDayEventsDontCount() {
        val lunch = event("2026-11-10")
        assertEquals("Team sync", ProposalValidator.overlapping(lunch, listOf(Triple("Team sync", 800, 830))))
        assertNull(ProposalValidator.overlapping(lunch, listOf(Triple("Early", 600, 780))))
        assertNull(ProposalValidator.overlapping(event("2026-11-10", start = ""), listOf(Triple("Team sync", 800, 830))))
    }

    @Test
    fun theDayTableStartsAtTheAnchorSoTomorrowFollowsTheMemo() {
        val table = ProposalValidator.dayTable(memoDay).lines()
        assertEquals("Monday 2026-11-02", table[0])
        assertEquals("Tuesday 2026-11-03", table[1])
        assertEquals(14, table.size)
    }

    @Test
    fun switchingKindsKeepsTheDetails() {
        val event = event("2026-11-10")
        val back = ProposalValidator.toggled(ProposalValidator.toggled(event)) as Proposal.Event
        assertEquals(event.title, back.title)
        assertEquals(event.date, back.date)
        assertEquals(event.startMinute, back.startMinute)
    }
}
