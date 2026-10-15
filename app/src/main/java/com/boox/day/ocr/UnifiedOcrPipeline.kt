package com.boox.day.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ocr.source.OcrBitmapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Unified OCR processing pipeline for all content sources.
 *
 * This is the SOLE OCR engine. Since Oct 2026 an AI model reads each page on the user's own
 * OpenRouter key ([OpenRouterOcrService], Gemini 3.1 Flash Lite); Google Cloud Vision
 * (the removed CloudVisionService, a key built into the app) is no longer used. Pages indexed before keep
 * their text.
 *
 * Pipeline steps:
 * 1. Check quota (the page budget is effectively unlimited) and the monthly AI read limit
 * 2. Render bitmap from source (Boox Notes, Memo, Task)
 * 3. Preprocess bitmap (downscale, grayscale)
 * 4. AI page read (no key or no credit pauses indexing; it is not a page failure)
 * 5. Semantic extraction (12-step pipeline)
 * 6. Store results in UnifiedOcrText
 * 7. Record the new or re-indexed page (logged only, no page budget) and the AI read
 *
 * CRITICAL: Always recycle bitmaps after use to prevent OOM.
 */
class UnifiedOcrPipeline(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedOcrPipeline"

        /** AI page read timeout (network call plus the model reading the page) */
        private const val AI_READ_TIMEOUT_MS = 90_000L

        /** Target width for bitmap preprocessing (balance quality vs speed) */
        private const val TARGET_WIDTH = 1400

        /** Fail count threshold for quarantine (A-SHORT-3: Zombie cleanup) */
        private const val QUARANTINE_THRESHOLD = 3

        // Note: the bitmap grace period comes from OcrQuotaConfig.bitmapGracePeriodMs
        // (local default: 30000ms), read through OcrQuotaManager
    }

    private val openRouterOcrService by lazy { OpenRouterOcrService() }
    private val booxNoteRepository by lazy { com.boox.day.data.db.BooxNoteRepository(context) }
    private val ocrRepository by lazy { UnifiedOcrRepository(context) }
    private val quotaManager by lazy { OcrQuotaManager(context) }

    /** Grace period for bitmap persistence (local default: 30 seconds) */
    private val bitmapGracePeriodMs: Long
        get() = quotaManager.bitmapGracePeriodMs

    /**
     * SemanticGeniusService for unified semantic extraction and cache invalidation.
     * Lazily initialized on first use.
     */
    private var _semanticGeniusService: SemanticGeniusService? = null

    private fun getSemanticService(): SemanticGeniusService {
        return _semanticGeniusService ?: SemanticGeniusService(context).also {
            _semanticGeniusService = it
        }
    }

    private suspend fun invalidateBooxNoteAggregateIfNeeded(sourceType: OcrSource, sourceId: String) {
        if (sourceType == OcrSource.BOOX_NOTE) {
            booxNoteRepository.deleteSemanticAggregate(sourceId)
        }
    }

    /**
     * Public accessor for cache invalidation (backward compatibility).
     */
    var semanticGeniusService: SemanticGeniusService?
        get() = _semanticGeniusService
        set(value) { _semanticGeniusService = value }

    /**
     * OCR result from the pipeline.
     */
    data class OcrResult(
        val text: String,
        val confidence: Float,
        val language: String?,
        val blocks: Int
    )

    /**
     * Result of processPage indicating what happened.
     */
    enum class ProcessResult {
        /** Page processed successfully */
        SUCCESS,
        /** Page was empty (no bitmap) - not a failure */
        EMPTY,
        /** Processing failed but should be retried later */
        FAILED,
        /** Processing failed too many times and was quarantined */
        QUARANTINED,
        /** Budget exhausted - page queued for later */
        BUDGET_EXHAUSTED,
        /** Bitmap not ready yet (recently modified page) - will retry later */
        BITMAP_NOT_READY
    }

    suspend fun processPage(
        source: OcrBitmapSource,
        sourceId: String,
        pageId: String,
        sourceModified: Long,
        sourceDate: String? = null,
        sourceTitle: String? = null
    ): Boolean {
        return processPageWithResult(source, sourceId, pageId, sourceModified, sourceDate, sourceTitle)
            .let { it == ProcessResult.SUCCESS || it == ProcessResult.EMPTY || it == ProcessResult.BITMAP_NOT_READY }
    }

    private suspend fun handleRetryableFailure(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        error: String?
    ): ProcessResult {
        val failCount = ocrRepository.markFailedWithRetry(sourceType, sourceId, pageId, error)
        return if (failCount >= QUARANTINE_THRESHOLD) {
            ocrRepository.quarantinePage(sourceType, sourceId, pageId, failCount, error)
            ProcessResult.QUARANTINED
        } else {
            ProcessResult.FAILED
        }
    }

    /**
     * Process a single page from any source with detailed result.
     *
     * @param source The bitmap source to use
     * @param sourceId Parent identifier (noteId, date)
     * @param pageId Page identifier
     * @param sourceModified Source modification timestamp
     * @param sourceDate Optional date for the content (for search filtering)
     * @param sourceTitle Optional title for the source
     * @return ProcessResult indicating what happened
     */
    suspend fun processPageWithResult(
        source: OcrBitmapSource,
        sourceId: String,
        pageId: String,
        sourceModified: Long,
        sourceDate: String? = null,
        sourceTitle: String? = null
    ): ProcessResult {
        val start = System.currentTimeMillis()
        var bitmap: Bitmap? = null

        // Step 1: Check if this is a new page (costs budget) or re-index (free)
        val isNewPage = ocrRepository.isNewPage(source.sourceType, sourceId, pageId)

        // Step 2: For new pages, check budget availability (source-type aware)
        if (isNewPage && !quotaManager.canIndexNewPage(source.sourceType)) {
            Log.w(TAG, "Budget exhausted for ${source.sourceType.name}, skipping new page $sourceId/$pageId " +
                "(remaining: ${quotaManager.getRemainingBudget()})")
            return ProcessResult.BUDGET_EXHAUSTED
        }

        // Step 2b: AI reads run on the user's own OpenRouter key and credit (Oct 2026): without a
        // key, or past this month's read limit, indexing waits (nothing marked, nothing failed)
        withContext(Dispatchers.IO) { com.boox.day.security.UserOpenRouterKey.ensureLoaded() }
        if (!com.boox.day.security.UserOpenRouterKey.hasKey) {
            Log.d(TAG, "No OpenRouter key - indexing waits")
            return ProcessResult.BUDGET_EXHAUSTED
        }
        if (!quotaManager.canReadWithAi()) {
            Log.w(TAG, "Monthly AI read limit reached (${quotaManager.getAiReadsThisMonth()}/${quotaManager.monthlyAiReadLimit})")
            return ProcessResult.BUDGET_EXHAUSTED
        }

        try {
            // Step 3: Mark as in-progress (crash recovery marker)
            // This also creates the page record if it doesn't exist (UPSERT)
            ocrRepository.markInProgress(source.sourceType, sourceId, pageId, sourceModified)

            // Step 4: Render the page to bitmap
            bitmap = source.renderPage(sourceId, pageId)
            if (bitmap == null) {
                // Check if page was recently modified - bitmap may still be saving to disk
                val pageAgeMs = System.currentTimeMillis() - sourceModified
                if (pageAgeMs < bitmapGracePeriodMs) {
                    // Recent page without bitmap - likely still being persisted
                    // Reset to PENDING so it gets retried later
                    Log.d(TAG, "No bitmap for recent page $sourceId/$pageId (age=${pageAgeMs}ms) - will retry later")
                    ocrRepository.resetToPending(source.sourceType, sourceId, pageId)
                    return ProcessResult.BITMAP_NOT_READY
                }

                // Page is old enough - truly empty (no content was ever drawn)
                Log.d(TAG, "No bitmap for page $sourceId/$pageId - marking as EMPTY")
                ocrRepository.storeOcrResult(
                    sourceType = source.sourceType,
                    sourceId = sourceId,
                    pageId = pageId,
                    sourceModifiedAt = sourceModified,
                    text = null,
                    confidence = 0f,
                    language = null,
                    semanticBundle = null,
                    sourceDate = sourceDate
                )
                invalidateBooxNoteAggregateIfNeeded(source.sourceType, sourceId)

                // Empty pages do NOT consume quota - no content to index
                // They're still tracked in the database to avoid re-processing
                Log.d(TAG, "Empty page - no quota consumed")

                return ProcessResult.EMPTY
            }

            // Step 5: Preprocess bitmap for better OCR
            val processedBitmap = preprocessBitmap(bitmap)
            if (processedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = processedBitmap
            }

            // Step 6: AI page read on the user's OpenRouter key
            val ocrResult = when (val read = executeAiRead(bitmap)) {
                is OpenRouterOcrService.Result.Success -> {
                    quotaManager.recordAiRead()
                    OcrResult(text = read.text, confidence = read.confidence, language = read.language, blocks = read.blocks)
                }
                is OpenRouterOcrService.Result.Paused -> {
                    // No key, rejected key or no credit: wait, without counting a page failure
                    Log.w(TAG, "AI read paused (${read.reason}) - $sourceId/$pageId stays queued")
                    ocrRepository.resetToPending(source.sourceType, sourceId, pageId)
                    return ProcessResult.BUDGET_EXHAUSTED
                }
                is OpenRouterOcrService.Result.Failed -> {
                    return handleRetryableFailure(
                        sourceType = source.sourceType,
                        sourceId = sourceId,
                        pageId = pageId,
                        error = "AI read failed: ${read.reason}"
                    )
                }
            }

            // Step 7: Run semantic extraction when the OCR config allows it.
            val semanticBundle = if (!quotaManager.isSemanticExtractionEnabled) {
                if (ocrResult.text.isNotBlank()) {
                    Log.d(TAG, "Semantic extraction disabled - storing OCR text only (${quotaManager.describeAppliedConfig()})")
                }
                null
            } else if (ocrResult.text.isNotBlank()) {
                try {
                    // Use unified SemanticGeniusService for extraction
                    getSemanticService().extractSemantics(
                        text = ocrResult.text,
                        sourceType = source.sourceType,
                        sourceTitle = sourceTitle,
                        sourceCreatedAt = sourceModified
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Semantic extraction failed: ${e.message}")
                    null
                }
            } else {
                null
            }

            // Step 8: Store result
            ocrRepository.storeOcrResult(
                sourceType = source.sourceType,
                sourceId = sourceId,
                pageId = pageId,
                sourceModifiedAt = sourceModified,
                text = ocrResult.text.takeIf { it.isNotBlank() },
                confidence = ocrResult.confidence,
                language = ocrResult.language,
                semanticBundle = semanticBundle,
                sourceTitle = sourceTitle,
                sourceDate = sourceDate
            )
            invalidateBooxNoteAggregateIfNeeded(source.sourceType, sourceId)
            // Invalidate SemanticGeniusService caches
            semanticGeniusService?.invalidateProfile(source.sourceType, sourceId, pageId)
            sourceDate?.let { semanticGeniusService?.invalidateDateContext(it) }

            // Step 9: Record quota usage (source-type aware)
            // Only consume quota if the page has actual text content
            val hasContent = ocrResult.text.isNotBlank()
            if (isNewPage && hasContent) {
                quotaManager.recordNewPageIndexed(source.sourceType)
                Log.d(TAG, "Recorded new ${source.sourceType.name} page for quota (${quotaManager.getRemainingBudget()} remaining)")
            } else if (isNewPage) {
                Log.d(TAG, "Empty OCR result - no quota consumed for ${source.sourceType.name}")
            } else {
                quotaManager.recordReIndexed()
            }

            val duration = System.currentTimeMillis() - start
            Log.i(TAG, "PERF: processPage ${source.sourceType.name}/$sourceId/$pageId " +
                "lang=${ocrResult.language} chars=${ocrResult.text.length} new=$isNewPage (${duration}ms)")

            return ProcessResult.SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "Page processing failed: ${e.message}", e)

            try {
                return handleRetryableFailure(
                    sourceType = source.sourceType,
                    sourceId = sourceId,
                    pageId = pageId,
                    error = e.message
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to mark page as failed: ${e2.message}")
            }

            return ProcessResult.FAILED

        } finally {
            // MANDATORY: Recycle bitmap to prevent OOM
            bitmap?.recycle()
        }
    }

    /**
     * Preprocess bitmap for better OCR accuracy.
     * - Downscale to target width if too large
     * - Convert to grayscale for improved OCR recognition
     *
     * @return Processed bitmap (may be same as input if no preprocessing needed)
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        var result = bitmap
        var needsRecycle = false

        try {
            // Step 1: Downscale if larger than target
            if (bitmap.width > TARGET_WIDTH) {
                val scale = TARGET_WIDTH.toFloat() / bitmap.width
                val newWidth = TARGET_WIDTH
                val newHeight = (bitmap.height * scale).toInt()
                result = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                needsRecycle = true
            }

            // Step 2: Convert to grayscale for better OCR
            val grayscale = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayscale)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            canvas.drawBitmap(result, 0f, 0f, paint)

            // Recycle intermediate bitmap if we created one from scaling
            if (needsRecycle && result !== bitmap) {
                result.recycle()
            }

            return grayscale
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preprocess bitmap: ${e.message}")
            return bitmap
        }
    }

    /**
     * Read the page with AI on the user's OpenRouter key ([OpenRouterOcrService]).
     */
    private suspend fun executeAiRead(bitmap: Bitmap): OpenRouterOcrService.Result = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(AI_READ_TIMEOUT_MS) {
                openRouterOcrService.recognizeText(bitmap)
            } ?: OpenRouterOcrService.Result.Failed("timed out").also { Log.w(TAG, "AI read timed out") }
        } catch (e: Exception) {
            Log.e(TAG, "AI read exception: ${e.message}", e)
            OpenRouterOcrService.Result.Failed(e.message ?: "error")
        }
    }

    /**
     * Check if a page needs OCR (either never processed or source changed).
     *
     * @param sourceType The content source type
     * @param sourceId Parent identifier
     * @param pageId Page identifier
     * @param currentModified Current source modification timestamp
     * @return true if OCR should be run
     */
    suspend fun needsOcr(
        sourceType: OcrSource,
        sourceId: String,
        pageId: String,
        currentModified: Long
    ): Boolean {
        return ocrRepository.needsReOcr(sourceType, sourceId, pageId, currentModified)
    }

    /**
     * Close resources.
     */
    fun close() {
        // OpenRouterOcrService doesn't need explicit cleanup
    }
}
