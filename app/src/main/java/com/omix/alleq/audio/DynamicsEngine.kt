package com.omix.alleq.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import android.util.Log
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.EqualizerDefaults
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

class DynamicsEngine(val audioSessionId: Int) {

    private val tag = "DynamicsEngine"
    private var dpe: DynamicsProcessing? = null
    private var hwEq: Equalizer? = null
    private var hwBass: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var onControlStatusChanged: ((Boolean) -> Unit)? = null

    // true: curve runs on dpe pre-eq, hw eq is kept disabled as fallback
    private var curveOnDpe = false

    fun isHealthy(): Boolean {
        return try {
            val curveEffect = if (curveOnDpe) dpe else hwEq
            if (!isInitialized || curveEffect == null) return false
            // checking enabled verifies native binder without failing on non-exclusive control
            curveEffect.enabled
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
            val on = try { if (it.enabled) "On" else "Off" } catch (_: Exception) { "Dead" }
            "$on (targetGain: $gain dB)"
        } ?: "Offline"

        val bbStatus = hwBass?.let {
            val str = try { it.roundedStrength / 10 } catch (_: Exception) { 0 }
            "Active (strength: $str%)"
        } ?: "Offline"

        val dpeStatus = dpe?.let {
            val preEq = if (curveOnDpe) "Pre-EQ ${EqualizerDefaults.BANDS.size} bands, " else ""
            val inputGain = try { formatDb(it.getInputGainByChannelIndex(0)) } catch (_: Exception) { "?" }
            "Active (${preEq}input gain $inputGain dB, Limiter $LIMITER_THRESHOLD_DB dBFS)"
        } ?: "Offline"
        val curve = if (curveOnDpe) "DynamicsProcessing pre-EQ" else "HW Equalizer"
        val staging = "Gain staging: makeup ${if (makeupGainEnabled && !(curveOnDpe && dpe != null)) "on" else "off"}, set LE $lastLeMilliBels mB, set input gain ${formatDb(lastInputGainDb)} dB"

        return "Curve: $curve\nEffects: ${effectsSummary()}\n${preEqReadback()}$staging\nHW Equalizer: $eqStatus\nLoudnessEnhancer: $leStatus\nBassBoost: $bbStatus\nDynamicsProcessing: $dpeStatus"
    }

    // android auto: head unit has its own gain stage, makeup gain only overdrives the limiter
    var makeupGainEnabled = true
        set(value) {
            if (field == value) return
            field = value
            updateGainCompensation()
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
        isEnabled: Boolean
    ): Boolean {
        release()
        cachedBandGains = bandGains
        cachedPreAmpDb = preAmpDb
        var anySuccess = false

        try {
            hwEq = Equalizer(1000, audioSessionId).apply {
                enabled = isEnabled
                watch(this, "Equalizer", forwardControl = true)
            }
            hwEqBandsCount = hwEq?.numberOfBands?.toInt() ?: 0
            AppLogger.log(tag, "Session $audioSessionId: Hardware Equalizer connected ($hwEqBandsCount bands)")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Error creating HW Equalizer: ${e.message}")
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = isEnabled
                watch(this, "LoudnessEnhancer")
            }
            AppLogger.log(tag, "Session $audioSessionId: LoudnessEnhancer connected")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Error creating LoudnessEnhancer: ${e.message}")
        }

        // curve on dpe pre-eq (true 10 bands) + brickwall limiter, hw eq stays as fallback
        try {
            dpe = createDpe(withPreEq = true, isEnabled = isEnabled)
            curveOnDpe = true
            AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing pre-eq (${EqualizerDefaults.BANDS.size} bands) + limiter connected")
            anySuccess = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing pre-eq unavailable: ${e.message}")
            try {
                dpe = createDpe(withPreEq = false, isEnabled = isEnabled)
                AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing Limiter connected, curve on HW Equalizer")
                anySuccess = true
            } catch (e2: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: DynamicsProcessing unavailable: ${e2.message}")
            }
        }
        try {
            hwEq?.enabled = isEnabled && !curveOnDpe
        } catch (_: Exception) {}

