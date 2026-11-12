package com.arindam.camerax.data.tracking

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.arindam.camerax.domain.model.FaceLandmark
import com.arindam.camerax.domain.model.FaceMesh
import com.arindam.camerax.domain.model.FaceTrackingResult
import com.arindam.camerax.domain.model.FaceTrackingState
import com.arindam.camerax.util.log.Logger
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Data: MediaPipe Tasks Vision implementation of real-time 3D facial landmark detection,
 * expression blendshape extraction, and 4x4 facial transformation matrix calculation.
 * Operates in non-blocking LIVE_STREAM mode on a dedicated background thread.
 */
class MediaPipeFaceTracker(
    private val context: Context,
    private val bridge: FaceTrackingBridge,
    private var maxFaces: Int = 1,
    private var minConfidence: Float = 0.5f,
    private val onResult: ((FaceTrackingResult) -> Unit)? = null,
    private val onError: ((Throwable) -> Unit)? = null
) {
    private var landmarker: FaceLandmarker? = null
    private val isInitialized = AtomicBoolean(false)
    private val isProcessingFrame = AtomicBoolean(false)
    private val smoother = FaceLandmarkSmoother(maxFaces = 2)

    private val lastFrameTimestampMs = AtomicLong(0L)
    private var faceFirstDetectedUptime = 0L
    private var faceLostUptime = 0L
    private val internalState = AtomicReference(FaceTrackingState.NO_FACE)

    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized.get() && landmarker != null) return true

        // API 24+ gating
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Logger.warning(TAG, "MediaPipe requires Android 7.0+ (API 24). Current: ${Build.VERSION.SDK_INT}")
            internalState.set(FaceTrackingState.TRACKING_ERROR)
            return false
        }

        try {
            // Verify model asset exists
            val assetExists = runCatching {
                context.assets.open(MODEL_ASSET_PATH).use { true }
            }.getOrDefault(false)

            if (!assetExists) {
                Logger.error(TAG, "MediaPipe model asset '$MODEL_ASSET_PATH' not found in assets/")
                internalState.set(FaceTrackingState.TRACKING_ERROR)
                return false
            }

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(maxFaces.coerceIn(1, 2))
                .setMinFaceDetectionConfidence(minConfidence)
                .setMinFacePresenceConfidence(minConfidence)
                .setMinTrackingConfidence(minConfidence)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener { result: FaceLandmarkerResult, input: MPImage ->
                    handleDetectionResult(result, input)
                }
                .setErrorListener { error: RuntimeException ->
                    handleDetectionError(error)
                }
                .build()

            landmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized.set(true)
            internalState.set(FaceTrackingState.NO_FACE)
            Logger.info(TAG, "MediaPipe FaceLandmarker successfully initialized (maxFaces=$maxFaces)")
            return true
        } catch (e: Throwable) {
            Logger.error(TAG, "FaceLandmarker initialization failed: ${e.message}", e)
            internalState.set(FaceTrackingState.TRACKING_ERROR)
            onError?.invoke(e)
            return false
        }
    }

    /**
     * Dispatches an [MPImage] frame to MediaPipe for asynchronous inference in LIVE_STREAM mode.
     * Enforces strictly monotonically increasing timestamps and prevents frame pile-up.
     */
    fun processLiveStreamFrame(
        mpImage: MPImage,
        rotationDegrees: Int = 0,
        isFrontCamera: Boolean = true
    ): Boolean {
        if (!isInitialized.get() || landmarker == null) {
            mpImage.close()
            return false
        }

        // Throttle if vision inference is still processing previous frame
        if (!isProcessingFrame.compareAndSet(false, true)) {
            bridge.recordDroppedFrame()
            mpImage.close()
            return false
        }

        try {
            val nowMs = SystemClock.uptimeMillis()
            val lastTs = lastFrameTimestampMs.get()
            val frameTs = if (nowMs > lastTs) nowMs else lastTs + 1L
            lastFrameTimestampMs.set(frameTs)

            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            landmarker?.detectAsync(mpImage, processingOptions, frameTs)
            return true
        } catch (e: Throwable) {
            isProcessingFrame.set(false)
            mpImage.close()
            Logger.error(TAG, "detectAsync error: ${e.message}", e)
            return false
        }
    }

    private fun handleDetectionResult(result: FaceLandmarkerResult, input: MPImage) {
        val now = SystemClock.uptimeMillis()
        try {
            val rawLandmarksList = result.faceLandmarks()
            val blendshapesList = if (result.faceBlendshapes().isPresent) {
                result.faceBlendshapes().get()
            } else emptyList()

            val matricesList = if (result.facialTransformationMatrixes().isPresent) {
                result.facialTransformationMatrixes().get()
            } else emptyList()

            val faceCount = rawLandmarksList.size

            // Update state machine
            val currentState = internalState.get()
            val newState = when {
                faceCount > 0 -> {
                    faceLostUptime = 0L
                    if (currentState == FaceTrackingState.NO_FACE || currentState == FaceTrackingState.TRACKING_LOST) {
                        faceFirstDetectedUptime = now
                        FaceTrackingState.FACE_DETECTED
                    } else {
                        FaceTrackingState.TRACKING
                    }
                }
                else -> {
                    smoother.reset()
                    if (currentState == FaceTrackingState.TRACKING || currentState == FaceTrackingState.FACE_DETECTED) {
                        if (faceLostUptime == 0L) faceLostUptime = now
                        if (now - faceLostUptime < TRACKING_LOST_GRACE_PERIOD_MS) {
                            FaceTrackingState.TRACKING_LOST
                        } else {
                            FaceTrackingState.NO_FACE
                        }
                    } else {
                        FaceTrackingState.NO_FACE
                    }
                }
            }
            internalState.set(newState)

            // Convert to domain FaceMesh list with smoothing
            val meshes = mutableListOf<FaceMesh>()
            for (i in 0 until faceCount) {
                val rawLandmarks = rawLandmarksList[i].map { norm ->
                    FaceLandmark(norm.x(), norm.y(), norm.z())
                }
                val smoothedLandmarks = smoother.smooth(i, rawLandmarks, now)

                val blendshapesMap = if (i < blendshapesList.size) {
                    blendshapesList[i].associate { category ->
                        category.categoryName() to category.score()
                    }
                } else emptyMap()

                val matrix = if (i < matricesList.size) matricesList[i] else null

                meshes.add(
                    FaceMesh(
                        faceIndex = i,
                        landmarks = smoothedLandmarks,
                        blendshapes = blendshapesMap,
                        facialTransformationMatrix = matrix
                    )
                )
            }

            val trackingResult = FaceTrackingResult(
                faces = meshes,
                timestampMs = now,
                processingTimeMs = SystemClock.uptimeMillis() - now
            )

            bridge.postResult(trackingResult, newState)
            onResult?.invoke(trackingResult)
        } catch (e: Throwable) {
            Logger.error(TAG, "Error handling detection result: ${e.message}", e)
        } finally {
            isProcessingFrame.set(false)
            input.close()
        }
    }

    private fun handleDetectionError(error: RuntimeException) {
        isProcessingFrame.set(false)
        internalState.set(FaceTrackingState.TRACKING_ERROR)
        Logger.error(TAG, "MediaPipe inference error: ${error.message}", error)
        onError?.invoke(error)
    }

    @Synchronized
    fun release() {
        isInitialized.set(false)
        isProcessingFrame.set(false)
        try {
            landmarker?.close()
            landmarker = null
        } catch (e: Throwable) {
            Logger.warning(TAG, "Error closing FaceLandmarker: ${e.message}")
        }
        smoother.reset()
        bridge.reset()
        internalState.set(FaceTrackingState.NO_FACE)
        Logger.info(TAG, "MediaPipeFaceTracker released")
    }

    companion object {
        private const val TAG = "MediaPipeFaceTracker"
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val TRACKING_LOST_GRACE_PERIOD_MS = 300L
    }
}
