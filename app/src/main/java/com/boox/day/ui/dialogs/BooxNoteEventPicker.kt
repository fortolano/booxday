package com.boox.day.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import com.boox.day.editor.ui.TaskDatePickerContent
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Choose Event and Link a Note (Nov 2026): the same place from either side, on the window's own surface
// (no popup on a popup). Same day first, then the days around it, then any day.

private sealed interface PickerStep {
    data object Nearby : PickerStep
    data object Calendar : PickerStep
    data class Day(val date: String) : PickerStep
}

@Composable
internal fun BooxNoteEventPicker(
    note: BooxNoteIndex,
    linkedEventId: String?,
    suggestedEventId: String?,
    loadEventsInRange: suspend (from: String, to: String) -> List<CalendarEvent>,
    onPick: (CalendarEvent) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchDays = BooxNoteIndexer.DAY_MODE_SEARCH_DAYS.toLong()
    var step by remember { mutableStateOf<PickerStep>(PickerStep.Nearby) }
    var nearby by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var dayEvents by remember { mutableStateOf<List<CalendarEvent>?>(null) }

    LaunchedEffect(note.uniqueId) {
        val noteDay = parseNoteDay(note.originalDate)
        nearby = if (noteDay == null) {
            emptyList()
        } else {
            loadEventsInRange(
                noteDay.minusDays(searchDays).format(DateTimeFormatter.ISO_LOCAL_DATE),
                noteDay.plusDays(searchDays).format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        }
    }
    val currentStep = step
    LaunchedEffect(currentStep) {
        if (currentStep is PickerStep.Day) {
            dayEvents = null
            dayEvents = loadEventsInRange(currentStep.date, currentStep.date)
        }
    }

    PickerSurface(
        title = "Choose Event",
        onBack = { if (currentStep == PickerStep.Nearby) onBack() else step = PickerStep.Nearby },
        onClose = onClose,
        header = {
            NoteThumbnailRow(note)
        },
        modifier = modifier
    ) {
        when (currentStep) {
            PickerStep.Nearby -> {
                val events = nearby
                if (events == null) {
                    PickerNote("Loading events…")
                } else {
                    val sameDay = events
                        .filter { it.dayId == note.originalDate }
                        .sortedWith(
                            compareBy<CalendarEvent> { it.isAllDay }
                                .thenBy { abs(it.startTime - note.originalTimestamp) }
                        )
                    val around = events
                        .filter { it.dayId != note.originalDate }
                        .sortedWith(compareBy<CalendarEvent> { it.dayId }.thenBy { it.isAllDay }.thenBy { it.startTime })
                    PickerSection("Same day · ${formatDayShort(note.originalDate)}")
                    if (sameDay.isEmpty()) PickerNote("No events on this day")
                    sameDay.forEach { event ->
                        PickerEventRow(note, event, showDay = false, linkedEventId, suggestedEventId) { onPick(event) }
                    }
                    PickerSection("Within ${BooxNoteIndexer.DAY_MODE_SEARCH_DAYS} days")
                    if (around.isEmpty()) PickerNote("No events in the days around it")
                    around.forEach { event ->
                        PickerEventRow(note, event, showDay = true, linkedEventId, suggestedEventId) { onPick(event) }
                    }
                    PickAnotherDayRow { step = PickerStep.Calendar }
                }
            }

            PickerStep.Calendar -> TaskDatePickerContent(
                currentDate = note.originalDate,
                selectedDate = null,
                onDateSelected = { step = PickerStep.Day(it) }
            )

            is PickerStep.Day -> {
                PickerSection("Events on ${formatDayShort(currentStep.date)}")
                val events = dayEvents
                when {
                    events == null -> PickerNote("Loading events…")
                    events.isEmpty() -> PickerNote("No events on this day")
                    else -> events
                        .sortedWith(compareBy<CalendarEvent> { it.isAllDay }.thenBy { it.startTime })
                        .forEach { event ->
                            PickerEventRow(note, event, showDay = false, linkedEventId, suggestedEventId) { onPick(event) }
                        }
                }
                PickAnotherDayRow { step = PickerStep.Calendar }
            }
        }
    }
}

/** Link a Note: the same place from the event's side, notes in plain time order */
@Composable
internal fun BooxNoteNotePicker(
    event: CalendarEvent,
    notes: List<BooxNoteIndex>,
    linkedEventOf: (BooxNoteIndex) -> String?,
    onPick: (BooxNoteIndex) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchDays = BooxNoteIndexer.DAY_MODE_SEARCH_DAYS.toLong()
    var step by remember { mutableStateOf<PickerStep>(PickerStep.Nearby) }
    val eventDay = remember(event.dayId) { parseNoteDay(event.dayId) }
    val currentStep = step
    val byTime = compareBy<BooxNoteIndex> { it.originalTimestamp }

    PickerSurface(
        title = "Link a Note",
        onBack = { if (currentStep == PickerStep.Nearby) onBack() else step = PickerStep.Nearby },
        onClose = onClose,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = event.title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = eventWhen(event, withDay = true),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    maxLines = 1
                )
            }
        },
        modifier = modifier
    ) {
        when (currentStep) {
            PickerStep.Nearby -> {
                val sameDay = notes.filter { it.originalDate == event.dayId }.sortedWith(byTime)
                val around = notes.filter { note ->
                    val day = parseNoteDay(note.originalDate)
                    note.originalDate != event.dayId && day != null && eventDay != null &&
                        abs(java.time.temporal.ChronoUnit.DAYS.between(eventDay, day)) <= searchDays
                }.sortedWith(byTime)
                PickerSection("Same day · ${formatDayShort(event.dayId)}")
                if (sameDay.isEmpty()) PickerNote("No notes on this day")
                sameDay.forEach { note ->
                    PickerNoteRow(note, showDay = false, linkedHere = linkedEventOf(note) == event.id) { onPick(note) }
                }
                PickerSection("Within ${BooxNoteIndexer.DAY_MODE_SEARCH_DAYS} days")
                if (around.isEmpty()) PickerNote("No notes in the days around it")
                around.forEach { note ->
                    PickerNoteRow(note, showDay = true, linkedHere = linkedEventOf(note) == event.id) { onPick(note) }
                }
                PickAnotherDayRow { step = PickerStep.Calendar }
            }

            PickerStep.Calendar -> TaskDatePickerContent(
                currentDate = event.dayId,
                selectedDate = null,
                onDateSelected = { step = PickerStep.Day(it) }
            )

            is PickerStep.Day -> {
                PickerSection("Notes on ${formatDayShort(currentStep.date)}")
                val dayNotes = notes.filter { it.originalDate == currentStep.date }.sortedWith(byTime)
                if (dayNotes.isEmpty()) PickerNote("No notes on this day")
                dayNotes.forEach { note ->
                    PickerNoteRow(note, showDay = false, linkedHere = linkedEventOf(note) == event.id) { onPick(note) }
                }
                PickAnotherDayRow { step = PickerStep.Calendar }
            }
        }
    }
}

