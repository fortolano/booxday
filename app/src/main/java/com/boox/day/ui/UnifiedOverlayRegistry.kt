package com.boox.day.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.boox.day.actionitems.ActionItemsUi
import com.boox.day.diagnostics.CrashReportGate
import com.boox.day.editor.DrawCanvas
import com.boox.day.meetings.MeetingNotesUi
import com.boox.day.ui.dialogs.BooxFreezeTip
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.update.AppUpdateGate

/**
 * Single source of truth for MainScreen overlays and raw-pen blocking (Phase 5 / H14).
 *
 * Every screen-scoped overlay renders through [Overlay] (or registers its visibility via [track]),
 * and [isBlockingCanvas] combines all registered screen overlays with every global overlay
 * singleton so an open modal can never silently leave BOOX raw drawing active underneath.
 */
@Stable
class UnifiedOverlayRegistry(
    private val globalBlocking: () -> Boolean = ::defaultGlobalOverlaysBlocking
) {
    private val activeOverlays = mutableStateMapOf<String, Boolean>()

    fun setOpen(key: String, open: Boolean) {
        if (open) {
            if (activeOverlays[key] != true) activeOverlays[key] = true
        } else {
            activeOverlays.remove(key)
        }
    }

    val activeKeys: Set<String>
        get() = activeOverlays.keys.toSet()

    val isBlockingCanvas: Boolean
        get() = activeOverlays.isNotEmpty() || globalBlocking()

    /**
     * Render [content] when [visible] is true AND guarantee [isBlockingCanvas] reflects [visible]
     * for the full lifecycle of this slot (clearing on hide or composition disposal).
     */
    @Composable
    fun Overlay(
        key: String,
        visible: Boolean,
        content: @Composable () -> Unit
    ) {
        Track(key = key, visible = visible)
        if (visible) {
            content()
        }
    }

    /**
     * Track an overlay whose UI lives elsewhere (e.g. a popup owned by a child header composable).
     */
    @Composable
    fun Track(key: String, visible: Boolean) {
        DisposableEffect(key, visible) {
            setOpen(key, visible)
            onDispose { setOpen(key, false) }
        }
    }

    companion object {
        /** Every process/global overlay singleton that sits over the drawing canvas. */
        fun defaultGlobalOverlaysBlocking(): Boolean =
            BooxNotesPlaces.notesOpen ||
                BooxNotesPlaces.setupOpen ||
                ActionItemsUi.isOpen ||
                MeetingNotesUi.isOpen ||
                AiKeyGate.isOpen ||
                AppUpdateGate.isOpen ||
                CrashReportGate.isOpen ||
                CalendarAiNotice.isOpen ||
                BooxFreezeTip.isOpen ||
                AppOverlays.isOpen ||
                FullScreenState.transitioning
    }
}

@Composable
fun rememberUnifiedOverlayRegistry(): UnifiedOverlayRegistry = remember { UnifiedOverlayRegistry() }

/**
 * Publishes [UnifiedOverlayRegistry.isBlockingCanvas] to [DrawCanvas.mainScreenModalVisible]
 * and guarantees cleanup on disposal.
 */
@Composable
fun BindOverlayRegistryToCanvas(registry: UnifiedOverlayRegistry) {
    val blocking = registry.isBlockingCanvas
    SideEffect {
        DrawCanvas.mainScreenModalVisible.value = blocking
    }
    DisposableEffect(Unit) {
        onDispose {
            DrawCanvas.mainScreenModalVisible.value = false
        }
    }
}
