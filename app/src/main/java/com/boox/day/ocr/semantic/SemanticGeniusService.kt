package com.boox.day.ocr.semantic

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.EntityCooccurrence
import com.boox.day.data.db.EntityCooccurrenceDao
import com.boox.day.data.db.Kv
import com.boox.day.data.db.KvRepository
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.NoteTimePreference
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.db.UnifiedOcrSearchResult
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.data.db.UserActivityPattern
import com.boox.day.data.db.UserActivityPatternDao
import com.boox.day.ocr.OcrQuotaManager
import java.util.concurrent.TimeUnit
import com.boox.day.ocr.model.*
import com.boox.day.ocr.semantic.classification.ConceptBridgeLoader
import com.boox.day.ocr.semantic.classification.DictionaryLoader
import com.boox.day.ocr.semantic.classification.DomainIntentClassifier
import com.boox.day.ocr.semantic.classification.EventPatternsDictionary
import com.boox.day.ocr.semantic.model.*
// Extraction components (Semantic Unification - Phase 1)
import com.boox.day.ocr.semantic.TextNormalizer
import com.boox.day.ocr.semantic.DocumentClassifier
import com.boox.day.ocr.semantic.KeywordExtractor
import com.boox.day.ocr.semantic.PhraseExtractor
import com.boox.day.ocr.semantic.SentenceExtractor
import com.boox.day.ocr.semantic.EntityExtractor
import com.boox.day.ocr.semantic.ActionItemExtractor
import com.boox.day.ocr.semantic.TemporalExtractor
import com.boox.day.ocr.semantic.TopicClassifier
import com.boox.day.ocr.semantic.SynopsisGenerator
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlin.math.abs
import kotlin.math.ln

/**
 * SemanticGeniusService - READ-ONLY semantic intelligence provider.
 *
 * Provides pre-computed semantic data to multiple features:
 * - BooxNoteIndexer (Smart Link event matching)
 * - SuggestionContextBuilder (AI context loading)
 * - SmartSuggestionsDialog (task recommendations)
 * - OcrSearchComponents (enhanced search)
 *
 * IMPORTANT: Does NOT re-run classification. All data from unified_ocr_text.
 *
 * Architecture: Service → Repository → DAO (matches codebase pattern)
 */
class SemanticGeniusService(context: Context) {

