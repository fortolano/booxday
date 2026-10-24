package com.boox.day.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.BuildConfig
import com.boox.day.ProjectInfo
import com.boox.day.R
import com.boox.day.update.AppUpdateGate
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * About dialog: what the app is, its open-source license (GPL-3.0) and project links, where the user's
 * data goes, features, story, philosophy and the legal notice.
 * Styled for e-ink displays with a fixed header and easier-to-scan sections.
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dialogShape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    val versionLabel = "Version ${BuildConfig.VERSION_NAME}"
    val highlightItems = listOf(
        stringResource(R.string.about_feature_1),
        stringResource(R.string.about_feature_3),
        stringResource(R.string.about_feature_5)
    )
    val featureItems = listOf(
        stringResource(R.string.about_feature_1),
        stringResource(R.string.about_feature_2),
        stringResource(R.string.about_feature_3),
        stringResource(R.string.about_feature_4),
        stringResource(R.string.about_feature_5),
        stringResource(R.string.about_feature_6),
        stringResource(R.string.about_feature_7),
        stringResource(R.string.about_feature_8),
        stringResource(R.string.about_feature_9),
        stringResource(R.string.about_feature_10)
    )

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
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .widthIn(max = EInkTheme.PopupOverlay.wideWidth)
                    .fillMaxHeight(0.82f)
                    .background(EInkTheme.ModernDialog.background, dialogShape)
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        dialogShape
                    )
            ) {
                AboutHeader(
                    versionLabel = versionLabel,
                    onDismiss = onDismiss
                )

                Divider(
                    color = EInkTheme.ModernDialog.borderColor,
                    thickness = EInkTheme.ModernDialog.borderWidth
                )

                ScrollableWithIndicator(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        AboutHeroCard(
                            highlights = highlightItems
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_open_source_title))
                            SectionCard {
                                SectionBodyText(stringResource(R.string.about_open_source_content))
                            }
                            SectionCard(
                                contentPadding = 0.dp,
                                verticalSpacing = 0.dp
                            ) {
                                ExternalLinkAction(
                                    label = stringResource(R.string.about_link_source),
                                    supportingText = ProjectInfo.REPO_URL.removePrefix("https://"),
                                    emphasized = true,
                                    onClick = { openUrl(context, ProjectInfo.REPO_URL) }
                                )
                                ExternalLinkAction(
                                    label = stringResource(R.string.about_link_releases),
                                    showTopDivider = true,
                                    onClick = { openUrl(context, ProjectInfo.RELEASES_URL) }
                                )
                                ExternalLinkAction(
                                    label = stringResource(R.string.about_link_issues),
                                    showTopDivider = true,
                                    onClick = { openUrl(context, ProjectInfo.ISSUES_URL) }
                                )
                                ExternalLinkAction(
                                    label = stringResource(R.string.about_link_license),
                                    showTopDivider = true,
                                    onClick = { openUrl(context, ProjectInfo.LICENSE_URL) }
                                )
                                ExternalLinkAction(
                                    label = stringResource(R.string.about_link_notable),
                                    showTopDivider = true,
                                    onClick = { openUrl(context, ProjectInfo.NOTABLE_URL) }
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_privacy_title))
                            SectionCard {
                                SectionBodyText(stringResource(R.string.about_privacy_content))
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_features_title))
                            SectionCard {
                                featureItems.forEachIndexed { index, feature ->
                                    FeatureRow(text = feature)
                                    if (index < featureItems.lastIndex) {
                                        Divider(
                                            color = EInkTheme.Divider.subtleColor,
                                            thickness = EInkTheme.Divider.subtleThickness
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_story_title))
                            SectionCard {
                                SectionBodyText(stringResource(R.string.about_story_content))
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_philosophy_title))
                            SectionCard {
                                SectionBodyText(stringResource(R.string.about_philosophy_content))
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(stringResource(R.string.about_legal_title))
                            SectionCard(
                                background = EInkTheme.Colors.offWhite
                            ) {
                                SectionBodyText(
                                    text = stringResource(R.string.about_legal_content),
                                    fontSize = EInkTheme.Typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.about_copyright),
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Debug builds only: test the in-app update without publishing a release
                        if (BuildConfig.DEBUG) {
                            UpdateLink(text = "Debug: test update as version 1.0.0") {
                                AppUpdateGate.debugCheckAsOldVersion(context)
                            }
                            UpdateLink(text = "Debug: install update-test.apk") {
                                AppUpdateGate.debugInstallTestApk(context)
                            }
                            // Tests the crash report (diagnostics/CrashReporter): reopen the app after
                            UpdateLink(text = "Debug: crash now") {
                                throw RuntimeException("Debug crash from About")
                            }
                            // Plan step 1a: does BOOX take a sleep image from the app? (sleepscreen/SleepScreenSpike)
                            UpdateLink(text = "Debug: set test sleep screen (Pictures)") {
                                com.boox.day.sleepscreen.SleepScreenSpike.run(context, com.boox.day.sleepscreen.SleepScreenSpike.Where.PICTURES)
                            }
                            UpdateLink(text = "Debug: set test sleep screen (app folder)") {
                                com.boox.day.sleepscreen.SleepScreenSpike.run(context, com.boox.day.sleepscreen.SleepScreenSpike.Where.APP_FILES)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(
    versionLabel: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = EInkTheme.Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = versionLabel,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray
            )
            UpdateLink(text = "Check for Updates") { AppUpdateGate.checkNow(context) }
        }

        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
        }
    }
}

@Composable
private fun AboutHeroCard(
    highlights: List<String>
) {
    val shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_icon),
            contentDescription = "App icon",
            modifier = Modifier.size(96.dp)
        )

        Text(
            text = stringResource(R.string.about_tagline),
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            textAlign = TextAlign.Center
        )

        Divider(
            color = EInkTheme.Divider.subtleColor,
            thickness = EInkTheme.Divider.subtleThickness
        )

        highlights.forEach { highlight ->
            FeatureRow(text = highlight)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionCard(
    background: androidx.compose.ui.graphics.Color = EInkTheme.Colors.background,
    contentPadding: Dp = 16.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content
    )
}

@Composable
private fun SectionBodyText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = EInkTheme.Typography.bodyMedium
) {
    Text(
        text = text,
        fontSize = fontSize,
        color = EInkTheme.Colors.darkGray,
        textAlign = TextAlign.Start,
        lineHeight = EInkTheme.Typography.bodyLarge,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExternalLinkAction(
    label: String,
    supportingText: String? = null,
    emphasized: Boolean = false,
    showTopDivider: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (showTopDivider) {
            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EInkTheme.Button.height)
                .noRippleClickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open link",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
    }
}

/** In-app updates (Oct 2026): opens the update popup (update/AppUpdateGate) with the answer. */
@Composable
private fun UpdateLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = EInkTheme.Global.touchTarget)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}

private fun openUrl(context: Context, url: String) {
    // No browser on the tablet: nothing to open, so the tap does nothing rather than crash
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
