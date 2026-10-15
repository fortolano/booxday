package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ClassificationExplanation
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.semantic.classification.DomainIntentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Test utilities for SemanticGenius classification debugging.
 * Phase 9b - Debug Observability (Section 4.18.3).
 */
object TestUtils {

    /**
     * Assert that an event classifies to the expected domain and intents.
     * Logs the full classification explanation on assertion failure for debugging.
     *
     * Usage:
     * ```
     * assertEventClassification(
     *     classifier = classifier,
     *     eventTitle = "Q4 Budget Review Meeting",
     *     eventDescription = "Quarterly finance review with team",
     *     expectedDomain = Domain.FINANCE,
     *     expectedIntents = listOf(Intent.MEETING_NOTES)
     * )
     * ```
     *
     * @param classifier The DomainIntentClassifier instance (requires Android context)
     * @param eventTitle The event title to classify
     * @param eventDescription Optional event description
     * @param expectedDomain The expected domain classification
     * @param expectedIntents The expected intents (at least one must match)
     * @throws AssertionError if classification doesn't match, with full explanation logged
     */
    fun assertEventClassification(
        classifier: DomainIntentClassifier,
        eventTitle: String,
        eventDescription: String?,
        expectedDomain: Domain,
        expectedIntents: List<Intent>
    ) {
        // Normalize text using EventTextNormalizer (same as indexEvent)
        val cleanText = EventTextNormalizer.normalize(eventTitle, eventDescription)

        // Classify WITHOUT recording user priors (same as event indexing)
        val classification = classifier.classify(cleanText, recordPriors = false)

        // Assert domain with explanation on failure
        try {
            assertEquals(
                "Domain mismatch for event '$eventTitle'",
                expectedDomain,
                classification.domain
            )
        } catch (e: AssertionError) {
            // Log full explanation for debugging
            println("=== CLASSIFICATION FAILURE: Domain mismatch ===")
            println(classification.toLogString())
            throw e
        }

        // Assert at least one expected intent is present
        if (expectedIntents.isNotEmpty()) {
            val actualIntents = classification.intents.map { it.intent }
            val hasExpectedIntent = expectedIntents.any { it in actualIntents }

            try {
                assertTrue(
                    "Intent mismatch for event '$eventTitle'. Expected one of $expectedIntents, got $actualIntents",
                    hasExpectedIntent
                )
            } catch (e: AssertionError) {
                // Log full explanation for debugging
                println("=== CLASSIFICATION FAILURE: Intent mismatch ===")
                println(classification.toLogString())
                throw e
            }
        }
    }

    /**
     * Assert domain classification only (without intent checking).
     * Useful for testing domain classification in isolation.
     *
     * @param classifier The DomainIntentClassifier instance
     * @param eventTitle The event title to classify
     * @param eventDescription Optional event description
     * @param expectedDomain The expected domain classification
     */
    fun assertEventDomain(
        classifier: DomainIntentClassifier,
        eventTitle: String,
        eventDescription: String?,
        expectedDomain: Domain
    ) {
        assertEventClassification(
            classifier = classifier,
            eventTitle = eventTitle,
            eventDescription = eventDescription,
            expectedDomain = expectedDomain,
            expectedIntents = emptyList()
        )
    }

    /**
     * Get the classification explanation for an event without assertions.
     * Useful for debugging and exploring classification results.
     *
     * @param classifier The DomainIntentClassifier instance
     * @param eventTitle The event title to classify
     * @param eventDescription Optional event description
     * @return The full classification explanation
     */
    fun classifyEvent(
        classifier: DomainIntentClassifier,
        eventTitle: String,
        eventDescription: String?
    ): ClassificationExplanation {
        val cleanText = EventTextNormalizer.normalize(eventTitle, eventDescription)
        return classifier.classify(cleanText, recordPriors = false)
    }

    /**
     * Print the classification explanation for an event.
     * Useful for manual debugging during test development.
     *
     * @param classifier The DomainIntentClassifier instance
     * @param eventTitle The event title to classify
     * @param eventDescription Optional event description
     */
    fun debugPrintClassification(
        classifier: DomainIntentClassifier,
        eventTitle: String,
        eventDescription: String?
    ) {
        val explanation = classifyEvent(classifier, eventTitle, eventDescription)
        println("=== CLASSIFICATION DEBUG: '$eventTitle' ===")
        println(explanation.toLogString())
    }

    /**
     * Assert that classification confidence is above a threshold.
     * Useful for testing that confident classifications are being made.
     *
     * @param classifier The DomainIntentClassifier instance
     * @param eventTitle The event title to classify
     * @param eventDescription Optional event description
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     */
    fun assertConfidenceAbove(
        classifier: DomainIntentClassifier,
        eventTitle: String,
        eventDescription: String?,
        minConfidence: Float
    ) {
        val classification = classifyEvent(classifier, eventTitle, eventDescription)

        try {
            assertTrue(
                "Confidence ${classification.domainConfidence} below threshold $minConfidence for '$eventTitle'",
                classification.domainConfidence >= minConfidence
            )
        } catch (e: AssertionError) {
            println("=== CLASSIFICATION FAILURE: Low confidence ===")
            println(classification.toLogString())
            throw e
        }
    }
}
