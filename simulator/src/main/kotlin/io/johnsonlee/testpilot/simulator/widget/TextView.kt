package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View

/**
 * A user interface element that displays text to the user.
 */
open class TextView(context: Context) : View(context) {

    var text: String = ""
        set(value) {
            field = value
            requestLayout()
        }

    var textSize: Float = 14f
        set(value) {
            field = value
            paint.textSize = value
            requestLayout()
        }

    var textColor: Int = Color.BLACK
        set(value) {
            field = value
            paint.color = value
        }

    var gravity: Int = Gravity.START or Gravity.TOP

    var maxLines: Int = Int.MAX_VALUE
        set(value) {
            field = value
            requestLayout()
        }

    private var typeface: Any? = null
    private var typefaceStyle: Int = 0

    protected val paint = Paint(
        color = textColor,
        textSize = textSize
    )

    private var textWidth: Int = 0
    private var textHeight: Int = 0

    fun setTypeface(tf: Any?, style: Int) {
        typeface = tf
        typefaceStyle = style
    }

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Simplified text measurement
        textWidth = (text.length * textSize * 0.6f).toInt()
        textHeight = (textSize * 1.2f).toInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(textWidth + paddingLeft + paddingRight, widthSize)
            else -> textWidth + paddingLeft + paddingRight
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(textHeight + paddingTop + paddingBottom, heightSize)
            else -> textHeight + paddingTop + paddingBottom
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return

        paint.color = textColor
        paint.textSize = textSize

        val x = when {
            (gravity and Gravity.CENTER_HORIZONTAL) != 0 -> (width - textWidth) / 2f
            (gravity and Gravity.END) != 0 -> (width - textWidth - paddingRight).toFloat()
            else -> paddingLeft.toFloat()
        }

        val y = when {
            (gravity and Gravity.CENTER_VERTICAL) != 0 -> (height + textHeight) / 2f - textSize * 0.2f
            (gravity and Gravity.BOTTOM) != 0 -> (height - paddingBottom).toFloat()
            else -> (paddingTop + textHeight).toFloat()
        }

        canvas.drawText(text, x, y, paint)
    }

    protected fun requestLayout() {
        // In a real implementation, this would trigger a re-layout
    }

    object Gravity {
        const val START = 0x00800003
        const val END = 0x00800005
        const val TOP = 0x30
        const val BOTTOM = 0x50
        const val CENTER = 0x11
        const val CENTER_HORIZONTAL = 0x01
        const val CENTER_VERTICAL = 0x10
    }
}
