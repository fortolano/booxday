package com.boox.day.sync

import com.google.android.gms.auth.GoogleAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google Calendar scopes narrowed for Google's OAuth verification (Oct 2026), and a refused Google
 * token (access withdrawn, or the narrower scopes not yet allowed) reported as a sign-in problem,
 * so Calendar Accounts offers Reconnect instead of a generic error.
 */
class GoogleCalendarScopesTest {

    @Test
    fun `calendar scopes are events and calendar list only`() {
        assertEquals(
            listOf(
                "https://www.googleapis.com/auth/calendar.events",
                "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
            ),
            GoogleAuthManager.CALENDAR_SCOPES
        )
        assertFalse("https://www.googleapis.com/auth/calendar" in GoogleAuthManager.CALENDAR_SCOPES)
    }

    @Test
    fun `refused Google token is an authentication error`() {
        val refused = GoogleAuthIOException(GoogleAuthException("NeedPermission"))
        assertTrue(refused.isGoogleTokenRefused())
        assertEquals(SyncErrorType.AUTHENTICATION, refused.toSyncErrorType())
    }

    @Test
    fun `refused token inside another exception is still found`() {
        val wrapped = RuntimeException("sync failed", GoogleAuthIOException(GoogleAuthException("NeedPermission")))
        assertTrue(wrapped.isGoogleTokenRefused())
    }

    @Test
    fun `network errors are not sign-in problems`() {
        val offline = java.net.UnknownHostException("www.googleapis.com")
        assertFalse(offline.isGoogleTokenRefused())
        assertEquals(SyncErrorType.NETWORK, offline.toSyncErrorType())
    }
}
