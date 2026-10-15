package com.boox.day.sync

import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.SyncStatus
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConflictResolver data classes and conflict detection logic.
 * Note: Tests that require repository interaction would need instrumentation tests.
 */
class ConflictResolverTest {

    private val testCalendarInfo = GoogleCalendarInfo(
        id = "test-calendar-id",
        name = "Test Calendar",
        isPrimary = true,
        accessRole = "owner",
        isWritable = true,
        backgroundColor = "#4285F4"
    )

    // =============================================================================
    // ConflictInfo Tests
    // =============================================================================

    @Test
    fun `ConflictInfo canAutoMerge is true when no overlapping fields`() {
        val conflict = ConflictInfo(
            localEvent = createTestLocalEvent(),
            remoteEvent = createTestRemoteEvent(),
            localChangedFields = setOf("title", "location"),
            remoteChangedFields = setOf("description"),
            overlappingFields = emptySet(),
            canAutoMerge = true
        )

        assertTrue(conflict.canAutoMerge)
        assertTrue(conflict.overlappingFields.isEmpty())
    }

    @Test
    fun `ConflictInfo canAutoMerge is false when fields overlap`() {
        val conflict = ConflictInfo(
            localEvent = createTestLocalEvent(),
            remoteEvent = createTestRemoteEvent(),
            localChangedFields = setOf("title", "location"),
            remoteChangedFields = setOf("title", "description"),
            overlappingFields = setOf("title"),
            canAutoMerge = false
        )

        assertFalse(conflict.canAutoMerge)
        assertTrue(conflict.overlappingFields.contains("title"))
    }

    @Test
    fun `ConflictInfo getConflictSummary formats correctly`() {
        val conflict = ConflictInfo(
            localEvent = createTestLocalEvent(),
            remoteEvent = createTestRemoteEvent(),
            localChangedFields = setOf("title", "location"),
            remoteChangedFields = setOf("title", "description"),
            overlappingFields = setOf("title"),
            canAutoMerge = false
        )

        val summary = conflict.getConflictSummary()

        assertTrue(summary.contains("Local changes"))
        assertTrue(summary.contains("Remote changes"))
        assertTrue(summary.contains("Conflicting fields"))
        assertTrue(summary.contains("title"))
    }

    @Test
    fun `ConflictInfo getConflictSummary omits conflicting when no overlap`() {
        val conflict = ConflictInfo(
            localEvent = createTestLocalEvent(),
            remoteEvent = createTestRemoteEvent(),
            localChangedFields = setOf("title"),
            remoteChangedFields = setOf("description"),
            overlappingFields = emptySet(),
            canAutoMerge = true
        )

        val summary = conflict.getConflictSummary()

        assertTrue(summary.contains("Local changes"))
        assertTrue(summary.contains("Remote changes"))
        assertFalse(summary.contains("Conflicting fields"))
    }

    // =============================================================================
    // ConflictResolutionStrategy Tests
    // =============================================================================

    @Test
    fun `ConflictResolutionStrategy enum has all expected values`() {
        val strategies = ConflictResolutionStrategy.values()

        assertEquals(4, strategies.size)
        assertTrue(strategies.contains(ConflictResolutionStrategy.KEEP_LOCAL))
        assertTrue(strategies.contains(ConflictResolutionStrategy.KEEP_REMOTE))
        assertTrue(strategies.contains(ConflictResolutionStrategy.KEEP_BOTH))
        assertTrue(strategies.contains(ConflictResolutionStrategy.AUTO_MERGE))
    }

    // =============================================================================
    // ConflictResolutionResult Tests
    // =============================================================================

    @Test
    fun `ConflictResolutionResult Resolved contains expected data`() {
        val resolvedEvent = createTestLocalEvent()
        val result = ConflictResolutionResult.Resolved(
            resolvedEvent = resolvedEvent,
            strategy = ConflictResolutionStrategy.KEEP_LOCAL,
            message = "Test message"
        )

        assertTrue(result is ConflictResolutionResult.Resolved)
        assertEquals(resolvedEvent, result.resolvedEvent)
        assertEquals(ConflictResolutionStrategy.KEEP_LOCAL, result.strategy)
        assertEquals("Test message", result.message)
    }

    @Test
    fun `ConflictResolutionResult ResolvedWithCopy contains both events`() {
        val resolvedEvent = createTestLocalEvent(id = "resolved")
        val localCopy = createTestLocalEvent(id = "copy")
        val result = ConflictResolutionResult.ResolvedWithCopy(
            resolvedEvent = resolvedEvent,
            localCopy = localCopy,
            strategy = ConflictResolutionStrategy.KEEP_BOTH,
            message = "Created copy"
        )

        assertTrue(result is ConflictResolutionResult.ResolvedWithCopy)
        assertEquals("resolved", result.resolvedEvent.id)
        assertEquals("copy", result.localCopy.id)
        assertEquals(ConflictResolutionStrategy.KEEP_BOTH, result.strategy)
    }

