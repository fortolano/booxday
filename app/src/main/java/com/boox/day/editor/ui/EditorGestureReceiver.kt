package com.boox.day.editor.ui

import android.content.Context
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.core.content.FileProvider
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.TextAnnotation
import java.io.File
import android.os.SystemClock
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.EditorControlTower
import com.boox.day.editor.MemoNavigator
import com.boox.day.editor.state.PAN_PEN_GUARD_MS
import com.boox.day.editor.state.PAN_RELAND_GRACE_MS
import com.boox.day.editor.state.PAN_START_MM
import com.boox.day.editor.state.PointerTracker
import com.boox.day.editor.state.isTwoFingerPan
import com.boox.day.editor.state.panStartPx
import com.boox.day.ui.FullScreenState
import com.boox.day.editor.state.DOUBLE_TAP_MIN_MS
import com.boox.day.editor.state.DOUBLE_TAP_TIMEOUT_MS
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.GestureMode
import com.boox.day.editor.state.GestureState
import com.boox.day.editor.state.HOLD_THRESHOLD_MS
import com.boox.day.editor.state.PINCH_ZOOM_THRESHOLD
import com.boox.day.editor.state.ResizeHandle
import com.boox.day.editor.state.SWIPE_THRESHOLD
import com.boox.day.ui.showHint
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

private val log = TaggedLog("GestureReceiver")


