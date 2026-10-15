package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.boox.day.ocr.model.ActionItem
import com.boox.day.ocr.model.DocumentStyle
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.IntentEntry
import com.boox.day.ocr.model.KeywordEntry
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.TemporalSignals
import com.boox.day.ocr.model.TopicEntry
import com.boox.day.ocr.semantic.model.SemanticProfile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

@Entity(
    tableName = "boox_note_semantic_aggregate",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["rebuiltAt"])
    ]
)
data class BooxNoteSemanticAggregate(
    @PrimaryKey
    val noteUniqueId: String,
    val samplePageIdsJson: String,
    val samplePageCount: Int,
    val sourceModifiedAt: Long,
    val configHashUsed: String,
    val aggregateSynopsis: String? = null,
    val docStyle: String? = null,
    val keywordsJson: String? = null,
    val keyPhrasesJson: String? = null,
    val keySentencesJson: String? = null,
    val entitiesJson: String? = null,
    val actionItemsJson: String? = null,
    val temporalSignalsJson: String? = null,
    val topicsJson: String? = null,
    val domain: String? = null,
    val domainConfidence: Float = 0f,
    val intentsJson: String? = null,
    val subdomainHint: String? = null,
    val wordCount: Int = 0,
    val confidence: Float = 0f,
    val coverageFlagsJson: String? = null,
    val aggregateVersion: Int = BooxNoteSemanticAggregateBuilder.AGGREGATE_VERSION,
    val rebuiltAt: Long = System.currentTimeMillis()
) {
    fun expectedSamplePageIds(): List<String> = decodeStringList(samplePageIdsJson)

    fun coverageFlags(): List<String> = decodeStringList(coverageFlagsJson)

    fun toSemanticProfile(
        sourceTitle: String?,
        sourceDate: String?
    ): SemanticProfile {
        return SemanticProfile(
            sourceType = OcrSource.BOOX_NOTE,
            sourceId = noteUniqueId,
            pageId = PAGE_ID_AGGREGATE,
            domain = domain?.let { Domain.fromString(it) },
            domainConfidence = domainConfidence,
            intents = decodeList(intentsJson),
            subdomainHint = subdomainHint,
            docStyle = docStyle?.let { DocumentStyle.fromString(it) },
            keywords = decodeList(keywordsJson),
            keyPhrases = decodeStringList(keyPhrasesJson),
            keySentences = decodeStringList(keySentencesJson),
            entities = decodeList(entitiesJson),
            actionItems = decodeList(actionItemsJson),
            temporalSignals = temporalSignalsJson?.let { decodeValue<TemporalSignals>(it) },
            synopsis = aggregateSynopsis,
            sourceDate = sourceDate,
            sourceTitle = sourceTitle,
            wordCount = wordCount,
            confidence = confidence
        )
    }

    companion object {
        const val PAGE_ID_AGGREGATE = "__note_aggregate__"

        private val json = Json { ignoreUnknownKeys = true }

        private inline fun <reified T> decodeList(value: String?): List<T> {
            if (value.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString<List<T>>(value)
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun decodeStringList(value: String?): List<String> {
            if (value.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString<List<String>>(value)
            } catch (_: Exception) {
                emptyList()
            }
        }

        private inline fun <reified T> decodeValue(value: String): T? {
            return try {
                json.decodeFromString<T>(value)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Dao
interface BooxNoteSemanticAggregateDao {
    @Query("SELECT * FROM boox_note_semantic_aggregate WHERE noteUniqueId = :noteId")
    suspend fun getAggregate(noteId: String): BooxNoteSemanticAggregate?

    @Query("SELECT * FROM boox_note_semantic_aggregate WHERE noteUniqueId IN (:noteIds)")
    suspend fun getAggregates(noteIds: List<String>): List<BooxNoteSemanticAggregate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: BooxNoteSemanticAggregate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(aggregates: List<BooxNoteSemanticAggregate>)

    @Query("DELETE FROM boox_note_semantic_aggregate WHERE noteUniqueId = :noteId")
    suspend fun deleteAggregate(noteId: String)

    @Query("DELETE FROM boox_note_semantic_aggregate WHERE noteUniqueId IN (:noteIds)")
    suspend fun deleteAggregates(noteIds: List<String>)

    @Query("DELETE FROM boox_note_semantic_aggregate")
    suspend fun deleteAll()
}

object BooxNoteSemanticAggregateBuilder {
    const val AGGREGATE_VERSION = 1

    private const val MAX_KEYWORDS = 25
    private const val MAX_KEY_PHRASES = 20
    private const val MAX_KEY_SENTENCES = 10
    private const val MAX_ENTITIES = 30
    private const val MAX_ACTION_ITEMS = 15
    private const val MAX_TOPICS = 6
    private const val MAX_INTENTS = 4
    private const val MAX_SYNOPSIS_CHARS = 900

    private val json = Json { ignoreUnknownKeys = true }

    fun build(
        note: BooxNoteIndex,
        eligibleSamplePageIds: List<String>,
        orderedProfiles: List<SemanticProfile>,
        configHash: String
    ): BooxNoteSemanticAggregate {
        require(orderedProfiles.isNotEmpty()) { "orderedProfiles must not be empty" }

        val mergedDomain = mergeDomain(orderedProfiles)
        val mergedIntents = mergeIntents(orderedProfiles)
        val mergedDocStyle = mergeDocStyle(orderedProfiles)
        val mergedKeywords = mergeKeywords(orderedProfiles)
        val mergedKeyPhrases = mergeOrderedStrings(
            values = orderedProfiles.flatMap { it.keyPhrases },
            maxItems = MAX_KEY_PHRASES
        )
        val mergedKeySentences = mergeOrderedStrings(
            values = orderedProfiles.flatMap { it.keySentences },
            maxItems = MAX_KEY_SENTENCES
        )
        val mergedEntities = mergeEntities(orderedProfiles)
        val mergedActionItems = mergeActionItems(orderedProfiles)
        val mergedTemporalSignals = mergeTemporalSignals(orderedProfiles)
        val mergedTopics = mergeTopics(orderedProfiles)
        val mergedSynopsis = mergeSynopsis(orderedProfiles)
        val mergedSubdomainHint = mergeSubdomainHint(orderedProfiles, mergedDomain.first)
        val totalWordCount = orderedProfiles.sumOf { it.wordCount }
        val mergedConfidence = weightedAverage(
            orderedProfiles.map { profile -> max(profile.wordCount, 1) to profile.confidence }
        )
        val coverageFlags = buildCoverageFlags(
            notePageCount = note.pageCount,
            eligibleSamplePageIds = eligibleSamplePageIds,
            usedProfileCount = orderedProfiles.size
        )

        return BooxNoteSemanticAggregate(
            noteUniqueId = note.uniqueId,
            samplePageIdsJson = json.encodeToString(eligibleSamplePageIds),
            samplePageCount = orderedProfiles.size,
            sourceModifiedAt = note.shapeFileModified,
            configHashUsed = configHash,
            aggregateSynopsis = mergedSynopsis,
            docStyle = mergedDocStyle?.name,
            keywordsJson = mergedKeywords.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            keyPhrasesJson = mergedKeyPhrases.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            keySentencesJson = mergedKeySentences.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            entitiesJson = mergedEntities.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            actionItemsJson = mergedActionItems.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            temporalSignalsJson = mergedTemporalSignals?.let { json.encodeToString(it) },
            topicsJson = mergedTopics.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            domain = mergedDomain.first?.name,
            domainConfidence = mergedDomain.second,
            intentsJson = mergedIntents.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            subdomainHint = mergedSubdomainHint,
            wordCount = totalWordCount,
            confidence = mergedConfidence,
            coverageFlagsJson = coverageFlags.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            aggregateVersion = AGGREGATE_VERSION,
            rebuiltAt = System.currentTimeMillis()
        )
    }

    private fun mergeDomain(profiles: List<SemanticProfile>): Pair<Domain?, Float> {
        val groups = profiles
            .filter { it.domain != null }
            .groupBy { it.domain!! }
            .mapValues { (_, sameDomainProfiles) ->
                sameDomainProfiles.sumOf { profile ->
                    (profile.domainConfidence * max(profile.wordCount, 1)).toDouble()
                }
            }

        val bestDomain = groups.maxByOrNull { it.value }?.key ?: return null to 0f
        val bestProfiles = profiles.filter { it.domain == bestDomain }
        val confidence = weightedAverage(
            bestProfiles.map { profile -> max(profile.wordCount, 1) to profile.domainConfidence }
        )
        return bestDomain to confidence
    }

    private fun mergeIntents(profiles: List<SemanticProfile>): List<IntentEntry> {
        return profiles
            .flatMap { it.intents }
            .groupBy { it.intent }
            .map { (intent, entries) ->
                val combinedConfidence = entries.fold(1f) { remaining, entry ->
                    remaining * (1f - entry.confidence.coerceIn(0f, 1f))
                }
                IntentEntry(intent, (1f - combinedConfidence).coerceIn(0f, 1f))
            }
            .sortedByDescending { it.confidence }
            .take(MAX_INTENTS)
    }

    private fun mergeDocStyle(profiles: List<SemanticProfile>): DocumentStyle? {
        return profiles
            .mapNotNull { profile -> profile.docStyle?.let { it to max(profile.wordCount, 1) } }
            .groupBy({ it.first }, { it.second })
            .maxByOrNull { (_, weights) -> weights.sum() }
            ?.key
    }

    private fun mergeKeywords(profiles: List<SemanticProfile>): List<KeywordEntry> {
        data class KeywordAccumulator(
            var displayTerm: String,
            var totalScore: Float,
            var totalFrequency: Int,
            val boostedBy: MutableSet<String>
        )

        val merged = linkedMapOf<String, KeywordAccumulator>()
        profiles.forEach { profile ->
            profile.keywords.forEach { keyword ->
                val key = normalizeText(keyword.term)
                if (key.isEmpty()) return@forEach
                val accumulator = merged.getOrPut(key) {
                    KeywordAccumulator(
                        displayTerm = keyword.term,
                        totalScore = 0f,
                        totalFrequency = 0,
                        boostedBy = linkedSetOf()
                    )
                }
                if (keyword.score > accumulator.totalScore || keyword.term.length > accumulator.displayTerm.length) {
                    accumulator.displayTerm = keyword.term
                }
                accumulator.totalScore += keyword.score
                accumulator.totalFrequency += max(keyword.frequency, 1)
                accumulator.boostedBy.addAll(keyword.boostedBy)
            }
        }

        return merged.values
            .map { accumulator ->
                KeywordEntry(
                    term = accumulator.displayTerm,
                    score = accumulator.totalScore,
                    frequency = accumulator.totalFrequency,
                    boostedBy = accumulator.boostedBy.toList()
                )
            }
            .sortedWith(compareByDescending<KeywordEntry> { it.score }.thenByDescending { it.frequency }.thenBy { it.term.lowercase() })
            .take(MAX_KEYWORDS)
    }

    private fun mergeEntities(profiles: List<SemanticProfile>): List<ExtractedEntity> {
        data class EntityAccumulator(
            var bestText: String,
            val type: com.boox.day.ocr.model.EntityType,
            var bestConfidence: Float
        )

        val merged = linkedMapOf<String, EntityAccumulator>()
        profiles.forEach { profile ->
            profile.entities.forEach { entity ->
                val key = "${entity.type.name}:${normalizeText(entity.text)}"
                if (key.endsWith(":")) return@forEach
                val accumulator = merged[key]
                if (accumulator == null) {
                    merged[key] = EntityAccumulator(entity.text, entity.type, entity.confidence)
                } else if (entity.confidence > accumulator.bestConfidence || entity.text.length > accumulator.bestText.length) {
                    accumulator.bestText = entity.text
                    accumulator.bestConfidence = max(accumulator.bestConfidence, entity.confidence)
                }
            }
        }

        return merged.values
            .map { accumulator ->
                ExtractedEntity(
                    text = accumulator.bestText,
                    type = accumulator.type,
                    confidence = accumulator.bestConfidence
                )
            }
            .sortedWith(compareByDescending<ExtractedEntity> { it.confidence }.thenBy { it.text.lowercase() })
            .take(MAX_ENTITIES)
    }

    private fun mergeActionItems(profiles: List<SemanticProfile>): List<ActionItem> {
        val merged = linkedMapOf<String, ActionItem>()
        profiles.forEach { profile ->
            profile.actionItems.forEach { actionItem ->
                val key = listOf(
                    normalizeText(actionItem.verb),
                    normalizeText(actionItem.objectText),
                    normalizeText(actionItem.dueDateCandidate),
                    normalizeText(actionItem.assigneeCandidate)
                ).joinToString("|")
                if (key.isBlank()) return@forEach
                val existing = merged[key]
                if (existing == null || actionItem.confidence > existing.confidence) {
                    merged[key] = actionItem
                }
            }
        }

        return merged.values
            .sortedWith(compareByDescending<ActionItem> { it.confidence }.thenBy { it.verb.lowercase() }.thenBy { it.objectText.lowercase() })
            .take(MAX_ACTION_ITEMS)
    }

    private fun mergeTemporalSignals(profiles: List<SemanticProfile>): TemporalSignals? {
        val temporalSignals = profiles.mapNotNull { it.temporalSignals }
        if (temporalSignals.isEmpty()) return null

        val dominantDateCandidate = temporalSignals
            .mapNotNull { it.dominantDateCandidate?.takeIf { value -> value.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return TemporalSignals(
            explicitDates = mergeOrderedStrings(temporalSignals.flatMap { it.explicitDates }, maxItems = 20),
            explicitTimes = mergeOrderedStrings(temporalSignals.flatMap { it.explicitTimes }, maxItems = 20),
            relativeDates = mergeOrderedStrings(temporalSignals.flatMap { it.relativeDates }, maxItems = 20),
            weekdays = mergeOrderedStrings(temporalSignals.flatMap { it.weekdays }, maxItems = 7),
            dateRanges = mergeOrderedStrings(temporalSignals.flatMap { it.dateRanges }, maxItems = 12),
            dominantDateCandidate = dominantDateCandidate
        )
    }

    private fun mergeTopics(profiles: List<SemanticProfile>): List<TopicEntry> {
        // SemanticProfile does not currently expose topic entries, so topics remain
        // reserved storage for future aggregate versions. Matching does not depend on them.
        return emptyList()
    }

    private fun mergeSynopsis(profiles: List<SemanticProfile>): String? {
        val merged = mergeOrderedStrings(
            values = profiles.mapNotNull { it.synopsis?.trim().takeIf { value -> !value.isNullOrBlank() } },
            maxItems = profiles.size
        )
        if (merged.isEmpty()) return null

        return merged.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SYNOPSIS_CHARS)
    }

    private fun mergeSubdomainHint(
        profiles: List<SemanticProfile>,
        selectedDomain: Domain?
    ): String? {
        return profiles
            .filter { selectedDomain == null || it.domain == selectedDomain }
            .mapNotNull { it.subdomainHint?.trim()?.takeIf { hint -> hint.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun buildCoverageFlags(
        notePageCount: Int,
        eligibleSamplePageIds: List<String>,
        usedProfileCount: Int
    ): List<String> {
        val flags = mutableListOf<String>()
        if (eligibleSamplePageIds.size > 1 && usedProfileCount == 1) {
            flags += "SINGLE_PAGE_COVERAGE"
        }
        if (usedProfileCount < eligibleSamplePageIds.size) {
            flags += "MISSING_SAMPLED_PAGE_OCR"
        }
        if (notePageCount > eligibleSamplePageIds.size) {
            flags += "TRUNCATED_BY_SAMPLE_LIMIT"
        }
        return flags
    }

    private fun mergeOrderedStrings(values: List<String>, maxItems: Int): List<String> {
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (value in values) {
            val normalized = normalizeText(value)
            if (normalized.isBlank() || !seen.add(normalized)) {
                continue
            }
            result += value.trim()
            if (result.size >= maxItems) {
                break
            }
        }

        return result
    }

    private fun weightedAverage(values: List<Pair<Int, Float>>): Float {
        val totalWeight = values.sumOf { it.first }.toFloat()
        if (totalWeight <= 0f) return 0f
        val weightedSum = values.sumOf { (weight, value) -> weight.toDouble() * value.toDouble() }
        return (weightedSum / totalWeight).toFloat().coerceIn(0f, 1f)
    }

    private fun normalizeText(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }
}
