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
        fun fromKey(key: String): ProviderId = fromKeyOrNull(key) ?: GOOGLE // Default for migration compatibility

        /**
         * The provider for a stored key, or null for one this version doesn't know. Accounts use
         * this (Oct 2026): an account of an unknown provider must never be synced as Google.
         * Events keep [fromKey]'s fallback for display ("LOCAL" and older rows).
         */
        fun fromKeyOrNull(key: String): ProviderId? = when (key.uppercase()) {
            GOOGLE.key -> GOOGLE
            OUTLOOK.key -> OUTLOOK
            else -> null
        }
    }
}
