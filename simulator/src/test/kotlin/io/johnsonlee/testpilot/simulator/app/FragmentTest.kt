package io.johnsonlee.testpilot.simulator.app

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.ActivityController
import io.johnsonlee.testpilot.simulator.activity.LifecycleState
import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.os.Bundle
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.widget.FrameLayout
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FragmentTest {

    companion object {
        const val CONTAINER_ID = 100
    }

    private class TestActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            val container = FrameLayout(this).apply { id = CONTAINER_ID }
            setContentView(container)
        }
    }

    private class LifecycleTrackingFragment : Fragment() {
        val events = mutableListOf<String>()
        var createdView: View? = null

        override fun onAttach(context: Context) { events.add("onAttach") }
        override fun onCreate(savedInstanceState: Bundle?) { events.add("onCreate") }
        override fun onCreateView(container: ViewGroup?): View? {
            events.add("onCreateView")
            createdView = View(requireActivity())
            return createdView
        }
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) { events.add("onViewCreated") }
        override fun onStart() { events.add("onStart") }
        override fun onResume() { events.add("onResume") }
        override fun onPause() { events.add("onPause") }
        override fun onStop() { events.add("onStop") }
        override fun onDestroyView() { events.add("onDestroyView") }
        override fun onDestroy() { events.add("onDestroy") }
        override fun onDetach() { events.add("onDetach") }
    }

    private fun createController(): ActivityController<TestActivity> {
        return ActivityController.of(TestActivity(), Window(), Resources())
    }

    @Test
    fun `fragment added to resumed activity receives full lifecycle`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()

        val fragment = LifecycleTrackingFragment()
        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, fragment, "test")
            .commit()

        assertThat(fragment.events).containsExactly(
            "onAttach", "onCreate", "onCreateView", "onViewCreated", "onStart", "onResume"
        )
        assertThat(fragment.isAdded).isTrue()
        assertThat(fragment.isResumed).isTrue()
        assertThat(fragment.activity).isSameAs(activity)
    }

    @Test
    fun `fragment added to created-only activity receives partial lifecycle`() {
        val controller = createController()
        controller.create()
        val activity = controller.get()

        val fragment = LifecycleTrackingFragment()
        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, fragment, "test")
            .commit()

        assertThat(fragment.events).containsExactly(
            "onAttach", "onCreate", "onCreateView", "onViewCreated"
        )
        assertThat(fragment.isAdded).isTrue()
        assertThat(fragment.isResumed).isFalse()
        assertThat(fragment.lifecycleState).isEqualTo(LifecycleState.CREATED)
    }

    @Test
    fun `fragment follows activity through full forward and backward lifecycle`() {
        val controller = createController()
        controller.create()
        val activity = controller.get()

        val fragment = LifecycleTrackingFragment()
        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, fragment, "test")
            .commit()

        fragment.events.clear()

        // Forward
        controller.start()
        assertThat(fragment.events).containsExactly("onStart")
        fragment.events.clear()

        controller.resume()
        assertThat(fragment.events).containsExactly("onResume")
        fragment.events.clear()

        // Backward
        controller.pause()
        assertThat(fragment.events).containsExactly("onPause")
        fragment.events.clear()

        controller.stop()
        assertThat(fragment.events).containsExactly("onStop")
        fragment.events.clear()

        controller.destroy()
        assertThat(fragment.events).containsExactly("onDestroyView", "onDestroy", "onDetach")
    }

    @Test
    fun `findFragmentByTag returns correct fragment`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val fragment = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment, "my_tag")
            .commit()

        assertThat(fm.findFragmentByTag("my_tag")).isSameAs(fragment)
        assertThat(fm.findFragmentByTag("nonexistent")).isNull()
    }

    @Test
    fun `findFragmentById returns correct fragment`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val fragment = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment)
            .commit()

        assertThat(fm.findFragmentById(CONTAINER_ID)).isSameAs(fragment)
        assertThat(fm.findFragmentById(999)).isNull()
    }

    @Test
    fun `remove tears down fragment lifecycle`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val fragment = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment, "test")
            .commit()

        fragment.events.clear()

        fm.beginTransaction()
            .remove(fragment)
            .commit()

        assertThat(fragment.events).containsExactly(
            "onPause", "onStop", "onDestroyView", "onDestroy", "onDetach"
        )
        assertThat(fragment.isAdded).isFalse()
        assertThat(fragment.activity).isNull()
        assertThat(fm.findFragmentByTag("test")).isNull()
    }

    @Test
    fun `replace removes existing and adds new fragment`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val first = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, first, "first")
            .commit()

        first.events.clear()

        val second = LifecycleTrackingFragment()
        fm.beginTransaction()
            .replace(CONTAINER_ID, second, "second")
            .commit()

        // First fragment should be torn down
        assertThat(first.events).containsExactly(
            "onPause", "onStop", "onDestroyView", "onDestroy", "onDetach"
        )
        assertThat(first.isAdded).isFalse()

        // Second fragment should be fully started
        assertThat(second.events).containsExactly(
            "onAttach", "onCreate", "onCreateView", "onViewCreated", "onStart", "onResume"
        )
        assertThat(second.isAdded).isTrue()

        assertThat(fm.findFragmentByTag("first")).isNull()
        assertThat(fm.findFragmentByTag("second")).isSameAs(second)
    }

    @Test
    fun `back stack pop reverses replace operation`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val first = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, first, "first")
            .commit()

        val second = LifecycleTrackingFragment()
        fm.beginTransaction()
            .replace(CONTAINER_ID, second, "second")
            .addToBackStack(null)
            .commit()

        assertThat(fm.findFragmentByTag("second")).isSameAs(second)
        assertThat(fm.getBackStackEntryCount()).isEqualTo(1)

        // Pop back stack
        val popped = fm.popBackStack()

        assertThat(popped).isTrue()
        assertThat(fm.getBackStackEntryCount()).isEqualTo(0)

        // Second fragment should be removed
        assertThat(second.isAdded).isFalse()

        // First fragment should be re-added
        assertThat(fm.findFragmentByTag("first")).isSameAs(first)
        assertThat(first.isAdded).isTrue()
    }

    @Test
    fun `requireView throws when no view`() {
        val fragment = Fragment()
        assertThatThrownBy { fragment.requireView() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `requireActivity throws when not attached`() {
        val fragment = Fragment()
        assertThatThrownBy { fragment.requireActivity() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `show and hide toggle visibility`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()
        val fm = activity.getFragmentManager()

        val fragment = LifecycleTrackingFragment()
        fm.beginTransaction()
            .add(CONTAINER_ID, fragment, "test")
            .commit()

        assertThat(fragment.isHidden).isFalse()
        assertThat(fragment.isVisible).isTrue()

        fm.beginTransaction().hide(fragment).commit()
        assertThat(fragment.isHidden).isTrue()
        assertThat(fragment.isVisible).isFalse()
        assertThat(fragment.view?.visibility).isEqualTo(View.GONE)

        fm.beginTransaction().show(fragment).commit()
        assertThat(fragment.isHidden).isFalse()
        assertThat(fragment.isVisible).isTrue()
        assertThat(fragment.view?.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `arguments are accessible in onCreate`() {
        val controller = createController()
        controller.create().start().resume()
        val activity = controller.get()

        var receivedValue: String? = null
        val fragment = object : Fragment() {
            override fun onCreate(savedInstanceState: Bundle?) {
                receivedValue = arguments?.getString("key")
            }
            override fun onCreateView(container: ViewGroup?): View? = View(requireActivity())
        }
        fragment.arguments = Bundle().apply { putString("key", "hello") }

        activity.getFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, fragment)
            .commit()

        assertThat(receivedValue).isEqualTo("hello")
    }

    @Test
    fun `getSupportFragmentManager returns same instance as getFragmentManager`() {
        val controller = createController()
        controller.create()
        val activity = controller.get()

        assertThat(activity.getSupportFragmentManager()).isSameAs(activity.getFragmentManager())
    }
}
