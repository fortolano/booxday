package com.boox.day.sync

import com.boox.day.api.NetworkState
import com.boox.day.api.ai.AiProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Google sign-in failures in plain words; before Oct 2026: "Sign-in failed: Sign-in failed: 10: ". */
class SignInErrorTextTest {

    private val online = NetworkState.ONLINE

    @Test
    fun eachKnownCodeHasItsOwnWords() {
        assertEquals(
            "Couldn't reach Google. Check the internet connection, then try again.",
            SignInErrorText.google(7, online)
        )
        assertEquals(
            "Google didn't accept Boox Day's sign-in (code 10). This is a setup problem in Boox Day, " +
                "not your account. Please report it on GitHub.",
            SignInErrorText.google(10, online)
        )
        assertEquals(
            "Google sign-in failed. Turn on Google Play in the BOOX settings and make sure Google Play " +
                "services is up to date, then try again.",
            SignInErrorText.google(12500, online)
        )
        assertEquals("Sign-in was cancelled.", SignInErrorText.google(12501, online))
        assertEquals("Sign-in is already in progress.", SignInErrorText.google(12502, online))
    }

    @Test
    fun otherCodesAndNoCodeStaySimple() {
        assertEquals("Google sign-in failed (code 8).", SignInErrorText.google(8, online))
        assertEquals("Google sign-in failed.", SignInErrorText.google(null, online))
        assertFalse(SignInErrorText.google(10, online).startsWith("Sign-in failed"))
    }

    @Test
    fun offlineTheConnectionIsTheReasonUnlessTheUserCancelled() {
        assertEquals(AiProblem.NO_NETWORK.message, SignInErrorText.google(12500, NetworkState.NONE))
        assertEquals(AiProblem.NO_INTERNET.message, SignInErrorText.google(7, NetworkState.NO_INTERNET))
        assertEquals(AiProblem.NO_NETWORK.message, SignInErrorText.google(null, NetworkState.NONE))
        assertEquals("Sign-in was cancelled.", SignInErrorText.google(12501, NetworkState.NONE))
        // UNKNOWN (the system couldn't tell) goes by the code
        assertEquals("Google sign-in failed (code 8).", SignInErrorText.google(8, NetworkState.UNKNOWN))
    }
}
