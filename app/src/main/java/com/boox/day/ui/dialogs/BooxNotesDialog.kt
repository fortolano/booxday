package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.BooxNotePreviewCoordinator
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.MatchReason
import com.boox.day.booxnotes.SmartLinkDecisionMode
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
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Boox Notes dialog (redesigned Oct 2026): the notes written on a day (Day) or all of them (All notes,
 * with search and filters), and the selected note with its page as the largest element. Linking a note
 * to an event happens in one row of the note pane; Smart Link suggestions come from one engine
 * (BooxNoteIndexer.suggestLinks) and are reviewed in place. Every link change can be undone.
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
    autoOpenBulkWizard: Boolean = false,  // Open the Smart Link suggestions for all notes right away
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter(),
    onOpenCalendarAccounts: () -> Unit = {}  // Smart Link found no events: open Calendar Accounts (host closes this dialog)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smartLinkEventRepository = remember(context, eventRepository) {
        SmartLinkEventRepository(context, eventRepository)
    }
    val previewCoordinator = remember { BooxNotePreviewCoordinator() }
    val cache = BooxNotesSuggestionCache

    // What is shown
    var showAll by remember { mutableStateOf(autoOpenBulkWizard) }
    var showSuggestions by remember { mutableStateOf(autoOpenBulkWizard) }
    var suggestionsForAll by remember { mutableStateOf(autoOpenBulkWizard) }
    var readerOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    // Notes and links
    var loaded by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var allNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var links by remember { mutableStateOf<Map<String, BooxNoteLink>>(emptyMap()) }
    var linkedEvents by remember { mutableStateOf<Map<String, CalendarEvent?>>(emptyMap()) }
    var currentDate by remember { mutableStateOf(date) }
    var dateNotice by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    // All notes: search and filters
    var query by remember { mutableStateOf("") }
    var handwritingMatches by remember { mutableStateOf<Set<String>>(emptySet()) }
    var unlinkedOnly by remember { mutableStateOf(false) }
    var semantics by remember { mutableStateOf<NoteSemantics?>(null) }
    var selectedDomain by remember { mutableStateOf<Domain?>(null) }
    var selectedIntent by remember { mutableStateOf<Intent?>(null) }

    // Smart Link
    var allProgress by remember { mutableStateOf<Float?>(null) }
    var allJob by remember { mutableStateOf<Job?>(null) }
    var preflight by remember { mutableStateOf<SmartLinkPreflightStatus?>(null) }
    var preflightChecked by remember { mutableStateOf(false) }

    // Selected note
    var pageIndex by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(NotePageState()) }
    var pageCount by remember { mutableStateOf(0) }
    var sameDayEventCount by remember { mutableStateOf(0) }

    // Last change, with Undo
    var message by remember { mutableStateOf<BooxNotesMessage?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun isLinked(noteId: String): Boolean = links[noteId]?.linkedEventId != null

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
    fun changeLinks(noteIds: List<String>, describe: String, change: suspend () -> Boolean) {
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
                    BooxNotesMessage(describe) {
                        withContext(Dispatchers.IO) {
                            previous.forEach { (id, link) -> booxNoteRepository.restoreLink(id, link) }
                        }
                        loadLinks()
                        refreshSuggestionsFor(noteIds)
                        onLinkChanged()
                    }
                } else {
                    BooxNotesMessage("That link could not be saved. Try again.")
                }
            } finally {
                busy = false
            }
        }
    }

    fun shortTitle(note: BooxNoteIndex): String =
        note.title.ifBlank { "Untitled note" }.let { if (it.length > 40) it.take(39) + "…" else it }

    fun linkSuggestion(note: BooxNoteIndex, suggestion: CrossDayLinkSuggestion, mode: SmartLinkDecisionMode) {
        val indexer = booxNoteIndexer ?: return
        changeLinks(listOf(note.uniqueId), "Linked “${shortTitle(note)}” to ${suggestion.event.title}") {
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

    fun skip(item: SuggestionItem) {
        changeLinks(listOf(item.note.uniqueId), "Skipped ${item.suggestion.event.title} for “${shortTitle(item.note)}”") {
            booxNoteRepository.dismissSuggestion(item.note.uniqueId, item.suggestion.event.id)
            true
        }
    }

    fun linkStrong(items: List<SuggestionItem>) {
        val indexer = booxNoteIndexer ?: return
        changeLinks(items.map { it.note.uniqueId }, "Linked ${plural(items.size, "note")}") {
            items.forEach { indexer.applyCrossDaySuggestion(it.suggestion, SmartLinkDecisionMode.CROSS_DAY_BULK_APPLY, userApproved = true) }
            items.all { booxNoteRepository.getLinkForNote(it.note.uniqueId)?.linkedEventId == it.suggestion.event.id }
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
                message = BooxNotesMessage("“${shortTitle(note)}” is no longer in Boox Notes. The list is updated.")
            }
        }
    }

    fun startAllSuggestions() {
        val indexer = booxNoteIndexer ?: return
        if (cache.allScored || allJob?.isActive == true || allNotes.isEmpty()) return
        allJob = scope.launch {
            allProgress = 0f
            try {
                val notes = allNotes
                var shown = 0f
                val results = indexer.suggestLinks(notes) { progress ->
                    // At most every 10%: each change refreshes the e-ink panel
                    if (progress - shown >= 0.1f) {
                        shown = progress
                        allProgress = progress
                    }
                }
                cache.store(notes.map { it.uniqueId }, results)
                cache.allScored = true
            } finally {
                allProgress = null
                allJob = null
            }
        }
    }

    // Open: scan for new notes, load notes and links, pick the day
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
        val signature = withContext(Dispatchers.IO) {
            "${allNotes.size}:${allNotes.maxOfOrNull { it.indexedAt } ?: 0}:${smartLinkEventRepository.getEventCount()}"
        }
        cache.resetIfChanged(signature)

        val dates = allNotes.map { it.originalDate }.filter { it.isNotBlank() }.distinct()
        if (dates.isNotEmpty() && date !in dates) {
            val requested = parseNoteDay(date)
            val nearest = if (requested == null) dates.max() else dates.minWithOrNull(
                compareBy<String> { d -> parseNoteDay(d)?.let { abs(ChronoUnit.DAYS.between(requested, it)) } ?: Long.MAX_VALUE }
                    .thenBy { it }
            )
            if (nearest != null) {
                currentDate = nearest
                dateNotice = "No notes on ${formatDayShort(date)}. Showing ${formatDayShort(nearest)}."
            }
        }
        loaded = true
    }

    val datesWithNotes = remember(allNotes) {
        allNotes.map { it.originalDate }.filter { it.isNotBlank() }.distinct().sortedDescending()
    }
    val noteCountByDate = remember(allNotes) { allNotes.groupingBy { it.originalDate }.eachCount() }
    val dayNotes = remember(allNotes, currentDate) { allNotes.filter { it.originalDate == currentDate } }

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
    val allFiltered = remember(allNotes, query, handwritingMatches, unlinkedOnly, links, semantics, selectedDomain, selectedIntent) {
        val trimmed = query.trim()
        allNotes.filter { note ->
            (trimmed.isEmpty() || note.title.contains(trimmed, ignoreCase = true) || note.uniqueId in handwritingMatches) &&
                (!unlinkedOnly || links[note.uniqueId]?.linkedEventId == null) &&
                (selectedDomain == null || semantics?.domains?.get(note.uniqueId) == selectedDomain) &&
                (selectedIntent == null || semantics?.intents?.get(note.uniqueId)?.contains(selectedIntent) == true)
        }
    }

    val listNotes = if (showAll) allFiltered else dayNotes
    val selectedNote = allNotes.firstOrNull { it.uniqueId == selectedId }

    // Keep a selection that is in the list being shown
    LaunchedEffect(loaded, showAll, listNotes) {
        if (loaded && listNotes.none { it.uniqueId == selectedId }) {
            selectedId = listNotes.firstOrNull()?.uniqueId
        }
    }

    // Category / Purpose (only when All notes is shown, and only with read handwriting)
    LaunchedEffect(loaded, showAll) {
        if (loaded && showAll && semantics == null && ocrRepository != null) {
            semantics = withContext(Dispatchers.IO) { loadNoteSemantics(ocrRepository) }
        }
    }

    // Suggestions: this day's notes right away; all notes once All notes or its suggestions are shown
    LaunchedEffect(loaded, currentDate, allNotes) {
        val indexer = booxNoteIndexer ?: return@LaunchedEffect
        val toScore = dayNotes.filter { it.uniqueId !in cache.scored && !isLinked(it.uniqueId) }
        if (loaded && toScore.isNotEmpty()) {
            cache.store(toScore.map { it.uniqueId }, indexer.suggestLinks(toScore))
        }
    }
    LaunchedEffect(loaded, showAll, showSuggestions, suggestionsForAll) {
        if (loaded && (showAll || (showSuggestions && suggestionsForAll))) startAllSuggestions()
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
                        showOutlook = sourceFilter.showOutlook
                    )
                )
            }
            preflightChecked = true
        }
    }

    // Selected note: its page, and how many events its day has
    LaunchedEffect(selectedId) {
        pageIndex = 0
        val note = selectedNote ?: return@LaunchedEffect
        sameDayEventCount = withContext(Dispatchers.IO) {
            smartLinkEventRepository.getEventsForDateSync(note.originalDate).size
        }
    }
    LaunchedEffect(selectedId, pageIndex) {
        val note = selectedNote ?: return@LaunchedEffect
        page = NotePageState(loading = true)
        val result = runCatching { previewCoordinator.loadPreview(note, pageIndex) }.getOrNull()
        if (selectedId != note.uniqueId) return@LaunchedEffect
        page = NotePageState(loading = false, result = result)
        pageCount = result?.totalPages?.takeIf { it > 0 } ?: note.pageCount
        if (result != null && result.totalPages > 0 && result.currentPageIndex != pageIndex) {
            pageIndex = result.currentPageIndex
        }
    }

    fun rowState(note: BooxNoteIndex): NoteRowState {
        val eventId = links[note.uniqueId]?.linkedEventId
        return NoteRowState(
            linkedEventTitle = eventId?.let { linkedEvents[it]?.title },
            isLinked = eventId != null,
            hasSuggestion = eventId == null && cache.best(note.uniqueId) != null
        )
    }

    fun reasonFor(note: BooxNoteIndex, suggestion: CrossDayLinkSuggestion): String {
        val term = if (MatchReason.TITLE_MATCH in suggestion.matchReasons) {
            booxNoteIndexer?.titleMatchTerm(note, suggestion.event)
        } else {
            null
        }
        return suggestionReason(suggestion, term)
    }

    val linkUi: NoteLinkUi = selectedNote?.let { note ->
        val link = links[note.uniqueId]
        val best = cache.best(note.uniqueId)
        when {
            link?.linkedEventId != null -> NoteLinkUi.Linked(linkedEvents[link.linkedEventId], byUser = link.isManualLink)
            best != null -> NoteLinkUi.Suggested(best, reasonFor(note, best))
            booxNoteIndexer != null && note.uniqueId !in cache.scored -> NoteLinkUi.Checking
            else -> NoteLinkUi.NotLinked(sameDayEventCount)
        }
    } ?: NoteLinkUi.Checking

    val closeOrBack: () -> Unit = {
        when {
            pickerOpen -> pickerOpen = false
            readerOpen -> readerOpen = false
            showSuggestions -> showSuggestions = false
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
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = theme.surfaceTopInset, bottom = theme.surfaceBottomInset)
                    .fillMaxWidth(theme.surfaceWidthFraction)
                    .fillMaxHeight()
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(theme.surfacePadding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                val note = selectedNote
                when {
                    readerOpen && note != null -> BooxNoteReader(
                        note = note,
                        page = page,
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onPageChange = { pageIndex = it },
                        onBack = { readerOpen = false },
                        onOpenInBooxNotes = { openInBooxNotes(note) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    showSuggestions -> {
                        val scopeNotes = if (suggestionsForAll) allNotes else dayNotes
                        val unlinked = scopeNotes.filter { !isLinked(it.uniqueId) }
                        val items = unlinked.mapNotNull { n ->
                            cache.best(n.uniqueId)?.let { SuggestionItem(n, it, reasonFor(n, it)) }
                        }
                        val blocked = preflight?.state in setOf(
                            SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED,
                            SmartLinkPreflightState.SERVER_OCR_DISABLED,
                            SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS,
                            SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS,
                            SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS,
                            SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS
                        )
                        val content = when {
                            !preflightChecked -> SuggestionsContent.Checking
                            blocked -> SuggestionsContent.Blocked(preflight!!)
                            suggestionsForAll && !cache.allScored -> SuggestionsContent.Finding(allProgress ?: 0f)
                            else -> SuggestionsContent.Ready
                        }
                        BooxNotesSuggestionsView(
                            scopeAll = suggestionsForAll,
                            dayLabel = formatDayShort(currentDate),
                            onScopeChange = { suggestionsForAll = it },
                            content = content,
                            items = items,
                            unlinkedCount = unlinked.size,
                            indexingNote = if (isOcrIndexingInProgress) "Handwriting is still being read, so suggestions may change." else null,
                            onBack = { showSuggestions = false },
                            onLinkStrong = { linkStrong(it) },
                            onLink = { linkSuggestion(it.note, it.suggestion, SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION) },
                            onSkip = { skip(it) },
                            onOpenNote = {
                                selectedId = it.uniqueId
                                readerOpen = true
                            },
                            onStop = {
                                allJob?.cancel()
                                showSuggestions = false
                            },
                            onOpenCalendarAccounts = onOpenCalendarAccounts,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    else -> {
                        BooxNotesHeader(
                            showAll = showAll,
                            onShowAllChange = { showAll = it },
                            isScanning = isScanning,
                            currentDate = currentDate,
                            datesWithNotes = datesWithNotes,
                            noteCountByDate = noteCountByDate,
                            onDateChange = {
                                currentDate = it
                                dateNotice = null
                            },
                            onClose = onDismiss
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when {
                                !loaded -> CenteredText("Loading notes…")
                                allNotes.isEmpty() -> CenteredText(
                                    "No Boox Notes yet. Notes you write in the Boox Notes app show up here on the days you wrote them."
                                )
                                else -> Row(modifier = Modifier.fillMaxSize()) {
                                    val listModifier = Modifier
                                        .weight(theme.listPaneFraction)
                                        .fillMaxHeight()
                                    if (showAll) {
                                        val linkedTotal = allNotes.count { isLinked(it.uniqueId) }
                                        val filtering = allFiltered.size != allNotes.size
                                        BooxNotesAllList(
                                            notes = allFiltered,
                                            caption = if (filtering) "${allFiltered.size} of ${plural(allNotes.size, "note")}"
                                                else "${plural(allNotes.size, "note")} · $linkedTotal linked",
                                            selectedId = selectedId,
                                            rowState = ::rowState,
                                            query = query,
                                            onQueryChange = { query = it },
                                            searchesHandwriting = ocrRepository != null,
                                            unlinkedOnly = unlinkedOnly,
                                            onUnlinkedOnlyChange = { unlinkedOnly = it },
                                            semantics = semantics,
                                            selectedDomain = selectedDomain,
                                            onDomainChange = { selectedDomain = it },
                                            selectedIntent = selectedIntent,
                                            onIntentChange = { selectedIntent = it },
                                            suggestionsStatus = when {
                                                allProgress != null -> AllSuggestionsStatus.Finding(allProgress ?: 0f)
                                                cache.allScored -> AllSuggestionsStatus.Ready(
                                                    allNotes.count { !isLinked(it.uniqueId) && cache.best(it.uniqueId) != null }
                                                )
                                                else -> AllSuggestionsStatus.Idle
                                            },
                                            onSelect = { selectedId = it.uniqueId },
                                            onReviewSuggestions = {
                                                suggestionsForAll = true
                                                showSuggestions = true
                                            },
                                            modifier = listModifier
                                        )
                                    } else {
                                        val linkedCount = dayNotes.count { isLinked(it.uniqueId) }
                                        BooxNotesDayList(
                                            notes = dayNotes,
                                            selectedId = selectedId,
                                            rowState = ::rowState,
                                            caption = when {
                                                linkedCount == 0 && dayNotes.size == 1 -> "1 note · not linked"
                                                linkedCount == 0 -> "${plural(dayNotes.size, "note")} · none linked"
                                                else -> "${plural(dayNotes.size, "note")} · $linkedCount linked"
                                            },
                                            notice = dateNotice,
                                            suggestionCount = dayNotes.count { !isLinked(it.uniqueId) && cache.best(it.uniqueId) != null },
                                            onSelect = { selectedId = it.uniqueId },
                                            onReviewSuggestions = {
                                                suggestionsForAll = false
                                                showSuggestions = true
                                            },
                                            modifier = listModifier
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(theme.paneGap))
                                    BooxNotePane(
                                        note = selectedNote,
                                        link = linkUi,
                                        page = page,
                                        pageIndex = pageIndex,
                                        pageCount = pageCount,
                                        onPageChange = { pageIndex = it },
                                        onLinkSuggestion = { suggestion ->
                                            selectedNote?.let { linkSuggestion(it, suggestion, SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION) }
                                        },
                                        onChooseEvent = { pickerOpen = true },
                                        onUnlink = { selectedNote?.let { unlink(it) } },
                                        onOpenReader = { readerOpen = true },
                                        onOpenInBooxNotes = { selectedNote?.let { openInBooxNotes(it) } },
                                        modifier = Modifier
                                            .weight(1f - theme.listPaneFraction)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }

                message?.let { current ->
                    BooxNotesMessageBar(
                        message = current,
                        onUndo = {
                            val undo = current.undo ?: return@BooxNotesMessageBar
                            if (busy) return@BooxNotesMessageBar
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
                    )
                }
            }
        }

        val pickerNote = selectedNote
        if (pickerOpen && pickerNote != null) {
            BooxNoteEventPicker(
                note = pickerNote,
                linkedEventId = links[pickerNote.uniqueId]?.linkedEventId,
                suggestedEventId = cache.best(pickerNote.uniqueId)?.event?.id,
                loadEventsInRange = { from, to ->
                    withContext(Dispatchers.IO) { smartLinkEventRepository.getEventsInRange(from, to) }
                },
                onPick = { event ->
                    pickerOpen = false
                    linkToEvent(pickerNote, event)
                },
                onDismiss = { pickerOpen = false }
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(EInkTheme.Global.paddingLarge)
        )
    }
}

/** Title, Day | All notes, the day (Day only) and close */
@Composable
private fun BooxNotesHeader(
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    isScanning: Boolean,
    currentDate: String,
    datesWithNotes: List<String>,
    noteCountByDate: Map<String, Int>,
    onDateChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.BooxNotes.headerSpacing)
    ) {
        Text(
            text = "Boox Notes",
            fontSize = EInkTheme.BooxNotes.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 1
        )
        BooxNotesSegmented(
            options = listOf("Day", "All notes"),
            selectedIndex = if (showAll) 1 else 0,
            onSelect = { onShowAllChange(it == 1) }
        )
        if (isScanning) {
            Text(
                text = "Checking for new notes…",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!showAll && datesWithNotes.isNotEmpty()) {
            BooxNotesDateControl(
                currentDate = currentDate,
                datesWithNotes = datesWithNotes,
                noteCountByDate = noteCountByDate,
                onDateChange = onDateChange
            )
        }
        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .noRippleClickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(28.dp)
            )
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
                    maxLines = 1
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
                LazyColumn(
                    modifier = Modifier
                        .width(EInkTheme.PopupPanel.width)
                        .heightIn(max = EInkTheme.PopupPanel.maxHeight)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
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
