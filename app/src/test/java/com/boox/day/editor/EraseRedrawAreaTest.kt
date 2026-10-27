package com.boox.day.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.toRect
import androidx.core.graphics.withClip
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.strokeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Erasing or undoing strokes redraws every pixel that drawing them painted (Go 6 test, Oct 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EraseRedrawAreaTest {
    private fun stroke(left: Float, top: Float, right: Float, bottom: Float) = Stroke(
        size = 5f, pen = Pen.BRUSH, top = top, bottom = bottom, left = left, right = right,
        points = listOf(StrokePoint(left + 5f, top + 5f)), pageId = "page"
    )

    // The Brush stroke whose column 883 and row 650 stayed on the page after it was erased.
    private val brush = stroke(530.69995f, 391.9458f, 883.1638f, 650.78027f)
    // Above the page top, where toInt() rounded toward the stroke instead of away from it.
    private val aboveTop = stroke(289.4f, -36.3f, 370.2f, 48.6f)

    @Test fun redrawAreaHoldsTheWholeDrawArea() {
        assertEquals(Rect(530, 391, 884, 651), strokeBounds(listOf(brush)))
        for (s in listOf(brush, aboveTop)) {
            val drawArea = strokeBounds(s).toRect()  // handleDraw's area
            assertTrue("$drawArea", strokeBounds(listOf(s)).contains(drawArea))
        }
        assertEquals(Rect(289, -37, 884, 651), strokeBounds(listOf(aboveTop, brush)))
    }

    @Test fun erasingLeavesNoPixelOfTheStroke() {
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // Worst case: the stroke covered its whole draw area (drawOnCanvasFromPage clips to it).
        canvas.withClip(strokeBounds(brush).toRect()) { drawColor(Color.BLACK) }
        canvas.withClip(strokeBounds(listOf(brush))) { drawColor(Color.WHITE) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertEquals(0, pixels.count { it != Color.WHITE })
    }
}
