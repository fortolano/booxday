package com.boox.day.startup

import com.boox.day.data.datastore.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Phase 1 H11 / Decision D7:
 * - Room read/decode failure returns Outcome.Failed instead of falling back to defaults.
 * - OpenRouter key read failures never fail settings loading.
 * - Missing settings row on a fresh install returns Outcome.Loaded(fromStorage = false).
 */
class StartupSettingsLoaderTest {

    @Test
    fun roomDecodeFailureReturnsFailedInsteadOfDefaults() {
        val outcome = StartupSettingsLoader.loadWith(
            initialSettings = AppSettings(version = 1),
            readApiKey = { "sk-or-test" },
            readStoredSettings = { throw IllegalStateException("corrupt json") },
        )
        assertTrue(outcome is StartupSettingsLoader.Outcome.Failed)
    }

    @Test
    fun keyReadFailureDoesNotBlockStoredSettingsLoad() {
        val saved = AppSettings(version = 1, showWelcome = false, tasksFeedEnabled = true)
        val outcome = StartupSettingsLoader.loadWith(
            initialSettings = AppSettings(version = 1, showWelcome = true),
            readApiKey = { throw SecurityException("keystore locked") },
            readStoredSettings = { saved },
        )
        assertTrue(outcome is StartupSettingsLoader.Outcome.Loaded)
        val loaded = outcome as StartupSettingsLoader.Outcome.Loaded
        assertTrue(loaded.fromStorage)
        assertEquals(saved, loaded.settings)
    }

    @Test
    fun missingRowOnFreshInstallUsesInitialDefaultsAndMergesKey() {
        val outcome = StartupSettingsLoader.loadWith(
            initialSettings = AppSettings(version = 1, showWelcome = true),
            readApiKey = { "sk-or-fresh" },
            readStoredSettings = { null },
        )
        assertTrue(outcome is StartupSettingsLoader.Outcome.Loaded)
        val loaded = outcome as StartupSettingsLoader.Outcome.Loaded
        assertFalse(loaded.fromStorage)
        assertEquals("sk-or-fresh", loaded.settings.userOpenRouterApiKey)
    }
}
