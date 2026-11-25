package com.boox.day.tasksync.microsoft

import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Old To Do copies a move or regroup left behind when deleting them had no clear answer (final review
 * of 25 Nov 2026): each with what it was when it was copied (a task's etag, a step's words and tick
 * as [TodoFields.stepVersion]), so its delete from the outbox goes only if it is still that, and
 * [KEPT] once it changed since: it then stays, and its next delete never goes either, until it is
 * linked to a tablet task of its own. In the device-only Task Sync prefs, ids by hash, the newest
 * [KEEP] of the account. No user content is stored.
 */
internal class LeftCopies(private val prefs: SharedPreferences, accountEmail: String) {
    private val key = "todo_left_copies_${accountEmail.hashCode()}"

    /** [remoteId] in [listId] was left, as [was] (null: not known, so never deleted). */
    fun left(listId: String, remoteId: String, was: String?) = write { it[hash(listId, remoteId)] = was ?: KEPT }

    /** Whether [remoteId] in [listId] is a copy a move left. */
    fun isLeft(listId: String, remoteId: String): Boolean = read().containsKey(hash(listId, remoteId))

    /** What it was when copied; null when it changed since or isn't known. */
    fun wasOf(listId: String, remoteId: String): String? = read()[hash(listId, remoteId)]?.takeUnless { it == KEPT }

    /** It changed since it was copied: it stays. */
    fun kept(listId: String, remoteId: String) = write { it[hash(listId, remoteId)] = KEPT }

    /** Deleted, or linked to a tablet task of its own: nothing left to hold. */
    fun done(listId: String, remoteIds: Collection<String>) {
        val hashes = remoteIds.mapTo(HashSet()) { hash(listId, it) }
        if (read().keys.any { it in hashes }) write { all -> hashes.forEach { all.remove(it) } }
    }

    private fun write(change: (LinkedHashMap<String, String>) -> Unit) {
        val all = read()
        change(all)
        while (all.size > KEEP) all.remove(all.keys.first())
        prefs.edit().putString(key, all.entries.joinToString("\n") { "${it.key}=${it.value}" }).commit()
    }

    /** Oldest first. */
    private fun read(): LinkedHashMap<String, String> {
        val all = LinkedHashMap<String, String>()
        prefs.getString(key, null)?.split('\n')?.forEach { entry ->
            val at = entry.indexOf('=')
            if (at > 0) all[entry.substring(0, at)] = entry.substring(at + 1)
        }
        return all
    }

    private fun hash(listId: String, remoteId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$listId\u0000$remoteId".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEPT = "-"
        const val KEEP = 200
    }
}
