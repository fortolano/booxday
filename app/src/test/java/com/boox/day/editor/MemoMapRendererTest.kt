package com.boox.day.editor

import android.app.Application
import android.graphics.Color
import android.graphics.RectF
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The page map's picture shows the page's ink where it is, black and visible, and is redrawn only after a change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoMapRendererTest {
    private val pageId = "map-test-page"
    // A 1000 × 2000 page in a map of at most 200 × 400: scale 0.2
    private val map = PageMapGeometry(RectF(0f, 0f, 1000f, 2000f), maxWidth = 200f, maxHeight = 400f)

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, size: Float) = Stroke(
        size = size, pen = Pen.BALLPEN, top = minOf(y1, y2), bottom = maxOf(y1, y2), left = minOf(x1, x2), right = maxOf(x1, x2),
        points = (0..20).map { StrokePoint(x1 + (x2 - x1) * it / 20f, y1 + (y2 - y1) * it / 20f) }, pageId = pageId
    )

    private fun isBlack(color: Int) = Color.red(color) < 80 && Color.green(color) < 80 && Color.blue(color) < 80

    @Test fun `ink lands at its place on the map, in black`() {
        PageDataManager.setStrokes(pageId, listOf(line(400f, 1500f, 600f, 1500f, 5f)))
        PageDataManager.invalidatePreviewCache(pageId)
        val picture = MemoMapRenderer.render(pageId, map)
        assertEquals(200, picture.width)
        assertEquals(400, picture.height)
        // (500, 1500) on the page is (100, 300) on the map
        assertTrue(isBlack(picture.getPixel(100, 300)))
        // Far from the ink: white
        assertEquals(Color.WHITE, picture.getPixel(100, 100))
    }

    @Test fun `a thin stroke still leaves a visible line`() {
        PageDataManager.setStrokes(pageId, listOf(line(100f, 500f, 900f, 500f, 1f)))
        PageDataManager.invalidatePreviewCache(pageId)
        val picture = MemoMapRenderer.render(pageId, map)
        val row = 100  // y 500 × 0.2
        val blackInRow = (0 until picture.width).count { x -> (row - 1..row + 1).any { isBlack(picture.getPixel(x, it)) } }
        assertTrue("black pixels in the line's row: $blackInRow", blackInRow >= 150)
    }

    @Test fun `the picture is reused until the page changes`() {
        PageDataManager.setStrokes(pageId, listOf(line(100f, 100f, 200f, 200f, 5f)))
        PageDataManager.invalidatePreviewCache(pageId)
        val first = MemoMapRenderer.render(pageId, map)
        assertSame(first, MemoMapRenderer.render(pageId, map))
        PageDataManager.setStrokes(pageId, listOf(line(100f, 100f, 900f, 1900f, 5f)))
        PageDataManager.invalidatePreviewCache(pageId)
        assertNotSame(first, MemoMapRenderer.render(pageId, map))
    }

    @Test fun `an empty page is a white picture`() {
        PageDataManager.setStrokes(pageId, emptyList())
        PageDataManager.invalidatePreviewCache(pageId)
        val picture = MemoMapRenderer.render(pageId, map)
        assertTrue((0 until picture.width step 10).all { x -> (0 until picture.height step 10).all { picture.getPixel(x, it) == Color.WHITE } })
    }
}
