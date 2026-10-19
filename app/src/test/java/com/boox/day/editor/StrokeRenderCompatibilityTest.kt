package com.boox.day.editor

import android.app.Application
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.decodeStrokePoints
import com.boox.day.data.db.encodeStrokePoints
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.strokeToTouchPoints
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
class StrokeRenderCompatibilityTest {
    private fun stroke(pen: Pen = Pen.FOUNTAIN, points: List<StrokePoint> = listOf(
        StrokePoint(20f,20f,512f,10,-5,0u), StrokePoint(80f,80f,1024f,20,-10,40u))) =
        Stroke(size=10f,pen=pen,maxPressure=1024,top=10f,bottom=90f,left=10f,right=90f,
            points=points,pageId="saved-page",createdAt=Date(1000),updatedAt=Date(9000))

    @Test fun sdkPressureMutationCannotChangeSavedPointsOrTheirEncodedBytes() {
        val saved=stroke()
        val original=encodeStrokePoints(saved.points)
        val render=strokeToTouchPoints(saved)
        render.forEach { it.pressure /= saved.maxPressure; it.x += 100f }
        assertArrayEquals(original,encodeStrokePoints(saved.points))
        assertEquals(listOf(512f,1024f),strokeToTouchPoints(saved).map { it.pressure })
        assertEquals(listOf(20f,80f),strokeToTouchPoints(saved).map { it.x })
    }

    @Test fun savedTimingTiltAndPressureSurviveCodecAndRedraw() {
        val original=stroke()
        val saved=original.copy(points=decodeStrokePoints(encodeStrokePoints(original.points)))
        val render=strokeToTouchPoints(saved)
        assertEquals(listOf(1000L,1040L),render.map { it.timestamp })
        assertEquals(listOf(10,20),render.map { it.tiltX })
        assertEquals(listOf(-5,-10),render.map { it.tiltY })
        assertEquals(listOf(512f,1024f),render.map { it.pressure })
    }

    private class RecordingCanvas: Canvas() {
        val widths=mutableListOf<Float>()
        val circles=mutableListOf<Float>()
        override fun drawPath(path: Path, paint: Paint) { widths.add(paint.strokeWidth) }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { circles.add(radius) }
    }

    @Test fun fountainRedrawUsesSavedPressureRangeInsteadOfCurrentDevice() {
        GlobalAppSettings.update(AppSettings(version=1,neoTools=false))
        val previous=pressure
        try {
            pressure=8192f
            val canvas=RecordingCanvas()
            drawStroke(canvas,stroke(),Offset.Zero)
            assertEquals(listOf(8f,10f),canvas.widths)
        } finally { pressure=previous }
    }

    @Test fun allEightPenChoicesRetainSinglePointDotsWithoutNativeCalls() {
        GlobalAppSettings.update(AppSettings(version=1,neoTools=false))
        for (pen in Pen.entries) {
            val canvas=RecordingCanvas()
            drawStroke(canvas,stroke(pen,listOf(StrokePoint(20f,20f,1024f))),Offset.Zero)
            assertEquals("Missing dot for $pen",1,canvas.circles.size)
            assertTrue(canvas.circles.single()>0f)
        }
    }
}
