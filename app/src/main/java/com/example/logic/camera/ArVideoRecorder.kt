package com.example.logic.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * High-performance, robust AR Measurement Video Recorder.
 * Features:
 * 1. Disk-cached lightweight frame buffering to prevent OutOfMemory on long recordings.
 * 2. Universal H.264 / AVC and HEVC hardware/software encoding with robust color format negotiation.
 * 3. Resilient frame queuing and EOS draining with guaranteed sample muxing.
 * 4. Android MediaStore export with Scoped Storage support and fallback to app-internal storage.
 */
class ArVideoRecorder(private val context: Context) {

    companion object {
        private const val TAG = "ArVideoRecorder"
        private const val MAX_RECORDING_SECONDS = 120 // Max 2 minutes
        private const val TARGET_FPS = 10
        private const val FRAME_DELAY_MS = 100L // 10 FPS
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private var recordScope: CoroutineScope? = null
    private var timerJob: Job? = null
    private var frameJob: Job? = null

    private var targetView: View? = null
    private var recordingStartTime = 0L
    private val capturedFrameFiles = mutableListOf<File>()
    private var firstThumbnailPath: String? = null
    private var cameraBitmapSupplier: (() -> Bitmap?)? = null

    /**
     * Starts recording the AR measurement view.
     */
    fun startRecording(
        view: View,
        cameraBitmapProvider: (() -> Bitmap?)? = null,
        onStarted: (() -> Unit)? = null
    ) {
        if (_isRecording.value) return

        targetView = view
        cameraBitmapSupplier = cameraBitmapProvider
        _isRecording.value = true
        _recordingSeconds.value = 0
        recordingStartTime = System.currentTimeMillis()

        cleanTempFrameDir()
        synchronized(capturedFrameFiles) {
            capturedFrameFiles.clear()
        }
        firstThumbnailPath = null

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        recordScope = scope

        // 1. Timer job (updates every 1 second)
        timerJob = scope.launch {
            onStarted?.invoke()
            while (isActive && _isRecording.value) {
                delay(1000)
                val currentSec = _recordingSeconds.value + 1
                _recordingSeconds.value = currentSec
                if (currentSec >= MAX_RECORDING_SECONDS) {
                    stopRecording { _, _, _ -> }
                    break
                }
            }
        }

        // 2. Safe continuous frame sampling on Main thread
        frameJob = scope.launch(Dispatchers.Main) {
            while (isActive && _isRecording.value) {
                captureFrameInternal(view)
                delay(FRAME_DELAY_MS)
            }
        }
    }

    private fun getTempFrameDir(): File {
        val dir = File(context.cacheDir, "rec_frames")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cleanTempFrameDir() {
        try {
            val dir = File(context.cacheDir, "rec_frames")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    private fun captureFrameInternal(view: View) {
        try {
            if (view.width <= 0 || view.height <= 0) return

            // Standard 16-aligned resolution for maximum encoder compatibility (e.g. 480x848 or 480x864)
            val baseW = 480
            val aspect = view.height.toFloat() / view.width.toFloat()
            val rawH = (baseW * aspect).toInt()
            val scaledW = (baseW / 16) * 16
            val scaledH = ((rawH / 16) * 16).coerceIn(480, 1280)

            val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Direct hardware camera frame sampling if available
            val cameraBmp = try { cameraBitmapSupplier?.invoke() } catch (_: Throwable) { null }
            if (cameraBmp != null && !cameraBmp.isRecycled) {
                val srcRect = Rect(0, 0, cameraBmp.width, cameraBmp.height)
                val dstRect = Rect(0, 0, scaledW, scaledH)
                canvas.drawBitmap(cameraBmp, srcRect, dstRect, null)
            } else {
                canvas.drawColor(android.graphics.Color.rgb(18, 24, 38))
            }

            // Draw AR overlay view hierarchy safely
            val scaleX = scaledW.toFloat() / view.width.toFloat()
            val scaleY = scaledH.toFloat() / view.height.toFloat()
            canvas.save()
            canvas.scale(scaleX, scaleY)
            try {
                view.draw(canvas)
            } catch (_: Throwable) {}
            canvas.restore()

            // Save to disk to prevent OOM
            val frameIndex = capturedFrameFiles.size
            val frameFile = File(getTempFrameDir(), "frame_${String.format("%04d", frameIndex)}.jpg")
            FileOutputStream(frameFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            synchronized(capturedFrameFiles) {
                capturedFrameFiles.add(frameFile)
            }

            if (firstThumbnailPath == null) {
                firstThumbnailPath = saveThumbnail(bitmap)
            }

            bitmap.recycle()
        } catch (e: Throwable) {
            Log.w(TAG, "Frame capture error: ${e.message}")
        }
    }

    private fun saveThumbnail(bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "record_thumbnails")
            if (!dir.exists()) dir.mkdirs()
            val thumbFile = File(dir, "thumb_${System.currentTimeMillis()}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            thumbFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Stops recording, generates video and registers to Android MediaStore.
     */
    fun stopRecording(
        onComplete: (videoPath: String?, thumbnailPath: String?, durationSeconds: Int) -> Unit
    ) {
        if (!_isRecording.value) {
            onComplete(null, null, 0)
            return
        }

        _isRecording.value = false
        val duration = _recordingSeconds.value.coerceAtLeast(1)

        timerJob?.cancel()
        frameJob?.cancel()
        timerJob = null
        frameJob = null

        val frameFilesCopy = synchronized(capturedFrameFiles) {
            capturedFrameFiles.toList()
        }

        val scope = recordScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch(Dispatchers.IO) {
            val videoFile = generateMp4VideoFile(frameFilesCopy, duration)
            var savedUri: Uri? = null

            if (videoFile != null && videoFile.exists() && videoFile.length() > 0) {
                savedUri = saveVideoToMediaStore(videoFile)
            }

            val finalVideoPath = savedUri?.toString() ?: videoFile?.absolutePath
            val thumbPath = firstThumbnailPath ?: frameFilesCopy.firstOrNull()?.absolutePath

            // Cleanup temp frames
            cleanTempFrameDir()

            withContext(Dispatchers.Main) {
                onComplete(finalVideoPath, thumbPath, duration)
                recordScope?.cancel()
                recordScope = null
            }
        }
    }

    /**
     * Selects supported concrete YUV420 color format for hardware encoder.
     */
    private fun findSupportedColorFormat(codec: MediaCodec, mime: String): Int {
        return try {
            val capabilities = codec.codecInfo.getCapabilitiesForType(mime)
            val supportedFormats = capabilities.colorFormats
            if (supportedFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            } else if (supportedFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            } else {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }
        } catch (_: Throwable) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
    }

    /**
     * Generates a standard playable MP4 file from captured frame files using MediaCodec & MediaMuxer.
     */
    private fun generateMp4VideoFile(frameFiles: List<File>, durationSec: Int): File? {
        val videoDir = File(context.filesDir, "measure_videos")
        if (!videoDir.exists()) videoDir.mkdirs()
        val outputFile = File(videoDir, "AR_Measure_Video_${System.currentTimeMillis()}.mp4")

        if (frameFiles.isEmpty()) {
            Log.w(TAG, "No frames captured for video encoding")
            return null
        }

        val firstBmp = BitmapFactory.decodeFile(frameFiles.first().absolutePath) ?: return null
        val rawW = (firstBmp.width / 16) * 16
        val rawH = (firstBmp.height / 16) * 16
        firstBmp.recycle()

        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var muxerStarted = false
        var sampleCount = 0

        try {
            val mimeTypesToTry = listOf("video/avc", "video/hevc")
            var selectedColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

            var encWidth = rawW
            var encHeight = rawH

            for (mime in mimeTypesToTry) {
                try {
                    val candidate = MediaCodec.createEncoderByType(mime)
                    val formatCode = findSupportedColorFormat(candidate, mime)

                    val caps = candidate.codecInfo.getCapabilitiesForType(mime)
                    val vCaps = caps.videoCapabilities
                    if (vCaps != null) {
                        encWidth = vCaps.supportedWidths.clamp(rawW)
                        encHeight = vCaps.supportedHeights.clamp(rawH)
                        encWidth = (encWidth / 16) * 16
                        encHeight = (encHeight / 16) * 16
                    }

                    val format = MediaFormat.createVideoFormat(mime, encWidth, encHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, formatCode)
                        setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                        setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    candidate.start()
                    encoder = candidate
                    selectedColorFormat = formatCode
                    Log.i(TAG, "Video encoder configured and started: $mime (${encWidth}x$encHeight, colorFormat=$formatCode)")
                    break
                } catch (e: Throwable) {
                    Log.w(TAG, "Encoder $mime initialization failed: ${e.message}")
                    try { encoder?.release() } catch (_: Throwable) {}
                    encoder = null
                }
            }

            if (encoder == null) {
                Log.e(TAG, "No compatible video encoder found on device.")
                return null
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = (1_000_000L / TARGET_FPS)
            var presentationTimeUs = 0L

            val totalFramesToFeed = maxOf(frameFiles.size, durationSec * TARGET_FPS)

            for (i in 0 until totalFramesToFeed) {
                val file = frameFiles[i % frameFiles.size]
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val scaled = if (bmp.width != encWidth || bmp.height != encHeight) {
                    Bitmap.createScaledBitmap(bmp, encWidth, encHeight, true)
                } else bmp

                val yuvBytes = convertBitmapToYuv420(scaled, encWidth, encHeight, selectedColorFormat)
                if (scaled != bmp && !scaled.isRecycled) {
                    scaled.recycle()
                }
                bmp.recycle()

                // Feed input buffer with retries while draining
                var inputIndex = -1
                var attempts = 0
                while (inputIndex < 0 && attempts < 8) {
                    inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex < 0) {
                        drainOutputBuffers(encoder, muxer, bufferInfo, trackIndex, isEos = false) { tIdx ->
                            muxerStarted = true
                            trackIndex = tIdx
                            sampleCount++
                        }
                        attempts++
                    }
                }

                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val bytesToPut = minOf(yuvBytes.size, inputBuffer.remaining())
                        inputBuffer.put(yuvBytes, 0, bytesToPut)
                        encoder.queueInputBuffer(inputIndex, 0, bytesToPut, presentationTimeUs, 0)
                        presentationTimeUs += frameDurationUs
                    }
                }

                // Drain output
                drainOutputBuffers(encoder, muxer, bufferInfo, trackIndex, isEos = false) { tIdx ->
                    muxerStarted = true
                    trackIndex = tIdx
                    sampleCount++
                }
            }

            // Signal End of Stream with retries
            for (attempt in 0 until 12) {
                val eosInputIndex = encoder.dequeueInputBuffer(15_000)
                if (eosInputIndex >= 0) {
                    encoder.queueInputBuffer(eosInputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                } else {
                    drainOutputBuffers(encoder, muxer, bufferInfo, trackIndex, isEos = false) { tIdx ->
                        muxerStarted = true
                        trackIndex = tIdx
                        sampleCount++
                    }
                }
            }

            // Drain remaining EOS output
            for (attempt in 0 until 25) {
                val drainedEos = drainOutputBuffers(encoder, muxer, bufferInfo, trackIndex, isEos = true) { tIdx ->
                    muxerStarted = true
                    trackIndex = tIdx
                    sampleCount++
                }
                if (drainedEos) break
            }

            Log.i(TAG, "Video encoding complete. File size: ${outputFile.length()} bytes, samples: $sampleCount")

        } catch (e: Throwable) {
            Log.e(TAG, "Error encoding MP4: ${e.message}", e)
        } finally {
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try {
                if (muxer != null && muxerStarted && sampleCount > 0) {
                    muxer.stop()
                }
            } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }

        return if (outputFile.exists() && outputFile.length() > 0) outputFile else null
    }

    /**
     * Drains output buffers from MediaCodec into MediaMuxer.
     * Returns true if EOS was reached.
     */
    private fun drainOutputBuffers(
        encoder: MediaCodec,
        muxer: MediaMuxer?,
        bufferInfo: MediaCodec.BufferInfo,
        currentTrackIndex: Int,
        isEos: Boolean,
        onMuxerTrackStarted: (Int) -> Unit
    ): Boolean {
        var trackIndex = currentTrackIndex
        val timeoutUs = if (isEos) 20_000L else 5_000L
        var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        var reachedEos = false

        while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxer != null && trackIndex < 0) {
                    val newFormat = encoder.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    onMuxerTrackStarted(trackIndex)
                    Log.i(TAG, "MediaMuxer output format changed and started. trackIndex=$trackIndex")
                }
            } else if (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0 && muxer != null && trackIndex >= 0 && outputBuffer != null) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    onMuxerTrackStarted(trackIndex)
                }
                encoder.releaseOutputBuffer(outputIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    reachedEos = true
                    break
                }
            }
            outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
        }
        return reachedEos
    }

