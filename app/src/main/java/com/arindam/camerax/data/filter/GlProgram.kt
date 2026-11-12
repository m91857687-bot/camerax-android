package com.arindam.camerax.data.filter

import android.opengl.GLES20
import com.arindam.camerax.util.log.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Utility wrapper for compiling and linking OpenGL ES programs.
 */
class GlProgram(vertexSource: String, fragmentSource: String) {

    val programId: Int

    init {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        programId = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (programId == 0) {
            throw RuntimeException("Could not create program")
        }
        GLES20.glAttachShader(programId, vertexShader)
        checkGlError("glAttachShader vertex")
        GLES20.glAttachShader(programId, fragmentShader)
        checkGlError("glAttachShader fragment")
        GLES20.glLinkProgram(programId)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw RuntimeException("Could not link program: $info")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun getAttribLocation(name: String): Int {
        val location = GLES20.glGetAttribLocation(programId, name)
        if (location < 0) {
            Logger.warning(TAG, "Attribute location not found: $name")
        }
        return location
    }

    fun getUniformLocation(name: String): Int {
        val location = GLES20.glGetUniformLocation(programId, name)
        if (location < 0) {
            Logger.warning(TAG, "Uniform location not found: $name")
        }
        return location
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
        }
    }

    companion object {
        private const val TAG = "GlProgram"

        fun loadShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            checkGlError("glCreateShader type $shaderType")
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader $shaderType: $info")
            }
            return shader
        }

        fun checkGlError(op: String) {
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                val msg = "$op: glError 0x${Integer.toHexString(error)}"
                Logger.error(TAG, msg)
            }
        }

        fun createFloatBuffer(coords: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(coords.size * 4).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(coords)
                    position(0)
                }
            }
        }
    }
}
