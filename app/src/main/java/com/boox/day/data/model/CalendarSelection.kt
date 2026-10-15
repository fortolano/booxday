package com.boox.day.data.model

/**
 * Represents a user's selection settings for a specific calendar.
 * Controls which calendars to sync and which to use for new events.
 * Persisted via [CalendarSelectionEntity] in Room database.
 *
 * @property id Unique identifier for this selection
 * @property accountId FK to the ConnectedAccount this calendar belongs to
 * @property calendarId Provider's calendar ID (Google/Outlook calendar ID)
 * @property calendarName Display name of the calendar
 * @property calendarColor Calendar color (hex format, e.g., "#4285F4")
 * @property isFetchEnabled Whether to sync events FROM this calendar
 * @property isDefaultForAccount Whether to create new events IN this calendar
 * @property displayOrder Sort order for UI display
 */
data class CalendarSelection(
    val id: String,
    val accountId: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String? = null,
    val isFetchEnabled: Boolean = false,
    val isDefaultForAccount: Boolean = false,
    val displayOrder: Int = 0
) {
    /**
     * Get display-friendly label for this calendar.
     */
    val displayLabel: String
        get() = calendarName

    /**
     * Check if this calendar should be synced.
     */
    val shouldSync: Boolean
        get() = isFetchEnabled

    companion object {
        /**
         * Create a new CalendarSelection from a ProviderCalendar.
         * New calendars default to NOT synced (user must explicitly enable).
         *
         * @param accountId The account this calendar belongs to
         * @param calendar The provider calendar data
         * @param isDefault Whether this should be the default calendar for the account
         */
        fun fromProviderCalendar(
            accountId: String,
            calendar: ProviderCalendar,
            isDefault: Boolean = false
        ): CalendarSelection = CalendarSelection(
            id = generateId(),
            accountId = accountId,
            calendarId = calendar.id,
            calendarName = calendar.name,
            calendarColor = calendar.color,
            isFetchEnabled = calendar.isPrimary, // Auto-enable primary calendar
            isDefaultForAccount = isDefault || calendar.isPrimary,
            displayOrder = if (calendar.isPrimary) 0 else 1
        )

        /**
         * Generate a unique ID for a new selection.
         */
        private fun generateId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(21)
    }
}
