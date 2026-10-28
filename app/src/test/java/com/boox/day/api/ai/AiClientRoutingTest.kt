package com.boox.day.api.ai

import com.boox.day.api.CatalogModel
import com.boox.day.api.CatalogSnapshot
import com.boox.day.api.OpenRouterModelCatalog
import com.boox.day.api.ProviderConfig
import com.boox.day.api.ReasoningConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Provider routing (Oct 2026): a provider that can't return the requested format ignored it. With a
 * format the model can return, only providers that take every setting are used, and the settings
 * the model doesn't take are left out: OpenRouter answered HTTP 404 "No endpoints found" for GPT-5
 * Nano with a temperature (live check, 27 Oct 2026).
 */
class AiClientRoutingTest {

    @After
    fun tearDown() {
        OpenRouterModelCatalog.setSnapshotForTest(null)
    }

    private fun listed(vararg models: CatalogModel) =
        OpenRouterModelCatalog.setSnapshotForTest(CatalogSnapshot(System.currentTimeMillis(), models.toList()))

    private fun catalog(id: String, schema: Boolean = true, temperature: Boolean = true, reasoning: Boolean = true) = CatalogModel(
        id = id, name = id, lab = "", rank = 0, supportsJson = true, supportsJsonSchema = schema,
        supportsTemperature = temperature, supportsMaxTokens = true,
        reasoning = com.boox.day.api.ReasoningOptions(supported = reasoning)
    )

    private fun model(id: String) = AiModel.Custom(
        id = id, displayName = id, capabilities = setOf(AiCapability.TEXT, AiCapability.JSON_OUTPUT, AiCapability.REASONING),
        defaultConfig = AiConfig(temperature = 0.3f, maxTokens = 4096)
    )

    private fun withSchema(id: String) = aiRequest {
        model(model(id))
        userMessage("Hello")
        jsonSchema("ocr_text", mapOf("type" to "object"))
        enableReasoning()
    }

    @Test
    fun `a format the model can return uses only providers that take every setting`() {
        listed(catalog("google/gemini-3.1-flash-lite"))
        val request = AiClient.buildApiRequest(withSchema("google/gemini-3.1-flash-lite"))
        assertEquals(ProviderConfig(requireParameters = true), request.provider)
        assertEquals(0.3f, request.temperature)
        assertEquals(4096, request.maxTokens)
        assertEquals(ReasoningConfig.enabled(), request.reasoning)
    }

    @Test
    fun `settings the model doesn't take are left out`() {
        listed(catalog("openai/gpt-5-nano", temperature = false), catalog("x/no-reasoning", reasoning = false))
        val nano = AiClient.buildApiRequest(withSchema("openai/gpt-5-nano"))
        assertNull(nano.temperature)
        assertEquals(true, nano.provider?.requireParameters)
        assertNull(AiClient.buildApiRequest(withSchema("x/no-reasoning")).reasoning)
    }

    @Test
    fun `no strict routing without a format, for JSON mode only models, or without a recent list`() {
        listed(catalog("x/json-mode-only", schema = false))
        val plain = AiClient.buildApiRequest(aiRequest { model(model("x/json-mode-only")); userMessage("Hello") })
        assertNull(plain.provider)
        assertEquals(0.3f, plain.temperature)
        assertNull(AiClient.buildApiRequest(withSchema("x/json-mode-only")).provider)
        OpenRouterModelCatalog.setSnapshotForTest(null)
        assertNull(AiClient.buildApiRequest(withSchema("google/gemini-3.1-flash-lite")).provider)
    }

    @Test
    fun `search indexing keeps its private providers, with strict routing`() {
        listed(catalog("google/gemini-3.1-flash-lite"))
        val request = AiClient.buildApiRequest(withSchema("google/gemini-3.1-flash-lite").copy(zeroDataRetention = true))
        assertEquals(ProviderConfig(zdr = true, requireParameters = true), request.provider)
    }
}