@Composable
@ExperimentalComposeUiApi
fun EditorGestureReceiver(
    controlTower: EditorControlTower,
    editorState: EditorState? = null,
) {

    val coroutineScope = rememberCoroutineScope()
    val appSettings = remember { GlobalAppSettings.current }
    var crossPosition by remember { mutableStateOf<IntOffset?>(null) }
    var rectangleBounds by remember { mutableStateOf<Rect?>(null) }
    val view = LocalView.current
    val context = LocalContext.current

    // Text box state for tap detection
    val textBoxState = editorState?.textBoxState
    val pageId = editorState?.currentPageId ?: ""
    val scroll = editorState?.pageView?.scroll ?: Offset.Zero
    val zoomLevel by editorState?.pageView?.zoomLevel?.collectAsState() ?: remember { mutableStateOf(1f) }
    // Memos two-finger drag (Oct 2026): the distance both fingers move before the page follows
    val panStartPx = remember { panStartPx().also { log.i("PAN_SPIKE: drag starts after $it px (${PAN_START_MM} mm)") } }
    Box(
        modifier = Modifier
            // TODO: Change to // .pointerInteropFilter { ev -> ……}
            // for now it consumes all gestures - even stylus one.
            // CRITICAL FIX: Key on pageId to recreate lambda when page changes
            // Without this, the captured pageId becomes stale after page navigation,
            // causing text box tap detection to query the wrong page's annotations.
            .pointerInput(pageId) {
                awaitEachGesture {
                    // Set while a two-finger drag moves the page, so any way out of the gesture ends it
                    var dragNavigator: MemoNavigator? = null
                    try {
                        // Detect initial touch
                        val down = awaitFirstDown()

                        // Ignore non-touch input
                        if (down.type == PointerType.Stylus) {
                            log.i("Redirecting stylus input")

                            // TODO: It's only temporary workaround.
                            // Track all moves until the stylus is lifted
                            do {
                                val event = awaitPointerEvent()
                                val stylus =
                                    event.changes.firstOrNull { it.type == PointerType.Stylus }
                                // Phase 4 (Oct 2026): no coroutine per stylus move any more — its only
                                // consumer (the GL eraser trail) never draws, the front-buffer
                                // renderer is never created (notable d2cf6451d7)
                                stylus?.consume()
                            } while (stylus?.pressed == true)

                            return@awaitEachGesture
                        }

                        // testing if it will fixed exception:
                        // kotlinx.coroutines.CompletionHandlerException: Exception in resume
                        // onCancellation handler for CancellableContinuation(DispatchedContinuation[AndroidUiDispatcher@145d639,
                        // Continuation at androidx.compose.foundation.gestures.PressGestureScopeImpl.reset(TapGestureDetector.kt:357)
                        // @8b7a2c]){Completed}@4a49cf5
                        if (!coroutineScope.isActive) return@awaitEachGesture
                        // if window lost focus, ignore input
                        if (!view.hasWindowFocus()) return@awaitEachGesture

                        // GUARD: Skip ALL gesture processing if text field is open
                        // This prevents accidental state clearing and gesture conflicts during text editing
                        if (textBoxState?.editingTextBox != null) {
                            log.d("Text field is open - ignoring gesture, letting Compose handle it")
                            return@awaitEachGesture
                        }

                        // GUARD: Close expanded events panel when canvas is touched (if not pinned)
                        // Signal to MainScreen to close the overlay, then return to avoid processing this touch as a gesture
                        if (DrawCanvas.eventsOverlayExpanded.value) {
                            log.i("Canvas touched (finger) while events expanded - signaling to close")
                            DrawCanvas.canvasInteraction.tryEmit(Unit)
                            return@awaitEachGesture
                        }

                        val gestureState = GestureState(scope = coroutineScope)
                        var overdueScroll = Offset.Zero

                        // Ignore non-touch input
                        if (down.type != PointerType.Touch) {
                            log.i("Ignoring non-touch input")
                            return@awaitEachGesture
                        }
                        gestureState.initialTimestamp = System.currentTimeMillis()
                        gestureState.insertPosition(down)
                        // Finger geometry for the two-finger drag (Notable's, handles dropped contacts)
                        val tracker = PointerTracker(now = { SystemClock.uptimeMillis() })
                        tracker.update(down)
                        var dragRefusalLogged = false
                        var dragTried = false

                        do {
                            // wait for second gesture
                            val event =
                                withTimeoutOrNull(HOLD_THRESHOLD_MS.toLong()) { awaitPointerEvent() }
                            if (!coroutineScope.isActive) return@awaitEachGesture
                            // if window lost focus, ignore input
                            if (!view.hasWindowFocus()) return@awaitEachGesture

                            if (event != null) {
                                val fingerChange =
                                    event.changes.filter { it.type == PointerType.Touch }

                                // is already consumed return
                                if (fingerChange.find { it.isConsumed } != null) {
                                    log.i("Canceling gesture - already consumed")
                                    if (gestureState.gestureMode == GestureMode.Selection) {
                                        crossPosition = null
                                        rectangleBounds = null
                                        gestureState.gestureMode = GestureMode.Normal
                                        controlTower.setIsDrawing(true)
                                    }
                                    return@awaitEachGesture
                                }
                                fingerChange.forEach { change ->
                                    // Consume changes and update positions
                                    change.consume()
                                    gestureState.insertPosition(change)
                                    tracker.update(change)
                                }
                                if (fingerChange.any { !it.pressed }) {
                                    gestureState.lastTimestamp = System.currentTimeMillis()
                                    // A two-finger drag goes on while a finger stays, and through a
                                    // dropped contact: e-ink panels lose a finger for a few ms
                                    if (gestureState.gestureMode != GestureMode.Pan) break
                                    if (tracker.pressedCount() == 0) {
                                        val reland = withTimeoutOrNull(PAN_RELAND_GRACE_MS) {
                                            var next = awaitPointerEvent()
                                            while (next.changes.none { it.type == PointerType.Touch && it.pressed }) {
                                                next = awaitPointerEvent()
                                            }
                                            next
                                        } ?: break
                                        reland.changes.filter { it.type == PointerType.Touch }.forEach {
                                            it.consume()
                                            tracker.update(it)
                                        }
                                    }
                                }
                            }
                            // events are only send on change, so we need to check for holding in place separately
                            gestureState.lastTimestamp = System.currentTimeMillis()
                            if (gestureState.gestureMode == GestureMode.Selection) {
                                crossPosition = gestureState.getLastPositionIO()
                                rectangleBounds = gestureState.calculateRectangleBounds()
                            } else {
                                    // set selection mode
                                // ⚠️ STYLUS GUARD (Dec 2025): Skip hold detection if stylus was recently active
                                // This prevents palm touches during writing from triggering selection mode.
                                if (gestureState.isHoldingOneFinger() && !DrawCanvas.isStylusRecentlyActive()) {
                                    val holdPosition = gestureState.getLastPositionIO()
                                    if (holdPosition != null) {
                                        val holdOffset = Offset(holdPosition.x.toFloat(), holdPosition.y.toFloat())
                                        
                                        // 1. Check if holding on an attachment
                                        val attachmentAtPoint = findAttachmentAtScreenPoint(
                                            holdOffset, pageId, scroll, zoomLevel
                                        )
                                        if (attachmentAtPoint != null) {
                                            // Directly select this attachment
                                            log.i("Long-press on attachment: ${attachmentAtPoint.displayName}")
                                            coroutineScope.launch {
                                                DrawCanvas.selectSingleAttachment.emit(attachmentAtPoint)
                                            }
                                            showHint("Attachment selected!", coroutineScope, 1500)
                                            return@awaitEachGesture
                                        }

                                        // 2. Check if holding on an Image (e.g. Table)
                                        val imageAtPoint = findImageAtScreenPoint(
                                            holdOffset, pageId, scroll, zoomLevel
                                        )
                                        if (imageAtPoint != null) {
                                            log.i("Long-press on image: ${imageAtPoint.id}")
                                            coroutineScope.launch {
                                                DrawCanvas.selectSingleImage.emit(imageAtPoint)
                                            }
                                            showHint("Image selected!", coroutineScope, 1500)
                                            return@awaitEachGesture
                                        }

                                        // 3. Check if holding on a TextAnnotation (text box)
                                        val textAnnotationAtPoint = findTextAnnotationAtScreenPoint(
                                            holdOffset, pageId, scroll, zoomLevel
                                        )
                                        if (textAnnotationAtPoint != null && textBoxState != null) {
                                            log.i("Long-press on text box: ${textAnnotationAtPoint.id}")
                                            textBoxState.selectTextBox(textAnnotationAtPoint)
                                            controlTower.setIsDrawing(false)
                                            coroutineScope.launch {
                                                DrawCanvas.forceUpdate.emit(null)
                                            }
                                            showHint("Text box selected!", coroutineScope, 1500)
                                            return@awaitEachGesture
                                        }
                                    }

                                    gestureState.gestureMode = GestureMode.Selection
                                    controlTower.setIsDrawing(false) // unfreeze the screen
                                    crossPosition = gestureState.getLastPositionIO()
                                    rectangleBounds = gestureState.calculateRectangleBounds()
                                    showHint("Selection mode!", coroutineScope, 1500)
                                }
                                gestureState.checkSmoothScrolling()
                                gestureState.checkContinuousZoom()
                                val onMemos = DrawCanvas.memoModeChanged.value
                                if (onMemos) {
                                    // Memos: two fingers moving together move the page (no hold first)
                                    if (gestureState.gestureMode == GestureMode.Normal && !dragTried &&
                                        isTwoFingerPan(tracker.pressedTravels(), panStartPx)
                                    ) {
                                        val refusal = memoDragRefusal(editorState)
                                        if (refusal != null) {
                                            if (!dragRefusalLogged) log.i("PAN_SPIKE: two fingers moved together, no drag: $refusal")
                                            dragRefusalLogged = true
                                        } else {
                                            dragTried = true  // once per gesture (the page may fit the view)
                                            val (a, b) = tracker.pressedTravels()
                                            val travel = (a + b) / 2f
                                            if (controlTower.navigator.beginDrag(travel)) {
                                                dragNavigator = controlTower.navigator
                                                gestureState.gestureMode = GestureMode.Pan
                                                tracker.consumeDragDelta()  // the deltas count from here
                                                controlTower.navigator.moveBy(travel)  // the page catches up
                                            }
                                        }
                                    }
                                } else if (gestureState.checkHoldingTwoFingers()) {
                                    showHint("Drag mode!", coroutineScope, 1500)
                                }

                            }
                            if (gestureState.gestureMode == GestureMode.Scroll) {
                                val delta = gestureState.getVerticalDragDelta()
                                overdueScroll = controlTower.processScroll(
                                    delta = Offset(overdueScroll.x, overdueScroll.y + delta)
                                )
                            }
                            // ⚠️ PINCH-TO-ZOOM DISABLED (Dec 2025): Prevents accidental zoom when holding device
                            // Unlike other gestures, zoom bypassed the GestureAction system entirely.
                            // To re-enable, uncomment this block and the discrete zoom block below.
                            // if (gestureState.gestureMode == GestureMode.Zoom) {
                            //     val delta = gestureState.getPinchDelta()
                            //     controlTower.onPinchToZoom(delta, gestureState.getPinchCenter())
                            // }

                            if (gestureState.gestureMode == GestureMode.Drag) {
                                val delta = gestureState.getTotalDragDelta()
                                overdueScroll =
                                    controlTower.processScroll(delta = overdueScroll + delta)
                            }

                            if (gestureState.gestureMode == GestureMode.Pan) {
                                val delta = tracker.consumeDragDelta()
                                if (delta != Offset.Zero) controlTower.navigator.moveBy(delta)
                            }

                        } while (true)

                        when (gestureState.gestureMode) {
                            GestureMode.Selection -> {
                                resolveGesture(
                                    settings = appSettings,
                                    default = AppSettings.defaultHoldAction,
                                    override = AppSettings::holdAction,
                                    scope = coroutineScope,
                                    rectangle = rectangleBounds!!,
                                    controlTower = controlTower
                                )
                                crossPosition = null
                                rectangleBounds = null
                                gestureState.gestureMode = GestureMode.Normal
                                controlTower.setIsDrawing(true)
                                return@awaitEachGesture
                            }

                            GestureMode.Scroll -> {
                                gestureState.gestureMode =
                                    GestureMode.Normal // return screen updates to normal.
                                return@awaitEachGesture
                            }

                            GestureMode.Zoom, GestureMode.Drag -> {
                                log.d("Zoom or drag -- final redraw")
                                coroutineScope.launch {
                                    // we need to redraw if we zoomed in only -- for now we will just always redraw after exiting gesture.
                                    DrawCanvas.forceUpdate.emit(null)
                                }
                                gestureState.gestureMode =
                                    GestureMode.Normal // return screen updates to normal.
                                return@awaitEachGesture
                            }

                            GestureMode.Pan -> {
                                // The view stays where the fingers left it; the navigator brings the pen back
                                dragNavigator?.endDrag()
                                dragNavigator = null
                                gestureState.gestureMode = GestureMode.Normal
                                return@awaitEachGesture
                            }

                            GestureMode.Normal -> {
                                // Phase 0: two-finger touches that didn't move the page (palms, pinches)
                                if (tracker.maxConcurrentPressed >= 2 && DrawCanvas.memoModeChanged.value) {
                                    log.i(
                                        "PAN_SPIKE: two-finger touch, no drag: fingers=${tracker.maxConcurrentPressed} " +
                                            "centre moved ${tracker.netCentroidTravel()} total=${tracker.totalTravel().toInt()} px " +
                                            "in ${tracker.inputDuration()} ms, pen ${System.currentTimeMillis() - DrawCanvas.lastStylusActivityMs} ms ago"
                                    )
                                }
                            }
                        }

                        if (!coroutineScope.isActive) return@awaitEachGesture
                        // if window lost focus, ignore input
                        if (!view.hasWindowFocus()) return@awaitEachGesture


                        if (gestureState.isOneFinger()) {
                            if (gestureState.isOneFingerTap()) {
                                // Check for attachment tap first (highest priority - tap to open)
                                val tapPositionIO = gestureState.getLastPositionIO()
                                var tapPosition: Offset? = null
                                if (tapPositionIO != null) {
                                    tapPosition = Offset(
                                        tapPositionIO.x.toFloat(),
                                        tapPositionIO.y.toFloat()
                                    )
                                }

                                // Check attachment tap BEFORE text box tap
                                if (tapPosition != null) {
                                    val attachmentHandled = handleAttachmentTap(
                                        context = context,
                                        tapPosition = tapPosition,
                                        pageId = pageId,
                                        scroll = scroll,
                                        zoomLevel = zoomLevel
                                    )
                                    if (attachmentHandled) {
                                        log.i("Tap handled by attachment")
                                        return@awaitEachGesture
                                    }
                                }

                                if (textBoxState != null && tapPosition != null) {
                                    val handled = handleTextBoxTap(
                                        tapPosition = tapPosition,
                                        pageId = pageId,
                                        scroll = scroll,
                                        zoomLevel = zoomLevel,
                                        textBoxState = textBoxState,
                                        editorState = editorState
                                    )
                                    if (handled) {
                                        log.i("Tap handled by text box")
                                        return@awaitEachGesture
                                    }
                                }

                                if (withTimeoutOrNull(DOUBLE_TAP_TIMEOUT_MS) {
                                        val secondDown = awaitFirstDown()
                                        val deltaTime =
                                            System.currentTimeMillis() - gestureState.lastTimestamp
                                        log.v("Second down detected: ${secondDown.type}, position: ${secondDown.position}, deltaTime: $deltaTime")
                                        if (deltaTime < DOUBLE_TAP_MIN_MS) {
                                            showHint(
                                                text = "Too quick for double click! time between: $deltaTime",
                                                coroutineScope
                                            )
                                            return@withTimeoutOrNull null
                                        } else {
                                            log.v("double click!")
                                        }
                                        if (secondDown.type != PointerType.Touch) {
                                            log.i("Ignoring non-touch input during double-tap detection")
                                            return@withTimeoutOrNull null
                                        }
                                        resolveGesture(
                                            settings = appSettings,
                                            default = AppSettings.defaultDoubleTapAction,
                                            override = AppSettings::doubleTapAction,
                                            scope = coroutineScope,
                                            controlTower = controlTower
                                        )


                                    } != null) return@awaitEachGesture
                            }
                        } else if (gestureState.isTwoFingers()) {
                            log.v("Two finger tap")
                            if (gestureState.isTwoFingersTap()) {
                                resolveGesture(
                                    settings = appSettings,
                                    default = AppSettings.defaultTwoFingerTapAction,
                                    override = AppSettings::twoFingerTapAction,
                                    scope = coroutineScope,
                                    controlTower = controlTower,
                                    context = context  // For Paste action
                                )
                            }
                            // ⚠️ PINCH-TO-ZOOM DISABLED (Dec 2025): Discrete zoom - see continuous zoom comment above
                            // val zoomDelta = gestureState.getPinchDrag()
                            // if (!appSettings.continuousZoom && abs(zoomDelta) > PINCH_ZOOM_THRESHOLD) {
                            //     controlTower.onPinchToZoom(zoomDelta, Offset(0f, 0f))
                            //     log.d("Discrete zoom: $zoomDelta")
                            // }
                        }

                        val horizontalDrag = gestureState.getHorizontalDrag()
                        val verticalDrag = gestureState.getVerticalDrag()

                        log.v("horizontalDrag $horizontalDrag, verticalDrag $verticalDrag")


                        if (gestureState.gestureMode == GestureMode.Normal) {
                            if (horizontalDrag < -SWIPE_THRESHOLD)
                                resolveGesture(
                                    settings = appSettings,
                                    default = if (gestureState.getInputCount() == 1) AppSettings.defaultSwipeLeftAction else AppSettings.defaultTwoFingerSwipeLeftAction,
                                    override = if (gestureState.getInputCount() == 1) AppSettings::swipeLeftAction else AppSettings::twoFingerSwipeLeftAction,
                                    scope = coroutineScope,
                                    controlTower = controlTower
                                )
                            else if (horizontalDrag > SWIPE_THRESHOLD)
                                resolveGesture(
                                    settings = appSettings,
                                    default = if (gestureState.getInputCount() == 1) AppSettings.defaultSwipeRightAction else AppSettings.defaultTwoFingerSwipeRightAction,
                                    override = if (gestureState.getInputCount() == 1) AppSettings::swipeRightAction else AppSettings::twoFingerSwipeRightAction,
                                    scope = coroutineScope,
                                    controlTower = controlTower
                                )
                        }
                        if (!GlobalAppSettings.current.smoothScroll && gestureState.isOneFinger()
                            && abs(verticalDrag) > SWIPE_THRESHOLD
                        ) {
                            log.d("Discrete scrolling, verticalDrag: $verticalDrag")
                            controlTower.processScroll(Offset(0f, verticalDrag))
                        }
                    } catch (e: CancellationException) {
                        log.d("Gesture coroutine canceled", e)
                    } catch (e: Exception) {
                        log.e("Unexpected error in gesture handling", e)
                    } finally {
                        // A drag cut short (page change, lost focus, an error) still brings the pen back
                        dragNavigator?.endDrag()
                    }
                }
            }

            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val density = LocalDensity.current
        // Draw cross where finger is touching
        DrawCross(crossPosition, density)
        // Draw the rectangle while dragging
        DrawRectangle(rectangleBounds, density)
    }
}

