package com.omix.vivoeq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omix.vivoeq.MainActivity
import com.omix.vivoeq.audio.DynamicsEngine
import com.omix.vivoeq.model.EqualizerDefaults
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
        const val CHANNEL_ID = "vivoeq_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.omix.vivoeq.ACTION_START"
        const val ACTION_STOP = "com.omix.vivoeq.ACTION_STOP"
        const val ACTION_TOGGLE = "com.omix.vivoeq.ACTION_TOGGLE"

        // Реактивное состояние для UI
        private val _isEnabled = MutableStateFlow(true)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _bandGains = MutableStateFlow(EqualizerDefaults.PRESETS[0].bandGains)
        val bandGains: StateFlow<List<Float>> = _bandGains.asStateFlow()

        private val _preAmpDb = MutableStateFlow(0f)
        val preAmpDb: StateFlow<Float> = _preAmpDb.asStateFlow()

        private val _bassBoostPercent = MutableStateFlow(0f)
        val bassBoostPercent: StateFlow<Float> = _bassBoostPercent.asStateFlow()

        private val _limiterEnabled = MutableStateFlow(true)
        val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

        private val _activePresetName = MutableStateFlow("Flat")
        val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

        private val _activeSessionsCount = MutableStateFlow(0)
        val activeSessionsCount: StateFlow<Int> = _activeSessionsCount.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initAudioEngines()
        registerPlaybackCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                toggleEnabled()
            }
            ACTION_STOP -> {
                stopSelf()
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
                "VivoEq Звуковой Процессор",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая работа эквалайзера"
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

        val stateText = if (_isEnabled.value) "Включен (${_activePresetName.value})" else "Выключен"
        val toggleActionText = if (_isEnabled.value) "Отключить" else "Включить"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VivoEq • $stateText")
            .setContentText("Активных сессий: ${_activeSessionsCount.value} | Динамики")
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

    private fun initAudioEngines() {
        // Подключаемся к глобальной сессии 0 (Global mix)
        attachSession(0)
    }

    private fun registerPlaybackCallback() {
        try {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    configs ?: return

                    val currentSessions = configs.mapNotNull {
                        try {
                            // getAudioAttributes or sessionId
                            val sessionId = it::class.java.getMethod("getAudioSessionId").invoke(it) as? Int
                            sessionId
                        } catch (_: Exception) {
                            null
                        }
                    }.filter { it > 0 }.toSet()

                    // Подключаем к новым сессиям
                    currentSessions.forEach { sessionId ->
                        if (!engines.containsKey(sessionId)) {
                            attachSession(sessionId)
                        }
                    }

                    // Чистим завершенные
                    engines.keys.toList().forEach { sessionId ->
                        if (sessionId != 0 && !currentSessions.contains(sessionId)) {
                            detachSession(sessionId)
                        }
                    }

                    _activeSessionsCount.value = engines.size
                    updateNotification()
                }
            }
            audioManager.registerAudioPlaybackCallback(playbackCallback!!, null)
        } catch (e: Exception) {
            Log.w(tag, "Не удалось зарегистрировать AudioPlaybackCallback: ${e.message}")
        }
    }

    private fun attachSession(sessionId: Int) {
        val engine = DynamicsEngine(sessionId)
        val success = engine.initialize(
            bandGains = _bandGains.value,
            preAmpDb = _preAmpDb.value,
            bassBoostPercent = _bassBoostPercent.value,
            limiterEnabled = _limiterEnabled.value,
            isEnabled = _isEnabled.value
        )
        if (success) {
            engines[sessionId] = engine
            _activeSessionsCount.value = engines.size
        }
    }

    private fun detachSession(sessionId: Int) {
        engines.remove(sessionId)?.release()
        _activeSessionsCount.value = engines.size
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        engines.values.forEach { it.setEnabled(enabled) }
        updateNotification()
    }

    fun updateBands(gains: List<Float>) {
        _bandGains.value = gains
        engines.values.forEach { it.updateBands(gains, _bassBoostPercent.value) }
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
    }

    fun setBassBoost(percent: Float) {
        _bassBoostPercent.value = percent
        engines.values.forEach { it.updateBands(_bandGains.value, percent) }
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        engines.values.forEach { it.updatePreAmpAndLimiter(_preAmpDb.value, enabled) }
    }

    fun applyPreset(presetName: String) {
        val preset = EqualizerDefaults.PRESETS.find { it.name == presetName } ?: return
        _activePresetName.value = preset.name
        _preAmpDb.value = preset.preAmpDb
        _bassBoostPercent.value = preset.bassBoostPercent
        _limiterEnabled.value = preset.limiterEnabled
        updateBands(preset.bandGains)
        engines.values.forEach { it.updatePreAmpAndLimiter(preset.preAmpDb, preset.limiterEnabled) }
        updateNotification()
    }

    override fun onDestroy() {
        playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
        engines.values.forEach { it.release() }
        engines.clear()
        super.onDestroy()
    }
}
