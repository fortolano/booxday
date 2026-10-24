package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * SyncStatus enum for tracking the synchronization state of local events.
 * This is the source of truth for determining what needs to be pushed to or pulled from Google Calendar.
 */
enum class SyncStatus {
    LOCAL_ONLY,        // Never synced, user chose not to sync
    PENDING_CREATE,    // Created locally, needs to be pushed to Google
    PENDING_UPDATE,    // Modified locally, needs to be pushed to Google
    PENDING_DELETE,    // Deleted locally, needs to be deleted from Google
    SYNCED,           // In sync with Google
    CONFLICT          // Local and remote differ, needs resolution
}

/**
 * LocalEvent entity - The unified source of truth for all calendar events.
 *
 * This entity supports both standalone mode (local-only events) and hybrid mode
 * (events synced with Google Calendar). The key difference from CalendarEvent:
 * - CalendarEvent was a read-only cache of Google Calendar data
 * - LocalEvent is the source of truth, with optional sync to Google
 *
 * Design principles:
 * - id: Always a local UUID (never Google's event ID)
 * - googleEventId: Links to Google Calendar when synced (nullable)
 * - syncEnabled: Per-event toggle for sync preference
 * - syncStatus: Tracks pending operations for bidirectional sync
 */
@Entity(
    tableName = "local_events",
    indices = [
        Index(value = ["dayId"]),
        Index(value = ["endDayId"]),
        Index(value = ["googleEventId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["localCalendarId"]),  // For filtering events by local calendar
        Index(value = ["providerId", "accountId"])
    ]
)
data class LocalEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Core event data
    val title: String,
    val dayId: String,                    // "yyyy-MM-dd" start date for multi-day range queries
    val endDayId: String = "",            // "yyyy-MM-dd" end date (inclusive). Same as dayId for single-day events.
    val startTime: Long,                  // Epoch millis
    val endTime: Long? = null,            // null for all-day events without specific end
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val colorHex: String? = null,         // User-selected or calendar color
    val attendees: String? = null,        // Comma-separated email list (matches CalendarEvent pattern)

    // Sync metadata
    val syncEnabled: Boolean = false,     // Per-event sync toggle
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val googleEventId: String? = null,    // Google event ID (null if local-only)
    val googleCalendarId: String? = null, // Target Google calendar (null if local-only)
    val googleCalendarName: String? = null, // Display name of Google calendar
    val providerId: String = "LOCAL",     // "LOCAL", "GOOGLE", "OUTLOOK"
    val accountId: String? = null,        // Connected account ID (null for local-only)
    val accountEmail: String? = null,     // Denormalized for display
    val providerEventId: String? = null,  // Provider event ID (any provider)
    val providerCalendarId: String? = null, // Provider calendar ID (any provider)
    val providerCalendarName: String? = null, // Provider calendar display name
    val lastSyncedAt: Long? = null,       // Last successful sync timestamp
    val serverEtag: String? = null,       // For conflict detection

    // Local metadata
    val isReadOnly: Boolean = false,      // For synced events from shared calendars
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Optional: Linked memo support (existing feature from Page.linkedEventId)
    val linkedMemoPageId: String? = null,  // FK to Page for event memos

    // Local calendar assignment (for Standalone mode organization)
    // null = Default calendar (for backward compatibility)
    // FK to LocalCalendar.id
    val localCalendarId: String? = null
) {
    /**
     * Parse attendees string to list
     */
    fun getAttendeesList(): List<String> =
        attendees?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()

    /**
     * Check if event has local changes pending sync
     */
    fun hasPendingChanges(): Boolean = syncStatus in listOf(
        SyncStatus.PENDING_CREATE,
        SyncStatus.PENDING_UPDATE,
        SyncStatus.PENDING_DELETE
    )

    /**
     * Check if event needs conflict resolution
     */
    fun hasConflict(): Boolean = syncStatus == SyncStatus.CONFLICT

    /**
     * Check if event is local-only (never synced or sync disabled)
     */
    fun isLocalOnly(): Boolean = providerId == "LOCAL" ||
        (!syncEnabled || syncStatus == SyncStatus.LOCAL_ONLY)

    /**
     * Check if event originated from Outlook.
     */
    fun isOutlookOrigin(): Boolean = providerId == "OUTLOOK"

    /**
     * Check if event originated from Google.
     */
    fun isGoogleOrigin(): Boolean = providerId == "GOOGLE" ||
        (providerId == "LOCAL" && googleEventId != null)

    /**
     * Check if event is synced to any cloud provider.
     */
    fun isSyncedToCloud(): Boolean = providerId in listOf("GOOGLE", "OUTLOOK") &&
        (providerEventId != null || googleEventId != null)

    /**
     * Event IDs that Smart Link may have persisted for this event.
     * Local-only events use the local UUID; synced events may use provider IDs in UI flows.
     */
    fun linkedEventIdsForSmartLinks(): Set<String> = setOfNotNull(
        providerEventId,
        googleEventId,
        id
    )

    /**
     * Convert LocalEvent to CalendarEvent for UI compatibility.
     * Used when displaying events that may be local-only or synced.
     */
    fun toCalendarEvent(): CalendarEvent = CalendarEvent(
        id = providerEventId ?: googleEventId ?: id,
        providerId = when {
            providerId != "LOCAL" -> providerId
            googleEventId != null -> "GOOGLE"
            else -> ""
        },
        accountId = accountId ?: "",
        accountEmail = accountEmail ?: "",
        calendarId = providerCalendarId ?: googleCalendarId ?: "local",
        calendarName = providerCalendarName ?: googleCalendarName ?: "Local Events",
        title = title,
        dayId = dayId,
        endDayId = endDayId.ifEmpty { dayId },
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        location = location,
        description = description,
        attendees = attendees,
        isReadOnly = isReadOnly,
        colorHex = colorHex,
        syncedAt = lastSyncedAt ?: createdAt
    )
}

/**
 * Data Access Object for LocalEvent entity.
 * Provides all database operations for the unified event storage.
 */
@Dao
interface LocalEventDao {
    // ========== Basic CRUD ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LocalEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LocalEvent>)

    @Update
    suspend fun update(event: LocalEvent)

    @Delete
    suspend fun delete(event: LocalEvent)

    @Query("DELETE FROM local_events WHERE id = :id")
    suspend fun deleteById(id: String)

    // ========== Queries by ID ==========

    @Query("SELECT * FROM local_events WHERE id = :id")
    suspend fun getById(id: String): LocalEvent?

    @Query("SELECT * FROM local_events WHERE googleEventId = :googleEventId")
    suspend fun getByGoogleEventId(googleEventId: String): LocalEvent?

    @Query("SELECT * FROM local_events WHERE providerEventId = :providerEventId")
    suspend fun getByProviderEventId(providerEventId: String): LocalEvent?

    @Query("""
        SELECT * FROM local_events
        WHERE syncStatus != 'PENDING_DELETE'
        ORDER BY dayId, isAllDay DESC, startTime ASC
    """)
    suspend fun getAllActiveEvents(): List<LocalEvent>

    @Query("""
        SELECT * FROM local_events
        WHERE syncStatus != 'PENDING_DELETE'
          AND (
              id IN (:eventIds)
              OR providerEventId IN (:eventIds)
              OR googleEventId IN (:eventIds)
          )
    """)
    suspend fun getByAnySmartLinkIds(eventIds: List<String>): List<LocalEvent>

    @Query("""
        SELECT * FROM local_events
        WHERE syncStatus != 'PENDING_DELETE'
          AND (
              id IN (:eventIds)
              OR providerEventId IN (:eventIds)
              OR googleEventId IN (:eventIds)
          )
    """)
    fun observeByAnySmartLinkIds(eventIds: List<String>): Flow<List<LocalEvent>>

    // ========== Queries by date ==========

    /**
     * Get events for a specific date, excluding pending deletes.
     * Includes multi-day events that span across this date.
     * Ordered by all-day first, then by start time.
     */
    @Query("""
        SELECT * FROM local_events
        WHERE dayId <= :dayId AND endDayId >= :dayId AND syncStatus != 'PENDING_DELETE'
        ORDER BY isAllDay DESC, startTime ASC
    """)
    suspend fun getEventsForDay(dayId: String): List<LocalEvent>

    /**
     * Get ALL events for a specific date (including pending deletes).
     * Includes multi-day events spanning this date.
     * Used internally for sync operations.
     */
    @Query("SELECT * FROM local_events WHERE dayId <= :dayId AND endDayId >= :dayId ORDER BY isAllDay DESC, startTime ASC")
    suspend fun getAllEventsForDay(dayId: String): List<LocalEvent>

    /**
     * Get ALL events in the database (for backup export).
     */
    @Query("SELECT * FROM local_events ORDER BY dayId, startTime ASC")
    fun getAllEvents(): List<LocalEvent>

    /**
     * Get events that overlap with a date range (exclusive of pending deletes).
     * An event overlaps if it starts before the range ends AND ends after the range starts.
     */
    @Query("""
        SELECT * FROM local_events
        WHERE dayId <= :endDayId AND endDayId >= :startDayId AND syncStatus != 'PENDING_DELETE'
        ORDER BY dayId, isAllDay DESC, startTime ASC
    """)
    suspend fun getEventsForRange(startDayId: String, endDayId: String): List<LocalEvent>

    /**
     * Get distinct dates that have events
     */
    @Query("SELECT DISTINCT dayId FROM local_events WHERE dayId BETWEEN :startDayId AND :endDayId AND syncStatus != 'PENDING_DELETE'")
    suspend fun getDaysWithEvents(startDayId: String, endDayId: String): List<String>

    /**
     * Get distinct dates that have events for a specific month
     */
    @Query("SELECT DISTINCT dayId FROM local_events WHERE dayId LIKE :yearMonth || '%' AND syncStatus != 'PENDING_DELETE'")
    suspend fun getDaysWithEventsForMonth(yearMonth: String): List<String>

    // ========== Reactive queries (Flow) ==========

    @Query("""
        SELECT * FROM local_events
        WHERE dayId <= :dayId AND endDayId >= :dayId AND syncStatus != 'PENDING_DELETE'
        ORDER BY isAllDay DESC, startTime ASC
    """)
    fun observeEventsForDay(dayId: String): Flow<List<LocalEvent>>

    @Query("SELECT DISTINCT dayId FROM local_events WHERE syncStatus != 'PENDING_DELETE'")
    fun observeDaysWithEvents(): Flow<List<String>>

    @Query("SELECT DISTINCT dayId FROM local_events WHERE syncStatus != 'PENDING_DELETE'")
    suspend fun getDaysWithEvents(): List<String>

    // ========== Sync-related queries ==========

    /**
     * Get events by sync status
     */
    @Query("SELECT * FROM local_events WHERE syncStatus IN (:statuses)")
    suspend fun getEventsBySyncStatus(statuses: List<SyncStatus>): List<LocalEvent>

    /**
     * Get all events that need to be synced to Google
     */
    @Query("""
        SELECT * FROM local_events
        WHERE syncEnabled = 1 AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')
    """)
    suspend fun getPendingSyncEvents(): List<LocalEvent>

    /**
     * Get count of events pending sync
     */
    @Query("""
        SELECT COUNT(*) FROM local_events
        WHERE syncEnabled = 1 AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')
    """)
    suspend fun getPendingSyncCount(): Int

    /**
     * Observe count of pending sync events (for UI indicator)
     */
    @Query("""
        SELECT COUNT(*) FROM local_events
        WHERE syncEnabled = 1 AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')
    """)
    fun observePendingSyncCount(): Flow<Int>

    /**
     * Get all events with conflicts
     */
    @Query("SELECT * FROM local_events WHERE syncStatus = 'CONFLICT'")
    suspend fun getConflictEvents(): List<LocalEvent>

    /**
     * Update sync status for an event
     */
    @Query("UPDATE local_events SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus, updatedAt: Long = System.currentTimeMillis())

    /**
     * Mark event as synced with Google (after successful push)
     */
    @Query("""
        UPDATE local_events
        SET syncStatus = 'SYNCED',
            googleEventId = :googleEventId,
            serverEtag = :etag,
            lastSyncedAt = :syncedAt,
            updatedAt = :syncedAt
        WHERE id = :id
    """)
    suspend fun markAsSynced(id: String, googleEventId: String, etag: String?, syncedAt: Long = System.currentTimeMillis())

    // ========== Notification Scheduling ==========

    /**
     * Get upcoming events within a time range for notification scheduling.
     * Used by EventNotificationScheduler and BootReceiver to reschedule event alarms.
     * Excludes events pending deletion.
     *
     * @param startTime Start of time range (epoch millis) - typically current time
     * @param endTime End of time range (epoch millis) - typically 7 days ahead
     * @return Events starting within the time range, ordered by start time
     */
    @Query("""
        SELECT * FROM local_events
        WHERE startTime > :startTime AND startTime < :endTime
        AND syncStatus != 'PENDING_DELETE'
        ORDER BY startTime ASC
    """)
    suspend fun getUpcomingEventsBetween(startTime: Long, endTime: Long): List<LocalEvent>

    // ========== Bulk operations ==========

    /**
     * Delete all events from a specific Google calendar
     */
    @Query("DELETE FROM local_events WHERE syncEnabled = 1 AND googleCalendarId = :calendarId")
    suspend fun deleteEventsByGoogleCalendar(calendarId: String)

    @Query("SELECT * FROM local_events WHERE syncEnabled = 1 AND googleCalendarId = :calendarId")
    suspend fun getEventsByGoogleCalendar(calendarId: String): List<LocalEvent>

    /**
     * Convert all synced events to local-only (used on sign-out when user chooses to keep data)
     */
    @Query("""
        UPDATE local_events
        SET syncEnabled = 0,
            syncStatus = 'LOCAL_ONLY',
            googleEventId = NULL,
            googleCalendarId = NULL,
            googleCalendarName = NULL,
            serverEtag = NULL,
            lastSyncedAt = NULL,
            updatedAt = :updatedAt
        WHERE syncEnabled = 1
    """)
    suspend fun convertAllToLocalOnly(updatedAt: Long = System.currentTimeMillis())

    /**
     * Delete all synced events (used on sign-out when user chooses to remove synced data)
     */
    @Query("DELETE FROM local_events WHERE syncEnabled = 1")
    suspend fun deleteAllSyncedEvents()

    @Query("SELECT * FROM local_events WHERE syncEnabled = 1")
    suspend fun getAllSyncedEvents(): List<LocalEvent>

    /**
     * Permanently delete all events marked as PENDING_DELETE
     * (called after successful sync to Google)
     */
    @Query("SELECT * FROM local_events WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteEvents(): List<LocalEvent>

    @Query("DELETE FROM local_events WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun purgePendingDeletes()

    // ========== Statistics ==========

    @Query("SELECT COUNT(*) FROM local_events WHERE dayId <= :dayId AND endDayId >= :dayId AND syncStatus != 'PENDING_DELETE'")
    suspend fun getEventCountForDay(dayId: String): Int

    /**
     * Observe event count for a specific date (reactive for badges).
     * Includes multi-day events spanning this date.
     * Returns Flow<Int> for lightweight updates without full list collection.
     * Excludes events pending deletion.
     */
    @Query("""
        SELECT COUNT(*) FROM local_events
        WHERE dayId <= :dayId AND endDayId >= :dayId
        AND syncStatus != 'PENDING_DELETE'
    """)
    fun observeEventCountForDate(dayId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM local_events WHERE syncEnabled = 1")
    suspend fun getSyncedEventCount(): Int

    @Query("SELECT COUNT(*) FROM local_events WHERE syncEnabled = 0")
    suspend fun getLocalOnlyEventCount(): Int

    @Query("""
        SELECT COUNT(*) FROM local_events
        WHERE syncEnabled = 0
        AND isAllDay = 0
        AND syncStatus != 'PENDING_DELETE'
    """)
    suspend fun getLocalOnlyTimedEventCount(): Int

    @Query("SELECT COUNT(*) FROM local_events")
    suspend fun getTotalEventCount(): Int

    /**
     * Check if there are any events in the database
     */
    @Query("SELECT EXISTS(SELECT 1 FROM local_events WHERE syncStatus != 'PENDING_DELETE' LIMIT 1)")
    suspend fun hasAnyEvents(): Boolean

    // ========== Local Calendar Operations ==========

    /**
     * Migrate events from one or more calendars to a target calendar.
     * Used when deleting calendars - moves events to Default.
     * @param sourceCalendarIds List of source calendar IDs to migrate from
     * @param targetCalendarId Target calendar ID to migrate to
     */
    @Query("UPDATE local_events SET localCalendarId = :targetCalendarId, updatedAt = :updatedAt WHERE localCalendarId IN (:sourceCalendarIds)")
    suspend fun migrateEventsToCalendar(
        sourceCalendarIds: List<String>,
        targetCalendarId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Get count of events in a specific local calendar.
     * Used for displaying event count in calendar management UI.
     */
    @Query("SELECT COUNT(*) FROM local_events WHERE localCalendarId = :calendarId AND syncStatus != 'PENDING_DELETE'")
    suspend fun getEventCountForCalendar(calendarId: String): Int

    /**
     * Get count of events with null localCalendarId (legacy or default).
     * Used during migration to Default calendar.
     */
    @Query("SELECT COUNT(*) FROM local_events WHERE localCalendarId IS NULL AND syncStatus != 'PENDING_DELETE'")
    suspend fun getEventCountWithNullCalendar(): Int

    /**
     * Assign all events with null localCalendarId to the Default calendar.
     * Used during migration or when switching modes.
     */
    @Query("UPDATE local_events SET localCalendarId = :defaultCalendarId, updatedAt = :updatedAt WHERE localCalendarId IS NULL")
    suspend fun assignNullEventsToCalendar(
        defaultCalendarId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Get events for a specific local calendar.
     */
    @Query("""
        SELECT * FROM local_events
        WHERE localCalendarId = :calendarId AND syncStatus != 'PENDING_DELETE'
        ORDER BY dayId DESC, isAllDay DESC, startTime ASC
    """)
    suspend fun getEventsByCalendar(calendarId: String): List<LocalEvent>

    /**
     * Observe events for a specific local calendar.
     */
    @Query("""
        SELECT * FROM local_events
        WHERE localCalendarId = :calendarId AND syncStatus != 'PENDING_DELETE'
        ORDER BY dayId DESC, isAllDay DESC, startTime ASC
    """)
    fun observeEventsByCalendar(calendarId: String): Flow<List<LocalEvent>>

    // ========== Semantic Indexing ==========

    /**
     * Find local events that have no corresponding unified_ocr_text entry.
     * Used for background indexing of seeded/imported events.
     */
    @Query("""
        SELECT le.* FROM local_events le
        LEFT JOIN unified_ocr_text uo
            ON uo.sourceType = 'LOCAL_EVENT' AND uo.sourceId = le.id
        WHERE uo.id IS NULL
        ORDER BY le.startTime DESC
        LIMIT :limit
    """)
    suspend fun getUnindexedEvents(limit: Int): List<LocalEvent>

    /** The columns the search dialog matches in memory (search/SearchCandidates). */
    @Query("SELECT id, title, description, dayId FROM local_events")
    suspend fun getSearchRows(): List<com.boox.day.search.EventSearchRow>
}
