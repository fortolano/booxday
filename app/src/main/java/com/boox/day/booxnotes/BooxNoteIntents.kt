package com.boox.day.booxnotes

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject

/** Contracts recovered from BOOX RouterUtils, OpenNoteBean and TabIntentHelper. */
internal object BooxNoteIntents {
    private const val PACKAGE = "com.onyx.android.note"

    fun direct(documentId: String): Intent {
        require(documentId.isNotBlank()) { "A note document id is required" }
        val bean = JSONObject().put("documentId", documentId).put("pageIndex", -1)
        return Intent().apply {
            component = ComponentName(PACKAGE, "com.onyx.android.note.note.ui.ScribbleActivity")
            putExtras(Bundle().apply { putString("OPEN_NOTE_BEAN", bean.toString()) })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun middleware(documentId: String): Intent {
        require(documentId.isNotBlank()) { "A note document id is required" }
        val route = JSONObject().put("action", "OPEN_NOTE").put("jumpPath", documentId)
        return Intent("com.onyx.action.MIDDLEWARE").apply {
            // Keep legacy middleware keys while supporting the SDK's JSON tab route.
            putExtras(Bundle().apply {
                putString("note_document_id", documentId)
                putString("type", "note")
                putString("json", route.toString())
            })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
    }
}
