package com.boox.day.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
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
import com.boox.day.api.AiJob
import com.boox.day.api.ModelConfigProvider
import com.boox.day.data.datastore.VoiceTextMode
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Voice-to-Text Settings Dialog
 *
 * Shown when user needs to configure voice input:
 * - First time use without Google Search app installed
 * - Via Settings menu for manual configuration
 *
 * Options:
 * 1. Google Speech Recognition (SpeechRecognizer) - free; the Google Search app, or Google's speech
 *    services that some BOOX tablets ship with. [hasGoogleSearchApp] = the tablet has either.
 * 2. AI on OpenRouter - the VOICE job's model (AI Models), on the user's own key; only without 1
 *    (TranscriptionService.voiceSource)
 */
@Composable
fun VoiceSettingsDialog(
    hasGoogleSearchApp: Boolean,
    currentMode: VoiceTextMode,
    onSelectMode: (VoiceTextMode) -> Unit,
    onInstallGoogleSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initialMode = if (hasGoogleSearchApp) VoiceTextMode.AUTO else currentMode
    var selectedMode by remember { mutableStateOf(initialMode) }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice-to-Text Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(EInkTheme.Global.iconSize)
                            .noRippleClickable { onDismiss() },
                        tint = EInkTheme.Colors.darkGray
                    )
                }

                // Explanatory header
                Text(
                    text = "Choose your voice transcription method:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                // Recommendation tip
                Text(
                    text = if (hasGoogleSearchApp)
                        "This tablet has Google's speech recognition. It is free and is always used first."
                    else
                        "Tip: install the Google Search app for free speech to text.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                // Option 1: Google Speech Recognition
                VoiceOptionCard(
                    title = "Google Speech Recognition",
                    isSelected = selectedMode == VoiceTextMode.AUTO || selectedMode == VoiceTextMode.SPEECH_RECOGNIZER,
                    isAvailable = hasGoogleSearchApp,
                    statusText = if (hasGoogleSearchApp) "Ready on this tablet" else "Needs the Google Search app",
                    descriptionText = "Free: no key or OpenRouter credit needed. Google may process the audio on its servers.",
                    showInstallButton = !hasGoogleSearchApp,
                    onSelect = {
                        if (hasGoogleSearchApp) {
                            selectedMode = VoiceTextMode.AUTO
                        }
                    },
                    onInstall = {
                        openGoogleSearchPlayStore(context)
                        onInstallGoogleSearch()
                    }
                )

                // Option 2: AI on OpenRouter, with the model the voice job uses now (Oct 2026: the
                // title said "Gemini 2.5 AI" whatever the AI Models card had)
                val voiceModel = ModelConfigProvider.modelInUse(AiJob.VOICE)
                val geminiStatusText = if (hasGoogleSearchApp) {
                    "Not used while Google Speech is ready, which is free"
                } else {
                    "Runs on your own OpenRouter account"
                }
                val geminiDescriptionText = "Model: ${voiceModel.name}, " +
                    (if (voiceModel.isUserChoice) "your choice" else "BooxDay's choice") +
                    ". Change it in AI Settings → AI Models."
                VoiceOptionCard(
                    title = "AI on OpenRouter",
                    isSelected = selectedMode == VoiceTextMode.GEMINI,
                    isAvailable = !hasGoogleSearchApp,
                    statusText = geminiStatusText,
                    descriptionText = geminiDescriptionText,
                    showInstallButton = false,
                    onSelect = { selectedMode = VoiceTextMode.GEMINI },
                    onInstall = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons (40% / 20% / 40%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.Button.height)
                            .border(
                                EInkTheme.Button.borderWidth,
                                EInkTheme.Button.borderColor,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
                            color = EInkTheme.Button.textColor
                        )
                    }

                    Spacer(modifier = Modifier.weight(0.2f))

                    // Save button
                    val canSave = hasGoogleSearchApp || selectedMode == VoiceTextMode.GEMINI
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.Button.height)
                            .background(
                                if (canSave) EInkTheme.Button.primaryBackground
                                else EInkTheme.Button.disabledBackground,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .border(
                                EInkTheme.Button.primaryBorderWidth,
                                if (canSave) EInkTheme.Button.primaryBorderColor
                                else EInkTheme.Button.disabledBorderColor,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable {
                                if (canSave) {
                                    onSelectMode(selectedMode)
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            fontSize = EInkTheme.Typography.button,
                            color = if (canSave) EInkTheme.Button.primaryTextColor
                            else EInkTheme.Button.disabledTextColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual voice option card with radio selection.
 */
@Composable
private fun VoiceOptionCard(
    title: String,
    isSelected: Boolean,
    isAvailable: Boolean,
    statusText: String,
    descriptionText: String,
    showInstallButton: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit
) {
    val borderColor = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
    val borderWidth = if (isSelected) 2.dp else EInkTheme.ModernDialog.OptionCard.borderWidth

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .background(
                EInkTheme.ModernDialog.OptionCard.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable {
                if (isAvailable) onSelect()
            }
            .padding(EInkTheme.ModernDialog.OptionRow.paddingHorizontal)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(EInkTheme.ModernDialog.RadioIndicator.size)
                    .border(
                        EInkTheme.ModernDialog.RadioIndicator.borderWidth,
                        if (isSelected) EInkTheme.ModernDialog.RadioIndicator.fillColor
                        else EInkTheme.ModernDialog.RadioIndicator.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(EInkTheme.ModernDialog.RadioIndicator.size - EInkTheme.ModernDialog.RadioIndicator.innerPadding * 2)
                            .background(
                                EInkTheme.ModernDialog.RadioIndicator.fillColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = EInkTheme.ModernDialog.OptionRow.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (isAvailable) EInkTheme.ModernDialog.OptionRow.textColor
                    else EInkTheme.Colors.gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAvailable) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = statusText,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = if (isAvailable) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = descriptionText,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }

            // Install button if needed
            if (showInstallButton) {
                Box(
                    modifier = Modifier
                        .height(EInkTheme.Global.touchTarget)
                        .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                        .noRippleClickable { onInstall() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Install",
                            modifier = Modifier.size(16.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Install",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens Google Play Store to the Google Search app page.
 */
fun openGoogleSearchPlayStore(context: Context) {
    val packageName = "com.google.android.googlequicksearchbox"
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
        )
    }
}




