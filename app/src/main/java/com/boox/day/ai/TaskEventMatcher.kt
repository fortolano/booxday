package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Task
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ocr.semantic.model.SemanticProfile

/**
 * Detects semantic relationships between tasks and calendar events.
 * Used by Plan My Day to provide context about task-event associations.
 */
class TaskEventMatcher(private val context: Context) {

    companion object {
        private const val TAG = "TaskEventMatcher"

        // Relationship types
        const val REL_PREP = "prep"           // Task prepares for event
        const val REL_FOLLOWUP = "follow-up"  // Task follows up after event
        const val REL_RELATED = "related"     // General relationship
    }

    private val semanticService by lazy { SemanticGeniusService(context) }

    /**
     * Represents a detected relationship between a task and a calendar event.
     */
    data class TaskEventRelation(
        val taskId: String,
        val taskText: String,
        val eventId: String,
        val eventTitle: String,
        val affinity: Float,          // 0.0-1.0, higher = stronger match
        val relationshipType: String  // REL_PREP, REL_FOLLOWUP, or REL_RELATED
    )

    /**
     * Find relationships between tasks and events based on text similarity
     * and semantic domain matching.
     *
     * @param tasks List of tasks to match
     * @param events List of events to match against
     * @param threshold Minimum affinity score (0.0-1.0) to include in results
     * @return List of detected relationships, sorted by affinity (highest first)
     */
    suspend fun findRelationships(
        tasks: List<Task>,
        events: List<LocalEvent>,
        threshold: Float = 0.4f,
        taskTextOverrides: Map<String, String> = emptyMap()
    ): List<TaskEventRelation> {
        if (tasks.isEmpty() || events.isEmpty()) {
            return emptyList()
        }

        Log.d(TAG, "Finding relationships: ${tasks.size} tasks, ${events.size} events")

        val relations = mutableListOf<TaskEventRelation>()

        for (event in events) {
            // Try to get semantic profile for the event (if indexed)
            val eventProfile = try {
                semanticService.getEventProfileIfExists(event.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get event profile for ${event.id}: ${e.message}")
                null
            }

            for (task in tasks) {
                if (task.isCompleted) continue

                val taskText = taskTextOverrides[task.id]?.takeIf { it.isNotBlank() }
                    ?: task.typedText
                    ?: task.recognizedText
                    ?: task.convertedText
                    ?: continue

                if (taskText.isBlank()) continue

                // Calculate affinity score
                val affinity = calculateAffinity(taskText, event.title, event.description, eventProfile)

                if (affinity >= threshold) {
                    relations.add(TaskEventRelation(
                        taskId = task.id,
                        taskText = taskText.take(60),
                        eventId = event.id,
                        eventTitle = event.title,
                        affinity = affinity,
                        relationshipType = inferRelationType(taskText, event.title)
                    ))
                }
            }
        }

        val result = relations
            .sortedByDescending { it.affinity }
            .take(10)  // Limit to top 10 relationships

        Log.d(TAG, "Found ${result.size} relationships above threshold $threshold")
        return result
    }

    /**
     * Calculate affinity score between a task and an event.
     * Uses keyword overlap + domain matching + prep/followup keyword boost.
     */
    private fun calculateAffinity(
        taskText: String,
        eventTitle: String,
        eventDescription: String?,
        eventProfile: SemanticProfile?
    ): Float {
        val taskLower = taskText.lowercase()
        val taskWords = extractSignificantWords(taskLower)

        // Combine event title and description for matching
        val eventTextLower = buildString {
            append(eventTitle.lowercase())
            if (!eventDescription.isNullOrBlank()) {
                append(" ")
                append(eventDescription.lowercase())
            }
        }
        val eventWords = extractSignificantWords(eventTextLower)

        if (taskWords.isEmpty() || eventWords.isEmpty()) {
            return 0f
        }

        // 1. Keyword overlap score (Jaccard-like)
        val overlap = taskWords.intersect(eventWords).size
        val union = taskWords.union(eventWords).size
        val keywordScore = if (union > 0) overlap.toFloat() / union else 0f

        // 2. Domain match boost from semantic profile
        var domainBoost = 0f
        eventProfile?.let { profile ->
            val domain = profile.domain
            if (domain != null) {
                // Check if task text contains domain-related keywords
                val domainName = domain.name.lowercase()
                if (taskLower.contains(domainName)) {
                    domainBoost = 0.15f
                }
                // Also check key phrases from profile
                profile.keyPhrases.forEach { phrase ->
                    if (taskLower.contains(phrase.lowercase())) {
                        domainBoost = maxOf(domainBoost, 0.1f)
                    }
                }
            }
        }

        // 3. Prep/followup keyword boost
        val actionBoost = if (containsPrepKeyword(taskLower) || containsFollowupKeyword(taskLower)) {
            0.1f
        } else {
            0f
        }

        // Combine scores (weighted)
        val finalScore = (keywordScore * 0.7f) + domainBoost + actionBoost

        return finalScore.coerceIn(0f, 1.0f)
    }

    /**
     * Extract significant words from text (filtering out stop words).
     */
    private fun extractSignificantWords(text: String): Set<String> {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "can", "this",
            "that", "these", "those", "my", "your", "his", "her", "its", "our",
            "their", "i", "you", "he", "she", "it", "we", "they", "what", "which"
        )

        return text
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    /**
     * Infer the relationship type based on task text patterns.
     */
    private fun inferRelationType(taskText: String, eventTitle: String): String {
        val taskLower = taskText.lowercase()

        return when {
            containsPrepKeyword(taskLower) -> REL_PREP
            containsFollowupKeyword(taskLower) -> REL_FOLLOWUP
            else -> REL_RELATED
        }
    }

    private fun containsPrepKeyword(text: String): Boolean {
        val prepKeywords = listOf(
            "prepare", "prep", "review", "draft", "write", "create",
            "gather", "research", "outline", "plan", "setup", "set up",
            "before", "ahead of", "in advance", "get ready"
        )
        return prepKeywords.any { text.contains(it) }
    }

    private fun containsFollowupKeyword(text: String): Boolean {
        val followupKeywords = listOf(
            "follow up", "followup", "follow-up", "send", "update",
            "summarize", "notes", "recap", "action items", "after",
            "debrief", "document", "share", "distribute"
        )
        return followupKeywords.any { text.contains(it) }
    }
}
