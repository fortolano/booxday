package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.BooxNotePreviewCoordinator
import com.boox.day.booxnotes.BooxNotePreviewResult
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.MatchReason
import com.boox.day.booxnotes.SmartLinkDecisionMode
import com.boox.day.booxnotes.SmartLinkProgress
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteLink
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.SmartLinkEventRepository
import com.boox.day.data.db.SmartLinkPreflightState
import com.boox.day.data.db.SmartLinkPreflightStatus
import com.boox.day.data.db.SmartLinkSourceVisibility
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.editor.DrawCanvas
import com.boox.day.ocr.model.OcrSource
import com.boox.day.search.SearchText
import com.boox.day.ui.components.DialogCloseButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.ScreenColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Notes scored per run: other runs (the note you just opened) get their turn in between */
private const val SCORE_CHUNK = 12

/**
 * Boox Notes window (redesigned Nov 2026): five places on one surface, each with one job. Notes (a day
 * or all of them), Note (the page and its link), Choose Event, Suggestions and Filter. From the Go 10.3
 * up, Notes and Note sit side by side; below that each is a screen of its own, so the page gets the
 * room. Suggestions come from one engine (BooxNoteIndexer.suggestLinks); every link change can be undone.
 */
@Composable
fun BooxNotesDialog(
    date: String,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    booxNoteIndexer: BooxNoteIndexer?,  // Optional: scans for new notes on open, suggests links
    ocrRepository: UnifiedOcrRepository?,  // Optional: handwriting search and filters
    isOcrIndexingInProgress: Boolean = false,
    eventRepository: CalendarEventRepository,
    onDismiss: () -> Unit,
    onLinkChanged: () -> Unit = {},  // A link changed (refreshes the events panel)
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter(),
    onOpenCalendarAccounts: () -> Unit = {},  // No events to match: open Calendar Accounts (host closes this window)
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smartLinkEventRepository = remember(context, eventRepository) {
        SmartLinkEventRepository(context, eventRepository)
    }
    val previewCoordinator = remember { BooxNotePreviewCoordinator() }
    val cache = BooxNotesSuggestionCache
    val start = remember { BooxNotesPlaces.start }
    val prefs = remember(context) { context.getSharedPreferences("boox_notes_window", android.content.Context.MODE_PRIVATE) }

    // Which place is shown
    var showAll by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(start.noteId != null) }
    var showSuggestions by remember { mutableStateOf(start.suggestions) }
    var suggestionsForAll by remember { mutableStateOf(true) }
    var suggestionListView by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var readerOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var linkEvent by remember { mutableStateOf(start.linkToEvent) }
    var twoPane by remember { mutableStateOf(false) }
    var hintClosed by remember { mutableStateOf(prefs.getBoolean("hint_closed", false)) }

    // Notes and links
    var loaded by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var allNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var links by remember { mutableStateOf<Map<String, BooxNoteLink>>(emptyMap()) }
    var linkedEvents by remember { mutableStateOf<Map<String, CalendarEvent?>>(emptyMap()) }
    var currentDate by remember { mutableStateOf(start.date ?: date) }
    var dateNotice by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf(start.noteId) }
    var listPage by remember { mutableIntStateOf(0) }

    // All notes: search and filters
    var query by remember { mutableStateOf("") }
    var handwritingMatches by remember { mutableStateOf<Set<String>>(emptySet()) }
    var unlinkedOnly by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(NoteFilters()) }
    var semantics by remember { mutableStateOf<NoteSemantics?>(null) }
    var showEmptyNotes by remember { mutableStateOf(false) }

    // Suggestions
    var allProgress by remember { mutableStateOf<SmartLinkProgress?>(null) }
    var allJob by remember { mutableStateOf<Job?>(null) }
    var scoringStopped by remember { mutableStateOf(false) }
    var preflight by remember { mutableStateOf<SmartLinkPreflightStatus?>(null) }
    var preflightChecked by remember { mutableStateOf(false) }
    // One at a Time: the note on show stays on show while more suggestions come in
    var reviewNoteId by remember { mutableStateOf<String?>(null) }
    var deferred by remember { mutableStateOf<List<String>>(emptyList()) }
    var reviewDone by remember { mutableIntStateOf(0) }
    var reviewPassed by remember { mutableIntStateOf(0) }
    var preloaded by remember { mutableStateOf<Pair<String, BooxNotePreviewResult?>?>(null) }

    // Selected note
    var pageIndex by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(NotePageState()) }
    var pageCount by remember { mutableIntStateOf(0) }
    var sameDayEventCount by remember { mutableIntStateOf(0) }
    // A note shown before its suggestion came in: the suggestion waits on a line, buttons stay put
    var heldNoteId by remember { mutableStateOf<String?>(null) }

    // Last change, with Undo
    var message by remember { mutableStateOf<BooxNotesMessage?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun isLinked(noteId: String): Boolean = links[noteId]?.linkedEventId != null

    /** No pages and no link. The stored count is not proof a note is empty, so these fold away, never vanish. */
    fun isFoldedEmpty(note: BooxNoteIndex): Boolean = note.pageCount == 0 && !isLinked(note.uniqueId)

    suspend fun loadNotes() {
        allNotes = withContext(Dispatchers.IO) { booxNoteRepository.getAllNotes() }
            .sortedByDescending { it.originalTimestamp }
    }

    suspend fun loadLinks() {
        val newLinks = withContext(Dispatchers.IO) { booxNoteRepository.getAllLinks() }.associateBy { it.noteUniqueId }
        val missing = newLinks.values.mapNotNull { it.linkedEventId }.distinct().filter { it !in linkedEvents }
        if (missing.isNotEmpty()) {
            val fetched = withContext(Dispatchers.IO) { missing.associateWith { smartLinkEventRepository.getEventById(it) } }
            linkedEvents = linkedEvents + fetched
        }
        links = newLinks
    }

    /** Page files change only when a note does; a scan rewrites indexedAt on every note */
    suspend fun checkSuggestionsStillHold() {
        val signature = withContext(Dispatchers.IO) {
            "${allNotes.size}:${allNotes.maxOfOrNull { it.shapeFileModified } ?: 0}:${smartLinkEventRepository.getEventCount()}"
        }
        cache.resetIfChanged(signature)
    }

    /** Suggestions again for these notes after a change (linked notes have none) */
    suspend fun refreshSuggestionsFor(noteIds: Collection<String>) {
        val notes = allNotes.filter { it.uniqueId in noteIds }
        val (linkedNow, unlinked) = notes.partition { isLinked(it.uniqueId) }
        linkedNow.forEach { cache.store(listOf(it.uniqueId), emptyMap()) }
        if (unlinked.isNotEmpty() && booxNoteIndexer != null) {
            cache.store(unlinked.map { it.uniqueId }, booxNoteIndexer.suggestLinks(unlinked))
        }
    }

    /**
     * Applies a link change to [noteIds], then shows [describe] with an Undo that puts every link back
     * exactly as it was. [change] returns false when the change didn't take.
     */
    fun changeLinks(noteIds: List<String>, describe: String, afterUndo: () -> Unit = {}, change: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val previous = withContext(Dispatchers.IO) { noteIds.associateWith { booxNoteRepository.getLinkForNote(it) } }
                val ok = withContext(Dispatchers.IO) { change() }
                loadLinks()
                refreshSuggestionsFor(noteIds)
                onLinkChanged()
                message = if (ok) {
                    BooxNotesMessage(describe, noteIds.singleOrNull()) {
                        withContext(Dispatchers.IO) {
                            previous.forEach { (id, link) -> booxNoteRepository.restoreLink(id, link) }
                        }
                        loadLinks()
                        refreshSuggestionsFor(noteIds)
                        onLinkChanged()
                        afterUndo()
                    }
                } else {
                    BooxNotesMessage("That link could not be saved. Try again.")
                }
            } finally {
                busy = false
            }
        }
    }

    fun runUndo() {
        val undo = message?.undo ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                undo()
                message = BooxNotesMessage("Undone")
            } finally {
                busy = false
            }
        }
    }

    fun shortTitle(note: BooxNoteIndex): String =
        note.title.ifBlank { "Untitled note" }.let { if (it.length > 40) it.take(39) + "…" else it }

    fun linkSuggestion(
        note: BooxNoteIndex,
        suggestion: CrossDayLinkSuggestion,
        mode: SmartLinkDecisionMode,
        afterUndo: () -> Unit = {}
    ) {
        val indexer = booxNoteIndexer ?: return
        changeLinks(listOf(note.uniqueId), "Linked “${shortTitle(note)}” to ${suggestion.event.title}", afterUndo) {
            indexer.applyCrossDaySuggestion(suggestion, mode, userApproved = true)
            booxNoteRepository.getLinkForNote(note.uniqueId)?.linkedEventId == suggestion.event.id
        }
    }

    fun linkToEvent(note: BooxNoteIndex, event: CalendarEvent) {
        changeLinks(listOf(note.uniqueId), "Linked “${shortTitle(note)}” to ${event.title}") {
            booxNoteRepository.linkNoteToEvent(note.uniqueId, event.id, isManual = true)
            booxNoteRepository.getLinkForNote(note.uniqueId)?.linkedEventId == event.id
        }
    }

    fun unlink(note: BooxNoteIndex) {
        val previousEventId = links[note.uniqueId]?.linkedEventId
        changeLinks(listOf(note.uniqueId), "Unlinked “${shortTitle(note)}”") {
            // A manual "no link", so auto-link leaves it alone, and the same event isn't suggested again
            booxNoteRepository.linkNoteToEvent(note.uniqueId, null, isManual = true)
            if (previousEventId != null) booxNoteRepository.dismissSuggestion(note.uniqueId, previousEventId)
            booxNoteRepository.getLinkForNote(note.uniqueId)?.linkedEventId == null
        }
    }

    fun reject(note: BooxNoteIndex, suggestion: CrossDayLinkSuggestion, afterUndo: () -> Unit = {}) {
        changeLinks(listOf(note.uniqueId), "Rejected ${suggestion.event.title} for “${shortTitle(note)}”", afterUndo) {
            booxNoteRepository.dismissSuggestion(note.uniqueId, suggestion.event.id)
            true
        }
    }

    fun linkLikely(items: List<SuggestionItem>) {
        val indexer = booxNoteIndexer ?: return
        changeLinks(items.map { it.note.uniqueId }, "Linked ${plural(items.size, "note")}") {
            items.forEach { indexer.applyCrossDaySuggestion(it.suggestion, SmartLinkDecisionMode.CROSS_DAY_BULK_APPLY, userApproved = true) }
            items.all { booxNoteRepository.getLinkForNote(it.note.uniqueId)?.linkedEventId == it.suggestion.event.id }
        }
    }

    /** It comes round again, and stays a suggestion the next time Boox Notes opens */
    fun decideLater(item: SuggestionItem) {
        val noteId = item.note.uniqueId
        deferred = deferred - noteId + noteId
        reviewPassed++
        reviewNoteId = null
        message = BooxNotesMessage("“${shortTitle(item.note)}” left for later") {
            deferred = deferred - noteId
            reviewPassed--
            reviewNoteId = noteId
        }
    }

    fun openInBooxNotes(note: BooxNoteIndex) {
        scope.launch {
            val validIds = withContext(Dispatchers.IO) {
                booxNoteIndexer?.quickValidation(listOf(note.uniqueId))?.toSet() ?: setOf(note.uniqueId)
            }
            if (note.uniqueId in validIds) {
                booxNotesIntegration.openNote(note.uniqueId)
            } else {
                // Gone from Boox Notes: rescan and show what is left
                isScanning = true
                withContext(Dispatchers.IO) {
                    booxNoteIndexer?.fullScan()
                    booxNoteRepository.invalidateLinksCache()
                }
                isScanning = false
                loadNotes()
                loadLinks()
                noteOpen = false
                message = BooxNotesMessage("“${shortTitle(note)}” is no longer in Boox Notes. The list is updated.")
            }
        }
    }

    /**
     * Scores every note in the background, this day's first, a few at a time, so the note you open never
     * waits behind all the others. Results stay for the app session.
     */
    fun startScoring() {
        val indexer = booxNoteIndexer ?: return
        if (cache.allScored || scoringStopped || allJob?.isActive == true || allNotes.isEmpty()) return
        allJob = scope.launch {
            try {
                val waiting = allNotes
                    .sortedBy { if (it.originalDate == currentDate) 0 else 1 }
                    .filter { it.uniqueId !in cache.scored && !isLinked(it.uniqueId) && it.pageCount > 0 }
                var done = 0
                allProgress = SmartLinkProgress(SmartLinkProgress.Step.COMPARING, 0, waiting.size)
                waiting.chunked(SCORE_CHUNK).forEach { chunk ->
                    val fresh = chunk.filter { it.uniqueId !in cache.scored }
                    if (fresh.isNotEmpty()) cache.store(fresh.map { it.uniqueId }, indexer.suggestLinks(fresh))
                    done += chunk.size
                    allProgress = SmartLinkProgress(SmartLinkProgress.Step.COMPARING, done, waiting.size)
                    delay(50)
                }
                cache.allScored = true
            } finally {
                allProgress = null
                allJob = null
            }
        }
    }

    // Open: scan for new notes, load notes and links, go to where it was opened for
    LaunchedEffect(Unit) {
        if (booxNoteIndexer != null) {
            isScanning = true
            withContext(Dispatchers.IO) {
                if (booxNoteIndexer.scanIfStale() != null) booxNoteRepository.invalidateLinksCache()
            }
            isScanning = false
        }
        loadNotes()
        loadLinks()
        checkSuggestionsStillHold()

        val startNote = start.noteId?.let { id -> allNotes.firstOrNull { it.uniqueId == id } }
        if (startNote != null) {
            currentDate = startNote.originalDate
            if (isFoldedEmpty(startNote)) showEmptyNotes = true
        } else {
            if (start.noteId != null) {
                selectedId = null
                noteOpen = false
                message = BooxNotesMessage("That note is no longer in Boox Notes.")
            }
            val dates = allNotes.map { it.originalDate }.filter { it.isNotBlank() }.distinct()
            if (dates.isNotEmpty() && currentDate !in dates) {
                val requested = parseNoteDay(currentDate)
                val nearest = if (requested == null) dates.max() else dates.minWithOrNull(
                    compareBy<String> { d -> parseNoteDay(d)?.let { abs(ChronoUnit.DAYS.between(requested, it)) } ?: Long.MAX_VALUE }
                        .thenBy { it }
                )
                if (nearest != null) {
                    dateNotice = "No notes on ${formatDayShort(currentDate)}. Showing ${formatDayShort(nearest)}."
                    currentDate = nearest
                }
            }
        }
        loaded = true
    }

    // Check for New Notes finished: read the list again
    val checkCount = BooxNotesPlaces.checkCount
    val checkCountAtOpen = remember { checkCount }
    LaunchedEffect(checkCount) {
        if (checkCount != checkCountAtOpen) {
            loadNotes()
            loadLinks()
            checkSuggestionsStillHold()
        }
    }

    val datesWithNotes = remember(allNotes) {
        allNotes.map { it.originalDate }.filter { it.isNotBlank() }.distinct().sortedDescending()
    }
    // A day counts its notes with pages; a day with only empty notes counts those, so its B leads somewhere
    val noteCountByDate = remember(allNotes, links) {
        allNotes.groupBy { it.originalDate }.mapValues { (_, notes) ->
            notes.count { !isFoldedEmpty(it) }.takeIf { it > 0 } ?: notes.size
        }
    }
    val dayAllNotes = remember(allNotes, currentDate) { allNotes.filter { it.originalDate == currentDate } }
    val dayEmptyNotes = remember(dayAllNotes, links) {
        dayAllNotes.filter(::isFoldedEmpty).takeIf { it.size < dayAllNotes.size }.orEmpty()
    }
    val dayNotes = remember(dayAllNotes, dayEmptyNotes, showEmptyNotes) {
        if (showEmptyNotes) dayAllNotes else dayAllNotes - dayEmptyNotes.toSet()
    }

    // Handwriting search (titles are matched directly)
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (ocrRepository == null || trimmed.length < 2) {
            handwritingMatches = emptySet()
            return@LaunchedEffect
        }
        delay(300)
        handwritingMatches = ocrRepository.searchByType(trimmed, OcrSource.BOOX_NOTE, limit = 200)
            .map { it.sourceId }
            .toSet()
    }

    fun matchesFilters(note: BooxNoteIndex, f: NoteFilters): Boolean {
        val trimmed = query.trim()
        // Title: every typed word somewhere in it, accents and case ignored (search/SearchText)
        val words = SearchText.queryWords(trimmed)
        return (f.emptyNotes || !isFoldedEmpty(note)) &&
            (trimmed.isEmpty() || SearchText.matchesSubstrings(SearchText.fold(note.title), words) ||
                note.uniqueId in handwritingMatches) &&
            (!unlinkedOnly || !isLinked(note.uniqueId)) &&
            (!f.hasSuggestion || (!isLinked(note.uniqueId) && cache.best(note.uniqueId) != null)) &&
            (f.domain == null || semantics?.domains?.get(note.uniqueId) == f.domain) &&
            (f.intent == null || semantics?.intents?.get(note.uniqueId)?.contains(f.intent) == true)
    }

    val allEmptyCount = remember(allNotes, links) { allNotes.count(::isFoldedEmpty) }
    val allFiltered = remember(allNotes, query, handwritingMatches, unlinkedOnly, links, semantics, filters, cache.revision) {
        allNotes.filter { matchesFilters(it, filters) }
    }

    val listNotes = if (showAll) allFiltered else dayNotes
    val selectedNote = allNotes.firstOrNull { it.uniqueId == selectedId }

    // Keep a selection that is in the list being shown (Suggestions and Link a Note pick their own)
    LaunchedEffect(loaded, showAll, listNotes, showSuggestions, linkEvent, noteOpen) {
        if (loaded && !showSuggestions && linkEvent == null && !noteOpen && listNotes.none { it.uniqueId == selectedId }) {
            selectedId = listNotes.firstOrNull()?.uniqueId
        }
    }

    // A new page of the list starts at the top
    LaunchedEffect(showAll, currentDate, query, unlinkedOnly, filters) { listPage = 0 }

    // Undo waits until the next action
    LaunchedEffect(selectedId, pageIndex, showAll, currentDate, noteOpen, showSuggestions) {
        if (!showSuggestions && message?.noteId != selectedId) message = null
    }

    // Topic / Kind of note (only when All notes is shown, and only with read handwriting)
    LaunchedEffect(loaded, showAll) {
        if (loaded && showAll && semantics == null && ocrRepository != null) {
            semantics = withContext(Dispatchers.IO) { loadNoteSemantics(ocrRepository) }
        }
    }

    LaunchedEffect(loaded, allNotes) {
        if (loaded) startScoring()
    }
    // A day you move to, and the note you open, go ahead of the rest
    LaunchedEffect(loaded, currentDate) {
        val indexer = booxNoteIndexer ?: return@LaunchedEffect
        val toScore = dayNotes.filter { it.uniqueId !in cache.scored && !isLinked(it.uniqueId) }
        if (loaded && toScore.isNotEmpty()) {
            cache.store(toScore.map { it.uniqueId }, indexer.suggestLinks(toScore))
        }
    }
    LaunchedEffect(loaded, selectedId) {
        val note = selectedNote ?: return@LaunchedEffect
        val indexer = booxNoteIndexer ?: return@LaunchedEffect
        if (loaded && note.uniqueId !in cache.scored && !isLinked(note.uniqueId)) {
            cache.store(listOf(note.uniqueId), indexer.suggestLinks(listOf(note)))
        }
    }

    // Before suggestions: is there anything to match against?
    LaunchedEffect(showSuggestions) {
        if (showSuggestions && !preflightChecked) {
            preflight = withContext(Dispatchers.IO) {
                smartLinkEventRepository.getPreflightStatus(
                    SmartLinkSourceVisibility(
                        showLocal = sourceFilter.showLocal,
                        showGoogle = sourceFilter.showGoogle,
                        showOutlook = sourceFilter.showOutlook,
                        showCalDav = sourceFilter.showCalDav
                    )
                )
            }
            preflightChecked = true
        }
    }

    fun reasonFor(note: BooxNoteIndex, suggestion: CrossDayLinkSuggestion): String {
        val term = if (MatchReason.TITLE_MATCH in suggestion.matchReasons) {
            booxNoteIndexer?.titleMatchTerm(note, suggestion.event)
        } else {
            null
        }
        return suggestionReason(suggestion, term)
    }

    val suggestionCount = remember(allNotes, links, cache.revision) {
        allNotes.count { !isLinked(it.uniqueId) && it.pageCount > 0 && cache.best(it.uniqueId) != null }
    }
    val unlinkedInScope = remember(allNotes, dayAllNotes, links, suggestionsForAll) {
        (if (suggestionsForAll) allNotes else dayAllNotes).filter { !isLinked(it.uniqueId) && it.pageCount > 0 }
    }
    // This day's first, then back in time; notes left for later come round last
    val suggestionItems = remember(showSuggestions, unlinkedInScope, cache.revision, deferred, currentDate) {
        if (!showSuggestions) {
            emptyList()
        } else {
            unlinkedInScope
                .mapNotNull { n -> cache.best(n.uniqueId)?.let { SuggestionItem(n, it, reasonFor(n, it)) } }
                .sortedWith(
                    compareBy<SuggestionItem> { deferred.indexOf(it.note.uniqueId) }
                        .thenBy { if (it.note.originalDate == currentDate) 0 else 1 }
                        .thenByDescending { it.note.originalTimestamp }
                )
        }
    }
    val reviewItem = suggestionItems.firstOrNull { it.note.uniqueId == reviewNoteId } ?: suggestionItems.firstOrNull()
    val reviewId = reviewItem?.note?.uniqueId
    val reviewing = showSuggestions && !suggestionListView

    // One at a Time shows its note through the same page loading as Note
    LaunchedEffect(reviewing, reviewId) {
        if (reviewing && reviewId != null) {
            reviewNoteId = reviewId
            selectedId = reviewId
        }
    }

    // Selected note: its page, and how many events its day has
    LaunchedEffect(selectedId) {
        pageIndex = 0
        heldNoteId = selectedId?.takeIf { it !in cache.scored && !isLinked(it) }
        val note = selectedNote ?: return@LaunchedEffect
        sameDayEventCount = withContext(Dispatchers.IO) {
            smartLinkEventRepository.getEventsForDateSync(note.originalDate).size
        }
    }
    // The list has its own thumbnails: a big page is drawn only where it is on show
    val pageWanted = twoPane || noteOpen || readerOpen || reviewing
    LaunchedEffect(selectedId, pageIndex, pageWanted) {
        if (!pageWanted) return@LaunchedEffect
        val note = selectedNote ?: return@LaunchedEffect
        val ready = preloaded?.takeIf { it.first == note.uniqueId && pageIndex == 0 }
        if (ready == null) page = NotePageState(loading = true)
        val result = if (ready != null) {
            ready.second
        } else {
            runCatching {
                BooxNoteThumbnails.renderLock.withLock { previewCoordinator.loadPreview(note, pageIndex) }
            }.getOrNull()
        }
        if (selectedId != note.uniqueId) return@LaunchedEffect
        page = NotePageState(loading = false, result = result)
        pageCount = result?.totalPages?.takeIf { it > 0 } ?: note.pageCount
        if (result != null && result.totalPages > 0 && result.currentPageIndex != pageIndex) {
            pageIndex = result.currentPageIndex
        }
    }
    // One at a Time: the next page is ready before it is asked for
    LaunchedEffect(reviewing, reviewId, suggestionItems) {
        if (!reviewing) return@LaunchedEffect
        val next = suggestionItems.firstOrNull { it.note.uniqueId != reviewId }?.note ?: return@LaunchedEffect
        if (preloaded?.first == next.uniqueId) return@LaunchedEffect
        delay(400)
        val result = runCatching {
            BooxNoteThumbnails.renderLock.withLock { previewCoordinator.loadPreview(next, 0) }
        }.getOrNull()
        preloaded = next.uniqueId to result
    }

    fun rowState(note: BooxNoteIndex): NoteRowState {
        val eventId = links[note.uniqueId]?.linkedEventId
        val event = eventId?.let { linkedEvents[it] }
        val suggested = if (eventId == null) cache.best(note.uniqueId)?.event else null
        return NoteRowState(
            linkedLine = event?.let { "${it.title} · ${eventWhen(it, withDay = it.dayId != note.originalDate)}" },
            isLinked = eventId != null,
            suggestedLine = suggested?.let { if (it.dayId == note.originalDate) it.title else "${it.title} · ${formatDayShort(it.dayId)}" },
            stripe = ScreenColour.calendarStripe(event?.colorHex)
        )
    }

    val best = selectedNote?.let { cache.best(it.uniqueId) }
    val held = selectedNote != null && heldNoteId == selectedNote.uniqueId
    val linkUi: NoteLinkUi = selectedNote?.let { note ->
        val link = links[note.uniqueId]
        when {
            link?.linkedEventId != null -> NoteLinkUi.Linked(linkedEvents[link.linkedEventId], byUser = link.isManualLink)
            best != null && !held -> NoteLinkUi.Suggested(best, reasonFor(note, best))
            booxNoteIndexer != null && cache.revision >= 0 && note.uniqueId !in cache.scored -> NoteLinkUi.Checking
            else -> NoteLinkUi.NotLinked(sameDayEventCount)
        }
    } ?: NoteLinkUi.Checking

    fun openSuggestions() {
        scoringStopped = false
        startScoring()
        reviewNoteId = null
        reviewDone = 0
        reviewPassed = 0
        deferred = emptyList()
        suggestionListView = false
        message = null
        showSuggestions = true
    }

    val closeOrBack: () -> Unit = {
        when {
            filterOpen -> filterOpen = false
            pickerOpen -> pickerOpen = false
            readerOpen -> readerOpen = false
            showSuggestions -> showSuggestions = false
            linkEvent != null -> if (start.linkToEvent != null) onDismiss() else linkEvent = null
            noteOpen && !twoPane -> noteOpen = false
            else -> onDismiss()
        }
    }

    // E-ink: a Popup, not a Dialog (no scrim, no dimming)
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = closeOrBack,
        properties = PopupProperties(focusable = true)
    ) {
        val theme = EInkTheme.BooxNotes
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = theme.surfaceTopInset, bottom = theme.surfaceBottomInset)
                    .fillMaxWidth(theme.surfaceWidthFraction)
                    .fillMaxHeight()
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(theme.surfacePadding)
            ) {
                val wide = maxWidth >= theme.twoPaneMinWidth
                SideEffect { twoPane = wide }
                val placeModifier = Modifier.fillMaxSize()
                val note = selectedNote

                when {
                    filterOpen -> BooxNotesFilterScreen(
                        filters = filters,
                        semantics = semantics,
                        matchCount = { draft -> allNotes.count { matchesFilters(it, draft) } },
                        onShow = {
                            filters = it
                            filterOpen = false
                        },
                        onCancel = { filterOpen = false },
                        modifier = placeModifier
                    )

                    pickerOpen && note != null -> BooxNoteEventPicker(
                        note = note,
                        linkedEventId = links[note.uniqueId]?.linkedEventId,
                        suggestedEventId = cache.best(note.uniqueId)?.event?.id,
                        loadEventsInRange = { from, to ->
                            withContext(Dispatchers.IO) { smartLinkEventRepository.getEventsInRange(from, to) }
                        },
                        onPick = { event ->
                            pickerOpen = false
                            linkToEvent(note, event)
                        },
                        onBack = { pickerOpen = false },
                        onClose = onDismiss,
                        modifier = placeModifier
                    )

                    readerOpen && note != null -> BooxNoteReader(
                        note = note,
                        page = page,
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onPageChange = { pageIndex = it },
                        onBack = { readerOpen = false },
                        onClose = onDismiss,
                        onOpenInBooxNotes = { openInBooxNotes(note) },
                        modifier = placeModifier
                    )

                    showSuggestions -> {
                        val blocked = preflight?.state in setOf(
                            SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS,
                            SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS,
                            SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS,
                            SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS
                        )
                        val finding = allProgress
                        val content = when {
                            !preflightChecked -> SuggestionsContent.Checking
                            blocked -> SuggestionsContent.Blocked(preflight!!)
                            !loaded || (suggestionItems.isEmpty() && finding != null) ->
                                SuggestionsContent.Finding(finding?.fraction ?: 0f, findingStepText(finding, isScanning))
                            else -> SuggestionsContent.Ready
                        }
                        val reviewTotal = reviewDone + suggestionItems.size
                        BooxNotesSuggestionsView(
                            host = SuggestionsHost(
                                content = content,
                                items = suggestionItems,
                                unlinkedCount = unlinkedInScope.size,
                                scopeAll = suggestionsForAll,
                                dayLabel = formatDayShort(currentDate),
                                onScopeChange = { suggestionsForAll = it },
                                indexingNote = if (isOcrIndexingInProgress) "Handwriting is still being read, so suggestions may change." else null,
                                onBack = { showSuggestions = false },
                                onClose = onDismiss,
                                onLinkLikely = { linkLikely(it) },
                                onLink = {
                                    reviewDone++
                                    val noteId = it.note.uniqueId
                                    linkSuggestion(it.note, it.suggestion, SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION, afterUndo = {
                                        reviewDone--
                                        reviewNoteId = noteId
                                    })
                                },
                                onReject = {
                                    reviewDone++
                                    val noteId = it.note.uniqueId
                                    reject(it.note, it.suggestion, afterUndo = {
                                        reviewDone--
                                        reviewNoteId = noteId
                                    })
                                },
                                onOpenNote = {
                                    selectedId = it.uniqueId
                                    readerOpen = true
                                },
                                onStop = {
                                    scoringStopped = true
                                    allJob?.cancel()
                                    showSuggestions = false
                                },
                                onOpenCalendarAccounts = onOpenCalendarAccounts,
                                message = message,
                                onUndo = ::runUndo
                            ),
                            current = reviewItem,
                            position = (reviewDone + reviewPassed % suggestionItems.size.coerceAtLeast(1) + 1).coerceAtMost(reviewTotal.coerceAtLeast(1)),
                            total = reviewTotal,
                            page = page,
                            pageIndex = pageIndex,
                            pageCount = pageCount,
                            onPageChange = { pageIndex = it },
                            onOpenReader = { readerOpen = true },
                            onDecideLater = { reviewItem?.let { decideLater(it) } },
                            listView = suggestionListView,
                            onListViewChange = { suggestionListView = it },
                            modifier = placeModifier
                        )
                    }

                    linkEvent != null -> {
                        val event = linkEvent!!
                        BooxNoteNotePicker(
                            event = event,
                            notes = allNotes.filter { !isFoldedEmpty(it) },
                            linkedEventOf = { links[it.uniqueId]?.linkedEventId },
                            onPick = { picked ->
                                // Land on the note: it shows the link, with Undo where Unlink goes
                                linkEvent = null
                                currentDate = picked.originalDate
                                selectedId = picked.uniqueId
                                noteOpen = true
                                linkToEvent(picked, event)
                            },
                            onBack = closeOrBack,
                            onClose = onDismiss,
                            modifier = placeModifier
                        )
                    }

                    else -> {
                        val undoHere = message?.takeIf { it.undo != null && it.noteId == note?.uniqueId }
                        val noteActions = NoteLinkActions(
                            onLink = { s -> note?.let { linkSuggestion(it, s, SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION) } },
                            onReject = { s -> note?.let { reject(it, s) } },
                            onChooseEvent = { pickerOpen = true },
                            onUnlink = { note?.let { unlink(it) } },
                            // Two panes have the bar under the list; a single screen has no room for one
                            undo = if (!wide && undoHere != null) ::runUndo else null,
                            lateSuggestion = if (held) best?.event?.title else null,
                            onShowLate = { heldNoteId = null }
                        )

                        if (!wide && noteOpen) {
                            BooxNotePane(
                                note = note,
                                link = linkUi,
                                page = page,
                                pageIndex = pageIndex,
                                pageCount = pageCount,
                                onPageChange = { pageIndex = it },
                                actions = noteActions,
                                onOpenReader = { readerOpen = true },
                                onOpenInBooxNotes = { note?.let { openInBooxNotes(it) } },
                                modifier = placeModifier,
                                bar = {
                                    BooxNotesBar(
                                        title = note?.title?.ifBlank { "Untitled note" }.orEmpty(),
                                        onClose = onDismiss,
                                        onBack = { noteOpen = false }
                                    )
                                }
                            )
                        } else {
                            Column(
                                modifier = placeModifier,
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                            ) {
                                BooxNotesHeader(
                                    showAll = showAll,
                                    onShowAllChange = { showAll = it },
                                    currentDate = currentDate,
                                    datesWithNotes = datesWithNotes,
                                    noteCountByDate = noteCountByDate,
                                    onDateChange = {
                                        currentDate = it
                                        dateNotice = null
                                    },
                                    onClose = onDismiss,
                                    wide = wide,
                                    menu = {
                                        NotesMoreMenu(
                                            onReview = ::openSuggestions,
                                            onCheck = { DrawCanvas.refreshBooxNotes.tryEmit(Unit) },
                                            onSetup = { BooxNotesPlaces.openSetup() },
                                            onHelp = onOpenHelp
                                        )
                                    },
                                    allControls = {
                                        ToggleChip("Not Linked", unlinkedOnly) { unlinkedOnly = it }
                                        EinkSecondaryButton(
                                            text = filters.activeCount.let { if (it > 0) "Filter ($it)" else "Filter" },
                                            onClick = { filterOpen = true }
                                        )
                                    }
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(if (wide) theme.listPaneFraction else 1f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                                    ) {
                                        if (showAll) {
                                            NotesSearchField(
                                                query = query,
                                                onQueryChange = { query = it },
                                                hint = if (ocrRepository != null) "Search titles and handwriting" else "Search titles"
                                            )
                                        }
                                        if (!hintClosed && allNotes.isNotEmpty()) {
                                            NotesFirstUseHint(onClose = {
                                                hintClosed = true
                                                prefs.edit().putBoolean("hint_closed", true).apply()
                                            })
                                        }
                                        val caption = notesCaption(
                                            scanning = isScanning || GlobalAppSettings.isBooxNotesScanning,
                                            notice = if (showAll) null else dateNotice,
                                            listed = listNotes.size,
                                            shownTotal = if (showAll) allNotes.size - if (filters.emptyNotes) 0 else allEmptyCount else listNotes.size,
                                            linked = listNotes.count { isLinked(it.uniqueId) }
                                        )
                                        NotesCaptionRow(
                                            caption = caption,
                                            reviewCount = suggestionCount,
                                            shortLabel = wide,
                                            onReview = ::openSuggestions
                                        )
                                        val entries = remember(showAll, allFiltered, dayNotes, dayEmptyNotes, showEmptyNotes) {
                                            if (showAll) {
                                                allNotesEntries(allFiltered)
                                            } else {
                                                dayNotes.map { NoteListEntry.Note(it) } + if (dayEmptyNotes.isEmpty()) {
                                                    emptyList()
                                                } else {
                                                    listOf(NoteListEntry.EmptyNotes(dayEmptyNotes.size, showEmptyNotes))
                                                }
                                            }
                                        }
                                        val listModifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                        when {
                                            !loaded -> CenteredText(if (isScanning) "Checking for new notes…" else "Loading notes…", listModifier)
                                            allNotes.isEmpty() -> CenteredText(
                                                "No Boox Notes yet. Notes you write in the Notes app show up here on the days you wrote them.",
                                                listModifier
                                            )
                                            entries.isEmpty() -> CenteredText("No notes match.", listModifier)
                                            else -> PagedNoteList(
                                                entries = entries,
                                                pageIndex = listPage,
                                                onPageChange = { listPage = it },
                                                selectedId = if (wide || noteOpen) selectedId else null,
                                                showDay = showAll,
                                                rowState = ::rowState,
                                                linkButtons = !wide,
                                                onSelect = {
                                                    selectedId = it.uniqueId
                                                    if (!wide) noteOpen = true
                                                },
                                                onLink = { n ->
                                                    cache.best(n.uniqueId)?.let { linkSuggestion(n, it, SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION) }
                                                },
                                                onShowEmptyChange = { showEmptyNotes = it },
                                                modifier = listModifier
                                            )
                                        }
                                        message?.let { BooxNotesMessageBar(it, ::runUndo) }
                                    }
                                    if (wide) {
                                        Spacer(modifier = Modifier.width(theme.paneGap))
                                        BooxNotePane(
                                            note = note,
                                            link = linkUi,
                                            page = page,
                                            pageIndex = pageIndex,
                                            pageCount = pageCount,
                                            onPageChange = { pageIndex = it },
                                            actions = noteActions,
                                            onOpenReader = { readerOpen = true },
                                            onOpenInBooxNotes = { note?.let { openInBooxNotes(it) } },
                                            modifier = Modifier
                                                .weight(1f - theme.listPaneFraction)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The line above the list: what is listed, or what is going on */
private fun notesCaption(scanning: Boolean, notice: String?, listed: Int, shownTotal: Int, linked: Int): String = when {
    scanning -> "Checking for new notes…"
    notice != null -> notice
    listed != shownTotal -> "$listed of ${plural(shownTotal, "note")}"
    else -> "${plural(listed, "note")} · $linked linked"
}

@Composable
private fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(EInkTheme.Global.paddingLarge)
        )
    }
}

/** Two lines the first time; Close removes them for good */
@Composable
private fun NotesFirstUseHint(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your Boox notes, on the days you wrote them",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "Link a note to an event and you can open it from your calendar.",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
        }
        EinkSecondaryButton(text = "Close", onClick = onClose)
    }
}

/** ⋮: everything that isn't a note */
@Composable
private fun NotesMoreMenu(onReview: () -> Unit, onCheck: () -> Unit, onSetup: () -> Unit, onHelp: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val menuOffsetY = with(LocalDensity.current) { EInkTheme.Global.touchTarget.roundToPx() }
    Box {
        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .noRippleClickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, menuOffsetY),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(EInkTheme.PopupMenu.width)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                ) {
                    listOf(
                        "Review Suggestions" to onReview,
                        "Check for New Notes" to onCheck,
                        "Boox Notes Setup" to onSetup,
                        "Help" to onHelp
                    ).forEach { (label, action) ->
                        FilterMenuItem(text = label, selected = false) {
                            open = false
                            action()
                        }
                    }
                }
            }
        }
    }
}

