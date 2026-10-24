package com.boox.day.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.utils.TaggedLog
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private val log = TaggedLog("AiSummarizationService")

/**
 * AI-based summarization service for handwritten notes.
 *
 * Uses a staged pipeline:
 * 1. **Stage 0**: Capture strokes to bitmap (JPEG)
 * 2. **Stage 1**: Vision OCR extracts text from strokes
 * 3. **Stage 1.5**: TextInferenceService cleans likely OCR errors
 * 4. **Stage 2**: Text model summarizes the cleaned text
 *
 * The summarization prompt is designed to:
 * - Extract WHO, WHERE, WHEN (key entities)
 * - Identify action items and decisions
 * - Produce structured output (bullets, numbered lists)
 * - Leverage reasoning for better understanding
 *
 * Models:
 * - Primary: Nova 2 Lite (vision + reasoning)
 * - Fallback: Gemma 3 27B (vision)
 */
object AiSummarizationService {

    /**
     * Processing states for UI feedback (single page)
     */
    sealed class SummarizationState {
        data object Idle : SummarizationState()
        data object CapturingImage : SummarizationState()
        data object Analyzing : SummarizationState()
        data class Success(val summary: String) : SummarizationState()
        data class NoContent(val message: String) : SummarizationState()
        data class Error(val message: String) : SummarizationState()
    }

    /**
     * Processing states for multi-page summarization.
     * Provides granular progress feedback for UI.
     */
    sealed class MultiPageSummarizationState {
        data object Idle : MultiPageSummarizationState()
        data class ProcessingPage(val current: Int, val total: Int) : MultiPageSummarizationState()
        data class AggregatingResults(val pageCount: Int) : MultiPageSummarizationState()
        data class Success(val finalSummary: String) : MultiPageSummarizationState()
        data class NoContent(val message: String) : MultiPageSummarizationState()
        data class Error(val message: String) : MultiPageSummarizationState()
    }

    /**
     * Data for a single page to be processed in multi-page summarization.
     */
    data class PageSummarizationData(
        val pageId: String,
        val pageIndex: Int,
        val strokes: List<Stroke>,
        val textAnnotations: List<TextAnnotation>
    ) {
        val hasContent: Boolean get() = strokes.isNotEmpty() || textAnnotations.isNotEmpty()
    }

    /**
     * Result of summarization
     */
    data class SummarizationResult(
        val summary: String,
        val wasSuccessful: Boolean,
        val errorMessage: String? = null,
        val strokeIds: List<String> = emptyList(),
        val textAnnotationIds: List<String> = emptyList()
    )

    /**
     * Content to be summarized - can include strokes, text annotations, or both.
     */
    data class SummarizationContent(
        val strokes: List<Stroke>,
        val textAnnotations: List<TextAnnotation>
    ) {
        val hasStrokes: Boolean get() = strokes.isNotEmpty()
        val hasText: Boolean get() = textAnnotations.isNotEmpty()
        val hasContent: Boolean get() = hasStrokes || hasText
        val strokeCount: Int get() = strokes.size
        val textBlockCount: Int get() = textAnnotations.size
    }

    private const val BITMAP_PADDING = 30f
    private const val MIN_BITMAP_SIZE = 100
    private const val MAX_TEXT_LENGTH = 8000

    // Delimiters for parsing AI responses
    private const val START_SUMMARY_DELIMITER = "<<<START_SUMMARY>>>"
    private const val END_SUMMARY_DELIMITER = "<<<END_SUMMARY>>>"
    private const val START_OCR_DELIMITER = "<<<START_TEXT>>>"
    private const val END_OCR_DELIMITER = "<<<END_TEXT>>>"
    private const val NO_TEXT_MARKER = "<<<NO_TEXT>>>"

