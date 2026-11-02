package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.getDbDir
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.viewmodel.shownEventCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Synced events changed on the tablet (Nov 2026): an edit or delete made offline survives a sync
 * that runs before it is sent; the tablet's copy of a synced event leaves with it (an event deleted
 * on the phone came back as a This device event), an event never sent stays when its account goes;
 * the Events tab counts each event once, as the filter shows them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineEventChangesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var events: CalendarEventRepository
    private lateinit var localEvents: LocalEventRepository
    private lateinit var changes: OfflineEventChanges

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = AppDatabase.getDatabase(context)
        events = CalendarEventRepository(context)
        localEvents = LocalEventRepository(context)
        changes = OfflineEventChanges(localEvents, db.localEventDao(), events)
        runBlocking(Dispatchers.IO) {
            db.connectedAccountDao().upsert(ConnectedAccountEntity.fromDomainModel(ConnectedAccount("acc", ProviderId.CALDAV, AccountType.CALDAV_OTHER, "me", serverUrl = "https://dav.example.com/")))
            db.calendarSelectionDao().upsert(CalendarSelectionEntity("sel", "acc", "/cal/", "Home", null, isFetchEnabled = true))
        }
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun anOfflineEditOrDeleteSurvivesASyncThatRunsFirst(): Unit = runBlocking<Unit>(Dispatchers.IO) {
        val dentist = cloud("caldav:acc:d", "Dentist")
        val yoga = cloud("caldav:acc:y", "Yoga")
        events.cacheEvents(listOf(dentist, yoga))

        changes.keepEdit(dentist, dentist.copy(title = "Dentist (moved)", location = "Clinic B"))
        changes.keepDelete(yoga)
        assertEquals(SyncStatus.PENDING_UPDATE, db.localEventDao().getByProviderEventId(dentist.id)?.syncStatus)
        assertEquals(SyncStatus.PENDING_DELETE, db.localEventDao().getByProviderEventId(yoga.id)?.syncStatus)
        assertNull("gone from the calendar at once", events.getEventById(yoga.id))

        // A sync before the push: the server still has both as they were
        val kept = changes.withWaitingChanges("acc", fetched = listOf(dentist, yoga), cached = events.getEventsInRange("2026-11-01", "2026-11-30"))
        assertEquals(listOf("Dentist (moved)"), kept.map { it.title })
    }

    @Test
    fun copiesLeaveWithTheirSyncedEventAndTheTabCountsEachOnce(): Unit = runBlocking<Unit>(Dispatchers.IO) {
        // Made on the tablet and synced: the synced event and its tablet copy
        val lunch = cloud("caldav:acc:l", "Lunch")
        events.cacheEvents(listOf(lunch))
        db.localEventDao().insert(copyOf(lunch, SyncStatus.SYNCED))
        // The tablet copy isn't counted beside its synced event
        assertEquals(0, db.localEventDao().observeShownEventCountForDate(DAY).first())
        val byProvider = db.calendarEventDao().observeCountsByProviderForDate(DAY).first().associate { it.providerId to it.count }
        assertEquals(1, shownEventCount(byProvider, 0, CalendarSourceFilter()))
        assertEquals("the CalDAV switch off hides it", 0, shownEventCount(byProvider, 0, CalendarSourceFilter(showCalDav = false)))

        // Deleted on the phone: the sync removes it, and the copy goes too
        events.removeEvents(listOf(lunch.id), "cloud sync removed missing events")
        assertNull(db.localEventDao().getByProviderEventId(lunch.id))

        // The account is removed: an event made offline and never sent stays on the tablet
        val never = LocalEvent(title = "Haircut", dayId = DAY, endDayId = DAY, startTime = START, endTime = START + HOUR,
            providerId = "CALDAV", accountId = "acc", providerCalendarId = "/cal/", syncEnabled = true, syncStatus = SyncStatus.PENDING_CREATE)
        db.localEventDao().insert(never)
        events.removeEventsForAccount("acc")
        val kept = db.localEventDao().getById(never.id)!!
        assertEquals(SyncStatus.LOCAL_ONLY to null, kept.syncStatus to kept.accountId)
    }

    private fun cloud(id: String, title: String) = CalendarEvent(
        id = id, providerId = "CALDAV", accountId = "acc", accountEmail = "me", calendarId = "/cal/", calendarName = "Home",
        title = title, dayId = DAY, endDayId = DAY, startTime = START, endTime = START + HOUR
    )

    private fun copyOf(event: CalendarEvent, status: SyncStatus) = LocalEvent(
        title = event.title, dayId = event.dayId, endDayId = event.endDayId, startTime = event.startTime, endTime = event.endTime,
        providerId = "CALDAV", accountId = "acc", providerEventId = event.id, providerCalendarId = "/cal/",
        syncEnabled = true, syncStatus = status, lastSyncedAt = 1L
    )

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val DAY = "2026-11-04"
        val START = java.time.LocalDate.parse(DAY).atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val HOUR = 3_600_000L
    }
}
