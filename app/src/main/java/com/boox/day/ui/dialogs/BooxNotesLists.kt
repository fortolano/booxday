package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.serialization.json.Json

// Note lists of the Boox Notes dialog (Oct 2026): the Day list and the All notes list. A row shows the
// title, one line of when / how long, and a state line only when there is something to act on (the
// linked event, or a Smart Link suggestion). Filters live in All notes only.

/** What a row shows besides title and time */
internal data class NoteRowState(
    /** Title of the linked event; null when the note is not linked (or the event is gone) */
    val linkedEventTitle: String?,
    val isLinked: Boolean,
    val hasSuggestion: Boolean
)

@Composable
internal fun BooxNoteRow(
    note: BooxNoteIndex,
    selected: Boolean,
    showDay: Boolean,
    state: NoteRowState,
    onClick: () -> Unit
) {
    val theme = EInkTheme.BooxNotes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) theme.rowSelectedBorderWidth else theme.rowBorderWidth,
                color = if (selected) theme.rowSelectedBorderColor else theme.rowBorderColor,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(theme.rowPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = note.title.ifBlank { "Untitled note" },
            fontSize = theme.rowTitleFontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = noteMetaLine(note, withDay = showDay),
            fontSize = theme.rowMetaFontSize,
            color = theme.rowMetaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when {
            state.isLinked -> RowStateLine(
                icon = Icons.Default.Link,
                text = state.linkedEventTitle ?: "Linked event",
                bold = false
            )
            state.hasSuggestion -> RowStateLine(icon = Icons.Default.Star, text = "Suggestion", bold = true)
        }
    }
}

