package com.boox.day.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.boox.day.PERF_LOGS
import com.boox.day.TAG
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.PageView
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.History
import android.util.Log

@Composable
@ExperimentalComposeUiApi
fun EditorSurface(
    state: EditorState,
    page: PageView,
    history: History,
    // TAB-BASED FIX: Identifies which tab this canvas belongs to
    // true = Memos canvas, false = Tasks canvas
    // Used to determine if this canvas should receive stylus input based on active tab
    isMemosCanvas: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    if (PERF_LOGS) Log.i(TAG, "PERF: EditorSurface recompose at ${System.currentTimeMillis()}")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()

    ) {
        AndroidView(
            factory = { ctx ->
                val factoryStart = if (PERF_LOGS) System.currentTimeMillis() else 0L
                if (PERF_LOGS) Log.i(TAG, "PERF: AndroidView factory START at $factoryStart")

                DrawCanvas(ctx, coroutineScope, state, page, history).apply {
                    // TAB-BASED FIX: Set canvas type for tab-based input routing
                    this.isMemosCanvas = isMemosCanvas
                    if (PERF_LOGS) Log.i(TAG, "PERF: DrawCanvas constructor took ${System.currentTimeMillis() - factoryStart}ms")

                    val startInit = if (PERF_LOGS) System.currentTimeMillis() else 0L
                    val initSucceeded = init()
                    if (PERF_LOGS) Log.i(TAG, "PERF: DrawCanvas.init() took ${System.currentTimeMillis() - startInit}ms")

                    if (initSucceeded) {
                        val startObservers = if (PERF_LOGS) System.currentTimeMillis() else 0L
                        registerObservers()
                        if (PERF_LOGS) Log.i(TAG, "PERF: DrawCanvas.registerObservers() took ${System.currentTimeMillis() - startObservers}ms")
                    }
                    if (PERF_LOGS) Log.i(TAG, "PERF: AndroidView factory END at ${System.currentTimeMillis()}")
                }
            },
            // PERFORMANCE FIX: Update block handles state changes when date/page changes
            // factory runs only ONCE, but remember(pageId) creates new page/state/history
            // without this update block, DrawCanvas would hold STALE references and
            // ~20 zombie observer coroutines would accumulate per date change
            update = { view ->
                // Update tab type for routing
                view.isMemosCanvas = isMemosCanvas

                // DAY CHANGE FIX: Compare against tracked pageId snapshot, not live PageView value
                // When PageView is reused and mutated by changePage(), view.page.currentPageId
                // equals page.currentPageId (same object), making the old check always false.
                // lastUpdatedPageId stores the pageId from the last updateState() call.
                if (view.lastUpdatedPageId != page.currentPageId) {
                    view.updateState(page, state, history)
                }
            }
        )
    }
}
