package com.boox.day.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import android.util.Log
import java.util.Collections
import com.boox.day.booxnotes.LinkConfidence
import com.boox.day.booxnotes.MatchReason
import com.boox.day.booxnotes.SmartLinkDataQualityFlag
import com.boox.day.booxnotes.SmartLinkDecisionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Index of all Boox Notes for fast calendar queries.
 * This is a local cache populated by scanning the Boox Notes .ksync folder.
 */
@Entity(
    tableName = "boox_note_index",
    indices = [Index(value = ["originalDate"])]
)
data class BooxNoteIndex(
    @PrimaryKey
    val uniqueId: String,               // Boox note ID from .ksync
    val title: String,                  // Note title
    val originalDate: String,           // "yyyy-MM-dd" (extracted from shape files)
    val originalTimestamp: Long,        // Earliest shape file timestamp (ms)
    val pageCount: Int,                 // Number of pages
    val pageIdsJson: String,            // JSON array of page IDs
    val shapeFileModified: Long,        // For cache invalidation
    val indexedAt: Long,                // When this entry was created/updated
    val titleSource: String? = null,    // native / provider / derived / generated
    val dateSource: String? = null,     // title / title_partial / shape_file / unknown
    val pageSource: String? = null,     // provided / live_point / shape_directory / mixed / none
    val previewStatus: String? = null,  // page_ready / shape_only / text_only / unavailable
    val hasTextContent: Boolean = false,
    val warningsJson: String? = null    // Compact JSON array of warning strings
)

/**
 * Links between Boox Notes and Calendar Events.
 * Supports both auto-linking (by time proximity) and manual user corrections.
 *
 * Enhanced (Dec 2025):
 * - Confidence levels: HIGH/MEDIUM/LOW/NONE
 * - Day-level linking: Every note belongs to at least a day (no orphans)
 * - Suggestions: Store alternative event matches for user review
 * - Attention flag: Quick filter for notes needing user decision
 */
