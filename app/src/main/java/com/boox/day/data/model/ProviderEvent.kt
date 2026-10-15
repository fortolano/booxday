package com.boox.day.data.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents an event as returned from a provider's API.
 * This is a provider-agnostic model that both Google and Outlook responses
 * are converted into before being stored as CalendarEvent entities.
 *
 * @property id Provider's unique identifier for this event
 * @property calendarId Provider's calendar ID this event belongs to
 * @property title Event title/subject
 * @property startTime Event start time in epoch milliseconds
 * @property endTime Event end time in epoch milliseconds (null for all-day events)
 * @property isAllDay Whether this is an all-day event
 * @property location Event location (optional)
 * @property description Event description/body (optional)
 * @property attendees Attendee information as JSON or comma-separated string
 * @property colorHex Event color in hex format (optional)
 * @property isReadOnly Whether user can only view (not edit) this event
 */
data class ProviderEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val attendees: String? = null,
    val colorHex: String? = null,
    val isReadOnly: Boolean = false
) {
    /**
     * Get the day ID (yyyy-MM-dd format) for this event.
     * Used for database queries and calendar display.
     */
    val dayId: String
        get() {
            val instant = java.time.Instant.ofEpochMilli(startTime)
            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            return localDate.format(DATE_FORMATTER)
        }

    /**
     * Get the LocalDate for this event's start time.
     */
    val startDate: LocalDate
        get() {
            val instant = java.time.Instant.ofEpochMilli(startTime)
            return instant.atZone(ZoneId.systemDefault()).toLocalDate()
        }

    /**
     * Get the end day ID (yyyy-MM-dd format, inclusive) for multi-day event support.
     * For all-day events, provider end dates are EXCLUSIVE (e.g., Mon-Fri ends Saturday),
     * so we subtract 1 day. For timed events, use the end time's date directly.
     * Falls back to dayId if no end time.
     */
    val endDayId: String
        get() {
            if (endTime == null) return dayId
            val endDate = java.time.Instant.ofEpochMilli(endTime).atZone(ZoneId.systemDefault()).toLocalDate()
            val inclusiveEnd = if (isAllDay) endDate.minusDays(1) else endDate
            val finalEnd = if (inclusiveEnd.isBefore(startDate)) startDate else inclusiveEnd
            return finalEnd.format(DATE_FORMATTER)
        }

    /**
     * Check if this event has a valid title.
     */
    val hasTitle: Boolean
        get() = title.isNotBlank()

    /**
     * Get a display-friendly title (or placeholder if empty).
     */
    val displayTitle: String
        get() = title.ifBlank { "(No title)" }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /**
         * Create from Google Calendar API event data.
         */
        fun fromGoogleEvent(
            id: String,
            calendarId: String,
            title: String?,
            startTime: Long,
            endTime: Long?,
            isAllDay: Boolean,
            location: String?,
            description: String?,
            attendees: String?,
            colorHex: String?,
            accessRole: String?
        ): ProviderEvent = ProviderEvent(
            id = id,
            calendarId = calendarId,
            title = title ?: "",
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location,
            description = description,
            attendees = attendees,
            colorHex = colorHex,
            isReadOnly = accessRole != null && accessRole !in setOf("owner", "writer")
        )

        /**
         * Create from Outlook/Graph API event data.
         */
        fun fromOutlookEvent(
            id: String,
            calendarId: String,
            subject: String?,
            startTime: Long,
            endTime: Long?,
            isAllDay: Boolean,
            locationDisplayName: String?,
            bodyPreview: String?,
            attendees: String?,
            canEdit: Boolean
        ): ProviderEvent = ProviderEvent(
            id = id,
            calendarId = calendarId,
            title = subject ?: "",
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = locationDisplayName,
            description = bodyPreview,
            attendees = attendees,
            colorHex = null, // Outlook events use category colors differently
            isReadOnly = !canEdit
        )
    }
}
