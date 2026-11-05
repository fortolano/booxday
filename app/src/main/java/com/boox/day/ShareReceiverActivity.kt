package com.boox.day

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.lifecycleScope
import com.boox.day.share.SharedInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Share to BooxDay (Nov 2026): a light screen with no canvas, as OpenRouterConnectActivity. It copies
 * what came at once, because the right to read a shared file is temporary, then brings the running app
 * forward: MainActivity never starts a second canvas for this.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val kept = withContext(Dispatchers.IO) {
                try {
                    receive(intent)
                } catch (e: Exception) {
                    Log.w("ShareReceiver", "Not kept: ${e.javaClass.simpleName}")
                    false
                }
            }
            if (!kept) Toast.makeText(applicationContext, "BooxDay couldn't read this. Pictures can be up to 20 MB.", Toast.LENGTH_LONG).show()
            startActivity(backToApp())
            finish()
        }
    }

    /** False when nothing usable came. */
    private fun receive(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND) return false
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val text = listOf(subject, body).filter { it.isNotBlank() }.distinct().joinToString("\n")
        val stream = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val readable = stream?.takeIf { SharedInbox.accepts(intent.type, it.scheme) }
        val image = readable?.takeIf { intent.type?.startsWith("image/") == true }?.let { copy(it) }
        // Some apps send text as a file
        val words = text.ifBlank { readable?.takeIf { intent.type == "text/plain" }?.let { readText(it) }.orEmpty() }
        if (words.isBlank() && image == null) return false
        SharedInbox.add(this, words, image)
        return true
    }

    private fun readText(uri: Uri): String? = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        val buffer = CharArray(SharedInbox.MAX_TEXT)
        var total = 0
        while (total < buffer.size) {
            val read = reader.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        String(buffer, 0, total)
    }

    /** Only content:// is read; the copy stops at the size limit. */
    private fun copy(uri: Uri): String? {
        val name = "${UUID.randomUUID()}.image"
        val target = SharedInbox.imageFile(this, name)
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > SharedInbox.MAX_IMAGE_BYTES) {
                        target.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: return null
        return name
    }

    /** The running app, brought forward (its existing MainActivity, not a new one). */
    @OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    private fun backToApp(): Intent =
        Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
}
