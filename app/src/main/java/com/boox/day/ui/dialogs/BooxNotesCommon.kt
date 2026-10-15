package com.boox.day.ui.dialogs

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.booxnotes.BooxNotePreviewCoordinator
import com.boox.day.booxnotes.BooxNotePreviewResult
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.LinkConfidence
import com.boox.day.booxnotes.MatchReason
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Shared pieces of the Boox Notes dialog (Oct 2026): wording for dates, times and suggestions, page
// thumbnails, the suggestion cache, and small controls used by its lists, note pane and suggestions.

// ═══════════════════════════════════════════════════════════════════════════
// WORDING
// ═══════════════════════════════════════════════════════════════════════════

private val dayFullFormat = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)
private val dayShortFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val dayShortYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal fun parseNoteDay(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

/** "Monday, Oct 12, 2026" */
internal fun formatDayFull(date: String): String = parseNoteDay(date)?.format(dayFullFormat) ?: date

/** "Mon, Oct 12" this year, "Mon, Oct 12, 2025" otherwise */
internal fun formatDayShort(date: String): String {
    val day = parseNoteDay(date) ?: return date
    return if (day.year == LocalDate.now().year) day.format(dayShortFormat) else day.format(dayShortYearFormat)
}

/** "October 2026" (month headers) */
internal fun formatMonth(date: String): String = parseNoteDay(date)?.format(monthFormat) ?: date

/** "1:39 PM" */
internal fun formatClock(timeMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormat)
}.getOrDefault("")

internal fun pagesLabel(count: Int): String = when (count) {
    0 -> "No pages"
    1 -> "1 page"
    else -> "$count pages"
}

internal fun plural(count: Int, one: String, many: String = one + "s"): String =
    if (count == 1) "1 $one" else "$count $many"

/** "Mon, Oct 12 · 1:39 PM · 1 page" ([withDay]) or "1:39 PM · 1 page" */
internal fun noteMetaLine(note: BooxNoteIndex, withDay: Boolean, fullDay: Boolean = false): String = buildList {
    if (withDay) add(if (fullDay) formatDayFull(note.originalDate) else formatDayShort(note.originalDate))
    formatClock(note.originalTimestamp).takeIf { it.isNotEmpty() }?.let(::add)
    add(pagesLabel(note.pageCount))
}.joinToString(" · ")

/** "Wed, Oct 14 · 8:45 AM" ([withDay]) or "8:45 AM" / "All day" */
internal fun eventWhen(event: CalendarEvent, withDay: Boolean): String {
    val time = if (event.isAllDay) "All day" else formatClock(event.startTime)
    return if (withDay) "${formatDayShort(event.dayId)} · $time" else time
}

private fun durationText(minutes: Long): String {
    if (minutes < 60) return "${minutes.coerceAtLeast(1)} min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L || hours >= 3) "$hours h" else "$hours h $rest min"
}

/**
 * Where an event sits relative to a note, from the note's point of view:
 * "2 days after this note", "ended 3 h before the note", "the note was written during it".
 */
internal fun eventRelationToNote(note: BooxNoteIndex, event: CalendarEvent): String? {
    val noteDay = parseNoteDay(note.originalDate)
    val eventDay = parseNoteDay(event.dayId)
    if (noteDay != null && eventDay != null && noteDay != eventDay) {
        val days = abs(ChronoUnit.DAYS.between(noteDay, eventDay))
        val span = if (days == 1L) "1 day" else "$days days"
        return if (eventDay.isAfter(noteDay)) "$span after this note" else "$span before this note"
    }
    if (event.isAllDay) return null
    val noteTime = note.originalTimestamp
    val end = event.endTime ?: event.startTime
    return when {
        noteTime in event.startTime..end -> "the note was written during it"
        end < noteTime -> "ended ${durationText((noteTime - end) / 60_000)} before the note"
        else -> "starts ${durationText((event.startTime - noteTime) / 60_000)} after the note"
    }
}

/** "Strong" or "Possible" (the only levels suggestions show) */
internal fun confidenceWord(confidence: LinkConfidence): String =
    if (confidence == LinkConfidence.HIGH) "Strong" else "Possible"

/**
 * Why Smart Link suggests this event, in words that match what the user can see: when the note was
 * written relative to the event, and the word they share when the titles matched ([titleTerm]).
 */
internal fun suggestionReason(suggestion: CrossDayLinkSuggestion, titleTerm: String?): String {
    val reasons = suggestion.matchReasons
    val parts = mutableListOf<String>()
    val days = abs(suggestion.dayOffset)
    val span = if (days == 1) "1 day" else "$days days"
    parts += when {
        // dayOffset > 0: the event is after the note's day
        suggestion.dayOffset > 0 -> "Written $span before the event"
        suggestion.dayOffset < 0 -> "Written $span after the event"
        MatchReason.DURING_EVENT in reasons -> "Written during the event"
        MatchReason.SHORTLY_AFTER_EVENT in reasons -> "Written soon after the event"
        MatchReason.SHORTLY_BEFORE_EVENT in reasons -> "Written shortly before the event"
        else -> "Same day"
    }
    if (titleTerm != null) parts += "shares “$titleTerm” with the event"
    when {
        MatchReason.SEMANTIC_CONTEXT_MATCH in reasons -> parts += "similar content"
        MatchReason.SEMANTIC_GROUP_MATCH in reasons -> parts += "similar topic words"
    }
    if (MatchReason.SPARSE_CONTEXT in reasons) parts += "few other events nearby"
    if (MatchReason.WEEKDAY_PATTERN in reasons) parts += "same weekday"
    return parts.take(3).joinToString(" · ")
}

