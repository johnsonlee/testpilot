package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Layout manager that allows the user to flip left and right through pages of data.
 */
open class ViewPager(context: Context) : ViewGroup(context) {

    var adapter: PagerAdapter? = null
        set(value) {
            field = value
            // In a real implementation, this would trigger layout
        }

    var currentItem: Int = 0
        set(value) {
            if (field != value) {
                field = value
                onPageChangeListener?.onPageSelected(value)
            }
        }

    var offscreenPageLimit: Int = 1

    private var onPageChangeListener: OnPageChangeListener? = null

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            else -> widthSize
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            else -> heightSize
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        // Simplified layout - just position current page
        children.forEachIndexed { index, child ->
            if (child.visibility != GONE) {
                val childWidth = child.getMeasuredWidth()
                val childHeight = child.getMeasuredHeight()
                val childLeft = (index - currentItem) * width
                child.layout(childLeft, 0, childLeft + childWidth, childHeight)
            }
        }
    }

    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        onPageChangeListener = listener
    }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        currentItem = item
    }

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int)
        fun onPageSelected(position: Int)
        fun onPageScrollStateChanged(state: Int)
    }

    abstract class PagerAdapter {
        abstract fun getCount(): Int
        abstract fun instantiateItem(container: ViewGroup, position: Int): Any
        abstract fun destroyItem(container: ViewGroup, position: Int, obj: Any)
        abstract fun isViewFromObject(view: View, obj: Any): Boolean

        open fun getPageTitle(position: Int): CharSequence? = null

        fun notifyDataSetChanged() {}
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }
}
