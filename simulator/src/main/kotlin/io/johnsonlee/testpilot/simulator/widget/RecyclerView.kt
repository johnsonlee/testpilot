package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * A flexible view for providing a limited window into a large data set.
 */
open class RecyclerView(context: Context) : ViewGroup(context) {

    private val viewHolders = mutableListOf<ViewHolder>()
    private var scrollOffsetX: Int = 0
    private var scrollOffsetY: Int = 0
    private val scrollListeners = mutableListOf<OnScrollListener>()

    var adapter: Adapter<*>? = null
        set(value) {
            field?.dataObserver = null
            field = value
            if (value != null) {
                value.dataObserver = { populateItems() }
                if (layoutManager != null) populateItems()
            } else {
                removeAllViews()
                viewHolders.clear()
                scrollOffsetX = 0
                scrollOffsetY = 0
            }
        }

    var layoutManager: LayoutManager? = null
        set(value) {
            field?.recyclerView = null
            field = value
            value?.recyclerView = this
            if (adapter != null) populateItems()
        }

    private fun populateItems() {
        removeAllViews()
        viewHolders.clear()
        scrollOffsetX = 0
        scrollOffsetY = 0
        val a = adapter ?: return
        val lm = layoutManager ?: return
        for (i in 0 until a.getItemCount()) {
            val viewType = a.getItemViewType(i)
            val vh = a.createAndBindViewHolder(this, viewType, i)
            viewHolders.add(vh)
            val itemView = vh.itemView
            if (itemView.layoutParams == null) {
                itemView.layoutParams = lm.generateDefaultLayoutParams()
            }
            addView(itemView)
        }
    }

    fun findViewHolderForAdapterPosition(position: Int): ViewHolder? =
        viewHolders.getOrNull(position)

    fun getChildAdapterPosition(child: View): Int =
        viewHolders.indexOfFirst { it.itemView === child }

