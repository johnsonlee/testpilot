package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.app.Fragment
import io.johnsonlee.testpilot.simulator.app.FragmentManager
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Implementation of [ViewPager.PagerAdapter] that uses [Fragment]s to manage each page.
 * Unlike [FragmentPagerAdapter], fragments that are no longer needed are fully destroyed.
 */
abstract class FragmentStatePagerAdapter(
    private val fm: FragmentManager
) : ViewPager.PagerAdapter() {

    private val fragments = mutableListOf<Fragment?>()

    abstract fun getItem(position: Int): Fragment

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        while (fragments.size <= position) fragments.add(null)
        var fragment = fragments[position]
        if (fragment != null) return fragment

        fragment = getItem(position)
        fragments[position] = fragment
        fm.beginTransaction()
            .add(fragment, "android:switcher:${container.id}:$position")
            .commit()
        fragment.view?.let { if (it.parent == null) container.addView(it) }
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val fragment = obj as Fragment
        fragment.view?.let { container.removeView(it) }
        fm.beginTransaction().remove(fragment).commit()
        if (position < fragments.size) fragments[position] = null
    }

    override fun isViewFromObject(view: io.johnsonlee.testpilot.simulator.view.View, obj: Any): Boolean =
        (obj as Fragment).view === view
}
