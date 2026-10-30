package com.boox.day.ai

import com.boox.day.data.ai.ExtendedTaskContext
import com.boox.day.search.SearchText

/**
 * Smart Suggestions and the tasks the user already has (29 Oct 2026). On the Go 10.3 it suggested
 * "Call mom" and "Send mockups to Sam", both on that day's list (handwritten, ticked): those tasks
 * reached the AI without their words, so its "avoid duplicates" list left them out.
 *
 * [tasksToAvoid] is that list; [withoutRepeats] is the last check on the answer, for a model that
 * repeats a task anyway. A suggestion that only shares some words with a task is kept. A follow-up
 * that adds words ("Call mom about the Lisbon trip dates") is kept for a task already done, but not
 * for one still open (owner, 29 Oct 2026: "Send email follow-up to landlord regarding overdue
 * notice" came back for the open, overdue "Email the landlord").
 */
object SuggestionDuplicates {

    /** Most tasks the prompt lists as existing */
    const val MAX_TASKS_TO_AVOID = 30

    /** Words a suggestion may add to a task's words and still be a repeat ("… during lunch") */
    private const val MAX_ADDED_WORDS = 2

    private val SPACES = Regex("\\s+")

    /** Small English words that don't tell two tasks apart */
    private val SMALL_WORDS = setOf(
        "a", "an", "the", "to", "for", "of", "and", "or", "at", "on", "in", "with", "by",
        "my", "your", "from", "about", "before", "after", "during", "up"
    )

    /**
     * The tasks a suggestion must not repeat: today's (ticked ones too), the overdue ones and the
     * coming days', each once. Tasks without words are left out.
     */
    fun tasksToAvoid(tasks: ExtendedTaskContext): List<String> =
        (tasks.todayTasks + tasks.recentIncompleteTasks + tasks.upcomingTasks)
            .map { it.text.trim().replace(SPACES, " ") }
            .filter { it.isNotEmpty() }
            .distinctBy { SearchText.fold(it) }
            .take(MAX_TASKS_TO_AVOID)

    /** The tasks among [tasksToAvoid] that aren't done yet: a suggestion can't restate them with more words. */
    fun openTasks(tasks: ExtendedTaskContext): List<String> =
        (tasks.todayTasks + tasks.recentIncompleteTasks + tasks.upcomingTasks)
            .filter { !it.isCompleted }
            .map { it.text.trim().replace(SPACES, " ") }
            .filter { it.isNotEmpty() }
            .distinctBy { SearchText.fold(it) }

    /**
     * True when [suggestion] repeats [task]: the same words; the suggestion's words (two or more)
     * all in the task ("Call mom" and "Call mom at 3PM"); or the task's words (two or more) all in
     * the suggestion, which adds at most two ("Send mockups to Sam during lunch"), or any number
     * when the task is [open] (not done yet). Words are compared folded (case, accents,
     * punctuation) without [SMALL_WORDS].
     */
    fun repeats(suggestion: String, task: String, open: Boolean = false): Boolean {
        val suggested = words(suggestion)
        val existing = words(task)
        if (suggested.isEmpty() || existing.isEmpty()) return false
        if (suggested == existing) return true
        if (suggested.size >= 2 && existing.containsAll(suggested)) return true
        return existing.size >= 2 && suggested.containsAll(existing) &&
            (open || suggested.size - existing.size <= MAX_ADDED_WORDS)
    }

    /**
     * [suggestions] without the ones that repeat one of [tasks] (those also in [openTasks] are still
     * to do, so the stricter rule applies); [text] gives a suggestion's text.
     */
    fun <T> withoutRepeats(
        suggestions: List<T>,
        tasks: List<String>,
        openTasks: List<String> = emptyList(),
        text: (T) -> String
    ): List<T> {
        val open = openTasks.map { SearchText.fold(it) }.toSet()
        return suggestions.filterNot { suggestion ->
            tasks.any { repeats(text(suggestion), it, open = SearchText.fold(it) in open) }
        }
    }

    private fun words(text: String): Set<String> =
        SearchText.queryWords(text).filterNot { it in SMALL_WORDS }.toSet()
}
