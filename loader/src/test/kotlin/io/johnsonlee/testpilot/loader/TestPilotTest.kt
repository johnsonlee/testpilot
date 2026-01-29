package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.widget.FrameLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class TestPilotTest {

    private val apkFile = File("../test-fixtures/simple-app.apk")

    private fun apkExists() = apkFile.exists()

    @Test
    @EnabledIf("apkExists")
    fun `should load APK and parse package name`() {
        val app = TestPilot.load(apkFile)

        assertThat(app.getPackageName()).isEqualTo("com.simplemobiletools.calculator")
    }

    @Test
    @EnabledIf("apkExists")
    fun `should parse activities from manifest`() {
        val app = TestPilot.load(apkFile)

        assertThat(app.getActivities()).hasSize(9)
    }

    @Test
    @EnabledIf("apkExists")
    fun `should find launcher activity`() {
        val app = TestPilot.load(apkFile)

        val launcher = app.getLauncherActivity()
        assertThat(launcher).isNotNull
        assertThat(launcher!!.name).contains("SplashActivity")
        assertThat(launcher.isLauncher).isTrue()
    }

    @Test
    @EnabledIf("apkExists")
    fun `should launch activity and create session`() {
        val app = TestPilot.load(apkFile)

        val session = app.launch()

        assertThat(session.getWindow().width).isEqualTo(480)
        assertThat(session.getWindow().height).isEqualTo(800)
    }

    @Test
    @EnabledIf("apkExists")
    fun `should support activity lifecycle`() {
        val app = TestPilot.load(apkFile)
        val session = app.launch()

        // Test lifecycle transitions
        session.pause()
        session.resume()
        session.stop()
        session.destroy()

        // Should not throw
    }

    @Test
    @EnabledIf("apkExists")
    fun `should parse resources`() {
        val app = TestPilot.load(apkFile)

        val resources = app.getResources()
        assertThat(resources).isNotNull
        assertThat(resources!!.packages).isNotEmpty
    }

    // ==================== Touch Event Integration Tests ====================

    private fun createTestContext() = object : Context() {
        override val resources = Resources()
        override fun getString(resId: Int) = ""
    }

    @Test
    @EnabledIf("apkExists")
    fun `should dispatch touch events and track action sequence`() {
        val app = TestPilot.load(apkFile)
        val session = app.launch()
        val window = session.getWindow()

        // Create a test view to receive touch events
        val actions = mutableListOf<Int>()
        val testView = object : FrameLayout(createTestContext()) {
            init {
                setOnTouchListener { _, event ->
                    actions.add(event.action)
                    true
                }
            }
        }

        // Set our test view as content
        window.setContentView(testView)

        val time = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100f, 100f)
        val moveEvent = MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, 110f, 110f)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 110f, 110f)

        session.dispatchTouchEvent(downEvent)
        session.dispatchTouchEvent(moveEvent)
        session.dispatchTouchEvent(upEvent)

        assertThat(actions).containsExactly(
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP
        )
    }

    @Test
    @EnabledIf("apkExists")
    fun `tap should dispatch DOWN and UP events`() {
        val app = TestPilot.load(apkFile)
        val session = app.launch()
        val window = session.getWindow()

        val actions = mutableListOf<Int>()
        val testView = object : FrameLayout(createTestContext()) {
            init {
                setOnTouchListener { _, event ->
                    actions.add(event.action)
                    true
                }
            }
        }

        window.setContentView(testView)
        session.tap(240f, 400f)

        assertThat(actions).containsExactly(
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP
        )
    }

    @Test
    @EnabledIf("apkExists")
    fun `tap coordinates should be passed correctly`() {
        val app = TestPilot.load(apkFile)
        val session = app.launch()
        val window = session.getWindow()

        val coordinates = mutableListOf<Pair<Float, Float>>()
        val testView = object : FrameLayout(createTestContext()) {
            init {
                setOnTouchListener { _, event ->
                    coordinates.add(event.x to event.y)
                    true
                }
            }
        }

        window.setContentView(testView)
        session.tap(123f, 456f)

        assertThat(coordinates).hasSize(2) // DOWN and UP
        assertThat(coordinates[0]).isEqualTo(123f to 456f)
        assertThat(coordinates[1]).isEqualTo(123f to 456f)
    }

    @Test
    @EnabledIf("apkExists")
    fun `multiple taps should each dispatch events`() {
        val app = TestPilot.load(apkFile)
        val session = app.launch()
        val window = session.getWindow()

        var tapCount = 0
        val testView = object : FrameLayout(createTestContext()) {
            init {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        tapCount++
                    }
                    true
                }
            }
        }

        window.setContentView(testView)

        session
            .tap(100f, 100f)
            .tap(200f, 200f)
            .tap(300f, 300f)

        assertThat(tapCount).isEqualTo(3)
    }
}
