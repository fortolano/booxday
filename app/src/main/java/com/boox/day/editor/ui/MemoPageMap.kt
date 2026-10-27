package com.boox.day.editor.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.boox.day.editor.MemoNavigator
import com.boox.day.editor.PageMapGeometry
import com.boox.day.ui.FullScreenState

/** A map drag whose finger or pen rests this long lets the canvas catch up (one refresh per stop). */
private const val MAP_DRAG_SETTLE_MS = 300L

/** Movement under this many pixels is a tap, not a drag. */
private const val MAP_TAP_SLOP_PX = 12f

private val MAP_MARGIN = 8.dp

/**
 * What the page map shows (Oct 2026, memo canvas navigation plan, Phase 0 spike). MemoNavigator sets it;
 * [MemoPageMap] draws it. Never open while the pen writes: the BOOX pen copies the screen when it comes
 * back, map included.
 */
object PageMapState {
    var visible by mutableStateOf(false)
        private set

    /** Opened from the menu (tap, drag, pen or finger) rather than shown during a two-finger drag. */
    var interactive by mutableStateOf(false)
        private set

    var geometry by mutableStateOf<PageMapGeometry?>(null)
        private set

    var picture by mutableStateOf<ImageBitmap?>(null)

    /** The view's frame in map pixels. */
    var frame by mutableStateOf<RectF?>(null)

    fun show(map: PageMapGeometry, interactive: Boolean, frame: RectF) {
        geometry = map
        this.frame = frame
        this.interactive = interactive
        visible = true
    }

    fun hide() {
        visible = false
        interactive = false
    }
}

/**
 * The canvas host's part (Oct 2026): [navigator] is the shown Memos page's, and a page, tab or full
 * screen change closes the map (its canvas is about to change under it).
 */
@Composable
fun MemoNavigationEffects(navigator: MemoNavigator?, pageId: String, onMemos: Boolean) {
    LaunchedEffect(navigator) { MemoNavigator.current = navigator }
    LaunchedEffect(pageId, onMemos, FullScreenState.isOn) { MemoNavigator.current?.closeMap() }
}

/**
 * The page map on the right edge of the Memos canvas: the whole page area with a frame for the part
 * on screen. Opened from the menu, a tap centres the view, a drag moves the frame and the canvas
 * catches up when it rests or lifts, and a touch anywhere else closes it (back to writing).
 */
@Composable
fun MemoPageMap() {
    val state = PageMapState
    if (!state.visible) return
    val map = state.geometry ?: return
    val density = LocalDensity.current
    val width = with(density) { map.width.toDp() }
    val height = with(density) { map.height.toDp() }

    Box(Modifier.fillMaxSize()) {
        if (state.interactive) {
            // Any touch on the canvas outside the map: back to writing
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            MemoNavigator.current?.closeMap()
                        }
                    }
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = MAP_MARGIN, end = MAP_MARGIN)
                .size(width, height)
                .background(Color.White)
                .border(1.dp, Color.Black)
                .then(if (state.interactive) Modifier.pointerInput(map) { mapGestures() } else Modifier)
        ) {
            state.picture?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            state.frame?.let { frame ->
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(frame.left, frame.top),
                        size = Size(frame.width(), frame.height()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.mapGestures() {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val navigator = MemoNavigator.current ?: return@awaitEachGesture
        val frame = PageMapState.frame ?: return@awaitEachGesture
        // A drag that starts outside the frame first puts the frame's middle there
        var corner = if (frame.contains(down.position.x, down.position.y)) Offset(frame.left, frame.top)
        else Offset(down.position.x - frame.width() / 2f, down.position.y - frame.height() / 2f)
        var last = down.position
        var dragging = false
        var unshown = false
        while (true) {
            val event = withTimeoutOrNull(MAP_DRAG_SETTLE_MS) { awaitPointerEvent() }
            if (event == null) {
                // Resting: the canvas catches up once per stop
                if (unshown) {
                    navigator.mapDragTo(corner)
                    unshown = false
                }
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            if (!dragging && (change.position - down.position).getDistance() > MAP_TAP_SLOP_PX) dragging = true
            if (dragging) {
                // The frame stops at the page edge, and turns back at once when the drag does
                navigator.frameForCorner(corner + (change.position - last))?.let {
                    PageMapState.frame = it
                    corner = Offset(it.left, it.top)
                }
                unshown = true
            }
            last = change.position
        }
        if (!dragging) navigator.mapTap(down.position) else if (unshown) navigator.mapDragTo(corner)
    }
}
