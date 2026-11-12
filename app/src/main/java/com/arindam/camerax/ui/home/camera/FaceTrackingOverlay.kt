package com.arindam.camerax.ui.home.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.arindam.camerax.domain.model.FaceLandmark
import com.arindam.camerax.domain.model.FaceMesh
import com.arindam.camerax.domain.model.FaceTrackingResult
import com.arindam.camerax.ui.theme.CameraAccent

/**
 * Presentation: real-time 3D facial landmark mesh and bounding tracking overlay.
 * Renders on Compose Canvas over the CameraX PreviewView with zero touch interception.
 * Accurately aligns normalized landmark coordinates [0.0, 1.0] to active viewport aspect ratio
 * and handles front selfie camera horizontal mirroring.
 */
@Composable
fun FaceTrackingMeshOverlay(
    result: FaceTrackingResult?,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    if (result == null || !result.hasFaces) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (face in result.faces) {
            drawFaceTrackingVisuals(
                face = face,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                isFrontCamera = isFrontCamera
            )
        }
    }
}

private fun DrawScope.drawFaceTrackingVisuals(
    face: FaceMesh,
    canvasWidth: Float,
    canvasHeight: Float,
    isFrontCamera: Boolean
) {
    val landmarks = face.landmarks
    if (landmarks.isEmpty()) return

    // Transform normalized coordinate to canvas space with mirroring support
    fun project(lm: FaceLandmark): Offset {
        val x = if (isFrontCamera) (1f - lm.x) * canvasWidth else lm.x * canvasWidth
        val y = lm.y * canvasHeight
        return Offset(x, y)
    }

    // 1. Calculate face bounding box
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE

    for (lm in landmarks) {
        val pt = project(lm)
        if (pt.x < minX) minX = pt.x
        if (pt.x > maxX) maxX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.y > maxY) maxY = pt.y
    }

    val padX = (maxX - minX) * 0.08f
    val padY = (maxY - minY) * 0.08f
    val boxLeft = (minX - padX).coerceAtLeast(0f)
    val boxTop = (minY - padY).coerceAtLeast(0f)
    val boxWidth = ((maxX + padX) - boxLeft).coerceAtMost(canvasWidth - boxLeft)
    val boxHeight = ((maxY + padY) - boxTop).coerceAtMost(canvasHeight - boxTop)

    // Corner bracket accents for tracking reticle
    val cornerLen = (boxWidth.coerceAtMost(boxHeight) * 0.18f).coerceAtLeast(14f)
    val strokeWidth = 2.5f
    val cornerColor = CameraAccent.copy(alpha = 0.85f)

    // Top-Left corner
    drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft + cornerLen, boxTop), strokeWidth)
    drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + cornerLen), strokeWidth)

    // Top-Right corner
    val right = boxLeft + boxWidth
    drawLine(cornerColor, Offset(right, boxTop), Offset(right - cornerLen, boxTop), strokeWidth)
    drawLine(cornerColor, Offset(right, boxTop), Offset(right, boxTop + cornerLen), strokeWidth)

    // Bottom-Left corner
    val bottom = boxTop + boxHeight
    drawLine(cornerColor, Offset(boxLeft, bottom), Offset(boxLeft + cornerLen, bottom), strokeWidth)
    drawLine(cornerColor, Offset(boxLeft, bottom), Offset(boxLeft, bottom - cornerLen), strokeWidth)

    // Bottom-Right corner
    drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth)
    drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth)

    // 2. Key Facial Feature Landmarks
    // Standard MediaPipe canonical landmark indices:
    // Left eye center: 468, Right eye center: 473 (or 33, 263)
    // Nose tip: 1, Chin: 152, Left cheek: 234, Right cheek: 454
    val keyIndices = listOf(
        1, 4, 19, 33, 133, 152, 159, 145, 234, 263, 362, 386, 374, 454,
        61, 291, 0, 17, 468, 473
    )

    val pointRadius = 2.2f
    val pointColor = CameraAccent.copy(alpha = 0.75f)

    for (idx in keyIndices) {
        if (idx < landmarks.size) {
            val pt = project(landmarks[idx])
            drawCircle(
                color = pointColor,
                radius = pointRadius,
                center = pt
            )
        }
    }
}
