package com.example.logic.vulkan

import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.view.Surface

data class HardwareBufferStats(
    val width: Int,
    val height: Int,
    val producedFrames: Long,
    val acquiredFrames: Long,
    val overwrittenPendingFrames: Long,
    val hasActiveInUseBuffer: Boolean,
    val isReleased: Boolean
)

/**
 * Manages zero-copy AHardwareBuffer memory bridging from Camera to Vulkan rendering pipeline.
 * Uses double-slot handoff (pending vs in-use) so ImageReader callbacks never close a buffer
 * while it is actively sampled by the Vulkan GPU thread, eliminating intermediate CPU copies.
 */
class VulkanHardwareBufferManager(
    val width: Int = 1920,
    val height: Int = 1080
) {
    companion object {
        private const val TAG = "VulkanHardwareBuffer"
    }

    private var imageReader: ImageReader? = null
    private var pendingBuffer: HardwareBuffer? = null
    private var inUseBuffer: HardwareBuffer? = null
    @Volatile
    private var isReleased = false

    private var producedFrames: Long = 0L
    private var acquiredFrames: Long = 0L
    private var overwrittenPendingFrames: Long = 0L

    private fun closeBufferQuietly(buffer: HardwareBuffer?) {
        if (buffer == null) return
        try {
            if (!buffer.isClosed) {
                buffer.close()
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Creates an ImageReader configured with GPU Sampled Image flags to produce HardwareBuffers directly.
     */
    fun createHardwareBufferSurface(): Surface? {
        synchronized(this) {
            isReleased = false
            producedFrames = 0L
            acquiredFrames = 0L
            overwrittenPendingFrames = 0L
        }
        return try {
            val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                4,
                usage
            ).apply {
                setOnImageAvailableListener({ reader ->
                    if (isReleased) {
                        try {
                            reader.acquireLatestImage()?.close()
                        } catch (_: Throwable) {}
                        return@setOnImageAvailableListener
                    }
                    try {
                        val image: Image? = reader.acquireLatestImage()
                        image?.let { img ->
                            val hb = img.hardwareBuffer
                            if (hb != null) {
                                synchronized(this@VulkanHardwareBufferManager) {
                                    if (isReleased) {
                                        closeBufferQuietly(hb)
                                    } else {
                                        if (pendingBuffer != null && pendingBuffer !== inUseBuffer) {
                                            closeBufferQuietly(pendingBuffer)
                                            overwrittenPendingFrames++
                                        }
                                        pendingBuffer = hb
                                        producedFrames++
                                    }
                                }
                            }
                            img.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error acquiring HardwareBuffer: ${e.message}")
                    }
                }, null)
            }
            imageReader?.surface
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HardwareBuffer ImageReader: ${e.message}")
            null
        }
    }

    /**
     * Promotes the latest produced zero-copy HardwareBuffer into active GPU ownership
     * and safely closes the previously retired in-use slot if unused.
     */
    fun acquireLatestBuffer(): HardwareBuffer? {
        synchronized(this) {
            if (isReleased) return null
            val next = pendingBuffer
            if (next != null) {
                if (inUseBuffer != null && inUseBuffer !== next) {
                    closeBufferQuietly(inUseBuffer)
                }
                inUseBuffer = next
                pendingBuffer = null
                acquiredFrames++
                return next
            }
            val active = inUseBuffer
            return if (active != null && !active.isClosed) active else null
        }
    }

    /**
     * Explicitly releases the currently pinned [HardwareBuffer] after a Vulkan GPU command
     * buffer fence signals completion.
     */
    fun releaseAcquiredBuffer(buffer: HardwareBuffer? = inUseBuffer) {
        if (buffer == null) return
        synchronized(this) {
            if (inUseBuffer === buffer) {
                inUseBuffer = null
            }
            if (pendingBuffer === buffer) {
                pendingBuffer = null
            }
            closeBufferQuietly(buffer)
        }
    }

    fun getStats(): HardwareBufferStats {
        synchronized(this) {
            return HardwareBufferStats(
                width = width,
                height = height,
                producedFrames = producedFrames,
                acquiredFrames = acquiredFrames,
                overwrittenPendingFrames = overwrittenPendingFrames,
                hasActiveInUseBuffer = inUseBuffer != null && !inUseBuffer!!.isClosed,
                isReleased = isReleased
            )
        }
    }

    /**
     * Smart dynamic release: immediately closes all native HardwareBuffers and ImageReaders
     * to eliminate battery drain and GPU memory overhead upon leaving the scene.
     */
    fun release() {
        synchronized(this) {
            isReleased = true
            val p = pendingBuffer
            val u = inUseBuffer
            pendingBuffer = null
            inUseBuffer = null
            closeBufferQuietly(p)
            if (u !== p) {
                closeBufferQuietly(u)
            }
        }
        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
        Log.i(TAG, "Dynamic release completed. Zero hardware buffers or image readers held.")
    }
}
