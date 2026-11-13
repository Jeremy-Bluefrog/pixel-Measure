package com.example.logic.vulkan

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Manages Vulkan hardware capability queries strictly requiring Vulkan 1.4 (0x404000)
 * on Google Pixel devices with zero legacy backward compatibility.
 */
object VulkanDeviceManager {

    // Vulkan 1.4 API version encoding: (1 << 22) | (4 << 12) | 0 = 0x404000
    const val VULKAN_1_4_VERSION = 0x404000

    data class VulkanCapabilities(
        val isVulkan14Supported: Boolean,
        val isGooglePixelDevice: Boolean,
        val vulkanVersionHex: Int,
        val vulkanLevel: Int,
        val vulkanComputeSupported: Boolean,
        val versionString: String,
        val summary: String
    )

    fun checkCapabilities(context: Context): VulkanCapabilities {
        val pm = context.packageManager
        var versionHex = 0
        var level = 0
        var compute = false

        val features = try {
            pm.systemAvailableFeatures ?: emptyArray()
        } catch (_: Throwable) {
            emptyArray()
        }

        for (f in features) {
            when (f.name) {
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION -> {
                    versionHex = maxOf(versionHex, f.version)
                }
                PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL -> {
                    level = maxOf(level, f.version)
                }
                PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE -> {
                    compute = true
                }
            }
        }

        val isVulkan14 = versionHex >= VULKAN_1_4_VERSION
        val isPixel = Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            Build.BRAND.equals("google", ignoreCase = true)

        val major = (versionHex shr 22) and 0x7F
        val minor = (versionHex shr 12) and 0x3FF
        val patch = versionHex and 0xFFF
        val verStr = if (versionHex > 0) "$major.$minor.$patch" else "不支援"

        val summary = if (isVulkan14) {
            "Vulkan $verStr (1.4 Core, Level $level, Compute: $compute)"
        } else {
            "未達 Vulkan 1.4 最低硬體要求 (需 1.4.0，目前: $verStr)"
        }

        return VulkanCapabilities(
            isVulkan14Supported = isVulkan14,
            isGooglePixelDevice = isPixel,
            vulkanVersionHex = versionHex,
            vulkanLevel = level,
            vulkanComputeSupported = compute,
            versionString = verStr,
            summary = summary
        )
    }
}
