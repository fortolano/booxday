package com.boox.day.data.datastore

import com.boox.day.api.AiJob
import com.boox.day.api.ReasoningOptions
import com.boox.day.data.db.KvProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The model choices live in the settings JSON: older and newer settings must still load. */
class AiModelChoicesSettingsTest {

    private val json = KvProxy.settingsJson

    @Test
    fun `settings saved before model choices existed still load`() {
        val old = """{"version":1,"aiEnabled":true,"handwritingLanguage":"en-US"}"""
        val settings = json.decodeFromString(AppSettings.serializer(), old)
        assertEquals(AiModelChoices(), settings.aiModels)
        for (job in AiJob.entries) assertNull(settings.aiModels.forJob(job))
    }

    @Test
    fun `a choice survives saving and loading`() {
        val choice = AiModelChoice(
            id = "google/gemini-3.1-flash-lite",
            name = "Gemini 3.1 Flash Lite",
            lab = "Google",
            reasoning = "low",
            maxOutputTokens = 5_000,
            reasoningOptions = ReasoningOptions(true, false, listOf("high", "medium", "low", "minimal", "none")),
            modelMaxOutputTokens = 65_536,
            contextLength = 1_048_576,
            inputs = listOf("text", "image", "audio")
        )
        val settings = AppSettings(version = 1, aiModels = AiModelChoices().withJob(AiJob.VOICE, choice))
        val loaded = json.decodeFromString(AppSettings.serializer(), json.encodeToString(AppSettings.serializer(), settings))
        assertEquals(choice, loaded.aiModels.forJob(AiJob.VOICE))
        assertNull(loaded.aiModels.forJob(AiJob.WRITING))
    }

    @Test
    fun `fields from newer versions are ignored`() {
        val newer = """{"version":1,"aiModels":{"planning":{"id":"x/y","futureField":true}},"someNewSetting":3}"""
        val settings = json.decodeFromString(AppSettings.serializer(), newer)
        assertEquals("x/y", settings.aiModels.planning?.id)
    }
}
