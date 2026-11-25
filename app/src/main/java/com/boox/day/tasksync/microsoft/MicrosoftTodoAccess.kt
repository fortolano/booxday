package com.boox.day.tasksync.microsoft

import android.app.Activity
import android.content.Context
import com.boox.day.sync.microsoft.MicrosoftScopes
import com.boox.day.sync.outlook.AccountNotFoundException
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.SignInCancelledException
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalUiRequiredException

/**
 * Microsoft's Tasks permission for an Outlook account of Calendar Accounts (Nov 2026, owner
 * decisions 79-84): asked when Task Sync is turned on with it, or on Reconnect. The calendar's
 * sign-in is reused: Microsoft shows its own consent page only when it needs the user.
 */
internal object MicrosoftTodoAccess {

    sealed class Result {
        object Granted : Result()
        /** The user closed Microsoft's page. */
        object Cancelled : Result()
        /** Microsoft's page was used with another account than this one. */
        object OtherAccount : Result()
        /** The account's Microsoft sign-in is gone from the tablet: signed in again in Calendar Accounts. */
        object SignInGone : Result()
        object Offline : Result()
        object Failed : Result()
    }

    /** The permission for the Outlook account [accountId]; Microsoft's page from [activity] when it asks. */
    suspend fun ensure(context: Context, activity: Activity?, accountId: String): Result {
        val app = context.applicationContext
        val msalId = OutlookCalendarProvider.msalAccountIdOf(app, accountId) ?: return Result.SignInGone
        val auth = OutlookAuthManager(app)
        if (auth.initialize().isFailure) return Result.Failed
        val silent = auth.getAccessToken(msalId, MicrosoftScopes.TASKS)
        if (silent.isSuccess) return Result.Granted
        when (val failure = silent.exceptionOrNull()) {
            is AccountNotFoundException -> return Result.SignInGone
            is MsalUiRequiredException -> Unit  // not allowed yet (or withdrawn): Microsoft's page
            is MsalClientException -> return if (failure.errorCode in NETWORK_CODES) Result.Offline else Result.Failed
            else -> return Result.Failed
        }
        activity ?: return Result.Failed
        val asked = auth.requestScopes(activity, msalId, MicrosoftScopes.TASKS)
        asked.getOrNull()?.let { return if (it == msalId) Result.Granted else Result.OtherAccount }
        return when (val failure = asked.exceptionOrNull()) {
            is SignInCancelledException -> Result.Cancelled
            is AccountNotFoundException -> Result.SignInGone
            is MsalClientException -> if (failure.errorCode in NETWORK_CODES) Result.Offline else Result.Failed
            else -> Result.Failed
        }
    }

    /** MSAL's "no network" answers. */
    private val NETWORK_CODES = setOf("device_network_not_available", "io_error")
}
