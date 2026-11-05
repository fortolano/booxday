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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
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
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.theme.EInkTheme

// Suggestions place of the Boox Notes window (Nov 2026). One at a Time: the page large, the event, and
// one decision, because the page is what the decision rests on. List: every card, in any order.

internal data class SuggestionItem(
    val note: BooxNoteIndex,
    val suggestion: CrossDayLinkSuggestion,
    val reason: String
)

internal sealed interface SuggestionsContent {
    /** Checking that there are calendar events to match against */
    data object Checking : SuggestionsContent

    /** Suggestions can't run: no usable events */
    data class Blocked(val status: SmartLinkPreflightStatus) : SuggestionsContent

    data class Finding(val progress: Float, val step: String) : SuggestionsContent

    data object Ready : SuggestionsContent
}

/** What the place needs from the window, and what it does */
internal class SuggestionsHost(
    val content: SuggestionsContent,
    val items: List<SuggestionItem>,
    val unlinkedCount: Int,
    val scopeAll: Boolean,
    val dayLabel: String,
    val onScopeChange: (Boolean) -> Unit,
    val indexingNote: String?,
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onLinkLikely: (List<SuggestionItem>) -> Unit,
    val onLink: (SuggestionItem) -> Unit,
    val onReject: (SuggestionItem) -> Unit,
    val onOpenNote: (BooxNoteIndex) -> Unit,
    val onStop: () -> Unit,
    val onOpenCalendarAccounts: () -> Unit,
    val message: BooxNotesMessage?,
    val onUndo: () -> Unit
)

/** One at a Time: the note on show comes from the window, so the page loads through its usual path */
@Composable
internal fun BooxNotesSuggestionsView(
    host: SuggestionsHost,
    current: SuggestionItem?,
    position: Int,
    total: Int,
    page: NotePageState,
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onOpenReader: () -> Unit,
    onDecideLater: () -> Unit,
    listView: Boolean,
    onListViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val likely = host.items.filter { it.suggestion.confidence == LinkConfidence.HIGH }
    var confirmBulk by remember { mutableStateOf(false) }
    val ready = host.content == SuggestionsContent.Ready && host.items.isNotEmpty()
    val title = if (ready && !listView) "Suggestions · $position of $total" else "Suggestions"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)) {
        BooxNotesBar(title = title, onClose = host.onClose, onBack = host.onBack) {
            if (ready) {
                EinkSecondaryButton(
                    text = if (listView) "One at a Time" else "List",
                    onClick = { onListViewChange(!listView) },
                    width = if (listView) 136.dp else 100.dp
                )
            }
        }

        when (val content = host.content) {
            SuggestionsContent.Checking -> CenterMessage("Checking your calendar…")

            is SuggestionsContent.Blocked -> SuggestionsBlocked(
                status = content.status,
                onBack = host.onBack,
                onOpenCalendarAccounts = host.onOpenCalendarAccounts
            )

            is SuggestionsContent.Finding -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = EInkTheme.Global.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Finding suggestions… ${(content.progress * 100).toInt()}%",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                BooxNotesProgressBar(progress = content.progress, modifier = Modifier.fillMaxWidth(0.7f))
                Text(text = content.step, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)
                EinkSecondaryButton(text = "Stop", onClick = host.onStop)
            }

            SuggestionsContent.Ready -> when {
                host.items.isEmpty() -> {
                    ScopeRow(host)
                    CenterMessage(
                        "No suggestions right now. BooxDay looks for events within " +
                            "${BooxNoteIndexer.BULK_MODE_SEARCH_DAYS} days of each note. You can still link a note " +
                            "yourself: open it and tap Choose Event."
                    )
                    host.message?.let { BooxNotesMessageBar(it, host.onUndo) }
                }

                listView -> {
                    ScopeRow(host)
                    val maybe = host.items.size - likely.size
                    val without = (host.unlinkedCount - host.items.size).coerceAtLeast(0)
                    Text(
                        text = listOfNotNull(
                            if (likely.isNotEmpty()) "${likely.size} likely" else null,
                            if (maybe > 0) "$maybe maybe" else null,
                            if (without > 0) "${plural(without, "note")} without a suggestion" else null
                        ).joinToString(" · "),
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                    host.indexingNote?.let {
                        Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)
                    }
                    SuggestionList(
                        host = host,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    BulkOrMessageRow(host, likely.size, keepRoom = false) { confirmBulk = true }
                }

                current != null -> {
                    NotePageView(
                        page = page,
                        onOpenReader = onOpenReader,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    PageRow(pageIndex, pageCount, onPageChange, onOpenInBooxNotes = null)
                    ReviewZone(
                        item = current,
                        onDecideLater = onDecideLater,
                        onReject = { host.onReject(current) },
                        onLink = { host.onLink(current) }
                    )
                    host.indexingNote?.let {
                        Text(text = it, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray, maxLines = 1)
                    }
                    BulkOrMessageRow(host, likely.size, keepRoom = true) { confirmBulk = true }
                }
            }
        }
    }

    if (confirmBulk) {
        BulkLinkConfirm(
            count = likely.size,
            onCancel = { confirmBulk = false },
            onConfirm = {
                confirmBulk = false
                host.onLinkLikely(likely)
            }
        )
    }
}

