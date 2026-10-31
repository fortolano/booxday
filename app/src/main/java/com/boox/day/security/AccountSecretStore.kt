package com.boox.day.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Passwords of accounts that sign in with one: CalDAV app passwords, from the CalDAV stage of task
 * sync on (Oct 2026). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 1.7.
 *
 * - AES-256-GCM with a key that stays in the Android Keystore (it can't be exported); a fresh IV
 *   per value, and the account id as associated data, so a value copied to another account's
 *   entry doesn't decrypt.
 * - Ciphertext in its own preferences file, which Android backup and device transfer leave out
 *   (res/xml/backup_rules.xml, data_extraction_rules.xml): restored elsewhere, the key isn't there.
 * - **No plain-text fallback** (unlike [UserApiKeyStore]): when the Keystore fails, [save] fails
 *   and the caller tells the user. Not EncryptedSharedPreferences: androidx deprecated it.
 * - Never log a value; never put one in settings JSON, backups or crash reports.
 *
 * Call off the main thread (the Keystore can be slow).
 */
class AccountSecretStore internal constructor(
    private val prefs: SharedPreferences,
    private val keys: KeySource
) {
    /** Where the encryption key comes from: the Android Keystore in the app, a stand-in in tests. */
    internal fun interface KeySource {
        fun key(): SecretKey
    }

    /** Encrypts and saves [secret] for [accountId]. A failure saves nothing. */
    fun save(accountId: String, secret: String): Result<Unit> = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())  // the cipher picks the IV (the Keystore insists)
        cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val stored = encode(cipher.iv) + SEPARATOR + encode(sealed)
        check(prefs.edit().putString(entry(accountId), stored).commit()) { "could not write $PREFS_NAME" }
    }.onFailure { Log.e(TAG, "Account secret not saved: ${it.javaClass.simpleName}") }

    /** The secret saved for [accountId], or null when there is none or it can't be decrypted. */
    fun read(accountId: String): String? {
        val stored = prefs.getString(entry(accountId), null) ?: return null
        return try {
            val (iv, sealed) = stored.split(SEPARATOR, limit = 2).let { decode(it[0]) to decode(it[1]) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key gone (Keystore reset), damaged entry, or another account's value: sign in again
            Log.w(TAG, "Account secret unreadable for $accountId: ${e.javaClass.simpleName}")
            null
        }
    }

    fun delete(accountId: String) {
        prefs.edit().remove(entry(accountId)).commit()
    }

    private fun entry(accountId: String) = "secret_$accountId"
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val TAG = "AccountSecretStore"
        /** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS_NAME = "account_secrets"
        private const val KEY_ALIAS = "booxday_account_secrets_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val SEPARATOR = ":"

        @Volatile
        private var instance: AccountSecretStore? = null

        fun get(context: Context): AccountSecretStore = instance ?: synchronized(this) {
            instance ?: AccountSecretStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                KeySource(::keystoreKey)
            ).also { instance = it }
        }

        /** The app's key, made on first use; it never leaves the Keystore. */
        private fun keystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
