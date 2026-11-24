package com.boox.day.editor.state

import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke

data class ClipboardContent(
    val strokes: List<Stroke> = emptyList(),
    val images: List<Image> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    val isEmpty: Boolean
        get() = strokes.isEmpty() && images.isEmpty() && attachments.isEmpty()
}