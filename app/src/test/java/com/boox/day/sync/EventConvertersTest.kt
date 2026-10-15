package com.boox.day.sync

import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.SyncStatus
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Unit tests for EventConverters extension functions.
 * Tests conversion between LocalEvent, CalendarEvent, and Google Calendar API Event.
 */
class EventConvertersTest {

    private val testCalendarInfo = GoogleCalendarInfo(
        id = "test-calendar-id",
        name = "Test Calendar",
        isPrimary = true,
        accessRole = "owner",
        isWritable = true,
        backgroundColor = "#4285F4"
    )

    private val readOnlyCalendarInfo = GoogleCalendarInfo(
        id = "readonly-calendar-id",
        name = "Read Only Calendar",
        isPrimary = false,
        accessRole = "reader",
        isWritable = false,
        backgroundColor = "#EA4335"
    )

    // =============================================================================
    // Google API Event → LocalEvent Tests
    // =============================================================================

    @Test
    fun `toLocalEvent converts timed event correctly`() {
        val googleEvent = Event().apply {
            id = "google-event-123"
            summary = "Test Meeting"
            location = "Conference Room A"
            description = "Discuss project updates"
            start = EventDateTime().setDateTime(DateTime(1702900800000L)) // 2023-12-18T10:00:00
            end = EventDateTime().setDateTime(DateTime(1702904400000L))   // 2023-12-18T11:00:00
            etag = "\"etag-value\""
            attendees = listOf(
                EventAttendee().setEmail("user1@example.com"),
                EventAttendee().setEmail("user2@example.com")
            )
        }

        val localEvent = googleEvent.toLocalEvent(testCalendarInfo)

        assertNotNull(localEvent)
        localEvent!!

        assertEquals("Test Meeting", localEvent.title)
        assertEquals("Conference Room A", localEvent.location)
        assertEquals("Discuss project updates", localEvent.description)
        assertEquals(1702900800000L, localEvent.startTime)
        assertEquals(1702904400000L, localEvent.endTime)
        assertFalse(localEvent.isAllDay)
        assertEquals("google-event-123", localEvent.googleEventId)
        assertEquals("test-calendar-id", localEvent.googleCalendarId)
        assertEquals("Test Calendar", localEvent.googleCalendarName)
        assertEquals("#4285F4", localEvent.colorHex)
        assertEquals("\"etag-value\"", localEvent.serverEtag)
        assertEquals(SyncStatus.SYNCED, localEvent.syncStatus)
        assertTrue(localEvent.syncEnabled)
        assertFalse(localEvent.isReadOnly)
        assertEquals("user1@example.com,user2@example.com", localEvent.attendees)
    }

    @Test
    fun `toLocalEvent converts all-day event correctly`() {
        withDefaultTimeZone("America/Los_Angeles") {
            val googleEvent = Event().apply {
                id = "all-day-event-123"
                summary = "Holiday"
                start = EventDateTime().setDate(DateTime("2026-03-17"))
                end = EventDateTime().setDate(DateTime("2026-03-18"))
            }

            val localEvent = googleEvent.toLocalEvent(testCalendarInfo)

            assertNotNull(localEvent)
            localEvent!!

            assertTrue(localEvent.isAllDay)
            assertEquals("Holiday", localEvent.title)
            assertEquals("2026-03-17", localEvent.dayId)
            assertEquals("2026-03-17", localEvent.endDayId)
            assertEquals(startOfDayMillis("2026-03-17"), localEvent.startTime)
            assertEquals(startOfDayMillis("2026-03-18"), localEvent.endTime)
        }
    }

    @Test
    fun `toLocalEvent keeps multi-day all-day end date inclusive`() {
        withDefaultTimeZone("America/Los_Angeles") {
            val googleEvent = Event().apply {
                id = "multi-day-all-day-123"
                summary = "Conference"
                start = EventDateTime().setDate(DateTime("2026-04-02"))
                end = EventDateTime().setDate(DateTime("2026-04-05"))
            }

            val localEvent = googleEvent.toLocalEvent(testCalendarInfo)

            assertNotNull(localEvent)
            assertEquals("2026-04-02", localEvent!!.dayId)
            assertEquals("2026-04-04", localEvent.endDayId)
            assertEquals(startOfDayMillis("2026-04-05"), localEvent.endTime)
        }
    }

