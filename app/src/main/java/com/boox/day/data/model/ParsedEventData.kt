package com.boox.day.data.model

import kotlinx.serialization.Serializable

/**
 * Parsed calendar event data from voice input via AI.
 *
 * This data class represents the structured output from the AI model
 * when parsing voice commands like "meeting with John tomorrow at 3pm".
 *
 * Fields are nullable where the AI couldn't determine a value,
 * allowing the AddEditEventDialog to show empty fields for user input.
 */
@Serializable
data class ParsedEventData(
    /** Event title extracted from speech */
    val title: String,

    /** Date in yyyy-MM-dd format, null if not specified */
    val date: String? = null,

    /** Start hour (0-23), null if not specified */
    val startHour: Int? = null,

    /** Start minute (0-59), null if not specified */
    val startMinute: Int? = null,

    /** End hour (0-23), null if not specified */
    val endHour: Int? = null,

    /** End minute (0-59), null if not specified */
    val endMinute: Int? = null,

    /** Duration in minutes if specified (alternative to end time) */
    val durationMinutes: Int? = null,

    /** True if "all day" mentioned or no time specified at all */
    val isAllDay: Boolean = false,

    /** Location/venue if mentioned */
    val location: String? = null,

    /** Additional notes or context */
    val description: String? = null,

    /** People mentioned (e.g., "with John" → ["John"]) */
    val attendees: List<String> = emptyList(),

    /** AI confidence score 0.0-1.0 */
    val confidence: Float = 0.0f,

    /** Fields that required guessing or inference */
    val uncertainFields: List<String> = emptyList(),

    /** Brief human-readable summary of what AI understood */
    val originalIntent: String = ""
) {
    /**
     * Whether the parsing result is reliable enough to auto-fill.
     * Low confidence results should show a warning banner.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.7f

    /**
     * Whether we have enough info to create a meaningful event.
     */
    val hasMinimumInfo: Boolean get() = title.isNotBlank()

    /**
     * Calculate end time from duration if end time not specified.
     * Returns Pair(endHour, endMinute) or null.
     */
    fun calculateEndTime(): Pair<Int, Int>? {
        // If end time already specified, use it
        if (endHour != null && endMinute != null) {
            return Pair(endHour, endMinute)
        }

        // If duration specified, calculate from start time
        if (durationMinutes != null && startHour != null) {
            val startMin = startMinute ?: 0
            val totalMinutes = startHour * 60 + startMin + durationMinutes
            return Pair(
                (totalMinutes / 60) % 24,
                totalMinutes % 60
            )
        }

        // Default: 1 hour duration if start time known
        if (startHour != null) {
            val startMin = startMinute ?: 0
            val totalMinutes = startHour * 60 + startMin + 60
            return Pair(
                (totalMinutes / 60) % 24,
                totalMinutes % 60
            )
        }

        return null
    }

    /**
     * Get description for local-only events (includes attendees in description).
     * Used when NOT syncing to Google Calendar.
     * Avoids duplication: doesn't add "With: X" if attendee names already appear in title.
     */
    fun getEffectiveDescription(): String? {
        val parts = mutableListOf<String>()

        // Add attendees if present AND not already mentioned in title
        if (attendees.isNotEmpty()) {
            // Check if all attendees are already in the title
            val titleLower = title.lowercase()
            val attendeesNotInTitle = attendees.filter { attendee ->
                !titleLower.contains(attendee.lowercase())
            }
            if (attendeesNotInTitle.isNotEmpty()) {
                parts.add("With: ${attendeesNotInTitle.joinToString(", ")}")
            }
        }

        // Add description if present
        if (!description.isNullOrBlank()) {
            parts.add(description)
        }

        return if (parts.isNotEmpty()) parts.joinToString("\n") else null
    }

    /**
     * Get description without attendees (for Google sync mode).
     * When syncing to Google, attendees go in the separate attendees field.
     */
    fun getDescriptionOnly(): String? = description?.takeIf { it.isNotBlank() }

    companion object {
        /**
         * Create an empty/failed result for error cases.
         */
        fun empty(originalIntent: String = "Could not understand") = ParsedEventData(
            title = "",
            confidence = 0f,
            originalIntent = originalIntent
        )
    }
}
