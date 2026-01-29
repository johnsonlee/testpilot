package io.johnsonlee.testpilot.renderer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SnapshotManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var snapshotsDir: File
    private lateinit var failuresDir: File
    private lateinit var manager: SnapshotManager

    @BeforeEach
    fun setup() {
        snapshotsDir = File(tempDir, "snapshots")
        failuresDir = File(tempDir, "failures")
        manager = SnapshotManager(snapshotsDir, failuresDir)
    }

    @Test
    fun `record should save image as PNG`() {
        val image = createTestImage(100, 100, Color.RED)

        manager.record("test_image", image)

        val savedFile = File(snapshotsDir, "test_image.png")
        assertTrue(savedFile.exists())

        val loadedImage = ImageIO.read(savedFile)
        assertEquals(100, loadedImage.width)
        assertEquals(100, loadedImage.height)
    }

    @Test
    fun `exists should return true for recorded image`() {
        val image = createTestImage(100, 100, Color.RED)

        assertFalse(manager.exists("test_image"))

        manager.record("test_image", image)

        assertTrue(manager.exists("test_image"))
    }

    @Test
    fun `load should return recorded image`() {
        val image = createTestImage(50, 50, Color.BLUE)
        manager.record("blue_image", image)

        val loaded = manager.load("blue_image")

        assertNotNull(loaded)
        assertEquals(50, loaded!!.width)
        assertEquals(50, loaded.height)
    }

    @Test
    fun `load should return null for non-existent image`() {
        val loaded = manager.load("non_existent")

        assertNull(loaded)
    }

    @Test
    fun `delete should remove image`() {
        val image = createTestImage(100, 100, Color.RED)
        manager.record("to_delete", image)
        assertTrue(manager.exists("to_delete"))

        val deleted = manager.delete("to_delete")

        assertTrue(deleted)
        assertFalse(manager.exists("to_delete"))
    }

    @Test
    fun `delete should return false for non-existent image`() {
        val deleted = manager.delete("non_existent")

        assertFalse(deleted)
    }

    @Test
    fun `list should return all snapshot names`() {
        manager.record("image_a", createTestImage(10, 10, Color.RED))
        manager.record("image_b", createTestImage(10, 10, Color.GREEN))
        manager.record("image_c", createTestImage(10, 10, Color.BLUE))

        val names = manager.list()

        assertEquals(3, names.size)
        assertTrue(names.contains("image_a"))
        assertTrue(names.contains("image_b"))
        assertTrue(names.contains("image_c"))
    }

    @Test
    fun `list should return empty list when no snapshots`() {
        val names = manager.list()

        assertTrue(names.isEmpty())
    }

    @Test
    fun `verify should match identical images`() {
        val image = createTestImage(100, 100, Color.RED)
        manager.record("identical", image)

        val result = manager.verify("identical", image)

        assertTrue(result.matches)
        assertNotNull(result.comparison)
        assertEquals(0.0, result.comparison!!.diffPercentage)
        assertNull(result.error)
    }

    @Test
    fun `verify should fail for different images`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.BLUE)
        manager.record("different", golden)

        val result = manager.verify("different", actual)

        assertFalse(result.matches)
        assertNotNull(result.comparison)
        assertEquals(1.0, result.comparison!!.diffPercentage)
    }

    @Test
    fun `verify should pass with tolerance`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.RED)

        // Make 5% of pixels different
        for (y in 0 until 5) {
            for (x in 0 until 100) {
                actual.setRGB(x, y, Color.BLUE.rgb)
            }
        }

        manager.record("tolerance_test", golden)

        // Should fail with 0% tolerance
        val resultStrict = manager.verify("tolerance_test", actual, tolerance = 0.0)
        assertFalse(resultStrict.matches)

        // Should pass with 5% tolerance
        val resultTolerant = manager.verify("tolerance_test", actual, tolerance = 0.05)
        assertTrue(resultTolerant.matches)
    }

    @Test
    fun `verify should fail for non-existent golden`() {
        val actual = createTestImage(100, 100, Color.RED)

        val result = manager.verify("non_existent", actual)

        assertFalse(result.matches)
        assertNull(result.comparison)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Golden image not found"))
    }

    @Test
    fun `verify should fail for dimension mismatch`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(200, 100, Color.RED)
        manager.record("dimension_test", golden)

        val result = manager.verify("dimension_test", actual)

        assertFalse(result.matches)
        assertNull(result.comparison)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("dimension"))
    }

    @Test
    fun `verify should save failure artifacts on mismatch`() {
        val golden = createTestImage(100, 100, Color.RED)
        val actual = createTestImage(100, 100, Color.BLUE)
        manager.record("failure_test", golden)

        manager.verify("failure_test", actual)

        assertTrue(File(failuresDir, "failure_test_actual.png").exists())
        assertTrue(File(failuresDir, "failure_test_diff.png").exists())
        assertTrue(File(failuresDir, "failure_test_golden.png").exists())
    }

    @Test
    fun `verify should not save failure artifacts on match`() {
        val image = createTestImage(100, 100, Color.RED)
        manager.record("match_test", image)

        manager.verify("match_test", image)

        assertFalse(File(failuresDir, "match_test_actual.png").exists())
        assertFalse(File(failuresDir, "match_test_diff.png").exists())
        assertFalse(File(failuresDir, "match_test_golden.png").exists())
    }

    @Test
    fun `verify with color tolerance should allow minor differences`() {
        val golden = createTestImage(100, 100, Color(100, 100, 100))
        val actual = createTestImage(100, 100, Color(105, 105, 105))
        manager.record("color_tolerance", golden)

        // Without color tolerance, should fail
        val resultStrict = manager.verify("color_tolerance", actual, colorTolerance = 0)
        assertFalse(resultStrict.matches)

        // With color tolerance, should pass
        val resultTolerant = manager.verify("color_tolerance", actual, colorTolerance = 5)
        assertTrue(resultTolerant.matches)
    }

    @Test
    fun `record should create nested directories`() {
        val image = createTestImage(10, 10, Color.RED)

        // Snapshot name with path separator
        manager.record("nested/path/image", image)

        val savedFile = File(snapshotsDir, "nested/path/image.png")
        assertTrue(savedFile.exists())
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
