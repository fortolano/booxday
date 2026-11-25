package com.boox.day.ocr

import com.boox.day.api.NetworkState
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page read while offline or while OpenRouter is down waits; it is not a failure of the page.
 * Until Oct 2026 these counted, and 3 of them quarantined the page for good (owner's Go 10.3:
 * 7 pages, all "Network error", never read).
 */
class OpenRouterOcrServiceWaitTest {

    private val service = OpenRouterOcrService()

    private fun paused(error: AiResponse.Error, network: NetworkState): AiProblem {
        val result = service.resultFor(error, network)
        assertTrue("$error -> $result", result is OpenRouterOcrService.Result.Paused)
        return (result as OpenRouterOcrService.Result.Paused).problem
    }

    @Test
    fun `no internet waits`() {
        val dns = AiResponse.Error.NetworkError("Network error: Unable to resolve host", UnknownHostException())
        val connect = AiResponse.Error.NetworkError("Network error: Failed to connect", ConnectException())
        assertEquals(AiProblem.NO_NETWORK, paused(dns, NetworkState.NONE))
        assertEquals(AiProblem.NO_INTERNET, paused(connect, NetworkState.NO_INTERNET))
        assertEquals(AiProblem.OPENROUTER_UNREACHABLE, paused(connect, NetworkState.ONLINE))
    }

    @Test
    fun `OpenRouter down or busy waits`() {
        assertEquals(AiProblem.OPENROUTER_DOWN, paused(AiResponse.Error.ApiError(503, "unavailable"), NetworkState.ONLINE))
        assertEquals(AiProblem.OPENROUTER_BUSY, paused(AiResponse.Error.RateLimitError("busy", null), NetworkState.ONLINE))
    }

    @Test
    fun `key and credit still wait`() {
        assertEquals(AiProblem.NO_KEY, paused(AiResponse.Error.NoApiKey(), NetworkState.ONLINE))
        assertEquals(AiProblem.KEY_REJECTED, paused(AiResponse.Error.KeyRejected(), NetworkState.ONLINE))
        assertEquals(AiProblem.NO_CREDIT, paused(AiResponse.Error.OutOfCredit(), NetworkState.ONLINE))
    }

    @Test
    fun `a model that refuses the page or answers badly still counts`() {
        for (error in listOf(
            AiResponse.Error.ApiError(403, "flagged"),
            AiResponse.Error.InvalidRequest("bad image"),
            AiResponse.Error.NoContent()
        )) {
            val result = service.resultFor(error, NetworkState.ONLINE)
            assertEquals(OpenRouterOcrService.Result.Failed(error.message), result)
        }
    }

    @Test
    fun `empty object or malformed OCR JSON falls through to backup model instead of storing empty text`() = runBlocking {
        val caps = setOf(com.boox.day.api.ai.AiCapability.VISION, com.boox.day.api.ai.AiCapability.JSON_OUTPUT)
        val m1 = AiModel.Custom("first/model", "First", caps, com.boox.day.api.ai.AiConfig())
        val m2 = AiModel.Custom("second/model", "Second", caps, com.boox.day.api.ai.AiConfig())
        val tried = mutableListOf<String>()

        val result = service.recognizeWithModels(
            base64 = "AA==",
            models = listOf(m1, m2),
            network = { NetworkState.ONLINE }
        ) { req ->
            tried += req.model.id
            when (req.model.id) {
                "first/model" -> AiResponse.Success("{}", req.model.id, null)
                else -> AiResponse.Success("""{"hasText": true, "language": "en", "text": "Hello ink"}""", req.model.id, null)
            }
        }

        assertEquals(listOf("first/model", "second/model"), tried)
        assertTrue(result is OpenRouterOcrService.Result.Success)
        assertEquals("Hello ink", (result as OpenRouterOcrService.Result.Success).text)
    }

    @Test
    fun `explicit hasText false still succeeds as genuine blank page`() {
        val parsed = service.parse("""{"hasText": false, "language": "", "text": ""}""")
        assertTrue(parsed is OpenRouterOcrService.Result.Success)
        assertEquals("", (parsed as OpenRouterOcrService.Result.Success).text)
    }
}
