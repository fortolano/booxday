package com.boox.day.ocr.semantic.classification

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads and indexes concept bridges for semantic expansion.
 * Concept bridges connect semantically related terms that don't share keywords.
 *
 * Example: "hungry" signal -> expands to include "lunch", "meal", "food" targets
 *
 * This enables matching notes containing "I'm hungry" to events like "Lunch Meeting"
 * even though they share no direct keywords.
 */
object ConceptBridgeLoader {

    private const val TAG = "ConceptBridgeLoader"

    @Serializable
    data class ConceptBridgesFile(
        val version: Int,
        val description: String,
        val bridges: List<Bridge>
    )

    @Serializable
    data class Bridge(
        val id: String,
        val signals: List<String>,
        val targets: List<String>
    )

    // Inverted index: signal term -> set of target terms
    private var signalToTargets: Map<String, Set<String>> = emptyMap()

    // Bridge ID lookup: signal term -> bridge ID (for debugging/logging)
    private var signalToBridgeId: Map<String, String> = emptyMap()

    private var initialized = false

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        try {
            val jsonString = context.assets.open("classification/en/concept_bridges.json")
                .bufferedReader().readText()
            val file = json.decodeFromString<ConceptBridgesFile>(jsonString)

            val targetMap = mutableMapOf<String, MutableSet<String>>()
            val bridgeIdMap = mutableMapOf<String, String>()

            for (bridge in file.bridges) {
                val targetSet = bridge.targets.map { it.lowercase() }.toSet()
                for (signal in bridge.signals) {
                    val key = signal.lowercase()
                    targetMap.getOrPut(key) { mutableSetOf() }.addAll(targetSet)
                    bridgeIdMap[key] = bridge.id
                }
            }

            signalToTargets = targetMap
            signalToBridgeId = bridgeIdMap
            initialized = true

            Log.d(TAG, "Loaded ${file.bridges.size} concept bridges with ${signalToTargets.size} signal terms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load concept bridges: ${e.message}", e)
            // Initialize with empty maps to prevent repeated load attempts
            signalToTargets = emptyMap()
            signalToBridgeId = emptyMap()
            initialized = true
        }
    }

    /**
     * Get target keywords for a given signal term.
     * Returns null if no bridge exists for this signal.
     */
    fun getTargetsFor(signal: String): Set<String>? {
        return signalToTargets[signal.lowercase()]
    }

    /**
     * Get the bridge ID for a signal term (for debugging/logging).
     */
    fun getBridgeIdFor(signal: String): String? {
        return signalToBridgeId[signal.lowercase()]
    }

    /**
     * Expand a set of keywords with concept bridge targets.
     *
     * For each keyword that matches a signal term, the corresponding
     * target terms are added to the expanded set.
     *
     * Example:
     * Input: {"hungry", "today"}
     * Output: {"hungry", "today", "lunch", "dinner", "meal", "food", ...}
     *
     * @param keywords The original keywords to expand
     * @return Expanded set including original keywords plus bridge targets
     */
    fun expandKeywords(keywords: Set<String>): Set<String> {
        if (!initialized || signalToTargets.isEmpty()) return keywords

        val expanded = keywords.toMutableSet()
        for (keyword in keywords) {
            getTargetsFor(keyword)?.let { targets ->
                expanded.addAll(targets)
            }
        }
        return expanded
    }

    /**
     * Check if the loader has been initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Get statistics about loaded bridges.
     */
    fun getStats(): String {
        return "Bridges loaded: signals=${signalToTargets.size}, initialized=$initialized"
    }

    /**
     * Reset for testing purposes only.
     */
    internal fun reset() {
        signalToTargets = emptyMap()
        signalToBridgeId = emptyMap()
        initialized = false
    }
}
