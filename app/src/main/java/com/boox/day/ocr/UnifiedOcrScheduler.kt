package com.boox.day.ocr

import android.content.Context
import android.os.Build
import com.boox.day.BuildConfig
import android.os.Environment
import android.util.Log
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrQueueItem
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.source.BooxNoteBitmapSource
import com.boox.day.ocr.source.MemoBitmapSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun filterCompletedQueueItems(
    queue: List<OcrQueueItem>,
    completedQueueIds: Set<String>
): List<OcrQueueItem> {
    return queue.filter { it.stableQueueId() !in completedQueueIds }
}

/**
 * Manages the unified OCR processing queue for all content sources.
 *
 * Quota Model:
 * - Page budgets are effectively unlimited (OcrQuotaManager): no backfill phase, no monthly page budget
 * - The monthly AI read limit is enforced per page in UnifiedOcrPipeline
 *
 * Key features:
 * - Priority-based queue (recent content processed first)
 * - Persistent queue (survives app restart via SharedPreferences)
 * - Quota-aware scheduling via OcrQuotaManager
 *
 * This is a FOREGROUND-ONLY scheduler. No WorkManager because:
 * - Boox devices aggressively freeze background apps
 * - OCR needs to be interruptible for user interactions
 * - Processing happens during idle periods detected by UnifiedOcrIdleProcessor
 */
