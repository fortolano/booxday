package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Indexes typed task text into the semantic extraction pipeline.
 *
 * Responsibilities:
 * - Debounces rapid typed text changes (500ms wait after last change)
 * - Applies text normalization (spell correction, name detection) via HandwritingNormalizer
 * - Calls SemanticGeniusService.extractSemantics() for full 12-step extraction
 * - Stores results in unified_ocr_text via UnifiedOcrRepository
 * - Merges typed text with handwritten text for same task page (combined indexing)
 *
 * Thread Safety: All public methods are thread-safe.
 *
 * Usage:
 * ```
 * // In TaskRepository.setTypedText() after DAO update:
 * TaskTextIndexer.getInstance(context, scope).onTypedTextChanged(taskId, dayDate, taskPageIndex, pageId)
 * ```
 *
 * Added Jan 2026: Makes typed text a first-class citizen alongside handwritten text.
 */
class TaskTextIndexer private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TaskTextIndexer"
        private const val DEBOUNCE_MS = 500L

        @Volatile
        private var instance: TaskTextIndexer? = null

        /**
         * Get or create the singleton instance.
         */
        fun getInstance(context: Context, scope: CoroutineScope): TaskTextIndexer {
            return instance ?: synchronized(this) {
                instance ?: TaskTextIndexer(context.applicationContext, scope).also {
                    instance = it
                }
            }
        }

        /**
         * Get existing instance, or null if not initialized.
         */
        fun getInstanceOrNull(): TaskTextIndexer? = instance
    }

    // Data class for pending indexing requests (taskId, dayDate, taskPageIndex, pageId)
    private data class IndexRequest(
        val taskId: String,
        val dayDate: String,
        val taskPageIndex: Int,
        val pageId: String
    )

    // Every page changed since the last indexing run (T19, Oct 2026: the debounce alone kept only
    // the last request, so inserts on several dates indexed just one of them)
    private val pagesToIndex = java.util.concurrent.ConcurrentHashMap<String, IndexRequest>()

    // Pending indexing events
    private val pendingIndexRequests = MutableSharedFlow<IndexRequest>(extraBufferCapacity = 20)

    // Services (lazy initialized)
    private val taskDao: TaskDao by lazy { AppDatabase.getDatabase(context).taskDao() }
    private val ocrRepository: UnifiedOcrRepository by lazy { UnifiedOcrRepository(context) }
    private val semanticGeniusService: SemanticGeniusService by lazy { SemanticGeniusService(context) }
    private val quotaManager: OcrQuotaManager by lazy { OcrQuotaManager(context) }

    init {
        startProcessingLoop()
    }

    /**
     * Notify that a task's typed text has been changed.
     * Triggers debounced re-indexing of the task page.
     *
     * @param taskId Task ID that was modified
     * @param dayDate Day date in "yyyy-MM-dd" format
     * @param taskPageIndex Task page index (0, 1, 2...)
     * @param pageId Page UUID for the task page
     */
    fun onTypedTextChanged(taskId: String, dayDate: String, taskPageIndex: Int, pageId: String) {
        Log.d(TAG, "Typed text changed for task $taskId on $dayDate (page $taskPageIndex)")
        val request = IndexRequest(taskId, dayDate, taskPageIndex, pageId)
        pagesToIndex[pageId] = request
        pendingIndexRequests.tryEmit(request)
    }

    /**
     * Force immediate indexing of a task page (bypasses debounce).
     * Used for testing or explicit user action.
     */
    fun indexNow(dayDate: String, taskPageIndex: Int, pageId: String) {
        scope.launch(Dispatchers.IO) {
            processTaskPage(dayDate, taskPageIndex, pageId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @OptIn(FlowPreview::class)
    private fun startProcessingLoop() {
        scope.launch {
            pendingIndexRequests
                .debounce(DEBOUNCE_MS)  // Wait 500ms after last change
                .collect {
                    val batch = pagesToIndex.values.toList()
                    batch.forEach { pagesToIndex.remove(it.pageId, it) }
                    for (request in batch) {
                        processTaskPage(request.dayDate, request.taskPageIndex, request.pageId)
                    }
                }
        }
    }

    /**
     * Process a task page and index combined text (typed + handwritten).
     *
     * Combines text from all 10 task slots, applying normalization to typed text,
     * then runs full semantic extraction and stores the result.
     */
    private suspend fun processTaskPage(dayDate: String, taskPageIndex: Int, pageId: String) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Processing task page: $dayDate page $taskPageIndex ($pageId)")

        try {
            // Build combined text from all slots (typed takes priority over recognized)
            val combinedText = buildCombinedTaskPageText(dayDate, taskPageIndex)

            if (combinedText.isBlank()) {
                val ocrId = com.boox.day.data.db.UnifiedOcrPage.generateId(OcrSource.TASK, dayDate, pageId)
                ocrRepository.deleteOcrForPage(ocrId)
                Log.d(TAG, "No text content for task page $dayDate/$taskPageIndex; cleared OCR")
                return
            }

            Log.d(TAG, "Combined text (${combinedText.length} chars): ${combinedText.take(100)}...")

            // Full 12-step semantic extraction
            val semanticBundle = semanticGeniusService.extractSemantics(
                text = combinedText,
                sourceType = OcrSource.TASK,
                sourceTitle = "Tasks - $dayDate",
                sourceCreatedAt = System.currentTimeMillis()
            )

            // Check if this is a new page (for quota tracking)
            val isNewPage = ocrRepository.isNewPage(OcrSource.TASK, dayDate, pageId)

            val now = System.currentTimeMillis()

            // Ensure page record exists before storing result
            // (storeOcrResult uses UPDATE, not INSERT, so page must exist first)
            ocrRepository.markInProgress(
                sourceType = OcrSource.TASK,
                sourceId = dayDate,
                pageId = pageId,
                sourceModifiedAt = now
            )

            // Store using existing repository infrastructure
            ocrRepository.storeOcrResult(
                sourceType = OcrSource.TASK,
                sourceId = dayDate,
                pageId = pageId,
                sourceModifiedAt = now,
                text = combinedText,
                confidence = 1.0f,  // Typed text has high confidence
                language = "en",
                semanticBundle = semanticBundle,
                sourceTitle = "Tasks - $dayDate",
                sourcePath = null,
                sourceDate = dayDate
            )

            // Track quota for new pages
            if (isNewPage) {
                quotaManager.recordNewPageIndexed(OcrSource.TASK)
                Log.d(TAG, "Quota: recorded new Task page indexed")
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Task page indexed: $dayDate/$taskPageIndex (${elapsed}ms, " +
                    "domain=${semanticBundle.domain?.name ?: "null"}, " +
                    "keywords=${semanticBundle.keywords.size})")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to index task page $dayDate/$taskPageIndex: ${e.message}", e)
        }
    }

    /**
     * Build combined text from all slots in a task page.
     *
     * For each slot, uses typedText if available (with normalization),
     * otherwise falls back to recognizedText (from handwriting).
     *
     * @param dayDate Day date in "yyyy-MM-dd" format
     * @param taskPageIndex Task page index (0, 1, 2...)
     * @return Combined text from all non-empty slots, one line per slot
     */
    private suspend fun buildCombinedTaskPageText(dayDate: String, taskPageIndex: Int): String {
        val tasks = taskDao.getTasksForPage(dayDate, taskPageIndex)

        if (tasks.isEmpty()) {
            Log.d(TAG, "No tasks found for $dayDate page $taskPageIndex")
            return ""
        }

        // Get the normalizer for typed text normalization
        val normalizer = HandwritingNormalizerFactory.getInstance(context, "en-US")

        return tasks
            .sortedBy { it.rowIndex }
            .mapNotNull { task ->
                getTaskText(task, normalizer)
            }
            .joinToString("\n")
    }

    /**
     * Get text for a single task, applying normalization to typed text.
     *
     * Priority:
     * 1. typedText (normalized via HandwritingNormalizer)
     * 2. recognizedText (already normalized during Digital Ink extraction)
     * 3. convertedText (from AI memo conversion)
     *
     * @param task The task to get text from
     * @param normalizer Optional normalizer for typed text (null = no normalization)
     * @return Normalized text, or null if task has no text content
     */
    private fun getTaskText(task: Task, normalizer: HandwritingNormalizer?): String? {
        // Priority 1: Typed text (apply normalization)
        task.typedText?.takeIf { it.isNotBlank() }?.let { typedText ->
            return if (normalizer != null) {
                normalizer.normalize(typedText)
            } else {
                typedText
            }
        }

        // Priority 2: Recognized text (already normalized during Digital Ink extraction)
        task.recognizedText?.takeIf { it.isNotBlank() }?.let { return it }

        // Priority 3: Converted text (from AI memo conversion)
        task.convertedText?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }
}
