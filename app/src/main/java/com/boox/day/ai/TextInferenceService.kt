package com.boox.day.ai

import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.aiRequest
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.boox.day.utils.TaggedLog

// JsonExtractor is in same package - no import needed

private val log = TaggedLog("TextInferenceService")

/**
 * Context hints for text inference optimization.
 * Each context biases the AI toward domain-specific corrections.
 */
enum class InferenceContext {
    /** Default - fix errors, improve clarity with equal weight to all rules */
    GENERAL,
    /** Bias toward dates, times, locations, people names */
    CALENDAR_EVENT,
    /** Bias toward action items, deadlines, priority indicators */
    TASK,
    /** Preserve structure, fix spelling without changing tone */
    NOTE,
    /** Bias toward product names, quantities, store names */
    SHOPPING_LIST,
    /** Bias toward names, decisions, action items, project names */
    MEETING_NOTES
}

/**
 * Configuration for text inference behavior.
 *
 * ## Fallback Chain (Dec 2025)
 * TextInferenceService uses "big brain" models only for semantic correction:
 * - GptOss120B (free, best reasoning) → KimiK2 (free, 32K context) → Gpt5Nano (PAID, guaranteed)
 *
 * Gpt5Nano is the paid safety net to ensure service availability when free models are rate-limited.
 */
data class InferenceConfig(
    /** Primary model for inference (default: GPT-OSS-120B - best free reasoning) */
    val primaryModel: AiModel = AiModel.GptOss120B,
    /** Fallback models if primary fails (tried in order). Ends with paid model for guaranteed availability. */
    val fallbackModels: List<AiModel> = listOf(AiModel.KimiK2, AiModel.Gpt5Nano),
    /** Enable reasoning/chain-of-thought for better accuracy */
    val enableReasoning: Boolean = true,
    /** Temperature for generation (lower = more deterministic) */
    val temperature: Float = 0.3f,
    /** Skip inference for very short text */
    val skipForShortText: Boolean = true,
    /** Threshold for short text skip (characters) */
    val shortTextThreshold: Int = 10,
    /** Maximum retry attempts per model */
    val maxRetries: Int = 2,
    /** Delay between retries (milliseconds) */
    val retryDelayMs: Long = 500L,
    /** Track what was corrected (opt-in for debugging) */
    val trackCorrections: Boolean = false
) {
    companion object {
        /**
         * Default config - uses big brain models with paid safety net.
         * Uses server-side model config when available.
         */
        val DEFAULT: InferenceConfig
            get() = fromServerConfig()

        /**
         * Fast config - single model with paid fallback, no reasoning.
         * Uses server-side model config when available.
         */
        val FAST: InferenceConfig
            get() = fromServerConfig(
                enableReasoning = false,
                temperature = 0.2f
            )

        /**
         * Accurate config - full chain with reasoning enabled.
         * Uses server-side model config when available.
         */
        val ACCURATE: InferenceConfig
            get() = fromServerConfig(
                enableReasoning = true,
                temperature = 0.3f
            )

        /**
         * Debug config - tracks all corrections.
         * Uses server-side model config when available.
         */
        val DEBUG: InferenceConfig
            get() = fromServerConfig(trackCorrections = true)

        /**
         * Create an InferenceConfig using server-side model configuration.
         * Falls back to hardcoded defaults if server config is unavailable.
         *
         * @param enableReasoning Enable reasoning for models that support it
         * @param temperature Temperature for generation
         * @param skipForShortText Skip inference for very short text
         * @param shortTextThreshold Threshold for short text skip
         * @param maxRetries Maximum retry attempts per model
         * @param retryDelayMs Delay between retries
         * @param trackCorrections Track what was corrected
         * @return InferenceConfig with server or default models
         */
        fun fromServerConfig(
            enableReasoning: Boolean = true,
            temperature: Float = 0.3f,
            skipForShortText: Boolean = true,
            shortTextThreshold: Int = 10,
            maxRetries: Int = 2,
            retryDelayMs: Long = 500L,
            trackCorrections: Boolean = false
        ): InferenceConfig {
            // Get reasoning models from server config
            val reasoningModels = ModelConfigProvider.getReasoningFallbackChain()
            val primaryModel = reasoningModels.firstOrNull() ?: AiModel.GptOss120B
            val fallbackModels = reasoningModels.drop(1)

            log.d("InferenceConfig.fromServerConfig: " +
                "models=[${reasoningModels.map { it.id }.joinToString()}]")

            return InferenceConfig(
                primaryModel = primaryModel,
                fallbackModels = fallbackModels,
                enableReasoning = enableReasoning,
                temperature = temperature,
                skipForShortText = skipForShortText,
                shortTextThreshold = shortTextThreshold,
                maxRetries = maxRetries,
                retryDelayMs = retryDelayMs,
                trackCorrections = trackCorrections
            )
        }

        /**
         * Create a config with hardcoded defaults (ignores server config).
         */
        fun hardcodedDefaults() = InferenceConfig(
            primaryModel = AiModel.GptOss120B,
            fallbackModels = listOf(AiModel.KimiK2, AiModel.Gpt5Nano)
        )
    }
}

