package com.boox.day.api.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.BuildConfig
import com.boox.day.api.AiPickStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last time an AI feature told the user it failed, kept on this tablet (2 Nov 2026, owner's
 * decision): what the dialog said, and each try behind it (which step, which model, why). Convert to
 * Tasks failed on a tablet away from the PC and left nothing to read: the system log drops BooxDay's
 * lines within minutes, and every AI request had answered (the failure was "no tasks").
 *
 * Never a prompt, an answer or the user's text: steps are answer formats, reasons are BooxDay's own
 * words. Kept in a small preferences file (survives a restart), not in the settings backups export.
 */
object LastAiProblem {
    private const val PREFS = "last_ai_problem"
    private const val KEY = "problem"
    private const val MAX_TRIES = 12

    /** Tries this long before the dialog's message belong to it. */
    private const val TRIES_WINDOW_MS = 5 * 60_000L

    @Serializable
    data class Try(val step: String, val model: String, val why: String, val atMs: Long)

    @Serializable
    data class Problem(val feature: String, val message: String, val atMs: Long, val appVersion: String, val tries: List<Try>)

    /** The last problem shown, or null (Compose state). */
    var last by mutableStateOf<Problem?>(null)
        private set

    private val recentTries = mutableListOf<Try>()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var prefs: SharedPreferences? = null

    /** App start: reads the saved problem off the main thread. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            p.getString(KEY, null)?.let { text ->
                last = runCatching { json.decodeFromString(Problem.serializer(), text) }.getOrNull()
            }
        }
    }

    /** A request that failed (AiClient sees every request). */
    fun noteTry(request: AiRequest, error: AiResponse.Error, nowMs: Long = System.currentTimeMillis()) =
        noteTry(request, whyOf(error), nowMs)

    /** A request whose answer the feature could not use, or any other [why] in BooxDay's own words. */
    fun noteTry(request: AiRequest, why: String, nowMs: Long = System.currentTimeMillis()) {
        val step = request.jsonSchema?.name?.let { STEPS[it] ?: it } ?: "answer"
        synchronized(recentTries) {
            recentTries.add(Try(step, request.model.id, why, nowMs))
            while (recentTries.size > MAX_TRIES) recentTries.removeAt(0)
        }
    }

    /** [feature]'s dialog shows [message] as a failure: kept with the tries just before it. */
    fun noteShown(feature: String, message: String, nowMs: Long = System.currentTimeMillis()) {
        val tries = synchronized(recentTries) {
            recentTries.filter { nowMs - it.atMs <= TRIES_WINDOW_MS }.also { recentTries.clear() }
        }
        val problem = Problem(feature, message, nowMs, BuildConfig.VERSION_NAME, tries)
        last = problem
        prefs?.edit()?.putString(KEY, json.encodeToString(Problem.serializer(), problem))?.apply()
    }

    /** [problem] in one line for AI Settings: which feature, when, and what it said. */
    fun summary(problem: Problem): String =
        "${problem.feature}, ${SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(problem.atMs))}: ${problem.message}"

    /** Puts [text] of [problem] on the clipboard (AI Settings → AI Models, owner's choice). */
    fun copy(context: Context, problem: Problem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BooxDay AI problem", text(problem)))
        Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
    }

    /** [problem] as plain lines, to copy into a report. */
    fun text(problem: Problem): String = buildString {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("Last AI problem: ${problem.feature}, ${time.format(Date(problem.atMs))}, BooxDay ${problem.appVersion}")
        appendLine("It said: ${problem.message}")
        if (problem.tries.isEmpty()) {
            appendLine("Every AI request before it answered.")
        } else {
            problem.tries.forEach { appendLine("- ${it.step}, ${it.model}: ${it.why}") }
        }
    }.trimEnd()

    /** Why [error] failed, in BooxDay's words: its kind, then the model's own fault if it was one. */
    internal fun whyOf(error: AiResponse.Error): String = buildString {
        append(AiProblem.of(error).shortText)
        AiPickStatus.reasonFor(error)?.let { append(" (").append(it).append(")") }
        (error as? AiResponse.Error.ApiError)?.code?.let { append(", HTTP ").append(it) }
    }

    // Answer formats, as the step they belong to
    private val STEPS = mapOf(
        "ocr_text" to "reading handwriting",
        "schedule_items" to "finding dates",
        "ocr_text_with_language" to "reading handwriting",
        "text_inference" to "clean-up",
        "memo_to_tasks" to "making tasks"
    )
}
