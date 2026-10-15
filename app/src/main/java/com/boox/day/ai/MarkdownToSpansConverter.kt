package com.boox.day.ai

import com.boox.day.data.model.*

/**
 * Converts Markdown-formatted text to RichTextSpan list.
 *
 * Supports:
 * - Headers: # H1, ## H2, ### H3
 * - Bold: **text** or __text__
 * - Italic: *text* or _text_
 * - Bullet lists: - item or * item
 * - Numbered lists: 1. item
 * - Strikethrough: ~~text~~
 *
 * Returns both:
 * - Plain text (content field)
 * - List of RichTextSpans (spans field as JSON)
 */
object MarkdownToSpansConverter {

    /**
     * Result of Markdown conversion.
     */
    data class ConversionResult(
        val plainText: String,
        val spans: List<RichTextSpan>
    ) {
        /**
         * Get spans as JSON string for storage.
         */
        fun spansAsJson(): String = RichTextSpanSerializer.encode(spans)
    }

    /**
     * Convert Markdown text to plain text + spans.
     */
    fun convert(markdown: String): ConversionResult {
        val spans = mutableListOf<RichTextSpan>()
        val plainTextBuilder = StringBuilder()

        // Process line by line for block-level elements
        val lines = markdown.lines()
        var currentPosition = 0
        var listNumber = 0

        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()

            // Track if we need a newline after this line
            val needsNewline = index < lines.lastIndex

            when {
                // Empty line - just add newline
                trimmedLine.isEmpty() -> {
                    if (plainTextBuilder.isNotEmpty() && !plainTextBuilder.endsWith("\n\n")) {
                        plainTextBuilder.append("\n")
                        currentPosition++
                    }
                    listNumber = 0 // Reset list numbering
                }

                // Header: # Title
                trimmedLine.startsWith("#") -> {
                    val headerMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmedLine)
                    if (headerMatch != null) {
                        val level = headerMatch.groupValues[1].length.coerceIn(1, 3)
                        val content = headerMatch.groupValues[2]

                        val startPos = currentPosition
                        val (processedText, inlineSpans) = processInlineFormatting(content, startPos)
                        plainTextBuilder.append(processedText)

                        val endPos = currentPosition + processedText.length
                        spans.add(HeaderSpan(start = startPos, end = endPos, level = level))
                        spans.addAll(inlineSpans)

                        currentPosition = endPos
                        listNumber = 0
                    } else {
                        // Malformed header, treat as regular text
                        val (processedText, inlineSpans) = processInlineFormatting(trimmedLine, currentPosition)
                        plainTextBuilder.append(processedText)
                        spans.addAll(inlineSpans)
                        currentPosition += processedText.length
                    }

                    if (needsNewline) {
                        plainTextBuilder.append("\n")
                        currentPosition++
                    }
                }

                // Bullet list: - item or * item or + item
                trimmedLine.matches(Regex("^[-*+]\\s+.+")) -> {
                    val content = trimmedLine.replaceFirst(Regex("^[-*+]\\s+"), "")
                    val bulletChar = "• "

                    val startPos = currentPosition
                    plainTextBuilder.append(bulletChar)
                    currentPosition += bulletChar.length

                    val contentStartPos = currentPosition
                    val (processedText, inlineSpans) = processInlineFormatting(content, contentStartPos)
                    plainTextBuilder.append(processedText)

                    val endPos = currentPosition + processedText.length
                    spans.add(BulletSpan(start = startPos, end = endPos))
                    spans.addAll(inlineSpans)

                    currentPosition = endPos
                    listNumber = 0

                    if (needsNewline) {
                        plainTextBuilder.append("\n")
                        currentPosition++
                    }
                }

                // Numbered list: 1. item
                trimmedLine.matches(Regex("^\\d+\\.\\s+.+")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.+)").find(trimmedLine)
                    if (match != null) {
                        listNumber++
                        val content = match.groupValues[2]
                        val numberPrefix = "$listNumber. "

                        val startPos = currentPosition
                        plainTextBuilder.append(numberPrefix)
                        currentPosition += numberPrefix.length

                        val contentStartPos = currentPosition
                        val (processedText, inlineSpans) = processInlineFormatting(content, contentStartPos)
                        plainTextBuilder.append(processedText)

                        val endPos = currentPosition + processedText.length
                        spans.add(NumberedListSpan(start = startPos, end = endPos, number = listNumber))
                        spans.addAll(inlineSpans)

                        currentPosition = endPos
                    }

                    if (needsNewline) {
                        plainTextBuilder.append("\n")
                        currentPosition++
                    }
                }

