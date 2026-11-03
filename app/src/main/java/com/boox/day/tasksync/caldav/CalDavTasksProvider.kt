package com.boox.day.tasksync.caldav

import android.content.Context
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavTaskFile
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.security.AccountSecretStore
import com.boox.day.sync.caldav.CalDavConnection
import com.boox.day.sync.caldav.CalDavTasks
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteChanges
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.RemoteTask
import com.boox.day.tasksync.RemoteTaskState
import com.boox.day.tasksync.TaskExtras
import com.boox.day.tasksync.TaskSyncService
import com.boox.day.tasksync.TaskSyncServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * CalDAV task lists as a task service (plan stage 4, Nov 2026): Nextcloud, Fastmail, Radicale and any
 * server whose collections take tasks (VTODO). Not iCloud: Apple keeps Reminders to its own apps.
 * Signed in through Calendar Accounts: the same account, password and trusted certificate.
 *
 * - A list is a collection (its id: the URL path), a task one file (its remote id: the URL path),
 *   kept whole in caldav_task_file so a change made here touches only BooxDay's fields ([CalDavTasks]).
 * - Reading: a list whose change marker didn't move costs one request; else its files' tags are
 *   listed and only new or changed files are downloaded. A change whose merge had to wait (the pull
 *   keeps its old cursor) comes again from the stored file: a linked task whose version isn't the
 *   one last merged, an unlinked unticked task, a linked task whose file is gone.
 * - Writing: a new task is a new file named by its own UUID (a task row is a slot, reused after a
 *   clear); a copy made by a send whose reply was lost is linked by the pull that runs before every
 *   send ("match before create"). Each change is guarded by the file's etag: changed on the server
 *   meanwhile, it is [TaskSyncServiceException.RemoteChanged], merged, and sent once more.
 * - Priority and reminder sync too (owner decisions 67, 69).
 * - A repeating task (owner decision 76, Nov 2026) is one task per due day of the coming week
 *   ([WINDOW_DAYS]); each day's remote id is the file's plus "#" and the day's key (a path never
 *   holds a raw "#"). Ticking or changing a day writes that day's exception into the file, clearing
 *   it adds an EXDATE; a day can't move to another list. Days already on the tablet stay after
 *   their week has passed; a day removed from the series on the server is a delete.
 */
