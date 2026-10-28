package com.boox.day.editor

import android.content.Context
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import com.boox.day.data.PageDataManager
import com.boox.day.editor.state.PanAxis
import com.boox.day.editor.state.constrain
import com.boox.day.editor.state.panAxis
import com.boox.day.editor.ui.PageMapState
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one place that moves the Memos view (Oct 2026, memo canvas navigation plan, Phase 0 spike).
 * Two-finger drags, page map taps and page map drags all come through here: the same page area and
 * limits (MemoViewport), the same move (PageView.panBy / jumpTo), the same frame (DrawCanvas
 * .showViewportFrame) and the same finish (DrawCanvas.endViewportMove / endMapSession). The page map
 * only shows this state ([PageMapState]); it keeps no position of its own. Main thread.
 */
class MemoNavigator(private val scope: CoroutineScope, private val page: PageView) {
    private val log = TaggedLog("MemoNavigator")

    private enum class Session { Drag, Map }

    private var session: Session? = null
    private var limits = RectF()
    private var map: PageMapGeometry? = null
    private var axis = PanAxis.Free
    private var startedAtMs = 0L
    private var frames = 0
    private var shiftMsTotal = 0L
    private var postMsTotal = 0L

    // Finger movement not yet shown, in screen pixels: a burst of touch events becomes one frame
    private val pending = MutableStateFlow(Offset.Zero)
    private var consumer: Job? = null

    val isMapOpen: Boolean get() = session == Session.Map

    // ── Two-finger drag ───────────────────────────────────────────────────────────────────────────

    /**
     * Fingers that have moved [travel] together start a drag. False when there's nowhere to go (the
     * page fits the view) or the canvas can't move now.
     */
    fun beginDrag(travel: Offset): Boolean {
        if (session != null) return false
        if (!measure()) {
            log.i("PAN_SPIKE: two-finger drag, but the page fits the view (scroll=${page.scroll})")
            return false
        }
        val canvas = DrawCanvas.active() ?: return false
        if (!canvas.beginViewportMove()) {
            log.i("PAN_SPIKE: two-finger drag refused by the canvas")
            return false
        }
        session = Session.Drag
        axis = panAxis(travel)
        startedAtMs = SystemClock.uptimeMillis()
        frames = 0
        shiftMsTotal = 0
        postMsTotal = 0
        pending.value = Offset.Zero
        consumer = scope.launch(Dispatchers.Main) {
            pending.collect {
                val delta = pending.getAndUpdate { Offset.Zero }
                if (delta == Offset.Zero) return@collect
                val start = SystemClock.uptimeMillis()
                // The page follows the fingers: fingers up = view corner down the page
                val moved = page.panBy(-axis.constrain(delta), limits)
                val shifted = SystemClock.uptimeMillis()
                if (moved) {
                    DrawCanvas.active()?.showViewportFrame()
                    showFrame()
                    frames++
                }
                val posted = SystemClock.uptimeMillis()
                shiftMsTotal += shifted - start
                postMsTotal += posted - shifted
                log.d("PAN_SPIKE: frame $frames: shift ${shifted - start} ms, post ${posted - shifted} ms, scroll=${page.scroll}")
                val wait = PanSpikeSettings.frameGapMs - (SystemClock.uptimeMillis() - start)
                if (wait > 0) delay(wait)
            }
        }
        showMap(interactive = false)
        log.i("PAN_SPIKE: drag begins: axis=$axis limits=$limits scroll=${page.scroll} gap=${PanSpikeSettings.frameGapMs} ms")
        return true
    }

    /** The fingers moved [fingerDelta] screen pixels. */
    fun moveBy(fingerDelta: Offset) {
        if (session != Session.Drag) return
        pending.value = pending.value + fingerDelta
    }

    /** The last finger lifted (or the gesture was cut short): the view stays, the pen comes back. */
    fun endDrag() {
        if (session != Session.Drag) return
        consumer?.cancel()
        consumer = null
        val rest = pending.getAndUpdate { Offset.Zero }
        if (rest != Offset.Zero) page.panBy(-axis.constrain(rest), limits)  // the finish draws it
        session = null
        hideMap()
        val took = SystemClock.uptimeMillis() - startedAtMs
        log.i(
            "PAN_SPIKE: drag ends: $frames frames in $took ms" +
                (if (frames > 0) ", shift avg ${shiftMsTotal / frames} ms, post avg ${postMsTotal / frames} ms" else "") +
                ", scroll=${page.scroll}"
        )
        scope.launch(Dispatchers.Main) {
            // The map leaves the screen before the pen comes back (the pen copies the screen)
            awaitFrame()
            awaitFrame()
            DrawCanvas.active()?.endViewportMove()
        }
    }

