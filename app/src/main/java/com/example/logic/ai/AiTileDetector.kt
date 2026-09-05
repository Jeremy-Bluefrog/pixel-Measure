package com.example.logic.ai

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.logic.*
import com.example.logic.ar.ArMath
import com.example.ui.viewmodel.Point3D
import com.google.ar.core.Pose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.*

@Serializable
data class DetectedTile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "磁磚",
    val material: String = "拋光石英磚",
    val estimatedWidthCm: Double = 60.0,
    val estimatedHeightCm: Double = 60.0,
    val areaM2: Double = 0.36,
    // Normalized screen bounding box (0f..1f)
    val leftNorm: Float = 0.25f,
    val topNorm: Float = 0.25f,
    val rightNorm: Float = 0.75f,
    val bottomNorm: Float = 0.75f,
    // 4 Corner 3D World coordinates (Top-Left, Top-Right, Bottom-Right, Bottom-Left)
    val worldCorners: List<Point3D> = emptyList(),
    // 4 Corner Screen coordinates
    @Transient val screenCorners: List<Pair<Float, Float>> = emptyList(),
    val groutWidthMm: Double = 2.0,
    val confidence: Float = 0.95f
) {
    val boundingBox: RectF
        get() = RectF(leftNorm, topNorm, rightNorm, bottomNorm)
}

@Serializable
data class TileEstimationResult(
    val tileWidthCm: Double,
    val tileHeightCm: Double,
    val singleTileAreaM2: Double,
    val targetAreaM2: Double,
    val baseTileCount: Int,
    val wastagePercentage: Int = 10,
    val totalRecommendedTileCount: Int,
    val estimatedGroutLengthMeters: Double
)

object AiTileDetector {

    private const val TAG = "AiTileDetector"

