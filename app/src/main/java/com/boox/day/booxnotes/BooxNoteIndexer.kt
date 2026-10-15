package com.boox.day.booxnotes

import android.content.Context
import android.util.Log
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.api.Modality
import com.boox.day.api.ModelConfigProvider
import com.boox.day.ai.JsonExtractor
import com.boox.day.data.db.BooxNoteLink
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.BooxNoteSemanticAggregate
import com.boox.day.data.db.BooxNoteSemanticAggregateBuilder
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.Kv
import com.boox.day.data.db.KvRepository
import com.boox.day.data.db.SmartLinkEventRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.EntityType
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ocr.semantic.classification.AbbreviationsDictionary
import com.boox.day.ocr.semantic.classification.ContextDisambiguator
import com.boox.day.ocr.semantic.classification.DictionaryLoader
import com.boox.day.ocr.semantic.classification.LanguageDictionaries
import com.boox.day.ocr.semantic.classification.OcrCorrector
import com.boox.day.ocr.semantic.model.SemanticProfile
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * LinkConfidence - Confidence levels for note-to-event auto-linking.
 *
 * HIGH (≥80): Auto-link silently - strong match
 * MEDIUM (50-79): Auto-link but surface for optional review
 * LOW (20-49): Don't auto-link, show as suggestion only
 * NONE (<20): No meaningful relationship
 */
enum class LinkConfidence(val minScore: Int, val maxScore: Int) {
    HIGH(80, Int.MAX_VALUE),    // Auto-link silently
    MEDIUM(50, 79),             // Auto-link, surface for review
    LOW(20, 49),                // Suggest only, don't auto-link
    NONE(0, 19);                // No relationship

    companion object {
        fun fromScore(score: Int): LinkConfidence = when {
            score >= HIGH.minScore -> HIGH
            score >= MEDIUM.minScore -> MEDIUM
            score >= LOW.minScore -> LOW
            else -> NONE
        }
    }
}

/**
 * Match reasons for display in UI (explains why a note matched an event).
 *
 * IMPORTANT: These are distinct match types - do not conflate them!
 * - TITLE_MATCH: Exact word overlap between note title and event title
 * - SEMANTIC_GROUP_MATCH: Note and event share domain vocabulary (e.g., both are LDS-related)
 * - SEMANTIC_CONTEXT_MATCH: Semantic scoring aligned the note and event beyond simple title overlap
 * - AI_SUGGESTED_MATCH: AI/LLM determined these are related
 */
enum class MatchReason {
    // Content-based matches (Jan 2026 - Fix #6: distinct types)
    TITLE_MATCH,           // Exact word match: note title contains event keywords
    SEMANTIC_GROUP_MATCH,  // Domain vocabulary overlap: both share terms from same semantic group
    SEMANTIC_CONTEXT_MATCH,// Semantic scoring aligned note and event context
    AI_SUGGESTED_MATCH,    // AI/LLM suggested match

    // Temporal matches
    DURING_EVENT,          // Note created during event
    SHORTLY_AFTER_EVENT,   // Note created within grace period after event
    SHORTLY_BEFORE_EVENT,  // Note created within grace period before event

    // Context bonuses
    EVENING_EVENT,         // Event is after 5 PM (extended grace period)
    ONLY_EVENT_OF_DAY,     // Only timed event on this day
    LONG_EVENT,            // Event is > 2 hours long

    // Cross-day matches
    WEEKDAY_PATTERN,       // Same day of week (recurring pattern)
    SPARSE_CONTEXT,        // Few events in window (stronger signal)
    MORNING_FOLLOWUP,      // Morning note + previous evening event

    // Negative signals (penalties)
    TEST_CONTENT_PENALTY   // Note or event contains test/placeholder indicators
}

/**
 * Semantic match reasons for hybrid scoring (Phase 4).
 * Explains WHY semantic affinity was calculated between note and event profiles.
 */
enum class SemanticMatchReason {
    DOMAIN_MATCH,           // Same domain (e.g., both FINANCE)
    INTENT_COMPATIBLE,      // Compatible intents (e.g., MEETING_NOTES + event)
    ENTITY_PERSON_MATCH,    // Shared person entity
    ENTITY_ORG_MATCH,       // Shared organization entity
    ENTITY_LOCATION_MATCH,  // Shared location entity
    KEYWORD_OVERLAP,        // Significant keyword overlap
    KEY_PHRASE_MATCH,       // Key phrase found in event
    ACTION_ITEM_MATCH,      // Action item matches event
    COOCCURRENCE_BOOST,     // Entities frequently appear together
    SYNOPSIS_SIMILARITY     // Synopsis matches event title
}

// ═══════════════════════════════════════════════════════════════════════════
// CROSS-DAY SMART LINK MODELS (Dec 2025)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Type of cross-day suggestion relationship.
 * Used to explain WHY a note might match an event on a different day.
 */
enum class SuggestionType {
    SAME_DAY,       // Note and event on same day (traditional matching)
    FOLLOW_UP,      // Note created AFTER event (next day review/follow-up notes)
    PREP_NOTES,     // Note created BEFORE event (preparation/agenda notes)
    LATE_CAPTURE    // Note created much later (catch-up documentation)
}

/**
 * Cross-day link suggestion with relationship context.
 * Enhanced version of LinkSuggestion that supports matching across day boundaries.
 */
data class CrossDayLinkSuggestion(
    val note: BooxNoteIndex,
    val event: CalendarEvent,
    val dayOffset: Int,              // -3 to +3 (negative = event is before note's date)
    val suggestionType: SuggestionType,
    val confidence: LinkConfidence,
    val confidenceScore: Int,
    val reasoning: String,           // Human-readable explanation (e.g., "Likely follow-up notes")
    val matchReasons: List<MatchReason>,
    val dataQualityFlags: List<SmartLinkDataQualityFlag> = emptyList()
)

/**
 * Session state for bulk smart linking.
 * Contains analysis results for ALL unlinked notes.
 */
data class BulkSmartLinkSession(
    val unlinkedNotes: List<BooxNoteIndex>,
    val suggestionsMap: Map<String, List<CrossDayLinkSuggestion>>,  // noteId -> suggestions
    val stats: BulkLinkStats
)

/**
 * Statistics for bulk smart link analysis.
 */
data class BulkLinkStats(
    val totalUnlinked: Int,
    val highConfidenceCount: Int,
    val mediumConfidenceCount: Int,
    val lowConfidenceCount: Int,
    val noMatchCount: Int
)

/**
 * Result of scoring a note against an event.
 */
data class ScoredMatch(
    val eventId: String,
    val eventTitle: String,
    val score: Int,
    val confidence: LinkConfidence,
    val reasons: List<MatchReason>,
    val dataQualityFlags: List<SmartLinkDataQualityFlag> = emptyList()
)

/**
 * Hybrid scoring result combining temporal and semantic signals (Phase 4).
 *
 * Formula: finalScore = temporalScore × (1.0 + semanticAffinity × SEMANTIC_WEIGHT + cooccurrenceBoost + cohesionBoost)
 *
 * Where:
 * - temporalScore: 0-180 points (existing scoring)
 * - semanticAffinity: 0.0-1.0 (semantic profile matching)
 * - SEMANTIC_WEIGHT: 0.8 (semantic can boost score by up to 80%)
 * - cooccurrenceBoost: 0.0-0.15 (entity co-occurrence boost, Phase 5)
 * - cohesionBoost: 0.0-0.20 (multi-note keyword cohesion boost, Phase 8)
 *
 * Example: temporalScore=60, semanticAffinity=0.5 → 60 × (1 + 0.5 × 0.8) = 60 × 1.4 = 84
 */
data class HybridScoredMatch(
    val eventId: String,
    val eventTitle: String,
    val temporalScore: Int,
    val semanticAffinity: Float,
    val cooccurrenceBoost: Float,
    val cohesionBoost: Float,  // Phase 8: Multi-note keyword cohesion boost
    val finalScore: Int,
    val confidence: LinkConfidence,
    val temporalReasons: List<MatchReason>,
    val semanticReasons: List<SemanticMatchReason>
)

internal fun shouldTreatNoteAsPresentDuringQuickValidation(
    nativeExists: Boolean?,
    providerHasNote: Boolean
): Boolean {
    if (providerHasNote) {
        return true
    }

    return nativeExists != false
}

private enum class SmartLinkScoringMode {
    SAME_DAY,
    CROSS_DAY
}

private data class NoteMatchContext(
    val semanticProfiles: List<SemanticProfile>?,
    val noteProfile: SemanticProfile?,
    val dismissedEventIds: Set<String>
)

private data class ScoredEventCandidate(
    val event: CalendarEvent,
    val scoredMatch: ScoredMatch,
    val semanticAffinity: Float,
    val dayOffset: Int,
    val midpointDistanceMs: Long
)

/**
 * Result of attempting to link a note - determines what UI action to take.
 */
sealed class LinkingResult {
    /** HIGH confidence - auto-linked silently */
    data class AutoLinked(
        val eventId: String,
        val eventTitle: String,
        val confidence: LinkConfidence,
        val score: Int,
        val reasons: List<MatchReason>
    ) : LinkingResult()

    /** MEDIUM confidence - auto-linked but flagged for review */
    data class AutoLinkedNeedsReview(
        val eventId: String,
        val eventTitle: String,
        val score: Int,
        val reasons: List<MatchReason>,
        val alternatives: List<ScoredMatch>  // Other possible matches
    ) : LinkingResult()

    /** LOW confidence - not auto-linked, suggestions provided */
    data class NeedsAttention(
        val suggestions: List<ScoredMatch>
    ) : LinkingResult()

    /** No matches found - linked to day only (generic note) */
    data class DayLinked(
        val date: String
    ) : LinkingResult()
}

// ═══════════════════════════════════════════════════════════════════════════
// EVENT EXCLUSION RULES (Phase 4 - Jan 2026)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Rules for excluding generic/recurring events from Smart Link matching.
 *
 * Purpose: Reduce noise by excluding events that are unlikely to have specific notes:
 * - Daily standups, syncs, and recurring check-ins
 * - Blocked/focus time
 * - Lunch breaks and commute
 * - Generic placeholders
 *
 * Implements Section 4.7 of the Semantic Genius Transformation Plan.
 */
object EventExclusionRules {

    /**
     * Events matching these patterns should be excluded from auto-matching.
     * These are generic, recurring events unlikely to have specific notes.
     */
    private val TITLE_EXCLUSION_PATTERNS = listOf(
        // Daily standups and syncs
        Regex("""(?i)\b(daily\s+)?(standup|scrum|sync|huddle)\b"""),

        // Blocked/focus time
        Regex("""(?i)\b(focus\s+time|blocked|busy|do\s+not\s+disturb|dnd)\b"""),

        // Lunch/breaks
        Regex("""(?i)\b(lunch|break|coffee)\b"""),

        // Generic holds
        Regex("""(?i)\b(hold|placeholder|tentative|tbd|tba)\b"""),

        // Commute
        Regex("""(?i)\b(commute|travel\s+time|drive\s+time)\b""")
    )

    /**
     * Recurring events below this duration are likely not note-worthy.
     */
    private const val MIN_RECURRING_DURATION_MS = 30 * 60 * 1000L  // 30 minutes

    /**
     * Check if an event should be excluded from Smart Link matching.
     *
     * @param event The calendar event
     * @param recurrenceCount How many times this event recurs (0 = not recurring)
     * @return true if event should be excluded
     */
    fun shouldExclude(event: CalendarEvent, recurrenceCount: Int = 0): Boolean {
        val titleLower = event.title.lowercase()

        // Check title patterns
        for (pattern in TITLE_EXCLUSION_PATTERNS) {
            if (pattern.containsMatchIn(titleLower)) {
                return true
            }
        }

        // Check recurring + short duration
        if (recurrenceCount > 5) {
            val duration = (event.endTime ?: event.startTime) - event.startTime
            if (duration < MIN_RECURRING_DURATION_MS) {
                return true
            }
        }

        return false
    }

    /**
     * Check if an event should have reduced weight in matching.
     * Returns a multiplier (0.0 to 1.0).
     *
     * @param event The calendar event
     * @return Weight multiplier (1.0 = full weight, lower = reduced weight)
     */
    fun getWeightMultiplier(event: CalendarEvent): Float {
        val titleLower = event.title.lowercase()

        // Recurring check-ins get reduced weight
        if (titleLower.contains("check-in") || titleLower.contains("1:1")) {
            return 0.7f
        }

        // All-hands/town halls get reduced weight (many notes unrelated)
        if (titleLower.contains("all-hands") || titleLower.contains("town hall")) {
            return 0.5f
        }

        return 1.0f
    }
}

/**
 * BooxNoteIndexer - Background service for scanning and indexing Boox Notes.
 *
 * Responsibilities:
 * - Scan all Boox Notes via native library
 * - Extract original dates from shape file timestamps (sync-safe)
 * - Maintain local index in Room database
 * - Auto-link notes to calendar events with confidence-based algorithm
 *
 * Enhanced Algorithm (Dec 2025):
 * - Extended grace periods: 2 hours after (3 hours for evening events)
 * - Confidence levels: HIGH auto-links, MEDIUM needs review, LOW suggests only
 * - New bonuses: evening event (+15), only-event-of-day (+20), long event (+10)
 * - Returns match reasons for UI display
 */
