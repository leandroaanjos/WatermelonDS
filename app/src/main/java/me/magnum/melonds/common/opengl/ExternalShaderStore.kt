package me.magnum.melonds.common.opengl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSAndroidInterface
import org.json.JSONObject
import java.io.File

object ExternalShaderStore {
    private const val URI_KEY = "external_shader_uri"
    private const val VALUES_KEY = "external_shader_values"
    private val mutex = Mutex()
    private var compiledBody: String? = null
    private var validatedOpenGlBody: String? = null
    private fun installed(context: Context) = AtomicFile(File(context.filesDir, "imported.wmshader"))
    private fun valuesKey(uri: String?): String = if (uri == null) VALUES_KEY else VALUES_KEY + "_" +
        java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun selectedUri(context: Context): String? = PreferenceManager.getDefaultSharedPreferences(context).getString(URI_KEY, null)
    fun source(context: Context): ExternalShaderSource? = runCatching {
        ExternalShaderSource.parse(installed(context).readFully().toString(Charsets.UTF_8))
    }.getOrNull()

    fun values(context: Context, source: ExternalShaderSource): FloatArray {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = valuesKey(selectedUri(context))
        if (key != VALUES_KEY && !preferences.contains(key) && preferences.contains(VALUES_KEY)) {
            preferences.edit().putString(key, preferences.getString(VALUES_KEY, "{}")).remove(VALUES_KEY).apply()
        }
        val json = runCatching { JSONObject(preferences.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return source.parameters.map { parameter ->
            val value = runCatching { json.getDouble(parameter.id).toFloat() }.getOrDefault(parameter.default)
            if (value.isFinite() && value in parameter.min..parameter.max) value else parameter.default
        }.toFloatArray()
    }

    fun saveValues(context: Context, source: ExternalShaderSource, values: FloatArray) {
        require(values.size == source.parameters.size)
        val json = JSONObject()
        source.parameters.forEachIndexed { index, parameter ->
            require(values[index].isFinite() && values[index] in parameter.min..parameter.max)
            json.put(parameter.id, values[index].toDouble())
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(valuesKey(selectedUri(context)), json.toString()).apply()
        MelonDSAndroidInterface.setExternalShaderParametersNative(values.copyOf(6))
        ExternalShaderRuntime.publish(source, values)
    }

    private fun compile(context: Context, source: ExternalShaderSource) {
        val body = source.compilerBody()
        val renderer = PreferenceManager.getDefaultSharedPreferences(context).getString("video_renderer", "software")
        if (renderer != "vulkan" && validatedOpenGlBody != body) {
            ExternalOpenGlValidator.validate(source)
            validatedOpenGlBody = body
        }
        if (compiledBody == body && MelonDSAndroidInterface.getExternalShaderErrorNative().isEmpty()) return
        val error = MelonDSAndroidInterface.compileExternalShaderNative(body)
        require(error.isEmpty()) { error }
        compiledBody = body
    }

    suspend fun restore(context: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        source(context)?.let { source ->
            compile(context, source)
            val values = values(context, source)
            MelonDSAndroidInterface.setExternalShaderParametersNative(values.copyOf(6))
            ExternalShaderRuntime.publish(source, values)
        }
    } }

    suspend fun importUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) { mutex.withLock {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= ExternalShaderSource.MAX_BYTES) { "Shader exceeds 128 KiB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Could not open shader file.")
        val previous = source(context)
        if (previous != null) values(context, previous) // Migrate the previous selection before changing its URI.
        val source = ExternalShaderSource.parse(bytes.toString(Charsets.UTF_8))
        // Compile before replacing the installed file or any saved parameters.
        compile(context, source)
        val file = installed(context)
        try {
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) } catch (error: Throwable) { file.failWrite(output); throw error }
        } catch (error: Throwable) {
            compiledBody = null
            runCatching {
                if (previous != null) compile(context, previous) else MelonDSAndroidInterface.clearExternalShaderNative()
            }.onFailure { MelonDSAndroidInterface.clearExternalShaderNative(); compiledBody = null }
            throw error
        }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(URI_KEY, uri.toString()).apply()
        saveValues(context, source, values(context, source))
    } }

    suspend fun reload(context: Context) {
        val uri = PreferenceManager.getDefaultSharedPreferences(context).getString(URI_KEY, null) ?: error("Select a shader first.")
        importUri(context, Uri.parse(uri))
    }

    suspend fun remove(context: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        source(context)?.let { values(context, it) } // Preserve legacy adjustments before clearing the selection.
        installed(context).delete()
        compiledBody = null
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(URI_KEY).remove(VALUES_KEY).apply()
        MelonDSAndroidInterface.clearExternalShaderNative()
        ExternalShaderRuntime.publish(null)
    } }
}
