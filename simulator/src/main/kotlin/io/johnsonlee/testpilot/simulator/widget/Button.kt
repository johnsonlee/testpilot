package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.Paint

/**
 * A user interface element the user can tap or click to perform an action.
 */
open class Button(context: Context) : TextView(context) {

    var buttonBackgroundColor: Int = Color.LTGRAY

    private val backgroundPaint = Paint(
        color = buttonBackgroundColor,
        style = Paint.Style.FILL
    )

    init {
        // Default button styling
        setPadding(24, 16, 24, 16)
        gravity = Gravity.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        // Draw background
        backgroundPaint.color = buttonBackgroundColor
        canvas.drawRoundRect(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            8f, 8f,
            backgroundPaint
        )

        // Draw text
        super.onDraw(canvas)
    }

    override val suggestedMinimumWidth: Int get() = 88
    override val suggestedMinimumHeight: Int get() = 48
}
