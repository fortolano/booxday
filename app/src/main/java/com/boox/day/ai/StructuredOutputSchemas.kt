package com.boox.day.ai

/**
 * Shared JSON schema definitions for live structured AI features.
 *
 * These schemas are intentionally small and provider-neutral:
 * - no provider-specific fields
 * - every object lists all its fields as required, and "none" is an empty text or list (Oct 2026:
 *   OpenAI's strict mode refuses a schema with an optional field, so GPT-5 Nano answered no Convert
 *   Memo to Tasks request, and an OpenAI pick no Smart Suggestions or Weekly Summary). Null types
 *   are left out: not every provider reads them. StructuredOutputSchemasTest checks the rule.
 * - additionalProperties is disabled to reduce drift across models
 */
internal object StructuredOutputSchemas {
    private const val DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$"
    private const val TIME_PATTERN = "^\\d{2}:\\d{2}$"
    const val MEMO_TO_TASKS_MAX_ITEMS = 30

    private val suggestionCategories = listOf(
        "WORK", "PERSONAL", "HEALTH", "ERRANDS", "SOCIAL", "LEARNING", "FINANCE", "OTHER"
    )

    private val correctionTypes = listOf(
        "SPELLING", "CAPITALIZATION", "GRAMMAR", "TIME_FORMAT",
        "DATE_FORMAT", "NAME", "ABBREVIATION", "SEMANTIC"
    )

    private fun objectSchema(
        properties: Map<String, Any>,
        required: List<String> = emptyList(),
        additionalProperties: Boolean = false
    ): Map<String, Any> {
        val schema = mutableMapOf<String, Any>(
            "type" to "object",
            "properties" to properties,
            "additionalProperties" to additionalProperties
        )
        if (required.isNotEmpty()) {
            schema["required"] = required
        }
        return schema
    }

    private fun arraySchema(
        items: Map<String, Any>,
        minItems: Int? = null,
        maxItems: Int? = null
    ): Map<String, Any> {
        val schema = mutableMapOf<String, Any>(
            "type" to "array",
            "items" to items
        )
        if (minItems != null) {
            schema["minItems"] = minItems
        }
        if (maxItems != null) {
            schema["maxItems"] = maxItems
        }
        return schema
    }

    private fun stringSchema(
        enum: List<String>? = null,
        minLength: Int? = null,
        maxLength: Int? = null,
        pattern: String? = null
    ): Map<String, Any> {
        val schema = mutableMapOf<String, Any>("type" to "string")
        if (enum != null) {
            schema["enum"] = enum
        }
        if (minLength != null) {
            schema["minLength"] = minLength
        }
        if (maxLength != null) {
            schema["maxLength"] = maxLength
        }
        if (pattern != null) {
            schema["pattern"] = pattern
        }
        return schema
    }

    private fun integerSchema(
        minimum: Int? = null,
        maximum: Int? = null
    ): Map<String, Any> {
        val schema = mutableMapOf<String, Any>("type" to "integer")
        if (minimum != null) {
            schema["minimum"] = minimum
        }
        if (maximum != null) {
            schema["maximum"] = maximum
        }
        return schema
    }

    private fun numberSchema(
        minimum: Double? = null,
        maximum: Double? = null
    ): Map<String, Any> {
        val schema = mutableMapOf<String, Any>("type" to "number")
        if (minimum != null) {
            schema["minimum"] = minimum
        }
        if (maximum != null) {
            schema["maximum"] = maximum
        }
        return schema
    }

    private fun booleanSchema(): Map<String, Any> = mapOf("type" to "boolean")

    private fun stringArraySchema(
        minItems: Int,
        maxItems: Int,
        minLength: Int = 1,
        maxLength: Int = 80
    ): Map<String, Any> = arraySchema(
        items = stringSchema(minLength = minLength, maxLength = maxLength),
        minItems = minItems,
        maxItems = maxItems
    )

