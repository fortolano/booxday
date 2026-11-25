package com.boox.day.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.data.getDbDir
import com.boox.day.notifications.EventReminderChain.Reminder
import com.boox.day.ui.MainScreenRequest
import com.boox.day.ui.MainScreenRequests
import com.boox.day.ui.TaskFocusRequests
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Event reminders on BOOX (Nov 2026): all of them share one queued job. A job per event filled
 * WorkManager's queue (a month of a busy calendar is hundreds), and nothing new ran: the sleep
 * screen was not redrawn after its settings changed (Note Air 6C, 18 Nov 2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EventReminderChainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // As on a BOOX tablet, where reminders can't use alarms
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("force_workmanager_notifications", true).commit()
        EventReminderChain.stop(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun `a month of reminders is one queued job`() {
        val scheduler = EventNotificationScheduler(context)
        val start = System.currentTimeMillis() + HOUR
        repeat(300) { i -> scheduler.scheduleCalendarEventAlarm(event("e$i", "Event $i", start + i * HOUR), 15) }

        assertEquals(1, waitingJobs().size)
    }

    @Test
    fun `a reminder is due from its time until five minutes into the event, once`() {
        val standup = Reminder("s", "Standup", "2027-01-15", startMs = now + 10 * MINUTE)  // reminder was 5 minutes ago
        val lunch = Reminder("l", "Lunch", "2027-01-15", startMs = now + 3 * HOUR)
        val missed = Reminder("m", "Missed", "2027-01-15", startMs = now - 6 * MINUTE)

        val due = EventReminderChain.due(listOf(standup, lunch, missed), minutesBefore = 15, handledUntil = now - HOUR, now = now)
        assertEquals(listOf(standup), due)

        val again = EventReminderChain.due(listOf(standup, lunch, missed), minutesBefore = 15, handledUntil = now, now = now + MINUTE)
        assertEquals("already shown", emptyList<Reminder>(), again)
    }

    @Test
    fun `the next wake is the earliest reminder still to come`() {
        val past = Reminder("p", "Past", "2027-01-15", startMs = now + 10 * MINUTE)
        val lunch = Reminder("l", "Lunch", "2027-01-15", startMs = now + 3 * HOUR)
        val dinner = Reminder("d", "Dinner", "2027-01-15", startMs = now + 8 * HOUR)

        assertEquals(lunch.startMs - 15 * MINUTE, EventReminderChain.nextWake(listOf(dinner, past, lunch), minutesBefore = 15, now = now))
        assertNull(EventReminderChain.nextWake(listOf(past), minutesBefore = 15, now = now))
    }

    @Test
    fun `a later reminder leaves the queued job, an earlier one moves it`() {
        EventReminderChain.wakeBy(context, now + 2 * HOUR, now)
        EventReminderChain.wakeBy(context, now + 5 * HOUR, now)
        assertEquals(now + 2 * HOUR, EventReminderChain.wakeAt(context))

        EventReminderChain.wakeBy(context, now + HOUR, now)
        assertEquals(now + HOUR, EventReminderChain.wakeAt(context))
        assertEquals(1, waitingJobs().size)
    }

    @Test
    fun `a run shows what is due and queues the next reminder`(): Unit = runBlocking(Dispatchers.IO) {
        val realNow = System.currentTimeMillis()
        KvProxy(context).setKv(
            APP_SETTINGS_KEY,
            AppSettings(version = 1).copy(eventNotificationsEnabled = true, defaultEventReminderMinutes = 15),
            AppSettings.serializer()
        )
        val db = AppDatabase.getDatabase(context)
        db.calendarEventDao().upsertAll(listOf(
            event("standup", "Standup", realNow + 10 * MINUTE),  // reminder was 5 minutes ago
            event("review", "Review", realNow + 26 * HOUR),
        ))
        // The chain was started 20 minutes ago, before the Standup reminder
        EventReminderChain.wakeBy(context, realNow - 5 * MINUTE, realNow - 20 * MINUTE)

        EventReminderChain.run(context, realNow)

        assertEquals(listOf("Standup"), shownTitles())
        assertEquals(realNow + 26 * HOUR - 15 * MINUTE, EventReminderChain.wakeAt(context))
        assertEquals(1, waitingJobs().size)

        EventReminderChain.run(context, realNow + MINUTE)
        assertEquals("not shown twice", listOf("Standup"), shownTitles())
    }

    /**
     * The app's start plans a month of reminders while the chain's first run is due or in progress.
     * Each used to replace that run, and the job woke at whatever was asked last (Note Mini C,
     * 19 Nov 2026: 8 Dec instead of the next morning).
     */
    @Test
    fun `reminders asked for while a run is due do not push the wake later`(): Unit = runBlocking(Dispatchers.IO) {
        val realNow = System.currentTimeMillis()
        KvProxy(context).setKv(
            APP_SETTINGS_KEY,
            AppSettings(version = 1).copy(eventNotificationsEnabled = true, defaultEventReminderMinutes = 15),
            AppSettings.serializer()
        )
        AppDatabase.getDatabase(context).calendarEventDao().upsertAll(listOf(event("review", "Review", realNow + 26 * HOUR)))
        // A run is due (written straight to the chain's file, so no job starts by itself here)
        context.getSharedPreferences("event_reminder_chain", Context.MODE_PRIVATE).edit()
            .putLong("wake_at", realNow - MINUTE).putLong("handled_until", realNow - MINUTE).commit()

        EventReminderChain.wakeBy(context, realNow + 400 * HOUR, realNow)  // a late event, asked for first
        EventReminderChain.wakeBy(context, realNow + 3 * HOUR, realNow)  // an event not saved yet
        assertEquals("the due run stays", realNow - MINUTE, EventReminderChain.wakeAt(context))

        EventReminderChain.run(context, realNow)

        assertEquals(realNow + 3 * HOUR, EventReminderChain.wakeAt(context))
        assertEquals(1, waitingJobs().size)
    }

    @Test
    fun `jobs queued one per event by older versions are cancelled`() {
        val workManager = WorkManager.getInstance(context)
        repeat(3) {
            workManager.enqueue(
                OneTimeWorkRequestBuilder<EventNotificationWorker>().setInitialDelay(5, TimeUnit.DAYS).build()
            ).result.get()
        }
        EventReminderChain.wakeBy(context, now + HOUR, now)

        EventReminderChain.retireOldJobs(context)

        assertEquals("only the shared job stays", 1, waitingJobs().size)
    }

    @Test
    fun `hybrid event with both local and synced rows fires only one reminder`(): Unit = runBlocking(Dispatchers.IO) {
        val realNow = System.currentTimeMillis()
        KvProxy(context).setKv(
            APP_SETTINGS_KEY,
            AppSettings(version = 1).copy(eventNotificationsEnabled = true, defaultEventReminderMinutes = 15),
            AppSettings.serializer()
        )
        val db = AppDatabase.getDatabase(context)
        val startMs = realNow + 10 * MINUTE
        db.calendarEventDao().upsertAll(listOf(event("g-standup", "Standup", startMs)))
        db.localEventDao().insert(
            LocalEvent(
                id = "local-standup",
                dayId = "2027-01-15",
                title = "Standup",
                startTime = startMs,
                endTime = startMs + HOUR,
                googleEventId = "g-standup"
            )
        )
        EventReminderChain.wakeBy(context, realNow - 5 * MINUTE, realNow - 20 * MINUTE)

        EventReminderChain.run(context, realNow)

        assertEquals("hybrid event must notify once", listOf("Standup"), shownTitles())
    }

    @Test
    fun `settings read error throws so worker retries without ending chain`(): Unit = runBlocking(Dispatchers.IO) {
        val realNow = System.currentTimeMillis()
        val originalWake = realNow + 2 * HOUR
        EventReminderChain.wakeBy(context, originalWake, realNow)

        var threw = false
        try {
            EventReminderChain.run(context, realNow, loadSettings = { throw IllegalStateException("db busy") })
        } catch (_: IllegalStateException) {
            threw = true
        }

        assertTrue("read failure must propagate to EventReminderWorker for retry", threw)
        assertEquals("wake_at must remain set when settings read fails", originalWake, EventReminderChain.wakeAt(context))
    }

    @Test
    fun `DueDateAlarmReceiver skips notification when task reminders are off`(): Unit = runBlocking(Dispatchers.IO) {
        KvProxy(context).setKv(
            APP_SETTINGS_KEY,
            AppSettings(version = 1).copy(taskNotificationsEnabled = false),
            AppSettings.serializer()
        )
        val db = AppDatabase.getDatabase(context)
        db.pageDao().create(Page(id = "page-1"))
        val task = Task(
            id = "task-disabled-1",
            dayDate = "2027-01-15",
            pageId = "page-1",
            taskPageIndex = 0,
            rowIndex = 0,
            typedText = "Pay bill",
            dueDay = "2027-01-15"
        )
        db.taskDao().insert(task)

        DueDateAlarmReceiver().validateAndShowNotification(context, task.id)

        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertTrue("disabled task notifications must stay silent", shown.isEmpty())
    }

    @Test
    fun `NotificationNavigation routes event taps to ShowDay and task taps to TaskFocusRequests`(): Unit = runBlocking {
        TaskFocusRequests.consume()

        val eventIntent = Intent().putExtra(NotificationHelper.EXTRA_NAVIGATE_TO_DATE, "2027-01-15")
        assertTrue(NotificationNavigation.handleIntent(eventIntent))
        assertEquals(MainScreenRequest.ShowDay("2027-01-15"), MainScreenRequests.next())
        assertNull("event tap should not trigger task focus", TaskFocusRequests.pending.value)

        val taskIntent = Intent()
            .putExtra(NotificationHelper.EXTRA_NAVIGATE_TO_DATE, "2027-01-16")
            .putExtra(NotificationHelper.EXTRA_OPEN_TAB, NotificationHelper.TAB_TASKS)
            .putExtra(NotificationHelper.EXTRA_TASK_ID, "t-42")
        assertTrue(NotificationNavigation.handleIntent(taskIntent))
        val pendingTask = TaskFocusRequests.pending.value
        assertNotNull(pendingTask)
        assertEquals("t-42", pendingTask?.taskId)
        assertEquals("2027-01-16", pendingTask?.dayDate)
        TaskFocusRequests.consume()

        val malformedIntent = Intent().putExtra(NotificationHelper.EXTRA_NAVIGATE_TO_DATE, "not-a-date")
        assertFalse(NotificationNavigation.handleIntent(malformedIntent))
    }

    @Test
    fun `NotificationRescheduleWorker skips daily task scan when task reminders are off`(): Unit = runBlocking(Dispatchers.IO) {
        val realNow = System.currentTimeMillis()
        KvProxy(context).setKv(
            APP_SETTINGS_KEY,
            AppSettings(version = 1).copy(taskNotificationsEnabled = false, eventNotificationsEnabled = true),
            AppSettings.serializer()
        )
        AppDatabase.getDatabase(context).calendarEventDao().upsertAll(
            listOf(event("future-1", "Review", realNow + 3 * HOUR))
        )

        val worker = androidx.work.testing.TestListenableWorkerBuilder<NotificationRescheduleWorker>(context).build()
        worker.reschedule(context, NotificationRescheduleWorker.REASON_BOOT)

        val dailyScan = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(TaskNotificationScheduler.WORK_NAME_DAILY_SCAN).get()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        assertTrue("daily task scan must not be enqueued when task notifications are off", dailyScan.isEmpty())
        assertNotNull("event reminder chain should still be scheduled", EventReminderChain.wakeAt(context))
    }

    private fun waitingJobs(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfos(WorkQuery.fromStates(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)).get()

    private fun shownTitles(): List<String> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
            .map { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() + "|" + it.extras.getCharSequence(android.app.Notification.EXTRA_TEXT) }
            .map { text -> listOf("Standup", "Review").firstOrNull { it in text } ?: text }

    private fun event(id: String, title: String, startMs: Long) = CalendarEvent(
        id = id, calendarId = "cal", calendarName = "Home", title = title,
        dayId = "2027-01-15", endDayId = "2027-01-15", startTime = startMs, endTime = startMs + HOUR
    )

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
