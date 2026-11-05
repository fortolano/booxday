package com.boox.day.io

import android.content.Context
import androidx.core.net.toUri
import com.boox.day.export.MarkdownExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Carries out what the Export dialog asked for (Nov 2026). */
object ExportRun {

    /** [share]: straight to another app, keeping no copy, instead of saving to the folder. */
    data class Job(val target: ExportTarget, val choices: ExportChoices, val share: Boolean)

    /** Never throws, like [ExportEngine.export]. */
    suspend fun run(context: Context, job: Job, onProgress: (suspend (Int, Int) -> Unit)? = null): ExportOutcome {
        val choices = job.choices
        if (choices.kind == ExportKind.MARKDOWN) {
            val days = when (val target = job.target) {
                is ExportTarget.Days -> target.days
                is ExportTarget.DayPages -> listOf(target)
                else -> emptyList()
            }
            return MarkdownExport.export(context, days, job.target, choices.pageImages, onProgress)
        }
        val options = ExportOptions(
            copyToClipboard = false,
            targetFolderUri = if (job.share) shareFolder(context).toUri() else ExportFolders.chosen(context, choices.kind),
            overwrite = job.share,
            layout = if (choices.fitMore) ExportLayout.COMPACT else ExportLayout.ONE_PER_PAGE,
            typedText = choices.typedText,
            eventsAndTasks = choices.eventsAndTasks,
            onProgress = onProgress
        )
        val format = if (choices.kind == ExportKind.PDF) ExportFormat.PDF else ExportFormat.PNG
        return ExportEngine(context).export(job.target, format, options)
    }

    /** An empty folder inside the one the share provider serves; each Share replaces the last. */
    private suspend fun shareFolder(context: Context): File = withContext(Dispatchers.IO) {
        File(context.cacheDir, "exports/shared").apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
