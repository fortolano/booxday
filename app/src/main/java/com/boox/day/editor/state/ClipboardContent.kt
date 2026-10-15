package com.boox.day.editor.state

import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke

data class ClipboardContent(
    val strokes: List<Stroke> = emptyList(),
    val images: List<Image> = emptyList(),
)