package com.example.logic

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
    val forceDisableTorch: Boolean = false,
    val predictedHeadroom: Float = Float.NaN
)

/**
 * Google Pixel & Android Thermal / Sustained Performance Monitor.
 * Combines Android Thermal Status API (API 29+) with rate-limited predictive
 * [PowerManager.getThermalHeadroom] forecasts (API 30+) and Sustained Performance Mode
 * so Tensor SoCs step down gracefully before hitting severe thermal throttling.
 */
class PixelPerformanceMonitor(context: Context) {

    companion object {
        private const val TAG = "PixelPerfMonitor"
        internal const val MIN_HEADROOM_POLL_INTERVAL_MS = 10_500L
        internal const val HEADROOM_FORECAST_SECONDS = 5
        internal const val HEADROOM_WARM_THRESHOLD = 0.85f
        internal const val HEADROOM_THROTTLED_THRESHOLD = 1.00f

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
            userHighDef: Boolean,
            targetRefreshRate: Int = 60
        ): PixelThermalPolicy = evaluateThermalPolicyWithHeadroom(
            thermalStatus = thermalStatus,
            thermalHeadroom = Float.NaN,
            userHighFps = userHighFps,
            userHighDef = userHighDef,
            targetRefreshRate = targetRefreshRate
        )

        /**
         * Pure evaluation combining OS thermal status with ADPF [PowerManager.getThermalHeadroom]
         * forecast heuristics so Pixel Tensor SoCs adapt proactively ahead of severe throttling.
         */
        fun evaluateThermalPolicyWithHeadroom(
            thermalStatus: Int,
            thermalHeadroom: Float,
            userHighFps: Boolean,
            userHighDef: Boolean,
            targetRefreshRate: Int = 60
        ): PixelThermalPolicy {
            val validHeadroom = thermalHeadroom.isFinite() && thermalHeadroom > 0f
            val throttledByHeadroom = validHeadroom && thermalHeadroom >= HEADROOM_THROTTLED_THRESHOLD
            val warmByHeadroom = validHeadroom && thermalHeadroom >= HEADROOM_WARM_THRESHOLD

            return when {
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE || throttledByHeadroom -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.THROTTLED,
                    targetFps = 30,
                    allowDepth = false,
                    allowHighRes = false,
                    forceDisableTorch = true,
                    predictedHeadroom = if (validHeadroom) thermalHeadroom else Float.NaN
                )
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE || warmByHeadroom -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.WARM,
                    targetFps = if (targetRefreshRate >= 120 && userHighFps) 60 else 30,
                    allowDepth = true,
                    allowHighRes = false,
                    forceDisableTorch = false,
                    predictedHeadroom = if (validHeadroom) thermalHeadroom else Float.NaN
                )
                else -> PixelThermalPolicy(
                    thermalStatus = thermalStatus,
                    tier = ThermalTier.OPTIMAL,
                    targetFps = if (userHighFps) targetRefreshRate else 30,
                    allowDepth = true,
                    allowHighRes = userHighDef,
                    forceDisableTorch = false,
                    predictedHeadroom = if (validHeadroom) thermalHeadroom else Float.NaN
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

    private val monitorLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastThermalStatus: Int = try {
        powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
    } catch (_: Throwable) {
        PowerManager.THERMAL_STATUS_NONE
    }

    @Volatile
    private var lastThermalHeadroom: Float = Float.NaN

    @Volatile
    private var lastHeadroomQueryElapsedMs: Long = -MIN_HEADROOM_POLL_INTERVAL_MS

    @Volatile
    private var isThermalHeadroomSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null

    private val _policy = MutableStateFlow(
        evaluateThermalPolicy(lastThermalStatus, preferHighFps, preferHighDef)
    )
    val policy: StateFlow<PixelThermalPolicy> = _policy.asStateFlow()

    var onPolicyChanged: ((PixelThermalPolicy) -> Unit)? = null

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        lastThermalStatus = status
        recomputePolicy()
    }

    private val headroomPollRunnable = object : Runnable {
        override fun run() {
            if (!isRegistered || !isThermalHeadroomSupported) return
            pollThermalHeadroomIfDue()
            if (isRegistered && isThermalHeadroomSupported) {
                mainHandler.postDelayed(this, MIN_HEADROOM_POLL_INTERVAL_MS)
            }
        }
    }

    @Volatile
    private var isRegistered = false

    /**
     * Queries [PowerManager.getThermalHeadroom] strictly honoring the >= 10s rate limit
     * and single-caller serialization required by Android ADPF to prevent NaN responses.
     */
    fun pollThermalHeadroomIfDue(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !isThermalHeadroomSupported) {
            return lastThermalHeadroom
        }
        val pm = powerManager ?: return Float.NaN
        synchronized(monitorLock) {
            if (!isThermalHeadroomSupported) return lastThermalHeadroom
            if (nowElapsedMs - lastHeadroomQueryElapsedMs < MIN_HEADROOM_POLL_INTERVAL_MS) {
                return lastThermalHeadroom
            }
            lastHeadroomQueryElapsedMs = nowElapsedMs
            val reading = try {
                pm.getThermalHeadroom(HEADROOM_FORECAST_SECONDS)
            } catch (_: Throwable) {
                Float.NaN
            }
            if (!reading.isFinite() || reading <= 0f) {
                if (!lastThermalHeadroom.isFinite()) {
                    // Initial probe returned NaN/0 -> unsupported on this device/emulator
                    isThermalHeadroomSupported = false
                }
                return lastThermalHeadroom
            }
            lastThermalHeadroom = reading
        }
        recomputePolicy()
        return lastThermalHeadroom
    }

    fun startMonitoring() {
        if (isRegistered) return
        try {
            powerManager?.let { pm ->
                lastThermalStatus = pm.currentThermalStatus
                pm.addThermalStatusListener(thermalListener)
                isRegistered = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isThermalHeadroomSupported) {
                    pollThermalHeadroomIfDue()
                    if (isThermalHeadroomSupported) {
                        mainHandler.removeCallbacks(headroomPollRunnable)
                        mainHandler.postDelayed(headroomPollRunnable, MIN_HEADROOM_POLL_INTERVAL_MS)
                    }
                }
                recomputePolicy()
                Log.i(
                    TAG,
                    "Thermal monitor active (pixel=$isPixelDevice, sustainedPerf=$isSustainedPerformanceSupported, headroomSupported=$isThermalHeadroomSupported, status=$lastThermalStatus)"
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to register thermal listener: ${t.message}")
        }
    }

    fun stopMonitoring() {
        mainHandler.removeCallbacks(headroomPollRunnable)
        if (!isRegistered) return
        isRegistered = false
        try {
            powerManager?.removeThermalStatusListener(thermalListener)
        } catch (_: Throwable) {
        }
    }

    @Volatile
    private var targetRefreshRate: Int = 120

    fun updateUserPreferences(highFps: Boolean, highDef: Boolean, targetRefreshRate: Int = 120) {
        preferHighFps = highFps
        preferHighDef = highDef
        this.targetRefreshRate = targetRefreshRate
        recomputePolicy()
    }

    private fun recomputePolicy() {
        val next = evaluateThermalPolicyWithHeadroom(
            thermalStatus = lastThermalStatus,
            thermalHeadroom = lastThermalHeadroom,
            userHighFps = preferHighFps,
            userHighDef = preferHighDef,
            targetRefreshRate = targetRefreshRate
        )
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
