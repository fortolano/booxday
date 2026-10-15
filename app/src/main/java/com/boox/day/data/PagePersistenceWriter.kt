package com.boox.day.data

import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single, ordered writer for everything PageView persists (strokes, images, text boxes,
 * attachments, page timestamps).
 *
 * A lasso move deletes the selected rows and then inserts the moved copies under the SAME ids.
 * When each write was its own Dispatchers.IO coroutine, the insert could run first and the delete
 * then removed the moved ink (A1b). Here writes run one at a time in submission order: the
 * one-thread dispatcher keeps the queue order, and the fair mutex keeps it across any suspension
 * inside a write. SQLite allows one writer at a time anyway, so this costs nothing measurable.
 *
 * Writes run on this process-lifetime scope, not the screen's: once submitted, both halves of a
 * move complete even if the screen is torn down in between. A failing write is logged and the
 * queue continues.
 */
object PagePersistenceWriter {
    private val log = TaggedLog("PagePersistenceWriter")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val mutex = Mutex()

    /** Queues [write] behind every write submitted before it. */
    fun submit(description: String, write: suspend () -> Unit): Job = scope.launch {
        mutex.withLock {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Write failed ($description): ${e.message}", e)
            }
        }
    }
}