    // ── Page map ──────────────────────────────────────────────────────────────────────────────────

    /** The toolbar menu's Page map: opens it once the menu has closed, or closes it. */
    fun toggleMapFromMenu() {
        if (session == Session.Map) {
            closeMap()
            return
        }
        scope.launch(Dispatchers.Main) {
            // The menu is a popup until its own state says it closed
            withTimeoutOrNull(1_500L) { DrawCanvas.toolbarModalVisible.first { !it } }
            openMap()
        }
    }

    private fun openMap() {
        if (session != null) return
        if (!measure()) {
            SnackState.globalSnackFlow.tryEmit(SnackConf(text = "The whole page is on the screen.", duration = SnackConf.DURATION_QUICK))
            return
        }
        val canvas = DrawCanvas.active() ?: return
        if (!canvas.beginMapSession()) {
            log.i("PAN_SPIKE: page map refused by the canvas")
            return
        }
        session = Session.Map
        startedAtMs = SystemClock.uptimeMillis()
        showMap(interactive = true)
        log.i("PAN_SPIKE: page map open: limits=$limits scroll=${page.scroll}")
    }

    /** Back to writing: the map closes, then the pen comes back. */
    fun closeMap() {
        if (session != Session.Map) return
        session = null
        hideMap()
        scope.launch(Dispatchers.Main) {
            awaitFrame()
            awaitFrame()
            DrawCanvas.active()?.endMapSession()
        }
    }

    /** A tap on the map at [mapPoint]: the view centres there. */
    fun mapTap(mapPoint: Offset) {
        val map = map ?: return
        if (session != Session.Map) return
        jump(map.scrollCentredOn(mapPoint, page.viewWidth, page.viewHeight, limits), "tap")
    }

    /** Where the frame goes for a drag that puts its corner at [mapCorner] (map pixels), inside the limits. */
    fun frameForCorner(mapCorner: Offset): RectF? {
        val map = map ?: return null
        return map.viewFrame(MemoViewport.clamp(map.toPage(mapCorner), limits), page.viewWidth, page.viewHeight)
    }

    /** A map drag stopped (paused or lifted) with the frame's corner at [mapCorner]: the canvas catches up. */
    fun mapDragTo(mapCorner: Offset) {
        val map = map ?: return
        if (session != Session.Map) return
        jump(MemoViewport.clamp(map.toPage(mapCorner), limits), "drag")
    }

    private fun jump(target: Offset, why: String) {
        val start = SystemClock.uptimeMillis()
        if (page.jumpTo(target, limits)) {
            val drawn = SystemClock.uptimeMillis()
            DrawCanvas.active()?.showViewportFrame()
            log.i("PAN_SPIKE: map $why: draw ${drawn - start} ms, post ${SystemClock.uptimeMillis() - drawn} ms, scroll=${page.scroll}")
        }
        showFrame()
    }

    // ── Shared ───────────────────────────────────────────────────────────────────────────────────

    /** Page area, limits and map size for the page shown now. True when the view can move. */
    private fun measure(): Boolean {
        load(page.context)
        val pageId = page.currentPageId
        val sheetHeight = MemoViewport.sheetHeight(
            fullScreenHeight, page.viewHeight, fullScreenExtra, com.boox.day.ui.FullScreenState.isOn
        )
        val area = MemoViewport.pageArea(
            page.viewWidth, page.viewHeight, sheetHeight, contentBounds(pageId),
            margin = MARGIN_DP * page.context.resources.displayMetrics.density
        )
        limits = MemoViewport.scrollLimits(area, page.viewWidth, page.viewHeight)
        map = PageMapGeometry(area, page.viewWidth * MAP_WIDTH_SHARE, page.viewHeight * MAP_HEIGHT_SHARE)
        return MemoViewport.canMove(limits)
    }

