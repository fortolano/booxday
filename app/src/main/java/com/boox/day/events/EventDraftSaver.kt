package com.boox.day.events

import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.model.WritableCalendar
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.dialogs.calendarForNewEvent
import com.boox.day.ui.dialogs.eventSaveTargets
import com.boox.day.sync.EventOperationResult
import com.boox.day.viewmodel.EventsViewModel

/** Where a new event goes when nobody picks: what Add Event starts with. */
data class EventTarget(
    val syncEnabled: Boolean,
    val calendar: WritableCalendar?,
    val serviceName: String?,
    val localCalendarId: String?
) {
    /** "Work (Google)", or the tablet. */
    val label: String
        get() = calendar?.let { if (serviceName != null) "${it.name} ($serviceName)" else it.name } ?: "This device only"
}

/** What MainScreen knows about accounts, for features outside it. */
object EventTargets {
    private var latest: () -> EventTarget = { EventTarget(false, null, null, null) }

    fun publish(target: () -> EventTarget) {
        latest = target
    }

    fun current(): EventTarget = latest()
}

/** A new event, as Add Event hands it over. */
data class EventDraft(
    val calendarId: String,
    val title: String,
    val date: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int?,
    val endMinute: Int?,
    val isAllDay: Boolean,
    val location: String?,
    val description: String?,
    val attendees: String?,
    val syncEnabled: Boolean,
    val syncProviderId: ProviderId?,
    val syncAccountId: String?,
    val localCalendarId: String?
)

object DefaultEventTarget {
    /** The calendars Add Event offers: the first Google and Outlook account's, and every CalDAV account's. */
    fun offered(all: List<WritableCalendar>, firstGoogleAccountId: String?, firstOutlookAccountId: String?): List<WritableCalendar> =
        all.filter { calendar ->
            (calendar.providerId == ProviderId.GOOGLE && calendar.accountId == firstGoogleAccountId) ||
                (calendar.providerId == ProviderId.OUTLOOK && calendar.accountId == firstOutlookAccountId) ||
                calendar.providerId == ProviderId.CALDAV  // each CalDAV account is its own line
        }

    fun localCalendar(localCalendars: List<LocalCalendar>): String? =
        localCalendars.find { it.isDefault }?.id ?: localCalendars.firstOrNull()?.id

    /** What a new event in Add Event starts on, left untouched. [calendars] is [offered]. */
    fun of(
        calendars: List<WritableCalendar>,
        hasGoogle: Boolean,
        hasOutlook: Boolean,
        hasCalDav: Boolean,
        syncMode: AppSettings.SyncMode,
        localCalendars: List<LocalCalendar>
    ): EventTarget {
        val local = localCalendar(localCalendars)
        val sync = syncMode == AppSettings.SyncMode.HYBRID && (hasGoogle || hasOutlook || hasCalDav)
        if (!sync) return EventTarget(syncEnabled = false, calendar = null, serviceName = null, localCalendarId = local)
        val line = eventSaveTargets(calendars, hasGoogle, hasOutlook).firstOrNull()
        val calendar = line?.let { target ->
            calendarForNewEvent(calendars.filter { it.providerId == target.providerId && (target.accountId == null || it.accountId == target.accountId) })
        }
        return EventTarget(syncEnabled = calendar != null, calendar = calendar, serviceName = line?.name, localCalendarId = local)
    }
}

object EventDraftSaver {
    /** Saves a new event and says how it went, as Add Event always has. [onCreated] runs on success only. */
    fun create(viewModel: EventsViewModel, draft: EventDraft, successText: String? = "Event created", onFailed: (() -> Unit)? = null, onCreated: () -> Unit) {
        viewModel.createEvent(
            calendarId = draft.calendarId,
            title = draft.title,
            date = draft.date,
            startTimeHour = draft.startHour,
            startTimeMinute = draft.startMinute,
            endTimeHour = draft.endHour,
            endTimeMinute = draft.endMinute,
            isAllDay = draft.isAllDay,
            location = draft.location?.takeIf { it.isNotBlank() },
            description = draft.description?.takeIf { it.isNotBlank() },
            attendees = draft.attendees?.takeIf { it.isNotBlank() },
            syncEnabled = draft.syncEnabled,
            syncProviderId = draft.syncProviderId,
            syncAccountId = draft.syncAccountId,
            localCalendarId = draft.localCalendarId
        ) { result ->
            when (result) {
                is EventOperationResult.Success -> {
                    onCreated()
                    successText?.let { SnackState.globalSnackFlow.tryEmit(SnackConf(text = it, duration = SnackConf.DURATION_STANDARD)) }
                }
                is EventOperationResult.Error -> {
                    // Dialog stays open for retry
                    val errorMessage = viewModel.lastOperationMessage.value ?: result.message
                    SnackState.globalSnackFlow.tryEmit(SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR))
                    onFailed?.invoke()
                }
                else -> {
                    // NoNetwork, NotSignedIn, PermissionDenied
                    val errorMessage = viewModel.lastOperationMessage.value ?: "Operation failed"
                    SnackState.globalSnackFlow.tryEmit(SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR))
                    onFailed?.invoke()
                }
            }
        }
    }
}
