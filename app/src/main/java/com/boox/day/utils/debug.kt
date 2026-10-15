package com.boox.day.utils

import android.os.Looper
import com.boox.day.TAG
import android.util.Log


fun logCallStack(reason: String, n: Int = 8) {
    val stackTrace = Thread.currentThread().stackTrace
        .drop(3) // Skip internal calls
        .take(n) // Limit depth
        .joinToString("\n") {
            "${it.className.removePrefix("com.boox.day.")}.${it.methodName} (${it.fileName}:${it.lineNumber})"
        }
    Log.w(TAG, "$reason Call stack:\n$stackTrace")
}

fun ensureNotMainThread(taskName: String) {
    if (Looper.getMainLooper().isCurrentThread) {
        Log.w(TAG, "$taskName running on main thread – consider dispatching to IO.")
    }
}