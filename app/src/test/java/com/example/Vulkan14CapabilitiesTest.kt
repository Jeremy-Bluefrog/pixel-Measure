package com.example

import android.app.Application
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.logic.vulkan.VulkanArGraphicsPipeline
import com.example.logic.vulkan.VulkanDeviceManager
import com.example.logic.vulkan.VulkanMatrix4
import com.example.ui.viewmodel.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Vulkan14CapabilitiesTest {

    @Test
    fun `VulkanDeviceManager encodes and detects Vulkan 1_4 hardware feature cleanly`() {
        assertEquals(0x404000, VulkanDeviceManager.VULKAN_1_4_VERSION)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)

        val vkVersionFeature = FeatureInfo().apply {
            name = PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
            version = VulkanDeviceManager.VULKAN_1_4_VERSION
        }
        val vkLevelFeature = FeatureInfo().apply {
            name = PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
            version = 1
        }
        val vkComputeFeature = FeatureInfo().apply {
            name = PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE
            version = 0
        }
        shadowPm.setSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, true)
        shadowPm.addSystemAvailableFeature(vkVersionFeature)
        shadowPm.addSystemAvailableFeature(vkLevelFeature)
        shadowPm.addSystemAvailableFeature(vkComputeFeature)

        val caps = VulkanDeviceManager.checkCapabilities(app)
        assertTrue(caps.isVulkan14Supported)
        assertTrue(caps.vulkanComputeSupported)
        assertEquals(VulkanDeviceManager.VULKAN_1_4_VERSION, caps.vulkanVersionHex)
        assertEquals("1.4.0", caps.versionString)
        assertTrue(caps.summary.contains("1.4 Core"))
    }

    @Test
    fun `VulkanDeviceManager strictly rejects legacy Vulkan 1_1 and 1_3 without backward compatibility`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)

        val vk13Feature = FeatureInfo().apply {
            name = PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
            version = 0x403000 // Vulkan 1.3
        }
        shadowPm.setSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, true)
        shadowPm.addSystemAvailableFeature(vk13Feature)

        val caps = VulkanDeviceManager.checkCapabilities(app)
        assertFalse(caps.isVulkan14Supported)
        assertEquals("1.3.0", caps.versionString)
        assertTrue(caps.summary.contains("未達 Vulkan 1.4"))
    }

    @Test
    fun `VulkanArGraphicsPipeline targets Vulkan 1_4 and executes double-buffered draw pass`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pipeline = VulkanArGraphicsPipeline(app)

        assertTrue(pipeline.deviceInfo.apiVersion.contains("Vulkan 1.4"))
        assertTrue(pipeline.deviceInfo.driverVersion.contains("v1.4"))

        val view = FloatArray(16)
        val proj = FloatArray(16)
        VulkanMatrix4.setIdentity(view, 0)
        VulkanMatrix4.perspective(proj, 0, 60f, 0.5625f, 0.05f, 50f)

        var callbackInvoked = false
        pipeline.recordAndExecuteDrawPass(
            viewMatrix = view,
            projectionMatrix = proj,
            capturedPoints = listOf(Point3D(0.0, 0.0, -1.0), Point3D(1.0, 0.0, -1.0)),
            liveTarget = Point3D(1.2, 0.0, -1.0),
            boxCorners = null
        ) {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        pipeline.release()
    }
}
