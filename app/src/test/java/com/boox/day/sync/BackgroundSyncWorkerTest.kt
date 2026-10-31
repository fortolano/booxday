package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Background sync on "Sync frequency" (Oct 2026): scheduled only with auto-sync on and an account
 * connected, its interval follows the setting, and a run in Private Mode syncs nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackgroundSyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var settingsWereLoaded = false
    private lateinit var settingsBefore: AppSettings

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        settingsWereLoaded = GlobalAppSettings.loaded
        settingsBefore = GlobalAppSettings.current
    }

    @After
    fun tearDown() {
        GlobalAppSettings.update(settingsBefore)
        GlobalAppSettings.loaded = settingsWereLoaded
    }

    @Test
    fun `the interval follows the settings`() {
        assertNull("auto-sync off", BackgroundSyncWorker.intervalHours(false, true, GoogleAuthManager.FREQUENCY_4_HOURS))
        assertNull("no account connected", BackgroundSyncWorker.intervalHours(true, false, GoogleAuthManager.FREQUENCY_4_HOURS))
        assertEquals(4L, BackgroundSyncWorker.intervalHours(true, true, GoogleAuthManager.FREQUENCY_4_HOURS))
        assertEquals(8L, BackgroundSyncWorker.intervalHours(true, true, GoogleAuthManager.FREQUENCY_8_HOURS))
        assertEquals(24L, BackgroundSyncWorker.intervalHours(true, true, GoogleAuthManager.FREQUENCY_DAILY))
        assertEquals("unknown value: the 4 h default", 4L, BackgroundSyncWorker.intervalHours(true, true, "Hourly-ish"))
    }

    @Test
    fun `scheduling follows a changed frequency and stops when turned off`() {
        BackgroundSyncWorker.applySchedule(context, 8L)
        val scheduled = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
        assertEquals(TimeUnit.HOURS.toMillis(8), scheduled.periodicityInfo?.repeatIntervalMillis)

        BackgroundSyncWorker.applySchedule(context, 24L)
        val updated = work().single()
        assertEquals("the same work, updated in place", scheduled.id, updated.id)
        assertEquals(TimeUnit.HOURS.toMillis(24), updated.periodicityInfo?.repeatIntervalMillis)

        BackgroundSyncWorker.applySchedule(context, null)
        assertEquals(WorkInfo.State.CANCELLED, work().single().state)
    }

    @Test
    fun `app start keeps the schedule that is there`() {
        BackgroundSyncWorker.applySchedule(context, 8L)
        val first = work().single()

        BackgroundSyncWorker.applySchedule(context, 8L, replace = false)  // the next app start
        BackgroundSyncWorker.applySchedule(context, 24L, replace = false)  // an account added

        val kept = work().single()
        assertEquals(first.id, kept.id)
        assertEquals("only a settings change takes a new interval", TimeUnit.HOURS.toMillis(8), kept.periodicityInfo?.repeatIntervalMillis)
        assertEquals(WorkInfo.State.ENQUEUED, kept.state)
    }

    @Test
    fun `a run in Private Mode syncs nothing`() = runBlocking {
        GlobalAppSettings.update(AppSettings(version = 1, syncMode = AppSettings.SyncMode.STANDALONE))
        GlobalAppSettings.loaded = true

        val result = TestListenableWorkerBuilder<BackgroundSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private fun work(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackgroundSyncWorker.WORK_NAME).get()
}
