package com.arindam.camerax.data.tracking

import com.arindam.camerax.domain.model.FaceLandmark
import kotlin.math.PI
import kotlin.math.abs

/**
 * 1€ (One Euro) Filter for low-latency, jitter-free real-time signal filtering.
 * Casiez, G., Roussel, N., & Vogel, D. (2012). 1€ filter: a simple speed-based low-pass filter.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f
) {
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrev = -1L

    fun filter(x: Float, timestampMs: Long): Float {
        if (tPrev < 0L) {
            xPrev = x
            dxPrev = 0f
            tPrev = timestampMs
            return x
        }

        val dt = ((timestampMs - tPrev) / 1000f).coerceAtLeast(1e-4f)
        tPrev = timestampMs

        // Derivative
        val dx = (x - xPrev) / dt
        val edx = lowPassFilter(dx, dxPrev, alpha(dt, dCutoff))
        dxPrev = edx

        // Adaptive cutoff based on speed
        val cutoff = minCutoff + beta * abs(edx)
        val filtered = lowPassFilter(x, xPrev, alpha(dt, cutoff))
        xPrev = filtered

        return filtered
    }

    fun reset() {
        tPrev = -1L
        xPrev = 0f
        dxPrev = 0f
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1.0f / (2f * PI.toFloat() * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }

    private fun lowPassFilter(x: Float, prev: Float, alpha: Float): Float {
        return alpha * x + (1.0f - alpha) * prev
    }
}

/**
 * 3D Landmark One Euro Filter holding independent filters for X, Y, and Z.
 */
class OneEuroFilter3D(
    minCutoff: Float = 1.2f,
    beta: Float = 0.015f,
    dCutoff: Float = 1.0f
) {
    private val xFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val yFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val zFilter = OneEuroFilter(minCutoff, beta, dCutoff)

    fun filter(landmark: FaceLandmark, timestampMs: Long): FaceLandmark {
        return FaceLandmark(
            x = xFilter.filter(landmark.x, timestampMs),
            y = yFilter.filter(landmark.y, timestampMs),
            z = zFilter.filter(landmark.z, timestampMs)
        )
    }

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        zFilter.reset()
    }
}

/**
 * Smoother managing filters for 478 MediaPipe facial landmarks per face.
 */
class FaceLandmarkSmoother(
    private val maxFaces: Int = 2
) {
    private val faceFilters = mutableMapOf<Int, MutableList<OneEuroFilter3D>>()

    fun smooth(faceIndex: Int, landmarks: List<FaceLandmark>, timestampMs: Long): List<FaceLandmark> {
        val filters = faceFilters.getOrPut(faceIndex) {
            MutableList(landmarks.size) { OneEuroFilter3D() }
        }

        // Reallocate filters if landmark count changes
        while (filters.size < landmarks.size) {
            filters.add(OneEuroFilter3D())
        }

        return landmarks.mapIndexed { index, landmark ->
            filters[index].filter(landmark, timestampMs)
        }
    }

    fun reset(faceIndex: Int? = null) {
        if (faceIndex != null) {
            faceFilters[faceIndex]?.forEach { it.reset() }
        } else {
            faceFilters.values.forEach { list -> list.forEach { it.reset() } }
            faceFilters.clear()
        }
    }
}
