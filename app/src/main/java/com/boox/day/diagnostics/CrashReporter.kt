package com.boox.day.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.compat.OnyxCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** A crash saved on this tablet by [CrashReporter], shown once at the next start ([CrashReportGate]). */
data class CrashReport(
    /** The whole report, as it is copied or sent. */
    val text: String,
    /** When the app stopped (epoch ms). */
    val crashedAtMs: Long,
)

/**
 * Keeps a report of a crash for the next start (Oct 2026, idea from CrossInk's crash screen).
 * Before this, a crash left nothing behind: StartupDiagnostics only knows the startup steps it
 * guards, and only until the process dies.
 *
 * Installed first thing in BooxDayApp.onCreate. On an uncaught exception it writes the report
 * (version, device, exception, stack, the app's own last log lines) to filesDir/crash, then hands
 * the exception to the handler that was there before (Android's "app stopped" and process exit).
 * It never throws and gives the log reader at most [LOG_TIMEOUT_MS].
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DIR = "crash"
    private const val FILE = "last-crash.txt"
    private const val LOG_LINES = 150
    private const val LOG_TIMEOUT_MS = 1_500L

    private val handling = AtomicBoolean(false)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Only the first crash of the process is written (a second one may come from this handler)
            if (handling.compareAndSet(false, true)) {
                try {
                    save(app, buildReport(thread.name, error, recentLogLines(), System.currentTimeMillis()))
                } catch (_: Throwable) {
                }
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** The crash saved before this start, or null. */
    fun pending(context: Context): CrashReport? {
        val file = reportFile(context)
        if (!file.isFile) return null
        return try {
            CrashReport(text = file.readText(), crashedAtMs = file.lastModified())
        } catch (e: Exception) {
            Log.w(TAG, "Can't read the saved crash report: ${e.message}")
            null
        }
    }

    fun clear(context: Context) {
        reportFile(context).delete()
    }

    /**
     * The report text. Plain words first, details after, like StartupFailureReport. Firmware and pen
     * backend sit near the top (Oct 2026), where the shortened GitHub issue keeps them: untested pens
     * (EMR, unknown active pens) are what reports are most needed for. Email addresses are masked
     * ([maskEmails]): "Report on GitHub" makes the report public.
     */
    internal fun buildReport(
        threadName: String,
        error: Throwable,
        logLines: List<String>,
        nowMs: Long,
        penBackend: String = OnyxCompat.penBackend
    ): String = maskEmails(
        buildString {
            appendLine("Boox Day stopped unexpectedly.")
            appendLine()
            appendLine("Exception: ${error.javaClass.name}")
            appendLine("Message: ${error.message ?: "<none>"}")
            appendLine("Thread: $threadName")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Android: ${Build.VERSION.RELEASE ?: "unknown"} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER ?: "unknown"} ${Build.MODEL ?: "unknown"}")
            appendLine("Firmware: ${Build.DISPLAY ?: "unknown"}")
            appendLine("Pen: $penBackend")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))}")
            appendLine()
            appendLine("Stack trace:")
            appendLine(stackTraceOf(error).trimEnd())
            if (logLines.isNotEmpty()) {
                appendLine()
                appendLine("Recent app log (${logLines.size} lines):")
                logLines.forEach { appendLine(it) }
            }
        }
    )

    // An email address: kept is its first letter and its domain, enough to tell accounts apart
    private val EMAIL = Regex("""([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,})""")

    /** "jane.doe@example.com" → "j…@example.com" (the log prints account emails, e.g. at sign-in). */
    internal fun maskEmails(text: String): String = EMAIL.replace(text, "$1…@$2")

    private fun save(context: Context, text: String) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(text)
        val target = File(dir, FILE)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun reportFile(context: Context) = File(File(context.filesDir, DIR), FILE)

    private fun stackTraceOf(error: Throwable): String = try {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        writer.toString()
    } catch (_: Throwable) {
        error.stackTrace.joinToString(separator = "\n")
    }

    /**
     * This process's last log lines. The app can read its own log without READ_LOGS. Runs on a
     * helper thread so a slow logcat can't hold the crash up; whatever it read by then is kept.
     */
    private fun recentLogLines(): List<String> {
        val lines = mutableListOf<String>()
        var logcat: java.lang.Process? = null
        val reader = Thread {
            try {
                logcat = ProcessBuilder(
                    "logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString(), "--pid=${Process.myPid()}"
                ).redirectErrorStream(true).start()
                logcat?.inputStream?.bufferedReader()?.useLines { seq ->
                    seq.forEach { line -> synchronized(lines) { lines += line } }
                }
            } catch (_: Throwable) {
            }
        }
        return try {
            reader.isDaemon = true
            reader.start()
            reader.join(LOG_TIMEOUT_MS)
            logcat?.destroy()
            synchronized(lines) { lines.takeLast(LOG_LINES).toList() }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
