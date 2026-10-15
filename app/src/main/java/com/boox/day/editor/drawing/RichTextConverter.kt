package com.boox.day.editor.drawing

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import com.boox.day.data.model.BoldSpan
import com.boox.day.data.model.BulletSpan
import com.boox.day.data.model.ColorSpan
import com.boox.day.data.model.HeaderSpan
import com.boox.day.data.model.ItalicSpan
import com.boox.day.data.model.NumberedListSpan
import com.boox.day.data.model.RichTextSpan
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.data.model.SizeSpan
import com.boox.day.data.model.StrikethroughSpan as RichStrikethroughSpan
import com.boox.day.data.model.UnderlineSpan as RichUnderlineSpan

/**
 * Converts between RichTextSpan list and various text formats:
 * - Android Spannable (for canvas rendering via StaticLayout)
 * - Compose AnnotatedString (for BasicTextField editing)
 * - Markdown (for AI conversion)
 */
object RichTextConverter {

    private const val TAG = "RichTextConverter"

    // Header size multipliers
    private const val HEADER_1_SIZE = 1.75f
    private const val HEADER_2_SIZE = 1.5f
    private const val HEADER_3_SIZE = 1.25f

    /**
     * Convert content + spans to Android Spannable for canvas rendering.
     * Used by pageDrawing.kt and TextBoxRenderer.kt with StaticLayout.
     */
    fun toSpanned(
        content: String,
        spans: List<RichTextSpan>,
        baseFontSize: Float = 16f
    ): Spannable {
        val builder = SpannableStringBuilder(content)
        Log.d(TAG, "toSpanned: content='${content.take(50)}...', ${spans.size} spans")

        for (span in spans) {
            // Ensure valid range
            val start = span.start.coerceIn(0, content.length)
            val end = span.end.coerceIn(start, content.length)
            if (start >= end) continue

            when (span) {
                is BoldSpan -> {
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is ItalicSpan -> {
                    builder.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is HeaderSpan -> {
                    val sizeMultiplier = when (span.level) {
                        1 -> HEADER_1_SIZE
                        2 -> HEADER_2_SIZE
                        else -> HEADER_3_SIZE
                    }
                    builder.setSpan(
                        RelativeSizeSpan(sizeMultiplier),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Headers are also bold
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is BulletSpan -> {
                    // Android BulletSpan doesn't work well with StaticLayout
                    // We handle bullets in text content conversion instead
                }
                is NumberedListSpan -> {
                    // Handled in text content
                }
                is ColorSpan -> {
                    builder.setSpan(
                        ForegroundColorSpan(span.color),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is SizeSpan -> {
                    builder.setSpan(
                        RelativeSizeSpan(span.size),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is RichUnderlineSpan -> {
                    builder.setSpan(
                        UnderlineSpan(),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                is RichStrikethroughSpan -> {
                    builder.setSpan(
                        StrikethroughSpan(),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        return builder
    }

    /**
     * Convert content + spans to Compose AnnotatedString for BasicTextField editing.
     * Used by InlineRichTextEditor for visual editing with formatting.
     */
    fun toAnnotatedString(
        content: String,
        spans: List<RichTextSpan>
    ): AnnotatedString {
        Log.d(TAG, "toAnnotatedString: content='${content.take(50)}...', ${spans.size} spans")

        return buildAnnotatedString {
            append(content)

            for (span in spans) {
                val start = span.start.coerceIn(0, content.length)
                val end = span.end.coerceIn(start, content.length)
                if (start >= end) continue

                when (span) {
                    is BoldSpan -> {
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start, end
                        )
                    }
                    is ItalicSpan -> {
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start, end
                        )
                    }
                    is HeaderSpan -> {
                        val fontSize = when (span.level) {
                            1 -> HEADER_1_SIZE
                            2 -> HEADER_2_SIZE
                            else -> HEADER_3_SIZE
                        }
                        addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    fontSize,
                                    androidx.compose.ui.unit.TextUnitType.Em
                                )
                            ),
                            start, end
                        )
                    }
                    is ColorSpan -> {
                        addStyle(
                            SpanStyle(color = ComposeColor(span.color)),
                            start, end
                        )
                    }
                    is SizeSpan -> {
                        addStyle(
                            SpanStyle(
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    span.size,
                                    androidx.compose.ui.unit.TextUnitType.Em
                                )
                            ),
                            start, end
                        )
                    }
                    is RichUnderlineSpan -> {
                        addStyle(
                            SpanStyle(textDecoration = TextDecoration.Underline),
                            start, end
                        )
                    }
                    is RichStrikethroughSpan -> {
                        addStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                            start, end
                        )
                    }
                    is BulletSpan, is NumberedListSpan -> {
                        // List formatting handled in text content
                    }
                }
            }
        }
    }

    /**
     * Convert markdown text to plain content + spans.
     * Used when AI returns markdown-formatted text.
     *
     * @return Pair of (plainText, spans)
     */
    fun markdownToSpans(markdown: String): Pair<String, List<RichTextSpan>> {
        Log.d(TAG, "markdownToSpans: input length=${markdown.length}")

        val spans = mutableListOf<RichTextSpan>()
        val output = StringBuilder()
        var inputIndex = 0
        val lines = markdown.split("\n")
        var outputLineStart = 0

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Track where this line starts in output
            if (lineIndex > 0) {
                output.append("\n")
                outputLineStart = output.length
            } else {
                outputLineStart = 0
            }

            // Headers
            val headerMatch = Regex("^(#{1,3})\\s+(.*)$").find(trimmed)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val headerText = headerMatch.groupValues[2]
                val start = output.length
                output.append(processInlineMarkdown(headerText, spans, start))
                spans.add(HeaderSpan(outputLineStart, output.length, level))
                continue
            }

            // Bullet points
            val bulletMatch = Regex("^[-*]\\s+(.*)$").find(trimmed)
            if (bulletMatch != null) {
                val bulletText = bulletMatch.groupValues[1]
                output.append("• ")
                val contentStart = output.length
                output.append(processInlineMarkdown(bulletText, spans, contentStart))
                spans.add(BulletSpan(outputLineStart, output.length))
                continue
            }

            // Numbered lists
            val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
            if (numberedMatch != null) {
                val number = numberedMatch.groupValues[1].toIntOrNull() ?: 1
                val listText = numberedMatch.groupValues[2]
                output.append("$number. ")
                val contentStart = output.length
                output.append(processInlineMarkdown(listText, spans, contentStart))
                spans.add(NumberedListSpan(outputLineStart, output.length, number))
                continue
            }

            // Regular line - process inline formatting
            output.append(processInlineMarkdown(trimmed, spans, outputLineStart))
        }

        val result = output.toString()
        Log.d(TAG, "markdownToSpans: output length=${result.length}, ${spans.size} spans")
        return Pair(result, RichTextSpanSerializer.sortSpans(spans))
    }

    /**
     * Process inline markdown formatting (bold, italic) within a text segment.
     * Returns the processed text and adds spans to the provided list.
     */
    private fun processInlineMarkdown(
        text: String,
        spans: MutableList<RichTextSpan>,
        outputOffset: Int
    ): String {
        val output = StringBuilder()
        var index = 0

        while (index < text.length) {
            // Bold+Italic: ***text***
            if (text.startsWith("***", index)) {
                val endIndex = text.indexOf("***", index + 3)
                if (endIndex > index + 3) {
                    val start = outputOffset + output.length
                    val content = text.substring(index + 3, endIndex)
                    output.append(content)
                    val end = outputOffset + output.length
                    spans.add(BoldSpan(start, end))
                    spans.add(ItalicSpan(start, end))
                    index = endIndex + 3
                    continue
                }
            }

            // Bold: **text**
            if (text.startsWith("**", index)) {
                val endIndex = text.indexOf("**", index + 2)
                if (endIndex > index + 2) {
                    val start = outputOffset + output.length
                    val content = text.substring(index + 2, endIndex)
                    output.append(content)
                    val end = outputOffset + output.length
                    spans.add(BoldSpan(start, end))
                    index = endIndex + 2
                    continue
                }
            }

            // Italic: *text* or _text_
            if ((text[index] == '*' || text[index] == '_') &&
                index + 1 < text.length &&
                text[index + 1] != text[index]  // Not ** or __
            ) {
                val delimiter = text[index]
                val endIndex = text.indexOf(delimiter, index + 1)
                if (endIndex > index + 1) {
                    val start = outputOffset + output.length
                    val content = text.substring(index + 1, endIndex)
                    output.append(content)
                    val end = outputOffset + output.length
                    spans.add(ItalicSpan(start, end))
                    index = endIndex + 1
                    continue
                }
            }

            // Strikethrough: ~~text~~
            if (text.startsWith("~~", index)) {
                val endIndex = text.indexOf("~~", index + 2)
                if (endIndex > index + 2) {
                    val start = outputOffset + output.length
                    val content = text.substring(index + 2, endIndex)
                    output.append(content)
                    val end = outputOffset + output.length
                    spans.add(RichStrikethroughSpan(start, end))
                    index = endIndex + 2
                    continue
                }
            }

            // Regular character
            output.append(text[index])
            index++
        }

        return output.toString()
    }

    /**
     * Convert spans back to markdown text.
     * Used for exporting or debugging.
     */
    fun spansToMarkdown(content: String, spans: List<RichTextSpan>): String {
        // For now, return content as-is
        // Full implementation would re-insert markdown markers
        return content
    }

    /**
     * Check if content has any spans applied.
     */
    fun hasFormatting(spans: List<RichTextSpan>?): Boolean {
        return !spans.isNullOrEmpty()
    }

    /**
     * Parse spans JSON string and convert to Spannable.
     * Convenience method for use in rendering code.
     */
    fun jsonToSpanned(
        content: String,
        spansJson: String?,
        baseFontSize: Float = 16f
    ): Spannable {
        val spans = RichTextSpanSerializer.decode(spansJson)
        return toSpanned(content, spans, baseFontSize)
    }

    /**
     * Parse spans JSON string and convert to AnnotatedString.
     * Convenience method for use in Compose editing code.
     */
    fun jsonToAnnotatedString(
        content: String,
        spansJson: String?
    ): AnnotatedString {
        val spans = RichTextSpanSerializer.decode(spansJson)
        return toAnnotatedString(content, spans)
    }
}
