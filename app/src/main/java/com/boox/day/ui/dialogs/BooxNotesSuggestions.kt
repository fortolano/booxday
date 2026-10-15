package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.LinkConfidence
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.SmartLinkPreflightState
import com.boox.day.data.db.SmartLinkPreflightStatus
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

// Smart Link suggestions of the Boox Notes dialog (Oct 2026), shown in place of the lists (no second
// popup): this day's notes or all notes, one card per note with its page, the suggested event and why.

internal data class SuggestionItem(
    val note: BooxNoteIndex,
    val suggestion: CrossDayLinkSuggestion,
    val reason: String
)

internal sealed interface SuggestionsContent {
    /** Checking that there are calendar events to match against */
    data object Checking : SuggestionsContent

    /** Smart Link can't run: no usable events, or turned off */
    data class Blocked(val status: SmartLinkPreflightStatus) : SuggestionsContent

    data class Finding(val progress: Float) : SuggestionsContent

    data object Ready : SuggestionsContent
}

@Composable
internal fun BooxNotesSuggestionsView(
    scopeAll: Boolean,
    dayLabel: String,
    onScopeChange: (Boolean) -> Unit,
    content: SuggestionsContent,
    items: List<SuggestionItem>,
    unlinkedCount: Int,
    indexingNote: String?,
    onBack: () -> Unit,
    onLinkStrong: (List<SuggestionItem>) -> Unit,
    onLink: (SuggestionItem) -> Unit,
    onSkip: (SuggestionItem) -> Unit,
    onOpenNote: (BooxNoteIndex) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strongItems = items.filter { it.suggestion.confidence == LinkConfidence.HIGH }
    var confirmBulk by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)) {
        // Header: back, title, scope, bulk link
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.BooxNotes.headerSpacing)
        ) {
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTarget)
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to notes",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                )
            }
            Text(
                text = "Smart Link suggestions",
                fontSize = EInkTheme.BooxNotes.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            BooxNotesSegmented(
                options = listOf(dayLabel, "All notes"),
                selectedIndex = if (scopeAll) 1 else 0,
                onSelect = { onScopeChange(it == 1) }
            )
            if (content == SuggestionsContent.Ready && strongItems.isNotEmpty()) {
                ActionButton(text = "Link ${strongItems.size} strong…", onClick = { confirmBulk = true })
            }
        }

        when (content) {
            SuggestionsContent.Checking -> CenterMessage("Checking your calendar…")

            is SuggestionsContent.Blocked -> SmartLinkBlocked(status = content.status, onClose = onBack)

            is SuggestionsContent.Finding -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = EInkTheme.Global.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Finding link suggestions… ${(content.progress * 100).toInt()}%",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                BooxNotesProgressBar(progress = content.progress, modifier = Modifier.fillMaxWidth(0.7f))
                EinkSecondaryButton(text = "Stop", onClick = onStop)
            }

            SuggestionsContent.Ready -> {
                val possible = items.size - strongItems.size
                val without = (unlinkedCount - items.size).coerceAtLeast(0)
                Text(
                    text = listOfNotNull(
                        plural(strongItems.size, "strong suggestion"),
                        if (possible > 0) "$possible possible" else null,
                        if (without > 0) "${plural(without, "note")} without a confident suggestion" else null
                    ).joinToString(" · "),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                if (indexingNote != null) {
                    Text(text = indexingNote, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)
                }
                if (items.isEmpty()) {
                    CenterMessage(
                        "No suggestions right now. Smart Link looks for events within " +
                            "${BooxNoteIndexer.BULK_MODE_SEARCH_DAYS} days of each note; you can still link a note " +
                            "from its note pane."
                    )
                } else {
                    SuggestionList(
                        items = items,
                        onLink = onLink,
                        onSkip = onSkip,
                        onOpenNote = onOpenNote,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (confirmBulk) {
        BulkLinkConfirm(
            count = strongItems.size,
            onCancel = { confirmBulk = false },
            onConfirm = {
                confirmBulk = false
                onLinkStrong(strongItems)
            }
        )
    }
}

@Composable
private fun CenterMessage(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        color = EInkTheme.Colors.darkGray,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(EInkTheme.Global.paddingLarge)
    )
}

@Composable
private fun SuggestionList(
    items: List<SuggestionItem>,
    onLink: (SuggestionItem) -> Unit,
    onSkip: (SuggestionItem) -> Unit,
    onOpenNote: (BooxNoteIndex) -> Unit,
    modifier: Modifier = Modifier
) {
    // Grouped by the day each note was written, newest first
    val groups = remember(items) {
        items.groupBy { it.note.originalDate }.toSortedMap(compareByDescending { it })
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EInkTheme.BooxNotes.cardSpacing)
    ) {
        groups.forEach { (date, dayItems) ->
            item(key = "day_$date") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EInkTheme.Global.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                ) {
                    Text(
                        text = formatDayFull(date),
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Divider(
                        color = EInkTheme.Colors.primary,
                        thickness = EInkTheme.Global.borderWidth,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            items(dayItems, key = { "${it.note.uniqueId}_${it.suggestion.event.id}" }) { item ->
                SuggestionCard(
                    item = item,
                    onLink = { onLink(item) },
                    onSkip = { onSkip(item) },
                    onOpenNote = { onOpenNote(item.note) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    item: SuggestionItem,
    onLink: () -> Unit,
    onSkip: () -> Unit,
    onOpenNote: () -> Unit
) {
    val theme = EInkTheme.BooxNotes
    val strong = item.suggestion.confidence == LinkConfidence.HIGH
    val event = item.suggestion.event
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (strong) theme.cardStrongBorderWidth else theme.cardBorderWidth,
                color = EInkTheme.Colors.primary,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(theme.cardPadding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // The page itself: names like "Notebook-27" or a date say little; tap to read it
            NotePageThumbnail(note = item.note, onClick = onOpenNote)
            CardColumn(
                label = "Note",
                title = item.note.title.ifBlank { "Untitled note" },
                detail = noteMetaLine(item.note, withDay = true),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "links to",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier
                    .padding(top = EInkTheme.Global.paddingLarge)
                    .size(EInkTheme.Global.iconSizeSmall)
            )
            CardColumn(
                label = "Event",
                title = event.title,
                detail = eventWhen(event, withDay = true),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            ConfidenceTag(item.suggestion.confidence)
            Text(
                text = item.reason,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing)
        ) {
            EinkSecondaryButton(text = "Skip", onClick = onSkip, modifier = Modifier.weight(1f))
            ActionButton(text = "Link", onClick = onLink, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardColumn(label: String, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = EInkTheme.BooxNotes.labelFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.darkGray
        )
        Text(
            text = title,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = detail,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Link 6 notes?" before any bulk change; the dialog then offers Undo */
@Composable
private fun BulkLinkConfirm(count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(EInkTheme.PopupPanel.width)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.ModernDialog.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
        ) {
            Text(
                text = "Link ${plural(count, "note")}?",
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "Each note is linked to its strong suggestion. You can undo this right after, " +
                    "or unlink any note later.",
                fontSize = EInkTheme.Typography.bodyLarge,
                color = EInkTheme.Colors.primary
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(0.4f))
                Spacer(modifier = Modifier.weight(0.2f))
                ActionButton(text = "Link ${plural(count, "note")}", onClick = onConfirm, modifier = Modifier.weight(0.4f))
            }
        }
    }
}

/** Why Smart Link can't run, and where to fix it (from the calendar check) */
@Composable
private fun SmartLinkBlocked(status: SmartLinkPreflightStatus, onClose: () -> Unit) {
    val (title, body, hint) = when (status.state) {
        SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED -> Triple(
            "Smart Link is turned off",
            "Smart Link is turned off right now, so it can't suggest links. Your existing links are safe.",
            "You can still link notes by hand from the note pane."
        )
        SmartLinkPreflightState.SERVER_OCR_DISABLED -> Triple(
            "Note reading is turned off",
            "Reading Boox notes is turned off right now, so Smart Link can't compare them with events. Your existing links are safe.",
            "You can still link notes by hand from the note pane."
        )
        SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS -> Triple(
            "Your calendar filters hide every event",
            "The Local, Google and Outlook filters in the events panel hide all the events Smart Link could use.",
            "Events panel › turn on a calendar that has events"
        )
        SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS -> Triple(
            "Only all-day events",
            "Every event in your calendar is all-day. Smart Link matches notes to events with a start time.",
            "Add or sync events that have a start time"
        )
        SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS -> Triple(
            "No synced events yet",
            "Your calendar account is connected, but no events have synced yet.",
            "Settings › Calendar Accounts › sync, and turn on at least one calendar"
        )
        else -> Triple(
            "No calendar events",
            "Smart Link matches your Boox Notes to calendar events. Connect a Google or Outlook account, or add events in BooxDay.",
            "Settings › Calendar Accounts › Add Account"
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(EInkTheme.Global.paddingLarge),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = EInkTheme.Typography.titleLarge, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary, textAlign = TextAlign.Center)
        Text(text = body, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary, textAlign = TextAlign.Center)
        Text(text = hint, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.darkGray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
        EinkSecondaryButton(text = "Close", onClick = onClose)
    }
}
