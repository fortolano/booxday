package com.boox.day.editor.utils

import android.graphics.Paint
import com.boox.day.data.db.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Cross out = done (plan T4 §13.6C, Oct 2026): is a new stroke a line drawn through a task's
 * existing content (its ink or typed text), as on paper?
 *
 * Runs after the stroke is saved (Tasks host, contentChanged) — never in the pen path.
 */
object TaskCrossOut {

    /** Bounding box of a row's earlier content, in canvas pixels. */
    data class Extent(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** Where TaskTypedTextOverlay draws a row's typed text, in canvas pixels. */
    data class TextGeometry(
        val textStartPx: Float,
        val subtaskTextStartPx: Float,
        val textSizePx: Float,
        val subtaskTextSizePx: Float,
        val maxRightPx: Float
    )

    /** Ink older than this (relative to the line) counts as "earlier content" of the row. */
    const val EARLIER_CONTENT_MS = 1_500L

    /** Only lines drawn this recently are checked (page loads and redo re-add old strokes). */
    const val FRESH_STROKE_MS = 10_000L

    /**
     * True when [line] crosses out [content]:
     * - near-horizontal: it runs across (doesn't double back), its ends are at most ~17° apart and
     *   it stays close to the straight path between them (a scribble or a letter is not a line)
     * - it sits in the middle band of the content (an underline sits at the bottom → ignored)
     * - it spans at least 60 % of the content's width
     * Content narrower than [minContentWidth] (e.g. a single letter being written) is ignored.
     */
    fun isCrossOut(line: Stroke, content: Extent, minContentWidth: Float): Boolean {
        if (line.pen == Pen.MARKER) return false  // a highlighter over a word is a highlight
        val points = line.points
        if (points.size < 2) return false
        if (content.width < minContentWidth || content.height <= 0f) return false

        val lineWidth = line.right - line.left
        if (lineWidth <= 0f) return false
        val first = points.first()
        val last = points.last()
        val dx = abs(last.x - first.x)
        val dy = abs(last.y - first.y)
        if (dx < lineWidth * 0.8f) return false   // goes back and forth
        if (dy > dx * 0.3f) return false          // steeper than ~17°

        // Close to the straight path between its ends, and not much longer than it (a zig-zag
        // scribble can end far from where it started)
        val chord = hypot(last.x - first.x, last.y - first.y)
        if (chord <= 0f) return false
        val pathLength = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        if (pathLength > chord * 1.35f) return false
        val maxDeviation = points.maxOf { p ->
            abs((last.x - first.x) * (first.y - p.y) - (first.x - p.x) * (last.y - first.y)) / chord
        }
        if (maxDeviation > content.height * 0.35f) return false

        // Middle band of the content
        val meanY = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        val relative = (meanY - content.top) / content.height
        if (relative < 0.2f || relative > 0.7f) return false

        // Spans most of it
        val overlap = min(line.right, content.right) - max(line.left, content.left)
        return overlap >= content.width * 0.6f
    }

    /** Bounding box of [strokes] (not empty). */
    fun inkExtent(strokes: List<Stroke>): Extent = Extent(
        left = strokes.minOf { it.left },
        top = strokes.minOf { it.top },
        right = strokes.maxOf { it.right },
        bottom = strokes.maxOf { it.bottom }
    )

    /** Approximate box of a row's typed text (one line, vertically centred in the row). */
    fun typedTextExtent(
        text: String,
        isSubtask: Boolean,
        rowTop: Float,
        rowBottom: Float,
        geometry: TextGeometry
    ): Extent {
        val size = if (isSubtask) geometry.subtaskTextSizePx else geometry.textSizePx
        val left = if (isSubtask) geometry.subtaskTextStartPx else geometry.textStartPx
        val width = Paint().apply { textSize = size }.measureText(text)
        val center = (rowTop + rowBottom) / 2f
        return Extent(
            left = left,
            top = center - size * 0.5f,
            right = min(left + width, geometry.maxRightPx),
            bottom = center + size * 0.5f
        )
    }
}
