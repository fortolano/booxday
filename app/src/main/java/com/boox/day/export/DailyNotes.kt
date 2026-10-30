package com.boox.day.export

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.boox.day.data.datastore.AppSettings
import com.boox.day.ocr.IndexingStatus
import com.boox.day.sleepscreen.SleepAgenda
import com.boox.day.today.DayDataWatcher
import com.boox.day.today.DayDigestLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Markdown daily notes (Oct 2026): [writeNote] writes one day's note ([DailyNoteMarkdown] into the
 * folder of [DailyNoteWriter]). With [AppSettings.dailyNotesEnabled] on, today's note follows the
 * day's changes a minute after they settle (new memo text included), and yesterday's note is
 * written once more just after midnight, so it ends with the whole day.
 */
object DailyNotes {
    private const val TAG = "DailyNotes"
    private const val WORK_TODAY = "daily_note_today"
    private const val WORK_NIGHTLY = "daily_note_nightly"

    private val watcher = DayDataWatcher(quietMs = 60_000L, inkQuietMs = 60_000L) {
        appContext?.let { requestWrite(it, LocalDate.now()) }
    }
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null

    fun isEnabled(context: Context): Boolean = SleepAgenda.settings(context).dailyNotesEnabled

    /** BooxDayApp.onCreate. */
    fun onAppStart(context: Context, appScope: CoroutineScope) {
        val app = context.applicationContext
        appContext = app
        scope = appScope
        appScope.launch(Dispatchers.IO) {
            if (isEnabled(app)) withContext(Dispatchers.Main) { startFollowing(app) }
        }
    }

    /** Preferences switch. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        if (enabled) {
            startFollowing(app)
            requestWrite(app, LocalDate.now(), force = true)
        } else {
            watcher.stop()
            WorkManager.getInstance(app).cancelUniqueWork(WORK_TODAY)
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NIGHTLY)
        }
    }

    /** Writes the note of [date] now. Blocking work on IO; returns the file name, or throws. */
    suspend fun writeNote(context: Context, date: LocalDate): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val digest = DayDigestLoader(app).load(date, withMemoText = true, withFullTaskText = true)
        val use24Hour = SleepAgenda.settings(app).timeFormat == AppSettings.TimeFormat.TwentyFourHour
        val name = DailyNoteMarkdown.fileName(digest)
        val block = DailyNoteMarkdown.block(
            digest,
            use24Hour,
            memoWaitReason = IndexingStatus.waitingFor.value?.shortText
        )
        try {
            DailyNoteWriter.write(app, name) { existing ->
                DailyNoteMarkdown.merge(existing, block, DailyNoteMarkdown.frontMatter(digest))
            }
        } catch (e: Exception) {
            lastWrite.failed(app, if (e is SecurityException) "the folder can't be written to" else "the note couldn't be saved")
            throw e
        }
        lastWrite.succeeded(app)
        Log.i(TAG, "Wrote $name")
        name
    }

    /** When a note was last written, or why the last try failed (the Preferences card shows it). */
    val lastWrite = com.boox.day.today.LastWrite("daily_notes")

    private fun requestWrite(context: Context, date: LocalDate, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DailyNoteWorker>()
            .setInputData(workDataOf(DailyNoteWorker.KEY_DATE to date.toString(), DailyNoteWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TODAY, ExistingWorkPolicy.REPLACE, request)
    }

    private fun startFollowing(context: Context) {
        scope?.let { watcher.start(context, it) }
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 5))
        val nightly = PeriodicWorkRequestBuilder<DailyNoteWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DailyNoteWorker.KEY_YESTERDAY to true))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NIGHTLY, ExistingPeriodicWorkPolicy.UPDATE, nightly)
    }
}

/** Writes one day's note: the date given, or yesterday for the nightly job. */
class DailyNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Forced = just turned on (the saved settings may not say so yet)
        if (!inputData.getBoolean(KEY_FORCE, false) && !DailyNotes.isEnabled(applicationContext)) return Result.success()
        val date = if (inputData.getBoolean(KEY_YESTERDAY, false)) LocalDate.now().minusDays(1)
        else inputData.getString(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        return try {
            DailyNotes.writeNote(applicationContext, date)
            Result.success()
        } catch (e: Exception) {
            Log.w("DailyNoteWorker", "Note for $date not written: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_YESTERDAY = "yesterday"
        const val KEY_FORCE = "force"
    }
}
