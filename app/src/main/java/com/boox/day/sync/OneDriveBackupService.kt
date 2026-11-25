package com.boox.day.sync

import android.content.Context
import android.util.Log
import com.boox.day.sync.DriveBackupService.BackupInfo
import com.boox.day.sync.DriveBackupService.FileDownloadResult
import com.boox.day.sync.DriveBackupService.ListResult
import com.boox.day.sync.DriveBackupService.UploadResult
import com.boox.day.sync.microsoft.GraphException
import com.boox.day.sync.microsoft.MicrosoftGraph
import com.boox.day.sync.microsoft.MicrosoftScopes
import com.boox.day.sync.outlook.OutlookAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/**
 * Backups in BooxDay's own OneDrive folder, OneDrive › Apps › BooxDay (owner decisions 85-88, Nov
 * 2026). Microsoft makes the folder on the first call; BooxDay can see nothing else in the OneDrive
 * (Files.ReadWrite.AppFolder). Every backup is kept, like Google Drive's.
 *
 * Never logs a file's contents, a token or an answer body.
 */
internal class OneDriveBackupService(
    /** Graph for the OneDrive backup account; null while none is connected. */
    private val graph: () -> MicrosoftGraph?,
    /** Where downloads wait for the restore. */
    private val downloadDir: () -> File,
    /** Records a finished upload's time. */
    private val onBackedUp: (Long) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pieceSize: Int = PIECE_SIZE
) : CloudBackupService {

    private class Uploaded(val id: String, val name: String)

    /** The upload session is gone (expired, or finished while its answer was lost). */
    private class SessionGone : Exception()

    /** The backup is no longer wanted in this OneDrive (another cloud or account chosen meanwhile). */
    private class NoLongerWanted : Exception()

    override suspend fun uploadBackup(localFile: File, stillWanted: () -> Boolean): UploadResult = withContext(Dispatchers.IO) {
        try {
            if (!stillWanted()) return@withContext UploadResult.Stopped
            val graph = graph() ?: return@withContext UploadResult.Error(NOT_CONNECTED, retryable = false)
            val item = try {
                uploadOnce(graph, localFile, stillWanted)
            } catch (e: SessionGone) {
                // Microsoft's advice for a lost session: start the upload over, once
                Log.w(TAG, "Upload session gone, starting over")
                try {
                    uploadOnce(graph, localFile, stillWanted)
                } catch (again: SessionGone) {
                    throw GraphException.Http(404, "uploadSessionNotFound")
                }
            }
            Log.i(TAG, "Backup uploaded (${localFile.length()} bytes)")
            onBackedUp(System.currentTimeMillis())
            UploadResult.Success(item.id, item.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoLongerWanted) {
            // Microsoft makes no file from an unfinished session; it drops the pieces itself
            Log.i(TAG, "Upload stopped: another cloud or account was chosen")
            UploadResult.Stopped
        } catch (e: GraphException) {
            Log.w(TAG, "Upload failed: ${e.javaClass.simpleName} ${(e as? GraphException.Http)?.status ?: ""}")
            UploadResult.Error(messageOf(e), retryable = isRetryable(e))
        } catch (e: IOException) {
            Log.w(TAG, "Upload failed reading the backup file: ${e.javaClass.simpleName}")
            UploadResult.Error("Couldn't read the backup file on the tablet.", retryable = true)
        } catch (e: Exception) {
            // MSAL that can't start, an MSAL error Graph doesn't sort, an answer that isn't JSON
            Log.w(TAG, "Upload failed: ${e.javaClass.simpleName}")
            UploadResult.Error(SOMETHING_WRONG, retryable = true)
        }
    }

    /** One upload session: pieces in order, each a multiple of 320 KiB (Microsoft's rule) but the last. */
    private suspend fun uploadOnce(graph: MicrosoftGraph, file: File, stillWanted: () -> Boolean): Uploaded {
        val total = file.length()
        if (total == 0L) throw IOException("empty backup file")
        if (!stillWanted()) throw NoLongerWanted()
        val session = graph.call(
            "POST",
            "$APP_FOLDER:/${pathPart(file.name)}:/createUploadSession",
            buildJsonObject { putJsonObject("item") { put("@microsoft.graph.conflictBehavior", "rename") } }
        )
        val uploadUrl = session?.text("uploadUrl") ?: throw GraphException.Http(200, "noUploadUrl")

        RandomAccessFile(file, "r").use { input ->
            val buffer = ByteArray(minOf(pieceSize.toLong(), total).toInt())
            var offset = 0L
            while (offset < total) {
                val length = minOf(pieceSize.toLong(), total - offset).toInt()
                input.seek(offset)
                input.readFully(buffer, 0, length)
                val last = offset + length == total
                val answer = try {
                    sendPiece(graph, uploadUrl, buffer, length, offset, total, stillWanted)
                } catch (e: SessionGone) {
                    // The last piece's answer can be lost after Microsoft made the file
                    if (last) findUploaded(graph, file.name, total)?.let { return it }
                    throw e
                }
                if (answer != null) return answer
                offset += length
            }
        }
        // Every piece is there but the last answer was lost on the way
        return findUploaded(graph, file.name, total) ?: throw GraphException.Offline()
    }

    /**
     * One piece, tried again on a busy or lost connection (at most [MAX_PIECE_RETRIES] times). The
     * finished file when it was the last piece, else null.
     */
    private suspend fun sendPiece(
        graph: MicrosoftGraph,
        uploadUrl: String,
        buffer: ByteArray,
        length: Int,
        offset: Long,
        total: Long,
        stillWanted: () -> Boolean
    ): Uploaded? {
        var retries = 0
        while (true) {
            // Before every send, a retry after a wait too (re-review, 25 Nov 2026: a switch during
            // Microsoft's wait let the last piece go and finish the file in the old cloud)
            if (!stillWanted()) throw NoLongerWanted()
            try {
                return graph.uploadPiece(uploadUrl, buffer, length, offset, total)?.let(::uploadedOf)
            } catch (e: GraphException) {
                if (e is GraphException.Http && e.status == 404) throw SessionGone()
                // 416: Microsoft already has this piece; the earlier try reached it, its answer didn't
                if (retries > 0 && e is GraphException.Http && e.status == 416) return null
                val wait = retryWait(e, retries) ?: throw e
                retries++
                Log.w(TAG, "Piece at $offset failed (${e.javaClass.simpleName}), retry $retries in ${wait}ms")
                pause(wait)
            }
        }
    }

    /** How long to wait before trying a piece again, or null to give up. */
    private fun retryWait(e: GraphException, retries: Int): Long? {
        if (retries >= MAX_PIECE_RETRIES) return null
        val backoff = FIRST_BACKOFF_MS shl retries
        return when (e) {
            is GraphException.Busy -> (e.retryAfterMs ?: backoff).takeIf { it <= MAX_WAIT_MS }
            is GraphException.Offline -> backoff
            is GraphException.Http -> if (e.status >= 500) backoff else null
            is GraphException.SignInNeeded -> null
        }
    }

    private suspend fun findUploaded(graph: MicrosoftGraph, name: String, size: Long): Uploaded? = try {
        graph.call("GET", "$APP_FOLDER:/${pathPart(name)}")
            ?.takeIf { it["size"]?.jsonPrimitive?.longOrNull == size }
            ?.let(::uploadedOf)
    } catch (e: GraphException.Http) {
        if (e.status == 404) null else throw e
    }

    override suspend fun listBackups(): ListResult = withContext(Dispatchers.IO) {
        try {
            val graph = graph() ?: return@withContext ListResult.Error(NOT_CONNECTED)
            val found = mutableListOf<BackupInfo>()
            var next: String? = "$APP_FOLDER/children?\$top=200&\$select=id,name,size,createdDateTime,file"
            var pages = 0
            while (next != null && pages < MAX_LIST_PAGES) {
                val page = graph.call("GET", next) ?: break
                page["value"]?.jsonArray?.mapNotNullTo(found) { backupOf(it.jsonObject) }
                next = page.text("@odata.nextLink")
                pages++
            }
            Log.i(TAG, "Found ${found.size} backup(s)")
            ListResult.Success(found.sortedByDescending { it.createdTime }.take(MAX_LISTED))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GraphException) {
            Log.w(TAG, "List failed: ${e.javaClass.simpleName}")
            ListResult.Error(messageOf(e))
        } catch (e: Exception) {
            // MSAL that can't start, an MSAL error Graph doesn't sort, a list in another shape
            Log.w(TAG, "List failed: ${e.javaClass.simpleName}")
            ListResult.Error(SOMETHING_WRONG)
        }
    }

    override suspend fun downloadBackupToFile(fileId: String): FileDownloadResult {
        var temp: File? = null
        // The caller owns the file only once it has the result: a cancellation before that (the
        // screen closed mid-download, or as the answer comes back) deletes it here
        var handedOver = false
        try {
            return withContext(Dispatchers.IO) {
                try {
                    val graph = graph() ?: return@withContext FileDownloadResult.Error(NOT_CONNECTED)
                    val dir = downloadDir().apply { mkdirs() }
                    val file = File(dir, "onedrive-backup-${UUID.randomUUID()}.tmp").also { temp = it }
                    FileOutputStream(file).buffered().use { graph.download("/me/drive/items/$fileId/content", it) }
                    Log.i(TAG, "Downloaded backup (${file.length()} bytes)")
                    FileDownloadResult.Success(file)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GraphException) {
                    Log.w(TAG, "Download failed: ${e.javaClass.simpleName}")
                    FileDownloadResult.Error(messageOf(e))
                } catch (e: IOException) {
                    Log.w(TAG, "Download failed writing the file: ${e.javaClass.simpleName}")
                    FileDownloadResult.Error("Couldn't save the backup on the tablet. Is the tablet full?")
                } catch (e: Exception) {
                    Log.w(TAG, "Download failed: ${e.javaClass.simpleName}")
                    FileDownloadResult.Error(SOMETHING_WRONG)
                }
            }.also { handedOver = it is FileDownloadResult.Success }
        } finally {
            if (!handedOver) temp?.delete()
        }
    }

    companion object {
        private const val TAG = "OneDriveBackupService"
        private const val APP_FOLDER = "/me/drive/special/approot"

        /** 16 x 320 KiB (5 MiB): Microsoft wants pieces in multiples of 320 KiB, 5-10 MiB at best. */
        internal const val PIECE_SIZE = 16 * 320 * 1024
        private const val MAX_PIECE_RETRIES = 3
        private const val FIRST_BACKOFF_MS = 2_000L
        /** A longer wait than this ends the try; automatic backup tries again later. */
        private const val MAX_WAIT_MS = 60_000L
        private const val MAX_LIST_PAGES = 100
        /** As many as Google Drive's list shows. */
        private const val MAX_LISTED = 50

        const val NOT_CONNECTED = "OneDrive isn't connected."

        /** Any other failure: plain words, never the exception's own text (it can hold content). */
        const val SOMETHING_WRONG = "Something went wrong with OneDrive. Try again later."

        fun create(context: Context): OneDriveBackupService {
            val app = context.applicationContext
            val settings = GoogleAuthManager(app)
            val outlook = OutlookAuthManager(app)
            return OneDriveBackupService(
                graph = {
                    settings.getOneDriveAccount()?.let {
                        MicrosoftGraph.forAccount(outlook, it.msalAccountId, MicrosoftScopes.BACKUP)
                    }
                },
                downloadDir = { File(app.cacheDir, "onedrive-restore") },
                onBackedUp = { settings.setLastBackupTime(BackupCloud.ONEDRIVE, it) }
            )
        }

        /** A backup in the folder's list: a ZIP archive or an old JSON backup. */
        internal fun backupOf(item: JsonObject): BackupInfo? {
            if (item["file"] == null) return null
            val id = item.text("id") ?: return null
            val name = item.text("name") ?: return null
            val lower = name.lowercase()
            if (!(lower.endsWith(".zip") || lower.endsWith(".json")) || lower.endsWith("_meta.json")) return null
            val created = item.text("createdDateTime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            return BackupInfo(id, name, created, item["size"]?.jsonPrimitive?.longOrNull ?: 0L)
        }

        private fun uploadedOf(item: JsonObject): Uploaded? {
            val id = item.text("id") ?: return null
            return Uploaded(id, item.text("name") ?: id)
        }

        private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

        private fun pathPart(name: String): String = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

        /** A failure in the words the backup screen shows. */
        internal fun messageOf(e: GraphException): String = when (e) {
            is GraphException.SignInNeeded -> "OneDrive needs you to sign in again."
            is GraphException.Offline -> "Couldn't reach OneDrive. Check the internet connection, then try again."
            is GraphException.Busy -> "OneDrive is busy. Try again in a few minutes."
            is GraphException.Http -> when {
                e.status == 507 || e.code == "quotaLimitReached" || e.code == "insufficientStorage" -> "Your OneDrive is full."
                // An answer that isn't Graph's JSON (such as a Wi-Fi sign-in page)
                e.code == INVALID_RESPONSE -> SOMETHING_WRONG
                else -> "OneDrive answered with an error (${e.status})."
            }
        }

        /** Worth trying again later: not when a sign-in is needed, the OneDrive is full or it refused. */
        internal fun isRetryable(e: GraphException): Boolean = when (e) {
            is GraphException.SignInNeeded -> false
            is GraphException.Offline, is GraphException.Busy -> true
            is GraphException.Http -> e.code == INVALID_RESPONSE || (e.status >= 500 && e.status != 507)
        }

        /** MicrosoftGraph's code for a success answer that isn't JSON (shared branch, Nov 2026). */
        private const val INVALID_RESPONSE = "InvalidResponse"
    }
}
