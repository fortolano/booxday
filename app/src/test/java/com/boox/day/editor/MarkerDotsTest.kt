package com.boox.day.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.utils.Pen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Marker on the canvas: only black and white pixels, so BOOX's black-and-white handwriting mode
 * shows it (the old grey 237 showed as white and the marker vanished; Go 6, Oct 2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkerDotsTest {
    @Before fun neoToolsOff() = GlobalAppSettings.update(AppSettings(version = 1, neoTools = false))

    private fun marker(color: Int = Color.LTGRAY, points: List<StrokePoint> = listOf(
        StrokePoint(10f, 50f, 2048f), StrokePoint(100f, 50f, 2048f), StrokePoint(190f, 50f, 2048f)
    )) = Stroke(
        size = 40f, pen = Pen.MARKER, color = color, top = 10f, bottom = 90f, left = 0f, right = 200f,
        points = points, pageId = "page"
    )

    private fun draw(vararg strokes: Stroke, dots: Boolean = true, under: (Canvas) -> Unit = {}) =
        Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            under(canvas)
            strokes.forEach { drawStroke(canvas, it, Offset.Zero, markerDots = dots) }
        }

    // Inside the band, away from its edges and round ends; 120 x 20 pixels, on the 4 x 4 dot grid.
    private fun band(bitmap: Bitmap) =
        (40 until 160).flatMap { x -> (40 until 60).map { y -> bitmap.getPixel(x, y) } }

    @Test fun canvasMarkerIsBlackDotsOnWhite() {
        val band = band(draw(marker()))
        assertTrue(band.all { it == Color.BLACK || it == Color.WHITE })
        assertEquals(3 * 2400 / 16, band.count { it == Color.BLACK })  // LTGRAY: 3 of 16
    }

    @Test fun overlappingPassesDoNotDarken() {
        assertEquals(band(draw(marker())), band(draw(marker(), marker())))
    }

    @Test fun inkUnderTheMarkerStaysBlack() {
        val ink = Paint().apply { color = Color.BLACK }
        val bitmap = draw(marker()) { it.drawRect(60f, 30f, 70f, 70f, ink) }
        for (x in 60 until 70) for (y in 40 until 60) assertEquals(Color.BLACK, bitmap.getPixel(x, y))
    }

    @Test fun aDarkMarkerCoversAtMostHalf() {
        assertEquals(8 * 2400 / 16, band(draw(marker(Color.BLACK))).count { it == Color.BLACK })
    }

    @Test fun aMarkerDotIsDotsToo() {
        val dot = draw(marker(points = listOf(StrokePoint(100f, 50f, 2048f))))
        val inside = (88 until 112).flatMap { x -> (40 until 60).map { y -> dot.getPixel(x, y) } }
        assertTrue(inside.all { it == Color.BLACK || it == Color.WHITE })
        assertTrue(inside.any { it == Color.BLACK })
    }

    @Test fun exportsKeepThePaleGrey() {
        val band = band(draw(marker(), dots = false))
        assertTrue(band.all { it != Color.BLACK && it != Color.WHITE })
    }
}