/** Link All takes this row; right after a change, the message and Undo take it for a moment */
@Composable
private fun BulkOrMessageRow(host: SuggestionsHost, likelyCount: Int, keepRoom: Boolean, onLinkAll: () -> Unit) {
    val message = host.message
    when {
        message != null -> BooxNotesMessageBar(message, host.onUndo)
        likelyCount > 0 -> EinkSecondaryButton(
            text = if (likelyCount == 1) "Link 1 Likely…" else "Link All $likelyCount Likely…",
            onClick = onLinkAll,
            modifier = Modifier.fillMaxWidth()
        )
        keepRoom -> Spacer(modifier = Modifier.height(EInkTheme.Button.height))
    }
}

/** This day or All notes, for the List and for Link All */
@Composable
private fun ScopeRow(host: SuggestionsHost) {
    BooxNotesSegmented(
        options = listOf(host.dayLabel, "All notes"),
        selectedIndex = if (host.scopeAll) 1 else 0,
        onSelect = { host.onScopeChange(it == 1) }
    )
}

@Composable
private fun ReviewZone(
    item: SuggestionItem,
    onDecideLater: () -> Unit,
    onReject: () -> Unit,
    onLink: () -> Unit
) {
    val theme = EInkTheme.BooxNotes
    val likely = item.suggestion.confidence == LinkConfidence.HIGH
    val event = item.suggestion.event
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.reviewZoneHeight)
            .border(
                width = if (likely) theme.cardStrongBorderWidth else theme.cardBorderWidth,
                color = EInkTheme.Colors.primary,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(theme.linkRowPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.note.title.ifBlank { "Untitled note" },
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ConfidenceTag(item.suggestion.confidence)
        }
        Text(
            text = event.title,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOf(eventWhen(event, withDay = true), item.reason).joinToString(" · "),
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing)
        ) {
            EinkSecondaryButton(text = "Decide Later", onClick = onDecideLater, modifier = Modifier.weight(1.3f))
            EinkSecondaryButton(text = "Reject", onClick = onReject, modifier = Modifier.weight(1f))
            ActionButton(text = "Link", onClick = onLink, modifier = Modifier.weight(1f))
        }
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
private fun SuggestionList(host: SuggestionsHost, modifier: Modifier = Modifier) {
    // Grouped by the day each note was written, newest first
    val groups = remember(host.items) {
        host.items.groupBy { it.note.originalDate }.toSortedMap(compareByDescending { it })
    }
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                        onLink = { host.onLink(item) },
                        onReject = { host.onReject(item) },
                        onOpenNote = { host.onOpenNote(item.note) }
                    )
                }
            }
        }
        ScrollMoreBar(listState)
    }
}

@Composable
private fun SuggestionCard(
    item: SuggestionItem,
    onLink: () -> Unit,
    onReject: () -> Unit,
    onOpenNote: () -> Unit
) {
    val theme = EInkTheme.BooxNotes
    val likely = item.suggestion.confidence == LinkConfidence.HIGH
    val event = item.suggestion.event
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (likely) theme.cardStrongBorderWidth else theme.cardBorderWidth,
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.note.title.ifBlank { "Untitled note" },
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = eventWhen(event, withDay = true),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            EinkSecondaryButton(text = "Reject", onClick = onReject, modifier = Modifier.weight(1f))
            ActionButton(text = "Link", onClick = onLink, modifier = Modifier.weight(1f))
        }
    }
}

/** "Link 6 notes?" before any bulk change; the window then offers Undo */
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
                text = "Each note is linked to its likely event. You can undo this right after, " +
                    "or unlink any note later.",
                fontSize = EInkTheme.Typography.bodyLarge,
                color = EInkTheme.Colors.primary
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(0.4f))
                Spacer(modifier = Modifier.weight(0.2f))
                ActionButton(text = "Link ${plural(count, "Note")}", onClick = onConfirm, modifier = Modifier.weight(0.4f))
            }
        }
    }
}

/** Why suggestions can't run, and where to fix it (from the calendar check) */
@Composable
private fun SuggestionsBlocked(status: SmartLinkPreflightStatus, onBack: () -> Unit, onOpenCalendarAccounts: () -> Unit) {
    val (title, body, hint) = when (status.state) {
        SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS -> Triple(
            "Your calendar filters hide every event",
            "The calendar filters in the events panel hide all the events a note could be matched to.",
            "Events panel › turn on a calendar that has events"
        )
        SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS -> Triple(
            "Only all-day events",
            "Every event in your calendar is all-day. Notes are matched to events with a start time.",
            "Add or sync events that have a start time"
        )
        SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS -> Triple(
            "No synced events yet",
            "Your calendar account is connected, but no events have synced yet.",
            "Open Calendar Accounts, sync, and turn on at least one calendar"
        )
        else -> Triple(
            "No calendar events",
            "Suggestions match your Boox Notes to calendar events. Connect a calendar account (Google, Outlook, iCloud and more), or add events in BooxDay.",
            "Open Calendar Accounts and tap Add Account"
        )
    }
    // Missing or unsynced events are fixed in Calendar Accounts, so take the user straight there
    val accountsFix = status.state == SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS ||
        status.state == SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS
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
        if (accountsFix) {
            Row(modifier = Modifier.fillMaxWidth()) {
                EinkSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(0.4f))
                Spacer(modifier = Modifier.weight(0.1f))
                ActionButton(text = "Open Calendar Accounts", onClick = onOpenCalendarAccounts, modifier = Modifier.weight(0.5f))
            }
        } else {
            EinkSecondaryButton(text = "Back", onClick = onBack)
        }
    }
}
