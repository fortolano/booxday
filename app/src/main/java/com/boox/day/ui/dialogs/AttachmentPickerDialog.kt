package com.boox.day.ui.dialogs

import android.os.Environment
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.io.EpubMetadataExtractor
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AttachmentPickerDialog"

data class AttachmentItem(
    val filePath: String,
    val displayName: String,
    val fileType: String,
    val fileSize: Long = 0
)

/**
 * Represents either a file or a folder in the picker
 */
sealed class FileSystemItem {
    data class FileItem(val attachment: AttachmentItem) : FileSystemItem()
    data class FolderItem(val path: String, val name: String) : FileSystemItem()
}

enum class AttachmentFolder(val displayName: String) {
    LIBRARY("Library"),
    BOOKS("Books"),
    SHOP("Shop"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads")
}

@Composable
fun AttachmentPickerDialog(
    onAttachmentSelected: (filePath: String, displayName: String, fileType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolder by remember { mutableStateOf(AttachmentFolder.LIBRARY) }
    var currentPath by remember { mutableStateOf<String?>(null) } // null = root of selected folder
    var items by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isGridView by remember { mutableStateOf(false) }

    // Scroll state for detecting "scroll for more"
    val listScrollState = rememberLazyListState()
    val gridScrollState = rememberLazyGridState()

    // Check if there's more content to scroll
    val canScrollMore by remember {
        derivedStateOf {
            if (isGridView) {
                gridScrollState.canScrollForward
            } else {
                listScrollState.canScrollForward
            }
        }
    }

    // Get root folder for navigation
    val rootFolder = remember(selectedFolder) {
        when (selectedFolder) {
            AttachmentFolder.LIBRARY -> null // Library doesn't support navigation
            AttachmentFolder.BOOKS -> getBooksFolder()
            AttachmentFolder.SHOP -> getShopFolder()
            AttachmentFolder.DOCUMENTS -> getDocumentsFolder()
            AttachmentFolder.DOWNLOADS -> getDownloadsFolder()
        }
    }

    // Reset path when folder tab changes
    LaunchedEffect(selectedFolder) {
        currentPath = null
    }

    // Load files when folder or path changes
    LaunchedEffect(selectedFolder, currentPath) {
        isLoading = true
        items = withContext(Dispatchers.IO) {
            if (selectedFolder == AttachmentFolder.LIBRARY) {
                // Library shows flat list of ebooks
                loadLibraryFiles().map { FileSystemItem.FileItem(it) }
            } else {
                // Other folders support navigation
                val targetFolder = if (currentPath != null) File(currentPath!!) else rootFolder
                loadFolderContents(targetFolder)
            }
        }
        isLoading = false
    }

    // Calculate if we can go back
    val canGoBack = currentPath != null && rootFolder != null && currentPath != rootFolder.absolutePath

    // Get current folder name for display
    val currentFolderName = if (currentPath != null) {
        File(currentPath!!).name
    } else {
        selectedFolder.displayName
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .height(420.dp)
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
                // Title row with view toggle and close button (compact for touch)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Spacer for symmetry
                    Spacer(modifier = Modifier.width(100.dp))

                    Text(
                        "Link File",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )

                    // View toggle and close buttons (larger touch targets)
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // View toggle button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .noRippleClickable { isGridView = !isGridView },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                                modifier = Modifier.size(28.dp),
                                tint = EInkTheme.Colors.darkGray
                            )
                        }

                        // Close button
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
                }

                // Subtle divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // Folder tabs - larger touch targets for tablet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AttachmentFolder.entries.forEach { folder ->
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

