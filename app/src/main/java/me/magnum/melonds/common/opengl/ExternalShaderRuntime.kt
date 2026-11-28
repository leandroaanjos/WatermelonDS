package me.magnum.melonds.common.opengl

/** Immutable snapshots shared by the main and external-display GL threads. */
object ExternalShaderRuntime {
    data class Snapshot(val generation: Long, val source: ExternalShaderSource?, val values: List<Float>)

    @Volatile var snapshot = Snapshot(0, null, List(6) { 0f })
        private set
    @Volatile var error: String = ""
        private set

    @Synchronized fun publish(source: ExternalShaderSource?, values: FloatArray = FloatArray(6)) {
        val previous = snapshot
        val changed = previous.source?.text != source?.text
        snapshot = Snapshot(previous.generation + if (changed) 1 else 0, source, values.copyOf(6).toList())
        if (changed) error = ""
    }

    @Synchronized fun reportError(generation: Long, message: String) {
        if (snapshot.generation == generation) error = message
    }
}
