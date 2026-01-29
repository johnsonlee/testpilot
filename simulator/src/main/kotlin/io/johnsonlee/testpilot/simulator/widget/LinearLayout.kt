package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * A Layout that arranges its children in a single column or a single row.
 */
class LinearLayout(context: Context) : ViewGroup(context) {

    var orientation: Int = VERTICAL
    private var gravity: Int = 0

    override fun setGravity(gravity: Int) {
        this.gravity = gravity
    }

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (orientation == VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec)
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun measureVertical(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)

        var totalHeight = 0
        var maxWidth = 0

        children.forEach { child ->
            if (child.visibility != GONE) {
                totalHeight += child.getMeasuredHeight()
                maxWidth = maxOf(maxWidth, child.getMeasuredWidth())
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
            MeasureSpec.AT_MOST -> minOf(totalHeight, heightSize)
            else -> totalHeight
        }

        setMeasuredDimension(width, height)
    }

    private fun measureHorizontal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)

        var totalWidth = 0
        var maxHeight = 0

        children.forEach { child ->
            if (child.visibility != GONE) {
                totalWidth += child.getMeasuredWidth()
                maxHeight = maxOf(maxHeight, child.getMeasuredHeight())
            }
        }

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(totalWidth, widthSize)
            else -> totalWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(maxHeight, heightSize)
            else -> maxHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        if (orientation == VERTICAL) {
            layoutVertical()
        } else {
            layoutHorizontal()
        }
    }

    private fun layoutVertical() {
        var childTop = 0

        children.forEach { child ->
            if (child.visibility != GONE) {
                val childWidth = child.getMeasuredWidth()
                val childHeight = child.getMeasuredHeight()
                val childLeft = 0

                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
                childTop += childHeight
            }
        }
    }

    private fun layoutHorizontal() {
        var childLeft = 0

        children.forEach { child ->
            if (child.visibility != GONE) {
                val childWidth = child.getMeasuredWidth()
                val childHeight = child.getMeasuredHeight()
                val childTop = 0

                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
                childLeft += childWidth
            }
        }
    }

    class LayoutParams(width: Int, height: Int) : View.LayoutParams(width, height) {
        var weight: Float = 0f
        var gravity: Int = -1
    }

    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }
}
