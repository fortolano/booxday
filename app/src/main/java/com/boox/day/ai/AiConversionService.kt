package com.boox.day.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.model.RichTextSpan
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.editor.drawing.RichTextConverter
import java.io.ByteArrayOutputStream

/**
 * Result of AI handwriting conversion containing rich text data.
 * @property content Plain text content (backward compatible)
 * @property spans List of formatting spans for rich text rendering
 * @property spansJson JSON string of spans for storage in TextAnnotation
 * @property originalMarkdown The raw markdown returned by the AI
 */
data class RichTextResult(
    val content: String,
    val spans: List<RichTextSpan>,
    val spansJson: String,
    val originalMarkdown: String
)

/**
 * Service for AI-powered handwriting to text conversion using OpenRouter API.
 *
 * ## Fallback Chain (Dec 2025)
 * Vision OCR: NovaLite → Gemini2FlashExp → NemotronNano → Gpt5Nano (PAID)
 *
 * NovaLite is primary (fast, good OCR). NemotronNano (~31s) is last-resort free.
 * Gpt5Nano is paid safety net for guaranteed availability.
 *
 * This service has been refactored to use the new [AiClient] and [aiRequest] builder
 * for consistent, type-safe API interactions across the app.
 */
class AiConversionService {

    companion object {
        private const val TAG = "AiConversionService"

        // Delimiters for parsing response
        private const val START_DELIMITER = "<<<START_TEXT>>>"
        private const val END_DELIMITER = "<<<END_TEXT>>>"

        // Enhanced prompt with markdown formatting, spacing preservation, and SEMANTIC INFERENCE
        private const val CONVERSION_PROMPT = """You are an expert handwriting-to-text converter with INTELLIGENT INFERENCE capabilities. Analyze the handwritten notes in the image carefully.

YOUR TASK:
1. Extract ALL handwritten text from the image
2. INTELLIGENTLY INFER the correct meaning when text is garbled, misspelled, or unclear
3. Format the output using Markdown for structure
4. Preserve the spatial layout and spacing from the original

## CRITICAL: SEMANTIC INFERENCE

Handwriting recognition often produces garbled output. You MUST use your intelligence to infer the CORRECT meaning based on:
- Common word patterns and sentence structures
- Context clues from surrounding text
- Likely intent based on typical note-taking scenarios

### INFERENCE EXAMPLES:

| Raw Extraction | Correct Inference |
|----------------|-------------------|
| "Mooting with JO&un tAmorru at iiam" | "Meeting with John tomorrow at 11am" |
| "Buy groceriz: Miik, egs, brad" | "Buy groceries: Milk, eggs, bread" |
| "Coll Dr. Smth @ 3pn" | "Call Dr. Smith at 3pm" |
| "Finsh repart by Fridy" | "Finish report by Friday" |
| "Meting w/ Sara & Bab" | "Meeting with Sarah and Bob" |
| "Dinnr resrvation 7 oclock" | "Dinner reservation 7 o'clock" |
| "Pik up presciption" | "Pick up prescription" |
| "Schedul apt. w/ dentst" | "Schedule appointment with dentist" |

### INFERENCE RULES:
- **Names**: Capitalize and correct common name misspellings (jon → John, sara → Sarah)
- **Times**: Normalize time formats (iiam → 11am, 3pn → 3pm, 7 oclock → 7 o'clock)
- **Dates**: Correct day names (tAmorru → tomorrow, Fridy → Friday, Munday → Monday)
- **Common words**: Fix obvious typos (Mooting → Meeting, groceriz → groceries)
- **Abbreviations**: Expand or correct (apt. → appointment, w/ → with, & → and when appropriate)
- **Numbers**: Interpret numeric confusion (iiam → 11am, not "iiam")

FORMATTING RULES:
- Use **bold** for emphasized or important text (names, dates, times, amounts)
- Use - or * for bullet points if the user drew any kind of list
- Use 1. 2. 3. for numbered lists
- Add blank lines between sections that appear spatially separated
- Preserve paragraph breaks where you see clear spacing

REFINEMENT (use reasoning):
- ALWAYS infer the most likely intended meaning - don't output garbled text
- Without changing the meaning, consider whether grammar or clarity can be improved
- Do NOT embellish or add content - only refine for coherence and readability
- Keep the author's voice and intent intact

OUTPUT FORMAT (use these exact delimiters):
<<<START_TEXT>>>
[Your markdown-formatted text here - AFTER inference and correction]
<<<END_TEXT>>>

IMPORTANT:
- Output ONLY between the delimiters
- NO explanations, NO commentary, NO notes about corrections
- NEVER output garbled text - always infer the correct meaning
- If no readable text, output only: No readable text found

Now extract, INFER correct meaning, and format the handwritten text:"""
    }

