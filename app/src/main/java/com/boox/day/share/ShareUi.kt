package com.boox.day.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.Image as PageImage
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.data.ensureImagesFolder
import com.boox.day.editor.DrawCanvas
import com.boox.day.meetings.MeetingNotesUi
import com.boox.day.meetings.MeetingRecapSaver
import com.boox.day.meetings.ShareCard
import com.boox.day.proposals.ScheduleUi
import com.boox.day.ui.AiFeature
import com.boox.day.ui.MainScreenRequest
import com.boox.day.ui.MainScreenRequests
import com.boox.day.ui.ScreenCentre
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.ui.theme.LayoutProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Add to BooxDay: what came from another app waits here for the user's choice. */
object ShareUi {
    const val SOURCE = "shared"
    private const val MARGIN = MeetingRecapSaver.MARGIN
    private const val PREVIEW_SIDE = 800

    var item by mutableStateOf<SharedInbox.Item?>(null)
        internal set

    val isOpen: Boolean get() = item != null

    /** Where a save ended up, for the snack's Open. */
    data class Saved(val day: String, val pageId: String)

    /** How a share sits on pages: the picture first, the text under it when all of it fits. */
    internal data class Layout(val parts: List<String>, val textBelowPicture: Boolean)

    internal fun layout(text: String, pictureHeight: Int?, usable: Float, heightOf: (String) -> Float): Layout {
        val parts = if (text.isBlank()) emptyList() else MeetingRecapSaver.pagesOf(text.trim()) { heightOf(it) <= usable }
        val below = pictureHeight != null && parts.size == 1 && pictureHeight + MARGIN + heightOf(parts[0]) <= usable
        return Layout(parts, below)
    }

    /** Inside the page, never enlarged. */
    internal fun fit(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        val scale = minOf(1f, maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    private data class Stored(val uri: String, val width: Int, val height: Int)

    /** Page-sized copy in the app's images folder: a full photo on a page would fill the memory. */
    private fun storePicture(context: Context, name: String, maxWidth: Int, maxHeight: Int): Stored? {
        val decoded = SharedInbox.picture(SharedInbox.imageFile(context, name), maxOf(maxWidth, maxHeight)) ?: return null
        val (width, height) = fit(decoded.width, decoded.height, maxWidth, maxHeight)
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(Bitmap.createScaledBitmap(decoded, width, height, true), 0f, 0f, null)
        }
        val file = File(ensureImagesFolder(context), "shared-${UUID.randomUUID()}.jpg")
        file.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Stored(file.toUri().toString(), width, height)
    }

    /** Today's Memos: the open page when it's empty, else new pages. No AI, works offline. Null: nothing usable came. */
    suspend fun saveAsNote(context: Context, shared: SharedInbox.Item): Saved? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val db = AppDatabase.getDatabase(app)
        val pages = DayPageRepository(app)
        val boxes = TextAnnotationRepository(app)
        val width = SCREEN_WIDTH - 2 * MARGIN
        val usable = SCREEN_HEIGHT - 2 * MARGIN
        val picture = shared.image?.let { storePicture(app, it, width.toInt(), usable.toInt()) }
        val plan = layout(shared.text, picture?.height, usable) { MeetingRecapSaver.height(it, width) }
        if (picture == null && plan.parts.isEmpty()) return@withContext null

        val day = LocalDate.now().toString()
        val open = pages.getOrCreatePageForDate(day)
        val empty = !db.strokeDao().hasStrokesForPage(open) && !db.textAnnotationDao().hasTextAnnotationsForPage(open) && !db.ImageDao().hasImagesForPage(open)
        val openBefore = db.dayPageDao().getByDate(day)?.openPageId
        var usedOpen = false
        fun nextPage(): String = if (empty && !usedOpen) open.also { usedOpen = true } else pages.createNewPageForDate(day)

        var first: String? = null
        var pageId: String? = null
        var top = MARGIN
        picture?.let {
            pageId = nextPage().also { id -> first = id }
            val image = PageImage(x = MARGIN.toInt(), y = MARGIN.toInt(), width = it.width, height = it.height, uri = it.uri, pageId = pageId!!)
            db.ImageDao().create(image)
            if (PageDataManager.isPageLoaded(pageId!!)) PageDataManager.setImages(pageId!!, PageDataManager.getImages(pageId!!) + image)
            top += it.height + MARGIN
        }
        plan.parts.forEachIndexed { index, part ->
            val onPicturePage = index == 0 && plan.textBelowPicture
            val target = if (onPicturePage) pageId!! else nextPage()
            if (first == null) first = target
            val box = TextAnnotation(
                id = UUID.randomUUID().toString(),
                content = part,
                x = MARGIN,
                y = if (onPicturePage) top else MARGIN,
                width = width,
                height = MeetingRecapSaver.height(part, width),
                fontSize = MeetingRecapSaver.FONT_SIZE,
                fontWeight = 400,
                textColor = 0xFF000000.toInt(),
                backgroundColor = 0x00000000,
                textAlign = "left",
                padding = 0f,
                pageId = target,
                sourceType = SOURCE
            )
            boxes.insert(box)
            if (PageDataManager.isPageLoaded(target)) PageDataManager.addTextAnnotation(target, box)
        }
        pages.invalidatePageCache(day)
        openBefore?.let { pages.setOpenPageId(day, it) }
        if (usedOpen) {
            withContext(Dispatchers.Main) {
                PageDataManager.invalidatePreviewCache(open)
                DrawCanvas.forceUpdate.emit(null)
            }
        }
        Saved(day, first!!)
    }

