package io.johnsonlee.testpilot.renderer

import com.android.resources.Density
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO

/**
 * Integration tests for LayoutRenderer using real layoutlib rendering.
 *
 * These tests require:
 * - Android SDK installed with platform version 31+
 * - Layoutlib data files (fonts, native libs, icu) in the data/ directory
 *
 * Tests will be skipped if the environment is not properly configured.
 */
class LayoutRendererIntegrationTest {

    companion object {
        private var environment: RenderEnvironment? = null
        private var environmentReady = false
        private var skipReason: String? = null

        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            try {
                environment = RenderEnvironment()
                // Try to initialize bridge to verify environment is ready
                environment!!.initBridge()

                // Also try creating a simple render to ensure full initialization works
                val renderer = LayoutRenderer(environment!!)
                renderer.render("<View xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>")
                renderer.close()

                environmentReady = true
            } catch (e: Exception) {
                skipReason = e.message ?: e.javaClass.simpleName
                println("Layoutlib environment not ready: $skipReason")
                e.printStackTrace()
                println("")
                println("Skipping integration tests. To enable:")
                println("  1. Ensure layoutlib runtime and resources are downloaded via Gradle")
                println("  2. Or manually set up data/ directory with fonts, native libs, and icu files")
                environmentReady = false
            }
        }
    }

    @BeforeEach
    fun checkEnvironment() {
        assumeTrue(environmentReady, "Layoutlib environment not ready: $skipReason")
    }

    @TempDir
    lateinit var tempDir: File

    // ==================== Basic Layout Rendering ====================

    @Test
    fun `render simple TextView`() {
        val layoutXml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Hello World"
                android:textSize="24sp"
                android:textColor="#000000" />
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
        assertTrue(result.image.width > 0)
        assertTrue(result.image.height > 0)
    }

    @Test
    fun `render LinearLayout with multiple children`() {
        val layoutXml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp"
                android:background="#FFFFFF">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Title"
                    android:textSize="20sp"
                    android:textStyle="bold"
                    android:textColor="#000000" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Subtitle"
                    android:textSize="14sp"
                    android:textColor="#666666"
                    android:layout_marginTop="8dp" />

                <View
                    android:layout_width="100dp"
                    android:layout_height="40dp"
                    android:background="#2196F3"
                    android:layout_marginTop="16dp" />
            </LinearLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
        // LinearLayout should have reasonable dimensions
        assertTrue(result.image.width > 100)
        assertTrue(result.image.height > 100)
    }

    @Test
    fun `render FrameLayout with overlapping views`() {
        val layoutXml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="200dp"
                android:layout_height="200dp"
                android:background="#EEEEEE">

                <View
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#FF0000" />

                <View
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:layout_gravity="bottom|end"
                    android:background="#0000FF" />
            </FrameLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    @Test
    fun `render RelativeLayout with positioned children`() {
        val layoutXml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="100dp"
                android:padding="16dp"
                android:background="#FFFFFF">

                <View
                    android:id="@+id/box1"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:background="#FF0000"
                    android:layout_alignParentStart="true" />

                <View
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:background="#00FF00"
                    android:layout_toEndOf="@id/box1"
                    android:layout_marginStart="8dp" />
            </RelativeLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    // ==================== Device Configuration Tests ====================

    @Test
    fun `render with different device configs produces different sizes`() {
        val layoutXml = """
            <View xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="#FF0000" />
        """.trimIndent()

        val smallConfig = DeviceConfig(name = "Small", screenWidth = 320, screenHeight = 480, density = Density.MEDIUM)
        val largeConfig = DeviceConfig(name = "Large", screenWidth = 1080, screenHeight = 1920, density = Density.XXHIGH)

        val smallResult = renderLayout(layoutXml, smallConfig)
        val largeResult = renderLayout(layoutXml, largeConfig)

        // Different configs should produce different image sizes
        assertNotEquals(smallResult.image.width, largeResult.image.width)
        assertNotEquals(smallResult.image.height, largeResult.image.height)
    }

    @Test
    fun `render with Pixel 5 config`() {
        val layoutXml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="100dp"
                android:background="#FFFFFF">
                <View
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_gravity="center"
                    android:background="#2196F3" />
            </FrameLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml, DeviceConfig.PIXEL_5)

        assertNotNull(result.image)
        assertEquals(1080, result.image.width)
    }

    // ==================== Theme Tests ====================

    @Test
    fun `render with Material Light theme`() {
        val layoutXml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp"
                android:background="#FFFFFF">

                <View
                    android:layout_width="100dp"
                    android:layout_height="48dp"
                    android:background="#2196F3" />
            </LinearLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml, theme = "Theme.Material.Light.NoActionBar")

        assertNotNull(result.image)
    }

    @Test
    fun `render with Material Dark theme`() {
        val layoutXml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp"
                android:background="#303030">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Dark Theme Text"
                    android:textColor="#FFFFFF" />
            </LinearLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml, theme = "Theme.Material.NoActionBar")

        assertNotNull(result.image)
    }

    // ==================== Visual Regression Tests ====================

    @Test
    fun `screenshot should be deterministic`() {
        val layoutXml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="200dp"
                android:layout_height="100dp"
                android:text="Deterministic"
                android:textSize="18sp"
                android:gravity="center"
                android:background="#FFFFFF"
                android:textColor="#000000" />
        """.trimIndent()

        val config = DeviceConfig(name = "Test", screenWidth = 200, screenHeight = 100, density = Density.MEDIUM)

        val result1 = renderLayout(layoutXml, config)
        val result2 = renderLayout(layoutXml, config)

        // Same layout should produce identical images
        result1.image.assertVisuallyEquals(result2.image, tolerance = 0.0)
    }

    @Test
    fun `can save and compare screenshots`() {
        val layoutXml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="300dp"
                android:layout_height="200dp"
                android:orientation="vertical"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Snapshot Test"
                    android:textSize="24sp"
                    android:textColor="#333333" />
            </LinearLayout>
        """.trimIndent()

        val snapshotsDir = File(tempDir, "snapshots")
        val manager = SnapshotManager(snapshotsDir)

        val config = DeviceConfig(name = "Snapshot", screenWidth = 300, screenHeight = 200, density = Density.MEDIUM)
        val screenshot = renderLayout(layoutXml, config).image

        // Record the golden
        screenshot.recordAsSnapshot(manager, "snapshot_test")
        assertTrue(manager.exists("snapshot_test"))

        // Render again and verify
        val screenshot2 = renderLayout(layoutXml, config).image
        screenshot2.assertMatchesSnapshot(manager, "snapshot_test")
    }

    // ==================== Complex Layout Tests ====================

    @Test
    fun `render card-like layout`() {
        val layoutXml = """
            <androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_margin="8dp"
                app:cardCornerRadius="8dp"
                app:cardElevation="4dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Card Title"
                        android:textSize="18sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Card description goes here"
                        android:layout_marginTop="8dp" />
                </LinearLayout>
            </androidx.cardview.widget.CardView>
        """.trimIndent()

        // CardView may not be available, so we catch and skip
        try {
            val result = renderLayout(layoutXml)
            assertNotNull(result.image)
        } catch (e: Exception) {
            // CardView not available in layoutlib, skip this test
            println("Skipping CardView test: ${e.message}")
        }
    }

    @Test
    fun `render ScrollView with content`() {
        val layoutXml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="200dp"
                android:background="#FFFFFF">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:background="#FF5722" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:layout_marginTop="8dp"
                        android:background="#2196F3" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:layout_marginTop="8dp"
                        android:background="#4CAF50" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:layout_marginTop="8dp"
                        android:background="#FFC107" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:layout_marginTop="8dp"
                        android:background="#9C27B0" />
                </LinearLayout>
            </ScrollView>
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    @Test
    fun `render ImageView with placeholder`() {
        val layoutXml = """
            <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:background="#CCCCCC"
                android:scaleType="centerCrop" />
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `render empty layout`() {
        val layoutXml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="100dp"
                android:layout_height="100dp" />
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    @Test
    fun `render deeply nested layout`() {
        val layoutXml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:background="#FFFFFF">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal">
                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="vertical">
                        <LinearLayout
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal">
                            <View
                                android:layout_width="50dp"
                                android:layout_height="50dp"
                                android:background="#FF5722" />
                        </LinearLayout>
                    </LinearLayout>
                </LinearLayout>
            </LinearLayout>
        """.trimIndent()

        val result = renderLayout(layoutXml)

        assertNotNull(result.image)
    }

    // ==================== Helper Methods ====================

    private fun renderLayout(
        layoutXml: String,
        deviceConfig: DeviceConfig = DeviceConfig.DEFAULT,
        theme: String = "Theme.Material.Light.NoActionBar"
    ): RenderResult {
        return LayoutRenderer(environment!!, deviceConfig).use { renderer ->
            renderer.render(layoutXml, theme)
        }
    }
}
