package com.boox.day.ocr

import com.boox.day.api.ai.AiProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Page reading for search waits while AI Features is off (Oct 2026). */
class AiReadWaitReasonTest {

    @Test
    fun aiOffStopsEveryReadAndIsTheReasonShown() {
        assertEquals(AiProblem.AI_OFF, aiReadWaitReason(aiEnabled = false, hasKey = true) { true })
        // Off wins over a missing key or the monthly limit
        assertEquals(AiProblem.AI_OFF, aiReadWaitReason(aiEnabled = false, hasKey = false) { false })
    }

    @Test
    fun withAiOnTheKeyThenTheMonthlyLimit() {
        assertEquals(AiProblem.NO_KEY, aiReadWaitReason(aiEnabled = true, hasKey = false) { true })
        assertEquals(AiProblem.MONTHLY_LIMIT, aiReadWaitReason(aiEnabled = true, hasKey = true) { false })
        assertNull(aiReadWaitReason(aiEnabled = true, hasKey = true) { true })
    }

    @Test
    fun theMonthlyCountIsNotReadWhenNothingCanBeSent() {
        var asked = false
        aiReadWaitReason(aiEnabled = false, hasKey = true) { asked = true; true }
        assertFalse(asked)
    }

    @Test
    fun aPausedPageWaitsInsteadOfFailing() {
        assert(AiProblem.AI_OFF.indexingWaits)
        assertEquals("AI Features is off", AiProblem.AI_OFF.shortText)
    }
}
