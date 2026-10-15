package com.boox.day.meetings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boox.day.actionitems.ActionItemsUi
import com.boox.day.data.db.CalendarEvent
import com.boox.day.editor.EditorTabManager
import com.boox.day.sync.EventMemoService
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.utils.DateTimeFormatUtils
import com.boox.day.viewmodel.EventsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Meeting mode (Oct 2026): notes for a calendar event on Memos pages linked to it. The first page gets
 * the meeting's title and time typed at the top (EventMemoService); pages added after it belong to the
 * same meeting (EditorControlTower). Its action items read those pages.
 */
object MeetingNotes {
    /** From 10 minutes before a meeting until it ends, opening notes means this meeting. */
    private const val EARLY_MS = 10 * 60_000L
    private const val DEFAULT_LENGTH_MS = 60 * 60_000L

    fun isHappeningNow(event: CalendarEvent, now: Long): Boolean =
        !event.isAllDay && now >= event.startTime - EARLY_MS && now < (event.endTime ?: (event.startTime + DEFAULT_LENGTH_MS))

    /**
     * The event's notes: the ones it has, or new ones (the day's open page when it's empty, else a new
     * page) with its title and time on top. Null (with a message) when no page could be made.
     */
    suspend fun start(context: Context, event: CalendarEvent): EventMemoService.LinkedMemo? {
        val service = EventMemoService(context.applicationContext)
        return when (val result = service.createOrGetMemo(event)) {
            is EventMemoService.MemoResult.Error -> {
                SnackState.globalSnackFlow.tryEmit(SnackConf(text = "Couldn't start notes: ${result.message}", duration = SnackConf.DURATION_STANDARD))
                null
            }
            else -> service.getLinkedMemo(event.id)
        }
    }

    /**
     * Shows a notes page: the Memos tab on its day (MainScreen has already selected the day and made the
     * page the day's open page), then the page itself once the canvas shows that day.
     */
    suspend fun showOnCanvas(tabManager: EditorTabManager, date: String, pageId: String) {
        tabManager.switchToMemos(date, pageId)
        val state = withTimeoutOrNull(3_000L) {
            var shown = tabManager.activeEditorState
            while (shown?.dayDate != date) {
                delay(50)
                shown = tabManager.activeEditorState
            }
            shown
        } ?: return
        if (state.currentPageId == pageId) return
        state.refreshDayPageInfo()
        tabManager.activeControlTower?.navigateToPage(pageId)
    }
}

/** What the meeting-notes UI asks MainScreen for, and the event picker of the Memos toolbar. */
object MeetingNotesUi {
    data class PageToShow(val date: String, val pageId: String)

    private val _pages = MutableSharedFlow<PageToShow>(extraBufferCapacity = 1)
    /** MainScreen selects the day and shows the page on the Memos tab. */
    val pages: SharedFlow<PageToShow> = _pages.asSharedFlow()

    fun showPage(date: String, pageId: String) {
        _pages.tryEmit(PageToShow(date, pageId))
    }

    /** The day whose events the picker lists (Memos toolbar › Meeting notes); null when closed. */
    var pickerDate by mutableStateOf<String?>(null)
        private set

    val isOpen: Boolean get() = pickerDate != null

    fun openPicker(date: String) {
        pickerDate = date
    }

    fun closePicker() {
        pickerDate = null
    }
}

@Composable
fun MeetingNotesHost() {
    val date = MeetingNotesUi.pickerDate ?: return
    InkaTheme {
        key(date) { MeetingPickerPopup(date, onDismiss = MeetingNotesUi::closePicker) }
    }
}

/**
 * Memos toolbar › Meeting notes: the meeting happening now opens straight away; otherwise (or when two
 * overlap) the day's events to choose from. [Close] at the bottom.
 */
