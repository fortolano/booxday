package com.boox.day.data.db

import android.content.Context
import android.util.Log
import com.boox.day.booxnotes.SmartLinkRefreshCoordinator
import java.util.Collections
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * CalendarEvent entity - Cached calendar events from Google and Outlook.
 * This is a local cache of events for offline viewing.
 * All CRUD operations go through provider APIs first, then update this cache.
 *
 * Multi-provider support added in v18:
 * - providerId: Identifies the calendar provider (GOOGLE, OUTLOOK)
 * - accountId: Links to ConnectedAccountEntity for multi-account support
 * - accountEmail: Denormalized for display (avoids joins)
 */
@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["dayId"]),
        Index(value = ["endDayId"]),
        Index(value = ["accountId"]),
        Index(value = ["providerId", "accountId", "calendarId"])
    ]
)
data class CalendarEvent(
    @PrimaryKey
    val id: String,                          // Provider event ID (NOT local UUID)

    // Provider/Account identification (added in v18)
    val providerId: String = "GOOGLE",       // "GOOGLE" or "OUTLOOK"
    val accountId: String = "",              // FK to connected_accounts (empty = legacy)
    val accountEmail: String = "",           // For display (denormalized)

    val calendarId: String,                  // Provider calendar ID
    val calendarName: String,                // Display name of the calendar
    val title: String,
    val dayId: String,                       // "yyyy-MM-dd" start date for multi-day range queries
    val endDayId: String = "",               // "yyyy-MM-dd" end date (inclusive). Same as dayId for single-day events.
    val startTime: Long,                     // Epoch milliseconds
    val endTime: Long? = null,               // Epoch milliseconds (null for all-day events)
    val isAllDay: Boolean = false,           // All-day event flag
    val location: String? = null,
    val description: String? = null,
    val attendees: String? = null,           // JSON array of attendee emails
    val isReadOnly: Boolean = false,         // Based on calendar accessRole
    val colorHex: String? = null,            // Event color from provider
    val syncedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this event is from Google Calendar.
     */
    val isGoogleEvent: Boolean
        get() = providerId == "GOOGLE"

    /**
     * Check if this event is from Outlook Calendar.
     */
    val isOutlookEvent: Boolean
        get() = providerId == "OUTLOOK"

    /**
     * Check if this event has been tagged with account info (post-migration).
     */
    val hasAccountInfo: Boolean
        get() = accountId.isNotEmpty()

    /**
     * Convert to LocalEvent for use in contexts that expect the local event type.
     */
    fun toLocalEvent(): LocalEvent = LocalEvent(
        id = id,
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
        providerId = providerId,
        providerEventId = id,
        providerCalendarId = calendarId,
        providerCalendarName = calendarName,
        accountId = accountId,
        accountEmail = accountEmail
    )
}

/**
 * Simplified calendar info for display purposes
 */
data class CachedCalendarInfo(
    val calendarId: String,
    val calendarName: String,
    val isReadOnly: Boolean
)

@Dao
interface CalendarEventDao {
    /**
     * Get events for a specific date (as Flow for reactive updates).
     * Includes multi-day events that span across this date.
     */
    @Query("SELECT * FROM calendar_events WHERE dayId <= :dayId AND endDayId >= :dayId ORDER BY isAllDay DESC, startTime ASC")
    fun getByDayId(dayId: String): Flow<List<CalendarEvent>>

    /**
     * Get events for a specific date (sync version for non-reactive access).
     * Includes multi-day events that span across this date.
     */
    @Query("SELECT * FROM calendar_events WHERE dayId <= :dayId AND endDayId >= :dayId ORDER BY isAllDay DESC, startTime ASC")
    suspend fun getByDayIdSync(dayId: String): List<CalendarEvent>

    /**
     * Get a single event by its Google event ID
     */
    @Query("SELECT * FROM calendar_events WHERE id = :eventId")
    suspend fun getById(eventId: String): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE id IN (:eventIds)")
    suspend fun getByIds(eventIds: List<String>): List<CalendarEvent>

    /**
     * Get all events that overlap with a date range.
     * An event overlaps if it starts before the range ends AND ends after the range starts.
     */
    @Query("SELECT * FROM calendar_events WHERE dayId <= :endDate AND endDayId >= :startDate ORDER BY dayId, startTime")
    suspend fun getByDateRange(startDate: String, endDate: String): List<CalendarEvent>

