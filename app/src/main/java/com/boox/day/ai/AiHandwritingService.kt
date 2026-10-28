package com.boox.day.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import com.boox.day.api.Modality
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.db.Stroke
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.ocr.DigitalInkService
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private val log = TaggedLog("AiHandwritingService")

/**
 * Configuration for multi-model text recognition.
 * Supports primary models, fallback chains, concurrent execution, and semantic inference.
 *
 * ## Fallback Chain (Dec 2025)
 * - Vision (Stage 1): NovaLite → Gemini2FlashExp → Gemma3_27B → NemotronNano → Gpt5Nano (PAID)
 * - Formatting (Stage 2): GptOss20B → KimiK2 → Gpt5Nano (PAID)
 *
 * NemotronNano (~31s) is kept as last-resort free option before paid fallback.
 */
data class RecognitionModelConfig(
    /** Primary vision model for text extraction (NovaLite is fastest with good OCR) */
    val primaryVisionModel: AiModel = AiModel.NovaLite,

    /** Fallback vision models if primary fails (tried in order). Ends with paid model. */
    val fallbackVisionModels: List<AiModel> = listOf(
        AiModel.Gemini2FlashExp,  // Free, fast vision
        AiModel.Gemma3_27B,       // Free, good vision
        AiModel.NemotronNano,     // Free, SLOW (~31s) - last resort free
        AiModel.Gpt5Nano          // PAID - guaranteed availability
    ),

    /** Models for text formatting/cleanup (Stage 2) - tried in order */
    val formattingModels: List<AiModel> = listOf(
        AiModel.GptOss20B,    // Free, fast
        AiModel.KimiK2,       // Free, reliable JSON
        AiModel.Gpt5Nano      // PAID - guaranteed availability
    ),

    /** Run multiple vision models concurrently and use first success */
    val useConcurrentVision: Boolean = false,

    /** Enable reasoning for models that support it */
    val enableReasoning: Boolean = true,

    /** Use /think prefix for NVIDIA models */
    val useNvidiaThinkPrefix: Boolean = true,

    /** Skip Stage 2 formatting (return raw extracted text) */
    val skipFormatting: Boolean = false,

    /** Enable Stage 1.5: Semantic inference using TextInferenceService */
    val enableSemanticInference: Boolean = true,

    /** Context for semantic inference (NOTE is default for handwriting) */
    val inferenceContext: InferenceContext = InferenceContext.NOTE,

    /** Use on-device ML Kit Digital Ink instead of cloud LLM (fast, offline, ~100-500ms) */
    val useDigitalInk: Boolean = false
) {
    companion object {
        /**
         * Default config optimized for accuracy with semantic inference.
         * Uses server-side model config when available, otherwise falls back to hardcoded defaults.
         */
        val DEFAULT: RecognitionModelConfig
            get() = fromServerConfig()

        /**
         * Fast config - uses concurrent models, skips formatting AND inference.
         * Uses server-side model config when available.
         */
        val FAST: RecognitionModelConfig
            get() = fromServerConfig(
                useConcurrentVision = true,
                skipFormatting = true,
                enableSemanticInference = false
            )

        /**
         * Reliable config - multiple fallbacks, no concurrent, with inference.
         * Uses server-side model config when available.
         */
        val RELIABLE: RecognitionModelConfig
            get() = fromServerConfig(
                useConcurrentVision = false,
                enableSemanticInference = true
            )

        /**
         * Maximum accuracy - all features enabled.
         * Uses server-side model config when available.
         */
        val ACCURATE: RecognitionModelConfig
            get() = fromServerConfig(
                enableReasoning = true,
                enableSemanticInference = true,
                skipFormatting = false
            )

        /**
         * Digital Ink config - uses on-device ML Kit recognition.
         * Fast (~100-500ms), works offline, no API costs.
         * Best for short text (sentences, paragraphs) like lasso selections.
         */
        val DIGITAL_INK: RecognitionModelConfig
            get() = RecognitionModelConfig(useDigitalInk = true)

        /**
         * Create a RecognitionModelConfig using server-side model configuration.
         * Falls back to hardcoded defaults if server config is unavailable.
         *
         * @param useConcurrentVision Run multiple vision models concurrently
         * @param enableReasoning Enable reasoning for models that support it
         * @param skipFormatting Skip Stage 2 formatting
         * @param enableSemanticInference Enable Stage 1.5 semantic inference
         * @param inferenceContext Context for semantic inference
         * @return RecognitionModelConfig with server or default models
         */
        fun fromServerConfig(
            useConcurrentVision: Boolean = false,
            enableReasoning: Boolean = true,
            useNvidiaThinkPrefix: Boolean = true,
            skipFormatting: Boolean = false,
            enableSemanticInference: Boolean = true,
            inferenceContext: InferenceContext = InferenceContext.NOTE
        ): RecognitionModelConfig {
            // Get vision models from server config
            val visionModels = ModelConfigProvider.getVisionFallbackChain()
            val primaryVision = visionModels.firstOrNull() ?: AiModel.NovaLite
            val fallbackVision = visionModels.drop(1)

            // Get text models from server config for formatting
            val textModels = ModelConfigProvider.getTextFallbackChain()
            val formattingModels = textModels

            log.d("RecognitionModelConfig.fromServerConfig: " +
                "vision=[${visionModels.map { it.id }.joinToString()}], " +
                "formatting=[${formattingModels.map { it.id }.joinToString()}]")

            return RecognitionModelConfig(
                primaryVisionModel = primaryVision,
                fallbackVisionModels = fallbackVision,
                formattingModels = formattingModels,
                useConcurrentVision = useConcurrentVision,
                enableReasoning = enableReasoning,
                useNvidiaThinkPrefix = useNvidiaThinkPrefix,
                skipFormatting = skipFormatting,
                enableSemanticInference = enableSemanticInference,
                inferenceContext = inferenceContext
            )
        }

        /**
         * Create a config with hardcoded defaults (ignores server config).
         * Useful for testing or when you explicitly want the original behavior.
         */
        fun hardcodedDefaults() = RecognitionModelConfig(
            primaryVisionModel = AiModel.NovaLite,
            fallbackVisionModels = listOf(
                AiModel.Gemini2FlashExp,
                AiModel.Gemma3_27B,
                AiModel.NemotronNano,
                AiModel.Gpt5Nano
            ),
            formattingModels = listOf(
                AiModel.GptOss20B,
                AiModel.KimiK2,
                AiModel.Gpt5Nano
            )
        )
    }
}

