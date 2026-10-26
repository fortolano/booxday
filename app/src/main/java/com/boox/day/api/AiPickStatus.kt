package com.boox.day.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Whether the model the user picked for a job failed last time (Oct 2026, plan step f): the AI
 * Models card then says so ("Didn't answer last time (model not found)..."), so a pick never fails
 * silently while BooxDay's choice answers instead.
 *
 * Only the model's own failures count; no internet, a key problem or a busy provider don't. Cleared
 * when the pick answers, and ignored once the job has another pick. Kept in a small preferences
 * file (survives a restart), not in the settings that backups export.
 */
object AiPickStatus {
    private const val TAG = "AiPickStatus"
    private const val PREFS = "ai_pick_status"

    @Serializable
    data class Failure(val modelId: String, val reason: String, val atMs: Long)

    /** Last failure per job (Compose state: the card follows it). */
    var failures by mutableStateOf<Map<AiJob, Failure>>(emptyMap())
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var prefs: SharedPreferences? = null

    /** App start: reads the saved failures off the main thread. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            failures = AiJob.entries.mapNotNull { job ->
                p.getString(job.name, null)?.let { text ->
                    runCatching { job to json.decodeFromString(Failure.serializer(), text) }.getOrNull()
                }
            }.toMap()
        }
    }

    /** The failure to show for [job], only while [currentPickId] is still the model that failed. */
    fun failureFor(job: AiJob, currentPickId: String?): Failure? =
        failures[job]?.takeIf { currentPickId != null && it.modelId == currentPickId }

    /**
     * Why [error] counts as the picked model's failure, in a few words, or null when it doesn't
     * count: no internet, a missing or rejected key, a busy provider (rate limit), or a server error.
     */
    fun reasonFor(error: AiResponse.Error): String? = when (error) {
        is AiResponse.Error.ApiError -> when {
            error.code == 404 && error.message.contains("data policy", ignoreCase = true) ->
                "blocked by your OpenRouter settings"
            error.code == 404 -> "model not found"
            error.code == 403 -> "refused by OpenRouter"
            else -> null
        }
        is AiResponse.Error.InvalidRequest -> "request not accepted"
        is AiResponse.Error.NoContent ->
            if (error.message.contains("cut off", ignoreCase = true)) "answer cut off" else "empty answer"
        is AiResponse.Error.OutOfCredit -> if (error.isAccountOutOfCredit) "needs credit" else null
        else -> null
    }

    /** What one request to a picked model says (AiClient sees every request). */
    fun noteResult(model: AiModel, result: AiResponse<String>) {
        val pick = (model as? AiModel.Custom)?.takeIf { it.userChoice } ?: return
        val job = pick.job ?: return
        when (result) {
            is AiResponse.Success -> onAnswered(job, pick.id)
            is AiResponse.Error -> reasonFor(result)?.let { onFailed(job, pick.id, it) }
        }
    }

    /** The feature could not use a picked model's answer (AiRetryExecutor). */
    fun noteUnusableAnswer(model: AiModel) {
        val pick = (model as? AiModel.Custom)?.takeIf { it.userChoice } ?: return
        onFailed(pick.job ?: return, pick.id, "unusable answer")
    }

    fun onFailed(job: AiJob, modelId: String, reason: String, nowMs: Long = System.currentTimeMillis()) {
        val failure = Failure(modelId, reason, nowMs)
        failures = failures + (job to failure)
        Log.w(TAG, "$job pick $modelId failed: $reason")
        prefs?.edit()?.putString(job.name, json.encodeToString(Failure.serializer(), failure))?.apply()
    }

    fun onAnswered(job: AiJob, modelId: String) {
        if (failures[job]?.modelId != modelId) return
        clear(job)
    }

    /** Forget [job]'s failure (it answered, or the user changed the pick). */
    fun clear(job: AiJob) {
        if (job !in failures) return
        failures = failures - job
        prefs?.edit()?.remove(job.name)?.apply()
    }
}
