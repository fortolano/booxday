package com.boox.day.api.ai

import com.boox.day.api.NetworkState
import com.boox.day.api.NetworkStatus
import com.boox.day.ocr.OcrQuotaManager
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException

private const val MONTHLY_READS = OcrQuotaManager.MONTHLY_AI_READ_LIMIT

/**
 * Why an AI request failed, in the user's words (Oct 2026). Before this, no Wi-Fi, OpenRouter being
 * down and a model's bad answer all showed "AI service temporarily unavailable", and search indexing
 * counted being offline as a fault of the page (3 of them and the page was never read again).
 *
 * [message] is for dialogs, [shortText] for a status line ("Handwriting not read yet: no internet").
 * [indexingWaits]: search indexing waits and tries again later instead of counting a page failure,
 * because the problem is not the page's.
 */
enum class AiProblem(val message: String, val shortText: String, val indexingWaits: Boolean) {
    NO_NETWORK(
        "No internet. Turn on Wi-Fi, then try again.",
        "no internet (Wi-Fi is off)",
        indexingWaits = true
    ),
    NO_INTERNET(
        "Wi-Fi is connected, but there is no internet. Check the network, then try again.",
        "no internet (Wi-Fi has no connection)",
        indexingWaits = true
    ),
    OPENROUTER_UNREACHABLE(
        "Can't reach OpenRouter. Check the internet connection, then try again.",
        "can't reach OpenRouter",
        indexingWaits = true
    ),
    OPENROUTER_DOWN(
        "OpenRouter isn't answering right now. Try again in a few minutes.",
        "OpenRouter isn't answering",
        indexingWaits = true
    ),
    OPENROUTER_BUSY(
        "OpenRouter is busy. Try again in a minute.",
        "OpenRouter is busy",
        indexingWaits = true
    ),
    NO_KEY(AiResponse.Error.NoApiKey().message, "no OpenRouter key", indexingWaits = true),
    KEY_REJECTED(AiResponse.Error.KeyRejected().message, "OpenRouter key not accepted", indexingWaits = true),
    NO_CREDIT(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, "no OpenRouter credit", indexingWaits = true),

    /** Boox Day's own cap on AI page reads for search (OcrQuotaManager): the rest wait for next month */
    MONTHLY_LIMIT(
        "Boox Day reads up to $MONTHLY_READS pages a month with AI. The rest are read next month.",
        "monthly limit of $MONTHLY_READS AI page reads reached",
        indexingWaits = true
    ),

    /** The request reached a model, which refused it or gave no usable answer */
    MODEL_FAILED(
        "The AI model couldn't answer this. Try again, or pick another model in AI Settings.",
        "the AI model couldn't read it",
        indexingWaits = false
    );

    companion object {

        fun of(error: AiResponse.Error, network: NetworkState = NetworkStatus.current()): AiProblem = when (error) {
            is AiResponse.Error.NetworkError -> connectionProblem(network, error.cause)
            is AiResponse.Error.RateLimitError -> OPENROUTER_BUSY
            is AiResponse.Error.ServiceUnavailable -> OPENROUTER_DOWN
            is AiResponse.Error.ApiError ->
                if (error.code != null && (error.code >= 500 || error.code == 408)) OPENROUTER_DOWN else MODEL_FAILED
            is AiResponse.Error.NoApiKey -> NO_KEY
            is AiResponse.Error.KeyRejected -> KEY_REJECTED
            is AiResponse.Error.OutOfCredit -> NO_CREDIT
            is AiResponse.Error.InvalidRequest,
            is AiResponse.Error.NoContent -> MODEL_FAILED
        }

        /** No answer within the time allowed (a request timeout, or a feature's own time limit). */
        fun timedOut(network: NetworkState = NetworkStatus.current()): AiProblem =
            connectionProblem(network, SocketTimeoutException())

        /** The text to show for [error]: key and credit errors keep their own, more specific words. */
        fun messageFor(error: AiResponse.Error, network: NetworkState = NetworkStatus.current()): String = when (error) {
            is AiResponse.Error.NoApiKey,
            is AiResponse.Error.KeyRejected,
            is AiResponse.Error.OutOfCredit -> error.message
            else -> of(error, network).message
        }

        private fun connectionProblem(network: NetworkState, cause: Throwable?): AiProblem = when (network) {
            NetworkState.NONE -> NO_NETWORK
            NetworkState.NO_INTERNET -> NO_INTERNET
            // Online: a timeout means OpenRouter or the model is slow; anything else (DNS, refused
            // connection) means OpenRouter can't be reached from here
            NetworkState.ONLINE, NetworkState.UNKNOWN ->
                if (cause is SocketTimeoutException || cause is CancellationException) OPENROUTER_DOWN
                else OPENROUTER_UNREACHABLE
        }
    }
}
