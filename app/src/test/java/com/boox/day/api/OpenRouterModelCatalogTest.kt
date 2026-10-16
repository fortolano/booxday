package com.boox.day.api

import com.boox.day.api.OpenRouterModelCatalog.Show
import com.boox.day.api.OpenRouterModelCatalog.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The model list's checks, filters and sorts, on a trimmed copy of OpenRouter's real answer
 * (22 models, popularity order, 15 Oct 2026: test resources openrouter/).
 */
class OpenRouterModelCatalogTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun resource(name: String): String =
        javaClass.getResource("/openrouter/$name")!!.readText()

    private val zdrIds = OpenRouterModelCatalog.parseIds(resource("zdr_sample.json"), "model_id")
    private val models = OpenRouterModelCatalog.parseModels(resource("models_sample.json"), zdrIds)

    private fun ids(job: AiJob, show: Show = Show.ALL, sort: Sort = Sort.MOST_POPULAR, query: String = "",
                    allowed: Set<String>? = null, limit: Int = 200) =
        OpenRouterModelCatalog.listFor(job, models, show, sort, query, allowed, today, limit).rows.map { it.id }

    @Test
    fun `reads OpenRouter's answer in its popularity order`() {
        assertEquals(22, models.size)
        assertEquals("deepseek/deepseek-v4.1-flash", models[0].id)
        assertEquals((0 until 22).toList(), models.map { it.rank })
        val haiku = models.first { it.id == "anthropic/claude-haiku-5.5" }
        assertEquals("Anthropic", haiku.lab)
        assertEquals("Claude Haiku 5.5", haiku.name)
        assertTrue(haiku.supportsJson && haiku.zdr && haiku.reasoning.supported)
        assertEquals(listOf("max", "xhigh", "high", "medium", "low", "none"), haiku.reasoning.efforts)
        assertEquals(128_000, haiku.maxOutput)
        assertTrue(models.first { it.id == "google/gemma-4-26b-a4b-it:free" }.isFree)
        assertFalse(models.first { it.id == "google/gemma-4-26b-a4b-it:free" }.zdr)
    }

    @Test
    fun `voice only lists models that take audio`() {
        assertEquals(
            listOf("xiaomi/mimo-v2.6-flash", "google/gemini-3-flash-preview", "google/gemini-3.1-flash-lite",
                "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "meta/muse-spark-open"),
            ids(AiJob.VOICE)
        )
    }

    @Test
    fun `handwriting lists models that read images and return JSON`() {
        val handwriting = ids(AiJob.HANDWRITING)
        assertEquals(13, handwriting.size)
        assertFalse("nvidia/nemotron-3-super-120b-a12b:free" in handwriting) // text only
        assertFalse("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free" in handwriting) // no JSON
        assertTrue("nvidia/nemotron-3-super-120b-a12b:free" in ids(AiJob.WRITING))
    }

    @Test
    fun `search indexing also needs a provider that keeps no copy`() {
        val indexing = ids(AiJob.SEARCH_INDEXING)
        assertEquals(10, indexing.size)
        assertTrue(indexing.all { it in zdrIds })
        assertEquals(emptyList<String>(), ids(AiJob.SEARCH_INDEXING, show = Show.TOP_FREE))
    }

    @Test
    fun `batch copies, aliases, routers, generators and retiring models are never listed`() {
        val everyJob = AiJob.entries.flatMap { ids(it) }.toSet()
        for (hidden in listOf(
            "anthropic/claude-haiku-5.5:batch", "~anthropic/claude-haiku-latest", "openrouter/auto",
            "typesafe/jev-router", "google/lyria-3-clip-preview",
            "google/gemini-2.5-flash-lite", "xiaomi/mimo-v2.5" // retire on 20 and 21 Oct
        )) {
            assertFalse(hidden, hidden in everyJob)
        }
    }

    @Test
    fun `show filters`() {
        assertEquals(listOf("google/gemma-4-26b-a4b-it:free", "google/gemma-4-31b-it:free"), ids(AiJob.HANDWRITING, show = Show.TOP_FREE))
        assertTrue(ids(AiJob.HANDWRITING, show = Show.TOP_PAID).none { it.endsWith(":free") })
        val reasoning = ids(AiJob.HANDWRITING, show = Show.TOP_REASONING)
        assertFalse("rekaai/reka-edge" in reasoning)
        assertEquals(12, reasoning.size)
    }

    @Test
    fun `sorts`() {
        val cheapest = ids(AiJob.HANDWRITING, sort = Sort.PRICE_LOW_TO_HIGH)
        assertEquals(listOf("google/gemma-4-26b-a4b-it:free", "google/gemma-4-31b-it:free", "rekaai/reka-edge"), cheapest.take(3))
        assertEquals("openai/o1-pro", cheapest.last())
        assertEquals("openai/o1-pro", ids(AiJob.HANDWRITING, sort = Sort.PRICE_HIGH_TO_LOW).first())
        assertEquals(
            listOf("meta/muse-spark-open", "anthropic/claude-haiku-5.5", "anthropic/claude-opus-5.5"),
            ids(AiJob.HANDWRITING, sort = Sort.NEWEST).take(3)
        )
    }

    @Test
    fun `search, the row limit and the account's allowed models`() {
        assertEquals(listOf("google/gemini-3-flash-preview", "google/gemini-3.1-flash-lite"), ids(AiJob.HANDWRITING, query = "gemini"))
        assertEquals(listOf("anthropic/claude-haiku-5.5"), ids(AiJob.HANDWRITING, query = "anthropic haiku"))
        val limited = OpenRouterModelCatalog.listFor(AiJob.HANDWRITING, models, Show.ALL, Sort.MOST_POPULAR, "", null, today, limit = 3)
        assertEquals(3, limited.rows.size)
        assertEquals(13, limited.matching)
        assertEquals(
            listOf("openai/gpt-5-nano", "google/gemma-4-26b-a4b-it:free"),
            ids(AiJob.HANDWRITING, allowed = setOf("openai/gpt-5-nano", "google/gemma-4-26b-a4b-it:free"))
        )
    }

    @Test
    fun `prices per million tokens`() {
        assertEquals("$0.25", OpenRouterModelCatalog.perMillion(0.00000025))
        assertEquals("$0.016", OpenRouterModelCatalog.perMillion(0.0000000156))
        assertEquals("$0.05", OpenRouterModelCatalog.perMillion(0.00000005))
        assertEquals("$4.00", OpenRouterModelCatalog.perMillion(0.000004))
        assertEquals("$0", OpenRouterModelCatalog.perMillion(0.0))
        assertEquals("<$0.001", OpenRouterModelCatalog.perMillion(0.0000000001))
        assertEquals(
            "Anthropic · $0.10 in · $0.50 out per 1M tokens",
            OpenRouterModelCatalog.priceLine(models.first { it.id == "anthropic/claude-haiku-5.5" })
        )
        assertEquals("Google · Free", OpenRouterModelCatalog.priceLine(models.first { it.id == "google/gemma-4-31b-it:free" }))
    }

    @Test
    fun `names without a lab use the id`() {
        assertEquals("Moondream" to "Moondream 3", OpenRouterModelCatalog.splitName("Moondream 3", "moondream/moondream3"))
    }
}
