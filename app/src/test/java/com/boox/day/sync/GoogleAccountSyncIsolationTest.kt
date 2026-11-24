package com.boox.day.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.sync.provider.GoogleCalendarProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Verifies Phase 1 H1 & H2:
 * - GoogleCalendarProvider reloads events scoped strictly by (providerId = GOOGLE, accountId, calendarId)
 *   so parallel Google accounts and same-named calendars never cross-contaminate.
 * - CalendarSyncManager retains cached events whose remote ID was present on the server even when
 *   local conversion fails or returns null, preventing deletion of cached rows and manual BOOX note links.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleAccountSyncIsolationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    private fun event(id: String, provider: String, accountId: String, calendarId: String) = CalendarEvent(
        id = id,
        providerId = provider,
        accountId = accountId,
        accountEmail = "$accountId@example.com",
        calendarId = calendarId,
        calendarName = calendarId,
        title = "Event $id",
        dayId = "2026-11-10",
        endDayId = "2026-11-10",
        startTime = 1_794_315_600_000L,
        endTime = 1_794_319_200_000L,
        isAllDay = false,
        location = null,
        description = null,
        attendees = null,
        isReadOnly = false,
        colorHex = null,
        syncedAt = 1L,
    )

    @Test
    fun loadProviderEventsForRangeIsScopedByProviderAndAccountId() = runBlocking {
        db.calendarEventDao().upsertAll(
            listOf(
                event("g-a-1", "GOOGLE", "acct-a", "primary"),
                event("g-b-1", "GOOGLE", "acct-b", "primary"),
                event("c-a-1", "CALDAV", "acct-a", "primary"),
            )
        )
        val authManager = GoogleAuthManager(context)
        val eventRepo = com.boox.day.data.db.CalendarEventRepository(context)
        val syncManager = CalendarSyncManager(context, authManager, eventRepo)
        val provider = GoogleCalendarProvider(context, authManager, syncManager, db.connectedAccountDao(), eventRepo)
        val day = LocalDate.of(2026, 11, 10)

        val acctAEvents = provider.loadProviderEventsForRange("acct-a", setOf("primary"), day, day)
        val acctBEvents = provider.loadProviderEventsForRange("acct-b", setOf("primary"), day, day)

        assertEquals(listOf("g-a-1"), acctAEvents.map { it.id })
        assertEquals(listOf("g-b-1"), acctBEvents.map { it.id })
    }

    @Test
    fun selectMissingEventsKeepsRemoteIdsPresentEvenIfLocalConversionFailed() {
        val manager = CalendarSyncManager(
            context,
            GoogleAuthManager(context),
            com.boox.day.data.db.CalendarEventRepository(context)
        )
        val existing = listOf(
            event("keep-present", "GOOGLE", "acct-a", "primary"),
            event("truly-deleted", "GOOGLE", "acct-a", "primary"),
            event("failed-cal", "GOOGLE", "acct-a", "work"),
        )

        val missing = manager.selectMissingEvents(
            existingEvents = existing,
            successfulCalendarIds = setOf("primary"),
            presentRemoteIds = setOf("keep-present"),
        )

        assertEquals(listOf("truly-deleted"), missing.map { it.id })
        assertTrue(missing.none { it.id == "keep-present" || it.id == "failed-cal" })
    }
}
