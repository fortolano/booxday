package com.boox.day.security

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Account passwords (CalDAV app passwords, later): encrypted, kept per account, never written in
 * plain text, left out of Android backup. Robolectric has no Android Keystore, so the key comes
 * from a software AES key; the app's key comes from the Keystore (AccountSecretStore.get).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountSecretStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("account_secrets_test", Context.MODE_PRIVATE) }
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store by lazy { AccountSecretStore(prefs, { key }) }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun aSavedPasswordReadsBackForItsAccountOnly() {
        assertTrue(store.save("acc-icloud", "abcd-efgh-ijkl-mnop").isSuccess)
        assertTrue(store.save("acc-nextcloud", "other-secret").isSuccess)

        assertEquals("abcd-efgh-ijkl-mnop", store.read("acc-icloud"))
        assertEquals("other-secret", store.read("acc-nextcloud"))
        assertNull(store.read("acc-unknown"))
    }

    @Test
    fun theStoredValueIsNotThePassword() {
        store.save("acc-icloud", "abcd-efgh-ijkl-mnop")

        val everything = prefs.all.values.joinToString()
        assertFalse(everything.contains("abcd-efgh-ijkl-mnop"))
        assertFalse(everything.contains("abcd"))
    }

    @Test
    fun aValueCopiedToAnotherAccountDoesNotDecrypt() {
        store.save("acc-icloud", "abcd-efgh-ijkl-mnop")
        val sealed = prefs.getString("secret_acc-icloud", null)
        prefs.edit().putString("secret_acc-other", sealed).commit()

        assertNull(store.read("acc-other"))
    }

    @Test
    fun aDamagedEntryOrALostKeyReadsAsNoPassword() {
        store.save("acc-icloud", "abcd-efgh-ijkl-mnop")
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull("the Keystore was reset", AccountSecretStore(prefs, { otherKey }).read("acc-icloud"))

        prefs.edit().putString("secret_acc-icloud", "not:base64!").commit()
        assertNull(store.read("acc-icloud"))
    }

    @Test
    fun whenTheKeystoreFailsNothingIsSaved() {
        val broken = AccountSecretStore(prefs, { throw IllegalStateException("Keystore unavailable") })

        assertTrue(broken.save("acc-icloud", "abcd-efgh-ijkl-mnop").isFailure)
        assertTrue("no plain-text fallback", prefs.all.isEmpty())
    }

    @Test
    fun deleteRemovesTheAccountsPassword() {
        store.save("acc-icloud", "abcd-efgh-ijkl-mnop")
        store.delete("acc-icloud")

        assertNull(store.read("acc-icloud"))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun androidBackupAndDeviceTransferLeaveThePasswordsOut() {
        val exclusion = """<exclude domain="sharedpref" path="${AccountSecretStore.PREFS_NAME}.xml" />"""
        val backupRules = resource("backup_rules.xml")
        val extractionRules = resource("data_extraction_rules.xml")

        assertTrue(backupRules.contains(exclusion))
        assertEquals("cloud backup and device transfer", 2, extractionRules.split(exclusion).size - 1)
    }

    private fun resource(name: String): String {
        val path = "src/main/res/xml/$name"
        return listOf(File(path), File("app/$path")).first { it.exists() }.readText()
    }
}
