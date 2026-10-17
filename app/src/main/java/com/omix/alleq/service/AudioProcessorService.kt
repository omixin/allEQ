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
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.media.audiofx.AudioEffect
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.omix.alleq.MainActivity
import com.omix.alleq.R
import com.omix.alleq.audio.DynamicsEngine
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.AudioDeviceType
import com.omix.alleq.model.DevicePresetBinding
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.telemetry.TelemetryManager
import com.omix.alleq.widget.AllEqWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AudioProcessorService : Service() {

    private val tag = "AudioProcessorService"
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessorService = this@AudioProcessorService
    }

    private lateinit var audioManager: AudioManager
    private val engines = ConcurrentHashMap<Int, DynamicsEngine>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var modeChangedListener: AudioManager.OnModeChangedListener? = null
    private var isSpeakerOutput: Boolean = true
    private var resurrectJob: Job? = null
    private var lastResurrectTime: Long = 0L
    private var saveSettingsJob: Job? = null
    private var foregroundFailed = false

    companion object {
        const val CHANNEL_ID = "alleq_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.omix.alleq.ACTION_START"
        const val ACTION_STOP = "com.omix.alleq.ACTION_STOP"
        const val ACTION_TOGGLE = "com.omix.alleq.ACTION_TOGGLE"
        const val ACTION_PREV_PRESET = "com.omix.alleq.ACTION_PREV_PRESET"
        const val ACTION_NEXT_PRESET = "com.omix.alleq.ACTION_NEXT_PRESET"
        const val ACTION_APPLY_PRESET = "com.omix.alleq.ACTION_APPLY_PRESET"
        const val EXTRA_FORCE_ENABLE = "com.omix.alleq.EXTRA_FORCE_ENABLE"
        const val EXTRA_PRESET_NAME = "com.omix.alleq.EXTRA_PRESET_NAME"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var instance: AudioProcessorService? = null
            private set

        // set by BootReceiver right before starting the service
        @Volatile
        var launchedFromBoot: Boolean = false

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

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

        private val _autoDevicePresetEnabled = MutableStateFlow(true)
        val autoDevicePresetEnabled: StateFlow<Boolean> = _autoDevicePresetEnabled.asStateFlow()

        private val _knownDeviceBindings = MutableStateFlow<List<DevicePresetBinding>>(EqualizerDefaults.DEFAULT_DEVICE_BINDINGS)
        val knownDeviceBindings: StateFlow<List<DevicePresetBinding>> = _knownDeviceBindings.asStateFlow()

        private val _activeDeviceKey = MutableStateFlow("speaker")
        val activeDeviceKey: StateFlow<String> = _activeDeviceKey.asStateFlow()
    }

    private var deviceCallback: AudioDeviceCallback? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var audioSessionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadSavedSettings()
        createNotificationChannel(this)
        val fromBoot = launchedFromBoot
        launchedFromBoot = false
        if (!promoteToForeground(fromBoot)) {
            foregroundFailed = true
            BootReceiver.postResumeNotification(this)
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java).cancel(BootReceiver.RESUME_NOTIFICATION_ID)
        initAudioEngines()
        registerPlaybackCallback()
        registerAudioSessionReceiver()
        registerDeviceCallback()
        registerVolumeReceiver()
        registerModeListener()
        updateAudioDeviceDescription()
        updateDynamicBass()
        AllEqTileService.requestUpdate(this)
        AppLogger.log(tag, "AudioProcessorService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (foregroundFailed) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_TOGGLE -> {
                toggleEnabled()
            }
            ACTION_PREV_PRESET -> {
                cyclePreset(-1)
            }
            ACTION_NEXT_PRESET -> {
                cyclePreset(1)
            }
            ACTION_APPLY_PRESET -> {
                val target = intent.getStringExtra(EXTRA_PRESET_NAME)
                if (!target.isNullOrBlank()) {
                    applyPreset(target, isAutoSwitch = false)
                }
            }
            ACTION_START -> {
                if (intent.getBooleanExtra(EXTRA_FORCE_ENABLE, false) && !_isEnabled.value) {
                    setEnabled(true)
                }
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

    fun cyclePreset(direction: Int) {
        val list = _allPresets.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.name.equals(_activePresetName.value, ignoreCase = true) }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + direction + list.size) % list.size
        applyPreset(list[nextIndex].name, isAutoSwitch = false)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun promoteToForeground(fromBoot: Boolean): Boolean {
        // android 15 blocks mediaPlayback fgs from BOOT_COMPLETED, specialUse is still allowed.
        // the os keeps the boot origin for this start, so the type stays specialUse until restart
        val type = if (fromBoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            AppLogger.log(tag, "Foreground started (boot=$fromBoot, type=$type)")
            true
        } catch (e: Exception) {
            AppLogger.log(tag, "Foreground start refused (boot=$fromBoot): ${e.message}")
            false
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
            .setSmallIcon(R.drawable.ic_qs_alleq)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_qs_alleq, toggleActionText, togglePendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
        AllEqTileService.requestUpdate(this)
        AllEqWidgetManager.updateWidgets(this)
    }

    private fun detectActiveOutputDevice(dev: AudioDeviceInfo?): Triple<String, String, AudioDeviceType> {
        if (dev == null || dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return Triple("speaker", getString(R.string.device_speaker), AudioDeviceType.SPEAKER)
        }
        val rawName = dev.productName?.toString()?.trim().orEmpty()
        return when (dev.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                val name = if (rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true)) {
                    rawName
                } else {
                    getString(R.string.device_bluetooth)
                }
                val key = "bluetooth_" + name.lowercase().replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                Triple(key, name, AudioDeviceType.BLUETOOTH)
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                val name = if (rawName.isNotBlank()) rawName else getString(R.string.device_wired)
                Triple("wired", name, AudioDeviceType.WIRED)
            }
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                val name = if (rawName.isNotBlank()) rawName else getString(R.string.device_usb)
                val key = "usb_" + name.lowercase().replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                Triple(key, name, AudioDeviceType.USB)
            }
            else -> Triple("speaker", getString(R.string.device_speaker), AudioDeviceType.SPEAKER)
        }
    }

    private fun updateAudioDeviceDescription() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDevice = devices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        isSpeakerOutput = activeDevice == null || activeDevice.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val (deviceKey, deviceName, deviceType) = detectActiveOutputDevice(activeDevice)
        _activeDeviceKey.value = deviceKey
        _currentAudioDevice.value = deviceName

        // register or update known device profile dynamically
        val currentBindings = _knownDeviceBindings.value.toMutableList()
        val existingIndex = currentBindings.indexOfFirst { it.deviceKey == deviceKey }
        val assignedPreset: String

        if (existingIndex >= 0) {
            val existing = currentBindings[existingIndex]
            assignedPreset = existing.assignedPresetName
            currentBindings[existingIndex] = existing.copy(
                deviceName = deviceName,
                deviceType = deviceType,
                isCurrentlyActive = true,
            )
        } else {
            // newly discovered audio output: default preset is Bass Punch for Bluetooth, Flat for others
            assignedPreset = if (deviceType == AudioDeviceType.BLUETOOTH) "Bass Punch" else "Flat"
            currentBindings.add(
                DevicePresetBinding(
                    deviceKey = deviceKey,
                    deviceName = deviceName,
                    deviceType = deviceType,
                    assignedPresetName = assignedPreset,
                    isCurrentlyActive = true,
                ),
            )
        }

        // update active status on all known bindings
        _knownDeviceBindings.value = currentBindings.map {
            it.copy(isCurrentlyActive = it.deviceKey == deviceKey)
        }
        saveSettings(debounce = true)
        AppLogger.log(tag, "Audio output: $deviceName (key=$deviceKey, targetPreset=$assignedPreset)")
        updateDynamicBass()

        // auto-apply assigned preset when routing changes
        if (_autoDevicePresetEnabled.value && _isEnabled.value && !_isTemporaryBypass.value) {
            if (!_activePresetName.value.equals(assignedPreset, ignoreCase = true)) {
                AppLogger.log(tag, "Auto-switched preset to '$assignedPreset' for $deviceName")
                applyPreset(assignedPreset, isAutoSwitch = true)
            }
        }
    }

    private fun registerVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    updateDynamicBass()
                    if (audioManager.mode == AudioManager.MODE_NORMAL && engines[0]?.isHealthy() == false) {
                        resurrectSession0IfNeeded()
                    }
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
                resurrectSession0IfNeeded()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioDeviceDescription()
                updateNotification()
                resurrectSession0IfNeeded()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun registerModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeChangedListener = AudioManager.OnModeChangedListener { mode ->
                handleAudioModeChanged(mode)
            }
            try {
                audioManager.addOnModeChangedListener(mainExecutor, modeChangedListener!!)
                AppLogger.log(tag, "Registered AudioManager.OnModeChangedListener")
            } catch (e: Exception) {
                AppLogger.log(tag, "Failed to register OnModeChangedListener: ${e.message}")
            }
        }
    }

    private fun unregisterModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeChangedListener?.let {
                try {
                    audioManager.removeOnModeChangedListener(it)
                } catch (_: Exception) {}
            }
            modeChangedListener = null
        }
    }

    private fun handleAudioModeChanged(mode: Int) {
        val modeDesc = when (mode) {
            AudioManager.MODE_NORMAL -> "NORMAL"
            AudioManager.MODE_RINGTONE -> "RINGTONE"
            AudioManager.MODE_IN_CALL -> "IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "CALL_SCREENING"
            else -> "UNKNOWN ($mode)"
        }
        AppLogger.log(tag, "Audio mode shifted to $modeDesc")

        if (mode == AudioManager.MODE_NORMAL) {
            // delay 300ms to allow audioflinger to restore multimedia mixer thread after call
            resurrectJob?.cancel()
            resurrectJob = serviceScope.launch {
                delay(300)
                resurrectSession0IfNeeded(force = true)
            }
        } else {
            resurrectJob?.cancel()
            updateSessionArbitration()
        }
    }

    private fun resurrectSession0IfNeeded(force: Boolean = false) {
        val shouldBeActive = _isEnabled.value && !_isTemporaryBypass.value
        if (!shouldBeActive) return

        // do not touch session 0 during active phone or voip calls
        val inCall = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_RINGTONE
        if (inCall) return

        val now = System.currentTimeMillis()
        if (!force && now - lastResurrectTime < 2000L) {
            // cooldown to prevent rapid resurrection thrashing
            return
        }

        val engine0 = engines[0]
        val isUnhealthy = (engine0 == null) || (!engine0.isHealthy())

        if (force || isUnhealthy) {
            lastResurrectTime = now
            AppLogger.log(tag, "Resurrecting session 0 engine (force=$force, unhealthy=$isUnhealthy)")
            detachSession(0)
            attachSession(0)
            updateSessionArbitration()

            // schedule retry if audioflinger needed more time to settle
            if (engines[0]?.isHealthy() == false) {
                resurrectJob?.cancel()
                resurrectJob = serviceScope.launch {
                    delay(500)
                    if (engines[0]?.isHealthy() == false && _isEnabled.value && audioManager.mode == AudioManager.MODE_NORMAL) {
                        lastResurrectTime = System.currentTimeMillis()
                        AppLogger.log(tag, "Retry resurrecting session 0 engine")
                        detachSession(0)
                        attachSession(0)
                        updateSessionArbitration()
                    }
                }
            }
        }
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
        val inCall = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_RINGTONE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && audioManager.mode == AudioManager.MODE_CALL_SCREENING)
        val shouldBeActive = _isEnabled.value && !_isTemporaryBypass.value && !inCall

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

                    // filter out voice calls (discord, phone, voip) and inactive tracks
                    val mediaConfigs = configs.filter { config ->
                        val usage = config.audioAttributes?.usage ?: AudioAttributes.USAGE_UNKNOWN
                        // usage 17 corresponds to USAGE_CALL_ASSISTANCE on API 30+
                        val isVoice = usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ||
                                usage == 17
                        val isActive = try {
                            val method = config.javaClass.getMethod("isActive")
                            (method.invoke(config) as? Boolean) ?: true
                        } catch (_: Exception) {
                            true
                        }
                        !isVoice && isActive
                    }

                    // collect currently playing audio sessions from configs
                    val activeSessionIds = mediaConfigs
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

                    val isAudioPlaying = audioManager.isMusicActive || mediaConfigs.isNotEmpty()
                    updateSessionArbitration(isAudioPlaying)

                    if (isAudioPlaying && audioManager.mode == AudioManager.MODE_NORMAL) {
                        if (engines[0]?.isHealthy() == false) {
                            resurrectSession0IfNeeded()
                        }
                    }
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
            if (sessionId == 0) {
                engine.onControlStatusChanged = { granted ->
                    AppLogger.log(tag, "Session 0 control status: $granted")
                    if (granted && _isEnabled.value && audioManager.mode == AudioManager.MODE_NORMAL) {
                        resurrectJob?.cancel()
                        resurrectJob = serviceScope.launch {
                            delay(200)
                            if (engines[0]?.isHealthy() == false) {
                                resurrectSession0IfNeeded(force = true)
                            }
                        }
                    }
                }
            }

            val hasSpecificSessions = engines.keys.any { it != 0 } || (sessionId != 0)
            val isSessionActive = _isEnabled.value && !_isTemporaryBypass.value && (if (sessionId == 0) !hasSpecificSessions else true)

            val success = engine.initialize(
                bandGains = _bandGains.value,
                preAmpDb = _preAmpDb.value,
                bassBoostPercent = _bassBoostPercent.value,
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

        _autoDevicePresetEnabled.value = prefs.getBoolean("auto_device_preset_enabled", true)
        val savedBindingsJson = prefs.getString("device_bindings_json", null)
        val loadedBindings = EqualizerDefaults.deviceBindingsFromJson(savedBindingsJson)
        if (loadedBindings.isNotEmpty()) {
            _knownDeviceBindings.value = loadedBindings
        } else {
            _knownDeviceBindings.value = EqualizerDefaults.DEFAULT_DEVICE_BINDINGS
        }
    }

    private fun saveSettings(debounce: Boolean = false) {
        if (debounce) {
            saveSettingsJob?.cancel()
            saveSettingsJob = serviceScope.launch {
                delay(300)
                writeSettingsToDisk()
            }
        } else {
            saveSettingsJob?.cancel()
            writeSettingsToDisk()
        }
    }

    private fun writeSettingsToDisk() {
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
            .putBoolean("auto_device_preset_enabled", _autoDevicePresetEnabled.value)
            .putString("device_name", _currentAudioDevice.value)
            .putString("device_bindings_json", EqualizerDefaults.deviceBindingsToJson(_knownDeviceBindings.value))
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
        updateNotification()
        saveSettings()
        AllEqTileService.requestUpdate(this)
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
        saveSettings(debounce = true)
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
        engines.values.forEach { it.updatePreAmp(db) }
        saveSettings(debounce = true)
    }

    fun setBassBoost(percent: Float) {
        _bassBoostPercent.value = percent
        engines.values.forEach { it.updateBands(_bandGains.value, percent) }
        saveSettings(debounce = true)
    }

    fun applyPreset(presetName: String, isAutoSwitch: Boolean = false) {
        val preset = _allPresets.value.find { it.name.equals(presetName, ignoreCase = true) }
            ?: _deletedPresets.value.find { it.name.equals(presetName, ignoreCase = true) }
            ?: EqualizerDefaults.PRESETS.find { it.name.equals(presetName, ignoreCase = true) }
            ?: return
        _activePresetName.value = preset.name
        _preAmpDb.value = preset.preAmpDb
        _bassBoostPercent.value = preset.bassBoostPercent
        _limiterEnabled.value = preset.limiterEnabled
        updateBands(preset.bandGains)
        engines.values.forEach { it.updatePreAmp(preset.preAmpDb) }

        if (!isAutoSwitch) {
            // remember preset for current active output device
            val currentKey = _activeDeviceKey.value
            _knownDeviceBindings.value = _knownDeviceBindings.value.map {
                if (it.deviceKey == currentKey) it.copy(assignedPresetName = preset.name) else it
            }
        }

        saveSettings()
        updateNotification()
        AppLogger.log(tag, "Applied preset: ${preset.name} (autoSwitch=$isAutoSwitch)")
        TelemetryManager.currentPreset = preset.name
    }

    fun setDevicePreset(deviceKey: String, presetName: String) {
        val updated = _knownDeviceBindings.value.map {
            if (it.deviceKey == deviceKey) it.copy(assignedPresetName = presetName) else it
        }
        _knownDeviceBindings.value = updated
        saveSettings()
        if (_activeDeviceKey.value == deviceKey) {
            applyPreset(presetName, isAutoSwitch = false)
        }
        AppLogger.log(tag, "Assigned preset '$presetName' to device '$deviceKey'")
    }

    fun setAutoDevicePresetEnabled(enabled: Boolean) {
        _autoDevicePresetEnabled.value = enabled
        saveSettings()
        AppLogger.log(tag, "Auto device preset routing: ${if (enabled) "ON" else "OFF"}")
        if (enabled && _isEnabled.value && !_isTemporaryBypass.value) {
            val currentKey = _activeDeviceKey.value
            val binding = _knownDeviceBindings.value.find { it.deviceKey == currentKey }
            if (binding != null && !_activePresetName.value.equals(binding.assignedPresetName, ignoreCase = true)) {
                applyPreset(binding.assignedPresetName, isAutoSwitch = true)
            }
        }
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

            val isSpeaker = isSpeakerOutput

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
            appendLine("Limiter Status: ${if (_isEnabled.value) "ALWAYS ON (${DynamicsEngine.LIMITER_THRESHOLD_DB} dBFS)" else "BYPASSED"}")
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
        isServiceRunning = false
        if (instance == this) instance = null
        AllEqTileService.requestUpdate(this)
        unregisterModeListener()
        unregisterAudioSessionReceiver()
        unregisterVolumeReceiver()
        try { deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) } } catch (_: Exception) {}
        try { playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) } } catch (_: Exception) {}
        engines.values.forEach {
            try { it.release() } catch (_: Exception) {}
        }
        engines.clear()
        try { serviceScope.cancel() } catch (_: Exception) {}
        super.onDestroy()
    }
}
