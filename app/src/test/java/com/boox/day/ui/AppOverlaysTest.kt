package com.boox.day.ui

import com.boox.day.ui.dialogs.SupportPopup
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies Phase 1 H14 immediate fix: SupportPopup blocks raw stylus drawing via AppOverlays. */
class AppOverlaysTest {

    @After
    fun tearDown() {
        SupportPopup.close()
    }

    @Test
    fun supportPopupSetsAppOverlaysOpen() {
        SupportPopup.close()
        assertFalse(AppOverlays.isOpen)

        SupportPopup.open()
        assertTrue(AppOverlays.isOpen)

        SupportPopup.close()
        assertFalse(AppOverlays.isOpen)
    }
}
