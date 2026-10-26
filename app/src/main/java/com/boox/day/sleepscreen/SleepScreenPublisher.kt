package com.boox.day.sleepscreen

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.PageRepository
import com.boox.day.ocr.source.MemoBitmapSource
import com.boox.day.today.DayDigest
import com.boox.day.today.DayDigestLoader
import com.boox.day.data.datastore.AppSettings
import com.onyx.android.sdk.api.device.screensaver.ScreenResourceManager
import com.onyx.android.sdk.api.device.screensaver.ScreenUtils
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draws today's agenda and hands it to BOOX as the sleep screen (Oct 2026).
 *
 * BOOX takes the image through the Onyx SDK (ScreenResourceManager.setScreensaver): its system app
 * (com.onyx) copies the file and its sleep screen (OnyxDaydreamService) shows the copy. Tested on the
 * Go 10.3 (Android 15): full size, upright. The copy lands in Pictures/ under the same file name.
 *
 * BOOX ignores a hand-off whose file name it already has: with one fixed name the sleep screen kept
 * the first image of the day (Go 10.3, 24 Oct 2026). So every image gets a new name, and the older
 * files are deleted here and in Pictures/, keeping the one before in case BOOX has not switched yet.
 *
 * Nothing is drawn or handed over when the day looks the same as last time ([fingerprint]).
 */
object SleepScreenPublisher {
    private const val TAG = "SleepScreenPublisher"
    private const val PREFS = "sleep_agenda"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_DATE = "date"
    private const val KEY_FILE = "file"
    private const val NAME_PREFIX = "booxday-today"
    /** The fixed name builds before 24 Oct 2026 used. */
    private const val OLD_FIXED_NAME = "booxday-today.png"

    private val mutex = Mutex()

