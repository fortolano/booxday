package com.boox.day.data.db

import com.boox.day.ocr.model.DocumentStyle
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.EntityType
import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.model.IntentEntry
import com.boox.day.ocr.model.KeywordEntry
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.model.SemanticProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BooxNoteSemanticAggregateBuilderTest {

    @Test
    fun build_mergesEligibleSamplePagesIntoSingleProfile() {
        val note = note(pageCount = 5)
        val aggregate = BooxNoteSemanticAggregateBuilder.build(
            note = note,
            eligibleSamplePageIds = listOf("page-1", "page-2"),
            orderedProfiles = listOf(
                profile(
                    pageId = "page-1",
                    keyword = "roadmap",
                    person = "Alice Johnson",
                    synopsis = "Weekly roadmap review",
                    domainConfidence = 0.82f
                ),
                profile(
                    pageId = "page-2",
                    keyword = "milestone",
                    person = "Bob Smith",
                    synopsis = "Milestone planning and follow-up",
                    domainConfidence = 0.78f
                )
            ),
            configHash = "cfg-123"
        )

        val mergedProfile = aggregate.toSemanticProfile(
            sourceTitle = note.title,
            sourceDate = note.originalDate
        )

        assertEquals(listOf("page-1", "page-2"), aggregate.expectedSamplePageIds())
        assertEquals(2, aggregate.samplePageCount)
        assertEquals("cfg-123", aggregate.configHashUsed)
        assertTrue(aggregate.coverageFlags().contains("TRUNCATED_BY_SAMPLE_LIMIT"))
        assertEquals(OcrSource.BOOX_NOTE, mergedProfile.sourceType)
        assertEquals(note.uniqueId, mergedProfile.sourceId)
        assertEquals(2, mergedProfile.keywords.size)
        assertTrue(mergedProfile.keywords.any { it.term == "roadmap" })
        assertTrue(mergedProfile.keywords.any { it.term == "milestone" })
        assertTrue(mergedProfile.entities.any { it.text == "Alice Johnson" })
        assertTrue(mergedProfile.entities.any { it.text == "Bob Smith" })
        assertEquals(Domain.PROFESSIONAL, mergedProfile.domain)
        assertEquals(Intent.MEETING_NOTES, mergedProfile.intents.first().intent)
    }

    @Test
    fun build_marksPartialCoverageWhenSamplePagesAreMissing() {
        val note = note(pageCount = 2)
        val aggregate = BooxNoteSemanticAggregateBuilder.build(
            note = note,
            eligibleSamplePageIds = listOf("page-1", "page-2"),
            orderedProfiles = listOf(
                profile(
                    pageId = "page-1",
                    keyword = "agenda",
                    person = "Carol Lee",
                    synopsis = "Agenda prep"
                )
            ),
            configHash = "cfg-123"
        )

        assertEquals(1, aggregate.samplePageCount)
        assertTrue(aggregate.coverageFlags().contains("SINGLE_PAGE_COVERAGE"))
        assertTrue(aggregate.coverageFlags().contains("MISSING_SAMPLED_PAGE_OCR"))
    }

    private fun note(pageCount: Int): BooxNoteIndex {
        val pageIdsJson = buildString {
            append("[")
            append((1..pageCount).joinToString(",") { index -> "\"page-$index\"" })
            append("]")
        }
        return BooxNoteIndex(
            uniqueId = "note-1",
            title = "Project Sync",
            originalDate = "2026-03-06",
            originalTimestamp = 1_700_000_000_000L,
            pageCount = pageCount,
            pageIdsJson = pageIdsJson,
            shapeFileModified = 1_700_000_001_000L,
            indexedAt = 1_700_000_001_500L
        )
    }

    private fun profile(
        pageId: String,
        keyword: String,
        person: String,
        synopsis: String,
        domainConfidence: Float = 0.75f
    ): SemanticProfile {
        return SemanticProfile(
            sourceType = OcrSource.BOOX_NOTE,
            sourceId = "note-1",
            pageId = pageId,
            domain = Domain.PROFESSIONAL,
            domainConfidence = domainConfidence,
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.8f)),
            subdomainHint = null,
            docStyle = DocumentStyle.MEETING_NOTES,
            keywords = listOf(KeywordEntry(keyword, 0.6f, frequency = 2)),
            keyPhrases = listOf("$keyword review"),
            keySentences = listOf("Discussed $keyword with $person"),
            entities = listOf(ExtractedEntity(person, EntityType.PERSON, 0.9f)),
            actionItems = emptyList(),
            temporalSignals = null,
            synopsis = synopsis,
            sourceDate = "2026-03-06",
            sourceTitle = "Project Sync",
            wordCount = 120,
            confidence = 0.88f
        )
    }
}