/** Title, ⋮ and X; Day | All notes; the day, or what narrows All notes. One row when there is room. */
@Composable
internal fun BooxNotesHeader(
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    currentDate: String,
    datesWithNotes: List<String>,
    noteCountByDate: Map<String, Int>,
    onDateChange: (String) -> Unit,
    onClose: () -> Unit,
    closeModifier: Modifier = Modifier,
    wide: Boolean = false,
    menu: @Composable () -> Unit = {},
    allControls: @Composable RowScope.() -> Unit = {}
) {
    val spacing = EInkTheme.BooxNotes.headerSpacing
    val title: @Composable () -> Unit = {
        Text(
            text = "Boox Notes",
            fontSize = EInkTheme.BooxNotes.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 1
        )
    }
    val segmented: @Composable () -> Unit = {
        BooxNotesSegmented(
            options = listOf("Day", "All notes"),
            selectedIndex = if (showAll) 1 else 0,
            onSelect = { onShowAllChange(it == 1) }
        )
    }
    // The controls take what is left, so the X always keeps its full size
    val controls: @Composable RowScope.() -> Unit = {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAll) {
                allControls()
            } else if (datesWithNotes.isNotEmpty()) {
                BooxNotesDateControl(
                    currentDate = currentDate,
                    datesWithNotes = datesWithNotes,
                    noteCountByDate = noteCountByDate,
                    onDateChange = onDateChange
                )
            }
        }
    }
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            title()
            segmented()
            controls()
            menu()
            DialogCloseButton(onClose = onClose, modifier = closeModifier)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { title() }
                menu()
                DialogCloseButton(onClose = onClose, modifier = closeModifier)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                segmented()
                controls()
            }
        }
    }
}