    @Test
    fun `toLocalEvent returns null for event without start time`() {
        val googleEvent = Event().apply {
            id = "invalid-event"
            summary = "No Start Time"
        }

        val localEvent = googleEvent.toLocalEvent(testCalendarInfo)

        assertNull(localEvent)
    }

    @Test
    fun `toLocalEvent preserves existing local event fields when updating`() {
        val existingLocalEvent = LocalEvent(
            id = "local-uuid-123",
            title = "Old Title",
            dayId = "2023-12-17",
            startTime = 1702800000000L,
            endTime = null,
            isAllDay = false,
            location = null,
            description = null,
            colorHex = null,
            attendees = null,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            googleEventId = "google-event-123",
            googleCalendarId = "test-calendar-id",
            googleCalendarName = "Test Calendar",
            lastSyncedAt = 1702800000000L,
            serverEtag = "old-etag",
            isReadOnly = false,
            createdAt = 1702700000000L,
            updatedAt = 1702800000000L,
            linkedMemoPageId = "memo-page-123"
        )

        val googleEvent = Event().apply {
            id = "google-event-123"
            summary = "Updated Title"
            start = EventDateTime().setDateTime(DateTime(1702900800000L))
            end = EventDateTime().setDateTime(DateTime(1702904400000L))
            etag = "new-etag"
        }

        val localEvent = googleEvent.toLocalEvent(testCalendarInfo, existingLocalEvent)

        assertNotNull(localEvent)
        localEvent!!

        // Should preserve local-only fields
        assertEquals("local-uuid-123", localEvent.id)
        assertEquals(1702700000000L, localEvent.createdAt)
        assertEquals("memo-page-123", localEvent.linkedMemoPageId)

        // Should update remote fields
        assertEquals("Updated Title", localEvent.title)
        assertEquals("new-etag", localEvent.serverEtag)
    }

    @Test
    fun `toLocalEvent marks read-only calendar events as read-only`() {
        val googleEvent = Event().apply {
            id = "readonly-event"
            summary = "Shared Event"
            start = EventDateTime().setDateTime(DateTime(1702900800000L))
            end = EventDateTime().setDateTime(DateTime(1702904400000L))
        }

        val localEvent = googleEvent.toLocalEvent(readOnlyCalendarInfo)

        assertNotNull(localEvent)
        assertTrue(localEvent!!.isReadOnly)
    }

    // =============================================================================
    // LocalEvent → Google API Event Tests
    // =============================================================================

    @Test
    fun `toGoogleEvent converts timed event correctly`() {
        val localEvent = LocalEvent(
            id = "local-123",
            title = "Team Meeting",
            dayId = "2023-12-18",
            startTime = 1702900800000L,
            endTime = 1702904400000L,
            isAllDay = false,
            location = "Room 101",
            description = "Weekly sync",
            colorHex = "#4285F4",
            attendees = "alice@example.com,bob@example.com",
            syncEnabled = true,
            syncStatus = SyncStatus.PENDING_CREATE,
            googleEventId = null,
            googleCalendarId = "test-calendar-id",
            googleCalendarName = "Test Calendar",
            lastSyncedAt = null,
            serverEtag = null,
            isReadOnly = false,
            createdAt = 1702800000000L,
            updatedAt = 1702800000000L,
            linkedMemoPageId = null
        )

        val googleEvent = localEvent.toGoogleEvent()

        assertEquals("Team Meeting", googleEvent.summary)
        assertEquals("Room 101", googleEvent.location)
        assertEquals("Weekly sync", googleEvent.description)
        assertNotNull(googleEvent.start?.dateTime)
        assertNotNull(googleEvent.end?.dateTime)
        assertNull(googleEvent.start?.date)
        assertEquals(2, googleEvent.attendees?.size)
    }

