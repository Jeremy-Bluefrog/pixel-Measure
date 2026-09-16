package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.logic.ar.ArMath
import com.example.logic.ai.ObjectronEngine
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hardware-accelerated, isolated 60/120 FPS 3D AR measurement drawing canvas overlay.
 * Isolates high-frequency frame updates to eliminate unnecessary parent UI recompositions.
 */
@Composable
fun ArMeasurementCanvasOverlay(
    viewModel: MeasureViewModel,
    pings: List<Pair<Offset, Animatable<Float, AnimationVector1D>>>,
    revealedTileIds: Map<String, Boolean>,
    dashPhase: State<Float>,
    reticlePulseScale: State<Float>,
    snapScaleAnimated: State<Float>,
    snapGlowAlphaAnimated: State<Float>,
    planeLockedParticleAnim: State<Float>,
    density: Float = androidx.compose.ui.platform.LocalDensity.current.density,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val colorTertiary = MaterialTheme.colorScheme.tertiary
    val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val colorOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary

    // High-frequency AR state collection isolated within this Composable
    val viewMatrix by viewModel.viewMatrix.collectAsState()
    val projectionMatrix by viewModel.projectionMatrix.collectAsState()
    val liveTargetPoint by viewModel.liveTargetPoint.collectAsState()
    val liveTargetScreenPos by viewModel.liveTargetScreenPos.collectAsState()
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val subMode by viewModel.cameraSubMode.collectAsState()
    val autoDetectedType by viewModel.autoDetectedType.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val isObjectronMode by viewModel.isObjectronMode.collectAsState()
    val objectron3DBox by viewModel.objectron3DBox.collectAsState()
    val isMobileSamMode by viewModel.isMobileSamMode.collectAsState()
    val segmentedObject by viewModel.segmentedObject.collectAsState()
    val detectedTiles by viewModel.detectedTiles.collectAsState()
    val isSimultaneousWallMeasureActive by viewModel.isSimultaneousWallMeasureActive.collectAsState()
    val detectedWalls by viewModel.detectedWalls.collectAsState()
    val planesCount by viewModel.arPlanesCount.collectAsState()
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val capturedPoints = viewModel.capturedPoints

    // Cached paints for ultra-low latency hardware canvas badge rendering
    val badgeBgPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
    }
    val badgeBorderPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }
    }
    val badgeTextPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 38f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    // Reusable Path caches to prevent per-frame GC allocations
    val samCachedPath = remember { Path() }
    val wallCachedPath = remember { Path() }
    val areaCachedPath = remember { Path() }

    // Persistent 2D smooth auto-follow position state across canvas draw passes
    var smoothedReticlePos by remember { mutableStateOf<Offset?>(null) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val screenW = size.width.toInt()
        val screenH = size.height.toInt()
        val screenCenter = Offset(size.width / 2f, size.height / 2f)

        // Calculate real-time projected reticle position from 3D live target point
        val rawTargetReticlePos = if (liveTargetScreenPos != null &&
            liveTargetScreenPos!!.x in (-120f)..(size.width + 120f) &&
            liveTargetScreenPos!!.y in (-120f)..(size.height + 120f)
        ) {
            liveTargetScreenPos!!
        } else if (liveTargetPoint != null && viewMatrix.size >= 16 && projectionMatrix.size >= 16) {
            val proj = ArMath.projectWorldToScreen(liveTargetPoint!!, viewMatrix, projectionMatrix, screenW, screenH)
            if (proj != null) Offset(proj.first, proj.second) else screenCenter
        } else {
            screenCenter
        }

        // 2D Critically-Damped Spring & Adaptive Exponential Auto-Follow Filter
        val lastPos = smoothedReticlePos ?: rawTargetReticlePos
        val dx = rawTargetReticlePos.x - lastPos.x
        val dy = rawTargetReticlePos.y - lastPos.y
        val distSq = dx * dx + dy * dy

        val currentReticlePos = if (distSq < 0.75f || distSq.isNaN() || distSq.isInfinite()) {
            if (lastPos.x.isNaN() || lastPos.y.isNaN()) screenCenter else lastPos
        } else {
            val d0Sq = 1600f // 40px characteristic distance
            val lerpFactor = (0.22f + 0.78f * (distSq / (distSq + d0Sq))).coerceIn(0.20f, 1.0f)
            val nx = lastPos.x + dx * lerpFactor
            val ny = lastPos.y + dy * lerpFactor
            if (nx.isNaN() || ny.isNaN() || nx.isInfinite() || ny.isInfinite()) {
                screenCenter
            } else {
                Offset(nx, ny)
            }
        }
        smoothedReticlePos = currentReticlePos

        // Optical Center & Magnetic Tension Guidance Line when Auto-Follow Lock is Active
        if (isSnapped) {
            val tetherDx = currentReticlePos.x - screenCenter.x
            val tetherDy = currentReticlePos.y - screenCenter.y
            val tetherDist = sqrt(tetherDx * tetherDx + tetherDy * tetherDy)
            if (tetherDist > 14f) {
                // Subtle Optical Axis Anchor Dot
                drawCircle(
                    color = Color.White.copy(alpha = 0.40f),
                    center = screenCenter,
                    radius = 3.5.dp.toPx(),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                // Glowing Magnetic Tether Dash Stream
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.75f),
                    start = screenCenter,
                    end = currentReticlePos,
                    strokeWidth = 2.0.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), dashPhase.value)
                )
            }
        }

        // Draw touch ripples
        pings.forEach { (offset, anim) ->
            val progress = anim.value
            drawCircle(
                color = colorPrimary.copy(alpha = 0.5f * (1f - progress)),
                center = offset,
                radius = 8.dp.toPx() + 36.dp.toPx() * progress,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // Take an immutable snapshot of capturedPoints for this entire drawing pass
        val pointsSnapshot = capturedPoints.toList()

        // Project 3D points to 2D screen positions
        val projectedPoints = pointsSnapshot.map { pt ->
            ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
        }

        // Mode Detection
        val isAreaMode = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")
        val isHeightMode = subMode == 2 || (subMode == 0 && autoDetectedType == "HEIGHT")
        val isAngleMode = subMode == 5 || (subMode == 0 && autoDetectedType == "ANGLE")
        val isDistanceMode = !isAreaMode && !isHeightMode && !isAngleMode

        // 1. DISTANCE & AREA MODE: Confirmed Connecting 3D Virtual Lines
        if (isDistanceMode || isAreaMode) {
            val stepVal = if (isAreaMode) 1 else 2
            if (projectedPoints.size >= 2) {
                for (i in 0 until projectedPoints.size - 1 step stepVal) {
                    val p1 = projectedPoints[i]
                    val p2 = projectedPoints[i + 1]
                    if (p1 != null && p2 != null) {
                        val startOffset = Offset(p1.first, p1.second)
                        val endOffset = Offset(p2.first, p2.second)
                        val dx = endOffset.x - startOffset.x
                        val dy = endOffset.y - startOffset.y
                        val segLen = sqrt(dx * dx + dy * dy)

                        if (!segLen.isNaN() && segLen > 0.5f) {
                            // 1. Ambient dark drop shadow
                            drawLine(
                                color = Color.Black.copy(alpha = 0.45f),
                                start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                                end = Offset(endOffset.x + 1f, endOffset.y + 2f),
                                strokeWidth = 7.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 2. Luminous glow halo
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.35f),
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 8.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 3. Core solid laser line
                            drawLine(
                                color = colorPrimary,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 4. Perpendicular dimension ticks
                            if (segLen > 20f && segLen < 4000f) {
                                val nx = -dy / segLen
                                val ny = dx / segLen
                                val tickHalfLen = 9.dp.toPx()

                                // End-cap at Start Point
                                drawLine(
                                    color = Color.White,
                                    start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                    end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // End-cap at End Point
                                drawLine(
                                    color = Color.White,
                                    start = Offset(endOffset.x - nx * tickHalfLen, endOffset.y - ny * tickHalfLen),
                                    end = Offset(endOffset.x + nx * tickHalfLen, endOffset.y + ny * tickHalfLen),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Holographic ruler scale hash marks (guarded against overflow)
                                val step = 28f
                                var d = step
                                var tickCount = 0
                                while (d < segLen - step && tickCount++ < 35) {
                                    val px = startOffset.x + (dx / segLen) * d
                                    val py = startOffset.y + (dy / segLen) * d
                                    val subTickLen = 4.dp.toPx()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                        end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                        strokeWidth = 1.8.dp.toPx()
                                    )
                                    d += step
                                }

                                // 3D In-Canvas Hardware Accelerated Floating Segment Capsule
                                val midX = (startOffset.x + endOffset.x) / 2f
                                val midY = (startOffset.y + endOffset.y) / 2f
                                val segDist = if (i + 1 < pointsSnapshot.size) {
                                    ArMath.distance(pointsSnapshot[i], pointsSnapshot[i + 1])
                                } else 0.0
                                val distText = viewModel.formatLength(segDist, selectedUnit)

                                drawContext.canvas.nativeCanvas.apply {
                                    val textWidth = badgeTextPaint.measureText(distText)
                                    val textHeight = badgeTextPaint.textSize
                                    val padH = 32f
                                    val padV = 16f
                                    val left = midX - textWidth / 2f - padH
                                    val top = midY - textHeight / 2f - padV
                                    val right = midX + textWidth / 2f + padH
                                    val bottom = midY + textHeight / 2f + padV
                                    val radius = 36f

                                    // Shadow
                                    badgeBgPaint.color = 0x55000000
                                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)

                                    // Capsule background
                                    badgeBgPaint.color = colorPrimaryContainer.toArgb()
                                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)

                                    // Border
                                    badgeBorderPaint.color = colorPrimary.copy(alpha = 0.5f).toArgb()
                                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)

                                    // Text
                                    badgeTextPaint.color = colorOnPrimaryContainer.toArgb()
                                    drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                                }
                            }
                        }
                    }
                }
            }

            // Closed Polygon in Area mode
            if (isAreaMode && projectedPoints.size >= 3) {
                val validPts = projectedPoints.filterNotNull()
                if (validPts.size >= 3) {
                    val first = validPts.first()
                    val last = validPts.last()

                    // Closing boundary line
                    drawLine(
                        color = colorPrimary.copy(alpha = 0.85f),
                        start = Offset(last.first, last.second),
                        end = Offset(first.first, first.second),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), dashPhase.value)
                    )

                    // Semi-transparent holographic polygon interior mesh fill
                    areaCachedPath.reset()
                    areaCachedPath.moveTo(validPts[0].first, validPts[0].second)
                    for (k in 1 until validPts.size) {
                        areaCachedPath.lineTo(validPts[k].first, validPts[k].second)
                    }
                    areaCachedPath.close()
                    drawPath(
                        path = areaCachedPath,
                        color = colorPrimary.copy(alpha = 0.16f)
                    )

                    // Visual Centroid & Floating Area Badge
                    val centroidX = validPts.map { it.first }.average().toFloat()
                    val centroidY = validPts.map { it.second }.average().toFloat()
                    val areaVal = viewModel.calculatePolygonArea()
                    val areaText = "面積 ${viewModel.formatArea(areaVal, selectedUnit)}"

                    drawContext.canvas.nativeCanvas.apply {
                        val textWidth = badgeTextPaint.measureText(areaText)
                        val textHeight = badgeTextPaint.textSize
                        val padH = 36f
                        val padV = 20f
                        val left = centroidX - textWidth / 2f - padH
                        val top = centroidY - textHeight / 2f - padV
                        val right = centroidX + textWidth / 2f + padH
                        val bottom = centroidY + textHeight / 2f + padV
                        val radius = 40f

                        badgeBgPaint.color = 0x66000000
                        drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                        badgeBgPaint.color = colorPrimary.toArgb()
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                        badgeBorderPaint.color = Color.White.copy(alpha = 0.7f).toArgb()
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                        badgeTextPaint.color = colorOnPrimary.toArgb()
                        drawText(areaText, centroidX, centroidY + textHeight * 0.35f, badgeTextPaint)
                    }
                }
            }
        }

        // 2. ANGLE MODE (3D Corner Angle & Vertex Arc Sector)
        if (isAngleMode) {
            val p0 = projectedPoints.getOrNull(0)
            val p1 = projectedPoints.getOrNull(1) // Vertex
            val p2 = projectedPoints.getOrNull(2)

            // Arm 1: P0 -> P1 (Vertex)
            if (p0 != null && p1 != null) {
                drawLine(
                    color = colorPrimary,
                    start = Offset(p0.first, p0.second),
                    end = Offset(p1.first, p1.second),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Arm 2: P1 (Vertex) -> P2
            if (p1 != null && p2 != null) {
                drawLine(
                    color = colorSecondary,
                    start = Offset(p1.first, p1.second),
                    end = Offset(p2.first, p2.second),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Confirmed Angle: 3 Points captured
            if (p0 != null && p1 != null && p2 != null) {
                val vOffset = Offset(p1.first, p1.second)
                val angleDeg = viewModel.calculateAngle()

                // Highlight Vertex with double halo
                drawCircle(
                    color = colorTertiary.copy(alpha = 0.4f),
                    center = vOffset,
                    radius = 18.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw Angle Arc Sector
                val a1 = Math.toDegrees(atan2((p0.second - p1.second).toDouble(), (p0.first - p1.first).toDouble())).toFloat()
                val a2 = Math.toDegrees(atan2((p2.second - p1.second).toDouble(), (p2.first - p1.first).toDouble())).toFloat()
                var sweep = a2 - a1
                while (sweep < -180f) sweep += 360f
                while (sweep > 180f) sweep -= 360f

                val arcRadius = 36.dp.toPx()
                drawArc(
                    color = colorTertiary,
                    startAngle = a1,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(vOffset.x - arcRadius, vOffset.y - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Floating Angle Badge along Bisector
                val bisectorRad = Math.toRadians((a1 + sweep / 2f).toDouble())
                val badgeDist = 58.dp.toPx()
                val badgeX = vOffset.x + (cos(bisectorRad) * badgeDist).toFloat()
                val badgeY = vOffset.y + (sin(bisectorRad) * badgeDist).toFloat()
                val angleText = "∠ %.1f°".format(java.util.Locale.US, angleDeg)

                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(angleText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 30f
                    val padV = 16f
                    val left = badgeX - textWidth / 2f - padH
                    val top = badgeY - textHeight / 2f - padV
                    val right = badgeX + textWidth / 2f + padH
                    val bottom = badgeY + textHeight / 2f + padV
                    val radius = 36f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = colorTertiary.toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.8f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.Black.toArgb()
                    drawText(angleText, badgeX, badgeY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // 3. HEIGHT MODE (Vertical Plumb & Ground Horizon Alignment)
        if (isHeightMode && pointsSnapshot.isNotEmpty()) {
            val base3D = pointsSnapshot[0]
            val top3D = if (pointsSnapshot.size >= 2) pointsSnapshot[1] else (liveTargetPoint ?: base3D)
            val plumb3D = Point3D(base3D.x, top3D.y, base3D.z)

            val baseProj = projectedPoints.getOrNull(0)
            val plumbProj = ArMath.projectWorldToScreen(plumb3D, viewMatrix, projectionMatrix, screenW, screenH)
            val topProj = if (projectedPoints.size >= 2) projectedPoints[1] else Pair(currentReticlePos.x, currentReticlePos.y)

            if (baseProj != null && plumbProj != null && topProj != null) {
                val pBase = Offset(baseProj.first, baseProj.second)
                val pPlumb = Offset(plumbProj.first, plumbProj.second)
                val pTop = Offset(topProj.first, topProj.second)

                // Vertical Plumb Laser Line (Base -> Plumb Foot)
                drawLine(
                    color = Color(0xFF10B981),
                    start = pBase,
                    end = pPlumb,
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Plumb line shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(pBase.x + 1.5f, pBase.y + 2f),
                    end = Offset(pPlumb.x + 1.5f, pPlumb.y + 2f),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Horizontal Guide Line (Plumb Foot -> Top Target)
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = pPlumb,
                    end = pTop,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), dashPhase.value)
                )

                // Right-Angle Indicator at Plumb Corner
                val cornerSize = 14.dp.toPx()
                val vertDy = if (pBase.y > pPlumb.y) 1f else -1f
                val horizDx = if (pTop.x > pPlumb.x) 1f else -1f
                val cP1 = Offset(pPlumb.x, pPlumb.y + vertDy * cornerSize)
                val cP2 = Offset(pPlumb.x + horizDx * cornerSize, pPlumb.y + vertDy * cornerSize)
                val cP3 = Offset(pPlumb.x + horizDx * cornerSize, pPlumb.y)
                drawLine(Color(0xFF10B981), cP1, cP2, 2.dp.toPx())
                drawLine(Color(0xFF10B981), cP2, cP3, 2.dp.toPx())

                // Floating Vertical Height Badge
                val midHeightX = (pBase.x + pPlumb.x) / 2f
                val midHeightY = (pBase.y + pPlumb.y) / 2f
                val hMeters = if (pointsSnapshot.size >= 2) {
                    viewModel.calculateVerticalHeight()
                } else {
                    abs(top3D.y - base3D.y)
                }
                val hText = "高度 ${viewModel.formatLength(hMeters, selectedUnit)}"

                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(hText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 32f
                    val padV = 16f
                    val left = midHeightX - textWidth / 2f - padH
                    val top = midHeightY - textHeight / 2f - padV
                    val right = midHeightX + textWidth / 2f + padH
                    val bottom = midHeightY + textHeight / 2f + padV
                    val radius = 36f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = 0xFF10B981.toInt()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.White.toArgb()
                    drawText(hText, midHeightX, midHeightY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // 4. ACTIVE DYNAMIC VIRTUAL LINE & RUBBER-BAND PREVIEW
        val isActivelyDrawingLine = when {
            isAreaMode -> projectedPoints.isNotEmpty()
            isAngleMode -> projectedPoints.size == 1 || projectedPoints.size == 2
            isHeightMode -> projectedPoints.size == 1
            else -> projectedPoints.size % 2 == 1
        }

        if (isActivelyDrawingLine && projectedPoints.isNotEmpty()) {
            val anchorIndex = if (isAngleMode && projectedPoints.size == 2) 1 else projectedPoints.size - 1
            val lastPt = projectedPoints.getOrNull(anchorIndex)
            if (lastPt != null) {
                val startOffset = Offset(lastPt.first, lastPt.second)
                val dx = currentReticlePos.x - startOffset.x
                val dy = currentReticlePos.y - startOffset.y
                val liveLen = sqrt(dx * dx + dy * dy)

                if (!liveLen.isNaN() && liveLen > 0.5f) {
                    // 1. Shadow under active line
                    drawLine(
                        color = Color.Black.copy(alpha = 0.4f),
                        start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                        end = Offset(currentReticlePos.x + 1f, currentReticlePos.y + 2f),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // 2. Luminous animated laser stream
                    drawLine(
                        color = colorPrimary.copy(alpha = 0.35f),
                        start = startOffset,
                        end = currentReticlePos,
                        strokeWidth = 8.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // 3. Flowing dynamic fine dashed scale line
                    drawLine(
                        color = colorPrimary,
                        start = startOffset,
                        end = currentReticlePos,
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), dashPhase.value),
                        cap = StrokeCap.Round
                    )

                    // Solid endpoint cap
                    drawCircle(
                        color = Color.White,
                        center = currentReticlePos,
                        radius = 5.dp.toPx()
                    )
                    drawCircle(
                        color = colorSecondary,
                        center = currentReticlePos,
                        radius = 2.5.dp.toPx()
                    )

                    // 4. Live perpendicular fine scale tick marks (guarded against overflow)
                    if (liveLen > 25f && liveLen < 4000f) {
                        val nx = -dy / liveLen
                        val ny = dx / liveLen
                        val tickHalfLen = 6.dp.toPx()

                        // End tick at start point
                        drawLine(
                            color = Color.White,
                            start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                            end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val step = 24f
                        var d = step
                        var tickCount = 0
                        while (d < liveLen - step && tickCount++ < 35) {
                            val px = startOffset.x + (dx / liveLen) * d
                            val py = startOffset.y + (dy / liveLen) * d
                            val subTickLen = 3f.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.75f),
                                start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                strokeWidth = 1.4.dp.toPx()
                            )
                            d += step
                        }
                    }

                    // Area Mode: Live rubber-band closing dashed line from reticle to first point
                    if (isAreaMode && projectedPoints.size >= 2) {
                        val firstPt = projectedPoints.first()
                        if (firstPt != null) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.65f),
                                start = currentReticlePos,
                                end = Offset(firstPt.first, firstPt.second),
                                strokeWidth = 2.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), dashPhase.value)
                            )
                        }
                    }

                    // Angle Mode Live Preview: Arc at Vertex (P1) between P0 and currentReticlePos
                    if (isAngleMode && projectedPoints.size == 2) {
                        val p0 = projectedPoints[0]
                        val p1 = projectedPoints[1]
                        if (p0 != null && p1 != null) {
                            val vOffset = Offset(p1.first, p1.second)
                            val a0 = Math.toDegrees(atan2((p0.second - p1.second).toDouble(), (p0.first - p1.first).toDouble())).toFloat()
                            val aRet = Math.toDegrees(atan2((currentReticlePos.y - p1.second).toDouble(), (currentReticlePos.x - p1.first).toDouble())).toFloat()
                            var liveSweep = aRet - a0
                            while (liveSweep < -180f) liveSweep += 360f
                            while (liveSweep > 180f) liveSweep -= 360f

                            val liveArcR = 30.dp.toPx()
                            drawArc(
                                color = colorTertiary.copy(alpha = 0.85f),
                                startAngle = a0,
                                sweepAngle = liveSweep,
                                useCenter = false,
                                topLeft = Offset(vOffset.x - liveArcR, vOffset.y - liveArcR),
                                size = androidx.compose.ui.geometry.Size(liveArcR * 2f, liveArcR * 2f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // 3D In-Canvas Hardware Accelerated Live Distance Badge
                    val liveDist = liveDistanceMeters
                    if (liveDist != null && liveDist > 0.0) {
                        val midX = (startOffset.x + currentReticlePos.x) / 2f
                        val midY = (startOffset.y + currentReticlePos.y) / 2f
                        val rawDistText = viewModel.formatLength(liveDist, selectedUnit)
                        val distText = if (isSnapped) "吸附 $rawDistText" else rawDistText

                        drawContext.canvas.nativeCanvas.apply {
                            val textWidth = badgeTextPaint.measureText(distText)
                            val textHeight = badgeTextPaint.textSize
                            val padH = 34f
                            val padV = 18f
                            val left = midX - textWidth / 2f - padH
                            val top = midY - textHeight / 2f - padV
                            val right = midX + textWidth / 2f + padH
                            val bottom = midY + textHeight / 2f + padV
                            val radius = 38f

                            badgeBgPaint.color = 0x55000000
                            drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                            badgeBgPaint.color = colorPrimary.toArgb()
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                            badgeTextPaint.color = colorOnPrimary.toArgb()
                            drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                        }
                    }
                }
            }
        }

        // Draw MediaPipe Objectron 3D Bounding Box Wireframe
        if (isObjectronMode && objectron3DBox != null) {
            val box = objectron3DBox!!
            val boxScreenCorners = box.corners.map { cornerPt ->
                ArMath.projectWorldToScreen(cornerPt, viewMatrix, projectionMatrix, screenW, screenH)
            }

            val boxCyan = colorPrimary
            val boxAmber = colorTertiary

            // 12 Wireframe Edges
            ObjectronEngine.WIREFRAME_EDGES.forEach { (i1, i2) ->
                val p1 = boxScreenCorners.getOrNull(i1)
                val p2 = boxScreenCorners.getOrNull(i2)
                if (p1 != null && p2 != null) {
                    val edgeColor = when {
                        i1 < 4 && i2 < 4 -> boxCyan
                        i1 >= 4 && i2 >= 4 -> boxAmber
                        else -> Color.White.copy(alpha = 0.85f)
                    }
                    drawLine(
                        color = edgeColor,
                        start = Offset(p1.first, p1.second),
                        end = Offset(p2.first, p2.second),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 8 Vertex Keypoints
            boxScreenCorners.forEachIndexed { vIdx, proj ->
                if (proj != null) {
                    val vOffset = Offset(proj.first, proj.second)
                    val isTopVertex = vIdx >= 4
                    val vColor = if (isTopVertex) boxAmber else boxCyan

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        center = Offset(vOffset.x, vOffset.y + 1.5f),
                        radius = 5.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        center = vOffset,
                        radius = 4.5.dp.toPx()
                    )
                    drawCircle(
                        color = vColor,
                        center = vOffset,
                        radius = 3.dp.toPx()
                    )
                }
            }

            // Center Ground Projection Reticle & Volume Badge
            val centerProj = ArMath.projectWorldToScreen(box.center, viewMatrix, projectionMatrix, screenW, screenH)
            if (centerProj != null) {
                val cOffset = Offset(centerProj.first, centerProj.second)
                drawCircle(
                    color = boxCyan.copy(alpha = 0.35f * reticlePulseScale.value),
                    center = cOffset,
                    radius = (16.dp * reticlePulseScale.value).toPx(),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                val volM3 = box.volumeM3
                val volText = "體積 %.3f m³".format(java.util.Locale.US, volM3)
                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(volText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 30f
                    val padV = 16f
                    val left = cOffset.x - textWidth / 2f - padH
                    val top = cOffset.y - textHeight / 2f - padV
                    val right = cOffset.x + textWidth / 2f + padH
                    val bottom = cOffset.y + textHeight / 2f + padV
                    val radius = 36f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = boxAmber.toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.8f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.Black.toArgb()
                    drawText(volText, cOffset.x, cOffset.y + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // Draw MobileSAM Segment Anything Mask & Boundary Polyline
        if (isMobileSamMode && segmentedObject != null) {
            val seg = segmentedObject!!
            if (seg.contour2D.size >= 3) {
                val samPath = samCachedPath.apply {
                    reset()
                    moveTo(seg.contour2D[0].x, seg.contour2D[0].y)
                    for (i in 1 until seg.contour2D.size) {
                        lineTo(seg.contour2D[i].x, seg.contour2D[i].y)
                    }
                    close()
                }
                val samEmerald = colorPrimary
                val samCyan = colorSecondary

                // Translucent radial gradient fill mask
                drawPath(
                    path = samPath,
                    brush = Brush.radialGradient(
                        colors = listOf(samEmerald.copy(alpha = 0.35f), samCyan.copy(alpha = 0.12f)),
                        center = seg.promptPoint,
                        radius = 220.dp.toPx()
                    )
                )

                // Glowing neon border stroke with dashes
                drawPath(
                    path = samPath,
                    color = samEmerald,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                    )
                )

                // Vertex pinpoints
                seg.contour2D.forEach { pt ->
                    drawCircle(color = Color.White, center = pt, radius = 4.dp.toPx())
                    drawCircle(color = samEmerald, center = pt, radius = 2.5.dp.toPx())
                }

                // Prompt point radar beacon
                drawCircle(
                    color = Color.White,
                    center = seg.promptPoint,
                    radius = 5.dp.toPx()
                )
                drawCircle(
                    color = samEmerald,
                    center = seg.promptPoint,
                    radius = (14.dp * reticlePulseScale.value).toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Draw AI Detected Tiles AR Bounding Frame
        if (detectedTiles.isNotEmpty()) {
            val tileGold = colorPrimary
            val tileCyan = colorSecondary

            detectedTiles.forEach { tile ->
                val leftPx = tile.leftNorm * screenW
                val topPx = tile.topNorm * screenH
                val rightPx = tile.rightNorm * screenW
                val bottomPx = tile.bottomNorm * screenH

                val tWidth = rightPx - leftPx
                val tHeight = bottomPx - topPx
                val isRevealed = revealedTileIds[tile.id] == true

                if (tWidth > 30f && tHeight > 30f) {
                    if (isRevealed) {
                        drawRoundRect(
                            color = tileGold.copy(alpha = 0.16f),
                            topLeft = Offset(leftPx, topPx),
                            size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )
                        drawRoundRect(
                            color = tileGold,
                            topLeft = Offset(leftPx, topPx),
                            size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                            )
                        )
                    } else {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(leftPx, topPx),
                            size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            )
                        )
                    }

                    // Corner L-Brackets
                    val bracketLen = minOf(tWidth, tHeight) * 0.20f
                    val bracketColor = if (isRevealed) tileCyan else Color.White.copy(alpha = 0.65f)
                    val bracketStroke = if (isRevealed) 3.5.dp.toPx() else 2.dp.toPx()
                    drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx + bracketLen, topPx), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx, topPx + bracketLen), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx - bracketLen, topPx), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx, topPx - bracketLen), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx + bracketLen, bottomPx), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - bracketLen), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx - bracketLen, bottomPx), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - bracketLen), bracketStroke, StrokeCap.Round)
                }
            }
        }

        // Simultaneous Wall Measurement Overlay
        if (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty()) {
            detectedWalls.forEach { wall ->
                val screenCorners = wall.corners3D.map { cornerPt ->
                    ArMath.projectWorldToScreen(cornerPt, viewMatrix, projectionMatrix, screenW, screenH)
                }
                val pBL = screenCorners.getOrNull(0)
                val pBR = screenCorners.getOrNull(1)
                val pTR = screenCorners.getOrNull(2)
                val pTL = screenCorners.getOrNull(3)

                if (pBL != null && pBR != null && pTR != null && pTL != null) {
                    val wallPath = wallCachedPath.apply {
                        reset()
                        moveTo(pBL.first, pBL.second)
                        lineTo(pBR.first, pBR.second)
                        lineTo(pTR.first, pTR.second)
                        lineTo(pTL.first, pTL.second)
                        close()
                    }

                    // 1. Semi-transparent holographic wall mesh fill
                    drawPath(
                        path = wallPath,
                        color = Color(0x2200E5FF)
                    )

                    // 2. Futuristic boundary outline with animated dash
                    drawPath(
                        path = wallPath,
                        color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), dashPhase.value)
                        )
                    )

                    // 3. Holographic grid lines inside wall surface
                    for (fraction in listOf(0.33f, 0.66f)) {
                        val hStart = Offset(
                            pBL.first + (pTL.first - pBL.first) * fraction,
                            pBL.second + (pTL.second - pBL.second) * fraction
                        )
                        val hEnd = Offset(
                            pBR.first + (pTR.first - pBR.first) * fraction,
                            pBR.second + (pTR.second - pBR.second) * fraction
                        )
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                            start = hStart,
                            end = hEnd,
                            strokeWidth = 1.2.dp.toPx()
                        )

                        val vStart = Offset(
                            pBL.first + (pBR.first - pBL.first) * fraction,
                            pBL.second + (pBR.second - pBL.second) * fraction
                        )
                        val vEnd = Offset(
                            pTL.first + (pTR.first - pTL.first) * fraction,
                            pTL.second + (pTR.second - pTL.second) * fraction
                        )
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                            start = vStart,
                            end = vEnd,
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }

                    // 4. Corner bracket anchors
                    screenCorners.forEach { cornerProj ->
                        if (cornerProj != null) {
                            val cOffset = Offset(cornerProj.first, cornerProj.second)
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.5f),
                                center = Offset(cOffset.x, cOffset.y + 1f),
                                radius = 6.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White,
                                center = cOffset,
                                radius = 5.dp.toPx()
                            )
                            drawCircle(
                                color = Color(0xFF00E5FF),
                                center = cOffset,
                                radius = 3.5.dp.toPx()
                            )
                        }
                    }
                }
            }
        }

        // Draw start and confirmed anchor pin node markers
        projectedPoints.forEachIndexed { index, proj ->
            if (proj != null) {
                val offset = Offset(proj.first, proj.second)
                val isStartNode = index == 0
                val isLastNode = index == projectedPoints.size - 1 && projectedPoints.size > 1

                // 1. Beacon pulse halo ring
                drawCircle(
                    color = colorPrimary.copy(alpha = 0.25f * (2f - reticlePulseScale.value)),
                    center = offset,
                    radius = (14.dp * reticlePulseScale.value).toPx()
                )

                // 2. High-contrast ground shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    center = Offset(offset.x, offset.y + 2f),
                    radius = 9.dp.toPx()
                )

                // 3. Solid Pin Outer Ring
                drawCircle(
                    color = colorPrimary,
                    center = offset,
                    radius = 9.dp.toPx()
                )

                // 4. White Contrast Ring
                drawCircle(
                    color = Color.White,
                    center = offset,
                    radius = 6.dp.toPx()
                )

                // 5. Center Core Pinpoint Dot
                drawCircle(
                    color = if (isStartNode) colorPrimary else if (isLastNode) colorSecondary else colorPrimary,
                    center = offset,
                    radius = 3.5.dp.toPx()
                )
            }
        }

        // Dynamic 3D Target Reticle
        val reticleCenter = currentReticlePos
        val baseRadius = 14.dp.toPx()
        val currentRadius = baseRadius * snapScaleAnimated.value * reticlePulseScale.value

        // Snapped Target Lock Radial Aura Glow
        if (isSnapped) {
            drawCircle(
                color = Color(0xFFFBBF24).copy(alpha = snapGlowAlphaAnimated.value * 0.45f),
                center = reticleCenter,
                radius = currentRadius * 1.6f
            )
            drawCircle(
                color = Color(0xFFFBBF24).copy(alpha = snapGlowAlphaAnimated.value * 0.25f),
                center = reticleCenter,
                radius = currentRadius * 2.2f
            )
        }

        // High-contrast ground shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            center = Offset(reticleCenter.x + 1f, reticleCenter.y + 1.5f),
            radius = currentRadius,
            style = Stroke(width = 3.dp.toPx())
        )

        // Clean Elegant Outer Gold Ring
        drawCircle(
            color = if (isSnapped) Color(0xFFFFD54F) else Color(0xFFFBBF24),
            center = reticleCenter,
            radius = currentRadius,
            style = Stroke(
                width = if (isSnapped) 2.6.dp.toPx() else 2.0.dp.toPx()
            )
        )

        // Optical Corner Targeting Brackets (Auto-Follow Target Lock Indicator)
        val bracketSize = if (isSnapped) 6.dp.toPx() else 5.dp.toPx()
        val bracketDist = currentRadius + if (isSnapped) 3.5.dp.toPx() else 6.dp.toPx()
        val bracketColor = if (isSnapped) Color(0xFFFFD54F) else Color(0xFF00E5FF).copy(alpha = 0.85f)
        val bracketStroke = if (isSnapped) 2.2.dp.toPx() else 1.8.dp.toPx()

        // Top-Left
        drawLine(bracketColor, Offset(reticleCenter.x - bracketDist, reticleCenter.y - bracketDist + bracketSize), Offset(reticleCenter.x - bracketDist, reticleCenter.y - bracketDist), bracketStroke)
        drawLine(bracketColor, Offset(reticleCenter.x - bracketDist, reticleCenter.y - bracketDist), Offset(reticleCenter.x - bracketDist + bracketSize, reticleCenter.y - bracketDist), bracketStroke)
        // Top-Right
        drawLine(bracketColor, Offset(reticleCenter.x + bracketDist, reticleCenter.y - bracketDist + bracketSize), Offset(reticleCenter.x + bracketDist, reticleCenter.y - bracketDist), bracketStroke)
        drawLine(bracketColor, Offset(reticleCenter.x + bracketDist, reticleCenter.y - bracketDist), Offset(reticleCenter.x + bracketDist - bracketSize, reticleCenter.y - bracketDist), bracketStroke)
        // Bottom-Left
        drawLine(bracketColor, Offset(reticleCenter.x - bracketDist, reticleCenter.y + bracketDist - bracketSize), Offset(reticleCenter.x - bracketDist, reticleCenter.y + bracketDist), bracketStroke)
        drawLine(bracketColor, Offset(reticleCenter.x - bracketDist, reticleCenter.y + bracketDist), Offset(reticleCenter.x - bracketDist + bracketSize, reticleCenter.y + bracketDist), bracketStroke)
        // Bottom-Right
        drawLine(bracketColor, Offset(reticleCenter.x + bracketDist, reticleCenter.y + bracketDist - bracketSize), Offset(reticleCenter.x + bracketDist, reticleCenter.y + bracketDist), bracketStroke)
        drawLine(bracketColor, Offset(reticleCenter.x + bracketDist, reticleCenter.y + bracketDist), Offset(reticleCenter.x + bracketDist - bracketSize, reticleCenter.y + bracketDist), bracketStroke)

        // Multi-Sample Burst Averaging Dynamic Progress Arc
        if (sensorTelemetry.multiSampleProgress > 0f) {
            val arcRadius = currentRadius + 5.dp.toPx()
            drawArc(
                color = if (sensorTelemetry.isMultiSampleLocked) colorTertiary else colorPrimary,
                startAngle = -90f,
                sweepAngle = sensorTelemetry.multiSampleProgress * 360f,
                useCenter = false,
                topLeft = Offset(reticleCenter.x - arcRadius, reticleCenter.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Solid Center White & Gold Accent Core Pinpoint Dot
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            center = Offset(reticleCenter.x + 0.5f, reticleCenter.y + 0.5f),
            radius = 4.5.dp.toPx()
        )
        drawCircle(
            color = Color.White,
            center = reticleCenter,
            radius = 4.0.dp.toPx()
        )
        drawCircle(
            color = if (isSnapped) Color(0xFFFFD54F) else Color(0xFFFBBF24),
            center = reticleCenter,
            radius = 2.2.dp.toPx()
        )

        // Plane Locked Center Micro Particle Feedback Ring
        if (planesCount > 0) {
            val particleCount = 8
            for (i in 0 until particleCount) {
                val angle = (i * (360f / particleCount)) + (planeLockedParticleAnim.value * 360f)
                val rad = Math.toRadians(angle.toDouble())
                val orbitRadius = (18.dp.toPx()) + (kotlin.math.sin(planeLockedParticleAnim.value * 6.28318f + i).toFloat() * 2.5.dp.toPx())
                val px = reticleCenter.x + (kotlin.math.cos(rad).toFloat() * orbitRadius)
                val py = reticleCenter.y + (kotlin.math.sin(rad).toFloat() * orbitRadius)
                val sineVal = kotlin.math.sin(planeLockedParticleAnim.value * 6.28318f + i)
                val pAlpha = ((sineVal + 1f) / 2f).coerceIn(0.25f, 0.9f)

                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = pAlpha),
                    center = Offset(px, py),
                    radius = 2f * density
                )
            }
        }
    }
}
