package com.boox.day.data.model

/**
 * Identifies a calendar provider (Google, Outlook, etc.)
 * Used throughout the app to distinguish events and accounts by their source.
 *
 * @property displayName Human-readable name for UI display
 * @property key Stable string key used for database storage and serialization
 */
enum class ProviderId(
    val displayName: String,
    val key: String
) {
    /**
     * Google Calendar provider (Gmail, Google Workspace)
     */
    GOOGLE("Google Calendar", "GOOGLE"),

    /**
     * Microsoft Outlook provider (Outlook.com, Office365)
     */
    OUTLOOK("Outlook Calendar", "OUTLOOK");

    companion object {
        /**
         * Parse provider from database key string.
         * Returns GOOGLE as default for backward compatibility with existing events.
         */
        fun fromKey(key: String): ProviderId = when (key.uppercase()) {
            GOOGLE.key -> GOOGLE
            OUTLOOK.key -> OUTLOOK
            else -> GOOGLE // Default for migration compatibility
        }
    }
}
