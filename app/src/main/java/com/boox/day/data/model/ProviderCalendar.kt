package com.boox.day.data.model

/**
 * Represents a calendar as returned from a provider's API.
 * This is a provider-agnostic model that both Google and Outlook responses
 * are converted into.
 *
 * @property id Provider's unique identifier for this calendar
 * @property name Display name of the calendar
 * @property color Calendar color (hex format, e.g., "#4285F4")
 * @property isPrimary Whether this is the user's primary calendar
 * @property isWritable Whether the user can create/edit events in this calendar
 * @property accessRole Provider-specific access role (for display/debugging)
 * @property description Optional description of the calendar
 */
data class ProviderCalendar(
    val id: String,
    val name: String,
    val color: String? = null,
    val isPrimary: Boolean = false,
    val isWritable: Boolean = true,
    val accessRole: String? = null,
    val description: String? = null
) {
    /**
     * Get a user-friendly access role description.
     */
    fun getAccessRoleDisplay(): String = when {
        accessRole == null -> if (isWritable) "Can edit" else "View only"
        accessRole.equals("owner", ignoreCase = true) -> "Owner"
        accessRole.equals("writer", ignoreCase = true) -> "Can edit"
        accessRole.equals("reader", ignoreCase = true) -> "View only"
        accessRole.equals("freeBusyReader", ignoreCase = true) -> "Free/busy only"
        else -> accessRole
    }

    companion object {
        /**
         * Writable access roles for Google Calendar.
         */
        val GOOGLE_WRITABLE_ROLES = setOf("owner", "writer")

        /**
         * Create from Google Calendar API response data.
         */
        fun fromGoogleCalendar(
            id: String,
            name: String,
            isPrimary: Boolean,
            accessRole: String,
            backgroundColor: String? = null,
            description: String? = null
        ): ProviderCalendar = ProviderCalendar(
            id = id,
            name = name,
            color = backgroundColor,
            isPrimary = isPrimary,
            isWritable = accessRole in GOOGLE_WRITABLE_ROLES,
            accessRole = accessRole,
            description = description
        )

        /**
         * Create from Outlook/Graph API response data.
         */
        fun fromOutlookCalendar(
            id: String,
            name: String,
            isDefaultCalendar: Boolean,
            canEdit: Boolean,
            color: String? = null
        ): ProviderCalendar = ProviderCalendar(
            id = id,
            name = name,
            color = color,
            isPrimary = isDefaultCalendar,
            isWritable = canEdit,
            accessRole = if (canEdit) "writer" else "reader"
        )
    }
}
