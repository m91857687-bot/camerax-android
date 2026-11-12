package com.arindam.camerax.data.filter

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * GPU-accelerated OES camera texture renderer with GLSL cinematic color filters.
 * Applies 11 professional color grading shaders with continuous intensity blending (0.0 to 1.0)
 * directly in the OpenGL ES render pipeline before outputting to EGL preview and video encoder surfaces.
 */
class GlPassthroughRenderer {

    private val vertexShader = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """.trimIndent()

    private val fragmentShader = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        uniform int uFilterType;
        uniform float uIntensity;

        // ITU-R BT.709 standard luminance weights
        float getLuma(vec3 c) {
            return dot(c, vec3(0.2126, 0.7152, 0.0722));
        }

        // Contrast adjustment
        vec3 applyContrast(vec3 color, float contrast) {
            return clamp((color - 0.5) * contrast + 0.5, 0.0, 1.0);
        }

        // Saturation adjustment
        vec3 adjustSaturation(vec3 color, float sat) {
            float luma = getLuma(color);
            return clamp(mix(vec3(luma), color, sat), 0.0, 1.0);
        }

        void main() {
            vec4 baseColor = texture2D(sTexture, vTexCoord);
            vec3 rgb = baseColor.rgb;
            vec3 filtered = rgb;

            if (uFilterType == 1) {
                // NATURAL: subtle vibrant clarity, gentle S-curve (+8%), preserved skin tones
                filtered = adjustSaturation(rgb, 1.15);
                filtered = applyContrast(filtered, 1.08);
            } else if (uFilterType == 2) {
                // CINEMATIC: Hollywood Teal & Orange split toning, rich blacks, filmic contrast
                float luma = getLuma(rgb);
                vec3 tealShadows = vec3(0.0, 0.08, 0.14);
                vec3 amberHighlights = vec3(0.12, 0.06, -0.04);
                vec3 split = mix(rgb + tealShadows * (1.0 - luma), rgb + amberHighlights * luma, luma);
                filtered = applyContrast(adjustSaturation(split, 1.12), 1.18);
            } else if (uFilterType == 3) {
                // VINTAGE: Lifted black toe (matte shadows), warm cream highlights, classic fade
                float luma = getLuma(rgb);
                vec3 faded = adjustSaturation(rgb, 0.82) * 0.88 + vec3(0.08, 0.06, 0.03);
                faded += vec3(0.04, 0.03, -0.02) * luma;
                filtered = clamp(faded, 0.0, 1.0);
            } else if (uFilterType == 4) {
                // WARM: Cozy golden color temperature boost
                filtered = vec3(rgb.r * 1.15 + 0.03, rgb.g * 1.04, rgb.b * 0.86);
                filtered = adjustSaturation(filtered, 1.05);
            } else if (uFilterType == 5) {
                // COLD: Crisp Nordic blue/cyan temperature shift
                filtered = vec3(rgb.r * 0.88, rgb.g * 0.98, rgb.b * 1.18 + 0.04);
                filtered = applyContrast(filtered, 1.06);
            } else if (uFilterType == 6) {
                // GOLDEN: Radiant golden hour warmth and amber illumination
                filtered = vec3(rgb.r * 1.20 + 0.05, rgb.g * 1.10 + 0.02, rgb.b * 0.80);
                filtered = applyContrast(adjustSaturation(filtered, 1.15), 1.05);
            } else if (uFilterType == 7) {
                // NOIR: High-contrast dramatic black & white (deep shadows, bright speculars)
                float luma = getLuma(rgb);
                float noirVal = clamp((luma - 0.45) * 1.35 + 0.45, 0.0, 1.0);
                filtered = vec3(noirVal);
            } else if (uFilterType == 8) {
                // DRAMA: Heavy contrast, punchy saturation, deep moody blacks
                vec3 punchy = adjustSaturation(rgb, 1.22);
                filtered = applyContrast(punchy, 1.28);
            } else if (uFilterType == 9) {
                // PORTRAIT: Flattering warm peach glow, smooth skin midtones
                vec3 skinWarm = vec3(rgb.r * 1.08 + 0.02, rgb.g * 1.03, rgb.b * 0.96);
                filtered = mix(rgb, skinWarm, 0.7);
                filtered = applyContrast(filtered, 1.04);
            } else if (uFilterType == 10) {
                // SUNSET: Dusky violet shadows, fiery orange/golden highlights
                float luma = getLuma(rgb);
                vec3 duskViolet = vec3(0.06, 0.0, 0.08);
                vec3 fireOrange = vec3(0.18, 0.08, -0.04);
                vec3 sunset = rgb + duskViolet * (1.0 - luma) + fireOrange * luma;
                filtered = adjustSaturation(applyContrast(sunset, 1.12), 1.20);
            } else if (uFilterType == 11) {
                // SOFT: Dreamy pastel palette, low contrast, lifted shadows
                filtered = applyContrast(rgb, 0.88) + vec3(0.04);
                filtered = adjustSaturation(filtered, 0.92);
            } else if (uFilterType == 12) {
                // Legacy GRAYSCALE
                filtered = vec3(getLuma(rgb));
            } else if (uFilterType == 13) {
                // Legacy INVERT
                filtered = 1.0 - rgb;
            } else if (uFilterType == 14) {
                // Legacy SEPIA
                filtered = vec3(
                    dot(rgb, vec3(0.393, 0.769, 0.189)),
                    dot(rgb, vec3(0.349, 0.686, 0.168)),
                    dot(rgb, vec3(0.272, 0.534, 0.131))
                );
            } else if (uFilterType == 15) {
                // Legacy COOL
                filtered = rgb * vec3(0.85, 1.0, 1.2);
            } else if (uFilterType == 16) {
                // Legacy VIVID
                filtered = adjustSaturation(rgb, 1.7);
            }

            filtered = clamp(filtered, 0.0, 1.0);
            float factor = clamp(uIntensity, 0.0, 1.0);
            gl_FragColor = vec4(mix(rgb, filtered, factor), baseColor.a);
        }
    """.trimIndent()

