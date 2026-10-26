package com.boox.day.sync

import android.content.Context
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.collectCoveredDayIds
import com.boox.day.data.db.smartLinkFieldsChanged
import com.boox.day.booxnotes.SmartLinkRefreshCoordinator
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

internal data class CalendarFetchFailure(
    val calendarId: String,
    val errorType: SyncErrorType,
    val message: String
)

internal fun buildCalendarFetchSyncResult(
    successfulCalendarCount: Int,
    fetchFailures: List<CalendarFetchFailure>,
    newEventsCount: Int,
    updatedEventsCount: Int
): SyncResult {
    val syncedEventCount = newEventsCount + updatedEventsCount
    val notFoundFailures = fetchFailures.filter { it.errorType == SyncErrorType.NOT_FOUND }
    val realFailures = fetchFailures.filter { it.errorType != SyncErrorType.NOT_FOUND }
    val deletedCalendarIds = notFoundFailures.map { it.calendarId }.distinct()

    if (realFailures.isEmpty()) {
        return SyncResult.Success(
            eventCount = syncedEventCount,
            newEvents = newEventsCount,
            updatedEvents = updatedEventsCount,
            deletedCalendarIds = deletedCalendarIds
        )
    }

    if (successfulCalendarCount > 0) {
        return SyncResult.PartialSuccess(
            successCount = syncedEventCount,
            failureCount = realFailures.size,
            errors = realFailures.map { "${it.calendarId}: ${it.message}" }.distinct(),
            deletedCalendarIds = deletedCalendarIds
        )
    }

    if (realFailures.all { it.errorType == SyncErrorType.NETWORK }) {
        return SyncResult.NoNetwork(deletedCalendarIds = deletedCalendarIds)
    }

    val dominantErrorType = realFailures.map { it.errorType }.distinct().singleOrNull() ?: SyncErrorType.UNKNOWN
    val message = when (dominantErrorType) {
        SyncErrorType.AUTHENTICATION -> "Authentication failed while fetching calendars"
        SyncErrorType.PERMISSION -> "Permission denied while fetching calendars"
        SyncErrorType.QUOTA -> "Calendar sync quota exceeded"
        SyncErrorType.SERVER -> "Google Calendar server error"
        SyncErrorType.CLIENT -> "Failed to fetch calendars"
        SyncErrorType.NOT_FOUND -> "Selected calendar no longer exists"
        else -> if (realFailures.size > 1) {
            "Failed to fetch ${realFailures.size} calendars"
        } else {
            realFailures.firstOrNull()?.message ?: "Failed to fetch calendars"
        }
    }

    return SyncResult.Error(
        message = message,
        errorType = dominantErrorType,
        deletedCalendarIds = deletedCalendarIds
    )
}

/**
 * Calendar Sync Manager for BooxDay
 * Handles Google Calendar API operations including full CRUD:
 * - Create: Add new events to Google Calendar
 * - Read: Sync events from Google Calendar to local cache
 * - Update: Edit existing events on Google Calendar
 * - Delete: Remove events from Google Calendar
 */
