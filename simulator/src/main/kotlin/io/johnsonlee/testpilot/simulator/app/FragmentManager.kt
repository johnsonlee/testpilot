package io.johnsonlee.testpilot.simulator.app

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.LifecycleState
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Manages fragments associated with an Activity.
 */
class FragmentManager internal constructor(
    private val activity: Activity
) {
    private val fragments = mutableListOf<Fragment>()
    private val backStack = mutableListOf<BackStackEntry>()
    private var transactionId = 0

    internal class BackStackEntry(
        val name: String?,
        val operations: List<FragmentTransaction.Operation>,
        val removedFragments: List<Pair<Fragment, Int>>
    )

    fun beginTransaction(): FragmentTransaction = FragmentTransaction(this)

    fun findFragmentById(id: Int): Fragment? = fragments.find { it.id == id }

    fun findFragmentByTag(tag: String): Fragment? = fragments.find { it.tag == tag }

    fun getFragments(): List<Fragment> = fragments.toList()

    fun getBackStackEntryCount(): Int = backStack.size

    fun popBackStack(): Boolean {
        if (backStack.isEmpty()) return false

        val entry = backStack.removeAt(backStack.lastIndex)

        for (op in entry.operations.asReversed()) {
            when (op) {
                is FragmentTransaction.Operation.Add -> {
                    removeFragment(op.fragment, op.containerId)
                }
                is FragmentTransaction.Operation.Replace -> {
                    removeFragment(op.fragment, op.containerId)
                    for ((removedFragment, containerId) in entry.removedFragments) {
                        addFragment(removedFragment, containerId, removedFragment.tag)
                    }
                }
                is FragmentTransaction.Operation.Remove -> {
                    val pair = entry.removedFragments.find { it.first == op.fragment }
                    if (pair != null) {
                        addFragment(op.fragment, pair.second, op.fragment.tag)
                    }
                }
                is FragmentTransaction.Operation.Show -> {
                    hideFragment(op.fragment)
                }
                is FragmentTransaction.Operation.Hide -> {
                    showFragment(op.fragment)
                }
            }
        }
        return true
    }

    internal fun executeTransaction(transaction: FragmentTransaction, addToBackStack: Boolean): Int {
        val id = ++transactionId
        val removedFragments = mutableListOf<Pair<Fragment, Int>>()

        for (op in transaction.operations) {
            when (op) {
                is FragmentTransaction.Operation.Add -> {
                    addFragment(op.fragment, op.containerId, op.tag)
                }
                is FragmentTransaction.Operation.Replace -> {
                    val existing = fragments.filter { it.id == op.containerId && it != op.fragment }
                    for (f in existing) {
                        removedFragments.add(f to op.containerId)
                        removeFragment(f, op.containerId)
                    }
                    addFragment(op.fragment, op.containerId, op.tag)
                }
                is FragmentTransaction.Operation.Remove -> {
                    removedFragments.add(op.fragment to op.fragment.id)
                    removeFragment(op.fragment, op.fragment.id)
                }
                is FragmentTransaction.Operation.Show -> {
                    showFragment(op.fragment)
                }
                is FragmentTransaction.Operation.Hide -> {
                    hideFragment(op.fragment)
                }
            }
        }

        if (addToBackStack) {
            backStack.add(BackStackEntry(transaction.backStackName, transaction.operations, removedFragments))
        }

        return id
    }

    private fun addFragment(fragment: Fragment, containerId: Int, tag: String?) {
        if (containerId != 0) {
            fragment.setId(containerId)
        }
        if (tag != null) {
            fragment.setTag(tag)
        }

        fragments.add(fragment)

        // Attach and advance lifecycle to match host Activity state
        fragment.performAttach(activity)
        fragment.performCreate(null)

        if (containerId != 0) {
            val container = activity.findViewById<ViewGroup>(containerId)
            val view = fragment.performCreateView(container)
            if (view != null && container != null) {
                container.addView(view)
            }
            fragment.performViewCreated(null)
        } else {
            fragment.performCreateView(null)
            fragment.performViewCreated(null)
        }

        if (activity.lifecycleState.isAtLeast(LifecycleState.STARTED)) {
            fragment.performStart()
        }
        if (activity.lifecycleState.isAtLeast(LifecycleState.RESUMED)) {
            fragment.performResume()
        }
    }

    private fun removeFragment(fragment: Fragment, containerId: Int) {
        // Tear down lifecycle in reverse
        if (fragment.lifecycleState.isAtLeast(LifecycleState.RESUMED)) {
            fragment.performPause()
        }
        if (fragment.lifecycleState.isAtLeast(LifecycleState.STARTED)) {
            fragment.performStop()
        }

        // Remove view from container
        if (containerId != 0) {
            val container = activity.findViewById<ViewGroup>(containerId)
            val view = fragment.view
            if (view != null && container != null) {
                container.removeView(view)
            }
        }

        fragment.performDestroyView()
        fragment.performDestroy()
        fragment.performDetach()

        fragments.remove(fragment)
    }

    private fun showFragment(fragment: Fragment) {
        fragment.setHidden(false)
        fragment.view?.visibility = View.VISIBLE
    }

    private fun hideFragment(fragment: Fragment) {
        fragment.setHidden(true)
        fragment.view?.visibility = View.GONE
    }

    // Called by Activity lifecycle methods

    internal fun dispatchStart() {
        fragments.filter { it.isAdded && !it.isDetached }.forEach { fragment ->
            if (!fragment.lifecycleState.isAtLeast(LifecycleState.STARTED)) {
                fragment.performStart()
            }
        }
    }

    internal fun dispatchResume() {
        fragments.filter { it.isAdded && !it.isDetached }.forEach { fragment ->
            if (!fragment.lifecycleState.isAtLeast(LifecycleState.RESUMED)) {
                fragment.performResume()
            }
        }
    }

    internal fun dispatchPause() {
        fragments.filter { it.isAdded && !it.isDetached }.forEach { fragment ->
            if (fragment.lifecycleState.isAtLeast(LifecycleState.RESUMED)) {
                fragment.performPause()
            }
        }
    }

    internal fun dispatchStop() {
        fragments.filter { it.isAdded && !it.isDetached }.forEach { fragment ->
            if (fragment.lifecycleState.isAtLeast(LifecycleState.STARTED)) {
                fragment.performStop()
            }
        }
    }

    internal fun dispatchDestroy() {
        fragments.toList().forEach { fragment ->
            if (fragment.lifecycleState.isAtLeast(LifecycleState.CREATED)) {
                fragment.performDestroyView()
                fragment.performDestroy()
                fragment.performDetach()
            }
        }
        fragments.clear()
    }
}
