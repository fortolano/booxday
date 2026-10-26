package com.boox.day.ui.dialogs

import android.os.Environment
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.components.ScrollMoreIndicator
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BACKUP_FILE_PREFIX = "booxday_backup_"

private data class BackupFileItem(
    val file: File,
    val displayName: String,
    val fileSize: Long,
    val lastModified: Long
)

private enum class BackupFolder(val displayName: String) {
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads")
}

@Composable
fun BackupFilePickerDialog(
    onFileSelected: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolder by remember { mutableStateOf(BackupFolder.DOWNLOADS) }
    var items by remember { mutableStateOf<List<BackupFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val listScrollState = rememberLazyListState()
    val canScrollMore by remember {
        derivedStateOf { listScrollState.canScrollForward }
    }

    LaunchedEffect(selectedFolder) {
        isLoading = true
        items = withContext(Dispatchers.IO) {
            loadBackupFiles(selectedFolder)
        }
        isLoading = false
    }

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
                    .height(380.dp)
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
                // Title row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(44.dp))

                    Text(
                        "Select Backup File",
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
                            modifier = Modifier.size(28.dp),
                            tint = EInkTheme.Colors.darkGray
                        )
                    }
                }

                // Divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // Folder tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BackupFolder.entries.forEach { folder ->
                        val isSelected = selectedFolder == folder
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .background(
                                    if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.background,
                                    RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                                )
                                .border(
                                    EInkTheme.ModernDialog.borderWidth,
                                    if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.borderColor,
                                    RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                                )
                                .noRippleClickable { selectedFolder = folder }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                folder.displayName,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }

                // Divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // File list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = EInkTheme.Colors.darkGray,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Scanning for backups...",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }
                        }
                        items.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No backup files found",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.gray
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Backups are saved as ${BACKUP_FILE_PREFIX}*.json",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = listScrollState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items) { item ->
                                    BackupFileRow(
                                        item = item,
                                        onClick = { onFileSelected(item.file) }
                                    )
                                }
                            }
                        }
                    }

                    // Scroll indicator
                    if (canScrollMore && !isLoading && items.isNotEmpty()) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) { ScrollMoreIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupFileRow(
    item: BackupFileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = "Backup file",
            modifier = Modifier.size(32.dp),
            tint = EInkTheme.Colors.darkGray
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.ModernDialog.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "${formatFileSize(item.fileSize)}  •  ${formatDate(item.lastModified)}",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Row divider
    Box(
        Modifier
            .height(EInkTheme.ModernDialog.borderWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(EInkTheme.ModernDialog.borderColor)
    )
}

private fun loadBackupFiles(folder: BackupFolder): List<BackupFileItem> {
    val dir = when (folder) {
        BackupFolder.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        BackupFolder.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return dir.listFiles()
        ?.filter { file ->
            file.isFile &&
                file.name.startsWith(BACKUP_FILE_PREFIX) &&
                file.name.endsWith(".json", ignoreCase = true)
        }
        ?.map { file ->
            BackupFileItem(
                file = file,
                displayName = file.name,
                fileSize = file.length(),
                lastModified = file.lastModified()
            )
        }
        ?.sortedByDescending { it.lastModified }
        ?: emptyList()
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDate(epochMs: Long): String {
    val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
    return format.format(Date(epochMs))
}
