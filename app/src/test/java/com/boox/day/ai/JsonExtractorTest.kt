package com.boox.day.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonExtractorTest {

    @Test
    fun `extract handles clean json object`() {
        val input = """{"title": "Test Event"}"""
        val result = JsonExtractor.extract(input)
        assertEquals(input, result)
    }

    @Test
    fun `extract handles clean json array`() {
        val input = """[{"id": 1}, {"id": 2}]"""
        val result = JsonExtractor.extract(input)
        assertEquals(input, result)
    }

    @Test
    fun `extract handles markdown code blocks`() {
        val input = """
            ```json
            {
              "title": "Markdown Event"
            }
            ```
        """.trimIndent()
        val expected = """
            {
              "title": "Markdown Event"
            }
        """.trimIndent()
        
        val result = JsonExtractor.extract(input)
        assertEquals(expected, result)
    }

    @Test
    fun `extract handles conversational text prefix`() {
        val input = """
            Here is the JSON you requested:
            {
              "title": "Conversational Event"
            }
        """.trimIndent()
        val expected = """
            {
              "title": "Conversational Event"
            }
        """.trimIndent()
        
        val result = JsonExtractor.extract(input)
        assertEquals(expected, result)
    }

    @Test
    fun `extract handles conversational text suffix`() {
        val input = """
            {
              "title": "Suffix Event"
            }
            I hope this helps!
        """.trimIndent()
        val expected = """
            {
              "title": "Suffix Event"
            }
        """.trimIndent()
        
        val result = JsonExtractor.extract(input)
        assertEquals(expected, result)
    }

    @Test
    fun `extract handles mixed noise`() {
        val input = """
            Okay, based on your input:
            ```json
            {
              "title": "Noisy Event",
              "date": "2025-12-25"
            }
            ```
            Let me know if you need changes.
        """.trimIndent()
        val expected = """
            {
              "title": "Noisy Event",
              "date": "2025-12-25"
            }
        """.trimIndent()
        
        val result = JsonExtractor.extract(input)
        assertEquals(expected, result)
    }

    @Test
    fun `extract returns original if no json found`() {
        val input = "I could not understand what you said."
        val result = JsonExtractor.extract(input)
        assertEquals(input, result)
    }
}
