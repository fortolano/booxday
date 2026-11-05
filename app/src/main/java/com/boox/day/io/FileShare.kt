package com.boox.day.io

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Hands saved files to another app through the Android share sheet (Nov 2026). */
object FileShare {
    private const val AUTHORITY = "com.boox.day.provider"

    /** @throws IllegalArgumentException when a file lies outside the folders res/xml/file_paths.xml shares. */
    fun share(context: Context, uris: List<Uri>, mimeType: String) {
        val shared = ArrayList(uris.map { uri ->
            if (uri.scheme == "file") FileProvider.getUriForFile(context, AUTHORITY, File(requireNotNull(uri.path))) else uri
        })
        val intent = if (shared.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, shared.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, shared)
        }
        intent.type = mimeType
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser passes on read access through the clip data
        intent.clipData = ClipData.newRawUri(null, shared.first()).apply {
            shared.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
