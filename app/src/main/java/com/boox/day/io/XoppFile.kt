package com.boox.day.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import com.boox.day.BuildConfig
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.db.BookRepository
import com.boox.day.data.db.Image
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.Stroke
import com.boox.day.editor.utils.Pen
import com.boox.day.ui.showHint
import com.boox.day.utils.ensureNotMainThread
import com.onyx.android.sdk.api.device.epd.EpdController
import com.boox.day.utils.TaggedLog
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// I do not know what pressureFactor should be, I just guest it.
// it's used to get strokes look relatively good in xournal++
private const val PRESSURE_FACTOR = 0.5f

// https://github.com/xournalpp/xournalpp/issues/2124
class XoppFile(
    private val context: Context,
    private val pageRepo: PageRepository = PageRepository(context),
) {
    private val log = TaggedLog("XoppFile")
    private val scaleFactor = A4_WIDTH.toFloat() / SCREEN_WIDTH
    private val maxPressure: Float = try { EpdController.getMaxTouchPressure() } catch (e: Exception) { 4095f }

    fun writeToXoppStream(target: ExportTarget, output: OutputStream) {
        // Build a temporary plain-XML file using existing writePage(), then gzip it into 'output'
        val tmp = File(
            context.cacheDir, when (target) {
                is ExportTarget.Book -> "booxday_xopp_book.xml"
                is ExportTarget.Page -> "booxday_xopp_page.xml"
                is ExportTarget.DayPages, is ExportTarget.Days -> throw IOException("XOPP export not supported for day pages")
            }
        )

        BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8)).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.write("<xournal creator=\"BooxDay ${BuildConfig.VERSION_NAME}\" version=\"0.4\">\n")
            when (target) {
                is ExportTarget.Book -> {
                    val book = BookRepository(context).getById(target.bookId)
                        ?: throw IOException("Book not found: ${target.bookId}")
                    book.pageIds.forEach { pageId ->
                        writePage(pageId, writer)
                    }
                }

                is ExportTarget.Page -> {
                    writePage(target.pageId, writer)
                }

                is ExportTarget.DayPages, is ExportTarget.Days -> throw IOException("XOPP export not supported for day pages")
            }
            writer.write("</xournal>\n")
        }

        GzipCompressorOutputStream(BufferedOutputStream(output)).use { gz ->
            tmp.inputStream().use { it.copyTo(gz) }
        }
        tmp.delete()
    }


    /**
     * Writes a single page's XML data to the output stream.
     *
     * This method retrieves the strokes and images for the given page
     * and writes them to the provided BufferedWriter.
     *
     * @param pageId The ID of the page to process.
     * @param writer The BufferedWriter to write XML data to.
     */
    private fun writePage(pageId: String, writer: BufferedWriter) {
        val (_, strokes) = pageRepo.getWithStrokeById(pageId)
        val (_, images) = pageRepo.getWithImageById(pageId)

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        val root = doc.createElement("page")
        val strokeHeight = if (strokes.isEmpty()) 0 else strokes.maxOf(Stroke::bottom).toInt() + 50
        val height = strokeHeight.coerceAtLeast(SCREEN_HEIGHT) * scaleFactor

        root.setAttribute("width", A4_WIDTH.toString())
        root.setAttribute("height", height.toString())
        doc.appendChild(root)

        val bcgElement = doc.createElement("background")
        bcgElement.setAttribute("type", "solid")
        bcgElement.setAttribute("color", "#ffffffff")
        bcgElement.setAttribute("style", "plain")
        root.appendChild(bcgElement)


        val layer = doc.createElement("layer")
        root.appendChild(layer)



        for (stroke in strokes) {
            // skip the small strokes, to avoid error: Wrong count of points (2)
            if (stroke.points.size < 3)
                continue
            val strokeElement = doc.createElement("stroke")
            strokeElement.setAttribute("tool", stroke.pen.toString())
            strokeElement.setAttribute("color", getColorName(Color(stroke.color)))
            val widthValues = mutableListOf(stroke.size * scaleFactor)
            if (stroke.pen == Pen.FOUNTAIN || stroke.pen == Pen.BRUSH || stroke.pen == Pen.PENCIL) widthValues += stroke.points.map {
                it.pressure?.div(stroke.maxPressure * PRESSURE_FACTOR) ?: 1f
            }
            val widthString = widthValues.joinToString(" ")

            strokeElement.setAttribute("width", widthString)

            val pointsString =
                stroke.points.joinToString(" ") { "${it.x * scaleFactor} ${it.y * scaleFactor}" }
            strokeElement.textContent = pointsString
            layer.appendChild(strokeElement)
        }

        for (image in images) {
            val imgElement = doc.createElement("image")

            val left = image.x * scaleFactor
            val top = image.y * scaleFactor
            val right = (image.x + image.width) * scaleFactor
            val bottom = (image.y + image.height) * scaleFactor

            imgElement.setAttribute("left", left.toString())
            imgElement.setAttribute("top", top.toString())
            imgElement.setAttribute("right", right.toString())
            imgElement.setAttribute("bottom", bottom.toString())

            image.uri?.let { uri ->
                imgElement.setAttribute("filename", uri)
                imgElement.textContent = convertImageToBase64(image.uri, context)
            }
            if (imgElement.textContent.isNotBlank()) layer.appendChild(imgElement)
            else showHint("Image cannot be loaded.")
        }

        val xmlString = convertXmlToString(doc)
        writer.write(xmlString)
    }


    /**
     * Opens a file and converts it to a base64 string.
     */
    private fun convertImageToBase64(uri: String, context: Context): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri.toUri())
            val bytes = inputStream?.readBytes() ?: return ""
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: SecurityException) {
            log.e("convertImageToBase64:" + "Permission denied: ${e.message}")
            ""
        } catch (e: FileNotFoundException) {
            log.e("convertImageToBase64:" + "File not found: ${e.message}")
            ""
        } catch (e: IOException) {
            log.e("convertImageToBase64:" + "I/O error: ${e.message}")
            ""
        }
    }


    /**
     * Converts an XML Document to a formatted string without the XML declaration.
     *
     * This is used to convert an individual page's XML structure into a string
     * before writing it to the output file. The XML declaration is removed to
     * prevent duplicate headers when merging pages.
     *
     * @param document The XML Document to convert.
     * @return The formatted XML string without the XML declaration.
     */
    private fun convertXmlToString(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") // ❗ Omit XML header
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString().trim() // Remove extra spaces or newlines
    }

    /**
     * Maps a Compose Color to an Xournal++ color name.
     *
     * @param color The Compose Color object.
     * @return The corresponding color name as a string.
     */
    private fun getColorName(color: Color): String {
        return when (color) {
            Color.Companion.Black -> "black"
            Color.Companion.Blue -> "blue"
            Color.Companion.Red -> "red"
            Color.Companion.Green -> "green"
            Color.Companion.Magenta -> "magenta"
            Color.Companion.Yellow -> "yellow"
            Color.Companion.DarkGray, Color.Companion.Gray -> "gray"
            else -> {
                val argb = color.toArgb()
                // Convert ARGB (Android default) → RGBA
                String.format(
                    "#%02X%02X%02X%02X",
                    (argb shr 16) and 0xFF, // Red
                    (argb shr 8) and 0xFF,  // Green
                    (argb) and 0xFF,        // Blue
                    (argb shr 24) and 0xFF  // Alpha
                )
            }
        }
    }
}