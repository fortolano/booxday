package com.boox.day.sleepscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.onyx.android.sdk.api.device.screensaver.ScreenResourceManager
import com.onyx.android.sdk.api.device.screensaver.ScreenUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug builds only (About → "Debug: set test sleep screen"): does the BOOX firmware take a sleep
 * image from a third-party app through the Onyx SDK, and from which folder? Plan step 1a for
 * "Today on the sleep screen" (Oct 2026). Draws a plain test image at the panel's size, saves
 * it to [where], hands it to ScreenResourceManager.setScreensaver and logs every step (tag
 * SleepScreenSpike). Then: put the tablet to sleep and look.
 */
object SleepScreenSpike {
    private const val TAG = "SleepScreenSpike"

    enum class Where { PICTURES, APP_FILES }

    fun run(context: Context, where: Where) {
        try {
            val size = ScreenUtils.getScreenSize(context)
            val width = minOf(size[0], size[1])
            val height = maxOf(size[0], size[1])
            Log.i(TAG, "Screen ${size[0]}x${size[1]}, drawing portrait ${width}x$height")

            val dir = when (where) {
                Where.PICTURES -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BooxDay")
                Where.APP_FILES -> File(context.getExternalFilesDir(null), "sleep")
            }
            dir.mkdirs()
            // A new name each time: shows whether the firmware keeps a list or one image
            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
            val file = File(dir, "sleep-test-$stamp.png")
            val bitmap = drawTestImage(width, height, "${where.name} $stamp")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Log.i(TAG, "Saved ${file.absolutePath} (${file.length()} bytes, readable by others: ${file.canRead()})")

            val accepted = ScreenResourceManager.setScreensaver(context, file.absolutePath, true)
            Log.i(TAG, "setScreensaver returned $accepted")
            Toast.makeText(context, "setScreensaver: $accepted. Now sleep the tablet.", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.e(TAG, "Spike failed", t)
            Toast.makeText(context, "Spike failed: ${t.javaClass.simpleName}: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawTestImage(width: Int, height: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val frame = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 12f }
        canvas.drawRect(24f, 24f, width - 24f, height - 24f, frame)
        // An arrow in the top-left corner shows how the firmware turns the image
        val fill = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawRect(60f, 60f, 260f, 90f, fill)
        canvas.drawRect(60f, 60f, 90f, 260f, fill)
        val text = Paint().apply {
            color = Color.BLACK; isAntiAlias = true; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD; textSize = width / 12f
        }
        canvas.drawText("BooxDay", width / 2f, height * 0.42f, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = width / 22f
        canvas.drawText("Sleep screen test", width / 2f, height * 0.50f, text)
        canvas.drawText(label, width / 2f, height * 0.56f, text)
        canvas.drawText("Top left corner ↖", width / 2f, height * 0.62f, text)
        return bitmap
    }
}
