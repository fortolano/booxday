package com.boox.day.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boox.day.sync.BackupCloud
import com.boox.day.sync.BackupMetadata
import com.boox.day.sync.DriveBackupService
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.ImportService
import com.boox.day.sync.OneDriveAccount
import com.boox.day.ui.components.RestoreConfirmationCard
import com.boox.day.ui.theme.EInkTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * OneDrive backup's screens (owner decisions 85-88, Nov 2026) in the Data & Backup panel at the
 * Go 10.3's and the Go 6's size and density, made-up accounts, saved to app/build/backup-previews/
 * for the owner's review. The panel's frame (title, Close, tabs) is a copy of the dialog's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupPicturesRenderTest {

    /** Panel: 85 % x 84 % of 773 x 1031 dp on the Go 10.3, 94 % x 86 % of 572 x 772 dp on the Go 6. */
    private data class Device(val name: String, val width: Int, val height: Int, val density: Float, val wide: Float, val tall: Float)

    private val devices = listOf(
        Device("go10.3", 773, 1031, 385 / 160f, 0.85f, 0.84f),
        Device("go6", 572, 772, 300 / 160f, 0.94f, 0.86f)
    )

    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val backups = listOf(
        DriveBackupService.BackupInfo("3", "booxday_backup_2026-11-25_091500.zip", at(25, 9, 15), 14_890_000),
        DriveBackupService.BackupInfo("2", "booxday_backup_2026-11-18_090200.zip", at(18, 9, 2), 14_200_000),
        DriveBackupService.BackupInfo("1", "booxday_backup_2026-11-11_090500.zip", at(11, 9, 5), 13_600_000)
    )

    private val google = CloudCardInfo(BackupCloud.GOOGLE_DRIVE, true, "sam.lee@gmail.com", null, isAutoBackupOn = true)
    private val oneDriveOff = CloudCardInfo(BackupCloud.ONEDRIVE, false, null, null, isAutoBackupOn = false)
    private val oneDriveOn = CloudCardInfo(BackupCloud.ONEDRIVE, true, "sam.lee@outlook.com", null, isAutoBackupOn = true)
    private val googleOff = CloudCardInfo(BackupCloud.GOOGLE_DRIVE, true, "sam.lee@gmail.com", null, isAutoBackupOn = false)

    private val preview = ImportService.BackupPreview(
        metadata = BackupMetadata(createdAt = at(25, 9, 15), appVersion = "1.0.67", deviceModel = "Go 10.3", deviceManufacturer = "Onyx", androidVersion = 35),
        eventCount = 42, taskCount = 118, pageCount = 64, strokeCount = 23_480, calendarCount = 2,
        notebookCount = 1, ocrPageCount = 60, ocrTextCount = 60, summaryCount = 12, settingCount = 1
    )

    @Test
    fun oneDriveBackupScreens() {
        for (d in devices) {
            panel(d, "1-where-to-back-up", "Create Backup") {
                BackupDestinationContent(listOf(google, oneDriveOff), {}, {}, {})
            }
            panel(d, "1b-where-to-back-up-onedrive-on", "Create Backup") {
                BackupDestinationContent(listOf(googleOff, oneDriveOn), {}, {}, {})
            }
            panel(d, "2-where-is-your-backup", "Restore Backup") {
                RestoreSourceContent(listOf(google, oneDriveOn), {}, {}, {})
            }
            panel(d, "3-microsoft-account", "Microsoft Account") {
                MicrosoftAccountChoiceContent(listOf(OneDriveAccount("m1", "sam.lee@outlook.com")), isConnecting = false, error = null, onUseAccount = {}, onSignIn = {})
            }
            panel(d, "3b-microsoft-account-none-cancelled", "Microsoft Account") {
                MicrosoftAccountChoiceContent(emptyList(), isConnecting = false, error = "Sign-in was cancelled.", onUseAccount = {}, onSignIn = {})
            }
            panel(d, "4-onedrive-backup", "Cloud Backup") {
                CloudBackupFlowContent(
                    cloud = BackupCloud.ONEDRIVE, userEmail = "sam.lee@outlook.com", isAutoBackupOn = true, otherCloudOn = null,
                    backupFrequency = GoogleAuthManager.FREQUENCY_WEEKLY, lastBackupTime = at(25, 9, 15), isBackupInProgress = false,
                    statusMessage = "✓ Connected to OneDrive", onBackupEnabledChanged = {}, onBackupFrequencyChanged = {},
                    onBackupNowClick = {}, onTurnOffBackupClick = {}
                )
            }
            panel(d, "4b-onedrive-backup-while-google-on", "Cloud Backup") {
                CloudBackupFlowContent(
                    cloud = BackupCloud.ONEDRIVE, userEmail = "samantha.lee-whitfield@riverside-school.org", isAutoBackupOn = false, otherCloudOn = BackupCloud.GOOGLE_DRIVE,
                    backupFrequency = GoogleAuthManager.FREQUENCY_WEEKLY, lastBackupTime = 0L, isBackupInProgress = false,
                    statusMessage = null, onBackupEnabledChanged = {}, onBackupFrequencyChanged = {},
                    onBackupNowClick = {}, onTurnOffBackupClick = {}
                )
            }
            panel(d, "5-onedrive-restore-list", "Cloud Restore") { restoreList() }
            panel(d, "6-restore-confirmation", "Processing", over = {
                Box(Modifier.size(d.width.dp, d.height.dp), contentAlignment = Alignment.Center) {
                    RestoreConfirmationCard(preview, {}, {})
                }
            }) {
                BackupProgressContent("Downloading backup from OneDrive...")
            }
        }
    }

    @Composable
    private fun restoreList() = CloudRestoreFlowContent(
        cloud = BackupCloud.ONEDRIVE, userEmail = "sam.lee@outlook.com", backups = backups, isLoadingBackups = false,
        isRestoreInProgress = false, restoreStatus = null, onRefreshBackups = {}, onRestoreBackup = {}
    )

    /** The Data & Backup panel around [content], on a grey stand-in for the app. */
    private fun panel(d: Device, name: String, title: String, over: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
        render(d, name) {
            Box(Modifier.size(d.width.dp, d.height.dp).background(Color(0xFFE8E8E8)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth(d.wide)
                        .fillMaxHeight(d.tall)
                        .wrapContentHeight(Alignment.Top)
                        .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .padding(EInkTheme.PopupOverlay.padding)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(24.dp), tint = EInkTheme.Colors.primary)
                            Text(title, fontSize = EInkTheme.PopupOverlay.titleFontSize, fontWeight = FontWeight.Bold, color = EInkTheme.PopupOverlay.titleColor)
                        }
                        Box(
                            Modifier.height(EInkTheme.Button.height)
                                .border(BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor), RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Close", fontSize = EInkTheme.Typography.button, fontWeight = FontWeight.Medium) }
                    }
                    Spacer(Modifier.height(EInkTheme.Global.paddingMedium))
                    Row(Modifier.fillMaxWidth().border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong)) {
                        Box(Modifier.weight(1f).background(EInkTheme.Colors.primary).padding(vertical = EInkTheme.Global.paddingSmall), contentAlignment = Alignment.Center) {
                            Text("Backup & Restore", fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.white)
                        }
                        Row(Modifier.weight(1f).padding(vertical = EInkTheme.Global.paddingSmall), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = EInkTheme.Colors.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Maintenance", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
                        }
                    }
                    Spacer(Modifier.height(EInkTheme.Global.paddingMedium))
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
                }
                if (over != null) {
                    Box(Modifier.size(d.width.dp, d.height.dp).background(Color(0x55000000)))
                    over()
                }
            }
        }
    }

    private fun render(d: Device, name: String, content: @Composable () -> Unit) {
        var screen = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                Box(Modifier.onGloballyPositioned { screen = it.boundsInRoot() }) { content() }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, screen.left.toInt(), screen.top.toInt(), screen.width.toInt(), screen.height.toInt())
        val out = File("build/backup-previews").apply { mkdirs() }
        File(out, "${d.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
