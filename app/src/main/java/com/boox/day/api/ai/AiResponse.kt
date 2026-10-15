package com.boox.day.api.ai

/**
 * Token usage statistics from the API response.
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Sealed class representing AI API responses.
 * Provides type-safe handling of success and various error conditions.
 */
sealed class AiResponse<out T> {

    /**
     * Successful response containing the AI-generated content.
     */
    data class Success<T>(
        val data: T,
        val model: String,
        val usage: TokenUsage?
    ) : AiResponse<T>()

    /**
     * Error responses with granular error types for proper handling.
     */
    sealed class Error : AiResponse<Nothing>() {
        abstract val message: String

        /** API returned an error (non-2xx response or error in body) */
        data class ApiError(
            val code: Int?,
            override val message: String
        ) : Error()

        /** Network failure (timeout, no connection, etc.) */
        data class NetworkError(
            override val message: String,
            val cause: Throwable?
        ) : Error()

        /** Rate limit exceeded (HTTP 429) */
        data class RateLimitError(
            override val message: String,
            val retryAfterSeconds: Int?
        ) : Error()

        /** Invalid request format or parameters (HTTP 400) */
        data class InvalidRequest(
            override val message: String
        ) : Error()

        /** API returned success but no content in response */
        data class NoContent(
            override val message: String = "No content in response"
        ) : Error()

        /** AI services temporarily unavailable (no API key, server unreachable) */
        data class ServiceUnavailable(
            override val message: String = "Our AI hamsters are taking a power nap! 🐹💤 We'll have them back shortly. Please check your internet connection or try again in a few minutes."
        ) : Error() {
            companion object {
                /** Friendly messages for different scenarios */
                fun noApiKey() = ServiceUnavailable(
                    "Our AI hamsters are taking a power nap! 🐹💤 " +
                    "We couldn't connect to our AI services. " +
                    "Please check your internet connection or try again in a few minutes."
                )

                fun serverUnreachable() = ServiceUnavailable(
                    "The AI hamsters can't find their way home! 🐹🏠 " +
                    "Our servers seem to be having a moment. " +
                    "Please try again shortly - they usually wake up fast!"
                )
            }
        }

        // The user's own OpenRouter key (Oct 2026): problems no other model or retry can fix

        /** No OpenRouter key saved: AI features need the user's own key */
        data class NoApiKey(
            override val message: String = "Add your OpenRouter key in AI Settings to use AI features."
        ) : Error()

        /** OpenRouter did not accept the saved key (HTTP 401) */
        data class KeyRejected(
            override val message: String = "OpenRouter did not accept your key. Check it in AI Settings."
        ) : Error()

        /**
         * OpenRouter refused a paid request for credit (HTTP 402). [limitSource] and [reason] are
         * OpenRouter's (Oct 2026): no credit left, the key's spending limit, or a request too big
         * for the credit OpenRouter holds while requests run. Free models are not refused this way.
         */
        data class OutOfCredit(
            override val message: String = NO_CREDIT_MESSAGE,
            val limitSource: String? = null,
            val reason: String? = null
        ) : Error() {
            /** True when the account or key can't pay for paid models now, not just this request */
            val isAccountOutOfCredit: Boolean
                get() = limitSource != IN_FLIGHT_BUDGET && reason != WEIGHT_EXCEEDS_BUDGET

            companion object {
                const val NO_CREDIT_MESSAGE =
                    "Your OpenRouter account is out of credit. Add credit at openrouter.ai/settings/credits, then try again."
                private const val IN_FLIGHT_BUDGET = "openrouter_in_flight_budget"
                private const val KEY_LIMIT = "openrouter_key_limit"
                private const val WEIGHT_EXCEEDS_BUDGET = "weight_exceeds_budget"

                fun from(limitSource: String?, reason: String?) = OutOfCredit(
                    message = when {
                        limitSource == IN_FLIGHT_BUDGET ->
                            "Too many paid AI requests are running for your OpenRouter credit. Try again in a moment."
                        reason == WEIGHT_EXCEEDS_BUDGET ->
                            "This request is too big for your OpenRouter credit. Lower Max output tokens in AI Settings, or add credit."
                        limitSource == KEY_LIMIT ->
                            "This OpenRouter key has reached its spending limit. Raise it at openrouter.ai/settings/keys, then try again."
                        else -> NO_CREDIT_MESSAGE
                    },
                    limitSource = limitSource,
                    reason = reason
                )
            }
        }
    }

    // Convenience properties
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    /** Returns the data if successful, null otherwise */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the error if failed, null otherwise */
    fun errorOrNull(): Error? = this as? Error

    /**
     * Transforms the response using the appropriate handler.
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (Error) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(this)
    }

    /**
     * Executes action if successful, returns self for chaining.
     */
    inline fun onSuccess(action: (T) -> Unit): AiResponse<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Executes action if error, returns self for chaining.
     */
    inline fun onError(action: (Error) -> Unit): AiResponse<T> {
        if (this is Error) action(this)
        return this
    }
}
