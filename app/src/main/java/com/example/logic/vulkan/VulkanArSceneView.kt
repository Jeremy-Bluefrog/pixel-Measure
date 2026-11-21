package com.example.logic.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.logic.TranslationManager
import com.example.logic.ar.ModernArEngine
import com.example.ui.viewmodel.MeasureViewModel
import com.google.ar.core.Session

/**
 * Pure Vulkan AR Scene View.
 * Completely replaces legacy OpenGL ES GLSurfaceView and ArGlRenderer with:
 * 1. Zero OpenGL ES dependencies (no GLES20, no EGL, no GLSurfaceView)
 * 2. Vulkan 1.4 compliance with Pixel SurfaceFlinger 60Hz lock
 * 3. AHardwareBuffer zero-copy hardware buffer pipe
 * 4. Choreographer 60fps frame loop syncing ARCore updates with Compose
 */
class VulkanArSceneView(
    context: Context,
    val session: Session? = null,
    val modernArEngine: ModernArEngine? = null,
    val viewModel: MeasureViewModel? = null
) : SurfaceView(context), SurfaceHolder.Callback, Choreographer.FrameCallback {

    companion object {
        private const val TAG = "VulkanArSceneView"
    }

    // Deterministic Lifecycle State Machine & Synchronization
    private val _renderState = java.util.concurrent.atomic.AtomicReference(VulkanRenderState.UNINITIALIZED)
    val renderState: VulkanRenderState get() = _renderState.get()

    private val renderLifecycleLock = Any()
    @Volatile
    private var isDestroyed = false

    @Volatile
    private var isRendering = false
    private var renderThread: HandlerThread? = null
    @Volatile
    private var renderHandler: Handler? = null
    @Volatile
    private var surfaceWidth = 0
    @Volatile
    private var surfaceHeight = 0

    @Volatile
    private var targetFps: Float = 60.0f
    @Volatile
    private var minFrameIntervalNs: Long = 15_500_000L
    @Volatile
    private var lastDispatchedDoFrameNs: Long = 0L
    @Volatile
    private var lastArFrameTimestampNs: Long = 0L

    // Android ADPF PerformanceHintManager session for Pixel Tensor GPU/CPU DVFS clock stability
    @Volatile
    private var adpfHintSession: PixelGpuHintSession? = null

    // Frame synchronization & watchdog
    val frameSyncManager = VulkanFrameSyncManager(maxFramesInFlight = 2)

    // Zero-copy HardwareBuffer manager for direct Vulkan memory sharing
    val hardwareBufferManager = VulkanHardwareBufferManager()

    init {
        holder.addCallback(this)
        frameSyncManager.onWatchdogTriggered = {
            handleWatchdogTimeoutRecovery()
        }
        applySurfaceFrameRate(targetFps)
    }

    fun setTargetFrameRate(fps: Float) {
        val clamped = when {
            fps <= 35f -> 30.0f
            fps <= 75f -> 60.0f
            else -> 120.0f
        }
        if (targetFps == clamped) return
        targetFps = clamped
        minFrameIntervalNs = when {
            clamped <= 30.0f -> 31_000_000L
            clamped <= 60.0f -> 15_500_000L
            else -> 7_800_000L // ~120Hz LTPO Smooth Display cadence
        }
        applySurfaceFrameRate(clamped)
        renderHandler?.post {
            adpfHintSession?.updateTargetFps(clamped.toInt())
        }
    }

    private fun isSurfaceReady(): Boolean {
        return try {
            holder.surface?.isValid == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun applySurfaceFrameRate(fps: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                holder.surface?.let { surf ->
                    if (surf.isValid) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            surf.setFrameRate(
                                fps,
                                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                            )
                        } else {
                            surf.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Frame rate lock notification: ${t.message}")
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "Vulkan Surface created. Transitioning to SURFACE_ATTACHED.")
        isDestroyed = false
        _renderState.set(VulkanRenderState.SURFACE_ATTACHED)
        applySurfaceFrameRate(targetFps)
        startRenderLoop()
    }

    private fun getCurrentDisplayRotation(): Int {
        val attachedDisplay = runCatching { this.display }.getOrNull()
        val display = attachedDisplay ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull()
                ?: @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        }
        return display?.rotation ?: Surface.ROTATION_0
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width >= 1 && height >= 1) {
            surfaceWidth = width
            surfaceHeight = height
            _renderState.set(VulkanRenderState.SWAPCHAIN_CONFIGURED)
            Log.i(TAG, "Vulkan Swapchain configured: ${width}x${height}")

            applySurfaceFrameRate(targetFps)
            if (width > 100 && height > 100) {
                val rot = getCurrentDisplayRotation()
                modernArEngine?.setDisplayGeometry(rot, width, height)
                viewModel?.updateDisplayGeometry(rot, width, height)
            }

            if (isRendering) {
                _renderState.set(VulkanRenderState.ACTIVE_RENDER_LOOP)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "Vulkan Surface destroyed. Transitioning to SAFE_SUSPENDED.")
        _renderState.set(VulkanRenderState.SAFE_SUSPENDED)
        stopRenderLoop()
    }

    fun onResume() {
        if (isDestroyed) return
        if (isSurfaceReady()) {
            if (_renderState.get() == VulkanRenderState.SAFE_SUSPENDED) {
                _renderState.set(
                    if (surfaceWidth > 0 && surfaceHeight > 0) VulkanRenderState.SWAPCHAIN_CONFIGURED
                    else VulkanRenderState.SURFACE_ATTACHED
                )
            }
            startRenderLoop()
        }
    }

    fun onPause() {
        _renderState.set(VulkanRenderState.SAFE_SUSPENDED)
        stopRenderLoop()
    }

    private val isFrameScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    private val frameProcessRunnable = Runnable {
        try {
            processVulkanFrame()
        } finally {
            isFrameScheduled.set(false)
        }
    }

    private fun startRenderLoop() {
        synchronized(renderLifecycleLock) {
            if (isRendering || isDestroyed) return
            isRendering = true
            isFrameScheduled.set(false)
            lastDispatchedDoFrameNs = 0L
            lastArFrameTimestampNs = 0L
            _renderState.set(VulkanRenderState.ACTIVE_RENDER_LOOP)

            val thread = HandlerThread("VulkanRenderThread", android.os.Process.THREAD_PRIORITY_DISPLAY).apply {
                start()
            }
            val h = Handler(thread.looper)
            renderThread = thread
            renderHandler = h
            h.post {
                if (!isDestroyed && isRendering && adpfHintSession == null) {
                    adpfHintSession = PixelGpuHintSession.createForCurrentThread(context, targetFps.toInt())
                }
            }
        }

        try {
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().postFrameCallback(this)
        } catch (_: Throwable) {}
    }

    private fun stopRenderLoop() {
        val threadToJoin: HandlerThread?
        val handlerToClear: Handler?
        synchronized(renderLifecycleLock) {
            isRendering = false
            isFrameScheduled.set(false)
            lastDispatchedDoFrameNs = 0L
            lastArFrameTimestampNs = 0L
            _renderState.set(VulkanRenderState.SAFE_SUSPENDED)
            threadToJoin = renderThread
            handlerToClear = renderHandler
            renderThread = null
            renderHandler = null
        }

        try {
            Choreographer.getInstance().removeFrameCallback(this)
        } catch (_: Throwable) {}

        handlerToClear?.removeCallbacks(frameProcessRunnable)
        handlerToClear?.post {
            adpfHintSession?.close()
            adpfHintSession = null
            modernArEngine?.detachGlContext()
        }

        // Enforce synchronous GPU idle wait before destroying window context
        frameSyncManager.waitAllFencesIdle(timeoutMs = 150L)

        if (threadToJoin != null) {
            threadToJoin.quitSafely()
            if (Thread.currentThread() !== threadToJoin) {
                try {
                    threadToJoin.join(250)
                } catch (_: InterruptedException) {}
            }
        }

        frameSyncManager.reset()
    }

    private fun handleWatchdogTimeoutRecovery() {
        Log.w(TAG, "Watchdog triggered pipeline rebuild.")
        val currentState = _renderState.get()
        if (isDestroyed || !isRendering || currentState == VulkanRenderState.SAFE_SUSPENDED) {
            frameSyncManager.reset()
            return
        }
        _renderState.set(VulkanRenderState.REBUILDING_PIPELINE)
        isFrameScheduled.set(false)
        lastDispatchedDoFrameNs = 0L
        val h = renderHandler
        if (h == null) {
            frameSyncManager.reset()
            _renderState.set(VulkanRenderState.SAFE_SUSPENDED)
            return
        }
        h.post {
            frameSyncManager.reset()
            modernArEngine?.detachGlContext()
            if (!isDestroyed && isRendering && isSurfaceReady() && surfaceWidth > 0 && surfaceHeight > 0) {
                _renderState.set(VulkanRenderState.SWAPCHAIN_CONFIGURED)
                _renderState.set(VulkanRenderState.ACTIVE_RENDER_LOOP)
            } else {
                _renderState.set(VulkanRenderState.SAFE_SUSPENDED)
            }
        }
    }

    /**
     * Complete lifecycle destruction: stops render loop, releases hardware buffers, and frees thread resources.
     */
    fun destroy() {
        isDestroyed = true
        stopRenderLoop()
        hardwareBufferManager.release()
        frameSyncManager.reset()
        _renderState.set(VulkanRenderState.UNINITIALIZED)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (isDestroyed || !isRendering || !_renderState.get().canRender) return

        // Throttle 90Hz/120Hz Pixel Smooth Display Choreographer callbacks to target AR cadence (30/60Hz)
        // and coalesce pending runnables so slow frames never cause queue buildup or fence timeouts.
        if ((lastDispatchedDoFrameNs == 0L || (frameTimeNanos - lastDispatchedDoFrameNs) >= minFrameIntervalNs) &&
            isFrameScheduled.compareAndSet(false, true)
        ) {
            lastDispatchedDoFrameNs = frameTimeNanos
            val posted = renderHandler?.post(frameProcessRunnable) ?: false
            if (!posted) {
                isFrameScheduled.set(false)
            }
        }

        if (!isDestroyed && isRendering && _renderState.get().canRender) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private var logThrottleCounter = 0

    private fun processVulkanFrame() {
        if (isDestroyed || !isRendering || !isSurfaceReady() || !_renderState.get().canRender) {
            return
        }

        val slot = frameSyncManager.acquireNextFrameSlot(timeoutMs = 50L)
        if (slot < 0) {
            // Drop frame gracefully when GPU is currently rendering
            return
        }

        val frameWorkStartNs = System.nanoTime()
        try {
            val arEngine = modernArEngine ?: return
            if (!arEngine.isSessionResumed) return

            arEngine.withLockedSessionUpdate(fallbackSession = session) { activeSession ->
                arEngine.updateDisplayGeometryIfNeeded(activeSession)
                if (arEngine.isExposeHardwareBufferActive) {
                    val frame = activeSession.update()
                    logThrottleCounter = 0
                    val frameTimestamp = frame.timestamp
                    if (frameTimestamp == 0L) return@withLockedSessionUpdate
                    if (frameTimestamp == lastArFrameTimestampNs) {
                        val hasPendingTap = viewModel?.hasPendingTapRequest() == true
                        if (!hasPendingTap) return@withLockedSessionUpdate
                    }
                    lastArFrameTimestampNs = frameTimestamp
                    viewModel?.processVulkanFrame(frame, arEngine)
                } else if (arEngine.makeGlContextCurrent()) {
                    try {
                        val frame = activeSession.update()
                        logThrottleCounter = 0
                        val frameTimestamp = frame.timestamp
                        if (frameTimestamp == 0L) return@withLockedSessionUpdate
                        if (frameTimestamp == lastArFrameTimestampNs) {
                            // Skip duplicate camera frame on higher-refresh VSync ticks unless tap is pending
                            val hasPendingTap = viewModel?.hasPendingTapRequest() == true
                            if (!hasPendingTap) return@withLockedSessionUpdate
                        }
                        lastArFrameTimestampNs = frameTimestamp
                        viewModel?.processVulkanFrame(frame, arEngine)
                    } finally {
                        arEngine.detachGlContext()
                    }
                } else {
                    if (logThrottleCounter++ % 60 == 0) {
                        Log.w(TAG, "GL context unavailable on render thread, attempting texture rebind")
                        arEngine.rebindCameraTexture()
                    }
                }
            }
        } catch (e: com.google.ar.core.exceptions.MissingGlContextException) {
            Log.w(TAG, "MissingGlContextException during ARCore update; rebinding camera texture")
            modernArEngine?.rebindCameraTexture()
        } catch (e: com.google.ar.core.exceptions.SessionPausedException) {
            Log.w(TAG, "SessionPausedException during ARCore update; marking session paused")
            modernArEngine?.markSessionPaused()
        } catch (e: com.google.ar.core.exceptions.CameraNotAvailableException) {
            if (logThrottleCounter++ % 90 == 0) {
                Log.w(TAG, "ARCore CameraNotAvailableException: ${e.message}")
                val lang = modernArEngine?.currentLangCode ?: "zh-TW"
                modernArEngine?.reportRuntimeError(TranslationManager.getString("arcore_stream_reconnecting", lang))
            }
        } catch (e: com.google.ar.core.exceptions.DeadlineExceededException) {
            if (logThrottleCounter++ % 60 == 0) {
                Log.w(TAG, "ARCore DeadlineExceededException; dropping slow frame cleanly: ${e.message}")
            }
        } catch (e: com.google.ar.core.exceptions.NotTrackingException) {
            if (logThrottleCounter++ % 90 == 0) {
                Log.d(TAG, "ARCore NotTrackingException while paused/relocalizing: ${e.message}")
            }
        } catch (e: com.google.ar.core.exceptions.ResourceExhaustedException) {
            if (logThrottleCounter++ % 30 == 0) {
                Log.w(TAG, "ARCore ResourceExhaustedException (anchors/images): ${e.message}")
            }
        } catch (e: Exception) {
            if (logThrottleCounter++ % 90 == 0) {
                Log.w(TAG, "ARCore update state: ${e.message}", e)
            }
        } finally {
            adpfHintSession?.reportFrameDurationNs(System.nanoTime() - frameWorkStartNs)
            frameSyncManager.signalFrameCompleted(slot)
        }
    }

    /**
     * Captures a bitmap for snapshots or video frame recording.
     */
    fun captureBitmap(onBitmap: (Bitmap?) -> Unit) {
        takeSnapshot(onBitmap)
    }

    val isAvailable: Boolean
        get() = isSurfaceReady()

    /**
     * Captures a high-resolution snapshot using zero-copy PixelCopy and HardwareBuffer.
     * Completely eliminates glReadPixels and OpenGL state synchronization.
     */
    fun takeSnapshot(onCaptured: (Bitmap?) -> Unit) {
        val safeCallback = { bmp: Bitmap? ->
            post { onCaptured(bmp) }
        }

        if (width <= 0 || height <= 0 || !isSurfaceReady()) {
            safeCallback(null)
            return
        }

        var bitmap: Bitmap? = null
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val h = Handler(context.mainLooper)

            PixelCopy.request(
                this,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        safeCallback(bitmap)
                    } else {
                        try { bitmap.recycle() } catch (_: Throwable) {}
                        Log.w(TAG, "PixelCopy returned status: $copyResult")
                        safeCallback(null)
                    }
                },
                h
            )
        } catch (e: Exception) {
            try { bitmap?.recycle() } catch (_: Throwable) {}
            Log.e(TAG, "Snapshot capture error: ${e.message}")
            safeCallback(null)
        }
    }
}
