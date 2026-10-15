package com.boox.day.ocr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages ML Kit Digital Ink Recognition model downloads and recognizer instances.
 *
 * Thread-safe singleton that handles:
 * - Background model pre-download on app launch
 * - Language model state tracking
 * - Recognizer lifecycle management
 * - Multi-language support (designed for future expansion)
 *
 * MVP: English-only. Architecture supports multiple languages for future.
 */
object DigitalInkModelManager {

    private const val TAG = "DigitalInkModelMgr"

    // Default language (pre-downloaded automatically)
    private const val DEFAULT_LANGUAGE = "en-US"

    // Download quota enforcement (5 language maximum)
    private const val MAX_DOWNLOADED_LANGUAGES = 5

    // Protected languages (cannot be deleted)
    private val PROTECTED_LANGUAGES = setOf("en-US")

    /**
     * Download state for a language model.
     */
    enum class DownloadState {
        NOT_DOWNLOADED,
        DOWNLOADING,
        READY,
        FAILED
    }

    /**
     * Language model info for UI display.
     */
    data class LanguageInfo(
        val tag: String,           // "en-US", "es-ES", "pt-BR", etc.
        val displayName: String,   // "English (US)", "Spanish", etc.
        val downloadSize: String,  // "~20MB"
        val isDownloaded: Boolean,
        val isActive: Boolean,
        val downloadState: DownloadState
    )

    // Active language tag
    private var _activeLanguage = MutableStateFlow(DEFAULT_LANGUAGE)
    val activeLanguage: StateFlow<String> = _activeLanguage.asStateFlow()

    // Download states per language
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    // Models and recognizers (cached)
    private val models = mutableMapOf<String, DigitalInkRecognitionModel>()
    private val recognizers = mutableMapOf<String, DigitalInkRecognizer>()

    // Prevent concurrent downloads
    private val downloadInProgress = AtomicBoolean(false)

    // Combined state for UI
    private val _languageStates = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val languageStates: StateFlow<List<LanguageInfo>> = _languageStates.asStateFlow()

    // Downloaded language count (for quota enforcement)
    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount.asStateFlow()

    init {
        // Initialize download states for all supported languages
        val supportedLanguages = listOf(
            "en-US", "es-ES", "fr-FR", "de-DE", "zh-Hans",
            "ja", "ko", "it-IT", "ru-RU", "ar"
        )
        supportedLanguages.forEach { lang ->
            downloadStates[lang] = MutableStateFlow(DownloadState.NOT_DOWNLOADED)
        }
        // Note: Language states are populated lazily when UI requests them via getAvailableLanguages()
        // This avoids blocking the main thread during app startup with runBlocking I/O calls
    }

