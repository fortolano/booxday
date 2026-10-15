package com.boox.day.data.db

import android.content.Context
import android.util.Log
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.SemanticBundle
import com.boox.day.ocr.model.UnifiedOcrStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

internal fun buildInProgressPage(
    existingPage: UnifiedOcrPage?,
    sourceType: OcrSource,
    sourceId: String,
    pageId: String,
    sourceModifiedAt: Long,
    now: Long
): UnifiedOcrPage {
    val pageIdComposite = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
    return existingPage?.copy(
        id = pageIdComposite,
        sourceType = sourceType.name,
        sourceId = sourceId,
        pageId = pageId,
        sourceModifiedAt = sourceModifiedAt,
        ocrAt = now,
        status = UnifiedOcrStatus.IN_PROGRESS.name
    ) ?: UnifiedOcrPage(
        id = pageIdComposite,
        sourceType = sourceType.name,
        sourceId = sourceId,
        pageId = pageId,
        status = UnifiedOcrStatus.IN_PROGRESS.name,
        sourceCreatedAt = sourceModifiedAt,
        sourceModifiedAt = sourceModifiedAt,
        ocrAt = now
    )
}

internal fun buildFailedPage(
    existingPage: UnifiedOcrPage?,
    sourceType: OcrSource,
    sourceId: String,
    pageId: String,
    error: String?,
    now: Long
): UnifiedOcrPage {
    val pageIdComposite = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
    val newFailCount = (existingPage?.failCount ?: 0) + 1
    return existingPage?.copy(
        id = pageIdComposite,
        sourceType = sourceType.name,
        sourceId = sourceId,
        pageId = pageId,
        ocrAt = now,
        status = UnifiedOcrStatus.FAILED.name,
        failCount = newFailCount,
        lastError = error?.take(500)
    ) ?: UnifiedOcrPage(
        id = pageIdComposite,
        sourceType = sourceType.name,
        sourceId = sourceId,
        pageId = pageId,
        status = UnifiedOcrStatus.FAILED.name,
        sourceCreatedAt = now,
        sourceModifiedAt = now,
        ocrAt = now,
        failCount = newFailCount,
        lastError = error?.take(500)
    )
}

/**
 * Repository for unified OCR data access.
 *
 * Provides a high-level API for OCR operations across all source types.
 */
class UnifiedOcrRepository(context: Context) {

