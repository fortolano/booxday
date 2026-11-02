package com.boox.day.data.model

/**
 * Provider-agnostic writable calendar for event creation.
 */
data class WritableCalendar(
    val id: String,
    val name: String,
    val accountId: String,
    val accountEmail: String,
    val providerId: ProviderId,
    val isPrimary: Boolean,
    val colorHex: String? = null,
    // Naming the account in Add Event's list (CalDAV, plan step 3.6): "iCloud", a Nextcloud's server
    val accountType: AccountType? = null,
    val serverUrl: String = "",
    /** The account's "Default calendar for new events": what Add Event picks first (Nov 2026). */
    val isDefault: Boolean = false
)
