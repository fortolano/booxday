package com.boox.day.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.io.ExportEngine
import com.boox.day.io.ExportFolders
import com.boox.day.io.ExportOutcome
import com.boox.day.io.ExportTarget
import com.boox.day.ocr.IndexingStatus
import com.boox.day.today.DayDigest
import com.boox.day.today.DayDigestLoader
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Export as Markdown (Nov 2026): one note per day in the Daily notes format, so export and Daily
 * notes update the same file and what the user wrote around BooxDay's part stays. With page images,
 * each page goes in attachments/ as a PNG, linked with plain Markdown that Obsidian and others read.
 */
object MarkdownExport {
    const val ATTACHMENTS = "attachments"
    private val log = TaggedLog("MarkdownExport")

    /** The file's path in the note, for the page numbered [pageNumber] (from 1). */
    fun imagePath(date: String, pageNumber: Int) = "$ATTACHMENTS/$date-p$pageNumber.png"

    suspend fun export(
        context: Context,
        days: List<ExportTarget.DayPages>,
        target: ExportTarget,
        pageImages: Boolean,
        onProgress: (suspend (Int, Int) -> Unit)? = null
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val folder = ExportFolders.notesFolder(app)
        val use24Hour = GlobalAppSettings.current.timeFormat == AppSettings.TimeFormat.TwentyFourHour
        val names = mutableListOf<String>()
        try {
            days.forEachIndexed { index, day ->
                onProgress?.invoke(index, days.size)
                val digest = DayDigestLoader(app).load(LocalDate.parse(day.date), withMemoText = true, withFullTaskText = true)
                val shown = DailyNoteMarkdown.shownPages(digest)
                if (shown.isEmpty()) return@forEachIndexed
                val images = if (pageImages) writeImages(app, folder, digest) else emptyMap()
                val block = DailyNoteMarkdown.block(
                    digest, use24Hour, memoWaitReason = IndexingStatus.waitingFor.value?.shortText, pageImages = images
                )
                val name = DailyNoteMarkdown.fileName(digest)
                DailyNoteWriter.writeTo(app, folder, name) { existing ->
                    DailyNoteMarkdown.merge(existing, block, DailyNoteMarkdown.frontMatter(digest))
                }
                names += name
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            log.e("Markdown export ran out of memory", e)
            return@withContext ExportOutcome.Failed(ExportOutcome.TOO_BIG_DATES_MESSAGE)
        } catch (e: Exception) {
            log.e("Markdown export failed: ${e.message}", e)
            return@withContext ExportOutcome.Failed(ExportOutcome.FAILED_MESSAGE)
        }
        if (names.isEmpty()) return@withContext ExportOutcome.Empty(ExportOutcome.emptyMessage(target))
        val where = ExportFolders.label(app, com.boox.day.io.ExportKind.MARKDOWN)
        val what = names.singleOrNull() ?: "${names.size} notes"
        // Nothing to pass to Share: a note and its pictures belong together in their folder
        ExportOutcome.Saved(emptyList(), "text/markdown", "Saved $what to $where")
    }

    /** Writes each shown page as a picture; the page numbers and their paths. */
    private suspend fun writeImages(context: Context, folder: Uri?, digest: DayDigest): Map<Int, String> {
        val engine = ExportEngine(context)
        val paths = mutableMapOf<Int, String>()
        DailyNoteMarkdown.shownPages(digest).forEachIndexed { index, page ->
            val number = index + 1
            val bitmap = engine.renderBitmapForPage(page.pageId) ?: return@forEachIndexed
            try {
                val path = imagePath(digest.date.toString(), number)
                DailyNoteWriter.writeBinary(context, folder, ATTACHMENTS, path.substringAfterLast('/')) { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Image not written")
                }
                paths[number] = path
            } finally {
                bitmap.recycle()
            }
        }
        return paths
    }
}
