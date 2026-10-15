package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.api.config.OcrQuotaConfig
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ocr.model.OcrSource
import java.time.LocalDate

/**
 * OCR indexing limits and settings for the unified OCR pipeline.
 *
 * Every value comes from the local defaults in [OcrQuotaConfig.DEFAULT]; there is no server config.
 * Page budgets are effectively unlimited: there is no backfill phase, no monthly page budget and
 * no per-source limit, so every new page can be indexed. The one real limit is the monthly cap on
 * AI page reads ([MONTHLY_AI_READ_LIMIT]), which are paid from the user's own OpenRouter credit.
 *
 * The backfill and monthly-budget members are kept for their callers and return the unlimited
 * values. [repairBackfillStateIfCorrupted] and [resetQuotaAfterClear] still maintain the stored
 * counters in OcrSettings.
 */
class OcrQuotaManager(private val context: Context) {

    companion object {
        private const val TAG = "OcrQuotaManager"

        /** Large but sane limits: page budgets are effectively unlimited. */
        private const val UNLIMITED_BUDGET = 10_000
        private const val LOOKBACK_DAYS = 36_500
        private const val PAGES_PER_NOTE = 10_000
        private const val MAX_PAGES_PER_IDLE_SESSION = 25
        private const val COOLDOWN_BETWEEN_PAGES_MS = 1_500L

        /**
         * Most pages AI reads a month on the user's OpenRouter key (Oct 2026): under $1 with Gemini
         * 3.1 Flash Lite (measured $0.0005 a page on real notes, about $0.0008 for a dense page).
         */
        const val MONTHLY_AI_READ_LIMIT = 1_000
    }

    private val kvProxy by lazy { KvProxy(context) }

    // ═══════════════════════════════════════════════════════════════════════════
    // OCR Config (local defaults)
    // ═══════════════════════════════════════════════════════════════════════════

    /** The OCR config in use: always the local defaults. */
    val config: OcrQuotaConfig
        get() = OcrQuotaConfig.DEFAULT

    /** Stable hash of [config], stored with Smart Link aggregates and the last applied config. */
    val configHash: String
        get() = config.stableHash()

    /** Backfill limit (effectively unlimited) */
    val backfillLimit: Int get() = UNLIMITED_BUDGET

    /** Monthly budget (effectively unlimited) */
    val monthlyBudget: Int get() = UNLIMITED_BUDGET

    /** OCR indexing is always on (the user still turns it on or off in settings). */
    val isOcrFeatureEnabled: Boolean get() = true

    /** Semantic extraction is always on. */
    val isSemanticExtractionEnabled: Boolean get() = true

    /** Smart Link is always on. */
    val isSmartLinkEnabled: Boolean get() = true

    // ───────────────────────────────────────────────────────────────────────────
    // Processing Parameters
    // ───────────────────────────────────────────────────────────────────────────

    /** Max pages per idle processing batch (e-ink device protection) */
    val maxPagesPerIdleSession: Int get() = MAX_PAGES_PER_IDLE_SESSION

    /** Delay between pages in milliseconds (e-ink device protection) */
    val cooldownBetweenPagesMs: Long get() = COOLDOWN_BETWEEN_PAGES_MS

    /** Milliseconds of inactivity before processing starts */
    val idleThresholdMs: Long get() = config.idleThresholdMs

    // ───────────────────────────────────────────────────────────────────────────
    // Content Selection
    // ───────────────────────────────────────────────────────────────────────────

    /** How many days back to look for eligible content */
    val dateLookbackDays: Int get() = LOOKBACK_DAYS

    /** Max pages to index per Boox Note (only first N pages are processed) */
    val booxNotesPagesPerNote: Int get() = PAGES_PER_NOTE

    // ───────────────────────────────────────────────────────────────────────────
    // Device Protection
    // ───────────────────────────────────────────────────────────────────────────

    /** Minimum free memory (MB) required before OCR processing starts */
    val minFreeMemoryMb: Int get() = config.minFreeMemoryMb

    /** Minimum battery percentage required (unless device is charging) */
    val minBatteryPercent: Int get() = config.minBatteryPercent

    /** Grace period (ms) for recently modified pages before marking as empty */
    val bitmapGracePeriodMs: Long get() = config.bitmapGracePeriodMs

    // ───────────────────────────────────────────────────────────────────────────
    // Priority Scoring (higher = processed first)
    // ───────────────────────────────────────────────────────────────────────────

    /** Priority score for content modified today */
    val priorityToday: Int get() = config.priorityToday

    /** Priority score for content modified within last 7 days */
    val priorityThisWeek: Int get() = config.priorityThisWeek

    /** Priority score for content modified within last 30 days */
    val priorityThisMonth: Int get() = config.priorityThisMonth

    /** Priority score for content older than 30 days */
    val priorityOlder: Int get() = config.priorityOlder

    // ───────────────────────────────────────────────────────────────────────────
    // Queue Management
    // ───────────────────────────────────────────────────────────────────────────

