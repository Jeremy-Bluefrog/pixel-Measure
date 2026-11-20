package com.example.logic.vulkan

import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi

/**
 * Manages zero-copy AHardwareBuffer memory bridging from Camera to Vulkan rendering pipeline.
 * Eliminates intermediate CPU byte-array copies and bypasses legacy OpenGL ES EGLImage wrappers.
 */
class VulkanHardwareBufferManager(
    val width: Int = 1920,
    val height: Int = 1080
) {
    companion object {
        private const val TAG = "VulkanHardwareBuffer"
    }

    private var imageReader: ImageReader? = null
    private var lastHardwareBuffer: HardwareBuffer? = null
    @Volatile
    private var isReleased = false

    /**
     * Creates an ImageReader configured with GPU Sampled Image flags to produce HardwareBuffers directly.
     */
    fun createHardwareBufferSurface(): Surface? {
        isReleased = false
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
                                        hb.close()
                                    } else {
                                        lastHardwareBuffer?.close()
                                        lastHardwareBuffer = hb
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
     * Retrieves the latest zero-copy HardwareBuffer for direct Vulkan texture sampling.
     */
    fun acquireLatestBuffer(): HardwareBuffer? {
        synchronized(this) {
            if (isReleased) return null
            return lastHardwareBuffer
        }
    }

    /**
     * Smart dynamic release: immediately closes all native HardwareBuffers and ImageReaders
     * to eliminate battery drain and GPU memory overhead upon leaving the scene.
     */
    fun release() {
        synchronized(this) {
            isReleased = true
            lastHardwareBuffer?.close()
            lastHardwareBuffer = null
        }
        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
        Log.i(TAG, "Dynamic release completed. Zero hardware buffers or image readers held.")
    }
}
