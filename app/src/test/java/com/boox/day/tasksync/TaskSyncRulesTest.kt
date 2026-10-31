package com.boox.day.tasksync

import com.boox.day.data.db.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TaskSyncRulesTest {

    @Test
    fun `typed words win over converted and recognized`() {
        assertEquals("Typed", TaskSyncRules.textToSend(task(typed = "Typed", converted = "Converted", recognized = "Ink")))
        assertEquals("Converted", TaskSyncRules.textToSend(task(converted = " Converted ", recognized = "Ink")))
        assertEquals("Ink", TaskSyncRules.textToSend(task(recognized = "Ink")))
        assertNull("blank rows are never sent", TaskSyncRules.textToSend(task(typed = "  ", recognized = "")))
    }

    @Test
    fun `only handwriting is handwriting`() {
        assertTrue(TaskSyncRules.isHandwrittenOnly(task(recognized = "Ink")))
        assertFalse(TaskSyncRules.isHandwrittenOnly(task(typed = "Typed", recognized = "Ink")))
        assertFalse(TaskSyncRules.isHandwrittenOnly(task()))
    }

    @Test
    fun `the due day is the task's own, else its day`() {
        assertEquals("2026-11-05", TaskSyncRules.dueDay(task(dueDay = "2026-11-05")))
        assertEquals("2026-11-02", TaskSyncRules.dueDay(task()))
    }

    @Test
    fun `the hash follows the synced fields only`() {
        val base = task(typed = "Call mom")
        val hash = TaskSyncRules.contentHash(base, null)

        assertEquals(
            "recognition rewriting updatedAt changes nothing",
            hash, TaskSyncRules.contentHash(base.copy(updatedAt = Date(99_999), priority = "A", estimatedMinutes = 30), null)
        )
        assertNotEquals(hash, TaskSyncRules.contentHash(base.copy(typedText = "Call dad"), null))
        assertNotEquals(hash, TaskSyncRules.contentHash(base.copy(isCompleted = true), null))
        assertNotEquals(hash, TaskSyncRules.contentHash(base.copy(dueDay = "2026-11-09"), null))
        assertNotEquals("moved to another day", hash, TaskSyncRules.contentHash(base.copy(dayDate = "2026-11-03"), null))
    }

    @Test
    fun `a subtask's parent is its synced copy, which moves with it`() {
        val child = task(typed = "Book hotel").copy(parentTaskId = "row-7")
        val hash = TaskSyncRules.contentHash(child, "g-trip")

        assertEquals("parent moved to another row", hash, TaskSyncRules.contentHash(child.copy(parentTaskId = "row-3"), "g-trip"))
        assertNotEquals(hash, TaskSyncRules.contentHash(child, "g-other"))
        assertNotEquals(hash, TaskSyncRules.contentHash(child.copy(parentTaskId = null), null))
    }

    private fun task(
        typed: String? = null,
        converted: String? = null,
        recognized: String? = null,
        dueDay: String? = null
    ) = Task(
        id = "t-1",
        dayDate = "2026-11-02",
        taskPageIndex = 0,
        rowIndex = 0,
        pageId = "page-1",
        typedText = typed,
        convertedText = converted,
        recognizedText = recognized,
        dueDay = dueDay,
        createdAt = Date(1),
        updatedAt = Date(1)
    )
}
