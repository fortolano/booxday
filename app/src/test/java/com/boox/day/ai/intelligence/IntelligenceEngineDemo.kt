package com.boox.day.ai.intelligence

import com.boox.day.ai.intelligence.IntelligenceTestUtils.TODAY
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarContext
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createCalendarEvent
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPatternAnalysis
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createPreferences
import com.boox.day.ai.intelligence.IntelligenceTestUtils.createTask
import com.boox.day.ai.intelligence.IntelligenceTestUtils.toEpochMillis
import com.boox.day.data.ai.*
import com.boox.day.data.db.Task
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.temporal.ChronoUnit

/**
 * Demonstration test showing the complete data flow through the Intelligence Engine.
 * Run this test to see the input and output of the scoring pipeline.
 */
class IntelligenceEngineDemo {

    private val scorerEngine = ScorerEngine()

    @Test
    fun `DEMO - Show complete data flow for SMART_PRIORITIZATION`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("INTELLIGENCE ENGINE DATA FLOW DEMONSTRATION")
        println("Feature: SMART_PRIORITIZATION")
        println("=".repeat(80))

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: INPUT DATA - Tasks to score
        // ═══════════════════════════════════════════════════════════════════════════
        val tasks = listOf(
            createTask(id = "task_1", text = "URGENT: Call client about contract", dueDate = TODAY.minusDays(2).toEpochMillis(), priority = "A"),
            createTask(id = "task_2", text = "Review quarterly report", dueDate = TODAY.toEpochMillis(), priority = "B"),
            createTask(id = "task_3", text = "Schedule team meeting", priority = "C"),
            createTask(id = "task_4", text = "Buy office supplies", migrationCount = 4),
            createTask(id = "task_5", text = "Update project documentation")
        )

