package com.boox.day.ui.dialogs

import android.graphics.Bitmap
import android.util.Log
import com.boox.day.ui.messages.AiFeedbackMessages
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.booxnotes.BooxNative
import com.boox.day.booxnotes.BooxNotePreviewCoordinator
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.BooxNotePreviewResult
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.LinkConfidence
import com.boox.day.booxnotes.MatchReason
import com.boox.day.booxnotes.ScoredMatch
import com.boox.day.booxnotes.SmartLinkDataQualityFlag
import com.boox.day.booxnotes.SmartLinkDecisionMode
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteLink
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.db.UnifiedOcrSearchResult
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.SmartLinkEventRepository
import com.boox.day.data.db.SmartLinkPreflightState
import com.boox.day.data.db.SmartLinkPreflightStatus
import com.boox.day.data.db.SmartLinkSourceVisibility
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.model.IntentEntry
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ui.components.CompactSearchFilterRow
import com.boox.day.ui.components.ExpandedReadModeContent
import com.boox.day.ui.components.OcrSearchField
import com.boox.day.ui.components.OcrSearchResultsList
import com.boox.day.ui.components.SemanticFilteredNotesList
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.state.CalendarSourceFilter
import kotlinx.serialization.json.Json
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Data class for date with note count (for date dropdown)
 */
data class DateWithNoteCount(
    val date: String,
    val noteCount: Int
)

/**
 * Smart link suggestion - matches an unlinked note to a nearby event.
 */
data class LinkSuggestion(
    val note: BooxNoteIndex,
    val suggestedEvent: CalendarEvent,
    val timeDiffMinutes: Long,
    val confidence: String  // "high", "medium", "low"
)

private enum class NoteFilter {
    ALL,
    UNLINKED,
    LINKED,
    NEEDS_REVIEW
}

private data class NoteStatusSummary(
    val linkedEventId: String?,
    val linkedEventTitle: String? = null,
    val confidence: LinkConfidence,
    val confidenceScore: Int,
    val needsAttention: Boolean,
    val hasSuggestedEvents: Boolean
)

private fun parseLinkConfidence(value: String?): LinkConfidence =
    runCatching { LinkConfidence.valueOf(value ?: LinkConfidence.NONE.name) }
        .getOrDefault(LinkConfidence.NONE)

private fun BooxNoteLink.toStatusSummary(): NoteStatusSummary = NoteStatusSummary(
    linkedEventId = linkedEventId,
    confidence = parseLinkConfidence(confidence),
    confidenceScore = confidenceScore,
    needsAttention = needsAttention,
    hasSuggestedEvents = !suggestedEventIds.isNullOrBlank()
)

private fun com.boox.day.data.db.BooxNoteLinkWithTitle.toStatusSummary(): NoteStatusSummary = NoteStatusSummary(
    linkedEventId = linkedEventId,
    confidence = parseLinkConfidence(confidence),
    confidenceScore = confidenceScore,
    needsAttention = needsAttention,
    hasSuggestedEvents = !suggestedEventIds.isNullOrBlank()
)

private fun NoteStatusSummary?.isLinked(): Boolean = this?.linkedEventId != null

private fun NoteStatusSummary?.hasStrongSuggestion(): Boolean =
    this != null &&
        linkedEventId == null &&
        hasSuggestedEvents &&
        (confidence == LinkConfidence.HIGH || confidence == LinkConfidence.MEDIUM)

private fun NoteStatusSummary?.needsReview(): Boolean =
    this != null && needsAttention && !isLinked()

private fun matchesNoteFilter(
    note: BooxNoteIndex,
    statusMap: Map<String, NoteStatusSummary>,
    noteFilter: NoteFilter
): Boolean {
    val status = statusMap[note.uniqueId]
    return when (noteFilter) {
        NoteFilter.ALL -> true
        NoteFilter.UNLINKED -> !status.isLinked()
        NoteFilter.LINKED -> status.isLinked()
        NoteFilter.NEEDS_REVIEW -> status.needsReview() || status.hasStrongSuggestion()
    }
}

private fun parseStoredMatchReasons(json: String?): List<MatchReason> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                runCatching { MatchReason.valueOf(value) }.getOrNull()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}

private fun parseStoredDataQualityFlags(json: String?): List<SmartLinkDataQualityFlag> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                runCatching { SmartLinkDataQualityFlag.valueOf(value) }.getOrNull()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}

private fun formatMatchReasonShort(reason: MatchReason): String = when (reason) {
    MatchReason.TITLE_MATCH -> "Title match"
    MatchReason.SEMANTIC_GROUP_MATCH -> "Shared topic"
    MatchReason.SEMANTIC_CONTEXT_MATCH -> "Semantic context"
    MatchReason.AI_SUGGESTED_MATCH -> "AI review"
    MatchReason.DURING_EVENT -> "During event"
    MatchReason.SHORTLY_AFTER_EVENT -> "Shortly after"
    MatchReason.SHORTLY_BEFORE_EVENT -> "Shortly before"
    MatchReason.EVENING_EVENT -> "Evening event"
    MatchReason.ONLY_EVENT_OF_DAY -> "Only event that day"
    MatchReason.LONG_EVENT -> "Long event"
    MatchReason.WEEKDAY_PATTERN -> "Recurring weekday"
    MatchReason.SPARSE_CONTEXT -> "Few nearby events"
    MatchReason.MORNING_FOLLOWUP -> "Morning follow-up"
    MatchReason.TEST_CONTENT_PENALTY -> "Weak test content"
}

private fun formatDecisionModeShort(decisionMode: String?): String? {
    val mode = runCatching { SmartLinkDecisionMode.valueOf(decisionMode ?: return null) }.getOrNull() ?: return null
    return when (mode) {
        SmartLinkDecisionMode.MANUAL_LINK -> "Linked manually"
        SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION -> "Accepted Smart Link suggestion"
        SmartLinkDecisionMode.SAME_DAY_AUTO_HIGH -> "Strong Smart Link match"
        SmartLinkDecisionMode.SAME_DAY_AUTO_MEDIUM -> "Auto-linked and worth review"
        SmartLinkDecisionMode.SAME_DAY_SUGGESTION -> "Smart Link suggestion"
        SmartLinkDecisionMode.DAY_ONLY_FALLBACK -> "No strong event match"
        SmartLinkDecisionMode.CROSS_DAY_BULK_APPLY -> "Cross-day Smart Link match"
        SmartLinkDecisionMode.AI_APPLY -> "AI-reviewed match"
        SmartLinkDecisionMode.LEGACY -> null
    }
}

private fun formatDataQualityMessage(flags: List<SmartLinkDataQualityFlag>): String? = when {
    SmartLinkDataQualityFlag.LINKED_EVENT_DELETED in flags -> "The previous event is no longer available."
    SmartLinkDataQualityFlag.SEMANTIC_RUNTIME_DISABLED in flags -> "Smart Link is running without semantic context right now."
    SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS in flags && SmartLinkDataQualityFlag.NO_EVENT_SEMANTICS in flags ->
        "Semantic context is missing for both the note and the event."
    SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS in flags -> "Note semantic context is missing."
    SmartLinkDataQualityFlag.NO_EVENT_SEMANTICS in flags -> "Event semantic context is missing."
    SmartLinkDataQualityFlag.AI_GENERATED in flags -> "This match came from AI review."
    else -> null
}

private fun buildLegacySameDaySuggestions(
    notes: List<BooxNoteIndex>,
    events: List<CalendarEvent>,
    noteToEventMap: Map<String, CalendarEvent>,
    actedSuggestions: Set<String>
): List<LinkSuggestion> {
    val unlinkedNotes = notes.filter { !noteToEventMap.containsKey(it.uniqueId) }
    val timedEvents = events.filter { !it.isAllDay }

    return unlinkedNotes.mapNotNull { note ->
        if (actedSuggestions.contains(note.uniqueId)) return@mapNotNull null
        if (timedEvents.isEmpty()) return@mapNotNull null

        val noteTime = note.originalTimestamp
        val closestEvent = timedEvents.minByOrNull { event ->
            kotlin.math.abs(event.startTime - noteTime)
        } ?: return@mapNotNull null

        val diffMs = kotlin.math.abs(closestEvent.startTime - noteTime)
        val diffMinutes = diffMs / 60000

        if (diffMinutes > 120) return@mapNotNull null

        val confidence = when {
            diffMinutes <= 15 -> "high"
            diffMinutes <= 45 -> "medium"
            else -> "low"
        }

        LinkSuggestion(note, closestEvent, diffMinutes, confidence)
    }.sortedBy { it.timeDiffMinutes }
}

private fun buildSharedSameDaySuggestion(
    note: BooxNoteIndex,
    eventById: Map<String, CalendarEvent>,
    scoredMatch: ScoredMatch
): LinkSuggestion? {
    val event = eventById[scoredMatch.eventId] ?: return null
    val diffMinutes = kotlin.math.abs(event.startTime - note.originalTimestamp) / 60000
    val confidence = when (scoredMatch.confidence) {
        LinkConfidence.HIGH -> "high"
        LinkConfidence.MEDIUM -> "medium"
        LinkConfidence.LOW, LinkConfidence.NONE -> "low"
    }
    return LinkSuggestion(note, event, diffMinutes, confidence)
}

/**
 * Dialog for viewing and managing Boox Notes for a specific date.
 * Features:
 * - Master-detail layout (notes list + thumbnail preview)
 * - Page navigation for multi-page notes
 * - Event linking dropdown with feedback message
 * - Date dropdown for quick navigation between dates with notes
 * - Timeline mini-view showing events and notes chronologically
 * - Statistics header with note/page counts
 */
