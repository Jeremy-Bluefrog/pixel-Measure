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
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    var sharedCamera: SharedCamera? = null
    private var activeTextureView: WeakReference<TextureView>? = null

    private var currentTorchState = false
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var isHighSpeedSessionActive = false

    var onFrameFpsUpdated: ((Int) -> Unit)? = null
    private var frameCounter = 0
    private var lastFpsTimestampNs = 0L

    private var isClosed = false
    private var openRetryCount = 0
    private var activeCameraId: String? = null
    @Volatile
    private var isOpening = false
    private var isFlashSupported = false
    var previewSize: Size? = null
        private set

    var preferredFacing: Int = CameraCharacteristics.LENS_FACING_BACK
        private set
    var isFrontFacing: Boolean = false
        private set
    var sensorOrientation: Int = 90
        private set
    var isHorizontallyMirrored: Boolean = false
        private set

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
        closeCamera()

        // Allow HAL hardware pipe 120ms to completely tear down previous camera instance
        val mainHandler = Handler(android.os.Looper.getMainLooper())
        mainHandler.postDelayed({
            try {
                if (textureView.isAvailable) {
                    openCameraAndStartSession(textureView) { success, size ->
                        isSwitchingCamera.set(false)
                        onSessionConfigured?.invoke(success, size)
                    }
                } else {
                    isSwitchingCamera.set(false)
                }
            } catch (e: Throwable) {
                isSwitchingCamera.set(false)
            }
        }, 120L)
    }

    val lowLightBoostEngine = LowLightBoostEngine(context)
    private var isLowLightBoostEnabled = true

    fun setLowLightBoostEnabled(enabled: Boolean = true) {
        isLowLightBoostEnabled = enabled
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
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("HighSpeedCameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        try {
            backgroundHandler?.removeCallbacksAndMessages(null)
            backgroundThread?.quitSafely()
            backgroundThread?.join(300)
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
    fun selectOptimalPreviewSize(characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

        if (outputSizes.isEmpty()) {
            return Size(1280, 720)
        }

        // Prioritize 720p (1280x720) for optimal 60 FPS performance and reduced GPU/CPU memory overhead
        return outputSizes.firstOrNull { it.width == 1280 && it.height == 720 }
            ?: outputSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
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
     * Safely queries supported AE target FPS ranges to prevent IllegalArgumentException.
     */
    private fun selectOptimalFpsRange(characteristics: CameraCharacteristics): Range<Int> {
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()

        // 1. Fixed 60 FPS: [60, 60] or higher for maximum hardware fluidity
        fpsRanges.firstOrNull { it.lower >= 60 && it.upper >= 60 }?.let { return it }

        // 2. Dynamic 30-60 FPS: maintains at least 30 FPS while allowing silky 60 FPS
        fpsRanges.firstOrNull { it.lower >= 30 && it.upper >= 60 }?.let { return it }

        // 3. Dynamic wide range up to 60 (e.g., [15, 60], [24, 60])
        fpsRanges.firstOrNull { it.upper >= 60 }?.let { return it }

        // 4. Standard smooth [30, 30]
        fpsRanges.firstOrNull { it.lower == 30 && it.upper == 30 }?.let { return it }

        // 5. Any range with upper >= 30
        fpsRanges.firstOrNull { it.upper >= 30 }?.let { return it }

        // 6. Default to first hardware reported range or standard [30, 30]
        return fpsRanges.firstOrNull() ?: Range(30, 30)
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
            lowLightBoostEngine.onCaptureResult(result)
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
        startBackgroundThread()

        try {
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                Log.e(TAG, "No camera available on device")
                isOpening = false
                return
            }

            // Select camera according to preferred facing (Back vs Front)
            val targetFacing = preferredFacing
            val cameraId = cameraIdList.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
                } catch (_: Throwable) { false }
            } ?: cameraIdList.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK
                } catch (_: Throwable) { false }
            } ?: cameraIdList.first()

            activeCameraId = cameraId
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            isFrontFacing = facing == CameraCharacteristics.LENS_FACING_FRONT
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val optimalSize = selectOptimalPreviewSize(characteristics)
            previewSize = optimalSize

            val texture = textureView.surfaceTexture
            if (texture == null) {
                Log.w(TAG, "TextureView surfaceTexture is null, waiting for surface availability.")
                isOpening = false
                return
            }

            activeTextureView = WeakReference(textureView)

            // Configure default buffer size matching hardware camera output
            texture.setDefaultBufferSize(optimalSize.width, optimalSize.height)
            previewSurface?.release()
            val surface = Surface(texture)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    surface.setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (_: Throwable) {}
            }
            previewSurface = surface

            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isOpening = false
                    if (isClosed) {
                        try { camera.close() } catch (_: Exception) {}
                        return
                    }
                    cameraDevice = camera
                    openRetryCount = 0
                    configureSession(camera, surface, optimalSize, characteristics, onSessionConfigured)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isOpening = false
                    Log.i(TAG, "Camera disconnected")
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isOpening = false
                    Log.e(TAG, "Camera device error: $error (retry=$openRetryCount)")
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }

                    if (!isClosed && openRetryCount < 2) {
                        openRetryCount++
                        val backoffMs = 250L * openRetryCount
                        backgroundHandler?.postDelayed({
                            if (!isClosed && textureView.isAvailable) {
                                openCameraAndStartSession(textureView, onSessionConfigured)
                            }
                        }, backoffMs)
                    }
                }
            }

            val wrappedCallback = sharedCamera?.createARDeviceStateCallback(stateCallback, backgroundHandler) ?: stateCallback
            cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler)

        } catch (e: Throwable) {
            isOpening = false
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
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
        onSessionConfigured: ((Boolean, Size) -> Unit)?
    ) {
        if (!surface.isValid) {
            Log.w(TAG, "Surface is invalid, aborting configureSession.")
            return
        }
        if (isClosed || cameraDevice == null) {
            return
        }
        try {
            lowLightBoostEngine.checkHardwareSupport(characteristics)

            val targetFpsRange = selectOptimalFpsRange(characteristics)
            val targetAfMode = selectOptimalAfMode(characteristics)

            val currentSharedCamera = sharedCamera
            val template = if (currentSharedCamera != null) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }

            val requestBuilder = camera.createCaptureRequest(template).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)
                set(CaptureRequest.CONTROL_AF_MODE, targetAfMode)

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

                // Apply Low Light Boost if enabled
                lowLightBoostEngine.applyLowLightBoost(this, isLowLightBoostEnabled)

                // Restore torch state if active
                if (currentTorchState && isFlashSupported) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

            val surfaceList = ArrayList<Surface>()
            surfaceList.add(surface)
            if (currentSharedCamera != null) {
                try {
                    val arSurfaces = currentSharedCamera.arCoreSurfaces
                    surfaceList.addAll(arSurfaces)
                    for (arSurface in arSurfaces) {
                        requestBuilder.addTarget(arSurface)
                    }
                    currentSharedCamera.setAppSurfaces(activeCameraId ?: "0", listOf(surface))
                    Log.i(TAG, "Configured SharedCamera with ${arSurfaces.size} ARCore surfaces added to capture request.")
                } catch (t: Throwable) {
                    Log.w(TAG, "Error binding SharedCamera surfaces: ${t.message}")
                }
            }
            previewRequestBuilder = requestBuilder

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (isClosed) {
                        try { session.close() } catch (_: Exception) {}
                        return
                    }
                    captureSession = session
                    isHighSpeedSessionActive = targetFpsRange.upper >= 60

                    try {
                        session.setRepeatingRequest(
                            requestBuilder.build(),
                            captureCallback,
                            backgroundHandler
                        )
                        if (currentSharedCamera != null) {
                            try {
                                currentSharedCamera.setCaptureCallback(captureCallback, backgroundHandler)
                            } catch (cbEx: Throwable) {
                                Log.w(TAG, "SharedCamera setCaptureCallback note: ${cbEx.message}")
                            }
                            Log.i(TAG, "CameraCaptureSession started with SharedCamera! FPS Range: $targetFpsRange, Size: ${size.width}x${size.height}")
                        } else {
                            Log.i(TAG, "CameraCaptureSession successfully started! FPS Range: $targetFpsRange, Size: ${size.width}x${size.height}")
                        }
                        onSessionConfigured?.invoke(isHighSpeedSessionActive, size)
                    } catch (e: Throwable) {
                        Log.e(TAG, "setRepeatingRequest error: ${e.message}", e)
                        onSessionConfigured?.invoke(isHighSpeedSessionActive, size)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "CameraCaptureSession configuration failed")
                    try { session.close() } catch (_: Exception) {}
                    if (captureSession == session) {
                        captureSession = null
                    }
                    onSessionConfigured?.invoke(false, size)
                }
            }

            val wrappedSessionCallback = currentSharedCamera?.createARSessionStateCallback(stateCallback, backgroundHandler) ?: stateCallback

            // API 28+ SessionConfiguration with safe fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val outputConfigs = surfaceList.map { OutputConfiguration(it) }
                    val executor = java.util.concurrent.Executor { command ->
                        backgroundHandler?.post(command) ?: command.run()
                    }
                    val sessionConfig = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigs,
                        executor,
                        wrappedSessionCallback
                    )
                    camera.createCaptureSession(sessionConfig)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "SessionConfiguration failed, falling back to legacy createCaptureSession: ${t.message}")
                }
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaceList, wrappedSessionCallback, backgroundHandler)

        } catch (e: Throwable) {
            Log.e(TAG, "configureSession failed: ${e.message}", e)
        }
    }

    fun reconnectWithSharedCamera(sharedCam: SharedCamera, onConfigured: ((Boolean, Size) -> Unit)? = null) {
        if (sharedCamera == sharedCam && captureSession != null && !isClosed) {
            Log.i(TAG, "Camera already active with this SharedCamera instance.")
            return
        }
        sharedCamera = sharedCam
        val tv = activeTextureView?.get() ?: return
        if (tv.isAvailable) {
            closeCamera()
            val mainHandler = Handler(android.os.Looper.getMainLooper())
            mainHandler.postDelayed({
                if (tv.isAvailable) {
                    openCameraAndStartSession(tv, onConfigured)
                }
            }, 120L)
        }
    }

    /**
     * Configures the transformation matrix on TextureView to prevent distortion
     * and stretch, adapting preview aspect ratio to match the camera sensor.
     */
    fun configureTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return
        if (viewWidth == 0 || viewHeight == 0) return

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        }
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val matrix = android.graphics.Matrix()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // Camera2 preview buffer dimensions (e.g. 1280x720)
        // In natural portrait orientation, the buffer effectively has short edge as width, long edge as height
        val bufferW = minOf(size.width, size.height).toFloat() // e.g. 720f
        val bufferH = maxOf(size.width, size.height).toFloat() // e.g. 1280f

        when (rotation) {
            Surface.ROTATION_0 -> {
                // Natural Portrait orientation:
                // TextureView automatically displays the sensor buffer aligned to the device.
                // Apply Center-Crop scale to fill view without stretching or aspect distortion.
                val scale = maxOf(viewWidth / bufferW, viewHeight / bufferH)
                val scaleX = scale * (bufferW / viewWidth)
                val scaleY = scale * (bufferH / viewHeight)
                matrix.setScale(scaleX, scaleY, centerX, centerY)
            }
            Surface.ROTATION_180 -> {
                val scale = maxOf(viewWidth / bufferW, viewHeight / bufferH)
                val scaleX = scale * (bufferW / viewWidth)
                val scaleY = scale * (bufferH / viewHeight)
                matrix.setScale(scaleX, scaleY, centerX, centerY)
                matrix.postRotate(180f, centerX, centerY)
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                // Landscape orientations: buffer width corresponds to view width, buffer height to view height
                val bufferRect = android.graphics.RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
                val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
                matrix.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL)
                val scale = maxOf(
                    viewHeight.toFloat() / size.height,
                    viewWidth.toFloat() / size.width
                )
                matrix.postScale(scale, scale, centerX, centerY)
                matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
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

    fun toggleTorch(enable: Boolean) {
        currentTorchState = enable
        if (enable && !isFlashSupported) {
            Log.w(TAG, "Hardware flash is not supported on this camera device.")
            return
        }
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            if (enable && isFlashSupported) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle torch: ${e.message}")
        }
    }

    fun closeCamera() {
        isClosed = true
        isOpening = false
        openRetryCount = 0
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
