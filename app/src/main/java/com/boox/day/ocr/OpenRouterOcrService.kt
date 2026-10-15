package com.boox.day.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.boox.day.ai.JsonExtractor
import com.boox.day.ai.StructuredOutputSchemas
import com.boox.day.api.ReasoningConfig
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiConfig
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

        /** Default model for reading pages (chosen by the 14 Oct 2026 test). */
        const val INDEXING_MODEL_ID = "google/gemini-3.1-flash-lite"

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

        private val MODEL = AiModel.Custom(
            id = INDEXING_MODEL_ID,
            displayName = "Gemini 3.1 Flash Lite",
            capabilities = setOf(AiCapability.VISION, AiCapability.JSON_OUTPUT),
            defaultConfig = AiConfig(temperature = 0f, maxTokens = 3000)
        )
    }

    sealed class Result {
        data class Success(val text: String, val language: String?, val confidence: Float, val blocks: Int) : Result()

        /** No key, a rejected key or no credit: indexing waits; the page is not a failure. */
        data class Paused(val reason: String) : Result()

        /** Network or model error: the page is retried later. */
        data class Failed(val reason: String) : Result()
    }

    suspend fun recognizeText(bitmap: Bitmap): Result {
        val base64 = withContext(Dispatchers.Default) { encodeJpeg(bitmap) }
        val request = aiRequest {
            model(MODEL)
            systemPrompt(SYSTEM_PROMPT)
            userMessageWithImage("Read the handwritten text:", base64)
            jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextWithLanguageSchema)
            zeroDataRetention()
        }.copy(overrideReasoning = ReasoningConfig.disabled())

        return when (val response = AiClient.execute(request, promptForKey = false)) {
            is AiResponse.Success -> parse(response.data)
            is AiResponse.Error.NoApiKey -> Result.Paused("no OpenRouter key")
            is AiResponse.Error.KeyRejected -> Result.Paused("OpenRouter key rejected")
            is AiResponse.Error.OutOfCredit -> Result.Paused("OpenRouter credit used up")
            is AiResponse.Error -> Result.Failed(response.message)
        }
    }

    private fun parse(data: String): Result {
        return try {
            val json = JSONObject(JsonExtractor.extract(data))
            val text = json.optString("text", "").trim()
            val hasText = json.optBoolean("hasText", text.isNotEmpty()) && text.isNotEmpty()
            val language = json.optString("language", "").trim().lowercase().take(8).ifBlank { null }
            Result.Success(
                text = if (hasText) text else "",
                language = if (hasText) language else null,
                // The model gives no per-word confidence; Cloud Vision's average was stored here
                confidence = if (hasText) 0.9f else 0f,
                blocks = if (hasText) text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() } else 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse OCR answer: ${e.message}")
            Result.Failed("unreadable answer")
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
