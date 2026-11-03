package com.boox.day.sync

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.CalendarSelection
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.sync.caldav.CalDavSignInOutcome
import com.boox.day.sync.caldav.calDavSignInOutcome
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.provider.CalendarProviderException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel for multi-account calendar sync UI.
 *
 * Provides:
 * - Reactive state via [uiState] StateFlow
 * - Account management (add, remove)
 * - Calendar selection (enable/disable, set default)
 * - Sync operations (sync all, sync individual)
 *
 * ## UI State Design
 *
 * The [UiState] provides all data needed by the UI:
 * - Accounts grouped by provider
 * - Per-account calendars and sync status
 * - Global loading/error states
 *
 * ## Usage Pattern
 *
 * ```kotlin
 * val viewModel: MultiAccountSyncViewModel by viewModels()
 *
 * LaunchedEffect(Unit) {
 *     viewModel.loadAccounts()
 * }
 *
 * val uiState by viewModel.uiState.collectAsState()
 *
 * // Display accounts
 * uiState.googleAccounts.forEach { account ->
 *     AccountItem(
 *         account = account,
 *         onRemove = { viewModel.removeAccount(account.account.id) },
 *         onToggleCalendar = { calId -> viewModel.toggleCalendarEnabled(account.account.id, calId) }
 *     )
 * }
 *
 * // Add account button
 * Button(onClick = { viewModel.addOutlookAccount(activity, AccountType.OUTLOOK_PERSONAL) })
 * ```
 *
 * @param syncManager The multi-account sync manager
 * @param googleAuthManager Legacy Google auth manager (for backward compatibility)
 * @param outlookAuthManager Outlook auth manager (for MSAL initialization check)
 */
