package com.boox.day.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.boox.day.editor.DrawCanvas
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.ui.dialogs.SupportPopup
import com.boox.day.week.WeekUi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Overlay contract test (Phase 5 / H14):
 * Verifies that every registered screen overlay and every global overlay singleton drives
 * [UnifiedOverlayRegistry.isBlockingCanvas] (and [DrawCanvas.mainScreenModalVisible]) to true
 * while open, and clears cleanly when dismissed or disposed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UnifiedOverlayRegistryTest {

    @Before
    fun resetGlobals() {
        BooxNotesPlaces.closeNotes()
        BooxNotesPlaces.setupOpen = false
        SupportPopup.close()
        WeekUi.close()
        CalendarAiNotice.onCancel()
        AiKeyGate.close()
        shadowOf(Looper.getMainLooper()).idle()
        DrawCanvas.mainScreenModalVisible.value = false
    }

    @After
    fun cleanupGlobals() {
        resetGlobals()
    }

    @Test
    fun registeredOverlaysBlockCanvasWhileVisibleAndClearOnDismissOrDispose() {
        val registry = UnifiedOverlayRegistry()
        val overlayKeys = listOf(
            "calendar_accounts",
            "calendar_settings",
            "add_local_calendar",
            "add_edit_event",
            "event_details",
            "voice_event",
            "preferences",
            "ai_settings",
            "voice_settings",
            "data_backup",
            "search",
            "help",
            "about",
            "day_quick_view",
            "header_popup",
            "task_modals"
        )
        val states = overlayKeys.associateWith { mutableStateOf(false) }

        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            BindOverlayRegistryToCanvas(registry)
            overlayKeys.forEach { key ->
                registry.Overlay(key = key, visible = states.getValue(key).value) {}
            }
        }
        fun pumpCompose() {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }

        pumpCompose()

        assertFalse("No overlays open -> canvas must not be blocked", registry.isBlockingCanvas)
        assertFalse(DrawCanvas.mainScreenModalVisible.value)

        // Every registered overlay must block canvas while open and unblock when closed
        for (key in overlayKeys) {
            states.getValue(key).value = true
            pumpCompose()
            assertTrue("Overlay '$key' must block canvas while visible", registry.isBlockingCanvas)
            assertTrue("DrawCanvas.mainScreenModalVisible must be true for '$key'", DrawCanvas.mainScreenModalVisible.value)

            states.getValue(key).value = false
            pumpCompose()
            assertFalse("Closing '$key' must unblock canvas", registry.isBlockingCanvas)
            assertFalse(DrawCanvas.mainScreenModalVisible.value)
        }

        // Overlapping overlays: closing one keeps canvas blocked until the last one closes
        states.getValue("calendar_settings").value = true
        states.getValue("add_local_calendar").value = true
        pumpCompose()
        assertTrue(registry.isBlockingCanvas)

        states.getValue("add_local_calendar").value = false
        pumpCompose()
        assertTrue("Still blocked while parent overlay remains open", registry.isBlockingCanvas)

        // Destroying activity disposes overlays and clears DrawCanvas.mainScreenModalVisible
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Disposing host must clear DrawCanvas.mainScreenModalVisible", DrawCanvas.mainScreenModalVisible.value)
    }

    @Test
    fun globalOverlaySingletonsBlockCanvasThroughUnifiedRegistry() {
        val registry = UnifiedOverlayRegistry()
        assertFalse(registry.isBlockingCanvas)

        SupportPopup.open()
        assertTrue("SupportPopup (AppOverlays) must block canvas", registry.isBlockingCanvas)
        SupportPopup.close()
        assertFalse(registry.isBlockingCanvas)

        WeekUi.open("2026-11-25")
        assertTrue("WeekUi (AppOverlays) must block canvas", registry.isBlockingCanvas)
        WeekUi.close()
        assertFalse(registry.isBlockingCanvas)

        BooxNotesPlaces.setupOpen = true
        assertTrue("BooxNotesPlaces.setupOpen must block canvas", registry.isBlockingCanvas)
        BooxNotesPlaces.setupOpen = false
        assertFalse(registry.isBlockingCanvas)

        CalendarAiNotice.afterNotice {}
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("CalendarAiNotice must block canvas while open", registry.isBlockingCanvas)
        CalendarAiNotice.onCancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(registry.isBlockingCanvas)

        AiKeyGate.open(AiKeyGate.Reason.KEY_REJECTED)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("AiKeyGate must block canvas while open", registry.isBlockingCanvas)
        AiKeyGate.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(registry.isBlockingCanvas)
    }
}
