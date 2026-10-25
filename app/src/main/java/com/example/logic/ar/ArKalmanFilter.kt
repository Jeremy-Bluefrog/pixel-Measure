package com.example.logic.ar

import com.example.ui.viewmodel.Point3D
import kotlin.math.sqrt

/**
 * High-performance 3D Kalman Filter for AR coordinate smoothing,
 * eliminating jitter, tremor, and flickering during rapid camera movement.
 */
class ArKalmanFilter3D(
    private var processNoise: Float = 0.004f,
    private var measurementNoise: Float = 0.035f
) {
    private var x = 0.0 // position X
    private var y = 0.0 // position Y
    private var z = 0.0 // position Z
    private var vx = 0.0 // velocity X
    private var vy = 0.0 // velocity Y
    private var vz = 0.0 // velocity Z

    // Error covariance matrix diagonal elements
    private var pX = 0.1
    private var pY = 0.1
    private var pZ = 0.1
    private var pVx = 1.0
    private var pVy = 1.0
    private var pVz = 1.0

    private var isInitialized = false

    fun reset() {
        isInitialized = false
    }

    fun update(measurement: Point3D, dt: Float = 0.016f): Point3D {
        if (!isInitialized) {
            x = measurement.x
            y = measurement.y
            z = measurement.z
            vx = 0.0
            vy = 0.0
            vz = 0.0
            pX = 0.05
            pY = 0.05
            pZ = 0.05
            pVx = 1.0
            pVy = 1.0
            pVz = 1.0
            isInitialized = true
            return measurement
        }

        // 1. Predict step
        val predX = x + vx * dt
        val predY = y + vy * dt
        val predZ = z + vz * dt
        val predVx = vx
        val predVy = vy
        val predVz = vz

        pX += processNoise + dt * dt * pVx
        pY += processNoise + dt * dt * pVy
        pZ += processNoise + dt * dt * pZ
        pVx += processNoise
        pVy += processNoise
        pVz += processNoise

        // Adaptive measurement noise based on innovation (distance between prediction and measurement)
        val dx = measurement.x - predX
        val dy = measurement.y - predY
        val dz = measurement.z - predZ
        val innovationDist = sqrt(dx * dx + dy * dy + dz * dz)

        // Rapid movement detection: adapt measurement noise for zero lag during fast panning
        val adaptiveR = if (innovationDist > 0.06) {
            measurementNoise * 0.15 // Trust measurement more during rapid camera movement
        } else {
            measurementNoise * (1.0 + innovationDist * 12.0) // Heavy smoothing for micro-vibrations
        }

        // 2. Update step (Kalman gain)
        val kX = pX / (pX + adaptiveR)
        val kY = pY / (pY + adaptiveR)
        val kZ = pZ / (pZ + adaptiveR)

        x = predX + kX * (measurement.x - predX)
        y = predY + kY * (measurement.y - predY)
        z = predZ + kZ * (measurement.z - predZ)

        // Update velocity estimate
        vx = predVx + (kX * (measurement.x - predX)) / dt
        vy = predVy + (kY * (measurement.y - predY)) / dt
        vz = predVz + (kZ * (measurement.z - predZ)) / dt

        pX = (1.0 - kX) * pX
        pY = (1.0 - kY) * pY
        pZ = (1.0 - kZ) * pZ

        return measurement.copy(
            x = x,
            y = y,
            z = z
        )
    }
}
