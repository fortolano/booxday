package com.boox.day.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.model.ParsedEventData
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.WritableCalendar
import com.boox.day.events.DefaultEventTarget
import com.boox.day.events.EventDraft
import com.boox.day.events.EventDraftSaver
import com.boox.day.proposals.FROM_HANDWRITING
import com.boox.day.sync.EventOperationResult
import com.boox.day.sync.MultiAccountSyncViewModel
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.UnifiedOverlayRegistry
import com.boox.day.ui.dialogs.AddEditEventDialog
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.ui.dialogs.EventDetailsDialog
import com.boox.day.ui.dialogs.VoiceEventDialog
import com.boox.day.ui.dialogs.chosenCalendar
import com.boox.day.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Controller for Add/Edit Event, Voice Event, and Event Details dialogs (Phase 5 / H10 + H14).
 * Keeps open/edit-target state in [rememberSaveable] so unsaved drafts survive Activity recreation.
 */
@Stable
internal class EventDraftHostState(
    val showAddEventState: MutableState<Boolean>,
    val showVoiceEventState: MutableState<Boolean>,
    val showEventDetailsState: MutableState<Boolean>,
    val selectedEventForDetailsState: MutableState<CalendarEvent?>,
    val editingEventState: MutableState<CalendarEvent?>,
    val editingEventIdState: MutableState<String?>,
    val editingEventIsLocalState: MutableState<Boolean>,
    val parsedEventDataState: MutableState<ParsedEventData?>,
    val requestedAddEventDateState: MutableState<String?>
) {
    fun openNewEvent(date: String? = null) {
        editingEventState.value = null
        editingEventIdState.value = null
        editingEventIsLocalState.value = false
        parsedEventDataState.value = null
        requestedAddEventDateState.value = date
        showAddEventState.value = true
    }

    fun openNewEventFrom(draft: ParsedEventData) {
        editingEventState.value = null
        editingEventIdState.value = null
        editingEventIsLocalState.value = false
        parsedEventDataState.value = draft
        requestedAddEventDateState.value = null
        showAddEventState.value = true
    }

    fun openVoiceEvent() {
        parsedEventDataState.value = null
        showVoiceEventState.value = true
    }

    fun openEventDetails(event: CalendarEvent) {
        selectedEventForDetailsState.value = event
        showEventDetailsState.value = true
    }

    fun closeAddEvent() {
        showAddEventState.value = false
        editingEventState.value = null
        editingEventIdState.value = null
        editingEventIsLocalState.value = false
        parsedEventDataState.value = null
        requestedAddEventDateState.value = null
    }
}

@Composable
internal fun rememberEventDraftHostState(): EventDraftHostState {
    val showAddEvent = rememberSaveable { mutableStateOf(false) }
    val showVoiceEvent = remember { mutableStateOf(false) }
    val showEventDetails = remember { mutableStateOf(false) }
    val selectedEventForDetails = remember { mutableStateOf<CalendarEvent?>(null) }
    val editingEvent = remember { mutableStateOf<CalendarEvent?>(null) }
    val editingEventId = rememberSaveable { mutableStateOf<String?>(null) }
    val editingEventIsLocal = rememberSaveable { mutableStateOf(false) }
    val parsedEventData = remember { mutableStateOf<ParsedEventData?>(null) }
    val requestedAddEventDate = rememberSaveable { mutableStateOf<String?>(null) }

    return remember {
        EventDraftHostState(
            showAddEventState = showAddEvent,
            showVoiceEventState = showVoiceEvent,
            showEventDetailsState = showEventDetails,
            selectedEventForDetailsState = selectedEventForDetails,
            editingEventState = editingEvent,
            editingEventIdState = editingEventId,
            editingEventIsLocalState = editingEventIsLocal,
            parsedEventDataState = parsedEventData,
            requestedAddEventDateState = requestedAddEventDate
        )
    }
}

