package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.MeasureSpec
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Displays a vertically-scrollable collection of views.
 */
open class ListView(context: Context) : ViewGroup(context) {

    var adapter: ListAdapter? = null
        set(value) {
            field = value
            // In a real implementation, this would trigger layout
        }

    var dividerHeight: Int = 0
    var choiceMode: Int = CHOICE_MODE_NONE

    private var onItemClickListener: OnItemClickListener? = null
    private var onItemLongClickListener: OnItemLongClickListener? = null

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
        // Simplified layout
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        onItemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        onItemLongClickListener = listener
    }

    fun smoothScrollToPosition(position: Int) {
        // Simplified
    }

    fun setSelection(position: Int) {
        // Simplified
    }

    interface ListAdapter {
        fun getCount(): Int
        fun getItem(position: Int): Any?
        fun getItemId(position: Int): Long
        fun getView(position: Int, convertView: View?, parent: ViewGroup): View
    }

    fun interface OnItemClickListener {
        fun onItemClick(parent: ViewGroup, view: View, position: Int, id: Long)
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(parent: ViewGroup, view: View, position: Int, id: Long): Boolean
    }

    companion object {
        const val CHOICE_MODE_NONE = 0
        const val CHOICE_MODE_SINGLE = 1
        const val CHOICE_MODE_MULTIPLE = 2
    }
}
