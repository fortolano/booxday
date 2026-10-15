package com.boox.day.data.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.QUICK_SETTINGS_PREFS
import com.boox.day.SHOW_WELCOME_COMPLETED_KEY
import com.boox.day.TAG
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.ui.views.hasFilePermission
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject


@Entity
data class Kv(
    @PrimaryKey
    val key: String,
    val value: String
)

// DAO
@Dao
interface KvDao {
    @Query("SELECT * FROM kv WHERE `key`=:key")
    fun get(key: String): Kv?

    @Query("SELECT * FROM kv WHERE `key`=:key")
    fun getLive(key: String): LiveData<Kv?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(kv: Kv)

    @Query("DELETE FROM kv WHERE `key`=:key")
    fun delete(key: String)

    @Query("SELECT * FROM kv")
    fun getAll(): List<Kv>

}

class KvRepository(context: Context) {
    var db = AppDatabase.getDatabase(context).kvDao()
    // Note: No permission check needed - database is now in internal app storage

    fun get(key: String): Kv? {
        return db.get(key)
    }

    fun getLive(key: String): LiveData<Kv?> {
        return db.getLive(key)
    }

    fun set(kv: Kv) {
        return db.set(kv)
    }

    fun delete(key: String) {
        db.delete(key)
    }

}

class KvProxy(private val context: Context) {
    private val kvRepository = KvRepository(context)

    fun <T> observeKv(key: String, serializer: KSerializer<T>, default: T): LiveData<T?> {
        return kvRepository.getLive(key).map {
            if (it == null) return@map default
            val jsonValue = it.value
            settingsJson.decodeFromString(serializer, jsonValue)
        }
    }

    fun <T> get(key: String, serializer: KSerializer<T>): T? {
        val kv = kvRepository.get(key) ?: return null //returns null when there is no database
        val jsonValue = kv.value
        return settingsJson.decodeFromString(serializer, jsonValue)
    }


    fun <T> setKv(key: String, value: T, serializer: KSerializer<T>) {
        val jsonValue = settingsJson.encodeToString(serializer, value)
        // Phase 3 (Oct 2026): the key only — values hold personal settings
        Log.i(TAG, "Saved setting '$key' (${jsonValue.length} chars)")
        kvRepository.set(Kv(key, jsonValue))
    }

    /**
     * Update app settings - non-blocking.
     *
     * 1. Updates GlobalAppSettings immediately (UI responsive)
     * 2. Syncs showWelcome flag to fast SharedPreferences (for next startup)
     * 3. Persists to database async on IO thread (no UI freeze)
     *
     * This fixes the systemic issue where Room's synchronous INSERT
     * was blocking the main thread and freezing the UI.
     */
    fun setAppSettings(value: AppSettings) {
        // Update global state FIRST for immediate UI responsiveness
        GlobalAppSettings.update(value)

        // PERFORMANCE: Sync showWelcome to fast SharedPreferences
        // This allows next startup to read routing decision in ~1ms instead of ~30ms Room query
        if (!value.showWelcome) {
            context.getSharedPreferences(QUICK_SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SHOW_WELCOME_COMPLETED_KEY, true)
                .apply()
        }

        // Persist to database async, on one writer so saves land in the order they were made
        // (Phase 3: separate launches could finish out of order and leave older settings stored)
        settingsWriter.launch {
            try {
                setKv(APP_SETTINGS_KEY, value, AppSettings.serializer())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist app settings: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * Settings JSON (Phase 3, Oct 2026): reads what older and newer versions wrote — unknown keys
         * are ignored and values that don't fit fall back to their defaults — instead of failing.
         */
        val settingsJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val settingsWriter = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /** Name of the OpenRouter key inside settings JSON written by older versions. */
        const val OPENROUTER_KEY_FIELD = "userOpenRouterApiKey"

        /** [kv] without secrets (an OpenRouter key older versions kept in the settings JSON). */
        fun withoutSecrets(kv: Kv): Kv {
            if (kv.key != APP_SETTINGS_KEY || !kv.value.contains(OPENROUTER_KEY_FIELD)) return kv
            return runCatching {
                val settings = Json.parseToJsonElement(kv.value).jsonObject
                kv.copy(value = JsonObject(settings - OPENROUTER_KEY_FIELD).toString())
            }.getOrElse { kv.copy(value = "{}") }
        }
    }
}
