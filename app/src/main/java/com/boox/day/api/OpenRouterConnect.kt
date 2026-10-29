package com.boox.day.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.ProjectInfo
import com.boox.day.security.LazyEncryptedPrefs
import com.boox.day.security.UserOpenRouterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * "Connect OpenRouter" (Oct 2026): OpenRouter's sign-in for apps (OAuth PKCE), so users don't copy
 * a key on e-ink. Plan: docs/AI_MODEL_PICKER_PLAN.md, section 8.
 *
 * 1. [begin] makes a one-time secret (verifier) and a state, keeps them encrypted for 10 minutes,
 *    and returns OpenRouter's sign-in address for the browser.
 * 2. OpenRouter sends the browser to the return page ([ProjectInfo.CONNECT_PAGE_URL]), whose
 *    button opens `booxday-connect://openrouter?code=...&state=...` (OpenRouterConnectActivity).
 * 3. [finishFromLink] checks the state and trades the code for a key; [finishWithCode] does the
 *    same for a code the user pasted from that page. The code is useless without the verifier.
 *
 * The key is checked and saved like a pasted one ([OpenRouterKeyCheck], [UserOpenRouterKey]).
 * Never log the code, the verifier or the key.
 */
object OpenRouterConnect {
    private const val TAG = "OpenRouterConnect"

    const val AUTH_URL = "https://openrouter.ai/auth"
    const val EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    const val CALLBACK_SCHEME = "booxday-connect"
    const val CALLBACK_HOST = "openrouter"
    const val KEY_LABEL = "BooxDay"

    /**
     * How long a started connection counts as pending, to tell a pasted code from a key (Oct 2026).
     * Expiry itself is OpenRouter's answer: its code lasts 10 minutes from when it is issued, which
     * can be long after the Connect tap when the user first creates an account.
     */
    const val PENDING_WINDOW_MS = 60 * 60 * 1000L

    private const val PREFS = "openrouter_connect"
    private const val PREF_VERIFIER = "verifier"
    private const val PREF_STATE = "state"
    private const val PREF_CREATED = "created_at_ms"
    private const val PREF_CODE = "code"

    /**
     * A sign-in in progress: the one-time secret and the state sent to OpenRouter. [code] is kept
     * after a network error, so Try Again can finish without another trip to the browser.
     */
    data class Pending(val verifier: String, val state: String, val createdAtMs: Long, val code: String? = null)

    sealed class Status {
        object Idle : Status()
        object Connecting : Status()

        /** Key saved. [checked] = OpenRouter answered the key check (it can be offline). */
        data class Connected(val checked: Boolean, val isFreeTier: Boolean, val limitRemaining: Double?) : Status()

        /** [canRetry]: the code is kept and [retry] can try again (network or server trouble). */
        data class Failed(val message: String, val canRetry: Boolean = false) : Status()
    }

    /** What the last connection did; the key card shows it (Compose state). */
    var status: Status by mutableStateOf<Status>(Status.Idle)
        private set

    // ═══════════════════════════════════════════════════════════════════════════
    // RULES (no Android, unit-tested)
    // ═══════════════════════════════════════════════════════════════════════════

    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes as base64url: 43 characters, within PKCE's 43 to 128 (RFC 7636). */
    internal fun newSecret(random: SecureRandom = SecureRandom()): String =
        ByteArray(32).also { random.nextBytes(it) }.let { base64Url.encodeToString(it) }

