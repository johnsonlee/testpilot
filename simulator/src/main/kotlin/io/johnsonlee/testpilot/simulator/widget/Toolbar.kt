package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * A standard toolbar for use within application content.
 */
open class Toolbar(context: Context) : ViewGroup(context) {

    var title: CharSequence? = null
        set(value) {
            field = value
            // In real implementation, update title view
        }

    var subtitle: CharSequence? = null
        set(value) {
            field = value
            // In real implementation, update subtitle view
        }

    var logo: Any? = null  // Drawable
    var navigationIcon: Any? = null  // Drawable

    private var onMenuItemClickListener: OnMenuItemClickListener? = null
    private var navigationOnClickListener: View.OnClickListener? = null

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)

        val widthMode = io.johnsonlee.testpilot.simulator.view.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = io.johnsonlee.testpilot.simulator.view.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = io.johnsonlee.testpilot.simulator.view.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = io.johnsonlee.testpilot.simulator.view.MeasureSpec.getSize(heightMeasureSpec)

        val desiredHeight = 56  // Standard toolbar height in dp

        val width = when (widthMode) {
            io.johnsonlee.testpilot.simulator.view.MeasureSpec.EXACTLY -> widthSize
            else -> widthSize
        }

        val height = when (heightMode) {
            io.johnsonlee.testpilot.simulator.view.MeasureSpec.EXACTLY -> heightSize
            io.johnsonlee.testpilot.simulator.view.MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        // Simplified layout
        var currentLeft = paddingLeft

        children.forEach { child ->
            if (child.visibility != GONE) {
                val childWidth = child.getMeasuredWidth()
                val childHeight = child.getMeasuredHeight()
                val childTop = (height - childHeight) / 2

                child.layout(currentLeft, childTop, currentLeft + childWidth, childTop + childHeight)
                currentLeft += childWidth
            }
        }
    }

    fun setNavigationOnClickListener(listener: View.OnClickListener?) {
        navigationOnClickListener = listener
    }

    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?) {
        onMenuItemClickListener = listener
    }

    fun setTitle(resId: Int) {
        // In real implementation, resolve string resource
    }

    fun setSubtitle(resId: Int) {
        // In real implementation, resolve string resource
    }

    fun setLogo(resId: Int) {
        // In real implementation, set logo drawable
    }

    fun setNavigationIcon(resId: Int) {
        // In real implementation, set navigation icon drawable
    }

    interface OnMenuItemClickListener {
        fun onMenuItemClick(item: Any): Boolean
    }
}