@Composable
private fun RowStateLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bold: Boolean) {
    val theme = EInkTheme.BooxNotes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(theme.rowStateIconSize)
        )
        Text(
            text = text,
            fontSize = theme.rowStateFontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListCaption(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = EInkTheme.Colors.darkGray,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// DAY LIST
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun BooxNotesDayList(
    notes: List<BooxNoteIndex>,
    selectedId: String?,
    rowState: (BooxNoteIndex) -> NoteRowState,
    caption: String,
    notice: String?,
    suggestionCount: Int,
    onSelect: (BooxNoteIndex) -> Unit,
    onReviewSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = EInkTheme.BooxNotes
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(theme.rowSpacing)) {
        ListCaption(caption)
        if (notice != null) {
            Text(
                text = notice,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(theme.rowSpacing)
        ) {
            items(notes, key = { it.uniqueId }) { note ->
                BooxNoteRow(
                    note = note,
                    selected = note.uniqueId == selectedId,
                    showDay = false,
                    state = rowState(note),
                    onClick = { onSelect(note) }
                )
            }
        }
        if (suggestionCount > 0) {
            EinkSecondaryButton(
                text = "Review ${plural(suggestionCount, "suggestion")}",
                onClick = onReviewSuggestions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ALL NOTES LIST
// ═══════════════════════════════════════════════════════════════════════════

/** Smart Link status for all notes, shown under the All notes list */
internal sealed interface AllSuggestionsStatus {
    data object Idle : AllSuggestionsStatus
    data class Finding(val progress: Float, val step: String) : AllSuggestionsStatus
    data class Ready(val count: Int) : AllSuggestionsStatus
}

private sealed interface AllNotesEntry {
    data class Month(val label: String) : AllNotesEntry
    data class Note(val note: BooxNoteIndex) : AllNotesEntry
}

@Composable
internal fun BooxNotesAllList(
    notes: List<BooxNoteIndex>,
    caption: String,
    selectedId: String?,
    rowState: (BooxNoteIndex) -> NoteRowState,
    query: String,
    onQueryChange: (String) -> Unit,
    searchesHandwriting: Boolean,
    unlinkedOnly: Boolean,
    onUnlinkedOnlyChange: (Boolean) -> Unit,
    semantics: NoteSemantics?,
    selectedDomain: Domain?,
    onDomainChange: (Domain?) -> Unit,
    selectedIntent: Intent?,
    onIntentChange: (Intent?) -> Unit,
    suggestionsStatus: AllSuggestionsStatus,
    onSelect: (BooxNoteIndex) -> Unit,
    onReviewSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = EInkTheme.BooxNotes
    // Month headers between groups (the list is newest first)
    val entries = remember(notes) {
        buildList {
            var lastMonth: String? = null
            notes.forEach { note ->
                val month = formatMonth(note.originalDate)
                if (month != lastMonth) {
                    add(AllNotesEntry.Month(month))
                    lastMonth = month
                }
                add(AllNotesEntry.Note(note))
            }
        }
    }
    val listState = rememberLazyListState()
    // Keep the selected note in view (it can come from another view or a search)
    LaunchedEffect(selectedId, entries) {
        val index = entries.indexOfFirst { it is AllNotesEntry.Note && it.note.uniqueId == selectedId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(theme.rowSpacing)) {
        NotesSearchField(
            query = query,
            onQueryChange = onQueryChange,
            hint = if (searchesHandwriting) "Search notes" else "Search titles"
        )
        ToggleChip(label = "Unlinked only", checked = unlinkedOnly, onCheckedChange = onUnlinkedOnlyChange)
        // Category and Purpose need read handwriting; hidden until any note has one
        val showDomains = semantics != null && semantics.domainCounts.isNotEmpty()
        val showIntents = semantics != null && semantics.intentCounts.isNotEmpty()
        if (showDomains || showIntents) Row(
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (semantics != null && semantics.domainCounts.isNotEmpty()) {
                FilterMenu(
                    label = selectedDomain?.let { formatDomainLabel(it) } ?: "Category",
                    allLabel = "All categories",
                    options = semantics.domainCounts.entries
                        .sortedByDescending { it.value }
                        .map { (domain, count) -> domain to "${formatDomainLabel(domain)} ($count)" },
                    selected = selectedDomain,
                    onSelect = onDomainChange,
                    modifier = Modifier.weight(1f)
                )
            }
            if (semantics != null && semantics.intentCounts.isNotEmpty()) {
                FilterMenu(
                    label = selectedIntent?.let { formatIntentLabel(it) } ?: "Purpose",
                    allLabel = "All purposes",
                    options = semantics.intentCounts.entries
                        .sortedByDescending { it.value }
                        .map { (intent, count) -> intent to "${formatIntentLabel(intent)} ($count)" },
                    selected = selectedIntent,
                    onSelect = onIntentChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ListCaption(caption)

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No notes match",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.darkGray,
                    modifier = Modifier.padding(top = EInkTheme.Global.paddingLarge)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(theme.rowSpacing)
            ) {
                items(
                    items = entries,
                    key = { entry ->
                        when (entry) {
                            is AllNotesEntry.Month -> "month_${entry.label}"
                            is AllNotesEntry.Note -> entry.note.uniqueId
                        }
                    }
                ) { entry ->
                    when (entry) {
                        is AllNotesEntry.Month -> Text(
                            text = entry.label,
                            fontSize = theme.monthHeaderFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary,
                            modifier = Modifier.padding(top = EInkTheme.Global.paddingSmall)
                        )
                        is AllNotesEntry.Note -> BooxNoteRow(
                            note = entry.note,
                            selected = entry.note.uniqueId == selectedId,
                            showDay = true,
                            state = rowState(entry.note),
                            onClick = { onSelect(entry.note) }
                        )
                    }
                }
            }
        }

        when (suggestionsStatus) {
            is AllSuggestionsStatus.Finding -> Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                Text(
                    text = "Finding link suggestions… ${(suggestionsStatus.progress * 100).toInt()}%",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                BooxNotesProgressBar(progress = suggestionsStatus.progress, modifier = Modifier.fillMaxWidth())
                Text(text = suggestionsStatus.step, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
            }
            is AllSuggestionsStatus.Ready -> if (suggestionsStatus.count > 0) {
                EinkSecondaryButton(
                    text = "Review ${plural(suggestionsStatus.count, "suggestion")}",
                    onClick = onReviewSuggestions,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "No link suggestions right now",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
            }
            AllSuggestionsStatus.Idle -> Unit
        }
    }
}

@Composable
private fun NotesSearchField(
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
                            color = EInkTheme.Colors.gray,
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

@Composable
private fun ToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            fontWeight = FontWeight.Medium,
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
private fun FilterMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
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
// CATEGORY / PURPOSE DATA
// ═══════════════════════════════════════════════════════════════════════════

/** Category and purposes per note, from read handwriting (empty without an OpenRouter key) */
internal class NoteSemantics(
    val domains: Map<String, Domain>,
    val intents: Map<String, List<Intent>>
) {
    val domainCounts: Map<Domain, Int> = domains.values.groupingBy { it }.eachCount()
    val intentCounts: Map<Intent, Int> = intents.values.flatten().groupingBy { it }.eachCount()
}

/**
 * Builds [NoteSemantics] from indexed Boox note pages: each page's category votes with its confidence
 * (the note gets the strongest), and a note keeps its two most confident purposes (confidence ≥ 0.3).
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
