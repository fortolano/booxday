package com.boox.day.api.ai

import android.util.Log
import com.boox.day.api.ExtraBody
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.OpenRouterClient
import com.boox.day.api.ProviderConfig
import com.boox.day.api.RawVisionChatRequest
import com.boox.day.api.ReasoningConfig
import com.boox.day.api.ResponseFormat
import com.boox.day.api.createAudioMessage
import com.boox.day.api.createTextMessage
import com.boox.day.api.createVisionMessage
import com.boox.day.security.UserOpenRouterKey
import com.boox.day.ui.AiKeyGate
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level AI client for executing requests via OpenRouter.
 *
 * This client:
 * - Translates [AiRequest] objects into OpenRouter API format
 * - Handles response parsing with granular error types
 * - Logs requests and responses for debugging
 *
 * Usage:
 * ```kotlin
 * val request = aiRequest {
 *     model(AiModel.GrokVision)
 *     userMessageWithImage("Extract text:", base64Image)
 *     enableReasoning()
 * }
 *
 * when (val response = AiClient.execute(request)) {
 *     is AiResponse.Success -> println("Result: ${response.data}")
 *     is AiResponse.Error -> println("Error: ${response.message}")
 * }
 * ```
 */
object AiClient {
    private const val TAG = "AiClient"