/**
 * Why the Memos page may not move now, or null when it may: no popup, selection, text box or full
 * screen switch, and the pen at rest (a palm next to the writing hand must not move the page).
 */
private fun memoDragRefusal(editorState: EditorState?): String? = when {
    DrawCanvas.isCanvasInputBlockedByOverlay() -> "a popup is open"
    FullScreenState.transitioning -> "full screen is switching"
    editorState?.selectionState?.isNonEmpty() == true -> "a selection is on the page"
    editorState?.textBoxState?.selectedTextBox != null -> "a text box is selected"
    DrawCanvas.isStylusActiveWithin(PAN_PEN_GUARD_MS) ->
        "the pen was used ${System.currentTimeMillis() - DrawCanvas.lastStylusActivityMs} ms ago"
    else -> null
}

@Composable
private fun DrawRectangle(rectangleBounds: Rect?, density: Density) {
    rectangleBounds?.let { bounds ->
        // Draw the rectangle
        Box(
            Modifier
                .offset { IntOffset(bounds.left, bounds.top) }
                .size(
                    width = with(density) { (bounds.right - bounds.left).toDp() },
                    height = with(density) { (bounds.bottom - bounds.top).toDp() }
                )
                // Is there rendering speed difference between colors?
                .background(Color(0x55000000))
                .border(1.dp, Color.Black)
        )
    }

}

