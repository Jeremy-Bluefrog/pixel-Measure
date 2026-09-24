package com.example.logic.ar

import android.content.Context
import android.util.Log
import com.example.ui.viewmodel.Point3D
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.util.EnumSet

/**
 * Geometric information about a detected surface in AR space.
 */
data class DetectedPlaneInfo(
    val id: String,
    val type: Plane.Type,
    val centerPose: Pose,
    val extentX: Float,
    val extentZ: Float,
    val polygonVertices: FloatArray,
    val isTracking: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DetectedPlaneInfo
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Enhanced data packet extracted from an AR frame on the GL rendering thread.
 */
data class ModernArFrame(
    val trackingState: TrackingState,
    val trackingFailureReason: TrackingFailureReason,
    val viewMatrix: FloatArray,
    val projectionMatrix: FloatArray,
    val pointCloud: FloatArray?,
    val planes: List<DetectedPlaneInfo>,
    val cameraPoseX: Float,
    val cameraPoseY: Float,
    val cameraPoseZ: Float,
    val cameraPitch: Float,
    val cameraYaw: Float,
    val isDepthAvailable: Boolean,
    val lightIntensity: Float,
    val surfaceTypeAtCenter: String,
    val stability: ArTrackingStability = ArTrackingStability.default()
)

/**
 * Modern AR Engine managing ARCore Session lifecycle, configurations,
 * multi-tier hit-testing (Planes, Depth, InstantPlacement, Points),
 * and dynamic Anchor management for zero-drift physical tracking.
 */
class ModernArEngine(private val context: Context) {

    var session: Session? = null
        private set

    var isSupported: Boolean = false
        private set

    var is60FpsActive: Boolean = false
        private set

    var isDepthModeActive: Boolean = false
        private set

    var isTorchActive: Boolean = false
        private set

    var isRawDepthConfidenceFilterEnabled: Boolean = true
    var rawDepthConfidenceThreshold: Int = 45

    private var frameCounter = 0
    private var cachedPlanes: List<DetectedPlaneInfo> = emptyList()
    private var lastCameraPose: Pose? = null
    private var lastFrameTimeNs: Long = 0L
    private var smoothedSpeedMps: Float = 0.05f
    private var smoothedConfidence: Float = 0.85f

    /**
     * Check device compatibility and create configured ARCore Session safely.
     */
    fun createSession(): Session? {
        if (session != null) return session

        try {
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") ||
                    android.os.Build.FINGERPRINT.startsWith("unknown") ||
                    android.os.Build.FINGERPRINT.contains("vbox") ||
                    android.os.Build.MODEL.contains("google_sdk") ||
                    android.os.Build.MODEL.contains("Emulator") ||
                    android.os.Build.MODEL.contains("Android SDK built for x86") ||
                    android.os.Build.MODEL.contains("Cuttlefish") ||
                    android.os.Build.HARDWARE.contains("goldfish") ||
                    android.os.Build.HARDWARE.contains("ranchu") ||
                    android.os.Build.HARDWARE.contains("cutf") ||
                    android.os.Build.BOARD.contains("cutf") ||
                    android.os.Build.PRODUCT.contains("sdk") ||
                    android.os.Build.PRODUCT.contains("cf_x86") ||
                    android.os.Build.PRODUCT.contains("vbox") ||
                    android.os.Build.PRODUCT.contains("emulator") ||
                    android.os.Build.PRODUCT.contains("simulator") ||
                    android.os.Build.MANUFACTURER.contains("Genymotion") ||
                    (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == android.os.Build.PRODUCT

            if (isEmulator) {
                Log.i("ModernArEngine", "Running on Emulator. Activating CameraX Precision Fallback.")
                isSupported = false
                return null
            }

            // Check if Google Play Services for AR (com.google.ar.core) is already installed locally.
            // If ARCore is not installed, calling checkAvailability() triggers ARCore's internal InstallService
            // to bind to Play Store, which fails on devices/emulators without Play Store install daemon.
            val isArCorePackageInstalled = try {
                context.packageManager.getPackageInfo("com.google.ar.core", 0) != null
            } catch (e: Throwable) {
                false
            }

            if (!isArCorePackageInstalled) {
                Log.i("ModernArEngine", "Google Play Services for AR (com.google.ar.core) not installed. Activating CameraX Fallback.")
                isSupported = false
                return null
            }

            val availability = try {
                com.google.ar.core.ArCoreApk.getInstance().checkAvailability(context)
            } catch (t: Throwable) {
                com.google.ar.core.ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
            }

            if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                Log.i("ModernArEngine", "ARCore status ($availability). Activating CameraX Fallback.")
                isSupported = false
                return null
            }

            isSupported = true
            val sessionInstance = try {
                Session(context)
            } catch (t: Throwable) {
                Log.w("ModernArEngine", "ARCore Session instantiation failed: ${t.message}. Falling back to CameraX.")
                isSupported = false
                return null
            }
            session = sessionInstance

            // Configure High Resolution & 60 FPS Target Camera if supported
            try {
                val filter60Fps = CameraConfigFilter(sessionInstance).apply {
                    setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60))
                }
                var cameraConfigs = sessionInstance.getSupportedCameraConfigs(filter60Fps)
                if (cameraConfigs.isEmpty()) {
                    val filterAll = CameraConfigFilter(sessionInstance)
                    cameraConfigs = sessionInstance.getSupportedCameraConfigs(filterAll)
                }

                if (cameraConfigs.isNotEmpty()) {
                    // Pick the camera config with maximum resolution (width * height) for highest picture quality
                    val bestConfig = cameraConfigs.maxByOrNull { it.textureSize.width * it.textureSize.height } ?: cameraConfigs[0]
                    sessionInstance.setCameraConfig(bestConfig)
                    is60FpsActive = true
                    Log.i("ModernArEngine", "ARCore configured for High Resolution (${bestConfig.textureSize.width}x${bestConfig.textureSize.height}) mode.")
                } else {
                    is60FpsActive = false
                    Log.i("ModernArEngine", "ARCore default camera config active.")
                }
            } catch (e: Throwable) {
                is60FpsActive = false
                Log.w("ModernArEngine", "60 FPS / High Res config filter unavailable: ${e.message}")
            }

            // Apply modern ARCore configuration
            val config = Config(sessionInstance).apply {
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

                // Enable Instant Placement for immediate zero-wait measuring
                try {
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    Log.i("ModernArEngine", "Instant Placement Mode enabled.")
                } catch (e: Throwable) {
                    Log.w("ModernArEngine", "Instant Placement not supported: ${e.message}")
                }

                // Enable Depth API if device hardware supports it
                try {
                    if (sessionInstance.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                        isDepthModeActive = true
                        Log.i("ModernArEngine", "Automatic Depth Mode enabled.")
                    } else {
                        isDepthModeActive = false
                    }
                } catch (e: Throwable) {
                    isDepthModeActive = false
                    Log.w("ModernArEngine", "Depth mode check unavailable: ${e.message}")
                }

                // Apply initial FlashMode if requested
                if (isTorchActive) {
                    try {
                        flashMode = Config.FlashMode.TORCH
                        Log.i("ModernArEngine", "FlashMode.TORCH configured on session init.")
                    } catch (e: Throwable) {
                        Log.w("ModernArEngine", "Initial FlashMode TORCH unavailable: ${e.message}")
                    }
                }
            }

            sessionInstance.configure(config)
            try {
                sessionInstance.setCameraTextureName(0)
            } catch (e: Throwable) {
                // Handled when GLSurfaceView creates texture
            }
            sessionInstance.resume()

            return sessionInstance
        } catch (t: Throwable) {
            Log.w("ModernArEngine", "Fallback to CameraX Sensor Engine: ${t.message}")
            isSupported = false
            return null
        }
    }

    fun setTorchMode(enabled: Boolean): Boolean {
        isTorchActive = enabled
        val currentSession = session ?: return false
        return try {
            val config = currentSession.config
            config.flashMode = if (enabled) Config.FlashMode.TORCH else Config.FlashMode.OFF
            currentSession.configure(config)
            Log.i("ModernArEngine", "ARCore FlashMode configured: ${config.flashMode}")
            true
        } catch (e: Throwable) {
            Log.w("ModernArEngine", "Failed to set ARCore flashMode: ${e.message}")
            false
        }
    }

    fun pause() {
        if (isTorchActive) {
            setTorchMode(false)
        }
        try {
            session?.pause()
        } catch (e: Throwable) {
            Log.w("ModernArEngine", "Failed to pause ARCore session: ${e.message}")
        }
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            Log.e("ModernArEngine", "Failed to resume ARCore session", e)
        }
    }

    var viewportWidth: Int = 1080
        private set
    var viewportHeight: Int = 2400
        private set

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        session?.setDisplayGeometry(rotation, width, height)
    }

    fun destroy() {
        if (isTorchActive) {
            setTorchMode(false)
        }
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("ModernArEngine", "Error closing ARCore session", e)
        }
        session = null
        isTorchActive = false
    }

    /**
     * Multi-tier precision hit testing at pixel coordinate (x, y).
     * Priority:
     * 1. Plane polygon hit (highest confidence)
     * 2. Plane estimated hit
     * 3. Depth point hit
     * 4. Instant placement point hit
     * 5. Point cloud feature point hit
     *
     * Note: createAnchor is set to true ONLY when committing a point on user tap.
     * During real-time aiming, creating anchors on every frame causes ARCore memory/graph stalls.
     */
    fun performHitTest(frame: Frame, x: Float, y: Float, createAnchor: Boolean = false): HitTestResult? {
        try {
            val hits = frame.hitTest(x, y)
            val validHits = hits.filter { it.distance in 0.05f..25.0f }

            // Tier 1: Detected Plane within polygon bounds at primary raycast
            var planePolygonHit = validHits.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.trackingState == TrackingState.TRACKING && trackable.isPoseInPolygon(hit.hitPose)
            }

            // Sub-pixel 4-neighbor cross check to maximize Plane Polygon hit rate when aiming near edges
            if (planePolygonHit == null) {
                val offsets = arrayOf(Pair(-4f, 0f), Pair(4f, 0f), Pair(0f, -4f), Pair(0f, 4f))
                for ((ox, oy) in offsets) {
                    val nHits = frame.hitTest(x + ox, y + oy)
                    val candidate = nHits.firstOrNull { hit ->
                        val trackable = hit.trackable
                        trackable is Plane && trackable.trackingState == TrackingState.TRACKING && trackable.isPoseInPolygon(hit.hitPose)
                    }
                    if (candidate != null) {
                        planePolygonHit = candidate
                        break
                    }
                }
            }

            if (planePolygonHit != null) {
                val plane = planePolygonHit.trackable as Plane
                val edgeSnap = findPlaneEdgeOrVertexSnap(plane, planePolygonHit.hitPose, snapRadiusMeters = 0.08f)
                val finalPose = edgeSnap?.first ?: planePolygonHit.hitPose
                val isSnapped = edgeSnap?.second ?: false

                val anchor = if (createAnchor) {
                    try {
                        plane.createAnchor(finalPose)
                    } catch (e: Exception) {
                        try { planePolygonHit.createAnchor() } catch (e2: Exception) { null }
                    }
                } else null
                return HitTestResult(
                    pose = finalPose,
                    anchor = anchor,
                    hitType = HitType.PLANE_POLYGON,
                    distance = planePolygonHit.distance,
                    planeType = plane.type,
                    isSnappedToFeature = isSnapped
                )
            }

            if (validHits.isNotEmpty()) {
                // Tier 2: Plane estimated (outside current polygon)
                val planeHit = validHits.firstOrNull { hit ->
                    val trackable = hit.trackable
                    trackable is Plane && trackable.trackingState == TrackingState.TRACKING
                }
                if (planeHit != null) {
                    val plane = planeHit.trackable as Plane
                    val edgeSnap = findPlaneEdgeOrVertexSnap(plane, planeHit.hitPose, snapRadiusMeters = 0.08f)
                    val finalPose = edgeSnap?.first ?: planeHit.hitPose
                    val isSnapped = edgeSnap?.second ?: false

                    val anchor = if (createAnchor) {
                        try {
                            plane.createAnchor(finalPose)
                        } catch (e: Exception) {
                            try { planeHit.createAnchor() } catch (e2: Exception) { null }
                        }
                    } else null
                    return HitTestResult(
                        pose = finalPose,
                        anchor = anchor,
                        hitType = HitType.PLANE_ESTIMATED,
                        distance = planeHit.distance,
                        planeType = plane.type,
                        isSnappedToFeature = isSnapped
                    )
                }

                // Tier 3: Depth Point (from Depth API)
                val depthHit = validHits.firstOrNull { hit ->
                    val trackable = hit.trackable
                    trackable is com.google.ar.core.Point && trackable.orientationMode == com.google.ar.core.Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                }
                if (depthHit != null) {
                    val anchor = if (createAnchor) {
                        try { depthHit.createAnchor() } catch (e: Exception) { null }
                    } else null
                    return HitTestResult(
                        pose = depthHit.hitPose,
                        anchor = anchor,
                        hitType = HitType.DEPTH_POINT,
                        distance = depthHit.distance,
                        planeType = null
                    )
                }

                // Tier 4: Instant Placement Point
                val instantHit = validHits.firstOrNull { it.trackable is InstantPlacementPoint }
                if (instantHit != null) {
                    val anchor = if (createAnchor) {
                        try { instantHit.createAnchor() } catch (e: Exception) { null }
                    } else null
                    return HitTestResult(
                        pose = instantHit.hitPose,
                        anchor = anchor,
                        hitType = HitType.INSTANT_PLACEMENT,
                        distance = instantHit.distance,
                        planeType = null
                    )
                }

                // Tier 5: PointCloud feature point
                val featurePointHit = validHits.firstOrNull { it.trackable is com.google.ar.core.Point }
                if (featurePointHit != null) {
                    val anchor = if (createAnchor) {
                        try { featurePointHit.createAnchor() } catch (e: Exception) { null }
                    } else null
                    return HitTestResult(
                        pose = featurePointHit.hitPose,
                        anchor = anchor,
                        hitType = HitType.FEATURE_POINT,
                        distance = featurePointHit.distance,
                        planeType = null,
                        isSnappedToFeature = true
                    )
                }
            }

            // Tier 6: Mathematical Ray-Plane Surface Extrapolation (Auto-Follow Surface Continuity)
            // Seamlessly bridges gaps across walls, tables, and floors even when raw feature hits are empty
            val raycastPlaneHit = performPlaneRaycastIntersection(frame, x, y, createAnchor)
            if (raycastPlaneHit != null) {
                return raycastPlaneHit
            }

        } catch (e: Exception) {
            Log.e("ModernArEngine", "Hit test exception: ${e.message}")
        }
        return null
    }

    /**
     * Tier 6: Mathematical Ray-Plane Surface Extrapolation.
     * When moving across surfaces beyond immediate detected polygons,
     * this raycasts from the camera onto the nearest active tracking Plane's surface.
     */
    private fun performPlaneRaycastIntersection(frame: Frame, x: Float, y: Float, createAnchor: Boolean): HitTestResult? {
        val currentSession = session ?: return null
        val planes = currentSession.getAllTrackables(Plane::class.java)
        val trackingPlanes = planes.filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        if (trackingPlanes.isEmpty()) return null

        val camera = frame.camera
        val cameraPose = camera.pose
        val camX = cameraPose.tx()
        val camY = cameraPose.ty()
        val camZ = cameraPose.tz()

        // Unproject screen pixel (x, y) to camera space ray direction
        val rayCamX: Float
        val rayCamY: Float
        val rayCamZ = -1.0f
        if (viewportWidth > 0 && viewportHeight > 0) {
            camera.getProjectionMatrix(reusablePMatrix, 0, 0.1f, 100f)
            val p00 = reusablePMatrix[0]
            val p11 = reusablePMatrix[5]
            if (kotlin.math.abs(p00) > 1e-4f && kotlin.math.abs(p11) > 1e-4f) {
                val ndcX = (2.0f * x / viewportWidth.toFloat()) - 1.0f
                val ndcY = 1.0f - (2.0f * y / viewportHeight.toFloat())
                rayCamX = ndcX / p00
                rayCamY = ndcY / p11
            } else {
                rayCamX = 0f
                rayCamY = 0f
            }
        } else {
            rayCamX = 0f
            rayCamY = 0f
        }

        // Transform ray from camera space to world space
        val forward = cameraPose.transformPoint(floatArrayOf(rayCamX, rayCamY, rayCamZ))
        val rayDirX = forward[0] - camX
        val rayDirY = forward[1] - camY
        val rayDirZ = forward[2] - camZ
        val rLen = kotlin.math.sqrt(rayDirX * rayDirX + rayDirY * rayDirY + rayDirZ * rayDirZ)
        if (rLen < 1e-5f) return null
        val rNormX = rayDirX / rLen
        val rNormY = rayDirY / rLen
        val rNormZ = rayDirZ / rLen

        var bestT = Float.MAX_VALUE
        var bestPlane: Plane? = null
        var bestHitX = 0f
        var bestHitY = 0f
        var bestHitZ = 0f

        for (plane in trackingPlanes) {
            val cPose = plane.centerPose
            val px = cPose.tx()
            val py = cPose.ty()
            val pz = cPose.tz()

            // Plane normal in world space (y-axis of center pose)
            val up = cPose.transformPoint(floatArrayOf(0f, 1f, 0f))
            val nx = up[0] - px
            val ny = up[1] - py
            val nz = up[2] - pz

            val denom = rNormX * nx + rNormY * ny + rNormZ * nz
            // Must not be parallel to ray
            if (kotlin.math.abs(denom) > 0.05f) {
                val t = ((px - camX) * nx + (py - camY) * ny + (pz - camZ) * nz) / denom
                if (t in 0.15f..18.0f && t < bestT) {
                    bestT = t
                    bestPlane = plane
                    bestHitX = camX + t * rNormX
                    bestHitY = camY + t * rNormY
                    bestHitZ = camZ + t * rNormZ
                }
            }
        }

        if (bestPlane != null && bestT < 18.0f) {
            val hitPose = Pose(floatArrayOf(bestHitX, bestHitY, bestHitZ), bestPlane.centerPose.rotationQuaternion)
            val edgeSnap = findPlaneEdgeOrVertexSnap(bestPlane, hitPose, snapRadiusMeters = 0.08f)
            val finalPose = edgeSnap?.first ?: hitPose
            val isSnapped = edgeSnap?.second ?: false

            val anchor = if (createAnchor) {
                try {
                    bestPlane.createAnchor(finalPose)
                } catch (e: Exception) { null }
            } else null

            return HitTestResult(
                pose = finalPose,
                anchor = anchor,
                hitType = HitType.PLANE_ESTIMATED,
                distance = bestT,
                planeType = bestPlane.type,
                isSnappedToFeature = isSnapped
            )
        }
        return null
    }

    /**
     * Magnetically snap and auto-follow nearest detected plane polygon vertex (corner)
     * or boundary edge segment (wall lines, tile seams, door frames, table borders)
     * with soft-magnetic potential well attraction and hysteresis.
     */
    fun findPlaneEdgeOrVertexSnap(
        plane: Plane,
        hitPose: Pose,
        snapRadiusMeters: Float = 0.08f,
        isCurrentlySnapped: Boolean = false
    ): Pair<Pose, Boolean>? {
        try {
            val poly = plane.polygon ?: return null
            val count = poly.remaining() / 2
            if (count < 2) return null

            val localHit = plane.centerPose.inverse().transformPoint(floatArrayOf(hitPose.tx(), hitPose.ty(), hitPose.tz()))
            val hx = localHit[0]
            val hz = localHit[2]

            val effectiveSnapRadius = if (isCurrentlySnapped) snapRadiusMeters * 1.6f else snapRadiusMeters
            var bestDist = effectiveSnapRadius
            var snapX = hx
            var snapZ = hz
            var didSnap = false

            for (i in 0 until count) {
                val x1 = poly.get(i * 2)
                val z1 = poly.get(i * 2 + 1)
                val nextIdx = (i + 1) % count
                val x2 = poly.get(nextIdx * 2)
                val z2 = poly.get(nextIdx * 2 + 1)

                // 1. Polygon Corner Vertex Snap
                val d1 = kotlin.math.sqrt((hx - x1) * (hx - x1) + (hz - z1) * (hz - z1))
                if (d1 < bestDist) {
                    bestDist = d1
                    snapX = x1
                    snapZ = z1
                    didSnap = true
                }

                // 2. Polygon Boundary Edge Segment Snap
                val edx = x2 - x1
                val edz = z2 - z1
                val lenSq = edx * edx + edz * edz
                if (lenSq > 1e-6f) {
                    val t = (((hx - x1) * edx + (hz - z1) * edz) / lenSq).coerceIn(0f, 1f)
                    val px = x1 + t * edx
                    val pz = z1 + t * edz
                    val segDist = kotlin.math.sqrt((hx - px) * (hx - px) + (hz - pz) * (hz - pz))
                    if (segDist < bestDist && segDist < effectiveSnapRadius * 0.75f) {
                        bestDist = segDist
                        snapX = px
                        snapZ = pz
                        didSnap = true
                    }
                }
            }

            if (didSnap) {
                // Apply soft magnetic pull curve if between inner lock and outer release
                val innerLockRadius = 0.035f
                val finalSnapX: Float
                val finalSnapZ: Float
                if (bestDist <= innerLockRadius || isCurrentlySnapped) {
                    finalSnapX = snapX
                    finalSnapZ = snapZ
                } else {
                    val t = ((bestDist - innerLockRadius) / (effectiveSnapRadius - innerLockRadius)).coerceIn(0f, 1f)
                    val pull = 1f - (t * t * (3f - 2f * t)) // cubic smoothstep
                    finalSnapX = hx * (1f - pull) + snapX * pull
                    finalSnapZ = hz * (1f - pull) + snapZ * pull
                }

                val worldSnap = plane.centerPose.transformPoint(floatArrayOf(finalSnapX, 0f, finalSnapZ))
                val snappedPose = Pose(floatArrayOf(worldSnap[0], worldSnap[1], worldSnap[2]), hitPose.rotationQuaternion)
                return Pair(snappedPose, true)
            }
        } catch (e: Throwable) {
            // fallback gracefully
        }
        return null
    }

    private val reusableVMatrix = FloatArray(16)
    private val reusablePMatrix = FloatArray(16)
    private var cachedFeaturePointsCount = 0

    /**
     * Extract frame data matrices and state safely on GL thread with zero allocations.
     */
    fun extractFrameData(frame: Frame, centerHitResult: HitTestResult? = null): ModernArFrame {
        val camera = frame.camera
        camera.getViewMatrix(reusableVMatrix, 0)
        camera.getProjectionMatrix(reusablePMatrix, 0, 0.05f, 50.0f)
        val vMatrix = reusableVMatrix.clone()
        val pMatrix = reusablePMatrix.clone()

        frameCounter++

        // Extract detected planes with polygon 2D vertices (throttled every 6 frames to prevent GC hiccups)
        if (frameCounter % 6 == 0 || cachedPlanes.isEmpty()) {
            val planeInfos = mutableListOf<DetectedPlaneInfo>()
            val allPlanes = session?.getAllTrackables(Plane::class.java)
            allPlanes?.forEach { plane ->
                if (plane.subsumedBy == null) {
                    val poly2d = plane.polygon
                    val polyArray = FloatArray(poly2d.remaining())
                    poly2d.get(polyArray)
                    planeInfos.add(
                        DetectedPlaneInfo(
                            id = plane.hashCode().toString(),
                            type = plane.type,
                            centerPose = plane.centerPose,
                            extentX = plane.extentX,
                            extentZ = plane.extentZ,
                            polygonVertices = polyArray,
                            isTracking = plane.trackingState == TrackingState.TRACKING
                        )
                    )
                }
            }
            cachedPlanes = planeInfos
        }

        val pose = camera.pose
        val quaternion = pose.rotationQuaternion // [x, y, z, w]
        val qx = quaternion[0]
        val qy = quaternion[1]
        val qz = quaternion[2]
        val qw = quaternion[3]

        // Pitch & Yaw from Camera Pose
        val sinP = 2.0 * (qw * qx - qy * qz)
        val pRad = if (Math.abs(sinP) >= 1) Math.copySign(Math.PI / 2, sinP) else Math.asin(sinP)
        val pitchDeg = Math.toDegrees(pRad).toFloat()

        val sinY = 2.0 * (qw * qy + qz * qx)
        val cosY = 1.0 - 2.0 * (qx * qx + qy * qy)
        val yawDeg = Math.toDegrees(Math.atan2(sinY, cosY)).toFloat()

        val lightVal = try {
            frame.lightEstimate.pixelIntensity
        } catch (e: Exception) {
            1.0f
        }

        // Fast surface label inference from center hit test result
        val surfaceLabel = when (centerHitResult?.planeType) {
            Plane.Type.HORIZONTAL_UPWARD_FACING -> "水平地面/桌面"
            Plane.Type.VERTICAL -> "垂直牆面"
            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "天花板表面"
            else -> if (isDepthModeActive) "深度表面" else "空間特徵點"
        }

        // 1. Feature points count from PointCloud (throttled every 10 frames to avoid native allocation overhead)
        if (frameCounter % 10 == 0 || cachedFeaturePointsCount == 0) {
            val ptCloud = try { frame.acquirePointCloud() } catch (e: Throwable) { null }
            cachedFeaturePointsCount = try {
                val buf = ptCloud?.points
                if (buf != null) buf.remaining() / 4 else 0
            } catch (e: Throwable) {
                0
            } finally {
                try { ptCloud?.release() } catch (e: Throwable) {}
            }
        }
        val featurePointsCount = cachedFeaturePointsCount

        // 2. Camera panning velocity estimation (m/s)
        val nowNs = frame.timestamp
        val speed = if (lastCameraPose != null && lastFrameTimeNs > 0 && nowNs > lastFrameTimeNs) {
            val dt = (nowNs - lastFrameTimeNs) / 1_000_000_000.0f
            val dx = pose.tx() - lastCameraPose!!.tx()
            val dy = pose.ty() - lastCameraPose!!.ty()
            val dz = pose.tz() - lastCameraPose!!.tz()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            if (dt in 0.001f..0.5f) (dist / dt) else 0f
        } else 0f
        lastCameraPose = pose
        lastFrameTimeNs = nowNs
        smoothedSpeedMps = smoothedSpeedMps * 0.75f + speed * 0.25f

        // 3. Multi-factor Stability & Confidence Calculation
        val isTracking = camera.trackingState == TrackingState.TRACKING
        val failureReason = camera.trackingFailureReason
        val isMotionExcessive = failureReason == TrackingFailureReason.EXCESSIVE_MOTION || smoothedSpeedMps > 0.55f
        val isFeatureDeficient = failureReason == TrackingFailureReason.INSUFFICIENT_FEATURES ||
                (isTracking && featurePointsCount < 25 && cachedPlanes.isEmpty()) ||
                (isTracking && featurePointsCount < 15)
        val isLightingDeficient = failureReason == TrackingFailureReason.INSUFFICIENT_LIGHT || lightVal < 0.20f

        var score = if (isTracking) 0.60f else 0.10f
        val activePlanes = cachedPlanes.count { it.isTracking }
        score += kotlin.math.min(0.20f, activePlanes * 0.08f)

        score += when {
            featurePointsCount >= 120 -> 0.15f
            featurePointsCount >= 60 -> 0.10f
            featurePointsCount >= 30 -> 0.05f
            featurePointsCount < 15 -> -0.25f
            featurePointsCount < 25 -> -0.15f
            else -> 0.0f
        }

        score += when {
            lightVal >= 0.40f -> 0.05f
            lightVal < 0.18f -> -0.20f
            lightVal < 0.28f -> -0.10f
            else -> 0.0f
        }

        if (smoothedSpeedMps > 0.80f) {
            score -= 0.35f
        } else if (smoothedSpeedMps > 0.45f) {
            score -= 0.18f
        }

        if (failureReason == TrackingFailureReason.EXCESSIVE_MOTION) score -= 0.30f
        if (failureReason == TrackingFailureReason.INSUFFICIENT_FEATURES) score -= 0.35f
        if (failureReason == TrackingFailureReason.INSUFFICIENT_LIGHT) score -= 0.25f

        val clampedScore = score.coerceIn(0.05f, 0.99f)
        smoothedConfidence = smoothedConfidence * 0.80f + clampedScore * 0.20f

        val stabilityLevel = when {
            smoothedConfidence >= 0.75f -> StabilityLevel.HIGH
            smoothedConfidence >= 0.50f -> StabilityLevel.MODERATE
            smoothedConfidence >= 0.25f -> StabilityLevel.LOW
            else -> StabilityLevel.POOR
        }

        val isDriftRisk = !isTracking || isMotionExcessive || isFeatureDeficient ||
                stabilityLevel == StabilityLevel.LOW || stabilityLevel == StabilityLevel.POOR

        val warningMsg = when {
            isFeatureDeficient -> "特徵點不足，請慢速平移相機"
            isMotionExcessive -> "移動過快，請慢速平移相機"
            isLightingDeficient -> "環境光線不足，建議開啟手電筒補光"
            !isTracking -> "請將相機對準有紋理的地面，緩慢平移"
            else -> null
        }

        val stabilityMetrics = ArTrackingStability(
            confidenceScore = smoothedConfidence,
            level = stabilityLevel,
            featurePointsCount = featurePointsCount,
            trackingPlanesCount = activePlanes,
            cameraSpeedMps = smoothedSpeedMps,
            lightIntensity = lightVal,
            isMotionExcessive = isMotionExcessive,
            isFeatureDeficient = isFeatureDeficient,
            isLightingDeficient = isLightingDeficient,
            isDriftRisk = isDriftRisk,
            warningMessage = warningMsg
        )

        return ModernArFrame(
            trackingState = camera.trackingState,
            trackingFailureReason = camera.trackingFailureReason,
            viewMatrix = vMatrix,
            projectionMatrix = pMatrix,
            pointCloud = null,
            planes = cachedPlanes,
            cameraPoseX = pose.tx(),
            cameraPoseY = pose.ty(),
            cameraPoseZ = pose.tz(),
            cameraPitch = pitchDeg,
            cameraYaw = yawDeg,
            isDepthAvailable = isDepthModeActive,
            lightIntensity = lightVal,
            surfaceTypeAtCenter = surfaceLabel,
            stability = stabilityMetrics
        )
    }
}

enum class HitType {
    PLANE_POLYGON,
    PLANE_ESTIMATED,
    DEPTH_POINT,
    INSTANT_PLACEMENT,
    FEATURE_POINT
}

data class HitTestResult(
    val pose: Pose,
    val anchor: Anchor?,
    val hitType: HitType,
    val distance: Float,
    val planeType: Plane.Type? = null,
    val isSnappedToFeature: Boolean = false
)
