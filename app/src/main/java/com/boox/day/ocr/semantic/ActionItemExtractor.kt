package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ActionItem

/**
 * Step 8: Action item extraction.
 *
 * Detects actionable items based on:
 * - Action verbs at sentence start
 * - "need to", "must", "should" patterns
 * - Checkbox patterns [ ], [x], - [ ]
 *
 * Extracts: verb, object, dueDateCandidate, timeCandidate, assigneeCandidate
 * Returns up to 15 action items.
 */
object ActionItemExtractor {

    /**
     * Maximum action items to return
     */
    private const val MAX_ACTION_ITEMS = 15

    /**
     * Action verbs that indicate actionable items
     */
    private val ACTION_VERBS = setOf(
        "call", "email", "send", "submit", "review", "complete", "finish",
        "buy", "schedule", "book", "prepare", "create", "update", "fix",
        "check", "confirm", "contact", "follow", "meet", "discuss", "plan",
        "write", "read", "organize", "clean", "pay", "order", "cancel",
        "remind", "pick", "return", "sign", "deliver", "upload", "download",
        "print", "draft", "edit", "approve", "reject", "assign", "delegate",
        "investigate", "research", "analyze", "report", "present", "demo",
        "setup", "configure", "install", "uninstall", "backup", "test",
        "hire", "fire", "train", "teach", "learn", "study", "practice"
    )

    /**
     * Modal patterns that precede action verbs
     */
    private val MODAL_PATTERNS = listOf(
        "need to",
        "needs to",
        "have to",
        "has to",
        "must",
        "should",
        "will",
        "shall",
        "going to",
        "want to",
        "wants to",
        "remember to",
        "don't forget to",
        "make sure to"
    )

