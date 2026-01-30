package io.johnsonlee.testpilot.simulator.app

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.LifecycleState
import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * A Fragment represents a reusable portion of your app's UI.
 */
open class Fragment {
    private var _activity: Activity? = null
    private var _view: View? = null
    private var _tag: String? = null
    private var _id: Int = 0
    private var _lifecycleState = LifecycleState.INITIALIZED
    private var _isAdded = false
    private var _isDetached = false
    private var _isHidden = false

    var arguments: Bundle? = null

    val activity: Activity? get() = _activity
    val context: Context? get() = _activity
    val view: View? get() = _view
    val tag: String? get() = _tag
    val id: Int get() = _id
    val lifecycleState: LifecycleState get() = _lifecycleState
    val isAdded: Boolean get() = _isAdded
    val isDetached: Boolean get() = _isDetached
    val isHidden: Boolean get() = _isHidden
    val isVisible: Boolean get() = _isAdded && !_isHidden && _view != null
    val isResumed: Boolean get() = _lifecycleState == LifecycleState.RESUMED

    fun requireActivity(): Activity =
        _activity ?: throw IllegalStateException("Fragment not attached to an activity.")

    fun requireContext(): Context =
        context ?: throw IllegalStateException("Fragment not attached to a context.")

    fun requireView(): View =
        _view ?: throw IllegalStateException("Fragment did not return a View from onCreateView() or this was called before onCreateView().")

    // Lifecycle callbacks
    open fun onAttach(context: Context) {}
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onCreateView(container: ViewGroup?): View? = null
    open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroyView() {}
    open fun onDestroy() {}
    open fun onDetach() {}

    // Internal methods called by FragmentManager

    internal fun performAttach(activity: Activity) {
        _activity = activity
        _isAdded = true
        onAttach(activity)
    }

    internal fun performCreate(savedInstanceState: Bundle?) {
        _lifecycleState = LifecycleState.CREATED
        onCreate(savedInstanceState)
    }

    internal fun performCreateView(container: ViewGroup?): View? {
        _view = onCreateView(container)
        return _view
    }

    internal fun performViewCreated(savedInstanceState: Bundle?) {
        val view = _view
        if (view != null) {
            onViewCreated(view, savedInstanceState)
        }
    }

    internal fun performStart() {
        _lifecycleState = LifecycleState.STARTED
        onStart()
    }

    internal fun performResume() {
        _lifecycleState = LifecycleState.RESUMED
        onResume()
    }

    internal fun performPause() {
        _lifecycleState = LifecycleState.PAUSED
        onPause()
    }

    internal fun performStop() {
        _lifecycleState = LifecycleState.STOPPED
        onStop()
    }

    internal fun performDestroyView() {
        onDestroyView()
        _view = null
    }

    internal fun performDestroy() {
        _lifecycleState = LifecycleState.DESTROYED
        onDestroy()
    }

    internal fun performDetach() {
        _isAdded = false
        onDetach()
        _activity = null
    }

    internal fun setTag(tag: String?) {
        _tag = tag
    }

    internal fun setId(id: Int) {
        _id = id
    }

    internal fun setHidden(hidden: Boolean) {
        _isHidden = hidden
    }
}
