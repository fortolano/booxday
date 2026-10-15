package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.SemanticBundle
import com.boox.day.ocr.semantic.SemanticGeniusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridges Digital Ink recognition results to the semantic extraction pipeline.
 *
 * Stores recognized text in unified_ocr_text table with:
 * - sourceType = "TASK"
 * - Full 12-step semantic extraction via SemanticGeniusService
 *
 * This enables Smart Link and semantic search for task content with rich
 * semantic data including domains, intents, keywords, entities, and action items.
 *
 * Upgraded from MVP (Jan 2026): Now uses full SemanticGeniusService extraction
 * instead of basic SemanticBundle.
 */
class TaskSemanticBridge private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TaskSemanticBridge"

        @Volatile
        private var instance: TaskSemanticBridge? = null

        /**
         * Get or create the singleton instance.
         */
        fun getInstance(context: Context, scope: CoroutineScope): TaskSemanticBridge {
            return instance ?: synchronized(this) {
                instance ?: TaskSemanticBridge(context.applicationContext, scope).also {
                    instance = it
                }
            }
        }

        /**
         * Create a new instance (for dependency injection).
         */
        fun create(context: Context, scope: CoroutineScope): TaskSemanticBridge {
            return getInstance(context, scope)
        }
    }

    private val taskDao: TaskDao by lazy { AppDatabase.getDatabase(context).taskDao() }
    private val ocrRepository: UnifiedOcrRepository by lazy { UnifiedOcrRepository(context) }
    private val semanticGeniusService: SemanticGeniusService by lazy { SemanticGeniusService(context) }

    // Normalizer for spell correction + name detection on handwritten text
    // en-US is always supported, so !! is safe here
    private val normalizer: HandwritingNormalizer by lazy {
        HandwritingNormalizerFactory.getInstance(context, "en-US")!!
    }

    /**
     * Process Digital Ink results and store in unified_ocr_text.
     *
     * @param pageId Task page UUID
     * @param results List of slot results from DigitalInkService
     */
    fun processResults(pageId: String, results: List<DigitalInkService.SlotResult>) {
        scope.launch(Dispatchers.IO) {
            processResultsInternal(pageId, results)
        }
    }

    private suspend fun processResultsInternal(
        pageId: String,
        results: List<DigitalInkService.SlotResult>
    ) {
        val startTime = System.currentTimeMillis()

        // Get day date for this task page
        val dayDate = taskDao.getDayDateByPageId(pageId)
        if (dayDate == null) {
            Log.w(TAG, "Could not find dayDate for pageId: $pageId")
            return
        }

        // Get taskPageIndex for storing to correct tasks
        val taskPageIndex = taskDao.getTaskPageIndexByPageId(pageId) ?: 0

        // Store per-slot text to individual Task records (for Due tab display)
        // IMPORTANT: Also clear recognizedText for empty slots to avoid stale data
        // when user erases content from a previously-filled slot
        for (result in results) {
            if (!result.isEmpty && result.text.isNotBlank()) {
                // Apply spell correction + name detection before storing
                val normalizedText = normalizer.normalize(result.text)
                taskDao.setRecognizedText(dayDate, result.slotIndex, taskPageIndex, normalizedText)
            } else {
                // Clear stale recognizedText for empty slots
                taskDao.setRecognizedText(dayDate, result.slotIndex, taskPageIndex, null)
            }
        }

        // Build combined text from ALL sources (typed + recognized)
        // This ensures semantic index includes both typed and handwritten content
        val combinedText = buildCombinedTaskPageText(dayDate, taskPageIndex)

        if (combinedText.isBlank()) {
            val ocrId = com.boox.day.data.db.UnifiedOcrPage.generateId(OcrSource.TASK, dayDate, pageId)
            ocrRepository.deleteOcrForPage(ocrId)
            Log.d(TAG, "No text content for task page $dayDate/$taskPageIndex; cleared OCR")
            return
        }

        // Calculate confidence from handwriting results (typed text has implicit 1.0)
        val nonEmptyResults = results.filter { !it.isEmpty }
        val avgConfidence = if (nonEmptyResults.isNotEmpty()) {
            nonEmptyResults.map { it.confidence }.average().toFloat()
        } else {
            1.0f  // All typed text
        }

        Log.i(TAG, "Processing task page $pageId: ${nonEmptyResults.size} slots, " +
                "${combinedText.length} chars, conf=${String.format("%.2f", avgConfidence)}")

        try {
            // Full 12-step semantic extraction (upgraded from MVP)
            // SemanticGeniusService handles short text internally via extractSemanticsShortText
            val semanticBundle = semanticGeniusService.extractSemantics(
                text = combinedText,
                sourceType = OcrSource.TASK,
                sourceTitle = "Tasks - $dayDate",
                sourceCreatedAt = System.currentTimeMillis()
            )

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
                sourceModifiedAt = System.currentTimeMillis(),
                text = combinedText,
                confidence = avgConfidence,
                language = "en",  // Digital Ink is English-only for MVP
                semanticBundle = semanticBundle,
                sourceTitle = "Tasks - $dayDate",
                sourcePath = null,
                sourceDate = dayDate
            )

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Stored task text for $pageId (${elapsed}ms)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process task page $pageId: ${e.message}", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build combined text from all slots in a task page.
     *
     * For each slot, uses typedText if available,
     * otherwise falls back to recognizedText (from handwriting).
     *
     * @param dayDate Day date in "yyyy-MM-dd" format
     * @param taskPageIndex Task page index (0, 1, 2...)
     * @return Combined text from all non-empty slots, one line per slot
     */
    private fun buildCombinedTaskPageText(dayDate: String, taskPageIndex: Int): String {
        val tasks = taskDao.getTasksForPage(dayDate, taskPageIndex)

        if (tasks.isEmpty()) {
            Log.d(TAG, "No tasks found for $dayDate page $taskPageIndex")
            return ""
        }

        // Use class-level normalizer for typed text (recognizedText already normalized in processResults)
        return tasks
            .sortedBy { it.rowIndex }
            .mapNotNull { task -> getTaskText(task, normalizer) }
            .joinToString("\n")
    }

    /**
     * Get text for a single task, applying normalization to typed text.
     *
     * Priority:
     * 1. typedText (normalized via HandwritingNormalizer)
     * 2. recognizedText (already normalized in processResultsInternal before storing)
     * 3. convertedText (from AI memo conversion)
     *
     * @param task The task to get text from
     * @param normalizer Optional normalizer for typed text (null = no normalization)
     * @return Text content, or null if task has no text
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

        // Priority 2: Recognized text (already normalized in processResultsInternal)
        task.recognizedText?.takeIf { it.isNotBlank() }?.let { return it }

        // Priority 3: Converted text (from AI memo conversion)
        task.convertedText?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }
}
