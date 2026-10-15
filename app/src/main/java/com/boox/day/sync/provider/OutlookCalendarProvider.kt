package com.boox.day.sync.provider

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.boox.day.data.db.ConnectedAccountDao
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.sync.SyncResult
import com.boox.day.sync.NewEventData
import com.boox.day.sync.UpdateEventData
import com.boox.day.sync.outlook.AccountNotFoundException
import com.boox.day.sync.outlook.GraphApiException
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.OutlookCalendarService
import com.boox.day.sync.outlook.PartialCalendarFetchException
import com.boox.day.sync.outlook.SignInCancelledException
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalIntuneAppProtectionPolicyRequiredException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.microsoft.identity.client.exception.MsalUnsupportedBrokerException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Outlook/Office365 calendar provider implementation.
 *
 * This provider integrates with Microsoft Graph API via MSAL authentication
 * to support both personal Outlook.com accounts and work/school Office 365 accounts.
 *
 * ## Authentication Flow
 * Unlike Google (which uses Intent-based auth), Outlook uses MSAL's callback-based
 * interactive authentication. Therefore:
 * - [getSignInIntent] returns null
 * - [handleSignInResult] returns failure (not used)
 * - [signInInteractive] is the primary sign-in method
 *
 * ## Account Identification
 * Outlook accounts have two IDs:
 * - `msalAccountId`: MSAL's internal account identifier (used for token operations)
 * - `accountId`: Our internal nanoid (stored in ConnectedAccountEntity.id)
 *
 * The [accountId] passed to most methods is our internal ID. This class maintains
 * a mapping by storing the msalAccountId in the database record.
 *
 * ## Error Handling
 * Graph API errors are converted to appropriate [CalendarProviderException] types:
 * - 401 → AuthenticationException
 * - 403 → PermissionException
 * - 404 → NotFoundException
 * - 429 → RateLimitException
 *
 * @param context Application context
 * @param authManager Outlook authentication manager
 * @param calendarService Outlook calendar service for Graph API calls
 * @param accountDao DAO for connected accounts
 */
