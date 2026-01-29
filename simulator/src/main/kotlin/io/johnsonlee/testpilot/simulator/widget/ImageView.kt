package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View

/**
 * Displays image resources.
 */
open class ImageView(context: Context) : View(context) {

    var scaleType: ScaleType = ScaleType.FIT_CENTER

    // Simplified: just store a reference to the drawable resource
    private var imageResourceId: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun setImageResource(resId: Int) {
        imageResourceId = resId
        // In a real implementation, this would load the drawable
    }

    fun setImageDrawable(drawable: Any?) {
        // Simplified implementation
    }

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // Default size if no image is set
        val desiredWidth = if (imageWidth > 0) imageWidth else 100
        val desiredHeight = if (imageHeight > 0) imageHeight else 100

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth + paddingLeft + paddingRight, widthSize)
            else -> desiredWidth + paddingLeft + paddingRight
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight + paddingTop + paddingBottom, heightSize)
            else -> desiredHeight + paddingTop + paddingBottom
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        // In a real implementation, this would draw the image
        // For now, just draw a placeholder rectangle
    }

    enum class ScaleType {
        MATRIX,
        FIT_XY,
        FIT_START,
        FIT_CENTER,
        FIT_END,
        CENTER,
        CENTER_CROP,
        CENTER_INSIDE
    }
}
