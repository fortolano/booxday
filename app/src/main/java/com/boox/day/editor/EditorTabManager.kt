package com.boox.day.editor

import com.boox.day.data.db.KvProxy
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.Mode
import com.boox.day.editor.utils.Eraser
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.PenSetting
import com.boox.day.ui.views.EditorTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Data class to hold transient tool state for a tab session.
 * This persists across day switches but resets on tab switches (unless we decide otherwise).
 */
data class ToolSessionState(
    val mode: Mode,
    val pen: Pen,
    val eraser: Eraser,
    val penSettings: Map<String, PenSetting>
)

/**
 * Manages dual-canvas state for Memos and Tasks tabs.
 * Provides the active EditorControlTower to the shared Toolbar.
 *
 * PERFORMANCE: This class uses direct property access (getters) for activeControlTower
 * and activeEditorState to eliminate StateFlow overhead. Only currentTab uses StateFlow
 * because Compose needs to observe it for recomposition.
 *
 * PERSISTENCE: Saves and restores last active tab to/from AppSettings.
 */
class EditorTabManager(
    private val kvProxy: KvProxy,
    private val coroutineScope: CoroutineScope
) {
    // Current tab (source of truth)
    // StateFlow allows Compose to observe and recompose when tab changes
    // FIX: Use startTab (user's preference from General Settings) instead of lastActiveTab
    // The user sets their preferred default tab in General Settings, so we should respect it
    val currentTab = MutableStateFlow(
        run {
            val preferredTab = GlobalAppSettings.current.startTab
            if (preferredTab == AppSettings.StartTab.Tasks) {
                EditorTab.Tasks
            } else {
                EditorTab.Memos
            }
        }
    )

    // Control towers for each tab (set by canvas areas when they mount)
    // Nullable because canvases may not be mounted yet
    // REACTIVE: Using StateFlow so SharedToolbar recomposes when registration happens
    private val _memosControlTower = MutableStateFlow<EditorControlTower?>(null)
    private val _tasksControlTower = MutableStateFlow<EditorControlTower?>(null)

    // EditorStates for each tab (needed for Toolbar UI state)
    private val _memosEditorState = MutableStateFlow<EditorState?>(null)
    private val _tasksEditorState = MutableStateFlow<EditorState?>(null)

    // Session state for tools (persists across day switches)
    private var memosSessionState: ToolSessionState? = null
    private var tasksSessionState: ToolSessionState? = null

    /**
     * Save the current tool state for a tab.
     * Called before disposing EditorState during day switch.
     */
    fun saveToolState(tab: EditorTab, state: EditorState) {
        android.util.Log.i("TOOL_STATE", "Saving $tab state: mode=${state.mode}, pen=${state.pen.penName}")
        val sessionState = ToolSessionState(
            mode = state.mode,
            pen = state.pen,
            eraser = state.eraser,
            penSettings = state.penSettings.toMap() // Copy the map
        )
        if (tab == EditorTab.Memos) {
            memosSessionState = sessionState
        } else {
            tasksSessionState = sessionState
        }
    }

    /**
     * Restore the tool state for a tab if available.
     * Called after creating new EditorState during day switch.
     * Returns true if state was restored.
     */
    fun restoreToolState(tab: EditorTab, state: EditorState): Boolean {
        val sessionState = if (tab == EditorTab.Memos) memosSessionState else tasksSessionState
        return if (sessionState != null) {
            android.util.Log.i("TOOL_STATE", "Restoring $tab state: mode=${sessionState.mode}, pen=${sessionState.pen.penName}")
            state.mode = sessionState.mode
            state.pen = sessionState.pen
            state.eraser = sessionState.eraser
            // Merge settings to preserve any defaults not in session, but override with session
            state.penSettings = state.penSettings + sessionState.penSettings
            true
        } else {
            android.util.Log.i("TOOL_STATE", "No saved state for $tab")
            false
        }
    }

    /**
     * Register the Memos canvas and its control tower.
     * Called by MemosCanvasArea when it mounts.
     * REACTIVE: Updates StateFlow to trigger SharedToolbar recomposition.
     * TOUCHHELPER FIX: Sets activeCanvasPageId if this is the current tab.
     */
    fun registerMemosCanvas(state: EditorState, controlTower: EditorControlTower) {
        _memosEditorState.value = state
        _memosControlTower.value = controlTower

        // If Memos is the current tab, set it as active to enable its TouchHelper
        if (currentTab.value == EditorTab.Memos) {
            DrawCanvas.activeCanvasPageId.value = state.pageId
        }
    }

    /**
     * Register the Tasks canvas and its control tower.
     * Called by TasksCanvasArea when it mounts.
     * REACTIVE: Updates StateFlow to trigger SharedToolbar recomposition.
     * TOUCHHELPER FIX: Sets activeCanvasPageId if this is the current tab.
     */
    fun registerTasksCanvas(state: EditorState, controlTower: EditorControlTower) {
        _tasksEditorState.value = state
        _tasksControlTower.value = controlTower

        // If Tasks is the current tab, set it as active to enable its TouchHelper
        if (currentTab.value == EditorTab.Tasks) {
            DrawCanvas.activeCanvasPageId.value = state.pageId
        }
    }

    /**
     * Unregister the Memos canvas.
     * Called by MemosCanvasArea when it unmounts.
     */
    fun unregisterMemosCanvas() {
        _memosEditorState.value = null
        _memosControlTower.value = null
    }

    /**
     * Unregister the Tasks canvas.
     * Called by TasksCanvasArea when it unmounts.
     */
    fun unregisterTasksCanvas() {
        _tasksEditorState.value = null
        _tasksControlTower.value = null
    }

    /**
     * Flow of the currently active EditorControlTower based on currentTab.
     *
     * REACTIVE: Emits when either currentTab changes OR when registration happens.
     * This ensures SharedToolbar recomposes when canvases mount.
     */
    val activeControlTowerFlow: Flow<EditorControlTower?> = combine(
        currentTab,
        _memosControlTower,
        _tasksControlTower
    ) { tab, memos, tasks ->
        if (tab == EditorTab.Memos) memos else tasks
    }

    /**
     * Flow of the currently active EditorState based on currentTab.
     *
     * REACTIVE: Emits when either currentTab changes OR when registration happens.
     * This ensures SharedToolbar recomposes when canvases mount.
     */
    val activeEditorStateFlow: Flow<EditorState?> = combine(
        currentTab,
        _memosEditorState,
        _tasksEditorState
    ) { tab, memos, tasks ->
        if (tab == EditorTab.Memos) memos else tasks
    }

    /**
     * Direct access to active control tower (for non-Compose code).
     *
     * PERFORMANCE: Direct property access - no Flow overhead.
     */
    val activeControlTower: EditorControlTower?
        get() = if (currentTab.value == EditorTab.Memos) _memosControlTower.value else _tasksControlTower.value

    /**
     * Direct access to active editor state (for non-Compose code).
     *
     * PERFORMANCE: Direct property access - no Flow overhead.
     */
    val activeEditorState: EditorState?
        get() = if (currentTab.value == EditorTab.Memos) _memosEditorState.value else _tasksEditorState.value

    /**
     * Switch to Memos tab.
     *
     * PERFORMANCE: Direct StateFlow update triggers only necessary recompositions.
     * PERSISTENCE: Saves tab state to settings for session restore.
     * E-INK FIX: Triggers forceUpdate to ensure screen refreshes after scribble mode.
     * TOUCHHELPER FIX: Sets activeCanvasPageId to ensure only Memos canvas receives pen input.
     * Also updates legacy DrawCanvas.memoModeChanged for backwards compatibility.
     *
     * STALENESS FIX: Accepts targetDate/targetPageId to force reload if lazy loading
     * caused the tab to become stale while inactive.
     */
    fun switchToMemos(targetDate: String, targetPageId: String?) {
        if (currentTab.value == EditorTab.Memos) return  // Already on Memos

        val memosDate = _memosEditorState.value?.dayDate
        val memosPageId = _memosEditorState.value?.pageId

        // STALENESS CHECK: If active tab is becoming visible but has old data (due to lazy load), force update
        if (targetPageId != null && memosPageId != targetPageId) {
            _memosControlTower.value?.loadPage(targetPageId, targetDate)
        } else if (memosDate != targetDate && targetPageId != null) {
            // Backup check: Page ID matches but date string doesn't? Force update to be safe
            _memosControlTower.value?.loadPage(targetPageId, targetDate)
        }

        // Force Pen Mode on Tab Switch to prevent tool flash
        _memosEditorState.value?.mode = com.boox.day.editor.state.Mode.Draw

        currentTab.value = EditorTab.Memos
        // Save to settings (async to avoid blocking tab switch)
        coroutineScope.launch(Dispatchers.IO) {
            kvProxy.setAppSettings(
                GlobalAppSettings.current.copy(lastActiveTab = AppSettings.StartTab.Memos)
            )
        }
        // Also update legacy StateFlow for backwards compatibility during migration
        DrawCanvas.memoModeChanged.value = true

        // TOUCHHELPER FIX: Set active canvas so only Memos TouchHelper receives pen input
        // This prevents strokes from going to the wrong (Tasks) page
        DrawCanvas.activeCanvasPageId.value = _memosEditorState.value?.pageId

        // NOTE: Removed forceUpdate.tryEmit(null) - it was causing stroke clearing bug
        // The e-ink refresh is handled by reactivateRawDrawing() via drawCanvasToView()
    }

    /**
     * Switch to Tasks tab.
     *
     * PERFORMANCE: Direct StateFlow update triggers only necessary recompositions.
     * PERSISTENCE: Saves tab state to settings for session restore.
     * E-INK FIX: Triggers forceUpdate to ensure screen refreshes after scribble mode.
     * TOUCHHELPER FIX: Sets activeCanvasPageId to ensure only Tasks canvas receives pen input.
     * Also updates legacy DrawCanvas.memoModeChanged for backwards compatibility.
     *
     * STALENESS FIX: Accepts targetDate/targetPageId to force reload if lazy loading
     * caused the tab to become stale while inactive.
     */
    fun switchToTasks(targetDate: String, targetPageId: String?) {
        if (currentTab.value == EditorTab.Tasks) return  // Already on Tasks    

        val tasksDate = _tasksEditorState.value?.dayDate
        val tasksPageId = _tasksEditorState.value?.pageId

        // STALENESS CHECK: If active tab is becoming visible but has old data (due to lazy load), force update
        if (targetPageId != null && tasksPageId != targetPageId) {
            _tasksControlTower.value?.loadPage(targetPageId, targetDate)
        } else if (tasksDate != targetDate && targetPageId != null) {
            // Backup check: Page ID matches but date string doesn't? Force update to be safe
            _tasksControlTower.value?.loadPage(targetPageId, targetDate)
        }

        // Force Pen Mode on Tab Switch to prevent tool flash
        _tasksEditorState.value?.mode = com.boox.day.editor.state.Mode.Draw

        currentTab.value = EditorTab.Tasks
        // Save to settings (async to avoid blocking tab switch)
        coroutineScope.launch(Dispatchers.IO) {
            kvProxy.setAppSettings(
                GlobalAppSettings.current.copy(lastActiveTab = AppSettings.StartTab.Tasks)
            )
        }
        DrawCanvas.memoModeChanged.value = false

        // TOUCHHELPER FIX: Set active canvas so only Tasks TouchHelper receives pen input
        // This prevents strokes from going to the wrong (Memos) page
        DrawCanvas.activeCanvasPageId.value = _tasksEditorState.value?.pageId

        // NOTE: Removed forceUpdate.tryEmit(null) - it was causing stroke clearing bug
        // The e-ink refresh is handled by reactivateRawDrawing() via drawCanvasToView()
    }
}
