package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.KeywordEntry
import kotlin.math.ln

/**
 * Steps 3-4: Keyword extraction with TF-IDF scoring.
 *
 * Features:
 * - Tokenization with stopword removal
 * - Position-based weighting boosts
 * - TF-IDF scoring
 * - Returns top 12-25 keywords
 */
object KeywordExtractor {

    /**
     * Minimum keyword length
     */
    private const val MIN_KEYWORD_LENGTH = 3

    /**
     * Maximum keywords to return
     */
    private const val MAX_KEYWORDS = 25

    /**
     * Minimum keywords to return
     */
    private const val MIN_KEYWORDS = 12

    /**
     * Weighting boost for keywords in title/header
     */
    private const val TITLE_BOOST = 0.40f

    /**
     * Weighting boost for keywords near dates
     */
    private const val DATE_PROXIMITY_BOOST = 0.25f

    /**
     * Weighting boost for keywords in bullet points
     */
    private const val BULLET_BOOST = 0.20f

    /**
     * Weighting boost for keywords in first 15% of text
     */
    private const val EARLY_POSITION_BOOST = 0.15f

    /**
     * English stopwords
     */
    private val ENGLISH_STOPWORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with", "this", "but", "they",
        "have", "had", "what", "when", "where", "who", "which", "why", "how",
        "all", "each", "every", "both", "few", "more", "most", "other", "some",
        "such", "no", "not", "only", "own", "same", "so", "than", "too",
        "very", "can", "just", "should", "now", "also", "been", "being",
        "do", "does", "did", "doing", "would", "could", "might", "must",
        "shall", "may", "need", "dare", "ought", "used", "get", "got",
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
        "you", "your", "yours", "yourself", "yourselves", "him", "his",
        "himself", "she", "her", "hers", "herself", "them", "their",
        "theirs", "themselves", "if", "then", "else", "about", "after",
        "before", "above", "below", "up", "down", "out", "off", "over",
        "under", "again", "further", "once", "here", "there", "any",
        "into", "through", "during", "until", "while", "because"
    )

    /**
     * Portuguese stopwords
     */
    private val PORTUGUESE_STOPWORDS = setOf(
        "a", "o", "e", "de", "da", "do", "que", "em", "um", "uma",
        "para", "com", "não", "uma", "os", "no", "se", "na", "por",
        "mais", "as", "dos", "como", "mas", "foi", "ao", "ele", "das",
        "tem", "à", "seu", "sua", "ou", "ser", "quando", "muito",
        "há", "nos", "já", "está", "eu", "também", "só", "pelo", "pela",
        "até", "isso", "ela", "entre", "era", "depois", "sem", "mesmo",
        "aos", "ter", "seus", "quem", "nas", "me", "esse", "eles",
        "estão", "você", "tinha", "foram", "essa", "num", "nem", "suas",
        "meu", "às", "minha", "têm", "numa", "pelos", "elas", "havia"
    )

    private val ALL_STOPWORDS = ENGLISH_STOPWORDS + PORTUGUESE_STOPWORDS

    /**
     * Date-related terms to identify date proximity
     */
    private val DATE_TERMS = setOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "mon", "tue", "wed", "thu", "fri", "sat", "sun",
        "today", "tomorrow", "yesterday", "week", "month", "year"
    )

    /**
     * Extract keywords with TF-IDF scoring.
     *
     * @param text Clean text
     * @param title Optional source title (for title boost)
     * @param dateProximityWords Words found near dates (for date boost)
     * @return List of keyword entries with scores
     */
    fun extract(
        text: String,
        title: String? = null,
        dateProximityWords: Set<String> = emptySet()
    ): List<KeywordEntry> {
        // Tokenize
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()

        // Calculate term frequencies
        val termFrequencies = tokens.groupingBy { it }.eachCount()

        // Calculate document frequency (IDF proxy - using position distribution)
        val documentLength = tokens.size.toFloat()

        // Calculate TF-IDF scores with boosts
        val titleTokens = title?.let { tokenize(it).toSet() } ?: emptySet()
        val earlyThreshold = (documentLength * 0.15).toInt()
        val bulletLines = extractBulletLines(text)
        val bulletTokens = bulletLines.flatMap { tokenize(it) }.toSet()

        val scoredKeywords = termFrequencies.mapNotNull { (term, frequency) ->
            // Skip stopwords
            if (term.lowercase() in ALL_STOPWORDS) return@mapNotNull null

            // Skip too short terms
            if (term.length < MIN_KEYWORD_LENGTH) return@mapNotNull null

            // Skip pure numbers
            if (term.all { it.isDigit() }) return@mapNotNull null

            // Calculate TF (log-normalized)
            val tf = 1 + ln(frequency.toFloat())

            // Calculate IDF proxy (inverse of how common the word is)
            val idf = ln(documentLength / (frequency + 1))

            // Base score
            var score = tf * idf
            val boosts = mutableListOf<String>()

            // Apply boosts
            if (term in titleTokens) {
                score *= (1 + TITLE_BOOST)
                boosts.add("title")
            }

            if (term.lowercase() in dateProximityWords.map { it.lowercase() }) {
                score *= (1 + DATE_PROXIMITY_BOOST)
                boosts.add("date_proximity")
            }

            if (term in bulletTokens) {
                score *= (1 + BULLET_BOOST)
                boosts.add("bullet")
            }

            // Check if term appears in first 15%
            val firstOccurrence = tokens.indexOf(term)
            if (firstOccurrence < earlyThreshold) {
                score *= (1 + EARLY_POSITION_BOOST)
                boosts.add("early_position")
            }

            KeywordEntry(
                term = term,
                score = score,
                frequency = frequency,
                boostedBy = boosts
            )
        }

        // Sort by score and take top keywords
        return scoredKeywords
            .sortedByDescending { it.score }
            .take(MAX_KEYWORDS)
            .let { keywords ->
                // Ensure at least MIN_KEYWORDS if available
                if (keywords.size < MIN_KEYWORDS && scoredKeywords.size > keywords.size) {
                    scoredKeywords.sortedByDescending { it.score }.take(MIN_KEYWORDS)
                } else {
                    keywords
                }
            }
    }

    /**
     * Tokenize text into words.
     */
    private fun tokenize(text: String): List<String> {
        return text
            .split(Regex("[\\s.,!?;:()\\[\\]{}\"'\\-—–/\\\\|]+"))
            .filter { it.isNotBlank() && it.length >= MIN_KEYWORD_LENGTH }
            .map { it.trim() }
    }

    /**
     * Extract bullet point lines from text.
     */
    private fun extractBulletLines(text: String): List<String> {
        val bulletPattern = Regex("^[•●○▪▸►◆◇→\\-*]\\s.*", RegexOption.MULTILINE)
        val numberedPattern = Regex("^\\d+[.):]\\s.*", RegexOption.MULTILINE)
        val checkboxPattern = Regex("^\\[[ xX]\\]\\s.*", RegexOption.MULTILINE)

        return bulletPattern.findAll(text).map { it.value }.toList() +
               numberedPattern.findAll(text).map { it.value }.toList() +
               checkboxPattern.findAll(text).map { it.value }.toList()
    }

    /**
     * Find words near date-like terms.
     */
    fun findWordsNearDates(text: String): Set<String> {
        val words = tokenize(text)
        val nearDateWords = mutableSetOf<String>()

        words.forEachIndexed { index, word ->
            if (word.lowercase() in DATE_TERMS || word.matches(Regex("\\d{1,2}/\\d{1,2}(/\\d{2,4})?"))) {
                // Add words within 3 positions
                for (offset in -3..3) {
                    val neighborIndex = index + offset
                    if (neighborIndex in words.indices && neighborIndex != index) {
                        nearDateWords.add(words[neighborIndex])
                    }
                }
            }
        }

        return nearDateWords
    }
}
