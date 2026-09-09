package com.omix.alleq.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.EqualizerDefaults
import kotlin.math.abs

class DynamicsEngine(val audioSessionId: Int) {

    private val tag = "DynamicsEngine"
    private var dpe: DynamicsProcessing? = null
    private var hwEq: Equalizer? = null
    private var hwBass: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var onControlStatusChanged: ((Boolean) -> Unit)? = null

    fun isHealthy(): Boolean {
        return try {
            if (!isInitialized || hwEq == null) return false
            // checking enabled verifies native binder without failing on non-exclusive control
            val isEnabled = hwEq?.enabled ?: false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getEngineStatus(): String {
        val eqStatus = hwEq?.let {
            val bands = try { it.numberOfBands } catch (_: Exception) { 0.toShort() }
            val range = try { it.bandLevelRange.map { r -> r / 100 } } catch (_: Exception) { listOf(0, 0) }
            val control = try { if (it.hasControl()) "HasControl" else "NoControl" } catch (_: Exception) { "Dead" }
            "Active ($bands bands, range: ${range.getOrElse(0) { 0 }}..${range.getOrElse(1) { 0 }} dB, $control)"
        } ?: "Offline"

        val leStatus = loudnessEnhancer?.let {
            val gain = try { it.targetGain / 100 } catch (_: Exception) { 0 }
            "Active (targetGain: $gain dB)"
        } ?: "Offline"

        val bbStatus = hwBass?.let {
            val str = try { it.roundedStrength / 10 } catch (_: Exception) { 0 }
            "Active (strength: $str%)"
        } ?: "Offline"

        val dpeStatus = dpe?.let { "Active (Limiter 0 dBFS)" } ?: "Offline"

        return "HW Equalizer: $eqStatus\nLoudnessEnhancer: $leStatus\nBassBoost: $bbStatus\nDynamicsProcessing: $dpeStatus"
    }

    private var cachedBandGains: List<Float> = EqualizerDefaults.BANDS.map { 0f }
    private var cachedPreAmpDb: Float = 0f

    @Volatile
    var isInitialized = false
        private set

    var hwEqBandsCount = 0
        private set

    fun initialize(
        bandGains: List<Float>,
        preAmpDb: Float,
        bassBoostPercent: Float,
        limiterEnabled: Boolean,
        isEnabled: Boolean
    ): Boolean {
        release()
        cachedBandGains = bandGains
        cachedPreAmpDb = preAmpDb
        var anySuccess = false

        try {
            hwEq = Equalizer(1000, audioSessionId).apply {
                enabled = isEnabled
                try {
                    setControlStatusListener { _, controlGranted ->
                        AppLogger.log(tag, "Session $audioSessionId control status: $controlGranted")
                        onControlStatusChanged?.invoke(controlGranted)
                    }
                } catch (_: Exception) {}
            }
            hwEqBandsCount = hwEq?.numberOfBands?.toInt() ?: 0
            AppLogger.log(tag, "Session $audioSessionId: Hardware Equalizer connected ($hwEqBandsCount bands)")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Error creating HW Equalizer: ${e.message}")
        }

        try {
            hwBass = BassBoost(1000, audioSessionId).apply {
                enabled = isEnabled && bassBoostPercent > 0f
                if (strengthSupported) {
                    setStrength((bassBoostPercent * 10f).toInt().toShort().coerceIn(0, 1000))
                }
            }
            AppLogger.log(tag, "Session $audioSessionId: BassBoost initialized")
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Error initializing BassBoost: ${e.message}")
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = isEnabled
            }
            AppLogger.log(tag, "Session $audioSessionId: LoudnessEnhancer connected")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Error creating LoudnessEnhancer: ${e.message}")
        }

        // brickwall limiter to protect against hardware clipping
        try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, // avoid double filtering with hweq
                0,
                false,
                0,
                false,
                0,
                true
            )

            for (channel in 0..1) {
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1.5f,
                    35.0f,
                    15.0f,
                    -1.5f,
                    0.0f
                )
                builder.setLimiterByChannelIndex(channel, limiter)
            }

