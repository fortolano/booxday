package com.boox.day.ocr

import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import java.util.Locale

/**
 * Every language the handwriting library can read, for the Other language card (Nov 2026). The
 * library's own list holds gesture models too (tags with -x-), which aren't languages.
 */
object HandwritingLanguages {

    data class Language(val tag: String, val name: String)

    /** The nine languages with a card of their own. */
    val FIXED = listOf("en-US", "de-DE", "es-ES", "fr-FR", "it-IT", "ru-RU", "ja", "ko", "zh-Hans")

    /** The library's codes for the fixed languages, which it lists under other tags. */
    private val FIXED_LIBRARY_TAGS = setOf("en-US", "de-DE", "es-ES", "fr-FR", "it-IT", "ru", "ja", "ko", "zh-Hani")

    /** A language from the library that has no card of its own. */
    fun isOther(libraryTag: String): Boolean = "-x-" !in libraryTag && libraryTag !in FIXED_LIBRARY_TAGS && libraryTag !in FIXED

    /** All but the fixed ones, by name. */
    val others: List<Language> by lazy {
        DigitalInkRecognitionModelIdentifier.allModelIdentifiers()
            .map { it.languageTag }
            .filter { isOther(it) && hasName(it) }
            .map { Language(it, nameOf(it)) }
            .sortedBy { it.name.lowercase(Locale.ENGLISH) }
    }

    /** Android has no name for many small languages, only their code, which means nothing in a list. */
    private fun hasName(tag: String): Boolean {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(Locale.ENGLISH) != locale.language
    }

    /** "Portuguese (Brazil)"; the tag itself when Android has no name for it. */
    fun nameOf(tag: String): String {
        val name = Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH)
        return name.ifBlank { tag }
    }

    /** The first letters with languages, in order. */
    fun letters(languages: List<Language> = others): List<Char> =
        languages.map { it.name.first().uppercaseChar() }.distinct().sorted()
}

/**
 * Replacing the language on the Other language card (owner, 5 Nov 2026): the new one downloads first,
 * so a failed download leaves the card as it was; a language in use hands over; the old one goes last.
 */
internal object OtherLanguageSwap {

    /** @return false when [new] couldn't be downloaded. */
    suspend fun swap(
        old: String?,
        new: String,
        wasActive: Boolean,
        show: (String?) -> Unit,
        download: suspend (String) -> Boolean,
        activate: suspend (String) -> Unit,
        delete: suspend (String) -> Unit
    ): Boolean {
        if (new == old) return true
        show(new)
        if (!download(new)) {
            show(old)
            return false
        }
        if (wasActive) activate(new)
        if (old != null) delete(old)
        return true
    }
}
