package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * FrameLayout is designed to block out an area on the screen to display a single item.
 * Child views are drawn in a stack, with the most recently added child on top.
 */
open class FrameLayout(context: Context) : ViewGroup(context) {

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)

        var maxWidth = 0
        var maxHeight = 0

        children.forEach { child ->
            if (child.visibility != GONE) {
                maxWidth = maxOf(maxWidth, child.getMeasuredWidth())
                maxHeight = maxOf(maxHeight, child.getMeasuredHeight())
            }
        }

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(maxWidth, widthSize)
            else -> maxWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(maxHeight, heightSize)
            else -> maxHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        children.forEach { child ->
            if (child.visibility != GONE) {
                val childWidth = child.getMeasuredWidth()
                val childHeight = child.getMeasuredHeight()

                // Default: top-left alignment
                val childLeft = 0
                val childTop = 0

                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
            }
        }
    }

    class LayoutParams(width: Int, height: Int) : View.LayoutParams(width, height) {
        var gravity: Int = -1
    }
}
