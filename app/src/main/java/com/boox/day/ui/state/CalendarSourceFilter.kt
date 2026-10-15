package com.boox.day.ui.state

/**
 * Represents the filter state for calendar event sources.
 * Controls which events are shown in the Events Grid based on their origin.
 *
 * Session-only persistence (resets on app restart).
 */
data class CalendarSourceFilter(
    val showLocal: Boolean = true,
    val showGoogle: Boolean = true,
    val showOutlook: Boolean = true
) {
    /**
     * Count of currently active (enabled) sources.
     * Used to prevent deselecting the last source.
     */
    val activeCount: Int get() = listOf(showLocal, showGoogle, showOutlook).count { it }

    /**
     * Check if a source can be deselected.
     * Returns false if this is the last active source (must keep at least one).
     */
    fun canDeselect(source: CalendarSource): Boolean = activeCount > 1

    /**
     * Toggle the visibility of a specific source.
     * Returns a new filter state with the source toggled.
     */
    fun toggle(source: CalendarSource): CalendarSourceFilter = when (source) {
        CalendarSource.LOCAL -> copy(showLocal = !showLocal)
        CalendarSource.GOOGLE -> copy(showGoogle = !showGoogle)
        CalendarSource.OUTLOOK -> copy(showOutlook = !showOutlook)
    }

    /**
     * Check if a specific source is enabled.
     */
    fun isEnabled(source: CalendarSource): Boolean = when (source) {
        CalendarSource.LOCAL -> showLocal
        CalendarSource.GOOGLE -> showGoogle
        CalendarSource.OUTLOOK -> showOutlook
    }
}

/**
 * Enum representing calendar event sources.
 */
enum class CalendarSource {
    /** Local/device-only events (not synced to any cloud service) */
    LOCAL,

    /** Events from Google Calendar */
    GOOGLE,

    /** Events from Microsoft Outlook Calendar */
    OUTLOOK
}
