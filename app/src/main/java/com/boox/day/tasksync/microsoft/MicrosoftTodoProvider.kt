package com.boox.day.tasksync.microsoft

import android.content.Context
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.microsoft.GraphException
import com.boox.day.sync.microsoft.MicrosoftGraph
import com.boox.day.sync.microsoft.MicrosoftScopes
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteChanges
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.RemoteTask
import com.boox.day.tasksync.RemoteTaskState
import com.boox.day.tasksync.TaskExtras
import com.boox.day.tasksync.TaskSyncService
import com.boox.day.tasksync.TaskSyncServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Microsoft To Do as a task service (Nov 2026, owner decisions 79-84), on Microsoft Graph with the
 * Outlook account already in Calendar Accounts and its own Tasks.ReadWrite permission, asked when
 * Task Sync is turned on. Plan: C:\Users\fort\.claude\plans\microsoft-todo-onedrive.md, part 1.
 *
 * - **Lists:** every To Do list but "Flagged email" (decision 84); the main one is To Do's "Tasks".
 * - **Reading:** Graph's delta per list with each task's steps; the cursor is the round's
 *   deltaLink, and one Microsoft forgot starts a new round. A step change changes its task, so it
 *   comes with it (measured 25 Nov 2026).
 * - **Steps are BooxDay's subtasks** (decision 79): words and tick only; a step's remote id is its
 *   task's id, [TodoFields.STEP] and its own. A step added on the phone comes as a normal task on
 *   its parent's day (decision 24); a subtask's due day, reminder and priority stay on the tablet.
 * - **Priority A is To Do's star** (decision 80); B and C stay on the tablet ([TodoFields]).
 *   **Reminder** like CalDAV (decision 81): a task's own reminder time on its due day.
 * - **Dates** always go as UTC, worked out on the tablet: Microsoft's time-zone rules and the
 *   tablet's can differ, and BooxDay follows the tablet's clock. A due day is midnight UTC of the
 *   day, as To Do itself keeps due days; a reminder is its real moment ([TodoFields], 25 Nov 2026).
 * - **Repeating tasks** (decision 82): the current one comes in. Ticked, To Do keeps the task's id
 *   for the next one and makes a new done copy with the same step ids (measured 25 Nov 2026): the
 *   tablet's task and its subtasks follow the done copy, ticked here ([upsert]) or on the phone
 *   ([RemoteTaskState.takesOverRemoteId]); the next one comes in with its steps as new tasks.
 * - **Moving to another list** (decision 83): a copy in the new list and the old one deleted; a
 *   task with attachments or a repeat is refused.
 * - An update first reads the copy and stops with [TaskSyncServiceException.RemoteChanged] if it
 *   changed since the last sync, then changes only what changed on the tablet; notes are never touched.
 *
 * Never log a task's words or a request or answer body.
 */
