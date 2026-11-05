package com.boox.day.booxnotes

import android.app.Application
import com.boox.day.data.db.BooxNoteIndex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BOOX's app shows and saves the name it is handed. Without one it shows "Create notebook"; with a name
 * BooxDay made up, it would rename the note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BooxNoteIntentsTest {

    private fun bean(title: String?) = JSONObject(BooxNoteIntents.direct("doc", title).getStringExtra("OPEN_NOTE_BEAN")!!)

    private fun note(title: String, source: String?) =
        BooxNoteIndex("doc", title, "2026-01-14", 1L, 1, "[]", 1L, 1L, titleSource = source)

    @Test
    fun theNotesOwnNameIsPassedExactly() {
        val name = "Stake \"Presidency\" / Meeting – é"
        assertEquals(name, bean(name).getString("title"))
        assertEquals("doc", bean(name).getString("documentId"))
    }

    @Test
    fun noNameMeansNoTitleKey() {
        assertFalse(bean(null).has("title"))
        assertFalse(bean(" ").has("title"))
    }

    @Test
    fun aNameBooxDayMadeUpIsNeverPassed() {
        assertEquals("Budget", note("Budget", "native").nameInBoox)
        assertEquals("Budget", note("Budget", "provider").nameInBoox)
        assertNull(note("Jan 14, 2026", "derived").nameInBoox)
        assertNull(note("Boox Note • Undated • #abc123", "generated").nameInBoox)
        assertNull(note("Unknown origin", null).nameInBoox)
    }
}
