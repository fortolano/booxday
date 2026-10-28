package com.boox.day.ai

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recognize Handwriting's first step asks for {"hasText", "text"} (Oct 2026). The default path read
 * only text between delimiters, so {"hasText": false} counted as text: a lasso selection of shapes
 * went through two more AI steps and came back as "[No text provided]" to put on the page.
 * Robolectric for org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HandwritingStage1TextTest {
    private val service = AiHandwritingService()

    @Test
    fun `no text found is an empty text`() {
        assertEquals("", service.parseStage1Text("""{"hasText": false, "text": ""}"""))
    }

    @Test
    fun `the text of a JSON answer, without the JSON`() {
        assertEquals("Team sync\n- Book a venue", service.parseStage1Text("""{"hasText": true, "text": "Team sync\n- Book a venue"}"""))
    }

    @Test
    fun `a JSON answer in a code fence`() {
        assertEquals("Buy milk", service.parseStage1Text("```json\n{\"hasText\": true, \"text\": \"Buy milk\"}\n```"))
    }

    @Test
    fun `older answers between delimiters and plain answers still read`() {
        assertEquals("Call Anna", service.parseStage1Text("<<<START_TEXT>>>\nCall Anna\n<<<END_TEXT>>>"))
        assertEquals("Call Anna", service.parseStage1Text("  Call Anna \n"))
    }
}
