# WatermelonDS external shaders (Vulkan)

`.wmshader` files contain plain GLSL, without Kotlin, surrounding quotes, or `\n`
escapes. This is a custom single-pass format compiled on the device by glslang.
It uses the native Vulkan presenter, independently of the RetroArch shader loader.

1. Copy examples from the [shaders](../shaders) directory into a folder on the device.
2. Select **External shader (Vulkan)** in **Settings → Video → Filter**.
3. Open **External shader source** and select your custom shader folder.
4. Open **External shader** to browse its subfolders and select a `.wmshader` file.
   The preference displays the selected shader's name, or
   **None selected. Pick one from the external shader folder.**
5. Open **External shader parameters** to edit the selected shader directly. This
   shortcut is hidden until a shader is selected and disabled if it declares no parameters.
6. After editing the original file, choose **External shader settings → Reload
   original file**, or select the file again in the browser. If folder access is
   revoked, select the folder again.

The app keeps a copy of the selected shader for subsequent sessions. Only one
external shader is active at a time. Selecting one does not modify the built-in
filters. Compilation errors display a diagnostic and preserve the installed
shader. If the Vulkan driver rejects its pipeline, the app uses passthrough;
the error is available under **External shader settings → Vulkan diagnostics**.
The browser lists folders first, then `.wmshader` files, and supports controller
navigation. Choosing a different source folder clears the active selection.

## Minimal example

```glsl
// @watermelon-shader 1
// @name My shader
// @parameter INTENSITY "Intensity" 0.5 0.0 1.0 0.01

vec3 watermelonShader(vec2 uv, vec2 pixel, vec3 color)
{
    return mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))), INTENSITY);
}
```

The numeric fields are **default, minimum, maximum, and step**. Up to six
parameters are allowed. Identifiers use uppercase letters, digits, and `_`,
starting with a letter; the `WM_` and `GL_` prefixes are reserved. Quoted labels
appear beside numeric fields, sliders, and −/+ buttons.

Settings are saved separately for each selected file URI. Switching between files
restores their saved parameter values. **Reload** preserves valid values for
matching identifiers. New files start with their declared defaults.

## GLSL interface

- `uv`: coordinates local to each screen, from 0 to 1.
- `pixel`: local coordinates multiplied by `256 × 192`, independently of the
  internal rendering resolution.
- `color`: the original RGB sample, with channel ordering already corrected.
- `wmSample(vec2 uv)`: samples RGB from the same screen, clamping coordinates to
  prevent sampling the other screen or the gap between them.
- `wmSourceSize`: `vec2(256.0, 192.0)`.
- `wmOutputSize()`: the current output area's size in pixels.
- Return value: the final RGB color. The emulator preserves layout transparency.

The app supplies the GLSL version, vertex shader, samplers, descriptors, and
parameters. Do not declare `#version`, `#include`, `#extension`, `layout`, `uniform`,
or `buffer`. Helper functions, constants, and ordinary macros are supported.
Files are limited to 128 KiB. The format does not directly accept RetroArch
`.slang` or `.slangp` files, multiple passes, or LUTs. The supplied examples are
already adapted to this interface.

## Examples

- [lcd1x_fine](../shaders/lcd1x_fine.wmshader): a fine, neutral LCD grid.
- [lcd1x_nds](../shaders/lcd1x_nds.wmshader): a native grid and NDS color correction.
- [zfast_lcd](../shaders/zfast_lcd.wmshader): pixel borders and a gamma adjustment.
- [LCD3x](../shaders/lcd3x.wmshader): Gigaherz's original RGB subpixel grid, adapted to native DS pixels.

This feature supports Vulkan only. The imported shader and its parameters apply
to both DS screens and to presentation on an external display.
