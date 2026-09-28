package com.omix.alleq.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.omix.alleq.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AllEqTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, AllEqTileService::class.java),
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        stateJob?.cancel()
        stateJob = tileScope.launch {
            AudioProcessorService.isEnabled.collect {
                updateTileState()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
        stateJob = null
    }

    override fun onClick() {
        super.onClick()
        if (AudioProcessorService.isServiceRunning) {
            val service = AudioProcessorService.instance
            if (service != null) {
                service.toggleEnabled()
            } else {
                val intent = Intent(this, AudioProcessorService::class.java).apply {
                    action = AudioProcessorService.ACTION_TOGGLE
                }
                ContextCompat.startForegroundService(this, intent)
            }
        } else {
            // start service and force enable if killed by system
            val intent = Intent(this, AudioProcessorService::class.java).apply {
                action = AudioProcessorService.ACTION_START
                putExtra(AudioProcessorService.EXTRA_FORCE_ENABLE, true)
            }
            ContextCompat.startForegroundService(this, intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = AudioProcessorService.isServiceRunning
        val isEnabled = AudioProcessorService.isEnabled.value

        val isActive = isRunning && isEnabled
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = null
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_alleq)
        tile.updateTile()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        tileScope.cancel()
        super.onDestroy()
    }
}
