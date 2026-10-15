package com.boox.day.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay

/**
 * Permission dialog for the Insert Image feature.
 *
 * Shown when user taps "Insert Image" in Insert menu without having
 * MANAGE_EXTERNAL_STORAGE permission. Explains what folders can be
 * accessed and guides user through permission grant.
 *
 * Once permission is granted, automatically dismisses and triggers
 * the image picker callback.
 */
@Composable
fun ImagePickerPermissionDialog(
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current

    // Check current permission status
    var hasPermission by remember {
        mutableStateOf(checkImageAccessPermission())
    }

    // ActivityResult launcher for storage permission settings
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val newPermissionState = checkImageAccessPermission()
        if (newPermissionState != hasPermission) {
            hasPermission = newPermissionState
            if (newPermissionState) {
                onPermissionGranted()
            }
        }
    }

    // Auto-proceed if permission already granted, otherwise poll for changes
    LaunchedEffect(Unit) {
        // If permission is already granted, proceed immediately
        if (hasPermission) {
            onPermissionGranted()
            return@LaunchedEffect
        }

        // Safety net polling in case ActivityResult does not fire
        while (!hasPermission) {
            delay(3000) // Check every 3 seconds
            val newPermissionState = checkImageAccessPermission()
            if (newPermissionState && !hasPermission) {
                hasPermission = true
                // Permission granted - proceed to image picker
                onPermissionGranted()
            } else if (newPermissionState != hasPermission) {
                hasPermission = newPermissionState
            }
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.01f))
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .noRippleClickable { /* consume clicks */ }
                    .background(
                        EInkTheme.PopupOverlay.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Insert Image",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Insert Image",
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Description
                Text(
                    text = "Browse your device to insert images into your notes. Photos, screenshots, and downloaded images can all be added.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Accessible folders info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = EInkTheme.Colors.offWhite,
                    border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong),
                    elevation = 0.dp,
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Column(
                        modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
                    ) {
                        Text(
                            text = "Accessible folders",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                        Text(
                            text = "Pictures\nDownloads\nDocuments\nDCIM (Camera)",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Permission instructions card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = EInkTheme.Colors.background,
                    border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray),
                    elevation = 0.dp,
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Column(
                        modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Permission needed",
                                modifier = Modifier.size(20.dp),
                                tint = EInkTheme.Colors.primary
                            )
                            Text(
                                text = "Permission Required",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                        // Explanation
                        Text(
                            text = "To browse and insert images, please grant file access permission:",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )

                        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                        // Step 1
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = "Tap the button below to open Android settings",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Step 2
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "2.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = "Toggle \"Allow access to manage all files\" to ON",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Step 3
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "3.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = "Tap \"\u2190 All files access\" at the top to return",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                        // Action button
                        OutlinedButton(
                            onClick = { openFilesAccessSettings(context, allFilesAccessLauncher) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = EInkTheme.Colors.primary,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                        ) {
                            Text(
                                text = "Open Permission Settings",
                                fontSize = EInkTheme.Typography.button,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // Note about shared permission
                Text(
                    text = "This is the same permission used by Link File and Boox Notes integration.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

/**
 * Check if the app has MANAGE_EXTERNAL_STORAGE permission.
 */
private fun checkImageAccessPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // Android 10 and below don't need this special permission
    }
}

/**
 * Launch system settings to request All Files Access permission.
 */
private fun openFilesAccessSettings(
    context: Context,
    launcher: ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            // Fallback to general storage settings
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                launcher.launch(intent)
            } catch (e2: Exception) {
                // Last resort: open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                launcher.launch(intent)
            }
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launcher.launch(intent)
    }
}
