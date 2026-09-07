package com.omix.alleq.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.media.audiofx.AudioEffect
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omix.alleq.MainActivity
import com.omix.alleq.R
import com.omix.alleq.audio.DynamicsEngine
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.telemetry.TelemetryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class AudioProcessorService : Service() {

    private val tag = "AudioProcessorService"
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessorService = this@AudioProcessorService
    }

    private lateinit var audioManager: AudioManager
    private val engines = ConcurrentHashMap<Int, DynamicsEngine>()

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    companion object {
        const val CHANNEL_ID = "alleq_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.omix.alleq.ACTION_START"
        const val ACTION_STOP = "com.omix.alleq.ACTION_STOP"
        const val ACTION_TOGGLE = "com.omix.alleq.ACTION_TOGGLE"

        private val _isEnabled = MutableStateFlow(true)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _bandGains = MutableStateFlow(EqualizerDefaults.PRESETS[0].bandGains)
        val bandGains: StateFlow<List<Float>> = _bandGains.asStateFlow()

        private val _preAmpDb = MutableStateFlow(0f)
        val preAmpDb: StateFlow<Float> = _preAmpDb.asStateFlow()

        private val _bassBoostPercent = MutableStateFlow(0f)
        val bassBoostPercent: StateFlow<Float> = _bassBoostPercent.asStateFlow()

        private val _limiterEnabled = MutableStateFlow(false)
        val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

        private val _activePresetName = MutableStateFlow("Flat")
        val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

        private val _activeSessionsCount = MutableStateFlow(0)
        val activeSessionsCount: StateFlow<Int> = _activeSessionsCount.asStateFlow()

        private val _allPresets = MutableStateFlow<List<EqualizerPreset>>(EqualizerDefaults.PRESETS)
        val allPresets: StateFlow<List<EqualizerPreset>> = _allPresets.asStateFlow()

        private val _deletedPresets = MutableStateFlow<List<EqualizerPreset>>(emptyList())
        val deletedPresets: StateFlow<List<EqualizerPreset>> = _deletedPresets.asStateFlow()

        private val _customPresets = MutableStateFlow<List<EqualizerPreset>>(emptyList())
        val customPresets: StateFlow<List<EqualizerPreset>> = _customPresets.asStateFlow()

        private val _currentAudioDevice = MutableStateFlow("Speaker")
        val currentAudioDevice: StateFlow<String> = _currentAudioDevice.asStateFlow()

        private val _isTemporaryBypass = MutableStateFlow(false)
        val isTemporaryBypass: StateFlow<Boolean> = _isTemporaryBypass.asStateFlow()

        private val _dynamicBassEnabled = MutableStateFlow(true)
        val dynamicBassEnabled: StateFlow<Boolean> = _dynamicBassEnabled.asStateFlow()

        private val _dynamicBassStrength = MutableStateFlow(0.6f)
        val dynamicBassStrength: StateFlow<Float> = _dynamicBassStrength.asStateFlow()

        private val _currentVolumeRatio = MutableStateFlow(1.0f)
        val currentVolumeRatio: StateFlow<Float> = _currentVolumeRatio.asStateFlow()

        private val _dynamicBassModeStatus = MutableStateFlow("Standby")
        val dynamicBassModeStatus: StateFlow<String> = _dynamicBassModeStatus.asStateFlow()
    }

    private var deviceCallback: AudioDeviceCallback? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var audioSessionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadSavedSettings()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initAudioEngines()
        registerPlaybackCallback()
        registerAudioSessionReceiver()
        registerDeviceCallback()
        registerVolumeReceiver()
        updateAudioDeviceDescription()
        updateDynamicBass()
        AppLogger.log(tag, "AudioProcessorService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                toggleEnabled()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, AudioProcessorService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateText = if (_isEnabled.value) getString(R.string.notif_enabled, _activePresetName.value) else getString(R.string.notif_disabled)
        val toggleActionText = if (_isEnabled.value) getString(R.string.notif_action_disable) else getString(R.string.notif_action_enable)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("allEQ • $stateText")
            .setContentText(getString(R.string.notif_active_sessions, _activeSessionsCount.value, _currentAudioDevice.value))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_play, toggleActionText, togglePendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateAudioDeviceDescription() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDevice = devices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        val desc = when (activeDevice?.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> getString(R.string.device_speaker)
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> getString(R.string.device_bluetooth)
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> getString(R.string.device_wired)
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> getString(R.string.device_usb)
            else -> getString(R.string.device_speaker)
        }
        _currentAudioDevice.value = desc
        AppLogger.log(tag, "Audio output: $desc")
        updateDynamicBass()
    }

    private fun registerVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    updateDynamicBass()
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        ContextCompat.registerReceiver(this, volumeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
            volumeReceiver = null
        }
    }

    private fun registerDeviceCallback() {
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioDeviceDescription()
                updateNotification()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioDeviceDescription()
                updateNotification()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun initAudioEngines() {
        attachSession(0)
    }

    @SuppressLint("BlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi")
    private fun extractSessionId(config: AudioPlaybackConfiguration): Int {
        return try {
            val method = config.javaClass.getMethod("getAudioSessionId")
            (method.invoke(config) as? Int) ?: 0
        } catch (_: Exception) {
            try {
                val fieldName = "m" + "SessionId"
                val field = config.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.getInt(config)
            } catch (_: Exception) {
                0
            }
        }
    }

    private fun updateSessionArbitration(isAudioPlayingOverride: Boolean? = null) {
        val specificSessions = engines.keys.filter { it != 0 }
        val hasSpecificSessions = specificSessions.isNotEmpty()
        val shouldBeActive = _isEnabled.value && !_isTemporaryBypass.value

        // prevent double eq: when specific sessions are active, mute session 0
        engines[0]?.setEnabled(shouldBeActive && !hasSpecificSessions)

        // all active specific sessions receive user's DSP settings
        specificSessions.forEach { sid ->
            engines[sid]?.setEnabled(shouldBeActive)
        }

        val playing = isAudioPlayingOverride ?: (audioManager.isMusicActive || hasSpecificSessions)
        _activeSessionsCount.value = if (shouldBeActive && playing) {
            if (hasSpecificSessions) specificSessions.size else 1
        } else 0

        updateDynamicBass()
        updateNotification()
    }

    private fun registerPlaybackCallback() {
        try {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    configs ?: return

                    // collect currently playing audio sessions from configs
                    val activeSessionIds = configs
                        .map { extractSessionId(it) }
                        .filter { it > 0 }
                        .toSet()

                    // attach to new media sessions (forces android to disable offload for them)
                    activeSessionIds.forEach { sessionId ->
                        if (!engines.containsKey(sessionId)) {
                            attachSession(sessionId)
                        }
                    }

                    // detach closed media sessions (except global session 0)
                    engines.keys.toList().forEach { sessionId ->
                        if (sessionId != 0 && !activeSessionIds.contains(sessionId)) {
                            detachSession(sessionId)
                        }
                    }

                    val isAudioPlaying = audioManager.isMusicActive || configs.isNotEmpty()
                    updateSessionArbitration(isAudioPlaying)
                }
            }
            audioManager.registerAudioPlaybackCallback(playbackCallback!!, null)
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to register AudioPlaybackCallback: ${e.message}")
        }
    }

    private fun registerAudioSessionReceiver() {
        audioSessionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val sessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioManager.ERROR) ?: return
                if (sessionId <= 0) return
                when (intent.action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                        if (!engines.containsKey(sessionId)) {
                            attachSession(sessionId)
                            updateSessionArbitration()
                        }
                    }
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                        detachSession(sessionId)
                        updateSessionArbitration()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, audioSessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterAudioSessionReceiver() {
        audioSessionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            audioSessionReceiver = null
        }
    }

    private fun attachSession(sessionId: Int) {
        if (engines.containsKey(sessionId)) return
        try {
            val engine = DynamicsEngine(sessionId)
            val hasSpecificSessions = engines.keys.any { it != 0 } || (sessionId != 0)
            val isSessionActive = _isEnabled.value && !_isTemporaryBypass.value && (if (sessionId == 0) !hasSpecificSessions else true)

            val success = engine.initialize(
                bandGains = _bandGains.value,
                preAmpDb = _preAmpDb.value,
                bassBoostPercent = _bassBoostPercent.value,
                limiterEnabled = _limiterEnabled.value,
                isEnabled = isSessionActive
            )
            if (success) {
                engines[sessionId] = engine
                _activeSessionsCount.value = engines.size
                AppLogger.log(tag, "Engine connected to session $sessionId (Total active: ${engines.size})")
                updateDynamicBass()
            }
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to attach session $sessionId: ${e.message}")
        }
    }

    private fun detachSession(sessionId: Int) {
        try {
            engines.remove(sessionId)?.release()
            _activeSessionsCount.value = engines.size
            AppLogger.log(tag, "Engine detached from session $sessionId (Total active: ${engines.size})")
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to detach session $sessionId: ${e.message}")
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean("is_enabled", true)
        _preAmpDb.value = prefs.getFloat("pre_amp_db", 0f)
        _bassBoostPercent.value = prefs.getFloat("bass_boost_percent", 0f)
        _limiterEnabled.value = prefs.getBoolean("limiter_enabled", false)
        _activePresetName.value = prefs.getString("preset_name", "Flat") ?: "Flat"
        _dynamicBassEnabled.value = prefs.getBoolean("dynamic_bass_enabled", true)
        _dynamicBassStrength.value = prefs.getFloat("dynamic_bass_strength", 0.6f)

        TelemetryManager.currentPreset = _activePresetName.value
        TelemetryManager.isDspEnabled = _isEnabled.value

        val gainsStr = prefs.getString("band_gains", null)
        if (gainsStr != null) {
            try {
                val parsed = gainsStr.split(",").map { it.toFloat() }
                if (parsed.size == 10) {
                    _bandGains.value = parsed
                }
            } catch (_: Exception) {
            }
        }

        val savedAllJson = prefs.getString("all_presets_json", null)
        if (!savedAllJson.isNullOrBlank()) {
            val loaded = EqualizerDefaults.allPresetsFromJson(savedAllJson)
            if (loaded.isNotEmpty()) {
                _allPresets.value = loaded
            } else {
                _allPresets.value = EqualizerDefaults.PRESETS
            }
        } else {
            val customJson = prefs.getString("custom_presets_json", null)
            val legacy = EqualizerDefaults.customPresetsFromJson(customJson)
            _allPresets.value = EqualizerDefaults.PRESETS + legacy
        }

        val deletedJson = prefs.getString("deleted_presets_json", null)
        _deletedPresets.value = EqualizerDefaults.allPresetsFromJson(deletedJson)
        _customPresets.value = _allPresets.value.filter { it.isCustom }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_enabled", _isEnabled.value)
            .putFloat("pre_amp_db", _preAmpDb.value)
            .putFloat("bass_boost_percent", _bassBoostPercent.value)
            .putBoolean("limiter_enabled", _limiterEnabled.value)
            .putString("preset_name", _activePresetName.value)
            .putString("band_gains", _bandGains.value.joinToString(","))
            .putBoolean("dynamic_bass_enabled", _dynamicBassEnabled.value)
            .putFloat("dynamic_bass_strength", _dynamicBassStrength.value)
            .apply()
    }

    private fun savePresetLists() {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("custom_presets_json", EqualizerDefaults.customPresetsToJson(_customPresets.value))
            .putString("all_presets_json", EqualizerDefaults.allPresetsToJson(_allPresets.value))
            .putString("deleted_presets_json", EqualizerDefaults.allPresetsToJson(_deletedPresets.value))
            .apply()
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        updateSessionArbitration()
        saveSettings()
        AppLogger.log(tag, "Master switch: ${if (enabled) "ON" else "OFF"}")
        TelemetryManager.isDspEnabled = enabled
    }

    fun setTemporaryBypass(bypass: Boolean) {
        _isTemporaryBypass.value = bypass
        updateSessionArbitration()
        if (bypass) {
            AppLogger.log(tag, "A/B Bypass: raw original sound active")
        } else {
            AppLogger.log(tag, "A/B Bypass: restored preset ${_activePresetName.value}")
        }
    }

    fun updateBands(gains: List<Float>) {
        _bandGains.value = gains
        engines.values.forEach { it.updateBands(gains, _bassBoostPercent.value) }
        saveSettings()
    }

    fun updateBand(index: Int, gain: Float) {
        val current = _bandGains.value.toMutableList()
        if (index in current.indices) {
            current[index] = gain
            updateBands(current)
        }
    }

    fun setPreAmp(db: Float) {
        _preAmpDb.value = db
        engines.values.forEach { it.updatePreAmpAndLimiter(db, _limiterEnabled.value) }
        saveSettings()
    }

    fun setBassBoost(percent: Float) {
        _bassBoostPercent.value = percent
        engines.values.forEach { it.updateBands(_bandGains.value, percent) }
        saveSettings()
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        engines.values.forEach { it.updatePreAmpAndLimiter(_preAmpDb.value, enabled) }
        saveSettings()
    }

    fun applyPreset(presetName: String) {
        val preset = _allPresets.value.find { it.name.equals(presetName, ignoreCase = true) }
            ?: _deletedPresets.value.find { it.name.equals(presetName, ignoreCase = true) }
            ?: EqualizerDefaults.PRESETS.find { it.name.equals(presetName, ignoreCase = true) }
            ?: return
        _activePresetName.value = preset.name
        _preAmpDb.value = preset.preAmpDb
        _bassBoostPercent.value = preset.bassBoostPercent
        _limiterEnabled.value = preset.limiterEnabled
        updateBands(preset.bandGains)
        engines.values.forEach { it.updatePreAmpAndLimiter(preset.preAmpDb, preset.limiterEnabled) }
        saveSettings()
        updateNotification()
        AppLogger.log(tag, "Applied preset: ${preset.name}")
        TelemetryManager.currentPreset = preset.name
    }

    fun saveCustomPreset(name: String): Boolean {
        if (name.isBlank()) return false
        val newPreset = EqualizerPreset(
            name = name.trim(),
            description = "Custom preset",
            bandGains = _bandGains.value,
            preAmpDb = _preAmpDb.value,
            bassBoostPercent = _bassBoostPercent.value,
            limiterEnabled = _limiterEnabled.value,
            isCustom = true
        )
        val updated = _allPresets.value.filter { !it.name.equals(newPreset.name, ignoreCase = true) } + newPreset
        _allPresets.value = updated
        _customPresets.value = updated.filter { it.isCustom }
        _activePresetName.value = newPreset.name
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Created preset: ${newPreset.name}")
        return true
    }

    // import an external preset into custom presets list
    fun importPreset(preset: EqualizerPreset, applyNow: Boolean = false): Boolean {
        val cleanPreset = preset.copy(isCustom = true)
        val updated = _allPresets.value.filter { !it.name.equals(cleanPreset.name, ignoreCase = true) } + cleanPreset
        _allPresets.value = updated
        _customPresets.value = updated.filter { it.isCustom }
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Imported preset: ${cleanPreset.name}")
        if (applyNow) {
            applyPreset(cleanPreset.name)
        }
        return true
    }

    // import multiple external presets at once
    fun importPresets(presets: List<EqualizerPreset>): Int {
        if (presets.isEmpty()) return 0
        var currentAll = _allPresets.value
        var importedCount = 0
        presets.forEach { p ->
            val clean = p.copy(isCustom = true)
            currentAll = currentAll.filter { !it.name.equals(clean.name, ignoreCase = true) } + clean
            importedCount++
        }
        _allPresets.value = currentAll
        _customPresets.value = currentAll.filter { it.isCustom }
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Imported $importedCount presets from pack")
        return importedCount
    }

    fun reorderPresets(newOrder: List<EqualizerPreset>) {
        _allPresets.value = newOrder
        _customPresets.value = newOrder.filter { it.isCustom }
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Preset order updated: ${newOrder.map { it.name }}")
    }

    fun deletePreset(name: String) {
        val toDelete = _allPresets.value.find { it.name.equals(name, ignoreCase = true) }
        val updated = _allPresets.value.filter { !it.name.equals(name, ignoreCase = true) }
        _allPresets.value = updated
        _customPresets.value = updated.filter { it.isCustom }

        if (toDelete != null) {
            val updatedDeleted = _deletedPresets.value.filter { !it.name.equals(toDelete.name, ignoreCase = true) } + toDelete
            _deletedPresets.value = updatedDeleted
        }

        if (_activePresetName.value.equals(name, ignoreCase = true)) {
            val fallback = updated.firstOrNull()?.name ?: "Flat"
            applyPreset(fallback)
        }
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Deleted preset: $name")
    }

    fun restorePreset(presetName: String) {
        val toRestore = _deletedPresets.value.find { it.name.equals(presetName, ignoreCase = true) }
            ?: EqualizerDefaults.PRESETS.find { it.name.equals(presetName, ignoreCase = true) }
            ?: return

        _deletedPresets.value = _deletedPresets.value.filter { !it.name.equals(toRestore.name, ignoreCase = true) }
        val updated = _allPresets.value.filter { !it.name.equals(toRestore.name, ignoreCase = true) } + toRestore
        _allPresets.value = updated
        _customPresets.value = updated.filter { it.isCustom }
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Restored preset: ${toRestore.name}")
    }

    fun resetPresetsToDefault() {
        _allPresets.value = EqualizerDefaults.PRESETS
        _deletedPresets.value = emptyList()
        _customPresets.value = emptyList()
        saveSettings()
        savePresetLists()
        AppLogger.log(tag, "Reset presets to factory defaults")
    }

    fun deleteCustomPreset(name: String) {
        deletePreset(name)
    }

    fun setDynamicBassEnabled(enabled: Boolean) {
        _dynamicBassEnabled.value = enabled
        saveSettings()
        updateDynamicBass()
        AppLogger.log(tag, "Smart Dynamic Bass: ${if (enabled) "ON" else "OFF"}")
    }

    fun setDynamicBassStrength(strength: Float) {
        _dynamicBassStrength.value = strength.coerceIn(0f, 1f)
        saveSettings()
        updateDynamicBass()
    }

    fun updateDynamicBass() {
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val ratio = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
            _currentVolumeRatio.value = ratio

            val isSpeaker = _currentAudioDevice.value.let { it.contains("speaker", ignoreCase = true) || it.contains("динамик", ignoreCase = true) }

            if (!_isEnabled.value || _isTemporaryBypass.value) {
                _dynamicBassModeStatus.value = if (_isTemporaryBypass.value) getString(R.string.dynamic_bass_status_bypass) else getString(R.string.dynamic_bass_status_master_off)
                engines.values.forEach { it.updateDynamicBassOffsets(0f, 0f, 0f, 0f) }
                return
            }

            if (!_dynamicBassEnabled.value) {
                _dynamicBassModeStatus.value = getString(R.string.dynamic_bass_status_disabled)
                engines.values.forEach { it.updateDynamicBassOffsets(0f, 0f, 0f, 0f) }
                return
            }

            if (!isSpeaker) {
                _dynamicBassModeStatus.value = getString(R.string.dynamic_bass_status_headphones)
                engines.values.forEach { it.updateDynamicBassOffsets(0f, 0f, 0f, 0f) }
                return
            }

            val strength = _dynamicBassStrength.value.coerceIn(0f, 1f)

            // bass punch smoothstep between 45% and 80% volume
            val factorPunch = when {
                ratio <= 0.45f -> 1.0f
                ratio >= 0.80f -> 0.0f
                else -> {
                    val t = (ratio - 0.45f) / (0.80f - 0.45f)
                    1.0f - (t * t * (3.0f - 2.0f * t))
                }
            }

            val punch62 = 5.0f * strength * factorPunch
            val punch125 = 6.0f * strength * factorPunch
            val punchBassBoost = 25.0f * strength * factorPunch

            // excursion cut above 70% volume
            val cutFactor = when {
                ratio <= 0.70f -> 0.0f
                ratio >= 1.0f -> 1.0f
                else -> {
                    val t = (ratio - 0.70f) / (1.0f - 0.70f)
                    t * t * (3.0f - 2.0f * t)
                }
            }

            val cut31 = -6.0f * cutFactor
            val cut62 = -2.5f * cutFactor

            val offset31 = cut31
            val offset62 = punch62 + cut62
            val offset125 = punch125
            val offsetBassBoost = punchBassBoost

            val percentVol = (ratio * 100).toInt()
            _dynamicBassModeStatus.value = when {
                ratio <= 0.45f -> getString(R.string.dynamic_bass_status_punch, punch125, percentVol)
                ratio < 0.80f -> getString(R.string.dynamic_bass_status_adaptive, percentVol)
                else -> getString(R.string.dynamic_bass_status_protection, cut31, percentVol)
            }

            engines.values.forEach {
                it.updateDynamicBassOffsets(offset31, offset62, offset125, offsetBassBoost)
            }
            AppLogger.log(tag, "Smart Dynamic Bass: Vol=$percentVol% | 31Hz=${offset31.toInt()}dB 62Hz=${offset62.toInt()}dB 125Hz=${offset125.toInt()}dB BB=+${offsetBassBoost.toInt()}%")
        } catch (e: Exception) {
            AppLogger.log(tag, "Error in updateDynamicBass: ${e.message}")
        }
    }

    fun getDiagnosticsReport(): String {
        val s0Engine = engines[0]
        val hwStatus = s0Engine?.getEngineStatus() ?: "Session 0: Engine Offline"
        val logs = AppLogger.getLogs().takeLast(40).joinToString("\n")

        return buildString {
            appendLine("=== allEQ SYSTEM DIAGNOSTIC REPORT ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("[DEVICE SPECIFICATION]")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Build Display: ${Build.DISPLAY}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("[AUDIO ROUTING & HARDWARE]")
            appendLine("Active Output: ${_currentAudioDevice.value}")
            appendLine("Active Session Count: ${_activeSessionsCount.value}")
            appendLine("Registered Session IDs: ${engines.keys.toList()}")
            appendLine(hwStatus)
            appendLine()
            appendLine("[DSP CONFIGURATION]")
            appendLine("Master Switch: ${if (_isEnabled.value) "ENABLED" else "DISABLED"}")
            appendLine("Active Preset: ${_activePresetName.value}")
            appendLine("Pre-Amp Gain: ${_preAmpDb.value} dB")
            appendLine("Bass Boost: ${_bassBoostPercent.value}%")
            appendLine("Limiter Status: ${if (_limiterEnabled.value) "ENABLED (0 dBFS)" else "BYPASSED"}")
            appendLine("Band Gains (31Hz..16kHz): ${_bandGains.value.map { it.toInt() }}")
            appendLine("Smart Dynamic Bass: ${if (_dynamicBassEnabled.value) "ENABLED (Strength: ${(_dynamicBassStrength.value * 100).toInt()}%)" else "DISABLED"}")
            appendLine("Dynamic Bass Status: ${_dynamicBassModeStatus.value} (Vol: ${(_currentVolumeRatio.value * 100).toInt()}%)")
            appendLine()
            appendLine("[RECENT EVENT JOURNAL (LAST 40)]")
            if (logs.isBlank()) {
                appendLine("No events recorded.")
            } else {
                appendLine(logs)
            }
            appendLine("=== END OF REPORT ===")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // ensure foreground service and notification remain active when task is swiped
        if (_isEnabled.value) {
            updateNotification()
        }
    }

    override fun onDestroy() {
        unregisterAudioSessionReceiver()
        unregisterVolumeReceiver()
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
        engines.values.forEach { it.release() }
        engines.clear()
        super.onDestroy()
    }
}
