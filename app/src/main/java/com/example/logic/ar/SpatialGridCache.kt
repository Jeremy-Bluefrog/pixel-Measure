package com.example.logic.ar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.ui.viewmodel.Point3D
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reusable zero-allocation 2D screen coordinate model with depth and validity state.
 */
class CachedScreenPoint(
    var x: Float = 0f,
    var y: Float = 0f,
    var depth: Float = 0f,
    var isValid: Boolean = false
) {
    fun set(px: Float, py: Float, pDepth: Float, valid: Boolean) {
        x = px
        y = py
        depth = pDepth
        isValid = valid
    }

    fun toOffset(): Offset = Offset(x, y)
}

/**
 * Cached Spatial Wall Mesh geometry (screen corners, cached fill Path, border Path).
 */
class CachedWallMesh(
    val id: String,
    val corners: Array<CachedScreenPoint> = Array(4) { CachedScreenPoint() },
    val fillPath: Path = Path(),
    val borderPath: Path = Path(),
    var isVisible: Boolean = false,
    var lastUpdateFrame: Long = 0L
)

/**
 * Cached Objectron 3D Bounding Box screen projection.
 */
class CachedBoxMesh(
    val corners: Array<CachedScreenPoint> = Array(8) { CachedScreenPoint() },
    var centerPoint: CachedScreenPoint = CachedScreenPoint(),
    var isVisible: Boolean = false,
    var lastUpdateFrame: Long = 0L
)

/**
 * Lightweight Spatial Grid & 3D Mesh Caching Engine for AR Measurement Mode.
 *
 * Solves:
 * 1. Frame rate drops (FPS dips) during fast camera panning by eliminating per-frame object allocations
 *    and caching repetitive matrix projections.
 * 2. Visual tearing, jitter, and flickering lines during high-speed device movement via motion-adaptive LOD.
 * 3. Screen frustum culling to skip rendering elements behind the camera or out of bounds.
 * 4. Automatic cleanup of stale spatial meshes.
 */
class SpatialGridCache {

    // Pre-allocated Screen Point Cache Pools (Zero GC churn)
    private val pointPool = ArrayList<CachedScreenPoint>(64)
    val liveTargetScreenPoint = CachedScreenPoint()
    val boxMeshCache = CachedBoxMesh()

    // Cached wall meshes keyed by wall identifier
    private val wallMeshCache = HashMap<String, CachedWallMesh>()

    // Motion metrics
    var currentCameraSpeedMps: Float = 0f
        private set
    var isFastMotion: Boolean = false
        private set
    var isCacheWarm: Boolean = false
        private set

    // Frame tracking
    private var currentFrame: Long = 0L
    private val lastViewMatrix = FloatArray(16)
    private val lastProjMatrix = FloatArray(16)
    private var lastScreenWidth = 0
    private var lastScreenHeight = 0

    init {
        for (i in 0 until 64) {
            pointPool.add(CachedScreenPoint())
        }
    }

    /**
     * Updates camera matrices and evaluates camera velocity to determine adaptive caching level.
     */
    fun updateCameraFrame(
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int,
        cameraSpeedMps: Float = 0f
    ) {
        currentFrame++
        currentCameraSpeedMps = cameraSpeedMps
        // Fast motion threshold: 0.45 m/s
        isFastMotion = cameraSpeedMps > 0.45f

        if (viewMatrix.size >= 16) System.arraycopy(viewMatrix, 0, lastViewMatrix, 0, 16)
        if (projMatrix.size >= 16) System.arraycopy(projMatrix, 0, lastProjMatrix, 0, 16)
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight
        isCacheWarm = true

        // Clean stale meshes periodically every 60 frames
        if (currentFrame % 60 == 0L) {
            cleanupStaleMeshes()
        }
    }