        // bass boost is a low shelf in the dpe curve. the nxp bundle bass boost lowers the whole
        // mix by its energy estimate (LvmEffect_limitLevel, -6 db at 35%) and lifts only ~90 hz
        if (!curveOnDpe) {
            try {
                hwBass = BassBoost(1000, audioSessionId).apply {
                    enabled = isEnabled && bassBoostPercent > 0f
                    if (strengthSupported) {
                        setStrength((bassBoostPercent * 10f).toInt().toShort().coerceIn(0, 1000))
                    }
                    watch(this, "BassBoost")
                }
                AppLogger.log(tag, "Session $audioSessionId: BassBoost initialized")
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error initializing BassBoost: ${e.message}")
            }
        }

        dpe?.let { watch(it, "DynamicsProcessing") }
        AppLogger.log(tag, "Session $audioSessionId effects: ${effectsSummary()}")

        isInitialized = anySuccess
        if (isInitialized) {
            updateBands(bandGains, bassBoostPercent)
            updateGainCompensation()
        }
        return isInitialized
    }

    private var isEnabled = true
    private var lastLoggedEnabled: Boolean? = null

    // resend the curve even if unchanged, used after playback restarts
    fun reapplyCurve() {
        if (!isInitialized || !isEnabled) return
        lastPreEqGains = null
        updateBands(cachedBandGains, cachedBassBoostPercent)
    }

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        try {
            dpe?.let { enableChecked(it, "DynamicsProcessing", enabled) }
            hwEq?.let { enableChecked(it, "Equalizer", enabled && !curveOnDpe) }
            hwBass?.let { enableChecked(it, "BassBoost", enabled && (cachedBassBoostPercent > 0f)) }
            loudnessEnhancer?.let { enableChecked(it, "LoudnessEnhancer", enabled && !curveOnDpe) }
            if (enabled != lastLoggedEnabled) {
                lastLoggedEnabled = enabled
                AppLogger.log(tag, "Session $audioSessionId master ${if (enabled) "on" else "off"}: ${effectsSummary()}")
            }

            if (!enabled) {
                resetToFlat()
            } else {
                // some roms (hios) drop dpe params while it is disabled or the track restarts, always resend
                lastPreEqGains = null
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

        val targets = buildCurve(bandGains, bassBoostPercent)

        if (curveOnDpe) {
            applyPreEq(targets)
        } else {
            hwEq?.let { eq ->
                try {
                    val numBands = eq.numberOfBands.toInt()
                    val minLevel = eq.bandLevelRange[0]
                    val maxLevel = eq.bandLevelRange[1]
                    // hw bands rarely match ours (nxp gives 5), sample the curve at each hw center
                    for (b in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val targetMilliBels = (sampleCurve(targets, centerFreqHz) * 100).toInt().toShort().coerceIn(minLevel, maxLevel)
                        eq.setBandLevel(b.toShort(), targetMilliBels)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error updating hardware EQ: ${e.message}")
                }
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

    private var lastLeMilliBels = -1
    private var lastInputGainDb = Float.NaN

    fun updateGainCompensation() {
        if (!isInitialized || !isEnabled) {
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (_: Exception) {}
            lastLeMilliBels = -1
            return
        }

        // makeup follows user boosts only, smart dynamic bass is a bass-only loudness curve
        // and must not lift the whole mix at low volume
        val maxBandBoost = (cachedBandGains.maxOrNull() ?: 0f).coerceAtLeast(0f)
        val bassBoostCompensation = (cachedBassBoostPercent.coerceIn(0f, 100f) / 100f) * 3.5f

        val maxBoostDb = maxOf(maxBandBoost, bassBoostCompensation)

        // with the curve on dpe there is no makeup: boosts stacked on top of it drove the limiter into
        // audible pumping on bass, and hios gives uneven loudness after pause with le. pre-amp goes to
        // dpe input gain, le stays off. otherwise le boosts, dpe only cuts
        val gainOnDpe = curveOnDpe && dpe != null

        // no fixed floor: flat bands with 0 db pre-amp leave the signal untouched
        val totalGainDb = if (makeupGainEnabled && !gainOnDpe) maxBoostDb + cachedPreAmpDb else cachedPreAmpDb
        val boostDb = if (gainOnDpe) 0f else totalGainDb.coerceIn(0f, 12f)
        val cutDb = if (gainOnDpe) totalGainDb.coerceIn(-12f, 12f) else totalGainDb.coerceIn(-12f, 0f)
        val targetMilliBels = (boostDb * 100).toInt().coerceIn(0, 1200)

        loudnessEnhancer?.let { le ->
            try {
                le.enabled = (targetMilliBels > 0)
                le.setTargetGain(targetMilliBels)
            } catch (e: Exception) {
                AppLogger.log(tag, "Error setting LoudnessEnhancer: ${e.message}")
            }
        }
        dpe?.let { dp ->
            try {
                dp.setInputGainAllChannelsTo(cutDb)
            } catch (e: Exception) {
                AppLogger.log(tag, "Error setting DynamicsProcessing input gain: ${e.message}")
            }
        }
        if (targetMilliBels != lastLeMilliBels || cutDb != lastInputGainDb) {
            lastLeMilliBels = targetMilliBels
            lastInputGainDb = cutDb
            AppLogger.log(tag, "Session $audioSessionId gain staging: LoudnessEnhancer $targetMilliBels mB, input gain $cutDb dB")
        }
    }

    fun updatePreAmp(preAmpDb: Float) {
        cachedPreAmpDb = preAmpDb
        if (!isInitialized) return

        updateGainCompensation()
    }

    private fun createDpe(withPreEq: Boolean, isEnabled: Boolean): DynamicsProcessing {
        val bandCount = if (withPreEq) EqualizerDefaults.BANDS.size else 0
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            withPreEq,
            bandCount,
            false,
            0,
            false,
            0,
            true
        )
        if (withPreEq) {
            builder.setPreEqAllChannelsTo(buildPreEq(FloatArray(bandCount)))
        }
        for (channel in 0..1) {
            builder.setLimiterByChannelIndex(channel, buildLimiter())
        }
        return DynamicsProcessing(1000, audioSessionId, builder.build()).apply {
            enabled = isEnabled
        }
    }

    // flat curve disables the stage so honest flat stays untouched
    private fun buildPreEq(gains: FloatArray): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(true, gains.any { it != 0f }, gains.size)
        for (i in gains.indices) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, PRE_EQ_CUTOFFS[i], gains[i]))
        }
        return eq
    }

    private var lastPreEqGains: FloatArray? = null
    private var lastPreEqLogTime = 0L

    private fun formatGains(gains: FloatArray) = gains.joinToString(" ", "[", "]") { formatDb(it) }

    private fun formatDb(db: Float) = String.format(Locale.US, "%.1f", db)

    // reads back what the native effect really holds, catches roms that drop parameters
    private fun preEqReadback(): String {
        if (!curveOnDpe) return ""
        val dp = dpe ?: return ""
        return try {
            val eq = dp.getPreEqByChannelIndex(0)
            val gains = FloatArray(eq.bandCount) { eq.getBand(it).gain }
            "Pre-EQ readback (ch0): stage ${if (eq.isEnabled) "on" else "off"} ${formatGains(gains)}\n"
        } catch (e: Exception) {
            "Pre-EQ readback failed: ${e.message}\n"
        }
    }

    private fun watch(effect: AudioEffect, name: String, forwardControl: Boolean = false) {
        try {
            effect.setControlStatusListener { _, granted ->
                AppLogger.log(tag, "Session $audioSessionId $name control: ${if (granted) "granted" else "lost"}")
                if (forwardControl) onControlStatusChanged?.invoke(granted)
            }
            effect.setEnableStatusListener { _, on ->
                AppLogger.log(tag, "Session $audioSessionId $name enable status changed by another client: $on")
            }
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId $name listeners unavailable: ${e.message}")
        }
    }

    private fun enableChecked(effect: AudioEffect, name: String, on: Boolean) {
        val rc = try { effect.setEnabled(on) } catch (_: Exception) { AudioEffect.ERROR }
        if (rc != AudioEffect.SUCCESS) {
            AppLogger.log(tag, "Session $audioSessionId $name setEnabled($on) failed: rc=$rc")
        }
    }

    private fun describe(effect: AudioEffect?, name: String): String? {
        effect ?: return null
        return try {
            "$name(id=${effect.id}, ${if (effect.enabled) "on" else "off"}, ${if (effect.hasControl()) "control" else "NO CONTROL"})"
        } catch (e: Exception) {
            "$name(dead: ${e.message})"
        }
    }

    fun effectsSummary(): String = listOfNotNull(
        describe(hwEq, "Equalizer"),
        describe(hwBass, "BassBoost"),
        describe(loudnessEnhancer, "LoudnessEnhancer"),
        describe(dpe, "DynamicsProcessing"),
    ).joinToString(", ").ifEmpty { "none" }

    private fun applyPreEq(gains: FloatArray) {
        if (lastPreEqGains?.contentEquals(gains) == true) return
        dpe?.let { dp ->
            try {
                dp.setPreEqAllChannelsTo(buildPreEq(gains))
                lastPreEqGains = gains
                // throttled, drags push dozens of updates per second
                val now = SystemClock.uptimeMillis()
                if (now - lastPreEqLogTime >= PRE_EQ_LOG_INTERVAL_MS) {
                    lastPreEqLogTime = now
                    AppLogger.log(tag, "Session $audioSessionId pre-eq set: ${formatGains(gains)}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating pre-eq: ${e.message}")
            }
        }
    }

    // final per-band gain in db: user curve + smart dynamic bass + vocal preservation
    private fun buildCurve(bandGains: List<Float>, bassBoostPercent: Float): FloatArray {
        // vocal presence boost if low end is pushed
        val totalBassActive = maxOf(
            bandGains.take(3).maxOrNull() ?: 0f,
            (bassBoostPercent / 100f) * 6f
        )
        val vocalPreservation = if (totalBassActive > 1.5f) {
            (totalBassActive * 0.25f).coerceAtMost(2.0f)
        } else {
            0f
        }

        return FloatArray(EqualizerDefaults.BANDS.size) { i ->
            val freqHz = EqualizerDefaults.BANDS[i].frequencyHz
            val dynGain = when (i) {
                0 -> dynamicOffset31
                1 -> dynamicOffset62
                2 -> dynamicOffset125
                else -> 0f
            }
            // no hw bass boost: low shelf, full depth up to 62 hz, fading out by 250 hz
            val shelfDepth = when {
                freqHz <= 62 -> 1f
                freqHz <= 125 -> 0.6f
                freqHz <= 250 -> 0.2f
                else -> 0f
            }
            val shelfPercent = (bassBoostPercent + dynamicOffsetBassBoost).coerceIn(0f, 100f)
            val extraBass = if (hwBass == null) (shelfPercent / 100f) * BASS_SHELF_MAX_DB * shelfDepth else 0f
            val speechBoost = if (freqHz in 800..3000) vocalPreservation else 0f
            (bandGains.getOrElse(i) { 0f } + dynGain + extraBass + speechBoost).coerceIn(-MAX_BAND_DB, MAX_BAND_DB)
        }
    }

    // log-frequency interpolation between our band centers
    private fun sampleCurve(curve: FloatArray, freqHz: Float): Float {
        val bands = EqualizerDefaults.BANDS
        if (freqHz <= bands.first().frequencyHz) return curve.first()
        if (freqHz >= bands.last().frequencyHz) return curve.last()
        val upper = bands.indexOfFirst { it.frequencyHz >= freqHz }
        val lower = upper - 1
        val t = ln(freqHz / bands[lower].frequencyHz) / ln(bands[upper].frequencyHz.toFloat() / bands[lower].frequencyHz)
        return curve[lower] + (curve[upper] - curve[lower]) * t
    }

    // limiter is a safety stage, always on while dsp is enabled
    private fun buildLimiter() = DynamicsProcessing.Limiter(
        true,
        true,
        0,
        LIMITER_ATTACK_MS,
        LIMITER_RELEASE_MS,
        LIMITER_RATIO,
        LIMITER_THRESHOLD_DB,
        0.0f,
    )

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
        curveOnDpe = false
        lastPreEqGains = null

        try { hwEq?.setControlStatusListener(null) } catch (_: Exception) {}
        try { hwEq?.enabled = false } catch (_: Exception) {}
        try { hwEq?.release() } catch (_: Exception) {}
        hwEq = null

        try { hwBass?.enabled = false } catch (_: Exception) {}
        try { hwBass?.release() } catch (_: Exception) {}
        hwBass = null

        isInitialized = false
    }

    companion object {
        const val LIMITER_THRESHOLD_DB = -1.5f
        private const val BASS_SHELF_MAX_DB = 8f
        private const val LIMITER_ATTACK_MS = 1.5f
        private const val LIMITER_RELEASE_MS = 35.0f
        private const val LIMITER_RATIO = 15.0f
        private const val MAX_BAND_DB = 20f
        private const val PRE_EQ_LOG_INTERVAL_MS = 1000L

        // dpe bands are defined by their upper edge: geometric midpoint to the next center
        private val PRE_EQ_CUTOFFS = EqualizerDefaults.BANDS.let { bands ->
            FloatArray(bands.size) { i ->
                if (i == bands.lastIndex) 20000f
                else sqrt(bands[i].frequencyHz.toFloat() * bands[i + 1].frequencyHz)
            }
        }
    }
}
