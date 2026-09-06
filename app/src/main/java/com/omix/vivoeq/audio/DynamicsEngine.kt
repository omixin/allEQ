package com.omix.vivoeq.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.util.Log
import com.omix.vivoeq.model.EqualizerDefaults
import kotlin.math.abs

class DynamicsEngine(val audioSessionId: Int) {

    private val tag = "DynamicsEngine"
    private var dpe: DynamicsProcessing? = null
    private var hwEq: Equalizer? = null
    private var hwBass: BassBoost? = null

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
        var anySuccess = false

        // 1. Аппаратный Equalizer (работает на ВСЕХ устройствах, включая динамики Vivo)
        try {
            hwEq = Equalizer(1000, audioSessionId).apply {
                enabled = isEnabled
            }
            hwEqBandsCount = hwEq?.numberOfBands?.toInt() ?: 0
            Log.d(tag, "Аппаратный Equalizer подключен к сессии $audioSessionId (полос: $hwEqBandsCount)")
            anySuccess = true
        } catch (e: Exception) {
            Log.w(tag, "Не удалось создать аппаратный Equalizer на сессии $audioSessionId: ${e.message}")
        }

        // 2. Аппаратный BassBoost (бас для динамиков)
        try {
            hwBass = BassBoost(1000, audioSessionId).apply {
                enabled = isEnabled && bassBoostPercent > 0f
                if (strengthSupported) {
                    setStrength((bassBoostPercent * 10f).toInt().toShort().coerceIn(0, 1000))
                }
            }
        } catch (_: Exception) {
        }

        // 3. DynamicsProcessing (PreEQ + PostEQ + Limiter / Pre-Amp)
        try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, // stereo
                true, // preEqInUse
                10, // preEqBandCount
                false, // mbcInUse
                0,
                true, // postEqInUse (для чипов, где PreEq заблокирован на динамиках)
                10, // postEqBandCount
                true // limiterInUse
            )

            for (channel in 0..1) {
                // Настройка PreEQ и PostEQ
                val eq = DynamicsProcessing.Eq(true, true, 10)
                EqualizerDefaults.BANDS.forEachIndexed { i, band ->
                    val gain = bandGains.getOrElse(i) { 0f }
                    val extraBass = if (i <= 2) (bassBoostPercent / 100f) * 6f else 0f
                    val eqBand = DynamicsProcessing.EqBand(true, band.frequencyHz.toFloat(), gain + extraBass)
                    eq.setBand(i, eqBand)
                }
                builder.setPreEqByChannelIndex(channel, eq)
                builder.setPostEqByChannelIndex(channel, eq)

                // Limiter (Pre-Amp и защита от перегрузки)
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    limiterEnabled,
                    0,
                    2.0f,
                    50.0f,
                    10.0f,
                    -1.0f,
                    preAmpDb
                )
                builder.setLimiterByChannelIndex(channel, limiter)
            }

            val config = builder.build()
            dpe = DynamicsProcessing(1000, audioSessionId, config).apply {
                enabled = isEnabled
            }
            Log.d(tag, "DynamicsProcessing успешно подключен к сессии $audioSessionId (enabled=$isEnabled)")
            anySuccess = true
        } catch (e: Exception) {
            Log.e(tag, "Ошибка DynamicsProcessing на сессии $audioSessionId: ${e.message}")
        }

        isInitialized = anySuccess
        if (isInitialized) {
            updateBands(bandGains, bassBoostPercent)
        }
        return isInitialized
    }

    fun setEnabled(enabled: Boolean) {
        try {
            dpe?.enabled = enabled
            hwEq?.enabled = enabled
            hwBass?.enabled = enabled
        } catch (e: Exception) {
            Log.e(tag, "Ошибка переключения enabled: ${e.message}")
        }
    }

    fun updateBands(bandGains: List<Float>, bassBoostPercent: Float) {
        if (!isInitialized) return

        // Обновляем аппаратный Equalizer
        hwEq?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]

                for (b in 0 until numBands) {
                    val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000
                    // Находим ближайшую полосу из наших 10
                    val nearestBand = EqualizerDefaults.BANDS.minByOrNull {
                        abs(it.frequencyHz - centerFreqHz)
                    }
                    val gainDb = nearestBand?.let { bandGains.getOrElse(it.index) { 0f } } ?: 0f
                    val extraBass = if (centerFreqHz <= 250) (bassBoostPercent / 100f) * 6f else 0f
                    // 1 dB = 100 millibels
                    val targetMilliBels = ((gainDb + extraBass) * 100).toInt().toShort().coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(b.toShort(), targetMilliBels)
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка обновления аппаратного EQ: ${e.message}")
            }
        }

        // Обновляем аппаратный BassBoost
        hwBass?.let { bb ->
            try {
                if (bb.strengthSupported) {
                    bb.enabled = (bassBoostPercent > 0f)
                    bb.setStrength((bassBoostPercent * 10f).toInt().toShort().coerceIn(0, 1000))
                }
            } catch (_: Exception) {
            }
        }

        // Обновляем DynamicsProcessing (PreEQ и PostEQ)
        dpe?.let { effect ->
            try {
                for (channel in 0..1) {
                    EqualizerDefaults.BANDS.forEachIndexed { i, band ->
                        val gain = bandGains.getOrElse(i) { 0f }
                        val extraBass = if (i <= 2) (bassBoostPercent / 100f) * 6f else 0f
                        val eqBand = DynamicsProcessing.EqBand(true, band.frequencyHz.toFloat(), gain + extraBass)
                        effect.setPreEqBandByChannelIndex(channel, i, eqBand)
                        effect.setPostEqBandByChannelIndex(channel, i, eqBand)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка обновления полос DynamicsProcessing: ${e.message}")
            }
        }
    }

    fun updatePreAmpAndLimiter(preAmpDb: Float, limiterEnabled: Boolean) {
        val effect = dpe ?: return
        if (!isInitialized) return

        try {
            for (channel in 0..1) {
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    limiterEnabled,
                    0,
                    2.0f,
                    50.0f,
                    10.0f,
                    -1.0f,
                    preAmpDb
                )
                effect.setLimiterByChannelIndex(channel, limiter)
            }
        } catch (e: Exception) {
            Log.e(tag, "Ошибка обновления лимитера: ${e.message}")
        }
    }

    fun release() {
        try {
            dpe?.enabled = false
            dpe?.release()
        } catch (_: Exception) {
        } finally {
            dpe = null
        }

        try {
            hwEq?.enabled = false
            hwEq?.release()
        } catch (_: Exception) {
        } finally {
            hwEq = null
        }

        try {
            hwBass?.enabled = false
            hwBass?.release()
        } catch (_: Exception) {
        } finally {
            hwBass = null
        }

        isInitialized = false
    }
}
