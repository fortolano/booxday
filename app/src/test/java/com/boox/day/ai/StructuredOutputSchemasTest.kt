package com.boox.day.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OpenAI's strict structured outputs (Oct 2026): every object must list all its fields as required
 * and allow no others. GPT-5 Nano refused memo_to_tasks with HTTP 400 on every Convert Memo to Tasks,
 * and an OpenAI pick for Planning refused Smart Suggestions and Weekly Summary.
 */
class StructuredOutputSchemasTest {

    /** Every answer format a feature sends (the unused agenticPlanSchema is not one). */
    private val inUse = mapOf(
        "smart_suggestions_v3" to StructuredOutputSchemas.suggestionsSchema,
        "task_breakdown_v3" to StructuredOutputSchemas.breakdownSchema,
        "daily_summary_insights_v3" to StructuredOutputSchemas.dailyInsightsSchema,
        "weekly_summary_insights_v3" to StructuredOutputSchemas.weeklyInsightsSchema,
        "memo_to_tasks" to StructuredOutputSchemas.memoToTasksSchema,
        "text_inference" to StructuredOutputSchemas.inferenceSchema(trackCorrections = false),
        "text_inference_with_corrections" to StructuredOutputSchemas.inferenceSchema(trackCorrections = true),
        "ocr_text" to StructuredOutputSchemas.ocrTextSchema,
        "ocr_text_with_language" to StructuredOutputSchemas.ocrTextWithLanguageSchema,
        "translation" to StructuredOutputSchemas.translationSchema
    )

    @Test
    fun `every object in every answer format requires all its fields and allows no others`() {
        inUse.forEach { (name, schema) -> checkStrict(name, schema, "") }
    }

    private fun checkStrict(name: String, node: Any?, path: String) {
        val schema = node as? Map<*, *> ?: return
        if (schema["type"] == "object") {
            val properties = schema["properties"] as Map<*, *>
            assertEquals("$name$path: additionalProperties", false, schema["additionalProperties"])
            assertEquals("$name$path: required", properties.keys, (schema["required"] as? List<*>)?.toSet().orEmpty())
            properties.forEach { (field, child) -> checkStrict(name, child, "$path.$field") }
        }
        schema["items"]?.let { checkStrict(name, it, "$path[]") }
    }
}
