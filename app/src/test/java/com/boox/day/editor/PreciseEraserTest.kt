package com.boox.day.editor.utils

import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.model.SimplePointF
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import kotlin.math.*

class PreciseEraserTest {
    private fun stroke(points: List<StrokePoint>, size: Float = 2f) = Stroke(
        id = "original", size = size, pen = Pen.FOUNTAIN, color = 0xff123456.toInt(), maxPressure = 8192,
        left = points.minOf { it.x } - size, right = points.maxOf { it.x } + size,
        top = points.minOf { it.y } - size, bottom = points.maxOf { it.y } + size,
        points = points, pageId = "memo", createdAt = Date(1000), updatedAt = Date(2000)
    )
    private fun line() = stroke(listOf(
        StrokePoint(0f, 0f, 100f, -10, 0, 0u), StrokePoint(100f, 0f, 1100f, 10, 20, 100u)
    ))
    private fun cross(x: Float = 50f, radius: Float = 5f) = PreciseEraser(
        listOf(SimplePointF(x, -20f), SimplePointF(x, 20f)), radius
    )

    @Test fun sparseLineIsSplitAtActualCrossingBetweenSamples() {
        val parts = cross().cut(line())!!
        assertEquals(2, parts.size)
        assertEquals(44f, parts[0].points.last().x, 0.001f)
        assertEquals(56f, parts[1].points.first().x, 0.001f)
        assertEquals(0f, parts[0].points.first().x, 0f)
        assertEquals(100f, parts[1].points.last().x, 0f)
    }
    @Test fun fragmentsKeepStyleCalibrationAndInterpolatePressureTiltAndTime() {
        val original = line()
        val parts = cross().cut(original)!!
        for (part in parts) {
            assertEquals(original.pen, part.pen)
            assertEquals(original.size, part.size)
            assertEquals(original.color, part.color)
            assertEquals(original.maxPressure, part.maxPressure)
            assertEquals(original.pageId, part.pageId)
            assertEquals(original.createdAt, part.createdAt)
            assertNotEquals(original.id, part.id)
        }
        assertNotEquals(parts[0].id, parts[1].id)
        val edge = parts[0].points.last()
        assertEquals(540f, edge.pressure!!, 0.001f)
        assertEquals(44, edge.dt!!.toInt())
        assertEquals(-1, edge.tiltX)
        assertEquals(9, edge.tiltY)
        parts[0].points[0].y = 999f
        assertEquals(0f, original.points[0].y, 0f)
    }
    @Test fun untouchedStrokeReturnsNullAndKeepsItsIdentity() {
        assertNull(cross(200f).cut(line()))
        assertNull(PreciseEraser(emptyList(), 5f).cut(line()))
    }
    @Test fun stationaryEraserCutsLineAndDeletesOnlyTouchedDot() {
        val eraser = PreciseEraser(listOf(SimplePointF(50f, 0f)), 5f)
        assertEquals(2, eraser.cut(line())!!.size)
        assertTrue(eraser.cut(stroke(listOf(StrokePoint(50f, 0f))))!!.isEmpty())
        assertNull(eraser.cut(stroke(listOf(StrokePoint(80f, 0f)))))
    }
    @Test fun repeatedInkAndEraserPointsDoNotCreatePhantomDots() {
        val eraser = PreciseEraser(List(4) { SimplePointF(50f, 0f) }, 5f)
        assertTrue(eraser.cut(stroke(List(4) { StrokePoint(50f, 0f) }))!!.isEmpty())
        assertEquals(2, eraser.cut(line())!!.size)
    }
    @Test fun wholeStrokeCanStillBeErased() {
        val eraser = PreciseEraser(listOf(SimplePointF(-20f, 0f), SimplePointF(120f, 0f)), 5f)
        assertTrue(eraser.cut(line())!!.isEmpty())
    }
    @Test fun zigzagKeepsSeparatePiecesWithoutJoiningAcrossGaps() {
        val zigzag = stroke(listOf(StrokePoint(0f, 0f), StrokePoint(100f, 20f),
            StrokePoint(0f, 40f), StrokePoint(100f, 60f)))
        val parts = PreciseEraser(listOf(SimplePointF(50f, -20f), SimplePointF(50f, 80f)), 5f).cut(zigzag)!!
        assertEquals(4, parts.size)
        for (part in parts) assertTrue(part.points.all { it.x <= 44.001f } || part.points.all { it.x >= 55.999f })
    }
    @Test fun circleRemainsOpenAfterErasingItsRightEdge() {
        val circle = stroke((0..100).map { i ->
            val angle = PI + i * 2 * PI / 100
            StrokePoint((50 + 40 * cos(angle)).toFloat(), (50 + 40 * sin(angle)).toFloat())
        })
        val parts = PreciseEraser(listOf(SimplePointF(90f, 30f), SimplePointF(90f, 70f)), 5f).cut(circle)!!
        assertEquals(2, parts.size)
        assertTrue(parts.sumOf { it.points.size } > 70)
        assertTrue(parts.all { it.points.all { p ->
            hypot(p.x - 90f, p.y - p.y.coerceIn(30f, 70f)) >= 5.99f
        } })
    }
    @Test fun overlappingEraseSweepsDoNotFragmentUnnecessarily() {
        val path = listOf(SimplePointF(50f, -20f), SimplePointF(50f, 20f), SimplePointF(50f, -20f))
        assertEquals(2, PreciseEraser(path, 5f).cut(line())!!.size)
    }
    @Test fun twoSeparateCrossingsLeaveTheMiddlePiece() {
        val path = listOf(SimplePointF(25f, -20f), SimplePointF(25f, 20f),
            SimplePointF(75f, 20f), SimplePointF(75f, -20f))
        val parts = PreciseEraser(path, 5f).cut(line())!!
        assertEquals(3, parts.size)
        assertEquals(31f, parts[1].points.first().x, 0.001f)
        assertEquals(69f, parts[1].points.last().x, 0.001f)
    }
    @Test fun reversedInkDirectionKeepsOrderAndLegacyMissingPressure() {
        val original = stroke(listOf(StrokePoint(100f, 0f), StrokePoint(0f, 0f)))
        val parts = cross().cut(original)!!
        assertEquals(56f, parts[0].points.last().x, 0.001f)
        assertNull(parts[0].points.last().pressure)
        assertNull(parts[0].points.last().dt)
    }
    @Test fun pageOffsetAndZoomDoNotChangeScreenEraserWidth() {
        val shifted = stroke(listOf(StrokePoint(1000f, 700f), StrokePoint(1100f, 700f)), 1f)
        val parts = PreciseEraser(listOf(SimplePointF(1050f, 680f), SimplePointF(1050f, 720f)), 15f / 2f).cut(shifted)!!
        assertEquals(1042f, parts[0].points.last().x, 0.001f)
        assertEquals(1058f, parts[1].points.first().x, 0.001f)
    }
}
