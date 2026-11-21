package com.example.logic.vulkan

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import android.util.Log
import com.example.logic.ThermalTier

/**
 * Hardware GPU micro-architecture families found across Google Pixel devices and Android targets.
 */
enum class PixelGpuArchitecture {
    /** Google Pixel 6–9 (Tensor G1–G4): ARM Mali / Immortalis TBDR with AFBC & FP16 Valhall ALUs */
    MALI_TBDR,

    /** Google Pixel 10+ (Tensor G5+): Imagination PowerVR DXT TBDR with Hardware HSR & strict image caps */
    POWERVR_TBDR,

    /** Qualcomm Adreno FlexRender TBR */
    ADRENO_TBR,

    /** Generic Vulkan 1.4 tile-based hardware / emulator fallback */
    GENERIC_VULKAN
}

/**
 * Detected Google Pixel Tensor SoC generation for targeted GPU memory & ALU tuning.
 */
enum class PixelTensorGeneration {
    TENSOR_G5_OR_NEWER,
    TENSOR_G3_G4,
    TENSOR_G1_G2,
    NON_PIXEL
}

/**
 * Static hardware profile of the active GPU on this device.
 */
data class PixelGpuProfile(
    val architecture: PixelGpuArchitecture,
    val tensorGeneration: PixelTensorGeneration,
    val hardwareModel: String,
    val isGooglePixel: Boolean,
    val gpuName: String,
    val supportsFp16Alu: Boolean,
    val supportsSubpassLoadStoreOpt: Boolean,
    val maxSafeScratchTextureDim: Int,
    val recommendedTileSizePx: Int,
    val recommendedMaxFramesInFlight: Int
)

/**
 * Dynamic per-frame GPU rendering policy adapting AGSL fragment shaders, offscreen downsample
 * scale, Vulkan TBDR load/store actions, and pre-rotation to Pixel thermal & refresh state.
 */
data class PixelGpuRenderPolicy(
    val architecture: PixelGpuArchitecture = PixelGpuArchitecture.MALI_TBDR,
    val thermalTier: ThermalTier = ThermalTier.OPTIMAL,
    val targetFps: Int = 60,
    val agslBlurTapCount: Int = 16,
    val backdropDownsampleScale: Float = 0.67f,
    val tier2DownsampleMultiplier: Float = 0.50f,
    val enableDeepGaussianTier2: Boolean = true,
    val useHalfPrecisionShaders: Boolean = true,
    val enablePreRotation: Boolean = true,
    val tbdrLoadOpDontCare: Boolean = true,
    val tbdrDepthStoreDontCare: Boolean = true,
    val maxScratchTextureDim: Int = 2048,
    val maxCircleSegments: Int = 48,
    val enableSpatialGridPass: Boolean = true
) {
    /**
     * Estimates DRAM memory bandwidth reduction (%) compared to unscaled dual-pass full-res blur:
     * - Baseline dual-pass full-res = 2.0x pixel fill area
     * - Tier 1 sampled once from source = s^2
     * - Tier 2 chained from Tier 1 (half-res pyramid) = (s * tier2Mult)^2 when enabled, else 0
     */
    fun estimatedTbdrBandwidthSavedPercent(): Int {
        val s = backdropDownsampleScale.coerceIn(0.35f, 1.0f)
        val t2 = if (enableDeepGaussianTier2) (s * tier2DownsampleMultiplier.coerceIn(0.35f, 1.0f)) else 0f
        val usedFillRatio = (s * s + t2 * t2) / 2.0f
        return ((1.0f - usedFillRatio) * 100f).toInt().coerceIn(0, 95)
    }

    companion object {
        val DEFAULT = PixelGpuRenderPolicy()
    }
}

/**
 * Safe wrapper around Android ADPF [PerformanceHintManager.Session] (API 31+).
 * Informs Google Tensor's power governor & Mali/PowerVR DVFS about the Vulkan render thread's
 * frame work duration target (e.g. 8.33ms for 120Hz, 16.67ms for 60Hz) to lock smooth GPU clocks
 * using EMA-smoothed duration reports that suppress single-frame jitter.
 */
