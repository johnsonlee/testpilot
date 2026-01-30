package io.johnsonlee.testpilot.simulator.app

/**
 * A transaction for performing a set of fragment operations.
 */
open class FragmentTransaction internal constructor(
    private val fragmentManager: FragmentManager
) {
    internal sealed class Operation {
        data class Add(val containerId: Int, val fragment: Fragment, val tag: String?) : Operation()
        data class Replace(val containerId: Int, val fragment: Fragment, val tag: String?) : Operation()
        data class Remove(val fragment: Fragment) : Operation()
        data class Show(val fragment: Fragment) : Operation()
        data class Hide(val fragment: Fragment) : Operation()
    }

    internal val operations = mutableListOf<Operation>()
    internal var backStackName: String? = null
    private var addedToBackStack = false

    fun add(containerId: Int, fragment: Fragment, tag: String? = null): FragmentTransaction {
        operations.add(Operation.Add(containerId, fragment, tag))
        return this
    }

    fun add(fragment: Fragment, tag: String?): FragmentTransaction {
        return add(0, fragment, tag)
    }

    fun replace(containerId: Int, fragment: Fragment, tag: String? = null): FragmentTransaction {
        operations.add(Operation.Replace(containerId, fragment, tag))
        return this
    }

    fun remove(fragment: Fragment): FragmentTransaction {
        operations.add(Operation.Remove(fragment))
        return this
    }

    fun show(fragment: Fragment): FragmentTransaction {
        operations.add(Operation.Show(fragment))
        return this
    }

    fun hide(fragment: Fragment): FragmentTransaction {
        operations.add(Operation.Hide(fragment))
        return this
    }

    fun addToBackStack(name: String?): FragmentTransaction {
        addedToBackStack = true
        backStackName = name
        return this
    }

    fun commit(): Int {
        return fragmentManager.executeTransaction(this, addedToBackStack)
    }

    fun commitNow() {
        fragmentManager.executeTransaction(this, addedToBackStack)
    }
}
