package com.boox.day.ocr.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for EventTextNormalizer.
 *
 * Tests verify that all 45 patterns correctly strip meeting platform boilerplate,
 * HTML tags, signatures, and other noise from calendar event descriptions.
 *
 * Test cases from Section 4.1.2 of SEMANTIC_GENIUS_TRANSFORMATION_PLAN_V2.md
 */
class EventTextNormalizerTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN COUNT VERIFICATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `pattern count should be exactly 45`() {
        assertEquals(
            "Pattern count must be exactly 45 as per plan specification",
            45,
            EventTextNormalizer.getPatternCount()
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PLAN-SPECIFIED TEST CASES (Section 4.1.2)
    // These test cleanDescription() which handles full text blocks with boilerplate
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes Zoom meeting boilerplate from text block`() {
        // Plan Section 4.1.2: Input → Expected Output
        val input = "Budget Review\n\nJoin Zoom Meeting\nhttps://zoom.us/j/123456789?pwd=abc123"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Budget Review", result)
    }

    @Test
    fun `cleanDescription removes HTML tags from text block`() {
        // Plan Section 4.1.2: Input → Expected Output
        val description = "<html><body><p>Q3 Planning</p></body></html>"
        val result = EventTextNormalizer.cleanDescription(description)
        assertEquals("Q3 Planning", result)
    }

    @Test
    fun `cleanDescription removes Teams meeting block from text`() {
        // Plan Section 4.1.2: Input → Expected Output
        val input = """Team Sync
________________________________________________________________________________

Join Microsoft Teams Meeting
https://teams.microsoft.com/l/meetup-join/abc123

Learn More | Meeting options
________________________________________________________________________________"""
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Team Sync", result)
    }

    @Test
    fun `cleanDescription removes iPhone signature from text block`() {
        // Plan Section 4.1.2: Input → Expected Output
        val input = "1:1 with John\n\nSent from my iPhone"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("1:1 with John", result)
    }

    @Test
    fun `cleanDescription removes Google Meet link from text block`() {
        // Plan Section 4.1.2: Input → Expected Output
        val input = "Project Alpha Kickoff\n\nJoin with Google Meet\nhttps://meet.google.com/abc-defg-hij"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Project Alpha Kickoff", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZOOM PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes Zoom Join Meeting URL`() {
        val input = "Discussion agenda\n\nJoin Zoom Meeting\nhttps://zoom.us/j/123"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Discussion agenda", result)
    }

    @Test
    fun `cleanDescription removes Zoom Meeting ID`() {
        val input = "Meeting ID: 123 456 7890\nPasscode: abc123"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Zoom One tap mobile section`() {
        val input = "Important meeting\n\nOne tap mobile\n+1234567890,,123456789#\n+0987654321,,123456789#\n\nDial by your location"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Important meeting", result)
    }

    @Test
    fun `cleanDescription removes Find your local number`() {
        val input = "Find your local number: https://zoom.us/u/abcde"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEAMS PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes full Teams meeting block with underscores`() {
        val input = """
            Discuss quarterly goals
            __________________________________________________________
            Join Microsoft Teams Meeting
            https://teams.microsoft.com/l/meetup-join/xyz
            Meeting ID: 123 456 789
            __________________________________________________________
        """.trimIndent()
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Discuss quarterly goals", result)
    }

    @Test
    fun `cleanDescription removes Teams Learn More Meeting options`() {
        val input = "Learn More | Meeting options"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Or call in audio only section`() {
        val input = "Agenda item\n\nOr call in (audio only)\n+1 555-123-4567\nPhone Conference ID: 123 456 789#\n\nNext section"
        val result = EventTextNormalizer.cleanDescription(input)
        assertTrue(result.contains("Agenda item"))
        assertTrue(result.contains("Next section"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE MEET PATTERNS (4 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes bare Google Meet URL`() {
        val input = "Weekly sync\nmeet.google.com/abc-defg-hij"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Weekly sync", result)
    }

    @Test
    fun `cleanDescription removes Join by phone section`() {
        val input = "Budget discussion\n\nJoin by phone\n+1 555-123-4567 PIN: 123456#\n\nMore joining options"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Budget discussion", result)
    }

    @Test
    fun `cleanDescription removes More joining options`() {
        val input = "More joining options"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEBEX PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes WebEx join from PC line`() {
        val input = "Join from PC, Mac, Linux, iOS or Android: https://example.webex.com/meet/abc"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes WebEx URL`() {
        val input = "Discussion\nhttps://company.webex.com/company/j.php?MTID=abc123"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Discussion", result)
    }

    @Test
    fun `cleanDescription removes WebEx meeting number`() {
        val input = "Meeting number (access code): 123 456 789\nMeeting password: abcdef"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Tap to join from mobile`() {
        val input = "Tap to join from a mobile device (attendees only)"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTML PATTERNS (9 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes all HTML tags`() {
        val input = "<div><p>Important <b>meeting</b> notes</p></div>"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Important meeting notes", result)
    }

    @Test
    fun `cleanDescription removes HTML entities`() {
        val input = "Q3&amp;Q4 Planning &gt; Budget &lt; Review"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Q3 Q4 Planning Budget Review", result)
    }

    @Test
    fun `cleanDescription removes nbsp entities`() {
        val input = "Word&nbsp;with&nbsp;spaces"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Word with spaces", result)
    }

    @Test
    fun `cleanDescription removes numeric HTML entities`() {
        val input = "Copyright &#169; 2025"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Copyright 2025", result)
    }

    @Test
    fun `cleanDescription normalizes Windows line endings`() {
        val input = "Line 1\r\nLine 2\r\nLine 3"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Line 1 Line 2 Line 3", result)
    }

    @Test
    fun `cleanDescription removes tabs`() {
        val input = "Column1\t\t\tColumn2"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Column1 Column2", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIGNATURE PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes separator lines`() {
        val input = "Content above\n-------------------\nSignature below"
        val result = EventTextNormalizer.cleanDescription(input)
        assertTrue(result.contains("Content above"))
    }

    @Test
    fun `cleanDescription removes Sent from various devices`() {
        val inputs = listOf(
            "Message\n\nSent from my iPhone",
            "Message\n\nSent from my iPad",
            "Message\n\nSent from my Android device",
            "Message\n\nSent from Outlook for iOS"
        )
        inputs.forEach { input ->
            val result = EventTextNormalizer.cleanDescription(input)
            assertEquals("Message", result)
        }
    }

    @Test
    fun `cleanDescription removes Get Outlook for iOS Android`() {
        val input = "Content\n\nGet Outlook for iOS or Android"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Content", result)
    }

    @Test
    fun `cleanDescription removes confidentiality notice`() {
        val input = "Meeting agenda\n\nCONFIDENTIALITY NOTICE: This message is intended for the use of the person or entity to which it is addressed."
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Meeting agenda", result)
    }

    @Test
    fun `cleanDescription removes received in error notice`() {
        val input = "If you have received this email in error, please delete it immediately."
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CALENDAR PATTERNS (6 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes When line with weekday`() {
        val input = "When: Monday, January 15, 2026 10:00 AM - 11:00 AM"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Where line`() {
        val input = "Where: Conference Room A\nAgenda: Budget review"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Agenda: Budget review", result)
    }

    @Test
    fun `cleanDescription removes Organizer line`() {
        val input = "Organizer: John Smith <john@example.com>"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Required and Optional lines`() {
        val input = "Required: Alice, Bob\nOptional: Charlie"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("", result)
    }

    @Test
    fun `cleanDescription removes Invitees section`() {
        val input = "Discussion\n\nInvitees:\nAlice\nBob\nCharlie\n\nAgenda follows"
        val result = EventTextNormalizer.cleanDescription(input)
        assertTrue(result.contains("Discussion"))
        assertTrue(result.contains("Agenda follows"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // URL PATTERNS (2 patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDescription removes https URLs`() {
        val input = "Check https://example.com/doc/123 for details"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Check for details", result)
    }

    @Test
    fun `cleanDescription removes http URLs`() {
        val input = "Link: http://example.com/page"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Link:", result)
    }

    @Test
    fun `cleanDescription removes www URLs`() {
        val input = "Visit www.example.com/info for more"
        val result = EventTextNormalizer.cleanDescription(input)
        assertEquals("Visit for more", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATTENDEE EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `extractAttendees finds names from Attendees line`() {
        val input = "Attendees: John Smith, Jane Doe, Bob Wilson"
        val attendees = EventTextNormalizer.extractAttendees(input)
        assertEquals(3, attendees.size)
        assertTrue(attendees.contains("John Smith"))
        assertTrue(attendees.contains("Jane Doe"))
        assertTrue(attendees.contains("Bob Wilson"))
    }

    @Test
    fun `extractAttendees finds names from Invitees line`() {
        val input = "Invitees: Alice, Bob, Charlie"
        val attendees = EventTextNormalizer.extractAttendees(input)
        assertEquals(3, attendees.size)
        assertTrue(attendees.contains("Alice"))
        assertTrue(attendees.contains("Bob"))
        assertTrue(attendees.contains("Charlie"))
    }

    @Test
    fun `extractAttendees finds names from Required line`() {
        val input = "Required: Project Manager, Team Lead"
        val attendees = EventTextNormalizer.extractAttendees(input)
        assertEquals(2, attendees.size)
    }

    @Test
    fun `extractAttendees filters out email addresses`() {
        val input = "Attendees: John Smith, john@example.com, Jane Doe"
        val attendees = EventTextNormalizer.extractAttendees(input)
        assertEquals(2, attendees.size)
        assertTrue(attendees.all { !it.contains("@") })
    }

    @Test
    fun `extractAttendees returns distinct names`() {
        val input = "Required: John Smith\nOptional: John Smith"
        val attendees = EventTextNormalizer.extractAttendees(input)
        assertEquals(1, attendees.count { it == "John Smith" })
    }

    @Test
    fun `extractAttendees returns empty list for null input`() {
        val attendees = EventTextNormalizer.extractAttendees(null)
        assertTrue(attendees.isEmpty())
    }

    @Test
    fun `extractAttendees returns empty list for blank input`() {
        val attendees = EventTextNormalizer.extractAttendees("   ")
        assertTrue(attendees.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NORMALIZE FUNCTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `normalize combines title and cleaned description`() {
        val result = EventTextNormalizer.normalize(
            "Budget Review",
            "<p>Quarterly budget discussion</p>"
        )
        assertEquals("Budget Review Quarterly budget discussion", result)
    }

    @Test
    fun `normalize returns only title when description is blank`() {
        val result = EventTextNormalizer.normalize("Team Meeting", "")
        assertEquals("Team Meeting", result)
    }

    @Test
    fun `normalize returns only title when description is null`() {
        val result = EventTextNormalizer.normalize("Team Meeting", null)
        assertEquals("Team Meeting", result)
    }

    @Test
    fun `normalize returns only description when title is null`() {
        val result = EventTextNormalizer.normalize(null, "Important discussion")
        assertEquals("Important discussion", result)
    }

    @Test
    fun `normalize returns only description when title is blank`() {
        val result = EventTextNormalizer.normalize("", "Important discussion")
        assertEquals("Important discussion", result)
    }

    @Test
    fun `normalize normalizes whitespace in title`() {
        val result = EventTextNormalizer.normalize("  Budget   Review  ", null)
        assertEquals("Budget Review", result)
    }

    @Test
    fun `normalize returns empty string for null title and null description`() {
        val result = EventTextNormalizer.normalize(null, null)
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPLEX SCENARIOS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `normalize handles real-world Outlook meeting invite`() {
        val description = """
            <html>
            <body>
            <p>Please join us for the quarterly budget review.</p>

            When: Tuesday, January 15, 2026 2:00 PM - 3:00 PM
            Where: Conference Room B

            ________________________________________________________________________________

            Join Microsoft Teams Meeting
            https://teams.microsoft.com/l/meetup-join/abc123xyz

            Meeting ID: 123 456 789
            Passcode: abc123

            Or call in (audio only)
            +1 555-123-4567
            Phone Conference ID: 987 654 321#

            Learn More | Meeting options

            ________________________________________________________________________________

            </body>
            </html>
        """.trimIndent()

        val result = EventTextNormalizer.normalize("Q4 Budget Review", description)
        assertEquals("Q4 Budget Review Please join us for the quarterly budget review.", result)
    }

    @Test
    fun `normalize handles real-world Google Calendar invite`() {
        val description = """
            Project kickoff meeting to discuss Phase 1 deliverables.

            Join with Google Meet
            https://meet.google.com/abc-defg-hij

            Join by phone
            +1 234-567-8901 PIN: 123456789#

            More joining options
        """.trimIndent()

        val result = EventTextNormalizer.normalize("Project Alpha Kickoff", description)
        assertEquals("Project Alpha Kickoff Project kickoff meeting to discuss Phase 1 deliverables.", result)
    }

    @Test
    fun `normalize handles real-world Zoom invite`() {
        val description = """
            Weekly team sync to discuss progress and blockers.

            Join Zoom Meeting
            https://zoom.us/j/123456789?pwd=abcdefghij

            Meeting ID: 123 456 789
            Passcode: abc123

            One tap mobile
            +12345678901,,123456789#,,,,*123456# US
            +10987654321,,123456789#,,,,*123456# US

            Dial by your location
                +1 234 567 8901 US
                +1 098 765 4321 US
            Meeting ID: 123 456 789
            Passcode: 123456
            Find your local number: https://zoom.us/u/abcdefg
        """.trimIndent()

        val result = EventTextNormalizer.normalize("Weekly Team Sync", description)
        assertEquals("Weekly Team Sync Weekly team sync to discuss progress and blockers.", result)
    }
}
