package com.boox.day.update

import android.util.Log
import com.boox.day.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds a newer BooxDay release on GitHub (Oct 2026): `GET releases/latest` on the public API (no
 * login, 60 requests an hour per network). GitHub's "latest" is the newest published release that is
 * not a draft or prerelease. The release must carry the APK as `booxday-<version>.apk`, which GitHub
 * gives a SHA-256 digest; the download is checked against it (see CLAUDE.md "Releases").
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val LATEST_URL = "https://api.github.com/repos/fortolano/booxday/releases/latest"
    private const val TAG_URL_PREFIX = "https://api.github.com/repos/fortolano/booxday/releases/tags/v"
    private const val MAX_NOTES = 6

    /** A release newer than the installed app. [sha256] is lower-case hex. */
    data class UpdateInfo(
        val version: String,
        val notes: List<String>,
        val apkUrl: String,
        val sizeBytes: Long,
        val sha256: String
    )

    sealed class Result {
        data class Available(val info: UpdateInfo) : Result()
        object UpToDate : Result()

        /** No answer, or no release with a usable APK. [reason] is for the log only. */
        data class Failed(val reason: String) : Result()
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList()
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val digest: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val apkName = Regex("""^booxday-[0-9][0-9A-Za-z.\-]*\.apk$""")
    private val versionPrefix = Regex("""^[vV]?(\d+(?:\.\d+)*)""")
    private val sha256Hex = Regex("^[0-9a-f]{64}$")

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Asks GitHub for the latest release; [currentVersion] is what it is compared with. */
    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): Result = withContext(Dispatchers.IO) {
        try {
            client.newCall(gitHubRequest(LATEST_URL)).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.Failed("HTTP ${response.code}")
                val info = parseRelease(response.body?.string().orEmpty())
                    ?: return@withContext Result.Failed("no usable release")
                if (isNewer(info.version, currentVersion)) Result.Available(info) else Result.UpToDate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            Result.Failed(e.message ?: "network error")
        }
    }

    /**
     * The "What's new" bullets of the published release v[version] (Oct 2026), for the first start
     * after an update installed by hand, when the in-app updater saved none. Null when GitHub can't
     * be reached or has no such release; asked once, never retried.
     */
    suspend fun releaseNotesFor(version: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            client.newCall(gitHubRequest(TAG_URL_PREFIX + version)).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val release = json.decodeFromString(ReleaseDto.serializer(), response.body?.string().orEmpty())
                if (release.draft || release.prerelease) null else releaseNotes(release.body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release notes for $version not loaded: ${e.message}")
            null
        }
    }

    private fun gitHubRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "BooxDay/${BuildConfig.VERSION_NAME}")
        .get()
        .build()

    /**
     * The update a `releases/latest` answer offers, or null when it is a draft or prerelease, or has
     * no `booxday-*.apk` asset with a SHA-256 digest (nothing to verify a download against).
     */
    fun parseRelease(body: String): UpdateInfo? {
        val release = try {
            json.decodeFromString(ReleaseDto.serializer(), body)
        } catch (e: Exception) {
            Log.w(TAG, "Release answer not understood: ${e.message}")
            return null
        }
        if (release.draft || release.prerelease) return null
        val version = parseVersion(release.tagName)?.joinToString(".") ?: return null
        val asset = release.assets.firstOrNull { apkName.matches(it.name) } ?: return null
        val sha256 = asset.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { sha256Hex.matches(it) } ?: return null
        if (asset.size <= 0 || !asset.downloadUrl.startsWith("https://")) return null
        return UpdateInfo(
            version = version,
            notes = releaseNotes(release.body),
            apkUrl = asset.downloadUrl,
            sizeBytes = asset.size,
            sha256 = sha256
        )
    }

    /** "v1.0.35", "1.0.35" or "1.0.34-next-20.10.2026-14:00" -> [1, 0, 35] / [1, 0, 34]; null if no number. */
    fun parseVersion(text: String): List<Int>? {
        val numbers = versionPrefix.find(text.trim())?.groupValues?.get(1) ?: return null
        return numbers.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** True when [candidate] is a higher version than [current] (missing parts count as 0). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * The "What's new" bullets: the release notes' `- ` lines (see the notes format in CLAUDE.md),
     * without markdown marks, at most [MAX_NOTES]. The "Install:" line is for people downloading by
     * hand, so it is left out.
     */
    fun releaseNotes(body: String?): List<String> = body.orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("- ") || it.startsWith("* ") }
        .map { it.drop(2).replace("**", "").replace("`", "").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("Install", ignoreCase = true) }
        .take(MAX_NOTES)
        .toList()
}