@Composable
fun BooxNotesDialog(
    date: String,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    booxNoteIndexer: BooxNoteIndexer?,  // Optional: if provided, performs smart scan on open
    ocrRepository: UnifiedOcrRepository?,  // Optional: if provided, enables OCR search
    isOcrIndexingInProgress: Boolean = false,
    eventRepository: CalendarEventRepository,
    onDismiss: () -> Unit,
    onLinkChanged: () -> Unit = {},  // Callback when a link is changed (to refresh events grid)
    autoOpenBulkWizard: Boolean = false,  // If true, opens the Bulk Smart Link wizard immediately
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smartLinkEventRepository = remember(context, eventRepository) {
        SmartLinkEventRepository(context, eventRepository)
    }

    // View mode: "date" (notes for current date) or "recent" (recent notes across all dates)
    // Auto-switch to "recent" if autoOpenBulkWizard is true (bulk wizard is only in recent view)
    var viewMode by remember { mutableStateOf(if (autoOpenBulkWizard) "recent" else "date") }

    // Current viewing date (can change via dropdown)
    var currentDate by remember { mutableStateOf(date) }

    // Notes for current date
    var notes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Recent notes (across all dates, sorted by indexedAt)
    var recentNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var isLoadingRecent by remember { mutableStateOf(false) }

    // Track which notes need attention (keyed by uniqueId)
    var notesNeedingAttention by remember { mutableStateOf<Set<String>>(emptySet()) }
    var noteFilter by remember { mutableStateOf(NoteFilter.ALL) }
    var currentDateNoteStatusMap by remember { mutableStateOf<Map<String, NoteStatusSummary>>(emptyMap()) }
    var recentNoteStatusMap by remember { mutableStateOf<Map<String, NoteStatusSummary>>(emptyMap()) }

    // All dates with notes (for dropdown)
    var datesWithNotes by remember { mutableStateOf<List<DateWithNoteCount>>(emptyList()) }

    // Selected note state
    var selectedNote by remember { mutableStateOf<BooxNoteIndex?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }

    // Link state for selected note
    var noteLink by remember { mutableStateOf<BooxNoteLink?>(null) }

    // Linked event details (supports cross-day links - fetched when noteLink changes)
    var linkedEventDetails by remember { mutableStateOf<CalendarEvent?>(null) }

    // Events for current date (for linking dropdown)
    var eventsForDate by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }

    // Map of noteUniqueId -> linked CalendarEvent (for contextual timestamps in list)
    var noteToEventMap by remember { mutableStateOf<Map<String, CalendarEvent>>(emptyMap()) }

    // Thumbnail state
    var thumbnailBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var notePreview by remember { mutableStateOf<BooxNotePreviewResult?>(null) }
    var isLoadingThumbnail by remember { mutableStateOf(false) }
    val previewCoordinator = remember { BooxNotePreviewCoordinator() }

    // Linking feedback message
    var linkFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Smart scan state - trigger scan if data is stale (>60s since last scan)
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }

    // OCR Search state (only available when ocrRepository is provided)
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UnifiedOcrSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val ocrSearchEnabled = ocrRepository != null

    // Semantic Filter state (e-ink friendly domain/intent filtering)
    // Jan 2026: Changed from multi-select (Set) to single-select (nullable)
    var selectedDomain by remember { mutableStateOf<Domain?>(null) }
    var selectedIntent by remember { mutableStateOf<Intent?>(null) }
    var isExpandedReadMode by remember { mutableStateOf(false) }  // Full-screen reading mode
    var filteredNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var noteDomains by remember { mutableStateOf<Map<String, Domain?>>(emptyMap()) }
    var noteSecondaryDomains by remember { mutableStateOf<Map<String, Domain?>>(emptyMap()) }  // A2: Mixed category
    var noteIntents by remember { mutableStateOf<Map<String, List<Intent>>>(emptyMap()) }
    var isLoadingFilters by remember { mutableStateOf(false) }
    var allSemanticNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var profilesLoaded by remember { mutableStateOf(false) }  // B9: Cache profiles across tab switches

    suspend fun validateNoteIdsOnDevice(noteIds: Collection<String>): Set<String> {
        val distinctIds = noteIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (distinctIds.isEmpty()) return emptySet()
        return booxNoteIndexer?.quickValidation(distinctIds)?.toSet() ?: distinctIds.toSet()
    }

    suspend fun refreshCurrentDateLinkState(currentEvents: List<CalendarEvent> = eventsForDate) {
        val links = booxNoteRepository.getLinksWithTitlesForDate(currentDate)
        currentDateNoteStatusMap = links.associate { it.noteUniqueId to it.toStatusSummary() }
        notesNeedingAttention = links
            .filter { it.needsAttention }
            .map { it.noteUniqueId }
            .toSet()
        noteToEventMap = buildMap {
            links.forEach { link ->
                link.linkedEventId?.let { eventId ->
                    currentEvents.find { it.id == eventId }?.let { event ->
                        put(link.noteUniqueId, event)
                    }
                }
            }
        }
    }

    suspend fun refreshRecentNoteStatus(currentRecentNotes: List<BooxNoteIndex> = recentNotes) {
        val recentNoteIds = currentRecentNotes.map { it.uniqueId }.toSet()
        if (recentNoteIds.isEmpty()) {
            recentNoteStatusMap = emptyMap()
            return
        }

        recentNoteStatusMap = booxNoteRepository.getAllLinks()
            .asSequence()
            .filter { it.noteUniqueId in recentNoteIds }
            .associate { it.noteUniqueId to it.toStatusSummary() }
    }

    suspend fun refreshNotesAfterValidationFailure(noteId: String) {
        data class ValidationRefreshSnapshot(
            val allNotes: List<BooxNoteIndex>,
            val currentDateNotes: List<BooxNoteIndex>,
            val currentDateEvents: List<CalendarEvent>,
            val currentDateLinks: List<com.boox.day.data.db.BooxNoteLinkWithTitle>,
            val allLinks: List<BooxNoteLink>
        )

        isScanning = true
        val snapshot = withContext(Dispatchers.IO) {
            booxNoteIndexer?.fullScan()
            booxNoteRepository.invalidateLinksCache()
            ValidationRefreshSnapshot(
                allNotes = booxNoteRepository.getAllNotes(),
                currentDateNotes = booxNoteRepository.getNotesForDate(currentDate)
                    .sortedByDescending { it.originalTimestamp },
                currentDateEvents = smartLinkEventRepository.getEventsForDateSync(currentDate),
                currentDateLinks = booxNoteRepository.getLinksWithTitlesForDate(currentDate),
                allLinks = booxNoteRepository.getAllLinks()
            )
        }
        isScanning = false
        scanCompleted = true

        val validNoteIds = snapshot.allNotes.map { it.uniqueId }.toSet()
        val datesList = snapshot.allNotes
            .groupBy { it.originalDate }
            .map { (dateStr, notesList) -> DateWithNoteCount(dateStr, notesList.size) }
            .filter { it.noteCount > 0 }
            .sortedByDescending { it.date }

        datesWithNotes = datesList
        notes = snapshot.currentDateNotes
        recentNotes = snapshot.allNotes
            .sortedByDescending { it.indexedAt }
            .take(200)
        recentNoteStatusMap = snapshot.allLinks
            .asSequence()
            .filter { link -> snapshot.allNotes.any { it.uniqueId == link.noteUniqueId } }
            .associate { it.noteUniqueId to it.toStatusSummary() }
        filteredNotes = filteredNotes.filter { it.uniqueId in validNoteIds }
        allSemanticNotes = allSemanticNotes.filter { it.uniqueId in validNoteIds }
        notesNeedingAttention = snapshot.currentDateLinks
            .filter { it.needsAttention }
            .map { it.noteUniqueId }
            .toSet()
        currentDateNoteStatusMap = snapshot.currentDateLinks
            .associate { it.noteUniqueId to it.toStatusSummary() }
        noteDomains = noteDomains.filterKeys { it in validNoteIds }
        noteSecondaryDomains = noteSecondaryDomains.filterKeys { it in validNoteIds }
        noteIntents = noteIntents.filterKeys { it in validNoteIds }
        eventsForDate = snapshot.currentDateEvents
        noteToEventMap = buildMap {
            snapshot.currentDateLinks.forEach { link ->
                link.linkedEventId?.let { eventId ->
                    snapshot.currentDateEvents.find { it.id == eventId }?.let { event ->
                        put(link.noteUniqueId, event)
                    }
                }
            }
        }
        linkFeedbackMessage = null

        if (datesList.none { it.date == currentDate } && datesList.isNotEmpty()) {
            currentDate = datesList.first().date
        }

        val refreshedSelectedNote = snapshot.allNotes.firstOrNull { it.uniqueId == noteId }
        selectedNote = when {
            refreshedSelectedNote != null -> refreshedSelectedNote
            viewMode == "recent" -> recentNotes.firstOrNull()
            viewMode == "search" -> filteredNotes.firstOrNull()
            else -> notes.firstOrNull()
        }
        currentPageIndex = 0
        noteLink = selectedNote?.let { booxNoteRepository.getLinkForNote(it.uniqueId) }
        linkedEventDetails = noteLink?.linkedEventId?.let { eventId ->
            eventsForDate.find { it.id == eventId } ?: smartLinkEventRepository.getEventById(eventId)
        }
    }

    fun openInBooxNotesIfValid(note: BooxNoteIndex) {
        scope.launch {
            val validIds = withContext(Dispatchers.IO) {
                validateNoteIdsOnDevice(listOf(note.uniqueId))
            }
            if (note.uniqueId in validIds) {
                booxNotesIntegration.openNote(note.uniqueId)
            } else {
                refreshNotesAfterValidationFailure(note.uniqueId)
            }
        }
    }

    // Smart scan on dialog open - only runs once, refreshes data if stale
    LaunchedEffect(Unit) {
        if (booxNoteIndexer != null) {
            isScanning = true
            withContext(Dispatchers.IO) {
                val result = booxNoteIndexer.scanIfStale()
                if (result != null) {
                    // Scan was performed - invalidate cache
                    booxNoteRepository.invalidateLinksCache()
                }
            }
            isScanning = false
            scanCompleted = true
        } else {
            scanCompleted = true  // No indexer, skip scan
        }
    }

    // Load all dates with notes on initial load (waits for scan to complete)
    // Also auto-select last day with notes if current date has no notes
    LaunchedEffect(scanCompleted) {
        if (!scanCompleted) return@LaunchedEffect  // Wait for scan to finish
        val allNotes = booxNoteRepository.getAllNotes()
        val datesList = allNotes
            .groupBy { it.originalDate }
            .map { (dateStr, notesList) -> DateWithNoteCount(dateStr, notesList.size) }
            .filter { it.noteCount > 0 }  // Exclude dates that have no valid notes after filtering
            .sortedByDescending { it.date }
        datesWithNotes = datesList

        // If current date has no notes, auto-select the most recent date with notes
        val currentDateHasNotes = datesList.any { it.date == currentDate }
        if (!currentDateHasNotes && datesList.isNotEmpty()) {
            currentDate = datesList.first().date  // Most recent date with notes
        }
    }

    // Load recent notes when view mode is "recent"
    LaunchedEffect(viewMode) {
        if (viewMode == "recent") {
            isLoadingRecent = true
            recentNotes = booxNoteRepository.getAllNotes()
                .sortedByDescending { it.indexedAt }  // Most recently indexed/updated first
                .take(200)  // Limit to 200 notes
            refreshRecentNoteStatus(recentNotes)
            isLoadingRecent = false

            // Auto-select first recent note if nothing selected
            if (selectedNote == null && recentNotes.isNotEmpty()) {
                selectedNote = recentNotes.first()
            }
        }
    }

    // Load notes and events when current date changes
    LaunchedEffect(currentDate) {
        isLoading = true
        selectedNote = null
        // Sort by timestamp (newest first)
        notes = booxNoteRepository.getNotesForDate(currentDate)
            .sortedByDescending { it.originalTimestamp }
        eventsForDate = smartLinkEventRepository.getEventsForDateSync(currentDate)
        refreshCurrentDateLinkState(eventsForDate)

        isLoading = false

        // Auto-select first note if available
        if (notes.isNotEmpty()) {
            selectedNote = notes.first()
        }
    }

    LaunchedEffect(viewMode, noteFilter, notes, recentNotes, currentDateNoteStatusMap, recentNoteStatusMap) {
        if (viewMode == "search") return@LaunchedEffect

        val baseNotes = if (viewMode == "recent") recentNotes else notes
        val statusMap = if (viewMode == "recent") recentNoteStatusMap else currentDateNoteStatusMap
        val filteredDisplayNotes = baseNotes.filter { matchesNoteFilter(it, statusMap, noteFilter) }

        selectedNote = when {
            filteredDisplayNotes.isEmpty() -> null
            selectedNote == null -> filteredDisplayNotes.first()
            filteredDisplayNotes.any { it.uniqueId == selectedNote?.uniqueId } -> selectedNote
            else -> filteredDisplayNotes.first()
        }
    }

    // Load link when selected note changes
    LaunchedEffect(selectedNote) {
        selectedNote?.let { note ->
            noteLink = booxNoteRepository.getLinkForNote(note.uniqueId)
            currentPageIndex = 0  // Reset page index
        }
    }

    // Load events for selected note's date when in search mode
    // This ensures quick link dropdown has events to show for notes from any date
    LaunchedEffect(selectedNote, viewMode) {
        if (viewMode == "search" && selectedNote != null) {
            val noteDate = selectedNote!!.originalDate
            // Load events for the note's date if different from current
            if (noteDate.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    eventsForDate = smartLinkEventRepository.getEventsForDateSync(noteDate)
                }
            }
        }
    }

    // Fetch linked event details when noteLink changes (supports cross-day links)
    // This effect triggers when: 1) different note selected, 2) link changed via dropdown
    LaunchedEffect(noteLink?.linkedEventId, eventsForDate) {
        linkedEventDetails = noteLink?.linkedEventId?.let { eventId ->
            // Fast path: check same-day events first
            eventsForDate.find { it.id == eventId }
                // Slow path: fetch from DB (cross-day case)
                ?: smartLinkEventRepository.getEventById(eventId)
        }
    }

    // Load thumbnail when selected note or page changes
    LaunchedEffect(selectedNote, currentPageIndex) {
        val note = selectedNote
        if (note == null) {
            notePreview = null
            thumbnailBitmap = null
            isLoadingThumbnail = false
            return@LaunchedEffect
        }

        isLoadingThumbnail = true
        thumbnailBitmap = null
        notePreview = null

        try {
            val preview = previewCoordinator.loadPreview(note, currentPageIndex)
            if (selectedNote?.uniqueId != note.uniqueId) {
                return@LaunchedEffect
            }

            notePreview = preview
            thumbnailBitmap = (preview as? BooxNotePreviewResult.ImagePreview)
                ?.bitmap
                ?.asImageBitmap()

            if (preview.totalPages > 0 && preview.currentPageIndex != currentPageIndex) {
                currentPageIndex = preview.currentPageIndex
            }

            when (preview) {
                is BooxNotePreviewResult.TextPreview -> {
                    Log.i(
                        "BooxNotesDialog",
                        "Showing text preview for ${note.uniqueId}: ${preview.diagnostics?.message}"
                    )
                }
                is BooxNotePreviewResult.Unavailable -> {
                    Log.w(
                        "BooxNotesDialog",
                        "Preview unavailable for ${note.uniqueId}: ${preview.diagnostics.reasonCode} | ${preview.diagnostics.debugSummary}"
                    )
                }
                else -> Unit
            }
        } catch (e: Throwable) {
            notePreview = BooxNotePreviewResult.Unavailable(
                totalPages = 0,
                currentPageIndex = 0,
                pageIds = emptyList(),
                diagnostics = com.boox.day.booxnotes.BooxPreviewDiagnostics(
                    reasonCode = "PREVIEW_EXCEPTION",
                    message = "Preview loading failed before content could be shown.",
                    debugSummary = e.message
                )
            )
            Log.w("BooxNotesDialog", "Preview load failed for ${note.uniqueId}: ${e.message}", e)
        } finally {
            isLoadingThumbnail = false
        }
    }

    // OCR Search - debounced search when query changes
    LaunchedEffect(searchQuery) {
        if (ocrRepository != null && searchQuery.length >= 2) {
            isSearching = true
            // Small debounce delay for typing
            kotlinx.coroutines.delay(300)
            withContext(Dispatchers.IO) {
                val results = ocrRepository.searchText(searchQuery, limit = 50)
                withContext(Dispatchers.Main) {
                    searchResults = results
                }
            }
            isSearching = false
        } else if (searchQuery.isEmpty()) {
            searchResults = emptyList()
        }
    }

    // Semantic Filter - Load semantic profiles ONCE when dialog opens (B9: cached across tab switches)
    LaunchedEffect(Unit) {
        if (!profilesLoaded && ocrRepository != null) {
            isLoadingFilters = true
            withContext(Dispatchers.IO) {
                try {
                    // Load all Boox Notes
                    val allNotes = booxNoteRepository.getAllNotes()

                    // Load OCR data for BOOX_NOTE source type
                    val ocrTexts = ocrRepository.getOcrTextsBySourceType(OcrSource.BOOX_NOTE)

                    // Build lookup maps: noteId -> domain/intents
                    // Weight each page's vote by its confidence score (A1: Confidence-Weighted Voting)
                    val domainWeights = mutableMapOf<String, MutableMap<Domain, Float>>()
                    val domainConfidences = mutableMapOf<String, MutableMap<Domain, MutableList<Float>>>()
                    // A11: Track confidence per intent (not just list of intents)
                    val intentConfidencesByNote = mutableMapOf<String, MutableMap<Intent, Float>>()

                    val jsonParser = Json { ignoreUnknownKeys = true }

                    for (ocrText in ocrTexts) {
                        val noteId = ocrText.sourceId

                        // Collect weighted domain votes (A1)
                        ocrText.domain?.let { domainStr ->
                            try {
                                val domain = Domain.valueOf(domainStr)
                                val confidence = ocrText.domainConfidence.coerceIn(0.1f, 1.0f)

                                // Accumulate weighted votes
                                domainWeights.getOrPut(noteId) { mutableMapOf() }
                                    .merge(domain, confidence) { a, b -> a + b }

                                // Track individual confidences for "Mixed" detection (A2)
                                domainConfidences.getOrPut(noteId) { mutableMapOf() }
                                    .getOrPut(domain) { mutableListOf() }
                                    .add(confidence)
                            } catch (e: Exception) { /* ignore invalid */ }
                        }

                        // Collect intents with confidence (A11)
                        ocrText.intentsJson?.let { json ->
                            try {
                                val intents = jsonParser.decodeFromString<List<IntentEntry>>(json)
                                intents.filter { it.confidence >= 0.3f }.forEach { entry ->
                                    val currentMap = intentConfidencesByNote.getOrPut(noteId) { mutableMapOf() }
                                    // Keep highest confidence for each intent
                                    val existing = currentMap[entry.intent] ?: 0f
                                    if (entry.confidence > existing) {
                                        currentMap[entry.intent] = entry.confidence
                                    }
                                }
                            } catch (e: Exception) { /* ignore parse errors */ }
                        }
                    }

                    // Build final maps - use weighted score, not vote count (A1)
                    // A2: Detect "Mixed" category when top two domains are close
                    val domainMap = mutableMapOf<String, Domain?>()
                    val secondaryDomainMap = mutableMapOf<String, Domain?>()

                    for ((noteId, weights) in domainWeights) {
                        val sorted = weights.entries.sortedByDescending { it.value }
                        if (sorted.isEmpty()) {
                            domainMap[noteId] = null
                        } else {
                            val top = sorted[0]
                            domainMap[noteId] = top.key

                            // A2: Check for Mixed category
                            if (sorted.size >= 2) {
                                val second = sorted[1]
                                val gap = top.value - second.value
                                val threshold = 0.15f * top.value  // Within 15%

                                // Mixed if gap is small AND second is meaningful (> 0.3)
                                if (gap <= threshold && second.value > 0.3f) {
                                    secondaryDomainMap[noteId] = second.key
                                }
                            }
                        }
                    }

                    // A11: Sort by confidence and take top 2
                    val intentMap = intentConfidencesByNote.mapValues { (_, intentConfMap) ->
                        intentConfMap.entries
                            .sortedByDescending { it.value }
                            .take(2)
                            .map { it.key }
                    }

                    withContext(Dispatchers.Main) {
                        allSemanticNotes = allNotes
                        noteDomains = domainMap
                        noteSecondaryDomains = secondaryDomainMap
                        noteIntents = intentMap
                        // Initially show all notes (no filters)
                        filteredNotes = allNotes
                        profilesLoaded = true  // B9: Mark as loaded
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BooxNotesDialog", "Failed to load semantic profiles", e)
                }
            }
            isLoadingFilters = false
        }
    }

    // Semantic Filter - Filter notes when selection or search changes
    // Jan 2026: Updated for single-select (Domain? and Intent?)
    LaunchedEffect(selectedDomain, selectedIntent, allSemanticNotes, searchQuery, searchResults) {
        if (viewMode == "search") {
            // Create set of matching note IDs from search results
            val matchingNoteIds = searchResults.map { it.sourceId }.toSet()

            filteredNotes = if (selectedDomain == null && selectedIntent == null && searchQuery.isEmpty()) {
                // No filters and no search - show all notes
                allSemanticNotes
            } else {
                allSemanticNotes.filter { note ->
                    // Text match (if query exists)
                    val textMatch = searchQuery.isEmpty() ||
                            searchQuery.length < 2 ||
                            note.uniqueId in matchingNoteIds

                    val noteDomainValue = noteDomains[note.uniqueId]
                    val noteIntentList = noteIntents[note.uniqueId] ?: emptyList()

                    // Domain match (single select: null = all, or exact match)
                    val domainMatch = selectedDomain == null ||
                            noteDomainValue == selectedDomain

                    // Intent match (single select: null = all, or contains match)
                    val intentMatch = selectedIntent == null ||
                            noteIntentList.contains(selectedIntent)

                    // All must match (AND logic between text, domain and intent)
                    textMatch && domainMatch && intentMatch
                }
            }
        }
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(Float.MAX_VALUE),  // Ensure dialog is on top of all other layers
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .offset(y = EInkTheme.BooxNotes.dialogVerticalOffset)
                    .fillMaxWidth(EInkTheme.BooxNotes.dialogWidthFraction)
                    .fillMaxHeight(EInkTheme.BooxNotes.dialogHeightFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        Color.Black,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header with view mode tabs and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: View mode tabs - styled like actual tabs, width matches 40% list column
                    Row(
                        modifier = Modifier.fillMaxWidth(EInkTheme.BooxNotes.listPaneWeight),
                        verticalAlignment = Alignment.Bottom,  // Align to bottom to touch divider
                        horizontalArrangement = Arrangement.spacedBy((-1).dp)  // Overlap borders slightly
                    ) {
                        // Tab shape: rounded top corners only
                        val tabShape = RoundedCornerShape(topStart = EInkTheme.ModernDialog.cornerRadius, topEnd = EInkTheme.ModernDialog.cornerRadius, bottomStart = 0.dp, bottomEnd = 0.dp)

                        // "This Day" tab - E-Ink safe: unselected=white+border, selected=black+white
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    if (viewMode == "date") EInkTheme.ModernDialog.PrimaryButton.background else EInkTheme.Colors.background,
                                    tabShape
                                )
                                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.titleColor, tabShape)
                                .noRippleClickable { viewMode = "date" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This Day",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (viewMode == "date") EInkTheme.ModernDialog.PrimaryButton.textColor else EInkTheme.ModernDialog.titleColor
                            )
                        }
                        // "Recent" tab - E-Ink safe: unselected=white+border, selected=black+white
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    if (viewMode == "recent") EInkTheme.ModernDialog.PrimaryButton.background else EInkTheme.Colors.background,
                                    tabShape
                                )
                                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.titleColor, tabShape)
                                .noRippleClickable { viewMode = "recent" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View All",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (viewMode == "recent") EInkTheme.ModernDialog.PrimaryButton.textColor else EInkTheme.ModernDialog.titleColor
                            )
                        }
                        // "Search" tab (only shown if OCR is enabled) - E-Ink safe
                        if (ocrSearchEnabled) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(
                                        if (viewMode == "search") EInkTheme.ModernDialog.PrimaryButton.background else EInkTheme.Colors.background,
                                        tabShape
                                    )
                                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.titleColor, tabShape)
                                    .noRippleClickable { viewMode = "search" },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (viewMode == "search") EInkTheme.ModernDialog.PrimaryButton.textColor else EInkTheme.ModernDialog.titleColor
                                    )
                                    Text(
                                        text = "Search & Filter",  // B1: Renamed to clarify tab purpose
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewMode == "search") EInkTheme.ModernDialog.PrimaryButton.textColor else EInkTheme.ModernDialog.titleColor
                                    )
                                }
                            }
                        }
                    }

                    // Center: Context info based on mode
                    when (viewMode) {
                        "date" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(start = 38.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = formatDateForDisplay(currentDate),
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.ModernDialog.titleColor
                                )
                                Spacer(modifier = Modifier.width(40.dp))  // Gap before dropdown
                                // Date selector dropdown
                                DateSelectorDropdown(
                                    currentDate = currentDate,
                                    datesWithNotes = datesWithNotes,
                                    onDateSelected = { currentDate = it },
                                    modifier = Modifier.width(260.dp)  // Wide enough for "Select date with Notes"
                                )
                            }
                        }
                        "search" -> {
                            // Header for semantic filter mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(start = 38.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = "Search and filter your notes",  // B1: Updated context text
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.ModernDialog.titleColor
                                )
                            }
                        }
                        else -> {
                            // "recent" - Match same layout as "This Day" view
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(start = 38.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = "All scanned notes",
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.ModernDialog.titleColor
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.ModernDialog.titleColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Only show scanning status while actively scanning (not "Scan complete")
                if (booxNoteIndexer != null && isScanning) {
                    Text(
                        text = "Scanning Boox Notes...",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                Divider(color = EInkTheme.ModernDialog.borderColor, thickness = EInkTheme.ModernDialog.borderWidth)

                Spacer(modifier = Modifier.height(8.dp))

                // ═══════════════════════════════════════════════════════════════════
                // SEARCH MODE - Compact Filter Row (Jan 2026 refactoring)
                // Space savings: ~240dp -> 44dp (single row with search + dropdowns)
                // ═══════════════════════════════════════════════════════════════════
                if (viewMode == "search" && ocrSearchEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Calculate counts for each domain and intent
                        val domainCounts = remember(allSemanticNotes, noteDomains) {
                            Domain.entries.associateWith { domain ->
                                allSemanticNotes.count { note -> noteDomains[note.uniqueId] == domain }
                            }
                        }
                        val intentCounts = remember(allSemanticNotes, noteIntents) {
                            Intent.entries.associateWith { intent ->
                                allSemanticNotes.count { note ->
                                    noteIntents[note.uniqueId]?.contains(intent) == true
                                }
                            }
                        }

                        // Compact search + filter row (44dp total)
                        CompactSearchFilterRow(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            isSearching = isSearching,
                            resultCount = searchResults.size,
                            selectedDomain = selectedDomain,
                            onDomainSelected = { selectedDomain = it },
                            selectedIntent = selectedIntent,
                            onIntentSelected = { selectedIntent = it },
                            domainCounts = domainCounts,
                            intentCounts = intentCounts,
                            isExpanded = isExpandedReadMode,
                            expandEnabled = selectedNote != null && thumbnailBitmap != null,
                            onExpandToggle = { isExpandedReadMode = !isExpandedReadMode },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Divider(color = EInkTheme.ModernDialog.borderColor, thickness = 1.dp)

                        // Content pane: expanded mode or two-pane layout
                        if (isExpandedReadMode && selectedNote != null) {
                            // Full-screen reading mode
                            ExpandedReadModeContent(
                                noteTitle = selectedNote?.title ?: selectedNote?.uniqueId ?: "Note",
                                thumbnailBitmap = thumbnailBitmap,
                                isLoadingThumbnail = isLoadingThumbnail,
                                currentPageIndex = currentPageIndex,
                                totalPages = notePreview?.totalPages ?: selectedNote?.pageCount ?: 1,
                                onPageChange = { currentPageIndex = it },
                                onCollapse = { isExpandedReadMode = false },
                                onOpenInBooxNotes = {
                                    selectedNote?.let { note ->
                                        openInBooxNotesIfValid(note)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            )
                        } else {
                            // Normal two-pane layout (list + detail)
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            ) {
                                // Left: Filtered notes list
                                SemanticFilteredNotesList(
                                    notes = filteredNotes,
                                    onNoteClick = { note ->
                                        selectedNote = note
                                        currentPageIndex = 0
                                    },
                                    selectedNoteId = selectedNote?.uniqueId,
                                    noteDomains = noteDomains,
                                    noteSecondaryDomains = noteSecondaryDomains,
                                    noteIntents = noteIntents,
                                    modifier = Modifier
                                        .weight(EInkTheme.BooxNotes.listPaneWeight)
                                        .fillMaxHeight()
                                )

                                // Vertical divider between list and detail pane
                                Divider(
                                    color = EInkTheme.Colors.darkGray,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(EInkTheme.Divider.thickness)
                                )

                                // Right: Preview pane (reuse existing detail UI)
                                NoteDetailPane(
                                    note = selectedNote,
                                    previewResult = notePreview,
                                    thumbnailBitmap = thumbnailBitmap,
                                    isLoadingThumbnail = isLoadingThumbnail,
                                    currentPageIndex = currentPageIndex,
                                    onPageChange = { currentPageIndex = it },
                                    noteLink = noteLink,
                                    linkedEventDetails = linkedEventDetails,
                                    eventsForDate = eventsForDate,
                                    booxNoteIndexer = booxNoteIndexer,
                                    linkFeedbackMessage = linkFeedbackMessage,
                                    onLinkChanged = { eventId ->
                                        selectedNote?.let { note ->
                                            scope.launch {
                                                if (eventId != null) {
                                                    booxNoteRepository.linkNoteToEvent(note.uniqueId, eventId, isManual = true)
                                                } else {
                                                    booxNoteRepository.unlinkNote(note.uniqueId)
                                                }
                                                // Update noteLink so UI refreshes to show linked state
                                                noteLink = booxNoteRepository.getLinkForNote(note.uniqueId)

                                                // Generate feedback message
                                                val noteTitle = note.title.take(20) + if (note.title.length > 20) "..." else ""
                                                if (eventId != null) {
                                                    val event = eventsForDate.find { it.id == eventId }
                                                    val eventName = event?.title?.take(20)?.let { it + if (event.title.length > 20) "..." else "" } ?: "event"
                                                    linkFeedbackMessage = "Linked: $noteTitle to $eventName"
                                                } else {
                                                    linkFeedbackMessage = "Unlinked: $noteTitle"
                                                    linkedEventDetails = null
                                                }

                                                onLinkChanged()
                                            }
                                        }
                                    },
                                    onOpenInBooxNotes = {
                                        selectedNote?.let { note ->
                                            openInBooxNotesIfValid(note)
                                        }
                                    },
                                    onLoadEventsForDate = { dateStr ->
                                        smartLinkEventRepository.getEventsForDateSync(dateStr)
                                    },
                                    modifier = Modifier
                                        .weight(EInkTheme.BooxNotes.detailPaneWeightWider)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                } else {
                    // ═══════════════════════════════════════════════════════════════
                    // DATE / RECENT MODES (original logic)
                    // ═══════════════════════════════════════════════════════════════

                    // Determine which notes to display based on view mode and filter
                    val baseNotes = if (viewMode == "recent") recentNotes else notes
                    val activeStatusMap = if (viewMode == "recent") recentNoteStatusMap else currentDateNoteStatusMap
                    val displayNotes = baseNotes.filter { note ->
                        matchesNoteFilter(note, activeStatusMap, noteFilter)
                    }
                    val displayLoading = if (viewMode == "recent") isLoadingRecent else isLoading

                    if (displayLoading) {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Loading notes...",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.darkGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This may take a moment",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                } else if (baseNotes.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (viewMode == "recent") "No Boox Notes found" else "No Boox Notes for this day",
                            fontSize = EInkTheme.Typography.bodyLarge,
                            color = EInkTheme.Colors.gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Master-detail layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Left: Notes list (35%)
                        if (viewMode == "recent") {
                            // Recent notes list with Bulk Smart Link Wizard
                            RecentNotesListPane(
                                notes = recentNotes,
                                selectedNote = selectedNote,
                                onNoteSelected = { selectedNote = it },
                                noteStatusMap = recentNoteStatusMap,
                                allEvents = eventsForDate,  // Events for smart link suggestions
                                onLinkNoteToEvent = { noteId, eventId ->
                                    scope.launch {
                                        val noteDate = recentNotes.find { it.uniqueId == noteId }?.originalDate
                                        if (!noteDate.isNullOrBlank()) {
                                            booxNoteRepository.acceptSuggestion(noteId, eventId, noteDate)
                                        } else {
                                            booxNoteRepository.linkNoteToEvent(
                                                noteId,
                                                eventId,
                                                isManual = true
                                            )
                                        }
                                        refreshRecentNoteStatus()
                                        if (noteDate == currentDate) {
                                            refreshCurrentDateLinkState(eventsForDate)
                                        }
                                        // Notify parent to refresh events grid
                                        onLinkChanged()
                                    }
                                },
                                onDismissSuggestion = { noteId, eventId ->
                                    scope.launch {
                                        booxNoteRepository.dismissSuggestion(noteId, eventId)
                                        refreshRecentNoteStatus()
                                        val noteDate = recentNotes.find { it.uniqueId == noteId }?.originalDate
                                        if (noteDate == currentDate) {
                                            refreshCurrentDateLinkState(eventsForDate)
                                        }
                                    }
                                },
                                noteFilter = noteFilter,
                                onFilterChange = { noteFilter = it },
                                // Bulk wizard parameters
                                booxNoteIndexer = booxNoteIndexer,
                                booxNoteRepository = booxNoteRepository,
                                eventRepository = eventRepository,
                                onLinkChanged = onLinkChanged,
                                autoOpenBulkWizard = autoOpenBulkWizard,
                                isOcrIndexingInProgress = isOcrIndexingInProgress,
                                showSearchControls = ocrSearchEnabled,
                                onOpenSearch = { viewMode = "search" },
                                sourceFilter = sourceFilter,
                                onDismissAll = onDismiss,  // Pass main dialog dismiss
                                modifier = Modifier
                                    .weight(EInkTheme.BooxNotes.listPaneWeight)
                                    .fillMaxHeight()
                            )
                        } else {
                            NotesListPane(
                                notes = notes,
                                selectedNote = selectedNote,
                                onNoteSelected = { selectedNote = it },
                                noteStatusMap = currentDateNoteStatusMap,
                                noteToEventMap = noteToEventMap,
                                eventsForDate = eventsForDate,
                                onLinkNoteToEvent = { noteId, eventId ->
                                    scope.launch {
                                        val noteDate = notes.find { it.uniqueId == noteId }?.originalDate
                                        if (!noteDate.isNullOrBlank()) {
                                            booxNoteRepository.acceptSuggestion(noteId, eventId, noteDate)
                                        } else {
                                            booxNoteRepository.linkNoteToEvent(
                                                noteId,
                                                eventId,
                                                isManual = true
                                            )
                                        }
                                        refreshCurrentDateLinkState(eventsForDate)
                                        refreshRecentNoteStatus()

                                        // Generate feedback message
                                        val note = notes.find { it.uniqueId == noteId }
                                        val event = eventsForDate.find { it.id == eventId }
                                        if (note != null && event != null) {
                                            val noteTitle = note.title.take(15) + if (note.title.length > 15) "…" else ""
                                            val eventName = event.title.take(15) + if (event.title.length > 15) "…" else ""
                                            linkFeedbackMessage = "Linked: $noteTitle to $eventName"
                                        }

                                        // Notify parent to refresh events grid
                                        onLinkChanged()
                                    }
                                },
                                onDismissSuggestion = { noteId, eventId ->
                                    // Persist dismissal to database so suggestion won't appear again
                                    scope.launch {
                                        booxNoteRepository.dismissSuggestion(noteId, eventId)
                                        refreshCurrentDateLinkState(eventsForDate)
                                        refreshRecentNoteStatus()
                                    }
                                },
                                noteFilter = noteFilter,
                                onFilterChange = { noteFilter = it },
                                // Cross-day matching parameters
                                currentDate = currentDate,
                                eventRepository = eventRepository,
                                booxNoteIndexer = booxNoteIndexer,
                                showSearchControls = ocrSearchEnabled,
                                onOpenSearch = { viewMode = "search" },
                                modifier = Modifier
                                    .weight(EInkTheme.BooxNotes.listPaneWeight)
                                    .fillMaxHeight()
                            )
                        }

                        Divider(
                            color = EInkTheme.Colors.lightGray,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(EInkTheme.Divider.thickness)
                        )

                        // Right: Note detail (70%)
                        NoteDetailPane(
                            note = selectedNote,
                            previewResult = notePreview,
                            thumbnailBitmap = thumbnailBitmap,
                            isLoadingThumbnail = isLoadingThumbnail,
                            currentPageIndex = currentPageIndex,
                            onPageChange = { currentPageIndex = it },
                            noteLink = noteLink,
                            linkedEventDetails = linkedEventDetails,
                            eventsForDate = eventsForDate,
                            booxNoteIndexer = booxNoteIndexer,
                            linkFeedbackMessage = linkFeedbackMessage,
                            onLinkChanged = { eventId ->
                                scope.launch {
                                    selectedNote?.let { note ->
                                        booxNoteRepository.linkNoteToEvent(
                                            note.uniqueId,
                                            eventId,
                                            isManual = true
                                        )
                                        noteLink = booxNoteRepository.getLinkForNote(note.uniqueId)
                                        refreshRecentNoteStatus()
                                        if (note.originalDate == currentDate) {
                                            refreshCurrentDateLinkState(eventsForDate)
                                        }

                                        // Generate feedback message
                                        val noteTitle = note.title.take(20) + if (note.title.length > 20) "..." else ""
                                        if (eventId != null) {
                                            val event = eventsForDate.find { it.id == eventId }
                                            val eventName = event?.title?.take(20)?.let { it + if (event.title.length > 20) "..." else "" } ?: "event"
                                            linkFeedbackMessage = "Linked: $noteTitle to $eventName"
                                        } else {
                                            linkFeedbackMessage = "Unlinked: $noteTitle"
                                        }

                                        // Notify parent to refresh events grid
                                        onLinkChanged()
                                    }
                                }
                            },
                            onOpenInBooxNotes = {
                                selectedNote?.let { note ->
                                    openInBooxNotesIfValid(note)
                                }
                            },
                            // Cross-day event loading for "Select a different day" feature
                            onLoadEventsForDate = { dateStr ->
                                smartLinkEventRepository.getEventsForDateSync(dateStr)
                            },
                            modifier = Modifier
                                .weight(EInkTheme.BooxNotes.detailPaneWeightWider)  // +20px wider for note render
                                .fillMaxHeight()
                        )
                    }
                }
                } // End of DATE/RECENT MODES else block
            }
        }
    }
}

private data class AiReviewItem(
    val result: BooxNoteIndexer.LlmMatchResult,
    val note: BooxNoteIndex?,
    val event: CalendarEvent?
)

/**
 * Review screen for AI match results (high confidence only).
 */
@Composable
private fun BulkAiReviewScreen(
    results: List<BooxNoteIndexer.LlmMatchResult>,
    booxNoteRepository: BooxNoteRepository,
    eventRepository: CalendarEventRepository,
    minConfidence: Float,
    actedResults: Set<String>,
    onLink: (BooxNoteIndexer.LlmMatchResult) -> Unit,
    onSkip: (BooxNoteIndexer.LlmMatchResult) -> Unit,
    onApplyRemaining: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val smartLinkEventRepository = remember(context, eventRepository) {
        SmartLinkEventRepository(context, eventRepository)
    }
    val filteredResults = remember(results, actedResults, minConfidence) {
        results.filter { result ->
            result.matchedEventId != null &&
                result.confidence >= minConfidence &&
                !actedResults.contains(result.noteUniqueId)
        }
    }
    var reviewItems by remember { mutableStateOf<List<AiReviewItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filteredResults) {
        if (filteredResults.isEmpty()) {
            reviewItems = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val items = withContext(Dispatchers.IO) {
            val noteCache = mutableMapOf<String, BooxNoteIndex?>()
            val eventCache = mutableMapOf<String, CalendarEvent?>()
            filteredResults.map { result ->
                val note = noteCache.getOrPut(result.noteUniqueId) {
                    booxNoteRepository.getNoteById(result.noteUniqueId)
                }
                val event = result.matchedEventId?.let { eventId ->
                    eventCache.getOrPut(eventId) { smartLinkEventRepository.getEventById(eventId) }
                }
                AiReviewItem(result, note, event)
            }
        }
        reviewItems = items
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = EInkTheme.Global.paddingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.Global.touchTarget)
                        .noRippleClickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                    )
                }
                Column {
                    Text(
                        text = "Review AI Matches",
                        fontSize = EInkTheme.Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "${filteredResults.size} high confidence matches",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            val remainingCount = filteredResults.size
            Box(
                modifier = Modifier
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        if (remainingCount > 0) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(enabled = remainingCount > 0, onClick = onApplyRemaining)
                    .padding(horizontal = EInkTheme.Global.paddingLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (remainingCount > 0) "Link All ($remainingCount)" else "Done",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingCount > 0) EInkTheme.Colors.background else EInkTheme.Colors.gray
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading matches...",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
            return
        }

        if (reviewItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No high confidence AI matches",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            items(reviewItems, key = { it.result.noteUniqueId }) { item ->
                AiMatchReviewCard(
                    item = item,
                    onLink = { onLink(item.result) },
                    onSkip = { onSkip(item.result) }
                )
            }
        }
    }
}

@Composable
private fun AiMatchReviewCard(
    item: AiReviewItem,
    onLink: () -> Unit,
    onSkip: () -> Unit
) {
    val noteTitle = item.note?.title ?: "Unknown note"
    val noteTime = item.note?.let { formatTime(it.originalTimestamp) } ?: ""
    val eventTitle = item.event?.title ?: "Unknown event"
    val eventTime = item.event?.let { if (it.isAllDay) "All day" else formatTime(it.startTime) } ?: ""
    val confidenceText = "${(item.result.confidence * 100).toInt()}%"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(EInkTheme.Global.paddingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
            Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
            Text(
                text = noteTitle,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = confidenceText,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        if (noteTime.isNotEmpty()) {
            Text(
                text = noteTime,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
            Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
            Text(
                text = eventTitle,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (eventTime.isNotEmpty()) {
                Text(
                    text = eventTime,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }

        if (item.result.reasoning.isNotBlank()) {
            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
            Text(
                text = item.result.reasoning,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                    .padding(EInkTheme.Global.paddingSmall)
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.gray,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Skip",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.gray
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onLink),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Link",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.background
                    )
                    Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = EInkTheme.Colors.background,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesListPane(
    notes: List<BooxNoteIndex>,
    selectedNote: BooxNoteIndex?,
    onNoteSelected: (BooxNoteIndex) -> Unit,
    noteStatusMap: Map<String, NoteStatusSummary>,
    noteToEventMap: Map<String, CalendarEvent>,  // Maps noteUniqueId to linked event
    eventsForDate: List<CalendarEvent>,
    onLinkNoteToEvent: (String, String) -> Unit,  // noteId, eventId -> link them
    onDismissSuggestion: (String, String) -> Unit,  // noteId, eventId -> persist dismissal
    noteFilter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
    currentDate: String = "",  // For cross-day event loading
    eventRepository: CalendarEventRepository? = null,  // For cross-day event loading
    booxNoteIndexer: BooxNoteIndexer? = null,  // For cross-day matching
    showSearchControls: Boolean = false,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val smartLinkEventRepository = remember(context, eventRepository) {
        eventRepository?.let { SmartLinkEventRepository(context, it) }
    }
    // Calculate statistics
    val totalPages = remember(notes) { notes.sumOf { it.pageCount } }
    val linkedCount = remember(notes, noteStatusMap) {
        notes.count { noteStatusMap[it.uniqueId].isLinked() }
    }
    val displayNotes = remember(notes, noteStatusMap, noteFilter) {
        notes.filter { matchesNoteFilter(it, noteStatusMap, noteFilter) }
    }

    // Day Overview overlay state
    var showDayOverview by remember { mutableStateOf(false) }

    // Cross-day events for smart linking (±3 days)
    var crossDayEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isLoadingCrossDayEvents by remember { mutableStateOf(false) }

    // Load cross-day events when opening the Smart Link overlay
    LaunchedEffect(showDayOverview, currentDate) {
        if (showDayOverview && eventRepository != null && currentDate.isNotEmpty()) {
            isLoadingCrossDayEvents = true
            withContext(Dispatchers.IO) {
                try {
                    val currentLocalDate = LocalDate.parse(currentDate)
                    val startDate = currentLocalDate.minusDays(BooxNoteIndexer.DAY_MODE_SEARCH_DAYS.toLong())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val endDate = currentLocalDate.plusDays(BooxNoteIndexer.DAY_MODE_SEARCH_DAYS.toLong())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    crossDayEvents = smartLinkEventRepository?.getEventsInRange(startDate, endDate) ?: emptyList()
                } catch (e: Exception) {
                    crossDayEvents = emptyList()
                }
            }
            isLoadingCrossDayEvents = false
        }
    }

    Column(
        modifier = modifier.padding(end = 8.dp)
    ) {
        // Statistics header (info only)
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            // Stats line 1: note and page counts
            Text(
                text = "${notes.size} note${if (notes.size != 1) "s" else ""} • ${totalPages} page${if (totalPages != 1) "s" else ""}",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.darkGray
            )
            // Stats line 2: linked/unlinked counts
            Text(
                text = "$linkedCount linked • ${notes.size - linkedCount} unlinked",
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget)
                    .background(EInkTheme.Colors.background)
                    .border(2.dp, EInkTheme.Colors.primary)
                    .noRippleClickable { showDayOverview = true },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                    Text(
                        text = "Smart Link",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showSearchControls) {
                ListPaneSearchButton(onClick = onOpenSearch)
                Spacer(modifier = Modifier.height(8.dp))
            }

            NoteFilterPills(
                selectedFilter = noteFilter,
                onFilterChange = onFilterChange
            )
        }

        // Smart Link Suggestions Overlay with cross-day support
        if (showDayOverview) {
            DayOverviewOverlay(
                notes = notes,
                events = eventsForDate,
                noteToEventMap = noteToEventMap,
                onDismiss = { showDayOverview = false },
                onNoteSelected = { note ->
                    onNoteSelected(note)
                    showDayOverview = false
                },
                onLinkNoteToEvent = { noteId, eventId ->
                    onLinkNoteToEvent(noteId, eventId)
                },
                onDismissSuggestion = { noteId, eventId ->
                    onDismissSuggestion(noteId, eventId)
                },
                // Cross-day matching parameters
                crossDayEvents = crossDayEvents,
                booxNoteIndexer = booxNoteIndexer,
                currentDate = currentDate
            )
        }

        if (displayNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notes match this filter",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayNotes) { note ->
                    NoteListItem(
                        note = note,
                        isSelected = note.uniqueId == selectedNote?.uniqueId,
                        statusSummary = noteStatusMap[note.uniqueId],
                        linkedEvent = noteToEventMap[note.uniqueId],
                        onClick = { onNoteSelected(note) }
                    )
                }
            }
        }
    }
}

/**
 * Recent notes list pane - shows notes across all dates with date labels.
 * Used when "Recent" tab is selected.
 *
 * Enhanced (Dec 2025): Smart Link button now opens BulkSmartLinkWizard
 * which analyzes ALL unlinked notes with cross-day matching.
 */
@Composable
private fun RecentNotesListPane(
    notes: List<BooxNoteIndex>,
    selectedNote: BooxNoteIndex?,
    onNoteSelected: (BooxNoteIndex) -> Unit,
    noteStatusMap: Map<String, NoteStatusSummary> = emptyMap(),
    allEvents: List<CalendarEvent> = emptyList(),  // Events for smart linking (all recent dates)
    onLinkNoteToEvent: (String, String) -> Unit = { _, _ -> },
    onDismissSuggestion: (String, String) -> Unit = { _, _ -> },
    noteFilter: NoteFilter = NoteFilter.ALL,
    onFilterChange: (NoteFilter) -> Unit = {},
    // Bulk wizard parameters
    booxNoteIndexer: BooxNoteIndexer? = null,
    booxNoteRepository: BooxNoteRepository? = null,
    eventRepository: CalendarEventRepository? = null,
    onLinkChanged: () -> Unit = {},
    autoOpenBulkWizard: Boolean = false,  // If true, opens the Bulk Smart Link wizard immediately
    isOcrIndexingInProgress: Boolean = false,
    showSearchControls: Boolean = false,
    onOpenSearch: () -> Unit = {},
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter(),
    onDismissAll: () -> Unit = {},  // Closes wizard AND main dialog
    modifier: Modifier = Modifier
) {
    // Calculate statistics
    val totalPages = remember(notes) { notes.sumOf { it.pageCount } }
    val linkedCount = remember(notes, noteStatusMap) {
        notes.count { noteStatusMap[it.uniqueId].isLinked() }
    }
    val unlinkedCount = notes.size - linkedCount

    // Bulk Smart Link wizard state - initialized from autoOpenBulkWizard parameter
    var showBulkWizard by remember(autoOpenBulkWizard) { mutableStateOf(autoOpenBulkWizard) }

    val displayNotes = remember(notes, noteStatusMap, noteFilter) {
        notes.filter { matchesNoteFilter(it, noteStatusMap, noteFilter) }
    }

    Column(
        modifier = modifier.padding(end = 8.dp)
    ) {
        // Statistics header
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            // Stats line 1: note and page counts
            Text(
                text = "${notes.size} note${if (notes.size != 1) "s" else ""} • ${totalPages} page${if (totalPages != 1) "s" else ""}",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.darkGray
            )
            // Stats line 2: linked/unlinked counts
            Text(
                text = "$linkedCount linked • $unlinkedCount unlinked",
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Smart Link button - opens Bulk Wizard (outline style - looks clickable)
            val hasRequiredDeps = booxNoteIndexer != null && booxNoteRepository != null && eventRepository != null
            val canUseBulkWizard = hasRequiredDeps && !isOcrIndexingInProgress

            val smartLinkLabel = when {
                isOcrIndexingInProgress -> "Indexing..."
                unlinkedCount > 0 -> "Smart Link ($unlinkedCount)"
                else -> "Smart Link"
            }
            val smartLinkIcon = if (isOcrIndexingInProgress) Icons.Default.HourglassEmpty else Icons.Default.AutoAwesome
            val smartLinkBorderColor = if (canUseBulkWizard) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
            val smartLinkContentColor = if (canUseBulkWizard) EInkTheme.Colors.primary else EInkTheme.Colors.gray
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget)
                    .background(EInkTheme.Colors.background)
                    .border(2.dp, smartLinkBorderColor)
                    .noRippleClickable(enabled = canUseBulkWizard) { showBulkWizard = true },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = smartLinkIcon,
                        contentDescription = null,
                        tint = smartLinkContentColor,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                    Text(
                        text = smartLinkLabel,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = smartLinkContentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showSearchControls) {
                ListPaneSearchButton(onClick = onOpenSearch)
                Spacer(modifier = Modifier.height(8.dp))
            }

            NoteFilterPills(
                selectedFilter = noteFilter,
                onFilterChange = onFilterChange
            )
        }

        // Bulk Smart Link Wizard
        if (showBulkWizard && booxNoteIndexer != null && booxNoteRepository != null && eventRepository != null) {
            BulkSmartLinkWizard(
                booxNoteIndexer = booxNoteIndexer,
                booxNoteRepository = booxNoteRepository,
                eventRepository = eventRepository,
                onDismiss = { showBulkWizard = false },
                onDismissAll = {
                    showBulkWizard = false
                    onDismissAll()  // Also close main dialog
                },
                onLinkChanged = onLinkChanged,
                sourceFilter = sourceFilter
            )
        }

        if (displayNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notes match this filter",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayNotes) { note ->
                    RecentNoteListItem(
                        note = note,
                        isSelected = note.uniqueId == selectedNote?.uniqueId,
                        statusSummary = noteStatusMap[note.uniqueId],
                        onClick = { onNoteSelected(note) }
                    )
                }
            }
        }
    }
}

/**
 * List item for recent notes - shows note title, date, page count, and status.
 */
@Composable
private fun RecentNoteListItem(
    note: BooxNoteIndex,
    isSelected: Boolean,
    statusSummary: NoteStatusSummary? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBackground else EInkTheme.BooxNotes.noteCardBackground)
            .border(
                width = if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBorderWidth else EInkTheme.BooxNotes.noteCardBorderWidth,
                color = if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBorderColor else EInkTheme.BooxNotes.noteCardBorderColor
            )
            .noRippleClickable(onClick = onClick)
            .padding(EInkTheme.BooxNotes.noteCardPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            MiniNoteThumbnailPlaceholder()

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        fontSize = EInkTheme.BooxNotes.noteTitleFontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = EInkTheme.BooxNotes.noteTitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (note.pageCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PageCountBadge(pageCount = note.pageCount)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${formatDateForDisplay(note.originalDate)} • ${formatTime(note.originalTimestamp)}",
                    fontSize = EInkTheme.BooxNotes.noteMetaFontSize,
                    color = EInkTheme.BooxNotes.noteMetaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            NoteStatusBadge(
                statusSummary = statusSummary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun NoteListItem(
    note: BooxNoteIndex,
    isSelected: Boolean,
    statusSummary: NoteStatusSummary?,
    linkedEvent: CalendarEvent?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBackground else EInkTheme.BooxNotes.noteCardBackground)
            .border(
                width = if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBorderWidth else EInkTheme.BooxNotes.noteCardBorderWidth,
                color = if (isSelected) EInkTheme.BooxNotes.noteCardSelectedBorderColor else EInkTheme.BooxNotes.noteCardBorderColor
            )
            .noRippleClickable(onClick = onClick)
            .padding(EInkTheme.BooxNotes.noteCardPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            MiniNoteThumbnailPlaceholder()

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        fontSize = EInkTheme.BooxNotes.noteTitleFontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = EInkTheme.BooxNotes.noteTitleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (note.pageCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PageCountBadge(pageCount = note.pageCount)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = buildContextualTimestamp(note, linkedEvent),
                    fontSize = EInkTheme.BooxNotes.noteMetaFontSize,
                    color = EInkTheme.BooxNotes.noteMetaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            NoteStatusBadge(
                statusSummary = statusSummary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun MiniNoteThumbnailPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(EInkTheme.BooxNotes.thumbnailSize)
            .border(
                width = EInkTheme.BooxNotes.thumbnailBorderWidth,
                color = EInkTheme.BooxNotes.thumbnailBorderColor
            )
            .background(EInkTheme.Colors.offWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(6.dp)
        ) {
            PlaceholderLine(width = 26.dp)
            PlaceholderLine(width = 34.dp)
            PlaceholderLine(width = 20.dp)
        }
    }
}

@Composable
private fun PlaceholderLine(width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(3.dp)
            .background(EInkTheme.Colors.lightGray)
    )
}

@Composable
private fun PageCountBadge(
    pageCount: Int
) {
    Box(
        modifier = Modifier
            .background(
                color = EInkTheme.Colors.offWhite,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .border(
                width = 1.dp,
                color = EInkTheme.Colors.darkGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${pageCount}p",
            fontSize = EInkTheme.Typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.darkGray
        )
    }
}

@Composable
private fun NoteStatusBadge(
    statusSummary: NoteStatusSummary?,
    modifier: Modifier = Modifier
) {
    val (label, icon, isFilled) = when {
        statusSummary.isLinked() -> Triple("Linked", Icons.Default.CheckCircle, true)
        statusSummary.hasStrongSuggestion() -> Triple("Suggested", Icons.Default.Star, false)
        statusSummary.needsReview() -> Triple("Review", Icons.Default.Lightbulb, false)
        else -> Triple("Unlinked", Icons.Default.Description, false)
    }

    val backgroundColor = if (isFilled) EInkTheme.Colors.primary else EInkTheme.Colors.background
    val contentColor = if (isFilled) EInkTheme.Colors.background else EInkTheme.Colors.primary

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = EInkTheme.Typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ListPaneSearchButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget)
            .background(EInkTheme.Colors.background)
            .border(1.dp, EInkTheme.ModernDialog.borderColor)
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = EInkTheme.ModernDialog.titleColor,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
            Text(
                text = "Search & Filter",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.ModernDialog.titleColor
            )
        }
    }
}

@Composable
private fun NoteFilterPills(
    selectedFilter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit
) {
    val filterRows = listOf(
        listOf(NoteFilter.ALL, NoteFilter.UNLINKED),
        listOf(NoteFilter.LINKED, NoteFilter.NEEDS_REVIEW)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.BooxNotes.filterChipSpacing)
    ) {
        filterRows.forEach { rowFilters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.BooxNotes.filterChipSpacing)
            ) {
                rowFilters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .background(
                                if (isSelected) EInkTheme.BooxNotes.filterChipSelectedBackground else EInkTheme.BooxNotes.filterChipBackground,
                                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                            )
                            .border(
                                EInkTheme.BooxNotes.filterChipBorderWidth,
                                EInkTheme.BooxNotes.filterChipBorderColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                            )
                            .noRippleClickable { onFilterChange(filter) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (filter) {
                                NoteFilter.ALL -> "All"
                                NoteFilter.UNLINKED -> "Unlinked"
                                NoteFilter.LINKED -> "Linked"
                                NoteFilter.NEEDS_REVIEW -> "Needs Review"
                            },
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EInkTheme.BooxNotes.filterChipSelectedTextColor else EInkTheme.BooxNotes.filterChipTextColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build contextual timestamp string.
 * If linked to an event: "3:47 PM • during 'Client Call'"
 * If not linked: just "3:47 PM" (page count is now in the badge)
 */
private fun buildContextualTimestamp(note: BooxNoteIndex, linkedEvent: CalendarEvent?): String {
    val timeStr = formatTime(note.originalTimestamp)
    return if (linkedEvent != null) {
        val eventName = if (linkedEvent.title.length > 18) {
            linkedEvent.title.take(18) + "…"
        } else {
            linkedEvent.title
        }
        "$timeStr • during '$eventName'"
    } else {
        timeStr
    }
}

@Composable
private fun NoteDetailPane(
    note: BooxNoteIndex?,
    previewResult: BooxNotePreviewResult?,
    thumbnailBitmap: ImageBitmap?,
    isLoadingThumbnail: Boolean,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    noteLink: BooxNoteLink?,
    linkedEventDetails: CalendarEvent?,  // Pre-fetched linked event (supports cross-day)
    eventsForDate: List<CalendarEvent>,
    booxNoteIndexer: BooxNoteIndexer? = null,
    linkFeedbackMessage: String?,
    onLinkChanged: (String?) -> Unit,
    onOpenInBooxNotes: () -> Unit,
    // Cross-day event loading
    onLoadEventsForDate: suspend (String) -> List<CalendarEvent> = { emptyList() },
    modifier: Modifier = Modifier
) {
    // Zoom state: 1.0 = 100%, 1.5 = 150%, 2.0 = 200%
    var zoomLevel by remember { mutableStateOf(1.0f) }
    var openEventDropdownSignal by remember { mutableStateOf(0) }

    if (note == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a note to preview",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray
            )
        }
        return
    }

    val fallbackPageIds = remember(note.pageIdsJson) { parsePageIds(note.pageIdsJson) }
    val pageIds = previewResult?.pageIds ?: fallbackPageIds
    val totalPages = previewResult?.totalPages ?: fallbackPageIds.size.coerceAtLeast(note.pageCount)
    val effectivePageIndex = if (totalPages > 0) currentPageIndex.coerceIn(0, totalPages - 1) else 0
    val textPreview = previewResult as? BooxNotePreviewResult.TextPreview
    val unavailablePreview = previewResult as? BooxNotePreviewResult.Unavailable
    var quickLinkEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    val matchReasonSummary = remember(noteLink?.matchReasons, linkedEventDetails?.id) {
        if (linkedEventDetails == null) {
            null
        } else {
            parseStoredMatchReasons(noteLink?.matchReasons)
                .take(3)
                .joinToString(" • ") { formatMatchReasonShort(it) }
                .takeIf { it.isNotBlank() }
        }
    }
    val decisionModeSummary = remember(noteLink?.decisionMode) {
        formatDecisionModeShort(noteLink?.decisionMode)
    }
    val dataQualityFlags = remember(noteLink?.dataQualityFlagsJson) {
        parseStoredDataQualityFlags(noteLink?.dataQualityFlagsJson)
    }
    val degradedMessage = remember(dataQualityFlags) {
        formatDataQualityMessage(dataQualityFlags)
    }

    // Reset zoom when note changes
    LaunchedEffect(note.uniqueId) {
        zoomLevel = 1.0f
    }

    LaunchedEffect(note.uniqueId, eventsForDate, linkedEventDetails, booxNoteIndexer) {
        if (linkedEventDetails != null) {
            quickLinkEvent = null
            return@LaunchedEffect
        }
        if (eventsForDate.isEmpty()) {
            quickLinkEvent = null
            return@LaunchedEffect
        }
        val indexer = booxNoteIndexer
        if (indexer == null) {
            quickLinkEvent = null
            return@LaunchedEffect
        }
        val best = withContext(Dispatchers.IO) {
            indexer.getBestEventMatchForNote(note, eventsForDate)
        }
        quickLinkEvent = best
    }

    // linkedEventDetails is passed as parameter (supports cross-day links)

    Column(
        modifier = modifier.padding(start = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Note title - larger and bolder
        Text(
            text = note.title,
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        NoteLinkStatusSection(
            noteDate = note.originalDate,
            linkedEvent = linkedEventDetails,
            suggestedEvent = quickLinkEvent,
            hasEventsAvailable = eventsForDate.isNotEmpty(),
            decisionModeSummary = decisionModeSummary,
            matchReasonSummary = matchReasonSummary,
            degradedMessage = degradedMessage,
            onAcceptSuggestion = quickLinkEvent?.let { event -> { onLinkChanged(event.id) } },
            onChooseDifferentEvent = { openEventDropdownSignal++ },
            onUnlink = if (noteLink?.linkedEventId != null) ({ onLinkChanged(null) }) else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Event linking dropdown (supports cross-day event selection)
        EventLinkingDropdown(
            currentEventId = noteLink?.linkedEventId,
            linkedEvent = linkedEventDetails,
            events = eventsForDate,
            onEventSelected = onLinkChanged,
            onLoadEventsForDate = onLoadEventsForDate,
            openDropdownSignal = openEventDropdownSignal,
            modifier = Modifier.fillMaxWidth()
        )

        // Fixed height container for feedback message (prevents dialog size shift)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (linkFeedbackMessage != null) {
                Text(
                    text = linkFeedbackMessage,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,  // Allow 2 lines for longer messages
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Thumbnail preview with zoom - styled as a paper page
        // Wrapped in Row with page navigation chevrons on left/right (only for multi-page notes)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left chevron - previous page (only shown for multi-page notes)
            if (totalPages > 1) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.BooxNotes.chevronSize)
                        .then(
                            if (effectivePageIndex > 0) Modifier.noRippleClickable { onPageChange(effectivePageIndex - 1) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous page",
                        tint = if (effectivePageIndex > 0) EInkTheme.BooxNotes.chevronIconColor
                               else EInkTheme.BooxNotes.chevronIconColorDisabled,
                        modifier = Modifier.size(EInkTheme.BooxNotes.chevronIconSize)
                    )
                }
            }

            // Thumbnail preview box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
            when {
                isLoadingThumbnail -> {
                    // Loading skeleton - paper outline with pulsing effect
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.75f)  // 3:4 aspect ratio
                            .background(EInkTheme.Colors.offWhite)
                            .border(2.dp, EInkTheme.Colors.borderStrong),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = EInkTheme.Colors.gray,
                                modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Loading...",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
                thumbnailBitmap != null -> {
                    // FIXED container with clipping - zoom stays within bounds
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),  // Clip zoomed content to container
                        contentAlignment = Alignment.Center
                    ) {
                        // Scrollable inner container for zoomed content
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (zoomLevel > 1.0f) {
                                        Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .verticalScroll(rememberScrollState())
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Paper with shadow - scales with zoom
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.95f)
                                    .aspectRatio(0.75f)
                                    .graphicsLayer {
                                        scaleX = zoomLevel
                                        scaleY = zoomLevel
                                    }
                            ) {
                                // Main paper with image
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(EInkTheme.Colors.background)
                                        .border(2.dp, EInkTheme.Colors.darkGray)
                                ) {
                                    Image(
                                        bitmap = thumbnailBitmap,
                                        contentDescription = "Note preview",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                textPreview != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.75f)
                            .background(EInkTheme.Colors.offWhite)
                            .border(2.dp, EInkTheme.Colors.borderStrong)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textPreview.text,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary,
                                lineHeight = 22.sp
                            )
                            textPreview.diagnostics?.message?.let { message ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = message,
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Empty state - styled paper placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.75f)
                            .background(EInkTheme.Colors.offWhite)
                            .border(2.dp, EInkTheme.Colors.borderStrong),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = EInkTheme.Colors.gray,
                                modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = unavailablePreview?.diagnostics?.message ?: "Preview unavailable",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )
                            unavailablePreview?.diagnostics?.reasonCode?.let { reasonCode ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = reasonCode,
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.lightGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            }  // End thumbnail preview box

            // Right chevron - next page (only shown for multi-page notes)
            if (totalPages > 1) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.BooxNotes.chevronSize)
                        .then(
                            if (effectivePageIndex < totalPages - 1) Modifier.noRippleClickable { onPageChange(effectivePageIndex + 1) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next page",
                        tint = if (effectivePageIndex < totalPages - 1) EInkTheme.BooxNotes.chevronIconColor
                               else EInkTheme.BooxNotes.chevronIconColorDisabled,
                        modifier = Modifier.size(EInkTheme.BooxNotes.chevronIconSize)
                    )
                }
            }
        }  // End Row with page chevrons

        Spacer(modifier = Modifier.height(6.dp))

        // Page indicator (centered, only for multi-page) + Zoom controls (centered)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom controls (always centered)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (zoomLevel > 1.0f) zoomLevel -= 0.5f },
                    enabled = zoomLevel > 1.0f,
                    modifier = Modifier.size(EInkTheme.Global.touchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out",
                        tint = if (zoomLevel > 1.0f) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                }
                Text(
                    text = "${(zoomLevel * 100).toInt()}%",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { if (zoomLevel < 2.0f) zoomLevel += 0.5f },
                    enabled = zoomLevel < 2.0f,
                    modifier = Modifier.size(EInkTheme.Global.touchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in",
                        tint = if (zoomLevel < 2.0f) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                }
            }

            // Page indicator text (if multi-page)
            if (totalPages > 1) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${effectivePageIndex + 1}/$totalPages",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Open in Boox Notes button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                .noRippleClickable(onClick = onOpenInBooxNotes),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = EInkTheme.Button.primaryTextColor,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open in Boox Notes",
                    color = EInkTheme.Button.primaryTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.Typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun NoteLinkStatusSection(
    noteDate: String,
    linkedEvent: CalendarEvent?,
    suggestedEvent: CalendarEvent?,
    hasEventsAvailable: Boolean,
    decisionModeSummary: String?,
    matchReasonSummary: String?,
    degradedMessage: String?,
    onAcceptSuggestion: (() -> Unit)?,
    onChooseDifferentEvent: () -> Unit,
    onUnlink: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val headerIcon = when {
        linkedEvent != null -> Icons.Default.CheckCircle
        suggestedEvent != null -> Icons.Default.Star
        else -> Icons.Default.Description
    }
    val headerLabel = when {
        linkedEvent != null -> "Linked event"
        suggestedEvent != null -> "Suggested match"
        else -> "Not linked"
    }
    val primaryText = when {
        linkedEvent != null -> linkedEvent.title
        suggestedEvent != null -> suggestedEvent.title
        hasEventsAvailable -> "Choose an event to link this note"
        else -> "No eligible events are available for this note"
    }
    val secondaryText = when {
        linkedEvent != null -> buildEventContextText(linkedEvent, noteDate)
        suggestedEvent != null -> "Best same-day Smart Link match • ${buildEventContextText(suggestedEvent, noteDate)}"
        hasEventsAvailable -> "You can link this note manually or accept a suggested match when available."
        else -> "Linking needs calendar events before Smart Link can suggest a match."
    }

    Column(
        modifier = modifier
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
            Text(
                text = headerLabel,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = primaryText,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = secondaryText,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        decisionModeSummary?.let { summary ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        matchReasonSummary?.let { summary ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Why: $summary",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        degradedMessage?.let { message ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            linkedEvent != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailActionButton(
                        text = "Unlink",
                        onClick = { onUnlink?.invoke() },
                        primary = false,
                        modifier = Modifier.weight(1f)
                    )
                    DetailActionButton(
                        text = "Choose Different Event",
                        onClick = onChooseDifferentEvent,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            suggestedEvent != null && onAcceptSuggestion != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailActionButton(
                        text = "Link Suggestion",
                        onClick = onAcceptSuggestion,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                    DetailActionButton(
                        text = "Choose Different Event",
                        onClick = onChooseDifferentEvent,
                        primary = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            hasEventsAvailable -> {
                DetailActionButton(
                    text = "Choose Event",
                    onClick = onChooseDifferentEvent,
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DetailActionButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (primary) EInkTheme.Colors.primary else EInkTheme.Colors.background
    val contentColor = if (primary) EInkTheme.Colors.background else EInkTheme.Colors.primary
    val borderColor = if (primary) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray

    Box(
        modifier = modifier
            .height(EInkTheme.Global.touchTarget)
            .background(background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Global.borderWidth, borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

private fun buildEventContextText(
    event: CalendarEvent,
    noteDate: String
): String {
    val timeOrDay = if (event.isAllDay) {
        "All day"
    } else {
        formatTime(event.startTime)
    }
    return if (event.dayId != noteDate) {
        "${formatDateShort(event.dayId)} • $timeOrDay"
    } else {
        timeOrDay
    }
}

@Composable
private fun EventLinkingDropdown(
    currentEventId: String?,
    linkedEvent: CalendarEvent? = null,  // Pre-fetched linked event (for cross-day display)
    events: List<CalendarEvent>,
    onEventSelected: (String?) -> Unit,
    onLoadEventsForDate: suspend (String) -> List<CalendarEvent> = { emptyList() },
    openDropdownSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(openDropdownSignal) {
        if (openDropdownSignal > 0) {
            expanded = true
        }
    }

    // State for "Select a different day" feature
    var showDatePicker by remember { mutableStateOf(false) }
    var customDate by remember { mutableStateOf<String?>(null) }
    var customDateEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isLoadingCustomEvents by remember { mutableStateOf(false) }

    // Determine which events to show: custom date events or original events
    val displayEvents = if (customDate != null) customDateEvents else events

    // Find currently linked event (use pre-fetched linkedEvent, then check local lists)
    val currentEvent = linkedEvent
        ?: displayEvents.find { it.id == currentEventId }
        ?: events.find { it.id == currentEventId }

    // Display text: show date for cross-day links, just title for same-day
    val displayText = when {
        currentEvent != null -> {
            // Cross-day if linkedEvent provided but not in same-day events list
            val isCrossDay = linkedEvent != null && !events.any { it.id == currentEventId }
            if (isCrossDay)
                "${currentEvent.title} (${formatDateShort(currentEvent.dayId)})"
            else
                currentEvent.title
        }
        currentEventId != null -> "Unknown event"
        else -> "No link"
    }

    // Format custom date for display
    val customDateDisplay = customDate?.let { dateStr ->
        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
            java.time.LocalDate.parse(dateStr, formatter).format(displayFormatter)
        } catch (e: Exception) { dateStr }
    }

    Column(modifier = modifier) {
        val headerText = when {
            customDate != null -> "Events from $customDateDisplay"
            currentEventId != null -> "Choose different event"
            else -> "Choose event"
        }
        Text(
            text = headerText,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = EInkTheme.Global.touchTarget)
                    .border(EInkTheme.Global.borderWidth, Color.Black)
                    .noRippleClickable { expanded = true }
                    .padding(horizontal = EInkTheme.Global.paddingMedium, vertical = EInkTheme.Global.spacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = if (currentEventId == null) EInkTheme.Colors.gray else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand",
                    tint = EInkTheme.Colors.darkGray
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(0.dp, EInkTheme.BooxNotes.eventDropdownOffset),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .heightIn(max = EInkTheme.BooxNotes.eventDropdownMaxHeight)
                    .border(1.dp, EInkTheme.BooxNotes.eventDropdownBorderColor)
            ) {
                EInkDropdownMenuItem(
                    onClick = {
                        expanded = false
                        showDatePicker = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select a different day",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }

                Divider()

                EInkDropdownMenuItem(
                    onClick = {
                        onEventSelected(null)
                        expanded = false
                    }
                ) {
                    Text(
                        text = if (currentEventId != null) "Remove link" else "No link",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.gray
                    )
                }

                // Event options (from current day or custom date)
                if (isLoadingCustomEvents) {
                    EInkDropdownMenuItem(onClick = {}, enabled = false) {
                        Text(
                            text = "Loading events...",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                    }
                } else {
                    if (displayEvents.isEmpty() && customDate == null) {
                        EInkDropdownMenuItem(onClick = {}, enabled = false) {
                            Text(
                                text = "No events today",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }

                    if (displayEvents.isNotEmpty()) {
                        Divider()
                    }

                    displayEvents.forEach { event ->
                        EInkDropdownMenuItem(
                            onClick = {
                                onEventSelected(event.id)
                                expanded = false
                            }
                        ) {
                            Column {
                                Text(
                                    text = event.title,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (event.isAllDay) "All day" else formatTime(event.startTime),
                                    fontSize = EInkTheme.Typography.labelSmall,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    }

                    if (displayEvents.isEmpty() && customDate != null) {
                        EInkDropdownMenuItem(onClick = {}, enabled = false) {
                            Text(
                                text = "No events on this day",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }
        }

        // Inline Date Picker (when "Select a different day" is clicked)
        if (showDatePicker) {
            Spacer(modifier = Modifier.height(8.dp))
            InlineDatePicker(
                onDateSelected = { selectedDate ->
                    showDatePicker = false
                    customDate = selectedDate
                    isLoadingCustomEvents = true
                    scope.launch {
                        customDateEvents = onLoadEventsForDate(selectedDate)
                        isLoadingCustomEvents = false
                        // Auto-expand dropdown to show loaded events
                        expanded = true
                    }
                },
                onCancel = { showDatePicker = false }
            )
        }
    }
}

@Composable
private fun EInkDropdownMenuItem(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .noRippleClickable(enabled = enabled) {
                if (enabled) {
                    onClick()
                }
            }
            .padding(horizontal = EInkTheme.Global.paddingMedium, vertical = EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Smart Link Suggestions popup - AI-like feature that analyzes time proximity
 * between unlinked notes and events, suggesting matches for one-tap linking.
 *
 * Enhanced with cross-day matching (Dec 2025):
 * - When crossDayEvents and booxNoteIndexer are provided, uses intelligent
 *   cross-day scoring algorithm that looks ±3 days for matching events.
 * - Shows day badges and reasoning for cross-day suggestions.
 */
@Composable
private fun DayOverviewOverlay(
    notes: List<BooxNoteIndex>,
    events: List<CalendarEvent>,
    noteToEventMap: Map<String, CalendarEvent>,
    onDismiss: () -> Unit,
    onNoteSelected: (BooxNoteIndex) -> Unit,
    onLinkNoteToEvent: ((String, String) -> Unit)? = null,  // noteId, eventId -> link them
    onDismissSuggestion: ((String, String) -> Unit)? = null,  // noteId, eventId -> persist dismissal
    // Cross-day matching parameters (optional - falls back to same-day only if not provided)
    crossDayEvents: List<CalendarEvent> = emptyList(),
    booxNoteIndexer: BooxNoteIndexer? = null,
    currentDate: String = ""
) {
    val scope = rememberCoroutineScope()

    // Track which suggestions have been acted on (linked or skipped)
    var actedSuggestions by remember { mutableStateOf(setOf<String>()) }

    // Track how many were actually LINKED (not skipped) this session
    var linkedThisSession by remember { mutableStateOf(0) }

    // Use cross-day matching if indexer and events are available, otherwise fall back to same-day
    val useCrossDayMatching = booxNoteIndexer != null && crossDayEvents.isNotEmpty()
    var crossDaySuggestions by remember { mutableStateOf<List<CrossDayLinkSuggestion>>(emptyList()) }
    var sameDaySuggestions by remember { mutableStateOf<List<LinkSuggestion>>(emptyList()) }

    // Build cross-day suggestions using the enhanced algorithm
    LaunchedEffect(notes, crossDayEvents, noteToEventMap, actedSuggestions, useCrossDayMatching, booxNoteIndexer) {
        if (!useCrossDayMatching) {
            crossDaySuggestions = emptyList()
            return@LaunchedEffect
        }
        val indexer = booxNoteIndexer ?: return@LaunchedEffect
        val unlinkedNotes = notes.filter { !noteToEventMap.containsKey(it.uniqueId) }
        if (unlinkedNotes.isEmpty()) {
            crossDaySuggestions = emptyList()
            return@LaunchedEffect
        }
        val suggestions = withContext(Dispatchers.IO) {
            unlinkedNotes.mapNotNull { note ->
                if (actedSuggestions.contains(note.uniqueId)) return@mapNotNull null

                val matches = indexer.findCrossDaySuggestions(
                    note = note,
                    events = crossDayEvents,
                    searchDays = BooxNoteIndexer.DAY_MODE_SEARCH_DAYS
                )

                matches.firstOrNull()
            }.sortedByDescending { it.confidenceScore }
        }
        crossDaySuggestions = suggestions
    }

    // Same-day suggestions should come from the shared engine when available.
    LaunchedEffect(notes, events, noteToEventMap, actedSuggestions, useCrossDayMatching, booxNoteIndexer) {
        if (useCrossDayMatching) {
            sameDaySuggestions = emptyList()
            return@LaunchedEffect
        }

        val unlinkedNotes = notes.filter { !noteToEventMap.containsKey(it.uniqueId) }
        if (unlinkedNotes.isEmpty()) {
            sameDaySuggestions = emptyList()
            return@LaunchedEffect
        }

        val timedEvents = events.filter { !it.isAllDay }
        if (timedEvents.isEmpty()) {
            sameDaySuggestions = emptyList()
            return@LaunchedEffect
        }

        val indexer = booxNoteIndexer
        if (indexer == null) {
            sameDaySuggestions = buildLegacySameDaySuggestions(
                notes = notes,
                events = events,
                noteToEventMap = noteToEventMap,
                actedSuggestions = actedSuggestions
            )
            return@LaunchedEffect
        }

        val eventById = timedEvents.associateBy { it.id }
        val suggestions = withContext(Dispatchers.IO) {
            unlinkedNotes.mapNotNull { note ->
                if (actedSuggestions.contains(note.uniqueId)) return@mapNotNull null

                indexer.getSuggestionsForNote(
                    note = note,
                    events = timedEvents,
                    maxSuggestions = 1
                )
                    .firstOrNull { it.confidence != LinkConfidence.NONE }
                    ?.let { buildSharedSameDaySuggestion(note, eventById, it) }
            }
        }.sortedWith(
            compareBy<LinkSuggestion>(
                {
                    when (it.confidence) {
                        "high" -> 0
                        "medium" -> 1
                        else -> 2
                    }
                },
                { it.timeDiffMinutes }
            )
        )
        sameDaySuggestions = suggestions
    }

    // Unified suggestion count for UI
    val suggestionCount = if (useCrossDayMatching) crossDaySuggestions.size else sameDaySuggestions.size

    // Already linked count
    val linkedCount = notes.count { noteToEventMap.containsKey(it.uniqueId) }
    val totalNotes = notes.size

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.BooxNotes.dialogWidthFraction)  // Match BulkSmartLinkWizard
                    .fillMaxHeight(0.85f)  // Match BulkSmartLinkWizard
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EInkTheme.ModernDialog.titleColor,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Link",
                            fontSize = EInkTheme.ModernDialog.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.ModernDialog.titleColor
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.ModernDialog.titleColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Status summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$linkedCount/$totalNotes linked",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    if (suggestionCount > 0) {
                        Text(
                            text = "$suggestionCount to review",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.ModernDialog.titleColor
                        )
                    }
                }

                // Cross-day indicator when enabled
                if (useCrossDayMatching && suggestionCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = EInkTheme.Colors.gray,
                            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Searching ±${BooxNoteIndexer.DAY_MODE_SEARCH_DAYS} days",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                // Scroll indicator when there are multiple items
                if (suggestionCount > 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = EInkTheme.Colors.gray,
                            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$suggestionCount suggestions — scroll to see all",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                // Suggestions list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (suggestionCount == 0) {
                        // Empty state - varies based on context
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when {
                                    // User linked notes this session AND all are now linked
                                    linkedThisSession > 0 && linkedCount == totalNotes -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EInkTheme.Colors.primary,
                                            modifier = Modifier.size(EInkTheme.BooxNotes.emptyStateIconSize)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "All notes linked!",
                                            fontSize = EInkTheme.Typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EInkTheme.Colors.primary
                                        )
                                        Text(
                                            text = "Your day is fully organized",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            color = EInkTheme.Colors.darkGray
                                        )
                                    }
                                    // User linked some notes this session
                                    linkedThisSession > 0 -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = EInkTheme.Colors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Done!",
                                                fontSize = EInkTheme.Typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = EInkTheme.Colors.primary
                                            )
                                        }
                                        Text(
                                            text = "Linked $linkedThisSession note${if (linkedThisSession > 1) "s" else ""} this session",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            color = EInkTheme.Colors.darkGray
                                        )
                                    }
                                    // No events to link to (check cross-day events too)
                                    events.isEmpty() && crossDayEvents.isEmpty() -> {
                                        Text(
                                            text = if (useCrossDayMatching) "No events within ±${BooxNoteIndexer.DAY_MODE_SEARCH_DAYS} days" else "No events today",
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.gray
                                        )
                                        Text(
                                            text = "Add events to link notes to them",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            color = EInkTheme.Colors.gray
                                        )
                                    }
                                    // All notes already linked (opened dialog with nothing to do)
                                    linkedCount == totalNotes -> {
                                        Text(
                                            text = "All notes already linked",
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.darkGray
                                        )
                                        Text(
                                            text = "Nothing to suggest",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            color = EInkTheme.Colors.gray
                                        )
                                    }
                                    // Unlinked notes exist but no time-based matches
                                    else -> {
                                        Text(
                                            text = "No matches found",
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.gray
                                        )
                                        Text(
                                            text = if (useCrossDayMatching) "No nearby events match these notes" else "Notes are too far from event times",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            color = EInkTheme.Colors.gray
                                        )
                                    }
                                }
                            }
                        }
                    } else if (useCrossDayMatching) {
                        // Show cross-day suggestions using enhanced card
                        crossDaySuggestions.forEach { suggestion ->
                            CrossDaySmartLinkSuggestionCard(
                                suggestion = suggestion,
                                onLink = {
                                    onLinkNoteToEvent?.invoke(suggestion.note.uniqueId, suggestion.event.id)
                                    actedSuggestions = actedSuggestions + suggestion.note.uniqueId
                                    linkedThisSession++
                                },
                                onSkip = {
                                    onDismissSuggestion?.invoke(suggestion.note.uniqueId, suggestion.event.id)
                                    actedSuggestions = actedSuggestions + suggestion.note.uniqueId
                                }
                            )
                        }
                    } else {
                        // Show same-day suggestions
                        sameDaySuggestions.forEach { suggestion ->
                            SmartLinkSuggestionCard(
                                suggestion = suggestion,
                                onLink = {
                                    onLinkNoteToEvent?.invoke(suggestion.note.uniqueId, suggestion.suggestedEvent.id)
                                    actedSuggestions = actedSuggestions + suggestion.note.uniqueId
                                    linkedThisSession++  // Track successful links
                                },
                                onSkip = {
                                    // Persist dismissal so this suggestion won't appear again
                                    onDismissSuggestion?.invoke(suggestion.note.uniqueId, suggestion.suggestedEvent.id)
                                    actedSuggestions = actedSuggestions + suggestion.note.uniqueId
                                }
                            )
                        }
                    }
                }

                // Footer - helpful explanation
                Divider(
                    color = EInkTheme.ModernDialog.borderColor,
                    thickness = EInkTheme.ModernDialog.borderWidth
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = EInkTheme.Colors.gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (useCrossDayMatching)
                            "Matching notes to events within ±${BooxNoteIndexer.DAY_MODE_SEARCH_DAYS} days"
                        else
                            "Notes created near event times are suggested as matches",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * A single smart link suggestion card - COMPACT design for e-ink.
 * White background, minimal padding, horizontal layout.
 */
@Composable
private fun SmartLinkSuggestionCard(
    suggestion: LinkSuggestion,
    onLink: () -> Unit,
    onSkip: () -> Unit
) {
    val note = suggestion.note
    val event = suggestion.suggestedEvent
    val timeDiff = suggestion.timeDiffMinutes
    val confidence = suggestion.confidence

    val timeDiffText = when {
        timeDiff < 1 -> "~0m"
        timeDiff < 60 -> "${timeDiff}m"
        else -> "${timeDiff / 60}h${timeDiff % 60}m"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.ModernDialog.OptionCard.background,
                RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .border(
                width = if (confidence == "high") EInkTheme.Global.borderWidth else EInkTheme.ModernDialog.OptionCard.borderWidth,
                color = if (confidence == "high") EInkTheme.ModernDialog.titleColor else EInkTheme.ModernDialog.OptionCard.borderColor,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Note info (compact)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatTime(note.originalTimestamp)} • ${note.pageCount}p",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        // Arrow with time diff - LARGER for visibility
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Link to",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = timeDiffText,
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        // Event info (compact)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (event.isAllDay) "All day" else formatTime(event.startTime),
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        // Compact action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            // Skip button (outlined style)
            Box(
                modifier = Modifier
                    .border(
                        EInkTheme.ModernDialog.OptionCard.borderWidth,
                        EInkTheme.ModernDialog.CancelLink.textColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    )
                    .noRippleClickable(onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Skip",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.ModernDialog.CancelLink.textColor
                )
            }

            // Link button (filled style)
            Box(
                modifier = Modifier
                    .background(
                        EInkTheme.ModernDialog.PrimaryButton.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    )
                    .noRippleClickable(onClick = onLink)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Link",
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.PrimaryButton.textColor
                )
            }
        }
    }
}

/**
 * E-INK OPTIMIZED Suggestion Card for Review Suggestions screen.
 *
 * Design principles:
 * - Side-by-side layout: Note on left, Event on right
 * - Dates shown below titles for clarity
 * - 44dp touch targets for buttons
 * - Visible confidence badge with label (HIGH/MED/LOW)
 * - Day offset badge for cross-day matches
 * - Reasoning text with lightbulb icon
 */
@Composable
private fun CrossDaySmartLinkSuggestionCard(
    suggestion: CrossDayLinkSuggestion,
    onLink: () -> Unit,
    onSkip: () -> Unit
) {
    val note = suggestion.note
    val event = suggestion.event
    val dayOffset = suggestion.dayOffset
    val confidence = suggestion.confidence
    val reasoning = suggestion.reasoning

    // Format day offset text
    val dayOffsetText = when (dayOffset) {
        0 -> null
        1 -> "+1 day"
        -1 -> "-1 day"
        else -> if (dayOffset > 0) "+$dayOffset days" else "$dayOffset days"
    }

    // Format note date for display
    val noteDateText = try {
        val noteDate = LocalDate.parse(note.originalDate)
        noteDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        note.originalDate
    }

    // Format event date for display
    val eventDateText = try {
        val eventDate = LocalDate.parse(event.dayId)
        eventDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        event.dayId
    }

    val confidenceScoreText = "Score ${suggestion.confidenceScore}"

    // Confidence badge styling
    val (confidenceLabel, confidenceBg, confidenceTextColor) = when (confidence) {
        LinkConfidence.HIGH -> Triple("HIGH", EInkTheme.Colors.primary, EInkTheme.Colors.background)
        LinkConfidence.MEDIUM -> Triple("MED", EInkTheme.Colors.offWhite, EInkTheme.Colors.primary)
        LinkConfidence.LOW -> Triple("LOW", EInkTheme.Colors.offWhite, EInkTheme.Colors.gray)
        else -> Triple("", EInkTheme.Colors.offWhite, EInkTheme.Colors.gray)
    }

    // Card border based on confidence
    val borderWidth = if (confidence == LinkConfidence.HIGH) 2.dp else EInkTheme.Global.borderWidth
    val borderColor = if (confidence == LinkConfidence.HIGH) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(EInkTheme.Global.paddingMedium)
    ) {
        // Row 1: Confidence badge + Day offset
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Confidence badge
            Box(
                modifier = Modifier
                    .background(confidenceBg, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                    .border(
                        EInkTheme.Global.borderWidth,
                        if (confidence == LinkConfidence.HIGH) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                        RoundedCornerShape(EInkTheme.Global.spacingSmall)
                    )
                    .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = confidenceLabel,
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = confidenceTextColor
                    )
                    Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                    Text(
                        text = confidenceScoreText,
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = confidenceTextColor
                    )
                    if (confidence == LinkConfidence.HIGH) {
                        Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = confidenceTextColor,
                            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Day offset badge (if cross-day)
            if (dayOffsetText != null) {
                Box(
                    modifier = Modifier
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                        .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
                ) {
                    Text(
                        text = dayOffsetText,
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

        // Row 2: Side-by-side Note and Event
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            // NOTE Column (left side)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Section label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "NOTE",
                        fontSize = EInkTheme.Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Note title
                Text(
                    text = note.title,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Note date
                Text(
                    text = noteDateText,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
                // Note time + pages
                Text(
                    text = "${formatTime(note.originalTimestamp)} • ${note.pageCount} pg",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }

            // Arrow in center
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "links to",
                tint = EInkTheme.Colors.gray,
                modifier = Modifier
                    .size(EInkTheme.Global.iconSize)
                    .align(Alignment.CenterVertically)
            )

            // EVENT Column (right side)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Section label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EVENT",
                        fontSize = EInkTheme.Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Event title
                Text(
                    text = event.title,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Event date
                Text(
                    text = eventDateText,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
                // Event time
                Text(
                    text = if (event.isAllDay) "All day" else formatTime(event.startTime),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }

        // Row 3: Reasoning (if available)
        if (reasoning.isNotEmpty()) {
            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                    .padding(EInkTheme.Global.paddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = EInkTheme.Colors.gray,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                )
                Text(
                    text = reasoning,
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = EInkTheme.Colors.gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

        // Row 4: Action buttons - 44dp touch targets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Skip button (outlined) - 44dp height
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.gray,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Skip",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.gray
                )
            }

            // Link button (filled) - 44dp height
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onLink),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Link",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.background
                    )
                    Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = EInkTheme.Colors.background,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                }
            }
        }
    }
}

// ============================================================================
// BULK SMART LINK WIZARD
// ============================================================================

/**
 * Wizard state machine for bulk linking process.
 */
private enum class BulkWizardScreen {
    LOADING,       // Unified analysis (heuristic + AI auto-chained)
    NO_EVENTS,     // No calendar events — guide user to connect an account
    SUMMARY,       // Stats + single "Review & Apply" button
    REVIEW,        // Review ALL matches (unified - high/medium/low)
    COMPLETION     // Final stats + close
}

/**
 * Bulk Smart Link Wizard - Analyzes ALL unlinked notes and helps link them
 * to nearby events using the intelligent cross-day matching algorithm.
 *
 * Features:
 * - Progress tracking during analysis
 * - Summary screen with stats and quick actions
 * - Review mode for manual linking decisions
 * - Confirmation dialog for bulk linking
 */
@Composable
fun BulkSmartLinkWizard(
    booxNoteIndexer: BooxNoteIndexer,
    booxNoteRepository: BooxNoteRepository,
    eventRepository: CalendarEventRepository,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit = {},  // Closes wizard AND main dialog
    onLinkChanged: () -> Unit,
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smartLinkEventRepository = remember(context, eventRepository) {
        SmartLinkEventRepository(context, eventRepository)
    }
    val sourceVisibility = remember(sourceFilter) {
        SmartLinkSourceVisibility(
            showLocal = sourceFilter.showLocal,
            showGoogle = sourceFilter.showGoogle,
            showOutlook = sourceFilter.showOutlook
        )
    }

    // Wizard state
    var currentScreen by remember { mutableStateOf(BulkWizardScreen.LOADING) }
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Analyzing notes...") }
    var noEventsPreflight by remember { mutableStateOf<SmartLinkPreflightStatus?>(null) }

    // Analysis results
    var session by remember { mutableStateOf<com.boox.day.booxnotes.BulkSmartLinkSession?>(null) }

    // Review state
    var actedSuggestions by remember { mutableStateOf(setOf<String>()) }
    var linkedCount by remember { mutableStateOf(0) }

    // Apply All success popup state
    var showApplyAllSuccess by remember { mutableStateOf(false) }
    var appliedCount by remember { mutableStateOf(0) }

    var analysisJob by remember { mutableStateOf<Job?>(null) }

    // Start unified analysis when wizard opens (heuristic + AI auto-chained)
    // First check if there are any calendar events to match against
    LaunchedEffect(Unit) {
        analysisJob?.cancel()
        analysisJob = scope.launch {
            try {
                // Pre-check: are there any calendar events?
                val preflightStatus = withContext(Dispatchers.IO) {
                    smartLinkEventRepository.getPreflightStatus(sourceVisibility)
                }
                noEventsPreflight = preflightStatus
                if (preflightStatus.state == SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED ||
                    preflightStatus.state == SmartLinkPreflightState.SERVER_OCR_DISABLED ||
                    preflightStatus.state == SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS ||
                    preflightStatus.state == SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS ||
                    preflightStatus.state == SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS ||
                    preflightStatus.state == SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS
                ) {
                    currentScreen = BulkWizardScreen.NO_EVENTS
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    booxNoteIndexer.analyzeUnified { progressPercent, status ->
                        progress = progressPercent
                        statusText = status
                    }
                }
                session = result
                currentScreen = BulkWizardScreen.SUMMARY
            } catch (e: CancellationException) {
                // User canceled analysis
            }
        }
    }

    // Prevent dismiss during loading
    val canDismiss = currentScreen != BulkWizardScreen.LOADING

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.BooxNotes.dialogWidthFraction)  // 0.92f - wider for e-ink
                    .fillMaxHeight(0.85f)  // Taller to accommodate larger touch targets
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                when (currentScreen) {
                    BulkWizardScreen.LOADING -> {
                        BulkLoadingScreen(
                            progress = progress,
                            statusText = statusText,
                            noticeText = if (noEventsPreflight?.isDeterministicOnly == true) {
                                "Semantic matching is currently unavailable, so Smart Link is using a simpler fallback based mostly on note titles and timing."
                            } else {
                                null
                            },
                            onCancel = {
                                analysisJob?.cancel()
                                onDismiss()
                            }
                        )
                    }

                    BulkWizardScreen.NO_EVENTS -> {
                        NoCalendarEventsScreen(
                            preflightStatus = noEventsPreflight,
                            onClose = onDismiss
                        )
                    }

                    BulkWizardScreen.SUMMARY -> {
                        session?.let { s ->
                            // Calculate total matches (HIGH + MEDIUM + LOW)
                            val totalMatched = s.stats.highConfidenceCount + s.stats.mediumConfidenceCount + s.stats.lowConfidenceCount

                            BulkUnifiedSummaryScreen(
                                stats = s.stats,
                                onApplyAll = if (s.stats.highConfidenceCount > 0) {
                                    {
                                        scope.launch {
                                            appliedCount = booxNoteIndexer.applyHighConfidenceLinks(s)
                                            onLinkChanged()
                                            showApplyAllSuccess = true
                                        }
                                    }
                                } else null,
                                onReviewAndApply = if (totalMatched > 0) {
                                    { currentScreen = BulkWizardScreen.REVIEW }
                                } else null,
                                onClose = onDismiss
                            )
                        }
                    }

                    BulkWizardScreen.REVIEW -> {
                        session?.let { s ->
                            // Calculate best suggestion per note for display
                            val bestPerNote = s.suggestionsMap
                                .filterKeys { noteId -> !actedSuggestions.contains(noteId) }
                                .mapNotNull { (_, suggestions) ->
                                    suggestions.firstOrNull { it.confidence != LinkConfidence.NONE }
                                }
                            val noteDateById = s.unlinkedNotes.associate { it.uniqueId to it.originalDate }
                            // Keep blind bulk apply strict: weaker suggestions must be reviewed note by note.
                            val bulkLinkableSuggestions = bestPerNote.filter { it.confidence == LinkConfidence.HIGH }
                            BulkReviewScreen(
                                session = s,
                                actedSuggestions = actedSuggestions,
                                onLink = { noteId, eventId ->
                                    scope.launch {
                                        val noteDate = noteDateById[noteId]
                                        if (!noteDate.isNullOrBlank()) {
                                            booxNoteRepository.acceptSuggestion(noteId, eventId, noteDate)
                                        } else {
                                            booxNoteRepository.linkNoteToEvent(noteId, eventId, isManual = true)
                                        }
                                        actedSuggestions = actedSuggestions + noteId
                                        linkedCount++
                                        onLinkChanged()
                                    }
                                },
                                onSkip = { noteId, eventId ->
                                    scope.launch {
                                        booxNoteRepository.dismissSuggestion(noteId, eventId)
                                        actedSuggestions = actedSuggestions + noteId
                                    }
                                },
                                onBack = {
                                    currentScreen = BulkWizardScreen.SUMMARY
                                },
                                onPrimaryAction = {
                                    if (bulkLinkableSuggestions.isEmpty()) {
                                        // Nothing to link
                                        if (linkedCount > 0) {
                                            currentScreen = BulkWizardScreen.COMPLETION
                                        } else {
                                            onDismiss()
                                        }
                                    } else {
                                        // Bulk link only strong suggestions; weaker ones still need review.
                                        scope.launch {
                                            bulkLinkableSuggestions.forEach { suggestion ->
                                                booxNoteIndexer.applyCrossDaySuggestion(suggestion)
                                            }
                                            booxNoteRepository.invalidateLinksCache()
                                            linkedCount += bulkLinkableSuggestions.size
                                            onLinkChanged()
                                            currentScreen = BulkWizardScreen.COMPLETION
                                        }
                                    }
                                },
                                primaryActionLabel = if (bulkLinkableSuggestions.isEmpty()) "Done" else "Link All (${bulkLinkableSuggestions.size})",
                                primaryActionEnabled = true,
                                title = "Review Matches",
                                filter = { it.confidence != LinkConfidence.NONE },  // All confidence levels except NONE
                                showNoMatchSection = false,
                                showOnlyBestPerNote = true  // Show only best match per note
                            )
                        }
                    }

                    BulkWizardScreen.COMPLETION -> {
                        BulkCompletionScreen(
                            linkedCount = linkedCount,
                            onClose = onDismiss
                        )
                    }
                }
            }

            // Apply All Success Popup - E-Ink safe (no scrim)
            if (showApplyAllSuccess) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .background(EInkTheme.Colors.background, RoundedCornerShape(12.dp))
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "All $appliedCount matches applied!",
                            fontSize = EInkTheme.Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EInkTheme.Global.touchTarget)
                                .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                                .noRippleClickable {
                                    showApplyAllSuccess = false
                                    onDismissAll()  // Close wizard AND main dialog
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Close",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.background
                            )
                        }
                    }
                }
            }
        }

    }
}

/**
 * Empty state screen when Smart Link has no usable event source.
 * Uses preflight status so guidance matches the actual reason.
 */
@Composable
private fun NoCalendarEventsScreen(
    preflightStatus: SmartLinkPreflightStatus?,
    onClose: () -> Unit
) {
    val titleText = when (preflightStatus?.state) {
        SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED -> "Smart Link Is Temporarily Disabled"
        SmartLinkPreflightState.SERVER_OCR_DISABLED -> "Boox Note Scanning Is Temporarily Disabled"
        SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS -> "Current Filters Hide All Events"
        SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS -> "Only All-Day Events Are Available"
        SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS -> "No Synced Events Yet"
        else -> "No Calendar Events Found"
    }
    val bodyText = when (preflightStatus?.state) {
        SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED ->
            "Smart Link is currently disabled by the server configuration, so Boox Day cannot run note matching right now. Your existing note links are still safe."
        SmartLinkPreflightState.SERVER_OCR_DISABLED ->
            "Boox note scanning is currently disabled by the server configuration, so Smart Link cannot read note content well enough to build new matches right now. Your existing note links are still safe."
        SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS ->
            "Your current Local, Google, and Outlook event filters are hiding every event Smart Link could use. Re-enable at least one source that has events, then try again."
        SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS ->
            "Boox Day can see calendar events, but every available event is all-day. Smart Link only matches Boox notes against timed events with a real start time."
        SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS ->
            "Smart Link can see your connected calendar account, but Boox Day does not have any synced events available yet. Open Calendar Accounts, run sync, and make sure at least one calendar is enabled."
        else ->
            "Smart Link works by matching your Boox Notes to calendar events. To get started, either connect a Google or Outlook account, or create local events directly in Boox Day."
    }
    val hintText = when (preflightStatus?.state) {
        SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED ->
            "Try again later or keep using manual note linking"
        SmartLinkPreflightState.SERVER_OCR_DISABLED ->
            "Try again later or keep using manual note linking until note scanning is re-enabled"
        SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS ->
            "Events Grid  >  Re-enable Local / Google / Outlook sources"
        SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS ->
            "Create or sync at least one timed event with a real start time"
        SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS ->
            "Settings  >  Calendar Accounts  >  Sync / Enable Calendars"
        else ->
            "Settings  >  Calendar Accounts  >  Add Account"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Event,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = EInkTheme.Colors.gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = titleText,
            fontSize = EInkTheme.Typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = bodyText,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = hintText,
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                .noRippleClickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Got it",
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
    }
}

/**
 * Loading screen - shows progress while analyzing notes.
 */
@Composable
private fun BulkLoadingScreen(
    progress: Float,
    statusText: String,
    noticeText: String? = null,
    onCancel: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                tint = EInkTheme.Colors.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Analyzing Notes",
                fontSize = EInkTheme.Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .background(EInkTheme.Colors.lightGray, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        if (!noticeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = noticeText,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(EInkTheme.Global.touchTarget)
                    .background(EInkTheme.Colors.background)
                    .border(2.dp, EInkTheme.Colors.primary)
                    .noRippleClickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stop Analysis",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
        }

        // Fun quote - rotates every 5 seconds
        Spacer(modifier = Modifier.height(24.dp))
        com.boox.day.ui.components.RotatingFunMessage(
            messageProvider = { AiFeedbackMessages.getRandomNotesMatchingMessage() },
            intervalMs = 5000L,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Summary screen - shows stats and action buttons.
 */
@Composable
private fun BulkSummaryScreen(
    stats: com.boox.day.booxnotes.BulkLinkStats,
    onApproveHighConfidence: () -> Unit,
    onReviewAll: () -> Unit,
    onAiAnalyze: (() -> Unit)? = null,  // Only shown when noMatchCount > 0
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bulk Smart Link",
                    fontSize = EInkTheme.Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stats section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Analysis Results",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatRow("Total unlinked notes", stats.totalUnlinked.toString())
            Divider(color = EInkTheme.Colors.dividerHairline, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Notes with HIGH match", stats.highConfidenceCount.toString(), bold = true)
            StatRow("Notes with MEDIUM match", stats.mediumConfidenceCount.toString())
            StatRow("Notes with LOW match", stats.lowConfidenceCount.toString())
            StatRow("Notes with no match", stats.noMatchCount.toString(), color = EInkTheme.Colors.gray)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primary action - Review and apply high confidence (consolidated)
            if (stats.highConfidenceCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                        .noRippleClickable(onClick = onApproveHighConfidence),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Review & Apply High Confidence (${stats.highConfidenceCount})",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.background
                    )
                }
            }

            // Secondary action - Review All
            val hasAnySuggestions = stats.highConfidenceCount + stats.mediumConfidenceCount + stats.lowConfidenceCount > 0
            if (hasAnySuggestions || stats.noMatchCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                        .noRippleClickable(onClick = onReviewAll),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Review All Suggestions",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            // AI Analyze button - only show if there are unmatched notes
            if (onAiAnalyze != null && stats.noMatchCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(4.dp))
                        .noRippleClickable(onClick = onAiAnalyze),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Analyze ${stats.noMatchCount} Unmatched",
                            fontSize = EInkTheme.Typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }

        // Empty state
        if (stats.totalUnlinked == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All notes already linked!",
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    bold: Boolean = false,
    color: Color = EInkTheme.Colors.darkGray
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = color
        )
        Text(
            text = value,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) EInkTheme.Colors.primary else color
        )
    }
}

/**
 * Visual progress bar for match categories with icon and percentage.
 */
@Composable
private fun MatchCategoryBar(
    label: String,
    count: Int,
    total: Int,
    icon: ImageVector
) {
    val percentage = if (total > 0) count.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray
                )
            }
            Text(
                text = "$count (${(percentage * 100).toInt()}%)",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = EInkTheme.Colors.primary,
            backgroundColor = EInkTheme.Colors.lightGray
        )
    }
}

/**
 * Unified Summary Screen - Enhanced visual interface for analysis results.
 * Shows stats with progress bars and side-by-side Apply All / Review buttons.
 */
@Composable
private fun BulkUnifiedSummaryScreen(
    stats: com.boox.day.booxnotes.BulkLinkStats,
    onApplyAll: (() -> Unit)? = null,  // Apply all matches immediately
    onReviewAndApply: (() -> Unit)? = null,  // Go to review screen
    onClose: () -> Unit
) {
    val totalMatched = stats.highConfidenceCount + stats.mediumConfidenceCount + stats.lowConfidenceCount

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Analysis Complete",
                    fontSize = EInkTheme.Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total notes analyzed - prominent
        Text(
            text = "${stats.totalUnlinked} notes analyzed",
            fontSize = EInkTheme.Typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.Colors.darkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Visual progress bars for each category
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (stats.highConfidenceCount > 0) {
                MatchCategoryBar(
                    label = "Perfect Matches",
                    count = stats.highConfidenceCount,
                    total = stats.totalUnlinked,
                    icon = Icons.Default.Star
                )
            }

            if (stats.mediumConfidenceCount > 0) {
                MatchCategoryBar(
                    label = "Good Matches",
                    count = stats.mediumConfidenceCount,
                    total = stats.totalUnlinked,
                    icon = Icons.Default.ThumbUp
                )
            }

            if (stats.lowConfidenceCount > 0) {
                MatchCategoryBar(
                    label = "Worth Reviewing",
                    count = stats.lowConfidenceCount,
                    total = stats.totalUnlinked,
                    icon = Icons.AutoMirrored.Filled.Help
                )
            }

            if (stats.noMatchCount > 0) {
                MatchCategoryBar(
                    label = "Need AI Help",
                    count = stats.noMatchCount,
                    total = stats.totalUnlinked,
                    icon = Icons.Default.Psychology
                )
            }
        }

        // Explanatory text
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Our intelligent engine uses heuristics and fuzzy logic to analyze titles, dates, and semantic context. Combined with AI-powered reasoning, it uncovers meaningful connections between your notes and calendar events that simple keyword matching would miss.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(111.dp))

        // Action buttons - side by side
        if (totalMatched > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Apply All button (left, outlined)
                if (onApplyAll != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                            .noRippleClickable(onClick = onApplyAll),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Apply All",
                            fontSize = EInkTheme.Typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }

                // Review Matches button (right, filled primary)
                if (onReviewAndApply != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                            .noRippleClickable(onClick = onReviewAndApply),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Review Matches",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.background
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = EInkTheme.Colors.background
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Done button (tertiary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(4.dp))
                .noRippleClickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Done",
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.gray
            )
        }

        // Empty state
        if (stats.totalUnlinked == 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All notes already linked!",
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
        }
    }
}

/**
 * AI Analyzing screen - shows progress while LLM processes notes.
 */
@Composable
private fun BulkAiAnalyzingScreen(
    progress: Float,
    statusText: String
) {
    // Fun quote - picked once when screen appears
    val funQuote = remember { AiFeedbackMessages.getRandomGeneralMessage() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = EInkTheme.Colors.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI Analysis",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusText,
            fontSize = 14.sp,
            color = EInkTheme.Colors.gray
        )
        Spacer(modifier = Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(6.dp),
            color = EInkTheme.Colors.primary,
            backgroundColor = EInkTheme.Colors.lightGray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Fun quote while waiting
        Text(
            text = "\"$funQuote\"",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * AI Results screen - shows LLM match results and actions.
 */
@Composable
private fun BulkAiResultsScreen(
    results: List<BooxNoteIndexer.LlmMatchResult>,
    onApproveHighConfidence: () -> Unit,
    onReviewMatches: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    // Count matches by confidence level
    val highConfidenceCount = results.count { it.matchedEventId != null && it.confidence >= 0.7f }
    val mediumConfidenceCount = results.count { it.matchedEventId != null && it.confidence >= 0.4f && it.confidence < 0.7f }
    val lowConfidenceCount = results.count { it.matchedEventId != null && it.confidence < 0.4f }
    val noNearbyEventsCount = results.count { it.matchedEventId == null }
    val totalMatched = highConfidenceCount + mediumConfidenceCount + lowConfidenceCount

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Analysis Complete",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Results stats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(EInkTheme.Global.padding)
        ) {
            Text(
                text = "AI Match Results",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
            StatRow("High confidence (≥70%)", highConfidenceCount.toString(), bold = true)
            StatRow("Medium confidence (40-70%)", mediumConfidenceCount.toString())
            if (lowConfidenceCount > 0) {
                StatRow("Low confidence (<40%)", lowConfidenceCount.toString())
            }
            if (noNearbyEventsCount > 0) {
                Divider(color = EInkTheme.Colors.dividerHairline, modifier = Modifier.padding(vertical = EInkTheme.Global.paddingSmall))
                StatRow("No nearby events (±7 days)", noNearbyEventsCount.toString(), color = EInkTheme.Colors.gray)
            }
        }

        // Explanation for no nearby events
        if (noNearbyEventsCount > 0) {
            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
            Text(
                text = "Notes with no nearby events cannot be auto-matched. Consider linking them manually.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                modifier = Modifier.padding(horizontal = EInkTheme.Global.paddingSmall)
            )
        }

        // Sample reasoning (show first confident match's reasoning)
        val sampleMatch = results.firstOrNull { it.matchedEventId != null && it.confidence >= 0.7f }
        if (sampleMatch != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Sample AI Reasoning:",
                    fontSize = EInkTheme.Typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${sampleMatch.reasoning}\"",
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            if (highConfidenceCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .noRippleClickable(onClick = onApproveHighConfidence),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Approve & Apply High Confidence ($highConfidenceCount)",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.background
                    )
                }
            }

            // Only show Review AI Matches if there are matches to review
            if (totalMatched > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .noRippleClickable(onClick = onReviewMatches),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Review AI Matches ($totalMatched)",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget)
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Back to Summary",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }
        }

        // Empty state
        if (results.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.Global.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(EInkTheme.BooxNotes.emptyStateIconSize),
                    tint = EInkTheme.Colors.gray
                )
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                Text(
                    text = "AI couldn't find any matches",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                Text(
                    text = "These notes may not have related events",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

/**
 * E-INK OPTIMIZED Review Suggestions Screen.
 *
 * Features:
 * - Legend section explaining confidence badges
 * - Date section headers with suggestion counts
 * - Larger spacing between day groups
 * - 44dp touch target for Done button
 * - All fonts from EInkTheme
 */
@Composable
private fun BulkReviewScreen(
    session: com.boox.day.booxnotes.BulkSmartLinkSession,
    actedSuggestions: Set<String>,
    onLink: (noteId: String, eventId: String) -> Unit,
    onSkip: (noteId: String, eventId: String) -> Unit,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionLabel: String = "Done",
    primaryActionEnabled: Boolean = true,
    title: String = "Review Suggestions",
    subtitleOverride: String? = null,
    filter: (CrossDayLinkSuggestion) -> Boolean = { true },
    showNoMatchSection: Boolean = true,
    showOnlyBestPerNote: Boolean = false  // When true, only show best suggestion per note
) {
    // Group suggestions by note date
    val groupedSuggestions = remember(session, actedSuggestions, filter, showOnlyBestPerNote) {
        val filteredMap = session.suggestionsMap
            .filterKeys { noteId -> !actedSuggestions.contains(noteId) }

        val suggestions = if (showOnlyBestPerNote) {
            // Only BEST (first) suggestion per note that passes filter
            filteredMap.mapNotNull { (_, suggestions) ->
                suggestions.firstOrNull(filter)
            }
        } else {
            // All suggestions that pass filter
            filteredMap.flatMap { (_, suggestions) -> suggestions.filter(filter) }
        }

        suggestions
            .groupBy { it.note.originalDate }
            .toSortedMap(compareByDescending { it })
    }

    // Notes with no matches
    val noMatchNotes = remember(session, actedSuggestions, showNoMatchSection) {
        if (!showNoMatchSection) return@remember emptyList()
        session.unlinkedNotes.filter { note ->
            !actedSuggestions.contains(note.uniqueId) &&
                session.suggestionsMap[note.uniqueId].isNullOrEmpty()
        }
    }

    // Count remaining items
    val totalSuggestions = groupedSuggestions.values.sumOf { it.size }
    val notesWithSuggestions = groupedSuggestions.values.flatten().map { it.note.uniqueId }.distinct().size

    // Legend expanded state
    var legendExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ═══════════════════════════════════════════════════════════════════════
        // HEADER: Title + Done button
        // ═══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = EInkTheme.Global.paddingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button + Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.Global.touchTarget)
                        .noRippleClickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = EInkTheme.Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = subtitleOverride ?: buildString {
                            if (showOnlyBestPerNote) {
                                // Simplified subtitle for best-per-note mode
                                append("$notesWithSuggestions notes to review")
                            } else {
                                append("$notesWithSuggestions notes • $totalSuggestions suggestions")
                            }
                            if (showNoMatchSection && noMatchNotes.isNotEmpty()) {
                                append(" • ${noMatchNotes.size} no match")
                            }
                        },
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            // Primary action button
            Box(
                modifier = Modifier
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        if (primaryActionEnabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(enabled = primaryActionEnabled, onClick = onPrimaryAction)
                    .padding(horizontal = EInkTheme.Global.paddingLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = primaryActionLabel,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (primaryActionEnabled) EInkTheme.Colors.background else EInkTheme.Colors.gray
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // LEGEND: Expandable section explaining confidence badges
        // ═══════════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(
                    EInkTheme.Global.borderWidth,
                    EInkTheme.Colors.lightGray,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
        ) {
            // Legend header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { legendExpanded = !legendExpanded }
                    .padding(EInkTheme.Global.paddingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = EInkTheme.Colors.darkGray,
                    modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                )
                Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
                Text(
                    text = "How to read suggestions",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.darkGray
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (legendExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (legendExpanded) "Collapse" else "Expand",
                    tint = EInkTheme.Colors.gray,
                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                )
            }

            // Legend content - expandable
            if (legendExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = EInkTheme.Global.paddingMedium,
                            end = EInkTheme.Global.paddingMedium,
                            bottom = EInkTheme.Global.paddingMedium
                        ),
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                ) {
                    // HIGH confidence
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
                        ) {
                            Text(
                                text = "HIGH ✓",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.background
                            )
                        }
                        Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
                        Text(
                            text = "Strong match — recommended to link",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }

                    // MEDIUM confidence
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                                .border(
                                    EInkTheme.Global.borderWidth,
                                    EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(EInkTheme.Global.spacingSmall)
                                )
                                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
                        ) {
                            Text(
                                text = "MED",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
                        Text(
                            text = "Possible match — review before linking",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }

                    // LOW confidence
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.spacingSmall))
                                .border(
                                    EInkTheme.Global.borderWidth,
                                    EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(EInkTheme.Global.spacingSmall)
                                )
                                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
                        ) {
                            Text(
                                text = "LOW",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.gray
                            )
                        }
                        Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
                        Text(
                            text = "Weak match — likely unrelated",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

        // ═══════════════════════════════════════════════════════════════════════
        // SUGGESTIONS LIST
        // ═══════════════════════════════════════════════════════════════════════
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Grouped by date with better headers
            groupedSuggestions.forEach { (date, suggestions) ->
                // Date section header
                item(key = "header_$date") {
                    val formattedDate = try {
                        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                    } catch (e: Exception) { date }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = EInkTheme.Global.paddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Divider line
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = EInkTheme.Colors.lightGray,
                            thickness = EInkTheme.Global.borderWidth
                        )
                        // Date text with count
                        Text(
                            text = "  $formattedDate (${suggestions.size})  ",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.darkGray
                        )
                        // Divider line
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = EInkTheme.Colors.lightGray,
                            thickness = EInkTheme.Global.borderWidth
                        )
                    }
                }

                // Suggestion cards for this date
                items(
                    items = suggestions,
                    key = { "${it.note.uniqueId}_${it.event.id}" }
                ) { suggestion ->
                    CrossDaySmartLinkSuggestionCard(
                        suggestion = suggestion,
                        onLink = { onLink(suggestion.note.uniqueId, suggestion.event.id) },
                        onSkip = { onSkip(suggestion.note.uniqueId, suggestion.event.id) }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // NO NEARBY EVENTS SECTION
            // ═══════════════════════════════════════════════════════════════════
            if (showNoMatchSection && noMatchNotes.isNotEmpty()) {
                item(key = "no_match_header") {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.padding))

                    // Section header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .padding(EInkTheme.Global.paddingMedium)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = EInkTheme.Colors.gray,
                                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                            )
                            Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))
                            Text(
                                text = "No Nearby Events (${noMatchNotes.size})",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                        Text(
                            text = "These notes have no calendar events within ±7 days. They cannot be linked automatically.",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                items(
                    items = noMatchNotes,
                    key = { "nomatch_${it.uniqueId}" }
                ) { note ->
                    NoMatchNoteRow(note = note)
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // EMPTY STATE (all reviewed)
            // ═══════════════════════════════════════════════════════════════════
            if (groupedSuggestions.isEmpty() && (!showNoMatchSection || noMatchNotes.isEmpty())) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(EInkTheme.Global.paddingLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(EInkTheme.BooxNotes.emptyStateIconSize)
                            )
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                            Text(
                                text = "All suggestions reviewed!",
                                fontSize = EInkTheme.Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                            Text(
                                text = "Tap Done to finish",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * E-INK OPTIMIZED Row for notes with no nearby events.
 */
@Composable
private fun NoMatchNoteRow(note: BooxNoteIndex) {
    val dateText = try {
        LocalDate.parse(note.originalDate).format(DateTimeFormatter.ofPattern("MMM d"))
    } catch (e: Exception) { note.originalDate }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Note icon
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(EInkTheme.Global.iconSize)
        )
        Spacer(modifier = Modifier.width(EInkTheme.Global.paddingSmall))

        // Note info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$dateText • ${note.pageCount} pages",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray
            )
        }

        // No events indicator
        Text(
            text = "No events",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
    }
}

/**
 * Completion screen - shows success message.
 */
@Composable
private fun BulkCompletionScreen(
    linkedCount: Int,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (linkedCount > 0) "Linked $linkedCount note${if (linkedCount > 1) "s" else ""}!" else "Done!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (linkedCount > 0) "Notes are now connected to their events" else "No changes made",
            fontSize = 14.sp,
            color = EInkTheme.Colors.darkGray
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                .noRippleClickable(onClick = onClose)
                .padding(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Close",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.background
            )
        }
    }
}

/**
 * Collapsible timeline mini-view showing events and notes on a time axis.
 * DEPRECATED: Replaced by DayOverviewOverlay, but kept for reference.
 * Collapsed: shows "Timeline ▼" hint
 * Expanded: shows hourly timeline with events and note markers
 */
@Composable
private fun TimelineMiniView(
    notes: List<BooxNoteIndex>,
    events: List<CalendarEvent>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNoteSelected: (BooxNoteIndex) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EInkTheme.Colors.borderStrong)
            .background(EInkTheme.Colors.offWhite)
    ) {
        // Toggle header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Timeline",
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = EInkTheme.Colors.gray,
                modifier = Modifier.size(16.dp)
            )
        }

        // Expanded timeline content
        if (expanded) {
            Divider(color = EInkTheme.Colors.dividerHairline, thickness = 1.dp)

            // Calculate time range for the day (use events and notes to determine range)
            val timeItems = remember(notes, events) {
                buildTimelineItems(notes, events)
            }

            if (timeItems.isEmpty()) {
                Text(
                    text = "No events or notes",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    timeItems.forEach { item ->
                        TimelineRow(
                            item = item,
                            onNoteClick = { note -> onNoteSelected(note) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data class for timeline items (either an event or a standalone note)
 */
private data class TimelineItem(
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEvent: Boolean,
    val note: BooxNoteIndex? = null,  // For clickable note items
    val notesCount: Int = 0  // For events with linked notes
)

/**
 * Build sorted list of timeline items from events and notes
 */
private fun buildTimelineItems(notes: List<BooxNoteIndex>, events: List<CalendarEvent>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()

    // Add events (non all-day)
    events.filter { !it.isAllDay }.forEach { event ->
        val instant = java.time.Instant.ofEpochMilli(event.startTime)
        val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        items.add(TimelineItem(
            hour = localTime.hour,
            minute = localTime.minute,
            label = event.title,
            isEvent = true,
            notesCount = 0  // Could count linked notes here
        ))
    }

    // Add notes (grouped by hour to avoid clutter)
    val notesByHour = notes.groupBy { note ->
        val instant = java.time.Instant.ofEpochMilli(note.originalTimestamp)
        instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime().hour
    }

    notesByHour.forEach { (hour, hourNotes) ->
        if (hourNotes.size == 1) {
            val note = hourNotes.first()
            val instant = java.time.Instant.ofEpochMilli(note.originalTimestamp)
            val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            items.add(TimelineItem(
                hour = localTime.hour,
                minute = localTime.minute,
                label = note.title,
                isEvent = false,
                note = note
            ))
        } else {
            // Multiple notes in same hour - show as group
            items.add(TimelineItem(
                hour = hour,
                minute = 0,
                label = "${hourNotes.size} notes",
                isEvent = false,
                note = hourNotes.first(),  // Click selects first
                notesCount = hourNotes.size
            ))
        }
    }

    // Sort by time
    return items.sortedWith(compareBy({ it.hour }, { it.minute }))
}

/**
 * Single row in the timeline
 */
@Composable
private fun TimelineRow(
    item: TimelineItem,
    onNoteClick: (BooxNoteIndex) -> Unit
) {
    val timeStr = String.format("%d:%02d", if (item.hour == 0) 12 else if (item.hour > 12) item.hour - 12 else item.hour, item.minute)
    val amPm = if (item.hour < 12) "a" else "p"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!item.isEvent && item.note != null) {
                    Modifier.noRippleClickable { onNoteClick(item.note) }
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time column
        Text(
            text = "$timeStr$amPm",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.gray,
            modifier = Modifier.width(36.dp)
        )

        // Indicator
        Text(
            text = if (item.isEvent) "─" else "•",
            fontSize = EInkTheme.Typography.labelSmall,
            color = if (item.isEvent) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
            modifier = Modifier.width(12.dp)
        )

        // Label
        Text(
            text = if (item.label.length > 16) item.label.take(16) + "…" else item.label,
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = if (item.isEvent) FontWeight.Medium else FontWeight.Normal,
            color = if (item.isEvent) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Note count badge for grouped notes
        if (item.notesCount > 1) {
            Text(
                text = "(${item.notesCount})",
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

/**
 * Quick-link row for one-tap event linking.
 * E-ink optimized: 44dp touch target, clear label and time.
 */
@Composable
private fun QuickLinkRow(
    eventTitle: String,
    eventTime: String,
    onClick: () -> Unit,
    isMoreRow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isMoreRow) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary
    val borderColor = if (isMoreRow) EInkTheme.Colors.lightGray else EInkTheme.Colors.darkGray
    val leadingIcon = if (isMoreRow) Icons.Default.MoreHoriz else Icons.Default.Event

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget)
            .border(EInkTheme.Global.borderWidth, borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = if (isMoreRow) EInkTheme.Colors.gray else EInkTheme.Colors.primary,
            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = eventTitle,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (isMoreRow) FontWeight.Medium else FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!isMoreRow && eventTime.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = eventTime,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }
        if (isMoreRow) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EInkTheme.Colors.gray,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
    }
}

/**
 * Parse page IDs from JSON array string.
 */
private fun parsePageIds(json: String): List<String> {
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Format timestamp to time string.
 */
private fun formatTime(timeMillis: Long): String {
    return try {
        val instant = java.time.Instant.ofEpochMilli(timeMillis)
        val localTime = instant
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        localTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Format date string to display format (e.g., "Nov 20, 2025")
 */
private fun formatDateForDisplay(dateStr: String): String {
    return try {
        val localDate = java.time.LocalDate.parse(dateStr)
        localDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        dateStr
    }
}

/**
 * Format date string to short display format (e.g., "Dec 30") - for cross-day links
 */
private fun formatDateShort(dateStr: String): String {
    return try {
        val localDate = java.time.LocalDate.parse(dateStr)
        localDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    } catch (e: Exception) {
        dateStr
    }
}

/**
 * Compact date selector dropdown for quick navigation between dates with notes.
 * Uses custom Popup instead of DropdownMenu to properly support scrollable content.
 * DropdownMenu uses intrinsic width measurement which is incompatible with LazyColumn.
 */
@Composable
private fun DateSelectorDropdown(
    currentDate: String,
    datesWithNotes: List<DateWithNoteCount>,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Trigger button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EInkTheme.Colors.darkGray)
                .noRippleClickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select date with Notes",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select date",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(22.dp)
            )
        }

        // Custom popup dropdown (not DropdownMenu which has intrinsic measurement issues)
        if (expanded) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, EInkTheme.BooxNotes.dateSelectorDropdownOffsetY),
                onDismissRequest = { expanded = false },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                // Dropdown content with fixed width and max height
                Card(
                    modifier = Modifier
                        .width(EInkTheme.BooxNotes.dateSelectorDropdownWidth)
                        .heightIn(max = 280.dp),
                    backgroundColor = EInkTheme.PopupMenu.background,
                    elevation = 0.dp,
                    border = BorderStroke(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                ) {
                    if (datesWithNotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(EInkTheme.Global.padding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No dates with notes",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    } else {
                        LazyColumn {
                            items(datesWithNotes) { dateInfo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .noRippleClickable {
                                            onDateSelected(dateInfo.date)
                                            expanded = false
                                        }
                                        .background(
                                            if (dateInfo.date == currentDate) EInkTheme.Colors.offWhite else Color.Transparent
                                        )
                                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDateForDisplay(dateInfo.date),
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = if (dateInfo.date == currentDate) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                                        fontWeight = if (dateInfo.date == currentDate) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${dateInfo.noteCount} note${if (dateInfo.noteCount != 1) "s" else ""}",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }

                                // Divider between items (except last)
                                if (dateInfo != datesWithNotes.last()) {
                                    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inline date picker for selecting a different day's events.
 * Simple month-view calendar with navigation.
 */
@Composable
private fun InlineDatePicker(
    onDateSelected: (String) -> Unit,
    onCancel: () -> Unit,
    initialDate: String = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
) {
    var currentMonth by remember {
        mutableStateOf(
            try {
                java.time.YearMonth.parse(initialDate.substring(0, 7))
            } catch (e: Exception) {
                java.time.YearMonth.now()
            }
        )
    }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7  // Sunday = 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EInkTheme.Colors.borderStrong)
            .padding(12.dp)
    ) {
        // Month navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { currentMonth = currentMonth.minusMonths(1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = EInkTheme.Colors.primary
                )
            }

            Text(
                text = currentMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )

            IconButton(
                onClick = { currentMonth = currentMonth.plusMonths(1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = EInkTheme.Colors.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val totalCells = firstDayOfWeek + daysInMonth
        val weeks = (totalCells + 6) / 7

        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNum = cellIndex - firstDayOfWeek + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                if (dayNum in 1..daysInMonth) {
                                    Modifier.noRippleClickable {
                                        val selectedDate = currentMonth.atDay(dayNum)
                                        onDateSelected(selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            Text(
                                text = dayNum.toString(),
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cancel button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Cancel",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray,
                modifier = Modifier
                    .noRippleClickable { onCancel() }
                    .padding(8.dp)
            )
        }
    }
}
