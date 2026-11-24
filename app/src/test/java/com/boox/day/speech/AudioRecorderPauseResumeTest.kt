package com.boox.day.speech

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies Phase 1 H6: Gemini Voice pause -> resume retains previously recorded PCM audio so both
 * segments combine into the final WAV payload, whereas starting a fresh session clears prior data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AudioRecorderPauseResumeTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun pauseAndResumePreservesPcmAcrossSegments() {
        val recorder = AudioRecorder(context)
        val part1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val part2 = byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16)

        recorder.prepareSessionBuffers(clearExisting = true)
        recorder.appendPcmForTest(part1)
        recorder.pauseRecording()
        assertEquals(part1.size, recorder.bufferedByteCountForTest())

        // Resume must keep part1 intact
        recorder.prepareSessionBuffers(clearExisting = false)
        assertEquals(part1.size, recorder.bufferedByteCountForTest())
        recorder.appendPcmForTest(part2)

        val base64Wav = recorder.stopAndGetBase64()
        assertNotNull(base64Wav)
        val wavBytes = Base64.decode(base64Wav!!, Base64.DEFAULT)
        // Standard 44-byte PCM WAV header followed by part1 + part2
        assertEquals(44 + part1.size + part2.size, wavBytes.size)
        assertArrayEquals(part1 + part2, wavBytes.copyOfRange(44, wavBytes.size))
    }

    @Test
    fun freshStartClearsPreviousPcm() {
        val recorder = AudioRecorder(context)
        recorder.appendPcmForTest(byteArrayOf(1, 2, 3, 4))
        recorder.prepareSessionBuffers(clearExisting = true)
        assertEquals(0, recorder.bufferedByteCountForTest())
    }
}
