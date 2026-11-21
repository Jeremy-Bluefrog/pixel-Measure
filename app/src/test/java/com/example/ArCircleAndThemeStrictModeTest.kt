package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.logic.ar.ArMath
import com.example.logic.vulkan.VulkanMatrix4
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArCircleAndThemeStrictModeTest {

    @get:Rule
    val vmRule = ManagedViewModelRule()

    @Test
    fun `app_name string resource matches Measure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Measure", context.getString(R.string.app_name))
    }

    @Test
    fun `ArMath fitCircle2Points and fitCircle3Points accurately compute 3D circles`() {
        val c2 = ArMath.fitCircle2Points(
            Point3D(0.0, 0.0, -1.0),
            Point3D(1.0, 0.0, -1.0)
        )
        assertNotNull(c2)
        assertEquals(0.5, c2!!.radius, 1e-4)
        assertEquals(1.0, c2.diameter, 1e-4)
        assertEquals(PI, c2.circumference, 1e-4)
        assertEquals(0.5, c2.center.x, 1e-4)

        // Tilted 3D plane circle with radius 0.8m around (1.0, 0.5, -2.0)
        val p1 = Point3D(1.8, 0.5, -2.0)
        val p2 = Point3D(1.0, 1.3, -2.0)
        val p3 = Point3D(0.2, 0.5, -2.0)
        val c3 = ArMath.fitCircle3Points(p1, p2, p3)
        assertNotNull(c3)
        assertEquals(0.8, c3!!.radius, 1e-4)
        assertEquals(1.6, c3.diameter, 1e-4)
        assertEquals(1.0, c3.center.x, 1e-4)
        assertEquals(0.5, c3.center.y, 1e-4)
        assertEquals(-2.0, c3.center.z, 1e-4)
    }

    @Test
    fun `ArMath extractSurfaceNormalFromQuaternion and basis project tilted 3D circle cleanly`() {
        val normal = FloatArray(3)
        // Identity quaternion -> +Y floor normal (0, 1, 0)
        ArMath.extractSurfaceNormalFromQuaternion(0f, 0f, 0f, 1f, normal)
        assertEquals(0f, normal[0], 1e-4f)
        assertEquals(1f, normal[1], 1e-4f)
        assertEquals(0f, normal[2], 1e-4f)

        // 90-degree rotation around X -> vertical wall normal (0, 0, +1)
        val halfAngle = (PI / 4.0)
        ArMath.extractSurfaceNormalFromQuaternion(
            sin(halfAngle).toFloat(),
            0f,
            0f,
            cos(halfAngle).toFloat(),
            normal
        )
        assertEquals(0f, normal[0], 1e-4f)
        assertEquals(0f, normal[1], 1e-4f)
        assertEquals(1f, normal[2], 1e-4f)

        val basis = DoubleArray(9)
        assertTrue(ArMath.buildOrthonormalBasis(normal[0].toDouble(), normal[1].toDouble(), normal[2].toDouble(), basis))

        // N, U, V must be unit length and mutually orthogonal
        val nLen = sqrt(basis[0] * basis[0] + basis[1] * basis[1] + basis[2] * basis[2])
        val uLen = sqrt(basis[3] * basis[3] + basis[4] * basis[4] + basis[5] * basis[5])
        val vLen = sqrt(basis[6] * basis[6] + basis[7] * basis[7] + basis[8] * basis[8])
        assertEquals(1.0, nLen, 1e-6)
        assertEquals(1.0, uLen, 1e-6)
        assertEquals(1.0, vLen, 1e-6)
        assertEquals(0.0, basis[0] * basis[3] + basis[1] * basis[4] + basis[2] * basis[5], 1e-6)

        val wx = DoubleArray(24)
        val wy = DoubleArray(24)
        val wz = DoubleArray(24)
        val count = ArMath.sampleCircle3DWorldCoords(0.0, 0.0, -2.0, 0.4, basis, 24, wx, wy, wz)
        assertEquals(24, count)
        for (i in 0 until count) {
            assertEquals(-2.0, wz[i], 1e-5)
            val r = sqrt(wx[i] * wx[i] + wy[i] * wy[i])
            assertEquals(0.4, r, 1e-5)
        }

        val view = FloatArray(16)
        val proj = FloatArray(16)
        VulkanMatrix4.setIdentity(view, 0)
        VulkanMatrix4.perspective(proj, 0, 60f, 1080f / 2400f, 0.05f, 50f)

        val sx = FloatArray(24)
        val sy = FloatArray(24)
        val scratch = FloatArray(2)
        val projCount = ArMath.projectCircle3DToScreen(
            0.0, 0.0, -2.0, 0.4, basis, 24, view, proj, 1080, 2400, sx, sy, scratch
        )
        assertEquals(24, projCount)
        assertTrue(sx.all { it.isFinite() && abs(it - 540f) < 600f })
        assertTrue(sy.all { it.isFinite() && abs(it - 1200f) < 600f })
    }

    @Test
    fun `MeasureViewModel themeMode updates StateFlow and persists choice`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)

        vm.setThemeMode("LIGHT")
        assertEquals("LIGHT", vm.themeMode.value)

        vm.setThemeMode("DARK")
        assertEquals("DARK", vm.themeMode.value)

        vm.setThemeMode("SYSTEM")
        assertEquals("SYSTEM", vm.themeMode.value)
    }

    @Test
    fun `MeasureViewModel strictly gates hit tests to native ARCore when not in explicit simulation mode`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val shadowPm = Shadows.shadowOf(app.packageManager)
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = "com.google.ar.core"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.google.ar.core"
                    enabled = true
                }
            }
        )

        val vm = vmRule.create(app)
        // Even with com.google.ar.core installed, ARCore Session is null in Robolectric -> must NOT capture fallback points
        assertFalse(vm.isCameraSimulationActive.value)
        vm.requestHitTest(540f, 1200f)

        assertTrue(vm.getCapturedPointsSnapshot().isEmpty())
        assertFalse(vm.isCameraSimulationActive.value)
        assertFalse(vm.isMeasurementAvailable.value)
    }

    @Test
    fun `MeasureViewModel calculateCircle handles 2-point and 3-point modes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = Shadows.shadowOf(app.packageManager)
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = "com.google.ar.core"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.google.ar.core"
                    enabled = true
                }
            }
        )
        val vm = vmRule.create(app)
        vm.setCameraSubMode(4)
        vm.setCameraSimulationActive(true)
        vm.clearActivePoints()

        vm.requestHitTest(350f, 1000f)
        Thread.sleep(210)
        vm.requestHitTest(750f, 1000f)
        val c2 = vm.calculateCircle()
        assertNotNull(c2)
        assertTrue(c2!!.diameter > 0.0)

        Thread.sleep(210)
        vm.requestHitTest(550f, 800f)
        val c3 = vm.calculateCircle()
        assertNotNull(c3)
        assertEquals(3, vm.getCapturedPointsSnapshot().size)
        assertTrue(c3!!.diameter > 0.0)
    }
}
