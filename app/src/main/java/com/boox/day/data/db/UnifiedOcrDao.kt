package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * DAO for Unified OCR data access.
 *
 * Handles:
 * - OCR page status tracking (UnifiedOcrPage)
 * - OCR text storage and retrieval (UnifiedOcrText)
 * - FTS4 full-text search
 * - Per-type statistics for UI
 */
@Dao
interface UnifiedOcrDao {

    // ═══════════════════════════════════════════════════════════════════════════
    // OCR PAGE STATUS OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get OCR status for a specific page.
     */
    @Query("SELECT * FROM unified_ocr_page WHERE id = :id")
    suspend fun getOcrPage(id: String): UnifiedOcrPage?

    /**
     * Get all OCR page statuses for a source.
     */
    @Query("SELECT * FROM unified_ocr_page WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY pageId")
    suspend fun getOcrPagesForSource(sourceType: String, sourceId: String): List<UnifiedOcrPage>

    /**
     * Get pages needing OCR processing.
     *
     * Returns pages that are:
     * - PENDING status
     * - IN_PROGRESS but stale (ocrAt older than staleThresholdMs - crash recovery)
     *
     * @param staleThresholdMs Pages with IN_PROGRESS status older than this are considered stale
     * @param limit Maximum number of pages to return
     */
    @Query("""
        SELECT * FROM unified_ocr_page
        WHERE status = 'PENDING'
           OR (status = 'IN_PROGRESS' AND ocrAt < :staleThresholdMs)
        ORDER BY
            CASE sourceType
                WHEN 'MEMO' THEN 0
                WHEN 'TASK' THEN 1
                WHEN 'BOOX_NOTE' THEN 2
                ELSE 3
            END,
            sourceModifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getPagesNeedingOcr(staleThresholdMs: Long, limit: Int = 50): List<UnifiedOcrPage>

    /**
     * Get pending pages by source type.
     */
    @Query("""
        SELECT * FROM unified_ocr_page
        WHERE sourceType = :sourceType
          AND (status = 'PENDING' OR (status = 'IN_PROGRESS' AND ocrAt < :staleThresholdMs))
        ORDER BY sourceModifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getPendingPagesByType(sourceType: String, staleThresholdMs: Long, limit: Int): List<UnifiedOcrPage>

    /**
     * Count pages needing OCR (for statistics).
     */
    @Query("""
        SELECT COUNT(*) FROM unified_ocr_page
        WHERE status = 'PENDING'
           OR (status = 'IN_PROGRESS' AND ocrAt < :staleThresholdMs)
    """)
    suspend fun getPendingCount(staleThresholdMs: Long): Int

    /**
     * Count pending pages by source type.
     */
    @Query("""
        SELECT COUNT(*) FROM unified_ocr_page
        WHERE sourceType = :sourceType
          AND (status = 'PENDING' OR (status = 'IN_PROGRESS' AND ocrAt < :staleThresholdMs))
    """)
    suspend fun getPendingCountByType(sourceType: String, staleThresholdMs: Long): Int

    /**
     * Upsert an OCR page status record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrPage(page: UnifiedOcrPage)

    /**
     * Upsert multiple OCR page status records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrPages(pages: List<UnifiedOcrPage>)

    /**
     * Update status for a specific page.
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = :status, ocrAt = :ocrAt, textHash = :textHash, semanticHash = :semanticHash, lastError = NULL
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: String, ocrAt: Long, textHash: String?, semanticHash: String?)

    /**
     * Update status and set firstIndexedAt for a specific page.
     *
     * Sets firstIndexedAt to the current timestamp ONLY if:
     * - It was previously 0 (never indexed with content), AND
     * - The new status has actual content (SUCCESS or LOW_SIGNAL)
     *
     * EMPTY pages do NOT set firstIndexedAt because they had no content to index.
     * This ensures that when content is later added, the page is treated as NEW
     * (costs budget) rather than a re-index (free).
     *
     * failCount goes back to 0 (Oct 2026): the page was read, so old failures say nothing about it.
     * Before, a page that once reached the quarantine threshold was quarantined again by its first
     * failure after any later edit.
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = :status,
            ocrAt = :ocrAt,
            textHash = :textHash,
            semanticHash = :semanticHash,
            lastError = NULL,
            failCount = 0,
            firstIndexedAt = CASE
                WHEN firstIndexedAt = 0 AND :status IN ('SUCCESS', 'LOW_SIGNAL') THEN :now
                ELSE firstIndexedAt
            END
        WHERE id = :id
    """)
    suspend fun updateStatusWithFirstIndexed(id: String, status: String, ocrAt: Long, textHash: String?, semanticHash: String?, now: Long)

    /**
     * Check if a page has been indexed before (firstIndexedAt > 0).
     *
     * @return true if page exists and has been indexed before, false otherwise
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM unified_ocr_page
            WHERE id = :id AND firstIndexedAt > 0
        )
    """)
    suspend fun hasBeenIndexedBefore(id: String): Boolean

    /**
     * Mark a page as failed with error message.
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = :status, failCount = failCount + 1, lastError = :error, ocrAt = :ocrAt
        WHERE id = :id
    """)
    suspend fun markFailed(id: String, status: String, error: String?, ocrAt: Long)

    /**
     * Update page status with explicit fail count (A-SHORT-3: Zombie cleanup).
     * Used for quarantine and retry tracking where we set (not increment) the fail count.
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = :status, failCount = :failCount, lastError = :error, ocrAt = strftime('%s','now') * 1000
        WHERE id = :id
    """)
    suspend fun updatePageStatus(id: String, status: String, failCount: Int, error: String?)

    /**
     * Mark a page as in-progress (crash recovery marker).
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = 'IN_PROGRESS', ocrAt = :ocrAt
        WHERE id = :id
    """)
    suspend fun markInProgress(id: String, ocrAt: Long)

    /**
     * Reset stale IN_PROGRESS pages to PENDING (crash recovery).
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = 'PENDING'
        WHERE status = 'IN_PROGRESS' AND ocrAt < :staleThresholdMs
    """)
    suspend fun resetStaleInProgress(staleThresholdMs: Long): Int

    /**
     * Reset a specific page to PENDING status.
     * Used when a page can't be processed right now (e.g., bitmap not ready yet)
     * but should be retried later.
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = 'PENDING'
        WHERE id = :id
    """)
    suspend fun resetToPending(id: String)

    /**
     * Delete OCR page records for a source.
     */
    @Query("DELETE FROM unified_ocr_page WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deletePagesBySource(sourceType: String, sourceId: String)

    /**
     * Delete OCR page record for a specific page.
     */
    @Query("DELETE FROM unified_ocr_page WHERE id = :id")
    suspend fun deletePageById(id: String)

    /**
     * Delete all OCR page records for a source type.
     */
    @Query("DELETE FROM unified_ocr_page WHERE sourceType = :sourceType")
    suspend fun deletePagesBySourceType(sourceType: String)

    /**
     * Delete all OCR page records.
     */
    @Query("DELETE FROM unified_ocr_page")
    suspend fun deleteAllPages()

    // ═══════════════════════════════════════════════════════════════════════════
    // OCR TEXT OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get OCR text for a specific page.
     */
    @Query("SELECT * FROM unified_ocr_text WHERE id = :id")
    suspend fun getOcrText(id: String): UnifiedOcrText?

    /**
     * Get all OCR texts for a source.
     */
    @Query("SELECT * FROM unified_ocr_text WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY pageId")
    suspend fun getOcrTextsForSource(sourceType: String, sourceId: String): List<UnifiedOcrText>

    /**
     * Get combined text for all pages of a source.
     */
    @Query("""
        SELECT GROUP_CONCAT(text, '
        ') FROM unified_ocr_text
        WHERE sourceType = :sourceType AND sourceId = :sourceId
        ORDER BY pageId
    """)
    suspend fun getCombinedTextForSource(sourceType: String, sourceId: String): String?

    /**
     * Upsert OCR text record.
     *
     * An existing id is UPDATEd in place, never REPLACEd: REPLACE deletes the old row without
     * firing the delete trigger, so each re-index left a stale document in
     * unified_ocr_text_fts (see FTS4_CALLBACK in Db.kt).
     */
    @Upsert
    suspend fun upsertOcrText(text: UnifiedOcrText)

    /**
     * Delete OCR text record for a specific page.
     */
    @Query("DELETE FROM unified_ocr_text WHERE id = :id")
    suspend fun deleteTextById(id: String)

    /**
     * Delete OCR text records for a source.
     */
    @Query("DELETE FROM unified_ocr_text WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteTextBySource(sourceType: String, sourceId: String)

    /**
     * Delete all OCR text records for a source type.
     */
    @Query("DELETE FROM unified_ocr_text WHERE sourceType = :sourceType")
    suspend fun deleteTextBySourceType(sourceType: String)

    /**
     * Delete all OCR text records.
     */
    @Query("DELETE FROM unified_ocr_text")
    suspend fun deleteAllText()

    // ═══════════════════════════════════════════════════════════════════════════
    // FTS4 FULL-TEXT SEARCH
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Search OCR text using FTS4 full-text search.
     *
     * Returns matching pages with snippet and relevance info.
     *
     * @param query FTS4 query string
     * @param limit Maximum results to return
     */
    @SkipQueryVerification  // FTS4 virtual tables can't be verified at compile time
    @Query("""
        SELECT
            ot.id,
            ot.sourceType,
            ot.sourceId,
            ot.pageId,
            ot.sourceTitle,
            ot.sourceDate,
            -- From the synopsis; from the text when the synopsis is NULL (pages stored
            -- without a semantic bundle), where snippet() returns NULL for a non-null field
            COALESCE(
                snippet(unified_ocr_text_fts, '<b>', '</b>', '...', 2, 32),
                snippet(unified_ocr_text_fts, '<b>', '</b>', '...', 0, 32)
            ) as snippet,
            0 as score,
            ot.domain,
            ot.wordCount,
            ot.confidence
        FROM unified_ocr_text_fts
        INNER JOIN unified_ocr_text ot ON unified_ocr_text_fts.docid = ot.rowid
        WHERE unified_ocr_text_fts MATCH :query
        ORDER BY ot.sourceDate DESC
        LIMIT :limit
    """)
    suspend fun searchText(query: String, limit: Int = 50): List<UnifiedOcrSearchResult>

    /**
     * Count search results for a query.
     */
    @SkipQueryVerification
    @Query("""
        SELECT COUNT(*) FROM unified_ocr_text_fts
        WHERE unified_ocr_text_fts MATCH :query
    """)
    suspend fun searchTextCount(query: String): Int

    /**
     * Search within a specific source type.
     */
    @SkipQueryVerification
    @Query("""
        SELECT
            ot.id,
            ot.sourceType,
            ot.sourceId,
            ot.pageId,
            ot.sourceTitle,
            ot.sourceDate,
            -- From the synopsis; from the text when the synopsis is NULL (pages stored
            -- without a semantic bundle), where snippet() returns NULL for a non-null field
            COALESCE(
                snippet(unified_ocr_text_fts, '<b>', '</b>', '...', 2, 32),
                snippet(unified_ocr_text_fts, '<b>', '</b>', '...', 0, 32)
            ) as snippet,
            0 as score,
            ot.domain,
            ot.wordCount,
            ot.confidence
        FROM unified_ocr_text_fts
        INNER JOIN unified_ocr_text ot ON unified_ocr_text_fts.docid = ot.rowid
        WHERE unified_ocr_text_fts MATCH :query AND ot.sourceType = :sourceType
        ORDER BY ot.sourceDate DESC
        LIMIT :limit
    """)
    suspend fun searchTextByType(query: String, sourceType: String, limit: Int = 50): List<UnifiedOcrSearchResult>

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get statistics grouped by source type.
     *
     * Note: "scanned" only counts pages with actual searchable content (SUCCESS, LOW_SIGNAL).
     * EMPTY pages (no content/bitmap) are excluded from counts since they have nothing to search.
     */
    @Query("""
        SELECT
            sourceType,
            COUNT(CASE WHEN status IN ('SUCCESS', 'LOW_SIGNAL') THEN 1 END) as scanned,
            COUNT(CASE WHEN status = 'PENDING' THEN 1 END) as pending,
            COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed
        FROM unified_ocr_page
        GROUP BY sourceType
    """)
    suspend fun getStatsByType(): List<OcrTypeStats>

    /**
     * Get count of pages by status.
     */
    @Query("SELECT COUNT(*) FROM unified_ocr_page WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    /**
     * Get count of pages by source type and status.
     */
    @Query("SELECT COUNT(*) FROM unified_ocr_page WHERE sourceType = :sourceType AND status = :status")
    suspend fun getCountByTypeAndStatus(sourceType: String, status: String): Int

    /**
     * Get total number of indexed pages (SUCCESS, EMPTY, or LOW_SIGNAL status).
     */
    @Query("SELECT COUNT(*) FROM unified_ocr_page WHERE status IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL')")
    suspend fun getSuccessCount(): Int

    /**
     * Get total number of failed pages.
     */
    @Query("SELECT COUNT(*) FROM unified_ocr_page WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int

    /**
     * Get total bytes of stored text (for storage stats).
     */
    @Query("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM unified_ocr_text")
    suspend fun getTotalTextBytes(): Long

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Clear all OCR data (for "Clear Index" feature).
     */
    @Transaction
    suspend fun clearAllOcrData() {
        deleteAllText()
        deleteAllPages()
    }

    /**
     * Clear OCR data for a specific source type.
     */
    @Transaction
    suspend fun clearOcrDataByType(sourceType: String) {
        deleteTextBySourceType(sourceType)
        deletePagesBySourceType(sourceType)
    }

    /**
     * Reset firstIndexedAt for EMPTY pages so they can be properly indexed when content is added.
     *
     * BUG FIX (Jan 2026): EMPTY pages were incorrectly setting firstIndexedAt, causing them
     * to be treated as "already indexed" when content was later added. This prevented the
     * monthly counter from incrementing for memos/tasks that were created empty and later filled.
     *
     * @return Number of pages reset
     */
    @Query("""
        UPDATE unified_ocr_page
        SET firstIndexedAt = 0
        WHERE status = 'EMPTY' AND firstIndexedAt > 0
    """)
    suspend fun resetFirstIndexedAtForEmptyPages(): Int

    /**
     * Free pages quarantined only because the AI read had no internet or timed out (Oct 2026).
     * Before then a network error counted as a page failure, so 3 of them while offline quarantined
     * a page for good. Reads now wait instead, so no page gets quarantined with these errors any
     * more and this can run at every start. Pages quarantined for other errors stay.
     *
     * @return Number of pages freed
     */
    @Query("""
        UPDATE unified_ocr_page
        SET status = 'PENDING', failCount = 0
        WHERE status = 'QUARANTINED'
          AND (lastError LIKE 'AI read failed: Network error:%' OR lastError = 'AI read failed: timed out')
    """)
    suspend fun releaseNetworkQuarantines(): Int

    /**
     * Check if a page needs OCR processing.
     *
     * Returns true (needs OCR) when:
     * - Page doesn't exist in database (never processed)
     * - Page exists but has failed/pending status
     * - Page exists but source was modified after last successful OCR
     *
     * Returns false (no OCR needed) when:
     * - Page exists with success status AND source hasn't been modified
     */
    @Query("""
        SELECT NOT EXISTS(
            SELECT 1 FROM unified_ocr_page
            WHERE id = :id
              AND sourceModifiedAt >= :currentModifiedAt
              AND status IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL', 'FAILED', 'QUARANTINED')
        )
    """)
    suspend fun needsReOcr(id: String, currentModifiedAt: Long): Boolean

    /**
     * Boox-specific re-OCR check that also refreshes when note identity changed.
     *
     * A Boox page should be re-indexed when:
     * - the source bitmap changed, OR
     * - the stored title/date metadata no longer matches the note index
     *
     * EMPTY pages often have no OCR text row, so they are treated as "metadata already synced"
     * unless the bitmap itself changed.
     */
    @Query("""
        SELECT NOT EXISTS(
            SELECT 1
            FROM unified_ocr_page p
            LEFT JOIN unified_ocr_text t ON t.id = p.id
            WHERE p.id = :id
              AND p.sourceModifiedAt >= :currentModifiedAt
              AND p.status IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL', 'FAILED', 'QUARANTINED')
              AND (
                    t.id IS NULL OR (
                        COALESCE(t.sourceTitle, '') = COALESCE(:currentSourceTitle, '')
                        AND COALESCE(t.sourceDate, '') = COALESCE(:currentSourceDate, '')
                    )
                  )
        )
    """)
    suspend fun needsReOcrWithSourceMetadata(
        id: String,
        currentModifiedAt: Long,
        currentSourceTitle: String?,
        currentSourceDate: String?
    ): Boolean

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFICIENT CHANGE DETECTION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find memo pages that need OCR - EFFICIENT single query instead of date iteration.
     *
     * Returns pages where:
     * - Never indexed (no record in unified_ocr_page), OR
     * - Modified since last processing (updatedAt > sourceModifiedAt), OR
     * - Has non-terminal status (IN_PROGRESS, PENDING)
     *
     * Skips pages where:
     * - Has terminal status (SUCCESS, EMPTY, LOW_SIGNAL, FAILED, QUARANTINED) AND not modified since
     *
     * @param limit Maximum pages to return
     * @return List of (date, pageId, updatedAt) for pages needing OCR
     */
    @Query("""
        SELECT dp.date, p.id as pageId, p.updatedAt
        FROM page p
        INNER JOIN daypage dp ON dp.pageIds LIKE '%' || p.id || '%'
        LEFT JOIN unified_ocr_page uop
            ON uop.pageId = p.id AND uop.sourceType = 'MEMO'
        WHERE uop.id IS NULL
           OR p.updatedAt > uop.sourceModifiedAt
           OR uop.status NOT IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL', 'FAILED', 'QUARANTINED')
        ORDER BY p.updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getMemoPagesNeedingOcr(limit: Int): List<MemoPageNeedingOcr>

    /**
     * Find task pages that need OCR.
     * Same logic as memo pages - only process if never indexed, modified, or non-terminal status.
     *
     * IMPORTANT: Uses GROUP BY to deduplicate by pageId because multiple Task rows
     * can share the same pageId (all tasks on a day share one page). Without GROUP BY,
     * we'd get 10 duplicate queue items for a single page, causing the same page to be
     * marked complete multiple times.
     */
    @Query("""
        SELECT t.dayDate as date, t.pageId, MAX(t.updatedAt) as updatedAt
        FROM task t
        LEFT JOIN unified_ocr_page uop
            ON uop.pageId = t.pageId AND uop.sourceType = 'TASK'
        WHERE t.pageId IS NOT NULL
          AND (uop.id IS NULL
               OR t.updatedAt > uop.sourceModifiedAt
               OR uop.status NOT IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL', 'FAILED', 'QUARANTINED'))
        GROUP BY t.pageId
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getTaskPagesNeedingOcr(limit: Int): List<MemoPageNeedingOcr>

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC GENIUS SERVICE QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get OCR texts by source date.
     * NOTE: sourceDate is nullable - only returns rows where sourceDate matches exactly.
     */
    @Query("SELECT * FROM unified_ocr_text WHERE sourceDate = :sourceDate")
    suspend fun getOcrTextsBySourceDate(sourceDate: String): List<UnifiedOcrText>

    /**
     * Get OCR texts by domain (uses existing domain index).
     */
    @Query("SELECT * FROM unified_ocr_text WHERE domain = :domain ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getOcrTextsByDomain(domain: String, limit: Int): List<UnifiedOcrText>

    /**
     * Get OCR texts with action items since a cutoff date.
     * Filters for non-null, non-empty actionItemsJson.
     */
    @Query("""
        SELECT * FROM unified_ocr_text
        WHERE actionItemsJson IS NOT NULL
          AND actionItemsJson != '[]'
          AND actionItemsJson != ''
          AND sourceDate IS NOT NULL
          AND sourceDate >= :cutoffDate
        ORDER BY sourceDate DESC
        LIMIT :limit
    """)
    suspend fun getOcrTextsWithActionItems(cutoffDate: String, limit: Int): List<UnifiedOcrText>

    /**
     * Get OCR texts in date range.
     * NOTE: Excludes rows where sourceDate is NULL.
     */
    @Query("""
        SELECT * FROM unified_ocr_text
        WHERE sourceDate IS NOT NULL
          AND sourceDate >= :startDate
          AND sourceDate <= :endDate
        ORDER BY sourceDate DESC
    """)
    suspend fun getOcrTextsInDateRange(startDate: String, endDate: String): List<UnifiedOcrText>

    /**
     * Get domain statistics for date range.
     */
    @Query("""
        SELECT domain, COUNT(*) as count
        FROM unified_ocr_text
        WHERE domain IS NOT NULL
          AND sourceDate IS NOT NULL
          AND sourceDate >= :startDate
          AND sourceDate <= :endDate
        GROUP BY domain
        ORDER BY count DESC
    """)
    suspend fun getDomainStatsForDateRange(startDate: String, endDate: String): List<DomainStat>

    /**
     * Get domain statistics for all time.
     */
    @Query("""
        SELECT domain, COUNT(*) as count
        FROM unified_ocr_text
        WHERE domain IS NOT NULL
        GROUP BY domain
        ORDER BY count DESC
    """)
    suspend fun getDomainStatsAll(): List<DomainStat>

    /**
     * Domain statistics result.
     */
    data class DomainStat(
        val domain: String,
        val count: Int
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART LINK BACKFILL FIX - Batch queries for backfill completion check
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get distinct source IDs that have been indexed for a source type.
     * Used to check backfill completion - compare against total notes in BooxNoteIndex.
     */
    @Query("""
        SELECT DISTINCT sourceId FROM unified_ocr_text
        WHERE sourceType = :sourceType
    """)
    suspend fun getIndexedSourceIdsByType(sourceType: String): List<String>

    /**
     * Get distinct source IDs that have been PROCESSED for a source type.
     * This includes notes with text (SUCCESS), blank notes (EMPTY), and low-signal notes.
     *
     * IMPORTANT: Use this for backfill completion check, NOT getIndexedSourceIdsByType.
     * Blank notes are processed but have no entry in unified_ocr_text, so using
     * unified_ocr_text would never count them as "indexed" → backfill never completes.
     */
    @Query("""
        SELECT DISTINCT sourceId FROM unified_ocr_page
        WHERE sourceType = :sourceType
        AND status IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL')
    """)
    suspend fun getProcessedSourceIdsByType(sourceType: String): List<String>

    /**
     * Get count of processed pages for a source type, restricted to specific page entry IDs.
     * Counts SUCCESS, EMPTY, and LOW_SIGNAL pages as processed.
     */
    @Query("""
        SELECT COUNT(*) FROM unified_ocr_page
        WHERE sourceType = :sourceType
        AND status IN ('SUCCESS', 'EMPTY', 'LOW_SIGNAL')
        AND id IN (:entryIds)
    """)
    suspend fun getProcessedPageCountByTypeAndIds(sourceType: String, entryIds: List<String>): Int

    /**
     * Get all OCR texts for a source type.
     * Used for batch profile loading to avoid N+1 queries.
     */
    @Query("""
        SELECT * FROM unified_ocr_text
        WHERE sourceType = :sourceType
        ORDER BY sourceDate DESC
    """)
    suspend fun getOcrTextsBySourceType(sourceType: String): List<UnifiedOcrText>

    /**
     * Count OCR text records for a source type.
     * Quick check if any OCR data exists (e.g., for Smart Link gating).
     */
    @Query("SELECT COUNT(*) FROM unified_ocr_text WHERE sourceType = :sourceType")
    suspend fun getCountBySourceType(sourceType: String): Int

    /**
     * Get all OCR texts (for backup).
     */
    @Query("SELECT * FROM unified_ocr_text")
    fun getAllTexts(): List<UnifiedOcrText>

    /**
     * Backup export reads OCR texts in id order, one batch at a time, so memory stays bounded.
     */
    @Query("SELECT * FROM unified_ocr_text WHERE id > :afterId ORDER BY id LIMIT :limit")
    fun getTextBatchAfter(afterId: String, limit: Int): List<UnifiedOcrText>

    /**
     * Get all OCR page statuses (for backup).
     */
    @Query("SELECT * FROM unified_ocr_page")
    fun getAllPages(): List<UnifiedOcrPage>
}

/**
 * Result class for efficient OCR change detection queries.
 */
data class MemoPageNeedingOcr(
    val date: String,
    val pageId: String,
    val updatedAt: Long
)
