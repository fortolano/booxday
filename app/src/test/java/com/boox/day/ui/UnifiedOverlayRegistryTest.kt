package com.boox.day.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.boox.day.actionitems.ActionItemsUi
import com.boox.day.diagnostics.CrashReportGate
import com.boox.day.editor.DrawCanvas
import com.boox.day.meetings.MeetingNotesUi
import com.boox.day.meetings.MeetingPrepUi
import com.boox.day.meetings.MeetingRecapUi
import com.boox.day.proposals.ScheduleUi
import com.boox.day.share.ShareUi
import com.boox.day.ui.dialogs.BooxFreezeTip
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.ui.dialogs.SupportPopup
import com.boox.day.update.AppUpdateGate
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
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        BooxNotesPlaces.closeNotes()
        BooxNotesPlaces.setupOpen = false
        ActionItemsUi.close()
        MeetingNotesUi.closePicker()
        MeetingRecapUi.close()
        MeetingPrepUi.close()
        ScheduleUi.close()
        ShareUi.item = null
        SupportPopup.close()
        WeekUi.close()
        ctx.getSharedPreferences("calendar_ai_notice", Context.MODE_PRIVATE).edit().clear().commit()
        CalendarAiNotice.init(ctx)
        CalendarAiNotice.onCancel()
        AiKeyGate.close()
        AiKeyGate.closeResult(resume = false)
        AppUpdateGate.close()
        CrashReportGate.close(ctx)
        BooxFreezeTip.close(ctx)
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

        val frameClock = BroadcastFrameClock()
        val recomposerScope = CoroutineScope(Dispatchers.Unconfined + frameClock)
        val recomposer = Recomposer(recomposerScope.coroutineContext)
        recomposerScope.launch { recomposer.runRecomposeAndApplyChanges() }

        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var frameNanos = 1_000_000L
        fun pumpCompose() {
            Snapshot.sendApplyNotifications()
            frameNanos += 16_000_000L
            frameClock.sendFrame(frameNanos)
            shadowOf(Looper.getMainLooper()).idle()
        }

        try {
            val activity = controller.get()
            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setParentCompositionContext(recomposer)
                setContent {
                    BindOverlayRegistryToCanvas(registry)
                    overlayKeys.forEach { key ->
                        registry.Overlay(key = key, visible = states.getValue(key).value) {}
                    }
                }
            }
            activity.setContentView(composeView)
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
        } finally {
            recomposer.cancel()
            recomposerScope.cancel()
        }
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