/**
 * AI-based handwriting recognition service using a three-stage pipeline:
 * 1. **Stage 1 (Vision)**: Vision model extracts raw text from handwriting image
 * 2. **Stage 1.5 (Inference)**: [TextInferenceService] corrects OCR errors and infers meaning
 * 3. **Stage 2 (Formatting)**: Text model formats, structures, and enhances the text
 *
 * Stage 1.5 uses [TextInferenceService] to correct common OCR mistakes like:
 * - "Mooting" → "Meeting"
 * - "tAmorru" → "tomorrow"
 * - "iiam" → "11am"
 *
 * This replaces ML Kit Digital Ink Recognition with AI-powered OCR.
 */
class AiHandwritingService {

    /**
     * Recognition processing states for UI feedback
     */
    sealed class RecognitionState {
        data object Idle : RecognitionState()
        data object CapturingImage : RecognitionState()
        data object ExtractingText : RecognitionState()
        data object InferringMeaning : RecognitionState()  // Stage 1.5
        data object FormattingText : RecognitionState()
        data class Success(val result: AiRecognitionResult) : RecognitionState()
        data class NoTextFound(val message: String) : RecognitionState()
        data class Error(val message: String) : RecognitionState()
    }

    /**
     * Result of AI-based handwriting recognition
     */
    data class AiRecognitionResult(
        val rawText: String,           // From Stage 1 (vision)
        val formattedText: String,     // From Stage 2 (text processing)
        val hasFormatting: Boolean     // Whether formatting was applied
    )

