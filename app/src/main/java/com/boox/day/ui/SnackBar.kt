package com.boox.day.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

val LocalSnackContext = staticCompositionLocalOf { SnackState() }

data class SnackConf(
    val id: String = UUID.randomUUID().toString(),
    /**
     * Semantic key for deduplication. If non-null, only one snackbar with this key
     * can be visible at a time. Behavior depends on [replaceExisting].
     */
    val key: String? = null,
    val text: String? = null,
    val duration: Int? = null,
    val content: (@Composable () -> Unit)? = null,
    val actions: List<Pair<String, () -> Unit>>? = null,
    /**
     * When true and a snackbar with the same [key] exists, replaces it with this one.
     * When false, this snackbar is skipped if one with the same key is already showing.
     * Only applies when [key] is non-null.
     */
    val replaceExisting: Boolean = true
) {
    companion object {
        /** Quick confirmations - 4 seconds */
        const val DURATION_QUICK = 4000
        /** Standard actions - 5 seconds */
        const val DURATION_STANDARD = 5000
        /** Error messages - 7 seconds (users need time to read) */
        const val DURATION_ERROR = 7000
    }
}

class SnackState {
    val snackFlow = MutableSharedFlow<SnackConf?>()
    val cancelSnackFlow = MutableSharedFlow<String?>()
    suspend fun displaySnack(conf: SnackConf): suspend () -> Unit {
        snackFlow.emit(conf)
        return suspend {
            Log.d("SnackState", "Removing snack ${conf.id}")
            removeSnack(conf.id)
        }
    }

    companion object {
        private const val TAG = "SnackState"

        /** Maximum snackbars visible at once. Oldest are dropped when exceeded. */
        const val MAX_VISIBLE_SNACKS = 5

        /** Minimum interval between same-key emissions (rate limiting). */
        private const val MIN_EMIT_INTERVAL_MS = 500L

        /** Track last emission time per key for rate limiting. */
        private val lastEmitByKey = mutableMapOf<String, Long>()
        private val rateLimitLock = Any()

        val globalSnackFlow = MutableSharedFlow<SnackConf>(extraBufferCapacity = 10)
        val cancelGlobalSnack = MutableSharedFlow<String>(extraBufferCapacity = 10)

        /**
         * Emit a snackbar with optional rate limiting for same-key messages.
         * Uses [SnackConf.key] if set, otherwise falls back to [SnackConf.text].
         *
         * @param conf The snackbar configuration to emit
         * @param minIntervalMs Minimum interval between same-key emissions (default 500ms)
         * @return true if emitted, false if rate-limited or buffer full
         */
        fun tryEmitRateLimited(
            conf: SnackConf,
            minIntervalMs: Long = MIN_EMIT_INTERVAL_MS
        ): Boolean {
            val effectiveKey = conf.key ?: conf.text ?: return globalSnackFlow.tryEmit(conf)
            val now = System.currentTimeMillis()

            synchronized(rateLimitLock) {
                val lastEmit = lastEmitByKey[effectiveKey] ?: 0L
                if (now - lastEmit < minIntervalMs) {
                    Log.d(TAG, "Rate limited snackbar with key: $effectiveKey")
                    return false
                }
                lastEmitByKey[effectiveKey] = now

                // Cleanup: remove entries older than 10 seconds to prevent memory leak
                if (lastEmitByKey.size > 50) {
                    lastEmitByKey.entries.removeIf { now - it.value > 10_000L }
                }
            }

            return globalSnackFlow.tryEmit(conf)
        }

        fun logAndShowError(
            reason: String,
            message: String,
            logger: (String, String) -> Unit = Log::e
        ) {
            logger(reason, message)
            // It will succeed if the buffer is not full.
            val emitted = globalSnackFlow.tryEmit(SnackConf(text = message, duration = SnackConf.DURATION_ERROR))
            if (!emitted) {
                logger(TAG, "Failed to emit snackbar, buffer is full.")
            }
        }
    }

