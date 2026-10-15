package com.boox.day.ocr.semantic

/**
 * Normalizes event text for semantic classification.
 *
 * Calendar event descriptions often contain meeting platform boilerplate (Zoom, Teams,
 * Google Meet, WebEx), HTML tags, signatures, and other noise that would mislead the
 * DomainIntentClassifier. This normalizer strips all such content to extract the
 * meaningful text for classification.
 *
 * Pattern groups (45 total patterns):
 * - ZOOM_PATTERNS: 6 patterns for Zoom meeting links and dial-in info
 * - TEAMS_PATTERNS: 6 patterns for Microsoft Teams meeting blocks
 * - GOOGLE_MEET_PATTERNS: 4 patterns for Google Meet links
 * - WEBEX_PATTERNS: 6 patterns for Cisco WebEx meeting info
 * - HTML_PATTERNS: 9 patterns for HTML tags and entities
 * - SIGNATURE_PATTERNS: 6 patterns for email signatures and confidentiality notices
 * - CALENDAR_PATTERNS: 6 patterns for calendar metadata (When, Where, Organizer, etc.)
 * - URL_PATTERNS: 2 patterns for generic URLs (applied last)
 *
 * @see normalize Main entry point for event text normalization
 * @see cleanDescription Strip all boilerplate from description
 * @see extractAttendees Extract attendee names from description
 */
object EventTextNormalizer {