@Composable
private fun PickerSurface(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)) {
        BooxNotesBar(title = title, onClose = onClose, onBack = onBack)
        header()
        Divider(color = EInkTheme.Colors.primary, thickness = EInkTheme.Global.borderWidth)
        val pickerScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pickerScroll)
            ) {
                content()
            }
            ScrollMoreBar(pickerScroll)
        }
    }
}

/** Thumbnail and title: which note this is about */
@Composable
private fun NoteThumbnailRow(note: BooxNoteIndex) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        NotePageThumbnail(note = note)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { "Untitled note" },
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = noteMetaLine(note, withDay = true),
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PickerSection(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        modifier = Modifier.padding(top = EInkTheme.Global.paddingMedium, bottom = EInkTheme.Global.spacingSmall)
    )
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.darkGray,
        modifier = Modifier.padding(vertical = EInkTheme.Global.paddingSmall)
    )
}

@Composable
private fun PickerEventRow(
    note: BooxNoteIndex,
    event: CalendarEvent,
    showDay: Boolean,
    linkedEventId: String?,
    suggestedEventId: String?,
    onClick: () -> Unit
) {
    val isLinked = event.id == linkedEventId
    val isSuggested = !isLinked && event.id == suggestedEventId
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EInkTheme.MenuItem.height)
                .then(
                    if (isSuggested || isLinked) {
                        Modifier.border(
                            EInkTheme.BooxNotes.cardStrongBorderWidth,
                            EInkTheme.Colors.primary,
                            RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                        )
                    } else {
                        Modifier
                    }
                )
                .noRippleClickable(onClick = onClick)
                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(EInkTheme.BooxNotes.pickerTimeColumnWidth)) {
                if (showDay) {
                    Text(
                        text = formatDayShort(event.dayId),
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary,
                        maxLines = 1
                    )
                }
                Text(
                    text = eventWhen(event, withDay = false),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = if (isLinked || isSuggested) FontWeight.Bold else FontWeight.Normal,
                    color = EInkTheme.Colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    when {
                        isLinked -> "Linked now"
                        isSuggested -> "Suggested"
                        else -> null
                    },
                    eventRelationToNote(note, event)
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Divider(color = EInkTheme.Colors.dividerHairline, thickness = EInkTheme.Global.borderWidth)
    }
}

@Composable
private fun PickerNoteRow(note: BooxNoteIndex, showDay: Boolean, linkedHere: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onClick)
                .padding(vertical = EInkTheme.Global.paddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            NotePageThumbnail(note = note)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { "Untitled note" },
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = if (linkedHere) FontWeight.Bold else FontWeight.Normal,
                    color = EInkTheme.Colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        if (linkedHere) "Linked now" else null,
                        noteMetaLine(note, withDay = showDay)
                    ).joinToString(" · "),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Divider(color = EInkTheme.Colors.dividerHairline, thickness = EInkTheme.Global.borderWidth)
    }
}

@Composable
private fun PickAnotherDayRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.MenuItem.height)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        Box(modifier = Modifier.width(EInkTheme.BooxNotes.pickerTimeColumnWidth - EInkTheme.Global.paddingSmall)) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
        Text(
            text = "Pick Another Day…",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )
    }
}
