package com.boox.day.sync

import android.app.Activity
import android.content.Context
import com.boox.day.api.NetworkState
import com.boox.day.api.NetworkStatus
import com.boox.day.api.ai.AiProblem
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ProviderId
import com.boox.day.sync.microsoft.MicrosoftScopes
import com.boox.day.sync.outlook.AccountNotFoundException
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.SignInCancelledException
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalIntuneAppProtectionPolicyRequiredException
import com.microsoft.identity.client.exception.MsalUnsupportedBrokerException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Microsoft account for OneDrive backup, picked on the backup screen (owner decision 86, Nov
 * 2026): an Outlook account already in Calendar Accounts (Microsoft shows only its consent page),
 * or any personal, work or school account. Only the backup permissions are asked for.
 */
internal object OneDriveSignIn {

    /** The Outlook accounts in Calendar Accounts this tablet is still signed in to, offered first. */
    suspend fun calendarAccounts(context: Context, outlook: OutlookAuthManager): List<OneDriveAccount> =
        withContext(Dispatchers.IO) {
            try {
                if (outlook.initialize().isFailure) return@withContext emptyList()
                AppDatabase.getDatabase(context).connectedAccountDao()
                    .getAccountsByProvider(ProviderId.OUTLOOK.key)
                    .mapNotNull { row ->
                        OutlookCalendarProvider.msalAccountIdOf(context, row.id)
                            ?.takeIf { outlook.hasAccount(it) }
                            ?.let { OneDriveAccount(it, row.email) }
                    }
                    .distinctBy { it.msalAccountId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // MSAL's account list can throw; then only the new sign-ins are offered
                Log.w(TAG, "Calendar accounts not read: ${e.javaClass.simpleName}")
                emptyList()
            }
        }

    /** Asks Microsoft to let [account] (from Calendar Accounts) keep BooxDay's backups too. */
    suspend fun useCalendarAccount(activity: Activity, outlook: OutlookAuthManager, account: OneDriveAccount): Result<OneDriveAccount> {
        outlook.initialize().onFailure { return Result.failure(it) }
        val id = outlook.requestScopes(activity, account.msalAccountId, MicrosoftScopes.BACKUP).getOrElse { return Result.failure(it) }
        // The user may pick another account on Microsoft's page: keep the one that came back
        if (id == account.msalAccountId) return Result.success(account)
        return try {
            Result.success(OneDriveAccount(id, outlook.getMsalAccounts().firstOrNull { it.id == id }?.username.orEmpty()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** A new sign-in, personal or work/school, asking only for OneDrive backup. */
    suspend fun signIn(activity: Activity, outlook: OutlookAuthManager, type: AccountType): Result<OneDriveAccount> {
        outlook.initialize().onFailure { return Result.failure(it) }
        return outlook.signInInteractive(activity, type, MicrosoftScopes.BACKUP)
            .map { OneDriveAccount(it.msalAccountId, it.email) }
    }

    /** Why the sign-in didn't work, in the user's words. */
    fun errorText(e: Throwable, network: NetworkState = NetworkStatus.current()): String = when {
        e is SignInCancelledException -> "Sign-in was cancelled."
        e is AccountNotFoundException -> "This tablet isn't signed in to that account any more. Choose Personal Account or Work or School Account."
        e is MsalDeclinedScopeException -> "Microsoft didn't allow BooxDay to use OneDrive. Try again, or ask your admin to approve BooxDay."
        e is MsalUnsupportedBrokerException -> "This work account needs Microsoft Authenticator or Company Portal on the tablet."
        e is MsalIntuneAppProtectionPolicyRequiredException -> "Your organization allows only company-approved apps to use its OneDrive."
        network == NetworkState.NONE -> AiProblem.NO_NETWORK.message
        network == NetworkState.NO_INTERNET -> AiProblem.NO_INTERNET.message
        e is MsalClientException && e.errorCode in NETWORK_CODES ->
            "Couldn't reach Microsoft. Check the internet connection, then try again."
        else -> "Microsoft sign-in failed."
    }

    private val NETWORK_CODES = setOf("device_network_not_available", "io_error")
    private const val TAG = "OneDriveSignIn"
}
