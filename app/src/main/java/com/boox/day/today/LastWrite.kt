package com.boox.day.today

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When a background writer last did its job, or why its last try failed (Oct 2026, plan B2): daily
 * notes and the sleep screen work unseen, so their Preferences cards say "Last written today at 18:05"
 * or what went wrong. Kept in the writer's own device-only prefs.
 */
class LastWrite(private val prefsName: String) {

    data class Record(val okMs: Long, val failMs: Long, val failReason: String?)

    fun succeeded(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_OK, nowMs).apply()
    }

    fun failed(context: Context, reason: String, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_FAIL, nowMs).putString(KEY_REASON, reason).apply()
    }

    fun read(context: Context): Record = prefs(context).let {
        Record(it.getLong(KEY_OK, 0L), it.getLong(KEY_FAIL, 0L), it.getString(KEY_REASON, null))
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OK = "last_ok_ms"
        private const val KEY_FAIL = "last_fail_ms"
        private const val KEY_REASON = "last_fail_reason"

        /** The card's line: the newer of the last success and the last failure. */
        fun statusText(
            record: Record,
            done: String,
            use24Hour: Boolean,
            nowMs: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault()
        ): String = when {
            record.okMs == 0L && record.failMs == 0L -> "Not $done yet"
            record.failMs > record.okMs ->
                "Last try failed ${whenText(record.failMs, nowMs, zone, use24Hour)}" + (record.failReason?.let { ": $it" } ?: "")
            else -> "Last $done ${whenText(record.okMs, nowMs, zone, use24Hour)}"
        }

        /** "today at 18:05", "yesterday at 00:05", "on Mon 26 Oct at 09:00". */
        internal fun whenText(ms: Long, nowMs: Long, zone: ZoneId, use24Hour: Boolean): String {
            val time = Instant.ofEpochMilli(ms).atZone(zone)
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val clock = time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.ENGLISH))
            val day: LocalDate = time.toLocalDate()
            return when (day) {
                today -> "today at $clock"
                today.minusDays(1) -> "yesterday at $clock"
                else -> "on ${time.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))} at $clock"
            }
        }
    }
}
