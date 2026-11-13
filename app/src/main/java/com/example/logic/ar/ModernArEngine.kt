package com.example.logic.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.Point3D
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
enum class ArCoreEngineStatus {
    ACTIVE,
    NEEDS_INSTALLATION,
    UNSUPPORTED,
    ERROR
}

/**
 * Modern AR Engine managing ARCore Session lifecycle, configurations,
 * multi-tier hit-testing (Planes, Depth, InstantPlacement, Points),
 * and dynamic Anchor management for zero-drift physical tracking.
 */
class ModernArEngine(private val context: Context) {

    var session: Session? = null
        private set

    val sharedCamera: SharedCamera?
        get() = try {
            session?.sharedCamera
        } catch (_: Throwable) {
            null
        }

    var isSupported: Boolean = false
        private set

    private val _arCoreStatus = MutableStateFlow(ArCoreEngineStatus.UNSUPPORTED)
    val arCoreStatus: StateFlow<ArCoreEngineStatus> = _arCoreStatus.asStateFlow()

    private val _arCoreStatusMessage = MutableStateFlow("正在檢查 Google ARCore 硬體狀態...")
    val arCoreStatusMessage: StateFlow<String> = _arCoreStatusMessage.asStateFlow()

    var is60FpsActive: Boolean = false
        private set

    var isDepthModeActive: Boolean = false
        private set

    var isTorchActive: Boolean = false
        private set

    @Volatile
    var isSessionResumed: Boolean = false
        private set

    @Volatile
    var isExposeHardwareBufferActive: Boolean = false
        private set

    var selectedCameraId: String? = null
        private set

    var selectedCameraImageSize: Size? = null
        private set

    var selectedCameraFpsRange: android.util.Range<Int>? = null
        private set

    var isRawDepthConfidenceFilterEnabled: Boolean = true
    var rawDepthConfidenceThreshold: Int = 45

    @Volatile
    var preferHighFps: Boolean = true
    @Volatile
    var preferHighDef: Boolean = true
    @Volatile
    var allowDepthMode: Boolean = true

    private var frameCounter = 0
    private var aimMissCounter = 0
    private var cachedPlanes: List<DetectedPlaneInfo> = emptyList()
    private var lastCameraPose: Pose? = null
    private var lastFrameTimeNs: Long = 0L
    private var smoothedSpeedMps: Float = 0.05f
    private var smoothedConfidence: Float = 0.85f

