package com.boox.day.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config

/**
 * The once-only "AI and your calendar" notice (Oct 2026): an AI feature that sends events runs only
 * after Continue, and only the first time asks; Not Now runs nothing and asks again next time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalendarAiNoticeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("calendar_ai_notice", Context.MODE_PRIVATE).edit().clear().commit()
        CalendarAiNotice.init(context)
        if (CalendarAiNotice.isOpen) CalendarAiNotice.onCancel()
    }

    @Test
    fun firstUseWaitsForContinueThenRunsTheFeature() {
        var runs = 0
        CalendarAiNotice.afterNotice { runs++ }
        assertTrue(CalendarAiNotice.isOpen)
        assertEquals("nothing is sent before the user answers", 0, runs)

        CalendarAiNotice.onContinue()
        assertFalse(CalendarAiNotice.isOpen)
        assertEquals(1, runs)

        CalendarAiNotice.afterNotice { runs++ }
        assertFalse("shown once", CalendarAiNotice.isOpen)
        assertEquals(2, runs)
    }

    @Test
    fun notNowRunsNothingAndAsksAgain() {
        var runs = 0
        CalendarAiNotice.afterNotice { runs++ }
        CalendarAiNotice.onCancel()
        assertEquals(0, runs)
        assertFalse(CalendarAiNotice.isOpen)

        CalendarAiNotice.afterNotice { runs++ }
        assertTrue("asks again next time", CalendarAiNotice.isOpen)
    }

    @Test
    fun awaitAllowedAnswersWithTheUsersChoice() = runBlocking {
        val first = async { CalendarAiNotice.awaitAllowed() }
        while (!CalendarAiNotice.isOpen) {
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        CalendarAiNotice.onCancel()
        assertFalse(first.await())

        val second = async { CalendarAiNotice.awaitAllowed() }
        while (!CalendarAiNotice.isOpen) {
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        CalendarAiNotice.onContinue()
        assertTrue(second.await())

        assertTrue("seen: no notice and no wait", CalendarAiNotice.awaitAllowed())
    }
}
