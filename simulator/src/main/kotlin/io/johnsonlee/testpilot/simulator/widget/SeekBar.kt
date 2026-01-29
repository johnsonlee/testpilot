package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * A SeekBar is an extension of ProgressBar that adds a draggable thumb.
 */
open class SeekBar(context: Context) : ProgressBar(context) {

    private var onSeekBarChangeListener: OnSeekBarChangeListener? = null

    override var progress: Int
        get() = super.progress
        set(value) {
            val oldProgress = super.progress
            super.progress = value
            if (oldProgress != value) {
                onSeekBarChangeListener?.onProgressChanged(this, value, false)
            }
        }

    fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener?) {
        onSeekBarChangeListener = listener
    }

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: SeekBar)
        fun onStopTrackingTouch(seekBar: SeekBar)
    }
}
