package com.boox.day.update

import com.boox.day.update.AppUpdateGate.Announcement
import org.junit.Assert.assertEquals
import org.junit.Test

/** The first start after an update shows that release's notes, once (Oct 2026). */
class AppUpdateAnnouncementTest {

    private val notes = listOf("Search: finds accents.", "Voice: free Google speech.")

    @Test
    fun anInAppUpdateShowsTheNotesItSaved() {
        assertEquals(
            Announcement.Notes("1.0.38", notes),
            AppUpdateGate.announcementFor(37, 38, "1.0.38", savedVersion = "1.0.38", savedNotes = notes)
        )
        // The tag's "v" and the version name's form don't matter
        assertEquals(
            Announcement.Notes("1.0.38", notes),
            AppUpdateGate.announcementFor(37, 38, "1.0.38", savedVersion = "v1.0.38", savedNotes = notes)
        )
    }

    @Test
    fun anUpdateByHandFetchesTheNotes() {
        assertEquals(Announcement.FetchNotes, AppUpdateGate.announcementFor(37, 38, "1.0.38", null, emptyList()))
        // Notes saved for another version (an update that failed, then one by hand)
        assertEquals(Announcement.FetchNotes, AppUpdateGate.announcementFor(36, 38, "1.0.38", "1.0.37", notes))
        // Saved, but the release had no bullets
        assertEquals(Announcement.FetchNotes, AppUpdateGate.announcementFor(37, 38, "1.0.38", "1.0.38", emptyList()))
    }

    @Test
    fun onlyOncePerVersionAndNotOnAFreshInstall() {
        // The version code is stored at the first start, so the next start finds the same code
        assertEquals(Announcement.None, AppUpdateGate.announcementFor(38, 38, "1.0.38", "1.0.38", notes))
        // Fresh install: the welcome screens cover it
        assertEquals(Announcement.None, AppUpdateGate.announcementFor(0, 38, "1.0.38", null, emptyList()))
        // An older version installed over a newer one
        assertEquals(Announcement.None, AppUpdateGate.announcementFor(39, 38, "1.0.38", null, emptyList()))
    }
}