    /**
     * Insert or update events (upsert).
     *
     * @Upsert updates an existing row in place. Never use REPLACE here: it deletes the old row, and
     * boox_note_links.linkedEventId (ON DELETE SET NULL) then loses every link to the event, so each
     * sync emptied all Boox Notes links and links set by hand never came back (Oct 2026).
     */
    @Upsert
    suspend fun upsertAll(events: List<CalendarEvent>)

    /**
     * Insert or update a single event (updates in place; see [upsertAll])
     */
    @Upsert
    suspend fun upsert(event: CalendarEvent)

    /**
     * Delete a single event by its Google event ID
     */
    @Query("DELETE FROM calendar_events WHERE id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM calendar_events WHERE id IN (:eventIds)")
    suspend fun deleteByIds(eventIds: List<String>)

    /**
     * Delete all events from a specific calendar
     */
    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendarId(calendarId: String)

    @Query("SELECT id FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun getIdsByCalendarId(calendarId: String): List<String>

    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun getByCalendarId(calendarId: String): List<CalendarEvent>

    /**
     * Delete all cached events (for sign-out or full refresh)
     */
    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()

    @Query("SELECT id FROM calendar_events")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAll(): List<CalendarEvent>

    /**
     * Get all distinct dates that have events (for badge indicators)
     */
    @Query("SELECT DISTINCT dayId FROM calendar_events")
    suspend fun getDatesWithEvents(): List<String>

    /**
     * Get all distinct dates with events for a specific month
     */
    @Query("SELECT DISTINCT dayId FROM calendar_events WHERE dayId LIKE :yearMonth || '%'")
    suspend fun getDatesWithEventsForMonth(yearMonth: String): List<String>

    /**
     * Get all distinct dates with events, filtered to enabled calendars only.
     * Uses same INNER JOIN as getByDayIdFiltered for badge/grid consistency.
     */
    @Query("""
        SELECT DISTINCT ce.dayId FROM calendar_events ce
        INNER JOIN calendar_selections cs
            ON ce.accountId = cs.accountId AND ce.calendarId = cs.calendarId
        WHERE cs.isFetchEnabled = 1
    """)
    suspend fun getDatesWithEventsFiltered(): List<String>

    /**
     * First and last day of every event overlapping [firstDay]..[lastDay], filtered to enabled
     * calendars. Same join and day condition as getByDayIdFiltered, for badge/grid consistency
     * (EventBadgeDays.kt expands them into days).
     */
    @Query("""
        SELECT DISTINCT ce.dayId, ce.endDayId FROM calendar_events ce
        INNER JOIN calendar_selections cs
            ON ce.accountId = cs.accountId AND ce.calendarId = cs.calendarId
        WHERE ce.dayId <= :lastDay AND ce.endDayId >= :firstDay AND cs.isFetchEnabled = 1
    """)
    suspend fun getDaySpansFiltered(firstDay: String, lastDay: String): List<EventDaySpan>

    /**
     * Delete cloud events with empty accountId (legacy sync artifacts).
     * These events are invisible to filtered grid queries and waste space.
     */
    @Query("DELETE FROM calendar_events WHERE accountId = ''")
    suspend fun deleteLegacyEvents(): Int

    /**
     * Get distinct calendars from cached events
     */
    @Query("SELECT DISTINCT calendarId, calendarName, isReadOnly FROM calendar_events")
    suspend fun getDistinctCalendars(): List<CachedCalendarInfo>

    /**
     * Count events for a specific date (includes multi-day spanning events)
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE dayId <= :dayId AND endDayId >= :dayId")
    suspend fun countEventsForDate(dayId: String): Int

    /**
     * Observe event count for a specific date (reactive for badges).
     * Includes multi-day events that span across this date.
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE dayId <= :dayId AND endDayId >= :dayId")
    fun observeEventCountForDate(dayId: String): Flow<Int>

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Selection Filtered Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get events for a date, filtered to only enabled calendars.
     * Includes multi-day events spanning this date.
     * INNER JOIN ensures events from deselected calendars are excluded.
     * Room auto-invalidates this Flow when either table changes.
     */
    @Query("""
        SELECT ce.* FROM calendar_events ce
        INNER JOIN calendar_selections cs
            ON ce.accountId = cs.accountId AND ce.calendarId = cs.calendarId
        WHERE ce.dayId <= :dayId AND ce.endDayId >= :dayId AND cs.isFetchEnabled = 1
        ORDER BY ce.isAllDay DESC, ce.startTime ASC
    """)
    fun getByDayIdFiltered(dayId: String): Flow<List<CalendarEvent>>

    /**
     * Sync version of filtered query (non-reactive, for cache warming).
     * Includes multi-day events spanning this date.
     */
    @Query("""
        SELECT ce.* FROM calendar_events ce
        INNER JOIN calendar_selections cs
            ON ce.accountId = cs.accountId AND ce.calendarId = cs.calendarId
        WHERE ce.dayId <= :dayId AND ce.endDayId >= :dayId AND cs.isFetchEnabled = 1
        ORDER BY ce.isAllDay DESC, ce.startTime ASC
    """)
    suspend fun getByDayIdFilteredSync(dayId: String): List<CalendarEvent>

    /**
     * Filtered event count for calendar badges.
     * Includes multi-day events spanning this date.
     * Only counts events from enabled calendars.
     */
    @Query("""
        SELECT COUNT(*) FROM calendar_events ce
        INNER JOIN calendar_selections cs
            ON ce.accountId = cs.accountId AND ce.calendarId = cs.calendarId
        WHERE ce.dayId <= :dayId AND ce.endDayId >= :dayId AND cs.isFetchEnabled = 1
    """)
    fun observeEventCountForDateFiltered(dayId: String): Flow<Int>

    /**
     * Delete events for a specific calendar within an account.
     * Used when user deselects a calendar to immediately remove its events.
     */
    @Query("DELETE FROM calendar_events WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun deleteByAccountAndCalendarId(accountId: String, calendarId: String)

    @Query("SELECT id FROM calendar_events WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun getIdsByAccountAndCalendarId(accountId: String, calendarId: String): List<String>

    /**
     * Check if there are any cached events
     */
    @Query("SELECT EXISTS(SELECT 1 FROM calendar_events LIMIT 1)")
    suspend fun hasAnyEvents(): Boolean

    /**
     * Get total count of cached events (for change detection)
     */
    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun getCount(): Int

    /**
     * Get cached event count for a specific provider.
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE providerId = :providerId")
    suspend fun getCountByProvider(providerId: String): Int

    /**
     * Get cached timed-event count for a specific provider.
     * Smart Link ignores all-day events for matching.
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE providerId = :providerId AND isAllDay = 0")
    suspend fun getTimedCountByProvider(providerId: String): Int

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Provider Support (v18+)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update provider info for events with empty accountId.
     * Used by post-migration helper to tag existing events with account info.
     * Idempotent: Only updates events where accountId is empty.
     */
    @Query("UPDATE calendar_events SET providerId = :providerId, accountId = :accountId, accountEmail = :accountEmail WHERE accountId = ''")
    suspend fun updateProviderInfo(providerId: String, accountId: String, accountEmail: String)

    /**
     * Get events for a specific account.
     */
    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId ORDER BY dayId, startTime")
    suspend fun getByAccountId(accountId: String): List<CalendarEvent>

    @Query("SELECT id FROM calendar_events WHERE accountId = :accountId")
    suspend fun getIdsByAccountId(accountId: String): List<String>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun getByAccountAndCalendarId(accountId: String, calendarId: String): List<CalendarEvent>

    /**
     * Delete all events for a specific account.
     * Used when removing an account.
     */
    @Query("DELETE FROM calendar_events WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    /**
     * Get events for a specific account and date.
     */
    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND dayId = :dayId ORDER BY isAllDay DESC, startTime ASC")
    suspend fun getByAccountAndDate(accountId: String, dayId: String): List<CalendarEvent>

    /**
     * Count events without account info (for migration progress tracking).
     */
    @Query("SELECT COUNT(*) FROM calendar_events WHERE accountId = ''")
    suspend fun countUnmigratedEvents(): Int

    /**
     * Get upcoming events within a time range.
     * Used for scheduling event reminder notifications.
     */
    @Query("""
        SELECT * FROM calendar_events
        WHERE startTime > :startTime AND startTime < :endTime
        ORDER BY startTime ASC
    """)
    suspend fun getUpcomingEventsBetween(startTime: Long, endTime: Long): List<CalendarEvent>

    // ═══════════════════════════════════════════════════════════════════════════
    // Semantic Indexing (Jan 2026)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find calendar events that have no corresponding unified_ocr_text entry.
     * Used for background indexing of events that bypassed normal sync indexing.
     */
    @Query("""
        SELECT ce.* FROM calendar_events ce
        LEFT JOIN unified_ocr_text uo
            ON uo.sourceType = 'CALENDAR_EVENT' AND uo.sourceId = ce.id
        WHERE uo.id IS NULL
        ORDER BY ce.startTime DESC
        LIMIT :limit
    """)
    suspend fun getUnindexedEvents(limit: Int): List<CalendarEvent>

    /** The columns the search dialog matches in memory (search/SearchCandidates). */
    @Query("SELECT id, title, description, dayId FROM calendar_events")
    suspend fun getSearchRows(): List<com.boox.day.search.EventSearchRow>
}

/**
 * Repository for CalendarEvent - provides clean API for data access.
 * Note: This is a cache repository. Actual CRUD goes through CalendarSyncManager.
 */
class CalendarEventRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(context).calendarEventDao()
    private val booxNoteRepository by lazy { BooxNoteRepository(appContext) }
    private val smartLinkRefreshCoordinator by lazy { SmartLinkRefreshCoordinator(appContext, eventRepository = this) }

    companion object {
        private const val TAG = "CalendarEventRepo"
    }

    // PERFORMANCE: LRU cache for events by date (saves DB queries on day switching)
    // Thread-safe: Wrapped with synchronizedMap for concurrent access from main/background threads
    private val eventsCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<CalendarEvent>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CalendarEvent>>): Boolean {
                return size > 60  // Cache ~2 months of events
            }
        }
    )

