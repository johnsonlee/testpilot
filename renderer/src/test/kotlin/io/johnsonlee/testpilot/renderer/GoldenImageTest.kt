package io.johnsonlee.testpilot.renderer

import com.android.resources.Density
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.io.File
import javax.imageio.ImageIO

/**
 * Golden image tests for visual regression testing.
 *
 * These tests compare rendered layouts against pre-recorded golden images.
 *
 * To record new golden images:
 *   ./gradlew :renderer:test -Dtestpilot.record=true
 *
 * Golden images are stored in: src/test/resources/golden/
 */
class GoldenImageTest {

    companion object {
        private var environment: RenderEnvironment? = null
        private var environmentReady = false
        private var skipReason: String? = null

        // Set to true to record new golden images
        private val recordMode = System.getProperty("testpilot.record")?.toBoolean() ?: false

        // Golden images directory
        private val goldenDir = File("src/test/resources/golden")

        // Tolerance for pixel comparison (0.0 = exact match, 0.01 = 1% difference allowed)
        private const val TOLERANCE = 0.001 // 0.1% tolerance for anti-aliasing differences

        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            goldenDir.mkdirs()

            try {
                environment = RenderEnvironment()
                environment!!.initBridge()

                // Warm up with a simple render
                val renderer = LayoutRenderer(environment!!)
                renderer.render("<View xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>")
                renderer.close()

                environmentReady = true

                if (recordMode) {
                    println("⚠️  RECORD MODE: Golden images will be recorded/updated")
                    println("   Location: ${goldenDir.absolutePath}")
                }
            } catch (e: Exception) {
                skipReason = e.message ?: e.javaClass.simpleName
                println("Layoutlib environment not ready: $skipReason")
                environmentReady = false
            }
        }
    }

    @BeforeEach
    fun checkEnvironment() {
        assumeTrue(environmentReady, "Layoutlib environment not ready: $skipReason")
    }

    // ==================== Golden Image Tests ====================

    @Test
    fun `simple_textview - Hello World text`() {
        verifyGolden(
            name = "simple_textview",
            layoutXml = """
                <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="200dp"
                    android:layout_height="50dp"
                    android:text="Hello World"
                    android:textSize="18sp"
                    android:textColor="#000000"
                    android:background="#FFFFFF"
                    android:gravity="center" />
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 200, screenHeight = 50, density = Density.MEDIUM)
        )
    }

    @Test
    fun `linear_vertical - Three colored boxes`() {
        verifyGolden(
            name = "linear_vertical",
            layoutXml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="150dp"
                    android:orientation="vertical"
                    android:background="#FFFFFF">

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:background="#FF0000" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:background="#00FF00" />
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="50dp"
                        android:background="#0000FF" />
                </LinearLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 100, screenHeight = 150, density = Density.MEDIUM)
        )
    }

    @Test
    fun `linear_horizontal - Three colored boxes`() {
        verifyGolden(
            name = "linear_horizontal",
            layoutXml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="150dp"
                    android:layout_height="50dp"
                    android:orientation="horizontal"
                    android:background="#FFFFFF">

                    <View
                        android:layout_width="50dp"
                        android:layout_height="match_parent"
                        android:background="#FF0000" />
                    <View
                        android:layout_width="50dp"
                        android:layout_height="match_parent"
                        android:background="#00FF00" />
                    <View
                        android:layout_width="50dp"
                        android:layout_height="match_parent"
                        android:background="#0000FF" />
                </LinearLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 150, screenHeight = 50, density = Density.MEDIUM)
        )
    }

    @Test
    fun `frame_overlap - Overlapping red and blue boxes`() {
        verifyGolden(
            name = "frame_overlap",
            layoutXml = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#FFFFFF">

                    <View
                        android:layout_width="60dp"
                        android:layout_height="60dp"
                        android:background="#FF0000" />

                    <View
                        android:layout_width="60dp"
                        android:layout_height="60dp"
                        android:layout_gravity="bottom|end"
                        android:background="#0000FF" />
                </FrameLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 100, screenHeight = 100, density = Density.MEDIUM)
        )
    }

    @Test
    fun `padding_margin - Box with padding and margin`() {
        verifyGolden(
            name = "padding_margin",
            layoutXml = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:padding="10dp"
                    android:background="#CCCCCC">

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:layout_margin="10dp"
                        android:background="#FF5722" />
                </FrameLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 100, screenHeight = 100, density = Density.MEDIUM)
        )
    }

    @Test
    fun `gravity_center - Centered box`() {
        verifyGolden(
            name = "gravity_center",
            layoutXml = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#EEEEEE">

                    <View
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_gravity="center"
                        android:background="#4CAF50" />
                </FrameLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 100, screenHeight = 100, density = Density.MEDIUM)
        )
    }

    @Test
    fun `relative_layout - Positioned boxes`() {
        verifyGolden(
            name = "relative_layout",
            layoutXml = """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#FFFFFF">

                    <View
                        android:id="@+id/top_left"
                        android:layout_width="30dp"
                        android:layout_height="30dp"
                        android:layout_alignParentTop="true"
                        android:layout_alignParentStart="true"
                        android:background="#F44336" />

                    <View
                        android:layout_width="30dp"
                        android:layout_height="30dp"
                        android:layout_alignParentTop="true"
                        android:layout_alignParentEnd="true"
                        android:background="#2196F3" />

                    <View
                        android:layout_width="30dp"
                        android:layout_height="30dp"
                        android:layout_alignParentBottom="true"
                        android:layout_centerHorizontal="true"
                        android:background="#4CAF50" />
                </RelativeLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 100, screenHeight = 100, density = Density.MEDIUM)
        )
    }

    @Test
    fun `text_styling - Bold and colored text`() {
        verifyGolden(
            name = "text_styling",
            layoutXml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="200dp"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="8dp"
                    android:background="#FFFFFF">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Bold Title"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:textColor="#000000" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Regular subtitle"
                        android:textSize="12sp"
                        android:textColor="#666666"
                        android:layout_marginTop="4dp" />
                </LinearLayout>
            """.trimIndent(),
            config = DeviceConfig(name = "Test", screenWidth = 200, screenHeight = 80, density = Density.MEDIUM)
        )
    }

    @Test
    fun `density_hdpi - Same layout at HDPI`() {
        verifyGolden(
            name = "density_hdpi",
            layoutXml = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#FFFFFF">

                    <View
                        android:layout_width="50dp"
                        android:layout_height="50dp"
                        android:layout_gravity="center"
                        android:background="#9C27B0" />
                </FrameLayout>
            """.trimIndent(),
            // 100dp at HDPI (240dpi) = 150px
            config = DeviceConfig(name = "HDPI", screenWidth = 150, screenHeight = 150, density = Density.HIGH)
        )
    }

    @Test
    fun `density_xhdpi - Same layout at XHDPI`() {
        verifyGolden(
            name = "density_xhdpi",
            layoutXml = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="100dp"
                    android:layout_height="100dp"
                    android:background="#FFFFFF">

                    <View
                        android:layout_width="50dp"
                        android:layout_height="50dp"
                        android:layout_gravity="center"
                        android:background="#9C27B0" />
                </FrameLayout>
            """.trimIndent(),
            // 100dp at XHDPI (320dpi) = 200px
            config = DeviceConfig(name = "XHDPI", screenWidth = 200, screenHeight = 200, density = Density.XHIGH)
        )
    }

    // ==================== Helper Methods ====================

    private fun verifyGolden(
        name: String,
        layoutXml: String,
        config: DeviceConfig,
        theme: String = "Theme.Material.Light.NoActionBar"
    ) {
        val goldenFile = File(goldenDir, "$name.png")
        val actualImage = LayoutRenderer(environment!!, config).use { renderer ->
            renderer.render(layoutXml, theme).image
        }

        if (recordMode || !goldenFile.exists()) {
            // Record mode: save the rendered image as golden
            ImageIO.write(actualImage, "PNG", goldenFile)
            println("📸 Recorded golden: ${goldenFile.name} (${actualImage.width}x${actualImage.height})")

            if (!recordMode) {
                fail<Unit>("Golden image not found: ${goldenFile.absolutePath}. Run with -Dtestpilot.record=true to create it.")
            }
        } else {
            // Verify mode: compare against golden
            val goldenImage = ImageIO.read(goldenFile)

            // Check dimensions first
            assertEquals(
                goldenImage.width, actualImage.width,
                "Width mismatch for $name"
            )
            assertEquals(
                goldenImage.height, actualImage.height,
                "Height mismatch for $name"
            )

            // Compare pixels
            val comparison = ImageComparator.compare(goldenImage, actualImage)

            if (!comparison.matches(TOLERANCE)) {
                // Save actual and diff for debugging
                val actualFile = File(goldenDir, "${name}_actual.png")
                val diffFile = File(goldenDir, "${name}_diff.png")

                ImageIO.write(actualImage, "PNG", actualFile)
                comparison.diffImage?.let { ImageIO.write(it, "PNG", diffFile) }

                fail<Unit>(
                    "Golden image mismatch for '$name':\n" +
                    "  Diff: ${String.format("%.2f", comparison.diffPercentage * 100)}% " +
                    "(${comparison.diffPixelCount} of ${comparison.totalPixels} pixels)\n" +
                    "  Tolerance: ${String.format("%.2f", TOLERANCE * 100)}%\n" +
                    "  Golden: ${goldenFile.absolutePath}\n" +
                    "  Actual: ${actualFile.absolutePath}\n" +
                    "  Diff: ${diffFile.absolutePath}"
                )
            }
        }
    }
}
