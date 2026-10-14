package com.omix.alleq.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.omix.alleq.R
import com.omix.alleq.service.AudioProcessorService
import com.omix.alleq.ui.FloatingPresetActivity

object AllEqWidgetManager {

    fun updateWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        // 2x1 widgets
        val ids2x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, AllEqWidgetProvider2x1::class.java))
        if (ids2x1 != null && ids2x1.isNotEmpty()) {
            val views = build2x1Views(context)
            for (id in ids2x1) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        // 2x2 widgets
        val ids2x2 = appWidgetManager.getAppWidgetIds(ComponentName(context, AllEqWidgetProvider2x2::class.java))
        if (ids2x2 != null && ids2x2.isNotEmpty()) {
            val views = build2x2Views(context)
            for (id in ids2x2) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        // 4x1 widgets
        val ids4x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, AllEqWidgetProvider4x1::class.java))
        if (ids4x1 != null && ids4x1.isNotEmpty()) {
            val views = build4x1Views(context)
            for (id in ids4x1) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun getWidgetState(context: Context): Triple<Boolean, String, String> {
        val isServiceRunning = AudioProcessorService.isServiceRunning
        val prefs = context.getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)

        val isEnabled = if (isServiceRunning) {
            AudioProcessorService.isEnabled.value && !AudioProcessorService.isTemporaryBypass.value
        } else {
            prefs.getBoolean("is_enabled", true)
        }

        val presetName = if (isServiceRunning) {
            AudioProcessorService.activePresetName.value
        } else {
            prefs.getString("preset_name", "Flat") ?: "Flat"
        }

        val deviceName = if (isServiceRunning) {
            AudioProcessorService.currentAudioDevice.value
        } else {
            prefs.getString("device_name", context.getString(R.string.device_speaker)) ?: context.getString(R.string.device_speaker)
        }

        return Triple(isEnabled, presetName, deviceName)
    }

    private fun getDeviceIconRes(deviceName: String): Int {
        val lower = deviceName.lowercase()
        return when {
            lower.contains("bluetooth") -> R.drawable.ic_widget_bluetooth
            lower.contains("проводные") || lower.contains("wired") || lower.contains("наушники") || lower.contains("headphone") -> R.drawable.ic_widget_headphones
            else -> R.drawable.ic_widget_speaker
        }
    }

    private fun createPowerPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AudioProcessorService::class.java).apply {
            action = AudioProcessorService.ACTION_TOGGLE
        }
        return PendingIntent.getService(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createPresetPickerPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FloatingPresetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createCyclePresetPendingIntent(context: Context, direction: Int): PendingIntent {
        val intent = Intent(context, AudioProcessorService::class.java).apply {
            action = if (direction > 0) AudioProcessorService.ACTION_NEXT_PRESET else AudioProcessorService.ACTION_PREV_PRESET
        }
        val requestCode = if (direction > 0) 103 else 104
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun build2x1Views(context: Context): RemoteViews {
        val (isEnabled, presetName, deviceName) = getWidgetState(context)
        val views = RemoteViews(context.packageName, R.layout.widget_2x1)

        val powerBg = if (isEnabled) R.drawable.widget_power_bg_on else R.drawable.widget_power_bg_off
        val powerIconTint = if (isEnabled) Color.WHITE else Color.parseColor("#666666")
        views.setInt(R.id.widget_btn_power, "setBackgroundResource", powerBg)
        views.setInt(R.id.widget_power_icon, "setColorFilter", powerIconTint)
        views.setOnClickPendingIntent(R.id.widget_btn_power, createPowerPendingIntent(context))

        views.setImageViewResource(R.id.widget_device_icon, getDeviceIconRes(deviceName))
        views.setInt(R.id.widget_device_icon, "setColorFilter", Color.parseColor("#888888"))
        views.setTextViewText(R.id.widget_device_name, deviceName.uppercase())

        views.setTextViewText(R.id.widget_preset_name, presetName.uppercase())
        val presetColor = if (isEnabled) Color.WHITE else Color.parseColor("#777777")
        val tuneTint = if (isEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#555555")
        views.setTextColor(R.id.widget_preset_name, presetColor)
        views.setInt(R.id.widget_preset_tune_icon, "setColorFilter", tuneTint)
        views.setOnClickPendingIntent(R.id.widget_btn_preset, createPresetPickerPendingIntent(context))

        return views
    }

    fun build2x2Views(context: Context): RemoteViews {
        val (isEnabled, presetName, deviceName) = getWidgetState(context)
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)

        views.setImageViewResource(R.id.widget_device_icon, getDeviceIconRes(deviceName))
        views.setInt(R.id.widget_device_icon, "setColorFilter", Color.parseColor("#AAAAAA"))
        views.setTextViewText(R.id.widget_device_name, deviceName.uppercase())

        val powerBg = if (isEnabled) R.drawable.widget_power_bg_on else R.drawable.widget_power_bg_off
        val powerIconTint = if (isEnabled) Color.WHITE else Color.parseColor("#666666")
        val statusText = if (isEnabled) "DSP ACTIVE" else "BYPASS"
        val statusColor = if (isEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#777777")

        views.setInt(R.id.widget_power_ring, "setBackgroundResource", powerBg)
        views.setInt(R.id.widget_power_icon, "setColorFilter", powerIconTint)
        views.setTextViewText(R.id.widget_status_text, statusText)
        views.setTextColor(R.id.widget_status_text, statusColor)
        views.setOnClickPendingIntent(R.id.widget_btn_power, createPowerPendingIntent(context))

        views.setTextViewText(R.id.widget_preset_name, presetName.uppercase())
        val presetColor = if (isEnabled) Color.WHITE else Color.parseColor("#777777")
        val tuneTint = if (isEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#555555")
        views.setTextColor(R.id.widget_preset_name, presetColor)
        views.setInt(R.id.widget_tune_icon, "setColorFilter", tuneTint)
        views.setTextColor(R.id.widget_change_text, tuneTint)
        views.setOnClickPendingIntent(R.id.widget_btn_preset, createPresetPickerPendingIntent(context))

        return views
    }

    fun build4x1Views(context: Context): RemoteViews {
        val (isEnabled, presetName, deviceName) = getWidgetState(context)
        val views = RemoteViews(context.packageName, R.layout.widget_4x1)

        val powerBg = if (isEnabled) R.drawable.widget_power_bg_on else R.drawable.widget_power_bg_off
        val powerIconTint = if (isEnabled) Color.WHITE else Color.parseColor("#666666")
        views.setInt(R.id.widget_btn_power, "setBackgroundResource", powerBg)
        views.setInt(R.id.widget_power_icon, "setColorFilter", powerIconTint)
        views.setOnClickPendingIntent(R.id.widget_btn_power, createPowerPendingIntent(context))

        views.setImageViewResource(R.id.widget_device_icon, getDeviceIconRes(deviceName))
        views.setInt(R.id.widget_device_icon, "setColorFilter", Color.parseColor("#AAAAAA"))
        views.setTextViewText(R.id.widget_device_name, deviceName.uppercase())

        views.setTextViewText(R.id.widget_preset_name, presetName.uppercase())
        val presetColor = if (isEnabled) Color.WHITE else Color.parseColor("#777777")
        val tuneTint = if (isEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#555555")
        views.setTextColor(R.id.widget_preset_name, presetColor)
        views.setInt(R.id.widget_preset_tune_icon, "setColorFilter", tuneTint)
        views.setOnClickPendingIntent(R.id.widget_btn_preset, createPresetPickerPendingIntent(context))

        views.setInt(R.id.widget_btn_prev, "setColorFilter", Color.parseColor("#AAAAAA"))
        views.setInt(R.id.widget_btn_next, "setColorFilter", Color.parseColor("#AAAAAA"))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, createCyclePresetPendingIntent(context, -1))
        views.setOnClickPendingIntent(R.id.widget_btn_next, createCyclePresetPendingIntent(context, 1))

        return views
    }
}
