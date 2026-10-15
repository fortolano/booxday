package com.boox.day.ai

import android.util.Log
import com.boox.day.api.Modality
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import kotlinx.coroutines.delay

/**
 * AI-powered parser for converting voice input to formatted text.
 *
 * ## Two APIs:
 *
 * 1. **parseAudioToText()** - LEGACY: Direct audio-to-text conversion using Gemini 2.0 Flash Lite
 *    - Handles transcription AND formatting in one call
 *    - Only works with Gemini (paid, audio-capable)
 *
 * 2. **formatTranscribedText()** - NEW: Format pre-transcribed text
 *    - For use with TranscriptionService (SpeechRecognizer + Gemini fallback)
 *    - Uses TextFormattingService for consistent formatting
 *    - Works with any transcription source
 *
 * The Markdown output is then converted to RichTextSpans for canvas rendering.
 */
object VoiceTextParser {
    private const val TAG = "VoiceTextParser"

    // Retry configuration
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1000L

    /**
     * Result of voice-to-text parsing.
     */
    data class ParsedText(
        val plainText: String,      // Plain text without formatting (for content field)
        val markdownText: String,   // Markdown-formatted text
        val originalTranscript: String, // Raw transcription before enhancement
        val success: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun empty(error: String) = ParsedText(
                plainText = "",
                markdownText = "",
                originalTranscript = "",
                success = false,
                errorMessage = error
            )
        }
    }

    /**
     * Parse audio directly into formatted text using Gemini 2.0 Flash Lite.
     * Includes automatic retry logic for transient failures.
     *
     * @param audioBase64 Base64-encoded WAV audio data
     * @param mimeType Audio MIME type (default: "audio/wav")
     * @return ParsedText with formatted Markdown and plain text versions
     */
    suspend fun parseAudioToText(
        audioBase64: String,
        mimeType: String = "audio/wav"
    ): ParsedText {
        if (audioBase64.isBlank()) {
            return ParsedText.empty("No audio recorded")
        }

        // Get audio model from server config (or hardcoded default)
        val audioModel = ModelConfigProvider.getPrimaryAudioModel()
        Log.d(TAG, "Parsing audio to formatted text using ${audioModel.id} (${audioBase64.length} chars base64)")

        // Build the AI request with audio content
        val request = aiRequest {
            model(audioModel)  // Audio-capable model from server config
            systemPrompt(buildSystemPrompt())
            userMessageWithAudio(
                text = buildAudioPrompt(),
                audioBase64 = audioBase64,
                mimeType = mimeType
            )
            temperature(0.4f) // Slightly higher for more natural writing
        }

        // Execute AI call with retry logic
        var lastError: String = "Unknown error"
        for (attempt in 1..MAX_RETRIES) {
            if (attempt > 1) {
                Log.i(TAG, "Retry attempt $attempt/$MAX_RETRIES after delay")
                delay(RETRY_DELAY_MS)
            }

            when (val response = AiClient.execute(request)) {
                is AiResponse.Success -> {
                    Log.d(TAG, "Gemini response: ${response.data.take(500)}...")
                    val result = parseResponse(response.data)
                    if (result.success) {
                        return result
                    }
                    lastError = result.errorMessage ?: "Empty response"
                }
                is AiResponse.Error.RateLimitError -> {
                    Log.w(TAG, "Rate limited (attempt $attempt), retry after ${response.retryAfterSeconds}s")
                    lastError = "Service busy"
                    // Continue to retry
                }
                is AiResponse.Error.NetworkError -> {
                    Log.e(TAG, "Network error: ${response.message}")
                    // Network errors - no point retrying
                    return ParsedText.empty("No internet connection")
                }
                is AiResponse.Error -> {
                    Log.e(TAG, "AI error (attempt $attempt): ${response.message}")
                    lastError = response.message
                    // May be transient, continue to retry
                }
            }
        }

        Log.e(TAG, "All retry attempts failed: $lastError")
        return ParsedText.empty("Could not process audio: $lastError")
    }

    /**
     * Build the system prompt that instructs Gemini on formatting.
     * NOTE: Headers/titles are intentionally NOT added - user wants raw content formatting only.
     */
    private fun buildSystemPrompt(): String {
        return """
You are an expert transcription and text formatting assistant. Your job is to transform spoken audio into well-formatted written text that ENHANCES readability.

## YOUR GOALS

1. **Accurate Transcription**: Capture what the user said faithfully
2. **Proper Punctuation**: Add periods, commas, colons, semicolons, question marks, exclamation points
3. **Correct Capitalization**: Proper nouns, sentence starts, acronyms
4. **Logical Structure**: Organize content with paragraphs and lists (NO headers/titles)
5. **Grammar Enhancement**: Fix grammatical errors while preserving the speaker's voice and intent
6. **Cohesive Flow**: Improve transitions and logical flow between ideas
7. **Clarity**: Rephrase awkward phrasing for better readability
8. **INTELLIGENT FORMATTING**: Proactively apply inline formatting to highlight important information

## OUTPUT FORMAT: MARKDOWN FORMATTING (NO HEADERS)

IMPORTANT: Do NOT add any headers (# ## ###) or titles to the output. The user wants their spoken content formatted, not categorized or labeled.

### Bold - HIGHLIGHT IMPORTANCE
- `**important**` - Make key terms, action items, names, dates, deadlines BOLD
- If something sounds emphasized or is contextually critical, make it **bold**
- Examples of what to bold:
  - **Names**: **John**, **Sarah**, **Dr. Smith**
  - **Dates/Times**: **Friday**, **March 15**, **3pm**, **next week**
  - **Amounts**: **$500**, **three items**, **50%**
  - **Deadlines**: **due tomorrow**, **by end of day**
  - **Priorities**: **urgent**, **important**, **critical**, **ASAP**
  - **Action items**: **call**, **email**, **review**, **finish**

### Italic - ADD EMPHASIS
- `*emphasis*` - Use for titles of works, foreign words, or softer emphasis
- Also good for: thoughts, quotes, or secondary emphasis

### Lists - STRUCTURE CONTENT
- `- item` - If content has multiple related items, format as bullet list
- `1. item` - If content has sequential steps or numbered items, use numbered list
- INFER list structure even from continuous speech if content is clearly list-like
- Example: "buy milk eggs and bread" → bullet list

### Smart Formatting Decisions
- **Detect to-do items**: "I need to", "remember to", "don't forget" → action list
- **Detect priorities**: "urgent", "important", "critical" → **bold**
- **Detect dates/times**: Any temporal reference → **bold** for visibility
- **Detect names**: People mentioned → **bold** (they're usually important)
- **Detect amounts**: Money, quantities, percentages → **bold**
- **Detect shopping lists**: Convert to bullet list format
- **Detect sequential items**: "first... second... third..." → numbered list

## EXAMPLES

**Input (spoken)**: "okay so i need to remember three things first pick up milk second call mom and third finish the report by friday"

**Output (Markdown)**:
```
1. Pick up milk
2. Call **Mom**
3. Finish the report by **Friday**
```

**Input (spoken)**: "meeting notes from today we discussed the new product launch timeline john suggested moving it to march but sarah thinks february is better we need to decide by next week also budget needs to be finalized the budget is around fifty thousand dollars"

**Output (Markdown)**:
```
We discussed the new product launch timeline:
- **John's suggestion**: Move launch to **March**
- **Sarah's preference**: Keep **February** timeline
- **Decision deadline**: **Next week**

Budget needs to be finalized. Estimated: **$50,000**
```

**Input (spoken)**: "urgent reminder call doctor smith tomorrow at 9am about the test results also pick up prescription"

**Output (Markdown)**:
```
**URGENT**:
- Call **Dr. Smith** - **Tomorrow 9am** (regarding test results)
- Pick up prescription
```

**Input (spoken)**: "hey just wanted to jot down my thoughts on the project i think we should focus on user experience first then worry about the backend the current design is okay but could be more intuitive"

**Output (Markdown)**:
```
I think we should focus on **user experience first**, then worry about the backend.

The current design is okay, but could be more intuitive.
```

**Input (spoken)**: "remember to buy eggs bread cheese and milk for dinner tonight also get wine for about twenty dollars"

**Output (Markdown)**:
```
For dinner tonight:
- Eggs
- Bread
- Cheese
- Milk
- Wine (~**$20**)
```

**Input (spoken)**: "project alpha deadline is march fifteenth team members are sarah john and mike budget approved"

**Output (Markdown)**:
```
**Project Alpha**
**Deadline:** March 15
**Team:** Sarah, John, Mike
✓ Budget approved
```

## IMPORTANT RULES

1. **NO HEADERS**: Do NOT add # ## ### headers or titles - output content directly
2. **Preserve Intent**: Don't change the meaning or add information that wasn't spoken
3. **Natural Language**: The output should read naturally, not sound robotic
4. **SMART Structure**: Use lists and bold to make content scannable (but NO headers)
5. **Highlight Key Info**: Names, dates, amounts, and action items should stand out
6. **Speaker's Voice**: Maintain the speaker's tone (formal, casual, etc.)
7. **Fix Filler Words**: Remove "um", "uh", "like", "you know" unless they add meaning
8. **Complete Sentences**: Turn fragments into complete sentences when it improves clarity
""".trimIndent()
    }

    /**
     * Build the prompt that accompanies the audio.
     */
    private fun buildAudioPrompt(): String {
        return """
Listen to this audio recording and transcribe it into intelligently formatted text.

Apply SMART formatting to enhance readability:
- Add proper punctuation and capitalization
- Structure with lists and paragraphs where appropriate (NO headers/titles)
- Improve grammar and flow while preserving meaning
- Use Markdown formatting with INTELLIGENT emphasis:
  - **Bold** names, dates, deadlines, amounts, and important terms
  - Convert enumerated items into bullet/numbered lists
  - Infer structure and importance from context

IMPORTANT: Do NOT add any headers (# ## ###) or titles. Output the content directly.

Return ONLY the formatted Markdown text, nothing else. No explanations, no meta-commentary.
If the audio is unclear or empty, return a brief note like "Could not understand audio" or "No speech detected".
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW UNIFIED API - Used by TranscriptionService integration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Format pre-transcribed text for memo/note insertion.
     *
     * This is the NEW primary API for TranscriptionService integration.
     * Takes text that was already transcribed (by SpeechRecognizer or Gemini)
     * and formats it with proper punctuation, capitalization, and structure.
     *
     * @param rawText Pre-transcribed text from SpeechRecognizer or Gemini
     * @return ParsedText with formatted plain and markdown versions
     */
    suspend fun formatTranscribedText(rawText: String): ParsedText {
        if (rawText.isBlank()) {
            return ParsedText.empty("No text to format")
        }

        Log.d(TAG, "formatTranscribedText: '${rawText.take(100)}...' (${rawText.length} chars)")

        // Without the user's own OpenRouter key the text goes in as heard (Oct 2026)
        if (!com.boox.day.security.UserOpenRouterKey.hasKey) {
            val capitalized = rawText.trim().replaceFirstChar { it.uppercaseChar() }
            com.boox.day.ui.SnackState.globalSnackFlow.tryEmit(
                com.boox.day.ui.SnackConf(
                    text = "Added as heard. Add an OpenRouter key in AI Settings for tidier text.",
                    duration = com.boox.day.ui.SnackConf.DURATION_STANDARD
                )
            )
            return ParsedText(
                plainText = capitalized,
                markdownText = capitalized,
                originalTranscript = rawText,
                success = true
            )
        }

        // Use TextFormattingService for consistent formatting
        val formattingResult = TextFormattingService.formatText(rawText)

        return if (formattingResult.success) {
            ParsedText(
                plainText = formattingResult.plainText,
                markdownText = formattingResult.markdownText,
                originalTranscript = rawText,
                success = true,
                errorMessage = formattingResult.errorMessage // May contain partial success message
            )
        } else {
            // Fallback: Use raw text with basic capitalization
            val capitalized = rawText.trim().replaceFirstChar { it.uppercaseChar() }
            ParsedText(
                plainText = capitalized,
                markdownText = capitalized,
                originalTranscript = rawText,
                success = true, // Partial success - we have usable text
                errorMessage = formattingResult.errorMessage
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse the Gemini response into ParsedText.
     */
    private fun parseResponse(response: String): ParsedText {
        val trimmedResponse = response.trim()

        // Check for error responses
        if (trimmedResponse.isEmpty() ||
            trimmedResponse.lowercase().contains("could not understand") ||
            trimmedResponse.lowercase().contains("no speech detected") ||
            trimmedResponse.lowercase().contains("audio is unclear")) {
            return ParsedText.empty(trimmedResponse.ifEmpty { "No speech detected" })
        }

        // Extract plain text by stripping Markdown
        val plainText = stripMarkdown(trimmedResponse)

        return ParsedText(
            plainText = plainText,
            markdownText = trimmedResponse,
            originalTranscript = plainText, // Best approximation without separate transcription
            success = true
        )
    }

    /**
     * Strip Markdown formatting to get plain text.
     * Used for the content field (searchable, backward compatible).
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
