package com.boox.day.ocr.semantic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.*
import com.boox.day.ocr.semantic.model.SemanticProfile
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test for SemanticGeniusService affinity calculation.
 *
 * Tests the 9-component affinity algorithm against generated test data
 * pushed to the device via adb.
 *
 * Setup:
 * 1. Generate test data: python -m test_data_generator generate-pairs --high 50 --medium 30 --low 50
 * 2. Push to device: adb push fixtures/test_semantic.db /sdcard/Download/
 * 3. Run test: ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.boox.day.ocr.semantic.SemanticGeniusServiceTest
 */
@RunWith(AndroidJUnit4::class)
class SemanticGeniusServiceTest {

    private lateinit var context: Context
    private lateinit var service: SemanticGeniusService
    private lateinit var repository: UnifiedOcrRepository

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "SemanticGeniusTest"
        private const val TEST_DB_PATH = "/sdcard/Download/test_semantic.db"
        private const val TEST_RELATIONSHIPS_PATH = "/sdcard/Download/relationships.json"

        // Expected affinity ranges by relationship type
        private const val HIGH_MIN = 0.60f  // Tightened to target 0.60+
        private const val HIGH_MAX = 0.95f
        private const val MEDIUM_MIN = 0.15f  // Relaxed from 0.25
        private const val MEDIUM_MAX = 0.65f  // Relaxed from 0.50
        private const val LOW_MIN = 0.00f
        private const val LOW_MAX = 0.35f  // Relaxed from 0.20
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = UnifiedOcrRepository(context)
        service = SemanticGeniusService(context)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BASIC SANITY TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testServiceInstantiation() {
        assertNotNull("Service should be created", service)
    }

    @Test
    fun testAffinityCalculationWithEmptyProfiles() {
        val profile1 = createMinimalProfile("test1", Domain.PROFESSIONAL)
        val profile2 = createMinimalProfile("test2", Domain.PROFESSIONAL)

        val affinity = service.calculateSemanticAffinity(profile1, profile2)

        assertTrue("Affinity should be between 0 and 1", affinity in 0f..1f)
        Log.d(TAG, "Empty profiles affinity: $affinity")
    }

    @Test
    fun testDomainMatchContribution() {
        val profileA = createMinimalProfile("a", Domain.PROFESSIONAL)
        val profileB = createMinimalProfile("b", Domain.PROFESSIONAL)
        val profileC = createMinimalProfile("c", Domain.EDUCATION)

        val sameAffinity = service.calculateSemanticAffinity(profileA, profileB)
        val diffAffinity = service.calculateSemanticAffinity(profileA, profileC)

        Log.d(TAG, "Same domain affinity: $sameAffinity")
        Log.d(TAG, "Different domain affinity: $diffAffinity")

        assertTrue(
            "Same domain should score higher than different domain",
            sameAffinity >= diffAffinity
        )
    }

    @Test
    fun testSiblingDomainContribution() {
        val professional = createMinimalProfile("a", Domain.PROFESSIONAL)
        val business = createMinimalProfile("b", Domain.BUSINESS)  // Sibling
        val education = createMinimalProfile("c", Domain.EDUCATION)  // Non-sibling

        val siblingAffinity = service.calculateSemanticAffinity(professional, business)
        val nonSiblingAffinity = service.calculateSemanticAffinity(professional, education)

        Log.d(TAG, "Sibling domain affinity (PROFESSIONAL-BUSINESS): $siblingAffinity")
        Log.d(TAG, "Non-sibling domain affinity (PROFESSIONAL-EDUCATION): $nonSiblingAffinity")

        assertTrue(
            "Sibling domains should score higher",
            siblingAffinity >= nonSiblingAffinity
        )
    }

    @Test
    fun testKeywordOverlapContribution() {
        val withKeywords = createProfileWithKeywords(
            "a",
            Domain.PROFESSIONAL,
            listOf("meeting", "project", "deadline", "review")
        )
        val matchingKeywords = createProfileWithKeywords(
            "b",
            Domain.PROFESSIONAL,
            listOf("meeting", "project", "timeline", "update")  // 2 overlapping
        )
        val noKeywords = createMinimalProfile("c", Domain.PROFESSIONAL)

        val overlapAffinity = service.calculateSemanticAffinity(withKeywords, matchingKeywords)
        val noOverlapAffinity = service.calculateSemanticAffinity(withKeywords, noKeywords)

        Log.d(TAG, "Keyword overlap affinity: $overlapAffinity")
        Log.d(TAG, "No keyword overlap affinity: $noOverlapAffinity")

        assertTrue(
            "Keyword overlap should increase affinity",
            overlapAffinity > noOverlapAffinity
        )
    }

