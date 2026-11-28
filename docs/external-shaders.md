# WatermelonDS external shaders

`.wmshader` files contain plain GLSL, without Kotlin, surrounding quotes, or `\n`
escapes. This is a custom single-pass format compiled on the device. Vulkan uses glslang;
OpenGL presentation (including Compute) uses the OpenGL ES 3 driver. Both paths
are independent of the RetroArch shader loader.

1. Copy examples from the [shaders](../shaders) directory into a folder on the device.
2. Select **External shader** in **Settings → Video → Filter**.
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
the error is available under **External shader settings → Shader diagnostics**.
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
- `wmIsTopScreen()`: true for the DS top screen, false for the bottom screen,
  regardless of layout size, position, or which display presents it.
- Return value: the final RGB color. The emulator preserves layout transparency.

The app supplies the GLSL version, vertex shader, samplers, descriptors, and
parameters. Do not declare `#version`, `#include`, `#extension`, `layout`, `uniform`,
or `buffer`. Helper functions, constants, and ordinary macros are supported.
Files are limited to 128 KiB. The format does not directly accept RetroArch
`.slang` or `.slangp` files, multiple passes, or LUTs. The supplied examples are
already adapted to this interface.

## Examples

- [lcd1x_fine_gamma](../shaders/lcd1x_fine_gamma.wmshader): the original PSP grid without PSP color correction or extra smoothing, with adjustable grid size and zfast gamma shaping.
- [lcd1x_nds](../shaders/lcd1x_nds.wmshader): a native grid and NDS color correction.
- [zfast_lcd](../shaders/zfast_lcd.wmshader): pixel borders and a gamma adjustment.
- [LCD3x](../shaders/lcd3x.wmshader): Gigaherz's original RGB subpixel grid, adapted to native DS pixels.

This feature supports Vulkan and OpenGL presentation, including Compute.
The imported shader and its parameters apply
to both DS screens and to presentation on an external display.

For `lcd1x_fine_gamma`, **Top screen grid size** and **Bottom screen grid size**
range from 1.0 to 2.0 in steps of 0.1, defaulting to 1.2 and 1.5 respectively.
At 1.0, the grid matches the PSP formula applied to each native DS screen;
larger values enlarge the cells. Defaults are LCD brightness 6.0 and gamma 0.3.
Set **Gamma effect strength** to 0 for a neutral grid without gamma shaping.
The former **Grid size** value is preserved for the top screen; the new bottom
screen control starts at 1.5. Reload the file to get the new controls; saved
brightness and gamma values are preserved unless reset. Set the top screen value
to 1.2 manually if a different value was already saved. This shader now requires
an app build that exposes `wmIsTopScreen()`; older shader files remain supported.

OpenGL imports are validated on a separate EGL context before replacing the saved
file. Each presentation context owns its program, including the external display.
Parameter changes update uniforms without recompiling. Shader reloads take effect
on the next rendered frame. If OpenGL rejects a program in a presentation context,
the previous program is kept (passthrough if none was installed), and its error is
available under **Shader diagnostics**. Backend-specific GLSL features may need
adaptation when switching between Vulkan GLSL and GLSL ES 3. Both backends expose
the same documented helpers and up to six parameters.
