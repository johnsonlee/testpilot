package io.johnsonlee.testpilot.demo

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.widget.Button
import io.johnsonlee.testpilot.simulator.widget.LinearLayout
import io.johnsonlee.testpilot.simulator.widget.TextView

/**
 * Demo Activity showing basic UI components.
 */
class MainActivity : Activity() {

    private lateinit var counterText: TextView
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build UI programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = View.LayoutParams(
                View.LayoutParams.MATCH_PARENT,
                View.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "TestPilot Demo"
            textSize = 24f
            textColor = Color.BLACK
            layoutParams = View.LayoutParams(
                View.LayoutParams.WRAP_CONTENT,
                View.LayoutParams.WRAP_CONTENT
            )
        }

        counterText = TextView(this).apply {
            text = "Counter: 0"
            textSize = 18f
            textColor = Color.DKGRAY
            layoutParams = View.LayoutParams(
                View.LayoutParams.WRAP_CONTENT,
                View.LayoutParams.WRAP_CONTENT
            )
        }

        val incrementButton = Button(this).apply {
            text = "Increment"
            layoutParams = View.LayoutParams(
                View.LayoutParams.WRAP_CONTENT,
                View.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                counter++
                counterText.text = "Counter: $counter"
                println("Button clicked! Counter: $counter")
            }
        }

        layout.addView(title)
        layout.addView(counterText)
        layout.addView(incrementButton)

        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        println("MainActivity.onStart()")
    }

    override fun onResume() {
        super.onResume()
        println("MainActivity.onResume()")
    }

    override fun onPause() {
        super.onPause()
        println("MainActivity.onPause()")
    }

    override fun onStop() {
        super.onStop()
        println("MainActivity.onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("MainActivity.onDestroy()")
    }
}
