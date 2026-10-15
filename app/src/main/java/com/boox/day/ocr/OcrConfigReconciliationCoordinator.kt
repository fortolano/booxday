package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.api.config.OcrQuotaConfig
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Applies OCR config changes to derived runtime state.
 *
 * The OCR config is always the local default ([OcrQuotaManager.config]). This coordinator
 * compares it with the last applied config (stored in SharedPreferences) and, when they differ
 * (for example after an app update changes the defaults, or on a device that last applied an
 * older config), does the follow-up work the rest of Smart Link depends on:
 * queue reset, aggregate invalidation, affected-date relinking, and backfill gate re-checks.
 */
class OcrConfigReconciliationCoordinator(context: Context) {

    companion object {
        private const val TAG = "OcrConfigReconcile"
        private const val PREFS_NAME = "ocr_config_reconcile_prefs"
        private const val KEY_LAST_APPLIED_HASH = "last_applied_hash"
        private const val KEY_LAST_APPLIED_JSON = "last_applied_json"

        private val reconcileMutex = Mutex()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val quotaManager by lazy { OcrQuotaManager(appContext) }
    private val scheduler by lazy { UnifiedOcrScheduler(appContext) }
    private val noteRepository by lazy { BooxNoteRepository(appContext) }
    private val ocrRepository by lazy { UnifiedOcrRepository(appContext) }
    private val eventRepository by lazy { CalendarEventRepository(appContext) }
    private val booxIntegration by lazy { BooxNotesIntegration(appContext) }

    private val booxNoteIndexer by lazy {
        BooxNoteIndexer(
            context = appContext,
            booxIntegration = booxIntegration,
            noteRepository = noteRepository,
            eventRepository = eventRepository
        )
    }

    suspend fun reconcileIfNeeded(trigger: String) {
        reconcileMutex.withLock {
            val currentConfig = quotaManager.config
            val currentHash = quotaManager.configHash
            val previousHash = prefs.getString(KEY_LAST_APPLIED_HASH, null)
            val previousConfig = loadLastAppliedConfig()

            if (previousHash.isNullOrBlank() || previousConfig == null) {
                persistAppliedConfig(currentConfig, currentHash)
                Log.d(
                    TAG,
                    "Recorded initial OCR config baseline from $trigger " +
                        "(local defaults, hash=$currentHash)"
                )
                return
            }

            if (previousHash == currentHash) {
                return
            }

            val changedFields = describeChangedFields(previousConfig, currentConfig)
            Log.i(
                TAG,
                "Reconciling OCR config change from $trigger: ${changedFields.joinToString()} " +
                    "($previousHash -> $currentHash)"
            )

            if (requiresQueueReset(previousConfig, currentConfig)) {
                scheduler.clearQueue()
                Log.d(TAG, "Cleared OCR queue because queue-affecting OCR config changed")
            }

            val affectedNotes = if (needsBooxNoteScope(previousConfig, currentConfig)) {
                collectAffectedBooxNotes(previousConfig, currentConfig)
            } else {
                emptyList()
            }

            if (affectsSampleSelection(previousConfig, currentConfig) && affectedNotes.isNotEmpty()) {
                noteRepository.deleteSemanticAggregates(affectedNotes.map { it.uniqueId })
                Log.d(
                    TAG,
                    "Deleted ${affectedNotes.size} Boox note semantic aggregates because sampling rules changed"
                )
            }

            val affectedDates = affectedNotes
                .mapNotNull { note -> note.originalDate.takeIf { it.isNotBlank() } }
                .toSet()

            if (shouldRefreshSmartLinks(previousConfig, currentConfig) && affectedDates.isNotEmpty()) {
                booxNoteIndexer.refreshSmartLinksForDates(
                    dates = affectedDates,
                    reason = "OCR config changed: ${changedFields.joinToString()}"
                )
            }

            if (shouldRecheckBackfill(previousConfig, currentConfig)) {
                recheckBackfillGate()
            }

            noteRepository.invalidateLinksCache()
            persistAppliedConfig(currentConfig, currentHash)
        }
    }

    private fun loadLastAppliedConfig(): OcrQuotaConfig? {
        val jsonString = prefs.getString(KEY_LAST_APPLIED_JSON, null) ?: return null
        return try {
            json.decodeFromString<OcrQuotaConfig>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode last applied OCR config: ${e.message}")
            null
        }
    }

    private fun persistAppliedConfig(config: OcrQuotaConfig, hash: String) {
        prefs.edit()
            .putString(KEY_LAST_APPLIED_HASH, hash)
            .putString(KEY_LAST_APPLIED_JSON, json.encodeToString(config))
            .apply()
    }

    private fun requiresQueueReset(previous: OcrQuotaConfig, current: OcrQuotaConfig): Boolean {
        return previous.ocrEnabled != current.ocrEnabled ||
            previous.backfillLimit != current.backfillLimit ||
            previous.monthlyBudget != current.monthlyBudget ||
            previous.monthlyBudgetBySource != current.monthlyBudgetBySource ||
            previous.dateLookbackDays != current.dateLookbackDays ||
            previous.booxNotesPagesPerNote != current.booxNotesPagesPerNote ||
            previous.priorityToday != current.priorityToday ||
            previous.priorityThisWeek != current.priorityThisWeek ||
            previous.priorityThisMonth != current.priorityThisMonth ||
            previous.priorityOlder != current.priorityOlder ||
            previous.queueStaleThresholdMs != current.queueStaleThresholdMs
    }

    private fun affectsSampleSelection(previous: OcrQuotaConfig, current: OcrQuotaConfig): Boolean {
        return previous.dateLookbackDays != current.dateLookbackDays ||
            previous.booxNotesPagesPerNote != current.booxNotesPagesPerNote
    }

    private fun needsBooxNoteScope(previous: OcrQuotaConfig, current: OcrQuotaConfig): Boolean {
        return affectsSampleSelection(previous, current) ||
            previous.smartLinkEnabled != current.smartLinkEnabled ||
            previous.semanticExtractionEnabled != current.semanticExtractionEnabled ||
            previous.ocrEnabled != current.ocrEnabled
    }

    private fun shouldRefreshSmartLinks(previous: OcrQuotaConfig, current: OcrQuotaConfig): Boolean {
        if (!current.smartLinkEnabled || !current.ocrEnabled) return false
        return affectsSampleSelection(previous, current) ||
            previous.semanticExtractionEnabled != current.semanticExtractionEnabled ||
            (!previous.smartLinkEnabled && current.smartLinkEnabled) ||
            (!previous.ocrEnabled && current.ocrEnabled)
    }

    private fun shouldRecheckBackfill(previous: OcrQuotaConfig, current: OcrQuotaConfig): Boolean {
        if (!current.ocrEnabled) return false
        return previous.backfillLimit != current.backfillLimit ||
            previous.backfillCompletionThreshold != current.backfillCompletionThreshold ||
            affectsSampleSelection(previous, current) ||
            (!previous.ocrEnabled && current.ocrEnabled)
    }

    private suspend fun collectAffectedBooxNotes(
        previous: OcrQuotaConfig,
        current: OcrQuotaConfig
    ): List<BooxNoteIndex> {
        val maxLookbackDays = maxOf(previous.dateLookbackDays, current.dateLookbackDays)
        if (maxLookbackDays <= 0) {
            return noteRepository.getAllNotes()
        }

        val cutoffTime = LocalDate.now()
            .minusDays(maxLookbackDays.toLong())
            .atStartOfDay()
            .toEpochSecond(ZoneOffset.UTC) * 1000

        return noteRepository.getAllNotes()
            .filter { note -> note.shapeFileModified >= cutoffTime }
    }

    private suspend fun recheckBackfillGate() {
        val eligiblePageEntryIds = scheduler.getEligibleBooxNotePageEntryIds()
        val eligiblePageCount = eligiblePageEntryIds.size
        val processedEligiblePageCount = if (eligiblePageEntryIds.isEmpty()) {
            0
        } else {
            ocrRepository.getProcessedPageCountByTypeAndIds(OcrSource.BOOX_NOTE, eligiblePageEntryIds)
        }

        val backfillJustCompleted = quotaManager.checkAndMarkBackfillComplete(
            eligiblePageCount = eligiblePageCount,
            processedPageCount = processedEligiblePageCount
        )

        if (backfillJustCompleted && quotaManager.isSmartLinkEnabled) {
            booxNoteIndexer.runPendingAutoLinkIfNeeded()
        }
    }

    private fun describeChangedFields(previous: OcrQuotaConfig, current: OcrQuotaConfig): List<String> {
        val changes = mutableListOf<String>()
        if (previous.ocrEnabled != current.ocrEnabled) changes += "ocrEnabled"
        if (previous.semanticExtractionEnabled != current.semanticExtractionEnabled) changes += "semanticExtractionEnabled"
        if (previous.smartLinkEnabled != current.smartLinkEnabled) changes += "smartLinkEnabled"
        if (previous.backfillLimit != current.backfillLimit) changes += "backfillLimit"
        if (previous.backfillCompletionThreshold != current.backfillCompletionThreshold) changes += "backfillCompletionThreshold"
        if (previous.monthlyBudget != current.monthlyBudget) changes += "monthlyBudget"
        if (previous.monthlyBudgetBySource != current.monthlyBudgetBySource) changes += "monthlyBudgetBySource"
        if (previous.dateLookbackDays != current.dateLookbackDays) changes += "dateLookbackDays"
        if (previous.booxNotesPagesPerNote != current.booxNotesPagesPerNote) changes += "booxNotesPagesPerNote"
        if (previous.priorityToday != current.priorityToday) changes += "priorityToday"
        if (previous.priorityThisWeek != current.priorityThisWeek) changes += "priorityThisWeek"
        if (previous.priorityThisMonth != current.priorityThisMonth) changes += "priorityThisMonth"
        if (previous.priorityOlder != current.priorityOlder) changes += "priorityOlder"
        if (previous.queueStaleThresholdMs != current.queueStaleThresholdMs) changes += "queueStaleThresholdMs"
        if (changes.isEmpty()) changes += "hashOnly"
        return changes
    }
}
