package com.boox.day.sleepscreen

import kotlinx.serialization.Serializable

/**
 * What the sleep screen shows (Nov 2026). [month]: null keeps what everyone has had so far, the month
 * when there's room; once the user sets the switch it's always on or always off (owner, decision D6).
 */
@Serializable
data class SleepScreenContent(
    val events: Boolean = true,
    val tasks: Boolean = true,
    val memo: Boolean = true,
    val month: Boolean? = null
) {
    /** How many things are switched on; [withMemo] is false where the memo can't appear. */
    fun switchedOn(withMemo: Boolean): Int =
        listOf(events, tasks, memo && withMemo, month == true).count { it }
}
