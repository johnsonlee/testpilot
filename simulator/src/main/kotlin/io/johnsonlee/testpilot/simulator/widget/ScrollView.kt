package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.View.LayoutParams.Companion.MATCH_PARENT
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * A view group that allows the view hierarchy placed within it to be scrolled.
 */
class ScrollView(context: Context) : FrameLayout(context) {

    var isFillViewport: Boolean = false

    private var scrollY: Int = 0

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.measure(widthMeasureSpec, heightMeasureSpec)

        if (isFillViewport && childCount > 0) {
            val child = getChildAt(0)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)

            if (heightMode != MeasureSpec.UNSPECIFIED) {
                if (child.getMeasuredHeight() < getMeasuredHeight()) {
                    val childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
                        widthMeasureSpec, 0, child.layoutParams?.width ?: MATCH_PARENT
                    )
                    val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        getMeasuredHeight(), MeasureSpec.EXACTLY
                    )
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
                }
            }
        }
    }

    fun scrollTo(x: Int, y: Int) {
        scrollY = y
    }

    fun scrollBy(x: Int, y: Int) {
        scrollY += y
    }

    fun getScrollY(): Int = scrollY
}
