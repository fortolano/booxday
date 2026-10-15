package com.boox.day.ui.dialogs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

/**
 * Data class for background template options
 */
private data class BackgroundTemplate(
    val key: String,
    val displayName: String
)

/**
 * Available native background templates
 */
private val templates = listOf(
    BackgroundTemplate("blank", "Blank"),
    BackgroundTemplate("dotted", "Dotted"),
    BackgroundTemplate("lined", "Lined"),
    BackgroundTemplate("squared", "Squared")
)

/**
 * Simple dialog for selecting native canvas background templates.
 */
@Composable
fun NativeBackgroundDialog(
    currentBackground: String,
    onBackgroundSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
            ) {
                // Header row with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = EInkTheme.ModernDialog.padding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(44.dp))
                    Text(
                        "Select Background",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.darkGray
                        )
                    }
                }

                // Subtle divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // Template options - single row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = EInkTheme.ModernDialog.padding,
                            vertical = EInkTheme.ModernDialog.contentSpacing
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    templates.forEach { template ->
                        BackgroundTemplateOption(
                            template = template,
                            isSelected = currentBackground == template.key,
                            onClick = { onBackgroundSelected(template.key) }
                        )
                    }
                }

                // Cancel button below templates
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EInkTheme.ModernDialog.padding)
                        .padding(bottom = EInkTheme.Global.paddingMedium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = EInkTheme.ModernDialog.CancelLink.fontSize,
                        color = EInkTheme.ModernDialog.CancelLink.textColor,
                        modifier = Modifier
                            .noRippleClickable { onDismiss() }
                            .padding(vertical = EInkTheme.ModernDialog.CancelLink.paddingVertical)
                    )
                }
            }
        }
    }
}

/**
 * Individual background template option with preview
 */
@Composable
private fun BackgroundTemplateOption(
    template: BackgroundTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val previewBitmap = remember(template.key) {
        generatePreviewBitmap(template.key)
    }

    // Wrapper with selection background highlight
    Box(
        modifier = Modifier
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .noRippleClickable(onClick = onClick)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(142.dp)
                    .border(
                        width = if (isSelected) 2.dp else EInkTheme.ModernDialog.borderWidth,
                        color = if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.borderColor,
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .clip(RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = template.displayName,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    modifier = Modifier
                        .width(104.dp)
                        .height(142.dp)
                )
            }

            Text(
                text = template.displayName,
                fontSize = EInkTheme.Typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.titleColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = EInkTheme.Global.paddingSmall)
            )
        }
    }
}

/**
 * Generate a preview bitmap for the given background template
 */
private fun generatePreviewBitmap(templateKey: String): Bitmap {
    val width = 160
    val height = 220 // Rectangular to match paper aspect ratio
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw white background first
    canvas.drawColor(Color.WHITE)

    // Medium gray paint for thumbnails
    val darkPaint = android.graphics.Paint().apply {
        color = Color.GRAY
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    val lineSpacing = 25 // Smaller spacing to show more lines
    val dotSize = 1.5f // Small dots

    when (templateKey) {
        "blank" -> { } // Already white
        "dotted" -> {
            for (y in lineSpacing until height step lineSpacing) {
                for (x in lineSpacing until width step lineSpacing) {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), dotSize, darkPaint)
                }
            }
        }
        "lined" -> {
            for (y in lineSpacing until height step lineSpacing) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), darkPaint)
            }
        }
        "squared" -> {
            // Smaller spacing (20) divides evenly into 160 width
            val gridSpacing = 20
            for (y in gridSpacing until height step gridSpacing) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), darkPaint)
            }
            for (x in gridSpacing until width step gridSpacing) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), darkPaint)
            }
        }
    }

    return bitmap
}
