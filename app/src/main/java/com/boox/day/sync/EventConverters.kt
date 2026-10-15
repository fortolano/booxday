package com.boox.day.sync

import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.SyncStatus
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Extension functions for converting between LocalEvent, CalendarEvent, and Google Calendar API Event.
 *
 * This file contains all type conversion logic for the hybrid calendar system:
 * - Google API Event → LocalEvent (pulling from Google)
 * - LocalEvent → Google API Event (pushing to Google)
 * - CalendarEvent → LocalEvent (migration from v10 to v11)
 * - Conflict detection helpers
 */

internal data class GoogleEventTiming(
    val isAllDay: Boolean,
    val dayId: String,
    val endDayId: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long?
)

private fun systemZoneId(): ZoneId = ZoneId.systemDefault()

private fun LocalDate.toSystemStartOfDayMillis(): Long {
    return atStartOfDay(systemZoneId()).toInstant().toEpochMilli()
}

private fun LocalDate.toGoogleDateTime(): com.google.api.client.util.DateTime {
    return com.google.api.client.util.DateTime(format(DateTimeFormatter.ISO_LOCAL_DATE))
}

private fun com.google.api.client.util.DateTime.toGoogleLocalDate(): LocalDate {
    return LocalDate.parse(toStringRfc3339())
}

internal fun Event.extractGoogleEventTiming(): GoogleEventTiming? {
    val startDateTime = this.start?.dateTime
    val startDateOnly = this.start?.date
    val endDateTime = this.end?.dateTime
    val endDateOnly = this.end?.date

    return if (startDateOnly != null) {
        val startDate = startDateOnly.toGoogleLocalDate()
        val endExclusiveDate = endDateOnly?.toGoogleLocalDate() ?: startDate.plusDays(1)
        val inclusiveEndDate = if (endExclusiveDate.isAfter(startDate)) {
            endExclusiveDate.minusDays(1)
        } else {
            startDate
        }

        GoogleEventTiming(
            isAllDay = true,
            dayId = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDayId = inclusiveEndDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            startTimeMillis = startDate.toSystemStartOfDayMillis(),
            endTimeMillis = endExclusiveDate.toSystemStartOfDayMillis()
        )
    } else {
        val startMillis = startDateTime?.value ?: return null
        val startDate = Instant.ofEpochMilli(startMillis).atZone(systemZoneId()).toLocalDate()
        val endMillis = endDateTime?.value ?: endDateOnly?.value
        val endDayId = if (endMillis != null) {
            val endDate = Instant.ofEpochMilli(endMillis).atZone(systemZoneId()).toLocalDate()
            val finalEnd = if (endDate.isBefore(startDate)) startDate else endDate
            finalEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }

        GoogleEventTiming(
            isAllDay = false,
            dayId = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDayId = endDayId,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis
        )
    }
}

// =============================================================================
// Google API Event → LocalEvent
// =============================================================================

/**
 * Convert Google Calendar API Event to LocalEvent.
 * Used when pulling events from Google Calendar.
 *
 * @param calendarInfo The calendar info from which this event was fetched
 * @param existingLocalEvent Optional existing local event to preserve local-only fields (id, linkedMemoPageId, createdAt)
 * @return LocalEvent or null if the event has no valid start time
 */
fun Event.toLocalEvent(
    calendarInfo: GoogleCalendarInfo,
    existingLocalEvent: LocalEvent? = null
): LocalEvent? {
    val timing = extractGoogleEventTiming() ?: return null

    val now = System.currentTimeMillis()

    // Extract attendees as comma-separated emails
    val attendeesList = this.attendees?.mapNotNull { it.email }?.joinToString(",")

    return LocalEvent(
        // Preserve local ID if updating, otherwise generate new
        id = existingLocalEvent?.id ?: UUID.randomUUID().toString(),

        // Core event data
        title = this.summary ?: "Untitled Event",
        dayId = timing.dayId,
        endDayId = timing.endDayId,
        startTime = timing.startTimeMillis,
        endTime = timing.endTimeMillis,
        isAllDay = timing.isAllDay,
        location = this.location,
        description = this.description,
        colorHex = calendarInfo.backgroundColor,
        attendees = attendeesList,

        // Sync metadata - always synced when coming from Google
        syncEnabled = true,
        syncStatus = SyncStatus.SYNCED,
        googleEventId = this.id,
        googleCalendarId = calendarInfo.id,
        googleCalendarName = calendarInfo.name,
        lastSyncedAt = now,
        serverEtag = this.etag,

        // Local metadata
        isReadOnly = !calendarInfo.isWritable,
        createdAt = existingLocalEvent?.createdAt ?: now,
        updatedAt = now,

        // Preserve linked memo if updating
        linkedMemoPageId = existingLocalEvent?.linkedMemoPageId
    )
}

// =============================================================================
// LocalEvent → Google API Event
// =============================================================================

