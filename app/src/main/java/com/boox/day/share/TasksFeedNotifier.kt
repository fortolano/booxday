package com.boox.day.share

import android.content.Context
import com.boox.day.data.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tells apps reading the tasks feed that BooxDay's tasks changed, so they can re-query. */
object TasksFeedNotifier {
    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            TaskRepository.taskChanged.collect {
                runCatching { app.contentResolver.notifyChange(TasksFeedContract.BASE_URI, null) }
            }
        }
    }
}
