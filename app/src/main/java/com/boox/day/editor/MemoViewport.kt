package com.boox.day.editor

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Where the Memos view may go (Oct 2026, memo canvas navigation plan): the page area is the full
 * screen page (canvas width × the tablet's full screen canvas height) plus everything on the page,
 * with a margin. The view's top-left corner (PageView.scroll, page coordinates) stays inside it, and
 * never above or left of the page corner. All in page pixels; zoom is 1.
 */
object MemoViewport {
    /**
     * The full screen page's height: [recorded] once full screen has shown the canvas on this tablet.
     * Before that, in the normal view, the view plus [fullScreenExtra] (the height full screen adds,
     * measured from the normal layout); without it the drag only reached ink until full screen was
     * opened once (Go 6, Oct 2026). 0 when neither is known.
     */
    fun sheetHeight(recorded: Int, viewHeight: Int, fullScreenExtra: Int, isFullScreen: Boolean): Int = when {
        recorded > 0 -> recorded
        isFullScreen -> viewHeight
        fullScreenExtra > 0 -> viewHeight + fullScreenExtra
        else -> 0
    }

    /** The page area: the full screen page plus [content] (everything on the page) and [margin]. */
    fun pageArea(viewWidth: Int, viewHeight: Int, sheetHeight: Int, content: RectF?, margin: Float): RectF {
        val area = RectF(0f, 0f, viewWidth.toFloat(), max(sheetHeight, viewHeight).toFloat())
        content?.let { area.union(it.left - margin, it.top - margin, it.right + margin, it.bottom + margin) }
        area.left = 0f
        area.top = 0f
        return area
    }

    /** How far the view's corner may move inside [area]: (0, 0) to (right, bottom). All zero = the page fits. */
    fun scrollLimits(area: RectF, viewWidth: Int, viewHeight: Int): RectF =
        RectF(0f, 0f, max(0f, area.right - viewWidth), max(0f, area.bottom - viewHeight))

    fun canMove(limits: RectF): Boolean = limits.right > 0f || limits.bottom > 0f

    fun clamp(scroll: Offset, limits: RectF): Offset = Offset(
        scroll.x.coerceIn(limits.left, limits.right),
        scroll.y.coerceIn(limits.top, limits.bottom)
    )
}

/**
 * The page map's geometry (Oct 2026): [area] (page coordinates) drawn in a box of at most
 * [maxWidth] × [maxHeight] map pixels, keeping its shape.
 */
class PageMapGeometry(val area: RectF, maxWidth: Float, maxHeight: Float) {
    val scale: Float = min(maxWidth / area.width(), maxHeight / area.height())
    val width: Float = area.width() * scale
    val height: Float = area.height() * scale

    fun toMap(page: Offset): Offset = Offset((page.x - area.left) * scale, (page.y - area.top) * scale)
    fun toPage(map: Offset): Offset = Offset(map.x / scale + area.left, map.y / scale + area.top)

    /** The view's frame in map pixels for a view at [scroll] of [viewWidth] × [viewHeight]. */
    fun viewFrame(scroll: Offset, viewWidth: Int, viewHeight: Int): RectF {
        val corner = toMap(scroll)
        return RectF(corner.x, corner.y, corner.x + viewWidth * scale, corner.y + viewHeight * scale)
    }

    /** The view corner that centres the view on [mapPoint], clamped to [limits]. */
    fun scrollCentredOn(mapPoint: Offset, viewWidth: Int, viewHeight: Int, limits: RectF): Offset =
        MemoViewport.clamp(toPage(mapPoint) - Offset(viewWidth / 2f, viewHeight / 2f), limits)
}
