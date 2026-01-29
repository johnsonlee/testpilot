package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Paint

/**
 * A FrameLayout with a rounded corner background and shadow.
 */
open class CardView(context: Context) : FrameLayout(context) {

    var cardElevation: Float = 4f
    var cardCornerRadius: Float = 4f
    var cardBackgroundColor: Int = 0xFFFFFFFF.toInt()
    var useCompatPadding: Boolean = false
    var preventCornerOverlap: Boolean = true

    private val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        // Draw card background
        paint.color = cardBackgroundColor
        canvas.drawRoundRect(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            cardCornerRadius, cardCornerRadius,
            paint
        )
    }

    fun setRadius(radius: Float) {
        cardCornerRadius = radius
    }

    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPadding(left, top, right, bottom)
    }

    fun getContentPaddingLeft(): Int = paddingLeft
    fun getContentPaddingTop(): Int = paddingTop
    fun getContentPaddingRight(): Int = paddingRight
    fun getContentPaddingBottom(): Int = paddingBottom
}
