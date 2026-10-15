package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.theme.EInkTheme

/**
 * Loading dialog shown while AI processes the canvas image.
 * Non-dismissable to prevent user interruption during processing.
 */
@Composable
fun AiProcessingDialog(
    isVisible: Boolean,
    message: String = "Converting handwriting to text..."
) {
    if (isVisible) {
        // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
        // Note: Non-dismissable popups still need an onDismissRequest, but we ignore it
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { /* Non-dismissable */ },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(EInkTheme.PopupOverlay.wideWidth)
                        .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = EInkTheme.Colors.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                    RotatingFunMessage(
                        messageProvider = { AiFeedbackMessages.getRandomGeneralMessage() }
                    )
                }
            }
        }
    }
}
