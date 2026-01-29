package io.johnsonlee.testpilot.loader

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
}
