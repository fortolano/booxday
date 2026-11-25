package com.boox.day.tasksync.microsoft

import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * The To Do done copies already taken as the evidence of a phone tick (owner decision 100, final
 * review of 25 Nov 2026): one copy proves the tick of one task only, also in a later pull and after
 * a restart. In the device-only Task Sync prefs, by hash (Graph's ids are long), the newest [KEEP]
 * of the account: a copy dropped then could only prove another tick for an alike task left
 * unchanged, due on that copy's day, through [KEEP] phone ticks since.
 */
internal class UsedDoneCopies(private val prefs: SharedPreferences, accountEmail: String) {
    private val key = "todo_used_done_copies_${accountEmail.hashCode()}"

    /** [copyId] in [listId] already proved the tick of a task other than [taskId]. */
    fun usedByAnother(listId: String, copyId: String, taskId: String): Boolean =
        read()[hash(listId, copyId)]?.let { it != hash(taskId) } == true

    /** [copyId] in [listId] proves the tick of [taskId] from now on. */
    fun use(listId: String, copyId: String, taskId: String) {
        val all = read()
        val copy = hash(listId, copyId)
        if (all[copy] == hash(taskId)) return
        all.remove(copy)
        all[copy] = hash(taskId)
        while (all.size > KEEP) all.remove(all.keys.first())
        prefs.edit().putString(key, all.entries.joinToString(";") { "${it.key}=${it.value}" }).commit()
    }

    /** Oldest first. */
    private fun read(): LinkedHashMap<String, String> {
        val all = LinkedHashMap<String, String>()
        prefs.getString(key, null)?.split(';')?.forEach { entry ->
            val parts = entry.split('=')
            if (parts.size == 2) all[parts[0]] = parts[1]
        }
        return all
    }

    private fun hash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEEP = 500
    }
}