    fun registerGlobalSnackObserver() {
        CoroutineScope(Dispatchers.Main).launch {
            globalSnackFlow.collect {
                displaySnack(it)
            }
        }
    }

    fun registerCancelGlobalSnackObserver() {
        CoroutineScope(Dispatchers.Main).launch {
            cancelGlobalSnack.collect {
                removeSnack(it)
            }
        }
    }


    private suspend fun removeSnack(id: String) {
        cancelSnackFlow.emit(id)
    }

    /**
     * Shows a snackbar that remains visible until the provided task completes
     * @param text Text to display in the snackbar
     * @param task Suspending task to execute while snackbar is visible
     * @return Result of the task
     * @throws Exception If the task throws an exception, it will be displayed as an error snackbar, and the exception will be rethrown
     */
    suspend fun <T> showSnackDuring(
        text: String,
        task: suspend () -> T,
    ): T {
        val dismissSnack = displaySnack(SnackConf(text = text))
        return try {
            val result = task()
            dismissSnack()
            result
        } catch (e: CancellationException) {
            Log.d("showSnackDuring", "Task cancelled")
            withContext(NonCancellable) {
                dismissSnack()
            }
            throw e
        } catch (e: Exception) {
            Log.e("showSnackDuring", "Task failed", e)
            withContext(NonCancellable) {
                dismissSnack()
                displaySnack(SnackConf(text = "Error: ${e.message}", duration = SnackConf.DURATION_ERROR))
            }
            throw e
        }
    }

    /**
     * Runs [block] while showing a "working" snack, then shows the resulting message as a new snack.
     * Returns the result message from [block].
     */
    suspend fun runWithSnack(
        textDuring: String,
        resultDurationMs: Int = SnackConf.DURATION_STANDARD,
        block: suspend () -> String
    ): String {
        val message = showSnackDuring(text = textDuring) { block() }
        displaySnack(SnackConf(text = message, duration = resultDurationMs))
        return message
    }

}

@Composable
fun SnackBar(state: SnackState) {
    val snacks = remember {
        mutableStateListOf<SnackConf>()
    }

    fun getSnacks() = snacks

    LaunchedEffect(Unit) {
        launch {
            state.cancelSnackFlow.collect { snackId ->
                getSnacks().removeIf { it.id == snackId }
            }
        }
        launch {
            state.snackFlow.collect { snack ->
                if (snack != null) {
                    // Key-based deduplication: handle existing snackbar with same key
                    if (snack.key != null) {
                        val existingIndex = getSnacks().indexOfFirst { it.key == snack.key }
                        if (existingIndex >= 0) {
                            if (snack.replaceExisting) {
                                // Replace existing snackbar with same key
                                getSnacks().removeAt(existingIndex)
                            } else {
                                // Skip - same key already showing
                                return@collect
                            }
                        }
                    }

                    // Queue bounds: drop oldest snackbars if queue is full
                    while (getSnacks().size >= SnackState.MAX_VISIBLE_SNACKS) {
                        getSnacks().removeAt(0)
                    }

                    getSnacks().add(snack)
                    if (snack.duration != null) {
                        launch {
                            delay(snack.duration.toLong())
                            getSnacks().removeIf { it.id == snack.id }
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .padding(3.dp), verticalArrangement = Arrangement.Bottom
    ) {
        snacks.map { snack ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black)
                        .padding(15.dp, 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (snack.text != null) {
                        Row {
                            Text(text = snack.text, color = Color.White)
                            if (snack.actions != null && snack.actions.isEmpty().not()) {
                                snack.actions.map {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = it.first,
                                        color = Color.White,
                                        modifier = Modifier.noRippleClickable { it.second() })
                                }
                            }
                        }

                    } else snack.content?.let { content ->
                        content()
                    }
                }
            }
        }
    }
}

fun showHint(
    text: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    duration: Int = SnackConf.DURATION_QUICK
) {
    scope.launch {
        SnackState.globalSnackFlow.emit(
            SnackConf(
                text = text,
                duration = duration,
            )
        )
    }
}