    /** How long (ms) before the processing queue is considered stale and rebuilt */
    val queueStaleThresholdMs: Long get() = config.queueStaleThresholdMs

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase Detection
    // ═══════════════════════════════════════════════════════════════════════════

    /** There is no backfill budget phase: always false. */
    fun isBackfillPhase(): Boolean = false

    /** Backfill gating is always complete (Boox Notes menu unlocked). */
    fun isBackfillComplete(): Boolean = true

    /** The backfill budget is always complete. */
    fun isBackfillBudgetComplete(): Boolean = true

    // ═══════════════════════════════════════════════════════════════════════════
    // Source Type Checks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a source type consumes OCR quota.
     * Events are exempt - they don't need an AI page read (text-only indexing).
     *
     * @param sourceType The source type to check
     * @return true if this source type consumes quota, false if exempt
     */
    fun consumesQuota(sourceType: OcrSource): Boolean {
        return when (sourceType) {
            OcrSource.BOOX_NOTE -> true   // AI page read
            OcrSource.MEMO -> true        // AI page read
            OcrSource.TASK -> true        // AI page read
            OcrSource.CALENDAR_EVENT -> false  // Text-only, no API call
            OcrSource.LOCAL_EVENT -> false     // Text-only, no API call
            OcrSource.TEXT_ANNOTATION -> false // Text-only, no API call
        }
    }

