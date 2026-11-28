package me.magnum.melonds.common.opengl

import android.opengl.GLES30
import android.util.Log

object ShaderFactory {
    fun createShaderProgram(source: ShaderProgramSource, strict: Boolean = false): Shader {
        val actual = if (source.isExternal) ExternalOpenGlShader.source(null) else source
        val vertex = createShader(GLES30.GL_VERTEX_SHADER, actual.vertexShaderSource, strict)
        var fragment = 0
        var program = 0
        try {
            fragment = createShader(GLES30.GL_FRAGMENT_SHADER, actual.fragmentShaderSource, strict)
            program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val result = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, result, 0)
            if (result[0] == GLES30.GL_FALSE) {
                val error = GLES30.glGetProgramInfoLog(program)
                Log.e("ShaderFactory", error)
                check(!strict) { "OpenGL link failed: $error" }
            }
            val filtering = when (actual.textureFiltering) {
                ShaderProgramSource.TextureFiltering.NEAREST -> GLES30.GL_NEAREST
                ShaderProgramSource.TextureFiltering.LINEAR -> GLES30.GL_LINEAR
            }
            return Shader(vertex, fragment, program, filtering, source.isExternal)
        } catch (error: Throwable) {
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(fragment)
            GLES30.glDeleteShader(vertex)
            throw error
        }
    }

    private fun createShader(type: Int, code: String, strict: Boolean): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        val result = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, result, 0)
        if (result[0] == GLES30.GL_FALSE) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e("ShaderFactory", error)
            if (strict) {
                GLES30.glDeleteShader(shader)
                error("OpenGL compilation failed: $error")
            }
        }
        return shader
    }
}