@Composable
private fun DrawCross(crossPosition: IntOffset?, density: Density) {
    // Draw target/bullseye where finger is touching
    crossPosition?.let { pos ->
        val centerDotSize = 8.dp
        val innerRingSize = 32.dp
        val outerRingSize = 56.dp
        val strokeWidth = 2.dp

        // Center dot (filled)
        Box(
            Modifier
                .offset {
                    IntOffset(
                        pos.x - with(density) { (centerDotSize / 2).toPx() }.toInt(),
                        pos.y - with(density) { (centerDotSize / 2).toPx() }.toInt()
                    )
                }
                .size(centerDotSize)
                .background(Color.Black, CircleShape)
        )

        // Inner ring
        Box(
            Modifier
                .offset {
                    IntOffset(
                        pos.x - with(density) { (innerRingSize / 2).toPx() }.toInt(),
                        pos.y - with(density) { (innerRingSize / 2).toPx() }.toInt()
                    )
                }
                .size(innerRingSize)
                .border(strokeWidth, Color.Black, CircleShape)
        )

        // Outer ring
        Box(
            Modifier
                .offset {
                    IntOffset(
                        pos.x - with(density) { (outerRingSize / 2).toPx() }.toInt(),
                        pos.y - with(density) { (outerRingSize / 2).toPx() }.toInt()
                    )
                }
                .size(outerRingSize)
                .border(strokeWidth, Color.Black, CircleShape)
        )
    }
}

