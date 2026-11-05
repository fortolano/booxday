package com.boox.day.sleepscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the sleep-screen agenda gives up, and in which order, when the day doesn't fit. */
class SleepAgendaLayoutTest {

    private val m = SleepAgendaLayout.Metrics(
        sectionHeader = 50f, row = 40f, secondLine = 20f, allDayRow = 50f, note = 40f,
        memoPanelMin = 300f, nextDaysPanel = 200f, monthPanel = 10_000f, gap = 20f,
    )

    private fun content(
        allDay: Int = 0, past: Int = 0, coming: Int = 0, withLocation: Int = 0,
        open: Int = 0, done: Int = 0, subtasks: Int = 0, memo: Boolean = false, nextDays: Boolean = false,
    ) = SleepAgendaLayout.Content(allDay, past, coming, withLocation, open, done, subtasks, memo, nextDays)

    @Test
    fun aLightDayKeepsEverything() {
        val plan = SleepAgendaLayout.plan(SleepSizeClass.LARGE, content(coming = 3, withLocation = 3, open = 3, done = 1, memo = true, nextDays = true), m, 2000f)
        assertTrue(plan.showNextDays)
        assertTrue(plan.showMemo)
        assertTrue(plan.showLocations)
        assertFalse(plan.collapseDone)
        assertNull(plan.eventRows)
        assertNull(plan.taskRows)
        assertTrue("memo grows into the free space", plan.memoHeight > m.memoPanelMin)
    }

    @Test
    fun nextDaysGoFirstThenTheMemo() {
        // Columns of 50 + 8*40 + 8*20 = 530; body 1000: memo (300) fits, next days (200) too: 530+520 > 1000
        val c = content(coming = 8, withLocation = 8, open = 5, memo = true, nextDays = true)
        val plan = SleepAgendaLayout.plan(SleepSizeClass.LARGE, c, m, 1000f)
        assertFalse(plan.showNextDays)
        assertTrue(plan.showMemo)

        val tighter = SleepAgendaLayout.plan(SleepSizeClass.LARGE, c, m, 700f)
        assertFalse(tighter.showMemo)
        assertTrue("locations stay while they fit", tighter.showLocations)
    }

    @Test
    fun thenLocationsThenDoneTasksThenPastEventsThenSubtasks() {
        val c = content(past = 4, coming = 4, withLocation = 8, open = 6, done = 4, subtasks = 3)
        // Columns: schedule 50 + 8*40 + 8*20 = 530 ; tasks 50 + 13*40 = 570
        assertTrue(SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, c, m, 600f).showLocations)