    companion object {
        private const val TAG = "SemanticGenius"
        private const val PROFILE_CACHE_SIZE = 32
        private const val DATE_CONTEXT_CACHE_SIZE = 14
        private const val EVENT_PROFILE_CACHE_SIZE = 200  // Events change less frequently
        private const val DUPLICATE_TIME_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes for duplicate detection
        const val DEFAULT_EVENT_DESCRIPTION_LIMIT = 500

        // Entity co-occurrence weight (Phase 5 - SemanticGenius Transformation)
        // Co-occurrence can boost hybrid score by up to 15%
        const val COOCCURRENCE_WEIGHT = 0.15f

        // Semantic version - increment to trigger reindex (bumped for full extraction unification)
        const val SEMANTIC_VERSION = 2
        private const val KV_SEMANTIC_VERSION = "semantic_version"

        // Unified extraction constants (Semantic Unification - Phase 1)
        /** Minimum text length for meaningful extraction */
        private const val MIN_TEXT_LENGTH = 20
        /** Current extraction version (v1=original, v2=Domain+Intent) */
        const val EXTRACTION_VERSION = 2

        // User Activity Pattern thresholds (Phase 6 - Section 4.9)
        private const val PATTERN_MIN_OBSERVATIONS = 3
        private const val NEXT_DAY_PATTERN_BOOST = 0.15f
        private const val PREP_NOTES_PATTERN_BOOST = 0.10f

        // Time thresholds for note timing classification
        private val ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1)
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)

        // Location extraction patterns
        private val LOCATION_PATTERNS = listOf(
            Regex("(?i)\\b(?:room|conf(?:erence)?|bldg|building|floor|suite|office)\\s*[A-Z0-9-]+\\b"),
            Regex("(?i)\\bat\\s+(?:the\\s+)?([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)"),
            Regex("(?i)\\b(?:zoom|teams|google\\s*meet|webex|skype)\\b")
        )

        // Domain and Intent event keywords moved to JSON dictionary (Phase 7 - Dictionary Consolidation)
        // See: assets/classification/en/event_patterns.json
    }

    // Use Repository pattern (not direct DAO) - matches UnifiedOcrPipeline
    private val repository by lazy { UnifiedOcrRepository(context) }

    private val json = Json { ignoreUnknownKeys = true }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE ARCHITECTURE
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // Three LRU caches with different purposes and activation status:
    //
    // 1. profileCache - PREPARED FOR FUTURE USE (no consumers yet)
    //    - Used by: getProfile(sourceType, sourceId, pageId)
    //    - Called by: NOBODY - method exists but has no callers
    //    - Note: BooxNoteIndexer uses getProfilesForSource() which bypasses this cache
    //
    // 2. dateContextCache - PREPARED FOR FUTURE USE (no consumers yet)
    //    - Used by: getDateContext(date)
    //    - Called by: NOBODY - method exists but has no callers
    //    - Future: Daily insights, suggestions UI
    //
    // 3. eventProfileCache - ACTIVELY USED
    //    - Used by: getEventProfile(), getOrCreateEventProfile()
    //    - Called by: BooxNoteIndexer, EventChangeObserver
    //    - Invalidated by: EventChangeObserver, LocalEventRepository
    //
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Profile cache for single-page queries via getProfile().
     * CURRENTLY UNUSED - getProfile() has no callers.
     * BooxNoteIndexer uses getProfilesForSource() which queries DB directly.
     * Wire up when a UI needs single-page profile with caching.
     */
    private val profileCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, SemanticProfile>(PROFILE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SemanticProfile>) =
                size > PROFILE_CACHE_SIZE
        }
    )

    /**
     * Date context cache for aggregated daily data via getDateContext().
     * CURRENTLY UNUSED - getDateContext() has no callers.
     * Wire up when daily insights/suggestions UI is implemented.
     */
    private val dateContextCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, DateSemanticContext>(DATE_CONTEXT_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DateSemanticContext>) =
                size > DATE_CONTEXT_CACHE_SIZE
        }
    )

    /**
     * Event profile cache - ACTIVELY USED by BooxNoteIndexer for Smart Link matching.
     * Larger than note cache (200 vs 32) because events change less frequently.
     * Invalidated by EventChangeObserver and LocalEventRepository on event changes.
     * Uses LRU eviction via LinkedHashMap with accessOrder=true.
     * Thread-safe via Collections.synchronizedMap wrapper.
     */
    private val eventProfileCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, SemanticProfile>(EVENT_PROFILE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SemanticProfile>) =
                size > EVENT_PROFILE_CACHE_SIZE
        }
    )

    // DomainIntentClassifier for on-demand event classification
    private val classifier by lazy { DomainIntentClassifier(context) }

    // Store context reference for classifier initialization
    private val appContext = context.applicationContext

    // Entity co-occurrence DAO (Phase 5 - SemanticGenius Transformation)
    private val cooccurrenceDao: EntityCooccurrenceDao by lazy {
        AppDatabase.getDatabase(appContext).entityCooccurrenceDao()
    }

    // User Activity Pattern DAO (Phase 6 - SemanticGenius Transformation)
    private val userActivityPatternDao: UserActivityPatternDao by lazy {
        AppDatabase.getDatabase(appContext).userActivityPatternDao()
    }

    // KV Repository for storing semantic version (Phase 6)
    private val kvRepository by lazy { KvRepository(appContext) }

    // Database for direct DAO access (Phase 6 - reindexing)
    private val database by lazy { AppDatabase.getDatabase(appContext) }

    // Server-authoritative OCR config and feature gates
    private val quotaManager by lazy { OcrQuotaManager(appContext) }

    // Dictionary Loader for event patterns (Phase 7 - Dictionary Consolidation)
    private val dictionaryLoader by lazy { DictionaryLoader(appContext) }

    // Event patterns dictionary loaded from JSON (Phase 7)
    private val eventPatterns: EventPatternsDictionary by lazy { dictionaryLoader.loadEventPatterns() }

    // Concept Bridges for semantic expansion (Phase 8 - Structural Fixes)
    init {
        ConceptBridgeLoader.initialize(appContext)
    }

    private fun isSemanticExtractionRuntimeEnabled(action: String): Boolean {
        if (!quotaManager.isSemanticExtractionEnabled) {
            Log.i(TAG, "Skipping $action because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")
            return false
        }
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROFILE RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get semantic profile for a specific page.
     *
     * CURRENTLY UNUSED - This method has no callers in the codebase.
     * BooxNoteIndexer uses getProfilesForSource() instead, which bypasses
     * the profileCache and queries the database directly.
     *
     * This method exists for future use cases that need single-page profile
     * lookups with caching (e.g., page-specific UI, detailed view).
     *
     * Uses profileCache for repeated lookups of the same page.
     */
    suspend fun getProfile(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String
    ): SemanticProfile? {
        val cacheKey = "${sourceType.name}_${sourceId}_${pageId}"

        profileCache[cacheKey]?.let { return it }

        val ocrText = repository.getOcrText(sourceType, sourceId, pageId) ?: return null
        val profile = parseToProfile(ocrText)

        profileCache[cacheKey] = profile
        return profile
    }

    /**
     * Get profiles for all pages of a source.
     */
    suspend fun getProfilesForSource(
        sourceType: OcrSource,
        sourceId: String
    ): List<SemanticProfile> {
        return repository.getOcrTextsForSource(sourceType, sourceId)
            .map { parseToProfile(it) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SMART LINK BACKFILL FIX - Batch profile loading
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all profiles for a source type (batch loading for Smart Link).
     *
     * SMART LINK BACKFILL FIX: Instead of loading profiles one-by-one (N+1 queries),
     * this loads all profiles for a source type in a single query. Returns a map
     * from sourceId to list of profiles for that source.
     *
     * @param sourceType The source type (e.g., BOOX_NOTE)
     * @return Map of sourceId -> list of profiles for that source
     */
    suspend fun getAllProfilesForSourceType(sourceType: OcrSource): Map<String, List<SemanticProfile>> {
        val ocrTexts = repository.getOcrTextsBySourceType(sourceType)
        Log.d(TAG, "Batch loaded ${ocrTexts.size} OCR texts for ${sourceType.name}")

        return ocrTexts
            .map { parseToProfile(it) }
            .groupBy { it.sourceId }
    }

    /**
     * Check if any OCR data exists for a source type.
     *
     * SMART LINK BACKFILL FIX: Quick check without loading all data.
     * Used to gate Smart Link features on backfill completion.
     *
     * @param sourceType The source type to check
     * @return true if at least one OCR text exists for this source type
     */
    suspend fun hasAnyOcrData(sourceType: OcrSource): Boolean {
        return repository.getCountBySourceType(sourceType) > 0
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UNIFIED EXTRACTION (Semantic Unification - Phase 1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extract semantic information from text.
     *
     * This is the SINGLE entry point for all semantic extraction.
     * Source-aware: uses appropriate normalizer based on source type.
     *
     * Pipeline (12 steps):
     * 1. Normalize (source-aware)
     * 2. Document style classification
     * 3-4. Keyword extraction (TF-IDF + title boost)
     * 5. Phrase extraction (n-gram scoring)
     * 6. Sentence extraction (signal-based)
     * 7. Entity extraction (10 NER types)
     * 8. Action item extraction (verb+object, checkboxes)
     * 9. Temporal extraction (dates, weekdays, relative)
     * 10. Topic classification
     * 11. Synopsis generation (900 chars max)
     * 12. Domain + Intent classification
     *
     * @param text The text to analyze
     * @param sourceType The content source type (affects normalization and priors)
     * @param sourceTitle Optional title for keyword boosting
     * @param sourceCreatedAt Optional creation timestamp for temporal analysis
     * @return SemanticBundle with all extracted information
     */
    fun extractSemantics(
        text: String,
        sourceType: OcrSource,
        sourceTitle: String? = null,
        sourceCreatedAt: Long? = null
    ): SemanticBundle {
        val startTime = System.currentTimeMillis()

        // Pre-check: Low signal detection
        if (text.length < MIN_TEXT_LENGTH) {
            Log.d(TAG, "Low signal: text length ${text.length} < $MIN_TEXT_LENGTH")
            return SemanticBundle.lowSignal()
        }

        try {
            // ═══════════════════════════════════════════════════════════════
            // Step 1: Source-Aware Normalization
            // ═══════════════════════════════════════════════════════════════
            val (cleanText, sentences) = normalizeForSource(text, sourceType, sourceTitle)

            if (cleanText.length < MIN_TEXT_LENGTH) {
                Log.d(TAG, "Low signal after normalization: ${cleanText.length} chars")
                return SemanticBundle.lowSignal()
            }

            // ═══════════════════════════════════════════════════════════════
            // Step 2: Document Classification
            // ═══════════════════════════════════════════════════════════════
            val docStyle = DocumentClassifier.classify(cleanText, sentences)

            // ═══════════════════════════════════════════════════════════════
            // Step 7: Entity Extraction (run early for other steps)
            // ═══════════════════════════════════════════════════════════════
            val baseEntities = EntityExtractor.extract(cleanText)

            // Add attendees for event sources
            val allEntities = if (sourceType.isEventSource()) {
                val attendees = EventTextNormalizer.extractAttendees(text)
                val attendeeEntities = attendees.map {
                    ExtractedEntity(it, EntityType.PERSON, 0.8f)
                }
                baseEntities + attendeeEntities
            } else {
                baseEntities
            }

            // ═══════════════════════════════════════════════════════════════
            // Step 9: Temporal Extraction (run early for keyword boosting)
            // ═══════════════════════════════════════════════════════════════
            val temporalSignals = TemporalExtractor.extract(cleanText, sourceCreatedAt)

            // ═══════════════════════════════════════════════════════════════
            // Steps 3-4: Keyword Extraction
            // ═══════════════════════════════════════════════════════════════
            val dateProximityWords = KeywordExtractor.findWordsNearDates(cleanText)
            val keywords = KeywordExtractor.extract(
                text = cleanText,
                title = sourceTitle,
                dateProximityWords = dateProximityWords
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 5: Phrase Extraction
            // ═══════════════════════════════════════════════════════════════
            val keyPhrases = PhraseExtractor.extract(
                sentences = sentences,
                keywords = keywords,
                entities = allEntities
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 6: Sentence Extraction
            // ═══════════════════════════════════════════════════════════════
            val keySentences = SentenceExtractor.extract(
                sentences = sentences,
                keywords = keywords,
                entities = allEntities
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 8: Action Item Extraction
            // ═══════════════════════════════════════════════════════════════
            val actionItems = ActionItemExtractor.extract(
                text = cleanText,
                sentences = sentences
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 10: Topic Classification
            // ═══════════════════════════════════════════════════════════════
            val topics = TopicClassifier.classify(
                text = cleanText,
                keywords = keywords,
                actionItems = actionItems
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 11: Synopsis Generation
            // ═══════════════════════════════════════════════════════════════
            val synopsis = SynopsisGenerator.generate(
                sourceTitle = sourceTitle,
                topics = topics,
                temporalSignals = temporalSignals,
                keywords = keywords,
                actionItems = actionItems,
                keySentences = keySentences
            )

            // ═══════════════════════════════════════════════════════════════
            // Step 12: Domain + Intent Classification
            // ═══════════════════════════════════════════════════════════════
            // CRITICAL: Events do NOT record user priors (external content)
            val recordPriors = !sourceType.isEventSource()

            val classificationResult = try {
                classifier.classify(
                    text = cleanText,
                    title = sourceTitle,
                    recordPriors = recordPriors
                )
            } catch (e: Exception) {
                Log.w(TAG, "Classification failed: ${e.message}")
                null
            }

            // ═══════════════════════════════════════════════════════════════
            // Build result
            // ═══════════════════════════════════════════════════════════════
            val bundle = SemanticBundle(
                docStyle = docStyle,
                domain = classificationResult?.domain,
                domainConfidence = classificationResult?.domainConfidence ?: 0f,
                intents = classificationResult?.intents?.map {
                    IntentEntry(it.intent, it.confidence)
                } ?: emptyList(),
                subdomainHint = classificationResult?.subdomainHint,
                classificationExplainJson = classificationResult?.toJson(),
                keywords = keywords,
                keyPhrases = keyPhrases,
                keySentences = keySentences,
                entities = allEntities,
                actionItems = actionItems,
                temporalSignals = temporalSignals ?: TemporalSignals(),
                topics = topics,
                synopsis = synopsis ?: "",
                isLowSignal = false,
                semanticVersion = EXTRACTION_VERSION
            )

            // ═══════════════════════════════════════════════════════════════
            // Quality Gates
            // ═══════════════════════════════════════════════════════════════
            val passesGates = bundle.passesQualityGates()

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "PERF: extractSemantics(${sourceType.name}) ${duration}ms - " +
                "keywords=${keywords.size} entities=${allEntities.size} " +
                "actions=${actionItems.size} domain=${classificationResult?.domain?.name ?: "null"} " +
                "passesGates=$passesGates")

            return if (passesGates) {
                bundle
            } else {
                if (keywords.isEmpty() && allEntities.isEmpty() && keySentences.isEmpty()) {
                    Log.w(TAG, "Quality gates failed with no usable data - returning low_signal")
                    SemanticBundle.lowSignal()
                } else {
                    Log.w(TAG, "Quality gates failed but returning partial data")
                    bundle
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            return SemanticBundle.lowSignal()
        }
    }

    /**
     * Lightweight semantic extraction for short text (event titles, brief notes).
     * Bypasses MIN_TEXT_LENGTH gate for known short-form content.
     *
     * This enables event titles like "Meeting" (7 chars), "Lunch" (5 chars), "Call" (4 chars)
     * to get meaningful semantic profiles instead of returning LOW_SIGNAL.
     *
     * @param text The short text to analyze (typically < MIN_TEXT_LENGTH)
     * @param sourceType The content source type
     * @return SemanticBundle with extracted information
     */
    private fun extractSemanticsShortText(
        text: String,
        sourceType: OcrSource
    ): SemanticBundle {
        val cleanText = if (sourceType.isEventSource()) {
            EventTextNormalizer.normalize("", text)
        } else {
            TextNormalizer.normalize(text).cleanText
        }

        if (cleanText.isBlank()) return SemanticBundle.lowSignal()

        // All tokens become keywords for short text
        val tokens = cleanText.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        val keywords = tokens.mapIndexed { idx, token ->
            KeywordEntry(
                term = token,
                score = 1.0f - (idx * 0.1f).coerceAtMost(0.5f), // Slight position decay
                frequency = 1
            )
        }

        // Classification still works on short text
        val classificationResult = try {
            classifier.classify(
                text = cleanText,
                title = cleanText, // Use entire text as title for short text
                recordPriors = !sourceType.isEventSource() // Events don't record priors
            )
        } catch (e: Exception) {
            Log.w(TAG, "Short-text classification failed: ${e.message}")
            null
        }

        // Entity extraction works on short text (names, orgs)
        val entities = EntityExtractor.extract(cleanText)

        Log.d(TAG, "Short-text extraction for '${cleanText}': " +
            "keywords=${keywords.size} domain=${classificationResult?.domain?.name ?: "null"} " +
            "entities=${entities.size}")

        return SemanticBundle(
            docStyle = DocumentStyle.MIXED,
            domain = classificationResult?.domain,
            domainConfidence = classificationResult?.domainConfidence ?: 0f,
            intents = classificationResult?.intents?.map {
                IntentEntry(it.intent, it.confidence)
            } ?: emptyList(),
            subdomainHint = classificationResult?.subdomainHint,
            classificationExplainJson = classificationResult?.toJson(),
            keywords = keywords,
            keyPhrases = listOf(cleanText), // Entire text is the phrase
            keySentences = emptyList(),
            entities = entities,
            actionItems = emptyList(),
            temporalSignals = TemporalSignals(),
            topics = emptyList(),
            synopsis = cleanText,
            isLowSignal = false,
            semanticVersion = EXTRACTION_VERSION
        )
    }

    /**
     * Source-aware text normalization.
     *
     * - OCR sources (BOOX_NOTE, MEMO, TASK): Use TextNormalizer for OCR artifact cleanup
     * - Event sources (CALENDAR_EVENT, LOCAL_EVENT): Use EventTextNormalizer for meeting boilerplate
     *
     * @return Pair of (cleanText, sentences)
     */
    private fun normalizeForSource(
        text: String,
        sourceType: OcrSource,
        sourceTitle: String?
    ): Pair<String, List<String>> {
        return if (sourceType.isEventSource()) {
            // Events: Use EventTextNormalizer to strip meeting boilerplate
            val cleanText = EventTextNormalizer.normalize(sourceTitle ?: "", text)
            val sentences = cleanText.split(Regex("[.!?]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            cleanText to sentences
        } else {
            // OCR sources: Use TextNormalizer for OCR cleanup
            val normalized = TextNormalizer.normalize(text)
            normalized.cleanText to normalized.sentences
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATE CONTEXT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get aggregated semantic context for a date with optional source filtering.
     *
     * CURRENTLY UNUSED - This method has no callers in the codebase.
     * Prepared for future features like daily insights, suggestions, or
     * "what happened on this day" summaries.
     *
     * Uses dateContextCache for repeated lookups (when no filters applied).
     *
     * @param date The date to query (yyyy-MM-dd)
     * @param includeSourceTypes If non-null, only include these source types
     * @param excludeSourceTypes If non-null, exclude these source types
     * @return Aggregated semantic context for the date
     */
    suspend fun getDateContext(
        date: String,
        includeSourceTypes: Set<OcrSource>? = null,
        excludeSourceTypes: Set<OcrSource>? = null
    ): DateSemanticContext {
        // Only use cache when no filters are applied
        val useCache = includeSourceTypes == null && excludeSourceTypes == null
        if (useCache) {
            dateContextCache[date]?.let { return it }
        }

        val rawTexts = repository.getOcrTextsBySourceDate(date)

        // Apply source type filters
        val allTexts = rawTexts.filter { ocrText ->
            val sourceType = OcrSource.fromString(ocrText.sourceType)
            // Include filter
            (includeSourceTypes == null || sourceType in includeSourceTypes) &&
            // Exclude filter
            (excludeSourceTypes == null || sourceType !in excludeSourceTypes)
        }

        if (allTexts.isEmpty()) {
            val empty = DateSemanticContext.empty(date)
            if (useCache) {
                dateContextCache[date] = empty
            }
            return empty
        }

        val memos = allTexts.count { it.sourceType == OcrSource.MEMO.name }
        val tasks = allTexts.count { it.sourceType == OcrSource.TASK.name }
        val booxNotes = allTexts.count { it.sourceType == OcrSource.BOOX_NOTE.name }

        val context = aggregateToDateContext(date, allTexts, memos, tasks, booxNotes)
        if (useCache) {
            dateContextCache[date] = context
        }
        return context
    }

    /**
     * Get context for a date range (parallel execution).
     */
    suspend fun getDateRangeContext(
        startDate: String,
        endDate: String
    ): List<DateSemanticContext> = coroutineScope {
        val dates = generateDateRange(startDate, endDate)
        dates.map { async { getDateContext(it) } }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EVENT MATCHING (Smart Link)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find content related to a calendar event with optional source filtering.
     *
     * @param eventTitle The event title to match against
     * @param eventDescription Optional event description
     * @param eventDate The event date (yyyy-MM-dd)
     * @param searchWindow Days before/after to search (default 3)
     * @param includeSourceTypes If non-null, only include these source types
     * @param excludeSourceTypes If non-null, exclude these source types
     * @return List of matching semantic results sorted by score
     */
    suspend fun findRelatedContent(
        eventTitle: String,
        eventDescription: String? = null,
        eventDate: String,
        searchWindow: Int = 3,
        includeSourceTypes: Set<OcrSource>? = null,
        excludeSourceTypes: Set<OcrSource>? = null
    ): List<SemanticMatchResult> {
        val eventKeywords = extractKeywordsFromText(eventTitle + " " + (eventDescription ?: ""))
        val eventPersons = extractPersonNames(eventTitle, eventDescription)
        val eventOrgs = extractOrgNames(eventTitle, eventDescription)

        val startDate = offsetDate(eventDate, -searchWindow)
        val endDate = offsetDate(eventDate, searchWindow)
        val rawCandidates = repository.getOcrTextsInDateRange(startDate, endDate)

        // Apply source type filters
        val candidates = rawCandidates.filter { ocrText ->
            val sourceType = OcrSource.fromString(ocrText.sourceType)
            // Include filter
            (includeSourceTypes == null || sourceType in includeSourceTypes) &&
            // Exclude filter
            (excludeSourceTypes == null || sourceType !in excludeSourceTypes)
        }

        return candidates.mapNotNull { ocrText ->
            val profile = parseToProfile(ocrText)
            val score = calculateMatchScore(profile, eventKeywords, eventPersons, eventOrgs)

            if (score > 0.15f) {
                SemanticMatchResult(
                    noteId = ocrText.sourceId,
                    eventTitle = eventTitle,
                    score = score,
                    matchReasons = buildMatchReasons(profile, eventKeywords, eventPersons, eventOrgs),
                    profile = profile
                )
            } else null
        }.sortedByDescending { it.score }
    }

    /**
     * Calculate semantic match score for a set of profiles against an event.
     */
    fun calculateEventSemanticScore(
        profiles: List<SemanticProfile>,
        eventTitle: String,
        eventDescription: String? = null
    ): Float {
        if (profiles.isEmpty()) return 0f
        val tokens = buildEventSemanticTokens(
            eventTitle = eventTitle,
            eventDescription = eventDescription,
            descriptionCharLimit = DEFAULT_EVENT_DESCRIPTION_LIMIT
        )
        return calculateEventSemanticScoreFromTokens(profiles, tokens)
    }

    data class EventSemanticTokens(
        val keywords: Set<String>,
        val persons: Set<String>,
        val orgs: Set<String>,
        val locations: Set<String> = emptySet(),
        val rawText: String = ""  // For action item matching
    )

    fun buildEventSemanticTokens(
        eventTitle: String,
        eventDescription: String? = null,
        eventLocation: String? = null,
        descriptionCharLimit: Int = DEFAULT_EVENT_DESCRIPTION_LIMIT
    ): EventSemanticTokens {
        val descriptionText = eventDescription
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.length > descriptionCharLimit) it.take(descriptionCharLimit) else it }
        val combinedText = if (descriptionText != null) "$eventTitle $descriptionText" else eventTitle
        return EventSemanticTokens(
            keywords = extractKeywordsFromText(combinedText),
            persons = extractPersonNames(eventTitle, descriptionText),
            orgs = extractOrgNames(eventTitle, descriptionText),
            locations = extractLocationTokens(eventTitle, descriptionText, eventLocation),
            rawText = combinedText.lowercase()
        )
    }

    /**
     * Extract location tokens from event text and explicit location field.
     */
    private fun extractLocationTokens(
        title: String,
        description: String?,
        explicitLocation: String?
    ): Set<String> {
        val locations = mutableSetOf<String>()

        // Add explicit location if present
        explicitLocation?.trim()?.takeIf { it.isNotEmpty() }?.let {
            locations.add(it.lowercase())
            // Also add individual words for partial matching
            it.split(Regex("[,\\s]+"))
                .filter { word -> word.length > 2 }
                .forEach { word -> locations.add(word.lowercase()) }
        }

        // Extract location-like patterns from title and description
        val combinedText = "$title ${description ?: ""}"
        LOCATION_PATTERNS.forEach { pattern ->
            pattern.findAll(combinedText).forEach { match ->
                locations.add(match.value.lowercase())
            }
        }

        return locations
    }

    fun calculateEventSemanticScoreFromTokens(
        profiles: List<SemanticProfile>,
        tokens: EventSemanticTokens
    ): Float {
        if (profiles.isEmpty()) return 0f
        return profiles.maxOf { profile ->
            calculateMatchScore(profile, tokens)  // Use enhanced scoring with full tokens
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9-COMPONENT SEMANTIC AFFINITY (Phase 3 - SemanticGenius Transformation)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculate semantic affinity between two profiles.
     *
     * Component distribution (max 1.0):
     * 1. Domain Match: 0.20
     * 2. Intent Compatibility: 0.12
     * 3. Keyword Overlap: 0.18
     * 4. Key Phrases: 0.08
     * 5. Person Entities: 0.12
     * 6. Org Entities: 0.08
     * 7. Location Entities: 0.07
     * 8. Action Items: 0.10
     * 9. Temporal Signals: 0.05
     *
     * @param profileA First semantic profile (typically note)
     * @param profileB Second semantic profile (typically event)
     * @return Affinity score 0.0 to 1.0
     */
    fun calculateSemanticAffinity(profileA: SemanticProfile, profileB: SemanticProfile): Float {
        var score = 0f

        // ═══════════════════════════════════════════════════════════════════════════
        // 1. DOMAIN MATCH (max 0.20)
        // ═══════════════════════════════════════════════════════════════════════════
        if (profileA.domain != null && profileB.domain != null) {
            if (profileA.domain == profileB.domain) {
                // Exact domain match
                score += 0.20f
            } else if (areSiblingDomains(profileA.domain, profileB.domain)) {
                // Sibling domains (e.g., WORK and BUSINESS)
                score += 0.10f
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // 2. INTENT COMPATIBILITY (max 0.12)
        // ═══════════════════════════════════════════════════════════════════════════
        val intentA = profileA.intents.map { it.intent }.toSet()
        val intentB = profileB.intents.map { it.intent }.toSet()
        val intentOverlap = intentA.intersect(intentB)

        if (intentOverlap.isNotEmpty()) {
            val confidencesA = profileA.intents.filter { it.intent in intentOverlap }.map { it.confidence }
            val confidencesB = profileB.intents.filter { it.intent in intentOverlap }.map { it.confidence }
            val avgConfidence = if (confidencesA.isNotEmpty() && confidencesB.isNotEmpty()) {
                (confidencesA.average() + confidencesB.average()) / 2
            } else {
                0.0
            }
            score += (0.12f * avgConfidence.toFloat()).coerceAtMost(0.12f)
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // 3. KEYWORD OVERLAP (max 0.18) - Jaccard similarity with concept bridge expansion
        // ═══════════════════════════════════════════════════════════════════════════
        val rawKeywordsA = profileA.keywords.map { it.term.lowercase() }.toSet()
        val rawKeywordsB = profileB.keywords.map { it.term.lowercase() }.toSet()

        // Expand with concept bridges for semantic matching
        // e.g., "hungry" -> includes "lunch", "meal", "food"
        val keywordsA = ConceptBridgeLoader.expandKeywords(rawKeywordsA)
        val keywordsB = ConceptBridgeLoader.expandKeywords(rawKeywordsB)

        val keywordIntersection = keywordsA.intersect(keywordsB)

        if (keywordIntersection.isNotEmpty() && keywordsA.isNotEmpty() && keywordsB.isNotEmpty()) {
            val jaccardSimilarity = keywordIntersection.size.toFloat() /
                (keywordsA.union(keywordsB).size)
            score += (jaccardSimilarity * 0.18f).coerceAtMost(0.18f)
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // 4. KEY PHRASES (max 0.08)
        // ═══════════════════════════════════════════════════════════════════════════
        val phrasesA = profileA.keyPhrases.map { it.lowercase() }.toSet()
        val phrasesB = profileB.keyPhrases.map { it.lowercase() }.toSet()

        // Check if any phrase from A appears in B's phrases or vice versa
        val phraseMatches = phrasesA.count { phrase ->
            phrasesB.any { it.contains(phrase) || phrase.contains(it) }
        }
        score += (phraseMatches * 0.04f).coerceAtMost(0.08f)

        // ═══════════════════════════════════════════════════════════════════════════
        // 5. PERSON ENTITIES (max 0.12)
        // ═══════════════════════════════════════════════════════════════════════════
        val personsA = profileA.entities.filter { it.type == EntityType.PERSON }
            .map { it.text.lowercase() }
        val personsB = profileB.entities.filter { it.type == EntityType.PERSON }
            .map { it.text.lowercase() }

        val personMatches = personsA.count { pa ->
            personsB.any { pb -> pa.contains(pb) || pb.contains(pa) }
        }
        score += (personMatches * 0.06f).coerceAtMost(0.12f)

        // ═══════════════════════════════════════════════════════════════════════════
        // 6. ORG ENTITIES (max 0.08)
        // ═══════════════════════════════════════════════════════════════════════════
        val orgsA = profileA.entities.filter { it.type == EntityType.ORG }
            .map { it.text.lowercase() }
        val orgsB = profileB.entities.filter { it.type == EntityType.ORG }
            .map { it.text.lowercase() }

        val orgMatches = orgsA.count { oa ->
            orgsB.any { ob -> oa.contains(ob) || ob.contains(oa) }
        }
        score += (orgMatches * 0.04f).coerceAtMost(0.08f)

        // ═══════════════════════════════════════════════════════════════════════════
        // 7. LOCATION ENTITIES (max 0.07)
        // ═══════════════════════════════════════════════════════════════════════════
        val locsA = profileA.entities.filter { it.type == EntityType.LOCATION }
            .map { it.text.lowercase() }
        val locsB = profileB.entities.filter { it.type == EntityType.LOCATION }
            .map { it.text.lowercase() }

        val locMatches = locsA.count { la ->
            locsB.any { lb -> la.contains(lb) || lb.contains(la) }
        }
        score += (locMatches * 0.035f).coerceAtMost(0.07f)

        // ═══════════════════════════════════════════════════════════════════════════
        // 8. ACTION ITEMS (max 0.10)
        // ═══════════════════════════════════════════════════════════════════════════
        val actionsA = profileA.actionItems
        val textB = (profileB.keywords.map { it.term } + profileB.keyPhrases + listOf(profileB.synopsis ?: ""))
            .joinToString(" ").lowercase()

        var actionScore = 0f
        for (action in actionsA) {
            val verbMatch = textB.contains(action.verb.lowercase())
            val objectMatch = action.objectText.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .any { textB.contains(it.lowercase()) }

            if (verbMatch && objectMatch) actionScore += 0.05f
            else if (verbMatch || objectMatch) actionScore += 0.025f
        }
        score += actionScore.coerceAtMost(0.10f)

        // ═══════════════════════════════════════════════════════════════════════════
        // 9. TEMPORAL SIGNALS (max 0.05)
        // ═══════════════════════════════════════════════════════════════════════════
        val temporalA = profileA.temporalSignals
        val temporalB = profileB.temporalSignals

        if (temporalA != null && temporalB != null) {
            val weekdayOverlap = temporalA.weekdays.intersect(temporalB.weekdays.toSet())
            val relativeDateOverlap = temporalA.relativeDates.intersect(temporalB.relativeDates.toSet())

            if (weekdayOverlap.isNotEmpty() || relativeDateOverlap.isNotEmpty()) {
                score += 0.05f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Check if two domains are siblings (related but not identical).
     * Delegates to Domain.areSiblings() which has the canonical sibling relationships.
     */
    private fun areSiblingDomains(a: Domain, b: Domain): Boolean {
        return Domain.areSiblings(a, b)
    }

    /**
     * Calculate synopsis similarity between note synopsis and event title.
     * Uses Jaccard similarity of words, capped at 0.15.
     *
     * @param noteProfile The note's semantic profile
     * @param eventTitle The event title to compare against
     * @return Affinity score 0.0 to 0.15
     */
    fun calculateSynopsisAffinity(noteProfile: SemanticProfile, eventTitle: String): Float {
        val synopsis = noteProfile.synopsis ?: return 0f
        if (synopsis.length < 10) return 0f

        val synopsisWords = synopsis.lowercase()
            .split(Regex("[\\s,.;:!?]+"))
            .filter { it.length >= 3 }
            .toSet()

        val eventWords = eventTitle.lowercase()
            .split(Regex("[\\s,.;:!?]+"))
            .filter { it.length >= 3 }
            .toSet()

        if (synopsisWords.isEmpty() || eventWords.isEmpty()) return 0f

        val overlap = synopsisWords.intersect(eventWords)
        val jaccardSimilarity = overlap.size.toFloat() / synopsisWords.union(eventWords).size

        // Cap at 0.15 (synopsis is supplementary signal)
        return (jaccardSimilarity * 0.3f).coerceAtMost(0.15f)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SIMILAR CONTENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find similar content based on domain/intent/keywords.
     */
    suspend fun findSimilarContent(
        profile: SemanticProfile,
        limit: Int = 10,
        excludeSourceId: String? = null
    ): List<SemanticProfile> {
        val domainMatches = profile.domain?.let {
            repository.getOcrTextsByDomain(it.name, limit * 3)
        } ?: emptyList()

        return domainMatches
            .filter { it.sourceId != excludeSourceId }
            .map { parseToProfile(it) }
            .sortedByDescending { calculateSimilarity(profile, it) }
            .take(limit)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTION ITEMS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get pending action items across all content.
     */
    suspend fun getPendingActionItems(
        limit: Int = 50,
        sinceDaysAgo: Int = 30
    ): List<ActionItemWithSource> {
        val cutoffDate = LocalDate.now().minusDays(sinceDaysAgo.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        return repository.getOcrTextsWithActionItems(cutoffDate, limit * 2)
            .flatMap { ocrText ->
                parseActionItems(ocrText.actionItemsJson).map { actionItem ->
                    ActionItemWithSource(
                        actionItem = actionItem,
                        sourceType = OcrSource.fromString(ocrText.sourceType) ?: OcrSource.MEMO,
                        sourceId = ocrText.sourceId,
                        sourceDate = ocrText.sourceDate
                    )
                }
            }
            .sortedByDescending { it.actionItem.confidence }
            .take(limit)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENHANCED SEARCH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Search with domain/intent filtering.
     */
    suspend fun searchWithFilters(
        query: String,
        domains: List<Domain>? = null,
        sourceTypes: List<OcrSource>? = null,
        limit: Int = 50
    ): List<SemanticSearchResult> {
        if (query.length < 2) return emptyList()

        val ftsResults = repository.searchText(query, limit * 2)

        return ftsResults.mapNotNull { result ->
            val ocrText = repository.getOcrText(
                OcrSource.fromString(result.sourceType) ?: return@mapNotNull null,
                result.sourceId,
                result.pageId
            ) ?: return@mapNotNull null

            val domainOk = domains?.let { d ->
                ocrText.domain?.let { Domain.fromString(it) }?.let { it in d } ?: false
            } ?: true

            val sourceOk = sourceTypes?.let { s ->
                OcrSource.fromString(result.sourceType)?.let { it in s } ?: false
            } ?: true

            if (!domainOk || !sourceOk) return@mapNotNull null

            val profile = parseToProfile(ocrText)
            SemanticSearchResult(
                ocrResult = result,
                semanticScore = calculateSearchRelevance(profile, query),
                matchedDomains = profile.domain?.let { listOf(it) } ?: emptyList(),
                matchedIntents = profile.intents.map { it.intent },
                combinedScore = result.score + calculateSearchRelevance(profile, query)
            )
        }.sortedByDescending { it.combinedScore }.take(limit)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOMAIN DISTRIBUTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get domain distribution for insights.
     */
    suspend fun getDomainDistribution(
        startDate: String? = null,
        endDate: String? = null
    ): DomainDistribution {
        val stats = repository.getDomainStats(startDate, endDate)

        val byDomain = stats.mapNotNull { stat ->
            Domain.fromString(stat.domain)?.let { it to stat.count }
        }.toMap()

        return DomainDistribution(
            totalPages = stats.sumOf { it.count },
            byDomain = byDomain,
            bySourceType = emptyMap(),
            topKeywords = emptyList(),
            periodStart = startDate,
            periodEnd = endDate
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CACHE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    fun invalidateProfile(sourceType: OcrSource, sourceId: String, pageId: String) {
        profileCache.remove("${sourceType.name}_${sourceId}_${pageId}")
    }

    fun invalidateDateContext(date: String) {
        dateContextCache.remove(date)
    }

    fun clearAllCaches() {
        profileCache.clear()
        dateContextCache.clear()
        eventProfileCache.clear()
        Log.d(TAG, "All caches cleared")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EVENT INDEXING (Phase 2a - SemanticGenius Transformation)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find a duplicate of an already-indexed event.
     *
     * Duplicate criteria:
     * - Same title (case-insensitive)
     * - Same date
     * - Start time within 5 minutes
     *
     * This prevents double-indexing when both LocalEvent and synced CalendarEvent
     * versions of the same event exist.
     *
     * @param event The event to check
     * @return The duplicate's semantic profile if found, null otherwise
     */
    suspend fun findDuplicateEventProfile(event: IndexableEvent): SemanticProfile? {
        // First check if THIS event is already indexed
        getEventProfile(event.id)?.let { return it }

        // Check for other events with same title/time
        val date = formatDateFromEpoch(event.startTime)
        val existingProfiles = repository.getOcrTextsBySourceDate(date)

        val duplicate = existingProfiles.find { existing ->
            val existingSourceType = OcrSource.fromString(existing.sourceType)
            existingSourceType?.isEventSource() == true &&
                existing.sourceId != event.id &&
                existing.sourceTitle?.lowercase() == event.title.lowercase() &&
                existing.sourceTimestamp != null &&
                abs(existing.sourceTimestamp - event.startTime) < DUPLICATE_TIME_WINDOW_MS
        }

        return duplicate?.let { parseToProfile(it) }
    }

    /**
     * Index an event for semantic search and Smart Link matching.
     *
     * Process:
     * 1. Check for duplicates (skip if duplicate exists, return existing profile)
     * 2. Run full 12-step extraction via extractSemantics()
     * 3. Persist to unified_ocr_text
     * 4. Cache the result
     *
     * IMPORTANT: Events do NOT record to UserPriors (see Section 4.14).
     * This prevents external calendar content from polluting user behavior models.
     *
     * @param event The event to index
     * @return The semantic profile for the event (existing or newly created)
     */
    suspend fun indexEvent(event: IndexableEvent): SemanticProfile = withContext(Dispatchers.IO) {
        // Check for duplicates first - returns existing profile if found
        findDuplicateEventProfile(event)?.let { existingProfile ->
            Log.d(TAG, "Skipping duplicate event: ${event.title} (using existing profile)")
            return@withContext existingProfile
        }

        // Combine title and description for extraction
        val fullText = buildString {
            append(event.title)
            event.description?.let { desc ->
                if (desc.isNotBlank()) {
                    append("\n\n")
                    append(desc)
                }
            }
        }

        // Use short-text path for brief event titles, full extraction for longer content
        val semanticBundle = if (fullText.length < MIN_TEXT_LENGTH) {
            Log.d(TAG, "Using short-text extraction for event: ${event.title} (${fullText.length} chars)")
            extractSemanticsShortText(fullText, event.getOcrSourceType())
        } else {
            extractSemantics(
                text = fullText,
                sourceType = event.getOcrSourceType(),
                sourceTitle = event.title,
                sourceCreatedAt = event.startTime
            )
        }

        // Debug logging
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Event Extraction for '${event.title}':\n" +
                "  domain=${semanticBundle.domain?.name} " +
                "keywords=${semanticBundle.keywords.size} " +
                "entities=${semanticBundle.entities.size} " +
                "actions=${semanticBundle.actionItems.size} " +
                "synopsis=${semanticBundle.synopsis.take(50).ifEmpty { "(empty)" }}")
        }

        val eventDate = formatDateFromEpoch(event.startTime)
        val now = System.currentTimeMillis()

        // Build UnifiedOcrText from SemanticBundle
        val ocrText = UnifiedOcrText(
            id = UnifiedOcrText.generateId(event.getOcrSourceType(), event.id, EventProfileContract.PAGE_ID_SENTINEL),
            sourceType = event.getOcrSourceType().name,
            sourceId = event.id,
            pageId = EventProfileContract.PAGE_ID_SENTINEL,
            text = fullText,
            textNormalized = fullText.lowercase(),
            cleanText = semanticBundle.synopsis.ifEmpty { fullText.take(500) },
            language = "en",
            wordCount = fullText.split(Regex("\\s+")).size,
            confidence = semanticBundle.domainConfidence,
            domain = semanticBundle.domain?.name,
            domainConfidence = semanticBundle.domainConfidence,
            intentsJson = json.encodeToString(semanticBundle.intents),
            subdomainHint = semanticBundle.subdomainHint,
            docStyle = semanticBundle.docStyle.name,
            entitiesJson = json.encodeToString(semanticBundle.entities),
            keywordsJson = json.encodeToString(semanticBundle.keywords),
            keyPhrasesJson = json.encodeToString(semanticBundle.keyPhrases),
            keySentencesJson = json.encodeToString(semanticBundle.keySentences),
            actionItemsJson = json.encodeToString(semanticBundle.actionItems),
            temporalSignalsJson = json.encodeToString(semanticBundle.temporalSignals),
            topicsJson = json.encodeToString(semanticBundle.topics),
            synopsis = semanticBundle.synopsis,
            sourceTitle = event.title,
            sourceDate = eventDate,
            sourceTimestamp = event.startTime,
            updatedAt = now
        )

        repository.upsertOcrText(ocrText)
        Log.d(TAG, "Indexed event: ${event.title} → ${semanticBundle.domain?.name ?: "MISC"}")

        // Parse to profile and cache
        val profile = parseToProfile(ocrText)
        eventProfileCache[event.id] = profile

        profile
    }

    /**
     * Convenience overload to index a CalendarEvent (synced from Google/Outlook).
     */
    suspend fun indexEvent(event: CalendarEvent): SemanticProfile =
        indexEvent(event.toIndexable())

    /**
     * Convenience overload to index a LocalEvent (created in-app).
     */
    suspend fun indexEvent(event: LocalEvent): SemanticProfile =
        indexEvent(event.toIndexable())

    /**
     * Get event profile from cache or database.
     * Does NOT create a profile if not found.
     *
     * @param eventId The event's unique ID
     * @return The semantic profile, or null if not indexed
     */
    suspend fun getEventProfile(eventId: String): SemanticProfile? {
        // Check cache first
        eventProfileCache[eventId]?.let { return it }

        // Try CALENDAR_EVENT first, then LOCAL_EVENT
        val ocrText = repository.getOcrText(OcrSource.CALENDAR_EVENT, eventId, EventProfileContract.PAGE_ID_SENTINEL)
            ?: repository.getOcrText(OcrSource.LOCAL_EVENT, eventId, EventProfileContract.PAGE_ID_SENTINEL)
            ?: return null

        val profile = parseToProfile(ocrText)
        eventProfileCache[eventId] = profile
        return profile
    }

    /**
     * Get event profile by source type (for when you know the event type).
     *
     * @param sourceType The event source type (CALENDAR_EVENT or LOCAL_EVENT)
     * @param eventId The event's unique ID
     * @return The semantic profile, or null if not indexed
     */
    suspend fun getEventProfile(sourceType: OcrSource, eventId: String): SemanticProfile? {
        require(sourceType.isEventSource()) { "sourceType must be CALENDAR_EVENT or LOCAL_EVENT" }

        // Check cache first
        eventProfileCache[eventId]?.let { return it }

        val ocrText = repository.getOcrText(sourceType, eventId, EventProfileContract.PAGE_ID_SENTINEL)
            ?: return null

        val profile = parseToProfile(ocrText)
        eventProfileCache[eventId] = profile
        return profile
    }

    /**
     * Invalidate event profile cache entry.
     * Call this when an event is updated or deleted.
     *
     * @param eventId The event's unique ID
     */
    fun invalidateEventProfile(eventId: String) {
        eventProfileCache.remove(eventId)
    }

    /**
     * Clear all event profile cache entries.
     * Call this during calendar sync to ensure fresh data.
     */
    fun clearEventProfileCache() {
        eventProfileCache.clear()
        Log.d(TAG, "Event profile cache cleared")
    }

    /**
     * Get or create event profile on demand.
     *
     * Flow:
     * 1. Check cache
     * 2. Check database
     * 3. If not found, classify on-demand and persist
     *
     * Used for incremental indexing during Smart Link when an event
     * hasn't been indexed yet.
     *
     * @param event The CalendarEvent to get or create profile for
     * @return The semantic profile (always non-null)
     */
    suspend fun getOrCreateEventProfile(event: CalendarEvent): SemanticProfile {
        // Try cache first
        eventProfileCache[event.id]?.let { return it }

        // Try database
        repository.getOcrText(OcrSource.CALENDAR_EVENT, event.id, EventProfileContract.PAGE_ID_SENTINEL)?.let { ocrText ->
            val profile = parseToProfile(ocrText)
            eventProfileCache[event.id] = profile
            return profile
        }

        // On-demand classification - index and return
        return indexEvent(event)
    }

    /**
     * Get or create event profile for LocalEvent.
     */
    suspend fun getOrCreateEventProfile(event: LocalEvent): SemanticProfile {
        // Try cache first
        eventProfileCache[event.id]?.let { return it }

        // Try database
        repository.getOcrText(OcrSource.LOCAL_EVENT, event.id, EventProfileContract.PAGE_ID_SENTINEL)?.let { ocrText ->
            val profile = parseToProfile(ocrText)
            eventProfileCache[event.id] = profile
            return profile
        }

        // On-demand classification - index and return
        return indexEvent(event)
    }

    /**
     * Get event profile IF IT EXISTS - no on-demand creation.
     * Returns null if the profile hasn't been indexed yet.
     *
     * Use this for bulk operations where on-demand indexing would be too slow.
     * Events without profiles will use fallback scoring.
     *
     * @param eventId The event's unique ID
     * @return The semantic profile, or null if not indexed
     */
    suspend fun getEventProfileIfExists(eventId: String): SemanticProfile? {
        // Try cache first
        eventProfileCache[eventId]?.let { return it }

        // Try database (CalendarEvent is most common)
        repository.getOcrText(OcrSource.CALENDAR_EVENT, eventId, EventProfileContract.PAGE_ID_SENTINEL)?.let { ocrText ->
            val profile = parseToProfile(ocrText)
            eventProfileCache[eventId] = profile
            return profile
        }

        // Not indexed - return null (don't create on-demand)
        return null
    }

    /**
     * Delete event profile from database and cache.
     * Call this when an event is deleted.
     *
     * @param sourceType The event source type
     * @param eventId The event's unique ID
     */
    suspend fun deleteEventProfile(sourceType: OcrSource, eventId: String) {
        require(sourceType.isEventSource()) { "sourceType must be CALENDAR_EVENT or LOCAL_EVENT" }

        val id = UnifiedOcrText.generateId(sourceType, eventId, EventProfileContract.PAGE_ID_SENTINEL)
        repository.deleteOcrText(id)
        eventProfileCache.remove(eventId)
        Log.d(TAG, "Deleted event profile: $eventId")
    }

    /**
     * Handle LocalEvent → CalendarEvent promotion.
     * When a local event is synced to Google/Outlook, delete the local profile.
     * The CalendarEvent will be indexed during normal sync flow.
     *
     * @param localEventId The LocalEvent ID being promoted
     * @param calendarEventId The new CalendarEvent ID
     */
    suspend fun handleEventPromotion(localEventId: String, calendarEventId: String) {
        deleteEventProfile(OcrSource.LOCAL_EVENT, localEventId)
        invalidateEventProfile(localEventId)
        Log.d(TAG, "Promoted LocalEvent $localEventId → CalendarEvent $calendarEventId")
    }

    /**
     * Check which event IDs are already indexed (have semantic profiles).
     * Used for filtering before enqueueing to worker - avoids re-indexing.
     *
     * @param eventIds List of event IDs to check
     * @return Set of event IDs that are already indexed
     */
    suspend fun getAlreadyIndexedEventIds(eventIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (eventIds.isEmpty()) return@withContext emptySet()

        val indexed = mutableSetOf<String>()
        eventIds.forEach { id ->
            // Check cache first
            if (eventProfileCache.containsKey(id)) {
                indexed.add(id)
            } else {
                // Check DB for CalendarEvent profiles (most common case during sync)
                val hasProfile = repository.getOcrText(
                    OcrSource.CALENDAR_EVENT, id, EventProfileContract.PAGE_ID_SENTINEL
                ) != null
                if (hasProfile) {
                    indexed.add(id)
                }
            }
        }
        indexed
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENTITY CO-OCCURRENCE GRAPH (Phase 5 - SemanticGenius Transformation)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Record entity co-occurrences from a semantic profile.
     * Called after each classification is persisted.
     *
     * Process:
     * 1. Extract entities of type PERSON, ORG, LOCATION
     * 2. Normalize to lowercase and deduplicate
     * 3. Generate all pairs (O(n²) but n is typically <10)
     * 4. Alphabetically order each pair (entity1 < entity2) to prevent duplicates
     * 5. Upsert each pair to increment count and update timestamp
     *
     * Example: Profile with entities ["John", "Acme Corp", "NYC"]
     * Generates pairs: ("acme corp", "john"), ("acme corp", "nyc"), ("john", "nyc")
     *
     * @param profile The semantic profile with entities to record
     */
    suspend fun recordCooccurrences(profile: SemanticProfile) {
        // Filter to entity types that indicate meaningful relationships
        val cooccurrenceEntityTypes = setOf(EntityType.PERSON, EntityType.ORG, EntityType.LOCATION)

        val entities = profile.entities
            .filter { it.type in cooccurrenceEntityTypes }
            .map { it.text.lowercase().trim() }
            .distinct()

        // Need at least 2 entities to form a pair
        if (entities.size < 2) return

        val now = System.currentTimeMillis()

        // Generate all pairs (O(n²) but n is small, typically <10)
        for (i in entities.indices) {
            for (j in i + 1 until entities.size) {
                // Alphabetically order to ensure consistent key
                val (e1, e2) = if (entities[i] < entities[j]) {
                    entities[i] to entities[j]
                } else {
                    entities[j] to entities[i]
                }

                val existing = cooccurrenceDao.getCooccurrence(e1, e2)
                val updated = EntityCooccurrence(
                    entity1 = e1,
                    entity2 = e2,
                    cooccurrenceCount = (existing?.cooccurrenceCount ?: 0) + 1,
                    lastSeenAt = now,
                    sourceTypes = updateSourceTypes(existing?.sourceTypes, profile.sourceType)
                )
                cooccurrenceDao.upsertCooccurrence(updated)
            }
        }

        Log.d(TAG, "Recorded co-occurrences for ${entities.size} entities (${entities.size * (entities.size - 1) / 2} pairs)")
    }

    /**
     * Calculate co-occurrence boost between two profiles.
     * Returns boost value 0.0 to COOCCURRENCE_WEIGHT (0.15).
     *
     * Algorithm:
     * 1. Extract entity texts from both profiles
     * 2. For each cross-profile entity pair (ea from A, eb from B):
     *    - Skip if ea == eb (direct match handled elsewhere)
     *    - Look up co-occurrence count
     *    - If count >= 2, calculate logarithmic boost factor
     * 3. Average all boost factors
     * 4. Scale by COOCCURRENCE_WEIGHT and cap at maximum
     *
     * Logarithmic scaling formula:
     * factor = 1 - (1 / (1 + ln(count)))
     *
     * This gives:
     * - 2 occurrences → ~0.50 factor
     * - 4 occurrences → ~0.67 factor
     * - 8 occurrences → ~0.75 factor
     * - 16 occurrences → ~0.81 factor
     *
     * @param profileA First semantic profile (typically note)
     * @param profileB Second semantic profile (typically event)
     * @return Boost value between 0.0 and COOCCURRENCE_WEIGHT
     */
    suspend fun calculateCooccurrenceBoost(
        profileA: SemanticProfile,
        profileB: SemanticProfile
    ): Float {
        val entitiesA = profileA.entities.map { it.text.lowercase() }.toSet()
        val entitiesB = profileB.entities.map { it.text.lowercase() }.toSet()

        if (entitiesA.isEmpty() || entitiesB.isEmpty()) return 0f

        var totalBoost = 0f
        var pairCount = 0

        for (ea in entitiesA) {
            for (eb in entitiesB) {
                if (ea == eb) continue  // Direct match handled elsewhere (entity overlap in affinity)

                val cooccurrence = cooccurrenceDao.getCooccurrence(ea, eb)
                if (cooccurrence != null && cooccurrence.cooccurrenceCount >= 2) {
                    // Logarithmic scaling: 2 occurrences = 0.5x, 4 = 0.67x, 8 = 0.75x, etc.
                    val factor = 1 - (1 / (1 + ln(cooccurrence.cooccurrenceCount.toDouble())))
                    totalBoost += factor.toFloat()
                    pairCount++
                }
            }
        }

        return if (pairCount > 0) {
            ((totalBoost / pairCount) * COOCCURRENCE_WEIGHT).coerceAtMost(COOCCURRENCE_WEIGHT)
        } else {
            0f
        }
    }

    /**
     * Update source types JSON array with new source type.
     * Creates new array or adds to existing if not already present.
     *
     * @param existingJson Existing JSON array string or null
     * @param newSourceType New source type to add
     * @return Updated JSON array string
     */
    private fun updateSourceTypes(existingJson: String?, newSourceType: OcrSource): String {
        val existing = if (existingJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<String>>(existingJson)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse sourceTypes: ${e.message}")
                emptyList()
            }
        }

        val updated = (existing + newSourceType.name).distinct()
        return json.encodeToString(updated)
    }

    /**
     * Prune old co-occurrence records to prevent unbounded table growth.
     * Removes records not seen in the last N days.
     *
     * @param daysOld Remove records older than this many days
     * @return Number of records deleted
     */
    suspend fun pruneOldCooccurrences(daysOld: Int = 90): Int {
        val cutoff = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        val deleted = cooccurrenceDao.pruneOldCooccurrences(cutoff)
        Log.d(TAG, "Pruned $deleted old co-occurrence records (older than $daysOld days)")
        return deleted
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MULTI-NOTE CONTEXT (Phase 8 - SemanticGenius Transformation)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculate cohesion boost when multiple notes share keywords.
     * If 3+ notes all mention "budget review", increase confidence
     * that they all relate to the "Q4 Budget Review" event.
     *
     * Algorithm:
     * 1. Count keyword frequency across ALL note profiles
     * 2. Filter to keywords appearing in 3+ notes
     * 3. Intersect with event keywords
     * 4. Return tiered boost based on overlap size
     *
     * @param profiles List of semantic profiles for notes on the same day
     * @param eventProfile The semantic profile of the event to match against
     * @return Cohesion boost value:
     *         - 0.20f if overlap >= 3 (strong cohesion)
     *         - 0.12f if overlap >= 2 (moderate cohesion)
     *         - 0.06f if overlap >= 1 (weak cohesion)
     *         - 0f otherwise
     */
    fun calculateDateCohesionBoost(
        profiles: List<SemanticProfile>,
        eventProfile: SemanticProfile
    ): Float {
        // Need at least 2 notes to calculate cohesion
        if (profiles.size < 2) return 0f

        // Find keywords shared across multiple notes
        // Count each keyword once per note (not multiple times within same note)
        val keywordCounts = mutableMapOf<String, Int>()
        for (profile in profiles) {
            // Use toSet() to deduplicate keywords within a single note
            profile.keywords.map { it.term.lowercase() }.toSet().forEach { keyword ->
                keywordCounts[keyword] = (keywordCounts[keyword] ?: 0) + 1
            }
        }

        // Keywords appearing in 3+ notes (high-cohesion keywords)
        val sharedKeywords = keywordCounts.filter { it.value >= 3 }.keys

        if (sharedKeywords.isEmpty()) return 0f

        // Check if event contains any of these shared keywords
        val eventKeywords = eventProfile.keywords.map { it.term.lowercase() }.toSet()
        val overlap = sharedKeywords.intersect(eventKeywords)

        val boost = when {
            overlap.size >= 3 -> 0.20f  // Strong cohesion
            overlap.size >= 2 -> 0.12f  // Moderate cohesion
            overlap.size >= 1 -> 0.06f  // Weak cohesion
            else -> 0f
        }

        if (boost > 0f) {
            Log.d(TAG, "Date cohesion boost: ${overlap.size} shared keywords, boost=$boost, keywords=${overlap.take(5)}")
        }

        return boost
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun parseToProfile(ocrText: UnifiedOcrText): SemanticProfile {
        return SemanticProfile(
            sourceType = OcrSource.fromString(ocrText.sourceType) ?: OcrSource.MEMO,
            sourceId = ocrText.sourceId,
            pageId = ocrText.pageId,
            domain = ocrText.domain?.let { Domain.fromString(it) },
            domainConfidence = ocrText.domainConfidence,  // Not nullable, defaults to 0f
            intents = parseIntents(ocrText.intentsJson),
            subdomainHint = ocrText.subdomainHint,
            docStyle = ocrText.docStyle?.let { DocumentStyle.fromString(it) },
            keywords = parseKeywords(ocrText.keywordsJson),
            keyPhrases = parseStringList(ocrText.keyPhrasesJson),
            keySentences = parseStringList(ocrText.keySentencesJson),
            entities = parseEntities(ocrText.entitiesJson),
            actionItems = parseActionItems(ocrText.actionItemsJson),
            temporalSignals = parseTemporalSignals(ocrText.temporalSignalsJson),
            synopsis = ocrText.synopsis,
            sourceDate = ocrText.sourceDate,
            sourceTitle = ocrText.sourceTitle,
            wordCount = ocrText.wordCount,
            confidence = ocrText.confidence
        )
    }

    private fun parseKeywords(jsonString: String?): List<KeywordEntry> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<KeywordEntry>>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse keywords: ${e.message}")
            emptyList()
        }
    }

    private fun parseIntents(jsonString: String?): List<IntentEntry> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<IntentEntry>>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse intents: ${e.message}")
            emptyList()
        }
    }

    private fun parseEntities(jsonString: String?): List<ExtractedEntity> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ExtractedEntity>>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse entities: ${e.message}")
            emptyList()
        }
    }

    private fun parseActionItems(jsonString: String?): List<ActionItem> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ActionItem>>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse action items: ${e.message}")
            emptyList()
        }
    }

    private fun parseTemporalSignals(jsonString: String?): TemporalSignals? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            json.decodeFromString<TemporalSignals>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse temporal signals: ${e.message}")
            null
        }
    }

    private fun parseStringList(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun aggregateToDateContext(
        date: String,
        allTexts: List<UnifiedOcrText>,
        memoCount: Int,
        taskCount: Int,
        booxNoteCount: Int
    ): DateSemanticContext {
        // Aggregate domains
        val domainCounts = mutableMapOf<Domain, MutableList<Float>>()
        allTexts.forEach { text ->
            text.domain?.let { Domain.fromString(it) }?.let { domain ->
                domainCounts.getOrPut(domain) { mutableListOf() }.add(text.domainConfidence)
            }
        }
        val dominantDomains = domainCounts.map { (domain, confidences) ->
            DomainScore(domain, confidences.size, confidences.average().toFloat())
        }.sortedByDescending { it.count }.take(3)

        // Aggregate intents
        val intentCounts = mutableMapOf<Intent, MutableList<Float>>()
        allTexts.forEach { text ->
            parseIntents(text.intentsJson).forEach { entry ->
                intentCounts.getOrPut(entry.intent) { mutableListOf() }.add(entry.confidence)
            }
        }
        val dominantIntents = intentCounts.map { (intent, confidences) ->
            com.boox.day.ocr.semantic.model.IntentScore(intent, confidences.size, confidences.average().toFloat())
        }.sortedByDescending { it.count }.take(5)

        // Merge keywords (keep highest score per term)
        val allKeywords = allTexts
            .flatMap { parseKeywords(it.keywordsJson) }
            .groupBy { it.term.lowercase() }
            .mapNotNull { (_, keywords) -> keywords.maxByOrNull { it.score } }
            .sortedByDescending { it.score }
            .take(20)

        // Merge entities (distinct by type+text)
        val allEntities = allTexts
            .flatMap { parseEntities(it.entitiesJson) }
            .distinctBy { "${it.type}_${it.text.lowercase()}" }

        // Aggregate action items
        val pendingActionItems = allTexts.flatMap { text ->
            parseActionItems(text.actionItemsJson).map { actionItem ->
                ActionItemWithSource(
                    actionItem = actionItem,
                    sourceType = OcrSource.fromString(text.sourceType) ?: OcrSource.MEMO,
                    sourceId = text.sourceId,
                    sourceDate = text.sourceDate
                )
            }
        }

        val withSemanticData = allTexts.count { it.domain != null || !it.keywordsJson.isNullOrBlank() }
        val coveragePercent = if (allTexts.isNotEmpty()) withSemanticData.toFloat() / allTexts.size else 0f

        return DateSemanticContext(
            date = date,
            dominantDomains = dominantDomains,
            dominantIntents = dominantIntents,
            allKeywords = allKeywords,
            allEntities = allEntities,
            pendingActionItems = pendingActionItems,
            memoCount = memoCount,
            taskCount = taskCount,
            booxNoteCount = booxNoteCount,
            coveragePercent = coveragePercent
        )
    }

    /**
     * Calculate semantic match score between a note profile and event tokens.
     * Enhanced scoring with full utilization of all semantic data.
     *
     * Score distribution (max 1.0):
     * - Keywords: 0.20 - Direct keyword overlap
     * - Key Phrases: 0.10 - Phrase presence in event text
     * - Person entities: 0.12 - Person name matching
     * - Org entities: 0.08 - Organization matching
     * - Location entities: 0.08 - Location matching
     * - Domain matching: 0.15 - Domain-to-event keyword mapping
     * - Intent matching: 0.10 - Intent-to-event type mapping
     * - Action items: 0.12 - Action verb/object matching
     * - Temporal signals: 0.05 - Weekday/date matching
     */
    private fun calculateMatchScore(
        profile: SemanticProfile,
        tokens: EventSemanticTokens
    ): Float {
        var score = 0f

        // 1. Keyword matching (max 0.20) - with concept bridge expansion
        val rawProfileKeywords = profile.keywords.map { it.term.lowercase() }.toSet()
        val rawTokenKeywords = tokens.keywords

        // Expand with concept bridges for semantic matching
        // e.g., note with "hungry" matches event with "lunch"
        val profileKeywords = ConceptBridgeLoader.expandKeywords(rawProfileKeywords)
        val tokenKeywords = ConceptBridgeLoader.expandKeywords(rawTokenKeywords)

        val keywordMatches = tokenKeywords.intersect(profileKeywords).size
        score += (keywordMatches * 0.05f).coerceAtMost(0.20f)

        // 2. Key Phrases matching (max 0.10)
        val keyPhraseMatches = profile.keyPhrases.count { phrase ->
            tokens.rawText.contains(phrase.lowercase())
        }
        score += (keyPhraseMatches * 0.05f).coerceAtMost(0.10f)

        // 3. Person entity matching (max 0.12)
        val profilePersons = profile.entities
            .filter { it.type == EntityType.PERSON }
            .map { it.text.lowercase() }
        val personMatches = tokens.persons.count { ep ->
            profilePersons.any { it.contains(ep) || ep.contains(it) }
        }
        score += (personMatches * 0.06f).coerceAtMost(0.12f)

        // 4. Org entity matching (max 0.08)
        val profileOrgs = profile.entities
            .filter { it.type == EntityType.ORG }
            .map { it.text.lowercase() }
        val orgMatches = tokens.orgs.count { eo ->
            profileOrgs.any { it.contains(eo) || eo.contains(it) }
        }
        score += (orgMatches * 0.04f).coerceAtMost(0.08f)

        // 5. Location entity matching (max 0.08)
        if (tokens.locations.isNotEmpty()) {
            val profileLocations = profile.entities
                .filter { it.type == EntityType.LOCATION }
                .map { it.text.lowercase() }
            val locationMatches = tokens.locations.count { el ->
                profileLocations.any { it.contains(el) || el.contains(it) }
            }
            score += (locationMatches * 0.04f).coerceAtMost(0.08f)
        }

        // 6. Domain matching (max 0.15) - uses all 23 domains
        score += calculateDomainScore(profile.domain, tokens.keywords, tokens.rawText)

        // 7. Intent matching (max 0.10) - uses relevant intents
        score += calculateIntentScore(profile.intents, tokens.keywords, tokens.rawText)

        // 8. Action item matching (max 0.12)
        score += calculateActionItemScore(profile.actionItems, tokens.rawText)

        // 9. Temporal signals matching (max 0.05)
        score += calculateTemporalScore(profile.temporalSignals, tokens.rawText)

        return score.coerceAtMost(1.0f)
    }

    /**
     * Legacy overload for backward compatibility.
     */
    private fun calculateMatchScore(
        profile: SemanticProfile,
        eventKeywords: Set<String>,
        eventPersons: Set<String>,
        eventOrgs: Set<String>
    ): Float {
        // Convert to tokens and use enhanced scoring
        val tokens = EventSemanticTokens(
            keywords = eventKeywords,
            persons = eventPersons,
            orgs = eventOrgs,
            locations = emptySet(),
            rawText = eventKeywords.joinToString(" ")
        )
        return calculateMatchScore(profile, tokens)
    }

    /**
     * Calculate domain-based score using event patterns dictionary (Phase 7).
     */
    private fun calculateDomainScore(
        domain: Domain?,
        eventKeywords: Set<String>,
        eventRawText: String
    ): Float {
        if (domain == null || domain == Domain.MISC) return 0f

        // Use JSON dictionary instead of hardcoded map (Phase 7 - Dictionary Consolidation)
        val domainKeywords = eventPatterns.getKeywordsForDomain(domain)
        if (domainKeywords.isEmpty()) return 0f

        val rawTextLower = eventRawText.lowercase()

        // Check if any domain keywords appear in event
        val matches = domainKeywords.count { keyword ->
            eventKeywords.contains(keyword) || rawTextLower.contains(keyword)
        }

        // Scale: 1 match = 0.05, 2+ matches = 0.10, 3+ = 0.15
        return when {
            matches >= 3 -> 0.15f
            matches >= 2 -> 0.10f
            matches >= 1 -> 0.05f
            else -> 0f
        }
    }

    /**
     * Calculate intent-based score using event patterns dictionary (Phase 7).
     */
    private fun calculateIntentScore(
        intents: List<IntentEntry>,
        eventKeywords: Set<String>,
        eventRawText: String
    ): Float {
        if (intents.isEmpty()) return 0f

        val rawTextLower = eventRawText.lowercase()
        var bestScore = 0f

        for (intentEntry in intents) {
            // Use JSON dictionary instead of hardcoded map (Phase 7 - Dictionary Consolidation)
            val intentKeywords = eventPatterns.getKeywordsForIntent(intentEntry.intent)
            if (intentKeywords.isEmpty()) continue

            val matches = intentKeywords.count { keyword ->
                eventKeywords.contains(keyword) || rawTextLower.contains(keyword)
            }

            // Weight by intent confidence
            val intentScore = when {
                matches >= 2 -> 0.10f * intentEntry.confidence
                matches >= 1 -> 0.05f * intentEntry.confidence
                else -> 0f
            }

            bestScore = maxOf(bestScore, intentScore)
        }

        return bestScore.coerceAtMost(0.10f)
    }

    /**
     * Calculate action item score by matching verbs/objects to event text.
     * E.g., "call John" in note matches "Call with John" event.
     */
    private fun calculateActionItemScore(
        actionItems: List<ActionItem>,
        eventRawText: String
    ): Float {
        if (actionItems.isEmpty()) return 0f

        val rawTextLower = eventRawText.lowercase()
        var matchCount = 0

        for (item in actionItems) {
            // Check if verb appears in event (call, email, review, meet, etc.)
            val verbMatches = rawTextLower.contains(item.verb.lowercase())

            // Check if object appears in event
            val objectMatches = item.objectText.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .any { word -> rawTextLower.contains(word.lowercase()) }

            // Count match if either verb+partial object or full object match
            if (verbMatches && objectMatches) {
                matchCount += 2  // Strong match
            } else if (verbMatches || objectMatches) {
                matchCount += 1  // Weak match
            }

            // Also check assignee if present
            item.assigneeCandidate?.let { assignee ->
                if (rawTextLower.contains(assignee.lowercase())) {
                    matchCount += 1
                }
            }
        }

        // Scale: 1 match = 0.04, 2 = 0.08, 3+ = 0.12
        return when {
            matchCount >= 3 -> 0.12f
            matchCount >= 2 -> 0.08f
            matchCount >= 1 -> 0.04f
            else -> 0f
        }
    }

    /**
     * Calculate temporal score by matching weekdays/dates.
     */
    private fun calculateTemporalScore(
        temporalSignals: TemporalSignals?,
        eventRawText: String
    ): Float {
        if (temporalSignals == null) return 0f

        val rawTextLower = eventRawText.lowercase()
        var matchCount = 0

        // Check weekdays (Monday, Tuesday, etc.)
        temporalSignals.weekdays.forEach { weekday ->
            if (rawTextLower.contains(weekday.lowercase())) {
                matchCount++
            }
        }

        // Check relative dates (tomorrow, next week, etc.)
        temporalSignals.relativeDates.forEach { relDate ->
            if (rawTextLower.contains(relDate.lowercase())) {
                matchCount++
            }
        }

        // Scale: 1 match = 0.03, 2+ = 0.05
        return when {
            matchCount >= 2 -> 0.05f
            matchCount >= 1 -> 0.03f
            else -> 0f
        }
    }

    private fun calculateSimilarity(a: SemanticProfile, b: SemanticProfile): Float {
        var score = 0f

        // Domain match (30%)
        if (a.domain == b.domain && a.domain != null) score += 0.3f

        // Intent overlap (30%)
        val aIntents = a.intents.map { it.intent }.toSet()
        val bIntents = b.intents.map { it.intent }.toSet()
        val intentUnion = aIntents.union(bIntents)
        if (intentUnion.isNotEmpty()) {
            score += (aIntents.intersect(bIntents).size.toFloat() / intentUnion.size) * 0.3f
        }

        // Keyword overlap (40%)
        val aKeywords = a.keywords.map { it.term.lowercase() }.toSet()
        val bKeywords = b.keywords.map { it.term.lowercase() }.toSet()
        val keywordUnion = aKeywords.union(bKeywords)
        if (keywordUnion.isNotEmpty()) {
            score += (aKeywords.intersect(bKeywords).size.toFloat() / keywordUnion.size) * 0.4f
        }

        return score
    }

    private fun calculateSearchRelevance(profile: SemanticProfile, query: String): Float {
        val queryTerms = query.lowercase().split(Regex("\\s+")).toSet()
        val keywordMatches = profile.keywords.count { kw ->
            queryTerms.any { kw.term.lowercase().contains(it) }
        }
        return (keywordMatches * 0.1f).coerceAtMost(0.5f)
    }

    private fun buildMatchReasons(
        profile: SemanticProfile,
        eventKeywords: Set<String>,
        eventPersons: Set<String>,
        eventOrgs: Set<String>
    ): List<SemanticMatchReason> {
        val reasons = mutableListOf<SemanticMatchReason>()

        val profileKeywords = profile.keywords.map { it.term.lowercase() }.toSet()
        if (eventKeywords.intersect(profileKeywords).isNotEmpty()) {
            reasons.add(SemanticMatchReason.KEYWORD_MATCH)
        }

        val profilePersons = profile.entities.filter { it.type == EntityType.PERSON }
        if (eventPersons.isNotEmpty() && profilePersons.any { person ->
            eventPersons.any { ep -> person.text.contains(ep, true) }
        }) {
            reasons.add(SemanticMatchReason.ENTITY_PERSON_MATCH)
        }

        val profileOrgs = profile.entities.filter { it.type == EntityType.ORG }
        if (eventOrgs.isNotEmpty() && profileOrgs.any { org ->
            eventOrgs.any { eo -> org.text.contains(eo, true) }
        }) {
            reasons.add(SemanticMatchReason.ENTITY_ORG_MATCH)
        }

        if (profile.domain in listOf(Domain.PROFESSIONAL, Domain.BUSINESS)) {
            reasons.add(SemanticMatchReason.DOMAIN_RELEVANCE)
        }

        if (profile.intents.any { it.intent == Intent.MEETING_NOTES }) {
            reasons.add(SemanticMatchReason.INTENT_MATCH)
        }

        return reasons
    }

    private fun extractKeywordsFromText(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[\\s,.;:!?()\\[\\]{}\"']+"))
            .filter { it.length >= 3 }
            .toSet()

    private fun extractPersonNames(title: String, description: String?): Set<String> =
        Regex("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b")
            .findAll("$title ${description ?: ""}")
            .map { it.value.lowercase() }
            .toSet()

    private fun extractOrgNames(title: String, description: String?): Set<String> =
        Regex("\\b(?:[A-Z][a-z]*\\.?\\s*)+(?:Inc|LLC|Corp|Ltd|Co|Company|Team|Group)\\b", RegexOption.IGNORE_CASE)
            .findAll("$title ${description ?: ""}")
            .map { it.value.lowercase() }
            .toSet()

    private fun generateDateRange(startDate: String, endDate: String): List<String> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
            .toList()
    }

    private fun offsetDate(date: String, days: Int): String =
        LocalDate.parse(date).plusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

    /**
     * Format epoch milliseconds to ISO date string (yyyy-MM-dd).
     * Used for event indexing to derive sourceDate from start time.
     */
    private fun formatDateFromEpoch(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    // ═══════════════════════════════════════════════════════════════════════
    // USER ACTIVITY PATTERNS (Phase 6 - SemanticGenius Transformation)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Record a link pattern when a note-event link is confirmed by the user.
     *
     * Called when:
     * - User accepts a Smart Link suggestion
     * - User manually links a note to an event
     *
     * Process:
     * 1. Calculate time delta between note creation and event start
     * 2. Determine NoteTimePreference from delta
     * 3. Upsert pattern record with updated observation count and average delay
     *
     * @param noteProfile The semantic profile of the linked note
     * @param eventStartTime The event start time in epoch milliseconds
     * @param noteCreatedAt The note creation time in epoch milliseconds
     */
    suspend fun recordLinkPattern(
        noteProfile: SemanticProfile,
        eventStartTime: Long,
        noteCreatedAt: Long
    ) {
        val domain = noteProfile.domain ?: return
        if (domain == Domain.MISC) return  // Don't track MISC domain patterns

        // Calculate time difference (note creation relative to event start)
        val deltaMs = noteCreatedAt - eventStartTime
        val preference = classifyNoteTimePreference(deltaMs, eventStartTime, noteCreatedAt)

        val existing = userActivityPatternDao.getPattern(domain.name, preference.name)
        val newObservationCount = (existing?.observationCount ?: 0) + 1

        // Calculate running average of delay
        val newAverageDelayMs = if (existing == null) {
            abs(deltaMs)
        } else {
            // Weighted average: (existing * count + new) / (count + 1)
            ((existing.averageDelayMs * existing.observationCount) + abs(deltaMs)) / newObservationCount
        }

        val pattern = UserActivityPattern(
            domain = domain.name,
            noteTimePreference = preference.name,
            observationCount = newObservationCount,
            lastObservedAt = System.currentTimeMillis(),
            averageDelayMs = newAverageDelayMs
        )

        userActivityPatternDao.upsert(pattern)
        Log.d(TAG, "Recorded link pattern: ${domain.name} → ${preference.name} (count=$newObservationCount, avgDelay=${newAverageDelayMs}ms)")
    }

    /**
     * Classify note timing relative to event into a NoteTimePreference.
     *
     * @param deltaMs Time difference (noteCreatedAt - eventStartTime)
     * @param eventStartTime Event start time
     * @param noteCreatedAt Note creation time
     * @return The appropriate NoteTimePreference
     */
    private fun classifyNoteTimePreference(
        deltaMs: Long,
        eventStartTime: Long,
        noteCreatedAt: Long
    ): NoteTimePreference {
        return when {
            // Note created before event (prep notes)
            deltaMs < -ONE_HOUR_MS -> NoteTimePreference.PREP_NOTES

            // Note created during event (within 1 hour before to event end)
            deltaMs >= -ONE_HOUR_MS && deltaMs <= ONE_HOUR_MS -> NoteTimePreference.DURING

            // Note created within 1 hour after event
            deltaMs > ONE_HOUR_MS && deltaMs <= 2 * ONE_HOUR_MS -> NoteTimePreference.IMMEDIATELY_AFTER

            // Note created same day but >2 hours after
            isSameDay(eventStartTime, noteCreatedAt) -> NoteTimePreference.SAME_DAY

            // Note created next day
            else -> NoteTimePreference.NEXT_DAY
        }
    }

    /**
     * Check if two timestamps fall on the same calendar day.
     */
    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val date1 = Instant.ofEpochMilli(time1).atZone(ZoneId.systemDefault()).toLocalDate()
        val date2 = Instant.ofEpochMilli(time2).atZone(ZoneId.systemDefault()).toLocalDate()
        return date1 == date2
    }

    /**
     * Get pattern-based boost for cross-day matching.
     *
     * Returns a boost value when the user has established patterns of:
     * - Taking notes the day after events (NEXT_DAY pattern)
     * - Taking preparation notes before events (PREP_NOTES pattern)
     *
     * This enables cross-day Smart Link matching for users who consistently
     * exhibit these note-taking behaviors.
     *
     * @param domain The event/note domain
     * @param isNextDay True if we're matching note to previous day's event
     * @param isPrepNotes True if we're matching note to future event
     * @return Boost value (0.0 to 0.15) or 0.0 if no pattern established
     */
    suspend fun getPatternBoost(
        domain: Domain,
        isNextDay: Boolean = false,
        isPrepNotes: Boolean = false
    ): Float {
        if (!isNextDay && !isPrepNotes) return 0f

        val preference = when {
            isNextDay -> NoteTimePreference.NEXT_DAY
            isPrepNotes -> NoteTimePreference.PREP_NOTES
            else -> return 0f
        }

        val pattern = userActivityPatternDao.getPattern(domain.name, preference.name)
            ?: return 0f

        // Require minimum observations before applying boost (Section 4.9.2)
        if (pattern.observationCount < PATTERN_MIN_OBSERVATIONS) {
            Log.d(TAG, "Pattern ${domain.name}/${preference.name} has ${pattern.observationCount} observations, need $PATTERN_MIN_OBSERVATIONS")
            return 0f
        }

        return when (preference) {
            NoteTimePreference.NEXT_DAY -> NEXT_DAY_PATTERN_BOOST
            NoteTimePreference.PREP_NOTES -> PREP_NOTES_PATTERN_BOOST
            else -> 0f
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEMANTIC VERSION REINDEXING (Phase 6 - Section 4.16)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if semantic dictionaries have changed and reindex all events if needed.
     *
     * Called on app startup via EventChangeObserver.initialize().
     *
     * Process:
     * 1. Load current dictionary version from code constant
     * 2. Compare with stored version in KV storage
     * 3. If different (or first run), trigger full reindex
     * 4. Update stored version after reindex completes
     */
    suspend fun checkAndReindexIfVersionChanged() = withContext(Dispatchers.IO) {
        if (!isSemanticExtractionRuntimeEnabled("semantic version reindex check")) {
            return@withContext
        }

        val storedVersionKv = kvRepository.get(KV_SEMANTIC_VERSION)
        val storedVersion = storedVersionKv?.value?.toIntOrNull() ?: 0

        Log.d(TAG, "Version check: stored=$storedVersion, current=$SEMANTIC_VERSION")

        if (storedVersion != SEMANTIC_VERSION) {
            Log.i(TAG, "Semantic version changed ($storedVersion → $SEMANTIC_VERSION), triggering reindex")
            reindexAllEvents()
            kvRepository.set(Kv(KV_SEMANTIC_VERSION, SEMANTIC_VERSION.toString()))
            Log.i(TAG, "Version updated to $SEMANTIC_VERSION")
        } else {
            Log.d(TAG, "Semantic version unchanged, no reindex needed")
        }
    }

    /**
     * Reindex all events with current classification dictionaries.
     *
     * Process:
     * 1. Clear event profile cache
     * 2. Delete all existing event profiles from database
     * 3. Reindex all CalendarEvents
     * 4. Reindex all LocalEvents
     *
     * Note: This is a heavyweight operation that should only run when
     * dictionaries change. Uses IO dispatcher for DB operations.
     */
    suspend fun reindexAllEvents() = withContext(Dispatchers.IO) {
        if (!isSemanticExtractionRuntimeEnabled("full event reindex")) {
            return@withContext
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting full event reindex...")

        // 1. Clear event profile cache
        clearEventProfileCache()

        // 2. Delete all existing event profiles (CALENDAR_EVENT and LOCAL_EVENT sources)
        repository.clearByType(OcrSource.CALENDAR_EVENT)
        repository.clearByType(OcrSource.LOCAL_EVENT)
        Log.d(TAG, "Cleared existing event profiles")

        // 3. Reindex all CalendarEvents
        val calendarEvents = database.calendarEventDao().getByDateRange("1900-01-01", "2100-12-31")
        var calendarCount = 0
        for (event in calendarEvents) {
            try {
                indexEvent(event)
                calendarCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index CalendarEvent ${event.id}: ${e.message}")
            }
        }
        Log.d(TAG, "Reindexed $calendarCount CalendarEvents")

        // 4. Reindex all LocalEvents
        val localEvents = database.localEventDao().getAllEvents()
        var localCount = 0
        for (event in localEvents) {
            try {
                indexEvent(event)
                localCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index LocalEvent ${event.id}: ${e.message}")
            }
        }
        Log.d(TAG, "Reindexed $localCount LocalEvents")

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Full event reindex complete: $calendarCount calendar + $localCount local events (${duration}ms)")
    }

    /**
     * Index events that have no semantic profile yet.
     *
     * This handles scenarios where events were inserted without going through
     * the normal repository methods (e.g., database seeding, data import).
     *
     * Unlike reindexAllEvents(), this is incremental and only processes
     * events that are truly missing from unified_ocr_text.
     */
    suspend fun indexUnindexedEvents() = withContext(Dispatchers.IO) {
        if (!isSemanticExtractionRuntimeEnabled("unindexed event scan")) {
            return@withContext
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Checking for unindexed events...")

        var localCount = 0
        var calendarCount = 0

        try {
            // Index unindexed local events (batch of 100)
            // Uses same pattern as reindexAllEvents() at line 2420/2433
            val unindexedLocal = database.localEventDao().getUnindexedEvents(100)
            for (event in unindexedLocal) {
                try {
                    indexEvent(event)
                    localCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to index local event ${event.id}: ${e.message}")
                }
            }

            // Index unindexed calendar events (batch of 100)
            val unindexedCalendar = database.calendarEventDao().getUnindexedEvents(100)
            for (event in unindexedCalendar) {
                try {
                    indexEvent(event)
                    calendarCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to index calendar event ${event.id}: ${e.message}")
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (localCount > 0 || calendarCount > 0) {
                Log.i(TAG, "Indexed $localCount local + $calendarCount calendar unindexed events (${elapsed}ms)")
            } else {
                Log.d(TAG, "No unindexed events found (${elapsed}ms)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing unindexed events: ${e.message}", e)
        }
    }
}
