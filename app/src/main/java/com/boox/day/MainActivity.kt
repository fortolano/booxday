package com.boox.day


import com.boox.day.security.UserApiKeyStore
import com.boox.day.data.db.KvRepository
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.EditorSettingCacheManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.reencodeStrokePointsToSB1
import com.boox.day.editor.DrawCanvas
import com.boox.day.compat.OnyxCompat
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.startup.StartupDiagnostics
import com.boox.day.startup.StartupStatus
import com.boox.day.ui.LocalSnackContext
import com.boox.day.ui.Router
import com.boox.day.ui.SnackBar
import com.boox.day.ui.SnackState
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.ui.views.StartupFailureScreen
import com.boox.day.ui.views.hasFilePermission
import com.boox.day.tips.TipsService
import android.util.Log
import android.view.MotionEvent
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


// Safe initialization with fallback for older/incompatible Boox devices
var SCREEN_WIDTH = OnyxCompat.DEFAULT_SCREEN_WIDTH
var SCREEN_HEIGHT = OnyxCompat.DEFAULT_SCREEN_HEIGHT

var TAG = "MainActivity"
const val APP_SETTINGS_KEY = "APP_SETTINGS"
const val PACKAGE_NAME = "com.boox.day"

// Fast SharedPreferences for routing decision (avoids Room query on startup)
const val QUICK_SETTINGS_PREFS = "quick_settings"
const val SHOW_WELCOME_COMPLETED_KEY = "show_welcome_completed"


@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@ExperimentalFoundationApi
class MainActivity : ComponentActivity() {

    /**
     * Sets the app's display density ([AppDensity.dpi], fixed per screen size) on the activity and
     * application resources.
     * Higher DPI = Larger UI elements (more zoomed in)
     * Lower DPI = Smaller UI elements (more zoomed out)
     *
     * @param propagateToCompose When true, calls updateConfiguration() to force Compose's
     *   LocalDensity to pick up the new density. Only use when returning from external
     *   activities (onResume). Do NOT use in onCreate — attachBaseContext already handles
     *   initial density, and updateConfiguration() in onCreate can cause the system to
     *   briefly apply the device's native DPI before our override takes effect.
     */
    private fun setAppDensity(targetDpi: Int = AppDensity.dpi, propagateToCompose: Boolean = false) {
        AppDensity.apply(resources, targetDpi)
        AppDensity.apply(applicationContext.resources, targetDpi)

        // Propagate configuration change to Compose's LocalDensity/LocalConfiguration.
        // Only needed when returning from external activities (Google Play billing, MSAL auth)
        // that may have reset the density. NOT safe in onCreate — causes density flicker.
        if (propagateToCompose) {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(resources.configuration, resources.displayMetrics)
            // The BOOX per-app DPI hook runs inside updateConfiguration (see AppDensity)
            AppDensity.apply(resources, targetDpi)
        }

        Log.i(TAG, "App density set to $targetDpi DPI (factor: ${targetDpi / 160f}, propagate=$propagateToCompose)")
    }

