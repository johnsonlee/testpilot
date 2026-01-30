package io.johnsonlee.testpilot.simulator

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Drives the lifecycle of a real [android.app.Activity] instance via reflection.
 *
 * The lifecycle methods (`performCreate`, `performStart`, etc.) are package-private in
 * `android.app`, so we use `setAccessible(true)` to invoke them -- the same pattern
 * used by Robolectric's `ActivityController`.
 */
class LayoutlibActivityController(private val activity: android.app.Activity) {

    private val logger = Logger.getLogger(LayoutlibActivityController::class.java.name)

    fun create(savedInstanceState: android.os.Bundle? = null): LayoutlibActivityController {
        invokeLifecycleMethod("performCreate", arrayOf(android.os.Bundle::class.java), arrayOf(savedInstanceState))
        return this
    }

    fun start(): LayoutlibActivityController {
        invokeLifecycleMethod("performStart")
        return this
    }

    fun resume(): LayoutlibActivityController {
        invokeLifecycleMethod("performResume")
        return this
    }

    fun pause(): LayoutlibActivityController {
        invokeLifecycleMethod("performPause")
        return this
    }

    fun stop(): LayoutlibActivityController {
        invokeLifecycleMethod("performStop")
        return this
    }

    fun destroy(): LayoutlibActivityController {
        invokeLifecycleMethod("performDestroy")
        return this
    }

    fun get(): android.app.Activity = activity

    private fun invokeLifecycleMethod(
        name: String,
        paramTypes: Array<Class<*>> = emptyArray(),
        args: Array<Any?> = emptyArray()
    ) {
        try {
            val method = android.app.Activity::class.java.getDeclaredMethod(name, *paramTypes)
            method.isAccessible = true
            method.invoke(activity, *args)
        } catch (e: NoSuchMethodException) {
            logger.log(Level.WARNING, "Lifecycle method not found: $name (API version difference?)")
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to invoke lifecycle method: $name", e)
        }
    }
}
