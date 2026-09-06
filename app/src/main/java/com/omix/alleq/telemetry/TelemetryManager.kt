package com.omix.alleq.telemetry

import android.content.Context
import android.util.Log
import com.omix.alleq.logger.AppLogger
import java.io.File

object TelemetryManager {

    private const val TAG = "TelemetryManager"
    private const val LAST_CRASH_FILE = "last_crash.txt"

    private lateinit var appContext: Context
    private var isInitialized = false

    var currentPreset: String = "Default"
    var isDspEnabled: Boolean = true

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true

        CrashHandler.install(this)
        AppLogger.log(TAG, "Offline CrashHandler initialized successfully")
    }

    fun recordCrashSync(throwable: Throwable) {
        if (!isInitialized) return
        try {
            val report = buildString {
                appendLine("=== FATAL CRASH REPORT ===")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("Preset: $currentPreset, DSP Enabled: $isDspEnabled")
                appendLine("Stacktrace:")
                appendLine(Log.getStackTraceString(throwable))
            }
            AppLogger.log(TAG, report)

            val file = File(appContext.filesDir, LAST_CRASH_FILE)
            file.writeText(report)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local crash dump", e)
        }
    }

    fun getLastCrashReport(): String? {
        if (!isInitialized) return null
        val file = File(appContext.filesDir, LAST_CRASH_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clearLastCrashReport() {
        if (!isInitialized) return
        val file = File(appContext.filesDir, LAST_CRASH_FILE)
        if (file.exists()) file.delete()
    }
}

