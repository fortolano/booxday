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
import com.boox.day.ocr.HandwritingLanguages
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RadioOption
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.showHint
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.showHint
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
 * Nine fixed languages (HandwritingLanguages.FIXED), and the Other language card for any of the rest.
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
    val otherTag by DigitalInkModelManager.otherLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LanguagePackPanel(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.87f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.74f
                    // Oct 2026 (owner): as tall as the list, up to the panel height, with the top
                    // where a full panel's top is (the same place as Preferences' header)
                    .wrapContentHeight(Alignment.Top),
                languageStates = languageStates.filter { it.tag in HandwritingLanguages.FIXED },
                other = languageStates.firstOrNull { it.tag == otherTag },
                downloadedCount = downloadedCount,
                onDismiss = onDismiss,
                onChooseOther = {
                    when {
                        DigitalInkModelManager.isDownloadRunning() -> showHint(WAIT_FOR_DOWNLOAD)
                        otherTag == null && DigitalInkModelManager.isQuotaReached() -> showHint("5 languages are downloaded. Delete one first.")
                        else -> picking = true
                    }
                },
                onDownload = { tag ->
                    if (DigitalInkModelManager.isDownloadRunning()) showHint(WAIT_FOR_DOWNLOAD)
                    else scope.launch { DigitalInkModelManager.downloadLanguage(tag) }
                },
                onDelete = { tag -> scope.launch { DigitalInkModelManager.deleteLanguage(tag) } },
                onSetActive = { tag ->
                    scope.launch {
                        DigitalInkModelManager.setActiveLanguage(tag)
                        onLanguageSelected(tag)
                    }
                }
            )
        }
        if (picking) {
            OtherLanguagePicker(
                chosenTag = otherTag,
                onPick = { tag ->
                    picking = false
                    val wasActive = languageStates.any { it.tag == otherTag && it.isActive }
                    scope.launch {
                        if (DigitalInkModelManager.chooseOtherLanguage(tag)) {
                            if (wasActive) onLanguageSelected(tag)
                        } else {
                            showHint("Couldn't download that language. Check your connection and try again.")
                        }
                    }
                },
                onCancel = { picking = false }
            )
        }
    }
}

private const val WAIT_FOR_DOWNLOAD = "Wait for the download that's running to finish."

/** Languages on a page of the list: fits the Go 6's panel. */
private const val LANGUAGES_PER_PAGE = 16

@Composable
private fun OtherLanguagePicker(chosenTag: String?, onPick: (String) -> Unit, onCancel: () -> Unit) {
    val all = HandwritingLanguages.others
    val letters = remember { HandwritingLanguages.letters() }
    val start = chosenTag?.let { tag -> all.firstOrNull { it.tag == tag }?.name?.first()?.uppercaseChar() }
    var letter by remember { mutableStateOf(start ?: letters.first()) }
    var page by remember { mutableStateOf(0) }
    val matching = remember(letter) { all.filter { it.name.first().uppercaseChar() == letter } }
    val pageCount = (matching.size + LANGUAGES_PER_PAGE - 1) / LANGUAGES_PER_PAGE

    Popup(alignment = Alignment.Center, onDismissRequest = onCancel, properties = PopupProperties(focusable = true)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LanguagePickerPanel(
                modifier = Modifier.fillMaxWidth(EInkTheme.PanelDialog.widthFraction),
                letters = letters,
                letter = letter,
                languages = matching.drop(page * LANGUAGES_PER_PAGE).take(LANGUAGES_PER_PAGE),
                chosenTag = chosenTag,
                page = page,
                pageCount = pageCount,
                onLetter = {
                    letter = it
                    page = 0
                },
                onPage = { page = it },
                onPick = onPick,
                onCancel = onCancel
            )
        }
    }
}

