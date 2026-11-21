package com.example

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.logic.ModernHapticFeedback
import com.example.logic.PixelPerformanceMonitor
import com.example.logic.ThermalTier
import com.example.logic.sensor.SensorFusionCorrectionEngine
import com.example.logic.vulkan.VulkanArSceneView
import com.example.logic.vulkan.VulkanFrameSyncManager
import com.example.logic.vulkan.VulkanRenderState
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReliabilityAndErrorReductionTest {

    @get:Rule
    val vmRule = ManagedViewModelRule()

    @Test
    fun `VulkanFrameSyncManager watchdog invokes callback safely outside lock without deadlock`() {
        val syncManager = VulkanFrameSyncManager(maxFramesInFlight = 2)
        val watchdogCount = AtomicInteger(0)
        syncManager.onWatchdogTriggered = {
            watchdogCount.incrementAndGet()
            // Calling reset() directly inside watchdog callback must not deadlock on internal lock
            syncManager.reset()
        }

        // Fill both slots without signaling completion
        val s0 = syncManager.acquireNextFrameSlot(timeoutMs = 10L)
        val s1 = syncManager.acquireNextFrameSlot(timeoutMs = 10L)
        assertEquals(0, s0)
        assertEquals(1, s1)

        // Trigger 3 consecutive timeouts on held slots to fire watchdog
        repeat(3) {
            val res = syncManager.acquireNextFrameSlot(timeoutMs = 2L)
            assertEquals(-1, res)
        }

        assertEquals(1, watchdogCount.get())

        // After reset() ran inside watchdog, acquiring slot 0 must succeed immediately
        val recoveredSlot = syncManager.acquireNextFrameSlot(timeoutMs = 10L)
        assertEquals(0, recoveredSlot)
    }

    @Test
    fun `SensorFusionCorrectionEngine handles concurrent fusion and barometer reset without exceptions`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = SensorFusionCorrectionEngine(app)
        val executor = Executors.newFixedThreadPool(3)
        val errors = AtomicInteger(0)
        val latch = CountDownLatch(3)

        executor.submit {
            try {
                var prev: Point3D? = null
                repeat(200) { idx ->
                    val raw = Point3D(0.1 * (idx % 5), 0.2, -1.5, isArPrecision = true)
                    val corrected = engine.correctPointWithSensorFusion(raw, prev)
                    if (corrected.x.isNaN() || corrected.y.isNaN() || corrected.z.isNaN()) {
                        errors.incrementAndGet()
                    }
                    prev = corrected
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                repeat(100) {
                    engine.resetBarometerBase()
                    engine.syncMultiSampleState()
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                val p1 = Point3D(0.0, 0.0, -1.0)
                val p2 = Point3D(0.0, 1.0, -1.0)
                repeat(200) {
                    val h = engine.correctVerticalHeightWithGravity(p1, p2)
                    if (h.isNaN() || h < 0.0) errors.incrementAndGet()
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(0, errors.get())
    }

    @Test
    fun `applyMeasurementProfile emits single consolidated toast instead of toast storm`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = vmRule.create(app)
        viewModel.setLanguage("zh-TW")

        val collected = mutableListOf<String>()
        val collectJob = launch {
            withTimeout(2000L) {
                viewModel.toastMessage.take(1).toList(collected)
            }
        }

        // Wait briefly for subscription to start
        kotlinx.coroutines.delay(50)
        viewModel.applyMeasurementProfile("PRO_PRECISION")
        collectJob.join()

        assertEquals(1, collected.size)
        assertTrue(collected.first().contains("專業工程極限精密"))
    }

    @Test
    fun `clearActivePoints and undo recover snapshot safely in simulation mode`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)
        val info = PackageInfo().apply {
            packageName = "com.google.ar.core"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.ar.core"
                enabled = true
            }
        }
        shadowPm.installPackage(info)

        val viewModel = vmRule.create(app)
        viewModel.setCameraSimulationActive(true)
        viewModel.requestHitTest(300f, 800f)
        assertEquals(1, viewModel.getCapturedPointsSnapshot().size)

        viewModel.clearActivePoints()
        assertTrue(viewModel.getCapturedPointsSnapshot().isEmpty())

        viewModel.undo()
        assertEquals(1, viewModel.getCapturedPointsSnapshot().size)
        assertEquals(0, viewModel.getCapturedPointsSnapshot().count { it.anchor != null })
    }

    @Test
    fun `openSystemLanguageSettings safely handles non-Activity context without crashing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = vmRule.create(app)

        // Should never throw AndroidRuntimeException / ActivityNotFoundException
        viewModel.openSystemLanguageSettings(app)
        assertFalse(app.packageName.isBlank())
    }

    @Test
    fun `auto-copy in auto-detect mode formats updated HEIGHT instead of stale DISTANCE and release clears liveAngleDegrees`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)
        val info = PackageInfo().apply {
            packageName = "com.google.ar.core"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.ar.core"
                enabled = true
            }
        }
        shadowPm.installPackage(info)

        val vm = vmRule.create(app)
        vm.setCameraSubMode(0) // Auto-detect
        vm.setSelectedUnit("cm")
        vm.setAntiJitterEnabled(false)
        vm.setAutoCopyOnComplete(true)
        vm.setCameraSimulationActive(true)
        vm.updateDisplayGeometry(0, 1000, 2000)

        // Tap 1 (high Y)
        vm.requestHitTest(500f, 100f)
        Thread.sleep(200)
        // Tap 2 (low Y, slight horizontal X offset so 3D distance != vertical height)
        vm.requestHitTest(560f, 1900f)

        assertEquals("HEIGHT", vm.autoDetectedType.value)
        val expectedHeightStr = vm.formatLength(vm.calculateVerticalHeight(), "cm")
        val staleDistStr = vm.formatLength(vm.calculateTotalDistance(), "cm")
        assertTrue(expectedHeightStr != staleDistStr)

        val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals(expectedHeightStr, copied)

        vm.releaseVulkanPipelineBuffers()
        assertEquals(null, vm.liveAngleDegrees.value)
    }

    @Test
    fun `ShareUtility handles titles with colons and slashes safely when exporting reports`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val record = com.example.data.model.MeasureRecord(
            id = 99,
            title = "門框/寬度 · 14:30",
            value = 1.25,
            unit = "m",
            type = "DISTANCE",
            videoPath = "content://media/external/video/media/99"
        )
        // Verify email & report export paths do not crash with special filename characters or content URIs
        com.example.logic.ShareUtility.shareImageReport(app, record)
        assertTrue(java.io.File(app.cacheDir, "MeasureReport_99.jpg").exists())
        val imageChooser = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, imageChooser?.action)

        com.example.logic.ShareUtility.shareViaEmail(app, record)
        val emailChooser = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, emailChooser?.action)
        val wrappedEmailIntent = emailChooser?.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        val attachedUri = wrappedEmailIntent?.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        assertEquals("content://media/external/video/media/99", attachedUri?.toString())

        com.example.logic.ShareUtility.sharePdfReport(app, record)
    }

    @Test
    fun `PixelPerformanceMonitor predictive thermal headroom steps down proactively before severe throttling`() {
        val optimal = PixelPerformanceMonitor.evaluateThermalPolicyWithHeadroom(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            thermalHeadroom = 0.72f,
            userHighFps = true,
            userHighDef = true,
            targetRefreshRate = 120
        )
        assertEquals(ThermalTier.OPTIMAL, optimal.tier)
        assertEquals(120, optimal.targetFps)
        assertTrue(optimal.allowDepth)
        assertTrue(optimal.allowHighRes)
        assertFalse(optimal.forceDisableTorch)

        // Proactive step-down at >= 0.85f even if OS thermalStatus still reports NONE
        val warmProactive = PixelPerformanceMonitor.evaluateThermalPolicyWithHeadroom(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            thermalHeadroom = 0.88f,
            userHighFps = true,
            userHighDef = true,
            targetRefreshRate = 120
        )
        assertEquals(ThermalTier.WARM, warmProactive.tier)
        assertEquals(60, warmProactive.targetFps)
        assertTrue(warmProactive.allowDepth)
        assertFalse(warmProactive.allowHighRes)
        assertFalse(warmProactive.forceDisableTorch)

        // Imminent / severe threshold at >= 1.00f forces emergency cool-down
        val throttledProactive = PixelPerformanceMonitor.evaluateThermalPolicyWithHeadroom(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            thermalHeadroom = 1.02f,
            userHighFps = true,
            userHighDef = true,
            targetRefreshRate = 120
        )
        assertEquals(ThermalTier.THROTTLED, throttledProactive.tier)
        assertEquals(30, throttledProactive.targetFps)
        assertFalse(throttledProactive.allowDepth)
        assertTrue(throttledProactive.forceDisableTorch)

        // NaN headroom falls back cleanly to thermalStatus
        val nanFallback = PixelPerformanceMonitor.evaluateThermalPolicyWithHeadroom(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            thermalHeadroom = Float.NaN,
            userHighFps = true,
            userHighDef = true,
            targetRefreshRate = 60
        )
        assertEquals(ThermalTier.OPTIMAL, nanFallback.tier)
        assertEquals(60, nanFallback.targetFps)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val monitor = PixelPerformanceMonitor(app)
        monitor.startMonitoring()
        monitor.pollThermalHeadroomIfDue(1_000L)
        monitor.stopMonitoring()
    }

    @Test
    fun `VulkanArSceneView handles rapid surface and pause destroy transitions safely without deadlock`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val sceneView = VulkanArSceneView(app)

        assertEquals(VulkanRenderState.UNINITIALIZED, sceneView.renderState)

        sceneView.surfaceCreated(sceneView.holder)
        assertEquals(VulkanRenderState.ACTIVE_RENDER_LOOP, sceneView.renderState)

        sceneView.surfaceChanged(sceneView.holder, android.graphics.PixelFormat.RGBA_8888, 1080, 2400)
        assertEquals(VulkanRenderState.ACTIVE_RENDER_LOOP, sceneView.renderState)

        sceneView.setTargetFrameRate(120f)
        sceneView.onPause()
        assertEquals(VulkanRenderState.SAFE_SUSPENDED, sceneView.renderState)

        // Rapid surfaceDestroyed + destroy + post-destroy onResume must not throw or re-enter active loop
        sceneView.surfaceDestroyed(sceneView.holder)
        assertEquals(VulkanRenderState.SAFE_SUSPENDED, sceneView.renderState)

        sceneView.destroy()
        assertEquals(VulkanRenderState.UNINITIALIZED, sceneView.renderState)

        sceneView.onResume()
        assertEquals(VulkanRenderState.UNINITIALIZED, sceneView.renderState)
    }

    @Test
    fun `ModernHapticFeedback enforces per-type IMU cooldown windows without blocking distinct events`() {
        ModernHapticFeedback.resetCooldowns()

        assertTrue(ModernHapticFeedback.shouldAllowHaptic(HapticType.SNAP, 1_000L))
        // Repeated SNAP within 160ms must be suppressed to avoid shaking the IMU
        assertFalse(ModernHapticFeedback.shouldAllowHaptic(HapticType.SNAP, 1_050L))
        assertFalse(ModernHapticFeedback.shouldAllowHaptic(HapticType.SNAP, 1_159L))
        // Distinct CLICK allowed independently once its gap elapses
        assertTrue(ModernHapticFeedback.shouldAllowHaptic(HapticType.CLICK, 1_050L))
        assertFalse(ModernHapticFeedback.shouldAllowHaptic(HapticType.CLICK, 1_070L))
        // Allowed once 160ms cooldown expires
        assertTrue(ModernHapticFeedback.shouldAllowHaptic(HapticType.SNAP, 1_160L))
    }

    @Test
    fun `MeasureViewModel bounds undo stack eviction cleanly over 25 sequential placements`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)
        val info = PackageInfo().apply {
            packageName = "com.google.ar.core"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.ar.core"
                enabled = true
            }
        }
        shadowPm.installPackage(info)

        val vm = vmRule.create(app)
        vm.setCameraSimulationActive(true)
        vm.updateDisplayGeometry(0, 1080, 2400)

        val prop = MeasureViewModel::class.java.getDeclaredField("lastTapTimestampMs").apply {
            isAccessible = true
        }
        repeat(25) { i ->
            prop.setLong(vm, 0L)
            vm.requestHitTest(400f + (i % 5) * 20f, 900f + (i % 4) * 20f)
        }
        assertEquals(25, vm.getCapturedPointsSnapshot().size)

        // Undo 20 times cleanly (matching bounded undo history)
        repeat(20) {
            vm.undo()
        }
        assertEquals(5, vm.getCapturedPointsSnapshot().size)
    }
}