class CalendarSyncManager(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val eventRepository: CalendarEventRepository
) {
    companion object {
        private const val TAG = "CalendarSyncManager"
        private const val APP_NAME = "BooxDay"
    }

    private val smartLinkRefreshCoordinator by lazy {
        SmartLinkRefreshCoordinator(context.applicationContext, eventRepository = eventRepository)
    }

    private suspend fun refreshSmartLinksForEvents(events: Collection<CalendarEvent>, reason: String) {
        val affectedDates = collectCoveredDayIds(events)
        if (affectedDates.isEmpty()) return
        smartLinkRefreshCoordinator.refreshDates(affectedDates, reason)
    }

    private fun logCalendarFetchSummary(fetchFailures: List<CalendarFetchFailure>, totalCalendars: Int) {
        val networkFailureCount = fetchFailures.count { it.errorType == SyncErrorType.NETWORK }
        if (networkFailureCount == 0) return

        val message = if (networkFailureCount == totalCalendars) {
            "Calendar sync skipped: no internet connection"
        } else {
            "Skipped $networkFailureCount calendar(s) during sync: no internet connection"
        }
        Log.w(TAG, message)
    }

    private suspend fun cleanupDeletedCalendars(
        deletedCalendarIds: List<String>,
        accountId: String,
        selectedCalendars: Set<String>,
        updateLegacySelection: Boolean
    ) {
        val normalizedIds = deletedCalendarIds.distinct()
        if (normalizedIds.isEmpty()) return

        if (updateLegacySelection) {
            val updatedSelection = selectedCalendars - normalizedIds.toSet()
            if (updatedSelection != selectedCalendars) {
                authManager.setSelectedCalendars(updatedSelection)
            }
        }

        normalizedIds.forEach { calendarId ->
            try {
                eventRepository.deleteEventsForCalendar(accountId, calendarId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove cached events for deleted calendar $calendarId", e)
            }
        }

        val syncScope = if (updateLegacySelection) "legacy Google sync" else "Google account $accountId"
        Log.w(
            TAG,
            "Auto-removed ${normalizedIds.size} deleted calendar(s) from $syncScope: ${normalizedIds.joinToString()}"
        )
    }

    /**
     * Build Google Calendar service with authenticated credential.
     *
     * @param credential Optional per-account credential. If null, falls back to
     *   the legacy single-account credential from GoogleAuthManager.
     */
    private fun getCalendarService(credential: GoogleAccountCredential? = null): Calendar? {
        val cred = credential ?: authManager.getCredential() ?: return null
        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            cred
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    // ========== READ OPERATIONS (Sync) ==========

    /**
     * Sync calendar events for a date range
     * @param accountId Account ID for multi-account support (empty string for legacy/migration)
     * @param accountEmail Account email for display
     * @param providerId Provider identifier ("GOOGLE")
     * @param credential Optional per-account credential for multi-account support
     */
    suspend fun syncEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        accountId: String = "",
        accountEmail: String = "",
        providerId: String = "GOOGLE",
        credential: GoogleAccountCredential? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService(credential)
                ?: return@withContext SyncResult.NotSignedIn()

            // Convert dates to RFC3339 format for API
            val timeMin = DateTime(
                Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            )
            val timeMax = DateTime(
                Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
            )

            Log.d(TAG, "Fetching events from $startDate to $endDate")

            // Get list of selected calendars from preferences
            val selectedCalendars = authManager.getSelectedCalendars()

            // Get all user's calendars if no selection made yet
            val calendarList = if (selectedCalendars.isEmpty()) {
                service.calendarList().list().execute().items?.map { it.id } ?: listOf("primary")
            } else {
                selectedCalendars.toList()
            }

            val allEvents = mutableListOf<Pair<Event, GoogleCalendarInfo>>()
            val fetchFailures = mutableListOf<CalendarFetchFailure>()

            // Fetch events from each selected calendar
            for (calendarId in calendarList) {
                try {
                    // Get calendar info
                    val calendarEntry = service.calendarList().get(calendarId).execute()
                    val calendarInfo = GoogleCalendarInfo.fromCalendarListEntry(calendarEntry)

                    // Fetch events from this calendar with pagination
                    // Google Calendar API returns max 250 events per page by default
                    var pageToken: String? = null
                    do {
                        val request = service.events().list(calendarId)
                            .setTimeMin(timeMin)
                            .setTimeMax(timeMax)
                            .setOrderBy("startTime")
                            .setSingleEvents(true)

                        if (pageToken != null) {
                            request.setPageToken(pageToken)
                        }

                        val events: Events = request.execute()
                        events.items?.forEach { event ->
                            allEvents.add(Pair(event, calendarInfo))
                        }

                        pageToken = events.nextPageToken
                    } while (pageToken != null)
                } catch (e: Exception) {
                    val errorType = e.toSyncErrorType()
                    val errorMessage = e.message ?: e.javaClass.simpleName
                    if (errorType != SyncErrorType.NETWORK && errorType != SyncErrorType.NOT_FOUND) {
                        Log.e(TAG, "Error fetching calendar $calendarId: $errorMessage", e)
                    }
                    fetchFailures.add(
                        CalendarFetchFailure(
                            calendarId = calendarId,
                            errorType = errorType,
                            message = errorMessage
                        )
                    )
                }
            }

            logCalendarFetchSummary(fetchFailures, calendarList.size)

            Log.d(TAG, "Fetched ${allEvents.size} events from Google Calendar")

            val existingEvents = eventRepository.getEventsInRange(
                startDate.toString(),
                endDate.toString()
            ).filter { it.accountId == accountId }
            val successfulCalendarIds = calendarList.toSet() - fetchFailures.map { it.calendarId }.toSet()

            // Convert and store events
            var newEventsCount = 0
            var updatedEventsCount = 0
            val changedEvents = mutableListOf<CalendarEvent>()
            val fetchedEventIds = mutableSetOf<String>()

            allEvents.forEach { (event, calendarInfo) ->
                try {
                    val calendarEvent = event.toCalendarEvent(calendarInfo, accountId, accountEmail, providerId)
                    if (calendarEvent != null) {
                        val existingEvent = eventRepository.getEventById(event.id)
                        eventRepository.cacheEvent(calendarEvent)
                        fetchedEventIds.add(calendarEvent.id)
                        // Re-link only days whose events really changed (old and new days of a moved event)
                        if (smartLinkFieldsChanged(existingEvent, calendarEvent)) {
                            if (existingEvent != null) {
                                changedEvents.add(existingEvent)
                            }
                            changedEvents.add(calendarEvent)
                        }

                        if (existingEvent != null) {
                            updatedEventsCount++
                        } else {
                            newEventsCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting event: ${event.summary}", e)
                }
            }

            val missingEvents = existingEvents.filter { existing ->
                existing.calendarId in successfulCalendarIds && existing.id !in fetchedEventIds
            }
            if (missingEvents.isNotEmpty()) {
                eventRepository.removeEvents(
                    eventIds = missingEvents.map { it.id },
                    reason = "legacy Google sync removed missing events"
                )
            }

            refreshSmartLinksForEvents(changedEvents, "legacy Google sync refreshed events")

            Log.d(TAG, "Sync complete: $newEventsCount new, $updatedEventsCount updated, ${missingEvents.size} removed")

            val result = buildCalendarFetchSyncResult(
                successfulCalendarCount = calendarList.size - fetchFailures.size,
                fetchFailures = fetchFailures,
                newEventsCount = newEventsCount,
                updatedEventsCount = updatedEventsCount
            )
            cleanupDeletedCalendars(
                deletedCalendarIds = result.deletedCalendarIds(),
                accountId = accountId,
                selectedCalendars = selectedCalendars,
                updateLegacySelection = accountId.isEmpty()
            )
            if (result is SyncResult.Success) {
                authManager.setLastSyncTime(System.currentTimeMillis())
            }
            result

        } catch (e: Exception) {
            val errorType = e.toSyncErrorType()
            when (errorType) {
                SyncErrorType.NETWORK -> {
                    Log.w(TAG, "Sync blocked by no internet connection")
                    SyncResult.NoNetwork()
                }
                else -> SyncResult.Error(
                    message = e.message ?: "Unknown error",
                    exception = e,
                    errorType = errorType
                ).also {
                    Log.e(TAG, "Sync failed", e)
                }
            }
        }
    }

    /**
     * Sync events for current month
     */
    suspend fun syncCurrentMonth(): SyncResult {
        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())
        return syncEvents(startOfMonth, endOfMonth)
    }

    /**
     * Sync events for a specific month
     */
    suspend fun syncMonth(year: Int, month: Int): SyncResult {
        val startOfMonth = LocalDate.of(year, month, 1)
        val endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth())
        return syncEvents(startOfMonth, endOfMonth)
    }

    /**
     * Sync events for a range of months centered around the current month.
     * @param monthsBefore Number of months to sync before current month (default 3)
     * @param monthsAfter Number of months to sync after current month (default 3)
     * @return Map of month keys ("yyyy-MM") to their SyncResult
     */
    suspend fun syncMonthRange(
        monthsBefore: Int = 3,
        monthsAfter: Int = 3
    ): Map<String, SyncResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, SyncResult>()
        val now = LocalDate.now()

        for (offset in -monthsBefore..monthsAfter) {
            val targetMonth = now.plusMonths(offset.toLong())
            val key = String.format("%04d-%02d", targetMonth.year, targetMonth.monthValue)

            Log.d(TAG, "Syncing month: $key (offset: $offset)")

            val result = syncMonth(targetMonth.year, targetMonth.monthValue)
            results[key] = result

            // Stop syncing if auth or other fatal issues occur
            if (result is SyncResult.NotSignedIn || result is SyncResult.Error || result is SyncResult.NoNetwork) {
                Log.w(TAG, "Error during range sync for $key, stopping early")
                break
            }
        }

        Log.d(TAG, "Range sync complete: ${results.size} months synced")
        results
    }

    /**
     * Get all available calendars from Google
     * @param credential Optional per-account credential for multi-account support
     */
    suspend fun getAvailableCalendarsResult(
        credential: GoogleAccountCredential? = null
    ): Result<List<GoogleCalendarInfo>> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService(credential)
                ?: return@withContext Result.failure(IllegalStateException("Not signed in"))

            val calendarList = service.calendarList().list().execute()
            Result.success(calendarList.items?.map { GoogleCalendarInfo.fromCalendarListEntry(it) } ?: emptyList())
        } catch (e: Exception) {
            when (e.toSyncErrorType()) {
                SyncErrorType.NETWORK -> Log.w(TAG, "Calendar list fetch skipped: no internet connection")
                else -> Log.e(TAG, "Error fetching calendars", e)
            }
            Result.failure(e)
        }
    }

    suspend fun getAvailableCalendars(
        credential: GoogleAccountCredential? = null
    ): List<GoogleCalendarInfo> {
        return getAvailableCalendarsResult(credential).getOrElse { emptyList() }
    }

    /**
     * Get only writable calendars (for event creation)
     */
    suspend fun getWritableCalendars(): List<GoogleCalendarInfo> {
        return getAvailableCalendars().filter { it.isWritable }
    }

    // ========== CREATE OPERATION ==========

    /**
     * Create a new event on Google Calendar
     */
    suspend fun createEvent(data: NewEventData): EventOperationResult<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService()
                ?: return@withContext EventOperationResult.NotSignedIn

            // Build the event
            val event = Event().apply {
                summary = data.title
                location = data.location
                description = data.description

                // Set attendees if present (comma-separated emails)
                data.attendees?.let { attendeesStr ->
                    if (attendeesStr.isNotBlank()) {
                        attendees = attendeesStr.split(",")
                            .filter { it.isNotBlank() }
                            .map { email -> EventAttendee().setEmail(email.trim()) }
                    }
                }

                if (data.isAllDay) {
                    // All-day event uses date only
                    val startDate = LocalDate.parse(data.date)
                    start = EventDateTime().setDate(DateTime(startDate.toString()))
                    end = EventDateTime().setDate(DateTime(startDate.plusDays(1).toString()))
                } else {
                    // Timed event
                    val startDateTime = LocalDateTime.of(
                        LocalDate.parse(data.date),
                        LocalTime.of(data.startTimeHour, data.startTimeMinute)
                    )
                    val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    start = EventDateTime().setDateTime(DateTime(startMillis))

                    // End time (default to 1 hour after start if not specified)
                    val endDateTime = if (data.endTimeHour != null && data.endTimeMinute != null) {
                        LocalDateTime.of(
                            LocalDate.parse(data.date),
                            LocalTime.of(data.endTimeHour, data.endTimeMinute)
                        )
                    } else {
                        startDateTime.plusHours(1)
                    }
                    val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    end = EventDateTime().setDateTime(DateTime(endMillis))
                }
            }

            Log.d(TAG, "Creating event: ${data.title} on ${data.calendarId}")

            // Insert event to Google Calendar
            val createdEvent = service.events().insert(data.calendarId, event).execute()

            // Get calendar info for caching
            val calendarEntry = service.calendarList().get(data.calendarId).execute()
            val calendarInfo = GoogleCalendarInfo.fromCalendarListEntry(calendarEntry)

            // Convert to local entity and cache
            val calendarEvent = createdEvent.toCalendarEvent(calendarInfo)
                ?: return@withContext EventOperationResult.Error("Failed to parse created event")

            eventRepository.cacheEvent(calendarEvent)
            refreshSmartLinksForEvents(listOf(calendarEvent), "legacy Google event created")

            Log.i(TAG, "Event created successfully: ${createdEvent.id}")
            EventOperationResult.Success(calendarEvent)

        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Log.e(TAG, "Google API error creating event", e)
            if (e.statusCode == 403) {
                EventOperationResult.PermissionDenied
            } else {
                EventOperationResult.Error(e.details?.message ?: e.message ?: "API error", e)
            }
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Create event skipped: no internet connection")
            EventOperationResult.NoNetwork
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            EventOperationResult.Error(e.message ?: "Unknown error", e)
        }
    }

    // ========== UPDATE OPERATION ==========

    /**
     * Update an existing event on Google Calendar
     */
    suspend fun updateEvent(data: UpdateEventData): EventOperationResult<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService()
                ?: return@withContext EventOperationResult.NotSignedIn

            // Fetch the existing event first
            val existingEvent = service.events().get(data.calendarId, data.eventId).execute()
            val previousCachedEvent = eventRepository.getEventById(data.eventId)

            // Update fields
            existingEvent.apply {
                summary = data.title
                location = data.location
                description = data.description

                // Update attendees if present (comma-separated emails)
                data.attendees?.let { attendeesStr ->
                    if (attendeesStr.isNotBlank()) {
                        attendees = attendeesStr.split(",")
                            .filter { it.isNotBlank() }
                            .map { email -> EventAttendee().setEmail(email.trim()) }
                    } else {
                        attendees = null  // Clear attendees if empty string
                    }
                }

                if (data.isAllDay) {
                    val startDate = LocalDate.parse(data.date)
                    start = EventDateTime().setDate(DateTime(startDate.toString()))
                    end = EventDateTime().setDate(DateTime(startDate.plusDays(1).toString()))
                } else {
                    val startDateTime = LocalDateTime.of(
                        LocalDate.parse(data.date),
                        LocalTime.of(data.startTimeHour, data.startTimeMinute)
                    )
                    val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    start = EventDateTime().setDateTime(DateTime(startMillis))

                    val endDateTime = if (data.endTimeHour != null && data.endTimeMinute != null) {
                        LocalDateTime.of(
                            LocalDate.parse(data.date),
                            LocalTime.of(data.endTimeHour, data.endTimeMinute)
                        )
                    } else {
                        startDateTime.plusHours(1)
                    }
                    val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    end = EventDateTime().setDateTime(DateTime(endMillis))
                }
            }

            Log.d(TAG, "Updating event: ${data.eventId}")

            // Update on Google Calendar
            val updatedEvent = service.events().update(data.calendarId, data.eventId, existingEvent).execute()

            // Get calendar info for caching
            val calendarEntry = service.calendarList().get(data.calendarId).execute()
            val calendarInfo = GoogleCalendarInfo.fromCalendarListEntry(calendarEntry)

            // Update local cache
            val calendarEvent = updatedEvent.toCalendarEvent(calendarInfo)
                ?: return@withContext EventOperationResult.Error("Failed to parse updated event")

            eventRepository.cacheEvent(calendarEvent)
            refreshSmartLinksForEvents(
                listOfNotNull(previousCachedEvent, calendarEvent),
                "legacy Google event updated"
            )

            Log.i(TAG, "Event updated successfully: ${data.eventId}")
            EventOperationResult.Success(calendarEvent)

        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Log.e(TAG, "Google API error updating event", e)
            if (e.statusCode == 403) {
                EventOperationResult.PermissionDenied
            } else {
                EventOperationResult.Error(e.details?.message ?: e.message ?: "API error", e)
            }
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Update event skipped: no internet connection")
            EventOperationResult.NoNetwork
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event", e)
            EventOperationResult.Error(e.message ?: "Unknown error", e)
        }
    }

    // ========== DELETE OPERATION ==========

    /**
     * Delete an event from Google Calendar
     */
    suspend fun deleteEvent(eventId: String, calendarId: String): EventOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService()
                ?: return@withContext EventOperationResult.NotSignedIn

            Log.d(TAG, "Deleting event: $eventId from calendar: $calendarId")

            // Delete from Google Calendar
            service.events().delete(calendarId, eventId).execute()

            // Remove from local cache
            eventRepository.removeEvent(eventId)

            Log.i(TAG, "Event deleted successfully: $eventId")
            EventOperationResult.Success(Unit)

        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Log.e(TAG, "Google API error deleting event", e)
            if (e.statusCode == 403) {
                EventOperationResult.PermissionDenied
            } else if (e.statusCode == 404 || e.statusCode == 410) {
                // Event already deleted, remove from cache anyway
                eventRepository.removeEvent(eventId)
                EventOperationResult.Success(Unit)
            } else {
                EventOperationResult.Error(e.details?.message ?: e.message ?: "API error", e)
            }
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Delete event skipped: no internet connection")
            EventOperationResult.NoNetwork
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event", e)
            EventOperationResult.Error(e.message ?: "Unknown error", e)
        }
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Clear all synced events from local cache
     */
    suspend fun clearSyncedEvents() = withContext(Dispatchers.IO) {
        eventRepository.clearCache()
        Log.d(TAG, "Cleared all synced events from cache")
    }

    /**
     * Remove events from a specific calendar in local cache
     */
    suspend fun removeCalendarEventsFromCache(calendarId: String) = withContext(Dispatchers.IO) {
        eventRepository.removeCalendarEvents(calendarId)
        Log.d(TAG, "Removed events from cache for calendar: $calendarId")
    }

    // ========== UTILITY ==========

    /**
     * Convert Google Calendar Event to local CalendarEvent entity
     * @param accountId Account ID for multi-account support
     * @param accountEmail Account email for display
     * @param providerId Provider identifier ("GOOGLE")
     */
    private fun Event.toCalendarEvent(
        calendarInfo: GoogleCalendarInfo,
        accountId: String = "",
        accountEmail: String = "",
        providerId: String = "GOOGLE"
    ): CalendarEvent? {
        val timing = extractGoogleEventTiming() ?: return null

        val attendeesList = this.attendees?.mapNotNull { it.email }?.joinToString(",")

        return CalendarEvent(
            id = this.id,
            providerId = providerId,
            accountId = accountId,
            accountEmail = accountEmail,
            calendarId = calendarInfo.id,
            calendarName = calendarInfo.name,
            title = this.summary ?: "Untitled Event",
            dayId = timing.dayId,
            endDayId = timing.endDayId,
            startTime = timing.startTimeMillis,
            endTime = timing.endTimeMillis,
            isAllDay = timing.isAllDay,
            location = this.location,
            description = this.description,
            attendees = attendeesList,
            isReadOnly = !calendarInfo.isWritable,
            colorHex = calendarInfo.backgroundColor,
            syncedAt = System.currentTimeMillis()
        )
    }
}