@Composable
private fun MeetingPickerPopup(date: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventsViewModel: EventsViewModel = viewModel()
    var events by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var withNotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var opening by remember { mutableStateOf<CalendarEvent?>(null) }
    val now = remember { System.currentTimeMillis() }
    val isToday = date == LocalDate.now().toString()

    fun open(event: CalendarEvent) {
        opening = event
        scope.launch {
            val memo = MeetingNotes.start(context, event)
            onDismiss()
            if (memo != null) MeetingNotesUi.showPage(memo.date, memo.pageIds.first())
        }
    }

    LaunchedEffect(date) {
        val list = eventsViewModel.getEventsForDateSync(date)
        withNotes = EventMemoService(context.applicationContext).getLinkedEventIds()
        val current = if (isToday) list.filter { MeetingNotes.isHappeningNow(it, now) } else emptyList()
        if (current.size == 1) open(current.first()) else events = list
    }

    NotesPopupFrame(onDismiss = onDismiss) {
        Text("Meeting notes", fontSize = EInkTheme.ModernDialog.titleFontSize, fontWeight = FontWeight.Bold, color = EInkTheme.ModernDialog.titleColor)
        Text(dayLabel(date), fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .heightIn(max = EInkTheme.PopupOverlay.maxHeight - 150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val list = events
            when {
                opening != null -> Message("Opening notes for ${opening?.title}…", italic = true)
                list == null -> Message("Loading the day's events…", italic = true)
                list.isEmpty() -> Message("No events on this day. Notes start from a calendar event.")
                else -> list.forEach { event ->
                    EventRow(
                        event = event,
                        now = isToday && MeetingNotes.isHappeningNow(event, now),
                        hasNotes = event.id in withNotes,
                        onClick = { open(event) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        EinkSecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EventRow(event: CalendarEvent, now: Boolean, hasNotes: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .border(1.dp, if (now) EInkTheme.Colors.primary else EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, color = EInkTheme.Colors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(timeLabel(event), "Now".takeIf { now }, "Has notes".takeIf { hasNotes }).joinToString(" · "),
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}

/**
 * The event's notes in its details (meeting mode): start them, or open them and their action items.
 * [onDismiss] closes the event details when the notes page opens.
 */
@Composable
fun MeetingNotesSection(event: CalendarEvent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember(event.id) { mutableStateOf<EventMemoService.LinkedMemo?>(null) }
    var loaded by remember(event.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(event.id) {
        notes = EventMemoService(context.applicationContext).getLinkedMemo(event.id)
        loaded = true
    }

    fun openNotes() {
        busy = true
        scope.launch {
            val memo = MeetingNotes.start(context, event)
            busy = false
            if (memo != null) {
                onDismiss()
                MeetingNotesUi.showPage(memo.date, memo.pageIds.first())
            }
        }
    }

    Spacer(Modifier.height(EInkTheme.Global.paddingMedium))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(20.dp))
            Text("Meeting notes", fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
            val memo = notes
            Text(
                text = when {
                    !loaded -> ""
                    memo == null -> "Not started"
                    else -> "In Memos · ${memo.pageIds.size} page${if (memo.pageIds.size == 1) "" else "s"}"
                },
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
        val memo = notes
        if (loaded) {
            if (memo == null) {
                EinkSecondaryButton(text = "Take notes in Memos", onClick = ::openNotes, enabled = !busy, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EinkSecondaryButton(
                        text = "Action items",
                        onClick = { ActionItemsUi.open(memo.date, memo.pageIds, title = "From your notes · ${event.title}") },
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(text = "Open notes", onClick = ::openNotes, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Centered card over the screen; a tap outside closes it. */
@Composable
private fun NotesPopupFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 8.dp)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .noRippleClickable { }  // taps inside don't dismiss
                    .padding(EInkTheme.ModernDialog.padding)
            ) { content() }
        }
    }
}

@Composable
private fun Message(text: String, italic: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = EInkTheme.Colors.darkGray,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun timeLabel(event: CalendarEvent): String =
    if (event.isAllDay) "All day" else DateTimeFormatUtils.formatTimeRange(event.startTime, event.endTime)

private fun dayLabel(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) }.getOrDefault(date)
