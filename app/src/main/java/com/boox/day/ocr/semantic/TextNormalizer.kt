package com.boox.day.ocr.semantic

import java.text.Normalizer

/**
 * Step 1: Text normalization for semantic extraction.
 *
 * Performs:
 * - NFKC Unicode normalization
 * - Whitespace collapse
 * - OCR garbage removal
 * - Line deduplication
 * - Paragraph/sentence splitting
 * - OCR confusion fixes (0/O, 1/l/I)
 */
object TextNormalizer {

    /**
     * Maximum text length to process (25,000 chars)
     */
    private const val MAX_TEXT_LENGTH = 25_000

    /**
     * If text exceeds max, keep first N chars
     */
    private const val KEEP_FIRST = 15_000

    /**
     * If text exceeds max, keep last N chars
     */
    private const val KEEP_LAST = 5_000

    /**
     * Result of text normalization.
     */
    data class NormalizationResult(
        val cleanText: String,
        val normalizedText: String,
        val paragraphs: List<String>,
        val sentences: List<String>,
        val originalLength: Int,
        val wasTruncated: Boolean
    )

    /**
     * Normalize OCR text for semantic extraction.
     */
    fun normalize(text: String): NormalizationResult {
        val originalLength = text.length

        // Step 0: Truncate if too long
        val (truncatedText, wasTruncated) = truncateIfNeeded(text)

        // Step 1: NFKC normalization
        val nfkc = Normalizer.normalize(truncatedText, Normalizer.Form.NFKC)

        // Step 2: Basic whitespace normalization
        val whitespaceNormalized = normalizeWhitespace(nfkc)

        // Step 3: Remove OCR garbage
        val garbageRemoved = removeOcrGarbage(whitespaceNormalized)

        // Step 4: Fix OCR confusions (0/O, 1/l/I)
        val confusionsFixed = fixOcrConfusions(garbageRemoved)

        // Step 5: Deduplicate repeated lines
        val deduped = deduplicateLines(confusionsFixed)

        // Step 6: Split into paragraphs
        val paragraphs = splitParagraphs(deduped)

        // Step 7: Split into sentences
        val sentences = splitSentences(deduped)

        // Final normalized text (for search)
        val normalizedText = deduped.lowercase().trim()

        return NormalizationResult(
            cleanText = deduped,
            normalizedText = normalizedText,
            paragraphs = paragraphs,
            sentences = sentences,
            originalLength = originalLength,
            wasTruncated = wasTruncated
        )
    }

    /**
     * Truncate text if it exceeds maximum length.
     * Keeps first 15K + last 5K chars, dropping middle.
     */
    private fun truncateIfNeeded(text: String): Pair<String, Boolean> {
        if (text.length <= MAX_TEXT_LENGTH) {
            return text to false
        }

        val first = text.take(KEEP_FIRST)
        val last = text.takeLast(KEEP_LAST)
        return "$first\n\n[...content truncated...]\n\n$last" to true
    }

    /**
     * Normalize whitespace - collapse multiple spaces/tabs to single space.
     */
    private fun normalizeWhitespace(text: String): String {
        return text
            .replace(Regex("[\t ]+"), " ")
            .replace(Regex(" *\n"), "\n")
            .replace(Regex("\n{4,}"), "\n\n\n")  // Max 3 consecutive newlines
            .trim()
    }

    /**
     * Remove OCR garbage patterns.
     */
    private fun removeOcrGarbage(text: String): String {
        return text
            // Remove repeated dashes, underscores, pipes, hashes
            .replace(Regex("-{4,}"), "---")
            .replace(Regex("_{4,}"), "___")
            .replace(Regex("\\|{3,}"), "||")
            .replace(Regex("#{4,}"), "###")
            .replace(Regex("={4,}"), "===")
            .replace(Regex("\\*{4,}"), "***")
            // Remove very long repeated characters (more than 5)
            .replace(Regex("(.)\\1{5,}"), "$1$1$1")
            // Remove isolated single special characters on lines
            .replace(Regex("^[\\-_|#=*]$", RegexOption.MULTILINE), "")
            // Remove excessive punctuation
            .replace(Regex("[.,!?]{4,}"), "...")
            // Remove control characters except newlines/tabs
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .trim()
    }

    /**
     * Fix common OCR confusions based on context.
     *
     * Only fixes high-confidence cases:
     * - 0 surrounded by letters likely should be O (e.g., "G00gle" -> "Google")
     * - O surrounded by digits likely should be 0 (e.g., "1O0" -> "100")
     * - 1 at word start/middle that looks like l/I
     * - l surrounded by digits likely should be 1
     */
    private fun fixOcrConfusions(text: String): String {
        var result = text

        // Fix 0 -> O when surrounded by letters (e.g., "G00gle" -> "Google")
        // Pattern: letter + 0 + letter
        result = result.replace(Regex("(?<=[a-zA-Z])0(?=[a-zA-Z])"), "O")

        // Fix 00 -> OO when at start of word followed by letters (e.g., "00gle" -> "OOgle")
        result = result.replace(Regex("(?<=\\b)00(?=[a-zA-Z]{2,})"), "OO")

        // Fix O -> 0 when surrounded by digits (e.g., "1O0" -> "100")
        result = result.replace(Regex("(?<=[0-9])O(?=[0-9])"), "0")

        // Fix l -> 1 when surrounded by digits (e.g., "l23" at word boundary -> "123")
        result = result.replace(Regex("(?<=[0-9])l(?=[0-9])"), "1")
        result = result.replace(Regex("(?<=\\b)l(?=[0-9]{2,})"), "1")

        // Fix common words with digit substitutions
        val commonFixes = mapOf(
            "0ne" to "One",
            "0nly" to "Only",
            "0n" to "On",
            "0f" to "Of",
            "0r" to "Or",
            "1f" to "If",
            "1n" to "In",
            "1s" to "Is",
            "1t" to "It",
            "Ca11" to "Call",
            "emai1" to "email",
            "Emai1" to "Email"
        )

        for ((wrong, correct) in commonFixes) {
            result = result.replace(Regex("\\b$wrong\\b", RegexOption.IGNORE_CASE)) { match ->
                if (match.value[0].isUpperCase()) correct else correct.lowercase()
            }
        }

        return result
    }

    /**
     * Deduplicate lines that appear 3+ times.
     */
    private fun deduplicateLines(text: String): String {
        val lines = text.split("\n")
        val lineCounts = lines.groupingBy { it.trim() }.eachCount()
        val seenDuplicates = mutableSetOf<String>()

        return lines.mapNotNull { line ->
            val trimmed = line.trim()
            val count = lineCounts[trimmed] ?: 0

            when {
                trimmed.isEmpty() -> line  // Keep empty lines
                count < 3 -> line  // Not a duplicate
                trimmed in seenDuplicates -> null  // Already shown once
                else -> {
                    seenDuplicates.add(trimmed)
                    line  // First occurrence of duplicate
                }
            }
        }.joinToString("\n")
    }

    /**
     * Split text into paragraphs (separated by blank lines).
     */
    private fun splitParagraphs(text: String): List<String> {
        return text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Split text into sentences.
     */
    private fun splitSentences(text: String): List<String> {
        // Split on sentence boundaries while keeping delimiters
        val sentencePattern = Regex("(?<=[.!?])\\s+(?=[A-Z])|(?<=\\n)")

        return text.split(sentencePattern)
            .flatMap { chunk ->
                // Further split on newlines
                chunk.split("\n")
            }
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 3 }
    }
}
