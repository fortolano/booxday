package com.boox.day.week

/**
 * How the Week page adapts to a screen (Nov 2026, owner's decision D7): the same two columns of four
 * cells everywhere; only the density changes. Pure, so every size can be tested without a screen.
 */
data class WeekLayoutSpec(
    /** Lines under a day's title. */
    val linesPerDay: Int,
    /** Short header, short times, the repeat mark as an icon. */
    val compact: Boolean,
    val headerDp: Int,
    val titleDp: Int,
    val lineDp: Int,
    val timeColumnDp: Int
) {
    companion object {
        private const val COMPACT_BELOW_WIDTH_DP = 640
        private const val CELLS_DOWN = 4
        private const val CELL_PADDING_DP = 8

        fun of(widthDp: Int, heightDp: Int): WeekLayoutSpec {
            val compact = widthDp < COMPACT_BELOW_WIDTH_DP
            val header = if (compact) 48 else 56
            val title = if (compact) 28 else 30
            val line = if (compact) 24 else 25
            val cell = (heightDp - header) / CELLS_DOWN
            return WeekLayoutSpec(
                linesPerDay = (cell - title - CELL_PADDING_DP) / line,
                compact = compact,
                headerDp = header,
                titleDp = title,
                lineDp = line,
                timeColumnDp = if (compact) 54 else 76
            )
        }

        /** "9:00 AM", or "9a" and "1:30p" on a compact page; "13:00" with the 24-hour setting. */
        fun timeText(minuteOfDay: Int, use24Hour: Boolean, compact: Boolean): String {
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            if (use24Hour) return "%d:%02d".format(hour, minute)
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            val morning = hour < 12
            return when {
                !compact -> "%d:%02d %s".format(hour12, minute, if (morning) "AM" else "PM")
                minute == 0 -> "$hour12${if (morning) "a" else "p"}"
                else -> "%d:%02d%s".format(hour12, minute, if (morning) "a" else "p")
            }
        }
    }
}
