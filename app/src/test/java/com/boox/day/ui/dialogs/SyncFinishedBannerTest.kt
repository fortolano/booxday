package com.boox.day.ui.dialogs

import com.boox.day.sync.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner's choice (31 Oct 2026): a sync with a failed account says so, never "all caught up". */
class SyncFinishedBannerTest {

    @Test
    fun `an account that needs a new sign-in makes the banner a warning with its message`() {
        val banner = syncFinishedBanner(
            listOf(
                SyncResult.Success(eventCount = 5, newEvents = 2, updatedEvents = 3),
                SyncResult.NotSignedIn("Google needs you to sign in again")
            ),
            funMessage = "All caught up."
        )

        assertTrue(banner.failed)
        assertEquals("Sync didn't finish", banner.title)
        assertEquals("Google needs you to sign in again", banner.subtext)
    }

    @Test
    fun `every account synced keeps the fun message and the counts`() {
        val banner = syncFinishedBanner(
            listOf(SyncResult.Success(eventCount = 5, newEvents = 2, updatedEvents = 3)),
            funMessage = "All caught up."
        )

        assertFalse(banner.failed)
        assertEquals("All caught up.", banner.title)
        assertEquals("2 new, 3 updated", banner.subtext)
    }
}
