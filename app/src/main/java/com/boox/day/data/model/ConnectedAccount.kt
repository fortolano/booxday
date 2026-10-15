package com.boox.day.data.model

/**
 * Represents a connected calendar account (Google or Outlook).
 * This is the domain model used throughout the app.
 * Persisted via [ConnectedAccountEntity] in Room database.
 *
 * @property id Unique identifier (nanoid format)
 * @property providerId The provider (GOOGLE or OUTLOOK)
 * @property accountType Specific account type within the provider
 * @property email User's email address for this account
 * @property displayName User's display name (may be null)
 * @property isActive Whether this account is active (false = soft deleted)
 * @property createdAt Timestamp when account was connected
 * @property lastSyncAt Timestamp of last successful sync (null if never synced)
 */
data class ConnectedAccount(
    val id: String,
    val providerId: ProviderId,
    val accountType: AccountType,
    val email: String,
    val displayName: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null
) {
    /**
     * Get a display-friendly label for this account.
     * Shows email with account type in parentheses.
     */
    val displayLabel: String
        get() = "$email (${accountType.displayName})"

    /**
     * Get short display label (just email).
     */
    val shortLabel: String
        get() = email

    /**
     * Check if this account has ever been synced.
     */
    val hasBeenSynced: Boolean
        get() = lastSyncAt != null

    /**
     * Check if this is a Google account.
     */
    val isGoogleAccount: Boolean
        get() = providerId == ProviderId.GOOGLE

    /**
     * Check if this is an Outlook account.
     */
    val isOutlookAccount: Boolean
        get() = providerId == ProviderId.OUTLOOK

    companion object {
        /**
         * Create a new ConnectedAccount with a generated ID.
         */
        fun create(
            providerId: ProviderId,
            accountType: AccountType,
            email: String,
            displayName: String? = null
        ): ConnectedAccount = ConnectedAccount(
            id = generateId(),
            providerId = providerId,
            accountType = accountType,
            email = email,
            displayName = displayName,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            lastSyncAt = null
        )

        /**
         * Generate a unique ID for a new account.
         * Uses a simple UUID-based approach for reliability.
         */
        private fun generateId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(21)
    }
}
