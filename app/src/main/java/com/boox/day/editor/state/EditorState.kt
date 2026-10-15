package com.boox.day.editor.state

import android.graphics.Color
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.data.AppRepository
import com.boox.day.data.datastore.EditorSettingCacheManager
import com.boox.day.editor.PageView
import com.boox.day.editor.utils.Eraser
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.PenSetting
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import android.util.Log
import com.boox.day.utils.TaggedLog

enum class Mode {
    Draw, Erase, Select, Line
}

@Stable
class MenuStates {
    var isStrokeSelectionOpen by mutableStateOf(false)
    var isMenuOpen by mutableStateOf(false)
    var isBackgroundSelectorModalOpen by mutableStateOf(false)
    fun closeAll() {
        isStrokeSelectionOpen = false
        isMenuOpen = false
        isBackgroundSelectorModalOpen = false
    }

    val anyMenuOpen: Boolean
        get() = isStrokeSelectionOpen || isMenuOpen || isBackgroundSelectorModalOpen
}


class EditorState(
    val bookId: String? = null,
    var dayDate: String? = null,  // For day-based pages (calendar mode)
    val pageId: String,
    val pageView: PageView,
    val appRepository: AppRepository,
    val onPageChange: (String) -> Unit
) {
    var currentPageId by mutableStateOf(pageId)
        private set

    // Day page info for toolbar display (only used in calendar mode)
    var dayPageInfo by mutableStateOf<com.boox.day.data.db.DayPageInfo?>(null)
        private set

    fun refreshDayPageInfo() {
        val currentDate = dayDate
        if (currentDate != null) {
            dayPageInfo = appRepository.dayPageRepository.getPageInfoForDate(currentDate)
        }
    }

    fun getNextPage(): String? {
        return if (bookId != null) {
            val newPageId = appRepository.getNextPageIdFromBookAndPageOrCreate(
                pageId = currentPageId, notebookId = bookId
            )
            newPageId
        } else null
    }

    fun getPreviousPage(): String? {
        return if (bookId != null) {
            val newPageId = appRepository.getPreviousPageIdFromBookAndPage(
                pageId = currentPageId, notebookId = bookId
            )
            newPageId
        } else null
    }

    // Day page navigation (for calendar mode)
    fun getNextDayPage(): String? {
        val currentDate = dayDate ?: return null
        return appRepository.dayPageRepository.getNextPageId(currentDate)
    }

    fun getPreviousDayPage(): String? {
        val currentDate = dayDate ?: return null
        return appRepository.dayPageRepository.getPreviousPageId(currentDate)
    }

    fun createNewDayPage(): String? {
        val currentDate = dayDate ?: return null
        return appRepository.dayPageRepository.createNewPageForDate(currentDate)
    }

    fun isOnLastDayPage(): Boolean {
        val info = dayPageInfo ?: return true
        return info.currentIndex >= info.totalPages - 1
    }

    fun isOnFirstDayPage(): Boolean {
        val info = dayPageInfo ?: return true
        return info.currentIndex <= 0
    }


    fun updateOpenedPage(newPageId: String) {
        Log.d("EditorView", "Update open page to $newPageId")
        if (bookId != null) {
            appRepository.bookRepository.setOpenPageId(bookId, newPageId)
        }
        if (newPageId != currentPageId) {
            Log.d("EditorView", "Page changed")
            onPageChange(newPageId)
            currentPageId = newPageId
        } else {
            Log.d("EditorView", "Tried to change to same page!")
            SnackState.globalSnackFlow.tryEmit(
                SnackConf(key = "page-change-error", text = "Tried to change to same page!", duration = SnackConf.DURATION_ERROR)
            )
        }
    }



    private val log = TaggedLog("EditorState")
    private val persistedEditorSettings = EditorSettingCacheManager.getEditorSettings()

    var mode by mutableStateOf(persistedEditorSettings?.mode ?: Mode.Draw) // should save
    var pen by mutableStateOf(persistedEditorSettings?.pen ?: Pen.BALLPEN) // should save
    var eraser by mutableStateOf(persistedEditorSettings?.eraser ?: Eraser.PEN) // should save
    var isDrawing by mutableStateOf(true) // gives information if pen touch will be drawn or not
    // For debugging:
//    var isDrawing: Boolean
//        get() = _isDrawing
//        set(value) {
//            if (_isDrawing != value) {
//                Log.d(TAG, "isDrawing modified from ${_isDrawing} to $value")
//                logCallStack("isDrawing modification")
//                _isDrawing = value
//            }
//        }

    var isToolbarOpen by mutableStateOf(true) // Always open in Boox Day

    // OPTION C: Whether this canvas has an internal toolbar (for TouchHelper limit rect calculation)
    // Set to false for MemosCanvasArea/TasksCanvasArea where SharedToolbar is external
    var hasInternalToolbar by mutableStateOf(true)

    private val defaultPenSettings: Map<String, PenSetting> = mapOf(
        Pen.BALLPEN.penName to PenSetting(5f, Color.BLACK),
        Pen.REDBALLPEN.penName to PenSetting(5f, Color.RED),
        Pen.BLUEBALLPEN.penName to PenSetting(5f, Color.BLUE),
        Pen.GREENBALLPEN.penName to PenSetting(5f, Color.GREEN),
        Pen.PENCIL.penName to PenSetting(5f, Color.BLACK),
        Pen.BRUSH.penName to PenSetting(5f, Color.BLACK),
        Pen.MARKER.penName to PenSetting(40f, Color.LTGRAY),
        Pen.FOUNTAIN.penName to PenSetting(5f, Color.BLACK)
    )
    var penSettings by mutableStateOf(
        defaultPenSettings + (persistedEditorSettings?.penSettings ?: emptyMap())
    )

    val selectionState = SelectionState()
    val textBoxState = TextBoxState()

    init {
        // Wire textBoxState to pageView so canvas drawing can skip selected text annotations
        pageView.textBoxState = textBoxState
    }

    private var _clipboard by mutableStateOf(Clipboard.content)
    var clipboard
        get() = _clipboard
        set(value) {
            this._clipboard = value

            // The clipboard content must survive the EditorState, so we store a copy in
            // a singleton that lives outside of the EditorState
            Clipboard.content = value
        }

    val menuStates = MenuStates()
    fun closeAllMenus() = menuStates.closeAll()

    fun checkForSelectionsAndMenus() {
        val shouldBeDrawing = !menuStates.anyMenuOpen && !selectionState.isNonEmpty()
        if (isDrawing != shouldBeDrawing) {
            log.d("Drawing state should be: $shouldBeDrawing (menus open: ${menuStates.anyMenuOpen}, selection active: ${selectionState.isNonEmpty()})")
            isDrawing = shouldBeDrawing
        }
    }

    /**
     * Changes the current page to the one with the specified [id].
     *
     * @param id The unique identifier of the page to switch to.
     */
    fun changePage(id: String) {
        log.d("Changing page to $id, from $currentPageId")
        updateOpenedPage(id)
        closeAllMenus()
        selectionState.reset()
        textBoxState.reset()
    }
}

// if state is Move then applySelectionDisplace() will delete original strokes and images
enum class PlacementMode {
    Move,
    Paste
}

object Clipboard {
    var content: ClipboardContent? = null
}