    /**
     * Checkbox patterns
     */
    private val CHECKBOX_PATTERNS = listOf(
        Regex("^\\s*\\[\\s*\\]\\s*(.*)$", RegexOption.MULTILINE),  // [ ] task
        Regex("^\\s*\\[x\\]\\s*(.*)$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),  // [x] completed
        Regex("^\\s*-\\s*\\[\\s*\\]\\s*(.*)$", RegexOption.MULTILINE),  // - [ ] task
        Regex("^\\s*-\\s*\\[x\\]\\s*(.*)$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))  // - [x] completed
    )

    /**
     * Date indicators for due date extraction
     */
    private val DATE_INDICATORS = listOf(
        "by", "before", "until", "due", "deadline", "on", "at"
    )

    /**
     * Extract action items from text.
     */
    fun extract(text: String, sentences: List<String>): List<ActionItem> {
        val actionItems = mutableListOf<ActionItem>()

        // Extract from checkbox patterns (highest confidence)
        actionItems.addAll(extractCheckboxItems(text))

        // Extract from imperative sentences (verb at start)
        actionItems.addAll(extractImperativeItems(sentences))

        // Extract from modal patterns (need to, must, should)
        actionItems.addAll(extractModalItems(text))

        // Deduplicate and limit
        return deduplicateItems(actionItems).take(MAX_ACTION_ITEMS)
    }

    /**
     * Extract action items from checkbox patterns.
     */
    private fun extractCheckboxItems(text: String): List<ActionItem> {
        val items = mutableListOf<ActionItem>()

        for (pattern in CHECKBOX_PATTERNS) {
            pattern.findAll(text).forEach { match ->
                val taskText = match.groupValues.getOrNull(1)?.trim() ?: match.value.trim()
                if (taskText.length >= 3) {
                    val parsed = parseActionText(taskText)
                    items.add(ActionItem(
                        verb = parsed.verb ?: "do",
                        objectText = parsed.objectText,
                        dueDateCandidate = parsed.dueDate,
                        timeCandidate = parsed.time,
                        assigneeCandidate = parsed.assignee,
                        confidence = 0.9f,  // High confidence for checkboxes
                        rawText = taskText
                    ))
                }
            }
        }

        return items
    }

    /**
     * Extract action items from imperative sentences (verb at start).
     */
    private fun extractImperativeItems(sentences: List<String>): List<ActionItem> {
        val items = mutableListOf<ActionItem>()

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            val words = trimmed.split(Regex("\\s+"))
            if (words.isEmpty()) continue

            val firstWord = words[0].lowercase().removeSuffix(".")

            if (firstWord in ACTION_VERBS) {
                val objectText = if (words.size > 1) {
                    words.drop(1).joinToString(" ").trim()
                } else {
                    ""
                }

                if (objectText.length >= 3) {
                    val parsed = parseActionText(trimmed)
                    items.add(ActionItem(
                        verb = firstWord,
                        objectText = parsed.objectText,
                        dueDateCandidate = parsed.dueDate,
                        timeCandidate = parsed.time,
                        assigneeCandidate = parsed.assignee,
                        confidence = 0.7f,
                        rawText = trimmed
                    ))
                }
            }
        }

        return items
    }

    /**
     * Extract action items from modal patterns.
     */
    private fun extractModalItems(text: String): List<ActionItem> {
        val items = mutableListOf<ActionItem>()

        for (modal in MODAL_PATTERNS) {
            val pattern = Regex("\\b$modal\\s+(\\w+)(.{3,100}?)(?:[.!?]|$)", RegexOption.IGNORE_CASE)

            pattern.findAll(text).forEach { match ->
                val verb = match.groupValues[1].lowercase()
                val rest = match.groupValues[2].trim()

                // Only include if verb is an action verb or closely related
                if (verb in ACTION_VERBS || isLikelyActionVerb(verb)) {
                    val fullText = "$modal $verb$rest"
                    val parsed = parseActionText(fullText)

                    items.add(ActionItem(
                        verb = verb,
                        objectText = rest.trimEnd('.', '!', '?'),
                        dueDateCandidate = parsed.dueDate,
                        timeCandidate = parsed.time,
                        assigneeCandidate = parsed.assignee,
                        confidence = 0.6f,
                        rawText = fullText
                    ))
                }
            }
        }

        return items
    }

    /**
     * Check if a verb is likely an action verb (ends in common action suffixes).
     */
    private fun isLikelyActionVerb(verb: String): Boolean {
        val actionSuffixes = listOf("ize", "ate", "ify", "ish")
        return actionSuffixes.any { verb.endsWith(it) }
    }

    /**
     * Parse action text to extract components.
     */
    private fun parseActionText(text: String): ParsedAction {
        val words = text.split(Regex("\\s+"))

        // Extract verb (first word if action verb)
        val verb = words.firstOrNull()?.lowercase()?.removeSuffix(".")?.let {
            if (it in ACTION_VERBS) it else null
        }

        // Extract date indicators
        var dueDate: String? = null
        var time: String? = null
        var assignee: String? = null

        val lowerText = text.lowercase()

        // Look for date patterns
        for (indicator in DATE_INDICATORS) {
            val datePattern = Regex("\\b$indicator\\s+([^,.]+ (?:\\d{1,2}(?:st|nd|rd|th)?|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?|tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next week))", RegexOption.IGNORE_CASE)
            datePattern.find(text)?.let {
                dueDate = it.groupValues[1].trim()
            }
        }

        // Look for time patterns
        val timePattern = Regex("\\bat\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon|midnight)", RegexOption.IGNORE_CASE)
        timePattern.find(text)?.let {
            time = it.groupValues[1].trim()
        }

        // Look for assignee patterns
        val assigneePattern = Regex("\\bwith\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)|\\bfor\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)")
        assigneePattern.find(text)?.let {
            assignee = (it.groupValues[1] + it.groupValues[2]).trim()
        }

        // Object text: everything except verb, date, time
        val objectText = text
            .let { if (verb != null) it.removePrefix(verb).removePrefix(verb.replaceFirstChar { c -> c.uppercaseChar() }) else it }
            .replace(Regex("\\b(by|before|until|due|on|at)\\s+[^,]+"), "")
            .trim()
            .trimStart()
            .trimEnd('.', '!', '?', ',')

        return ParsedAction(
            verb = verb,
            objectText = objectText,
            dueDate = dueDate,
            time = time,
            assignee = assignee
        )
    }

    /**
     * Deduplicate similar action items.
     */
    private fun deduplicateItems(items: List<ActionItem>): List<ActionItem> {
        val result = mutableListOf<ActionItem>()

        for (item in items.sortedByDescending { it.confidence }) {
            val normalized = item.rawText.lowercase().trim()

            val isDuplicate = result.any { existing ->
                val existingNorm = existing.rawText.lowercase().trim()
                existingNorm == normalized ||
                existingNorm.contains(normalized) ||
                normalized.contains(existingNorm)
            }

            if (!isDuplicate) {
                result.add(item)
            }
        }

        return result
    }

    private data class ParsedAction(
        val verb: String?,
        val objectText: String,
        val dueDate: String?,
        val time: String?,
        val assignee: String?
    )
}
