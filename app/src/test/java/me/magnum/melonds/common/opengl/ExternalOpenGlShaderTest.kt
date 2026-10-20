package me.magnum.melonds.common.opengl

import org.junit.Assert.*
import org.junit.Test

class ExternalOpenGlShaderTest {
    private val source = ExternalShaderSource.parse("""
        // @watermelon-shader 1
        // @name Test
        // @parameter AMOUNT "Amount" 0.5 0.0 1.0 0.1
        vec3 watermelonShader(vec2 uv, vec2 pixel, vec3 color) { return color * AMOUNT; }
    """.trimIndent())

    @Test fun generatesEs3InterfaceWithUniformParameters() {
        val program = ExternalOpenGlShader.source(source)
        assertTrue(program.vertexShaderSource.startsWith("#version 300 es"))
        assertTrue(program.fragmentShaderSource.startsWith("#version 300 es"))
        assertTrue(program.fragmentShaderSource.contains("#define AMOUNT (wmParameters[0])"))
        assertFalse(program.fragmentShaderSource.contains("pushConstants"))
        assertTrue(program.fragmentShaderSource.contains(source.text))
    }

    @Test fun parameterUpdatesDoNotRequireNewProgramAndSnapshotsStayUnchanged() {
        ExternalShaderRuntime.publish(source, floatArrayOf(0.5f))
        val previous = ExternalShaderRuntime.snapshot
        val values = floatArrayOf(0.8f)
        ExternalShaderRuntime.publish(source, values)
        values[0] = 0f
        assertEquals(previous.generation, ExternalShaderRuntime.snapshot.generation)
        assertEquals(0.5f, previous.values[0])
        assertEquals(0.8f, ExternalShaderRuntime.snapshot.values[0])
        ExternalShaderRuntime.publish(null)
        assertTrue(previous.generation < ExternalShaderRuntime.snapshot.generation)
        assertNull(ExternalShaderRuntime.snapshot.source)
    }

    @Test fun staleContextErrorsDoNotReplaceCurrentDiagnostics() {
        ExternalShaderRuntime.publish(source)
        val previous = ExternalShaderRuntime.snapshot.generation
        ExternalShaderRuntime.publish(null)
        ExternalShaderRuntime.reportError(previous, "Stale error")
        assertEquals("", ExternalShaderRuntime.error)
        assertTrue(ExternalOpenGlShader.source(null).fragmentShaderSource.contains("return color;"))
    }
}
