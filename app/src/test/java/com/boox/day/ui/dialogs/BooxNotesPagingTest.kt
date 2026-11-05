package com.boox.day.ui.dialogs

import androidx.compose.ui.unit.dp
import com.boox.day.data.db.BooxNoteIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list turns over in pages: no note may go missing or show twice, and no page may end on a heading. */
class BooxNotesPagingTest {

    private fun note(id: Int, day: String) =
        NoteListEntry.Note(BooxNoteIndex("n$id", "Note $id", day, id.toLong(), 1, "[]", 1L, 1L))

    private val entries: List<NoteListEntry> = buildList {
        add(NoteListEntry.Month("November 2026"))
        repeat(7) { add(note(it, "2026-11-05")) }
        add(NoteListEntry.Month("October 2026"))
        repeat(3) { add(note(10 + it, "2026-10-05")) }
        add(NoteListEntry.Month("September 2026"))
        add(note(20, "2026-09-05"))
    }

    private fun page(height: Int) = paginateNotes(
        entries, height.dp, noteHeight = 100.dp, monthHeight = 30.dp, otherHeight = 40.dp, gap = 0.dp
    )

    @Test
    fun everyNoteAppearsOnce() {
        for (height in listOf(130, 230, 330, 450, 2000)) {
            val shown = page(height).flatten().filterIsInstance<NoteListEntry.Note>().map { it.note.uniqueId }
            val all = entries.filterIsInstance<NoteListEntry.Note>().map { it.note.uniqueId }
            assertEquals("height $height", all, shown)
        }
    }

    @Test
    fun aPageNeverEndsOnAHeadingAndAlwaysSaysItsMonth() {
        for (height in listOf(130, 230, 330, 450)) {
            page(height).forEach { p ->
                assertFalse("height $height: $p", p.last() is NoteListEntry.Month)
                assertTrue("height $height: $p", p.first() is NoteListEntry.Month)
                assertEquals("height $height: $p", p.size, p.distinct().size)
            }
        }
    }

    @Test
    fun noNotesIsOneEmptyPage() {
        assertEquals(listOf(emptyList<NoteListEntry>()), paginateNotes(emptyList(), 300.dp))
    }
}
