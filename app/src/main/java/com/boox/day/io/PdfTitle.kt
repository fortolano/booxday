package com.boox.day.io

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.boox.day.utils.TaggedLog
import java.io.File

/**
 * The title a PDF reader shows (Nov 2026). Android's PdfDocument can't set one, so mupdf adds it
 * to the finished file as an incremental update: the pages written before stay byte for byte.
 * Only a nicety, so a failure leaves the file as it is.
 */
internal object PdfTitle {
    private val log = TaggedLog("PdfTitle")

    fun set(file: File, title: String) {
        try {
            val document = Document.openDocument(file.absolutePath) as? PDFDocument ?: return
            try {
                document.setMetaData(Document.META_INFO_TITLE, title)
                document.save(file.absolutePath, "incremental")
            } finally {
                document.destroy()
            }
        } catch (e: Throwable) {
            if (e is OutOfMemoryError) throw e
            log.w("Title not set: ${e.javaClass.simpleName}")
        }
    }
}
