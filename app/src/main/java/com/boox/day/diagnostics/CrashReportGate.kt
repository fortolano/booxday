package com.boox.day.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.boox.day.ProjectInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * The "Boox Day closed unexpectedly" popup's state (Oct 2026), like AppUpdateGate: MainActivity
 * checks for a saved [CrashReport] at start; MainScreen shows ui/dialogs/CrashReportPopup while
 * [report] is set and keeps raw drawing off meanwhile.
 */
object CrashReportGate {
    /** Characters of the report put in the GitHub link (its whole URL must stay under ~8 KB). */
    private const val GITHUB_BODY_CHARS = 4_000

    var report by mutableStateOf<CrashReport?>(null)
        private set

    val isOpen: Boolean get() = report != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var checked = false

    /** MainActivity.onCreate: once per process. */
    fun onAppStart(context: Context) {
        if (checked) return
        checked = true
        val app = context.applicationContext
        scope.launch {
            report = withContext(Dispatchers.IO) { CrashReporter.pending(app) }
        }
    }

    /** Close: the report has been seen; it is deleted. */
    fun close(context: Context) {
        CrashReporter.clear(context.applicationContext)
        report = null
    }

    fun copy(context: Context) {
        val text = report?.text ?: return
        copyToClipboard(context, text)
        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
    }

    /** Copies the whole report, then opens a new GitHub issue holding its start. */
    fun reportOnGitHub(context: Context) {
        val current = report ?: return
        copyToClipboard(context, current.text)
        val url = "${ProjectInfo.ISSUES_URL}/new?" +
            "title=${URLEncoder.encode(issueTitle(current.text), "UTF-8")}" +
            "&body=${URLEncoder.encode(issueBody(current.text), "UTF-8")}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            close(context)
        } catch (_: Exception) {
            Toast.makeText(context, "No browser found. The report is copied.", Toast.LENGTH_LONG).show()
        }
    }

    internal fun issueTitle(reportText: String): String {
        val exception = reportText.lineSequence().firstOrNull { it.startsWith("Exception: ") }
            ?.removePrefix("Exception: ")?.substringAfterLast('.') ?: "crash"
        return "Crash: $exception"
    }

    internal fun issueBody(reportText: String): String = buildString {
        appendLine("### What were you doing when it closed?")
        appendLine()
        appendLine("_Please describe it here._")
        appendLine()
        appendLine("### Crash report")
        appendLine()
        appendLine("```")
        if (reportText.length > GITHUB_BODY_CHARS) {
            appendLine(reportText.take(GITHUB_BODY_CHARS).trimEnd())
            appendLine("…")
            appendLine("```")
            appendLine()
            append("_The report was cut to fit the link. The full report is on your clipboard: paste it here._")
        } else {
            appendLine(reportText.trimEnd())
            append("```")
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Boox Day crash report", text))
    }
}