    @Test
    fun `toGoogleEvent converts all-day event correctly`() {
        val localEvent = LocalEvent(
            id = "local-456",
            title = "Company Holiday",
            dayId = "2023-12-25",
            endDayId = "2023-12-25",
            startTime = 1703462400000L,
            endTime = null,
            isAllDay = true,
            location = null,
            description = "Christmas Day",
            colorHex = null,
            attendees = null,
            syncEnabled = true,
            syncStatus = SyncStatus.PENDING_CREATE,
            googleEventId = null,
            googleCalendarId = "test-calendar-id",
            googleCalendarName = "Test Calendar",
            lastSyncedAt = null,
            serverEtag = null,
            isReadOnly = false,
            createdAt = 1702800000000L,
            updatedAt = 1702800000000L,
            linkedMemoPageId = null
        )

        val googleEvent = localEvent.toGoogleEvent()

        assertEquals("Company Holiday", googleEvent.summary)
        assertNotNull(googleEvent.start?.date)
        assertNull(googleEvent.start?.dateTime)
        assertEquals("2023-12-25", googleEvent.start?.date?.toStringRfc3339())
        assertEquals("2023-12-26", googleEvent.end?.date?.toStringRfc3339())
    }

    @Test
    fun `mergeWithRemote updates all-day end date from google`() {
        withDefaultTimeZone("America/Los_Angeles") {
            val localEvent = LocalEvent(
                id = "local-merge-1",
                title = "Old Holiday",
                dayId = "2026-03-17",
                endDayId = "2026-03-17",
                startTime = startOfDayMillis("2026-03-17"),
                endTime = startOfDayMillis("2026-03-18"),
                isAllDay = true,
                syncEnabled = true,
                syncStatus = SyncStatus.SYNCED,
                googleEventId = "google-merge-1",
                googleCalendarId = "test-calendar-id",
                googleCalendarName = "Test Calendar",
                lastSyncedAt = 1702800000000L,
                serverEtag = "old-etag",
                isReadOnly = false,
                createdAt = 1702700000000L,
                updatedAt = 1702800000000L,
                linkedMemoPageId = null
            )

            val remoteEvent = Event().apply {
                id = "google-merge-1"
                summary = "Updated Holiday"
                start = EventDateTime().setDate(DateTime("2026-03-17"))
                end = EventDateTime().setDate(DateTime("2026-03-20"))
                etag = "new-etag"
            }

            val merged = localEvent.mergeWithRemote(remoteEvent, testCalendarInfo)

            assertEquals("Updated Holiday", merged.title)
            assertEquals("2026-03-17", merged.dayId)
            assertEquals("2026-03-19", merged.endDayId)
            assertEquals(startOfDayMillis("2026-03-20"), merged.endTime)
            assertEquals("new-etag", merged.serverEtag)
        }
    }

    @Test
    fun `getRemotelyChangedFields does not flag matching all-day google event as time change`() {
        withDefaultTimeZone("America/Los_Angeles") {
            val localEvent = LocalEvent(
                id = "local-remote-fields-1",
                title = "Holiday",
                dayId = "2026-03-17",
                endDayId = "2026-03-19",
                startTime = startOfDayMillis("2026-03-17"),
                endTime = startOfDayMillis("2026-03-20"),
                isAllDay = true,
                syncEnabled = true,
                syncStatus = SyncStatus.SYNCED,
                googleEventId = "google-remote-fields-1",
                googleCalendarId = "test-calendar-id",
                googleCalendarName = "Test Calendar",
                lastSyncedAt = 1702800000000L,
                serverEtag = "etag",
                isReadOnly = false,
                createdAt = 1702700000000L,
                updatedAt = 1702800000000L,
                linkedMemoPageId = null
            )

            val remoteEvent = Event().apply {
                id = "google-remote-fields-1"
                summary = "Holiday"
                start = EventDateTime().setDate(DateTime("2026-03-17"))
                end = EventDateTime().setDate(DateTime("2026-03-20"))
            }

            assertFalse("time" in localEvent.getRemotelyChangedFields(remoteEvent))
        }
    }

    @Test
    fun `toGoogleEvent sets default end time for timed event without end`() {
        val localEvent = LocalEvent(
            id = "local-789",
            title = "Quick Meeting",
            dayId = "2023-12-18",
            startTime = 1702900800000L,
            endTime = null, // No end time
            isAllDay = false,
            location = null,
            description = null,
            colorHex = null,
            attendees = null,
            syncEnabled = true,
            syncStatus = SyncStatus.PENDING_CREATE,
            googleEventId = null,
            googleCalendarId = "test-calendar-id",
            googleCalendarName = null,
            lastSyncedAt = null,
            serverEtag = null,
            isReadOnly = false,
            createdAt = 1702800000000L,
            updatedAt = 1702800000000L,
            linkedMemoPageId = null
        )

        val googleEvent = localEvent.toGoogleEvent()

        // Should default to 1 hour duration
        val expectedEnd = 1702900800000L + 60 * 60 * 1000
        assertEquals(expectedEnd, googleEvent.end?.dateTime?.value)
    }

