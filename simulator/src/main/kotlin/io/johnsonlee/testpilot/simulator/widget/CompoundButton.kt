package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * A button with two states, checked and unchecked.
 */
abstract class CompoundButton(context: Context) : Button(context) {

    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onCheckedChangeListener?.onCheckedChanged(this, value)
            }
        }

    private var onCheckedChangeListener: OnCheckedChangeListener? = null

    fun toggle() {
        isChecked = !isChecked
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        onCheckedChangeListener = listener
    }

    fun setOnCheckedChangeListener(listener: (CompoundButton, Boolean) -> Unit) {
        onCheckedChangeListener = OnCheckedChangeListener { buttonView, isChecked ->
            listener(buttonView, isChecked)
        }
    }

    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
    }

    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean)
    }
}
