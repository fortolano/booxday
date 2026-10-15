package com.boox.day.api.config

import com.boox.day.ocr.model.OcrSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Local OCR configuration: indexing limits, feature flags and processing parameters.
 *
 * The app always uses [DEFAULT]; there is no server config. OcrQuotaManager reads these values
 * and overrides the page budgets and content-selection limits with its own unlimited values.
 *
 * The JSON form is stored by OcrConfigReconciliationCoordinator as the last applied config, and
 * [stableHash] is stored with Boox note Smart Link aggregates. Adding, removing or renaming a field
 * changes the hash, which makes the coordinator reconcile once and rebuilds those aggregates.
 */
@Serializable
data class OcrQuotaConfig(
    /** Config version, part of the stored JSON and the hash */
    val configVersion: Int = 1,

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKFILL QUOTAS (One-time historical indexing)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Max pages for initial Boox Notes indexing (one-time budget) */
    val backfillLimit: Int = 100,

    /** Percentage of notes indexed to consider backfill complete (0.0 - 1.0) */
    val backfillCompletionThreshold: Float = 0.9f,

    // ═══════════════════════════════════════════════════════════════════════════
    // MONTHLY QUOTAS (Recurring budget)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Max NEW pages per month (OcrQuotaManager uses an unlimited value instead) */
    val monthlyBudget: Int = 150,

    /** Optional per-source limits within monthly budget (null = no sub-limit) */
    val monthlyBudgetBySource: SourceBudgets? = null,

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE FLAGS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Master switch for OCR feature */
    val ocrEnabled: Boolean = true,

    /** Run 12-step semantic extraction */
    val semanticExtractionEnabled: Boolean = true,

    /** Enable Smart Link features (note-to-event matching) */
    val smartLinkEnabled: Boolean = true,

    // ═══════════════════════════════════════════════════════════════════════════
    // PROCESSING CONTROLS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Pages per idle processing batch (e-ink device protection) */
    val maxPagesPerIdleSession: Int = 2,

    /** Delay between pages in milliseconds (e-ink device protection) */
    val cooldownBetweenPagesMs: Long = 7000,

    /** Milliseconds of inactivity before processing starts */
    val idleThresholdMs: Long = 5000,

    // ═══════════════════════════════════════════════════════════════════════════
    // RE-INDEXING RULES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Modified pages don't consume budget (should always be true) */
    val reindexFree: Boolean = true,

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT SELECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /** How many days back to look for eligible content (notes within this window are candidates) */
    val dateLookbackDays: Int = 120,

    /** Max pages to index per Boox Note (only first N pages are processed) */
    val booxNotesPagesPerNote: Int = 2,

    // ═══════════════════════════════════════════════════════════════════════════
    // DEVICE PROTECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minimum free memory (MB) required before OCR processing starts */
    val minFreeMemoryMb: Int = 100,

    /** Minimum battery percentage required (unless device is charging) */
    val minBatteryPercent: Int = 15,

    /** Grace period (ms) for recently modified pages before marking as empty */
    val bitmapGracePeriodMs: Long = 30000,

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIORITY SCORING (higher = processed first)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Priority score for content modified today */
    val priorityToday: Int = 100,

    /** Priority score for content modified within last 7 days */
    val priorityThisWeek: Int = 80,

    /** Priority score for content modified within last 30 days */
    val priorityThisMonth: Int = 60,

    /** Priority score for content older than 30 days */
    val priorityOlder: Int = 40,

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEUE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** How long (ms) before the processing queue is considered stale and rebuilt */
    val queueStaleThresholdMs: Long = 3600000
) {
    fun stableHash(): String {
        val normalized = HASH_JSON.encodeToString(serializer(), this)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun monthlySourceBudget(sourceType: OcrSource): Int? {
        return when (sourceType) {
            OcrSource.BOOX_NOTE -> monthlyBudgetBySource?.booxNotes
            OcrSource.MEMO -> monthlyBudgetBySource?.memos
            OcrSource.TASK -> monthlyBudgetBySource?.tasks
            OcrSource.CALENDAR_EVENT, OcrSource.LOCAL_EVENT, OcrSource.TEXT_ANNOTATION -> null
        }
    }

    companion object {
        /** The configuration the app uses */
        val DEFAULT = OcrQuotaConfig()

        private val HASH_JSON = Json { encodeDefaults = true }
    }
}

/**
 * Per-source budget limits within the monthly budget.
 * All values are optional - null means no sub-limit (uses shared pool).
 */
@Serializable
data class SourceBudgets(
    val booxNotes: Int? = null,
    val memos: Int? = null,
    val tasks: Int? = null
)
