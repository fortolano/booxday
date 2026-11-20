package com.boox.day.ocr

import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/**
 * BOOX's own handwriting reading (BooxHandwritingReader, 9 Nov 2026): the language as MyScript names
 * it, and the pen's path as the service gets it.
 */
class BooxHandwritingReaderTest {

    private fun stroke(vararg points: StrokePoint, penUpMs: Long) = Stroke(
        size = 3f, pen = Pen.BALLPEN, top = 0f, bottom = 0f, left = 0f, right = 0f,
        points = points.toList(), pageId = "p", createdAt = Date(penUpMs)
    )

    @Test
    fun `the language is named as MyScript names it`() {
        assertEquals("en_US", BooxHandwritingReader.myScriptLanguage("en-US"))
        assertEquals("uk_UA", BooxHandwritingReader.myScriptLanguage("uk"))
        assertEquals("pt_BR", BooxHandwritingReader.myScriptLanguage("pt-BR"))
        assertEquals("de_DE", BooxHandwritingReader.myScriptLanguage("de"))
    }

    @Test
    fun `the path keeps its order and timing, moved next to the corner`() {
        val line = stroke(
            StrokePoint(200f, 500f, pressure = 2048f, dt = 0u),
            StrokePoint(210f, 505f, pressure = 4096f, dt = 8u),
            StrokePoint(220f, 510f, dt = 16u),
            penUpMs = 10_016
        )
        val dot = stroke(StrokePoint(190f, 520f, dt = 0u), penUpMs = 10_500)

        val events = BooxHandwritingReader.pointerEvents(listOf(line, dot))

        assertEquals(listOf(0, 1, 2, 0, 2), events.map { it.type })  // a dot still lifts
        assertEquals(listOf(20f, 30f, 40f, 10f, 10f), events.map { it.x })  // 10 px from the left
        assertEquals(10f, events[0].y)
        assertEquals(listOf(10_000L, 10_008L, 10_016L, 10_500L, 10_500L), events.map { it.t })
        assertEquals(listOf(0.5f, 1f, 0.5f), events.take(3).map { it.pressure })
        assertEquals(emptyList<BooxHandwritingReader.PointerEvent>(), BooxHandwritingReader.pointerEvents(emptyList()))
    }

    @Test
    fun `the request starts with the language`() {
        val request = BooxHandwritingReader.request("uk_UA", 1f, 1f, emptyList())
        assertArrayEquals(byteArrayOf(0x0A, 5) + "uk_UA".toByteArray(), request.copyOf(7))
    }
}
