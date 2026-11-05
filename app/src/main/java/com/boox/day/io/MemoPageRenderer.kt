package com.boox.day.io

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.ai.MemoPageContent
import com.boox.day.editor.drawing.drawAttachment
import com.boox.day.editor.drawing.drawImage
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.drawing.drawTextAnnotation
import kotlin.math.ceil
import kotlin.math.max

/**
 * A memo page drawn the way the canvas draws it (Nov 2026): images, file chips, ink, then text boxes.
 * One place for it, so nothing can be left out of one output again: exports used to draw ink and
 * images only, so a meeting's title and every typed text box were missing from them.
 *
 * Page pixels. The caller owns scale and background.
 */
object MemoPageRenderer {

    /** Room kept past content that leaves the screen. */
    private const val EDGE = 50f

    /** The part of the page an output shows. [left] and [top] are 0 unless content lies above or left of the page. */
    data class Frame(val left: Float, val top: Float, val width: Int, val height: Int) {
        val corner: Offset get() = Offset(left, top)
    }

    /** True when anything would be drawn. */
    fun hasContent(page: MemoPageContent.Page): Boolean = !page.isEmpty || page.attachments.isNotEmpty()

    /** Draws everything on [page], with [corner] (page pixels) at the canvas's top left. Off the main thread. */
    fun drawMemoPage(context: Context, canvas: Canvas, page: MemoPageContent.Page, corner: Offset) {
        page.images.forEach { drawImage(context, canvas, it, -corner) }
        page.attachments.forEach { drawAttachment(canvas, it, -corner, context) }
        page.strokes.forEach { drawStroke(canvas, it, -corner) }
        page.textAnnotations.forEach { drawTextAnnotation(canvas, it, corner) }  // takes the view's corner
    }

    /** What [page] covers, text boxes and file chips included; null when it holds nothing. */
    fun contentBounds(page: MemoPageContent.Page): RectF? {
        var bounds: RectF? = null
        fun add(left: Float, top: Float, right: Float, bottom: Float) {
            bounds?.union(left, top, right, bottom) ?: run { bounds = RectF(left, top, right, bottom) }
        }
        page.strokes.forEach { add(it.left, it.top, it.right, it.bottom) }
        page.images.forEach { add(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat()) }
        page.attachments.forEach { add(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat()) }
        page.textAnnotations.forEach { add(it.x, it.y, it.x + it.width, it.y + it.height) }
        return bounds
    }

    /**
     * The screen-sized page, grown to take in content past its edges: ink written on a wider tablet
     * (a backup restored on a Go 6), above or left of the corner, or below the screen.
     */
    fun frame(page: MemoPageContent.Page): Frame {
        val bounds = contentBounds(page) ?: return Frame(0f, 0f, SCREEN_WIDTH, SCREEN_HEIGHT)
        val left = if (bounds.left < 0f) bounds.left - EDGE else 0f
        val top = if (bounds.top < 0f) bounds.top - EDGE else 0f
        val right = if (bounds.right > SCREEN_WIDTH) bounds.right + EDGE else SCREEN_WIDTH.toFloat()
        val bottom = max(bounds.bottom + EDGE, SCREEN_HEIGHT.toFloat())
        return Frame(left, top, ceil(right - left).toInt(), ceil(bottom - top).toInt())
    }
}
