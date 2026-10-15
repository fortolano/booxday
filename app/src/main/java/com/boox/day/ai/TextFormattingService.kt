package com.boox.day.ai

import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.aiRequest

/**
 * TextFormattingService - Formats raw transcribed text to match Gemini quality output.
 *
 * Used when SpeechRecognizer provides raw text (no punctuation, capitalization, or structure).
 * This service enhances readability by:
 * - Adding proper punctuation and capitalization
 * - Structuring content with paragraphs and lists
 * - Applying markdown formatting (bold for important items)
 * - Improving grammar and flow
 *
 * Uses free text models with paid fallback for guaranteed availability.
 */
object TextFormattingService {
    private const val TAG = "TextFormattingService"

    /**
     * Result of text formatting.
     */
    data class FormattingResult(
        val plainText: String,      // Plain text without formatting
        val markdownText: String,   // Markdown-formatted text
        val success: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun empty(error: String) = FormattingResult(
                plainText = "",
                markdownText = "",
                success = false,
                errorMessage = error
            )

            /**
             * Passthrough for text that doesn't need formatting
             * (already formatted or too short to benefit).
             */
            fun passthrough(text: String) = FormattingResult(
                plainText = text,
                markdownText = text,
                success = true
            )
        }
    }

    /**
     * Format raw transcribed text to enhance readability.
     *
     * @param rawText Raw text from SpeechRecognizer (typically lowercase, no punctuation)
     * @return FormattingResult with enhanced plain and markdown versions
     */
    suspend fun formatText(rawText: String): FormattingResult {
        if (rawText.isBlank()) {
            return FormattingResult.empty("No text to format")
        }

        // Short text (< 20 chars) doesn't benefit much from formatting
        if (rawText.length < 20) {
            val capitalized = rawText.trim().replaceFirstChar { it.uppercaseChar() }
            return FormattingResult.passthrough(capitalized)
        }

        // Get text models from server config - uses 3×3 retry via AiRetryExecutor
        val modelsToTry = ModelConfigProvider.getTextFallbackChain()
        val systemPrompt = buildFormattingSystemPrompt()

        Log.d(TAG, "Formatting text: '${rawText.take(100)}...' (${rawText.length} chars), " +
            "using models: [${modelsToTry.map { it.id }.joinToString()}]")

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(rawText)
                    temperature(0.4f) // Slightly higher for natural writing
                }
            },
            processResponse = { response, model ->
                val formattingResult = parseFormattingResponse(response.data, rawText)
                if (formattingResult.success) {
                    Log.d(TAG, "Formatting success with ${model.displayName}")
                    formattingResult
                } else {
                    null  // Triggers retry
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> {
                // All models failed - return capitalized original as graceful fallback
                Log.e(TAG, "All models failed: ${result.lastError}")
                val capitalized = rawText.trim().replaceFirstChar { it.uppercaseChar() }
                FormattingResult(
                    plainText = capitalized,
                    markdownText = capitalized,
                    success = true, // Partial success - we have usable text
                    errorMessage = "Formatting unavailable, using original text"
                )
            }
        }
    }

    /**
     * System prompt for text formatting.
     * Based on VoiceTextParser's proven prompt but adapted for text-only input.
     */
    private fun buildFormattingSystemPrompt(): String {
        return """
You clean up spoken transcription into readable written text.

GOALS:
1. Add punctuation and capitalization.
2. Keep the speaker's meaning and wording as intact as possible.
3. Use paragraphs or lists only when the structure is clear.
4. Apply light markdown for clearly important items.

MARKDOWN RULES:
- Use **bold** for clearly important names, dates, times, deadlines, action items, and amounts.
- Use "- " for bullet lists when the speaker clearly listed items.
- Use "1. " for numbered lists when the speaker described sequential steps.
- Do NOT add headers (# ## ###) or titles.

EXAMPLES:

Input: "okay so i need to remember three things first pick up milk second call mom and third finish the report by friday"
Output:
1. Pick up milk
2. Call **Mom**
3. Finish the report by **Friday**

Input: "meeting notes we discussed the product launch john suggested march but sarah prefers february budget is fifty thousand dollars"
Output:
We discussed the product launch:
- **John** suggested **March**
- **Sarah** prefers **February**

Budget: **${'$'}50,000**

Input: "remember to buy eggs bread cheese and milk"
Output:
- Eggs
- Bread
- Cheese
- Milk

RULES:
1. Do not invent facts, names, dates, or intent.
2. Remove filler words (um, uh, like) only when clearly safe.
3. If the structure is unclear, prefer plain sentences over aggressive reformatting.
4. Return only the formatted text — no explanations or commentary.
""".trimIndent()
    }

    /**
     * Parse the AI response into FormattingResult.
     */
    private fun parseFormattingResponse(response: String, originalText: String): FormattingResult {
        val trimmedResponse = response.trim()

        if (trimmedResponse.isEmpty()) {
            return FormattingResult.empty("Empty response from AI")
        }

        // Check for error responses
        if (trimmedResponse.lowercase().contains("could not") ||
            trimmedResponse.lowercase().contains("unable to") ||
            trimmedResponse.lowercase().contains("cannot format")) {
            return FormattingResult.empty(trimmedResponse)
        }

        // Extract plain text by stripping Markdown
        val plainText = stripMarkdown(trimmedResponse)

        return FormattingResult(
            plainText = plainText,
            markdownText = trimmedResponse,
            success = true
        )
    }

    /**
     * Strip Markdown formatting to get plain text.
     */
    private fun stripMarkdown(markdown: String): String {
        return markdown
            // Remove headers
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            // Remove bold/italic markers
            .replace(Regex("\\*{1,2}([^*]+)\\*{1,2}"), "$1")
            .replace(Regex("_{1,2}([^_]+)_{1,2}"), "$1")
            // Remove bullet points but keep content
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
            // Remove numbered list markers but keep content
            .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // Remove inline code
            .replace(Regex("`([^`]+)`"), "$1")
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Remove links but keep text
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            // Clean up extra whitespace
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