    /**
     * Converts an ARGB_8888 Bitmap to YUV420 byte array (supporting both SemiPlanar NV12 and Planar I420).
     */
    private fun convertBitmapToYuv420(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        colorFormat: Int
    ): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        val isSemiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

        val totalPixels = width * height
        val uOffset = totalPixels
        val vOffset = totalPixels + (totalPixels / 4)
        var yIndex = 0
        var uvSemiIndex = totalPixels
        var uPlanarIndex = uOffset
        var vPlanarIndex = vOffset

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                // ITU-R BT.601 conversion formula
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uByte = u.coerceIn(0, 255).toByte()
                    val vByte = v.coerceIn(0, 255).toByte()

                    if (isSemiPlanar) {
                        if (uvSemiIndex < yuv.size - 1) {
                            yuv[uvSemiIndex++] = uByte
                            yuv[uvSemiIndex++] = vByte
                        }
                    } else {
                        if (uPlanarIndex < vOffset) yuv[uPlanarIndex++] = uByte
                        if (vPlanarIndex < yuv.size) yuv[vPlanarIndex++] = vByte
                    }
                }
            }
        }
        return yuv
    }

    /**
     * Saves the recorded MP4 file to system Movies gallery with MediaStore IS_PENDING scoped storage.
     */
    private fun saveVideoToMediaStore(videoFile: File): Uri? {
        val filename = "MeasureVideo_${System.currentTimeMillis()}.mp4"
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MeasureApp")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { destUri ->
                    resolver.openOutputStream(destUri)?.use { out ->
                        videoFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(destUri, contentValues, null, null)
                }
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString() + "/MeasureApp"
                val dir = File(moviesDir)
                if (!dir.exists()) dir.mkdirs()
                val targetFile = File(dir, filename)
                videoFile.copyTo(targetFile, overwrite = true)
                uri = Uri.fromFile(targetFile)
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving to MediaStore: ${e.message}", e)
        }
        return uri
    }
}
