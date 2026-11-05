package com.boox.day.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.model.IntentEntry
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.formatDomainLabel
import com.boox.day.ui.components.formatIntentLabel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.ScreenColour
import kotlinx.serialization.json.Json

// Notes place of the Boox Notes window (Nov 2026): a list of note cards, by day or all of them. A card
// answers three things at a glance: what it looks like, when it was written, and which event it belongs
// to. Lists turn over in pages: calmer than scrolling on e-ink, and thumbnails load a page at a time.

/** What a card shows besides the note itself */
internal data class NoteRowState(
    /** "Team sync · 1:30 PM"; null when the note isn't linked */
    val linkedLine: String?,
    val isLinked: Boolean,
    /** The suggested event's title; null when there is no suggestion */
    val suggestedLine: String?,
    /** On a colour screen, the linked event's calendar */
    val stripe: Color? = null
)

/** One row of a page */
internal sealed interface NoteListEntry {
    data class Month(val label: String) : NoteListEntry
    data class Note(val note: BooxNoteIndex) : NoteListEntry

    /** "2 empty notes" */
    data class EmptyNotes(val count: Int, val showing: Boolean) : NoteListEntry
}

/**
 * Splits [entries] into pages that fit [height]. A month heading never ends a page, and a page that
 * continues a month starts with its heading again, so you always see where you are.
 */
internal fun paginateNotes(
    entries: List<NoteListEntry>,
    height: Dp,
    noteHeight: Dp = EInkTheme.BooxNotes.noteCardHeight,
    monthHeight: Dp = EInkTheme.BooxNotes.monthRowHeight,
    otherHeight: Dp = EInkTheme.Global.touchTarget,
    gap: Dp = EInkTheme.BooxNotes.noteCardGap
): List<List<NoteListEntry>> {
    if (entries.isEmpty()) return listOf(emptyList())
    fun heightOf(entry: NoteListEntry): Dp = when (entry) {
        is NoteListEntry.Month -> monthHeight
        is NoteListEntry.Note -> noteHeight
        is NoteListEntry.EmptyNotes -> otherHeight
    } + gap

    val pages = mutableListOf<List<NoteListEntry>>()
    var page = mutableListOf<NoteListEntry>()
    var used = 0.dp
    var month: NoteListEntry.Month? = null

    fun startPage() {
        // Don't leave a heading alone at the bottom
        val lastHeading = page.lastOrNull() as? NoteListEntry.Month
        if (lastHeading != null) page.removeAt(page.lastIndex)
        if (page.isNotEmpty()) pages += page
        page = mutableListOf()
        used = 0.dp
        (lastHeading ?: month)?.let {
            page += it
            used += heightOf(it)
        }
    }

    for (entry in entries) {
        if (entry is NoteListEntry.Month) month = entry
        if (page.isNotEmpty() && used + heightOf(entry) > height) {
            if (entry is NoteListEntry.Month) {
                // The heading goes on the new page by itself, not twice
                val before = month
                month = null
                startPage()
                month = before
            } else {
                startPage()
            }
        }
        page += entry
        used += heightOf(entry)
    }
    if (page.isNotEmpty()) pages += page
    return pages
}

@Composable
internal fun BooxNoteCard(
    note: BooxNoteIndex,
    selected: Boolean,
    showDay: Boolean,
    state: NoteRowState,
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    onLink: (() -> Unit)?
) {
    val theme = EInkTheme.BooxNotes
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.noteCardHeight)
            .border(if (selected) theme.rowSelectedBorderWidth else theme.rowBorderWidth, EInkTheme.Colors.primary, shape)
            .calendarStripe(state.stripe)
            .noRippleClickable(onClick = onClick)
            .padding(start = cardStartPadding, end = EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NoteThumbnailSheet(thumbnail, "First page of ${note.title}")
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = note.title.ifBlank { "Untitled note" },
                fontSize = theme.rowTitleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = noteMetaLine(note, withDay = showDay && !titleIsItsDate(note)),
                fontSize = theme.rowMetaFontSize,
                color = theme.rowMetaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                state.isLinked -> CardStateLine(Icons.Default.Link, EInkTheme.Colors.primary, state.linkedLine ?: "Linked event", bold = false)
                state.suggestedLine != null -> CardStateLine(Icons.Default.Star, ScreenColour.starTint, "Suggested: ${state.suggestedLine}", bold = true)
                else -> Text(text = "Not linked", fontSize = theme.rowMetaFontSize, color = theme.rowMetaColor, maxLines = 1)
            }
        }
        if (onLink != null) {
            ActionButton(text = "Link", onClick = onLink, modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
private fun CardStateLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String, bold: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            fontSize = EInkTheme.BooxNotes.rowMetaFontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "2 empty notes": notes with no pages stay reachable, out of the way */
@Composable
private fun EmptyNotesRow(count: Int, showing: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget)
            .noRippleClickable { onChange(!showing) }
            .padding(horizontal = EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (showing) "Hide ${plural(count, "empty note")}" else plural(count, "empty note"),
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (showing) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = EInkTheme.Colors.darkGray
        )
    }
}

