package com.boox.day.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wipe All Data (Oct 2026): the sleep image goes before Android clears the data, and a refusal is reported. */
class AppDataWipeTest {

    @Test
    fun cleanupRunsBeforeTheClear() {
        val order = mutableListOf<String>()
        AppDataWipe.run(beforeClear = { order += "cleanup" }, clear = { order += "clear"; true })
        assertEquals(listOf("cleanup", "clear"), order)
    }

    @Test
    fun refusalIsPassedOn() {
        assertFalse(AppDataWipe.run(beforeClear = {}, clear = { false }))
        assertTrue(AppDataWipe.run(beforeClear = {}, clear = { true }))
    }
}