                // Subtle divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // Breadcrumb trail (only when navigating into subfolders)
                if (currentPath != null && selectedFolder != AttachmentFolder.LIBRARY) {
                    val breadcrumbSegments = buildBreadcrumbSegments(currentPath, rootFolder)
                    if (breadcrumbSegments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EInkTheme.Colors.offWhite)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            breadcrumbSegments.forEachIndexed { index, segment ->
                                val isLast = index == breadcrumbSegments.lastIndex
                                Text(
                                    text = segment,
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isLast) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                                )
                                if (!isLast) {
                                    Text(
                                        text = " › ",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }
                        }
                    }
                }

                // File list/grid or loading indicator
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
                                        color = EInkTheme.Colors.darkGray,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (selectedFolder == AttachmentFolder.LIBRARY)
                                            "Extracting book titles..."
                                        else
                                            "Loading files...",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }
                        }
                        items.isEmpty() && !canGoBack -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No files found in $currentFolderName",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                        else -> {
                            if (isGridView) {
                                // Grid view
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    state = gridScrollState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Back navigation item
                                    if (canGoBack) {
                                        item {
                                            BackNavigationGridItem(
                                                onClick = {
                                                    val parentPath = File(currentPath!!).parent
                                                    currentPath = if (parentPath == rootFolder?.absolutePath) null else parentPath
                                                }
                                            )
                                        }
                                    }
                                    items(items) { item ->
                                        when (item) {
                                            is FileSystemItem.FolderItem -> {
                                                FolderGridItem(
                                                    name = item.name,
                                                    onClick = { currentPath = item.path }
                                                )
                                            }
                                            is FileSystemItem.FileItem -> {
                                                AttachmentGridItem(
                                                    item = item.attachment,
                                                    onClick = {
                                                        onAttachmentSelected(
                                                            item.attachment.filePath,
                                                            item.attachment.displayName,
                                                            item.attachment.fileType
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // List view
                                LazyColumn(
                                    state = listScrollState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // Back navigation row
                                    if (canGoBack) {
                                        item {
                                            BackNavigationRow(
                                                onClick = {
                                                    val parentPath = File(currentPath!!).parent
                                                    currentPath = if (parentPath == rootFolder?.absolutePath) null else parentPath
                                                }
                                            )
                                        }
                                    }
                                    items(items) { item ->
                                        when (item) {
                                            is FileSystemItem.FolderItem -> {
                                                FolderRow(
                                                    name = item.name,
                                                    onClick = { currentPath = item.path }
                                                )
                                            }
                                            is FileSystemItem.FileItem -> {
                                                AttachmentFileRow(
                                                    item = item.attachment,
                                                    onClick = {
                                                        onAttachmentSelected(
                                                            item.attachment.filePath,
                                                            item.attachment.displayName,
                                                            item.attachment.fileType
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // "Scroll for more" indicator at bottom
                    if (canScrollMore && !isLoading && items.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll for more",
                                    modifier = Modifier.size(16.dp),
                                    tint = EInkTheme.Colors.gray
                                )
                                Text(
                                    text = "Scroll for more",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentFileRow(
    item: AttachmentItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon (larger for touch)
        FileIconImage(
            fileType = item.fileType,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Display name (title or filename) - larger font
            Text(
                text = item.displayName,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.ModernDialog.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            // File path (truncated) - larger font
            Text(
                text = truncatePath(item.filePath),
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // File type badge (slightly larger)
        Text(
            text = item.fileType.uppercase(),
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier
                .border(
                    EInkTheme.ModernDialog.borderWidth,
                    EInkTheme.ModernDialog.borderColor,
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }

    // Divider
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(EInkTheme.ModernDialog.borderWidth)
            .background(EInkTheme.ModernDialog.borderColor)
    )
}

/**
 * Grid item for attachment picker (thumbnail view)
 */
@Composable
private fun AttachmentGridItem(
    item: AttachmentItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.ModernDialog.borderWidth,
                EInkTheme.ModernDialog.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // File type icon (larger for grid)
        FileIconImage(
            fileType = item.fileType,
            modifier = Modifier.size(44.dp)
        )

        Spacer(Modifier.height(6.dp))

        // Display name
        Text(
            text = item.displayName,
            fontSize = EInkTheme.Typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.ModernDialog.titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // File type badge
        Text(
            text = item.fileType.uppercase(),
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.gray
        )
    }
}

/**
 * Back navigation row for list view
 */
@Composable
private fun BackNavigationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
            modifier = Modifier.size(24.dp),
            tint = EInkTheme.Colors.darkGray
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "..",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.ModernDialog.titleColor
        )
    }
    // Divider
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(EInkTheme.ModernDialog.borderWidth)
            .background(EInkTheme.ModernDialog.borderColor)
    )
}

/**
 * Back navigation item for grid view
 */
@Composable
private fun BackNavigationGridItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.ModernDialog.borderWidth,
                EInkTheme.ModernDialog.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
            modifier = Modifier.size(44.dp),
            tint = EInkTheme.Colors.darkGray
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "..",
            fontSize = EInkTheme.Typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.ModernDialog.titleColor
        )
    }
}

/**
 * Folder row for list view
 */
@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            modifier = Modifier.size(24.dp),
            tint = EInkTheme.Colors.darkGray
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.ModernDialog.titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    // Divider
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(EInkTheme.ModernDialog.borderWidth)
            .background(EInkTheme.ModernDialog.borderColor)
    )
}

/**
 * Folder item for grid view
 */
@Composable
private fun FolderGridItem(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.ModernDialog.borderWidth,
                EInkTheme.ModernDialog.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            modifier = Modifier.size(44.dp),
            tint = EInkTheme.Colors.darkGray
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = EInkTheme.Typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.ModernDialog.titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Represents an icon that can be either a vector resource or a Material icon
 */
private sealed class FileIcon {
    data class DrawableRes(val resId: Int) : FileIcon()
    data class VectorIcon(val imageVector: ImageVector) : FileIcon()
}

/**
 * Get the appropriate icon for a file type
 */
private fun getFileIcon(fileType: String): FileIcon {
    return when (fileType.lowercase()) {
        // Documents
        "pdf" -> FileIcon.DrawableRes(R.drawable.ic_file_pdf)
        "doc", "docx", "odt", "rtf" -> FileIcon.DrawableRes(R.drawable.ic_file_document)
        "txt", "md", "log", "json", "xml", "html", "css", "js", "kt", "java", "py" -> FileIcon.DrawableRes(R.drawable.ic_file_text)

        // Spreadsheets
        "xls", "xlsx", "ods", "csv" -> FileIcon.VectorIcon(Icons.Default.TableChart)

        // Presentations
        "ppt", "pptx", "odp" -> FileIcon.VectorIcon(Icons.Default.Slideshow)

        // Audio
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> FileIcon.DrawableRes(R.drawable.ic_file_audio)

        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> FileIcon.VectorIcon(Icons.Default.Movie)

        // Images
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico" -> FileIcon.VectorIcon(Icons.Default.Image)

        // Ebooks
        "epub", "mobi", "azw3", "djvu", "cbz", "cbr" -> FileIcon.DrawableRes(R.drawable.ic_file_text)

        // Archives
        "zip", "rar", "7z", "tar", "gz" -> FileIcon.DrawableRes(R.drawable.ic_file_generic)

        // Generic fallback
        else -> FileIcon.DrawableRes(R.drawable.ic_file_generic)
    }
}

/**
 * Composable to display a file icon
 */
@Composable
private fun FileIconImage(
    fileType: String,
    modifier: Modifier = Modifier,
    tint: Color = EInkTheme.Colors.darkGray
) {
    when (val icon = getFileIcon(fileType)) {
        is FileIcon.DrawableRes -> {
            Icon(
                painter = painterResource(id = icon.resId),
                contentDescription = fileType,
                modifier = modifier,
                tint = tint
            )
        }
        is FileIcon.VectorIcon -> {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = fileType,
                modifier = modifier,
                tint = tint
            )
        }
    }
}

private fun truncatePath(path: String): String {
    // Show last 2-3 path segments
    val segments = path.split("/")
    return if (segments.size > 3) {
        ".../" + segments.takeLast(3).joinToString("/")
    } else {
        path
    }
}

/**
 * Load folder contents (folders and files) for navigation.
 * Returns folders first, then files, both sorted alphabetically.
 * Allows all file types to be linked.
 */
private fun loadFolderContents(folder: File?): List<FileSystemItem> {
    if (folder == null || !folder.exists()) return emptyList()

    val items = mutableListOf<FileSystemItem>()

    try {
        val files = folder.listFiles() ?: return emptyList()

        // Separate folders and files
        val folders = files.filter { it.isDirectory && !it.name.startsWith(".") }
        val documents = files.filter { it.isFile && !it.name.startsWith(".") }

        // Add folders first (sorted by name)
        folders.sortedBy { it.name.lowercase() }.forEach { dir ->
            items.add(FileSystemItem.FolderItem(dir.absolutePath, dir.name))
        }

        // Add all files (sorted by name)
        documents.sortedBy { it.name.lowercase() }.forEach { file ->
            val ext = file.extension.lowercase()
            val displayName = when (ext) {
                "epub" -> EpubMetadataExtractor.extractMetadata(file.absolutePath)?.title
                    ?: file.nameWithoutExtension
                else -> file.nameWithoutExtension
            }
            items.add(
                FileSystemItem.FileItem(
                    AttachmentItem(
                        filePath = file.absolutePath,
                        displayName = displayName,
                        fileType = if (ext.isEmpty()) "file" else ext,
                        fileSize = file.length()
                    )
                )
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading folder contents: ${e.message}", e)
    }

    return items
}

/**
 * Load cloud-synced ebooks from .ksync folder.
 * Extracts titles from epub metadata.
 */
private fun loadLibraryFiles(): List<AttachmentItem> {
    val items = mutableListOf<AttachmentItem>()

    try {
        val ksyncDir = File(Environment.getExternalStorageDirectory(), ".ksync")
        if (!ksyncDir.exists()) {
            Log.d(TAG, ".ksync folder not found")
            return items
        }

        // Pattern: .ksync/{account}/reader/{uuid}/book/{uuid}.epub
        ksyncDir.listFiles()?.forEach { accountDir ->
            if (!accountDir.isDirectory) return@forEach

            val readerDir = File(accountDir, "reader")
            if (!readerDir.exists()) return@forEach

            readerDir.listFiles()?.forEach { uuidDir ->
                if (!uuidDir.isDirectory) return@forEach

                val bookDir = File(uuidDir, "book")
                if (!bookDir.exists()) return@forEach

                bookDir.listFiles { file ->
                    file.isFile && file.extension.lowercase() == "epub"
                }?.forEach { epubFile ->
                    val metadata = EpubMetadataExtractor.extractMetadata(epubFile.absolutePath)
                    items.add(
                        AttachmentItem(
                            filePath = epubFile.absolutePath,
                            displayName = metadata?.title ?: epubFile.nameWithoutExtension,
                            fileType = "epub",
                            fileSize = epubFile.length()
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading library files: ${e.message}", e)
    }

    // Sort by title
    return items.sortedBy { it.displayName.lowercase() }
}

private fun getBooksFolder(): File? {
    return File(Environment.getExternalStorageDirectory(), "Books").takeIf { it.exists() }
}

private fun getShopFolder(): File? {
    return File(Environment.getExternalStorageDirectory(), "Shop").takeIf { it.exists() }
}

private fun getDocumentsFolder(): File? {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
}

private fun getDownloadsFolder(): File? {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}

private fun buildBreadcrumbSegments(currentPath: String?, rootFolder: File?): List<String> {
    if (currentPath == null || rootFolder == null) return emptyList()
    val relativePath = currentPath.removePrefix(rootFolder.absolutePath).trim('/')
    val segments = relativePath.split("/").filter { it.isNotEmpty() }
    return if (segments.size > 3) {
        listOf("...") + segments.takeLast(2)
    } else {
        segments
    }
}