/**
 * Type of correction applied to text.
 */
enum class CorrectionType {
    SPELLING,
    CAPITALIZATION,
    GRAMMAR,
    TIME_FORMAT,
    DATE_FORMAT,
    NAME,
    ABBREVIATION,
    SEMANTIC
}

/**
 * A single correction made during inference.
 * Only populated when [InferenceConfig.trackCorrections] is true.
 */
data class Correction(
    val original: String,
    val corrected: String,
    val type: CorrectionType
)

/**
 * Result of text inference operation.
 */
data class InferenceResult(
    /** The refined/corrected text */
    val refinedText: String,
    /** The original input text */
    val originalText: String,
    /** Confidence score (0.0-1.0) */
    val confidence: Float,
    /** Whether the text was modified */
    val wasModified: Boolean,
    /** List of corrections (only if trackCorrections=true) */
    val corrections: List<Correction>,
    /** Which model processed this request */
    val modelUsed: String?,
    /** Error message if inference failed */
    val error: String? = null
) {
    /** Whether inference completed successfully */
    val isSuccess: Boolean get() = error == null

    companion object {
        /**
         * Create a successful inference result.
         */
        fun success(
            refined: String,
            original: String,
            confidence: Float,
            corrections: List<Correction> = emptyList(),
            modelUsed: String? = null
        ) = InferenceResult(
            refinedText = refined,
            originalText = original,
            confidence = confidence,
            wasModified = refined != original,
            corrections = corrections,
            modelUsed = modelUsed
        )

        /**
         * Create a result indicating no changes were needed.
         */
        fun unchanged(original: String) = InferenceResult(
            refinedText = original,
            originalText = original,
            confidence = 1.0f,
            wasModified = false,
            corrections = emptyList(),
            modelUsed = null
        )

        /**
         * Create an error result with graceful degradation.
         * Returns ORIGINAL TEXT as refinedText so caller always has usable text.
         */
        fun error(original: String, message: String) = InferenceResult(
            refinedText = original,  // GRACEFUL DEGRADATION
            originalText = original,
            confidence = 0f,
            wasModified = false,
            corrections = emptyList(),
            modelUsed = null,
            error = message
        )
    }
}

/**
 * Centralized service for text inference and refinement.
 *
 * Takes noisy/garbled text from OCR, speech-to-text, or handwriting recognition
 * and returns corrected, semantically accurate text.
 *
 * ## Features
 * - Context-aware inference (calendar, task, note, shopping list, meeting notes)
 * - Model fallback chain for reliability
 * - Configurable reasoning and temperature
 * - Graceful degradation on errors (returns original text)
 * - Optional correction tracking for debugging
 *
 * ## Usage
 * ```kotlin
 * val result = TextInferenceService.refineText(
 *     rawText = "Mooting with JO&un tAmorru at iiam",
 *     context = InferenceContext.CALENDAR_EVENT
 * )
 * // result.refinedText = "Meeting with John tomorrow at 11:00 AM"
 * ```
 *
 * @see InferenceContext for available context types
 * @see InferenceConfig for configuration options
 */
object TextInferenceService {
    private const val TAG = "TextInferenceService"
    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON RESPONSE STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * JSON structure expected from LLM response.
     */
    private data class InferenceResponse(
        @SerializedName("refinedText") val refinedText: String,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("corrections") val corrections: List<CorrectionJson>? = null
    )

