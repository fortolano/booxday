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
    fun `old full calendar scope still counts as signed in`() {
        val email = "https://www.googleapis.com/auth/userinfo.email"
        val driveFile = "https://www.googleapis.com/auth/drive.file"
        // A sign-in record from 1.0.37 or older lists only the full scope
        assertTrue(GoogleAuthManager.coversCalendarAccess(listOf("https://www.googleapis.com/auth/calendar", driveFile, email)))
        // A sign-in with this version
        assertTrue(GoogleAuthManager.coversCalendarAccess(GoogleAuthManager.CALENDAR_SCOPES + driveFile))
        // Partial or calendar-less records are not enough
        assertFalse(GoogleAuthManager.coversCalendarAccess(listOf("https://www.googleapis.com/auth/calendar.events", driveFile)))
        assertFalse(GoogleAuthManager.coversCalendarAccess(listOf("https://www.googleapis.com/auth/calendar.readonly", driveFile)))
        assertFalse(GoogleAuthManager.coversCalendarAccess(listOf(driveFile, email)))
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