    companion object {
        private const val TAG = "UnifiedOcrRepo"

        /**
         * Stale threshold for IN_PROGRESS pages (5 minutes)
         * Pages stuck in IN_PROGRESS longer than this are considered crashed
         */
        const val STALE_THRESHOLD_MS = 5 * 60 * 1000L

        /**
         * A6: Comprehensive synonym groups for search query expansion.
         * Maps a term to its synonyms for broader FTS matching.
         * Organized by category for maintainability.
         */
        private val SYNONYM_MAP = buildMap {
            // ═══════════════════════════════════════════════════════════════
            // MEETINGS & COMMUNICATION
            // ═══════════════════════════════════════════════════════════════
            put("meeting", listOf("mtg", "sync", "standup", "huddle", "call", "discussion", "session", "conference"))
            put("mtg", listOf("meeting", "sync", "call"))
            put("sync", listOf("meeting", "standup", "huddle", "call", "catchup"))
            put("standup", listOf("meeting", "sync", "daily", "scrum"))
            put("call", listOf("meeting", "phone", "conference", "zoom", "teams"))
            put("interview", listOf("meeting", "screening", "chat"))
            put("presentation", listOf("preso", "deck", "slides", "demo", "pitch"))
            put("demo", listOf("presentation", "demonstration", "showcase", "walkthrough"))

            // ═══════════════════════════════════════════════════════════════
            // TASKS & ACTIONS
            // ═══════════════════════════════════════════════════════════════
            put("task", listOf("todo", "action", "item", "work", "job"))
            put("todo", listOf("task", "action", "item", "checklist"))
            put("action", listOf("task", "todo", "step", "item"))
            put("deadline", listOf("due", "duedate", "milestone", "target", "deliverable"))
            put("due", listOf("deadline", "target", "deliverable"))
            put("priority", listOf("urgent", "important", "critical", "asap"))
            put("urgent", listOf("priority", "asap", "critical", "immediate"))
            put("followup", listOf("follow", "reminder", "ping", "checkin"))

            // ═══════════════════════════════════════════════════════════════
            // PROJECTS & PLANNING
            // ═══════════════════════════════════════════════════════════════
            put("project", listOf("initiative", "workstream", "program", "effort"))
            put("plan", listOf("strategy", "roadmap", "outline", "agenda"))
            put("goal", listOf("objective", "target", "aim", "outcome"))
            put("milestone", listOf("deadline", "checkpoint", "deliverable", "target"))
            put("roadmap", listOf("plan", "timeline", "schedule", "strategy"))
            put("sprint", listOf("iteration", "cycle", "phase"))

            // ═══════════════════════════════════════════════════════════════
            // DOCUMENTS & WRITING
            // ═══════════════════════════════════════════════════════════════
            put("doc", listOf("document", "file", "paper", "write"))
            put("document", listOf("doc", "file", "paper", "report"))
            put("report", listOf("summary", "analysis", "document", "writeup"))
            put("summary", listOf("report", "overview", "recap", "brief"))
            put("notes", listOf("note", "minutes", "memo", "jot"))
            put("draft", listOf("version", "revision", "outline"))
            put("review", listOf("feedback", "assessment", "evaluation", "critique"))
            put("feedback", listOf("review", "input", "comments", "response"))

            // ═══════════════════════════════════════════════════════════════
            // COMMUNICATION
            // ═══════════════════════════════════════════════════════════════
            put("email", listOf("mail", "message", "msg", "correspondence"))
            put("message", listOf("msg", "email", "text", "note"))
            put("msg", listOf("message", "email", "text"))
            put("chat", listOf("message", "slack", "teams", "im"))
            put("reply", listOf("response", "answer", "respond"))

            // ═══════════════════════════════════════════════════════════════
            // FINANCE & BUSINESS
            // ═══════════════════════════════════════════════════════════════
            put("budget", listOf("finance", "money", "cost", "expense", "spend"))
            put("expense", listOf("cost", "spend", "payment", "charge"))
            put("cost", listOf("expense", "price", "budget", "spend"))
            put("invoice", listOf("bill", "receipt", "payment"))
            put("payment", listOf("pay", "bill", "invoice", "transaction"))
            put("salary", listOf("pay", "wage", "compensation", "income"))
            put("revenue", listOf("income", "sales", "earnings"))
            put("profit", listOf("earnings", "gain", "margin"))
            put("client", listOf("customer", "account", "buyer"))
            put("customer", listOf("client", "buyer", "user"))

            // ═══════════════════════════════════════════════════════════════
            // HEALTH & WELLNESS
            // ═══════════════════════════════════════════════════════════════
            put("doctor", listOf("dr", "physician", "appointment", "medical"))
            put("appointment", listOf("appt", "meeting", "visit", "session"))
            put("appt", listOf("appointment", "meeting", "visit"))
            put("gym", listOf("workout", "exercise", "fitness", "training"))
            put("workout", listOf("gym", "exercise", "training", "run"))
            put("exercise", listOf("workout", "gym", "fitness", "run"))
            put("medicine", listOf("medication", "meds", "prescription", "drug"))
            put("meds", listOf("medicine", "medication", "prescription"))

            // ═══════════════════════════════════════════════════════════════
            // EDUCATION & LEARNING
            // ═══════════════════════════════════════════════════════════════
            put("class", listOf("course", "lesson", "lecture", "session"))
            put("course", listOf("class", "training", "program", "workshop"))
            put("lecture", listOf("class", "talk", "presentation", "seminar"))
            put("study", listOf("learn", "review", "read", "research"))
            put("exam", listOf("test", "quiz", "assessment", "evaluation"))
            put("test", listOf("exam", "quiz", "assessment"))
            put("homework", listOf("assignment", "hw", "work", "task"))
            put("hw", listOf("homework", "assignment"))

            // ═══════════════════════════════════════════════════════════════
            // TIME & SCHEDULING
            // ═══════════════════════════════════════════════════════════════
            put("today", listOf("now", "current"))
            put("tomorrow", listOf("tmrw", "next"))
            put("weekly", listOf("week", "recurring"))
            put("monthly", listOf("month", "recurring"))
            put("schedule", listOf("calendar", "agenda", "plan", "timetable"))
            put("calendar", listOf("schedule", "agenda", "planner"))
            put("reminder", listOf("alert", "notification", "followup"))

            // ═══════════════════════════════════════════════════════════════
            // TRAVEL & LOGISTICS
            // ═══════════════════════════════════════════════════════════════
            put("flight", listOf("fly", "plane", "trip", "travel"))
            put("travel", listOf("trip", "flight", "journey", "vacation"))
            put("trip", listOf("travel", "journey", "visit"))
            put("hotel", listOf("accommodation", "lodging", "stay"))
            put("reservation", listOf("booking", "appointment", "reserve"))
            put("booking", listOf("reservation", "appointment"))

            // ═══════════════════════════════════════════════════════════════
            // PEOPLE & RELATIONSHIPS
            // ═══════════════════════════════════════════════════════════════
            put("team", listOf("group", "squad", "crew", "staff"))
            put("manager", listOf("boss", "lead", "supervisor", "director"))
            put("boss", listOf("manager", "lead", "supervisor"))
            put("colleague", listOf("coworker", "teammate", "peer"))
            put("family", listOf("relatives", "home"))
            put("friend", listOf("buddy", "pal"))

            // ═══════════════════════════════════════════════════════════════
            // TECH & DEVELOPMENT
            // ═══════════════════════════════════════════════════════════════
            put("bug", listOf("issue", "defect", "problem", "error"))
            put("issue", listOf("bug", "problem", "ticket", "defect"))
            put("feature", listOf("functionality", "capability", "enhancement"))
            put("deploy", listOf("release", "launch", "ship", "publish"))
            put("release", listOf("deploy", "launch", "version", "ship"))
            put("code", listOf("programming", "development", "coding"))
            put("pr", listOf("pullrequest", "review", "merge"))
            put("repo", listOf("repository", "codebase", "project"))
        }
    }

