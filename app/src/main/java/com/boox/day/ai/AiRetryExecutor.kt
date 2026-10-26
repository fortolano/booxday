package com.boox.day.ai

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.boox.day.api.AiPickStatus
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.AiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Implements the **Rule of 3+3** for AI service calls:
 * - The job's models from ModelConfigProvider (the user's pick, then BooxDay's choice)
 * - 3 cycles through those models with 500ms delay between cycles
 * - User-friendly error when all attempts fail
 *
 * The user's own OpenRouter key (Oct 2026): a missing or rejected key stops at once. When
 * OpenRouter refuses a paid model for credit, only free models are tried after it, and accounts
 * without credit get one pass (free models allow 50 requests a day).
 */
object AiRetryExecutor {
    private const val TAG = "AiRetryExecutor"

    /** Number of complete cycles through all models */
    private const val MAX_CYCLES = 3

    /** Delay between cycles in milliseconds */
    private const val INTER_CYCLE_DELAY_MS = 500L

    /** User-friendly error message when all attempts fail */
    const val SERVICE_UNAVAILABLE_MESSAGE = "AI service temporarily unavailable. Please try again later."

    /** Sends one request (request, promptForKey); unit tests replace it. */
    @VisibleForTesting
    internal var send: suspend (AiRequest, Boolean) -> AiResponse<String> =
        { request, promptForKey -> AiClient.execute(request, promptForKey) }

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
        // What the user is told if every attempt fails (Oct 2026: no Wi-Fi, OpenRouter down or a
        // model's bad answer all said "temporarily unavailable" before)
        var lastProblem: AiProblem? = null
        var totalAttempts = 0
        var creditRefusal: AiResponse.Error.OutOfCredit? = null
        // No credit: one pass, to save the free models' daily allowance
        var cycles = if (OpenRouterAccount.isFreeOnly()) 1 else MAX_CYCLES

        for (cycle in 1..MAX_CYCLES) {
            if (cycle > cycles) break
            coroutineContext.ensureActive()
            Log.d(TAG, "Starting cycle $cycle/$cycles")

            for ((index, model) in models.withIndex()) {
                // After a credit refusal only free models can answer
                if (creditRefusal != null && !OpenRouterAccount.isFreeModel(model.id)) continue
                totalAttempts++
                val attemptInCycle = index + 1
                Log.d(TAG, "Cycle $cycle, attempt $attemptInCycle/${models.size}: ${model.displayName}")

                val request = buildRequest(model)

                when (val response = send(request, true)) {
                    is AiResponse.Success -> {
                        val result = processResponse(response, model)
                        if (result != null) {
                            Log.i(TAG, "Success on cycle $cycle, attempt $attemptInCycle (${model.displayName})")
                            return RetryResult.Success(result, model, cycle, attemptInCycle)
                        } else {
                            Log.w(TAG, "${model.displayName} returned invalid response, trying next...")
                            lastError = "Invalid response from ${model.displayName}"
                            lastProblem = AiProblem.MODEL_FAILED
                            AiPickStatus.noteUnusableAnswer(model)
                        }
                    }
                    is AiResponse.Error.RateLimitError -> {
                        Log.w(TAG, "${model.displayName} rate limited")
                        lastError = "Rate limited: ${response.message}"
                        lastProblem = AiProblem.of(response)
                    }
                    is AiResponse.Error.NetworkError -> {
                        Log.w(TAG, "${model.displayName} network error: ${response.message}")
                        lastError = "Network error: ${response.message}"
                        lastProblem = AiProblem.of(response)
                    }
                    is AiResponse.Error.ServiceUnavailable -> {
                        Log.w(TAG, "${model.displayName} service unavailable: ${response.message}")
                        return RetryResult.Failure(response.message, response.message)
                    }
                    // The user's own key: missing or rejected. No other model can help.
                    is AiResponse.Error.NoApiKey,
                    is AiResponse.Error.KeyRejected -> {
                        Log.w(TAG, "OpenRouter key problem: ${response.message}")
                        return RetryResult.Failure(response.message, response.message)
                    }
                    // Refused for credit: free models still work (before Oct 2026 this ended here,
                    // so an account without credit never reached the free models)
                    is AiResponse.Error.OutOfCredit -> {
                        Log.w(TAG, "${model.displayName} refused for credit: ${response.message}")
                        creditRefusal = response
                        lastError = response.message
                        if (models.none { OpenRouterAccount.isFreeModel(it.id) }) {
                            return RetryResult.Failure(response.message, response.message)
                        }
                        cycles = minOf(cycles, cycle + 1)
                    }
                    is AiResponse.Error -> {
                        Log.w(TAG, "${model.displayName} error: ${response.message}")
                        lastError = response.message
                        lastProblem = AiProblem.of(response)
                    }
                }
            }

            // Wait between cycles (but not after the last cycle)
            if (cycle < cycles) {
                Log.d(TAG, "Cycle $cycle complete, waiting ${INTER_CYCLE_DELAY_MS}ms before next cycle...")
                delay(INTER_CYCLE_DELAY_MS)
            }
        }

        Log.e(TAG, "All $totalAttempts attempts failed. Last error: $lastError")
        // A credit refusal says what to do (add credit); the free models' errors usually don't
        return RetryResult.Failure(creditRefusal?.message ?: lastProblem?.message ?: SERVICE_UNAVAILABLE_MESSAGE, lastError)
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

    /**
     * One pass through [models] for features that handle the answer themselves (Oct 2026: voice,
     * memo context, search indexing used a single model before): the first answer wins. A missing
     * or rejected key stops at once; after a credit refusal only free models are tried.
     *
     * @return the first answer, else the credit refusal if there was one, else the last error
     */
    suspend fun executeWithBackup(
        models: List<AiModel>,
        promptForKey: Boolean = true,
        buildRequest: (AiModel) -> AiRequest
    ): AiResponse<String> {
        var creditRefusal: AiResponse.Error.OutOfCredit? = null
        var lastError: AiResponse.Error = AiResponse.Error.ApiError(null, SERVICE_UNAVAILABLE_MESSAGE)
        for (model in models) {
            coroutineContext.ensureActive()
            if (creditRefusal != null && !OpenRouterAccount.isFreeModel(model.id)) continue
            when (val response = send(buildRequest(model), promptForKey)) {
                is AiResponse.Success -> return response
                is AiResponse.Error.NoApiKey,
                is AiResponse.Error.KeyRejected -> return response
                is AiResponse.Error.OutOfCredit -> {
                    Log.w(TAG, "${model.displayName} refused for credit: ${response.message}")
                    creditRefusal = response
                    lastError = response
                }
                is AiResponse.Error -> {
                    Log.w(TAG, "${model.displayName} failed, trying the next model: ${response.message}")
                    lastError = response
                }
            }
        }
        return creditRefusal ?: lastError
    }
}