            val config = builder.build()
            dpe = DynamicsProcessing(1000, audioSessionId, config).apply {
                enabled = isEnabled
            }
            AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing Limiter connected")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing unavailable: ${e.message}")
        }

        isInitialized = anySuccess
        if (isInitialized) {
            updateBands(bandGains, bassBoostPercent)
            updateGainCompensation()
        }
        return isInitialized
    }

    private var isEnabled = true

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        try {
            dpe?.enabled = enabled
            hwEq?.enabled = enabled
            hwBass?.enabled = enabled && (cachedBassBoostPercent > 0f)
            loudnessEnhancer?.enabled = enabled

            if (!enabled) {
                resetToFlat()
            } else {
                updateBands(cachedBandGains, cachedBassBoostPercent)
                updateGainCompensation()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error toggling enabled: ${e.message}")
        }
    }

    private var dynamicOffset31: Float = 0f
    private var dynamicOffset62: Float = 0f
    private var dynamicOffset125: Float = 0f
    private var dynamicOffsetBassBoost: Float = 0f

    fun updateDynamicBassOffsets(offset31: Float, offset62: Float, offset125: Float, offsetBassBoost: Float) {
        dynamicOffset31 = offset31
        dynamicOffset62 = offset62
        dynamicOffset125 = offset125
        dynamicOffsetBassBoost = offsetBassBoost
        if (!isEnabled || !isInitialized) return

        updateBands(cachedBandGains, cachedBassBoostPercent)
    }

    private var cachedBassBoostPercent: Float = 0f

    fun updateBands(bandGains: List<Float>, bassBoostPercent: Float) {
        cachedBandGains = bandGains
        cachedBassBoostPercent = bassBoostPercent
        if (!isInitialized || !isEnabled) return

        hwEq?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]

                // vocal presence boost if low end is pushed
                val totalBassActive = maxOf(
                    cachedBandGains.take(3).maxOrNull() ?: 0f,
                    (bassBoostPercent / 100f) * 6f
                )
                val vocalPreservation = if (totalBassActive > 1.5f) {
                    (totalBassActive * 0.25f).coerceAtMost(2.0f)
                } else {
                    0f
                }

                for (b in 0 until numBands) {
                    val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000
                    val nearestBand = EqualizerDefaults.BANDS.minByOrNull {
                        abs(it.frequencyHz - centerFreqHz)
                    }
                    val gainDb = nearestBand?.let { bandGains.getOrElse(it.index) { 0f } } ?: 0f
                    val dynGain = when (nearestBand?.index) {
                        0 -> dynamicOffset31
                        1 -> dynamicOffset62
                        2 -> dynamicOffset125
                        else -> 0f
                    }
                    val extraBass = if (hwBass == null && centerFreqHz in 100..250) (bassBoostPercent / 100f) * 4f else 0f
                    val speechBoost = if (centerFreqHz in 800..3000) vocalPreservation else 0f
                    val targetMilliBels = ((gainDb + dynGain + extraBass + speechBoost) * 100).toInt().toShort().coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(b.toShort(), targetMilliBels)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating hardware EQ: ${e.message}")
            }
        }

        hwBass?.let { bb ->
            try {
                if (bb.strengthSupported) {
                    val totalPercent = (bassBoostPercent + dynamicOffsetBassBoost).coerceIn(0f, 100f)
                    bb.enabled = isEnabled && (totalPercent > 0f)
                    bb.setStrength((totalPercent * 10f).toInt().toShort().coerceIn(0, 1000))
                }
            } catch (_: Exception) {
            }
        }

        updateGainCompensation()
    }

    fun updateGainCompensation() {
        if (!isInitialized || !isEnabled) {
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (_: Exception) {}
            return
        }

        val speechLevellerFloor = 3.5f

        val maxBandBoost = (cachedBandGains.mapIndexed { idx, g ->
            val dyn = when (idx) {
                0 -> dynamicOffset31
                1 -> dynamicOffset62
                2 -> dynamicOffset125
                else -> 0f
            }
            g + dyn
        }.maxOrNull() ?: 0f).coerceAtLeast(0f)

        val totalBassBoost = (cachedBassBoostPercent + dynamicOffsetBassBoost).coerceIn(0f, 100f)
        val bassBoostCompensation = (totalBassBoost / 100f) * 3.5f

        val maxBoostDb = maxOf(maxBandBoost, bassBoostCompensation)

        val totalGainDb = (speechLevellerFloor + maxBoostDb + cachedPreAmpDb).coerceIn(0f, 12f)
        val targetMilliBels = (totalGainDb * 100).toInt().coerceIn(0, 1200)

        loudnessEnhancer?.let { le ->
            try {
                le.enabled = (targetMilliBels > 0)
                le.setTargetGain(targetMilliBels)
                AppLogger.log(tag, "LoudnessEnhancer: targetGain set to ${targetMilliBels} mB (+${totalGainDb} dB)")
            } catch (e: Exception) {
                AppLogger.log(tag, "Error setting LoudnessEnhancer: ${e.message}")
            }
        }
    }

    fun updatePreAmpAndLimiter(preAmpDb: Float, limiterEnabled: Boolean) {
        cachedPreAmpDb = preAmpDb
        if (!isInitialized) return

        updateGainCompensation()

        if (!isEnabled) return

        dpe?.let { effect ->
            try {
                for (channel in 0..1) {
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1.5f,
                        35.0f,
                        15.0f,
                        -1.5f,
                        0.0f
                    )
                    effect.setLimiterByChannelIndex(channel, limiter)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating limiter: ${e.message}")
            }
        }
    }

    private fun resetToFlat() {
        hwEq?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                for (b in 0 until numBands) {
                    eq.setBandLevel(b.toShort(), 0)
                }
            } catch (_: Exception) {}
        }
        loudnessEnhancer?.let { le ->
            try {
                le.setTargetGain(0)
                le.enabled = false
            } catch (_: Exception) {}
        }
        hwBass?.let { bb ->
            try {
                bb.setStrength(0)
                bb.enabled = false
            } catch (_: Exception) {}
        }
        dpe?.let { dp ->
            try {
                dp.enabled = false
            } catch (_: Exception) {}
        }
    }

    fun release() {
        onControlStatusChanged = null
        try {
            resetToFlat()
        } catch (_: Exception) {}

        try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null

        try { dpe?.enabled = false } catch (_: Exception) {}
        try { dpe?.release() } catch (_: Exception) {}
        dpe = null

        try { hwEq?.setControlStatusListener(null) } catch (_: Exception) {}
        try { hwEq?.enabled = false } catch (_: Exception) {}
        try { hwEq?.release() } catch (_: Exception) {}
        hwEq = null

        try { hwBass?.enabled = false } catch (_: Exception) {}
        try { hwBass?.release() } catch (_: Exception) {}
        hwBass = null

        isInitialized = false
    }
}