/**
 * Convert LocalEvent to Google Calendar API Event.
 * Used when pushing local events to Google Calendar.
 *
 * @return Google Calendar API Event ready for insert/update
 */
fun LocalEvent.toGoogleEvent(): Event {
    return Event().apply {
        summary = this@toGoogleEvent.title
        location = this@toGoogleEvent.location
        description = this@toGoogleEvent.description

        // Set start time
        start = if (this@toGoogleEvent.isAllDay) {
            val startDate = LocalDate.parse(this@toGoogleEvent.dayId)
            EventDateTime().setDate(startDate.toGoogleDateTime())
        } else {
            // Timed event: use datetime with timezone
            EventDateTime()
                .setDateTime(com.google.api.client.util.DateTime(this@toGoogleEvent.startTime))
                .setTimeZone(ZoneId.systemDefault().id)
        }

        // Set end time
        end = if (this@toGoogleEvent.isAllDay) {
            val inclusiveEndDate = LocalDate.parse(this@toGoogleEvent.endDayId.ifBlank { this@toGoogleEvent.dayId })
            val endExclusiveDate = if (inclusiveEndDate.isBefore(LocalDate.parse(this@toGoogleEvent.dayId))) {
                LocalDate.parse(this@toGoogleEvent.dayId).plusDays(1)
            } else {
                inclusiveEndDate.plusDays(1)
            }
            EventDateTime().setDate(endExclusiveDate.toGoogleDateTime())
        } else {
            // Timed event: default to 1 hour duration if no end time
            val endMillis = this@toGoogleEvent.endTime
                ?: (this@toGoogleEvent.startTime + 60 * 60 * 1000)
            EventDateTime()
                .setDateTime(com.google.api.client.util.DateTime(endMillis))
                .setTimeZone(ZoneId.systemDefault().id)
        }

        // Set attendees if present
        this@toGoogleEvent.attendees?.let { attendeesStr ->
            if (attendeesStr.isNotBlank()) {
                attendees = attendeesStr.split(",")
                    .filter { it.isNotBlank() }
                    .map { email -> EventAttendee().setEmail(email.trim()) }
            }
        }
    }
}

// =============================================================================
// Merge Remote Event into Local Event
// =============================================================================

/**
 * Merge remote Google Event data into existing LocalEvent.
 * Preserves local-only fields (id, linkedMemoPageId, createdAt) while updating synced data.
 *
 * @param remoteEvent The Google Calendar API Event with updated data
 * @param calendarInfo The calendar info for color and permission data
 * @return Updated LocalEvent with merged data
 */
fun LocalEvent.mergeWithRemote(remoteEvent: Event, calendarInfo: GoogleCalendarInfo): LocalEvent {
    val timing = remoteEvent.extractGoogleEventTiming() ?: return this

    val attendeesList = remoteEvent.attendees?.mapNotNull { it.email }?.joinToString(",")
    val now = System.currentTimeMillis()

    return this.copy(
        // Update from remote
        title = remoteEvent.summary ?: "Untitled Event",
        dayId = timing.dayId,
        endDayId = timing.endDayId,
        startTime = timing.startTimeMillis,
        endTime = timing.endTimeMillis,
        isAllDay = timing.isAllDay,
        location = remoteEvent.location,
        description = remoteEvent.description,
        colorHex = calendarInfo.backgroundColor,
        attendees = attendeesList,

        // Update sync metadata
        googleCalendarId = calendarInfo.id,
        googleCalendarName = calendarInfo.name,
        serverEtag = remoteEvent.etag,
        lastSyncedAt = now,
        updatedAt = now,
        syncStatus = SyncStatus.SYNCED,
        isReadOnly = !calendarInfo.isWritable

        // Preserved fields: id, linkedMemoPageId, createdAt, syncEnabled
    )
}

// =============================================================================
// CalendarEvent → LocalEvent (Migration)
// =============================================================================

/**
 * Convert legacy CalendarEvent to LocalEvent.
 * Used during database migration from v10 to v11.
 *
 * Note: The actual migration in MIGRATION_10_11 uses SQL for efficiency,
 * but this function is useful for programmatic conversion if needed.
 *
 * @return LocalEvent with sync enabled and status SYNCED
 */
fun CalendarEvent.toLocalEvent(): LocalEvent {
    return LocalEvent(
        // Generate new local UUID (Google ID stored separately)
        id = UUID.randomUUID().toString(),

        // Core event data
        title = this.title,
        dayId = this.dayId,
        endDayId = this.endDayId.ifEmpty { this.dayId },
        startTime = this.startTime,
        endTime = this.endTime,
        isAllDay = this.isAllDay,
        location = this.location,
        description = this.description,
        colorHex = this.colorHex,
        attendees = this.attendees,

        // Sync metadata - came from Google, so mark as synced
        syncEnabled = true,
        syncStatus = SyncStatus.SYNCED,
        googleEventId = this.id,  // Original Google ID
        googleCalendarId = this.calendarId,
        googleCalendarName = this.calendarName,
        lastSyncedAt = this.syncedAt,
        serverEtag = null,  // Not available in CalendarEvent

        // Local metadata
        isReadOnly = this.isReadOnly,
        createdAt = this.syncedAt,
        updatedAt = this.syncedAt,
        linkedMemoPageId = null
    )
}

