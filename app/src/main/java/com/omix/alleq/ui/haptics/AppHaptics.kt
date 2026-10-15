package com.omix.alleq.ui.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * High-performance hardware haptic engine tailored for cyber-audio dials,
 * pickers, and console controls across Samsung One UI, OriginOS, and AOSP.
 */
object AppHaptics {

    private var vibrator: Vibrator? = null
    private var lastTickTimestamp: Long = 0L
    private const val MIN_TICK_INTERVAL_MS = 18L

    fun init(context: Context) {
        if (vibrator == null) {
            vibrator = getVibrator(context.applicationContext)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Crisp mechanical tick for wheel scrolling (3D drum), sliders, and discrete stepped adjustments.
     * Throttled to 18ms so high-speed flings produce clean distinct ticks without motor choke.
     */
    fun tick(view: View? = null, context: Context? = null) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickTimestamp < MIN_TICK_INTERVAL_MS) return
        lastTickTimestamp = now

        perform(
            view = view,
            context = context,
            hapticConstant = HapticFeedbackConstants.CLOCK_TICK,
            predefinedEffect = VibrationEffect.EFFECT_TICK,
            fallbackMillis = 8L,
            fallbackAmplitude = 110,
        )
    }

    /**
     * Standard tactile click for button presses, pills, and preset selections.
     */
    fun click(view: View? = null, context: Context? = null) {
        perform(
            view = view,
            context = context,
            hapticConstant = HapticFeedbackConstants.KEYBOARD_TAP,
            predefinedEffect = VibrationEffect.EFFECT_CLICK,
            fallbackMillis = 14L,
            fallbackAmplitude = 170,
        )
    }

    /**
     * Heavy tactile pulse for destructive operations, resets, or long-press activations.
     */
    fun heavyClick(view: View? = null, context: Context? = null) {
        perform(
            view = view,
            context = context,
            hapticConstant = HapticFeedbackConstants.LONG_PRESS,
            predefinedEffect = VibrationEffect.EFFECT_HEAVY_CLICK,
            fallbackMillis = 28L,
            fallbackAmplitude = 250,
        )
    }

    private fun perform(
        view: View?,
        context: Context?,
        hapticConstant: Int,
        predefinedEffect: Int,
        fallbackMillis: Long,
        fallbackAmplitude: Int,
    ) {
        // try view feedback first with bypass flags
        if (view != null) {
            try {
                view.performHapticFeedback(
                    hapticConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
            } catch (_: Throwable) {}
        }

        // hardware vibrator direct pulse
        try {
            val vib = vibrator ?: (context?.let { getVibrator(it) })
            if (vib != null && vib.hasVibrator()) {
                val effect = try {
                    VibrationEffect.createPredefined(predefinedEffect)
                } catch (_: Throwable) {
                    val amp = if (vib.hasAmplitudeControl()) fallbackAmplitude else VibrationEffect.DEFAULT_AMPLITUDE
                    VibrationEffect.createOneShot(fallbackMillis, amp)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                    vib.vibrate(effect, attrs)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val attrs = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
                    vib.vibrate(effect, attrs)
                } else {
                    val attrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                    vib.vibrate(effect, attrs)
                }
            }
        } catch (_: Throwable) {}
    }
}