    /**
     * Invalidate events cache for a specific date or all dates.
     * Call this when events are synced/created/edited/deleted.
     */
    fun invalidateEventsCache(dayId: String? = null) {
        if (dayId != null) {
            eventsCache.remove(dayId)
        } else {
            eventsCache.clear()
        }
    }

    private fun invalidateEventsCacheForDays(dayIds: Set<String>) {
        if (dayIds.isEmpty()) {
            invalidateEventsCache()
            return
        }
        dayIds.forEach(::invalidateEventsCache)
    }

    private suspend fun refreshSmartLinksForEvents(events: Collection<CalendarEvent>, reason: String) {
        val affectedDates = collectCoveredDayIds(events)
        if (affectedDates.isEmpty()) return
        smartLinkRefreshCoordinator.refreshDates(affectedDates, reason)
    }

    /**
     * Pre-warm the events cache for all dates with events in a month.
     * Called when month changes to speed up EventsGrid loading.
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     * @return Set of dates with events (for calendar badges)
     */
    suspend fun preWarmCacheForMonth(yearMonth: String): Set<String> {
        val start = System.currentTimeMillis()
        val datesWithEvents = getDatesWithEventsForMonth(yearMonth)
        var cachedCount = 0
        datesWithEvents.forEach { date ->
            if (!eventsCache.containsKey(date)) {
                val events = dao.getByDayIdFilteredSync(date)
                eventsCache[date] = events
                cachedCount++
            }
        }
        Log.i(TAG, "PERF: preWarmCacheForMonth $yearMonth - cached $cachedCount dates (${System.currentTimeMillis() - start}ms)")
        return datesWithEvents.toSet()
    }

