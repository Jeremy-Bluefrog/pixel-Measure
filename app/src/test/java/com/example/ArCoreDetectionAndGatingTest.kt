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
    fun `MeasureViewModel gates measurement and blocks point capture when com_google_ar_core is not installed`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)

        val viewModel = MeasureViewModel(app)
        assertFalse(viewModel.isArCoreInstalled())
        assertFalse(viewModel.isMeasurementAvailable.value)

        viewModel.requestHitTest(540f, 1200f)

        assertTrue(viewModel.capturedPoints.isEmpty())
        assertFalse(viewModel.isMeasurementAvailable.value)
    }
}