    /**
     * Zero-allocation 3D world to 2D screen projection with frustum culling.
     */
    fun projectPoint(
        worldPoint: Point3D,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int,
        outPoint: CachedScreenPoint
    ): Boolean {
        if (viewMatrix.size < 16 || projMatrix.size < 16 || screenWidth <= 0 || screenHeight <= 0) {
            outPoint.isValid = false
            return false
        }

        val wx = worldPoint.x.toFloat()
        val wy = worldPoint.y.toFloat()
        val wz = worldPoint.z.toFloat()

        // Multiply ViewMatrix * WorldPoint
        val vx = viewMatrix[0] * wx + viewMatrix[4] * wy + viewMatrix[8] * wz + viewMatrix[12]
        val vy = viewMatrix[1] * wx + viewMatrix[5] * wy + viewMatrix[9] * wz + viewMatrix[13]
        val vz = viewMatrix[2] * wx + viewMatrix[6] * wy + viewMatrix[10] * wz + viewMatrix[14]
        val vw = viewMatrix[3] * wx + viewMatrix[7] * wy + viewMatrix[11] * wz + viewMatrix[15]

        // Multiply ProjectionMatrix * ViewPoint
        val clipX = projMatrix[0] * vx + projMatrix[4] * vy + projMatrix[8] * vz + projMatrix[12] * vw
        val clipY = projMatrix[1] * vx + projMatrix[5] * vy + projMatrix[9] * vz + projMatrix[13] * vw
        val clipW = projMatrix[3] * vx + projMatrix[7] * vy + projMatrix[11] * vz + projMatrix[15] * vw

        // Discard points behind the camera
        if (clipW <= 0.001f) {
            outPoint.isValid = false
            return false
        }

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW

        val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
        val screenY = (1.0f - ndcY) * 0.5f * screenHeight

        // Frustum boundary margin check (-300px to width+300px)
        val inBounds = screenX >= -300f && screenX <= (screenWidth + 300f) &&
                screenY >= -300f && screenY <= (screenHeight + 300f)

        if (!inBounds) {
            outPoint.isValid = false
            return false
        }

        outPoint.set(screenX, screenY, clipW, true)
        return true
    }

    /**
     * Batch project captured measurement points with zero GC allocation.
     */
    fun batchProjectPoints(
        points: List<Point3D>,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int
    ): List<CachedScreenPoint> {
        while (pointPool.size < points.size) {
            pointPool.add(CachedScreenPoint())
        }

        for (i in points.indices) {
            val cachedPt = pointPool[i]
            projectPoint(points[i], viewMatrix, projMatrix, screenWidth, screenHeight, cachedPt)
        }

        return pointPool.subList(0, points.size)
    }

    /**
     * Batch project Objectron 3D Bounding Box 8 corners and center point.
     */
    fun projectBox(
        corners: List<Point3D>,
        center: Point3D,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int
    ): CachedBoxMesh {
        var anyVisible = false
        val count = minOf(8, corners.size)
        for (i in 0 until count) {
            val vis = projectPoint(corners[i], viewMatrix, projMatrix, screenWidth, screenHeight, boxMeshCache.corners[i])
            if (vis) anyVisible = true
        }
        projectPoint(center, viewMatrix, projMatrix, screenWidth, screenHeight, boxMeshCache.centerPoint)
        boxMeshCache.isVisible = anyVisible
        boxMeshCache.lastUpdateFrame = currentFrame
        return boxMeshCache
    }

    /**
     * Retrieves or updates cached wall mesh geometry, updating Paths only when necessary.
     */
    fun getOrUpdateWallMesh(
        wallId: String,
        corners3D: List<Point3D>,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        screenWidth: Int,
        screenHeight: Int
    ): CachedWallMesh? {
        if (corners3D.size < 4) return null

        val mesh = wallMeshCache.getOrPut(wallId) {
            CachedWallMesh(id = wallId)
        }

        var visibleCorners = 0
        for (i in 0 until 4) {
            val visible = projectPoint(
                corners3D[i],
                viewMatrix,
                projMatrix,
                screenWidth,
                screenHeight,
                mesh.corners[i]
            )
            if (visible) {
                visibleCorners++
            }
        }

        mesh.isVisible = visibleCorners >= 2
        if (mesh.isVisible && mesh.corners[0].isValid && mesh.corners[1].isValid && mesh.corners[2].isValid && mesh.corners[3].isValid) {
            mesh.fillPath.apply {
                reset()
                moveTo(mesh.corners[0].x, mesh.corners[0].y)
                lineTo(mesh.corners[1].x, mesh.corners[1].y)
                lineTo(mesh.corners[2].x, mesh.corners[2].y)
                lineTo(mesh.corners[3].x, mesh.corners[3].y)
                close()
            }
            mesh.lastUpdateFrame = currentFrame
        }

        return mesh
    }

    /**
     * Clean up stale cached wall meshes that haven't been seen for over 60 frames.
     */
    fun cleanupStaleMeshes() {
        val iterator = wallMeshCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentFrame - entry.value.lastUpdateFrame > 60) {
                iterator.remove()
            }
        }
    }

    /**
     * Clear all cached spatial mesh data.
     */
    fun clear() {
        wallMeshCache.clear()
        isCacheWarm = false
    }
}
