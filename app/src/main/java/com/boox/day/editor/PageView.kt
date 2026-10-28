package com.boox.day.editor

import com.boox.day.editor.state.TextBoxState


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.AppRepository
import com.boox.day.data.CachedBackground
import com.boox.day.data.PageDataManager
import com.boox.day.data.PageDataManager.cacheNeighbors
import com.boox.day.data.PageDataManager.collectAndPersistBitmapsBatch
import com.boox.day.data.PageDataManager.updateOnExit
import com.boox.day.data.PagePersistenceGate
import com.boox.day.data.PagePersistenceWriter
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.getBackgroundType
import com.boox.day.data.model.BackgroundType
import com.boox.day.editor.drawing.drawBg
import com.boox.day.editor.drawing.drawOnCanvasFromPage
import com.boox.day.editor.state.ZOOM_SNAP_THRESHOLD
import com.boox.day.editor.utils.div
import com.boox.day.editor.utils.loadPersistBitmap
import com.boox.day.editor.utils.minus
import com.boox.day.editor.utils.plus
import com.boox.day.editor.utils.times
import com.boox.day.editor.utils.toIntOffset
import com.boox.day.ui.SnackState
import com.boox.day.ui.SnackState.Companion.logAndShowError
import com.boox.day.PERF_LOGS
import com.onyx.android.sdk.extension.isNotNull
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

const val OVERLAP = 2

/**
 * Manages the state and rendering of a single page within the editor.
 * @param currentPageId Id of page assigned to it.
 */
