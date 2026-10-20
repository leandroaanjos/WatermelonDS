package me.magnum.melonds.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSAndroidInterface
import me.magnum.melonds.R
import me.magnum.melonds.common.opengl.ExternalShaderStore

class ExternalShaderSettingsDialog(private val fragment: Fragment) {
    private companion object {
        const val FOLDER_KEY = "external_shader_folder"
        const val FOLDER_NAME_KEY = "external_shader_folder_name"
    }
    private var sourcePreference: Preference? = null
    private var shaderPreference: Preference? = null
    private var parameterPreference: Preference? = null
    private fun preferences() = PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
    private fun folderUri(): Uri? = preferences().getString(FOLDER_KEY, null)?.let(Uri::parse)

    private val folderPicker = fragment.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) work(showReady = false, after = { showBrowser() }) {
            val context = fragment.requireContext().applicationContext
            val name = withContext(Dispatchers.IO) {
                val directory = DocumentFile.fromTreeUri(context, uri)
                require(directory != null && directory.isDirectory && directory.canRead()) { context.getString(R.string.external_shader_folder_error) }
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                directory.name ?: context.getString(R.string.external_shader_folder)
            }
            if (folderUri() != uri) ExternalShaderStore.remove(context)
            preferences().edit().putString(FOLDER_KEY, uri.toString()).putString(FOLDER_NAME_KEY, name).apply()
        }
    }

    fun bind(source: Preference?, shader: Preference?, parameters: Preference?) {
        sourcePreference = source
        shaderPreference = shader
        parameterPreference = parameters
        source?.setOnPreferenceClickListener { folderPicker.launch(folderUri()); true }
        shader?.setOnPreferenceClickListener { showBrowser(); true }
        refresh()
    }

    private fun refresh() {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        sourcePreference?.summary = preferences().getString(FOLDER_NAME_KEY, null)
            ?: context.getString(R.string.external_shader_pick_folder)
        shaderPreference?.summary = ExternalShaderStore.source(context)?.name
            ?: context.getString(R.string.external_shader_none_selected)
        shaderPreference?.isEnabled = folderUri() != null
        updateVisibility(preferences().getString("video_filtering", "none") == "external")
    }

    fun updateVisibility(externalEnabled: Boolean) {
        sourcePreference?.isVisible = externalEnabled
        shaderPreference?.isVisible = externalEnabled
        val source = ExternalShaderStore.source(fragment.requireContext())
        parameterPreference?.isVisible = externalEnabled && source != null
        parameterPreference?.isEnabled = source?.parameters?.isNotEmpty() == true
    }

    private fun work(showReady: Boolean = true, after: () -> Unit = {}, action: suspend () -> Unit) {
        val progress = AlertDialog.Builder(fragment.requireContext()).setMessage(R.string.external_shader_compiling).setCancelable(false).show()
        fragment.lifecycleScope.launch {
            val result = try { runCatching { action() } } finally { progress.dismiss() }
            if (!fragment.isAdded) return@launch
            result.fold({
                refresh()
                after()
                if (showReady) Toast.makeText(fragment.requireContext(), R.string.external_shader_ready, Toast.LENGTH_LONG).show()
            }, { error ->
                if (error is CancellationException) throw error
                AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.external_shader_error)
                    .setMessage(error.message ?: error.toString()).setPositiveButton(R.string.external_shader_ok, null).show()
            })
        }
    }

    private data class Entry(val name: String, val document: DocumentFile?, val directory: Boolean, val parent: Boolean = false)

    private fun showBrowser() {
        val context = fragment.requireContext()
        val uri = folderUri() ?: return
        val root = DocumentFile.fromTreeUri(context, uri) ?: return
        val view = fragment.layoutInflater.inflate(R.layout.dialog_shader_browser, null)
        val path = view.findViewById<TextView>(R.id.textShaderBrowserPath)
        val empty = view.findViewById<TextView>(R.id.textShaderBrowserEmpty)
        val list = view.findViewById<RecyclerView>(R.id.listShaderBrowser)
        view.findViewById<View>(R.id.containerShaderBrowserList).apply {
            layoutParams = layoutParams.apply { height = minOf(height, (context.resources.displayMetrics.heightPixels * 0.45f).toInt()) }
        }
        val stack = mutableListOf(root)
        var job: Job? = null
        val dialog = AlertDialog.Builder(context).setTitle(R.string.external_shader_selection).setView(view)
            .setNegativeButton(R.string.external_shader_cancel, null).create()
        lateinit var adapter: BrowserAdapter
        lateinit var load: () -> Unit
        adapter = BrowserAdapter({ ExternalShaderStore.selectedUri(context) }) { entry ->
            when {
                entry.parent -> { stack.removeAt(stack.lastIndex); load() }
                entry.directory -> { stack.add(entry.document!!); load() }
                else -> {
                    dialog.dismiss()
                    work { ExternalShaderStore.importUri(context.applicationContext, entry.document!!.uri) }
                }
            }
        }
        load = {
            job?.cancel()
            adapter.submit(emptyList())
            path.text = "/" + stack.drop(1).joinToString("/") { it.name.orEmpty() }
            empty.setText(R.string.external_shader_loading)
            empty.isVisible = true
            val directory = stack.last()
            val hasParent = stack.size > 1
            job = fragment.lifecycleScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    require(directory.isDirectory && directory.canRead()) { context.getString(R.string.external_shader_folder_error) }
                    val entries = directory.listFiles().mapNotNull { child ->
                        val name = child.name ?: return@mapNotNull null
                        val isDirectory = child.isDirectory
                        if (isDirectory || name.endsWith(".wmshader", ignoreCase = true)) Entry(name, child, isDirectory) else null
                    }.sortedWith(compareBy<Entry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    (if (hasParent) listOf(Entry("..", null, directory = true, parent = true)) else emptyList()) + entries
                } }
                result.fold({ entries ->
                    adapter.submit(entries)
                    empty.setText(R.string.external_shader_folder_empty)
                    empty.isVisible = entries.isEmpty()
                    val selected = adapter.selectedIndex().coerceAtLeast(0)
                    list.scrollToPosition(selected)
                    list.post { list.findViewHolderForAdapterPosition(selected)?.itemView?.requestFocus() }
                }, { error ->
                    if (error is CancellationException) throw error
                    empty.text = context.getString(R.string.external_shader_folder_error)
                    empty.isVisible = true
                })
            }
        }
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        fun back() {
            if (stack.size > 1) { stack.removeAt(stack.lastIndex); load() } else dialog.dismiss()
        }
        val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OnBackInvokedCallback { back() } else null
        dialog.setOnShowListener {
            if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
            load()
        }
        dialog.setOnDismissListener {
            job?.cancel()
            if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                dialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        }
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP) back()
                true
            } else false
        }
        dialog.show()
    }

    private class BrowserAdapter(private val selectedUri: () -> String?, private val onClick: (Entry) -> Unit) : RecyclerView.Adapter<BrowserAdapter.Holder>() {
        private var entries: List<Entry> = emptyList()
        @Suppress("NotifyDataSetChanged")
        fun submit(items: List<Entry>) { entries = items; notifyDataSetChanged() }
        fun selectedIndex() = entries.indexOfFirst { it.document?.uri?.toString() == selectedUri() }
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_shader_browser_entry, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { holder.bind(entries[position]) }
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(entry: Entry) {
                itemView.findViewById<TextView>(R.id.textShaderEntryName).text = entry.name
                itemView.findViewById<ImageView>(R.id.imageShaderEntryIcon).setImageResource(
                    if (entry.parent) R.drawable.ic_arrow_up else if (entry.directory) R.drawable.ic_folder else R.drawable.ic_file
                )
                itemView.findViewById<View>(R.id.imageShaderEntryChevron).isVisible = entry.directory && !entry.parent
                itemView.findViewById<View>(R.id.textShaderEntryDetail).isVisible = false
                itemView.findViewById<View>(R.id.textShaderEntryBadge).isVisible = false
                itemView.isSelected = !entry.directory && entry.document?.uri?.toString() == selectedUri()
                itemView.setOnClickListener { onClick(entry) }
            }
        }
    }

    fun show() {
        val context = fragment.requireContext()
        val source = ExternalShaderStore.source(context)
        val options = intArrayOf(R.string.external_shader_reload,
            R.string.external_shader_remove, R.string.external_shader_diagnostics)
        AlertDialog.Builder(context).setTitle(source?.name ?: context.getString(R.string.external_shader_settings))
            .setItems(options.map { context.getString(it) }.toTypedArray()) { _, which ->
                when (which) {
                    0 -> work { ExternalShaderStore.reload(context.applicationContext) }
                    1 -> AlertDialog.Builder(context).setMessage(R.string.external_shader_remove_confirm)
                        .setPositiveButton(R.string.external_shader_ok) { _, _ -> work(showReady = false) { ExternalShaderStore.remove(context.applicationContext) } }
                        .setNegativeButton(R.string.external_shader_cancel, null).show()
                    2 -> AlertDialog.Builder(context).setTitle(R.string.external_shader_diagnostics)
                        .setMessage(listOf(
                            me.magnum.melonds.common.opengl.ExternalShaderRuntime.error.takeIf { it.isNotEmpty() }?.let { "OpenGL: $it" },
                            MelonDSAndroidInterface.getExternalShaderErrorNative().takeIf { it.isNotEmpty() }?.let { "Vulkan: $it" },
                        ).filterNotNull().joinToString("\n\n").ifEmpty { context.getString(R.string.external_shader_no_errors) })
                        .setPositiveButton(R.string.external_shader_ok, null).show()
                }
            }.setNegativeButton(R.string.external_shader_cancel, null).show()
    }
}
