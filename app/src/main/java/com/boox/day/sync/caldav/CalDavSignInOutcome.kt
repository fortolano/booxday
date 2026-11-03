package com.boox.day.sync.caldav

import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount

/**
 * What a CalDAV sign-in from the "iCloud & more" form ends with (plan step 3.6, Nov 2026): connected,
 * a certificate to ask about ("Trust This Server"), or a failure in plain words for the form.
 */

/** What the user picked in "Use … For" (owner decision 78). */
internal data class CalDavUseChoice(val calendars: Boolean, val tasks: Boolean)

/** A first sign-in to a server with calendars and task lists: the user picks what to use it for (owner decision 78). */
internal class CalDavChoiceNeeded(val calendarCount: Int, val taskListCount: Int) : Exception("Choose what to use the account for")

/** A sign-in that worked; [startTasks]: Task Sync is to start with this account (Tasks picked, or a server with only task lists). */
internal data class CalDavSignedIn(val account: ConnectedAccount, val startTasks: Boolean)

/** What the form does after Connect. */
internal sealed class CalDavSignInOutcome {
    data class Connected(val account: ConnectedAccount, val startTasks: Boolean = false) : CalDavSignInOutcome()

    /** Calendars and task lists found: ask what to use the account for, then sign in again with the choice. */
    data class ChooseUses(val calendarCount: Int, val taskListCount: Int) : CalDavSignInOutcome()

    /**
     * The server's certificate isn't one Android can check: ask before trusting it ([changed]: a
     * certificate was trusted for this account before and this is another one). Never trusted
     * without the user's tap.
     */
    data class AskTrust(val sha256: String, val issuedTo: String, val changed: Boolean) : CalDavSignInOutcome()

    data class Failed(val message: String) : CalDavSignInOutcome()
}

/** [result] of a sign-in to a [type] account, where [trustedBefore] is the certificate trusted for it already. */
internal fun calDavSignInOutcome(result: Result<CalDavSignedIn>, type: AccountType, trustedBefore: String?): CalDavSignInOutcome {
    result.getOrNull()?.let { return CalDavSignInOutcome.Connected(it.account, it.startTasks) }
    val failure = result.exceptionOrNull()
    if (failure is CalDavChoiceNeeded) return CalDavSignInOutcome.ChooseUses(failure.calendarCount, failure.taskListCount)
    if (failure is CalDavConnection.Failure.UntrustedCertificate) {
        val issuedTo = failure.subject.split(',').map { it.trim() }.firstOrNull { it.startsWith("CN=") }?.removePrefix("CN=")
            ?: failure.subject
        return CalDavSignInOutcome.AskTrust(failure.sha256, issuedTo, changed = trustedBefore != null && trustedBefore != failure.sha256)
    }
    return CalDavSignInOutcome.Failed(calDavFailureMessage(failure, type))
}

/** A failed sign-in in plain words, with the hint the service needs. */
internal fun calDavFailureMessage(failure: Throwable?, type: AccountType): String = when (failure) {
    is CalDavConnection.Failure.NotHttps -> "This address isn't secure (http). BooxDay connects only over https."
    is CalDavConnection.Failure.BadAddress -> "That isn't a server address. Check it and try again."
    is CalDavConnection.Failure.WrongPassword -> when (type) {
        AccountType.CALDAV_ICLOUD -> "iCloud refused this password. Use an app-specific password, not your Apple Account password."
        AccountType.CALDAV_FASTMAIL -> "Fastmail refused this password. Use an app password, not your usual password."
        AccountType.CALDAV_NEXTCLOUD -> "Nextcloud refused this name or password. Use an app password."
        else -> "The server refused this name or password."
    }
    is CalDavConnection.Failure.NoCalendars -> when (type) {
        AccountType.CALDAV_FASTMAIL -> "No calendars found. Check that the app password has access to Calendars (CalDAV)."
        else -> "No calendar server found at this address. Check it and try again."
    }
    is CalDavConnection.Failure.Offline ->
        if (failure.cause is java.net.UnknownHostException) "Can't find this server. Check the address and the internet connection."
        else "Couldn't reach the server. Check the internet connection and try again."
    is CalDavConnection.Failure.Server -> "The server answered with an error (${failure.code}). Try again later."
    else -> "Couldn't connect. Try again."
}