class MultiAccountSyncViewModel(
    private val syncManager: MultiAccountSyncManager,
    private val googleAuthManager: GoogleAuthManager,
    private val outlookAuthManager: OutlookAuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "MultiAccountSyncVM"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI State
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Phases of the sync operation, reported for UI progress feedback.
     */
    enum class SyncPhase {
        /** Pulling events from Google/Outlook API */
        FETCHING,
        /** Running semantic indexing for Smart Link */
        INDEXING
    }

    /**
     * Progress of a sync operation for a single account.
     * Used to drive phase-aware UI feedback (button text, per-account status).
     */
    data class SyncProgress(
        val phase: SyncPhase,
        val current: Int = 0,
        val total: Int = 0
    )

    /**
     * Complete UI state for the multi-account sync screen.
     */
    data class UiState(
        val googleAccounts: List<AccountWithCalendars> = emptyList(),
        val outlookAccounts: List<AccountWithCalendars> = emptyList(),
        /** iCloud, Fastmail, Nextcloud and other CalDAV servers (plan stage 3, the "iCloud & more" tab). */
        val calDavAccounts: List<AccountWithCalendars> = emptyList(),
        val isLoading: Boolean = false,
        val isSyncing: Boolean = false,
        /** Aggregate sync progress across all accounts (driven by the most active account) */
        val syncProgress: SyncProgress? = null,
        val syncResults: Map<String, SyncResult> = emptyMap(),
        val error: String? = null,
        val errorDismissible: Boolean = true,
        /**
         * Google sign-in in progress (Oct 2026): from the moment it starts until its result is handled.
         * After the account picker closes, Google keeps working for a few seconds before the result
         * reaches the app; without this the Google tab showed "No Google accounts connected" meanwhile.
         */
        val isConnectingGoogle: Boolean = false
    ) {
        /**
         * All accounts combined.
         */
        val allAccounts: List<AccountWithCalendars>
            get() = googleAccounts + outlookAccounts + calDavAccounts

        /**
         * Total number of accounts.
         */
        val accountCount: Int
            get() = googleAccounts.size + outlookAccounts.size + calDavAccounts.size

        /** Every account changed by [transform] (all three lists). */
        fun mapAccounts(transform: (AccountWithCalendars) -> AccountWithCalendars): UiState = copy(
            googleAccounts = googleAccounts.map(transform),
            outlookAccounts = outlookAccounts.map(transform),
            calDavAccounts = calDavAccounts.map(transform)
        )

        /**
         * Check if any account is currently syncing.
         */
        val hasActiveSyncs: Boolean
            get() = allAccounts.any { it.isSyncing }

        /**
         * Check if there are any errors in sync results.
         */
        val hasErrors: Boolean
            get() = syncResults.values.any { it.isError() || it is SyncResult.NotSignedIn }
    }

    /**
     * Account with its calendars and status.
     */
    data class AccountWithCalendars(
        val account: ConnectedAccount,
        val calendars: List<CalendarSelection> = emptyList(),
        val isExpanded: Boolean = true,
        val isSyncing: Boolean = false,
        /** Current sync progress for this account (phase + counts) */
        val syncProgress: SyncProgress? = null,
        val isLoadingCalendars: Boolean = false,
        val lastSyncMessage: String? = null,
        val error: String? = null,
        /**
         * True if the account needs re-authentication.
         * This happens when token refresh fails.
         */
        val needsReauth: Boolean = false
    ) {
        /**
         * Count of enabled calendars.
         */
        val enabledCalendarCount: Int
            get() = calendars.count { it.isFetchEnabled }

        /**
         * The default calendar for this account.
         */
        val defaultCalendar: CalendarSelection?
            get() = calendars.find { it.isDefaultForAccount }

        /**
         * Whether this account has an error state (either error message or needs reauth).
         */
        val hasError: Boolean
            get() = error != null || needsReauth
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Track pending sign-in for handleActivityResult
    private var pendingSignInProvider: ProviderId? = null
    private var pendingSignInAccountType: AccountType? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        // Initialize Outlook MSAL on ViewModel creation
        viewModelScope.launch {
            outlookAuthManager.initialize()
        }
    }

    /**
     * Load all accounts and their calendars.
     * Call this when the UI first appears.
     * Returns the Job so callers can join() if they need to wait for completion.
     */
    fun loadAccounts(): kotlinx.coroutines.Job = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val googleAccounts = syncManager.getAccountsByProvider(ProviderId.GOOGLE)
                val outlookAccounts = syncManager.getAccountsByProvider(ProviderId.OUTLOOK)
                val calDavAccounts = syncManager.getAccountsByProvider(ProviderId.CALDAV)

                // Load calendars for each account
                val googleWithCalendars = googleAccounts.map { account ->
                    val calendars = syncManager.getCalendarsForAccount(account.id)
                    AccountWithCalendars(
                        account = account,
                        calendars = calendars
                    )
                }

                val outlookWithCalendars = outlookAccounts.map { account ->
                    val calendars = syncManager.getCalendarsForAccount(account.id)
                    AccountWithCalendars(
                        account = account,
                        calendars = calendars
                    )
                }

                val calDavWithCalendars = calDavAccounts.map { account ->
                    AccountWithCalendars(account = account, calendars = syncManager.getCalendarsForAccount(account.id))
                }

                com.boox.day.data.model.AccountServiceNames.update(googleAccounts + outlookAccounts + calDavAccounts)
                _uiState.update {
                    it.copy(
                        googleAccounts = keepSyncStatus(googleWithCalendars, it.googleAccounts),
                        outlookAccounts = keepSyncStatus(outlookWithCalendars, it.outlookAccounts),
                        calDavAccounts = keepSyncStatus(calDavWithCalendars, it.calDavAccounts),
                        isLoading = false
                    )
                }

                Log.d(TAG, "Loaded ${googleWithCalendars.size} Google, ${outlookWithCalendars.size} Outlook, ${calDavWithCalendars.size} CalDAV accounts")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading accounts", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load accounts: ${e.message}"
                    )
                }
            }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Start Google account sign-in flow.
     * Returns Intent to launch with startActivityForResult.
     * After activity result, call [handleGoogleSignInResult].
     *
     * This is a suspend function because when a Google account is already connected,
     * we need to sign out of GoogleSignInClient first to force the account picker
     * to appear (otherwise it auto-selects the existing account).
     *
     * @param activity Activity context
     * @param accountType GOOGLE_PERSONAL or GOOGLE_WORKSPACE
     * @return Intent to launch, or null if blocked
     */
    suspend fun getGoogleSignInIntent(activity: Activity, accountType: AccountType = AccountType.GOOGLE_PERSONAL): Intent? {
        if (accountType.providerId != ProviderId.GOOGLE) {
            Log.w(TAG, "Invalid account type for Google sign-in: ${accountType.key}")
            return null
        }


        pendingSignInProvider = ProviderId.GOOGLE
        pendingSignInAccountType = accountType
        _uiState.update { it.copy(isConnectingGoogle = true, error = null) }

        // Check if a Google account is already connected. If so, we need to force
        // the account picker by signing out of GoogleSignInClient first.
        // This does NOT invalidate existing account tokens in AccountManager.
        val hasExistingGoogleAccount = _uiState.value.googleAccounts.isNotEmpty()

        val intent = try {
            if (hasExistingGoogleAccount) {
                Log.i(TAG, "Existing Google account found, forcing account picker for new account")
                googleAuthManager.getSignInIntentForNewAccount()
            } else {
                syncManager.getSignInIntent(activity, accountType)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isConnectingGoogle = false) }
            throw e
        }
        if (intent == null) {
            _uiState.update { it.copy(isConnectingGoogle = false) }
        }
        return intent
    }

    /**
     * Handle Google sign-in activity result.
     * Call this from onActivityResult. [resultCode]: a cancelled sign-in can come back with no data,
     * which GoogleSignIn reports as an internal error (code 8) instead of a cancel.
     */
    fun handleGoogleSignInResult(data: Intent?, resultCode: Int = Activity.RESULT_OK) {
        if (pendingSignInProvider != ProviderId.GOOGLE) {
            Log.w(TAG, "No pending Google sign-in")
            _uiState.update { it.copy(isConnectingGoogle = false) }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val result = if (resultCode == Activity.RESULT_CANCELED && data == null) {
                    Result.failure(CalendarProviderException.CancelledException("User cancelled sign-in"))
                } else {
                    syncManager.handleSignInResult(ProviderId.GOOGLE, data)
                }

                if (result.isSuccess) {
                    val account = result.getOrThrow()
                    Log.i(TAG, "Google sign-in successful: ${account.email}")

                    // Refresh calendars for the new account
                    val calendarsResult = syncManager.refreshCalendarsForAccount(account.id)
                    if (calendarsResult.isFailure) {
                        Log.e(TAG, "Failed to refresh calendars for ${account.email}: ${calendarsResult.exceptionOrNull()?.message}")
                    } else {
                        val calendars = calendarsResult.getOrThrow()
                        val enabledCount = calendars.count { it.isFetchEnabled }
                        Log.i(TAG, "Refreshed ${calendars.size} calendars for ${account.email}, $enabledCount enabled for sync")
                    }

                    // Reload all accounts to update UI - MUST wait for completion
                    // so that syncAccount() can find the account in state and set isSyncing=true
                    loadAccounts().join()

                    // AUTO-SYNC: Trigger initial sync for the newly added account
                    // This fetches events from the calendar and populates the Events grid
                    Log.i(TAG, "Starting initial sync for newly added account: ${account.email}")
                    syncAccount(account.id)
                } else {
                    val error = result.exceptionOrNull()
                    // The provider's text is already the full message (SignInErrorText). A cancel is
                    // said too (Oct 2026): showing nothing looked like a sign-in loop.
                    val message = if (error is CalendarProviderException.CancelledException) {
                        SignInErrorText.google(SignInErrorText.CANCELLED)
                    } else {
                        error?.message ?: SignInErrorText.google(null)
                    }
                    _uiState.update { it.copy(isLoading = false, error = message) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling Google sign-in result", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Sign-in error: ${e.message}"
                    )
                }
            } finally {
                pendingSignInProvider = null
                pendingSignInAccountType = null
                _uiState.update { it.copy(isConnectingGoogle = false) }
            }
        }
    }

    /**
     * Start Outlook account sign-in flow.
     * This uses MSAL's callback-based flow (not Intent-based).
     *
     * @param activity Activity context
     * @param accountType OUTLOOK_PERSONAL or OUTLOOK_WORK_SCHOOL
     */
    fun addOutlookAccount(activity: Activity, accountType: AccountType = AccountType.OUTLOOK_PERSONAL) {
        if (accountType.providerId != ProviderId.OUTLOOK) {
            Log.w(TAG, "Invalid account type for Outlook sign-in: ${accountType.key}")
            return
        }


        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val result = syncManager.addAccountInteractive(activity, accountType)

                if (result.isSuccess) {
                    val account = result.getOrThrow()
                    Log.i(TAG, "Outlook sign-in successful: ${account.email}")

                    // Reload all accounts to update UI - MUST wait for completion
                    // so that syncAccount() can find the account in state and set isSyncing=true
                    loadAccounts().join()

                    // AUTO-SYNC: Trigger initial sync for the newly added account
                    // This fetches events from the calendar and populates the Events grid
                    Log.i(TAG, "Starting initial sync for newly added account: ${account.email}")
                    syncAccount(account.id)
                } else {
                    val error = result.exceptionOrNull()
                    if (error is CalendarProviderException.CancelledException) {
                        // User cancelled - don't show error
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        // The provider's text is already a full message ("Microsoft sign-in failed …")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error?.message ?: "Microsoft sign-in failed"
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during Outlook sign-in", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Sign-in error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Sign in to a CalDAV account from the "iCloud & more" form (plan step 3.6), or again to one whose
     * password the server refused (same server and name: same account, its choices kept). [trust] is
     * a certificate the user has just trusted; otherwise the one trusted before is used. A new
     * account is synced at once, like a new Google or Outlook account.
     */
    internal suspend fun signInCalDav(
        type: AccountType,
        address: String,
        name: String,
        password: String,
        trust: String? = null,
        choice: com.boox.day.sync.caldav.CalDavUseChoice? = null
    ): CalDavSignInOutcome {
        val before = syncManager.trustedCalDavCertificate(address, name)
        val outcome = calDavSignInOutcome(syncManager.addCalDavAccount(type, address, name, password, trust ?: before, choice), type, before)
        if (outcome is CalDavSignInOutcome.Connected) {
            Log.i(TAG, "CalDAV sign-in successful (${type.key})")
            loadAccounts().join()
            syncAccount(outcome.account.id)
        }
        return outcome
    }

    /**
     * Remove an account.
     *
     * @param accountId The account to remove
     */
    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            try {
                updateAccount(accountId) { it.copy(isSyncing = true) }

                val result = syncManager.removeAccount(accountId)

                if (result.isSuccess) {
                    Log.i(TAG, "Account removed: $accountId")
                    loadAccounts()
                } else {
                    val error = result.exceptionOrNull()
                    updateAccount(accountId) {
                        it.copy(
                            isSyncing = false,
                            error = "Failed to remove: ${error?.message}"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error removing account: $accountId", e)
                updateAccount(accountId) {
                    it.copy(
                        isSyncing = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Disconnect all connected accounts.
     * Used when switching from Hybrid → Standalone mode.
     * This removes all Google and Outlook accounts but does NOT clear cached events.
     * Event clearing should be done separately via EventsViewModel.clearAllSyncedEvents().
     */
    suspend fun disconnectAllAccounts() {
        val state = _uiState.value
        val allAccountIds = state.allAccounts.map { it.account.id }

        if (allAccountIds.isEmpty()) {
            Log.d(TAG, "No accounts to disconnect")
            return
        }

        Log.i(TAG, "Disconnecting ${allAccountIds.size} accounts")
        _uiState.update { it.copy(isLoading = true, error = null) }

        var successCount = 0
        var failCount = 0

        for (accountId in allAccountIds) {
            try {
                val result = syncManager.removeAccount(accountId)
                if (result.isSuccess) {
                    successCount++
                    Log.d(TAG, "Disconnected account: $accountId")
                } else {
                    failCount++
                    Log.e(TAG, "Failed to disconnect account $accountId: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "Error disconnecting account: $accountId", e)
            }
        }

        Log.i(TAG, "Disconnected $successCount accounts, $failCount failed")

        // Reload accounts (should be empty now)
        loadAccounts()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refresh calendars for an account from the provider.
     */
    fun refreshCalendars(accountId: String) {
        viewModelScope.launch {
            try {
                updateAccount(accountId) { it.copy(isLoadingCalendars = true, error = null) }

                val result = syncManager.refreshCalendarsForAccount(accountId)

                if (result.isSuccess) {
                    val calendars = result.getOrThrow()
                    updateAccount(accountId) {
                        it.copy(
                            calendars = calendars,
                            isLoadingCalendars = false
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()
                    updateAccount(accountId) {
                        it.copy(
                            isLoadingCalendars = false,
                            error = "Failed to refresh: ${error?.message}"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing calendars for $accountId", e)
                updateAccount(accountId) {
                    it.copy(
                        isLoadingCalendars = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Toggle calendar enabled/disabled for sync.
     */
    fun toggleCalendarEnabled(accountId: String, calendarId: String) {
        viewModelScope.launch {
            try {
                val account = findAccount(accountId) ?: return@launch
                val calendar = account.calendars.find { it.calendarId == calendarId } ?: return@launch

                val newEnabled = !calendar.isFetchEnabled
                syncManager.toggleCalendarEnabled(accountId, calendarId, newEnabled)

                // Update local state
                updateAccount(accountId) { acc ->
                    acc.copy(
                        calendars = acc.calendars.map { cal ->
                            if (cal.calendarId == calendarId) {
                                cal.copy(isFetchEnabled = newEnabled)
                            } else cal
                        }
                    )
                }

                Log.d(TAG, "Toggled calendar $calendarId to $newEnabled")

            } catch (e: Exception) {
                Log.e(TAG, "Error toggling calendar", e)
            }
        }
    }

    /**
     * Set a calendar as the default for its account.
     */
    fun setDefaultCalendar(accountId: String, calendarId: String) {
        viewModelScope.launch {
            try {
                val account = findAccount(accountId) ?: return@launch
                val calendar = account.calendars.find { it.calendarId == calendarId } ?: return@launch

                syncManager.setDefaultCalendar(accountId, calendar.id)

                // Update local state
                updateAccount(accountId) { acc ->
                    acc.copy(
                        calendars = acc.calendars.map { cal ->
                            cal.copy(isDefaultForAccount = cal.calendarId == calendarId)
                        }
                    )
                }

                Log.d(TAG, "Set default calendar: $calendarId for account $accountId")

            } catch (e: Exception) {
                Log.e(TAG, "Error setting default calendar", e)
            }
        }
    }

    /**
     * Toggle account expanded/collapsed state in UI.
     */
    fun toggleAccountExpanded(accountId: String) {
        updateAccount(accountId) { it.copy(isExpanded = !it.isExpanded) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sync Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync all accounts.
     * Each account syncs independently - one failure doesn't affect others.
     */
    fun syncAllAccounts() {
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    state.mapAccounts { it.copy(isSyncing = true, syncProgress = null, error = null) }.copy(
                        isSyncing = true,
                        syncProgress = SyncProgress(SyncPhase.FETCHING),
                        error = null
                    )
                }

                // Sync 6-month window: 2 months past + current + 3 months future
                val now = LocalDate.now()
                val startDate = now.minusMonths(SyncScheduler.DEFAULT_MONTHS_BEFORE.toLong())
                val endDate = now.plusMonths(SyncScheduler.DEFAULT_MONTHS_AFTER.toLong())

                val results = syncManager.syncAllAccounts(startDate, endDate) { accountId, progress ->
                    // Update per-account progress and propagate to global state
                    updateAccount(accountId) { it.copy(syncProgress = progress) }
                    _uiState.update { it.copy(syncProgress = progress) }
                }

                // Update state with results, detecting auth errors and feature gating
                _uiState.update { state ->
                    state.mapAccounts { acc ->
                        val result = results[acc.account.id]
                        val needsReauth = result is SyncResult.NotSignedIn ||
                                (result is SyncResult.Error && result.errorType == SyncErrorType.AUTHENTICATION)
                        acc.copy(
                            isSyncing = false,
                            syncProgress = null,
                            lastSyncMessage = result?.getDisplayMessage(),
                            needsReauth = needsReauth,
                            error = if (result?.isError() == true && !needsReauth) result.getDisplayMessage() else null
                        )
                    }.copy(
                        isSyncing = false,
                        syncProgress = null,
                        syncResults = results
                    )
                }

                Log.i(TAG, "Sync complete for ${results.size} accounts")

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing all accounts", e)
                _uiState.update { state ->
                    state.mapAccounts { it.copy(isSyncing = false, syncProgress = null) }.copy(
                        isSyncing = false,
                        syncProgress = null,
                        error = "Sync failed: ${e.message}"
                    )
                }
            }
        }
    }

    /** "Use for: Calendars" on a CalDAV account (owner decision 78): on, its calendars are read and synced again. */
    fun setCalDavCalendarsOn(accountId: String, on: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        syncManager.setCalDavCalendarsOn(accountId, on)
        loadAccounts().join()
        if (on) syncAccount(accountId)
    }

    /**
     * Sync a single account.
     */
    fun syncAccount(accountId: String) {
        viewModelScope.launch {
            try {
                // Set GLOBAL isSyncing to true so SyncStatusBanner shows feedback messages
                // This is critical for first-time account sync after sign-in
                _uiState.update { it.copy(isSyncing = true, syncProgress = SyncProgress(SyncPhase.FETCHING), error = null) }
                updateAccount(accountId) { it.copy(isSyncing = true, syncProgress = null, error = null, needsReauth = false) }

                // Sync 6-month window: 2 months past + current + 3 months future
                val now = LocalDate.now()
                val startDate = now.minusMonths(SyncScheduler.DEFAULT_MONTHS_BEFORE.toLong())
                val endDate = now.plusMonths(SyncScheduler.DEFAULT_MONTHS_AFTER.toLong())

                val result = syncManager.syncAccount(accountId, startDate, endDate) { progress ->
                    updateAccount(accountId) { it.copy(syncProgress = progress) }
                    _uiState.update { it.copy(syncProgress = progress) }
                }

                // Detect auth errors
                val needsReauth = result is SyncResult.NotSignedIn ||
                        (result is SyncResult.Error && result.errorType == SyncErrorType.AUTHENTICATION)


                updateAccount(accountId) {
                    it.copy(
                        isSyncing = false,
                        syncProgress = null,
                        lastSyncMessage = result.getDisplayMessage(),
                        needsReauth = needsReauth,
                        error = if (result.isError() && !needsReauth) result.getDisplayMessage() else null
                    )
                }

                // Update sync results and clear GLOBAL isSyncing state
                _uiState.update { state ->
                    state.copy(
                        isSyncing = false,
                        syncProgress = null,
                        syncResults = state.syncResults + (accountId to result)
                    )
                }

                Log.i(TAG, "Sync complete for $accountId: ${result.getDisplayMessage()}")

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing account: $accountId", e)
                // Check if the exception indicates auth error
                val isAuthError = e.message?.contains("auth", ignoreCase = true) == true ||
                        e.message?.contains("sign in", ignoreCase = true) == true
                updateAccount(accountId) {
                    it.copy(
                        isSyncing = false,
                        syncProgress = null,
                        needsReauth = isAuthError,
                        error = if (!isAuthError) "Sync failed: ${e.message}" else null
                    )
                }
                // Clear GLOBAL isSyncing state on error too
                _uiState.update { it.copy(isSyncing = false, syncProgress = null) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Handling
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Dismiss the global error message.
     */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Dismiss error for a specific account.
     */
    fun dismissAccountError(accountId: String) {
        updateAccount(accountId) { it.copy(error = null) }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find an account in the current state.
     */
    private fun findAccount(accountId: String): AccountWithCalendars? {
        return _uiState.value.allAccounts.find { it.account.id == accountId }
    }

    /**
     * Update a specific account in the state.
     */
    private fun updateAccount(
        accountId: String,
        transform: (AccountWithCalendars) -> AccountWithCalendars
    ) {
        _uiState.update { state ->
            state.mapAccounts { acc -> if (acc.account.id == accountId) transform(acc) else acc }
        }
    }
}

/**
 * Accounts reloaded by [MultiAccountSyncViewModel.loadAccounts] keep what the last sync said about
 * them: "sign in again" ([MultiAccountSyncViewModel.AccountWithCalendars.needsReauth]), its error and
 * its message. Opening Calendar Accounts reloads them; until Oct 2026 that dropped needsReauth, so the
 * Re-auth button and the Settings gear's reconnect dot vanished as soon as the window opened.
 * A new sign-in still clears it: syncAccount starts by setting needsReauth back to false.
 */
internal fun keepSyncStatus(
    loaded: List<MultiAccountSyncViewModel.AccountWithCalendars>,
    shown: List<MultiAccountSyncViewModel.AccountWithCalendars>
): List<MultiAccountSyncViewModel.AccountWithCalendars> {
    val before = shown.associateBy { it.account.id }
    return loaded.map { account ->
        val last = before[account.account.id] ?: return@map account
        account.copy(needsReauth = last.needsReauth, error = last.error, lastSyncMessage = last.lastSyncMessage)
    }
}