    /**
     * Execute an AI request and return the response.
     *
     * AI runs only on the user's own OpenRouter key (Oct 2026). Without one, or when OpenRouter
     * rejects it, the request is not retried and the "Add your OpenRouter key" popup opens
     * (unless [promptForKey] is false, for work the user did not start, such as search indexing).
     *
     * @param request The [AiRequest] to execute
     * @param promptForKey Open the key popup on a missing or rejected key
     * @return [AiResponse] with the result or error
     */
    suspend fun execute(request: AiRequest, promptForKey: Boolean = true): AiResponse<String> {
        Log.d(TAG, "Executing request with model: ${request.model.displayName}")
        Log.d(TAG, "Messages: ${request.messages.size}, JSON output: ${request.jsonOutput}, Reasoning: ${request.enableReasoning}")

        // The key may still be loading if this runs right after the app started
        withContext(Dispatchers.IO) { UserOpenRouterKey.ensureLoaded() }
        if (!OpenRouterClient.hasApiKey()) {
            Log.w(TAG, "No OpenRouter key saved")
            if (promptForKey) AiKeyGate.open(AiKeyGate.Reason.NO_KEY)
            return AiResponse.Error.NoApiKey()
        }

        val result = try {
            val apiRequest = buildApiRequest(request)
            Log.d(TAG, "Calling OpenRouter API...")

            val response = OpenRouterClient.service.chatCompletion(apiRequest)
            Log.d(TAG, "API response code: ${response.code()}")

            if (response.isSuccessful) {
                handleSuccessResponse(response.body(), request.model.id)
            } else {
                handleErrorResponse(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during AI request", e)
            AiResponse.Error.NetworkError(
                message = "Network error: ${e.message}",
                cause = e
            )
        }
        if (result is AiResponse.Error.KeyRejected && promptForKey) {
            AiKeyGate.open(AiKeyGate.Reason.KEY_REJECTED)
        }
        noteCreditState(request.model.id, result)
        // A picked model's failure shows on its AI Models row until it answers again (Oct 2026)
        com.boox.day.api.AiPickStatus.noteResult(request.model, result)
        return result
    }

    /**
     * What a paid model's answer says about the account's credit (Oct 2026): a refusal for credit
     * makes Boox Day's choice use free models for a while (OpenRouterAccount), an answer ends that.
     */
    internal fun noteCreditState(modelId: String, result: AiResponse<String>) {
        if (OpenRouterAccount.isFreeModel(modelId)) return
        when {
            result is AiResponse.Error.OutOfCredit && result.isAccountOutOfCredit ->
                OpenRouterAccount.onPaidRequestRefused()
            result is AiResponse.Success -> OpenRouterAccount.onPaidRequestSucceeded()
        }
    }

    /**
     * Handle a successful HTTP response (may still contain API-level errors).
     */
    internal fun handleSuccessResponse(
        body: com.boox.day.api.ChatResponse?,
        requestModelId: String
    ): AiResponse<String> {
        // DEBUG: Log full response structure for troubleshooting
        Log.d(TAG, "═══════════════ RESPONSE DEBUG ═══════════════")
        Log.d(TAG, "Model ID: ${body?.model ?: "null"}")
        Log.d(TAG, "Choices count: ${body?.choices?.size ?: 0}")
        body?.choices?.forEachIndexed { idx, choice ->
            Log.d(TAG, "Choice[$idx] finish_reason: ${choice.finishReason}")
            Log.d(TAG, "Choice[$idx] message.role: ${choice.message?.role}")
            Log.d(TAG, "Choice[$idx] message.content: '${choice.message?.content?.take(200) ?: "NULL"}'...")
        }
        Log.d(TAG, "Usage: prompt=${body?.usage?.promptTokens}, completion=${body?.usage?.completionTokens}, total=${body?.usage?.totalTokens}")
        Log.d(TAG, "Error: ${body?.error?.code} - ${body?.error?.message}")
        Log.d(TAG, "═══════════════════════════════════════════════")

        return when {
            // Check for API-level error in response body
            body?.error != null -> {
                val code = body.error.code
                val message = body.error.message ?: "Unknown API error"
                Log.e(TAG, "API returned error: $code - $message")

                when (code) {
                    429 -> AiResponse.Error.RateLimitError(
                        message = message,
                        retryAfterSeconds = null
                    )
                    401 -> AiResponse.Error.KeyRejected()
                    402 -> outOfCredit(body.error)
                    else -> AiResponse.Error.ApiError(
                        code = code,
                        message = message
                    )
                }
            }

            // Extract content from response
            // GPT-OSS and other reasoning models put output in 'reasoning' field, not 'content'
            else -> {
                val choice = body?.choices?.firstOrNull()
                val message = choice?.message
                val content = message?.content
                val reasoning = message?.reasoning

                // Use content if available, otherwise fall back to reasoning field
                val actualContent = when {
                    !content.isNullOrBlank() -> content
                    // Oct 2026: the model spent the whole output limit reasoning. Its reasoning is
                    // not the answer (it could end up in a note), so the next model is tried.
                    choice?.finishReason == "length" -> {
                        Log.w(TAG, "Answer cut off: output limit used up before any answer (${reasoning?.length ?: 0} chars of reasoning)")
                        return AiResponse.Error.NoContent(
                            "The answer was cut off: the model used all its output tokens before answering."
                        )
                    }
                    !reasoning.isNullOrBlank() -> {
                        Log.d(TAG, "Content was empty, using reasoning field instead (${reasoning.length} chars)")
                        reasoning
                    }
                    else -> null
                }

                if (actualContent.isNullOrBlank()) {
                    Log.w(TAG, "No content in response - content='$content', reasoning='${reasoning?.take(100)}'")
                    AiResponse.Error.NoContent()
                } else {
                    val usage = body?.usage?.let {
                        TokenUsage(
                            promptTokens = it.promptTokens ?: 0,
                            completionTokens = it.completionTokens ?: 0,
                            totalTokens = it.totalTokens ?: 0
                        )
                    }

                    Log.i(TAG, "SUCCESS! Received ${actualContent.length} chars, usage: ${usage?.totalTokens ?: "unknown"} tokens")

                    AiResponse.Success(
                        data = actualContent,
                        model = body?.model ?: requestModelId,
                        usage = usage
                    )
                }
            }
        }
    }

    /**
     * OpenRouter's 402 with its reason (Oct 2026, OpenRouter docs: limits): no credit, the key's
     * spending limit, or a request too big for the credit held while requests run.
     */
    private fun outOfCredit(error: com.boox.day.api.ApiError?): AiResponse.Error.OutOfCredit {
        val metadata = error?.metadata
        return AiResponse.Error.OutOfCredit.from(
            limitSource = metadata?.get("limit_source") as? String,
            reason = metadata?.get("reason") as? String
        )
    }

    /** The `error` object of an OpenRouter error body, or null when the body is not JSON. */
    private fun parseErrorBody(errorBody: String?): com.boox.day.api.ApiError? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            Gson().fromJson(errorBody, com.boox.day.api.ChatResponse::class.java)?.error
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Handle HTTP error responses.
     */
    internal fun handleErrorResponse(code: Int, errorBody: String?): AiResponse<String> {
        val message = errorBody ?: "Unknown error"
        Log.e(TAG, "HTTP error $code: $message")

        return when (code) {
            429 -> AiResponse.Error.RateLimitError(
                message = "Rate limit exceeded: $message",
                retryAfterSeconds = null
            )
            400 -> AiResponse.Error.InvalidRequest(
                message = message
            )
            // The user's own key: rejected, or no credit left on their account
            401 -> AiResponse.Error.KeyRejected()
            402 -> outOfCredit(parseErrorBody(errorBody))
            403 -> AiResponse.Error.ApiError(
                code = code,
                message = "Authentication error: $message"
            )
            else -> AiResponse.Error.ApiError(
                code = code,
                message = message
            )
        }
    }

    /**
     * Convert [AiRequest] to OpenRouter API format.
     */
    internal fun buildApiRequest(request: AiRequest): RawVisionChatRequest {
        // Convert messages to API format
        val messages = request.messages.map { msg ->
            when (msg) {
                is AiMessage.System -> createTextMessage("system", msg.content)
                is AiMessage.User -> createTextMessage("user", msg.content)
                is AiMessage.UserWithImage -> createVisionMessage("user", msg.text, msg.imageBase64)
                is AiMessage.UserWithAudio -> createAudioMessage("user", msg.text, msg.audioBase64, msg.mimeType)
                is AiMessage.Assistant -> createTextMessage("assistant", msg.content)
            }
        }

        // Build reasoning config at ROOT level
        // Priority: 1. overrideReasoning (set by the feature), 2. the user's level for a model they
        // picked (Oct 2026; none = the model's own default), 3. enableReasoning flag
        val pickedModel = (request.model as? AiModel.Custom)?.takeIf { it.userChoice }
        val reasoning = when {
            request.overrideReasoning != null -> {
                Log.d(TAG, "Using overrideReasoning: ${request.overrideReasoning}")
                request.overrideReasoning
            }
            pickedModel != null -> {
                Log.d(TAG, "Using the reasoning level picked for ${pickedModel.id}: ${pickedModel.reasoning}")
                pickedModel.reasoning
            }
            // If enableReasoning is set but no override, use enabled() as generic fallback
            // The server config should provide proper params; this is just a safety net
            request.enableReasoning -> {
                Log.d(TAG, "enableReasoning=true but no override, using generic enabled()")
                ReasoningConfig.enabled()
            }
            else -> null
        }

        // Build response format:
        // 1. JSON schema takes priority (structured output)
        // 2. Basic JSON output (just valid JSON)
        // 3. No format specified (free text)
        val responseFormat = when {
            request.jsonSchema != null -> ResponseFormat.jsonSchema(
                name = request.jsonSchema.name,
                schema = request.jsonSchema.schema,
                strict = request.jsonSchema.strict
            )
            request.jsonOutput -> ResponseFormat.jsonObject()
            else -> null
        }

        // OpenRouter routes each model to any of its providers (Oct 2026). Every request now uses the
        // user's own key, and the old pin to the model's prefix ("google", "x-ai") named no real
        // provider slug (Google's are google-vertex and google-ai-studio), so it is gone.
        val providerConfig = if (request.zeroDataRetention) {
            Log.d(TAG, "Zero data retention providers only")
            ProviderConfig(zdr = true)
        } else {
            null
        }

        // Get temperature and maxTokens from config (defaults to model's default)
        val config = request.config

        return RawVisionChatRequest(
            model = request.model.id,
            messages = messages,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            reasoning = reasoning,
            responseFormat = responseFormat,
            provider = providerConfig
        )
    }
}