@Entity(
    tableName = "boox_note_links",
    indices = [
        Index(value = ["noteUniqueId"]),
        Index(value = ["linkedEventId"]),
        Index(value = ["linkedDayDate"]),      // NEW: For day-based queries
        Index(value = ["needsAttention"])      // NEW: For attention list queries
    ],
    foreignKeys = [
        ForeignKey(
            entity = CalendarEvent::class,
            parentColumns = ["id"],
            childColumns = ["linkedEventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class BooxNoteLink(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val noteUniqueId: String,           // References BooxNoteIndex.uniqueId
    val linkedEventId: String?,         // Null = unlinked (day-only general note)
    val linkedDayDate: String = "",     // NEW: Always set - "yyyy-MM-dd" (no orphans)
    val isManualLink: Boolean,          // false = auto-linked, true = user corrected
    val confidence: String = "NONE",    // NEW: "HIGH", "MEDIUM", "LOW", "NONE"
    val confidenceScore: Int = 0,       // NEW: Raw score (0-200+)
    val matchReasons: String? = null,   // NEW: JSON array of MatchReason names
    val suggestedEventIds: String? = null,  // NEW: JSON array of alternative event IDs
    val dismissedEventIds: String? = null,  // NEW: JSON array of user-dismissed suggestions
    val needsAttention: Boolean = false,    // NEW: Quick filter flag
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,   // NEW: When user last interacted with this link
    val decisionMode: String = SmartLinkDecisionMode.LEGACY.name,
    val engineVersion: String? = null,
    val dataQualityFlagsJson: String? = null
)

// ============================================================================
// DAOs
// ============================================================================

@Dao
interface BooxNoteIndexDao {
    @Query("SELECT * FROM boox_note_index WHERE originalDate = :date ORDER BY originalTimestamp ASC")
    suspend fun getNotesForDate(date: String): List<BooxNoteIndex>

    @Query("SELECT * FROM boox_note_index WHERE uniqueId = :uniqueId")
    suspend fun getNoteById(uniqueId: String): BooxNoteIndex?

    @Query("SELECT DISTINCT originalDate FROM boox_note_index WHERE originalDate LIKE :yearMonth || '%'")
    suspend fun getDatesWithNotesForMonth(yearMonth: String): List<String>

    @Query("SELECT * FROM boox_note_index ORDER BY originalTimestamp DESC")
    suspend fun getAllNotes(): List<BooxNoteIndex>

    @Query("SELECT uniqueId FROM boox_note_index")
    suspend fun getAllNoteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: BooxNoteIndex)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<BooxNoteIndex>)

    @Query("DELETE FROM boox_note_index WHERE uniqueId = :uniqueId")
    suspend fun deleteNote(uniqueId: String)

    @Query("DELETE FROM boox_note_index WHERE uniqueId NOT IN (:validIds)")
    suspend fun deleteStaleNotes(validIds: List<String>)

    @Query("SELECT MAX(indexedAt) FROM boox_note_index")
    suspend fun getLatestIndexedAt(): Long?

    @Query("SELECT COUNT(*) FROM boox_note_index")
    suspend fun getCount(): Int

    // ════════════════════════════════════════════════════════════════════════
    // SMART LINK QUERIES (Dec 2025)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Get ALL unlinked notes (no corresponding entry in boox_note_links with a linkedEventId).
     * Used by Bulk Smart Link wizard to find all notes needing attention.
     * Returns notes that either:
     * - Have no link record at all (noteUniqueId IS NULL after LEFT JOIN)
     * - Have a link record but linkedEventId is NULL (day-only note)
     */
    @Query("""
        SELECT bni.* FROM boox_note_index bni
        LEFT JOIN boox_note_links bnl ON bni.uniqueId = bnl.noteUniqueId
        WHERE (bnl.linkedEventId IS NULL OR bnl.noteUniqueId IS NULL)
        ORDER BY bni.originalDate DESC, bni.originalTimestamp DESC
    """)
    suspend fun getAllUnlinkedNotes(): List<BooxNoteIndex>

    /**
     * Get count of unlinked notes (for stats display on Smart Link button).
     */
    @Query("""
        SELECT COUNT(*) FROM boox_note_index bni
        LEFT JOIN boox_note_links bnl ON bni.uniqueId = bnl.noteUniqueId
        WHERE (bnl.linkedEventId IS NULL OR bnl.noteUniqueId IS NULL)
    """)
    suspend fun getUnlinkedNoteCount(): Int
}

@Dao
interface BooxNoteLinkDao {
    @Query("SELECT * FROM boox_note_links WHERE noteUniqueId = :noteId")
    suspend fun getLinkForNote(noteId: String): BooxNoteLink?

    @Query("SELECT * FROM boox_note_links WHERE linkedEventId = :eventId")
    suspend fun getLinksForEvent(eventId: String): List<BooxNoteLink>

    @Query("SELECT * FROM boox_note_links")
    suspend fun getAllLinks(): List<BooxNoteLink>

    @Query("""
        SELECT DISTINCT linkedEventId
        FROM boox_note_links
        WHERE noteUniqueId IN (
            SELECT uniqueId FROM boox_note_index WHERE originalDate = :date
        ) AND linkedEventId IS NOT NULL
    """)
    suspend fun getLinkedEventIdsForDate(date: String): List<String>

    @Query("""
        SELECT bnl.*, bni.title as noteTitle, bni.originalTimestamp as noteTimestamp, bni.pageCount as notePageCount
        FROM boox_note_links bnl
        INNER JOIN boox_note_index bni ON bnl.noteUniqueId = bni.uniqueId
        WHERE bni.originalDate = :date
        ORDER BY bnl.needsAttention DESC, bni.originalTimestamp DESC
    """)
    suspend fun getLinksWithTitlesForDate(date: String): List<BooxNoteLinkWithTitle>

    /**
     * Get links for specific event IDs (for Events Grid badges - cross-day aware).
     * Unlike getLinksWithTitlesForDate(), this finds links regardless of note origin date.
     * Used when we have the list of events and want to find ALL linked notes.
     */
    @Query("""
        SELECT bnl.*, bni.title as noteTitle, bni.originalTimestamp as noteTimestamp, bni.pageCount as notePageCount
        FROM boox_note_links bnl
        INNER JOIN boox_note_index bni ON bnl.noteUniqueId = bni.uniqueId
        WHERE bnl.linkedEventId IN (:eventIds)
        ORDER BY bni.originalTimestamp DESC
    """)
    suspend fun getLinksForEventIds(eventIds: List<String>): List<BooxNoteLinkWithTitle>

    @Query("""
        SELECT bnl.*, bni.title as noteTitle, bni.originalTimestamp as noteTimestamp, bni.pageCount as notePageCount
        FROM boox_note_links bnl
        INNER JOIN boox_note_index bni ON bnl.noteUniqueId = bni.uniqueId
        WHERE bnl.linkedEventId IN (:eventIds)
        ORDER BY bni.originalTimestamp DESC
    """)
    fun observeLinksForEventIds(eventIds: List<String>): Flow<List<BooxNoteLinkWithTitle>>

    // ════════════════════════════════════════════════════════════════════════
    // NEW: Enhanced queries for confidence-based UI (Dec 2025)
    // ════════════════════════════════════════════════════════════════════════

    /** Get all notes that need user attention (LOW confidence or MEDIUM needing review) */
    @Query("SELECT * FROM boox_note_links WHERE needsAttention = 1 ORDER BY updatedAt DESC")
    suspend fun getNotesNeedingAttention(): List<BooxNoteLink>

    /** Flow version for real-time UI updates */
    @Query("SELECT * FROM boox_note_links WHERE needsAttention = 1 ORDER BY updatedAt DESC")
    fun observeNotesNeedingAttention(): Flow<List<BooxNoteLink>>

    /** Get notes for a specific day (all notes, regardless of event link) */
    @Query("SELECT * FROM boox_note_links WHERE linkedDayDate = :date ORDER BY updatedAt DESC")
    suspend fun getNotesForDay(date: String): List<BooxNoteLink>

    /** Count notes linked to a specific event */
    @Query("SELECT COUNT(*) FROM boox_note_links WHERE linkedEventId = :eventId")
    suspend fun getNoteCountForEvent(eventId: String): Int

    /** Check if any notes for a day need attention */
    @Query("SELECT COUNT(*) FROM boox_note_links WHERE linkedDayDate = :date AND needsAttention = 1")
    suspend fun getAttentionCountForDay(date: String): Int

    /** Get note counts per event for a date (for badges) */
    @Query("""
        SELECT linkedEventId, COUNT(*) as count
        FROM boox_note_links
        WHERE linkedDayDate = :date AND linkedEventId IS NOT NULL
        GROUP BY linkedEventId
    """)
    suspend fun getNoteCountsPerEventForDate(date: String): List<EventNoteCount>

    /** Check if any event on a date has notes needing attention */
    @Query("""
        SELECT linkedEventId
        FROM boox_note_links
        WHERE linkedDayDate = :date AND needsAttention = 1 AND linkedEventId IS NOT NULL
    """)
    suspend fun getEventsWithAttentionForDate(date: String): List<String>

    /** Mark a note as reviewed (clears needsAttention) */
    @Query("UPDATE boox_note_links SET needsAttention = 0, lastReviewedAt = :timestamp WHERE noteUniqueId = :noteId")
    suspend fun markAsReviewed(noteId: String, timestamp: Long = System.currentTimeMillis())

    /** Dismiss a suggestion for a note */
    @Query("UPDATE boox_note_links SET dismissedEventIds = :dismissedIds, updatedAt = :timestamp WHERE noteUniqueId = :noteId")
    suspend fun updateDismissedSuggestions(noteId: String, dismissedIds: String, timestamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: BooxNoteLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<BooxNoteLink>)

    @Query("DELETE FROM boox_note_links WHERE noteUniqueId = :noteId")
    suspend fun deleteLinkForNote(noteId: String)

    @Query("DELETE FROM boox_note_links WHERE linkedEventId = :eventId")
    suspend fun deleteLinksForEvent(eventId: String)

    @Query("DELETE FROM boox_note_links")
    suspend fun deleteAll()
}

/**
 * Helper data class for event note count queries.
 */
data class EventNoteCount(
    val linkedEventId: String,
    val count: Int
)

/**
 * Helper data class for queries that join links with note titles.
 * Enhanced (Dec 2025) with confidence fields and note metadata.
 */
data class BooxNoteLinkWithTitle(
    val id: String,
    val noteUniqueId: String,
    val linkedEventId: String?,
    val linkedDayDate: String,
    val isManualLink: Boolean,
    val confidence: String,
    val confidenceScore: Int,
    val matchReasons: String?,
    val suggestedEventIds: String?,
    val dismissedEventIds: String?,
    val needsAttention: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long?,
    val decisionMode: String,
    val engineVersion: String?,
    val dataQualityFlagsJson: String?,
    // Joined from BooxNoteIndex
    val noteTitle: String,
    val noteTimestamp: Long,
    val notePageCount: Int
)

/**
 * Combined snapshot of all Boox Notes data for a single date.
 * PERFORMANCE: Reduces 3 separate DB queries to 1 by deriving counts in memory.
 * Used by EventsGrid and ExpandedEventsList to get all note data in one call.
 */
data class DateNotesSnapshot(
    /** All note links with their titles for the date (the primary data) */
    val linksWithTitles: List<BooxNoteLinkWithTitle>,
    /** Map of eventId -> note count (derived from linksWithTitles) */
    val noteCounts: Map<String, Int>,
    /** Set of eventIds that have notes needing attention (derived from linksWithTitles) */
    val eventsWithAttention: Set<String>
) {
    companion object {
        val EMPTY = DateNotesSnapshot(emptyList(), emptyMap(), emptySet())
    }
}

internal data class SmartLinkEventAliasResolution(
    val queryEventIds: List<String>,
    val aliasToDisplayEventId: Map<String, String>
)

internal fun parseJsonStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()

    return try {
        json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun toJsonStringList(values: Collection<String>): String? {
    val normalized = values
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    if (normalized.isEmpty()) return null
    return "[${normalized.joinToString(",") { "\"$it\"" }}]"
}