    /**
     * Smart summarization prompt - produces markdown formatted output.
     * Uses bold headers and proper bullet formatting for rich text display.
     */
    private val SUMMARIZATION_PROMPT = """
You are an expert at analyzing handwritten notes and extracting key information.

ANALYZE THE IMAGE:
1. Read ALL handwritten text carefully.
2. Identify the type of content (meeting notes, to-do list, brainstorm, journal, etc.).
3. Extract: WHO (people), WHEN (dates/times), WHERE (locations).
4. Identify main topics and any action items.

OUTPUT FORMAT (use markdown formatting):
<<<START_SUMMARY>>>
**Summary**
[1-2 sentence overview of what this note is about]

**Key Points**
- [Main point 1]
- [Main point 2]
- [Main point 3]

**Context**
- **Who**: [Names or "None mentioned"]
- **When**: [Dates/times or "None mentioned"]
- **Where**: [Locations or "None mentioned"]

**Action Items**
- [Task 1]
- [Task 2]
(or "None identified" if no tasks)
<<<END_SUMMARY>>>

MARKDOWN RULES:
- Use **bold** for section headers (Summary, Key Points, Context, Action Items)
- Use **bold** for labels (Who, When, Where)
- Use "- " for bullet points (NOT "* ")
- Use **bold** for important names, dates, deadlines within content
- Be concise — 3-5 points per section maximum.
- If handwriting is unclear, say "Unclear" instead of inventing content.
- Preserve specific details like numbers and dates.
- Detect the language of the content and produce the summary in the same language.
- Keep the summary under 300 words.

If no readable text: <<<NO_TEXT>>>
    """.trimIndent()

    /**
     * Text-only summarization prompt - for typed text (no vision needed).
     * Uses markdown formatting for rich text display.
     */
    private val TEXT_SUMMARIZATION_PROMPT = """
You are an expert at analyzing notes and extracting key information.

ANALYZE THE TEXT:
1. Read ALL text carefully.
2. Identify the type of content (meeting notes, to-do list, brainstorm, journal, etc.).
3. Extract: WHO (people), WHEN (dates/times), WHERE (locations).
4. Identify main topics and any action items.

OUTPUT FORMAT (use markdown formatting):
<<<START_SUMMARY>>>
**Summary**
[1-2 sentence overview of what this note is about]

**Key Points**
- [Main point 1]
- [Main point 2]
- [Main point 3]

**Context**
- **Who**: [Names or "None mentioned"]
- **When**: [Dates/times or "None mentioned"]
- **Where**: [Locations or "None mentioned"]

**Action Items**
- [Task 1]
- [Task 2]
(or "None identified" if no tasks)
<<<END_SUMMARY>>>

MARKDOWN RULES:
- Use **bold** for section headers (Summary, Key Points, Context, Action Items)
- Use **bold** for labels (Who, When, Where)
- Use "- " for bullet points (NOT "* ")
- Use **bold** for important names, dates, deadlines within content
- Be concise — 3-5 points per section maximum.
- Preserve specific details like numbers and dates.
- If something is unclear, say "Unclear" or "None mentioned" instead of inventing it.
- Detect the language of the content and produce the summary in the same language.
- Keep the summary under 300 words.

If no readable text: <<<NO_TEXT>>>
    """.trimIndent()

    /**
     * Summarize content from strokes AND/OR text annotations.
     *
     * This is the main entry point that supports both handwritten and typed content.
     *
     * @param content Strokes and/or text annotations to summarize
     * @param onStateChange Callback for state updates (for UI progress)
     * @return Result containing the summary or error
     */
    suspend fun summarizeContent(
        content: SummarizationContent,
        onStateChange: (SummarizationState) -> Unit
    ): SummarizationResult {
        if (!content.hasContent) {
            log.i("No content to summarize")
            onStateChange(SummarizationState.NoContent("No content on this page to summarize"))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = "No content on this page"
            )
        }

        log.i("Starting summarization: ${content.strokeCount} strokes, ${content.textBlockCount} text blocks")

