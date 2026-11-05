package com.boox.day.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.boox.day.export.DailyNoteWriter

/**
 * The folders the Export dialog saves to, chosen with Change (Nov 2026). Kept on this tablet only,
 * in its own preferences: a folder grant means nothing on another tablet, and the settings are
 * exported. PDFs and images share one folder; Markdown has its own and starts in the Daily notes
 * folder, because it updates the same notes.
 */
object ExportFolders {
    private const val PREFS = "export_folders"
    private const val KEY_FILES = "files_tree"
    private const val KEY_NOTES = "notes_tree"

    const val DEFAULT_FILES_LABEL = "Documents/BooxDay"

    private fun key(kind: ExportKind) = if (kind == ExportKind.MARKDOWN) KEY_NOTES else KEY_FILES

    /** The folder picked for [kind]; null for the default. */
    fun chosen(context: Context, kind: ExportKind): Uri? =
        prefs(context).getString(key(kind), null)?.let(Uri::parse)

    /** Where Markdown notes go; null for the default Daily notes folder. */
    fun notesFolder(context: Context): Uri? =
        chosen(context, ExportKind.MARKDOWN) ?: DailyNoteWriter.chosenFolder(context)

    /** Keeps the folder the picker returned, with lasting read and write access to it. */
    fun choose(context: Context, kind: ExportKind, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(tree, flags)
        val old = chosen(context, kind)
        prefs(context).edit().putString(key(kind), tree.toString()).apply()
        if (old != null && old != tree && !stillUsed(context, old)) {
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
    }

    /** Another setting may share the folder, and needs the access to stay. */
    private fun stillUsed(context: Context, folder: Uri): Boolean =
        folder == chosen(context, ExportKind.PDF) || folder == chosen(context, ExportKind.MARKDOWN) ||
            folder == DailyNoteWriter.chosenFolder(context)

    /** The folder's name as the dialog shows it. */
    fun label(context: Context, kind: ExportKind): String = when (kind) {
        ExportKind.MARKDOWN -> chosen(context, kind)?.let { ExportFolderLabel.of(it) } ?: DailyNoteWriter.folderLabel(context)
        else -> chosen(context, kind)?.let { ExportFolderLabel.of(it) ?: "Chosen folder" } ?: DEFAULT_FILES_LABEL
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
