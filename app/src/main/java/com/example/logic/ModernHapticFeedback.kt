package com.example.logic

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.ui.viewmodel.HapticType
import java.util.EnumMap

/**
 * Advanced Google Pixel LRA (Linear Resonance Actuator) Haptic Feedback Engine.
 * Leverages API 31+ VibrationEffect.Composition primitives (PRIMITIVE_CLICK, PRIMITIVE_TICK,
 * PRIMITIVE_QUICK_RISE, PRIMITIVE_THUD, PRIMITIVE_LOW_TICK) for ultra-crisp, physical-grade
 * tactile feedback, with robust waveforms, IMU-safe cooldown throttling, and fallback chains.
 */
object ModernHapticFeedback {

    private val cooldownLock = Any()
    private val lastPlayedAtMs = EnumMap<HapticType, Long>(HapticType::class.java)

    internal fun cooldownMsFor(type: HapticType): Long = when (type) {
        HapticType.SNAP -> 160L
        HapticType.CLICK -> 45L
        HapticType.HEAVY -> 90L
        HapticType.DOUBLE -> 200L
    }

    /**
     * Thread-safe cooldown check preventing continuous vibration bursts that disturb ARCore IMU tracking.
     * Returns true if playback is allowed for [type] at [nowMs].
     */
    internal fun shouldAllowHaptic(type: HapticType, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        synchronized(cooldownLock) {
            val minGap = cooldownMsFor(type)
            val prev = lastPlayedAtMs[type]
            if (prev != null && (nowMs - prev) in 0 until minGap) {
                return false
            }
            lastPlayedAtMs[type] = nowMs
            return true
        }
    }

    internal fun resetCooldowns() {
        synchronized(cooldownLock) {
            lastPlayedAtMs.clear()
        }
    }

    fun play(context: Context, type: HapticType) {
        if (!shouldAllowHaptic(type)) return
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
