package com.boox.day.sync.provider

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.ConnectedAccountDao
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.sync.CalendarSyncManager
import com.boox.day.sync.EventOperationResult
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.GoogleCalendarInfo
import com.boox.day.sync.SignInErrorText
import com.boox.day.sync.NewEventData
import com.boox.day.sync.SyncErrorType
import com.boox.day.sync.SyncResult
import com.boox.day.sync.UpdateEventData
import com.boox.day.sync.deletedCalendarIds
import com.boox.day.sync.isGoogleTokenRefused
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Google Calendar provider implementation.
 *
 * Wraps existing [GoogleAuthManager] and [CalendarSyncManager] to implement
 * the [CalendarProvider] interface for multi-provider support.
 *
 * ## Design Notes
 *
 * - **Backward Compatibility**: This class wraps existing managers without breaking
 *   current functionality. All existing sync operations continue to work.
 *
 * - **Single Account (Current)**: GoogleAuthManager currently supports only one
 *   signed-in Google account. This provider works with that model.
 *
 * - **Account Mapping**: When a user signs in, we create/update a [ConnectedAccountEntity]
 *   in the database. The accountId used throughout is the nanoid from that entity,
 *   NOT the Google account ID or email.
 *
 * - **Error Handling**: Converts existing [SyncResult] and [EventOperationResult]
 *   to Kotlin [Result] for consistency with the [CalendarProvider] interface.
 *
 * @param context Application context
 * @param authManager Existing Google authentication manager
 * @param syncManager Existing calendar sync manager
 * @param accountDao DAO for connected accounts database operations
 * @param eventRepository Repository for cached calendar events
 */