    @Test
    fun `ConflictResolutionResult CannotAutoMerge contains conflict info`() {
        val conflict = ConflictInfo(
            localEvent = createTestLocalEvent(),
            remoteEvent = createTestRemoteEvent(),
            localChangedFields = setOf("title"),
            remoteChangedFields = setOf("title"),
            overlappingFields = setOf("title"),
            canAutoMerge = false
        )
        val result = ConflictResolutionResult.CannotAutoMerge(
            conflict = conflict,
            message = "Fields conflict"
        )

        assertTrue(result is ConflictResolutionResult.CannotAutoMerge)
        assertEquals(conflict, result.conflict)
        assertTrue(result.message.contains("Fields"))
    }

    @Test
    fun `ConflictResolutionResult Error contains exception info`() {
        val exception = RuntimeException("Test error")
        val result = ConflictResolutionResult.Error(
            message = "Something went wrong",
            exception = exception
        )

        assertTrue(result is ConflictResolutionResult.Error)
        assertEquals("Something went wrong", result.message)
        assertEquals(exception, result.exception)
    }

    // =============================================================================
    // Conflict Detection Integration Tests (using EventConverters)
    // =============================================================================

    @Test
    fun `detectConflict scenario - SYNCED event has no conflict`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.SYNCED,
            serverEtag = "old-etag"
        )
        val remoteEvent = createTestRemoteEvent(etag = "new-etag")

        // hasConflictWith returns false for SYNCED events
        assertFalse(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `detectConflict scenario - PENDING_UPDATE with different etag has conflict`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = "old-etag"
        )
        val remoteEvent = createTestRemoteEvent(etag = "new-etag")

        assertTrue(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `detectConflict scenario - PENDING_DELETE with different etag has conflict`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_DELETE,
            serverEtag = "old-etag"
        )
        val remoteEvent = createTestRemoteEvent(etag = "new-etag")

        assertTrue(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `detectConflict scenario - matching etags means no conflict`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = "same-etag"
        )
        val remoteEvent = createTestRemoteEvent(etag = "same-etag")

        assertFalse(localEvent.hasConflictWith(remoteEvent))
    }

    @Test
    fun `detectConflict scenario - remote updated timestamp after last sync`() {
        val localEvent = createTestLocalEvent(
            syncStatus = SyncStatus.PENDING_UPDATE,
            serverEtag = null,
            lastSyncedAt = 1702800000000L
        )
        val remoteEvent = Event().apply {
            updated = DateTime(1702900000000L) // After last sync
        }

        assertTrue(localEvent.hasConflictWith(remoteEvent))
    }

    // =============================================================================
    // Field Change Detection Tests (using EventConverters)
    // =============================================================================

    @Test
    fun `getLocallyChangedFields always includes title and time`() {
        val localEvent = createTestLocalEvent()
        val changedFields = localEvent.getLocallyChangedFields()

        assertTrue(changedFields.contains("title"))
        assertTrue(changedFields.contains("time"))
    }

    @Test
    fun `getLocallyChangedFields includes non-blank fields`() {
        val localEvent = createTestLocalEvent(
            location = "Room 101",
            description = "Test description"
        )
        val changedFields = localEvent.getLocallyChangedFields()

        assertTrue(changedFields.contains("location"))
        assertTrue(changedFields.contains("description"))
    }

    @Test
    fun `getRemotelyChangedFields detects title change`() {
        val localEvent = createTestLocalEvent(title = "Local Title")
        val remoteEvent = createTestRemoteEvent(summary = "Remote Title")

        val changedFields = localEvent.getRemotelyChangedFields(remoteEvent)

        assertTrue(changedFields.contains("title"))
    }

    @Test
    fun `getRemotelyChangedFields detects location change`() {
        val localEvent = createTestLocalEvent(location = "Local Room")
        val remoteEvent = createTestRemoteEvent(location = "Remote Room")

        val changedFields = localEvent.getRemotelyChangedFields(remoteEvent)

        assertTrue(changedFields.contains("location"))
    }

    // =============================================================================
    // Test Helpers
    // =============================================================================

    private fun createTestLocalEvent(
        id: String = "test-local-id",
        title: String = "Test Event",
        dayId: String = "2023-12-18",
        startTime: Long = 1702900800000L,
        endTime: Long? = 1702904400000L,
        location: String? = null,
        description: String? = null,
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
            isAllDay = false,
            location = location,
            description = description,
            colorHex = null,
            attendees = null,
            syncEnabled = true,
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

    private fun createTestRemoteEvent(
        id: String = "google-123",
        summary: String = "Test Event",
        location: String? = null,
        description: String? = null,
        etag: String = "test-etag",
        startTime: Long = 1702900800000L,
        endTime: Long = 1702904400000L
    ): Event {
        return Event().apply {
            this.id = id
            this.summary = summary
            this.location = location
            this.description = description
            this.etag = etag
            start = EventDateTime().setDateTime(DateTime(startTime))
            end = EventDateTime().setDateTime(DateTime(endTime))
        }
    }
}
