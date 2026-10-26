package com.boox.day.io

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Saved … to <folder>" after an export (Oct 2026; reported 13 Dec: the user couldn't find the PDF). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExportFolderLabelTest {

    @Test
    fun theDefaultFolderIsShownBelowTheStorageRoot() {
        // The tablet's paths (the JVM's own storage root is a Windows path on the build PC)
        val folder = Uri.parse("file:///storage/emulated/0/Documents/BooxDay/Calendar/2026-11")
        val label = ExportFolderLabel.of(folder, storageRoot = "/storage/emulated/0")

        assertEquals("Documents/BooxDay/Calendar/2026-11", label)
        assertEquals(
            "Saved BooxDay-2026-11-08-p1.pdf to Documents/BooxDay/Calendar/2026-11",
            ExportFolderLabel.savedMessage("BooxDay-2026-11-08-p1.pdf", label)
        )
    }

    @Test
    fun aPickedFolderIsShownByItsPathOnTheVolume() {
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Documents/Export")

        assertEquals("Documents/Export", ExportFolderLabel.of(tree))
        assertEquals("Backups/2026", ExportFolderLabel.forDocumentId("1234-5678:Backups/2026"))
    }

    @Test
    fun pathsOutsideTheRootAndUnnamedFoldersKeepTheOldMessage() {
        assertEquals("storage/1234-5678/Exports", ExportFolderLabel.forPath("/storage/1234-5678/Exports", "/storage/emulated/0"))
        assertNull(ExportFolderLabel.forPath("/storage/emulated/0/", "/storage/emulated/0"))
        assertNull(ExportFolderLabel.forDocumentId("primary:"))
        assertEquals("Saved notes.pdf", ExportFolderLabel.savedMessage("notes.pdf", null))
    }
}
