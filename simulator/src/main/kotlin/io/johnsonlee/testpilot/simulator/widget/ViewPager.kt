package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Layout manager that allows the user to flip left and right through pages of data.
 */
open class ViewPager(context: Context) : ViewGroup(context) {

    private var scrollOffsetX: Int = 0
    private var pageKeys: Array<Any?> = emptyArray()
    private val pageChangeListeners = mutableListOf<OnPageChangeListener>()
    private var legacyPageChangeListener: OnPageChangeListener? = null

    var adapter: PagerAdapter? = null
        set(value) {
            field?.dataObserver = null
            field = value
            if (value != null) {
                value.dataObserver = { populatePages() }
                populatePages()
            } else {
                removeAllViews()
                pageKeys = emptyArray()
                currentItem = 0
                scrollOffsetX = 0
            }
        }

    var currentItem: Int = 0
        set(value) {
            val a = adapter
            if (a != null && value !in 0 until a.getCount()) return
            if (field != value) {
                field = value
                scrollOffsetX = value * width
                updatePageWindow()
                notifyPageSelected(value)
            }
        }

    var offscreenPageLimit: Int = 1

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        currentItem = item
    }

    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        legacyPageChangeListener = listener
    }

    fun addOnPageChangeListener(listener: OnPageChangeListener) {
        pageChangeListeners.add(listener)
    }

    fun removeOnPageChangeListener(listener: OnPageChangeListener) {
        pageChangeListeners.remove(listener)
    }

    fun setPageTransformer(reverseDrawingOrder: Boolean, transformer: PageTransformer?) {
        // No-op in simulator
    }

    private fun notifyPageSelected(position: Int) {
        legacyPageChangeListener?.onPageSelected(position)
        pageChangeListeners.forEach { it.onPageSelected(position) }
    }

    private fun populatePages() {
        val a = adapter ?: return
        // Destroy all existing pages
        destroyAllPages(a)
        val count = a.getCount()
        pageKeys = Array(count) { null }
        currentItem = 0
        scrollOffsetX = 0
        // Instantiate pages within the offscreen window
        updatePageWindow()
    }

    private fun destroyAllPages(a: PagerAdapter) {
        for (i in pageKeys.indices) {
            val key = pageKeys[i]
            if (key != null) {
                a.destroyItem(this, i, key)
                pageKeys[i] = null
            }
        }
        removeAllViews()
    }

    private fun updatePageWindow() {
        val a = adapter ?: return
        val count = a.getCount()
        if (count == 0) return
        val start = maxOf(0, currentItem - offscreenPageLimit)
        val end = minOf(count - 1, currentItem + offscreenPageLimit)

        // Destroy pages outside the window
        for (i in 0 until count) {
            val key = pageKeys[i]
            if (key != null && (i < start || i > end)) {
                a.destroyItem(this, i, key)
                pageKeys[i] = null
            }
        }

        // Instantiate pages inside the window
        for (i in start..end) {
            if (pageKeys[i] == null) {
                val key = a.instantiateItem(this, i)
                pageKeys[i] = key
            }
        }
    }

    private fun findPageIndex(child: View): Int? {
        val a = adapter ?: return null
        for (i in pageKeys.indices) {
            val key = pageKeys[i] ?: continue
            if (a.isViewFromObject(child, key)) return i
        }
        return null
    }

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        children.forEach { it.measure(childW, childH) }
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        scrollOffsetX = currentItem * width
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val pageIndex = findPageIndex(child) ?: continue
            val childLeft = pageIndex * width
            child.layout(childLeft, 0, childLeft + child.getMeasuredWidth(), child.getMeasuredHeight())
        }
    }

    override fun draw(canvas: Canvas) {
        onDraw(canvas)
        canvas.save()
        canvas.translate(-scrollOffsetX.toFloat(), 0f)
        drawChildren(canvas)
        canvas.restore()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val adjusted = event.offsetLocation(scrollOffsetX.toFloat(), 0f)
        return super.dispatchTouchEvent(adjusted)
    }

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int)
        fun onPageSelected(position: Int)
        fun onPageScrollStateChanged(state: Int)
    }

    open class SimpleOnPageChangeListener : OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        override fun onPageSelected(position: Int) {}
        override fun onPageScrollStateChanged(state: Int) {}
    }

    interface PageTransformer {
        fun transformPage(page: View, position: Float)
    }

    abstract class PagerAdapter {
        internal var dataObserver: (() -> Unit)? = null

        abstract fun getCount(): Int
        abstract fun instantiateItem(container: ViewGroup, position: Int): Any
        abstract fun destroyItem(container: ViewGroup, position: Int, obj: Any)
        abstract fun isViewFromObject(view: View, obj: Any): Boolean

        open fun getPageTitle(position: Int): CharSequence? = null

        fun notifyDataSetChanged() { dataObserver?.invoke() }
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }
}
