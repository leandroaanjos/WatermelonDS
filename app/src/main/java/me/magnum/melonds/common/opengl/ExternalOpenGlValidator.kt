package me.magnum.melonds.common.opengl

import android.opengl.EGL14
import android.opengl.EGLConfig

/** Validate imports off the UI thread, without using either presentation context. */
object ExternalOpenGlValidator {
    // The default display is shared with the emulator. Do not terminate it here.
    private val display by lazy {
        EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
            check(it != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(it, IntArray(2), 0, IntArray(2), 0)) {
                "Could not initialize OpenGL shader validation."
            }
        }
    }

    @Synchronized fun validate(source: ExternalShaderSource) {
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "OpenGL ES 3 shader validation is unavailable."
        }
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Could not create OpenGL shader validation context." }
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        val previousDisplay = EGL14.eglGetCurrentDisplay()
        val previousContext = EGL14.eglGetCurrentContext()
        val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        try {
            check(surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, context)) {
                "Could not activate OpenGL shader validation context."
            }
            ShaderFactory.createShaderProgram(ExternalOpenGlShader.source(source), strict = true).delete()
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (previousContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext)
            }
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }
}
