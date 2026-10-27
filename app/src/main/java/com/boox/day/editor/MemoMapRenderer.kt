package com.boox.day.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.isUndecodableStrokePoints
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * The page map's picture (Oct 2026, memo canvas navigation plan): the page's content drawn straight at
 * map size from the strokes already loaded. Ink is plain black lines at least 1 px wide, unsmoothed, as
 * grey would dither in the fast e-ink modes; images, attachments and text boxes are outlined boxes.
 * The last picture is kept and used again while the page hasn't changed (PageDataManager.contentVersion).
 * Call [render] off the main thread.
 */
object MemoMapRenderer {
    private data class Key(val pageId: String, val version: Int, val width: Int, val height: Int, val area: RectF)

    @Volatile private var last: Pair<Key, Bitmap>? = null

    private fun keyOf(pageId: String, map: PageMapGeometry) = Key(
        pageId, PageDataManager.contentVersion(pageId),
        ceil(map.width).toInt().coerceAtLeast(1), ceil(map.height).toInt().coerceAtLeast(1), RectF(map.area)
    )

    /** The picture for [pageId] at [map], if the page hasn't changed since it was drawn. */
    fun cached(pageId: String, map: PageMapGeometry): Bitmap? =
        last?.takeIf { it.first == keyOf(pageId, map) && !it.second.isRecycled }?.second

    fun render(pageId: String, map: PageMapGeometry): Bitmap {
        cached(pageId, map)?.let { return it }
        val key = keyOf(pageId, map)
        val bitmap = createBitmap(key.width, key.height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = false
        }
        val path = Path()
        for (stroke in PageDataManager.getStrokes(pageId)) {
            val points = stroke.points
            if (points.isEmpty() || points.isUndecodableStrokePoints()) continue
            ink.strokeWidth = max(1f, stroke.size * map.scale)
            path.rewind()
            var lastX = (points[0].x - map.area.left) * map.scale
            var lastY = (points[0].y - map.area.top) * map.scale
            path.moveTo(lastX, lastY)
            var segments = 0
            for (i in 1 until points.size) {
                val x = (points[i].x - map.area.left) * map.scale
                val y = (points[i].y - map.area.top) * map.scale
                // Points closer than a map pixel add nothing but time
                if (abs(x - lastX) < 1f && abs(y - lastY) < 1f && i != points.size - 1) continue
                path.lineTo(x, y)
                lastX = x
                lastY = y
                segments++
            }
            if (segments == 0) path.lineTo(lastX + 0.5f, lastY)  // a dot
            canvas.drawPath(path, ink)
        }
        val box = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = false
        }
        fun outline(left: Float, top: Float, right: Float, bottom: Float) = canvas.drawRect(
            (left - map.area.left) * map.scale, (top - map.area.top) * map.scale,
            (right - map.area.left) * map.scale, (bottom - map.area.top) * map.scale, box
        )
        PageDataManager.getImages(pageId).forEach {
            outline(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
        }
        PageDataManager.getAttachments(pageId).forEach {
            outline(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
        }
        PageDataManager.getTextAnnotations(pageId).forEach { outline(it.x, it.y, it.x + it.width, it.y + it.height) }
        last = key to bitmap
        return bitmap
    }
}
