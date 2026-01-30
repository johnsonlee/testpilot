package io.johnsonlee.testpilot.simulator.app

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.ActivityController
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.DrawCommand
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.graphics.toImage
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.widget.FrameLayout
import io.johnsonlee.testpilot.simulator.widget.TextView
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class FragmentRenderingTest {

    companion object {
        const val CONTAINER_ID = 100
        const val CONTAINER_2_ID = 200
        const val FRAGMENT_VIEW_ID = 300
        const val FRAGMENT_VIEW_2_ID = 301
    }

    private class SingleContainerActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            val container = FrameLayout(this).apply { id = CONTAINER_ID }
            setContentView(container)
        }
    }

    private class TwoContainerActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            val root = FrameLayout(this)
            val container1 = FrameLayout(this).apply { id = CONTAINER_ID }
            val container2 = FrameLayout(this).apply { id = CONTAINER_2_ID }
            root.addView(container1)
            root.addView(container2)
            setContentView(root)
        }
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

    private fun colorFragment(color: Int, viewId: Int = FRAGMENT_VIEW_ID): Fragment {
        return object : Fragment() {
            override fun onCreateView(container: ViewGroup?): View? {
                return ColorView(requireActivity(), color).apply {
                    id = viewId
                    layoutParams = View.LayoutParams(
                        View.LayoutParams.MATCH_PARENT,
                        View.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
    }

    private fun renderToImage(activity: Activity): BufferedImage {
        activity.window.measureAndLayout()
        return activity.window.draw().toImage()
    }

    private fun createSmallSingleContainer(): Pair<Activity, ActivityController<SingleContainerActivity>> {
        val controller = ActivityController.of(SingleContainerActivity(), Window(100, 100), Resources())
        controller.create().start().resume()
        return controller.get() to controller
    }

    private fun viewFragment(viewId: Int = FRAGMENT_VIEW_ID): Fragment {
        return object : Fragment() {
            override fun onCreateView(container: ViewGroup?): View? {
                return View(requireActivity()).apply { id = viewId }
            }
        }
    }

    private fun textFragment(text: String, viewId: Int = FRAGMENT_VIEW_ID): Fragment {
        return object : Fragment() {
            override fun onCreateView(container: ViewGroup?): View? {
                return TextView(requireActivity()).apply {
                    id = viewId
                    this.text = text
                }
            }
        }
    }

    private fun drawTexts(activity: Activity): List<String> {
        activity.window.measureAndLayout()
        val canvas = activity.window.draw()
        return canvas.getCommands()
            .filterIsInstance<DrawCommand.Text>()
            .map { it.text }
    }

    private fun createSingleContainer(): Pair<Activity, ActivityController<SingleContainerActivity>> {
        val controller = ActivityController.of(SingleContainerActivity(), Window(), Resources())
        controller.create().start().resume()
        return controller.get() to controller
    }

    @Test
    fun `fragment view is added as child of container`() {
        val (activity, _) = createSingleContainer()
        val container = activity.findViewById<FrameLayout>(CONTAINER_ID)!!

        assertThat(container.childCount).isEqualTo(0)

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, viewFragment())
            .commit()

        assertThat(container.childCount).isEqualTo(1)
    }

    @Test
    fun `fragment view is removed from container when fragment is removed`() {
        val (activity, _) = createSingleContainer()
        val container = activity.findViewById<FrameLayout>(CONTAINER_ID)!!
        val fm = activity.getFragmentManager()

        val fragment = viewFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment, "frag")
            .commit()
        assertThat(container.childCount).isEqualTo(1)

        fm.beginTransaction()
            .remove(fragment)
            .commit()
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun `replace removes old view and adds new view to container`() {
        val (activity, _) = createSingleContainer()
        val container = activity.findViewById<FrameLayout>(CONTAINER_ID)!!
        val fm = activity.getFragmentManager()

        val first = viewFragment(FRAGMENT_VIEW_ID)
        fm.beginTransaction()
            .add(CONTAINER_ID, first, "first")
            .commit()
        assertThat(container.childCount).isEqualTo(1)

        val second = viewFragment(FRAGMENT_VIEW_2_ID)
        fm.beginTransaction()
            .replace(CONTAINER_ID, second, "second")
            .commit()

        assertThat(container.childCount).isEqualTo(1)
        assertThat(container.getChildAt(0).id).isEqualTo(FRAGMENT_VIEW_2_ID)
    }

    @Test
    fun `hidden fragment view has GONE visibility, shown restores VISIBLE`() {
        val (activity, _) = createSingleContainer()
        val fm = activity.getFragmentManager()

        val fragment = viewFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment, "frag")
            .commit()

        val view = fragment.view!!
        assertThat(view.visibility).isEqualTo(View.VISIBLE)

        fm.beginTransaction().hide(fragment).commit()
        assertThat(view.visibility).isEqualTo(View.GONE)

        fm.beginTransaction().show(fragment).commit()
        assertThat(view.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `multiple fragments in different containers render independently`() {
        val controller = ActivityController.of(TwoContainerActivity(), Window(), Resources())
        controller.create().start().resume()
        val activity = controller.get()

        val container1 = activity.findViewById<FrameLayout>(CONTAINER_ID)!!
        val container2 = activity.findViewById<FrameLayout>(CONTAINER_2_ID)!!
        val fm = activity.getFragmentManager()

        fm.beginTransaction()
            .add(CONTAINER_ID, viewFragment(FRAGMENT_VIEW_ID), "frag1")
            .commit()

        fm.beginTransaction()
            .add(CONTAINER_2_ID, viewFragment(FRAGMENT_VIEW_2_ID), "frag2")
            .commit()

        assertThat(container1.childCount).isEqualTo(1)
        assertThat(container1.getChildAt(0).id).isEqualTo(FRAGMENT_VIEW_ID)
        assertThat(container2.childCount).isEqualTo(1)
        assertThat(container2.getChildAt(0).id).isEqualTo(FRAGMENT_VIEW_2_ID)
    }

    @Test
    fun `fragment view is accessible via activity findViewById`() {
        val (activity, _) = createSingleContainer()

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, viewFragment(FRAGMENT_VIEW_ID))
            .commit()

        val foundView = activity.findViewById<View>(FRAGMENT_VIEW_ID)
        assertThat(foundView).isNotNull()
        assertThat(foundView!!.id).isEqualTo(FRAGMENT_VIEW_ID)
    }

    // --- Draw command tests: verify fragment views produce rendering output ---

    @Test
    fun `added fragment view is drawn`() {
        val (activity, _) = createSingleContainer()

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, textFragment("Hello Fragment"))
            .commit()

        assertThat(drawTexts(activity)).contains("Hello Fragment")
    }

    @Test
    fun `removed fragment view is not drawn`() {
        val (activity, _) = createSingleContainer()
        val fm = activity.getFragmentManager()

        val fragment = textFragment("Goodbye")
        fm.beginTransaction().add(CONTAINER_ID, fragment, "f").commit()
        assertThat(drawTexts(activity)).contains("Goodbye")

        fm.beginTransaction().remove(fragment).commit()
        assertThat(drawTexts(activity)).doesNotContain("Goodbye")
    }

    @Test
    fun `hidden fragment view is not drawn, shown fragment is drawn again`() {
        val (activity, _) = createSingleContainer()
        val fm = activity.getFragmentManager()

        val fragment = textFragment("Toggle Me")
        fm.beginTransaction().add(CONTAINER_ID, fragment, "f").commit()
        assertThat(drawTexts(activity)).contains("Toggle Me")

        fm.beginTransaction().hide(fragment).commit()
        assertThat(drawTexts(activity)).doesNotContain("Toggle Me")

        fm.beginTransaction().show(fragment).commit()
        assertThat(drawTexts(activity)).contains("Toggle Me")
    }

    @Test
    fun `replaced fragment draws only the new content`() {
        val (activity, _) = createSingleContainer()
        val fm = activity.getFragmentManager()

        fm.beginTransaction()
            .add(CONTAINER_ID, textFragment("Old"), "old")
            .commit()

        fm.beginTransaction()
            .replace(CONTAINER_ID, textFragment("New"), "new")
            .commit()

        val texts = drawTexts(activity)
        assertThat(texts).contains("New")
        assertThat(texts).doesNotContain("Old")
    }

    @Test
    fun `multiple fragments in different containers are both drawn`() {
        val controller = ActivityController.of(TwoContainerActivity(), Window(), Resources())
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        fm.beginTransaction()
            .add(CONTAINER_ID, textFragment("First", FRAGMENT_VIEW_ID), "f1")
            .commit()
        fm.beginTransaction()
            .add(CONTAINER_2_ID, textFragment("Second", FRAGMENT_VIEW_2_ID), "f2")
            .commit()

        val texts = drawTexts(activity)
        assertThat(texts).contains("First")
        assertThat(texts).contains("Second")
    }

    // --- Pixel-level tests: verify actual rasterised output via Canvas.toImage() ---

    @Test
    fun `pixel - added fragment fills container with its color`() {
        val (activity, _) = createSmallSingleContainer()

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, colorFragment(Color.RED))
            .commit()

        val image = renderToImage(activity)
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.RED)
    }

    @Test
    fun `pixel - removed fragment leaves transparent pixels`() {
        val (activity, _) = createSmallSingleContainer()
        val fm = activity.getFragmentManager()

        val fragment = colorFragment(Color.RED)
        fm.beginTransaction().add(CONTAINER_ID, fragment, "f").commit()

        val before = renderToImage(activity)
        assertThat(before.getRGB(50, 50)).isEqualTo(Color.RED)

        fm.beginTransaction().remove(fragment).commit()

        val after = renderToImage(activity)
        assertThat(after.getRGB(50, 50)).isNotEqualTo(Color.RED)
    }

    @Test
    fun `pixel - hidden fragment is not rasterised, shown fragment is`() {
        val (activity, _) = createSmallSingleContainer()
        val fm = activity.getFragmentManager()

        val fragment = colorFragment(Color.BLUE)
        fm.beginTransaction().add(CONTAINER_ID, fragment, "f").commit()
        assertThat(renderToImage(activity).getRGB(50, 50)).isEqualTo(Color.BLUE)

        fm.beginTransaction().hide(fragment).commit()
        assertThat(renderToImage(activity).getRGB(50, 50)).isNotEqualTo(Color.BLUE)

        fm.beginTransaction().show(fragment).commit()
        assertThat(renderToImage(activity).getRGB(50, 50)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `pixel - replace changes rendered color`() {
        val (activity, _) = createSmallSingleContainer()
        val fm = activity.getFragmentManager()

        fm.beginTransaction()
            .add(CONTAINER_ID, colorFragment(Color.RED), "old")
            .commit()
        assertThat(renderToImage(activity).getRGB(50, 50)).isEqualTo(Color.RED)

        fm.beginTransaction()
            .replace(CONTAINER_ID, colorFragment(Color.GREEN), "new")
            .commit()

        val image = renderToImage(activity)
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.GREEN)
    }

    @Test
    fun `pixel - entire fragment area is filled`() {
        val (activity, _) = createSmallSingleContainer()

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, colorFragment(Color.RED))
            .commit()

        val image = renderToImage(activity)
        // Check corners and center
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.RED)
        assertThat(image.getRGB(99, 0)).isEqualTo(Color.RED)
        assertThat(image.getRGB(0, 99)).isEqualTo(Color.RED)
        assertThat(image.getRGB(99, 99)).isEqualTo(Color.RED)
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.RED)
    }
}