    private val dao: UnifiedOcrDao = AppDatabase.getDatabase(context).unifiedOcrDao()
    private val json = Json { ignoreUnknownKeys = true }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGE STATUS OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Register a page for OCR processing.
     */
    suspend fun registerPage(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        sourceCreatedAt: Long,
        sourceModifiedAt: Long
    ) = withContext(Dispatchers.IO) {
        val page = UnifiedOcrPage.create(
            sourceType = sourceType,
            sourceId = sourceId,
            pageId = pageId,
            sourceCreatedAt = sourceCreatedAt,
            sourceModifiedAt = sourceModifiedAt
        )
        dao.upsertOcrPage(page)
        Log.d(TAG, "Registered page: ${page.id}")
    }

    /**
     * Mark a page as in-progress (crash recovery marker).
     *
     * IMPORTANT: This creates the page record if it doesn't exist, but when the
     * row already exists it preserves fail counts, hashes, and firstIndexedAt.
     * We only want to move the row into IN_PROGRESS, not wipe its retry history.
     */
    suspend fun markInProgress(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        sourceModifiedAt: Long
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val existingPage = dao.getOcrPage(UnifiedOcrPage.generateId(sourceType, sourceId, pageId))
        val page = buildInProgressPage(
            existingPage = existingPage,
            sourceType = sourceType,
            sourceId = sourceId,
            pageId = pageId,
            sourceModifiedAt = sourceModifiedAt,
            now = now
        )
        dao.upsertOcrPage(page)
        Log.d(TAG, "Marked in-progress: ${page.id}")
    }

