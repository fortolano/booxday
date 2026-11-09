package com.boox.day.ocr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.MemoryFile
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.boox.day.data.db.Stroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.util.Locale

/**
 * Handwriting read by the MyScript engine in the BOOX firmware (9 Nov 2026): the service BOOX Notes
 * reads with, in com.onyx.android.ksync. Onyx doesn't document it; the calls and their data follow
 * jdkruzr/aragonite (MIT), a Notable fork that reads with it. On other tablets or firmware, or in a
 * language BOOX can't read, [read] returns null and ML Kit reads instead.
 */
internal object BooxHandwritingReader {
    private const val TAG = "BooxHandwritingReader"
    private const val PACKAGE = "com.onyx.android.ksync"
    private const val SERVICE = "com.onyx.android.ksync.service.KHwrService"
    private const val SERVICE_INTERFACE = "com.onyx.android.sdk.hwr.service.IHWRService"
    private const val CALLBACK_INTERFACE = "com.onyx.android.sdk.hwr.service.HWROutputCallback"

    // IHWRService's calls in its order (init, compileRecognizeText, batchRecognize, …), all one-way
    private const val INIT = IBinder.FIRST_CALL_TRANSACTION
    private const val BATCH_RECOGNIZE = IBinder.FIRST_CALL_TRANSACTION + 2

    // HWRPointerProto's event types
    private const val DOWN = 0
    private const val MOVE = 1
    private const val UP = 2

    private const val CONNECT_MS = 2_000L
    private const val ANSWER_MS = 10_000L
    private const val PAUSE_MS = 30_000L
    private const val MARGIN = 10f

    /** One reading at a time: the service has one recognizer, set to one language */
    private val calls = Mutex()
    @Volatile private var service: IBinder? = null
    @Volatile private var connected = CompletableDeferred<IBinder?>()
    @Volatile private var bound = false
    /** This tablet has no BOOX handwriting service: ML Kit reads */
    @Volatile private var absent = false
    /** The service didn't answer (9 Nov 2026: once for 3 min, likely the tablet sleeping mid-call): ML Kit reads until then */
    @Volatile private var pausedUntil = 0L
    @Volatile private var readyLanguage: String? = null
    private val refusedLanguages = mutableSetOf<String>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            connected.complete(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            readyLanguage = null
            connected = CompletableDeferred()
        }

