package com.boox.day.compat

import android.app.Application
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33, 35, 36], application = Application::class)
class AndroidStylusInputTest {
    private class Recorded : RawInputCallback() {
        val strokes = mutableListOf<List<TouchPoint>>()
        val erasures = mutableListOf<List<TouchPoint>>()
        val order = mutableListOf<String>()
        override fun onBeginRawDrawing(cancelled: Boolean, point: TouchPoint?) { order.add("begin") }
        override fun onEndRawDrawing(cancelled: Boolean, point: TouchPoint?) { order.add("end:$cancelled") }
        override fun onRawDrawingTouchPointListReceived(points: TouchPointList) { strokes.add(points.points); order.add("commit") }
        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint?) {}
        override fun onBeginRawErasing(cancelled: Boolean, point: TouchPoint?) { order.add("erase-begin") }
        override fun onEndRawErasing(cancelled: Boolean, point: TouchPoint?) { order.add("erase-end:$cancelled") }
        override fun onRawErasingTouchPointListReceived(points: TouchPointList) { erasures.add(points.points) }
        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint?) {}
    }
    private val recorded = Recorded()
    private val previews = mutableListOf<Pair<Int, Float>>()
    private val input = AndroidStylusInput(recorded, { 4096f }) { action, _, pressure, _ ->
        previews.add(action to pressure)
        if (action == MotionEvent.ACTION_UP) recorded.order.add("preview-up")
    }
    private val bounds = Rect(10, 20, 500, 800)
    private fun event(action: Int, time: Long, x: Float = 40f, y: Float = 50f,
        pressure: Float = 0.5f, type: Int = MotionEvent.TOOL_TYPE_STYLUS, buttons: Int = 0, flags: Int = 0): MotionEvent {
        val prop = MotionEvent.PointerProperties().apply { id = 3; toolType = type }
        val coords = MotionEvent.PointerCoords().apply { this.x=x; this.y=y; this.pressure=pressure; size=1f }
        return MotionEvent.obtain(100, time, action, 1, arrayOf(prop), arrayOf(coords),
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, flags)
    }
    private fun send(event: MotionEvent): Boolean = try { input.onTouchEvent(event, bounds) } finally { event.recycle() }

    @Test fun pressureHistoryAndOneCommitSurviveAStroke() {
        assertTrue(send(event(MotionEvent.ACTION_DOWN, 100, pressure=0.25f)))
        val move = event(MotionEvent.ACTION_MOVE, 110, x=50f, pressure=0.5f)
        move.addBatch(120, 60f, 60f, 0.75f, 1f, 0)
        send(move)
        send(event(MotionEvent.ACTION_UP, 130, x=70f, pressure=1f))
        val points=recorded.strokes.single()
        assertEquals(listOf(100L,110L,120L,130L), points.map { it.timestamp })
        assertEquals(listOf(1024f,2048f,3072f,4096f), points.map { it.pressure })
        assertEquals(listOf("begin","commit","end:false","preview-up"), recorded.order)
        assertFalse(input.isCaptured)
    }

    @Test fun aSingleContactDotIsSaved() {
        send(event(MotionEvent.ACTION_DOWN,100))
        send(event(MotionEvent.ACTION_UP,100))
        assertEquals(1,recorded.strokes.single().size)
    }

    @Test fun fingerAndHoverDoNotWriteOrStealNavigation() {
        assertFalse(send(event(MotionEvent.ACTION_DOWN,100,type=MotionEvent.TOOL_TYPE_FINGER)))
        assertFalse(send(event(MotionEvent.ACTION_HOVER_MOVE,110)))
        assertTrue(recorded.strokes.isEmpty())
        assertTrue(previews.isEmpty())
    }

    @Test fun eraserTipAndSideButtonUseExistingEraseCallbacks() {
        send(event(MotionEvent.ACTION_DOWN,100,type=MotionEvent.TOOL_TYPE_ERASER))
        send(event(MotionEvent.ACTION_UP,120,type=MotionEvent.TOOL_TYPE_ERASER))
        send(event(MotionEvent.ACTION_DOWN,200,buttons=MotionEvent.BUTTON_STYLUS_PRIMARY))
        send(event(MotionEvent.ACTION_UP,220,buttons=MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertEquals(2,recorded.erasures.size)
        assertTrue(recorded.strokes.isEmpty())
    }

    @Test fun cancellationCannotSaveInkOnTheNextPage() {
        send(event(MotionEvent.ACTION_DOWN,100))
        send(event(MotionEvent.ACTION_CANCEL,120))
        send(event(MotionEvent.ACTION_UP,130))
        assertTrue(recorded.strokes.isEmpty())
        assertFalse(input.isCaptured)
        assertTrue(recorded.order.contains("end:true"))
        send(event(MotionEvent.ACTION_DOWN,200,x=80f))
        send(event(MotionEvent.ACTION_UP,210,x=90f))
        assertEquals(80f,recorded.strokes.single().first().x,0f)
    }

    @Test fun leavingCanvasNeverConnectsAcrossToolbarOrTaskControls() {
        assertFalse(send(event(MotionEvent.ACTION_DOWN,100,x=9.9f)))
        send(event(MotionEvent.ACTION_DOWN,110))
        send(event(MotionEvent.ACTION_MOVE,120,x=550f))
        send(event(MotionEvent.ACTION_MOVE,130,x=100f))
        send(event(MotionEvent.ACTION_UP,140,x=100f))
        assertEquals(1,recorded.strokes.single().size)
        assertTrue(previews.none { it.first == MotionEvent.ACTION_MOVE })
        assertFalse(input.isCaptured)
    }

    @Test fun palmAtPointerZeroDoesNotReplaceThePen() {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id=3; toolType=MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x=300f;y=400f;pressure=1f },
            MotionEvent.PointerCoords().apply { x=40f;y=50f;pressure=0.25f })
        fun mixed(action:Int,time:Long)=MotionEvent.obtain(100,time,action,2,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
        send(mixed(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),100))
        coords[1].x=60f
        send(mixed(MotionEvent.ACTION_MOVE,110))
        send(mixed(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),120))
        assertEquals(listOf(40f,60f,60f),recorded.strokes.single().map { it.x })
        assertTrue(recorded.strokes.single().all { it.pressure==1024f })
    }

    @Test fun longContactAndInvalidPressureRemainBounded() {
        send(event(MotionEvent.ACTION_DOWN,100,pressure=Float.NaN))
        send(event(MotionEvent.ACTION_MOVE,10100,pressure=2f))
        assertTrue(input.isCaptured)
        send(event(MotionEvent.ACTION_UP,10200,pressure=-1f))
        assertEquals(listOf(0f,4096f,0f),recorded.strokes.single().map { it.pressure })
    }

    @Test fun cancelledPenReleaseDiscardsTheWholeStroke() {
        send(event(MotionEvent.ACTION_DOWN,100))
        send(event(MotionEvent.ACTION_MOVE,110,x=60f))
        send(event(MotionEvent.ACTION_UP,120,flags=MotionEvent.FLAG_CANCELED))
        assertTrue(recorded.strokes.isEmpty())
        assertEquals("end:true",recorded.order.last())
        assertEquals(MotionEvent.ACTION_CANCEL,previews.last().first)
        assertFalse(input.isCaptured)
    }

    @Test fun cancellationAfterLeavingCanvasStillDiscardsInk() {
        send(event(MotionEvent.ACTION_DOWN,100))
        send(event(MotionEvent.ACTION_MOVE,110,x=600f))
        assertTrue(input.isCaptured)
        send(event(MotionEvent.ACTION_CANCEL,120,x=600f))
        assertTrue(recorded.strokes.isEmpty())
        assertFalse(input.isCaptured)
    }

    @Test fun newContactAfterLostUpCannotJoinThePreviousStroke() {
        send(event(MotionEvent.ACTION_DOWN,100))
        send(event(MotionEvent.ACTION_MOVE,110,x=60f))
        send(event(MotionEvent.ACTION_DOWN,200,x=200f))
        send(event(MotionEvent.ACTION_UP,210,x=210f))
        assertEquals(listOf(200f,210f),recorded.strokes.single().map { it.x })
        assertEquals(listOf("begin","end:true","begin","commit","end:false","preview-up"),recorded.order)
        assertEquals(1,previews.count { it.first == MotionEvent.ACTION_CANCEL })
    }

    @Test fun shiftedWindowCoordinatesAndNewBoundsAreHonored() {
        val down=event(MotionEvent.ACTION_DOWN,100,x=140f,y=250f)
        down.offsetLocation(-100f,-200f)
        send(down)
        send(event(MotionEvent.ACTION_UP,110))
        assertEquals(40f,recorded.strokes.single().first().x,0f)
        assertEquals(50f,recorded.strokes.single().first().y,0f)
        val outside=event(MotionEvent.ACTION_DOWN,200,x=40f,y=50f)
        try { assertFalse(input.onTouchEvent(outside,Rect(80,20,200,300))) } finally { outside.recycle() }
    }
}
