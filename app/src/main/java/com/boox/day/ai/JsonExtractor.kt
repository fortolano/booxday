package com.boox.day.ai

/**
 * Utility for extracting JSON from AI responses.
 * Handles markdown code blocks, conversational prefixes/suffixes, and whitespace.
 */
object JsonExtractor {

    /**
     * Extract the first valid JSON object or array from the given text.
     *
     * Handles:
     * - Markdown code blocks (```json ... ```)
     * - Conversational intro text ("Here is the JSON: { ... }")
     * - Trailing commentary
     * - Whitespace
     *
     * @param response The raw string response from an LLM.
     * @return The cleaned JSON string, or the original string if no JSON structure is found.
     */
    fun extract(response: String): String {
        var json = response.trim()

        // 1. Remove markdown code blocks if present
        if (json.startsWith("```")) {
            val firstNewline = json.indexOf('\n')
            if (firstNewline != -1) {
                json = json.substring(firstNewline + 1)
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length - 3)
            }
        }

        // 2. Find the first '{' and last '}' to isolate the JSON object
        // OR find first '[' and last ']' for arrays
        val firstBrace = json.indexOf('{')
        val firstBracket = json.indexOf('[')

        val startIndex: Int
        val endIndex: Int

        // Determine if we are looking for an object or an array
        // Use the one that appears first
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            startIndex = firstBrace
            endIndex = json.lastIndexOf('}')
        } else if (firstBracket != -1) {
            startIndex = firstBracket
            endIndex = json.lastIndexOf(']')
        } else {
            return json.trim() // No JSON structure found
        }

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            json = json.substring(startIndex, endIndex + 1)
        }

        return json.trim()
    }
}