    private fun showMap(interactive: Boolean) {
        val map = map ?: return
        val pageId = page.currentPageId
        PageMapState.show(map, interactive, map.viewFrame(page.scroll, page.viewWidth, page.viewHeight))
        val cached = MemoMapRenderer.cached(pageId, map)
        if (cached != null) {
            PageMapState.picture = cached.asImageBitmap()
            return
        }
        PageMapState.picture = null
        scope.launch(Dispatchers.Default) {
            val start = SystemClock.uptimeMillis()
            val picture = MemoMapRenderer.render(pageId, map)
            val took = SystemClock.uptimeMillis() - start
            withContext(Dispatchers.Main) {
                log.i("PAN_SPIKE: map picture ${picture.width}x${picture.height} drawn in $took ms (${PageDataManager.getStrokes(pageId).size} strokes)")
                if (PageMapState.visible && page.currentPageId == pageId) PageMapState.picture = picture.asImageBitmap()
            }
        }
    }

    private fun showFrame() {
        val map = map ?: return
        PageMapState.frame = map.viewFrame(page.scroll, page.viewWidth, page.viewHeight)
    }

    private fun hideMap() = PageMapState.hide()

    private fun contentBounds(pageId: String): RectF? {
        var bounds: RectF? = null
        fun add(left: Float, top: Float, right: Float, bottom: Float) {
            bounds?.union(left, top, right, bottom) ?: run { bounds = RectF(left, top, right, bottom) }
        }
        PageDataManager.getStrokes(pageId).forEach { add(it.left, it.top, it.right, it.bottom) }
        PageDataManager.getImages(pageId).forEach {
            add(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
        }
        PageDataManager.getAttachments(pageId).forEach {
            add(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
        }
        PageDataManager.getTextAnnotations(pageId).forEach { add(it.x, it.y, it.x + it.width, it.y + it.height) }
        return bounds
    }

    companion object {
        /** Room past the page's content the view may move into. */
        private const val MARGIN_DP = 48f

        /** Most of the canvas the map may take: its width and its height. */
        private const val MAP_WIDTH_SHARE = 0.22f
        private const val MAP_HEIGHT_SHARE = 0.6f

        private const val PREFS = "memo_viewport"
        private const val KEY_FULL_SCREEN_HEIGHT = "full_screen_canvas_height"

        /** The navigator of the Memos page shown now (set by the canvas host). */
        @Volatile var current: MemoNavigator? = null

        /** The Memos canvas height in full screen on this tablet: the full screen page (0 until first seen). */
        @Volatile var fullScreenHeight = 0
            private set

        // The normal view's place per page while full screen shows it from the top
        private val normalViewScroll = HashMap<String, Offset>()

        fun load(context: Context) {
            if (fullScreenHeight == 0) {
                fullScreenHeight = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_FULL_SCREEN_HEIGHT, 0)
            }
        }

        /**
         * How much taller the canvas is in full screen, measured from the normal layout (window height
         * minus the canvas area, minus the full screen date line). Used until full screen is opened once.
         */
        @Volatile var fullScreenExtra = 0
            private set

        fun recordFullScreenExtra(extra: Int) {
            if (extra <= 0 || extra == fullScreenExtra) return
            fullScreenExtra = extra
            TaggedLog("MemoNavigator").i("PAN_SPIKE: full screen adds $extra px (measured, saved height $fullScreenHeight)")
        }

        /** Full screen showed the canvas at [height] px: that is this tablet's full screen page. */
        fun recordFullScreenHeight(context: Context, height: Int) {
            if (height <= 0 || height == fullScreenHeight) return
            fullScreenHeight = height
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FULL_SCREEN_HEIGHT, height).apply()
        }

    }

    /**
     * Full screen goes on or off: full screen shows the page from the top, and the normal view comes
     * back where it was. Called before the canvas is redrawn at its new size.
     */
    fun onFullScreenChange(on: Boolean) {
        val pageId = page.currentPageId
        if (on) {
            normalViewScroll[pageId] = page.scroll
            page.scroll = Offset.Zero
        } else {
            page.scroll = normalViewScroll.remove(pageId) ?: Offset.Zero
        }
        log.i("PAN_SPIKE: full screen ${if (on) "on" else "off"}, view at ${page.scroll}")
    }
}
