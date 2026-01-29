package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View

/**
 * A view that shows progress, either determinate or indeterminate.
 */
open class ProgressBar(context: Context) : View(context) {

    open var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, max)
        }

    var max: Int = 100
        set(value) {
            field = maxOf(0, value)
            if (progress > field) progress = field
        }

    var isIndeterminate: Boolean = false

    var secondaryProgress: Int = 0
        set(value) {
            field = value.coerceIn(0, max)
        }

    private val paint = Paint()

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val desiredWidth = 200
        val desiredHeight = 48

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val progressWidth = if (max > 0 && !isIndeterminate) {
            (width * progress / max.toFloat()).toInt()
        } else {
            0
        }

        // Draw background
        paint.color = 0xFFE0E0E0.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw progress
        paint.color = 0xFF4CAF50.toInt()
        canvas.drawRect(0f, 0f, progressWidth.toFloat(), height.toFloat(), paint)
    }

    fun incrementProgressBy(diff: Int) {
        progress += diff
    }
}
