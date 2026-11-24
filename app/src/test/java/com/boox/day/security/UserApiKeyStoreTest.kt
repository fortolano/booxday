package com.boox.day.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies Decision D6 / Phase 1 C5(a):
 * - New writes fail closed (never write plaintext) when encrypted storage is unavailable.
 * - Deleting a key while encrypted storage is unavailable records a tombstone so the key never resurrects.
 * - Read-only migration from legacy api_key_prefs (used by adb setup script) still migrates into encrypted storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UserApiKeyStoreTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var fakeSecure: SharedPreferences
    private lateinit var legacy: SharedPreferences

    @Before
    fun setUp() {
        fakeSecure = context.getSharedPreferences("test_secure_api_key", Context.MODE_PRIVATE)
        legacy = context.getSharedPreferences("api_key_prefs", Context.MODE_PRIVATE)
        fakeSecure.edit().clear().commit()
        legacy.edit().clear().commit()
        UserApiKeyStore.securePrefsOverride = { fakeSecure }
    }

    @After
    fun tearDown() {
        UserApiKeyStore.securePrefsOverride = null
        fakeSecure.edit().clear().commit()
        legacy.edit().clear().commit()
    }

    @Test
    fun savingFailsClosedWhenEncryptedStorageIsUnavailable() {
        UserApiKeyStore.securePrefsOverride = { null }

        val saved = UserApiKeyStore.setOpenRouterKey(context, "sk-or-secret-123456")

        assertFalse("must refuse plaintext fallback on write", saved)
        assertNull("must not write key to plaintext legacy prefs", legacy.getString("openrouter_api_key", null))
        assertNull(UserApiKeyStore.getOpenRouterKey(context))
    }

    @Test
    fun deletingWhileEncryptedStorageUnavailableTombstonesAndPurgesOnRecovery() {
        assertTrue(UserApiKeyStore.setOpenRouterKey(context, "sk-or-secret-123456"))
        assertEquals("sk-or-secret-123456", UserApiKeyStore.getOpenRouterKey(context))

        // Simulate Keystore outage during key removal
        UserApiKeyStore.securePrefsOverride = { null }
        assertTrue(UserApiKeyStore.setOpenRouterKey(context, null))
        assertNull(UserApiKeyStore.getOpenRouterKey(context))

        // Keystore recovers: stale encrypted copy must be purged, never resurrected
        UserApiKeyStore.securePrefsOverride = { fakeSecure }
        assertNull(UserApiKeyStore.getOpenRouterKey(context))
        assertNull("encrypted copy purged after recovery", fakeSecure.getString("openrouter_api_key", null))
    }

    @Test
    fun legacyPlaintextKeyMigratesIntoEncryptedStorageOnRead() {
        legacy.edit().putString("openrouter_api_key", "sk-or-adb-migrated").commit()

        assertEquals("sk-or-adb-migrated", UserApiKeyStore.getOpenRouterKey(context))
        assertEquals("sk-or-adb-migrated", fakeSecure.getString("openrouter_api_key", null))
        assertNull("plain copy removed after migration", legacy.getString("openrouter_api_key", null))
    }
}
