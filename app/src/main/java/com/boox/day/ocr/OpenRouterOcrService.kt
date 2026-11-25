package com.boox.day.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.boox.day.ai.AiRetryExecutor
import com.boox.day.ai.JsonExtractor
import com.boox.day.ai.StructuredOutputSchemas
import com.boox.day.api.AiJob
import com.boox.day.api.AiPickStatus
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.NetworkState
import com.boox.day.api.NetworkStatus
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ReasoningConfig
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream

/**
 * Reads a page image for search indexing with an AI model on the user's own OpenRouter key
 * (Oct 2026, replaces Google Cloud Vision). Picked from a test on 10 of the owner's handwritten
 * BOOX Notes pages (14 Oct 2026): Gemini 3.1 Flash Lite found 99.5% of the words at about $0.0005
 * a page; Cloud Vision's existing index of the same pages had 85%.
 *
 * Only providers with zero data retention see the page, and thinking is off (not needed to read).
 * Indexing is background work: a missing key or empty credit never opens the key popup, it pauses.
 */
class OpenRouterOcrService {

    companion object {
        private const val TAG = "OpenRouterOcrService"

        private const val JPEG_QUALITY = 85

        // The app's OCR prompt (AiSummarizationService) plus the language code indexing stores
        private val SYSTEM_PROMPT = """
Read all handwritten and printed text in this image and return only the extracted text.

OUTPUT:
- Return raw JSON only.
- If text exists, return {"hasText": true, "language": "<two-letter ISO 639-1 code, e.g. en>", "text": "..."}.
- If no readable text exists, return {"hasText": false, "language": "", "text": ""}.

RULES:
- Preserve line breaks and list structure when clear.
- Use [unclear] for unreadable words instead of guessing.
- Do not summarize, explain, or format beyond the extracted text.
        """.trimIndent()
    }

    sealed class Result {
        data class Success(val text: String, val language: String?, val confidence: Float, val blocks: Int) : Result()

        /**
         * Indexing waits; the page is not a failure: no key, a rejected key, no credit, and since
         * Oct 2026 no internet or OpenRouter down. Before, those two counted as page failures, and
         * 3 of them while offline quarantined the page for good (owner's Go 10.3, 20 and 24 Oct 2026).
         */
        data class Paused(val problem: AiProblem) : Result()

        /** The model refused the page or gave no usable answer: counted, the page is retried later. */
        data class Failed(val reason: String) : Result()
    }

    /** What a failed read means for the page: wait without counting, or count a failure. */
    internal fun resultFor(error: AiResponse.Error, network: NetworkState): Result {
        val problem = AiProblem.of(error, network)
        return if (problem.indexingWaits) Result.Paused(problem) else Result.Failed(error.message)
    }

    suspend fun recognizeText(bitmap: Bitmap): Result =
        com.boox.day.api.ai.LastAiProblem.withScope("background_ocr") {
            val base64 = withContext(Dispatchers.Default) { encodeJpeg(bitmap) }
            val models = ModelConfigProvider.getChain(AiJob.SEARCH_INDEXING)
            recognizeWithModels(base64, models)
        }

    /**
     * Tries each model in [models] and validates the OCR JSON contract *inside* the fallback loop
     * so malformed or empty-object (`{}`) answers fall through to the next model instead of being
     * stored as empty page text (H8).
     */
    internal suspend fun recognizeWithModels(
        base64: String,
        models: List<AiModel>,
        network: () -> NetworkState = { NetworkStatus.current() },
        executeModel: suspend (AiRequest) -> AiResponse<String> = { req -> AiClient.execute(req, promptForKey = false) }
    ): Result {
        if (models.isEmpty()) return Result.Failed("no OCR models configured")
        var creditRefusal: AiResponse.Error.OutOfCredit? = null
        var lastError: AiResponse.Error? = null
        var lastParseFailure: Result.Failed? = null

        for (model in models) {
            currentCoroutineContext().ensureActive()
            if (creditRefusal != null && !OpenRouterAccount.isFreeModel(model.id)) continue
            val baseRequest = aiRequest {
                model(model)
                systemPrompt(SYSTEM_PROMPT)
                userMessageWithImage("Read the handwritten text:", base64)
                jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextWithLanguageSchema)
                zeroDataRetention()
            }
            val request = if ((model as? AiModel.Custom)?.userChoice == true) {
                baseRequest
            } else {
                baseRequest.copy(overrideReasoning = ReasoningConfig.disabled())
            }

            when (val response = executeModel(request)) {
                is AiResponse.Success -> {
                    when (val parsed = parse(response.data)) {
                        is Result.Success -> return parsed
                        is Result.Failed -> {
                            AiPickStatus.noteUnusableAnswer(model)
                            lastParseFailure = parsed
                            Log.w(TAG, "Model ${model.displayName} returned unreadable OCR JSON; trying fallback")
                        }
                        is Result.Paused -> return parsed
                    }
                }
                is AiResponse.Error.NoApiKey,
                is AiResponse.Error.KeyRejected -> return resultFor(response, network())
                is AiResponse.Error.OutOfCredit -> {
                    creditRefusal = response
                    lastError = response
                }
                is AiResponse.Error -> {
                    lastError = response
                }
            }
        }

        val error = creditRefusal ?: lastError
        return error?.let { resultFor(it, network()) }
            ?: lastParseFailure
            ?: Result.Failed("all OCR models failed")
    }

    internal fun parse(data: String): Result {
        return try {
            val json = Json.parseToJsonElement(JsonExtractor.extract(data)).jsonObject
            // Schema requires explicit boolean `hasText` and string `text`; `{}` or missing fields must fail over
            val hasTextElem = json["hasText"] as? JsonPrimitive
            val textElem = json["text"] as? JsonPrimitive
            val hasTextBool = if (hasTextElem != null && !hasTextElem.isString) hasTextElem.booleanOrNull else null
            if (hasTextBool == null || textElem == null || !textElem.isString) {
                Log.w(TAG, "OCR JSON missing required fields (${data.length} chars)")
                return Result.Failed("missing required OCR fields")
            }
            val text = textElem.content.trim()
            val hasText = hasTextBool && text.isNotEmpty()
            val langElem = json["language"] as? JsonPrimitive
            val language = if (langElem != null && langElem.isString) {
                langElem.content.trim().lowercase().take(8).ifBlank { null }
            } else null
            Result.Success(
                text = if (hasText) text else "",
                language = if (hasText) language else null,
                // The model gives no per-word confidence; Cloud Vision's average was stored here
                confidence = if (hasText) 0.9f else 0f,
                blocks = if (hasText) text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() } else 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse OCR answer (${data.length} chars, ${e.javaClass.simpleName})")
            Result.Failed("unreadable answer")
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
