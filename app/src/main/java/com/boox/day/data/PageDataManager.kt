package com.boox.day.data

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.FileObserver
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.ConcurrentHashMap
import com.boox.day.PERF_LOGS
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.getBackgroundType
import com.boox.day.data.db.isUndecodable
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.editor.drawing.RichTextConverter
import com.boox.day.data.model.BackgroundType
import com.boox.day.data.model.BackgroundType.AutoPdf.getPage
import com.boox.day.data.model.BackgroundType.CoverImage
import com.boox.day.data.model.BackgroundType.ImageRepeating
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.utils.loadPersistBitmap
import com.boox.day.editor.utils.persistBitmapFullAtomic
import com.boox.day.editor.utils.persistBitmapThumbnail
import com.boox.day.io.IN_IGNORED
import com.boox.day.io.fileObserverEventNames
import com.boox.day.io.loadBackgroundBitmap
import com.boox.day.io.waitForFileAvailable
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.showHint
import com.boox.day.ocr.TaskTextExtractor
import com.boox.day.utils.chunked
import com.onyx.android.sdk.extension.isNotNull
import com.onyx.android.sdk.extension.isNull
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max


// Save bitmap, to avoid loading from disk every time.
data class CachedBackground(val path: String, val pageNumber: Int, val scale: Float) {
    val id: String = keyOf(path, pageNumber)

    var bitmap: Bitmap? = loadBackgroundBitmap(path, pageNumber, scale)
    fun matches(filePath: String, pageNum: Int, targetScale: Float): Boolean {
        return path == filePath && pageNumber == pageNum && scale >= targetScale // Consider valid if our scale is larger
    }

    companion object {
        fun keyOf(path: String, pageNumber: Int): String {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest("$path#$pageNumber".toByteArray(Charsets.UTF_8))
            return bytes.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

// Cache manager companion object
object PageDataManager {
    val log = TaggedLog("PageDataManager")

    // Memory pressure callback registry for propagating trim events to repository caches
    private val memoryPressureCallbacks = mutableListOf<MemoryPressureCallback>()

    fun registerMemoryPressureCallback(callback: MemoryPressureCallback) {
        memoryPressureCallbacks.add(callback)
    }

    fun unregisterMemoryPressureCallback(callback: MemoryPressureCallback) {
        memoryPressureCallbacks.remove(callback)
    }

    private val strokes = LinkedHashMap<String, MutableList<Stroke>>()
    private var strokesById = LinkedHashMap<String, HashMap<String, Stroke>>()

    private val images = LinkedHashMap<String, MutableList<Image>>()
    private var imagesById = LinkedHashMap<String, HashMap<String, Image>>()

    private val textAnnotations = LinkedHashMap<String, MutableList<TextAnnotation>>()
    private var textAnnotationsById = LinkedHashMap<String, HashMap<String, TextAnnotation>>()

    private val attachments = LinkedHashMap<String, MutableList<Attachment>>()
    private var attachmentsById = LinkedHashMap<String, HashMap<String, Attachment>>()

    private val backgroundCache = LinkedHashMap<String, CachedBackground>()
    private val pageToBackgroundKey = HashMap<String, String>()

    // ════════════════════════════════════════════════════════════════════════
    // SIZE-AWARE BITMAP CACHE WITH HEAL SUPPORT (v3.0)
    // ════════════════════════════════════════════════════════════════════════

    data class BitmapCacheEntry(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val generation: Long,
        val cachedAt: Long,
        val isClean: Boolean
    )

    data class BitmapCacheKey(
        val pageId: String,
        val width: Int,
        val height: Int
    )

    private const val MAX_CACHE_ENTRIES = 6
    private const val HEAL_THRESHOLD_PX = 2
    private const val CACHE_ENABLED = true  // Toggle for instant rollback

    private val bitmapCache = LinkedHashMap<BitmapCacheKey, BitmapCacheEntry>(
        MAX_CACHE_ENTRIES,
        0.75f,
        true
    )
    private val bitmapCacheLock = Any()

    private val protectedPages = ConcurrentHashMap.newKeySet<String>()
    private val pageGenerations = ConcurrentHashMap<String, Long>()
    private val expectedDimensions = ConcurrentHashMap<String, Pair<Int, Int>>()

    // ════════════════════════════════════════════════════════════════════════
    // PREVIEW CACHE FOR FAST DAY SWITCHING (v1.0)
    // ════════════════════════════════════════════════════════════════════════
    // Stores immutable bitmaps loaded from disk for neighboring days.
    // This eliminates disk I/O delay when switching between days.
    // Unlike bitmapCache (which stores mutable active bitmaps), this cache
    // stores immutable previews that are COPIED to the active bitmap on use.

    private val previewCacheBytes = BitmapMemoryBudget.previewBytes(Runtime.getRuntime().maxMemory())
    private val pageCacheBytes = BitmapMemoryBudget.pageCacheBytes(Runtime.getRuntime().maxMemory())

    private val previewCache = object : android.util.LruCache<String, Bitmap>(previewCacheBytes) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Recycle evicted bitmaps to free memory immediately
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                oldValue.recycle()
                if (PERF_LOGS) log.d("PREVIEW_CACHE: Evicted and recycled bitmap for $key")
            }
        }

        override fun sizeOf(key: String?, value: Bitmap?): Int {
            // Full-resolution pages vary greatly between Go 6 and Note Max class displays.
            return value?.allocationByteCount ?: 0
        }
    }
    private val previewCacheLock = Any()

    /**
     * Get a preview bitmap from cache. Returns null if not cached.
     * The returned bitmap is IMMUTABLE and should be copied before drawing.
     */
    fun getPreviewBitmap(pageId: String): Bitmap? {
        synchronized(previewCacheLock) {
            val cached = previewCache.get(pageId)
            if (cached != null && !cached.isRecycled) {
                if (PERF_LOGS) log.d("PREVIEW_CACHE_HIT: $pageId")
                return cached
            }
            if (cached != null && cached.isRecycled) {
                previewCache.remove(pageId)
            }
            if (PERF_LOGS) log.d("PREVIEW_CACHE_MISS: $pageId")
            return null
        }
    }

    /**
     * Returns true if a valid preview bitmap is present in cache.
     */
    fun hasPreviewBitmap(pageId: String): Boolean {
        synchronized(previewCacheLock) {
            val cached = previewCache.get(pageId)
            if (cached != null && !cached.isRecycled) {
                return true
            }
            if (cached != null && cached.isRecycled) {
                previewCache.remove(pageId)
            }
            return false
        }
    }

    /**
     * Draws a cached preview onto the provided canvas while holding the cache lock.
     * Returns true if a preview was drawn.
     */
    fun drawPreviewToCanvas(
        pageId: String,
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int
    ): Boolean {
        if (targetWidth <= 0 || targetHeight <= 0) return false
        synchronized(previewCacheLock) {
            val cached = previewCache.get(pageId)
            if (cached == null || cached.isRecycled) {
                if (cached?.isRecycled == true) {
                    previewCache.remove(pageId)
                }
                if (PERF_LOGS) log.d("PREVIEW_CACHE_MISS: $pageId")
                return false
            }
            if (cached.width == targetWidth && cached.height == targetHeight) {
                canvas.drawBitmap(cached, 0f, 0f, Paint())
                if (PERF_LOGS) log.d("PREVIEW_CACHE_HIT: $pageId (exact)")
                return true
            }
            val srcRect = Rect(0, 0, cached.width, cached.height)
            val dstRect = Rect(0, 0, targetWidth, targetHeight)
            val paint = Paint().apply { isFilterBitmap = true }
            canvas.drawBitmap(cached, srcRect, dstRect, paint)
            if (PERF_LOGS) {
                log.d("PREVIEW_CACHE_HIT: $pageId scaled ${cached.width}x${cached.height} -> ${targetWidth}x${targetHeight}")
            }
            return true
        }
    }

