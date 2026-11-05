package com.boox.day.proposals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskRepository
import com.boox.day.events.EventTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only what's ticked is made, and it's made as shown (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProposalApplierTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val day = "2026-11-13"
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    private fun tasksOn(date: String) = db.taskDao().getTasksForDateRange(date, date).filter { !it.typedText.isNullOrBlank() }

    @Test
    fun aTaskGetsItsDayDueDayAndReminder() {
        val task = Proposal.Task("1", "Pay the car insurance", day, day, 540)
        val added = runBlocking { ProposalApplier.addTasks(TaskRepository(context), listOf(task), "2026-11-02", 2000f) }
        assertEquals(1, added)
        val row = tasksOn(day).single()
        assertEquals("Pay the car insurance", row.typedText)
        assertEquals(day, row.dueDay)
        assertEquals(540, row.reminderMinuteOfDay)
    }

    @Test
    fun nothingIsMadeForAnUntickedItem() {
        val task = Proposal.Task("1", "Not this one", day, null, null, accepted = false)
        val added = runBlocking { ProposalApplier.addTasks(TaskRepository(context), listOf(task), "2026-11-02", 2000f) }
        assertEquals(0, added)
        assertEquals(0, tasksOn(day).size)
    }

    @Test
    fun theAiNeverFillsAttendeesAndAnAllDayEventHasNoEnd() {
        val event = Proposal.Event("1", " Conference ", day, null, null, allDay = true, location = "Hall")
        val draft = ProposalApplier.draft(event, "2026-11-02", EventTarget(false, null, null, "l1"))
        assertNull(draft.attendees)
        assertNull(draft.endHour)
        assertEquals("Conference", draft.title)
        assertEquals("l1", draft.localCalendarId)
    }
}
