package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.logic.ar.ArMath
import com.example.logic.ai.ObjectronEngine
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import kotlin.math.*

// Pre-allocated static dash arrays to eliminate per-frame GC allocations
private val DASH_10_6 = floatArrayOf(10f, 6f)
private val DASH_10_8 = floatArrayOf(10f, 8f)
private val DASH_14_10 = floatArrayOf(14f, 10f)
private val DASH_15_10 = floatArrayOf(15f, 10f)
private val DASH_20_10 = floatArrayOf(20f, 10f)
private val DASH_12_12 = floatArrayOf(12f, 12f)
private val DASH_24_12 = floatArrayOf(24f, 12f)

/**
 * 專業硬體加速 3D AR 空間測量繪製畫布 (Hardware-Accelerated AR Measurement Canvas Overlay)
 *
 * 核心渲染能力：
 * 1. 支援六大測量模式幾何繪製：直線距離、封閉多邊形面積、空間垂準高度、空間夾角弧形、3D 包絡方框與 MobileSAM 分割輪廓。
 * 2. 支援四大準心風格 (DOUBLE_RING, PRECISION_CROSSHAIR, MINIMAL_DOT, HOLOGRAPHIC_RADAR) 與磁吸鎖定回饋。
 * 3. 支援 3D 透視輔助網格 (PERSPECTIVE_GRID, DOT_MATRIX, OFF) 與特徵點雲空間視覺化。
 * 4. 支援字體大小 (COMPACT, STANDARD, LARGE)、標籤透明度 (SOLID, GLASS, CLEAR) 與線寬自適應。
 * 5. 全向 3D 空間坐標投影與 2D 低通濾波，徹底消滅抖動與重組卡頓。
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
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

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
    val planesCount by viewModel.arPlanesCount.collectAsState()
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val lineThickness by viewModel.lineThickness.collectAsState()
    val reticleStyle by viewModel.reticleStyle.collectAsState()
    val arFontSize by viewModel.arFontSize.collectAsState()
    val badgeOpacity by viewModel.badgeOpacity.collectAsState()
    val gridOverlayStyle by viewModel.gridOverlayStyle.collectAsState()

    val badgeAlpha = when (badgeOpacity) {
        "SOLID" -> 0.95f
        "CLEAR" -> 0.45f
        else -> 0.75f
    }

    val arTextSizePx = when (arFontSize) {
        "COMPACT" -> 28f
        "LARGE" -> 44f
        else -> 36f
    }

    val capturedPoints = viewModel.capturedPoints

    // Cached paints for hardware canvas badge rendering
    val badgeBgPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
    }
    val badgeBorderPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f
        }
    }
    val badgeTextPaint = remember(arTextSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = arTextSizePx
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val nodeLabelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
        }
    }

    // Reusable Path caches
    val samCachedPath = remember { Path() }
    val areaCachedPath = remember { Path() }
    val haloCachedPath = remember { Path() }
    val baseCachedPath = remember { Path() }
    val innerCachedPath = remember { Path() }
    val reticleCachedPath = remember { Path() }
    val floorInnerReticleCachedPath = remember { Path() }
    val bracketBatchCachedPath = remember { Path() }

    // Persistent 2D smooth reticle position holder
    val reticlePosHolder = remember { floatArrayOf(-1000f, -1000f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
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

        // 2D Adaptive Low-Pass Filter
        val lastX = reticlePosHolder[0]
        val lastY = reticlePosHolder[1]

        val currentReticlePos = if (lastX < -500f || lastY < -500f) {
            reticlePosHolder[0] = rawTargetReticlePos.x
            reticlePosHolder[1] = rawTargetReticlePos.y
            rawTargetReticlePos
        } else {
            val dx = rawTargetReticlePos.x - lastX
            val dy = rawTargetReticlePos.y - lastY
            val distSq = dx * dx + dy * dy

            if (distSq.isNaN() || distSq.isInfinite()) {
                Offset(lastX, lastY)
            } else {
                val d0Sq = 2500f
                val lerpFactor = (0.10f + 0.85f * (distSq / (distSq + d0Sq))).coerceIn(0.08f, 0.95f)
                val nx = lastX + dx * lerpFactor
                val ny = lastY + dy * lerpFactor
                if (nx.isNaN() || ny.isNaN() || nx.isInfinite() || ny.isInfinite()) {
                    Offset(lastX, lastY)
                } else {
                    reticlePosHolder[0] = nx
                    reticlePosHolder[1] = ny
                    Offset(nx, ny)
                }
            }
        }

        // -------------------------------------------------------------
        // 0. 3D Spatial Grid & Matrix Overlay (透視地面網格 / 點陣)
        // -------------------------------------------------------------
        if (gridOverlayStyle != "OFF" && viewMatrix.size >= 16 && projectionMatrix.size >= 16) {
            val centerTarget = liveTargetPoint ?: Point3D(0.0, -0.4, -1.2)
            if (gridOverlayStyle == "PERSPECTIVE_GRID") {
                val gridRadius = 1.0 // 1 meter radius around center
                val step = 0.25 // 25cm grid lines
                val numLines = 8
                val startX = centerTarget.x - (numLines / 2) * step
                val startZ = centerTarget.z - (numLines / 2) * step
                val groundY = centerTarget.y

                for (i in 0..numLines) {
                    val lineX = startX + i * step
                    val p1 = Point3D(lineX, groundY, centerTarget.z - gridRadius)
                    val p2 = Point3D(lineX, groundY, centerTarget.z + gridRadius)
                    val proj1 = ArMath.projectWorldToScreen(p1, viewMatrix, projectionMatrix, screenW, screenH)
                    val proj2 = ArMath.projectWorldToScreen(p2, viewMatrix, projectionMatrix, screenW, screenH)
                    if (proj1 != null && proj2 != null) {
                        drawLine(
                            color = colorPrimary.copy(alpha = 0.10f),
                            start = Offset(proj1.first, proj1.second),
                            end = Offset(proj2.first, proj2.second),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                for (j in 0..numLines) {
                    val lineZ = startZ + j * step
                    val p1 = Point3D(centerTarget.x - gridRadius, groundY, lineZ)
                    val p2 = Point3D(centerTarget.x + gridRadius, groundY, lineZ)
                    val proj1 = ArMath.projectWorldToScreen(p1, viewMatrix, projectionMatrix, screenW, screenH)
                    val proj2 = ArMath.projectWorldToScreen(p2, viewMatrix, projectionMatrix, screenW, screenH)
                    if (proj1 != null && proj2 != null) {
                        drawLine(
                            color = colorPrimary.copy(alpha = 0.10f),
                            start = Offset(proj1.first, proj1.second),
                            end = Offset(proj2.first, proj2.second),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            } else if (gridOverlayStyle == "DOT_MATRIX") {
                val step = 0.30
                val numDots = 4
                val startX = centerTarget.x - (numDots / 2) * step
                val startZ = centerTarget.z - (numDots / 2) * step
                val groundY = centerTarget.y

                for (i in 0..numDots) {
                    for (j in 0..numDots) {
                        val pt = Point3D(startX + i * step, groundY, startZ + j * step)
                        val proj = ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
                        if (proj != null) {
                            drawCircle(
                                color = colorPrimary.copy(alpha = 0.25f),
                                center = Offset(proj.first, proj.second),
                                radius = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }

        // Optical Center & Magnetic Tether Guidance Line
        if (isSnapped) {
            val tetherDx = currentReticlePos.x - screenCenter.x
            val tetherDy = currentReticlePos.y - screenCenter.y
            val tetherDist = sqrt(tetherDx * tetherDx + tetherDy * tetherDy)
            if (tetherDist > 14f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.40f),
                    center = screenCenter,
                    radius = 3.5.dp.toPx(),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawLine(
                    color = colorPrimary.copy(alpha = 0.75f),
                    start = screenCenter,
                    end = currentReticlePos,
                    strokeWidth = 2.0.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(DASH_10_6, dashPhase.value)
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

        // Immutable snapshot of capturedPoints
        val pointsSnapshot = capturedPoints.toList()

        // Project 3D points to 2D screen coordinates
        val projectedPoints = pointsSnapshot.map { pt ->
            ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
        }

        // Mode Detection
        val isAreaMode = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")
        val isHeightMode = subMode == 2 || (subMode == 0 && autoDetectedType == "HEIGHT")
        val isAngleMode = subMode == 5 || (subMode == 0 && autoDetectedType == "ANGLE")
        val isDistanceMode = !isAreaMode && !isHeightMode && !isAngleMode

        val effectiveLineThickness = lineThickness.coerceIn(1.5f, 8.0f)

        // -------------------------------------------------------------
        // 1. DISTANCE & AREA MODE: Confirmed Connecting 3D Virtual Lines
        // -------------------------------------------------------------
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
                            // 1. Drop shadow
                            drawLine(
                                color = Color.Black.copy(alpha = 0.45f),
                                start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                                end = Offset(endOffset.x + 1f, endOffset.y + 2f),
                                strokeWidth = (effectiveLineThickness + 3f).dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 2. Luminous glow halo
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.35f),
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = (effectiveLineThickness + 4.5f).dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 3. Core solid laser line
                            drawLine(
                                color = colorPrimary,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = effectiveLineThickness.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 4. Perpendicular dimension ticks
                            if (segLen > 20f && segLen < 4000f) {
                                val nx = -dy / segLen
                                val ny = dx / segLen
                                val tickHalfLen = 8.dp.toPx()

                                drawLine(
                                    color = Color.White,
                                    start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                    end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                drawLine(
                                    color = Color.White,
                                    start = Offset(endOffset.x - nx * tickHalfLen, endOffset.y - ny * tickHalfLen),
                                    end = Offset(endOffset.x + nx * tickHalfLen, endOffset.y + ny * tickHalfLen),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Ruler scale hash marks
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
                                        strokeWidth = 1.6.dp.toPx()
                                    )
                                    d += step
                                }

                                // 3D Floating Segment Dimension Capsule
                                val midX = (startOffset.x + endOffset.x) / 2f
                                val midY = (startOffset.y + endOffset.y) / 2f
                                val segDist = if (i + 1 < pointsSnapshot.size) {
                                    ArMath.distance(pointsSnapshot[i], pointsSnapshot[i + 1])
                                } else 0.0
                                val distText = viewModel.formatLength(segDist, selectedUnit)

                                drawContext.canvas.nativeCanvas.apply {
                                    val textWidth = badgeTextPaint.measureText(distText)
                                    val textHeight = badgeTextPaint.textSize
                                    val padH = 30f
                                    val padV = 16f
                                    val left = midX - textWidth / 2f - padH
                                    val top = midY - textHeight / 2f - padV
                                    val right = midX + textWidth / 2f + padH
                                    val bottom = midY + textHeight / 2f + padV
                                    val radius = 34f

                                    // Shadow
                                    badgeBgPaint.color = 0x55000000
                                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)

                                    // Capsule background with badgeAlpha
                                    val bgBase = colorPrimaryContainer
                                    badgeBgPaint.color = Color(bgBase.red, bgBase.green, bgBase.blue, badgeAlpha).toArgb()
                                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)

                                    // Border
                                    badgeBorderPaint.color = colorPrimary.copy(alpha = 0.8f).toArgb()
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

                    val startOffset = Offset(last.first, last.second)
                    val endOffset = Offset(first.first, first.second)
                    val dx = endOffset.x - startOffset.x
                    val dy = endOffset.y - startOffset.y
                    val segLen = sqrt(dx * dx + dy * dy)

                    if (!segLen.isNaN() && segLen > 0.5f) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.45f),
                            start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                            end = Offset(endOffset.x + 1f, endOffset.y + 2f),
                            strokeWidth = (effectiveLineThickness + 3f).dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        drawLine(
                            color = colorPrimary.copy(alpha = 0.35f),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = (effectiveLineThickness + 4.5f).dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        drawLine(
                            color = colorPrimary,
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = effectiveLineThickness.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (segLen > 20f && segLen < 4000f) {
                            val midX = (startOffset.x + endOffset.x) / 2f
                            val midY = (startOffset.y + endOffset.y) / 2f
                            val segDist = ArMath.distance(pointsSnapshot.last(), pointsSnapshot.first())
                            val distText = viewModel.formatLength(segDist, selectedUnit)

                            drawContext.canvas.nativeCanvas.apply {
                                val textWidth = badgeTextPaint.measureText(distText)
                                val textHeight = badgeTextPaint.textSize
                                val padH = 30f
                                val padV = 16f
                                val left = midX - textWidth / 2f - padH
                                val top = midY - textHeight / 2f - padV
                                val right = midX + textWidth / 2f + padH
                                val bottom = midY + textHeight / 2f + padV
                                val radius = 34f

                                badgeBgPaint.color = 0x55000000
                                drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                                val bgBase = colorPrimaryContainer
                                badgeBgPaint.color = Color(bgBase.red, bgBase.green, bgBase.blue, badgeAlpha).toArgb()
                                drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                                badgeBorderPaint.color = colorPrimary.copy(alpha = 0.8f).toArgb()
                                drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                                badgeTextPaint.color = colorOnPrimaryContainer.toArgb()
                                drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                            }
                        }
                    }

                    // Semi-transparent polygon interior mesh fill
                    areaCachedPath.reset()
                    areaCachedPath.moveTo(validPts[0].first, validPts[0].second)
                    for (k in 1 until validPts.size) {
                        areaCachedPath.lineTo(validPts[k].first, validPts[k].second)
                    }
                    areaCachedPath.close()
                    drawPath(
                        path = areaCachedPath,
                        color = colorPrimary.copy(alpha = 0.18f)
                    )

                    // Visual Centroid & Floating Area Badge
                    val centroidX = validPts.map { it.first }.average().toFloat()
                    val centroidY = validPts.map { it.second }.average().toFloat()
                    val areaVal = viewModel.calculatePolygonArea()
                    val areaText = "面積 ${viewModel.formatArea(areaVal, selectedUnit)}"

                    drawContext.canvas.nativeCanvas.apply {
                        val textWidth = badgeTextPaint.measureText(areaText)
                        val textHeight = badgeTextPaint.textSize
                        val padH = 34f
                        val padV = 18f
                        val left = centroidX - textWidth / 2f - padH
                        val top = centroidY - textHeight / 2f - padV
                        val right = centroidX + textWidth / 2f + padH
                        val bottom = centroidY + textHeight / 2f + padV
                        val radius = 38f

                        badgeBgPaint.color = 0x66000000
                        drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                        badgeBgPaint.color = Color(colorPrimary.red, colorPrimary.green, colorPrimary.blue, badgeAlpha).toArgb()
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                        badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                        badgeTextPaint.color = colorOnPrimary.toArgb()
                        drawText(areaText, centroidX, centroidY + textHeight * 0.35f, badgeTextPaint)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. ANGLE MODE (3D Corner Angle & Vertex Arc Sector)
        // -------------------------------------------------------------
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
                    strokeWidth = effectiveLineThickness.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Arm 2: P1 (Vertex) -> P2
            if (p1 != null && p2 != null) {
                drawLine(
                    color = colorSecondary,
                    start = Offset(p1.first, p1.second),
                    end = Offset(p2.first, p2.second),
                    strokeWidth = effectiveLineThickness.dp.toPx(),
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
                    size = Size(arcRadius * 2f, arcRadius * 2f),
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
                    val padH = 28f
                    val padV = 16f
                    val left = badgeX - textWidth / 2f - padH
                    val top = badgeY - textHeight / 2f - padV
                    val right = badgeX + textWidth / 2f + padH
                    val bottom = badgeY + textHeight / 2f + padV
                    val radius = 34f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = Color(colorTertiary.red, colorTertiary.green, colorTertiary.blue, badgeAlpha).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.Black.toArgb()
                    drawText(angleText, badgeX, badgeY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 3. HEIGHT MODE (Vertical Plumb & Ground Horizon Alignment)
        // -------------------------------------------------------------
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
                    color = colorTertiary,
                    start = pBase,
                    end = pPlumb,
                    strokeWidth = (effectiveLineThickness + 1.5f).dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(pBase.x + 1.5f, pBase.y + 2f),
                    end = Offset(pPlumb.x + 1.5f, pPlumb.y + 2f),
                    strokeWidth = (effectiveLineThickness + 3f).dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Horizontal Guide Line (Plumb Foot -> Top Target)
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = pPlumb,
                    end = pTop,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(DASH_10_6, dashPhase.value)
                )

                // Right-Angle Indicator at Plumb Corner
                val cornerSize = 14.dp.toPx()
                val vPlumb = Offset(pBase.x - pPlumb.x, pBase.y - pPlumb.y)
                val lenPlumb = sqrt(vPlumb.x * vPlumb.x + vPlumb.y * vPlumb.y)
                val vHoriz = Offset(pTop.x - pPlumb.x, pTop.y - pPlumb.y)
                val lenHoriz = sqrt(vHoriz.x * vHoriz.x + vHoriz.y * vHoriz.y)

                if (lenPlumb > 18f && lenHoriz > 18f) {
                    val uPlumb = Offset(vPlumb.x / lenPlumb, vPlumb.y / lenPlumb)
                    val uHoriz = Offset(vHoriz.x / lenHoriz, vHoriz.y / lenHoriz)
                    val cP1 = Offset(pPlumb.x + uPlumb.x * cornerSize, pPlumb.y + uPlumb.y * cornerSize)
                    val cP2 = Offset(pPlumb.x + (uPlumb.x + uHoriz.x) * cornerSize, pPlumb.y + (uPlumb.y + uHoriz.y) * cornerSize)
                    val cP3 = Offset(pPlumb.x + uHoriz.x * cornerSize, pPlumb.y + uHoriz.y * cornerSize)
                    drawLine(colorTertiary, cP1, cP2, 2.dp.toPx())
                    drawLine(colorTertiary, cP2, cP3, 2.dp.toPx())
                }

                // Floating Vertical Height Badge
                val midHeightX = (pBase.x + pPlumb.x) / 2f
                val midHeightY = (pBase.y + pPlumb.y) / 2f
                val hMeters = if (pointsSnapshot.size >= 2) {
                    viewModel.calculateVerticalHeight()
                } else {
                    abs(top3D.y - base3D.y)
                }
                val hText = "垂直高度 ${viewModel.formatLength(hMeters, selectedUnit)}"

                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(hText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 30f
                    val padV = 16f
                    val left = midHeightX - textWidth / 2f - padH
                    val top = midHeightY - textHeight / 2f - padV
                    val right = midHeightX + textWidth / 2f + padH
                    val bottom = midHeightY + textHeight / 2f + padV
                    val radius = 34f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = Color(colorTertiary.red, colorTertiary.green, colorTertiary.blue, badgeAlpha).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.White.toArgb()
                    drawText(hText, midHeightX, midHeightY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 4. ACTIVE DYNAMIC VIRTUAL LINE & RUBBER-BAND PREVIEW
        // -------------------------------------------------------------
        val isActivelyDrawingLine = when {
            isAreaMode -> if (subMode == 0 && projectedPoints.size >= 3) false else projectedPoints.isNotEmpty()
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
                    // Dynamic dashed measurement line
                    drawLine(
                        color = colorPrimary,
                        start = startOffset,
                        end = currentReticlePos,
                        strokeWidth = effectiveLineThickness.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(DASH_14_10, dashPhase.value),
                        cap = StrokeCap.Round
                    )

                    // Solid endpoint cap
                    drawCircle(
                        color = Color.White,
                        center = currentReticlePos,
                        radius = 4.dp.toPx()
                    )
                    drawCircle(
                        color = colorSecondary,
                        center = currentReticlePos,
                        radius = 2.dp.toPx()
                    )

                    // Area Mode: Live rubber-band closing dashed line from reticle to first point
                    if (isAreaMode && projectedPoints.size >= 2) {
                        val firstPt = projectedPoints.first()
                        if (firstPt != null) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.65f),
                                start = currentReticlePos,
                                end = Offset(firstPt.first, firstPt.second),
                                strokeWidth = 2.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(DASH_10_8, dashPhase.value)
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
                                size = Size(liveArcR * 2f, liveArcR * 2f),
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
                            val padH = 32f
                            val padV = 16f
                            val left = midX - textWidth / 2f - padH
                            val top = midY - textHeight / 2f - padV
                            val right = midX + textWidth / 2f + padH
                            val bottom = midY + textHeight / 2f + padV
                            val radius = 34f

                            badgeBgPaint.color = 0x55000000
                            drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                            badgeBgPaint.color = Color(colorPrimary.red, colorPrimary.green, colorPrimary.blue, badgeAlpha).toArgb()
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                            badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                            badgeTextPaint.color = colorOnPrimary.toArgb()
                            drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 5. MediaPipe Objectron 3D Bounding Box Wireframe
        // -------------------------------------------------------------
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
                    val padH = 28f
                    val padV = 16f
                    val left = cOffset.x - textWidth / 2f - padH
                    val top = cOffset.y - textHeight / 2f - padV
                    val right = cOffset.x + textWidth / 2f + padH
                    val bottom = cOffset.y + textHeight / 2f + padV
                    val radius = 34f

                    badgeBgPaint.color = 0x66000000
                    drawRoundRect(left + 2f, top + 4f, right + 2f, bottom + 4f, radius, radius, badgeBgPaint)
                    badgeBgPaint.color = Color(boxAmber.red, boxAmber.green, boxAmber.blue, badgeAlpha).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = Color.White.copy(alpha = 0.85f).toArgb()
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = Color.Black.toArgb()
                    drawText(volText, cOffset.x, cOffset.y + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 6. MobileSAM Segment Anything Mask & Boundary
        // -------------------------------------------------------------
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
                        pathEffect = PathEffect.dashPathEffect(DASH_24_12, 0f)
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

        // -------------------------------------------------------------
        // 7. AI Detected Tiles AR Bounding Frame
        // -------------------------------------------------------------
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
                            size = Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )
                        drawRoundRect(
                            color = tileGold,
                            topLeft = Offset(leftPx, topPx),
                            size = Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(DASH_20_10, 0f)
                            )
                        )
                    } else {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(leftPx, topPx),
                            size = Size(tWidth, tHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(DASH_12_12, 0f)
                            )
                        )
                    }

                    // Batched Corner L-Brackets
                    val bracketLen = minOf(tWidth, tHeight) * 0.20f
                    val bracketColor = if (isRevealed) tileCyan else Color.White.copy(alpha = 0.65f)
                    val bracketStroke = if (isRevealed) 3.5.dp.toPx() else 2.dp.toPx()

                    bracketBatchCachedPath.apply {
                        reset()
                        // Top-Left
                        moveTo(leftPx, topPx + bracketLen)
                        lineTo(leftPx, topPx)
                        lineTo(leftPx + bracketLen, topPx)
                        // Top-Right
                        moveTo(rightPx - bracketLen, topPx)
                        lineTo(rightPx, topPx)
                        lineTo(rightPx, topPx + bracketLen)
                        // Bottom-Left
                        moveTo(leftPx, bottomPx - bracketLen)
                        lineTo(leftPx, bottomPx)
                        lineTo(leftPx + bracketLen, bottomPx)
                        // Bottom-Right
                        moveTo(rightPx - bracketLen, bottomPx)
                        lineTo(rightPx, bottomPx)
                        lineTo(rightPx, bottomPx - bracketLen)
                    }
                    drawPath(
                        path = bracketBatchCachedPath,
                        color = bracketColor,
                        style = Stroke(width = bracketStroke, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 8. High-Visibility Billboard Anchor Pin Node Markers
        // -------------------------------------------------------------
        pointsSnapshot.forEachIndexed { index, pt3d ->
            val proj = projectedPoints.getOrNull(index)
            if (proj != null) {
                val offset = Offset(proj.first, proj.second)
                val isStartNode = index == 0
                val isLastNode = index == pointsSnapshot.size - 1 && pointsSnapshot.size > 1
                val nodeColor = if (isStartNode) colorPrimary else if (isLastNode) colorSecondary else colorTertiary

                // Luminous pulse aura around active/latest node
                if (isLastNode || isStartNode) {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.22f * (2f - reticlePulseScale.value)),
                        center = offset,
                        radius = (14.dp * reticlePulseScale.value).toPx(),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Outer ambient shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    center = Offset(offset.x + 1f, offset.y + 1.5f),
                    radius = 9.dp.toPx()
                )

                // Outer colored anchor pin ring
                drawCircle(
                    color = nodeColor,
                    center = offset,
                    radius = 8.dp.toPx()
                )

                // Inner white contrast disc
                drawCircle(
                    color = Color.White,
                    center = offset,
                    radius = 5.5.dp.toPx()
                )

                // Core pinpoint center dot
                drawCircle(
                    color = nodeColor,
                    center = offset,
                    radius = 3.5.dp.toPx()
                )

                // Floating Node Label Letter (A, B, C, D...)
                val labelChar = ('A'.code + index).toChar().toString()
                drawContext.canvas.nativeCanvas.apply {
                    val labelBgRadius = 16f
                    val labelCenterY = offset.y - 18.dp.toPx()
                    badgeBgPaint.color = 0xDD000000.toInt()
                    drawCircle(offset.x, labelCenterY, labelBgRadius, badgeBgPaint)
                    badgeBorderPaint.color = nodeColor.toArgb()
                    drawCircle(offset.x, labelCenterY, labelBgRadius, badgeBorderPaint)
                    drawText(labelChar, offset.x, labelCenterY + 8f, nodeLabelPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 9. Modern Dynamic 3D Target Reticle (根據 reticleStyle 繪製)
        // -------------------------------------------------------------
        val reticleCenter = currentReticlePos
        val baseRadius = 16.dp.toPx()
        val currentRadius = baseRadius * snapScaleAnimated.value * reticlePulseScale.value

        // Snapping outer aura
        if (isSnapped) {
            drawCircle(
                color = colorTertiary.copy(alpha = snapGlowAlphaAnimated.value * 0.45f),
                center = reticleCenter,
                radius = currentRadius * 1.8f
            )
        }

        when (reticleStyle) {
            "MINIMAL_DOT" -> {
                // Minimalist glowing dot
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    center = Offset(reticleCenter.x + 0.5f, reticleCenter.y + 1f),
                    radius = 6.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    center = reticleCenter,
                    radius = 5.dp.toPx()
                )
                drawCircle(
                    color = if (isSnapped) colorTertiary else colorPrimary,
                    center = reticleCenter,
                    radius = 3.dp.toPx()
                )
                if (isSnapped) {
                    drawCircle(
                        color = colorTertiary,
                        center = reticleCenter,
                        radius = 12.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            "PRECISION_CROSSHAIR" -> {
                // High precision crosshair with distance ticks
                val crossColor = if (isSnapped) colorTertiary else colorPrimary
                val armLen = 24.dp.toPx()
                val gap = 6.dp.toPx()
                val strokeW = 2.dp.toPx()

                // 4 Crosshair Arms
                drawLine(crossColor, Offset(reticleCenter.x - armLen, reticleCenter.y), Offset(reticleCenter.x - gap, reticleCenter.y), strokeW, StrokeCap.Round)
                drawLine(crossColor, Offset(reticleCenter.x + gap, reticleCenter.y), Offset(reticleCenter.x + armLen, reticleCenter.y), strokeW, StrokeCap.Round)
                drawLine(crossColor, Offset(reticleCenter.x, reticleCenter.y - armLen), Offset(reticleCenter.x, reticleCenter.y - gap), strokeW, StrokeCap.Round)
                drawLine(crossColor, Offset(reticleCenter.x, reticleCenter.y + gap), Offset(reticleCenter.x, reticleCenter.y + armLen), strokeW, StrokeCap.Round)

                // Sub-ticks
                val subLen = 3.5.dp.toPx()
                val subPos = 14.dp.toPx()
                drawLine(crossColor.copy(alpha = 0.7f), Offset(reticleCenter.x - subPos, reticleCenter.y - subLen), Offset(reticleCenter.x - subPos, reticleCenter.y + subLen), 1.2.dp.toPx())
                drawLine(crossColor.copy(alpha = 0.7f), Offset(reticleCenter.x + subPos, reticleCenter.y - subLen), Offset(reticleCenter.x + subPos, reticleCenter.y + subLen), 1.2.dp.toPx())
                drawLine(crossColor.copy(alpha = 0.7f), Offset(reticleCenter.x - subLen, reticleCenter.y - subPos), Offset(reticleCenter.x + subLen, reticleCenter.y - subPos), 1.2.dp.toPx())
                drawLine(crossColor.copy(alpha = 0.7f), Offset(reticleCenter.x - subLen, reticleCenter.y + subPos), Offset(reticleCenter.x + subLen, reticleCenter.y + subPos), 1.2.dp.toPx())

                // Center aperture
                drawCircle(color = Color.White, center = reticleCenter, radius = 2.5.dp.toPx())
            }

            "HOLOGRAPHIC_RADAR" -> {
                // Holographic radar rotating rings
                val radarColor = if (isSnapped) colorTertiary else colorPrimary
                // Outer dashed ring
                drawCircle(
                    color = radarColor.copy(alpha = 0.65f),
                    center = reticleCenter,
                    radius = currentRadius * 1.3f,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(DASH_10_6, dashPhase.value))
                )
                // Inner solid ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    center = reticleCenter,
                    radius = currentRadius * 0.75f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // 4 Cardinal tick pips
                for (angle in 0 until 360 step 90) {
                    val rad = Math.toRadians((angle + dashPhase.value * 4).toDouble())
                    val pStart = Offset(reticleCenter.x + (cos(rad) * currentRadius * 0.9f).toFloat(), reticleCenter.y + (sin(rad) * currentRadius * 0.9f).toFloat())
                    val pEnd = Offset(reticleCenter.x + (cos(rad) * currentRadius * 1.35f).toFloat(), reticleCenter.y + (sin(rad) * currentRadius * 1.35f).toFloat())
                    drawLine(radarColor, pStart, pEnd, 2.dp.toPx(), StrokeCap.Round)
                }
                // Center core
                drawCircle(color = Color.White, center = reticleCenter, radius = 3.dp.toPx())
                drawCircle(color = radarColor, center = reticleCenter, radius = 1.8.dp.toPx())
            }

            else -> { // "DOUBLE_RING" (Default)
                // Dual concentric rings with shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    center = Offset(reticleCenter.x + 1f, reticleCenter.y + 1.5f),
                    radius = currentRadius,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = if (isSnapped) colorTertiary else colorPrimary,
                    center = reticleCenter,
                    radius = currentRadius,
                    style = Stroke(width = if (isSnapped) 2.8.dp.toPx() else 2.0.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    center = reticleCenter,
                    radius = currentRadius * 0.5f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(color = Color.White, center = reticleCenter, radius = 3.5.dp.toPx())
                drawCircle(color = if (isSnapped) colorTertiary else colorPrimary, center = reticleCenter, radius = 2.0.dp.toPx())
            }
        }

        // Multi-Sample Burst Averaging Dynamic Progress Arc
        if (sensorTelemetry.multiSampleProgress > 0f) {
            val arcRadius = currentRadius + 6.dp.toPx()
            drawArc(
                color = if (sensorTelemetry.isMultiSampleLocked) colorTertiary else colorPrimary,
                startAngle = -90f,
                sweepAngle = sensorTelemetry.multiSampleProgress * 360f,
                useCenter = false,
                topLeft = Offset(reticleCenter.x - arcRadius, reticleCenter.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Plane Locked Micro Particle Feedback Ring
        if (planesCount > 0) {
            val particleCount = 8
            for (i in 0 until particleCount) {
                val angle = (i * (360f / particleCount)) + (planeLockedParticleAnim.value * 360f)
                val rad = Math.toRadians(angle.toDouble())
                val orbitRadius = (20.dp.toPx()) + (sin(planeLockedParticleAnim.value * 6.28318f + i).toFloat() * 2.5.dp.toPx())
                val px = reticleCenter.x + (cos(rad).toFloat() * orbitRadius)
                val py = reticleCenter.y + (sin(rad).toFloat() * orbitRadius)
                val sineVal = sin(planeLockedParticleAnim.value * 6.28318f + i)
                val pAlpha = ((sineVal + 1f) / 2f).coerceIn(0.25f, 0.9f)

                drawCircle(
                    color = colorPrimary.copy(alpha = pAlpha),
                    center = Offset(px, py),
                    radius = 2f * density
                )
            }
        }
    }
}
