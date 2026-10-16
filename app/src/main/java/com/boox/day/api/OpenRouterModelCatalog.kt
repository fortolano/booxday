package com.boox.day.api

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.security.UserOpenRouterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One model from OpenRouter's list, as the model list screen and the model chains need it. */
@Serializable
data class CatalogModel(
    val id: String,
    /** Model name without the lab, e.g. "Gemini 3.1 Flash Lite" */
    val name: String,
    /** The lab that made it, e.g. "Google" */
    val lab: String,
    /** Place in OpenRouter's popularity ranking (weekly usage), 0 = most popular */
    val rank: Int,
    val created: Long = 0L,
    /** Dollars per token; negative = a router with variable prices */
    val promptPrice: Double = 0.0,
    val completionPrice: Double = 0.0,
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    /** Can return JSON (`structured_outputs` or `response_format`) */
    val supportsJson: Boolean = false,
    val reasoning: ReasoningOptions = ReasoningOptions(),
    val contextLength: Int? = null,
    val maxOutput: Int? = null,
    /** "yyyy-MM-dd" when OpenRouter will retire it */
    val expiration: String? = null,
    /** Has a provider that keeps no copy (OpenRouter's zero-data-retention list) */
    val zdr: Boolean = false
) {
    val isFree: Boolean get() = promptPrice == 0.0 && completionPrice == 0.0

    /** OpenRouter's price sort: the average of the in and out price */
    val averagePrice: Double get() = (promptPrice + completionPrice) / 2
}

/** The saved list: OpenRouter's models and, with a key, the ones the account's settings allow. */
@Serializable
data class CatalogSnapshot(
    val fetchedAtMs: Long,
    val models: List<CatalogModel>,
    /** From `/models/user`; null = not known (no key, or that call failed) */
    val allowedIds: List<String>? = null
)

/**
 * OpenRouter's model list (Oct 2026, plan step d): one download sorted by popularity, saved on the
 * tablet and refreshed after a day or on Refresh. Filtering, sorting and search run on the tablet,
 * so they are instant and work offline. The checks per AI job keep models that can't do the job
 * out of the list (plan: docs/AI_MODEL_PICKER_PLAN.md, section 5).
 */
object OpenRouterModelCatalog {
    private const val TAG = "OpenRouterModels"

    const val MODELS_URL = "https://openrouter.ai/api/v1/models?sort=most-popular&limit=1000"
    const val USER_MODELS_URL = "https://openrouter.ai/api/v1/models/user"
    const val ZDR_URL = "https://openrouter.ai/api/v1/endpoints/zdr"
    private const val CACHE_FILE = "openrouter_models.json"

    /** The list is downloaded again when older than this */
    const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** Older lists are not trusted to say a model is gone (an old list misses newer models) */
    const val TRUST_FOR_SKIPPING_MS = 14 * 24 * 60 * 60 * 1000L

    /** At most this many rows in the list (search still looks at every model) */
    const val LIST_LIMIT = 200

    /** Models retiring this soon are hidden */
    private const val RETIRING_DAYS = 30L

    enum class Show { ALL, TOP_PAID, TOP_FREE, TOP_REASONING }

    enum class Sort { MOST_POPULAR, NEWEST, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW }

    data class ListResult(val rows: List<CatalogModel>, val matching: Int)

    private val json = Json { ignoreUnknownKeys = true }

    // ═══════════════════════════════════════════════════════════════════════════
    // RULES (no Android, unit-tested)
    // ═══════════════════════════════════════════════════════════════════════════

    /** OpenRouter's `GET /models` answer, in its order (the popularity ranking). */
    internal fun parseModels(body: String, zdrIds: Set<String>): List<CatalogModel> {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapIndexedNotNull { index, element ->
            val m = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = m.string("id")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val fullName = m.string("name") ?: id
            val (lab, name) = splitName(fullName, id)
            val pricing = m["pricing"] as? JsonObject
            val architecture = m["architecture"] as? JsonObject
            val parameters = m.strings("supported_parameters")
            CatalogModel(
                id = id,
                name = name,
                lab = lab,
                rank = index,
                created = (m["created"] as? JsonPrimitive)?.longOrNull ?: 0L,
                promptPrice = pricing?.string("prompt")?.toDoubleOrNull() ?: 0.0,
                completionPrice = pricing?.string("completion")?.toDoubleOrNull() ?: 0.0,
                inputs = architecture?.strings("input_modalities").orEmpty(),
                outputs = architecture?.strings("output_modalities").orEmpty(),
                supportsJson = "structured_outputs" in parameters || "response_format" in parameters,
                reasoning = ReasoningOptions.fromOpenRouter(parameters, m["reasoning"] as? JsonObject),
                contextLength = (m["context_length"] as? JsonPrimitive)?.intOrNull,
                maxOutput = ((m["top_provider"] as? JsonObject)?.get("max_completion_tokens") as? JsonPrimitive)?.intOrNull,
                expiration = m.string("expiration_date"),
                zdr = id in zdrIds
            )
        }
    }

