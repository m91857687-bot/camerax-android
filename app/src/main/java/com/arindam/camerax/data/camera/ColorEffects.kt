package com.arindam.camerax.data.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import com.arindam.camerax.domain.model.EffectMode

/**
 * Data: ColorMatrix set for cinematic and legacy effects.
 * Used by Effects-mode [ImageAnalysis] and still capture inside [CameraSession].
 *
 * [ImageProxy.toBitmap] often returns a HARDWARE bitmap. [ColorMatrixColorFilter] is skipped on
 * those buffers. Copy to software ARGB first, then draw so the matrices actually apply.
 */
object ColorEffects {

    fun androidMatrix(type: EffectMode): ColorMatrix? = when (type) {
        EffectMode.NONE -> null
        EffectMode.NATURAL -> ColorMatrix().apply { setSaturation(1.15f) }
        EffectMode.CINEMATIC -> ColorMatrix(
            floatArrayOf(
                1.12f, 0.00f, 0.00f, 0f, 15f,
                0.00f, 1.05f, 0.00f, 0f, 5f,
                0.00f, 0.00f, 0.88f, 0f, -10f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            )
        )
        EffectMode.VINTAGE -> ColorMatrix(
            floatArrayOf(
                0.85f, 0.05f, 0.00f, 0f, 25f,
                0.00f, 0.82f, 0.00f, 0f, 20f,
                0.00f, 0.05f, 0.70f, 0f, 10f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            )
        )
        EffectMode.WARM -> ColorMatrix().apply { setScale(1.15f, 1.04f, 0.86f, 1f) }
        EffectMode.COLD -> ColorMatrix().apply { setScale(0.88f, 0.98f, 1.18f, 1f) }
        EffectMode.GOLDEN -> ColorMatrix().apply { setScale(1.20f, 1.10f, 0.80f, 1f) }
        EffectMode.NOIR -> ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.35f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        EffectMode.DRAMA -> ColorMatrix().apply {
            setSaturation(1.22f)
            val contrast = 1.25f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        EffectMode.PORTRAIT -> ColorMatrix().apply { setScale(1.08f, 1.03f, 0.96f, 1f) }
        EffectMode.SUNSET -> ColorMatrix(
            floatArrayOf(
                1.20f, 0.00f, 0.00f, 0f, 20f,
                0.00f, 1.02f, 0.00f, 0f, 5f,
                0.05f, 0.00f, 0.90f, 0f, 10f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            )
        )
        EffectMode.SOFT -> ColorMatrix().apply {
            setSaturation(0.92f)
            setScale(0.95f, 0.95f, 0.95f, 1f)
        }
        EffectMode.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
        EffectMode.INVERT -> ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        EffectMode.SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        EffectMode.COOL -> ColorMatrix().apply { setScale(0.85f, 1f, 1.2f, 1f) }
        EffectMode.VIVID -> ColorMatrix().apply { setSaturation(1.7f) }
    }

    fun render(source: Bitmap, type: EffectMode): Bitmap {
        val software = source.toSoftwareArgb()
        val output = Bitmap.createBitmap(software.width, software.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            androidMatrix(type)?.let { colorFilter = ColorMatrixColorFilter(it) }
        }
        Canvas(output).drawBitmap(software, 0f, 0f, paint)
        if (software !== source) software.recycle()
        return output
    }

    /**
     * Draws [source] with [type] color matrix onto a pre-allocated [target] bitmap using
     * [canvas]. Avoids per-frame allocation for live Effects mode. Caller must ensure
     * [target] dimensions match the source after orientation.
     */
    fun renderInto(
        source: Bitmap,
        type: EffectMode,
        target: Bitmap,
        canvas: Canvas,
        paint: Paint
    ) {
        val software = source.toSoftwareArgb()
        paint.colorFilter = androidMatrix(type)?.let { ColorMatrixColorFilter(it) }
        canvas.drawBitmap(software, 0f, 0f, paint)
        if (software !== source) software.recycle()
    }

    /**
     * Applies the given effect and intensity blending to [source].
     * @param intensity 0.0f returns unmodified source, 1.0f returns 100% full effect, intermediate values blend.
     */
    fun applyToBitmap(source: Bitmap, type: EffectMode, intensity: Float = 1.0f): Bitmap {
        if (type == EffectMode.NONE || intensity <= 0f) return source
        val filtered = render(source, type)
        if (intensity >= 1.0f) return filtered

        // Blend filtered with original using intensity
        val software = source.toSoftwareArgb()
        val output = Bitmap.createBitmap(software.width, software.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(software, 0f, 0f, null)
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (intensity.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawBitmap(filtered, 0f, 0f, blendPaint)
        filtered.recycle()
        if (software !== source) software.recycle()
        return output
    }
}

internal fun Bitmap.toSoftwareArgb(): Bitmap {
    if (config == Bitmap.Config.ARGB_8888) return this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
        return copy(Bitmap.Config.ARGB_8888, false) ?: this
    }
    return copy(Bitmap.Config.ARGB_8888, false) ?: this
}
