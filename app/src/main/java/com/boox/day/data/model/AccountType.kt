package com.boox.day.data.model

/**
 * Specific account type within a provider.
 * Distinguishes between personal and workspace/enterprise accounts.
 *
 * @property displayName Human-readable name for UI display
 * @property key Stable string key used for database storage
 * @property providerId The parent provider this account type belongs to
 * @property authority OAuth authority URL (for MSAL/Outlook accounts)
 * @property serverAddress The CalDAV server to start from, for the ready-made choices (owner
 *   decision 59); null where the user types it (Nextcloud, Other)
 */
enum class AccountType(
    val displayName: String,
    val key: String,
    val providerId: ProviderId,
    val authority: String? = null,
    val serverAddress: String? = null
) {
    /**
     * Personal Gmail account (e.g., user@gmail.com)
     */
    GOOGLE_PERSONAL(
        displayName = "Gmail",
        key = "GOOGLE_PERSONAL",
        providerId = ProviderId.GOOGLE
    ),

    /**
     * Google Workspace account (e.g., user@company.com with Google Workspace)
     */
    GOOGLE_WORKSPACE(
        displayName = "Google Workspace",
        key = "GOOGLE_WORKSPACE",
        providerId = ProviderId.GOOGLE
    ),

    /**
     * Personal Microsoft account (Outlook.com, Hotmail, Live)
     */
    OUTLOOK_PERSONAL(
        displayName = "Outlook.com",
        key = "OUTLOOK_PERSONAL",
        providerId = ProviderId.OUTLOOK,
        authority = "https://login.microsoftonline.com/consumers"
    ),

    /**
     * Work or school Office 365 account
     */
    OUTLOOK_WORK_SCHOOL(
        displayName = "Work/School",
        key = "OUTLOOK_WORK_SCHOOL",
        providerId = ProviderId.OUTLOOK,
        authority = "https://login.microsoftonline.com/organizations"
    ),

    // CalDAV (plan stage 3, owner decision 59, Oct 2026). Sign-in finds the calendars from the
    // address (RFC 6764 well-known first), so these are starting points, not exact paths.
    CALDAV_ICLOUD(
        displayName = "iCloud",
        key = "CALDAV_ICLOUD",
        providerId = ProviderId.CALDAV,
        serverAddress = "https://caldav.icloud.com/"
    ),

    CALDAV_FASTMAIL(
        displayName = "Fastmail",
        key = "CALDAV_FASTMAIL",
        providerId = ProviderId.CALDAV,
        serverAddress = "https://caldav.fastmail.com/dav/"
    ),

    /** The user's own Nextcloud: its address is asked for. */
    CALDAV_NEXTCLOUD(
        displayName = "Nextcloud",
        key = "CALDAV_NEXTCLOUD",
        providerId = ProviderId.CALDAV
    ),

    /** Any other CalDAV server (https only, owner decision 5). */
    CALDAV_OTHER(
        displayName = "Other",
        key = "CALDAV_OTHER",
        providerId = ProviderId.CALDAV
    );

    companion object {
        /**
         * Parse account type from database key string.
         * Returns GOOGLE_PERSONAL as default for migration compatibility.
         */
        fun fromKey(key: String): AccountType = fromKeyOrNull(key) ?: GOOGLE_PERSONAL

        /** The account type for a stored key, or null for one this version doesn't know. */
        fun fromKeyOrNull(key: String): AccountType? = entries.find { it.key == key }

        /**
         * Get all account types for a specific provider.
         */
        fun forProvider(providerId: ProviderId): List<AccountType> =
            entries.filter { it.providerId == providerId }

        /**
         * Get Google account types for UI.
         */
        val googleTypes: List<AccountType>
            get() = listOf(GOOGLE_PERSONAL, GOOGLE_WORKSPACE)

        /**
         * Get Outlook account types for UI.
         */
        val outlookTypes: List<AccountType>
            get() = listOf(OUTLOOK_PERSONAL, OUTLOOK_WORK_SCHOOL)

        /** CalDAV choices, in the order the sign-in shows them (owner decision 59). */
        val calDavTypes: List<AccountType>
            get() = listOf(CALDAV_ICLOUD, CALDAV_FASTMAIL, CALDAV_NEXTCLOUD, CALDAV_OTHER)
    }
}