internal class MicrosoftTodoProvider(
    private val graph: MicrosoftGraph,
    /** The links of a list: which steps are on the tablet (a step deleted with its task has no delta entry of its own). */
    private val linksIn: suspend (listId: String) -> List<TaskSyncLink> = { emptyList() },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) : TaskSyncService {

    override val provider: String = TaskSyncProvider.MICROSOFT_TODO
    override val carriesExtras: Boolean = true

    /** A done copy keeps its steps' ids (measured 25 Nov 2026): a step of [replaced] is the same step of [replacement]. */
    override fun subtaskUnder(replacement: String, subtaskRemoteId: String, replaced: String): String? =
        if (TodoFields.isStep(subtaskRemoteId) && TodoFields.taskOf(subtaskRemoteId) == replaced) {
            TodoFields.stepRemoteId(replacement, TodoFields.stepOf(subtaskRemoteId))
        } else null

    override fun keptPriority(priority: String): String = TodoFields.keptPriority(priority)

    /** The steps the last [moveToList] copied: old remote id to new one, for [takenAlong]. */
    private val movedSteps = HashMap<String, String>()

    // ── Lists ──

    override suspend fun lists(): List<RemoteList> = call { todoLists().map(::remoteListOf) }

    /** To Do's own list ("Tasks"). */
    override suspend fun mainList(): RemoteList? = call {
        val lists = todoLists()
        (lists.firstOrNull { it.str("wellknownListName") == DEFAULT_LIST } ?: lists.firstOrNull())?.let(::remoteListOf)
    }

    /** Every list but "Flagged email" (owner decision 84). */
    private suspend fun todoLists(): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        try {
            pages(LISTS) { found += it.items() }
        } catch (e: GraphException.Http) {
            if (e.status == 404) throw TaskSyncServiceException.NotAvailable(e)  // a mailbox without To Do
            throw e
        }
        return found.filter { it.str("id") != null && it.str("wellknownListName") != FLAGGED_EMAILS }
    }

    // ── Reading ──

    override suspend fun changes(listId: String, cursor: String?): RemoteChanges = call {
        val round = onList(listId) {
            try {
                delta(listId, cursor)
            } catch (e: StaleCursor) {
                delta(listId, null)  // Microsoft forgot where the last round ended: all again, which is harmless
            }
        }
        val links = linksIn(listId).filter { !it.keptOnTablet }
        val linked = links.mapTo(HashSet()) { it.remoteId }
        val linkOf = links.associateBy { it.remoteId }
        val stepLinks = links.filter { TodoFields.isStep(it.remoteId) }.groupBy { TodoFields.taskOf(it.remoteId) }
        val out = mutableListOf<RemoteTaskState>()
        val present = mutableListOf<JsonObject>()
        for (item in round.items) {
            val id = item.str("id") ?: continue
            if (item.containsKey(REMOVED)) {
                out += goneState(listId, id)
                // Its steps went with it
                stepLinks[id].orEmpty().forEach { out += goneState(listId, it.remoteId) }
            } else {
                present += item
            }
        }
        val takesOver = doneCopiesTakingOver(present, linkOf)
        for (item in present) {
            val id = item.str("id")!!
            val task = taskState(listId, item).copy(takesOverRemoteId = takesOver[id])
            out += task
            val steps = stepItems(item) ?: continue  // steps not listed: none taken as deleted
            // A done copy taking over: its steps are the tablet's subtasks from now on, so they come too
            val finished = task.completed && task.takesOverRemoteId == null
            out += stepChanges(listId, id, finished, steps, linked, stepLinks[id].orEmpty())
        }
        RemoteChanges(out, round.deltaLink)
    }

    /**
     * Repeating tasks ticked on the phone (owner decision 82): To Do keeps the task's id for the next
     * one and makes a new done copy with the same title and repeat (measured 25 Nov 2026). An open
     * task on the tablet that changed since it was last seen and is now due later is taken over by a
     * done copy of its series due earlier, made after that last-seen version: the one due on the
     * tablet's day, else the earliest. An unchanged task (a full round, old done copies) never is.
     * Done copy id to the id it takes over.
     */
    private fun doneCopiesTakingOver(present: List<JsonObject>, linkOf: Map<String, TaskSyncLink>): Map<String, String> {
        val doneCopies = present.filter { it.str("status") == COMPLETED && it.obj("recurrence") != null && it.str("id") !in linkOf }
        if (doneCopies.isEmpty()) return emptyMap()
        val takes = HashMap<String, String>()
        for (open in present) {
            if (open.str("status") == COMPLETED || open.obj("recurrence") == null) continue
            val id = open.str("id") ?: continue
            val link = linkOf[id] ?: continue  // not on the tablet: nothing to keep
            if (open.str(MODIFIED) == link.remoteVersion) continue  // as the tablet last saw it: not ticked since
            val lastSeen = instantOf(link.remoteVersion) ?: continue
            val due = dueOf(open) ?: continue
            if (!link.remoteUndated && link.syncedDueDay != null && due <= link.syncedDueDay) continue  // not moved on to a next one
            val candidates = doneCopies.filter { copy ->
                copy.str("id") !in takes && TodoFields.sameSeries(copy, open) && (dueOf(copy) ?: "") < due &&
                    (instantOf(copy.str("createdDateTime")) ?: Instant.MIN) >= lastSeen
            }
            val copy = candidates.firstOrNull { dueOf(it) == link.syncedDueDay } ?: candidates.minByOrNull { dueOf(it).orEmpty() } ?: continue
            takes[copy.str("id")!!] = id
        }
        return takes
    }

    /** A task's steps as read now, and the ones on the tablet that are gone. */
    private fun stepChanges(
        listId: String,
        taskId: String,
        taskDone: Boolean,
        steps: List<JsonObject>,
        linked: Set<String>,
        onTablet: List<TaskSyncLink>
    ): List<RemoteTaskState> {
        val states = steps.mapNotNull { stepState(listId, taskId, it) }
        val there = states.mapTo(HashSet()) { it.remoteId }
        // A finished task's steps don't come to the tablet as new tasks (like decision 27's finished tasks)
        return states.filter { it.remoteId in linked || !taskDone } +
            onTablet.filter { it.remoteId !in there }.map { goneState(listId, it.remoteId) }
    }

    private class StaleCursor : Exception("Delta cursor expired")

    private class Round(val items: List<JsonObject>, val deltaLink: String)

    /** One delta round from [cursor] (null: everything), every page of it. */
    private suspend fun delta(listId: String, cursor: String?): Round {
        val items = mutableListOf<JsonObject>()
        var next = cursor ?: "${tasksPath(listId)}/delta?\$expand=checklistItems"
        while (true) {
            val page = try {
                get(next)
            } catch (e: GraphException.Http) {
                if (cursor != null && isStale(e)) throw StaleCursor()
                throw e
            }
            items += page.items()
            val more = page.str(NEXT_LINK)
            if (more != null) {
                next = more
                continue
            }
            return Round(items, page.str(DELTA_LINK) ?: throw IllegalStateException("A delta round without its end"))
        }
    }

    override suspend fun fetch(listId: String, remoteId: String): RemoteTaskState? = call {
        if (TodoFields.isStep(remoteId)) {
            val taskId = TodoFields.taskOf(remoteId)
            orNull(listId) { get(stepPath(listId, taskId, TodoFields.stepOf(remoteId))) }?.let { stepState(listId, taskId, it) }
        } else {
            orNull(listId) { get(taskPath(listId, remoteId)) }?.let { taskState(listId, it) }
        }
    }

    /** A moved task's steps have new ids in the new list. */
    override suspend fun takenAlong(toListId: String, remoteId: String): RemoteTaskState? =
        fetch(toListId, movedSteps[remoteId] ?: remoteId)

    override suspend fun allIds(listId: String): Set<String> = call {
        val stepsOnTablet = linksIn(listId).map { it.remoteId }.filter(TodoFields::isStep).groupBy(TodoFields::taskOf)
        val ids = HashSet<String>()
        onList(listId) {
            pages("${tasksPath(listId)}?\$expand=checklistItems&\$top=$PAGE_SIZE") { page ->
                for (item in page.items()) {
                    val id = item.str("id") ?: continue
                    ids += id
                    val steps = stepItems(item)
                    if (steps == null) {
                        ids += stepsOnTablet[id].orEmpty()  // steps not listed: none taken as deleted
                    } else {
                        steps.mapNotNullTo(ids) { step -> step.str("id")?.let { TodoFields.stepRemoteId(id, it) } }
                    }
                }
            }
        }
        ids
    }

    /** A copy moved on the phone gets a new id in To Do: found by its words and due day, or a step by its words (owner decision 49). */
    override suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long): RemoteTaskState? {
        val step = TodoFields.isStep(remoteId)
        for (list in lists()) {
            if (list.listId in skip) continue
            val found = try {
                call { onList(list.listId) { lookIn(list.listId, remoteId, step, title, dueDay, since) } }
            } catch (e: TaskSyncServiceException.ListGone) {
                null  // deleted meanwhile: nothing there
            }
            if (found != null) return found
        }
        return null
    }

    private suspend fun lookIn(listId: String, remoteId: String, step: Boolean, title: String?, dueDay: String?, since: Long): RemoteTaskState? {
        var match: RemoteTaskState? = null
        pages("${tasksPath(listId)}?\$expand=checklistItems&\$top=$PAGE_SIZE") { page ->
            for (item in page.items()) {
                if (match != null) return@pages
                val task = taskState(listId, item)
                val recent = (task.modifiedAt ?: Long.MAX_VALUE) >= since
                match = if (!step) {
                    task.takeIf { it.remoteId == remoteId || (title != null && recent && it.title == title && it.dueDay == dueDay) }
                } else if (title != null && recent) {
                    stepItems(item)?.firstOrNull { it.str("displayName") == title }?.let { stepState(listId, task.remoteId, it) }
                } else null
            }
        }
        return match
    }

    // ── Writing ──

    override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = call {
        val parent = stepParent(task.parentRemoteId)
        when {
            link == null && parent != null -> createStep(task.listId, parent, task)
            link == null -> createTask(task.listId, task)
            TodoFields.isStep(link.remoteId) -> updateStep(task, link)
            // A task the tablet made a subtask stays a task in To Do: turning it into a step would delete
            // its notes there (see the report of 25 Nov 2026)
            else -> updateTask(task, link)
        }
    }

    override suspend fun delete(listId: String, remoteId: String) = call { deleteCopy(listId, remoteId) }

    private suspend fun createTask(listId: String, task: OutgoingTask): RemoteTask {
        // H15: a create whose reply was lost: the copy it made, if it is there
        if (task.createRetry) unlinkedCopy(listId, task)?.let { return it }
        val made = onList(listId) { graph.call("POST", tasksPath(listId), newTask(task)) }
            ?: throw IllegalStateException("No answer to a new task")
        return remoteTaskOf(listId, made)
    }

    private suspend fun unlinkedCopy(listId: String, task: OutgoingTask): RemoteTask? = try {
        titled(listId, task.title)
            .firstOrNull { it.str("id") !in task.knownRemoteIds && dueOf(it) == task.dueDay }
            ?.let { remoteTaskOf(listId, it) }
    } catch (e: GraphException.Http) {
        null
    }

    /** A new step of [taskId]; its task gone meanwhile, the send is tried again later (the pull brings the delete). */
    private suspend fun createStep(listId: String, taskId: String, task: OutgoingTask): RemoteTask {
        if (task.createRetry) unlinkedStep(listId, taskId, task)?.let { return it }
        val made = onList(listId) { graph.call("POST", stepsPath(listId, taskId), newStep(task.title, task.completed)) }
            ?: throw IllegalStateException("No answer to a new step")
        return stepTaskOf(listId, taskId, made)
    }

    private suspend fun unlinkedStep(listId: String, taskId: String, task: OutgoingTask): RemoteTask? = try {
        readSteps(listId, taskId)
            .firstOrNull { step ->
                step.str("displayName") == task.title &&
                    step.str("id")?.let { TodoFields.stepRemoteId(taskId, it) } !in task.knownRemoteIds
            }
            ?.let { stepTaskOf(listId, taskId, it) }
    } catch (e: GraphException.Http) {
        null
    }

    private suspend fun updateTask(task: OutgoingTask, link: TaskSyncLink): RemoteTask {
        val listId = link.listId
        val path = taskPath(listId, link.remoteId)
        val before = onTask(listId) { get(path) }
        if (before.str(MODIFIED) != link.remoteVersion) throw TaskSyncServiceException.RemoteChanged(taskState(listId, before))

        val patch = changesOf(task, link)
        if (patch.isEmpty()) return remoteTaskOf(listId, before)
        val after = onTask(listId) { graph.call("PATCH", path, patch) } ?: onTask(listId) { get(path) }
        // Ticked, a repeating task keeps its id for the next one and To Do makes a done copy: the
        // tablet's ticked task is that copy from now on (owner decision 82); the next one comes in
        if (task.completed && patch.containsKey("status") && before.obj("recurrence") != null) {
            doneCopy(listId, before, after)?.let { return remoteTaskOf(listId, it) }
        }
        return remoteTaskOf(listId, after)
    }

    /** Only what changed on the tablet since the last sync (the copy is as it was then). */
    private fun changesOf(task: OutgoingTask, link: TaskSyncLink): JsonObject = buildJsonObject {
        if (task.title != link.syncedTitle) put("title", task.title)
        if (task.completed != link.syncedCompleted) put("status", statusOf(task.completed))
        val lastDue = if (link.remoteUndated) null else link.syncedDueDay
        val dueChanged = task.dueDay != lastDue
        if (dueChanged) put("dueDateTime", task.dueDay?.let(::dueFor) ?: JsonNull)
        val extras = task.extras ?: return@buildJsonObject
        // A link without priority recorded: both go (nothing to compare with)
        val unknown = link.syncedPriority == null
        if (unknown || TodoFields.keptPriority(extras.priority) != link.syncedPriority) {
            put("importance", TodoFields.importanceOf(extras.priority))
        }
        val minute = extras.reminderMinute?.takeIf { task.dueDay != null }
        if (unknown || minute != link.syncedReminder || (dueChanged && minute != null)) {
            val at = reminderFor(task.dueDay, minute)
            if (at != null) {
                put("isReminderOn", true)
                put("reminderDateTime", at)
            } else if (link.syncedReminder != null) {
                put("isReminderOn", false)  // the usual time: the one BooxDay sent goes
            }
        }
    }

    /**
     * The done copy To Do made when [before], a repeating task, was ticked: same title and repeat,
     * ticked, made at the tick or later. It may take a moment to be listed: a few tries.
     */
    private suspend fun doneCopy(listId: String, before: JsonObject, after: JsonObject): JsonObject? {
        val title = before.str("title") ?: return null
        val id = before.str("id")
        // Microsoft's own clock: the tick's time, a little earlier for safety
        val since = instantOf(after.str(MODIFIED))?.minusSeconds(DONE_COPY_MARGIN_S)
        repeat(DONE_COPY_TRIES) { attempt ->
            if (attempt > 0) pause(DONE_COPY_WAIT_MS * attempt)
            val found = titled(listId, title)
                .filter { copy ->
                    copy.str("id") != id && copy.str("status") == COMPLETED && TodoFields.sameSeries(copy, before) &&
                        (since == null || (instantOf(copy.str("createdDateTime")) ?: Instant.MIN) >= since)
                }
                .maxByOrNull { instantOf(it.str("createdDateTime")) ?: Instant.MIN }
            if (found != null) return found
        }
        return null  // the next pull's round has both and follows it (doneCopiesTakingOver)
    }

    private suspend fun updateStep(task: OutgoingTask, link: TaskSyncLink): RemoteTask {
        val listId = link.listId
        val taskId = TodoFields.taskOf(link.remoteId)
        val path = stepPath(listId, taskId, TodoFields.stepOf(link.remoteId))
        // The tablet's grouping changed: the step goes under the new parent, or becomes a task
        val wanted = if (task.parentRemoteId != link.syncedParentRemoteId) stepParent(task.parentRemoteId) else taskId
        val before = onTask(listId) { get(path) }
        if (TodoFields.stepVersion(before) != link.remoteVersion) {
            stepState(listId, taskId, before)?.let { throw TaskSyncServiceException.RemoteChanged(it) }
        }
        if (wanted != taskId) return regroupStep(listId, link.remoteId, task, wanted)

        val patch = buildJsonObject {
            if (task.title != link.syncedTitle) put("displayName", task.title)
            if (task.completed != link.syncedCompleted) put("isChecked", task.completed)
        }
        if (patch.isEmpty()) return stepTaskOf(listId, taskId, before)
        val after = onTask(listId) { graph.call("PATCH", path, patch) } ?: onTask(listId) { get(path) }
        return stepTaskOf(listId, taskId, after)
    }

    /** A step to another task ([parent]) or to a task of its own (null): made there first, then deleted here. */
    private suspend fun regroupStep(listId: String, remoteId: String, task: OutgoingTask, parent: String?): RemoteTask {
        val fresh = task.copy(createRetry = false)
        val made = if (parent == null) createTask(listId, fresh.copy(parentRemoteId = null)) else createStep(listId, parent, fresh)
        replaced(listId, remoteId, made.remoteId)
        return made
    }

    /** Deletes [oldId] now that [newId] holds it; if that fails, [newId] goes again so nothing is doubled. */
    private suspend fun replaced(listId: String, oldId: String, newId: String) {
        try {
            deleteCopy(listId, oldId)
        } catch (e: Exception) {
            try {
                deleteCopy(listId, newId)
            } catch (ignored: Exception) {
                // The next pull brings the second copy in as a new task
            }
            throw e
        }
    }

    /** One already gone counts as deleted. */
    private suspend fun deleteCopy(listId: String, remoteId: String) {
        val path = if (TodoFields.isStep(remoteId)) stepPath(listId, TodoFields.taskOf(remoteId), TodoFields.stepOf(remoteId))
        else taskPath(listId, remoteId)
        try {
            graph.call("DELETE", path)
        } catch (e: GraphException.Http) {
            if (!isGone(e)) throw e
        }
    }

    /**
     * To another list (owner decision 83): a copy there with the words, notes, dates, reminder, star,
     * categories, links and steps, then the old one deleted. Repeating tasks and tasks with attachments
     * are refused. The steps get new ids ([takenAlong]).
     */
    override suspend fun moveToList(fromListId: String, remoteId: String, toListId: String, parentRemoteId: String?): RemoteTaskState = call {
        if (TodoFields.isStep(remoteId)) return@call moveStep(fromListId, remoteId, toListId, stepParent(parentRemoteId))
        val before = onTask(fromListId) { get("${taskPath(fromListId, remoteId)}?\$expand=checklistItems") }
        if (before.bool("hasAttachments") == true || before.obj("recurrence") != null) throw TaskSyncServiceException.Refused()
        val steps = stepItems(before) ?: readSteps(fromListId, remoteId)
        val links = (before["linkedResources"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: get("${taskPath(fromListId, remoteId)}/linkedResources").items()
        val made = onList(toListId) { graph.call("POST", tasksPath(toListId), copyOf(before, links)) }
            ?: throw IllegalStateException("No answer to a moved task")
        val newId = made.str("id") ?: throw IllegalStateException("A moved task without an id")
        val newSteps = HashMap<String, String>()
        try {
            for (step in steps) {
                val oldStep = step.str("id") ?: continue
                val copy = graph.call("POST", stepsPath(toListId, newId), newStep(step.str("displayName").orEmpty(), step.bool("isChecked") == true))
                    ?: throw IllegalStateException("No answer to a moved step")
                newSteps[TodoFields.stepRemoteId(remoteId, oldStep)] = TodoFields.stepRemoteId(newId, copy.str("id")!!)
            }
        } catch (e: Exception) {
            try {
                deleteCopy(toListId, newId)
            } catch (ignored: Exception) {
            }
            throw e
        }
        replaced(fromListId, remoteId, newId)
        movedSteps.putAll(newSteps)
        taskState(toListId, onTask(toListId) { get(taskPath(toListId, newId)) })
    }

    /** One step to another list, under [parent] there or as a task of its own. */
    private suspend fun moveStep(fromListId: String, remoteId: String, toListId: String, parent: String?): RemoteTaskState {
        val step = onTask(fromListId) { get(stepPath(fromListId, TodoFields.taskOf(remoteId), TodoFields.stepOf(remoteId))) }
        val words = step.str("displayName").orEmpty()
        val ticked = step.bool("isChecked") == true
        val moved = if (parent != null) {
            val made = onList(toListId) { graph.call("POST", stepsPath(toListId, parent), newStep(words, ticked)) }
                ?: throw IllegalStateException("No answer to a moved step")
            stepState(toListId, parent, made) ?: throw IllegalStateException("A moved step without an id")
        } else {
            val made = onList(toListId) {
                graph.call("POST", tasksPath(toListId), buildJsonObject { put("title", words); put("status", statusOf(ticked)) })
            } ?: throw IllegalStateException("No answer to a moved step")
            taskState(toListId, made)
        }
        try {
            deleteCopy(fromListId, remoteId)
        } catch (e: Exception) {
            try {
                deleteCopy(toListId, moved.remoteId)
            } catch (ignored: Exception) {
            }
            throw e
        }
        return moved
    }

    // ── Graph's shapes ──

    private fun newTask(task: OutgoingTask): JsonObject = buildJsonObject {
        put("title", task.title)
        put("status", statusOf(task.completed))
        put("importance", TodoFields.importanceOf(task.extras?.priority.orEmpty()))
        task.dueDay?.let { put("dueDateTime", dueFor(it)) }
        reminderFor(task.dueDay, task.extras?.reminderMinute)?.let {
            put("isReminderOn", true)
            put("reminderDateTime", it)
        }
    }

    private fun newStep(words: String, ticked: Boolean) = buildJsonObject {
        put("displayName", words)
        put("isChecked", ticked)
    }

    /** What a moved task takes to its new list. */
    private fun copyOf(task: JsonObject, links: List<JsonObject>): JsonObject = buildJsonObject {
        for (key in COPIED) task[key]?.takeUnless { it is JsonNull }?.let { put(key, it) }
        if (task.str("status") == COMPLETED) task["completedDateTime"]?.takeUnless { it is JsonNull }?.let { put("completedDateTime", it) }
        if (links.isNotEmpty()) {
            put("linkedResources", JsonArray(links.map { link -> JsonObject(link.filterKeys { it in LINK_FIELDS }) }))
        }
    }

    private fun dueFor(day: String): JsonObject = TodoFields.dueFor(LocalDate.parse(day))

    /** The reminder minute on [day] in the tablet's zone, as its moment in UTC. */
    private fun reminderFor(day: String?, minute: Int?): JsonObject? {
        if (day == null || minute == null) return null
        return TodoFields.utc(LocalDate.parse(day).atTime(minute / 60, minute % 60).atZone(zone()))
    }

    private fun dueOf(task: JsonObject): String? = TodoFields.dayOf(task.obj("dueDateTime"), zone())

    private fun taskState(listId: String, task: JsonObject): RemoteTaskState {
        val due = dueOf(task)
        val modified = task.str(MODIFIED)
        return RemoteTaskState(
            listId = listId,
            remoteId = task.str("id").orEmpty(),
            title = task.str("title").orEmpty(),
            completed = task.str("status") == COMPLETED,
            dueDay = due,
            parentRemoteId = null,
            deleted = false,
            version = modified,
            modifiedAt = instantOf(modified)?.toEpochMilli(),
            extras = TaskExtras(TodoFields.priorityOf(task.str("importance")), TodoFields.reminderMinuteOf(task, due, zone()))
        )
    }

    /** A step as the engine sees it: a subtask of [taskId] with only words and a tick. */
    private fun stepState(listId: String, taskId: String, step: JsonObject): RemoteTaskState? {
        val id = step.str("id") ?: return null
        val ticked = step.bool("isChecked") == true
        return RemoteTaskState(
            listId = listId,
            remoteId = TodoFields.stepRemoteId(taskId, id),
            title = step.str("displayName").orEmpty(),
            completed = ticked,
            dueDay = null,
            parentRemoteId = taskId,
            deleted = false,
            version = TodoFields.stepVersion(step),
            modifiedAt = if (ticked) instantOf(step.str("checkedDateTime"))?.toEpochMilli() else null,
            carriesDue = false
        )
    }

    private fun goneState(listId: String, remoteId: String) = RemoteTaskState(
        listId = listId, remoteId = remoteId, title = "", completed = false, dueDay = null, parentRemoteId = null,
        deleted = true, version = null, carriesDue = !TodoFields.isStep(remoteId)
    )

    private fun remoteTaskOf(listId: String, task: JsonObject) =
        RemoteTask(listId, task.str("id") ?: throw IllegalStateException("A task without an id"), task.str(MODIFIED), task.str("title"))

    private fun stepTaskOf(listId: String, taskId: String, step: JsonObject) = RemoteTask(
        listId,
        TodoFields.stepRemoteId(taskId, step.str("id") ?: throw IllegalStateException("A step without an id")),
        TodoFields.stepVersion(step),
        step.str("displayName")
    )

    private fun remoteListOf(list: JsonObject) = RemoteList(list.str("id")!!, list.str("displayName").orEmpty())

    /** A parent To Do can hold steps under: a task, not a step (a step can't have steps). */
    private fun stepParent(remoteId: String?): String? = remoteId?.takeUnless(TodoFields::isStep)

    /** Steps listed with their task ($expand), or null when they weren't. */
    private fun stepItems(task: JsonObject): List<JsonObject>? = (task["checklistItems"] as? JsonArray)?.mapNotNull { it as? JsonObject }

    private suspend fun readSteps(listId: String, taskId: String): List<JsonObject> {
        val steps = mutableListOf<JsonObject>()
        pages(stepsPath(listId, taskId)) { steps += it.items() }
        return steps
    }

    /** The list's tasks with [title]: by Graph's filter, else read through (a filter it refuses). */
    private suspend fun titled(listId: String, title: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        try {
            pages("${tasksPath(listId)}?\$filter=${query("title eq '${title.replace("'", "''")}'")}") { found += it.items() }
        } catch (e: GraphException.Http) {
            if (e.status != 400) throw e
            found.clear()
            pages("${tasksPath(listId)}?\$top=$PAGE_SIZE") { page -> found += page.items().filter { it.str("title") == title } }
        }
        return found.filter { it.str("title") == title }
    }

    // ── Graph's answers ──

    private suspend fun get(path: String): JsonObject = graph.call("GET", local(path)) ?: JsonObject(emptyMap())

    /** Every page from [first], following nextLink. */
    private suspend fun pages(first: String, each: suspend (JsonObject) -> Unit) {
        var next: String? = first
        while (next != null) {
            val page = get(next)
            each(page)
            next = page.str(NEXT_LINK)
        }
    }

    /** A 404/410 for the list itself. */
    private suspend fun <T> onList(listId: String, block: suspend () -> T): T = try {
        block()
    } catch (e: GraphException.Http) {
        if (isGone(e) && !listExists(listId)) throw TaskSyncServiceException.ListGone(listId, e)
        throw e
    }

    /** A 404/410 for one task or step: it is gone, or its whole list. */
    private suspend fun <T> onTask(listId: String, block: suspend () -> T): T = try {
        block()
    } catch (e: GraphException.Http) {
        if (!isGone(e)) throw e
        if (listExists(listId)) throw TaskSyncServiceException.NotFound(e)
        throw TaskSyncServiceException.ListGone(listId, e)
    }

    /** A copy, or null when it is gone (its list still there). */
    private suspend fun <T> orNull(listId: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: GraphException.Http) {
        if (!isGone(e)) throw e
        if (!listExists(listId)) throw TaskSyncServiceException.ListGone(listId, e)
        null
    }

    private suspend fun listExists(listId: String): Boolean = try {
        get(listPath(listId))
        true
    } catch (e: GraphException.Http) {
        if (!isGone(e)) throw e
        false
    }

    private fun isGone(e: GraphException.Http) = e.status == 404 || e.status == 410

    private fun isStale(e: GraphException.Http) = e.status == 410 || e.code?.lowercase() in STALE_CODES

    /** Graph's failures as the engine knows them. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: GraphException) {
        throw when (e) {
            is GraphException.SignInNeeded -> TaskSyncServiceException.SignInNeeded(e)
            is GraphException.Busy -> TaskSyncServiceException.Busy(e.retryAfterMs, e)
            is GraphException.Offline -> TaskSyncServiceException.Offline(e)
            // No To Do for this account (a work account without it, a mailbox not in Microsoft's cloud)
            is GraphException.Http ->
                if (e.status == 403 || e.code.equals(NO_MAILBOX, ignoreCase = true)) TaskSyncServiceException.NotAvailable(e) else e
        }
    }

    private fun listPath(listId: String) = "$LISTS/${segment(listId)}"
    private fun tasksPath(listId: String) = "${listPath(listId)}/tasks"
    private fun taskPath(listId: String, taskId: String) = "${tasksPath(listId)}/${segment(taskId)}"
    private fun stepsPath(listId: String, taskId: String) = "${taskPath(listId, taskId)}/checklistItems"
    private fun stepPath(listId: String, taskId: String, stepId: String) = "${stepsPath(listId, taskId)}/${segment(stepId)}"

    companion object {
        private const val LISTS = "/me/todo/lists"
        private const val DEFAULT_LIST = "defaultList"
        private const val FLAGGED_EMAILS = "flaggedEmails"
        private const val COMPLETED = "completed"
        private const val MODIFIED = "lastModifiedDateTime"
        private const val REMOVED = "@removed"
        private const val NEXT_LINK = "@odata.nextLink"
        private const val DELTA_LINK = "@odata.deltaLink"
        private const val NO_MAILBOX = "MailboxNotEnabledForRESTAPI"
        /** For the plain task list only: delta refuses any $top (400, measured 25 Nov 2026). */
        private const val PAGE_SIZE = 100

        /** Graph's answers for a delta cursor it no longer knows (410 Gone, or these codes). */
        private val STALE_CODES = setOf("syncstatenotfound", "resyncrequired", "syncstateinvalid", "errorsyncstateinvalid", "errorinvalidsyncstatedata")

        /** What a moved task keeps (owner decision 83). */
        private val COPIED = listOf("title", "body", "importance", "status", "isReminderOn", "reminderDateTime", "dueDateTime", "startDateTime", "categories")
        private val LINK_FIELDS = setOf("webUrl", "applicationName", "displayName", "externalId")

        /** Looking for a repeating task's done copy: tries, the wait before each next try, the clock margin. */
        private const val DONE_COPY_TRIES = 3
        private const val DONE_COPY_WAIT_MS = 1_000L
        private const val DONE_COPY_MARGIN_S = 60L

        /**
         * The To Do of the Outlook account [accountId] in Calendar Accounts, as [accountEmail] (task_sync_link's
         * accountEmail); [TaskSyncServiceException.SignInNeeded] when its Microsoft sign-in isn't known.
         */
        fun create(context: Context, accountId: String, accountEmail: String): MicrosoftTodoProvider {
            val app = context.applicationContext
            val msalId = OutlookCalendarProvider.msalAccountIdOf(app, accountId) ?: throw TaskSyncServiceException.SignInNeeded()
            val sync = AppDatabase.getDatabase(app).taskSyncDao()
            return MicrosoftTodoProvider(
                MicrosoftGraph.forAccount(OutlookAuthManager(app), msalId, MicrosoftScopes.TASKS),
                linksIn = { listId ->
                    withContext(Dispatchers.IO) { sync.getLinksInList(TaskSyncProvider.MICROSOFT_TODO, accountEmail, "", listId) }
                }
            )
        }

        private fun statusOf(completed: Boolean) = if (completed) COMPLETED else "notStarted"

        /** A path segment: Graph's ids may hold characters a path can't. */
        private fun segment(id: String) = URLEncoder.encode(id, "UTF-8").replace("+", "%20")

        private fun query(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        /** A link Graph handed out, as a path MicrosoftGraph puts on its own base (the same address in use). */
        private fun local(link: String) = if (link.startsWith(MicrosoftGraph.BASE_URL)) link.substring(MicrosoftGraph.BASE_URL.length) else link

        private fun instantOf(value: String?): Instant? = value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    }
}

