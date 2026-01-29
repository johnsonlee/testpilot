package io.johnsonlee.testpilot.simulator.view

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas

/**
 * Base class for all UI components.
 * A View occupies a rectangular area on the screen and is responsible for drawing and event handling.
 */
open class View(val context: Context) {
    var id: Int = NO_ID
    var parent: ViewGroup? = null

    // Layout parameters
    var layoutParams: LayoutParams? = null

    // Dimensions
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0

    private var measuredWidth: Int = 0
    private var measuredHeight: Int = 0

    // Visibility
    var visibility: Int = VISIBLE

    // Appearance
    var alpha: Float = 1.0f
    var backgroundColor: Int = 0

    // Interaction state
    var isEnabled: Boolean = true
    var isClickable: Boolean = false
    var isFocusable: Boolean = false

    // Padding
    var paddingLeft: Int = 0
    var paddingTop: Int = 0
    var paddingRight: Int = 0
    var paddingBottom: Int = 0

    // Click listener
    private var onClickListener: OnClickListener? = null

    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun getMeasuredWidth(): Int = measuredWidth
    fun getMeasuredHeight(): Int = measuredHeight

    protected fun setMeasuredDimension(measuredWidth: Int, measuredHeight: Int) {
        this.measuredWidth = measuredWidth
        this.measuredHeight = measuredHeight
    }

    /**
     * Measure the view and its content to determine the measured width and height.
     */
    open fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(suggestedMinimumWidth, widthSize)
            else -> suggestedMinimumWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(suggestedMinimumHeight, heightSize)
            else -> suggestedMinimumHeight
        }

        setMeasuredDimension(width, height)
    }

    protected open val suggestedMinimumWidth: Int = 0
    protected open val suggestedMinimumHeight: Int = 0

    /**
     * Assign a size and position to the view.
     */
    open fun layout(l: Int, t: Int, r: Int, b: Int) {
        left = l
        top = t
        right = r
        bottom = b
        onLayout(l, t, r, b)
    }

    protected open fun onLayout(l: Int, t: Int, r: Int, b: Int) {}

    /**
     * Draw the view on the given canvas.
     */
    open fun draw(canvas: Canvas) {
        onDraw(canvas)
    }

    protected open fun onDraw(canvas: Canvas) {}

    /**
     * Handle touch/click events.
     */
    open fun performClick(): Boolean {
        onClickListener?.onClick(this)
        return onClickListener != null
    }

    fun setOnClickListener(listener: OnClickListener?) {
        this.onClickListener = listener
    }

    fun setOnClickListener(listener: (View) -> Unit) {
        this.onClickListener = OnClickListener { listener(it) }
    }

    fun interface OnClickListener {
        fun onClick(v: View)
    }

    /**
     * Set padding for all edges.
     */
    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        paddingLeft = left
        paddingTop = top
        paddingRight = right
        paddingBottom = bottom
    }


    /**
     * LayoutParams are used by views to tell their parents how they want to be laid out.
     */
    open class LayoutParams(
        var width: Int,
        var height: Int
    ) {
        var leftMargin: Int = 0
        var topMargin: Int = 0
        var rightMargin: Int = 0
        var bottomMargin: Int = 0

        fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
            leftMargin = left
            topMargin = top
            rightMargin = right
            bottomMargin = bottom
        }

        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }

    companion object {
        const val NO_ID = -1
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}

/**
 * Utility class for creating measure specifications.
 */
object MeasureSpec {
    private const val MODE_SHIFT = 30
    private const val MODE_MASK = 0x3 shl MODE_SHIFT

    const val UNSPECIFIED = 0 shl MODE_SHIFT
    const val EXACTLY = 1 shl MODE_SHIFT
    const val AT_MOST = 2 shl MODE_SHIFT

    fun makeMeasureSpec(size: Int, mode: Int): Int {
        return (size and MODE_MASK.inv()) or (mode and MODE_MASK)
    }

    fun getMode(measureSpec: Int): Int {
        return measureSpec and MODE_MASK
    }

    fun getSize(measureSpec: Int): Int {
        return measureSpec and MODE_MASK.inv()
    }
}
