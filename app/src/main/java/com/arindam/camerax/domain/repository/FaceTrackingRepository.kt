package com.arindam.camerax.domain.repository

import com.arindam.camerax.domain.model.FaceTrackingMetrics
import com.arindam.camerax.domain.model.FaceTrackingResult
import com.arindam.camerax.domain.model.FaceTrackingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for real-time face tracking and landmark telemetry.
 * UI and use cases observe this interface without direct MediaPipe coupling.
 */
interface FaceTrackingRepository {

    /** Current state machine status (NO_FACE, FACE_DETECTED, TRACKING, etc.). */
    val trackingState: StateFlow<FaceTrackingState>

    /** Latest valid face tracking result (landmarks, blendshapes, pose). */
    val trackingResult: StateFlow<FaceTrackingResult?>

    /** Real-time performance metrics (FPS, latency, dropped frames). */
    val trackingMetrics: StateFlow<FaceTrackingMetrics>

    /** Whether face tracking is currently enabled by user preference. */
    val isEnabled: StateFlow<Boolean>

    /** Enable or disable real-time face tracking. */
    fun setTrackingEnabled(enabled: Boolean)

    /** Configure maximum simultaneous faces to track (e.g. 1 or 2). */
    fun setMaxFaces(maxFaces: Int)

    /** Set minimum confidence threshold in range [0.0, 1.0]. */
    fun setMinConfidence(confidence: Float)

    /** Release native model and tracking resources. */
    fun release()
}

/**
 * Default no-op implementation of [FaceTrackingRepository] used for tests and fallback.
 */
class NoOpFaceTrackingRepository : FaceTrackingRepository {
    override val trackingState = kotlinx.coroutines.flow.MutableStateFlow(FaceTrackingState.NO_FACE)
    override val trackingResult = kotlinx.coroutines.flow.MutableStateFlow<FaceTrackingResult?>(null)
    override val trackingMetrics = kotlinx.coroutines.flow.MutableStateFlow(FaceTrackingMetrics())
    override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun setTrackingEnabled(enabled: Boolean) { isEnabled.value = enabled }
    override fun setMaxFaces(maxFaces: Int) {}
    override fun setMinConfidence(confidence: Float) {}
    override fun release() {}
}