    /** S256 challenge: base64url of the verifier's SHA-256 (RFC 7636). */
    internal fun challengeFor(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    internal fun authUrl(callbackUrl: String, challenge: String, state: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "$AUTH_URL?callback_url=${enc(callbackUrl)}" +
            "&code_challenge=${enc(challenge)}&code_challenge_method=S256" +
            "&key_label=${enc(KEY_LABEL)}&state=${enc(state)}"
    }

    /** `code` and `state` from a `booxday-connect://openrouter?...` link; null when it isn't one. */
    internal fun parseLink(link: String?): Map<String, String>? {
        if (link == null) return null
        val prefix = "$CALLBACK_SCHEME://$CALLBACK_HOST"
        if (!link.startsWith(prefix, ignoreCase = true)) return null
        val query = link.substringAfter('?', "").substringBefore('#')
        return query.split('&')
            .filter { it.isNotEmpty() }
            .associate { part ->
                val name = URLDecoder.decode(part.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                name to value
            }
    }

    /** True for text that is an OpenRouter key rather than a connection code. */
    fun looksLikeKey(text: String): Boolean = text.trim().startsWith("sk-or-")

    /** Why a returned code can't be used with [pending], or null when it can. */
    internal fun problemWith(pending: Pending?, code: String?, state: String?, checkState: Boolean): String? = when {
        code.isNullOrBlank() -> "OpenRouter didn't send a connection code. Tap Connect OpenRouter again."
        pending == null -> "This connection wasn't started on this tablet, or it already finished. Tap Connect OpenRouter again."
        checkState && state != pending.state -> "This connection doesn't match the one started here. Tap Connect OpenRouter again."
        else -> null
    }

    /** What an exchange answer means; network and server trouble can be tried again with the same code. */
    internal fun exchangeResultFor(httpCode: Int, body: String): Exchange = when {
        httpCode in 200..299 -> keyFromExchange(body)?.let { Exchange.Key(it) }
            ?: Exchange.Error("OpenRouter's answer had no key. Tap Connect OpenRouter again.", retryable = false)
        httpCode == 403 -> Exchange.Error(
            "OpenRouter says this connection code has expired or was already used. Tap Connect OpenRouter again.",
            retryable = false
        )
        httpCode >= 500 -> Exchange.Error("OpenRouter is having trouble right now. Tap Try Again in a moment.", retryable = true)
        else -> Exchange.Error("OpenRouter couldn't finish the connection (error $httpCode). Tap Connect OpenRouter again.", retryable = false)
    }

    internal fun exchangeBody(code: String, verifier: String): String = buildJsonObject {
        put("code", code)
        put("code_verifier", verifier)
        put("code_challenge_method", "S256")
    }.toString()

    /** The key from OpenRouter's exchange answer, or null. */
    internal fun keyFromExchange(body: String): String? = try {
        Json.parseToJsonElement(body).jsonObject["key"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FLOW
    // ═══════════════════════════════════════════════════════════════════════════

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var memoryPending: Pending? = null

    @Volatile
    private var store: LazyEncryptedPrefs? = null

    private fun prefs(context: Context): SharedPreferences? {
        val lazy = store ?: synchronized(this) {
            store ?: LazyEncryptedPrefs(context.applicationContext, PREFS, TAG).also { store = it }
        }
        return lazy.getOrNull()
    }

    /** Starts a connection; returns the address to open in the browser. Runs off the main thread. */
    suspend fun begin(context: Context): String = withContext(Dispatchers.IO) {
        val pending = Pending(newSecret(), newSecret(), System.currentTimeMillis())
        memoryPending = pending
        // Encrypted so a restart while the browser is open doesn't lose it; memory only otherwise
        prefs(context)?.edit()
            ?.putString(PREF_VERIFIER, pending.verifier)
            ?.putString(PREF_STATE, pending.state)
            ?.putLong(PREF_CREATED, pending.createdAtMs)
            ?.apply()
        status = Status.Idle
        Log.i(TAG, "Connect OpenRouter started")
        authUrl(ProjectInfo.CONNECT_PAGE_URL, challengeFor(pending.verifier), pending.state)
    }

    /** Finishes from the return page's link. Keeps running if the screen that started it closes. */
    fun finishFromLinkAsync(context: Context, link: String?): Deferred<Status> {
        val appContext = context.applicationContext
        return scope.async { finish(appContext, parseLink(link), checkState = true) }
    }

    /** True while a connection started here can still be finished. Off the main thread. */
    suspend fun hasPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pending = loadPending(context.applicationContext)
        pending != null && System.currentTimeMillis() - pending.createdAtMs <= PENDING_WINDOW_MS
    }

    /** Finishes with a connection code the user pasted from the return page. */
    suspend fun finishWithCode(context: Context, code: String): Status =
        withContext(Dispatchers.IO) {
            finish(context.applicationContext, mapOf("code" to code.trim()), checkState = false)
        }

    /** Try Again after network or server trouble: the same code, no browser. */
    suspend fun retry(context: Context): Status = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val code = loadPending(app)?.code
            ?: return@withContext fail("This connection can't be finished any more. Tap Connect OpenRouter again.")
        finish(app, mapOf("code" to code), checkState = false)
    }

    /** Forgets a started connection (the browser didn't open). */
    suspend fun discardPending(context: Context) = withContext(Dispatchers.IO) { clearPending(context.applicationContext) }

    /** Shown on the key card when a connection couldn't even start. */
    fun reportProblem(message: String) {
        fail(message)
    }

    // One exchange at a time: the return screen can run twice if Android recreates it
    private val finishing = Mutex()

    private suspend fun finish(context: Context, params: Map<String, String>?, checkState: Boolean): Status = finishing.withLock {
        val pending = loadPending(context)
        // Already done by an earlier run of the same link: keep "Connected"
        if (pending == null && status is Status.Connected) return@withLock status
        status = Status.Connecting
        problemWith(pending, params?.get("code"), params?.get("state"), checkState)?.let {
            Log.w(TAG, "Connection not finished: $it")
            return@withLock fail(it)
        }
        val code = params!!.getValue("code")
        val key = when (val result = exchange(code, pending!!.verifier)) {
            is Exchange.Key -> result.key
            is Exchange.Error -> {
                if (result.retryable) {
                    keepCode(context, pending, code)
                } else {
                    clearPending(context)
                }
                return@withLock fail(result.message, canRetry = result.retryable)
            }
        }
        return@withLock when (val check = OpenRouterKeyCheck.check(key)) {
            OpenRouterKeyCheck.Result.Rejected -> {
                clearPending(context)
                fail("OpenRouter didn't accept the new key. Tap Connect OpenRouter again.")
            }
            is OpenRouterKeyCheck.Result.Accepted -> saved(context, key, Status.Connected(true, check.isFreeTier, check.limitRemaining))
            is OpenRouterKeyCheck.Result.Unreachable -> saved(context, key, Status.Connected(false, false, null))
        }
    }

    private fun saved(context: Context, key: String, connected: Status.Connected): Status {
        UserOpenRouterKey.save(context, key)
        clearPending(context)
        Log.i(TAG, "OpenRouter connected, key saved")
        status = connected
        return connected
    }

    private fun fail(message: String, canRetry: Boolean = false): Status {
        val failed = Status.Failed(message, canRetry)
        status = failed
        return failed
    }

    internal sealed class Exchange {
        class Key(val key: String) : Exchange()
        class Error(val message: String, val retryable: Boolean) : Exchange()
    }

    /** Trades the code for a key (`POST /api/v1/auth/keys`). */
    private fun exchange(code: String, verifier: String): Exchange {
        return try {
            val request = Request.Builder()
                .url(EXCHANGE_URL)
                .post(exchangeBody(code, verifier).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                exchangeResultFor(response.code, response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Code exchange failed: ${e.javaClass.simpleName}")
            Exchange.Error("Couldn't reach OpenRouter. Check the Wi-Fi, then tap Try Again.", retryable = true)
        }
    }

    private fun keepCode(context: Context, pending: Pending, code: String) {
        memoryPending = pending.copy(code = code)
        prefs(context)?.edit()?.putString(PREF_CODE, code)?.apply()
    }

    private fun loadPending(context: Context): Pending? {
        memoryPending?.let { return it }
        val prefs = prefs(context) ?: return null
        val verifier = prefs.getString(PREF_VERIFIER, null) ?: return null
        val state = prefs.getString(PREF_STATE, null) ?: return null
        return Pending(verifier, state, prefs.getLong(PREF_CREATED, 0L), prefs.getString(PREF_CODE, null))
            .also { memoryPending = it }
    }

    private fun clearPending(context: Context) {
        memoryPending = null
        prefs(context)?.edit()?.clear()?.apply()
    }
}
