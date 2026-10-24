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

private val log = TaggedLog("AiTranslationService")

/**
 * AI-based translation service for canvas content.
 *
 * Supports iterative translation of both handwritten strokes AND typed text.
 *
 * ## 3-Stage Pipeline
 *
 * ```
 * Stage 1: Content Extraction
 *   - Strokes → Vision OCR (GPT-5 Nano)
 *   - TextAnnotations → Direct text extraction (no AI needed)
 *
 * Stage 2: Semantic Inference (TextInferenceService)
 *   - Fixes OCR errors from handwriting
 *   - Context: NOTE
 *
 * Stage 3: Translation
 *   - Text-only model (GPT-OSS-20B) for speed
 *   - Or vision model if OCR + translate combined
 * ```
 *
 * ## Iterative Translation Flow
 *
 * 1. User writes "Hello World" (strokes)
 * 2. Translate to Portuguese → "Olá Mundo" (TextAnnotation replaces strokes)
 * 3. User writes more English text (new strokes)
 * 4. Translate to French → Both "Olá Mundo" + new strokes → All translated to French
 */
object AiTranslationService {

    /**
     * Processing states for UI feedback (single page)
     */
    sealed class TranslationState {
        data object Idle : TranslationState()
        data object ExtractingContent : TranslationState()  // Reading strokes + text
        data object Translating : TranslationState()
        data class Success(val translatedText: String, val detectedLanguages: String) : TranslationState()
        data class NoContent(val message: String) : TranslationState()
        data class Error(val message: String) : TranslationState()
    }

    /**
     * Processing states for multi-page translation.
     * Provides granular progress feedback for UI.
     */
    sealed class MultiPageTranslationState {
        data object Idle : MultiPageTranslationState()
        data class ProcessingPage(val current: Int, val total: Int) : MultiPageTranslationState()
        data class Success(val fullTranslation: String, val detectedLanguages: String) : MultiPageTranslationState()
        data class NoContent(val message: String) : MultiPageTranslationState()
        data class Error(val message: String) : MultiPageTranslationState()
    }

    /**
     * Data for a single page to be processed in multi-page translation.
     */
    data class PageTranslationData(
        val pageId: String,
        val pageIndex: Int,
        val strokes: List<Stroke>,
        val textAnnotations: List<TextAnnotation>
    ) {
        val hasContent: Boolean get() = strokes.isNotEmpty() || textAnnotations.isNotEmpty()
    }

    /**
     * Content to be translated - can include strokes, text annotations, or both.
     */
    data class TranslationContent(
        val strokes: List<Stroke>,
        val textAnnotations: List<TextAnnotation>
    ) {
        val hasStrokes: Boolean get() = strokes.isNotEmpty()
        val hasText: Boolean get() = textAnnotations.isNotEmpty()
        val hasContent: Boolean get() = hasStrokes || hasText
        val strokeCount: Int get() = strokes.size
        val textBlockCount: Int get() = textAnnotations.size
    }

    /**
     * Result of translation including IDs for cleanup
     */
    data class TranslationResult(
        val translatedText: String,
        val detectedLanguages: String,
        val wasSuccessful: Boolean,
        val errorMessage: String? = null,
        val strokeIds: List<String> = emptyList(),
        val textAnnotationIds: List<String> = emptyList()
    )

    private const val BITMAP_PADDING = 30f
    private const val MIN_BITMAP_SIZE = 100
    private const val MAX_TEXT_LENGTH = 8000

    private fun truncateForApi(text: String): String {
        return if (text.length > MAX_TEXT_LENGTH) {
            log.w("Text truncated from ${text.length} to $MAX_TEXT_LENGTH chars")
            text.take(MAX_TEXT_LENGTH) + "\n...[content truncated]"
        } else text
    }

