package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * A user interface element for entering and modifying text.
 */
open class EditText(context: Context) : TextView(context) {

    var hint: String = ""
        set(value) {
            field = value
            requestLayout()
        }

    var inputType: Int = TYPE_CLASS_TEXT

    private var selectionStart: Int = 0
    private var selectionEnd: Int = 0

    init {
        isFocusable = true
        isClickable = true
    }

    fun setSelection(index: Int) {
        selectionStart = index
        selectionEnd = index
    }

    fun setSelection(start: Int, end: Int) {
        selectionStart = start
        selectionEnd = end
    }

    fun getSelectionStart(): Int = selectionStart
    fun getSelectionEnd(): Int = selectionEnd

    fun append(text: CharSequence) {
        this.text = this.text + text.toString()
    }

    companion object {
        const val TYPE_CLASS_TEXT = 0x00000001
        const val TYPE_CLASS_NUMBER = 0x00000002
        const val TYPE_CLASS_PHONE = 0x00000003
        const val TYPE_CLASS_DATETIME = 0x00000004
        const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
        const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    }
}
