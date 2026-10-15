package com.boox.day.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageUnavailableException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)

class LazyEncryptedPrefs(
    private val context: Context,
    private val prefsName: String,
    private val tag: String
) {
    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var initError: Throwable? = null

    // Phase 3 (Oct 2026): a failure is retried after this long instead of being kept for the
    // life of the process
    @Volatile
    private var initErrorAtMs = 0L

    fun getOrNull(): SharedPreferences? {
        return try {
            getOrThrow()
        } catch (_: SecureStorageUnavailableException) {
            null
        }
    }

    fun getOrThrow(): SharedPreferences {
        prefs?.let { return it }

        recentError()?.let {
            throw SecureStorageUnavailableException(
                "Encrypted preferences unavailable for $prefsName",
                it
            )
        }

        synchronized(this) {
            prefs?.let { return it }
            recentError()?.let {
                throw SecureStorageUnavailableException(
                    "Encrypted preferences unavailable for $prefsName",
                    it
                )
            }

            return try {
                create()
            } catch (first: Throwable) {
                if (!isUndecryptable(first)) return fail(first)
                // A copy restored from another install (or a lost Keystore key) can't be decrypted
                // here; its contents are unrecoverable, so start the file fresh once
                Log.w(tag, "Encrypted preferences $prefsName can't be decrypted; recreating", first)
                try {
                    context.deleteSharedPreferences(prefsName)
                    create()
                } catch (second: Throwable) {
                    fail(second)
                }
            }
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also {
            prefs = it
            initError = null
        }
    }

    private fun fail(t: Throwable): Nothing {
        initError = t
        initErrorAtMs = System.currentTimeMillis()
        Log.e(tag, "Failed to initialize encrypted preferences: $prefsName", t)
        throw SecureStorageUnavailableException(
            "Encrypted preferences unavailable for $prefsName",
            t
        )
    }

    /** The last failure, if it is recent enough not to retry yet. */
    private fun recentError(): Throwable? =
        initError?.takeIf { System.currentTimeMillis() - initErrorAtMs < RETRY_AFTER_MS }

    /** A decryption failure (wrong key for the stored keyset), not e.g. a busy Keystore. */
    private fun isUndecryptable(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.any {
            it is javax.crypto.AEADBadTagException || it.javaClass.name.endsWith("InvalidProtocolBufferException")
        }

    private companion object {
        const val RETRY_AFTER_MS = 60_000L
    }
}
