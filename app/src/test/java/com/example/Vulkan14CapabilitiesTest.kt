package com.example

import android.app.Application
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.example.logic.ThermalTier
import com.example.logic.vulkan.PixelGpuArchitecture
import com.example.logic.vulkan.PixelGpuHintSession
import com.example.logic.vulkan.PixelGpuOptimizer
import com.example.logic.vulkan.PixelTensorGeneration
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
        assertTrue(pipeline.tbdrRenderPassConfig.colorLoadOpDontCare)
        assertTrue(pipeline.tbdrRenderPassConfig.depthStoreOpDontCare)
        assertTrue(pipeline.tbdrRenderPassConfig.preTransformActive)

        pipeline.updateGpuPolicy(ThermalTier.WARM, 60)
        assertEquals(12, pipeline.currentRenderPolicy.agslBlurTapCount)
        assertEquals(0.55f, pipeline.currentRenderPolicy.backdropDownsampleScale, 0.001f)

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
            boxCorners = null,
            displayRotation = Surface.ROTATION_90
        ) {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        pipeline.release()
    }

    @Test
    fun `PixelGpuOptimizer detects Mali on Pixel 8_9 and PowerVR DXT on Pixel 10`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val pixel9Profile = PixelGpuOptimizer.detectGpuProfile(
            context = app,
            model = "Pixel 9 Pro",
            hardware = "caiman",
            socModel = "Tensor G4",
            manufacturer = "Google",
            brand = "google"
        )
        assertTrue(pixel9Profile.isGooglePixel)
        assertEquals(PixelTensorGeneration.TENSOR_G3_G4, pixel9Profile.tensorGeneration)
        assertEquals(PixelGpuArchitecture.MALI_TBDR, pixel9Profile.architecture)
        assertEquals(4096, pixel9Profile.maxSafeScratchTextureDim)
        assertTrue(pixel9Profile.supportsFp16Alu)

        val pixel10Profile = PixelGpuOptimizer.detectGpuProfile(
            context = app,
            model = "Pixel 10 Pro",
            hardware = "laguna",
            socModel = "Tensor G5",
            manufacturer = "Google",
            brand = "google"
        )
        assertTrue(pixel10Profile.isGooglePixel)
        assertEquals(PixelTensorGeneration.TENSOR_G5_OR_NEWER, pixel10Profile.tensorGeneration)
        assertEquals(PixelGpuArchitecture.POWERVR_TBDR, pixel10Profile.architecture)
        assertEquals(2048, pixel10Profile.maxSafeScratchTextureDim)
        assertEquals(32, pixel10Profile.recommendedTileSizePx)
    }

    @Test
    fun `PixelGpuOptimizer scales AGSL taps, downsample ratio, and Gaussian tier across thermal & FPS tiers`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val profile = PixelGpuOptimizer.detectGpuProfile(
            context = app,
            model = "Pixel 9",
            socModel = "Tensor G4",
            manufacturer = "Google",
            brand = "google"
        )

        val optimal60 = PixelGpuOptimizer.evaluateRenderPolicy(profile, ThermalTier.OPTIMAL, 60)
        assertEquals(16, optimal60.agslBlurTapCount)
        assertEquals(0.67f, optimal60.backdropDownsampleScale, 0.001f)
        assertEquals(0.50f, optimal60.tier2DownsampleMultiplier, 0.001f)
        assertEquals(48, optimal60.maxCircleSegments)
        assertTrue(optimal60.enableSpatialGridPass)
        assertTrue(optimal60.enableDeepGaussianTier2)
        assertTrue(optimal60.estimatedTbdrBandwidthSavedPercent() >= 70)

        val optimal120 = PixelGpuOptimizer.evaluateRenderPolicy(profile, ThermalTier.OPTIMAL, 120)
        assertEquals(12, optimal120.agslBlurTapCount)
        assertEquals(0.60f, optimal120.backdropDownsampleScale, 0.001f)
        assertEquals(40, optimal120.maxCircleSegments)
        assertTrue(optimal120.enableDeepGaussianTier2)

        val warm = PixelGpuOptimizer.evaluateRenderPolicy(profile, ThermalTier.WARM, 60)
        assertEquals(12, warm.agslBlurTapCount)
        assertEquals(0.55f, warm.backdropDownsampleScale, 0.001f)
        assertEquals(32, warm.maxCircleSegments)
        assertTrue(warm.enableDeepGaussianTier2)

        val throttled = PixelGpuOptimizer.evaluateRenderPolicy(profile, ThermalTier.THROTTLED, 30)
        assertEquals(8, throttled.agslBlurTapCount)
        assertEquals(0.50f, throttled.backdropDownsampleScale, 0.001f)
        assertEquals(24, throttled.maxCircleSegments)
        assertFalse(throttled.enableSpatialGridPass)
        assertFalse(throttled.enableDeepGaussianTier2)
        assertTrue(throttled.estimatedTbdrBandwidthSavedPercent() >= 85)

        // ADPF duration target reserves ~4% headroom below VSync period
        assertEquals(8_000_000L, PixelGpuHintSession.targetFpsToDurationNs(120))

        // ADPF EMA smoothing absorbs transient single-frame GC spikes without DVFS oscillation
        val target120Ns = PixelGpuHintSession.targetFpsToDurationNs(120)
        val firstSmoothed = PixelGpuHintSession.smoothWorkDurationNs(0L, 4_000_000L, target120Ns)
        assertEquals(4_000_000L, firstSmoothed)
        val spikeSmoothed = PixelGpuHintSession.smoothWorkDurationNs(firstSmoothed, 90_000_000L, target120Ns)
        assertTrue(spikeSmoothed in 8_000_000L..15_000_000L)
    }

    @Test
    fun `VulkanArGraphicsPipeline batches 3D bounding box edges and adapts grid LOD by thermal tier`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pipeline = VulkanArGraphicsPipeline(app)

        val view = FloatArray(16)
        val proj = FloatArray(16)
        VulkanMatrix4.setIdentity(view, 0)
        VulkanMatrix4.perspective(proj, 0, 60f, 0.5625f, 0.05f, 50f)

        val boxCorners = listOf(
            Point3D(0.0, 0.0, -1.0), Point3D(1.0, 0.0, -1.0),
            Point3D(1.0, 0.0, -2.0), Point3D(0.0, 0.0, -2.0),
            Point3D(0.0, 1.0, -1.0), Point3D(1.0, 1.0, -1.0),
            Point3D(1.0, 1.0, -2.0), Point3D(0.0, 1.0, -2.0)
        )

        pipeline.updateGpuPolicy(ThermalTier.OPTIMAL, 60)
        pipeline.recordAndExecuteDrawPass(
            viewMatrix = view,
            projectionMatrix = proj,
            capturedPoints = emptyList(),
            liveTarget = null,
            boxCorners = boxCorners,
            displayRotation = Surface.ROTATION_0
        )
        // 12 edges * 2 vertices = 24 vertices submitted cleanly
        assertEquals(24, pipeline.lastSubmittedLineVertices)
        val optimalGridVerts = pipeline.lastSubmittedGridVertices
        assertTrue(optimalGridVerts > 0)

        pipeline.updateGpuPolicy(ThermalTier.WARM, 60)
        pipeline.recordAndExecuteDrawPass(view, proj, emptyList(), null, boxCorners)
        val warmGridVerts = pipeline.lastSubmittedGridVertices
        assertEquals(optimalGridVerts / 2, warmGridVerts)

        pipeline.updateGpuPolicy(ThermalTier.THROTTLED, 30)
        pipeline.recordAndExecuteDrawPass(view, proj, emptyList(), null, boxCorners)
        assertEquals(0, pipeline.lastSubmittedGridVertices)

        val telemetry = pipeline.getTelemetrySnapshot()
        assertEquals(ThermalTier.THROTTLED, telemetry.thermalTier)
        assertEquals(24, telemetry.activeCircleLodSegments)
        assertTrue(telemetry.renderedFrames >= 3L)
        assertTrue(telemetry.tbdrBandwidthSavedPercent >= 85)

        val hbManager = com.example.logic.vulkan.VulkanHardwareBufferManager(1280, 720)
        assertEquals(1280, hbManager.getStats().width)
        assertFalse(hbManager.getStats().isReleased)
        hbManager.release()
        assertTrue(hbManager.getStats().isReleased)

        pipeline.release()
    }

    @Test
    fun `VulkanMatrix4 applyDisplayPreRotation transforms coordinates cleanly across all orientations`() {
        val identity = FloatArray(16)
        val rotated = FloatArray(16)
        val vIn = floatArrayOf(1f, 0f, 0f, 1f)
        val vOut = FloatArray(4)

        VulkanMatrix4.setIdentity(identity, 0)

        // 0 deg: unchanged
        VulkanMatrix4.applyDisplayPreRotation(rotated, identity, Surface.ROTATION_0)
        VulkanMatrix4.multiplyMV(vOut, 0, rotated, 0, vIn, 0)
        assertEquals(1f, vOut[0], 1e-4f)
        assertEquals(0f, vOut[1], 1e-4f)

        // 90 deg: -90 deg Z rotation maps (1, 0) -> (0, -1)
        VulkanMatrix4.applyDisplayPreRotation(rotated, identity, Surface.ROTATION_90)
        VulkanMatrix4.multiplyMV(vOut, 0, rotated, 0, vIn, 0)
        assertEquals(0f, vOut[0], 1e-4f)
        assertEquals(-1f, vOut[1], 1e-4f)

        // 180 deg: maps (1, 0) -> (-1, 0)
        VulkanMatrix4.applyDisplayPreRotation(rotated, identity, Surface.ROTATION_180)
        VulkanMatrix4.multiplyMV(vOut, 0, rotated, 0, vIn, 0)
        assertEquals(-1f, vOut[0], 1e-4f)
        assertEquals(0f, vOut[1], 1e-4f)

        // 270 deg: +90 deg Z rotation maps (1, 0) -> (0, 1)
        VulkanMatrix4.applyDisplayPreRotation(rotated, identity, Surface.ROTATION_270)
        VulkanMatrix4.multiplyMV(vOut, 0, rotated, 0, vIn, 0)
        assertEquals(0f, vOut[0], 1e-4f)
        assertEquals(1f, vOut[1], 1e-4f)
    }
}
