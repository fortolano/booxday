package com.boox.day.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Where the Markdown daily notes go (Oct 2026): Documents/BooxDay/Daily Notes by default, or a
 * folder the user picked (an Obsidian vault, a Syncthing folder) through Android's folder picker.
 * The picked folder is kept on this tablet only (its own preferences, not the exported settings).
 */
object DailyNoteWriter {
    private const val PREFS = "daily_notes"
    private const val KEY_TREE = "tree_uri"

    fun defaultFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BooxDay/Daily Notes")

    fun chosenFolder(context: Context): Uri? =
        prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)

    /** Keeps the folder the picker returned, with lasting read and write access to it. */
    fun chooseFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        chosenFolder(context)?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        prefs(context).edit().putString(KEY_TREE, tree.toString()).apply()
    }

    fun useDefaultFolder(context: Context) {
        chosenFolder(context)?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs(context).edit().remove(KEY_TREE).apply()
    }

    /** "Documents/BooxDay/Daily Notes" or the picked folder's name, for Settings. */
    fun folderLabel(context: Context): String {
        val tree = chosenFolder(context) ?: return "Documents/BooxDay/Daily Notes"
        return runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "Chosen folder" } }
            .getOrDefault("Chosen folder")
    }

    /**
     * Writes [fileName]: reads what is there, lets [update] make the new content, writes it.
     * Blocking; call from a background thread.
     */
    fun write(context: Context, fileName: String, update: (existing: String?) -> String) {
        val tree = chosenFolder(context)
        if (tree == null) writeFile(fileName, update) else writeDocument(context, tree, fileName, update)
    }

    private fun writeFile(fileName: String, update: (String?) -> String) {
        val dir = defaultFolder().apply { mkdirs() }
        val target = File(dir, fileName)
        val content = update(if (target.isFile) target.readText() else null)
        val tmp = File(dir, "$fileName.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Could not save ${target.absolutePath}" }
        }
    }

    private fun writeDocument(context: Context, tree: Uri, fileName: String, update: (String?) -> String) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val existing = findChild(context, tree, fileName)
        val old = existing?.let { uri -> resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
        val content = update(old)
        // application/octet-stream: providers keep the name as given (no ".txt" added)
        val target = existing ?: DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName)
            ?: error("The folder refused a new file")
        resolver.openOutputStream(target, "wt")?.use { it.write(content.toByteArray()) }
            ?: error("Could not write $fileName")
    }

    private fun findChild(context: Context, tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
