package com.boox.day.compat

/** Vendor queries can return an invalid value without throwing on unsupported firmware. */
internal object TouchPressureRange {
    const val FALLBACK = 4095f

    fun resolve(reported: Float, fallback: Float = FALLBACK): Float {
        if (reported.isFinite() && reported > 0f) return reported
        return if (fallback.isFinite() && fallback > 0f) fallback else FALLBACK
    }
}
