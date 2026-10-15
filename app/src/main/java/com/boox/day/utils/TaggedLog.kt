package com.boox.day.utils

import android.util.Log

/**
 * A logger with a fixed tag, writing to Android's log (logcat) only. It replaced the ShipBook
 * logger (Oct 2026), so the calls read the same: `TaggedLog("Tag").d("message")`.
 */
class TaggedLog(val tag: String) {
    fun v(msg: String, throwable: Throwable? = null) { Log.v(tag, msg, throwable) }
    fun d(msg: String, throwable: Throwable? = null) { Log.d(tag, msg, throwable) }
    fun i(msg: String, throwable: Throwable? = null) { Log.i(tag, msg, throwable) }
    fun w(msg: String, throwable: Throwable? = null) { Log.w(tag, msg, throwable) }
    fun e(msg: String, throwable: Throwable? = null) { Log.e(tag, msg, throwable) }
}
