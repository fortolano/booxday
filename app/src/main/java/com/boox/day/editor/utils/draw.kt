package com.boox.day.editor.utils

import androidx.core.graphics.toRect
import com.boox.day.TAG
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.PageView
import com.onyx.android.sdk.api.device.epd.EpdController
import android.util.Log


// touchpoints are in page coordinates
fun handleDraw(
    page: PageView,
    historyBucket: MutableList<String>,
    strokeSize: Float,
    color: Int,
    pen: Pen,
    touchPoints: List<StrokePoint>
) {
    try {
        val boundingBox = calculateBoundingBox(touchPoints) { Pair(it.x, it.y) }

        //move rectangle
        boundingBox.inset(-strokeSize, -strokeSize)

        val stroke = Stroke(
            size = strokeSize,
            pen = pen,
            pageId = page.currentPageId,
            top = boundingBox.top,
            bottom = boundingBox.bottom,
            left = boundingBox.left,
            right = boundingBox.right,
            points = touchPoints,
            color = color,
            maxPressure = try { EpdController.getMaxTouchPressure().toInt() } catch (e: Exception) { 4095 }
        )
        page.addStrokes(listOf(stroke))
        // this is causing lagging and crushing, neo pens are not good
        page.drawAreaPageCoordinates(strokeBounds(stroke).toRect())
        historyBucket.add(stroke.id)
    } catch (e: Exception) {
        Log.e(TAG, "Handle Draw: An error occurred while handling the drawing: ${e.message}")
    }
}
