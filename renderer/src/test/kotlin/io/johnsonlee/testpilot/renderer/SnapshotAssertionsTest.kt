package io.johnsonlee.testpilot.renderer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File

class SnapshotAssertionsTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var snapshotsDir: File
    private lateinit var manager: SnapshotManager

    @BeforeEach
    fun setup() {
        snapshotsDir = File(tempDir, "snapshots")
        manager = SnapshotManager(snapshotsDir)
    }

    // ==================== assertMatchesSnapshot tests ====================

    @Test
    fun `assertMatchesSnapshot should pass for matching images`() {
        val image = createTestImage(100, 100, Color.RED)
        manager.record("matching", image)

        // Should not throw
        image.assertMatchesSnapshot(manager, "matching")
    }

    @Test
    fun `assertMatchesSnapshot should fail for different images`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.BLUE)
        manager.record("different", golden)

        val exception = assertThrows(AssertionError::class.java) {
            actual.assertMatchesSnapshot(manager, "different")
        }

        assertTrue(exception.message!!.contains("Snapshot verification failed"))
        assertTrue(exception.message!!.contains("different"))
    }

    @Test
    fun `assertMatchesSnapshot should pass with tolerance`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.RED)

        // Make 3% of pixels different
        for (y in 0 until 3) {
            for (x in 0 until 100) {
                actual.setRGB(x, y, Color.BLUE.rgb)
            }
        }

        manager.record("tolerance", golden)

        // Should pass with 5% tolerance
        actual.assertMatchesSnapshot(manager, "tolerance", tolerance = 0.05)
    }

    @Test
    fun `assertMatchesSnapshot should fail when exceeding tolerance`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.RED)

        // Make 10% of pixels different
        for (y in 0 until 10) {
            for (x in 0 until 100) {
                actual.setRGB(x, y, Color.BLUE.rgb)
            }
        }

        manager.record("exceed_tolerance", golden)

        val exception = assertThrows(AssertionError::class.java) {
            actual.assertMatchesSnapshot(manager, "exceed_tolerance", tolerance = 0.05)
        }

        assertTrue(exception.message!!.contains("10.00%"))
        assertTrue(exception.message!!.contains("5.00%"))
    }

    @Test
    fun `assertMatchesSnapshot should fail for missing golden`() {
        val actual = createTestImage(100, 100, Color.RED)

        val exception = assertThrows(AssertionError::class.java) {
            actual.assertMatchesSnapshot(manager, "missing")
        }

        assertTrue(exception.message!!.contains("Golden image not found"))
    }

    @Test
    fun `assertMatchesSnapshot should record if missing with flag`() {
        val actual = createTestImage(100, 100, Color.RED)

        // Should not throw and should record the image
        actual.assertMatchesSnapshot(manager, "new_golden", recordIfMissing = true)

        assertTrue(manager.exists("new_golden"))
    }

    @Test
    fun `assertMatchesSnapshot should not record if golden exists`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.BLUE)
        manager.record("existing", golden)

        // Should still fail even with recordIfMissing=true
        val exception = assertThrows(AssertionError::class.java) {
            actual.assertMatchesSnapshot(manager, "existing", recordIfMissing = true)
        }

        assertTrue(exception.message!!.contains("Snapshot verification failed"))
    }

    @Test
    fun `assertMatchesSnapshot with color tolerance should pass`() {
        val golden = createTestImage(100, 100, Color(100, 100, 100))
        val actual = createTestImage(100, 100, Color(105, 105, 105))
        manager.record("color_tol", golden)

        // Should pass with color tolerance of 5
        actual.assertMatchesSnapshot(manager, "color_tol", colorTolerance = 5)
    }

    @Test
    fun `assertMatchesSnapshot should fail for dimension mismatch`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(200, 100, Color.RED)
        manager.record("dimension", golden)

        val exception = assertThrows(AssertionError::class.java) {
            actual.assertMatchesSnapshot(manager, "dimension")
        }

        assertTrue(exception.message!!.contains("dimension"))
    }

    // ==================== assertVisuallyEquals tests ====================

    @Test
    fun `assertVisuallyEquals should pass for identical images`() {
        val image1 = createTestImage(100, 100, Color.RED)
        val image2 = createTestImage(100, 100, Color.RED)

        // Should not throw
        image1.assertVisuallyEquals(image2)
    }

    @Test
    fun `assertVisuallyEquals should fail for different images`() {
        val image1 = createTestImage(100, 100, Color.RED)
        val image2 = createTestImage(100, 100, Color.BLUE)

        val exception = assertThrows(AssertionError::class.java) {
            image1.assertVisuallyEquals(image2)
        }

        assertTrue(exception.message!!.contains("not visually equal"))
        assertTrue(exception.message!!.contains("100.00%"))
    }

    @Test
    fun `assertVisuallyEquals should pass with tolerance`() {
        val image1 = createTestImage(100, 100, Color.RED)
        val image2 = createTestImage(100, 100, Color.RED)

        // Make 5% different
        for (y in 0 until 5) {
            for (x in 0 until 100) {
                image2.setRGB(x, y, Color.BLUE.rgb)
            }
        }

        // Should pass with 5% tolerance
        image1.assertVisuallyEquals(image2, tolerance = 0.05)
    }

    @Test
    fun `assertVisuallyEquals with color tolerance should pass`() {
        val image1 = createTestImage(100, 100, Color(100, 100, 100))
        val image2 = createTestImage(100, 100, Color(103, 103, 103))

        // Should pass with color tolerance
        image1.assertVisuallyEquals(image2, colorTolerance = 3)
    }

    @Test
    fun `assertVisuallyEquals should fail for dimension mismatch`() {
        val image1 = createTestImage(100, 100, Color.RED)
        val image2 = createTestImage(200, 100, Color.RED)

        assertThrows(IllegalArgumentException::class.java) {
            image1.assertVisuallyEquals(image2)
        }
    }

    // ==================== recordAsSnapshot tests ====================

    @Test
    fun `recordAsSnapshot should save image`() {
        val image = createTestImage(100, 100, Color.GREEN)

        image.recordAsSnapshot(manager, "recorded")

        assertTrue(manager.exists("recorded"))
        val loaded = manager.load("recorded")
        assertNotNull(loaded)
        assertEquals(100, loaded!!.width)
    }

    @Test
    fun `recordAsSnapshot should overwrite existing`() {
        val image1 = createTestImage(100, 100, Color.RED)
        val image2 = createTestImage(100, 100, Color.BLUE)

        image1.recordAsSnapshot(manager, "overwrite")
        image2.recordAsSnapshot(manager, "overwrite")

        val loaded = manager.load("overwrite")
        // Check that it's blue (second image)
        assertEquals(Color.BLUE.rgb, loaded!!.getRGB(50, 50))
    }

    private fun createTestImage(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }
}
