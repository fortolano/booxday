package com.boox.day.editor

import android.content.Context

/**
 * Phase 0 spike only (Oct 2026, memo canvas navigation plan): the two choices to compare on the
 * tablets without a new build, switched from the toolbar menu (≡) of debug builds. Device-only
 * preferences, never AppSettings.
 */
object PanSpikeSettings {
    private const val PREFS = "pan_spike"
    private const val KEY_QUALITY = "quality_mode"
    private const val KEY_GAP = "frame_gap_ms"
    private val GAPS = listOf(100L, 200L, 50L)

    @Volatile private var loaded = false

    /** Fast e-ink mode while the view moves: ANIMATION_QUALITY (true) or ANIMATION_X (false). */
    @Volatile var qualityMode = false
        private set

    /** Least time between two frames of a two-finger drag. */
    @Volatile var frameGapMs = GAPS.first()
        private set

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        qualityMode = prefs.getBoolean(KEY_QUALITY, false)
        frameGapMs = prefs.getLong(KEY_GAP, GAPS.first())
        loaded = true
    }

    fun modeName(): String = if (qualityMode) "Quality" else "Fast (X)"

    fun toggleMode(context: Context) {
        load(context)
        qualityMode = !qualityMode
        save(context)
    }

    fun nextFrameGap(context: Context) {
        load(context)
        frameGapMs = GAPS[(GAPS.indexOf(frameGapMs) + 1).mod(GAPS.size)]
        save(context)
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_QUALITY, qualityMode)
            .putLong(KEY_GAP, frameGapMs)
            .apply()
    }
}
