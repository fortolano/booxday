package com.boox.day.data.db

import android.content.Context
import android.util.Log
import com.boox.day.booxnotes.SmartLinkRefreshCoordinator
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.notifications.EventNotificationScheduler
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Collections

/**
 * Repository for LocalEvent - provides clean API for data access with LRU caching.
 *
 * This is the PRIMARY event repository for Boox Day, serving as the source of truth
 * for all calendar events (both local-only and synced with Google).
 *
 * Key differences from CalendarEventRepository:
 * - LocalEventRepository is the SOURCE OF TRUTH
 * - CalendarEventRepository was just a CACHE of Google data
 * - This repository handles sync status transitions
 */
class LocalEventRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).localEventDao()
    private val calendarEventDao = AppDatabase.getDatabase(context).calendarEventDao()
    private val appContext = context.applicationContext

    // Lazy-initialized semantic service for event indexing (Phase 2b)
    private val semanticGeniusService by lazy { SemanticGeniusService(appContext) }
    private val quotaManager by lazy { OcrQuotaManager(appContext) }
    private val booxNoteRepository by lazy { BooxNoteRepository(appContext) }
    private val smartLinkRefreshCoordinator by lazy { SmartLinkRefreshCoordinator(appContext) }

    // Lazy-initialized notification scheduler for event reminders
    private val eventNotificationScheduler by lazy { EventNotificationScheduler(appContext) }

    companion object {
        private const val TAG = "LocalEventRepo"
    }

    // PERFORMANCE: LRU cache for events by date (saves DB queries on day switching)
    // Same pattern as CalendarEventRepository and TaskRepository
    // Thread-safe: Wrapped with synchronizedMap for concurrent access from main/background threads
    private val eventsCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<LocalEvent>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LocalEvent>>): Boolean {
                return size > 60  // Cache ~2 months of events
            }
        }
    )

    // ========== Cache Management ==========

    /**
     * Invalidate events cache for a specific date or all dates.
     * Call this when events are created/edited/deleted.
     */
    fun invalidateCache(dayId: String? = null) {
        if (dayId != null) {
            eventsCache.remove(dayId)
            Log.d(TAG, "CACHE: Invalidated cache for $dayId")
        } else {
            eventsCache.clear()
            Log.d(TAG, "CACHE: Cleared entire cache")
        }
    }

    private fun invalidateCacheForDays(dayIds: Set<String>) {
        if (dayIds.isEmpty()) {
            invalidateCache()
            return
        }
        dayIds.forEach(::invalidateCache)
    }

    private suspend fun rebindSmartLinkIdsForEventTransition(
        existingEvent: LocalEvent?,
        updatedEvent: LocalEvent
    ) {
        if (existingEvent == null) return

        val newPrimaryEventId = updatedEvent.toCalendarEvent().id.trim()
        if (newPrimaryEventId.isEmpty()) return

        val replacements = existingEvent.linkedEventIdsForSmartLinks()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != newPrimaryEventId }
            .associateWith { newPrimaryEventId }

        if (replacements.isNotEmpty()) {
            booxNoteRepository.rebindEventIds(replacements)
        }
    }

    private suspend fun refreshSmartLinksForEvents(events: Collection<LocalEvent>, reason: String) {
        val affectedDates = collectLocalCoveredDayIds(events)
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
        val datesWithEvents = dao.getDaysWithEventsForMonth(yearMonth)
        var cachedCount = 0
        datesWithEvents.forEach { date ->
            if (!eventsCache.containsKey(date)) {
                val events = dao.getEventsForDay(date)
                eventsCache[date] = events
                cachedCount++
            }
        }
        Log.i(TAG, "PERF: preWarmCacheForMonth $yearMonth - cached $cachedCount dates (${System.currentTimeMillis() - start}ms)")
        return datesWithEvents.toSet()
    }

    /**
     * Pre-warm cache for a YearMonth (convenience overload)
     */
    suspend fun preWarmCacheForMonth(yearMonth: YearMonth): Set<String> {
        val yearMonthStr = String.format("%04d-%02d", yearMonth.year, yearMonth.monthValue)
        return preWarmCacheForMonth(yearMonthStr)
    }

    // ========== CREATE ==========

    /**
     * Create a new local event.
     * Sets syncStatus based on syncEnabled flag.
     * Also indexes the event for semantic search (Phase 2b).
     */
    suspend fun createEvent(event: LocalEvent): LocalEvent {
        // Ensure proper sync status for new events
        val eventToInsert = if (event.syncEnabled && event.syncStatus == SyncStatus.LOCAL_ONLY) {
            event.copy(syncStatus = SyncStatus.PENDING_CREATE)
        } else {
            event
        }

        dao.insert(eventToInsert)
        invalidateCache(eventToInsert.dayId)

        // Index for semantic search (Phase 2b - SemanticGenius)
        if (quotaManager.isSemanticExtractionEnabled) {
            try {
                semanticGeniusService.indexEvent(eventToInsert)
                Log.d(TAG, "SEMANTIC: Indexed event ${eventToInsert.id}")
            } catch (e: Exception) {
                // Non-fatal: semantic indexing failure shouldn't block event creation
                Log.w(TAG, "SEMANTIC: Failed to index event ${eventToInsert.id}: ${e.message}")
            }
        } else {
            Log.d(TAG, "SEMANTIC: Skipping event indexing for ${eventToInsert.id} because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")
        }

        // Schedule notification if enabled
        scheduleEventNotificationIfEnabled(eventToInsert)
        refreshSmartLinksForEvents(listOf(eventToInsert), "local event created")

        Log.d(TAG, "CREATE: Event ${eventToInsert.id} created for ${eventToInsert.dayId}, sync=${eventToInsert.syncEnabled}")
        return eventToInsert
    }

    // ========== READ ==========

    /**
     * Get events for a specific date (non-reactive, cached).
     * Excludes events pending deletion.
     */
    suspend fun getEventsForDay(dayId: String): List<LocalEvent> {
        // PERFORMANCE: Check cache first
        eventsCache[dayId]?.let { cached ->
            Log.d(TAG, "PERF: getEventsForDay CACHE HIT for $dayId (0ms)")
            return cached
        }

        val start = System.currentTimeMillis()
        val events = dao.getEventsForDay(dayId)
        eventsCache[dayId] = events
        Log.d(TAG, "PERF: getEventsForDay CACHE MISS for $dayId (${System.currentTimeMillis() - start}ms)")
        return events
    }

    /**
     * Get events for a date range.
     */
    suspend fun getEventsForRange(startDate: LocalDate, endDate: LocalDate): List<LocalEvent> {
        val startDayId = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endDayId = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getEventsForRange(startDayId, endDayId)
    }

    /**
     * Get a single event by ID.
     */
    suspend fun getEventById(id: String): LocalEvent? = dao.getById(id)

    /**
     * Get event by Google event ID (for sync matching).
     */
    suspend fun getByGoogleEventId(googleEventId: String): LocalEvent? =
        dao.getByGoogleEventId(googleEventId)

    /**
     * Get event by provider event ID (for Outlook/other provider matching).
     */
    suspend fun getByProviderEventId(providerEventId: String): LocalEvent? =
        dao.getByProviderEventId(providerEventId)

    /**
     * Resolve an event using any ID Smart Link may have persisted.
     * Order matters: local UUID first, then provider IDs.
     */
    suspend fun getEventBySmartLinkId(eventId: String): CalendarEvent? {
        return dao.getById(eventId)?.toCalendarEvent()
            ?: dao.getByProviderEventId(eventId)?.toCalendarEvent()
            ?: dao.getByGoogleEventId(eventId)?.toCalendarEvent()
    }

    /**
     * Get all active local events as the Smart Link/UI-compatible CalendarEvent shape.
     */
    suspend fun getAllActiveEventsForSmartLink(): List<CalendarEvent> =
        dao.getAllActiveEvents().map { it.toCalendarEvent() }

    /**
     * Get events for a specific date as a reactive Flow.
     * Automatically excludes pending deletes.
     */
    fun observeEventsForDay(dayId: String): Flow<List<LocalEvent>> = dao.observeEventsForDay(dayId)

    /**
     * Get dates with events for a month (for calendar badges).
     */
    suspend fun getDatesWithEventsForMonth(yearMonth: String): Set<String> =
        dao.getDaysWithEventsForMonth(yearMonth).toSet()

    /**
     * Get all dates with events (for calendar badges).
     */
    suspend fun getDatesWithEvents(): Set<String> =
        dao.getDaysWithEvents().toSet()

    /**
     * Check if date has any events.
     */
    suspend fun dateHasEvents(dayId: String): Boolean = dao.getEventCountForDay(dayId) > 0

    /**
     * Observe event count for a specific date (reactive for badges)
     * Returns Flow<Int> for lightweight updates without full list collection
     */
    fun observeEventCountForDate(dayId: String): Flow<Int> = dao.observeEventCountForDate(dayId)

    /**
     * Check if there are any events in the database.
     */
    suspend fun hasAnyEvents(): Boolean = dao.hasAnyEvents()

    // ========== UPDATE ==========

    /**
     * Update an event.
     * Automatically sets syncStatus to PENDING_UPDATE if event is synced.
     * Also re-indexes the event for semantic search (Phase 2b).
     */
    suspend fun updateEvent(event: LocalEvent): LocalEvent {
        val now = System.currentTimeMillis()
        val existingEvent = dao.getById(event.id)

        // A write that records a provider confirmation carries SYNCED with a newer lastSyncedAt
        // (every confirm path sets lastSyncedAt = now); a user edit of a synced event copies the
        // stored lastSyncedAt. Plan Phase 2 (Oct 2026): confirmations used to be re-flagged
        // PENDING_UPDATE and would have been re-uploaded over newer remote edits.
        val recordsProviderConfirmation = event.syncStatus == SyncStatus.SYNCED &&
            event.lastSyncedAt != null &&
            event.lastSyncedAt != existingEvent?.lastSyncedAt

        // Determine new sync status
        val newSyncStatus = when {
            // If sync disabled, keep as local only
            !event.syncEnabled -> SyncStatus.LOCAL_ONLY
            // Just confirmed by the provider: stays synced
            recordsProviderConfirmation -> SyncStatus.SYNCED
            // If already synced, mark as pending update
            event.syncStatus == SyncStatus.SYNCED -> SyncStatus.PENDING_UPDATE
            // Otherwise keep existing status
            else -> event.syncStatus
        }

        val updatedEvent = event.copy(
            updatedAt = now,
            syncStatus = newSyncStatus
        )

        dao.update(updatedEvent)
        rebindSmartLinkIdsForEventTransition(existingEvent, updatedEvent)
        val affectedDates = buildSet {
            existingEvent?.let { addAll(it.coveredDayIds()) }
            addAll(updatedEvent.coveredDayIds())
        }
        invalidateCacheForDays(affectedDates)

        // Re-index for semantic search (Phase 2b - SemanticGenius)
        try {
            semanticGeniusService.invalidateEventProfile(updatedEvent.id)
            if (quotaManager.isSemanticExtractionEnabled) {
                semanticGeniusService.indexEvent(updatedEvent)
                Log.d(TAG, "SEMANTIC: Re-indexed event ${updatedEvent.id}")
            } else {
                semanticGeniusService.deleteEventProfile(OcrSource.LOCAL_EVENT, updatedEvent.id)
                Log.d(TAG, "SEMANTIC: Cleared profile for ${updatedEvent.id} because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")
            }
        } catch (e: Exception) {
            // Non-fatal: semantic indexing failure shouldn't block event update
            Log.w(TAG, "SEMANTIC: Failed to refresh event semantics for ${updatedEvent.id}: ${e.message}")
        }

        // Reschedule notification (cancel old, schedule new if still valid)
        cancelEventNotification(event.id)
        scheduleEventNotificationIfEnabled(updatedEvent)
        refreshSmartLinksForEvents(
            events = listOfNotNull(existingEvent, updatedEvent),
            reason = "local event updated"
        )

        Log.d(TAG, "UPDATE: Event ${event.id} updated, syncStatus=${newSyncStatus}")
        return updatedEvent
    }

    /**
     * Update sync status for an event.
     */
    suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        val event = dao.getById(id)
        dao.updateSyncStatus(id, status)
        event?.let { invalidateCache(it.dayId) }
        Log.d(TAG, "UPDATE_STATUS: Event $id status changed to $status")
    }

    // ========== DELETE ==========

    /**
     * Delete an event.
     * If synced with a cloud provider, marks as PENDING_DELETE (actual deletion after sync).
     * If local-only, deletes immediately and removes semantic profile.
     */
    suspend fun deleteEvent(event: LocalEvent) {
        val hasCloudEventId = !event.providerEventId.isNullOrBlank() || !event.googleEventId.isNullOrBlank()
        if (event.syncEnabled && hasCloudEventId) {
            // Mark for deletion, will be removed after sync confirms
            val markedEvent = event.copy(
                syncStatus = SyncStatus.PENDING_DELETE,
                updatedAt = System.currentTimeMillis()
            )
            dao.update(markedEvent)
            Log.d(TAG, "DELETE: Event ${event.id} marked as PENDING_DELETE")
            // Note: Semantic profile kept until permanentlyDelete() is called after sync
        } else {
            // Local-only, delete immediately
            dao.delete(event)
            booxNoteRepository.handleDeletedEvents(event.linkedEventIdsForSmartLinks())

            // Delete semantic profile (Phase 2b - SemanticGenius)
            try {
                semanticGeniusService.deleteEventProfile(OcrSource.LOCAL_EVENT, event.id)
                Log.d(TAG, "SEMANTIC: Deleted profile for event ${event.id}")
            } catch (e: Exception) {
                Log.w(TAG, "SEMANTIC: Failed to delete profile for event ${event.id}: ${e.message}")
            }

            Log.d(TAG, "DELETE: Event ${event.id} deleted immediately (local-only)")
        }

        // Cancel notification
        cancelEventNotification(event.id)

        invalidateCacheForDays(event.coveredDayIds())
        refreshSmartLinksForEvents(listOf(event), "local event deleted")
    }

    /**
     * Permanently delete an event by ID (use after sync confirms deletion).
     * Also removes the semantic profile.
     */
    suspend fun permanentlyDelete(id: String) {
        val event = dao.getById(id)
        dao.deleteById(id)
        event?.let { invalidateCacheForDays(it.coveredDayIds()) }
        event?.let { booxNoteRepository.handleDeletedEvents(it.linkedEventIdsForSmartLinks()) }

        // Cancel notification
        cancelEventNotification(id)

        // Delete semantic profile (Phase 2b - SemanticGenius)
        try {
            semanticGeniusService.deleteEventProfile(OcrSource.LOCAL_EVENT, id)
            Log.d(TAG, "SEMANTIC: Deleted profile for event $id")
        } catch (e: Exception) {
            Log.w(TAG, "SEMANTIC: Failed to delete profile for event $id: ${e.message}")
        }

        event?.let { refreshSmartLinksForEvents(listOf(it), "local event permanently deleted") }

        Log.d(TAG, "PERMANENT_DELETE: Event $id removed from database")
    }

    // ========== Sync Operations ==========

    /**
     * Get all events pending sync to Google.
     */
    suspend fun getPendingSyncEvents(): List<LocalEvent> = dao.getPendingSyncEvents()

    /**
     * Get count of pending sync events.
     */
    suspend fun getPendingSyncCount(): Int = dao.getPendingSyncCount()

    /**
     * Observe pending sync count (for UI indicator).
     */
    fun observePendingSyncCount(): Flow<Int> = dao.observePendingSyncCount()

    /**
     * Get all events with conflicts.
     */
    suspend fun getConflictEvents(): List<LocalEvent> = dao.getConflictEvents()

    /**
     * Mark an event as successfully synced with Google.
     */
    suspend fun markAsSynced(localEventId: String, googleEventId: String, etag: String?) {
        val event = dao.getById(localEventId)
        dao.markAsSynced(localEventId, googleEventId, etag)
        event?.let {
            val replacements = it.linkedEventIdsForSmartLinks()
                .map { eventId -> eventId.trim() }
                .filter { eventId -> eventId.isNotEmpty() && eventId != googleEventId }
                .associateWith { googleEventId }
            if (replacements.isNotEmpty()) {
                booxNoteRepository.rebindEventIds(replacements)
            }
        }
        event?.let { invalidateCache(it.dayId) }
        Log.d(TAG, "SYNCED: Event $localEventId synced as $googleEventId")
    }

    /**
     * Purge all events marked as PENDING_DELETE.
     * Call after successful sync to Google.
     */
    suspend fun purgePendingDeletes() {
        val deletedEvents = dao.getPendingDeleteEvents()
        if (deletedEvents.isEmpty()) {
            return
        }
        dao.purgePendingDeletes()
        booxNoteRepository.handleDeletedEvents(
            deletedEvents.flatMap { it.linkedEventIdsForSmartLinks() }.toSet()
        )
        invalidateCacheForDays(collectLocalCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "pending local event deletes purged")
        Log.d(TAG, "PURGE: Removed all PENDING_DELETE events")
    }

    // ========== Sign-out Operations ==========

    /**
     * Convert all synced events to local-only (user chose to keep data on sign-out).
     */
    suspend fun convertAllToLocalOnly() {
        val syncedEvents = dao.getAllSyncedEvents()
        dao.convertAllToLocalOnly()
        val replacements = linkedMapOf<String, String>()
        syncedEvents.forEach { event ->
            val convertedEvent = event.copy(
                syncEnabled = false,
                syncStatus = SyncStatus.LOCAL_ONLY,
                googleEventId = null,
                googleCalendarId = null,
                googleCalendarName = null,
                lastSyncedAt = null
            )
            val newPrimaryEventId = convertedEvent.toCalendarEvent().id.trim()
            if (newPrimaryEventId.isEmpty()) return@forEach

            event.linkedEventIdsForSmartLinks()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != newPrimaryEventId }
                .forEach { oldEventId ->
                    replacements[oldEventId] = newPrimaryEventId
                }
        }
        if (replacements.isNotEmpty()) {
            booxNoteRepository.rebindEventIds(replacements)
        }
        invalidateCache()
        Log.i(TAG, "SIGNOUT: Converted all synced events to local-only")
    }

    /**
     * Delete all synced events (user chose to remove synced data on sign-out).
     */
    suspend fun deleteAllSyncedEvents() {
        val deletedEvents = dao.getAllSyncedEvents()
        val deletedEventIds = deletedEvents
            .flatMap { it.linkedEventIdsForSmartLinks() }
            .toSet()
        dao.deleteAllSyncedEvents()
        booxNoteRepository.handleDeletedEvents(deletedEventIds)
        invalidateCacheForDays(collectLocalCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "all synced local events deleted")
        Log.i(TAG, "SIGNOUT: Deleted all synced events")
    }

    /**
     * Delete all events from a specific Google calendar.
     */
    suspend fun deleteEventsByGoogleCalendar(calendarId: String) {
        val deletedEvents = dao.getEventsByGoogleCalendar(calendarId)
        val deletedEventIds = deletedEvents
            .flatMap { it.linkedEventIdsForSmartLinks() }
            .toSet()
        dao.deleteEventsByGoogleCalendar(calendarId)
        booxNoteRepository.handleDeletedEvents(deletedEventIds)
        invalidateCacheForDays(collectLocalCoveredDayIds(deletedEvents))
        refreshSmartLinksForEvents(deletedEvents, "synced local calendar removed")
        Log.d(TAG, "DELETE_CALENDAR: Removed all events from calendar $calendarId")
    }

    // ========== Statistics ==========

    /**
     * Get count of synced events.
     */
    suspend fun getSyncedEventCount(): Int = dao.getSyncedEventCount()

    /**
     * Get count of local-only events.
     */
    suspend fun getLocalOnlyEventCount(): Int = dao.getLocalOnlyEventCount()

    /**
     * Get count of local-only timed events.
     * Smart Link ignores all-day events for matching.
     */
    suspend fun getLocalOnlyTimedEventCount(): Int = dao.getLocalOnlyTimedEventCount()

    /**
     * Get total event count.
     */
    suspend fun getTotalEventCount(): Int = dao.getTotalEventCount()

    // ========== Local Calendar Operations ==========

    /**
     * Migrate events from source calendars to a target calendar.
     * Used when deleting local calendars - events are moved to Default.
     * @param sourceCalendarIds IDs of calendars to migrate from
     * @param targetCalendarId ID of calendar to migrate to (typically Default)
     * @return Number of events migrated
     */
    suspend fun migrateEventsToCalendar(sourceCalendarIds: List<String>, targetCalendarId: String): Int {
        // Get count before migration for logging
        var totalCount = 0
        sourceCalendarIds.forEach { calendarId ->
            totalCount += dao.getEventCountForCalendar(calendarId)
        }

        if (totalCount > 0) {
            dao.migrateEventsToCalendar(sourceCalendarIds, targetCalendarId)
            invalidateCache() // Events moved, invalidate all cached data
            Log.i(TAG, "MIGRATE: Moved $totalCount events from ${sourceCalendarIds.size} calendars to $targetCalendarId")
        }

        return totalCount
    }

    /**
     * Get count of events in a specific local calendar.
     */
    suspend fun getEventCountForCalendar(calendarId: String): Int =
        dao.getEventCountForCalendar(calendarId)

    /**
     * Assign all events with null localCalendarId to the Default calendar.
     * Used during migration or mode switching.
     */
    suspend fun assignNullEventsToDefaultCalendar(defaultCalendarId: String): Int {
        val count = dao.getEventCountWithNullCalendar()
        if (count > 0) {
            dao.assignNullEventsToCalendar(defaultCalendarId)
            invalidateCache()
            Log.i(TAG, "ASSIGN_DEFAULT: Assigned $count events with null calendar to Default")
        }
        return count
    }

    // ========== Toggle Sync ==========

    /**
     * Toggle sync for an event.
     * @param event The event to toggle
     * @param syncEnabled New sync state
     * @param defaultCalendarId Calendar to sync to (required if enabling sync)
     * @param defaultCalendarName Calendar name (required if enabling sync)
     * @return Updated event
     */
    suspend fun toggleEventSync(
        event: LocalEvent,
        syncEnabled: Boolean,
        defaultCalendarId: String? = null,
        defaultCalendarName: String? = null
    ): LocalEvent {
        val now = System.currentTimeMillis()

        val updatedEvent = if (syncEnabled) {
            // Enabling sync: mark for creation on Google
            event.copy(
                syncEnabled = true,
                syncStatus = SyncStatus.PENDING_CREATE,
                googleCalendarId = defaultCalendarId,
                googleCalendarName = defaultCalendarName,
                updatedAt = now
            )
        } else {
            // Disabling sync
            if (event.googleEventId != null) {
                // Already synced: mark for deletion from Google, keep locally
                event.copy(
                    syncEnabled = false,
                    syncStatus = SyncStatus.PENDING_DELETE,
                    updatedAt = now
                )
            } else {
                // Never synced: just update flag
                event.copy(
                    syncEnabled = false,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    googleCalendarId = null,
                    googleCalendarName = null,
                    updatedAt = now
                )
            }
        }

        dao.update(updatedEvent)
        invalidateCache(event.dayId)
        Log.d(TAG, "TOGGLE_SYNC: Event ${event.id} sync=${syncEnabled}, status=${updatedEvent.syncStatus}")
        return updatedEvent
    }

    // ========== Notification Scheduling ==========

    /**
     * Schedule notification for an event if enabled in settings.
     * Only schedules for future events.
     */
    private fun scheduleEventNotificationIfEnabled(event: LocalEvent) {
        val settings = GlobalAppSettings.current
        if (!settings.eventNotificationsEnabled) {
            Log.d(TAG, "NOTIF: Event notifications disabled, not scheduling for ${event.id}")
            return
        }
        if (event.startTime <= System.currentTimeMillis()) {
            Log.d(TAG, "NOTIF: Event ${event.id} is in the past, not scheduling")
            return
        }

        val result = eventNotificationScheduler.scheduleEventAlarm(
            event = event,
            minutesBefore = settings.defaultEventReminderMinutes
        )
        Log.d(TAG, "NOTIF: Scheduled event ${event.id}: $result")
    }

    /**
     * Cancel notification for an event.
     */
    private fun cancelEventNotification(eventId: String) {
        eventNotificationScheduler.cancelEventAlarm(eventId)
        Log.d(TAG, "NOTIF: Cancelled notification for event $eventId")
    }

    /**
     * Schedule notifications for all upcoming events.
     * Called when event notifications are enabled in settings.
     * Schedules events within the next 30 days from BOTH tables:
     * - LocalEvent (standalone/local events)
     * - CalendarEvent (synced Google/Outlook events)
     */
    suspend fun scheduleNotificationsForUpcomingEvents() {
        val settings = GlobalAppSettings.current
        if (!settings.eventNotificationsEnabled) {
            Log.d(TAG, "NOTIF: Event notifications disabled, not scheduling upcoming events")
            return
        }

        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + (30 * 24 * 60 * 60 * 1000L)
        Log.d(TAG, "NOTIF: Querying events between $now and $thirtyDaysFromNow")

        // Query BOTH tables for upcoming events (Hybrid mode support)
        val localEvents = dao.getUpcomingEventsBetween(now, thirtyDaysFromNow)
        val calendarEvents = calendarEventDao.getUpcomingEventsBetween(now, thirtyDaysFromNow)

        Log.d(TAG, "NOTIF: Found ${localEvents.size} local events, ${calendarEvents.size} calendar events")

        var scheduledCount = 0

        // Schedule LocalEvent notifications
        localEvents.forEach { event ->
            cancelEventNotification(event.id)
            val result = eventNotificationScheduler.scheduleEventAlarm(
                event = event,
                minutesBefore = settings.defaultEventReminderMinutes
            )
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        // Schedule CalendarEvent notifications (synced Google/Outlook)
        calendarEvents.forEach { event ->
            cancelEventNotification(event.id)
            val result = eventNotificationScheduler.scheduleCalendarEventAlarm(
                event = event,
                minutesBefore = settings.defaultEventReminderMinutes
            )
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        Log.i(TAG, "NOTIF: Scheduled $scheduledCount event notifications (${localEvents.size} local + ${calendarEvents.size} synced)")
    }
}