    // Delimiters for parsing OCR AI responses
    private const val START_OCR_DELIMITER = "<<<START_TEXT>>>"
    private const val END_OCR_DELIMITER = "<<<END_TEXT>>>"
    private const val NO_TEXT_MARKER = "<<<NO_TEXT>>>"

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Translate all content on the canvas (strokes + text annotations).
     *
     * This is the main entry point for the iterative translation flow.
     *
     * @param content Strokes and/or text annotations to translate
     * @param targetLanguage Target language for translation
     * @param onStateChange Callback for state updates (for UI progress)
     * @return Result containing the translation and IDs of content to delete
     */
    suspend fun translateContent(
        content: TranslationContent,
        targetLanguage: String = "English",
        onStateChange: (TranslationState) -> Unit
    ): TranslationResult {
        // Check for empty content
        if (!content.hasContent) {
            log.i("No content to translate")
            onStateChange(TranslationState.NoContent("No content on this page to translate"))
            return TranslationResult(
                translatedText = "",
                detectedLanguages = "",
                wasSuccessful = false,
                errorMessage = "No content on this page"
            )
        }

        log.i("Starting translation: ${content.strokeCount} strokes, ${content.textBlockCount} text blocks → $targetLanguage")

        try {
            // ═══════════════════════════════════════════════════════════════════
            // STAGE 1: Content Extraction
            // ═══════════════════════════════════════════════════════════════════
            onStateChange(TranslationState.ExtractingContent)

            val extractedTexts = mutableListOf<String>()
            val detectedLanguages = mutableListOf<String>()
            // Why reading the handwriting failed (no Wi-Fi, no credit, ...), if it did
            var readFailure: String? = null

            // 1a. Extract text from strokes via Vision OCR (if any)
            if (content.hasStrokes) {
                log.d("Stage 1a: OCR for ${content.strokeCount} strokes")
                val bitmap = captureStrokesToBitmap(content.strokes)
                val base64Image = bitmapToBase64(bitmap)
                bitmap.recycle()

                val ocrResult = executeOcr(base64Image)
                if (ocrResult.isSuccess) {
                    val (text, lang) = ocrResult.getOrThrow()
                    if (text.isNotBlank()) {
                        val cleanedOcrText = refineExtractedTextForTranslation(text)
                        extractedTexts.add(cleanedOcrText)
                        if (lang.isNotBlank() && lang != "Unknown") {
                            detectedLanguages.add(lang)
                        }
                        log.d("OCR extracted: ${text.length} chars, cleaned=${cleanedOcrText.length} chars, language: $lang")
                    }
                } else {
                    readFailure = ocrResult.exceptionOrNull()?.message
                    log.w("OCR failed: $readFailure")
                    // Continue with text annotations even if OCR fails
                }
            }

            // 1b. Extract text from existing TextAnnotations (direct - no AI needed)
            if (content.hasText) {
                log.d("Stage 1b: Extracting ${content.textBlockCount} text blocks")
                content.textAnnotations.forEach { annotation ->
                    if (annotation.content.isNotBlank()) {
                        extractedTexts.add(annotation.content)
                    }
                }
            }

            // Nothing read because reading failed: say why (Oct 2026: said "No readable text
            // found" even with a page full of handwriting and Wi-Fi off)
            if (extractedTexts.isEmpty() && readFailure != null) {
                onStateChange(TranslationState.Error(readFailure))
                return TranslationResult(
                    translatedText = "",
                    detectedLanguages = "",
                    wasSuccessful = false,
                    errorMessage = readFailure
                )
            }

            // Check if we got any text
            if (extractedTexts.isEmpty()) {
                log.i("No readable text found in content")
                onStateChange(TranslationState.NoContent("No readable text found on this page"))
                return TranslationResult(
                    translatedText = "",
                    detectedLanguages = "",
                    wasSuccessful = false,
                    errorMessage = "No readable text found"
                )
            }

            // Combine all extracted text
            val combinedText = extractedTexts.joinToString("\n\n")
            log.d("Combined text: ${combinedText.length} chars from ${extractedTexts.size} sources")

            val textToTranslate = combinedText

            // ═══════════════════════════════════════════════════════════════════
            // STAGE 3: Translation
            // ═══════════════════════════════════════════════════════════════════
            onStateChange(TranslationState.Translating)
            log.d("Stage 3: Translating to $targetLanguage")

            val translationResult = executeTextTranslation(textToTranslate, targetLanguage)

            if (translationResult.isFailure) {
                val error = translationResult.exceptionOrNull()?.message ?: "Translation failed"
                log.e("Stage 3 failed: $error")
                onStateChange(TranslationState.Error(error))
                return TranslationResult(
                    translatedText = "",
                    detectedLanguages = "",
                    wasSuccessful = false,
                    errorMessage = error
                )
            }

            val (translatedText, detectedLang) = translationResult.getOrThrow()
            if (detectedLang.isNotBlank() && detectedLang != "Unknown") {
                detectedLanguages.add(detectedLang)
            }

            val detectedLanguagesStr = detectedLanguages.distinct().joinToString(", ").ifEmpty { "Unknown" }

            log.i("Translation complete: ${translatedText.length} chars, detected: $detectedLanguagesStr")
            onStateChange(TranslationState.Success(translatedText, detectedLanguagesStr))

            return TranslationResult(
                translatedText = translatedText,
                detectedLanguages = detectedLanguagesStr,
                wasSuccessful = true,
                strokeIds = content.strokes.map { it.id },
                textAnnotationIds = content.textAnnotations.map { it.id }
            )

        } catch (e: Exception) {
            log.e("Translation failed: ${e.message}", e)
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network."
                is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                else -> "Translation failed: ${e.message}"
            }
            onStateChange(TranslationState.Error(errorMessage))
            return TranslationResult(
                translatedText = "",
                detectedLanguages = "",
                wasSuccessful = false,
                errorMessage = errorMessage
            )
        }
    }

    private suspend fun refineExtractedTextForTranslation(rawOcrText: String): String {
        log.d("Stage 2: Semantic inference for OCR cleanup")
        val inferenceResult = TextInferenceService.refineNoteText(rawOcrText)
        if (inferenceResult.wasModified) {
            log.d("Inference corrected OCR text: ${inferenceResult.refinedText.take(100)}...")
        }
        return inferenceResult.refinedText
    }

    /**
     * Legacy method for backwards compatibility.
     * @deprecated Use translateContent() instead for full iterative translation support.
     */
    @Deprecated("Use translateContent() for iterative translation support",
        ReplaceWith("translateContent(TranslationContent(strokes, emptyList()), targetLanguage, onStateChange)"))
    suspend fun translateStrokes(
        strokes: List<Stroke>,
        targetLanguage: String = "English",
        onStateChange: (TranslationState) -> Unit
    ): TranslationResult {
        return translateContent(
            TranslationContent(strokes, emptyList()),
            targetLanguage
        ) { state ->
            // Map new states to old states for compatibility
            val mappedState = when (state) {
                is TranslationState.ExtractingContent -> TranslationState.ExtractingContent
                else -> state
            }
            onStateChange(mappedState)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE 1: OCR (Vision)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * OCR prompt for extracting text from handwriting.
     */
    private fun buildOcrPrompt(): String = """
You extract text from handwritten notes.

TASK:
1. Read all readable text in the image.
2. Detect the language or languages present.
3. Return the extracted text exactly as written.

OUTPUT FORMAT:
- Return raw JSON only.
- If text exists, return {"hasText": true, "language": "detected language", "text": "..."}.
- If no readable text exists, return {"hasText": false, "language": "", "text": ""}.

RULES:
- Do not translate.
- Preserve line breaks and obvious list structure.
- Use [unclear] for unreadable words instead of guessing.
- Do not add markdown, explanations, or cleanup.
    """.trimIndent()

    /**
     * Execute OCR on handwritten strokes image - uses 3×3 retry via AiRetryExecutor.
     */
    private suspend fun executeOcr(base64Image: String): Result<Pair<String, String>> {
        val models = ModelConfigProvider.getVisionFallbackChain()
        val ocrPrompt = buildOcrPrompt()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(ocrPrompt)
                    userMessageWithImage("Read the handwritten text in this image:", base64Image)
                    jsonSchema("ocr_text_with_language", StructuredOutputSchemas.ocrTextWithLanguageSchema)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                val parsed = parseOcrResponse(response.data)
                if (parsed != null && parsed.first.isNotBlank()) {
                    log.i("OCR ${model.id} succeeded: ${parsed.first.length} chars")
                    parsed
                } else if (response.data.contains(NO_TEXT_MARKER)) {
                    Pair("", "")  // Empty but valid
                } else {
                    null  // Triggers retry
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Parse OCR response to extract text and detected language.
     */
    private fun parseOcrResponse(response: String): Pair<String, String>? {
        try {
            val cleanJson = JsonExtractor.extract(response)
            val json = JSONObject(cleanJson)
            val hasText = json.optBoolean("hasText", true)
            val text = json.optString("text", "").trim()
            val language = json.optString("language", "").trim().ifBlank { "Unknown" }

            return if (!hasText) {
                Pair("", "")
            } else if (text.isNotBlank()) {
                Pair(text, language)
            } else {
                null
            }
        } catch (_: Exception) {
            // Fall back to legacy delimiter parsing for older model behavior
        }

        val startIdx = response.indexOf(START_OCR_DELIMITER)
        val endIdx = response.indexOf(END_OCR_DELIMITER)

        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            // Try to extract any text if delimiters not found
            return if (response.isNotBlank() && !response.contains(NO_TEXT_MARKER)) {
                Pair(response.trim(), "Unknown")
            } else null
        }

        val content = response.substring(startIdx + START_OCR_DELIMITER.length, endIdx).trim()

        // Parse language from "LANGUAGE: [lang]" line
        val langRegex = Regex("""LANGUAGE:\s*(.+)""", RegexOption.IGNORE_CASE)
        val match = langRegex.find(content)
        val detectedLanguage = match?.groupValues?.get(1)?.trim() ?: "Unknown"

        // Get the text (everything after the LANGUAGE line)
        val text = if (match != null) {
            content.substring(match.range.last + 1).trim()
        } else {
            content
        }

        return Pair(text, detectedLanguage)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE 3: Translation (Text-only)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Translation prompt for text-only translation.
     * Uses structured JSON output for reliable parsing.
     */
    private fun buildTextTranslationPrompt(targetLanguage: String): String = """
You translate text faithfully.

TASK:
1. Detect the source language or languages.
2. Translate all text into $targetLanguage.
3. Preserve the structure of the original text.

CONTEXT:
- The source text was extracted from handwritten personal notes on an e-ink tablet.
- It may contain informal language, abbreviations, or shorthand.
- Favor natural, idiomatic phrasing over literal word-for-word translation.

RULES:
- Do not add commentary or explanations.
- Keep proper nouns in original form when appropriate.
- If the text is already in $targetLanguage, return it unchanged.
- Preserve paragraphs, bullet lists, and numbering.
- Do not add markdown styling that was not implied by the source structure.
- Return raw JSON only, matching the schema exactly.
    """.trimIndent()

    /**
     * Execute text-only translation (faster, no vision needed) - uses 3x3 retry via AiRetryExecutor.
     */
    private suspend fun executeTextTranslation(
        text: String,
        targetLanguage: String
    ): Result<Pair<String, String>> {
        val models = ModelConfigProvider.getTextFallbackChain()
        val translationPrompt = buildTextTranslationPrompt(targetLanguage)
        val truncatedText = truncateForApi(text)

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(translationPrompt)
                    userMessage("Translate the following text to $targetLanguage:\n\n$truncatedText")
                    jsonSchema("translation", StructuredOutputSchemas.translationSchema)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                val parsed = parseTranslationJsonResponse(response.data)
                if (parsed != null && parsed.first.isNotBlank()) {
                    log.i("Translation ${model.id} succeeded: ${parsed.first.length} chars")
                    parsed
                } else {
                    null  // Trigger retry on next model
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Parse structured JSON translation response.
     * Expected: {"translatedText": "...", "detectedLanguage": "..."}
     */
    private fun parseTranslationJsonResponse(response: String): Pair<String, String>? {
        return try {
            val cleanJson = JsonExtractor.extract(response)
            val json = JSONObject(cleanJson)
            val translatedText = json.optString("translatedText", "").trim()
            val detectedLanguage = json.optString("detectedLanguage", "Unknown").trim()
            if (translatedText.isNotBlank()) {
                Pair(translatedText, detectedLanguage)
            } else {
                null
            }
        } catch (e: Exception) {
            log.w("Failed to parse translation JSON: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BITMAP UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Capture strokes to a bitmap for vision model processing.
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
     * Calculate the bounding box that encompasses all strokes.
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
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Page Translation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Translate multiple pages sequentially, concatenating results.
     *
     * Unlike summarization, translation doesn't need aggregation - we simply
     * concatenate per-page translations with page separators.
     *
     * @param pages List of pages with their content
     * @param targetLanguage Target language for translation
     * @param onStateChange Callback for progress updates
     * @return Result containing the full translation
     */
    suspend fun translateMultiplePages(
        pages: List<PageTranslationData>,
        targetLanguage: String = "English",
        onStateChange: (MultiPageTranslationState) -> Unit
    ): TranslationResult {
        // Filter to pages with content
        val pagesWithContent = pages.filter { it.hasContent }

        if (pagesWithContent.isEmpty()) {
            log.i("No pages with content to translate")
            onStateChange(MultiPageTranslationState.NoContent("No content found on any page"))
            return TranslationResult(
                translatedText = "",
                detectedLanguages = "",
                wasSuccessful = false,
                errorMessage = "No content found on any page"
            )
        }

        log.i("Starting multi-page translation: ${pagesWithContent.size} pages with content → $targetLanguage")

        val perPageTranslations = mutableListOf<String>()
        val allDetectedLanguages = mutableListOf<String>()
        val allStrokeIds = mutableListOf<String>()
        val allTextAnnotationIds = mutableListOf<String>()

        // Why the last unreadable page failed, if it really failed
        var pageFailure: String? = null

        try {
            // Process each page sequentially
            pagesWithContent.forEachIndexed { index, page ->
                onStateChange(MultiPageTranslationState.ProcessingPage(index + 1, pagesWithContent.size))
                log.d("Processing page ${index + 1}/${pagesWithContent.size}: ${page.strokes.size} strokes, ${page.textAnnotations.size} text blocks")

                val content = TranslationContent(page.strokes, page.textAnnotations)
                val result = translateContent(content, targetLanguage) { /* Ignore internal state changes */ }

                if (result.wasSuccessful && result.translatedText.isNotBlank()) {
                    perPageTranslations.add(result.translatedText)
                    if (result.detectedLanguages.isNotBlank() && result.detectedLanguages != "Unknown") {
                        allDetectedLanguages.add(result.detectedLanguages)
                    }
                    allStrokeIds.addAll(result.strokeIds)
                    allTextAnnotationIds.addAll(result.textAnnotationIds)
                    log.d("Page ${index + 1} translated: ${result.translatedText.length} chars")
                } else {
                    // A real failure (no Wi-Fi, no credit, ...), not just an empty page (Oct 2026)
                    result.errorMessage?.takeUnless { it.startsWith("No readable") || it.startsWith("No content") }
                        ?.let { pageFailure = it }
                    log.w("Page ${index + 1} had no translatable content, skipping")
                }
            }

            // Check if we got any translations
            if (perPageTranslations.isEmpty()) {
                log.i("No readable content found across all pages")
                onStateChange(MultiPageTranslationState.NoContent(pageFailure ?: "No readable content found on any page"))
                return TranslationResult(
                    translatedText = "",
                    detectedLanguages = "",
                    wasSuccessful = false,
                    errorMessage = pageFailure ?: "No readable content found"
                )
            }

            // Concatenate translations with page separators
            val fullTranslation = if (perPageTranslations.size == 1) {
                // Single page - no separators needed
                perPageTranslations.first()
            } else {
                // Multiple pages - add separators
                perPageTranslations.mapIndexed { index, translation ->
                    if (index == 0) {
                        translation
                    } else {
                        "--- Page ${index + 1} ---\n\n$translation"
                    }
                }.joinToString("\n\n")
            }

            val detectedLanguagesStr = allDetectedLanguages.distinct().joinToString(", ").ifEmpty { "Unknown" }

            log.i("Multi-page translation complete: ${fullTranslation.length} chars from ${perPageTranslations.size} pages")
            onStateChange(MultiPageTranslationState.Success(fullTranslation, detectedLanguagesStr))

            return TranslationResult(
                translatedText = fullTranslation,
                detectedLanguages = detectedLanguagesStr,
                wasSuccessful = true,
                strokeIds = allStrokeIds,
                textAnnotationIds = allTextAnnotationIds
            )

        } catch (e: Exception) {
            log.e("Multi-page translation failed: ${e.message}", e)
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network."
                is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                else -> "Translation failed: ${e.message}"
            }
            onStateChange(MultiPageTranslationState.Error(errorMessage))
            return TranslationResult(
                translatedText = "",
                detectedLanguages = "",
                wasSuccessful = false,
                errorMessage = errorMessage
            )
        }
    }
}