class PageView(
    val context: Context,
    val coroutineScope: CoroutineScope,
    var currentPageId: String,
    var viewWidth: Int,
    var viewHeight: Int,
    val snackManager: SnackState,
    // PERFORMANCE FIX: Accept AppRepository from caller instead of creating new instance
    // This eliminates ~10 repository instantiations per PageView creation
    val appRepository: AppRepository
) {
    // TODO: unify width height variable

    val log = TaggedLog("PageView")

    // Reference to TextBoxState for checking selected text annotations during draw
    // Set by EditorState after PageView is created
    var textBoxState: com.boox.day.editor.state.TextBoxState? = null

    private val logCache = TaggedLog("PageViewCache")

    private var loadingJob: Job? = null

    @Volatile
    var windowedBitmap = createBitmap(viewWidth, viewHeight)
        private set

    @Volatile
    var windowedCanvas = Canvas(windowedBitmap)
        private set

    /** TASKS-ONLY: Row indices (0-9) of completed tasks. Strokes in these rows
     *  render in completedStrokeColor. Empty for Memos pages (zero overhead). */
    @Volatile
    var completedRowIndices: Set<Int> = emptySet()

    /** Page whose load (loadPage) has not yet asked for its full redraw. That redraw reads
     *  [completedRowIndices], so another full redraw asked for meanwhile only repeats it. */
    @Volatile
    private var loadRedrawPendingPageId: String? = null

    /** True while the current page's own load is still going to redraw the whole canvas. */
    val isLoadRedrawPending: Boolean
        get() = loadRedrawPendingPageId == currentPageId

    // Surface sync state for validation
    @Volatile
    private var surfaceSyncedWidth: Int = 0
    @Volatile
    private var surfaceSyncedHeight: Int = 0
    @Volatile
    private var surfaceSynced: Boolean = false

    fun isSurfaceSynced(): Boolean = surfaceSynced

    fun getSyncedDimensions(): Pair<Int, Int>? {
        return if (surfaceSynced) Pair(surfaceSyncedWidth, surfaceSyncedHeight) else null
    }

    // Reusable bitmap for scroll operations to avoid allocations
    @Volatile
    private var reusableBitmap: Bitmap? = null

    //    var strokes = listOf<Stroke>()
    var strokes: List<Stroke>
        get() = PageDataManager.getStrokes(currentPageId)
        set(value) = PageDataManager.setStrokes(currentPageId, value)

    var images: List<Image>
        get() = PageDataManager.getImages(currentPageId)
        set(value) = PageDataManager.setImages(currentPageId, value)

    var textAnnotations: List<TextAnnotation>
        get() = PageDataManager.getTextAnnotations(currentPageId)
        set(value) = PageDataManager.setTextAnnotations(currentPageId, value)

    var attachments: List<Attachment>
        get() = PageDataManager.getAttachments(currentPageId)
        set(value) = PageDataManager.setAttachments(currentPageId, value)

    private var currentBackground: CachedBackground
        get() = PageDataManager.getBackground(currentPageId)
        set(value) {
            val observeBg = appRepository.isObservable(pageFromDb?.notebookId)
            PageDataManager.setBackground(currentPageId, value, observeBg)
        }

    // scroll is observed by ui, represents top left corner
    var scroll: Offset
        get() = PageDataManager.getPageScroll(currentPageId) ?: run {
            val value = Offset(0f, pageFromDb?.scroll?.toFloat() ?: 0f)
            PageDataManager.setPageScroll(currentPageId, value)
            value
        }
        set(value) {
            PageDataManager.setPageScroll(currentPageId, value)
        }

    val isTransformationAllowed: Boolean
        get() = when (pageFromDb?.backgroundType) {
            "native", null -> true
            "coverImage" -> false
            else -> true
        }

    // we need to observe zoom level, to adjust strokes size.
    val zoomLevel: MutableStateFlow<Float> = MutableStateFlow(PageDataManager.getPageZoom(currentPageId))

    var height: Int
        get() = PageDataManager.getPageHeight(currentPageId) ?: viewHeight
        set(value) {
            PageDataManager.setPageHeight(currentPageId, value)
        }


    var pageFromDb = appRepository.pageRepository.getById(currentPageId)

    private var dbStrokes = AppDatabase.getDatabase(context).strokeDao()
    private var dbImages = AppDatabase.getDatabase(context).ImageDao()

    val currentPageNumber: Int
        get() {
            // Day pages don't have a notebookId, return -1
            val notebookId = pageFromDb?.notebookId ?: return -1
            return try {
                appRepository.getPageNumber(notebookId, currentPageId)
            } catch (e: Exception) {
                logAndShowError(
                    "PageView.currentPageNumber",
                    "Error getting page number: ${e.message}"
                )
                -1
            }
        }


    /*
        If pageNumber is -1, its assumed that the background is image type.
     */
    fun getOrLoadBackground(filePath: String, pageNumber: Int, scale: Float): Bitmap? {
        if (!currentBackground.matches(filePath, pageNumber, scale))
        // 0.1 to avoid constant rerender on zoom.
            currentBackground = CachedBackground(filePath, pageNumber, scale + 0.1f)
        return currentBackground.bitmap
    }


    init {
        val initStart = System.currentTimeMillis()
        if (PERF_LOGS) {
            log.i("PERF: PageView init START at $initStart")
        }
        PageDataManager.setPage(currentPageId)
        PageDataManager.setExpectedDimensions(currentPageId, viewWidth, viewHeight)
        zoomLevel.value = PageDataManager.getPageZoom(currentPageId)
        PageDataManager.getCachedBitmapExact(currentPageId, viewWidth, viewHeight)?.let { cached ->
            if (PERF_LOGS) {
                log.i("PERF: PageView using cached bitmap")
            }
            windowedBitmap = cached
            windowedCanvas = Canvas(windowedBitmap)
            surfaceSyncedWidth = viewWidth
            surfaceSyncedHeight = viewHeight
            surfaceSynced = true
        } ?: run {
            if (PERF_LOGS) {
                log.i("PERF: PageView creating new bitmap")
            }
            val bitmapStart = System.currentTimeMillis()
            recreateCanvas()
            if (PERF_LOGS) {
                log.i("PERF: recreateCanvas took ${System.currentTimeMillis() - bitmapStart}ms")
            }
            // FIX: Set surfaceSynced with initial dimensions. The bitmap created by
            // recreateCanvas() has dimensions viewWidth x viewHeight, which are correct.
            // This prevents CACHE_SKIP during early operations before surfaceCreated fires.
            surfaceSyncedWidth = viewWidth
            surfaceSyncedHeight = viewHeight
            surfaceSynced = true
        }
        if (PERF_LOGS) {
            log.i("PERF: PageView init sync part took ${System.currentTimeMillis() - initStart}ms")
        }

        loadRedrawPendingPageId = currentPageId
        coroutineScope.launch {
//            DrawCanvas.refreshUiImmediately.emit(Unit)
            loadPage()
            log.d("Page loaded (Init with id: $currentPageId)")
            // PREVIEW SAVER LIFETIME FIX (Oct 2026): the persist collector is started only once per
            // process (collectorStarted), so it must not run on this composition-bound scope. When
            // MainScreen's scope was cancelled (activity recreation, e.g. a Boox DPI change), previews
            // and thumbnails silently stopped being saved until the app was force-restarted.
            // dataLoadingScope lives for the whole process; applicationContext avoids leaking the Activity.
            collectAndPersistBitmapsBatch(context.applicationContext, PageDataManager.dataLoadingScope)
        }
    }

    /**
     * Switches the `PageView` to display a different page.
     * **It doesn't notify the UI about the change.**
     *
     * This function handles the entire process of transitioning from the current page to a new one specified by `newPageId`.
     * It performs the following steps:
     * 1.  Saves the state of the old page, including persisting its bitmap representation to disk.
     * 2.  Updates the internal `currentPageId` to `newPageId`.
     * 3.  Fetches the new page's data from the repository.
     * 4.  Updates the `PageDataManager` to the new page context.
     * 5.  Restores the zoom level for the new page.
     * 6.  Attempts to load a cached bitmap for the new page. If a cached bitmap exists and its dimensions
     *     match the current view, it's used directly. If dimensions differ, the canvas is resized.
     * 7.  If no cached bitmap is available, it creates a new bitmap and canvas from scratch.
     * 8.  Launches a coroutine to load the page's content (strokes, images) asynchronously and refreshes the UI.
     *
     * @param newPageId The unique identifier of the page to switch to.
     */
        fun changePage(newPageId: String) {
            val oldId = currentPageId

            currentPageId = newPageId
            loadRedrawPendingPageId = newPageId
            coroutineScope.launch {
                updateOnExit(oldId)
                persistBitmapDebounced(oldId)
            }
            pageFromDb = appRepository.pageRepository.getById(currentPageId)
            PageDataManager.setPage(newPageId)
            PageDataManager.setExpectedDimensions(newPageId, viewWidth, viewHeight)
            zoomLevel.value = PageDataManager.getPageZoom(currentPageId)
            PageDataManager.getCachedBitmapExact(newPageId, viewWidth, viewHeight)?.let { cached ->
                windowedBitmap = cached
                windowedCanvas = Canvas(windowedBitmap)
                // Mark synced dimensions
                surfaceSyncedWidth = viewWidth
                surfaceSyncedHeight = viewHeight
                surfaceSynced = true
                // Check if we have correct size of canvas
                if (windowedCanvas.width != viewWidth || windowedCanvas.height != viewHeight) {
                    updateCanvasDimensions()
                }
            } ?: run {
                // PREVIEW OVERWRITE FIX (Oct 2026): the outgoing bitmap is still referenced by the
                // old page's PageDataManager cache entry (bitmaps are cached by reference), and the
                // old page's persist runs after this swap. Reusing it here made the old page's saved
                // preview/thumbnail (read by OCR and thumbnails) capture this page's blank content.
                // Give the new page its own bitmap; recreateCanvas() then clears it and draws the
                // initial background/preview as before.
                windowedBitmap = createBitmap(viewWidth, viewHeight)
                windowedCanvas = Canvas(windowedBitmap)
                recreateCanvas()
                // FIX: Set surfaceSynced with current dimensions (same fix as init block)
                surfaceSyncedWidth = viewWidth
                surfaceSyncedHeight = viewHeight
                surfaceSynced = true
            }

            coroutineScope.launch {
                loadPage()
            }
    }

    private fun recreateCanvas() {
        // OPTIMIZATION: Reuse existing bitmap if dimensions match to avoid memory churn
        if (windowedBitmap.width != viewWidth || windowedBitmap.height != viewHeight) {
            windowedBitmap = createBitmap(viewWidth, viewHeight)
            windowedCanvas = Canvas(windowedBitmap)
        } else {
            // FIX: Clear reused bitmap immediately to prevent old content (stale strokes)
            // from being briefly visible during async page load. This fixes the "black line"
            // artifact that appeared on first tab switch. (Dec 2025)
            windowedCanvas.drawColor(Color.WHITE)
        }
        // loadInitialBitmap will overwrite the canvas content with proper background/strokes
        loadInitialBitmap()
    }

    /*
        Cancel loading strokes, and save bitmap to disk
    */
    fun disposeOldPage() {
        log.d("Dispose old page: $currentPageId")
        PageDataManager.releasePage(currentPageId)

        val pageIdToSave = currentPageId

        // Cache with validated dimensions
        if (surfaceSynced) {
            cacheBitmapIfSynced(pageIdToSave)
        }

        coroutineScope.launch(Dispatchers.Default) {
            updateOnExit(pageIdToSave)
            PageDataManager.forcePersistBitmap(context, pageIdToSave)
        }
        cleanJob()
    }

    private fun loadPage() {
        logCache.i("Init from persist layer, pageId: $currentPageId")
        windowedCanvas.scale(zoomLevel.value, zoomLevel.value)
        val bookId = pageFromDb?.notebookId
        val loadingPageId = currentPageId

        loadingJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val timeToLoad = measureTimeMillis {
                    logCache.d("Start page, id $currentPageId")
                    PageDataManager.requestPageLoadJoin(appRepository, currentPageId, bookId)
                    logCache.d("Got page data (PageView.loadPage). id $currentPageId")
                }
                logCache.d("All strokes loaded in $timeToLoad ms")
                // TODO: If we put it in loadPage(…) sometimes it will try to refresh
                //  without seeing strokes, I have no idea why.
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    clearLoadRedrawPending(loadingPageId)
                    DrawCanvas.forceUpdate.emit(null)
                }
                logCache.d("Loaded page from persistent layer $currentPageId")
                if (!PageDataManager.validatePageDataLoaded(currentPageId))
                    logCache.e("Page should be loaded, but it is not. $currentPageId")
                coroutineScope.launch(Dispatchers.Default) {
                    delay(10)
                    PageDataManager.reduceCache(20)
                    if (bookId.isNotNull())
                        cacheNeighbors(appRepository, currentPageId, bookId)
                }
            } catch (_: CancellationException) {
                clearLoadRedrawPending(loadingPageId)
                val dataStatus = PageDataManager.validatePageDataLoaded(currentPageId)
                logCache.d("Page loading cancelled, data was loaded correctly: $dataStatus")
            } catch (e: Exception) {
                clearLoadRedrawPending(loadingPageId)
                val dataStatus = PageDataManager.validatePageDataLoaded(currentPageId)
                logCache.e("Page loading cancelled, data was loaded correctly: $dataStatus", e)
            }
        }
    }

    // A load that ends without its redraw must not keep other full redraws waiting for it.
    private fun clearLoadRedrawPending(pageId: String) {
        if (loadRedrawPendingPageId == pageId) loadRedrawPendingPageId = null
    }


    fun addStrokes(strokesToAdd: List<Stroke>) {
        if (PagePersistenceGate.isDeleting(currentPageId)) {
            log.d("addStrokes: Skipping ${strokesToAdd.size} strokes for deleting page $currentPageId")
            return
        }

        PageDataManager.appendStrokes(currentPageId, strokesToAdd)  // Phase 3: locked, no read-then-replace
        // Infinite scroll disabled - page height is fixed to viewHeight
        // Users should use multi-page system for additional space

        saveStrokesToPersistLayer(strokesToAdd)
        PageDataManager.indexStrokes(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()

        // Invalidate preview cache since content changed
        PageDataManager.invalidatePreviewCache(currentPageId)

        // Notify that content changed (for calendar badge updates)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun removeStrokes(strokeIds: List<String>) {
        PageDataManager.removeStrokes(strokeIds, currentPageId)  // Phase 3: locked, no read-then-replace
        removeStrokesFromPersistLayer(strokeIds)
        PageDataManager.indexStrokes(coroutineScope, currentPageId)
        PageDataManager.recomputeHeight(currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()

        // Invalidate preview cache since content changed
        PageDataManager.invalidatePreviewCache(currentPageId)

        // Notify that content changed (for calendar badge updates)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    /** Preserve surviving ink atomically when erasing, including Undo and Redo. */
    fun replaceStrokes(sourceIds: List<String>, replacements: List<Stroke>) {
        val pageId = currentPageId
        require(replacements.all { it.pageId == pageId })
        if (PagePersistenceGate.isDeleting(pageId)) return
        PageDataManager.replaceStrokes(pageId, sourceIds, replacements)
        PagePersistenceWriter.submit("replace erased strokes on $pageId") {
            PagePersistenceGate.withWrite(pageId) {
                if (appRepository.pageRepository.getById(pageId) != null) {
                    dbStrokes.replaceOnPage(pageId, sourceIds, replacements)
                }
            }
        }
        PageDataManager.indexStrokes(coroutineScope, pageId)
        PageDataManager.recomputeHeight(pageId)
        persistPageUpdatedAt()
        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(pageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun getStrokes(strokeIds: List<String>): List<Stroke?> {
        return PageDataManager.getStrokes(strokeIds, currentPageId)
    }

    private fun saveStrokesToPersistLayer(strokes: List<Stroke>) {
        val pageId = currentPageId
        PagePersistenceWriter.submit("insert ${strokes.size} strokes on $pageId") {
            try {
                val inserted = PagePersistenceGate.withWrite(pageId) {
                    if (appRepository.pageRepository.getById(pageId) == null) {
                        log.d("saveStrokesToPersistLayer: Skipping ${strokes.size} strokes for missing page $pageId")
                        return@withWrite false
                    }

                    dbStrokes.create(strokes)
                    true
                }

                if (inserted == null) {
                    log.d("saveStrokesToPersistLayer: Skipping ${strokes.size} strokes for deleting page $pageId")
                }
            } catch (e: Exception) {
                log.e("saveStrokesToPersistLayer: Failed to insert ${strokes.size} strokes for $pageId: ${e.message}", e)
            }
        }
    }

    private fun saveImagesToPersistLayer(images: List<Image>) {
        PagePersistenceWriter.submit("insert ${images.size} images") {
            dbImages.create(images)
        }
    }


    fun addImage(imageToAdd: Image) {
        images += listOf(imageToAdd)
        // Infinite scroll disabled - page height is fixed to viewHeight

        saveImagesToPersistLayer(listOf(imageToAdd))
        PageDataManager.indexImages(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)

        // Notify that content changed (for calendar badge updates)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun addImage(imageToAdd: List<Image>) {
        images += imageToAdd
        // Infinite scroll disabled - page height is fixed to viewHeight

        saveImagesToPersistLayer(imageToAdd)
        PageDataManager.indexImages(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)

        // Notify that content changed (for calendar badge updates)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun removeImages(imageIds: List<String>) {
        images = images.filter { s -> !imageIds.contains(s.id) }
        removeImagesFromPersistLayer(imageIds)
        PageDataManager.indexImages(coroutineScope, currentPageId)
        PageDataManager.recomputeHeight(currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)

        // Notify that content changed (for calendar badge updates)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    /**
     * Clear all strokes and images from memory (used when clearing page content).
     * Note: Database deletion is handled separately by DayPageRepository.
     * Also redraws the canvas to show the cleared state.
     */
    fun clearAllContent() {
        strokes = emptyList()
        images = emptyList()
        PageDataManager.clearPageData(currentPageId)
        height = viewHeight

        // Redraw the canvas with just the background (no strokes/images)
        val redrawRect = Rect(0, 0, windowedBitmap.width, windowedBitmap.height)
        drawAreaScreenCoordinates(redrawRect)

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
    }

    fun getImage(imageId: String): Image? = PageDataManager.getImage(imageId, currentPageId)


    fun getImages(imageIds: List<String>): List<Image?> = PageDataManager.getImages(imageIds, currentPageId)

    // TextAnnotation methods for undo/redo support
    fun addTextAnnotations(annotations: List<TextAnnotation>) {
        annotations.forEach { annotation ->
            PageDataManager.addTextAnnotation(currentPageId, annotation)
        }
        saveTextAnnotationsToPersistLayer(annotations)
        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun removeTextAnnotations(annotationIds: List<String>) {
        annotationIds.forEach { id ->
            PageDataManager.removeTextAnnotation(currentPageId, id)
        }
        removeTextAnnotationsFromPersistLayer(annotationIds)
        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun getTextAnnotations(annotationIds: List<String>): List<TextAnnotation?> {
        return PageDataManager.getTextAnnotations(currentPageId)
            .filter { it.id in annotationIds }
            .takeIf { it.size == annotationIds.size }
            ?: annotationIds.map { id ->
                PageDataManager.getTextAnnotations(currentPageId).find { it.id == id }
            }
    }

    private fun saveTextAnnotationsToPersistLayer(annotations: List<TextAnnotation>) {
        PagePersistenceWriter.submit("insert ${annotations.size} text boxes") {
            val dao = AppDatabase.getDatabase(context).textAnnotationDao()
            dao.insertAll(annotations)
        }
    }

    private fun removeTextAnnotationsFromPersistLayer(annotationIds: List<String>) {
        PagePersistenceWriter.submit("delete ${annotationIds.size} text boxes") {
            val dao = AppDatabase.getDatabase(context).textAnnotationDao()
            dao.deleteByIds(annotationIds)
        }
    }

    private fun removeStrokesFromPersistLayer(strokeIds: List<String>) {
        // CROSS-DAY UNDO FIX (A1, Oct 2026): delete only from the page shown now. A stale ID list
        // (undo right after a day/page switch) must never remove another page's strokes.
        val pageId = currentPageId
        PagePersistenceWriter.submit("delete ${strokeIds.size} strokes on $pageId") {
            appRepository.strokeRepository.deleteByIdsOnPage(pageId, strokeIds)
        }
    }

    private fun removeImagesFromPersistLayer(imageIds: List<String>) {
        PagePersistenceWriter.submit("delete ${imageIds.size} images") {
            appRepository.imageRepository.deleteAll(imageIds)
        }
    }

    /** Update Page.updatedAt (OCR staleness detection) for the page shown now, in write order. */
    private fun persistPageUpdatedAt() {
        val pageId = currentPageId
        PagePersistenceWriter.submit("updatedAt $pageId") {
            appRepository.pageRepository.updateUpdatedAt(pageId)
        }
    }

    // Attachment methods
    fun addAttachment(attachment: Attachment) {
        PageDataManager.addAttachment(currentPageId, attachment)
        saveAttachmentToPersistLayer(attachment)
        PageDataManager.indexAttachments(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun removeAttachments(attachmentIds: List<String>) {
        PageDataManager.removeAttachments(currentPageId, attachmentIds)
        removeAttachmentsFromPersistLayer(attachmentIds)
        PageDataManager.indexAttachments(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    fun getAttachment(attachmentId: String): Attachment? {
        return PageDataManager.getAttachment(attachmentId, currentPageId)
    }

    fun getAttachments(attachmentIds: List<String>): List<Attachment?> {
        return attachmentIds.map { PageDataManager.getAttachment(it, currentPageId) }
    }

    fun addAttachments(attachmentList: List<Attachment>) {
        attachmentList.forEach { attachment ->
            PageDataManager.addAttachment(currentPageId, attachment)
            saveAttachmentToPersistLayer(attachment)
        }
        PageDataManager.indexAttachments(coroutineScope, currentPageId)

        // Update Page.updatedAt for OCR staleness detection
        persistPageUpdatedAt()

        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
        DrawCanvas.contentChanged.tryEmit(Unit)
    }

    private fun saveAttachmentToPersistLayer(attachment: Attachment) {
        PagePersistenceWriter.submit("insert attachment ${attachment.id}") {
            appRepository.attachmentRepository.create(attachment)
        }
    }

    private fun removeAttachmentsFromPersistLayer(attachmentIds: List<String>) {
        PagePersistenceWriter.submit("delete ${attachmentIds.size} attachments") {
            appRepository.attachmentRepository.deleteAll(attachmentIds)
        }
    }

        // load background, fast, if it is accurate enough.
    private fun loadInitialBitmap(): Boolean {
        val canvasWidth = windowedCanvas.width
        val canvasHeight = windowedCanvas.height

        // PRIORITY 1: Check preview cache (instant - no disk I/O)
        if (PageDataManager.drawPreviewToCanvas(currentPageId, windowedCanvas, canvasWidth, canvasHeight)) {
            log.d("PREVIEW_HIT: Used cached preview for $currentPageId")
            return true
        }

        // PRIORITY 2: Load from disk asynchronously (avoid blocking main thread)
        val pageIdSnapshot = currentPageId
        val scrollSnapshot = scroll
        val zoomSnapshot = zoomLevel.value
        coroutineScope.launch(Dispatchers.IO) {
            val bitmapFromDisk = loadPersistBitmap(
                context,
                pageIdSnapshot,
                scrollSnapshot,
                zoomSnapshot,
                true
            )
            if (bitmapFromDisk == null || bitmapFromDisk.isRecycled) return@launch
            PageDataManager.putPreviewBitmap(pageIdSnapshot, bitmapFromDisk)
            coroutineScope.launch(Dispatchers.Main.immediate) {
                if (currentPageId != pageIdSnapshot) return@launch
                if (PageDataManager.drawPreviewToCanvas(
                        pageIdSnapshot,
                        windowedCanvas,
                        windowedCanvas.width,
                        windowedCanvas.height
                    )
                ) {
                    DrawCanvas.forceUpdate.emit(null)
                }
            }
        }

        log.d("Drawing initial background.")
        // draw just background - no persisted bitmap available
        val backgroundType = pageFromDb?.getBackgroundType()
        if (backgroundType == BackgroundType.Native)
            drawBg(
                context, windowedCanvas, backgroundType,
                pageFromDb?.background ?: "blank", scroll, 1f, this
            )
        else
            windowedCanvas.drawColor(Color.WHITE)
        return false
    }

    private fun cleanJob() {
        //ensure that snack is canceled, even on dispose of the page.
        CoroutineScope(Dispatchers.IO).launch {
            PageDataManager.cancelLoadingPage(pageId = currentPageId)
        }
        loadingJob?.cancel()
        if (loadingJob?.isActive == true) {
            log.e("Strokes are still loading, trying to cancel and resume")
        }
    }


    fun drawAreaPageCoordinates(
        pageArea: Rect, // in page coordinates
        ignoredStrokeIds: List<String> = listOf(),
        ignoredImageIds: List<String> = listOf(),
        ignoredTextAnnotationIds: List<String> = listOf(),
        ignoredAttachmentIds: List<String> = listOf(),
        canvas: Canvas? = null
    ) {
        val areaInScreen = toScreenCoordinates(pageArea)
        drawAreaScreenCoordinates(areaInScreen, ignoredStrokeIds, ignoredImageIds, ignoredTextAnnotationIds, ignoredAttachmentIds, canvas)
    }

    /*
        provided a rectangle, in screen coordinates, its check
        for all images intersecting it, excluding ones set to be ignored,
        and redraws them. Does not refresh screen/SurfaceView.
     */
    fun drawAreaScreenCoordinates(
        screenArea: Rect,
        ignoredStrokeIds: List<String> = listOf(),
        ignoredImageIds: List<String> = listOf(),
        ignoredTextAnnotationIds: List<String> = listOf(),
        ignoredAttachmentIds: List<String> = listOf(),
        canvas: Canvas? = null
    ) {
        val activeCanvas = canvas ?: windowedCanvas
        val pageArea = toPageCoordinates(screenArea)
        val pageAreaWithoutScroll = removeScroll(pageArea)
        
        // Auto-add selected text box to ignored list so it's only rendered in TextBoxOverlay
        // Uses global companion object value to bypass instance/Compose state timing issues
        val selectedTextId = TextBoxState.globalSelectedTextBoxId
        log.d("drawAreaScreenCoordinates: globalSelectedTextId=$selectedTextId")
        val allIgnoredTextIds = if (selectedTextId != null && !ignoredTextAnnotationIds.contains(selectedTextId)) {
            log.d("drawAreaScreenCoordinates: SKIPPING text annotation $selectedTextId")
            ignoredTextAnnotationIds + selectedTextId
        } else {
            ignoredTextAnnotationIds
        }
        
        if (activeCanvas === windowedCanvas) {
            synchronized(windowedBitmap) {
                drawOnCanvasFromPage(
                    page = this,
                    canvas = activeCanvas,
                    canvasClipBounds = pageAreaWithoutScroll,
                    pageArea = pageArea,
                    ignoredStrokeIds = ignoredStrokeIds,
                    ignoredImageIds = ignoredImageIds,
                    ignoredTextAnnotationIds = allIgnoredTextIds,
                    ignoredAttachmentIds = ignoredAttachmentIds,
                )
            }
        } else {
            drawOnCanvasFromPage(
                page = this,
                canvas = activeCanvas,
                canvasClipBounds = pageAreaWithoutScroll,
                pageArea = pageArea,
                ignoredStrokeIds = ignoredStrokeIds,
                ignoredImageIds = ignoredImageIds,
                ignoredTextAnnotationIds = allIgnoredTextIds,
                ignoredAttachmentIds = ignoredAttachmentIds,
            )
        }
    }

    suspend fun simpleUpdateScroll(dragDelta: Offset) {
        // Just update scroll, for debugging.
        // It will redraw whole screen, instead of trying to redraw only needed area.
        log.d("Simple update scroll")
        val delta = (dragDelta / zoomLevel.value)

        DrawCanvas.waitForDrawingWithSnack()

        scroll =
            Offset((scroll.x + delta.x).coerceAtLeast(0f), (scroll.y + delta.y).coerceAtLeast(0f))

        DrawCanvas.forceUpdate.emit(null)
    }


    fun alreadyDrawnRectAfterShift(
        movement: IntOffset,
        screenW: Int,
        screenH: Int
    ): Rect {
        val dx = -movement.x
        val dy = -movement.y
        val left = max(0, dx)
        val top = max(0, dy)
        val right = min(screenW, dx + screenW)
        val bottom = min(screenH, dy + screenH)
        return Rect(left, top, right, bottom)
    }

    suspend fun updateScroll(dragDelta: Offset) {
//        log.d("Update scroll, dragDelta: $dragDelta, scroll: $scroll, zoomLevel.value: $zoomLevel.value")
        // drag delta is in screen coordinates,
        // so we have to scale it back to page coordinates.
        var deltaInPage = Offset(dragDelta.x / zoomLevel.value, dragDelta.y / zoomLevel.value)

        // Cut, so we won't shift outside the screen.
        if (scroll.x + deltaInPage.x < 0) {
            deltaInPage = deltaInPage.copy(x = -scroll.x)
        }
        if (scroll.y + deltaInPage.y < 0) {
            deltaInPage = deltaInPage.copy(y = -scroll.y)
        }

        // There is nothing to do, return.
        if (deltaInPage == Offset.Zero) return

        // before scrolling, make sure that strokes are drawn.
        DrawCanvas.waitForDrawingWithSnack()

        scroll += deltaInPage
        // To avoid rounding errors, we just calculate it again.
        val movement = (deltaInPage * zoomLevel.value)
        if (movement.toIntOffset() == IntOffset.Zero) return

        val width = windowedBitmap.width
        val height = windowedBitmap.height

        // Reuse bitmap if available and correct size, otherwise create new one
        val shiftedBitmap = reusableBitmap?.takeIf {
            it.width == width && it.height == height && !it.isRecycled
        } ?: createBitmap(width, height, windowedBitmap.config!!)

        val shiftedCanvas = Canvas(shiftedBitmap)
        shiftedCanvas.drawColor(Color.RED) //for debugging.
        shiftedCanvas.drawBitmap(windowedBitmap, -movement.x, -movement.y, null)

        // Keep old bitmap for reuse, swap in shifted bitmap
        reusableBitmap = windowedBitmap
        windowedBitmap = shiftedBitmap
        windowedCanvas.setBitmap(windowedBitmap)
        windowedCanvas.scale(zoomLevel.value, zoomLevel.value)

        redrawOutsideRect(
            alreadyDrawnRectAfterShift(movement.toIntOffset(), width, height),
            width,
            height
        )

        persistBitmapDebounced()
        saveToPersistLayer()
    }

    // ── Memos view moves (Oct 2026, memo canvas navigation plan, Phase 0 spike) ──────────────────────

    // Spare bitmap for [panBy]. The shown bitmap itself is never swapped: the bitmap cache and the
    // image saver hold it by reference.
    private var panSpare: Bitmap? = null

    /**
     * Moves the view corner by [delta] page pixels, inside [limits] and on whole pixels: the shown image
     * is shifted and only the uncovered bands are drawn. No database write, and no saved image while the
     * view is moved (PageDataManager skips it). True when the view moved. Main thread.
     */
    fun panBy(delta: Offset, limits: RectF): Boolean {
        val from = scroll
        val target = MemoViewport.clamp(from + delta, limits)
        val to = Offset(target.x.roundToInt().toFloat(), target.y.roundToInt().toFloat())
        val dx = (to.x - from.x).roundToInt()
        val dy = (to.y - from.y).roundToInt()
        if (dx == 0 && dy == 0) return false
        scroll = to
        val width = windowedBitmap.width
        val height = windowedBitmap.height
        synchronized(windowedBitmap) {
            val spare = panSpare?.takeIf { it.width == width && it.height == height && !it.isRecycled }
                ?: createBitmap(width, height, windowedBitmap.config ?: Bitmap.Config.ARGB_8888).also { panSpare = it }
            Canvas(spare).apply {
                drawColor(Color.WHITE)
                drawBitmap(windowedBitmap, -dx.toFloat(), -dy.toFloat(), null)
            }
            Canvas(windowedBitmap).drawBitmap(spare, 0f, 0f, null)
        }
        redrawOutsideRect(alreadyDrawnRectAfterShift(IntOffset(dx, dy), width, height), width, height)
        return true
    }

    /** Puts the view corner at [target] (inside [limits], whole pixels) and draws the whole view. True when it moved. */
    fun jumpTo(target: Offset, limits: RectF): Boolean {
        val clamped = MemoViewport.clamp(target, limits)
        val to = Offset(clamped.x.roundToInt().toFloat(), clamped.y.roundToInt().toFloat())
        if (to == scroll) return false
        scroll = to
        drawAreaScreenCoordinates(Rect(0, 0, windowedBitmap.width, windowedBitmap.height))
        return true
    }


    private fun calculateZoomLevel(
        scaleDelta: Float,
        currentZoom: Float,
    ): Float {
        // TODO: Better snapping logic
        val portraitRatio = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT

        return if (!GlobalAppSettings.current.continuousZoom) {
            // Discrete zoom mode - snap to either 1.0 or screen ratio
            if (scaleDelta <= 1.0f) {
                if (SCREEN_HEIGHT > SCREEN_WIDTH) portraitRatio else 1.0f
            } else {
                if (SCREEN_HEIGHT > SCREEN_WIDTH) 1.0f else portraitRatio
            }
        } else {
            // Continuous zoom mode with snap behavior
            val newZoom = (scaleDelta / 3 + currentZoom).coerceIn(0.5f, 5.0f)

            // Snap to either 1.0 or screen ratio depending on which is closer
            val snapTarget = if (abs(newZoom - 1.0f) < abs(newZoom - portraitRatio)) {
                1.0f
            } else {
                portraitRatio
            }

            if (abs(newZoom - snapTarget) < ZOOM_SNAP_THRESHOLD) {
                log.d("Zoom snap to $snapTarget")
                snapTarget
            } else {
                log.d("Left zoom as is. $newZoom")
                newZoom
            }
        }
    }

    suspend fun simpleUpdateZoom(scaleDelta: Float) {
        log.d("Simple Zoom updated, $scaleDelta")
        // Update the zoom factor
        val newZoomLevel = calculateZoomLevel(scaleDelta, zoomLevel.value)

        // If there's no actual zoom change, skip
        if (newZoomLevel == zoomLevel.value) {
            log.d("Zoom unchanged. Current level: ${zoomLevel.value}")
            return
        }
        log.d("New zoom level: $newZoomLevel")
        applyZoomAndRedraw(newZoomLevel)
    }

    suspend fun applyZoomAndRedraw(newZoom: Float) {
        zoomLevel.value = newZoom
        DrawCanvas.waitForDrawingWithSnack()
        // Create a scaled bitmap to represent zoomed view
        val scaledWidth = windowedCanvas.width
        val scaledHeight = windowedCanvas.height
        log.d("Canvas dimensions: width=$scaledWidth, height=$scaledHeight")
        log.d("Bitmap dimensions: width=${windowedBitmap.width}, height=${windowedBitmap.height}")
        log.d("Screen dimensions: width=$SCREEN_WIDTH, height=$SCREEN_HEIGHT")
        log.d("Page View dimension: width=${viewWidth}, height=${viewHeight}")


        val zoomedBitmap = createBitmap(scaledWidth, scaledHeight, windowedBitmap.config!!)

        // Swap in the new zoomed bitmap
//        windowedBitmap.recycle() -- It causes race condition with init from persistent layer
        windowedBitmap = zoomedBitmap
        windowedCanvas.setBitmap(windowedBitmap)
        windowedCanvas.scale(zoomLevel.value, zoomLevel.value)


        // Redraw everything at new zoom level
        val redrawRect = Rect(0, 0, windowedBitmap.width, windowedBitmap.height)

        log.d("Redrawing full logical rect: $redrawRect")
        windowedCanvas.drawColor(Color.BLACK)

        drawBg(
            context,
            windowedCanvas,
            pageFromDb?.getBackgroundType() ?: BackgroundType.Native,
            pageFromDb?.background ?: "blank",
            scroll,
            zoomLevel.value,
            this,
            redrawRect
        )
        cacheBitmapIfSynced()

        drawAreaScreenCoordinates(redrawRect)

        saveToPersistLayer()
        log.i("Zoom and redraw completed")
    }


    /**
     * Update zoom by reusing the existing screen bitmap.
     * - Scales the snapshot around the given center (screen coords).
     * - Redraws only the uncovered bands when zooming out.
     * - When zooming in, keeps the upscaled snapshot (even if low-res) for now.
     * - Updates scroll (IntOffset) so that the top-left of the view is correct after zoom,
     *   keeping the content under the pinch center stationary on screen.
     */
    suspend fun updateZoom(scaleDelta: Float, center: Offset?) {
        log.d("Zoom(delta): $scaleDelta. Center: $center")

        val oldZoom = zoomLevel.value
        val newZoom = calculateZoomLevel(scaleDelta, oldZoom)
        if (newZoom == oldZoom) {
            log.d("Zoom unchanged. Current level: $oldZoom")
            return
        }

        // Flush pending strokes/background before snapshot-based operations
        DrawCanvas.waitForDrawingWithSnack()

        val scaleFactor = newZoom / oldZoom
        val screenW = windowedCanvas.width
        val screenH = windowedCanvas.height

        // Default pivot to screen center if none passed
        val pivotX = center?.x ?: (screenW / 2f)
        val pivotY = center?.y ?: (screenH / 2f)

        // Draw scaled snapshot into a fresh screen-sized bitmap
        val scaledBitmap = createBitmap(screenW, screenH, windowedBitmap.config!!)
        val scaledCanvas = Canvas(scaledBitmap)
        scaledCanvas.drawColor(Color.RED) // clear

        val matrix = Matrix().apply {
            postScale(scaleFactor, scaleFactor, pivotX, pivotY)
        }

        // Calculate where the scaled snapshot ended up on screen.
        // Map the original screen rect through the same matrix to get content bounds.
        val srcRect = RectF(0f, 0f, screenW.toFloat(), screenH.toFloat())
        val dstRect = RectF()
        matrix.mapRect(dstRect, srcRect)


        //make sure that we won't go outside canvas.
        val dx = (scroll.x - dstRect.left).coerceAtMost(0f)
        val dy = (scroll.y - dstRect.top).coerceAtMost(0f)
        if (dx != 0f || dy != 0f) {
            matrix.postTranslate(dx, dy)
            matrix.mapRect(dstRect, srcRect)
        }
        scaledCanvas.drawBitmap(windowedBitmap, matrix, null)


        val deltaScrollPage = Offset(-dstRect.left / newZoom, -dstRect.top / newZoom)


        val newScrollX = (scroll.x + deltaScrollPage.x).coerceAtLeast(0f)
        val newScrollY = (scroll.y + deltaScrollPage.y).coerceAtLeast(0f)
        scroll = Offset(newScrollX, newScrollY)

        // Swap in the new bitmap and update zoom on the windowed canvas
        windowedBitmap = scaledBitmap
        windowedCanvas.setBitmap(windowedBitmap)

        zoomLevel.value = newZoom
        windowedCanvas.scale(zoomLevel.value, zoomLevel.value)

        if (scaleFactor < 1f) redrawOutsideRect(dstRect.toRect(), screenW, screenH)

        persistBitmapDebounced()
        saveToPersistLayer()
        log.i(
            "Zoom updated using snapshot scaling. " +
                    "oldZoom=$oldZoom newZoom=$newZoom " +
                    "scaleFactor=$scaleFactor pivot=($pivotX,$pivotY) " +
                    "bounds=$dstRect" +
                    "scrollDelta=$deltaScrollPage newScroll=$scroll"
        )
    }

    fun redrawOutsideRect(dstRect: Rect, screenW: Int, screenH: Int) {
        val scaledOverlap = ceil(OVERLAP * zoomLevel.value.coerceAtLeast(1f)).toInt()

        // Uncovered top band
        if (dstRect.top > 0) {
            val r = Rect(
                0,
                0,
                screenW,
                (dstRect.top + scaledOverlap).coerceAtMost(screenH)
            )
            if (!r.isEmpty) drawAreaScreenCoordinates(r)
        }
        // Uncovered bottom band
        if (dstRect.bottom < screenH) {
            val r = Rect(
                0, (dstRect.bottom - scaledOverlap).coerceAtLeast(0), screenW, screenH
            )
            if (!r.isEmpty) drawAreaScreenCoordinates(r)
        }
        // Uncovered left band
        if (dstRect.left > 0) {
            val r = Rect(
                0,
                (dstRect.top - scaledOverlap).coerceAtLeast(0),
                (dstRect.left + scaledOverlap).coerceAtMost(screenW),
                (dstRect.bottom + scaledOverlap).coerceAtMost(screenH)
            )
            if (!r.isEmpty) drawAreaScreenCoordinates(r)
        }
        // Uncovered right band
        if (dstRect.right < screenW) {
            val r = Rect(
                (dstRect.right - scaledOverlap).coerceAtLeast(0),
                (dstRect.top - scaledOverlap).coerceAtLeast(0),
                screenW,
                (dstRect.bottom + scaledOverlap).coerceAtMost(screenH)
            )
            if (!r.isEmpty) drawAreaScreenCoordinates(r)
        }
    }


    // updates page setting in db, (for instance type of background)
// and redraws page to vew.
    fun updatePageSettings(page: Page) {
        appRepository.pageRepository.update(page)
        // PERFORMANCE: Use the passed page object directly instead of re-fetching from DB
        pageFromDb = page
        log.i("Page settings updated, ${pageFromDb?.background}")
        drawAreaScreenCoordinates(Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT))
        persistBitmapDebounced()
        PageDataManager.invalidatePreviewCache(currentPageId)
    }
    fun syncToSurfaceSize(surfaceWidth: Int, surfaceHeight: Int) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        val dimensionsChanged = viewWidth != surfaceWidth || viewHeight != surfaceHeight
        val bitmapMismatch = windowedBitmap.width != surfaceWidth || windowedBitmap.height != surfaceHeight

        if (dimensionsChanged || bitmapMismatch) {
            log.i("SYNC: ${viewWidth}x${viewHeight} -> ${surfaceWidth}x${surfaceHeight}")

            // Set expected dimensions for cache validation
            PageDataManager.setExpectedDimensions(currentPageId, surfaceWidth, surfaceHeight)

            // Determine if heal is allowed (only for native backgrounds)
            val backgroundType = pageFromDb?.getBackgroundType()
            val allowHeal = backgroundType == BackgroundType.Native

            // Try to heal FIRST, before any generation change
            PageDataManager.getOrHealBitmap(currentPageId, surfaceWidth, surfaceHeight, allowHeal)?.let { healed ->
                windowedBitmap = healed
                windowedCanvas = Canvas(windowedBitmap)
                viewWidth = surfaceWidth
                viewHeight = surfaceHeight
                surfaceSyncedWidth = surfaceWidth
                surfaceSyncedHeight = surfaceHeight
                surfaceSynced = true
                log.i("SYNC: Used healed bitmap (no generation change)")
                return
            }

            // Heal failed or not allowed - need full recreation
            // NOW increment generation since we're creating new content
            PageDataManager.incrementPageGeneration(currentPageId)

            // FIX: Update synced dimensions BEFORE updateDimensions() so that
            // persistBitmapDebounced() sees correct values during bitmap recreation.
            surfaceSyncedWidth = surfaceWidth
            surfaceSyncedHeight = surfaceHeight
            surfaceSynced = true

            updateDimensions(surfaceWidth, surfaceHeight)
        }

        // Also set for the no-change case (idempotent)
        surfaceSyncedWidth = surfaceWidth
        surfaceSyncedHeight = surfaceHeight
        surfaceSynced = true
    }

    /**
     * Caches bitmap only if surface is synced and dimensions match.
     * Use this instead of direct cacheBitmap() calls.
     */
    fun cacheBitmapIfSynced(pageId: String = currentPageId): Boolean {
        if (!surfaceSynced) {
            log.d("CACHE_SKIP: Surface not synced for $pageId")
            return false
        }

        if (windowedBitmap.width != surfaceSyncedWidth ||
            windowedBitmap.height != surfaceSyncedHeight) {
            log.w(
                "CACHE_SKIP: Bitmap/surface mismatch for $pageId: " +
                    "bitmap=${windowedBitmap.width}x${windowedBitmap.height}, " +
                    "surface=${surfaceSyncedWidth}x${surfaceSyncedHeight}"
            )
            return false
        }

        return PageDataManager.cacheBitmapValidated(
            pageId,
            windowedBitmap,
            surfaceSyncedWidth,
            surfaceSyncedHeight
        )
    }



    fun updateDimensions(newWidth: Int, newHeight: Int) {
        if (newWidth != viewWidth || newHeight != viewHeight) {
            log.d("Updating dimensions: $newWidth x $newHeight")
            viewWidth = newWidth
            viewHeight = newHeight
            updateCanvasDimensions()
        }
    }

    private fun updateCanvasDimensions() {
        // Recreate bitmap and canvas with new dimensions
        recreateCanvas()
        //Reset zoom level.
        zoomLevel.value = 1.0f
        // TODO: it might be worth to do it
        //  by redrawing only part of the screen, like in scroll and zoom.
        coroutineScope.launch {
            DrawCanvas.forceUpdate.emit(null)
        }
        persistBitmapDebounced()
    }

    // PERFORMANCE FIX: Track last emit time to rate-limit coroutine spawning
    // This prevents the "bitmap storm" where 50+ coroutines flood the system
    @Volatile
    private var lastPersistEmitTime = 0L
    private companion object {
        const val MIN_EMIT_INTERVAL_MS = 100L  // Rate limit: max 1 emit per 100ms
    }

    // should be run after every modification of widowedBitmap.
    // Especially, on major ones -- this persistent bitmap will be used to reinitialize page.
    // if its not correct, might cause ghosting
    private fun persistBitmapDebounced(pageId: String = this.currentPageId) {
        val now = System.currentTimeMillis()

        // Cache with validated dimensions (not self-validating!)
        if (surfaceSynced) {
            PageDataManager.cacheBitmapValidated(
                currentPageId,
                windowedBitmap,
                surfaceSyncedWidth,
                surfaceSyncedHeight
            )
        } else {
            log.d("CACHE_SKIP: Surface not synced, skipping cache during persist")
        }

        // Rate limit: don't spawn more than one coroutine per MIN_EMIT_INTERVAL_MS for same page
        // But ALWAYS emit for different pageIds (e.g., when changing pages)
        val timeSinceLastEmit = now - lastPersistEmitTime
        if (pageId == currentPageId && timeSinceLastEmit < MIN_EMIT_INTERVAL_MS) {
            // Skip emit - we already cached the bitmap above, and the save scheduled by the
            // previous emit runs once edits pause, from this same bitmap
            return
        }

        lastPersistEmitTime = now
        coroutineScope.launch {
            PageDataManager.saveTopic.emit(pageId)
        }
    }

    private fun saveToPersistLayer() {
        coroutineScope.launch(Dispatchers.IO) {
            appRepository.pageRepository.updateScroll(currentPageId, scroll.y.toInt())
            pageFromDb = appRepository.pageRepository.getById(currentPageId)
        }
    }


    /**
     * Capture the current visible canvas content as a bitmap.
     * Returns a copy of the windowedBitmap for AI processing.
     */
    fun captureCanvasBitmap(): Bitmap? {
        return try {
            windowedBitmap.copy(windowedBitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            log.e("Failed to capture canvas bitmap: ${e.message}")
            null
        }
    }

    fun applyZoom(point: IntOffset): IntOffset {
        return point * zoomLevel.value
    }

    fun removeZoom(point: IntOffset): IntOffset {
        return point / zoomLevel.value
    }

    private fun removeScroll(rect: Rect): Rect {
        return rect - scroll
    }

    fun toScreenCoordinates(rect: Rect): Rect {
        return (rect - scroll) * zoomLevel.value
    }

    private fun toPageCoordinates(rect: Rect): Rect {
        return rect / zoomLevel.value + scroll
    }
}

