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

    private fun ids(job: AiJob, show: Show = Show.POPULAR, sort: Sort = Sort.MOST_POPULAR, query: String = "",
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
        assertTrue(haiku.supportsJson && haiku.supportsJsonSchema && haiku.zdr && haiku.reasoning.supported)
        assertTrue(haiku.supportsMaxTokens)
        assertFalse(haiku.supportsTemperature)
        assertEquals(listOf("max", "xhigh", "high", "medium", "low", "none"), haiku.reasoning.efforts)
        assertEquals(128_000, haiku.maxOutput)
        val gemma = models.first { it.id == "google/gemma-4-26b-a4b-it:free" }
        assertTrue(gemma.isFree && gemma.supportsJson && gemma.supportsTemperature)
        assertFalse(gemma.zdr)
        assertFalse(gemma.supportsJsonSchema) // JSON mode only
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
    fun `handwriting lists models that read images and return the exact JSON format`() {
        val handwriting = ids(AiJob.HANDWRITING)
        assertEquals(10, handwriting.size)
        assertFalse("nvidia/nemotron-3-super-120b-a12b:free" in handwriting) // text only
        assertFalse("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free" in handwriting) // no JSON
        // Oct 2026: JSON mode alone is not enough, the features ask for an exact format
        assertFalse("google/gemma-4-26b-a4b-it:free" in handwriting)
        assertFalse("google/gemma-4-31b-it:free" in handwriting)
        assertFalse("rekaai/reka-edge" in handwriting) // 16,384 tokens of context: no room for a page
        assertTrue("nvidia/nemotron-3-super-120b-a12b:free" in ids(AiJob.WRITING))
        assertEquals(11, ids(AiJob.WRITING).size)
    }

    @Test
    fun `search indexing also needs a provider that keeps no copy, and reasoning it can turn off`() {
        val indexing = ids(AiJob.SEARCH_INDEXING)
        assertEquals(
            listOf("deepseek/deepseek-v4.1-flash", "xiaomi/mimo-v2.6-flash", "anthropic/claude-haiku-5.5",
                "google/gemini-3-flash-preview", "google/gemini-3.1-flash-lite"),
            indexing
        )
        assertTrue(indexing.all { it in zdrIds })
        assertFalse("openai/gpt-5-nano" in indexing) // always reasons; indexing turns reasoning off
        assertEquals(emptyList<String>(), ids(AiJob.SEARCH_INDEXING, show = Show.FREE))
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
    fun `show tabs`() {
        assertEquals(emptyList<String>(), ids(AiJob.HANDWRITING, show = Show.FREE))
        assertEquals(listOf("nvidia/nemotron-3-super-120b-a12b:free"), ids(AiJob.WRITING, show = Show.FREE))
        assertEquals(10, ids(AiJob.HANDWRITING, show = Show.PAID).size)
        assertTrue(ids(AiJob.WRITING, show = Show.PAID).none { it.endsWith(":free") })
    }

    @Test
    fun `sorts`() {
        val cheapest = ids(AiJob.HANDWRITING, sort = Sort.PRICE_LOW_TO_HIGH)
        assertEquals(listOf("xiaomi/mimo-v2.6-flash", "openai/gpt-5-nano", "anthropic/claude-haiku-5.5"), cheapest.take(3))
        assertEquals("openai/o1-pro", cheapest.last())
        assertEquals("openai/o1-pro", ids(AiJob.HANDWRITING, sort = Sort.PRICE_HIGH_TO_LOW).first())
        assertEquals(
            listOf("meta/muse-spark-open", "anthropic/claude-haiku-5.5", "anthropic/claude-opus-5.5"),
            ids(AiJob.HANDWRITING, sort = Sort.NEWEST).take(3)
        )
    }

    @Test
    fun `a list holds the most used models, and a sort only reorders them`() {
        val top3 = OpenRouterModelCatalog.listFor(AiJob.HANDWRITING, models, Show.POPULAR, Sort.MOST_POPULAR, "", null, today, limit = 3)
        assertEquals(listOf("deepseek/deepseek-v4.1-flash", "z-ai/glm-5.3-flash", "xiaomi/mimo-v2.6-flash"), top3.rows.map { it.id })
        assertEquals(10, top3.matching)
        // Price high to low reorders those three; it doesn't bring in o1-pro, the dearest model
        assertEquals(
            listOf("z-ai/glm-5.3-flash", "deepseek/deepseek-v4.1-flash", "xiaomi/mimo-v2.6-flash"),
            ids(AiJob.HANDWRITING, sort = Sort.PRICE_HIGH_TO_LOW, limit = 3)
        )
        assertEquals(10, OpenRouterModelCatalog.LIST_LIMIT)
    }

    @Test
    fun `search and the account's allowed models`() {
        assertEquals(listOf("google/gemini-3-flash-preview", "google/gemini-3.1-flash-lite"), ids(AiJob.HANDWRITING, query = "gemini"))
        assertEquals(listOf("anthropic/claude-haiku-5.5"), ids(AiJob.HANDWRITING, query = "anthropic haiku"))
        assertEquals(
            listOf("openai/gpt-5-nano"),
            ids(AiJob.HANDWRITING, allowed = setOf("openai/gpt-5-nano", "google/gemma-4-26b-a4b-it:free"))
        )
    }

    @Test
    fun `the request builder finds a model in a recent list only`() {
        try {
            OpenRouterModelCatalog.setSnapshotForTest(CatalogSnapshot(System.currentTimeMillis(), models, version = OpenRouterModelCatalog.SNAPSHOT_VERSION))
            assertEquals(false, OpenRouterModelCatalog.model("openai/gpt-5-nano")?.supportsTemperature)
            assertEquals(null, OpenRouterModelCatalog.model("x/unknown"))
            OpenRouterModelCatalog.setSnapshotForTest(CatalogSnapshot(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000, models))
            assertEquals(null, OpenRouterModelCatalog.model("openai/gpt-5-nano"))
        } finally {
            OpenRouterModelCatalog.setSnapshotForTest(null)
        }
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
