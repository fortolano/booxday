package com.boox.day.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.DueTasksData
import com.boox.day.viewmodel.EventsViewModel
import java.time.LocalDate

/**
 * Shared day-panel subscription & multi-note popup state used by both `EventsGrid`
 * and `ExpandedEventsList` (Phase 5 / H14: eliminates duplicated Room/BooxNotes subscriptions).
 */
@Stable
internal class DayPanelState(
    val sortedEvents: List<CalendarEvent>,
    val dueTasksData: DueTasksData,
    val eventToNoteLinks: Map<String, String>,
    val eventNoteCounts: Map<String, Int>,
    val eventsWithAttention: Set<String>,
    val notesPopupEventId: String?,
    val notesPopupEventTitle: String?,
    val notesPopupNotes: List<BooxNoteIndex>,
    val notesPopupY: Float,
    val openNotesPopup: (eventId: String, yPos: Float) -> Unit,
    val dismissNotesPopup: () -> Unit
)

@Composable
internal fun rememberDayPanelState(
    selectedDate: String,
    eventsViewModel: EventsViewModel,
    booxNoteRepository: BooxNoteRepository,
    noteLinkRefreshTrigger: Int
): DayPanelState {
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(selectedDate) {
        eventsViewModel.getSharedEventsFlow(selectedDate).collect { eventsList ->
            events = eventsList
        }
    }

    val dueFlowToday = LocalDate.now().toString()
    val dueTasksData by remember(selectedDate, dueFlowToday) {
        eventsViewModel.getDueTasksForDate(selectedDate)
    }.collectAsState(initial = DueTasksData.EMPTY)

    var eventToNoteLinks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var eventNoteCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var eventsWithAttention by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedDate, noteLinkRefreshTrigger, events) {
        val eventIds = events.map { it.id }
        if (eventIds.isEmpty()) {
            eventToNoteLinks = emptyMap()
            eventNoteCounts = emptyMap()
            eventsWithAttention = emptySet()
            return@LaunchedEffect
        }
        booxNoteRepository.observeEventBadgeData(eventIds).collect { badgeData ->
            eventToNoteLinks = badgeData.eventToNoteLinks
            eventNoteCounts = badgeData.noteCounts
            eventsWithAttention = badgeData.eventsWithAttention
        }
    }

    var notesPopupEventId by remember { mutableStateOf<String?>(null) }
    var notesPopupEventTitle by remember { mutableStateOf<String?>(null) }
    var notesPopupNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var notesPopupY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedDate) {
        notesPopupEventId = null
    }

    LaunchedEffect(notesPopupEventId) {
        notesPopupEventId?.let { eventId ->
            notesPopupEventTitle = events.find { it.id == eventId }?.title
            notesPopupNotes = booxNoteRepository.getNotesLinkedToEvent(eventId)
        }
    }

    return DayPanelState(
        sortedEvents = events,
        dueTasksData = dueTasksData,
        eventToNoteLinks = eventToNoteLinks,
        eventNoteCounts = eventNoteCounts,
        eventsWithAttention = eventsWithAttention,
        notesPopupEventId = notesPopupEventId,
        notesPopupEventTitle = notesPopupEventTitle,
        notesPopupNotes = notesPopupNotes,
        notesPopupY = notesPopupY,
        openNotesPopup = { eventId, yPos ->
            notesPopupEventId = eventId
            notesPopupY = yPos
        },
        dismissNotesPopup = {
            notesPopupEventId = null
        }
    )
}