    /**
     * Convert handwritten content on canvas to rich text with formatting spans.
     * This is the primary method for the new rich text system.
     *
     * Uses fallback chain: NovaLite → Gemini2FlashExp → NemotronNano → Gpt5Nano (PAID)
     *
     * @param bitmap The canvas bitmap containing handwritten notes
     * @return Result containing RichTextResult with content, spans, and original markdown
     */
    suspend fun convertHandwritingToRichText(bitmap: Bitmap): Result<RichTextResult> {
        Log.d(TAG, "convertHandwritingToRichText called")
        Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

        // 1. Encode bitmap to base64 PNG
        Log.d(TAG, "Encoding bitmap to base64...")
        val base64Image = encodeToBase64(bitmap)
        Log.d(TAG, "Base64 length: ${base64Image.length} chars")

        // 2. Vision models fallback chain - uses 3×3 retry via AiRetryExecutor
        val modelsToTry = ModelConfigProvider.getVisionFallbackChain()

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    userMessageWithImage(CONVERSION_PROMPT, base64Image)
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                val rawContent = response.data
                Log.d(TAG, "Raw content (${model.displayName}): ${rawContent.take(300)}...")

                val richTextResult = parseResponseToRichText(rawContent)
                Log.i(TAG, "SUCCESS! RichTextResult: ${richTextResult.content.length} chars, ${richTextResult.spans.size} spans")

                // Return null if no readable text (triggers retry)
                if (richTextResult.content.equals("No readable text found", ignoreCase = true)) {
                    null
                } else {
                    richTextResult
                }
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> Result.success(result.data)
            is AiRetryExecutor.RetryResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Convert handwritten content on canvas to formatted text.
     *
     * @deprecated Use [convertHandwritingToRichText] instead for rich text support.
     *
     * @param bitmap The canvas bitmap containing handwritten notes
     * @return Result containing the formatted text or error
     */
    @Deprecated(
        message = "Use convertHandwritingToRichText() for rich text support",
        replaceWith = ReplaceWith("convertHandwritingToRichText(bitmap)")
    )
    suspend fun convertHandwritingToText(bitmap: Bitmap): Result<String> {
        Log.d(TAG, "convertHandwritingToText called (deprecated)")

        return convertHandwritingToRichText(bitmap).map { richTextResult ->
            richTextResult.content
        }
    }

    /**
     * Parse the LLM response to extract only the text between delimiters.
     * Falls back to full response if delimiters not found.
     * Returns raw markdown text (no longer strips formatting).
     */
    private fun parseResponse(response: String): String {
        val startIndex = response.indexOf(START_DELIMITER)
        val endIndex = response.indexOf(END_DELIMITER)

        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            // Extract text between delimiters
            val extracted = response.substring(startIndex + START_DELIMITER.length, endIndex).trim()
            Log.d(TAG, "Successfully parsed text between delimiters: ${extracted.length} chars")
            extracted
        } else {
            // Fallback: try to clean up common LLM artifacts
            Log.w(TAG, "Delimiters not found, attempting fallback cleanup")
            cleanupResponse(response)
        }
    }

    /**
     * Parse the LLM response and convert to RichTextResult with spans.
     * This is the primary method for new rich text system.
     */
    private fun parseResponseToRichText(response: String): RichTextResult {
        val rawMarkdown = parseResponse(response)

        // Convert markdown to plain text + spans
        val (plainText, spans) = RichTextConverter.markdownToSpans(rawMarkdown)
        val spansJson = RichTextSpanSerializer.encode(spans)

        Log.d(TAG, "Parsed to RichTextResult: ${plainText.length} chars, ${spans.size} spans")

        return RichTextResult(
            content = plainText,
            spans = spans,
            spansJson = spansJson,
            originalMarkdown = rawMarkdown
        )
    }

    /**
     * Fallback cleanup for responses without proper delimiters.
     * Removes common LLM commentary patterns.
     */
    private fun cleanupResponse(response: String): String {
        var cleaned = response.trim()

        // Remove lines that look like notes/commentary
        val linesToRemove = listOf(
            "---",
            "*Note:",
            "**Note:",
            "*Correction",
            "**Correction",
            "Here is",
            "Here's",
            "The text reads:",
            "The handwritten text",
            "I can see",
            "The image shows"
        )

        val lines = cleaned.lines().toMutableList()
        lines.removeAll { line ->
            val trimmed = line.trim()
            linesToRemove.any { pattern -> trimmed.startsWith(pattern, ignoreCase = true) }
        }

        // Remove trailing notes (everything after "---" or "*Note")
        val result = StringBuilder()
        for (line in lines) {
            if (line.trim() == "---" || line.trim().startsWith("*Note", ignoreCase = true)) {
                break
            }
            result.appendLine(line)
        }

        cleaned = result.toString().trim()
        Log.d(TAG, "Fallback cleanup result: ${cleaned.length} chars")
        return cleaned.ifEmpty { response.trim() }
    }

    /**
     * Encode bitmap to base64 PNG string
     */
    private fun encodeToBase64(bitmap: Bitmap): String {
        // Use PNG for better quality with handwritten text, with proper resource cleanup
        val imageBytes = ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        }
        Log.d(TAG, "Encoded image: ${imageBytes.size} bytes")
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }
}
