package com.boox.day.editor

import android.app.Application
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.boox.day.compat.OnyxCompat
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.drawing.drawWithOnyxPen
import com.boox.day.editor.drawing.onyxPenLibraryFailed
import com.boox.day.editor.utils.Pen
import com.onyx.android.sdk.pen.utils.PenUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import com.onyx.android.sdk.base.utils.ResManager as PenLibraryResManager

/**
 * The Onyx pen library behind Brush, Pencil, and Marker/Fountain with NeoTools on (Oct 2026): 1.0.33
 * to 1.0.38 never set it up, so the first Brush stroke closed the app on a Go 6, and its page then
 * closed the app every time it opened. On the PC the library can't draw at all (its native part is
 * built for the tablet), which stands in for a library that fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OnyxPenLibraryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settingsBefore = GlobalAppSettings.current

    @Before fun setUp() {
        OnyxCompat.initResManager(app)  // as BooxDayApp does at start
        onyxPenLibraryFailed = false
    }

    @After fun tearDown() {
        onyxPenLibraryFailed = false
        GlobalAppSettings.update(settingsBefore)
    }

    // Size 10, pressures 512 and 1024 of 1024: BooxDay's Fountain look draws them 8 and 10 wide
    private fun stroke(pen: Pen) = Stroke(size = 10f, pen = pen, maxPressure = 1024,
        top = 10f, bottom = 90f, left = 10f, right = 90f,
        points = listOf(StrokePoint(20f, 20f, 512f), StrokePoint(80f, 80f, 1024f)),
        pageId = "page", createdAt = Date(1000), updatedAt = Date(9000))

    private class RecordingCanvas : Canvas() {
        val widths = mutableListOf<Float>()
        val alphas = mutableListOf<Int>()
        override fun drawPath(path: Path, paint: Paint) {
            widths.add(paint.strokeWidth)
            alphas.add(paint.alpha)
        }
    }

    @Test fun startupSetupGivesThePenLibraryTheContextItLoadsWith() {
        assertSame(app, PenLibraryResManager.getAppContext())
        // What failed on the Go 6 (ExceptionInInitializerError): PenUtils reads that context while loading
        assertNotNull(PenUtils)
    }

    @Test fun brushPencilAndNeoToolsFountainStrokesAreDrawnWhenTheLibraryFails() {
        GlobalAppSettings.update(AppSettings(version = 1, neoTools = true))
        for (pen in listOf(Pen.BRUSH, Pen.PENCIL, Pen.FOUNTAIN)) {
            onyxPenLibraryFailed = false
            val canvas = RecordingCanvas()
            drawStroke(canvas, stroke(pen), Offset.Zero)
            assertTrue("$pen: the library should fail to load on the PC", onyxPenLibraryFailed)
            assertEquals("$pen drawn by BooxDay from the saved pressures", listOf(8f, 10f), canvas.widths)
        }
    }

    @Test fun neoToolsMarkerKeepsTheMarkerLookWhenTheLibraryFailed() {
        GlobalAppSettings.update(AppSettings(version = 1, neoTools = true))
        onyxPenLibraryFailed = true
        val canvas = RecordingCanvas()
        drawStroke(canvas, stroke(Pen.MARKER), Offset.Zero)
        assertEquals(listOf(10f), canvas.widths)
        assertEquals(listOf(100), canvas.alphas)
    }

    @Test fun aWorkingLibraryDrawsTheStrokeAlone() {
        val canvas = RecordingCanvas()
        var calls = 0
        drawWithOnyxPen(canvas, stroke(Pen.BRUSH), Offset.Zero, null, 1024f) { calls++ }
        assertEquals(1, calls)
        assertTrue(canvas.widths.isEmpty())
    }

    @Test fun aStrokeTheLibraryRejectsIsDrawnByBooxDayAndTheLibraryIsTriedAgain() {
        val canvas = RecordingCanvas()
        var calls = 0
        repeat(2) {
            drawWithOnyxPen(canvas, stroke(Pen.BRUSH), Offset.Zero, null, 1024f) {
                calls++
                throw IllegalStateException("bad stroke")
            }
        }
        assertEquals(2, calls)
        assertFalse(onyxPenLibraryFailed)
        assertEquals(listOf(8f, 10f, 8f, 10f), canvas.widths)
    }

    @Test fun aLibraryThatFailedToLoadIsNotCalledAgain() {
        val canvas = RecordingCanvas()
        var calls = 0
        repeat(2) {
            drawWithOnyxPen(canvas, stroke(Pen.BRUSH), Offset.Zero, null, 1024f) {
                calls++
                // The Go 6 crash
                throw ExceptionInInitializerError(
                    UninitializedPropertyAccessException("lateinit property appContext has not been initialized"))
            }
        }
        assertEquals(1, calls)
        assertTrue(onyxPenLibraryFailed)
        assertEquals(listOf(8f, 10f, 8f, 10f), canvas.widths)
    }

    @Test fun runningOutOfMemoryIsNotHidden() {
        assertThrows(OutOfMemoryError::class.java) {
            drawWithOnyxPen(RecordingCanvas(), stroke(Pen.BRUSH), Offset.Zero, null, 1024f) { throw OutOfMemoryError() }
        }
        assertFalse(onyxPenLibraryFailed)
    }
}