/** The popup's content, apart from the language manager, so the render test can draw it. */
@Composable
internal fun LanguagePackPanel(
    modifier: Modifier,
    languageStates: List<DigitalInkModelManager.LanguageInfo>,
    other: DigitalInkModelManager.LanguageInfo?,
    downloadedCount: Int,
    onDismiss: () -> Unit,
    onChooseOther: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit
) {
    // Quota status
    val quotaReached = downloadedCount >= 5

    Column(
        modifier = modifier
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
                        "Only 5 languages can be downloaded at once (~15MB each). To download another language, you must delete an existing one first. English is protected and cannot be deleted.",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray,
                        lineHeight = EInkTheme.Typography.bodySmall * 1.3f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

        // Language list - 2-column grid: nine languages, then the Other language card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
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
                                onDownload = { onDownload(language.tag) },
                                onDelete = { onDelete(language.tag) },
                                onSetActive = { onSetActive(language.tag) }
                            )
                        }
                    }
                    // The tenth place is always the Other language card
                    if (rowLanguages.size == 1) {
                        Box(modifier = Modifier.weight(1f)) {
                            OtherLanguageCard(other, onChooseOther) { other?.let { onSetActive(it.tag) } }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The tenth card: any other language the library can read. What's chosen here replaces what was here.
 * A heavier border and its own label, so it reads as a different kind of card.
 */
@Composable
private fun OtherLanguageCard(
    language: DigitalInkModelManager.LanguageInfo?,
    onChoose: () -> Unit,
    onSetActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (language?.isActive == true) EInkTheme.Colors.lightGray else EInkTheme.Colors.background,
        border = BorderStroke(2.dp, EInkTheme.Colors.primary),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            Text(
                "Other language",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            // Below the name: a long name beside two buttons would break in the middle of a word
            Text(
                language?.displayName ?: "None yet",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (language?.downloadState == DigitalInkModelManager.DownloadState.DOWNLOADING) {
                    Text("Downloading...", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.darkGray)
                    return@Row
                }
                if (language?.isActive == true) {
                    Text("Active", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary)
                    Spacer(Modifier.width(EInkTheme.Global.spacing))
                } else if (language != null) {
                    SmallButton("Use", onSetActive)
                }
                SmallButton(if (language == null) "Choose" else "Change", onChoose)
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary),
        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor)
    ) {
        Text(text, fontSize = EInkTheme.Typography.bodySmall)
    }
}

/**
 * The list behind the Other language card: pick a letter, then a language. Letters, not a long scroll:
 * each scroll step is a full e-ink refresh.
 */
@Composable
internal fun LanguagePickerPanel(
    modifier: Modifier,
    letters: List<Char>,
    letter: Char,
    languages: List<HandwritingLanguages.Language>,
    chosenTag: String?,
    page: Int,
    pageCount: Int,
    onLetter: (Char) -> Unit,
    onPage: (Int) -> Unit,
    onPick: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = modifier
            .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        Text(
            "Choose a language",
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
        Text(
            "It replaces what's on the Other language card.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )
        letters.chunked((letters.size + 1) / 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { item ->
                    val selected = item == letter
                    Box(
                        Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .background(if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background)
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                            .noRippleClickable { onLetter(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.toString(),
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }
        languages.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)) {
                pair.forEach { language ->
                    Box(Modifier.weight(1f)) {
                        RadioOption(
                            label = language.name,
                            isSelected = language.tag == chosenTag,
                            onClick = { onPick(language.tag) },
                            modifier = Modifier.heightIn(min = EInkTheme.Global.touchTarget)
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            if (pageCount > 1) {
                EinkSecondaryButton(text = "Previous", onClick = { onPage(page - 1) }, enabled = page > 0)
                Text(
                    "${page + 1} of $pageCount",
                    modifier = Modifier.padding(horizontal = EInkTheme.Global.paddingSmall),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary
                )
                EinkSecondaryButton(text = "Next", onClick = { onPage(page + 1) }, enabled = page < pageCount - 1)
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
                            Text("Use", fontSize = EInkTheme.Typography.bodySmall)
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
                                Text("Use", fontSize = EInkTheme.Typography.bodySmall)
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
