package com.boox.day.editor.state

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import com.boox.day.AppDensity
import kotlin.math.abs
import kotlin.math.atan2

/*
 * Memos two-finger drag (Oct 2026, docs: memo canvas navigation plan). The rules are BOOX Notes' own
 * (decompiled knote2, ScribbleTouchDistributor): both fingers move 5 mm before the page moves, their
 * paths at most 90° apart (a pinch moves them apart), and a drag within 20° of an axis stays on it.
 */

/** How far both fingers move before the page follows (BOOX Notes: GESTURE_DETECT_START_DISTANCE_MM). */
const val PAN_START_MM = 5f

/** A drag within this angle of an axis moves along that axis only (BOOX Notes: 20°). */
const val PAN_AXIS_LOCK_DEGREES = 20.0

/** No drag starts while the pen touches the screen or this soon after it lifted (palm while writing). */
const val PAN_PEN_GUARD_MS = 500L

/** E-ink panels drop a finger for a few ms and report it again: a drag waits this long for it (Notable). */
const val PAN_RELAND_GRACE_MS = 80L

/** Which way a two-finger drag moves the page. */
enum class PanAxis { Free, Horizontal, Vertical }

/**
 * Two fingers moving together far enough to move the page: both have moved at least [startPx] from
 * where they landed, and their paths are at most 90° apart. A still finger next to a moving one (a
 * palm, a holding grip) never gets there.
 */
fun isTwoFingerPan(travels: List<Offset>, startPx: Float): Boolean {
    if (travels.size != 2) return false
    val (a, b) = travels
    if (a.getDistance() < startPx || b.getDistance() < startPx) return false
    return a.x * b.x + a.y * b.y >= 0f
}

/** The axis a drag that has moved [travel] so far is locked to. */
fun panAxis(travel: Offset): PanAxis {
    if (travel == Offset.Zero) return PanAxis.Free
    // 0° = horizontal, 90° = vertical
    val degrees = Math.toDegrees(atan2(abs(travel.y).toDouble(), abs(travel.x).toDouble()))
    return when {
        degrees <= PAN_AXIS_LOCK_DEGREES -> PanAxis.Horizontal
        degrees >= 90.0 - PAN_AXIS_LOCK_DEGREES -> PanAxis.Vertical
        else -> PanAxis.Free
    }
}

/** [delta] with the movement across the locked axis taken out. */
fun PanAxis.constrain(delta: Offset): Offset = when (this) {
    PanAxis.Free -> delta
    PanAxis.Horizontal -> Offset(delta.x, 0f)
    PanAxis.Vertical -> Offset(0f, delta.y)
}

/**
 * [PAN_START_MM] in screen pixels. AppDensity rewrites the app's own dpi and xdpi, so the panel's
 * real pixels per inch come from the system resources; an implausible value falls back to the app dpi.
 */
fun panStartPx(): Float {
    val panelDpi = Resources.getSystem().displayMetrics.xdpi
    val dpi = if (panelDpi in 100f..700f) panelDpi else AppDensity.dpi.toFloat()
    return PAN_START_MM / 25.4f * dpi
}