    companion object {
        private const val BITMAP_PADDING = 30f  // Extra padding to capture edge/corner text
        private const val MIN_BITMAP_SIZE = 100

        // Delimiters for parsing AI responses
        private const val START_TEXT_DELIMITER = "<<<START_TEXT>>>"
        private const val END_TEXT_DELIMITER = "<<<END_TEXT>>>"
        private const val NO_TEXT_MARKER = "<<<NO_TEXT>>>"

        // Stage 1 prompt: Extract ALL text from image (handwritten + typed)
        private val STAGE1_PROMPT = """
You extract text from images of handwritten and typed notes.

STEPS:
1. Scan the ENTIRE image systematically — top to bottom, left to right.
2. Identify ALL text regions: handwritten, typed, and printed.
3. For each text region, carefully read and transcribe the text.
4. Check corners, edges, and margins for small text that might be missed.
5. If text is inside a shape, extract just the text content.

RULES:
- Extract ALL readable text — everything handwritten, typed, or printed.
- IGNORE non-text elements: shapes, arrows, lines, hatching, shading, decorative marks.
- Preserve line breaks and spatial groupings.
- Keep wording as written — do not correct spelling or grammar.
- Use [unclear] for words you cannot read instead of guessing.
- If the handwriting is in a non-English language, transcribe in that language.
- If mixed scripts or languages are present, transcribe each in its original language.
- Output only the extracted text.

OUTPUT:
- Return raw JSON only.
- If text exists, return {"hasText": true, "text": "..."}.
- If no readable text exists, return {"hasText": false, "text": ""}.
        """.trimIndent()

        // Build Stage 1 prompt with optional /think prefix for NVIDIA models
        private fun buildStage1Prompt(useThinkPrefix: Boolean): String {
            return if (useThinkPrefix) {
                "/think\n$STAGE1_PROMPT"
            } else {
                STAGE1_PROMPT
            }
        }

        // Stage 2 prompt: Format and enhance extracted text with markdown
        private fun buildStage2Prompt() = """
You clean up extracted text and apply markdown formatting for rich text display.

TASKS:
1. Correct only highly likely OCR or handwriting errors — preserve wording otherwise.
2. Correct capitalization (proper nouns, sentence starts).
3. Fix obvious punctuation.
4. Apply MARKDOWN formatting:
   - Use **bold** for important words: names, dates, times, deadlines, priorities, key terms.
   - Use "- " for bullet point lists.
   - Use "1. 2. 3." for numbered/ordered lists.
   - Separate paragraphs with blank lines.
5. Identify and enhance structure:
   - Group related items into bullet lists when the structure is obvious.
   - Make clear headings/titles **bold**.
   - Highlight action items, deadlines, and names.
6. Do not merge or rewrite content for style.
7. Do not invent missing words, dates, or names.
8. Do not add explanations.

MARKDOWN EXAMPLES:
- "Meeting with John tomorrow at 3pm" -> "Meeting with **John** tomorrow at **3pm**"
- "Buy groceries milk eggs bread" -> "Buy groceries:\n- Milk\n- Eggs\n- Bread"
- "Important call Dr Smith" -> "**Important**: Call **Dr. Smith**"

OUTPUT FORMAT:
<<<START_TEXT>>>
[markdown formatted text here]
<<<END_TEXT>>>
        """.trimIndent()
    }

