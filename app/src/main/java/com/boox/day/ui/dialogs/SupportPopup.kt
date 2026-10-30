package com.boox.day.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ProjectInfo
import com.boox.day.R
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme

/**
 * "Support BooxDay" (Oct 2026): a short thank-you pitch with the Buy Me a Coffee link and its QR code.
 * Opened from the Settings menu, About's header and Help's Support BooxDay topic; shown by
 * [SupportPopupHost] in MainScreen, so it stays up after the Settings menu closes and opens above
 * About or Help.
 */
object SupportPopup {
    var isOpen by mutableStateOf(false)
        private set

    fun open() { isOpen = true }
    fun close() { isOpen = false }
}

/** MainScreen's host for [SupportPopup]; its own composable, outside MainScreen's root lambda. */
@Composable
fun SupportPopupHost() {
    if (!SupportPopup.isOpen) return
    val context = LocalContext.current
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { SupportPopup.close() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SupportCard(
                onNotNow = { SupportPopup.close() },
                onBuyCoffee = {
                    // No browser on the tablet: nothing to open, so the tap only closes the popup
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(ProjectInfo.SUPPORT_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    SupportPopup.close()
                }
            )
        }
    }
}

/** The popup's content; its own composable for the render test (which has no app resources: [qrCode]). */
@Composable
internal fun SupportCard(
    onNotNow: () -> Unit,
    onBuyCoffee: () -> Unit,
    qrCode: @Composable () -> Unit = { SupportQrCode() }
) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
            .background(EInkTheme.PopupOverlay.background, shape)
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, shape)
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            Icon(
                imageVector = Icons.Default.LocalCafe,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Support BooxDay",
                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.PopupOverlay.titleColor
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            SupportText(
                "BooxDay is free: no ads, no subscription, nothing to unlock. It's made by one person, " +
                    "for people who plan their days on a BOOX."
            )
            SupportText(
                "If it makes your days a little easier, you can buy me a coffee. For the price of a cup, " +
                    "you help keep the best calendar for BOOX growing: new features, support for new tablets " +
                    "and firmware, and fixes when something breaks."
            )
            SupportText("It's never required, and BooxDay stays free for everyone. Thank you for using it.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                qrCode()
                Text(
                    text = "Or scan this with your phone and pay there.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            EinkSecondaryButton(text = "Not Now", onClick = onNotNow, modifier = Modifier.weight(0.4f))
            ActionButton(text = "Buy Me a Coffee", onClick = onBuyCoffee, modifier = Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun SupportQrCode() {
    Image(
        painter = painterResource(id = R.drawable.support_qr),
        contentDescription = "QR code for ${ProjectInfo.SUPPORT_URL}",
        modifier = Modifier.size(112.dp)
    )
}

@Composable
private fun SupportText(text: String, bold: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = EInkTheme.Colors.primary
    )
}
