package com.omix.alleq.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omix.alleq.R
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.service.AudioProcessorService
import com.omix.alleq.widget.AllEqWidgetManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// preset switcher shown on the android auto display
class PresetScreen(carContext: CarContext) : Screen(carContext) {

    private val tag = "PresetScreen"

    init {
        ensureServiceRunning()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    AudioProcessorService.allPresets,
                    AudioProcessorService.activePresetName,
                    AudioProcessorService.isEnabled,
                ) { presets, active, enabled -> Triple(presets.map { it.name }, active, enabled) }
                    .distinctUntilChanged()
                    .collect { invalidate() }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val enabled = AudioProcessorService.isEnabled.value
        val activeName = AudioProcessorService.activePresetName.value
        val names = visiblePresetNames(activeName)

        val items = ItemList.Builder()
        names.forEach { items.addItem(Row.Builder().setTitle(it).build()) }
        items.setOnSelectedListener { index -> names.getOrNull(index)?.let(::applyPreset) }
        val selected = names.indexOfFirst { it.equals(activeName, ignoreCase = true) }
        if (selected >= 0) items.setSelectedIndex(selected)

        val toggle = Action.Builder()
            .setTitle(carContext.getString(if (enabled) R.string.notif_action_disable else R.string.notif_action_enable))
            .setOnClickListener(::toggleEnabled)
            .build()

        val title = if (enabled) "allEQ" else "allEQ  ${carContext.getString(R.string.notif_disabled)}"

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(ActionStrip.Builder().addAction(toggle).build())
            .setSingleList(items.build())
            .build()
    }

    // host caps list length while driving, keep the active preset visible even if it is far down
    private fun visiblePresetNames(activeName: String): List<String> {
        val all = AudioProcessorService.allPresets.value.map { it.name }
        val limit = listLimit()
        if (all.size <= limit) return all
        val head = all.take(limit)
        val activeIndex = all.indexOfFirst { it.equals(activeName, ignoreCase = true) }
        return if (activeIndex < limit || activeIndex < 0) head else head.dropLast(1) + all[activeIndex]
    }

    private fun listLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } catch (_: Exception) {
        DEFAULT_LIST_LIMIT
    }

    private fun applyPreset(name: String) {
        val service = AudioProcessorService.instance
        if (service != null) {
            service.applyPreset(name, isAutoSwitch = false)
        } else {
            startService(
                Intent(carContext, AudioProcessorService::class.java)
                    .setAction(AudioProcessorService.ACTION_APPLY_PRESET)
                    .putExtra(AudioProcessorService.EXTRA_PRESET_NAME, name),
            )
        }
        AllEqWidgetManager.updateWidgets(carContext)
    }

    private fun toggleEnabled() {
        val service = AudioProcessorService.instance
        if (service != null) {
            service.toggleEnabled()
        } else {
            startService(
                Intent(carContext, AudioProcessorService::class.java)
                    .setAction(AudioProcessorService.ACTION_START)
                    .putExtra(AudioProcessorService.EXTRA_FORCE_ENABLE, true),
            )
        }
        AllEqWidgetManager.updateWidgets(carContext)
    }

    // presets are loaded by the service, start it if the car screen is opened first
    private fun ensureServiceRunning() {
        if (AudioProcessorService.instance != null) return
        startService(Intent(carContext, AudioProcessorService::class.java).setAction(AudioProcessorService.ACTION_START))
    }

    private fun startService(intent: Intent) {
        try {
            ContextCompat.startForegroundService(carContext, intent)
        } catch (e: Exception) {
            AppLogger.log(tag, "Service start from car screen refused: ${e.message}")
        }
    }

    companion object {
        private const val DEFAULT_LIST_LIMIT = 6
    }
}
