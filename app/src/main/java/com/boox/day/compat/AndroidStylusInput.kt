package com.boox.day.compat

import android.graphics.Rect
import android.view.MotionEvent
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** One Android pen contact. Finger gestures stay in Compose; no second SF reader is opened. */
internal class AndroidStylusInput(
    private val callback: RawInputCallback,
    private val maxPressure: () -> Float,
    private val preview: (Int, TouchPoint?, Float, Boolean) -> Unit,
) {
    private var pointerId = -1
    private var erasing = false
    private var ended = false
    private var pressureMin = 0f
    private var pressureSpan = 1f
    private var targetPressure = 4095f
    private val points = ArrayList<TouchPoint>()
    val isCaptured: Boolean get() = pointerId >= 0

    fun onTouchEvent(event: MotionEvent, bounds: Rect): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN && isCaptured) cancel()
        if (!isCaptured) {
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) return false
            val index = event.actionIndex
            val type = event.getToolType(index)
            if (type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) return false
            if (!inside(bounds, event.getX(index), event.getY(index))) return false
            pointerId = event.getPointerId(index)
            erasing = type == MotionEvent.TOOL_TYPE_ERASER ||
                event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
            ended = false
            points.clear()
            targetPressure = TouchPressureRange.resolve(maxPressure())
            val range = event.device?.getMotionRange(MotionEvent.AXIS_PRESSURE, event.source)
            val calibrated = range != null && range.min.isFinite() &&
                range.range.isFinite() && range.range > 0f
            pressureMin = if (calibrated) range!!.min else 0f
            pressureSpan = if (calibrated) range!!.range else 1f
        }
        val index = event.findPointerIndex(pointerId)
        if (action == MotionEvent.ACTION_CANCEL || index < 0) {
            cancel()
            return true
        }
        // Read the captured pointer by ID, including historical samples. Never use
        // pointer zero implicitly: a palm may occupy it. No hidden MotionEvent.split API.
        val penReleased = action == MotionEvent.ACTION_UP ||
            (action == MotionEvent.ACTION_POINTER_UP && event.actionIndex == index)
        if (penReleased && event.flags and MotionEvent.FLAG_CANCELED != 0) {
            cancel()
            return true
        }
        if (!ended) {
            for (h in 0 until event.historySize) {
                if (!append(event, index, h, bounds)) { ended = true; break }
            }
            if (!ended) ended = !append(event, index, -1, bounds)
        }
        // Leaving the canvas stops preview/sampling, but keep the contact until UP:
        // Android can still cancel it, in which case none of its points are saved.
        if (penReleased) {
            finish(false)
            // Commit the canonical bitmap before releasing the live EPD ink layer.
            preview(MotionEvent.ACTION_UP, null, 0f, erasing)
            reset()
        }
        return true
    }

    private fun normalized(value: Float): Float =
        if (value.isFinite()) ((value - pressureMin) / pressureSpan).coerceIn(0f, 1f) else 0f

    private fun inside(bounds: Rect, x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x >= bounds.left && x < bounds.right &&
            y >= bounds.top && y < bounds.bottom

    private fun append(event: MotionEvent, index: Int, history: Int, bounds: Rect): Boolean {
        val x = if (history < 0) event.getX(index) else event.getHistoricalX(index, history)
        val y = if (history < 0) event.getY(index) else event.getHistoricalY(index, history)
        if (!inside(bounds, x, y)) return false
        val raw = if (history < 0) event.getPressure(index) else event.getHistoricalPressure(index, history)
        val time = if (history < 0) event.eventTime else event.getHistoricalEventTime(history)
        fun axis(axis: Int) = if (history < 0) event.getAxisValue(axis, index) else event.getHistoricalAxisValue(axis, index, history)
        val tilt = axis(MotionEvent.AXIS_TILT).takeIf { it.isFinite() } ?: 0f
        val angle = axis(MotionEvent.AXIS_ORIENTATION).takeIf { it.isFinite() } ?: 0f
        val point = TouchPoint(x, y, normalized(raw) * targetPressure, 0f,
            Math.toDegrees(atan(-sin(angle) * tan(tilt)).toDouble()).toInt(),
            Math.toDegrees(atan(cos(angle) * tan(tilt)).toDouble()).toInt(), time)
        val previous = points.lastOrNull()
        if (previous != null && previous.timestamp == time && previous.x == x && previous.y == y) return true
        points.add(point)
        if (points.size == 1) {
            if (erasing) callback.onBeginRawErasing(false, point) else callback.onBeginRawDrawing(false, point)
        }
        preview(if (points.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
            point, normalized(raw), erasing)
        return true
    }

    private fun finish(cancelled: Boolean) {
        if (points.isNotEmpty()) {
            if (!cancelled) {
                val list = TouchPointList()
                points.forEach { list.add(TouchPoint(it)) }
                if (erasing) callback.onRawErasingTouchPointListReceived(list)
                else callback.onRawDrawingTouchPointListReceived(list)
            }
            if (erasing) callback.onEndRawErasing(cancelled, points.last())
            else callback.onEndRawDrawing(cancelled, points.last())
        }
        points.clear()
    }

    fun cancel() {
        if (!isCaptured) return
        finish(true)
        preview(MotionEvent.ACTION_CANCEL, null, 0f, erasing)
        reset()
    }

    private fun reset() {
        pointerId = -1
        points.clear()
    }

    companion object {
        // The SDK provides the EPD preview; this class owns exactly one saved stroke.
        val previewCallback = object : RawInputCallback() {
            override fun onBeginRawDrawing(cancelled: Boolean, point: TouchPoint?) {}
            override fun onEndRawDrawing(cancelled: Boolean, point: TouchPoint?) {}
            override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint?) {}
            override fun onRawDrawingTouchPointListReceived(points: TouchPointList?) {}
            override fun onBeginRawErasing(cancelled: Boolean, point: TouchPoint?) {}
            override fun onEndRawErasing(cancelled: Boolean, point: TouchPoint?) {}
            override fun onRawErasingTouchPointMoveReceived(point: TouchPoint?) {}
            override fun onRawErasingTouchPointListReceived(points: TouchPointList?) {}
        }
    }
}
