package com.boox.day.sync

import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.google.api.services.calendar.model.Event
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ConflictResolver - Handles sync conflicts between local and remote events.
 *
 * A conflict occurs when:
 * 1. Local event has PENDING_UPDATE or PENDING_DELETE status
 * 2. Remote event has been modified since our last sync (ETag changed or updated timestamp newer)
 *
 * Resolution strategies:
 * - KEEP_LOCAL: Push local changes to Google, overwrite remote
 * - KEEP_REMOTE: Pull remote changes, discard local modifications
 * - KEEP_BOTH: Create a copy of the local version, update original with remote
 * - AUTO_MERGE: If changes don't overlap, merge automatically
 */
class ConflictResolver(
    private val localEventRepository: LocalEventRepository
) {
    companion object {
        private const val TAG = "ConflictResolver"
    }

    /**
     * Detect conflict between local event and remote event.
     *
     * @param localEvent The local event with pending changes
     * @param remoteEvent The Google Calendar event fetched from server
     * @return ConflictInfo if conflict detected, null if no conflict
     */
    fun detectConflict(localEvent: LocalEvent, remoteEvent: Event): ConflictInfo? {
        // Use the helper from EventConverters
        if (!localEvent.hasConflictWith(remoteEvent)) {
            return null
        }

        // Analyze what changed on each side
        val localChanges = localEvent.getLocallyChangedFields()
        val remoteChanges = localEvent.getRemotelyChangedFields(remoteEvent)
        val overlappingFields = localChanges.intersect(remoteChanges)

        return ConflictInfo(
            localEvent = localEvent,
            remoteEvent = remoteEvent,
            localChangedFields = localChanges,
            remoteChangedFields = remoteChanges,
            overlappingFields = overlappingFields,
            canAutoMerge = overlappingFields.isEmpty()
        )
    }

    /**
     * Resolve a conflict using the specified strategy.
     *
     * @param conflict The conflict to resolve
     * @param strategy How to resolve the conflict
     * @param calendarInfo Calendar info for merging remote data
     * @return Resolution result
     */
    suspend fun resolveConflict(
        conflict: ConflictInfo,
        strategy: ConflictResolutionStrategy,
        calendarInfo: GoogleCalendarInfo
    ): ConflictResolutionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "RESOLVE: ${conflict.localEvent.id} with strategy $strategy")

        when (strategy) {
            ConflictResolutionStrategy.KEEP_LOCAL -> keepLocal(conflict)
            ConflictResolutionStrategy.KEEP_REMOTE -> keepRemote(conflict, calendarInfo)
            ConflictResolutionStrategy.KEEP_BOTH -> keepBoth(conflict, calendarInfo)
            ConflictResolutionStrategy.AUTO_MERGE -> autoMerge(conflict, calendarInfo)
        }
    }

    /**
     * KEEP_LOCAL: Keep local changes, mark for push to Google.
     * The local version will overwrite the remote when synced.
     */
    private suspend fun keepLocal(conflict: ConflictInfo): ConflictResolutionResult {
        val localEvent = conflict.localEvent

        // Keep the local status (PENDING_UPDATE or PENDING_DELETE)
        // It will be pushed to Google on next sync
        Log.i(TAG, "KEEP_LOCAL: Keeping local version of ${localEvent.id}, will push on sync")

        return ConflictResolutionResult.Resolved(
            resolvedEvent = localEvent,
            strategy = ConflictResolutionStrategy.KEEP_LOCAL,
            message = "Local changes will be synced to Google"
        )
    }

    /**
     * KEEP_REMOTE: Discard local changes, update with remote version.
     */
    private suspend fun keepRemote(
        conflict: ConflictInfo,
        calendarInfo: GoogleCalendarInfo
    ): ConflictResolutionResult {
        val localEvent = conflict.localEvent
        val remoteEvent = conflict.remoteEvent

        // Merge remote data into local event (preserves local ID, linkedMemoPageId)
        val updatedEvent = localEvent.mergeWithRemote(remoteEvent, calendarInfo)

        // Update in repository
        localEventRepository.updateEvent(updatedEvent)

        Log.i(TAG, "KEEP_REMOTE: Updated ${localEvent.id} with remote version")

        return ConflictResolutionResult.Resolved(
            resolvedEvent = updatedEvent,
            strategy = ConflictResolutionStrategy.KEEP_REMOTE,
            message = "Updated with Google Calendar version"
        )
    }

    /**
     * KEEP_BOTH: Create a copy of local version, update original with remote.
     * User gets both versions to manually reconcile later.
     */
    private suspend fun keepBoth(
        conflict: ConflictInfo,
        calendarInfo: GoogleCalendarInfo
    ): ConflictResolutionResult {
        val localEvent = conflict.localEvent
        val remoteEvent = conflict.remoteEvent

        // Create a copy of the local version (local-only, not synced)
        val localCopy = localEvent.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${localEvent.title} (Local Copy)",
            syncEnabled = false,
            syncStatus = SyncStatus.LOCAL_ONLY,
            googleEventId = null,
            googleCalendarId = null,
            googleCalendarName = null,
            serverEtag = null,
            lastSyncedAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        localEventRepository.createEvent(localCopy)

        // Update original with remote version
        val updatedOriginal = localEvent.mergeWithRemote(remoteEvent, calendarInfo)
        localEventRepository.updateEvent(updatedOriginal)

        Log.i(TAG, "KEEP_BOTH: Created local copy ${localCopy.id}, updated original ${localEvent.id} with remote")

        return ConflictResolutionResult.ResolvedWithCopy(
            resolvedEvent = updatedOriginal,
            localCopy = localCopy,
            strategy = ConflictResolutionStrategy.KEEP_BOTH,
            message = "Created local copy, synced version updated"
        )
    }

    /**
     * AUTO_MERGE: Automatically merge non-overlapping changes.
     * Only works when local and remote changed different fields.
     */
    private suspend fun autoMerge(
        conflict: ConflictInfo,
        calendarInfo: GoogleCalendarInfo
    ): ConflictResolutionResult {
        if (!conflict.canAutoMerge) {
            Log.w(TAG, "AUTO_MERGE: Cannot auto-merge, overlapping fields: ${conflict.overlappingFields}")
            return ConflictResolutionResult.CannotAutoMerge(
                conflict = conflict,
                message = "Fields changed on both sides: ${conflict.overlappingFields.joinToString(", ")}"
            )
        }

        val localEvent = conflict.localEvent
        val remoteEvent = conflict.remoteEvent

        // Start with local event (preserves local changes)
        // Then selectively apply remote changes for fields not changed locally
        var mergedEvent = localEvent

        // Apply remote changes for fields not in localChangedFields
        val remoteTitle = remoteEvent.summary ?: localEvent.title
        val remoteLocation = remoteEvent.location
        val remoteDescription = remoteEvent.description
        val remoteStart = remoteEvent.start?.dateTime?.value ?: remoteEvent.start?.date?.value
        val remoteEnd = remoteEvent.end?.dateTime?.value ?: remoteEvent.end?.date?.value
        val remoteAttendees = remoteEvent.attendees?.mapNotNull { it.email }?.joinToString(",")

        // Only update fields that changed remotely but not locally
        if ("title" in conflict.remoteChangedFields && "title" !in conflict.localChangedFields) {
            mergedEvent = mergedEvent.copy(title = remoteTitle)
        }
        if ("location" in conflict.remoteChangedFields && "location" !in conflict.localChangedFields) {
            mergedEvent = mergedEvent.copy(location = remoteLocation)
        }
        if ("description" in conflict.remoteChangedFields && "description" !in conflict.localChangedFields) {
            mergedEvent = mergedEvent.copy(description = remoteDescription)
        }
        if ("time" in conflict.remoteChangedFields && "time" !in conflict.localChangedFields) {
            mergedEvent = mergedEvent.copy(
                startTime = remoteStart ?: localEvent.startTime,
                endTime = remoteEnd
            )
        }
        if ("attendees" in conflict.remoteChangedFields && "attendees" !in conflict.localChangedFields) {
            mergedEvent = mergedEvent.copy(attendees = remoteAttendees)
        }

        // Update sync metadata from remote
        val now = System.currentTimeMillis()
        mergedEvent = mergedEvent.copy(
            serverEtag = remoteEvent.etag,
            lastSyncedAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_UPDATE // Still needs to push local changes
        )

        localEventRepository.updateEvent(mergedEvent)

        Log.i(TAG, "AUTO_MERGE: Merged ${localEvent.id} - local: ${conflict.localChangedFields}, remote: ${conflict.remoteChangedFields}")

        return ConflictResolutionResult.Resolved(
            resolvedEvent = mergedEvent,
            strategy = ConflictResolutionStrategy.AUTO_MERGE,
            message = "Changes merged automatically"
        )
    }

    /**
     * Mark an event as having a conflict (for manual resolution later).
     */
    suspend fun markAsConflict(localEvent: LocalEvent): LocalEvent {
        val conflictEvent = localEvent.copy(
            syncStatus = SyncStatus.CONFLICT,
            updatedAt = System.currentTimeMillis()
        )
        localEventRepository.updateEvent(conflictEvent)
        Log.d(TAG, "MARK_CONFLICT: ${localEvent.id}")
        return conflictEvent
    }

    /**
     * Get all events with unresolved conflicts.
     */
    suspend fun getUnresolvedConflicts(): List<LocalEvent> {
        return localEventRepository.getConflictEvents()
    }

    /**
     * Check if there are any unresolved conflicts.
     */
    suspend fun hasUnresolvedConflicts(): Boolean {
        return getUnresolvedConflicts().isNotEmpty()
    }
}