    private data class CorrectionJson(
        @SerializedName("original") val original: String,
        @SerializedName("corrected") val corrected: String,
        @SerializedName("type") val type: String
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refine raw/garbled text using AI inference.
     *
     * @param rawText The noisy input text (from OCR, STT, etc.)
     * @param context Context hint for domain-specific inference
     * @param config Configuration for model selection and behavior
     * @return InferenceResult with refined text and metadata
     */
    suspend fun refineText(
        rawText: String,
        context: InferenceContext = InferenceContext.GENERAL,
        config: InferenceConfig = InferenceConfig.DEFAULT
    ): InferenceResult {
        // Validate input
        if (rawText.isBlank()) {
            log.d("Empty input text, returning unchanged")
            return InferenceResult.unchanged("")
        }

        // Skip very short text if configured
        if (config.skipForShortText && rawText.length < config.shortTextThreshold) {
            log.d("Text too short (${rawText.length} < ${config.shortTextThreshold}), skipping inference")
            return InferenceResult.unchanged(rawText)
        }

        log.i("Refining text: ${rawText.take(50)}... (context=$context)")

        // Execute with fallback chain
        return executeWithFallback(rawText, context, config)
    }

    /**
     * Convenience method for calendar event text.
     */
    suspend fun refineCalendarText(rawText: String): InferenceResult {
        return refineText(rawText, InferenceContext.CALENDAR_EVENT)
    }

    /**
     * Convenience method for task text.
     */
    suspend fun refineTaskText(rawText: String): InferenceResult {
        return refineText(rawText, InferenceContext.TASK)
    }

    /**
     * Convenience method for note text.
     */
    suspend fun refineNoteText(rawText: String): InferenceResult {
        return refineText(rawText, InferenceContext.NOTE)
    }

    /**
     * Convenience method for shopping list text.
     */
    suspend fun refineShoppingListText(rawText: String): InferenceResult {
        return refineText(rawText, InferenceContext.SHOPPING_LIST)
    }

    /**
     * Convenience method for meeting notes text.
     */
    suspend fun refineMeetingNotesText(rawText: String): InferenceResult {
        return refineText(rawText, InferenceContext.MEETING_NOTES)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXECUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Execute inference with 3×3 retry via AiRetryExecutor.
     * Uses server-controlled models from ModelConfigProvider.
     */
    private suspend fun executeWithFallback(
        rawText: String,
        context: InferenceContext,
        config: InferenceConfig
    ): InferenceResult {
        // Use server-controlled reasoning models (Rule of 3+3)
        val models = ModelConfigProvider.getReasoningFallbackChain()
        val systemPrompt = buildSystemPrompt(context, config.trackCorrections)
        val userPrompt = buildUserPrompt(rawText)

        return when (val result = AiRetryExecutor.execute(
            models = models,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(userPrompt)
                    jsonSchema(
                        "text_inference",
                        StructuredOutputSchemas.inferenceSchema(config.trackCorrections)
                    )
                    temperature(config.temperature)
                    if (config.enableReasoning && model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                log.d("${model.displayName} response: ${response.data.take(300)}...")
                val parsed = parseResponse(response.data, rawText, config.trackCorrections, model.displayName)
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> {
                // Graceful degradation - return original text with error
                log.e("All models failed, returning original text. Last error: ${result.lastError}")
                InferenceResult.error(rawText, result.message)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROMPT BUILDING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build the system prompt with context-specific inference rules.
     */
    private fun buildSystemPrompt(context: InferenceContext, trackCorrections: Boolean): String {
        val basePrompt = """
You correct noisy text from OCR, speech-to-text, or handwriting recognition.

JOB:
- Infer the most likely intended wording.
- Fix obvious spelling, capitalization, time, date, name, and abbreviation errors.
- Preserve the author's meaning and structure.

HARD RULES:
1. Do not invent new facts or add missing content.
2. Keep lists, line breaks, and order when they matter.
3. If the text is already correct, return it unchanged.
4. Return raw JSON only and match the response schema exactly.
5. Never add markdown, code fences, or commentary.
6. Detect the language and apply corrections appropriate to that language.
7. Keep the output in the same language as the input.
""".trimIndent()

        val contextPrompt = when (context) {
            InferenceContext.CALENDAR_EVENT -> """

## CALENDAR EVENT CONTEXT

Bias inference toward:
- Event titles (meetings, appointments, calls)
- People names (attendees, contacts)
- Times and durations
- Locations (offices, restaurants, addresses)
- Date references (today, tomorrow, next week, specific dates)

Examples:
- "Lnch w/ Sara @ noon" → "Lunch with Sarah at 12:00 PM"
- "Dentst apt 3pm tues" → "Dentist appointment 3:00 PM Tuesday"
- "Mtg w/ team tmrw 10am conf rm B" → "Meeting with team tomorrow 10:00 AM conference room B"
"""

            InferenceContext.TASK -> """

## TASK CONTEXT

Bias inference toward:
- Action verbs (call, email, buy, finish, review, send)
- Deadlines and due dates
- Priority indicators (urgent, ASAP, important)
- People to contact or collaborate with

Examples:
- "Cll mom tmorw" → "Call Mom tomorrow"
- "Finsh reprt by Fri ASAP" → "Finish report by Friday ASAP"
- "Snd email 2 boss re: projct updts" → "Send email to boss regarding project updates"
"""

            InferenceContext.NOTE -> """

## NOTE CONTEXT

Preserve natural structure while fixing errors:
- Keep bullet points and lists
- Maintain paragraph breaks
- Preserve emphasis (if detectable)
- Fix spelling without changing tone

Examples:
- "Idaes for projct:\n- Imprve UI\n- Fix bugs" → "Ideas for project:\n- Improve UI\n- Fix bugs"
- "Thots on mktng stratgy" → "Thoughts on marketing strategy"
"""

            InferenceContext.SHOPPING_LIST -> """

## SHOPPING LIST CONTEXT

Bias inference toward:
- Product names (milk, eggs, bread, vegetables)
- Quantities and amounts
- Store names
- Brands (if mentioned)

Examples:
- "Miik, egs, brad, chese" → "Milk, eggs, bread, cheese"
- "2 doz egs, galln milk" → "2 dozen eggs, gallon milk"
- "Apls, banas, ornges" → "Apples, bananas, oranges"
"""

            InferenceContext.MEETING_NOTES -> """

## MEETING NOTES CONTEXT

Bias inference toward:
- People names (capitalize, correct spelling)
- Action items and decisions
- Dates and deadlines
- Project/product names

Examples:
- "John sugstd movng lanch to mrch" → "John suggested moving launch to March"
- "Decison: go w/ optn A" → "Decision: go with option A"
- "Actn item: Sara to revw by Fri" → "Action item: Sarah to review by Friday"
"""

            InferenceContext.GENERAL -> """

## GENERAL CONTEXT

Apply all inference rules with equal weight.
Focus on readability and grammatical correctness.
"""
        }

        return basePrompt + contextPrompt
    }

    /**
     * Build the user prompt with the raw text to refine.
     */
    private fun buildUserPrompt(rawText: String): String {
        return """
Refine this text by inferring the intended wording and fixing obvious errors.

$rawText

Return only the JSON object.
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESPONSE PARSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse the LLM JSON response into an InferenceResult.
     */
    private fun parseResponse(
        json: String,
        originalText: String,
        trackCorrections: Boolean,
        modelUsed: String
    ): InferenceResult {
        return try {
            // Use JsonExtractor for robust JSON extraction (handles markdown blocks, prefixes, etc.)
            val cleanJson = JsonExtractor.extract(json)

            val parsed = gson.fromJson(cleanJson, InferenceResponse::class.java)

            if (parsed.refinedText.isBlank()) {
                log.w("Empty refinedText in response")
                return InferenceResult.error(originalText, "Empty response")
            }

            // Convert corrections if tracking is enabled and we have them
            val corrections = if (trackCorrections && parsed.corrections != null) {
                parsed.corrections.mapNotNull { correctionJson ->
                    try {
                        Correction(
                            original = correctionJson.original,
                            corrected = correctionJson.corrected,
                            type = try {
                                CorrectionType.valueOf(correctionJson.type.uppercase())
                            } catch (e: IllegalArgumentException) {
                                CorrectionType.SEMANTIC // Default to SEMANTIC if type is unknown
                            }
                        )
                    } catch (e: Exception) {
                        log.w("Failed to parse correction: $correctionJson")
                        null
                    }
                }
            } else {
                emptyList()
            }

            InferenceResult.success(
                refined = parsed.refinedText,
                original = originalText,
                confidence = parsed.confidence.coerceIn(0f, 1f),
                corrections = corrections,
                modelUsed = modelUsed
            )
        } catch (e: JsonSyntaxException) {
            log.e("Failed to parse JSON response: ${json.take(200)}", e)
            // Try to extract refinedText with regex as fallback
            extractRefinedTextFallback(json, originalText, modelUsed)
        } catch (e: Exception) {
            log.e("Unexpected error parsing response", e)
            InferenceResult.error(originalText, "Parse error: ${e.message}")
        }
    }

    /**
     * Fallback extraction when JSON parsing fails.
     * Attempts to extract refinedText using regex.
     */
    private fun extractRefinedTextFallback(
        response: String,
        originalText: String,
        modelUsed: String
    ): InferenceResult {
        log.d("Attempting fallback text extraction")

        // Try to extract refinedText value
        val refinedTextPattern = Regex(""""refinedText"\s*:\s*"([^"]+)"""")
        val match = refinedTextPattern.find(response)

        return if (match != null) {
            val extractedText = match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")

            log.i("Fallback extraction successful: ${extractedText.take(50)}...")

            InferenceResult.success(
                refined = extractedText,
                original = originalText,
                confidence = 0.7f, // Lower confidence for fallback
                corrections = emptyList(),
                modelUsed = modelUsed
            )
        } else {
            log.w("Fallback extraction failed")
            InferenceResult.error(originalText, "Could not parse response")
        }
    }
}