    /** The folder BooxDay writes the image to; BOOX reads it from there. */
    fun folder(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BooxDay")

    /** The day the image on the sleep screen shows, or null when none was handed over. */
    fun shownDate(context: Context): String? = prefs(context).getString(KEY_DATE, null)

    /** Draws the agenda for now and hands it over when it changed. [force]: hand over even if unchanged. */
    suspend fun publish(context: Context, force: Boolean = false): Boolean = mutex.withLock {
        val app = context.applicationContext
        try {
            val nowMs = System.currentTimeMillis()
            val today = LocalDate.now()
            val (width, height, ppi) = panel(app)
            val sizeClass = SleepSizeClass.of(minOf(width, height), ppi)
            val loader = DayDigestLoader(app)
            val digest = loader.load(today, nextDays = if (sizeClass == SleepSizeClass.LARGE) 3 else 0)
            val memo = if (sizeClass != SleepSizeClass.COMPACT) memoBitmap(app, loader, today) else null
            val fingerprint = fingerprint(digest, nowMs, sizeClass, memo)
            if (!force && fingerprint == prefs(app).getString(KEY_FINGERPRINT, null)) {
                memo?.recycle()
                return@withLock true
            }

            val bitmap = SleepAgendaRenderer(SleepAgendaStyle.forClass(sizeClass, ppi)).render(
                SleepAgendaRenderer.Input(
                    digest = digest, widthPx = width, heightPx = height, nowMs = nowMs,
                    use24Hour = settings(app).timeFormat == AppSettings.TimeFormat.TwentyFourHour,
                    memo = memo,
                    firstDayOfWeek = settings(app).firstDayOfWeek.dayOfWeek,
                )
            )
            memo?.recycle()
            val previous = prefs(app).getString(KEY_FILE, null) ?: OLD_FIXED_NAME
            val file = write(bitmap, "$NAME_PREFIX-$nowMs.png")
            bitmap.recycle()

            val accepted = ScreenResourceManager.setScreensaver(app, file.absolutePath, false)
            Log.i(TAG, "Sleep screen ${if (accepted) "set" else "refused"}: $today, ${sizeClass.name.lowercase()} ${width}x$height, ${file.name}")
            if (accepted) {
                prefs(app).edit()
                    .putString(KEY_FINGERPRINT, fingerprint).putString(KEY_DATE, today.toString()).putString(KEY_FILE, file.name)
                    .apply()
                deleteImages(keep = setOf(file.name, previous))
            } else {
                file.delete()
            }
            accepted
        } catch (t: Throwable) {
            Log.e(TAG, "Sleep screen not updated", t)
            false
        }
    }

    /** The image as it would look now, for the Settings preview. Null when it can't be drawn. */
    suspend fun preview(context: Context): Bitmap? = try {
        val app = context.applicationContext
        val (width, height, ppi) = panel(app)
        val sizeClass = SleepSizeClass.of(minOf(width, height), ppi)
        val loader = DayDigestLoader(app)
        val today = LocalDate.now()
        val digest = loader.load(today, nextDays = if (sizeClass == SleepSizeClass.LARGE) 3 else 0)
        val memo = if (sizeClass != SleepSizeClass.COMPACT) memoBitmap(app, loader, today) else null
        SleepAgendaRenderer(SleepAgendaStyle.forClass(sizeClass, ppi)).render(
            SleepAgendaRenderer.Input(
                digest = digest, widthPx = width, heightPx = height, nowMs = System.currentTimeMillis(),
                use24Hour = settings(app).timeFormat == AppSettings.TimeFormat.TwentyFourHour, memo = memo,
                firstDayOfWeek = settings(app).firstDayOfWeek.dayOfWeek,
            )
        ).also { memo?.recycle() }
    } catch (t: Throwable) {
        Log.e(TAG, "Preview not drawn", t)
        null
    }

    /**
     * The setting was turned off: the image files go, BooxDay's and BOOX's copy in Pictures/,
     * so the sleep screen no longer shows a day that is over.
     */
    fun withdraw(context: Context) {
        prefs(context).edit().clear().apply()
        deleteImages(keep = emptySet())
    }

    /** Deletes BooxDay's sleep images, in its folder and BOOX's copies in Pictures/, except [keep]. */
    private fun deleteImages(keep: Set<String>) {
        listOfNotNull(folder(), folder().parentFile).forEach { dir ->
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.startsWith(NAME_PREFIX) && f.name.endsWith(".png") && f.name !in keep }
                    ?.forEach { if (!it.delete()) Log.w(TAG, "Could not delete ${it.absolutePath}") }
            }.onFailure { Log.w(TAG, "Old sleep images not cleaned up in $dir: ${it.message}") }
        }
    }

    /** The panel's portrait size in pixels and its pixels per inch. */
    private fun panel(context: Context): Triple<Int, Int, Int> {
        val size = runCatching { ScreenUtils.getScreenSize(context) }.getOrNull()
            ?: Resources.getSystem().displayMetrics.let { intArrayOf(it.widthPixels, it.heightPixels) }
        // Physical pixels per inch (BooxDay changes the app's density, not the panel's xdpi)
        val ppi = Resources.getSystem().displayMetrics.xdpi.toInt().takeIf { it in 150..500 } ?: 300
        return Triple(minOf(size[0], size[1]), maxOf(size[0], size[1]), ppi)
    }

    private suspend fun memoBitmap(context: Context, loader: DayDigestLoader, date: LocalDate): Bitmap? {
        val pageId = loader.memoPageId(date) ?: return null
        return MemoBitmapSource(context, DayPageRepository(context), PageRepository(context)).renderPage(date.toString(), pageId)
    }

    /**
     * What the image depends on: the day's content, which events are over or on (that changes
     * with the clock, not with the data), the size class, and a sample of the memo page's pixels.
     */
    private fun fingerprint(digest: DayDigest, nowMs: Long, sizeClass: SleepSizeClass, memo: Bitmap?): String {
        val timed = digest.events.filter { !it.allDay }
        val over = timed.count { (it.endMs ?: it.startMs) <= nowMs }
        val on = timed.filter { it.startMs <= nowMs && (it.endMs ?: it.startMs) > nowMs }.map { it.id }
        var memoHash = 0
        if (memo != null) {
            val stepX = maxOf(1, memo.width / 48)
            val stepY = maxOf(1, memo.height / 64)
            for (y in 0 until memo.height step stepY) for (x in 0 until memo.width step stepX) {
                memoHash = 31 * memoHash + memo.getPixel(x, y)
            }
        }
        return listOf(digest.hashCode(), over, on, sizeClass, memoHash).joinToString("|")
    }

    private fun write(bitmap: Bitmap, name: String): File {
        val dir = folder().apply { mkdirs() }
        val tmp = File(dir, "$NAME_PREFIX.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Could not save ${target.absolutePath}" }
        }
        return target
    }

    private fun settings(context: Context): AppSettings = SleepAgenda.settings(context)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
