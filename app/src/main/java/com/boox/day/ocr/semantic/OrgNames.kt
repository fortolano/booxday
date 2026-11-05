package com.boox.day.ocr.semantic

/**
 * Company and team names in event text ("Acme Corp", "Design team"): capitalised words, then a
 * company word.
 *
 * Each word must end in a space before the next one starts, so a run of letters can only be read one
 * way, and the repeat has a limit. The old pattern let every part of a word be empty and ignored case,
 * so six plain words could keep the regex engine busy for hours. That runs in native code, where
 * cancelling a coroutine can't stop it: All notes pinned a CPU core on the Go 10.3 (Nov 2026).
 */
internal object OrgNames {
    private val PATTERN = Regex("\\b(?:[A-Z][A-Za-z&'-]*\\.?\\s+){1,5}(?i:Inc|LLC|Corp|Ltd|Co|Company|Team|Group)\\b")

    fun find(text: String): Set<String> = PATTERN.findAll(text).map { it.value.lowercase() }.toSet()
}
