package com.example.logic.vulkan

import kotlin.math.*

/**
 * High-performance pure Kotlin 4x4 Matrix math utility for Vulkan and 3D projections.
 * Fully replaces android.opengl.Matrix to achieve zero OpenGL dependencies across the architecture.
 */
object VulkanMatrix4 {

    fun setIdentity(m: FloatArray, offset: Int = 0) {
        for (i in 0 until 16) {
            m[offset + i] = 0f
        }
        m[offset + 0] = 1.0f
        m[offset + 5] = 1.0f
        m[offset + 10] = 1.0f
        m[offset + 15] = 1.0f
    }

    /**
     * Creates a perspective projection matrix compatible with 3D coordinate space.
     */
    fun perspective(
        m: FloatArray,
        offset: Int = 0,
        fovyDeg: Float,
        aspect: Float,
        zNear: Float,
        zFar: Float
    ) {
        val f = 1.0f / tan(Math.toRadians(fovyDeg.toDouble() * 0.5)).toFloat()
        val rangeReciprocal = 1.0f / (zNear - zFar)

        for (i in 0 until 16) {
            m[offset + i] = 0f
        }
        m[offset + 0] = f / aspect
        // Invert Y axis for Vulkan NDC coordinate system where Y points down
        m[offset + 5] = -f
        // Vulkan NDC depth range is [0, 1] instead of OpenGL [-1, 1]
        m[offset + 10] = zFar * rangeReciprocal
        m[offset + 11] = -1.0f
        m[offset + 14] = zNear * zFar * rangeReciprocal
    }

    private val scratchR: ThreadLocal<FloatArray> = ThreadLocal.withInitial { FloatArray(16) }
    private val scratchTemp: ThreadLocal<FloatArray> = ThreadLocal.withInitial { FloatArray(16) }

    /**
     * Rotates matrix in-place along specified axis (angle in degrees) with zero heap allocations.
     */
    fun rotate(
        m: FloatArray,
        offset: Int = 0,
        angleDeg: Float,
        x: Float,
        y: Float,
        z: Float
    ) {
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        val nc = 1.0f - c

        var len = sqrt(x * x + y * y + z * z)
        if (len == 0f) return
        val invLen = 1.0f / len
        val nx = x * invLen
        val ny = y * invLen
        val nz = z * invLen

        val r = scratchR.get()
        r[0] = nx * nx * nc + c
        r[1] = ny * nx * nc + nz * s
        r[2] = nz * nx * nc - ny * s
        r[3] = 0f

        r[4] = nx * ny * nc - nz * s
        r[5] = ny * ny * nc + c
        r[6] = nz * ny * nc + nx * s
        r[7] = 0f

        r[8] = nx * nz * nc + ny * s
        r[9] = ny * nz * nc - nx * s
        r[10] = nz * nz * nc + c
        r[11] = 0f

        r[12] = 0f
        r[13] = 0f
        r[14] = 0f
        r[15] = 1f

        val temp = scratchTemp.get()
        multiplyMM(temp, 0, m, offset, r, 0)
        System.arraycopy(temp, 0, m, offset, 16)
    }

    /**
     * Multiplies two 4x4 matrices: result = lhs * rhs.
     */
    fun multiplyMM(
        result: FloatArray,
        resultOffset: Int,
        lhs: FloatArray,
        lhsOffset: Int,
        rhs: FloatArray,
        rhsOffset: Int
    ) {
        for (i in 0 until 4) {
            val rhs_i0 = rhs[rhsOffset + i * 4 + 0]
            val rhs_i1 = rhs[rhsOffset + i * 4 + 1]
            val rhs_i2 = rhs[rhsOffset + i * 4 + 2]
            val rhs_i3 = rhs[rhsOffset + i * 4 + 3]

            result[resultOffset + i * 4 + 0] = (lhs[lhsOffset + 0] * rhs_i0 +
                    lhs[lhsOffset + 4] * rhs_i1 +
                    lhs[lhsOffset + 8] * rhs_i2 +
                    lhs[lhsOffset + 12] * rhs_i3)
            result[resultOffset + i * 4 + 1] = (lhs[lhsOffset + 1] * rhs_i0 +
                    lhs[lhsOffset + 5] * rhs_i1 +
                    lhs[lhsOffset + 9] * rhs_i2 +
                    lhs[lhsOffset + 13] * rhs_i3)
            result[resultOffset + i * 4 + 2] = (lhs[lhsOffset + 2] * rhs_i0 +
                    lhs[lhsOffset + 6] * rhs_i1 +
                    lhs[lhsOffset + 10] * rhs_i2 +
                    lhs[lhsOffset + 14] * rhs_i3)
            result[resultOffset + i * 4 + 3] = (lhs[lhsOffset + 3] * rhs_i0 +
                    lhs[lhsOffset + 7] * rhs_i1 +
                    lhs[lhsOffset + 11] * rhs_i2 +
                    lhs[lhsOffset + 15] * rhs_i3)
        }
    }

    /**
     * Multiplies 4x4 matrix with 4-element vector: resultVec = lhsMat * rhsVec.
     */
    fun multiplyMV(
        resultVec: FloatArray,
        resultOffset: Int,
        lhsMat: FloatArray,
        lhsMatOffset: Int,
        rhsVec: FloatArray,
        rhsVecOffset: Int
    ) {
        val x = rhsVec[rhsVecOffset + 0]
        val y = rhsVec[rhsVecOffset + 1]
        val z = rhsVec[rhsVecOffset + 2]
        val w = rhsVec[rhsVecOffset + 3]

        resultVec[resultOffset + 0] = lhsMat[lhsMatOffset + 0] * x + lhsMat[lhsMatOffset + 4] * y + lhsMat[lhsMatOffset + 8] * z + lhsMat[lhsMatOffset + 12] * w
        resultVec[resultOffset + 1] = lhsMat[lhsMatOffset + 1] * x + lhsMat[lhsMatOffset + 5] * y + lhsMat[lhsMatOffset + 9] * z + lhsMat[lhsMatOffset + 13] * w
        resultVec[resultOffset + 2] = lhsMat[lhsMatOffset + 2] * x + lhsMat[lhsMatOffset + 6] * y + lhsMat[lhsMatOffset + 10] * z + lhsMat[lhsMatOffset + 14] * w
        resultVec[resultOffset + 3] = lhsMat[lhsMatOffset + 3] * x + lhsMat[lhsMatOffset + 7] * y + lhsMat[lhsMatOffset + 11] * z + lhsMat[lhsMatOffset + 15] * w
    }
}
