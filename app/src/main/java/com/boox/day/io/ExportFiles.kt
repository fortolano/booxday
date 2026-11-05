package com.boox.day.io

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Writes an export to its folder (Nov 2026). The file is made in app storage first and reaches the
 * folder only once it is whole, under a name not in use: a failed export leaves nothing behind and
 * can't harm an earlier one. It used to open the old file for writing before rendering, so a
 * failure destroyed it, and a second export of a page silently replaced the first.
 */
internal object ExportFiles {

    data class Saved(val uri: Uri, val displayName: String)

    /** [displayName], or the same with -2, -3... when that one is taken. */
    fun uniqueName(displayName: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(displayName)) return displayName
        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        return generateSequence(2) { it + 1 }.map { "$stem-$it$extension" }.first { !isTaken(it) }
    }

    /**
     * Saves what [writer] writes as [displayName] in [folderUri] (a file:// folder or a picked one).
     * With [overwrite] a file of that name is replaced; otherwise the new one gets its own name.
     * @throws IOException or whatever [writer] throws; the folder then holds nothing new.
     */
    suspend fun save(
        context: Context,
        folderUri: Uri,
        displayName: String,
        mimeType: String,
        overwrite: Boolean,
        writer: suspend (OutputStream) -> Unit
    ): Saved = withContext(Dispatchers.IO) {
        val temp = File(File(context.cacheDir, "exports").apply { mkdirs() }, "writing-${UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { writer(it) }
            if (folderUri.scheme == "file") copyToFolder(temp, folderUri, displayName, overwrite)
            else copyToPickedFolder(context, temp, folderUri, displayName, mimeType, overwrite)
        } finally {
            temp.delete()
        }
    }

    private fun copyToFolder(temp: File, folderUri: Uri, displayName: String, overwrite: Boolean): Saved {
        val folder = File(requireNotNull(folderUri.path) { "Missing folder path" })
        folder.mkdirs()
        val name = if (overwrite) displayName else uniqueName(displayName) { File(folder, it).exists() }
        val target = File(folder, name)
        try {
            temp.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            if (!overwrite && target.isFile) target.delete()
            throw e
        }
        return Saved(target.toUri(), name)
    }

    private fun copyToPickedFolder(
        context: Context, temp: File, folderUri: Uri, displayName: String, mimeType: String, overwrite: Boolean
    ): Saved {
        val resolver = context.contentResolver
        val isTree = DocumentsContract.isTreeUri(folderUri)
        val parentId = if (isTree) DocumentsContract.getTreeDocumentId(folderUri) else DocumentsContract.getDocumentId(folderUri)
        val parent = if (isTree) DocumentsContract.buildDocumentUriUsingTree(folderUri, parentId) else folderUri
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentId)

        val existing = HashMap<String, String>()  // name → document id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) existing[cursor.getString(1)] = cursor.getString(0)
        }

        val name = if (overwrite) displayName else uniqueName(displayName) { it in existing }
        val document = (if (overwrite) existing[name] else null)?.let { DocumentsContract.buildDocumentUriUsingTree(folderUri, it) }
            ?: DocumentsContract.createDocument(resolver, parent, mimeType, name)
            ?: throw IOException("Could not create the file in the folder")
        try {
            temp.inputStream().use { input ->
                (resolver.openOutputStream(document, "wt") ?: throw IOException("Could not open the file"))
                    .use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            if (!overwrite) runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw e
        }
        return Saved(document, name)
    }
}
