package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.LastAiProblem
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.SummaryCache
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MeetingRecapAction(
    @SerializedName("text") val text: String = "",
    @SerializedName("owner") val owner: String = "",
    @SerializedName("due_date") val dueDate: String = ""
)

/** What the meeting's notes say. Empty lists are fine: nothing here may be made up. */
data class MeetingRecap(
    @SerializedName("summary") val summary: String = "",
    @SerializedName("decisions") val decisions: List<String> = emptyList(),
    @SerializedName("action_items") val actionItems: List<MeetingRecapAction> = emptyList(),
    @SerializedName("follow_ups") val followUps: List<String> = emptyList(),
    @SerializedName("open_questions") val openQuestions: List<String> = emptyList()
)

sealed interface MeetingRecapResult {
    data class Done(val recap: MeetingRecap) : MeetingRecapResult
    data object Offline : MeetingRecapResult
    data class Failed(val message: String) : MeetingRecapResult
}

/** A meeting's notes to a recap, in one writing call (Nov 2026). */
object MeetingRecapService {
    private const val TAG = "MeetingRecap"
    private const val FEATURE = "Meeting recap"
    private const val TYPE = "meeting_recap"
    private const val MAX_TEXT_LENGTH = 16000
    /** A saved recap isn't a day's summary: it stays until the notes change. */
    private const val VALID_HOURS = 24 * 365 * 10
    private val gson = Gson()

    private val OFFLINE = setOf(AiProblem.NO_NETWORK.message, AiProblem.NO_INTERNET.message, AiProblem.OPENROUTER_UNREACHABLE.message)

    private val SYSTEM_PROMPT = """
You write a short recap of a meeting from the person's own notes.

Return raw JSON only, in the format given.

RULES:
- Use only what the notes say. Never add a decision, a task, a date or a name that isn't there.
- Keep names exactly as written.
- Write in the same language as the notes.
- The meeting's day is given: use it for words like "tomorrow" or "Friday" in due_date, as yyyy-MM-dd, or "" when no date is written.
- summary is at most 5 sentences.
- owner is the person named for the action, or "".
- Empty lists are fine. Never fill a list to look complete.
""".trim()

    private fun id(eventId: String) = "${TYPE}_$eventId"

    /** The last recap of this meeting and the notes it came from; no AI. */
    fun cached(context: Context, eventId: String): Pair<MeetingRecap, String>? {
        val row = AppDatabase.getDatabase(context.applicationContext).summaryCacheDao().get(id(eventId)) ?: return null
        val recap = runCatching { gson.fromJson(row.resultJson, MeetingRecap::class.java) }.getOrNull() ?: return null
        return recap to row.contentHash
    }

    private fun remember(context: Context, eventId: String, day: String, fingerprint: String, recap: MeetingRecap) {
        AppDatabase.getDatabase(context.applicationContext).summaryCacheDao().save(
            SummaryCache(
                id = id(eventId), type = TYPE, dateKey = day, contentHash = fingerprint, resultJson = gson.toJson(recap),
                generatedAt = System.currentTimeMillis(), validHours = VALID_HOURS
            )
        )
    }

    /** One AI call on notes already read. [fingerprint] keeps the answer for the next visit. */
    suspend fun make(context: Context, eventId: String, title: String, day: String, notes: String, fingerprint: String): MeetingRecapResult {
        val failures = MemoToTasksService.ModelFailures()
        val userPrompt = buildString {
            appendLine("Meeting: $title")
            appendLine("Its day: $day")
            appendLine()
            appendLine("The notes:")
            append(notes.take(MAX_TEXT_LENGTH))
        }
        for (model in ModelConfigProvider.getTextFallbackChain()) {
            try {
                val request = aiRequest {
                    model(model)
                    systemPrompt(SYSTEM_PROMPT)
                    userMessage(userPrompt)
                    jsonSchema("meeting_recap", StructuredOutputSchemas.meetingRecapSchema)
                    if (model.hasCapability(AiCapability.REASONING)) enableReasoning()
                }
                val response = AiRetryExecutor.send(request, true)
                if (response is AiResponse.Success) {
                    val recap = parse(response.data)
                    if (recap != null) {
                        withContext(Dispatchers.IO) { remember(context, eventId, day, fingerprint, recap) }
                        return MeetingRecapResult.Done(recap)
                    }
                    LastAiProblem.noteTry(request, AiRetryExecutor.UNUSABLE_ANSWER)
                }
                if (failures.note(response)) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A model failed: ${e.javaClass.simpleName}")
            }
        }
        return failed(failures.message())
    }

    fun failed(message: String): MeetingRecapResult {
        if (message in OFFLINE) return MeetingRecapResult.Offline
        LastAiProblem.noteShown(FEATURE, message)
        return MeetingRecapResult.Failed(message)
    }

    private fun parse(answer: String): MeetingRecap? = try {
        gson.fromJson(JsonExtractor.extract(answer), MeetingRecap::class.java)?.takeIf { it.summary.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