// =============================================================================
// Data Classes
// =============================================================================

/**
 * Information about a detected conflict.
 */
data class ConflictInfo(
    val localEvent: LocalEvent,
    val remoteEvent: Event,
    val localChangedFields: Set<String>,
    val remoteChangedFields: Set<String>,
    val overlappingFields: Set<String>,
    val canAutoMerge: Boolean
) {
    /**
     * Get a summary of what changed on each side.
     */
    fun getConflictSummary(): String {
        return buildString {
            append("Local changes: ${localChangedFields.joinToString(", ")}\n")
            append("Remote changes: ${remoteChangedFields.joinToString(", ")}\n")
            if (overlappingFields.isNotEmpty()) {
                append("Conflicting fields: ${overlappingFields.joinToString(", ")}")
            }
        }
    }
}

/**
 * Strategy for resolving a conflict.
 */
enum class ConflictResolutionStrategy {
    KEEP_LOCAL,     // Keep local changes, push to Google
    KEEP_REMOTE,    // Discard local changes, use remote version
    KEEP_BOTH,      // Create local copy, update original with remote
    AUTO_MERGE      // Merge non-overlapping changes automatically
}

/**
 * Result of conflict resolution.
 */
sealed class ConflictResolutionResult {
    /**
     * Conflict resolved successfully.
     */
    data class Resolved(
        val resolvedEvent: LocalEvent,
        val strategy: ConflictResolutionStrategy,
        val message: String
    ) : ConflictResolutionResult()

    /**
     * Conflict resolved with a local copy created.
     */
    data class ResolvedWithCopy(
        val resolvedEvent: LocalEvent,
        val localCopy: LocalEvent,
        val strategy: ConflictResolutionStrategy,
        val message: String
    ) : ConflictResolutionResult()

    /**
     * Cannot auto-merge due to overlapping changes.
     */
    data class CannotAutoMerge(
        val conflict: ConflictInfo,
        val message: String
    ) : ConflictResolutionResult()

    /**
     * Resolution failed with error.
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : ConflictResolutionResult()
}
