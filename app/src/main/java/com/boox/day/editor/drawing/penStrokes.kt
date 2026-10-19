package com.boox.day.editor.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.model.SimplePointF
import com.boox.day.compat.TouchPressureRange
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.offsetStroke
import com.boox.day.editor.utils.pointsToPath
import com.boox.day.editor.utils.strokeToTouchPoints
import com.onyx.android.sdk.data.note.ShapeCreateArgs
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.PenRenderArgs
import com.onyx.android.sdk.pen.NeoBrushPenWrapper
import com.onyx.android.sdk.pen.NeoCharcoalPenWrapper
import com.onyx.android.sdk.pen.NeoFountainPenWrapper
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper
import com.boox.day.utils.TaggedLog
import kotlin.math.abs
import kotlin.math.cos

private val penStrokesLog = TaggedLog("PenStrokesLog")


fun drawBallPenStroke(
    canvas: Canvas, paint: Paint, strokeSize: Float, points: List<TouchPoint>
) {
    val copyPaint = Paint(paint).apply {
        this.strokeWidth = strokeSize
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND

        this.isAntiAlias = true
    }

    val path = Path()
    val prePoint = PointF(points[0].x, points[0].y)
    path.moveTo(prePoint.x, prePoint.y)

    // Every point is drawn, as the live ink drew it. (Oct 2026: a "skip strange jump" filter here
    // skipped a point >= 30 px from the last drawn one without moving on, so after one fast jump the
    // rest of the stroke vanished whenever the page was redrawn.)
    for (point in points) {
        path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
        prePoint.x = point.x
        prePoint.y = point.y
    }
    try {
        canvas.drawPath(path, copyPaint)
    } catch (e: Exception) {
        penStrokesLog.e("Exception during draw", e)
    }
}

val eraserPaint = Paint().apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = Color.BLACK
    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    isAntiAlias = false
}
private val reusablePath = Path()
fun drawEraserStroke(canvas: Canvas, points: List<StrokePoint>, strokeSize: Float) {
    eraserPaint.strokeWidth = strokeSize

    reusablePath.reset()
    if (points.isEmpty()) return

    val prePoint = PointF(points[0].x, points[0].y)
    reusablePath.moveTo(prePoint.x, prePoint.y)

    for (i in 1 until points.size) {
        val point = points[i]
        if (abs(prePoint.y - point.y) >= 30) continue
        reusablePath.quadTo(prePoint.x, prePoint.y, point.x, point.y)
        prePoint.x = point.x
        prePoint.y = point.y
    }

    try {
        canvas.drawPath(reusablePath, eraserPaint)
    } catch (e: Exception) {
        penStrokesLog.e("Exception during draw", e)
    }
}


fun drawMarkerStroke(
    canvas: Canvas, paint: Paint, strokeSize: Float, points: List<TouchPoint>
) {
    val copyPaint = Paint(paint).apply {
        this.strokeWidth = strokeSize
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
        this.isAntiAlias = true
        this.alpha = 100

    }

    val path = pointsToPath(points.map { SimplePointF(it.x, it.y) })

    canvas.drawPath(path, copyPaint)
}

fun drawFountainPenStroke(
    canvas: Canvas, paint: Paint, strokeSize: Float, points: List<TouchPoint>, maxPressure: Float
) {
    val copyPaint = Paint(paint).apply {
        this.strokeWidth = strokeSize
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
//        this.blendMode = BlendMode.OVERLAY
        this.isAntiAlias = true
    }

    val path = Path()
    val prePoint = PointF(points[0].x, points[0].y)
    path.moveTo(prePoint.x, prePoint.y)

    // Every point is drawn (Oct 2026: the same jump filter as drawBallPenStroke dropped the rest of
    // a fast stroke on redraw).
    for (point in points) {
        path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
        prePoint.x = point.x
        prePoint.y = point.y

        // Pressure-based stroke width: ranges from 60% to 100% of strokeSize
        // based on stylus pressure (0 to maxPressure)
        // Higher minimum (60%) keeps strokes closer to selected thickness
        val pressureRatio = (point.pressure / maxPressure).coerceIn(0f, 1f)
        val minWidthFactor = 0.6f
        val widthFactor = minWidthFactor + (1f - minWidthFactor) * pressureRatio
        copyPaint.strokeWidth = strokeSize * widthFactor

        canvas.drawPath(path, copyPaint)
        path.reset()
        path.moveTo(point.x, point.y)
    }
}