@Composable
internal fun EventDraftHost(
    state: EventDraftHostState,
    overlayRegistry: UnifiedOverlayRegistry,
    selectedDate: String,
    eventsViewModel: EventsViewModel,
    eventRepository: CalendarEventRepository,
    localEventRepository: LocalEventRepository,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    noteLinkRefreshTrigger: Int,
    multiAccountUiState: MultiAccountSyncViewModel.UiState,
    writableCalendars: List<WritableCalendar>,
    localCalendars: List<LocalCalendar>,
    syncModeSetting: AppSettings.SyncMode,
    defaultSyncEnabled: Boolean,
    isSignedIn: Boolean
) {
    val scope = rememberCoroutineScope()
    val operationInProgress by eventsViewModel.operationInProgress.collectAsState()
    val hasGoogleAccount = multiAccountUiState.googleAccounts.isNotEmpty()
    val hasOutlookAccount = multiAccountUiState.outlookAccounts.isNotEmpty()
    val hasCalDavAccount = multiAccountUiState.calDavAccounts.isNotEmpty()
    val hasAnyExternalAccount = hasGoogleAccount || hasOutlookAccount || hasCalDavAccount

    LaunchedEffect(state.showAddEventState.value) {
        if (!state.showAddEventState.value) {
            state.requestedAddEventDateState.value = null
        }
    }

    // Reload existing event if Activity recreated while editing (H10)
    LaunchedEffect(state.showAddEventState.value, state.editingEventIdState.value) {
        val id = state.editingEventIdState.value
        if (state.showAddEventState.value && id != null && state.editingEventState.value == null) {
            val restored = withContext(Dispatchers.IO) {
                if (state.editingEventIsLocalState.value) localEventRepository.getEventById(id)?.toCalendarEvent()
                else eventRepository.getEventById(id) ?: localEventRepository.getEventById(id)?.toCalendarEvent()
            }
            if (restored != null) {
                state.editingEventState.value = restored
            } else {
                state.closeAddEvent()
            }
        }
    }

    // Load linked Boox Notes only while Event Details is open
    var linkedBooxNotesForEvent by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    val detailEvent = state.selectedEventForDetailsState.value
    LaunchedEffect(detailEvent, noteLinkRefreshTrigger) {
        linkedBooxNotesForEvent = if (detailEvent != null) {
            withContext(Dispatchers.IO) { booxNoteRepository.getNotesLinkedToEvent(detailEvent.id) }
        } else {
            emptyList()
        }
    }

    overlayRegistry.Overlay("voice_event", state.showVoiceEventState.value) {
        VoiceEventDialog(
            currentDate = try {
                LocalDate.parse(selectedDate)
            } catch (_: Exception) {
                LocalDate.now()
            },
            onEventParsed = { parsed ->
                state.showVoiceEventState.value = false
                state.openNewEventFrom(parsed)
            },
            onDismiss = { state.showVoiceEventState.value = false }
        )
    }

    val addEventReady = state.showAddEventState.value &&
        (state.editingEventIdState.value == null || state.editingEventState.value != null)
    overlayRegistry.Overlay("add_edit_event", state.showAddEventState.value) {
        if (!addEventReady) return@Overlay
        val editingEvent = state.editingEventState.value
        val parsedEventData = state.parsedEventDataState.value
        val calculatedEndTime = parsedEventData?.calculateEndTime()
        val existingEventProviderId = editingEvent?.let { event ->
            if (event.calendarId == "local") null else ProviderId.fromKey(event.providerId)
        }
        val primaryGoogleAccountId = multiAccountUiState.googleAccounts.firstOrNull()?.account?.id
        val primaryOutlookAccountId = multiAccountUiState.outlookAccounts.firstOrNull()?.account?.id
        val writableCalendarsForDialog = DefaultEventTarget.offered(
            writableCalendars,
            primaryGoogleAccountId,
            primaryOutlookAccountId
        )

        var localEventForEdit by remember { mutableStateOf<LocalEvent?>(null) }
        LaunchedEffect(editingEvent) {
            localEventForEdit = if (editingEvent != null && editingEvent.calendarId == "local") {
                withContext(Dispatchers.IO) { localEventRepository.getEventById(editingEvent.id) }
            } else null
        }

        AddEditEventDialog(
            onDismiss = { state.closeAddEvent() },
            onSave = { calendarId, title, date, startHour, startMin, endHour, endMin, isAllDay, location, description, attendees, syncEnabled, localCalendarId, syncProviderId, chosenAccountId ->
                val selectedCalendar = chosenCalendar(writableCalendarsForDialog, syncProviderId, chosenAccountId, calendarId)
                val syncAccountId = when {
                    editingEvent?.accountId?.isNotBlank() == true -> editingEvent.accountId
                    selectedCalendar != null -> selectedCalendar.accountId
                    syncProviderId == ProviderId.GOOGLE -> primaryGoogleAccountId
                    syncProviderId == ProviderId.OUTLOOK -> primaryOutlookAccountId
                    else -> null
                }
                if (editingEvent != null) {
                    eventsViewModel.updateEvent(
                        eventId = editingEvent.id,
                        calendarId = calendarId,
                        title = title,
                        date = date,
                        startTimeHour = startHour,
                        startTimeMinute = startMin,
                        endTimeHour = endHour,
                        endTimeMinute = endMin,
                        isAllDay = isAllDay,
                        location = location?.takeIf { it.isNotBlank() },
                        description = description?.takeIf { it.isNotBlank() },
                        attendees = attendees?.takeIf { it.isNotBlank() },
                        syncEnabled = syncEnabled,
                        syncProviderId = syncProviderId,
                        syncAccountId = syncAccountId,
                        localCalendarId = localCalendarId
                    ) { result ->
                        when (result) {
                            is EventOperationResult.Success -> {
                                state.closeAddEvent()
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = "Event updated", duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                            is EventOperationResult.Error -> {
                                val errorMessage = eventsViewModel.lastOperationMessage.value ?: result.message
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                )
                            }
                            else -> {
                                val errorMessage = eventsViewModel.lastOperationMessage.value ?: "Operation failed"
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                )
                            }
                        }
                    }
                } else {
                    EventDraftSaver.create(
                        eventsViewModel,
                        EventDraft(
                            calendarId, title, date, startHour, startMin, endHour, endMin, isAllDay,
                            location, description, attendees, syncEnabled, syncProviderId, syncAccountId, localCalendarId
                        )
                    ) {
                        state.closeAddEvent()
                    }
                }
            },
            onMoveToLocal = { cloudEvent ->
                scope.launch {
                    eventsViewModel.moveCloudToLocal(cloudEvent).fold(
                        onSuccess = { localEvent ->
                            state.closeAddEvent()
                            state.selectedEventForDetailsState.value = localEvent.toCalendarEvent()
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "Event moved to local only", duration = SnackConf.DURATION_STANDARD)
                            )
                        },
                        onFailure = { e ->
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = e.message ?: "Failed to move event", duration = SnackConf.DURATION_STANDARD)
                            )
                        }
                    )
                }
            },
            onVoiceInput = {
                AiKeyGate.withKey {
                    state.closeAddEvent()
                    state.showVoiceEventState.value = true
                }
            },
            writableCalendars = writableCalendarsForDialog,
            existingEvent = editingEvent,
            initialDate = parsedEventData?.date ?: state.requestedAddEventDateState.value ?: selectedDate,
            isLoading = operationInProgress,
            prefillTitle = parsedEventData?.title,
            prefillDate = parsedEventData?.date,
            prefillStartHour = parsedEventData?.startHour,
            prefillStartMinute = parsedEventData?.startMinute,
            prefillEndHour = calculatedEndTime?.first,
            prefillEndMinute = calculatedEndTime?.second,
            prefillIsAllDay = parsedEventData?.isAllDay,
            prefillLocation = parsedEventData?.location,
            prefillDescription = if (syncModeSetting == AppSettings.SyncMode.HYBRID && hasAnyExternalAccount)
                parsedEventData?.getDescriptionOnly()
            else
                parsedEventData?.getEffectiveDescription(),
            prefillAttendees = parsedEventData?.attendees?.takeIf { it.isNotEmpty() },
            prefillConfidenceLow = parsedEventData?.let { !it.isHighConfidence } ?: false,
            prefillFromHandwriting = parsedEventData?.originalIntent == FROM_HANDWRITING,
            syncMode = syncModeSetting,
            isSignedIn = hasAnyExternalAccount,
            defaultSyncEnabled = defaultSyncEnabled,
            existingSyncEnabled = when (editingEvent?.calendarId) {
                null -> null
                "local" -> false
                else -> true
            },
            localCalendars = localCalendars,
            existingLocalCalendarId = localEventForEdit?.localCalendarId,
            hasGoogleAccount = hasGoogleAccount,
            hasOutlookAccount = hasOutlookAccount,
            hasCalDavAccount = hasCalDavAccount,
            existingProviderId = existingEventProviderId
        )
    }

    val detailsOpen = state.showEventDetailsState.value && detailEvent != null
    overlayRegistry.Overlay("event_details", detailsOpen) {
        val event = detailEvent ?: return@Overlay
        EventDetailsDialog(
            event = event,
            onDismiss = {
                state.showEventDetailsState.value = false
                state.selectedEventForDetailsState.value = null
            },
            onEdit = if (!event.isReadOnly) {
                {
                    state.editingEventState.value = event
                    state.editingEventIdState.value = event.id
                    state.editingEventIsLocalState.value = event.calendarId == "local"
                    state.showEventDetailsState.value = false
                    state.showAddEventState.value = true
                }
            } else null,
            onDelete = if (!event.isReadOnly) {
                {
                    eventsViewModel.deleteEvent(
                        eventId = event.id,
                        calendarId = event.calendarId
                    ) { result ->
                        when (result) {
                            is EventOperationResult.Success -> {
                                state.showEventDetailsState.value = false
                                state.selectedEventForDetailsState.value = null
                                val message = eventsViewModel.lastOperationMessage.value ?: "Event deleted"
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                            else -> {
                                val errorMessage = eventsViewModel.lastOperationMessage.value ?: "Delete failed"
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = errorMessage, duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        }
                    }
                }
            } else null,
            isLoading = operationInProgress,
            linkedBooxNotes = linkedBooxNotesForEvent,
            onBooxNoteClick = { note -> booxNotesIntegration.openNote(note.uniqueId, note.nameInBoox) },
            onViewBooxNote = { note -> BooxNotesPlaces.openNote(note.uniqueId) },
            onLinkANote = { BooxNotesPlaces.openLinkANote(event) },
            onCreateBooxNote = { booxNotesIntegration.createQuickNote() },
            syncMode = syncModeSetting,
            isSignedIn = isSignedIn,
            syncEnabled = isSignedIn,
            syncStatus = null,
            lastSyncedAt = event.syncedAt,
            onToggleSync = null,
            localCalendarName = localCalendars.find { it.isDefault }?.name,
            localCalendarColorHex = localCalendars.find { it.isDefault }?.colorHex
        )
    }
}
