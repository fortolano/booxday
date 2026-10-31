package com.boox.day.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.tasks.await

/**
 * The Google Tasks permission (task sync, Oct 2026). Plan:
 * C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 2.1.
 *
 * Asked for on its own, only when the user turns Google Tasks sync on, and only for the account
 * they pick. It is never added to the calendar sign-in (GoogleAuthManager.CALENDAR_SCOPES):
 * Google verified that consent screen on 28 Oct 2026, and every calendar token would otherwise
 * ask for Tasks too, failing for accounts that never allowed it.
 *
 * Until Google approves the Tasks permission, release builds don't ask for it (task sync is off
 * there, BuildConfig.TASK_SYNC_ENABLED); a debug build shows Google's "unverified app" screen.
 * Removing the last Google account or Data & Backup "Sign out" withdraw it too (revokeAccess):
 * calls then fail as a refused token, and task sync asks for Reconnect, which runs [check] again.
 */
object GoogleTasksAuth {
    private const val TAG = "GoogleTasksAuth"
    const val TASKS_SCOPE: String = TasksScopes.TASKS

    sealed class Status {
        object Granted : Status()
        /** Google wants the user to say yes: launch this (IntentSenderRequest), then [consentResult]. */
        class NeedsConsent(val pendingIntent: PendingIntent) : Status()
        class Failed(val reason: String) : Status()
    }

    /** Whether [email] has allowed Google Tasks, or what Google needs first. Never shows anything itself. */
    suspend fun check(context: Context, email: String): Status = try {
        status(Identity.getAuthorizationClient(context).authorize(request(email)).await())
    } catch (e: ApiException) {
        Log.w(TAG, "Tasks permission check failed: status ${e.statusCode}")
        Status.Failed("Google said no (${e.statusCode})")
    }

    /** After Google's consent screen ([Status.NeedsConsent]): what the user chose. */
    fun consentResult(context: Context, data: Intent?): Status = try {
        status(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        Log.w(TAG, "Tasks permission not given: status ${e.statusCode}")
        Status.Failed("Not allowed (${e.statusCode})")
    }

    /** For Google Tasks calls, with [email]'s own token (as calendar calls do, GoogleAuthManager.getCredentialForAccount). */
    fun credential(context: Context, email: String): GoogleAccountCredential =
        GoogleAccountCredential.usingOAuth2(context, listOf(TASKS_SCOPE)).apply {
            selectedAccount = Account(email, GOOGLE_ACCOUNT_TYPE)
        }

    /** Only the Tasks scope, for this one account. */
    internal fun request(email: String): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
            .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            .build()

    private fun status(result: AuthorizationResult): Status {
        val pending = result.pendingIntent
        return when {
            result.hasResolution() && pending != null -> Status.NeedsConsent(pending)
            TASKS_SCOPE in result.grantedScopes -> Status.Granted
            else -> Status.Failed("Google Tasks was not allowed")
        }
    }

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
