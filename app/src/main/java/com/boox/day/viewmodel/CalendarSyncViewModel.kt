package com.boox.day.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boox.day.BuildConfig
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.sync.CalendarSyncManager
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.GoogleCalendarInfo
import com.boox.day.sync.SyncResult
import com.boox.day.sync.SyncScheduler
import com.boox.day.sync.deletedCalendarIds
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * ViewModel for managing Google Calendar sync operations.
 * Handles OAuth authentication, calendar selection, and sync state.
 */
class CalendarSyncViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CalendarSyncViewModel"
    }

    private val authManager = GoogleAuthManager(application)
    private val eventRepository = CalendarEventRepository(application)
    private val syncManager = CalendarSyncManager(application, authManager, eventRepository)

    // Authentication state
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // Sync state
    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    // Sync preferences
    private val _autoSyncEnabled = MutableStateFlow(true)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _syncFrequency = MutableStateFlow(GoogleAuthManager.FREQUENCY_1_HOUR)
    val syncFrequency: StateFlow<String> = _syncFrequency.asStateFlow()

    // Calendar selection
    private val _availableCalendars = MutableStateFlow<List<GoogleCalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<GoogleCalendarInfo>> = _availableCalendars.asStateFlow()

    private val _selectedCalendars = MutableStateFlow<Set<String>>(emptySet())
    val selectedCalendars: StateFlow<Set<String>> = _selectedCalendars.asStateFlow()

    // Writable calendars (for event creation)
    val writableCalendars: StateFlow<List<GoogleCalendarInfo>>
        get() = MutableStateFlow(_availableCalendars.value.filter { it.isWritable })

    // Month sync cache (per app session)
    private val syncedMonths = mutableSetOf<String>()
    private val monthSyncTimestamps = mutableMapOf<String, Long>()

    // Startup sync state (for splash dialog)
    private val _startupSyncInProgress = MutableStateFlow(false)
    val startupSyncInProgress: StateFlow<Boolean> = _startupSyncInProgress.asStateFlow()

    // Last successful sync time (for UI)
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        checkSignInStatus()
        loadSyncPreferences()

        viewModelScope.launch {
            // Skip legacy sync if multi-account system is active —
            // MultiAccountSyncViewModel handles startup sync via MainScreen LaunchedEffect
            if (hasMultiAccountAccounts()) {
                Log.d(TAG, "Skipping legacy startup sync - multi-account system active")
                return@launch
            }

            if (_isSignedIn.value) {
                // Load available calendars first (for calendar selection)
                loadAvailableCalendars()

                // Smart sync: Check last sync time to decide sync strategy
                val lastSync = authManager.getLastSyncTime()
                val elapsed = System.currentTimeMillis() - lastSync
                val elapsedMinutes = elapsed / 60_000L

                when {
                    elapsedMinutes < 15 -> {
                        // Synced within 15 minutes - skip sync entirely (user just reopened)
                        Log.d(TAG, "Skipping startup sync - last sync was ${elapsedMinutes}min ago")
                        _lastSyncMessage.value = null
                    }
                    elapsedMinutes < 60 -> {
                        // Synced within hour - silent background sync (no indicator)
                        Log.d(TAG, "Silent background sync - last sync was ${elapsedMinutes}min ago")
                        _lastSyncMessage.value = null
                        performStartupMonthRangeSync(
                            monthsBefore = SyncScheduler.DEFAULT_MONTHS_BEFORE,
                            monthsAfter = SyncScheduler.DEFAULT_MONTHS_AFTER
                        )
                    }
                    else -> {
                        // Stale data (> 1 hour) or never synced - sync with indicator
                        Log.d(TAG, "Full startup sync - last sync was ${elapsedMinutes}min ago")
                        performStartupMonthRangeSync(
                            monthsBefore = SyncScheduler.DEFAULT_MONTHS_BEFORE,
                            monthsAfter = SyncScheduler.DEFAULT_MONTHS_AFTER
                        )
                    }
                }
            }
        }

        startAutoSyncLoop()
    }

    /**
     * Perform startup sync for a range of months around current month.
     * Uses SyncScheduler defaults: 2 months past + current + 3 months future = 6 months total.
     */
    private suspend fun performStartupMonthRangeSync(
        monthsBefore: Int = SyncScheduler.DEFAULT_MONTHS_BEFORE,
        monthsAfter: Int = SyncScheduler.DEFAULT_MONTHS_AFTER
    ) {
        if (_syncInProgress.value) {
            Log.d(TAG, "Startup sync skipped, another sync in progress")
            return
        }

        _syncInProgress.value = true
        _startupSyncInProgress.value = true
        _lastSyncResult.value = SyncResult.InProgress
        _lastSyncMessage.value = "Syncing..."

        val results = syncManager.syncMonthRange(monthsBefore, monthsAfter)

        var totalEvents = 0
        var totalNew = 0
        var totalUpdated = 0
        var successfulMonths = 0
        var partialSuccessCount = 0
        var partialFailureCount = 0
        val partialErrors = mutableListOf<String>()
        val deletedCalendarIds = linkedSetOf<String>()
        var terminalFailure: SyncResult? = null

        results.forEach { (key, result) ->
            deletedCalendarIds += result.deletedCalendarIds()
            when (result) {
                is SyncResult.Success -> {
                    totalEvents += result.eventCount
                    totalNew += result.newEvents
                    totalUpdated += result.updatedEvents
                    successfulMonths++
                    markMonthSynced(key)
                }
                is SyncResult.PartialSuccess -> {
                    partialSuccessCount += result.successCount
                    partialFailureCount += result.failureCount
                    partialErrors += result.errors
                }
                is SyncResult.NotSignedIn -> {
                    if (terminalFailure == null) {
                        terminalFailure = result
                    }
                }
                is SyncResult.NoNetwork -> {
                    if (terminalFailure == null) {
                        terminalFailure = result
                    }
                }
                is SyncResult.Error -> {
                    if (terminalFailure == null) {
                        terminalFailure = result
                    }
                }
                else -> {}
            }
        }

        applyDeletedCalendarCleanup(deletedCalendarIds.toList())

        val startupResult: SyncResult? = when {
            partialFailureCount > 0 || (successfulMonths > 0 && terminalFailure != null) -> {
                SyncResult.PartialSuccess(
                    successCount = totalEvents + partialSuccessCount,
                    failureCount = partialFailureCount + if (successfulMonths > 0 && terminalFailure != null) 1 else 0,
                    errors = (
                        partialErrors +
                            listOfNotNull(terminalFailure?.takeIf { successfulMonths > 0 }?.getDisplayMessage())
                        ).distinct()
                )
            }
            successfulMonths > 0 -> {
                SyncResult.Success(
                    eventCount = totalEvents,
                    newEvents = totalNew,
                    updatedEvents = totalUpdated
                )
            }
            else -> terminalFailure
        }

        when (startupResult) {
            is SyncResult.Success -> {
            val message = when {
                totalNew == 0 && totalUpdated == 0 ->
                    "Events up to date ($totalEvents total across $successfulMonths months)"
                totalNew > 0 && totalUpdated > 0 ->
                    "Synced $successfulMonths months: $totalNew new, $totalUpdated updated"
                totalNew > 0 ->
                    "Synced $successfulMonths months: $totalNew new events"
                totalUpdated > 0 ->
                    "Updated $successfulMonths months: $totalUpdated events"
                else ->
                    "Synced $successfulMonths months ($totalEvents events)"
            }

            _lastSyncResult.value = startupResult
            _lastSyncMessage.value = message
            Log.d(TAG, "Startup range sync complete: $message")
        }
            is SyncResult.PartialSuccess -> {
                _lastSyncResult.value = startupResult
                _lastSyncMessage.value = startupResult.getDisplayMessage()
                Log.w(TAG, "Startup range sync partial: ${startupResult.errors.joinToString()}")
            }
            is SyncResult.NoNetwork -> {
                _lastSyncResult.value = startupResult
                _lastSyncMessage.value = startupResult.toDisplayMessage()
                Log.w(TAG, "Startup range sync blocked by network loss")
            }
            is SyncResult.Error -> {
                _lastSyncResult.value = startupResult
                _lastSyncMessage.value = "Sync failed: ${startupResult.message}"
                Log.e(TAG, "Startup range sync failed: ${startupResult.message}")
            }
            is SyncResult.NotSignedIn -> {
                _lastSyncResult.value = startupResult
                _lastSyncMessage.value = startupResult.message
            }
            else -> Unit
        }

        // Update last sync time from preferences (set by CalendarSyncManager)
        val last = authManager.getLastSyncTime()
        _lastSyncTime.value = last.takeIf { it > 0 }

        _startupSyncInProgress.value = false
        _syncInProgress.value = false
    }

    /**
     * Check if user is currently signed in
     */
    private fun checkSignInStatus() {
        _isSignedIn.value = authManager.isSignedIn()
        _userEmail.value = authManager.getUserEmail()
        Log.d(TAG, "Sign-in status: ${_isSignedIn.value}, email: ${_userEmail.value}")
    }

    /**
     * Load sync preferences from storage
     */
    private fun loadSyncPreferences() {
        _autoSyncEnabled.value = authManager.isSyncEnabled()
        _syncFrequency.value = authManager.getSyncFrequency()
        _selectedCalendars.value = authManager.getSelectedCalendars()
        val last = authManager.getLastSyncTime()
        _lastSyncTime.value = last.takeIf { it > 0 }
    }

    /**
     * Load available calendars from Google
     */
    fun loadAvailableCalendars() {
        viewModelScope.launch {
            val calendarsResult = syncManager.getAvailableCalendarsResult()
            calendarsResult.onSuccess { calendars ->
                _availableCalendars.value = calendars
                Log.d(TAG, "Loaded ${calendars.size} calendars, ${calendars.count { it.isWritable }} writable")

                // If no calendars selected yet, select all by default
                if (_selectedCalendars.value.isEmpty() && calendars.isNotEmpty()) {
                    val allCalendarIds = calendars.map { it.id }.toSet()
                    _selectedCalendars.value = allCalendarIds
                    authManager.setSelectedCalendars(allCalendarIds)
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to load available calendars: ${error.message}")
            }
        }
    }

    /**
     * Toggle calendar selection
     */
    fun toggleCalendarSelection(calendarId: String) {
        viewModelScope.launch {
            val currentSelection = _selectedCalendars.value.toMutableSet()

            if (calendarId in currentSelection) {
                currentSelection.remove(calendarId)
                // Remove events from this calendar in cache
                syncManager.removeCalendarEventsFromCache(calendarId)
            } else {
                currentSelection.add(calendarId)
            }

            _selectedCalendars.value = currentSelection
            authManager.setSelectedCalendars(currentSelection)

            // Re-sync to update events
            if (calendarId in currentSelection) {
                syncCurrentMonth()
            }
        }
    }

    /**
     * Get Google Sign-In intent for OAuth flow
     */
    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    /**
     * Handle OAuth sign-in result from ActivityResult
     */
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing sign-in result, data=${data != null}")
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)

                // Save account
                authManager.setSelectedAccount(account)

                _isSignedIn.value = true
                _userEmail.value = account.email

                Log.i(TAG, "Sign in successful: ${account.email}")
                _lastSyncMessage.value = "Connected to ${account.email}"

                // Load available calendars
                loadAvailableCalendars()

                // Trigger initial sync for 6-month window (2 past + current + 3 future)
                performStartupMonthRangeSync(
                    monthsBefore = SyncScheduler.DEFAULT_MONTHS_BEFORE,
                    monthsAfter = SyncScheduler.DEFAULT_MONTHS_AFTER
                )

            } catch (e: ApiException) {
                // Provide more helpful error messages based on status code
                val errorMessage = when (e.statusCode) {
                    12501 -> "Sign in cancelled"
                    12502 -> "Sign in already in progress"
                    12500 -> "Sign in failed - check network connection"
                    10 -> "Developer error - check OAuth configuration"
                    7 -> "Network error - check internet connection"
                    else -> "Sign in failed (code ${e.statusCode})"
                }
                if (e.statusCode == 12501) {
                    Log.i(TAG, "Sign in cancelled by user (statusCode=12501)")
                } else {
                    Log.e(TAG, "Sign in failed: statusCode=${e.statusCode}, message=${e.message}", e)
                }
                _lastSyncMessage.value = errorMessage
                _isSignedIn.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during sign-in", e)
                _lastSyncMessage.value = "Sign in error: ${e.message}"
                _isSignedIn.value = false
            }
        }
    }

    /**
     * Sign out from Google account
     */
    fun signOut() {
        viewModelScope.launch {
            // Clear synced events from cache
            syncManager.clearSyncedEvents()
            clearMonthCache()
            _lastSyncTime.value = null

            authManager.revokeAccess {
                _isSignedIn.value = false
                _userEmail.value = null
                _availableCalendars.value = emptyList()
                _selectedCalendars.value = emptySet()
                _lastSyncMessage.value = "Signed out successfully"
                Log.i(TAG, "Signed out and revoked access")
            }
        }
    }

    /**
     * Clear all locally synced Google Calendar events.
     * Optionally perform a fresh sync for nearby months.
     */
    fun clearSyncedData(performFreshSync: Boolean) {
        viewModelScope.launch {
            syncManager.clearSyncedEvents()
            clearMonthCache()
            _lastSyncTime.value = null
            _lastSyncMessage.value = "Local Google Calendar cache cleared"

            if (performFreshSync && _isSignedIn.value) {
                // Sync full 6-month window (2 past + current + 3 future)
                performStartupMonthRangeSync(
                    monthsBefore = SyncScheduler.DEFAULT_MONTHS_BEFORE,
                    monthsAfter = SyncScheduler.DEFAULT_MONTHS_AFTER
                )
            }
        }
    }

    /**
     * Sync current month's events from Google Calendar
     */
    fun syncCurrentMonth() {
        viewModelScope.launch {
            if (_syncInProgress.value) {
                Log.d(TAG, "Sync already in progress, skipping")
                return@launch
            }

            _syncInProgress.value = true
            _lastSyncResult.value = SyncResult.InProgress
            _lastSyncMessage.value = "Syncing..."

            val result = syncManager.syncCurrentMonth()
            _lastSyncResult.value = result

            applyMonthSyncResult(
                result = result,
                successAction = {
                    val now = LocalDate.now()
                    markMonthSynced(now.year, now.monthValue)
                },
                logPrefix = "Sync"
            )

            _syncInProgress.value = false
        }
    }

    /**
     * Sync a specific month
     */
    fun syncMonth(year: Int, month: Int) {
        viewModelScope.launch {
            if (_syncInProgress.value) return@launch

            _syncInProgress.value = true
            _lastSyncResult.value = SyncResult.InProgress
            _lastSyncMessage.value = "Syncing..."

            val result = syncManager.syncMonth(year, month)
            _lastSyncResult.value = result

            applyMonthSyncResult(
                result = result,
                successAction = { markMonthSynced(year, month) },
                logPrefix = "Month $year-$month sync"
            )

            _syncInProgress.value = false
        }
    }

    /**
     * Sync a specific month if cache rules indicate it is needed.
     */
    fun syncMonthIfNeeded(year: Int, month: Int) {
        viewModelScope.launch {
            if (!monthNeedsSync(year, month)) {
                Log.d(TAG, "Month $year-$month already cached or not due for sync, skipping")
                return@launch
            }

            if (_syncInProgress.value) {
                Log.d(TAG, "Sync already in progress, skipping monthIfNeeded for $year-$month")
                return@launch
            }

            _syncInProgress.value = true
            _lastSyncResult.value = SyncResult.InProgress
            _lastSyncMessage.value = "Syncing..."

            val result = syncManager.syncMonth(year, month)
            _lastSyncResult.value = result

            applyMonthSyncResult(
                result = result,
                successAction = { markMonthSynced(year, month) },
                logPrefix = "Month $year-$month sync"
            )

            _syncInProgress.value = false
        }
    }

    /**
     * Update auto-sync preference
     */
    fun setAutoSync(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        authManager.setSyncEnabled(enabled)
        Log.d(TAG, "Auto-sync set to: $enabled")
    }

    /**
     * Update sync frequency
     */
    fun setSyncFrequency(frequency: String) {
        _syncFrequency.value = frequency
        authManager.setSyncFrequency(frequency)
        Log.d(TAG, "Sync frequency set to: $frequency")
    }

    /**
     * Get writable calendars for event creation
     */
    fun getWritableCalendarsSync(): List<GoogleCalendarInfo> {
        return _availableCalendars.value.filter { it.isWritable }
    }

    private fun monthKey(year: Int, month: Int): String {
        return String.format("%04d-%02d", year, month)
    }

    private fun applyDeletedCalendarCleanup(deletedCalendarIds: List<String>) {
        val normalizedIds = deletedCalendarIds.distinct()
        if (normalizedIds.isEmpty()) return

        val idSet = normalizedIds.toSet()
        val updatedSelection = _selectedCalendars.value - idSet
        if (updatedSelection != _selectedCalendars.value) {
            _selectedCalendars.value = updatedSelection
            authManager.setSelectedCalendars(updatedSelection)
        }

        val removedCalendars = _availableCalendars.value.filter { it.id in idSet }
        _availableCalendars.value = _availableCalendars.value.filter { it.id !in idSet }

        val removedSummary = removedCalendars.map { it.name }.ifEmpty { normalizedIds }
        Log.w(
            TAG,
            "Removed ${normalizedIds.size} externally deleted Google calendar(s) from legacy sync: ${removedSummary.joinToString()}"
        )
    }

    private fun applyMonthSyncResult(
        result: SyncResult,
        successAction: () -> Unit,
        logPrefix: String
    ) {
        applyDeletedCalendarCleanup(result.deletedCalendarIds())

        when (result) {
            is SyncResult.Success -> {
                _lastSyncMessage.value = result.toDisplayMessage()
                successAction()
                val last = authManager.getLastSyncTime()
                _lastSyncTime.value = last.takeIf { it > 0 }
                Log.d(TAG, "$logPrefix complete: ${result.toDisplayMessage()}")
            }
            is SyncResult.PartialSuccess -> {
                _lastSyncMessage.value = result.getDisplayMessage()
                Log.w(TAG, "$logPrefix partial: ${result.errors.joinToString()}")
            }
            is SyncResult.NoNetwork -> {
                _lastSyncMessage.value = result.toDisplayMessage()
                Log.w(TAG, "$logPrefix blocked by network loss")
            }
            is SyncResult.Error -> {
                _lastSyncMessage.value = "Sync failed: ${result.message}"
                Log.e(TAG, "$logPrefix failed: ${result.message}")
            }
            is SyncResult.NotSignedIn -> {
                _lastSyncMessage.value = result.message
            }
            else -> Unit
        }
    }

    private fun markMonthSynced(year: Int, month: Int) {
        markMonthSynced(monthKey(year, month))
    }

    private fun markMonthSynced(key: String) {
        syncedMonths.add(key)
        monthSyncTimestamps[key] = System.currentTimeMillis()
    }

    private fun clearMonthCache() {
        syncedMonths.clear()
        monthSyncTimestamps.clear()
    }

    private fun monthNeedsSync(year: Int, month: Int): Boolean {
        val key = monthKey(year, month)

        val now = LocalDate.now()
        // Limit automatic month navigation sync to a reasonable window:
        // up to 6 months in the past and 6 months in the future.
        val diffMonths = (year - now.year) * 12 + (month - now.monthValue)
        if (diffMonths < -6 || diffMonths > 6) {
            return false
        }

        val currentYearMonth = now.withDayOfMonth(1)
        val targetYearMonth = LocalDate.of(year, month, 1)

        return when {
            targetYearMonth.isBefore(currentYearMonth) -> {
                // Past month: sync only once
                key !in syncedMonths
            }
            targetYearMonth.isEqual(currentYearMonth) -> {
                // Current month: sync once per app session (startup sync will already refresh)
                key !in syncedMonths
            }
            else -> {
                // Future month: re-sync if last sync is older than 24 hours
                val last = monthSyncTimestamps[key] ?: return true
                val ageMillis = System.currentTimeMillis() - last
                ageMillis >= 24L * 60L * 60L * 1000L
            }
        }
    }

    /**
     * Background auto-sync loop
     * Runs only while the app (and ViewModel) are alive
     */
    private fun startAutoSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    // Only attempt auto-sync when signed in and sync is enabled
                    if (!authManager.isAutoSyncConfigured()) {
                        delay(60_000L)
                        continue
                    }

                    // Check if enough time has passed since last sync
                    if (authManager.shouldAutoSync() && !_syncInProgress.value) {
                        // Skip legacy auto-sync if multi-account system is active
                        if (hasMultiAccountAccounts()) {
                            delay(60_000L)
                            continue
                        }
                        Log.d(TAG, "Background auto-sync triggered")
                        syncCurrentMonth()
                    }

                    // Sleep before checking again (minimum 1 minute)
                    delay(60_000L)

                } catch (e: CancellationException) {
                    // Cancellation is expected when ViewModel is cleared.
                    Log.d(TAG, "Auto-sync loop cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, buildAutoSyncErrorContext(e), e)
                    if (isActive) {
                        delay(60_000L)
                    }
                }
            }
        }
    }

    private fun buildAutoSyncErrorContext(error: Throwable): String {
        val now = System.currentTimeMillis()
        val lastSyncAt = authManager.getLastSyncTime()
        val lastSyncAgeMs = if (lastSyncAt > 0L) now - lastSyncAt else -1L
        return "AUTO_SYNC_LOOP_ERROR|" +
            "appVersion=${BuildConfig.VERSION_NAME}|" +
            "versionCode=${BuildConfig.VERSION_CODE}|" +
            "isSignedIn=${_isSignedIn.value}|" +
            "autoSyncEnabled=${authManager.isSyncEnabled()}|" +
            "autoSyncConfigured=${authManager.isAutoSyncConfigured()}|" +
            "syncInProgress=${_syncInProgress.value}|" +
            "syncFrequency=${authManager.getSyncFrequency()}|" +
            "lastSyncAt=$lastSyncAt|" +
            "lastSyncAgeMs=$lastSyncAgeMs|" +
            "exceptionClass=${error.javaClass.name}|" +
            "exceptionMessage=${error.message}"
    }

    /**
     * Clear sync message
     */
    fun clearSyncMessage() {
        _lastSyncMessage.value = null
    }

    /**
     * Check if the multi-account sync system has any connected accounts.
     * When true, legacy sync should be skipped — multi-account handles all syncing
     * with proper accountId values that match calendar_selections.
     */
    private suspend fun hasMultiAccountAccounts(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(getApplication()).connectedAccountDao().countActiveAccounts() > 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
