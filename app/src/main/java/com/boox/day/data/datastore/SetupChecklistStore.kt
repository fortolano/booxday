package com.boox.day.data.datastore

import android.content.Context
import com.boox.day.data.db.KvProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one place that clears the Settings setup dots (Oct 2026). It always starts from the live
 * settings, so a dot cleared elsewhere (the AI Settings or Boox Notes dialog) is never brought back
 * by an older copy, and it does nothing before the saved settings are loaded.
 */
object SetupChecklistStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun mark(context: Context, update: (SetupChecklist) -> SetupChecklist) {
        val app = context.applicationContext
        val newSettings = updated(GlobalAppSettings.loaded, GlobalAppSettings.current, update) ?: return
        GlobalAppSettings.update(newSettings)
        scope.launch { KvProxy(app).setAppSettings(GlobalAppSettings.current) }
    }

    /** The settings to save, or null when nothing changes or the saved settings aren't loaded yet. */
    internal fun updated(
        loaded: Boolean,
        current: AppSettings,
        update: (SetupChecklist) -> SetupChecklist
    ): AppSettings? {
        if (!loaded) return null
        val checklist = update(current.setupChecklist)
        return if (checklist == current.setupChecklist) null else current.copy(setupChecklist = checklist)
    }
}
