package io.johnsonlee.testpilot.simulator.window

import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View

/**
 * Abstract base class for a top-level window look and behavior policy.
 * The Window is the top-level container for a view hierarchy.
 */
class Window(
    val width: Int = 1080,
    val height: Int = 1920
) {
    var contentView: View? = null
        private set

    fun setContentView(view: View) {
        contentView = view
        // Trigger initial layout
        measureAndLayout()
    }

    fun measureAndLayout() {
        contentView?.let { view ->
            val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight())
        }
    }

    fun draw(): Canvas {
        val canvas = Canvas(width, height)
        contentView?.draw(canvas)
        return canvas
    }

    /**
     * Dispatch a touch event to the view hierarchy.
     *
     * @param event The motion event to dispatch.
     * @return True if the event was handled, false otherwise.
     */
    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return contentView?.dispatchTouchEvent(event) ?: false
    }
}