private fun resolveGesture(
    settings: AppSettings?,
    default: AppSettings.GestureAction,
    override: AppSettings.() -> AppSettings.GestureAction?,
    scope: CoroutineScope,
    rectangle: Rect = Rect(),
    controlTower: EditorControlTower,
    context: Context? = null  // For paste action
) {
    when (if (settings != null) override(settings) else default) {
        null -> log.i("No Action (null)")
        // ⚠️ NO-TOUCH ZONE: PALM REJECTION FIX (Dec 2025) - Gestures disabled to prevent palm triggers
        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
        AppSettings.GestureAction.None -> log.v("No Action (None)")  // Explicitly disabled - DO NOT REMOVE
        // ⚠️ DAY PAGE NAVIGATION FIX (Dec 2025): Use day page methods for calendar mode
        // goToNextPage/goToPreviousPage require bookId (notebook mode) which is null in Memos/Tasks.
        // goToNextDayPage creates new page if on last; goToPreviousDayPage does nothing if on first.
        AppSettings.GestureAction.PreviousPage -> controlTower.goToPreviousDayPage()

        AppSettings.GestureAction.NextPage -> controlTower.goToNextDayPage()

        AppSettings.GestureAction.ChangeTool -> controlTower.toggleTool()

        AppSettings.GestureAction.ToggleZen -> controlTower.toggleZen()

        AppSettings.GestureAction.Undo -> controlTower.undo()

        AppSettings.GestureAction.Redo -> controlTower.redo()

        AppSettings.GestureAction.Select -> {
            log.i("select")
            scope.launch {
//                log.w( "rect in screen coord: $rectangle")
                DrawCanvas.rectangleToSelectByGesture.emit(rectangle)
            }
        }

        AppSettings.GestureAction.DeletePage -> {
            log.i("Delete page requested (two-finger tap)")
            scope.launch {
                DrawCanvas.requestDeletePageConfirmation.emit(Unit)
            }
        }

        AppSettings.GestureAction.Paste -> {
            log.i("Paste via gesture (two-finger tap)")
            if (context != null) {
                scope.launch {
                    controlTower.unifiedPaste(context)
                }
            } else {
                log.w("Paste gesture ignored - context not available")
            }
        }
    }
}

