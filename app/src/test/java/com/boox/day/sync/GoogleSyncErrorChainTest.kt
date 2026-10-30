package com.boox.day.sync

import com.boox.day.sync.provider.CalendarProviderException
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.google.android.gms.auth.GoogleAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Google sign-in problem must reach Calendar Accounts as "Reconnect" (Oct 2026). Until then a
 * token Google refused on every calendar was caught per calendar, its exception dropped
 * (CalendarSyncManager.syncEvents), so the combined result carried no exception and
 * GoogleCalendarProvider reported a generic provider error: "Authentication failed while
 * fetching calendars" with no Reconnect button. And every 403 counted as a sign-in problem,
 * although Google also answers 403 for "rate limit exceeded".
 */
class GoogleSyncErrorChainTest {

    private val refused = GoogleAuthIOException(GoogleAuthException("NeedPermission"))

    @Test
    fun `a refused token on every calendar reaches the account as sign-in needed`() {
        // CalendarSyncManager.syncEvents: each calendar's request fails inside its own catch
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 0,
            fetchFailures = listOf(
                CalendarFetchFailure("work", refused.toSyncErrorType(), "NeedPermission", cause = refused),
                CalendarFetchFailure("family", refused.toSyncErrorType(), "NeedPermission", cause = refused)
            ),
            newEventsCount = 0,
            updatedEventsCount = 0
        ) as SyncResult.Error

        assertTrue("the refusal reaches the result", result.exception?.isGoogleTokenRefused() == true)
        // MultiAccountSyncManager.syncAccount turns this into SyncResult.NotSignedIn -> needsReauth
        assertTrue(GoogleCalendarProvider.fetchFailure(result) is CalendarProviderException.AuthenticationException)
    }

    @Test
    fun `a 401 on every calendar is sign-in needed too`() {
        val unauthorized = googleError(401, "authError")
        val result = buildCalendarFetchSyncResult(
            0, listOf(CalendarFetchFailure("work", unauthorized.toSyncErrorType(), "401", cause = unauthorized)), 0, 0
        ) as SyncResult.Error

        assertTrue(GoogleCalendarProvider.fetchFailure(result) is CalendarProviderException.AuthenticationException)
    }

    @Test
    fun `rate limited on every calendar is not a sign-in problem`() {
        val busy = googleError(403, "rateLimitExceeded")
        val result = buildCalendarFetchSyncResult(
            0, listOf(CalendarFetchFailure("work", busy.toSyncErrorType(), "Rate Limit Exceeded", cause = busy)), 0, 0
        ) as SyncResult.Error

        assertEquals(SyncErrorType.QUOTA, result.errorType)
        assertTrue(GoogleCalendarProvider.fetchFailure(result) is CalendarProviderException.ProviderException)
    }

    @Test
    fun `one calendar refused while another synced stays a partial sync`() {
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 1,
            fetchFailures = listOf(CalendarFetchFailure("work", SyncErrorType.AUTHENTICATION, "NeedPermission", cause = refused)),
            newEventsCount = 3,
            updatedEventsCount = 0
        )

        assertTrue(result is SyncResult.PartialSuccess)
    }

    @Test
    fun `Google errors are classified by status and reason`() {
        assertEquals(SyncErrorType.AUTHENTICATION, googleError(401, null).toSyncErrorType())
        assertEquals(SyncErrorType.AUTHENTICATION, googleError(403, "insufficientPermissions").toSyncErrorType())
        assertEquals(SyncErrorType.QUOTA, googleError(403, "rateLimitExceeded").toSyncErrorType())
        assertEquals(SyncErrorType.QUOTA, googleError(403, "userRateLimitExceeded").toSyncErrorType())
        assertEquals(SyncErrorType.PERMISSION, googleError(403, "forbidden").toSyncErrorType())
        assertEquals(SyncErrorType.NOT_FOUND, googleError(404, "notFound").toSyncErrorType())
        assertEquals(SyncErrorType.QUOTA, googleError(429, "rateLimitExceeded").toSyncErrorType())
        assertEquals(SyncErrorType.SERVER, googleError(503, "backendError").toSyncErrorType())
        assertEquals(SyncErrorType.CLIENT, googleError(400, "invalid").toSyncErrorType())
        // Until Oct 2026 any message with the digit 5 counted as a server error
        assertEquals(SyncErrorType.CLIENT, googleError(400, "invalid", message = "Field 5 is invalid").toSyncErrorType())
        assertEquals(SyncErrorType.AUTHENTICATION, refused.toSyncErrorType())
    }

    private fun googleError(status: Int, reason: String?, message: String = "Google said no"): GoogleJsonResponseException {
        val details = GoogleJsonError().apply {
            code = status
            this.message = message
            if (reason != null) errors = listOf(GoogleJsonError.ErrorInfo().apply { this.reason = reason })
        }
        return GoogleJsonResponseException(
            HttpResponseException.Builder(status, "HTTP $status", HttpHeaders()).setMessage("$status $message"),
            details
        )
    }
}
