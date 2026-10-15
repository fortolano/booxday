package com.boox.day.sync

import android.content.Context
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.model.ProviderId
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * LocalEventService - Unified event operations for standalone and hybrid modes.
 *
 * This is an ORCHESTRATION LAYER that:
 * - STANDALONE mode: Direct CRUD on LocalEventRepository (no Google API)
 * - HYBRID mode: Delegates to CalendarSyncManager for Google API, updates LocalEventRepository
 *
 * Design principle: NO DUPLICATE FUNCTIONALITY
 * - Google API operations are delegated to CalendarSyncManager
 * - This service adds sync status tracking and mode-aware routing
 */
class LocalEventService(
    private val context: Context,
    private val localEventRepository: LocalEventRepository,
    private val calendarSyncManager: CalendarSyncManager,
    private val multiAccountSyncManager: MultiAccountSyncManager,
    private val authManager: GoogleAuthManager
) {
    companion object {
        private const val TAG = "LocalEventService"

        /** A PENDING_UPDATE whose updatedAt is this close to lastSyncedAt came from a sync write. */
        private const val STALE_PENDING_UPDATE_WINDOW_MS = 2_000L
    }

    // ========== Mode Detection ==========

    /**
     * Get current sync mode from settings.
     */
    private fun getSyncMode(): AppSettings.SyncMode {
        return GlobalAppSettings.current.syncMode
    }

    /**
     * Check if we're in hybrid mode AND signed in to Google.
     */
    private fun isHybridModeActive(): Boolean {
        return getSyncMode() == AppSettings.SyncMode.HYBRID && authManager.isSignedIn()
    }

    /**
     * Check if sync is enabled by default for new events.
     */
    private fun isDefaultSyncEnabled(): Boolean {
        return GlobalAppSettings.current.defaultSyncEnabled && isHybridModeActive()
    }

    // ========== CREATE ==========

    /**
     * Create a new event.
     *
     * STANDALONE mode: Create locally with LOCAL_ONLY status
     * HYBRID mode (sync enabled): Create on Google first, then store locally as SYNCED
     * HYBRID mode (sync disabled): Create locally with LOCAL_ONLY status
     *
     * @param title Event title
     * @param dayId Date in "yyyy-MM-dd" format
     * @param startTime Start time in epoch millis
     * @param endTime End time in epoch millis (null for open-ended)
     * @param isAllDay True if all-day event
     * @param location Optional location
     * @param description Optional description
     * @param syncEnabled Whether to sync this event to Google (only in HYBRID mode)
     * @return Created LocalEvent or null on failure
     */
    suspend fun createEvent(
        title: String,
        dayId: String,
        startTime: Long,
        endTime: Long? = null,
        isAllDay: Boolean = false,
        location: String? = null,
        description: String? = null,
        syncEnabled: Boolean = isDefaultSyncEnabled()
    ): LocalEventResult<LocalEvent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "CREATE: title=$title, dayId=$dayId, syncEnabled=$syncEnabled, mode=${getSyncMode()}")

        // Build the local event
        val now = System.currentTimeMillis()
        // Calculate endDayId for multi-day event support
        val endDayId = if (endTime != null) {
            val endDate = java.time.Instant.ofEpochMilli(endTime).atZone(ZoneId.systemDefault()).toLocalDate()
            val startDate = LocalDate.parse(dayId)
            val inclusiveEnd = if (isAllDay) endDate.minusDays(1) else endDate
            val finalEnd = if (inclusiveEnd.isBefore(startDate)) startDate else inclusiveEnd
            finalEnd.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            dayId
        }
        val localEvent = LocalEvent(
            title = title,
            dayId = dayId,
            endDayId = endDayId,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location,
            description = description,
            syncEnabled = syncEnabled && isHybridModeActive(),
            syncStatus = if (syncEnabled && isHybridModeActive()) SyncStatus.PENDING_CREATE else SyncStatus.LOCAL_ONLY,
            createdAt = now,
            updatedAt = now
        )

        // STANDALONE mode or sync disabled: Just create locally
        if (!syncEnabled || !isHybridModeActive()) {
            val created = localEventRepository.createEvent(localEvent)
            Log.i(TAG, "CREATE: Local-only event created: ${created.id}")
            return@withContext LocalEventResult.Success(created)
        }

        // HYBRID mode with sync enabled: Try to create on Google first
        val defaultCalendarId = authManager.getDefaultCalendarId()
        if (defaultCalendarId == null) {
            // No default calendar set, create locally as pending
            val created = localEventRepository.createEvent(localEvent)
            Log.w(TAG, "CREATE: No default calendar, created as PENDING_CREATE: ${created.id}")
            return@withContext LocalEventResult.Success(created)
        }

        // Extract time components for NewEventData
        val startDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(startTime),
            ZoneId.systemDefault()
        )
        val endDateTime = endTime?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val newEventData = NewEventData(
            calendarId = defaultCalendarId,
            title = title,
            date = dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = isAllDay,
            location = location,
            description = description
        )

        // Delegate to CalendarSyncManager for Google API call
        when (val result = calendarSyncManager.createEvent(newEventData)) {
            is EventOperationResult.Success -> {
                // Google creation succeeded - store locally as SYNCED
                val calendarEvent = result.data
                val syncedLocalEvent = localEvent.copy(
                    syncStatus = SyncStatus.SYNCED,
                    googleEventId = calendarEvent.id,
                    googleCalendarId = calendarEvent.calendarId,
                    googleCalendarName = calendarEvent.calendarName,
                    lastSyncedAt = now,
                    colorHex = calendarEvent.colorHex,
                    isReadOnly = calendarEvent.isReadOnly
                )
                val created = localEventRepository.createEvent(syncedLocalEvent)
                Log.i(TAG, "CREATE: Synced event created: ${created.id} -> Google: ${calendarEvent.id}")
                LocalEventResult.Success(created)
            }

            is EventOperationResult.NoNetwork -> {
                // Offline - create locally as pending
                val created = localEventRepository.createEvent(localEvent)
                Log.w(TAG, "CREATE: Offline, created as PENDING_CREATE: ${created.id}")
                LocalEventResult.Success(created)
            }

            is EventOperationResult.NotSignedIn -> {
                // Not signed in - create as local only
                val localOnlyEvent = localEvent.copy(
                    syncEnabled = false,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                val created = localEventRepository.createEvent(localOnlyEvent)
                Log.w(TAG, "CREATE: Not signed in, created as LOCAL_ONLY: ${created.id}")
                LocalEventResult.Success(created)
            }

            is EventOperationResult.PermissionDenied -> {
                Log.e(TAG, "CREATE: Permission denied for calendar $defaultCalendarId")
                LocalEventResult.Error("Permission denied for this calendar")
            }

            is EventOperationResult.Error -> {
                // Other error - create locally as pending for retry
                val created = localEventRepository.createEvent(localEvent)
                Log.e(TAG, "CREATE: Google error (${result.message}), created as PENDING_CREATE: ${created.id}")
                LocalEventResult.Success(created)
            }
        }
    }

    // ========== READ ==========

    /**
     * Get events for a specific date.
     * Returns from LocalEventRepository (source of truth).
     */
    suspend fun getEventsForDay(dayId: String): List<LocalEvent> {
        return localEventRepository.getEventsForDay(dayId)
    }

    /**
     * Observe events for a specific date (reactive).
     */
    fun observeEventsForDay(dayId: String): Flow<List<LocalEvent>> {
        return localEventRepository.observeEventsForDay(dayId)
    }

    /**
     * Get a single event by ID.
     */
    suspend fun getEventById(id: String): LocalEvent? {
        return localEventRepository.getEventById(id)
    }

    /**
     * Get events for a date range.
     */
    suspend fun getEventsForRange(startDate: LocalDate, endDate: LocalDate): List<LocalEvent> {
        return localEventRepository.getEventsForRange(startDate, endDate)
    }

    /**
     * Get dates with events for a month (for calendar badges).
     */
    suspend fun getDatesWithEventsForMonth(yearMonth: String): Set<String> {
        return localEventRepository.getDatesWithEventsForMonth(yearMonth)
    }

    /**
     * Pre-warm cache for a month.
     */
    suspend fun preWarmCacheForMonth(yearMonth: String): Set<String> {
        return localEventRepository.preWarmCacheForMonth(yearMonth)
    }

    // ========== UPDATE ==========

    /**
     * Update an existing event.
     *
     * STANDALONE mode: Update locally only
     * HYBRID mode (synced event): Update on Google first, then update locally
     * HYBRID mode (local-only event): Update locally only
     *
     * @param event The event to update (with new values)
     * @return Updated LocalEvent or error
     */
    suspend fun updateEvent(event: LocalEvent): LocalEventResult<LocalEvent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "UPDATE: id=${event.id}, syncEnabled=${event.syncEnabled}, status=${event.syncStatus}")

        // If not synced or not in hybrid mode, just update locally
        if (!event.syncEnabled || !isHybridModeActive() || event.googleEventId == null) {
            val updated = localEventRepository.updateEvent(event)
            Log.i(TAG, "UPDATE: Local-only update: ${updated.id}")
            return@withContext LocalEventResult.Success(updated)
        }

        // HYBRID mode with synced event: Update on Google first
        val calendarId = event.googleCalendarId ?: authManager.getDefaultCalendarId()
        if (calendarId == null) {
            // No calendar ID - update locally as pending
            val pendingEvent = event.copy(syncStatus = SyncStatus.PENDING_UPDATE)
            val updated = localEventRepository.updateEvent(pendingEvent)
            Log.w(TAG, "UPDATE: No calendar ID, marked as PENDING_UPDATE: ${updated.id}")
            return@withContext LocalEventResult.Success(updated)
        }

        // Extract time components
        val startDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(event.startTime),
            ZoneId.systemDefault()
        )
        val endDateTime = event.endTime?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val updateData = UpdateEventData(
            eventId = event.googleEventId!!,
            calendarId = calendarId,
            title = event.title,
            date = event.dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = event.isAllDay,
            location = event.location,
            description = event.description
        )

        // Delegate to CalendarSyncManager
        when (val result = calendarSyncManager.updateEvent(updateData)) {
            is EventOperationResult.Success -> {
                val now = System.currentTimeMillis()
                val syncedEvent = event.copy(
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = now,
                    updatedAt = now
                )
                val updated = localEventRepository.updateEvent(syncedEvent)
                Log.i(TAG, "UPDATE: Synced update: ${updated.id}")
                LocalEventResult.Success(updated)
            }

            is EventOperationResult.NoNetwork -> {
                // Offline - mark as pending update
                val pendingEvent = event.copy(syncStatus = SyncStatus.PENDING_UPDATE)
                val updated = localEventRepository.updateEvent(pendingEvent)
                Log.w(TAG, "UPDATE: Offline, marked as PENDING_UPDATE: ${updated.id}")
                LocalEventResult.Success(updated)
            }

            is EventOperationResult.NotSignedIn -> {
                // Convert to local-only
                val localOnlyEvent = event.copy(
                    syncEnabled = false,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                val updated = localEventRepository.updateEvent(localOnlyEvent)
                Log.w(TAG, "UPDATE: Not signed in, converted to LOCAL_ONLY: ${updated.id}")
                LocalEventResult.Success(updated)
            }

            is EventOperationResult.PermissionDenied -> {
                Log.e(TAG, "UPDATE: Permission denied")
                LocalEventResult.Error("Permission denied for this calendar")
            }

            is EventOperationResult.Error -> {
                // Error - mark as pending for retry
                val pendingEvent = event.copy(syncStatus = SyncStatus.PENDING_UPDATE)
                val updated = localEventRepository.updateEvent(pendingEvent)
                Log.e(TAG, "UPDATE: Google error (${result.message}), marked as PENDING_UPDATE: ${updated.id}")
                LocalEventResult.Success(updated)
            }
        }
    }

    // ========== DELETE ==========

    /**
     * Delete an event.
     *
     * STANDALONE mode: Delete locally only
     * HYBRID mode (synced event): Delete from Google first, then delete locally
     * HYBRID mode (local-only event): Delete locally only
     *
     * @param event The event to delete
     * @return Success or error
     */
    suspend fun deleteEvent(event: LocalEvent): LocalEventResult<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "DELETE: id=${event.id}, syncEnabled=${event.syncEnabled}, googleEventId=${event.googleEventId}")

        // If not synced or not in hybrid mode, just delete locally
        if (!event.syncEnabled || !isHybridModeActive() || event.googleEventId == null) {
            localEventRepository.deleteEvent(event)
            Log.i(TAG, "DELETE: Local-only delete: ${event.id}")
            return@withContext LocalEventResult.Success(Unit)
        }

        // HYBRID mode with synced event: Delete from Google first
        val calendarId = event.googleCalendarId ?: authManager.getDefaultCalendarId()
        if (calendarId == null) {
            // No calendar ID - mark as pending delete
            localEventRepository.deleteEvent(event) // This marks as PENDING_DELETE for synced events
            Log.w(TAG, "DELETE: No calendar ID, marked as PENDING_DELETE: ${event.id}")
            return@withContext LocalEventResult.Success(Unit)
        }

        // Delegate to CalendarSyncManager
        when (val result = calendarSyncManager.deleteEvent(event.googleEventId!!, calendarId)) {
            is EventOperationResult.Success -> {
                // Google deletion succeeded - delete locally permanently
                localEventRepository.permanentlyDelete(event.id)
                Log.i(TAG, "DELETE: Synced delete: ${event.id}")
                LocalEventResult.Success(Unit)
            }

            is EventOperationResult.NoNetwork -> {
                // Offline - mark as pending delete
                localEventRepository.deleteEvent(event)
                Log.w(TAG, "DELETE: Offline, marked as PENDING_DELETE: ${event.id}")
                LocalEventResult.Success(Unit)
            }

            is EventOperationResult.NotSignedIn -> {
                // Not signed in - delete locally only
                localEventRepository.permanentlyDelete(event.id)
                Log.w(TAG, "DELETE: Not signed in, deleted locally: ${event.id}")
                LocalEventResult.Success(Unit)
            }

            is EventOperationResult.PermissionDenied -> {
                Log.e(TAG, "DELETE: Permission denied")
                LocalEventResult.Error("Permission denied for this calendar")
            }

            is EventOperationResult.Error -> {
                // Error - mark as pending for retry
                localEventRepository.deleteEvent(event)
                Log.e(TAG, "DELETE: Google error (${result.message}), marked as PENDING_DELETE: ${event.id}")
                LocalEventResult.Success(Unit)
            }
        }
    }

    // ========== SYNC OPERATIONS ==========

    /**
     * Sync pending changes to cloud providers.
     * Called periodically or on-demand when network is available.
     *
     * @return Sync result with counts
     */
    suspend fun syncPendingChanges(): SyncPendingResult = withContext(Dispatchers.IO) {
        if (getSyncMode() != AppSettings.SyncMode.HYBRID) {
            Log.d(TAG, "SYNC_PENDING: Not in hybrid mode, skipping")
            return@withContext SyncPendingResult(0, 0, 0, emptyList())
        }

        if (!multiAccountSyncManager.hasAnyAccounts()) {
            Log.d(TAG, "SYNC_PENDING: No connected accounts, skipping")
            return@withContext SyncPendingResult(0, 0, 0, emptyList())
        }

        val pendingEvents = localEventRepository.getPendingSyncEvents()
        Log.d(TAG, "SYNC_PENDING: Found ${pendingEvents.size} pending events")

        var created = 0
        var updated = 0
        var deleted = 0
        val errors = mutableListOf<String>()

        for (event in pendingEvents) {
            val providerId = resolveProviderId(event)
            val hasAccount = !event.accountId.isNullOrBlank()
            val useProviderRouting = providerId != null && hasAccount

            when (event.syncStatus) {
                SyncStatus.PENDING_CREATE -> {
                    val result = when {
                        useProviderRouting -> pushCreateToProvider(event, providerId!!, event.accountId!!)
                        providerId == null || providerId == ProviderId.GOOGLE -> pushCreateToGoogle(event)
                        else -> LocalEventResult.Error("Missing account for ${providerId.displayName}")
                    }
                    if (result is LocalEventResult.Success) created++
                    else if (result is LocalEventResult.Error) errors.add("Create ${event.title}: ${result.message}")
                }

                SyncStatus.PENDING_UPDATE -> {
                    // Plan Phase 2 (Oct 2026): before the status-mapping fix, writes that recorded
                    // a provider confirmation were re-flagged PENDING_UPDATE (updatedAt set
                    // milliseconds after lastSyncedAt). Those hold nothing new — mark them synced
                    // instead of uploading them over newer remote edits.
                    val syncedAt = event.lastSyncedAt
                    if (syncedAt != null && event.updatedAt - syncedAt in 0..STALE_PENDING_UPDATE_WINDOW_MS) {
                        localEventRepository.updateSyncStatus(event.id, SyncStatus.SYNCED)
                        Log.i(TAG, "SYNC_PENDING: ${event.id} was flagged by its own sync confirmation; marked SYNCED")
                        continue
                    }
                    val result = when {
                        useProviderRouting -> pushUpdateToProvider(event, providerId!!, event.accountId!!)
                        providerId == null || providerId == ProviderId.GOOGLE -> pushUpdateToGoogle(event)
                        else -> LocalEventResult.Error("Missing account for ${providerId.displayName}")
                    }
                    if (result is LocalEventResult.Success) updated++
                    else if (result is LocalEventResult.Error) errors.add("Update ${event.title}: ${result.message}")
                }

                SyncStatus.PENDING_DELETE -> {
                    val result = when {
                        useProviderRouting -> pushDeleteToProvider(event, providerId!!, event.accountId!!)
                        providerId == null || providerId == ProviderId.GOOGLE -> pushDeleteToGoogle(event)
                        else -> LocalEventResult.Error("Missing account for ${providerId.displayName}")
                    }
                    if (result is LocalEventResult.Success) deleted++
                    else if (result is LocalEventResult.Error) errors.add("Delete ${event.title}: ${result.message}")
                }

                else -> { /* Not pending, skip */ }
            }
        }

        Log.i(TAG, "SYNC_PENDING: Complete - created=$created, updated=$updated, deleted=$deleted, errors=${errors.size}")
        SyncPendingResult(created, updated, deleted, errors)
    }

    private fun resolveProviderId(event: LocalEvent): ProviderId? = when (event.providerId) {
        ProviderId.GOOGLE.key -> ProviderId.GOOGLE
        ProviderId.OUTLOOK.key -> ProviderId.OUTLOOK
        else -> null
    }

    /**
     * Push a PENDING_CREATE event to its provider (Google or Outlook).
     */
    private suspend fun pushCreateToProvider(
        event: LocalEvent,
        providerId: ProviderId,
        accountId: String
    ): LocalEventResult<LocalEvent> {
        val calendarId = event.providerCalendarId ?: event.googleCalendarId
        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault())
        val endDateTime = event.endTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val newEventData = NewEventData(
            calendarId = calendarId ?: "",
            title = event.title,
            date = event.dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = event.isAllDay,
            location = event.location,
            description = event.description,
            attendees = event.attendees,
            syncEnabled = true
        )

        val result = multiAccountSyncManager.createEvent(
            accountId = accountId,
            calendarId = calendarId?.ifBlank { null },
            event = newEventData
        )

        if (result.isFailure) {
            return LocalEventResult.Error(result.exceptionOrNull()?.message ?: "Create failed")
        }

        val cloudEvent = result.getOrThrow()
        val now = System.currentTimeMillis()
        val isGoogle = providerId == ProviderId.GOOGLE
        val syncedEvent = event.copy(
            providerId = providerId.key,
            accountId = accountId,
            accountEmail = cloudEvent.accountEmail.takeIf { it.isNotBlank() } ?: event.accountEmail,
            providerEventId = cloudEvent.id,
            providerCalendarId = cloudEvent.calendarId,
            providerCalendarName = cloudEvent.calendarName,
            googleEventId = if (isGoogle) cloudEvent.id else null,
            googleCalendarId = if (isGoogle) cloudEvent.calendarId else null,
            googleCalendarName = if (isGoogle) cloudEvent.calendarName else null,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = now,
            updatedAt = now
        )
        localEventRepository.updateEvent(syncedEvent)
        return LocalEventResult.Success(syncedEvent)
    }

    /**
     * Push a PENDING_UPDATE event to its provider (Google or Outlook).
     */
    private suspend fun pushUpdateToProvider(
        event: LocalEvent,
        providerId: ProviderId,
        accountId: String
    ): LocalEventResult<LocalEvent> {
        val eventId = event.providerEventId ?: event.googleEventId
            ?: return LocalEventResult.Error("No provider event ID")
        val calendarId = event.providerCalendarId ?: event.googleCalendarId
            ?: return LocalEventResult.Error("No calendar ID for sync")

        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault())
        val endDateTime = event.endTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val updateData = UpdateEventData(
            eventId = eventId,
            calendarId = calendarId,
            title = event.title,
            date = event.dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = event.isAllDay,
            location = event.location,
            description = event.description,
            attendees = event.attendees,
            syncEnabled = true
        )

        val result = multiAccountSyncManager.updateEvent(
            accountId = accountId,
            calendarId = calendarId,
            eventId = eventId,
            event = updateData
        )

        if (result.isFailure) {
            return LocalEventResult.Error(result.exceptionOrNull()?.message ?: "Update failed")
        }

        val cloudEvent = result.getOrThrow()
        val now = System.currentTimeMillis()
        val isGoogle = providerId == ProviderId.GOOGLE
        val syncedEvent = event.copy(
            providerId = providerId.key,
            accountId = accountId,
            accountEmail = cloudEvent.accountEmail.takeIf { it.isNotBlank() } ?: event.accountEmail,
            providerEventId = cloudEvent.id,
            providerCalendarId = cloudEvent.calendarId,
            providerCalendarName = cloudEvent.calendarName,
            googleEventId = if (isGoogle) cloudEvent.id else null,
            googleCalendarId = if (isGoogle) cloudEvent.calendarId else null,
            googleCalendarName = if (isGoogle) cloudEvent.calendarName else null,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = now,
            updatedAt = now
        )
        localEventRepository.updateEvent(syncedEvent)
        return LocalEventResult.Success(syncedEvent)
    }

    /**
     * Push a PENDING_DELETE event to its provider (Google or Outlook).
     */
    private suspend fun pushDeleteToProvider(
        event: LocalEvent,
        providerId: ProviderId,
        accountId: String
    ): LocalEventResult<Unit> {
        val eventId = event.providerEventId ?: event.googleEventId ?: run {
            localEventRepository.permanentlyDelete(event.id)
            return LocalEventResult.Success(Unit)
        }
        val calendarId = event.providerCalendarId ?: event.googleCalendarId
            ?: return LocalEventResult.Error("No calendar ID for sync")

        val result = multiAccountSyncManager.deleteEvent(
            accountId = accountId,
            calendarId = calendarId,
            eventId = eventId
        )

        return if (result.isSuccess) {
            localEventRepository.permanentlyDelete(event.id)
            LocalEventResult.Success(Unit)
        } else {
            LocalEventResult.Error("${providerId.displayName} delete failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
        }
    }

    /**
     * Push a PENDING_CREATE event to Google.
     */
    private suspend fun pushCreateToGoogle(event: LocalEvent): LocalEventResult<LocalEvent> {
        val calendarId = event.googleCalendarId ?: authManager.getDefaultCalendarId()
            ?: return LocalEventResult.Error("No calendar ID for sync")

        val startDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(event.startTime),
            ZoneId.systemDefault()
        )
        val endDateTime = event.endTime?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val newEventData = NewEventData(
            calendarId = calendarId,
            title = event.title,
            date = event.dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = event.isAllDay,
            location = event.location,
            description = event.description
        )

        return when (val result = calendarSyncManager.createEvent(newEventData)) {
            is EventOperationResult.Success -> {
                val now = System.currentTimeMillis()
                val syncedEvent = event.copy(
                    syncStatus = SyncStatus.SYNCED,
                    googleEventId = result.data.id,
                    googleCalendarId = result.data.calendarId,
                    googleCalendarName = result.data.calendarName,
                    lastSyncedAt = now,
                    updatedAt = now
                )
                localEventRepository.updateEvent(syncedEvent)
                LocalEventResult.Success(syncedEvent)
            }
            is EventOperationResult.NoNetwork -> LocalEventResult.Error("No network")
            is EventOperationResult.NotSignedIn -> LocalEventResult.Error("Not signed in")
            is EventOperationResult.PermissionDenied -> LocalEventResult.Error("Permission denied")
            is EventOperationResult.Error -> LocalEventResult.Error(result.message)
        }
    }

    /**
     * Push a PENDING_UPDATE event to Google.
     */
    private suspend fun pushUpdateToGoogle(event: LocalEvent): LocalEventResult<LocalEvent> {
        val googleEventId = event.googleEventId ?: return LocalEventResult.Error("No Google event ID")
        val calendarId = event.googleCalendarId ?: authManager.getDefaultCalendarId()
            ?: return LocalEventResult.Error("No calendar ID for sync")

        val startDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(event.startTime),
            ZoneId.systemDefault()
        )
        val endDateTime = event.endTime?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

        val updateData = UpdateEventData(
            eventId = googleEventId,
            calendarId = calendarId,
            title = event.title,
            date = event.dayId,
            startTimeHour = startDateTime.hour,
            startTimeMinute = startDateTime.minute,
            endTimeHour = endDateTime?.hour,
            endTimeMinute = endDateTime?.minute,
            isAllDay = event.isAllDay,
            location = event.location,
            description = event.description
        )

        return when (val result = calendarSyncManager.updateEvent(updateData)) {
            is EventOperationResult.Success -> {
                val now = System.currentTimeMillis()
                val syncedEvent = event.copy(
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = now,
                    updatedAt = now
                )
                localEventRepository.updateEvent(syncedEvent)
                LocalEventResult.Success(syncedEvent)
            }
            is EventOperationResult.NoNetwork -> LocalEventResult.Error("No network")
            is EventOperationResult.NotSignedIn -> LocalEventResult.Error("Not signed in")
            is EventOperationResult.PermissionDenied -> LocalEventResult.Error("Permission denied")
            is EventOperationResult.Error -> LocalEventResult.Error(result.message)
        }
    }

    /**
     * Push a PENDING_DELETE event to Google.
     */
    private suspend fun pushDeleteToGoogle(event: LocalEvent): LocalEventResult<Unit> {
        val googleEventId = event.googleEventId ?: run {
            // No Google ID means it was never synced, just delete locally
            localEventRepository.permanentlyDelete(event.id)
            return LocalEventResult.Success(Unit)
        }

        val calendarId = event.googleCalendarId ?: authManager.getDefaultCalendarId()
            ?: return LocalEventResult.Error("No calendar ID for sync")

        return when (val result = calendarSyncManager.deleteEvent(googleEventId, calendarId)) {
            is EventOperationResult.Success -> {
                localEventRepository.permanentlyDelete(event.id)
                LocalEventResult.Success(Unit)
            }
            is EventOperationResult.NoNetwork -> LocalEventResult.Error("No network")
            is EventOperationResult.NotSignedIn -> {
                // Not signed in - delete locally anyway
                localEventRepository.permanentlyDelete(event.id)
                LocalEventResult.Success(Unit)
            }
            is EventOperationResult.PermissionDenied -> LocalEventResult.Error("Permission denied")
            is EventOperationResult.Error -> LocalEventResult.Error(result.message)
        }
    }

    /**
     * Pull events from Google Calendar and merge with local events.
     * Delegates to CalendarSyncManager for the Google API call.
     *
     * @param startDate Start of sync range
     * @param endDate End of sync range
     * @return Sync result
     */
    suspend fun pullFromGoogle(startDate: LocalDate, endDate: LocalDate): SyncResult = withContext(Dispatchers.IO) {
        if (!isHybridModeActive()) {
            Log.d(TAG, "PULL: Not in hybrid mode, skipping")
            return@withContext SyncResult.NotSignedIn("Not in hybrid mode")
        }

        // Delegate to CalendarSyncManager for Google API call
        // Note: Currently CalendarSyncManager updates CalendarEventRepository
        // In future, we'll update this to use LocalEventRepository with converters
        val result = calendarSyncManager.syncEvents(startDate, endDate)

        // TODO: Phase 4 will add proper merging with conflict detection
        // For now, just return the sync result
        result
    }

    // ========== TOGGLE SYNC ==========

    /**
     * Toggle sync for a specific event.
     *
     * @param event The event to toggle
     * @param syncEnabled New sync state
     * @return Updated event
     */
    suspend fun toggleEventSync(event: LocalEvent, syncEnabled: Boolean): LocalEventResult<LocalEvent> = withContext(Dispatchers.IO) {
        if (!isHybridModeActive()) {
            Log.w(TAG, "TOGGLE_SYNC: Not in hybrid mode, cannot enable sync")
            return@withContext LocalEventResult.Error("Sign in to Google to enable sync")
        }

        val defaultCalendarId = authManager.getDefaultCalendarId()
        val defaultCalendarName = authManager.getSelectedCalendars().firstOrNull()

        val updated = localEventRepository.toggleEventSync(
            event = event,
            syncEnabled = syncEnabled,
            defaultCalendarId = defaultCalendarId,
            defaultCalendarName = defaultCalendarName
        )

        Log.i(TAG, "TOGGLE_SYNC: Event ${event.id} sync=${syncEnabled}, status=${updated.syncStatus}")
        LocalEventResult.Success(updated)
    }

    // ========== CONFLICT HANDLING ==========

    // Lazy-initialized conflict resolver
    private val conflictResolver by lazy { ConflictResolver(localEventRepository) }

    /**
     * Get all events with unresolved conflicts.
     */
    suspend fun getConflictEvents(): List<LocalEvent> {
        return localEventRepository.getConflictEvents()
    }

    /**
     * Check if there are any unresolved conflicts.
     */
    suspend fun hasConflicts(): Boolean {
        return conflictResolver.hasUnresolvedConflicts()
    }

    /**
     * Resolve a conflict using the specified strategy.
     *
     * @param conflict The conflict info from detectConflict()
     * @param strategy The resolution strategy chosen by user
     * @return Resolution result
     */
    suspend fun resolveConflict(
        conflict: ConflictInfo,
        strategy: ConflictResolutionStrategy
    ): ConflictResolutionResult = withContext(Dispatchers.IO) {
        // Get calendar info for the event
        val calendarId = conflict.localEvent.googleCalendarId ?: authManager.getDefaultCalendarId()
        if (calendarId == null) {
            return@withContext ConflictResolutionResult.Error("No calendar ID available")
        }

        // Get calendar info from sync manager
        val calendars = calendarSyncManager.getAvailableCalendars()
        val calendarInfo = calendars.find { it.id == calendarId }
            ?: GoogleCalendarInfo(
                id = calendarId,
                name = conflict.localEvent.googleCalendarName ?: "Unknown",
                isPrimary = false,
                accessRole = "writer",
                isWritable = true
            )

        val result = conflictResolver.resolveConflict(conflict, strategy, calendarInfo)

        // If we chose KEEP_LOCAL, trigger a sync to push changes
        if (result is ConflictResolutionResult.Resolved &&
            strategy == ConflictResolutionStrategy.KEEP_LOCAL) {
            // The event still has PENDING_UPDATE status, will be synced
            Log.d(TAG, "CONFLICT_RESOLVED: KEEP_LOCAL - will push on next sync")
        }

        result
    }

    /**
     * Mark an event as having a conflict for manual resolution.
     */
    suspend fun markEventAsConflict(event: LocalEvent): LocalEvent {
        return conflictResolver.markAsConflict(event)
    }

    // ========== STATISTICS ==========

    /**
     * Get count of events pending sync.
     */
    suspend fun getPendingSyncCount(): Int {
        return localEventRepository.getPendingSyncCount()
    }

    /**
     * Observe count of pending sync events (for UI indicator).
     */
    fun observePendingSyncCount(): Flow<Int> {
        return localEventRepository.observePendingSyncCount()
    }

    /**
     * Check if there are any events.
     */
    suspend fun hasAnyEvents(): Boolean {
        return localEventRepository.hasAnyEvents()
    }
}

// ========== Result Types ==========

/**
 * Result type for LocalEventService operations.
 */
sealed class LocalEventResult<out T> {
    data class Success<T>(val data: T) : LocalEventResult<T>()
    data class Error(val message: String) : LocalEventResult<Nothing>()
}

/**
 * Result of syncing pending changes.
 */
data class SyncPendingResult(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val errors: List<String>
) {
    val totalSynced: Int get() = created + updated + deleted
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun toDisplayMessage(): String {
        return when {
            totalSynced == 0 && !hasErrors -> "No pending changes"
            hasErrors -> "Synced $totalSynced, ${errors.size} errors"
            else -> "Synced: $created created, $updated updated, $deleted deleted"
        }
    }
}
