package com.omix.alleq.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {

    const val REQUEST_CODE_PERMISSION = 1001

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isRunning()) return false
        return try {
            if (Shizuku.isPreV11()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (isRunning() && !hasPermission()) {
            try {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            } catch (_: Exception) {
            }
        }
    }

    fun runShellCommand(command: String): Result<String> {
        if (!hasPermission()) {
            return Result.failure(IllegalStateException("Shizuku permission not granted"))
        }

        var process: Process? = null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(output.trim())
            } else {
                Result.failure(RuntimeException("Shell command error ($exitCode): $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {}
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    // whitelist app from aggressive background killing
    fun applyOriginOsWhitelist(context: Context): Result<String> {
        val pkg = context.packageName
        val commands = listOf(
            "cmd deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg START_FOREGROUND allow",
            "am set-standby-bucket $pkg active"
        )

        val results = mutableListOf<String>()
        var criticalSuccess = false

        commands.forEach { cmd ->
            val res = runShellCommand(cmd)
            if (res.isSuccess) {
                results.add("$cmd -> OK")
                if (cmd.contains("deviceidle")) criticalSuccess = true
            } else {
                results.add("$cmd -> SKIPPED (${res.exceptionOrNull()?.message})")
            }
        }

        return if (criticalSuccess) {
            Result.success(results.joinToString("\n"))
        } else {
            Result.failure(Exception("Failed to apply deviceidle whitelist"))
        }
    }
}
