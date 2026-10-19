package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Calendar sync saves every fetched event again. A Boox Notes link points at its event
 * (boox_note_links.linkedEventId, foreign key ON DELETE SET NULL), so saving an event must keep its
 * links; only deleting the event may clear them (Oct 2026: every sync emptied all links).
 *
 * Runs on the JVM (Robolectric) against the real Room database and DAOs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
class CalendarEventLinkSurvivalTest {

    private lateinit var db: AppDatabase
    private lateinit var events: CalendarEventDao
    private lateinit var links: BooxNoteLinkDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        events = db.calendarEventDao()
        links = db.booxNoteLinkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun syncingAnEventAgainKeepsItsManualLink() = runBlocking {
        events.upsert(event("evt-1", "Ward council"))
        links.upsert(link("note-1", "evt-1", manual = true))

        events.upsert(event("evt-1", "Ward council (moved room)"))

        assertEquals("evt-1", links.getAllLinks().single().linkedEventId)
        assertEquals("Ward council (moved room)", events.getAll().single().title)
    }

    @Test
    fun syncingManyEventsAgainKeepsEveryLink() = runBlocking {
        events.upsertAll(listOf(event("evt-1", "Standup"), event("evt-2", "Review")))
        links.upsert(link("note-1", "evt-1", manual = true))
        links.upsert(link("note-2", "evt-2", manual = false))

        events.upsertAll(listOf(event("evt-1", "Standup"), event("evt-2", "Review"), event("evt-3", "New")))

        assertEquals(
            mapOf("note-1" to "evt-1", "note-2" to "evt-2"),
            links.getAllLinks().associate { it.noteUniqueId to it.linkedEventId }
        )
        assertEquals(3, events.getAll().size)
    }

    @Test
    fun deletingTheEventStillClearsTheLink() = runBlocking {
        events.upsert(event("evt-1", "Cancelled meeting"))
        links.upsert(link("note-1", "evt-1", manual = true))

        events.deleteById("evt-1")

        assertNull(links.getAllLinks().single().linkedEventId)
    }

    private fun event(id: String, title: String) = CalendarEvent(
        id = id,
        providerId = "GOOGLE",
        accountId = "acct",
        accountEmail = "acct@example.com",
        calendarId = "cal",
        calendarName = "Calendar",
        title = title,
        dayId = "2026-10-12",
        startTime = 1_791_000_000_000L,
        endTime = 1_791_003_600_000L,
        isAllDay = false,
        location = null,
        description = null,
        attendees = null,
        isReadOnly = false,
        colorHex = null
    )

    private fun link(noteId: String, eventId: String, manual: Boolean) = BooxNoteLink(
        noteUniqueId = noteId,
        linkedEventId = eventId,
        linkedDayDate = "2026-10-12",
        isManualLink = manual
    )
}