/**
 * How a BooxDay task's fields look in To Do, in one place (owner decisions 79-81, Nov 2026).
 * Pinned by MicrosoftTodoProviderTest.
 */
internal object TodoFields {
    /** Between a step's task id and its own id in its remote id. */
    const val STEP = "/step/"

    fun stepRemoteId(taskId: String, stepId: String) = "$taskId$STEP$stepId"
    fun isStep(remoteId: String) = remoteId.contains(STEP)
    fun taskOf(remoteId: String) = remoteId.substringBefore(STEP)
    fun stepOf(remoteId: String) = remoteId.substringAfter(STEP)

    // ── Priority (owner decision 80): A is To Do's star; B and C stay on the tablet ──

    private const val STARRED = "high"
    private const val NOT_STARRED = "normal"

    /** A copy's priority: A when starred, else none. */
    fun priorityOf(importance: String?): String = if (importance == STARRED) "A" else ""

    /** What goes to To Do: the star for A; B, C and none go unstarred. */
    fun importanceOf(priority: String): String = if (priority == "A") STARRED else NOT_STARRED

    /** The part of a tablet priority To Do keeps: B and C read back as none, so they never change. */
    fun keptPriority(priority: String): String = priorityOf(importanceOf(priority))

    // ── Dates (measured 25 Nov 2026): always sent as UTC worked out on the tablet, since Microsoft's
    // time-zone rules and the tablet's can differ; Graph keeps and returns them in UTC ──