    @Test
    fun testPersonEntityContribution() {
        val withPerson = createProfileWithEntities(
            "a",
            Domain.PROFESSIONAL,
            listOf(ExtractedEntity("John Smith", EntityType.PERSON, 0.9f))
        )
        val matchingPerson = createProfileWithEntities(
            "b",
            Domain.PROFESSIONAL,
            listOf(ExtractedEntity("John Smith", EntityType.PERSON, 0.9f))
        )
        val differentPerson = createProfileWithEntities(
            "c",
            Domain.PROFESSIONAL,
            listOf(ExtractedEntity("Jane Doe", EntityType.PERSON, 0.9f))
        )

        val matchAffinity = service.calculateSemanticAffinity(withPerson, matchingPerson)
        val noMatchAffinity = service.calculateSemanticAffinity(withPerson, differentPerson)

        Log.d(TAG, "Person match affinity: $matchAffinity")
        Log.d(TAG, "Person no-match affinity: $noMatchAffinity")

        assertTrue(
            "Matching person should increase affinity",
            matchAffinity > noMatchAffinity
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIXTURE-BASED TESTS (require pushed test data)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun testWithGeneratedFixtures() {
        val testDbFile = File(TEST_DB_PATH)
        if (!testDbFile.exists()) {
            Log.w(TAG, "Test database not found at $TEST_DB_PATH - skipping fixture test")
            Log.w(TAG, "Generate with: python -m test_data_generator generate-database")
            Log.w(TAG, "Push with: adb push fixtures/test_database/test_semantic.db /sdcard/Download/")
            return
        }

        val db = SQLiteDatabase.openDatabase(TEST_DB_PATH, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val ocrProfiles = loadOcrProfiles(db)
            val eventProfiles = loadEventProfiles(db)

            Log.d(TAG, "Loaded ${ocrProfiles.size} OCR profiles, ${eventProfiles.size} event profiles")

            assertTrue("Should have OCR profiles", ocrProfiles.isNotEmpty())
            assertTrue("Should have event profiles", eventProfiles.isNotEmpty())

            // Test a sample of pairings
            var testedCount = 0
            val results = mutableListOf<Pair<Float, String>>()

            for (ocr in ocrProfiles.take(20)) {
                for (event in eventProfiles.take(10)) {
                    val affinity = service.calculateSemanticAffinity(ocr, event)
                    results.add(affinity to "${ocr.domain?.name} vs ${event.domain?.name}")
                    testedCount++
                }
            }

            Log.d(TAG, "Tested $testedCount pairs")
            Log.d(TAG, "Affinity range: ${results.minOfOrNull { it.first }} - ${results.maxOfOrNull { it.first }}")

            // Log distribution
            val highCount = results.count { it.first >= HIGH_MIN }
            val mediumCount = results.count { it.first >= MEDIUM_MIN && it.first < HIGH_MIN }
            val lowCount = results.count { it.first < MEDIUM_MIN }
            Log.d(TAG, "Distribution: High=$highCount, Medium=$mediumCount, Low=$lowCount")

        } finally {
            db.close()
        }
    }

    @Test
    fun testRelationshipValidation() {
        val testDbFile = File(TEST_DB_PATH)
        val relationshipsFile = File(TEST_RELATIONSHIPS_PATH)

        if (!testDbFile.exists() || !relationshipsFile.exists()) {
            Log.w(TAG, "Test files not found - skipping relationship validation")
            Log.w(TAG, "Generate: python -m test_data_generator generate-pairs --high 50 --medium 30 --low 50")
            Log.w(TAG, "Push: adb push fixtures/relationships.json /sdcard/Download/")
            return
        }

        val db = SQLiteDatabase.openDatabase(TEST_DB_PATH, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val ocrProfilesById = loadOcrProfiles(db).associateBy { "${it.sourceType.name}_${it.sourceId}_${it.pageId}" }
            val eventProfilesById = loadEventProfiles(db).associateBy { it.sourceId }

            val relationships = loadRelationships(relationshipsFile)
            Log.d(TAG, "Loaded ${relationships.size} relationships to validate")

            var passCount = 0
            var failCount = 0
            val failures = mutableListOf<String>()

            for (rel in relationships) {
                val ocr = ocrProfilesById[rel.ocrId]
                val event = eventProfilesById[rel.eventId]

                if (ocr == null || event == null) {
                    Log.w(TAG, "Missing profile for relationship: ocr=${rel.ocrId}, event=${rel.eventId}")
                    continue
                }

                val affinity = service.calculateSemanticAffinity(ocr, event)
                val inRange = affinity >= rel.minAffinity && affinity <= rel.maxAffinity

                if (inRange) {
                    passCount++
                } else {
                    failCount++
                    failures.add(
                        "${rel.type}: expected ${rel.minAffinity}-${rel.maxAffinity}, " +
                        "got ${"%.3f".format(affinity)} (${ocr.domain?.name} vs ${event.domain?.name})"
                    )
                }
            }

            val passRate = if (passCount + failCount > 0) {
                passCount.toFloat() / (passCount + failCount) * 100
            } else 0f

            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "VALIDATION RESULTS")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "Pass: $passCount, Fail: $failCount, Rate: ${"%.1f".format(passRate)}%")

            if (failures.isNotEmpty()) {
                Log.d(TAG, "Sample failures (first 10):")
                failures.take(10).forEach { Log.d(TAG, "  - $it") }
            }

            // We expect at least 70% pass rate (relationships are approximate)
            assertTrue(
                "Expected at least 70% pass rate, got ${"%.1f".format(passRate)}%",
                passRate >= 70f
            )

        } finally {
            db.close()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun createMinimalProfile(
        id: String,
        domain: Domain
    ): SemanticProfile {
        return SemanticProfile(
            sourceType = OcrSource.MEMO,
            sourceId = id,
            pageId = "page1",
            domain = domain,
            domainConfidence = 0.8f,
            intents = emptyList(),
            subdomainHint = null,
            docStyle = null,
            keywords = emptyList(),
            keyPhrases = emptyList(),
            keySentences = emptyList(),
            entities = emptyList(),
            actionItems = emptyList(),
            temporalSignals = null,
            synopsis = null,
            sourceDate = null,
            sourceTitle = null,
            wordCount = 0,
            confidence = 0.9f
        )
    }

    private fun createProfileWithKeywords(
        id: String,
        domain: Domain,
        keywords: List<String>
    ): SemanticProfile {
        return createMinimalProfile(id, domain).copy(
            keywords = keywords.mapIndexed { index, term ->
                KeywordEntry(term, 0.8f - index * 0.1f, 1)
            }
        )
    }

    private fun createProfileWithEntities(
        id: String,
        domain: Domain,
        entities: List<ExtractedEntity>
    ): SemanticProfile {
        return createMinimalProfile(id, domain).copy(
            entities = entities
        )
    }

    private fun loadOcrProfiles(db: SQLiteDatabase): List<SemanticProfile> {
        val profiles = mutableListOf<SemanticProfile>()

        val cursor = db.rawQuery(
            """
            SELECT id, sourceType, sourceId, pageId, domain, domainConfidence,
                   intentsJson, keywordsJson, entitiesJson, actionItemsJson,
                   temporalSignalsJson, keyPhrasesJson, keySentencesJson,
                   synopsis, sourceDate, sourceTitle, wordCount, confidence
            FROM unified_ocr_text
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                try {
                    profiles.add(cursorToProfile(it))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse OCR profile: ${e.message}")
                }
            }
        }

        return profiles
    }

    private fun loadEventProfiles(db: SQLiteDatabase): List<SemanticProfile> {
        // Events are stored in calendar_events table but we need their semantic profiles
        // from unified_ocr_text where sourceType = 'CALENDAR_EVENT' or 'LOCAL_EVENT'
        val profiles = mutableListOf<SemanticProfile>()

        // First try to load indexed event profiles
        val cursor = db.rawQuery(
            """
            SELECT id, sourceType, sourceId, pageId, domain, domainConfidence,
                   intentsJson, keywordsJson, entitiesJson, actionItemsJson,
                   temporalSignalsJson, keyPhrasesJson, keySentencesJson,
                   synopsis, sourceDate, sourceTitle, wordCount, confidence
            FROM unified_ocr_text
            WHERE sourceType IN ('CALENDAR_EVENT', 'LOCAL_EVENT')
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                try {
                    profiles.add(cursorToProfile(it))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse event profile: ${e.message}")
                }
            }
        }

        // If no indexed events, create profiles from calendar_events table
        if (profiles.isEmpty()) {
            val eventCursor = db.rawQuery(
                "SELECT id, title, description, dayId FROM calendar_events",
                null
            )

            eventCursor.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val title = it.getString(1) ?: ""
                    val description = it.getString(2) ?: ""
                    val dayId = it.getString(3) ?: ""

                    // Create a basic profile from event data
                    profiles.add(createEventProfile(id, title, description, dayId))
                }
            }
        }

