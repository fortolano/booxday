package com.boox.day.api.ai

import com.boox.day.api.ApiError
import com.boox.day.api.ChatResponse
import com.boox.day.api.Choice
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ReasoningConfig
import com.boox.day.api.ResponseMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AiClient's handling of OpenRouter answers and of a picked model's settings (Oct 2026). */
class AiClientResponseTest {

    @After
    fun tearDown() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    private fun answer(content: String?, reasoning: String?, finishReason: String?) = ChatResponse(
        id = "gen-1",
        model = "test/model",
        choices = listOf(Choice(ResponseMessage("assistant", content, reasoning), finishReason)),
        usage = null,
        error = null
    )

    @Test
    fun `an answer cut off at the output limit is an error, not the reasoning text`() {
        val result = AiClient.handleSuccessResponse(answer("", "Let me think about this page...", "length"), "test/model")
        assertTrue(result is AiResponse.Error.NoContent)
    }

    @Test
    fun `a finished answer with only reasoning still uses it (older models)`() {
        val result = AiClient.handleSuccessResponse(answer(null, "The text says hello", "stop"), "test/model")
        assertEquals("The text says hello", (result as AiResponse.Success).data)
    }

    @Test
    fun `a normal answer is used`() {
        val result = AiClient.handleSuccessResponse(answer("Hello", "thinking", "stop"), "test/model")
        assertEquals("Hello", (result as AiResponse.Success).data)
    }

    @Test
    fun `402 reasons from an HTTP error body`() {
        fun body(limitSource: String, reason: String? = null) =
            """{"error":{"code":402,"message":"Payment required","metadata":{"limit_source":"$limitSource"${reason?.let { ",\"reason\":\"$it\"" } ?: ""}}}}"""

        val noCredit = AiClient.handleErrorResponse(402, body("openrouter_credits")) as AiResponse.Error.OutOfCredit
        assertTrue(noCredit.isAccountOutOfCredit)
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, noCredit.message)

        val keyLimit = AiClient.handleErrorResponse(402, body("openrouter_key_limit")) as AiResponse.Error.OutOfCredit
        assertTrue(keyLimit.isAccountOutOfCredit)
        assertTrue(keyLimit.message.contains("spending limit"))

        val busy = AiClient.handleErrorResponse(402, body("openrouter_in_flight_budget")) as AiResponse.Error.OutOfCredit
        assertFalse(busy.isAccountOutOfCredit)
        assertTrue(busy.message.contains("Try again in a moment"))

        val tooBig = AiClient.handleErrorResponse(402, body("openrouter_credits", "weight_exceeds_budget")) as AiResponse.Error.OutOfCredit
        assertFalse(tooBig.isAccountOutOfCredit)
        assertTrue(tooBig.message.contains("Max output tokens"))

        val plain = AiClient.handleErrorResponse(402, "not json") as AiResponse.Error.OutOfCredit
        assertTrue(plain.isAccountOutOfCredit)
    }

    @Test
    fun `402 reasons from an error inside a 200 answer`() {
        val body = ChatResponse(
            id = null, model = null, choices = null, usage = null,
            error = ApiError("Payment required", 402, mapOf("limit_source" to "openrouter_key_limit"))
        )
        val result = AiClient.handleSuccessResponse(body, "test/model") as AiResponse.Error.OutOfCredit
        assertEquals("openrouter_key_limit", result.limitSource)
    }

    @Test
    fun `a paid refusal for credit switches to free models, a paid answer switches back`() {
        AiClient.noteCreditState("google/gemini-3.1-flash-lite", AiResponse.Error.OutOfCredit())
        assertTrue(OpenRouterAccount.isFreeOnly())
        AiClient.noteCreditState("google/gemini-3.1-flash-lite", AiResponse.Success("ok", "m", null))
        assertFalse(OpenRouterAccount.isFreeOnly())
        // Busy or too-big requests say nothing about the balance
        AiClient.noteCreditState("google/gemini-3.1-flash-lite", AiResponse.Error.OutOfCredit.from("openrouter_in_flight_budget", null))
        assertFalse(OpenRouterAccount.isFreeOnly())
        // Free models are never refused for credit and don't prove there is credit
        AiClient.noteCreditState("google/gemma-4-31b-it:free", AiResponse.Error.OutOfCredit())
        assertFalse(OpenRouterAccount.isFreeOnly())
    }

    private fun model(userChoice: Boolean, reasoning: ReasoningConfig? = null, maxTokens: Int = 4096) = AiModel.Custom(
        id = "test/model",
        displayName = "Test",
        capabilities = setOf(AiCapability.TEXT, AiCapability.JSON_OUTPUT, AiCapability.REASONING),
        defaultConfig = AiConfig(temperature = 0.3f, maxTokens = maxTokens),
        userChoice = userChoice,
        reasoning = reasoning
    )

    @Test
    fun `a picked model sends the user's reasoning level, whatever the feature asks`() {
        val request = aiRequest {
            model(model(userChoice = true, reasoning = ReasoningConfig.withEffort("low")))
            userMessage("Hi")
            enableReasoning()
        }
        assertEquals(ReasoningConfig.withEffort("low"), AiClient.buildApiRequest(request).reasoning)

        // No level saved: nothing is sent, the model uses its own default
        val noLevel = aiRequest {
            model(model(userChoice = true, reasoning = null))
            userMessage("Hi")
            enableReasoning()
        }
        assertNull(AiClient.buildApiRequest(noLevel).reasoning)
    }

    @Test
    fun `Boox Day's models keep today's reasoning, and a feature's override still wins`() {
        val asked = aiRequest {
            model(model(userChoice = false))
            userMessage("Hi")
            enableReasoning()
        }
        assertEquals(ReasoningConfig.enabled(), AiClient.buildApiRequest(asked).reasoning)

        val overridden = aiRequest {
            model(model(userChoice = true, reasoning = ReasoningConfig.withEffort("high")))
            userMessage("Hi")
        }.copy(overrideReasoning = ReasoningConfig.disabled())
        assertEquals(ReasoningConfig.disabled(), AiClient.buildApiRequest(overridden).reasoning)
    }

    @Test
    fun `a picked model's output limit applies unless the feature asks for more`() {
        val picked = model(userChoice = true, maxTokens = 5_000)
        assertEquals(5_000, aiRequest { model(picked); userMessage("Hi") }.config.maxTokens)
        assertEquals(5_000, aiRequest { model(picked); userMessage("Hi"); temperature(0.2f) }.config.maxTokens)
        // Agentic planning asks for 8,192: a lower setting can't cut it
        assertEquals(8_192, aiRequest { model(picked); userMessage("Hi"); maxTokens(8_192) }.config.maxTokens)
        // A higher setting is kept
        assertEquals(20_000, aiRequest { model(model(true, maxTokens = 20_000)); userMessage("Hi"); maxTokens(8_192) }.config.maxTokens)
        // Boox Day's models: the feature's value as before
        assertEquals(2_000, aiRequest { model(model(false)); userMessage("Hi"); maxTokens(2_000) }.config.maxTokens)
    }
}
