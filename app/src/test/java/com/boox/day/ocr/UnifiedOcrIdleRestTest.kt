package com.boox.day.ocr

import com.boox.day.ocr.UnifiedOcrIdleProcessor.Companion.NO_WORK_BACKOFF_MS
import com.boox.day.ocr.UnifiedOcrIdleProcessor.Companion.shouldRest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When idle OCR passes rest after a pass that found no work (empty queue, or no key / AI budget). */
class UnifiedOcrIdleRestTest {

    private val activity = 1_000_000L
    private val pass = activity + 10_000L

    @Test
    fun noRestBeforeAnyNoWorkPass() {
        assertFalse(shouldRest(0L, activity, pass, waitingForKey = false, hasKeyNow = false))
    }

    @Test
    fun restsAfterNoWorkPassUntilUserIsActive() {
        assertTrue(shouldRest(pass, activity, pass + 2_000L, waitingForKey = true, hasKeyNow = false))
        assertTrue(shouldRest(pass, activity, pass + NO_WORK_BACKOFF_MS - 1, waitingForKey = false, hasKeyNow = true))
    }

    @Test
    fun userActivityAfterThePassEndsTheRest() {
        assertFalse(shouldRest(pass, pass + 1, pass + 2_000L, waitingForKey = true, hasKeyNow = false))
    }

    @Test
    fun restEndsAfterTheBackoff() {
        assertFalse(shouldRest(pass, activity, pass + NO_WORK_BACKOFF_MS, waitingForKey = false, hasKeyNow = false))
    }

    @Test
    fun savingAKeyEndsARestThatWaitedForOne() {
        assertFalse(shouldRest(pass, activity, pass + 2_000L, waitingForKey = true, hasKeyNow = true))
    }

    @Test
    fun aKeyDoesNotEndARestForOtherReasons() {
        // Empty queue or monthly AI limit: a key was already there, so it changes nothing
        assertTrue(shouldRest(pass, activity, pass + 2_000L, waitingForKey = false, hasKeyNow = true))
    }
}