    /** Model ids from an answer whose `data` lists models (`id`) or endpoints (`model_id`). */
    internal fun parseIds(body: String, field: String): Set<String> {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptySet()
        return data.mapNotNull { (it as? JsonObject)?.string(field) }.toSet()
    }

    /** "Google: Gemini 3.1 Flash Lite" → ("Google", "Gemini 3.1 Flash Lite"); no lab → from the id. */
    internal fun splitName(fullName: String, id: String): Pair<String, String> {
        val colon = fullName.indexOf(": ")
        if (colon > 0) return fullName.substring(0, colon).trim() to fullName.substring(colon + 2).trim()
        val author = id.substringBefore('/').trimStart('~')
        return author.replaceFirstChar { it.titlecase(Locale.US) } to fullName.trim()
    }

    /**
     * Not shown for any job: batch-only copies (no live answers), "latest" aliases (model and price
     * change without notice), routers and variable prices, models retiring within 30 days, and
     * models that make anything but text (music, image and speech generators).
     */
    internal fun isListable(model: CatalogModel, today: LocalDate): Boolean {
        if (model.id.endsWith(":batch") || model.id.startsWith("~") || model.id.startsWith("openrouter/")) return false
        if (model.promptPrice < 0 || model.completionPrice < 0) return false
        if (model.outputs.isNotEmpty() && model.outputs.any { it != "text" }) return false
        val retires = model.expiration?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        return retires == null || retires.isAfter(today.plusDays(RETIRING_DAYS))
    }

    /** What a model must do for the job's requests (they read images or audio, and ask for JSON). */
    internal fun canDo(job: AiJob, model: CatalogModel): Boolean = when (job) {
        AiJob.VOICE -> "audio" in model.inputs
        AiJob.HANDWRITING -> "image" in model.inputs && model.supportsJson
        AiJob.WRITING, AiJob.PLANNING -> model.supportsJson
        AiJob.SEARCH_INDEXING -> "image" in model.inputs && model.supportsJson && model.zdr
    }

    /** The rows for one job: the checks, Show, search and Sort, then at most [limit] rows. */
    fun listFor(
        job: AiJob,
        models: List<CatalogModel>,
        show: Show,
        sort: Sort,
        query: String,
        allowedIds: Set<String>?,
        today: LocalDate,
        limit: Int = LIST_LIMIT
    ): ListResult {
        val terms = query.lowercase(Locale.US).split(' ').filter { it.isNotBlank() }
        val matching = models.filter { model ->
            isListable(model, today) && canDo(job, model) &&
                (allowedIds == null || model.id in allowedIds) &&
                when (show) {
                    Show.ALL -> true
                    Show.TOP_PAID -> !model.isFree
                    Show.TOP_FREE -> model.isFree
                    Show.TOP_REASONING -> model.reasoning.supported
                } &&
                terms.all { term -> "${model.name} ${model.lab} ${model.id}".lowercase(Locale.US).contains(term) }
        }
        val sorted = when (sort) {
            Sort.MOST_POPULAR -> matching.sortedBy { it.rank }
            Sort.NEWEST -> matching.sortedWith(compareByDescending<CatalogModel> { it.created }.thenBy { it.rank })
            Sort.PRICE_LOW_TO_HIGH -> matching.sortedWith(compareBy<CatalogModel> { it.averagePrice }.thenBy { it.rank })
            Sort.PRICE_HIGH_TO_LOW -> matching.sortedWith(compareByDescending<CatalogModel> { it.averagePrice }.thenBy { it.rank })
        }
        return ListResult(sorted.take(limit), matching.size)
    }

