package me.magnum.melonds.common.opengl

import android.opengl.GLES30

class Shader(
    private var vertexShaderId: Int,
    private var fragmentShaderId: Int,
    private var programId: Int,
    val textureFiltering: Int,
    private val isExternal: Boolean = false,
) {
    var attribUv = GLES30.glGetAttribLocation(programId, "vUV")
        private set
    var attribPos = GLES30.glGetAttribLocation(programId, "vPos")
        private set
    var attribAlpha = GLES30.glGetAttribLocation(programId, "vAlpha")
        private set
    var uniformTex = GLES30.glGetUniformLocation(programId, "tex")
        private set
    private var uniformParameters = GLES30.glGetUniformLocation(programId, "wmParameters[0]")
    private var uniformOutputSize = GLES30.glGetUniformLocation(programId, "wmViewportSize")
    private var externalGeneration = Long.MIN_VALUE
    private var activeGeneration = Long.MIN_VALUE

    fun use(outputWidth: Float = 1f, outputHeight: Float = 1f) {
        val snapshot = ExternalShaderRuntime.snapshot
        if (isExternal && externalGeneration != snapshot.generation) {
            // GL programs belong to their rendering context, including the external display.
            runCatching {
                ShaderFactory.createShaderProgram(ExternalOpenGlShader.source(snapshot.source), strict = true)
            }.onSuccess { next ->
                delete()
                vertexShaderId = next.vertexShaderId
                fragmentShaderId = next.fragmentShaderId
                programId = next.programId
                attribUv = next.attribUv
                attribPos = next.attribPos
                attribAlpha = next.attribAlpha
                uniformTex = next.uniformTex
                uniformParameters = next.uniformParameters
                uniformOutputSize = next.uniformOutputSize
                activeGeneration = snapshot.generation
            }.onFailure { error ->
                ExternalShaderRuntime.reportError(snapshot.generation, error.message ?: "OpenGL shader compilation failed.")
            }
            externalGeneration = snapshot.generation
        }
        GLES30.glUseProgram(programId)
        if (attribUv >= 0) GLES30.glEnableVertexAttribArray(attribUv)
        if (attribPos >= 0) GLES30.glEnableVertexAttribArray(attribPos)
        if (attribAlpha >= 0) GLES30.glEnableVertexAttribArray(attribAlpha)
        if (isExternal) {
            if (activeGeneration == snapshot.generation) {
                GLES30.glUniform1fv(uniformParameters, 6, snapshot.values.toFloatArray(), 0)
            }
            GLES30.glUniform2f(uniformOutputSize, outputWidth, outputHeight)
        }
    }

    fun delete() {
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)
        GLES30.glDeleteProgram(programId)
    }
}
