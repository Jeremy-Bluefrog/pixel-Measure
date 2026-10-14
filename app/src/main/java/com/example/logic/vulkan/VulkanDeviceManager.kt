package com.example.logic.vulkan

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Manages Vulkan hardware capability queries and enforces Vulkan 1.1+ compliance.
 * Pure Vulkan architecture requires Vulkan 1.1 (0x401000) or higher.
 */
object VulkanDeviceManager {

    // Vulkan 1.1 API version (Major 1, Minor 1): 0x401000
    const val VULKAN_1_1_VERSION = 0x401000

    data class VulkanCapabilities(
        val isVulkan11Supported: Boolean,
        val vulkanVersionHex: Int,
        val vulkanLevel: Int,
        val vulkanComputeSupported: Boolean,
        val versionString: String,
        val summary: String
    )

    fun checkCapabilities(context: Context): VulkanCapabilities {
        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        var versionHex = 0
        var level = 0
        var compute = false

        if (hasVulkan) {
            val features = pm.systemAvailableFeatures
            for (f in features) {
                when (f.name) {
                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION -> {
                        versionHex = f.version
                    }
                    PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL -> {
                        level = f.version
                    }
                    PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE -> {
                        compute = true
                    }
                }
            }
        }

        val isSupported = versionHex >= VULKAN_1_1_VERSION
        val major = (versionHex shr 22) and 0x7F
        val minor = (versionHex shr 12) and 0x3FF
        val patch = versionHex and 0xFFF
        val verStr = if (versionHex > 0) "$major.$minor.$patch" else "不支援"
        val summary = if (isSupported) {
            "Vulkan $verStr (Level $level, Compute: $compute)"
        } else {
            "裝置未滿足 Vulkan 1.1 規格 (目前: $verStr)"
        }

        return VulkanCapabilities(
            isVulkan11Supported = isSupported,
            vulkanVersionHex = versionHex,
            vulkanLevel = level,
            vulkanComputeSupported = compute,
            versionString = verStr,
            summary = summary
        )
    }
}