internal fun buildDismissedSuggestionLink(
    existingLink: BooxNoteLink?,
    noteId: String,
    linkedDayDate: String,
    eventIdToDismiss: String,
    timestamp: Long
): BooxNoteLink {
    val activeEventDismissed = existingLink?.linkedEventId == eventIdToDismiss
    val updatedDismissed = parseJsonStringList(existingLink?.dismissedEventIds) + eventIdToDismiss
    val updatedSuggestions = parseJsonStringList(existingLink?.suggestedEventIds)
        .filterNot { it == eventIdToDismiss }

    return BooxNoteLink(
        id = existingLink?.id ?: UUID.randomUUID().toString(),
        noteUniqueId = noteId,
        linkedEventId = if (activeEventDismissed) null else existingLink?.linkedEventId,
        linkedDayDate = existingLink?.linkedDayDate?.takeIf { it.isNotBlank() } ?: linkedDayDate,
        isManualLink = if (activeEventDismissed) false else existingLink?.isManualLink ?: false,
        confidence = if (activeEventDismissed) "NONE" else existingLink?.confidence ?: "NONE",
        confidenceScore = if (activeEventDismissed) 0 else existingLink?.confidenceScore ?: 0,
        matchReasons = if (activeEventDismissed) null else existingLink?.matchReasons,
        suggestedEventIds = toJsonStringList(updatedSuggestions),
        dismissedEventIds = toJsonStringList(updatedDismissed),
        needsAttention = if (activeEventDismissed) true else existingLink?.needsAttention ?: false,
        createdAt = existingLink?.createdAt ?: timestamp,
        updatedAt = timestamp,
        lastReviewedAt = existingLink?.lastReviewedAt,
        decisionMode = existingLink?.decisionMode ?: SmartLinkDecisionMode.LEGACY.name,
        engineVersion = existingLink?.engineVersion,
        dataQualityFlagsJson = existingLink?.dataQualityFlagsJson
    )
}

