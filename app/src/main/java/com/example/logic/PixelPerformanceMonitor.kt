package com.example.logic

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalTier {
    OPTIMAL,
    WARM,
    THROTTLED
}

data class PixelThermalPolicy(
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val tier: ThermalTier = ThermalTier.OPTIMAL,
    val targetFps: Int = 60,
    val allowDepth: Boolean = true,
    val allowHighRes: Boolean = true,
    val forceDisableTorch: Boolean = false
)

/**
 * Google Pixel & Android Thermal / Sustained Performance Monitor.
 * Uses Android Thermal API (API 29+) and Sustained Performance Mode to keep
 * Tensor SoCs cool and maintain consistent ARCore + Camera2 + Vulkan frame pacing
 * without sudden thermal throttling drops.
 */
class PixelPerformanceMonitor(context: Context) {

    companion object {
        private const val TAG = "PixelPerfMonitor"

        fun isGooglePixelDevice(): Boolean {
            return Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
                Build.BRAND.equals("google", ignoreCase = true)
        }

        /**
         * Pure evaluation mapping OS thermal status + user preferences to an effective runtime policy.
         */
        fun evaluateThermalPolicy(
            thermalStatus: Int,
            userHighFps: Boolean,
            userHighDef: Boolean
        ): PixelThermalPolicy {
            return when {
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.THROTTLED,
                    targetFps = 30,
                    allowDepth = false,
                    allowHighRes = false,
                    forceDisableTorch = true
                )
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.WARM,
                    targetFps = 30,
                    allowDepth = true,
                    allowHighRes = false,
                    forceDisableTorch = false
                )
                else -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.OPTIMAL,
                    targetFps = if (userHighFps) 60 else 30,
                    allowDepth = true,
                    allowHighRes = userHighDef,
                    forceDisableTorch = false
                )
            }
        }
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isSustainedPerformanceSupported: Boolean = powerManager?.isSustainedPerformanceModeSupported == true
    val isPixelDevice: Boolean = isGooglePixelDevice()

    @Volatile
    private var preferHighFps: Boolean = true

    @Volatile
    private var preferHighDef: Boolean = true

    private var lastThermalStatus: Int = try {
        powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
    } catch (_: Throwable) {
        PowerManager.THERMAL_STATUS_NONE
    }

    private val _policy = MutableStateFlow(
        evaluateThermalPolicy(lastThermalStatus, preferHighFps, preferHighDef)
    )
    val policy: StateFlow<PixelThermalPolicy> = _policy.asStateFlow()

    var onPolicyChanged: ((PixelThermalPolicy) -> Unit)? = null

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        lastThermalStatus = status
        recomputePolicy()
    }

    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered) return
        try {
            powerManager?.let { pm ->
                lastThermalStatus = pm.currentThermalStatus
                pm.addThermalStatusListener(thermalListener)
                isRegistered = true
                recomputePolicy()
                Log.i(
                    TAG,
                    "Thermal monitor active (pixel=$isPixelDevice, sustainedPerf=$isSustainedPerformanceSupported, status=$lastThermalStatus)"
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to register thermal listener: ${t.message}")
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        isRegistered = false
        try {
            powerManager?.removeThermalStatusListener(thermalListener)
        } catch (_: Throwable) {
        }
    }

    fun updateUserPreferences(highFps: Boolean, highDef: Boolean) {
        preferHighFps = highFps
        preferHighDef = highDef
        recomputePolicy()
    }

    private fun recomputePolicy() {
        val next = evaluateThermalPolicy(lastThermalStatus, preferHighFps, preferHighDef)
        if (next != _policy.value) {
            _policy.value = next
            onPolicyChanged?.invoke(next)
        }
    }

    fun applySustainedPerformanceMode(window: Window?, enabled: Boolean) {
        if (window == null || !isSustainedPerformanceSupported) return
        try {
            window.setSustainedPerformanceMode(enabled)
        } catch (t: Throwable) {
            Log.w(TAG, "setSustainedPerformanceMode($enabled) skipped: ${t.message}")
        }
    }
}
