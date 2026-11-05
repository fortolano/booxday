package com.boox.day.sleepscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.theme.EInkTheme
import java.io.File

/**
 * The image the user wants back when the sleep screen setting goes off (Nov 2026). BOOX clears the
 * user's own sleep screen when an app sets one and keeps it where apps can't read it, so BooxDay holds
 * its own copy: sized for the panel and as a JPEG, because BOOX refuses files over 10 MB. Device-only.
 */
object SleepBackImage {
    private const val TAG = "SleepBackImage"

    fun file(context: Context): File = File(context.applicationContext.filesDir, "sleep/back-image.jpg")

    fun exists(context: Context): Boolean = file(context).isFile

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Keeps a copy of [uri]. False when it can't be read; an image saved earlier then stays. */
    fun save(context: Context, uri: Uri): Boolean = store(context) { width, height ->
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = fitted(info.size.width, info.size.height, width, height)
            decoder.setTargetSize(w, h)
        }
    }

    /** Inside the panel, upright or on its side, whichever lets the image stay larger; never enlarged. */
    internal fun fitted(imageWidth: Int, imageHeight: Int, panelWidth: Int, panelHeight: Int): Pair<Int, Int> {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val scale = minOf(1f, maxOf(minOf(panelWidth / w, panelHeight / h), minOf(panelHeight / w, panelWidth / h)))
        return maxOf(1, (w * scale).toInt()) to maxOf(1, (h * scale).toInt())
    }

    internal fun store(context: Context, decode: (Int, Int) -> Bitmap): Boolean = try {
        val panel = SleepScreenPublisher.panel(context)
        val image = decode(panel.first, panel.second)
        // White under any transparent parts: a JPEG has none
        val flat = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(image, 0f, 0f, null)
        }
        image.recycle()
        val target = file(context).apply { parentFile?.mkdirs() }
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        flat.recycle()
        check(tmp.renameTo(target)) { "Could not save the image" }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Image not saved: ${e.javaClass.simpleName}")
        false
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Image too large to read")
        false
    }

    /** A small copy for the Preferences card, or null when no image is kept. */
    fun thumbnail(context: Context): Bitmap? {
        val saved = file(context)
        if (!saved.isFile) return null
        return BitmapFactory.decodeFile(saved.path, BitmapFactory.Options().apply { inSampleSize = 8 })
    }
}

/**
 * Shown when the sleep screen switch goes on (Nov 2026). BOOX clears the user's own sleep screen when an
 * app sets one and keeps it where apps can't read it, so the user picks what comes back at turn-off.
 */
@Composable
internal fun SleepTurnOnCard(onCancel: () -> Unit, onSkip: () -> Unit, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Text(
            text = "Replace your sleep screen?",
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = "BooxDay's page will replace your BOOX sleep screen. BOOX doesn't let apps read what you " +
                "have now, so BooxDay can't bring it back by itself.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = "Pick an image to go back to when you turn this off. If you skip this, BOOX shows its own image.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            EinkSecondaryButton(text = "Skip", onClick = onSkip)
            ActionButton(text = "Pick Image", onClick = onPick)
        }
    }
}

/** On the card while the switch is on: what comes back when it goes off, and a way to change it. */
@Composable
internal fun SleepBackImageRow(image: ImageBitmap?, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
        Text(
            text = "When turned off",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = EInkTheme.Global.touchTarget),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(72.dp)
                        .width(54.dp)
                        .border(1.dp, EInkTheme.Colors.primary)
                )
            }
            Text(
                text = if (image != null) "Your image comes back" else "BOOX's own image comes back",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                modifier = Modifier.weight(1f)
            )
            EinkSecondaryButton(text = if (image != null) "Change" else "Pick Image", onClick = onPick, width = 130.dp)
        }
    }
}