class UnifiedOcrScheduler(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedOcrScheduler"
        private const val PREFS_NAME = "unified_ocr_queue_prefs"
        private const val KEY_QUEUE = "pending_queue"
        private const val KEY_LAST_BUILD_TIME = "last_build_time"

        // Note: BOOX_NOTES_PAGES_PER_NOTE and DATE_LOOKBACK_DAYS come from OcrQuotaManager
        // (booxNotesPagesPerNote, dateLookbackDays)
    }

    private val quotaManager by lazy { OcrQuotaManager(context) }

    // Content selection parameters (from OcrQuotaManager)
    private val dateLookbackDays: Int get() = quotaManager.dateLookbackDays
    private val booxNotesPagesPerNote: Int get() = quotaManager.booxNotesPagesPerNote

    // Priority scoring from the local OCR config (higher = processed first)
    private val priorityToday: Int get() = quotaManager.priorityToday
    private val priorityThisWeek: Int get() = quotaManager.priorityThisWeek
    private val priorityThisMonth: Int get() = quotaManager.priorityThisMonth
    private val priorityOlder: Int get() = quotaManager.priorityOlder

    // Queue management from the local OCR config
    private val queueStaleThresholdMs: Long get() = quotaManager.queueStaleThresholdMs

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ocrRepository by lazy { UnifiedOcrRepository(context) }
    private val booxNoteRepository by lazy { BooxNoteRepository(context) }

    // Bitmap sources (lazy initialized when needed)
    // NOTE: Tasks use Digital Ink Recognition instead of Cloud Vision bitmap source
    private val booxNoteBitmapSource by lazy { BooxNoteBitmapSource() }
    private val memoBitmapSource by lazy {
        MemoBitmapSource(context, DayPageRepository(context), PageRepository(context))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Source Type Checks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a source type should be scheduled for OCR processing.
     * Events are not scheduled - they're indexed immediately on sync/creation.
     *
     * @param sourceType The source type to check
     * @return true if this source type should be scheduled, false if exempt
     */
    fun shouldSchedule(sourceType: OcrSource): Boolean {
        return when (sourceType) {
            OcrSource.BOOX_NOTE -> true   // Scheduled for batch OCR (Cloud Vision)
            OcrSource.MEMO -> true        // Scheduled for batch OCR (Cloud Vision)
            OcrSource.TASK -> false       // Uses Digital Ink Recognition (NOT Cloud Vision)
            OcrSource.CALENDAR_EVENT -> false  // Indexed by sync
            OcrSource.LOCAL_EVENT -> false     // Indexed immediately on creation
            OcrSource.TEXT_ANNOTATION -> false // Indexed immediately on creation/edit
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Queue Building
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build a fresh priority-ordered queue from all content sources.
     *
     * Budget: the page budget is effectively unlimited (OcrQuotaManager.getRemainingBudget()),
     * so it only caps the queue size.
     *
     * Priority calculation:
     * - Today's content: 100
     * - This week: 80
     * - This month: 60
     * - Older: 40
     *
     * Source ordering: Memos > Tasks > Boox Notes (user content first)
     *
     * @return Queue ordered by priority (highest first), limited by available budget
     */
    suspend fun buildQueue(): List<OcrQueueItem> {
        val start = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        if (!quotaManager.isOcrFeatureEnabled) {
            Log.d(TAG, "OCR disabled in OCR config - skipping queue build (${quotaManager.describeAppliedConfig()})")
            return emptyList()
        }

        // Get remaining budget based on current phase
        val remainingBudget = quotaManager.getRemainingBudget()
        if (remainingBudget <= 0) {
            Log.d(TAG, "Budget exhausted - no pages to process")
            return emptyList()
        }

        val queue = mutableListOf<OcrQueueItem>()

        // Index memos (user content first, uses monthly budget)
        // NOTE: Tasks use Digital Ink Recognition, NOT Cloud Vision - not added to queue
        queue.addAll(buildMemoQueue(today, now))

        // Boox Notes: use backfill budget during backfill phase, monthly budget after
        if (quotaManager.isBackfillPhase()) {
            Log.d(TAG, "Backfill phase: remaining ${quotaManager.getRemainingBackfill()}/${quotaManager.backfillLimit}")
        } else {
            Log.d(TAG, "Monthly phase: remaining ${quotaManager.getRemainingMonthlyBudget()}/${quotaManager.monthlyBudget}")
        }
        queue.addAll(buildBooxNotesQueue(today, now))

        // Sort by priority (highest first) and limit to remaining budget
        // Note: The actual budget enforcement happens in UnifiedOcrPipeline.processPage()
        // This limit here is just to avoid building a huge queue
        val sortedQueue = queue.sortedByDescending { it.priority }.take(remainingBudget)

        Log.i(TAG, "PERF: buildQueue -> ${sortedQueue.size} items (budget=$remainingBudget, phase=${if (quotaManager.isBackfillPhase()) "backfill" else "monthly"}) " +
            "(${System.currentTimeMillis() - start}ms)")
        return sortedQueue
    }

    /**
     * Build queue items from Memo pages.
     * Uses efficient single SQL query to find pages that changed.
     * NO date iteration - O(1) query instead of O(days).
     */
    private suspend fun buildMemoQueue(today: LocalDate, now: Long): List<OcrQueueItem> {
        val items = mutableListOf<OcrQueueItem>()

        try {
            // Query limit: use remaining budget as soft limit (actual enforcement in pipeline)
            val queryLimit = quotaManager.getRemainingBudget().coerceAtLeast(50)
            val pagesNeedingOcr = ocrRepository.getMemoPagesNeedingOcr(queryLimit)

            for (page in pagesNeedingOcr) {
                items.add(OcrQueueItem(
                    sourceType = OcrSource.MEMO,
                    sourceId = page.date,
                    pageId = page.pageId,
                    sourceCreatedAt = dateToTimestamp(page.date),
                    sourceModifiedAt = page.updatedAt,
                    priority = calculatePriority(page.updatedAt, now)
                ))
            }

            Log.d(TAG, "Memo queue: ${items.size} items (efficient query)")
            // DEBUG: Log each memo item for diagnosis
            items.forEach { item ->
                Log.d(TAG, "  MEMO: date=${item.sourceId} pageId=${item.pageId.take(8)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build memo queue: ${e.message}")
        }

        return items
    }

    /**
     * Build queue items from Boox Notes.
     * Limit: dateLookbackDays (default 120 days), max booxNotesPagesPerNote pages per note.
     * Total pages limited by quota (backfill or monthly budget).
     */
    private suspend fun buildBooxNotesQueue(today: LocalDate, now: Long): List<OcrQueueItem> {
        if (!hasBooxStoragePermission()) {
            Log.d(TAG, "Skipping Boox Notes queue: storage permission not granted")
            return emptyList()
        }

        val items = mutableListOf<OcrQueueItem>()
        val pageLimit = quotaManager.getRemainingBudget().coerceAtLeast(50)

        // Tracking counters for diagnostic logging
        var skippedNoPages = 0      // Notes with pageIdsJson = [] (empty notebooks)
        var skippedTooOld = 0       // Notes outside dateLookbackDays window
        var skippedAlreadyDone = 0  // Pages already processed (needsReOcr = false)
        var notesWithEligiblePages = 0

        try {
            val cutoffTime = today.minusDays(dateLookbackDays.toLong())
                .atStartOfDay()
                .toEpochSecond(java.time.ZoneOffset.UTC) * 1000

            // SMART LINK BACKFILL FIX: Read from BooxNoteIndex (source of truth) instead of filesystem
            // This ensures we use the same list of notes that the scan discovered
            val indexedNotes = booxNoteRepository.getAllNotes()
            Log.d(TAG, "Building Boox Notes queue from ${indexedNotes.size} indexed notes " +
                "(lookback=${dateLookbackDays}d, pagesPerNote=$booxNotesPagesPerNote, budget=$pageLimit)")

            noteLoop@ for (note in indexedNotes) {
                // Enforce soft page count limit (actual enforcement in pipeline)
                if (items.size >= pageLimit) {
                    Log.d(TAG, "Boox Notes queue: reached soft limit ($pageLimit)")
                    break
                }

                // Parse page IDs from the indexed JSON
                val pageIds = parsePageIds(note.pageIdsJson)
                if (pageIds.isEmpty()) {
                    skippedNoPages++
                    continue
                }

                // Limit pages per note
                val limitedPageIds = pageIds.take(booxNotesPagesPerNote)

                // Use indexed modification time (avoids filesystem access)
                val sourceModified = note.shapeFileModified

                // Skip if note is too old
                if (sourceModified < cutoffTime) {
                    skippedTooOld++
                    continue
                }

                // Add queue items for each page that needs OCR
                var addedFromThisNote = 0
                for (pageId in limitedPageIds) {
                    // Check soft limit again before adding
                    if (items.size >= pageLimit) {
                        Log.d(TAG, "Boox Notes queue: reached soft limit ($pageLimit)")
                        break@noteLoop
                    }

                    if (!ocrRepository.needsReOcr(
                            sourceType = OcrSource.BOOX_NOTE,
                            sourceId = note.uniqueId,
                            pageId = pageId,
                            currentModifiedAt = sourceModified,
                            currentSourceTitle = note.title,
                            currentSourceDate = note.originalDate
                        )
                    ) {
                        skippedAlreadyDone++
                        continue
                    }

                    items.add(OcrQueueItem(
                        sourceType = OcrSource.BOOX_NOTE,
                        sourceId = note.uniqueId,
                        pageId = pageId,
                        sourceCreatedAt = sourceModified,
                        sourceModifiedAt = sourceModified,
                        priority = calculatePriority(sourceModified, now),
                        sourceTitle = note.title,
                        sourceDate = note.originalDate
                    ))
                    addedFromThisNote++
                }
                if (addedFromThisNote > 0) notesWithEligiblePages++
            }

            // Detailed summary log for diagnostics
            Log.i(TAG, "Boox Notes queue: ${items.size} pages from $notesWithEligiblePages notes " +
                "(total=${indexedNotes.size}, noPages=$skippedNoPages, tooOld=$skippedTooOld, alreadyDone=$skippedAlreadyDone)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build Boox Notes queue: ${e.message}")
        }

        return items
    }

    /**
     * Get eligible Boox Note page entry IDs based on scheduler rules.
     *
     * Eligibility matches backfill indexing:
     * - Note within dateLookbackDays (default 120 days)
     * - First booxNotesPagesPerNote pages only
     */
    suspend fun getEligibleBooxNotePageEntryIds(): Set<String> {
        if (!hasBooxStoragePermission()) {
            return emptySet()
        }

        val eligibleEntryIds = mutableSetOf<String>()
        val today = LocalDate.now()

        try {
            val cutoffTime = today.minusDays(dateLookbackDays.toLong())
                .atStartOfDay()
                .toEpochSecond(java.time.ZoneOffset.UTC) * 1000

            val indexedNotes = booxNoteRepository.getAllNotes()
            for (note in indexedNotes) {
                if (note.shapeFileModified < cutoffTime) continue

                val pageIds = parsePageIds(note.pageIdsJson)
                if (pageIds.isEmpty()) continue

                for (pageId in pageIds.take(booxNotesPagesPerNote)) {
                    eligibleEntryIds.add(
                        UnifiedOcrPage.generateId(OcrSource.BOOX_NOTE, note.uniqueId, pageId)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build eligible Boox Notes page entry IDs: ${e.message}")
        }

        return eligibleEntryIds
    }

    /**
     * Parse page IDs from the JSON array stored in BooxNoteIndex.
     */
    private fun parsePageIds(pageIdsJson: String): List<String> {
        return try {
            Json.decodeFromString<List<String>>(pageIdsJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse pageIdsJson: ${e.message}")
            emptyList()
        }
    }

    private fun hasBooxStoragePermission(): Boolean = com.boox.day.booxnotes.BooxNotesAccess.has(context)

    /**
     * Convert date string to timestamp for priority calculation.
     */
    private fun dateToTimestamp(dateStr: String): Long {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            date.atStartOfDay()
                .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Calculate priority based on content age.
     * Priority values come from the local OCR config (OcrQuotaConfig).
     *
     * @param contentTimestamp Timestamp of the content (epoch ms)
     * @param now Current time (epoch ms)
     * @return Priority score (higher = more important)
     */
    private fun calculatePriority(contentTimestamp: Long, now: Long): Int {
        val ageMs = now - contentTimestamp
        val ageDays = ageMs / (24 * 60 * 60 * 1000)

        return when {
            ageDays <= 1 -> priorityToday       // Today (default: 100)
            ageDays <= 7 -> priorityThisWeek    // This week (default: 80)
            ageDays <= 30 -> priorityThisMonth  // This month (default: 60)
            else -> priorityOlder               // Older (default: 40)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEUE PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Save queue to SharedPreferences for app restart persistence.
     */
    fun saveQueue(queue: List<OcrQueueItem>) {
        try {
            val json = Json.encodeToString(queue)
            prefs.edit()
                .putString(KEY_QUEUE, json)
                .putLong(KEY_LAST_BUILD_TIME, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved queue with ${queue.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save queue: ${e.message}")
        }
    }

    /**
     * Load queue from SharedPreferences.
     *
     * @return Persisted queue, or empty list if none exists
     */
    fun loadQueue(): List<OcrQueueItem> {
        val json = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load queue: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clear the persisted queue.
     */
    fun clearQueue() {
        prefs.edit()
            .remove(KEY_QUEUE)
            .remove(KEY_LAST_BUILD_TIME)
            .apply()
        Log.d(TAG, "Cleared queue")
    }

    /**
     * Check if the queue is stale and should be rebuilt.
     * Stale threshold comes from the local OCR config (1 hour).
     */
    fun isQueueStale(): Boolean {
        val lastBuildTime = prefs.getLong(KEY_LAST_BUILD_TIME, 0L)
        return System.currentTimeMillis() - lastBuildTime > queueStaleThresholdMs
    }

    /**
     * Get the next batch of pages to process.
     *
     * @param maxPages Maximum number of pages to return (default 2 for low memory)
     * @return List of queue items to process
     */
    fun getNextBatch(maxPages: Int = 2): List<OcrQueueItem> {
        val queue = loadQueue()
        if (queue.isEmpty()) return emptyList()

        val batch = queue.take(maxPages)
        Log.d(TAG, "getNextBatch: returning ${batch.size} items")
        return batch
    }

    /**
     * Mark pages as completed and update the queue.
     *
     * @param completedQueueIds Set of composite queue IDs that were completed
     */
    fun markCompleted(completedQueueIds: Set<String>) {
        val queue = loadQueue()
        val updatedQueue = filterCompletedQueueItems(queue, completedQueueIds)

        val completedCount = queue.size - updatedQueue.size
        Log.d(TAG, "Marked $completedCount items completed, ${updatedQueue.size} remaining")

        saveQueue(updatedQueue)
    }

    /**
     * Remove a single item from the queue.
     */
    fun removeItem(sourceType: OcrSource, sourceId: String, pageId: String) {
        val queue = loadQueue()
        val updatedQueue = queue.filter {
            !(it.sourceType == sourceType && it.sourceId == sourceId && it.pageId == pageId)
        }
        saveQueue(updatedQueue)
    }

    /**
     * Get queue statistics for UI.
     */
    fun getQueueStats(): QueueStats {
        val queue = loadQueue()
        val bySource = queue.groupBy { it.sourceType }

        return QueueStats(
            totalItems = queue.size,
            booxNotesCount = bySource[OcrSource.BOOX_NOTE]?.size ?: 0,
            memoCount = bySource[OcrSource.MEMO]?.size ?: 0,
            taskCount = bySource[OcrSource.TASK]?.size ?: 0,
            isStale = isQueueStale()
        )
    }

    data class QueueStats(
        val totalItems: Int,
        val booxNotesCount: Int,
        val memoCount: Int,
        val taskCount: Int,
        val isStale: Boolean
    )

    /**
     * Get the appropriate bitmap source for a source type.
     * Only valid for bitmap sources (BOOX_NOTE, MEMO, TASK).
     * @throws IllegalArgumentException for event sources (CALENDAR_EVENT, LOCAL_EVENT)
     */
    fun getBitmapSource(sourceType: OcrSource) = when (sourceType) {
        OcrSource.BOOX_NOTE -> booxNoteBitmapSource
        OcrSource.MEMO -> memoBitmapSource
        OcrSource.TASK ->
            throw IllegalArgumentException("Tasks use Digital Ink Recognition, not Cloud Vision bitmap source")
        OcrSource.CALENDAR_EVENT, OcrSource.LOCAL_EVENT, OcrSource.TEXT_ANNOTATION ->
            throw IllegalArgumentException("Text sources don't have bitmap sources: $sourceType")
    }
}
