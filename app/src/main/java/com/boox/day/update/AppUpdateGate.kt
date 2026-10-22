package com.boox.day.update

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.BuildConfig
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.DrawCanvas
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app updates (Oct 2026). Checks GitHub for a newer release 15 s after the app starts (and again
 * after a day in the background, as BOOX keeps apps alive for days), then offers it in the update
 * popup (ui/dialogs/AppUpdatePopup.kt). Update Now asks once for the "Install unknown apps"
 * permission, downloads and checks the APK ([AppUpdateDownloader]) and hands it to Android
 * ([AppUpdateInstaller]). Not Now stays quiet for a day. Compose state like [com.boox.day.ui.AiKeyGate]:
 * MainScreen shows the popup and keeps raw drawing off while it is open.
 *
 * Times are kept in their own preferences file, on this tablet only (not in the exported settings).
 */
object AppUpdateGate {
    private const val TAG = "AppUpdateGate"
    private const val PREFS = "app_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_ms"
    private const val KEY_LAST_RUN_VERSION_CODE = "last_run_version_code"
    private const val LAUNCH_DELAY_MS = 15_000L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    sealed class State {
        object Idle : State()

        /** About → Check for Updates is waiting for GitHub. */
        object Checking : State()

        /** About → Check for Updates found nothing newer. */
        object UpToDate : State()
        data class Available(val info: AppUpdateChecker.UpdateInfo) : State()

        /** Update Now needs "Install unknown apps" first; continues by itself when the user comes back. */
        data class NeedsPermission(val info: AppUpdateChecker.UpdateInfo) : State()
        data class Downloading(val info: AppUpdateChecker.UpdateInfo, val percent: Int) : State()
        data class Installing(val info: AppUpdateChecker.UpdateInfo) : State()

        /** [info] is set when Try Again can start the download again. */
        data class Failed(val info: AppUpdateChecker.UpdateInfo?, val message: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    val isOpen: Boolean get() = state !is State.Idle

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var startedAtMs = 0L

    /** MainActivity.onCreate: clears old downloads, says "updated" once, plans the launch check. */
    fun onAppStart(context: Context) {
        if (startedAtMs != 0L) return  // activity recreated: once per process
        startedAtMs = SystemClock.elapsedRealtime()
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) { removeOldDownloads(app) }
        announceIfUpdated(app)
        scope.launch {
            delay(LAUNCH_DELAY_MS)
            checkAutomatically(app)
        }
    }

    /**
     * MainActivity.onResume: back from the permission screen with the switch on → download now.
     * Otherwise, when the app has been running for over a day since the last check, check again.
     */
    fun onResume(context: Context) {
        val app = context.applicationContext
        val current = state
        if (current is State.NeedsPermission) {
            if (AppUpdateInstaller.canInstall(app)) startDownload(app, current.info)
            return
        }
        if (startedAtMs == 0L || SystemClock.elapsedRealtime() - startedAtMs < 2 * LAUNCH_DELAY_MS) return
        if (System.currentTimeMillis() - prefs(app).getLong(KEY_LAST_CHECK, 0L) > DAY_MS) {
            scope.launch { checkAutomatically(app) }
        }
    }

    private suspend fun checkAutomatically(app: Context) {
        if (state !is State.Idle || job?.isActive == true) return
        val prefs = prefs(app)
        if (System.currentTimeMillis() < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)) return
        // First run: the Welcome popup comes first; the next start checks
        if (GlobalAppSettings.current.onboardingDismissedAt == 0L) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        val result = AppUpdateChecker.check()
        if (result !is AppUpdateChecker.Result.Available) return
        // Don't cover the page in the middle of writing
        while (DrawCanvas.isStylusRecentlyActive()) delay(1_000L)
        if (state is State.Idle) {
            Log.i(TAG, "Update ${result.info.version} available")
            state = State.Available(result.info)
        }
    }

