package com.omix.alleq.telemetry

import android.util.Log
import com.omix.alleq.logger.AppLogger

class CrashHandler private constructor(
    private val telemetryManager: TelemetryManager,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            AppLogger.log("CrashHandler", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}")
            telemetryManager.recordCrashSync(throwable)
        } catch (e: Exception) {
            Log.e("CrashHandler", "Error processing uncaught exception", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install(manager: TelemetryManager) {
            val currentDefault = Thread.getDefaultUncaughtExceptionHandler()
            if (currentDefault !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(manager, currentDefault))
            }
        }
    }
}
