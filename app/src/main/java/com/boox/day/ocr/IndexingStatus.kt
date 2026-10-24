package com.boox.day.ocr

import com.boox.day.api.ai.AiProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Why reading handwriting for search is waiting right now (Oct 2026): no internet, no key, no
 * credit, OpenRouter down. Shown in the Boox Notes settings and the daily notes, which only said
 * "not read yet" before. Kept in memory: after a restart the next read finds out again.
 */
object IndexingStatus {

    private val _waitingFor = MutableStateFlow<AiProblem?>(null)

    /** The problem the last read stopped on, or null once a page was read */
    val waitingFor: StateFlow<AiProblem?> = _waitingFor.asStateFlow()

    fun onWaiting(problem: AiProblem) {
        _waitingFor.value = problem
    }

    fun onPageRead() {
        _waitingFor.value = null
    }
}
