package com.boox.day.ui.dialogs

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log

private const val TAG = "ImagePickerDialog"

data class ImageItem(
    val uri: Uri,
    val name: String,
    val path: String
)

enum class ImageFolder(val displayName: String) {
    PICTURES("Pictures"),
    DOWNLOADS("Downloads"),
    DOCUMENTS("Documents"),
    DCIM("DCIM")
}

@Composable
fun ImagePickerDialog(
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf(ImageFolder.PICTURES) }
    var images by remember { mutableStateOf<List<ImageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load images when folder changes
    LaunchedEffect(selectedFolder) {
        isLoading = true
        images = withContext(Dispatchers.IO) {
            loadImagesFromFolder(context, selectedFolder)
        }
        isLoading = false
    }

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
                    .height(500.dp)
                    .background(EInkTheme.PopupPanel.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupPanel.borderWidth, EInkTheme.PopupPanel.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            ) {
            // Header row with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = EInkTheme.PopupPanel.padding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(44.dp))
                Text(
                    "Select Image",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.PopupPanel.titleFontSize,
                    color = EInkTheme.PopupPanel.titleColor
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

            Box(
                Modifier
                    .height(EInkTheme.Divider.subtleThickness)
                    .fillMaxWidth()
                    .background(EInkTheme.Divider.color)
            )

            // Folder tabs - e-ink optimized (all 4 tabs fit on screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.paddingSmall),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ImageFolder.entries.forEach { folder ->
                    val isSelected = selectedFolder == folder
                    Button(
                        onClick = { selectedFolder = folder },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isSelected) EInkTheme.Selection.background else EInkTheme.Colors.background,
                            contentColor = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Colors.primary
                        ),
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, EInkTheme.Colors.primary) else null
                    ) {
                        Text(folder.displayName, fontSize = EInkTheme.Typography.bodyMedium)
                    }
                }
            }

            Box(
                Modifier
                    .height(EInkTheme.Divider.thickness)
                    .fillMaxWidth()
                    .background(EInkTheme.Divider.color)
            )

            // Image grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(EInkTheme.Global.paddingSmall)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EInkTheme.Colors.primary)
                        }
                    }
                    images.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                                    tint = EInkTheme.Colors.gray
                                )
                                Text(
                                    text = "No images found in ${selectedFolder.displayName}",
                                    color = EInkTheme.Colors.gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(images) { imageItem ->
                                ImageThumbnail(
                                    imageItem = imageItem,
                                    onClick = {
                                        onImageSelected(imageItem.uri)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(
    imageItem: ImageItem,
    onClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(imageItem.uri) {
        withContext(Dispatchers.IO) {
            try {
                // Load thumbnail with size limit to avoid OOM
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                context.contentResolver.openInputStream(imageItem.uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }

                // Calculate sample size for larger thumbnail (3 per row)
                val targetSize = 250
                var sampleSize = 1
                while (options.outWidth / sampleSize > targetSize * 2 ||
                    options.outHeight / sampleSize > targetSize * 2) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }

                context.contentResolver.openInputStream(imageItem.uri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
                }
            } catch (e: Exception) {
                loadFailed = true
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .clip(RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(EInkTheme.Colors.offWhite),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = imageItem.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    loadFailed -> {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Failed to load",
                            tint = EInkTheme.Colors.gray,
                            modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(EInkTheme.Global.iconSize),
                            color = EInkTheme.Colors.gray,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            Text(
                text = imageItem.name,
                fontSize = EInkTheme.Typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.background)
                    .padding(4.dp),
                textAlign = TextAlign.Center,
                color = EInkTheme.Colors.primary
            )
        }
    }
}

private fun loadImagesFromFolder(context: Context, folder: ImageFolder): List<ImageItem> {
    return loadFromStandardFolder(context, folder)
}

private fun loadFromStandardFolder(context: Context, folder: ImageFolder): List<ImageItem> {
    val images = mutableListOf<ImageItem>()

    // First try MediaStore for Pictures and DCIM
    if (folder == ImageFolder.PICTURES || folder == ImageFolder.DCIM) {
        images.addAll(loadFromMediaStore(context, folder))
    }

    // Current user path
    val currentUserPath = when (folder) {
        ImageFolder.PICTURES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        ImageFolder.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ImageFolder.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        ImageFolder.DCIM -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        else -> return emptyList()
    }

    // Primary user path (for multi-user devices)
    val primaryUserPath = when (folder) {
        ImageFolder.PICTURES -> File("/storage/emulated/0/Pictures")
        ImageFolder.DOWNLOADS -> File("/storage/emulated/0/Download")
        ImageFolder.DOCUMENTS -> File("/storage/emulated/0/Documents")
        ImageFolder.DCIM -> File("/storage/emulated/0/DCIM")
        else -> null
    }

    images.addAll(loadFromFileSystem(currentUserPath))
    primaryUserPath?.let {
        if (it.absolutePath != currentUserPath.absolutePath) {
            images.addAll(loadFromFileSystem(it))
        }
    }

    // Remove duplicates based on path and sort by modification date
    return images
        .distinctBy { it.path }
        .sortedByDescending { File(it.path).lastModified() }
}

// Supported image MIME types - excludes virtual/cloud files that can't be opened
private val SUPPORTED_IMAGE_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/bmp"
)

/**
 * Check if a URI can be opened as an input stream.
 * This filters out deleted files, cloud/virtual files, and files without permission.
 */
private fun isFileAccessible(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}

private fun loadFromMediaStore(context: Context, folder: ImageFolder): List<ImageItem> {
    val images = mutableListOf<ImageItem>()

    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.MIME_TYPE
    )

    // Filter by folder
    val folderName = when (folder) {
        ImageFolder.PICTURES -> "Pictures"
        ImageFolder.DCIM -> "DCIM"
        else -> return emptyList()
    }

    // Filter by folder AND supported MIME types
    val mimeTypePlaceholders = SUPPORTED_IMAGE_MIME_TYPES.joinToString(",") { "?" }
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} IN ($mimeTypePlaceholders)"
    val selectionArgs = arrayOf("$folderName%") + SUPPORTED_IMAGE_MIME_TYPES

    val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    try {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "unknown"
                val path = cursor.getString(dataColumn) ?: ""
                val uri = ContentUris.withAppendedId(collection, id)

                // Verify file is accessible before adding
                if (!name.startsWith(".") && isFileAccessible(context, uri)) {
                    images.add(ImageItem(uri, name, path))
                }
            }
        }
    } catch (e: Exception) {
        // MediaStore query failed, will fall back to file system
        Log.e(TAG, "MediaStore folder query failed: ${e.message}", e)
    }

    return images
}

