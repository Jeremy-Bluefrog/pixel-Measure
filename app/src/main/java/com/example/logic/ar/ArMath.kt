package com.example.logic.ar

import com.example.ui.viewmodel.Point3D
import kotlin.math.*

/**
 * 3D Bounding Box result with dimensions and volume.
 */
data class BoundingBoxResult(
    val minX: Double = 0.0,
    val maxX: Double = 0.0,
    val minY: Double = 0.0,
    val maxY: Double = 0.0,
    val minZ: Double = 0.0,
    val maxZ: Double = 0.0,
    val lengthX: Double = 0.0,
    val heightY: Double = 0.0,
    val widthZ: Double = 0.0,
    val volume: Double = 0.0,
    val surfaceArea: Double = 0.0
)

/**
 * 3D Circle estimation result (Center, Radius, Diameter, Circumference, Area).
 */
data class CircleResult(
    val center: Point3D,
    val radius: Double,
    val diameter: Double,
    val circumference: Double,
    val area: Double
)

/**
 * High-precision 3D mathematical calculation utilities for modern AR spatial measurements.
 */
object ArMath {

    /**
     * Euclidean distance between two 3D points in meters.
     */
    fun distance(p1: Point3D, p2: Point3D): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dz = p2.z - p1.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Total length of a 3D polyline (chain of connected points) in meters.
     */
    fun polylineLength(points: List<Point3D>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distance(points[i], points[i + 1])
        }
        return total
    }

    /**
     * High-precision 3D polygon area using cross-product sum projection in meters².
     */
    fun polygonArea(points: List<Point3D>): Double {
        if (points.size < 3) return 0.0

        var totalVectorX = 0.0
        var totalVectorY = 0.0
        var totalVectorZ = 0.0

        val n = points.size
        for (i in 0 until n) {
            val pCurrent = points[i]
            val pNext = points[(i + 1) % n]

            val crossX = (pCurrent.y * pNext.z) - (pCurrent.z * pNext.y)
            val crossY = (pCurrent.z * pNext.x) - (pCurrent.x * pNext.z)
            val crossZ = (pCurrent.x * pNext.y) - (pCurrent.y * pNext.x)

            totalVectorX += crossX
            totalVectorY += crossY
            totalVectorZ += crossZ
        }

        val normalLength = sqrt(
            totalVectorX * totalVectorX +
            totalVectorY * totalVectorY +
            totalVectorZ * totalVectorZ
        )
        return 0.5 * normalLength
    }

    /**
     * 3D Bounding box and envelope volume computation.
     */
    fun calculateBoundingBox(points: List<Point3D>): BoundingBoxResult {
        if (points.isEmpty()) return BoundingBoxResult()

        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        var minZ = points[0].z
        var maxZ = points[0].z

        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
            if (p.z < minZ) minZ = p.z
            if (p.z > maxZ) maxZ = p.z
        }

        val lenX = abs(maxX - minX)
        val lenY = abs(maxY - minY)
        val lenZ = abs(maxZ - minZ)
        val vol = lenX * lenY * lenZ
        val area = 2.0 * (lenX * lenY + lenY * lenZ + lenZ * lenX)

        return BoundingBoxResult(
            minX = minX, maxX = maxX,
            minY = minY, maxY = maxY,
            minZ = minZ, maxZ = maxZ,
            lengthX = lenX, heightY = lenY, widthZ = lenZ,
            volume = vol, surfaceArea = area
        )
    }

    /**
     * Vertical height difference between two points (e.g. base point and top point) in meters.
     */
    fun verticalHeight(base: Point3D, top: Point3D): Double {
        return abs(top.y - base.y).coerceAtLeast(abs(top.z - base.z))
    }

    /**
     * Angle between two 3D vectors formed by (p1 -> p2) and (p2 -> p3) in degrees.
     */
    fun calculateAngleDegrees(p1: Point3D, vertex: Point3D, p3: Point3D): Double {
        val v1x = p1.x - vertex.x
        val v1y = p1.y - vertex.y
        val v1z = p1.z - vertex.z

        val v2x = p3.x - vertex.x
        val v2y = p3.y - vertex.y
        val v2z = p3.z - vertex.z

        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)

        if (mag1 < 1e-6 || mag2 < 1e-6) return 0.0

        val cosVal = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosVal))
    }

    /**
     * Fit a 3D circle from 3 points on a plane.
     */
    fun fitCircle3Points(p1: Point3D, p2: Point3D, p3: Point3D): CircleResult? {
        val a = distance(p2, p3)
        val b = distance(p1, p3)
        val c = distance(p1, p2)

        if (a < 1e-4 || b < 1e-4 || c < 1e-4) return null

        val s = (a + b + c) / 2.0
        val triangleArea = sqrt(max(0.0, s * (s - a) * (s - b) * (s - c)))
        if (triangleArea < 1e-6) return null

        val radius = (a * b * c) / (4.0 * triangleArea)
        val diameter = radius * 2.0
        val circumference = 2.0 * Math.PI * radius
        val area = Math.PI * radius * radius

        // Circumcenter formula in barycentric coordinates
        val a2 = a * a
        val b2 = b * b
        val c2 = c * c

        val alpha = a2 * (b2 + c2 - a2)
        val beta = b2 * (a2 + c2 - b2)
        val gamma = c2 * (a2 + b2 - c2)
        val total = alpha + beta + gamma

        if (abs(total) < 1e-6) return null

        val cx = (alpha * p1.x + beta * p2.x + gamma * p3.x) / total
        val cy = (alpha * p1.y + beta * p2.y + gamma * p3.y) / total
        val cz = (alpha * p1.z + beta * p2.z + gamma * p3.z) / total

        val center = Point3D(cx, cy, cz, 0f, 0f, true, "圓心")
        return CircleResult(center, radius, diameter, circumference, area)
    }

    /**
     * Midpoint between two 3D points.
     */
    fun midpoint(p1: Point3D, p2: Point3D): Point3D {
        return Point3D(
            x = (p1.x + p2.x) / 2.0,
            y = (p1.y + p2.y) / 2.0,
            z = (p1.z + p2.z) / 2.0,
            pitch = 0f,
            yaw = 0f,
            isArPrecision = true,
            label = null,
            anchor = null
        )
    }

    /**
     * Temporal Exponential Moving Average (EMA) filter to eliminate jitter on 3D spatial points.
     * When movement is small (< snapDistanceThreshold), strong smoothing is applied.
     * When camera moves quickly, it switches dynamically to fast tracking to prevent lag.
     */
    fun filterJitterEMA(previous: Point3D?, current: Point3D, snapDistanceThreshold: Double = 0.15): Point3D {
        if (previous == null) return current
        if (!isPointValid(current)) return previous

        val d = distance(previous, current)
        if (d.isNaN() || d.isInfinite()) return current

        // Adaptive alpha: strong smoothing (0.22) for hand tremor, immediate (1.0) for rapid camera panning
        val alpha = when {
            d < 0.02 -> 0.12 // Almost stationary: lock in place
            d < snapDistanceThreshold -> 0.25 // Minor tremor: smooth out noise
            d < snapDistanceThreshold * 2 -> 0.65 // Moderate move
            else -> 1.0 // Fast camera movement: instantaneous response
        }

        val smoothedX = previous.x * (1.0 - alpha) + current.x * alpha
        val smoothedY = previous.y * (1.0 - alpha) + current.y * alpha
        val smoothedZ = previous.z * (1.0 - alpha) + current.z * alpha

        return current.copy(
            x = smoothedX,
            y = smoothedY,
            z = smoothedZ
        )
    }

    /**
     * Validate that 3D coordinates are valid finite numbers within a realistic measuring range (0.02m to 40.0m).
     */
    fun isPointValid(point: Point3D): Boolean {
        if (point.x.isNaN() || point.y.isNaN() || point.z.isNaN()) return false
        if (point.x.isInfinite() || point.y.isInfinite() || point.z.isInfinite()) return false
        val distSq = point.x * point.x + point.y * point.y + point.z * point.z
        return distSq in 0.0004..1600.0 // 0.02m to 40.0m
    }

    /**
     * Check if live point can magnetically snap to an existing vertex within [snapThresholdMeters].
     */
    fun findVertexSnap(livePoint: Point3D, existingPoints: List<Point3D>, snapThresholdMeters: Double = 0.06): Point3D? {
        var closest: Point3D? = null
        var minD = snapThresholdMeters
        for (pt in existingPoints) {
            val d = distance(livePoint, pt)
            if (d < minD) {
                minD = d
                closest = pt
            }
        }
        return closest
    }

    /**
     * Project a 3D world coordinate (X, Y, Z) to 2D screen pixel coordinate using View and Projection matrices.
     * Returns Pair(screenX, screenY) in pixels, or null if the point is behind the camera (W <= 0).
     */
    fun projectWorldToScreen(
        worldPoint: Point3D,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        if (viewMatrix.size < 16 || projectionMatrix.size < 16 || screenWidth <= 0 || screenHeight <= 0) {
            return null
        }

        // Multiply ViewMatrix * WorldPoint
        val vx = viewMatrix[0] * worldPoint.x.toFloat() + viewMatrix[4] * worldPoint.y.toFloat() + viewMatrix[8] * worldPoint.z.toFloat() + viewMatrix[12]
        val vy = viewMatrix[1] * worldPoint.x.toFloat() + viewMatrix[5] * worldPoint.y.toFloat() + viewMatrix[9] * worldPoint.z.toFloat() + viewMatrix[13]
        val vz = viewMatrix[2] * worldPoint.x.toFloat() + viewMatrix[6] * worldPoint.y.toFloat() + viewMatrix[10] * worldPoint.z.toFloat() + viewMatrix[14]
        val vw = viewMatrix[3] * worldPoint.x.toFloat() + viewMatrix[7] * worldPoint.y.toFloat() + viewMatrix[11] * worldPoint.z.toFloat() + viewMatrix[15]

        // Multiply ProjectionMatrix * ViewPoint
        val clipX = projectionMatrix[0] * vx + projectionMatrix[4] * vy + projectionMatrix[8] * vz + projectionMatrix[12] * vw
        val clipY = projectionMatrix[1] * vx + projectionMatrix[5] * vy + projectionMatrix[9] * vz + projectionMatrix[13] * vw
        val clipW = projectionMatrix[3] * vx + projectionMatrix[7] * vy + projectionMatrix[11] * vz + projectionMatrix[15] * vw

        if (clipW <= 0.001f) {
            return null
        }

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW

        val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
        val screenY = (1.0f - ndcY) * 0.5f * screenHeight

        return Pair(screenX, screenY)
    }
}