    /**
     * Store OCR result for a page.
     */
    suspend fun storeOcrResult(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        sourceModifiedAt: Long,
        text: String?,
        confidence: Float,
        language: String?,
        semanticBundle: SemanticBundle?,
        sourceTitle: String? = null,
        sourcePath: String? = null,
        sourceDate: String? = null
    ) = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        val now = System.currentTimeMillis()

        // Determine status
        val status = when {
            text.isNullOrBlank() -> UnifiedOcrStatus.EMPTY
            semanticBundle?.isLowSignal == true -> UnifiedOcrStatus.LOW_SIGNAL
            else -> UnifiedOcrStatus.SUCCESS
        }

        // Calculate hashes for change detection
        val textHash = text?.let { computeHash(it) }
        val semanticHash = semanticBundle?.let { computeHash(it.toJson()) }

        // Update page status and set firstIndexedAt (only if not already set)
        dao.updateStatusWithFirstIndexed(
            id = id,
            status = status.name,
            ocrAt = now,
            textHash = textHash,
            semanticHash = semanticHash,
            now = now
        )

        // Clean up stale text entries when page becomes EMPTY.
        // A page can transition from SUCCESS/LOW_SIGNAL to EMPTY when content is deleted
        // or when a ghost bitmap is detected (no strokes/annotations in DB).
        if (text.isNullOrBlank()) {
            dao.deleteTextById(id)
        }

        // Store text if not empty
        if (!text.isNullOrBlank()) {
            val normalizedText = normalizeForSearch(text)
            val cleanText = semanticBundle?.let { text } ?: text  // Use semantic-cleaned if available

            val ocrText = UnifiedOcrText(
                id = id,
                sourceType = sourceType.name,
                sourceId = sourceId,
                pageId = pageId,
                text = text,
                textNormalized = normalizedText,
                cleanText = cleanText,
                language = language,
                wordCount = countWords(text),
                confidence = confidence,
                docStyle = semanticBundle?.docStyle?.name,
                keywordsJson = semanticBundle?.keywords?.let { json.encodeToString(it) },
                keyPhrasesJson = semanticBundle?.keyPhrases?.let { json.encodeToString(it) },
                keySentencesJson = semanticBundle?.keySentences?.let { json.encodeToString(it) },
                entitiesJson = semanticBundle?.entities?.let { json.encodeToString(it) },
                actionItemsJson = semanticBundle?.actionItems?.let { json.encodeToString(it) },
                temporalSignalsJson = semanticBundle?.temporalSignals?.let { json.encodeToString(it) },
                topicsJson = semanticBundle?.topics?.let { json.encodeToString(it) },
                synopsis = semanticBundle?.synopsis,
                // Domain + Intent Classification
                domain = semanticBundle?.domain?.name,
                domainConfidence = semanticBundle?.domainConfidence ?: 0f,
                intentsJson = semanticBundle?.intents?.let { json.encodeToString(it) },
                subdomainHint = semanticBundle?.subdomainHint,
                classificationExplainJson = semanticBundle?.classificationExplainJson,
                sourceTitle = sourceTitle,
                sourcePath = sourcePath,
                sourceDate = sourceDate,
                semanticVersion = semanticBundle?.semanticVersion ?: 1,
                updatedAt = now
            )
            dao.upsertOcrText(ocrText)
        }

