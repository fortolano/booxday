package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.TextAnnotation
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Context menu for text box actions (shown on long-press).
 * Provides Copy, Cut, Edit, Style, and Delete options.
 * E-ink optimized with black/white styling.
 */
@Composable
fun TextBoxContextMenu(
    annotation: TextAnnotation,
    positionX: Float,
    positionY: Float,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onStyle: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(positionX.toInt(), positionY.toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = EInkTheme.PopupMenu.width)
                .background(EInkTheme.PopupMenu.background)
                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.PopupMenu.padding)
        ) {
            ContextMenuItem(
                icon = Icons.Default.Edit,
                text = "Edit",
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )

            ContextMenuItem(
                icon = Icons.Default.ContentCopy,
                text = "Copy",
                onClick = {
                    onCopy()
                    onDismiss()
                }
            )

            ContextMenuItem(
                icon = Icons.Default.ContentCut,
                text = "Cut",
                onClick = {
                    onCut()
                    onDismiss()
                }
            )

            Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)

            ContextMenuItem(
                icon = Icons.Default.FormatSize,
                text = "Style",
                onClick = {
                    onStyle()
                    onDismiss()
                }
            )

            Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)

            ContextMenuItem(
                icon = Icons.Default.Delete,
                text = "Delete",
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = EInkTheme.MenuItem.iconColor,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize)
        )
        Spacer(Modifier.width(EInkTheme.MenuItem.iconTextSpacing))
        Text(
            text,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = EInkTheme.MenuItem.textColor
        )
    }
}
