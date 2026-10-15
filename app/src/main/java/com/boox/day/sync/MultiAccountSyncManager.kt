package com.boox.day.sync

import android.app.Activity
import android.content.Context
import com.boox.day.BuildConfig
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.CalendarSelectionDao
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.db.ConnectedAccountDao
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.db.collectCoveredDayIds
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.CalendarSelection
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.data.model.WritableCalendar
import com.boox.day.booxnotes.SmartLinkRefreshCoordinator
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.EventSemanticIndexingWorker
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.sync.provider.CalendarProvider
import com.boox.day.sync.provider.CalendarProviderException
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.boox.day.sync.provider.OutlookCalendarProvider
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Multi-Account Sync Manager
 *
 * Orchestrates calendar synchronization across multiple providers (Google, Outlook)
 * and multiple accounts. Provides a unified interface for:
 *
 * - Account management (add, remove, list accounts)
 * - Calendar selection (enable/disable calendars, set defaults)
 * - Sync operations (sync individual accounts or all accounts)
 * - Event CRUD operations (create, update, delete events)
 *
 * ## Design Principles
 *
 * - **Provider Agnostic**: Works with any CalendarProvider implementation
 * - **Parallel Sync**: Syncs multiple accounts concurrently for performance
 * - **Fault Tolerant**: One account's failure doesn't affect others
 * - **Selection Aware**: Only syncs calendars the user has enabled
 *
 * ## Account Lifecycle
 *
 * 1. User adds account via [addAccount] or [addAccountInteractive]
 * 2. System fetches calendars via [refreshCalendarsForAccount]
 * 3. User selects calendars to sync via [updateCalendarSelection]
 * 4. Sync runs via [syncAllAccounts] or [syncAccount]
 * 5. User removes account via [removeAccount]
 *
 * @param context Application context
 * @param googleProvider Google Calendar provider
 * @param outlookProvider Outlook Calendar provider
 * @param accountDao DAO for connected accounts
 * @param selectionDao DAO for calendar selections
 * @param eventRepository Repository for cached calendar events
 */