/** ‹ Mon, Oct 12 ▾ ›: arrows step through days that have notes; the label lists them all */
@Composable
private fun BooxNotesDateControl(
    currentDate: String,
    datesWithNotes: List<String>,  // newest first
    noteCountByDate: Map<String, Int>,
    onDateChange: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val index = datesWithNotes.indexOf(currentDate)
    val older = if (index >= 0) datesWithNotes.getOrNull(index + 1) else datesWithNotes.firstOrNull { it < currentDate }
    val newer = if (index > 0) datesWithNotes[index - 1] else if (index < 0) datesWithNotes.lastOrNull { it > currentDate } else null
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    val menuOffsetY = with(LocalDensity.current) { EInkTheme.Global.touchTarget.roundToPx() }

    Box {
        Row(
            modifier = Modifier
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateArrow(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                description = "Earlier day with notes",
                target = older,
                onDateChange = onDateChange
            )
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxHeight()
                    .noRippleClickable { open = true }
                    .padding(horizontal = EInkTheme.Global.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDayShort(currentDate),
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Days with notes",
                    tint = EInkTheme.Colors.primary
                )
            }
            DateArrow(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                description = "Later day with notes",
                target = newer,
                onDateChange = onDateChange
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, menuOffsetY),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                val dateMenuState = rememberLazyListState()
                Box(modifier = Modifier
                    .width(EInkTheme.PopupPanel.width)
                    .heightIn(max = EInkTheme.PopupPanel.maxHeight)
                    .background(EInkTheme.PopupMenu.background)
                    .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)) {
                    LazyColumn(
                        state = dateMenuState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(datesWithNotes, key = { it }) { day ->
                            val selected = day == currentDate
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(EInkTheme.MenuItem.height)
                                    .background(if (selected) EInkTheme.MenuItem.selectedBackground else EInkTheme.PopupMenu.background)
                                    .noRippleClickable {
                                        open = false
                                        onDateChange(day)
                                    }
                                    .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val color = if (selected) EInkTheme.MenuItem.selectedTextColor else EInkTheme.MenuItem.textColor
                                Text(
                                    text = formatDayShort(day),
                                    fontSize = EInkTheme.MenuItem.fontSize,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = color,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = plural(noteCountByDate[day] ?: 0, "note"),
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = color
                                )
                            }
                        }
                    }
                    ScrollMoreBar(dateMenuState)
                }
            }
        }
    }
}

@Composable
private fun DateArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    target: String?,
    onDateChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .then(if (target != null) Modifier.noRippleClickable { onDateChange(target) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (target != null) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
            modifier = Modifier.size(EInkTheme.Global.iconSize)
        )
    }
}
