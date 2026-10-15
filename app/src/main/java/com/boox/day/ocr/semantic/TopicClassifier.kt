package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ActionItem
import com.boox.day.ocr.model.KeywordEntry
import com.boox.day.ocr.model.Topic
import com.boox.day.ocr.model.TopicEntry

/**
 * Step 10: Topic classification.
 *
 * Taxonomy:
 * - MEETINGS, CALLS, TRAVEL, FINANCE, HEALTH, FAMILY
 * - WORK, SHOPPING, PROJECTS, REMINDERS, MISC
 *
 * Scores based on keyword presence and action verbs.
 * Returns top 3 topics with scores.
 */
object TopicClassifier {

    /**
     * Maximum topics to return
     */
    private const val MAX_TOPICS = 3

    /**
     * Topic keywords mapping
     */
    private val TOPIC_KEYWORDS = mapOf(
        Topic.MEETINGS to listOf(
            "meeting", "meetings", "meet", "attendees", "participants",
            "agenda", "minutes", "discussed", "presenter", "conference",
            "zoom", "teams", "call", "standup", "sync", "review",
            "workshop", "presentation", "demo"
        ),
        Topic.CALLS to listOf(
            "call", "phone", "dial", "ring", "voicemail", "callback",
            "conversation", "speak", "talk", "discuss", "contact"
        ),
        Topic.TRAVEL to listOf(
            "travel", "flight", "hotel", "booking", "reservation",
            "trip", "vacation", "airport", "train", "car", "rental",
            "passport", "visa", "luggage", "itinerary", "destination"
        ),
        Topic.FINANCE to listOf(
            "budget", "expense", "payment", "invoice", "bill",
            "salary", "tax", "money", "bank", "account", "transfer",
            "cost", "price", "purchase", "receipt", "reimbursement"
        ),
        Topic.HEALTH to listOf(
            "doctor", "appointment", "health", "medical", "hospital",
            "medicine", "prescription", "symptom", "exercise", "gym",
            "workout", "diet", "nutrition", "wellness", "checkup"
        ),
        Topic.FAMILY to listOf(
            "family", "kids", "children", "birthday", "anniversary",
            "wedding", "parent", "school", "homework", "pickup",
            "dinner", "breakfast", "lunch"
        ),
        Topic.WORK to listOf(
            "work", "office", "project", "deadline", "task",
            "client", "customer", "report", "email", "document",
            "review", "submit", "complete", "assign", "team"
        ),
        Topic.SHOPPING to listOf(
            "buy", "purchase", "shopping", "store", "order",
            "delivery", "amazon", "online", "groceries", "list",
            "gift", "return", "exchange", "cart", "checkout"
        ),
        Topic.PROJECTS to listOf(
            "project", "milestone", "sprint", "release", "launch",
            "development", "design", "implement", "feature", "bug",
            "test", "deploy", "version", "update", "roadmap"
        ),
        Topic.REMINDERS to listOf(
            "remind", "reminder", "remember", "forget", "note",
            "memo", "todo", "task", "important", "urgent", "asap"
        )
    )

    /**
     * Action verb to topic mapping (for action item boosting)
     */
    private val ACTION_VERB_TOPICS = mapOf(
        "call" to Topic.CALLS,
        "phone" to Topic.CALLS,
        "meet" to Topic.MEETINGS,
        "book" to Topic.TRAVEL,
        "fly" to Topic.TRAVEL,
        "buy" to Topic.SHOPPING,
        "order" to Topic.SHOPPING,
        "pay" to Topic.FINANCE,
        "exercise" to Topic.HEALTH,
        "pick" to Topic.FAMILY  // pick up kids
    )

    /**
     * Classify text into topics.
     *
     * @param text Original text
     * @param keywords Extracted keywords
     * @param actionItems Extracted action items
     * @return Top 3 topics with scores
     */
    fun classify(
        text: String,
        keywords: List<KeywordEntry>,
        actionItems: List<ActionItem>
    ): List<TopicEntry> {
        val lowerText = text.lowercase()
        val keywordTerms = keywords.map { it.term.lowercase() }.toSet()
        val scores = mutableMapOf<Topic, Float>()

        // Initialize all topics with zero score
        Topic.entries.forEach { scores[it] = 0f }

        // Score based on keyword presence in text
        for ((topic, topicKeywords) in TOPIC_KEYWORDS) {
            var score = 0f

            for (keyword in topicKeywords) {
                // Check if keyword appears in text
                if (keyword in lowerText) {
                    score += 1.0f

                    // Bonus if it's also an extracted keyword
                    if (keyword in keywordTerms) {
                        score += 0.5f
                    }
                }
            }

            scores[topic] = (scores[topic] ?: 0f) + score
        }

        // Boost based on action items
        for (item in actionItems) {
            val verb = item.verb.lowercase()
            ACTION_VERB_TOPICS[verb]?.let { topic ->
                scores[topic] = (scores[topic] ?: 0f) + 2.0f
            }
        }

        // Convert to entries and sort
        val entries = scores
            .filter { it.value > 0 }
            .map { TopicEntry(it.key, it.value) }
            .sortedByDescending { it.score }

        // Return top topics or MISC if nothing found
        return if (entries.isNotEmpty()) {
            entries.take(MAX_TOPICS)
        } else {
            listOf(TopicEntry(Topic.MISC, 0f))
        }
    }
}