                // Regular paragraph
                else -> {
                    val (processedText, inlineSpans) = processInlineFormatting(trimmedLine, currentPosition)
                    plainTextBuilder.append(processedText)
                    spans.addAll(inlineSpans)
                    currentPosition += processedText.length
                    listNumber = 0

                    if (needsNewline) {
                        plainTextBuilder.append("\n")
                        currentPosition++
                    }
                }
            }
        }

        return ConversionResult(
            plainText = plainTextBuilder.toString().trimEnd(),
            spans = RichTextSpanSerializer.sortSpans(spans)
        )
    }

    /**
     * Process inline formatting (bold, italic, strikethrough) within a line.
     * Returns the plain text and list of spans.
     */
    private fun processInlineFormatting(
        text: String,
        startOffset: Int
    ): Pair<String, List<RichTextSpan>> {
        val spans = mutableListOf<RichTextSpan>()
        val result = StringBuilder()
        var position = startOffset

        // Define inline patterns with their span creators
        data class InlinePattern(
            val regex: Regex,
            val createSpan: (start: Int, end: Int) -> RichTextSpan
        )

        val patterns = listOf(
            // Bold: **text** or __text__ (check first, it's more specific)
            InlinePattern(Regex("\\*\\*(.+?)\\*\\*")) { s, e -> BoldSpan(s, e) },
            InlinePattern(Regex("__(.+?)__")) { s, e -> BoldSpan(s, e) },
            // Italic: *text* or _text_
            InlinePattern(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")) { s, e -> ItalicSpan(s, e) },
            InlinePattern(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)")) { s, e -> ItalicSpan(s, e) },
            // Strikethrough: ~~text~~
            InlinePattern(Regex("~~(.+?)~~")) { s, e -> StrikethroughSpan(s, e) }
        )

        // Process text character by character, finding and replacing patterns
        var processedText = text
        var offset = 0

        // Process bold first (** or __)
        processedText = processSinglePattern(
            processedText,
            Regex("\\*\\*(.+?)\\*\\*"),
            spans,
            startOffset
        ) { s, e -> BoldSpan(s, e) }

        processedText = processSinglePattern(
            processedText,
            Regex("__(.+?)__"),
            spans,
            startOffset
        ) { s, e -> BoldSpan(s, e) }

        // Process italic (* or _) - need to avoid matching inside bold
        processedText = processSinglePattern(
            processedText,
            Regex("(?<![*_])\\*([^*]+)\\*(?![*_])"),
            spans,
            startOffset
        ) { s, e -> ItalicSpan(s, e) }

        processedText = processSinglePattern(
            processedText,
            Regex("(?<![*_])_([^_]+)_(?![*_])"),
            spans,
            startOffset
        ) { s, e -> ItalicSpan(s, e) }

        // Process strikethrough
        processedText = processSinglePattern(
            processedText,
            Regex("~~(.+?)~~"),
            spans,
            startOffset
        ) { s, e -> StrikethroughSpan(s, e) }

        return Pair(processedText, spans)
    }

    /**
     * Process a single regex pattern, extracting spans and returning cleaned text.
     */
    private fun processSinglePattern(
        text: String,
        pattern: Regex,
        spans: MutableList<RichTextSpan>,
        baseOffset: Int,
        createSpan: (Int, Int) -> RichTextSpan
    ): String {
        var result = text
        var offsetAdjustment = 0

        pattern.findAll(text).forEach { match ->
            val fullMatch = match.value
            val content = match.groupValues[1]
            val markerLength = (fullMatch.length - content.length) / 2

            // Calculate positions in the final plain text
            val originalStart = match.range.first
            val adjustedStart = baseOffset + originalStart - offsetAdjustment
            val adjustedEnd = adjustedStart + content.length

            spans.add(createSpan(adjustedStart, adjustedEnd))

            // We'll remove markers, so track the adjustment
            offsetAdjustment += markerLength * 2
        }

        // Remove all markers
        return pattern.replace(text) { it.groupValues[1] }
    }

    /**
     * Quick check if text contains any Markdown formatting.
     */
    fun hasMarkdownFormatting(text: String): Boolean {
        return text.contains(Regex("[*_#~-]")) ||
                text.contains(Regex("^\\d+\\.", RegexOption.MULTILINE))
    }
}