    private val WIRE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

    /** The moment a Graph dateTimeTimeZone stands for. */
    private fun momentOf(value: JsonObject?): ZonedDateTime? {
        val text = value?.str("dateTime") ?: return null
        val written = runCatching { LocalDateTime.parse(text.substring(0, minOf(19, text.length))) }.getOrNull() ?: return null
        return written.atZone(zoneOf(value.str("timeZone")))
    }

    /** A Graph dateTimeTimeZone as a time in [zone]. */
    fun localOf(value: JsonObject?, zone: ZoneId): LocalDateTime? = momentOf(value)?.withZoneSameInstant(zone)?.toLocalDateTime()

    /**
     * A due day, "yyyy-MM-dd": midnight UTC is a day as To Do keeps due days, taken as it is; any
     * other moment (another app, or an older send, wrote a local midnight) is the day in [zone].
     */
    fun dayOf(value: JsonObject?, zone: ZoneId): String? {
        val moment = momentOf(value) ?: return null
        val utc = moment.withZoneSameInstant(ZoneOffset.UTC)
        if (utc.toLocalTime() == LocalTime.MIDNIGHT) return utc.toLocalDate().toString()
        return moment.withZoneSameInstant(zone).toLocalDate().toString()
    }

    /** A due day as To Do keeps it: midnight UTC of the day, whatever the tablet's zone. */
    fun dueFor(day: LocalDate): JsonObject = utc(day.atStartOfDay(ZoneOffset.UTC))

