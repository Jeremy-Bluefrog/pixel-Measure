package com.example.logic.camera

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * High-performance AR Measurement Video Recorder.
 * Supports:
 * 1. Long-press shutter hold/toggle recording for AR measurement sessions.
 * 2. Real-time duration timer & state flow.
 * 3. Frame snapshot capture & video file generation (.mp4).
 * 4. Automatic export and registration into Android System Gallery / MediaStore (Movies/MeasureApp).
 */
class ArVideoRecorder(private val context: Context) {

    companion object {
        private const val TAG = "ArVideoRecorder"
        private const val MAX_RECORDING_SECONDS = 120 // Max 2 minutes
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
    private val capturedFrameBitmaps = mutableListOf<Bitmap>()
    private var firstThumbnailPath: String? = null

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Starts recording the AR measurement view.
     */
    fun startRecording(view: View, onStarted: (() -> Unit)? = null) {
        if (_isRecording.value) return

        targetView = view
        _isRecording.value = true
        _recordingSeconds.value = 0
        recordingStartTime = System.currentTimeMillis()
        capturedFrameBitmaps.clear()
        firstThumbnailPath = null

        recordScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // 1. Timer job (updates every 1 second)
        timerJob = recordScope?.launch {
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

        // 2. Continuous frame sampling for smooth video synthesis
        frameJob = recordScope?.launch(Dispatchers.IO) {
            while (isActive && _isRecording.value) {
                captureFrameInternal(view)
                delay(100) // 10 FPS keyframe sampling
            }
        }
    }

    private fun captureFrameInternal(view: View) {
        try {
            if (view.width <= 0 || view.height <= 0) return
            val scaledW = (view.width / 2).coerceAtLeast(320)
            val scaledH = (view.height / 2).coerceAtLeast(480)
            val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)

            val activity = findActivity(view.context)
            val window = activity?.window

            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val loc = IntArray(2)
                view.getLocationInWindow(loc)
                val srcRect = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)

                val latch = java.util.concurrent.CountDownLatch(1)
                PixelCopy.request(
                    window,
                    srcRect,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            synchronized(capturedFrameBitmaps) {
                                if (capturedFrameBitmaps.size < 600) {
                                    capturedFrameBitmaps.add(bitmap)
                                }
                            }
                            if (firstThumbnailPath == null) {
                                firstThumbnailPath = saveThumbnail(bitmap)
                            }
                        }
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper())
                )
                latch.await(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                val canvas = Canvas(bitmap)
                canvas.scale(0.5f, 0.5f)
                view.draw(canvas)
                synchronized(capturedFrameBitmaps) {
                    if (capturedFrameBitmaps.size < 600) {
                        capturedFrameBitmaps.add(bitmap)
                    }
                }
                if (firstThumbnailPath == null) {
                    firstThumbnailPath = saveThumbnail(bitmap)
                }
            }
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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

        val framesCopy = synchronized(capturedFrameBitmaps) {
            capturedFrameBitmaps.toList()
        }

        recordScope?.launch(Dispatchers.IO) {
            val videoFile = generateMp4VideoFile(framesCopy, duration)
            var savedUri: Uri? = null

            if (videoFile != null && videoFile.exists()) {
                savedUri = saveVideoToMediaStore(videoFile)
            }

            val finalVideoPath = savedUri?.toString() ?: videoFile?.absolutePath
            val thumbPath = firstThumbnailPath

            withContext(Dispatchers.Main) {
                onComplete(finalVideoPath, thumbPath, duration)
                recordScope?.cancel()
                recordScope = null
            }
        }
    }

    /**
     * Generates a standard playable MP4 file from captured frames using MediaCodec & MediaMuxer.
     */
    private fun generateMp4VideoFile(frames: List<Bitmap>, durationSec: Int): File? {
        val videoDir = File(context.filesDir, "measure_videos")
        if (!videoDir.exists()) videoDir.mkdirs()
        val outputFile = File(videoDir, "AR_Measure_Video_${System.currentTimeMillis()}.mp4")

        if (frames.isEmpty()) {
            // Write a placeholder minimal file
            try {
                outputFile.createNewFile()
                return outputFile
            } catch (e: Exception) {
                return null
            }
        }

        val width = (frames.first().width / 2) * 2 // Must be even
        val height = (frames.first().height / 2) * 2 // Must be even

        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null

        try {
            val mimeType = "video/avc"
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 10)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = (1_000_000L / 10L) // 10 FPS
            var presentationTimeUs = 0L

            for (i in 0 until (durationSec * 10).coerceAtLeast(frames.size)) {
                val frameBitmap = frames[i % frames.size]
                val scaled = if (frameBitmap.width != width || frameBitmap.height != height) {
                    Bitmap.createScaledBitmap(frameBitmap, width, height, true)
                } else frameBitmap

                val yuvBytes = convertBitmapToYuv420(scaled, width, height)

                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(yuvBytes)
                    encoder.queueInputBuffer(inputIndex, 0, yuvBytes.size, presentationTimeUs, 0)
                    presentationTimeUs += frameDurationUs
                }

                var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            // End of stream
            val eosInputIndex = encoder.dequeueInputBuffer(10000)
            if (eosInputIndex >= 0) {
                encoder.queueInputBuffer(eosInputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                if (bufferInfo.size > 0 && muxerStarted) {
                    outputBuffer?.position(bufferInfo.offset)
                    outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding MP4: ${e.message}", e)
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        return outputFile
    }

    private fun convertBitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    /**
     * Saves the recorded MP4 file to system Movies gallery.
     */
    private fun saveVideoToMediaStore(videoFile: File): Uri? {
        val filename = "MeasureVideo_${System.currentTimeMillis()}.mp4"
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MeasureApp")
                }
                val resolver = context.contentResolver
                uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { out ->
                        videoFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
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
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore: ${e.message}", e)
        }
        return uri
    }
}