class PixelGpuHintSession private constructor(
    private val session: Any?
) {
    companion object {
        private const val TAG = "PixelGpuHintSession"
        private const val DURATION_EMA_ALPHA = 0.25f

        fun createForCurrentThread(context: Context, targetFps: Int): PixelGpuHintSession? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return try {
                val manager = context.getSystemService(PerformanceHintManager::class.java) ?: return null
                val tid = Process.myTid()
                val targetNs = targetFpsToDurationNs(targetFps)
                val hintSession = manager.createHintSession(intArrayOf(tid), targetNs) ?: return null
                Log.i(TAG, "ADPF PerformanceHintManager session created (tid=$tid, targetFps=$targetFps, budget=${targetNs / 1_000_000f}ms)")
                PixelGpuHintSession(hintSession).apply {
                    lastTargetFps = targetFps
                    targetDurationNs = targetNs
                }
            } catch (t: Throwable) {
                Log.d(TAG, "ADPF PerformanceHintManager unavailable: ${t.message}")
                null
            }
        }

        fun targetFpsToDurationNs(targetFps: Int): Long {
            val safeFps = targetFps.coerceIn(24, 120)
            // Keep a slight 4% safety headroom below VSync period for Tensor DVFS stability
            return (1_000_000_000L * 96L) / (safeFps.toLong() * 100L)
        }

        /**
         * Pure helper computing outlier-clamped EMA work duration in nanoseconds so
         * OS/garbage-collection spikes don't cause excessive DVFS clock oscillation.
         */
        fun smoothWorkDurationNs(
            previousSmoothedNs: Long,
            rawActualNs: Long,
            targetBudgetNs: Long
        ): Long {
            val clampedRaw = rawActualNs.coerceIn(100_000L, (targetBudgetNs * 5L).coerceIn(20_000_000L, 100_000_000L))
            if (previousSmoothedNs <= 0L) return clampedRaw
            return ((previousSmoothedNs * (1f - DURATION_EMA_ALPHA)) + (clampedRaw * DURATION_EMA_ALPHA)).toLong()
                .coerceAtLeast(100_000L)
        }
    }

    @Volatile
    private var lastTargetFps: Int = -1
    @Volatile
    private var targetDurationNs: Long = 16_000_000L
    @Volatile
    var lastSmoothedDurationNs: Long = 0L
        private set
    @Volatile
    var reportedFrameCount: Long = 0L
        private set

    fun updateTargetFps(targetFps: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (lastTargetFps == targetFps) return
        lastTargetFps = targetFps
        val nextBudgetNs = targetFpsToDurationNs(targetFps)
        targetDurationNs = nextBudgetNs
        try {
            val s = session as? PerformanceHintManager.Session ?: return
            s.updateTargetWorkDuration(nextBudgetNs)
        } catch (_: Throwable) {
        }
    }

    fun reportFrameDurationNs(actualDurationNs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (actualDurationNs <= 0L) return
        val smoothed = smoothWorkDurationNs(lastSmoothedDurationNs, actualDurationNs, targetDurationNs)
        lastSmoothedDurationNs = smoothed
        reportedFrameCount++
        try {
            val s = session as? PerformanceHintManager.Session ?: return
            s.reportActualWorkDuration(smoothed)
        } catch (_: Throwable) {
        }
    }

    fun close() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val s = session as? PerformanceHintManager.Session ?: return
            s.close()
        } catch (_: Throwable) {
        }
    }
}

/**
 * Hardware-aware GPU Optimizer tailored for Google Pixel Tensor GPUs
 * (ARM Mali/Immortalis TBDR on Pixel 6–9 & Imagination PowerVR DXT TBDR on Pixel 10+).
 */
object PixelGpuOptimizer {