    /**
     * Get events for a specific date as a reactive Flow
     */
    fun getEventsForDate(dayId: String): Flow<List<CalendarEvent>> = dao.getByDayIdFiltered(dayId)

    /**
     * Get events for a specific date (non-reactive, cached)
     */
    suspend fun getEventsForDateSync(dayId: String): List<CalendarEvent> {
        // PERFORMANCE: Check cache first
        eventsCache[dayId]?.let { cached ->
            Log.i(TAG, "PERF: getEventsForDateSync CACHE HIT for $dayId (0ms)")
            return cached
        }

        val start = System.currentTimeMillis()
        val events = dao.getByDayIdFilteredSync(dayId)
        eventsCache[dayId] = events
        Log.i(TAG, "PERF: getEventsForDateSync CACHE MISS for $dayId (${System.currentTimeMillis() - start}ms)")
        return events
    }

    /**
     * Get a single event by ID
     */
    suspend fun getEventById(eventId: String): CalendarEvent? = dao.getById(eventId)

    /**
     * Get events within a date range
     */
    suspend fun getEventsInRange(startDate: String, endDate: String): List<CalendarEvent> =
        dao.getByDateRange(startDate, endDate)

    /**
     * Cache multiple events (from sync)
     */
    suspend fun cacheEvents(events: List<CalendarEvent>) {
        dao.upsertAll(events)
        // Invalidate entire cache since sync can affect many dates
        invalidateEventsCache()
    }

    /**
     * Cache a single event
     */
    suspend fun cacheEvent(event: CalendarEvent) {
        dao.upsert(event)
        // Invalidate the specific date
        invalidateEventsCache(event.dayId)
    }

    /**
     * Remove a single event from cache
     */
    suspend fun removeEvent(eventId: String) {
        removeEvents(listOf(eventId), "cloud event removed")
    }