    val suggestionsSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "suggestions" to arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "text" to stringSchema(minLength = 3, maxLength = 120),
                        "reason" to stringSchema(minLength = 1, maxLength = 220),
                        "confidence" to numberSchema(minimum = 0.0, maximum = 1.0),
                        "category" to stringSchema(enum = suggestionCategories),
                        "isFollowUp" to booleanSchema()
                    ),
                    required = listOf("text", "reason", "confidence", "category", "isFollowUp")
                ),
                minItems = 1,
                // Oct 2026: 10, so "Comprehensive (8-10)" can be delivered (it asks for 9; the cap was 7)
                maxItems = 10
            )
            // "patternInsights" was an optional object here; no screen showed it (Oct 2026)
        ),
        required = listOf("suggestions")
    )

    val breakdownSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "subtasks" to arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "text" to stringSchema(minLength = 1, maxLength = 80),
                        "order" to integerSchema(minimum = 1, maximum = 20)
                    ),
                    required = listOf("text", "order")
                ),
                minItems = 3,
                maxItems = 7
            ),
            "keepOriginal" to booleanSchema()
        ),
        required = listOf("subtasks", "keepOriginal")
    )

    val dailyInsightsSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "insights" to arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "type" to stringSchema(
                            enum = listOf("productivity", "pattern", "suggestion", "schedule", "tip"),
                            minLength = 3,
                            maxLength = 32
                        ),
                        "text" to stringSchema(minLength = 1, maxLength = 320)
                    ),
                    required = listOf("type", "text")
                ),
                minItems = 1,
                maxItems = 3
            )
        ),
        required = listOf("insights")
    )

    val weeklyInsightsSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "insights" to arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "type" to stringSchema(
                            enum = listOf("overview", "streak", "pattern", "suggestion", "achievement", "tip"),
                            minLength = 3,
                            maxLength = 32
                        ),
                        "title" to stringSchema(minLength = 1, maxLength = 60),
                        "text" to stringSchema(minLength = 1, maxLength = 420)
                    ),
                    required = listOf("type", "title", "text")
                ),
                minItems = 1,
                maxItems = 4
            )
            // "categories" / "categoryBreakdown" were optional lists here; the weekly summary counts the
            // categories itself (computeLocalWeeklyCategoryBreakdown) and never used them (Oct 2026)
        ),
        required = listOf("insights")
    )

    val memoToTasksSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "tasks" to arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "text" to stringSchema(minLength = 1, maxLength = 140),
                        "suggestedPriority" to stringSchema(enum = listOf("", "A", "B", "C")),
                        "reason" to stringSchema(minLength = 1, maxLength = 200),
                        "confidence" to numberSchema(minimum = 0.0, maximum = 1.0),
                        // "" = none (the sanitizers in MemoToTasksService drop blank values)
                        "suggestedTime" to stringSchema(minLength = 0, maxLength = 60),
                        "relatedEventId" to stringSchema(minLength = 0, maxLength = 120)
                    ),
                    required = listOf("text", "suggestedPriority", "reason", "confidence", "suggestedTime", "relatedEventId")
                ),
                minItems = 0,
                maxItems = MEMO_TO_TASKS_MAX_ITEMS
            )
        ),
        required = listOf("tasks")
    )

    fun inferenceSchema(trackCorrections: Boolean): Map<String, Any> {
        val properties = mutableMapOf<String, Any>(
            "refinedText" to stringSchema(minLength = 1, maxLength = 6000),
            "confidence" to numberSchema(minimum = 0.0, maximum = 1.0)
        )
        val required = mutableListOf("refinedText", "confidence")

        if (trackCorrections) {
            properties["corrections"] = arraySchema(
                items = objectSchema(
                    properties = mapOf(
                        "original" to stringSchema(minLength = 1, maxLength = 200),
                        "corrected" to stringSchema(minLength = 1, maxLength = 200),
                        "type" to stringSchema(enum = correctionTypes)
                    ),
                    required = listOf("original", "corrected", "type")
                ),
                minItems = 0,
                maxItems = 30
            )
            required += "corrections"
        }

        return objectSchema(
            properties = properties,
            required = required
        )
    }

    val ocrTextSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "hasText" to booleanSchema(),
            "text" to stringSchema(minLength = 0, maxLength = 12000)
        ),
        required = listOf("hasText", "text")
    )

    val ocrTextWithLanguageSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "hasText" to booleanSchema(),
            "language" to stringSchema(minLength = 0, maxLength = 80),
            "text" to stringSchema(minLength = 0, maxLength = 12000)
        ),
        required = listOf("hasText", "language", "text")
    )

    val translationSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "translatedText" to stringSchema(minLength = 1, maxLength = 12000),
            "detectedLanguage" to stringSchema(minLength = 1, maxLength = 80)
        ),
        required = listOf("translatedText", "detectedLanguage")
    )

    fun agenticPlanSchema(allowedActions: Set<String>): Map<String, Any> {
        val sortedActions = allowedActions.toList().sorted()

        val alternativeActionSchema = objectSchema(
            properties = mapOf(
                "label" to stringSchema(minLength = 1, maxLength = 50),
                "action" to stringSchema(enum = sortedActions),
                "targetDate" to stringSchema(pattern = DATE_PATTERN, minLength = 10, maxLength = 10)
            ),
            required = listOf("label", "action")
        )

        val timeBlockSchema = objectSchema(
            properties = mapOf(
                "startTime" to stringSchema(pattern = TIME_PATTERN, minLength = 5, maxLength = 5),
                "endTime" to stringSchema(pattern = TIME_PATTERN, minLength = 5, maxLength = 5),
                "title" to stringSchema(minLength = 1, maxLength = 80),
                "durationMinutes" to integerSchema(minimum = 1, maximum = 720),
                "isDeepWork" to booleanSchema()
            ),
            required = listOf("startTime", "endTime", "title", "durationMinutes")
        )

        val zombieInfoSchema = objectSchema(
            properties = mapOf(
                "migrationCount" to integerSchema(minimum = 0, maximum = 365),
                "originalDate" to stringSchema(pattern = DATE_PATTERN, minLength = 10, maxLength = 10),
                "daysSinceCreation" to integerSchema(minimum = 0, maximum = 3650),
                "suggestion" to stringSchema(
                    enum = listOf("BREAK_DOWN", "SCHEDULE_TODAY", "COMMIT_DATE", "DELEGATE", "DROP")
                ),
                "suggestionReason" to stringSchema(minLength = 1, maxLength = 220)
            ),
            required = listOf("migrationCount", "daysSinceCreation", "suggestion", "suggestionReason")
        )

        val proposalSchema = objectSchema(
            properties = mapOf(
                "action" to stringSchema(enum = sortedActions),
                "title" to stringSchema(minLength = 1, maxLength = 70),
                "description" to stringSchema(minLength = 1, maxLength = 220),
                "reason" to stringSchema(minLength = 1, maxLength = 240),
                "confidence" to numberSchema(minimum = 0.0, maximum = 1.0),
                "priority" to integerSchema(minimum = 1, maximum = 7),
                "estimatedMinutes" to integerSchema(minimum = 1, maximum = 720),
                "taskId" to stringSchema(minLength = 1, maxLength = 120),
                "taskText" to stringSchema(minLength = 1, maxLength = 140),
                "targetDate" to stringSchema(pattern = DATE_PATTERN, minLength = 10, maxLength = 10),
                "suggestedTimeBlock" to timeBlockSchema,
                "suggestedSubtasks" to stringArraySchema(minItems = 1, maxItems = 7),
                "zombieInfo" to zombieInfoSchema,
                "alternativeActions" to arraySchema(
                    items = alternativeActionSchema,
                    minItems = 1,
                    maxItems = 5
                )
            ),
            required = listOf("action", "title", "description", "reason", "confidence", "priority")
        )

        return objectSchema(
            properties = mapOf(
                "summary" to stringSchema(minLength = 1, maxLength = 180),
                "capacityStatus" to stringSchema(
                    enum = listOf("Light", "Moderate", "Busy", "Overbooked", "Severely Overbooked")
                ),
                "proposals" to arraySchema(
                    items = proposalSchema,
                    minItems = 1,
                    maxItems = 7
                )
            ),
            required = listOf("summary", "capacityStatus", "proposals")
        )
    }
}