    private var program: GlProgram? = null
    private var uTexMatrixLoc = -1
    private var sTextureLoc = -1
    private var uFilterTypeLoc = -1
    private var uIntensityLoc = -1
    private var aPositionLoc = -1
    private var aTexCoordLoc = -1

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private var currentFilterType: Int = 0
    private var currentIntensity: Float = 1.0f

    init {
        // Full screen quad (-1 to 1)
        val vertices = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        // Normalized texture coords (0 to 1)
        val texCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        vertexBuffer = GlProgram.createFloatBuffer(vertices)
        texCoordBuffer = GlProgram.createFloatBuffer(texCoords)
    }

    fun init() {
        if (program != null) return
        val prog = GlProgram(vertexShader, fragmentShader)
        program = prog
        aPositionLoc = prog.getAttribLocation("aPosition")
        aTexCoordLoc = prog.getAttribLocation("aTexCoord")
        uTexMatrixLoc = prog.getUniformLocation("uTexMatrix")
        sTextureLoc = prog.getUniformLocation("sTexture")
        uFilterTypeLoc = prog.getUniformLocation("uFilterType")
        uIntensityLoc = prog.getUniformLocation("uIntensity")
    }

    /**
     * Updates the active GLSL filter algorithm and blending intensity.
     * @param filterType 0 for original/none, 1..11 for cinematic filters, 12..16 for legacy.
     * @param intensity Blending weight between 0.0 (original) and 1.0 (100% full effect).
     */
    fun setFilter(filterType: Int, intensity: Float) {
        currentFilterType = filterType
        currentIntensity = intensity.coerceIn(0.0f, 1.0f)
    }

    fun draw(textureId: Int, texMatrix: FloatArray) {
        val prog = program ?: return
        prog.use()

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sTextureLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES20.glUniform1i(uFilterTypeLoc, currentFilterType)
        GLES20.glUniform1f(uIntensityLoc, currentIntensity)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(
            aPositionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(
            aTexCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            texCoordBuffer
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun release() {
        program?.release()
        program = null
    }
}