    /** A moment as Graph gets it from BooxDay: always in UTC, never a zone's name. */
    fun utc(at: ZonedDateTime): JsonObject = buildJsonObject {
        put("dateTime", at.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().format(WIRE))
        put("timeZone", "UTC")
    }

    /**
     * The task's own reminder time (owner decision 81, like CalDAV's 69): a reminder on its due day,
     * as minutes after midnight in [zone]; any other reminder is none (the usual time).
     */
    fun reminderMinuteOf(task: JsonObject, dueDay: String?, zone: ZoneId): Int? {
        if (dueDay == null || task.bool("isReminderOn") != true) return null
        val at = localOf(task.obj("reminderDateTime"), zone) ?: return null
        return if (at.toLocalDate().toString() == dueDay) at.hour * 60 + at.minute else null
    }

    /** A zone Graph names in what it returns: "UTC" (always so far), else an IANA id or a Windows name. */
    fun zoneOf(name: String?): ZoneId {
        if (name.isNullOrBlank() || name.equals("UTC", ignoreCase = true)) return ZoneOffset.UTC
        runCatching { return ZoneId.of(name) }
        val iana = runCatching { android.icu.util.TimeZone.getIDForWindowsID(name, null) }.getOrNull()
        return iana?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    }

    /** A step has no version of its own: its words and tick stand in for one. */
    fun stepVersion(step: JsonObject): String {
        val text = step.str("displayName").orEmpty() + "\u0000" + (step.bool("isChecked") == true)
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return "step:" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Two copies of one repeating task: same title, same repeat, same start (owner decision 82). */
    fun sameSeries(a: JsonObject, b: JsonObject): Boolean {
        val ra = a.obj("recurrence") ?: return false
        val rb = b.obj("recurrence") ?: return false
        return a.str("title") == b.str("title") && ra["pattern"] == rb["pattern"] &&
            ra.obj("range")?.str("startDate") == rb.obj("range")?.str("startDate")
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.items(): List<JsonObject> = (this["value"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
