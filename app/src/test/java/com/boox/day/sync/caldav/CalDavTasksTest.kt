package com.boox.day.sync.caldav

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * CalDAV tasks (VTODO) as task sync reads and writes them (plan stage 4, owner decisions 67, 69):
 * priority A/B/C, the reminder as a reminder on the due day (or the phone's due time), a parent by
 * UID; repeating tasks marked to be left out; an edit changes only BooxDay's fields and reads back as
 * written, so a sync never sends the same task again and again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavTasksTest {

    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun aPhoneTaskReadsAsBooxDaySeesIt() {
        // Made on the phone: due 5 Nov, a reminder at 15:00 Toronto (20:00 UTC), priority 2, under "p"
        val phone = ics(
            "BEGIN:VTODO", "UID:t", "SUMMARY:Call the bank", "STATUS:NEEDS-ACTION", "DUE;VALUE=DATE:20261105",
            "PRIORITY:2", "RELATED-TO:p", "DESCRIPTION:Ask about the card", "CATEGORIES:Errands",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Call the bank", "TRIGGER;VALUE=DATE-TIME:20261105T200000Z", "END:VALARM",
            "END:VTODO"
        )
        val read = CalDavTasks.read(phone, zone)!!
        assertEquals(
            listOf("t", "Call the bank", false, "2026-11-05", "p", "A", 15 * 60, false),
            listOf(read.uid, read.title, read.completed, read.dueDay, read.parentUid, read.priority, read.reminderMinute, read.repeats)
        )

        // A due time with no reminder is the reminder time (owner decision 69); a repeat rule is marked
        val timed = CalDavTasks.read(ics("BEGIN:VTODO", "UID:u", "SUMMARY:Pills", "DUE:20261105T140000Z", "RRULE:FREQ=DAILY", "END:VTODO"), zone)!!
        assertEquals(listOf("2026-11-05", 9 * 60, true), listOf(timed.dueDay, timed.reminderMinute, timed.repeats))

        // Ticked by a status, or by a completion time alone
        assertTrue(CalDavTasks.read(ics("BEGIN:VTODO", "UID:v", "SUMMARY:x", "STATUS:COMPLETED", "END:VTODO"), zone)!!.completed)
        assertTrue(CalDavTasks.read(ics("BEGIN:VTODO", "UID:w", "SUMMARY:x", "COMPLETED:20261101T100000Z", "END:VTODO"), zone)!!.completed)
    }

    @Test
    fun anEditChangesOnlyBooxDaysFieldsAndReadsBackAsWritten() {
        val phone = ics(
            "BEGIN:VTODO", "UID:t", "SUMMARY:Call the bank", "STATUS:NEEDS-ACTION", "DTSTART;VALUE=DATE:20261104",
            "DUE:20261105T200000Z", "PRIORITY:2", "DESCRIPTION:Ask about the card", "CATEGORIES:Errands", "X-OTHER-APP:keep",
            "END:VTODO"
        )
        // On the tablet: ticked, moved to 3 Nov, priority C, reminder 8:30, now a subtask of "p"
        val wanted = CalDavTasks.Fields("Call the bank today", true, "2026-11-03", "p", "C", 8 * 60 + 30)
        val edited = CalDavTasks.edit(phone, wanted, zone, NOW)
        val back = CalDavTasks.read(edited, zone)!!
        assertEquals(
            listOf(wanted.title, wanted.completed, wanted.dueDay, wanted.parentUid, wanted.priority, wanted.reminderMinute),
            listOf(back.title, back.completed, back.dueDay, back.parentUid, back.priority, back.reminderMinute)
        )
        for (kept in listOf("DESCRIPTION:Ask about the card", "CATEGORIES:Errands", "X-OTHER-APP:keep", "UID:t")) {
            assertTrue("$kept stays", kept in edited)
        }
        assertTrue("the start after the new due day goes", "DTSTART" !in edited)
        assertEquals("the same fields again: the file reads the same", back, CalDavTasks.read(CalDavTasks.edit(edited, wanted, zone, NOW), zone))

        // The usual time (no reminder of its own): BooxDay's reminder goes, no due time stands in for it
        val usual = CalDavTasks.read(CalDavTasks.edit(edited, wanted.copy(reminderMinute = null), zone, NOW), zone)!!
        assertNull(usual.reminderMinute)

        // A new task reads back as written too
        val made = CalDavTasks.read(CalDavTasks.newTask("n", wanted.copy(completed = false, parentUid = null), zone, NOW), zone)!!
        assertEquals(listOf("n", false, "2026-11-03", null, "C", 8 * 60 + 30), listOf(made.uid, made.completed, made.dueDay, made.parentUid, made.priority, made.reminderMinute))
    }

    private fun ics(vararg lines: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN") + lines + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n")

    private companion object {
        const val NOW = 1_793_620_800_000L  // 2 Nov 2026
    }
}
