package com.boox.day.tasksync

import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.UUID

/**
 * H15: the mark of a new task's create, in the device-only Task Sync prefs. Written before each send
 * of the create (its op id, kept across tries, and a hash of the parent and words of that send); cleared once
 * the task's link is saved, by the send itself or by a pull that links the copy the create made when
 * its answer was lost (TaskSyncMerger). The key is the one TaskSyncSender used before (Oct 2026).
 */
internal class CreateMarks(
    private val prefs: SharedPreferences,
    private val provider: String,
    private val accountEmail: String,
    private val serverUrl: String = ""
) {
    /** The op id of an earlier send of [taskId]'s create, if one went. */
    fun opIdOf(taskId: String): String? = prefs.getString(key(taskId), null)

    /** Before a send of [taskId]'s create under [parentRemoteId] with [words]; returns its op id. */
    fun mark(taskId: String, parentRemoteId: String?, words: String): String {
        val opId = opIdOf(taskId) ?: UUID.randomUUID().toString()
        prefs.edit().putString(key(taskId), opId).putString(scopeKey(taskId), scope(parentRemoteId, words)).commit()
        return opId
    }

    /** A create of [taskId] was sent under [parentRemoteId] with [words], and its link never saved. */
    fun sent(taskId: String, parentRemoteId: String?, words: String): Boolean =
        opIdOf(taskId) != null && prefs.getString(scopeKey(taskId), null) == scope(parentRemoteId, words)

    /** The task's link is saved. */
    fun clear(taskId: String) {
        prefs.edit().remove(key(taskId)).remove(scopeKey(taskId)).apply()
    }

    private fun key(taskId: String) = "pending_create_${provider}_${accountEmail.hashCode()}_${serverUrl.hashCode()}_$taskId"

    private fun scopeKey(taskId: String) = key(taskId) + "_sent"

    /**
     * The parent and words of a send, by hash: a task's words are never stored. A send that never
     * reached the service and an identical step added on the phone under the same parent then look
     * alike, and the two identical steps become one row: accepted, nothing is lost.
     */
    private fun scope(parentRemoteId: String?, words: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((parentRemoteId.orEmpty() + "\u0000" + words).toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
