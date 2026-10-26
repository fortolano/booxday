package com.boox.day.data

import android.content.Context
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import java.io.File

/**
 * Memo pages whose content was just cleared ("Clear All Memos Today", "Clear This Page"), once their
 * strokes, images and text boxes are deleted from the database (Oct 2026).
 *
 * Until then a cleared page that wasn't on screen kept its ink in memory: a loaded page is shown from
 * [PageDataManager] and never read again, so going to it showed the cleared ink until the app
 * restarted. Search kept finding the cleared text, because nothing told the index.
 */
object ClearedPages {

    /**
     * Pages not on screen: drops what is kept of them besides the database rows (loaded strokes,
     * images and text boxes, the cached page image, an image still waiting to be saved, the saved
     * images). The page on screen is cleared by PageView.clearAllContent, which saves its blank image.
     */
    fun forgetLoadedContent(context: Context, pageIds: Collection<String>) {
        for (pageId in pageIds) {
            PageDataManager.cancelPendingPersist(pageId)
            PageDataManager.clearPageData(pageId)
            PageDataManager.invalidatePreviewCache(pageId)
            deleteSavedImages(context, pageId)
        }
    }

    /**
     * Search forgets the pages' text. The indexer then finds them unread, sees they are empty and
     * marks them EMPTY without an AI read (MemoBitmapSource skips a page with no strokes or text boxes).
     */
    suspend fun forgetSearchText(context: Context, date: String, pageIds: Collection<String>) {
        val ocr = UnifiedOcrRepository(context)
        pageIds.forEach { ocr.deleteOcrForPage(OcrSource.MEMO, date, it) }
    }

    private fun deleteSavedImages(context: Context, pageId: String) {
        ensurePreviewsFullFolder(context).listFiles { file -> file.name.startsWith(pageId) }?.forEach { it.delete() }
        File(context.filesDir, "pages/previews/thumbs/$pageId").delete()
    }
}
