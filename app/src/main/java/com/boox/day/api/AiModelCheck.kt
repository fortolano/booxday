package com.boox.day.api

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.boox.day.ai.JsonExtractor
import com.boox.day.ai.StructuredOutputSchemas
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.datastore.AiModelChoice
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tries a model on a job before it becomes the pick (Oct 2026, owner: a listed model must work).
 * One small real request, built like the job's features build theirs (a picture, audio, the exact
 * JSON format, private providers for search indexing) with the reasoning level and output tokens as
 * set, and the answer read like the features read it. The list's checks say a model should work;
 * this says it does, for this account, before it is saved. A fraction of a cent; free models cost
 * nothing.
 */
object AiModelCheck {

    sealed class Result {
        object Works : Result()

        /** [reason]: a full sentence for the model settings step */
        data class Fails(val reason: String) : Result()
    }

    const val UNUSABLE_ANSWER = "The model answered, but not in the form BooxDay needs for this job."
    private const val CANT_TAKE_INPUT = "This model can't take what this job sends."

    /** Sends the check for [choice] on [job] and reads the answer. */
    suspend fun check(job: AiJob, choice: AiModelChoice): Result {
        val model = ModelConfigProvider.userModel(job, choice)
            ?: return Result.Fails(CANT_TAKE_INPUT)
        val request = try {
            requestFor(job, model)
        } catch (e: IllegalArgumentException) {
            return Result.Fails(CANT_TAKE_INPUT)  // AiRequestBuilder: the model lacks a needed capability
        }
        val response = AiClient.execute(request, promptForKey = false, notePick = false)
        return when (response) {
            is AiResponse.Success -> if (answerFits(job, response.data)) Result.Works else Result.Fails(UNUSABLE_ANSWER)
            is AiResponse.Error -> Result.Fails(reasonFor(response))
        }
    }

    /** The job's check request for [model] (a picked model: AiClient sends its reasoning level). */
    internal fun requestFor(job: AiJob, model: AiModel): AiRequest = when (job) {
        AiJob.HANDWRITING -> aiRequest {
            model(model)
            systemPrompt(READ_PROMPT)
            userMessageWithImage("Read the text in this picture:", picture())
            jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextSchema)
        }
        // As OpenRouterOcrService reads a page: providers that keep no copy, no reasoning
        AiJob.SEARCH_INDEXING -> aiRequest {
            model(model)
            systemPrompt(READ_PROMPT)
            userMessageWithImage("Read the text in this picture:", picture())
            jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextWithLanguageSchema)
            zeroDataRetention()
            temperature(0f)
        }.copy(overrideReasoning = ReasoningConfig.disabled())
        AiJob.VOICE -> aiRequest {
            model(model)
            systemPrompt("You transcribe audio. If there is no speech, say so in a few words.")
            userMessageWithAudio("This is a short test tone without speech. Reply with the word OK.", tone(), "audio/wav")
        }
        AiJob.WRITING -> aiRequest {
            model(model)
            systemPrompt(TASKS_PROMPT)
            userMessage("Note: call the dentist tomorrow at 10am")
            jsonSchema("memo_to_tasks", StructuredOutputSchemas.memoToTasksSchema)
        }
        AiJob.PLANNING -> aiRequest {
            model(model)
            systemPrompt(BREAKDOWN_PROMPT)
            userMessage("Task: plan a family picnic")
            jsonSchema("task_breakdown_v3", StructuredOutputSchemas.breakdownSchema)
        }
    }

    /** Whether [answer] is what the job's features need: the words read, tasks, steps, or any reply to the tone. */
    internal fun answerFits(job: AiJob, answer: String): Boolean = try {
        when (job) {
            AiJob.VOICE -> answer.isNotBlank()
            else -> {
                val json = JSONObject(JsonExtractor.extract(answer))
                when (job) {
                    AiJob.HANDWRITING, AiJob.SEARCH_INDEXING ->
                        json.optBoolean("hasText", false) && json.optString("text").contains("milk", ignoreCase = true)
                    AiJob.WRITING -> json.optJSONArray("tasks")?.optJSONObject(0)?.optString("text")
                        ?.contains("dentist", ignoreCase = true) == true
                    else -> (json.optJSONArray("subtasks")?.length() ?: 0) >= 3
                }
            }
        }
    } catch (e: Exception) {
        false
    }

    /** Why the check failed, as a full sentence. */
    internal fun reasonFor(error: AiResponse.Error): String = when {
        error is AiResponse.Error.ApiError && error.code == 404 && error.message.contains("data policy", ignoreCase = true) ->
            "Your OpenRouter privacy settings block this model. You can change them at openrouter.ai/settings/privacy."
        error is AiResponse.Error.ApiError && error.code == 404 && error.message.contains("No endpoints found", ignoreCase = true) ->
            "None of this model's providers can take this job's requests."
        error is AiResponse.Error.ApiError && error.code == 404 -> "OpenRouter no longer has this model."
        error is AiResponse.Error.InvalidRequest -> "The model refused this job's request."
        error is AiResponse.Error.NoContent && error.message.contains("cut off", ignoreCase = true) ->
            "The model used all its output tokens before answering. Try more output tokens or less reasoning."
        error is AiResponse.Error.NoContent -> "The model gave an empty answer."
        // No internet, busy, key or credit: AiProblem's sentences
        else -> AiProblem.messageFor(error)
    }

    /** "Buy milk", black on white, as JPEG Base64 like the features send pages. */
    private fun picture(): String {
        val bitmap = Bitmap.createBitmap(480, 160, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("Buy milk", 40f, 105f, Paint().apply { color = Color.BLACK; textSize = 72f; isAntiAlias = true })
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Half a second of a 440 Hz tone, 16 kHz mono 16-bit WAV like AudioRecorder's, as Base64. */
    private fun tone(): String {
        val rate = 16_000
        val samples = rate / 2
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
        wav.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(samples * 2)
        for (i in 0 until samples) wav.putShort((3000 * sin(2 * PI * 440 * i / rate)).toInt().toShort())
        return Base64.encodeToString(wav.array(), Base64.NO_WRAP)
    }

    private val READ_PROMPT = """
        You extract text from images of handwritten and typed notes.
        Return raw JSON only: {"hasText": true, "text": "..."} with the text as written,
        or {"hasText": false, "text": ""} when there is no readable text.
    """.trimIndent()

    private val TASKS_PROMPT = """
        Turn the note into tasks. Return raw JSON only, matching the schema: every field in every task,
        "" for a field with nothing to say.
    """.trimIndent()

    private val BREAKDOWN_PROMPT = """
        Break the task into 3 to 5 short, concrete steps in order. Return raw JSON only, matching the
        schema, with keepOriginal false.
    """.trimIndent()
}