    /**
     * Convert Bitmap to Base64 JPEG string with dynamic dimension clamping to optimize memory & API latency
     */
    fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 80): String {
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * AI Core Vision Tile Recognition using Gemini 3.5 Flash Multimodal API
     */
    suspend fun analyzeTilesWithGemini(
        bitmap: Bitmap,
        apiKey: String = BuildConfig.GEMINI_API_KEY
    ): List<DetectedTile> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key is not set, running on-device computer vision tile detection")
            return@withContext detectTilesFromBitmap(bitmap)
        }

        try {
            val base64Img = bitmapToBase64(bitmap)
            val prompt = """
                You are an expert AI vision system specialized in interior architecture and tile detection.
                Analyze the provided image and identify the floor or wall tiles (磁磚).
                If visible tiles are found, extract their normalized 2D bounding box and specifications.
                If NO tiles are detected in the image, return an empty "tiles": [] array.
                
                Respond ONLY with a JSON object in this exact schema:
                {
                  "tiles": [
                    {
                      "label": "磁磚 #1",
                      "material": "拋光石英磚",
                      "estimatedWidthCm": 60.0,
                      "estimatedHeightCm": 60.0,
                      "box2d": [ymin, xmin, ymax, xmax],
                      "confidence": 0.95,
                      "groutWidthMm": 2.0
                    }
                  ]
                }
                Note: box2d coordinates are normalized from 0 to 1000 (integers).
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img))
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val response = RetrofitClient.service.generateContent(
                model = "gemini-2.5-flash",
                apiKey = apiKey,
                request = request
            )
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d(TAG, "Gemini AI Core raw response: $jsonText")

            val parsedTiles = parseGeminiTileJson(jsonText, bitmap.width, bitmap.height)
            if (parsedTiles.isNotEmpty()) {
                parsedTiles
            } else {
                detectTilesFromBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI Core tile detection failed: ${e.message}", e)
            detectTilesFromBitmap(bitmap)
        }
    }

    /**
     * Parses the Gemini JSON response safely into a list of DetectedTile
     */
    private fun parseGeminiTileJson(jsonString: String, screenW: Int, screenH: Int): List<DetectedTile> {
        val result = mutableListOf<DetectedTile>()
        try {
            // Strip markdown code block if present
            val cleanJson = jsonString
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonElement = Json.parseToJsonElement(cleanJson).jsonObject
            val tilesArray = jsonElement["tiles"]?.jsonArray ?: return emptyList()

            for ((idx, tileItem) in tilesArray.withIndex()) {
                val obj = tileItem.jsonObject
                val label = obj["label"]?.jsonPrimitive?.content ?: "磁磚 #${idx + 1}"
                val material = obj["material"]?.jsonPrimitive?.content ?: "拋光石英磚"
                val wCm = obj["estimatedWidthCm"]?.jsonPrimitive?.doubleOrNull ?: 60.0
                val hCm = obj["estimatedHeightCm"]?.jsonPrimitive?.doubleOrNull ?: 60.0
                val grout = obj["groutWidthMm"]?.jsonPrimitive?.doubleOrNull ?: 2.0
                val conf = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.92f

                val box2d = obj["box2d"]?.jsonArray
                var topN = 0.3f
                var leftN = 0.3f
                var bottomN = 0.7f
                var rightN = 0.7f

                if (box2d != null && box2d.size >= 4) {
                    val ymin = box2d[0].jsonPrimitive.floatOrNull ?: 300f
                    val xmin = box2d[1].jsonPrimitive.floatOrNull ?: 300f
                    val ymax = box2d[2].jsonPrimitive.floatOrNull ?: 700f
                    val xmax = box2d[3].jsonPrimitive.floatOrNull ?: 700f

                    topN = (ymin / 1000f).coerceIn(0.05f, 0.95f)
                    leftN = (xmin / 1000f).coerceIn(0.05f, 0.95f)
                    bottomN = (ymax / 1000f).coerceIn(topN + 0.05f, 0.98f)
                    rightN = (xmax / 1000f).coerceIn(leftN + 0.05f, 0.98f)
                }

                val areaM2 = (wCm * hCm) / 10000.0

                val screenTL = Pair(leftN * screenW, topN * screenH)
                val screenTR = Pair(rightN * screenW, topN * screenH)
                val screenBR = Pair(rightN * screenW, bottomN * screenH)
                val screenBL = Pair(leftN * screenW, bottomN * screenH)

                result.add(
                    DetectedTile(
                        id = UUID.randomUUID().toString(),
                        label = label,
                        material = material,
                        estimatedWidthCm = wCm,
                        estimatedHeightCm = hCm,
                        areaM2 = areaM2,
                        leftNorm = leftN,
                        topNorm = topN,
                        rightNorm = rightN,
                        bottomNorm = bottomN,
                        screenCorners = listOf(screenTL, screenTR, screenBR, screenBL),
                        groutWidthMm = grout,
                        confidence = conf
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini tile JSON: ${e.message}", e)
        }
        return result
    }

    /**
     * Real-time local computer vision tile detection.
     * Analyzes luminance edge gradients to find true orthogonal grout lines / joints.
     * Returns a non-empty list ONLY if a valid rectangular tile grid is genuinely detected in the image.
     * If no tiles are detected (e.g. plain surface, wall, clutter, camera covered), returns emptyList().
     */
    fun detectTilesFromBitmap(
        bitmap: Bitmap,
        preset: AiTilePreset = AiTilePreset.PRESET_60X60,
        center3D: Point3D? = null
    ): List<DetectedTile> {
        val originalW = bitmap.width
        val originalH = bitmap.height
        if (originalW < 50 || originalH < 50) return emptyList()

        // Downscale for real-time low-latency CV processing (width ~ 240)
        val procW = 240
        val procH = (originalH * (procW.toFloat() / originalW)).toInt().coerceAtLeast(100)
        val scaled = Bitmap.createScaledBitmap(bitmap, procW, procH, true)
        val pixels = IntArray(procW * procH)
        scaled.getPixels(pixels, 0, procW, 0, 0, procW, procH)
        if (scaled != bitmap) scaled.recycle()

        // Compute grayscale luminance
        val lum = IntArray(procW * procH)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        // Horizontal and Vertical gradient projections across the frame
        val rowEdgeEnergy = FloatArray(procH)
        val colEdgeEnergy = FloatArray(procW)

        var totalEnergy = 0f
        for (y in 1 until procH - 1) {
            val rowOffset = y * procW
            val prevRow = (y - 1) * procW
            val nextRow = (y + 1) * procW
            for (x in 1 until procW - 1) {
                val gx = kotlin.math.abs(lum[rowOffset + x + 1] - lum[rowOffset + x - 1])
                val gy = kotlin.math.abs(lum[nextRow + x] - lum[prevRow + x])
                rowEdgeEnergy[y] += gy.toFloat()
                colEdgeEnergy[x] += gx.toFloat()
                totalEnergy += (gx + gy)
            }
        }

        val avgEnergy = totalEnergy / (procW * procH)
        // If image has virtually no contrast or edges (e.g. uniform color, blocked camera), NO tile!
        if (avgEnergy < 6.0f) {
            return emptyList()
        }

        // Find prominent horizontal and vertical edge peaks representing tile grout lines
        val hAvg = rowEdgeEnergy.average().toFloat()
        val vAvg = colEdgeEnergy.average().toFloat()
        val hThreshold = hAvg * 1.35f
        val vThreshold = vAvg * 1.35f

        val hPeaks = mutableListOf<Int>()
        for (y in (procH * 0.12).toInt() until (procH * 0.88).toInt()) {
            if (rowEdgeEnergy[y] > hThreshold &&
                rowEdgeEnergy[y] > rowEdgeEnergy[y - 1] &&
                rowEdgeEnergy[y] >= rowEdgeEnergy[y + 1]
            ) {
                if (hPeaks.isEmpty() || y - hPeaks.last() > (procH * 0.15)) {
                    hPeaks.add(y)
                }
            }
        }

        val vPeaks = mutableListOf<Int>()
        for (x in (procW * 0.12).toInt() until (procW * 0.88).toInt()) {
            if (colEdgeEnergy[x] > vThreshold &&
                colEdgeEnergy[x] > colEdgeEnergy[x - 1] &&
                colEdgeEnergy[x] >= colEdgeEnergy[x + 1]
            ) {
                if (vPeaks.isEmpty() || x - vPeaks.last() > (procW * 0.15)) {
                    vPeaks.add(x)
                }
            }
        }

        // Must have at least 2 distinct horizontal and 2 distinct vertical grout edges
        if (hPeaks.size < 2 || vPeaks.size < 2) {
            return emptyList()
        }

        val targetCenterY = procH / 2
        val targetCenterX = procW / 2
        var minCenterDist = Float.MAX_VALUE

        var bestTop = hPeaks.first()
        var bestBottom = hPeaks.last()
        var bestLeft = vPeaks.first()
        var bestRight = vPeaks.last()

        for (i in 0 until hPeaks.size - 1) {
            val y1 = hPeaks[i]
            val y2 = hPeaks[i + 1]
            val midY = (y1 + y2) / 2
            val hSpan = y2 - y1
            if (hSpan < procH * 0.18 || hSpan > procH * 0.85) continue

            for (j in 0 until vPeaks.size - 1) {
                val x1 = vPeaks[j]
                val x2 = vPeaks[j + 1]
                val midX = (x1 + x2) / 2
                val wSpan = x2 - x1
                if (wSpan < procW * 0.18 || wSpan > procW * 0.85) continue

                val aspect = wSpan.toFloat() / hSpan.toFloat()
                if (aspect !in 0.4f..2.5f) continue

                val distToCenter = kotlin.math.hypot((midX - targetCenterX).toFloat(), (midY - targetCenterY).toFloat())
                if (distToCenter < minCenterDist) {
                    minCenterDist = distToCenter
                    bestTop = y1
                    bestBottom = y2
                    bestLeft = x1
                    bestRight = x2
                }
            }
        }

        if (minCenterDist == Float.MAX_VALUE) {
            return emptyList()
        }

        val leftNorm = (bestLeft.toFloat() / procW).coerceIn(0.05f, 0.90f)
        val rightNorm = (bestRight.toFloat() / procW).coerceIn(leftNorm + 0.15f, 0.95f)
        val topNorm = (bestTop.toFloat() / procH).coerceIn(0.05f, 0.90f)
        val bottomNorm = (bestBottom.toFloat() / procH).coerceIn(topNorm + 0.15f, 0.95f)

        val wCm = preset.widthCm
        val hCm = preset.heightCm
        val areaM2 = (wCm * hCm) / 10000.0

        val baseCenter = center3D ?: Point3D(0.0, -0.4, -1.2, isArPrecision = true)
        val worldCorners = createTileCorners(baseCenter, wCm / 100.0, hCm / 100.0)

        val screenCorners = listOf(
            Pair(leftNorm * originalW, topNorm * originalH),
            Pair(rightNorm * originalW, topNorm * originalH),
            Pair(rightNorm * originalW, bottomNorm * originalH),
            Pair(leftNorm * originalW, bottomNorm * originalH)
        )

        return listOf(
            DetectedTile(
                id = "tile_cv_${bestLeft}_${bestTop}",
                label = "偵測磁磚 (${wCm.toInt()}×${hCm.toInt()} cm)",
                material = "拋光石英地磚",
                estimatedWidthCm = wCm,
                estimatedHeightCm = hCm,
                areaM2 = areaM2,
                leftNorm = leftNorm,
                topNorm = topNorm,
                rightNorm = rightNorm,
                bottomNorm = bottomNorm,
                worldCorners = worldCorners,
                screenCorners = screenCorners,
                confidence = 0.94f
            )
        )
    }

    /**
     * Legacy helper kept for backwards-compatibility; returns empty list if no tile is detected.
     */
    fun generateLocalVisionTiles(screenW: Int, screenH: Int, center3D: Point3D? = null): List<DetectedTile> {
        return emptyList()
    }

    /**
     * Creates 4 3D world corners around a given 3D center point on the horizontal plane.
     */
    fun createTileCorners(center: Point3D, widthMeters: Double, heightMeters: Double): List<Point3D> {
        val halfW = widthMeters / 2.0
        val halfH = heightMeters / 2.0
        return listOf(
            Point3D(center.x - halfW, center.y, center.z - halfH, label = "起點 A (磁磚左上)", isArPrecision = true),
            Point3D(center.x + halfW, center.y, center.z - halfH, label = "節點 B (磁磚右上)", isArPrecision = true),
            Point3D(center.x + halfW, center.y, center.z + halfH, label = "節點 C (磁磚右下)", isArPrecision = true),
            Point3D(center.x - halfW, center.y, center.z + halfH, label = "終點 D (磁磚左下)", isArPrecision = true)
        )
    }

    /**
     * Computes the 4 3D physical world corners [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
     * for a tile placed on a detected plane given its center Pose and width/height in meters.
     */
    fun create3DWorldTileCorners(
        centerPose: Pose,
        widthMeters: Double = 0.60,
        heightMeters: Double = 0.60
    ): List<Point3D> {
        val halfW = (widthMeters / 2.0).toFloat()
        val halfH = (heightMeters / 2.0).toFloat()

        // 4 local corners on the plane X-Z grid
        val localTL = floatArrayOf(-halfW, 0f, -halfH)
        val localTR = floatArrayOf(halfW, 0f, -halfH)
        val localBR = floatArrayOf(halfW, 0f, halfH)
        val localBL = floatArrayOf(-halfW, 0f, halfH)

        val worldTL = centerPose.transformPoint(localTL)
        val worldTR = centerPose.transformPoint(localTR)
        val worldBR = centerPose.transformPoint(localBR)
        val worldBL = centerPose.transformPoint(localBL)

        return listOf(
            Point3D(worldTL[0].toDouble(), worldTL[1].toDouble(), worldTL[2].toDouble(), label = "起點 A (磁磚左上)", isArPrecision = true),
            Point3D(worldTR[0].toDouble(), worldTR[1].toDouble(), worldTR[2].toDouble(), label = "節點 B (磁磚右上)", isArPrecision = true),
            Point3D(worldBR[0].toDouble(), worldBR[1].toDouble(), worldBR[2].toDouble(), label = "節點 C (磁磚右下)", isArPrecision = true),
            Point3D(worldBL[0].toDouble(), worldBL[1].toDouble(), worldBL[2].toDouble(), label = "終點 D (磁磚左下)", isArPrecision = true)
        )
    }

    /**
     * Calculates room construction tile estimate (總磁磚需求片數與備料損耗預估)
     */
    fun estimateTileRequirement(
        tileWidthCm: Double,
        tileHeightCm: Double,
        roomAreaM2: Double,
        wastagePercent: Int = 10
    ): TileEstimationResult {
        val singleTileM2 = (tileWidthCm * tileHeightCm) / 10000.0
        val baseCount = if (singleTileM2 > 0) ceil(roomAreaM2 / singleTileM2).toInt() else 0
        val totalCount = ceil(baseCount * (1.0 + wastagePercent / 100.0)).toInt()

        // Estimated grout length: (Perimeter / 2) * baseCount
        val perimeterMeters = ((tileWidthCm + tileHeightCm) * 2.0) / 100.0
        val groutLen = (perimeterMeters / 2.0) * baseCount

        return TileEstimationResult(
            tileWidthCm = tileWidthCm,
            tileHeightCm = tileHeightCm,
            singleTileAreaM2 = singleTileM2,
            targetAreaM2 = roomAreaM2,
            baseTileCount = baseCount,
            wastagePercentage = wastagePercent,
            totalRecommendedTileCount = totalCount,
            estimatedGroutLengthMeters = groutLen
        )
    }
}
