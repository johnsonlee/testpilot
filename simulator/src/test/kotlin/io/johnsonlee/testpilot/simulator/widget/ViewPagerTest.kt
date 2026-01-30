package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.graphics.toImage
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ViewPagerTest {

    private val context = object : Context() {
        override val resources = Resources()
        override fun getString(resId: Int) = ""
    }

    /** A View that fills itself with a solid color. */
    private class ColorView(
        context: Context,
        private val fillColor: Int
    ) : View(context) {
        override fun draw(canvas: Canvas) {
            canvas.drawRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint(color = fillColor, style = Paint.Style.FILL)
            )
        }
    }

    /**
     * Simple PagerAdapter that creates a View per page and returns it as the key.
     */
    private fun createSimpleAdapter(count: Int): ViewPager.PagerAdapter {
        return object : ViewPager.PagerAdapter() {
            override fun getCount() = count
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val view = View(context)
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
    }

    /**
     * PagerAdapter that creates ColorViews and tracks instantiate/destroy calls.
     */
    private fun createColorAdapter(
        colors: List<Int>,
        instantiated: MutableList<Int>? = null,
        destroyed: MutableList<Int>? = null
    ): ViewPager.PagerAdapter {
        return object : ViewPager.PagerAdapter() {
            override fun getCount() = colors.size
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                instantiated?.add(position)
                val view = ColorView(context, colors[position])
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                destroyed?.add(position)
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
    }

    // -- Adapter binding tests --

    @Test
    fun `setting adapter populates children`() {
        val pager = ViewPager(context)
        pager.adapter = createSimpleAdapter(3)
        // With offscreenPageLimit=1, pages 0 and 1 should be alive
        assertThat(pager.childCount).isEqualTo(2)
    }

    @Test
    fun `setting adapter to null clears children`() {
        val pager = ViewPager(context)
        pager.adapter = createSimpleAdapter(3)
        pager.adapter = null
        assertThat(pager.childCount).isEqualTo(0)
    }

    @Test
    fun `notifyDataSetChanged repopulates pages`() {
        var count = 2
        val adapter = object : ViewPager.PagerAdapter() {
            override fun getCount() = count
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val view = View(context)
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
        val pager = ViewPager(context)
        pager.adapter = adapter
        assertThat(pager.childCount).isEqualTo(2)

        count = 4
        adapter.notifyDataSetChanged()
        // After repopulate, currentItem resets to 0, offscreen limit=1, so pages 0,1
        assertThat(pager.childCount).isEqualTo(2)
    }

    @Test
    fun `instantiateItem receives ViewPager as container`() {
        var receivedContainer: ViewGroup? = null
        val adapter = object : ViewPager.PagerAdapter() {
            override fun getCount() = 1
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                receivedContainer = container
                val view = View(context)
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
        val pager = ViewPager(context)
        pager.adapter = adapter
        assertThat(receivedContainer).isSameAs(pager)
    }

    // -- Page navigation tests --

    @Test
    fun `initial currentItem is 0`() {
        val pager = ViewPager(context)
        assertThat(pager.currentItem).isEqualTo(0)
    }

    @Test
    fun `setCurrentItem changes current page`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        pager.currentItem = 2
        assertThat(pager.currentItem).isEqualTo(2)
    }

    @Test
    fun `setCurrentItem with smoothScroll`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        pager.setCurrentItem(3, true)
        assertThat(pager.currentItem).isEqualTo(3)
    }

    @Test
    fun `setCurrentItem out of range is ignored`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(3)
        window.measureAndLayout()

        pager.currentItem = 10
        assertThat(pager.currentItem).isEqualTo(0)

        pager.currentItem = -1
        assertThat(pager.currentItem).isEqualTo(0)
    }

    // -- Listener tests --

    @Test
    fun `OnPageChangeListener notified on setCurrentItem`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        val selectedPages = mutableListOf<Int>()
        pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) { selectedPages.add(position) }
            override fun onPageScrollStateChanged(state: Int) {}
        })

        pager.currentItem = 2
        assertThat(selectedPages).containsExactly(2)
    }

    @Test
    fun `addOnPageChangeListener receives notifications`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        val selectedPages = mutableListOf<Int>()
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) { selectedPages.add(position) }
        })

        pager.currentItem = 1
        pager.currentItem = 3
        assertThat(selectedPages).containsExactly(1, 3)
    }

    @Test
    fun `removeOnPageChangeListener stops notifications`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        val selectedPages = mutableListOf<Int>()
        val listener = object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) { selectedPages.add(position) }
        }
        pager.addOnPageChangeListener(listener)
        pager.currentItem = 1
        pager.removeOnPageChangeListener(listener)
        pager.currentItem = 2
        assertThat(selectedPages).containsExactly(1)
    }

    @Test
    fun `deprecated and new listeners both receive notifications`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        val legacyPages = mutableListOf<Int>()
        val modernPages = mutableListOf<Int>()

        pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) { legacyPages.add(position) }
            override fun onPageScrollStateChanged(state: Int) {}
        })
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) { modernPages.add(position) }
        })

        pager.currentItem = 2
        assertThat(legacyPages).containsExactly(2)
        assertThat(modernPages).containsExactly(2)
    }

    // -- Layout tests --

    @Test
    fun `pages positioned horizontally at index times width`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(3)
        window.measureAndLayout()

        // currentItem=0, offscreenPageLimit=1, so pages 0 and 1 are alive
        // Page 0 child at left=0, Page 1 child at left=100
        val children = (0 until pager.childCount).map { pager.getChildAt(it) }
        assertThat(children.any { it.left == 0 && it.right == 100 }).isTrue()
        assertThat(children.any { it.left == 100 && it.right == 200 }).isTrue()
    }

    @Test
    fun `children measured to fill ViewPager`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(2)
        window.measureAndLayout()

        for (i in 0 until pager.childCount) {
            val child = pager.getChildAt(i)
            assertThat(child.getMeasuredWidth()).isEqualTo(100)
            assertThat(child.getMeasuredHeight()).isEqualTo(100)
        }
    }

    // -- Pixel tests --

    @Test
    fun `pixel - first page visible by default`() {
        val window = Window(100, 100)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors)
        window.measureAndLayout()

        val image = window.draw().toImage()
        // Center pixel should be RED (first page)
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.RED)
    }

    @Test
    fun `pixel - setCurrentItem shows correct page`() {
        val window = Window(100, 100)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors)
        window.measureAndLayout()

        pager.currentItem = 1
        window.measureAndLayout()

        val image = window.draw().toImage()
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.GREEN)
    }

    @Test
    fun `pixel - navigating to last page`() {
        val window = Window(100, 100)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors)
        window.measureAndLayout()

        pager.currentItem = 2
        window.measureAndLayout()

        val image = window.draw().toImage()
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.BLUE)
    }

    // -- Offscreen page limit tests --

    @Test
    fun `default offscreenPageLimit keeps adjacent pages alive`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        // currentItem=0, offscreenPageLimit=1 → pages 0,1 alive
        assertThat(pager.childCount).isEqualTo(2)
    }

    @Test
    fun `navigating destroys pages outside offscreen window`() {
        val window = Window(100, 100)
        val destroyed = mutableListOf<Int>()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors, destroyed = destroyed)
        window.measureAndLayout()

        // currentItem=0, pages 0,1 alive
        destroyed.clear()

        pager.currentItem = 3
        window.measureAndLayout()
        // pages 2,3,4 should be alive; pages 0,1 should have been destroyed
        assertThat(destroyed).contains(0, 1)
        assertThat(pager.childCount).isEqualTo(3)
    }

    @Test
    fun `navigating instantiates pages entering offscreen window`() {
        val window = Window(100, 100)
        val instantiated = mutableListOf<Int>()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors, instantiated = instantiated)
        window.measureAndLayout()

        // Initial instantiation: pages 0,1
        assertThat(instantiated).containsExactly(0, 1)
        instantiated.clear()

        pager.currentItem = 2
        window.measureAndLayout()
        // Page 2 was not alive, page 3 was not alive → both should be instantiated
        // Page 0 gets destroyed (outside window 1..3)
        assertThat(instantiated).contains(2, 3)
    }

    @Test
    fun `offscreenPageLimit 2 keeps more pages alive`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        pager.offscreenPageLimit = 2
        window.setContentView(pager)
        pager.adapter = createSimpleAdapter(5)
        window.measureAndLayout()

        // currentItem=0, offscreenPageLimit=2 → pages 0,1,2 alive
        assertThat(pager.childCount).isEqualTo(3)
    }

    @Test
    fun `destroyItem is called for pages leaving window`() {
        val window = Window(100, 100)
        val destroyed = mutableListOf<Int>()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK)
        val pager = ViewPager(context)
        window.setContentView(pager)
        pager.adapter = createColorAdapter(colors, destroyed = destroyed)
        window.measureAndLayout()

        // currentItem=0, pages 0,1 alive
        destroyed.clear()

        pager.currentItem = 3
        window.measureAndLayout()
        // Pages 0 and 1 should be destroyed (outside window 2..3)
        assertThat(destroyed).contains(0, 1)
    }

    // -- Touch dispatch tests --

    @Test
    fun `touch dispatch hits current page`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)

        val clicked = mutableListOf<Int>()
        val adapter = object : ViewPager.PagerAdapter() {
            override fun getCount() = 3
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val view = View(context).apply {
                    isClickable = true
                    setOnClickListener { clicked.add(position) }
                }
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
        pager.adapter = adapter
        window.measureAndLayout()

        // Tap center of page 0
        val downTime = System.currentTimeMillis()
        window.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 50f, 50f)
        )
        window.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, 50f, 50f)
        )

        assertThat(clicked).containsExactly(0)
    }

    @Test
    fun `touch dispatch hits correct page after navigation`() {
        val window = Window(100, 100)
        val pager = ViewPager(context)
        window.setContentView(pager)

        val clicked = mutableListOf<Int>()
        val adapter = object : ViewPager.PagerAdapter() {
            override fun getCount() = 3
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val view = View(context).apply {
                    isClickable = true
                    setOnClickListener { clicked.add(position) }
                }
                container.addView(view)
                return view
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
            override fun isViewFromObject(view: View, obj: Any) = view === obj
        }
        pager.adapter = adapter
        window.measureAndLayout()

        pager.currentItem = 1
        window.measureAndLayout()

        // Tap center — should hit page 1 (current page)
        val downTime = System.currentTimeMillis()
        window.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 50f, 50f)
        )
        window.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, 50f, 50f)
        )

        assertThat(clicked).containsExactly(1)
    }
}
