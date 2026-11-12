package com.arindam.camerax.data.tracking

import android.content.Context
import com.arindam.camerax.domain.model.FaceTrackingMetrics
import com.arindam.camerax.domain.model.FaceTrackingResult
import com.arindam.camerax.domain.model.FaceTrackingState
import com.arindam.camerax.domain.repository.FaceTrackingRepository
import com.arindam.camerax.util.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Concrete implementation of [FaceTrackingRepository].
 * Coordinates background MediaPipe vision tracking, state machines, and metrics telemetry.
 */
class FaceTrackingEngine(
    private val context: Context,
    val bridge: FaceTrackingBridge = FaceTrackingBridge()
) : FaceTrackingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _trackingState = MutableStateFlow(FaceTrackingState.NO_FACE)
    override val trackingState: StateFlow<FaceTrackingState> = _trackingState.asStateFlow()

    private val _trackingResult = MutableStateFlow<FaceTrackingResult?>(null)
    override val trackingResult: StateFlow<FaceTrackingResult?> = _trackingResult.asStateFlow()

    private val _trackingMetrics = MutableStateFlow(FaceTrackingMetrics())
    override val trackingMetrics: StateFlow<FaceTrackingMetrics> = _trackingMetrics.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var maxFaces: Int = 1
    private var minConfidence: Float = 0.5f

    private var tracker: MediaPipeFaceTracker? = null
    private val isStarted = AtomicBoolean(false)

    init {
        // Polling loop for state and metrics telemetry (every 100ms)
        scope.launch {
            while (isActive) {
                if (_isEnabled.value) {
                    val state = bridge.getTrackingState()
                    val result = bridge.getLatestResult()
                    val metrics = bridge.getMetrics()

                    _trackingState.value = state
                    _trackingResult.value = result
                    _trackingMetrics.value = metrics
                }
                delay(100L)
            }
        }
    }

    /**
     * Initializes the underlying MediaPipe tracker if not already active.
     */
    fun start(): Boolean {
        if (isStarted.get() && tracker != null) return true
        val tr = MediaPipeFaceTracker(
            context = context,
            bridge = bridge,
            maxFaces = maxFaces,
            minConfidence = minConfidence,
            onResult = { result ->
                _trackingResult.value = result
            },
            onError = { error ->
                _trackingState.value = FaceTrackingState.TRACKING_ERROR
            }
        )
        val success = tr.initialize()
        if (success) {
            tracker = tr
            isStarted.set(true)
            Logger.info(TAG, "FaceTrackingEngine started successfully")
        } else {
            _trackingState.value = FaceTrackingState.TRACKING_ERROR
            Logger.warning(TAG, "Failed to initialize MediaPipe in FaceTrackingEngine")
        }
        return success
    }

    /**
     * Creates an [androidx.camera.core.ImageAnalysis.Analyzer] hooked to this tracking engine.
     */
    fun createAnalyzer(isFrontCamera: () -> Boolean = { true }): FaceTrackingAnalyzer? {
        val tr = tracker ?: run {
            if (start()) tracker else null
        } ?: return null

        return FaceTrackingAnalyzer(tr, isFrontCamera)
    }

    override fun setTrackingEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) {
            start()
        } else {
            releaseTracker()
            bridge.reset()
            _trackingResult.value = null
            _trackingState.value = FaceTrackingState.NO_FACE
        }
    }

    override fun setMaxFaces(maxFaces: Int) {
        this.maxFaces = maxFaces.coerceIn(1, 2)
        if (isStarted.get()) {
            // Re-initialize tracker with updated face count
            releaseTracker()
            start()
        }
    }

    override fun setMinConfidence(confidence: Float) {
        this.minConfidence = confidence.coerceIn(0.1f, 0.9f)
    }

    private fun releaseTracker() {
        tracker?.release()
        tracker = null
        isStarted.set(false)
    }

    override fun release() {
        releaseTracker()
        bridge.reset()
        _trackingResult.value = null
        _trackingState.value = FaceTrackingState.NO_FACE
        scope.cancel()
        Logger.info(TAG, "FaceTrackingEngine released")
    }

    companion object {
        private const val TAG = "FaceTrackingEngine"
    }
}
