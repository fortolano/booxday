package com.boox.day.ai

import android.app.Application
import android.graphics.Bitmap
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiMessage
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.ui.dialogs.AiScope
import com.boox.day.ui.dialogs.memoToTasksRetry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where Convert to Tasks ends (2 Nov 2026, owner's decisions). On 1.0.56 a selected handwritten
 * sentence, or a memo page of notes, ended on "Could not convert memo" because the AI found no
 * to-do, though nothing had failed; and Retry after a lasso converted the whole page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoToTasksOutcomeTest {

    private val originalSend = AiRetryExecutor.send
    private val requests = mutableListOf<AiRequest>()

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    /** Answers each step of the conversion by its answer format; [ocr] answers the reader's tries in turn. */
    private fun answer(ocr: List<String> = emptyList(), refined: String, tasks: String) {
        var ocrTry = 0
        AiRetryExecutor.send = { request, _ ->
            requests += request
            val data = when (request.jsonSchema?.name) {
                "ocr_text" -> ocr[ocrTry++]
                "text_inference" -> """{"refinedText":${json(refined)},"confidence":0.9}"""
                else -> tasks
            }
            AiResponse.Success(data, request.model.id, null)
        }
    }

    private fun json(text: String) = com.google.gson.Gson().toJson(text)

    private val notes = "Phoenix kickoff\n- goal: beta by Dec 15\n\n- call the bank about the\nmortgage rate"

    @Test
    fun `a page with no clear to-dos offers its lines to tick`() {
        answer(refined = notes, tasks = """{"tasks":[]}""")
        val result = runBlocking { MemoToTasksService.convertReadText(notes, MemoToTasksService.Source.PAGE, null, emptyList()) {} }

        assertNull(result.error)
        assertTrue(result.linesToPick)
        // Markers dropped, a sentence written over two lines kept whole, nothing ticked for the user
        assertEquals(listOf("Phoenix kickoff", "goal: beta by Dec 15", "call the bank about the mortgage rate"), result.tasks.map { it.text })
        assertTrue(result.tasks.none { it.isSelected })
        assertEquals(MemoToTasksProcessingState.SelectTasks, MemoToTasksProcessingState.of(result))
        // A real failure still says what went wrong
        val failed = MemoToTasksResult("", "", emptyList(), error = "No internet.")
        assertEquals(MemoToTasksProcessingState.Error("No internet."), MemoToTasksProcessingState.of(failed))
    }

    @Test
    fun `a selection is never turned down`() {
        val sentence = "Ask Sam about the\nLisbon trip"
        // The AI finds no to-do: the selection's own words are the task, ticked
        answer(refined = sentence, tasks = """{"tasks":[]}""")
        val empty = runBlocking { MemoToTasksService.convertReadText(sentence, MemoToTasksService.Source.SELECTION, null, emptyList()) {} }
        assertEquals(listOf("Ask Sam about the Lisbon trip"), empty.tasks.map { it.text })
        assertTrue(empty.tasks.all { it.isSelected })
        assertFalse(empty.linesToPick)
        val conversion = requests.last { it.jsonSchema?.name == "memo_to_tasks" }
        assertTrue(conversion.messages.filterIsInstance<AiMessage.System>().single().content.contains("so every item in it is a task"))

        // A task the model is unsure of is kept (a page drops it below 0.3)
        answer(refined = sentence, tasks = """{"tasks":[{"text":"Ask Sam about the Lisbon trip","suggestedPriority":"","reason":"Selected","confidence":0.1,"suggestedTime":"","relatedEventId":""}]}""")
        val unsure = runBlocking { MemoToTasksService.convertReadText(sentence, MemoToTasksService.Source.SELECTION, null, emptyList()) {} }
        assertEquals(listOf("Ask Sam about the Lisbon trip"), unsure.tasks.map { it.text })
    }

    @Test
    fun `the handwriting reader never takes a reply as the memo's text`() {
        answer(
            ocr = listOf("Sure! The note in this picture says to buy milk.", """{"hasText":true,"text":"buy milk"}"""),
            refined = "Buy milk",
            tasks = """{"tasks":[{"text":"Buy milk","suggestedPriority":"","reason":"Errand","confidence":0.9,"suggestedTime":"","relatedEventId":""}]}"""
        )
        val page = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val result = runBlocking { MemoToTasksService.convertMemoToTasks(page) {} }

        assertEquals("buy milk", result.originalText)
        assertEquals(2, requests.count { it.jsonSchema?.name == "ocr_text" })
        assertEquals(listOf("Buy milk"), result.tasks.map { it.text })
    }

    @Test
    fun `retry runs the same selection again`() {
        var selectionRuns = 0
        val pageChoices = mutableListOf<AiScope>()
        // A lasso conversion: the dialog holds This page as a placeholder choice
        memoToTasksRetry({ selectionRuns++ }, AiScope.CurrentPage) { pageChoices += it }
        assertEquals(1, selectionRuns)
        assertTrue(pageChoices.isEmpty())
        // The AI menu's conversion repeats the page choice made
        memoToTasksRetry(null, AiScope.AllPages) { pageChoices += it }
        assertEquals(listOf(AiScope.AllPages), pageChoices)
    }
}
