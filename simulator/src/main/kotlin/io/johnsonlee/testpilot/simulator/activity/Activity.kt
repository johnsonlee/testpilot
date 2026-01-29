package io.johnsonlee.testpilot.simulator.activity

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.LayoutInflater
import io.johnsonlee.testpilot.simulator.window.Window

/**
 * An Activity is a single, focused thing that the user can do.
 * Almost all activities interact with the user through a window.
 */
abstract class Activity : Context() {
    private var _lifecycleState = LifecycleState.INITIALIZED
    val lifecycleState: LifecycleState get() = _lifecycleState

    private var _window: Window? = null
    val window: Window get() = _window ?: throw IllegalStateException("Window not initialized")

    private var _resources: Resources? = null
    override val resources: Resources get() = _resources ?: throw IllegalStateException("Resources not initialized")

    private val lifecycleCallbacks = mutableListOf<(LifecycleEvent) -> Unit>()

    /**
     * Called when the activity is starting.
     */
    protected open fun onCreate(savedInstanceState: Bundle?) {}

    /**
     * Called after onCreate — or after onRestart when the activity had been stopped.
     */
    protected open fun onStart() {}

    /**
     * Called when the activity will start interacting with the user.
     */
    protected open fun onResume() {}

    /**
     * Called when the activity is going into the background.
     */
    protected open fun onPause() {}

    /**
     * Called when the activity is no longer visible to the user.
     */
    protected open fun onStop() {}

    /**
     * Called before the activity is destroyed.
     */
    protected open fun onDestroy() {}

    /**
     * Set the activity content to the given view.
     */
    open fun setContentView(view: View) {
        window.setContentView(view)
    }

    /**
     * Set the activity content from a layout resource.
     */
    open fun setContentView(layoutResId: Int) {
        val inflater = LayoutInflater(this)
        val view = inflater.inflate(layoutResId, null)
        setContentView(view)
    }

    /**
     * Find a view by its ID.
     */
    open fun <T : View> findViewById(id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return window.contentView?.findViewByIdRecursive(id) as? T
    }

    override fun getString(resId: Int): String = resources.getString(resId)

    // Common Activity methods that might be overridden
    open fun finish() {}
    open fun onBackPressed() {}
    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Any?) {}
    open fun getIntent(): Any? = null
    open fun startActivity(intent: Any?) {}
    open fun startActivityForResult(intent: Any?, requestCode: Int) {}

    /**
     * Add a lifecycle callback.
     */
    fun addLifecycleCallback(callback: (LifecycleEvent) -> Unit) {
        lifecycleCallbacks.add(callback)
    }

    // Internal methods called by ActivityController

    internal fun performCreate(savedInstanceState: Bundle?) {
        _lifecycleState = LifecycleState.CREATED
        notifyCallbacks(LifecycleEvent.ON_CREATE)
        onCreate(savedInstanceState)
    }

    internal fun performStart() {
        _lifecycleState = LifecycleState.STARTED
        notifyCallbacks(LifecycleEvent.ON_START)
        onStart()
    }

    internal fun performResume() {
        _lifecycleState = LifecycleState.RESUMED
        notifyCallbacks(LifecycleEvent.ON_RESUME)
        onResume()
    }

    internal fun performPause() {
        _lifecycleState = LifecycleState.PAUSED
        notifyCallbacks(LifecycleEvent.ON_PAUSE)
        onPause()
    }

    internal fun performStop() {
        _lifecycleState = LifecycleState.STOPPED
        notifyCallbacks(LifecycleEvent.ON_STOP)
        onStop()
    }

    internal fun performDestroy() {
        _lifecycleState = LifecycleState.DESTROYED
        notifyCallbacks(LifecycleEvent.ON_DESTROY)
        onDestroy()
    }

    internal fun attachWindow(window: Window) {
        _window = window
    }

    internal fun attachResources(resources: Resources) {
        _resources = resources
    }

    private fun notifyCallbacks(event: LifecycleEvent) {
        lifecycleCallbacks.forEach { it(event) }
    }

    private fun View.findViewByIdRecursive(id: Int): View? {
        if (this.id == id) return this
        if (this is io.johnsonlee.testpilot.simulator.view.ViewGroup) {
            for (i in 0 until childCount) {
                val found = getChildAt(i).findViewByIdRecursive(id)
                if (found != null) return found
            }
        }
        return null
    }
}