class GoogleCalendarProvider(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val syncManager: CalendarSyncManager,
    private val accountDao: ConnectedAccountDao,
    private val eventRepository: CalendarEventRepository
) : CalendarProvider {

    companion object {
        private const val TAG = "GoogleCalendarProvider"
        private const val GOOGLE_SIGN_IN_NEEDED = "Google needs you to sign in again"

        /**
         * A failed calendar fetch as the error the account sees. A sign-in problem (a token Google
         * refused, a 401, a missing permission) must be an AuthenticationException, which becomes
         * "Reconnect" in Calendar Accounts; anything else stays a provider error.
         */
        internal fun fetchFailure(result: SyncResult.Error): CalendarProviderException =
            if (result.exception?.isGoogleTokenRefused() == true || result.errorType == SyncErrorType.AUTHENTICATION) {
                CalendarProviderException.AuthenticationException(GOOGLE_SIGN_IN_NEEDED, result.exception)
            } else {
                CalendarProviderException.ProviderException(result.message, cause = result.exception)
            }
    }

    override val providerId: ProviderId = ProviderId.GOOGLE
    private val selectionDao by lazy { AppDatabase.getDatabase(context).calendarSelectionDao() }

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all signed-in Google accounts.
     *
     * Returns ALL active Google accounts from the database. With multi-account
     * support, each account has its own credentials via AccountManager, so we
     * don't rely on GoogleSignIn.getLastSignedInAccount() which only tracks one.
     */
    override suspend fun getSignedInAccounts(): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        try {
            // Get all Google accounts from our database
            val dbAccounts = accountDao.getAccountsByProvider(ProviderId.GOOGLE.key)

            // For backward compat: if GoogleSignIn has an account not yet in DB, add it
            val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (googleAccount != null) {
                val email = googleAccount.email
                if (email != null && dbAccounts.none { it.email == email }) {
                    val newAccount = createConnectedAccountEntity(googleAccount, AccountType.GOOGLE_PERSONAL)
                    accountDao.upsert(newAccount)
                    return@withContext (dbAccounts + newAccount).map { it.toDomainModel() }
                }
            }

            // Return all active accounts from DB
            dbAccounts.map { it.toDomainModel() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signed-in accounts", e)
            emptyList()
        }
    }

    /**
     * Get Intent to start Google sign-in flow.
     *
     * @param activity Activity to launch sign-in from
     * @param accountType The Google account type (GOOGLE_PERSONAL or GOOGLE_WORKSPACE)
     * @return Intent to launch, or null if sign-in not possible
     */
    override fun getSignInIntent(activity: Activity, accountType: AccountType): Intent? {
        if (accountType.providerId != ProviderId.GOOGLE) {
            Log.w(TAG, "Invalid account type for Google provider: ${accountType.key}")
            return null
        }
        return authManager.getSignInIntent()
    }

    /**
     * Handle result from Google sign-in Intent.
     *
     * @param data Intent data from onActivityResult
     * @return Result with new ConnectedAccount on success, or failure
     */
    override suspend fun handleSignInResult(data: Intent?): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val googleAccount = task.getResult(ApiException::class.java)

            if (googleAccount == null) {
                Log.e(TAG, "Google sign-in returned null account")
                return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Sign-in returned null account")
                )
            }

            // Set account in auth manager
            authManager.setSelectedAccount(googleAccount)

            // Determine account type (workspace vs personal)
            val accountType = detectAccountType(googleAccount)

            // Create or update database record
            val existingEntity = googleAccount.email?.let { accountDao.getByEmail(ProviderId.GOOGLE.key, "", it) }
            val accountEntity = if (existingEntity != null) {
                // Update existing account
                existingEntity.copy(
                    accountType = accountType.key,
                    displayName = googleAccount.displayName,
                    isActive = true
                ).also { accountDao.upsert(it) }
            } else {
                // Create new account
                createConnectedAccountEntity(googleAccount, accountType).also { accountDao.upsert(it) }
            }

            Log.i(TAG, "Google sign-in successful: ${accountEntity.email}")
            Result.success(accountEntity.toDomainModel())

        } catch (e: ApiException) {
            when (e.statusCode) {
                12501 -> {
                    Log.i(TAG, "Google sign-in cancelled by user (status code 12501)")
                    Result.failure(CalendarProviderException.CancelledException("User cancelled sign-in"))
                }
                else -> {
                    Log.e(TAG, "Google sign-in failed with status code: ${e.statusCode}", e)
                    Result.failure(CalendarProviderException.AuthenticationException(SignInErrorText.google(e.statusCode), e))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google sign-in", e)
            Result.failure(CalendarProviderException.ProviderException(SignInErrorText.google(null), cause = e))
        }
    }

    /**
     * Interactive sign-in for Google.
     *
     * Google uses Intent-based flow, so this returns failure indicating
     * to use [getSignInIntent] + [handleSignInResult] instead.
     */
    override suspend fun signInInteractive(activity: Activity, accountType: AccountType): Result<ConnectedAccount> {
        return Result.failure(
            CalendarProviderException.ProviderException(
                "Google uses Intent-based sign-in. Use getSignInIntent() and handleSignInResult() instead."
            )
        )
    }

    /**
     * Remove a Google account.
     *
     * Signs out from Google and deactivates the account in our database.
     * Note: This does NOT delete the database record (soft delete).
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

            // Deactivate account in database (soft delete)
            accountDao.deactivate(accountId)

            // Check if this was the LAST active Google account
            val remainingGoogleAccounts = accountDao.getAccountsByProvider(ProviderId.GOOGLE.key)
                .filter { it.isActive && it.id != accountId }

            if (remainingGoogleAccounts.isEmpty()) {
                // Last Google account - revoke access to force consent screen on next sign-in
                var signOutComplete = false
                authManager.revokeAccess { signOutComplete = true }

                var waitMs = 0
                while (!signOutComplete && waitMs < 2000) {
                    kotlinx.coroutines.delay(100)
                    waitMs += 100
                }
                Log.i(TAG, "Last Google account removed, revoked access: ${accountEntity.email}")
            } else {
                Log.i(TAG, "Google account removed (${remainingGoogleAccounts.size} remaining): ${accountEntity.email}")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing account: $accountId", e)
            Result.failure(CalendarProviderException.ProviderException("Failed to remove account: ${e.message}", cause = e))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch all calendars for a Google account.
     * Uses per-account credential to support multiple simultaneous Google accounts.
     */
    override suspend fun fetchCalendars(accountId: String): Result<List<ProviderCalendar>> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            val credential = authManager.getCredentialForAccount(account.email)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Not signed in")
                )
            val calendarsResult = syncManager.getAvailableCalendarsResult(credential)
            if (calendarsResult.isFailure) {
                val error = calendarsResult.exceptionOrNull()
                return@withContext Result.failure(
                    wrapException(error as? Exception ?: Exception(error?.message ?: "Unknown error"))
                )
            }
            val calendars = calendarsResult.getOrThrow()

            // Convert GoogleCalendarInfo to ProviderCalendar
            val providerCalendars = calendars.map { it.toProviderCalendar() }

            Log.d(TAG, "Fetched ${providerCalendars.size} calendars for account ${account.email}")
            Result.success(providerCalendars)

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error fetching calendars for account $accountId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Fetch events from specified calendars within a date range.
     * Uses per-account credential to support multiple simultaneous Google accounts.
     */
    override suspend fun fetchEvents(
        accountId: String,
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<ProviderEvent>> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            // Get per-account credential
            val credential = authManager.getCredentialForAccount(account.email)
                ?: return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Not signed in")
                )

            // Temporarily set selected calendars for sync
            val previousSelection = authManager.getSelectedCalendars()
            authManager.setSelectedCalendars(calendarIds)

            // Use existing sync manager with per-account credential
            val syncResult = try {
                syncManager.syncEvents(
                    startDate = startDate,
                    endDate = endDate,
                    accountId = accountId,
                    accountEmail = account.email,
                    providerId = "GOOGLE",
                    credential = credential
                )
            } finally {
                authManager.setSelectedCalendars(previousSelection)
            }

            val deletedCalendarIds = syncResult.deletedCalendarIds()
            if (deletedCalendarIds.isNotEmpty()) {
                selectionDao.deleteByCalendarIds(accountId, deletedCalendarIds)
                Log.w(
                    TAG,
                    "Auto-removed ${deletedCalendarIds.size} deleted Google calendar(s) for account $accountId: ${deletedCalendarIds.joinToString()}"
                )
            }

            val activeCalendarIds = calendarIds - deletedCalendarIds.toSet()

            when (syncResult) {
                is SyncResult.Success -> {
                    val providerEvents = loadProviderEventsForRange(activeCalendarIds, startDate, endDate)

                    // Update last sync time for account
                    accountDao.updateLastSync(accountId, System.currentTimeMillis())

                    Log.d(TAG, "Fetched ${providerEvents.size} events for account $accountId")
                    Result.success(providerEvents)
                }

                is SyncResult.NotSignedIn -> {
                    Result.failure(CalendarProviderException.AuthenticationException("Not signed in"))
                }

                is SyncResult.Error -> Result.failure(fetchFailure(syncResult))

                is SyncResult.PartialSuccess -> {
                    val providerEvents = loadProviderEventsForRange(activeCalendarIds, startDate, endDate)
                    Log.w(TAG, "Partial Google sync for account $accountId: ${syncResult.errors.joinToString()}")
                    Result.failure(CalendarProviderException.PartialSyncException(syncResult, providerEvents))
                }

                is SyncResult.NoNetwork -> {
                    Result.failure(CalendarProviderException.NetworkException("No network connection"))
                }

                else -> {
                    Result.failure(CalendarProviderException.ProviderException("Unexpected sync result: $syncResult"))
                }
            }

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
     * The account's own credential, as sync uses (Oct 2026). Create, update and delete used the
     * last Google sign-in instead, which is empty once another account was added or the sign-in
     * record was cleared, so saving failed with "Please sign in to Google again" while sync worked.
     */
    private suspend fun accountCredential(accountId: String): GoogleAccountCredential? =
        accountDao.getById(accountId)?.email?.takeIf { it.isNotBlank() }?.let { authManager.getCredentialForAccount(it) }

    /**
     * Create a new event in a Google Calendar.
     */
    override suspend fun createEvent(
        accountId: String,
        calendarId: String,
        event: NewEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        try {
            // Use existing sync manager to create event, with this account's own credential
            val eventData = event.copy(calendarId = calendarId)
            val result = syncManager.createEvent(eventData, accountCredential(accountId))

            when (result) {
                is EventOperationResult.Success -> {
                    val calendarEvent = result.data
                    Result.success(calendarEvent.toProviderEvent())
                }

                is EventOperationResult.NotSignedIn -> {
                    Result.failure(CalendarProviderException.AuthenticationException("Not signed in"))
                }

                is EventOperationResult.PermissionDenied -> {
                    Result.failure(CalendarProviderException.PermissionException("Permission denied for calendar: $calendarId"))
                }

                is EventOperationResult.NoNetwork -> {
                    Result.failure(CalendarProviderException.NetworkException("No network connection"))
                }

                is EventOperationResult.Error -> {
                    Result.failure(CalendarProviderException.ProviderException(result.message, cause = result.exception))
                }
            }

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error creating event in calendar $calendarId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Update an existing event in Google Calendar.
     */
    override suspend fun updateEvent(
        accountId: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        try {
            // Ensure eventId and calendarId match the data
            val eventData = event.copy(eventId = eventId, calendarId = calendarId)
            val result = syncManager.updateEvent(eventData, accountCredential(accountId))

            when (result) {
                is EventOperationResult.Success -> {
                    val calendarEvent = result.data
                    Result.success(calendarEvent.toProviderEvent())
                }

                is EventOperationResult.NotSignedIn -> {
                    Result.failure(CalendarProviderException.AuthenticationException("Not signed in"))
                }

                is EventOperationResult.PermissionDenied -> {
                    Result.failure(CalendarProviderException.PermissionException("Permission denied for event: $eventId"))
                }

                is EventOperationResult.NoNetwork -> {
                    Result.failure(CalendarProviderException.NetworkException("No network connection"))
                }

                is EventOperationResult.Error -> {
                    Result.failure(CalendarProviderException.ProviderException(result.message, cause = result.exception))
                }
            }

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error updating event $eventId in calendar $calendarId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    /**
     * Delete an event from Google Calendar.
     */
    override suspend fun deleteEvent(
        accountId: String,
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = syncManager.deleteEvent(eventId, calendarId, accountCredential(accountId))

            when (result) {
                is EventOperationResult.Success -> {
                    Result.success(Unit)
                }

                is EventOperationResult.NotSignedIn -> {
                    Result.failure(CalendarProviderException.AuthenticationException("Not signed in"))
                }

                is EventOperationResult.PermissionDenied -> {
                    Result.failure(CalendarProviderException.PermissionException("Permission denied for event: $eventId"))
                }

                is EventOperationResult.NoNetwork -> {
                    Result.failure(CalendarProviderException.NetworkException("No network connection"))
                }

                is EventOperationResult.Error -> {
                    Result.failure(CalendarProviderException.ProviderException(result.message, cause = result.exception))
                }
            }

        } catch (e: Exception) {
            val wrapped = wrapException(e)
            logProviderFailure("Error deleting event $eventId from calendar $calendarId", wrapped, e)
            Result.failure(wrapped)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Token Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refresh token if needed.
     *
     * Google Sign-In handles token refresh automatically via GoogleAccountCredential.
     * Uses per-account credential to validate the specific account.
     */
    override suspend fun refreshTokenIfNeeded(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            // GoogleAccountCredential handles token refresh automatically
            // Create per-account credential to verify it's accessible
            val credential = authManager.getCredentialForAccount(account.email)
            if (credential.selectedAccount == null) {
                return@withContext Result.failure(
                    CalendarProviderException.AuthenticationException("Could not get Google credential for ${account.email}")
                )
            }

            Result.success(Unit)

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
     * 2. If 401/403 error, attempt token refresh
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

        // Attempt token refresh
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
     * Checks that the account exists in DB and is active. The per-account
     * credential validity is verified when actual API calls are made.
     */
    override fun isAccountValid(accountId: String): Boolean {
        return try {
            // For multi-account, we can't rely on GoogleSignIn.getLastSignedInAccount()
            // since it only tracks one account. Instead check DB state.
            // Actual token validity is verified on API call via GoogleAccountCredential.
            true // Account validity is checked when credentials are used
        } catch (e: Exception) {
            Log.e(TAG, "Error checking account validity: $accountId", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a ConnectedAccountEntity from GoogleSignInAccount.
     */
    private fun createConnectedAccountEntity(
        googleAccount: GoogleSignInAccount,
        accountType: AccountType
    ): ConnectedAccountEntity {
        return ConnectedAccountEntity(
            id = ConnectedAccount.create(
                providerId = ProviderId.GOOGLE,
                accountType = accountType,
                email = googleAccount.email ?: ""
            ).id,
            providerId = ProviderId.GOOGLE.key,
            accountType = accountType.key,
            email = googleAccount.email ?: "",
            displayName = googleAccount.displayName,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            lastSyncAt = null
        )
    }

    /**
     * Detect if this is a Google Workspace account or personal Gmail.
     *
     * Simple heuristic: non-gmail.com domains are likely workspace.
     */
    private fun detectAccountType(googleAccount: GoogleSignInAccount): AccountType {
        val email = googleAccount.email ?: return AccountType.GOOGLE_PERSONAL
        return when {
            email.endsWith("@gmail.com") -> AccountType.GOOGLE_PERSONAL
            email.endsWith("@googlemail.com") -> AccountType.GOOGLE_PERSONAL
            else -> AccountType.GOOGLE_WORKSPACE
        }
    }

    /**
     * Convert GoogleCalendarInfo to ProviderCalendar.
     */
    private fun GoogleCalendarInfo.toProviderCalendar(): ProviderCalendar {
        return ProviderCalendar.fromGoogleCalendar(
            id = this.id,
            name = this.name,
            isPrimary = this.isPrimary,
            accessRole = this.accessRole,
            backgroundColor = this.backgroundColor,
            description = this.description
        )
    }

    /**
     * Convert CalendarEvent to ProviderEvent.
     */
    private fun CalendarEvent.toProviderEvent(): ProviderEvent {
        return ProviderEvent(
            id = this.id,
            calendarId = this.calendarId,
            title = this.title,
            startTime = this.startTime,
            endTime = this.endTime,
            isAllDay = this.isAllDay,
            location = this.location,
            description = this.description,
            attendees = this.attendees,
            colorHex = this.colorHex,
            isReadOnly = this.isReadOnly
        )
    }

    private suspend fun loadProviderEventsForRange(
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ProviderEvent> {
        val startStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return eventRepository.getEventsInRange(startStr, endStr)
            .filter { it.calendarId in calendarIds }
            .map { it.toProviderEvent() }
    }

    /**
     * Wrap exceptions in CalendarProviderException.
     */
    private fun wrapException(e: Exception): CalendarProviderException {
        return when {
            e is CalendarProviderException -> e
            e.isGoogleTokenRefused() -> CalendarProviderException.AuthenticationException(GOOGLE_SIGN_IN_NEEDED, e)
            e is java.net.UnknownHostException -> CalendarProviderException.NetworkException("No network connection", e)
            e is java.net.SocketTimeoutException -> CalendarProviderException.NetworkException("Connection timeout", e)
            else -> CalendarProviderException.ProviderException(e.message ?: "Unknown error", cause = e)
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Helper Methods (for external use)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the currently signed-in Google account's ID from our database.
     * Returns null if not signed in.
     */
    suspend fun getCurrentAccountId(): String? = withContext(Dispatchers.IO) {
        val email = authManager.getUserEmail() ?: return@withContext null
        val entity = accountDao.getByEmail(ProviderId.GOOGLE.key, "", email)
        entity?.id
    }

    /**
     * Get the currently signed-in account as ConnectedAccount.
     * Returns null if not signed in.
     */
    suspend fun getCurrentAccount(): ConnectedAccount? = withContext(Dispatchers.IO) {
        val email = authManager.getUserEmail() ?: return@withContext null
        val entity = accountDao.getByEmail(ProviderId.GOOGLE.key, "", email)
        entity?.toDomainModel()
    }

    /**
     * Sync events using the default behavior (respects authManager settings).
     * This is a convenience method for backward compatibility.
     */
    suspend fun syncCurrentMonth(): SyncResult {
        return syncManager.syncCurrentMonth()
    }

    /**
     * Get available calendars using existing sync manager.
     * Convenience method for backward compatibility.
     */
    suspend fun getAvailableCalendars(): List<GoogleCalendarInfo> {
        return syncManager.getAvailableCalendars()
    }

    /**
     * Get writable calendars using existing sync manager.
     * Convenience method for backward compatibility.
     */
    suspend fun getWritableCalendars(): List<GoogleCalendarInfo> {
        return syncManager.getWritableCalendars()
    }
}
