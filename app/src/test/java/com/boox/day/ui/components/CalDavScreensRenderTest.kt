package com.boox.day.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.boox.day.R
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.CalendarSelection
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The CalDAV screens (plan step 3.6) at the size and density of the Go 10.3 and the Go 6, saved to
 * app/build/caldav-previews/ for the owner's review. The Calendar Accounts frame is a copy of the
 * dialog's own around the real tab content.
 *
 * Needs the app's resources (the Google and Outlook logos): skipped unless the run has them
 * (testOptions includeAndroidResources on for the picture run).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalDavScreensRenderTest {

    /** Calendar Accounts: 85 % of 773 dp on the Go 10.3, 94 % of 572 dp on the Go 6. Popups: forms 460 dp. */
    private data class Device(val name: String, val dialogDp: Int, val density: Float)

    private val devices = listOf(Device("go10.3", 657, 385 / 160f), Device("go6", 538, 300 / 160f))

    private fun render(device: Device, name: String, widthDp: Int, content: @Composable () -> Unit) {
        val bounds = mutableMapOf<String, Rect>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Column(Modifier.background(Color.White)) {
                    Box(Modifier.width(widthDp.dp).onGloballyPositioned { bounds["it"] = it.boundsInRoot() }) { content() }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val rect = bounds.getValue("it")
        val crop = Bitmap.createBitmap(image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt())
        val out = File("build/caldav-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("${device.name}-$name: ${(rect.height / device.density).toInt()} dp tall")
    }

    @Test
    fun theCalDavScreensOnBothTablets() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assumeTrue("picture run only (needs the app's resources)", runCatching { context.resources.getDrawable(R.drawable.ic_google_provider, null) }.isSuccess)
        for (device in devices) {
            val w = device.dialogDp
            render(device, "1-tab-empty", w) {
                AccountsFrame {
                    CalDavEmptyMessage()
                    Spacer(Modifier.height(EInkTheme.Global.spacing))
                    CalDavAddButtons {}
                }
            }
            render(device, "1b-tabs-google-selected", w) { Tabs(selected = 0) }
            render(device, "2-tab-connected", w) { AccountsFrame { ConnectedTab() } }
            val popup = 460
            render(device, "3a-signin-icloud", popup) {
                PopupFrame { CalDavSignInContent(CalDavSignInState(AccountType.CALDAV_ICLOUD, name = "you@icloud.com", password = "abcd-efgh-ijkl-mnop")) }
            }
            render(device, "3b-signin-nextcloud-not-found", popup) {
                PopupFrame {
                    CalDavSignInContent(
                        CalDavSignInState(
                            AccountType.CALDAV_NEXTCLOUD, address = "cloud.exmaple.com", name = "fort", password = "x".repeat(29),
                            message = "No calendar server found at this address. Check it and try again."
                        )
                    )
                }
            }
            render(device, "3c-signin-icloud-again", popup) {
                PopupFrame { CalDavSignInContent(CalDavSignInState.again(ICLOUD).copy(password = "abcd")) }
            }
            render(device, "4a-trust-server", popup) {
                PopupFrame { TrustServerContent("radicale.home.lan", "radicale.home.lan", FINGERPRINT) }
            }
            render(device, "4b-trust-server-details", popup) {
                PopupFrame { TrustServerContent("radicale.home.lan", "radicale.home.lan", FINGERPRINT, showDetails = true) }
            }
            render(device, "4c-trust-server-changed", popup) {
                PopupFrame { TrustServerContent("radicale.home.lan", "radicale.home.lan", FINGERPRINT, changed = true) }
            }
            render(device, "5-filter", 300) { FilterRows() }
        }
    }

    // ── Calendar Accounts, as the dialog draws it (CalendarSyncSettingsDialog), on the third tab ──

    @Composable
    private fun AccountsFrame(content: @Composable () -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(EInkTheme.PopupOverlay.padding)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                    Icon(Icons.Default.Refresh, null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(EInkTheme.Global.iconSize))
                    Text("Calendar Sync Settings", fontSize = EInkTheme.PopupOverlay.titleFontSize, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(EInkTheme.Global.touchTarget), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(EInkTheme.Global.iconSize))
                }
            }
            Spacer(Modifier.height(EInkTheme.Global.spacing))
            Tabs(selected = 2)
            Spacer(Modifier.height(EInkTheme.Global.spacing))
            Spacer(Modifier.height(56.dp))  // the sync banner's place
            Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) { content() }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                OutlinedButton(
                    onClick = {}, modifier = Modifier.weight(0.4f).height(EInkTheme.Global.touchTarget),
                    border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Button.textColor),
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                ) { Text("Close", fontSize = EInkTheme.Typography.button, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = {}, modifier = Modifier.weight(0.6f).height(EInkTheme.Global.touchTarget),
                    colors = ButtonDefaults.buttonColors(backgroundColor = EInkTheme.Colors.primary, contentColor = EInkTheme.Colors.white),
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius), elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(EInkTheme.Global.spacingSmall))
                    Text("Sync All Now", fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    @Composable
    private fun Tabs(selected: Int) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            for (i in 0..2) {
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(EInkTheme.Global.touchTarget)
                        .background(if (on) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    if (i == 2) CalDavTabLabel(selected = on) else Icon(
                        painterResource(if (i == 0) R.drawable.ic_google_provider else R.drawable.ic_outlook_provider), null,
                        modifier = Modifier.height(20.dp), tint = if (on) EInkTheme.Colors.white else EInkTheme.Colors.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun ConnectedTab() {
        val nextcloud = ConnectedAccount("a2", ProviderId.CALDAV, AccountType.CALDAV_NEXTCLOUD, "fort", serverUrl = "https://cloud.example.com/")
        val calendars = listOf("Home" to "#1BADF8", "Work" to "#CC73E1", "Family" to "#FF9500", "Shared with Ana" to "#63DA38")
            .mapIndexed { i, (name, color) ->
                CalendarSelection("s$i", "a1", "/cal/$i/", name, color, isFetchEnabled = i != 3, isDefaultForAccount = i == 0, displayOrder = i)
            }
        for ((account, open) in listOf(ICLOUD to true, nextcloud to false)) {
            AccountCalendarList(
                account = account,
                calendars = if (open) calendars else calendars.take(2),
                onDisconnectAccount = {}, onToggleCalendar = {}, onSetDefaultCalendar = {}, onRefreshCalendars = {},
                isExpanded = open,
                needsReauth = !open,
                lastSyncMessage = if (open) "Synced 2 min ago" else null,
                onReauth = {}
            )
        }
        Spacer(Modifier.height(EInkTheme.Global.spacing))
        CalDavAddButtons {}
    }

    @Composable
    private fun PopupFrame(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(EInkTheme.PopupOverlay.padding)
        ) { content() }
    }

    /** The events filter with a CalDAV account: all on, then CalDAV off. */
    @Composable
    private fun FilterRows() {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (filter in listOf(CalendarSourceFilter(), CalendarSourceFilter(showCalDav = false))) {
                CalendarSourceFilterIcons(filter, { true }, hasGoogleAccount = true, hasOutlookAccount = true, hasCalDavAccount = true, selectedDayOfMonth = 2)
            }
        }
    }

    private companion object {
        val ICLOUD = ConnectedAccount("a1", ProviderId.CALDAV, AccountType.CALDAV_ICLOUD, "you@icloud.com", serverUrl = "https://caldav.icloud.com/")
        val FINGERPRINT = (0 until 32).joinToString(":") { "%02X".format((it * 37 + 11) % 256) }
    }
}
