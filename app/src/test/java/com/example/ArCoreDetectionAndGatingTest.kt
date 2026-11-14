package com.example

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.logic.ar.ArCoreEngineStatus
import com.example.logic.ar.ModernArEngine
import com.example.ui.viewmodel.MeasureViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

import android.app.Activity
import android.view.Surface
import com.example.logic.camera.HighSpeedCamera2Manager
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArCoreDetectionAndGatingTest {

    @Test
    fun `isArCorePackageInstalled returns false when com_google_ar_core is missing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(ModernArEngine.isArCorePackageInstalled(app))
    }

    @Test
    fun `isArCorePackageInstalled reflects installed and enabled state of com_google_ar_core`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)

        val enabledInfo = PackageInfo().apply {
            packageName = "com.google.ar.core"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.ar.core"
                enabled = true
            }
        }
        shadowPm.installPackage(enabledInfo)
        assertTrue(ModernArEngine.isArCorePackageInstalled(app))

        val disabledInfo = PackageInfo().apply {
            packageName = "com.google.ar.core"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.google.ar.core"
                enabled = false
            }
        }
        shadowPm.installPackage(disabledInfo)
        assertFalse(ModernArEngine.isArCorePackageInstalled(app))
    }

    @Test
    fun `isPlayStoreInstallServiceAvailable returns false when vending or BIND_INSTALL_SERVICE is missing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(ModernArEngine.isPlayStoreInstallServiceAvailable(app))

        val shadowPm = Shadows.shadowOf(app.packageManager)
        val vendingOnly = PackageInfo().apply {
            packageName = "com.android.vending"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.android.vending"
                enabled = true
            }
        }
        shadowPm.installPackage(vendingOnly)
        // Package exists without exported BIND_INSTALL_SERVICE service -> still false
        assertFalse(ModernArEngine.isPlayStoreInstallServiceAvailable(app))
    }

    @Test
    fun `isPlayStoreInstallServiceAvailable returns true when vending exports BIND_INSTALL_SERVICE`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)

        val appInfo = ApplicationInfo().apply {
            packageName = "com.android.vending"
            enabled = true
        }
        val component = android.content.ComponentName("com.android.vending", "com.google.android.finsky.installapi.PlayInstallService")
        val serviceInfo = android.content.pm.ServiceInfo().apply {
            packageName = component.packageName
            name = component.className
            applicationInfo = appInfo
            enabled = true
            exported = true
        }
        val vendingPackage = PackageInfo().apply {
            packageName = "com.android.vending"
            applicationInfo = appInfo
            services = arrayOf(serviceInfo)
        }
        shadowPm.installPackage(vendingPackage)
        val intent = android.content.Intent("com.google.android.play.core.install.BIND_INSTALL_SERVICE").apply {
            setPackage("com.android.vending")
        }
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            this.serviceInfo = serviceInfo
        }
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(intent, resolveInfo)

        assertTrue(ModernArEngine.isPlayStoreInstallServiceAvailable(app))
    }

    @Test
    fun `checkAndCreateSession sets NEEDS_INSTALLATION when com_google_ar_core is missing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)

        val engine = ModernArEngine(app)
        val session = engine.checkAndCreateSession(activity = null)

        assertNull(session)
        assertFalse(engine.isSupported)
        assertEquals(ArCoreEngineStatus.NEEDS_INSTALLATION, engine.arCoreStatus.value)
        assertTrue(engine.arCoreStatusMessage.value.contains("com.google.ar.core"))
    }

    @Test
    fun `ensureInstallReadyOnMainThread reports NEEDS_INSTALLATION when com_google_ar_core is missing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val engine = ModernArEngine(app)

        val ready = engine.ensureInstallReadyOnMainThread(activity, explicitUserAction = false)

        assertFalse(ready)
        assertFalse(engine.isSupported)
        assertEquals(ArCoreEngineStatus.NEEDS_INSTALLATION, engine.arCoreStatus.value)
    }

    @Test
    fun `warmUpAvailability executes safely without crashing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = ModernArEngine(app)
        engine.warmUpAvailability()
        assertFalse(engine.isSessionResumed)
    }

    @Test
    fun `setDisplayGeometry validates dimensions and tracks rotation changes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = ModernArEngine(app)

        engine.setDisplayGeometry(Surface.ROTATION_0, 1080, 2400)
        assertEquals(1080, engine.viewportWidth)
        assertEquals(2400, engine.viewportHeight)

        // Invalid dimensions must be ignored
        engine.setDisplayGeometry(Surface.ROTATION_90, 0, -100)
        assertEquals(1080, engine.viewportWidth)
        assertEquals(2400, engine.viewportHeight)

        // Valid 180-degree rotation with identical size should remain valid
        engine.setDisplayGeometry(Surface.ROTATION_180, 1080, 2400)
        assertEquals(1080, engine.viewportWidth)
        assertEquals(2400, engine.viewportHeight)
    }

    @Test
    fun `pause and destroy are idempotent without active native session`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = ModernArEngine(app)

        engine.pause()
        engine.pause()
        engine.destroy()
        engine.destroy()

        assertNull(engine.session)
        assertFalse(engine.isSessionResumed)
        assertFalse(engine.isTorchActive)
        assertFalse(engine.isExposeHardwareBufferActive)
    }

    @Test
    fun `HighSpeedCamera2Manager exposes SharedCamera gating safely before open`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = HighSpeedCamera2Manager(app)

        assertFalse(manager.isSharedCameraArActive())
        assertFalse(manager.isCameraOpenWithoutSharedCamera())
        manager.closeCamera()
        assertFalse(manager.isCameraOpenWithoutSharedCamera())
    }

    @Test
    fun `MeasureViewModel gates measurement and blocks point capture when com_google_ar_core is not installed`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)

        val viewModel = MeasureViewModel(app)
        assertFalse(viewModel.isArCoreInstalled())
        assertFalse(viewModel.isMeasurementAvailable.value)

        viewModel.warmUpArCoreAvailability()
        viewModel.requestHitTest(540f, 1200f)

        assertTrue(viewModel.capturedPoints.isEmpty())
        assertFalse(viewModel.isMeasurementAvailable.value)
    }
}
