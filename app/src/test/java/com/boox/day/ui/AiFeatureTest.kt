package com.boox.day.ui

import android.app.Application
import com.boox.day.data.datastore.GlobalAppSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The privacy promise: with AI off, nothing runs (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiFeatureTest {
    private val before = GlobalAppSettings.current

    @After
    fun tearDown() {
        GlobalAppSettings.update(before)
    }

    @Test
    fun nothingRunsWhileAiIsOff() {
        GlobalAppSettings.update(before.copy(aiEnabled = false))
        var ran = false
        AiFeature.start(sendsCalendar = false) { ran = true }
        assertFalse(ran)
    }
}
