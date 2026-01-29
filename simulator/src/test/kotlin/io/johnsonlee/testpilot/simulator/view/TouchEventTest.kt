package io.johnsonlee.testpilot.simulator.view

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.widget.Button
import io.johnsonlee.testpilot.simulator.widget.FrameLayout
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TouchEventTest {

    private val context = object : Context() {
        override val resources = Resources()
        override fun getString(resId: Int) = ""
    }

    @Test
    fun `tap should trigger click listener`() {
        var clicked = false
        val button = Button(context).apply {
            id = 1
            isClickable = true
            setOnClickListener { clicked = true }
        }

        val layout = object : FrameLayout(context) {
            init {
                addView(button, LayoutParams(200, 100))
            }
        }

        val window = Window(width = 480, height = 800)
        window.setContentView(layout)

        // Simulate tap at center of button
        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100f, 50f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 100f, 50f)

        window.dispatchTouchEvent(downEvent)
        window.dispatchTouchEvent(upEvent)

        assertThat(clicked).isTrue()
    }

    @Test
    fun `tap outside button should not trigger click`() {
        var clicked = false
        val button = Button(context).apply {
            id = 1
            isClickable = true
            setOnClickListener { clicked = true }
        }

        val layout = object : FrameLayout(context) {
            init {
                addView(button, LayoutParams(200, 100))
            }
        }

        val window = Window(width = 480, height = 800)
        window.setContentView(layout)

        // Simulate tap outside button bounds
        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 300f, 200f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 300f, 200f)

        window.dispatchTouchEvent(downEvent)
        window.dispatchTouchEvent(upEvent)

        assertThat(clicked).isFalse()
    }

    @Test
    fun `touch listener should be called before click`() {
        val events = mutableListOf<String>()
        val button = Button(context).apply {
            id = 1
            isClickable = true
            setOnTouchListener { _, event ->
                events.add("touch:${MotionEvent.actionToString(event.action)}")
                false // Don't consume, let click happen
            }
            setOnClickListener { events.add("click") }
        }

        val layout = object : FrameLayout(context) {
            init {
                addView(button, LayoutParams(200, 100))
            }
        }

        val window = Window(width = 480, height = 800)
        window.setContentView(layout)

        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100f, 50f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 100f, 50f)

        window.dispatchTouchEvent(downEvent)
        window.dispatchTouchEvent(upEvent)

        assertThat(events).containsExactly("touch:ACTION_DOWN", "touch:ACTION_UP", "click")
    }

    @Test
    fun `touch listener consuming event should prevent click`() {
        var clicked = false
        val button = Button(context).apply {
            id = 1
            isClickable = true
            setOnTouchListener { _, _ -> true } // Consume all touch events
            setOnClickListener { clicked = true }
        }

        val layout = object : FrameLayout(context) {
            init {
                addView(button, LayoutParams(200, 100))
            }
        }

        val window = Window(width = 480, height = 800)
        window.setContentView(layout)

        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100f, 50f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 100f, 50f)

        window.dispatchTouchEvent(downEvent)
        window.dispatchTouchEvent(upEvent)

        assertThat(clicked).isFalse()
    }

    @Test
    fun `nested view groups should dispatch to correct child`() {
        var button1Clicked = false
        var button2Clicked = false

        val button1 = Button(context).apply {
            id = 1
            isClickable = true
            setOnClickListener { button1Clicked = true }
        }

        val button2 = Button(context).apply {
            id = 2
            isClickable = true
            setOnClickListener { button2Clicked = true }
        }

        val innerLayout = object : FrameLayout(context) {
            init {
                addView(button2, LayoutParams(100, 50))
            }
        }

        val outerLayout = object : FrameLayout(context) {
            init {
                addView(button1, LayoutParams(100, 50))
                // Position inner layout below button1
                addView(innerLayout, LayoutParams(100, 50))
            }

            override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
                button1.layout(0, 0, 100, 50)
                innerLayout.layout(0, 60, 100, 110)
            }
        }

        val window = Window(width = 480, height = 800)
        window.setContentView(outerLayout)

        // Tap on button2 (inside innerLayout at y=60-110)
        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 50f, 80f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 50f, 80f)

        window.dispatchTouchEvent(downEvent)
        window.dispatchTouchEvent(upEvent)

        assertThat(button1Clicked).isFalse()
        assertThat(button2Clicked).isTrue()
    }

    @Test
    fun `MotionEvent actionToString should return correct names`() {
        assertThat(MotionEvent.actionToString(MotionEvent.ACTION_DOWN)).isEqualTo("ACTION_DOWN")
        assertThat(MotionEvent.actionToString(MotionEvent.ACTION_UP)).isEqualTo("ACTION_UP")
        assertThat(MotionEvent.actionToString(MotionEvent.ACTION_MOVE)).isEqualTo("ACTION_MOVE")
        assertThat(MotionEvent.actionToString(MotionEvent.ACTION_CANCEL)).isEqualTo("ACTION_CANCEL")
    }
}
