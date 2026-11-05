package com.boox.day.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour on Kaleido 3 tablets (Nov 2026). Kaleido shows black at 300 ppi and colour at 150 ppi, muted,
 * so colour only marks areas beside the content and text stays black. Colour repeats a meaning that
 * words, icons and borders already carry: black-and-white tablets lose nothing.
 *
 * Two roles, each with its own shape, so they can't be confused: a stripe in the calendar's colour,
 * and an amber star for "needs your decision" (a calendar can be amber too).
 */
object ScreenColour {
    /** For render tests; null reads the device */
    var forced: Boolean? = null

    private val deviceHasColour: Boolean by lazy {
        runCatching { com.onyx.android.sdk.utils.DeviceInfoUtil.isColorDevice() }.getOrDefault(false)
    }

    val isColourScreen: Boolean get() = forced ?: deviceHasColour

    /** A strong amber: Kaleido mutes everything */
    val decisionAmber = Color(0xFFF2A100)

    val starTint: Color get() = if (isColourScreen) decisionAmber else EInkTheme.Colors.primary

    /** The calendar's colour for a stripe, or null when this screen shows none */
    fun calendarStripe(colorHex: String?): Color? {
        if (!isColourScreen || colorHex.isNullOrBlank()) return null
        return runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrNull()
    }
}