    internal fun preview(context: Context, item: SharedInbox.Item): Bitmap? =
        item.image?.let { SharedInbox.picture(SharedInbox.imageFile(context, it), PREVIEW_SIDE) }
}

@Composable
fun ShareHost() {
    val context = LocalContext.current
    val revision by SharedInbox.revision.collectAsState()
    // Waits for the saved settings: a cold start runs with defaults until then
    LaunchedEffect(revision, GlobalAppSettings.loaded) {
        if (GlobalAppSettings.loaded && ShareUi.item == null) {
            ShareUi.item = withContext(Dispatchers.IO) { SharedInbox.next(context) }
        }
    }
    val item = ShareUi.item ?: return
    InkaTheme { key(item.id) { ShareFlow(item) } }
}

@Composable
private fun ShareFlow(item: SharedInbox.Item) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) { picture = withContext(Dispatchers.IO) { ShareUi.preview(context, item) } }
    // A failed or cancelled search brings the card back: the share stays until something was kept
    LaunchedEffect(finding, ScheduleUi.isOpen) { if (finding && !ScheduleUi.isOpen) finding = false }

    fun done() {
        ShareUi.item = null
        SharedInbox.remove(context, item)
    }

    suspend fun save(): ShareUi.Saved? = ShareUi.saveAsNote(context, item)

    if (finding) return
    val sheet = (LayoutProfiles.screenWidthDp * EInkTheme.ModernDialog.widthFraction).dp.coerceAtMost(700.dp)
    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = {}, properties = PopupProperties(focusable = true)) {
        ShareCard(
            preview = item.text,
            modifier = Modifier.width(sheet),
            picture = picture?.let { bitmap ->
                { Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Shared image", modifier = Modifier.fillMaxWidth().height(180.dp)) }
            },
            onCancel = { done() },
            onSave = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        val saved = save()
                        MainScreenRequests.request(MainScreenRequest.ReloadMemoPages)
                        done()
                        SnackState.globalSnackFlow.tryEmit(
                            if (saved == null) {
                                SnackConf(text = "This picture can't be opened.", duration = SnackConf.DURATION_ERROR)
                            } else {
                                SnackConf(
                                    text = "Saved to today's Memos",
                                    duration = SnackConf.DURATION_STANDARD,
                                    actions = listOf("Open" to { MeetingNotesUi.showPage(saved.day, saved.pageId) })
                                )
                            }
                        )
                    }
                }
            },
            onFind = {
                AiFeature.start(sendsCalendar = false) {
                    ScheduleUi.fromShared(
                        ScheduleUi.Shared(
                            text = item.text.take(SharedInbox.MAX_AI_TEXT),
                            picture = item.image?.let { SharedInbox.imageFile(context, it) },
                            saveNote = {
                                save()
                                MainScreenRequests.request(MainScreenRequest.ReloadMemoPages)
                            },
                            done = ::done
                        )
                    )
                    finding = true
                }
            }
        )
    }
}
