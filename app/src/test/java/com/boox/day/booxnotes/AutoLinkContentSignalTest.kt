package com.boox.day.booxnotes

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.db.ConnectedAccountEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The background auto-link links a note only when something says the note is ABOUT the event
 * (title word, topic, AI). Timing alone is saved as a suggestion (Oct 2026: one Boox sync gave eight
 * page-less notes the same folder time inside a General Conference session, and all were linked).
 *
 * Runs the real [BooxNoteIndexer.autoLinkNotesForDate] on the JVM (Robolectric) against the real Room
 * database, with semantic matching off, as on the Go 10.3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutoLinkContentSignalTest {

    private lateinit var context: Context
    private lateinit var notes: BooxNoteRepository
    private lateinit var indexer: BooxNoteIndexer

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        notes = BooxNoteRepository(context)
        val events = CalendarEventRepository(context)
        indexer = BooxNoteIndexer(context, BooxNotesIntegration(context), notes, events)

        val db = AppDatabase.getDatabase(context)
        db.connectedAccountDao().upsert(
            ConnectedAccountEntity(
                id = "acct",
                providerId = "GOOGLE",
                accountType = "GOOGLE_PERSONAL",
                email = "owner@example.com",
                displayName = null,
                createdAt = 0L
            )
        )
        db.calendarSelectionDao().upsert(
            CalendarSelectionEntity(
                id = "acct:cal",
                accountId = "acct",
                calendarId = "cal",
                calendarName = "Church",
                calendarColor = null,
                isFetchEnabled = true
            )
        )
        // 3 Oct 2026 on the Go 10.3: two conference sessions and a vet visit
        events.cacheEvents(
            listOf(
                event("gc-morning", "General Conference Saturday Morning Session - Churchwide Events", OCT_3, 9, 11),
                event("gc-afternoon", "General Conference Saturday Afternoon Session - Churchwide Events", OCT_3, 13, 15),
                event("vet", "Daisy's vet app", OCT_3, 16, 17),
                event("council", "Ward council", OCT_10, 10, 11)
            )
        )
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    @Test
    fun noteWrittenDuringAnEventWithNoContentMatchIsOnlyASuggestion() = runBlocking {
        notes.upsertNote(note("easter", "easter devotional", OCT_3, 9, 23))

        indexer.autoLinkNotesForDate(OCT_3)

        val link = notes.getLinkForNote("easter")!!
        assertNull(link.linkedEventId)
        assertEquals(SmartLinkDecisionMode.SAME_DAY_SUGGESTION.name, link.decisionMode)
        assertTrue(link.suggestedEventIds.orEmpty().contains("gc-morning"))
    }

    @Test
    fun noteWhoseTitleNamesTheEventIsStillLinked() = runBlocking {
        notes.upsertNote(note("s2", "S2 conference 2026", OCT_3, 9, 23))

        indexer.autoLinkNotesForDate(OCT_3)

        val link = notes.getLinkForNote("s2")!!
        assertEquals("gc-morning", link.linkedEventId)
        assertEquals(SmartLinkDecisionMode.SAME_DAY_AUTO_HIGH.name, link.decisionMode)
    }

    @Test
    fun onlyEventOfTheDayIsNotLinkedOnTimingAlone() = runBlocking {
        // During the only timed event: 60 + 20 = 80, HIGH on timing alone
        notes.upsertNote(note("nb27", "Notebook-27", OCT_10, 10, 30))

        indexer.autoLinkNotesForDate(OCT_10)

        val link = notes.getLinkForNote("nb27")!!
        assertNull(link.linkedEventId)
        assertEquals(SmartLinkDecisionMode.SAME_DAY_SUGGESTION.name, link.decisionMode)
    }

    @Test
    fun oldTimingOnlyAutoLinkIsUndoneByTheNextPass() = runBlocking {
        notes.upsertNote(note("fireside", "youth fireside", OCT_3, 9, 23))
        notes.linkNoteToEventEnhanced(
            noteId = "fireside",
            eventId = "gc-morning",
            linkedDayDate = OCT_3,
            confidence = LinkConfidence.MEDIUM,
            confidenceScore = 70,
            matchReasons = listOf(MatchReason.DURING_EVENT, MatchReason.LONG_EVENT),
            suggestedEventIds = emptyList(),
            needsAttention = true,
            decisionMode = SmartLinkDecisionMode.SAME_DAY_AUTO_MEDIUM
        )

        indexer.autoLinkNotesForDate(OCT_3)

        assertNull(notes.getLinkForNote("fireside")!!.linkedEventId)
    }

    private fun millis(day: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.parse("${day}T%02d:%02d".format(hour, minute))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(id: String, title: String, day: String, startHour: Int, endHour: Int) = CalendarEvent(
        id = id,
        providerId = "GOOGLE",
        accountId = "acct",
        calendarId = "cal",
        calendarName = "Church",
        title = title,
        dayId = day,
        endDayId = day,
        startTime = millis(day, startHour),
        endTime = millis(day, endHour)
    )

    /** A note with no page files: its time is its folder's last change */
    private fun note(id: String, title: String, day: String, hour: Int, minute: Int): BooxNoteIndex {
        val time = millis(day, hour, minute)
        return BooxNoteIndex(
            uniqueId = id,
            title = title,
            originalDate = day,
            originalTimestamp = time,
            pageCount = 0,
            pageIdsJson = "[]",
            shapeFileModified = time,
            indexedAt = time
        )
    }

    private companion object {
        const val OCT_3 = "2026-10-03"
        const val OCT_10 = "2026-10-10"
    }
}
