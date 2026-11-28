package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import me.magnum.melonds.R

class PerformanceHintBudgetPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        val description = when (value) {
            "balanced" -> context.getString(R.string.performance_hint_budget_balanced_summary)
            "compatibility" -> context.getString(R.string.performance_hint_budget_compatibility_summary)
            else -> context.getString(R.string.performance_hint_budget_automatic_summary)
        }
        return "${entry ?: context.getString(R.string.performance_hint_budget_automatic)} — " +
            "${context.getString(R.string.performance_hint_budget_summary)} $description"
    }
}