// ═══════════════════════════════════════════════════════════════════════════
// SUGGESTION CACHE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Smart Link suggestions for this app session, from BooxNoteIndexer.suggestLinks (one engine for the
 * lists, the note pane and the Suggestions view). Kept between dialog opens; cleared when the notes or
 * the calendar change ([signature]).
 */
internal object BooxNotesSuggestionCache {
    /** noteId -> suggestions (Strong / Possible, best first); notes without one are absent */
    val byNote = mutableStateMapOf<String, List<CrossDayLinkSuggestion>>()

    /** Notes scored at least once since the last reset (absent from [byNote] = no suggestion) */
    val scored = mutableSetOf<String>()

    /** True once every note has been scored */
    var allScored = false

    var signature: String? = null

    fun resetIfChanged(newSignature: String) {
        if (signature != newSignature) {
            byNote.clear()
            scored.clear()
            allScored = false
            signature = newSignature
        }
    }

    fun store(noteIds: Collection<String>, results: Map<String, List<CrossDayLinkSuggestion>>) {
        noteIds.forEach { id ->
            val suggestions = results[id]
            if (suggestions.isNullOrEmpty()) byNote.remove(id) else byNote[id] = suggestions
            scored.add(id)
        }
    }

    fun best(noteId: String): CrossDayLinkSuggestion? = byNote[noteId]?.firstOrNull()
}

// ═══════════════════════════════════════════════════════════════════════════
// PAGE THUMBNAILS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * First-page thumbnails for suggestion cards: rendered one at a time (native BOOX rendering), scaled
 * down, and kept in a small cache so a long list never holds full-size page bitmaps.
 */
internal object BooxNoteThumbnails {
    private val coordinator = BooxNotePreviewCoordinator()
    private val renderLock = Mutex()
    private val cache = LruCache<String, Bitmap>(48)

    suspend fun load(note: BooxNoteIndex, widthPx: Int): Bitmap? {
        val key = "${note.uniqueId}:${note.shapeFileModified}:$widthPx"
        cache.get(key)?.let { return it }
        return renderLock.withLock {
            cache.get(key) ?: run {
                val preview = runCatching { coordinator.loadPreview(note, 0) }.getOrNull()
                val full = (preview as? BooxNotePreviewResult.ImagePreview)?.bitmap ?: return@run null
                val height = (full.height * widthPx / full.width.toFloat()).roundToInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(full, widthPx, height, true)
                if (small != full) full.recycle()
                cache.put(key, small)
                small
            }
        }
    }
}

/** A note's first page as a small bordered sheet (empty sheet while loading or when it can't render). */
@Composable
internal fun NotePageThumbnail(
    note: BooxNoteIndex,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val theme = EInkTheme.BooxNotes
    val widthPx = with(LocalDensity.current) { theme.cardThumbWidth.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, note.uniqueId, note.shapeFileModified, widthPx) {
        value = BooxNoteThumbnails.load(note, widthPx)
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier = modifier
            .size(theme.cardThumbWidth, theme.cardThumbHeight)
            .background(EInkTheme.Colors.background)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "First page of ${note.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SMALL CONTROLS
// ═══════════════════════════════════════════════════════════════════════════

/** Two or more options in one bordered row; the selected one is filled (ModernDialog.SegmentedControl). */
@Composable
internal fun BooxNotesSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = EInkTheme.ModernDialog.SegmentedControl
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Row(
        modifier = modifier
            .height(theme.height)
            .border(theme.borderWidth, theme.borderColor, shape)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (selected) theme.backgroundSelected else theme.background)
                    .noRippleClickable { if (!selected) onSelect(index) }
                    .padding(horizontal = EInkTheme.Global.padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = EInkTheme.BooxNotes.segmentFontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) theme.textColorSelected else theme.textColor,
                    maxLines = 1
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(theme.borderWidth)
                        .background(theme.borderColor)
                )
            }
        }
    }
}

/** "Strong" (filled) or "Possible" (outlined) */
@Composable
internal fun ConfidenceTag(confidence: LinkConfidence, modifier: Modifier = Modifier) {
    val strong = confidence == LinkConfidence.HIGH
    val shape = RoundedCornerShape(EInkTheme.Global.spacingSmall)
    Box(
        modifier = modifier
            .background(if (strong) EInkTheme.Colors.primary else EInkTheme.Colors.background, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
            .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 2.dp)
    ) {
        Text(
            text = confidenceWord(confidence),
            fontSize = EInkTheme.BooxNotes.labelFontSize,
            fontWeight = FontWeight.Bold,
            color = if (strong) EInkTheme.Colors.background else EInkTheme.Colors.primary
        )
    }
}

/** Outlined track with a black fill: no grey fills on e-ink */
@Composable
internal fun BooxNotesProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(EInkTheme.BooxNotes.progressHeight)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(EInkTheme.Colors.primary)
        )
    }
}

/** What the last change did, with a way to undo it (shown at the bottom of the dialog) */
internal data class BooxNotesMessage(
    val text: String,
    val undo: (suspend () -> Unit)? = null
)

@Composable
internal fun BooxNotesMessageBar(
    message: BooxNotesMessage,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(horizontal = EInkTheme.Global.paddingMedium, vertical = EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Text(
            text = message.text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (message.undo != null) {
            EinkSecondaryButton(text = "Undo", onClick = onUndo)
        }
    }
}