    /**
     * Store a preview bitmap in cache.
     * The bitmap should be loaded from disk (immutable).
     */
    fun putPreviewBitmap(pageId: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) {
            log.w("PREVIEW_CACHE: Attempted to cache recycled bitmap for $pageId")
            return
        }
        if (bitmap.allocationByteCount > previewCacheBytes) {
            log.d("PREVIEW_CACHE: Page exceeds optional preview budget; loading it on demand")
            return
        }
        synchronized(previewCacheLock) {
            previewCache.put(pageId, bitmap)
            if (PERF_LOGS) log.d("PREVIEW_CACHE_STORE: $pageId (${bitmap.width}x${bitmap.height})")
        }
    }

    /**
     * Remove a specific page from preview cache (e.g., when content changes).
     */
    fun invalidatePreviewCache(pageId: String) {
        synchronized(previewCacheLock) {
            previewCache.remove(pageId)?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        if (PERF_LOGS) log.d("PREVIEW_CACHE_INVALIDATE: $pageId")
    }

    /**
     * Clear entire preview cache (e.g., on low memory).
     */
    fun clearPreviewCache() {
        synchronized(previewCacheLock) {
            previewCache.evictAll()
        }
        log.i("PREVIEW_CACHE: Cleared all entries")
    }

    @Volatile
    var isTransitioning: Boolean = false
        private set

    fun setTransitioning(transitioning: Boolean) {
        isTransitioning = transitioning
        if (PERF_LOGS) {
            log.d("TRANSITION: ${if (transitioning) "ENTER" else "EXIT"} - caching ${if (transitioning) "disabled" else "enabled"}")
        }
    }

    fun getPageGeneration(pageId: String): Long {
        return pageGenerations.getOrPut(pageId) { System.nanoTime() }
    }

    fun incrementPageGeneration(pageId: String): Long {
        return pageGenerations.compute(pageId) { _, current ->
            (current ?: 0L) + 1
        }!!
    }

    fun setExpectedDimensions(pageId: String, width: Int, height: Int) {
        expectedDimensions[pageId] = Pair(width, height)
    }

    fun protectPage(pageId: String) {
        protectedPages.add(pageId)
    }

    fun unprotectPage(pageId: String) {
        protectedPages.remove(pageId)
    }

    /**
     * Retrieves cached bitmap with exact match OR heals minor mismatches.
     *
     * Heal does NOT require generation match. Generation is for content staleness,
     * not size eligibility.
     *
     * @param pageId Page identifier
     * @param targetWidth Expected width from SurfaceView
     * @param targetHeight Expected height from SurfaceView
     * @param allowHeal True for native backgrounds, false for PDF/image (would show white band)
     * @return Bitmap at exact requested dimensions, or null if recreation needed
     */
    fun getOrHealBitmap(
        pageId: String,
        targetWidth: Int,
        targetHeight: Int,
        allowHeal: Boolean = true
    ): Bitmap? {
        if (!CACHE_ENABLED) return null

        synchronized(bitmapCacheLock) {
            // Step 1: Try exact match (fast path)
            val exactKey = BitmapCacheKey(pageId, targetWidth, targetHeight)
            bitmapCache[exactKey]?.let { entry ->
                if (!entry.bitmap.isRecycled && entry.bitmap.isMutable) {
                    if (PERF_LOGS) log.d("CACHE_HIT: Exact match for $pageId@${targetWidth}x${targetHeight}")
                    return entry.bitmap
                } else {
                    // Clean up invalid entry
                    bitmapCache.remove(exactKey)
                }
            }

            // Step 2: Find ANY valid bitmap for this pageId
            val anyMatch = bitmapCache.entries.firstOrNull { (key, entry) ->
                key.pageId == pageId &&
                    !entry.bitmap.isRecycled &&
                    entry.bitmap.isMutable
            }

            if (anyMatch == null) {
                if (PERF_LOGS) log.d("CACHE_MISS: No valid entry for $pageId")
                return null
            }

            val cached = anyMatch.value.bitmap
            val widthDiff = abs(cached.width - targetWidth)
            val heightDiff = abs(cached.height - targetHeight)

            // Step 3: Check if heal is allowed and dimensions are within threshold
            if (!allowHeal) {
                if (PERF_LOGS) log.d("CACHE_MISS: Heal disabled for $pageId (non-native background)")
                return null
            }

            if (widthDiff <= HEAL_THRESHOLD_PX && heightDiff <= HEAL_THRESHOLD_PX) {
                log.i("CACHE_HEAL: Healing $pageId from ${cached.width}x${cached.height} to ${targetWidth}x${targetHeight}")
                return healBitmap(cached, targetWidth, targetHeight, pageId, anyMatch.key)
            }

            // Step 4: Major mismatch - recreation needed
            log.i("CACHE_MISS: Major mismatch for $pageId (${cached.width}x${cached.height} vs ${targetWidth}x${targetHeight}, diff=${widthDiff}x${heightDiff})")
            return null
        }
    }

    /**
     * Heals a cached bitmap to target dimensions.
     * Gap pixels filled with WHITE (invisible on e-ink).
     */
    private fun healBitmap(
        cached: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        pageId: String,
        oldKey: BitmapCacheKey
    ): Bitmap {
        // Create new bitmap at target dimensions
        val healed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(healed)

        // Fill with WHITE to prevent black artifacts in gap
        canvas.drawColor(Color.WHITE)

        // Draw cached content at origin (1:1, no scaling)
        canvas.drawBitmap(cached, 0f, 0f, null)

        // Update cache: remove old entry, add healed entry
        bitmapCache.remove(oldKey)

        val newKey = BitmapCacheKey(pageId, targetWidth, targetHeight)
        val currentGen = getPageGeneration(pageId)
        val newEntry = BitmapCacheEntry(
            bitmap = healed,
            width = targetWidth,
            height = targetHeight,
            generation = currentGen,
            cachedAt = System.currentTimeMillis(),
            isClean = !isTransitioning
        )
        bitmapCache[newKey] = newEntry

        // Evict if over limit
        evictIfNeeded()

        if (PERF_LOGS) log.d("CACHE_HEALED: $pageId now ${targetWidth}x${targetHeight}, gen=$currentGen")
        return healed
    }

    private fun evictIfNeeded() {
        while (bitmapCache.size > MAX_CACHE_ENTRIES || bitmapCache.values.sumOf {
                if (it.bitmap.isRecycled) 0L else it.bitmap.allocationByteCount.toLong()
            } > pageCacheBytes) {
            // Find oldest non-protected entry (LinkedHashMap maintains insertion/access order).
            // A page whose image still waits for the pen to rest keeps its bitmap until saved.
            val victim = bitmapCache.entries.firstOrNull { (key, _) ->
                !protectedPages.contains(key.pageId) && !hasPendingPersist(key.pageId)
            }
            if (victim != null) {
                log.d("CACHE_EVICT: Removing ${victim.key.pageId}@${victim.key.width}x${victim.key.height}")
                bitmapCache.remove(victim.key)
            } else {
                // All entries are protected, cannot evict
                log.w("CACHE_EVICT: All ${bitmapCache.size} entries protected, cannot evict")
                break
            }
        }
    }

    /**
     * Caches bitmap with full validation.
     *
     * Requires explicit expected dimensions.
     *
     * @param pageId Page identifier
     * @param bitmap Bitmap to cache
     * @param expectedWidth Expected width from surface sync
     * @param expectedHeight Expected height from surface sync
     * @return True if cached, false if rejected
     */
    fun cacheBitmapValidated(
        pageId: String,
        bitmap: Bitmap,
        expectedWidth: Int,
        expectedHeight: Int
    ): Boolean {
        if (!CACHE_ENABLED) return false

        // Reject if transitioning (Layer 3)
        if (isTransitioning) {
            if (PERF_LOGS) log.d("CACHE_REJECT: Transition in progress for $pageId")
            return false
        }

        // Validate bitmap state
        if (bitmap.isRecycled) {
            log.w("CACHE_REJECT: Bitmap recycled for $pageId")
            return false
        }
        if (!bitmap.isMutable) {
            log.w("CACHE_REJECT: Bitmap immutable for $pageId")
            return false
        }

        // Validate dimensions match expected (Layer 1)
        if (bitmap.width != expectedWidth || bitmap.height != expectedHeight) {
            log.w("CACHE_REJECT: Size mismatch for $pageId: bitmap=${bitmap.width}x${bitmap.height}, expected=${expectedWidth}x${expectedHeight}")
            return false
        }

        val key = BitmapCacheKey(pageId, expectedWidth, expectedHeight)
        val generation = getPageGeneration(pageId)
        val entry = BitmapCacheEntry(
            bitmap = bitmap,
            width = expectedWidth,
            height = expectedHeight,
            generation = generation,
            cachedAt = System.currentTimeMillis(),
            isClean = true  // Clean because we checked isTransitioning above
        )

        synchronized(bitmapCacheLock) {
            // Remove any stale entries for this pageId with different dimensions
            val staleKeys = bitmapCache.keys.filter { it.pageId == pageId && it != key }
            staleKeys.forEach { staleKey ->
                if (PERF_LOGS) log.d("CACHE_EVICT: Removing stale ${staleKey.width}x${staleKey.height} for $pageId")
                bitmapCache.remove(staleKey)
            }

            bitmapCache[key] = entry
            evictIfNeeded()
        }

        if (PERF_LOGS) log.d("CACHE_STORE: $pageId@${expectedWidth}x${expectedHeight} gen=$generation")
        return true
    }

    /**
     * DEPRECATED: Use cacheBitmapValidated() with explicit dimensions.
     * This wrapper exists for gradual migration but logs warnings.
     */
    @Deprecated("Use cacheBitmapValidated with explicit dimensions", ReplaceWith("cacheBitmapValidated(pageId, bitmap, width, height)"))
    fun cacheBitmap(pageId: String, bitmap: Bitmap): Boolean {
        val expected = expectedDimensions[pageId]
        if (expected == null) {
            log.w("CACHE_WARN: cacheBitmap() called without expectedDimensions for $pageId - use cacheBitmapValidated()")
            return false
        }
        return cacheBitmapValidated(pageId, bitmap, expected.first, expected.second)
    }

    /**
     * Gets cache entry with full metadata (for validation checks).
     */
    fun getCacheEntry(pageId: String): BitmapCacheEntry? {
        synchronized(bitmapCacheLock) {
            return bitmapCache.entries.firstOrNull { (key, entry) ->
                key.pageId == pageId &&
                    !entry.bitmap.isRecycled
            }?.value
        }
    }

    /**
     * Returns any cached bitmap for the page, regardless of size.
     * Used for persistence and OCR where exact dimensions don't matter.
     *
     * Enforces isClean check.
     */
    fun getCachedBitmapAnySize(pageId: String): Bitmap? {
        if (!CACHE_ENABLED) return null

        synchronized(bitmapCacheLock) {
            val entry = bitmapCache.entries.firstOrNull { (key, entry) ->
                key.pageId == pageId &&
                    !entry.bitmap.isRecycled &&
                    entry.bitmap.isMutable &&
                    entry.isClean
            }

            if (entry != null) {
                return entry.value.bitmap
            }

            // Check if there's a dirty entry we're rejecting
            val dirtyEntry = bitmapCache.entries.firstOrNull { (key, entry) ->
                key.pageId == pageId && !entry.isClean
            }
            if (dirtyEntry != null) {
                log.d("CACHE_SKIP: Found dirty entry for $pageId (isClean=false)")
            }

            return null
        }
    }

    /**
     * Gets bitmap for exact dimensions (used by PageView init).
     * Unlike getOrHealBitmap, this does NOT heal - just exact match.
     */
    fun getCachedBitmapExact(pageId: String, width: Int, height: Int): Bitmap? {
        if (!CACHE_ENABLED) return null

        synchronized(bitmapCacheLock) {
            val key = BitmapCacheKey(pageId, width, height)
            val entry = bitmapCache[key]

            if (entry != null && !entry.bitmap.isRecycled && entry.bitmap.isMutable) {
                return entry.bitmap
            }

            // Clean up invalid entry
            if (entry != null) {
                bitmapCache.remove(key)
            }

            return null
        }
    }

    // observe background file changes
    // fileObservers: filename to observer
    // fileToPages: filename to files with this file
    private val fileObservers = mutableMapOf<String, FileObserver>()
    private val fileToPages = mutableMapOf<String, MutableSet<String>>()
    val invalidateFileFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    // PERFORMANCE FIX: Changed from mutableStateMapOf to ConcurrentHashMap
    // mutableStateMapOf are Compose state objects that can ONLY be modified from the main thread
    // These were being modified from background threads in reduceCache()/removePage(), causing:
    // "IllegalStateException: Unsupported concurrent change during composition"
    // ConcurrentHashMap is thread-safe and doesn't trigger Compose recomposition
    private var pageHigh = ConcurrentHashMap<String, Int>()
    private var pageScroll = ConcurrentHashMap<String, Offset>()

    // On change, we need to adjust stroke size.
    // Phase 3 (Oct 2026): read by the bitmap persister off the main thread
    private var pageZoom = java.util.concurrent.ConcurrentHashMap<String, Float>()

    @Volatile
    private var currentPage = ""  // Kept for backward compatibility

    private val accessLock = Any() // Lock for accessing Images, Strokes, Backgrounds & derived
    private var entrySizeMB = LinkedHashMap<String, Int>()

    private val jobLock = Mutex()
    private val dataLoadingJobs = mutableMapOf<String, Job>()
    val dataLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        startFileInvalidationCollector()
    }

    /**
     * Suspends until the page is done loading (if it is being loaded).
     * Logs an error and returns if no job is present or job is cancelled.
     * Throws if no job is present or job is cancelled.
     */
    private suspend fun waitForPageLoad(pageId: String) {
        val job = jobLock.withLock { dataLoadingJobs[pageId] }
        if (job == null || job.isCancelled) {
            log.e("Illegal state: Job missing or cancelled for $pageId.")
            showHint("Illegal state: Job: ${job}.")
            return
        }
        job.join()
        if (!validatePageDataLoaded(pageId)) log.e("illegal state: after loading page, it is still not loaded correctly")
    }

    /**
     * Returns the existing loading Job for the page, or starts and returns a new one.
     * Locking is handled internally.
     */
    private suspend fun getOrStartLoadingJob(
        appRepository: AppRepository, pageId: String, bookId: String?
    ): Job {
        log.d("getOrStartLoadingJob($pageId)")
        //             PageDataManager.ensureMemoryAvailable(15)
        return jobLock.withLock {
            val existing = dataLoadingJobs[pageId]
            when {
                existing?.isActive == true -> {
                    log.d("Page($pageId) is already loading")
                    existing
                }

                existing?.isCompleted == true -> {
                    log.d("Page($pageId) already in memory, stroke number ${strokes[pageId]?.size}")
                    existing
                }

                existing == null || existing.isCancelled -> {
                    // Cancel any previous job, without current, next and previous page
                    if (bookId.isNotNull())
                        cancelUnnecessaryLoading(appRepository, pageId, bookId)
                    log.d("starting loading of the Page($pageId)")
                    if (existing.isNull() && areListInitialized(pageId)) log.e("Illegal state: Page($pageId) already in memory, but job is null.")
                    val newJob = dataLoadingScope.launch {
                        loadPageFromDb(appRepository, this, pageId)
                    }
                    dataLoadingJobs[pageId] = newJob
                    newJob
                }

                else -> error("Unexpected job state, for Page($pageId)")
            }
        }
    }

    /**
     * Ensures that the page is loaded; suspends until load is finished.
     */
    suspend fun requestPageLoadJoin(
        appRepository: AppRepository, pageId: String, bookId: String?
    ) {
        log.d("requestPageLoadJoin($pageId)")
        getOrStartLoadingJob(appRepository, pageId, bookId).join()
    }

    private fun cancelUnnecessaryLoading(
        appRepository: AppRepository,
        pageId: String,
        bookId: String
    ) {
        log.d("Canceling unnecessary loading of the Page($pageId)")
        val nextPageId =
            appRepository.getNextPageIdFromBookAndPage(pageId = pageId, notebookId = bookId)
        val prevPageId =
            appRepository.getPreviousPageIdFromBookAndPage(pageId = pageId, notebookId = bookId)

        cancelLoadingPages(
            ignoredPageIds =
                listOfNotNull(nextPageId, prevPageId, pageId).distinct()
        )
    }

    fun cacheNeighbors(appRepository: AppRepository, pageId: String, bookId: String) {

        // Only attempt to cache neighbors if we have memory to spare.
        if (!hasEnoughMemory(15)) return
        try {
            // Cache next page if not already cached
            val nextPageId =
                appRepository.getNextPageIdFromBookAndPage(pageId = pageId, notebookId = bookId)
            log.d("Caching next page $nextPageId")

            nextPageId?.let { nextPage ->
                requestPageLoad(appRepository, nextPage)
            }
            if (hasEnoughMemory(15)) {
                // Cache previous page if not already cached
                val prevPageId =
                    appRepository.getPreviousPageIdFromBookAndPage(
                        pageId = pageId,
                        notebookId = bookId
                    )
                log.d("Caching prev page $prevPageId")

                prevPageId?.let { prevPage ->
                    requestPageLoad(appRepository, prevPage)
                }
            }
        } catch (e: CancellationException) {
            log.i("Caching was cancelled: ${e.message}")
        } catch (e: Exception) {
            // All other unexpected exceptions
            log.e("Error caching neighbor pages", e)
            showHint("Error encountered while caching neighbors", duration = 5000)

        }

    }

    // ════════════════════════════════════════════════════════════════════════
    // PREVIEW BITMAP PRELOADING FOR FAST DAY SWITCHING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Preloads preview bitmaps for neighboring days to eliminate disk I/O delay on day switch.
     *
     * Priority order:
     * 1. Current day, other tab (for fast tab switching)
     * 2. Tomorrow/yesterday, Memos pages (for fast day switching)
     * 3. Tomorrow/yesterday, Tasks pages (if taskRepository provided)
     *
     * @param context Android context for loading bitmaps
     * @param currentDate Current date in "yyyy-MM-dd" format
     * @param currentTabPageId Page ID of the currently active tab
     * @param otherTabPageId Page ID of the other tab on current day (null if not available)
     * @param dayPageRepository Repository for looking up neighboring days' Memos page IDs
     * @param taskRepository Optional repository for looking up neighboring days' Tasks page IDs
     */
    fun preloadNeighboringPreviews(
        context: Context,
        currentDate: String,
        currentTabPageId: String,
        otherTabPageId: String?,
        dayPageRepository: com.boox.day.data.db.DayPageRepository,
        taskRepository: TaskRepository? = null
    ) {
        if (!hasEnoughMemory(10)) {
            log.d("PRELOAD: Skipping - low memory")
            return
        }

        dataLoadingScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var loaded = 0

            try {
                // PRIORITY 1: Other tab on current day (for fast tab switching)
                otherTabPageId?.let { pageId ->
                    if (preloadSingleBitmap(context, pageId)) loaded++
                }

                // PRIORITY 2: Neighboring days (yesterday and tomorrow)
                val neighboringDates = listOf(
                    getYesterdayDate(currentDate),
                    getTomorrowDate(currentDate)
                )

                for (date in neighboringDates) {
                    if (date == null) continue
                    if (!hasEnoughMemory(10)) {
                        log.d("PRELOAD: Stopping - memory pressure")
                        break
                    }

                    // Load Memos page (instant lookup - no DB call)
                    val memosPageId = dayPageRepository.getCachedPageIdForDate(date)
                    memosPageId?.let { pageId ->
                        if (preloadSingleBitmap(context, pageId)) loaded++
                    }

                    // Load Tasks page if repository provided (instant lookup - no DB call)
                    taskRepository?.getCachedTaskPageIdForDate(date)?.let { pageId ->
                        if (preloadSingleBitmap(context, pageId)) loaded++
                    }
                }

                if (PERF_LOGS) {
                    log.i("PRELOAD: Loaded $loaded previews in ${System.currentTimeMillis() - startTime}ms")
                }
            } catch (e: Exception) {
                log.e("PRELOAD: Error preloading previews", e)
            }
        }
    }

    /**
     * Preloads a single bitmap into the preview cache.
     * @return true if successfully loaded and cached, false otherwise
     */
    private fun preloadSingleBitmap(context: Context, pageId: String): Boolean {
        // Skip if already in preview cache
        if (hasPreviewBitmap(pageId)) {
            return false
        }

        // Load from disk (this is the slow operation we're doing in background)
        val bitmap = loadPersistBitmap(context, pageId, null, null, false)
        if (bitmap != null && !bitmap.isRecycled) {
            putPreviewBitmap(pageId, bitmap)
            return true
        }
        return false
    }

    /**
     * Get yesterday's date string from current date.
     */
    private fun getYesterdayDate(currentDate: String): String? {
        return try {
            val date = java.time.LocalDate.parse(currentDate)
            date.minusDays(1).toString()
        } catch (e: Exception) {
            log.w("PRELOAD: Failed to parse date $currentDate")
            null
        }
    }

    /**
     * Get tomorrow's date string from current date.
     */
    private fun getTomorrowDate(currentDate: String): String? {
        return try {
            val date = java.time.LocalDate.parse(currentDate)
            date.plusDays(1).toString()
        } catch (e: Exception) {
            log.w("PRELOAD: Failed to parse date $currentDate")
            null
        }
    }

    /**
     * Requests that the given page is loaded, but doesn't wait.
     * If already loading, is a no-op.
     */
    fun requestPageLoad(
        appRepository: AppRepository, pageId: String
    ) {
        dataLoadingScope.launch {
            getOrStartLoadingJob(appRepository, pageId, null)
        }
    }

    private fun preLoadBackground(appRepository: AppRepository, pageId: String) {
        val pageFromDb = appRepository.pageRepository.getById(pageId) ?: return
        val backgroundType = pageFromDb.getBackgroundType()
        val background = pageFromDb.background
        val pageNumber = when (backgroundType) {
            is BackgroundType.Pdf -> backgroundType.page
            is BackgroundType.AutoPdf -> backgroundType.getPage(
                appRepository, pageFromDb.notebookId, pageId
            ) ?: return

            BackgroundType.Native -> return
            BackgroundType.Image, ImageRepeating, CoverImage -> -1
        }
        val value = CachedBackground(background, pageNumber, 1f)
        log.i("Preloaded background: $value")
        val observeBg = appRepository.isObservable(pageFromDb.notebookId)
        setBackground(pageId, value, observeBg)
    }

    private suspend fun loadPageFromDb(
        appRepository: AppRepository, coroutineScope: CoroutineScope, pageId: String
    ) {
        try {
            log.d("Loading page $pageId")
//            sleep(5000)
            coroutineScope.launch {
                log.d("Preloading background for page $pageId")
                // A background that fails to load must not fail the page load (and crash the app).
                try {
                    preLoadBackground(appRepository, pageId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.e("Preloading background for page $pageId failed", e)
                }
            }

            // PAGE LOAD GUARD (A7, Oct 2026): this job runs on dataLoadingScope, which has no
            // exception handler - an exception escaping it crashes the app. Run the parallel DB
            // fetches in a nested scope so a failing one (e.g. the page row is gone) is caught
            // here, and fetch everything before caching anything, so a failed load leaves no
            // half-loaded page behind. The failed job is forgotten so the page can load again.
            val fetched = try {
                kotlinx.coroutines.coroutineScope {
                    val strokesDeferred = async { appRepository.pageRepository.getWithStrokeByIdSuspend(pageId) }
                    val imagesDeferred = async { appRepository.pageRepository.getWithImageById(pageId) }
                    val annotationsDeferred = async { appRepository.textAnnotationRepository.getByPageId(pageId) }
                    val attachmentsDeferred = async { appRepository.attachmentRepository.getByPageId(pageId) }
                    FetchedPage(
                        strokes = strokesDeferred.await().strokes,
                        images = imagesDeferred.await().images,
                        textAnnotations = annotationsDeferred.await(),
                        attachments = attachmentsDeferred.await()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("PAGE_LOAD_FAILED: page $pageId was not loaded", e)
                jobLock.withLock { dataLoadingJobs.remove(pageId) }
                return
            }

            // Strokes whose stored points can't be decoded are left out; their rows stay untouched.
            val (strokesToShow, undecodable) = fetched.strokes.partition { !it.isUndecodable }
            if (undecodable.isNotEmpty()) {
                log.e("PAGE_LOAD: skipped ${undecodable.size} undecodable stroke(s) on page $pageId: ${undecodable.map { it.id }}")
            }
            cacheStrokes(pageId, strokesToShow)
            cacheImages(pageId, fetched.images)
            cacheTextAnnotations(pageId, fetched.textAnnotations)
            cacheAttachments(pageId, fetched.attachments)

            recomputeHeight(pageId)
            indexImages(coroutineScope, pageId)
            indexStrokes(coroutineScope, pageId)
            indexTextAnnotations(coroutineScope, pageId)
            indexAttachments(coroutineScope, pageId)
            calculateMemoryUsage(pageId, 1)
        } catch (e: CancellationException) {
            log.w("Loading of page $pageId was cancelled.")
            if (!validatePageDataLoaded(pageId)) removePage(pageId)
            throw e  // rethrow cancellation
        } finally {
            log.d("Loaded page $pageId")
        }

    }

    private data class FetchedPage(
        val strokes: List<Stroke>,
        val images: List<Image>,
        val textAnnotations: List<TextAnnotation>,
        val attachments: List<Attachment>
    )


    /**
     * - Verifies loaded data presence.
     * - Tries to peek job state without suspending (tryLock).
     * - If inconsistent, logs a warning, clears the page, and returns false.
     *   (Call the overload below to also trigger reload.)
     */
    fun validatePageDataLoaded(pageId: String): Boolean {
        // 1) Snapshot job state non-suspending
        val jobSnapshot: Job? = if (jobLock.tryLock()) {
            try {
                dataLoadingJobs[pageId]
            } finally {
                jobLock.unlock()
            }
        } else {
            // Could not acquire lock without suspending; treat as unknown
            log.d("isPAgeLoaded: Couldn't obtain job status.")
            null
        }
        if (jobSnapshot?.isActive == true) {
            log.d("isPageLoaded: Still loading page($pageId).")
            return false
        }
        // if its canceled or null, we consider that data are not loaded
        val jobDone = jobSnapshot?.isCompleted ?: false

        // 2) Snapshot data state
        val dataLoaded = areListInitialized(pageId)

        // 3) Reconcile: if they disagree, warn and clear
        if (jobSnapshot.isNotNull() && dataLoaded != jobDone) {
            log.e("Inconsistent state for page($pageId): dataLoaded=$dataLoaded, jobDone=$jobDone, job=$jobSnapshot")
            showHint("Fixing inconsistent page state: $pageId")
            dataLoadingScope.launch {
                // Cancel/remove any job for this page
                jobLock.withLock {
                    dataLoadingJobs.remove(pageId)?.cancel()
                }
                // Drop partial data
                removePage(pageId)
            }
            return false
        }
        return dataLoaded
    }

    private fun areListInitialized(pageId: String): Boolean {
        return synchronized(accessLock) {
            strokes.containsKey(pageId) && images.containsKey(pageId) && entrySizeMB.containsKey(
                pageId
            )
        }
    }

    val saveTopic = MutableSharedFlow<String>()

    // A page image is saved once its edits have paused this long (Oct 2026). The old 500 ms throttle
    // saved on the first stroke and then about twice a second while writing: each save copies the
    // ~10 MB page bitmap and encodes a full PNG and a JPEG (~200 ms of CPU, about a quarter of a
    // core while writing on the Go 10.3), and it could skip the last state. Readers flush first.
    private const val PERSIST_QUIET_MS = 2_000L
    @Volatile
    private var persistDebouncer: PerKeyDebouncer? = null

    // CRITICAL FIX: Ensure only ONE collector is running globally
    // Previously, every PageView init called collectAndPersistBitmapsBatch(),
    // creating multiple collectors that all processed the same emissions concurrently
    @Volatile
    private var collectorStarted = false

    fun collectAndPersistBitmapsBatch(
        context: Context, scope: CoroutineScope
    ) {
        // Only start ONE collector globally - subsequent calls are no-ops
        if (collectorStarted) {
            log.d("Persist collector already running, skipping duplicate start")
            return
        }
        collectorStarted = true
        log.i("Starting persist collector (single instance)")

        val debouncer = PerKeyDebouncer(scope + Dispatchers.IO, PERSIST_QUIET_MS) { pageId ->
            persistPageBitmap(context, pageId)
        }
        persistDebouncer = debouncer
        scope.launch(Dispatchers.IO) {
            saveTopic.collect { pageId -> debouncer.schedule(pageId) }
        }
    }

    /** A page image still waiting for the pen to rest is saved now, so a reader gets every stroke. */
    fun flushPendingPersist(pageId: String) {
        persistDebouncer?.flush(pageId)
    }

    /** Saves every waiting page image: the app is going to the background and may be killed. */
    fun flushAllPendingPersists() {
        val debouncer = persistDebouncer ?: return
        dataLoadingScope.launch(Dispatchers.IO) { debouncer.flushAll() }
    }

    /** The page is being deleted: its waiting image must not be written back. */
    fun cancelPendingPersist(pageId: String) {
        persistDebouncer?.cancel(pageId)
    }

    private fun hasPendingPersist(pageId: String): Boolean = persistDebouncer?.isPending(pageId) == true

    private fun persistPageBitmap(context: Context, pageId: String) {
        if (PagePersistenceGate.isDeleting(pageId)) return

        // Skip during transition (Layer 3), and save once it has settled
        if (isTransitioning) {
            log.d("PERSIST_SKIP: Transition in progress for $pageId, will retry")
            persistDebouncer?.schedule(pageId)
            return
        }

        // Get entry and validate (Layer 4 - isClean check)
        val entry = getCacheEntry(pageId)
        if (entry == null || entry.bitmap.isRecycled) {
            log.d("PERSIST_SKIP: No valid entry for $pageId")
            return
        }

        if (!entry.isClean) {
            log.d("PERSIST_SKIP: Entry not clean for $pageId")
            return
        }

        // Create snapshot (Layer 4)
        val snapshot: Bitmap? = try {
            synchronized(entry.bitmap) {
                entry.bitmap.copy(entry.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            }
        } catch (e: Exception) {
            log.e("PERSIST_SKIP: Snapshot failed for $pageId: ${e.message}")
            null
        }

        if (snapshot == null) return

        val currentZoomLevel = pageZoom[pageId]
        val currentScroll = pageScroll[pageId]

        if (PERF_LOGS) log.i("PERSIST: Persisting SNAPSHOT for $pageId")

        try {
            persistBitmapFullAtomic(context, snapshot, pageId, currentScroll, currentZoomLevel)
            persistBitmapThumbnail(context, snapshot, pageId)

            // Trigger Digital Ink extraction for Task pages (debounced, fire-and-forget)
            val density = context.resources.displayMetrics.density
            TaskTextExtractor.getInstanceOrNull()?.onPageChanged(pageId, density)
        } finally {
            snapshot.recycle()
        }
    }

    /**
     * FORCE PERSIST: Persists bitmap immediately without deduplication check.
     * Used during page disposal to ensure the final state is always saved.
     * This prevents the bug where rapid day switching could skip the final persist,
     * causing strokes to appear "lost" even though they were saved to the database.
     */
    fun forcePersistBitmap(context: Context, pageId: String) {
        // Step 1: Check transition state
        if (isTransitioning) {
            log.d("PERSIST_SKIP: Transition in progress for $pageId")
            return
        }

        // Step 2: Get entry and validate
        val entry = getCacheEntry(pageId)
        if (entry == null) {
            log.d("PERSIST_SKIP: No cache entry for $pageId")
            return
        }

        if (!entry.isClean) {
            log.w("PERSIST_SKIP: Entry not clean for $pageId (captured during transition)")
            return
        }

        if (entry.bitmap.isRecycled) {
            log.d("PERSIST_SKIP: Bitmap recycled for $pageId")
            return
        }

        // Step 3: Create immutable snapshot (Layer 4)
        val snapshot: Bitmap? = try {
            synchronized(entry.bitmap) {
                entry.bitmap.copy(entry.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            }
        } catch (e: Exception) {
            log.e("PERSIST_SKIP: Snapshot creation failed for $pageId: ${e.message}")
            null
        }

        if (snapshot == null) {
            return
        }

        val currentZoomLevel = pageZoom[pageId]
        val currentScroll = pageScroll[pageId]

        log.i("PERSIST: Force persisting SNAPSHOT for $pageId")

        // This save supersedes one still waiting for the pen to rest
        persistDebouncer?.cancel(pageId)

        try {
            persistBitmapFullAtomic(context, snapshot, pageId, currentScroll, currentZoomLevel)
            persistBitmapThumbnail(context, snapshot, pageId)

            // Trigger Digital Ink extraction for Task pages (debounced, fire-and-forget)
            val density = context.resources.displayMetrics.density
            TaskTextExtractor.getInstanceOrNull()?.onPageChanged(pageId, density)
        } finally {
            snapshot.recycle()
        }
    }

    fun setPage(pageId: String) {
        val startTime = System.currentTimeMillis()
        currentPage = pageId
        protectedPages.add(pageId)
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 5) {
            log.w("PERF_BLOCK: setPage($pageId) took ${elapsed}ms on ${Thread.currentThread().name}")
        }
    }

    /**
     * Protect both Memos and Tasks pages from cache eviction.
     * Used by PersistentCanvasHost to keep both tab pages in memory during tab switches.
     *
     * @param memosPageId The current Memos tab page ID
     * @param tasksPageId The current Tasks tab page ID
     */
    fun setDualActivePages(memosPageId: String, tasksPageId: String) {
        val startTime = System.currentTimeMillis()
        currentPage = memosPageId  // Backward compatibility

        // Update protection (add-before-remove to avoid empty window)
        val oldPages = protectedPages.toSet()
        protectedPages.add(memosPageId)
        protectedPages.add(tasksPageId)
        oldPages.filter { it != memosPageId && it != tasksPageId }
            .forEach { protectedPages.remove(it) }

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 5) {
            log.w("PERF_BLOCK: setDualActivePages took ${elapsed}ms")
        }
        if (PERF_LOGS) {
            log.d("PERF: Protected dual pages: memos=$memosPageId, tasks=$tasksPageId")
        }
    }

    /**
     * Additively protect pages without clearing existing protection.
     * Use during transitions when we want to add protection without removing others.
     *
     * @param pageIds Variable number of page IDs to protect
     */
    fun protectPages(vararg pageIds: String) {
        pageIds.forEach { protectedPages.add(it) }
        log.d("PERF: Protected pages: ${pageIds.toList()}, total protected=${protectedPages.size}")
    }

    /**
     * PERF_DEBUG: Log all cache sizes to help diagnose memory leaks
     */
    fun logCacheSizes(context: String) {
        synchronized(accessLock) {
            log.i(
                "PERF_DEBUG: $context - strokes=${strokes.size}, images=${images.size}, " +
                    "textAnnotations=${textAnnotations.size}, backgroundCache=${backgroundCache.size}, " +
                    "pageHigh=${pageHigh.size}, pageScroll=${pageScroll.size}, pageZoom=${pageZoom.size}, " +
                    "bitmapCache=${bitmapCache.size}, protectedPages=${protectedPages.size}, currentPage=$currentPage"
            )
        }
    }

    /**
     * Release a page from protection, allowing it to be evicted.
     * Call this when a canvas/PageView is disposed.
     */
    fun releasePage(pageId: String) {
        val startTime = System.currentTimeMillis()
        protectedPages.remove(pageId)
        val elapsed = System.currentTimeMillis() - startTime
        if (PERF_LOGS) {
            log.d("PERF_DEBUG: releasePage($pageId) took ${elapsed}ms, protectedPages=${protectedPages.size}")
        }
    }

    fun getCachedBitmap(pageId: String): Bitmap? {
        return getCachedBitmapAnySize(pageId)
    }

    fun getPageHeight(pageId: String): Int? = pageHigh[pageId]
    fun setPageHeight(pageId: String, height: Int) {
        pageHigh[pageId] = height
    }

    fun recomputeHeight(pageId: String): Int {
        synchronized(accessLock) {
            if (strokes[pageId].isNullOrEmpty()) {
                return SCREEN_HEIGHT
            }
            val maxStrokeBottom = strokes[pageId]!!.maxOf { it.bottom }.plus(50)
            pageHigh[pageId] = max(maxStrokeBottom.toInt(), SCREEN_HEIGHT)
            return pageHigh[pageId]!!
        }
    }

    /**
     * Clear all cached data for a page (strokes, images, etc.)
     */
    fun clearPageData(pageId: String) {
        synchronized(accessLock) {
            strokes[pageId]?.clear()
            strokesById[pageId]?.clear()
            images[pageId]?.clear()
            imagesById[pageId]?.clear()
            textAnnotations[pageId]?.clear()
            textAnnotationsById[pageId]?.clear()
            attachments[pageId]?.clear()
            attachmentsById[pageId]?.clear()
            pageHigh[pageId] = SCREEN_HEIGHT
            synchronized(bitmapCacheLock) {
                val keysToRemove = bitmapCache.keys.filter { it.pageId == pageId }
                keysToRemove.forEach { bitmapCache.remove(it) }
            }
        }
    }

    /**
     * Invalidate page cache so it will be reloaded from DB on next access.
     * Use this after adding data directly to DB (e.g., TextAnnotations for memo headers).
     */
    fun invalidatePageCache(pageId: String) {
        synchronized(accessLock) {
            strokes.remove(pageId)
            strokesById.remove(pageId)
            images.remove(pageId)
            imagesById.remove(pageId)
            textAnnotations.remove(pageId)
            textAnnotationsById.remove(pageId)
            attachments.remove(pageId)
            attachmentsById.remove(pageId)
                pageHigh.remove(pageId)
                synchronized(bitmapCacheLock) {
                    val keysToRemove = bitmapCache.keys.filter { it.pageId == pageId }
                    keysToRemove.forEach { bitmapCache.remove(it) }
                }
                pageScroll.remove(pageId)
                pageZoom.remove(pageId)
            }
        // Also clear the loading job SYNCHRONOUSLY to avoid race condition
        // where new PageView sees old "completed" job but data is gone
        kotlinx.coroutines.runBlocking {
            jobLock.withLock {
                dataLoadingJobs.remove(pageId)?.cancel()
            }
        }
        log.d("Invalidated cache for page $pageId - will reload from DB")
    }

    fun computeWidth(pageId: String): Int {
        synchronized(accessLock) {
            if (strokes[pageId].isNullOrEmpty()) {
                return SCREEN_WIDTH
            }
            val maxStrokeRight = strokes[pageId]!!.maxOf { it.right }.plus(50)
            return max(maxStrokeRight.toInt(), SCREEN_WIDTH)
        }
    }

    fun getPageScroll(pageId: String): Offset? = pageScroll[pageId]
    fun setPageScroll(pageId: String, scroll: Offset) {
        pageScroll[pageId] = scroll
    }

    fun getPageZoom(pageId: String): Float = pageZoom.getOrPut(pageId) { 1f }
    fun setPageZoom(pageId: String, zoom: Float) {
        pageZoom[pageId] = zoom
    }


    /** A snapshot of [pageId]'s strokes (Phase 3: copied under the lock — the live list changes on other threads). */
    fun getStrokes(pageId: String): List<Stroke> = synchronized(accessLock) {
        strokes[pageId]?.toList() ?: emptyList()
    }

    /**
     * Check if a page has been loaded into the cache.
     * Returns true if the page exists in the cache (even if empty).
     * Returns false if the page has never been loaded.
     *
     * CRITICAL: Used by findFirstEmptyRow() to determine if DB fallback is needed.
     */
    fun isPageLoaded(pageId: String): Boolean {
        synchronized(accessLock) {
            return pageId in strokes
        }
    }


    fun setStrokes(pageId: String, strokes: List<Stroke>) {
        synchronized(accessLock) {
            this.strokes[pageId] = strokes.toMutableList()
        }
    }

    /**
     * Append strokes to a page the caller has open (PageView), under the lock (Phase 3, Oct 2026).
     * Unlike [addStrokes] it also starts the page's list when there is none yet. IDs already present
     * are not added twice.
     */
    fun appendStrokes(pageId: String, strokesToAdd: List<Stroke>) {
        if (strokesToAdd.isEmpty()) return
        synchronized(accessLock) {
            val list = strokes.getOrPut(pageId) { mutableListOf() }
            val map = strokesById.getOrPut(pageId) { hashMapOf() }
            val presentIds = list.mapTo(HashSet(list.size)) { it.id }
            val newStrokes = strokesToAdd.filter { presentIds.add(it.id) }
            list.addAll(newStrokes)
            newStrokes.forEach { map[it.id] = it }
        }
    }

    /** Replace erased ink and its lookup entries under the same cache lock. */
    fun replaceStrokes(pageId: String, sourceIds: List<String>, replacements: List<Stroke>) {
        require(replacements.all { it.pageId == pageId })
        synchronized(accessLock) {
            removeStrokes(sourceIds, pageId)
            appendStrokes(pageId, replacements)
        }
    }

    fun removeStroke(pageId: String, strokeId: String) {
        synchronized(accessLock) {
            strokes[pageId]?.removeAll { it.id == strokeId }
            strokesById[pageId]?.remove(strokeId)
        }
    }

    /**
     * Remove multiple strokes from the cache by their IDs.
     * Used when migrating strokes between pages (e.g., task move).
     */
    fun removeStrokes(strokeIds: List<String>, pageId: String) {
        if (strokeIds.isEmpty()) return
        synchronized(accessLock) {
            val strokeIdSet = strokeIds.toSet()
            strokes[pageId]?.removeAll { it.id in strokeIdSet }
            strokesById[pageId]?.let { map ->
                strokeIds.forEach { map.remove(it) }
            }
        }
    }

    /**
     * Add strokes to the cache.
     * Used when migrating strokes between pages (e.g., task move).
     *
     * T6 (Oct 2026): a page that was never loaded is skipped — its strokes are already in the DB
     * and arrive when the page is opened. Creating a partial list here made that load append the
     * DB copy (duplicated ink) and made [isPageLoaded] report a partial page as loaded.
     * Strokes already in the cache (same ID) are not added twice.
     */
    fun addStrokes(strokesToAdd: List<Stroke>, pageId: String) {
        if (strokesToAdd.isEmpty()) return
        val loading = isLoadInProgress(pageId)
        synchronized(accessLock) {
            if (!strokes.containsKey(pageId) && !loading) return
            val list = strokes.getOrPut(pageId) { mutableListOf() }
            val map = strokesById.getOrPut(pageId) { hashMapOf() }
            val presentIds = list.mapTo(HashSet(list.size)) { it.id }
            val newStrokes = strokesToAdd.filter { presentIds.add(it.id) }
            list.addAll(newStrokes)
            newStrokes.forEach { map[it.id] = it }
        }
    }

    /** True while [pageId]'s load job runs (or when the job map is busy — then assume it does). */
    private fun isLoadInProgress(pageId: String): Boolean {
        if (!jobLock.tryLock()) return true
        return try {
            dataLoadingJobs[pageId]?.isActive == true
        } finally {
            jobLock.unlock()
        }
    }

    fun getImages(pageId: String): List<Image> = images[pageId] ?: emptyList()

    fun setImages(pageId: String, images: List<Image>) {
        this.images[pageId] = images.toMutableList()
    }

    fun getTextAnnotations(pageId: String): List<TextAnnotation> = textAnnotations[pageId] ?: emptyList()

    fun setTextAnnotations(pageId: String, annotations: List<TextAnnotation>) {
        this.textAnnotations[pageId] = annotations.toMutableList()
    }

    fun addTextAnnotation(pageId: String, annotation: TextAnnotation) {
        synchronized(accessLock) {
            val list = textAnnotations.getOrPut(pageId) { mutableListOf() }
            list.add(annotation)
            textAnnotationsById.getOrPut(pageId) { hashMapOf() }[annotation.id] = annotation
        }
    }

    fun removeTextAnnotation(pageId: String, annotationId: String) {
        synchronized(accessLock) {
            textAnnotations[pageId]?.removeAll { it.id == annotationId }
            textAnnotationsById[pageId]?.remove(annotationId)
        }
    }

    // Attachment methods
    fun getAttachments(pageId: String): List<Attachment> = attachments[pageId] ?: emptyList()

    fun setAttachments(pageId: String, attachmentList: List<Attachment>) {
        this.attachments[pageId] = attachmentList.toMutableList()
    }

    fun addAttachment(pageId: String, attachment: Attachment) {
        synchronized(accessLock) {
            val list = attachments.getOrPut(pageId) { mutableListOf() }
            list.add(attachment)
            attachmentsById.getOrPut(pageId) { hashMapOf() }[attachment.id] = attachment
        }
    }

    fun removeAttachment(pageId: String, attachmentId: String) {
        synchronized(accessLock) {
            attachments[pageId]?.removeAll { it.id == attachmentId }
            attachmentsById[pageId]?.remove(attachmentId)
        }
    }

    fun removeAttachments(pageId: String, attachmentIds: List<String>) {
        synchronized(accessLock) {
            attachments[pageId]?.removeAll { attachmentIds.contains(it.id) }
            attachmentIds.forEach { attachmentsById[pageId]?.remove(it) }
        }
    }

    fun getAttachment(attachmentId: String, pageId: String): Attachment? {
        return attachmentsById[pageId]?.get(attachmentId)
    }

    // Phase 3 (Oct 2026): under the lock, and no crash when the page was evicted meanwhile
    fun indexStrokes(scope: CoroutineScope, pageId: String) {
        scope.launch {
            synchronized(accessLock) {
                val list = strokes[pageId] ?: return@synchronized
                strokesById[pageId] = HashMap(list.associateBy { it.id })
            }
        }
    }

    fun indexImages(scope: CoroutineScope, pageId: String) {
        scope.launch {
            synchronized(accessLock) {
                val list = images[pageId] ?: return@synchronized
                imagesById[pageId] = HashMap(list.associateBy { it.id })
            }
        }
    }

    fun getStrokes(strokeIds: List<String>, pageId: String): List<Stroke?> = synchronized(accessLock) {
        strokeIds.map { s -> strokesById[pageId]?.get(s) }
    }

    fun getImage(imageId: String, pageId: String): Image? {
        return imagesById[pageId]?.get(imageId)
    }

    fun getImages(imageIds: List<String>, pageId: String): List<Image?> {
        return imageIds.map { i -> imagesById[pageId]?.get(i) }
    }


    // Assuming Rect uses 'left', 'top', 'right', 'bottom'
    fun getImagesInRectangle(inPageCoordinates: Rect, id: String): List<Image>? {
        synchronized(accessLock) {
            if (!validatePageDataLoaded(id)) return null
            val imageList = images[id] ?: return emptyList()
            return imageList.filter { image ->
                image.x < inPageCoordinates.right && (image.x + image.width) > inPageCoordinates.left && image.y < inPageCoordinates.bottom && (image.y + image.height) > inPageCoordinates.top
            }
        }
    }

    fun getStrokesInRectangle(inPageCoordinates: Rect, id: String): List<Stroke>? {
        synchronized(accessLock) {
            if (!validatePageDataLoaded(id)) return null
            val strokeList = strokes[id] ?: return emptyList()
            return strokeList.filter { stroke ->
                stroke.right > inPageCoordinates.left && stroke.left < inPageCoordinates.right && stroke.bottom > inPageCoordinates.top && stroke.top < inPageCoordinates.bottom
            }
        }
    }

    fun getAttachmentsInRectangle(inPageCoordinates: Rect, id: String): List<Attachment>? {
        synchronized(accessLock) {
            if (!validatePageDataLoaded(id)) return null
            val attachmentList = attachments[id] ?: return emptyList()
            return attachmentList.filter { attachment ->
                attachment.x < inPageCoordinates.right && (attachment.x + attachment.width) > inPageCoordinates.left && attachment.y < inPageCoordinates.bottom && (attachment.y + attachment.height) > inPageCoordinates.top
            }
        }
    }

    private fun cacheStrokes(pageId: String, strokes: List<Stroke>) {
        synchronized(accessLock) {
            if (!this.strokes.containsKey(pageId)) {
                this.strokes[pageId] = strokes.toMutableList()
            } else {
                log.d("Joining strokes drawn during page loading and existing strokes")
                // T6: merge by stroke ID — strokes added while loading are also in the DB copy
                val existing = this.strokes[pageId]
                if (existing != null) {
                    val presentIds = existing.mapTo(HashSet(existing.size)) { it.id }
                    existing.addAll(strokes.filter { presentIds.add(it.id) })
                }
            }
        }
    }

    private fun cacheImages(pageId: String, images: List<Image>) {
        synchronized(accessLock) {
            if (!this.images.containsKey(pageId)) {
                this.images[pageId] = images.toMutableList()
            } else {
                log.d("Joining images drawn during page loading and existing images")
                this.images[pageId]?.addAll(images)
            }
        }
    }

    private fun cacheTextAnnotations(pageId: String, annotations: List<TextAnnotation>) {
        synchronized(accessLock) {
            // Migrate annotations if needed before caching
            val migratedAnnotations = annotations.map { migrateAnnotationIfNeeded(it) }

            if (!this.textAnnotations.containsKey(pageId)) {
                this.textAnnotations[pageId] = migratedAnnotations.toMutableList()
            } else {
                log.d("Joining text annotations during page loading and existing annotations")
                this.textAnnotations[pageId]?.addAll(migratedAnnotations)
            }
        }
    }

    private fun cacheAttachments(pageId: String, attachmentList: List<Attachment>) {
        synchronized(accessLock) {
            if (!this.attachments.containsKey(pageId)) {
                this.attachments[pageId] = attachmentList.toMutableList()
            } else {
                log.d("Joining attachments during page loading and existing attachments")
                this.attachments[pageId]?.addAll(attachmentList)
            }
        }
    }

    /**
     * Migrate a TextAnnotation to the new rich text format if needed.
     * This is a non-destructive operation - original content is preserved.
     *
     * Migration rules:
     * 1. If spans is already populated, return as-is (already migrated)
     * 2. If sourceFormat is "markdown" and has markdown patterns, convert to spans
     * 3. If content has markdown patterns (legacy AI conversion), convert to spans
     */
    private fun migrateAnnotationIfNeeded(annotation: TextAnnotation): TextAnnotation {
        // Already has spans - no migration needed
        if (!annotation.spans.isNullOrEmpty()) {
            return annotation
        }

        val content = annotation.content

        // Check if content has markdown patterns that should be converted
        val hasMarkdownPatterns = content.contains(Regex("^#{1,3}\\s", RegexOption.MULTILINE)) ||
                content.contains("**") ||
                content.contains(Regex("^[-*]\\s", RegexOption.MULTILINE)) ||
                content.contains(Regex("^\\d+\\.\\s", RegexOption.MULTILINE))

        // Check if it's from AI conversion (sourceType or sourceFormat indicates markdown)
        val isFromAiOrMarkdown = annotation.sourceType == "ai_conversion" ||
                annotation.sourceFormat == "markdown"

        // Only migrate if there are markdown patterns to convert
        if (!hasMarkdownPatterns) {
            return annotation
        }

        // Convert markdown to spans
        log.d("Migrating TextAnnotation ${annotation.id}: converting markdown to spans")
        val (plainText, spans) = RichTextConverter.markdownToSpans(content)
        val spansJson = RichTextSpanSerializer.encode(spans)

        return annotation.copy(
            content = plainText,
            spans = spansJson,
            sourceFormat = if (isFromAiOrMarkdown) "markdown" else annotation.sourceFormat
        )
    }

    fun indexTextAnnotations(scope: CoroutineScope, pageId: String) {
        scope.launch {
            val annotations = textAnnotations[pageId] ?: return@launch
            textAnnotationsById[pageId] = hashMapOf(*annotations.map { a -> a.id to a }.toTypedArray())
        }
    }

    fun indexAttachments(scope: CoroutineScope, pageId: String) {
        scope.launch {
            val attachmentList = attachments[pageId] ?: return@launch
            attachmentsById[pageId] = hashMapOf(*attachmentList.map { a -> a.id to a }.toTypedArray())
        }
    }

    fun setBackground(pageId: String, background: CachedBackground, observe: Boolean) {
        synchronized(accessLock) {

            // Merge/upgrade cache: if we already have an entry for this background,
            // keep the one with higher scale (higher quality).
            val existing = backgroundCache[background.id]
            if (existing == null || background.scale > existing.scale) {
                backgroundCache[background.id] = background
                log.d("Cached background set: id=${background.id} scale=${background.scale}")
            } else {
                log.d("Cached background exists with equal/higher scale; reusing id=${existing.id} scale=${existing.scale}")
            }

            // Link this page to the background key
            pageToBackgroundKey[pageId] = background.id

            if (observe) observeBackgroundFile(pageId, background.path)
        }
    }

    /**
     * Retrieves the cached background for a specific page.
     *
     * If a background is associated with the page and is present in the cache, it returns the
     * [CachedBackground] object.
     *
     * If no background is found for the given `pageId`, it returns a default, empty
     * [CachedBackground] object to prevent null pointer exceptions downstream.
     *
     * @param pageId The unique identifier of the page for which to retrieve the background.
     * @return The [CachedBackground] associated with the page, or a default empty instance if not found.
     */
    fun getBackground(pageId: String): CachedBackground {
        return synchronized(accessLock) {
            val key = pageToBackgroundKey[pageId]
            val bg = if (key != null) backgroundCache[key] else null
            log.d("Background for page $pageId: $bg")
            bg ?: CachedBackground("", 0, 1.0f)
        }
    }

    /**
     * Start observing a background file for changes.
     * Registers the pageId to the file, and launches a FileObserver if not already present.
     */
    private fun observeBackgroundFile(pageId: String, filePath: String) {
        synchronized(fileObservers) {
            fileToPages.getOrPut(filePath) { mutableSetOf() }.add(pageId)
            if (fileObservers.containsKey(filePath)) return // Already observing this file

            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                log.w("Cannot observe background file: $filePath does not exist or is not readable")
                return
            }
            val mask = (FileObserver.CREATE or
                    FileObserver.DELETE or
                    FileObserver.DELETE_SELF or
                    FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_TO or
                    FileObserver.MOVE_SELF)

            // Launch a FileObserver for this file
            val observer = object : FileObserver(file, mask) {
                override fun onEvent(event: Int, path: String?) {
                    dataLoadingScope.launch {
                        if(event == IN_IGNORED)
                            return@launch
                        val eventString = fileObserverEventNames(event)

                        log.d("Background file changed: $filePath [event=$eventString]")
                        if (event == DELETE || event == DELETE_SELF) {
                            log.d("Background file deleted.")
                            synchronized(fileObservers) {
                                fileObservers.remove(filePath)?.stopWatching()
                            }
                            if (!waitForFileAvailable(filePath)) {
                                log.w("File changed, but does not exist: $filePath")
                                SnackState.globalSnackFlow.emit(
                                    SnackConf(text = "Background does not exist", duration = SnackConf.DURATION_ERROR)
                                )
                                return@launch
                            } else
                                observeBackgroundFile(pageId, filePath)
                        }


                        invalidateFileFlow.emit(filePath)
                    }
                }
            }
            observer.startWatching()
            fileObservers[filePath] = observer
        }
    }


    /**
     * Starts the collector to process file invalidation events.
     * Uses chunked batching to process all events received in a 10ms window.
     */
    fun startFileInvalidationCollector() {
        dataLoadingScope.launch {
            invalidateFileFlow.chunked(10) // Batch events every 20ms
                .collect { filePathBatch ->
                    val uniqueFilePaths = filePathBatch.distinct()
                    if (uniqueFilePaths.isEmpty()) return@collect
                    log.i("Persisting batch of fileChanges: $uniqueFilePaths")
                    for (filePath in uniqueFilePaths) {
                        // Invalidate all pages that use this file
                        fileToPages[filePath]?.forEach { pid ->
                            invalidateBackground(pid)
                            if (pid == currentPage) {
                                DrawCanvas.Companion.forceUpdate.emit(null)
                                SnackState.globalSnackFlow.emit(
                                    SnackConf(text = "Background file changed", duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Stop observing the background file for the given page.
     * Cleans up observers if no more pages are using the file.
     */
    private fun stopObservingBackground(pageId: String) {
        synchronized(fileObservers) {
            val iterator = fileToPages.entries.iterator()
            while (iterator.hasNext()) {
                val (filePath, pageIds) = iterator.next()
                if (pageIds.remove(pageId) && pageIds.isEmpty()) {
                    fileObservers.remove(filePath)?.stopWatching()
                    iterator.remove()
                }
            }
        }
    }

    private fun invalidateBackground(pageId: String) {
        synchronized(accessLock) {
            // Remove page->bg mapping and drop bg if no other page references it
            val key = pageToBackgroundKey.remove(pageId)
            if (key != null) {
                val stillUsed = pageToBackgroundKey.values.any { it == key }
                if (!stillUsed) {
                    backgroundCache.remove(key)
                    log.d("Invalidated background cache key=$key (no remaining pages)")
                } else {
                    log.d("Unlinked page $pageId from background key=$key (still used elsewhere)")
                }
            }
            synchronized(bitmapCacheLock) {
                val keysToRemove = bitmapCache.keys.filter { it.pageId == pageId }
                keysToRemove.forEach { bitmapCache.remove(it) }
            }
            log.d("Invalidated background cache for page: $pageId")
        }
    }

    fun updateOnExit(targetPageId: String) {
        log.i("Page exit, is page loaded: ${validatePageDataLoaded(targetPageId)}")
        if (validatePageDataLoaded(targetPageId)) {
            recomputeHeight(targetPageId)
            calculateMemoryUsage(targetPageId, 0)
            // TODO: if we exited the book, we should clear the cache.
        }
    }

    /** --- cleaning and memory management ---- **/

    @Volatile
    private var currentCacheSizeMB = 0

    fun removePage(pageId: String): Boolean {
        log.d("Removing page $pageId")
        // PERFORMANCE FIX: Check ALL protected pages, not just currentPage
        val isActive = protectedPages.contains(pageId)
        if (pageId == currentPage || isActive) {
            // This is expected during race conditions (e.g., app resume while cache cleanup runs)
            // The code correctly refuses to remove active pages - this is not a bug
            log.d("Skipping removal of active page: pageId=$pageId, currentPage=$currentPage, isActive=$isActive")
            return false
        }
        if (hasPendingPersist(pageId)) {
            // Its bitmap is needed for the save waiting for the pen to rest
            log.d("Skipping removal of page $pageId: image save pending")
            return false
        }
        synchronized(accessLock) {
            strokes.remove(pageId)
            images.remove(pageId)
            textAnnotations.remove(pageId)
            attachments.remove(pageId)
               pageHigh.remove(pageId)
               pageZoom.remove(pageId)
               pageScroll.remove(pageId)
               synchronized(bitmapCacheLock) {
                   val keysToRemove = bitmapCache.keys.filter { it.pageId == pageId }
                   keysToRemove.forEach { bitmapCache.remove(it) }
               }
               strokesById.remove(pageId)
               imagesById.remove(pageId)
               textAnnotationsById.remove(pageId)
            attachmentsById.remove(pageId)
            dataLoadingJobs.remove(pageId)
            currentCacheSizeMB -= entrySizeMB[pageId] ?: 0
            entrySizeMB.remove(pageId)

            // Unlink and possibly remove background
            val key = pageToBackgroundKey.remove(pageId)
            if (key != null && !pageToBackgroundKey.values.any { it == key }) {
                backgroundCache.remove(key)
            }
            stopObservingBackground(pageId)
        }
        return true
    }


    /**
     * Cancels and removes currently loading page, given by [pageId].
     */
    fun cancelLoadingPage(pageId: String) {
        dataLoadingScope.launch {
            log.d("Cancelling loading page: pageId=$pageId")
            jobLock.withLock {
                if (dataLoadingJobs[pageId]?.isActive == true) {
                    dataLoadingJobs[pageId]?.cancel()
                    removePage(pageId)
                }
            }
        }
    }


    /**
     * Cancels and removes all currently loading pages, optionally ignoring a specified list of pages -- [ignoredPageIds].
     */
    fun cancelLoadingPages(ignoredPageIds: List<String> = listOf()) {
        dataLoadingScope.launch {
            log.d("Cancelling loading pages, ignoring: $ignoredPageIds")
            val toCancel: List<String>
            jobLock.withLock {
                // Collect all pageIds with jobs that are not finished
                toCancel = dataLoadingJobs.filter { (_, job) ->
                    job.isActive
                }.map { (pageId, _) -> pageId }
                // Cancel and remove pages outside the lock
                for (pageId in toCancel) {
                    if (ignoredPageIds.contains(pageId)) continue
                    val job = jobLock.withLock { dataLoadingJobs[pageId] }
                    if (job != null && job.isActive) {
                        job.cancel()
                        log.d("Cancelled job for page $pageId")
                    }
                    log.d("Cancelling page $pageId")
                    removePage(pageId)
                }
            }
        }
    }

    fun clearAllPages() {
        dataLoadingScope.launch {
            log.d("Clearing loaded pages")
            jobLock.withLock {
                // Collect all pageIds with jobs that are not finished
                dataLoadingJobs.forEach { (id, _) ->
                    log.d("Clearing page $id, requested by clearAllPages")
                    removePage(id)
                }
            }
        }
    }

    fun ensureMemoryAvailable(requiredMb: Int): Boolean {
        return when {
            hasEnoughMemory(requiredMb) -> true
            else -> ensureMemoryCapacity(requiredMb)
        }
    }

    fun getUsedMemory(): Int {
        return currentCacheSizeMB
    }

    fun reduceCache(maxPages: Int) {
        val startTime = System.currentTimeMillis()
        log.d("reduceCache($maxPages), strokes.size=${strokes.size}, protectedPages=${protectedPages.size}")

        // FIX: Don't hold accessLock for entire operation - this blocks main thread!
        // Step 1: Quickly collect pages to remove (with brief lock)
        val pagesToRemove: List<String>
        synchronized(accessLock) {
            val candidates = mutableListOf<String>()
            for (pageId in strokes.keys) {
                if (strokes.size - candidates.size <= maxPages) break

                // Check against ALL protected pages, not just currentPage
                val isActive = protectedPages.contains(pageId)
                if (isActive || pageId == currentPage || hasPendingPersist(pageId)) {
                    continue // Skip active pages and pages whose image save is pending
                }
                candidates.add(pageId)
            }
            pagesToRemove = candidates.toList()
        }
        // Lock released here - main thread can now proceed if needed

        // Step 2: Remove pages one at a time (each acquires lock briefly)
        // This allows main thread to acquire lock between removals
        for (pageId in pagesToRemove) {
            log.d("Clearing page (oldest) $pageId, requested by reduceCache")
            if (!removePage(pageId)) {
                log.w("Skipped removing page $pageId (became active during cache cleanup)")
                break
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 50) {
            log.w("PERF_BLOCK: reduceCache took ${elapsed}ms to remove ${pagesToRemove.size} pages")
        }
    }

    // sign: if 1, add, if -1, remove, if 0 don't modify
    private fun calculateMemoryUsage(pageId: String, sign: Int = 1): Int {
        return synchronized(accessLock) {
            var totalBytes = 0L

            // 1. Calculate strokes memory
            strokes[pageId]?.let { strokeList ->
                totalBytes += strokeList.sumOf { stroke ->
                    // Stroke object base size (~120 bytes)
                    var strokeMemory = 120L
                    // Points memory (32 bytes per StrokePoint)
                    strokeMemory += stroke.points.size * 32L
                    // Bounding box (4 floats = 16 bytes)
                    strokeMemory += 16L
                    strokeMemory
                }
            }

            // 2. Calculate images memory (average 100 bytes per image)
            totalBytes += images.size.times(100L)


            // 3. Calculate background memory
            backgroundCache[pageToBackgroundKey[pageId]]?.let { background ->
                background.bitmap?.let { bitmap ->
                    totalBytes += bitmap.allocationByteCount.toLong()
                }
                // Background metadata (approx 50 bytes)
                totalBytes += 50L
            }

            // 4. Calculate cached bitmap memory
            val bitmap = synchronized(bitmapCacheLock) {
                bitmapCache.entries.firstOrNull { it.key.pageId == pageId }?.value?.bitmap
            }
            bitmap?.let {
                if (!it.isRecycled) {
                    totalBytes += it.allocationByteCount.toLong()
                }
            }

            // 5. Add map entry overhead (approx 40 bytes per entry)
            totalBytes += 40L * 4 // 4 maps (strokes, images, backgrounds, bitmaps)

            // Convert to MB and update cache
            val memoryUsedMB = (totalBytes / (1024 * 1024)).toInt()
            entrySizeMB[pageId] = memoryUsedMB
            currentCacheSizeMB += memoryUsedMB * sign
            memoryUsedMB
        }
    }

    private fun clearAllCache() {
        freeMemory(0)
    }

    fun hasEnoughMemory(requiredMb: Int): Boolean {
        val availableMem = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()
        return availableMem > requiredMb * 1024 * 1024L
    }

    private fun ensureMemoryCapacity(requiredMb: Int): Boolean {
        val availableMem = ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime()
            .totalMemory()) / (1024 * 1024)).toInt()
        if (availableMem > requiredMb) return true
        val toFree = requiredMb - availableMem
        freeMemory(toFree)
        return hasEnoughMemory(requiredMb)
    }

    private fun freeMemory(cacheSizeLimit: Int): Boolean {
        log.d("freeMemory($cacheSizeLimit)")
        synchronized(accessLock) {
            // A page whose image save is pending keeps its bitmap until saved (a few seconds)
            val pagesToRemove = strokes.keys.filter { it != currentPage && !hasPendingPersist(it) }
            for (pageId in pagesToRemove) {
                if (currentCacheSizeMB <= cacheSizeLimit) break
                log.d("Clearing page (all except current) $pageId, requested by freeMemory")
                if(!removePage(pageId)) {
                    log.w("Skipped removing page $pageId (became active during memory cleanup)")
                    break
                }
            }
            currentCacheSizeMB = maxOf(0, currentCacheSizeMB)
            return currentCacheSizeMB <= cacheSizeLimit
        }
    }

    /**
     * BATTERY FIX: Stop all FileObservers when app goes to background.
     * FileObservers use kernel inotify watches which should be released when not needed.
     * They will be recreated when background images are loaded again on resume.
     */
    fun stopAllFileObservers() {
        synchronized(fileObservers) {
            log.d("Stopping all ${fileObservers.size} FileObservers (app going to background)")
            fileObservers.values.forEach { it.stopWatching() }
            fileObservers.clear()
            fileToPages.clear()
        }
    }

    fun registerComponentCallbacks(context: Context) {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                log.d("onTrimMemory: $level, currentCacheSizeMB: $currentCacheSizeMB")
                when (level) {
                    // for API <34
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        clearPreviewCache()
                        clearAllCache()
                    }
                    ComponentCallbacks2.TRIM_MEMORY_MODERATE -> freeMemory(32)
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                        clearPreviewCache()
                        freeMemory(64)
                    }
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                        clearPreviewCache()
                        freeMemory(128)
                    }
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> freeMemory(256)
                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> freeMemory(32)
                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> freeMemory(10)
                }
                // Notify registered callbacks for repository cache clearing
                memoryPressureCallbacks.forEach { it.onMemoryPressure(level) }
                log.d("after trim currentCacheSizeMB: $currentCacheSizeMB")
            }

            override fun onConfigurationChanged(newConfig: Configuration) {
                // No action needed for config changes
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                // Handle legacy low-memory callback (API < 14)
                clearAllCache()
            }
        })
    }
}
