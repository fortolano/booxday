package com.boox.day.ui.permissions

import android.app.AppOpsManager
import android.app.Application
import android.os.Process
import com.boox.day.booxnotes.BooxNotesAccess
import com.boox.day.ui.dialogs.reminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Permission buttons always do something (Oct 2026): Android's prompt first, Settings only once
 * Android answers without showing it; the reminder row says what stands in the way; Boox Notes
 * access is checked the right way on Android 10 and on 11+.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PermissionAskTest {

    @Test
    fun promptFirstSettingsOnlyAfterAnInstantDenial() {
        assertEquals(AskState.GRANTED, PermissionAsks.stateFor(granted = true, instantDenialSeen = true))
        assertEquals(AskState.CAN_ASK, PermissionAsks.stateFor(granted = false, instantDenialSeen = false))
        assertEquals(AskState.OPEN_SETTINGS, PermissionAsks.stateFor(granted = false, instantDenialSeen = true))
    }

    @Test
    fun aDismissedOrRefusedPromptIsNotDeniedForGood() {
        // Instant: no prompt was on screen
        assertTrue(PermissionAsks.isInstantDenial(granted = false, answerAfterMs = 40))
        // The user saw the prompt and refused or dismissed it: ask again next time
        assertFalse(PermissionAsks.isInstantDenial(granted = false, answerAfterMs = 2_500))
        assertFalse(PermissionAsks.isInstantDenial(granted = true, answerAfterMs = 40))
    }

    @Test
    fun reminderStatusSaysWhatStandsInTheWay() {
        assertEquals("Off", reminderStatus(on = false, notificationsAllowed = false, channelOn = false, batteryOk = false))
        assertEquals("On", reminderStatus(on = true, notificationsAllowed = true, channelOn = true, batteryOk = true))
        assertTrue(reminderStatus(true, false, true, true).contains("notifications are off"))
        assertTrue(reminderStatus(true, true, false, true).contains("reminder type is off"))
        assertTrue(reminderStatus(true, true, true, false).contains("may be late"))
    }

    @Test
    fun booxNotesAccessOnAndroid11Plus() {
        val app = RuntimeEnvironment.getApplication()
        org.robolectric.shadows.ShadowEnvironment.addExternalDir("sdcard")
        val appOps = shadowOf(app.getSystemService(AppOpsManager::class.java))
        appOps.setMode("android:manage_external_storage", Process.myUid(), app.packageName, AppOpsManager.MODE_ERRORED)
        assertFalse(BooxNotesAccess.has(app))
        appOps.setMode("android:manage_external_storage", Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        assertTrue(BooxNotesAccess.has(app))
    }

    @Test
    @Config(sdk = [29])
    fun booxNotesAccessOnAndroid10NeedsTheStoragePermission() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(BooxNotesAccess.usesRuntimePermission)
        assertFalse(BooxNotesAccess.has(app))  // it said yes here, and the scan never ran
        shadowOf(app).grantPermissions(*BooxNotesAccess.LEGACY_PERMISSIONS)
        assertTrue(BooxNotesAccess.has(app))
    }
}
