package com.boox.day.io

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Where an export was saved, as the tablet's file manager shows it (Oct 2026): the message said only
 * "Saved BooxDay-2026-11-08-p1.pdf", and a user couldn't find the PDF (reported 13 Dec). Now it is
 * "Saved … to Documents/BooxDay/Calendar/2026-11". Null when the folder can't be named; the message
 * then stays as it was.
 */
internal object ExportFolderLabel {

    fun of(folder: Uri, storageRoot: String = Environment.getExternalStorageDirectory().absolutePath): String? = try {
        when {
            folder.scheme == "file" -> folder.path?.let { forPath(it, storageRoot) }
            DocumentsContract.isTreeUri(folder) -> forDocumentId(DocumentsContract.getTreeDocumentId(folder))
            // A document folder: getDocumentId reads the id from the Uri (and throws if it has none)
            folder.scheme == "content" -> forDocumentId(DocumentsContract.getDocumentId(folder))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /** A folder on shared storage: its path below [storageRoot] ("/storage/emulated/0"). */
    fun forPath(path: String, storageRoot: String): String? {
        val root = storageRoot.trimEnd('/')
        val relative = if (root.isNotEmpty() && path.startsWith("$root/")) path.removePrefix("$root/") else path
        return relative.trim('/').ifEmpty { null }
    }

    /** A folder picked by the user: its document id is "<volume>:<path>", e.g. "primary:Documents/Export". */
    fun forDocumentId(documentId: String): String? =
        documentId.substringAfter(':', documentId).trim('/').ifEmpty { null }

    /** "Saved [fileName]", and where, when the folder can be named. */
    fun savedMessage(fileName: String, folderLabel: String?): String =
        if (folderLabel == null) "Saved $fileName" else "Saved $fileName to $folderLabel"
}
