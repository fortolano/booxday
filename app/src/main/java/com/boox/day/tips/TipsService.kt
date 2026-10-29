package com.boox.day.tips

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.boox.day.R
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.views.EditorTab
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Singleton service for managing and displaying context-aware tips.
 *
 * DESIGN DECISIONS:
 * - Singleton ensures single timer instance across app lifecycle
 * - Uses GlobalAppSettings to check enabled state
 * - Timer runs in Dispatchers.Default (background) - never blocks UI
 * - Emits tips via existing SnackState.globalSnackFlow
 * - Does NOT touch DrawCanvas or any hot paths
 *
 * USAGE:
 * - MainScreen calls setCurrentTab() when tab changes
 * - MainScreen calls start()/stop() based on tipsEnabled setting
 */
object TipsService {
    private const val TAG = "TipsService"

    // Tip display duration: 7 seconds (longer for readability on e-ink)
    private const val TIP_DURATION_MS = 7000

    /**
     * Get the current tip interval from settings.
     * Reads from GlobalAppSettings each time to respond to setting changes.
     */
    private val currentIntervalMs: Long
        get() = GlobalAppSettings.current.tipInterval.milliseconds

    // Selection weights: 75% contextual, 25% discovery
    private const val CONTEXTUAL_WEIGHT = 0.75f

    // Internal state
    private var timerJob: Job? = null
    private val shownTipIds = mutableSetOf<String>()
    private var currentTab: EditorTab = EditorTab.Memos

    // All tips loaded from repository
    private val allTips: List<Tip> by lazy { TipsRepository.getAllTips() }

    /**
     * Update the current tab context.
     * Called by MainScreen when tab changes.
     */
    fun setCurrentTab(tab: EditorTab) {
        currentTab = tab
        Log.d(TAG, "Current tab set to: $tab")
    }