        try {
            // Determine summarization strategy based on content type
            return when {
                content.hasStrokes && content.hasText -> {
                    // Both: OCR strokes + extract text, then summarize combined
                    summarizeMixedContent(content, onStateChange)
                }
                content.hasStrokes -> {
                    // Strokes only: OCR -> cleanup -> text summary
                    summarizeStrokesInternal(content.strokes, onStateChange).let { result ->
                        result.copy(strokeIds = content.strokes.map { it.id })
                    }
                }
                else -> {
                    // Text only: extract and summarize directly (no vision needed)
                    summarizeTextOnly(content.textAnnotations, onStateChange)
                }
            }
        } catch (e: Exception) {
            log.e("Summarization failed: ${e.message}", e)
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network."
                is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                else -> "Summarization failed: ${e.message}"
            }
            onStateChange(SummarizationState.Error(errorMessage))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = errorMessage
            )
        }
    }

    /**
     * Summarize text-only content (no strokes, faster).
     */
    private suspend fun summarizeTextOnly(
        textAnnotations: List<TextAnnotation>,
        onStateChange: (SummarizationState) -> Unit
    ): SummarizationResult {
        onStateChange(SummarizationState.Analyzing)
        log.d("Summarizing ${textAnnotations.size} text blocks (text-only mode)")

        // Extract all text content
        val combinedText = textAnnotations
            .filter { it.content.isNotBlank() }
            .joinToString("\n\n") { it.content }

        if (combinedText.isBlank()) {
            onStateChange(SummarizationState.NoContent("No readable text found"))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = "No readable text found"
            )
        }

        log.d("Combined text: ${combinedText.length} chars")

        // Use text-only model for speed
        val result = executeTextSummarization(combinedText)

        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Summarization failed"
            log.e("Text summarization failed: $error")
            onStateChange(SummarizationState.Error(error))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = error
            )
        }

        val summary = result.getOrThrow()
        log.i("Text summarization complete: ${summary.length} chars")
        onStateChange(SummarizationState.Success(summary))

        return SummarizationResult(
            summary = summary,
            wasSuccessful = true,
            textAnnotationIds = textAnnotations.map { it.id }
        )
    }

    /**
     * Summarize mixed content (both strokes and text annotations).
     */
    private suspend fun summarizeMixedContent(
        content: SummarizationContent,
        onStateChange: (SummarizationState) -> Unit
    ): SummarizationResult {
        onStateChange(SummarizationState.CapturingImage)
        log.d("Summarizing mixed content: ${content.strokeCount} strokes + ${content.textBlockCount} text blocks")

        val extractedTexts = mutableListOf<String>()

        // 1. OCR strokes
        val bitmap = captureStrokesToBitmap(content.strokes)
        val base64Image = bitmapToBase64(bitmap)
        bitmap.recycle()

        onStateChange(SummarizationState.Analyzing)

        val ocrResult = extractAndRefineStrokeTextForSummarization(base64Image)
        // Why reading the handwriting failed (no Wi-Fi, no credit, ...), if it did
        var readFailure: String? = null
        if (ocrResult.isSuccess) {
            val ocrText = ocrResult.getOrThrow()
            if (ocrText.isNotBlank()) {
                extractedTexts.add(ocrText)
                log.d("OCR extracted and cleaned: ${ocrText.length} chars")
            }
        } else {
            readFailure = ocrResult.exceptionOrNull()?.message
            log.w("OCR failed ($readFailure), continuing with text annotations only")
        }

        // 2. Extract text from annotations
        content.textAnnotations
            .filter { it.content.isNotBlank() }
            .forEach { extractedTexts.add(it.content) }

        // Nothing read because reading failed: say why (Oct 2026: said "No readable text found")
        if (extractedTexts.isEmpty() && readFailure != null) {
            onStateChange(SummarizationState.Error(readFailure))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = readFailure
            )
        }

        if (extractedTexts.isEmpty()) {
            onStateChange(SummarizationState.NoContent("No readable text found"))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = "No readable text found"
            )
        }

        // 3. Combine and summarize
        val combinedText = extractedTexts.joinToString("\n\n")
        log.d("Combined text from ${extractedTexts.size} sources: ${combinedText.length} chars")

        val result = executeTextSummarization(combinedText)

        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Summarization failed"
            onStateChange(SummarizationState.Error(error))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = error
            )
        }

        val summary = result.getOrThrow()
        log.i("Mixed content summarization complete: ${summary.length} chars")
        onStateChange(SummarizationState.Success(summary))

        return SummarizationResult(
            summary = summary,
            wasSuccessful = true,
            strokeIds = content.strokes.map { it.id },
            textAnnotationIds = content.textAnnotations.map { it.id }
        )
    }

    /**
     * Execute text-only summarization (no vision) - uses 3×3 retry via AiRetryExecutor.
     */
    private suspend fun executeTextSummarization(text: String): Result<String> {
        val models = ModelConfigProvider.getTextFallbackChain()
        val truncatedText = if (text.length > MAX_TEXT_LENGTH) {
            log.w("Text truncated from ${text.length} to $MAX_TEXT_LENGTH chars")
            text.take(MAX_TEXT_LENGTH) + "\n...[content truncated]"
        } else text

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(TEXT_SUMMARIZATION_PROMPT)
                    userMessage("Summarize the following notes:\n\n$truncatedText")
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                val summary = parseDelimitedText(response.data)
                if (summary != null && summary.isNotBlank()) {
                    log.i("Text summarization ${model.id} succeeded: ${summary.length} chars")
                    summary
                } else if (response.data.contains(NO_TEXT_MARKER)) {
                    ""  // Empty but valid
                } else {
                    // Use full response if no delimiters
                    log.w("No delimiters, using full response")
                    response.data.trim()
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Simple OCR for mixed content summarization - uses 3×3 retry via AiRetryExecutor.
     */
    private suspend fun executeOcrForSummarization(base64Image: String): Result<String> {
        val ocrPrompt = """
Read all handwritten text in this image and return only the extracted text.

OUTPUT:
- Return raw JSON only.
- If text exists, return {"hasText": true, "text": "..."}.
- If no readable text exists, return {"hasText": false, "text": ""}.

RULES:
- Preserve line breaks and list structure when clear.
- Use [unclear] for unreadable words instead of guessing.
- Do not summarize, explain, or format beyond the extracted text.
        """.trimIndent()

        // Use server-controlled vision models
        val models = ModelConfigProvider.getVisionFallbackChain()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(ocrPrompt)
                    userMessageWithImage("Read the handwritten text:", base64Image)
                    jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextSchema)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, _ ->
                val extracted = parseStructuredOcrText(response.data)
                    ?: parseOcrDelimitedText(response.data)
                    ?: if (response.data.contains(NO_TEXT_MARKER)) "" else response.data.trim()
                extracted.ifBlank { "" }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    private suspend fun extractAndRefineStrokeTextForSummarization(base64Image: String): Result<String> {
        val ocrResult = executeOcrForSummarization(base64Image)
        if (ocrResult.isFailure) {
            return Result.failure(ocrResult.exceptionOrNull() ?: Exception("OCR failed"))
        }

        val rawText = ocrResult.getOrThrow()
        if (rawText.isBlank()) {
            return Result.success("")
        }

        val inferenceResult = TextInferenceService.refineNoteText(rawText)
        if (inferenceResult.wasModified) {
            log.d("Summarization OCR cleanup refined text: ${inferenceResult.refinedText.take(100)}...")
        }
        return Result.success(inferenceResult.refinedText)
    }

    /**
     * Internal method for stroke-only summarization.
     */
    private suspend fun summarizeStrokesInternal(
        strokes: List<Stroke>,
        onStateChange: (SummarizationState) -> Unit
    ): SummarizationResult {
        // Stage 0: Capture strokes to bitmap
        onStateChange(SummarizationState.CapturingImage)
        log.d("Stage 0: Capturing ${strokes.size} strokes to bitmap")

        val bitmap = captureStrokesToBitmap(strokes)
        val base64Image = bitmapToBase64(bitmap)
        bitmap.recycle()

        log.d("Stage 0 complete: Bitmap encoded (${base64Image.length} chars)")

        // Stage 1: OCR + cleanup, then summarize with text model
        onStateChange(SummarizationState.Analyzing)
        log.d("Stage 1: Extracting and cleaning text for summarization")

        val extractedTextResult = extractAndRefineStrokeTextForSummarization(base64Image)

        if (extractedTextResult.isFailure) {
            val error = extractedTextResult.exceptionOrNull()?.message ?: "Text extraction failed"
            log.e("Stage 1 failed: $error")
            onStateChange(SummarizationState.Error(error))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = error
            )
        }

        val extractedText = extractedTextResult.getOrThrow()

        // Check for "no text" response
        if (extractedText.isBlank() || extractedText.contains(NO_TEXT_MARKER)) {
            log.i("Stage 1: No readable text detected")
            onStateChange(SummarizationState.NoContent("No readable text found on this page"))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = "No readable text found"
            )
        }

        val summaryResult = executeTextSummarization(extractedText)
        if (summaryResult.isFailure) {
            val error = summaryResult.exceptionOrNull()?.message ?: "Summarization failed"
            log.e("Stage 2 failed: $error")
            onStateChange(SummarizationState.Error(error))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = error
            )
        }

        val summary = summaryResult.getOrThrow()
        log.i("Summarization complete: ${summary.length} chars")
        onStateChange(SummarizationState.Success(summary))

        return SummarizationResult(
            summary = summary,
            wasSuccessful = true
        )
    }

    /**
     * Execute summarization with fallback model support - uses 3×3 retry via AiRetryExecutor.
     */
    private suspend fun executeWithFallback(base64Image: String): Result<String> {
        // Use server-controlled vision models
        val models = ModelConfigProvider.getVisionFallbackChain()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(SUMMARIZATION_PROMPT)
                    userMessageWithImage("Analyze and summarize the handwritten content in this image:", base64Image)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                log.i("${model.id} SUCCESS - Response: ${response.data.length} chars")
                val summary = parseDelimitedText(response.data)
                if (summary != null && summary.isNotBlank()) {
                    log.i("Parsed summary between delimiters: ${summary.length} chars")
                    summary
                } else if (response.data.contains(NO_TEXT_MARKER)) {
                    ""  // Empty but valid
                } else {
                    // Use full response if no delimiters found
                    log.w("No delimiters found, using full response")
                    response.data.trim().ifBlank { null }  // null triggers retry if blank
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Parse text between START_SUMMARY and END_SUMMARY delimiters
     */
    private fun parseDelimitedText(response: String): String? {
        val startIdx = response.indexOf(START_SUMMARY_DELIMITER)
        val endIdx = response.indexOf(END_SUMMARY_DELIMITER)

        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            response.substring(startIdx + START_SUMMARY_DELIMITER.length, endIdx).trim()
        } else {
            null
        }
    }

    private fun parseOcrDelimitedText(response: String): String? {
        val startIdx = response.indexOf(START_OCR_DELIMITER)
        val endIdx = response.indexOf(END_OCR_DELIMITER)

        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            response.substring(startIdx + START_OCR_DELIMITER.length, endIdx).trim()
        } else {
            null
        }
    }

    private fun parseStructuredOcrText(response: String): String? {
        return try {
            val cleanJson = JsonExtractor.extract(response)
            val json = JSONObject(cleanJson)
            if (!json.optBoolean("hasText", true)) {
                ""
            } else {
                json.optString("text", "").trim()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Capture strokes to a bitmap for vision model processing.
     * Reuses the same logic as AiHandwritingService.
     */
    private fun captureStrokesToBitmap(strokes: List<Stroke>): Bitmap {
        val bounds = calculateBoundingBox(strokes)

        log.d("Original bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")

        // Add padding
        bounds.inset(-BITMAP_PADDING, -BITMAP_PADDING)

        // Ensure minimum size
        val width = bounds.width().toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val height = bounds.height().toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        log.d("Creating bitmap: ${width}x${height} for ${strokes.size} strokes")

        // Create bitmap with white background
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Translate canvas so strokes are drawn at correct position
        canvas.translate(-bounds.left, -bounds.top)

        // Draw all strokes
        strokes.forEach { stroke ->
            drawStroke(canvas, stroke, Offset.Zero)
        }

        return bitmap
    }

    /**
     * Calculate the bounding box that encompasses all strokes
     */
    private fun calculateBoundingBox(strokes: List<Stroke>): RectF {
        if (strokes.isEmpty()) {
            return RectF(0f, 0f, MIN_BITMAP_SIZE.toFloat(), MIN_BITMAP_SIZE.toFloat())
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        strokes.forEach { stroke ->
            minX = minOf(minX, stroke.left)
            minY = minOf(minY, stroke.top)
            maxX = maxOf(maxX, stroke.right)
            maxY = maxOf(maxY, stroke.bottom)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Convert bitmap to base64 JPEG string for API submission.
     * Uses JPEG format for better compatibility with vision models.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Page Summarization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Aggregation prompt for combining multiple page summaries into one.
     */
    private val AGGREGATION_PROMPT = """
You are an expert at synthesizing information from multiple note pages.

You will receive summaries from multiple pages of handwritten notes.
Your job is to create ONE cohesive summary that combines the key information.

OUTPUT FORMAT (use markdown formatting):
<<<START_SUMMARY>>>
**Combined Summary**
[2-3 sentences covering the main themes across all pages]

**Key Points**
- [Most important point from all pages]
- [Second most important point]
- [Third most important point]
(prioritize by importance, max 5 points)

**People & Places**
- **Who**: [All names mentioned across pages]
- **Where**: [All locations mentioned]

**Action Items**
- [All action items from all pages, consolidated and deduplicated]

**Timeline**
- [Any dates, deadlines, or time-sensitive items mentioned]
<<<END_SUMMARY>>>

RULES:
- Deduplicate similar points
- Prioritize by importance, not by page order
- Keep the summary concise (under 500 words)
- Use **bold** for headers and important terms
- Use "- " for bullet points
    """.trimIndent()

    /**
     * Summarize multiple pages sequentially, then aggregate results.
     *
     * Strategy:
     * 1. Process each page individually (sequential to avoid API rate limits)
     * 2. Collect per-page summaries
     * 3. Final LLM pass to synthesize into cohesive result
     *
     * @param pages List of pages with their content
     * @param onStateChange Callback for progress updates
     * @return Result containing the final aggregated summary
     */
    suspend fun summarizeMultiplePages(
        pages: List<PageSummarizationData>,
        onStateChange: (MultiPageSummarizationState) -> Unit
    ): SummarizationResult {
        // Filter to pages with content
        val pagesWithContent = pages.filter { it.hasContent }

        if (pagesWithContent.isEmpty()) {
            log.i("No pages with content to summarize")
            onStateChange(MultiPageSummarizationState.NoContent("No content found on any page"))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = "No content found on any page"
            )
        }

        log.i("Starting multi-page summarization: ${pagesWithContent.size} pages with content")

        val perPageSummaries = mutableListOf<String>()
        val allStrokeIds = mutableListOf<String>()
        val allTextAnnotationIds = mutableListOf<String>()

        try {
            // Process each page sequentially
            pagesWithContent.forEachIndexed { index, page ->
                onStateChange(MultiPageSummarizationState.ProcessingPage(index + 1, pagesWithContent.size))
                log.d("Processing page ${index + 1}/${pagesWithContent.size}: ${page.strokes.size} strokes, ${page.textAnnotations.size} text blocks")

                val content = SummarizationContent(page.strokes, page.textAnnotations)
                val result = summarizeContent(content) { /* Ignore internal state changes */ }

                if (result.wasSuccessful && result.summary.isNotBlank()) {
                    perPageSummaries.add("Page ${page.pageIndex + 1}:\n${result.summary}")
                    allStrokeIds.addAll(result.strokeIds)
                    allTextAnnotationIds.addAll(result.textAnnotationIds)
                    log.d("Page ${index + 1} summarized: ${result.summary.length} chars")
                } else {
                    log.w("Page ${index + 1} had no extractable content, skipping")
                }
            }

            // Check if we got any summaries
            if (perPageSummaries.isEmpty()) {
                log.i("No readable content found across all pages")
                onStateChange(MultiPageSummarizationState.NoContent("No readable content found on any page"))
                return SummarizationResult(
                    summary = "",
                    wasSuccessful = false,
                    errorMessage = "No readable content found"
                )
            }

            // If only one page, return its summary directly
            if (perPageSummaries.size == 1) {
                val singleSummary = perPageSummaries.first().substringAfter(":\n")
                log.i("Single page summary: ${singleSummary.length} chars")
                onStateChange(MultiPageSummarizationState.Success(singleSummary))
                return SummarizationResult(
                    summary = singleSummary,
                    wasSuccessful = true,
                    strokeIds = allStrokeIds,
                    textAnnotationIds = allTextAnnotationIds
                )
            }

            // Aggregate multiple summaries into one
            onStateChange(MultiPageSummarizationState.AggregatingResults(perPageSummaries.size))
            log.d("Aggregating ${perPageSummaries.size} page summaries")

            val aggregatedSummary = aggregateSummaries(perPageSummaries)

            if (aggregatedSummary.isFailure) {
                val error = aggregatedSummary.exceptionOrNull()?.message ?: "Aggregation failed"
                log.e("Summary aggregation failed: $error")
                onStateChange(MultiPageSummarizationState.Error(error))
                return SummarizationResult(
                    summary = "",
                    wasSuccessful = false,
                    errorMessage = error
                )
            }

            val finalSummary = aggregatedSummary.getOrThrow()
            log.i("Multi-page summarization complete: ${finalSummary.length} chars from ${perPageSummaries.size} pages")
            onStateChange(MultiPageSummarizationState.Success(finalSummary))

            return SummarizationResult(
                summary = finalSummary,
                wasSuccessful = true,
                strokeIds = allStrokeIds,
                textAnnotationIds = allTextAnnotationIds
            )

        } catch (e: Exception) {
            log.e("Multi-page summarization failed: ${e.message}", e)
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network."
                is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                else -> "Summarization failed: ${e.message}"
            }
            onStateChange(MultiPageSummarizationState.Error(errorMessage))
            return SummarizationResult(
                summary = "",
                wasSuccessful = false,
                errorMessage = errorMessage
            )
        }
    }

    /**
     * Aggregate multiple per-page summaries into one cohesive summary.
     * Uses text model for speed (no vision needed).
     */
    private suspend fun aggregateSummaries(perPageSummaries: List<String>): Result<String> {
        val combinedInput = perPageSummaries.joinToString("\n\n---\n\n")
        log.d("Aggregating summaries: ${combinedInput.length} chars total input")

        val models = ModelConfigProvider.getTextFallbackChain()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(AGGREGATION_PROMPT)
                    userMessage("Synthesize these page summaries into one cohesive summary:\n\n$combinedInput")
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                val aggregated = parseDelimitedText(response.data)
                if (aggregated != null && aggregated.isNotBlank()) {
                    log.i("Aggregation with ${model.id} succeeded: ${aggregated.length} chars")
                    aggregated
                } else {
                    // Use full response if no delimiters
                    log.w("No delimiters in aggregation response, using full text")
                    response.data.trim().ifBlank { null }
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }
}
