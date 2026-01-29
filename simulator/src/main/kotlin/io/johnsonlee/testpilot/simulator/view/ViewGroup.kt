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

    // Touch target - the child that is currently receiving touch events
    private var touchTarget: View? = null

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
     * Pass the touch screen motion event down to the target view, or this view if it is the target.
     *
     * @param event The motion event to be dispatched.
     * @return True if the event was handled, false otherwise.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        var handled = false

        val action = event.action

        // Handle an initial down event
        if (action == MotionEvent.ACTION_DOWN) {
            // Clear any previous touch target
            touchTarget = null
        }

        // Check for interception
        val intercepted = onInterceptTouchEvent(event)

        // If not intercepted, try to dispatch to children
        if (!intercepted) {
            if (action == MotionEvent.ACTION_DOWN) {
                // Look for a child that can handle the event
                val target = findTouchTarget(event)
                if (target != null) {
                    touchTarget = target
                }
            }
        }

        // Dispatch to touch target if we have one
        val target = touchTarget
        if (target != null && !intercepted) {
            // Transform the event coordinates to child's local space
            val offsetX = target.left.toFloat()
            val offsetY = target.top.toFloat()
            val transformedEvent = event.offsetLocation(-offsetX, -offsetY)
            handled = target.dispatchTouchEvent(transformedEvent)
        }

        // If no child handled it, or we intercepted, handle it ourselves
        if (!handled) {
            handled = super.dispatchTouchEvent(event)
        }

        // Clear the touch target on up or cancel
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchTarget = null
        }

        return handled
    }

    /**
     * Implement this method to intercept all touch screen motion events.
     * This allows you to watch events as they are dispatched to your children,
     * and take ownership of the current gesture at any point.
     *
     * @param event The motion event being dispatched down the hierarchy.
     * @return True if you want to intercept the motion event.
     */
    open fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * Find the child view that should receive the touch event.
     * Iterates children in reverse order (top-most child first).
     */
    private fun findTouchTarget(event: MotionEvent): View? {
        val x = event.x
        val y = event.y

        // Iterate in reverse order so top-most children are checked first
        for (i in children.lastIndex downTo 0) {
            val child = children[i]

            // Skip invisible or gone views
            if (child.visibility != VISIBLE) {
                continue
            }

            // Check if the touch is within the child's bounds
            if (isTransformedTouchPointInView(x, y, child)) {
                return child
            }
        }

        return null
    }

    /**
     * Check if the given point (in this ViewGroup's coordinates) is within the child's bounds.
     */
    private fun isTransformedTouchPointInView(x: Float, y: Float, child: View): Boolean {
        val localX = x - child.left
        val localY = y - child.top
        return localX >= 0 && localX < child.width && localY >= 0 && localY < child.height
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
