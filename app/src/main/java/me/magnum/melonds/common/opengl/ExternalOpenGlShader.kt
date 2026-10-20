package me.magnum.melonds.common.opengl

object ExternalOpenGlShader {
    fun source(shader: ExternalShaderSource?): ShaderProgramSource {
        val bindings = shader?.parameters?.mapIndexed { index, parameter ->
            "#define ${parameter.id} (wmParameters[$index])"
        }?.joinToString("\n").orEmpty()
        val body = shader?.text ?: "vec3 watermelonShader(vec2 uv, vec2 pixel, vec3 color) { return color; }"
        return ShaderProgramSource(ShaderProgramSource.TextureFiltering.NEAREST, vertex, """
            #version 300 es
            precision highp float;
            uniform sampler2D tex;
            uniform float wmParameters[6];
            uniform vec2 wmViewportSize;
            in vec2 wmUv;
            in float wmAlpha;
            out vec4 wmFragmentColor;
            const vec2 wmSourceSize = vec2(256.0, 192.0);
            bool wmBottom;
            vec2 wmOutputSize() { return wmViewportSize; }
            vec3 wmSample(vec2 local) {
                vec2 halfTexel = 0.5 / vec2(textureSize(tex, 0));
                float base = wmBottom ? 194.0 / 386.0 : 0.0;
                vec2 packed = vec2(local.x, base + local.y * (192.0 / 386.0));
                packed = clamp(packed, vec2(halfTexel.x, base + halfTexel.y),
                    vec2(1.0 - halfTexel.x, base + 192.0 / 386.0 - halfTexel.y));
                return texture(tex, packed).bgr;
            }
            $bindings
            #line 1
            $body
            void main() {
                wmBottom = wmUv.y > 0.5;
                float base = wmBottom ? 194.0 / 386.0 : 0.0;
                vec2 local = vec2(wmUv.x, (wmUv.y - base) * (386.0 / 192.0));
                vec3 color = wmSample(local);
                wmFragmentColor = vec4(clamp(watermelonShader(local, local * wmSourceSize, color), 0.0, 1.0), wmAlpha);
            }
        """.trimIndent().trimStart())
    }

    private val vertex = """
        #version 300 es
        in vec2 vUV;
        in vec2 vPos;
        in float vAlpha;
        out vec2 wmUv;
        out float wmAlpha;
        void main() {
            gl_Position = vec4(vPos, 0.0, 1.0);
            wmUv = vUV;
            wmAlpha = vAlpha;
        }
    """.trimIndent()
}