    // ═══════════════════════════════════════════════════════════════════════════
    // ZOOM PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val ZOOM_PATTERNS = listOf(
        // Pattern 1: Join Zoom Meeting with URL
        Regex("""Join Zoom Meeting\s*https://[\w./\-?=&]+""", RegexOption.IGNORE_CASE),
        // Pattern 2: Meeting ID line
        Regex("""Meeting ID:\s*\d[\d\s]+""", RegexOption.IGNORE_CASE),
        // Pattern 3: Passcode line
        Regex("""Passcode:\s*\S+""", RegexOption.IGNORE_CASE),
        // Pattern 4: One tap mobile section (multi-line until double newline)
        Regex("""One tap mobile[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE),
        // Pattern 5: Dial by location section (multi-line until double newline)
        Regex("""Dial by your location[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE),
        // Pattern 6: Find local number line
        Regex("""Find your local number:.*""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // MICROSOFT TEAMS PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val TEAMS_PATTERNS = listOf(
        // Pattern 1: Full Teams meeting block (surrounded by underscores)
        Regex("""_{10,}[\s\S]*?Join Microsoft Teams Meeting[\s\S]*?_{10,}""", RegexOption.IGNORE_CASE),
        // Pattern 2: Join Teams Meeting with URL
        Regex("""Join Microsoft Teams Meeting\s*https://teams\.microsoft\.com/[\w./\-?=&]+""", RegexOption.IGNORE_CASE),
        // Pattern 3: Teams meeting on computer line
        Regex("""Microsoft Teams meeting\s*Join on your computer.*""", RegexOption.IGNORE_CASE),
        // Pattern 4: Meeting ID and Passcode combined
        Regex("""Meeting ID:\s*[\d\s]+\s*Passcode:\s*\S+""", RegexOption.IGNORE_CASE),
        // Pattern 5: Call in audio only section (multi-line until double newline)
        Regex("""Or call in \(audio only\)[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE),
        // Pattern 6: Learn More and Meeting options links
        Regex("""Learn More\s*\|\s*Meeting options""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE MEET PATTERNS (4 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val GOOGLE_MEET_PATTERNS = listOf(
        // Pattern 1: Join with Google Meet with full URL
        Regex("""Join with Google Meet\s*https://meet\.google\.com/[\w\-]+""", RegexOption.IGNORE_CASE),
        // Pattern 2: Bare meet.google.com URL
        Regex("""meet\.google\.com/[\w\-]+""", RegexOption.IGNORE_CASE),
        // Pattern 3: Join by phone section (multi-line until double newline)
        Regex("""Join by phone[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE),
        // Pattern 4: More joining options text
        Regex("""More joining options""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // WEBEX PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val WEBEX_PATTERNS = listOf(
        // Pattern 1: Join from PC/Mac/Linux/iOS/Android line
        Regex("""Join from PC, Mac, Linux, iOS or Android:.*""", RegexOption.IGNORE_CASE),
        // Pattern 2: WebEx URL
        Regex("""https://[\w.]+\.webex\.com/[\w./\-?=&]+""", RegexOption.IGNORE_CASE),
        // Pattern 3: Join by video system section (multi-line until double newline)
        Regex("""Join by video system[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE),
        // Pattern 4: Meeting number (access code) line
        Regex("""Meeting number \(access code\):.*""", RegexOption.IGNORE_CASE),
        // Pattern 5: Meeting password line
        Regex("""Meeting password:.*""", RegexOption.IGNORE_CASE),
        // Pattern 6: Tap to join from mobile device
        Regex("""Tap to join from a mobile device.*""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // HTML & OUTLOOK PATTERNS (9 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val HTML_PATTERNS = listOf(
        // Pattern 1: All HTML tags
        Regex("""<[^>]+>"""),
        // Pattern 2: Non-breaking space entity
        Regex("""&nbsp;"""),
        // Pattern 3: Ampersand entity
        Regex("""&amp;"""),
        // Pattern 4: Less-than entity
        Regex("""&lt;"""),
        // Pattern 5: Greater-than entity
        Regex("""&gt;"""),
        // Pattern 6: Quote entity
        Regex("""&quot;"""),
        // Pattern 7: Numeric HTML entities
        Regex("""&#\d+;"""),
        // Pattern 8: Windows line endings
        Regex("""\r\n"""),
        // Pattern 9: Multiple tabs
        Regex("""\t+""")
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SIGNATURE & BOILERPLATE PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val SIGNATURE_PATTERNS = listOf(
        // Pattern 1: Separator lines (---, ___, ===, etc.)
        Regex("""^[-_=]{3,}.*""", RegexOption.MULTILINE),
        // Pattern 2: Sent from iPhone/iPad/Android/Outlook (with or without "my")
        Regex("""Sent from (my )?(iPhone|iPad|Android|Outlook).*""", RegexOption.IGNORE_CASE),
        // Pattern 3: Get Outlook for iOS/Android
        Regex("""Get Outlook for (iOS|Android).*""", RegexOption.IGNORE_CASE),
        // Pattern 4: Email confidentiality generic
        Regex("""This email and any attachments.*confidential.*""", RegexOption.IGNORE_CASE),
        // Pattern 5: Confidentiality notice block (to end of text)
        Regex("""CONFIDENTIALITY NOTICE:[\s\S]*$""", RegexOption.IGNORE_CASE),
        // Pattern 6: Received in error notice
        Regex("""If you have received this email in error.*""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CALENDAR-SPECIFIC PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    private val CALENDAR_PATTERNS = listOf(
        // Pattern 1: When line with weekday
        Regex("""When:\s*(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday).*""", RegexOption.IGNORE_CASE),
        // Pattern 2: Where line
        Regex("""Where:\s*.*""", RegexOption.IGNORE_CASE),
        // Pattern 3: Organizer line
        Regex("""Organizer:\s*.*""", RegexOption.IGNORE_CASE),
        // Pattern 4: Required attendees line
        Regex("""Required:\s*.*""", RegexOption.IGNORE_CASE),
        // Pattern 5: Optional attendees line
        Regex("""Optional:\s*.*""", RegexOption.IGNORE_CASE),
        // Pattern 6: Invitees section (multi-line until double newline)
        Regex("""Invitees:[\s\S]*?(?=\n\n|\z)""", RegexOption.IGNORE_CASE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // GENERIC URL PATTERNS (2 patterns) - Applied last after platform-specific
    // ═══════════════════════════════════════════════════════════════════════════

    private val URL_PATTERNS = listOf(
        // Pattern 1: http/https URLs
        Regex("""https?://[^\s<>"{}|\\^`\[\]]+"""),
        // Pattern 2: www URLs without protocol
        Regex("""www\.[^\s<>"{}|\\^`\[\]]+""")
    )

    /**
     * Normalize event text for classification.
     *
     * Combines the event title (kept mostly intact, just whitespace normalized) with
     * the cleaned description. The result is suitable for DomainIntentClassifier.
     *
     * @param title Event title (kept as-is, only whitespace normalized)
     * @param description Event description (fully cleaned of boilerplate)
     * @return Combined clean text suitable for classification
     */
    fun normalize(title: String?, description: String?): String {
        val cleanTitle = title?.trim()?.replace(Regex("\\s+"), " ") ?: ""
        val cleanDesc = cleanDescription(description)

        return when {
            cleanTitle.isNotBlank() && cleanDesc.isNotBlank() -> "$cleanTitle $cleanDesc"
            cleanTitle.isNotBlank() -> cleanTitle
            cleanDesc.isNotBlank() -> cleanDesc
            else -> ""
        }.trim()
    }

    /**
     * Clean event description by removing all boilerplate.
     *
     * Applies all 45 patterns in order:
     * 1. HTML patterns (strip tags and entities first)
     * 2. Platform-specific patterns (Zoom, Teams, Meet, WebEx)
     * 3. Signature patterns (email signatures, confidentiality)
     * 4. Calendar patterns (When, Where, Organizer metadata)
     * 5. URL patterns (generic URLs last, after platform-specific)
     *
     * @param description Raw event description
     * @return Cleaned description with all boilerplate removed
     */
    fun cleanDescription(description: String?): String {
        if (description.isNullOrBlank()) return ""

        // After null/blank check, description is guaranteed non-null and non-blank
        var result: String = description

        // Apply all pattern groups in order
        // HTML first (removes tags that might contain platform content)
        val allPatterns = HTML_PATTERNS +
            ZOOM_PATTERNS +
            TEAMS_PATTERNS +
            GOOGLE_MEET_PATTERNS +
            WEBEX_PATTERNS +
            SIGNATURE_PATTERNS +
            CALENDAR_PATTERNS +
            URL_PATTERNS  // URLs last (after platform-specific removal)

        for (pattern in allPatterns) {
            result = pattern.replace(result, " ")
        }

        // Normalize whitespace: collapse multiple spaces, trim
        result = result
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^\\s+|\\s+$"), "")
            .trim()

        return result
    }

    /**
     * Extract attendee names from description.
     *
     * Looks for attendee/invitee lists in the format:
     * - "Attendees: John Smith, Jane Doe"
     * - "Invitees: Alice, Bob, Charlie"
     * - "Required: Person A, Person B"
     * - "Optional: Person C"
     *
     * Note: This should be called on the RAW description, not the cleaned one,
     * since cleanDescription removes the attendee patterns.
     *
     * @param description Raw event description (before cleaning)
     * @return List of distinct attendee names (emails filtered out)
     */
    fun extractAttendees(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()

        val attendees = mutableListOf<String>()

        // Pattern: "Attendees|Invitees|Required|Optional: Name1, Name2, ..."
        val attendeePattern = Regex(
            """(?:Attendees?|Invitees?|Required|Optional):\s*([^,\n]+(?:,\s*[^,\n]+)*)""",
            RegexOption.IGNORE_CASE
        )

        attendeePattern.findAll(description).forEach { match ->
            match.groupValues[1].split(",").forEach { name ->
                val cleaned = name.trim()
                // Filter out email addresses and empty strings
                if (cleaned.isNotBlank() && !cleaned.contains("@")) {
                    attendees.add(cleaned)
                }
            }
        }

        return attendees.distinct()
    }

    /**
     * Get the total count of patterns for verification.
     * Should return 45 as per the plan specification.
     */
    fun getPatternCount(): Int {
        return ZOOM_PATTERNS.size +      // 6
            TEAMS_PATTERNS.size +        // 6
            GOOGLE_MEET_PATTERNS.size +  // 4
            WEBEX_PATTERNS.size +        // 6
            HTML_PATTERNS.size +         // 9
            SIGNATURE_PATTERNS.size +    // 6
            CALENDAR_PATTERNS.size +     // 6
            URL_PATTERNS.size            // 2
        // Total: 45
    }
}
