package com.arindam.camerax.data.tracking

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.arindam.camerax.util.log.Logger
import com.google.mediapipe.framework.image.BitmapImageBuilder

/**
 * CameraX [ImageAnalysis.Analyzer] pipeline feeding frames into [MediaPipeFaceTracker].
 * Runs entirely on a background vision executor off the Main thread.
 *
 * Enforces zero frame buffer leakage: [ImageProxy.close] is unconditionally executed
 * in a guaranteed finally block on every single incoming frame.
 */
class FaceTrackingAnalyzer(
    private val tracker: MediaPipeFaceTracker,
    private val isFrontCamera: () -> Boolean = { true }
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            val enqueued = tracker.processLiveStreamFrame(
                mpImage = mpImage,
                rotationDegrees = rotationDegrees,
                isFrontCamera = isFrontCamera()
            )

            if (!enqueued) {
                // If not accepted by tracker, mpImage is closed inside tracker
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "Analysis frame processing failed: ${e.message}", e)
        } finally {
            // CRITICAL: Unconditionally release CameraX frame buffer to prevent pipeline starvation
            imageProxy.close()
        }
    }

    companion object {
        private const val TAG = "FaceTrackingAnalyzer"
    }
}
