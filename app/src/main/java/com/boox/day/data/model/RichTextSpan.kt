package com.boox.day.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sealed class hierarchy for rich text formatting spans.
 * Each span defines a range (start, end) and formatting properties.
 * Stored as JSON in TextAnnotation.spans field.
 */
@Serializable
sealed class RichTextSpan {
    abstract val start: Int
    abstract val end: Int
}

@Serializable
@SerialName("bold")
data class BoldSpan(
    override val start: Int,
    override val end: Int
) : RichTextSpan()

@Serializable
@SerialName("italic")
data class ItalicSpan(
    override val start: Int,
    override val end: Int
) : RichTextSpan()

@Serializable
@SerialName("header")
data class HeaderSpan(
    override val start: Int,
    override val end: Int,
    val level: Int  // 1, 2, or 3
) : RichTextSpan()

@Serializable
@SerialName("bullet")
data class BulletSpan(
    override val start: Int,
    override val end: Int
) : RichTextSpan()

@Serializable
@SerialName("numbered")
data class NumberedListSpan(
    override val start: Int,
    override val end: Int,
    val number: Int
) : RichTextSpan()

@Serializable
@SerialName("color")
data class ColorSpan(
    override val start: Int,
    override val end: Int,
    val color: Int  // ARGB color value
) : RichTextSpan()

@Serializable
@SerialName("size")
data class SizeSpan(
    override val start: Int,
    override val end: Int,
    val size: Float  // Font size multiplier (e.g., 1.5 for 150%)
) : RichTextSpan()

@Serializable
@SerialName("underline")
data class UnderlineSpan(
    override val start: Int,
    override val end: Int
) : RichTextSpan()

@Serializable
@SerialName("strikethrough")
data class StrikethroughSpan(
    override val start: Int,
    override val end: Int
) : RichTextSpan()

/**
 * Serializer for encoding/decoding spans to/from JSON.
 */
object RichTextSpanSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * Encode a list of spans to JSON string.
     */
    fun encode(spans: List<RichTextSpan>): String {
        return json.encodeToString(spans)
    }

    /**
     * Decode JSON string to list of spans.
     * Returns empty list if JSON is null, empty, or invalid.
     */
    fun decode(jsonString: String?): List<RichTextSpan> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<RichTextSpan>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if two spans overlap.
     */
    fun overlaps(span1: RichTextSpan, span2: RichTextSpan): Boolean {
        return span1.start < span2.end && span2.start < span1.end
    }

    /**
     * Sort spans by start position, then by end position.
     */
    fun sortSpans(spans: List<RichTextSpan>): List<RichTextSpan> {
        return spans.sortedWith(compareBy({ it.start }, { it.end }))
    }

    /**
     * Adjust span positions after text insertion.
     * @param spans The current spans
     * @param insertPosition Where text was inserted
     * @param insertLength How many characters were inserted
     * @return Adjusted spans
     */
    fun adjustForInsertion(
        spans: List<RichTextSpan>,
        insertPosition: Int,
        insertLength: Int
    ): List<RichTextSpan> {
        return spans.map { span ->
            when {
                // Span is entirely before insertion - no change
                span.end <= insertPosition -> span
                // Span starts at or after insertion - shift both positions
                span.start >= insertPosition -> span.shiftBy(insertLength)
                // Insertion is inside span - extend end only
                else -> span.extendEndBy(insertLength)
            }
        }
    }

    /**
     * Adjust span positions after text deletion.
     * @param spans The current spans
     * @param deleteStart Start of deleted range
     * @param deleteEnd End of deleted range
     * @return Adjusted spans (some may be removed if fully deleted)
     */
    fun adjustForDeletion(
        spans: List<RichTextSpan>,
        deleteStart: Int,
        deleteEnd: Int
    ): List<RichTextSpan> {
        val deleteLength = deleteEnd - deleteStart
        return spans.mapNotNull { span ->
            when {
                // Span is entirely before deletion - no change
                span.end <= deleteStart -> span
                // Span is entirely after deletion - shift back
                span.start >= deleteEnd -> span.shiftBy(-deleteLength)
                // Span is entirely within deletion - remove it
                span.start >= deleteStart && span.end <= deleteEnd -> null
                // Deletion is entirely within span - shrink span
                deleteStart >= span.start && deleteEnd <= span.end ->
                    span.extendEndBy(-deleteLength)
                // Partial overlap at start of span
                span.start < deleteEnd && span.start >= deleteStart ->
                    span.adjustRange(deleteStart, span.end - deleteLength)
                // Partial overlap at end of span
                span.end > deleteStart && span.end <= deleteEnd ->
                    span.adjustRange(span.start, deleteStart)
                else -> span
            }
        }.filter { it.start < it.end }  // Remove zero-length spans
    }

    /**
     * Create a new span of the same type with shifted positions.
     */
    private fun RichTextSpan.shiftBy(delta: Int): RichTextSpan {
        return adjustRange(start + delta, end + delta)
    }

    /**
     * Create a new span of the same type with extended end.
     */
    private fun RichTextSpan.extendEndBy(delta: Int): RichTextSpan {
        return adjustRange(start, end + delta)
    }

    /**
     * Create a new span of the same type with new range.
     */
    private fun RichTextSpan.adjustRange(newStart: Int, newEnd: Int): RichTextSpan {
        return when (this) {
            is BoldSpan -> copy(start = newStart, end = newEnd)
            is ItalicSpan -> copy(start = newStart, end = newEnd)
            is HeaderSpan -> copy(start = newStart, end = newEnd)
            is BulletSpan -> copy(start = newStart, end = newEnd)
            is NumberedListSpan -> copy(start = newStart, end = newEnd)
            is ColorSpan -> copy(start = newStart, end = newEnd)
            is SizeSpan -> copy(start = newStart, end = newEnd)
            is UnderlineSpan -> copy(start = newStart, end = newEnd)
            is StrikethroughSpan -> copy(start = newStart, end = newEnd)
        }
    }
}
