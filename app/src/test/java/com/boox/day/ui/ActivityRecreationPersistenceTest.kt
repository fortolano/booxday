package com.boox.day.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ProviderId
import com.boox.day.sync.MultiAccountSyncViewModel
import com.boox.day.sync.SignInErrorText
import com.boox.day.ui.dialogs.EventDraftFormState
import com.boox.day.ui.dialogs.rememberEventDraftFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Activity-recreation persistence tests (H10):
 * 1. MultiAccountSyncViewModel owned by ViewModelStore survives configuration change and restores
 *    pending Google Sign-In discriminator from SavedStateHandle across process/Activity recreation.
 * 2. selectedDate, dialog open state, and unsaved AddEditEventDialog draft fields survive a real
 *    Android saveInstanceState -> fresh Activity recreation cycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ActivityRecreationPersistenceTest {

    @Test
    fun multiAccountViewModelSurvivesConfigChangeAndRestoresPendingGoogleSignIn() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // 1. ViewModelStore ownership: same ViewModel instance across configuration change
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val vmBefore = ViewModelProvider(controller.get(), MultiAccountSyncViewModel.Factory)[MultiAccountSyncViewModel::class.java]
        controller.configurationChange()
        val vmAfter = ViewModelProvider(controller.get(), MultiAccountSyncViewModel.Factory)[MultiAccountSyncViewModel::class.java]
        assertSame("ViewModelProvider must retain the same MultiAccountSyncViewModel across config change", vmBefore, vmAfter)
        controller.pause().stop().destroy()

        // 2. Process / Activity recreation with restored SavedStateHandle during Google Sign-In
        val restoredHandle = SavedStateHandle(
            mapOf(
                MultiAccountSyncViewModel.KEY_PENDING_PROVIDER to ProviderId.GOOGLE.key,
                MultiAccountSyncViewModel.KEY_PENDING_ACCOUNT_TYPE to AccountType.GOOGLE_PERSONAL.key
            )
        )
        val restoredVm = MultiAccountSyncViewModel.create(app, restoredHandle)
        assertTrue(
            "Restored ViewModel should remain in isConnectingGoogle state while waiting for activity result",
            restoredVm.uiState.value.isConnectingGoogle
        )

        // Deliver a returning Google Sign-In result (cancelled with null data); before H10 this
        // bailed out silently with "No pending Google sign-in" and left error=null.
        restoredVm.handleGoogleSignInResult(data = null, resultCode = Activity.RESULT_CANCELED)
        val deadline = System.currentTimeMillis() + 5_000L
        while (restoredVm.uiState.value.isConnectingGoogle && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("isConnectingGoogle should clear after sign-in result is processed", restoredVm.uiState.value.isConnectingGoogle)
        assertEquals(
            "Restored pending sign-in must be processed instead of silently dropped",
            SignInErrorText.google(SignInErrorText.CANCELLED),
            restoredVm.uiState.value.error
        )
        assertNull(
            "Pending provider key should be cleared from SavedStateHandle after handling result",
            restoredHandle.get<String>(MultiAccountSyncViewModel.KEY_PENDING_PROVIDER)
        )
    }

    class DraftHostActivity : ComponentActivity() {
        lateinit var selectedDateState: MutableState<String>
        lateinit var showAddDialogState: MutableState<Boolean>
        internal var draftState: EventDraftFormState? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                val selectedDate = rememberSaveable { mutableStateOf("2026-11-25") }
                val showAdd = rememberSaveable { mutableStateOf(false) }
                selectedDateState = selectedDate
                showAddDialogState = showAdd
                if (showAdd.value) {
                    draftState = rememberEventDraftFormState(
                        initialDate = selectedDate.value,
                        initialSyncEnabled = false,
                        initialLocalCalendarId = "local-default"
                    )
                } else {
                    draftState = null
                }
            }
        }
    }

    @Test
    fun selectedDateAndUnsavedEventDraftSurviveActivityRecreation() {
        val firstController = Robolectric.buildActivity(DraftHostActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val first = firstController.get()

        // User navigates to another date, opens New Event, and types an unsaved draft
        first.selectedDateState.value = "2026-12-14"
        first.showAddDialogState.value = true
        shadowOf(Looper.getMainLooper()).idle()

        val draftBefore = checkNotNull(first.draftState)
        draftBefore.titleState.value = "Quarterly Review"
        draftBefore.dateState.value = "2026-12-14"
        draftBefore.isAllDayState.value = false
        draftBefore.startHourState.value = 14
        draftBefore.startMinuteState.value = 30
        draftBefore.endHourState.value = 15
        draftBefore.endMinuteState.value = 45
        draftBefore.locationState.value = "Room 4B"
        draftBefore.descriptionState.value = "Bring Q4 budget deck"
        draftBefore.attendeesState.value = "Anna, Sam"
        draftBefore.syncEnabledState.value = true
        draftBefore.selectedTargetKeyState.value = "google"
        draftBefore.selectedCalendarIdState.value = "cal-work"
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate Android saving instance state and destroying the Activity
        val savedBundle = Bundle()
        firstController.pause().saveInstanceState(savedBundle).stop().destroy()

        // Recreate a brand-new Activity instance from the saved Bundle
        val recreatedController = Robolectric.buildActivity(DraftHostActivity::class.java)
            .create(savedBundle)
            .start()
            .resume()
            .visible()
        shadowOf(Looper.getMainLooper()).idle()
        val recreated = recreatedController.get()

        assertEquals("2026-12-14", recreated.selectedDateState.value)
        assertTrue("Add Event dialog must remain open after Activity recreation", recreated.showAddDialogState.value)

        val draftAfter = checkNotNull(recreated.draftState)
        assertEquals("Quarterly Review", draftAfter.titleState.value)
        assertEquals("2026-12-14", draftAfter.dateState.value)
        assertFalse(draftAfter.isAllDayState.value)
        assertEquals(14, draftAfter.startHourState.value)
        assertEquals(30, draftAfter.startMinuteState.value)
        assertEquals(15, draftAfter.endHourState.value)
        assertEquals(45, draftAfter.endMinuteState.value)
        assertEquals("Room 4B", draftAfter.locationState.value)
        assertEquals("Bring Q4 budget deck", draftAfter.descriptionState.value)
        assertEquals("Anna, Sam", draftAfter.attendeesState.value)
        assertTrue(draftAfter.syncEnabledState.value)
        assertEquals("google", draftAfter.selectedTargetKeyState.value)
        assertEquals("cal-work", draftAfter.selectedCalendarIdState.value)

        recreatedController.pause().stop().destroy()
    }
}
