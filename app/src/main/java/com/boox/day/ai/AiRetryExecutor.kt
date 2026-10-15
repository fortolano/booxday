package com.boox.day.ai

import android.util.Log
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.AiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Implements the **Rule of 3+3** for AI service calls:
 * - 3 models per modality (from server via ModelConfigProvider)
 * - 3 cycles through those models with 500ms delay between cycles
 * - User-friendly error when all 9 attempts fail
 *
 * This ensures maximum resilience while maintaining pure server control
 * over model selection.
 */
object AiRetryExecutor {
    private const val TAG = "AiRetryExecutor"

    /** Number of complete cycles through all models */
    private const val MAX_CYCLES = 3

    /** Delay between cycles in milliseconds */
    private const val INTER_CYCLE_DELAY_MS = 500L

    /** User-friendly error message when all attempts fail */
    const val SERVICE_UNAVAILABLE_MESSAGE = "AI service temporarily unavailable. Please try again later."

    /**
     * Result of an AI execution with retry logic.
     */
    sealed class RetryResult<out T> {
        data class Success<T>(val data: T, val model: AiModel, val cycle: Int, val attempt: Int) : RetryResult<T>()
        data class Failure(val message: String, val lastError: String?) : RetryResult<Nothing>()
    }

    /**
     * Execute an AI request with 3×3 retry logic.
     *
     * @param models List of models to try (typically 3 from ModelConfigProvider)
     * @param buildRequest Lambda that builds the AiRequest for a given model
     * @param processResponse Lambda that processes a successful response, returns null if response is invalid
     * @return RetryResult with success data or failure message
     */
    suspend fun <T> execute(
        models: List<AiModel>,
        buildRequest: (AiModel) -> AiRequest,
        processResponse: (AiResponse.Success<String>, AiModel) -> T?
    ): RetryResult<T> {
        if (models.isEmpty()) {
            Log.e(TAG, "No models provided for retry execution")
            return RetryResult.Failure(SERVICE_UNAVAILABLE_MESSAGE, "No models configured")
        }

        var lastError: String? = null
        var totalAttempts = 0

        for (cycle in 1..MAX_CYCLES) {
            coroutineContext.ensureActive()
            Log.d(TAG, "Starting cycle $cycle/$MAX_CYCLES")

            for ((index, model) in models.withIndex()) {
                totalAttempts++
                val attemptInCycle = index + 1
                Log.d(TAG, "Cycle $cycle, attempt $attemptInCycle/${models.size}: ${model.displayName}")

                val request = buildRequest(model)

                when (val response = AiClient.execute(request)) {
                    is AiResponse.Success -> {
                        val result = processResponse(response, model)
                        if (result != null) {
                            Log.i(TAG, "Success on cycle $cycle, attempt $attemptInCycle (${model.displayName})")
                            return RetryResult.Success(result, model, cycle, attemptInCycle)
                        } else {
                            Log.w(TAG, "${model.displayName} returned invalid response, trying next...")
                            lastError = "Invalid response from ${model.displayName}"
                        }
                    }
                    is AiResponse.Error.RateLimitError -> {
                        Log.w(TAG, "${model.displayName} rate limited")
                        lastError = "Rate limited: ${response.message}"
                    }
                    is AiResponse.Error.NetworkError -> {
                        Log.w(TAG, "${model.displayName} network error: ${response.message}")
                        lastError = "Network error: ${response.message}"
                    }
                    is AiResponse.Error.ServiceUnavailable -> {
                        Log.w(TAG, "${model.displayName} service unavailable: ${response.message}")
                        return RetryResult.Failure(response.message, response.message)
                    }
                    // The user's own key: no key, a rejected key or no credit. No other model can help.
                    is AiResponse.Error.NoApiKey,
                    is AiResponse.Error.KeyRejected,
                    is AiResponse.Error.OutOfCredit -> {
                        Log.w(TAG, "OpenRouter key problem: ${response.message}")
                        return RetryResult.Failure(response.message, response.message)
                    }
                    is AiResponse.Error -> {
                        Log.w(TAG, "${model.displayName} error: ${response.message}")
                        lastError = response.message
                    }
                }
            }

            // Wait between cycles (but not after the last cycle)
            if (cycle < MAX_CYCLES) {
                Log.d(TAG, "Cycle $cycle complete, waiting ${INTER_CYCLE_DELAY_MS}ms before next cycle...")
                delay(INTER_CYCLE_DELAY_MS)
            }
        }

        Log.e(TAG, "All $totalAttempts attempts failed. Last error: $lastError")
        return RetryResult.Failure(SERVICE_UNAVAILABLE_MESSAGE, lastError)
    }

    /**
     * Simplified execute for cases where response validation is straightforward.
     * Returns the raw response data string on success.
     */
    suspend fun executeSimple(
        models: List<AiModel>,
        buildRequest: (AiModel) -> AiRequest
    ): RetryResult<String> {
        return execute(models, buildRequest) { response, _ ->
            val data = response.data.trim()
            if (data.isNotBlank()) data else null
        }
    }
}
