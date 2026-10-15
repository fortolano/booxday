package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch

/**
 * Language Pack Manager - allows users to download, delete, and switch handwriting recognition languages.
 *
 * Features:
 * - Shows all 10 supported languages in 2-column grid layout
 * - 5-language download quota with visual indicator
 * - Download/delete language packs
 * - Set active language for recognition
 * - English is protected (cannot be deleted)
 *
 * Supported languages: English, German, Spanish, French, Italian, Russian, Japanese, Korean, Chinese, Arabic
 *
 * E-Ink optimized: Uses Popup, noRippleClickable, EInkTheme styling, no scrolling required.
 */
@Composable
fun LanguagePackManagerPopup(
    onDismiss: () -> Unit,
    onLanguageSelected: (languageTag: String) -> Unit
) {
    val languageStates by DigitalInkModelManager.languageStates.collectAsState()
    val downloadedCount by DigitalInkModelManager.downloadedCount.collectAsState()
    val scope = rememberCoroutineScope()

    // Quota status
    val quotaReached = downloadedCount >= 5

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.87f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.74f
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
                // Header with quota
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Handwriting Languages",
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                        // Quota indicator
                        Text(
                            "$downloadedCount/5 languages downloaded",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = if (quotaReached) FontWeight.Bold else FontWeight.Normal,
                            color = if (quotaReached) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(
                            EInkTheme.Button.borderWidth,
                            EInkTheme.Button.borderColor
                        ),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                // Prominent quota alert
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = EInkTheme.Colors.offWhite,
                    border = BorderStroke(1.5.dp, EInkTheme.Colors.primary),
                    elevation = 0.dp,
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                ) {
                    Row(
                        modifier = Modifier.padding(EInkTheme.Global.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Column {
                            Text(
                                "5 Language Limit",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Only 5 languages can be downloaded at once (~20MB each). To download another language, you must delete an existing one first. English is protected and cannot be deleted.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray,
                                lineHeight = EInkTheme.Typography.bodySmall * 1.3f
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                // Language list - 2-column grid (5 rows x 2 columns = 10 languages)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Split languages into pairs for 2-column layout
                    languageStates.chunked(2).forEach { rowLanguages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowLanguages.forEach { language ->
                                Box(modifier = Modifier.weight(1f)) {
                                    LanguagePackRow(
                                        language = language,
                                        quotaReached = quotaReached,
                                        onDownload = {
                                            scope.launch {
                                                DigitalInkModelManager.downloadLanguage(language.tag)
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                DigitalInkModelManager.deleteLanguage(language.tag)
                                            }
                                        },
                                        onSetActive = {
                                            scope.launch {
                                                DigitalInkModelManager.setActiveLanguage(language.tag)
                                                onLanguageSelected(language.tag)
                                            }
                                        }
                                    )
                                }
                            }
                            // Add spacer if odd number in row (shouldn't happen with 10 languages)
                            if (rowLanguages.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual language pack row with download/delete/activate actions.
 */
@Composable
private fun LanguagePackRow(
    language: DigitalInkModelManager.LanguageInfo,
    quotaReached: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
    // Check if this is a protected language
    val isProtected = language.tag == "en-US"

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (language.isActive) EInkTheme.Colors.lightGray else EInkTheme.Colors.background,
        border = BorderStroke(
            width = 0.5.dp,
            color = when {
                language.isActive -> EInkTheme.Colors.primary
                isProtected && language.isDownloaded -> EInkTheme.Colors.darkGray
                else -> EInkTheme.Colors.borderStrong
            }
        ),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Language info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (language.isActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            modifier = Modifier.size(16.dp),
                            tint = EInkTheme.Colors.primary
                        )
                    }
                    // Protected language indicator
                    if (isProtected && language.isDownloaded) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Protected",
                            modifier = Modifier.size(14.dp),
                            tint = EInkTheme.Colors.darkGray
                        )
                    }
                    Text(
                        language.displayName,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = if (language.isActive) FontWeight.Bold else FontWeight.Normal,
                        color = EInkTheme.Colors.primary
                    )
                }
                Text(
                    language.downloadSize,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }

            // Right: Status + Actions
            when (language.downloadState) {
                DigitalInkModelManager.DownloadState.NOT_DOWNLOADED -> {
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = !quotaReached,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.primary,
                            disabledContentColor = EInkTheme.Colors.darkGray
                        ),
                        border = BorderStroke(
                            EInkTheme.Button.borderWidth,
                            if (quotaReached) EInkTheme.Colors.gray else EInkTheme.Button.borderColor
                        )
                    ) {
                        Text("Download", fontSize = EInkTheme.Typography.bodySmall)
                    }
                }

                DigitalInkModelManager.DownloadState.DOWNLOADING -> {
                    Text(
                        "Downloading...",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray,
                        fontWeight = FontWeight.Medium
                    )
                }

                DigitalInkModelManager.DownloadState.READY -> {
                    if (language.isActive) {
                        Text(
                            "Active",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isProtected) {
                        // Protected language can be activated but NOT deleted
                        OutlinedButton(
                            onClick = onSetActive,
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EInkTheme.Colors.primary
                            ),
                            border = BorderStroke(
                                EInkTheme.Button.borderWidth,
                                EInkTheme.Button.borderColor
                            )
                        ) {
                            Text("Set Active", fontSize = EInkTheme.Typography.bodySmall)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = onSetActive,
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = EInkTheme.Colors.primary
                                ),
                                border = BorderStroke(
                                    EInkTheme.Button.borderWidth,
                                    EInkTheme.Button.borderColor
                                )
                            ) {
                                Text("Set Active", fontSize = EInkTheme.Typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = onDelete,
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = EInkTheme.Colors.red
                                ),
                                border = BorderStroke(
                                    EInkTheme.Button.borderWidth,
                                    EInkTheme.Colors.red
                                )
                            ) {
                                Text("Delete", fontSize = EInkTheme.Typography.bodySmall)
                            }
                        }
                    }
                }

                DigitalInkModelManager.DownloadState.FAILED -> {
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = !quotaReached,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.primary
                        ),
                        border = BorderStroke(
                            EInkTheme.Button.borderWidth,
                            EInkTheme.Button.borderColor
                        )
                    ) {
                        Text("Retry", fontSize = EInkTheme.Typography.bodySmall)
                    }
                }
            }
        }
    }
}
