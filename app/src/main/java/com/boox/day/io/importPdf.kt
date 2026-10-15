package com.boox.day.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.WorkerThread
import com.boox.day.TAG
import com.boox.day.data.copyBackgroundToDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.model.BackgroundType
import com.boox.day.ui.SnackState
import com.boox.day.utils.ensureNotMainThread
import android.util.Log
import java.io.File

fun isPdfFile(mimeType: String?, fileName: String?): Boolean {
    return mimeType == "application/pdf" || fileName?.endsWith(
        ".pdf", ignoreCase = true
    ) == true
}


@WorkerThread
fun handleFileSaving(
    context: Context,
    uri: Uri,
    options: ImportOptions,
): File? {
    ensureNotMainThread("Importing")

    //copy file:
    val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
    context.contentResolver.takePersistableUriPermission(uri, flag)
    val subfolder = BackgroundType.Pdf(0).folderName
    return if (!options.linkToExternalFile) copyBackgroundToDatabase(context, uri, subfolder)
    else {
        val fileName = getFilePathFromUri(context, uri)
        if (fileName == null) {
            SnackState.logAndShowError(
                "copyFileToDatabase",
                "Couldn't determine file path. Does the app have permission to read external storage?"
            )
            return null
        } else File(fileName)
    }
}

@WorkerThread
fun importPdf(
    fileToSave: File,
    options: ImportOptions,
    savePageToDatabase: (PageContent) -> Unit
): String {
    Log.v(TAG, "Importing PDF from")

    val numberOfPages = getPdfPageCount(fileToSave.toString())

    for (i in 0 until numberOfPages) {
        val page = Page(
            notebookId = options.saveToBookId,
            background = fileToSave.toString(),
            backgroundType = if (options.linkToExternalFile) BackgroundType.AutoPdf.key
            else BackgroundType.Pdf(i).key
        )
        savePageToDatabase(PageContent(page, emptyList(), emptyList()))
    }
    return "Imported ${fileToSave.name}"
}