/**
 * Handle a tap on a text box.
 * Returns true if the tap was handled (tap was on a text box), false otherwise.
 */
private fun handleTextBoxTap(
    tapPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    editorState: EditorState?
): Boolean {
    // Convert screen coordinates to page coordinates
    val pageX = (tapPosition.x / zoomLevel) + scroll.x
    val pageY = (tapPosition.y / zoomLevel) + scroll.y

    log.d("Text box tap check at screen (${tapPosition.x}, ${tapPosition.y}) -> page ($pageX, $pageY)")

    // Get all text annotations for this page
    val annotations = PageDataManager.getTextAnnotations(pageId)

    if (annotations.isEmpty()) {
        // No text boxes on this page - check if we should clear selection
        if (textBoxState.hasSelection()) {
            log.i("Clearing text box selection (no text boxes)")
            textBoxState.clearSelection()
            return true
        }
        return false
    }

    // Find which annotation (if any) was tapped
    val tappedAnnotation = findAnnotationAtPoint(annotations, pageX, pageY)

    return when {
        tappedAnnotation != null -> {
            val currentlySelected = textBoxState.selectedTextBox

            if (currentlySelected?.id == tappedAnnotation.id) {
                // Already selected - check if we should enter edit mode
                val handle = textBoxState.getHandleAtPoint(tappedAnnotation, pageX, pageY, 40f)
                if (handle == ResizeHandle.NONE) {
                    // Tap inside the box - enter edit mode
                    log.i("Entering edit mode for text box ${tappedAnnotation.id}")
                    textBoxState.startEditing(tappedAnnotation)
                } else {
                    log.d("Tap on resize handle: $handle")
                }
            } else {
                // Select the tapped annotation
                log.i("Selecting text box ${tappedAnnotation.id}")
                textBoxState.selectTextBox(tappedAnnotation)
                // CRITICAL: Disable drawing IMMEDIATELY (same as lasso selection)
                editorState?.isDrawing = false
                
                // Force update to hide the text box on canvas (so it only shows in overlay)
                // This fixes the "Double Rendering" bug
                DrawCanvas.forceUpdate.tryEmit(null)
            }
            true
        }

        textBoxState.hasSelection() -> {
            // Tap outside any text box - clear selection
            log.i("Clearing text box selection (tap outside)")
            textBoxState.clearSelection()
            // Refresh canvas so the text box is drawn again (it was hidden while selected)
            DrawCanvas.forceUpdate.tryEmit(null)
            true
        }

        else -> {
            // No text boxes involved
            false
        }
    }
}

