package com.boox.day.ui.dialogs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner decision 73: once, on BOOX only, when something needs BooxDay in the background, never over the welcome. */
class BooxFreezeTipTest {

    private fun show(
        isBoox: Boolean = true, alreadyShown: Boolean = false, welcomePending: Boolean = false,
        remindersOn: Boolean = false, backgroundSyncOn: Boolean = false, taskSyncOn: Boolean = false
    ) = BooxFreezeTip.shouldShow(isBoox, alreadyShown, welcomePending, remindersOn, backgroundSyncOn, taskSyncOn)

    @Test
    fun showsOnceOnBooxWhenSomethingRunsInTheBackground() {
        assertTrue(show(remindersOn = true))
        assertTrue(show(backgroundSyncOn = true))
        assertTrue(show(taskSyncOn = true))

        assertFalse("nothing in the background", show())
        assertFalse("not a BOOX tablet", show(isBoox = false, remindersOn = true))
        assertFalse("already shown", show(alreadyShown = true, remindersOn = true))
        assertFalse("first-run welcome still open", show(welcomePending = true, remindersOn = true))
    }
}