        Log.d(TAG, "Stored result: $id status=$status chars=${text?.length ?: 0}")
    }

    /**
     * Mark a page as failed with error message.
     *
     * IMPORTANT: This creates the page record if it doesn't exist (UPSERT behavior).
     * This ensures failures are tracked even for pages that fail before markInProgress.
     */
    suspend fun markFailed(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        error: String?
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existingPage = dao.getOcrPage(UnifiedOcrPage.generateId(sourceType, sourceId, pageId))
        val page = buildFailedPage(
            existingPage = existingPage,
            sourceType = sourceType,
            sourceId = sourceId,
            pageId = pageId,
            error = error,
            now = now
        )
        dao.upsertOcrPage(page)
        Log.d(TAG, "Marked failed: ${page.id} error=$error")
    }

    /**
     * Quarantine a page permanently after repeated failures (A-SHORT-3).
     * Quarantined pages are never retried automatically.
     */
    suspend fun quarantinePage(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        failCount: Int,
        error: String?
    ) = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        dao.updatePageStatus(id, UnifiedOcrStatus.QUARANTINED.name, failCount, error?.take(500))
        Log.w(TAG, "Quarantined page: $id after $failCount failures")
    }

    /**
     * Record a failed attempt and keep the page retryable.
     *
     * Retryable failures move the row back to PENDING while preserving failCount
     * and lastError. The pipeline is responsible for quarantining after the
     * configured threshold.
     *
     * Returns the new fail count.
     */
    suspend fun markFailedWithRetry(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        error: String?
    ): Int = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        val existingPage = dao.getOcrPage(id)
        val newFailCount = (existingPage?.failCount ?: 0) + 1

        dao.updatePageStatus(id, UnifiedOcrStatus.PENDING.name, newFailCount, error?.take(500))
        Log.d(TAG, "Recorded retryable failure (attempt $newFailCount): $id error=$error")
        newFailCount
    }

    /**
     * Recover from crash - reset stale IN_PROGRESS pages to PENDING.
     */
    suspend fun recoverFromCrash(): Int = withContext(Dispatchers.IO) {
        val staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS
        val count = dao.resetStaleInProgress(staleThreshold)
        if (count > 0) {
            Log.i(TAG, "Crash recovery: reset $count stale pages to PENDING")
        }
        count
    }

    /**
     * Reset a specific page to PENDING status.
     *
     * Used when a page can't be processed right now (e.g., bitmap not saved to disk yet)
     * but should be retried later instead of being marked as EMPTY.
     */
    suspend fun resetToPending(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String
    ) = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        dao.resetToPending(id)
        Log.d(TAG, "Reset to PENDING: $id")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get pages needing OCR processing.
     */
    suspend fun getPagesNeedingOcr(limit: Int = 50): List<UnifiedOcrPage> = withContext(Dispatchers.IO) {
        val staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS
        dao.getPagesNeedingOcr(staleThreshold, limit)
    }

    /**
     * Get pending pages for a specific source type.
     */
    suspend fun getPendingPagesByType(sourceType: OcrSource, limit: Int = 50): List<UnifiedOcrPage> = withContext(Dispatchers.IO) {
        val staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS
        dao.getPendingPagesByType(sourceType.name, staleThreshold, limit)
    }

    /**
     * Check if a page needs re-OCR (source modified after last OCR).
     */
    suspend fun needsReOcr(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        currentModifiedAt: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        dao.needsReOcr(id, currentModifiedAt)
    }

    suspend fun needsReOcr(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        currentModifiedAt: Long,
        currentSourceTitle: String?,
        currentSourceDate: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        dao.needsReOcrWithSourceMetadata(
            id = id,
            currentModifiedAt = currentModifiedAt,
            currentSourceTitle = currentSourceTitle,
            currentSourceDate = currentSourceDate
        )
    }

    /**
     * Check if a page is new (never been indexed before).
     *
     * A page is considered "new" if:
     * - It doesn't exist in the database, OR
     * - It exists but firstIndexedAt == 0 (never successfully indexed)
     *
     * New pages cost budget when indexed. Re-indexed pages (already indexed) are free.
     *
     * @return true if this is a new page (costs budget), false if re-indexing (free)
     */
    suspend fun isNewPage(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val id = UnifiedOcrPage.generateId(sourceType, sourceId, pageId)
        !dao.hasBeenIndexedBefore(id)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFICIENT CHANGE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get memo pages needing OCR using efficient single query.
     * O(1) instead of iterating through dates.
     */
    suspend fun getMemoPagesNeedingOcr(limit: Int): List<MemoPageNeedingOcr> = withContext(Dispatchers.IO) {
        dao.getMemoPagesNeedingOcr(limit)
    }

    /**
     * Get task pages needing OCR using efficient single query.
     */
    suspend fun getTaskPagesNeedingOcr(limit: Int): List<MemoPageNeedingOcr> = withContext(Dispatchers.IO) {
        dao.getTaskPagesNeedingOcr(limit)
    }

    /**
     * Get OCR text for a page.
     */
    suspend fun getOcrText(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String
    ): UnifiedOcrText? = withContext(Dispatchers.IO) {
        val id = UnifiedOcrText.generateId(sourceType, sourceId, pageId)
        dao.getOcrText(id)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Search OCR text using FTS4.
     *
     * @param query Search query (will be escaped for FTS)
     * @param limit Maximum results
     * @return List of search results
     */
    suspend fun searchText(query: String, limit: Int = 50): List<UnifiedOcrSearchResult> = withContext(Dispatchers.IO) {
        try {
            val escapedQuery = escapeForFts(query)
            dao.searchText(escapedQuery, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Search within a specific source type.
     */
    suspend fun searchByType(
        query: String,
        sourceType: OcrSource,
        limit: Int = 50
    ): List<UnifiedOcrSearchResult> = withContext(Dispatchers.IO) {
        try {
            val escapedQuery = escapeForFts(query)
            dao.searchTextByType(escapedQuery, sourceType.name, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Search by type failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * A6: Search with automatic synonym expansion.
     *
     * Expands the query to include synonyms for broader matching.
     * Example: "meeting" also searches for "sync", "standup", "huddle", "call"
     *
     * @param query Search query
     * @param limit Maximum results
     * @return List of search results including synonym matches
     */
    suspend fun searchTextWithSynonyms(
        query: String,
        limit: Int = 50
    ): List<UnifiedOcrSearchResult> = withContext(Dispatchers.IO) {
        try {
            // Start with the original query terms
            val expandedTerms = mutableSetOf<String>()

            // Split query into individual terms and expand each
            val queryTerms = query.lowercase().split(Regex("\\s+"))
            for (term in queryTerms) {
                expandedTerms.add(term)
                // Add synonyms if available
                SYNONYM_MAP[term]?.let { synonyms ->
                    expandedTerms.addAll(synonyms)
                }
            }

            // Build FTS query with OR for synonym expansion
            // Escape each term individually, then join with OR
            val ftsQuery = expandedTerms
                .filter { it.isNotBlank() }
                .joinToString(" OR ") { escapeForFts(it) }

            if (ftsQuery.isBlank()) {
                emptyList()
            } else {
                dao.searchText(ftsQuery, limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search with synonyms failed: ${e.message}")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get statistics for all source types.
     */
    suspend fun getStatistics(): UnifiedOcrStatistics = withContext(Dispatchers.IO) {
        try {
            val statsByType = dao.getStatsByType()

            // OCR-based sources
            var booxNotesScanned = 0
            var booxNotesPending = 0
            var memosScanned = 0
            var memosPending = 0
            var tasksScanned = 0
            var tasksPending = 0
            var totalFailed = 0

            // Event sources (text-only, no OCR)
            var calendarEventsIndexed = 0
            var localEventsIndexed = 0

            for (stats in statsByType) {
                // Exhaustive when on OcrSource.name values - no else branch
                when (stats.sourceType) {
                    OcrSource.BOOX_NOTE.name -> {
                        booxNotesScanned = stats.scanned
                        booxNotesPending = stats.pending
                    }
                    OcrSource.MEMO.name -> {
                        memosScanned = stats.scanned
                        memosPending = stats.pending
                    }
                    OcrSource.TASK.name -> {
                        tasksScanned = stats.scanned
                        tasksPending = stats.pending
                    }
                    OcrSource.CALENDAR_EVENT.name -> {
                        // Events are indexed immediately, no pending state
                        calendarEventsIndexed = stats.scanned
                    }
                    OcrSource.LOCAL_EVENT.name -> {
                        // Events are indexed immediately, no pending state
                        localEventsIndexed = stats.scanned
                    }
                }
                totalFailed += stats.failed
            }

            UnifiedOcrStatistics(
                booxNotesScanned = booxNotesScanned,
                booxNotesPending = booxNotesPending,
                memosScanned = memosScanned,
                memosPending = memosPending,
                tasksScanned = tasksScanned,
                tasksPending = tasksPending,
                totalFailed = totalFailed,
                calendarEventsIndexed = calendarEventsIndexed,
                localEventsIndexed = localEventsIndexed
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get statistics: ${e.message}")
            UnifiedOcrStatistics()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Clear all OCR data.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllOcrData()
        Log.i(TAG, "Cleared all OCR data")
    }

    /**
     * Clear OCR data for a specific source type.
     */
    suspend fun clearByType(sourceType: OcrSource) = withContext(Dispatchers.IO) {
        dao.clearOcrDataByType(sourceType.name)
        Log.i(TAG, "Cleared OCR data for ${sourceType.name}")
    }

    /**
     * Clear OCR data for a specific source.
     */
    suspend fun clearBySource(sourceType: OcrSource, sourceId: String) = withContext(Dispatchers.IO) {
        dao.deleteTextBySource(sourceType.name, sourceId)
        dao.deletePagesBySource(sourceType.name, sourceId)
        Log.d(TAG, "Cleared OCR data for ${sourceType.name}:$sourceId")
    }

    /**
     * Fix EMPTY pages that incorrectly have firstIndexedAt set.
     *
     * BUG FIX (Jan 2026): EMPTY pages were incorrectly setting firstIndexedAt, causing them
     * to be treated as "already indexed" when content was later added. This resets those
     * pages so they can be properly counted as NEW when content is added.
     *
     * Should be called once on app startup to fix existing data.
     *
     * @return Number of pages fixed
     */
    suspend fun fixEmptyPagesFirstIndexedAt(): Int = withContext(Dispatchers.IO) {
        val count = dao.resetFirstIndexedAtForEmptyPages()
        if (count > 0) {
            Log.i(TAG, "BUG FIX: Reset firstIndexedAt for $count EMPTY pages")
        }
        count
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC GENIUS SERVICE QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all OCR texts for a specific source date.
     */
    suspend fun getOcrTextsBySourceDate(sourceDate: String): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsBySourceDate(sourceDate)
        }

    /**
     * Get OCR texts filtered by domain.
     */
    suspend fun getOcrTextsByDomain(domain: String, limit: Int): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsByDomain(domain, limit)
        }

    /**
     * Get OCR texts with action items since cutoff date.
     */
    suspend fun getOcrTextsWithActionItems(cutoffDate: String, limit: Int): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsWithActionItems(cutoffDate, limit)
        }

    /**
     * Get OCR texts in date range.
     */
    suspend fun getOcrTextsInDateRange(startDate: String, endDate: String): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsInDateRange(startDate, endDate)
        }

    /**
     * Get domain statistics for date range.
     */
    suspend fun getDomainStats(startDate: String?, endDate: String?): List<UnifiedOcrDao.DomainStat> =
        withContext(Dispatchers.IO) {
            if (startDate != null && endDate != null) {
                dao.getDomainStatsForDateRange(startDate, endDate)
            } else {
                dao.getDomainStatsAll()
            }
        }

    /**
     * Get all OCR texts for a source (wrapper for existing DAO method).
     */
    suspend fun getOcrTextsForSource(sourceType: OcrSource, sourceId: String): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsForSource(sourceType.name, sourceId)
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART LINK BACKFILL FIX - Batch queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get distinct source IDs that have been indexed for a source type.
     * Used for backfill completion detection - compare against total notes in BooxNoteIndex.
     */
    suspend fun getIndexedSourceIdsByType(sourceType: OcrSource): Set<String> =
        withContext(Dispatchers.IO) {
            dao.getIndexedSourceIdsByType(sourceType.name).toSet()
        }

    /**
     * Get distinct source IDs that have been PROCESSED for a source type.
     * This includes notes with text (SUCCESS), blank notes (EMPTY), and low-signal notes.
     *
     * IMPORTANT: Use this for backfill completion check instead of getIndexedSourceIdsByType.
     * Blank notes are processed but have no entry in unified_ocr_text, so using
     * unified_ocr_text would never count them as "indexed" → backfill never completes.
     */
    suspend fun getProcessedSourceIdsByType(sourceType: OcrSource): Set<String> =
        withContext(Dispatchers.IO) {
            dao.getProcessedSourceIdsByType(sourceType.name).toSet()
        }

    /**
     * Count processed pages for a source type, restricted to a set of page entry IDs.
     * Uses chunking to avoid SQLite bind limits.
     */
    suspend fun getProcessedPageCountByTypeAndIds(sourceType: OcrSource, entryIds: Set<String>): Int =
        withContext(Dispatchers.IO) {
            if (entryIds.isEmpty()) return@withContext 0
            val chunkSize = 900
            entryIds.toList()
                .chunked(chunkSize)
                .sumOf { dao.getProcessedPageCountByTypeAndIds(sourceType.name, it) }
        }

    /**
     * Get all OCR texts for a source type (batch loading for profiles).
     * Used to avoid N+1 queries when loading semantic profiles for Smart Link.
     */
    suspend fun getOcrTextsBySourceType(sourceType: OcrSource): List<UnifiedOcrText> =
        withContext(Dispatchers.IO) {
            dao.getOcrTextsBySourceType(sourceType.name)
        }

    /**
     * Count OCR text records for a source type.
     * Quick check if any OCR data exists (used for Smart Link gating).
     */
    suspend fun getCountBySourceType(sourceType: OcrSource): Int =
        withContext(Dispatchers.IO) {
            dao.getCountBySourceType(sourceType.name)
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECT WRITE OPERATIONS (for event indexing)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Upsert OCR text directly.
     * Used for event indexing where we bypass the normal page → text flow.
     * Events are text-only (no bitmap), so they don't need UnifiedOcrPage records.
     *
     * @param ocrText The OCR text record to upsert
     */
    suspend fun upsertOcrText(ocrText: UnifiedOcrText) = withContext(Dispatchers.IO) {
        dao.upsertOcrText(ocrText)
        Log.d(TAG, "Upserted OCR text: ${ocrText.id}")
    }

    /**
     * Delete OCR text by ID.
     * Used for event profile cleanup when events are deleted.
     *
     * @param id The composite ID to delete
     */
    suspend fun deleteOcrText(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTextById(id)
        Log.d(TAG, "Deleted OCR text: $id")
    }

    /**
     * Delete OCR page status + text for a specific source page.
     * Used when Boox Notes pages are removed from a notebook.
     */
    suspend fun deleteOcrForPage(sourceType: OcrSource, sourceId: String, pageId: String) {
        deleteOcrForPage(UnifiedOcrPage.generateId(sourceType, sourceId, pageId))
    }

    /**
     * Delete OCR page status + text by composite ID.
     * Used when a page becomes empty after content removal.
     *
     * @param id The composite ID to delete
     */
    suspend fun deleteOcrForPage(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTextById(id)
        dao.deletePageById(id)
        Log.d(TAG, "Deleted OCR page + text: $id")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Normalize text for search (lowercase, NFKC, whitespace collapsed).
     */
    private fun normalizeForSearch(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Compute SHA-256 hash of text (truncated to 16 chars).
     */
    private fun computeHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Count words in text.
     */
    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    /**
     * Escape query for FTS4 MATCH.
     */
    private fun escapeForFts(query: String): String {
        // FTS4 special characters that need escaping
        return query
            .replace("\"", "\"\"")
            .replace("*", "")
            .replace(":", " ")
            .trim()
            .let { if (it.isBlank()) "" else "\"$it\"" }
    }
}