internal class CalDavTasksProvider(
    private val db: AppDatabase,
    private val accountId: String,
    /** The sign-in name, as task_sync_link's accountEmail. */
    private val accountEmail: String,
    private val serverUrl: String,
    private val calendarHomeUrl: String,
    private val connection: CalDavConnection,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis
) : TaskSyncService {

    companion object {
        /** The CalDAV account [accountId] of Calendar Accounts; [TaskSyncServiceException.SignInNeeded] without it or its password. */
        suspend fun create(context: Context, accountId: String): CalDavTasksProvider = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val db = AppDatabase.getDatabase(app)
            val account = db.connectedAccountDao().getById(accountId) ?: throw TaskSyncServiceException.SignInNeeded()
            val dav = db.calDavDao().getAccount(accountId) ?: throw TaskSyncServiceException.SignInNeeded()
            // Unreadable (Keystore reset, restored on another tablet): signed in again in Calendar Accounts
            val password = AccountSecretStore.get(app).read(accountId) ?: throw TaskSyncServiceException.SignInNeeded()
            CalDavTasksProvider(
                db, accountId, account.email, account.serverUrl, dav.calendarHomeUrl,
                CalDavConnection(dav.username, password, dav.pinnedCertSha256)
            )
        }

        /** Servers that don't move a file between collections: it is copied and the original deleted. */
        private val NO_MOVE = setOf(403, 405, 501, 502)

        /** A repeating task comes for its due days from today to 6 days on (owner decision 76: the coming week). */
        internal const val WINDOW_DAYS = 7L

        /** The remote id of day [key] of the repeating task in file [href]. */
        internal fun occurrenceId(href: String, key: String) = "$href#$key"

        /** The file a remote id is in. */
        internal fun fileOf(remoteId: String) = remoteId.substringBefore('#')

        /** The day key of a repeating task's day, or null for a whole file. */
        internal fun dayKeyOf(remoteId: String) = remoteId.substringAfter('#', "").ifEmpty { null }
    }

    override val provider: String = TaskSyncProvider.CALDAV
    override val carriesExtras: Boolean = true

    private val files = db.calDavDao()
    private val sync = db.taskSyncDao()

    // ── Reading ──

    override suspend fun lists(): List<RemoteList> = call {
        connection.listTaskLists(calendarHomeUrl).map { RemoteList(it.href, it.name) }
    }

    override suspend fun changes(listId: String, cursor: String?): RemoteChanges = call(listId) {
        // The cursor holds the day too: a new day brings a repeating task's next day with no change on the server
        val today = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()
        val tag = connection.collectionTag(url(listId))?.let { "$it|$today" }
        if (tag != null && tag == cursor) return@call RemoteChanges(emptyList(), cursor)
        val listed = connection.taskTags(url(listId))
        val stored = io { files.getTaskFiles(accountId, listId) }.associateBy { it.href }
        val fresh = download(listId, listed.filter { (href, etag) -> etag == null || stored[href]?.etag != etag }.keys)
        val links = io { sync.getLinksInList(provider, accountEmail, serverUrl, listId) }.associateBy { it.remoteId }
        val dayLinks = links.filterKeys { dayKeyOf(it) != null }.entries.groupBy({ fileOf(it.key) }, { it.value })

        val tasks = mutableListOf<RemoteTaskState>()
        for (href in listed.keys) {
            val file = fresh[href] ?: stored[href] ?: continue
            val days = CalDavTasks.occurrences(file.ics, zone(), today, today.plusDays(WINDOW_DAYS - 1))
            if (days != null) {
                tasks += dayChanges(file, days, links, dayLinks[href].orEmpty(), changed = href in fresh)
                continue
            }
            val link = links[href]
            // New or changed now, or a change whose merge waited
            if (href !in fresh && link != null && link.remoteVersion == file.etag) continue
            val state = stateOf(file) ?: continue
            // Not linked: only what would come to the tablet (unticked, with words; owner decision 27)
            if (link == null && (state.completed || state.title.isBlank())) continue
            tasks += state
        }
        // Gone from the server: a copy linked to it is reported deleted, again at later pulls while its
        // merge waits (the link stays until then); its stored file stays as long, so the report can
        // say whether it was ticked (owner decision 26)
        val waiting = links.values.filter { !it.keptOnTablet }.map { fileOf(it.remoteId) }.toSet()
        val forgotten = (stored.keys - listed.keys).filter { it !in waiting }
        io { db.runInTransaction<Unit> { forgotten.forEach { files.deleteTaskFile(accountId, it) } } }
        for ((remoteId, link) in links) {
            if (fileOf(remoteId) !in listed && !link.keptOnTablet) tasks += goneState(listId, remoteId, stored[fileOf(remoteId)])
        }
        RemoteChanges(tasks, tag)
    }

    /**
     * A repeating task's days: those of the coming week that changed or aren't on the tablet yet
     * (unticked, with words, as any new task), and the days on the tablet from other weeks when the
     * file changed: still in the series they come as they are, removed from it as deleted.
     */
    private suspend fun dayChanges(
        file: CalDavTaskFile,
        days: List<CalDavTasks.Occurrence>,
        links: Map<String, TaskSyncLink>,
        linkedDays: List<TaskSyncLink>,
        changed: Boolean
    ): List<RemoteTaskState> {
        val out = mutableListOf<RemoteTaskState>()
        val inWeek = mutableSetOf<String>()
        for (day in days) {
            val id = occurrenceId(file.href, day.key)
            inWeek += id
            val link = links[id]
            if (!changed && link != null && link.remoteVersion == file.etag) continue
            val state = dayState(file, id, day.read)
            if (link == null && (state.completed || state.title.isBlank())) continue
            out += state
        }
        for (link in linkedDays) {
            if (link.remoteId in inWeek || (!changed && link.remoteVersion == file.etag)) continue
            val read = CalDavTasks.occurrence(file.ics, zone(), dayKeyOf(link.remoteId)!!)
            out += if (read != null) dayState(file, link.remoteId, read)
            else if (!link.keptOnTablet) goneState(file.listHref, link.remoteId, file) else continue
        }
        return out
    }

    override suspend fun fetch(listId: String, remoteId: String): RemoteTaskState? = call(listId) {
        val href = fileOf(remoteId)
        val file = download(listId, listOf(href))[href]
        if (file == null) io { files.deleteTaskFile(accountId, href) }
        val day = dayKeyOf(remoteId)
        if (day != null) return@call file?.let { f -> CalDavTasks.occurrence(f.ics, zone(), day)?.let { dayState(f, remoteId, it) } }
        // Here a repeating task counts as there: null would read as deleted
        file?.let { stateOf(it, leaveOutRepeating = false) }
    }

    /** The files, and the days on the tablet of repeating tasks still there (a day removed from its series is caught by [changes]). */
    override suspend fun allIds(listId: String): Set<String> = call(listId) {
        val listed = connection.taskTags(url(listId)).keys
        val days = io { sync.getLinksInList(provider, accountEmail, serverUrl, listId) }
            .map { it.remoteId }.filter { dayKeyOf(it) != null && fileOf(it) in listed }
        listed + days
    }

    /** A copy moved on the phone keeps its file name or its words and due day (owner decision 49). */
    override suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long): RemoteTaskState? {
        val name = fileOf(remoteId).substringAfterLast('/')
        val day = dayKeyOf(remoteId)
        for (list in lists()) {
            if (list.listId in skip) continue
            val listed = try {
                call(list.listId) { connection.taskTags(url(list.listId)) }
            } catch (e: TaskSyncServiceException.ListGone) {
                continue  // deleted meanwhile: nothing there
            }
            val sameName = listed.keys.firstOrNull { it.substringAfterLast('/') == name }
            if (sameName != null && day != null) {
                // A repeating task's day: the same day of the series moved there
                return call(list.listId) {
                    download(list.listId, listOf(sameName))[sameName]?.let { f ->
                        CalDavTasks.occurrence(f.ics, zone(), day)?.let { dayState(f, occurrenceId(sameName, day), it) }
                    }
                }
            }
            if (sameName != null) return call(list.listId) { download(list.listId, listOf(sameName))[sameName]?.let { stateOf(it) } }
            if (title == null || day != null) continue
            val found = call(list.listId) { download(list.listId, listed.keys) }.values.mapNotNull { stateOf(it) }
            found.firstOrNull { it.title == title && it.dueDay == dueDay && (it.modifiedAt ?: Long.MAX_VALUE) >= since }?.let { return it }
        }
        return null
    }

    // ── Writing ──

    override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = call {
        val parentUid = task.parentRemoteId?.let { io { files.getTaskFile(accountId, it) }?.uid }
        val fields = CalDavTasks.Fields(
            title = task.title, completed = task.completed, dueDay = task.dueDay, parentUid = parentUid,
            priority = task.extras?.priority.orEmpty(), reminderMinute = task.extras?.reminderMinute
        )
        when {
            link == null -> create(task.listId, fields)
            dayKeyOf(link.remoteId) != null -> updateDay(link, fields)
            else -> update(link, fields)
        }
    }

    /** One day of a repeating task: its exception in the file, guarded by the file's etag like [update]. */
    private suspend fun updateDay(link: TaskSyncLink, fields: CalDavTasks.Fields): RemoteTask {
        val href = fileOf(link.remoteId)
        val day = dayKeyOf(link.remoteId)!!
        val stored = io { files.getTaskFile(accountId, href) }
            ?: download(link.listId, listOf(href))[href]
            ?: throw TaskSyncServiceException.NotFound()
        val current = CalDavTasks.occurrence(stored.ics, zone(), day) ?: throw TaskSyncServiceException.NotFound()
        if (stored.etag != link.remoteVersion) throw TaskSyncServiceException.RemoteChanged(dayState(stored, link.remoteId, current))
        // A day keeps the series' parent; it has none of its own
        val seriesParent = CalDavTasks.read(stored.ics, zone())?.parentUid
        val ics = CalDavTasks.editOccurrence(stored.ics, day, fields.copy(parentUid = seriesParent), zone(), now())
        val etag = putDay(link.listId, href, link.remoteId, ics, stored.etag)
        val file = saved(link.listId, href, ics, etag) ?: throw TaskSyncServiceException.NotFound()
        return RemoteTask(link.listId, link.remoteId, file.etag)
    }

    /** Writes a repeating task's file for one of its days; changed on the server meanwhile, that day is merged first. */
    private suspend fun putDay(listId: String, href: String, remoteId: String, ics: String, etag: String?): String? = try {
        connection.putEvent(url(href), ics, ifMatch = etag, create = false)
    } catch (e: CalDavConnection.Failure.Server) {
        when (e.code) {
            412 -> throw download(listId, listOf(href))[href]
                ?.let { f -> CalDavTasks.occurrence(f.ics, zone(), dayKeyOf(remoteId)!!)?.let { dayState(f, remoteId, it) } }
                ?.let { TaskSyncServiceException.RemoteChanged(it) }
                ?: TaskSyncServiceException.NotFound(e)
            404, 410 -> {
                io { files.deleteTaskFile(accountId, href) }
                throw TaskSyncServiceException.NotFound(e)
            }
            else -> throw e
        }
    }

    private suspend fun create(listId: String, fields: CalDavTasks.Fields): RemoteTask {
        val uid = UUID.randomUUID().toString()
        val href = listId + "$uid.ics"
        val ics = CalDavTasks.newTask(uid, fields, zone(), now())
        val etag = connection.putEvent(url(href), ics, ifMatch = null, create = true)
        val file = saved(listId, href, ics, etag) ?: throw IllegalStateException("The new task isn't on the server")
        return RemoteTask(listId, href, file.etag)
    }

    private suspend fun update(link: TaskSyncLink, fields: CalDavTasks.Fields): RemoteTask {
        val stored = io { files.getTaskFile(accountId, link.remoteId) }
            ?: download(link.listId, listOf(link.remoteId))[link.remoteId]
            ?: throw TaskSyncServiceException.NotFound()
        // A newer version than the one last merged (its merge waited): merged first, like a clash
        if (stored.etag != link.remoteVersion) stateOf(stored, leaveOutRepeating = false)?.let { throw TaskSyncServiceException.RemoteChanged(it) }
        val ics = CalDavTasks.edit(stored.ics, fields, zone(), now())
        val etag = try {
            connection.putEvent(url(link.remoteId), ics, ifMatch = stored.etag, create = false)
        } catch (e: CalDavConnection.Failure.Server) {
            when (e.code) {
                // Changed on the server since: nothing was written
                412 -> throw download(link.listId, listOf(link.remoteId))[link.remoteId]
                    ?.let { stateOf(it, leaveOutRepeating = false) }?.let { TaskSyncServiceException.RemoteChanged(it) }
                    ?: TaskSyncServiceException.NotFound(e)
                404, 410 -> {
                    io { files.deleteTaskFile(accountId, link.remoteId) }
                    throw TaskSyncServiceException.NotFound(e)
                }
                else -> throw e
            }
        }
        val file = saved(link.listId, link.remoteId, ics, etag) ?: throw TaskSyncServiceException.NotFound()
        return RemoteTask(link.listId, link.remoteId, file.etag)
    }

    override suspend fun delete(listId: String, remoteId: String) = call {
        val day = dayKeyOf(remoteId)
        if (day != null) return@call deleteDay(listId, remoteId, day)
        connection.deleteEvent(url(remoteId), ifMatch = null)  // one already gone counts as deleted
        io { files.deleteTaskFile(accountId, remoteId) }
    }

    /** Removes one day of a repeating task (EXDATE); a file or a day already gone counts as deleted. */
    private suspend fun deleteDay(listId: String, remoteId: String, day: String) {
        val href = fileOf(remoteId)
        repeat(2) { attempt ->
            // Read fresh: the delete has no merge to fall back on, so it works on the server's version
            val file = download(listId, listOf(href))[href]
            if (file == null) {
                io { files.deleteTaskFile(accountId, href) }
                return
            }
            if (CalDavTasks.occurrence(file.ics, zone(), day) == null) return
            val ics = CalDavTasks.removeOccurrence(file.ics, day, now())
            try {
                saved(listId, href, ics, connection.putEvent(url(href), ics, ifMatch = file.etag, create = false))
                return
            } catch (e: CalDavConnection.Failure.Server) {
                if (e.code == 404 || e.code == 410) {
                    io { files.deleteTaskFile(accountId, href) }
                    return
                }
                if (e.code != 412 || attempt == 1) throw e
            }
        }
    }

    /**
     * Moves the file to [toListId] under the same name (WebDAV MOVE, else a copy and a delete). Its
     * UID stays, so its subtasks' RELATED-TO still names it; [parentRemoteId] is already right.
     */
    override suspend fun moveToList(fromListId: String, remoteId: String, toListId: String, parentRemoteId: String?): RemoteTaskState = call {
        // One day of a repeating task can't leave its series (as Google refuses a repeating task)
        if (dayKeyOf(remoteId) != null) throw TaskSyncServiceException.Refused()
        val target = toListId + remoteId.substringAfterLast('/')
        try {
            connection.moveFile(url(remoteId), url(target))
        } catch (e: CalDavConnection.Failure.Server) {
            if (e.code !in NO_MOVE) throw e
            val stored = io { files.getTaskFile(accountId, remoteId) }
                ?: download(fromListId, listOf(remoteId))[remoteId] ?: throw TaskSyncServiceException.NotFound(e)
            connection.putEvent(url(target), stored.ics, ifMatch = null, create = true)
            connection.deleteEvent(url(remoteId), ifMatch = null)
        }
        io { files.deleteTaskFile(accountId, remoteId) }
        val moved = download(toListId, listOf(target))[target] ?: throw TaskSyncServiceException.NotFound()
        stateOf(moved, leaveOutRepeating = false) ?: throw TaskSyncServiceException.NotFound()
    }

    // ── Steps ──

    /** The files at [hrefs] of [listId], stored as the server now has them; gone ones are left out. */
    private suspend fun download(listId: String, hrefs: Collection<String>): Map<String, CalDavTaskFile> {
        if (hrefs.isEmpty()) return emptyMap()
        val got = connection.downloadEvents(url(listId), hrefs.toList()).map { file ->
            CalDavTaskFile(accountId, file.href, listId, file.etag, file.ics, CalDavTasks.read(file.ics, zone())?.uid, now())
        }
        io { db.runInTransaction<Unit> { got.forEach(files::upsertTaskFile) } }
        return got.associateBy { it.href }
    }

    /**
     * Stores what was just written. With a change tag the text sent is the server's; without one
     * the server changed it (RFC 4791 5.3.4), so its version is read back.
     */
    private suspend fun saved(listId: String, href: String, ics: String, etag: String?): CalDavTaskFile? {
        if (etag == null) return download(listId, listOf(href))[href]
        val file = CalDavTaskFile(accountId, href, listId, etag, ics, CalDavTasks.read(ics, zone())?.uid, now())
        io { files.upsertTaskFile(file) }
        return file
    }

    /** Null: unreadable, or a repeating task, which comes by its days instead ([dayChanges], owner decision 76). */
    private suspend fun stateOf(file: CalDavTaskFile, leaveOutRepeating: Boolean = true): RemoteTaskState? {
        val read = CalDavTasks.read(file.ics, zone()) ?: return null
        if (leaveOutRepeating && read.repeats) return null
        val parent = read.parentUid?.let { uid -> io { files.getTaskFileByUid(accountId, file.listHref, uid) }?.href }
        return RemoteTaskState(
            listId = file.listHref, remoteId = file.href, title = read.title, completed = read.completed,
            dueDay = read.dueDay, parentRemoteId = parent, deleted = false, version = file.etag,
            modifiedAt = read.modifiedAt, extras = TaskExtras(read.priority, read.reminderMinute)
        )
    }

    /** One day of a repeating task as the engine sees it: no parent, the file's etag as its version. */
    private fun dayState(file: CalDavTaskFile, remoteId: String, read: CalDavTasks.Read) = RemoteTaskState(
        listId = file.listHref, remoteId = remoteId, title = read.title, completed = read.completed,
        dueDay = read.dueDay, parentRemoteId = null, deleted = false, version = file.etag,
        modifiedAt = read.modifiedAt, extras = TaskExtras(read.priority, read.reminderMinute)
    )

    /** A deleted copy; ticked when its last stored version was (owner decision 26). */
    private fun goneState(listId: String, href: String, last: CalDavTaskFile?): RemoteTaskState {
        val read = last?.let { f -> dayKeyOf(href)?.let { CalDavTasks.occurrence(f.ics, zone(), it) } ?: CalDavTasks.read(f.ics, zone()) }
        return RemoteTaskState(
            listId = listId, remoteId = href, title = read?.title.orEmpty(), completed = read?.completed == true,
            dueDay = read?.dueDay, parentRemoteId = null, deleted = true, version = null
        )
    }

    /** A path of the account's server as a full address. */
    private fun url(path: String): String = calendarHomeUrl.toHttpUrl().resolve(path)?.toString() ?: path

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /**
     * The engine's failure kinds. [listId]: the call reads that list, so a 404 or 410 means the list
     * was deleted on the server (owner decision 23).
     */
    private suspend fun <T> call(listId: String? = null, block: suspend () -> T): T = try {
        block()
    } catch (e: TaskSyncServiceException) {
        throw e
    } catch (e: CalDavConnection.Failure.WrongPassword) {
        throw TaskSyncServiceException.SignInNeeded(e)
    } catch (e: CalDavConnection.Failure.UntrustedCertificate) {
        throw TaskSyncServiceException.SignInNeeded(e)  // a changed certificate: trusted again in Calendar Accounts
    } catch (e: CalDavConnection.Failure.Offline) {
        throw TaskSyncServiceException.Offline(e)
    } catch (e: CalDavConnection.Failure.Server) {
        throw when {
            e.code == 429 || e.code == 503 -> TaskSyncServiceException.Busy(null, e)
            listId != null && (e.code == 404 || e.code == 410) -> TaskSyncServiceException.ListGone(listId, e)
            else -> e
        }
    }
}
