package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * Displays a button with an image (instead of text) that can be pressed or clicked by the user.
 */
open class ImageButton(context: Context) : ImageView(context) {
    init {
        isClickable = true
        isFocusable = true
    }
}