class OutlookCalendarProvider(
    private val context: Context,
    private val authManager: OutlookAuthManager,
    private val calendarService: OutlookCalendarService,
    private val accountDao: ConnectedAccountDao
) : CalendarProvider {

    companion object {
        private const val TAG = "OutlookCalendarProvider"

        // SharedPreferences key prefix for storing MSAL account ID mapping
        private const val PREFS_NAME = "outlook_accounts"
        private const val KEY_MSAL_ID_PREFIX = "msal_id_"
    }

    override val providerId: ProviderId = ProviderId.OUTLOOK

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all signed-in Outlook accounts.
     *
     * Returns accounts that:
     * 1. Exist in our database (isActive = true)
     * 2. Have a valid MSAL account in cache
     */
    override suspend fun getSignedInAccounts(): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        try {
            // Get Outlook accounts from our database
            val dbAccounts = accountDao.getAccountsByProvider(ProviderId.OUTLOOK.key)

            // Verify each account still exists in MSAL
            val validAccounts = dbAccounts.filter { entity ->
                val msalId = getMsalAccountId(entity.id)
                if (msalId != null) {
                    authManager.hasAccount(msalId)
                } else {
                    false
                }
            }

            validAccounts.map { it.toDomainModel() }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting signed-in accounts", e)
            emptyList()
        }
    }

    /**
     * Outlook uses callback-based auth, not Intent-based.
     * Always returns null. Use [signInInteractive] instead.
     */
    override fun getSignInIntent(activity: Activity, accountType: AccountType): Intent? {
        // Outlook uses MSAL's callback-based flow, not Intent-based
        return null
    }

    /**
     * Not used for Outlook. Returns failure.
     * Use [signInInteractive] instead.
     */
    override suspend fun handleSignInResult(data: Intent?): Result<ConnectedAccount> {
        return Result.failure(
            CalendarProviderException.ProviderException(
                "Outlook uses callback-based sign-in. Use signInInteractive() instead."
            )
        )
    }

    /**
     * Perform interactive sign-in for Outlook.
     *
     * This launches Chrome Custom Tabs for the user to authenticate with Microsoft.
     *
     * @param activity The activity context for displaying the sign-in UI
     * @param accountType OUTLOOK_PERSONAL or OUTLOOK_WORK_SCHOOL
     * @return Result containing the new ConnectedAccount, or failure
     */
    override suspend fun signInInteractive(
        activity: Activity,
        accountType: AccountType
    ): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
        if (accountType.providerId != ProviderId.OUTLOOK) {
            return@withContext Result.failure(
                CalendarProviderException.ProviderException("Invalid account type for Outlook: ${accountType.key}")
            )
        }

        // Ensure MSAL is initialized
        val initResult = authManager.initialize()
        if (initResult.isFailure) {
            return@withContext Result.failure(
                CalendarProviderException.ProviderException(
                    "MSAL initialization failed: ${initResult.exceptionOrNull()?.message}",
                    cause = initResult.exceptionOrNull()
                )
            )
        }

        try {
            // Perform interactive sign-in
            val authResult = authManager.signInInteractive(activity, accountType)
                .getOrElse { e ->
                    return@withContext Result.failure(wrapException(e))
                }

            // Fetch user profile to get display name
            val tokenResult = authManager.getAccessToken(authResult.msalAccountId)
            val displayName = if (tokenResult.isSuccess) {
                val profileResult = calendarService.fetchUserProfile(tokenResult.getOrThrow())
                profileResult.getOrNull()?.displayName
            } else {
                authResult.displayName
            }

            // Check if account already exists in database
            val existingEntity = accountDao.getByEmail(authResult.email)
            val accountEntity = if (existingEntity != null) {
                // Update existing account
                existingEntity.copy(
                    accountType = accountType.key,
                    displayName = displayName,
                    isActive = true
                ).also {
                    accountDao.upsert(it)
                    // Update MSAL ID mapping
                    saveMsalAccountId(it.id, authResult.msalAccountId)
                }
            } else {
                // Create new account
                val newAccount = ConnectedAccount.create(
                    providerId = ProviderId.OUTLOOK,
                    accountType = accountType,
                    email = authResult.email,
                    displayName = displayName
                )
                val newEntity = ConnectedAccountEntity.fromDomainModel(newAccount)
                accountDao.upsert(newEntity)
                // Save MSAL ID mapping
                saveMsalAccountId(newEntity.id, authResult.msalAccountId)
                newEntity
            }

            Log.i(TAG, "Outlook sign-in successful: ${accountEntity.email}")
            Result.success(accountEntity.toDomainModel())

        } catch (e: SignInCancelledException) {
            Result.failure(CalendarProviderException.CancelledException("Sign-in cancelled"))
        } catch (e: Exception) {
            Log.e(TAG, "Error during Outlook sign-in", e)
            Result.failure(wrapException(e))
        }
    }

    /**
     * Remove an Outlook account.
     *
     * Signs out from MSAL and deactivates the account in our database.
     */
    override suspend fun removeAccount(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accountEntity = accountDao.getById(accountId)
            if (accountEntity == null) {
                Log.w(TAG, "Account not found for removal: $accountId")
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            // Remove from MSAL
            val msalId = getMsalAccountId(accountId)
            if (msalId != null) {
                authManager.removeAccount(msalId)
                clearMsalAccountId(accountId)
            }

            // Deactivate in database
            accountDao.deactivate(accountId)

            Log.i(TAG, "Outlook account removed: ${accountEntity.email}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing account: $accountId", e)
            Result.failure(wrapException(e))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch all calendars for an Outlook account.
     */
    override suspend fun fetchCalendars(accountId: String): Result<List<ProviderCalendar>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get access token for account: $accountId")
                )

            val result = calendarService.fetchCalendars(accessToken)

            result.fold(
                onSuccess = { calendars ->
                    Log.d(TAG, "Fetched ${calendars.size} calendars for account $accountId")
                    Result.success(calendars)
                },
                onFailure = { e ->
                    Result.failure(wrapException(e))
                }
            )

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error fetching calendars for account $accountId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Fetch events from specified calendars within a date range.
     */
    override suspend fun fetchEvents(
        accountId: String,
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<ProviderEvent>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get access token for account: $accountId")
                )

            val result = calendarService.fetchEvents(accessToken, calendarIds, startDate, endDate)

            result.fold(
                onSuccess = { events ->
                    // Update last sync time
                    accountDao.updateLastSync(accountId, System.currentTimeMillis())
                    Log.d(TAG, "Fetched ${events.size} events for account $accountId")
                    Result.success(events)
                },
                onFailure = { e ->
                    Result.failure(wrapException(e))
                }
            )

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error fetching events for account $accountId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new event in an Outlook calendar.
     */
    override suspend fun createEvent(
        accountId: String,
        calendarId: String,
        event: NewEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get access token")
                )

            val result = calendarService.createEvent(accessToken, calendarId, event)

            result.fold(
                onSuccess = { createdEvent ->
                    Log.i(TAG, "Created event: ${createdEvent.title}")
                    Result.success(createdEvent)
                },
                onFailure = { e ->
                    Result.failure(wrapException(e))
                }
            )

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error creating event in calendar $calendarId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Update an existing event in Outlook.
     */
    override suspend fun updateEvent(
        accountId: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get access token")
                )

            val result = calendarService.updateEvent(accessToken, calendarId, eventId, event)

            result.fold(
                onSuccess = { updatedEvent ->
                    Log.i(TAG, "Updated event: ${updatedEvent.title}")
                    Result.success(updatedEvent)
                },
                onFailure = { e ->
                    Result.failure(wrapException(e))
                }
            )

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error updating event $eventId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Delete an event from Outlook.
     */
    override suspend fun deleteEvent(
        accountId: String,
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get access token")
                )

            val result = calendarService.deleteEvent(accessToken, calendarId, eventId)

            result.fold(
                onSuccess = {
                    Log.i(TAG, "Deleted event: $eventId")
                    Result.success(Unit)
                },
                onFailure = { e ->
                    Result.failure(wrapException(e))
                }
            )

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error deleting event $eventId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Token Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refresh token if needed.
     *
     * MSAL handles token refresh automatically via acquireTokenSilent.
     * This method just validates that we can get a token.
     */
    override suspend fun refreshTokenIfNeeded(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessTokenForAccount(accountId)
            if (accessToken != null) {
                Result.success(Unit)
            } else {
                Result.failure(
                    CalendarProviderException.AuthenticationException("Could not refresh token for account: $accountId")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token for account $accountId", e)
            Result.failure(wrapException(e))
        }
    }

    /**
     * Execute an operation with automatic retry on auth failure.
     *
     * Flow:
     * 1. Execute operation
     * 2. If 401/403 error, attempt token refresh via MSAL acquireTokenSilent
     * 3. Retry operation once
     * 4. If still fails, mark account as needing re-auth
     *
     * @param accountId The account ID for token refresh
     * @param operation The operation to execute
     * @return Result of the operation
     */
    private suspend fun <T> withAuthRetry(
        accountId: String,
        operation: suspend () -> Result<T>
    ): Result<T> {
        val firstAttempt = operation()

        if (firstAttempt.isSuccess) {
            return firstAttempt
        }

        val error = firstAttempt.exceptionOrNull()

        // Check if this is an auth error that we should retry
        val isAuthError = when (error) {
            is CalendarProviderException.AuthenticationException -> true
            is MsalUiRequiredException -> true
            is GraphApiException -> error.isAuthError
            is Exception -> {
                val msg = error.message ?: ""
                msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized")
            }
            else -> false
        }

        if (!isAuthError) {
            // Not an auth error, return original failure
            return firstAttempt
        }

        Log.d(TAG, "Auth error detected, attempting token refresh for account $accountId")

        // Attempt token refresh - MSAL will try acquireTokenSilent
        val refreshResult = refreshTokenIfNeeded(accountId)
        if (refreshResult.isFailure) {
            Log.w(TAG, "Token refresh failed, account needs re-authentication")
            return Result.failure(
                CalendarProviderException.AuthenticationException(
                    "Session expired. Please sign in again.",
                    error
                )
            )
        }

        // Retry the operation once
        Log.d(TAG, "Token refreshed, retrying operation")
        val retryResult = operation()

        if (retryResult.isFailure) {
            val retryError = retryResult.exceptionOrNull()
            Log.w(TAG, "Operation failed after token refresh: ${retryError?.message}")
            // If it still fails with auth error, the account needs re-authentication
            return Result.failure(
                CalendarProviderException.AuthenticationException(
                    "Authentication expired. Please sign in again.",
                    retryError
                )
            )
        }

        return retryResult
    }

    /**
     * Check if account credentials are valid.
     *
     * Quick check that the MSAL account exists in cache.
     */
    override fun isAccountValid(accountId: String): Boolean {
        return try {
            val msalId = getMsalAccountId(accountId)
            msalId != null && authManager.isReady()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking account validity: $accountId", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get access token for an account, handling token refresh.
     */
    private suspend fun getAccessTokenForAccount(accountId: String): String? {
        val msalId = getMsalAccountId(accountId) ?: return null

        val result = authManager.getAccessToken(msalId)
        return result.getOrNull()
    }

    /**
     * Get the MSAL account ID for our internal account ID.
     */
    private fun getMsalAccountId(accountId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MSAL_ID_PREFIX + accountId, null)
    }

    /**
     * Save the MSAL account ID mapping.
     */
    private fun saveMsalAccountId(accountId: String, msalId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MSAL_ID_PREFIX + accountId, msalId).apply()
    }

    /**
     * Clear the MSAL account ID mapping.
     */
    private fun clearMsalAccountId(accountId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MSAL_ID_PREFIX + accountId).apply()
    }

    /**
     * Wrap exceptions in CalendarProviderException.
     */
    private fun wrapException(e: Throwable): CalendarProviderException {
        return when (e) {
            is CalendarProviderException -> e

            is SignInCancelledException -> CalendarProviderException.CancelledException(e.message ?: "Cancelled")

            is AccountNotFoundException -> CalendarProviderException.NotFoundException(e.message ?: "Account not found")

            is MsalUnsupportedBrokerException -> CalendarProviderException.PermissionException(
                "This work account requires Microsoft Authenticator or Company Portal support before it can be connected.",
                e
            )

            is MsalIntuneAppProtectionPolicyRequiredException -> CalendarProviderException.PermissionException(
                "Your organization requires an approved company-managed app before Outlook access can be granted.",
                e
            )

            is MsalDeclinedScopeException -> CalendarProviderException.PermissionException(
                "Microsoft did not grant all required Outlook permissions. Please try again or ask your admin to approve BooxDay.",
                e
            )

            is MsalUiRequiredException -> mapMsalException(e)

            is MsalException -> mapMsalException(e)

            is PartialCalendarFetchException -> when {
                e.isAllNetworkFailures -> CalendarProviderException.NetworkException(
                    "No network connection",
                    e.primaryCause ?: e
                )
                e.isAllAuthFailures -> CalendarProviderException.AuthenticationException(
                    "Token expired or invalid",
                    e.primaryCause ?: e
                )
                e.isAllPermissionFailures -> CalendarProviderException.PermissionException(
                    "Microsoft denied calendar access. Your organization may require admin approval for BooxDay.",
                    e.primaryCause ?: e
                )
                e.isAllRateLimitFailures -> CalendarProviderException.RateLimitException(
                    "Rate limited",
                    null,
                    e.primaryCause ?: e
                )
                else -> CalendarProviderException.PartialSyncException(
                    syncResult = SyncResult.PartialSuccess(
                        successCount = e.partialEvents.size,
                        failureCount = e.failures.size,
                        errors = e.errorMessages
                    ),
                    partialEvents = e.partialEvents
                )
            }

            is GraphApiException -> when {
                e.isAuthError -> CalendarProviderException.AuthenticationException(
                    "Token expired or invalid", e
                )
                e.isPermissionError -> CalendarProviderException.PermissionException(
                    "Microsoft denied calendar access. Your organization may require admin approval for BooxDay.",
                    e
                )
                e.isNotFound -> CalendarProviderException.NotFoundException(
                    "Resource not found", e
                )
                e.isRateLimited -> CalendarProviderException.RateLimitException(
                    "Rate limited", null, e
                )
                else -> CalendarProviderException.ProviderException(
                    "Graph API error: ${e.statusCode}", cause = e
                )
            }

            is java.net.UnknownHostException -> CalendarProviderException.NetworkException(
                "No network connection", e
            )

            is java.net.SocketTimeoutException -> CalendarProviderException.NetworkException(
                "Connection timeout", e
            )

            else -> CalendarProviderException.ProviderException(
                e.message ?: "Unknown error", cause = e
            )
        }
    }

    private fun logProviderFailure(
        message: String,
        wrapped: CalendarProviderException,
        cause: Exception
    ) {
        if (wrapped is CalendarProviderException.NetworkException) {
            Log.w(TAG, "$message: ${wrapped.message}")
        } else {
            Log.e(TAG, message, cause)
        }
    }

    private fun mapMsalException(e: MsalException): CalendarProviderException {
        val errorCode = e.errorCode.orEmpty().lowercase()
        val message = e.message.orEmpty().lowercase()
        val searchable = "$errorCode $message"

        return when {
            searchable.contains("broker") ||
                searchable.contains("company portal") ||
                searchable.contains("authenticator") ||
                searchable.contains("conditional access") -> {
                CalendarProviderException.PermissionException(
                    "This work account requires Microsoft Authenticator or Company Portal support before it can be connected.",
                    e
                )
            }

            searchable.contains("admin consent") ||
                searchable.contains("administrator consent") ||
                searchable.contains("admin approval") ||
                searchable.contains("approval required") ||
                searchable.contains("request approval") ||
                searchable.contains("not consented") ||
                searchable.contains("consent_required") ||
                searchable.contains("aadsts65001") ||
                searchable.contains("aadsts65004") ||
                errorCode == MsalServiceException.ACCESS_DENIED.lowercase() -> {
                CalendarProviderException.PermissionException(
                    "Your organization requires admin approval before BooxDay can access Outlook Calendar.",
                    e
                )
            }

            searchable.contains("publisher") && searchable.contains("verify") -> {
                CalendarProviderException.PermissionException(
                    "Your organization is blocking BooxDay until it is admin-approved or publisher-verified.",
                    e
                )
            }

            searchable.contains("password") ||
                searchable.contains("invalid username") ||
                searchable.contains("invalid password") ||
                searchable.contains("password or account is invalid") -> {
                CalendarProviderException.AuthenticationException(
                    "Microsoft rejected the sign-in. Please check the account details and password.",
                    e
                )
            }

            e is MsalUiRequiredException -> {
                CalendarProviderException.AuthenticationException(
                    "Microsoft sign-in is required again. Please try connecting the account again.",
                    e
                )
            }

            else -> CalendarProviderException.AuthenticationException(
                buildMsalErrorMessage(e),
                e
            )
        }
    }

    private fun buildMsalErrorMessage(e: MsalException): String {
        val errorCode = e.errorCode?.trim().orEmpty()
        val rawMessage = e.message?.trim().orEmpty()

        return when {
            rawMessage.isNotBlank() && errorCode.isNotBlank() ->
                "Microsoft sign-in failed ($errorCode): $rawMessage"
            rawMessage.isNotBlank() ->
                "Microsoft sign-in failed: $rawMessage"
            errorCode.isNotBlank() ->
                "Microsoft sign-in failed ($errorCode)"
            else ->
                "Microsoft sign-in failed"
        }
    }
}