/**
 * Find the annotation at a given point (in page coordinates).
 */
private fun findAnnotationAtPoint(
    annotations: List<TextAnnotation>,
    x: Float,
    y: Float
): TextAnnotation? {
    return annotations.findLast { annotation ->
        x >= annotation.x &&
        x <= annotation.x + annotation.width &&
        y >= annotation.y &&
        y <= annotation.y + annotation.height
    }
}

/**
 * Handle a tap on an attachment chip.
 * Returns true if the tap was handled (tap was on an attachment), false otherwise.
 * Opens the file in the appropriate external app (e.g., NeoReader for epubs).
 */
private fun handleAttachmentTap(
    context: Context,
    tapPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float
): Boolean {
    // Convert screen coordinates to page coordinates
    val pageX = (tapPosition.x / zoomLevel) + scroll.x
    val pageY = (tapPosition.y / zoomLevel) + scroll.y

    log.d("Attachment tap check at screen (${tapPosition.x}, ${tapPosition.y}) -> page ($pageX, $pageY)")

    // Get all attachments for this page
    val attachments = PageDataManager.getAttachments(pageId)

    if (attachments.isEmpty()) {
        return false
    }

    // Find which attachment (if any) was tapped
    val tappedAttachment = findAttachmentAtPoint(attachments, pageX, pageY)

    if (tappedAttachment != null) {
        log.i("Attachment tapped: ${tappedAttachment.displayName} (${tappedAttachment.fileType})")
        openAttachmentFile(context, tappedAttachment)
        return true
    }

    return false
}

