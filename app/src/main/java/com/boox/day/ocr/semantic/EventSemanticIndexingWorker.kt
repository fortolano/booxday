package com.boox.day.ocr.semantic

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.ocr.OcrQuotaManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for batch event semantic indexing.
 *
 * This worker processes calendar events for semantic classification after:
 * - Calendar sync completes with changes
 * - Multiple local events are created/updated
 *
 * The worker is idempotent - re-indexing an already-indexed event is safe.
 *
 * ## Input Data
 * - EVENT_IDS: Array of event IDs to index (required)
 * - IS_CALENDAR_EVENT: Whether these are CalendarEvents (true) or LocalEvents (false)
 *
 * ## Result
 * - Returns Result.success() when all events are processed
 * - Returns Result.retry() on transient errors (network, etc.)
 * - Returns Result.failure() only on permanent errors
 */
class EventSemanticIndexingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EventSemanticIndexer"

        // Work request name for coalescing
        const val WORK_NAME = "event_semantic_indexing"

        // Input data keys
        const val KEY_EVENT_IDS = "event_ids"
        const val KEY_IS_CALENDAR_EVENT = "is_calendar_event"

        // Output data keys
        const val KEY_INDEXED_COUNT = "indexed_count"
        const val KEY_SKIPPED_COUNT = "skipped_count"
        const val KEY_ERROR_COUNT = "error_count"

        // WorkManager input data limit is 10KB. UUIDs are ~36 chars each, but
        // serialization adds significant overhead (JSON structure, escaping).
        // 100 events * ~50 bytes overhead = ~5000 bytes, safely under 10KB limit.
        private const val MAX_EVENTS_PER_BATCH = 100

        /**
         * Enqueue batch event indexing.
         *
         * Uses APPEND_OR_REPLACE policy to coalesce multiple sync operations.
         * Large batches are automatically chunked to stay under WorkManager's 10KB data limit.
         *
         * @param context Application context
         * @param eventIds List of event IDs to index
         * @param isCalendarEvent True for CalendarEvent, false for LocalEvent
         */
        fun enqueue(
            context: Context,
            eventIds: List<String>,
            isCalendarEvent: Boolean = true
        ) {
            if (eventIds.isEmpty()) {
                Log.d(TAG, "No events to index, skipping worker enqueue")
                return
            }

            val quotaManager = OcrQuotaManager(context.applicationContext)
            if (!quotaManager.isSemanticExtractionEnabled) {
                Log.i(TAG, "Skipping event semantic worker enqueue because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")
                return
            }

            // Chunk large batches to stay under WorkManager's 10KB data limit
            val chunks = eventIds.chunked(MAX_EVENTS_PER_BATCH)

            if (chunks.size > 1) {
                Log.i(TAG, "Splitting ${eventIds.size} events into ${chunks.size} batches of max $MAX_EVENTS_PER_BATCH")
            }

            val workManager = WorkManager.getInstance(context)

            chunks.forEachIndexed { index, chunk ->
                val inputData = Data.Builder()
                    .putStringArray(KEY_EVENT_IDS, chunk.toTypedArray())
                    .putBoolean(KEY_IS_CALENDAR_EVENT, isCalendarEvent)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<EventSemanticIndexingWorker>()
                    .setInputData(inputData)
                    .addTag("semantic_indexing")
                    .build()

                // Use unique work name per chunk to allow parallel processing
                val workName = if (chunks.size > 1) "${WORK_NAME}_batch_$index" else WORK_NAME

                workManager.enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )
            }

            Log.i(TAG, "Enqueued indexing for ${eventIds.size} events in ${chunks.size} batch(es) (calendar=$isCalendarEvent)")
        }

        /**
         * Enqueue indexing for synced calendar events after sync completes.
         *
         * @param context Application context
         * @param accountId The account that was synced (for logging)
         * @param changedEventIds IDs of events that changed during sync
         */
        fun enqueueForSync(
            context: Context,
            accountId: String,
            changedEventIds: List<String>
        ) {
            if (changedEventIds.isEmpty()) {
                Log.d(TAG, "No changed events from sync for account $accountId")
                return
            }

            Log.i(TAG, "Enqueueing indexing for ${changedEventIds.size} events from account $accountId")
            enqueue(context, changedEventIds, isCalendarEvent = true)
        }
    }

    // Lazy-initialized repositories
    private val calendarEventRepository by lazy { CalendarEventRepository(applicationContext) }
    private val localEventRepository by lazy { LocalEventRepository(applicationContext) }
    private val semanticGeniusService by lazy { SemanticGeniusService(applicationContext) }
    private val quotaManager by lazy { OcrQuotaManager(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!quotaManager.isSemanticExtractionEnabled) {
            Log.i(TAG, "Skipping event semantic worker because semantic extraction is disabled (${quotaManager.describeAppliedConfig()})")
            return@withContext Result.success()
        }

        val eventIds = inputData.getStringArray(KEY_EVENT_IDS) ?: emptyArray()
        val isCalendarEvent = inputData.getBoolean(KEY_IS_CALENDAR_EVENT, true)

        if (eventIds.isEmpty()) {
            Log.d(TAG, "No event IDs provided, completing with success")
            return@withContext Result.success()
        }

        Log.i(TAG, "Starting indexing for ${eventIds.size} events (calendar=$isCalendarEvent)")
        val startTime = System.currentTimeMillis()

        var indexedCount = 0
        var skippedCount = 0
        var errorCount = 0

        for (eventId in eventIds) {
            try {
                val indexed = if (isCalendarEvent) {
                    indexCalendarEvent(eventId)
                } else {
                    indexLocalEvent(eventId)
                }

                if (indexed) {
                    indexedCount++
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error indexing event $eventId", e)
                errorCount++
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Indexing complete: $indexedCount indexed, $skippedCount skipped, $errorCount errors (${duration}ms)")

        // Build output data
        val outputData = Data.Builder()
            .putInt(KEY_INDEXED_COUNT, indexedCount)
            .putInt(KEY_SKIPPED_COUNT, skippedCount)
            .putInt(KEY_ERROR_COUNT, errorCount)
            .build()

        // If we had errors but also some successes, still report success
        // The errors are logged and can be retried later
        if (errorCount > 0 && indexedCount == 0 && skippedCount == 0) {
            // All failed - worth retrying
            Log.w(TAG, "All events failed, requesting retry")
            return@withContext Result.retry()
        }

        Result.success(outputData)
    }

    /**
     * Index a CalendarEvent by ID.
     * @return true if indexed, false if skipped (not found or already indexed)
     */
    private suspend fun indexCalendarEvent(eventId: String): Boolean {
        val event = calendarEventRepository.getEventById(eventId)
        if (event == null) {
            Log.d(TAG, "CalendarEvent $eventId not found, skipping")
            return false
        }

        // Use getOrCreateEventProfile for idempotent indexing
        semanticGeniusService.getOrCreateEventProfile(event)
        Log.d(TAG, "Indexed CalendarEvent: ${event.title}")
        return true
    }

    /**
     * Index a LocalEvent by ID.
     * @return true if indexed, false if skipped (not found or already indexed)
     */
    private suspend fun indexLocalEvent(eventId: String): Boolean {
        val event = localEventRepository.getEventById(eventId)
        if (event == null) {
            Log.d(TAG, "LocalEvent $eventId not found, skipping")
            return false
        }

        // Use getOrCreateEventProfile for idempotent indexing
        semanticGeniusService.getOrCreateEventProfile(event)
        Log.d(TAG, "Indexed LocalEvent: ${event.title}")
        return true
    }
}