/**
 * The count of what's listed, and the way into Suggestions. One row that is always there: a Review
 * button that arrives late takes empty space and moves nothing.
 */
@Composable
internal fun NotesCaptionRow(caption: String, reviewCount: Int, shortLabel: Boolean, onReview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        Text(
            text = caption,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.Colors.darkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (reviewCount > 0) {
            val shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            Row(
                modifier = Modifier
                    .height(EInkTheme.Button.height)
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
                    .noRippleClickable(onClick = onReview)
                    .padding(horizontal = EInkTheme.Global.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = ScreenColour.starTint, modifier = Modifier.size(18.dp))
                Text(
                    text = if (shortLabel) "Review $reviewCount" else "Review ${plural(reviewCount, "Suggestion")}",
                    fontSize = EInkTheme.Button.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Note cards in pages, with arrows when there is more than one. Thumbnails of a page load together and
 * paint at once, then the next page's load quietly: one repaint, not one per card.
 */
@Composable
internal fun PagedNoteList(
    entries: List<NoteListEntry>,
    pageIndex: Int,
    onPageChange: (Int) -> Unit,
    selectedId: String?,
    showDay: Boolean,
    rowState: (BooxNoteIndex) -> NoteRowState,
    linkButtons: Boolean,
    onSelect: (BooxNoteIndex) -> Unit,
    onLink: (BooxNoteIndex) -> Unit,
    onShowEmptyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = EInkTheme.BooxNotes
    BoxWithConstraints(modifier = modifier) {
        val full = maxHeight
        val pagerHeight = EInkTheme.Global.touchTarget + theme.noteCardGap
        val pages = remember(entries, full) {
            val all = paginateNotes(entries, full)
            if (all.size == 1) all else paginateNotes(entries, full - pagerHeight)
        }
        val current = pageIndex.coerceIn(0, pages.lastIndex)

        // Follow a selection made elsewhere, such as a note opened from an event
        LaunchedEffect(selectedId, pages) {
            val onPage = pages.indexOfFirst { p -> p.any { it is NoteListEntry.Note && it.note.uniqueId == selectedId } }
            if (onPage >= 0 && onPage != current) onPageChange(onPage)
        }

        val widthPx = with(LocalDensity.current) { theme.cardThumbWidth.roundToPx() }
        val pageNotes = remember(pages, current) { pages[current].filterIsInstance<NoteListEntry.Note>().map { it.note } }
        val nextNotes = remember(pages, current) {
            pages.getOrNull(current + 1).orEmpty().filterIsInstance<NoteListEntry.Note>().map { it.note }
        }
        val thumbnails by produceState(BooxNoteThumbnails.cached(pageNotes, widthPx), pageNotes, widthPx) {
            val loaded = value.toMutableMap()
            pageNotes.filter { it.pageCount > 0 && it.uniqueId !in loaded }.forEach { note ->
                BooxNoteThumbnails.load(note, widthPx)?.let { loaded[note.uniqueId] = it }
            }
            value = loaded.toMap()
            nextNotes.filter { it.pageCount > 0 }.forEach { BooxNoteThumbnails.load(it, widthPx) }
        }

        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(theme.noteCardGap)) {
            pages[current].forEach { entry ->
                when (entry) {
                    is NoteListEntry.Month -> Text(
                        text = entry.label,
                        fontSize = theme.monthHeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.height(theme.monthRowHeight)
                    )
                    is NoteListEntry.Note -> {
                        val state = rowState(entry.note)
                        BooxNoteCard(
                            note = entry.note,
                            selected = entry.note.uniqueId == selectedId,
                            showDay = showDay,
                            state = state,
                            thumbnail = thumbnails[entry.note.uniqueId],
                            onClick = { onSelect(entry.note) },
                            onLink = if (linkButtons && state.suggestedLine != null) ({ onLink(entry.note) }) else null
                        )
                    }
                    is NoteListEntry.EmptyNotes -> EmptyNotesRow(entry.count, entry.showing, onShowEmptyChange)
                }
            }
            if (pages.size > 1) {
                Spacer(modifier = Modifier.weight(1f))
                NotesPager(pages, current, onPageChange)
            }
        }
    }
}

/** ‹ 1–4 of 142 ▾ ›: the middle opens a list of months, to jump */
@Composable
private fun NotesPager(pages: List<List<NoteListEntry>>, current: Int, onPageChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val noteCounts = remember(pages) { pages.map { p -> p.count { it is NoteListEntry.Note } } }
    val total = noteCounts.sum()
    val first = noteCounts.take(current).sum() + 1
    val last = first + noteCounts[current] - 1
    // Where each month starts
    val months = remember(pages) {
        buildList {
            val seen = mutableSetOf<String>()
            pages.forEachIndexed { index, page ->
                page.forEach { entry ->
                    if (entry is NoteListEntry.Month && seen.add(entry.label)) add(entry.label to index)
                }
            }
        }
    }
    val menuOffsetY = with(LocalDensity.current) { EInkTheme.Global.touchTarget.roundToPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous notes", current > 0) { onPageChange(current - 1) }
        Box {
            Row(
                modifier = Modifier
                    .height(EInkTheme.Global.touchTarget)
                    .then(if (months.size > 1) Modifier.noRippleClickable { open = true } else Modifier)
                    .padding(horizontal = EInkTheme.Global.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (first == last) "$first of $total" else "$first–$last of $total",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1
                )
                if (months.size > 1) Icon(Icons.Default.ArrowDropDown, contentDescription = "Jump to a month", tint = EInkTheme.Colors.primary)
            }
            if (open) {
                Popup(
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, -menuOffsetY),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Column(
                        modifier = Modifier
                            .width(EInkTheme.PopupMenu.width)
                            .heightIn(max = EInkTheme.PopupPanel.maxHeight)
                            .background(EInkTheme.PopupMenu.background)
                            .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                            .verticalScroll(rememberScrollState())
                    ) {
                        months.forEach { (label, page) ->
                            FilterMenuItem(text = label, selected = page == current) {
                                open = false
                                onPageChange(page)
                            }
                        }
                    }
                }
            }
        }
        PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "More notes", current < pages.lastIndex) { onPageChange(current + 1) }
    }
}

@Composable
private fun PagerArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
            modifier = Modifier.size(EInkTheme.Global.iconSize)
        )
    }
}

