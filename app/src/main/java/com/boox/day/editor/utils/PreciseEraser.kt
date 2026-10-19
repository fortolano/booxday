package com.boox.day.editor.utils

import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.model.SimplePointF
import java.util.Date
import java.util.UUID
import kotlin.math.*

/** Cuts vector ink at pen-up. No per-move rendering or database work happens here. */
internal class PreciseEraser(points: List<SimplePointF>, private val radius: Float) {
    private data class Sweep(val x: Double, val y: Double, val endX: Double, val endY: Double)
    private data class Interval(val start: Double, val end: Double)
    private val sweeps = if (points.size == 1) listOf(
        Sweep(points[0].x.toDouble(), points[0].y.toDouble(), points[0].x.toDouble(), points[0].y.toDouble())
    ) else points.zipWithNext { a, b -> Sweep(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble()) }

    init { require(radius.isFinite() && radius > 0) }

    /** null means untouched; an empty list means fully erased. Coordinates are page-local. */
    fun cut(stroke: Stroke): List<Stroke>? {
        if (sweeps.isEmpty() || stroke.points.isEmpty()) return null
        // Include the ink's round end cap so it does not protrude back into the erased strip.
        val r = radius.toDouble() + stroke.size / 2.0
        val nearby = sweeps.filter {
            max(it.x, it.endX) + r >= stroke.left && min(it.x, it.endX) - r <= stroke.right &&
                max(it.y, it.endY) + r >= stroke.top && min(it.y, it.endY) - r <= stroke.bottom
        }
        if (nearby.isEmpty()) return null
        val pieces = mutableListOf<List<StrokePoint>>()
        var current = mutableListOf<StrokePoint>()
        var changed = false
        fun finish() {
            if (current.isNotEmpty()) pieces.add(current)
            current = mutableListOf()
        }
        fun append(point: StrokePoint) {
            if (current.lastOrNull() != point) current.add(point.copy())
        }
        val points = stroke.points
        if (points.size == 1) {
            return if (nearby.any { intersections(points[0], points[0], it, r).isNotEmpty() }) emptyList() else null
        }
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            val hits = nearby.flatMap { intersections(a, b, it, r) }.sortedBy { it.start }
            if (hits.isEmpty()) {
                append(a)
                append(b)
                continue
            }
            changed = true
            var cursor = 0.0
            for (hit in hits) {
                if (hit.start > cursor + EPSILON) {
                    append(interpolate(a, b, cursor))
                    append(interpolate(a, b, hit.start))
                }
                finish()
                cursor = max(cursor, hit.end)
            }
            if (cursor < 1.0 - EPSILON) {
                append(interpolate(a, b, cursor))
                append(b)
            }
        }
        finish()
        if (!changed) return null
        return pieces.map { fragment ->
            stroke.copy(
                id = UUID.randomUUID().toString(), points = fragment,
                left = fragment.minOf { it.x } - stroke.size,
                right = fragment.maxOf { it.x } + stroke.size,
                top = fragment.minOf { it.y } - stroke.size,
                bottom = fragment.maxOf { it.y } + stroke.size,
                updatedAt = Date()
            )
        }
    }

    // Exact segment/capsule intersections catch fast gestures even between sparse samples.
    private fun intersections(a: StrokePoint, b: StrokePoint, sweep: Sweep, r: Double): List<Interval> {
        if (max(a.x, b.x) < min(sweep.x, sweep.endX) - r ||
            min(a.x, b.x) > max(sweep.x, sweep.endX) + r ||
            max(a.y, b.y) < min(sweep.y, sweep.endY) - r ||
            min(a.y, b.y) > max(sweep.y, sweep.endY) + r) return emptyList()
        val hits = mutableListOf<Interval>()
        val dx = b.x.toDouble() - a.x
        val dy = b.y.toDouble() - a.y
        val lengthSquared = dx * dx + dy * dy
        fun circle(x: Double, y: Double) {
            val ox = a.x - x
            val oy = a.y - y
            val c = ox * ox + oy * oy - r * r
            if (lengthSquared < EPSILON) {
                if (c <= 0) hits.add(Interval(0.0, 1.0))
                return
            }
            val projection = ox * dx + oy * dy
            val discriminant = projection * projection - lengthSquared * c
            if (discriminant <= 0) return // Tangency removes no length of ink.
            val root = sqrt(discriminant)
            val start = max(0.0, (-projection - root) / lengthSquared)
            val end = min(1.0, (-projection + root) / lengthSquared)
            if (end > start + EPSILON) hits.add(Interval(start, end))
        }
        circle(sweep.x, sweep.y)
        circle(sweep.endX, sweep.endY)
        val sx = sweep.endX - sweep.x
        val sy = sweep.endY - sweep.y
        val length = hypot(sx, sy)
        if (length > EPSILON) {
            val ux = sx / length
            val uy = sy / length
            val ox = a.x - sweep.x
            val oy = a.y - sweep.y
            var start = 0.0
            var end = 1.0
            fun slab(origin: Double, direction: Double, low: Double, high: Double): Boolean {
                if (abs(direction) < EPSILON) return origin in low..high
                val t1 = (low - origin) / direction
                val t2 = (high - origin) / direction
                start = max(start, min(t1, t2))
                end = min(end, max(t1, t2))
                return end > start + EPSILON
            }
            if (slab(ox * ux + oy * uy, dx * ux + dy * uy, 0.0, length) &&
                slab(-ox * uy + oy * ux, -dx * uy + dy * ux, -r, r)) {
                hits.add(Interval(start, end))
            }
        }
        return hits
    }

    private fun interpolate(a: StrokePoint, b: StrokePoint, t: Double): StrokePoint {
        if (t <= EPSILON) return a
        if (t >= 1.0 - EPSILON) return b
        fun blend(x: Float, y: Float) = (x + (y - x) * t).toFloat()
        fun optional(x: Int?, y: Int?): Int? = if (x != null && y != null) {
            (x + (y - x) * t).roundToInt()
        } else if (t < 0.5) x else y
        return a.copy(
            x = blend(a.x, b.x), y = blend(a.y, b.y),
            pressure = if (a.pressure != null && b.pressure != null) blend(a.pressure, b.pressure)
                else if (t < 0.5) a.pressure else b.pressure,
            tiltX = optional(a.tiltX, b.tiltX), tiltY = optional(a.tiltY, b.tiltY),
            dt = optional(a.dt?.toInt(), b.dt?.toInt())?.toUShort()
        )
    }

    private companion object { const val EPSILON = 1e-9 }
}