fun drawStroke(canvas: Canvas, stroke: Stroke, offset: Offset, colorOverride: Int? = null) {
    //canvas.save()
    //canvas.translate(offset.x.toFloat(), offset.y.toFloat())

    val paint = Paint().apply {
        color = colorOverride ?: stroke.color
        this.strokeWidth = stroke.size
    }

    // Phase 4 (Oct 2026): no copy of every point when there is nothing to shift (the usual case on
    // a page redraw; notable ecb263cc17)
    val points = strokeToTouchPoints(if (offset == Offset.Zero) stroke else offsetStroke(stroke, offset))

    if (points.isEmpty()) return
    val maxPressure = TouchPressureRange.resolve(stroke.maxPressure.toFloat())
    // SDK wrappers mutate their input. strokeToTouchPoints creates fresh objects,
    // so redraw/export can never normalize the canonical saved points in place.
    if (points.size == 1) {
        val point = points.single()
        val ratio = (point.pressure / maxPressure).coerceIn(0f, 1f)
        val width = if (stroke.pen in listOf(Pen.BRUSH, Pen.FOUNTAIN, Pen.PENCIL))
            stroke.size * (0.6f + 0.4f * ratio) else stroke.size
        if (stroke.pen == Pen.MARKER && !GlobalAppSettings.current.neoTools) paint.alpha = 100
        canvas.drawCircle(point.x, point.y, width / 2f, paint)
        return
    }

    // Preserve each saved pen type and the optional non-native marker/fountain renderers.
    try {
        when (stroke.pen) {
            Pen.BALLPEN -> drawBallPenStroke(canvas, paint, stroke.size, points)
            Pen.REDBALLPEN -> drawBallPenStroke(canvas, paint, stroke.size, points)
            Pen.GREENBALLPEN -> drawBallPenStroke(canvas, paint, stroke.size, points)
            Pen.BLUEBALLPEN -> drawBallPenStroke(canvas, paint, stroke.size, points)
            // TODO: this functions for drawing are slow and unreliable
            // replace them with something better
            Pen.PENCIL -> NeoCharcoalPenWrapper.drawNormalStroke(
                PenRenderArgs().setCanvas(canvas).setPaint(paint).setPoints(points)
                    .setColor(paint.color).setStrokeWidth(stroke.size)
                    .setCreateArgs(ShapeCreateArgs().setMaxPressure(maxPressure))
                    .setScreenMatrix(Matrix()).setRenderMatrix(Matrix()).setErase(false)
            )

            Pen.BRUSH -> NeoBrushPenWrapper.drawStroke(canvas, paint, points, stroke.size, maxPressure, false)
            Pen.MARKER -> {
                if (GlobalAppSettings.current.neoTools)
                    NeoMarkerPenWrapper.drawStroke(canvas, paint, points, stroke.size, false)
                else
                    drawMarkerStroke(canvas, paint, stroke.size, points)
            }

            Pen.FOUNTAIN -> {
                if (GlobalAppSettings.current.neoTools)
                    NeoFountainPenWrapper.drawStroke(
                        canvas,
                        paint,
                        points,
                        1f,
                        stroke.size,
                        maxPressure,
                        false
                    )
                else
                    drawFountainPenStroke(canvas, paint, stroke.size, points, maxPressure)
            }


        }
    } catch (e: Exception) {
        penStrokesLog.e("pageDrawing.kt: Drawing strokes failed: ${e.message}")
    }
    //canvas.restore()
}


val selectPaint = Paint().apply {
    strokeWidth = 5f
    style = Paint.Style.STROKE
    pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    isAntiAlias = true
    color = Color.GRAY
}