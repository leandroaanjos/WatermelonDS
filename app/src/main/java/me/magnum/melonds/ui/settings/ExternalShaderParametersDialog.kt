package me.magnum.melonds.ui.settings

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.view.Gravity
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import me.magnum.melonds.R
import me.magnum.melonds.common.opengl.ExternalShaderStore
import java.text.DecimalFormatSymbols
import kotlin.math.round
import kotlin.math.roundToInt

class ExternalShaderParametersDialog(private val fragment: Fragment) {
    fun show() {
        val context = fragment.requireContext()
        val source = ExternalShaderStore.source(context)
        if (source == null) {
            Toast.makeText(context, R.string.external_shader_import_first, Toast.LENGTH_LONG).show()
            return
        }
        if (source.parameters.isEmpty()) {
            Toast.makeText(context, R.string.external_shader_no_parameters, Toast.LENGTH_LONG).show()
            return
        }
        val parameters = source.parameters
        val values = ExternalShaderStore.values(context, source)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).roundToInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        content.addView(TextView(context).apply { setText(R.string.external_shader_parameters_help) })
        val validators = mutableListOf<() -> Boolean>()
        parameters.forEachIndexed { index, parameter ->
            val decimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator
            fun format(value: Float) = value.toString().replace('.', decimalSeparator)
            val field = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    (if (parameter.min < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                setSingleLine(true)
                gravity = Gravity.CENTER
                contentDescription = parameter.label
                setText(format(values[index]))
                setSelectAllOnFocus(true)
            }
            val slider = SeekBar(context).apply {
                max = ((parameter.max - parameter.min) / parameter.step).roundToInt()
                progress = ((values[index] - parameter.min) / parameter.step).roundToInt()
                contentDescription = parameter.label
            }
            var synchronizing = false
            fun validate(): Boolean {
                val value = field.text.toString().trim().replace(',', '.').toFloatOrNull()
                if (value == null || !value.isFinite() || value !in parameter.min..parameter.max) {
                    field.error = context.getString(R.string.external_shader_value_range, format(parameter.min), format(parameter.max))
                    return false
                }
                field.error = null
                values[index] = value
                slider.progress = ((value - parameter.min) / parameter.step).roundToInt().coerceIn(0, slider.max)
                return true
            }
            validators += ::validate
            fun setValue(value: Float) {
                synchronizing = true
                values[index] = value.coerceIn(parameter.min, parameter.max)
                field.setText(format(values[index]))
                field.error = null
                slider.progress = ((values[index] - parameter.min) / parameter.step).roundToInt().coerceIn(0, slider.max)
                synchronizing = false
            }
            field.doAfterTextChanged { if (!synchronizing) validate() }
            val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val buttonWidth = (56 * context.resources.displayMetrics.density).roundToInt()
            fun stepButton(text: String, direction: Int) = Button(context).apply {
                this.text = text
                contentDescription = context.getString(
                    if (direction < 0) R.string.external_shader_decrease else R.string.external_shader_increase,
                    parameter.label
                )
                setOnClickListener {
                    if (validate()) {
                        val stepped = round((values[index].toDouble() + direction * parameter.step.toDouble()) * 1_000_000) / 1_000_000
                        setValue(stepped.toFloat())
                    }
                }
            }
            content.addView(TextView(context).apply { text = parameter.label })
            controls.addView(stepButton("−", -1), LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            controls.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            controls.addView(stepButton("+", 1), LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            content.addView(controls)
            content.addView(slider.apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val value = round((parameter.min.toDouble() + progress * parameter.step.toDouble()) * 1_000_000) / 1_000_000
                            setValue(value.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            })
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(source.name)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.external_shader_apply, null)
            .setNegativeButton(R.string.external_shader_cancel, null)
            .setNeutralButton(R.string.external_shader_reset) { _, _ ->
                ExternalShaderStore.saveValues(context, source, parameters.map { it.default }.toFloatArray())
                show()
            }.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (validators.map { it() }.all { it }) {
                ExternalShaderStore.saveValues(context, source, values)
                dialog.dismiss()
            }
        }
    }
}