    /**
     * Override attachBaseContext to set DPI at the earliest possible lifecycle point.
     * This ensures the correct density is applied before any views are inflated.
     */
    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            com.boox.day.diagnostics.StartReport.baseContext(newBase.resources)
            val config = newBase.resources.configuration
            config.densityDpi = AppDensity.dpi
            val context = newBase.createConfigurationContext(config)
            super.attachBaseContext(context)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    /**
     * Keeps the app's density whatever the BOOX per-app DPI setting says: the BOOX hook replaces
     * it on every resources update, and Compose reads it from here when the screen and each popup
     * window are created (see [AppDensity]). A no-op while the density is already right.
     */
    override fun getResources(): Resources {
        val res = super.getResources()
        AppDensity.apply(res)
        return res
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set app-wide DPI for optimal toolbar fit and readability
        setAppDensity()
        com.boox.day.diagnostics.StartReport.activityCreated(this)

        enableFullScreen()
        SCREEN_WIDTH = applicationContext.resources.displayMetrics.widthPixels
        SCREEN_HEIGHT = applicationContext.resources.displayMetrics.heightPixels

        if (StartupDiagnostics.hasFatalFailure()) {
            setBooxDayContent()
            return
        }

        Log.i(TAG, "BooxDay started")


        val snackState = SnackState()
        snackState.registerGlobalSnackObserver()
        snackState.registerCancelGlobalSnackObserver()
        PageDataManager.registerComponentCallbacks(this)

        // PERFORMANCE FIX: Hybrid settings loading
        // 1. Read showWelcome from fast SharedPreferences (~1ms) - required for Router decision
        // 2. Load full settings async from Room (~30-50ms) - doesn't block UI
        val quickPrefs = getSharedPreferences(QUICK_SETTINGS_PREFS, MODE_PRIVATE)
        val showWelcomeCompleted = quickPrefs.getBoolean(SHOW_WELCOME_COMPLETED_KEY, false)

        // Oct 2026: a recreated activity (config change) keeps the settings already in memory. Putting
        // defaults back, or reloading the database copy (written after memory), could undo the user's
        // latest changes and brought dismissed setup dots back
        val appContext = applicationContext
        if (!GlobalAppSettings.loaded) {
        // Initialize with defaults + correct showWelcome flag for immediate Router use
        val initialSettings = AppSettings(version = 1, showWelcome = !showWelcomeCompleted)

        // Update GlobalAppSettings with initial values (Router can now decide correctly)
        GlobalAppSettings.update(initialSettings)

        // Load full settings async from Room database (Phase 1 H11 / Decision D7: isolate key reads
        // from Room reads, and route settings read/decode failures to StartupFailureScreen instead
        // of marking loaded on defaults and overwriting saved settings).
        this.lifecycleScope.launch(Dispatchers.IO) {
            when (val outcome = com.boox.day.startup.StartupSettingsLoader.load(appContext)) {
                is com.boox.day.startup.StartupSettingsLoader.Outcome.Failed -> {
                    StartupDiagnostics.reportFatal(
                        stage = com.boox.day.startup.StartupStage.SETTINGS_LOAD,
                        throwable = outcome.error,
                        summary = "BooxDay couldn't read your saved settings. Tap Retry so your settings aren't reset to defaults."
                    )
                    com.boox.day.diagnostics.StartReport.mark("Settings load failed", outcome.error)
                    return@launch
                }
                is com.boox.day.startup.StartupSettingsLoader.Outcome.Loaded -> {
                    val mergedSettings = outcome.settings
                    GlobalAppSettings.update(mergedSettings)
                    GlobalAppSettings.loaded = true
                    com.boox.day.diagnostics.StartReport.mark("Settings loaded")
                    if (outcome.fromStorage) {
                        Log.i(TAG, "Full settings loaded async from Room")
                    }

                    // Post-load maintenance runs in isolated guards so secondary errors never block startup
                    if (outcome.fromStorage) {
                        runCatching {
                            // Phase 3 (Oct 2026): older versions also kept the OpenRouter key in the stored
                            // settings JSON; save once without it (the key stays in UserApiKeyStore)
                            val storedJson = KvRepository(appContext).get(APP_SETTINGS_KEY)?.value
                            if (storedJson?.contains(KvProxy.OPENROUTER_KEY_FIELD) == true) {
                                KvProxy(appContext).setAppSettings(mergedSettings)
                            }
                        }.onFailure { Log.w(TAG, "Legacy key strip skipped: ${it.javaClass.simpleName}") }

                        runCatching {
                            DigitalInkModelManager.syncLanguageFromSettings()
                        }.onFailure { Log.w(TAG, "Handwriting language sync skipped: ${it.javaClass.simpleName}") }

                        runCatching {
                            if (mergedSettings.taskNotificationsEnabled) {
                                TaskRepository(appContext).scheduleNotificationsForExistingTasks()
                            }
                            if (mergedSettings.eventNotificationsEnabled) {
                                LocalEventRepository(appContext).scheduleNotificationsForUpcomingEvents()
                            }
                        }.onFailure { Log.w(TAG, "Startup reminder scheduling skipped: ${it.javaClass.simpleName}") }
                    }
                }
            }
        }
        }

        EditorSettingCacheManager.init(applicationContext)
        // Note: Task/event notification scheduling moved to async settings load block above

        // Legacy stroke migration only runs if user has file permission (accesses old external storage location)
        if (hasFilePermission(this)) {
            this.lifecycleScope.launch(Dispatchers.IO) {
                reencodeStrokePointsToSB1(this@MainActivity)
            }
        }

        // Clean up old preview cache files on startup (runs after 5s delay to not compete with initialization)
        this.lifecycleScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000)
            com.boox.day.data.cleanupPreviewCache(applicationContext)
        }

        //EpdDeviceManager.enterAnimationUpdate(true);
