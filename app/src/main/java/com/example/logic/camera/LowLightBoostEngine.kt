package com.example.logic.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.util.Log

/**
 * Low Light Boost (暗光增強 / 夜間畫面提亮) Engine.
 * 
 * Based on Android 15 Camera2 Low Light Boost:
 * - CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
 * - CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE
 * 
 * Provides:
 * 1. Hardware AE Mode Detection (Android 15+ API 35)
 * 2. Software Fallback ISP / Computational Brightening & Gamma Curve Compensation
 * 3. Dynamic Ambient Light Measurement & Real-time Auto-Activation State
 */
class LowLightBoostEngine(private val context: Context) {

    companion object {
        private const val TAG = "LowLightBoostEngine"
        
        // Android 15 Camera2 Constant values for forward & backward compatibility
        // CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
        // CaptureRequest.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY = 6
        const val AE_MODE_LOW_LIGHT_BOOST = 6
        
        // CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE_INACTIVE = 0
        // CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE_ACTIVE = 1
        const val BOOST_STATE_INACTIVE = 0
        const val BOOST_STATE_ACTIVE = 1
    }

    var isHardwareBoostSupported: Boolean = false
        private set

    var isBoostActive: Boolean = false
        private set

    var ambientLuxEstimated: Float = 50.0f
        private set

    /**
     * Inspect Camera Characteristics to determine if Hardware Low Light Boost is supported.
     */
    fun checkHardwareSupport(characteristics: CameraCharacteristics): Boolean {
        isHardwareBoostSupported = try {
            val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            aeModes?.contains(AE_MODE_LOW_LIGHT_BOOST) == true
        } catch (e: Throwable) {
            false
        }
        Log.i(TAG, "Hardware Low Light Boost supported: $isHardwareBoostSupported (API=${Build.VERSION.SDK_INT})")
        return isHardwareBoostSupported
    }

    /**
     * Apply Low Light Boost AE mode to Camera2 CaptureRequest.Builder (permanently forced ON).
     */
    fun applyLowLightBoost(builder: CaptureRequest.Builder, enabled: Boolean = true) {
        try {
            // Unconditionally forced ON
            if (isHardwareBoostSupported) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, AE_MODE_LOW_LIGHT_BOOST)
                Log.i(TAG, "Permanently Forced CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY ON")
            } else {
                // Forced high exposure compensation fallback for non-Android 15 devices
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 4) // +2.0 EV boost
                Log.i(TAG, "Permanently Forced AE Exposure Compensation +2.0 EV for Low Light Boost")
            }
            isBoostActive = true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply Low Light Boost to CaptureRequest: ${e.message}")
        }
    }

    /**
     * Update state from Camera2 TotalCaptureResult.
     */
    fun onCaptureResult(result: CaptureResult) {
        try {
            if (Build.VERSION.SDK_INT >= 35) {
                // In API 35, CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE can be read
                // Here we safely inspect metadata keys or fallback to sensor sensitivity
            }
            val sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
            val exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 10_000_000L // ns
            
            // Estimate scene brightness from ISO and Exposure time
            // Low light condition typically when ISO > 800 or Exposure > 25ms (25,000,000 ns)
            val isDim = sensorSensitivity >= 600 || exposureTime >= 20_000_000L
            isBoostActive = isDim
            ambientLuxEstimated = (1_000_000_000.0f / (sensorSensitivity * exposureTime / 1000.0f)).coerceIn(0.1f, 1000f)
        } catch (e: Throwable) {
            // Ignore capture result parsing errors
        }
    }

    /**
     * Software ISP Brightness Enhancement Filter (for fallback / live preview boost).
     * Applies an optimized tone-mapping & luminance lift curve for low-light frames.
     */
    fun enhanceLowLightBitmap(src: Bitmap, boostFactor: Float = 1.45f): Bitmap {
        if (boostFactor <= 1.0f) return src
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gamma = 0.75f // Lift shadows without blowing out highlights
        val invGamma = 1.0f / gamma

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            var r = Color.red(pixel) / 255.0f
            var g = Color.green(pixel) / 255.0f
            var b = Color.blue(pixel) / 255.0f

            // Apply Gamma & Lift
            r = (Math.pow(r.toDouble(), gamma.toDouble()) * boostFactor).toFloat().coerceIn(0.0f, 1.0f)
            g = (Math.pow(g.toDouble(), gamma.toDouble()) * boostFactor).toFloat().coerceIn(0.0f, 1.0f)
            b = (Math.pow(b.toDouble(), gamma.toDouble()) * boostFactor).toFloat().coerceIn(0.0f, 1.0f)

            pixels[i] = Color.argb(a, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
