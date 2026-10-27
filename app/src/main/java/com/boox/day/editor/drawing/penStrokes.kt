package com.boox.day.editor.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.annotation.VisibleForTesting
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
import kotlin.math.roundToInt

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
    canvas: Canvas, paint: Paint, strokeSize: Float, points: List<TouchPoint>, dots: Boolean = false
) {
    val copyPaint = Paint(paint).apply {
        this.strokeWidth = strokeSize
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
        this.isAntiAlias = true
        this.alpha = 100

    }
    if (dots) useMarkerDots(copyPaint, paint.color)

    val path = pointsToPath(points.map { SimplePointF(it.x, it.y) })

    canvas.drawPath(path, copyPaint)
}

// 4x4 ordered-dither thresholds: a pixel is a dot when its value is below the dot count.
private val MARKER_DOT_ORDER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
private val markerDotShaders = arrayOfNulls<BitmapShader>(17)

/**
 * Marker on the canvas (Oct 2026): black dots instead of a pale grey.
 *
 * The canvas is shown in BOOX's black-and-white handwriting mode (onSurfaceInit). The old marker
 * grey (LTGRAY at alpha 100, grey 237 on the page) showed as white there, so marker ink vanished
 * as soon as BooxDay's drawing replaced the live pen layer: on a pen switch, or an erase nearby.
 * Dots show the same in every e-ink mode. There are as many as the marker colour is dark (2 to 8
 * of every 16 pixels, so ink under the marker stays readable), and overlapping passes don't darken.
 */
internal fun useMarkerDots(paint: Paint, color: Int) {
    val grey = 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)
    val dots = ((1f - grey / 255f) * 16f).roundToInt().coerceIn(2, 8)
    paint.shader = markerDotShaders[dots] ?: BitmapShader(
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            for (i in MARKER_DOT_ORDER.indices) {
                if (MARKER_DOT_ORDER[i] < dots) setPixel(i % 4, i / 4, Color.BLACK)
            }
        },
        Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
    ).also { markerDotShaders[dots] = it }
    paint.alpha = 255
    paint.isAntiAlias = false
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

// The Onyx pen library draws Brush and Pencil strokes, and Marker and Fountain with NeoTools on.
// 1.0.33 to 1.0.38 never set it up (OnyxCompat.initResManager), so the first such stroke closed the
// app and its page then closed the app every time it opened. Since Oct 2026, when the library
// fails, BooxDay draws the stroke itself.

/**
 * True once the Onyx pen library failed to load in this process (a class that could not
 * initialise, a missing native library). Its pens are then drawn by BooxDay until the app restarts:
 * a class that failed to load stays failed, and a failed call can leave a native pen behind (the
 * library frees it only after the stroke's last point).
 */
@Volatile
@VisibleForTesting
internal var onyxPenLibraryFailed = false

/** Draws a stroke with the Onyx pen library ([libraryDraw]), or with BooxDay's own drawing when that library fails. */
internal inline fun drawWithOnyxPen(
    canvas: Canvas, stroke: Stroke, offset: Offset, colorOverride: Int?, maxPressure: Float,
    libraryDraw: () -> Unit
) {
    if (!onyxPenLibraryFailed) {
        try {
            libraryDraw()
            return
        } catch (e: LinkageError) {
            onyxPenFailed(stroke.pen, e)
        } catch (e: Exception) {
            onyxPenFailed(stroke.pen, e)
        }
    }
    drawOwnStroke(canvas, stroke, offset, colorOverride, maxPressure)
}

internal fun onyxPenFailed(pen: Pen, e: Throwable) {
    if (e is LinkageError) {
        onyxPenLibraryFailed = true
        penStrokesLog.e("Onyx pen library failed to load; BooxDay draws its pens until restart ($pen)", e)
    } else {
        penStrokesLog.e("Onyx pen library failed on a $pen stroke; BooxDay drew it: $e")
    }
}

/**
 * BooxDay's own drawing for a stroke of an Onyx library pen: the Marker look for Marker, the
 * pressure-sensitive Fountain look for the others. It starts again from the saved points and a new
 * paint: the library changes the points it is given (it divides each pressure by the maximum
 * before its first pen call, which is where 1.0.38 failed).
 */
internal fun drawOwnStroke(canvas: Canvas, stroke: Stroke, offset: Offset, colorOverride: Int?, maxPressure: Float) {
    val paint = Paint().apply {
        color = colorOverride ?: stroke.color
        this.strokeWidth = stroke.size
    }
    val points = strokeToTouchPoints(if (offset == Offset.Zero) stroke else offsetStroke(stroke, offset))
    if (stroke.pen == Pen.MARKER) drawMarkerStroke(canvas, paint, stroke.size, points)
    else drawFountainPenStroke(canvas, paint, stroke.size, points, maxPressure)
}

/** [markerDots]: draw Marker strokes as black dots (useMarkerDots), for the canvas on e-ink. */
fun drawStroke(
    canvas: Canvas, stroke: Stroke, offset: Offset, colorOverride: Int? = null, markerDots: Boolean = false
) {
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
        if (stroke.pen == Pen.MARKER && !GlobalAppSettings.current.neoTools) {
            if (markerDots) useMarkerDots(paint, paint.color) else paint.alpha = 100
        }
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
            Pen.PENCIL -> drawWithOnyxPen(canvas, stroke, offset, colorOverride, maxPressure) {
                NeoCharcoalPenWrapper.drawNormalStroke(
                    PenRenderArgs().setCanvas(canvas).setPaint(paint).setPoints(points)
                        .setColor(paint.color).setStrokeWidth(stroke.size)
                        .setCreateArgs(ShapeCreateArgs().setMaxPressure(maxPressure))
                        .setScreenMatrix(Matrix()).setRenderMatrix(Matrix()).setErase(false)
                )
            }

            Pen.BRUSH -> drawWithOnyxPen(canvas, stroke, offset, colorOverride, maxPressure) {
                NeoBrushPenWrapper.drawStroke(canvas, paint, points, stroke.size, maxPressure, false)
            }
            Pen.MARKER -> {
                if (GlobalAppSettings.current.neoTools)
                    drawWithOnyxPen(canvas, stroke, offset, colorOverride, maxPressure) {
                        NeoMarkerPenWrapper.drawStroke(canvas, paint, points, stroke.size, false)
                    }
                else
                    drawMarkerStroke(canvas, paint, stroke.size, points, markerDots)
            }

            Pen.FOUNTAIN -> {
                if (GlobalAppSettings.current.neoTools)
                    drawWithOnyxPen(canvas, stroke, offset, colorOverride, maxPressure) {
                        NeoFountainPenWrapper.drawStroke(
                            canvas,
                            paint,
                            points,
                            1f,
                            stroke.size,
                            maxPressure,
                            false
                        )
                    }
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