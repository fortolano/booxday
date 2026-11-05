package com.boox.day.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/**
 * What another app shared, kept in files until the user decides (Nov 2026): a cold start or a killed
 * app loses nothing. Text comes from anywhere, so it's content, never instructions, and is never logged.
 */
object SharedInbox {
    const val MAX_TEXT = 20_000
    const val MAX_AI_TEXT = 8_000
    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

    data class Item(val id: String = UUID.randomUUID().toString(), val text: String = "", val image: String? = null)

    private val gson = Gson()
    private val _revision = MutableStateFlow(0)
    /** Goes up when something arrives, for a running app. */
    val revision: StateFlow<Int> = _revision

    private fun folder(context: Context) = File(context.applicationContext.filesDir, "shared").apply { mkdirs() }

    fun imageFile(context: Context, name: String) = File(folder(context), name)

    /** Long text is cut here, once. */
    fun add(context: Context, text: String, image: String?) {
        val item = Item(text = text.take(MAX_TEXT), image = image)
        val tmp = File(folder(context), "${item.id}.tmp")
        tmp.writeText(gson.toJson(item))
        check(tmp.renameTo(File(folder(context), "${System.currentTimeMillis()}-${item.id}.json")))
        _revision.value++
    }

    /** The oldest waiting: a second share waits its turn. */
    fun next(context: Context): Item? {
        val file = folder(context).listFiles { f -> f.name.endsWith(".json") }?.minByOrNull { it.name } ?: return null
        return runCatching { gson.fromJson(file.readText(), Item::class.java) }.getOrNull()
            ?: run {
                file.delete()
                next(context)
            }
    }

    /** Done with [item]: it won't come back. */
    fun remove(context: Context, item: Item) {
        folder(context).listFiles { f -> f.name.endsWith("-${item.id}.json") }?.forEach { it.delete() }
        item.image?.let { imageFile(context, it).delete() }
        _revision.value++
    }

    /** Only content:// streams, and only text or pictures. */
    fun accepts(type: String?, scheme: String?): Boolean =
        scheme == "content" && (type == "text/plain" || type?.startsWith("image/") == true)

    /** Upright, its longest side at most [maxSide]; null when it isn't a picture. */
    fun picture(file: File, maxSide: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
        }
    }.getOrNull()
}