    // =============================================================================
    // CalendarEvent → LocalEvent (Migration) Tests
    // =============================================================================

    @Test
    fun `CalendarEvent toLocalEvent converts correctly`() {
        val calendarEvent = CalendarEvent(
            id = "google-legacy-123",
            calendarId = "legacy-calendar-id",
            calendarName = "Legacy Calendar",
            title = "Legacy Event",
            dayId = "2023-12-18",
            startTime = 1702900800000L,
            endTime = 1702904400000L,
            isAllDay = false,
            location = "Old Room",
            description = "Migrated event",
            attendees = "legacy@example.com",
            isReadOnly = true,
            colorHex = "#FBBC04",
            syncedAt = 1702800000000L
        )

        val localEvent = calendarEvent.toLocalEvent()

        // Local event should have new UUID, not Google ID
        assertNotEquals("google-legacy-123", localEvent.id)
        assertTrue(localEvent.id.isNotBlank())

        // But Google ID should be stored separately
        assertEquals("google-legacy-123", localEvent.googleEventId)
        assertEquals("legacy-calendar-id", localEvent.googleCalendarId)
        assertEquals("Legacy Calendar", localEvent.googleCalendarName)

        // Core data should be preserved
        assertEquals("Legacy Event", localEvent.title)
        assertEquals("2023-12-18", localEvent.dayId)
        assertEquals(1702900800000L, localEvent.startTime)
        assertEquals(1702904400000L, localEvent.endTime)
        assertEquals("Old Room", localEvent.location)
        assertEquals("legacy@example.com", localEvent.attendees)
        assertTrue(localEvent.isReadOnly)
        assertEquals("#FBBC04", localEvent.colorHex)

        // Sync status should be SYNCED (came from Google)
        assertTrue(localEvent.syncEnabled)
        assertEquals(SyncStatus.SYNCED, localEvent.syncStatus)
    }

    // =============================================================================
    // Conflict Detection Tests
    // =============================================================================

    @Test
    fun `hasConflictWith returns false for SYNCED event`() {
        val localEvent = createTestLocalEvent(syncStatus = SyncStatus.SYNCED)
        val remoteEvent = Event().apply {
            etag = "different-etag"
        }

        assertFalse(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `hasConflictWith returns true when etags differ for PENDING_UPDATE`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = "local-etag"
        )
        val remoteEvent = Event().apply {
            etag = "remote-etag"
        }

        assertTrue(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `hasConflictWith returns false when etags match`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = "same-etag"
        )
        val remoteEvent = Event().apply {
            etag = "same-etag"
        }

        assertFalse(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `hasConflictWith returns true when remote updated after last sync`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = null,
            lastSyncedAt = 1702800000000L
        )
        val remoteEvent = Event().apply {
            updated = DateTime(1702900000000L) // After local lastSyncedAt
        }

        assertTrue(localEvent.hasConflictWith(remoteEvent))
    }

    // =============================================================================
    // Helper Functions Tests
    // =============================================================================

    @Test
    fun `getSyncStatusDisplay returns correct strings`() {
        assertEquals("Local only", createTestLocalEvent(syncEnabled = false).getSyncStatusDisplay())
        assertEquals("Synced", createTestLocalEvent(syncStatus = SyncStatus.SYNCED).getSyncStatusDisplay())
        assertEquals("Pending upload", createTestLocalEvent(syncStatus = SyncStatus.PENDING_CREATE).getSyncStatusDisplay())
        assertEquals("Pending update", createTestLocalEvent(syncStatus = SyncStatus.PENDING_UPDATE).getSyncStatusDisplay())
        assertEquals("Pending deletion", createTestLocalEvent(syncStatus = SyncStatus.PENDING_DELETE).getSyncStatusDisplay())
        assertEquals("Conflict", createTestLocalEvent(syncStatus = SyncStatus.CONFLICT).getSyncStatusDisplay())
    }

