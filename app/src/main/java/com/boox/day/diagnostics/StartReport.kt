package com.boox.day.diagnostics

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Display
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.boox.day.AppDensity
import com.boox.day.BuildConfig
import com.boox.day.compat.OnyxCompat
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.utils.refreshScreen
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.device.Device
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The start report (Nov 2026, issues #1 and #8: a white screen after the splash on a Tab Ultra C and
 * a Nova Air 2, tablets the owner doesn't have). Only in a build made with `-PstartReport`
 * ([BuildConfig.START_REPORT]); in every other build each function returns at once.
 *
 * It writes `Documents/BooxDay/booxday-start-report.txt` (and a copy in the app's own files folder):
 * the tablet, the screen sizes and density before and after the app's changes, each start-up stage
 * with its time, and a few pictures of the main screen taken as numbers: where the layout has parts,
 * and how much of each area of the app's own picture is not white. No events, tasks, handwriting,
 * account names or keys: no text of the screen is read, only counts and sizes.
 *
 * It also runs two timed checks the reporter can watch: about 20 s after the main screen appears
 * the pen layer goes off for 10 s with one full panel refresh, and at about 40 s the drawing
 * surfaces are hidden for 8 s. Whether the calendar shows up at either moment tells causes apart.
 */
object StartReport {
    val on: Boolean get() = BuildConfig.START_REPORT

    private const val FILE_NAME = "booxday-start-report.txt"
    private const val FOLDER = "Documents/BooxDay/"
    private const val MAX_MARKS = 400
    private const val MAX_PICTURES = 12
    private const val GRID_COLS = 6
    private const val GRID_ROWS = 8

    private val marks = ArrayList<String>()
    private val pictures = ArrayList<String>()
    private val main = Handler(Looper.getMainLooper())
    private val writer by lazy { Executors.newSingleThreadExecutor() }
    private var app: Context? = null
    private var activity = WeakReference<Activity>(null)
    private var activityCount = 0
    private var splashGone = false
    private var mainComposed = false
    private var mainScheduled = false
    private var probeDone = false
    private var fileUri: Uri? = null
    private var lastSaved = "not saved yet"

    private fun sinceStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    /** One start-up stage, with its time since the process started. */
    fun mark(text: String) {
        if (!on) return
        val line = "%7d ms  %s%s".format(
            Locale.US, sinceStart(), text,
            if (Looper.myLooper() == Looper.getMainLooper()) "" else "  [${Thread.currentThread().name}]"
        )
        synchronized(marks) { if (marks.size < MAX_MARKS) marks += line }
    }

    fun mark(text: String, error: Throwable) = mark("$text: ${error.javaClass.name}: ${error.message}")

    fun begin(context: Context) {
        if (!on) return
        app = context.applicationContext
        mark("App.onCreate")
    }

    /** The screen as the system hands it to the activity, before the app sets its own density. */
    fun baseContext(resources: Resources) {
        if (!on) return
        mark("Activity.attachBaseContext, as given: ${describe(resources)}")
    }

    fun activityCreated(a: Activity) {
        if (!on) return
        activity = WeakReference(a)
        activityCount++
        splashGone = false
        mainComposed = false
        mainScheduled = false
        main.removeCallbacksAndMessages(null)
        mark("Activity.onCreate #$activityCount: ${describe(a.resources)}")
        after(2_000) { save() }
        after(30_000) { if (!mainScheduled) picture("30 s after the activity started, main screen not reached") }
    }

    fun splashRemoved() {
        if (!on) return
        mark("Router: splash removed")
        splashGone = true
        mainShown()
    }

    fun mainScreenComposed() {
        if (!on || mainComposed) return
        mark("Router: main screen composed")
        mainComposed = true
        mainShown()
    }

    private fun mainShown() {
        if (!splashGone || !mainComposed || mainScheduled) return
        mainScheduled = true
        after(2_000) { picture("2 s after the main screen appeared") }
        after(8_000) { picture("8 s after the main screen appeared") }
        if (!probeDone) after(20_000) { probe() }
    }

    /** Pen layer off, one full panel refresh, a picture, pen layer back: does the calendar show then? */
    private fun probe() {
        probeDone = true
        val modalWas = DrawCanvas.mainScreenModalVisible.value
        mark("Check: pen layer off (a window was open: $modalWas)")
        DrawCanvas.mainScreenModalVisible.value = true
        after(1_000) {
            activity.get()?.window?.decorView?.invalidate()
            refreshScreen()
            mark("Check: full panel refresh asked")
        }
        after(3_000) { picture("pen layer off, after the full refresh") }
        after(10_000) {
            DrawCanvas.mainScreenModalVisible.value = modalWas
            mark("Check: pen layer back on")
        }
        after(13_000) { picture("3 s after the pen layer came back") }
        // Second check, 20 s after the first: the drawing surfaces hidden for 8 s
        after(20_000) {
            val canvases = ArrayList<View>()
            activity.get()?.window?.decorView?.let { collectSurfaces(it, canvases) }
            mark("Check: ${canvases.size} drawing surface(s) hidden")
            canvases.forEach { it.visibility = View.INVISIBLE }
            after(3_000) { picture("drawing surfaces hidden") }
            after(8_000) {
                canvases.forEach { it.visibility = View.VISIBLE }
                mark("Check: drawing surfaces shown again")
            }
            after(11_000) { picture("3 s after the drawing surfaces came back") }
        }
    }

    private fun collectSurfaces(view: View, into: MutableList<View>) {
        if (view is SurfaceView && view.visibility == View.VISIBLE) into += view
        if (view is ViewGroup) for (i in 0 until view.childCount) collectSurfaces(view.getChildAt(i), into)
    }

    fun activityStopped() {
        if (!on) return
        mark("Activity.onStop")
        save()
    }

    private fun after(ms: Long, block: () -> Unit) {
        main.postDelayed({ try { block() } catch (t: Throwable) { mark("Report step failed", t) } }, ms)
    }

    // ---- Pictures of the screen, as numbers --------------------------------------------------

    private fun picture(label: String) {
        val a = activity.get() ?: return
        val text = StringBuilder()
        text.appendLine("--- $label (${sinceStart()} ms) ---")
        text.appendLine("Activity resources: ${describe(a.resources)}")
        try {
            val roots = WindowInspector.getGlobalWindowViews()
            text.appendLine("Windows of the app: ${roots.size}")
            roots.forEachIndexed { index, root ->
                text.appendLine("Window ${index + 1}: ${root.javaClass.simpleName} ${place(root)}" +
                    " focus=${root.hasWindowFocus()}" + if (root === a.window.decorView) " (the activity)" else "")
                viewTree(root, 1, text, IntArray(1))
            }
        } catch (t: Throwable) {
            text.appendLine("Windows: failed, ${t.javaClass.simpleName}: ${t.message}")
        }
        insets(a, text)
        text.appendLine(onyxState())

        val decor = a.window.decorView
        val width = decor.width / 8
        val height = decor.height / 8
        if (width <= 0 || height <= 0 || !decor.isAttachedToWindow) {
            text.appendLine("App picture: the window has no size yet")
            finishPicture(text)
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(a.window, bitmap, { result ->
                text.appendLine("App picture (what the app itself drew, canvas left out), result $result.")
                text.appendLine("Percent of each area that is not white (see-through in brackets):")
                if (result == PixelCopy.SUCCESS) text.append(grid(bitmap))
                bitmap.recycle()
                finishPicture(text)
            }, main)
        } catch (t: Throwable) {
            text.appendLine("App picture: failed, ${t.javaClass.simpleName}: ${t.message}")
            finishPicture(text)
        }
    }

    private fun finishPicture(text: StringBuilder) {
        synchronized(pictures) { if (pictures.size < MAX_PICTURES) pictures += text.toString() }
        save()
    }

    /** Class names, places and sizes only: no text, no content descriptions. */
    private fun viewTree(view: View, depth: Int, out: StringBuilder, lines: IntArray) {
        if (lines[0] >= 60) return
        val isCompose = view is ViewRootForTest
        val isSurface = view is SurfaceView
        val group = view as? ViewGroup
        // Plain containers with one child add nothing: only branches, Compose roots, surfaces and leaves
        if (isCompose || isSurface || group == null || group.childCount != 1 || depth <= 2) {
            lines[0]++
            out.append("  ".repeat(depth)).append(view.javaClass.simpleName).append(' ').append(place(view))
            if (view.visibility != View.VISIBLE) out.append(" visibility=").append(view.visibility)
            if (view.alpha != 1f) out.append(" alpha=").append(view.alpha)
            if (isSurface) {
                val surface = view as SurfaceView
                out.append(" surfaceValid=").append(surface.holder.surface?.isValid)
                if (surface is DrawCanvas) out.append(if (surface.isMemosCanvas) " Memos" else " Tasks")
            }
            out.appendLine()
            if (isCompose) layoutGrid(view as ViewRootForTest, view, depth + 1, out)
            if (isSurface) surfacePicture(view as SurfaceView)
        }
        if (group != null) for (i in 0 until group.childCount) viewTree(group.getChildAt(i), depth + 1, out, lines)
    }

    private fun place(view: View): String {
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        return "${view.width}x${view.height} at ${at[0]},${at[1]}"
    }

    /** Where the Compose layout has parts: how many, and how many sit in each area of its window. */
    @SuppressLint("VisibleForTests")
    private fun layoutGrid(root: ViewRootForTest, view: View, depth: Int, out: StringBuilder) {
        val pad = "  ".repeat(depth)
        try {
            val cells = IntArray(GRID_COLS * GRID_ROWS)
            var total = 0
            var noSize = 0
            var withText = 0
            fun walk(node: SemanticsNode) {
                total++
                if (node.config.contains(SemanticsProperties.Text)) withText++
                val box = node.boundsInRoot
                if (box.width <= 0f || box.height <= 0f || view.width <= 0 || view.height <= 0) {
                    noSize++
                } else {
                    val col = (box.center.x / view.width * GRID_COLS).toInt().coerceIn(0, GRID_COLS - 1)
                    val row = (box.center.y / view.height * GRID_ROWS).toInt().coerceIn(0, GRID_ROWS - 1)
                    cells[row * GRID_COLS + col]++
                }
                node.children.forEach { walk(it) }
            }
            walk(root.semanticsOwner.unmergedRootSemanticsNode)
            out.append(pad).appendLine("Layout parts: $total ($withText with text, $noSize with no size). Parts per area:")
            for (row in 0 until GRID_ROWS) {
                out.append(pad)
                for (col in 0 until GRID_COLS) out.append("%5d".format(Locale.US, cells[row * GRID_COLS + col]))
                out.appendLine()
            }
        } catch (t: Throwable) {
            out.append(pad).appendLine("Layout parts: failed, ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** How much of a drawing surface is not white; arrives a moment later as its own stage line. */
    private fun surfacePicture(surface: SurfaceView) {
        try {
            if (surface.width < 16 || surface.height < 16 || surface.holder.surface?.isValid != true) return
            val bitmap = Bitmap.createBitmap(surface.width / 8, surface.height / 8, Bitmap.Config.ARGB_8888)
            val name = if (surface is DrawCanvas) (if (surface.isMemosCanvas) "Memos" else "Tasks") else "other"
            PixelCopy.request(surface, bitmap, { result ->
                val ink = if (result == PixelCopy.SUCCESS) "${notWhite(bitmap, 0, 0, bitmap.width, bitmap.height).first}% not white" else "no picture"
                mark("Surface picture ($name ${surface.width}x${surface.height}): result $result, $ink")
                bitmap.recycle()
            }, main)
        } catch (t: Throwable) {
            mark("Surface picture failed", t)
        }
    }

    private fun grid(bitmap: Bitmap): String {
        val out = StringBuilder()
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val (ink, clear) = notWhite(
                    bitmap,
                    bitmap.width * col / GRID_COLS, bitmap.height * row / GRID_ROWS,
                    bitmap.width * (col + 1) / GRID_COLS, bitmap.height * (row + 1) / GRID_ROWS
                )
                out.append("%4d(%3d)".format(Locale.US, ink, clear))
            }
            out.appendLine()
        }
        return out.toString()
    }

    /** Percent of the area that is darker than near-white, and percent that is see-through. */
    private fun notWhite(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Pair<Int, Int> {
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return 0 to 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)
        var ink = 0
        var clear = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) { clear++; continue }
            val light = (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10
            if (light < 200) ink++
        }
        return (ink * 100 / pixels.size) to (clear * 100 / pixels.size)
    }

    private fun insets(a: Activity, out: StringBuilder) {
        try {
            val decor = a.window.decorView
            val insets = decor.rootWindowInsets
            if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                out.appendLine("System bars: left ${bars.left}, top ${bars.top}, right ${bars.right}, bottom ${bars.bottom}" +
                    "; status shown=${insets.isVisible(WindowInsets.Type.statusBars())}, navigation shown=${insets.isVisible(WindowInsets.Type.navigationBars())}")
                val bounds = a.windowManager.currentWindowMetrics.bounds
                val largest = a.windowManager.maximumWindowMetrics.bounds
                out.appendLine("Window bounds: ${bounds.width()}x${bounds.height()} at ${bounds.left},${bounds.top}; largest ${largest.width()}x${largest.height()}")
            }
            val attrs = a.window.attributes
            out.appendLine("Window: format=${attrs.format}, flags=0x${Integer.toHexString(attrs.flags)}, " +
                "hardware drawing=${decor.isHardwareAccelerated}, rotation=${decor.display?.rotation}")
        } catch (t: Throwable) {
            out.appendLine("Window: failed, ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ---- The tablet --------------------------------------------------------------------------

    private fun describe(resources: Resources): String {
        val m = resources.displayMetrics
        val c = resources.configuration
        return "${m.widthPixels}x${m.heightPixels} px, ${m.densityDpi} dpi (config ${c.densityDpi}), " +
            "${c.screenWidthDp}x${c.screenHeightDp} dp, font ${c.fontScale}, orientation ${c.orientation}"
    }

    private fun display(context: Context): String {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        return "panel ${mode.physicalWidth}x${mode.physicalHeight}, turned ${display.rotation * 90} degrees, " +
            "activity asks for orientation ${activity.get()?.requestedOrientation}"
    }

    private fun attempt(block: () -> Any?): String = try {
        block().toString()
    } catch (t: Throwable) {
        "failed (${t.javaClass.simpleName}: ${t.message})"
    }

    private fun onyxState(): String = "BOOX display: fast mode=${attempt { EpdController.isInFastMode() }}, " +
        "app refresh mode=${attempt { EpdController.getAppScopeRefreshMode() }}, " +
        "system update mode=${attempt { EpdController.getSystemDefaultUpdateMode() }}, " +
        "pen state=${attempt { EpdController.getPenState() }}, regal=${attempt { EpdController.isRegalEnabled() }}"

    private fun header(context: Context): String = buildString {
        appendLine("BooxDay start report")
        appendLine("It holds no events, tasks, handwriting, account names or keys.")
        appendLine()
        appendLine("Written: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE}, start report build)")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (device ${Build.DEVICE}, product ${Build.PRODUCT}, board ${Build.BOARD}, hardware ${Build.HARDWARE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), security patch ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Firmware: ${Build.DISPLAY}")
        appendLine("Build: ${Build.FINGERPRINT}")
        appendLine("BOOX device class: ${attempt { Device.currentDevice().javaClass.name }}")
        appendLine("Pen: ${OnyxCompat.penBackend}; tablet has a BOOX pen: ${attempt { !OnyxCompat.usesAndroidPenInput(context) }}")
        appendLine("Hardware display controller: ${attempt { OnyxCompat.hasHardwareDisplayController }}")
        appendLine("Panel: ${attempt { EpdController.getEpdWidth() }} x ${attempt { EpdController.getEpdHeight() }}")
        appendLine(onyxState())
        appendLine("System screen: ${describe(Resources.getSystem())}")
        appendLine("Display: ${attempt { display(context) }}")
        appendLine("App resources: ${describe(context.resources)}")
        appendLine("App density: ${attempt { AppDensity.dpi }} dpi")
        appendLine("Memory: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB for the app")
        appendLine("Activity started $activityCount time(s) in this run")
        appendLine("Saved: $lastSaved")
    }

    /** The saved crash report without its log lines, when the last run crashed. */
    private fun lastCrash(context: Context): String? = try {
        CrashReporter.pending(context)?.text?.substringBefore("Recent app log")?.trimEnd()
    } catch (_: Throwable) {
        null
    }

    /** This run's own warnings and errors, email addresses masked (the crash report's rule). */
    private fun warnings(): List<String> {
        val lines = ArrayList<String>()
        var logcat: java.lang.Process? = null
        val reader = Thread {
            try {
                logcat = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}", "*:W")
                    .redirectErrorStream(true).start()
                logcat?.inputStream?.bufferedReader()?.useLines { all ->
                    all.forEach { synchronized(lines) { lines += it } }
                }
            } catch (_: Throwable) {
            }
        }
        return try {
            reader.isDaemon = true
            reader.start()
            reader.join(1_500)
            logcat?.destroy()
            synchronized(lines) { collapseRepeats(lines).takeLast(120).map { CrashReporter.maskEmails(it.take(300)) } }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** A line repeated in a row is kept once, with its count ("time" format: 19 characters of date). */
    private fun collapseRepeats(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        var last = ""
        var repeats = 0
        fun flush() { if (repeats > 0) out += "    (the line above came $repeats more time(s))" }
        for (line in lines) {
            val body = line.drop(19)
            if (body == last) { repeats++; continue }
            flush()
            repeats = 0
            last = body
            out += line
        }
        flush()
        return out
    }

    // ---- The file ----------------------------------------------------------------------------

    private fun save() {
        val context = app ?: return
        writer.execute {
            try {
                val text = buildString {
                    append(header(context))
                    appendLine()
                    appendLine("=== Start-up stages (time since the app's process started) ===")
                    synchronized(marks) { marks.forEach { appendLine(it) } }
                    synchronized(pictures) {
                        pictures.forEach {
                            appendLine()
                            append(it)
                        }
                    }
                    lastCrash(context)?.let {
                        appendLine()
                        appendLine("=== Crash saved by an earlier run ===")
                        appendLine(it)
                    }
                    val log = warnings()
                    appendLine()
                    appendLine("=== The app's warnings and errors in this run (${log.size} lines) ===")
                    log.forEach { appendLine(it) }
                }
                val own = try {
                    File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME).apply { writeText(text) }.path
                } catch (t: Throwable) {
                    "no copy (${t.message})"
                }
                lastSaved = try {
                    saveShared(context, text)
                    "$FOLDER$FILE_NAME, copy $own"
                } catch (t: Throwable) {
                    "only $own; $FOLDER failed: ${t.javaClass.simpleName}: ${t.message}"
                }
            } catch (t: Throwable) {
                mark("Report not saved", t)
            }
        }
    }

    /** Documents/BooxDay through the media store: needs no file permission, and file managers show it. */
    private fun saveShared(context: Context, text: String) {
        val resolver = context.contentResolver
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        fun write(uri: Uri) {
            (resolver.openOutputStream(uri, "wt") ?: error("no stream")).use { it.write(text.toByteArray()) }
        }
        // This run's file, else the file an earlier run of the report left
        val known = fileUri ?: resolver.query(
            files, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(FOLDER, FILE_NAME), null
        )?.use { if (it.moveToFirst()) Uri.withAppendedPath(files, it.getLong(0).toString()) else null }
        if (known != null) {
            try {
                write(known)
                fileUri = known
                return
            } catch (_: Throwable) {
                // The file was deleted or belongs to an older install: a new one below
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val created = resolver.insert(files, values) ?: error("the media store gave no file")
        write(created)
        fileUri = created
    }
}
