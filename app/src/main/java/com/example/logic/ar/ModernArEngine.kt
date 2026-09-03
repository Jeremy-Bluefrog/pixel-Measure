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
    val surfaceTypeAtCenter: String
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

    private var frameCounter = 0
    private var cachedPlanes: List<DetectedPlaneInfo> = emptyList()

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

            // Configure 60 FPS Target Camera if supported
            try {
                val filter = CameraConfigFilter(sessionInstance)
                filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60))
                val cameraConfigs = sessionInstance.getSupportedCameraConfigs(filter)
                if (cameraConfigs.isNotEmpty()) {
                    sessionInstance.setCameraConfig(cameraConfigs[0])
                    is60FpsActive = true
                    Log.i("ModernArEngine", "ARCore configured for 60 FPS high-refresh mode.")
                } else {
                    is60FpsActive = false
                    Log.i("ModernArEngine", "ARCore 60 FPS not supported on this device sensor, using default FPS config.")
                }
            } catch (e: Throwable) {
                is60FpsActive = false
                Log.w("ModernArEngine", "60 FPS config filter unavailable, using default camera config: ${e.message}")
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
            sessionInstance.setCameraTextureName(0)
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
        session?.pause()
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            Log.e("ModernArEngine", "Failed to resume ARCore session", e)
        }
    }

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
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
            if (hits.isEmpty()) return null

            // Filter hits within a reliable physical distance range (0.05m to 25.0m)
            val validHits = hits.filter { it.distance in 0.05f..25.0f }
            if (validHits.isEmpty()) return null

            // Tier 1: Detected Plane within polygon bounds
            val planePolygonHit = validHits.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.trackingState == TrackingState.TRACKING && trackable.isPoseInPolygon(hit.hitPose)
            }
            if (planePolygonHit != null) {
                val plane = planePolygonHit.trackable as Plane
                val anchor = if (createAnchor) {
                    try { planePolygonHit.createAnchor() } catch (e: Exception) { null }
                } else null
                return HitTestResult(
                    pose = planePolygonHit.hitPose,
                    anchor = anchor,
                    hitType = HitType.PLANE_POLYGON,
                    distance = planePolygonHit.distance,
                    planeType = plane.type
                )
            }

            // Tier 2: Plane estimated (outside current polygon)
            val planeHit = validHits.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.trackingState == TrackingState.TRACKING
            }
            if (planeHit != null) {
                val plane = planeHit.trackable as Plane
                val anchor = if (createAnchor) {
                    try { planeHit.createAnchor() } catch (e: Exception) { null }
                } else null
                return HitTestResult(
                    pose = planeHit.hitPose,
                    anchor = anchor,
                    hitType = HitType.PLANE_ESTIMATED,
                    distance = planeHit.distance,
                    planeType = plane.type
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
                    planeType = null
                )
            }

        } catch (e: Exception) {
            Log.e("ModernArEngine", "Hit test exception: ${e.message}")
        }
        return null
    }

    /**
     * Extract frame data matrices and state safely on GL thread with zero allocations.
     */
    fun extractFrameData(frame: Frame, centerHitResult: HitTestResult? = null): ModernArFrame {
        val camera = frame.camera
        val vMatrix = FloatArray(16)
        val pMatrix = FloatArray(16)
        camera.getViewMatrix(vMatrix, 0)
        camera.getProjectionMatrix(pMatrix, 0, 0.05f, 50.0f)

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
            surfaceTypeAtCenter = surfaceLabel
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
    val planeType: Plane.Type? = null
)
