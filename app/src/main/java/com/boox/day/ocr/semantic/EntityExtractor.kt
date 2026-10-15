package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.EntityType
import com.boox.day.ocr.model.ExtractedEntity

/**
 * Step 7: Named entity extraction via regex patterns.
 *
 * Extracts:
 * - DATE: Various date formats (YYYY-MM-DD, MM/DD/YYYY, "Jan 5", "next week")
 * - TIME: Time formats (HH:MM, "10am", "noon")
 * - EMAIL: Email addresses
 * - PHONE: Phone numbers
 * - URL: Web URLs
 * - MONEY: Currency amounts ($100, USD 50, €25, R$100)
 * - PERCENT: Percentages (50%, 25.5%, -10%)
 * - PERSON: Potential person names (capitalized multi-word)
 * - ORG: Organization names (common suffixes)
 * - LOCATION: Location indicators (common prepositions + place)
 */
object EntityExtractor {

    /**
     * Date patterns with confidence levels
     */
    private val DATE_PATTERNS = listOf(
        // ISO format: 2024-01-15
        PatternDef(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), EntityType.DATE, 1.0f),

        // US format: 01/15/2024 or 1/15/24
        PatternDef(Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b"), EntityType.DATE, 0.9f),

        // European format: 15.01.2024
        PatternDef(Regex("\\b\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\b"), EntityType.DATE, 0.9f),

        // Month name: Jan 15, January 15, 15 January
        PatternDef(Regex("\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.95f),

        // Day Month: 15 Jan, 15th January
        PatternDef(Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.95f),

        // Relative dates: today, tomorrow, yesterday
        PatternDef(Regex("\\b(today|tomorrow|yesterday)\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.8f),

        // Relative week: next week, last week, this week
        PatternDef(Regex("\\b(next|last|this)\\s+(week|month|year)\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.7f),

        // In N days/weeks
        PatternDef(Regex("\\bin\\s+\\d+\\s+(day|week|month)s?\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.7f),

        // Weekdays
        PatternDef(Regex("\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.6f),

        // Date ranges: Jan 5-10, 5th-10th
        PatternDef(Regex("\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:st|nd|rd|th)?\\s*[-–]\\s*\\d{1,2}(?:st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE), EntityType.DATE, 0.85f)
    )

    /**
     * Time patterns
     */
    private val TIME_PATTERNS = listOf(
        // HH:MM format: 14:30, 2:30
        PatternDef(Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b"), EntityType.TIME, 0.95f),

        // AM/PM format: 10am, 10 am, 10:30am
        PatternDef(Regex("\\b\\d{1,2}(:\\d{2})?\\s*[ap]\\.?m\\.?\\b", RegexOption.IGNORE_CASE), EntityType.TIME, 0.9f),

        // Named times: noon, midnight
        PatternDef(Regex("\\b(noon|midnight|morning|afternoon|evening|night)\\b", RegexOption.IGNORE_CASE), EntityType.TIME, 0.7f),

        // O'clock: 3 o'clock
        PatternDef(Regex("\\b\\d{1,2}\\s+o'clock\\b", RegexOption.IGNORE_CASE), EntityType.TIME, 0.85f)
    )

    /**
     * Email pattern
     */
    private val EMAIL_PATTERN = PatternDef(
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
        EntityType.EMAIL,
        0.98f
    )

    /**
     * Phone patterns
     */
    private val PHONE_PATTERNS = listOf(
        // US format: (123) 456-7890, 123-456-7890
        PatternDef(Regex("\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"), EntityType.PHONE, 0.9f),

        // International: +1 123 456 7890
        PatternDef(Regex("\\+\\d{1,3}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}\\b"), EntityType.PHONE, 0.95f),

        // Brazilian format: (11) 99999-9999
        PatternDef(Regex("\\(\\d{2}\\)\\s*\\d{4,5}[-.\\s]?\\d{4}\\b"), EntityType.PHONE, 0.9f)
    )

    /**
     * URL pattern
     */
    private val URL_PATTERN = PatternDef(
        Regex("\\bhttps?://[^\\s]+\\b"),
        EntityType.URL,
        0.98f
    )

    /**
     * Money patterns
     */
    private val MONEY_PATTERNS = listOf(
        // Dollar: $100, $1,000.00, $50.99
        PatternDef(Regex("\\$\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b"), EntityType.MONEY, 0.95f),

        // Euro: €100, €1,000.00
        PatternDef(Regex("€\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b"), EntityType.MONEY, 0.95f),

        // Pound: £100
        PatternDef(Regex("£\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b"), EntityType.MONEY, 0.95f),

        // Real: R$100, R$ 100
        PatternDef(Regex("R\\$\\s?\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\b"), EntityType.MONEY, 0.95f),

        // Written currency: USD 100, EUR 50
        PatternDef(Regex("\\b(USD|EUR|GBP|BRL|JPY)\\s?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b", RegexOption.IGNORE_CASE), EntityType.MONEY, 0.9f),

        // Amount + currency: 100 USD, 50 euros
        PatternDef(Regex("\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\s*(USD|EUR|GBP|BRL|dollars?|euros?|pounds?|reais)\\b", RegexOption.IGNORE_CASE), EntityType.MONEY, 0.85f)
    )

    /**
     * Percent patterns
     */
    private val PERCENT_PATTERNS = listOf(
        // Standard: 50%, 25.5%, -10%
        PatternDef(Regex("-?\\d{1,3}(?:\\.\\d{1,2})?%\\b"), EntityType.PERCENT, 0.95f),

        // Written: 50 percent
        PatternDef(Regex("\\b\\d{1,3}(?:\\.\\d{1,2})?\\s*percent\\b", RegexOption.IGNORE_CASE), EntityType.PERCENT, 0.9f)
    )

    /**
     * Organization suffixes
     */
    private val ORG_SUFFIXES = listOf(
        "Inc", "LLC", "Ltd", "Corp", "Corporation", "Company", "Co",
        "Foundation", "Institute", "University", "College", "School",
        "Hospital", "Bank", "Group", "Association", "Society"
    )

    /**
     * Location prepositions
     */
    private val LOCATION_PREPOSITIONS = setOf("in", "at", "from", "to", "near", "by")

    /**
     * Extract all entities from text.
     */
    fun extract(text: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Extract pattern-based entities
        entities.addAll(extractByPatterns(text, DATE_PATTERNS))
        entities.addAll(extractByPatterns(text, TIME_PATTERNS))
        entities.addAll(extractByPattern(text, EMAIL_PATTERN))
        entities.addAll(extractByPatterns(text, PHONE_PATTERNS))
        entities.addAll(extractByPattern(text, URL_PATTERN))
        entities.addAll(extractByPatterns(text, MONEY_PATTERNS))
        entities.addAll(extractByPatterns(text, PERCENT_PATTERNS))

        // Extract organization names
        entities.addAll(extractOrganizations(text))

        // Extract potential person names
        entities.addAll(extractPersonNames(text))

        // Extract locations
        entities.addAll(extractLocations(text))

        // Deduplicate
        return deduplicateEntities(entities)
    }

    /**
     * Extract entities using a list of patterns.
     */
    private fun extractByPatterns(text: String, patterns: List<PatternDef>): List<ExtractedEntity> {
        val results = mutableListOf<ExtractedEntity>()

        for (patternDef in patterns) {
            results.addAll(extractByPattern(text, patternDef))
        }

        return results
    }

    /**
     * Extract entities using a single pattern.
     */
    private fun extractByPattern(text: String, patternDef: PatternDef): List<ExtractedEntity> {
        return patternDef.regex.findAll(text).map { match ->
            ExtractedEntity(
                text = match.value.trim(),
                type = patternDef.type,
                confidence = patternDef.confidence,
                startIndex = match.range.first,
                endIndex = match.range.last
            )
        }.toList()
    }

    /**
     * Extract organization names based on suffixes.
     */
    private fun extractOrganizations(text: String): List<ExtractedEntity> {
        val results = mutableListOf<ExtractedEntity>()

        for (suffix in ORG_SUFFIXES) {
            val pattern = Regex("\\b([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)*)\\s+$suffix\\b")
            pattern.findAll(text).forEach { match ->
                results.add(ExtractedEntity(
                    text = match.value.trim(),
                    type = EntityType.ORG,
                    confidence = 0.8f,
                    startIndex = match.range.first,
                    endIndex = match.range.last
                ))
            }
        }

        return results
    }

    /**
     * Extract potential person names (capitalized multi-word patterns).
     */
    private fun extractPersonNames(text: String): List<ExtractedEntity> {
        // Pattern: Two or more capitalized words together
        val namePattern = Regex("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\\b")

        return namePattern.findAll(text).mapNotNull { match ->
            val name = match.value
            val words = name.split(" ")

            // Filter out unlikely names
            if (words.size > 4) return@mapNotNull null  // Too many words
            if (words.any { it.length < 2 }) return@mapNotNull null  // Too short words

            // Check it's not a known pattern (date, org, etc)
            if (isLikelyDate(name) || isOrganization(name)) return@mapNotNull null

            ExtractedEntity(
                text = name,
                type = EntityType.PERSON,
                confidence = 0.6f,  // Lower confidence for person names
                startIndex = match.range.first,
                endIndex = match.range.last
            )
        }.toList()
    }

    /**
     * Extract locations based on preposition context.
     */
    private fun extractLocations(text: String): List<ExtractedEntity> {
        val results = mutableListOf<ExtractedEntity>()

        for (prep in LOCATION_PREPOSITIONS) {
            // Pattern: preposition + Capitalized location
            val pattern = Regex("\\b$prep\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+){0,2})\\b", RegexOption.IGNORE_CASE)
            pattern.findAll(text).forEach { match ->
                val location = match.groupValues[1]
                if (location.isNotBlank() && location.first().isUpperCase()) {
                    results.add(ExtractedEntity(
                        text = location,
                        type = EntityType.LOCATION,
                        confidence = 0.7f,
                        startIndex = match.range.first,
                        endIndex = match.range.last
                    ))
                }
            }
        }

        return results
    }

    /**
     * Check if text is likely a date.
     */
    private fun isLikelyDate(text: String): Boolean {
        val dateWords = setOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        )
        return dateWords.any { text.lowercase().contains(it) }
    }

    /**
     * Check if text is likely an organization.
     */
    private fun isOrganization(text: String): Boolean {
        return ORG_SUFFIXES.any { text.endsWith(it) }
    }

    /**
     * Deduplicate overlapping entities, preferring higher confidence.
     */
    private fun deduplicateEntities(entities: List<ExtractedEntity>): List<ExtractedEntity> {
        val sorted = entities.sortedByDescending { it.confidence }
        val result = mutableListOf<ExtractedEntity>()
        val usedRanges = mutableListOf<IntRange>()

        for (entity in sorted) {
            val range = entity.startIndex..entity.endIndex
            val overlaps = usedRanges.any { it.overlaps(range) }

            if (!overlaps) {
                result.add(entity)
                usedRanges.add(range)
            }
        }

        return result
    }

    /**
     * Check if two ranges overlap.
     */
    private fun IntRange.overlaps(other: IntRange): Boolean {
        return this.first <= other.last && other.first <= this.last
    }

    private data class PatternDef(
        val regex: Regex,
        val type: EntityType,
        val confidence: Float
    )
}