/** Day and All notes entries: month headings between groups (the list is newest first) */
internal fun allNotesEntries(notes: List<BooxNoteIndex>): List<NoteListEntry> = buildList {
    var lastMonth: String? = null
    notes.forEach { note ->
        val month = formatMonth(note.originalDate)
        if (month != lastMonth) {
            add(NoteListEntry.Month(month))
            lastMonth = month
        }
        add(NoteListEntry.Note(note))
    }
}

@Composable
internal fun NotesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.TextField.height)
            .background(EInkTheme.Colors.background, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
            .padding(horizontal = EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = EInkTheme.Colors.darkGray,
            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
        )
        Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary),
            cursorBrush = SolidColor(EInkTheme.Colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            fontSize = EInkTheme.Typography.bodyLarge,
                            color = EInkTheme.Colors.darkGray,
                            maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTargetCompact)
                    .noRippleClickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                )
            }
        }
    }
}

/** A button that stays on: black when active (Not Linked) */
@Composable
internal fun ToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Row(
        modifier = Modifier
            .height(EInkTheme.Global.touchTarget)
            .background(if (checked) EInkTheme.Colors.primary else EInkTheme.Colors.background, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
            .noRippleClickable { onCheckedChange(!checked) }
            .padding(horizontal = EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = EInkTheme.Colors.background,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (checked) EInkTheme.Colors.background else EInkTheme.Colors.primary,
            maxLines = 1
        )
    }
}

