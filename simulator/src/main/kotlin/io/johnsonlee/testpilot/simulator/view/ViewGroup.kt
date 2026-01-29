package io.johnsonlee.testpilot.simulator.view

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas

/**
 * A ViewGroup is a special view that can contain other views (called children).
 * The view group is the base class for layouts and view containers.
 */
abstract class ViewGroup(context: Context) : View(context) {
    protected val children = mutableListOf<View>()
    protected var groupGravity: Int = 0

    open fun setGravity(gravity: Int) {
        groupGravity = gravity
    }

    val childCount: Int get() = children.size

    fun getChildAt(index: Int): View = children[index]

    open fun addView(child: View) {
        addView(child, child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    open fun addView(child: View, params: LayoutParams) {
        child.layoutParams = params
        child.parent = this
        children.add(child)
    }

    open fun removeView(child: View) {
        if (children.remove(child)) {
            child.parent = null
        }
    }

    fun removeViewAt(index: Int) {
        val child = children.removeAt(index)
        child.parent = null
    }

    fun removeAllViews() {
        children.forEach { it.parent = null }
        children.clear()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        drawChildren(canvas)
    }

    protected fun drawChildren(canvas: Canvas) {
        children.forEach { child ->
            if (child.visibility != GONE) {
                canvas.save()
                canvas.translate(child.left.toFloat(), child.top.toFloat())
                child.draw(canvas)
                canvas.restore()
            }
        }
    }

    /**
     * Ask all children to measure themselves.
     */
    protected fun measureChildren(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        children.forEach { child ->
            if (child.visibility != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    protected fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        val childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, 0, lp.width)
        val childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, 0, lp.height)
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    companion object {
        fun getChildMeasureSpec(spec: Int, padding: Int, childDimension: Int): Int {
            val specMode = MeasureSpec.getMode(spec)
            val specSize = MeasureSpec.getSize(spec)
            val size = maxOf(0, specSize - padding)

            return when {
                childDimension >= 0 -> {
                    MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY)
                }
                childDimension == LayoutParams.MATCH_PARENT -> {
                    when (specMode) {
                        MeasureSpec.EXACTLY, MeasureSpec.AT_MOST ->
                            MeasureSpec.makeMeasureSpec(size, specMode)
                        else ->
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    }
                }
                else -> { // WRAP_CONTENT
                    when (specMode) {
                        MeasureSpec.EXACTLY, MeasureSpec.AT_MOST ->
                            MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST)
                        else ->
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    }
                }
            }
        }
    }

    open class MarginLayoutParams(width: Int, height: Int) : LayoutParams(width, height)
}