class BooxNoteIndexer(
    private val context: Context,
    private val booxIntegration: BooxNotesIntegration,
    private val noteRepository: BooxNoteRepository,
    private val eventRepository: CalendarEventRepository
) {
    // KvRepository for tracking autolink state (created lazily to avoid permission issues at construction)
    private val kvRepository by lazy { KvRepository(context) }
    private val quotaManager by lazy { OcrQuotaManager(context) }
    private val unifiedOcrRepository by lazy { UnifiedOcrRepository(context) }
    private val smartLinkEventRepository by lazy { SmartLinkEventRepository(context, eventRepository) }
    var semanticGeniusService: SemanticGeniusService? = null

    private fun isSmartLinkRuntimeEnabled(): Boolean =
        quotaManager.isSmartLinkEnabled && quotaManager.isOcrFeatureEnabled

    private fun logSmartLinkRuntimeDisabled(action: String) {
        val reason = when {
            !quotaManager.isSmartLinkEnabled -> "Smart Link disabled by server"
            !quotaManager.isOcrFeatureEnabled -> "OCR disabled by server"
            else -> "Smart Link runtime unavailable"
        }
        Log.d(TAG, "$action skipped: $reason (${quotaManager.describeAppliedConfig()})")
    }

    private fun getSemanticServiceForMatching(): SemanticGeniusService? {
        return semanticGeniusService.takeIf { quotaManager.isSemanticExtractionEnabled }
    }

    // Dictionary Loader for JSON-based dictionaries (Phase 7 - Dictionary Consolidation)
    private val dictionaryLoader by lazy { DictionaryLoader(context) }

    // Language dictionaries for domain-based semantic matching (Phase 7)
    private val languageDictionaries: LanguageDictionaries by lazy { dictionaryLoader.loadDictionaries("en") }

    // Abbreviations dictionary for title matching (Phase 7)
    private val abbreviations: AbbreviationsDictionary by lazy { dictionaryLoader.loadAbbreviations() }

    companion object {
        private const val TAG = "BooxNoteIndexer"

        // KV keys for change detection
        private const val KV_LAST_AUTOLINK_TIMESTAMP = "boox_last_autolink_timestamp"
        private const val KV_LAST_AUTOLINK_EVENT_COUNT = "boox_last_autolink_event_count"
        private const val KV_LAST_SCAN_TIMESTAMP = "boox_last_scan_timestamp"
        private const val KV_NEEDS_AUTOLINK = "boox_needs_autolink"
        private const val KV_PENDING_AUTOLINK_SCOPE = "boox_pending_autolink_scope"
        private const val KV_PENDING_AUTOLINK_DATES = "boox_pending_autolink_dates"

        private const val PENDING_AUTOLINK_SCOPE_ALL = "all"
        private const val PENDING_AUTOLINK_SCOPE_DATES = "dates"

        // Smart scan threshold (only scan if last scan was more than this ago)
        const val SMART_SCAN_THRESHOLD_MS = 60_000L  // 60 seconds

        // Quick scan only checks notes from the last 24 hours (Boox Notes can't create notes in the past)
        const val QUICK_SCAN_WINDOW_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val SMART_LINK_ENGINE_VERSION = "shared_engine_v2"

        // ═══════════════════════════════════════════════════════════════════
        // ENHANCED SCORING CONSTANTS (Dec 2025)
        // ═══════════════════════════════════════════════════════════════════

        // Base scores
        private const val SCORE_TITLE_MATCH = 100      // Strongest signal - title contains event keywords
        private const val SCORE_DURING_EVENT = 60     // Note created while event was happening (+10)
        private const val SCORE_GRACE_AFTER_MAX = 45  // Follow-up notes (decays with time) (+5)
        private const val SCORE_GRACE_BEFORE_MAX = 25 // Prep notes (decays with time) (+5)

        // New bonus scores
        private const val BONUS_EVENING_EVENT = 15    // Events after 5 PM (people often write notes after)
        private const val BONUS_ONLY_EVENT = 20       // Only timed event of day (obvious match)
        private const val BONUS_LONG_EVENT = 10       // Events > 2 hours (workshops, all-day focus)

        // Penalty scores (Fix #10 - Jan 2026)
        private const val PENALTY_TEST_CONTENT = -50  // Heavy penalty for test/placeholder content

        // Threshold scores
        private const val SCORE_MINIMUM = 20          // Minimum score to show as suggestion
        private const val SEMANTIC_BONUS_MAX = 80     // Equal weight to title match (was 35)
        private const val SEMANTIC_SCORE_MIN = 0.10f  // Lower threshold for inclusion (was 0.15)

        // ═══════════════════════════════════════════════════════════════════
        // HYBRID SCORING WEIGHTS (Phase 4 - Jan 2026)
        // Formula: finalScore = temporalScore × (1.0 + semanticAffinity × SEMANTIC_WEIGHT)
        // ═══════════════════════════════════════════════════════════════════
        const val SEMANTIC_WEIGHT = 0.8f      // Semantic affinity can boost score by up to 80%
        const val COOCCURRENCE_WEIGHT = 0.15f // Entity co-occurrence can boost by up to 15%
        private const val SEMANTIC_HIGH_QUALITY = 0.40f  // High quality semantic match threshold
        private const val EVENT_DESCRIPTION_MAX_CHARS = 500

        // ═══════════════════════════════════════════════════════════════════
        // EXTENDED GRACE PERIODS (Dec 2025)
        // Key insight: Notes kept open for hours still belong to their event.
        // E.g., 6:30 PM meeting, note saved at 10 PM = should link to meeting.
        // ═══════════════════════════════════════════════════════════════════

        private const val GRACE_BEFORE_EVENT_MS = 30 * 60 * 1000L      // 30 min before (was 15)
        private const val GRACE_AFTER_EVENT_MS = 2 * 60 * 60 * 1000L   // 2 hours after (was 30 min)
        private const val EVENING_GRACE_AFTER_MS = 3 * 60 * 60 * 1000L // 3 hours for evening events
        private const val DEFAULT_EVENT_DURATION_MS = 60 * 60 * 1000L  // 1 hour if no end time
        private const val LONG_EVENT_THRESHOLD_MS = 2 * 60 * 60 * 1000L // Events > 2 hours

        // Time definitions
        private const val EVENING_START_HOUR = 17  // 5 PM


        // ═══════════════════════════════════════════════════════════════════
        // CROSS-DAY SCORING (Smart Link Enhancement - Dec 2025)
        // ═══════════════════════════════════════════════════════════════════

        // Day distance penalties (REBALANCED v2 - gentler curve)
        private const val PENALTY_1_DAY = -5      // ±1 day (was -15, 66% reduction)
        private const val PENALTY_2_DAYS = -15    // ±2 days (was -35, 57% reduction)
        private const val PENALTY_3_DAYS = -25    // ±3 days (was -50, 50% reduction)
        private const val PENALTY_4_TO_7_DAYS = -35  // ±4-7 days (for bulk mode)

        // Relationship bonuses
        private const val BONUS_FOLLOW_UP = 20    // Note after event (review/follow-up)
        private const val BONUS_PREP_NOTES = 15   // Note before event (preparation)
        private const val BONUS_SAME_DAY_PROXIMITY = 15  // Note and event on same day (FIX Jan 2026)

        // Time-of-day context bonuses
        private const val BONUS_MORNING_FOLLOW_UP = 10  // Morning note + yesterday PM event

        // Cross-day confidence thresholds (REBALANCED v2 - cross-day-appropriate)
        private const val CROSS_DAY_HIGH_THRESHOLD = 55     // Was 85
        private const val CROSS_DAY_MEDIUM_THRESHOLD = 35   // Was 60
        private const val CROSS_DAY_LOW_THRESHOLD = 20      // Was 35
        private const val CROSS_DAY_POSSIBLE_THRESHOLD = 8  // Was 15

        // ═══════════════════════════════════════════════════════════════════
        // ABBREVIATIONS & SEMANTIC KEYWORDS - MOVED TO JSON (Phase 7)
        // ═══════════════════════════════════════════════════════════════════
        // All abbreviations and semantic keyword groups have been consolidated
        // into JSON dictionaries for maintainability and consistency:
        // - Abbreviations: assets/classification/en/abbreviations.json
        // - Domain keywords: assets/classification/en/domains/*.json
        // See: DictionaryLoader.loadAbbreviations(), loadDictionaries()

        // Semantic group overlap bonuses (graduated by quality score)
        // Quality score 0.0-1.0 from calculateSemanticOverlapScore()
        private const val BONUS_SEMANTIC_STRONG = 30   // Quality >= 0.7: TierA terms + exact overlap
        private const val BONUS_SEMANTIC_MODERATE = 20 // Quality 0.4-0.7: TierB terms or mixed
        private const val BONUS_SEMANTIC_WEAK = 8      // Quality 0.2-0.4: Mostly TierC, no exact overlap
        // Quality < 0.2: No bonus (insufficient semantic connection)

        // Quality thresholds for semantic scoring
        private const val SEMANTIC_STRONG_THRESHOLD = 0.7f
        private const val SEMANTIC_MODERATE_THRESHOLD = 0.4f
        private const val SEMANTIC_WEAK_THRESHOLD = 0.2f

        // Sparse context bonuses (few events in window = stronger signal)
        private const val BONUS_SPARSE_CONTEXT = 20      // 2-3 events in window
        private const val BONUS_ONLY_CANDIDATE = 30      // Only 1 event in entire window

        // Weekday pattern bonus
        private const val BONUS_SAME_WEEKDAY = 10  // Same day of week (different week)

        // Search windows
        const val DAY_MODE_SEARCH_DAYS = 3   // ±3 days for Day mode
        const val BULK_MODE_SEARCH_DAYS = 7  // ±7 days for Bulk mode

        // LLM batching
        private const val LLM_BATCH_SIZE = 3
        private const val LLM_CONCURRENCY = 5
    }

    /**
     * Result of a full scan operation.
     */
    data class IndexResult(
        val totalScanned: Int,
        val newNotes: Int,
        val updatedNotes: Int,
        val deletedNotes: Int,
        val scanDurationMs: Long,
        val newNoteIds: List<String> = emptyList(),  // IDs of newly added notes (for smart auto-linking)
        val error: String? = null,
        val recoveryPath: String = "native",
        val nativeErrorRaw: String? = null,
        val missingTitleRecovered: Int = 0,
        val zeroPageRecovered: Int = 0
    )

    private data class DiscoveredNote(
        val uniqueId: String,
        val nativeTitle: String?,
        val providerTitle: String?,
        val pageCount: Int,
        val pageIds: List<String>,
        val modifiedTimestampMs: Long,
        val pageCountRecovered: Boolean = false,
        val titleSource: String? = null,
        val pageSource: String = "none",
        val hasTextContent: Boolean = false,
        val metadataWarnings: List<String> = emptyList()
    )

    private data class DiscoveryResult(
        val notes: List<DiscoveredNote>,
        val scannedIds: Set<String>,
        val recoveryPath: String,
        val nativeErrorRaw: String? = null,
        val error: String? = null,
        val canDeleteStale: Boolean = true,
        val zeroPageRecovered: Int = 0
    )

    private data class ResolvedTitle(
        val title: String,
        val source: String
    )

    private data class PageResolution(
        val pageIds: List<String>,
        val pageCount: Int,
        val recovered: Boolean,
        val source: String
    )

    private data class ResolvedDate(
        val originalDate: String?,
        val originalTimestamp: Long,
        val source: String,
        val warnings: List<String> = emptyList()
    )

    /**
     * Perform a full scan of all Boox Notes and update the index.
     * Should be called on app launch and when returning from Boox Notes app.
     */
    suspend fun fullScan(): IndexResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting full scan of Boox Notes")

        try {
            val discovery = discoverNotesSmart()
            if (discovery.error != null) {
                Log.e(TAG, "Scan failed after recovery attempts: ${discovery.error}")
                return@withContext IndexResult(
                    totalScanned = 0, newNotes = 0, updatedNotes = 0,
                    deletedNotes = 0, scanDurationMs = System.currentTimeMillis() - startTime,
                    error = discovery.error,
                    recoveryPath = discovery.recoveryPath,
                    nativeErrorRaw = discovery.nativeErrorRaw,
                    zeroPageRecovered = discovery.zeroPageRecovered
                )
            }
            Log.i(
                TAG,
                "Discovery path=${discovery.recoveryPath}, notes=${discovery.notes.size}, " +
                    "zeroPageRecovered=${discovery.zeroPageRecovered}, nativeError=${discovery.nativeErrorRaw ?: "none"}"
            )

            val existingNotes = noteRepository.getAllNotes().associateBy { it.uniqueId }
            val existingNoteIds = existingNotes.keys

            val scannedNotes = mutableListOf<BooxNoteIndex>()
            val newNoteIds = mutableListOf<String>()  // Track NEW notes for smart auto-linking
            val relinkDates = mutableSetOf<String>()
            var missingTitleRecovered = 0

            // Process discovered notes
            for (discovered in discovery.notes) {
                val uniqueId = discovered.uniqueId

                // ═══════════════════════════════════════════════════════════════════
                // FIX: Use earliest shape file timestamp (creation time) NOT modified_timestamp
                // This prevents notes from "moving" to different dates when edited.
                // Same logic as quickScanRecent() for consistency.
                // ═══════════════════════════════════════════════════════════════════
                val (ts1, ts2) = booxIntegration.getShapeFileTimestamps(uniqueId)
                val shapeTimestamp = listOfNotNull(ts1, ts2).minOrNull() ?: discovered.modifiedTimestampMs

                // Determine original date (priority: title > shape file timestamp)
                val resolvedDate = extractOriginalDate(
                    uniqueId = uniqueId,
                    title = discovered.nativeTitle ?: discovered.providerTitle,
                    shapeTimestamp = shapeTimestamp
                )

                // Skip notes we cannot place on a day.
                if (resolvedDate.originalDate == null) {
                    continue
                }

                val resolvedTitle = resolveTitle(
                    uniqueId = uniqueId,
                    nativeTitle = discovered.nativeTitle,
                    providerTitle = discovered.providerTitle,
                    originalDate = resolvedDate.originalDate
                )
                if (resolvedTitle.source != "native") {
                    missingTitleRecovered++
                }

                val warnings = (discovered.metadataWarnings + resolvedDate.warnings).distinct()
                reconcileRemovedPages(existingNotes[uniqueId], discovered.pageIds)

                val noteIndex = BooxNoteIndex(
                    uniqueId = uniqueId,
                    title = resolvedTitle.title,
                    originalDate = resolvedDate.originalDate,
                    originalTimestamp = resolvedDate.originalTimestamp,
                    pageCount = discovered.pageCount,
                    pageIdsJson = discovered.pageIds.joinToString(",") { "\"$it\"" }.let { "[$it]" },
                    shapeFileModified = discovered.modifiedTimestampMs,
                    indexedAt = System.currentTimeMillis(),
                    titleSource = if (resolvedTitle.source == "native") discovered.titleSource ?: "native" else resolvedTitle.source,
                    dateSource = resolvedDate.source,
                    pageSource = discovered.pageSource,
                    previewStatus = computePreviewStatus(discovered.pageIds, discovered.pageSource, discovered.hasTextContent),
                    hasTextContent = discovered.hasTextContent,
                    warningsJson = warningsToJson(warnings)
                )
                scannedNotes.add(noteIndex)

                if (doesNoteChangeAffectMatching(existingNotes[uniqueId], noteIndex)) {
                    existingNotes[uniqueId]?.originalDate
                        ?.takeIf { it.isNotBlank() }
                        ?.let { relinkDates.add(it) }
                    noteIndex.originalDate
                        .takeIf { it.isNotBlank() }
                        ?.let { relinkDates.add(it) }
                }

                // Track if this is a NEW note (not seen before)
                if (uniqueId !in existingNoteIds) {
                    newNoteIds.add(uniqueId)
                }
            }

            // Update database
            if (scannedNotes.isNotEmpty()) {
                noteRepository.upsertNotes(scannedNotes)
            }

            relinkDates.addAll(collectDeletedNoteDates(existingNotes, discovery))
            val deletedCount = cleanupStaleNotes(existingNoteIds, discovery)

            val duration = System.currentTimeMillis() - startTime
            val actualNewCount = newNoteIds.size
            val updatedCount = scannedNotes.size - actualNewCount

            Log.d(TAG, "Scan complete: ${scannedNotes.size} notes (${actualNewCount} new, ${updatedCount} updated, ${deletedCount} deleted) in ${duration}ms")

            // ═══════════════════════════════════════════════════════════════════
            // OPTIMIZATION: Skip auto-linking if nothing changed (#1)
            // Re-evaluate links only when:
            // - New notes were added
            // - Notes were deleted
            // - Calendar event count changed (events added/removed)
            // ═══════════════════════════════════════════════════════════════════
            if (scannedNotes.isNotEmpty() || deletedCount > 0) {
                val currentEventCount = smartLinkEventRepository.getEventCount()
                val lastAutolinkEventCount = kvRepository.get(KV_LAST_AUTOLINK_EVENT_COUNT)?.value?.toLongOrNull() ?: -1L

                val hasNewNotes = newNoteIds.isNotEmpty()
                val hasDeletedNotes = deletedCount > 0
                val hasUpdatedMatchingDates = relinkDates.isNotEmpty()
                val eventCountChanged = currentEventCount != lastAutolinkEventCount

                val needsRelinking = hasUpdatedMatchingDates || hasDeletedNotes || eventCountChanged

                if (needsRelinking) {
                    val reason = buildString {
                        if (hasNewNotes) append("${newNoteIds.size} new notes, ")
                        if (hasUpdatedMatchingDates) append("${relinkDates.size} affected note dates, ")
                        if (hasDeletedNotes) append("$deletedCount deleted notes, ")
                        if (eventCountChanged) append("event count changed ($lastAutolinkEventCount → $currentEventCount), ")
                    }.trimEnd(',', ' ')

                    if (eventCountChanged) {
                        if (quotaManager.isBackfillComplete()) {
                            Log.d(TAG, "Notes/events changed + backfill complete — auto-linking all notes now (reason: $reason)")
                            autoLinkAllNotes()
                            clearPendingAutolinkState(currentEventCount)
                        } else {
                            Log.d(TAG, "Notes/events changed — deferring full auto-link until indexing completes (reason: $reason)")
                            markPendingAutolinkForAllNotes(currentEventCount)
                        }
                    } else {
                        relinkDatesAfterChange(
                            dates = relinkDates,
                            reason = reason,
                            currentEventCount = currentEventCount,
                            changeLabel = "Note dates changed"
                        )
                    }
                } else {
                    Log.d(TAG, "Skipping auto-link - no changes detected (notes: ${scannedNotes.size}, events: $currentEventCount)")
                }
            }

            // Save last scan timestamp for smart scan feature
            kvRepository.set(Kv(KV_LAST_SCAN_TIMESTAMP, System.currentTimeMillis().toString()))

            return@withContext IndexResult(
                totalScanned = scannedNotes.size,
                newNotes = actualNewCount,
                updatedNotes = updatedCount,
                deletedNotes = deletedCount,
                scanDurationMs = duration,
                newNoteIds = newNoteIds,
                recoveryPath = discovery.recoveryPath,
                nativeErrorRaw = discovery.nativeErrorRaw,
                missingTitleRecovered = missingTitleRecovered,
                zeroPageRecovered = discovery.zeroPageRecovered
            )

        } catch (e: Throwable) {
            // Catch Throwable to handle UnsatisfiedLinkError (native method not found)
            // which is an Error, not an Exception
            Log.e(TAG, "Scan failed with exception", e)
            return@withContext IndexResult(
                totalScanned = 0, newNotes = 0, updatedNotes = 0,
                deletedNotes = 0, scanDurationMs = System.currentTimeMillis() - startTime,
                error = e.message ?: "Unknown error: ${e.javaClass.simpleName}",
                recoveryPath = "native"
            )
        }
    }

    /**
     * Get the timestamp of the last successful scan.
     * Returns 0 if never scanned.
     */
    suspend fun getLastScanTimestamp(): Long = withContext(Dispatchers.IO) {
        kvRepository.get(KV_LAST_SCAN_TIMESTAMP)?.value?.toLongOrNull() ?: 0L
    }

    /**
     * Check if scan data is stale (last scan was more than thresholdMs ago).
     */
    suspend fun isScanStale(thresholdMs: Long = SMART_SCAN_THRESHOLD_MS): Boolean {
        val lastScan = getLastScanTimestamp()
        val elapsed = System.currentTimeMillis() - lastScan
        return elapsed > thresholdMs
    }

    /**
     * Perform a scan only if the last scan was more than thresholdMs ago.
     * Uses quickScanRecent() which only checks notes from the last 24 hours.
     * Returns the scan result if scan was performed, null if skipped.
     *
     * @param thresholdMs Minimum time since last scan to trigger a new scan (default 60s)
     */
    suspend fun scanIfStale(thresholdMs: Long = SMART_SCAN_THRESHOLD_MS): IndexResult? {
        return if (isScanStale(thresholdMs)) {
            Log.d(TAG, "Scan data is stale (>${thresholdMs}ms), performing quick scan")
            quickScanRecent()
        } else {
            Log.d(TAG, "Scan data is fresh, skipping scan")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART LINK BACKFILL FIX - Auto-link after OCR indexing completes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if auto-link is pending (notes changed but OCR wasn't ready yet).
     * This flag is set during scan and cleared after auto-link runs.
     */
    suspend fun needsAutolink(): Boolean = withContext(Dispatchers.IO) {
        kvRepository.get(KV_NEEDS_AUTOLINK)?.value == "true" ||
            readPendingAutolinkScope() == PENDING_AUTOLINK_SCOPE_ALL ||
            readPendingAutolinkDates().isNotEmpty()
    }

    private fun readPendingAutolinkScope(): String? =
        kvRepository.get(KV_PENDING_AUTOLINK_SCOPE)?.value?.trim()?.takeIf { it.isNotEmpty() }

    private fun readPendingAutolinkDates(): Set<String> {
        val raw = kvRepository.get(KV_PENDING_AUTOLINK_DATES)?.value?.trim().orEmpty()
        if (raw.isBlank()) return emptySet()

        return try {
            val json = JSONArray(raw)
            buildSet {
                for (i in 0 until json.length()) {
                    val date = json.optString(i).trim()
                    if (date.isNotEmpty()) add(date)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse pending auto-link dates: ${e.message}")
            emptySet()
        }
    }

    private fun writePendingAutolinkDates(dates: Set<String>) {
        if (dates.isEmpty()) {
            kvRepository.delete(KV_PENDING_AUTOLINK_DATES)
            return
        }

        val json = JSONArray().apply {
            dates.asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .forEach { put(it) }
        }
        kvRepository.set(Kv(KV_PENDING_AUTOLINK_DATES, json.toString()))
    }

    private fun setPendingAutolinkScope(scope: String?) {
        val normalized = scope?.trim().orEmpty()
        if (normalized.isEmpty()) {
            kvRepository.delete(KV_PENDING_AUTOLINK_SCOPE)
        } else {
            kvRepository.set(Kv(KV_PENDING_AUTOLINK_SCOPE, normalized))
        }
    }

    private fun markPendingAutolinkForAllNotes(currentEventCount: Long) {
        kvRepository.set(Kv(KV_NEEDS_AUTOLINK, "true"))
        kvRepository.set(Kv(KV_LAST_AUTOLINK_EVENT_COUNT, currentEventCount.toString()))
        setPendingAutolinkScope(PENDING_AUTOLINK_SCOPE_ALL)
        kvRepository.delete(KV_PENDING_AUTOLINK_DATES)
    }

    private fun markPendingAutolinkForDates(dates: Set<String>, currentEventCount: Long) {
        val normalizedDates = dates.asSequence()
            .filter { it.isNotBlank() }
            .toSet()
        if (normalizedDates.isEmpty()) return

        kvRepository.set(Kv(KV_NEEDS_AUTOLINK, "true"))
        kvRepository.set(Kv(KV_LAST_AUTOLINK_EVENT_COUNT, currentEventCount.toString()))

        if (readPendingAutolinkScope() == PENDING_AUTOLINK_SCOPE_ALL) {
            return
        }

        setPendingAutolinkScope(PENDING_AUTOLINK_SCOPE_DATES)
        writePendingAutolinkDates(readPendingAutolinkDates() + normalizedDates)
    }

    private fun clearPendingAutolinkState(currentEventCount: Long) {
        kvRepository.set(Kv(KV_NEEDS_AUTOLINK, "false"))
        kvRepository.set(Kv(KV_LAST_AUTOLINK_EVENT_COUNT, currentEventCount.toString()))
        kvRepository.set(Kv(KV_LAST_AUTOLINK_TIMESTAMP, System.currentTimeMillis().toString()))
        kvRepository.delete(KV_PENDING_AUTOLINK_SCOPE)
        kvRepository.delete(KV_PENDING_AUTOLINK_DATES)
    }

    private fun recordDateScopedAutolinkCompletion(dates: Set<String>, currentEventCount: Long) {
        if (readPendingAutolinkScope() == PENDING_AUTOLINK_SCOPE_DATES) {
            val remainingDates = readPendingAutolinkDates() - dates
            if (remainingDates.isEmpty()) {
                kvRepository.set(Kv(KV_NEEDS_AUTOLINK, "false"))
                kvRepository.delete(KV_PENDING_AUTOLINK_SCOPE)
                kvRepository.delete(KV_PENDING_AUTOLINK_DATES)
            } else {
                kvRepository.set(Kv(KV_NEEDS_AUTOLINK, "true"))
                writePendingAutolinkDates(remainingDates)
            }
        }

        kvRepository.set(Kv(KV_LAST_AUTOLINK_EVENT_COUNT, currentEventCount.toString()))
        kvRepository.set(Kv(KV_LAST_AUTOLINK_TIMESTAMP, System.currentTimeMillis().toString()))
    }

    /**
     * Run pending auto-link if needed (called after backfill completes).
     * This ensures notes are linked to events using semantic data from OCR.
     *
     * @return true if auto-link was run, false if not needed
     */
    suspend fun runPendingAutoLinkIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!needsAutolink()) {
            Log.d(TAG, "No pending auto-link needed")
            return@withContext false
        }

        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Pending auto-link")
            return@withContext false
        }

        val pendingScope = readPendingAutolinkScope()
        val pendingDates = readPendingAutolinkDates()

        when {
            pendingScope == PENDING_AUTOLINK_SCOPE_ALL -> {
                Log.i(TAG, "Running pending auto-link for all notes (backfill complete, semantic data available)")
                autoLinkAllNotes()
            }
            pendingDates.isNotEmpty() -> {
                Log.i(TAG, "Running pending auto-link for ${pendingDates.size} affected date(s) (backfill complete, semantic data available)")
                autoLinkNotesForDates(pendingDates)
            }
            else -> {
                Log.i(TAG, "Running pending auto-link with legacy full fallback (backfill complete, semantic data available)")
                autoLinkAllNotes()
            }
        }

        clearPendingAutolinkState(currentEventCount = smartLinkEventRepository.getEventCount())

        Log.i(TAG, "Pending auto-link complete")
        return@withContext true
    }

    /**
     * Quick scan that only checks notes from the last 24 hours.
     * Since Boox Notes can't create notes in the past, we only need to check recent notes.
     * This is much faster than fullScan() for dialog opening.
     *
     * Checks for:
     * - New notes not in our index
     * - Notes with modified timestamps in the last 24 hours
     */
    suspend fun quickScanRecent(): IndexResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cutoffTime = startTime - QUICK_SCAN_WINDOW_MS
        Log.d(TAG, "Starting quick scan (last 24 hours only)")

        try {
            val discovery = discoverNotesSmart()
            if (discovery.error != null) {
                Log.e(TAG, "Quick scan failed after recovery attempts: ${discovery.error}")
                return@withContext IndexResult(
                    totalScanned = 0, newNotes = 0, updatedNotes = 0,
                    deletedNotes = 0, scanDurationMs = System.currentTimeMillis() - startTime,
                    error = discovery.error,
                    recoveryPath = discovery.recoveryPath,
                    nativeErrorRaw = discovery.nativeErrorRaw,
                    zeroPageRecovered = discovery.zeroPageRecovered
                )
            }
            Log.d(
                TAG,
                "Quick discovery path=${discovery.recoveryPath}, notes=${discovery.notes.size}, " +
                    "zeroPageRecovered=${discovery.zeroPageRecovered}"
            )

            // Get existing indexed notes for comparison
            val existingNotes = noteRepository.getAllNotes().associateBy { it.uniqueId }

            var newCount = 0
            var updatedCount = 0
            var missingTitleRecovered = 0
            val newNoteIds = mutableListOf<String>()
            val relinkDates = mutableSetOf<String>()

            // Process only new notes or notes modified in the last 24 hours
            for (discovered in discovery.notes) {
                val uniqueId = discovered.uniqueId
                val modifiedTimestamp = discovered.modifiedTimestampMs
                val existingNote = existingNotes[uniqueId]

                // Skip if note exists AND was not modified recently
                if (existingNote != null && modifiedTimestamp < cutoffTime) {
                    continue
                }

                // Extract original date
                val (ts1, ts2) = booxIntegration.getShapeFileTimestamps(uniqueId)
                val shapeTimestamp = listOfNotNull(ts1, ts2).minOrNull() ?: modifiedTimestamp
                val resolvedDate = extractOriginalDate(
                    uniqueId = uniqueId,
                    title = discovered.nativeTitle ?: discovered.providerTitle,
                    shapeTimestamp = shapeTimestamp
                )

                if (resolvedDate.originalDate == null) {
                    continue
                }

                val resolvedTitle = resolveTitle(
                    uniqueId = uniqueId,
                    nativeTitle = discovered.nativeTitle,
                    providerTitle = discovered.providerTitle,
                    originalDate = resolvedDate.originalDate
                )
                if (resolvedTitle.source != "native") {
                    missingTitleRecovered++
                }

                val warnings = (discovered.metadataWarnings + resolvedDate.warnings).distinct()
                reconcileRemovedPages(existingNote, discovered.pageIds)

                val noteIndex = BooxNoteIndex(
                    uniqueId = uniqueId,
                    title = resolvedTitle.title,
                    originalDate = resolvedDate.originalDate,
                    originalTimestamp = resolvedDate.originalTimestamp,
                    pageCount = discovered.pageCount,
                    pageIdsJson = discovered.pageIds.joinToString(",") { "\"$it\"" }.let { "[$it]" },
                    shapeFileModified = modifiedTimestamp,
                    indexedAt = startTime,
                    titleSource = if (resolvedTitle.source == "native") discovered.titleSource ?: "native" else resolvedTitle.source,
                    dateSource = resolvedDate.source,
                    pageSource = discovered.pageSource,
                    previewStatus = computePreviewStatus(discovered.pageIds, discovered.pageSource, discovered.hasTextContent),
                    hasTextContent = discovered.hasTextContent,
                    warningsJson = warningsToJson(warnings)
                )

                noteRepository.upsertNote(noteIndex)

                if (doesNoteChangeAffectMatching(existingNote, noteIndex)) {
                    existingNote?.originalDate
                        ?.takeIf { it.isNotBlank() }
                        ?.let { relinkDates.add(it) }
                    noteIndex.originalDate
                        .takeIf { it.isNotBlank() }
                        ?.let { relinkDates.add(it) }
                }

                if (existingNote == null) {
                    newCount++
                    newNoteIds.add(uniqueId)
                    Log.d(TAG, "Quick scan: New note '${noteIndex.title}'")
                } else {
                    updatedCount++
                    Log.d(TAG, "Quick scan: Updated note '${noteIndex.title}'")
                }
            }

            relinkDates.addAll(collectDeletedNoteDates(existingNotes, discovery))
            val deletedCount = cleanupStaleNotes(existingNotes.keys, discovery)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Quick scan completed: $newCount new, $updatedCount updated, $deletedCount deleted in ${duration}ms")

            // Save scan timestamp
            kvRepository.set(Kv(KV_LAST_SCAN_TIMESTAMP, System.currentTimeMillis().toString()))

            if (relinkDates.isNotEmpty()) {
                val currentEventCount = smartLinkEventRepository.getEventCount()
                relinkDatesAfterChange(
                    dates = relinkDates,
                    reason = "quick scan found ${newNoteIds.size} new note(s) and ${updatedCount} updated note(s)",
                    currentEventCount = currentEventCount,
                    changeLabel = "Note dates changed"
                )
            }

            return@withContext IndexResult(
                totalScanned = newCount + updatedCount,
                newNotes = newCount,
                updatedNotes = updatedCount,
                deletedNotes = deletedCount,
                scanDurationMs = duration,
                newNoteIds = newNoteIds,
                recoveryPath = discovery.recoveryPath,
                nativeErrorRaw = discovery.nativeErrorRaw,
                missingTitleRecovered = missingTitleRecovered,
                zeroPageRecovered = discovery.zeroPageRecovered
            )

        } catch (e: Throwable) {
            Log.e(TAG, "Quick scan failed with exception", e)
            return@withContext IndexResult(
                totalScanned = 0, newNotes = 0, updatedNotes = 0,
                deletedNotes = 0, scanDurationMs = System.currentTimeMillis() - startTime,
                error = e.message ?: "Unknown error: ${e.javaClass.simpleName}",
                recoveryPath = "native"
            )
        }
    }

    private suspend fun discoverNotesSmart(): DiscoveryResult {
        var nativeErrorRaw: String? = null
        val providerNotesById = loadProviderNotesById()

        if (!BooxNative.isLibraryLoaded) {
            nativeErrorRaw = "Native library not loaded: ${BooxNative.libraryLoadError ?: "Native library unavailable"}"
            if (providerNotesById.isNotEmpty()) {
                val providerOnly = providerNotesById.values.map { providerToDiscoveredNote(it) }
                return DiscoveryResult(
                    notes = providerOnly,
                    scannedIds = providerOnly.map { it.uniqueId }.toSet(),
                    recoveryPath = "provider_fallback",
                    nativeErrorRaw = nativeErrorRaw,
                    canDeleteStale = false,
                    zeroPageRecovered = providerOnly.count { it.pageCountRecovered }
                )
            }
            return DiscoveryResult(
                notes = emptyList(),
                scannedIds = emptySet(),
                recoveryPath = "native_failed",
                nativeErrorRaw = nativeErrorRaw,
                error = nativeErrorRaw,
                canDeleteStale = false
            )
        }

        val nativeFirst = parseNativeDiscovery(BooxNative.listAllNotesOnDevice(BooxNative.KSYNC_BASE_PATH))
        if (nativeFirst.error == null) {
            val merged = mergeWithProvider(nativeFirst.notes, providerNotesById)
            return merged.copy(
                recoveryPath = if (merged.notes.size > nativeFirst.notes.size) "native_merged" else "native",
                nativeErrorRaw = nativeFirst.rawError
            )
        }
        nativeErrorRaw = nativeFirst.rawError ?: nativeFirst.error

        val nativeRetry = parseNativeDiscovery(BooxNative.listAllNotesOnDevice(BooxNative.KSYNC_BASE_PATH))
        if (nativeRetry.error == null) {
            val merged = mergeWithProvider(nativeRetry.notes, providerNotesById)
            return merged.copy(
                recoveryPath = if (merged.notes.size > nativeRetry.notes.size) "native_retry_merged" else "native_retry",
                nativeErrorRaw = nativeErrorRaw
            )
        }
        nativeErrorRaw = nativeErrorRaw ?: nativeRetry.rawError ?: nativeRetry.error

        if (providerNotesById.isNotEmpty()) {
            val providerOnly = providerNotesById.values.map { providerToDiscoveredNote(it) }
            return DiscoveryResult(
                notes = providerOnly,
                scannedIds = providerOnly.map { it.uniqueId }.toSet(),
                recoveryPath = "provider_fallback",
                nativeErrorRaw = nativeErrorRaw,
                canDeleteStale = false,
                zeroPageRecovered = providerOnly.count { it.pageCountRecovered }
            )
        }

        return DiscoveryResult(
            notes = emptyList(),
            scannedIds = emptySet(),
            recoveryPath = "native_failed",
            nativeErrorRaw = nativeErrorRaw,
            error = nativeRetry.error ?: nativeFirst.error ?: "Failed to discover notes from native and provider sources",
            canDeleteStale = false
        )
    }

    private data class NativeDiscoveryParse(
        val notes: List<DiscoveredNote>,
        val error: String?,
        val rawError: String?
    )

    private fun parseNativeDiscovery(rawJson: String?): NativeDiscoveryParse {
        if (rawJson.isNullOrBlank()) {
            return NativeDiscoveryParse(emptyList(), "Native scan returned empty response", "empty_response")
        }

        val result = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return NativeDiscoveryParse(
                notes = emptyList(),
                error = "Native scan returned malformed JSON",
                rawError = rawJson.take(500)
            )
        }

        val explicitError = result.optString("error").takeUnless { it.isNullOrBlank() }
        if (explicitError != null) {
            return NativeDiscoveryParse(emptyList(), explicitError, explicitError)
        }

        if (result.optString("status") == "error") {
            val message = result.optString("message", "Unknown native scan error")
            return NativeDiscoveryParse(emptyList(), message, message)
        }

        val notesArray = result.optJSONArray("document_notes")
            ?: return NativeDiscoveryParse(
                notes = emptyList(),
                error = "Native response missing document_notes",
                rawError = rawJson.take(500)
            )

        val notes = mutableListOf<DiscoveredNote>()
        for (i in 0 until notesArray.length()) {
            val noteObj = notesArray.optJSONObject(i) ?: continue
            val uniqueId = noteObj.optString("unique_id")
            if (uniqueId.isBlank()) continue

            val nativeTitle = sanitizeTitle(if (noteObj.isNull("title")) null else noteObj.optString("title"))
            val titleSource = sanitizeTitle(if (noteObj.isNull("title_source")) null else noteObj.optString("title_source"))
            val modifiedTimestampMs = normalizeTimestampToMs(noteObj.optLong("modified_timestamp", 0L))
            val nativePageCount = noteObj.optInt("page_count", 0).coerceAtLeast(0)
            val hasTextContent = noteObj.optBoolean("has_text_content", false) ||
                !noteObj.optString("text_content").isNullOrBlank()

            val nativePageIds = mutableListOf<String>()
            val pageIdsArray = noteObj.optJSONArray("page_ids")
            if (pageIdsArray != null) {
                for (j in 0 until pageIdsArray.length()) {
                    val pageId = pageIdsArray.optString(j)
                    if (pageId.isNotBlank()) {
                        nativePageIds.add(pageId)
                    }
                }
            }

            val pageResolution = resolvePageData(
                uniqueId = uniqueId,
                pageIds = nativePageIds,
                pageCount = nativePageCount
            )

            notes.add(
                DiscoveredNote(
                    uniqueId = uniqueId,
                    nativeTitle = nativeTitle,
                    providerTitle = null,
                    pageCount = pageResolution.pageCount,
                    pageIds = pageResolution.pageIds,
                    modifiedTimestampMs = modifiedTimestampMs,
                    pageCountRecovered = pageResolution.recovered,
                    titleSource = titleSource,
                    pageSource = pageResolution.source,
                    hasTextContent = hasTextContent,
                    metadataWarnings = parseWarningsArray(noteObj.optJSONArray("metadata_warnings"))
                )
            )
        }

        return NativeDiscoveryParse(notes = notes, error = null, rawError = null)
    }

    private fun mergeWithProvider(
        nativeNotes: List<DiscoveredNote>,
        providerNotesById: Map<String, NoteReference>
    ): DiscoveryResult {
        val merged = nativeNotes.associateBy { it.uniqueId }.toMutableMap()

        providerNotesById.forEach { (uniqueId, provider) ->
            val providerTitle = sanitizeTitle(provider.title)
            val providerTimestamp = normalizeTimestampToMs(provider.updatedAt ?: provider.createdAt ?: 0L)
            val providerCount = (provider.pageCount ?: 0).coerceAtLeast(0)
            val providerPageIds = extractPageIdsFromProvider(provider)

            val existing = merged[uniqueId]
            if (existing != null) {
                val pageResolution = resolvePageData(
                    uniqueId = uniqueId,
                    pageIds = existing.pageIds + providerPageIds,
                    pageCount = maxOf(existing.pageCount, providerCount)
                )
                merged[uniqueId] = existing.copy(
                    providerTitle = existing.providerTitle ?: providerTitle,
                    pageCount = pageResolution.pageCount,
                    pageIds = pageResolution.pageIds,
                    modifiedTimestampMs = maxOf(existing.modifiedTimestampMs, providerTimestamp),
                    pageCountRecovered = existing.pageCountRecovered || pageResolution.recovered,
                    pageSource = if (existing.pageSource == pageResolution.source) existing.pageSource else "mixed",
                    hasTextContent = existing.hasTextContent,
                    metadataWarnings = (existing.metadataWarnings + pageResolution.source.takeIf { it == "stale_provided" }?.let {
                        listOf("Stored page IDs did not fully match live device pages")
                    }.orEmpty()).distinct()
                )
            } else {
                merged[uniqueId] = providerToDiscoveredNote(provider)
            }
        }

        val notes = merged.values.toList()
        return DiscoveryResult(
            notes = notes,
            scannedIds = notes.map { it.uniqueId }.toSet(),
            recoveryPath = "native",
            canDeleteStale = true,
            zeroPageRecovered = notes.count { it.pageCountRecovered }
        )
    }

    private fun providerToDiscoveredNote(provider: NoteReference): DiscoveredNote {
        val providerPageCount = (provider.pageCount ?: 0).coerceAtLeast(0)
        val providerPageIds = extractPageIdsFromProvider(provider)
        val pageResolution = resolvePageData(
            uniqueId = provider.uniqueId,
            pageIds = providerPageIds,
            pageCount = providerPageCount
        )
        return DiscoveredNote(
            uniqueId = provider.uniqueId,
            nativeTitle = null,
            providerTitle = sanitizeTitle(provider.title),
            pageCount = pageResolution.pageCount,
            pageIds = pageResolution.pageIds,
            modifiedTimestampMs = normalizeTimestampToMs(provider.updatedAt ?: provider.createdAt ?: 0L),
            pageCountRecovered = pageResolution.recovered,
            titleSource = "provider",
            pageSource = pageResolution.source
        )
    }

    private fun loadProviderNotesById(): Map<String, NoteReference> {
        return try {
            booxIntegration.getAllNotes().associateBy { it.uniqueId }
        } catch (e: Exception) {
            Log.w(TAG, "Provider fallback unavailable: ${e.message}")
            emptyMap()
        }
    }

    private fun resolvePageData(uniqueId: String, pageIds: List<String>, pageCount: Int): PageResolution {
        val providedPageIds = pageIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val livePages = BooxNative.getOnDevicePages(uniqueId)
        val livePageIds = livePages.map { it.pageId }
        val livePointPageIds = livePages.filter { it.hasPoints }.map { it.pageId }
        val liveShapeOnlyPageIds = livePages.filter { it.hasShapes && !it.hasPoints }.map { it.pageId }
        val pointDirectoryPageIds = BooxNative.getPageIdsFromPointDirectory(uniqueId)
        val shapeDirectoryPageIds = BooxNative.getPageIdsFromShapeDirectory(uniqueId)
        val knownLiveIds = (livePageIds + pointDirectoryPageIds + shapeDirectoryPageIds).toSet()

        val validatedProvidedPageIds = if (knownLiveIds.isEmpty()) {
            providedPageIds
        } else {
            providedPageIds.filter { it in knownLiveIds }
        }

        val resolvedPageIds = buildList {
            addAll(validatedProvidedPageIds)
            addAll(livePointPageIds)
            addAll(pointDirectoryPageIds)
            addAll(liveShapeOnlyPageIds)
            addAll(shapeDirectoryPageIds)
        }.distinct()

        val recovered = (pageCount <= 0 && resolvedPageIds.isNotEmpty()) ||
            (providedPageIds.isNotEmpty() && validatedProvidedPageIds.size != providedPageIds.size)
        val resolvedPageCount = if (resolvedPageIds.isNotEmpty()) resolvedPageIds.size else pageCount
        val source = when {
            validatedProvidedPageIds.isNotEmpty() && validatedProvidedPageIds.size == resolvedPageIds.size -> "provided_validated"
            livePointPageIds.isNotEmpty() && liveShapeOnlyPageIds.isEmpty() -> "live_point"
            pointDirectoryPageIds.isNotEmpty() -> "point_directory"
            liveShapeOnlyPageIds.isNotEmpty() || shapeDirectoryPageIds.isNotEmpty() -> "shape_directory"
            providedPageIds.isNotEmpty() && validatedProvidedPageIds.isEmpty() -> "stale_provided"
            else -> "none"
        }

        return PageResolution(
            pageIds = resolvedPageIds,
            pageCount = resolvedPageCount,
            recovered = recovered,
            source = source
        )
    }

    private fun parseStoredPageIds(pageIdsJson: String): Set<String> {
        return try {
            buildSet {
                val json = JSONArray(pageIdsJson)
                for (i in 0 until json.length()) {
                    val pageId = json.optString(i).trim()
                    if (pageId.isNotEmpty()) {
                        add(pageId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse stored page IDs: ${e.message}")
            emptySet()
        }
    }

    private suspend fun reconcileRemovedPages(existingNote: BooxNoteIndex?, currentPageIds: List<String>) {
        if (existingNote == null) return

        val previousPageIds = parseStoredPageIds(existingNote.pageIdsJson)
        if (previousPageIds.isEmpty()) return

        val removedPageIds = previousPageIds - currentPageIds.toSet()
        if (removedPageIds.isEmpty()) return

        removedPageIds.forEach { pageId ->
            try {
                unifiedOcrRepository.deleteOcrForPage(OcrSource.BOOX_NOTE, existingNote.uniqueId, pageId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear OCR for removed Boox page ${existingNote.uniqueId}/$pageId: ${e.message}")
            }
        }

        noteRepository.deleteSemanticAggregate(existingNote.uniqueId)

        Log.i(TAG, "Cleared OCR for ${removedPageIds.size} removed pages in note ${existingNote.uniqueId}")
    }

    private suspend fun cleanupStaleNotes(existingNoteIds: Set<String>, discovery: DiscoveryResult): Int {
        if (!discovery.canDeleteStale || discovery.scannedIds.isEmpty()) {
            return 0
        }

        val staleNoteIds = existingNoteIds - discovery.scannedIds
        if (staleNoteIds.isEmpty()) {
            return 0
        }

        var deletedCount = 0
        staleNoteIds.forEach { noteId ->
            try {
                unifiedOcrRepository.clearBySource(OcrSource.BOOX_NOTE, noteId)
                noteRepository.deleteSemanticAggregate(noteId)
                noteRepository.deleteLinkForNote(noteId)
                noteRepository.deleteNote(noteId)
                deletedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove stale Boox note $noteId: ${e.message}")
            }
        }

        if (deletedCount > 0) {
            noteRepository.invalidateLinksCache()
            Log.i(TAG, "Removed $deletedCount stale Boox notes with OCR/link cleanup")
        }

        return deletedCount
    }

    private fun collectDeletedNoteDates(
        existingNotes: Map<String, BooxNoteIndex>,
        discovery: DiscoveryResult
    ): Set<String> {
        if (!discovery.canDeleteStale || discovery.scannedIds.isEmpty()) {
            return emptySet()
        }

        return (existingNotes.keys - discovery.scannedIds)
            .mapNotNull { noteId -> existingNotes[noteId]?.originalDate?.takeIf { it.isNotBlank() } }
            .toSet()
    }

    private fun doesNoteChangeAffectMatching(
        existingNote: BooxNoteIndex?,
        updatedNote: BooxNoteIndex
    ): Boolean {
        if (existingNote == null) return true

        return existingNote.title != updatedNote.title ||
            existingNote.originalDate != updatedNote.originalDate ||
            existingNote.originalTimestamp != updatedNote.originalTimestamp ||
            existingNote.pageCount != updatedNote.pageCount ||
            existingNote.pageIdsJson != updatedNote.pageIdsJson ||
            existingNote.shapeFileModified != updatedNote.shapeFileModified
    }

    private fun getPageIdsFromShapeDirectory(uniqueId: String): List<String> {
        return BooxNative.getPageIdsFromShapeDirectory(uniqueId)
    }

    private fun extractPageIdsFromProvider(provider: NoteReference): List<String> {
        val raw = provider.pageNameList?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val pageId = arr.optString(i)
                    if (pageId.isNotBlank()) add(pageId)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeTimestampToMs(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw < 1_000_000_000_000L) raw * 1000 else raw
    }

    private fun sanitizeTitle(raw: String?): String? {
        val cleaned = raw?.trim()
        return when {
            cleaned.isNullOrEmpty() -> null
            cleaned.equals("null", ignoreCase = true) -> null
            else -> cleaned
        }
    }

    private fun resolveTitle(
        uniqueId: String,
        nativeTitle: String?,
        providerTitle: String?,
        originalDate: String?
    ): ResolvedTitle {
        val cleanedNative = sanitizeTitle(nativeTitle)
        if (cleanedNative != null) return ResolvedTitle(cleanedNative, "native")

        val cleanedProvider = sanitizeTitle(providerTitle)
        if (cleanedProvider != null) return ResolvedTitle(cleanedProvider, "provider")

        if (!originalDate.isNullOrBlank()) {
            return ResolvedTitle(formatDateAsTitle(originalDate), "derived")
        }

        val idSuffix = uniqueId.take(6).ifBlank { "unknown" }
        return ResolvedTitle("Boox Note • Undated • #$idSuffix", "generated")
    }

    /**
     * Auto-link newly discovered notes to events.
     */
    private suspend fun autoLinkNewNotes(noteIds: List<String>) {
        for (noteId in noteIds) {
            val note = noteRepository.getNoteById(noteId) ?: continue
            if (note.originalDate.isNotEmpty()) {
                autoLinkNotesForDate(note.originalDate, listOf(note))
            }
        }
    }

    private suspend fun autoLinkNotesForDates(dates: Set<String>) {
        dates.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach { date ->
                autoLinkNotesForDate(date)
            }
    }

    suspend fun refreshSmartLinksForDates(
        dates: Collection<String>,
        reason: String
    ) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("refreshSmartLinksForDates")
            return
        }

        val normalizedDates = dates
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .toSet()
        if (normalizedDates.isEmpty()) return

        val currentEventCount = smartLinkEventRepository.getEventCount()
        relinkDatesAfterChange(
            dates = normalizedDates,
            reason = reason,
            currentEventCount = currentEventCount,
            changeLabel = "Event dates changed"
        )
    }

    private suspend fun relinkDatesAfterChange(
        dates: Set<String>,
        reason: String,
        currentEventCount: Long,
        changeLabel: String
    ) {
        if (dates.isEmpty()) return

        if (quotaManager.isBackfillComplete()) {
            Log.d(TAG, "$changeLabel + backfill complete — auto-linking affected dates now (reason: $reason)")
            autoLinkNotesForDates(dates)
            recordDateScopedAutolinkCompletion(dates, currentEventCount)
        } else {
            Log.d(TAG, "$changeLabel — deferring auto-link until indexing completes (reason: $reason)")
            markPendingAutolinkForDates(dates, currentEventCount)
        }
    }

    /**
     * Extract original date from note metadata.
     * Priority: parsed title > shape file timestamp
     *
     * For partial-date titles (e.g. "April 24" with no year), the year is
     * derived from the shape file timestamp to avoid incorrectly assuming
     * the current year.
     */
    private fun extractOriginalDate(
        uniqueId: String,
        title: String?,
        shapeTimestamp: Long
    ): ResolvedDate {
        val warnings = mutableListOf<String>()

        // Derive reference year from shape file timestamp (for partial-date titles)
        val referenceYear = if (shapeTimestamp > 0) {
            Instant.ofEpochMilli(shapeTimestamp)
                .atZone(ZoneId.systemDefault())
                .year
        } else null
        val shapeDate = if (shapeTimestamp > 0) {
            Instant.ofEpochMilli(shapeTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else null

        // Try parsing title as date
        if (!title.isNullOrEmpty()) {
            val cleanedTitle = title.trim()
            val titleDate = booxIntegration.parseTitleAsDate(cleanedTitle, referenceYear)
            if (titleDate != null) {
                val isPartialTitle = isPartialDateTitle(cleanedTitle)
                val isFutureDate = titleDate.isAfter(LocalDate.now())
                val shapeDistanceDays = if (shapeDate != null) {
                    abs(java.time.temporal.ChronoUnit.DAYS.between(shapeDate, titleDate).toInt())
                } else {
                    0
                }
                val canTrustTitle = when {
                    isFutureDate -> {
                        warnings += "Rejected future title date '$cleanedTitle'"
                        false
                    }
                    shapeDate == null -> true
                    isPartialTitle && (titleDate.year != shapeDate.year || shapeDistanceDays > 31) -> {
                        warnings += "Rejected partial title date '$cleanedTitle' because it disagreed with shape date $shapeDate"
                        false
                    }
                    !isPartialTitle && shapeDistanceDays > 45 -> {
                        warnings += "Rejected title date '$cleanedTitle' because it disagreed with shape date $shapeDate"
                        false
                    }
                    else -> true
                }

                if (canTrustTitle) {
                    val dateStr = titleDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    // Use midnight of that date as timestamp
                    val timestamp = titleDate.atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    return ResolvedDate(
                        originalDate = dateStr,
                        originalTimestamp = if (shapeTimestamp > 0) shapeTimestamp else timestamp,
                        source = if (isPartialTitle) "title_partial" else "title",
                        warnings = warnings
                    )
                }
            }
        }

        // Fall back to shape file timestamp
        if (shapeDate != null) {
            return ResolvedDate(
                originalDate = shapeDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                originalTimestamp = shapeTimestamp,
                source = "shape_file",
                warnings = warnings
            )
        }

        return ResolvedDate(null, 0L, "unknown", warnings)
    }

    private fun isPartialDateTitle(title: String): Boolean {
        return Regex("^[A-Za-z]+\\s+\\d{1,2}$").matches(title.trim())
    }

    private fun parseWarningsArray(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val warning = array.optString(i).trim()
                if (warning.isNotEmpty()) {
                    add(warning)
                }
            }
        }
    }

    private fun warningsToJson(warnings: List<String>): String? {
        if (warnings.isEmpty()) return null
        return Json.encodeToString(warnings)
    }

    private fun computePreviewStatus(
        pageIds: List<String>,
        pageSource: String,
        hasTextContent: Boolean
    ): String {
        return when {
            pageIds.isNotEmpty() && pageSource == "shape_directory" -> "shape_only"
            pageIds.isNotEmpty() -> "page_ready"
            hasTextContent -> "text_only"
            pageSource == "stale_provided" -> "degraded"
            else -> "unavailable"
        }
    }

    /**
     * Format a date as a display title.
     */
    private fun formatDateAsTitle(date: String): String {
        return try {
            val localDate = LocalDate.parse(date)
            localDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } catch (e: Exception) {
            date
        }
    }

    /**
     * Auto-link all notes to events using intelligent scoring.
     * Called on every scan to ensure links stay updated when:
     * - New events are added to calendar
     * - Event IDs change after calendar re-sync
     * - Links were lost for any reason
     *
     * Only creates links for notes that don't already have manual links.
     */
    private suspend fun autoLinkAllNotes() {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Auto-link all notes")
            return
        }

        val allNotes = noteRepository.getAllNotes()
        val dateGroups = allNotes.groupBy { it.originalDate }

        for ((date, notes) in dateGroups) {
            autoLinkNotesForDate(date, notes)
        }
    }

    /**
     * Auto-link notes to events for a specific date using enhanced confidence-based algorithm.
     *
     * Enhanced Scoring (Dec 2025):
     * - Title match: +100 points
     * - During event: +60 points
     * - Grace after: +45 points (2 hours, 3 for evening events)
     * - Grace before: +25 points (30 min)
     * - Evening event bonus: +15 points
     * - Only timed event bonus: +20 points
     * - Long event bonus: +10 points
     *
     * Confidence-based linking:
     * - HIGH (≥80): Auto-link silently
     * - MEDIUM (50-79): Auto-link but flag needsAttention for review
     * - LOW (20-49): Don't auto-link, store suggestions for user
     * - NONE (<20): Link to day only (generic note)
     *
     * @param date The date to process notes for ("yyyy-MM-dd")
     * @param notes Optional list of notes (if null, fetched from repository)
     * @return Map of noteId to LinkingResult for UI processing
     */
    suspend fun autoLinkNotesForDate(
        date: String,
        notes: List<BooxNoteIndex>? = null
    ): Map<String, LinkingResult> {
        val results = mutableMapOf<String, LinkingResult>()
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Auto-link for date $date")
            return results
        }

        val notesForDate = notes ?: noteRepository.getNotesForDate(date)
        if (notesForDate.isEmpty()) return results

        val events = smartLinkEventRepository.getEventsForDateSync(date)

        // Filter to timed events only (all-day events can't be meaningfully matched)
        val timedEvents = events.filter { !it.isAllDay }
        val isOnlyTimedEvent = timedEvents.size == 1

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 8: Multi-Note Context - Pre-load ALL note profiles for cohesion
        // ═══════════════════════════════════════════════════════════════════
        val service = getSemanticServiceForMatching()
        val allNoteProfilesForDate = mutableListOf<SemanticProfile>()
        val noteProfileCache = mutableMapOf<String, List<SemanticProfile>?>()

        for (note in notesForDate) {
            val profiles = loadSemanticProfiles(note)
            noteProfileCache[note.uniqueId] = profiles
            profiles?.let { allNoteProfilesForDate.addAll(it) }
        }

        // Pre-calculate cohesion boost per event (Phase 8)
        // Cohesion is calculated once per event using ALL note profiles for the date
        val cohesionBoostCache = mutableMapOf<String, Float>()
        if (service != null && allNoteProfilesForDate.size >= 2) {
            for (event in timedEvents.filter { !EventExclusionRules.shouldExclude(it) }) {
                try {
                    val eventProfile = service.getOrCreateEventProfile(event)
                    val cohesionBoost = service.calculateDateCohesionBoost(allNoteProfilesForDate, eventProfile)
                    cohesionBoostCache[event.id] = cohesionBoost
                    if (cohesionBoost > 0f) {
                        Log.d(TAG, "Cohesion boost for event ${event.title}: $cohesionBoost (${allNoteProfilesForDate.size} notes)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate cohesion for ${event.id}: ${e.message}")
                }
            }
        }

        for (note in notesForDate) {
            // Use cached profiles (already loaded for cohesion calculation)
            val semanticProfiles = noteProfileCache[note.uniqueId]
            val noteDataQualityFlags = buildDataQualityFlags(
                noteProfile = semanticProfiles?.firstOrNull(),
                eventProfile = null,
                serviceAvailable = service != null,
                includeEventSemantics = false
            )

            // Check if already has manual link
            val existingLink = noteRepository.getLinkForNote(note.uniqueId)
            if (existingLink?.isManualLink == true) {
                continue  // Don't override manual links
            }

            // If no timed events, link to day only
            if (timedEvents.isEmpty()) {
                results[note.uniqueId] = LinkingResult.DayLinked(date)
                noteRepository.linkNoteToEventEnhanced(
                    noteId = note.uniqueId,
                    eventId = null,
                    linkedDayDate = date,
                    confidence = LinkConfidence.NONE,
                    confidenceScore = 0,
                    matchReasons = emptyList(),
                    suggestedEventIds = emptyList(),
                    needsAttention = false,
                    isManual = false,
                    decisionMode = SmartLinkDecisionMode.DAY_ONLY_FALLBACK,
                    engineVersion = SMART_LINK_ENGINE_VERSION,
                    dataQualityFlags = noteDataQualityFlags
                )
                continue
            }

            val scoredMatches = rankSameDayMatches(
                note = note,
                events = timedEvents,
                existingLink = existingLink,
                semanticProfilesCache = noteProfileCache,
                allowOnDemandEventProfiles = true,
                cohesionBoostCache = cohesionBoostCache
            ).map { it.scoredMatch }

            // Determine linking result based on confidence
            val result = when {
                scoredMatches.isEmpty() -> {
                    // No matches - link to day only
                    noteRepository.linkNoteToEventEnhanced(
                        noteId = note.uniqueId,
                        eventId = null,
                        linkedDayDate = date,
                        confidence = LinkConfidence.NONE,
                        confidenceScore = 0,
                        matchReasons = emptyList(),
                        suggestedEventIds = emptyList(),
                        needsAttention = false,
                        isManual = false,
                        decisionMode = SmartLinkDecisionMode.DAY_ONLY_FALLBACK,
                        engineVersion = SMART_LINK_ENGINE_VERSION,
                        dataQualityFlags = noteDataQualityFlags
                    )
                    LinkingResult.DayLinked(date)
                }

                scoredMatches.first().confidence == LinkConfidence.HIGH -> {
                    // HIGH confidence - auto-link silently
                    val best = scoredMatches.first()
                    noteRepository.linkNoteToEventEnhanced(
                        noteId = note.uniqueId,
                        eventId = best.eventId,
                        linkedDayDate = date,
                        confidence = LinkConfidence.HIGH,
                        confidenceScore = best.score,
                        matchReasons = best.reasons,
                        suggestedEventIds = emptyList(),
                        needsAttention = false,  // HIGH confidence = don't need review
                        isManual = false,
                        decisionMode = SmartLinkDecisionMode.SAME_DAY_AUTO_HIGH,
                        engineVersion = SMART_LINK_ENGINE_VERSION,
                        dataQualityFlags = best.dataQualityFlags
                    )
                    Log.d(TAG, "AUTO-LINKED (HIGH): '${note.title}' → '${best.eventTitle}' (score: ${best.score}, reasons: ${best.reasons})")
                    LinkingResult.AutoLinked(
                        eventId = best.eventId,
                        eventTitle = best.eventTitle,
                        confidence = best.confidence,
                        score = best.score,
                        reasons = best.reasons
                    )
                }

                scoredMatches.first().confidence == LinkConfidence.MEDIUM -> {
                    // MEDIUM confidence - auto-link but flag for review
                    val best = scoredMatches.first()
                    val alternatives = scoredMatches.drop(1).take(3)  // Top 3 alternatives
                    noteRepository.linkNoteToEventEnhanced(
                        noteId = note.uniqueId,
                        eventId = best.eventId,
                        linkedDayDate = date,
                        confidence = LinkConfidence.MEDIUM,
                        confidenceScore = best.score,
                        matchReasons = best.reasons,
                        suggestedEventIds = alternatives.map { it.eventId },
                        needsAttention = true,  // MEDIUM = flag for optional review
                        isManual = false,
                        decisionMode = SmartLinkDecisionMode.SAME_DAY_AUTO_MEDIUM,
                        engineVersion = SMART_LINK_ENGINE_VERSION,
                        dataQualityFlags = best.dataQualityFlags
                    )
                    Log.d(TAG, "AUTO-LINKED (MEDIUM): '${note.title}' → '${best.eventTitle}' (score: ${best.score}, needs review)")
                    LinkingResult.AutoLinkedNeedsReview(
                        eventId = best.eventId,
                        eventTitle = best.eventTitle,
                        score = best.score,
                        reasons = best.reasons,
                        alternatives = alternatives
                    )
                }

                else -> {
                    // LOW confidence - don't auto-link, just store suggestions
                    val suggestions = scoredMatches.take(4)  // Top 4 suggestions
                    noteRepository.linkNoteToEventEnhanced(
                        noteId = note.uniqueId,
                        eventId = null,  // Not auto-linked
                        linkedDayDate = date,
                        confidence = LinkConfidence.LOW,
                        confidenceScore = scoredMatches.first().score,
                        matchReasons = scoredMatches.first().reasons,
                        suggestedEventIds = suggestions.map { it.eventId },
                        needsAttention = true,  // LOW = needs user decision
                        isManual = false,
                        decisionMode = SmartLinkDecisionMode.SAME_DAY_SUGGESTION,
                        engineVersion = SMART_LINK_ENGINE_VERSION,
                        dataQualityFlags = scoredMatches.first().dataQualityFlags
                    )
                    Log.d(TAG, "SUGGESTIONS ONLY (LOW): '${note.title}' has ${suggestions.size} suggestions")
                    LinkingResult.NeedsAttention(suggestions)
                }
            }

            results[note.uniqueId] = result
        }

        return results
    }

    /**
     * Get suggestions for a specific note.
     * Useful for UI when user wants to see alternative event matches.
     * Filters out events the user has previously dismissed.
     */
    suspend fun getSuggestionsForNote(
        note: BooxNoteIndex,
        maxSuggestions: Int = 5
    ): List<ScoredMatch> {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Suggestions for note ${note.uniqueId}")
            return emptyList()
        }

        val events = smartLinkEventRepository.getEventsForDateSync(note.originalDate)
        return getSuggestionsForNote(note, events, maxSuggestions)
    }

    suspend fun getSuggestionsForNote(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        maxSuggestions: Int
    ): List<ScoredMatch> {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Suggestions for note ${note.uniqueId}")
            return emptyList()
        }

        val timedEvents = events.filter { !it.isAllDay }
        if (timedEvents.isEmpty()) return emptyList()

        return rankSameDayMatches(
            note = note,
            events = timedEvents,
            existingLink = noteRepository.getLinkForNote(note.uniqueId),
            allowOnDemandEventProfiles = true
        )
            .map { it.scoredMatch }
            .take(maxSuggestions)
    }

    /**
     * Get the best matching event for a note from a provided list.
     * Returns null if no meaningful match is found.
     */
    suspend fun getBestEventMatchForNote(
        note: BooxNoteIndex,
        events: List<CalendarEvent>
    ): CalendarEvent? {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Best-event lookup for note ${note.uniqueId}")
            return null
        }

        val timedEvents = events.filter { !it.isAllDay }
        if (timedEvents.isEmpty()) return null

        return rankSameDayMatches(
            note = note,
            events = timedEvents,
            existingLink = noteRepository.getLinkForNote(note.uniqueId),
            allowOnDemandEventProfiles = true
        ).firstOrNull()?.event
    }

    /**
     * Calculate match score between a note and an event.
     * Returns detailed scoring with confidence level and match reasons.
     *
     * Enhanced Algorithm (Dec 2025):
     * - Extended grace periods (2 hours, 3 hours for evening)
     * - Bonus points for evening events, only-event-of-day, long events
     * - Returns match reasons for UI transparency
     *
     * @param note The Boox note to match
     * @param event The calendar event to match against
     * @param isOnlyTimedEvent True if this is the only timed event on the day
     * @return ScoredMatch with score, confidence, and reasons
     */
    private fun calculateMatchScoreEnhanced(
        note: BooxNoteIndex,
        event: CalendarEvent,
        isOnlyTimedEvent: Boolean = false
    ): ScoredMatch {
        var score = 0
        val reasons = mutableListOf<MatchReason>()
        val eventText = buildEventText(event)

        val noteTime = note.originalTimestamp
        val eventStart = event.startTime
        val eventEnd = event.endTime ?: (eventStart + DEFAULT_EVENT_DURATION_MS)
        val eventDuration = eventEnd - eventStart

        // Check if this is an evening event (after 5 PM)
        val isEveningEvent = isEveningTime(eventStart)

        // Use extended grace period for evening events
        val effectiveGraceAfter = if (isEveningEvent) EVENING_GRACE_AFTER_MS else GRACE_AFTER_EVENT_MS

        // ═══════════════════════════════════════════════════════════════════
        // SCORING LOGIC
        // ═══════════════════════════════════════════════════════════════════

        // 1. Title match (strongest signal)
        if (hasTitleMatch(note.title, eventText)) {
            score += SCORE_TITLE_MATCH
            reasons.add(MatchReason.TITLE_MATCH)
        }

        // 2. Temporal proximity scoring
        when {
            // Note created during event
            noteTime in eventStart..eventEnd -> {
                score += SCORE_DURING_EVENT
                reasons.add(MatchReason.DURING_EVENT)
            }

            // Note created within grace period after event ended (follow-up notes)
            noteTime > eventEnd && noteTime <= eventEnd + effectiveGraceAfter -> {
                val msAfter = noteTime - eventEnd
                val decayFactor = 1.0 - (msAfter.toDouble() / effectiveGraceAfter)
                score += (SCORE_GRACE_AFTER_MAX * decayFactor).toInt()
                reasons.add(MatchReason.SHORTLY_AFTER_EVENT)
            }

            // Note created within grace period before event started (prep notes)
            noteTime >= eventStart - GRACE_BEFORE_EVENT_MS && noteTime < eventStart -> {
                val msBefore = eventStart - noteTime
                val decayFactor = 1.0 - (msBefore.toDouble() / GRACE_BEFORE_EVENT_MS)
                score += (SCORE_GRACE_BEFORE_MAX * decayFactor).toInt()
                reasons.add(MatchReason.SHORTLY_BEFORE_EVENT)
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // BONUS SCORES (new in Dec 2025)
        // ═══════════════════════════════════════════════════════════════════

        // 3. Evening event bonus
        // Rationale: People often keep meeting notes open and save them late
        if (isEveningEvent && reasons.isNotEmpty()) {
            score += BONUS_EVENING_EVENT
            reasons.add(MatchReason.EVENING_EVENT)
        }

        // 4. Only timed event of day bonus
        // Rationale: If there's only one meeting, notes almost certainly belong to it
        if (isOnlyTimedEvent && reasons.isNotEmpty()) {
            score += BONUS_ONLY_EVENT
            reasons.add(MatchReason.ONLY_EVENT_OF_DAY)
        }

        // 5. Long event bonus
        // Rationale: Workshops/long meetings are more likely to have associated notes
        if (eventDuration >= LONG_EVENT_THRESHOLD_MS && reasons.isNotEmpty()) {
            score += BONUS_LONG_EVENT
            reasons.add(MatchReason.LONG_EVENT)
        }

        // ═══════════════════════════════════════════════════════════════════
        // PENALTY SCORES (Fix #10 - Jan 2026)
        // ═══════════════════════════════════════════════════════════════════

        // 6. Test/placeholder content penalty
        // Rationale: Test notes like "test meeting notes" shouldn't match real events
        val noteIsTest = isTestContent(note.title)
        val eventIsTest = isTestContent(eventText)
        if (noteIsTest || eventIsTest) {
            score += PENALTY_TEST_CONTENT
            reasons.add(MatchReason.TEST_CONTENT_PENALTY)
        }

        val confidence = LinkConfidence.fromScore(score.coerceAtLeast(0))
        return ScoredMatch(
            eventId = event.id,
            eventTitle = event.title,
            score = score,
            confidence = confidence,
            reasons = reasons
        )
    }

    /**
     * Calculate match score with semantic enhancement.
     * Uses tiered bonus calculation based on semantic score quality:
     * - Score ≥ 0.40: Full bonus (up to 80 pts)
     * - Score ≥ 0.25: 80% bonus (up to 64 pts)
     * - Score ≥ 0.10: 50% bonus (up to 40 pts)
     */
    private fun calculateMatchScoreEnhancedWithSemantic(
        note: BooxNoteIndex,
        event: CalendarEvent,
        isOnlyTimedEvent: Boolean,
        semanticProfiles: List<SemanticProfile>?,
        eventSemanticTokens: SemanticGeniusService.EventSemanticTokens? = null
    ): ScoredMatch {
        val base = calculateMatchScoreEnhanced(note, event, isOnlyTimedEvent)
        val service = getSemanticServiceForMatching()
        if (service == null || semanticProfiles.isNullOrEmpty()) return base

        val semanticScore = if (eventSemanticTokens != null) {
            service.calculateEventSemanticScoreFromTokens(
                profiles = semanticProfiles,
                tokens = eventSemanticTokens
            )
        } else {
            service.calculateEventSemanticScore(
                profiles = semanticProfiles,
                eventTitle = event.title,
                eventDescription = event.description
            )
        }
        if (semanticScore < SEMANTIC_SCORE_MIN) return base

        // Tiered bonus calculation based on semantic score quality
        val bonusMultiplier = when {
            semanticScore >= SEMANTIC_HIGH_QUALITY -> 1.0f      // High quality: full bonus
            semanticScore >= 0.25f -> 0.8f                      // Medium quality: 80% bonus
            else -> 0.5f                                        // Low quality: 50% bonus
        }

        val semanticBonus = (semanticScore * SEMANTIC_BONUS_MAX * bonusMultiplier).toInt()
        if (semanticBonus <= 0) return base

        val newScore = base.score + semanticBonus
        return base.copy(
            reasons = SmartLinkDecisionSupport.mergeMatchReasons(
                base.reasons,
                addSemanticContext = true
            ),
            score = newScore,
            confidence = LinkConfidence.fromScore(newScore)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HYBRID SCORING (Phase 4 - Jan 2026)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate hybrid score combining temporal and semantic signals.
     *
     * Formula: finalScore = temporalScore × (1.0 + semanticAffinity × SEMANTIC_WEIGHT)
     *
     * Where:
     * - temporalScore: 0-180 points (existing scoring)
     * - semanticAffinity: 0.0-1.0 (new semantic profile matching)
     * - SEMANTIC_WEIGHT: 0.8 (semantic can boost by up to 80%)
     *
     * Example: temporalScore=60, semanticAffinity=0.5 → 60 × (1 + 0.5 × 0.8) = 60 × 1.4 = 84
     *
     * @param note The Boox note to match
     * @param event The calendar event to match against
     * @param isOnlyTimedEvent Whether this is the only timed event on the day
     * @param noteProfile Pre-loaded semantic profile for the note (optional)
     * @param eventProfile Pre-loaded semantic profile for the event (optional, will fetch on-demand)
     * @param cohesionBoost Pre-calculated cohesion boost from multi-note context (Phase 8)
     * @return HybridScoredMatch with score breakdown
     */
    private suspend fun calculateHybridScore(
        note: BooxNoteIndex,
        event: CalendarEvent,
        isOnlyTimedEvent: Boolean,
        noteProfile: SemanticProfile?,
        eventProfile: SemanticProfile?,
        cohesionBoost: Float = 0f
    ): HybridScoredMatch {
        val service = getSemanticServiceForMatching()

        // Layer 1: Temporal scoring (existing algorithm)
        val temporalResult = calculateMatchScoreEnhanced(note, event, isOnlyTimedEvent)

        // Layer 2: Semantic affinity (profile-to-profile comparison)
        val semanticAffinity = if (service != null && noteProfile != null && eventProfile != null) {
            service.calculateSemanticAffinity(noteProfile, eventProfile)
        } else {
            0f
        }

        // Layer 3: Entity co-occurrence boost (Phase 5 - SemanticGenius Transformation)
        val cooccurrenceBoost = if (service != null && noteProfile != null && eventProfile != null) {
            service.calculateCooccurrenceBoost(noteProfile, eventProfile)
        } else {
            0f
        }

        // Layer 4: Cohesion boost (Phase 8 - Multi-Note Context)
        // Cohesion boost is passed in as a parameter since it's calculated at the batch level
        // (requires knowledge of all note profiles for the date)

        // Apply weight multiplier for reduced-weight events (check-ins, all-hands)
        val weightMultiplier = EventExclusionRules.getWeightMultiplier(event)

        // Combine: temporal is base, semantic is multiplier
        // cohesionBoost is added to the multiplier (not multiplied by weight)
        val semanticMultiplier = 1.0f + (semanticAffinity * SEMANTIC_WEIGHT * weightMultiplier) + cooccurrenceBoost + cohesionBoost
        val finalScore = (temporalResult.score * semanticMultiplier).toInt()

        // Build semantic reasons
        val semanticReasons = buildSemanticReasons(noteProfile, eventProfile, service)

        return HybridScoredMatch(
            eventId = event.id,
            eventTitle = event.title,
            temporalScore = temporalResult.score,
            semanticAffinity = semanticAffinity,
            cooccurrenceBoost = cooccurrenceBoost,
            cohesionBoost = cohesionBoost,
            finalScore = finalScore,
            confidence = LinkConfidence.fromScore(finalScore),
            temporalReasons = temporalResult.reasons,
            semanticReasons = semanticReasons
        )
    }

    /**
     * Build list of semantic match reasons explaining why profiles matched.
     */
    private fun buildSemanticReasons(
        noteProfile: SemanticProfile?,
        eventProfile: SemanticProfile?,
        service: SemanticGeniusService?
    ): List<SemanticMatchReason> {
        if (noteProfile == null || eventProfile == null || service == null) {
            return emptyList()
        }

        val reasons = mutableListOf<SemanticMatchReason>()

        // 1. Domain match
        if (noteProfile.domain != null && eventProfile.domain != null &&
            noteProfile.domain == eventProfile.domain) {
            reasons.add(SemanticMatchReason.DOMAIN_MATCH)
        }

        // 2. Intent compatibility
        val noteIntents = noteProfile.intents.map { it.intent }.toSet()
        val eventIntents = eventProfile.intents.map { it.intent }.toSet()
        if (noteIntents.intersect(eventIntents).isNotEmpty()) {
            reasons.add(SemanticMatchReason.INTENT_COMPATIBLE)
        }

        // 3. Entity matches (person, org, location)
        val noteEntities = noteProfile.entities.groupBy { it.type }
        val eventEntities = eventProfile.entities.groupBy { it.type }

        val notePersons = noteEntities[EntityType.PERSON]
            ?.map { it.text.lowercase() } ?: emptyList()
        val eventPersons = eventEntities[EntityType.PERSON]
            ?.map { it.text.lowercase() } ?: emptyList()
        if (notePersons.any { np -> eventPersons.any { ep -> np.contains(ep) || ep.contains(np) } }) {
            reasons.add(SemanticMatchReason.ENTITY_PERSON_MATCH)
        }

        val noteOrgs = noteEntities[EntityType.ORG]
            ?.map { it.text.lowercase() } ?: emptyList()
        val eventOrgs = eventEntities[EntityType.ORG]
            ?.map { it.text.lowercase() } ?: emptyList()
        if (noteOrgs.any { no -> eventOrgs.any { eo -> no.contains(eo) || eo.contains(no) } }) {
            reasons.add(SemanticMatchReason.ENTITY_ORG_MATCH)
        }

        val noteLocs = noteEntities[EntityType.LOCATION]
            ?.map { it.text.lowercase() } ?: emptyList()
        val eventLocs = eventEntities[EntityType.LOCATION]
            ?.map { it.text.lowercase() } ?: emptyList()
        if (noteLocs.any { nl -> eventLocs.any { el -> nl.contains(el) || el.contains(nl) } }) {
            reasons.add(SemanticMatchReason.ENTITY_LOCATION_MATCH)
        }

        // 4. Keyword overlap
        val noteKeywords = noteProfile.keywords.map { it.term.lowercase() }.toSet()
        val eventKeywords = eventProfile.keywords.map { it.term.lowercase() }.toSet()
        if (noteKeywords.intersect(eventKeywords).isNotEmpty()) {
            reasons.add(SemanticMatchReason.KEYWORD_OVERLAP)
        }

        // 5. Key phrase match
        val notePhrases = noteProfile.keyPhrases.map { it.lowercase() }.toSet()
        val eventPhrases = eventProfile.keyPhrases.map { it.lowercase() }.toSet()
        if (notePhrases.any { np -> eventPhrases.any { ep -> np.contains(ep) || ep.contains(np) } }) {
            reasons.add(SemanticMatchReason.KEY_PHRASE_MATCH)
        }

        // 6. Action item match
        if (noteProfile.actionItems.isNotEmpty()) {
            val eventText = (eventProfile.keywords.map { it.term } + eventProfile.keyPhrases)
                .joinToString(" ").lowercase()
            if (noteProfile.actionItems.any { action ->
                eventText.contains(action.verb.lowercase()) ||
                action.objectText.split(Regex("\\s+")).any { eventText.contains(it.lowercase()) }
            }) {
                reasons.add(SemanticMatchReason.ACTION_ITEM_MATCH)
            }
        }

        // 7. Synopsis similarity
        if (!noteProfile.synopsis.isNullOrBlank()) {
            val synopsisAffinity = service.calculateSynopsisAffinity(noteProfile, eventProfile.keywords.firstOrNull()?.term ?: "")
            if (synopsisAffinity > 0.05f) {
                reasons.add(SemanticMatchReason.SYNOPSIS_SIMILARITY)
            }
        }

        return reasons
    }

    /**
     * Legacy method for backward compatibility.
     * Calculate match score between a note and an event.
     * Higher score = better match.
     */
    private fun calculateMatchScore(note: BooxNoteIndex, event: CalendarEvent): Int {
        return calculateMatchScoreEnhanced(note, event, isOnlyTimedEvent = false).score
    }

    private suspend fun loadSemanticProfiles(note: BooxNoteIndex): List<SemanticProfile>? {
        val aggregateProfile = loadNoteSemanticProfile(note) ?: return null
        return listOf(aggregateProfile)
    }

    private suspend fun loadNoteSemanticProfile(note: BooxNoteIndex): SemanticProfile? {
        val service = getSemanticServiceForMatching() ?: return null
        val eligibleSamplePageIds = getEligibleSamplePageIds(note)
        if (eligibleSamplePageIds.isEmpty()) {
            noteRepository.deleteSemanticAggregate(note.uniqueId)
            return null
        }

        val existingAggregate = noteRepository.getSemanticAggregate(note.uniqueId)
        if (existingAggregate != null &&
            isSemanticAggregateValid(note, existingAggregate, eligibleSamplePageIds) &&
            existingAggregate.samplePageCount >= eligibleSamplePageIds.size) {
            return existingAggregate.toSemanticProfile(note.title, note.originalDate)
        }

        val orderedProfiles = try {
            loadEligiblePageProfiles(note, service, eligibleSamplePageIds)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load semantic profiles for ${note.uniqueId}: ${e.message}")
            return existingAggregate
                ?.takeIf { isSemanticAggregateValid(note, it, eligibleSamplePageIds) }
                ?.toSemanticProfile(note.title, note.originalDate)
        }

        if (orderedProfiles.isEmpty()) {
            noteRepository.deleteSemanticAggregate(note.uniqueId)
            return null
        }

        if (existingAggregate != null &&
            isSemanticAggregateValid(note, existingAggregate, eligibleSamplePageIds) &&
            existingAggregate.samplePageCount == orderedProfiles.size) {
            return existingAggregate.toSemanticProfile(note.title, note.originalDate)
        }

        val rebuiltAggregate = BooxNoteSemanticAggregateBuilder.build(
            note = note,
            eligibleSamplePageIds = eligibleSamplePageIds,
            orderedProfiles = orderedProfiles,
            configHash = quotaManager.configHash
        )
        noteRepository.upsertSemanticAggregate(rebuiltAggregate)
        return rebuiltAggregate.toSemanticProfile(note.title, note.originalDate)
    }

    private fun getEligibleSamplePageIds(note: BooxNoteIndex): List<String> {
        val pageIds = parseOrderedPageIds(note.pageIdsJson)
        if (pageIds.isEmpty()) return emptyList()
        return pageIds.take(quotaManager.booxNotesPagesPerNote.coerceAtLeast(0))
    }

    private fun isSemanticAggregateValid(
        note: BooxNoteIndex,
        aggregate: BooxNoteSemanticAggregate,
        eligibleSamplePageIds: List<String>
    ): Boolean {
        return aggregate.aggregateVersion == BooxNoteSemanticAggregateBuilder.AGGREGATE_VERSION &&
            aggregate.sourceModifiedAt == note.shapeFileModified &&
            aggregate.configHashUsed == quotaManager.configHash &&
            aggregate.expectedSamplePageIds() == eligibleSamplePageIds
    }

    private suspend fun loadEligiblePageProfiles(
        note: BooxNoteIndex,
        service: SemanticGeniusService,
        eligibleSamplePageIds: List<String>
    ): List<SemanticProfile> {
        val profiles = service.getProfilesForSource(OcrSource.BOOX_NOTE, note.uniqueId)
        if (profiles.isEmpty()) {
            return emptyList()
        }

        val profilesByPageId = profiles.associateBy { it.pageId }
        return eligibleSamplePageIds.mapNotNull { pageId -> profilesByPageId[pageId] }
    }

    private suspend fun preloadSemanticProfilesFromAggregates(
        notes: List<BooxNoteIndex>,
        cache: MutableMap<String, List<SemanticProfile>?>
    ) {
        if (notes.isEmpty()) return

        val notesById = notes.associateBy { it.uniqueId }
        val aggregates = noteRepository.getSemanticAggregates(notes.map { it.uniqueId })
        var cachedCount = 0

        aggregates.forEach { aggregate ->
            val note = notesById[aggregate.noteUniqueId] ?: return@forEach
            val eligibleSamplePageIds = getEligibleSamplePageIds(note)
            if (eligibleSamplePageIds.isEmpty()) return@forEach
            if (!isSemanticAggregateValid(note, aggregate, eligibleSamplePageIds)) return@forEach
            if (aggregate.samplePageCount < eligibleSamplePageIds.size) return@forEach

            cache[note.uniqueId] = listOf(aggregate.toSemanticProfile(note.title, note.originalDate))
            cachedCount++
        }

        if (cachedCount > 0) {
            Log.d(TAG, "Preloaded $cachedCount full-coverage note semantic aggregates")
        }
    }

    private fun parseOrderedPageIds(pageIdsJson: String): List<String> {
        return try {
            val json = JSONArray(pageIdsJson)
            buildList {
                for (i in 0 until json.length()) {
                    val pageId = json.optString(i).trim()
                    if (pageId.isNotEmpty()) {
                        add(pageId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Boox page order: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadSemanticProfilesCached(
        note: BooxNoteIndex,
        cache: MutableMap<String, List<SemanticProfile>?>?
    ): List<SemanticProfile>? {
        if (cache == null) return loadSemanticProfiles(note)
        if (cache.containsKey(note.uniqueId)) return cache[note.uniqueId]
        val loaded = loadSemanticProfiles(note)
        cache[note.uniqueId] = loaded
        return loaded
    }

    private suspend fun buildNoteMatchContext(
        note: BooxNoteIndex,
        existingLink: BooxNoteLink? = null,
        semanticProfilesCache: MutableMap<String, List<SemanticProfile>?>? = null
    ): NoteMatchContext {
        val resolvedExistingLink = existingLink ?: noteRepository.getLinkForNote(note.uniqueId)
        val semanticProfiles = loadSemanticProfilesCached(note, semanticProfilesCache)
        return NoteMatchContext(
            semanticProfiles = semanticProfiles,
            noteProfile = semanticProfiles?.firstOrNull(),
            dismissedEventIds = SmartLinkDecisionSupport.parseDismissedEventIds(
                resolvedExistingLink?.dismissedEventIds
            )
        )
    }

    private suspend fun loadEventProfileForMatching(
        event: CalendarEvent,
        eventProfilesMap: Map<String, SemanticProfile>?,
        allowOnDemandEventProfiles: Boolean
    ): SemanticProfile? {
        val preloadedProfile = eventProfilesMap?.get(event.id)
        if (preloadedProfile != null || !allowOnDemandEventProfiles) {
            return preloadedProfile
        }

        val service = getSemanticServiceForMatching() ?: return null
        return try {
            service.getOrCreateEventProfile(event)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load event profile for ${event.id}: ${e.message}")
            null
        }
    }

    private fun buildDataQualityFlags(
        noteProfile: SemanticProfile?,
        eventProfile: SemanticProfile?,
        serviceAvailable: Boolean,
        includeEventSemantics: Boolean,
        aiGenerated: Boolean = false
    ): List<SmartLinkDataQualityFlag> {
        val flags = mutableListOf<SmartLinkDataQualityFlag>()

        if (!quotaManager.isOcrFeatureEnabled || !quotaManager.isSemanticExtractionEnabled || !serviceAvailable) {
            flags.add(SmartLinkDataQualityFlag.SEMANTIC_RUNTIME_DISABLED)
        } else {
            if (noteProfile == null) {
                flags.add(SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS)
            }
            if (includeEventSemantics && eventProfile == null) {
                flags.add(SmartLinkDataQualityFlag.NO_EVENT_SEMANTICS)
            }
        }

        if (aiGenerated) {
            flags.add(SmartLinkDataQualityFlag.AI_GENERATED)
        }

        return SmartLinkDecisionSupport.mergeDataQualityFlags(flags)
    }

    private fun calculateCandidateDayOffset(
        note: BooxNoteIndex,
        event: CalendarEvent
    ): Int {
        return try {
            java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(note.originalDate),
                LocalDate.parse(event.dayId)
            ).toInt()
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
    }

    private fun calculateCandidateMidpointDistanceMs(
        note: BooxNoteIndex,
        event: CalendarEvent
    ): Long {
        val eventEnd = event.endTime ?: (event.startTime + DEFAULT_EVENT_DURATION_MS)
        val midpoint = event.startTime + ((eventEnd - event.startTime) / 2)
        return abs(note.originalTimestamp - midpoint)
    }

    private fun sortScoredEventCandidates(
        candidates: List<ScoredEventCandidate>
    ): List<ScoredEventCandidate> {
        return candidates.sortedWith(
            compareByDescending<ScoredEventCandidate> { it.scoredMatch.score }
                .thenByDescending { it.semanticAffinity }
                .thenBy { abs(it.dayOffset) }
                .thenBy { it.midpointDistanceMs }
                .thenBy { it.event.startTime }
                .thenBy { it.event.id }
        )
    }

    private suspend fun buildScoredEventCandidates(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        scoringMode: SmartLinkScoringMode,
        existingLink: BooxNoteLink? = null,
        semanticProfilesCache: MutableMap<String, List<SemanticProfile>?>? = null,
        eventProfilesMap: Map<String, SemanticProfile>? = null,
        allowOnDemandEventProfiles: Boolean = true,
        cohesionBoostCache: Map<String, Float> = emptyMap(),
        searchDays: Int? = null
    ): List<ScoredEventCandidate> {
        val matchContext = buildNoteMatchContext(note, existingLink, semanticProfilesCache)
        val service = getSemanticServiceForMatching()
        val eligibleEvents = SmartLinkDecisionSupport.filterEligibleEvents(
            note = note,
            events = events,
            dismissedEventIds = matchContext.dismissedEventIds,
            searchDays = searchDays
        )
        if (eligibleEvents.isEmpty()) return emptyList()

        val isOnlyTimedEvent = scoringMode == SmartLinkScoringMode.SAME_DAY && eligibleEvents.size == 1

        return eligibleEvents.map { event ->
            val eventProfile = loadEventProfileForMatching(
                event = event,
                eventProfilesMap = eventProfilesMap,
                allowOnDemandEventProfiles = allowOnDemandEventProfiles
            )
            val cohesionBoost = if (scoringMode == SmartLinkScoringMode.SAME_DAY) {
                cohesionBoostCache[event.id] ?: 0f
            } else {
                0f
            }
            val dataQualityFlags = buildDataQualityFlags(
                noteProfile = matchContext.noteProfile,
                eventProfile = eventProfile,
                serviceAvailable = service != null,
                includeEventSemantics = true
            )

            val (scoredMatch, semanticAffinity) =
                if (matchContext.noteProfile != null && eventProfile != null && service != null) {
                    val hybrid = calculateHybridScore(
                        note = note,
                        event = event,
                        isOnlyTimedEvent = isOnlyTimedEvent,
                        noteProfile = matchContext.noteProfile,
                        eventProfile = eventProfile,
                        cohesionBoost = cohesionBoost
                    )
                    ScoredMatch(
                        eventId = hybrid.eventId,
                        eventTitle = hybrid.eventTitle,
                        score = hybrid.finalScore,
                        confidence = hybrid.confidence,
                        reasons = SmartLinkDecisionSupport.mergeMatchReasons(
                            hybrid.temporalReasons,
                            addSemanticContext =
                                hybrid.semanticAffinity > 0f ||
                                    hybrid.cooccurrenceBoost > 0f ||
                                    hybrid.cohesionBoost > 0f ||
                                    hybrid.semanticReasons.isNotEmpty()
                        ),
                        dataQualityFlags = dataQualityFlags
                    ) to hybrid.semanticAffinity
                } else {
                    calculateMatchScoreEnhancedWithSemantic(
                        note = note,
                        event = event,
                        isOnlyTimedEvent = isOnlyTimedEvent,
                        semanticProfiles = matchContext.semanticProfiles
                    ).copy(dataQualityFlags = dataQualityFlags) to 0f
                }

            ScoredEventCandidate(
                event = event,
                scoredMatch = scoredMatch,
                semanticAffinity = semanticAffinity,
                dayOffset = calculateCandidateDayOffset(note, event),
                midpointDistanceMs = calculateCandidateMidpointDistanceMs(note, event)
            )
        }
    }

    private suspend fun rankSameDayMatches(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        existingLink: BooxNoteLink? = null,
        semanticProfilesCache: MutableMap<String, List<SemanticProfile>?>? = null,
        eventProfilesMap: Map<String, SemanticProfile>? = null,
        allowOnDemandEventProfiles: Boolean = true,
        cohesionBoostCache: Map<String, Float> = emptyMap()
    ): List<ScoredEventCandidate> {
        val scoredCandidates = buildScoredEventCandidates(
            note = note,
            events = events,
            scoringMode = SmartLinkScoringMode.SAME_DAY,
            existingLink = existingLink,
            semanticProfilesCache = semanticProfilesCache,
            eventProfilesMap = eventProfilesMap,
            allowOnDemandEventProfiles = allowOnDemandEventProfiles,
            cohesionBoostCache = cohesionBoostCache
        )
        return sortScoredEventCandidates(
            scoredCandidates.filter { it.scoredMatch.score >= SCORE_MINIMUM }
        )
    }

    private fun buildEventSemanticTokensMap(
        events: List<CalendarEvent>
    ): Map<String, SemanticGeniusService.EventSemanticTokens> {
        val service = getSemanticServiceForMatching() ?: return emptyMap()
        return events.asSequence()
            .filter { !it.isAllDay }
            .associate { event ->
                event.id to service.buildEventSemanticTokens(
                    eventTitle = event.title,
                    eventDescription = event.description,
                    eventLocation = event.location,  // Include location for enhanced scoring
                    descriptionCharLimit = EVENT_DESCRIPTION_MAX_CHARS
                )
            }
    }

    /**
     * Batch load EXISTING event profiles for bulk analysis.
     * Only loads profiles that have already been indexed - NO on-demand creation.
     * Events without profiles will use fallback temporal scoring.
     *
     * This is critical for performance - on-demand indexing takes 100-700ms per event
     * which would make bulk analysis unusably slow.
     *
     * @param events List of CalendarEvents to load profiles for
     * @return Map of event ID to SemanticProfile (only for events with existing profiles)
     */
    private suspend fun loadEventProfilesBatch(
        events: List<CalendarEvent>
    ): Map<String, SemanticProfile> {
        val service = getSemanticServiceForMatching() ?: return emptyMap()
        val result = mutableMapOf<String, SemanticProfile>()
        for (event in events) {
            if (event.isAllDay) continue
            try {
                // Use getEventProfileIfExists - returns null if not indexed (no on-demand creation)
                service.getEventProfileIfExists(event.id)?.let { profile ->
                    result[event.id] = profile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load profile for event ${event.id}: ${e.message}")
            }
        }
        return result
    }

    /**
     * Check if a timestamp falls in evening hours (after 5 PM).
     */
    private fun isEveningTime(timestamp: Long): Boolean {
        val localTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return localTime.hour >= EVENING_START_HOUR
    }

    /** Whether the note title names the event (whole words only; see [TitleMatcher]). */
    private fun hasTitleMatch(noteTitle: String?, eventText: String): Boolean =
        findTitleMatchTerm(noteTitle, eventText) != null

    /** The event word the note title names (null = no title match); shown as a suggestion's reason. */
    private fun findTitleMatchTerm(noteTitle: String?, eventText: String): String? =
        TitleMatcher.matchTerm(noteTitle, eventText) { abbreviations.expand(it) }

    /** The word from [event]'s title or description that matched [note]'s title, or null. */
    fun titleMatchTerm(note: BooxNoteIndex, event: CalendarEvent): String? =
        findTitleMatchTerm(note.title, buildEventText(event))

    // ═══════════════════════════════════════════════════════════════════════════
    // CROSS-DAY SMART LINK FUNCTIONS (Dec 2025)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if note was created in morning hours (before noon).
     * Used for time-of-day context bonus.
     */
    private fun isMorningNote(note: BooxNoteIndex): Boolean {
        val localTime = Instant.ofEpochMilli(note.originalTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return localTime.hour < 12
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LDS CONTEXT DETECTION (Jan 2026 - Fix #8)
    // High-confidence LDS indicators that disambiguate from generic business terms
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * High-confidence LDS context indicators.
     * These terms are almost exclusively LDS-related and serve as strong signals.
     * When BOTH note and event have these, we boost the LDS domain match.
     */
    private val LDS_CONTEXT_INDICATORS = setOf(
        // Organizational structure (unique to LDS)
        "bishop", "bishopric", "stake", "ward", "quorum", "priesthood",
        "relief", "society", "elders", "deacon", "priest", "elder",
        // Meetings (LDS-specific names)
        "sacrament", "pec", "byc", "ministering", "tithing", "testimony",
        // Callings and ordinances
        "calling", "sustaining", "release", "ordain", "recommend", "temple",
        "endowment", "sealing",
        // Programs
        "seminary", "mutual", "fsy", "efy", "trek", "primary", "nursery",
        // Abbreviations
        "rs", "eq", "ym", "yw", "ss", "ctr", "lcr", "bom"
    )

    /**
     * Very strong LDS indicators - unique enough that a single occurrence
     * confirms LDS context without needing a second indicator.
     */
    private val VERY_STRONG_LDS_INDICATORS = setOf(
        "bishopric", "priesthood", "sacrament", "tithing",
        "ministering", "pec", "byc", "endowment", "sealing"
    )

    /**
     * Tokenize text for context detection.
     * Returns set of whole words (not substrings) for accurate matching.
     *
     * BUGFIX (Jan 2026): Previously used substring contains() which caused
     * false positives: "progress" matched "rs", "assessment" matched "ss".
     */
    private fun tokenizeForContext(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * Check if text has strong LDS context.
     * Returns true if 2+ LDS indicators found or 1 very strong indicator.
     *
     * BUGFIX (Jan 2026): Now uses token-based matching instead of substring
     * contains() to prevent false positives like "rs" in "progress".
     */
    private fun hasLdsContext(text: String): Boolean {
        // Tokenize to whole words - prevents substring false positives
        val tokens = tokenizeForContext(text)

        var count = 0
        var hasVeryStrong = false

        for (indicator in LDS_CONTEXT_INDICATORS) {
            if (indicator in tokens) {  // EXACT TOKEN MATCH, not substring
                count++
                if (indicator in VERY_STRONG_LDS_INDICATORS) {
                    hasVeryStrong = true
                }
            }
        }

        return hasVeryStrong || count >= 2
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIX #10: TEST/PLACEHOLDER CONTENT DETECTION (Jan 2026)
    // Negative signals to prevent false matches with test/dummy content
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Indicators that content is test, placeholder, or dummy data.
     * These should NOT be matched with real events.
     */
    private val TEST_CONTENT_INDICATORS = setOf(
        // Explicit test markers
        "test", "testing", "testcase", "unittest", "utest", "tst",
        // Development markers
        "todo", "fixme", "xxx", "hack", "debug", "wip",
        // Placeholder content
        "placeholder", "dummy", "sample", "example", "mock", "fake",
        "lorem", "ipsum", "dolor", "sit amet", "asdf", "qwerty",
        // Temp/scratch content
        "temp", "tmp", "scratch", "draft", "untitled",
        // Generic/meaningless
        "aaa", "bbb", "ccc", "abc", "xyz", "123", "foo", "bar", "baz"
    )

    /**
     * BUGFIX Issue #5 (Jan 2026): Pre-compiled regexes to avoid GC churn.
     * Creating ~40 Regex objects per call was causing performance issues on e-ink.
     * Now compiled once on first use (lazy initialization).
     */
    private val TEST_CONTENT_REGEXES: List<Pair<String, Regex>> by lazy {
        TEST_CONTENT_INDICATORS.map { indicator ->
            indicator to Regex("\\b${Regex.escape(indicator)}\\b", RegexOption.IGNORE_CASE)
        }
    }

    /**
     * BUGFIX Issue #6 (Jan 2026): Context-dependent exceptions for test indicators.
     * Some indicators like "draft" have legitimate uses in certain contexts.
     * Key = indicator, Value = words that, when present, make the indicator legitimate.
     *
     * Example: "draft pick" is sports content, not test content.
     */
    private val TEST_CONTENT_EXCEPTIONS = mapOf(
        "draft" to setOf("pick", "picks", "trade", "nfl", "nba", "mlb", "round", "order", "lottery", "fantasy", "prospect"),
        "sample" to setOf("size", "rate", "data", "population", "survey", "audio", "music", "beat"),
        "example" to setOf("code", "usage", "case", "problem", "lesson", "teaching")
    )

    /**
     * Check if text contains test/placeholder content indicators.
     * Returns true if likely test content that should be deprioritized.
     *
     * BUGFIX Issue #5: Uses pre-compiled regexes for performance.
     * BUGFIX Issue #6: Checks context exceptions for ambiguous indicators.
     */
    private fun isTestContent(text: String): Boolean {
        val lower = text.lowercase()
        val tokens = tokenizeForContext(lower)

        // Check for explicit test indicators using pre-compiled regexes
        for ((indicator, regex) in TEST_CONTENT_REGEXES) {
            if (regex.containsMatchIn(lower)) {
                // Check for context-dependent exceptions
                val exceptions = TEST_CONTENT_EXCEPTIONS[indicator]
                if (exceptions != null && tokens.any { it in exceptions }) {
                    // Context suggests legitimate use, not test content
                    continue
                }
                return true
            }
        }

        // Check for lorem ipsum pattern (even partial)
        if ("lorem" in tokens && "ipsum" in tokens) {
            return true
        }

        return false
    }

    /**
     * Calculate prior domain scores from UNAMBIGUOUS tokens only.
     * Used to provide context for disambiguation of ambiguous terms.
     *
     * BUGFIX (Jan 2026): Added to fix empty priors bug where disambiguation
     * was ignoring 40% of the signal by passing emptyMap().
     *
     * @param tokens All tokens from the text
     * @param ambiguousTerms Set of terms considered ambiguous (will be excluded)
     * @return Map of Domain -> score from unambiguous terms only
     */
    private fun calculatePriorScoresFromUnambiguous(
        tokens: Set<String>,
        ambiguousTerms: Set<String>
    ): Map<Domain, Float> {
        val unambiguousTokens = tokens.filter { it !in ambiguousTerms }
        val scores = mutableMapOf<Domain, Float>()

        for ((domainName, domainDict) in languageDictionaries.domains) {
            var score = 0f
            for (token in unambiguousTokens) {
                val weight = domainDict.getWeight(token)
                if (weight > 0) {
                    score += weight
                }
            }
            if (score > 0) {
                scores[domainName] = score
            }
        }

        return scores
    }

    /**
     * Enhanced semantic group scoring with weighted analysis (Jan 2026).
     * Returns a quality score indicating strength of semantic overlap.
     *
     * IMPORTANT: Score can exceed 1.0 for exceptionally strong matches.
     * Caller should normalize for UI display if needed (e.g., coerceAtMost(1.0f)).
     *
     * Improvements over boolean version:
     * 1. Tier-weighted scoring (TierA=4.0 worth more than TierC=1.0)
     * 2. Geometric mean requires BOTH note and event to be strong (prevents one-sided matches)
     * 3. Exact term overlap bonus (same word in both = stronger signal)
     * 4. Graduated quality enables proportional bonuses at call site
     * 5. LDS context boost (Jan 2026 Fix #8): When both sides have LDS indicators, boost score
     *
     * Quality levels (approximate, can exceed 1.0 with strong bonuses):
     * - 1.0+: Exceptional match (multiple bonuses stacking)
     * - 0.7-1.0: Strong overlap (TierA terms or multiple TierB + exact overlap)
     * - 0.4-0.7: Moderate overlap (TierB terms or TierA + TierC)
     * - 0.2-0.4: Weak overlap (mostly TierC, no exact overlap)
     * - <0.2: Insufficient (single generic term, different domains)
     */
    private fun calculateSemanticOverlapScore(noteTitle: String, eventText: String): Float {
        val noteTitleLower = noteTitle.lowercase()
        val eventTitleLower = eventText.lowercase()

        // Tokenize with minimum length filter
        // Keep both indexed list (for context window) and set (for fast lookup)
        val noteTokensList = noteTitleLower.split(Regex("[\\s\\-_]+"))
            .filter { it.length >= 2 }
        val noteWords = noteTokensList.toSet()
        val eventTokensList = eventTitleLower.split(Regex("[\\s\\-_]+"))
            .filter { it.length >= 2 }
        val eventWords = eventTokensList.toSet()

        // LDS context detection (Fix #8)
        val noteHasLdsContext = hasLdsContext(noteTitleLower)
        val eventHasLdsContext = hasLdsContext(eventTitleLower)
        val bothHaveLdsContext = noteHasLdsContext && eventHasLdsContext

        // ═══════════════════════════════════════════════════════════════════════════
        // Fix #9: Collocation-based disambiguation for ambiguous terms
        // For terms like "primary", "service", "meeting" - use local context to
        // determine which domain they belong to, boosting correct domain scores.
        //
        // BUGFIX (Jan 2026): Previously passed emptyMap() as priors, which ignored
        // 40% of the disambiguation signal. Now we calculate priors from unambiguous
        // terms first, then pass them to improve disambiguation accuracy.
        // ═══════════════════════════════════════════════════════════════════════════

        // Step 1: Calculate prior scores from UNAMBIGUOUS tokens only
        // This gives the disambiguator context about what domains are already present
        val ambiguousTerms = ContextDisambiguator.AMBIGUOUS_TERMS.keys
        val notePriors = calculatePriorScoresFromUnambiguous(noteWords, ambiguousTerms)
        val eventPriors = calculatePriorScoresFromUnambiguous(eventWords, ambiguousTerms)

        // Step 2: Disambiguate ambiguous terms using priors + collocations
        // Map: term -> resolved domain (if successfully disambiguated)
        val noteDisambiguated = mutableMapOf<String, Domain>()
        for ((idx, token) in noteTokensList.withIndex()) {
            if (ContextDisambiguator.isAmbiguous(token)) {
                val result = ContextDisambiguator.resolve(
                    term = token,
                    tokens = noteTokensList,
                    termIndex = idx,
                    priorScores = notePriors  // BUGFIX: Now uses real priors
                )
                if (result != null && result.confidence >= 0.6f) {
                    noteDisambiguated[token] = result.resolvedDomain
                }
            }
        }

        // Pre-compute disambiguation for ambiguous terms in event
        val eventDisambiguated = mutableMapOf<String, Domain>()
        for ((idx, token) in eventTokensList.withIndex()) {
            if (ContextDisambiguator.isAmbiguous(token)) {
                val result = ContextDisambiguator.resolve(
                    term = token,
                    tokens = eventTokensList,
                    termIndex = idx,
                    priorScores = eventPriors  // BUGFIX: Now uses real priors
                )
                if (result != null && result.confidence >= 0.6f) {
                    eventDisambiguated[token] = result.resolvedDomain
                }
            }
        }

        // Track best overlap across all domains
        var bestOverlapScore = 0f

        for ((domainName, domainDict) in languageDictionaries.domains) {
            // Calculate weighted score for NOTE in this domain
            var noteScore = 0f
            var noteTierACount = 0
            val noteMatchedTerms = mutableSetOf<String>()

            for (word in noteWords) {
                var weight = domainDict.getWeight(word)
                if (weight > 0) {
                    // Fix #9: Apply disambiguation modifier for ambiguous terms
                    // If this term was disambiguated, boost/reduce based on whether
                    // it resolved to THIS domain or a DIFFERENT domain
                    val disambiguatedDomain = noteDisambiguated[word]
                    if (disambiguatedDomain != null) {
                        weight *= if (disambiguatedDomain == domainName) {
                            1.5f  // Boost: context confirms this domain
                        } else {
                            0.3f  // Penalty: context points to different domain
                        }
                    }

                    noteScore += weight
                    noteMatchedTerms.add(word)
                    if (domainDict.getTier(word) == "A") noteTierACount++
                }
            }

            // Check multi-word TierA terms (e.g., "ward-council", "elders-quorum")
            // BUGFIX (Jan 2026): Use token set membership, not substring contains()
            // Previously "reward" would match "ward" via contains()
            for (term in domainDict.tierA.first) {
                val termLower = term.lowercase()
                if (termLower.contains("-") || termLower.contains(" ")) {
                    // Check if all parts of the term are present as WHOLE WORDS
                    val parts = termLower.split(Regex("[\\-\\s]+"))
                    if (parts.all { it in noteWords }) {  // Token match, not substring
                        noteScore += domainDict.tierA.second
                        noteTierACount++
                        noteMatchedTerms.addAll(parts)
                    }
                }
            }

            // Calculate weighted score for EVENT in this domain
            var eventScore = 0f
            var eventTierACount = 0
            val eventMatchedTerms = mutableSetOf<String>()

            for (word in eventWords) {
                var weight = domainDict.getWeight(word)
                if (weight > 0) {
                    // Fix #9: Apply disambiguation modifier for ambiguous terms
                    val disambiguatedDomain = eventDisambiguated[word]
                    if (disambiguatedDomain != null) {
                        weight *= if (disambiguatedDomain == domainName) {
                            1.5f  // Boost: context confirms this domain
                        } else {
                            0.3f  // Penalty: context points to different domain
                        }
                    }

                    eventScore += weight
                    eventMatchedTerms.add(word)
                    if (domainDict.getTier(word) == "A") eventTierACount++
                }
            }

            // Check multi-word TierA terms for event
            // BUGFIX (Jan 2026): Use token set membership, not substring contains()
            for (term in domainDict.tierA.first) {
                val termLower = term.lowercase()
                if (termLower.contains("-") || termLower.contains(" ")) {
                    val parts = termLower.split(Regex("[\\-\\s]+"))
                    if (parts.all { it in eventWords }) {  // Token match, not substring
                        eventScore += domainDict.tierA.second
                        eventTierACount++
                        eventMatchedTerms.addAll(parts)
                    }
                }
            }

            // Both must have SOME score in this domain for overlap
            if (noteScore > 0 && eventScore > 0) {
                // Calculate exact term overlap (same words in both)
                val exactOverlap = noteMatchedTerms.intersect(eventMatchedTerms)
                val exactOverlapBonus = when {
                    exactOverlap.isEmpty() -> 1.0f      // No bonus
                    exactOverlap.size == 1 -> 1.3f     // Single shared term
                    else -> 1.5f                        // Multiple shared terms
                }

                // TierA presence bonus (high-value terms on both sides)
                val tierABonus = when {
                    noteTierACount > 0 && eventTierACount > 0 -> 1.5f  // Both have TierA
                    noteTierACount > 0 || eventTierACount > 0 -> 1.2f  // One has TierA
                    else -> 1.0f                                        // No TierA
                }

                // LDS context boost (Fix #8)
                // When both note and event have LDS context AND we're scoring LDS_LEADERSHIP domain,
                // apply a contextual boost to help disambiguate from generic business terms
                val ldsContextBoost = if (bothHaveLdsContext &&
                    domainName == Domain.LDS_LEADERSHIP) {
                    1.4f  // 40% boost for LDS domain when both have context
                } else {
                    1.0f
                }

                // Fix #9: Collocation agreement bonus
                // When BOTH note and event have the same ambiguous term that both resolved
                // to THIS domain, it's a very strong signal of semantic overlap
                val sharedAmbiguousTerms = noteDisambiguated.keys.intersect(eventDisambiguated.keys)
                val collocationAgreementCount = sharedAmbiguousTerms.count { term ->
                    noteDisambiguated[term] == domainName && eventDisambiguated[term] == domainName
                }
                val collocationAgreementBonus = when (collocationAgreementCount) {
                    0 -> 1.0f      // No agreement
                    1 -> 1.3f      // One shared disambiguated term
                    else -> 1.5f   // Multiple shared disambiguated terms
                }

                // Geometric mean: sqrt(noteScore * eventScore)
                // This requires BOTH to be strong - one strong + one weak = moderate
                val geometricMean = kotlin.math.sqrt(noteScore * eventScore)

                // Apply bonuses and compute final score
                // Max expected geometric mean: sqrt(8 * 8) = 8 for very strong matches
                // Divide by 8 to normalize base, then apply multiplicative bonuses
                //
                // BUGFIX (Jan 2026): Removed coerceAtMost(1.0f) that was destroying ranking data.
                // With max multiplier of 1.5 × 1.5 × 1.4 × 1.5 = 4.725, mediocre and excellent
                // matches were both clamped to 1.0, making them indistinguishable.
                // Scores > 1.0 now indicate exceptionally strong matches.
                // Caller should normalize for UI display if needed.
                val normalizedScore = (geometricMean / 8f) * exactOverlapBonus * tierABonus * ldsContextBoost * collocationAgreementBonus

                if (normalizedScore > bestOverlapScore) {
                    bestOverlapScore = normalizedScore  // Allow scores > 1.0 for ranking
                }
            }
        }

        return bestOverlapScore
    }

    /**
     * Legacy boolean wrapper for compatibility.
     * Uses threshold of 0.2 (weak overlap minimum) for backward compatibility.
     *
     * @deprecated Use calculateSemanticOverlapScore() directly for graduated bonuses.
     */
    @Suppress("UNUSED")
    private fun shareSemanticGroup(noteTitle: String, eventText: String): Boolean {
        return calculateSemanticOverlapScore(noteTitle, eventText) >= 0.2f
    }

    /**
     * Check if note and event fall on the same day of the week (different weeks).
     * Catches weekly recurring patterns like "Tuesday standup".
     */
    private fun sameWeekday(note: BooxNoteIndex, event: CalendarEvent): Boolean {
        return try {
            val noteDate = java.time.LocalDate.parse(note.originalDate)
            val eventDate = java.time.LocalDate.parse(event.dayId)
            noteDate.dayOfWeek == eventDate.dayOfWeek
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculate cross-day match score between a note and an event.
     * Applies day distance penalties and relationship bonuses on top of base scoring.
     *
     * @param note The note to match
     * @param event The event to match against
     * @param dayOffset Days between note and event (positive = event is in future, negative = event is in past)
     * @param totalEventsInWindow Total events in search window (for sparse context bonus)
     * @return CrossDayLinkSuggestion with score, confidence, and reasoning
     */
    fun calculateCrossDayScore(
        note: BooxNoteIndex,
        event: CalendarEvent,
        dayOffset: Int,
        totalEventsInWindow: Int = Int.MAX_VALUE  // Default disables sparse bonus for backwards compat
    ): CrossDayLinkSuggestion {
        // Start with base same-day score calculation
        val baseScore = calculateMatchScoreEnhanced(note, event, isOnlyTimedEvent = false)
        return calculateCrossDayScoreWithBase(note, event, dayOffset, totalEventsInWindow, baseScore)
    }

    private fun calculateCrossDayScoreWithBase(
        note: BooxNoteIndex,
        event: CalendarEvent,
        dayOffset: Int,
        totalEventsInWindow: Int,
        baseScore: ScoredMatch
    ): CrossDayLinkSuggestion {
        var score = baseScore.score
        val reasons = baseScore.reasons.toMutableList()
        val eventText = buildEventText(event)

        // Apply day distance penalty (REBALANCED v2 - gentler curve)
        val dayPenalty = when (kotlin.math.abs(dayOffset)) {
            0 -> 0
            1 -> PENALTY_1_DAY       // -5 (was -15)
            2 -> PENALTY_2_DAYS      // -15 (was -35)
            3 -> PENALTY_3_DAYS      // -25 (was -50)
            else -> PENALTY_4_TO_7_DAYS  // -35 for 4-7 days (bulk mode)
        }
        score += dayPenalty

        // Determine suggestion type based on relationship
        // dayOffset > 0 means event is AFTER note's date = PREP_NOTES
        // dayOffset < 0 means event is BEFORE note's date = FOLLOW_UP
        val suggestionType = when {
            dayOffset == 0 -> SuggestionType.SAME_DAY
            dayOffset < 0 -> SuggestionType.FOLLOW_UP  // Event was before, note is follow-up
            dayOffset > 0 -> SuggestionType.PREP_NOTES // Event is after, note is prep
            else -> SuggestionType.SAME_DAY
        }

        // Apply relationship bonuses ALWAYS (even if day penalty made score negative)
        // FIX (Jan 2026): Removed "if (score > 0)" check that was blocking bonuses
        // for cross-day notes with no title match (day penalty made score negative)
        when (suggestionType) {
            SuggestionType.FOLLOW_UP -> {
                score += BONUS_FOLLOW_UP
                reasons.add(MatchReason.SHORTLY_AFTER_EVENT)
            }
            SuggestionType.PREP_NOTES -> {
                score += BONUS_PREP_NOTES
                reasons.add(MatchReason.SHORTLY_BEFORE_EVENT)
            }
            SuggestionType.SAME_DAY -> {
                // Same-day proximity bonus: note and event are on same day
                // This helps surface matches even without temporal proximity
                score += BONUS_SAME_DAY_PROXIMITY
            }
            SuggestionType.LATE_CAPTURE -> {
                // Late capture: note created much later (catch-up documentation)
                // Still give a small bonus for relationship
                score += BONUS_FOLLOW_UP  // Treat like follow-up
                reasons.add(MatchReason.SHORTLY_AFTER_EVENT)
            }
        }

        // Time-of-day context bonus: Morning note + yesterday PM event
        // This catches the common "morning review of yesterday's meeting" pattern
        if (dayOffset == -1 && isMorningNote(note) && isEveningTime(event.startTime)) {
            score += BONUS_MORNING_FOLLOW_UP
            reasons.add(MatchReason.MORNING_FOLLOWUP)  // Fix #6: Track morning followup as reason
        }

        // NEW v3: Semantic keyword group bonus with graduated scoring (Jan 2026)
        // Uses quality score to apply proportional bonuses - prevents weak matches from getting full bonus
        val semanticQuality = calculateSemanticOverlapScore(note.title, eventText)
        val semanticBonus = when {
            semanticQuality >= SEMANTIC_STRONG_THRESHOLD -> BONUS_SEMANTIC_STRONG   // +30: TierA + exact overlap
            semanticQuality >= SEMANTIC_MODERATE_THRESHOLD -> BONUS_SEMANTIC_MODERATE // +20: TierB or mixed
            semanticQuality >= SEMANTIC_WEAK_THRESHOLD -> BONUS_SEMANTIC_WEAK       // +8: Mostly TierC
            else -> 0  // Insufficient semantic connection - no bonus
        }
        if (semanticBonus > 0) {
            score += semanticBonus
            reasons.add(MatchReason.SEMANTIC_GROUP_MATCH)  // Fix #6: Use distinct reason type
        }

        // NEW v2: Sparse context bonus ("only game in town")
        // Fewer events in window = stronger signal that this event is the match
        val sparseBonus = when (totalEventsInWindow) {
            1 -> BONUS_ONLY_CANDIDATE     // +30: Only 1 event in entire window
            in 2..3 -> BONUS_SPARSE_CONTEXT  // +20: 2-3 events
            else -> 0
        }
        if (sparseBonus > 0) {
            score += sparseBonus
            reasons.add(MatchReason.SPARSE_CONTEXT)  // Fix #6: Track sparse context as reason
        }

        // NEW v2: Weekday pattern bonus
        // Same day of week (different week) suggests weekly recurring patterns
        if (dayOffset != 0 && sameWeekday(note, event)) {
            score += BONUS_SAME_WEEKDAY
            reasons.add(MatchReason.WEEKDAY_PATTERN)  // Fix #6: Track weekday pattern as reason
        }

        // Ensure score doesn't go below 0
        score = maxOf(0, score)

        // Determine cross-day confidence using different thresholds
        val confidence = when {
            score >= CROSS_DAY_HIGH_THRESHOLD -> LinkConfidence.HIGH
            score >= CROSS_DAY_MEDIUM_THRESHOLD -> LinkConfidence.MEDIUM
            score >= CROSS_DAY_LOW_THRESHOLD -> LinkConfidence.LOW
            score >= CROSS_DAY_POSSIBLE_THRESHOLD -> LinkConfidence.LOW // "POSSIBLE" mapped to LOW
            else -> LinkConfidence.NONE
        }

        // Generate human-readable reasoning
        val reasoning = generateReasoning(note, event, dayOffset, suggestionType, reasons)

        return CrossDayLinkSuggestion(
            note = note,
            event = event,
            dayOffset = dayOffset,
            suggestionType = suggestionType,
            confidence = confidence,
            confidenceScore = score,
            reasoning = reasoning,
            matchReasons = reasons,
            dataQualityFlags = baseScore.dataQualityFlags
        )
    }

    /**
     * Generate human-readable reasoning for a cross-day suggestion.
     * Explains WHY a note might match an event.
     */
    private fun generateReasoning(
        note: BooxNoteIndex,
        event: CalendarEvent,
        dayOffset: Int,
        suggestionType: SuggestionType,
        reasons: List<MatchReason>
    ): String {
        // Day relationship text
        val dayText = when (dayOffset) {
            0 -> "Same day"
            -1 -> "Yesterday's event"
            1 -> "Tomorrow's event"
            else -> "${kotlin.math.abs(dayOffset)} days ${if (dayOffset < 0) "ago" else "ahead"}"
        }

        // Suggestion type hint
        val typeHint = when (suggestionType) {
            SuggestionType.SAME_DAY -> ""
            SuggestionType.FOLLOW_UP -> " - likely follow-up notes"
            SuggestionType.PREP_NOTES -> " - likely prep notes"
            SuggestionType.LATE_CAPTURE -> " - late documentation"
        }

        // Reason text (most specific reason)
        val reasonText = when {
            MatchReason.TITLE_MATCH in reasons -> "Title matches"
            MatchReason.DURING_EVENT in reasons -> "Created during event"
            MatchReason.SHORTLY_AFTER_EVENT in reasons -> "Created shortly after"
            MatchReason.SHORTLY_BEFORE_EVENT in reasons -> "Created shortly before"
            else -> "Time proximity"
        }

        return "$dayText$typeHint. $reasonText."
    }

    /**
     * Find cross-day suggestions for a single note.
     * Searches within the specified day range around the note's date.
     *
     * @param note The note to find suggestions for
     * @param events All events in the search window (pre-fetched for performance)
     * @param searchDays Number of days to search (±searchDays)
     * @return List of suggestions sorted by confidence (best first)
     */
    suspend fun findCrossDaySuggestions(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        searchDays: Int = DAY_MODE_SEARCH_DAYS
    ): List<CrossDayLinkSuggestion> {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Cross-day suggestions for note ${note.uniqueId}")
            return emptyList()
        }

        return findCrossDaySuggestionsInternal(note, events, searchDays, null, null)
    }

    private suspend fun findCrossDaySuggestionsInternal(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        searchDays: Int,
        eventProfilesMap: Map<String, SemanticProfile>?,
        semanticProfilesCache: MutableMap<String, List<SemanticProfile>?>?
    ): List<CrossDayLinkSuggestion> {
        val scoredCandidates = buildScoredEventCandidates(
            note = note,
            events = events,
            scoringMode = SmartLinkScoringMode.CROSS_DAY,
            semanticProfilesCache = semanticProfilesCache,
            eventProfilesMap = eventProfilesMap,
            allowOnDemandEventProfiles = eventProfilesMap == null,
            searchDays = searchDays
        )
        val totalEventsInWindow = scoredCandidates.size

        return scoredCandidates
            .map { candidate ->
                candidate to calculateCrossDayScoreWithBase(
                    note = note,
                    event = candidate.event,
                    dayOffset = candidate.dayOffset,
                    totalEventsInWindow = totalEventsInWindow,
                    baseScore = candidate.scoredMatch
                )
            }
            .filter { (_, suggestion) -> suggestion.confidence != LinkConfidence.NONE }
            .sortedWith(
                compareByDescending<Pair<ScoredEventCandidate, CrossDayLinkSuggestion>> { it.second.confidenceScore }
                    .thenByDescending { it.first.semanticAffinity }
                    .thenBy { abs(it.second.dayOffset) }
                    .thenBy { it.first.midpointDistanceMs }
                    .thenBy { it.first.event.startTime }
                    .thenBy { it.first.event.id }
            )
            .map { it.second }
    }

    private fun sanitizeEventDescription(description: String?): String? {
        val trimmed = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.length > EVENT_DESCRIPTION_MAX_CHARS) {
            trimmed.take(EVENT_DESCRIPTION_MAX_CHARS)
        } else {
            trimmed
        }
    }

    private fun buildEventText(event: CalendarEvent): String {
        val descriptionText = sanitizeEventDescription(event.description)
        return if (descriptionText != null) "${event.title} $descriptionText" else event.title
    }

    /**
     * Analyze ALL unlinked notes and find suggestions.
     * Optimized for bulk mode: queries events once for the full date range.
     *
     * @param onProgress Callback with progress (0.0 to 1.0)
     * @return BulkSmartLinkSession with all suggestions
     */
    suspend fun analyzeBulkSmartLink(
        onProgress: (Float) -> Unit = {}
    ): BulkSmartLinkSession = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting bulk smart link analysis")

        // Step 1: Get all unlinked notes
        val unlinkedNotes = noteRepository.getAllUnlinkedNotes()
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Bulk Smart Link analysis")
            onProgress(1f)
            return@withContext BulkSmartLinkSession(
                unlinkedNotes = unlinkedNotes,
                suggestionsMap = unlinkedNotes.associate { it.uniqueId to emptyList() },
                stats = BulkLinkStats(unlinkedNotes.size, 0, 0, 0, unlinkedNotes.size)
            )
        }

        if (unlinkedNotes.isEmpty()) {
            Log.d(TAG, "No unlinked notes found")
            return@withContext BulkSmartLinkSession(
                unlinkedNotes = emptyList(),
                suggestionsMap = emptyMap(),
                stats = BulkLinkStats(0, 0, 0, 0, 0)
            )
        }

        Log.d(TAG, "Found ${unlinkedNotes.size} unlinked notes")

        // Step 2: Calculate overall date range needed
        val noteDates = unlinkedNotes.map { LocalDate.parse(it.originalDate) }
        val minNoteDate = noteDates.minOrNull()!!.minusDays(BULK_MODE_SEARCH_DAYS.toLong())
        val maxNoteDate = noteDates.maxOrNull()!!.plusDays(BULK_MODE_SEARCH_DAYS.toLong())

        // Step 3: Batch query ALL events in range (performance optimization)
        val allEvents = smartLinkEventRepository.getEventsInRange(
            minNoteDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            maxNoteDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        Log.d(TAG, "Loaded ${allEvents.size} events for date range $minNoteDate to $maxNoteDate")

        // Step 4: Find suggestions for each note
        val suggestionsMap = mutableMapOf<String, List<CrossDayLinkSuggestion>>()
        var highCount = 0
        var mediumCount = 0
        var lowCount = 0
        var noMatchCount = 0

        val semanticProfilesCache = mutableMapOf<String, List<SemanticProfile>?>()
        preloadSemanticProfilesFromAggregates(unlinkedNotes, semanticProfilesCache)

        // Load event profiles from indexed data (uses getOrCreateEventProfile)
        val eventProfilesMap = loadEventProfilesBatch(allEvents)
        Log.d(TAG, "Loaded ${eventProfilesMap.size} event profiles for bulk matching")

        unlinkedNotes.forEachIndexed { index, note ->
            coroutineContext.ensureActive()
            val suggestions = findCrossDaySuggestionsInternal(
                note = note,
                events = allEvents,
                searchDays = BULK_MODE_SEARCH_DAYS,
                eventProfilesMap = eventProfilesMap,
                semanticProfilesCache = semanticProfilesCache
            )
            suggestionsMap[note.uniqueId] = suggestions

            // Update stats based on best suggestion
            val bestConfidence = suggestions.firstOrNull()?.confidence
            when (bestConfidence) {
                LinkConfidence.HIGH -> highCount++
                LinkConfidence.MEDIUM -> mediumCount++
                LinkConfidence.LOW -> lowCount++
                null, LinkConfidence.NONE -> noMatchCount++
            }

            // Report progress
            onProgress((index + 1).toFloat() / unlinkedNotes.size)
        }

        Log.d(TAG, "Bulk analysis complete in ${System.currentTimeMillis() - startTime}ms: " +
                "high=$highCount, medium=$mediumCount, low=$lowCount, noMatch=$noMatchCount")

        BulkSmartLinkSession(
            unlinkedNotes = unlinkedNotes,
            suggestionsMap = suggestionsMap,
            stats = BulkLinkStats(
                totalUnlinked = unlinkedNotes.size,
                highConfidenceCount = highCount,
                mediumConfidenceCount = mediumCount,
                lowConfidenceCount = lowCount,
                noMatchCount = noMatchCount
            )
        )
    }

    /**
     * Apply all high-confidence links from a bulk session.
     * @return Number of notes linked
     */
    suspend fun applyHighConfidenceLinks(session: BulkSmartLinkSession): Int = withContext(Dispatchers.IO) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Apply high-confidence links")
            return@withContext 0
        }

        var linkedCount = 0

        session.suggestionsMap.forEach { (noteId, suggestions) ->
            val bestSuggestion = suggestions.firstOrNull()
            if (bestSuggestion != null && bestSuggestion.confidence == LinkConfidence.HIGH) {
                applyCrossDaySuggestion(bestSuggestion)
                linkedCount++
            }
        }

        // Invalidate cache after bulk linking
        noteRepository.invalidateLinksCache()

        Log.d(TAG, "Applied $linkedCount high-confidence links")
        linkedCount
    }

    suspend fun applyCrossDaySuggestion(
        suggestion: CrossDayLinkSuggestion,
        decisionMode: SmartLinkDecisionMode = SmartLinkDecisionMode.CROSS_DAY_BULK_APPLY
    ) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Apply cross-day suggestion for note ${suggestion.note.uniqueId}")
            return
        }

        noteRepository.linkNoteToEventEnhanced(
            noteId = suggestion.note.uniqueId,
            eventId = suggestion.event.id,
            linkedDayDate = suggestion.note.originalDate,
            confidence = suggestion.confidence,
            confidenceScore = suggestion.confidenceScore,
            matchReasons = suggestion.matchReasons,
            suggestedEventIds = emptyList(),
            needsAttention = false,
            isManual = false,
            decisionMode = decisionMode,
            engineVersion = SMART_LINK_ENGINE_VERSION,
            dataQualityFlags = suggestion.dataQualityFlags
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIFIED INTELLIGENT ANALYSIS (Dec 2025)
    // Auto-chains heuristic → AI for seamless user experience
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unified intelligent analysis that auto-chains heuristic matching with AI analysis.
     *
     * Flow:
     * 1. Heuristic analysis (0-40%): Fast local matching with enhanced algorithms
     * 2. AI analysis (40-90%): Semantic matching for unmatched notes
     * 3. Merge results (90-100%): Combine into unified session
     *
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Unified BulkSmartLinkSession with all matches (heuristic + AI)
     */
    suspend fun analyzeUnified(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BulkSmartLinkSession = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting unified intelligent analysis")

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 1: Heuristic Analysis (0-40%)
        // ═══════════════════════════════════════════════════════════════════
        onProgress(0.05f, "Gathering unlinked notes...")

        val unlinkedNotes = noteRepository.getAllUnlinkedNotes()
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Unified Smart Link analysis")
            onProgress(1.0f, "Complete")
            return@withContext BulkSmartLinkSession(
                unlinkedNotes = unlinkedNotes,
                suggestionsMap = unlinkedNotes.associate { it.uniqueId to emptyList() },
                stats = BulkLinkStats(unlinkedNotes.size, 0, 0, 0, unlinkedNotes.size)
            )
        }

        if (unlinkedNotes.isEmpty()) {
            Log.d(TAG, "No unlinked notes found")
            onProgress(1.0f, "Complete")
            return@withContext BulkSmartLinkSession(
                unlinkedNotes = emptyList(),
                suggestionsMap = emptyMap(),
                stats = BulkLinkStats(0, 0, 0, 0, 0)
            )
        }

        onProgress(0.1f, "Loading calendar events...")

        // Calculate date range for all notes
        val noteDates = unlinkedNotes.map { LocalDate.parse(it.originalDate) }
        val minNoteDate = noteDates.minOrNull()!!.minusDays(BULK_MODE_SEARCH_DAYS.toLong())
        val maxNoteDate = noteDates.maxOrNull()!!.plusDays(BULK_MODE_SEARCH_DAYS.toLong())

        val allEvents = smartLinkEventRepository.getEventsInRange(
            minNoteDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            maxNoteDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        Log.d(TAG, "Loaded ${allEvents.size} events for range $minNoteDate to $maxNoteDate")

        onProgress(0.15f, "Loading semantic profiles...")

        val semanticProfilesCache = mutableMapOf<String, List<SemanticProfile>?>()
        preloadSemanticProfilesFromAggregates(unlinkedNotes, semanticProfilesCache)

        onProgress(0.20f, "Running intelligent matching...")

        // Run heuristic analysis
        val suggestionsMap = mutableMapOf<String, List<CrossDayLinkSuggestion>>()
        val unmatchedNotes = mutableListOf<BooxNoteIndex>()

        // Load event profiles from indexed data (uses getOrCreateEventProfile)
        val eventProfilesMap = loadEventProfilesBatch(allEvents)
        Log.d(TAG, "Loaded ${eventProfilesMap.size} event profiles for unified analysis")

        unlinkedNotes.forEachIndexed { index, note ->
            coroutineContext.ensureActive()
            val suggestions = findCrossDaySuggestionsInternal(
                note = note,
                events = allEvents,
                searchDays = BULK_MODE_SEARCH_DAYS,
                eventProfilesMap = eventProfilesMap,
                semanticProfilesCache = semanticProfilesCache
            )
            suggestionsMap[note.uniqueId] = suggestions

            // Track notes with no match for AI analysis
            val bestConfidence = suggestions.firstOrNull()?.confidence
            if (bestConfidence == null || bestConfidence == LinkConfidence.NONE) {
                unmatchedNotes.add(note)
            }

            onProgress(
                0.20f + 0.20f * (index + 1) / unlinkedNotes.size,
                "Analyzing notes (${index + 1}/${unlinkedNotes.size})"
            )
        }

        val heuristicMatchedCount = unlinkedNotes.size - unmatchedNotes.size
        Log.d(TAG, "Heuristic phase: $heuristicMatchedCount matched, ${unmatchedNotes.size} unmatched")

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 2: Second-Pass Analysis with Relaxed Thresholds (40-90%)
        // Uses enhanced semantic scoring - NO LLM CALLS
        // ═══════════════════════════════════════════════════════════════════
        if (unmatchedNotes.isNotEmpty()) {
            onProgress(0.42f, "Re-analyzing ${unmatchedNotes.size} remaining notes...")

            var secondPassMatches = 0
            unmatchedNotes.forEachIndexed { index, note ->
                coroutineContext.ensureActive()

                // Get best suggestion from first pass (may be NONE confidence)
                val currentSuggestions = suggestionsMap[note.uniqueId] ?: emptyList()
                val bestCurrent = currentSuggestions.firstOrNull()

                // If we have a LOW confidence match, upgrade it (it's still useful)
                if (bestCurrent != null && bestCurrent.confidence == LinkConfidence.LOW) {
                    // Keep LOW matches - they're valid for user review
                    secondPassMatches++
                } else if (bestCurrent == null || bestCurrent.confidence == LinkConfidence.NONE) {
                    // Re-run with expanded search window for notes with no semantic data
                    val extendedSuggestions = findCrossDaySuggestionsInternal(
                        note = note,
                        events = allEvents,
                        searchDays = BULK_MODE_SEARCH_DAYS + 3,  // Extended window
                        eventProfilesMap = eventProfilesMap,
                        semanticProfilesCache = semanticProfilesCache
                    )

                    // Accept any match with confidence > NONE
                    val bestExtended = extendedSuggestions.firstOrNull()
                    if (bestExtended != null && bestExtended.confidence != LinkConfidence.NONE) {
                        suggestionsMap[note.uniqueId] = extendedSuggestions
                        secondPassMatches++
                    }
                }

                onProgress(
                    0.42f + 0.48f * (index + 1) / unmatchedNotes.size,
                    "Re-analyzing (${index + 1}/${unmatchedNotes.size})"
                )
            }

            Log.d(TAG, "Second-pass complete: $secondPassMatches additional matches found")
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3: Build Unified Results (90-100%)
        // ═══════════════════════════════════════════════════════════════════
        onProgress(0.92f, "Finalizing results...")

        // Calculate final stats
        var highCount = 0
        var mediumCount = 0
        var lowCount = 0
        var noMatchCount = 0

        suggestionsMap.forEach { (_, suggestions) ->
            when (suggestions.firstOrNull()?.confidence) {
                LinkConfidence.HIGH -> highCount++
                LinkConfidence.MEDIUM -> mediumCount++
                LinkConfidence.LOW -> lowCount++
                null, LinkConfidence.NONE -> noMatchCount++
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Unified analysis complete in ${elapsed}ms: " +
                "high=$highCount, medium=$mediumCount, low=$lowCount, noMatch=$noMatchCount")

        onProgress(1.0f, "Complete")

        BulkSmartLinkSession(
            unlinkedNotes = unlinkedNotes,
            suggestionsMap = suggestionsMap,
            stats = BulkLinkStats(
                totalUnlinked = unlinkedNotes.size,
                highConfidenceCount = highCount,
                mediumConfidenceCount = mediumCount,
                lowConfidenceCount = lowCount,
                noMatchCount = noMatchCount
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LLM-POWERED SMART LINK (Dec 2025)
    // Uses OpenRouter AI models for semantic note-to-event matching
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of LLM-powered note-to-event matching.
     */
    data class LlmMatchResult(
        val noteUniqueId: String,
        val matchedEventId: String?,
        val confidence: Float,        // 0.0-1.0
        val reasoning: String,
        val isLlmGenerated: Boolean = true
    )

    /**
     * Internal: JSON response structure from LLM.
     */
    private data class LlmMatchResponse(
        @SerializedName("matches") val matches: List<LlmMatchItem>?
    )

    private data class NotePromptEntry(
        val noteId: String,
        val titleWithDate: String,
        val nearbyEvents: List<CalendarEvent>
    )

    private data class LlmMatchItem(
        @SerializedName("noteId") val noteId: String,
        @SerializedName("eventId") val eventId: String? = null,
        @SerializedName("eventNum") val eventNum: Int? = null,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("reasoning") val reasoning: String?
    )

    private val gson = Gson()

    /**
     * Use LLM to semantically match notes to events.
     * This is the "big brain" fallback when heuristics fail.
     *
     * @param notes Notes to find matches for
     * @param events Events in the search window
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return List of match results with confidence and reasoning
     */
    suspend fun analyzeWithLlm(
        notes: List<BooxNoteIndex>,
        events: List<CalendarEvent>,
        onProgress: (Float) -> Unit = {}
    ): List<LlmMatchResult> = withContext(Dispatchers.IO) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("LLM Smart Link analysis")
            onProgress(1f)
            return@withContext emptyList()
        }

        if (notes.isEmpty()) return@withContext emptyList()

        onProgress(0.1f)

        // Filter to timed events only and create lookup by date
        val timedEvents = events.filter { !it.isAllDay }
        val eventsByDate = timedEvents.groupBy { it.dayId }

        // For each note, find events within ±7 days and build numbered entry
        // IMPORTANT: Skip notes with no nearby events - LLM can't match them anyway
        val noMatchResults = mutableListOf<LlmMatchResult>()
        val promptEntries = notes.mapNotNull { note ->
            val noteDate = try {
                LocalDate.parse(note.originalDate)
            } catch (e: Exception) {
                return@mapNotNull null
            }

            // Get events within ±7 days of this note
            val nearbyEvents = (-7..7).flatMap { offset ->
                val targetDate = noteDate.plusDays(offset.toLong()).toString()
                eventsByDate[targetDate] ?: emptyList()
            }.distinctBy { it.id }

            val limitedEvents = nearbyEvents.take(12)

            // Skip notes with no nearby events - return them as no-match directly
            if (limitedEvents.isEmpty()) {
                noMatchResults.add(LlmMatchResult(
                    noteUniqueId = note.uniqueId,
                    matchedEventId = null,
                    confidence = 0f,
                    reasoning = "No calendar events within ±7 days"
                ))
                return@mapNotNull null
            }

            NotePromptEntry(
                noteId = note.uniqueId,
                titleWithDate = "${note.title} (${note.originalDate})",
                nearbyEvents = limitedEvents
            )
        }

        Log.d(TAG, "LLM: ${promptEntries.size} notes with events, ${noMatchResults.size} skipped (no nearby events), ${timedEvents.size} total events")

        if (promptEntries.isEmpty()) {
            Log.w(TAG, "No notes with nearby events for LLM analysis")
            return@withContext noMatchResults  // Return the no-match results
        }

        val batches = promptEntries.chunked(LLM_BATCH_SIZE)
        val fallbackChain = ModelConfigProvider.getReasoningFallbackChain()
        Log.d(TAG, "LLM fallback chain: ${fallbackChain.map { it.id }}, batches=${batches.size}, batchSize=$LLM_BATCH_SIZE")

        // ENHANCED PROMPT v5: Semantic matching - understand MEANING not just literal words
        val systemPrompt = """
You are an expert at understanding the MEANING and INTENT behind handwritten notes.
Each note MUST be linked to exactly ONE event from the Nearby Events list.

For each note, THINK SEMANTICALLY about:

1. TOPIC INFERENCE: What is this note actually ABOUT?
   - Project name, client name, person name, subject matter

2. ABBREVIATION EXPANSION: Could shorthand expand to event keywords?
   - "Q3 rev" → "Q3 Review Meeting"
   - "John sync" → "Sync with John"
   - "proj kickoff" → "Project Kickoff"

3. SYNONYM RECOGNITION: Same meaning, different words?
   - discussion = meeting = call = sync = chat = conversation
   - review = feedback = assessment = evaluation
   - class = lecture = lesson = tutorial
   - appointment = checkup = visit

4. CONTEXTUAL CLUES: Names, codes, terminology
   - Both mention same name → likely related
   - Both mention same project/client → likely related
   - Project codes: "PRJ-123", "FEAT-456"

5. TEMPORAL RELATIONSHIP: Prep vs Follow-up
   - Note before event = preparation notes
   - Note after event = meeting notes, action items

MATCH BASED ON MEANING, not just literal word overlap.

OUTPUT: JSON array with semantic reasoning
[{"noteId":"X","eventNum":N,"confidence":0.8,"reasoning":"..."}]

RULES:
1. eventNum MUST be a positive integer (1, 2, 3...) from the Nearby Events list
2. Pick the event with the BEST SEMANTIC match (meaning > proximity)
3. If no semantic match found, use closest date
4. NEVER return eventNum: null or eventNum: 0

CONFIDENCE GUIDE:
0.9+ = Strong semantic match (same topic/person/project/subject)
0.7-0.8 = Good contextual match (related subject, temporal fit)
0.5-0.6 = Weak but plausible (only temporal proximity)
""".trimIndent()

        val progressPerBatch = if (batches.isEmpty()) 0f else 0.6f / batches.size

        onProgress(0.3f)

        val llmMatchResults = coroutineScope {
            val semaphore = Semaphore(LLM_CONCURRENCY)

            batches.mapIndexed { batchIndex, batchEntries ->
                async {
                    semaphore.withPermit {
                        val noteEventMap = batchEntries.associate { entry ->
                            entry.noteId to entry.nearbyEvents.map { it.id }
                        }

                        val userPrompt = buildString {
                            appendLine("Analyze these ${batchEntries.size} notes. Return a JSON array with exactly ${batchEntries.size} items.")
                            appendLine()
                            batchEntries.forEach { entry ->
                                appendLine("[${entry.noteId}] \"${entry.titleWithDate}\"")
                                appendLine("Nearby Events:")
                                entry.nearbyEvents.forEachIndexed { idx, event ->
                                    appendLine("${idx + 1}. ${event.title.take(50)} (${event.dayId})")
                                }
                                appendLine()
                            }
                            appendLine("Return JSON array with ${batchEntries.size} items. Pick an event for each note.")
                        }

                        Log.d(TAG, "LLM batch ${batchIndex + 1}/${batches.size}, prompt size: ~${userPrompt.length} chars")

                        for ((modelIndex, model) in fallbackChain.withIndex()) {
                            val modelName = model.id.substringAfterLast("/").substringBefore(":")
                            Log.d(TAG, "Batch ${batchIndex + 1}: trying model ${modelIndex + 1}/${fallbackChain.size}: ${model.id}")

                            val request = aiRequest {
                                model(model)
                                systemPrompt(systemPrompt)
                                userMessage(userPrompt)
                                jsonOutput()
                                temperature(0.3f)
                                enableReasoning()
                            }

                            when (val response = AiClient.execute(request)) {
                                is AiResponse.Success -> {
                                    Log.i(TAG, "Batch ${batchIndex + 1} LLM analysis succeeded with model: $modelName")
                                    onProgress(0.3f + progressPerBatch * (batchIndex + 1))
                                    return@async parseLlmMatchResponse(response.data, noteEventMap)
                                }
                                is AiResponse.Error.RateLimitError -> {
                                    Log.w(TAG, "Batch ${batchIndex + 1}: model $modelName rate-limited, trying next...")
                                    // Continue to next model in chain
                                }
                                is AiResponse.Error -> {
                                    Log.e(TAG, "Batch ${batchIndex + 1}: model $modelName failed: ${response.message}")
                                    // For non-rate-limit errors, also try next model
                                }
                            }

                            // Small delay before trying next model
                            if (modelIndex < fallbackChain.size - 1) {
                                kotlinx.coroutines.delay(500)
                            }
                        }

                        Log.e(TAG, "Batch ${batchIndex + 1}: all models in reasoning chain failed")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        // Combine LLM results with pre-filtered no-match results
        Log.d(TAG, "LLM analysis complete: ${llmMatchResults.size} from LLM + ${noMatchResults.size} no-match = ${llmMatchResults.size + noMatchResults.size} total")
        llmMatchResults + noMatchResults
    }

    /**
     * Fix Grok's malformed concatenated objects format.
     *
     * Problem: Grok outputs duplicate keys in one object:
     * {"noteId":"a","eventNum":1,"noteId":"b","eventNum":2}
     *
     * Solution: Detect and split into proper JSON array:
     * [{"noteId":"a","eventNum":1},{"noteId":"b","eventNum":2}]
     */
    private fun fixConcatenatedObjects(json: String): String {
        // Quick check: Does it have multiple "noteId" keys?
        val noteIdCount = Regex(""""noteId"\s*:""").findAll(json).count()
        if (noteIdCount <= 1) {
            return json  // Normal JSON, no fix needed
        }

        // Check if it's already a proper array
        val trimmed = json.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return json  // Already an array
        }

        // Detect: single object with duplicate "noteId" keys
        // Pattern: {"noteId":"x",...,"noteId":"y",...}
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && noteIdCount > 1) {
            Log.w(TAG, "Detected Grok-style concatenated objects ($noteIdCount noteIds in single object)")

            // Strategy: Split on each "noteId" occurrence, rebuild as array
            // Find all positions where "noteId" starts
            val noteIdRegex = Regex(""""noteId"\s*:\s*"[^"]+"""")
            val positions = mutableListOf<Int>()

            noteIdRegex.findAll(json).forEach { match ->
                // Find the opening brace before each noteId
                // We search backwards from the match for the nearest comma or opening brace
                positions.add(match.range.first)
            }

            if (positions.size > 1) {
                // Build individual objects by finding each "noteId"..."reasoning":"..." block
                val objectRegex = Regex(
                    """"noteId"\s*:\s*"[^"]+"\s*,\s*"eventNum"\s*:\s*(?:null|\d+)\s*,\s*"confidence"\s*:\s*[\d.]+\s*,\s*"reasoning"\s*:\s*(?:"[^"]*"|null)"""
                )

                val objects = objectRegex.findAll(json).map { match ->
                    "{${match.value}}"
                }.toList()

                if (objects.isNotEmpty()) {
                    val fixed = "[${objects.joinToString(",")}]"
                    Log.i(TAG, "Fixed Grok JSON: Split into ${objects.size} objects")
                    return fixed
                }
            }

            // Fallback: Try simpler split on "}," pattern
            // This handles: {"noteId":"a",...},{"noteId":"b",...} (missing outer brackets)
            if (json.contains("},{")) {
                val fixed = "[$json]"
                Log.i(TAG, "Fixed concatenated JSON by adding array brackets")
                return fixed
            }
        }

        // Check for }{-style concatenation (no comma between objects)
        // Pattern: {"noteId":"a",...}{"noteId":"b",...}
        if (json.contains("}{")) {
            val fixed = "[" + json.replace("}{", "},{") + "]"
            Log.i(TAG, "Fixed concatenated objects (}{ pattern)")
            return fixed
        }

        return json
    }

    /**
     * Parse LLM JSON response into match results.
     * Robust strategy:
     * 1. Try generic JSON parsing (Array or Object->Array).
     * 2. Search ALL keys in root object for arrays.
     * 3. Fallback: Regex rescue for malformed JSON.
     */
    private fun parseLlmMatchResponse(
        json: String,
        noteEventMap: Map<String, List<String>>
    ): List<LlmMatchResult> {
        var cleanJson = JsonExtractor.extract(json)
        Log.d(TAG, "Parsing LLM response: ${cleanJson.take(200)}...")

        // ═══════════════════════════════════════════════════════════════════
        // PRE-PROCESSOR: Fix Grok's concatenated objects format
        // Grok outputs: {"noteId":"x",...,"noteId":"y",...} (duplicate keys)
        // This is invalid JSON. We detect and split into proper array.
        // ═══════════════════════════════════════════════════════════════════
        cleanJson = fixConcatenatedObjects(cleanJson)

        // Helper to convert parsed items to results
        fun itemsToResults(items: List<LlmMatchItem>?): List<LlmMatchResult> {
            return items?.map { item ->
                val eventIdsForNote = noteEventMap[item.noteId]

                val resolvedEventId = if (eventIdsForNote == null) {
                    // Only log warning if eventNum was provided but we can't map it
                    if (item.eventNum != null && item.eventNum > 0) {
                        Log.w(TAG, "LLM returned noteId ${item.noteId} not in prompt map")
                    }
                    item.eventId
                } else {
                    item.eventNum?.let { num ->
                        if (num in 1..eventIdsForNote.size) {
                            eventIdsForNote[num - 1]
                        } else {
                            if (num != 0) Log.w(TAG, "LLM returned invalid eventNum=$num for note ${item.noteId} (available=${eventIdsForNote.size})")
                            null
                        }
                    } ?: item.eventId
                }

                LlmMatchResult(
                    noteUniqueId = item.noteId,
                    matchedEventId = resolvedEventId,
                    confidence = item.confidence.coerceIn(0f, 1f),
                    reasoning = item.reasoning ?: ""
                )
            } ?: emptyList()
        }

        val results = mutableListOf<LlmMatchResult>()

        try {
            val element = JsonParser.parseString(cleanJson)

            // Strategy 1: It's a direct array
            if (element.isJsonArray) {
                val arrayType = object : com.google.gson.reflect.TypeToken<List<LlmMatchItem>>() {}.type
                val items: List<LlmMatchItem> = gson.fromJson(element, arrayType)
                Log.d(TAG, "Parsed as bare array: ${items.size} matches")
                return itemsToResults(items)
            }

            // Strategy 2: It's an object, search for the array
            if (element.isJsonObject) {
                val obj = element.asJsonObject

                // 2a. Check common keys first
                val commonKeys = listOf("matches", "notes", "results", "output")
                for (key in commonKeys) {
                    if (obj.has(key) && obj.get(key).isJsonArray) {
                        val arrayType = object : com.google.gson.reflect.TypeToken<List<LlmMatchItem>>() {}.type
                        val items: List<LlmMatchItem> = gson.fromJson(obj.get(key), arrayType)
                        Log.d(TAG, "Parsed from key '$key': ${items.size} matches")
                        return itemsToResults(items)
                    }
                }

                // 2b. Check ALL keys for an array of objects
                for (entry in obj.entrySet()) {
                    if (entry.value.isJsonArray) {
                        try {
                            val arrayType = object : com.google.gson.reflect.TypeToken<List<LlmMatchItem>>() {}.type
                            val items: List<LlmMatchItem> = gson.fromJson(entry.value, arrayType)
                            if (items.isNotEmpty() && items.first().noteId.isNotEmpty()) {
                                Log.d(TAG, "Parsed from discovered key '${entry.key}': ${items.size} matches")
                                return itemsToResults(items)
                            }
                        } catch (ignore: Exception) {}
                    }
                }

                // 2c. Fallback: Parse the object itself as a single item
                try {
                    val item = gson.fromJson(element, LlmMatchItem::class.java)
                    if (!item.noteId.isNullOrEmpty()) {
                        Log.d(TAG, "Parsed as single object")
                        return itemsToResults(listOf(item))
                    }
                } catch (ignore: Exception) {}
            }

        } catch (e: Exception) {
            Log.d(TAG, "Standard JSON parsing failed, attempting rescue: ${e.message}")
        }

        // Strategy 3: Regex Rescue for malformed JSON or mixed content
        // Looks for block-like structures containing "noteId"
        try {
            // Regex to find JSON objects containing "noteId" property
            // This is a heuristic and assumes relatively standard formatting
            val regex = Regex("""\{[^{}]*?"noteId"[^{}]*?\}""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(cleanJson)
            val rescuedItems = mutableListOf<LlmMatchItem>()

            for (match in matches) {
                try {
                    val item = gson.fromJson(match.value, LlmMatchItem::class.java)
                    if (!item.noteId.isNullOrEmpty()) {
                        rescuedItems.add(item)
                    }
                } catch (ignore: Exception) {}
            }

            if (rescuedItems.isNotEmpty()) {
                Log.w(TAG, "Rescued ${rescuedItems.size} items via Regex from malformed JSON")
                return itemsToResults(rescuedItems)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Regex rescue failed: ${e.message}")
        }

        Log.e(TAG, "All parse strategies failed for: ${cleanJson.take(500)}")
        return emptyList()
    }

    /**
     * Apply LLM match results by linking notes to their matched events.
     *
     * @param results LLM match results
     * @param minConfidence Minimum confidence to apply (default 0.35)
     * @return Number of notes successfully linked
     */
    suspend fun applyLlmMatches(
        results: List<LlmMatchResult>,
        minConfidence: Float = 0.35f  // Prompt forces matches with min 0.4, allow slight margin
    ): Int = withContext(Dispatchers.IO) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Apply LLM matches")
            return@withContext 0
        }

        var linkedCount = 0
        var skippedCount = 0
        var hallucinatedCount = 0
        val validNoteIds = quickValidation(results.map { it.noteUniqueId }.distinct()).toSet()

        for (result in results) {
            if (result.matchedEventId != null && result.confidence >= minConfidence) {
                try {
                    if (result.noteUniqueId !in validNoteIds) {
                        skippedCount++
                        Log.w(TAG, "Skipping stale note ID from LLM results: ${result.noteUniqueId}")
                        continue
                    }

                    // CRITICAL: Validate that the event ID actually exists in our database
                    // LLMs sometimes hallucinate event IDs instead of copying from input
                    val event = smartLinkEventRepository.getEventById(result.matchedEventId)
                    if (event == null) {
                        hallucinatedCount++
                        Log.w(TAG, "LLM HALLUCINATED event ID: ${result.matchedEventId} (not in database)")
                        continue
                    }

                    // Get the note to find its date
                    val note = noteRepository.getNoteById(result.noteUniqueId)
                    if (note == null) {
                        skippedCount++
                        Log.w(TAG, "Note not found: ${result.noteUniqueId}")
                        continue
                    }

                    noteRepository.linkNoteToEventEnhanced(
                        noteId = result.noteUniqueId,
                        eventId = result.matchedEventId,
                        linkedDayDate = note.originalDate,
                        confidence = when {
                            result.confidence >= 0.9f -> LinkConfidence.HIGH
                            result.confidence >= 0.7f -> LinkConfidence.MEDIUM
                            else -> LinkConfidence.LOW
                        },
                        confidenceScore = (result.confidence * 100).toInt(),
                        matchReasons = SmartLinkDecisionSupport.mergeMatchReasons(
                            emptyList(),
                            addAiSuggested = true
                        ),
                        suggestedEventIds = emptyList(),
                        needsAttention = false,
                        isManual = false,
                        decisionMode = SmartLinkDecisionMode.AI_APPLY,
                        engineVersion = SMART_LINK_ENGINE_VERSION,
                        dataQualityFlags = buildDataQualityFlags(
                            noteProfile = null,
                            eventProfile = null,
                            serviceAvailable = getSemanticServiceForMatching() != null,
                            includeEventSemantics = false,
                            aiGenerated = true
                        )
                    )
                    linkedCount++
                    Log.d(TAG, "LLM linked note ${result.noteUniqueId} to event ${result.matchedEventId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply LLM match: ${e.message}", e)
                }
            }
        }

        // Invalidate cache after linking
        if (linkedCount > 0) {
            noteRepository.invalidateLinksCache()
        }

        Log.d(TAG, "Applied $linkedCount LLM matches (skipped=$skippedCount, hallucinated=$hallucinatedCount)")
        linkedCount
    }

    /**
     * Quick validation that specific notes still exist on device.
     * Returns list of IDs that are still valid.
     */
    suspend fun quickValidation(noteIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        val providerNotesById = loadProviderNotesById()
        noteIds.filter { noteId ->
            val providerHasNote = providerNotesById.containsKey(noteId)
            try {
                val json = BooxNative.noteExistsOnDevice(BooxNative.KSYNC_BASE_PATH, noteId)
                val result = JSONObject(json)
                shouldTreatNoteAsPresentDuringQuickValidation(
                    nativeExists = result.optBoolean("exists", false),
                    providerHasNote = providerHasNote
                )
            } catch (e: Throwable) {
                // Be conservative on validation failures so transient native/provider issues
                // do not delete note/link/OCR state from the dialog path.
                shouldTreatNoteAsPresentDuringQuickValidation(
                    nativeExists = null,
                    providerHasNote = providerHasNote
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AI + ALGORITHM MERGE FUNCTIONS (Dec 2025)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convert an LLM match result to a CrossDayLinkSuggestion for unified display.
     * This allows AI results to be shown alongside algorithm results in the review UI.
     *
     * @param llmResult The LLM match result
     * @param note The note being matched
     * @param event The matched event
     * @return CrossDayLinkSuggestion or null if conversion fails
     */
    fun convertLlmToSuggestion(
        llmResult: LlmMatchResult,
        note: BooxNoteIndex,
        event: CalendarEvent
    ): CrossDayLinkSuggestion? {
        return try {
            val noteDate = LocalDate.parse(note.originalDate)
            val eventDate = LocalDate.parse(event.dayId)
            val dayOffset = java.time.temporal.ChronoUnit.DAYS.between(noteDate, eventDate).toInt()

            val confidence = when {
                llmResult.confidence >= 0.7f -> LinkConfidence.HIGH
                llmResult.confidence >= 0.4f -> LinkConfidence.MEDIUM
                else -> LinkConfidence.LOW
            }

            val suggestionType = when {
                dayOffset == 0 -> SuggestionType.SAME_DAY
                dayOffset < 0 -> SuggestionType.FOLLOW_UP  // Event was before, note is follow-up
                else -> SuggestionType.PREP_NOTES          // Event is after, note is prep
            }

            CrossDayLinkSuggestion(
                note = note,
                event = event,
                dayOffset = dayOffset,
                suggestionType = suggestionType,
                confidence = confidence,
                confidenceScore = (llmResult.confidence * 100).toInt(),
                reasoning = "AI: ${llmResult.reasoning}",  // Prefix to indicate AI source
                matchReasons = SmartLinkDecisionSupport.mergeMatchReasons(
                    emptyList(),
                    addAiSuggested = true
                ),
                dataQualityFlags = buildDataQualityFlags(
                    noteProfile = null,
                    eventProfile = null,
                    serviceAvailable = getSemanticServiceForMatching() != null,
                    includeEventSemantics = false,
                    aiGenerated = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert LLM result to suggestion: ${e.message}")
            null
        }
    }

    /**
     * Merge LLM match results into an existing BulkSmartLinkSession.
     * AI suggestions are added to notes that previously had no algorithm matches.
     *
     * @param session The original session from algorithm analysis
     * @param llmResults The LLM match results to merge
     * @return Updated BulkSmartLinkSession with merged suggestions and recalculated stats
     */
    suspend fun mergeAiResultsIntoSession(
        session: BulkSmartLinkSession,
        llmResults: List<LlmMatchResult>
    ): BulkSmartLinkSession = withContext(Dispatchers.IO) {
        if (!isSmartLinkRuntimeEnabled()) {
            logSmartLinkRuntimeDisabled("Merge AI Smart Link results")
            return@withContext session
        }

        val mergedSuggestionsMap = session.suggestionsMap.toMutableMap()
        var addedCount = 0

        for (llmResult in llmResults) {
            if (llmResult.matchedEventId == null) continue

            val note = session.unlinkedNotes.find { it.uniqueId == llmResult.noteUniqueId }
            if (note == null) {
                Log.w(TAG, "Note not found in session for LLM result: ${llmResult.noteUniqueId}")
                continue
            }

            val event = smartLinkEventRepository.getEventById(llmResult.matchedEventId)
            if (event == null) {
                Log.w(TAG, "Event not found for LLM result: ${llmResult.matchedEventId}")
                continue
            }

            val suggestion = convertLlmToSuggestion(llmResult, note, event)
            if (suggestion == null) continue

            val existingSuggestions = mergedSuggestionsMap[note.uniqueId] ?: emptyList()

            // Only add if not a duplicate event
            if (existingSuggestions.none { it.event.id == event.id }) {
                mergedSuggestionsMap[note.uniqueId] = existingSuggestions + suggestion
                addedCount++
            }
        }

        // Recalculate stats
        var highCount = 0
        var mediumCount = 0
        var lowCount = 0
        var noMatchCount = 0

        for (note in session.unlinkedNotes) {
            val suggestions = mergedSuggestionsMap[note.uniqueId] ?: emptyList()
            val bestConfidence = suggestions.maxByOrNull { it.confidenceScore }?.confidence

            when (bestConfidence) {
                LinkConfidence.HIGH -> highCount++
                LinkConfidence.MEDIUM -> mediumCount++
                LinkConfidence.LOW -> lowCount++
                null, LinkConfidence.NONE -> noMatchCount++
            }
        }

        Log.d(TAG, "Merged $addedCount AI suggestions into session. New stats: high=$highCount, medium=$mediumCount, low=$lowCount, noMatch=$noMatchCount")

        BulkSmartLinkSession(
            unlinkedNotes = session.unlinkedNotes,
            suggestionsMap = mergedSuggestionsMap,
            stats = BulkLinkStats(
                totalUnlinked = session.unlinkedNotes.size,
                highConfidenceCount = highCount,
                mediumConfidenceCount = mediumCount,
                lowConfidenceCount = lowCount,
                noMatchCount = noMatchCount
            )
        )
    }
}