    @Test
    fun `canAutoMergeWith returns true for non-overlapping changes`() {
        val localEvent = createTestLocalEvent(
            title = "Local Title",
            location = "Local Location",
            description = null
        )
        val remoteEvent = Event().apply {
            summary = "Local Title" // Same title
            location = "Local Location" // Same location
            description = "Remote Description" // Only description changed remotely
        }

        // Since local has description null, it's not in localChanges
        // Remote changed description, so they don't overlap
        assertTrue(localEvent.canAutoMergeWith(remoteEvent))
    }

    @Test
    fun `toCalendarEvent uses provider fields when present`() {
        val localEvent = LocalEvent(
            id = "local-1",
            title = "Provider Event",
            dayId = "2024-01-10",
            startTime = 1704873600000L,
            endTime = 1704877200000L,
            providerId = "OUTLOOK",
            accountId = "acc-1",
            accountEmail = "user@example.com",
            providerEventId = "evt-1",
            providerCalendarId = "cal-1",
            providerCalendarName = "Work"
        )

        val calendarEvent = localEvent.toCalendarEvent()

        assertEquals("OUTLOOK", calendarEvent.providerId)
        assertEquals("acc-1", calendarEvent.accountId)
        assertEquals("user@example.com", calendarEvent.accountEmail)
        assertEquals("evt-1", calendarEvent.id)
        assertEquals("cal-1", calendarEvent.calendarId)
        assertEquals("Work", calendarEvent.calendarName)
    }

    @Test
    fun `toCalendarEvent uses legacy google fields for migrated events`() {
        val localEvent = LocalEvent(
            id = "local-2",
            title = "Legacy Google",
            dayId = "2024-01-10",
            startTime = 1704873600000L,
            endTime = 1704877200000L,
            providerId = "LOCAL",
            googleEventId = "g-evt",
            googleCalendarId = "g-cal",
            googleCalendarName = "Google Calendar"
        )

        val calendarEvent = localEvent.toCalendarEvent()

        assertEquals("GOOGLE", calendarEvent.providerId)
        assertEquals("g-evt", calendarEvent.id)
        assertEquals("g-cal", calendarEvent.calendarId)
        assertEquals("Google Calendar", calendarEvent.calendarName)
    }

    @Test
    fun `toCalendarEvent returns empty providerId for local-only`() {
        val localEvent = LocalEvent(
            id = "local-3",
            title = "Local Only",
            dayId = "2024-01-10",
            startTime = 1704873600000L,
            endTime = 1704877200000L,
            providerId = "LOCAL"
        )

        val calendarEvent = localEvent.toCalendarEvent()

        assertEquals("", calendarEvent.providerId)
        assertEquals("local", calendarEvent.calendarId)
        assertEquals("Local Events", calendarEvent.calendarName)
    }

    // =============================================================================
    // Test Helpers
    // =============================================================================

    private fun createTestLocalEvent(
        id: String = "test-id",
        title: String = "Test Event",
        dayId: String = "2023-12-18",
        startTime: Long = 1702900800000L,
        endTime: Long? = 1702904400000L,
        isAllDay: Boolean = false,
        location: String? = null,
        description: String? = null,
        syncEnabled: Boolean = true,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        serverEtag: String? = null,
        lastSyncedAt: Long? = 1702800000000L
    ): LocalEvent {
        return LocalEvent(
            id = id,
            title = title,
            dayId = dayId,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location,
            description = description,
            colorHex = null,
            attendees = null,
            syncEnabled = syncEnabled,
            syncStatus = syncStatus,
            googleEventId = "google-123",
            googleCalendarId = "calendar-123",
            googleCalendarName = "Test Calendar",
            lastSyncedAt = lastSyncedAt,
            serverEtag = serverEtag,
            isReadOnly = false,
            createdAt = 1702700000000L,
            updatedAt = 1702800000000L,
            linkedMemoPageId = null
        )
    }

    private fun startOfDayMillis(dayId: String): Long {
        return LocalDate.parse(dayId)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun <T> withDefaultTimeZone(zoneId: String, block: () -> T): T {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