    /**
     * Record that an event was indexed (text-only, no quota consumed).
     * This is for tracking/logging purposes - event stats are computed from the database.
     *
     * @param sourceType Must be CALENDAR_EVENT or LOCAL_EVENT
     */
    fun recordEventIndexed(sourceType: OcrSource) {
        require(sourceType.isEventSource()) {
            "recordEventIndexed only for event types, got: $sourceType"
        }

        when (sourceType) {
            OcrSource.CALENDAR_EVENT -> {
                Log.d(TAG, "Calendar event indexed (text-only, no quota consumed)")
            }
            OcrSource.LOCAL_EVENT -> {
                Log.d(TAG, "Local event indexed (text-only, no quota consumed)")
            }
            OcrSource.BOOX_NOTE, OcrSource.MEMO, OcrSource.TASK, OcrSource.TEXT_ANNOTATION -> {
                // Unreachable due to require check above
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Quota Queries (page budgets are effectively unlimited)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Pages left in the backfill budget (effectively unlimited). */
    fun getRemainingBackfill(): Int = UNLIMITED_BUDGET

    /** Pages counted against the backfill budget: always 0. */
    fun getBackfillPagesIndexed(): Int = 0

    /** Pages left in the monthly budget (effectively unlimited). */
    fun getRemainingMonthlyBudget(): Int = UNLIMITED_BUDGET

    /** Pages counted against the monthly budget: always 0. */
    fun getMonthlyPagesUsed(): Int = 0

    /** Monthly pages counted per source: always 0. */
    fun getMonthlyNotesUsed(): Int = 0

    fun getMonthlyMemosUsed(): Int = 0

    fun getMonthlyTasksUsed(): Int = 0

    // ═══════════════════════════════════════════════════════════════════════════
    // Budget Checks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a NEW page can be indexed: always true, there is no page budget.
     * The monthly AI read limit is checked separately ([canReadWithAi]).
     *
     * @param sourceType The source type of the page being indexed
     */
    @Suppress("UNUSED_PARAMETER")
    fun canIndexNewPage(sourceType: OcrSource): Boolean = true

    /** Pages left in the current budget (effectively unlimited). */
    fun getRemainingBudget(): Int = UNLIMITED_BUDGET

    fun describeAppliedConfig(): String {
        return "source=LOCAL_DEFAULT unlimited=true pagesPerNote=$booxNotesPagesPerNote lookbackDays=$dateLookbackDays idleBatch=$maxPagesPerIdleSession"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Budget Recording
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Record that a NEW page was indexed. There is no page budget, so nothing is counted;
     * this only logs.
     *
     * @param sourceType The source type of the page being indexed
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun recordNewPageIndexed(sourceType: OcrSource) {
        Log.d(TAG, "Indexed ${sourceType.name} page without budget accounting")
    }

    /**
     * Record that an existing page was re-indexed (FREE, no budget consumed).
     *
     * Call this for pages that were previously indexed and are being updated
     * due to source modification. This is a no-op but useful for tracking/logging.
     */
    fun recordReIndexed() {
        // Re-indexing is free - no budget consumed
        Log.d(TAG, "Re-indexed existing page (free)")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AI page reads on the user's own OpenRouter key (Oct 2026)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Most pages AI reads a calendar month; the reads are paid from the user's OpenRouter credit. */
    val monthlyAiReadLimit: Int get() = MONTHLY_AI_READ_LIMIT

    /** Pages read by AI this calendar month (new pages and re-reads of edited pages). */
    fun getAiReadsThisMonth(): Int {
        val ocrSettings = GlobalAppSettings.current.ocrSettings
        return if (ocrSettings.aiReadsMonth == currentMonthKey()) ocrSettings.aiReadsThisMonth else 0
    }

    fun canReadWithAi(): Boolean = getAiReadsThisMonth() < MONTHLY_AI_READ_LIMIT

    /** Counts one page read by AI (call after OpenRouter answered). */
    fun recordAiRead() {
        val settings = GlobalAppSettings.current
        val used = getAiReadsThisMonth() + 1
        kvProxy.setAppSettings(
            settings.copy(
                ocrSettings = settings.ocrSettings.copy(aiReadsThisMonth = used, aiReadsMonth = currentMonthKey())
            )
        )
        Log.d(TAG, "AI page reads this month: $used/$MONTHLY_AI_READ_LIMIT")
    }

    private fun currentMonthKey(): String = LocalDate.now().toString().take(7)

    // ═══════════════════════════════════════════════════════════════════════════
    // Stored Counters
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Reset all quota counters after user clears the OCR index.
     *
     * Called when the user presses "Clear Index": restarts the stored backfill state
     * and zeroes the stored monthly counters.
     */
    fun resetQuotaAfterClear() {
        val settings = GlobalAppSettings.current

        val updatedSettings = settings.copy(
            ocrSettings = settings.ocrSettings.copy(
                // Reset backfill to allow re-indexing historical Boox Notes
                backfillCompleted = false,
                backfillCompletedAt = 0L,
                backfillPagesIndexed = 0,
                backfillBudgetCompleted = false,
                // Reset monthly counters (pages were deleted, shouldn't count against budget)
                monthlyPagesUsed = 0,
                monthlyNotesUsed = 0,
                monthlyMemosUsed = 0,
                monthlyTasksUsed = 0
                // Note: Keep billingAnniversaryDay and monthlyBudgetResetDate unchanged
            )
        )

        kvProxy.setAppSettings(updatedSettings)

        Log.i(TAG, "Quota reset after Clear Index - backfill phase restarted, monthly budget reset")
    }

    /**
     * Detect and repair corrupted backfill state caused by an old premature-
     * completion race condition.
     *
     * Corruption signature:
     *   backfillBudgetCompleted == true  (backfill ended)
     *   backfillPagesIndexed   == 0     (but nothing was ever counted toward it)
     *   monthlyNotesUsed       >  0     (Boox Note pages charged to monthly instead)
     *
     * Repair action:
     *   Move monthlyNotesUsed into backfillPagesIndexed, deduct from monthly
     *   totals, and reopen backfill if under the limit.
     *
     * Safe to call multiple times — it is a no-op once the state is clean.
     * Should be called once early in app startup.
     */
    suspend fun repairBackfillStateIfCorrupted() {
        val settings = GlobalAppSettings.current
        val ocr = settings.ocrSettings

        val backfillEnded = ocr.backfillBudgetCompleted || ocr.backfillCompleted
        val nothingCounted = ocr.backfillPagesIndexed == 0
        val notesChargedToMonthly = ocr.monthlyNotesUsed > 0

        if (!(backfillEnded && nothingCounted && notesChargedToMonthly)) {
            return  // State is not corrupted — nothing to do
        }

        // How many Boox Note pages were mis-attributed to monthly
        val misattributed = ocr.monthlyNotesUsed
        val movedToBackfill = minOf(misattributed, backfillLimit)
        val backfillNowComplete = movedToBackfill >= backfillLimit

        val repairedOcr = ocr.copy(
            // Move mis-attributed pages into backfill bucket
            backfillPagesIndexed = movedToBackfill,
            // Reopen backfill if we haven't reached the limit yet
            backfillCompleted = backfillNowComplete,
            backfillBudgetCompleted = backfillNowComplete,
            backfillCompletedAt = if (backfillNowComplete) {
                ocr.backfillCompletedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            } else {
                0L
            },
            // Deduct the moved pages from monthly counters
            monthlyNotesUsed = maxOf(0, ocr.monthlyNotesUsed - movedToBackfill),
            monthlyPagesUsed = maxOf(0, ocr.monthlyPagesUsed - movedToBackfill)
        )

        kvProxy.setAppSettings(settings.copy(ocrSettings = repairedOcr))

        Log.w(TAG, "REPAIRED corrupted backfill state: moved $movedToBackfill Boox Note pages " +
            "from monthly bucket into backfill ($movedToBackfill/$backfillLimit). " +
            "Backfill phase ${if (backfillNowComplete) "now complete" else "reopened"}. " +
            "Monthly notes: ${ocr.monthlyNotesUsed} → ${repairedOcr.monthlyNotesUsed}, " +
            "Monthly total: ${ocr.monthlyPagesUsed} → ${repairedOcr.monthlyPagesUsed}")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART LINK BACKFILL FIX - Content-aware completion detection
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Backfill gating is always complete ([isBackfillComplete]), so it can never be
     * "just completed": always false, and nothing is written.
     *
     * @return true if backfill gating was just completed (triggers auto-link); always false
     */
    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun checkAndMarkBackfillComplete(eligiblePageCount: Int, processedPageCount: Int): Boolean = false
}
