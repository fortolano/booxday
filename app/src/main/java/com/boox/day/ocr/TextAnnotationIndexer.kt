package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.UnifiedOcrPage
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
 * Indexes TextAnnotation content into the semantic extraction pipeline.
 *
 * Responsibilities:
 * - Debounces rapid updates (500ms wait after last change)
 * - Extracts semantics via SemanticGeniusService
 * - Stores results in unified_ocr_text via UnifiedOcrRepository
 *
 * Thread Safety: All public methods are thread-safe.
 *
 * Usage:
 * ```
 * // After textAnnotationRepository.insert(annotation):
 * TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)
 * ```
 *
 * Added Jan 2026: Makes TextAnnotations searchable via semantic search.
 */
class TextAnnotationIndexer private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TextAnnotationIndexer"
        private const val DEBOUNCE_MS = 500L

        @Volatile
        private var instance: TextAnnotationIndexer? = null

        /**
         * Get or create the singleton instance.
         */
        fun getInstance(context: Context, scope: CoroutineScope): TextAnnotationIndexer {
            return instance ?: synchronized(this) {
                instance ?: TextAnnotationIndexer(context.applicationContext, scope).also {
                    instance = it
                }
            }
        }

        /**
         * Get existing instance, or null if not initialized.
         */
        fun getInstanceOrNull(): TextAnnotationIndexer? = instance
    }

    // Data class for pending indexing requests
    private data class IndexRequest(
        val annotationId: String,
        val pageId: String,
        val content: String
    )

    // Pending indexing events
    private val pendingIndexRequests = MutableSharedFlow<IndexRequest>(extraBufferCapacity = 20)

    // Services (lazy initialized)
    private val dayPageRepository: DayPageRepository by lazy { DayPageRepository(context) }
    private val ocrRepository: UnifiedOcrRepository by lazy { UnifiedOcrRepository(context) }
    private val semanticGeniusService: SemanticGeniusService by lazy { SemanticGeniusService(context) }

    init {
        startProcessingLoop()
    }

    /**
     * Notify that a TextAnnotation's content has changed.
     * Call this ONLY when content changes, not for position/style updates.
     *
     * @param annotation The annotation that was created or modified
     */
    fun onAnnotationChanged(annotation: TextAnnotation) {
        if (annotation.content.isBlank()) {
            // Delete from index if content is now empty
            scope.launch(Dispatchers.IO) {
                deleteFromIndex(annotation.id, annotation.pageId)
            }
            return
        }

        Log.d(TAG, "Annotation changed: ${annotation.id.take(8)}... (${annotation.content.length} chars)")
        pendingIndexRequests.tryEmit(
            IndexRequest(annotation.id, annotation.pageId, annotation.content)
        )
    }

    /**
     * Remove an annotation from the semantic index.
     * Call when annotation is deleted.
     *
     * @param annotationId The ID of the deleted annotation
     * @param pageId The page ID the annotation was on
     */
    fun onAnnotationDeleted(annotationId: String, pageId: String) {
        scope.launch(Dispatchers.IO) {
            deleteFromIndex(annotationId, pageId)
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
                .collect { request ->
                    processAnnotation(request)
                }
        }
    }

    /**
     * Process an annotation and index its content with semantic extraction.
     */
    private suspend fun processAnnotation(request: IndexRequest) {
        val startTime = System.currentTimeMillis()

        try {
            // Resolve dayDate from pageId
            val dayDate = dayPageRepository.getDateByPageId(request.pageId)
            if (dayDate == null) {
                Log.w(TAG, "Cannot find date for page ${request.pageId} - skipping")
                return
            }

            // Full semantic extraction
            val semanticBundle = semanticGeniusService.extractSemantics(
                text = request.content,
                sourceType = OcrSource.TEXT_ANNOTATION,
                sourceTitle = null,
                sourceCreatedAt = System.currentTimeMillis()
            )

            val now = System.currentTimeMillis()

            // Ensure page record exists before storing result
            // (storeOcrResult uses UPDATE, not INSERT, so page must exist first)
            ocrRepository.markInProgress(
                sourceType = OcrSource.TEXT_ANNOTATION,
                sourceId = dayDate,
                pageId = request.annotationId,  // Use annotation ID as unique identifier
                sourceModifiedAt = now
            )

            // Store using existing repository infrastructure
            ocrRepository.storeOcrResult(
                sourceType = OcrSource.TEXT_ANNOTATION,
                sourceId = dayDate,
                pageId = request.annotationId,
                sourceModifiedAt = now,
                text = request.content,
                confidence = 1.0f,  // Text annotations have perfect confidence
                language = "en",
                semanticBundle = semanticBundle,
                sourceTitle = null,
                sourcePath = null,
                sourceDate = dayDate
            )

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Indexed annotation ${request.annotationId.take(8)}... (${elapsed}ms, " +
                    "domain=${semanticBundle.domain?.name ?: "null"})")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to index annotation ${request.annotationId}: ${e.message}", e)
        }
    }

    /**
     * Delete an annotation from the semantic index.
     */
    private suspend fun deleteFromIndex(annotationId: String, pageId: String) {
        try {
            val dayDate = dayPageRepository.getDateByPageId(pageId)
            if (dayDate == null) {
                Log.w(TAG, "Cannot find date for page $pageId during delete - skipping")
                return
            }

            val ocrId = UnifiedOcrPage.generateId(OcrSource.TEXT_ANNOTATION, dayDate, annotationId)
            ocrRepository.deleteOcrForPage(ocrId)
            Log.d(TAG, "Deleted from index: ${annotationId.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete annotation $annotationId from index: ${e.message}", e)
        }
    }
}
