package com.example.logic.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView

/**
 * High-performance Camera2 Direct Manager supporting:
 * 1. ConstrainedHighSpeedCaptureSession (dedicated 60/120 FPS hardware pipeline).
 * 2. Optimal resolution step-down (1080p / 720p) to satisfy HAL bandwidth constraints.
 * 3. AE / Night / HDR algorithm clamping (CONTROL_SCENE_MODE_DISABLED) preventing frame-drop in dim light.
 * 4. Graceful fallback to Standard 60 FPS Camera2 Session when High Speed is unsupported by HAL.
 */
class HighSpeedCamera2Manager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentTorchState = false
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var isHighSpeedSessionActive = false

    var onFrameFpsUpdated: ((Int) -> Unit)? = null
    private var frameCounter = 0
    private var lastFpsTimestampNs = 0L

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("HighSpeedCameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    /**
     * Choose optimal 60 FPS resolution (1080p or 720p) within High Speed / Standard map limits.
     */
    fun selectOptimal60FpsSize(characteristics: CameraCharacteristics): Pair<Size, Boolean> {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return Pair(Size(1920, 1080), false)
        
        // Check High Speed Video Sizes for [60, 60]
        try {
            val highSpeedRanges = map.highSpeedVideoFpsRanges
            val has60FpsHighSpeed = highSpeedRanges?.any { it.upper >= 60 && it.lower >= 30 } == true
            if (has60FpsHighSpeed) {
                val targetRange = highSpeedRanges.firstOrNull { it.lower == 60 && it.upper == 60 }
                    ?: highSpeedRanges.firstOrNull { it.upper >= 60 }
                if (targetRange != null) {
                    val sizes = map.getHighSpeedVideoSizesFor(targetRange)
                    if (!sizes.isNullOrEmpty()) {
                        // Prefer 1080p, then 720p
                        val chosen = sizes.firstOrNull { it.width == 1920 && it.height == 1080 }
                            ?: sizes.firstOrNull { it.width == 1280 && it.height == 720 }
                            ?: sizes.first()
                        Log.i(TAG, "Found High-Speed 60 FPS Size: ${chosen.width}x${chosen.height} for range $targetRange")
                        return Pair(chosen, true)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking high speed video sizes: ${e.message}")
        }

        // Standard 60 FPS fallback: Pick 1080p or 720p from SurfaceTexture output sizes
        val outputSizes = map.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        val preferred1080p = outputSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
        val preferred720p = outputSizes.firstOrNull { it.width == 1280 && it.height == 720 }
        val chosen = preferred1080p ?: preferred720p ?: outputSizes.firstOrNull() ?: Size(1920, 1080)
        Log.i(TAG, "Using Standard 60 FPS Size: ${chosen.width}x${chosen.height}")
        return Pair(chosen, false)
    }

    @SuppressLint("MissingPermission")
    fun openCameraAndStartSession(
        textureView: TextureView,
        onSessionConfigured: ((Boolean, Size) -> Unit)? = null
    ) {
        startBackgroundThread()

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: run {
                Log.e(TAG, "No suitable camera found")
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val (optimalSize, isHighSpeedSupported) = selectOptimal60FpsSize(characteristics)

            // Step 2: Set buffer size to 1080p / 720p (never 4K / max texture size)
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(optimalSize.width, optimalSize.height)
            val surface = Surface(texture)
            previewSurface = surface

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    if (isHighSpeedSupported) {
                        tryConfigureHighSpeedSession(camera, surface, optimalSize, characteristics, onSessionConfigured)
                    } else {
                        configureStandard60FpsSession(camera, surface, optimalSize, characteristics, onSessionConfigured)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
        }
    }

    /**
     * Step 1: ConstrainedHighSpeedCaptureSession Pipeline
     */
    private fun tryConfigureHighSpeedSession(
        camera: CameraDevice,
        surface: Surface,
        size: Size,
        characteristics: CameraCharacteristics,
        onSessionConfigured: ((Boolean, Size) -> Unit)?
    ) {
        try {
            // High speed capture requests typically use TEMPLATE_RECORD
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)

                // Enforce 60 FPS range
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(60, 60))

                // Clamping AE / Night / HDR algorithm to prevent frame dropping to 30 FPS in dim light
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            previewRequestBuilder = requestBuilder

            camera.createConstrainedHighSpeedCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        isHighSpeedSessionActive = true
                        try {
                            val highSpeedSession = session as CameraConstrainedHighSpeedCaptureSession
                            val highSpeedRequestList = highSpeedSession.createHighSpeedRequestList(requestBuilder.build())
                            highSpeedSession.setRepeatingBurst(
                                highSpeedRequestList,
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        countFrameAndCalculateFps()
                                    }
                                },
                                backgroundHandler
                            )
                            Log.i(TAG, "ConstrainedHighSpeedCaptureSession successfully configured at 60 FPS!")
                            onSessionConfigured?.invoke(true, size)
                        } catch (e: Throwable) {
                            Log.w(TAG, "setRepeatingBurst high speed failed, falling back: ${e.message}")
                            configureStandard60FpsSession(camera, surface, size, characteristics, onSessionConfigured)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.w(TAG, "High Speed Session config failed, falling back to Standard 60 FPS Session")
                        configureStandard60FpsSession(camera, surface, size, characteristics, onSessionConfigured)
                    }
                },
                backgroundHandler
            )
        } catch (e: Throwable) {
            Log.w(TAG, "createConstrainedHighSpeedCaptureSession failed: ${e.message}, falling back")
            configureStandard60FpsSession(camera, surface, size, characteristics, onSessionConfigured)
        }
    }

    /**
     * Standard 60 FPS Session with AE Scene Mode Off and Strict 60 FPS Target
     */
    private fun configureStandard60FpsSession(
        camera: CameraDevice,
        surface: Surface,
        size: Size,
        characteristics: CameraCharacteristics,
        onSessionConfigured: ((Boolean, Size) -> Unit)?
    ) {
        try {
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val selectedFpsRange = fpsRanges?.firstOrNull { it.lower == 60 && it.upper == 60 }
                ?: fpsRanges?.firstOrNull { it.upper >= 60 && it.lower >= 30 }
                ?: Range(60, 60)

            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedFpsRange)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                // Disable scene mode / night mode auto-stretching exposure
                set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // Set exposure time clamp (16.6ms max) to ensure 60 FPS cadence
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 60L)
            }
            previewRequestBuilder = requestBuilder

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        isHighSpeedSessionActive = false
                        try {
                            session.setRepeatingRequest(
                                requestBuilder.build(),
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        countFrameAndCalculateFps()
                                    }
                                },
                                backgroundHandler
                            )
                            Log.i(TAG, "Standard 60 FPS Session configured with range $selectedFpsRange")
                            onSessionConfigured?.invoke(false, size)
                        } catch (e: Throwable) {
                            Log.e(TAG, "setRepeatingRequest failed: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Standard CameraCaptureSession configure failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Throwable) {
            Log.e(TAG, "configureStandard60FpsSession failed: ${e.message}", e)
        }
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
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            if (enable) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            if (isHighSpeedSessionActive && session is CameraConstrainedHighSpeedCaptureSession) {
                val list = session.createHighSpeedRequestList(builder.build())
                session.setRepeatingBurst(list, null, backgroundHandler)
            } else {
                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle torch: ${e.message}")
        }
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
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
