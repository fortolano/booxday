package com.boox.day.api.ai

import com.boox.day.api.NetworkState
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the user is told when an AI request fails (Oct 2026). Before, no Wi-Fi, OpenRouter down and
 * a model's bad answer all said "AI service temporarily unavailable".
 */
class AiProblemTest {

    private fun network(cause: Throwable) = AiResponse.Error.NetworkError("Network error: ${cause.message}", cause)

    private val dnsFailure = network(UnknownHostException("Unable to resolve host \"openrouter.ai\""))
    private val connectFailure = network(ConnectException("Failed to connect to openrouter.ai/104.18.2.115:443"))
    private val timeout = network(SocketTimeoutException("timeout"))

    @Test
    fun `no network says to turn on Wi-Fi, whatever the exception`() {
        for (error in listOf(dnsFailure, connectFailure, timeout)) {
            assertEquals(AiProblem.NO_NETWORK, AiProblem.of(error, NetworkState.NONE))
        }
        assertEquals("No internet. Turn on Wi-Fi, then try again.", AiProblem.messageFor(dnsFailure, NetworkState.NONE))
    }

    @Test
    fun `Wi-Fi without internet says so`() {
        assertEquals(AiProblem.NO_INTERNET, AiProblem.of(connectFailure, NetworkState.NO_INTERNET))
    }

    @Test
    fun `online but OpenRouter can't be reached or is slow`() {
        assertEquals(AiProblem.OPENROUTER_UNREACHABLE, AiProblem.of(dnsFailure, NetworkState.ONLINE))
        assertEquals(AiProblem.OPENROUTER_UNREACHABLE, AiProblem.of(connectFailure, NetworkState.UNKNOWN))
        assertEquals(AiProblem.OPENROUTER_DOWN, AiProblem.of(timeout, NetworkState.ONLINE))
        assertEquals(AiProblem.OPENROUTER_DOWN, AiProblem.timedOut(NetworkState.ONLINE))
        assertEquals(AiProblem.NO_NETWORK, AiProblem.timedOut(NetworkState.NONE))
    }

    @Test
    fun `OpenRouter errors`() {
        assertEquals(AiProblem.OPENROUTER_DOWN, AiProblem.of(AiResponse.Error.ApiError(502, "bad gateway"), NetworkState.ONLINE))
        assertEquals(AiProblem.OPENROUTER_DOWN, AiProblem.of(AiResponse.Error.ApiError(408, "timeout"), NetworkState.ONLINE))
        assertEquals(AiProblem.OPENROUTER_BUSY, AiProblem.of(AiResponse.Error.RateLimitError("busy", null), NetworkState.ONLINE))
    }

    @Test
    fun `problems with the request or the answer are the model's`() {
        for (error in listOf(
            AiResponse.Error.ApiError(403, "flagged"),
            AiResponse.Error.ApiError(404, "no such model"),
            AiResponse.Error.ApiError(null, "unknown"),
            AiResponse.Error.InvalidRequest("image too big"),
            AiResponse.Error.NoContent()
        )) {
            assertEquals(error.toString(), AiProblem.MODEL_FAILED, AiProblem.of(error, NetworkState.ONLINE))
        }
    }

    @Test
    fun `key and credit errors keep their own words`() {
        val credit = AiResponse.Error.OutOfCredit.from("openrouter_key_limit", null)
        assertEquals(credit.message, AiProblem.messageFor(credit, NetworkState.ONLINE))
        assertEquals(AiResponse.Error.KeyRejected().message, AiProblem.messageFor(AiResponse.Error.KeyRejected(), NetworkState.ONLINE))
        assertEquals(AiProblem.NO_KEY, AiProblem.of(AiResponse.Error.NoApiKey(), NetworkState.ONLINE))
    }

    @Test
    fun `indexing waits on everything but a model failure`() {
        AiProblem.entries.forEach { problem ->
            if (problem == AiProblem.MODEL_FAILED) assertFalse(problem.indexingWaits) else assertTrue(problem.name, problem.indexingWaits)
        }
    }
}
