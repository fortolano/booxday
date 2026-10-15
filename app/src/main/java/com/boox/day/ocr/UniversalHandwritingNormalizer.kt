package com.boox.day.ocr

import android.util.Log

/**
 * Lightweight, language-agnostic handwriting normalizer.
 *
 * Used for non-English languages where full dictionary-based
 * processing is not available. Provides basic cleanup without
 * requiring language-specific word lists.
 *
 * Pipeline (3 stages):
 * 1. Whitespace normalization - collapse multiple spaces, trim
 * 2. Trailing noise removal - remove punctuation artifacts
 * 3. Smart capitalization - capitalize first letter (Latin/Cyrillic only)
 *
 * Thread-safe singleton. No initialization required.
 */
object UniversalHandwritingNormalizer {

    private const val TAG = "UniversalNormalizer"

    // Languages with no case concept (CJK scripts + Arabic)
    private val NO_CASE_LANGUAGES = setOf("zh-Hans", "zh-Hant", "ja", "ko", "ar")

    /**
     * Normalize handwriting recognition result (language-agnostic).
     *
     * @param text Raw recognition result from ML Kit
     * @param languageTag BCP-47 language tag (e.g., "es-ES", "fr-FR")
     * @return Normalized text with basic cleanup
     */
    fun normalize(text: String, languageTag: String): String {
        if (text.isBlank()) return text

        try {
            var result = text

            // Stage 1: Normalize whitespace
            result = normalizeWhitespace(result)

            // Stage 2: Remove trailing punctuation noise
            result = removeTrailingNoise(result)

            // Stage 3: Smart capitalization (language-aware)
            result = smartCapitalize(result, languageTag)

            Log.d(TAG, "[$languageTag] Normalized: '$text' → '$result'")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Normalization failed for '$text': ${e.message}")
            return text
        }
    }

    /**
     * Stage 1: Normalize whitespace patterns.
     */
    private fun normalizeWhitespace(text: String): String {
        // Collapse multiple spaces into one
        var result = text.replace(Regex("\\s+"), " ")
        // Trim leading/trailing whitespace
        result = result.trim()
        return result
    }

    /**
     * Stage 2: Remove trailing punctuation artifacts.
     * ML Kit sometimes adds spurious punctuation at the end.
     */
    private fun removeTrailingNoise(text: String): String {
        // Remove trailing punctuation that looks like recognition noise
        var result = text.trimEnd('.', ',', ';', ':', '!', '?', '-', '_')
        // Also trim any whitespace introduced
        result = result.trimEnd()
        return result
    }

    /**
     * Stage 3: Smart capitalization based on language script.
     *
     * - CJK languages (Chinese, Japanese, Korean): No changes (no case concept)
     * - Latin/Cyrillic scripts: Capitalize first letter only
     */
    private fun smartCapitalize(text: String, languageTag: String): String {
        // Don't capitalize CJK languages (no concept of case)
        if (languageTag in NO_CASE_LANGUAGES) {
            return text
        }

        // For Latin/Cyrillic scripts: capitalize first letter of sentence
        if (text.isEmpty()) return text
        return text.replaceFirstChar { it.uppercase() }
    }

    /**
     * Batch normalize multiple results.
     *
     * @param texts List of raw recognition results
     * @param languageTag BCP-47 language tag
     * @return List of normalized texts
     */
    fun normalizeBatch(texts: List<String>, languageTag: String): List<String> {
        return texts.map { normalize(it, languageTag) }
    }
}