        override fun onNullBinding(name: ComponentName?) {
            absent = true
            connected.complete(null)
        }
    }

    private class NoAnswer(message: String) : Exception(message)

    internal data class PointerEvent(val x: Float, val y: Float, val t: Long, val pressure: Float, val type: Int)

    private class Answer(val result: ParcelFileDescriptor?, val activated: Boolean, val error: String?)

    /** The text BOOX reads in [strokes], in [languageTag] (ML Kit's tag); null when BOOX can't read it */
    suspend fun read(context: Context, strokes: List<Stroke>, languageTag: String): String? {
        if (absent || paused()) return null
        val events = pointerEvents(strokes)
        if (events.isEmpty()) return null
        val language = myScriptLanguage(languageTag)
        return calls.withLock {
            if (absent || paused() || language in refusedLanguages) return@withLock null
            try {
                val binder = connect(context) ?: return@withLock null
                val started = SystemClock.elapsedRealtime()
                val metrics = context.resources.displayMetrics
                val width = metrics.widthPixels.toFloat()
                val height = metrics.heightPixels.toFloat()
                if (readyLanguage != language && !start(binder, language, width, height)) {
                    refusedLanguages += language
                    return@withLock null
                }
                recognize(binder, request(language, width, height, events))?.also {
                    Log.i(TAG, "Read ${it.length} chars in ${SystemClock.elapsedRealtime() - started} ms ($language)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoAnswer) {
                pause(context, e.message)
                null
            } catch (e: Exception) {
                Log.w(TAG, "Not read: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    private fun paused() = SystemClock.elapsedRealtime() < pausedUntil

    /** Drops the connection, so BOOX is asked afresh once the pause is over */
    private fun pause(context: Context, why: String?) {
        pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS
        readyLanguage = null
        service = null
        connected = CompletableDeferred()
        if (bound) runCatching { context.applicationContext.unbindService(connection) }
        bound = false
        Log.w(TAG, "$why: ML Kit reads for ${PAUSE_MS / 1000} s, then BOOX again")
    }

    private suspend fun connect(context: Context): IBinder? {
        service?.let { return it }
        if (!bound) {
            val app = context.applicationContext
            bound = try {
                app.bindService(Intent().setComponent(ComponentName(PACKAGE, SERVICE)), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                false
            }
            if (!bound) {
                runCatching { app.unbindService(connection) }
                absent = true
                Log.i(TAG, "No BOOX handwriting service on this tablet: ML Kit reads")
                return null
            }
        }
        return withTimeoutOrNull(CONNECT_MS) { connected.await() } ?: if (absent) null else throw NoAnswer("BOOX handwriting service didn't connect")
    }

    /** Sets the service's recognizer to [language]; false when BOOX can't read it */
    private suspend fun start(binder: IBinder, language: String, width: Float, height: Float): Boolean {
        val answer = call(binder, INIT) { data, callback ->
            data.writeInt(1)  // HWRInputArgs follow
            // its HWRInputData, as Parcel.writeParcelable writes it: class name, then the fields
            data.writeString("com.onyx.android.sdk.hwr.bean.HWRInputData")
            data.writeString(language)
            data.writeString("Text")  // content type
            data.writeString("MS_ON_SCREEN")  // recognizer type
            data.writeFloat(width)
            data.writeFloat(height)
            data.writeFloat(0f)  // offset x
            data.writeFloat(0f)  // offset y
            data.writeByte(0)  // gestures
            data.writeByte(1)  // text
            data.writeByte(0)  // shapes
            data.writeByte(0)  // incremental
            data.writeString(null)  // no file
            data.writeString(null)  // no content
            data.writeInt(1)  // force re-init
            data.writeStrongBinder(callback)
        }
        readyLanguage = language.takeIf { answer.activated }
        if (!answer.activated) Log.i(TAG, "BOOX can't read $language: ML Kit reads it")
        return answer.activated
    }

    private suspend fun recognize(binder: IBinder, request: ByteArray): String? {
        val input = sharedFile(request)
        val answer = try {
            call(binder, BATCH_RECOGNIZE) { data, callback ->
                data.writeInt(1)  // the file follows
                input.writeToParcel(data, 0)
                data.writeStrongBinder(callback)
            }
        } finally {
            input.close()
        }
        if (!answer.error.isNullOrBlank()) {
            answer.result?.close()
            Log.w(TAG, "Not read: ${answer.error.take(200)}")
            return null
        }
        val json = answer.result?.use { FileInputStream(it.fileDescriptor).readBytes().toString(Charsets.UTF_8) } ?: return null
        return label(json)
    }

    /** Sends one call and waits for the service's HWROutputCallback; [NoAnswer] when it doesn't answer */
    private suspend fun call(binder: IBinder, code: Int, write: (Parcel, IBinder) -> Unit): Answer {
        val answer = CompletableDeferred<Answer>()
        val callback = object : Binder() {
            init { attachInterface(null, CALLBACK_INTERFACE) }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(CALLBACK_INTERFACE)
                answer.complete(if (data.readInt() != 0) readAnswer(data) else Answer(null, false, "no output"))
                reply?.writeNoException()
                return true
            }
        }
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE)
            write(data, callback)
            binder.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
        return withTimeoutOrNull(ANSWER_MS) { answer.await() } ?: throw NoAnswer("BOOX handwriting service didn't answer in ${ANSWER_MS / 1000} s")
    }

    /** HWROutputArgs: result file, recognizer activated, compiled, error, … */
    @Suppress("DEPRECATION")
    private fun readAnswer(data: Parcel): Answer {
        val result = data.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader)
        val activated = data.readByte() != 0.toByte()
        data.readByte()  // compiled
        return Answer(result, activated, data.readString())
    }

    /** The service reads the request from a MemoryFile, as Onyx's apps send it (hidden API: BooxDayApp lifts the limit) */
    private fun sharedFile(bytes: ByteArray): ParcelFileDescriptor {
        val memory = MemoryFile("hwr_input", bytes.size)
        try {
            memory.writeBytes(bytes, 0, 0, bytes.size)
            val descriptor = MemoryFile::class.java.getDeclaredMethod("getFileDescriptor")
                .apply { isAccessible = true }.invoke(memory) as FileDescriptor
            return ParcelFileDescriptor.dup(descriptor)
        } finally {
            memory.close()
        }
    }

    internal fun label(json: String): String? {
        val answer = JSONObject(json)
        if (answer.has("exception")) {
            Log.w(TAG, "Not read: ${answer.optJSONObject("exception")?.optString("message")}")
            return null
        }
        return (answer.optJSONObject("result") ?: answer).optString("label").trim().ifEmpty { null }
    }

    /** ML Kit's tag ("en-US", "uk") as MyScript names the language ("en_US", "uk_UA") */
    internal fun myScriptLanguage(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val country = locale.country.ifEmpty { HOME_COUNTRY[locale.language] ?: locale.language.uppercase(Locale.ROOT) }
        return "${locale.language}_$country"
    }

    private val HOME_COUNTRY = mapOf(
        "en" to "US", "uk" to "UA", "cs" to "CZ", "da" to "DK", "el" to "GR", "sv" to "SE",
        "ja" to "JP", "ko" to "KR", "zh" to "CN", "vi" to "VN", "hi" to "IN", "nb" to "NO"
    )

    /** The pen's path, moved next to the view's corner; a one-point stroke still lifts */
    internal fun pointerEvents(strokes: List<Stroke>): List<PointerEvent> {
        val written = strokes.filter { it.points.isNotEmpty() }
        if (written.isEmpty()) return emptyList()
        val left = written.minOf { s -> s.points.minOf { it.x } } - MARGIN
        val top = written.minOf { s -> s.points.minOf { it.y } } - MARGIN
        return written.flatMap { stroke ->
            // createdAt is its pen-up; dt counts from its first point
            val start = stroke.createdAt.time - (stroke.points.last().dt?.toLong() ?: 0L)
            val last = stroke.points.lastIndex
            val events = stroke.points.mapIndexed { i, p ->
                PointerEvent(
                    x = p.x - left,
                    y = p.y - top,
                    t = start + (p.dt?.toLong() ?: (i * 5L)),
                    pressure = ((p.pressure ?: (stroke.maxPressure / 2f)) / stroke.maxPressure).coerceIn(0f, 1f),
                    type = when (i) { 0 -> DOWN; last -> UP; else -> MOVE }
                )
            }
            if (last == 0) events + events[0].copy(type = UP) else events
        }
    }

    /** HWRInputProto: language, content and recognizer type, view size, text on, then the pointer events */
    internal fun request(language: String, width: Float, height: Float, events: List<PointerEvent>): ByteArray =
        ByteArrayOutputStream().apply {
            text(1, language)
            text(2, "Text")
            text(4, "MS_ON_SCREEN")
            float(5, width)
            float(6, height)
            tag(10, 0); varint(1)  // recognize text
            for (e in events) {
                bytes(15, ByteArrayOutputStream().apply {
                    float(1, e.x)
                    float(2, e.y)
                    tag(3, 0); varint((e.t shl 1) xor (e.t shr 63))  // sint64
                    float(4, e.pressure)
                    tag(5, 0); varint(0)  // pointer id
                    tag(6, 0); varint(e.type.toLong())
                    tag(7, 0); varint(0)  // pen
                }.toByteArray())
            }
        }.toByteArray()

    private fun ByteArrayOutputStream.tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    private fun ByteArrayOutputStream.varint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        write(v.toInt())
    }

    private fun ByteArrayOutputStream.text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    private fun ByteArrayOutputStream.bytes(field: Int, value: ByteArray) {
        tag(field, 2)
        varint(value.size.toLong())
        write(value, 0, value.size)
    }

    private fun ByteArrayOutputStream.float(field: Int, value: Float) {
        tag(field, 5)
        val bits = java.lang.Float.floatToIntBits(value)
        for (shift in 0..24 step 8) write((bits shr shift) and 0xFF)
    }
}