/** A filter button that opens a plain bordered list (no Material menu: no shadow) */
@Composable
private fun <T> FilterMenu(
    label: String,
    allLabel: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    // Open just below the button, never over it
    val menuOffsetY = with(LocalDensity.current) { EInkTheme.Global.touchTarget.roundToPx() }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
                .noRippleClickable { open = true }
                .padding(start = EInkTheme.Global.paddingMedium, end = EInkTheme.Global.spacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Medium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = EInkTheme.Colors.primary
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, menuOffsetY),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(EInkTheme.PopupMenu.width)
                        .heightIn(max = EInkTheme.PopupPanel.maxHeight)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                        .verticalScroll(rememberScrollState())
                ) {
                    FilterMenuItem(text = allLabel, selected = selected == null) {
                        onSelect(null)
                        open = false
                    }
                    options.forEach { (value, text) ->
                        FilterMenuItem(text = text, selected = value == selected) {
                            onSelect(value)
                            open = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilterMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .background(if (selected) EInkTheme.MenuItem.selectedBackground else EInkTheme.PopupMenu.background)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = if (selected) EInkTheme.MenuItem.selectedTextColor else EInkTheme.MenuItem.textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FILTER
// ═══════════════════════════════════════════════════════════════════════════

/** What All notes is narrowed to, besides search and Not Linked */
internal data class NoteFilters(
    val hasSuggestion: Boolean = false,
    val emptyNotes: Boolean = false,
    val domain: Domain? = null,
    val intent: Intent? = null
) {
    val activeCount: Int
        get() = listOf(hasSuggestion, emptyNotes, domain != null, intent != null).count { it }
}

/** Its own place: filters need room, and most days nobody opens it. Empty groups stay hidden. */
@Composable
internal fun BooxNotesFilterScreen(
    filters: NoteFilters,
    semantics: NoteSemantics?,
    matchCount: (NoteFilters) -> Int,
    onShow: (NoteFilters) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf(filters) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)) {
        Text(
            text = "Filter Notes",
            fontSize = EInkTheme.BooxNotes.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.padding(vertical = EInkTheme.Global.paddingSmall)
        )
        FilterHeading("Show")
        FilterCheck("Has a suggestion", draft.hasSuggestion) { draft = draft.copy(hasSuggestion = it) }
        FilterCheck("Empty notes", draft.emptyNotes) { draft = draft.copy(emptyNotes = it) }
        if (semantics != null && semantics.domainCounts.isNotEmpty()) {
            FilterHeading("Topic")
            FilterMenu(
                label = draft.domain?.let { formatDomainLabel(it) } ?: "All topics",
                allLabel = "All topics",
                options = semantics.domainCounts.entries
                    .sortedByDescending { it.value }
                    .map { (domain, count) -> domain to "${formatDomainLabel(domain)} ($count)" },
                selected = draft.domain,
                onSelect = { draft = draft.copy(domain = it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (semantics != null && semantics.intentCounts.isNotEmpty()) {
            FilterHeading("Kind of note")
            FilterMenu(
                label = draft.intent?.let { formatIntentLabel(it) } ?: "All kinds",
                allLabel = "All kinds",
                options = semantics.intentCounts.entries
                    .sortedByDescending { it.value }
                    .map { (intent, count) -> intent to "${formatIntentLabel(intent)} ($count)" },
                selected = draft.intent,
                onSelect = { draft = draft.copy(intent = it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing)
        ) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            EinkSecondaryButton(text = "Clear", onClick = { draft = NoteFilters() }, modifier = Modifier.weight(1f))
            ActionButton(
                text = "Show ${plural(matchCount(draft), "Note")}",
                onClick = { onShow(draft) },
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun FilterHeading(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        modifier = Modifier.padding(top = EInkTheme.Global.paddingSmall)
    )
}

@Composable
private fun FilterCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget)
            .noRippleClickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) EInkTheme.Colors.primary else EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, tint = EInkTheme.Colors.background, modifier = Modifier.size(20.dp))
            }
        }
        Text(text = label, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TOPIC / KIND DATA
// ═══════════════════════════════════════════════════════════════════════════

/** Topic and kinds per note, from read handwriting (empty without an OpenRouter key) */
internal class NoteSemantics(
    val domains: Map<String, Domain>,
    val intents: Map<String, List<Intent>>
) {
    val domainCounts: Map<Domain, Int> = domains.values.groupingBy { it }.eachCount()
    val intentCounts: Map<Intent, Int> = intents.values.flatten().groupingBy { it }.eachCount()
}

/**
 * Builds [NoteSemantics] from indexed Boox note pages: each page's topic votes with its confidence
 * (the note gets the strongest), and a note keeps its two most confident kinds (confidence ≥ 0.3).
 */
internal suspend fun loadNoteSemantics(ocrRepository: UnifiedOcrRepository): NoteSemantics {
    val json = Json { ignoreUnknownKeys = true }
    val domainWeights = mutableMapOf<String, MutableMap<Domain, Float>>()
    val intentConfidences = mutableMapOf<String, MutableMap<Intent, Float>>()
    for (page in ocrRepository.getOcrTextsBySourceType(OcrSource.BOOX_NOTE)) {
        val noteId = page.sourceId
        page.domain
            ?.let { runCatching { Domain.valueOf(it) }.getOrNull() }
            ?.let { domain ->
                domainWeights.getOrPut(noteId) { mutableMapOf() }
                    .merge(domain, page.domainConfidence.coerceIn(0.1f, 1f)) { a, b -> a + b }
            }
        page.intentsJson?.let { intentsJson ->
            runCatching { json.decodeFromString<List<IntentEntry>>(intentsJson) }.getOrNull()
                ?.filter { it.confidence >= 0.3f }
                ?.forEach { entry ->
                    val perNote = intentConfidences.getOrPut(noteId) { mutableMapOf() }
                    if (entry.confidence > (perNote[entry.intent] ?: 0f)) perNote[entry.intent] = entry.confidence
                }
        }
    }
    return NoteSemantics(
        domains = domainWeights.mapNotNull { (noteId, weights) ->
            weights.maxByOrNull { it.value }?.key?.let { noteId to it }
        }.toMap(),
        intents = intentConfidences.mapValues { (_, confidences) ->
            confidences.entries.sortedByDescending { it.value }.take(2).map { it.key }
        }
    )
}