    override fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
        layoutManager?.onLayoutChildren(this)
    }

    fun scrollToPosition(position: Int) {
        if (position !in 0 until childCount) return
        val child = getChildAt(position)
        val oldX = scrollOffsetX
        val oldY = scrollOffsetY
        val lm = layoutManager
        if (lm is LinearLayoutManager && lm.orientation == LinearLayoutManager.HORIZONTAL) {
            if (child.left < scrollOffsetX) {
                scrollOffsetX = child.left.coerceIn(0, maxScrollX())
            } else if (child.right > scrollOffsetX + width) {
                scrollOffsetX = (child.right - width).coerceIn(0, maxScrollX())
            }
        } else {
            if (child.top < scrollOffsetY) {
                scrollOffsetY = child.top.coerceIn(0, maxScrollY())
            } else if (child.bottom > scrollOffsetY + height) {
                scrollOffsetY = (child.bottom - height).coerceIn(0, maxScrollY())
            }
        }
        val dx = scrollOffsetX - oldX
        val dy = scrollOffsetY - oldY
        if (dx != 0 || dy != 0) notifyScrollListeners(dx, dy)
    }

    internal fun scrollToPositionWithOffset(position: Int, offset: Int) {
        if (position !in 0 until childCount) return
        val child = getChildAt(position)
        val oldX = scrollOffsetX
        val oldY = scrollOffsetY
        val lm = layoutManager
        if (lm is LinearLayoutManager && lm.orientation == LinearLayoutManager.HORIZONTAL) {
            scrollOffsetX = (child.left - offset).coerceIn(0, maxScrollX())
        } else {
            scrollOffsetY = (child.top - offset).coerceIn(0, maxScrollY())
        }
        val dx = scrollOffsetX - oldX
        val dy = scrollOffsetY - oldY
        if (dx != 0 || dy != 0) notifyScrollListeners(dx, dy)
    }

    fun smoothScrollToPosition(position: Int) {
        scrollToPosition(position) // no animation in simulator
    }

    fun scrollBy(dx: Int, dy: Int) {
        val oldX = scrollOffsetX
        val oldY = scrollOffsetY
        val lm = layoutManager
        if (lm is LinearLayoutManager && lm.orientation == LinearLayoutManager.HORIZONTAL) {
            scrollOffsetX = (scrollOffsetX + dx).coerceIn(0, maxScrollX())
        } else {
            scrollOffsetY = (scrollOffsetY + dy).coerceIn(0, maxScrollY())
        }
        val actualDx = scrollOffsetX - oldX
        val actualDy = scrollOffsetY - oldY
        if (actualDx != 0 || actualDy != 0) notifyScrollListeners(actualDx, actualDy)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val adjusted = event.offsetLocation(scrollOffsetX.toFloat(), scrollOffsetY.toFloat())
        return super.dispatchTouchEvent(adjusted)
    }

    override fun draw(canvas: Canvas) {
        onDraw(canvas)
        canvas.save()
        canvas.translate(-scrollOffsetX.toFloat(), -scrollOffsetY.toFloat())
        drawChildren(canvas)
        canvas.restore()
    }

    fun computeVerticalScrollOffset(): Int = scrollOffsetY
    fun computeVerticalScrollRange(): Int = totalContentHeight()
    fun computeVerticalScrollExtent(): Int = height
    fun computeHorizontalScrollOffset(): Int = scrollOffsetX
    fun computeHorizontalScrollRange(): Int = totalContentWidth()
    fun computeHorizontalScrollExtent(): Int = width

    fun addItemDecoration(decor: ItemDecoration) {}
    fun addItemDecoration(decor: ItemDecoration, index: Int) {}
    fun removeItemDecoration(decor: ItemDecoration) {}
    fun setItemAnimator(animator: ItemAnimator?) {}
    fun addOnScrollListener(listener: OnScrollListener) { scrollListeners.add(listener) }
    fun removeOnScrollListener(listener: OnScrollListener) { scrollListeners.remove(listener) }

    private fun totalContentWidth(): Int {
        var max = 0
        for (i in 0 until childCount) max = maxOf(max, getChildAt(i).right)
        return max
    }

    private fun totalContentHeight(): Int {
        var max = 0
        for (i in 0 until childCount) max = maxOf(max, getChildAt(i).bottom)
        return max
    }

    private fun maxScrollX(): Int = maxOf(0, totalContentWidth() - width)
    private fun maxScrollY(): Int = maxOf(0, totalContentHeight() - height)

    private fun notifyScrollListeners(dx: Int, dy: Int) {
        scrollListeners.forEach { it.onScrolled(this, dx, dy) }
    }

    /**
     * Adapter for RecyclerView.
     */
    abstract class Adapter<VH : ViewHolder> {
        internal var dataObserver: (() -> Unit)? = null

        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, position: Int)
        abstract fun getItemCount(): Int

        open fun getItemViewType(position: Int): Int = 0
        open fun getItemId(position: Int): Long = NO_ID

        fun notifyDataSetChanged() { dataObserver?.invoke() }
        fun notifyItemChanged(position: Int) { dataObserver?.invoke() }
        fun notifyItemInserted(position: Int) { dataObserver?.invoke() }
        fun notifyItemRemoved(position: Int) { dataObserver?.invoke() }
        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) { dataObserver?.invoke() }
        fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) { dataObserver?.invoke() }
        fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) { dataObserver?.invoke() }

        @Suppress("UNCHECKED_CAST")
        internal fun createAndBindViewHolder(parent: ViewGroup, viewType: Int, position: Int): ViewHolder {
            val holder = (this as Adapter<ViewHolder>).onCreateViewHolder(parent, viewType)
            holder.adapterPosition = position
            (this as Adapter<ViewHolder>).onBindViewHolder(holder, position)
            return holder
        }

        companion object {
            const val NO_ID = -1L
        }
    }

    /**
     * ViewHolder for RecyclerView.
     */
    abstract class ViewHolder(val itemView: View) {
        var adapterPosition: Int = NO_POSITION
        var itemId: Long = NO_ID

        companion object {
            const val NO_POSITION = -1
            const val NO_ID = -1L
        }
    }

    /**
     * LayoutManager for RecyclerView.
     */
    abstract class LayoutManager {
        internal var recyclerView: RecyclerView? = null
        abstract fun generateDefaultLayoutParams(): ViewGroup.MarginLayoutParams
        open fun onLayoutChildren(recyclerView: RecyclerView) {}
    }

    /**
     * Linear layout manager for RecyclerView.
     */
    open class LinearLayoutManager(
        context: Context,
        orientation: Int = VERTICAL,
        reverseLayout: Boolean = false
    ) : LayoutManager() {
        var orientation: Int = orientation
        var reverseLayout: Boolean = reverseLayout

        override fun generateDefaultLayoutParams(): ViewGroup.MarginLayoutParams {
            return ViewGroup.MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        fun scrollToPositionWithOffset(position: Int, offset: Int) {
            recyclerView?.scrollToPositionWithOffset(position, offset)
        }

        override fun onLayoutChildren(recyclerView: RecyclerView) {
            val parentWidth = recyclerView.width
            val parentHeight = recyclerView.height
            val widthSpec = MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)

            if (orientation == VERTICAL) {
                var yOffset = 0
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val lp = child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, 0, lp.width)
                    val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, 0, lp.height)
                    child.measure(childWidthSpec, childHeightSpec)
                    child.layout(0, yOffset, child.getMeasuredWidth(), yOffset + child.getMeasuredHeight())
                    yOffset += child.getMeasuredHeight()
                }
            } else {
                var xOffset = 0
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val lp = child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, 0, lp.width)
                    val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, 0, lp.height)
                    child.measure(childWidthSpec, childHeightSpec)
                    child.layout(xOffset, 0, xOffset + child.getMeasuredWidth(), child.getMeasuredHeight())
                    xOffset += child.getMeasuredWidth()
                }
            }
        }

        companion object {
            const val HORIZONTAL = 0
            const val VERTICAL = 1
        }
    }

    /**
     * Grid layout manager for RecyclerView.
     */
    class GridLayoutManager(
        context: Context,
        spanCount: Int,
        orientation: Int = VERTICAL,
        reverseLayout: Boolean = false
    ) : LinearLayoutManager(context, orientation, reverseLayout) {
        var spanCount: Int = spanCount

        override fun generateDefaultLayoutParams(): ViewGroup.MarginLayoutParams {
            return ViewGroup.MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        override fun onLayoutChildren(recyclerView: RecyclerView) {
            val parentWidth = recyclerView.width
            val parentHeight = recyclerView.height

            if (orientation == VERTICAL) {
                val cellWidth = parentWidth / spanCount
                val heightSpec = MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)
                var col = 0
                var yOffset = 0
                var rowHeight = 0
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val lp = child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val childWidthSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY)
                    val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, 0, lp.height)
                    child.measure(childWidthSpec, childHeightSpec)
                    child.layout(col * cellWidth, yOffset, (col + 1) * cellWidth, yOffset + child.getMeasuredHeight())
                    rowHeight = maxOf(rowHeight, child.getMeasuredHeight())
                    col++
                    if (col == spanCount) {
                        col = 0
                        yOffset += rowHeight
                        rowHeight = 0
                    }
                }
            } else {
                val cellHeight = parentHeight / spanCount
                val widthSpec = MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.EXACTLY)
                var row = 0
                var xOffset = 0
                var colWidth = 0
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val lp = child.layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, 0, lp.width)
                    val childHeightSpec = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)
                    child.measure(childWidthSpec, childHeightSpec)
                    child.layout(xOffset, row * cellHeight, xOffset + child.getMeasuredWidth(), (row + 1) * cellHeight)
                    colWidth = maxOf(colWidth, child.getMeasuredWidth())
                    row++
                    if (row == spanCount) {
                        row = 0
                        xOffset += colWidth
                        colWidth = 0
                    }
                }
            }
        }
    }

    /**
     * An ItemDecoration allows the application to add a special drawing and layout offset
     * to specific item views from the adapter's data set.
     */
    abstract class ItemDecoration {
        open fun onDraw(c: Canvas, parent: RecyclerView, state: State) {}
        open fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {}
        open fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {}
    }

    /**
     * This class defines animations that take place on items as changes are made to the adapter.
     */
    open class ItemAnimator

    /**
     * An OnScrollListener can be added to a RecyclerView to receive messages when a scrolling event
     * has occurred on that RecyclerView.
     */
    abstract class OnScrollListener {
        open fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
        open fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {}
    }

    /**
     * Contains useful information about the current RecyclerView state.
     */
    class State {
        var itemCount: Int = 0
    }

    /**
     * Simple rectangle for item offsets.
     */
    class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0)
}
