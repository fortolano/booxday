package com.boox.day.utils

/**
 * Utility for splitting large text across multiple memo pages.
 *
 * Splits at natural boundaries to maintain readability:
 * 1. Paragraph breaks (double newlines)
 * 2. Sentence endings (.!?)
 * 3. Word boundaries (spaces)
 *
 * Used by Summarize and Translate features when results exceed
 * single page capacity.
 */
object TextSplitUtils {

    /**
     * Safe character limit per memo page.
     * Calculated based on:
     * - Average handwriting density on e-ink
     * - TextAnnotation rendering at standard font size
     * - Leaving margin for user edits
     */
    const val MAX_CHARS_PER_PAGE = 900

    /**
     * Split text into page-sized chunks at natural boundaries.
     *
     * @param text The full text to split
     * @param maxCharsPerPage Optional override for page capacity (defaults to MAX_CHARS_PER_PAGE)
     * @return List of text chunks, each fitting on one page
     */
    fun splitForPages(text: String, maxCharsPerPage: Int = MAX_CHARS_PER_PAGE): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxCharsPerPage) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        var remaining = trimmed

        while (remaining.isNotEmpty()) {
            val chunk = if (remaining.length <= maxCharsPerPage) {
                remaining
            } else {
                findBestBreakPoint(remaining, maxCharsPerPage)
            }

            chunks.add(chunk.trim())
            remaining = remaining.drop(chunk.length).trimStart()
        }

        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Calculate how many pages are needed for the given text.
     *
     * @param text The text to measure
     * @return Number of pages required (minimum 1 for non-empty text)
     */
    fun calculateRequiredPages(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        if (trimmed.length <= MAX_CHARS_PER_PAGE) return 1

        return splitForPages(trimmed).size
    }

    /**
     * Find the best break point within maxLength characters.
     * Tries to break at natural boundaries in order:
     * 1. Paragraph break (double newline)
     * 2. Single newline
     * 3. Sentence ending (.!? followed by space)
     * 4. Comma or semicolon
     * 5. Word boundary (space)
     * 6. Hard cut at maxLength (last resort)
     */
    private fun findBestBreakPoint(text: String, maxLength: Int): String {
        val searchRange = text.take(maxLength)

        // 1. Try paragraph break (double newline) - best for readability
        val paragraphBreak = searchRange.lastIndexOf("\n\n")
        if (paragraphBreak > maxLength / 3) {
            return text.take(paragraphBreak + 2)
        }

        // 2. Try single newline
        val singleNewline = searchRange.lastIndexOf('\n')
        if (singleNewline > maxLength / 3) {
            return text.take(singleNewline + 1)
        }

        // 3. Try sentence ending (.!? followed by space or newline)
        val sentenceEndRegex = Regex("[.!?][\\s\\n]")
        val sentenceMatches = sentenceEndRegex.findAll(searchRange)
        val lastSentenceEnd = sentenceMatches.lastOrNull()
        if (lastSentenceEnd != null && lastSentenceEnd.range.first > maxLength / 3) {
            return text.take(lastSentenceEnd.range.last)
        }

        // 4. Try comma or semicolon (clause boundary)
        val clauseBreak = maxOf(
            searchRange.lastIndexOf(", "),
            searchRange.lastIndexOf("; ")
        )
        if (clauseBreak > maxLength / 2) {
            return text.take(clauseBreak + 2)
        }

        // 5. Try word boundary (space)
        val lastSpace = searchRange.lastIndexOf(' ')
        if (lastSpace > maxLength / 2) {
            return text.take(lastSpace + 1)
        }

        // 6. Hard cut (last resort - avoids infinite loops)
        return text.take(maxLength)
    }

    /**
     * Add page separators to a list of page contents.
     * Used for translation results to show page boundaries.
     *
     * @param pages List of page contents
     * @param separatorFormat Format string with %d for page number (e.g., "--- Page %d ---")
     * @return Combined text with separators
     */
    fun joinWithSeparators(pages: List<String>, separatorFormat: String = "--- Page %d ---"): String {
        return pages.mapIndexed { index, content ->
            if (index == 0) {
                content
            } else {
                "${separatorFormat.format(index + 1)}\n\n$content"
            }
        }.joinToString("\n\n")
    }
}
