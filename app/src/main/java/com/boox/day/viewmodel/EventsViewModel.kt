package com.boox.day.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.DueTasksData
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.model.ProviderId
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ui.state.CalendarSource
import com.boox.day.ui.state.CalendarSourceFilter
import java.time.format.DateTimeFormatter
import com.boox.day.data.db.AppDatabase
import com.boox.day.sync.CalendarSyncManager
import com.boox.day.sync.EventOperationResult
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.MultiAccountSyncManager
import com.boox.day.sync.NewEventData
import com.boox.day.sync.UpdateEventData
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.OutlookCalendarService
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.boox.day.sync.provider.CalendarProviderException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * ViewModel for managing calendar events (CRUD operations).
 * All operations go through provider APIs, then update local cache.
 */
class EventsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "EventsViewModel"
        private const val MAX_CACHED_DAYS = 30  // ~1 month of day subscriptions for LRU cache
    }

    private val authManager = GoogleAuthManager(application)
    private val eventRepository = CalendarEventRepository(application)
    private val localEventRepository = LocalEventRepository(application)
    private val semanticGeniusService = SemanticGeniusService(application)
    private val syncManager = CalendarSyncManager(application, authManager, eventRepository)
    // Pushes offline creates/deletes when a network is available (plan Phase 2, Oct 2026)
    private val syncScheduler by lazy { com.boox.day.sync.SyncScheduler(application) }
    private val taskRepository = TaskRepository(application)

    // Multi-account sync manager (for both Google and Outlook sync)
    private val db = AppDatabase.getDatabase(application)
    private val accountDao = db.connectedAccountDao()
    private val selectionDao = db.calendarSelectionDao()
    private val localCalendarDao = db.localCalendarDao()
    private val localEventDao = db.localEventDao()
    private val outlookAuthManager = OutlookAuthManager(application)
    private val outlookCalendarService = OutlookCalendarService(outlookAuthManager)
    private val googleProvider = GoogleCalendarProvider(application, authManager, syncManager, accountDao, eventRepository)
    private val outlookProvider = OutlookCalendarProvider(application, outlookAuthManager, outlookCalendarService, accountDao)
    private val multiAccountSyncManager = MultiAccountSyncManager(application, googleProvider, outlookProvider, accountDao, selectionDao, eventRepository)

    // Operation state
    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private val _lastOperationMessage = MutableStateFlow<String?>(null)
    val lastOperationMessage: StateFlow<String?> = _lastOperationMessage.asStateFlow()

    private val _lastOperationSuccess = MutableStateFlow<Boolean?>(null)
    val lastOperationSuccess: StateFlow<Boolean?> = _lastOperationSuccess.asStateFlow()

    // Current selected event for viewing/editing
    private val _selectedEvent = MutableStateFlow<CalendarEvent?>(null)
    val selectedEvent: StateFlow<CalendarEvent?> = _selectedEvent.asStateFlow()

    // Source filter state (session-only, resets on app restart)
    private val _sourceFilter = MutableStateFlow(CalendarSourceFilter())
    val sourceFilter: StateFlow<CalendarSourceFilter> = _sourceFilter.asStateFlow()

    init {
        // Initialize Outlook MSAL for event operations
        // This is required because EventsViewModel has its own OutlookAuthManager instance
        // (separate from MainScreen's instance which is initialized via MultiAccountSyncViewModel)
        viewModelScope.launch {
            outlookAuthManager.initialize()
        }

    }


    // PERFORMANCE: Shared StateFlow cache per date - eliminates redundant Room subscriptions
    // Multiple UI consumers (badge, EventsGrid, ExpandedEventsList) share the same Flow
    // LRU eviction prevents unbounded memory growth when navigating many days
    private val eventsFlowCache = object : LinkedHashMap<String, StateFlow<List<CalendarEvent>>>(
        MAX_CACHED_DAYS + 1,  // Initial capacity
        0.75f,                // Load factor
        true                  // accessOrder = true for LRU behavior
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StateFlow<List<CalendarEvent>>>?): Boolean {
            val shouldRemove = size > MAX_CACHED_DAYS
            if (shouldRemove) {
                Log.d(TAG, "LRU_CACHE: Evicting oldest entry ${eldest?.key}, cache size: $size")
            }
            return shouldRemove
        }
    }
    private val flowCacheMutex = Mutex()  // Thread-safe access to eventsFlowCache

    // ========== READ OPERATIONS ==========

    /**
     * Get events for a specific date as a Flow (reactive).
     * Combines events from both Google Calendar cache (CalendarEvent) and
     * local-only events (LocalEvent), converting LocalEvents to CalendarEvent format.
     * Applies source filter to show/hide events based on their origin.
     */
    fun getEventsForDate(dayId: String): Flow<List<CalendarEvent>> {
        val cloudEvents = eventRepository.getEventsForDate(dayId)
        val localEvents = localEventRepository.observeEventsForDay(dayId)
            .map { events -> events.map { it.toCalendarEvent() } }

        return combine(cloudEvents, localEvents, _sourceFilter) { cloud, local, filter ->
            val filtered = mutableListOf<CalendarEvent>()

            // Filter cloud events by provider (Google/Outlook)
            // When BOTH filters are ON (default), include ALL cloud events to ensure none are missed
            // Only apply selective filtering when user explicitly toggles one OFF
            when {
                filter.showGoogle && filter.showOutlook -> {
                    // Default: show ALL cloud events (no provider filtering)
                    filtered.addAll(cloud)
                }
                filter.showGoogle -> {
                    // User disabled Outlook, show only Google
                    filtered.addAll(cloud.filter { it.isGoogleEvent })
                }
                filter.showOutlook -> {
                    // User disabled Google, show only Outlook
                    filtered.addAll(cloud.filter { it.isOutlookEvent })
                }
                // else: both disabled, no cloud events shown
            }

            // Filter local events (avoiding duplicates with cloud-synced events)
            if (filter.showLocal) {
                val cloudIds = cloud.map { it.id }.toSet()
                val uniqueLocal = local.filter { it.id !in cloudIds }
                filtered.addAll(uniqueLocal)
            }

            // Sort: all-day events first, then by start time
            filtered.sortedWith(
                compareByDescending<CalendarEvent> { it.isAllDay }
                    .thenBy { it.startTime }
            )
        }
    }

    /**
     * Get a SHARED StateFlow for events on a specific date.
     * Multiple collectors share the same underlying Room subscription.
     *
     * PERFORMANCE: Reduces Room subscriptions from 3 per date to 1 by sharing the Flow.
     * Uses WhileSubscribed(5000ms) strategy:
     * - Keeps collecting for 5 seconds after last UI subscriber leaves
     * - Survives quick config changes (rotation)
     * - Releases resources when user navigates away
     */
    suspend fun getSharedEventsFlow(dayId: String): StateFlow<List<CalendarEvent>> {
        return flowCacheMutex.withLock {
            eventsFlowCache.getOrPut(dayId) {
                createEventsFlow(dayId)
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5000),
                        initialValue = emptyList()
                    )
            }
        }
    }

    /**
     * Create the underlying events Flow for a specific date.
     * This is the same logic as getEventsForDate but extracted for StateFlow sharing.
     */
    private fun createEventsFlow(dayId: String): Flow<List<CalendarEvent>> {
        val cloudEvents = eventRepository.getEventsForDate(dayId)
        val localEvents = localEventRepository.observeEventsForDay(dayId)
            .map { events -> events.map { it.toCalendarEvent() } }

        return combine(cloudEvents, localEvents, _sourceFilter) { cloud, local, filter ->
            val filtered = mutableListOf<CalendarEvent>()

            // Filter cloud events by provider (Google/Outlook)
            when {
                filter.showGoogle && filter.showOutlook -> filtered.addAll(cloud)
                filter.showGoogle -> filtered.addAll(cloud.filter { it.isGoogleEvent })
                filter.showOutlook -> filtered.addAll(cloud.filter { it.isOutlookEvent })
            }

            // Filter local events (avoiding duplicates with cloud-synced events)
            if (filter.showLocal) {
                val cloudIds = cloud.map { it.id }.toSet()
                val uniqueLocal = local.filter { it.id !in cloudIds }
                filtered.addAll(uniqueLocal)
            }

            // Sort: all-day events first, then by start time
            filtered.sortedWith(
                compareByDescending<CalendarEvent> { it.isAllDay }
                    .thenBy { it.startTime }
            )
        }
    }

    /**
     * Clear cached flows for a date or all dates.
     * Call when source filter changes or after data mutations.
     */
    fun invalidateEventsFlowCache(dayId: String? = null) {
        if (dayId != null) {
            eventsFlowCache.remove(dayId)
        } else {
            eventsFlowCache.clear()
        }
        Log.d(TAG, "Events flow cache invalidated: ${dayId ?: "ALL"}")
    }

    /**
     * Get event COUNT for a specific date as a reactive Flow.
     * PERFORMANCE: More efficient than getSharedEventsFlow().map { it.size } because:
     * - Uses COUNT(*) query instead of fetching full event objects
     * - No combine() overhead with source filter (applies filter in query result)
     * - Ideal for badge display where only the count is needed
     */
    fun getEventCountForDate(dayId: String): Flow<Int> {
        val cloudCount = eventRepository.observeEventCountForDate(dayId)
        val localCount = localEventRepository.observeEventCountForDate(dayId)

        return combine(cloudCount, localCount, _sourceFilter) { cloud, local, filter ->
            var count = 0

            // Count cloud events based on filter
            // When both Google and Outlook filters are ON, include all cloud events
            if (filter.showGoogle && filter.showOutlook) {
                count += cloud
            } else if (filter.showGoogle || filter.showOutlook) {
                // For single-source filtering, we use the full count (can't distinguish providers in COUNT query)
                // This is an acceptable simplification since most users won't toggle filters constantly
                count += cloud
            }

            // Count local events
            if (filter.showLocal) {
                count += local
            }

            count
        }
    }

    /**
     * Get events for a specific date (sync, non-reactive).
     * Combines events from both sources.
     */
    suspend fun getEventsForDateSync(dayId: String): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val googleEvents = eventRepository.getEventsForDateSync(dayId)
        val localEvents = localEventRepository.getEventsForDay(dayId).map { it.toCalendarEvent() }

        // Combine both sources, avoiding duplicates
        val googleIds = googleEvents.map { it.id }.toSet()
        val uniqueLocal = localEvents.filter { it.id !in googleIds }
        (googleEvents + uniqueLocal).sortedWith(
            compareByDescending<CalendarEvent> { it.isAllDay }
                .thenBy { it.startTime }
        )
    }

    /**
     * Get a single event by ID
     */
    suspend fun getEventById(eventId: String): CalendarEvent? {
        return eventRepository.getEventById(eventId)
    }

    /**
     * Get all dates that have events (for calendar badges).
     * Combines dates from both Google Calendar and local events.
     */
    suspend fun getDatesWithEvents(): Set<String> = withContext(Dispatchers.IO) {
        val googleDates = eventRepository.getDatesWithEvents()
        val localDates = localEventRepository.getDatesWithEvents()
        googleDates + localDates
    }

    /**
     * Get dates with events for a specific month.
     * Combines dates from both sources.
     */
    suspend fun getDatesWithEventsForMonth(yearMonth: String): Set<String> = withContext(Dispatchers.IO) {
        val googleDates = eventRepository.getDatesWithEventsForMonth(yearMonth)
        val localDates = localEventRepository.getDatesWithEventsForMonth(yearMonth)
        googleDates + localDates
    }

    /**
     * Dates with events in a month and the months before and after it: the grid shows days of both
     * (Oct 2026). The month load (CacheWarmupCoordinator) covers all three, so the refresh after a
     * sync must too, or the neighbouring days lose their dots.
     */
    suspend fun getDatesWithEventsAroundMonth(yearMonth: String): Set<String> {
        val month = java.time.YearMonth.parse(yearMonth)
        return listOf(month.minusMonths(1), month, month.plusMonths(1))
            .flatMap { getDatesWithEventsForMonth(it.toString()) }
            .toSet()
    }

    /**
     * Set the currently selected event
     */
    fun selectEvent(event: CalendarEvent?) {
        _selectedEvent.value = event
    }

    // ========== CREATE OPERATION ==========

    /**
     * Create a new event.
     *
     * In STANDALONE mode (syncEnabled=false):
     * - Creates a local-only event stored in LocalEvent table
     * - No provider API calls
     *
     * In HYBRID mode (syncEnabled=true with provider):
     * - Creates event on provider first
     * - Then caches in CalendarEvent table and LocalEvent table
     */
    fun createEvent(
        calendarId: String,
        title: String,
        date: String,
        startTimeHour: Int,
        startTimeMinute: Int,
        endTimeHour: Int? = null,
        endTimeMinute: Int? = null,
        isAllDay: Boolean = false,
        location: String? = null,
        description: String? = null,
        attendees: String? = null,  // Comma-separated for Google Calendar sync
        syncEnabled: Boolean = false,
        syncProviderId: ProviderId? = null,
        syncAccountId: String? = null,
        localCalendarId: String? = null,
        onResult: (EventOperationResult<CalendarEvent>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _lastOperationMessage.value = "Creating event..."

            val shouldSyncToCloud = syncEnabled && syncProviderId != null && syncAccountId != null
            val resolvedLocalCalendarId = if (!shouldSyncToCloud) {
                localCalendarId ?: resolveDefaultLocalCalendarId()
            } else {
                localCalendarId
            }

            if (!shouldSyncToCloud) {
                // STANDALONE MODE: Create local-only event
                Log.d(TAG, "Creating local-only event (syncEnabled=$syncEnabled)")

                try {
                    val localEvent = createLocalOnlyEvent(
                        title = title,
                        date = date,
                        startTimeHour = startTimeHour,
                        startTimeMinute = startTimeMinute,
                        endTimeHour = endTimeHour,
                        endTimeMinute = endTimeMinute,
                        isAllDay = isAllDay,
                        location = location,
                        description = description,
                        attendees = attendees,
                        localCalendarId = resolvedLocalCalendarId
                    )

                    _lastOperationMessage.value = "Event created"
                    _lastOperationSuccess.value = true
                    Log.i(TAG, "Local event created: ${localEvent.title}")

                    // Convert to CalendarEvent for callback compatibility
                    val calendarEvent = localEvent.toCalendarEvent()
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Success(calendarEvent))
                    return@launch

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create local event", e)
                    _lastOperationMessage.value = "Failed: ${e.message}"
                    _lastOperationSuccess.value = false
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Error(e.message ?: "Unknown error", e))
                    return@launch
                }
            }

            if (!isOnline()) {
                val now = System.currentTimeMillis()
                val startDateTime = LocalDateTime.of(
                    LocalDate.parse(date),
                    if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(startTimeHour, startTimeMinute)
                )
                val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val endMillis = if (isAllDay) {
                    null
                } else if (endTimeHour != null && endTimeMinute != null) {
                    val endDateTime = LocalDateTime.of(
                        LocalDate.parse(date),
                        LocalTime.of(endTimeHour, endTimeMinute)
                    )
                    endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else {
                    startMillis + 60 * 60 * 1000
                }

                val account = accountDao.getById(syncAccountId!!)
                val selection = if (calendarId.isNotBlank()) {
                    selectionDao.getByAccountAndCalendarId(syncAccountId, calendarId)
                } else {
                    null
                }

                val pendingEvent = LocalEvent(
                    title = title,
                    dayId = date,
                    endDayId = date,
                    startTime = startMillis,
                    endTime = endMillis,
                    isAllDay = isAllDay,
                    location = location,
                    description = description,
                    attendees = attendees,
                    providerId = syncProviderId!!.key,
                    accountId = syncAccountId,
                    accountEmail = account?.email,
                    providerEventId = null,
                    providerCalendarId = calendarId.ifBlank { null },
                    providerCalendarName = selection?.calendarName,
                    googleEventId = null,
                    googleCalendarId = if (syncProviderId == ProviderId.GOOGLE) calendarId.ifBlank { null } else null,
                    googleCalendarName = if (syncProviderId == ProviderId.GOOGLE) selection?.calendarName else null,
                    syncEnabled = true,
                    syncStatus = SyncStatus.PENDING_CREATE,
                    createdAt = now,
                    updatedAt = now
                )

                localEventRepository.createEvent(pendingEvent)
                syncScheduler.requestPendingPush()
                _lastOperationMessage.value = "Event created (will sync when online)"
                _lastOperationSuccess.value = true
                _operationInProgress.value = false
                onResult(EventOperationResult.Success(pendingEvent.toCalendarEvent()))
                return@launch
            }

            // HYBRID MODE: Create on provider
            val data = NewEventData(
                calendarId = calendarId,
                title = title,
                date = date,
                startTimeHour = startTimeHour,
                startTimeMinute = startTimeMinute,
                endTimeHour = endTimeHour,
                endTimeMinute = endTimeMinute,
                isAllDay = isAllDay,
                location = location,
                description = description,
                attendees = attendees,
                syncEnabled = syncEnabled
            )

            val result = multiAccountSyncManager.createEvent(
                accountId = syncAccountId!!,
                calendarId = calendarId.ifBlank { null },
                event = data
            )

            result.fold(
                onSuccess = { cloudEvent ->
                    val now = System.currentTimeMillis()
                    val account = accountDao.getById(syncAccountId)
                    val localEvent = LocalEvent(
                        id = UUID.randomUUID().toString(),
                        title = cloudEvent.title,
                        dayId = cloudEvent.dayId,
                        endDayId = cloudEvent.dayId,
                        startTime = cloudEvent.startTime,
                        endTime = cloudEvent.endTime,
                        isAllDay = cloudEvent.isAllDay,
                        location = cloudEvent.location,
                        description = cloudEvent.description,
                        attendees = cloudEvent.attendees,
                        colorHex = cloudEvent.colorHex,
                        providerId = syncProviderId.key,
                        accountId = syncAccountId,
                        accountEmail = account?.email,
                        providerEventId = cloudEvent.id,
                        providerCalendarId = cloudEvent.calendarId,
                        providerCalendarName = cloudEvent.calendarName,
                        googleEventId = if (syncProviderId == ProviderId.GOOGLE) cloudEvent.id else null,
                        googleCalendarId = if (syncProviderId == ProviderId.GOOGLE) cloudEvent.calendarId else null,
                        googleCalendarName = if (syncProviderId == ProviderId.GOOGLE) cloudEvent.calendarName else null,
                        syncEnabled = true,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = now,
                        isReadOnly = cloudEvent.isReadOnly,
                        createdAt = now,
                        updatedAt = now
                    )

                    localEventRepository.createEvent(localEvent)
                    _lastOperationMessage.value = "Event created"
                    _lastOperationSuccess.value = true
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Success(cloudEvent))
                },
                onFailure = { e ->
                    _operationInProgress.value = false
                    val message = when (e) {
                        is CalendarProviderException.AuthenticationException ->
                            "Please sign in to ${syncProviderId.displayName} again"
                        is CalendarProviderException.PermissionException ->
                            "No permission for this ${syncProviderId.displayName} calendar"
                        is CalendarProviderException.NetworkException ->
                            "No internet connection"
                        else -> "Create failed: ${e.message}"
                    }
                    _lastOperationMessage.value = message
                    _lastOperationSuccess.value = false
                    onResult(EventOperationResult.Error(message, e as? Exception))
                }
            )
        }
    }

    /**
     * Create a local-only event (not synced to Google).
     * Used in standalone mode.
     */
    private suspend fun createLocalOnlyEvent(
        title: String,
        date: String,
        startTimeHour: Int,
        startTimeMinute: Int,
        endTimeHour: Int?,
        endTimeMinute: Int?,
        isAllDay: Boolean,
        location: String?,
        description: String?,
        attendees: String? = null,
        localCalendarId: String?
    ): LocalEvent = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Calculate start time in millis
        val startDateTime = LocalDateTime.of(
            LocalDate.parse(date),
            if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(startTimeHour, startTimeMinute)
        )
        val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Calculate end time in millis (default to 1 hour after start)
        val endMillis = if (isAllDay) {
            null
        } else if (endTimeHour != null && endTimeMinute != null) {
            val endDateTime = LocalDateTime.of(
                LocalDate.parse(date),
                LocalTime.of(endTimeHour, endTimeMinute)
            )
            endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            startMillis + 60 * 60 * 1000 // 1 hour default
        }

        val localEvent = LocalEvent(
            title = title,
            dayId = date,
            endDayId = date,
            startTime = startMillis,
            endTime = endMillis,
            isAllDay = isAllDay,
            location = location,
            description = description,
            attendees = attendees,
            syncEnabled = false,
            syncStatus = SyncStatus.LOCAL_ONLY,
            createdAt = now,
            updatedAt = now,
            localCalendarId = localCalendarId
        )

        localEventRepository.createEvent(localEvent)
    }

    // ========== UPDATE OPERATION ==========

    /**
     * Update an existing event.
     *
     * In STANDALONE mode (not signed in OR syncEnabled=false OR local-only event):
     * - Updates the local event in LocalEvent table
     * - No Google Calendar API calls
     *
     * In HYBRID mode (signed in AND syncEnabled=true AND Google event):
     * - Updates event on Google Calendar first
     * - Then updates local cache
     *
     * UPGRADE mode (local event + syncEnabled=true + syncProviderId!=null):
     * - Creates the event on Google Calendar (new event, since it doesn't exist there)
     * - Updates LocalEvent with sync metadata (googleEventId, syncStatus, etc.)
     */
    fun updateEvent(
        eventId: String,
        calendarId: String,
        title: String,
        date: String,
        startTimeHour: Int,
        startTimeMinute: Int,
        endTimeHour: Int? = null,
        endTimeMinute: Int? = null,
        isAllDay: Boolean = false,
        location: String? = null,
        description: String? = null,
        attendees: String? = null,  // Comma-separated for Google Calendar sync
        syncEnabled: Boolean = false,
        syncProviderId: ProviderId? = null,  // Target provider when upgrading local event to sync
        syncAccountId: String? = null,  // Account ID for the target provider
        localCalendarId: String? = null,
        onResult: (EventOperationResult<CalendarEvent>) -> Unit = {}
    ) {
        viewModelScope.launch {
            Log.d(TAG, "=== updateEvent CALLED ===")
            Log.d(TAG, "eventId: $eventId")
            Log.d(TAG, "calendarId: $calendarId")
            Log.d(TAG, "syncEnabled: $syncEnabled")
            Log.d(TAG, "syncProviderId: $syncProviderId")
            Log.d(TAG, "syncAccountId: $syncAccountId")
            _operationInProgress.value = true
            _lastOperationMessage.value = "Updating event..."

            val cloudEvent = withContext(Dispatchers.IO) {
                eventRepository.getEventById(eventId)
            }

            val startDateTime = LocalDateTime.of(
                LocalDate.parse(date),
                if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(startTimeHour, startTimeMinute)
            )
            val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val endMillis = if (isAllDay) {
                null
            } else if (endTimeHour != null && endTimeMinute != null) {
                val endDateTime = LocalDateTime.of(
                    LocalDate.parse(date),
                    LocalTime.of(endTimeHour, endTimeMinute)
                )
                endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                startMillis + 60 * 60 * 1000
            }

            if (cloudEvent != null && cloudEvent.accountId.isNotEmpty()) {
                val data = UpdateEventData(
                    eventId = eventId,
                    calendarId = cloudEvent.calendarId,
                    title = title,
                    date = date,
                    startTimeHour = startTimeHour,
                    startTimeMinute = startTimeMinute,
                    endTimeHour = endTimeHour,
                    endTimeMinute = endTimeMinute,
                    isAllDay = isAllDay,
                    location = location,
                    description = description,
                    attendees = attendees,
                    syncEnabled = syncEnabled
                )

                val result = multiAccountSyncManager.updateEvent(
                    accountId = cloudEvent.accountId,
                    calendarId = cloudEvent.calendarId,
                    eventId = eventId,
                    event = data
                )

                result.fold(
                    onSuccess = { updatedEvent ->
                        withContext(Dispatchers.IO) {
                            val localEvent = localEventDao.getByProviderEventId(eventId)
                                ?: localEventDao.getByGoogleEventId(eventId)
                            if (localEvent != null) {
                                val now = System.currentTimeMillis()
                                val isGoogle = cloudEvent.providerId == ProviderId.GOOGLE.key
                                val updatedLocal = localEvent.copy(
                                    title = title,
                                    dayId = date,
                                    startTime = startMillis,
                                    endTime = endMillis,
                                    isAllDay = isAllDay,
                                    location = location,
                                    description = description,
                                    attendees = attendees,
                                    colorHex = updatedEvent.colorHex,
                                    providerId = cloudEvent.providerId,
                                    accountId = cloudEvent.accountId,
                                    accountEmail = cloudEvent.accountEmail,
                                    providerEventId = cloudEvent.id,
                                    providerCalendarId = cloudEvent.calendarId,
                                    providerCalendarName = cloudEvent.calendarName,
                                    googleEventId = if (isGoogle) cloudEvent.id else null,
                                    googleCalendarId = if (isGoogle) cloudEvent.calendarId else null,
                                    googleCalendarName = if (isGoogle) cloudEvent.calendarName else null,
                                    syncEnabled = true,
                                    syncStatus = SyncStatus.SYNCED,
                                    lastSyncedAt = now,
                                    updatedAt = now
                                )
                                localEventRepository.updateEvent(updatedLocal)
                            }
                        }

                        _lastOperationMessage.value = "Event updated"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = updatedEvent
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(updatedEvent))
                    },
                    onFailure = { e ->
                        val providerName = when (cloudEvent.providerId) {
                            ProviderId.GOOGLE.key -> ProviderId.GOOGLE.displayName
                            ProviderId.OUTLOOK.key -> ProviderId.OUTLOOK.displayName
                            else -> "cloud"
                        }
                        val message = when (e) {
                            is CalendarProviderException.AuthenticationException ->
                                "Please sign in to $providerName again"
                            is CalendarProviderException.PermissionException ->
                                "No permission for this $providerName calendar"
                            is CalendarProviderException.NetworkException ->
                                "No internet connection"
                            else -> "Update failed: ${e.message}"
                        }
                        _lastOperationMessage.value = message
                        _lastOperationSuccess.value = false
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Error(message, e as? Exception))
                    }
                )
                return@launch
            }

            val localEvent = withContext(Dispatchers.IO) {
                localEventRepository.getEventById(eventId)
            }
            Log.d(TAG, "localEvent found: ${localEvent != null}")
            Log.d(TAG, "localEvent.isLocalOnly(): ${localEvent?.isLocalOnly()}")
            Log.d(TAG, "syncEnabled: $syncEnabled")
            Log.d(TAG, "syncProviderId: $syncProviderId")
            Log.d(TAG, "syncAccountId: $syncAccountId")
            val isUpgradingToSync = localEvent != null &&
                localEvent.isLocalOnly() &&
                syncEnabled &&
                syncProviderId != null &&
                syncAccountId != null
            Log.d(TAG, "isUpgradingToSync: $isUpgradingToSync")

            if (isUpgradingToSync) {
                Log.d(TAG, "Upgrading local event to sync (provider=$syncProviderId, account=$syncAccountId)")
                upgradeLocalEventToSync(
                    eventId = eventId,
                    calendarId = calendarId.ifBlank { null },
                    title = title,
                    date = date,
                    startTimeHour = startTimeHour,
                    startTimeMinute = startTimeMinute,
                    endTimeHour = endTimeHour,
                    endTimeMinute = endTimeMinute,
                    isAllDay = isAllDay,
                    location = location,
                    description = description,
                    attendees = attendees,
                    providerId = syncProviderId!!,
                    accountId = syncAccountId!!,
                    onResult = onResult
                )
                return@launch
            }

            val resolvedLocalCalendarId = localCalendarId ?: resolveDefaultLocalCalendarId()

            Log.d(TAG, "Updating local-only event (syncEnabled=$syncEnabled, calendarId=$calendarId)")

            if (localEvent != null) {
                try {
                    val updatedEvent = updateLocalOnlyEvent(
                        eventId = eventId,
                        title = title,
                        date = date,
                        startTimeHour = startTimeHour,
                        startTimeMinute = startTimeMinute,
                        endTimeHour = endTimeHour,
                        endTimeMinute = endTimeMinute,
                        isAllDay = isAllDay,
                        location = location,
                        description = description,
                        attendees = attendees,
                        localCalendarId = resolvedLocalCalendarId
                    )

                    _lastOperationMessage.value = "Event updated"
                    _lastOperationSuccess.value = true
                    Log.i(TAG, "Local event updated: ${updatedEvent.title}")

                    val calendarEvent = updatedEvent.toCalendarEvent()
                    _selectedEvent.value = calendarEvent
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Success(calendarEvent))
                    return@launch

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update local event", e)
                    _lastOperationMessage.value = "Failed: ${e.message}"
                    _lastOperationSuccess.value = false
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Error(e.message ?: "Unknown error", e))
                    return@launch
                }
            }

            _lastOperationMessage.value = "Event not found"
            _lastOperationSuccess.value = false
            _operationInProgress.value = false
            onResult(EventOperationResult.Error("Event not found"))
            return@launch
        }
    }

    /**
     * Update a local-only event (not synced to Google).
     * Used in standalone mode.
     */
    private suspend fun updateLocalOnlyEvent(
        eventId: String,
        title: String,
        date: String,
        startTimeHour: Int,
        startTimeMinute: Int,
        endTimeHour: Int?,
        endTimeMinute: Int?,
        isAllDay: Boolean,
        location: String?,
        description: String?,
        attendees: String? = null,
        localCalendarId: String?
    ): LocalEvent = withContext(Dispatchers.IO) {
        // Get existing event
        val existingEvent = localEventRepository.getEventById(eventId)
            ?: throw IllegalStateException("Event not found: $eventId")

        val now = System.currentTimeMillis()

        // Calculate start time in millis
        val startDateTime = LocalDateTime.of(
            LocalDate.parse(date),
            if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(startTimeHour, startTimeMinute)
        )
        val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Calculate end time in millis (default to 1 hour after start)
        val endMillis = if (isAllDay) {
            null
        } else if (endTimeHour != null && endTimeMinute != null) {
            val endDateTime = LocalDateTime.of(
                LocalDate.parse(date),
                LocalTime.of(endTimeHour, endTimeMinute)
            )
            endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            startMillis + 60 * 60 * 1000 // 1 hour default
        }

        val updatedEvent = existingEvent.copy(
            title = title,
            dayId = date,
            startTime = startMillis,
            endTime = endMillis,
            isAllDay = isAllDay,
            location = location,
            description = description,
            attendees = attendees,
            updatedAt = now,
            localCalendarId = localCalendarId
        )

        localEventRepository.updateEvent(updatedEvent)
    }

    /**
     * Upgrade a local event to sync with a cloud calendar (Google or Outlook).
     * This creates the event on the cloud provider and updates the LocalEvent with sync metadata.
     *
     * @param accountId The account ID to sync to (from MultiAccountSyncManager)
     * @param providerId The provider type (GOOGLE or OUTLOOK)
     */
    private suspend fun upgradeLocalEventToSync(
        eventId: String,
        calendarId: String?,
        title: String,
        date: String,
        startTimeHour: Int,
        startTimeMinute: Int,
        endTimeHour: Int?,
        endTimeMinute: Int?,
        isAllDay: Boolean,
        location: String?,
        description: String?,
        attendees: String?,
        providerId: ProviderId,
        accountId: String,
        onResult: (EventOperationResult<CalendarEvent>) -> Unit
    ) {
        try {
            _lastOperationMessage.value = "Syncing to ${providerId.displayName}..."

            // Create the event data
            val createData = NewEventData(
                calendarId = calendarId ?: "",
                title = title,
                date = date,
                startTimeHour = startTimeHour,
                startTimeMinute = startTimeMinute,
                endTimeHour = endTimeHour,
                endTimeMinute = endTimeMinute,
                isAllDay = isAllDay,
                location = location,
                description = description,
                attendees = attendees,
                syncEnabled = true
            )

            // Use MultiAccountSyncManager which supports both Google and Outlook
            val result = multiAccountSyncManager.createEvent(
                accountId = accountId,
                calendarId = calendarId?.ifBlank { null },
                event = createData
            )

            result.fold(
                onSuccess = { cloudEvent ->
                    // Event created on cloud provider, now update LocalEvent with sync metadata
                    withContext(Dispatchers.IO) {
                        val existingEvent = localEventRepository.getEventById(eventId)
                        if (existingEvent != null) {
                            val now = System.currentTimeMillis()
                            val account = accountDao.getById(accountId)
                            val isGoogle = providerId == ProviderId.GOOGLE

                            // Calculate times
                            val startDateTime = LocalDateTime.of(
                                LocalDate.parse(date),
                                if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(startTimeHour, startTimeMinute)
                            )
                            val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endMillis = if (isAllDay) {
                                null
                            } else if (endTimeHour != null && endTimeMinute != null) {
                                val endDateTime = LocalDateTime.of(
                                    LocalDate.parse(date),
                                    LocalTime.of(endTimeHour, endTimeMinute)
                                )
                                endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            } else {
                                startMillis + 60 * 60 * 1000
                            }

                            // Update LocalEvent with new data AND sync metadata
                            val upgradedEvent = existingEvent.copy(
                                title = title,
                                dayId = date,
                                startTime = startMillis,
                                endTime = endMillis,
                                isAllDay = isAllDay,
                                location = location,
                                description = description,
                                attendees = attendees,
                                providerId = providerId.key,
                                accountId = accountId,
                                accountEmail = account?.email,
                                providerEventId = cloudEvent.id,
                                providerCalendarId = cloudEvent.calendarId,
                                providerCalendarName = cloudEvent.calendarName,
                                googleEventId = if (isGoogle) cloudEvent.id else null,
                                googleCalendarId = if (isGoogle) cloudEvent.calendarId else null,
                                googleCalendarName = if (isGoogle) cloudEvent.calendarName else null,
                                syncEnabled = true,
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = now,
                                updatedAt = now
                            )
                            localEventRepository.updateEvent(upgradedEvent)
                            semanticGeniusService.handleEventPromotion(existingEvent.id, cloudEvent.id)
                            Log.i(TAG, "LocalEvent upgraded to ${providerId.displayName}: ${upgradedEvent.id} -> ${cloudEvent.id}")
                        }
                    }

                    _lastOperationMessage.value = "Event synced to ${providerId.displayName}"
                    _lastOperationSuccess.value = true
                    _selectedEvent.value = cloudEvent
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Success(cloudEvent))
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to create event on ${providerId.displayName}", e)
                    _lastOperationMessage.value = when (e) {
                        is CalendarProviderException.AuthenticationException ->
                            "Please sign in to ${providerId.displayName} again"
                        is CalendarProviderException.PermissionException ->
                            "No permission for this ${providerId.displayName} calendar"
                        is CalendarProviderException.NetworkException ->
                            "No internet connection"
                        else -> "Sync failed: ${e.message}"
                    }
                    _lastOperationSuccess.value = false
                    _operationInProgress.value = false
                    onResult(EventOperationResult.Error(e.message ?: "Unknown error", e as? Exception))
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade event to sync", e)
            _lastOperationMessage.value = "Sync failed: ${e.message}"
            _lastOperationSuccess.value = false
            _operationInProgress.value = false
            onResult(EventOperationResult.Error(e.message ?: "Unknown error", e))
        }
    }

    // ========== DELETE OPERATION ==========

    /**
     * Delete an event.
     *
     * Handles both local events (LocalEvent table) and cloud events:
     * - For LocalEvent: Uses LocalEventRepository (soft-delete if synced, hard-delete if local-only)
     * - For cloud cache events: Uses MultiAccountSyncManager (provider delete)
     *
     * @param eventId The event ID (local UUID or Google event ID)
     * @param calendarId The calendar ID ("local" for local events, provider calendar ID for synced)
     * @param onResult Callback with the operation result
     */
    fun deleteEvent(
        eventId: String,
        calendarId: String,
        onResult: (EventOperationResult<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _lastOperationMessage.value = "Deleting event..."

            // STEP 1: Check if this is a cloud event (CalendarEvent cache)
            val cloudEvent = withContext(Dispatchers.IO) {
                eventRepository.getEventById(eventId)
            }

            if (cloudEvent != null && cloudEvent.accountId.isNotEmpty()) {
                val result = multiAccountSyncManager.deleteEvent(
                    accountId = cloudEvent.accountId,
                    calendarId = cloudEvent.calendarId,
                    eventId = eventId
                )

                result.fold(
                    onSuccess = {
                        withContext(Dispatchers.IO) {
                            val localEvent = localEventDao.getByProviderEventId(eventId)
                                ?: localEventDao.getByGoogleEventId(eventId)
                            if (localEvent != null) {
                                localEventRepository.permanentlyDelete(localEvent.id)
                            }
                            // Clean up semantic profile for the cloud event
                            semanticGeniusService.deleteEventProfile(OcrSource.CALENDAR_EVENT, eventId)
                        }
                        _lastOperationMessage.value = "Event deleted"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = null
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(Unit))
                    },
                    onFailure = { e ->
                        val providerName = when (cloudEvent.providerId) {
                            ProviderId.GOOGLE.key -> ProviderId.GOOGLE.displayName
                            ProviderId.OUTLOOK.key -> ProviderId.OUTLOOK.displayName
                            else -> "cloud"
                        }
                        val message = when (e) {
                            is CalendarProviderException.AuthenticationException ->
                                "Please sign in to $providerName again"
                            is CalendarProviderException.PermissionException ->
                                "No permission for this $providerName calendar"
                            is CalendarProviderException.NetworkException ->
                                "No internet connection"
                            else -> "Delete failed: ${e.message}"
                        }
                        _lastOperationMessage.value = message
                        _lastOperationSuccess.value = false
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Error(message, e as? Exception))
                    }
                )
                return@launch
            }

            // STEP 2: Check if this is a LocalEvent (by local ID or provider event ID)
            val localEvent = withContext(Dispatchers.IO) {
                localEventRepository.getEventById(eventId)
                    ?: localEventRepository.getByGoogleEventId(eventId)
                    ?: localEventDao.getByProviderEventId(eventId)
            }

            if (localEvent != null) {
                Log.d(TAG, "Deleting LocalEvent: ${localEvent.id} (googleEventId=${localEvent.googleEventId})")
                deleteLocalEvent(localEvent, onResult)
                return@launch
            }

            _lastOperationMessage.value = "Event not found"
            _lastOperationSuccess.value = false
            _operationInProgress.value = false
            onResult(EventOperationResult.Error("Event not found"))
        }
    }

    /**
     * Delete a LocalEvent (handles both STANDALONE and HYBRID modes).
     *
     * - STANDALONE mode (local-only): Immediate hard delete
     * - HYBRID mode (synced, online): Delete from the source provider first, then hard delete locally
     * - HYBRID mode (synced, offline): Soft-delete (PENDING_DELETE), sync later
     * - HYBRID mode (never synced): Immediate hard delete (never reached the provider)
     */
    private suspend fun deleteLocalEvent(
        event: LocalEvent,
        onResult: (EventOperationResult<Unit>) -> Unit
    ) {
        try {
            // Outlook events, and Google events that know their account (Oct 2026), are deleted
            // through the account's own credential. The Google branch further down uses the last
            // Google sign-in, which can be empty while the account syncs fine: it then skipped
            // Google, deleted only the tablet copy, and the next sync brought the event back.
            val deleteProvider = when (event.providerId) {
                ProviderId.OUTLOOK.key -> ProviderId.OUTLOOK
                ProviderId.GOOGLE.key -> ProviderId.GOOGLE
                else -> null
            }?.takeIf {
                event.syncEnabled &&
                    !event.accountId.isNullOrBlank() &&
                    !event.providerEventId.isNullOrBlank() &&
                    !event.providerCalendarId.isNullOrBlank()
            }

            if (deleteProvider != null) {
                Log.d(TAG, "Attempting ${deleteProvider.displayName} delete for synced event: ${event.providerEventId}")
                val result = multiAccountSyncManager.deleteEvent(
                    accountId = event.accountId!!,
                    calendarId = event.providerCalendarId!!,
                    eventId = event.providerEventId!!
                )

                result.fold(
                    onSuccess = {
                        withContext(Dispatchers.IO) {
                            localEventRepository.permanentlyDelete(event.id)
                        }
                        _lastOperationMessage.value = "Event deleted"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = null
                        Log.i(TAG, "Synced event deleted (${deleteProvider.displayName} + local): ${event.id}")
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(Unit))
                    },
                    onFailure = { e ->
                        when (e) {
                            is CalendarProviderException.NetworkException -> {
                                withContext(Dispatchers.IO) {
                                    localEventRepository.deleteEvent(event)
                                }
                                syncScheduler.requestPendingPush()
                                _lastOperationMessage.value = "Event will be deleted when online"
                                _lastOperationSuccess.value = true
                                _selectedEvent.value = null
                                Log.i(TAG, "Synced event marked PENDING_DELETE for ${deleteProvider.displayName} (offline): ${event.id}")
                                _operationInProgress.value = false
                                onResult(EventOperationResult.Success(Unit))
                            }
                            is CalendarProviderException.AuthenticationException -> {
                                _lastOperationMessage.value = "Please sign in to ${deleteProvider.displayName} again"
                                _lastOperationSuccess.value = false
                                Log.e(TAG, "Failed to delete synced ${deleteProvider.displayName} event (auth): ${event.id}", e)
                                _operationInProgress.value = false
                                onResult(EventOperationResult.Error(_lastOperationMessage.value!!, e))
                            }
                            is CalendarProviderException.PermissionException -> {
                                _lastOperationMessage.value = "No permission for this ${deleteProvider.displayName} calendar"
                                _lastOperationSuccess.value = false
                                Log.e(TAG, "Failed to delete synced ${deleteProvider.displayName} event (permission): ${event.id}", e)
                                _operationInProgress.value = false
                                onResult(EventOperationResult.Error(_lastOperationMessage.value!!, e))
                            }
                            else -> {
                                _lastOperationMessage.value = "Delete failed: ${e.message}"
                                _lastOperationSuccess.value = false
                                Log.e(TAG, "Failed to delete synced ${deleteProvider.displayName} event: ${event.id}", e)
                                _operationInProgress.value = false
                                onResult(EventOperationResult.Error(_lastOperationMessage.value!!, e as? Exception))
                            }
                        }
                    }
                )
                return
            }

            val isSignedIn = authManager.isSignedIn()
            val hasGoogleId = event.googleEventId != null && event.googleCalendarId != null
            val shouldDeleteFromGoogle = isSignedIn && event.syncEnabled && hasGoogleId

            if (shouldDeleteFromGoogle) {
                // HYBRID MODE: Try to delete from Google first
                Log.d(TAG, "Attempting Google delete for synced event: ${event.googleEventId}")
                val googleResult = syncManager.deleteEvent(
                    event.googleEventId!!,
                    event.googleCalendarId!!
                )

                when (googleResult) {
                    is EventOperationResult.Success -> {
                        // Google confirmed deletion, permanently delete local copy
                        withContext(Dispatchers.IO) {
                            localEventRepository.permanentlyDelete(event.id)
                        }
                        _lastOperationMessage.value = "Event deleted"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = null
                        Log.i(TAG, "Synced event deleted (Google + local): ${event.id}")
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(Unit))
                    }
                    is EventOperationResult.NoNetwork -> {
                        // Offline: soft-delete locally, will sync deletion later
                        withContext(Dispatchers.IO) {
                            localEventRepository.deleteEvent(event)
                        }
                        syncScheduler.requestPendingPush()
                        _lastOperationMessage.value = "Event will be deleted when online"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = null
                        Log.i(TAG, "Synced event marked PENDING_DELETE (offline): ${event.id}")
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(Unit))
                    }
                    is EventOperationResult.NotSignedIn -> {
                        // Auth lost: just delete locally (can't sync anyway)
                        withContext(Dispatchers.IO) {
                            localEventRepository.permanentlyDelete(event.id)
                        }
                        _lastOperationMessage.value = "Event deleted"
                        _lastOperationSuccess.value = true
                        _selectedEvent.value = null
                        Log.i(TAG, "Synced event deleted locally (not signed in): ${event.id}")
                        _operationInProgress.value = false
                        onResult(EventOperationResult.Success(Unit))
                    }
                    else -> {
                        // Other errors (permission denied, API error, etc.)
                        _lastOperationMessage.value = when (googleResult) {
                            is EventOperationResult.PermissionDenied -> "No permission to delete this event"
                            is EventOperationResult.Error -> "Delete failed: ${googleResult.message}"
                            else -> "Delete failed"
                        }
                        _lastOperationSuccess.value = false
                        Log.e(TAG, "Failed to delete synced event from Google: ${event.id}")
                        _operationInProgress.value = false
                        onResult(googleResult)
                    }
                }
            } else {
                // STANDALONE MODE or never-synced event: immediate local delete
                Log.d(TAG, "Deleting local-only event: ${event.id} (syncEnabled=${event.syncEnabled}, hasGoogleId=$hasGoogleId)")
                withContext(Dispatchers.IO) {
                    localEventRepository.deleteEvent(event)
                }
                _lastOperationMessage.value = "Event deleted"
                _lastOperationSuccess.value = true
                _selectedEvent.value = null
                Log.i(TAG, "Local event deleted: ${event.id}")
                _operationInProgress.value = false
                onResult(EventOperationResult.Success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete local event: ${event.id}", e)
            _lastOperationMessage.value = "Delete failed: ${e.message}"
            _lastOperationSuccess.value = false
            _operationInProgress.value = false
            onResult(EventOperationResult.Error(e.message ?: "Unknown error", e))
        }
    }

    /**
     * MOVE a cloud event to local storage.
     *
     * This is a DESTRUCTIVE operation:
     * 1. Creates or updates a LocalEvent with the event content
     * 2. Deletes the event from the cloud provider
     * 3. Removes the CalendarEvent cache entry
     */
    suspend fun moveCloudToLocal(cloudEvent: CalendarEvent): Result<LocalEvent> {
        _operationInProgress.value = true
        return try {
            withContext(Dispatchers.IO) {
                if (!isOnline()) {
                    _lastOperationMessage.value = "Requires internet connection"
                    _lastOperationSuccess.value = false
                    return@withContext Result.failure(IllegalStateException("No internet connection"))
                }

                val defaultLocalCalendarId = resolveDefaultLocalCalendarId()
                val now = System.currentTimeMillis()

                val existingLocal = localEventDao.getByProviderEventId(cloudEvent.id)
                    ?: localEventDao.getByGoogleEventId(cloudEvent.id)

                val localEvent = if (existingLocal != null) {
                    val updatedEvent = existingLocal.copy(
                        title = cloudEvent.title,
                        dayId = cloudEvent.dayId,
                        startTime = cloudEvent.startTime,
                        endTime = cloudEvent.endTime,
                        isAllDay = cloudEvent.isAllDay,
                        location = cloudEvent.location,
                        description = cloudEvent.description,
                        attendees = cloudEvent.attendees,
                        colorHex = cloudEvent.colorHex,
                        isReadOnly = false,
                        providerId = "LOCAL",
                        accountId = null,
                        accountEmail = null,
                        providerEventId = null,
                        providerCalendarId = null,
                        providerCalendarName = null,
                        googleEventId = null,
                        googleCalendarId = null,
                        googleCalendarName = null,
                        localCalendarId = defaultLocalCalendarId,
                        syncEnabled = false,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                        updatedAt = now
                    )
                    localEventRepository.updateEvent(updatedEvent)
                    updatedEvent
                } else {
                    val newEvent = LocalEvent(
                        id = UUID.randomUUID().toString(),
                        title = cloudEvent.title,
                        dayId = cloudEvent.dayId,
                        endDayId = cloudEvent.dayId,
                        startTime = cloudEvent.startTime,
                        endTime = cloudEvent.endTime,
                        isAllDay = cloudEvent.isAllDay,
                        location = cloudEvent.location,
                        description = cloudEvent.description,
                        attendees = cloudEvent.attendees,
                        colorHex = cloudEvent.colorHex,
                        providerId = "LOCAL",
                        accountId = null,
                        accountEmail = null,
                        providerEventId = null,
                        providerCalendarId = null,
                        providerCalendarName = null,
                        googleEventId = null,
                        googleCalendarId = null,
                        googleCalendarName = null,
                        localCalendarId = defaultLocalCalendarId,
                        syncEnabled = false,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                        createdAt = now,
                        updatedAt = now
                    )
                    localEventRepository.createEvent(newEvent)
                    newEvent
                }

                val deleteResult = multiAccountSyncManager.deleteEvent(
                    accountId = cloudEvent.accountId,
                    calendarId = cloudEvent.calendarId,
                    eventId = cloudEvent.id
                )

                if (deleteResult.isFailure) {
                    Log.w(TAG, "Cloud delete failed for ${cloudEvent.id}: ${deleteResult.exceptionOrNull()?.message}")
                    _lastOperationMessage.value = "Moved locally, but cloud delete failed"
                } else {
                    _lastOperationMessage.value = "Event moved to local only"
                }
                _lastOperationSuccess.value = true

                eventRepository.deleteById(cloudEvent.id)
                semanticGeniusService.deleteEventProfile(OcrSource.CALENDAR_EVENT, cloudEvent.id)

                Result.success(localEvent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move cloud event to local", e)
            _lastOperationMessage.value = "Failed to move event: ${e.message}"
            _lastOperationSuccess.value = false
            Result.failure(e)
        } finally {
            _operationInProgress.value = false
        }
    }

    // ========== UTILITY ==========

    /**
     * Resolve the default local calendar ID for local-only persistence.
     */
    private suspend fun resolveDefaultLocalCalendarId(): String? = withContext(Dispatchers.IO) {
        localCalendarDao.getDefault()?.id
    }

    /**
     * Check if the device has an active internet connection.
     */
    private fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Clear the operation message
     */
    fun clearOperationMessage() {
        _lastOperationMessage.value = null
        _lastOperationSuccess.value = null
    }

    // ========== SOURCE FILTER ==========

    /**
     * Toggle a calendar source filter.
     * Returns true if the toggle was successful, false if blocked (last source).
     *
     * @param source The calendar source to toggle
     * @return true if toggled, false if this is the last active source
     */
    fun toggleSource(source: CalendarSource): Boolean {
        val currentFilter = _sourceFilter.value
        if (!currentFilter.isEnabled(source)) {
            // Enabling a source is always allowed
            _sourceFilter.value = currentFilter.toggle(source)
            Log.d(TAG, "Source filter toggled ON: $source")
            return true
        }
        // Trying to disable - check if this is the last active source
        if (!currentFilter.canDeselect(source)) {
            Log.d(TAG, "Cannot disable last source: $source")
            return false
        }
        _sourceFilter.value = currentFilter.toggle(source)
        Log.d(TAG, "Source filter toggled OFF: $source")
        return true
    }

    /**
     * Set the entire source filter state.
     * Used when initializing or resetting filters.
     */
    fun setSourceFilter(filter: CalendarSourceFilter) {
        _sourceFilter.value = filter
        Log.d(TAG, "Source filter updated: local=${filter.showLocal}, google=${filter.showGoogle}, outlook=${filter.showOutlook}")
    }

    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        return authManager.isSignedIn()
    }

    /**
     * Check if an event is editable (user has write permission)
     */
    fun canEditEvent(event: CalendarEvent): Boolean {
        return !event.isReadOnly && authManager.isSignedIn()
    }

    // ========== MODE SWITCHING (Local Calendars Feature) ==========

    /**
     * Get count of synced events (for mode switch dialog)
     */
    suspend fun getSyncedEventCount(): Int = withContext(Dispatchers.IO) {
        localEventRepository.getSyncedEventCount()
    }

    /**
     * Convert all synced events to local-only (for Hybrid → Standalone switch)
     */
    suspend fun convertAllToLocalOnly() = withContext(Dispatchers.IO) {
        localEventRepository.convertAllToLocalOnly()
    }

    /**
     * Delete all synced events (for Hybrid → Standalone switch)
     */
    suspend fun deleteAllSyncedEvents() = withContext(Dispatchers.IO) {
        localEventRepository.deleteAllSyncedEvents()
    }

    /**
     * Clear all cloud-synced events from cache.
     * Used when disconnecting all accounts or switching Hybrid → Standalone.
     * This clears:
     * - CalendarEvent table (cloud-synced events cache from Google/Outlook)
     * - Synced LocalEvents (events that were uploaded to cloud)
     */
    suspend fun clearAllSyncedEvents() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Clearing all synced events from cache")
        // Clear cloud events cache (CalendarEvent table)
        eventRepository.clearCache()
        // Also clear synced LocalEvents
        localEventRepository.deleteAllSyncedEvents()
        Log.i(TAG, "All synced events cleared")
    }

    /**
     * Assign events with null localCalendarId to a calendar
     */
    suspend fun assignNullEventsToDefaultCalendar(defaultCalendarId: String) = withContext(Dispatchers.IO) {
        localEventRepository.assignNullEventsToDefaultCalendar(defaultCalendarId)
    }

    /**
     * Refresh events display (invalidate cache)
     */
    fun refreshEvents() {
        localEventRepository.invalidateCache()
        eventRepository.invalidateEventsCache()
        invalidateEventsFlowCache()  // Clear shared StateFlows so new Room subscriptions are created
    }

    // ========== DUE TASKS (Events Panel "Due" Tab) ==========

    /**
     * Get due tasks for the Events Panel "Due" tab.
     *
     * When viewing today: shows tasks due today + overdue from other dates.
     * When viewing other dates: shows only tasks due on that specific date.
     *
     * @param dayId The selected date in format "yyyy-MM-dd"
     * @return Flow of DueTasksData containing due today and overdue tasks
     */
    fun getDueTasksForDate(dayId: String): Flow<DueTasksData> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return combine(
            taskRepository.getTasksDueOnDateFlow(dayId),
            taskRepository.getOverdueTasksFlow(today)
        ) { dueTasks, overdueTasks ->
            if (dayId == today) {
                // On today: show tasks due today + every overdue task. T17 (Oct 2026): overdue
                // tasks written on today's page were filtered out; due-today and overdue
                // (due day before today) can't overlap, so nothing is listed twice.
                DueTasksData(
                    dueTodayTasks = dueTasks,
                    overdueTasks = overdueTasks
                )
            } else {
                // On other dates: only show tasks due on that specific date
                DueTasksData(
                    dueTodayTasks = dueTasks,
                    overdueTasks = emptyList()
                )
            }
        }
    }

    /**
     * Get total count of due tasks for badge display.
     * When viewing today: includes overdue tasks.
     * When viewing other dates: only tasks due on that date.
     *
     * @param dayId The selected date in format "yyyy-MM-dd"
     * @return Flow of total due task count
     */
    fun getDueTaskCount(dayId: String): Flow<Int> =
        getDueTasksForDate(dayId).map { it.totalCount }
}