    /** About → Check for Updates. [pretendVersion]: debug builds test the flow as an older version. */
    fun checkNow(context: Context, pretendVersion: String? = null) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        state = State.Checking
        job = scope.launch {
            prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            state = when (val result = AppUpdateChecker.check(pretendVersion ?: BuildConfig.VERSION_NAME)) {
                is AppUpdateChecker.Result.Available -> State.Available(result.info)
                AppUpdateChecker.Result.UpToDate -> State.UpToDate
                is AppUpdateChecker.Result.Failed ->
                    State.Failed(null, "Couldn't reach the update server. Check the Wi-Fi connection and try again.")
            }
        }
    }

    /** Not Now: no automatic prompt for a day (About can still check). */
    fun notNow(context: Context) {
        prefs(context.applicationContext).edit()
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + DAY_MS)
            .apply()
        state = State.Idle
    }

    /** Update Now / Try Again. */
    fun updateNow(context: Context) {
        val info = when (val current = state) {
            is State.Available -> current.info
            is State.Failed -> current.info
            is State.NeedsPermission -> current.info
            else -> null
        } ?: return
        val app = context.applicationContext
        if (!AppUpdateInstaller.canInstall(app)) {
            state = State.NeedsPermission(info)
            return
        }
        startDownload(app, info)
    }

    fun openInstallPermission(context: Context) = AppUpdateInstaller.openInstallPermission(context)

    /** Cancel during the download: the partial file stays, so the next try continues. */
    fun cancel() {
        job?.cancel()
        state = State.Idle
    }

    /** Close on "up to date", a failure or the install screen. */
    fun close() {
        if (state !is State.Downloading) state = State.Idle
    }

    /** From [AppUpdateResultReceiver]: Android did not install the update. */
    fun fail(message: String) = onMain {
        val info = (state as? State.Installing)?.info
        state = State.Failed(info, message)
    }

    private fun startDownload(app: Context, info: AppUpdateChecker.UpdateInfo) {
        job?.cancel()
        state = State.Downloading(info, 0)
        job = scope.launch {
            val result = AppUpdateDownloader.download(app, info) { percent ->
                onMain { if (state is State.Downloading) state = State.Downloading(info, percent) }
            }
            when (result) {
                is AppUpdateDownloader.Result.Ready -> install(app, info, result.apk)
                is AppUpdateDownloader.Result.Failed -> state = State.Failed(info, result.message)
            }
        }
    }

    private suspend fun install(app: Context, info: AppUpdateChecker.UpdateInfo, apk: File) {
        state = State.Installing(info)
        if (!AppUpdateInstaller.install(app, apk)) {
            state = State.Failed(info, "The update couldn't be installed. Try again, or download it from the website.")
        }
    }

    /** Snackbar on the first start after any update (in-app or by hand). */
    private fun announceIfUpdated(app: Context) {
        val prefs = prefs(app)
        val lastCode = prefs.getLong(KEY_LAST_RUN_VERSION_CODE, 0L)
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        if (lastCode == currentCode) return
        prefs.edit().putLong(KEY_LAST_RUN_VERSION_CODE, currentCode).apply()
        if (lastCode in 1 until currentCode) {
            scope.launch {
                delay(3_000L)  // after the main screen is up
                SnackState.globalSnackFlow.tryEmit(
                    SnackConf(text = "Boox Day was updated to ${BuildConfig.VERSION_NAME}.", duration = SnackConf.DURATION_STANDARD)
                )
            }
        }
    }

    /** Keeps only a partial download of a version newer than this one (to continue it). */
    private fun removeOldDownloads(app: Context) {
        AppUpdateDownloader.updatesDir(app).listFiles()?.forEach { file ->
            val version = file.name.removePrefix("booxday-").substringBefore(".apk")
            val keep = file.name.endsWith(".apk.part") && AppUpdateChecker.isNewer(version, BuildConfig.VERSION_NAME)
            if (!keep) file.delete()
        }
    }

    // ---- Debug builds only (About shows the buttons when BuildConfig.DEBUG) -------------------

    /** Runs the whole flow against the real latest release as if this were version 1.0.0. */
    fun debugCheckAsOldVersion(context: Context) = checkNow(context, pretendVersion = "1.0.0")

    /**
     * Installs `<app external files>/update-test.apk` through the same checks and installer, e.g. after
     * `adb push app-debug.apk /sdcard/Android/data/com.boox.day/files/update-test.apk`.
     */
    fun debugInstallTestApk(context: Context) {
        val app = context.applicationContext
        val file = File(app.getExternalFilesDir(null), "update-test.apk")
        if (!file.exists()) {
            state = State.Failed(null, "No test APK. Push one to ${file.path}")
            return
        }
        if (!AppUpdateInstaller.canInstall(app)) {
            AppUpdateInstaller.openInstallPermission(app)
            state = State.Failed(null, "Allow installs from Boox Day, then tap the debug button again.")
            return
        }
        job?.cancel()
        job = scope.launch {
            // Reading the signature goes through the whole APK: off the main thread
            val checked = withContext(Dispatchers.IO) { AppUpdateDownloader.checkPackage(app, file) }
            when (checked) {
                is AppUpdateDownloader.Result.Failed -> state = State.Failed(null, checked.message)
                is AppUpdateDownloader.Result.Ready -> {
                    val testInfo = AppUpdateChecker.UpdateInfo("test", emptyList(), "", file.length(), "")
                    install(app, testInfo, checked.apk)
                }
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}