        return profiles
    }

    private fun createEventProfile(
        id: String,
        title: String,
        description: String,
        dayId: String
    ): SemanticProfile {
        // Extract keywords from title and description
        val text = "$title $description"
        val words = text.lowercase()
            .split(Regex("[\\s,.;:!?]+"))
            .filter { it.length > 2 }
            .distinct()
            .take(10)

        return SemanticProfile(
            sourceType = OcrSource.CALENDAR_EVENT,
            sourceId = id,
            pageId = "event",
            domain = inferDomainFromText(text),
            domainConfidence = 0.7f,
            intents = emptyList(),
            subdomainHint = null,
            docStyle = null,
            keywords = words.mapIndexed { i, w -> KeywordEntry(w, 0.8f - i * 0.05f, 1) },
            keyPhrases = listOf(title),
            keySentences = emptyList(),
            entities = extractEntitiesFromText(text),
            actionItems = emptyList(),
            temporalSignals = null,
            synopsis = title,
            sourceDate = dayId,
            sourceTitle = title,
            wordCount = text.split(" ").size,
            confidence = 0.9f
        )
    }

    private fun inferDomainFromText(text: String): Domain {
        val lower = text.lowercase()
        return when {
            lower.contains("ward") || lower.contains("bishop") || lower.contains("ministering") -> Domain.LDS_LEADERSHIP
            lower.contains("sprint") || lower.contains("standup") || lower.contains("1:1") -> Domain.PROFESSIONAL
            lower.contains("client") || lower.contains("sales") || lower.contains("deal") -> Domain.BUSINESS
            lower.contains("lesson") || lower.contains("student") || lower.contains("class") -> Domain.EDUCATION
            lower.contains("code") || lower.contains("deploy") || lower.contains("api") -> Domain.TECH
            lower.contains("doctor") || lower.contains("health") || lower.contains("exercise") -> Domain.HEALTH
            else -> Domain.MISC
        }
    }

    private fun extractEntitiesFromText(text: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Simple person name pattern (capitalized words)
        val namePattern = Regex("\\b([A-Z][a-z]+ [A-Z][a-z]+)\\b")
        namePattern.findAll(text).forEach {
            entities.add(ExtractedEntity(it.value, EntityType.PERSON, 0.8f))
        }

        return entities.distinctBy { it.text }.take(5)
    }

    private fun cursorToProfile(cursor: android.database.Cursor): SemanticProfile {
        val sourceType = cursor.getString(cursor.getColumnIndexOrThrow("sourceType"))
        val domainStr = cursor.getString(cursor.getColumnIndexOrThrow("domain"))
        val intentsJson = cursor.getString(cursor.getColumnIndexOrThrow("intentsJson"))
        val keywordsJson = cursor.getString(cursor.getColumnIndexOrThrow("keywordsJson"))
        val entitiesJson = cursor.getString(cursor.getColumnIndexOrThrow("entitiesJson"))
        val actionItemsJson = cursor.getString(cursor.getColumnIndexOrThrow("actionItemsJson"))
        val temporalJson = cursor.getString(cursor.getColumnIndexOrThrow("temporalSignalsJson"))
        val phrasesJson = cursor.getString(cursor.getColumnIndexOrThrow("keyPhrasesJson"))
        val sentencesJson = cursor.getString(cursor.getColumnIndexOrThrow("keySentencesJson"))

        return SemanticProfile(
            sourceType = OcrSource.fromString(sourceType) ?: OcrSource.MEMO,
            sourceId = cursor.getString(cursor.getColumnIndexOrThrow("sourceId")),
            pageId = cursor.getString(cursor.getColumnIndexOrThrow("pageId")),
            domain = domainStr?.let { Domain.fromString(it) },
            domainConfidence = cursor.getFloat(cursor.getColumnIndexOrThrow("domainConfidence")),
            intents = parseIntents(intentsJson),
            subdomainHint = null,
            docStyle = null,
            keywords = parseKeywords(keywordsJson),
            keyPhrases = parseStringList(phrasesJson),
            keySentences = parseStringList(sentencesJson),
            entities = parseEntities(entitiesJson),
            actionItems = parseActionItems(actionItemsJson),
            temporalSignals = parseTemporalSignals(temporalJson),
            synopsis = cursor.getString(cursor.getColumnIndexOrThrow("synopsis")),
            sourceDate = cursor.getString(cursor.getColumnIndexOrThrow("sourceDate")),
            sourceTitle = cursor.getString(cursor.getColumnIndexOrThrow("sourceTitle")),
            wordCount = cursor.getInt(cursor.getColumnIndexOrThrow("wordCount")),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence"))
        )
    }

    private fun parseIntents(json: String?): List<IntentEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val intentStr = obj.getString("intent")
                val confidence = obj.getDouble("confidence").toFloat()
                Intent.fromString(intentStr)?.let { IntentEntry(it, confidence) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseKeywords(json: String?): List<KeywordEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                KeywordEntry(
                    term = obj.getString("term"),
                    score = obj.getDouble("score").toFloat(),
                    frequency = obj.optInt("frequency", 1)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseEntities(json: String?): List<ExtractedEntity> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val typeStr = obj.getString("type")
                EntityType.fromString(typeStr)?.let { type ->
                    ExtractedEntity(
                        text = obj.getString("text"),
                        type = type,
                        confidence = obj.getDouble("confidence").toFloat()
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseActionItems(json: String?): List<ActionItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ActionItem(
                    verb = obj.getString("verb"),
                    objectText = obj.getString("objectText"),
                    dueDateCandidate = obj.optString("dueDateCandidate", null),
                    timeCandidate = obj.optString("timeCandidate", null),
                    assigneeCandidate = obj.optString("assigneeCandidate", null),
                    confidence = obj.optDouble("confidence", 0.5).toFloat(),
                    rawText = obj.optString("rawText", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTemporalSignals(json: String?): TemporalSignals? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            TemporalSignals(
                explicitDates = parseJsonStringArray(obj.optJSONArray("explicitDates")),
                explicitTimes = parseJsonStringArray(obj.optJSONArray("explicitTimes")),
                relativeDates = parseJsonStringArray(obj.optJSONArray("relativeDates")),
                weekdays = parseJsonStringArray(obj.optJSONArray("weekdays")),
                dateRanges = parseJsonStringArray(obj.optJSONArray("dateRanges")),
                dominantDateCandidate = obj.optString("dominantDateCandidate", null)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseJsonStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    data class TestRelationship(
        val ocrId: String,
        val eventId: String,
        val type: String,
        val minAffinity: Float,
        val maxAffinity: Float
    )

    private fun loadRelationships(file: File): List<TestRelationship> {
        return try {
            val json = file.readText()
            val root = JSONObject(json)
            val relationships = mutableListOf<TestRelationship>()

            val byType = root.getJSONObject("by_type")

            // Parse each type
            for (type in listOf("high_match", "medium_match", "low_match")) {
                val typeObj = byType.optJSONObject(type) ?: continue
                val range = typeObj.optJSONArray("expected_affinity_range")
                val minAffinity = range?.optDouble(0)?.toFloat() ?: 0f
                val maxAffinity = range?.optDouble(1)?.toFloat() ?: 1f

                val rels = typeObj.optJSONArray("relationships") ?: continue
                for (i in 0 until rels.length()) {
                    val rel = rels.getJSONObject(i)
                    relationships.add(TestRelationship(
                        ocrId = rel.getString("ocrId"),
                        eventId = rel.getString("eventId"),
                        type = type.uppercase(),
                        minAffinity = minAffinity,
                        maxAffinity = maxAffinity
                    ))
                }
            }

            relationships
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load relationships: ${e.message}")
            emptyList()
        }
    }
}
