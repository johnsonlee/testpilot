package io.johnsonlee.testpilot.demo

import io.johnsonlee.testpilot.simulator.activity.ActivityController
import io.johnsonlee.testpilot.simulator.activity.LifecycleEvent
import io.johnsonlee.testpilot.simulator.graphics.DrawCommand
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.window.Window

/**
 * Demo entry point showing Activity lifecycle and rendering.
 */
fun main() {
    println("=== TestPilot Demo ===\n")

    // Set up resources (empty for now)
    val resources = Resources()

    // Set up window
    val window = Window(width = 480, height = 800)

    // Create Activity controller
    val controller = ActivityController.of(
        activity = MainActivity(),
        window = window,
        resources = resources
    )

    // Add lifecycle observer
    controller.get().addLifecycleCallback { event ->
        println("[Lifecycle] $event")
    }

    println("--- Starting Activity Lifecycle ---\n")

    // Drive through lifecycle
    controller
        .create()
        .start()
        .resume()

    println("\n--- Activity is now RESUMED ---\n")

    // Render the UI
    println("--- Rendering UI ---\n")
    val canvas = window.draw()
    canvas.getCommands().forEach { command ->
        when (command) {
            is DrawCommand.Text -> println("  Draw Text: \"${command.text}\" at (${command.x}, ${command.y})")
            is DrawCommand.Rect -> println("  Draw Rect: (${command.left}, ${command.top}) - (${command.right}, ${command.bottom})")
            is DrawCommand.RoundRect -> println("  Draw RoundRect: (${command.left}, ${command.top}) - (${command.right}, ${command.bottom})")
            is DrawCommand.Color -> println("  Fill Color: 0x${command.color.toUInt().toString(16)}")
            is DrawCommand.Translate -> println("  Translate: (${command.dx}, ${command.dy})")
            DrawCommand.Save -> println("  Save")
            DrawCommand.Restore -> println("  Restore")
        }
    }

    println("\n--- Simulating Button Click ---\n")

    // Simulate button click
    val button = window.contentView?.let { root ->
        findViewOfType<io.johnsonlee.testpilot.simulator.widget.Button>(root)
    }
    button?.performClick()

    println("\n--- Stopping Activity ---\n")

    // Drive to stopped state
    controller
        .pause()
        .stop()
        .destroy()

    println("\n=== Demo Complete ===")
}

@Suppress("UNCHECKED_CAST")
private fun <T : io.johnsonlee.testpilot.simulator.view.View> findViewOfType(
    view: io.johnsonlee.testpilot.simulator.view.View,
    type: Class<T>
): T? {
    if (type.isInstance(view)) return view as T
    if (view is io.johnsonlee.testpilot.simulator.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findViewOfType(view.getChildAt(i), type)
            if (found != null) return found
        }
    }
    return null
}

private inline fun <reified T : io.johnsonlee.testpilot.simulator.view.View> findViewOfType(
    view: io.johnsonlee.testpilot.simulator.view.View
): T? = findViewOfType(view, T::class.java)
