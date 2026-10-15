package com.boox.day.ai

import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiConfig
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Trying the next model (Oct 2026): a credit refusal moves on to free models instead of ending
 * the action, and accounts without credit get one pass.
 */
class AiRetryExecutorTest {

    private val originalSend = AiRetryExecutor.send
    private val sent = mutableListOf<String>()

    private fun model(id: String) = AiModel.Custom(
        id = id,
        displayName = id,
        capabilities = setOf(AiCapability.TEXT, AiCapability.JSON_OUTPUT),
        defaultConfig = AiConfig()
    )

    private val paidA = model("google/gemini-3.1-flash-lite")
    private val paidB = model("openai/gpt-5-nano-custom")
    private val free = model("nvidia/nemotron-3-super-120b-a12b:free")

    private fun build(model: AiModel): AiRequest = aiRequest {
        model(model)
        userMessage("Hello")
    }

    /** Answers by model id; unknown ids get a generic error. */
    private fun answers(vararg byId: Pair<String, AiResponse<String>>) {
        val map = byId.toMap()
        AiRetryExecutor.send = { request, _ ->
            sent += request.model.id
            map[request.model.id] ?: AiResponse.Error.ApiError(500, "server error")
        }
    }

    private fun ok(model: String) = AiResponse.Success("answer from $model", model, null)

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @Test
    fun `no credit moves on to the free model and skips the other paid ones`() = runBlocking {
        answers(paidA.id to AiResponse.Error.OutOfCredit(), free.id to ok(free.id))
        val result = AiRetryExecutor.executeSimple(listOf(paidA, paidB, free), ::build)
        assertEquals("answer from ${free.id}", (result as AiRetryExecutor.RetryResult.Success).data)
        assertEquals(listOf(paidA.id, free.id), sent)
    }

    @Test
    fun `no credit and no free model ends with the credit message`() = runBlocking {
        answers(paidA.id to AiResponse.Error.OutOfCredit())
        val result = AiRetryExecutor.executeSimple(listOf(paidA, paidB), ::build)
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, (result as AiRetryExecutor.RetryResult.Failure).message)
        assertEquals(listOf(paidA.id), sent)
    }

    @Test
    fun `after a credit refusal the free model gets one more pass, then the credit message`() = runBlocking {
        answers(paidA.id to AiResponse.Error.OutOfCredit(), free.id to AiResponse.Error.RateLimitError("busy", null))
        val result = AiRetryExecutor.executeSimple(listOf(paidA, paidB, free), ::build)
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, (result as AiRetryExecutor.RetryResult.Failure).message)
        assertEquals(listOf(paidA.id, free.id, free.id), sent)
    }

    @Test
    fun `a rejected key stops at once`() = runBlocking {
        answers(paidA.id to AiResponse.Error.KeyRejected(), free.id to ok(free.id))
        val result = AiRetryExecutor.executeSimple(listOf(paidA, free), ::build)
        assertTrue(result is AiRetryExecutor.RetryResult.Failure)
        assertEquals(listOf(paidA.id), sent)
    }

    @Test
    fun `accounts without credit get one pass`() = runBlocking {
        OpenRouterAccount.onPaidRequestRefused()
        answers()
        AiRetryExecutor.executeSimple(listOf(free, model("google/gemma-4-31b-it:free")), ::build)
        assertEquals(2, sent.size)
    }

    @Test
    fun `accounts with credit still get three passes`() = runBlocking {
        answers()
        AiRetryExecutor.executeSimple(listOf(paidA, free), ::build)
        assertEquals(6, sent.size)
    }

    @Test
    fun `single pass helper returns the first answer`() = runBlocking {
        answers(paidB.id to ok(paidB.id))
        val result = AiRetryExecutor.executeWithBackup(listOf(paidA, paidB, free), buildRequest = ::build)
        assertEquals("answer from ${paidB.id}", (result as AiResponse.Success).data)
        assertEquals(listOf(paidA.id, paidB.id), sent)
    }

    @Test
    fun `single pass helper prefers the credit refusal over later errors`() = runBlocking {
        answers(paidA.id to AiResponse.Error.OutOfCredit(), free.id to AiResponse.Error.RateLimitError("busy", null))
        val result = AiRetryExecutor.executeWithBackup(listOf(paidA, paidB, free), buildRequest = ::build)
        assertTrue(result is AiResponse.Error.OutOfCredit)
        assertEquals(listOf(paidA.id, free.id), sent)
    }

    @Test
    fun `single pass helper stops on a missing key`() = runBlocking {
        answers(paidA.id to AiResponse.Error.NoApiKey(), free.id to ok(free.id))
        val result = AiRetryExecutor.executeWithBackup(listOf(paidA, free), buildRequest = ::build)
        assertTrue(result is AiResponse.Error.NoApiKey)
        assertEquals(listOf(paidA.id), sent)
    }
}
