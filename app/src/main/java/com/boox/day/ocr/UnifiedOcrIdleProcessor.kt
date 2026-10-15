package com.boox.day.ocr

import android.content.Context
import android.os.BatteryManager
import com.boox.day.BuildConfig
import android.util.Log
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.security.UserOpenRouterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  ⚠️  CRITICAL COMPONENT - READ BEFORE MODIFYING  ⚠️                         │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │                                                                              │
 * │  This processor runs OCR during foreground IDLE periods only.               │
 * │  ANY modification risks making the app unresponsive on e-ink devices.       │
 * │                                                                              │
 * │  MANDATORY REQUIREMENTS (DO NOT REMOVE):                                     │
 * │                                                                              │
 * │  1. @Volatile shouldAbort - cross-thread visibility for instant abort       │
 * │  2. shouldAbort check at START of every page loop iteration                 │
 * │  3. Memory check: 100MB free minimum (canProcess())                         │
 * │  4. Battery check: 15% minimum or charging                                  │
 * │  5. yield() after EVERY page to let UI thread breathe                       │
 * │  6. Bitmap.recycle() after EVERY page (in UnifiedOcrPipeline)               │
 * │  7. Dispatchers.Default for processing (NEVER Dispatchers.Main)             │
 * │                                                                              │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * NO WorkManager - Boox devices aggressively freeze background apps.
 * This processor runs ONLY when:
 * 1. App is in foreground
 * 2. User has been idle for 5+ seconds
 * 3. Memory is adequate (>100MB free)
 * 4. Battery is adequate (>15% or charging)
 * 5. OCR indexing is turned on in settings
 */
