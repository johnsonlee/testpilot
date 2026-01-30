package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.app.Fragment
import io.johnsonlee.testpilot.simulator.app.FragmentManager
import io.johnsonlee.testpilot.simulator.view.ViewGroup

/**
 * Implementation of [ViewPager.PagerAdapter] that uses [Fragment]s to manage each page.
 * Fragments that are no longer needed are hidden rather than destroyed.
 */
abstract class FragmentPagerAdapter(
    private val fm: FragmentManager
) : ViewPager.PagerAdapter() {

    abstract fun getItem(position: Int): Fragment

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val tag = makeFragmentName(container.id, position)
        var fragment = fm.findFragmentByTag(tag)
        if (fragment != null) {
            fm.beginTransaction().show(fragment).commit()
        } else {
            fragment = getItem(position)
            fm.beginTransaction().add(fragment, tag).commit()
        }
        fragment.view?.let { if (it.parent == null) container.addView(it) }
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val fragment = obj as Fragment
        fragment.view?.let { container.removeView(it) }
        fm.beginTransaction().hide(fragment).commit()
    }

    override fun isViewFromObject(view: io.johnsonlee.testpilot.simulator.view.View, obj: Any): Boolean =
        (obj as Fragment).view === view

    private fun makeFragmentName(containerId: Int, position: Int): String =
        "android:switcher:$containerId:$position"
}
