package com.boox.day.utils

/**
 * Utility for stripping markdown formatting from text while preserving readability.
 *
 * Used to display clean text to users in AI result dialogs while keeping
 * the original markdown for storage/rendering on memo pages.
 *
 * Strips:
 * - Headers (# ## ### #### ##### ######)
 * - Bold (**text** and __text__)
 * - Italic (*text* and _text_)
 * - Bold+Italic (***text*** and ___text___)
 * - Strikethrough (~~text~~)
 * - Inline code (`code`)
 * - Code blocks (```code```)
 * - Links [text](url) → text
 * - Images ![alt](url) → alt
 *
 * Preserves:
 * - List markers (- * + and 1. 2. etc.)
 * - Line breaks and paragraph structure
 * - Plain text content
 */
object MarkdownStripper {

    /**
     * Strips markdown formatting symbols from text.
     *
     * @param markdown The markdown-formatted text from AI
     * @return Clean text suitable for display to users
     */
    fun strip(markdown: String): String {
        if (markdown.isBlank()) return markdown

        var text = markdown

        // 1. Strip fenced code blocks first (```lang\ncode\n```)
        text = stripCodeBlocks(text)

        // 2. Process line-start patterns (headers)
        text = text.lines().joinToString("\n") { line ->
            stripHeaderMarkers(line)
        }

        // 3. Strip inline formatting (bold, italic, etc.)
        text = stripInlineFormatting(text)

        // 4. Strip links and images
        text = stripLinksAndImages(text)

        // 5. Final whitespace cleanup
        text = cleanupWhitespace(text)

        return text.trim()
    }

    /**
     * Strips fenced code blocks while preserving the code content.
     * Handles both ```language\ncode``` and plain ```code``` formats.
     */
    private fun stripCodeBlocks(text: String): String {
        var result = text

        // Match ```lang\ncode\n``` (with optional language identifier)
        result = result.replace(Regex("```\\w*\\r?\\n([\\s\\S]*?)\\r?\\n```")) { match ->
            match.groupValues[1]
        }

        // Fallback: match ```code``` without newlines
        result = result.replace(Regex("```([^`]+)```")) { match ->
            match.groupValues[1]
        }

        return result
    }

    /**
     * Strips header markers (# ## ### etc.) from line start.
     * Preserves the header text content.
     */
    private fun stripHeaderMarkers(line: String): String {
        // Match 1-6 # symbols followed by space at line start
        return line.replace(Regex("^#{1,6}\\s+"), "")
    }

    /**
     * Strips inline formatting markers while preserving text content.
     * Order matters: process combined formats before individual ones.
     */
    private fun stripInlineFormatting(text: String): String {
        var result = text

        // Bold+Italic: ***text*** or ___text___
        result = result.replace(Regex("\\*{3}([^*]+)\\*{3}"), "$1")
        result = result.replace(Regex("_{3}([^_]+)_{3}"), "$1")

        // Bold: **text** or __text__
        result = result.replace(Regex("\\*{2}([^*]+)\\*{2}"), "$1")
        result = result.replace(Regex("_{2}([^_]+)_{2}"), "$1")

        // Strikethrough: ~~text~~
        result = result.replace(Regex("~~([^~]+)~~"), "$1")

        // Inline code: `code`
        result = result.replace(Regex("`([^`]+)`"), "$1")

        // Italic: *text* - careful not to match list markers or bold
        // Match *text* where:
        // - Not preceded by * (would be bold)
        // - Not followed by * (would be bold)
        // - Content is non-empty and doesn't contain *
        result = result.replace(Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)"), "$1")

        // Italic: _text_ - similar care needed
        result = result.replace(Regex("(?<!_)_([^_\\n]+?)_(?!_)"), "$1")

        return result
    }

    /**
     * Strips link and image markdown while preserving text/alt content.
     */
    private fun stripLinksAndImages(text: String): String {
        var result = text

        // Images: ![alt text](url) → alt text
        result = result.replace(Regex("!\\[([^\\]]*)\\]\\([^)]+\\)"), "$1")

        // Links: [link text](url) → link text
        result = result.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

        // Reference-style links: [text][ref] → text
        result = result.replace(Regex("\\[([^\\]]+)\\]\\[[^\\]]*\\]"), "$1")

        return result
    }

    /**
     * Cleans up excessive whitespace while preserving paragraph structure.
     */
    private fun cleanupWhitespace(text: String): String {
        var result = text

        // Collapse multiple spaces/tabs to single space
        result = result.replace(Regex("[ \\t]{2,}"), " ")

        // Collapse 3+ newlines to double newline (paragraph break)
        result = result.replace(Regex("\\n{3,}"), "\n\n")

        // Trim trailing whitespace from each line
        result = result.lines().joinToString("\n") { it.trimEnd() }

        return result
    }
}
