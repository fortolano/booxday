package com.boox.day.share

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import com.boox.day.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Tells apps reading the tasks feed that BooxDay's tasks changed, so they can re-query.
 *
 * Watches the task and stroke tables themselves, so every change reaches readers: the app's own edits,
 * and also handwriting recognition, sync, repeating tasks and full screen Tasks, which write to the
 * database without going through TaskRepository, and new handwriting on a task row. A burst of
 * changes (writing, a page of recognition results) is one notice, a moment after it ends.
 */
object TasksFeedNotifier {
    private const val TAG = "TasksFeedNotifier"
    private const val QUIET_MS = 1_000L

    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val tracker = runCatching { AppDatabase.getDatabase(app).invalidationTracker }
                .onFailure { Log.w(TAG, "Database not available; tasks feed readers won't be notified", it) }
                .getOrNull() ?: return@launch
            callbackFlow {
                val observer = object : InvalidationTracker.Observer(arrayOf("task", "stroke")) {
                    override fun onInvalidated(tables: Set<String>) {
                        trySend(Unit)
                    }
                }
                tracker.addObserver(observer)
                awaitClose { tracker.removeObserver(observer) }
            }.debounce(QUIET_MS).collect {
                runCatching { app.contentResolver.notifyChange(TasksFeedContract.BASE_URI, null) }
            }
        }
    }
}