class MultiAccountSyncManager(
    private val context: Context,
    private val googleProvider: GoogleCalendarProvider,
    private val outlookProvider: OutlookCalendarProvider,
    private val accountDao: ConnectedAccountDao,
    private val selectionDao: CalendarSelectionDao,
    private val eventRepository: CalendarEventRepository
) {
    companion object {
        private const val TAG = "MultiAccountSyncManager"
    }

    private val quotaManager by lazy { OcrQuotaManager(context.applicationContext) }
    private val smartLinkRefreshCoordinator by lazy {
        SmartLinkRefreshCoordinator(context.applicationContext, eventRepository = eventRepository)
    }

    private val _allWritableCalendars = MutableStateFlow<List<WritableCalendar>>(emptyList())
    val allWritableCalendars: StateFlow<List<WritableCalendar>> = _allWritableCalendars.asStateFlow()

    private fun buildSyncErrorContext(
        event: String,
        accountId: String?,
        accountEmail: String?,
        providerId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        error: Throwable
    ): String {
        return "$event|" +
            "appVersion=${BuildConfig.VERSION_NAME}|" +
            "versionCode=${BuildConfig.VERSION_CODE}|" +
            "accountId=${accountId ?: "unknown"}|" +
            "accountEmail=${accountEmail ?: "unknown"}|" +
            "provider=${providerId ?: "unknown"}|" +
            "startDate=${startDate ?: "n/a"}|" +
            "endDate=${endDate ?: "n/a"}|" +
            "exceptionClass=${error.javaClass.name}|" +
            "exceptionMessage=${error.message}"
    }

    private fun logNetworkAwareFailure(
        networkMessage: String,
        errorMessage: String,
        error: Throwable
    ) {
        if (error is CalendarProviderException.NetworkException) {
            Log.w(TAG, networkMessage)
        } else {
            Log.e(TAG, errorMessage, error)
        }
    }

    private suspend fun refreshSmartLinksForEvents(events: Collection<CalendarEvent>, reason: String) {
        val affectedDates = collectCoveredDayIds(events)
        if (affectedDates.isEmpty()) return
        smartLinkRefreshCoordinator.refreshDates(affectedDates, reason)
    }

    private suspend fun refreshSemanticProfileForEvent(event: CalendarEvent) {
        val semanticService = SemanticGeniusService(context)
        if (!quotaManager.isSemanticExtractionEnabled) {
            try {
                semanticService.deleteEventProfile(OcrSource.CALENDAR_EVENT, event.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear semantic profile for event ${event.id}: ${e.message}")
            }
            return
        }

        try {
            semanticService.invalidateEventProfile(event.id)
            semanticService.indexEvent(event)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh semantic profile for event ${event.id}: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Provider Access
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the provider for a given provider ID.
     */
    fun getProvider(providerId: ProviderId): CalendarProvider {
        return when (providerId) {
            ProviderId.GOOGLE -> googleProvider
            ProviderId.OUTLOOK -> outlookProvider
        }
    }

    /**
     * Get the provider for an account.
     * Returns null if account not found.
     */
    private suspend fun getProviderForAccount(accountId: String): CalendarProvider? {
        val account = accountDao.getById(accountId) ?: return null
        return getProvider(ProviderId.fromKey(account.providerId))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all active connected accounts from all providers.
     */
    suspend fun getAllAccounts(): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        accountDao.getActiveAccounts().map { it.toDomainModel() }
    }

    /**
     * Get all active accounts for a specific provider.
     */
    suspend fun getAccountsByProvider(providerId: ProviderId): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        accountDao.getAccountsByProvider(providerId.key).map { it.toDomainModel() }
    }

    /**
     * Get an account by ID.
     */
    suspend fun getAccountById(accountId: String): ConnectedAccount? = withContext(Dispatchers.IO) {
        accountDao.getById(accountId)?.toDomainModel()
    }

    /**
     * Add a new account using Intent-based sign-in (Google).
     *
     * For Google accounts, use this to get the sign-in Intent, then call
     * [handleSignInResult] with the activity result.
     *
     * @param activity Activity to launch sign-in from
     * @param accountType The account type to add
     * @return Intent to launch, or null if provider uses callback-based auth
     */
    fun getSignInIntent(activity: Activity, accountType: AccountType): android.content.Intent? {
        val provider = getProvider(accountType.providerId)
        return provider.getSignInIntent(activity, accountType)
    }

    /**
     * Handle the result of Intent-based sign-in (Google).
     */
    suspend fun handleSignInResult(
        providerId: ProviderId,
        data: android.content.Intent?
    ): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId)
        provider.handleSignInResult(data)
    }

    /**
     * Add a new account using interactive sign-in (Outlook/MSAL).
     *
     * For Outlook accounts, this launches the MSAL sign-in flow directly.
     *
     * @param activity Activity context for the sign-in UI
     * @param accountType The account type to add
     * @return Result containing the new ConnectedAccount, or failure
     */
    suspend fun addAccountInteractive(
        activity: Activity,
        accountType: AccountType
    ): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(accountType.providerId)
            val result = provider.signInInteractive(activity, accountType)

            if (result.isSuccess) {
                val account = result.getOrThrow()
                Log.i(TAG, "Account added successfully: ${account.email}")

                // Auto-fetch calendars for the new account
                refreshCalendarsForAccount(account.id)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error adding account", e)
            Result.failure(e)
        }
    }

    /**
     * Remove an account and clean up associated data.
     *
     * This will:
     * 1. Sign out from the provider
     * 2. Delete cached events for this account
     * 3. Delete calendar selections (via CASCADE)
     * 4. Deactivate the account record
     */
    suspend fun removeAccount(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                Log.w(TAG, "Account not found for removal: $accountId")
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            val provider = getProvider(ProviderId.fromKey(account.providerId))

            // Sign out from provider
            provider.removeAccount(accountId)

            // Delete cached events for this account
            eventRepository.removeEventsForAccount(accountId)

            // Calendar selections are deleted via CASCADE when account is deactivated
            // Deactivate account (provider.removeAccount already does this, but ensure it)
            accountDao.deactivate(accountId)

            Log.i(TAG, "Account removed successfully: ${account.email}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing account: $accountId", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get calendar selections for an account.
     */
    suspend fun getCalendarsForAccount(accountId: String): List<CalendarSelection> = withContext(Dispatchers.IO) {
        selectionDao.getByAccountId(accountId).map { it.toDomainModel() }
    }

    /**
     * Get writable calendars for a specific provider.
     */
    suspend fun getWritableCalendars(providerId: ProviderId): List<WritableCalendar> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAccountsByProvider(providerId.key)
        val provider = getProvider(providerId)
        val writableCalendars = mutableListOf<WritableCalendar>()

        accounts.forEach { account ->
            val calendarsResult = provider.fetchCalendars(account.id)
            if (calendarsResult.isFailure) return@forEach
            val providerCalendars = calendarsResult.getOrThrow()
            updateWritableCalendarsForAccount(account.id, providerId, account.email, providerCalendars)
            val accountCalendars = providerCalendars
                .filter { it.isWritable }
                .map { calendar ->
                    WritableCalendar(
                        id = calendar.id,
                        name = calendar.name,
                        accountId = account.id,
                        accountEmail = account.email,
                        providerId = providerId,
                        isPrimary = calendar.isPrimary,
                        colorHex = calendar.color
                    )
                }
            writableCalendars.addAll(accountCalendars)
        }

        writableCalendars
    }

    /**
     * Get writable calendars for a specific account.
     */
    suspend fun getWritableCalendarsForAccount(accountId: String): List<WritableCalendar> = withContext(Dispatchers.IO) {
        val account = accountDao.getById(accountId) ?: return@withContext emptyList()
        val providerId = ProviderId.fromKey(account.providerId)
        val provider = getProvider(providerId)
        val calendarsResult = provider.fetchCalendars(accountId)
        if (calendarsResult.isFailure) return@withContext emptyList()

        val providerCalendars = calendarsResult.getOrThrow()
        val writableCalendars = providerCalendars
            .filter { it.isWritable }
            .map { calendar ->
                WritableCalendar(
                    id = calendar.id,
                    name = calendar.name,
                    accountId = accountId,
                    accountEmail = account.email,
                    providerId = providerId,
                    isPrimary = calendar.isPrimary,
                    colorHex = calendar.color
                )
            }

        updateWritableCalendarsForAccount(accountId, providerId, account.email, providerCalendars)
        writableCalendars
    }

    /**
     * Fetch fresh calendars from the provider and update selections.
     *
     * This merges new calendars with existing selections:
     * - New calendars are added with isFetchEnabled = false (auto-enabled if primary)
     * - Existing calendars retain their selection state
     * - Removed calendars (deleted externally) are removed from local DB
     *
     * @return Result containing the updated calendar selections, or failure
     */
    suspend fun refreshCalendarsForAccount(accountId: String): Result<List<CalendarSelection>> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found: $accountId")
                )
            }

            val provider = getProvider(ProviderId.fromKey(account.providerId))
            val calendarsResult = provider.fetchCalendars(accountId)

            if (calendarsResult.isFailure) {
                return@withContext Result.failure(calendarsResult.exceptionOrNull()!!)
            }

            val providerCalendars = calendarsResult.getOrThrow()
            updateWritableCalendarsForAccount(accountId, ProviderId.fromKey(account.providerId), account.email, providerCalendars)
            val providerCalendarIds = providerCalendars.map { it.id }.toSet()
            val existingSelections = selectionDao.getByAccountId(accountId)
            val existingByCalendarId = existingSelections.associateBy { it.calendarId }

            // Detect externally deleted calendars
            val deletedCalendarIds = existingSelections
                .map { it.calendarId }
                .filter { it !in providerCalendarIds }

            if (deletedCalendarIds.isNotEmpty()) {
                Log.w(TAG, "Detected ${deletedCalendarIds.size} externally deleted calendar(s) for account $accountId")

                // Check if the default calendar was deleted
                val deletedDefault = existingSelections.find {
                    it.isDefaultForAccount && it.calendarId in deletedCalendarIds
                }
                if (deletedDefault != null) {
                    Log.w(TAG, "Default calendar '${deletedDefault.calendarName}' was deleted externally")
                }

                // Remove deleted calendars from local DB
                selectionDao.deleteByCalendarIds(accountId, deletedCalendarIds)
            }

            // Check if any calendar is marked as primary
            val hasPrimaryCalendar = providerCalendars.any { it.isPrimary }

            // Merge remaining calendars
            val updatedSelections = providerCalendars.mapIndexed { index, calendar ->
                val existing = existingByCalendarId[calendar.id]
                if (existing != null) {
                    // Update existing selection with fresh data
                    existing.copy(
                        calendarName = calendar.name,
                        calendarColor = calendar.color,
                        displayOrder = index
                    )
                } else {
                    // Create new selection - ALL calendars enabled by default (opt-out approach)
                    // Users can uncheck calendars they don't want to see
                    // Check if we need a new default (old one was deleted)
                    val needsDefault = existingSelections.none { it.isDefaultForAccount && it.calendarId in providerCalendarIds }

                    // Default calendar logic (for creating new events):
                    // - If a primary calendar exists, use it as default
                    // - If NO primary calendar exists, use the FIRST calendar as fallback
                    val shouldBeDefault = calendar.isPrimary || (!hasPrimaryCalendar && index == 0)

                    CalendarSelectionEntity(
                        id = "${accountId}_${calendar.id}".hashCode().toString(),
                        accountId = accountId,
                        calendarId = calendar.id,
                        calendarName = calendar.name,
                        calendarColor = calendar.color,
                        isFetchEnabled = true,  // All calendars enabled by default
                        isDefaultForAccount = shouldBeDefault && needsDefault,
                        displayOrder = index
                    )
                }
            }

            // Log if we had to use fallback
            if (!hasPrimaryCalendar && providerCalendars.isNotEmpty()) {
                Log.w(TAG, "No primary calendar found for account $accountId - auto-enabled first calendar: ${providerCalendars.first().name}")
            }

            // Save updated selections
            selectionDao.upsertAll(updatedSelections)

            Log.d(TAG, "Refreshed ${updatedSelections.size} calendars for account $accountId" +
                    if (deletedCalendarIds.isNotEmpty()) " (${deletedCalendarIds.size} deleted)" else "")
            Result.success(updatedSelections.map { it.toDomainModel() })

        } catch (e: Exception) {
            logNetworkAwareFailure(
                networkMessage = "Refresh calendars skipped for account $accountId: ${e.message ?: "No network connection"}",
                errorMessage = "Error refreshing calendars for account $accountId",
                error = e
            )
            Result.failure(e)
        }
    }

    /**
     * Update a calendar selection (enable/disable, set as default).
     */
    suspend fun updateCalendarSelection(selection: CalendarSelection) = withContext(Dispatchers.IO) {
        selectionDao.upsert(CalendarSelectionEntity.fromDomainModel(selection))
        Log.d(TAG, "Updated calendar selection: ${selection.calendarName}")
    }

    /**
     * Toggle whether a calendar is enabled for sync.
     */
    suspend fun toggleCalendarEnabled(accountId: String, calendarId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        selectionDao.setFetchEnabledByCalendarId(accountId, calendarId, enabled)
        // Invalidate LRU cache so the filtered Room query (INNER JOIN) takes effect immediately.
        // Events are NOT deleted — the INNER JOIN on isFetchEnabled handles visibility.
        // This means re-enabling a calendar instantly restores its events without re-syncing.
        eventRepository.invalidateEventsCache()
        Log.d(TAG, "Toggled calendar $calendarId enabled: $enabled")
    }

    /**
     * Set a calendar as the default for its account.
     * This is used when creating new events - they go to the default calendar.
     */
    suspend fun setDefaultCalendar(accountId: String, selectionId: String) = withContext(Dispatchers.IO) {
        selectionDao.setAsDefault(accountId, selectionId)
        Log.d(TAG, "Set default calendar: $selectionId for account $accountId")
    }

    /**
     * Get the default calendar for an account.
     */
    suspend fun getDefaultCalendar(accountId: String): CalendarSelection? = withContext(Dispatchers.IO) {
        selectionDao.getDefaultCalendar(accountId)?.toDomainModel()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sync Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync all active accounts in parallel.
     *
     * Each account syncs independently - one failure doesn't affect others.
     * Returns a map of account ID to sync result.
     *
     * @param startDate Start of date range to sync
     * @param endDate End of date range to sync
     * @return Map of accountId to SyncResult
     */
    suspend fun syncAllAccounts(
        startDate: LocalDate,
        endDate: LocalDate,
        onProgress: ((accountId: String, MultiAccountSyncViewModel.SyncProgress) -> Unit)? = null
    ): Map<String, SyncResult> = coroutineScope {
        val accounts = getAllAccounts()

        if (accounts.isEmpty()) {
            Log.d(TAG, "No accounts to sync")
            return@coroutineScope emptyMap()
        }

        Log.i(TAG, "Starting sync for ${accounts.size} accounts")

        // Launch parallel sync for each account
        val results = accounts.map { account ->
            async {
                val result = try {
                    syncAccount(account.id, startDate, endDate) { progress ->
                        onProgress?.invoke(account.id, progress)
                    }
                } catch (e: Exception) {
                    val syncErrorContext = buildSyncErrorContext(
                        event = "SYNC_ACCOUNT_TASK_ERROR",
                        accountId = account.id,
                        accountEmail = account.email,
                        providerId = account.providerId.key,
                        startDate = startDate,
                        endDate = endDate,
                        error = e
                    )
                    logNetworkAwareFailure(
                        networkMessage = syncErrorContext,
                        errorMessage = syncErrorContext,
                        error = e
                    )
                    SyncResult.Error(
                        message = e.message ?: "Unknown error",
                        exception = e,
                        errorType = e.toSyncErrorType()
                    )
                }
                account.id to result
            }
        }

        results.awaitAll().toMap()
    }

    /**
     * Sync a single account.
     *
     * @param accountId The account to sync
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param onProgress Optional callback for reporting sync progress (phase + counts)
     * @return SyncResult indicating success or failure
     */
    suspend fun syncAccount(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        onProgress: ((MultiAccountSyncViewModel.SyncProgress) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var accountEmail: String? = null
        var providerId: String? = null

        try {
            val account = accountDao.getById(accountId)
            if (account == null) {
                return@withContext SyncResult.Error("Account not found: $accountId")
            }
            accountEmail = account.email
            providerId = account.providerId

            val provider = getProvider(ProviderId.fromKey(account.providerId))



            // Validate account
            if (!provider.isAccountValid(accountId)) {
                Log.w(TAG, "Account not valid: ${account.email}")
                return@withContext SyncResult.NotSignedIn("Account needs re-authentication")
            }

            // Refresh token if needed
            val tokenResult = provider.refreshTokenIfNeeded(accountId)
            if (tokenResult.isFailure) {
                val error = tokenResult.exceptionOrNull()
                if (error is CalendarProviderException.AuthenticationException) {
                    return@withContext SyncResult.NotSignedIn("Authentication expired")
                }
            }

            // Get enabled calendars for this account
            val enabledCalendars = selectionDao.getByAccountId(accountId)
                .filter { it.isFetchEnabled }
                .map { it.calendarId }
                .toSet()

            if (enabledCalendars.isEmpty()) {
                Log.d(TAG, "No enabled calendars for account ${account.email}")
                return@withContext SyncResult.Success(
                    eventCount = 0,
                    newEvents = 0,
                    updatedEvents = 0
                )
            }

            // Report fetching phase
            onProgress?.invoke(MultiAccountSyncViewModel.SyncProgress(
                phase = MultiAccountSyncViewModel.SyncPhase.FETCHING
            ))

            // Fetch events from provider
            val eventsResult = provider.fetchEvents(accountId, enabledCalendars, startDate, endDate)
            var partialSyncResult: SyncResult.PartialSuccess? = null
            val providerEvents = if (eventsResult.isFailure) {
                val error = eventsResult.exceptionOrNull()
                when (error) {
                    is CalendarProviderException.AuthenticationException ->
                        return@withContext SyncResult.NotSignedIn(error.message ?: "Authentication failed")
                    is CalendarProviderException.NetworkException ->
                        return@withContext SyncResult.NoNetwork()
                    is CalendarProviderException.PartialSyncException -> {
                        partialSyncResult = error.syncResult
                        error.partialEvents
                    }
                    else ->
                        return@withContext SyncResult.Error(error?.message ?: "Unknown error", error as? Exception)
                }
            } else {
                eventsResult.getOrThrow()
            }

            // Convert to CalendarEvent and store
            val calendarSelections = selectionDao.getByAccountId(accountId).associateBy { it.calendarId }
            val calendarEvents = providerEvents.map { event ->
                val selection = calendarSelections[event.calendarId]
                event.toCalendarEvent(
                    providerId = account.providerId,
                    accountId = accountId,
                    accountEmail = account.email,
                    calendarName = selection?.calendarName ?: event.calendarId
                )
            }

            // Get existing events for comparison
            val existingEvents = eventRepository.getEventsInRange(
                startDate.toString(),
                endDate.toString()
            ).filter { it.accountId == accountId }
            val existingIds = existingEvents.map { it.id }.toSet()
            val fetchedEventIds = calendarEvents.map { it.id }.toSet()
            val missingEvents = if (partialSyncResult == null) {
                existingEvents.filter { existing ->
                    existing.calendarId in enabledCalendars && existing.id !in fetchedEventIds
                }
            } else {
                emptyList()
            }
            val changedExistingEvents = existingEvents.filter { existing -> calendarEvents.any { it.id == existing.id } }

            // Calculate new vs updated
            var newCount = 0
            var updatedCount = 0
            calendarEvents.forEach { event ->
                if (event.id in existingIds) updatedCount++ else newCount++
            }

            // Store events
            eventRepository.cacheEvents(calendarEvents)
            if (missingEvents.isNotEmpty()) {
                eventRepository.removeEvents(
                    eventIds = missingEvents.map { it.id },
                    reason = "cloud sync removed missing events"
                )
            }

            // Inline semantic indexing for synced events
            // Index directly during sync so Smart Link works immediately after sync completes.
            // Uses bounded parallelism (4 concurrent) since extraction is CPU-only (dictionaries + regex).
            // Failed events fall back to WorkManager for retry.
            if (newCount + updatedCount > 0) {
                val semanticService = SemanticGeniusService(context)
                if (!quotaManager.isSemanticExtractionEnabled) {
                    Log.i(TAG, "Skipping inline event indexing because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")

                    // If event semantics are disabled during sync, remove the fetched profiles so
                    // stale semantic data does not survive and silently reappear later.
                    calendarEvents.forEach { event ->
                        try {
                            semanticService.deleteEventProfile(OcrSource.CALENDAR_EVENT, event.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to clear semantic profile for event ${event.id}: ${e.message}")
                        }
                    }
                } else {
                    val now = System.currentTimeMillis()

                    // Sort by proximity to today - closest events indexed first for faster Smart Link
                    val sortedEvents = calendarEvents
                        .sortedBy { kotlin.math.abs(it.startTime - now) }

                    // Filter out already-indexed events to avoid redundant work
                    val alreadyIndexed = semanticService.getAlreadyIndexedEventIds(sortedEvents.map { it.id })
                    val eventsToIndex = sortedEvents.filter { it.id !in alreadyIndexed }

                    if (eventsToIndex.isNotEmpty()) {
                        Log.i(TAG, "Inline indexing ${eventsToIndex.size} events (${alreadyIndexed.size} already indexed)")
                        val indexStartTime = System.currentTimeMillis()
                        val totalToIndex = eventsToIndex.size

                        // Report indexing phase start
                        onProgress?.invoke(MultiAccountSyncViewModel.SyncProgress(
                            phase = MultiAccountSyncViewModel.SyncPhase.INDEXING,
                            current = 0,
                            total = totalToIndex
                        ))

                        // Bound parallelism: 4 concurrent indexing coroutines
                        // Each indexEvent() is ~160-600ms (CPU-bound dictionary work + DB write)
                        val semaphore = Semaphore(4)
                        val failedEventIds = Collections.synchronizedList(mutableListOf<String>())
                        val indexedCount = java.util.concurrent.atomic.AtomicInteger(0)

                        coroutineScope {
                            eventsToIndex.map { event ->
                                async(Dispatchers.IO) {
                                    semaphore.withPermit {
                                        try {
                                            semanticService.indexEvent(event)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to index event ${event.id}: ${e.message}")
                                            failedEventIds.add(event.id)
                                        }
                                        // Report progress every 5 events or on the last event (throttled for e-ink)
                                        val done = indexedCount.incrementAndGet()
                                        if (done % 5 == 0 || done == totalToIndex) {
                                            onProgress?.invoke(MultiAccountSyncViewModel.SyncProgress(
                                                phase = MultiAccountSyncViewModel.SyncPhase.INDEXING,
                                                current = done,
                                                total = totalToIndex
                                            ))
                                        }
                                    }
                                }
                            }.awaitAll()
                        }

                        val indexDuration = System.currentTimeMillis() - indexStartTime
                        Log.i(TAG, "Inline indexing complete: ${eventsToIndex.size - failedEventIds.size} indexed, " +
                            "${failedEventIds.size} failed (${indexDuration}ms)")

                        // Fallback: enqueue failed events to WorkManager for retry
                        if (failedEventIds.isNotEmpty()) {
                            Log.w(TAG, "Enqueueing ${failedEventIds.size} failed events for retry via WorkManager")
                            EventSemanticIndexingWorker.enqueueForSync(
                                context = context,
                                accountId = accountId,
                                changedEventIds = failedEventIds.toList()
                            )
                        }
                    } else if (sortedEvents.isNotEmpty()) {
                        Log.d(TAG, "All ${sortedEvents.size} events already indexed, skipping")
                    }
                }

                refreshSmartLinksForEvents(
                    events = changedExistingEvents + calendarEvents,
                    reason = "cloud sync refreshed events"
                )
            }

            val duration = System.currentTimeMillis() - startTime
            if (partialSyncResult != null) {
                Log.w(
                    TAG,
                    "Sync partially complete for ${account.email}: ${partialSyncResult!!.successCount} synced, " +
                        "${partialSyncResult!!.failureCount} calendars failed (${duration}ms)"
                )
                partialSyncResult!!
            } else {
                // Update last sync time
                accountDao.updateLastSync(accountId, System.currentTimeMillis())
                Log.i(
                    TAG,
                    "Sync complete for ${account.email}: $newCount new, $updatedCount updated, ${missingEvents.size} removed (${duration}ms)"
                )
                SyncResult.Success(
                    eventCount = calendarEvents.size,
                    newEvents = newCount,
                    updatedEvents = updatedCount
                )
            }

        } catch (e: Exception) {
            val syncErrorContext = buildSyncErrorContext(
                event = "SYNC_ACCOUNT_ERROR",
                accountId = accountId,
                accountEmail = accountEmail,
                providerId = providerId,
                startDate = startDate,
                endDate = endDate,
                error = e
            )
            logNetworkAwareFailure(
                networkMessage = syncErrorContext,
                errorMessage = syncErrorContext,
                error = e
            )
            SyncResult.Error(
                message = e.message ?: "Sync failed",
                exception = e,
                errorType = e.toSyncErrorType()
            )
        }
    }

    /**
     * Sync the current month for all accounts.
     */
    suspend fun syncCurrentMonthAllAccounts(): Map<String, SyncResult> {
        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())
        return syncAllAccounts(startOfMonth, endOfMonth)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new event on the provider and cache it locally.
     *
     * @param accountId The account to create the event in
     * @param calendarId The calendar to create the event in (or null for default)
     * @param event The event data
     * @return Result containing the created CalendarEvent
     */
    suspend fun createEvent(
        accountId: String,
        calendarId: String?,
        event: NewEventData
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found")
                )

            // Determine calendar to use
            val targetCalendarId = calendarId
                ?: selectionDao.getDefaultCalendar(accountId)?.calendarId
                ?: return@withContext Result.failure(
                    CalendarProviderException.ProviderException("No default calendar set")
                )

            val provider = getProvider(ProviderId.fromKey(account.providerId))
            val result = provider.createEvent(accountId, targetCalendarId, event)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val providerEvent = result.getOrThrow()
            val selection = selectionDao.getByAccountAndCalendarId(accountId, targetCalendarId)

            // Convert to CalendarEvent and cache
            val calendarEvent = providerEvent.toCalendarEvent(
                providerId = account.providerId,
                accountId = accountId,
                accountEmail = account.email,
                calendarName = selection?.calendarName ?: targetCalendarId
            )
            eventRepository.cacheEvent(calendarEvent)
            refreshSemanticProfileForEvent(calendarEvent)
            refreshSmartLinksForEvents(listOf(calendarEvent), "cloud event created")

            Log.i(TAG, "Created event: ${calendarEvent.title}")
            Result.success(calendarEvent)

        } catch (e: Exception) {
            logNetworkAwareFailure(
                networkMessage = "Create event skipped: ${e.message ?: "No network connection"}",
                errorMessage = "Error creating event",
                error = e
            )
            Result.failure(e)
        }
    }

    /**
     * Update an existing event on the provider and update the cache.
     */
    suspend fun updateEvent(
        accountId: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found")
                )

            val provider = getProvider(ProviderId.fromKey(account.providerId))
            val previousCachedEvent = eventRepository.getEventById(eventId)
            val result = provider.updateEvent(accountId, calendarId, eventId, event)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val providerEvent = result.getOrThrow()
            val selection = selectionDao.getByAccountAndCalendarId(accountId, calendarId)

            // Convert to CalendarEvent and update cache
            val calendarEvent = providerEvent.toCalendarEvent(
                providerId = account.providerId,
                accountId = accountId,
                accountEmail = account.email,
                calendarName = selection?.calendarName ?: calendarId
            )
            eventRepository.cacheEvent(calendarEvent)
            refreshSemanticProfileForEvent(calendarEvent)
            refreshSmartLinksForEvents(
                events = listOfNotNull(previousCachedEvent, calendarEvent),
                reason = "cloud event updated"
            )

            Log.i(TAG, "Updated event: ${calendarEvent.title}")
            Result.success(calendarEvent)

        } catch (e: Exception) {
            logNetworkAwareFailure(
                networkMessage = "Update event skipped: ${e.message ?: "No network connection"}",
                errorMessage = "Error updating event",
                error = e
            )
            Result.failure(e)
        }
    }

    /**
     * Delete an event from the provider and remove from cache.
     */
    suspend fun deleteEvent(
        accountId: String,
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
                ?: return@withContext Result.failure(
                    CalendarProviderException.NotFoundException("Account not found")
                )

            val provider = getProvider(ProviderId.fromKey(account.providerId))
            val result = provider.deleteEvent(accountId, calendarId, eventId)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            // Remove from cache
            eventRepository.removeEvent(eventId)

            Log.i(TAG, "Deleted event: $eventId")
            Result.success(Unit)

        } catch (e: Exception) {
            logNetworkAwareFailure(
                networkMessage = "Delete event skipped: ${e.message ?: "No network connection"}",
                errorMessage = "Error deleting event",
                error = e
            )
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convert ProviderEvent to CalendarEvent for storage.
     */
    private fun ProviderEvent.toCalendarEvent(
        providerId: String,
        accountId: String,
        accountEmail: String,
        calendarName: String
    ): CalendarEvent = CalendarEvent(
        id = this.id,
        providerId = providerId,
        accountId = accountId,
        accountEmail = accountEmail,
        calendarId = this.calendarId,
        calendarName = calendarName,
        title = this.title,
        dayId = this.dayId,
        endDayId = this.endDayId,
        startTime = this.startTime,
        endTime = this.endTime,
        isAllDay = this.isAllDay,
        location = this.location,
        description = this.description,
        attendees = this.attendees,
        isReadOnly = this.isReadOnly,
        colorHex = this.colorHex,
        syncedAt = System.currentTimeMillis()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if any accounts are configured.
     */
    suspend fun hasAnyAccounts(): Boolean = withContext(Dispatchers.IO) {
        accountDao.countActiveAccounts() > 0
    }

    /**
     * Check if a specific provider has any accounts.
     */
    suspend fun hasAccountsForProvider(providerId: ProviderId): Boolean = withContext(Dispatchers.IO) {
        accountDao.hasAccountsForProvider(providerId.key)
    }

    /**
     * Get the total count of enabled calendars across all accounts.
     */
    suspend fun getEnabledCalendarCount(): Int = withContext(Dispatchers.IO) {
        selectionDao.getEnabledCalendars().size
    }

    /**
     * Get all enabled calendars across all accounts.
     */
    suspend fun getAllEnabledCalendars(): List<CalendarSelection> = withContext(Dispatchers.IO) {
        selectionDao.getEnabledCalendars().map { it.toDomainModel() }
    }

    /**
     * Update writable calendars for a specific account.
     */
    private fun updateWritableCalendarsForAccount(
        accountId: String,
        providerId: ProviderId,
        accountEmail: String,
        calendars: List<ProviderCalendar>
    ) {
        val writableCalendars = calendars.filter { it.isWritable }.map { calendar ->
            WritableCalendar(
                id = calendar.id,
                name = calendar.name,
                accountId = accountId,
                accountEmail = accountEmail,
                providerId = providerId,
                isPrimary = calendar.isPrimary,
                colorHex = calendar.color
            )
        }
        val remaining = _allWritableCalendars.value.filterNot { it.accountId == accountId }
        _allWritableCalendars.value = remaining + writableCalendars
    }

    /**
     * Replace writable calendars for a provider in the cached list.
     */
    private fun mergeWritableCalendars(
        existing: List<WritableCalendar>,
        updated: List<WritableCalendar>,
        providerId: ProviderId
    ): List<WritableCalendar> {
        val remaining = existing.filterNot { it.providerId == providerId }
        return remaining + updated
    }
}
