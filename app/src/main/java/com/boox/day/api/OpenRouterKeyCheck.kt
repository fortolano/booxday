package com.boox.day.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Asks OpenRouter whether a key works before Boox Day relies on it (Oct 2026): `GET /api/v1/key`,
 * which uses no tokens and returns the key's spending limit when it has one.
 */
object OpenRouterKeyCheck {
    private const val TAG = "OpenRouterKeyCheck"
    private const val URL = "https://openrouter.ai/api/v1/key"

    sealed class Result {
        /** OpenRouter accepted the key. [limit] and [limitRemaining] are dollars, null when the key has no limit. */
        data class Accepted(val limit: Double?, val limitRemaining: Double?, val isFreeTier: Boolean) : Result()

        /** OpenRouter does not know this key (HTTP 401). */
        object Rejected : Result()

        /** No answer (offline, timeout, server error): the key could not be checked. */
        data class Unreachable(val reason: String) : Result()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(key: String): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(URL)
                .header("Authorization", "Bearer ${key.trim()}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> Result.Rejected
                    !response.isSuccessful -> Result.Unreachable("HTTP ${response.code}")
                    else -> {
                        val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                            ?: JSONObject()
                        Result.Accepted(
                            limit = data.optDoubleOrNull("limit"),
                            limitRemaining = data.optDoubleOrNull("limit_remaining"),
                            isFreeTier = data.optBoolean("is_free_tier", false)
                        ).also {
                            // An accepted key is the one AI uses (Save Key keeps it): which models to try
                            OpenRouterAccount.onKeyChecked(it.isFreeTier, it.limitRemaining)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Key check failed: ${e.message}")
            Result.Unreachable(e.message ?: "network error")
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null
}
