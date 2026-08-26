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
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.MeasureRecord
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
     * Captures a screenshot of the specified view and saves it to local app internal storage.
     * Works seamlessly for both AR Camera views and 2D Ruler views.
     */
    fun captureViewSnapshot(view: View, onComplete: (String?) -> Unit) {
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
                        if (copyResult == PixelCopy.SUCCESS) {
                            val savedPath = saveBitmapToInternalStorage(view.context, bitmap)
                            onComplete(savedPath)
                        } else {
                            // Fallback to view drawing
                            val canvas = Canvas(bitmap)
                            view.draw(canvas)
                            val savedPath = saveBitmapToInternalStorage(view.context, bitmap)
                            onComplete(savedPath)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                return
            } catch (e: Exception) {
                // Fallback below
            }
        }

        // Standard View Draw fallback
        try {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            val savedPath = saveBitmapToInternalStorage(view.context, bitmap)
            onComplete(savedPath)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }

    private fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val screenshotsDir = File(context.filesDir, "screenshots")
            if (!screenshotsDir.exists()) screenshotsDir.mkdirs()

            val file = File(screenshotsDir, "measure_snap_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
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
    fun shareRecord(context: Context, record: MeasureRecord) {
        try {
            val textReport = formatTextReport(record)
            val subject = "【AR 測量紀錄】${record.title}"

            var imageUri: Uri? = null
            if (!record.imagePath.isNullOrBlank()) {
                val file = File(record.imagePath)
                if (file.exists()) {
                    imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }

            // If no screenshot exists, generate a sleek blueprint card bitmap
            if (imageUri == null) {
                try {
                    val bitmap = generateMeasurementBitmap(record)
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
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, textReport)
                if (imageUri != null) {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }

            val chooser = Intent.createChooser(intent, "分享測量紀錄至...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "分享失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * Dedicated Email Share Handler: Pre-fills subject, message body, and screenshot attachment.
     */
    fun shareViaEmail(context: Context, record: MeasureRecord) {
        try {
            val textReport = formatTextReport(record)
            val subject = "【AR 測量報告】${record.title}"

            var imageUri: Uri? = null
            if (!record.imagePath.isNullOrBlank()) {
                val file = File(record.imagePath)
                if (file.exists()) {
                    imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, textReport)
                if (imageUri != null) {
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, "透過電子郵件發送報告")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            shareRecord(context, record)
        }
    }

    /**
     * Copies measurement text report directly to system clipboard.
     */
    fun copyToClipboard(context: Context, record: MeasureRecord) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AR 測量紀錄", formatTextReport(record))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "測量紀錄已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }

    /**
     * Captures a screenshot of the specified view (including SurfaceView like Camera Preview)
     * and saves it to the gallery.
     */
    fun captureScreen(window: Window, view: View, onComplete: (Uri?) -> Unit) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
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
                    if (copyResult == PixelCopy.SUCCESS) {
                        onComplete(saveBitmapToGallery(view.context, bitmap))
                    } else {
                        onComplete(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: IllegalArgumentException) {
            onComplete(null)
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
                // Trigger media scanner
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri
    }

    /**
     * Formats a clean structural text report of the measurement
     */
    fun formatTextReport(record: MeasureRecord): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(record.timestamp))
        val modeStr = if (record.type == "CAM") "相機 AR 測量" else "螢幕卡鉗直尺"
        
        val sb = StringBuilder()
        sb.append("===============================\n")
        sb.append("        數位 AR 測量報告        \n")
        sb.append("===============================\n")
        sb.append("✍️ 測量名稱 : ${record.title}\n")
        sb.append("⏱️ 測量時間 : $dateStr\n")
        sb.append("🔧 測量模式 : $modeStr\n")
        sb.append("📐 測量數值 : ${String.format("%.2f %s", record.value, record.unit)}\n")
        
        if (!record.notes.isNullOrBlank()) {
            sb.append("📝 備註事項 : ${record.notes}\n")
        }
        
        if (record.type == "CAM" && !record.pointsData.isNullOrBlank()) {
            val points = record.pointsData.deserializePoints()
            if (points.isNotEmpty()) {
                sb.append("\n📍 [AR 空間標註點紀錄] :\n")
                points.forEachIndexed { index, p ->
                    val lbl = if (!p.label.isNullOrBlank()) " - \"${p.label}\"" else ""
                    sb.append("   - 標註點 #${index + 1}: ${String.format("(X:%.2f, Y:%.2f, Z:%.2f)", p.x, p.y, p.z)}$lbl\n")
                }
                
                if (points.size >= 2) {
                    sb.append("\n📐 [分段長度分析] :\n")
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val dist = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))
                        
                        // Convert meters to record unit
                        val cmValue = dist * 100.0
                        val displayDist = when (record.unit) {
                            "m" -> cmValue / 100.0
                            "in" -> cmValue / 2.54
                            "ft" -> cmValue / 30.48
                            else -> cmValue
                        }
                        
                        val lbl1 = if (!p1.label.isNullOrBlank()) p1.label else "#${i + 1}"
                        val lbl2 = if (!p2.label.isNullOrBlank()) p2.label else "#${i + 2}"
                        sb.append("   👉 [$lbl1] ↗ [$lbl2] : ${String.format("%.2f %s", displayDist, record.unit)}\n")
                    }
                }
            }
        }
        sb.append("===============================\n")
        sb.append("產生自 Google AI Studio 高精測量 App")
        return sb.toString()
    }

    /**
     * Shares formatted text report via plain intent
     */
    fun shareTextReport(context: Context, record: MeasureRecord) {
        val report = formatTextReport(record)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AR測量結果: ${record.title}")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "分享測量文字報告"))
    }

    /**
     * Generates a structural bitmap image representing this measurement card
     */
    fun generateMeasurementBitmap(record: MeasureRecord): Bitmap {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
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
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(record.title, 80f, 100f, titlePaint)
        
        // Value Text (Huge display)
        val valPaint = Paint().apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val formattedVal = String.format("%.2f %s", record.value, record.unit)
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
        val modeStr = if (record.type == "CAM") "相機 AR 測量模式" else "螢幕高精密直尺"
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
            canvas.drawText("備註: ${record.notes}", 60f, 295f, notesPaint)
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
        canvas.drawText("⚡ 3D 空間投影佈線圖", 60f, drawTop + 40f, bpLabelPaint)
        
        // Map points and draw the graph
        if (record.type == "CAM" && !record.pointsData.isNullOrBlank()) {
            val points = record.pointsData.deserializePoints()
            if (points.isNotEmpty()) {
                val cx = width / 2f
                val cy = drawTop + (drawBottom - drawTop) / 2f
                
                // Find bounding range to scale automatically
                var minX = Double.MAX_VALUE
                var maxX = Double.MIN_VALUE
                var minY = Double.MAX_VALUE
                var maxY = Double.MIN_VALUE
                
                points.forEach { p ->
                    if (p.x < minX) minX = p.x
                    if (p.x > maxX) maxX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.y > maxY) maxY = p.y
                }
                
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
                
                val textMeasurer = Paint().apply {
                    color = Color.WHITE
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                // Connect lines
                for (i in 0 until mappedPoints.size - 1) {
                    val p1 = mappedPoints[i]
                    val p2 = mappedPoints[i + 1]
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                    
                    // Segment Label text
                    val dist = sqrt((points[i].x - points[i+1].x).pow(2) + (points[i].y - points[i+1].y).pow(2) + (points[i].z - points[i+1].z).pow(2))
                    val cmValue = dist * 100.0
                    val displayDist = when (record.unit) {
                        "m" -> cmValue / 100.0
                        "in" -> cmValue / 2.54
                        "ft" -> cmValue / 30.48
                        else -> cmValue
                    }
                    val segmentText = String.format("%.1f %s", displayDist, record.unit)
                    
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
                    val visualLabel = if (!pLabel.isNullOrBlank()) "點${i+1}: $pLabel" else "標記點 ${i+1}"
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
            canvas.drawText("螢幕卡鉗測量精準對接線", cx, cy - 60f, rulerTextLabelPaint)
        }
        
        // Draw Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#475569") // GraySlate
            textSize = 18f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("© 智慧型 AI 3D 測量儀與標誌分享工具 報告自動生成器", width / 2f, height - 40f, footerPaint)
        
        return bitmap
    }

    /**
     * Generates a fully compliant PDF file and launches share sheet
     */
    fun sharePdfReport(context: Context, record: MeasureRecord) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Standard point Size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // Draw a styled card onto the PDF using a scaled version of our bitmap
            val bitmap = generateMeasurementBitmap(record)
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(20, 20, 575, 822) // padding elements
            
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            
            pdfDocument.finishPage(page)
            
            // Save inside local cache
            val outputDir = context.cacheDir
            val pdfFile = File(outputDir, "MeasureReport_${record.id}.pdf")
            val fileOutputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOutputStream)
            pdfDocument.close()
            fileOutputStream.close()
            
            // Generate File URI via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "高精公分測量報告: ${record.title}")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享 PDF 測量報告"))
            
        } catch (e: Exception) {
            Toast.makeText(context, "產生 PDF 失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Generates a high quality JPEG/PNG and saves to public downloads as well as launch share sheet
     */
    fun shareImageReport(context: Context, record: MeasureRecord) {
        try {
            val bitmap = generateMeasurementBitmap(record)
            
            // Save to local cache first
            val outputDir = context.cacheDir
            val imageFile = File(outputDir, "MeasureReport_${record.id}.jpg")
            val out = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
            
            // Try saving to public downloads so user can access directly (and display success path toast)
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val publicFile = File(downloadsDir, "MeasureReport_${record.title.replace(" ", "_")}_${record.id}.jpg")
                    val pubOut = FileOutputStream(publicFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, pubOut)
                    pubOut.flush()
                    pubOut.close()
                    Toast.makeText(context, "圖檔已自動匯出至下載目錄！\n${publicFile.name}", Toast.LENGTH_LONG).show()
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
                putExtra(Intent.EXTRA_SUBJECT, "空間 3D 圖案佈局報告: ${record.title}")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享圖案佈置報告"))
            
        } catch (e: Exception) {
            Toast.makeText(context, "產生圖檔失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Shares arbitrary formatted text using standard Android Share Sheet
     */
    fun shareText(context: Context, text: String, title: String = "分享資訊") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
