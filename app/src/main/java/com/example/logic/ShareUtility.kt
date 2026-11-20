package com.example.logic

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.TextureView
import android.view.View
import android.view.Window
import androidx.core.content.FileProvider
import com.example.data.model.MeasureRecord
import com.example.ui.theme.loadRoundedNativeTypeface
import com.example.ui.viewmodel.Point3D
import com.example.ui.viewmodel.deserializePoints
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

object ShareUtility {

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Captures a composite AR measurement photo combining:
     * 1. Real Hardware Camera frame (from TextureView bitmap or GLSurfaceView render buffer).
     * 2. AR measurement overlay graphics (lines, points, text labels, badges, angles).
     * 3. Professional watermark stamp (AR measurement branding, current measurement value, date/time).
     *
     * Cropped to target Aspect Ratio (4:3, 16:9, 1:1, or FULL) and saved as a high-quality JPEG.
     */
    fun captureArCompositeSnapshot(
        view: View,
        cameraBitmap: Bitmap?,
        textureView: TextureView? = null,
        glView: View? = null,
        aspectRatio: String = "4_3",
        useDisplayP3: Boolean = true,
        primaryMeasurement: String? = null,
        langCode: String = "zh-TW",
        onComplete: (String?) -> Unit
    ) {
        if (view.width <= 0 || view.height <= 0) {
            onComplete(null)
            return
        }

        val width = view.width
        val height = view.height
        val compositeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compositeBitmap)

        // 1. Draw live hardware camera preview
        if (cameraBitmap != null && !cameraBitmap.isRecycled) {
            val srcRect = Rect(0, 0, cameraBitmap.width, cameraBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(cameraBitmap, srcRect, dstRect, paint)
        } else {
            // Elegant dark space background if hardware camera stream is unavailable
            val darkGrad = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.rgb(15, 23, 42),
                    Color.rgb(30, 41, 59),
                    Color.rgb(15, 23, 42)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint().apply { shader = darkGrad }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // 2. Hide hardware preview views (TextureView / GLSurfaceView) so their opaque surfaces do not wipe out cameraBitmap
        val prevTvVisibility = textureView?.visibility
        val prevGlVisibility = glView?.visibility

        textureView?.visibility = View.INVISIBLE
        glView?.visibility = View.INVISIBLE

        // Draw view hierarchy (AR overlay lines, distance tags, badges) on top
        try {
            view.draw(canvas)
        } catch (e: Exception) {
            android.util.Log.e("ShareUtility", "Error drawing overlay on composite snapshot", e)
        } finally {
            if (prevTvVisibility != null) textureView.visibility = prevTvVisibility
            if (prevGlVisibility != null) glView.visibility = prevGlVisibility
        }

        // 3. Draw Watermark Tag (Bottom Left)
        try {
            drawArMeasurementWatermark(view.context, canvas, width, height, primaryMeasurement, langCode)
        } catch (e: Exception) {
            android.util.Log.w("ShareUtility", "Watermark drawing error", e)
        }

        // 4. Crop to Aspect Ratio and save
        val croppedBitmap = cropBitmapToAspectRatio(compositeBitmap, aspectRatio)
        val savedPath = saveBitmapToInternalStorage(view.context, croppedBitmap, useDisplayP3)
        try {
            if (croppedBitmap != compositeBitmap) {
                compositeBitmap.recycle()
            }
            croppedBitmap.recycle()
        } catch (_: Throwable) {}
        onComplete(savedPath)
    }

    private fun drawArMeasurementWatermark(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        primaryMeasurement: String?,
        langCode: String = "zh-TW"
    ) {
        val padX = 36f
        val padY = height - 44f
        val roundedBold = loadRoundedNativeTypeface(context, isBold = true)
        val roundedReg = loadRoundedNativeTypeface(context, isBold = false)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(175, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            typeface = roundedBold
        }

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 226, 232, 240)
            textSize = 20f
            typeface = roundedReg
        }

        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val timeStr = dateFormat.format(Date())
        val badgeTitle = TranslationManager.getString("share_badge_title", langCode)
        val measurementStr = if (!primaryMeasurement.isNullOrBlank() && primaryMeasurement != "0.0 cm" && primaryMeasurement != "0.00 m") {
            " · $primaryMeasurement"
        } else ""

