package com.boox.day.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SB1 stroke codec round trip (Phase 3, Oct 2026): guards the LZ4 library swap — strokes written
 * before it must decode the same after it.
 */
class StrokePointCodecTest {

    private fun points(count: Int) = (0 until count).map { i ->
        StrokePoint(
            x = 100f + i * 0.5f,
            y = 200f + (i % 7) * 0.25f,
            pressure = 2048f,
            tiltX = 10,
            tiltY = -5
        )
    }

    @Test
    fun longStroke_roundTrips() {
        // Long and regular: large enough to be LZ4-compressed
        val original = points(2_000)
        val decoded = decodeStrokePoints(encodeStrokePoints(original))
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.05f)
            assertEquals(a.y, b.y, 0.05f)
            assertEquals(a.pressure!!, b.pressure!!, 1f)
            assertEquals(a.tiltX, b.tiltX)
            assertEquals(a.tiltY, b.tiltY)
        }
    }

    @Test
    fun pointTimings_roundTrip() {
        // Phase 4: new strokes carry per-point times (ms since the first point)
        val original = points(500).mapIndexed { i, p -> p.copy(dt = (i * 7).coerceAtMost(65_535).toUShort()) }
        val decoded = decodeStrokePoints(encodeStrokePoints(original))
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (a, b) -> assertEquals(a.dt, b.dt) }
    }

    @Test
    fun shortStroke_roundTrips() {
        val original = points(3)
        val decoded = decodeStrokePoints(encodeStrokePoints(original))
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.05f)
            assertEquals(a.y, b.y, 0.05f)
        }
    }
}
