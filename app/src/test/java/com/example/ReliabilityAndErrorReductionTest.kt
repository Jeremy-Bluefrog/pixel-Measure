package com.example

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.logic.sensor.SensorFusionCorrectionEngine
import com.example.logic.vulkan.VulkanFrameSyncManager
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
        val viewModel = MeasureViewModel(app)

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

        val viewModel = MeasureViewModel(app)
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
        val viewModel = MeasureViewModel(app)

        // Should never throw AndroidRuntimeException / ActivityNotFoundException
        viewModel.openSystemLanguageSettings(app)
        assertFalse(app.packageName.isBlank())
    }
}
