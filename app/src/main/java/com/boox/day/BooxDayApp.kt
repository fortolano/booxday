package com.boox.day

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.boox.day.ai.TaskAiContextBuilderV1
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.KvProxy
import com.boox.day.editor.drawing.MarkdownHelper
import com.boox.day.notifications.NotificationChannelManager
import com.boox.day.notifications.NotificationPermissionHelper
import com.boox.day.notifications.TaskNotificationScheduler
import com.boox.day.sync.CalendarMigrationHelper
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.SyncScheduler
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.ocr.HandwritingNormalizerFactory
import com.boox.day.ocr.OcrConfigReconciliationCoordinator
import com.boox.day.ocr.TaskTextExtractor
import com.boox.day.ocr.TaskTextIndexer
import com.boox.day.ocr.TextAnnotationIndexer
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.compat.OnyxCompat
import com.boox.day.startup.StartupDiagnostics
import com.boox.day.startup.StartupStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class BooxDayApp : Application() {

    // Application-scoped coroutine scope for background tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The app's density for code that reads it through the application context (e.g. the Tasks
     * row reader in TaskTextExtractor), whatever the BOOX per-app DPI setting says (see AppDensity).
     */
    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        AppDensity.apply(res)
        return res
    }

    override fun onCreate() {
        super.onCreate()
        PerfProbe.install(this)  // TEMPORARY: MainScreen JIT measurement
        if (!runFatalStartupStep(
                stage = StartupStage.HIDDEN_API,
                summary = "Failed to enable Onyx hidden API access."
            ) { enableHiddenApiBypass() }) {
            return
        }

        if (!runFatalStartupStep(
                stage = StartupStage.RX_MANAGER,
                summary = "Failed to initialize the Onyx SDK app context."
            ) { OnyxCompat.initRxManager(this) }) {
            return
        }

        runSafeStartupStep("OpenRouter key") { initUserOpenRouterKey() }
        runSafeStartupStep("Removed features cleanup") { com.boox.day.startup.RemovedFeaturesCleanup.run(this) }
        runSafeStartupStep("Task AI context") { TaskAiContextBuilderV1.initialize(this) }
        runSafeStartupStep("Markdown helper") { MarkdownHelper.init(this) }
        runSafeStartupStep("Notification channels") { initNotificationChannels() }
        runSafeStartupStep("Boox notification mode") { initBooxWorkManagerMode() }
        runSafeStartupStep("Notification scheduling") { initNotificationScheduling() }
        runSafeStartupStep("OCR config") { reconcileOcrConfig() }
        runSafeStartupStep("Calendar migration startup") { runCalendarMigration() }
        runSafeStartupStep("Digital Ink startup") { initDigitalInk() }
        runSafeStartupStep("Semantic indexing startup") { initSemanticIndexing() }
        runSafeStartupStep("Pending calendar push") { requestPendingCalendarPush() }
        // Automatic Drive backup check (does nothing unless Drive backup is on and due)
        runSafeStartupStep("Drive auto-backup schedule") { com.boox.day.sync.DriveAutoBackupWorker.schedule(this) }
    }

    /** The user's own OpenRouter key (the only key AI features use), read from encrypted storage off the main thread. */
    private fun initUserOpenRouterKey() {
        com.boox.day.security.UserOpenRouterKey.init(this)
        applicationScope.launch(Dispatchers.IO) {
            com.boox.day.security.UserOpenRouterKey.ensureLoaded()
        }
        // Oct 2026: OpenRouter's model list, so retired models are skipped (refreshed daily with a key)
        com.boox.day.api.OpenRouterModelCatalog.init(this)
    }

    /**
     * Calendar changes saved while offline are marked PENDING_* and promised to sync "when online",
     * but no push was ever scheduled (plan Phase 2, Oct 2026). Queue one network-constrained push
     * at startup when any are waiting; SyncWorker itself skips standalone mode / no accounts.
     */
    private fun requestPendingCalendarPush() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val pending = AppDatabase.getDatabase(this@BooxDayApp).localEventDao().getPendingSyncCount()
                if (pending > 0) {
                    Log.i("BooxDayApp", "Pending calendar changes: $pending - requesting push")
                    SyncScheduler(this@BooxDayApp).requestPendingPush()
                }
            } catch (e: Exception) {
                Log.w("BooxDayApp", "Pending calendar push check failed: ${e.message}")
            }
        }
    }

    /**
     * Initialize notification channels for task due dates and event reminders.
     * Safe to call on every launch - Android ignores duplicate channel creation.
     */
    private fun initNotificationChannels() {
        NotificationChannelManager.createChannels(this)
    }

    /**
     * NOTIFICATION FIX: Set WorkManager-only mode on Boox devices.
     * This is a hard failsafe - even if runtime detection fails, this flag persists.
     *
     * Boox devices have firmware-level AlarmManager restrictions that silently
     * drop alarms even when all permissions are granted.
     */
    private fun initBooxWorkManagerMode() {
        if (isBooxDevice()) {
            val prefs = getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            if (!prefs.contains("force_workmanager_notifications")) {
                prefs.edit().putBoolean("force_workmanager_notifications", true).apply()
                Log.i("BooxDayApp", "Boox device detected at startup - enabled WorkManager-only notifications")
            }
        }
    }

    /**
     * Check if running on a Boox/Onyx device.
     * These devices have firmware-level AlarmManager restrictions.
     */
    private fun isBooxDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val device = Build.DEVICE?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""
        val product = Build.PRODUCT?.lowercase() ?: ""
        return manufacturer.contains("onyx") || brand.contains("onyx") ||
               device.contains("onyx") || model.contains("onyx") || product.contains("onyx") ||
               manufacturer.contains("boox") || brand.contains("boox") ||
               device.contains("boox") || model.contains("boox") || product.contains("boox")
    }

    /**
     * NOTIFICATION FIX: Initialize notification scheduling on app startup.
     *
     * Ensures daily scan worker is running if notifications are enabled.
     * This provides a recovery mechanism for Boox devices where WorkManager jobs
     * can be killed by aggressive battery management.
     *
     * Re-scheduling on every app start ensures notifications work even after:
     * - WorkManager jobs were killed by the system
     * - Device reboot (if BootReceiver didn't fire)
     * - App force-stop
     *
     * Safe to call multiple times - PeriodicWorkRequest with KEEP policy.
     */
    private fun initNotificationScheduling() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val settings = KvProxy(this@BooxDayApp)
                    .get(APP_SETTINGS_KEY, AppSettings.serializer())
                    ?: return@launch  // No settings = notifications not configured yet

                // Log diagnostic info for debugging notification issues
                Log.i("BooxDayApp", "Notification diagnostics: " +
                    "taskEnabled=${settings.taskNotificationsEnabled}, " +
                    "eventEnabled=${settings.eventNotificationsEnabled}, " +
                    "notifPermission=${NotificationPermissionHelper.hasNotificationPermission(this@BooxDayApp)}, " +
                    "exactAlarmPermission=${NotificationPermissionHelper.canScheduleExactAlarms(this@BooxDayApp)}, " +
                    "batteryExempt=${NotificationPermissionHelper.isBatteryOptimizationExempt(this@BooxDayApp)}, " +
                    "isBoox=${isBooxDevice()}")

                // Schedule daily scan if task notifications are enabled
                if (settings.taskNotificationsEnabled) {
                    TaskNotificationScheduler(this@BooxDayApp).scheduleDailyScan()
                    Log.d("BooxDayApp", "Daily scan worker scheduled on startup")
                }
            } catch (e: Exception) {
                Log.e("BooxDayApp", "Failed to init notification scheduling: ${e.message}")
            }
        }
    }

    /**
     * Run post-v17→v18 migration to tag existing Google Calendar events
     * with account information.
     *
     * This runs asynchronously after the database is ready and does not
     * block app startup. It's idempotent - safe to run multiple times.
     */
    private fun runCalendarMigration() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Skip if already migrated
                if (CalendarMigrationHelper.isMigrationComplete(this@BooxDayApp)) {
                    return@launch
                }

                val db = AppDatabase.getDatabase(this@BooxDayApp)
                val googleAuthManager = GoogleAuthManager(this@BooxDayApp)

                CalendarMigrationHelper.migrateExistingGoogleEvents(
                    context = this@BooxDayApp,
                    googleAuthManager = googleAuthManager,
                    accountDao = db.connectedAccountDao(),
                    eventDao = db.calendarEventDao()
                )

                // Invalidate events cache so UI picks up the updated accountId/providerId
                // Without this, cached events lack account info and provider icons don't show
                CalendarEventRepository(this@BooxDayApp).invalidateEventsCache()
                Log.d("BooxDayApp", "Events cache invalidated after migration")
            } catch (e: Exception) {
                Log.e("BooxDayApp", "Calendar migration error: ${e.message}")
                // Don't crash - migration will retry on next launch
            }
        }
    }

    /**
     * Enable hidden API access for Onyx SDK compatibility.
     *
     * The Onyx SDK (TouchHelper, EpdController, RawInputCallback) uses Android's
     * non-SDK interfaces for low-level e-ink display control. Without this bypass,
     * Android 10+ would block these calls, causing crashes or broken functionality.
     *
     * Wrapped in try-catch as a defensive measure - if bypass fails, app continues
     * but e-ink optimization features may not work properly.
     */
    private fun enableHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
            Log.d("BooxDayApp", "Hidden API bypass enabled for Onyx SDK")
        }
    }

    private inline fun runFatalStartupStep(
        stage: StartupStage,
        summary: String,
        block: () -> Unit
    ): Boolean {
        return try {
            block()
            true
        } catch (t: Throwable) {
            StartupDiagnostics.reportFatal(stage, t, summary)
            false
        }
    }

    private inline fun runSafeStartupStep(
        name: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e("BooxDayApp", "$name failed during startup", t)
        }
    }

    /**
     * Search indexing settings are local defaults (OcrQuotaConfig.DEFAULT). When they differ from the
     * ones last applied (an app update changed them, or a tablet still had settings cached from the
     * old commercial server), the indexing queue is reconciled once, off the main thread.
     */
    private fun reconcileOcrConfig() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                OcrConfigReconciliationCoordinator(this@BooxDayApp)
                    .reconcileIfNeeded("app startup local OCR defaults")
            } catch (e: Exception) {
                Log.e("BooxDayApp", "OCR config reconcile error: ${e.message}")
            }
        }
    }

    /**
     * Initialize Digital Ink Recognition for Task pages.
     *
     * Two components initialized in background (non-blocking):
     * 1. DigitalInkModelManager: Pre-downloads en-US recognition model (~20MB)
     * 2. HandwritingNormalizer: Loads spell correction dictionaries (~300KB)
     *
     * Both are fire-and-forget - if they fail, Task extraction gracefully degrades.
     */
    private fun initDigitalInk() {
        // Pre-download Digital Ink model (background, non-blocking)
        DigitalInkModelManager.preDownloadAsync(applicationScope)

        // Initialize TaskTextExtractor singleton (for PageDataManager hooks)
        TaskTextExtractor.getInstance(this, applicationScope)

        // Initialize TextAnnotationIndexer singleton (for semantic search of text annotations)
        TextAnnotationIndexer.getInstance(this, applicationScope)

        // Initialize TaskTextIndexer on the app scope (plan Phase 2, Oct 2026): MainScreen used to
        // create it first with its composition scope, so typed-task indexing died with that screen.
        TaskTextIndexer.getInstance(this, applicationScope)

        // Initialize HandwritingNormalizerFactory and pre-load English normalizer
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Pre-load English normalizer on startup (optimization)
                val englishNormalizer = HandwritingNormalizerFactory.getInstance(this@BooxDayApp, "en-US")
                if (englishNormalizer != null) {
                    Log.d("BooxDayApp", "English HandwritingNormalizer initialized: " +
                            "${englishNormalizer.getWordDictSize()} words, " +
                            "${englishNormalizer.getNamesDictSize()} names")
                }

                // Log all supported languages
                val supported = HandwritingNormalizerFactory.getSupportedLanguages(this@BooxDayApp)
                Log.i("BooxDayApp", "Handwriting normalization supported for: $supported")
            } catch (e: Exception) {
                Log.e("BooxDayApp", "HandwritingNormalizerFactory init failed: ${e.message}")
                // Non-fatal - extraction will work with reduced normalization
            }
        }
    }

    /**
     * Initialize semantic indexing - check for version changes and reindex if needed.
     * Also indexes any events that were never indexed (e.g., from data import/seeding).
     */
    private fun initSemanticIndexing() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val service = SemanticGeniusService(this@BooxDayApp)
                service.checkAndReindexIfVersionChanged()
                // Also index any unindexed events (handles seeding/import scenarios)
                service.indexUnindexedEvents()
            } catch (e: Exception) {
                Log.e("BooxDayApp", "Semantic indexing init failed: ${e.message}", e)
            }
        }
    }
}
