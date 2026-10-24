package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.editor.ui.TASKS_CONTROL_COLUMN_WIDTH
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ui.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates Digital Ink extraction for Task pages.
 *
 * Responsibilities:
 * - Debounces rapid page changes (500ms wait after last change)
 * - Checks if page is a Task page before processing
 * - Triggers DigitalInkService extraction
 * - Routes results to TaskSemanticBridge
 * - Queues pages if model not ready, processes when ready
 * - Batch processes unconverted pages on startup (after model downloads)
 *
 * Thread Safety: All public methods are thread-safe.
 *
 * Usage:
 * ```
 * // In PageDataManager after bitmap persist:
 * TaskTextExtractor.instance.onPageChanged(pageId, density)
 * ```
 */
class TaskTextExtractor private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TaskTextExtractor"
        private const val DEBOUNCE_MS = 500L

        /** Max pages to process in startup batch (respects quota) */
        private const val STARTUP_BATCH_LIMIT = 100

        /** T7: how long recognition waits for MainScreen to measure the Tasks canvas */
        private const val GEOMETRY_WAIT_STEP_MS = 250L
        private const val GEOMETRY_WAIT_STEPS = 120  // 30 s

        /**
         * Version of the rules that tidy recognized text (DigitalInkService.tidyRecognizedText). Raise it
         * when they change: every task page with recognized text is then read again once.
         * 2 (Oct 2026): no more dropped letters and words, no more spell "correction".
         */
        private const val TIDY_RULES_VERSION = 2
        private const val PREFS = "task_handwriting"
        private const val KEY_TIDY_RULES = "tidyRulesVersion"

        @Volatile
        private var instance: TaskTextExtractor? = null

        /**
         * Get or create the singleton instance.
         */
        fun getInstance(context: Context, scope: CoroutineScope): TaskTextExtractor {
            return instance ?: synchronized(this) {
                instance ?: TaskTextExtractor(context.applicationContext, scope).also {
                    instance = it
                }
            }
        }

        /**
         * Get existing instance, or null if not initialized.
         */
        fun getInstanceOrNull(): TaskTextExtractor? = instance
    }

    // Page change events (pageId, density)
    private val pendingPages = MutableSharedFlow<Pair<String, Float>>(extraBufferCapacity = 10)

    // Pages attempted before model was ready - will be processed when model downloads
    private val pendingModelReady = mutableSetOf<Pair<String, Float>>()
    private val pendingModelReadyLock = Any()

    // Prevent multiple concurrent batch processing runs
    private val batchProcessingInProgress = AtomicBoolean(false)
    private val rereadInProgress = AtomicBoolean(false)

    // Services (lazy initialized)
    private val taskDao: TaskDao by lazy { AppDatabase.getDatabase(context).taskDao() }
    private val digitalInkService: DigitalInkService by lazy {
        DigitalInkService.create(context, scope)
    }
    private val taskSemanticBridge: TaskSemanticBridge by lazy {
        TaskSemanticBridge.create(context, scope)
    }
    private val ocrRepository: UnifiedOcrRepository by lazy { UnifiedOcrRepository(context) }
    private val quotaManager: OcrQuotaManager by lazy { OcrQuotaManager(context) }

    init {
        startProcessingLoop()
    }

    /**
     * Notify that a page has been changed (strokes saved).
     * Checks if it's a Task page and queues for extraction.
     *
     * @param pageId Page UUID
     * @param density Screen density for dp-to-px conversion
     */
    fun onPageChanged(pageId: String, density: Float) {
        scope.launch(Dispatchers.IO) {
            // Check if this is a Task page BEFORE emitting to debounce
            val isTask = taskDao.isTaskPage(pageId) > 0
            if (isTask) {
                Log.d(TAG, "Task page changed: $pageId")
                pendingPages.tryEmit(pageId to density)
            }
        }
    }

    /**
     * Force immediate extraction (bypasses debounce).
     * Used for testing or explicit user action.
     */
    fun extractNow(pageId: String, density: Float) {
        scope.launch(Dispatchers.IO) {
            val isTask = taskDao.isTaskPage(pageId) > 0
            if (isTask) {
                processPage(pageId, density)
            } else {
                Log.d(TAG, "Not a task page: $pageId")
            }
        }
    }

    /**
     * Called when Digital Ink model download completes.
     *
     * This triggers:
     * 1. Processing of any pages that were queued while waiting for model
     * 2. Batch processing of all historical unconverted Task pages
     * 3. Once after a change of the tidy rules: reading again every page with recognized text
     *
     * Called from DigitalInkModelManager after successful model download (and at every start when the
     * model is already there).
     */
    fun onModelReady() {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Model ready - processing pending and unconverted pages")

            // 1. Process pages that were queued while waiting for model
            val pending = synchronized(pendingModelReadyLock) {
                pendingModelReady.toList().also { pendingModelReady.clear() }
            }

            if (pending.isNotEmpty()) {
                Log.i(TAG, "Processing ${pending.size} pages queued while model was downloading")
                for ((pageId, density) in pending) {
                    processPage(pageId, density)
                }
            }

            // 2. Batch process all unconverted Task pages
            processUnconvertedPages()

            // 3. Text saved under older tidy rules
            rereadRecognizedPagesOnce()
        }
    }

    /**
     * Reads every task page that has recognized text again, once per [TIDY_RULES_VERSION], so text saved
     * under older rules is replaced (Oct 2026: they had dropped the "B" of "Task B", "3 PM" and the like,
     * and turned "bread" into "read"). From the ink, on the tablet, no quota. Done only when every page
     * could be read; otherwise tried again at the next start.
     */
    private suspend fun rereadRecognizedPagesOnce() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_TIDY_RULES, 0) >= TIDY_RULES_VERSION) return
        if (!rereadInProgress.compareAndSet(false, true)) return
        try {
            if (!digitalInkService.isReady() || awaitMeasuredTasksCanvasHeight() == null) {
                Log.i(TAG, "Re-read under tidy rules $TIDY_RULES_VERSION postponed (model or canvas not ready)")
                return
            }
            val density = context.resources.displayMetrics.density
            val pageIds = taskDao.getPageIdsWithRecognizedText()
            var allRead = true
            for (pageId in pageIds) {
                try {
                    if (!processPageInternal(pageId, density, isNewPage = false)) allRead = false
                } catch (e: Exception) {
                    allRead = false
                    Log.e(TAG, "Re-read of $pageId failed: ${e.message}")
                }
            }
            if (allRead) prefs.edit().putInt(KEY_TIDY_RULES, TIDY_RULES_VERSION).apply()
            Log.i(TAG, "Re-read ${pageIds.size} task pages under tidy rules $TIDY_RULES_VERSION (complete=$allRead)")
        } finally {
            rereadInProgress.set(false)
        }
    }

    /**
     * Batch process all Task pages that have strokes but no text in unified_ocr_text.
     *
     * Called:
     * - After model download completes (via onModelReady)
     * - Respects monthly quota budget
     *
     * Uses getTaskPagesNeedingOcr() which finds pages where:
     * - Page has strokes but no OCR entry, OR
     * - Page was modified after last OCR, OR
     * - Previous OCR status was non-terminal
     */
    private suspend fun processUnconvertedPages() {
        // Prevent concurrent batch runs
        if (!batchProcessingInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Batch processing already in progress, skipping")
            return
        }

        try {
            // T7: wait once for the measured geometry instead of per page
            if (awaitMeasuredTasksCanvasHeight() == null) {
                Log.w(TAG, "Tasks canvas not measured; batch skipped (retried on next launch/model ready)")
                return
            }
            val density = context.resources.displayMetrics.density
            val unconverted = ocrRepository.getTaskPagesNeedingOcr(STARTUP_BATCH_LIMIT)

            if (unconverted.isEmpty()) {
                Log.d(TAG, "No unconverted Task pages found")
                return
            }

            Log.i(TAG, "Found ${unconverted.size} unconverted Task pages, starting batch processing")
            var processedCount = 0
            var skippedQuota = 0

            for (page in unconverted) {
                // Check if this is truly a new page (not previously indexed)
                val isNewPage = ocrRepository.isNewPage(OcrSource.TASK, page.date, page.pageId)

                // Only check quota for new pages (re-processing doesn't consume quota)
                if (isNewPage && !quotaManager.canIndexNewPage(OcrSource.TASK)) {
                    skippedQuota = unconverted.size - processedCount
                    Log.d(TAG, "Monthly budget exhausted, stopping batch ($skippedQuota pages remaining)")
                    break
                }

                try {
                    processPageInternal(page.pageId, density, isNewPage = isNewPage)
                    processedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process page ${page.pageId}: ${e.message}")
                }
            }

            Log.i(TAG, "Batch processing complete: $processedCount processed" +
                    if (skippedQuota > 0) ", $skippedQuota skipped (quota)" else "")

        } finally {
            batchProcessingInProgress.set(false)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @OptIn(FlowPreview::class)
    private fun startProcessingLoop() {
        scope.launch {
            pendingPages
                .debounce(DEBOUNCE_MS)  // Wait 500ms after last change
                .collect { (pageId, density) ->
                    processPage(pageId, density)
                }
        }
    }

    /**
     * Process a page from the debounce flow or extractNow.
     * Queues page if model not ready (instead of silently skipping).
     */
    private suspend fun processPage(pageId: String, density: Float) {
        if (!digitalInkService.isReady()) {
            // Queue for later instead of silently skipping
            Log.d(TAG, "Model not ready, queueing page for later: $pageId")
            synchronized(pendingModelReadyLock) {
                pendingModelReady.add(pageId to density)
            }
            return
        }

        // Check if this is a new page (for quota tracking)
        // Use existing isNewPage() - requires dayDate lookup
        val dayDate = taskDao.getDayDateByPageId(pageId)
        val isNewPage = if (dayDate != null) {
            ocrRepository.isNewPage(OcrSource.TASK, dayDate, pageId)
        } else {
            true // Can't find dayDate - treat as new (conservative for quota)
        }
        processPageInternal(pageId, density, isNewPage)
    }

    /** The measured Tasks canvas height, waiting up to 30 s for MainScreen to report it. */
    private suspend fun awaitMeasuredTasksCanvasHeight(): Float? {
        repeat(GEOMETRY_WAIT_STEPS) {
            GlobalAppSettings.tasksCanvasHeight.takeIf { it > 0f }?.let { return it }
            delay(GEOMETRY_WAIT_STEP_MS)
        }
        return GlobalAppSettings.tasksCanvasHeight.takeIf { it > 0f }
    }

    /**
     * Internal page processing with quota tracking.
     *
     * @param pageId Page UUID
     * @param density Screen density
     * @param isNewPage True if this page has never been indexed (consumes quota)
     * @return false when the page was skipped for now (canvas not measured, day on full screen pages)
     */
    private suspend fun processPageInternal(pageId: String, density: Float, isNewPage: Boolean): Boolean {
        Log.i(TAG, "Processing task page: $pageId (isNew=$isNewPage)")
        val startTime = System.currentTimeMillis()

        // Use measured canvas height and top offset from GlobalAppSettings (set by MainScreen).
        // T7 (Oct 2026): never guess — a fallback height maps ink to the wrong rows and writes
        // recognized text there. Unmeasured -> skip; the page stays "needing OCR" and is retried.
        val canvasHeight = awaitMeasuredTasksCanvasHeight() ?: run {
            Log.w(TAG, "Tasks canvas not measured; skipping $pageId for now")
            return false
        }
        // Full screen Tasks (Oct 2026): rows are read on pages of 10; a day on full screen pages waits
        // like an unmeasured canvas (the page stays needing OCR and is retried after full screen)
        val pageDate = taskDao.getDayDateByPageId(pageId)
        if (pageDate != null && com.boox.day.tasks.TaskPagination.isReflowed(pageDate)) {
            Log.i(TAG, "Tasks of $pageDate are on full screen pages; skipping $pageId for now")
            return false
        }
        val controlColumnPx = convertDpToPixel(TASKS_CONTROL_COLUMN_WIDTH, context)
        val topOffset = GlobalAppSettings.tasksTopOffsetPx.takeIf { it > 0f }
            ?: density  // 1.dp * density

        val results = withContext(Dispatchers.Default) {
            digitalInkService.extractPage(pageId, canvasHeight, controlColumnPx, topOffset)
        }

        // Only bridge to semantic pipeline if we got some text
        val nonEmptyResults = results.filter { !it.isEmpty }
        if (nonEmptyResults.isNotEmpty()) {
            Log.i(TAG, "Extracted ${nonEmptyResults.size} slots with text, bridging to semantic pipeline")
            taskSemanticBridge.processResults(pageId, results)

            // Track quota for new pages
            if (isNewPage) {
                quotaManager.recordNewPageIndexed(OcrSource.TASK)
                Log.d(TAG, "Quota: recorded new Task page indexed")
            }
        } else {
            Log.d(TAG, "No text extracted from $pageId")
            // T10 (Oct 2026): once all ink on the page is erased, drop the old recognized text
            // (badge, search, AI and Convert kept using it). Only when the page really has no
            // strokes — an all-empty result can also mean the ink model wasn't ready.
            val pageHasStrokes = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).strokeDao().hasStrokesForPage(pageId)
            }
            if (!pageHasStrokes) {
                taskSemanticBridge.processResults(pageId, results)  // clears every row + the page's OCR text
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Task page processing complete: $pageId (${elapsed}ms)")
        return true
    }
}