    /** A per-token price as dollars per million tokens: "$0.25", "$0.05", "$0.016", "$10.00". */
    fun perMillion(pricePerToken: Double): String {
        val dollars = pricePerToken * 1_000_000
        return when {
            dollars == 0.0 -> "$0"
            dollars < 0.001 -> "<$0.001"
            dollars < 0.1 -> "$" + String.format(Locale.US, "%.3f", dollars).let { if (it.endsWith('0')) it.dropLast(1) else it }
            else -> "$" + String.format(Locale.US, "%.2f", dollars)
        }
    }

    /** "Google · $0.25 in · $1.50 out per 1M tokens", or "Google · Free". */
    fun priceLine(model: CatalogModel): String =
        if (model.isFree) "${model.lab} · Free"
        else "${model.lab} · ${perMillion(model.promptPrice)} in · ${perMillion(model.completionPrice)} out per 1M tokens"

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE AND DOWNLOAD
    // ═══════════════════════════════════════════════════════════════════════════

    /** The saved list; null until loaded (Compose state for the list screen). */
    var snapshot by mutableStateOf<CatalogSnapshot?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    /** Why the last download failed, or null */
    var error by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile
    private var appContext: Context? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * App start: reads the saved list, so the model chains can skip models OpenRouter no longer
     * lists, and downloads a new one when it is older than a day and AI is in use (a key is saved).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            loadCache()
            UserOpenRouterKey.ensureLoaded()
            if (UserOpenRouterKey.hasKey && isStale()) refresh(force = false)
        }
    }

    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        snapshot?.let { nowMs - it.fetchedAtMs > MAX_AGE_MS } ?: true

    /** Ids OpenRouter lists, or null when there is no list recent enough to trust. */
    fun listedIds(nowMs: Long = System.currentTimeMillis()): Set<String>? =
        trusted(nowMs)?.models?.mapTo(HashSet()) { it.id }

    /** Ids the account's settings allow, or null when not known. */
    fun allowedIds(nowMs: Long = System.currentTimeMillis()): Set<String>? =
        trusted(nowMs)?.allowedIds?.toSet()

    private fun trusted(nowMs: Long): CatalogSnapshot? =
        snapshot?.takeIf { nowMs - it.fetchedAtMs <= TRUST_FOR_SKIPPING_MS && it.models.isNotEmpty() }

    /** Unit tests set the list directly. */
    internal fun setSnapshotForTest(value: CatalogSnapshot?) {
        snapshot = value
    }

    /** Downloads the list (when stale, or always with [force]). Off the main thread. */
    suspend fun refresh(force: Boolean): Boolean = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            if (!force && !isStale()) return@withLock true
            loading = true
            error = null
            try {
                val zdrIds = runCatching { parseIds(get(ZDR_URL, null), "model_id") }.getOrElse {
                    Log.w(TAG, "Zero-data-retention list not loaded: ${it.message}")
                    emptySet()
                }
                val models = parseModels(get(MODELS_URL, null), zdrIds)
                if (models.isEmpty()) throw IllegalStateException("empty list")
                val key = UserOpenRouterKey.value
                val allowed = if (key.isNullOrBlank()) null else runCatching {
                    parseIds(get(USER_MODELS_URL, key), "id").toList()
                }.getOrElse {
                    Log.w(TAG, "Account's allowed models not loaded: ${it.message}")
                    null
                }
                val fresh = CatalogSnapshot(System.currentTimeMillis(), models, allowed?.takeIf { it.isNotEmpty() })
                snapshot = fresh
                saveCache(fresh)
                Log.i(TAG, "Model list updated: ${models.size} models, ${allowed?.size ?: "unknown"} allowed")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Model list download failed: ${e.message}")
                error = "Can't load the model list. Check the connection, then tap Refresh."
                false
            } finally {
                loading = false
            }
        }
    }

    private fun get(url: String, key: String?): String {
        val request = Request.Builder().url(url).get().apply {
            if (key != null) header("Authorization", "Bearer ${key.trim()}")
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun cacheFile(): File? = appContext?.let { File(it.cacheDir, CACHE_FILE) }

    private fun loadCache() {
        if (snapshot != null) return
        val file = cacheFile()?.takeIf { it.exists() } ?: return
        snapshot = try {
            json.decodeFromString(CatalogSnapshot.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Saved model list unreadable: ${e.message}")
            null
        }
    }

    private fun saveCache(value: CatalogSnapshot) {
        val file = cacheFile() ?: return
        try {
            val temp = File(file.parentFile, "$CACHE_FILE.tmp")
            temp.writeText(json.encodeToString(CatalogSnapshot.serializer(), value))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Model list not saved: ${e.message}")
        }
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.strings(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}