/**
 * Find the attachment at a given point (in page coordinates).
 */
private fun findAttachmentAtPoint(
    attachments: List<Attachment>,
    x: Float,
    y: Float
): Attachment? {
    return attachments.findLast { attachment ->
        x >= attachment.x &&
        x <= attachment.x + attachment.width &&
        y >= attachment.y &&
        y <= attachment.y + attachment.height
    }
}

/**
 * Open an attachment file in the appropriate external app.
 */
private fun openAttachmentFile(context: Context, attachment: Attachment) {
    val file = File(attachment.filePath)

    if (!file.exists()) {
        log.w("Attachment file not found: ${attachment.filePath}")
        Toast.makeText(context, "File not found: ${attachment.displayName}", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Use FileProvider to share external storage files with other apps
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",  // Authority from AndroidManifest.xml
            file
        )
        val mimeType = getMimeType(attachment.fileType)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
        log.i("Opened attachment: ${attachment.displayName}")
    } catch (e: ActivityNotFoundException) {
        log.e("No app found to open file: ${attachment.filePath}", e)
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        log.e("Failed to open attachment: ${e.message}", e)
        Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Get MIME type for a file extension.
 */
private fun getMimeType(fileType: String): String {
    return when (fileType.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.ebook"
        "djvu" -> "image/vnd.djvu"
        "cbz" -> "application/x-cbz"
        "cbr" -> "application/x-cbr"
        else -> "*/*"
    }
}

/**
 * Find the attachment at a given point (in page coordinates).
 */
private fun findAttachmentAtScreenPoint(
    screenPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float
): Attachment? {
    // Convert screen coordinates to page coordinates
    val pageX = (screenPosition.x / zoomLevel) + scroll.x
    val pageY = (screenPosition.y / zoomLevel) + scroll.y

    val attachments = PageDataManager.getAttachments(pageId)
    return attachments.findLast { attachment ->
        pageX >= attachment.x &&
        pageX <= attachment.x + attachment.width &&
        pageY >= attachment.y &&
        pageY <= attachment.y + attachment.height
    }
}

/**
 * Find the TextAnnotation at a given screen position.
 * Converts screen coordinates to page coordinates and checks all text annotations.
 */
private fun findTextAnnotationAtScreenPoint(
    screenPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float
): TextAnnotation? {
    // Convert screen coordinates to page coordinates
    val pageX = (screenPosition.x / zoomLevel) + scroll.x
    val pageY = (screenPosition.y / zoomLevel) + scroll.y

    val annotations = PageDataManager.getTextAnnotations(pageId)
    // Check in reverse order (topmost first)
    return annotations.findLast { annotation ->
        pageX >= annotation.x &&
        pageX <= annotation.x + annotation.width &&
        pageY >= annotation.y &&
        pageY <= annotation.y + annotation.height
    }
}

/**
 * Find the Image at a given screen position.
 * Converts screen coordinates to page coordinates and checks all images.
 */
private fun findImageAtScreenPoint(
    screenPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float
): Image? {
    // Convert screen coordinates to page coordinates
    val pageX = (screenPosition.x / zoomLevel) + scroll.x
    val pageY = (screenPosition.y / zoomLevel) + scroll.y

    val images = PageDataManager.getImages(pageId)
    // Check in reverse order (topmost first)
    return images.findLast { image ->
        pageX >= image.x &&
        pageX <= image.x + image.width &&
        pageY >= image.y &&
        pageY <= image.y + image.height
    }
}

