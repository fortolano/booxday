package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import com.boox.day.editor.ui.TaskDatePickerContent
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.theme.EInkTheme
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Event picker of the Boox Notes dialog (Oct 2026): pick the event a note belongs to. Same-day events
// come first (timed ones nearest to when the note was written), then the days around it, then any day.

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
    onDismiss: () -> Unit
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

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .heightIn(max = EInkTheme.BooxNotes.pickerMaxHeight)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.Global.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            Text(
                text = "Link to an event",
                fontSize = EInkTheme.Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "${note.title.ifBlank { "Untitled note" }} · ${noteMetaLine(note, withDay = true)}",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Divider(color = EInkTheme.Colors.primary, thickness = EInkTheme.Global.borderWidth)

            val pickerScroll = rememberScrollState()
            Box(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(pickerScroll)
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
                ScrollMoreBar(pickerScroll)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing)
            ) {
                EinkSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                if (currentStep != PickerStep.Nearby) {
                    EinkSecondaryButton(
                        text = "Back",
                        onClick = { step = PickerStep.Nearby },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
            text = "Pick another day…",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )
    }
}