private fun loadFromFileSystem(folder: File): List<ImageItem> {
    val images = mutableListOf<ImageItem>()

    Log.d(TAG, "loadFromFileSystem: ${folder.absolutePath}")
    Log.d(TAG, "  exists: ${folder.exists()}, canRead: ${folder.canRead()}")

    if (!folder.exists() || !folder.canRead()) {
        Log.d(TAG, "  SKIPPING - folder doesn't exist or can't read")
        return images
    }

    val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    val files = folder.listFiles()
    Log.d(TAG, "  listFiles returned: ${files?.size ?: "null"} items")

    // Scan folder and subfolders (1 level deep), skip hidden directories
    files?.forEach { file ->
        // Skip hidden files/folders
        if (file.name.startsWith(".")) return@forEach

        if (file.isFile && file.canRead() && imageExtensions.contains(file.extension.lowercase())) {
            images.add(ImageItem(
                uri = Uri.fromFile(file),
                name = file.name,
                path = file.absolutePath
            ))
        } else if (file.isDirectory) {
            // Scan one level of subfolders
            file.listFiles()?.forEach { subFile ->
                // Skip hidden files
                if (subFile.name.startsWith(".")) return@forEach

                if (subFile.isFile && subFile.canRead() && imageExtensions.contains(subFile.extension.lowercase())) {
                    images.add(ImageItem(
                        uri = Uri.fromFile(subFile),
                        name = subFile.name,
                        path = subFile.absolutePath
                    ))
                }
            }
        }
    }

    return images
}