    /**
     * Start the tips timer.
     * Called when app starts (if enabled) or when setting is enabled.
     * Safe to call multiple times - stops existing timer first.
     */
    fun start() {
        if (!GlobalAppSettings.current.tipsEnabled) {
            Log.d(TAG, "Tips disabled in settings, not starting")
            return
        }

        stop()  // Stop existing timer if any

        timerJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            Log.i(TAG, "Tips timer started (interval: ${currentIntervalMs / 1000}s)")
            while (isActive) {
                // Use current interval setting (can change between tips)
                val intervalMs = currentIntervalMs
                delay(intervalMs)
                // Re-check setting in case it changed
                if (GlobalAppSettings.current.tipsEnabled) {
                    // Oct 2026: not over the first-start welcome popup (open until onboardingDismissedAt is set)
                    if (GlobalAppSettings.current.onboardingDismissedAt != 0L) showNextTip()
                } else {
                    Log.d(TAG, "Tips disabled during timer, stopping")
                    break
                }
            }
        }
    }

    /**
     * Stop the tips timer.
     * Called when setting is disabled or app goes to background.
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "Tips timer stopped")
    }

    /**
     * Check if the timer is currently running.
     */
    fun isRunning(): Boolean = timerJob?.isActive == true

    /**
     * Reset shown tips tracking.
     * Useful for testing or when user wants to see all tips again.
     */
    fun resetShownTips() {
        shownTipIds.clear()
        Log.d(TAG, "Shown tips reset")
    }

    /**
     * Select and display the next tip based on:
     * 1. Current tab context (Memos/Tasks + always-visible Calendar/Events)
     * 2. 75% contextual / 25% discovery weight
     * 3. Avoid repeating tips until all shown
     */
    private fun showNextTip() {
        val relevantContexts = buildRelevantContexts(currentTab)
        val tip = selectTip(relevantContexts)

        if (tip != null) {
            displayTip(tip)
            shownTipIds.add(tip.id)

            // Reset if all tips have been shown
            if (shownTipIds.size >= allTips.size) {
                Log.i(TAG, "All ${allTips.size} tips shown, resetting cycle")
                resetShownTips()
            }
        } else {
            Log.w(TAG, "No tip available to show")
        }
    }

    /**
     * Build the set of relevant contexts for the current state.
     * - Memos OR Tasks (mutually exclusive based on current tab)
     * - Calendar and Events are always included (always visible)
     * - General is always included
     */
    private fun buildRelevantContexts(tab: EditorTab): Set<TipContext> {
        return when (tab) {
            EditorTab.Memos -> setOf(
                TipContext.MEMOS,
                TipContext.CALENDAR,
                TipContext.EVENTS,
                TipContext.GENERAL
            )
            EditorTab.Tasks -> setOf(
                TipContext.TASKS,
                TipContext.CALENDAR,
                TipContext.EVENTS,
                TipContext.GENERAL
            )
        }
    }

    /**
     * Select a tip using weighted random selection.
     *
     * Algorithm:
     * 1. Filter out already-shown tips
     * 2. Separate into contextual and discovery pools
     * 3. Roll 75/25 to pick which pool
     * 4. Pick random from selected pool
     */
    private fun selectTip(relevantContexts: Set<TipContext>): Tip? {
        // Discovery tips for the other tab are left out ("Try the Tasks tab!" while on Tasks)
        val unseenTips = allTips.filter { it.id !in shownTipIds && fitsHere(it, relevantContexts) }
        if (unseenTips.isEmpty()) {
            // All tips shown - reset and try again
            resetShownTips()
            return allTips.filter { fitsHere(it, relevantContexts) }.randomOrNull()
        }

        val (contextualTips, discoveryTips) = splitPools(unseenTips, relevantContexts)

        // Weighted selection: 75% contextual, 25% discovery
        val useContextual = Random.nextFloat() < CONTEXTUAL_WEIGHT

        val selectedPool = when {
            useContextual && contextualTips.isNotEmpty() -> {
                Log.v(TAG, "Selecting from ${contextualTips.size} contextual tips")
                contextualTips
            }
            !useContextual && discoveryTips.isNotEmpty() -> {
                Log.v(TAG, "Selecting from ${discoveryTips.size} discovery tips")
                discoveryTips
            }
            contextualTips.isNotEmpty() -> {
                Log.v(TAG, "Fallback to ${contextualTips.size} contextual tips")
                contextualTips
            }
            discoveryTips.isNotEmpty() -> {
                Log.v(TAG, "Fallback to ${discoveryTips.size} discovery tips")
                discoveryTips
            }
            else -> {
                Log.v(TAG, "Fallback to ${unseenTips.size} unseen tips")
                unseenTips
            }
        }

        return selectedPool.randomOrNull()
    }

    /** A discovery tip is shown only in the contexts it lists; other tips anywhere. */
    internal fun fitsHere(tip: Tip, relevantContexts: Set<TipContext>): Boolean =
        !tip.isDiscovery || tip.contexts.any { it in relevantContexts }

    /**
     * Contextual pool (tips for what is on screen) and discovery pool (discovery tips listing a
     * context on screen, plus tips for the other tab).
     */
    internal fun splitPools(tips: List<Tip>, relevantContexts: Set<TipContext>): Pair<List<Tip>, List<Tip>> {
        val contextual = tips.filter { !it.isDiscovery && it.contexts.any { c -> c in relevantContexts } }
        val discovery = tips.filter {
            if (it.isDiscovery) fitsHere(it, relevantContexts) else it.contexts.none { c -> c in relevantContexts }
        }
        return contextual to discovery
    }

    /**
     * Display the tip via the existing snackbar system.
     */
    private fun displayTip(tip: Tip) {
        val snackConf = SnackConf(
            content = { TipSnackContent(tip.message) },
            duration = TIP_DURATION_MS
        )

        val emitted = SnackState.globalSnackFlow.tryEmit(snackConf)
        if (emitted) {
            Log.d(TAG, "Displayed tip: ${tip.id}")
        } else {
            Log.w(TAG, "Failed to emit tip (buffer full): ${tip.id}")
        }
    }
}

/**
 * Composable content for tip snackbar with vector lightbulb icon.
 */
@Composable
private fun TipSnackContent(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.lightbulb_40px),
            contentDescription = "Tip",
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = message, color = Color.White)
    }
}
