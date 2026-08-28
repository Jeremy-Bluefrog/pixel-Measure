package com.example.logic.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.ui.viewmodel.MeasureViewModel
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Modern High-Performance 60 FPS GLSurfaceView for ARCore.
 * Renders:
 * 1. OES Camera background texture with aspect ratio distortion correction.
 * 2. 3D Spatial Feature Point Cloud.
 * 3. Detected 3D Planes (Grid Mesh & Boundaries).
 * Handles synchronized hit-testing and frame extraction on GL thread.
 */
@android.annotation.SuppressLint("ViewConstructor")
class ModernArGlView(
    context: Context,
    private val session: Session,
    private val modernArEngine: ModernArEngine,
    private val viewModel: MeasureViewModel
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    // Camera Background Texture & Shader
    private var textureId = -1
    private var bgProgram = -1
    private var bgPositionAttrib = -1
    private var bgTexCoordAttrib = -1

    // Point Cloud Shader
    private var pointProgram = -1
    private var pointPosAttrib = -1
    private var pointModelViewProjUniform = -1
    private var pointColorUniform = -1
    private var pointPointSizeUniform = -1

    // Plane Shader
    private var planeProgram = -1
    private var planePosAttrib = -1
    private var planeMvpUniform = -1
    private var planeColorUniform = -1

    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f,
         1.0f,  1.0f, 0.0f
    )

    private val quadTexCoords = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )

    private val quadVertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadVertices)
            position(0)
        }

    private val quadTexCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadTexCoords)
            position(0)
        }

    private val transformedTexCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Temporary matrices for GL calculations
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)
    private val viewMat = FloatArray(16)
    private val projMat = FloatArray(16)
    private val isFrameUpdatePending = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 1. Camera Background
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        session.setCameraTextureName(textureId)

        val bgVertCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val bgFragCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """.trimIndent()

        bgProgram = createProgram(bgVertCode, bgFragCode)
        bgPositionAttrib = GLES20.glGetAttribLocation(bgProgram, "a_Position")
        bgTexCoordAttrib = GLES20.glGetAttribLocation(bgProgram, "a_TexCoord")

        // 2. Point Cloud Shaders
        val pointVertCode = """
            uniform mat4 u_ModelViewProjection;
            uniform float u_PointSize;
            attribute vec4 a_Position;
            varying float v_Confidence;
            void main() {
                vec4 pos = u_ModelViewProjection * vec4(a_Position.xyz, 1.0);
                gl_Position = pos;
                // Scale point size based on depth for realistic perspective
                float depth = max(0.4, pos.z);
                gl_PointSize = clamp(u_PointSize * (1.2 / depth), 4.0, 16.0);
                v_Confidence = a_Position.w;
            }
        """.trimIndent()

        val pointFragCode = """
            precision mediump float;
            uniform vec4 u_Color;
            varying float v_Confidence;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord);
                if (dist > 0.5) {
                    discard;
                }
                // Soft glowing anti-aliased circular particle falloff
                float edgeAlpha = smoothstep(0.5, 0.08, dist);
                float alpha = edgeAlpha * u_Color.a * clamp(v_Confidence, 0.4, 1.0);
                gl_FragColor = vec4(u_Color.rgb, alpha);
            }
        """.trimIndent()

        pointProgram = createProgram(pointVertCode, pointFragCode)
        pointPosAttrib = GLES20.glGetAttribLocation(pointProgram, "a_Position")
        pointModelViewProjUniform = GLES20.glGetUniformLocation(pointProgram, "u_ModelViewProjection")
        pointColorUniform = GLES20.glGetUniformLocation(pointProgram, "u_Color")
        pointPointSizeUniform = GLES20.glGetUniformLocation(pointProgram, "u_PointSize")

        // 3. Plane Shaders
        val planeVertCode = """
            uniform mat4 u_Mvp;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_Mvp * a_Position;
            }
        """.trimIndent()

        val planeFragCode = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """.trimIndent()

        planeProgram = createProgram(planeVertCode, planeFragCode)
        planePosAttrib = GLES20.glGetAttribLocation(planeProgram, "a_Position")
        planeMvpUniform = GLES20.glGetUniformLocation(planeProgram, "u_Mvp")
        planeColorUniform = GLES20.glGetUniformLocation(planeProgram, "u_Color")
    }

    private fun createProgram(vertCode: String, fragCode: String): Int {
        val vertShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).apply {
            GLES20.glShaderSource(this, vertCode)
            GLES20.glCompileShader(this)
        }
        val fragShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).apply {
            GLES20.glShaderSource(this, fragCode)
            GLES20.glCompileShader(this)
        }
        return GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertShader)
            GLES20.glAttachShader(this, fragShader)
            GLES20.glLinkProgram(this)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val activity = context as? android.app.Activity
        activity?.let {
            val display = it.windowManager.defaultDisplay
            modernArEngine.setDisplayGeometry(display.rotation, width, height)
            viewModel.updateDisplayGeometry(display.rotation, width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (textureId == -1 || bgProgram == -1) return

        try {
            val frame = session.update()
            val camera = frame.camera

            // Correct UV coords
            frame.transformDisplayUvCoords(quadTexCoordBuffer, transformedTexCoordBuffer)

            // Render camera background
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthMask(false)

            GLES20.glUseProgram(bgProgram)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            GLES20.glEnableVertexAttribArray(bgPositionAttrib)
            GLES20.glVertexAttribPointer(bgPositionAttrib, 3, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

            GLES20.glEnableVertexAttribArray(bgTexCoordAttrib)
            GLES20.glVertexAttribPointer(bgTexCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, transformedTexCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(bgPositionAttrib)
            GLES20.glDisableVertexAttribArray(bgTexCoordAttrib)

            // Process hit-tests on GL frame
            val centerHit = viewModel.processGlFrame(frame, modernArEngine)

            // View & Projection matrices for 3D elements (reusing cached FloatArrays)
            camera.getViewMatrix(viewMat, 0)
            camera.getProjectionMatrix(projMat, 0, 0.1f, 100.0f)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projMat, 0, viewMat, 0)

            // Render Point Cloud (Feature points / 掃描點雲)
            if (viewModel.showPointCloud.value) {
                var pointCloud: com.google.ar.core.PointCloud? = null
                try {
                    pointCloud = frame.acquirePointCloud()
                    val pointsBuf = pointCloud.points
                    if (pointsBuf != null && pointsBuf.remaining() > 0) {
                        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                        GLES20.glDepthMask(false)
                        GLES20.glUseProgram(pointProgram)

                        GLES20.glUniformMatrix4fv(pointModelViewProjUniform, 1, false, modelViewProjectionMatrix, 0)
                        // Dynamic Laser Cyan glowing feature particles (#00E5FF / #38BDF8)
                        GLES20.glUniform4f(pointColorUniform, 0.0f, 0.898f, 1.0f, 0.85f)
                        GLES20.glUniform1f(pointPointSizeUniform, 9.0f)

                        GLES20.glEnableVertexAttribArray(pointPosAttrib)
                        GLES20.glVertexAttribPointer(pointPosAttrib, 4, GLES20.GL_FLOAT, false, 16, pointsBuf)

                        val numPoints = pointsBuf.remaining() / 4
                        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints)

                        GLES20.glDisableVertexAttribArray(pointPosAttrib)
                    }
                } catch (e: Exception) {
                    // Ignore transient point cloud read issues
                } finally {
                    try {
                        pointCloud?.release()
                    } catch (e: Exception) {}
                }
            }

            // Extract Frame Data for UI without duplicate hit-testing
            val frameData = modernArEngine.extractFrameData(frame, centerHit)

            // Throttled non-blocking post to UI main thread (prevents message pileup stutter)
            if (isFrameUpdatePending.compareAndSet(false, true)) {
                post {
                    isFrameUpdatePending.set(false)
                    viewModel.onArFrameUpdated(frameData)
                }
            }

        } catch (e: com.google.ar.core.exceptions.SessionPausedException) {
            // Expected when activity/view pauses
            return
        } catch (e: com.google.ar.core.exceptions.CameraNotAvailableException) {
            // Camera currently busy or switching
            return
        } catch (e: Exception) {
            return
        }
    }
}
