package com.boox.day.data.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * How accounts are named on screen once CalDAV accounts exist (plan step 3.6, Nov 2026): a CalDAV
 * account is named by its service, or by its server when the user typed the address in.
 */

/** "cloud.example.com" from a server address; "" when there is none (Google, Outlook). */
fun serverHost(serverUrl: String): String = serverUrl.toHttpUrlOrNull()?.host ?: ""

/** The service in short lists (Add Event, Calendar & Sync): "Google", "iCloud", or the server for Other. */
fun serviceName(providerId: ProviderId, accountType: AccountType?, serverUrl: String): String = when (providerId) {
    ProviderId.GOOGLE -> "Google"
    ProviderId.OUTLOOK -> "Outlook"
    ProviderId.CALDAV -> when (accountType) {
        AccountType.CALDAV_ICLOUD, AccountType.CALDAV_FASTMAIL, AccountType.CALDAV_NEXTCLOUD -> accountType.displayName
        else -> serverHost(serverUrl).ifEmpty { "CalDAV" }
    }
}

val ConnectedAccount.serviceName: String get() = serviceName(providerId, accountType, serverUrl)

/**
 * The line under an account's address in Calendar Accounts: its type for Google and Outlook
 * ("Gmail"), the service for iCloud and Fastmail, the service and server for Nextcloud, the server
 * for Other.
 */
val ConnectedAccount.typeLabel: String
    get() = when (accountType) {
        AccountType.CALDAV_NEXTCLOUD -> "Nextcloud · ${serverHost(serverUrl)}"
        AccountType.CALDAV_OTHER -> serverHost(serverUrl).ifEmpty { accountType.displayName }
        else -> accountType.displayName
    }

/** The account in Add Event's list, after the service's name: the address, and the server for Nextcloud. */
fun accountDetail(accountType: AccountType?, email: String, serverUrl: String): String =
    if (accountType == AccountType.CALDAV_NEXTCLOUD) "$email · ${serverHost(serverUrl)}" else email

/**
 * Each connected account's service name, for the screens that name an event's service (owner's
 * choice, 2 Nov 2026: "Synced to iCloud", never "CalDAV Calendar"). Kept up to date by the account
 * list behind Calendar Accounts (MultiAccountSyncViewModel.loadAccounts), which loads at start.
 */
object AccountServiceNames {
    @Volatile
    private var names: Map<String, String> = emptyMap()

    fun update(accounts: List<ConnectedAccount>) {
        names = accounts.associate { it.id to it.serviceName }
    }

    /** "Google Calendar" and "Outlook Calendar" as before; a CalDAV account's service or server. */
    fun of(providerId: ProviderId?, accountId: String?): String = when (providerId) {
        null -> "cloud"
        ProviderId.CALDAV -> accountId?.let { names[it] } ?: providerId.displayName
        else -> providerId.displayName
    }

    /** By the event's provider key; an unknown key reads as Google, like ProviderId.fromKey. */
    fun of(providerKey: String, accountId: String?): String = of(ProviderId.fromKey(providerKey), accountId)
}
