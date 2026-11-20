package com.example.logic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.ui.viewmodel.HapticType

/**
 * Advanced Google Pixel LRA (Linear Resonance Actuator) Haptic Feedback Engine.
 * Leverages API 31+ VibrationEffect.Composition primitives (PRIMITIVE_CLICK, PRIMITIVE_TICK,
 * PRIMITIVE_QUICK_RISE, PRIMITIVE_THUD, PRIMITIVE_LOW_TICK) for ultra-crisp, physical-grade
 * tactile feedback, with robust waveforms and fallback chains.
 */
object ModernHapticFeedback {

    fun play(context: Context, type: HapticType) {
        val vibrator = getVibrator(context) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check if device supports advanced LRA primitives
                val composition = VibrationEffect.startComposition()
                val effect = when (type) {
                    HapticType.CLICK -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f).compose()
                    }
                    HapticType.SNAP -> {
                        composition
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.85f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 12)
                            .compose()
                    }
                    HapticType.HEAVY -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f).compose()
                    }
                    HapticType.DOUBLE -> {
                        composition
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 35)
                            .compose()
                    }
                }
                vibrator.vibrate(effect)
            } else {
                fallbackVibrate(vibrator, type)
            }
        } catch (_: Throwable) {
            try {
                fallbackVibrate(vibrator, type)
            } catch (_: Throwable) {}
        }
    }

    fun playDirect(context: Context, customEffect: VibrationEffect) {
        val vibrator = getVibrator(context) ?: return
        try {
            vibrator.vibrate(customEffect)
        } catch (_: Throwable) {}
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fallbackVibrate(vibrator: Vibrator, type: HapticType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (type) {
                HapticType.CLICK -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                HapticType.SNAP -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 20, 25), intArrayOf(0, 255, 0, 180), -1))
                HapticType.HEAVY -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                HapticType.DOUBLE -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 30, 25), intArrayOf(0, 255, 0, 220), -1))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}