class UnifiedOcrIdleProcessor(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedOcrIdleProc"

        /** Default idle threshold (MainScreen's idle clock uses it) */
        const val DEFAULT_IDLE_THRESHOLD_MS = 5_000L

        /** Stylus idle threshold - shorter because stylus activity is more specific (5 seconds) */
        const val STYLUS_IDLE_THRESHOLD_MS = 5_000L

        /** Old default cooldown between pages (not used: OcrQuotaManager supplies the value) */
        const val DEFAULT_COOLDOWN_BETWEEN_PAGES_MS = 7_000L

        /** Old default max pages per idle session (not used: OcrQuotaManager supplies the value) */
        const val DEFAULT_MAX_PAGES_PER_IDLE_SESSION = 2

        /**
         * Battery (Oct 2026): after an idle pass can do no work, idle passes rest until the user is
         * active again or this much time has passed. MainScreen calls tryProcessIfIdle() every 2 s
         * while the user is idle. "No work" is an empty queue (each pass rebuilt the whole queue and
         * rewrote it to SharedPreferences), or pages that all wait for an OpenRouter key or AI budget
         * (BUDGET_EXHAUSTED): without a key the pass restarted every 2 s, flipping processingState
         * (two MainScreen recompositions) and re-running the backfill check each time. A memo the
         * user just wrote is still indexed in the next idle period; "Index now" (processNow) never
         * waits, and saving a key ends a rest that was waiting for one.
         */
        const val NO_WORK_BACKOFF_MS = 10 * 60_000L

        /**
         * Whether an idle pass should rest instead of running: the last pass found no work, the user
         * has not been active since, and [NO_WORK_BACKOFF_MS] has not passed. A rest that was only
         * waiting for a key ends as soon as a key is saved.
         */
        internal fun shouldRest(
            noWorkPassAtMs: Long,
            lastUserActivityMs: Long,
            nowMs: Long,
            waitingForKey: Boolean,
            hasKeyNow: Boolean
        ): Boolean {
            if (noWorkPassAtMs <= lastUserActivityMs) return false
            if (nowMs - noWorkPassAtMs >= NO_WORK_BACKOFF_MS) return false
            return !(waitingForKey && hasKeyNow)
        }

        // Note: MIN_FREE_MEMORY_MB and MIN_BATTERY_PERCENT come from the local OCR config
        // (OcrQuotaConfig.minFreeMemoryMb and OcrQuotaConfig.minBatteryPercent)
    }

    // Processing parameters, read through OcrQuotaManager (local defaults plus its own
    // batch size and cooldown).
    private val idleThresholdMs: Long get() = quotaManager.idleThresholdMs
    private val cooldownBetweenPagesMs: Long get() = quotaManager.cooldownBetweenPagesMs
    private val maxPagesPerIdleSession: Int get() = quotaManager.maxPagesPerIdleSession
    private val minFreeMemoryMb: Int get() = quotaManager.minFreeMemoryMb
    private val minBatteryPercent: Int get() = quotaManager.minBatteryPercent

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * CRITICAL: @Volatile for cross-thread visibility.
     * When set to true, processing MUST stop immediately.
     */
    @Volatile
    private var shouldAbort = false

    /** When the last pass found no work it could do (0 = never); see NO_WORK_BACKOFF_MS. */
    @Volatile
    private var lastNoWorkPassAtMs = 0L

    /** That pass stopped because there is no OpenRouter key, so saving a key ends the rest. */
    @Volatile
    private var noWorkPassWaitingForKey = false

    private val _processingState = MutableStateFlow<ProcessingState?>(null)
    val processingState: StateFlow<ProcessingState?> = _processingState.asStateFlow()

    // Lazy initialization of dependencies
    private val scheduler by lazy { UnifiedOcrScheduler(context) }
    private val pipeline by lazy { UnifiedOcrPipeline(context) }
    private val quotaManager by lazy { OcrQuotaManager(context) }

    // SMART LINK BACKFILL FIX: Repositories for backfill completion check
    private val ocrRepository by lazy { UnifiedOcrRepository(context) }

    // Shared BooxNoteIndexer from MainScreen (properly wired with semanticGeniusService).
    // Must be set before backfill completion triggers auto-link.
    var booxNoteIndexer: BooxNoteIndexer? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processing state for UI feedback.
     */
    data class ProcessingState(
        val isProcessing: Boolean,
        val currentSource: String?,
        val currentItem: String?,
        val currentPage: Int,
        val totalPages: Int
    )

    /**
     * Try to process OCR queue if conditions are met.
     * Called from MainScreen when idle is detected.
     *
     * @param lastUserActivityMs when the user last tapped the app or wrote with the pen (MainScreen's idle clock)
     * @return true if processing started, false if conditions not met
     */
    suspend fun tryProcessIfIdle(lastUserActivityMs: Long): Boolean {
        // Battery: a pass in this idle period already found no work - rest instead of starting
        // another pass every 2 s
        if (shouldRest(
                noWorkPassAtMs = lastNoWorkPassAtMs,
                lastUserActivityMs = lastUserActivityMs,
                nowMs = System.currentTimeMillis(),
                waitingForKey = noWorkPassWaitingForKey,
                hasKeyNow = UserOpenRouterKey.hasKey
            )
        ) {
            return false
        }

        if (!canProcess()) {
            Log.d(TAG, "Cannot process: safety checks failed")
            return false
        }

        shouldAbort = false
        processQueue()
        return true
    }

    /**
     * INSTANT abort on any user interaction.
     * Call this when user touches screen, uses stylus, or presses any key.
     */
    fun abortNow() {
        shouldAbort = true
        _processingState.value = null
        Log.d(TAG, "Abort requested - processing will stop immediately")
    }

    /**
     * Check if currently processing.
     */
    fun isProcessing(): Boolean {
        return _processingState.value?.isProcessing == true
    }

    /**
     * Manual "Index Now" trigger from settings.
     * Bypasses idle check but still respects the OCR config flag and memory/battery.
     */
    suspend fun processNow() {
        if (!quotaManager.isOcrFeatureEnabled) {
            Log.w(TAG, "Cannot process now: OCR disabled in OCR config (${quotaManager.describeAppliedConfig()})")
            return
        }

        if (!canProcessResources()) {
            Log.w(TAG, "Cannot process now: insufficient resources")
            return
        }

        shouldAbort = false
        // Clear and rebuild queue for fresh indexing
        scheduler.clearQueue()
        processQueue()
    }

    /**
     * Get current queue statistics.
     */
    fun getQueueStats(): UnifiedOcrScheduler.QueueStats {
        return scheduler.getQueueStats()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SAFETY CHECKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check ALL conditions for idle processing.
     * ALL checks are MANDATORY - DO NOT REMOVE.
     */
    private fun canProcess(): Boolean {
        if (!quotaManager.isOcrFeatureEnabled) {
            Log.d(TAG, "OCR disabled in OCR config (${quotaManager.describeAppliedConfig()})")
            return false
        }

        // CHECK 1: Feature enabled in settings (Boox Notes OR general OCR)
        val settings = GlobalAppSettings.current
        if (!settings.ocrSettings.enabled) {
            Log.d(TAG, "OCR disabled: OCR indexing not enabled in settings")
            return false
        }

        // CHECK 2: Check resource conditions
        return canProcessResources()
    }

    /**
     * Check resource conditions (memory, battery).
     * Separated so processNow() can bypass idle check but still check resources.
     */
    private fun canProcessResources(): Boolean {
        // CHECK 1: Memory (MANDATORY - DO NOT REMOVE)
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val availableMemory = maxMemory - totalMemory + freeMemory
        val freeMb = availableMemory / (1024 * 1024)

        if (freeMb < minFreeMemoryMb) {
            Log.d(TAG, "Insufficient memory: ${freeMb}MB free, need ${minFreeMemoryMb}MB")
            return false
        }

        // CHECK 2: Battery (MANDATORY - DO NOT REMOVE)
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            if (batteryPercent < minBatteryPercent && !isCharging) {
                Log.d(TAG, "Low battery: ${batteryPercent}%, not charging")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check battery: ${e.message}")
            // Continue anyway if battery check fails
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEUE PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process the OCR queue on background thread.
     *
     * CRITICAL REQUIREMENTS:
     * - Check shouldAbort at START of every page iteration
     * - Call yield() after every page
     * - Re-verify resources periodically
     */
    private suspend fun processQueue() = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting queue processing")

        try {
            // Load or build queue
            var queue = scheduler.loadQueue()
            if (!quotaManager.isOcrFeatureEnabled) {
                Log.d(TAG, "Stopping queue processing: OCR disabled in OCR config (${quotaManager.describeAppliedConfig()})")
                return@withContext
            }
            if (queue.isEmpty() || scheduler.isQueueStale()) {
                queue = scheduler.buildQueue()
                scheduler.saveQueue(queue)
            }

            if (queue.isEmpty()) {
                Log.d(TAG, "Queue is empty - nothing to process")
                noWorkPassWaitingForKey = false
                lastNoWorkPassAtMs = System.currentTimeMillis()
                if (!quotaManager.isBackfillBudgetComplete()) {
                    checkAndTriggerAutoLink()
                }
                return@withContext
            }

            Log.i(TAG, "Processing ${queue.size} items")

            val completedQueueIds = mutableSetOf<String>()
            val affectedBooxNoteDates = mutableSetOf<String>()
            var processedCount = 0

            for (item in queue) {
                // CHECK 1: Abort check FIRST (before any work for this item)
                if (shouldAbort) {
                    Log.d(TAG, "Abort requested, stopping")
                    break
                }

                // CHECK 2: Re-verify resources periodically
                if (!canProcessResources()) {
                    Log.d(TAG, "Safety check failed mid-processing, stopping")
                    break
                }

                // CHECK 3: Re-verify the OCR config flag
                if (!quotaManager.isOcrFeatureEnabled) {
                    Log.d(TAG, "OCR disabled mid-processing, stopping")
                    break
                }

                // CHECK 4: Limit pages per session (from OcrQuotaManager)
                if (processedCount >= maxPagesPerIdleSession) {
                    Log.d(TAG, "Reached max pages per session ($maxPagesPerIdleSession), stopping")
                    break
                }

                // CHECK 5: Skip event types - they're indexed by sync, not idle processor
                // Events should never be in queue, but check explicitly for safety
                if (item.sourceType.isEventSource()) {
                    Log.w(TAG, "Skipping event type ${item.sourceType} (should not be in OCR queue)")
                    completedQueueIds.add(item.stableQueueId())  // Remove exact queue item
                    continue
                }

                // CHECK 6: Skip TASK types - they use Digital Ink Recognition, not Cloud Vision
                // Tasks should never be in queue, but check explicitly for safety (stale queues)
                if (item.sourceType == OcrSource.TASK) {
                    Log.w(TAG, "Skipping TASK (uses Digital Ink Recognition, not Cloud Vision)")
                    completedQueueIds.add(item.stableQueueId())  // Remove exact queue item
                    continue
                }

                // Update UI state
                _processingState.value = ProcessingState(
                    isProcessing = true,
                    currentSource = item.sourceType.name,
                    currentItem = "${item.sourceType.name}/${item.sourceId}",
                    currentPage = processedCount + 1,
                    totalPages = minOf(queue.size, maxPagesPerIdleSession)
                )

                val bitmapSource = scheduler.getBitmapSource(item.sourceType)

                // Process the page
                try {
                    when (pipeline.processPageWithResult(
                        source = bitmapSource,
                        sourceId = item.sourceId,
                        pageId = item.pageId,
                        sourceModified = item.sourceModifiedAt,
                        sourceDate = item.sourceDate ?: if (item.sourceType != OcrSource.BOOX_NOTE) {
                            item.sourceId  // For Memos/Tasks, sourceId is the date
                        } else {
                            null
                        },
                        sourceTitle = item.sourceTitle
                    )) {
                        UnifiedOcrPipeline.ProcessResult.SUCCESS,
                        UnifiedOcrPipeline.ProcessResult.EMPTY -> {
                            completedQueueIds.add(item.stableQueueId())
                            if (item.sourceType == OcrSource.BOOX_NOTE) {
                                item.sourceDate
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(affectedBooxNoteDates::add)
                            }
                            processedCount++
                        }
                        UnifiedOcrPipeline.ProcessResult.FAILED -> {
                            Log.d(TAG, "Leaving ${item.sourceType}/${item.sourceId}/${item.pageId} in queue for retry")
                        }
                        UnifiedOcrPipeline.ProcessResult.QUARANTINED -> {
                            completedQueueIds.add(item.stableQueueId())
                            Log.w(TAG, "Removed quarantined OCR item ${item.sourceType}/${item.sourceId}/${item.pageId} from queue")
                        }
                        UnifiedOcrPipeline.ProcessResult.BITMAP_NOT_READY -> {
                            Log.d(TAG, "Leaving ${item.sourceType}/${item.sourceId}/${item.pageId} in queue - bitmap not ready yet")
                        }
                        UnifiedOcrPipeline.ProcessResult.BUDGET_EXHAUSTED -> {
                            Log.d(TAG, "Stopping queue processing: budget exhausted while processing ${item.sourceType}/${item.sourceId}/${item.pageId}")
                            // Battery: the next pages wait for the same key or budget - rest like an empty pass
                            noWorkPassWaitingForKey = !UserOpenRouterKey.hasKey
                            lastNoWorkPassAtMs = System.currentTimeMillis()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Page processing failed: ${e.message}")
                    // Remove from queue even on exception - prevents infinite retry
                    completedQueueIds.add(item.stableQueueId())
                }

                // MANDATORY: Yield to UI thread between pages
                yield()

                // MANDATORY: Cooldown between pages to let CPU/memory recover on e-ink devices
                // Skip cooldown after last page or if we've hit the limit (from OcrQuotaManager)
                if (processedCount < maxPagesPerIdleSession && !shouldAbort) {
                    Log.d(TAG, "Cooldown ${cooldownBetweenPagesMs}ms before next page")
                    delay(cooldownBetweenPagesMs)
                }
            }

            // Update queue with completed pages
            if (completedQueueIds.isNotEmpty()) {
                scheduler.markCompleted(completedQueueIds)
                Log.i(TAG, "Marked ${completedQueueIds.size} queue items completed")
            }

            // SMART LINK BACKFILL FIX: Check for backfill completion after processing
            // This triggers auto-link when all eligible Boox Note pages have been indexed
            val pendingAutoLinkRan = if (!quotaManager.isBackfillBudgetComplete()) {
                checkAndTriggerAutoLink()
            } else {
                false
            }

            if (affectedBooxNoteDates.isNotEmpty() &&
                !pendingAutoLinkRan
            ) {
                val indexer = booxNoteIndexer
                if (indexer == null) {
                    Log.w(TAG, "Skipping date-scoped Smart Link refresh: booxNoteIndexer not wired yet")
                } else {
                    indexer.refreshSmartLinksForDates(
                        dates = affectedBooxNoteDates,
                        reason = if (quotaManager.isBackfillComplete()) {
                            "OCR updated sampled Boox note pages"
                        } else {
                            "OCR updated sampled Boox note pages while backfill was still in progress"
                        }
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Queue processing failed: ${e.message}", e)
        } finally {
            _processingState.value = null
            Log.i(TAG, "Queue processing ended")
        }
    }

    /**
     * Check if backfill is complete and trigger auto-link if so.
     *
     * SMART LINK BACKFILL FIX: This runs after OCR processing to detect when
     * all Boox Notes have been indexed. When complete, it triggers auto-linking
     * to match notes with calendar events using the newly available semantic data.
     */
    private suspend fun checkAndTriggerAutoLink(): Boolean {
        // ═══════════════════════════════════════════════════════════════════
        // FIX: Do NOT evaluate backfill completion until the Boox Notes
        // index has been populated by at least one successful fullScan().
        //
        // Without this guard, the idle processor can fire before fullScan()
        // finishes on first launch.  getEligibleBooxNotePageEntryIds()
        // returns 0 (empty index), and checkAndMarkBackfillComplete(0,0)
        // interprets that as "nothing to do" → markBackfillComplete() →
        // backfill phase permanently ends before any historical pages are
        // bucketed.  All subsequent Boox Note OCR then charges against
        // the monthly budget instead of the free backfill bucket.
        // ═══════════════════════════════════════════════════════════════════
        if (!GlobalAppSettings.isBooxNotesReady) {
            Log.d(TAG, "Skipping backfill completion check: Boox Notes scan has not completed yet (isBooxNotesReady=false)")
            return false
        }

        if (!quotaManager.isOcrFeatureEnabled) {
            Log.d(TAG, "Skipping backfill auto-link check: OCR disabled in OCR config (${quotaManager.describeAppliedConfig()})")
            return false
        }

        if (!quotaManager.isSmartLinkEnabled) {
            Log.d(TAG, "Skipping backfill auto-link check: Smart Link disabled in OCR config (${quotaManager.describeAppliedConfig()})")
            return false
        }

        val indexer = booxNoteIndexer
        if (indexer == null) {
            Log.w(TAG, "Skipping backfill auto-link check: booxNoteIndexer not wired yet")
            return false
        }

        try {
            // Eligible pages match scheduler rules (lookback window + page cap per note)
            val eligiblePageEntryIds = scheduler.getEligibleBooxNotePageEntryIds()
            val eligiblePageCount = eligiblePageEntryIds.size
            val processedEligiblePageCount = if (eligiblePageEntryIds.isEmpty()) {
                0
            } else {
                ocrRepository.getProcessedPageCountByTypeAndIds(OcrSource.BOOX_NOTE, eligiblePageEntryIds)
            }

            Log.d(TAG, "Backfill check: $processedEligiblePageCount/$eligiblePageCount eligible pages processed")

            // Check if backfill is complete
            val backfillJustCompleted = quotaManager.checkAndMarkBackfillComplete(
                eligiblePageCount = eligiblePageCount,
                processedPageCount = processedEligiblePageCount
            )

            if (backfillJustCompleted) {
                Log.i(TAG, "Backfill just completed! Triggering auto-link...")
                val linkedAny = indexer.runPendingAutoLinkIfNeeded()
                if (linkedAny) {
                    Log.i(TAG, "Auto-link completed after backfill")
                }
                return linkedAny
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/triggering backfill completion: ${e.message}", e)
        }

        return false
    }

    /**
     * Close resources.
     */
    fun close() {
        pipeline.close()
    }
}