        println("\n📥 INPUT: Tasks to Score")
        println("-".repeat(80))
        tasks.forEach { task ->
            val dueInfo = task.dueDay?.let {
                val days = ChronoUnit.DAYS.between(TODAY, java.time.LocalDate.parse(it))
                if (days < 0) "OVERDUE by ${-days} days" else if (days == 0L) "DUE TODAY" else "Due in $days days"
            } ?: "No due date"
            val priority = if (task.priority.isNotBlank()) "Priority: ${task.priority}" else "No priority"
            val zombie = if (task.migrationCount > 0) "Rolled over ${task.migrationCount}x" else ""
            println("  - [${task.id}] ${task.typedText}")
            println("      $priority | $dueInfo $zombie")
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: CONVERT TO SCORABLE ITEMS
        // ═══════════════════════════════════════════════════════════════════════════
        val scorableItems = tasks.map { task ->
            val daysFromToday = if (task.dueDay != null) {
                ChronoUnit.DAYS.between(TODAY, java.time.LocalDate.parse(task.dueDay)).toInt()
            } else 0

            ScorableItem.TaskItem(
                id = task.id,
                text = task.typedText ?: "",
                task = task,
                daysFromToday = daysFromToday,
                isOverdue = daysFromToday < 0 && !task.isCompleted && task.dueDay != null
            )
        }

        println("\n📊 SCORABLE ITEMS (converted for scoring)")
        println("-".repeat(80))
        scorableItems.forEach { item ->
            val taskItem = item as ScorableItem.TaskItem
            println("  - [${item.id}] daysFromToday=${taskItem.daysFromToday}, isOverdue=${taskItem.isOverdue}")
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3: BUILD SCORING INPUT
        // ═══════════════════════════════════════════════════════════════════════════
        val input = ScoringInput(
            feature = IntelligenceFeature.SMART_PRIORITIZATION,
            currentDate = TODAY,
            currentHour = 10,
            preferences = null,
            calendarContext = null,
            taskContext = null,
            patternAnalysis = null,
            semanticContexts = null,
            itemsToScore = scorableItems
        )

        println("\n⚙️ SCORING INPUT")
        println("-".repeat(80))
        println("  Feature: ${input.feature}")
        println("  Current Date: ${input.currentDate}")
        println("  Current Hour: ${input.currentHour}")
        println("  Items to Score: ${input.itemsToScore.size}")

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 4: GET FEATURE-SPECIFIC WEIGHTS
        // ═══════════════════════════════════════════════════════════════════════════
        val weights = FeatureWeightProfiles.getWeights(IntelligenceFeature.SMART_PRIORITIZATION)

        println("\n⚖️ FEATURE WEIGHTS (SMART_PRIORITIZATION profile)")
        println("-".repeat(80))
        weights.entries.sortedByDescending { it.value }.forEach { (scorer, weight) ->
            val bar = "█".repeat((weight * 20).toInt())
            println("  ${scorer.padEnd(12)} : ${String.format("%.2f", weight)} $bar")
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 5: RUN SCORING ENGINE (7 scorers in parallel)
        // ═══════════════════════════════════════════════════════════════════════════
        println("\n🔄 RUNNING 7 SCORERS IN PARALLEL...")
        println("-".repeat(80))
        println("  - UrgencyScorer (deadlines, overdue)")
        println("  - PreferenceScorer (user priorities)")
        println("  - CalendarScorer (event alignment)")
        println("  - PatternScorer (completion trends)")
        println("  - SemanticScorer (content relevance)")
        println("  - CapacityScorer (workload balance)")
        println("  - ComplexityScorer (task breakdown)")

        val startTime = System.currentTimeMillis()
        val result = scorerEngine.scoreItems(input, weights)
        val duration = System.currentTimeMillis() - startTime

        println("  ✓ Scoring completed in ${duration}ms")

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 6: OUTPUT - SCORED ITEMS (sorted by score)
        // ═══════════════════════════════════════════════════════════════════════════
        println("\n📤 OUTPUT: Scored Items (sorted by finalScore)")
        println("-".repeat(80))
        result.forEach { scoredItem ->
            val scoreBar = "█".repeat((scoredItem.finalScore * 20).toInt())
            println("  #${scoredItem.rank} [${String.format("%.3f", scoredItem.finalScore)}] $scoreBar")
            println("     ${scoredItem.itemText.take(50)}")
            println("     Primary Signal: ${scoredItem.primarySignal}")

            // Show component scores breakdown
            println("     Component Scores:")
            scoredItem.componentScores.entries
                .filter { it.value.weight > 0 }
                .sortedByDescending { it.value.weightedScore }
                .take(3)
                .forEach { (scorerId, component) ->
                    println("       - $scorerId: raw=${String.format("%.2f", component.rawScore)} × weight=${String.format("%.2f", component.weight)} = ${String.format("%.3f", component.weightedScore)}")
                    component.signals.take(1).forEach { signal ->
                        println("         → $signal")
                    }
                }
            println()
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 7: CONTEXT SUMMARY (for LLM prompt)
        // ═══════════════════════════════════════════════════════════════════════════
        println("\n📝 CONTEXT SUMMARY (injected into LLM prompt)")
        println("-".repeat(80))

        // Simulate what would be in scoredContext.contextSummary
        val topItems = result.take(3)
        val contextSummary = buildString {
            appendLine("Top priority items:")
            topItems.forEachIndexed { index, item ->
                appendLine("${index + 1}. [${String.format("%.2f", item.finalScore)}] ${item.itemText} - ${item.primarySignal}")
            }
        }
        println(contextSummary)

        println("=".repeat(80))
        println("END OF DEMONSTRATION")
        println("=".repeat(80) + "\n")
    }

    @Test
    fun `DEMO - Show SMART_SUGGESTIONS with calendar context`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("INTELLIGENCE ENGINE DATA FLOW DEMONSTRATION")
        println("Feature: SMART_SUGGESTIONS (with Calendar Context)")
        println("=".repeat(80))

        // Input: Tasks + Calendar Events
        val tasks = listOf(
            createTask(id = "meeting_prep", text = "Prepare slides for quarterly review"),
            createTask(id = "exercise", text = "Go to gym for morning workout"),
            createTask(id = "groceries", text = "Buy milk and bread from store"),
            createTask(id = "email", text = "Reply to client about proposal")
        )

        val calendarContext = createCalendarContext(
            todayEvents = listOf(
                createCalendarEvent("Quarterly Review Meeting", daysFromToday = 0),
                createCalendarEvent("Client call at 3pm", daysFromToday = 0)
            )
        )

        val preferences = createPreferences(
            healthPriority = 4,
            workPriority = 5,
            familyPriority = 2
        )

        println("\n📥 INPUT: Tasks")
        println("-".repeat(80))
        tasks.forEach { println("  - ${it.typedText}") }

        println("\n📅 INPUT: Calendar Events (Today)")
        println("-".repeat(80))
        calendarContext.todayEvents.forEach { println("  - ${it.title}") }

        println("\n👤 INPUT: User Preferences")
        println("-".repeat(80))
        println("  Health Priority: ${preferences.healthPriority}/5")
        println("  Work Priority: ${preferences.workPriority}/5")
        println("  Family Priority: ${preferences.familyPriority}/5")

        // Build input
        val scorableItems = tasks.map { task ->
            ScorableItem.TaskItem(
                id = task.id,
                text = task.typedText ?: "",
                task = task,
                daysFromToday = 0,
                isOverdue = false
            )
        }

        val input = ScoringInput(
            feature = IntelligenceFeature.SMART_SUGGESTIONS,
            currentDate = TODAY,
            currentHour = 9,
            preferences = preferences,
            calendarContext = calendarContext,
            taskContext = null,
            patternAnalysis = null,
            semanticContexts = null,
            itemsToScore = scorableItems
        )

        // Get weights for SMART_SUGGESTIONS
        val weights = FeatureWeightProfiles.getWeights(IntelligenceFeature.SMART_SUGGESTIONS)

        println("\n⚖️ FEATURE WEIGHTS (SMART_SUGGESTIONS profile)")
        println("-".repeat(80))
        weights.entries.sortedByDescending { it.value }.forEach { (scorer, weight) ->
            val bar = "█".repeat((weight * 20).toInt())
            println("  ${scorer.padEnd(12)} : ${String.format("%.2f", weight)} $bar")
        }

        // Run scoring
        val result = scorerEngine.scoreItems(input, weights)

        println("\n📤 OUTPUT: Suggested Items (ranked)")
        println("-".repeat(80))
        result.forEach { scoredItem ->
            val scoreBar = "█".repeat((scoredItem.finalScore * 20).toInt())
            println("  #${scoredItem.rank} [${String.format("%.3f", scoredItem.finalScore)}] $scoreBar")
            println("     ${scoredItem.itemText}")
            println("     Signal: ${scoredItem.primarySignal}")
            println()
        }

        println("=".repeat(80))
        println("END OF DEMONSTRATION")
        println("=".repeat(80) + "\n")
    }
}
