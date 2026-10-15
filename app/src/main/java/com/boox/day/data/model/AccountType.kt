package com.boox.day.data.model

/**
 * Specific account type within a provider.
 * Distinguishes between personal and workspace/enterprise accounts.
 *
 * @property displayName Human-readable name for UI display
 * @property key Stable string key used for database storage
 * @property providerId The parent provider this account type belongs to
 * @property authority OAuth authority URL (for MSAL/Outlook accounts)
 */
enum class AccountType(
    val displayName: String,
    val key: String,
    val providerId: ProviderId,
    val authority: String? = null
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
    );

    companion object {
        /**
         * Parse account type from database key string.
         * Returns GOOGLE_PERSONAL as default for migration compatibility.
         */
        fun fromKey(key: String): AccountType = entries.find { it.key == key } ?: GOOGLE_PERSONAL

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
    }
}
