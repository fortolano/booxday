package com.boox.day.speech

import com.boox.day.data.datastore.VoiceTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Voice uses Google's free speech recognition whenever the tablet has it (Oct 2026). */
class VoiceSourceTest {

    @Test
    fun googleSpeechWheneverTheTabletHasIt() {
        for (mode in VoiceTextMode.entries) {
            assertEquals("$mode", TranscriptionSource.SPEECH_RECOGNIZER, voiceSource(mode, speechRecognitionAvailable = true))
        }
    }

    @Test
    fun aiOnlyWhenChosenAndTheTabletHasNoSpeechRecognition() {
        assertEquals(TranscriptionSource.GEMINI, voiceSource(VoiceTextMode.GEMINI, speechRecognitionAvailable = false))
    }

    @Test
    fun otherwiseVoiceSettingsOpenFirst() {
        assertNull(voiceSource(VoiceTextMode.AUTO, speechRecognitionAvailable = false))
        assertNull(voiceSource(VoiceTextMode.SPEECH_RECOGNIZER, speechRecognitionAvailable = false))
    }
}
