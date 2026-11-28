package me.magnum.melonds.common.opengl

/** WatermelonDS single-pass RGB shader. Metadata is kept in GLSL comments. */
data class ExternalShaderSource(val name: String, val text: String, val parameters: List<Parameter>) {
    data class Parameter(val id: String, val label: String, val default: Float, val min: Float, val max: Float, val step: Float)

    fun compilerBody(): String {
        return parameters.mapIndexed { index, parameter ->
            "#define ${parameter.id} (pushConstants.externalParameters[$index])"
        }.joinToString("\n") + "\n#line 1 1\n" + text
    }

    companion object {
        const val MAX_BYTES = 131072
        private val parameterLine = Regex("""//\s*@parameter\s+([A-Z][A-Z0-9_]*)\s+"([^"]+)"\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s*""")

        fun parse(text: String): ExternalShaderSource {
            require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) { "Shader must be at most 128 KiB." }
            val lines = text.removePrefix("\uFEFF").lines()
            require(lines.firstOrNull { it.isNotBlank() }?.trim() == "// @watermelon-shader 1") { "Expected // @watermelon-shader 1" }
            val name = lines.firstOrNull { it.trim().startsWith("// @name ") }?.trim()?.removePrefix("// @name ")?.trim()
            require(!name.isNullOrEmpty() && name.length <= 80) { "A shader name is required (// @name)." }
            val parameters = lines.filter { it.trim().startsWith("// @parameter") }.map { line ->
                val match = parameterLine.matchEntire(line.trim()) ?: error("Invalid parameter declaration: $line")
                val (id, label, initial, lower, upper, increment) = match.destructured
                require(label.length <= 80 && id.length <= 48 && !id.startsWith("WM_") && !id.startsWith("GL_") && "__" !in id) { "Invalid parameter identifier or label." }
                val values = listOf(initial, lower, upper, increment).map { it.toFloatOrNull() ?: error("Invalid number in $id") }
                require(values.all { it.isFinite() && kotlin.math.abs(it) <= 100000f }) { "Non-finite or excessive value in $id" }
                val (default, min, max, step) = values
                require(min < max && default in min..max && step > 0f && step <= max - min && (max - min) / step <= 10000f) { "Invalid range or step in $id" }
                Parameter(id, label, default, min, max, step)
            }
            require(parameters.size <= 6 && parameters.map { it.id }.distinct().size == parameters.size) { "Use up to six unique parameters." }
            val code = text.replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"), "")
            require(!Regex("""(?m)^\s*#\s*(version|include|extension)\b|\blayout\s*\(|\b(uniform|buffer)\s""").containsMatchIn(code)) { "The app supplies the GLSL version, resources and shader interface." }
            require(Regex("""\bvec3\s+watermelonShader\s*\(""").containsMatchIn(code)) { "Define vec3 watermelonShader(vec2 uv, vec2 pixel, vec3 color)." }
            return ExternalShaderSource(name, text.removePrefix("\uFEFF"), parameters)
        }
    }
}
