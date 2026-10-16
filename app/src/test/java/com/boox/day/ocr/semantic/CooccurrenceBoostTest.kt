package com.boox.day.ocr.semantic

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CooccurrenceCounts
import com.boox.day.data.db.EntityCooccurrence
import com.boox.day.data.db.EntityCooccurrenceDao
import com.boox.day.ocr.model.EntityType
import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.model.SemanticProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ln
import kotlin.random.Random

/**
 * The co-occurrence boost reads all counts once ([SemanticGeniusService.loadCooccurrenceCounts])
 * instead of querying getCooccurrence for every entity pair (Oct 2026). It must give exactly what the
 * old code gave.
 *
 * Runs on the JVM (Robolectric) against the real Room database and DAO, so the old code runs its real
 * SQL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CooccurrenceBoostTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EntityCooccurrenceDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.entityCooccurrenceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun boostsMatchThePreviousCodeWithAnEmptyTable() = runBlocking {
        val nonZero = compareRandomPairs(seed = 1, pairs = 500)
        assertEquals(0, nonZero)
    }

    @Test
    fun boostsMatchThePreviousCodeWithAFewRows() = runBlocking {
        fillRandomTable(seed = 2, rows = 25)
        val nonZero = compareRandomPairs(seed = 3, pairs = 1000)
        assertTrue("too few boosted pairs to be a real check: $nonZero", nonZero > 50)
    }

    @Test
    fun boostsMatchThePreviousCodeWithManyRows() = runBlocking {
        fillRandomTable(seed = 4, rows = 150)
        val nonZero = compareRandomPairs(seed = 5, pairs = 1000)
        assertTrue("too few boosted pairs to be a real check: $nonZero", nonZero > 300)
    }

    @Test
    fun lookupFindsTheSameRowAsTheQuery() = runBlocking {
        fillRandomTable(seed = 6, rows = 150)
        val counts = loadCounts()
        val keys = (BASE_NAMES.map { it.lowercase() } + RAW_ROW_ENTITIES).distinct()
        for (a in keys) {
            for (b in keys) {
                assertEquals("($a, $b)", dao.getCooccurrence(a, b)?.cooccurrenceCount, counts.get(a, b))
            }
        }
    }

    @Test
    fun eitherOrderCountsCountOfOneDoesNotAndTheSameEntityIsSkipped() = runBlocking {
        dao.upsertCooccurrence(row("acme corp", "alice", 4)) // sorted, as recordCooccurrences writes
        dao.upsertCooccurrence(row("zurich", "bob", 8)) // stored the other way round
        dao.upsertCooccurrence(row("alice", "bob", 1)) // seen once: no boost
        val counts = loadCounts()

        fun boost(note: List<String>, event: List<String>) =
            SemanticGeniusService.calculateCooccurrenceBoost(profile(note), profile(event), counts)

        val four = (1 - (1 / (1 + ln(4.0)))).toFloat()
        val eight = (1 - (1 / (1 + ln(8.0)))).toFloat()
        assertEquals(four * 0.15f, boost(listOf("Alice"), listOf("ACME Corp")), 0f)
        assertEquals(eight * 0.15f, boost(listOf("Bob"), listOf("Zurich")), 0f)
        assertEquals(0f, boost(listOf("Alice"), listOf("Bob")), 0f)
        assertEquals(0f, boost(listOf("Alice"), listOf("alice")), 0f)
        assertEquals((four + eight) / 2 * 0.15f, boost(listOf("Alice", "Bob"), listOf("Acme Corp", "Zurich")), 0f)
        assertEquals(0f, boost(listOf(" alice"), listOf("acme corp")), 0f) // texts are not trimmed
        assertEquals(0f, boost(emptyList(), listOf("acme corp")), 0f)
    }

    /** Old and new boost for random note/event entity lists; returns how many were above 0 */
    private suspend fun compareRandomPairs(seed: Int, pairs: Int): Int {
        val random = Random(seed)
        val counts = loadCounts()
        var nonZero = 0
        repeat(pairs) {
            val note = profile(List(random.nextInt(0, 7)) { entityText(random) })
            val event = profile(List(random.nextInt(0, 13)) { entityText(random) })
            val expected = PreviousBoost.calculateCooccurrenceBoost(note, event, dao)
            val actual = SemanticGeniusService.calculateCooccurrenceBoost(note, event, counts)
            assertEquals("${note.entities.map { it.text }} vs ${event.entities.map { it.text }}", expected, actual, 0f)
            if (expected > 0f) nonZero++
        }
        return nonZero
    }

    /**
     * Random rows over the lowercased names (sorted like recordCooccurrences writes them, or the other
     * way round), plus a few raw texts. Never both orders of one pair: the only writer sorts the pair.
     */
    private suspend fun fillRandomTable(seed: Int, rows: Int) {
        val random = Random(seed)
        val keys = BASE_NAMES.map { it.lowercase() } + RAW_ROW_ENTITIES
        val written = mutableSetOf<Set<String>>()
        while (written.size < rows) {
            val a = keys.random(random)
            val b = keys.random(random)
            if (a == b || !written.add(setOf(a, b))) continue
            val (first, second) = if ((a < b) == random.nextBoolean()) a to b else b to a
            dao.upsertCooccurrence(row(first, second, random.nextInt(1, 21)))
        }
    }

    /** A name as OCR might give it: any case, sometimes with spaces around it */
    private fun entityText(random: Random): String {
        val name = (BASE_NAMES + RAW_ROW_ENTITIES).random(random)
        return when (random.nextInt(6)) {
            0 -> name.uppercase()
            1 -> name.lowercase()
            2 -> " $name"
            3 -> "$name "
            else -> name
        }
    }

    private fun row(entity1: String, entity2: String, count: Int) =
        EntityCooccurrence(entity1, entity2, count, lastSeenAt = 1L, sourceTypes = "[\"BOOX_NOTE\"]")

    private fun profile(entityTexts: List<String>) = SemanticProfile(
        sourceType = OcrSource.BOOX_NOTE,
        sourceId = "source",
        pageId = "page",
        domain = null,
        domainConfidence = 0f,
        intents = emptyList(),
        subdomainHint = null,
        docStyle = null,
        keywords = emptyList(),
        keyPhrases = emptyList(),
        keySentences = emptyList(),
        entities = entityTexts.mapIndexed { i, text -> ExtractedEntity(text, ENTITY_TYPES[i % ENTITY_TYPES.size]) },
        actionItems = emptyList(),
        temporalSignals = null,
        synopsis = null,
        sourceDate = null,
        sourceTitle = null,
        wordCount = 0,
        confidence = 1f
    )

    /** What SemanticGeniusService.loadCooccurrenceCounts does, without the service */
    private suspend fun loadCounts() = CooccurrenceCounts(dao.getAllCooccurrences())

    /** SemanticGeniusService.calculateCooccurrenceBoost before Oct 2026, verbatim (comments removed) */
    private object PreviousBoost {
        suspend fun calculateCooccurrenceBoost(
            profileA: SemanticProfile,
            profileB: SemanticProfile,
            cooccurrenceDao: EntityCooccurrenceDao
        ): Float {
            val entitiesA = profileA.entities.map { it.text.lowercase() }.toSet()
            val entitiesB = profileB.entities.map { it.text.lowercase() }.toSet()

            if (entitiesA.isEmpty() || entitiesB.isEmpty()) return 0f

            var totalBoost = 0f
            var pairCount = 0

            for (ea in entitiesA) {
                for (eb in entitiesB) {
                    if (ea == eb) continue

                    val cooccurrence = cooccurrenceDao.getCooccurrence(ea, eb)
                    if (cooccurrence != null && cooccurrence.cooccurrenceCount >= 2) {
                        val factor = 1 - (1 / (1 + ln(cooccurrence.cooccurrenceCount.toDouble())))
                        totalBoost += factor.toFloat()
                        pairCount++
                    }
                }
            }

            return if (pairCount > 0) {
                ((totalBoost / pairCount) * SemanticGeniusService.COOCCURRENCE_WEIGHT)
                    .coerceAtMost(SemanticGeniusService.COOCCURRENCE_WEIGHT)
            } else {
                0f
            }
        }
    }

    private companion object {
        val BASE_NAMES = listOf(
            "Alice", "Bob Smith", "Acme Corp", "TuGo", "Zurich", "Room 4B", "Bishop Lee", "Stake Center",
            "José", "Zoë", "İstanbul", "Q4 Budget", "oct 12", "alice@example.com", "Sprint Team"
        )

        /** Row texts stored exactly as written: spaces, capitals. Lookups lowercase but never trim */
        val RAW_ROW_ENTITIES = listOf(" alice", "bob smith ", "Acme Corp")

        val ENTITY_TYPES = listOf(EntityType.PERSON, EntityType.ORG, EntityType.LOCATION, EntityType.DATE, EntityType.EMAIL)
    }
}
