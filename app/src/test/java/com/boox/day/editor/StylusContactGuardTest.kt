package com.boox.day.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusContactGuardTest {
    @Test fun aLongContactDoesNotDependOnTimeoutsOrMoveCallbacks() {
        val contact = StylusContactGuard()
        assertFalse(contact.isActive)
        contact.begin("Memos#1")
        // No timer or move callback can expire contact while the pen remains down.
        assertTrue(contact.isActive)
        assertTrue(contact.end("Memos#1"))
        assertFalse(contact.isActive)
    }

    @Test fun detachingThePreviousCanvasCannotReleaseTheNewCanvasContact() {
        val contact = StylusContactGuard()
        contact.begin("Memos#1")
        contact.begin("Tasks#2")
        assertFalse(contact.end("Memos#1"))
        assertTrue(contact.isActive)
        assertTrue(contact.end("Tasks#2"))
        assertFalse(contact.isActive)
    }

    @Test fun completedListAllowsImmediateBlitBeforeTheFollowingEndCallback() {
        val contact = StylusContactGuard()
        contact.begin("Memos#1")
        assertTrue(contact.end("Memos#1")) // completed point list
        assertFalse(contact.isActive) // required first-stroke blit can proceed
        assertFalse(contact.end("Memos#1")) // SDK's subsequent onEnd
        assertFalse(contact.isActive)
        contact.begin("Memos#1")
        assertTrue(contact.isActive) // next stroke is protected again
    }

    @Test fun lifecycleCleanupReleasesAnInterruptedContact() {
        val contact = StylusContactGuard()
        contact.begin("Memos#1")
        assertTrue(contact.end("Memos#1")) // surface destruction or detach
        assertFalse(contact.isActive)
    }
}
