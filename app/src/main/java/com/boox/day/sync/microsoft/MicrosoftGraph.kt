package com.boox.day.sync.microsoft

import com.boox.day.sync.outlook.AccountNotFoundException
import com.boox.day.sync.outlook.OutlookAuthManager
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** What each feature asks Microsoft for on top of the calendar sign-in (owner decisions 79-88, Nov 2026). */
internal object MicrosoftScopes {
    /** Microsoft To Do, for Task Sync. */
    val TASKS = listOf("Tasks.ReadWrite")

    /** BooxDay's own OneDrive folder (Apps/<app name>) and nothing else; User.Read gives the account's address. */
    val BACKUP = listOf("User.Read", "Files.ReadWrite.AppFolder")
}

/** A Graph call's failure, as To Do and OneDrive backup react to it. */
internal sealed class GraphException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No token without the user: permission not given yet, withdrawn, or the account is gone. */
    class SignInNeeded(cause: Throwable? = null) : GraphException("Sign in again", cause)

    /** Too many requests or Microsoft busy: wait [retryAfterMs] (Microsoft's own figure when it sends one). */
    class Busy(val retryAfterMs: Long?) : GraphException("Microsoft is busy")

    /** No connection, a timeout, or the reply lost on the way. */
    class Offline(cause: Throwable? = null) : GraphException("Offline", cause)

    /** Any other refusal; [code] is Graph's error code ("ErrorItemNotFound", "itemNotFound", ...). */
    class Http(val status: Int, val code: String?) :
        GraphException("Microsoft answered $status${code?.let { " ($it)" } ?: ""}")
}

/**
 * Microsoft Graph for Microsoft To Do and OneDrive backup (Nov 2026, owner decisions 79-88): one
 * account's token for one feature's scopes, a fresh token once when Graph refuses the cached one,
 * and Graph's failures as [GraphException]. The Outlook calendar code keeps its own calls.
 *
 * Never log a request or answer body: they hold the user's tasks.
 */
internal class MicrosoftGraph(
    /** A token; [forceRefresh] after Graph answered 401 to the cached one. */
    private val token: suspend (forceRefresh: Boolean) -> String,
    private val baseUrl: String = BASE_URL,
    private val client: OkHttpClient = sharedClient
) {
    /**
     * GET, POST, PATCH or DELETE on [path]: a path after /v1.0 ("/me/todo/lists") or a whole URL
     * Graph handed out (nextLink, deltaLink). The answer's JSON, or null when it has none (204).
     */
    suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject? {
        val content = body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST" || method == "PATCH") "{}".toRequestBody(JSON_TYPE) else null
        return authorized { auth ->
            Request.Builder().url(urlOf(path)).header("Authorization", auth).method(method, content).build()
        }.use { read(it) }
    }

    /** The file at [path] (OneDrive's ".../content", which redirects to the file), written to [out]. */
    suspend fun download(path: String, out: OutputStream) {
        authorized { auth -> Request.Builder().url(urlOf(path)).header("Authorization", auth).get().build() }
            .use { response ->
                withContext(Dispatchers.IO) {
                    try {
                        response.body?.byteStream()?.use { it.copyTo(out) }
                    } catch (e: IOException) {
                        throw GraphException.Offline(e)
                    }
                }
            }
    }

    /**
     * One piece of a OneDrive upload session: [length] bytes of [bytes] at [offset] of [total].
     * [uploadUrl] comes from createUploadSession and carries its own permission, so no token goes
     * with it (Microsoft's rule). The answer: the next range wanted, or the finished file.
     */
    suspend fun uploadPiece(uploadUrl: String, bytes: ByteArray, length: Int, offset: Long, total: Long): JsonObject? {
        val request = Request.Builder().url(uploadUrl)
            .header("Content-Range", "bytes $offset-${offset + length - 1}/$total")
            .put(bytes.toRequestBody(BYTES_TYPE, 0, length))
            .build()
        return checked(send(request)).use { read(it) }
    }

    private suspend fun authorized(build: (String) -> Request): Response {
        var response = send(build("Bearer ${tokenOrThrow(false)}"))
        if (response.code == 401) {
            response.close()
            response = send(build("Bearer ${tokenOrThrow(true)}"))
            if (response.code == 401) {
                response.close()
                throw GraphException.SignInNeeded()
            }
        }
        return checked(response)
    }

    private suspend fun tokenOrThrow(forceRefresh: Boolean): String = try {
        token(forceRefresh)
    } catch (e: GraphException) {
        throw e
    } catch (e: MsalUiRequiredException) {
        throw GraphException.SignInNeeded(e)
    } catch (e: AccountNotFoundException) {
        throw GraphException.SignInNeeded(e)
    } catch (e: MsalClientException) {
        if (e.errorCode in MSAL_NETWORK_CODES) throw GraphException.Offline(e)
        throw e
    } catch (e: IOException) {
        throw GraphException.Offline(e)
    }

    private suspend fun send(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw GraphException.Offline(e)
        }
    }

    /** [response] when it succeeded; otherwise closed and thrown as a [GraphException]. */
    private fun checked(response: Response): Response {
        if (response.isSuccessful) return response
        response.use {
            val status = it.code
            if (status == 429 || status == 503 || status == 504) throw GraphException.Busy(retryAfterMs(it))
            throw GraphException.Http(status, errorCode(it))
        }
    }

    private suspend fun read(response: Response): JsonObject? = withContext(Dispatchers.IO) {
        val text = try {
            response.body?.string().orEmpty()
        } catch (e: IOException) {
            throw GraphException.Offline(e)
        }
        if (text.isBlank()) null else Json.parseToJsonElement(text).jsonObject
    }

    private fun urlOf(path: String) = if (path.startsWith("https://")) path else baseUrl + path

    companion object {
        const val BASE_URL = "https://graph.microsoft.com/v1.0"
        private val JSON_TYPE = "application/json".toMediaType()
        private val BYTES_TYPE = "application/octet-stream".toMediaType()
        private const val MAX_RETRY_AFTER_MS = 24 * 60 * 60_000L

        /** MSAL's "no network" answers (MsalClientException codes). */
        private val MSAL_NETWORK_CODES = setOf("device_network_not_available", "io_error")

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        /** Graph for the MSAL account [msalAccountId] with [scopes]; starts MSAL first when it isn't yet. */
        fun forAccount(auth: OutlookAuthManager, msalAccountId: String, scopes: List<String>) = MicrosoftGraph(
            token = { force ->
                auth.initialize().getOrThrow()
                auth.getAccessToken(msalAccountId, scopes, force).getOrThrow()
            }
        )

        /** Microsoft's Retry-After (seconds), at most a day; null when it sends none. */
        internal fun retryAfterMs(response: Response): Long? =
            response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)?.coerceIn(0L, MAX_RETRY_AFTER_MS)

        /** Graph's error code from {"error": {"code": ...}}, or null. */
        private fun errorCode(response: Response): String? = try {
            val text = response.body?.string().orEmpty()
            Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }
}
