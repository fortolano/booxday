package com.boox.day.booxnotes

import java.io.File

/**
 * NeoReader keeps pen marks made on a book or PDF in the same folders as notebooks, so the note scan
 * finds them too. They aren't notes: no name, no place in the Notes app.
 *
 * BOOX's own folders tell them apart. On the Go 10.3 (Nov 2026, all 149 documents): every notebook has
 * `pageModel`; the 25 annotations have `resource` without it. Documents with neither (the dated notes
 * BOOX's provider lists) are kept: anything unclear stays in the list.
 */
internal object BooxReaderAnnotations {
    fun isAnnotation(uniqueId: String, ksyncBase: String = BooxNative.KSYNC_BASE_PATH): Boolean {
        val document = File(ksyncBase, "document/$uniqueId")
        return File(document, "resource").isDirectory && !File(document, "pageModel").isDirectory
    }
}
