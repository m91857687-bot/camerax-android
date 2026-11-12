package com.arindam.camerax.data.filter

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.arindam.camerax.domain.model.EffectMode
import com.arindam.camerax.util.log.Logger
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX SurfaceProcessor implementation backed by OpenGL ES.
 * Receives the camera frame stream, renders through [GlPassthroughRenderer], and directs output
 * to both Preview and VideoCapture surfaces with zero CPU pixel readback.
 */
class OpenGlSurfaceProcessor : SurfaceProcessor, SurfaceTexture.OnFrameAvailableListener {

    private val glThread = HandlerThread("OpenGlSurfaceProcessorThread").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val glExecutor = Executor { command -> glHandler.post(command) }

    private var eglCore: EglCore? = null
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: GlPassthroughRenderer? = null
    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private data class OutputTarget(
        val surfaceOutput: SurfaceOutput,
        var eglSurface: EGLSurface,
        val width: Int,
        val height: Int
    )

    private val outputTargets = mutableListOf<OutputTarget>()
    private val texMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)
    private val isReleased = AtomicBoolean(false)

    @Volatile
    private var filterEnabled: Boolean = false

    @Volatile
    private var activeFilterType: Int = 0

    @Volatile
    private var activeIntensity: Float = 1.0f

    @Volatile
    private var faceTrackingBridge: com.arindam.camerax.data.tracking.FaceTrackingBridge? = null

    /**
     * Attaches the thread-safe bridge to poll real-time face tracking results.
     */
    fun setFaceTrackingBridge(bridge: com.arindam.camerax.data.tracking.FaceTrackingBridge?) {
        this.faceTrackingBridge = bridge
    }

    /**
     * Returns the latest valid face tracking result, or null if tracking is disabled or stale.
     */
    fun getLatestFaceTrackingResult(): com.arindam.camerax.domain.model.FaceTrackingResult? {
        return faceTrackingBridge?.getLatestResultForRendering()
    }

    /**
     * Updates the active cinematic filter, intensity, and enable state for Preview and VideoCapture.
     * Takes effect on the next frame immediately with zero pipeline rebind.
     */
    fun setFilter(type: EffectMode, intensity: Float = 1.0f, enabled: Boolean = (type != EffectMode.NONE && intensity > 0f)) {
        filterEnabled = enabled && (type != EffectMode.NONE) && (intensity > 0f)
        activeFilterType = if (filterEnabled) type.filterId else 0
        activeIntensity = if (filterEnabled) intensity.coerceIn(0.0f, 1.0f) else 0.0f
        glHandler.post {
            renderer?.setFilter(activeFilterType, activeIntensity)
        }
    }

    init {
        glHandler.post {
            try {
                val core = EglCore()
                eglCore = core
                pbufferSurface = core.createOffscreenSurface(1, 1)
                core.makeCurrent(pbufferSurface)

                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                textureId = textures[0]
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                renderer = GlPassthroughRenderer().apply { init() }
                Logger.debug(TAG, "EGL and GLPassthroughRenderer successfully initialized")
            } catch (e: Throwable) {
                Logger.error(TAG, "Initialization failed: ${e.message}", e)
            }
        }
    }

    override fun onInputSurface(surfaceRequest: SurfaceRequest) {
        glHandler.post {
            if (isReleased.get()) {
                surfaceRequest.willNotProvideSurface()
                return@post
            }
            try {
                val core = eglCore ?: return@post
                core.makeCurrent(pbufferSurface)

                surfaceTexture?.release()
                inputSurface?.release()

                val st = SurfaceTexture(textureId)
                st.setDefaultBufferSize(surfaceRequest.resolution.width, surfaceRequest.resolution.height)
                st.setOnFrameAvailableListener(this, glHandler)
                surfaceTexture = st

                val surface = Surface(st)
                inputSurface = surface

                surfaceRequest.provideSurface(surface, glExecutor) { result ->
                    Logger.debug(TAG, "Input surface result: ${result.resultCode}")
                    glHandler.post {
                        st.release()
                        surface.release()
                        if (surfaceTexture === st) surfaceTexture = null
                        if (inputSurface === surface) inputSurface = null
                    }
                }
            } catch (e: Throwable) {
                Logger.error(TAG, "onInputSurface failed: ${e.message}", e)
                surfaceRequest.willNotProvideSurface()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        glHandler.post {
            if (isReleased.get()) {
                surfaceOutput.close()
                return@post
            }
            try {
                val core = eglCore ?: return@post
                val width = surfaceOutput.size.width
                val height = surfaceOutput.size.height

                val surface = surfaceOutput.getSurface(glExecutor) {
                    // Cleanup when output surface is closed
                    glHandler.post {
                        val iterator = outputTargets.iterator()
                        while (iterator.hasNext()) {
                            val target = iterator.next()
                            if (target.surfaceOutput === surfaceOutput) {
                                core.destroySurface(target.eglSurface)
                                iterator.remove()
                                Logger.debug(TAG, "Destroyed output surface: targets=${surfaceOutput.targets}")
                            }
                        }
                    }
                }

                core.makeCurrent(pbufferSurface)
                val eglSurface = core.createWindowSurface(surface)
                outputTargets.add(OutputTarget(surfaceOutput, eglSurface, width, height))
                Logger.debug(TAG, "Registered output surface: targets=${surfaceOutput.targets}, size=${width}x${height}")
            } catch (e: Throwable) {
                Logger.error(TAG, "onOutputSurface failed: ${e.message}", e)
                surfaceOutput.close()
            }
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (isReleased.get()) return
        val core = eglCore ?: return
        val rend = renderer ?: return
        val st = this.surfaceTexture ?: return

        try {
            core.makeCurrent(pbufferSurface)
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            val timestamp = st.timestamp

            for (target in outputTargets) {
                core.makeCurrent(target.eglSurface)
                GLES20.glViewport(0, 0, target.width, target.height)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                // Multiply SurfaceTexture transform matrix with SurfaceOutput matrix
                target.surfaceOutput.updateTransformMatrix(finalMatrix, texMatrix)

                rend.setFilter(activeFilterType, activeIntensity)
                rend.draw(textureId, finalMatrix)

                core.setPresentationTime(target.eglSurface, timestamp)
                core.swapBuffers(target.eglSurface)
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "Render frame error: ${e.message}", e)
        }
    }

    fun toCameraEffect(): CameraEffect {
        return object : CameraEffect(
            PREVIEW or VIDEO_CAPTURE,
            glExecutor,
            this@OpenGlSurfaceProcessor,
            Consumer<Throwable> { error ->
                Logger.error(TAG, "CameraEffect error: ${error.message}", error)
            }
        ) {}
    }

    fun release() {
        if (isReleased.compareAndSet(false, true)) {
            glHandler.post {
                val core = eglCore
                if (core != null) {
                    for (target in outputTargets) {
                        core.destroySurface(target.eglSurface)
                    }
                    outputTargets.clear()

                    renderer?.release()
                    renderer = null

                    surfaceTexture?.release()
                    surfaceTexture = null
                    inputSurface?.release()
                    inputSurface = null

                    if (textureId != 0) {
                        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                        textureId = 0
                    }

                    core.destroySurface(pbufferSurface)
                    core.release()
                    eglCore = null
                }
                glThread.quitSafely()
                Logger.debug(TAG, "OpenGlSurfaceProcessor released")
            }
        }
    }

    companion object {
        private const val TAG = "OpenGlSurfaceProcessor"
    }
}
