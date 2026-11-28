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
            "aggressive" -> context.getString(R.string.performance_hint_budget_aggressive_summary)
            else -> context.getString(R.string.performance_hint_budget_default_summary)
        }
        return "${entry ?: context.getString(R.string.performance_hint_budget_default)}. $description"
    }
}