internal fun applyDeletedEventToLink(
    existingLink: BooxNoteLink,
    deletedEventId: String,
    timestamp: Long
): BooxNoteLink? {
    val existingSuggestions = parseJsonStringList(existingLink.suggestedEventIds)
    val updatedSuggestions = existingSuggestions.filterNot { it == deletedEventId }
    val activeEventDeleted = existingLink.linkedEventId == deletedEventId
    val suggestionsChanged = updatedSuggestions.size != existingSuggestions.size
    val existingFlags = parseJsonStringList(existingLink.dataQualityFlagsJson)

    if (!activeEventDeleted && !suggestionsChanged) {
        return null
    }

    return existingLink.copy(
        linkedEventId = if (activeEventDeleted) null else existingLink.linkedEventId,
        isManualLink = if (activeEventDeleted) false else existingLink.isManualLink,
        confidence = if (activeEventDeleted) "NONE" else existingLink.confidence,
        confidenceScore = if (activeEventDeleted) 0 else existingLink.confidenceScore,
        matchReasons = if (activeEventDeleted) null else existingLink.matchReasons,
        suggestedEventIds = toJsonStringList(updatedSuggestions),
        needsAttention = if (activeEventDeleted) true else existingLink.needsAttention,
        updatedAt = timestamp,
        dataQualityFlagsJson = if (activeEventDeleted) {
            toJsonStringList(existingFlags + SmartLinkDataQualityFlag.LINKED_EVENT_DELETED.name)
        } else {
            existingLink.dataQualityFlagsJson
        }
    )
}

internal fun remapEventIdList(
    values: List<String>,
    eventIdReplacements: Map<String, String>
): List<String> {
    if (values.isEmpty() || eventIdReplacements.isEmpty()) return values

    val remapped = LinkedHashSet<String>()
    values.forEach { value ->
        val normalized = value.trim()
        if (normalized.isEmpty()) return@forEach
        val replacement = eventIdReplacements[normalized] ?: normalized
        if (replacement.isNotBlank()) {
            remapped += replacement
        }
    }
    return remapped.toList()
}

internal fun rebindEventReferencesInLink(
    existingLink: BooxNoteLink,
    eventIdReplacements: Map<String, String>,
    timestamp: Long
): BooxNoteLink? {
    if (eventIdReplacements.isEmpty()) return null

    val originalLinkedEventId = existingLink.linkedEventId?.trim()?.takeIf { it.isNotEmpty() }
    val updatedLinkedEventId = originalLinkedEventId?.let { eventIdReplacements[it] ?: it }

    val originalSuggestions = parseJsonStringList(existingLink.suggestedEventIds)
    val updatedSuggestions = remapEventIdList(originalSuggestions, eventIdReplacements)

    val originalDismissed = parseJsonStringList(existingLink.dismissedEventIds)
    val updatedDismissed = remapEventIdList(originalDismissed, eventIdReplacements)

    val changed = updatedLinkedEventId != originalLinkedEventId ||
        updatedSuggestions != originalSuggestions ||
        updatedDismissed != originalDismissed

    if (!changed) {
        return null
    }

    return existingLink.copy(
        linkedEventId = updatedLinkedEventId,
        suggestedEventIds = toJsonStringList(updatedSuggestions),
        dismissedEventIds = toJsonStringList(updatedDismissed),
        updatedAt = timestamp
    )
}

internal fun buildSmartLinkEventAliasResolution(
    displayEventIds: List<String>,
    matchingLocalEvents: List<LocalEvent>
): SmartLinkEventAliasResolution {
    val normalizedDisplayIds = displayEventIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (normalizedDisplayIds.isEmpty()) {
        return SmartLinkEventAliasResolution(emptyList(), emptyMap())
    }

    val displayIdSet = normalizedDisplayIds.toSet()
    val queryEventIds = LinkedHashSet<String>()
    val aliasToDisplayEventId = linkedMapOf<String, String>()

    normalizedDisplayIds.forEach { displayEventId ->
        queryEventIds += displayEventId
        aliasToDisplayEventId[displayEventId] = displayEventId
    }

    matchingLocalEvents.forEach { localEvent ->
        val aliases = localEvent.linkedEventIdsForSmartLinks()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (aliases.isEmpty()) {
            return@forEach
        }

        val displayEventId = localEvent.toCalendarEvent().id
        val targetDisplayId = when {
            displayEventId in displayIdSet -> displayEventId
            else -> aliases.firstOrNull { it in displayIdSet } ?: return@forEach
        }

        aliases.forEach { alias ->
            queryEventIds += alias
            aliasToDisplayEventId[alias] = targetDisplayId
        }
    }

    return SmartLinkEventAliasResolution(
        queryEventIds = queryEventIds.toList(),
        aliasToDisplayEventId = aliasToDisplayEventId.toMap()
    )
}

