package com.boox.day.booxnotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Hiding a real notebook would be worse than showing an annotation: only the exact pattern is hidden. */
class BooxReaderAnnotationsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun document(id: String, vararg folders: String) {
        folders.forEach { File(folder.root, "document/$id/$it").mkdirs() }
    }

    private fun isAnnotation(id: String) = BooxReaderAnnotations.isAnnotation(id, folder.root.path)

    @Test
    fun aBookOrPdfAnnotationIsFound() {
        document("book", "resource", "shape", "stash")
        assertTrue(isAnnotation("book"))
    }

    @Test
    fun aNotebookIsNeverHidden() {
        document("notebook", "pageModel", "template", "virtual", "resource", "shape")
        document("no-resource", "pageModel", "template", "virtual", "shape")
        assertFalse(isAnnotation("notebook"))
        assertFalse(isAnnotation("no-resource"))
    }

    @Test
    fun anythingUnclearStays() {
        document("dated-note", "shape")
        assertFalse(isAnnotation("dated-note"))
        assertFalse(isAnnotation("no-folder-at-all"))
    }
}
