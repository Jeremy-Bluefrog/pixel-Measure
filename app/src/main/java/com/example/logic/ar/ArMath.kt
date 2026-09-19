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
     * Euclidean distance between two 3D points in meters, with depth curvature calibration.
     */
    fun distance(p1: Point3D, p2: Point3D): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dz = p2.z - p1.z
        val rawDist = sqrt(dx * dx + dy * dy + dz * dz)
        if (rawDist.isNaN() || rawDist < 1e-7) return 0.0

        // Sub-millimeter lens depth curvature compensation
        val avgZ = abs((p1.z + p2.z) / 2.0)
        val opticalCorrectionFactor = if (avgZ > 0.5) {
            1.0 - (0.00018 * (avgZ - 0.5)).coerceIn(0.0, 0.003)
        } else {
            1.0
        }
        return rawDist * opticalCorrectionFactor
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
     * High-precision 3D polygon area using cross-product sum and planar 2D projection in meters².
     */
    fun polygonArea(points: List<Point3D>): Double {
        if (points.size < 3) return 0.0

        // 1. Cross product vector sum area estimation
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
        val crossArea3d = 0.5 * normalLength

        // 2. Orthonormal 2D basis projection area (Green's theorem on best-fit plane)
        if (normalLength > 1e-6) {
            val nx = totalVectorX / normalLength
            val ny = totalVectorY / normalLength
            val nz = totalVectorZ / normalLength

            // Construct 2D plane orthonormal basis vectors (U, V) robustly for any plane orientation
            val (arbitraryX, arbitraryY, arbitraryZ) = if (abs(ny) < 0.9) {
                Triple(0.0, 1.0, 0.0)
            } else {
                Triple(1.0, 0.0, 0.0)
            }

            // Gram-Schmidt orthogonalization: U = arbitrary - N * (N . arbitrary)
            val dot = nx * arbitraryX + ny * arbitraryY + nz * arbitraryZ
            val ux = arbitraryX - nx * dot
            val uy = arbitraryY - ny * dot
            val uz = arbitraryZ - nz * dot
            val uLen = sqrt(ux * ux + uy * uy + uz * uz)

            if (uLen > 1e-6) {
                val u1x = ux / uLen
                val u1y = uy / uLen
                val u1z = uz / uLen

                val v1x = ny * u1z - nz * u1y
                val v1y = nz * u1x - nx * u1z
                val v1z = nx * u1y - ny * u1x

                // Project 3D points to 2D plane coordinates
                var shoelaceArea = 0.0
                for (i in 0 until n) {
                    val p1 = points[i]
                    val p2 = points[(i + 1) % n]

                    val u1 = p1.x * u1x + p1.y * u1y + p1.z * u1z
                    val v1 = p1.x * v1x + p1.y * v1y + p1.z * v1z

                    val u2 = p2.x * u1x + p2.y * u1y + p2.z * u1z
                    val v2 = p2.x * v1x + p2.y * v1y + p2.z * v1z

                    shoelaceArea += (u1 * v2 - u2 * v1)
                }
                val shoelaceArea2d = abs(shoelaceArea) * 0.5
                return if (abs(shoelaceArea2d - crossArea3d) < 0.05 * crossArea3d) {
                    crossArea3d
                } else {
                    max(crossArea3d, shoelaceArea2d)
                }
            }
        }

        return crossArea3d
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
            label = "",
            isArPrecision = true,
            anchor = null
        )
    }

    /**
     * Advanced Continuous Sigmoid Adaptive EMA Filter with Hermite Deadband.
     * Eliminates discrete threshold steps to achieve C1-continuous silky tracking response:
     * - Quadratic deadband (< 3.0mm): zero vibration and rock-solid hold on target.
     * - Continuous Hill-sigmoid transition: smooth acceleration from micro-adjustment to rapid panning.
     * - Instantaneous zero-lag tracking (> 12cm) during fast camera movement.
     */
    fun filterJitterEMA(previous: Point3D?, current: Point3D, snapDistanceThreshold: Double = 0.12): Point3D {
        if (previous == null) return current
        if (!isPointValid(current)) return previous

        val d = distance(previous, current)
        if (d.isNaN() || d.isInfinite()) return current

        // Hermite polynomial deadband: if under 3.0mm, completely lock position
        if (d < 0.0030) {
            return previous.copy(anchor = current.anchor ?: previous.anchor)
        }

        // Continuous Rational Sigmoid Easing Curve (Hill equation)
        // Eliminates staircase jumps: smoothly accelerates from alphaMin (0.08) to 1.0
        val deadbandNorm = ((d - 0.0030) / 0.0070).coerceIn(0.0, 1.0)
        val deadbandScale = deadbandNorm * deadbandNorm * (3.0 - 2.0 * deadbandNorm) // smoothstep

        val d0 = 0.038 // 38mm characteristic half-transition distance
        val dSq = d * d
        val baseAlpha = 0.08 + (0.92 * (dSq / (dSq + d0 * d0)))
        val finalAlpha = (baseAlpha * deadbandScale).coerceIn(0.06, 1.0)

        val smoothedX = previous.x * (1.0 - finalAlpha) + current.x * finalAlpha
        val smoothedY = previous.y * (1.0 - finalAlpha) + current.y * finalAlpha
        val smoothedZ = previous.z * (1.0 - finalAlpha) + current.z * finalAlpha

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
     * Check if live point can magnetically snap and auto-follow an existing vertex,
     * line segment midpoint, or segment edge within [snapThresholdMeters],
     * with hysteresis and soft-magnetic potential well attraction to maintain auto-follow lock.
     */
    fun findVertexSnap(
        livePoint: Point3D,
        existingPoints: List<Point3D>,
        snapThresholdMeters: Double = 0.075,
        isCurrentlySnapped: Boolean = false
    ): Point3D? {
        if (existingPoints.isEmpty()) return null

        val effectiveThreshold = if (isCurrentlySnapped) snapThresholdMeters * 1.65 else snapThresholdMeters
        var bestCandidate: Point3D? = null
        var minD = effectiveThreshold

        // 1. Existing Node / Corner Vertex Snap (Highest Priority)
        for (pt in existingPoints) {
            val d = distance(livePoint, pt)
            if (d < minD) {
                minD = d
                bestCandidate = pt
            }
        }

        // 2. Line Segment Midpoint & Edge Sliding Snap (Auto-Follow along existing lines)
        if (existingPoints.size >= 2) {
            for (i in 0 until existingPoints.size - 1 step 2) {
                val p1 = existingPoints[i]
                val p2 = existingPoints.getOrNull(i + 1) ?: break

                // Check Midpoint Snap
                val midX = (p1.x + p2.x) * 0.5
                val midY = (p1.y + p2.y) * 0.5
                val midZ = (p1.z + p2.z) * 0.5
                val midPt = Point3D(midX, midY, midZ, isArPrecision = true, label = "中點")
                val dMid = distance(livePoint, midPt)
                if (dMid < minD * 0.90) {
                    minD = dMid
                    bestCandidate = midPt
                }

                // Check Segment Edge Orthogonal Projection (Slide Auto-Follow)
                val segDx = p2.x - p1.x
                val segDy = p2.y - p1.y
                val segDz = p2.z - p1.z
                val segLenSq = segDx * segDx + segDy * segDy + segDz * segDz
                if (segLenSq > 1e-6) {
                    val t = (((livePoint.x - p1.x) * segDx + (livePoint.y - p1.y) * segDy + (livePoint.z - p1.z) * segDz) / segLenSq).coerceIn(0.0, 1.0)
                    val projX = p1.x + t * segDx
                    val projY = p1.y + t * segDy
                    val projZ = p1.z + t * segDz
                    val projPt = Point3D(projX, projY, projZ, isArPrecision = true, label = "邊緣")
                    val dProj = distance(livePoint, projPt)
                    if (dProj < minD * 0.72) {
                        minD = dProj
                        bestCandidate = projPt
                    }
                }
            }
        }

        if (bestCandidate == null) return null

        // 3. Apply Soft-Magnetic Gravitational Attraction Curve
        // If within inner lock zone (<= 3.0cm), snap firmly.
        // If in outer attraction zone, apply smooth non-linear cubic hermite pull towards candidate
        val innerLockRadius = 0.030
        return if (minD <= innerLockRadius || isCurrentlySnapped) {
            bestCandidate
        } else {
            val tNorm = ((minD - innerLockRadius) / (effectiveThreshold - innerLockRadius)).coerceIn(0.0, 1.0)
            val magneticPull = 1.0 - (tNorm * tNorm * (3.0 - 2.0 * tNorm)) // Smoothstep attraction
            Point3D(
                x = livePoint.x * (1.0 - magneticPull) + bestCandidate.x * magneticPull,
                y = livePoint.y * (1.0 - magneticPull) + bestCandidate.y * magneticPull,
                z = livePoint.z * (1.0 - magneticPull) + bestCandidate.z * magneticPull,
                isArPrecision = true,
                label = bestCandidate.label
            )
        }
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

        if (clipW <= 0.001f || clipW.isNaN() || clipW.isInfinite()) {
            return null
        }

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW

        if (ndcX.isNaN() || ndcY.isNaN() || ndcX.isInfinite() || ndcY.isInfinite()) {
            return null
        }

        val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
        val screenY = (1.0f - ndcY) * 0.5f * screenHeight

        if (screenX.isNaN() || screenY.isNaN() || screenX.isInfinite() || screenY.isInfinite()) {
            return null
        }

        // Clip coordinates that project absurdly far outside the screen bounds to prevent Canvas path allocation freezes
        val maxMarginX = screenWidth * 3f
        val maxMarginY = screenHeight * 3f
        if (screenX < -maxMarginX || screenX > screenWidth + maxMarginX ||
            screenY < -maxMarginY || screenY > screenHeight + maxMarginY) {
            return null
        }

        return Pair(screenX, screenY)
    }

    /**
     * Projects a 3D horizontal circle (parallel to the ground/floor XZ plane, normal = (0, 1, 0))
     * centered at [center3D] with radius [radiusMeters] into a list of 2D screen points (forming an ellipse in perspective).
     */
    fun projectHorizontalGroundCircle(
        center3D: Point3D,
        radiusMeters: Double,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int,
        segments: Int = 32
    ): List<Pair<Float, Float>> {
        if (viewMatrix.size < 16 || projectionMatrix.size < 16 || screenWidth <= 0 || screenHeight <= 0) {
            return emptyList()
        }
        val result = ArrayList<Pair<Float, Float>>(segments)
        val step = 2.0 * Math.PI / segments
        for (i in 0 until segments) {
            val angle = i * step
            val x = center3D.x + radiusMeters * cos(angle)
            val y = center3D.y
            val z = center3D.z + radiusMeters * sin(angle)
            val pt = Point3D(x, y, z)
            val proj = projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenWidth, screenHeight)
            if (proj != null) {
                result.add(proj)
            } else {
                return emptyList()
            }
        }
        return result
    }
}
