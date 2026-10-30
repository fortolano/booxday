package com.boox.day.ui

import android.app.Application
import android.os.Looper
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.security.UserOpenRouterKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The feature that asked for a key goes on after Connect OpenRouter (Oct 2026, plan B1): it waits
 * while the browser is open, runs on Continue after "AI is on", and is dropped by Not Now, by the
 * paste route and when no key was saved. Each connection result shows once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiKeyGateResumeTest {

    private fun setKey(key: String?) {
        UserOpenRouterKey::class.java.getDeclaredMethod("setValue", String::class.java)
            .apply { isAccessible = true }
            .invoke(UserOpenRouterKey, key)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        GlobalAppSettings.update(GlobalAppSettings.current.copy(aiEnabled = true))
        setKey(null)
        AiKeyGate.close()
        AiKeyGate.closeResult(resume = false)
        idle()
    }

    @After
    fun tearDown() = setKey(null)

    @Test
    fun withAKeyTheFeatureRunsAtOnce() {
        setKey("sk-or-v1-test")
        var runs = 0
        AiKeyGate.withKey { runs++ }
        assertEquals(1, runs)
        assertFalse(AiKeyGate.isOpen)
    }

    @Test
    fun theFeatureWaitsForConnectThenRunsOnContinue() {
        var runs = 0
        AiKeyGate.withKey { runs++ }
        idle()
        assertEquals(AiKeyGate.Reason.NO_KEY, AiKeyGate.openReason)
        assertEquals("nothing runs without a key", 0, runs)

        AiKeyGate.closeForConnect()  // Continue to OpenRouter
        idle()
        assertNull(AiKeyGate.openReason)
        assertTrue("it waits while the browser is open", AiKeyGate.hasPendingAction)

        setKey("sk-or-v1-new")  // Connect OpenRouter saved a key
        AiKeyGate.showResult(1)
        assertTrue("\"AI is on\" keeps raw drawing off", AiKeyGate.isOpen)
        AiKeyGate.closeResult(resume = true)
        assertEquals(1, runs)
        assertFalse(AiKeyGate.isOpen)
        assertFalse(AiKeyGate.hasPendingAction)
    }

    @Test
    fun notNowAndThePasteRouteDropTheFeature() {
        var runs = 0
        AiKeyGate.withKey { runs++ }
        AiKeyGate.close()
        idle()
        assertFalse(AiKeyGate.hasPendingAction)

        AiKeyGate.withKey { runs++ }
        AiKeyGate.requestAddKey()
        idle()
        assertFalse(AiKeyGate.hasPendingAction)
        assertTrue("the key card opens at the paste field", AiKeyGate.consumePasteRequest())
        assertFalse("only once", AiKeyGate.consumePasteRequest())
        AiKeyGate.consumeAddKey()
        assertEquals(0, runs)
    }

    @Test
    fun aFailedConnectionRunsNothing() {
        var runs = 0
        AiKeyGate.withKey { runs++ }
        AiKeyGate.closeForConnect()
        idle()
        AiKeyGate.showResult(1)
        AiKeyGate.closeResult(resume = true)  // no key was saved
        assertEquals(0, runs)
    }

    @Test
    fun aiFeaturesOffOpensNothingAndKeepsNothing() {
        GlobalAppSettings.update(GlobalAppSettings.current.copy(aiEnabled = false))
        var runs = 0
        AiKeyGate.withKey { runs++ }
        idle()
        assertNull(AiKeyGate.openReason)
        assertFalse(AiKeyGate.hasPendingAction)
        assertEquals(0, runs)
    }

    @Test
    fun eachResultIsHandledOnce() {
        val before = AiKeyGate.handledResult
        AiKeyGate.markResultHandled(before + 1)  // the AI Settings card showed it
        assertEquals(before + 1, AiKeyGate.handledResult)
        AiKeyGate.showResult(before + 2)
        assertEquals(before + 2, AiKeyGate.handledResult)
        AiKeyGate.markResultHandled(before + 1)  // an older one never lowers it
        assertEquals(before + 2, AiKeyGate.handledResult)
    }
}