//        val intentData = intent.data?.lastPathSegment

        com.boox.day.notifications.NotificationNavigation.handleIntent(intent)
        setBooxDayContent(snackState)

        // In-app updates (Oct 2026): checks GitHub for a newer release 15 s from now
        com.boox.day.update.AppUpdateGate.onAppStart(this)
        // A crash report saved by the last run (Oct 2026, diagnostics/CrashReporter)
        com.boox.day.diagnostics.CrashReportGate.onAppStart(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.boox.day.notifications.NotificationNavigation.handleIntent(intent)
    }


    override fun onResume() {
        super.onResume()
        // Reset DPI when returning from external activities (e.g., MSAL Outlook auth)
        // External activities (Chrome Custom Tabs, system browser) may use default system DPI,
        // which can corrupt our custom DPI setting. Reset it every time we resume.
        // propagateToCompose=true forces Compose to pick up the restored density.
        setAppDensity(propagateToCompose = true)
        com.boox.day.diagnostics.StartReport.mark("Activity.onResume")

        if (StartupDiagnostics.hasFatalFailure()) {
            return
        }

        // BATTERY FIX: Restart tips timer when app comes to foreground
        // TipsService.start() internally checks if tips are enabled
        TipsService.start()

        // In-app updates: continue after the "Install unknown apps" screen; daily check
        com.boox.day.update.AppUpdateGate.onResume(this)

        // NOTIFICATION FIX: Detect permission changes from Android Settings
        // When user grants POST_NOTIFICATIONS outside the app (via system settings),
        // the permission launcher callback doesn't fire. We need to detect this on resume.
        lifecycleScope.launch(Dispatchers.IO) {
            // CRITICAL: Load settings from persistent storage, not in-memory cache
            // GlobalAppSettings.current may still be defaults on fast resume
            val settings = try {
                KvProxy(applicationContext).get(APP_SETTINGS_KEY, AppSettings.serializer())
                    ?: AppSettings(version = 1)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings from storage: ${e.message ?: e.javaClass.simpleName}, using defaults", e)
                AppSettings(version = 1)
            }

            // Only check if notifications are enabled in settings
            if (settings.taskNotificationsEnabled || settings.eventNotificationsEnabled) {
                val hasPermission = com.boox.day.notifications.NotificationPermissionHelper.hasNotificationPermission(this@MainActivity)
                val hasExactAlarm = com.boox.day.notifications.NotificationPermissionHelper.canScheduleExactAlarms(this@MainActivity)

                // Track permission state transitions
                val prefs = getSharedPreferences(QUICK_SETTINGS_PREFS, MODE_PRIVATE)
                val hadPermissionBefore = prefs.getBoolean("notification_permission_granted", false)
                val hadExactAlarmBefore = prefs.getBoolean("exact_alarm_permission_granted", true)

                val notificationPermissionJustGranted = hasPermission && !hadPermissionBefore
                val exactAlarmJustGranted = hasExactAlarm && !hadExactAlarmBefore
                val exactAlarmJustRevoked = !hasExactAlarm && hadExactAlarmBefore

                if (notificationPermissionJustGranted || exactAlarmJustGranted || exactAlarmJustRevoked) {
                    if (notificationPermissionJustGranted) {
                        Log.i(TAG, "Notification permission detected as newly granted - triggering scheduling")
                    }
                    if (exactAlarmJustGranted) {
                        Log.i(TAG, "Exact alarm permission detected as newly granted - rescheduling for exact alarms")
                    }
                    if (exactAlarmJustRevoked) {
                        Log.w(TAG, "Exact alarm permission revoked - rescheduling via WorkManager fallback")
                    }

                    try {
                        if (settings.taskNotificationsEnabled) {
                            val taskRepo = TaskRepository(applicationContext)
                            taskRepo.scheduleNotificationsForExistingTasks()
                            Log.d(TAG, "Scheduled notifications for existing tasks")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule task notifications: ${e.message}")
                    }

                    try {
                        if (settings.eventNotificationsEnabled) {
                            val eventRepo = LocalEventRepository(applicationContext)
                            eventRepo.scheduleNotificationsForUpcomingEvents()
                            Log.d(TAG, "Scheduled notifications for upcoming events")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule event notifications: ${e.message}")
                    }
                }

                // Update stored permission state for next check
                prefs.edit()
                    .putBoolean("notification_permission_granted", hasPermission)
                    .putBoolean("exact_alarm_permission_granted", hasExactAlarm)
                    .apply()
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (StartupDiagnostics.hasFatalFailure()) {
            return
        }
        // redraw after device sleep
        this.lifecycleScope.launch {
            DrawCanvas.restartAfterConfChange.emit(Unit)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!DrawCanvas.dispatchAndroidStylusEvent(event)) return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            // A palm may already have started a Compose gesture before the pen arrived.
            val cancel = MotionEvent.obtain(event)
            try { cancel.action = MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(cancel) }
            finally { cancel.recycle() }
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        com.boox.day.diagnostics.StartReport.activityStopped()
        // Page images still waiting for the pen to rest: save them before the app can be killed
        PageDataManager.flushAllPendingPersists()
        // The tablet is often put to sleep right after: bring the sleep-screen agenda up to date
        com.boox.day.sleepscreen.SleepAgenda.onAppBackground(this)
    }

    override fun onPause() {
        DrawCanvas.cancelAndroidStylusInput()
        super.onPause()
        com.boox.day.diagnostics.StartReport.mark("Activity.onPause")

        // BATTERY FIX: Stop tips timer when app goes to background
        TipsService.stop()

        // BATTERY FIX: Stop FileObservers (kernel inotify watches) when app goes to background
        PageDataManager.stopAllFileObservers()

        if (StartupDiagnostics.hasFatalFailure()) {
            return
        }

        this.lifecycleScope.launch {
            Log.d("QuickSettings", "App is paused - maybe quick settings opened?")

            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        Log.d(TAG, "OnWindowFocusChanged: $hasFocus")
        super.onWindowFocusChanged(hasFocus)
        if (com.boox.day.diagnostics.StartReport.on) com.boox.day.diagnostics.StartReport.mark("Window focus: $hasFocus")
        if (!hasFocus) DrawCanvas.cancelAndroidStylusInput()
        if (hasFocus) {
            enableFullScreen()
        }
        if (StartupDiagnostics.hasFatalFailure()) {
            return
        }
        lifecycleScope.launch {
            DrawCanvas.onFocusChange.emit(hasFocus)
        }
    }


    // when the screen orientation is changed, set new screen width restart is not necessary,
    // as we need first to update page dimensions which is done in EditorView()
    override fun onConfigurationChanged(newConfig: Configuration) {
        DrawCanvas.cancelAndroidStylusInput()
        super.onConfigurationChanged(newConfig)
        if (com.boox.day.diagnostics.StartReport.on) com.boox.day.diagnostics.StartReport.mark("Configuration changed: ${newConfig.densityDpi} dpi, ${newConfig.screenWidthDp}x${newConfig.screenHeightDp} dp")
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "Switched to Landscape")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "Switched to Portrait")
        }
        SCREEN_WIDTH = applicationContext.resources.displayMetrics.widthPixels
        SCREEN_HEIGHT = applicationContext.resources.displayMetrics.heightPixels
        // Not necessary, done in DrawCanvas.surfaceChanged()
//        this.lifecycleScope.launch {
//            DrawCanvas.restartAfterConfChange.emit(Unit)
//        }
    }


    // written by GPT, but it works
    // needs to be checked if it is ok approach.
    private fun enableFullScreen() {
        // Turn on onyx optimization, no idea what it does.
        // https://github.com/onyx-intl/OnyxAndroidDemo/blob/3290434f0edba751ec907d777fe95208378ae752/app/OnyxAndroidDemo/src/main/java/com/android/onyx/demo/AppOptimizeActivity.java#L4
        Intent().apply {
            action = "com.onyx.app.optimize.setting"
            putExtra("optimize_fullScreen", true)
            putExtra("optimize_pkgName", "com.boox.day")
            sendBroadcast(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            // 'setDecorFitsSystemWindows(Boolean): Unit' is deprecated. Deprecated in Java
//            window.setDecorFitsSystemWindows(false)
            WindowCompat.setDecorFitsSystemWindows(window, false)
//            if (window.insetsController != null) {
//                window.insetsController!!.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
//                window.insetsController!!.systemBarsBehavior =
//                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//            }
            // Safely access the WindowInsetsController
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                Log.e(TAG, "WindowInsetsController is null")
            }
        } else {
            // For Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }


    private fun setBooxDayContent(snackState: SnackState? = null) {
        setContent {
            val startupStatus by StartupDiagnostics.status.collectAsState()

            InkaTheme {
                when (val status = startupStatus) {
                    is StartupStatus.Fatal -> {
                        StartupFailureScreen(
                            report = status.report,
                            onRetry = {
                                StartupDiagnostics.clearFatalFailure()
                                recreate()
                            }
                        )
                    }

                    StartupStatus.Healthy -> {
                        if (snackState != null) {
                            CompositionLocalProvider(LocalSnackContext provides snackState) {
                                Box(
                                    Modifier
                                        .background(Color.White)
                                ) {
                                    Router()
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.Black)
                                )
                                // TODO: maybe this snack is responsible for buttons not clickable on the button of screen?
                                //  No, without it the issue still persists.
                                SnackBar(state = snackState)
                            }
                        } else {
                            Box(
                                Modifier
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }

}
