package me.magnum.melonds.common.opengl

import org.junit.Assert.*
import org.junit.Test

class ExternalShaderSourceTest {
    private val shader = """
        // @watermelon-shader 1
        // @name Example
        // @parameter AMOUNT "Effect amount" 0.5 0.0 1.0 0.01
        vec3 watermelonShader(vec2 uv, vec2 pixel, vec3 color) { return color * AMOUNT; }
    """.trimIndent()

    @Test fun mapsDeclaredParametersToTheRuntimeSlots() {
        val parsed = ExternalShaderSource.parse(shader)
        assertEquals("Example", parsed.name)
        assertEquals(0.5f, parsed.parameters.single().default)
        assertTrue(parsed.compilerBody().contains("#define AMOUNT (pushConstants.externalParameters[0])"))
        assertTrue(parsed.compilerBody().contains("return color * AMOUNT;"))
    }

    @Test fun rejectsMalformedRangesDuplicatesAndNonFiniteValues() {
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader.replace("0.5 0.0 1.0 0.01", "0.5 1.0 0.0 0.01")) }
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader.replace("0.5 0.0 1.0 0.01", "NaN 0.0 1.0 0.01")) }
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader + "\n// @parameter AMOUNT \"Duplicate\" 0.5 0.0 1.0 0.01") }
    }

    @Test fun rejectsIncompatibleShaderInterfacesAndOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader + "\n#version 450") }
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader + "\nuniform sampler2D other;") }
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader + " ".repeat(ExternalShaderSource.MAX_BYTES)) }
        assertThrows(IllegalArgumentException::class.java) { ExternalShaderSource.parse(shader.replace("// @watermelon-shader 1", "// @watermelon-shader 2")) }
    }

    @Test fun acceptsShadersWithoutParametersAndWindowsLineEndings() {
        val source = shader.lines().filterNot { "@parameter" in it }.joinToString("\r\n")
        assertTrue(ExternalShaderSource.parse("\uFEFF$source").parameters.isEmpty())
    }
}