    private var userRequestedInstall = false
    private var eglHelper: HeadlessEglHelper? = null
    private val glContextLock = Any()
    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)

    // Pre-allocated scratch buffers to eliminate per-frame heap allocations on Pixel
    private val scratchPointIn = FloatArray(3)
    private val scratchPointOut = FloatArray(3)
    private val scratchNormalIn = floatArrayOf(0f, 1f, 0f)
    private val scratchNormalOut = FloatArray(3)
    private val scratchPoseTrans = FloatArray(3)
    private val neighborDx = floatArrayOf(-4f, 4f, 0f, 0f)
    private val neighborDy = floatArrayOf(0f, 0f, -4f, 4f)
    private val vMatrixPool = Array(2) { FloatArray(16) }
    private val pMatrixPool = Array(2) { FloatArray(16) }
    private var matrixPoolIdx = 0

    companion object {
        /**
         * Checks whether Google Play Store is installed and active on the device.
         */
        fun isGooglePlayStoreAvailable(context: Context): Boolean {
            return try {
                val pm = context.packageManager
                val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo("com.android.vending", android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo("com.android.vending", 0)
                }
                info.applicationInfo?.enabled == true
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Checks whether Google Play Services for AR (ARCore) package is installed and enabled on the device.
         */
        fun isArCorePackageInstalled(context: Context): Boolean {
            return try {
                val pm = context.packageManager
                val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo("com.google.ar.core", android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo("com.google.ar.core", 0)
                }
                info.applicationInfo?.enabled ?: true
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Opens the Google Play Store or web listing for com.google.ar.core.
         */
        fun openArCorePlayStorePage(context: Context): Boolean {
            val uris = listOf(
                android.net.Uri.parse("market://details?id=com.google.ar.core"),
                android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")
            )
            for (uri in uris) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return true
                } catch (_: Throwable) {}
            }
            return false
        }
    }

    /**
     * Check device compatibility and create configured ARCore Session safely.
     * Prevents Play Store install service binding errors when Play Store is absent,
     * and gracefully falls back to 9-axis sensor fusion without user interruption.
     */
    fun checkAndCreateSession(activity: Activity? = null): Session? {
        if (session != null) return session
        if (!isInitializing.compareAndSet(false, true)) {
            return session
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.i("ModernArEngine", "Camera permission not granted yet; deferring ARCore session creation.")
                return null
            }

            if (!isArCorePackageInstalled(context)) {
                isSupported = false
                _arCoreStatus.value = ArCoreEngineStatus.NEEDS_INSTALLATION
                _arCoreStatusMessage.value = "⚠️ 尚未安裝 Google Play Services for AR (com.google.ar.core)"
                Log.i("ModernArEngine", "ARCore package com.google.ar.core is not installed on device. Skipping native Session creation.")
                if (activity != null && !userRequestedInstall && isGooglePlayStoreAvailable(activity)) {
                    userRequestedInstall = true
                    try {
                        ArCoreApk.getInstance().requestInstall(activity, true)
                    } catch (_: Throwable) {}
                }
                return null
            }

            // First priority: Create ARCore session supporting shared camera or standard camera
            val newSession = try {
                Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA))
            } catch (e: UnavailableException) {
                throw e
            } catch (e: Throwable) {
                Log.w("ModernArEngine", "SharedCamera session creation note, fallback to standard: ${e.message}")
                Session(context)
            }

            // Align ARCore CameraConfig with Camera2 target FPS (30 vs 60) & resolution (1080p vs 720p)
            try {
                val desiredFpsFlag = if (preferHighFps) CameraConfig.TargetFps.TARGET_FPS_60 else CameraConfig.TargetFps.TARGET_FPS_30
                val fpsFilter = CameraConfigFilter(newSession).apply {
                    facingDirection = CameraConfig.FacingDirection.BACK
                    targetFps = EnumSet.of(desiredFpsFlag)
                }
                var supportedConfigs = newSession.getSupportedCameraConfigs(fpsFilter)
                if (supportedConfigs.isEmpty()) {
                    val fallbackFilter = CameraConfigFilter(newSession).apply {
                        facingDirection = CameraConfig.FacingDirection.BACK
                    }
                    supportedConfigs = newSession.getSupportedCameraConfigs(fallbackFilter)
                }
                if (supportedConfigs.isNotEmpty()) {
                    val preferredConfig = if (preferHighDef) {
                        supportedConfigs.firstOrNull { cfg ->
                            val ts = cfg.textureSize
                            ts.width == 1920 && ts.height == 1080
                        } ?: supportedConfigs.firstOrNull { cfg ->
                            val ts = cfg.textureSize
                            ts.width == 1280 && ts.height == 720
                        }
                    } else {
                        supportedConfigs.firstOrNull { cfg ->
                            val ts = cfg.textureSize
                            ts.width == 1280 && ts.height == 720
                        } ?: supportedConfigs.firstOrNull { cfg ->
                            val ts = cfg.textureSize
                            ts.width == 1920 && ts.height == 1080
                        }
                    } ?: supportedConfigs.firstOrNull { cfg ->
                        val ts = cfg.textureSize
                        kotlin.math.abs((ts.width.toFloat() / ts.height.coerceAtLeast(1)) - (16f / 9f)) < 0.05f
                    } ?: supportedConfigs.first()
                    newSession.cameraConfig = preferredConfig
                    is60FpsActive = try { preferredConfig.fpsRange.upper >= 60 } catch (_: Throwable) { false }
                }
            } catch (cfgEx: Throwable) {
                Log.w("ModernArEngine", "CameraConfig selection fallback: ${cfgEx.message}")
            }

            selectedCameraId = try { newSession.cameraConfig.cameraId } catch (_: Throwable) { null }
            selectedCameraImageSize = try { newSession.cameraConfig.textureSize } catch (_: Throwable) { null }
            selectedCameraFpsRange = try { newSession.cameraConfig.fpsRange } catch (_: Throwable) { null }

            val configured = configureSessionWithFallback(newSession)
            isExposeHardwareBufferActive = configured.textureUpdateMode == Config.TextureUpdateMode.EXPOSE_HARDWARE_BUFFER

            // Bind headless external OES GL texture cleanly for ARCore / SharedCamera pipeline
            if (!isExposeHardwareBufferActive) {
                synchronized(glContextLock) {
                    eglHelper?.release()
                    val helper = HeadlessEglHelper()
                    val texId = helper.init()
                    if (texId > 0) {
                        helper.makeCurrent()
                        try {
                            newSession.setCameraTextureName(texId)
                        } finally {
                            helper.detachCurrent()
                        }
                        eglHelper = helper
                    } else {
                        helper.release()
                        eglHelper = null
                        Log.w("ModernArEngine", "HeadlessEglHelper failed to allocate OES texture ID.")
                    }
                }
            }

            session = newSession
            isSessionResumed = false
            isSupported = true
            _arCoreStatus.value = ArCoreEngineStatus.ACTIVE
            _arCoreStatusMessage.value = "Google ARCore 已就緒 (水平/垂直平面與深度追蹤)"
            Log.i("ModernArEngine", "Google ARCore Session configured: camId=$selectedCameraId, textureSize=$selectedCameraImageSize, fps=$selectedCameraFpsRange, depth=$isDepthModeActive, ahb=$isExposeHardwareBufferActive")
            return newSession
        } catch (e: UnavailableDeviceNotCompatibleException) {
            isSupported = false
            _arCoreStatus.value = ArCoreEngineStatus.UNSUPPORTED
            _arCoreStatusMessage.value = "⚠️ 此硬體不相容 Google ARCore"
            Log.w("ModernArEngine", "DeviceNotCompatible: ${e.message}")
            return null
        } catch (e: UnavailableArcoreNotInstalledException) {
            isSupported = false
            _arCoreStatus.value = ArCoreEngineStatus.NEEDS_INSTALLATION
            _arCoreStatusMessage.value = "⚠️ 警告：尚未安裝 Google Play Services for AR"
            Log.w("ModernArEngine", "ArcoreNotInstalled: ${e.message}")
            return null
        } catch (e: UnavailableApkTooOldException) {
            isSupported = false
            _arCoreStatus.value = ArCoreEngineStatus.NEEDS_INSTALLATION
            _arCoreStatusMessage.value = "⚠️ 警告：Google Play Services for AR 版本過舊，請前往商店更新"
            Log.w("ModernArEngine", "ApkTooOld: ${e.message}")
            return null
        } catch (e: UnavailableSdkTooOldException) {
            isSupported = false
            _arCoreStatus.value = ArCoreEngineStatus.UNSUPPORTED
            _arCoreStatusMessage.value = "⚠️ 警告：應用程式 ARCore SDK 版本不相容"
            return null
        } catch (e: UnavailableUserDeclinedInstallationException) {
            isSupported = false
            _arCoreStatus.value = ArCoreEngineStatus.NEEDS_INSTALLATION
            _arCoreStatusMessage.value = "⚠️ 警告：使用者已取消安裝 ARCore"
            return null
        } catch (t: Throwable) {
            Log.e("ModernArEngine", "ARCore Session init exception: ${t.message}", t)
            _arCoreStatus.value = ArCoreEngineStatus.ERROR
            _arCoreStatusMessage.value = "⚠️ 警告：ARCore 初始化失敗 (${t.localizedMessage ?: "未知"})"
            isSupported = false
            return null
        } finally {
            isInitializing.set(false)
        }
    }

    private fun configureSessionWithFallback(newSession: Session): Config {
        val depthSupported = allowDepthMode && try {
            newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        } catch (_: Throwable) {
            false
        }

        // Always use BIND_TO_TEXTURE_EXTERNAL_OES with HeadlessEglHelper so SharedCamera GPU/CPU
        // surfaces stream reliably without needing direct Vulkan AHardwareBuffer texture sampling.
        data class SessionAttempt(
            val useDepth: Boolean,
            val lightMode: Config.LightEstimationMode,
            val instantPlacement: Config.InstantPlacementMode
        )

        val attempts = listOf(
            SessionAttempt(depthSupported, Config.LightEstimationMode.ENVIRONMENTAL_HDR, Config.InstantPlacementMode.LOCAL_Y_UP),
            SessionAttempt(depthSupported, Config.LightEstimationMode.AMBIENT_INTENSITY, Config.InstantPlacementMode.LOCAL_Y_UP),
            SessionAttempt(false, Config.LightEstimationMode.AMBIENT_INTENSITY, Config.InstantPlacementMode.LOCAL_Y_UP),
            SessionAttempt(false, Config.LightEstimationMode.AMBIENT_INTENSITY, Config.InstantPlacementMode.DISABLED)
        )

        var lastError: Throwable? = null
        for (attempt in attempts) {
            try {
                val config = Config(newSession)
                config.focusMode = Config.FocusMode.AUTO
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = attempt.lightMode
                config.depthMode = if (attempt.useDepth) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                config.instantPlacementMode = attempt.instantPlacement
                config.textureUpdateMode = Config.TextureUpdateMode.BIND_TO_TEXTURE_EXTERNAL_OES
                newSession.configure(config)
                isDepthModeActive = (config.depthMode == Config.DepthMode.AUTOMATIC)
                return config
            } catch (t: Throwable) {
                lastError = t
                Log.w("ModernArEngine", "ARCore configure attempt failed (depth=${attempt.useDepth}, light=${attempt.lightMode}, ip=${attempt.instantPlacement}): ${t.message}")
            }
        }
        throw lastError ?: IllegalStateException("Unable to configure ARCore session")
    }

    /**
     * Dynamically adapts ARCore Depth usage under thermal stress to prevent Tensor throttling.
     */
    fun applyRuntimePerformanceConfig(allowDepth: Boolean) {
        allowDepthMode = allowDepth
        val currentSession = session ?: return
        try {
            val config = currentSession.config
            val desiredDepth = if (allowDepth && currentSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Config.DepthMode.AUTOMATIC
            } else {
                Config.DepthMode.DISABLED
            }
            if (config.depthMode != desiredDepth) {
                config.depthMode = desiredDepth
                currentSession.configure(config)
                isDepthModeActive = (desiredDepth == Config.DepthMode.AUTOMATIC)
                Log.i("ModernArEngine", "Adjusted ARCore DepthMode=$desiredDepth for thermal/performance policy.")
            }
        } catch (t: Throwable) {
            Log.w("ModernArEngine", "applyRuntimePerformanceConfig skipped: ${t.message}")
        }
    }

    /**
     * User-requested explicit installation of Google Play Services for AR.
     */
    fun requestInstallArCore(activity: Activity): Boolean {
        if (!isGooglePlayStoreAvailable(activity)) {
            _arCoreStatusMessage.value = "裝置未安裝 Google Play 商店，無法自動安裝 ARCore"
            Log.i("ModernArEngine", "Cannot request install: Play Store not available on device.")
            return openArCorePlayStorePage(activity)
        }
        return try {
            val status = ArCoreApk.getInstance().requestInstall(activity, true)
            if (status == ArCoreApk.InstallStatus.INSTALLED) {
                checkAndCreateSession(activity)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.w("ModernArEngine", "Error requesting ARCore install: ${e.message}")
            openArCorePlayStorePage(activity)
        }
    }

    fun makeGlContextCurrent(): Boolean = synchronized(glContextLock) {
        val helper = eglHelper ?: return false
        return try {
            helper.makeCurrent()
        } catch (t: Throwable) {
            Log.w("ModernArEngine", "makeGlContextCurrent error: ${t.message}")
            false
        }
    }

    fun detachGlContext() = synchronized(glContextLock) {
        val helper = eglHelper ?: return
        try {
            helper.detachCurrent()
        } catch (_: Throwable) {}
    }

    fun rebindCameraTexture(): Boolean = synchronized(glContextLock) {
        val curSession = session ?: return false
        return try {
            eglHelper?.release()
            val helper = HeadlessEglHelper()
            val texId = helper.init()
            if (texId > 0) {
                helper.makeCurrent()
                try {
                    curSession.setCameraTextureName(texId)
                } finally {
                    helper.detachCurrent()
                }
                eglHelper = helper
                true
            } else {
                helper.release()
                eglHelper = null
                false
            }
        } catch (t: Throwable) {
            Log.w("ModernArEngine", "rebindCameraTexture error: ${t.message}")
            false
        }
    }

    fun createSession(): Session? {
        return checkAndCreateSession(null)
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
        isSessionResumed = false
        if (isTorchActive) {
            setTorchMode(false)
        }
        try {
            synchronized(glContextLock) {
                makeGlContextCurrent()
                try {
                    session?.pause()
                } finally {
                    detachGlContext()
                }
            }
        } catch (e: Throwable) {
            Log.w("ModernArEngine", "Failed to pause ARCore session: ${e.message}")
        }
    }

    fun markSessionPaused() {
        isSessionResumed = false
    }

    fun reportRuntimeError(message: String) {
        _arCoreStatusMessage.value = message
    }

    fun resume() {
        val curSession = session ?: return
        if (isSessionResumed) return
        try {
            synchronized(glContextLock) {
                if (eglHelper == null) {
                    rebindCameraTexture()
                }
                makeGlContextCurrent()
                try {
                    if (viewportWidth > 0 && viewportHeight > 0) {
                        curSession.setDisplayGeometry(lastDisplayRotation, viewportWidth, viewportHeight)
                    }
                    curSession.resume()
                } finally {
                    detachGlContext()
                }
            }
            // Publish resumed state ONLY after EGL context has been detached so VulkanRenderThread can acquire it
            isSessionResumed = true
            _arCoreStatus.value = ArCoreEngineStatus.ACTIVE
            _arCoreStatusMessage.value = "Google ARCore 已就緒 (水平/垂直平面與深度追蹤)"
            Log.i("ModernArEngine", "ARCore session resumed successfully.")
        } catch (e: CameraNotAvailableException) {
            isSessionResumed = false
            _arCoreStatusMessage.value = "⚠️ 相機暫時不可用，等待重新連線..."
            Log.w("ModernArEngine", "Camera not available for ARCore: ${e.message}")
        } catch (e: Exception) {
            isSessionResumed = false
            _arCoreStatusMessage.value = "⚠️ ARCore 啟動異常：${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e("ModernArEngine", "Failed to resume ARCore session", e)
        }
    }

    var viewportWidth: Int = 1080
        private set
    var viewportHeight: Int = 2400
        private set
    private var lastDisplayRotation: Int = 0

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        lastDisplayRotation = rotation
        viewportWidth = width
        viewportHeight = height
        try {
            session?.setDisplayGeometry(rotation, width, height)
        } catch (t: Throwable) {
            Log.w("ModernArEngine", "setDisplayGeometry note: ${t.message}")
        }
    }

    fun destroy() {
        isSessionResumed = false
        if (isTorchActive) {
            setTorchMode(false)
        }
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("ModernArEngine", "Error closing ARCore session", e)
        }
        session = null
        selectedCameraId = null
        selectedCameraImageSize = null
        selectedCameraFpsRange = null
        isTorchActive = false
        synchronized(glContextLock) {
            eglHelper?.release()
            eglHelper = null
        }
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
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return null
        }
        if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite() || x < 0f || y < 0f) {
            return null
        }
        if (viewportWidth > 0 && viewportHeight > 0 && (x > viewportWidth * 1.5f || y > viewportHeight * 1.5f)) {
            return null
        }
        try {
            val hits = frame.hitTest(x, y)

            // Single-pass zero-allocation tier resolution over hit list
            var planePolygonHit: HitResult? = null
            var planeEstimatedHit: HitResult? = null
            var depthHit: HitResult? = null
            var instantHit: HitResult? = null
            var featurePointHit: HitResult? = null
            var hasValidHit = false

            for (i in hits.indices) {
                val hit = hits[i]
                val dist = hit.distance
                if (dist < 0.05f || dist > 25.0f) continue
                hasValidHit = true
                val trackable = hit.trackable
                when {
                    trackable is Plane && trackable.trackingState == TrackingState.TRACKING -> {
                        if (planePolygonHit == null && trackable.isPoseInPolygon(hit.hitPose)) {
                            planePolygonHit = hit
                            break
                        } else if (planeEstimatedHit == null) {
                            planeEstimatedHit = hit
                        }
                    }
                    trackable is DepthPoint && trackable.trackingState == TrackingState.TRACKING -> {
                        if (depthHit == null) depthHit = hit
                    }
                    trackable is com.google.ar.core.Point -> {
                        if (depthHit == null && trackable.orientationMode == com.google.ar.core.Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) {
                            depthHit = hit
                        } else if (featurePointHit == null) {
                            featurePointHit = hit
                        }
                    }
                    trackable is InstantPlacementPoint -> {
                        if (instantHit == null) instantHit = hit
                    }
                }
            }

            // Sub-pixel 4-neighbor cross check: always run on tap, throttled every 3rd miss during aiming
            if (planePolygonHit == null && (createAnchor || aimMissCounter++ % 3 == 0)) {
                for (n in 0 until 4) {
                    val nHits = frame.hitTest(x + neighborDx[n], y + neighborDy[n])
                    for (j in nHits.indices) {
                        val hit = nHits[j]
                        if (hit.distance in 0.05f..25.0f) {
                            val trackable = hit.trackable
                            if (trackable is Plane && trackable.trackingState == TrackingState.TRACKING && trackable.isPoseInPolygon(hit.hitPose)) {
                                planePolygonHit = hit
                                break
                            }
                        }
                    }
                    if (planePolygonHit != null) break
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

            if (hasValidHit) {
                // Tier 2: Plane estimated (outside current polygon)
                if (planeEstimatedHit != null) {
                    val plane = planeEstimatedHit.trackable as Plane
                    val edgeSnap = findPlaneEdgeOrVertexSnap(plane, planeEstimatedHit.hitPose, snapRadiusMeters = 0.08f)
                    val finalPose = edgeSnap?.first ?: planeEstimatedHit.hitPose
                    val isSnapped = edgeSnap?.second ?: false

                    val anchor = if (createAnchor) {
                        try {
                            plane.createAnchor(finalPose)
                        } catch (e: Exception) {
                            try { planeEstimatedHit.createAnchor() } catch (e2: Exception) { null }
                        }
                    } else null
                    return HitTestResult(
                        pose = finalPose,
                        anchor = anchor,
                        hitType = HitType.PLANE_ESTIMATED,
                        distance = planeEstimatedHit.distance,
                        planeType = plane.type,
                        isSnappedToFeature = isSnapped
                    )
                }

                // Tier 3: Depth Point (from Depth API)
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
            val raycastPlaneHit = performPlaneRaycastIntersection(frame, x, y, createAnchor)
            if (raycastPlaneHit != null) {
                return raycastPlaneHit
            }

            // Tier 7: ARCore Instant Placement API (only when camera tracking is active)
            val currentSession = session
            if (currentSession != null && currentSession.config.instantPlacementMode != Config.InstantPlacementMode.DISABLED) {
                val instantHits = try {
                    frame.hitTestInstantPlacement(x, y, 1.5f)
                } catch (_: Throwable) {
                    emptyList()
                }
                val firstInstant = instantHits.firstOrNull { it.distance in 0.10f..15.0f }
                if (firstInstant != null) {
                    val anchor = if (createAnchor) {
                        try { firstInstant.createAnchor() } catch (_: Exception) { null }
                    } else null
                    return HitTestResult(
                        pose = firstInstant.hitPose,
                        anchor = anchor,
                        hitType = HitType.INSTANT_PLACEMENT,
                        distance = firstInstant.distance,
                        planeType = null
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("ModernArEngine", "Hit test exception: ${e.message}")
        }
        return null
    }

    /**
     * Tier 6: Mathematical Ray-Plane Surface Extrapolation.
     * Zero-allocation scratch array version.
     */
    private fun performPlaneRaycastIntersection(frame: Frame, x: Float, y: Float, createAnchor: Boolean): HitTestResult? {
        val currentSession = session ?: return null
        val planes = currentSession.getAllTrackables(Plane::class.java)
        if (planes.isEmpty()) return null

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
            camera.getProjectionMatrix(reusablePMatrix, 0, 0.05f, 50.0f)
            val p00 = reusablePMatrix[0]
            val p11 = reusablePMatrix[5]
            if (kotlin.math.abs(p00) > 1e-4f && kotlin.math.abs(p11) > 1e-4f) {
                val ndcX = (2.0f * x / viewportWidth.toFloat()) - 1.0f
                val ndcY = 1.0f - (2.0f * y / viewportHeight.toFloat())
                rayCamX = (ndcX + reusablePMatrix[8]) / p00
                rayCamY = (ndcY + reusablePMatrix[9]) / p11
            } else {
                rayCamX = 0f
                rayCamY = 0f
            }
        } else {
            rayCamX = 0f
            rayCamY = 0f
        }

        // Transform ray from camera space to world space using reusable scratch buffers
        scratchPointIn[0] = rayCamX
        scratchPointIn[1] = rayCamY
        scratchPointIn[2] = rayCamZ
        cameraPose.transformPoint(scratchPointIn, 0, scratchPointOut, 0)
        val rayDirX = scratchPointOut[0] - camX
        val rayDirY = scratchPointOut[1] - camY
        val rayDirZ = scratchPointOut[2] - camZ
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

        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
            val cPose = plane.centerPose
            val px = cPose.tx()
            val py = cPose.ty()
            val pz = cPose.tz()

            // Plane normal in world space (y-axis of center pose)
            cPose.transformPoint(scratchNormalIn, 0, scratchNormalOut, 0)
            val nx = scratchNormalOut[0] - px
            val ny = scratchNormalOut[1] - py
            val nz = scratchNormalOut[2] - pz

            val denom = rNormX * nx + rNormY * ny + rNormZ * nz
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
            scratchPoseTrans[0] = bestHitX
            scratchPoseTrans[1] = bestHitY
            scratchPoseTrans[2] = bestHitZ
            val hitPose = Pose(scratchPoseTrans, bestPlane.centerPose.rotationQuaternion)
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
     * or boundary edge segment with zero-alloc scratch transforms.
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

            scratchPointIn[0] = hitPose.tx()
            scratchPointIn[1] = hitPose.ty()
            scratchPointIn[2] = hitPose.tz()
            plane.centerPose.inverse().transformPoint(scratchPointIn, 0, scratchPointOut, 0)
            val hx = scratchPointOut[0]
            val hz = scratchPointOut[2]

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
                val dx1 = hx - x1
                val dz1 = hz - z1
                val d1Sq = dx1 * dx1 + dz1 * dz1
                if (d1Sq < bestDist * bestDist) {
                    bestDist = kotlin.math.sqrt(d1Sq)
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
                    val pdx = hx - px
                    val pdz = hz - pz
                    val segDistSq = pdx * pdx + pdz * pdz
                    val edgeCutoff = kotlin.math.min(bestDist, effectiveSnapRadius * 0.75f)
                    if (segDistSq < edgeCutoff * edgeCutoff) {
                        bestDist = kotlin.math.sqrt(segDistSq)
                        snapX = px
                        snapZ = pz
                        didSnap = true
                    }
                }
            }

            if (didSnap) {
                val innerLockRadius = 0.035f
                val finalSnapX: Float
                val finalSnapZ: Float
                if (bestDist <= innerLockRadius || isCurrentlySnapped) {
                    finalSnapX = snapX
                    finalSnapZ = snapZ
                } else {
                    val t = ((bestDist - innerLockRadius) / (effectiveSnapRadius - innerLockRadius)).coerceIn(0f, 1f)
                    val pull = 1f - (t * t * (3f - 2f * t))
                    finalSnapX = hx * (1f - pull) + snapX * pull
                    finalSnapZ = hz * (1f - pull) + snapZ * pull
                }

                scratchPointIn[0] = finalSnapX
                scratchPointIn[1] = 0f
                scratchPointIn[2] = finalSnapZ
                plane.centerPose.transformPoint(scratchPointIn, 0, scratchPointOut, 0)
                val snappedPose = Pose(scratchPointOut, hitPose.rotationQuaternion)
                return Pair(snappedPose, true)
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private val reusableVMatrix = FloatArray(16)
    private val reusablePMatrix = FloatArray(16)
    private var cachedFeaturePointsCount = 0

    /**
     * Extract frame data matrices and state safely on Vulkan/Render thread with double-buffered zero-alloc matrices.
     */
    fun extractFrameData(frame: Frame, centerHitResult: HitTestResult? = null): ModernArFrame {
        val camera = frame.camera
        matrixPoolIdx = 1 - matrixPoolIdx
        val vMatrix = vMatrixPool[matrixPoolIdx]
        val pMatrix = pMatrixPool[matrixPoolIdx]
        camera.getViewMatrix(vMatrix, 0)
        camera.getProjectionMatrix(pMatrix, 0, 0.05f, 50.0f)

        frameCounter++

        // Extract detected planes with polygon 2D vertices (throttled every 8 frames to prevent GC hiccups)
        if (frameCounter % 8 == 0 || cachedPlanes.isEmpty()) {
            val planeInfos = mutableListOf<DetectedPlaneInfo>()
            val allPlanes = session?.getAllTrackables(Plane::class.java)
            allPlanes?.forEach { plane ->
                if (plane.subsumedBy == null) {
                    val poly2d = plane.polygon
                    val rem = poly2d?.remaining() ?: 0
                    val polyArray = if (rem > 0) {
                        val arr = FloatArray(rem)
                        try { poly2d.get(arr); arr } catch (_: Throwable) { FloatArray(0) }
                    } else FloatArray(0)
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

/**
 * Headless EGL context providing an OpenGL ES texture ID for ARCore camera streaming.
 * Operates purely offscreen without requiring a GLSurfaceView.
 */
private class HeadlessEglHelper {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    var textureId: Int = 0
        private set

    fun init(): Int {
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return 0

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return 0

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            if (numConfigs[0] <= 0 || configs[0] == null) return 0

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return 0

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return 0

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            textureId = textures[0]

            // CRITICAL: Unbind immediately from initialization thread so VulkanRenderThread can bind it
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            return textureId
        } catch (e: Throwable) {
            Log.w("HeadlessEglHelper", "Headless EGL setup skipped: ${e.message}")
            return 0
        }
    }

    fun makeCurrent(): Boolean {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
            val currentContext = EGL14.eglGetCurrentContext()
            if (currentContext == eglContext) return true
            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }
        return false
    }

    fun detachCurrent() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
    }

    fun release() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (textureId != 0 && makeCurrent()) {
                    GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                    textureId = 0
                }
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Throwable) {
        } finally {
            textureId = 0
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
