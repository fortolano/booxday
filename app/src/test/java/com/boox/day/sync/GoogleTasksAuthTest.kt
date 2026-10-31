package com.boox.day.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Google Tasks permission is asked for on its own, for one account, and never joins the
 * calendar sign-in Google verified on 28 Oct 2026 (task sync step 2.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleTasksAuthTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the tasks scope is Google's read-write Tasks scope`() {
        assertEquals("https://www.googleapis.com/auth/tasks", GoogleTasksAuth.TASKS_SCOPE)
    }

    @Test
    fun `the calendar sign-in never asks for Tasks`() {
        assertFalse(GoogleTasksAuth.TASKS_SCOPE in GoogleAuthManager.CALENDAR_SCOPES)
        val calendarCredential = GoogleAuthManager(context).getCredentialForAccount("me@gmail.com")
        assertFalse(calendarCredential.scope.contains("auth/tasks"))
    }

    @Test
    fun `the request asks for Tasks only, for the chosen account`() {
        val request = GoogleTasksAuth.request("me@gmail.com")

        assertEquals(listOf(Scope(GoogleTasksAuth.TASKS_SCOPE)), request.requestedScopes)
        assertEquals("me@gmail.com", request.account?.name)
        assertEquals("com.google", request.account?.type)
    }

    @Test
    fun `Tasks calls use that account's own token, for Tasks only`() {
        val credential = GoogleTasksAuth.credential(context, "me@gmail.com")

        assertEquals("me@gmail.com", credential.selectedAccountName)
        assertTrue(credential.scope.contains("auth/tasks"))
        assertFalse(credential.scope.contains("calendar"))
    }
}