internal fun buildEventBadgeData(
    links: List<BooxNoteLinkWithTitle>,
    aliasToDisplayEventId: Map<String, String> = emptyMap()
): BooxNoteRepository.EventBadgeData {
    val normalizedLinks = links
        .mapNotNull { link ->
            val linkedEventId = link.linkedEventId?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            link.copy(linkedEventId = aliasToDisplayEventId[linkedEventId] ?: linkedEventId)
        }

    val eventLinks = normalizedLinks
        .groupBy { it.linkedEventId!! }

    val noteCounts = eventLinks.mapValues { (_, groupedLinks) -> groupedLinks.size }

    val eventsWithAttention = normalizedLinks
        .filter { it.needsAttention }
        .map { it.linkedEventId!! }
        .toSet()

    val eventToNoteLinks = eventLinks.mapValues { (_, groupedLinks) ->
        groupedLinks
            .sortedWith(
                compareByDescending<BooxNoteLinkWithTitle> { it.isManualLink }
                    .thenByDescending { confidenceRank(it.confidence) }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.noteTimestamp }
            )
            .first()
            .noteUniqueId
    }

    return BooxNoteRepository.EventBadgeData(noteCounts, eventsWithAttention, eventToNoteLinks)
}

internal fun confidenceRank(confidence: String): Int {
    return when (confidence) {
        "HIGH" -> 3
        "MEDIUM" -> 2
        "LOW" -> 1
        else -> 0
    }
}

// ============================================================================
// Repository
// ============================================================================

class BooxNoteRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val indexDao = db.booxNoteIndexDao()
    private val linkDao = db.booxNoteLinkDao()
    private val localEventDao = db.localEventDao()
    private val semanticAggregateDao = db.booxNoteSemanticAggregateDao()

    companion object {
        private const val TAG = "BooxNoteRepo"
    }

    // PERFORMANCE: LRU cache for links-by-date queries (saves ~22ms per cached hit)
    // Thread-safe: Wrapped with synchronizedMap for concurrent access from main/background threads
    private val linksCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<BooxNoteLinkWithTitle>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<BooxNoteLinkWithTitle>>): Boolean {
                return size > 60  // Cache ~2 months (consistent with other repositories)
            }
        }
    )

    /**
     * Invalidate links cache for a specific date or all dates.
     * Call this when links are modified.
     */
    fun invalidateLinksCache(date: String? = null) {
        if (date != null) {
            linksCache.remove(date)
        } else {
            linksCache.clear()
        }
    }

    /**
     * Pre-warm the links cache for all dates with Boox Notes in a month.
     * Called when month changes to speed up EventsGrid loading.
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     */
    suspend fun preWarmLinksCacheForMonth(yearMonth: String) {
        val start = System.currentTimeMillis()
        val datesWithNotes = indexDao.getDatesWithNotesForMonth(yearMonth)
        var cachedCount = 0
        datesWithNotes.forEach { date ->
            if (!linksCache.containsKey(date)) {
                val links = linkDao.getLinksWithTitlesForDate(date)
                linksCache[date] = links
                cachedCount++
            }
        }
        Log.i(TAG, "PERF: preWarmLinksCacheForMonth $yearMonth - cached $cachedCount dates (${System.currentTimeMillis() - start}ms)")
    }

    // ========================================================================
    // Index Operations
    // ========================================================================

    suspend fun getNotesForDate(date: String): List<BooxNoteIndex> =
        indexDao.getNotesForDate(date)

    suspend fun getNoteById(uniqueId: String): BooxNoteIndex? =
        indexDao.getNoteById(uniqueId)

    suspend fun getDatesWithNotesForMonth(yearMonth: String): Set<String> =
        indexDao.getDatesWithNotesForMonth(yearMonth).toSet()

    suspend fun getAllNotes(): List<BooxNoteIndex> =
        indexDao.getAllNotes()

    suspend fun getAllNoteIds(): Set<String> =
        indexDao.getAllNoteIds().toSet()

    suspend fun upsertNote(note: BooxNoteIndex) =
        indexDao.upsert(note)

    suspend fun upsertNotes(notes: List<BooxNoteIndex>) =
        indexDao.upsertAll(notes)

    suspend fun deleteNote(uniqueId: String) {
        semanticAggregateDao.deleteAggregate(uniqueId)
        indexDao.deleteNote(uniqueId)
    }

    suspend fun deleteStaleNotes(validIds: List<String>) =
        indexDao.deleteStaleNotes(validIds)

    suspend fun getLatestIndexedAt(): Long? =
        indexDao.getLatestIndexedAt()

    suspend fun getNoteCount(): Int =
        indexDao.getCount()

    // ========================================================================
    // Smart Link Operations (Dec 2025)
    // ========================================================================

    /**
     * Get all notes that are not linked to any event.
     * Used by Bulk Smart Link wizard.
     */
    suspend fun getAllUnlinkedNotes(): List<BooxNoteIndex> =
        indexDao.getAllUnlinkedNotes()

    /**
     * Get count of unlinked notes.
     * Used to display count on Smart Link button.
     */
    suspend fun getUnlinkedNoteCount(): Int =
        indexDao.getUnlinkedNoteCount()

    // ========================================================================
    // Semantic Aggregate Operations (Smart Link launch rebuild)
    // ========================================================================

    suspend fun getSemanticAggregate(noteId: String): BooxNoteSemanticAggregate? =
        semanticAggregateDao.getAggregate(noteId)

    suspend fun getSemanticAggregates(noteIds: List<String>): List<BooxNoteSemanticAggregate> =
        if (noteIds.isEmpty()) emptyList() else semanticAggregateDao.getAggregates(noteIds)

    suspend fun upsertSemanticAggregate(aggregate: BooxNoteSemanticAggregate) =
        semanticAggregateDao.upsert(aggregate)

    suspend fun upsertSemanticAggregates(aggregates: List<BooxNoteSemanticAggregate>) {
        if (aggregates.isEmpty()) return
        semanticAggregateDao.upsertAll(aggregates)
    }

    suspend fun deleteSemanticAggregate(noteId: String) =
        semanticAggregateDao.deleteAggregate(noteId)

    suspend fun deleteSemanticAggregates(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        semanticAggregateDao.deleteAggregates(noteIds)
    }

    // ========================================================================
    // Link Operations
    // ========================================================================

    suspend fun getLinkForNote(noteId: String): BooxNoteLink? =
        linkDao.getLinkForNote(noteId)

    suspend fun getLinksForEvent(eventId: String): List<BooxNoteLink> =
        linkDao.getLinksForEvent(eventId)

    suspend fun getAllLinks(): List<BooxNoteLink> =
        linkDao.getAllLinks()

    suspend fun getLinkedEventIdsForDate(date: String): Set<String> =
        linkDao.getLinkedEventIdsForDate(date).filterNotNull().toSet()

    suspend fun getLinksWithTitlesForDate(date: String): List<BooxNoteLinkWithTitle> {
        val start = System.currentTimeMillis()

        // PERFORMANCE: Check cache first
        linksCache[date]?.let {
            Log.i(TAG, "PERF: getLinksWithTitlesForDate CACHE HIT for $date (0ms)")
            return it
        }

        // Cache miss - query DB and cache result
        val result = linkDao.getLinksWithTitlesForDate(date)
        linksCache[date] = result
        Log.i(TAG, "PERF: getLinksWithTitlesForDate CACHE MISS for $date (${System.currentTimeMillis() - start}ms)")
        return result
    }

    /**
     * Get all Boox Notes data for a date in a single snapshot.
     * PERFORMANCE: Replaces 3 separate calls with 1 by deriving counts in memory.
     *
     * Instead of calling:
     * - getLinksWithTitlesForDate() -> linksWithTitles
     * - getNoteCountsForDate() -> noteCounts
     * - getEventsWithAttentionForDate() -> eventsWithAttention
     *
     * Call this once and get all three derived from the same cached data.
     */
    suspend fun getDateNotesSnapshot(date: String): DateNotesSnapshot {
        val start = System.currentTimeMillis()

        // Use the existing cached method for the primary data
        val links = getLinksWithTitlesForDate(date)

        // Derive noteCounts in memory using Kotlin's groupingBy
        val noteCounts = links
            .filter { it.linkedEventId != null }
            .groupingBy { it.linkedEventId!! }
            .eachCount()

        // Derive eventsWithAttention in memory
        val eventsWithAttention = links
            .filter { it.linkedEventId != null && it.needsAttention }
            .map { it.linkedEventId!! }
            .toSet()

        Log.i(TAG, "PERF: getDateNotesSnapshot for $date: ${links.size} links, ${noteCounts.size} events with notes (${System.currentTimeMillis() - start}ms)")

        return DateNotesSnapshot(
            linksWithTitles = links,
            noteCounts = noteCounts,
            eventsWithAttention = eventsWithAttention
        )
    }

    /**
     * Badge data for Events Grid (cross-day aware).
     * Contains all information needed to render note badges on events.
     */
    data class EventBadgeData(
        val noteCounts: Map<String, Int>,           // eventId -> number of linked notes
        val eventsWithAttention: Set<String>,       // eventIds that have notes needing attention
        val eventToNoteLinks: Map<String, String>   // eventId -> first noteId (for single-note preview)
    )

    private suspend fun resolveEventAliases(eventIds: List<String>): SmartLinkEventAliasResolution {
        val normalizedEventIds = eventIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedEventIds.isEmpty()) {
            return SmartLinkEventAliasResolution(emptyList(), emptyMap())
        }

        val matchingLocalEvents = localEventDao.getByAnySmartLinkIds(normalizedEventIds)
        return buildSmartLinkEventAliasResolution(normalizedEventIds, matchingLocalEvents)
    }

    /**
     * Get badge data for specific events (cross-day aware).
     * Unlike getDateNotesSnapshot(), this finds notes linked to events regardless of
     * when the note was created. Use this for Events Grid badges.
     *
     * @param eventIds List of event IDs to check for linked notes
     * @return EventBadgeData containing counts, attention flags, and note IDs
     */
    suspend fun getEventBadgeData(eventIds: List<String>): EventBadgeData {
        val aliasResolution = resolveEventAliases(eventIds)
        if (aliasResolution.queryEventIds.isEmpty()) {
            return EventBadgeData(emptyMap(), emptySet(), emptyMap())
        }

        val start = System.currentTimeMillis()
        val links = linkDao.getLinksForEventIds(aliasResolution.queryEventIds)
        val badgeData = buildEventBadgeData(links, aliasResolution.aliasToDisplayEventId)

        Log.i(
            TAG,
            "PERF: getEventBadgeData for ${eventIds.size} events (${aliasResolution.queryEventIds.size} aliases): ${links.size} links found (${System.currentTimeMillis() - start}ms)"
        )

        return badgeData
    }

    fun observeEventBadgeData(eventIds: List<String>): Flow<EventBadgeData> {
        val normalizedEventIds = eventIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedEventIds.isEmpty()) {
            return flowOf(EventBadgeData(emptyMap(), emptySet(), emptyMap()))
        }

        return localEventDao.observeByAnySmartLinkIds(normalizedEventIds)
            .flatMapLatest { matchingLocalEvents ->
                val aliasResolution = buildSmartLinkEventAliasResolution(normalizedEventIds, matchingLocalEvents)
                if (aliasResolution.queryEventIds.isEmpty()) {
                    flowOf(EventBadgeData(emptyMap(), emptySet(), emptyMap()))
                } else {
                    linkDao.observeLinksForEventIds(aliasResolution.queryEventIds)
                        .map { links -> buildEventBadgeData(links, aliasResolution.aliasToDisplayEventId) }
                }
            }
    }

    /**
     * Link a note to an event (or unlink by passing null).
     * Legacy method - use linkNoteToEventEnhanced for new code.
     * @param noteId The Boox note uniqueId
     * @param eventId The Google Calendar event ID, or null to unlink
     * @param isManual true if user manually set this link
     */
    suspend fun linkNoteToEvent(noteId: String, eventId: String?, isManual: Boolean = false) {
        val existingLink = linkDao.getLinkForNote(noteId)
        // Get the note's date for linkedDayDate field
        val note = indexDao.getNoteById(noteId)
        val dayDate = note?.originalDate ?: ""

        val link = BooxNoteLink(
            id = existingLink?.id ?: UUID.randomUUID().toString(),
            noteUniqueId = noteId,
            linkedEventId = eventId,
            linkedDayDate = dayDate,
            isManualLink = isManual,
            confidence = if (isManual) "HIGH" else existingLink?.confidence ?: "NONE",
            confidenceScore = existingLink?.confidenceScore ?: 0,
            matchReasons = existingLink?.matchReasons,
            suggestedEventIds = existingLink?.suggestedEventIds,
            dismissedEventIds = existingLink?.dismissedEventIds,
            needsAttention = false,  // Manual links don't need attention
            createdAt = existingLink?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastReviewedAt = if (isManual) System.currentTimeMillis() else existingLink?.lastReviewedAt,
            decisionMode = when {
                isManual -> SmartLinkDecisionMode.MANUAL_LINK.name
                else -> existingLink?.decisionMode ?: SmartLinkDecisionMode.LEGACY.name
            },
            engineVersion = existingLink?.engineVersion,
            dataQualityFlagsJson = existingLink?.dataQualityFlagsJson
        )
        linkDao.upsert(link)
        // PERFORMANCE: Invalidate cache - we don't know which date, so clear all
        invalidateLinksCache()
    }

    /**
     * Enhanced link method with confidence tracking.
     * Used by the auto-linking algorithm.
     */
    suspend fun linkNoteToEventEnhanced(
        noteId: String,
        eventId: String?,
        linkedDayDate: String,
        confidence: LinkConfidence,
        confidenceScore: Int,
        matchReasons: List<MatchReason>,
        suggestedEventIds: List<String>,
        needsAttention: Boolean,
        isManual: Boolean = false,
        decisionMode: SmartLinkDecisionMode = SmartLinkDecisionMode.LEGACY,
        engineVersion: String? = null,
        dataQualityFlags: List<SmartLinkDataQualityFlag> = emptyList()
    ) {
        val existingLink = linkDao.getLinkForNote(noteId)

        // Serialize match reasons to JSON
        val reasonsJson = if (matchReasons.isNotEmpty()) {
            "[${matchReasons.joinToString(",") { "\"${it.name}\"" }}]"
        } else null

        // Serialize suggested event IDs to JSON
        val suggestionsJson = if (suggestedEventIds.isNotEmpty()) {
            "[${suggestedEventIds.joinToString(",") { "\"$it\"" }}]"
        } else null

        val dataQualityJson = toJsonStringList(dataQualityFlags.map { it.name })

        val link = BooxNoteLink(
            id = existingLink?.id ?: UUID.randomUUID().toString(),
            noteUniqueId = noteId,
            linkedEventId = eventId,
            linkedDayDate = linkedDayDate,
            isManualLink = isManual,
            confidence = confidence.name,
            confidenceScore = confidenceScore,
            matchReasons = reasonsJson,
            suggestedEventIds = suggestionsJson,
            dismissedEventIds = existingLink?.dismissedEventIds,  // Preserve dismissed
            needsAttention = needsAttention,
            createdAt = existingLink?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastReviewedAt = existingLink?.lastReviewedAt,
            decisionMode = decisionMode.name,
            engineVersion = engineVersion ?: existingLink?.engineVersion,
            dataQualityFlagsJson = dataQualityJson
        )
        linkDao.upsert(link)
        invalidateLinksCache()
    }

    /**
     * Remove the link for a specific note.
     */
    suspend fun unlinkNote(noteId: String) {
        linkDao.deleteLinkForNote(noteId)
        // PERFORMANCE: Invalidate cache
        invalidateLinksCache()
    }

    suspend fun deleteLinkForNote(noteId: String) =
        linkDao.deleteLinkForNote(noteId)

    /**
     * Clear all links (for cleanup/reset).
     */
    suspend fun clearAllLinks() {
        linkDao.deleteAll()
        // PERFORMANCE: Invalidate cache
        invalidateLinksCache()
    }

    // ========================================================================
    // NEW: Enhanced Link Operations (Dec 2025)
    // ========================================================================

    /** Get all notes needing user attention */
    suspend fun getNotesNeedingAttention(): List<BooxNoteLink> =
        linkDao.getNotesNeedingAttention()

    /** Flow for real-time attention list updates */
    fun observeNotesNeedingAttention(): Flow<List<BooxNoteLink>> =
        linkDao.observeNotesNeedingAttention()

    /** Get note counts per event for a date (for UI badges) */
    suspend fun getNoteCountsForDate(date: String): Map<String, Int> {
        return linkDao.getNoteCountsPerEventForDate(date)
            .associate { it.linkedEventId to it.count }
    }

    /** Get events that have notes needing attention for a date */
    suspend fun getEventsWithAttentionForDate(date: String): Set<String> =
        linkDao.getEventsWithAttentionForDate(date).toSet()

    /** Get all BooxNoteIndex objects linked to a specific event */
    suspend fun getNotesLinkedToEvent(eventId: String): List<BooxNoteIndex> {
        val aliasResolution = resolveEventAliases(listOf(eventId))
        if (aliasResolution.queryEventIds.isEmpty()) return emptyList()

        val links = linkDao.getLinksForEventIds(aliasResolution.queryEventIds)
        if (links.isEmpty()) return emptyList()

        val noteIds = links.map { it.noteUniqueId }.distinct()
        return noteIds.mapNotNull { indexDao.getNoteById(it) }
    }

    /** Get attention count for a specific day */
    suspend fun getAttentionCountForDay(date: String): Int =
        linkDao.getAttentionCountForDay(date)

    /** Mark a note as reviewed (clears needsAttention flag) */
    suspend fun markNoteAsReviewed(noteId: String) {
        linkDao.markAsReviewed(noteId)
        invalidateLinksCache()
    }

    /** Dismiss a suggestion for a note */
    suspend fun dismissSuggestion(noteId: String, eventIdToDismiss: String) {
        val existingLink = linkDao.getLinkForNote(noteId)
        val linkedDayDate = existingLink?.linkedDayDate?.takeIf { it.isNotBlank() }
            ?: indexDao.getNoteById(noteId)?.originalDate
            ?: ""
        val updatedLink = buildDismissedSuggestionLink(
            existingLink = existingLink,
            noteId = noteId,
            linkedDayDate = linkedDayDate,
            eventIdToDismiss = eventIdToDismiss,
            timestamp = System.currentTimeMillis()
        )

        linkDao.upsert(updatedLink)
        invalidateLinksCache()
    }

    suspend fun handleDeletedEvent(eventId: String) {
        handleDeletedEvents(listOf(eventId))
    }

    suspend fun handleDeletedEvents(eventIds: Collection<String>) {
        val deletedEventIds = eventIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (deletedEventIds.isEmpty()) return

        val timestamp = System.currentTimeMillis()
        val updatedLinks = linkDao.getAllLinks().mapNotNull { link ->
            var current = link
            var changed = false

            deletedEventIds.forEach { deletedEventId ->
                val updated = applyDeletedEventToLink(current, deletedEventId, timestamp)
                if (updated != null) {
                    current = updated
                    changed = true
                }
            }

            if (changed) current else null
        }

        if (updatedLinks.isEmpty()) return

        linkDao.upsertAll(updatedLinks)
        invalidateLinksCache()
        Log.i(TAG, "Marked ${updatedLinks.size} note link(s) unresolved after deleting ${deletedEventIds.size} event(s)")
    }

    suspend fun rebindEventIds(eventIdReplacements: Map<String, String>) {
        val normalizedReplacements = linkedMapOf<String, String>()
        eventIdReplacements.forEach { (fromEventId, toEventId) ->
            val from = fromEventId.trim()
            val to = toEventId.trim()
            if (from.isNotEmpty() && to.isNotEmpty() && from != to) {
                normalizedReplacements[from] = to
            }
        }
        if (normalizedReplacements.isEmpty()) return

        val timestamp = System.currentTimeMillis()
        val updatedLinks = linkDao.getAllLinks().mapNotNull { link ->
            rebindEventReferencesInLink(link, normalizedReplacements, timestamp)
        }
        if (updatedLinks.isEmpty()) return

        linkDao.upsertAll(updatedLinks)
        invalidateLinksCache()
        Log.i(
            TAG,
            "Rebound ${updatedLinks.size} note link(s) across ${normalizedReplacements.size} event ID mapping(s)"
        )
    }

    /** Accept a suggestion - link note to the suggested event */
    suspend fun acceptSuggestion(noteId: String, eventId: String, dayDate: String) {
        val existingLink = linkDao.getLinkForNote(noteId)

        val link = BooxNoteLink(
            id = existingLink?.id ?: UUID.randomUUID().toString(),
            noteUniqueId = noteId,
            linkedEventId = eventId,
            linkedDayDate = dayDate,
            isManualLink = true,  // User accepted = manual
            confidence = "HIGH",  // User confirmed = high confidence
            confidenceScore = existingLink?.confidenceScore ?: 100,
            matchReasons = existingLink?.matchReasons,
            suggestedEventIds = null,  // Clear suggestions
            dismissedEventIds = null,  // Clear dismissed
            needsAttention = false,    // No longer needs attention
            createdAt = existingLink?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastReviewedAt = System.currentTimeMillis(),
            decisionMode = SmartLinkDecisionMode.MANUAL_ACCEPT_SUGGESTION.name,
            engineVersion = existingLink?.engineVersion,
            dataQualityFlagsJson = existingLink?.dataQualityFlagsJson
        )
        linkDao.upsert(link)
        invalidateLinksCache()
    }

}
