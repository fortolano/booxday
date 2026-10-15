package com.boox.day.sync

import com.google.api.services.calendar.model.CalendarListEntry

/**
 * Represents a Google Calendar with its metadata.
 * Used for calendar selection and permission checking.
 */
data class GoogleCalendarInfo(
    val id: String,
    val name: String,
    val isPrimary: Boolean,
    val accessRole: String,      // "owner", "writer", "reader", "freeBusyReader"
    val isWritable: Boolean,     // Derived from accessRole
    val backgroundColor: String? = null,
    val description: String? = null
) {
    companion object {
        // Access roles that allow writing
        private val WRITABLE_ROLES = setOf("owner", "writer")

        /**
         * Create from Google Calendar API CalendarListEntry
         */
        fun fromCalendarListEntry(entry: CalendarListEntry): GoogleCalendarInfo {
            val accessRole = entry.accessRole ?: "reader"
            return GoogleCalendarInfo(
                id = entry.id,
                name = entry.summary ?: entry.id,
                isPrimary = entry.primary == true,
                accessRole = accessRole,
                isWritable = accessRole in WRITABLE_ROLES,
                backgroundColor = entry.backgroundColor,
                description = entry.description
            )
        }
    }

    /**
     * User-friendly access role description
     */
    fun getAccessRoleDisplay(): String {
        return when (accessRole) {
            "owner" -> "Owner"
            "writer" -> "Can edit"
            "reader" -> "View only"
            "freeBusyReader" -> "Free/busy only"
            else -> accessRole
        }
    }
}

/**
 * Result of a sync operation.
 * Extended for standalone/hybrid mode with more specific error types.
 */
sealed class SyncResult {
    /**
     * Sync completed successfully
     */
    data class Success(
        val eventCount: Int,
        val newEvents: Int,
        val updatedEvents: Int,
        val deletedEvents: Int = 0,
        val conflictCount: Int = 0,
        val deletedCalendarIds: List<String> = emptyList()
    ) : SyncResult() {
        fun toDisplayMessage(): String {
            return when {
                conflictCount > 0 -> "Synced with $conflictCount conflict(s) to resolve"
                newEvents == 0 && updatedEvents == 0 -> "Events up to date ($eventCount total)"
                newEvents > 0 && updatedEvents > 0 -> "Synced: $newEvents new, $updatedEvents updated"
                newEvents > 0 -> "Synced: $newEvents new events"
                updatedEvents > 0 -> "Updated: $updatedEvents events"
                else -> "Synced $eventCount events"
            }
        }

        val hasConflicts: Boolean get() = conflictCount > 0
    }

    /**
     * Sync failed with an error
     */
    data class Error(
        val message: String,
        val exception: Exception? = null,
        val errorType: SyncErrorType = SyncErrorType.UNKNOWN,
        val isRecoverable: Boolean = true,
        val deletedCalendarIds: List<String> = emptyList()
    ) : SyncResult() {
        fun toDisplayMessage(): String = message
    }

    /**
     * No sync performed (e.g., not signed in)
     */
    data class NotSignedIn(
        val message: String = "Sign in to sync events"
    ) : SyncResult()

    /**
     * Sync in progress
     */
    object InProgress : SyncResult()

    /**
     * Partial success - some events synced, some failed
     */
    data class PartialSuccess(
        val successCount: Int,
        val failureCount: Int,
        val errors: List<String>,
        val deletedCalendarIds: List<String> = emptyList()
    ) : SyncResult() {
        fun toDisplayMessage(): String = "Synced $successCount, $failureCount failed"
    }

    /**
     * No network connection
     */
    data class NoNetwork(
        val pendingChanges: Int = 0,
        val deletedCalendarIds: List<String> = emptyList()
    ) : SyncResult() {
        fun toDisplayMessage(): String = if (pendingChanges > 0) {
            "Offline - $pendingChanges change(s) pending"
        } else {
            "No internet connection"
        }
    }

    // Helper extension for all types
    fun getDisplayMessage(): String = when (this) {
        is Success -> toDisplayMessage()
        is Error -> toDisplayMessage()
        is NotSignedIn -> message
        is InProgress -> "Syncing..."
        is PartialSuccess -> toDisplayMessage()
        is NoNetwork -> toDisplayMessage()
    }

    fun isError(): Boolean = this is Error || this is NoNetwork || this is PartialSuccess
    fun isSuccess(): Boolean = this is Success
}

fun SyncResult.deletedCalendarIds(): List<String> = when (this) {
    is SyncResult.Success -> deletedCalendarIds
    is SyncResult.Error -> deletedCalendarIds
    is SyncResult.PartialSuccess -> deletedCalendarIds
    is SyncResult.NoNetwork -> deletedCalendarIds
    else -> emptyList()
}

/**
 * Types of sync errors for categorization and handling.
 */
enum class SyncErrorType {
    NETWORK,            // Network connectivity issues
    AUTHENTICATION,     // Auth token expired or invalid
    PERMISSION,         // Calendar permission denied
    QUOTA,              // API quota exceeded
    CONFLICT,           // Sync conflict detected
    SERVER,             // Google server error (5xx)
    CLIENT,             // Client-side error (invalid data)
    NOT_FOUND,          // Calendar deleted or unsubscribed outside the app
    UNKNOWN             // Unknown error
}

/**
 * Get error type from exception for proper error handling.
 */
fun Exception.toSyncErrorType(): SyncErrorType {
    return when {
        this is java.net.UnknownHostException -> SyncErrorType.NETWORK
        this is java.net.SocketTimeoutException -> SyncErrorType.NETWORK
        this is java.io.IOException && message?.contains("network", ignoreCase = true) == true -> SyncErrorType.NETWORK
        this.javaClass.name.contains("GoogleJsonResponseException") -> {
            // Parse Google API errors
            val message = this.message ?: ""
            when {
                message.contains("401") || message.contains("403") -> SyncErrorType.AUTHENTICATION
                message.contains("404") -> SyncErrorType.NOT_FOUND
                message.contains("429") -> SyncErrorType.QUOTA
                message.contains("5") -> SyncErrorType.SERVER
                else -> SyncErrorType.UNKNOWN
            }
        }
        else -> SyncErrorType.UNKNOWN
    }
}

/**
 * Result of a CRUD operation on Google Calendar
 */
sealed class EventOperationResult<out T> {
    data class Success<T>(val data: T) : EventOperationResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : EventOperationResult<Nothing>()
    object NotSignedIn : EventOperationResult<Nothing>()
    object NoNetwork : EventOperationResult<Nothing>()
    object PermissionDenied : EventOperationResult<Nothing>()
}

/**
 * Data class for creating a new event
 */
data class NewEventData(
    val calendarId: String,
    val title: String,
    val date: String,           // "yyyy-MM-dd"
    val startTimeHour: Int,
    val startTimeMinute: Int,
    val endTimeHour: Int? = null,
    val endTimeMinute: Int? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val attendees: String? = null,  // Comma-separated emails for Google Calendar invites
    val syncEnabled: Boolean = false  // Whether to sync this event to Google Calendar (hybrid mode)
)

/**
 * Data class for updating an existing event
 */
data class UpdateEventData(
    val eventId: String,
    val calendarId: String,
    val title: String,
    val date: String,           // "yyyy-MM-dd"
    val startTimeHour: Int,
    val startTimeMinute: Int,
    val endTimeHour: Int? = null,
    val endTimeMinute: Int? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val attendees: String? = null,  // Comma-separated emails for Google Calendar invites
    val syncEnabled: Boolean = false  // Whether to sync this event to Google Calendar (hybrid mode)
)
