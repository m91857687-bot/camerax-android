package com.arindam.camerax.data.tracking

import android.os.SystemClock
import com.arindam.camerax.domain.model.FaceTrackingMetrics
import com.arindam.camerax.domain.model.FaceTrackingResult
import com.arindam.camerax.domain.model.FaceTrackingState
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, non-blocking bridge connecting the MediaPipe vision analysis pipeline
 * with the OpenGL ES render thread and UI presentation layer.
 *
 * Guarantees zero locking on GLThread: uses [AtomicReference] so the render loop
 * queries the newest valid face mesh in sub-microsecond time without mutex contention.
 */
class FaceTrackingBridge(
    private val staleThresholdMs: Long = 150L
) {
    private val latestResult = AtomicReference<FaceTrackingResult?>(null)
    private val currentState = AtomicReference(FaceTrackingState.NO_FACE)
    private val lastUpdateUptimeMs = AtomicLong(0L)
    private val droppedFramesCounter = AtomicLong(0L)
    private val totalProcessedFrames = AtomicLong(0L)

    // Performance telemetry
    private var lastFpsCalculationUptime = SystemClock.uptimeMillis()
    private var framesSinceLastFpsCalc = 0
    private var currentFps = 0f
    private var lastLatencyMs = 0L

    /**
     * Called by [MediaPipeFaceTracker] on the vision analysis thread when a new inference finishes.
     */
    fun postResult(result: FaceTrackingResult, state: FaceTrackingState) {
        val now = SystemClock.uptimeMillis()
        latestResult.set(result)
        currentState.set(state)
        lastUpdateUptimeMs.set(now)
        totalProcessedFrames.incrementAndGet()
        lastLatencyMs = result.processingTimeMs

        // Update live detection FPS calculation
        framesSinceLastFpsCalc++
        val elapsed = now - lastFpsCalculationUptime
        if (elapsed >= 1000L) {
            currentFps = (framesSinceLastFpsCalc * 1000f) / elapsed
            framesSinceLastFpsCalc = 0
            lastFpsCalculationUptime = now
        }
    }

    /**
     * Non-blocking query called by [com.arindam.camerax.data.filter.OpenGlSurfaceProcessor]
     * on the OpenGL ES render thread on every camera frame.
     *
     * @return The latest valid [FaceTrackingResult], or null if no face is tracked
     * or the result has exceeded the [staleThresholdMs] threshold.
     */
    fun getLatestResultForRendering(): FaceTrackingResult? {
        val result = latestResult.get() ?: return null
        val age = SystemClock.uptimeMillis() - lastUpdateUptimeMs.get()
        return if (age <= staleThresholdMs) {
            result
        } else {
            null // Stale result rejected to prevent visual stutter/ghosting
        }
    }

    /**
     * Reports an unanalyzed frame dropped due to backpressure / busy analyzer.
     */
    fun recordDroppedFrame() {
        droppedFramesCounter.incrementAndGet()
    }

    /**
     * Retrieves the current tracking state machine status.
     */
    fun getTrackingState(): FaceTrackingState {
        val state = currentState.get()
        if (state == FaceTrackingState.TRACKING || state == FaceTrackingState.FACE_DETECTED) {
            val age = SystemClock.uptimeMillis() - lastUpdateUptimeMs.get()
            if (age > staleThresholdMs) {
                return FaceTrackingState.TRACKING_LOST
            }
        }
        return state
    }

    /**
     * Direct snapshot of latest raw result for UI consumption.
     */
    fun getLatestResult(): FaceTrackingResult? = latestResult.get()

    /**
     * Real-time metrics snapshot.
     */
    fun getMetrics(): FaceTrackingMetrics {
        return FaceTrackingMetrics(
            fps = currentFps,
            latencyMs = lastLatencyMs,
            droppedFrames = droppedFramesCounter.get(),
            trackingState = getTrackingState()
        )
    }

    /**
     * Clears all tracking data when the camera unbinds or face tracking stops.
     */
    fun reset() {
        latestResult.set(null)
        currentState.set(FaceTrackingState.NO_FACE)
        lastUpdateUptimeMs.set(0L)
        currentFps = 0f
        lastLatencyMs = 0L
    }
}