        val noLocations = SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, c, m, 560f)
        assertFalse(noLocations.showLocations)
        assertTrue(noLocations.collapseDone)  // tasks 570 > 560, so done tasks go too

        val pastCollapsed = SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, c, m, 330f)
        assertTrue(pastCollapsed.collapsePast)

        // Everything given up and tasks still 50 + 7*40 = 330: the list keeps 6 rows, the last "+N more"
        val trimmed = SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, c, m, 320f)
        assertTrue(trimmed.hideSubtasks)
        assertEquals(6, trimmed.taskRows)
        assertNull(trimmed.eventRows)
    }

    @Test
    fun compactStacksTheListsAndTrimsTheLongerOne() {
        val c = content(coming = 10, open = 4)
        // Stacked: 50 + 10*40 + 20 + 50 + 4*40 = 680; events lose rows until 50 + 5*40 + 20 + 210 = 480
        val plan = SleepAgendaLayout.plan(SleepSizeClass.COMPACT, c, m, 500f)
        assertFalse(plan.showLocations)
        assertEquals(5, plan.eventRows)
        assertNull(plan.taskRows)
    }

    @Test
    fun compactNeverShowsTheMemoOrNextDays() {
        val plan = SleepAgendaLayout.plan(SleepSizeClass.COMPACT, content(coming = 1, open = 1, memo = true, nextDays = true), m, 2000f)
        assertFalse(plan.showMemo)
        assertFalse(plan.showNextDays)
        assertFalse("done tasks stay listed while there is room", plan.collapseDone)
    }

    private val mWithMonth = m.copy(monthPanel = 300f)
    private val heavyDay = content(coming = 12, open = 9)

    @Test
    fun theDefaultChoiceChangesNothing() {
        val days = listOf(heavyDay, content(coming = 3, withLocation = 3, open = 3, done = 1, memo = true, nextDays = true), content())
        for (size in SleepSizeClass.entries) for (day in days) for (body in listOf(400f, 900f, 2000f)) {
            val before = SleepAgendaLayout.plan(size, day, mWithMonth, body)
            val after = SleepAgendaLayout.plan(size, day, mWithMonth, body, SleepScreenContent())
            assertEquals(before, after)
            assertFalse("a month when there's room isn't pinned", after.monthPinned)
        }
    }

    @Test
    fun aMonthTheUserAskedForIsNeverGivenUpTheListsTrimInstead() {
        val choice = SleepScreenContent(tasks = false, memo = false, month = true)
        for (size in SleepSizeClass.entries) {
            val plan = SleepAgendaLayout.plan(size, heavyDay, mWithMonth, 700f, choice)
            assertTrue(plan.showMonth)
            assertTrue(plan.monthPinned)
            assertEquals(300f, plan.monthHeight, 0f)
            assertTrue("events cut to what's left", plan.eventRows != null)
            assertTrue(SleepAgendaLayout.scheduleHeight(heavyDay, mWithMonth, plan, plan.eventRows) <= 700f - 300f - mWithMonth.gap)
        }
    }

    @Test
    fun theMonthAloneGrowsToSixtyPercent() {
        val plan = SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, heavyDay, mWithMonth, 1000f, SleepScreenContent(events = false, tasks = false, memo = false, month = true))
        assertFalse(plan.showSchedule)
        assertFalse(plan.showTasks)
        assertEquals(600f, plan.monthHeight, 0.01f)
    }

    @Test
    fun whatIsOffIsNotPlanned() {
        val day = content(coming = 2, open = 2, memo = true, nextDays = true)
        val noMemo = SleepAgendaLayout.plan(SleepSizeClass.LARGE, day, mWithMonth, 2000f, SleepScreenContent(memo = false))
        assertFalse(noMemo.showMemo)
        assertEquals(0f, noMemo.memoHeight, 0f)

        val noEvents = SleepAgendaLayout.plan(SleepSizeClass.LARGE, day, mWithMonth, 2000f, SleepScreenContent(events = false))
        assertFalse(noEvents.showSchedule)
        assertFalse("next days are part of events", noEvents.showNextDays)

        val noTasks = SleepAgendaLayout.plan(SleepSizeClass.COMPACT, heavyDay, mWithMonth, 480f, SleepScreenContent(tasks = false))
        assertFalse(noTasks.showTasks)
        assertTrue("one list has the height to itself", (noTasks.eventRows ?: 12) >= 10)

        val noMonth = SleepAgendaLayout.plan(SleepSizeClass.MEDIUM, content(coming = 1), mWithMonth, 3000f, SleepScreenContent(month = false))
        assertFalse(noMonth.showMonth)
    }

    @Test
    fun theLastSwitchOnIsCounted() {
        assertEquals(3, SleepScreenContent().switchedOn(withMemo = true))
        assertEquals(2, SleepScreenContent().switchedOn(withMemo = false))
        assertEquals(1, SleepScreenContent(events = false, tasks = false, memo = false, month = true).switchedOn(withMemo = true))
    }

    @Test
    fun emptyListsTakeANoteRow() {
        val c = content()
        assertEquals(m.sectionHeader + m.note, SleepAgendaLayout.scheduleHeight(c, m, SleepAgendaLayout.plan(SleepSizeClass.COMPACT, c, m, 1000f), null))
    }
}
