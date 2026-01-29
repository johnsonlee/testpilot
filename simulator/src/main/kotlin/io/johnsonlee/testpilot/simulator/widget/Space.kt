package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.view.View

/**
 * Space is a lightweight View subclass that may be used to create gaps between components.
 */
class Space(context: Context) : View(context) {

    override fun draw(canvas: Canvas) {
        // Space draws nothing
    }

    override fun onDraw(canvas: Canvas) {
        // Space draws nothing
    }
}
