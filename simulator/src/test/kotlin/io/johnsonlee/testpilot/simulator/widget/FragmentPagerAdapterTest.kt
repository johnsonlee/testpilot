package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.ActivityController
import io.johnsonlee.testpilot.simulator.app.Fragment
import io.johnsonlee.testpilot.simulator.app.FragmentManager
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.graphics.toImage
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FragmentPagerAdapterTest {

    companion object {
        const val PAGER_ID = 42
    }

    /** A View that fills itself with a solid color. */
    private class ColorView(
        context: io.johnsonlee.testpilot.simulator.content.Context,
        private val fillColor: Int
    ) : View(context) {
        override fun draw(canvas: Canvas) {
            canvas.drawRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint(color = fillColor, style = Paint.Style.FILL)
            )
        }
    }

    /** Fragment that creates a ColorView. */
    private class ColorFragment(private val color: Int) : Fragment() {
        override fun onCreateView(container: ViewGroup?): View {
            return ColorView(requireActivity(), color)
        }
    }

    /** Activity that hosts a ViewPager. */
    private class ViewPagerActivity : Activity() {
        lateinit var viewPager: ViewPager

        override fun onCreate(savedInstanceState: Bundle?) {
            viewPager = ViewPager(this).apply { id = PAGER_ID }
            setContentView(viewPager)
        }
    }

    private fun createTestAdapter(
        fm: FragmentManager,
        colors: List<Int>
    ): FragmentPagerAdapter {
        return object : FragmentPagerAdapter(fm) {
            override fun getItem(position: Int) = ColorFragment(colors[position])
            override fun getCount() = colors.size
        }
    }

    private fun createStateTestAdapter(
        fm: FragmentManager,
        colors: List<Int>
    ): FragmentStatePagerAdapter {
        return object : FragmentStatePagerAdapter(fm) {
            override fun getItem(position: Int) = ColorFragment(colors[position])
            override fun getCount() = colors.size
        }
    }

    private fun createController(): ActivityController<ViewPagerActivity> {
        return ActivityController.of(ViewPagerActivity(), Window(100, 100), Resources())
    }

    // -- FragmentPagerAdapter tests --

    @Test
    fun `FragmentPagerAdapter creates fragments via getItem`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        // offscreenPageLimit=1, so pages 0,1 should be instantiated
        val fragments = fm.getFragments()
        assertThat(fragments).hasSize(2)
    }

    @Test
    fun `fragment views become children of ViewPager`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        assertThat(activity.viewPager.childCount).isEqualTo(2)
    }

    @Test
    fun `fragments receive full lifecycle when activity is resumed`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        val fragments = fm.getFragments()
        fragments.forEach { fragment ->
            assertThat(fragment.isAdded).isTrue()
            assertThat(fragment.isResumed).isTrue()
        }
    }

    @Test
    fun `fragments are registered in FragmentManager`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN)

        activity.viewPager.adapter = createTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        // Fragments should be findable by tag
        assertThat(fm.findFragmentByTag("android:switcher:$PAGER_ID:0")).isNotNull
        assertThat(fm.findFragmentByTag("android:switcher:$PAGER_ID:1")).isNotNull
    }

    @Test
    fun `navigating pages shows correct fragment pixel color`() {
        val window = Window(100, 100)
        val controller = ActivityController.of(ViewPagerActivity(), window, Resources())
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createTestAdapter(fm, colors)
        window.measureAndLayout()

        // Page 0 = RED
        var image = window.draw().toImage()
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.RED)

        // Navigate to page 1 = GREEN
        activity.viewPager.currentItem = 1
        window.measureAndLayout()
        image = window.draw().toImage()
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.GREEN)

        // Navigate to page 2 = BLUE
        activity.viewPager.currentItem = 2
        window.measureAndLayout()
        image = window.draw().toImage()
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `notifyDataSetChanged repopulates fragment pages`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        var colors = listOf(Color.RED, Color.GREEN)
        val adapter = object : FragmentPagerAdapter(fm) {
            override fun getItem(position: Int) = ColorFragment(colors[position])
            override fun getCount() = colors.size
        }

        activity.viewPager.adapter = adapter
        activity.window.measureAndLayout()
        assertThat(activity.viewPager.childCount).isEqualTo(2)

        colors = listOf(Color.BLUE, Color.BLACK, Color.WHITE)
        adapter.notifyDataSetChanged()
        activity.window.measureAndLayout()
        // Repopulated with 3 items, offscreenPageLimit=1, pages 0,1 alive
        assertThat(activity.viewPager.childCount).isEqualTo(2)
    }

    // -- FragmentStatePagerAdapter tests --

    @Test
    fun `FragmentStatePagerAdapter creates fragments`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createStateTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        assertThat(activity.viewPager.childCount).isEqualTo(2)
    }

    @Test
    fun `fragment lifecycle on FragmentStatePagerAdapter`() {
        val controller = createController().create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        activity.viewPager.adapter = createStateTestAdapter(fm, colors)
        activity.window.measureAndLayout()

        val fragments = fm.getFragments()
        fragments.forEach { fragment ->
            assertThat(fragment.isAdded).isTrue()
            assertThat(fragment.isResumed).isTrue()
        }
    }

    // -- Edge cases --

    @Test
    fun `adapter set before activity resume still works`() {
        val window = Window(100, 100)
        val activity = ViewPagerActivity()
        val controller = ActivityController.of(activity, window, Resources())
        controller.create()
        // Set adapter during CREATED state (before start/resume)
        val fm = activity.getFragmentManager()
        val colors = listOf(Color.RED, Color.GREEN)
        activity.viewPager.adapter = createTestAdapter(fm, colors)
        window.measureAndLayout()

        // Fragments should still be created
        assertThat(activity.viewPager.childCount).isEqualTo(2)
        assertThat(fm.getFragments()).hasSize(2)

        // Now advance to resumed
        controller.start().resume()
        val fragments = fm.getFragments()
        fragments.forEach { fragment ->
            assertThat(fragment.isResumed).isTrue()
        }
    }

    @Test
    fun `pixel - fragment page renders correct color through ViewPager`() {
        val window = Window(100, 100)
        val controller = ActivityController.of(ViewPagerActivity(), window, Resources())
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        activity.viewPager.adapter = createTestAdapter(fm, listOf(Color.RED, Color.GREEN, Color.BLUE))
        window.measureAndLayout()

        val image = window.draw().toImage()
        // First page should be red
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.RED)
        // Left edge should also be red
        assertThat(image.getRGB(1, 50)).isEqualTo(Color.RED)
        // Right edge should also be red
        assertThat(image.getRGB(98, 50)).isEqualTo(Color.RED)
    }
}