    /**
     * Sync download states with actual models on device.
     * Checks which models are downloaded and updates state flows accordingly.
     * Called on app startup to recover state after app restart.
     */
    private suspend fun syncDownloadStatesWithDevice() = withContext(Dispatchers.IO) {
        val supportedLanguages = listOf(
            "en-US", "es-ES", "fr-FR", "de-DE", "zh-Hans",
            "ja", "ko", "it-IT", "ru-RU", "ar"
        )

        supportedLanguages.forEach { lang ->
            try {
                val isDownloaded = isLanguageDownloaded(lang)
                val currentState = downloadStates[lang]?.value

                if (isDownloaded && currentState != DownloadState.READY) {
                    // Model exists on device but state says NOT_DOWNLOADED - sync it
                    downloadStates[lang]?.value = DownloadState.READY
                    initRecognizer(lang)
                    Log.i(TAG, "Synced $lang: found existing model on device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing state for $lang: ${e.message}")
            }
        }

        // Update downloaded count and language states for UI
        _downloadedCount.value = getDownloadedLanguagesCount()
        updateLanguageStates()
        Log.i(TAG, "Download state sync complete: ${_downloadedCount.value}/5 languages")
    }

    /**
     * Pre-download the default language model in background.
     * Also syncs download states with device on startup.
     * Fire-and-forget. Safe to call multiple times.
     *
     * @param scope CoroutineScope for the download operation
     */
    fun preDownloadAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // First sync existing models
            syncDownloadStatesWithDevice()
            // Then ensure default language is downloaded
            downloadLanguage(DEFAULT_LANGUAGE)
        }
    }

    /**
     * Check if the active language model is downloaded and ready.
     */
    suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        isLanguageDownloaded(_activeLanguage.value)
    }

    /**
     * Check if a specific language model is downloaded.
     */
    suspend fun isLanguageDownloaded(languageTag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = getOrCreateModel(languageTag) ?: return@withContext false
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.isModelDownloaded(model).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if model downloaded for $languageTag: ${e.message}")
            false
        }
    }

    /**
     * Download a language model. Blocks until complete.
     *
     * @param languageTag Language tag (e.g., "en-US")
     * @return true if download succeeded or already downloaded
     */
    suspend fun downloadLanguage(languageTag: String): Boolean = withContext(Dispatchers.IO) {
        // Check quota BEFORE download
        val currentDownloadedCount = getDownloadedLanguagesCount()
        if (currentDownloadedCount >= MAX_DOWNLOADED_LANGUAGES && !isLanguageDownloaded(languageTag)) {
            Log.w(TAG, "Cannot download $languageTag - quota reached ($currentDownloadedCount/$MAX_DOWNLOADED_LANGUAGES)")
            return@withContext false
        }

        // Prevent concurrent downloads of the same language
        if (downloadInProgress.getAndSet(true)) {
            // Another download is in progress, wait for it
            Log.d(TAG, "Download already in progress, skipping")
            return@withContext isLanguageDownloaded(languageTag)
        }

        try {
            val stateFlow = downloadStates.getOrPut(languageTag) {
                MutableStateFlow(DownloadState.NOT_DOWNLOADED)
            }

            // Check if already downloaded
            if (isLanguageDownloaded(languageTag)) {
                Log.i(TAG, "$languageTag model already downloaded")
                stateFlow.value = DownloadState.READY
                initRecognizer(languageTag)
                _downloadedCount.value = getDownloadedLanguagesCount()
                updateLanguageStates()
                notifyModelReady(languageTag)
                return@withContext true
            }

            stateFlow.value = DownloadState.DOWNLOADING
            updateLanguageStates()
            Log.i(TAG, "Downloading Digital Ink model for $languageTag...")

            val model = getOrCreateModel(languageTag)
            if (model == null) {
                Log.e(TAG, "No model identifier found for $languageTag")
                stateFlow.value = DownloadState.FAILED
                updateLanguageStates()
                return@withContext false
            }

            val remoteModelManager = RemoteModelManager.getInstance()
            val conditions = DownloadConditions.Builder().build()

            remoteModelManager.download(model, conditions).await()
            Log.i(TAG, "Model download complete for $languageTag")

            stateFlow.value = DownloadState.READY
            initRecognizer(languageTag)
            _downloadedCount.value = getDownloadedLanguagesCount()
            updateLanguageStates()
            notifyModelReady(languageTag)
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model for $languageTag: ${e.message}", e)
            downloadStates[languageTag]?.value = DownloadState.FAILED
            updateLanguageStates()
            return@withContext false
        } finally {
            downloadInProgress.set(false)
        }
    }

    /**
     * Get the recognizer for the active language.
     * Returns null if model not ready.
     */
    fun getActiveRecognizer(): DigitalInkRecognizer? {
        return recognizers[_activeLanguage.value]
    }

    /**
     * Get recognizer for a specific language.
     * Returns null if model not ready.
     */
    fun getRecognizer(languageTag: String): DigitalInkRecognizer? {
        return recognizers[languageTag]
    }

    /**
     * Get all available languages for UI.
     * Returns 10 supported languages: English (Full), 9 others (Basic).
     * Uses cached downloadStates for instant performance (no file I/O).
     */
    fun getAvailableLanguages(): List<LanguageInfo> {
        val allLanguages = listOf(
            "en-US" to "English (US) - Full Support",
            "de-DE" to "German (Germany) - Basic",
            "es-ES" to "Spanish (Spain) - Basic",
            "fr-FR" to "French (France) - Basic",
            "it-IT" to "Italian (Italy) - Basic",
            "ru-RU" to "Russian (Russia) - Basic",
            "ja" to "Japanese - Basic",
            "ko" to "Korean - Basic",
            "zh-Hans" to "Chinese (Simplified) - Basic",
            "ar" to "Arabic - Basic"
        )

        return allLanguages.map { (tag, displayName) ->
            val state = downloadStates[tag]?.value ?: DownloadState.NOT_DOWNLOADED
            LanguageInfo(
                tag = tag,
                displayName = displayName,
                downloadSize = "~20MB",
                isDownloaded = state == DownloadState.READY,
                isActive = _activeLanguage.value == tag,
                downloadState = state
            )
        }
    }

    /**
     * Get download state for a language.
     */
    fun getDownloadState(languageTag: String): DownloadState {
        return downloadStates[languageTag]?.value ?: DownloadState.NOT_DOWNLOADED
    }

    /**
     * Set the active language for recognition.
     * Must be downloaded first.
     */
    suspend fun setActiveLanguage(languageTag: String): Boolean {
        if (!isLanguageDownloaded(languageTag)) {
            Log.w(TAG, "Cannot set active language $languageTag - not downloaded")
            return false
        }

        _activeLanguage.value = languageTag
        updateLanguageStates()
        Log.i(TAG, "Active language set to $languageTag")
        return true
    }

    /**
     * Sync active language from GlobalAppSettings.
     * Called from MainActivity after settings are loaded to restore user's language preference.
     */
    suspend fun syncLanguageFromSettings() {
        val savedLanguage = com.boox.day.data.datastore.GlobalAppSettings.current.handwritingLanguage

        // Only update if:
        // 1. Saved language is different from current
        // 2. Saved language is downloaded (otherwise fall back to current)
        if (savedLanguage != _activeLanguage.value && isLanguageDownloaded(savedLanguage)) {
            _activeLanguage.value = savedLanguage
            updateLanguageStates()
            Log.i(TAG, "Active language synced from settings: $savedLanguage")
        }
    }

    /**
     * Delete a downloaded language model (to free space).
     * Cannot delete protected languages (English) or the active language.
     */
    suspend fun deleteLanguage(languageTag: String): Boolean = withContext(Dispatchers.IO) {
        // Protect English from deletion
        if (languageTag in PROTECTED_LANGUAGES) {
            Log.w(TAG, "Cannot delete protected language: $languageTag")
            return@withContext false
        }

        if (languageTag == _activeLanguage.value) {
            Log.w(TAG, "Cannot delete active language $languageTag")
            return@withContext false
        }

        try {
            val model = models[languageTag] ?: return@withContext false
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.deleteDownloadedModel(model).await()

            // Clean up - just remove from cache (no close() method on DigitalInkRecognizer)
            recognizers.remove(languageTag)
            downloadStates[languageTag]?.value = DownloadState.NOT_DOWNLOADED
            _downloadedCount.value = getDownloadedLanguagesCount()
            updateLanguageStates()

            Log.i(TAG, "Deleted model for $languageTag")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model for $languageTag: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Clear all cached recognizers.
     * Note: DigitalInkRecognizer has no close() method - just clear the cache.
     */
    fun close() {
        recognizers.clear()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun getOrCreateModel(languageTag: String): DigitalInkRecognitionModel? {
        return models.getOrPut(languageTag) {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            if (identifier == null) {
                Log.e(TAG, "No model identifier for language: $languageTag")
                return null
            }
            DigitalInkRecognitionModel.builder(identifier).build()
        }
    }

    private fun initRecognizer(languageTag: String) {
        val model = models[languageTag] ?: return
        if (recognizers.containsKey(languageTag)) return

        recognizers[languageTag] = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        Log.d(TAG, "Recognizer initialized for $languageTag")
    }

    private fun updateLanguageStates() {
        _languageStates.value = getAvailableLanguages()
    }

    /**
     * Notify TaskTextExtractor that a model is ready.
     * Only notifies for the default language (en-US) which is used by Tasks.
     */
    private fun notifyModelReady(languageTag: String) {
        // Only notify for default language (Task extraction uses en-US)
        if (languageTag == DEFAULT_LANGUAGE) {
            TaskTextExtractor.getInstanceOrNull()?.onModelReady()
        }
    }

    /**
     * Count how many languages are currently downloaded.
     * Uses cached downloadStates instead of file I/O for performance.
     */
    private fun getDownloadedLanguagesCount(): Int {
        return downloadStates.count { (_, stateFlow) ->
            stateFlow.value == DownloadState.READY
        }
    }

    /**
     * Check if quota is reached (5 languages downloaded).
     * Uses cached downloadStates for instant check (no I/O).
     */
    fun isQuotaReached(): Boolean {
        return getDownloadedLanguagesCount() >= MAX_DOWNLOADED_LANGUAGES
    }

    /**
     * Get remaining download quota (0-5).
     * Uses cached downloadStates for instant check (no I/O).
     */
    fun getRemainingQuota(): Int {
        return (MAX_DOWNLOADED_LANGUAGES - getDownloadedLanguagesCount()).coerceAtLeast(0)
    }
}
