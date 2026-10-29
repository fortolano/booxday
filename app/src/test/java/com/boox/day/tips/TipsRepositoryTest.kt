package com.boox.day.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tips list stays accurate (29 Oct 2026), and discovery tips show only where they belong. */
class TipsRepositoryTest {

    private val tips = TipsRepository.getAllTips()

    /** Items of the Settings menu (ui/calendar/SettingsMenu.kt); a tip naming another one is stale. */
    private val settingsMenu = setOf(
        "Preferences", "Calendar & Sync", "Calendar Accounts", "AI Settings", "Voice-to-Text",
        "Data & Backup", "Boox Notes", "Help", "About"
    )

    @Test
    fun idsAreUnique() {
        val dupes = tips.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate tip ids: $dupes", dupes.isEmpty())
    }

    @Test
    fun messagesAreUnique() {
        val dupes = tips.groupBy { it.message }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate tip messages: $dupes", dupes.isEmpty())
    }

    @Test
    fun messagesFitAToast() {
        // A tip shows for 7 seconds on e-ink: keep it readable in one glance
        val long = tips.filter { it.message.length > 120 }.map { "${it.id} (${it.message.length})" }
        assertTrue("Tips over 120 characters: $long", long.isEmpty())
    }

    @Test
    fun everyTipHasAContext() {
        assertTrue(tips.all { it.contexts.isNotEmpty() && it.message.isNotBlank() })
    }

    @Test
    fun settingsPathsNameRealMenuItems() {
        // "Settings → X": X must be a menu item ("AI Settings → AI Models" is a page inside one)
        val path = Regex("""(?<!AI )Settings → """)
        var found = 0
        val wrong = tips.flatMap { tip ->
            path.findAll(tip.message).map { tip.message.substring(it.range.last + 1) }
                .onEach { found++ }
                .filter { rest -> settingsMenu.none { rest.startsWith(it) } }
                .map { "${tip.id}: $it" }
        }
        assertTrue("Settings paths that don't exist: $wrong", wrong.isEmpty())
        assertTrue(found > 10)
    }

    @Test
    fun discoveryTipForTheOtherTabIsNotShownOnItsOwnTab() {
        val onTasks = setOf(TipContext.TASKS, TipContext.CALENDAR, TipContext.EVENTS, TipContext.GENERAL)
        val promoteTasks = tips.single { it.id == "discover_tasks_from_memos" }
        assertFalse(TipsService.fitsHere(promoteTasks, onTasks))
        val (contextual, discovery) = TipsService.splitPools(tips, onTasks)
        assertFalse(promoteTasks in contextual || promoteTasks in discovery)
        // Its counterpart is shown on Tasks, in the discovery pool
        assertTrue(tips.single { it.id == "discover_memos_from_tasks" } in discovery)
    }

    @Test
    fun otherTabTipsGoToTheDiscoveryPool() {
        val onTasks = setOf(TipContext.TASKS, TipContext.CALENDAR, TipContext.EVENTS, TipContext.GENERAL)
        val (contextual, discovery) = TipsService.splitPools(tips, onTasks)
        val memosOnly = tips.single { it.id == "memos_eraser" }
        assertTrue(memosOnly in discovery)
        assertFalse(memosOnly in contextual)
        assertTrue(contextual.none { it.isDiscovery })
        // Nothing is lost: every tip that fits on Tasks is in one pool
        assertEquals(tips.count { TipsService.fitsHere(it, onTasks) }, (contextual + discovery).toSet().size)
    }
}