        val displayText = "$badgeTitle$measurementStr"
        val textWidth = max(textPaint.measureText(displayText), subTextPaint.measureText(timeStr))
        val boxRect = RectF(padX, padY - 60f, padX + textWidth + 36f, padY + 16f)

        canvas.drawRoundRect(boxRect, 16f, 16f, bgPaint)
        canvas.drawRoundRect(boxRect, 16f, 16f, borderPaint)
        canvas.drawText(displayText, padX + 18f, padY - 26f, textPaint)
        canvas.drawText(timeStr, padX + 18f, padY + 2f, subTextPaint)
    }

    /**
     * Captures a screenshot of the specified view and saves it to local app internal storage.
     * Supports customizable Aspect Ratio cropping (4:3, 16:9, 1:1, FULL) and Display P3 wide color gamut export.
     */
    fun captureViewSnapshot(
        view: View,
        aspectRatio: String = "4_3",
        useDisplayP3: Boolean = true,
        onComplete: (String?) -> Unit
    ) {
        captureArCompositeSnapshot(
            view = view,
            cameraBitmap = null,
            aspectRatio = aspectRatio,
            useDisplayP3 = useDisplayP3,
            primaryMeasurement = null,
            onComplete = onComplete
        )
    }

    private fun cropBitmapToAspectRatio(src: Bitmap, aspectRatio: String): Bitmap {
        if (aspectRatio == "FULL") return src

        val srcW = src.width
        val srcH = src.height
        if (srcW <= 0 || srcH <= 0) return src

        val isPortrait = srcH >= srcW
        val targetRatio = when (aspectRatio) {
            "4_3" -> if (isPortrait) 3f / 4f else 4f / 3f
            "16_9" -> if (isPortrait) 9f / 16f else 16f / 9f
            "1_1" -> 1.0f
            else -> return src
        }

        val currentRatio = srcW.toFloat() / srcH.toFloat()
        var cropW = srcW
        var cropH = srcH

        if (currentRatio > targetRatio) {
            // Source is wider than target ratio: crop width
            cropW = (srcH * targetRatio).toInt().coerceAtMost(srcW)
        } else {
            // Source is taller than target ratio: crop height
            cropH = (srcW / targetRatio).toInt().coerceAtMost(srcH)
        }

        val startX = ((srcW - cropW) / 2).coerceAtLeast(0)
        val startY = ((srcH - cropH) / 2).coerceAtLeast(0)

        return try {
            Bitmap.createBitmap(src, startX, startY, cropW, cropH)
        } catch (e: Exception) {
            src
        }
    }

    private fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        useDisplayP3: Boolean = true
    ): String? {
        return try {
            val screenshotsDir = File(context.filesDir, "screenshots")
            if (!screenshotsDir.exists()) screenshotsDir.mkdirs()

            val file = File(screenshotsDir, "measure_snap_${System.currentTimeMillis()}.jpg")

            val targetBitmap = if (useDisplayP3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bitmap.colorSpace != ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
            ) {
                try {
                    val p3Bmp = Bitmap.createBitmap(
                        bitmap.width,
                        bitmap.height,
                        Bitmap.Config.ARGB_8888,
                        true,
                        ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
                    )
                    Canvas(p3Bmp).drawBitmap(bitmap, 0f, 0f, null)
                    p3Bmp
                } catch (_: Throwable) {
                    bitmap
                }
            } else {
                bitmap
            }

            try {
                FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }
            } finally {
                if (targetBitmap !== bitmap && !targetBitmap.isRecycled) {
                    targetBitmap.recycle()
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Primary Sharing Handler: Shares the measurement record with formatted text and screenshot attachment
     * via Android System Share Sheet to Email, Instant Messaging apps (LINE, WhatsApp, Telegram, Messenger), etc.
     */
    private fun resolveShareMediaUri(context: Context, rawPath: String?): Uri? {
        if (rawPath.isNullOrBlank()) return null
        return try {
            if (rawPath.startsWith("content://") || rawPath.startsWith("file://")) {
                Uri.parse(rawPath)
            } else {
                val file = File(rawPath)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareRecord(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        try {
            val textReport = formatTextReport(record, langCode)
            val subject = TranslationManager.formatString("share_subject_record_fmt", langCode, record.title)

            val videoUri = resolveShareMediaUri(context, record.videoPath)
            var imageUri: Uri? = if (videoUri == null) resolveShareMediaUri(context, record.imagePath) else null

            // If neither video nor screenshot exists, generate a sleek blueprint card bitmap
            if (videoUri == null && imageUri == null) {
                var bitmap: Bitmap? = null
                try {
                    bitmap = generateMeasurementBitmap(record, context, langCode)
                    val cacheFile = File(context.cacheDir, "measure_share_${record.id}.jpg")
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        out.flush()
                    }
                    imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cacheFile
                    )
                } catch (e: Exception) {
                    // Fall back to text only
                } finally {
                    try {
                        if (bitmap != null && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    } catch (_: Throwable) {}
                }
            }

            val streamUri = videoUri ?: imageUri
            val mimeType = when {
                videoUri != null -> "video/mp4"
                imageUri != null -> "image/jpeg"
                else -> "text/plain"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, textReport)
                type = mimeType
                if (streamUri != null) {
                    putExtra(Intent.EXTRA_STREAM, streamUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, TranslationManager.getString("share_chooser_record", langCode))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Dedicated Email Share Handler: Pre-fills subject, message body, and screenshot attachment.
     */
    fun shareViaEmail(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        try {
            val textReport = formatTextReport(record, langCode)
            val subject = TranslationManager.formatString("share_subject_report_fmt", langCode, record.title)

            val mediaUri = resolveShareMediaUri(context, record.videoPath)
                ?: resolveShareMediaUri(context, record.imagePath)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, textReport)
                if (mediaUri != null) {
                    putExtra(Intent.EXTRA_STREAM, mediaUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, TranslationManager.getString("share_chooser_email", langCode))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            shareRecord(context, record, langCode)
        }
    }

    /**
     * Copies measurement text report directly to system clipboard.
     */
    fun copyToClipboard(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = ClipData.newPlainText(
                TranslationManager.getString("share_clip_label", langCode),
                formatTextReport(record, langCode)
            )
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }

    /**
     * Exports all measurement records as a professional CSV file and opens the system share sheet.
     */
    fun exportAllRecordsCsv(context: Context, records: List<MeasureRecord>, langCode: String = "zh-TW") {
        if (records.isEmpty()) {
            return
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "AR_Measure_Report_${fileDateFormat.format(Date())}.csv"

            val csvDir = File(context.cacheDir, "csv_exports")
            if (!csvDir.exists()) csvDir.mkdirs()
            val csvFile = File(csvDir, fileName)

            csvFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Write UTF-8 BOM for Microsoft Excel compatibility
                writer.write("\uFEFF")
                writer.write("ID,Title,Type,Value,Unit,Timestamp,Notes\n")
                records.forEach { r ->
                    val escapedTitle = "\"${r.title.replace("\"", "\"\"")}\""
                    val escapedNotes = "\"${(r.notes ?: "").replace("\"", "\"\"")}\""
                    val timeStr = dateFormat.format(Date(r.timestamp))
                    writer.write("${r.id},$escapedTitle,${r.type},${r.value},${r.unit},$timeStr,$escapedNotes\n")
                }
            }

            val csvUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, TranslationManager.formatString("share_csv_subject_fmt", langCode, records.size))
                putExtra(Intent.EXTRA_TEXT, TranslationManager.formatString("share_csv_text_fmt", langCode, records.size))
                putExtra(Intent.EXTRA_STREAM, csvUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, TranslationManager.getString("share_csv_chooser", langCode))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Captures a screenshot of the specified view (including SurfaceView like Camera Preview)
     * and saves it to the gallery.
     */
    /**
     * Captures a screenshot of the specified view (including SurfaceView like Camera Preview)
     * and saves it to the gallery with fallback drawing support.
     */
    fun captureAndSaveToGallery(view: View, onComplete: (Uri?) -> Unit) {
        val activity = findActivity(view.context)
        val window = activity?.window

        if (view.width <= 0 || view.height <= 0) {
            onComplete(null)
            return
        }

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)

            try {
                PixelCopy.request(
                    window,
                    Rect(
                        locationOfViewInWindow[0],
                        locationOfViewInWindow[1],
                        locationOfViewInWindow[0] + view.width,
                        locationOfViewInWindow[1] + view.height
                    ),
                    bitmap,
                    { copyResult ->
                        val uri = try {
                            if (copyResult == PixelCopy.SUCCESS) {
                                saveBitmapToGallery(view.context, bitmap)
                            } else {
                                val canvas = Canvas(bitmap)
                                view.draw(canvas)
                                saveBitmapToGallery(view.context, bitmap)
                            }
                        } finally {
                            if (!bitmap.isRecycled) {
                                try { bitmap.recycle() } catch (_: Throwable) {}
                            }
                        }
                        onComplete(uri)
                    },
                    Handler(Looper.getMainLooper())
                )
                return
            } catch (e: Exception) {
                // Fallback below
            }
        }

        try {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            val uri = saveBitmapToGallery(view.context, bitmap)
            onComplete(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        } finally {
            if (!bitmap.isRecycled) {
                try { bitmap.recycle() } catch (_: Throwable) {}
            }
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "MeasureScreenshot_${System.currentTimeMillis()}.jpg"
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MeasureApp")
                }
                val contentResolver = context.contentResolver
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/MeasureApp"
                val file = File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val imageFile = File(imagesDir, filename)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                uri = Uri.fromFile(imageFile)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri
    }

    private val AR_SPATIAL_RECORD_TYPES = setOf("CAM", "DISTANCE", "AREA", "HEIGHT", "VOLUME", "CIRCLE", "ANGLE")

    private fun isArSpatialRecordType(type: String): Boolean = type in AR_SPATIAL_RECORD_TYPES

    private fun formatLengthValueForUnit(meters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.2f m", meters)
            "mm" -> String.format(Locale.US, "%.0f mm", meters * 1000.0)
            "in" -> String.format(Locale.US, "%.2f in", meters * 39.3701)
            "ft" -> String.format(Locale.US, "%.2f ft", meters * 3.28084)
            "yd" -> String.format(Locale.US, "%.2f yd", meters * 1.09361)
            else -> String.format(Locale.US, "%.1f cm", meters * 100.0)
        }
    }

    private fun formatAreaValueForUnit(sqMeters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.2f m²", sqMeters)
            "in" -> String.format(Locale.US, "%.1f in²", sqMeters * 1550.0)
            "ft" -> String.format(Locale.US, "%.2f sq ft", sqMeters * 10.7639)
            "yd" -> String.format(Locale.US, "%.2f sq yd", sqMeters * 1.19599)
            else -> String.format(Locale.US, "%.1f cm²", sqMeters * 10000.0)
        }
    }

    private fun formatVolumeValueForUnit(cuMeters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.3f m³", cuMeters)
            "ft" -> String.format(Locale.US, "%.2f cu ft", cuMeters * 35.3147)
            else -> String.format(Locale.US, "%.2f L", cuMeters * 1000.0)
        }
    }

    internal fun formatRecordPrimaryValue(record: MeasureRecord, langCode: String = "zh-TW"): String {
        return when (record.type) {
            "RULER" -> {
                val cm = record.value * 100.0
                "${String.format(Locale.US, "%.2f", cm)} cm (${String.format(Locale.US, "%.1f", cm * 10.0)} mm)"
            }
            "LEVEL" -> TranslationManager.formatString("share_level_tilt_fmt", langCode, record.value)
            "AREA" -> formatAreaValueForUnit(record.value, record.unit)
            "VOLUME" -> formatVolumeValueForUnit(record.value, record.unit)
            "ANGLE" -> "${Math.round(record.value)}°"
            else -> formatLengthValueForUnit(record.value, record.unit)
        }
    }

    private fun resolveModeLabel(type: String, langCode: String): String {
        val key = when (type) {
            "CAM", "DISTANCE" -> "share_mode_distance"
            "AREA" -> "share_mode_area"
            "HEIGHT" -> "share_mode_height"
            "VOLUME" -> "share_mode_vol"
            "CIRCLE" -> "share_mode_circle"
            "ANGLE" -> "share_mode_angle"
            "RULER" -> "share_mode_ruler"
            "LEVEL" -> "share_mode_level"
            else -> "share_mode_other"
        }
        return TranslationManager.getString(key, langCode)
    }

    /**
     * Formats a clean structural text report of the measurement
     */
    fun formatTextReport(record: MeasureRecord, langCode: String = "zh-TW"): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(record.timestamp))
        val modeStr = resolveModeLabel(record.type, langCode)

        val sb = StringBuilder()
        sb.append("===============================\n")
        sb.append("${TranslationManager.getString("share_rpt_header", langCode)}\n")
        sb.append("===============================\n")
        sb.append("${TranslationManager.formatString("share_rpt_name_fmt", langCode, record.title)}\n")
        sb.append("${TranslationManager.formatString("share_rpt_time_fmt", langCode, dateStr)}\n")
        sb.append("${TranslationManager.formatString("share_rpt_mode_fmt", langCode, modeStr)}\n")
        sb.append("${TranslationManager.formatString("share_rpt_val_fmt", langCode, formatRecordPrimaryValue(record, langCode))}\n")

        if (!record.notes.isNullOrBlank()) {
            sb.append("${TranslationManager.formatString("share_rpt_notes_fmt", langCode, record.notes)}\n")
        }

        if (isArSpatialRecordType(record.type) && !record.pointsData.isNullOrBlank()) {
            val points = record.pointsData.deserializePoints()
            if (points.isNotEmpty()) {
                sb.append("\n${TranslationManager.getString("share_rpt_pts_header", langCode)}\n")
                points.forEachIndexed { index, p ->
                    val lbl = if (!p.label.isNullOrBlank()) " - \"${p.label}\"" else ""
                    val line = TranslationManager.formatString(
                        "share_rpt_pt_fmt",
                        langCode,
                        index + 1,
                        String.format(Locale.US, "%.2f", p.x),
                        String.format(Locale.US, "%.2f", p.y),
                        String.format(Locale.US, "%.2f", p.z),
                        lbl
                    )
                    sb.append("$line\n")
                }

                if (points.size >= 2) {
                    sb.append("\n${TranslationManager.getString("share_rpt_segs_header", langCode)}\n")
                    val step = if (record.type == "AREA" || record.type == "ANGLE") 1 else 2
                    for (i in 0 until points.size - 1 step step) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val dist = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))
                        val lbl1 = if (!p1.label.isNullOrBlank()) p1.label else "#${i + 1}"
                        val lbl2 = if (!p2.label.isNullOrBlank()) p2.label else "#${i + 2}"
                        val distLabel = formatLengthValueForUnit(dist, if (record.unit == "°") "cm" else record.unit)
                        sb.append("   👉 [$lbl1] ↗ [$lbl2] : $distLabel\n")
                    }
                }
            }
        }
        sb.append("===============================\n")
        sb.append(TranslationManager.getString("share_rpt_footer", langCode))
        return sb.toString()
    }

    /**
     * Shares formatted text report via plain intent
     */
    fun shareTextReport(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        try {
            val report = formatTextReport(record, langCode)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, TranslationManager.formatString("share_text_subject_fmt", langCode, record.title))
                putExtra(Intent.EXTRA_TEXT, report)
            }
            val chooser = Intent.createChooser(intent, TranslationManager.getString("share_text_chooser", langCode)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates a structural bitmap image representing this measurement card
     */
    fun generateMeasurementBitmap(record: MeasureRecord, context: Context? = null, langCode: String = "zh-TW"): Bitmap {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val roundedBold = if (context != null) loadRoundedNativeTypeface(context, isBold = true) else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        
        // 1. Draw elegant dark background Slate900 (#001E2E equivalent or #0F172A)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // 2. Draw Blueprint Grid inside layout
        val gridPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val step = 40f
        for (x in 0..(width / step.toInt())) {
            canvas.drawLine(x * step, 0f, x * step, height.toFloat(), gridPaint)
        }
        for (y in 0..(height / step.toInt())) {
            canvas.drawLine(0f, y * step, width.toFloat(), y * step, gridPaint)
        }
        
        // 3. Draw Header Section Top Card
        val cardPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, 40f, width - 40f, 220f, 24f, 24f, cardPaint)
        
        // Title Text
        val titlePaint = Paint().apply {
            color = Color.parseColor("#F59E0B") // MeasureYellow
            textSize = 36f
            typeface = roundedBold
            isAntiAlias = true
        }
        canvas.drawText(record.title, 80f, 100f, titlePaint)
        
        // Value Text (Huge display)
        val valPaint = Paint().apply {
            color = Color.WHITE
            textSize = 54f
            typeface = roundedBold
            isAntiAlias = true
        }
        val formattedVal = when (record.type) {
            "RULER" -> String.format(Locale.US, "%.2f cm", record.value * 100.0)
            "LEVEL" -> String.format(Locale.US, "%.1f°", record.value)
            "AREA" -> formatAreaValueForUnit(record.value, record.unit)
            "VOLUME" -> formatVolumeValueForUnit(record.value, record.unit)
            "ANGLE" -> "${Math.round(record.value)}°"
            else -> formatLengthValueForUnit(record.value, record.unit)
        }
        canvas.drawText(formattedVal, 80f, 175f, valPaint)

        // Meta Tag (Mode Label)
        val metaPaint = Paint().apply {
            color = Color.parseColor("#94A3B8") // CadetBlue
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(record.timestamp))
        val modeStr = resolveModeLabel(record.type, langCode)
        canvas.drawText("$modeStr | $dateStr", 80f, 210f, metaPaint)
        
        // Notes if any
        var currentY = 280f
        if (!record.notes.isNullOrBlank()) {
            val notesCardPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(40f, 240f, width - 40f, 340f, 16f, 16f, notesCardPaint)
            
            val notesPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText(TranslationManager.formatString("share_bp_notes_fmt", langCode, record.notes), 60f, 295f, notesPaint)
            currentY = 380f
        }
        
        // 4. Drawing Blueprint Sketch area
        val blueprintCardPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            alpha = 128
            style = Paint.Style.FILL
        }
        val drawTop = currentY
        val drawBottom = height - 100f
        canvas.drawRoundRect(40f, drawTop, width - 40f, drawBottom, 24f, 24f, blueprintCardPaint)
        
        // Drawn Outer Stroke of Blueprint
        val borderPaint = Paint().apply {
            color = Color.parseColor("#0EA5E9") // PrecisionCyan
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, drawTop, width - 40f, drawBottom, 24f, 24f, borderPaint)
        
        // Blueprint labels & Title
        val bpLabelPaint = Paint().apply {
            color = Color.parseColor("#38BDF8") // Clearer cyan
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(TranslationManager.getString("share_bp_title", langCode), 60f, drawTop + 40f, bpLabelPaint)
        
        // Map points and draw the graph
        if (isArSpatialRecordType(record.type) && !record.pointsData.isNullOrBlank()) {
            val points = record.pointsData.deserializePoints()
            if (points.isNotEmpty()) {
                val cx = width / 2f
                val cy = drawTop + (drawBottom - drawTop) / 2f

                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }

                val rangeX = maxX - minX
                val rangeY = maxY - minY
                val maxRange = max(max(rangeX, rangeY), 0.1)

                // Max dimensions to sit inside the blueprint
                val fitW = width - 200f
                val fitH = (drawBottom - drawTop) - 150f
                val scale = min(fitW / maxRange, fitH / maxRange).toFloat()

                val midX = (minX + maxX) / 2.0
                val midY = (minY + maxY) / 2.0

                // Transform helper mapping 3D space to 2D coordinates
                val mappedPoints: List<android.graphics.PointF> = points.map { p ->
                    val px = cx + ((p.x - midX) * scale).toFloat()
                    val py = cy - ((p.y - midY) * scale).toFloat() // Flip Y for traditional screen space look
                    android.graphics.PointF(px, py)
                }

                // Set Up drawing details
                val linePaint = Paint().apply {
                    color = Color.parseColor("#0EA5E9") // PrecisionCyan
                    strokeWidth = 6f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }

                // Connect lines (AREA/ANGLE consecutive vertices; DISTANCE pairs)
                val isPolyOrChain = record.type == "AREA" || record.type == "ANGLE"
                val stepCount = if (isPolyOrChain) 1 else 2
                val edgeCount = if (record.type == "AREA" && mappedPoints.size >= 3) mappedPoints.size else mappedPoints.size - 1
                for (i in 0 until edgeCount step stepCount) {
                    val nextIdx = (i + 1) % mappedPoints.size
                    val p1 = mappedPoints[i]
                    val p2 = mappedPoints[nextIdx]
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)

                    // Segment Label text
                    val dist = sqrt((points[i].x - points[nextIdx].x).pow(2) + (points[i].y - points[nextIdx].y).pow(2) + (points[i].z - points[nextIdx].z).pow(2))
                    val segmentText = formatLengthValueForUnit(dist, if (record.unit == "°") "cm" else record.unit)
                    
                    // Box placard in segment center
                    val mx = (p1.x + p2.x) / 2f
                    val my = (p1.y + p2.y) / 2f
                    
                    val textBgPaint = Paint().apply {
                        color = Color.parseColor("#0F172A")
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(mx - 60f, my - 20f, mx + 60f, my + 20f, 8f, 8f, textBgPaint)
                    canvas.drawRoundRect(mx - 60f, my - 20f, mx + 60f, my + 20f, 8f, 8f, borderPaint)
                    
                    val midTextPaint = Paint().apply {
                        color = Color.parseColor("#38BDF8")
                        textSize = 18f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    canvas.drawText(segmentText, mx, my + 6f, midTextPaint)
                }
                
                // Draw dots and annotation texts
                val dotPaintFill = Paint().apply {
                    color = Color.parseColor("#F59E0B") // MeasureYellow
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                
                val dotBorder = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                
                val textAnnoLabelPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                mappedPoints.forEachIndexed { i: Int, offset: android.graphics.PointF ->
                    canvas.drawCircle(offset.x, offset.y, 16f, dotPaintFill)
                    canvas.drawCircle(offset.x, offset.y, 16f, dotBorder)
                    
                    // Inside number index
                    val numPaint = Paint().apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 20f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    canvas.drawText((i + 1).toString(), offset.x, offset.y + 7f, numPaint)
                    
                    // Label text on screen
                    val pLabel = points[i].label
                    val visualLabel = if (!pLabel.isNullOrBlank()) {
                        TranslationManager.formatString("share_bp_pt_named_fmt", langCode, i + 1, pLabel)
                    } else {
                        TranslationManager.formatString("share_bp_pt_fmt", langCode, i + 1)
                    }
                    canvas.drawText(visualLabel, offset.x + 24f, offset.y + 6f, textAnnoLabelPaint)
                }
            }
        } else {
            // Draw regular screen ruler layout graphic
            val rulerLinePaint = Paint().apply {
                color = Color.parseColor("#F59E0B")
                strokeWidth = 4f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val cx = width / 2f
            val cy = drawTop + (drawBottom - drawTop) / 2f
            
            canvas.drawCircle(cx, cy, 100f, borderPaint)
            canvas.drawLine(cx - 200f, cy, cx + 200f, cy, rulerLinePaint)
            canvas.drawLine(cx - 200f, cy - 30f, cx - 200f, cy + 30f, rulerLinePaint)
            canvas.drawLine(cx + 200f, cy - 30f, cx + 200f, cy + 30f, rulerLinePaint)
            
            val rulerTextLabelPaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(TranslationManager.getString("share_bp_ruler_line", langCode), cx, cy - 60f, rulerTextLabelPaint)
        }
        
        // Draw Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#475569") // GraySlate
            textSize = 18f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(TranslationManager.getString("share_bp_footer", langCode), width / 2f, height - 40f, footerPaint)
        
        return bitmap
    }

    /**
     * Generates a fully compliant PDF file and launches share sheet
     */
    fun sharePdfReport(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        var pdfDocument: PdfDocument? = null
        var bitmap: Bitmap? = null
        try {
            pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Standard point Size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Draw a styled card onto the PDF using a scaled version of our bitmap
            bitmap = generateMeasurementBitmap(record, context, langCode)
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(20, 20, 575, 822) // padding elements

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            pdfDocument.finishPage(page)

            // Save inside local cache
            val outputDir = context.cacheDir
            val pdfFile = File(outputDir, "MeasureReport_${record.id}.pdf")
            FileOutputStream(pdfFile).use { fileOutputStream ->
                pdfDocument.writeTo(fileOutputStream)
                fileOutputStream.flush()
            }

            // Generate File URI via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, TranslationManager.formatString("share_pdf_subj_fmt", langCode, record.title))
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, TranslationManager.getString("share_pdf_chooser", langCode)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { pdfDocument?.close() } catch (_: Throwable) {}
            try {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Generates a high quality JPEG/PNG and saves to public downloads as well as launch share sheet
     */
    fun shareImageReport(context: Context, record: MeasureRecord, langCode: String = "zh-TW") {
        var bitmap: Bitmap? = null
        try {
            bitmap = generateMeasurementBitmap(record, context, langCode)

            // Save to local cache first
            val outputDir = context.cacheDir
            val imageFile = File(outputDir, "MeasureReport_${record.id}.jpg")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }

            // Try saving to public downloads so user can access directly
            try {
                val safeTitle = record.title.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]"), "_")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val publicFile = File(downloadsDir, "MeasureReport_${safeTitle}_${record.id}.jpg")
                    FileOutputStream(publicFile).use { pubOut ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, pubOut)
                        pubOut.flush()
                    }
                }
            } catch (secError: Exception) {
                // Ignore permission issues for writing to physical external public directories, share sheet fallback excels
            }

            // Generate File URI via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_SUBJECT, TranslationManager.formatString("share_img_subj_fmt", langCode, record.title))
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, TranslationManager.getString("share_img_chooser", langCode)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Shares arbitrary formatted text using standard Android Share Sheet
     */
    fun shareText(
        context: Context,
        text: String,
        title: String = TranslationManager.getString("share_text_default_title", "zh-TW")
    ) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Silently handled
        }
    }

    /**
     * Sends user problem feedback to dedicated support email (jeremy1030623@gmail.com)
     */
    fun sendFeedbackEmail(
        context: Context,
        targetEmail: String = "jeremy1030623@gmail.com",
        langCode: String = "zh-TW"
    ) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val appInfo = buildString {
            appendLine()
            appendLine("-------------------------")
            appendLine(TranslationManager.getString("share_fb_debug_header", langCode))
            appendLine(TranslationManager.formatString("share_fb_app_label", langCode, TranslationManager.getString("share_fb_app_name", langCode)))
            appendLine(TranslationManager.formatString("share_fb_os_label", langCode, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
            appendLine(TranslationManager.formatString("share_fb_device_label", langCode, Build.MANUFACTURER, Build.MODEL))
            appendLine(TranslationManager.formatString("share_fb_hw_label", langCode, Build.HARDWARE))
            appendLine(TranslationManager.formatString("share_fb_time_label", langCode, nowStr))
            append("-------------------------")
        }

        val subj = TranslationManager.getString("share_email_feedback_subj", langCode)
        val body = "${TranslationManager.getString("share_email_feedback_prompt", langCode)}\n\n\n$appInfo"
        val chooserTitle = TranslationManager.getString("share_email_feedback_chooser", langCode)

        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$targetEmail")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(targetEmail))
                putExtra(Intent.EXTRA_SUBJECT, subj)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            val chooser = Intent.createChooser(emailIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(targetEmail))
                    putExtra(Intent.EXTRA_SUBJECT, subj)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                val chooser = Intent.createChooser(fallbackIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (err: Exception) {
                copyToClipboard(
                    context,
                    targetEmail,
                    TranslationManager.formatString("share_email_copied_fmt", langCode, targetEmail)
                )
            }
        }
    }

    /**
     * Exports all measurement records as a structured CSV/Text summary for easy sharing.
     */
    fun exportRecordsAsCsvText(context: Context, records: List<MeasureRecord>, langCode: String = "zh-TW") {
        if (records.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                TranslationManager.getString("share_export_empty_toast", langCode),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val sb = StringBuilder()
        sb.append("${TranslationManager.getString("share_csv_txt_header", langCode)}\n")
        val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        sb.append("${TranslationManager.formatString("share_csv_txt_time_fmt", langCode, exportedAt)}\n\n")
        for ((idx, rec) in records.withIndex()) {
            sb.append("${TranslationManager.formatString("share_csv_txt_idx_fmt", langCode, idx + 1)}\n")
            sb.append("${TranslationManager.formatString("share_csv_txt_type_fmt", langCode, rec.type)}\n")
            sb.append("${TranslationManager.formatString("share_csv_txt_val_fmt", langCode, rec.value.toString(), rec.unit)}\n")
            val recDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rec.timestamp))
            sb.append("${TranslationManager.formatString("share_csv_txt_date_fmt", langCode, recDate)}\n")
            if (!rec.notes.isNullOrBlank()) {
                sb.append("${TranslationManager.formatString("share_csv_txt_notes_fmt", langCode, rec.notes)}\n")
            }
            sb.append("------------------------------------\n")
        }
        val text = sb.toString()
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, TranslationManager.getString("share_csv_txt_subj", langCode))
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, TranslationManager.getString("share_csv_txt_chooser", langCode)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            copyToClipboard(context, text, TranslationManager.getString("share_copied_report_toast", langCode))
        }
    }

    /**
     * Copies text to system clipboard with Toast prompt
     */
    fun copyToClipboard(
        context: Context,
        text: String,
        toastMsg: String = TranslationManager.getString("share_copied_default_toast", "zh-TW")
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboard?.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
