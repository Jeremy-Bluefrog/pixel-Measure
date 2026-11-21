package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.logic.ar.ArKalmanFilter3D
import com.example.logic.ar.ArMath
import com.example.logic.sensor.SensorFusionCorrectionEngine
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArAccuracyFixTest {

    @get:Rule
    val vmRule = ManagedViewModelRule()

    @Test
    fun `ArKalmanFilter3D handles Z axis tracking identically to X and tiny dt safely`() {
        val filterX = ArKalmanFilter3D()
        val filterZ = ArKalmanFilter3D()

        var outX = Point3D(0.0, 0.0, 0.0)
        var outZ = Point3D(0.0, 0.0, 0.0)

        // Initialize at origin
        outX = filterX.update(Point3D(0.0, 0.0, 0.0), dt = 0.016f)
        outZ = filterZ.update(Point3D(0.0, 0.0, 0.0), dt = 0.016f)

        // Step along X vs Z symmetrically; fixed pZ covariance update must yield identical gains
        for (i in 1..20) {
            val v = i * 0.05
            outX = filterX.update(Point3D(v, 0.0, 0.0), dt = 0.016f)
            outZ = filterZ.update(Point3D(0.0, 0.0, v), dt = 0.016f)
            assertEquals("Z covariance/gain must match X symmetrically", outX.x, outZ.z, 1e-9)
        }

        // Edge case: tiny / zero / huge dt must never produce NaN or Inf
        val tinyOut = filterZ.update(Point3D(0.5, 0.5, 1.0), dt = 0.0f)
        val hugeOut = filterZ.update(Point3D(0.5, 0.5, 1.5), dt = 2.0f)
        assertFalse(tinyOut.x.isNaN() || tinyOut.y.isNaN() || tinyOut.z.isNaN())
        assertFalse(hugeOut.x.isNaN() || hugeOut.y.isNaN() || hugeOut.z.isNaN())
        assertFalse(tinyOut.z.isInfinite() || hugeOut.z.isInfinite())
    }

    @Test
    fun `SensorFusionCorrectionEngine avoids artificial warping and double scaling`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = SensorFusionCorrectionEngine(app)
        engine.userScaleCalibrationFactor = 1.08f
        engine.isOrthogonalSnapEnabled = true
        engine.isBarometerFusionEnabled = true
        engine.isProximityZeroContactEnabled = true
        engine.isStereoParallaxScaleEnabled = true

        // 1. Shallow 2.5 deg slope & non-axis azimuth must NOT be flattened or warped
        val start = Point3D(0.0, 0.0, -1.0)
        val end = Point3D(0.95, 0.044, -0.68)
        val aligned = engine.correctOrthogonalAlignment(start, end)
        assertEquals(end.x, aligned.x, 1e-9)
        assertEquals(end.y, aligned.y, 1e-9)
        assertEquals(end.z, aligned.z, 1e-9)

        // 2. Vertical height returns exact gravity-aligned ARCore delta without double calibration
        val base = Point3D(0.2, -0.5, -1.5)
        val top = Point3D(0.25, 0.75, -1.52)
        assertEquals(1.25, engine.correctVerticalHeightWithGravity(base, top), 1e-9)

        // 3. Distance correction preserves metric ARCore value without synthetic drift or double calibration
        assertEquals(2.345, engine.correctDistanceWithStereoParallax(2.345), 1e-9)

        // 4. Zero-contact offset preserves optical hit surface point
        val hit = Point3D(0.12, -0.34, -0.89)
        val offsetHit = engine.applyProximityZeroContactOffset(hit)
        assertEquals(hit, offsetHit)
    }

    @Test
    fun `SensorFusionCorrectionEngine flushes burst window during panning for zero drag`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = SensorFusionCorrectionEngine(app)

        // Hold stationary at (0, 0, -1)
        var prev: Point3D? = null
        val stationary = Point3D(0.0, 0.0, -1.0, isArPrecision = true)
        repeat(8) {
            prev = engine.correctPointWithSensorFusion(stationary, prev)
        }

        // Rapidly pan 6 cm in X across successive frames; output should track close to target without trailing behind
        val panned = Point3D(0.06, 0.0, -1.0, isArPrecision = true)
        val tracked1 = engine.correctPointWithSensorFusion(panned, prev)
        val tracked2 = engine.correctPointWithSensorFusion(panned, tracked1)
        assertTrue("Panning target should rapidly converge near 0.06m (was ${tracked2.x})", tracked2.x > 0.05)
    }

    @Test
    fun `MeasureViewModel applies scaleCalibrationFactor strictly once across all geometry types`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)
        val factor = 1.05f
        vm.setScaleCalibrationFactor(factor)
        val f = factor.toDouble()

        vm.clearActivePoints()
        // 3x4 rectangle on Z=-2 plane -> edges 3m & 4m, area 12m², height difference along Y = 4m
        vm.capturedPoints.add(Point3D(0.0, 0.0, -2.0, label = "A"))
        vm.capturedPoints.add(Point3D(3.0, 0.0, -2.0, label = "B"))
        vm.capturedPoints.add(Point3D(3.0, 4.0, -2.0, label = "C"))
        vm.capturedPoints.add(Point3D(0.0, 4.0, -2.0, label = "D"))
        // Sync immutable snapshot cache
        vm.updatePointLabel(0, "A")

        // Distance (paired segments: A-B = 3m, C-D = 3m -> raw = 6m)
        assertEquals(6.0 * f, vm.calculateTotalDistance(), 1e-4)

        // Area (raw 3x4 = 12 m² -> scaled once by f²)
        assertEquals(12.0 * f * f, vm.calculatePolygonArea(), 1e-4)

        // Vertical height (first A.y=0, last D.y=4 -> raw = 4m)
        assertEquals(4.0 * f, vm.calculateVerticalHeight(), 1e-4)

        // Bounding box (lengthX=3, heightY=4, widthZ=0 -> scaled once by f)
        val box = vm.calculateBoundingBox()
        assertEquals(3.0 * f, box.lengthX, 1e-4)
        assertEquals(4.0 * f, box.heightY, 1e-4)

        // Circle from first 3 points of 3-4-5 right triangle -> circumradius = 2.5m, diameter = 5.0m
        val circle = vm.calculateCircle()
        assertNotNull(circle)
        assertEquals(2.5 * f, circle!!.radius, 1e-4)
        assertEquals(5.0 * f, circle.diameter, 1e-4)
        assertEquals(Math.PI * 2.5 * 2.5 * f * f, circle.area, 1e-4)
    }

    @Test
    fun `ArMath projectWorldToScreenInPlace respects horizontal mirroring`() {
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        VulkanMatrix4.setIdentity(viewMatrix, 0)
        VulkanMatrix4.perspective(projMatrix, 0, 60.0f, 1.0f, 0.1f, 100.0f)

        val rightPoint = Point3D(0.4, 0.0, -2.0, isArPrecision = true)
        val outNormal = FloatArray(2)
        val outMirrored = FloatArray(2)

        assertTrue(
            ArMath.projectWorldToScreenInPlace(
                worldPoint = rightPoint,
                viewMatrix = viewMatrix,
                projectionMatrix = projMatrix,
                screenWidth = 1080,
                screenHeight = 1920,
                outScreenPos = outNormal,
                isHorizontallyMirrored = false
            )
        )
        assertTrue(
            ArMath.projectWorldToScreenInPlace(
                worldPoint = rightPoint,
                viewMatrix = viewMatrix,
                projectionMatrix = projMatrix,
                screenWidth = 1080,
                screenHeight = 1920,
                outScreenPos = outMirrored,
                isHorizontallyMirrored = true
            )
        )

        assertEquals(1080f - outNormal[0], outMirrored[0], 0.5f)
        assertEquals(outNormal[1], outMirrored[1], 0.5f)
    }

    @Test
    fun `ArKalmanFilter3D damps single-frame depth spike and tracks sustained pan cleanly`() {
        val filter = ArKalmanFilter3D()
        val origin = Point3D(0.0, 0.0, -1.5, isArPrecision = true)
        var est = filter.update(origin, dt = 0.016f)
        repeat(5) {
            est = filter.update(origin, dt = 0.016f)
        }

        // Single-frame 25cm spike along Z should be heavily damped (< 8cm jump on the spike frame)
        val spike = Point3D(0.0, 0.0, -1.75, isArPrecision = true)
        val afterSpike = filter.update(spike, dt = 0.016f)
        val spikeDelta = kotlin.math.abs(afterSpike.z - est.z)
        assertTrue("Isolated 25cm depth glitch must be damped (<0.08m, was $spikeDelta)", spikeDelta < 0.08)

        // Sustained consistent panning along +X should confirm after 2+ frames and converge fast
        val panFilter = ArKalmanFilter3D()
        panFilter.update(origin, dt = 0.016f)
        panFilter.update(origin, dt = 0.016f)
        val target = Point3D(0.20, 0.0, -1.5, isArPrecision = true)
        panFilter.update(target, dt = 0.016f)
        val panFrame2 = panFilter.update(target, dt = 0.016f)
        val panFrame3 = panFilter.update(target, dt = 0.016f)
        assertTrue("Sustained pan should confirm and approach target (>0.15m, was ${panFrame3.x})", panFrame3.x > 0.15 && panFrame3.x > panFrame2.x)

        // Frame-rate invariance: 120Hz (dt=0.0083s * 4 steps) vs 30Hz (dt=0.033s * 1 step) over identical step
        val f120 = ArKalmanFilter3D()
        val f30 = ArKalmanFilter3D()
        f120.update(origin, dt = 0.0083f)
        f30.update(origin, dt = 0.033f)
        val step = Point3D(0.02, 0.0, -1.5, isArPrecision = true)
        var out120 = origin
        repeat(4) { out120 = f120.update(step, dt = 0.0083f) }
        val out30 = f30.update(step, dt = 0.033f)
        assertTrue("120Hz and 30Hz filters must converge within 6mm over equal wall-clock step", kotlin.math.abs(out120.x - out30.x) < 0.006)
    }

    @Test
    fun `ArMath computeRobustSampleCentroid rejects wild outlier spikes via Median and MAD`() {
        val xs = doubleArrayOf(1.000, 1.001, 0.999, 1.000, 1.001, 0.999, 1.450, 0.550)
        val ys = doubleArrayOf(0.500, 0.501, 0.499, 0.500, 0.500, 0.500, 0.900, 0.100)
        val zs = doubleArrayOf(-2.000, -2.001, -1.999, -2.000, -2.000, -2.001, -3.200, -0.800)
        val sortScratch = DoubleArray(xs.size)
        val distScratch = DoubleArray(xs.size)
        val outCentroid = DoubleArray(3)

        val stdErr = ArMath.computeRobustSampleCentroid(
            xs = xs,
            ys = ys,
            zs = zs,
            count = xs.size,
            sortScratch = sortScratch,
            distScratch = distScratch,
            outCentroidXYZ = outCentroid
        )

        assertEquals(1.000, outCentroid[0], 0.001)
        assertEquals(0.500, outCentroid[1], 0.001)
        assertEquals(-2.000, outCentroid[2], 0.001)
        assertTrue("Estimated stdErr must be sub-2mm after outlier rejection (was $stdErr)", stdErr < 0.002)
    }

    @Test
    fun `ArMath polygonArea and fitCircle3Points stay accurate far from origin and on obtuse angles`() {
        // 2m x 3m tilted rectangle translated far from world origin (5000, -2000, 4000)
        val ox = 5000.0
        val oy = -2000.0
        val oz = 4000.0
        val farQuad = listOf(
            Point3D(ox + 0.0, oy + 0.0, oz + 0.0),
            Point3D(ox + 2.0, oy + 0.0, oz + 0.0),
            Point3D(ox + 2.0, oy + 0.0, oz + 3.0),
            Point3D(ox + 0.0, oy + 0.0, oz + 3.0)
        )
        assertEquals(6.0, ArMath.polygonArea(farQuad), 1e-6)

        // Obtuse triangle on circle of radius 1.25m centered at (ox + 1.0, oy + 0.5, oz - 2.0)
        val cx = ox + 1.0
        val cy = oy + 0.5
        val cz = oz - 2.0
        val r = 1.25
        val anglesDeg = doubleArrayOf(10.0, 28.0, 48.0) // tightly clustered -> obtuse circumtriangle
        val pts = anglesDeg.map { deg ->
            val rad = Math.toRadians(deg)
            Point3D(cx + r * kotlin.math.cos(rad), cy, cz + r * kotlin.math.sin(rad))
        }
        val circle = ArMath.fitCircle3Points(pts[0], pts[1], pts[2])
        assertNotNull(circle)
        assertEquals(r, circle!!.radius, 1e-4)
        assertEquals(cx, circle.center.x, 1e-4)
        assertEquals(cy, circle.center.y, 1e-4)
        assertEquals(cz, circle.center.z, 1e-4)
    }

    @Test
    fun `ArMath rayPlaneIntersection computes exact intersection and rejects grazing or behind hits`() {
        val outHit = DoubleArray(4)

        // Oblique downward ray from (0, 1.5, 0) along normalized (0.6, -0.8, 0) onto floor plane y = 0, normal = (0, 1, 0)
        val hitOk = ArMath.rayPlaneIntersection(
            rayOriginX = 0.0, rayOriginY = 1.5, rayOriginZ = 0.0,
            rayDirX = 0.6, rayDirY = -0.8, rayDirZ = 0.0,
            planePointX = 1.0, planePointY = 0.0, planePointZ = -2.0,
            planeNormalX = 0.0, planeNormalY = 1.0, planeNormalZ = 0.0,
            minCosIncidence = 0.15,
            minT = 0.05,
            maxT = 25.0,
            outHitXYZT = outHit
        )
        assertTrue(hitOk)
        // t = 1.5 / 0.8 = 1.875 -> hitX = 1.875 * 0.6 = 1.125, hitY = 0.0, hitZ = 0.0
        assertEquals(1.125, outHit[0], 1e-6)
        assertEquals(0.0, outHit[1], 1e-6)
        assertEquals(0.0, outHit[2], 1e-6)
        assertEquals(1.875, outHit[3], 1e-6)

        // Reject shallow grazing angle (cos = 0.08 < 0.15)
        val grazingOk = ArMath.rayPlaneIntersection(
            rayOriginX = 0.0, rayOriginY = 1.5, rayOriginZ = 0.0,
            rayDirX = 0.9968, rayDirY = -0.08, rayDirZ = 0.0,
            planePointX = 0.0, planePointY = 0.0, planePointZ = 0.0,
            planeNormalX = 0.0, planeNormalY = 1.0, planeNormalZ = 0.0,
            minCosIncidence = 0.15,
            outHitXYZT = outHit
        )
        assertFalse(grazingOk)

        // Reject plane behind camera (ray pointing up while plane is below)
        val behindOk = ArMath.rayPlaneIntersection(
            rayOriginX = 0.0, rayOriginY = 1.5, rayOriginZ = 0.0,
            rayDirX = 0.0, rayDirY = 1.0, rayDirZ = 0.0,
            planePointX = 0.0, planePointY = 0.0, planePointZ = 0.0,
            planeNormalX = 0.0, planeNormalY = 1.0, planeNormalZ = 0.0,
            minCosIncidence = 0.15,
            outHitXYZT = outHit
        )
        assertFalse(behindOk)
    }

    @Test
    fun `MeasureViewModel syncs multiSampleAveragingEnabled and BALANCED preset with engine`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)

        // Initialized state must mirror multiSampleAveragingEnabled into sensorCorrectionEngine
        assertEquals(
            vm.multiSampleAveragingEnabled.value,
            vm.sensorCorrectionEngine.isMultiSampleAveragingEnabled
        )
        assertEquals(
            vm.rawDepthConfidenceEnabled.value,
            vm.modernArEngine.isRawDepthConfidenceFilterEnabled
        )

        // BALANCED preset must keep multiSampleAveragingEnabled = true for stationary shutter precision
        vm.applyMeasurementProfile("POWER_SAVER")
        assertFalse(vm.multiSampleAveragingEnabled.value)
        assertFalse(vm.sensorCorrectionEngine.isMultiSampleAveragingEnabled)

        vm.applyMeasurementProfile("BALANCED")
        assertTrue(vm.multiSampleAveragingEnabled.value)
        assertTrue(vm.sensorCorrectionEngine.isMultiSampleAveragingEnabled)

        // Surface uncertainty context scales error estimate cleanly without NaN
        vm.sensorCorrectionEngine.updateSurfaceUncertaintyContext(depthConfidencePercent = 25, surfaceDistanceMeters = 3.0f)
        vm.sensorCorrectionEngine.correctPointWithSensorFusion(
            rawPoint = Point3D(0.0, 0.0, -3.0, isArPrecision = true),
            previousPoint = null
        )
        val errMm = vm.sensorCorrectionEngine.telemetry.value.estimatedErrorMm
        assertFalse(errMm.isNaN() || errMm.isInfinite())
        assertTrue("Estimated error must stay strictly positive (was $errMm)", errMm > 0f)
    }
}