    /**
     * Recognize handwriting from the given strokes using AI.
     *
     * @param strokes List of strokes to recognize
     * @param config Model configuration (defaults to RecognitionModelConfig.DEFAULT)
     * @param context Android context (required only when config.useDigitalInk = true)
     * @param onStateChange Callback for state updates (for UI progress)
     * @return Result containing the recognition result or error
     */
    suspend fun recognizeStrokes(
        strokes: List<Stroke>,
        config: RecognitionModelConfig = RecognitionModelConfig.DEFAULT,
        context: android.content.Context? = null,
        onStateChange: (RecognitionState) -> Unit
    ): Result<AiRecognitionResult> {
        if (strokes.isEmpty()) {
            return Result.failure(IllegalArgumentException("No strokes to recognize"))
        }

        // ─────────────────────────────────────────────────────────────────────
        // Digital Ink Path (fast, on-device, ~100-500ms)
        // ─────────────────────────────────────────────────────────────────────
        if (config.useDigitalInk) {
            return recognizeWithDigitalInk(strokes, context, onStateChange)
        }

        // ─────────────────────────────────────────────────────────────────────
        // LLM Path (cloud-based, 3-15s)
        // ─────────────────────────────────────────────────────────────────────
        try {
            // Stage 0: Capture strokes to bitmap
            onStateChange(RecognitionState.CapturingImage)
            log.d("Stage 0: Capturing ${strokes.size} strokes to bitmap")

            val bitmap = captureStrokesToBitmap(strokes)
            val base64Image = bitmapToBase64(bitmap)
            bitmap.recycle()

            log.d("Stage 0 complete: Bitmap encoded (${base64Image.length} chars)")

            // Stage 1: Extract text with vision model (with fallback/concurrent support)
            onStateChange(RecognitionState.ExtractingText)
            log.d("Stage 1: Extracting text (primary=${config.primaryVisionModel.id}, concurrent=${config.useConcurrentVision})")

            val stage1Result = executeStage1WithConfig(base64Image, config)
            if (stage1Result.isFailure) {
                val error = stage1Result.exceptionOrNull()?.message ?: "Vision model failed"
                log.e("Stage 1 failed: $error")
                onStateChange(RecognitionState.Error(error))
                return Result.failure(stage1Result.exceptionOrNull()!!)
            }

            val rawText = stage1Result.getOrThrow()

            // Check for "no text" response
            if (rawText.isBlank() || rawText.contains(NO_TEXT_MARKER)) {
                log.i("Stage 1: No handwritten text detected")
                onStateChange(RecognitionState.NoTextFound("No handwritten text detected in selection"))
                return Result.failure(NoTextFoundException("No handwritten text detected"))
            }

            log.d("Stage 1 complete: Extracted ${rawText.length} chars")

            // ─────────────────────────────────────────────────────────────────────
            // Stage 1.5: Semantic Inference (optional, via TextInferenceService)
            // ─────────────────────────────────────────────────────────────────────
            val inferredText = if (config.enableSemanticInference) {
                onStateChange(RecognitionState.InferringMeaning)
                log.d("Stage 1.5: Inferring meaning with TextInferenceService (context=${config.inferenceContext})")

                val inferenceResult = TextInferenceService.refineText(
                    rawText = rawText,
                    context = config.inferenceContext,
                    config = InferenceConfig.DEFAULT
                )

                if (inferenceResult.wasModified) {
                    log.i("Stage 1.5: Text refined (confidence=${inferenceResult.confidence})")
                    log.d("Stage 1.5: '${rawText.take(100)}...' → '${inferenceResult.refinedText.take(100)}...'")
                } else {
                    log.d("Stage 1.5: No changes needed (confidence=${inferenceResult.confidence})")
                }

                inferenceResult.refinedText
            } else {
                log.d("Stage 1.5: Skipped (enableSemanticInference=false)")
                rawText
            }

            // Stage 2: Format text (optional based on config)
            if (config.skipFormatting) {
                log.d("Stage 2: Skipped (skipFormatting=true)")
                val result = AiRecognitionResult(
                    rawText = rawText,
                    formattedText = inferredText,  // Use inferred text if available
                    hasFormatting = false
                )
                onStateChange(RecognitionState.Success(result))
                return Result.success(result)
            }

            onStateChange(RecognitionState.FormattingText)
            log.d("Stage 2: Formatting text with ${config.formattingModels.size} model fallback chain")

            // Use inferred text for Stage 2 if semantic inference was applied
            val stage2Result = executeStage2WithConfig(inferredText, config)
            val formattedText = if (stage2Result.isSuccess) {
                stage2Result.getOrThrow()
            } else {
                // If formatting fails, use inferred text (or raw text if inference was skipped)
                log.w("Stage 2 failed, using inferred text: ${stage2Result.exceptionOrNull()?.message}")
                inferredText
            }

            log.d("Stage 2 complete: Formatted text (${formattedText.length} chars)")

            val result = AiRecognitionResult(
                rawText = rawText,
                formattedText = formattedText,
                hasFormatting = stage2Result.isSuccess && formattedText != rawText
            )

            onStateChange(RecognitionState.Success(result))
            return Result.success(result)

        } catch (e: Exception) {
            log.e("Recognition failed: ${e.message}", e)
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network."
                is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                else -> "Recognition failed: ${e.message}"
            }
            onStateChange(RecognitionState.Error(errorMessage))
            return Result.failure(e)
        }
    }

    /**
     * Recognize strokes using on-device ML Kit Digital Ink Recognition.
     * Fast (~100-500ms), works offline, no API costs.
     */
    private suspend fun recognizeWithDigitalInk(
        strokes: List<Stroke>,
        context: android.content.Context?,
        onStateChange: (RecognitionState) -> Unit
    ): Result<AiRecognitionResult> {
        if (context == null) {
            val error = "Context required for Digital Ink recognition"
            log.e(error)
            onStateChange(RecognitionState.Error(error))
            return Result.failure(IllegalArgumentException(error))
        }

        try {
            // Check if model is ready
            if (!DigitalInkModelManager.isModelReady()) {
                val error = "Digital Ink model not downloaded. Please wait for model download to complete."
                log.w(error)
                onStateChange(RecognitionState.Error(error))
                return Result.failure(IllegalStateException(error))
            }

            // Stage: Extracting text
            onStateChange(RecognitionState.ExtractingText)
            log.d("Digital Ink: Recognizing ${strokes.size} strokes")

            val digitalInkService = DigitalInkService.create(context, CoroutineScope(Dispatchers.Default))
            val result = digitalInkService.recognizeStrokes(strokes)

            if (result.isEmpty) {
                log.i("Digital Ink: No text detected")
                onStateChange(RecognitionState.NoTextFound("No handwritten text detected in selection"))
                return Result.failure(NoTextFoundException("No handwritten text detected"))
            }

            log.i("Digital Ink: Recognized '${result.text.take(50)}...' (confidence=${result.confidence})")

            // Return result (no formatting stage for Digital Ink - it's already clean)
            val aiResult = AiRecognitionResult(
                rawText = result.text,
                formattedText = result.text,
                hasFormatting = false
            )

            onStateChange(RecognitionState.Success(aiResult))
            return Result.success(aiResult)

        } catch (e: Exception) {
            log.e("Digital Ink recognition failed: ${e.message}", e)
            onStateChange(RecognitionState.Error("Recognition failed: ${e.message}"))
            return Result.failure(e)
        }
    }

    /**
     * Stage 1: Extract text using vision model with fallback/concurrent support
     */
    private suspend fun executeStage1WithConfig(
        base64Image: String,
        config: RecognitionModelConfig
    ): Result<String> {
        return if (config.useConcurrentVision) {
            executeStage1Concurrent(base64Image, config)
        } else {
            executeStage1WithFallback(base64Image, config)
        }
    }

    /**
     * Execute Stage 1 with 3×3 retry via AiRetryExecutor.
     * Uses server-controlled vision models from ModelConfigProvider.
     */
    private suspend fun executeStage1WithFallback(
        base64Image: String,
        config: RecognitionModelConfig
    ): Result<String> {
        // Use server-controlled vision models (Rule of 3+3)
        val models = ModelConfigProvider.getVisionFallbackChain()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(buildStage1Prompt(config.useNvidiaThinkPrefix && isNvidiaModel(model)))
                    userMessageWithImage("Read and transcribe all handwritten text:", base64Image)
                    jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextSchema)
                    if (config.enableReasoning && model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                // The prompt asks for {"hasText", "text"} (Oct 2026: only the concurrent path read it, so
                // here {"hasText": false} counted as text and a selection of shapes was "recognized")
                val text = parseStage1Text(response.data)
                // Check if model returned "no text" - this is a valid response, not a retry trigger
                if (text.contains(NO_TEXT_MARKER) || text.isBlank()) {
                    log.i("Stage 1: Model ${model.id} found no text")
                    ""  // Empty string is valid (no text found)
                } else {
                    log.i("Stage 1: Model ${model.id} succeeded with ${text.length} chars")
                    text
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Execute Stage 1 with concurrent model execution (race - first success wins)
     */
    private suspend fun executeStage1Concurrent(
        base64Image: String,
        config: RecognitionModelConfig
    ): Result<String> = coroutineScope {
        // Build full model list: primary + fallbacks
        val modelsToTry = listOf(config.primaryVisionModel) + config.fallbackVisionModels
        log.i("Stage 1: Running ${modelsToTry.size} models concurrently")

        // Launch all models concurrently
        val deferredResults = modelsToTry.map { model ->
            async {
                model to executeVisionModelSingle(base64Image, model, config)
            }
        }

        // Collect results - take first successful one with actual text
        var firstSuccess: Result<String>? = null
        var firstSuccessModel: AiModel? = null
        val errors = mutableListOf<String>()

        for (deferred in deferredResults) {
            val (model, result) = deferred.await()
            if (result.isSuccess) {
                val text = result.getOrThrow()
                if (text.isNotBlank() && !text.contains(NO_TEXT_MARKER)) {
                    if (firstSuccess == null) {
                        firstSuccess = result
                        firstSuccessModel = model
                        log.i("Stage 1 Concurrent: ${model.id} succeeded first with ${text.length} chars")
                    }
                } else {
                    log.d("Stage 1 Concurrent: ${model.id} returned no text")
                }
            } else {
                errors.add("${model.id}: ${result.exceptionOrNull()?.message}")
            }
        }

        if (firstSuccess != null) {
            log.i("Stage 1 Concurrent: Using result from ${firstSuccessModel?.id}")
            firstSuccess
        } else if (errors.isEmpty()) {
            // All models returned "no text"
            Result.success("")  // Let caller handle "no text" case
        } else {
            Result.failure(Exception("All concurrent models failed: ${errors.joinToString("; ")}"))
        }
    }

    /**
     * Execute a single vision model for text extraction
     */
    private suspend fun executeVisionModelSingle(
        base64Image: String,
        model: AiModel,
        config: RecognitionModelConfig
    ): Result<String> {
        log.d("Executing vision model ${model.id} (image: ${base64Image.length} chars)")

        // Build system prompt with optional /think prefix for NVIDIA models
        val systemPrompt = if (config.useNvidiaThinkPrefix && isNvidiaModel(model)) {
            "/think\n$STAGE1_PROMPT"
        } else {
            STAGE1_PROMPT
        }

        val request = aiRequest {
            model(model)
            systemPrompt(systemPrompt)
            userMessageWithImage("Extract all text from this image:", base64Image)
            jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextSchema)
            // Only enable reasoning if model supports it (e.g., Gemini does NOT)
            if (config.enableReasoning && model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                enableReasoning()
            }
        }

        return when (val response = AiClient.execute(request)) {
            is AiResponse.Success -> {
                log.i("${model.id} SUCCESS - Raw response (${response.data.length} chars):")
                log.i("=== RESPONSE START ===")
                response.data.chunked(500).forEachIndexed { idx, chunk ->
                    log.i("[$idx]: $chunk")
                }
                log.i("=== RESPONSE END ===")
                log.i("Model used: ${response.model}, Tokens: ${response.usage?.totalTokens}")

                Result.success(parseStage1Text(response.data))
            }
            is AiResponse.Error -> {
                log.e("${model.id} ERROR: ${response.message}")
                Result.failure(Exception(response.message))
            }
        }
    }

    /**
     * Check if a model is from NVIDIA (requires /think prefix for reasoning)
     */
    private fun isNvidiaModel(model: AiModel): Boolean {
        return model.id.contains("nvidia", ignoreCase = true) ||
               model.id.contains("nemotron", ignoreCase = true)
    }

    /**
     * Stage 2: Format and enhance the extracted text using 3×3 retry via AiRetryExecutor.
     * Uses server-controlled text models from ModelConfigProvider.
     */
    private suspend fun executeStage2WithConfig(
        rawText: String,
        config: RecognitionModelConfig
    ): Result<String> {
        // Use server-controlled text models (Rule of 3+3)
        val models = ModelConfigProvider.getTextFallbackChain()
        val stage2Prompt = buildStage2Prompt()

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(stage2Prompt)
                    userMessage("Clean up and lightly format this extracted text:\n\n$rawText")
                    if (config.enableReasoning && model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                log.d("Stage 2 SUCCESS: ${response.data.length} chars from ${model.id}")
                val formattedText = parseDelimitedText(response.data) ?: response.data.trim()
                formattedText.ifBlank { null }  // null triggers retry if blank
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Parse text between START_TEXT and END_TEXT delimiters
     */
    private fun parseDelimitedText(response: String): String? {
        val startIdx = response.indexOf(START_TEXT_DELIMITER)
        val endIdx = response.indexOf(END_TEXT_DELIMITER)

        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            response.substring(startIdx + START_TEXT_DELIMITER.length, endIdx).trim()
        } else {
            null
        }
    }

    /**
     * The text of a Stage 1 answer: the "text" of {"hasText", "text"} ("" for hasText false), else
     * text between the delimiters, else the whole answer.
     */
    internal fun parseStage1Text(response: String): String =
        parseStructuredOcrResponse(response) ?: parseDelimitedText(response) ?: response.trim()

    private fun parseStructuredOcrResponse(response: String): String? {
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
     * Capture strokes to a bitmap for vision model processing
     */
    private fun captureStrokesToBitmap(strokes: List<Stroke>): Bitmap {
        // Calculate bounding box of all strokes
        val bounds = calculateBoundingBox(strokes)

        log.d("Original bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")

        // Add padding
        bounds.inset(-BITMAP_PADDING, -BITMAP_PADDING)

        // Ensure minimum size
        val width = bounds.width().toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val height = bounds.height().toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        log.d("Creating bitmap: ${width}x${height} for ${strokes.size} strokes")
        log.d("Padded bounds: left=${bounds.left}, top=${bounds.top}")

        // Create bitmap with white background
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Translate canvas so strokes are drawn at correct position
        canvas.translate(-bounds.left, -bounds.top)

        // Draw all strokes
        strokes.forEach { stroke ->
            log.d("Drawing stroke: id=${stroke.id}, pen=${stroke.pen}, points=${stroke.points.size}, bounds=[${stroke.left},${stroke.top},${stroke.right},${stroke.bottom}]")
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
     * Convert bitmap to base64 JPEG string for API submission
     * Note: Nova 2 Lite requires JPEG format, not PNG
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

/**
 * Exception thrown when no text is found in the selection
 */
class NoTextFoundException(message: String) : Exception(message)
