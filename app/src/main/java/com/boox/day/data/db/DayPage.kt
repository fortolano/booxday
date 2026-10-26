package com.boox.day.data.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import android.util.Log
import com.boox.day.TAG
import com.boox.day.data.PagePersistenceGate
import java.util.Collections
import java.util.Date
import java.util.UUID

/**
 * DayPage maps a calendar date to one or more Pages.
 * This is the core data model for BooxDay - each day can have multiple pages of notes.
 * All stroke/image data is stored in the associated Page entities.
 */
@Entity
data class DayPage(
    @PrimaryKey val date: String,  // Format: "yyyy-MM-dd" (e.g., "2025-11-24")
    val pageIds: List<String> = listOf(),  // List of page IDs for this date
    val openPageId: String? = null,  // Currently open page for this date
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * Data class for page info within a day
 */
data class DayPageInfo(
    val date: String,
    val pageIds: List<String>,
    val openPageId: String?,
    val currentIndex: Int,
    val totalPages: Int
)

@Dao
interface DayPageDao {
    @Query("SELECT * FROM daypage WHERE date = :date")
    fun getByDate(date: String): DayPage?

    @Query("SELECT * FROM daypage WHERE date = :date")
    fun getByDateLive(date: String): LiveData<DayPage?>

    @Query("SELECT * FROM daypage ORDER BY date DESC")
    fun getAll(): LiveData<List<DayPage>>

    // Get all day pages synchronously (for backup export)
    @Query("SELECT * FROM daypage ORDER BY date DESC")
    fun getAllDayPages(): List<DayPage>

    @Query("SELECT * FROM daypage WHERE date LIKE :yearMonth || '%'")
    fun getAllForMonth(yearMonth: String): List<DayPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(dayPage: DayPage)

    @Update
    fun update(dayPage: DayPage)

    @Query("UPDATE daypage SET openPageId = :pageId, updatedAt = :updatedAt WHERE date = :date")
    fun setOpenPageId(date: String, pageId: String, updatedAt: Date = Date())

    @Query("UPDATE daypage SET pageIds = :pageIds, updatedAt = :updatedAt WHERE date = :date")
    fun setPageIds(date: String, pageIds: List<String>, updatedAt: Date = Date())

    @Query("DELETE FROM daypage WHERE date = :date")
    fun delete(date: String)

    // Find date by searching pageIds (uses LIKE since pageIds is stored as JSON list)
    @Query("SELECT date FROM daypage WHERE pageIds LIKE '%' || :pageId || '%' LIMIT 1")
    fun getDateByPageId(pageId: String): String?
}

class DayPageRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dayPageDao = db.dayPageDao()
    private val pageDao = db.pageDao()
    private val strokeDao = db.strokeDao()
    private val imageDao = db.ImageDao()
    private val textAnnotationDao = db.textAnnotationDao()

    companion object {
        private const val LOCAL_TAG = "DayPageRepo"
    }

    // PERFORMANCE: LRU cache for page IDs by date (saves DB queries on day switching)
    // Thread-safe: Wrapped with synchronizedMap for concurrent access from main/background threads
    private val pageIdCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
                return size > 60  // Cache ~2 months of page IDs
            }
        }
    )

    /**
     * Invalidate page ID cache for a specific date or all dates.
     * Call this when pages are created/deleted.
     */
    fun invalidatePageCache(date: String? = null) {
        if (date != null) {
            pageIdCache.remove(date)
        } else {
            pageIdCache.clear()
        }
    }

    /**
     * Non-suspending cache lookup for fast day switching.
     */
    fun getCachedPageIdForDate(date: String): String? {
        return pageIdCache[date]
    }

    /**
     * Pre-warm the page ID cache for all dates in a month.
     * Called when month changes to speed up day switching.
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     */
    fun preWarmCacheForMonth(yearMonth: String) {
        val start = System.currentTimeMillis()
        val dayPages = dayPageDao.getAllForMonth(yearMonth)
        dayPages.forEach { dayPage ->
            if (dayPage.pageIds.isNotEmpty()) {
                val pageId = dayPage.openPageId ?: dayPage.pageIds.first()
                pageIdCache[dayPage.date] = pageId
            }
        }
        Log.i(LOCAL_TAG, "PERF: preWarmCacheForMonth $yearMonth - cached ${dayPages.size} dates (${System.currentTimeMillis() - start}ms)")
    }

    /**
     * Get or create the first page for the given date.
     * If no page exists for this date, creates a new one.
     * Returns the page ID of the currently open page.
     */
    fun getOrCreatePageForDate(date: String): String {
        val start = System.currentTimeMillis()

        // PERFORMANCE: Check cache first
        pageIdCache[date]?.let { cachedPageId ->
            Log.i(LOCAL_TAG, "PERF: getOrCreatePageForDate CACHE HIT for $date (0ms)")
            return cachedPageId
        }

        val existing = dayPageDao.getByDate(date)
        if (existing != null && existing.pageIds.isNotEmpty()) {
            // Return the open page, or the first page if none is set
            val pageId = existing.openPageId ?: existing.pageIds.first()
            // Cache the result
            pageIdCache[date] = pageId
            Log.i(LOCAL_TAG, "PERF: getOrCreatePageForDate CACHE MISS (loaded) for $date (${System.currentTimeMillis() - start}ms)")
            return pageId
        }

        // Create new page for this date
        val newPage = Page(
            id = UUID.randomUUID().toString(),
            background = "blank",
            backgroundType = "native"
        )
        pageDao.create(newPage)

        // Create or update DayPage with this page
        val dayPage = DayPage(
            date = date,
            pageIds = listOf(newPage.id),
            openPageId = newPage.id
        )
        dayPageDao.insert(dayPage)

        // Cache the new page ID
        pageIdCache[date] = newPage.id
        Log.i(LOCAL_TAG, "PERF: getOrCreatePageForDate CACHE MISS (created) for $date (${System.currentTimeMillis() - start}ms)")
        return newPage.id
    }

    /**
     * Get page info for a date (for toolbar display)
     */
    fun getPageInfoForDate(date: String): DayPageInfo? {
        val dayPage = dayPageDao.getByDate(date) ?: return null
        if (dayPage.pageIds.isEmpty()) return null

        val openPageId = dayPage.openPageId ?: dayPage.pageIds.first()
        val currentIndex = dayPage.pageIds.indexOf(openPageId).coerceAtLeast(0)

        return DayPageInfo(
            date = date,
            pageIds = dayPage.pageIds,
            openPageId = openPageId,
            currentIndex = currentIndex,
            totalPages = dayPage.pageIds.size
        )
    }

    /**
     * Get page count for a date
     */
    fun getPageCount(date: String): Int {
        return dayPageDao.getByDate(date)?.pageIds?.size ?: 0
    }


    /**
     * Get current page index (0-based)
     */
    fun getOpenPageIndex(date: String): Int {
        val dayPage = dayPageDao.getByDate(date) ?: return 0
        val openPageId = dayPage.openPageId ?: return 0
        return dayPage.pageIds.indexOf(openPageId).coerceAtLeast(0)
    }

    /**
     * Create a new page for a date and add it at the end.
     * Returns the new page ID.
     *
     * @throws IllegalStateException if date doesn't exist in database
     */
    fun createNewPageForDate(date: String): String {
        val dayPage = dayPageDao.getByDate(date)
            ?: throw IllegalStateException("Cannot add page to non-existent date: $date")

        val newPage = Page(
            id = UUID.randomUUID().toString(),
            background = "blank",
            backgroundType = "native"
        )
        pageDao.create(newPage)

        val updatedPageIds = dayPage.pageIds + newPage.id
        dayPageDao.setPageIds(date, updatedPageIds)
        setOpenPageId(date, newPage.id)  // Use repository method to update BOTH DB and cache

        Log.i(TAG, "DayPageRepository: Created new page ${newPage.id} for date $date (now ${updatedPageIds.size} pages)")
        return newPage.id
    }

    /**
     * Set the currently open page for a date
     */
    fun setOpenPageId(date: String, pageId: String) {
        dayPageDao.setOpenPageId(date, pageId)
        // Update cache with new open page
        pageIdCache[date] = pageId
    }

    /**
     * Get page at specific index
     */
    fun getPageAtIndex(date: String, index: Int): String? {
        val dayPage = dayPageDao.getByDate(date) ?: return null
        return dayPage.pageIds.getOrNull(index)
    }

    /**
     * Get the next page ID for a date. Returns null if on the last page.
     */
    fun getNextPageId(date: String): String? {
        val dayPage = dayPageDao.getByDate(date) ?: return null
        val currentIndex = dayPage.pageIds.indexOf(dayPage.openPageId)
        if (currentIndex < 0 || currentIndex >= dayPage.pageIds.size - 1) return null
        return dayPage.pageIds[currentIndex + 1]
    }

    /**
     * Get the previous page ID for a date. Returns null if on the first page.
     */
    fun getPreviousPageId(date: String): String? {
        val dayPage = dayPageDao.getByDate(date) ?: return null
        val currentIndex = dayPage.pageIds.indexOf(dayPage.openPageId)
        if (currentIndex <= 0) return null
        return dayPage.pageIds[currentIndex - 1]
    }

    /**
     * Remove a page from a date.
     * Also deletes the Page entity and its strokes/images.
     * Returns the page ID to navigate to after deletion, or null if no pages left.
     */
    fun removePage(date: String, pageId: String): String? {
        val dayPage = dayPageDao.getByDate(date) ?: return null

        val currentIndex = dayPage.pageIds.indexOf(pageId)
        if (currentIndex < 0) return null

        return PagePersistenceGate.withDelete(pageId) {
            // Delete strokes, images, and text annotations for this page
            strokeDao.deleteAllForPage(pageId)
            imageDao.deleteAllForPage(pageId)
            textAnnotationDao.deleteAllForPage(pageId)

            // Delete the page entity
            pageDao.delete(pageId)

            // Remove from pageIds list
            val updatedPageIds = dayPage.pageIds.filterNot { it == pageId }

            if (updatedPageIds.isEmpty()) {
                // No pages left - delete the DayPage entirely
                dayPageDao.delete(date)
                Log.i(TAG, "DayPageRepository: Removed last page $pageId from date $date, deleted DayPage")
                return@withDelete null
            }

            // Determine new open page
            val newOpenPageId = when {
                currentIndex > 0 -> updatedPageIds[currentIndex - 1]  // Go to previous
                else -> updatedPageIds.first()  // Go to first
            }

            dayPageDao.setPageIds(date, updatedPageIds)
            setOpenPageId(date, newOpenPageId)  // Use repository method to update BOTH DB and cache

            Log.i(TAG, "DayPageRepository: Removed page $pageId from date $date, now ${updatedPageIds.size} pages")
            newOpenPageId
        }
    }

    /**
     * Reorder pages for a date.
     * @param date The date to reorder pages for
     * @param newPageIds The new ordered list of page IDs (must contain same IDs as current)
     */
    fun reorderPages(date: String, newPageIds: List<String>) {
        val dayPage = dayPageDao.getByDate(date) ?: return

        // Validate: new list must contain exactly the same page IDs
        if (dayPage.pageIds.toSet() != newPageIds.toSet()) {
            Log.e(LOCAL_TAG, "reorderPages: page IDs mismatch - aborting")
            return
        }

        dayPageDao.setPageIds(date, newPageIds)
        invalidatePageCache(date)
        Log.i(LOCAL_TAG, "Reordered pages for $date: $newPageIds")
    }

    /**
     * Clear all content from a page (strokes and images) without deleting the page
     */
    fun clearPageContent(pageId: String) {
        strokeDao.deleteAllForPage(pageId)
        imageDao.deleteAllForPage(pageId)
        textAnnotationDao.deleteAllForPage(pageId)
        Log.i(TAG, "DayPageRepository: Cleared content from page $pageId (strokes, images, text annotations)")
    }

    /**
     * Check if a page has any content (strokes, images, or text annotations)
     * PERFORMANCE: Uses single combined query instead of 3 sequential queries.
     */
    fun pageHasContent(pageId: String): Boolean {
        return strokeDao.pageHasAnyContent(pageId)
    }

    /**
     * Check if any page for a date has content
     */
    fun dateHasContent(date: String): Boolean {
        val dayPage = dayPageDao.getByDate(date) ?: return false
        return dayPage.pageIds.any { pageHasContent(it) }
    }

    fun getByDate(date: String): DayPage? {
        return dayPageDao.getByDate(date)
    }

    fun getByDateLive(date: String): LiveData<DayPage?> {
        return dayPageDao.getByDateLive(date)
    }

    /**
     * Get all dates with notes for a month.
     * Checks all pages for each date to see if any have content.
     * OPTIMIZED: Uses batch queries to avoid N+1 problem.
     */
    fun getDatesWithNotesForMonthSync(yearMonth: String): List<String> {
        val start = System.currentTimeMillis()
        val dayPages = dayPageDao.getAllForMonth(yearMonth)
        
        // 1. Collect all page IDs for the month
        val allPageIds = dayPages.flatMap { it.pageIds }.distinct()
        
        if (allPageIds.isEmpty()) return emptyList()

        // 2. Batch check for content (3 queries instead of N*3)
        // Split into chunks of 900 to avoid SQLite variable limit (999)
        val activePageIds = mutableSetOf<String>()
        val chunks = allPageIds.chunked(900)
        
        for (chunk in chunks) {
            val pagesWithStrokes = strokeDao.getPagesWithStrokes(chunk)
            val pagesWithImages = imageDao.getPagesWithImages(chunk)
            val pagesWithText = textAnnotationDao.getPagesWithTextAnnotations(chunk)
            
            activePageIds.addAll(pagesWithStrokes)
            activePageIds.addAll(pagesWithImages)
            activePageIds.addAll(pagesWithText)
        }
        
        // 3. Map back to dates
        val result = dayPages.filter { dayPage ->
            dayPage.pageIds.any { pageId -> activePageIds.contains(pageId) }
        }.map { it.date }
        
        Log.d(LOCAL_TAG, "PERF: getDatesWithNotesForMonthSync optimized - ${dayPages.size} days, ${allPageIds.size} pages -> ${result.size} active (${System.currentTimeMillis() - start}ms)")
        return result
    }

    fun delete(date: String) {
        // Delete all pages for this date first
        val dayPage = dayPageDao.getByDate(date)
        dayPage?.pageIds?.forEach { pageId ->
            strokeDao.deleteAllForPage(pageId)
            imageDao.deleteAllForPage(pageId)
            textAnnotationDao.deleteAllForPage(pageId)
            pageDao.delete(pageId)
        }
        dayPageDao.delete(date)
    }

    /**
     * Find the date that contains a specific page ID.
     * Returns null if the page is not part of any DayPage.
     */
    fun getDateByPageId(pageId: String): String? {
        return dayPageDao.getDateByPageId(pageId)
    }

    /**
     * Get the page index within a day (0-based).
     * Returns null if page is not found in any day.
     */
    fun getPageIndexForDay(pageId: String): Int? {
        val date = getDateByPageId(pageId) ?: return null
        val dayPage = dayPageDao.getByDate(date) ?: return null
        val index = dayPage.pageIds.indexOf(pageId)
        return if (index >= 0) index else null
    }
}
