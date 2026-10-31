package com.boox.day.tasksync

import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import java.security.MessageDigest

/**
 * What a BooxDay task looks like to a task service (Oct 2026). Plan:
 * C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 1.8.
 */
internal object TaskSyncRules {
    /** Handwriting is sent once its recognized words stay the same this long (owner, 31 Oct 2026). */
    const val HANDWRITING_STABLE_MS = 10_000L

    /** After ink changes, nothing is looked at for this long (writing is still going on). */
    const val INK_QUIET_MS = 10_000L

    /** A synced task whose row stays empty (no ink, no words) this long is deleted (owner decision 21). */
    const val EMPTY_ROW_DELETE_MS = 120_000L

    /**
     * The remote id of a tablet-only link: a task already ticked when task sync was turned on stays
     * on the tablet (owner decision 9). A link, so the mark moves with the task (moves, sorts).
     */
    const val TICKED_AT_TURN_ON = "ticked-at-turn-on:"

    fun isTickedAtTurnOn(link: TaskSyncLink): Boolean = link.remoteId.startsWith(TICKED_AT_TURN_ON)

    /**
     * The words to send: typed, else converted, else recognized handwriting (the canvas shows
     * typed over converted; recognized text is never shown but is what handwriting says).
     */
    fun textToSend(task: Task): String? =
        listOf(task.typedText, task.convertedText, task.recognizedText)
            .firstOrNull { !it.isNullOrBlank() }?.trim()

    /** Only handwriting: its words come from recognition and may still change. */
    fun isHandwrittenOnly(task: Task): Boolean =
        task.typedText.isNullOrBlank() && task.convertedText.isNullOrBlank() && !task.recognizedText.isNullOrBlank()

    /**
     * The due day sent: the task's own due day, else the day it sits on. A task with no own due
     * day that moves to another day changes its due day (owner decision 10, the other way round).
     */
    fun dueDay(task: Task): String = task.dueDay ?: task.dayDate

    /**
     * The synced fields, hashed: words, done mark, due day, parent. Not updatedAt, which
     * recognition rewrites on every row of a page. The parent is its synced copy's id, which moves
     * with it, not its row id, which changes when it moves.
     */
    fun contentHash(task: Task, parentRemoteId: String?): String {
        val parent = parentRemoteId ?: if (task.parentTaskId != null) "local" else ""
        val fields = listOf(textToSend(task).orEmpty(), task.isCompleted.toString(), dueDay(task), parent)
        val digest = MessageDigest.getInstance("SHA-256").digest(fields.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
