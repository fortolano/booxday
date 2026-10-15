package com.boox.day.editor.drawing

import android.content.Context
import android.text.Spanned
import android.util.Log
import io.noties.markwon.Markwon

/**
 * Helper singleton for rendering markdown text.
 * Must be initialized with Context before use.
 * Used by TextBoxRenderer to render markdown content from AI conversion.
 */
object MarkdownHelper {

    private const val TAG = "MarkdownHelper"
    private var markwon: Markwon? = null

    /**
     * Initialize with application context.
     * Call this in Application.onCreate() or MainActivity.onCreate()
     */
    fun init(context: Context) {
        if (markwon == null) {
            markwon = Markwon.create(context.applicationContext)
            Log.i(TAG, "Markwon initialized successfully")
        }
    }

    /**
     * Convert markdown text to Spanned for rendering.
     * Returns plain text if Markwon is not initialized.
     */
    fun toSpanned(markdown: String): Spanned {
        val result = markwon?.toMarkdown(markdown)
        if (result != null) {
            Log.d(TAG, "Converted markdown (${markdown.length} chars) to Spanned with ${result.getSpans(0, result.length, Any::class.java).size} spans")
            return result
        }
        Log.w(TAG, "Markwon not initialized, returning plain text")
        return android.text.SpannedString(markdown)
    }

    /**
     * Check if markdown rendering is available
     */
    fun isInitialized(): Boolean {
        val initialized = markwon != null
        Log.d(TAG, "isInitialized() = $initialized")
        return initialized
    }
}
