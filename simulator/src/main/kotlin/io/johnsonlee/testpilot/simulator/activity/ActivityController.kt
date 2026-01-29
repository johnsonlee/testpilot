package io.johnsonlee.testpilot.simulator.activity

import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.window.Window

/**
 * Controller for driving Activity lifecycle transitions.
 * Similar to Robolectric's ActivityController.
 */
class ActivityController<T : Activity>(
    private val activity: T,
    private val window: Window,
    private val resources: Resources
) {
    init {
        activity.attachWindow(window)
        activity.attachResources(resources)
    }

    /**
     * Drives the Activity to the CREATED state.
     */
    fun create(savedInstanceState: Bundle? = null): ActivityController<T> {
        activity.performCreate(savedInstanceState)
        return this
    }

    /**
     * Drives the Activity to the STARTED state.
     */
    fun start(): ActivityController<T> {
        if (!activity.lifecycleState.isAtLeast(LifecycleState.CREATED)) {
            create()
        }
        activity.performStart()
        return this
    }

    /**
     * Drives the Activity to the RESUMED state.
     */
    fun resume(): ActivityController<T> {
        if (!activity.lifecycleState.isAtLeast(LifecycleState.STARTED)) {
            start()
        }
        activity.performResume()
        return this
    }

    /**
     * Drives the Activity to the PAUSED state.
     */
    fun pause(): ActivityController<T> {
        activity.performPause()
        return this
    }

    /**
     * Drives the Activity to the STOPPED state.
     */
    fun stop(): ActivityController<T> {
        if (activity.lifecycleState == LifecycleState.RESUMED) {
            pause()
        }
        activity.performStop()
        return this
    }

    /**
     * Drives the Activity to the DESTROYED state.
     */
    fun destroy(): ActivityController<T> {
        if (activity.lifecycleState.isAtLeast(LifecycleState.STARTED) &&
            !activity.lifecycleState.isAtLeast(LifecycleState.STOPPED)) {
            stop()
        }
        activity.performDestroy()
        return this
    }

    /**
     * Get the controlled Activity.
     */
    fun get(): T = activity

    companion object {
        /**
         * Create a controller for the given Activity class.
         */
        inline fun <reified T : Activity> of(
            window: Window = Window(),
            resources: Resources = Resources()
        ): ActivityController<T> {
            val activity = T::class.java.getDeclaredConstructor().newInstance()
            return ActivityController(activity, window, resources)
        }

        /**
         * Create a controller for an existing Activity instance.
         */
        fun <T : Activity> of(
            activity: T,
            window: Window = Window(),
            resources: Resources = Resources()
        ): ActivityController<T> {
            return ActivityController(activity, window, resources)
        }
    }
}
