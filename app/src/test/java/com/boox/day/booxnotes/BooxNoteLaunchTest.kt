package com.boox.day.booxnotes

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33, 35, 36], application = Application::class)
class BooxNoteLaunchTest {
    private class LaunchContext(val failures: List<RuntimeException> = emptyList()) :
        ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempts.add(intent)
            failures.getOrNull(attempts.lastIndex)?.let { throw it }
        }
    }

    @Test fun openingAnExistingNoteTargetsItsDocumentAndRetainsLastPageSelection() {
        val context = LaunchContext()
        val id = "note-\"quoted\"-é"
        BooxNotesIntegration(context).openNote(id)
        val intent = context.attempts.single()
        assertEquals("com.onyx.android.note", intent.component!!.packageName)
        assertEquals("com.onyx.android.note.note.ui.ScribbleActivity", intent.component!!.className)
        val bean = JSONObject(intent.getStringExtra("OPEN_NOTE_BEAN")!!)
        assertEquals(id, bean.getString("documentId"))
        assertEquals(-1, bean.getInt("pageIndex"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun missingDirectActivityFallsBackWithBothMiddlewareFormats() {
        val context = LaunchContext(listOf(ActivityNotFoundException("older firmware")))
        BooxNotesIntegration(context).openNote("existing-note")
        assertEquals(2, context.attempts.size)
        val intent = context.attempts.last()
        assertEquals("com.onyx.action.MIDDLEWARE", intent.action)
        assertEquals("existing-note", intent.getStringExtra("note_document_id"))
        assertEquals("note", intent.getStringExtra("type"))
        val route = JSONObject(intent.getStringExtra("json")!!)
        assertEquals("OPEN_NOTE", route.getString("action"))
        assertEquals("existing-note", route.getString("jumpPath"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_HISTORY != 0)
    }

    @Test fun restrictedDirectActivityUsesTheExistingMiddlewareFallback() {
        val context = LaunchContext(listOf(SecurityException("activity not exported")))
        BooxNotesIntegration(context).openNote("existing-note")
        assertEquals("com.onyx.action.MIDDLEWARE", context.attempts.last().action)
        assertEquals(2, context.attempts.size)
    }

    @Test fun unavailableRoutesGiveVisibleFailureFeedback() {
        val context = LaunchContext(listOf(ActivityNotFoundException(), ActivityNotFoundException()))
        BooxNotesIntegration(context).openNote("existing-note")
        assertEquals(2, context.attempts.size)
        assertEquals("Could not open note in Boox Notes.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun aMissingDocumentIdCannotAccidentallyCreateOrOpenAnUnrelatedNote() {
        val context = LaunchContext()
        BooxNotesIntegration(context).openNote("  ")
        assertTrue(context.attempts.isEmpty())
        assertEquals("Could not open note in Boox Notes.", ShadowToast.getTextOfLatestToast())
    }
}
