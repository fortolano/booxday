package com.boox.day.editor

import android.app.Application
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where the Memos view may go, and the page map's geometry (Oct 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoViewportTest {
    // Go 6 sizes: normal view 1072 × 700, full screen 1072 × 1300
    private val w = 1072
    private val h = 700
    private val sheet = 1300

    @Test fun `an empty page that fits the view can't move`() {
        val area = MemoViewport.pageArea(w, h, h, content = null, margin = 90f)
        assertFalse(MemoViewport.canMove(MemoViewport.scrollLimits(area, w, h)))
    }

    @Test fun `the normal view can move down to the bottom of the full screen page`() {
        val area = MemoViewport.pageArea(w, h, sheet, content = null, margin = 90f)
        val limits = MemoViewport.scrollLimits(area, w, h)
        assertEquals(0f, limits.right)
        assertEquals((sheet - h).toFloat(), limits.bottom)
    }

    @Test fun `full screen can't move when everything is on it`() {
        val area = MemoViewport.pageArea(w, sheet, sheet, RectF(40f, 60f, 900f, 1200f), margin = 90f)
        assertFalse(MemoViewport.canMove(MemoViewport.scrollLimits(area, w, sheet)))
    }

    @Test fun `ink below the full screen page can be reached, with the margin`() {
        val area = MemoViewport.pageArea(w, h, sheet, RectF(40f, 60f, 900f, 1500f), margin = 90f)
        assertEquals(1590f - h, MemoViewport.scrollLimits(area, w, h).bottom)
    }

    @Test fun `ink from a wider tablet gives room to move sideways`() {
        val area = MemoViewport.pageArea(w, h, sheet, RectF(40f, 60f, 1800f, 600f), margin = 90f)
        assertEquals(1890f - w, MemoViewport.scrollLimits(area, w, h).right)
    }

    @Test fun `the view never goes above or left of the page corner`() {
        val area = MemoViewport.pageArea(w, h, sheet, RectF(-300f, -200f, 500f, 500f), margin = 90f)
        val limits = MemoViewport.scrollLimits(area, w, h)
        assertEquals(Offset(0f, 0f), MemoViewport.clamp(Offset(-50f, -80f), limits))
    }

    @Test fun `clamp keeps the view inside after ink was deleted`() {
        val limits = MemoViewport.scrollLimits(MemoViewport.pageArea(w, h, h, null, 90f), w, h)
        assertEquals(Offset.Zero, MemoViewport.clamp(Offset(0f, 450f), limits))
    }

    @Test fun `page and map points convert both ways`() {
        val map = PageMapGeometry(RectF(0f, 0f, 1072f, 1300f), maxWidth = 214f, maxHeight = 400f)
        val page = Offset(500f, 900f)
        val back = map.toPage(map.toMap(page))
        assertEquals(page.x, back.x, 0.01f)
        assertEquals(page.y, back.y, 0.01f)
        assertTrue(map.width <= 214.01f && map.height <= 400.01f)
    }

    @Test fun `the map keeps the page's shape`() {
        val tall = PageMapGeometry(RectF(0f, 0f, 1000f, 4000f), maxWidth = 200f, maxHeight = 400f)
        assertEquals(100f, tall.width, 0.01f)
        assertEquals(400f, tall.height, 0.01f)
    }

    @Test fun `the frame shows the view at its place on the map`() {
        val map = PageMapGeometry(RectF(0f, 0f, 1000f, 2000f), maxWidth = 200f, maxHeight = 400f)
        val frame = map.viewFrame(Offset(0f, 1000f), viewWidth = 1000, viewHeight = 700)
        assertEquals(200f, frame.top, 0.01f)
        assertEquals(340f, frame.bottom, 0.01f)
        assertEquals(200f, frame.width(), 0.01f)
    }

    @Test fun `a tap on the map centres the view there, inside the limits`() {
        val area = RectF(0f, 0f, 1072f, 2000f)
        val map = PageMapGeometry(area, maxWidth = 214f, maxHeight = 400f)
        val limits = MemoViewport.scrollLimits(area, w, h)
        val middle = map.scrollCentredOn(map.toMap(Offset(536f, 1000f)), w, h, limits)
        assertEquals(1000f - h / 2f, middle.y, 0.5f)
        val bottom = map.scrollCentredOn(map.toMap(Offset(536f, 1990f)), w, h, limits)
        assertEquals(limits.bottom, bottom.y, 0.01f)
        val top = map.scrollCentredOn(Offset(0f, 0f), w, h, limits)
        assertEquals(Offset.Zero, top)
    }
}
