package com.boox.day.share

import android.net.Uri

/** Public, read-only contract other apps can copy to read BooxDay tasks. */
object TasksFeedContract {
    const val AUTHORITY = "com.boox.day.tasks"
    const val PATH_DUE = "due"
    const val PATH_TODAY = "today"
    const val PATH_OPEN = "open"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

    const val ID = "_id"                     // row number in this result, not stable
    const val TASK_ID = "task_id"            // stable BooxDay task id
    const val TITLE = "title"                // converted, typed or recognised text; "Handwritten task" if none
    const val HANDWRITTEN_ONLY = "handwritten_only" // 1 when there is no text yet
    const val DAY = "day"                    // yyyy-MM-dd page the task lives on
    const val DUE_DAY = "due_day"            // yyyy-MM-dd or null
    const val COMPLETED = "completed"        // 0 or 1
    const val PRIORITY = "priority"          // "", "A", "B" or "C"
    const val OVERDUE = "overdue"            // 1 when open and due before today
    const val PARENT_TASK_ID = "parent_task_id"
    const val UPDATED_AT = "updated_at"      // epoch milliseconds

    val COLUMNS = arrayOf(ID, TASK_ID, TITLE, HANDWRITTEN_ONLY, DAY, DUE_DAY, COMPLETED, PRIORITY, OVERDUE, PARENT_TASK_ID, UPDATED_AT)
}
