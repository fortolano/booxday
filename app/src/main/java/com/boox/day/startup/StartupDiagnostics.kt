package com.boox.day.startup

import android.os.Build
import android.util.Log
import com.boox.day.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StartupStage(val label: String) {
    HIDDEN_API("Hidden API setup"),
    RX_MANAGER("Onyx SDK setup"),
    CANVAS_INIT("Canvas initialization"),
    CANVAS_SURFACE("Canvas surface activation")
}

data class StartupFailureReport(
    val stage: StartupStage,
    val summary: String,
    val throwableClass: String,
    val throwableMessage: String?,
    val stackTrace: String,
    val appVersion: String,
    val flavor: String,
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val timestamp: String
) {
    fun toSupportDescription(): String = buildString {
        appendLine("Boox Day failed during startup.")
        appendLine()
        appendLine("Summary: $summary")
        appendLine("Stage: ${stage.label}")
        appendLine("Exception: $throwableClass")
        appendLine("Message: ${throwableMessage ?: "<none>"}")
        appendLine("App Version: $appVersion")
        appendLine("Flavor: $flavor")
        appendLine("Android: $androidVersion (SDK $sdkInt)")
        appendLine("Device: $manufacturer $model")
        appendLine("Timestamp: $timestamp")
        appendLine()
        appendLine("Stack trace:")
        append(stackTrace)
    }
}

sealed interface StartupStatus {
    data object Healthy : StartupStatus
    data class Fatal(val report: StartupFailureReport) : StartupStatus
}

object StartupDiagnostics {
    private const val TAG = "StartupDiagnostics"

    private val _status = MutableStateFlow<StartupStatus>(StartupStatus.Healthy)
    val status: StateFlow<StartupStatus> = _status.asStateFlow()

    fun hasFatalFailure(): Boolean = _status.value is StartupStatus.Fatal

    fun currentFatalReport(): StartupFailureReport? {
        return (_status.value as? StartupStatus.Fatal)?.report
    }

    fun clearFatalFailure() {
        _status.value = StartupStatus.Healthy
    }

    fun reportFatal(stage: StartupStage, throwable: Throwable, summary: String) {
        synchronized(this) {
            if (_status.value is StartupStatus.Fatal) return

            Log.e(TAG, "Fatal startup failure at ${stage.label}: $summary", throwable)

            _status.value = StartupStatus.Fatal(
                StartupFailureReport(
                    stage = stage,
                    summary = summary,
                    throwableClass = throwable.javaClass.name,
                    throwableMessage = throwable.message,
                    stackTrace = throwable.stackTraceToStringSafe(),
                    appVersion = BuildConfig.VERSION_NAME,
                    flavor = BuildConfig.BUILD_TYPE,
                    androidVersion = Build.VERSION.RELEASE ?: "unknown",
                    sdkInt = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER ?: "unknown",
                    model = Build.MODEL ?: "unknown",
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
            )
        }
    }

    private fun Throwable.stackTraceToStringSafe(): String {
        return try {
            val writer = StringWriter()
            printStackTrace(PrintWriter(writer))
            writer.toString()
        } catch (_: Throwable) {
            stackTrace.joinToString(separator = "\n")
        }
    }
}
