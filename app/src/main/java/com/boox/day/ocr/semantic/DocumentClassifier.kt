package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.DocumentStyle

/**
 * Step 2: Document style classification.
 *
 * Detects document type based on structural heuristics:
 * - Bullet density
 * - Timestamp frequency
 * - Imperative verb count
 * - Form-like patterns
 */
object DocumentClassifier {

    /**
     * Bullet/list patterns
     */
    private val BULLET_PATTERNS = listOf(
        Regex("^[•●○▪▸►◆◇→-]\\s", RegexOption.MULTILINE),
        Regex("^\\d+[.):]\\s", RegexOption.MULTILINE),
        Regex("^[a-z][.):]\\s", RegexOption.MULTILINE),
        Regex("^\\[[ xX]\\]\\s", RegexOption.MULTILINE),  // Checkbox [ ] or [x]
        Regex("^\\*\\s", RegexOption.MULTILINE)
    )

    /**
     * Meeting-related keywords
     */
    private val MEETING_KEYWORDS = listOf(
        "meeting", "attendees", "participants", "agenda", "minutes",
        "action items", "discussed", "presenter", "attendee", "recap",
        "next steps", "follow-up", "call", "conference"
    )

    /**
     * Project plan keywords
     */
    private val PROJECT_KEYWORDS = listOf(
        "milestone", "deadline", "sprint", "phase", "deliverable",
        "timeline", "project", "task", "objective", "goal", "target",
        "schedule", "plan", "roadmap", "epic", "backlog"
    )

    /**
     * Form-like patterns (field: value)
     */
    private val FORM_PATTERN = Regex("^[A-Za-z][A-Za-z\\s]{1,20}:\\s", RegexOption.MULTILINE)

    /**
     * Timestamp patterns
     */
    private val TIMESTAMP_PATTERNS = listOf(
        Regex("\\d{1,2}:\\d{2}(\\s?[ap]m)?", RegexOption.IGNORE_CASE),
        Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"),
        Regex("\\d{4}-\\d{2}-\\d{2}")
    )

    /**
     * Imperative verbs (action-oriented)
     */
    private val IMPERATIVE_VERBS = listOf(
        "call", "email", "send", "submit", "review", "complete", "finish",
        "buy", "schedule", "book", "prepare", "create", "update", "fix",
        "check", "confirm", "contact", "follow", "meet", "discuss", "plan",
        "write", "read", "organize", "clean", "pay", "order", "cancel"
    )

    /**
     * Classify document style.
     */
    fun classify(text: String, sentences: List<String>): DocumentStyle {
        val lowerText = text.lowercase()
        val lines = text.split("\n")

        // Calculate features
        val bulletCount = countBullets(text)
        val bulletDensity = if (lines.isNotEmpty()) bulletCount.toFloat() / lines.size else 0f
        val timestampCount = countTimestamps(text)
        val imperativeCount = countImperatives(sentences)
        val formFieldCount = countFormFields(text)
        val meetingScore = countKeywords(lowerText, MEETING_KEYWORDS)
        val projectScore = countKeywords(lowerText, PROJECT_KEYWORDS)
        val checkboxCount = countCheckboxes(text)

        // Classification logic
        return when {
            // High checkbox count = checklist
            checkboxCount >= 3 || (bulletDensity > 0.5f && checkboxCount >= 1) -> {
                DocumentStyle.CHECKLIST
            }

            // High meeting score + timestamps = meeting notes
            meetingScore >= 3 && timestampCount >= 2 -> {
                DocumentStyle.MEETING_NOTES
            }

            // High form field count = form-like
            formFieldCount >= 5 -> {
                DocumentStyle.FORM_LIKE
            }

            // High project keywords + bullets = project plan
            projectScore >= 3 && bulletDensity > 0.3f -> {
                DocumentStyle.PROJECT_PLAN
            }

            // High bullet density but not meeting/project = checklist
            bulletDensity > 0.6f -> {
                DocumentStyle.CHECKLIST
            }

            // Low bullets, low structure = freeform journal
            bulletDensity < 0.15f && formFieldCount < 3 && timestampCount < 2 -> {
                DocumentStyle.FREEFORM_JOURNAL
            }

            // Mixed signals
            else -> DocumentStyle.MIXED
        }
    }

    /**
     * Count bullet points in text.
     */
    private fun countBullets(text: String): Int {
        return BULLET_PATTERNS.sumOf { pattern ->
            pattern.findAll(text).count()
        }
    }

    /**
     * Count timestamps in text.
     */
    private fun countTimestamps(text: String): Int {
        return TIMESTAMP_PATTERNS.sumOf { pattern ->
            pattern.findAll(text).count()
        }
    }

    /**
     * Count imperative verbs at sentence starts.
     */
    private fun countImperatives(sentences: List<String>): Int {
        return sentences.count { sentence ->
            val firstWord = sentence.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
            firstWord in IMPERATIVE_VERBS
        }
    }

    /**
     * Count form-like fields (label: value).
     */
    private fun countFormFields(text: String): Int {
        return FORM_PATTERN.findAll(text).count()
    }

    /**
     * Count keyword occurrences.
     */
    private fun countKeywords(text: String, keywords: List<String>): Int {
        return keywords.count { keyword -> keyword in text }
    }

    /**
     * Count checkbox patterns.
     */
    private fun countCheckboxes(text: String): Int {
        val checkboxPattern = Regex("\\[[ xX]\\]|\\[ \\]|\\[x\\]|\\[X\\]|- \\[ \\]|- \\[x\\]")
        return checkboxPattern.findAll(text).count()
    }
}
