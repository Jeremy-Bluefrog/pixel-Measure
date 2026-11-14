package com.example.logic.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.google.ar.core.SharedCamera
import java.lang.ref.WeakReference

/**
 * High-performance, rock-solid Camera2 Direct Manager supporting:
 * 1. Safe resolution step-down (1080p / 720p) matching HAL buffer constraints.
 * 2. Dynamic, hardware-supported FPS range selection (60 FPS prioritized, 30 FPS safe fallback).
 * 3. Graceful hardware feature negotiation (AF, AE, Edge, Noise modes) preventing HAL crashes.
 * 4. Resilient session configuration with automatic recovery.
 * 5. Low Light Boost & dynamic torch management.
 */
class HighSpeedCamera2Manager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    @Volatile
    private var cameraDevice: CameraDevice? = null
    @Volatile
    private var captureSession: CameraCaptureSession? = null

    private var backgroundThread: HandlerThread? = null
    @Volatile
    private var backgroundHandler: Handler? = null
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private val cameraGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    var sharedCamera: SharedCamera? = null
    var preferredCameraId: String? = null
    var preferredAspectRatio: Float? = null
    var preferredFpsRange: Range<Int>? = null
    var onArSessionReadyToResume: ((CameraCaptureSession.CaptureCallback, Handler?) -> Unit)? = null
    var onBeforeCameraSwitch: (() -> Unit)? = null
    var isArCoreResumedChecker: (() -> Boolean)? = null

    private var activeTextureView: WeakReference<TextureView>? = null
    private val arResumeTriggeredForSession = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile
    private var isSharedCameraBoundToSession = false

    private var currentTorchState = false
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var isHighSpeedSessionActive = false

    var onFrameFpsUpdated: ((Int) -> Unit)? = null
    private var frameCounter = 0
    private var lastFpsTimestampNs = 0L
    private var captureResultCounter = 0

    @Volatile
    var preferHighFps: Boolean = true
    @Volatile
    var preferHighDef: Boolean = true
    @Volatile
    var thermalMaxFps: Int = 60
    private var activeCharacteristics: CameraCharacteristics? = null

    @Volatile
    private var isClosed = false
    private var openRetryCount = 0
    private var activeCameraId: String? = null
    @Volatile
    private var isOpening = false
    private var isFlashSupported = false
    var previewSize: Size? = null
        private set

    var onCameraUnavailable: (() -> Unit)? = null
    var onCameraAvailable: (() -> Unit)? = null

    var preferredFacing: Int = CameraCharacteristics.LENS_FACING_BACK
        private set
    var isFrontFacing: Boolean = false
        private set
    var sensorOrientation: Int = 90
        private set
    var isHorizontallyMirrored: Boolean = false
        private set

    fun isSharedCameraArActive(): Boolean {
        return preferredFacing == CameraCharacteristics.LENS_FACING_BACK &&
            sharedCamera != null &&
            (isArCoreResumedChecker?.invoke() == true)
    }

    fun isCameraOpenWithoutSharedCamera(): Boolean {
        return (cameraDevice != null || captureSession != null || isOpening) &&
            !isClosed &&
            !isSharedCameraBoundToSession
    }

    fun setHorizontallyMirrored(mirrored: Boolean, textureView: TextureView?) {
        isHorizontallyMirrored = mirrored
        textureView?.let { tv ->
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                configureTransform(tv, tv.width, tv.height)
            }
        }
    }

    private val isSwitchingCamera = java.util.concurrent.atomic.AtomicBoolean(false)

    fun switchCamera(
        textureView: TextureView,
        onBeforeCloseCamera: (() -> Unit)? = null,
        onSessionConfigured: ((Boolean, Size) -> Unit)? = null
    ) {
        if (!isSwitchingCamera.compareAndSet(false, true)) {
            Log.w(TAG, "Camera switch already in progress, ignoring duplicate tap.")
            return
        }

        preferredFacing = if (preferredFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        (onBeforeCloseCamera ?: onBeforeCameraSwitch)?.invoke()
        closeCamera()
        val expectedGen = cameraGeneration.get()

        // Allow HAL hardware pipe 120ms to completely tear down previous camera instance
        mainHandler.postDelayed({
            try {
                if (cameraGeneration.get() != expectedGen) {
                    isSwitchingCamera.set(false)
                    return@postDelayed
                }
                if (textureView.isAvailable) {
                    openCameraAndStartSession(textureView) { success, size ->
                        isSwitchingCamera.set(false)
                        onSessionConfigured?.invoke(success, size)
                    }
                } else {
                    isSwitchingCamera.set(false)
                    onSessionConfigured?.invoke(false, Size(0, 0))
                }
            } catch (e: Throwable) {
                isSwitchingCamera.set(false)
                onSessionConfigured?.invoke(false, Size(0, 0))
            }
        }, 120L)
    }

    val lowLightBoostEngine = LowLightBoostEngine(context)
    private var isLowLightBoostEnabled = true

    fun setLowLightBoostEnabled(enabled: Boolean = true) {
        isLowLightBoostEnabled = enabled
        if (isSharedCameraArActive()) {
            // While ARCore SharedCamera is resumed, ARCore manages CaptureSession repeating requests.
            return
        }
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            lowLightBoostEngine.applyLowLightBoost(builder, enabled)
            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply Low Light Boost update: ${e.message}")
        }
    }

    fun startBackgroundThread() {
        if (backgroundThread == null || backgroundThread?.isAlive != true) {
            backgroundThread = HandlerThread(
                "HighSpeedCameraBackground",
                android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE
            ).apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        try {
            val thread = backgroundThread
            backgroundHandler?.removeCallbacksAndMessages(null)
            thread?.quitSafely()
            if (thread != null && Thread.currentThread() !== thread) {
                thread.join(300)
            }
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    /**
     * Selects the optimal preview resolution supported by the device's SurfaceTexture map.
     * Capped at 1080p (1920x1080) for high frame-rate rendering and low GPU memory overhead.
     */
    fun selectOptimalPreviewSize(characteristics: CameraCharacteristics, targetAspect: Float? = null): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

        if (outputSizes.isEmpty()) {
            return Size(1280, 720)
        }

        val want1080p = preferHighDef && thermalMaxFps >= 60

        // If ARCore specified an active CameraConfig aspect ratio, match it first to prevent FOV/crop mismatch
        if (targetAspect != null && targetAspect > 0f) {
            val matching = outputSizes.filter { s ->
                val aspect = s.width.toFloat() / s.height.coerceAtLeast(1)
                kotlin.math.abs(aspect - targetAspect) < 0.05f
            }
            if (matching.isNotEmpty()) {
                val preferred = if (want1080p) {
                    matching.firstOrNull { it.width == 1920 && it.height == 1080 }
                        ?: matching.firstOrNull { it.width == 1280 && it.height == 720 }
                } else {
                    matching.firstOrNull { it.width == 1280 && it.height == 720 }
                        ?: matching.firstOrNull { it.width == 1920 && it.height == 1080 }
                }
                return preferred
                    ?: matching.firstOrNull { it.width in 960..1440 }
                    ?: matching.maxByOrNull { it.width * it.height }
                    ?: matching.first()
            }
        }

        return if (want1080p) {
            outputSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
                ?: outputSizes.firstOrNull { it.width == 1280 && it.height == 720 }
        } else {
            outputSizes.firstOrNull { it.width == 1280 && it.height == 720 }
                ?: outputSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
        }
            ?: outputSizes.firstOrNull { it.width == 960 && it.height == 540 }
            ?: outputSizes.firstOrNull { it.width in 1000..1400 }
            ?: outputSizes.maxByOrNull { it.width * it.height }
            ?: outputSizes.first()
    }

    /**
     * Backward & forward-compatible FPS size & capability checker.
     */
    fun selectOptimal60FpsSize(characteristics: CameraCharacteristics): Pair<Size, Boolean> {
        val optimalSize = selectOptimalPreviewSize(characteristics)
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val supports60 = fpsRanges.any { it.upper >= 60 }
        return Pair(optimalSize, supports60)
    }

    /**
     * Safely queries supported AE target FPS ranges respecting user preference & thermal cap.
     */
    private fun selectOptimalFpsRange(
        characteristics: CameraCharacteristics,
        forceSafe30Fps: Boolean = false,
        matchArPreferred: Boolean = (sharedCamera != null)
    ): Range<Int> {
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        if (matchArPreferred && !forceSafe30Fps) {
            val arFps = preferredFpsRange
            if (arFps != null) {
                fpsRanges.firstOrNull { it == arFps }?.let { return it }
                fpsRanges.firstOrNull { it.upper == arFps.upper && it.lower <= arFps.lower }?.let { return it }
                fpsRanges.firstOrNull { it.upper == arFps.upper }?.let { return it }
            }
        }
        val effectiveMaxFps = if (!forceSafe30Fps && preferHighFps) thermalMaxFps.coerceAtMost(60) else 30

        if (effectiveMaxFps >= 60) {
            fpsRanges.firstOrNull { it.lower >= 60 && it.upper >= 60 }?.let { return it }
            fpsRanges.firstOrNull { it.lower >= 30 && it.upper >= 60 }?.let { return it }
            fpsRanges.firstOrNull { it.upper >= 60 }?.let { return it }
        }

        // Standard smooth 30 FPS (also used in Power Saver / thermal step-down)
        fpsRanges.firstOrNull { it.lower == 30 && it.upper == 30 }?.let { return it }
        fpsRanges.firstOrNull { it.upper in 30..effectiveMaxFps }?.let { return it }
        fpsRanges.firstOrNull { it.upper >= 30 }?.let { return it }

        return fpsRanges.firstOrNull() ?: Range(30, 30)
    }

    fun updatePerformanceConfig(highFps: Boolean, highDef: Boolean, maxFps: Int) {
        preferHighFps = highFps
        preferHighDef = highDef
        thermalMaxFps = maxFps
        val targetRate = if (highFps) maxFps.toFloat().coerceAtMost(60f) else 30f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                previewSurface?.let { s ->
                    if (s.isValid) s.setFrameRate(targetRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            } catch (_: Throwable) {}
        }
        if (isSharedCameraArActive()) return
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val chars = activeCharacteristics ?: return
        try {
            val fpsRange = selectOptimalFpsRange(chars)
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            isHighSpeedSessionActive = fpsRange.upper >= 60
            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Throwable) {
            Log.w(TAG, "Dynamic FPS range update skipped: ${e.message}")
        }
    }

    /**
     * Safely selects supported Autofocus Mode.
     */
    private fun selectOptimalAfMode(characteristics: CameraCharacteristics): Int {
        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        return when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> CaptureRequest.CONTROL_AF_MODE_OFF
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            countFrameAndCalculateFps()
            if (captureResultCounter++ % 6 == 0) {
                lowLightBoostEngine.onCaptureResult(result)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun openCameraAndStartSession(
        textureView: TextureView,
        onSessionConfigured: ((Boolean, Size) -> Unit)? = null
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot open camera: CAMERA permission not granted.")
            return
        }

        if (isOpening) {
            Log.i(TAG, "Camera is currently opening, ignoring duplicate request.")
            return
        }

        if (cameraDevice != null && captureSession != null && !isClosed) {
            Log.i(TAG, "Camera session already active.")
            return
        }

        // If cameraDevice was opened but session is null or stale, safely close it first
        if (cameraDevice != null) {
            try {
                captureSession?.close()
                captureSession = null
                cameraDevice?.close()
                cameraDevice = null
            } catch (_: Exception) {}
        }

        isClosed = false
        isOpening = true
        val sessionGen = cameraGeneration.incrementAndGet()
        startBackgroundThread()

        try {
            val cameraIdList = try { cameraManager.cameraIdList } catch (_: Throwable) { emptyArray() }
            if (cameraIdList.isEmpty()) {
                Log.e(TAG, "No camera available on device, entering simulation fallback mode.")
                isOpening = false
                onSessionConfigured?.invoke(false, Size(0, 0))
                mainHandler.post {
                    if (!isClosed && cameraGeneration.get() == sessionGen) {
                        onCameraUnavailable?.invoke()
                    }
                }
                return
            }

            // Select camera according to preferred facing (Back vs Front) and ARCore CameraConfig ID
            val targetFacing = preferredFacing
            val activeSharedCamera = if (targetFacing == CameraCharacteristics.LENS_FACING_BACK) sharedCamera else null
            val prefId = preferredCameraId
            val cameraId = if (targetFacing == CameraCharacteristics.LENS_FACING_BACK &&
                prefId != null &&
                cameraIdList.contains(prefId)
            ) {
                prefId
            } else {
                cameraIdList.firstOrNull { id ->
                    try {
                        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
                    } catch (_: Throwable) { false }
                } ?: cameraIdList.firstOrNull { id ->
                    try {
                        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_BACK
                    } catch (_: Throwable) { false }
                } ?: cameraIdList.first()
            }

            activeCameraId = cameraId
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            activeCharacteristics = characteristics
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            isFrontFacing = facing == CameraCharacteristics.LENS_FACING_FRONT
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val aspectToMatch = if (activeSharedCamera != null) preferredAspectRatio else null
            val optimalSize = selectOptimalPreviewSize(characteristics, aspectToMatch)
            previewSize = optimalSize

            val texture = textureView.surfaceTexture
            if (texture == null) {
                Log.w(TAG, "TextureView surfaceTexture is null, waiting for surface availability.")
                isOpening = false
                onSessionConfigured?.invoke(false, optimalSize)
                return
            }

            activeTextureView = WeakReference(textureView)

            // Configure default buffer size matching hardware camera output
            texture.setDefaultBufferSize(optimalSize.width, optimalSize.height)
            previewSurface?.release()
            val surface = Surface(texture)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val targetRate = if (preferHighFps) thermalMaxFps.toFloat().coerceAtMost(60f) else 30f
                    surface.setFrameRate(targetRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (_: Throwable) {}
            }
            previewSurface = surface

            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isOpening = false
                    if (isClosed || cameraGeneration.get() != sessionGen) {
                        try { camera.close() } catch (_: Exception) {}
                        return
                    }
                    cameraDevice = camera
                    openRetryCount = 0
                    mainHandler.post { onCameraAvailable?.invoke() }
                    configureSession(camera, surface, optimalSize, characteristics, activeSharedCamera, sessionGen, onSessionConfigured)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isOpening = false
                    Log.i(TAG, "Camera disconnected")
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }
                    if (!isClosed && cameraGeneration.get() == sessionGen && openRetryCount < 3) {
                        openRetryCount++
                        val backoffMs = 250L * openRetryCount
                        mainHandler.postDelayed({
                            if (!isClosed && cameraGeneration.get() == sessionGen && textureView.isAvailable) {
                                openCameraAndStartSession(textureView, onSessionConfigured)
                            }
                        }, backoffMs)
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isOpening = false
                    Log.e(TAG, "Camera device error: $error (retry=$openRetryCount)")
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }

                    if (!isClosed && cameraGeneration.get() == sessionGen && openRetryCount < 3) {
                        openRetryCount++
                        val backoffMs = 250L * openRetryCount
                        mainHandler.postDelayed({
                            if (!isClosed && cameraGeneration.get() == sessionGen && textureView.isAvailable) {
                                openCameraAndStartSession(textureView, onSessionConfigured)
                            } else {
                                onSessionConfigured?.invoke(false, optimalSize)
                            }
                        }, backoffMs)
                    } else {
                        onSessionConfigured?.invoke(false, optimalSize)
                        if (!isClosed && cameraGeneration.get() == sessionGen) {
                            mainHandler.post { onCameraUnavailable?.invoke() }
                        }
                    }
                }
            }

            val wrappedCallback = activeSharedCamera?.createARDeviceStateCallback(stateCallback, backgroundHandler) ?: stateCallback
            cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler)

        } catch (cae: CameraAccessException) {
            isOpening = false
            val isTransient = cae.reason == CameraAccessException.CAMERA_IN_USE ||
                cae.reason == CameraAccessException.MAX_CAMERAS_IN_USE ||
                cae.reason == CameraAccessException.CAMERA_DISCONNECTED
            if (isTransient && !isClosed && cameraGeneration.get() == sessionGen && openRetryCount < 3) {
                openRetryCount++
                val backoffMs = 250L * openRetryCount
                Log.w(TAG, "Transient CameraAccessException (reason=${cae.reason}), retrying in ${backoffMs}ms...")
                mainHandler.postDelayed({
                    if (!isClosed && cameraGeneration.get() == sessionGen && textureView.isAvailable) {
                        openCameraAndStartSession(textureView, onSessionConfigured)
                    } else {
                        onSessionConfigured?.invoke(false, previewSize ?: Size(0, 0))
                    }
                }, backoffMs)
            } else {
                Log.e(TAG, "Failed to open camera (reason=${cae.reason}): ${cae.message}", cae)
                onSessionConfigured?.invoke(false, previewSize ?: Size(0, 0))
                mainHandler.post {
                    if (!isClosed && cameraGeneration.get() == sessionGen) {
                        onCameraUnavailable?.invoke()
                    }
                }
            }
        } catch (e: Throwable) {
            isOpening = false
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
            onSessionConfigured?.invoke(false, previewSize ?: Size(0, 0))
            mainHandler.post {
                if (!isClosed && cameraGeneration.get() == sessionGen) {
                    onCameraUnavailable?.invoke()
                }
            }
        }
    }

    /**
     * Configures a robust, smooth CameraCaptureSession on the preview surface.
     */
    private fun configureSession(
        camera: CameraDevice,
        surface: Surface,
        size: Size,
        characteristics: CameraCharacteristics,
        currentSharedCamera: SharedCamera?,
        sessionGen: Long,
        onSessionConfigured: ((Boolean, Size) -> Unit)?,
        fallbackStep: Int = 0
    ) {
        if (!surface.isValid) {
            Log.w(TAG, "Surface is invalid, aborting configureSession.")
            onSessionConfigured?.invoke(false, size)
            return
        }
        if (isClosed || cameraDevice == null || cameraGeneration.get() != sessionGen) {
            onSessionConfigured?.invoke(false, size)
            return
        }
        try {
            lowLightBoostEngine.checkHardwareSupport(characteristics)

            val forceSafeFallback = fallbackStep > 0
            val targetFpsRange = selectOptimalFpsRange(
                characteristics = characteristics,
                forceSafe30Fps = forceSafeFallback,
                matchArPreferred = currentSharedCamera != null && !forceSafeFallback
            )
            val targetAfMode = selectOptimalAfMode(characteristics)

            val template = if (currentSharedCamera != null && !forceSafeFallback) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }

            val requestBuilder = camera.createCaptureRequest(template).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)
                set(CaptureRequest.CONTROL_AF_MODE, targetAfMode)

                if (currentSharedCamera != null) {
                    // Keep deterministic CONTROL_AE_MODE_ON during SharedCamera AR tracking
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                } else {
                    // Edge enhancement mode if supported
                    val edgeModes = characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                    if (edgeModes?.contains(CaptureRequest.EDGE_MODE_FAST) == true) {
                        set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
                    }

                    // Noise reduction mode if supported
                    val noiseModes = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                    if (noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) == true) {
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                    }

                    // Apply Low Light Boost when operating standalone Camera2 preview
                    lowLightBoostEngine.applyLowLightBoost(this, isLowLightBoostEnabled)
                }

                // Restore torch state if active
                if (currentTorchState && isFlashSupported) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

            val surfaceList = ArrayList<Surface>()
            surfaceList.add(surface)
            isSharedCameraBoundToSession = false
            if (currentSharedCamera != null) {
                try {
                    currentSharedCamera.setAppSurfaces(activeCameraId ?: camera.id, listOf(surface))
                    val arSurfaces = currentSharedCamera.arCoreSurfaces
                    surfaceList.addAll(arSurfaces)
                    for (arSurface in arSurfaces) {
                        requestBuilder.addTarget(arSurface)
                    }
                    isSharedCameraBoundToSession = true
                    Log.i(TAG, "Configured SharedCamera (cam=${activeCameraId}, step=$fallbackStep) with ${arSurfaces.size} ARCore surfaces.")
                } catch (t: Throwable) {
                    Log.w(TAG, "Error binding SharedCamera surfaces: ${t.message}")
                }
            }
            previewRequestBuilder = requestBuilder
            arResumeTriggeredForSession.set(false)

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (isClosed || cameraGeneration.get() != sessionGen) {
                        try { session.close() } catch (_: Exception) {}
                        return
                    }
                    captureSession = session
                    isHighSpeedSessionActive = targetFpsRange.upper >= 60

                    try {
                        // Official SharedCameraActivity protocol: start repeating request while ARCore is still paused
                        // so CameraCaptureSession transitions to onActive(), where session.resume() + setCaptureCallback() run.
                        if (!isSharedCameraArActive()) {
                            session.setRepeatingRequest(
                                requestBuilder.build(),
                                captureCallback,
                                backgroundHandler
                            )
                        }
                        if (currentSharedCamera != null) {
                            Log.i(TAG, "CameraCaptureSession configured for SharedCamera! FPS=$targetFpsRange, Size=${size.width}x${size.height}")
                        } else {
                            Log.i(TAG, "CameraCaptureSession started! FPS=$targetFpsRange, Size=${size.width}x${size.height}")
                        }
                        onSessionConfigured?.invoke(isHighSpeedSessionActive, size)
                    } catch (e: Throwable) {
                        Log.e(TAG, "setRepeatingRequest error: ${e.message}", e)
                        onSessionConfigured?.invoke(isHighSpeedSessionActive, size)
                    }
                }

                override fun onActive(session: CameraCaptureSession) {
                    super.onActive(session)
                    if (!isClosed &&
                        cameraGeneration.get() == sessionGen &&
                        currentSharedCamera != null &&
                        isSharedCameraBoundToSession &&
                        arResumeTriggeredForSession.compareAndSet(false, true)
                    ) {
                        Log.i(TAG, "CaptureSession onActive -> resuming ARCore Session and binding capture callback")
                        onArSessionReadyToResume?.invoke(captureCallback, backgroundHandler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "CameraCaptureSession configuration failed (step=$fallbackStep, shared=${currentSharedCamera != null})")
                    try { session.close() } catch (_: Exception) {}
                    if (captureSession == session) {
                        captureSession = null
                    }
                    if (!isClosed && cameraGeneration.get() == sessionGen && fallbackStep == 0 && cameraDevice == camera && surface.isValid) {
                        Log.w(TAG, "Retrying CameraCaptureSession with safe 30fps preview fallback...")
                        backgroundHandler?.post {
                            configureSession(
                                camera = camera,
                                surface = surface,
                                size = size,
                                characteristics = characteristics,
                                currentSharedCamera = currentSharedCamera,
                                sessionGen = sessionGen,
                                onSessionConfigured = onSessionConfigured,
                                fallbackStep = 1
                            )
                        }
                        return
                    }
                    onSessionConfigured?.invoke(false, size)
                }
            }

            val wrappedSessionCallback = currentSharedCamera?.createARSessionStateCallback(stateCallback, backgroundHandler) ?: stateCallback

            val supportsPreviewUseCase = if (currentSharedCamera == null && !forceSafeFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val useCases = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES)
                useCases?.contains(CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()) == true
            } else false

            val outputConfigs = surfaceList.mapIndexed { idx, s ->
                OutputConfiguration(s).apply {
                    if (idx == 0 && supportsPreviewUseCase && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            streamUseCase = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()
                        } catch (_: Throwable) {}
                    }
                }
            }
            val executor = java.util.concurrent.Executor { command ->
                val handler = backgroundHandler
                if (!isClosed && handler != null && cameraGeneration.get() == sessionGen) {
                    handler.post(command)
                }
            }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                executor,
                wrappedSessionCallback
            )
            camera.createCaptureSession(sessionConfig)

        } catch (e: Throwable) {
            Log.e(TAG, "configureSession failed: ${e.message}", e)
            onSessionConfigured?.invoke(false, size)
        }
    }

    fun reconnectWithSharedCamera(
        sharedCam: SharedCamera,
        cameraId: String? = null,
        imageSize: Size? = null,
        fpsRange: Range<Int>? = null,
        onBeforeCloseCamera: (() -> Unit)? = null,
        onConfigured: ((Boolean, Size) -> Unit)? = null
    ) {
        val aspect = if (imageSize != null && imageSize.height > 0) {
            imageSize.width.toFloat() / imageSize.height.toFloat()
        } else null
        if (sharedCamera == sharedCam &&
            isSharedCameraBoundToSession &&
            preferredCameraId == cameraId &&
            preferredFpsRange == fpsRange &&
            (captureSession != null || isOpening) &&
            !isClosed
        ) {
            Log.i(TAG, "Camera already active or opening with this SharedCamera instance.")
            return
        }
        sharedCamera = sharedCam
        preferredCameraId = cameraId
        preferredAspectRatio = aspect
        preferredFpsRange = fpsRange
        val tv = activeTextureView?.get() ?: return
        if (tv.isAvailable && preferredFacing == CameraCharacteristics.LENS_FACING_BACK) {
            onBeforeCloseCamera?.invoke()
            closeCamera()
            val expectedGen = cameraGeneration.get()
            mainHandler.postDelayed({
                if (cameraGeneration.get() == expectedGen && tv.isAvailable) {
                    openCameraAndStartSession(tv, onConfigured)
                }
            }, 120L)
        }
    }

    /**
     * Configures the transformation matrix on TextureView to prevent distortion
     * and stretch, adapting preview aspect ratio to match the camera sensor.
     * Follows official Android Camera2 rotation and aspect ratio compensation guidelines.
     */
    fun configureTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        }
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val matrix = android.graphics.Matrix()
        val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = android.graphics.RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / size.height,
                viewWidth.toFloat() / size.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else {
            // Surface.ROTATION_0 and Surface.ROTATION_180 (Portrait orientations):
            // Camera sensor buffer is landscape (e.g. 1280x720).
            // When oriented in portrait, the effective aspect ratio is (size.height : size.width), e.g. 720:1280 (9:16).
            // Scale TextureView with center-crop so the buffer fills the view without aspect ratio distortion or stretching.
            val bufferAspect = size.height.toFloat() / size.width.toFloat()
            val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

            val scaleX: Float
            val scaleY: Float
            if (viewAspect > bufferAspect) {
                // View is wider than camera buffer: match width, expand height
                scaleX = 1f
                scaleY = (viewWidth.toFloat() / bufferAspect) / viewHeight.toFloat()
            } else {
                // View is taller than camera buffer: match height, expand width
                scaleX = (viewHeight.toFloat() * bufferAspect) / viewWidth.toFloat()
                scaleY = 1f
            }
            matrix.setScale(scaleX, scaleY, centerX, centerY)

            if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180f, centerX, centerY)
            }
        }

        // Horizontal Mirroring correction (for front-facing selfie mirror or manual toggle)
        val shouldMirror = isHorizontallyMirrored || isFrontFacing
        if (shouldMirror) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }

        textureView.setTransform(matrix)
    }

    private fun countFrameAndCalculateFps() {
        val now = System.nanoTime()
        frameCounter++
        if (lastFpsTimestampNs == 0L) {
            lastFpsTimestampNs = now
        } else {
            val elapsed = now - lastFpsTimestampNs
            if (elapsed >= 1_000_000_000L) { // 1 second
                val calculatedFps = (frameCounter * 1_000_000_000.0 / elapsed).toInt()
                frameCounter = 0
                lastFpsTimestampNs = now
                onFrameFpsUpdated?.invoke(calculatedFps)
            }
        }
    }

    fun toggleTorch(enable: Boolean): Boolean {
        currentTorchState = enable
        if (enable && !isFlashSupported) {
            Log.w(TAG, "Hardware flash is not supported on this camera device.")
            return false
        }
        if (isSharedCameraArActive()) {
            // Handled via ModernArEngine.setTorchMode while ARCore SharedCamera owns repeating requests
            return false
        }
        val session = captureSession ?: return false
        val builder = previewRequestBuilder ?: return false
        return try {
            if (enable && isFlashSupported) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle torch: ${e.message}")
            false
        }
    }

    fun closeCamera() {
        cameraGeneration.incrementAndGet()
        isClosed = true
        isOpening = false
        isSharedCameraBoundToSession = false
        arResumeTriggeredForSession.set(false)
        isSwitchingCamera.set(false)
        openRetryCount = 0
        mainHandler.removeCallbacksAndMessages(null)
        try {
            backgroundHandler?.removeCallbacksAndMessages(null)
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
            previewRequestBuilder = null
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing camera: ${e.message}")
        } finally {
            stopBackgroundThread()
        }
    }

    companion object {
        private const val TAG = "HighSpeedCamera2"
    }
}
