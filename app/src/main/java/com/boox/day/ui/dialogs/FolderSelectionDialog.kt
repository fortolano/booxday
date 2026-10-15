package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.AppRepository
import com.boox.day.data.db.Notebook
import com.boox.day.ui.components.BreadCrumb
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme


@Composable
fun ShowFolderSelectionDialog(
    book: Notebook,
    notebookName: String,
    initialFolderId: String?,
    onCancel: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val appRepository = AppRepository(LocalContext.current)


    var currentFolderId by remember { mutableStateOf(initialFolderId) }
    val availableFolders by appRepository.folderRepository.getAllInFolder(currentFolderId)
        .observeAsState()
    val currentFolderName = currentFolderId?.let {
        appRepository.folderRepository.get(it).title
    } ?: "Library"
    val parentFolder = appRepository.folderRepository.getParent(currentFolderId)

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { onCancel() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = "Choose folder to move \"$notebookName\":",
                fontWeight = FontWeight.Bold,
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                color = EInkTheme.ModernDialog.titleColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            BreadCrumb(folderId = currentFolderId) { currentFolderId = it }
            // Folder List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { currentFolderId = parentFolder }
                        .padding(2.dp)
                        .background(if (currentFolderId == parentFolder) EInkTheme.Selection.background else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "..", fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Normal, color = if (currentFolderId == parentFolder) EInkTheme.Selection.foreground else EInkTheme.Colors.primary)
                }

                // List Folders
                availableFolders?.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable {
                                currentFolderId = folder.id // Navigate into child folder
                            }
                            .padding(8.dp)
                            .background(if (currentFolderId == folder.id) EInkTheme.Selection.background else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = folder.title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Normal, color = if (currentFolderId == folder.id) EInkTheme.Selection.foreground else EInkTheme.Colors.primary)
                    }
                }
            }

            // Cancel and Confirm Buttons
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                EinkSecondaryButton(text = "Cancel", onClick = onCancel)
                ActionButton("Select Folder", onClick = { onConfirm(currentFolderId) })
            }
            }
        }
    }
}