// =============================================================================
// Conflict Detection
// =============================================================================

/**
 * Check if local event has diverged from server version.
 * Used for conflict detection during sync.
 *
 * @param remoteEvent The Google Calendar API Event to compare against
 * @return true if there's a potential conflict, false if safe to sync
 */
fun LocalEvent.hasConflictWith(remoteEvent: Event): Boolean {
    // No conflict if we don't have pending local changes
    if (this.syncStatus !in listOf(SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE)) {
        return false
    }

    // Compare ETags if available (most reliable)
    val remoteEtag = remoteEvent.etag
    if (this.serverEtag != null && remoteEtag != null) {
        if (this.serverEtag != remoteEtag) {
            return true  // Server version changed since our last sync
        }
    }

    // Compare timestamps if ETags not available
    val remoteUpdated = remoteEvent.updated?.value
    if (remoteUpdated != null) {
        val localLastSync = this.lastSyncedAt
        if (localLastSync == null) {
            return true  // No sync record, assume potential conflict
        }
        if (remoteUpdated > localLastSync) {
            return true  // Server was updated after our last sync
        }
    }

    return false
}

/**
 * Detect which fields changed locally (compared to synced state).
 * Used for conflict resolution UI to show what the user changed.
 *
 * Note: In a full implementation, you'd compare against a stored "base" version.
 * This simplified version assumes any non-default field was potentially changed.
 *
 * @return Set of field names that have local values
 */
fun LocalEvent.getLocallyChangedFields(): Set<String> {
    return buildSet {
        add("title")  // Title is always considered potentially changed
        if (!location.isNullOrBlank()) add("location")
        if (!description.isNullOrBlank()) add("description")
        add("time")  // Time is always considered potentially changed
        if (!attendees.isNullOrBlank()) add("attendees")
    }
}

/**
 * Detect which fields changed remotely (compared to local state).
 * Used for conflict resolution UI to show what changed on Google Calendar.
 *
 * @param remoteEvent The Google Calendar API Event to compare against
 * @return Set of field names that differ from local
 */
fun LocalEvent.getRemotelyChangedFields(remoteEvent: Event): Set<String> {
    return buildSet {
        val remoteTitle = remoteEvent.summary ?: ""
        if (title != remoteTitle) add("title")

        if (location != remoteEvent.location) add("location")
        if (description != remoteEvent.description) add("description")

        val hasRemoteTiming = remoteEvent.start?.dateTime != null || remoteEvent.start?.date != null
        if (hasRemoteTiming) {
            val remoteTiming = remoteEvent.extractGoogleEventTiming()
            if (
                remoteTiming == null ||
                dayId != remoteTiming.dayId ||
                endDayId.ifBlank { dayId } != remoteTiming.endDayId ||
                startTime != remoteTiming.startTimeMillis ||
                endTime != remoteTiming.endTimeMillis ||
                isAllDay != remoteTiming.isAllDay
            ) {
                add("time")
            }
        }

        // Compare attendees
        val remoteAttendees = remoteEvent.attendees?.mapNotNull { it.email }?.joinToString(",")
        if (attendees != remoteAttendees) add("attendees")
    }
}

// =============================================================================
// Helper Extensions
// =============================================================================

/**
 * Check if two events can be auto-merged (non-overlapping changes).
 *
 * @param remoteEvent The Google Calendar API Event to check
 * @return true if changes don't overlap and auto-merge is safe
 */
fun LocalEvent.canAutoMergeWith(remoteEvent: Event): Boolean {
    val localChanges = getLocallyChangedFields()
    val remoteChanges = getRemotelyChangedFields(remoteEvent)
    return localChanges.intersect(remoteChanges).isEmpty()
}

/**
 * Create a display-friendly summary of sync status.
 *
 * @return Human-readable sync status string
 */
fun LocalEvent.getSyncStatusDisplay(): String {
    return when {
        !syncEnabled -> "Local only"
        syncStatus == SyncStatus.SYNCED -> "Synced"
        syncStatus == SyncStatus.PENDING_CREATE -> "Pending upload"
        syncStatus == SyncStatus.PENDING_UPDATE -> "Pending update"
        syncStatus == SyncStatus.PENDING_DELETE -> "Pending deletion"
        syncStatus == SyncStatus.CONFLICT -> "Conflict"
        syncStatus == SyncStatus.LOCAL_ONLY -> "Local only"
        else -> "Unknown"
    }
}