    fun detectGpuProfile(
        context: Context,
        model: String = Build.MODEL ?: "",
        hardware: String = Build.HARDWARE ?: "",
        socModel: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "",
        manufacturer: String = Build.MANUFACTURER ?: "",
        brand: String = Build.BRAND ?: "",
        rendererHint: String = ""
    ): PixelGpuProfile {
        val isPixel = manufacturer.equals("Google", ignoreCase = true) ||
            brand.equals("google", ignoreCase = true) ||
            model.contains("Pixel", ignoreCase = true)

        val combined = "$model $hardware $socModel $rendererHint".lowercase()

        val tensorGen = when {
            !isPixel -> PixelTensorGeneration.NON_PIXEL
            combined.contains("pixel 10") ||
                combined.contains("tensor g5") ||
                combined.contains("laguna") ||
                combined.contains("powervr") ||
                combined.contains("dxt") -> PixelTensorGeneration.TENSOR_G5_OR_NEWER
            combined.contains("pixel 9") ||
                combined.contains("pixel 8") ||
                combined.contains("tensor g4") ||
                combined.contains("tensor g3") ||
                combined.contains("zuma") ||
                combined.contains("immortalis") ||
                combined.contains("g715") -> PixelTensorGeneration.TENSOR_G3_G4
            combined.contains("pixel 7") ||
                combined.contains("pixel 6") ||
                combined.contains("tensor") ||
                combined.contains("gs101") ||
                combined.contains("gs201") ||
                combined.contains("g710") ||
                combined.contains("g78") -> PixelTensorGeneration.TENSOR_G1_G2
            else -> PixelTensorGeneration.TENSOR_G3_G4
        }

        val architecture = when {
            combined.contains("powervr") ||
                combined.contains("imagination") ||
                combined.contains("dxt-48") ||
                tensorGen == PixelTensorGeneration.TENSOR_G5_OR_NEWER -> PixelGpuArchitecture.POWERVR_TBDR
            combined.contains("adreno") || combined.contains("qcom") -> PixelGpuArchitecture.ADRENO_TBR
            isPixel || combined.contains("mali") || combined.contains("immortalis") -> PixelGpuArchitecture.MALI_TBDR
            else -> PixelGpuArchitecture.GENERIC_VULKAN
        }

        val gpuName = when (architecture) {
            PixelGpuArchitecture.POWERVR_TBDR -> "Imagination PowerVR DXT™ TBDR (Pixel Tensor G5+)"
            PixelGpuArchitecture.MALI_TBDR -> when (tensorGen) {
                PixelTensorGeneration.TENSOR_G3_G4 -> "ARM Mali-G715 / Immortalis™ TBDR (Pixel Tensor G3/G4)"
                PixelTensorGeneration.TENSOR_G1_G2 -> "ARM Mali-G78 / G710™ TBDR (Pixel Tensor G1/G2)"
                else -> "ARM Mali / Immortalis™ TBDR"
            }
            PixelGpuArchitecture.ADRENO_TBR -> "Qualcomm Adreno™ FlexRender TBR"
            PixelGpuArchitecture.GENERIC_VULKAN -> "Vulkan 1.4 Tile-Based GPU"
        }

        // PowerVR drivers strictly cap certain offscreen image targets at 2048px; Mali handles 4096px cleanly.
        val maxScratchDim = if (architecture == PixelGpuArchitecture.POWERVR_TBDR) 2048 else 4096
        val tileSize = when (architecture) {
            PixelGpuArchitecture.POWERVR_TBDR -> 32
            PixelGpuArchitecture.MALI_TBDR -> 16
            else -> 32
        }

        val vulkanCaps = try {
            VulkanDeviceManager.checkCapabilities(context)
        } catch (_: Throwable) {
            null
        }
        val computeSupported = vulkanCaps?.vulkanComputeSupported ?: true

        return PixelGpuProfile(
            architecture = architecture,
            tensorGeneration = tensorGen,
            hardwareModel = model.ifBlank { "Google Pixel" },
            isGooglePixel = isPixel,
            gpuName = gpuName,
            supportsFp16Alu = true,
            supportsSubpassLoadStoreOpt = architecture == PixelGpuArchitecture.MALI_TBDR ||
                architecture == PixelGpuArchitecture.POWERVR_TBDR ||
                computeSupported,
            maxSafeScratchTextureDim = maxScratchDim,
            recommendedTileSizePx = tileSize,
            recommendedMaxFramesInFlight = 2
        )
    }

    /**
     * Computes dynamic Pixel GPU render settings balancing visual fidelity and TBDR memory bandwidth
     * across 120Hz Smooth Display, 60Hz, and thermal throttling tiers.
     */
    fun evaluateRenderPolicy(
        profile: PixelGpuProfile,
        thermalTier: ThermalTier,
        targetFps: Int
    ): PixelGpuRenderPolicy {
        val isHighRefresh = targetFps >= 90

        val tapCount = when (thermalTier) {
            ThermalTier.THROTTLED -> 8
            ThermalTier.WARM -> 12
            ThermalTier.OPTIMAL -> if (isHighRefresh) 12 else 16
        }

        // Downsampled offscreen backdrop buffers cut Mali/PowerVR TBDR DRAM bandwidth by ~70%-88%
        val downsampleScale = when (thermalTier) {
            ThermalTier.THROTTLED -> 0.50f
            ThermalTier.WARM -> 0.55f
            ThermalTier.OPTIMAL -> when {
                isHighRefresh -> 0.60f
                profile.tensorGeneration == PixelTensorGeneration.TENSOR_G1_G2 -> 0.625f
                else -> 0.67f
            }
        }

        val tier2Multiplier = when {
            profile.architecture == PixelGpuArchitecture.POWERVR_TBDR -> 0.45f
            thermalTier == ThermalTier.WARM -> 0.45f
            else -> 0.50f
        }

        val circleSegments = when (thermalTier) {
            ThermalTier.THROTTLED -> 24
            ThermalTier.WARM -> 32
            ThermalTier.OPTIMAL -> if (isHighRefresh) 40 else 48
        }

        // Avoid dual offscreen Gaussian layers and spatial grid overhead when thermally throttled
        val enableTier2 = thermalTier != ThermalTier.THROTTLED
        val enableGridPass = thermalTier != ThermalTier.THROTTLED

        return PixelGpuRenderPolicy(
            architecture = profile.architecture,
            thermalTier = thermalTier,
            targetFps = targetFps,
            agslBlurTapCount = tapCount,
            backdropDownsampleScale = downsampleScale,
            tier2DownsampleMultiplier = tier2Multiplier,
            enableDeepGaussianTier2 = enableTier2,
            useHalfPrecisionShaders = profile.supportsFp16Alu,
            enablePreRotation = true,
            tbdrLoadOpDontCare = true,
            tbdrDepthStoreDontCare = true,
            maxScratchTextureDim = profile.maxSafeScratchTextureDim,
            maxCircleSegments = circleSegments,
            enableSpatialGridPass = enableGridPass
        )
    }
}
