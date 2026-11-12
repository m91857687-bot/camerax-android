package com.arindam.camerax.domain.model

/**
 * Domain models for real-time facial landmark detection, head pose, blendshapes,
 * and tracking state machine. Decoupled from CameraX and MediaPipe dependencies.
 */

/**
 * 3D point representing a single facial landmark.
 * @param x Normalized X coordinate in range [0.0, 1.0].
 * @param y Normalized Y coordinate in range [0.0, 1.0].
 * @param z Relative depth coordinate (smaller values closer to camera).
 */
data class FaceLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * ARKit-compatible facial blendshape parameter (52 standard expression categories).
 * @param category Blendshape name (e.g., "eyeBlinkLeft", "mouthSmileRight", "jawOpen").
 * @param score Activation score in range [0.0, 1.0].
 */
data class FacialBlendshape(
    val category: String,
    val score: Float
)

/**
 * Full mesh and pose information for a single detected face.
 * @param faceIndex Zero-based face index within the current frame.
 * @param landmarks 478 normalized 3D landmarks (MediaPipe topology).
 * @param blendshapes Map of 52 expression blendshape scores.
 * @param facialTransformationMatrix 4x4 column-major transformation matrix for 3D pose, if available.
 */
data class FaceMesh(
    val faceIndex: Int,
    val landmarks: List<FaceLandmark>,
    val blendshapes: Map<String, Float> = emptyMap(),
    val facialTransformationMatrix: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceMesh) return false
        if (faceIndex != other.faceIndex) return false
        if (landmarks != other.landmarks) return false
        if (blendshapes != other.blendshapes) return false
        if (facialTransformationMatrix != null) {
            if (other.facialTransformationMatrix == null) return false
            if (!facialTransformationMatrix.contentEquals(other.facialTransformationMatrix)) return false
        } else if (other.facialTransformationMatrix != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = faceIndex
        result = 31 * result + landmarks.hashCode()
        result = 31 * result + blendshapes.hashCode()
        result = 31 * result + (facialTransformationMatrix?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * State machine for real-time face tracking.
 */
enum class FaceTrackingState {
    /** No face is currently detected in the viewfinder frame. */
    NO_FACE,
    /** A face has entered the frame and initial detection succeeded. */
    FACE_DETECTED,
    /** Face tracking is actively engaged and receiving continuous updates. */
    TRACKING,
    /** Face was recently tracked but temporarily lost (occlusion or out-of-frame). */
    TRACKING_LOST,
    /** Unrecoverable error in the vision tracking pipeline (e.g., missing model or hardware failure). */
    TRACKING_ERROR
}

/**
 * Comprehensive result of a single processed frame's face tracking analysis.
 * @param faces List of detected face meshes in the frame.
 * @param timestampMs Monotonic timestamp of the analyzed frame in milliseconds.
 * @param processingTimeMs Time spent executing the vision model in milliseconds.
 * @param frameRotation Sensor rotation degrees of the analyzed frame (0, 90, 180, 270).
 * @param isFrontCamera True if the analyzed frame originated from the front-facing selfie camera.
 */
data class FaceTrackingResult(
    val faces: List<FaceMesh>,
    val timestampMs: Long,
    val processingTimeMs: Long,
    val frameRotation: Int = 0,
    val isFrontCamera: Boolean = true
) {
    val hasFaces: Boolean get() = faces.isNotEmpty()
    val primaryFace: FaceMesh? get() = faces.firstOrNull()
}

/**
 * Live performance telemetry and diagnostics for face tracking.
 * @param fps Measured detection rate in frames per second.
 * @param latencyMs End-to-end detection latency in milliseconds.
 * @param droppedFrames Number of skipped/dropped frames due to backpressure.
 * @param trackingState Current state machine status.
 */
data class FaceTrackingMetrics(
    val fps: Float = 0f,
    val latencyMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val trackingState: FaceTrackingState = FaceTrackingState.NO_FACE
)
