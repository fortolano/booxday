package com.boox.day.ui.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings gear counts only what needs the user's action (Oct 2026, plan B3): never a screen
 * that just wasn't opened yet, and nothing for a feature that is off.
 */
class SettingsAttentionTest {

    private val allFine = AttentionInputs(
        aiEnabled = true, hasKey = true, failingPicks = 0,
        remindersOn = true, notificationsAllowed = true, reminderChannelsOn = true, batteryOk = true,
        booxNotesUsed = true, booxNotesAccess = true, accountsToReconnect = 0
    )

    private fun places(inputs: AttentionInputs) = SettingsAttentionRules.items(inputs).map { it.place }

    @Test
    fun aSetUpTabletNeedsNothing() {
        assertTrue(SettingsAttentionRules.items(allFine).isEmpty())
    }

    @Test
    fun remindersCountOnlyWhenTheyAreOnAndBlocked() {
        assertEquals(listOf(SettingsPlace.PREFERENCES), places(allFine.copy(notificationsAllowed = false)))
        assertEquals(listOf(SettingsPlace.PREFERENCES), places(allFine.copy(reminderChannelsOn = false)))
        assertEquals(listOf(SettingsPlace.PREFERENCES), places(allFine.copy(batteryOk = false)))
        // Several problems with reminders are still one thing to fix
        assertEquals(1, places(allFine.copy(notificationsAllowed = false, batteryOk = false)).size)
        assertTrue(places(allFine.copy(remindersOn = false, notificationsAllowed = false)).isEmpty())
    }

    @Test
    fun aiNeedsAKeyOnlyWhileAiFeaturesIsOn() {
        val noKey = allFine.copy(hasKey = false)
        assertEquals(listOf(SettingsPlace.AI_SETTINGS), places(noKey))
        assertTrue(places(noKey.copy(aiEnabled = false)).isEmpty())
        assertEquals(listOf(SettingsPlace.AI_SETTINGS), places(allFine.copy(failingPicks = 2)))
    }

    @Test
    fun booxNotesCountsOnlyLostAccess() {
        assertEquals(listOf(SettingsPlace.BOOX_NOTES), places(allFine.copy(booxNotesAccess = false)))
        assertTrue("never used: nothing to fix", places(allFine.copy(booxNotesUsed = false, booxNotesAccess = false)).isEmpty())
    }

    @Test
    fun accountsToReconnect() {
        val items = SettingsAttentionRules.items(allFine.copy(accountsToReconnect = 2))
        assertEquals(listOf(SettingsPlace.CALENDAR_ACCOUNTS), items.map { it.place })
        assertEquals("2 accounts need to reconnect", items[0].reason)
    }

    @Test
    fun aJumpFromTheDotIsUsedOnceByItsOwnDialog() {
        SettingsJump.request(SettingsPlace.PREFERENCES)
        assertFalse("another dialog doesn't take it", SettingsJump.consume(SettingsPlace.AI_SETTINGS))
        assertTrue(SettingsJump.consume(SettingsPlace.PREFERENCES))
        assertFalse("only once", SettingsJump.consume(SettingsPlace.PREFERENCES))
    }
}