    /**
     * Delete a single event by ID (alias for cache removal).
     */
    suspend fun deleteById(eventId: String) {
        removeEvent(eventId)
    }

    suspend fun removeEvents(eventIds: Collection<String>, reason: String) {
        val ids = eventIds
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (ids.isEmpty()) return

        val deletedEvents = dao.getByIds(ids)
        booxNoteRepository.handleDeletedEvents(ids)
        dao.deleteByIds(ids)

        if (deletedEvents.isNotEmpty()) {
            invalidateEventsCacheForDays(collectCoveredDayIds(deletedEvents))
            refreshSmartLinksForEvents(deletedEvents, reason)
        } else {
            invalidateEventsCache()
        }
    }

    /**
     * Delete events for a specific calendar within an account.
     * Used when user deselects a calendar to immediately remove its cached events.
     */
    suspend fun deleteEventsForCalendar(accountId: String, calendarId: String) {
        val deletedEvents = dao.getByAccountAndCalendarId(accountId, calendarId)
        booxNoteRepository.handleDeletedEvents(deletedEvents.map { it.id })
        dao.deleteByAccountAndCalendarId(accountId, calendarId)
        invalidateEventsCacheForDays(collectCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "cloud calendar deselected")
    }

    /**
     * Remove all events from a specific calendar
     */
    suspend fun removeCalendarEvents(calendarId: String) {
        val deletedEvents = dao.getByCalendarId(calendarId)
        booxNoteRepository.handleDeletedEvents(deletedEvents.map { it.id })
        dao.deleteByCalendarId(calendarId)
        invalidateEventsCacheForDays(collectCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "cloud calendar removed")
    }

    suspend fun removeEventsForAccount(accountId: String) {
        val deletedEvents = dao.getByAccountId(accountId)
        booxNoteRepository.handleDeletedEvents(deletedEvents.map { it.id })
        dao.deleteByAccountId(accountId)
        invalidateEventsCacheForDays(collectCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "cloud account removed")
    }

    /**
     * Clear entire cache (for sign-out)
     */
    suspend fun clearCache() {
        val deletedEvents = dao.getAll()
        booxNoteRepository.handleDeletedEvents(deletedEvents.map { it.id })
        dao.deleteAll()
        invalidateEventsCacheForDays(collectCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "cloud cache cleared")
    }

    /**
     * Get all dates that have events (for calendar badges).
     * Uses filtered query (INNER JOIN calendar_selections) so badges
     * only appear for dates whose events are also visible in the grid.
     */
    suspend fun getDatesWithEvents(): Set<String> = dao.getDatesWithEventsFiltered().toSet()

    /**
     * Days of a month with events (for calendar badges): every day an event covers, not only its first.
     * Uses filtered query (INNER JOIN calendar_selections) for badge/grid consistency.
     */
    suspend fun getDatesWithEventsForMonth(yearMonth: String): Set<String> {
        val (firstDay, lastDay) = monthDayRange(yearMonth)
        return daysCoveredInMonth(dao.getDaySpansFiltered(firstDay, lastDay), yearMonth)
    }

    /**
     * Get distinct calendars from cache
     */
    suspend fun getDistinctCalendars(): List<CachedCalendarInfo> = dao.getDistinctCalendars()

    /**
     * Check if date has any events
     */
    suspend fun dateHasEvents(dayId: String): Boolean = dao.countEventsForDate(dayId) > 0

    /**
     * Observe event count for a specific date (reactive for badges)
     * Returns Flow<Int> for lightweight updates without full list collection
     */
    fun observeEventCountForDate(dayId: String): Flow<Int> = dao.observeEventCountForDateFiltered(dayId)

    /**
     * Check if there are any cached events
     */
    suspend fun hasCachedEvents(): Boolean = dao.hasAnyEvents()

    /**
     * Get all cached cloud event IDs.
     * Used by Smart Link to compute unique event availability across cloud + local sources.
     */
    suspend fun getAllEventIds(): Set<String> = dao.getAllIds().toSet()

    /**
     * Get total count of cached events (for change detection)
     */
    suspend fun getEventCount(): Long = dao.getCount().toLong()

    /**
     * Get cached event count for a specific provider.
     */
    suspend fun getEventCountByProvider(providerId: String): Long = dao.getCountByProvider(providerId).toLong()

    /**
     * Get cached timed-event count for a specific provider.
     * Smart Link uses this for honest preflight gating.
     */
    suspend fun getTimedEventCountByProvider(providerId: String): Long =
        dao.getTimedCountByProvider(providerId).toLong()
}
