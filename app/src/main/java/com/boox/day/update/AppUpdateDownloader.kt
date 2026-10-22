package com.boox.day.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK into `cacheDir/updates` and checks it before anything installs it
 * (Oct 2026): the size and SHA-256 GitHub published, then that it is Boox Day, newer than the
 * installed app and signed with the same key. Android checks the key again when installing; doing
 * it here gives a plain message instead of a cryptic install failure. `cacheDir` is never in a
 * backup, and Android may clear it when storage runs low (the download then simply starts over).
 */
object AppUpdateDownloader {
    private const val TAG = "AppUpdateDownloader"
    private const val SPARE_BYTES = 20L * 1024 * 1024
    private const val WAKE_LOCK_MS = 15L * 60 * 1000

    sealed class Result {
        data class Ready(val apk: File) : Result()

        /** [message] is shown to the user as is. */
        data class Failed(val message: String) : Result()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates")

    /**
     * Downloads [info] (resuming a partial download of the same version) and checks it.
     * [onProgress] gets whole percents in steps of 5, so the e-ink screen refreshes rarely.
     * Cancelling the coroutine stops the download and keeps the partial file for next time.
     */
    suspend fun download(context: Context, info: AppUpdateChecker.UpdateInfo, onProgress: (Int) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val dir = updatesDir(context).apply { mkdirs() }
            val finalFile = File(dir, "booxday-${info.version}.apk")
            val partFile = File(dir, "booxday-${info.version}.apk.part")
            dir.listFiles()?.filter { it != finalFile && it != partFile }?.forEach { it.delete() }

            if (finalFile.exists()) {
                if (finalFile.length() == info.sizeBytes && sha256Of(finalFile) == info.sha256) {
                    return@withContext checkPackage(context, finalFile)
                }
                finalFile.delete()
            }

            val alreadyHave = partFile.length().takeIf { partFile.exists() && it < info.sizeBytes } ?: 0L
            if (alreadyHave == 0L) partFile.delete()
            if (dir.usableSpace < info.sizeBytes - alreadyHave + SPARE_BYTES) {
                val neededMb = (info.sizeBytes - alreadyHave + SPARE_BYTES) / (1024 * 1024)
                return@withContext Result.Failed("Not enough free space. Free about $neededMb MB, then try again.")
            }

            val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BooxDay:update-download")
            wakeLock.acquire(WAKE_LOCK_MS)
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .apply { if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-") }
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        partFile.delete()
                        return@withContext Result.Failed("The download did not start (error ${response.code}). Try again later.")
                    }
                    // 206 = the server continued where we stopped; 200 = it sent the whole file again
                    val resumed = response.code == 206
                    val body = response.body ?: return@withContext Result.Failed("The download did not start. Try again later.")
                    var written = if (resumed) alreadyHave else 0L
                    var lastStep = -1
                    body.byteStream().use { input ->
                        FileOutputStream(partFile, resumed).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                val step = (written * 100 / info.sizeBytes).toInt().coerceIn(0, 100) / 5
                                if (step != lastStep) {
                                    lastStep = step
                                    onProgress(step * 5)
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download stopped: ${e.message}")
                return@withContext Result.Failed("The download stopped. Check the Wi-Fi connection and try again; it continues where it stopped.")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }

            if (partFile.length() != info.sizeBytes) {
                partFile.delete()
                return@withContext Result.Failed("The download was incomplete. Try again.")
            }
            if (sha256Of(partFile) != info.sha256) {
                partFile.delete()
                return@withContext Result.Failed("The download was damaged. Try again.")
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                return@withContext Result.Failed("The update could not be saved. Try again.")
            }
            checkPackage(context, finalFile)
        }

    /**
     * Checks that [apk] is Boox Day, newer than the installed version and signed with the same key.
     * Deletes the file when it is not.
     */
    fun checkPackage(context: Context, apk: File): Result {
        val problem = packageProblem(context, apk)
        if (problem != null) {
            Log.w(TAG, "APK check failed: $problem")
            apk.delete()
            return Result.Failed(problem)
        }
        return Result.Ready(apk)
    }

    private fun packageProblem(context: Context, apk: File): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
            ?: return "The downloaded file is not a valid app. Try again."
        if (archive.packageName != context.packageName) return "The downloaded file is not Boox Day."
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.longVersionCode <= installed.longVersionCode) {
            return "This update is not newer than the version you have."
        }
        val archiveSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        if (archiveSigners.isEmpty() || archiveSigners != installedSigners) {
            return "This update is not signed like your installed Boox Day, so it was not installed."
        }
        return null
    }

    /** SHA-256 of each signing certificate (current signers; Boox Day has one key, no rotation). */
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signing = info.signingInfo
        @Suppress("DEPRECATION")
        val certificates = when {
            signing != null && signing.hasMultipleSigners() -> signing.apkContentsSigners
            signing != null -> signing.signingCertificateHistory
            else -> info.signatures
        } ?: return emptySet()
        return certificates.map { sha256Hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return sha256Hex(digest.digest())
    }

    private fun sha256Hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
