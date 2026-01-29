package io.johnsonlee.testpilot.renderer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File

class ImageComparatorTest {

    @Test
    fun `identical images should have 0% diff`() {
        val image1 = createSolidImage(100, 100, Color.RED)
        val image2 = createSolidImage(100, 100, Color.RED)

        val result = ImageComparator.compare(image1, image2)

        assertEquals(0.0, result.diffPercentage)
        assertEquals(0, result.diffPixelCount)
        assertEquals(10000, result.totalPixels)
        assertTrue(result.matches())
        assertNull(result.diffImage) // No diff image when identical
    }

    @Test
    fun `completely different images should have 100% diff`() {
        val image1 = createSolidImage(100, 100, Color.RED)
        val image2 = createSolidImage(100, 100, Color.BLUE)

        val result = ImageComparator.compare(image1, image2)

        assertEquals(1.0, result.diffPercentage)
        assertEquals(10000, result.diffPixelCount)
        assertFalse(result.matches())
        assertNotNull(result.diffImage)
    }

    @Test
    fun `half different images should have 50% diff`() {
        val image1 = createSolidImage(100, 100, Color.RED)
        val image2 = createSolidImage(100, 100, Color.RED)

        // Make bottom half blue in image2
        for (y in 50 until 100) {
            for (x in 0 until 100) {
                image2.setRGB(x, y, Color.BLUE.rgb)
            }
        }

        val result = ImageComparator.compare(image1, image2)

        assertEquals(0.5, result.diffPercentage)
        assertEquals(5000, result.diffPixelCount)
        assertFalse(result.matches())
        assertTrue(result.matches(0.5)) // Matches with 50% tolerance
    }

    @Test
    fun `color tolerance should allow minor differences`() {
        val image1 = createSolidImage(10, 10, Color(100, 100, 100))
        val image2 = createSolidImage(10, 10, Color(105, 105, 105)) // 5 units different

        // Without tolerance, images should differ
        val resultNoTolerance = ImageComparator.compare(image1, image2, colorTolerance = 0)
        assertEquals(1.0, resultNoTolerance.diffPercentage)

        // With tolerance of 5, images should match
        val resultWithTolerance = ImageComparator.compare(image1, image2, colorTolerance = 5)
        assertEquals(0.0, resultWithTolerance.diffPercentage)

        // With tolerance of 4, images should still differ
        val resultLowTolerance = ImageComparator.compare(image1, image2, colorTolerance = 4)
        assertEquals(1.0, resultLowTolerance.diffPercentage)
    }

    @Test
    fun `color tolerance should check all channels`() {
        val image1 = createSolidImage(10, 10, Color(100, 100, 100))
        val image2 = createSolidImage(10, 10, Color(100, 100, 110)) // Only blue differs by 10

        val resultWithTolerance = ImageComparator.compare(image1, image2, colorTolerance = 10)
        assertEquals(0.0, resultWithTolerance.diffPercentage)

        val resultLowTolerance = ImageComparator.compare(image1, image2, colorTolerance = 9)
        assertEquals(1.0, resultLowTolerance.diffPercentage)
    }

    @Test
    fun `different dimensions should throw exception`() {
        val image1 = createSolidImage(100, 100, Color.RED)
        val image2 = createSolidImage(200, 100, Color.RED)

        assertThrows(IllegalArgumentException::class.java) {
            ImageComparator.compare(image1, image2)
        }
    }

    @Test
    fun `diff image should highlight differences in red`() {
        val image1 = createSolidImage(10, 10, Color.WHITE)
        val image2 = createSolidImage(10, 10, Color.WHITE)

        // Make one pixel different
        image2.setRGB(5, 5, Color.BLACK.rgb)

        val result = ImageComparator.compare(image1, image2)

        assertNotNull(result.diffImage)
        assertEquals(1, result.diffPixelCount)

        // Check that the different pixel is red in diff image
        val diffPixel = result.diffImage!!.getRGB(5, 5)
        assertEquals(Color.RED.rgb, diffPixel)
    }

    @Test
    fun `generateDiffImage false should not create diff image`() {
        val image1 = createSolidImage(100, 100, Color.RED)
        val image2 = createSolidImage(100, 100, Color.BLUE)

        val result = ImageComparator.compare(image1, image2, generateDiffImage = false)

        assertEquals(1.0, result.diffPercentage)
        assertNull(result.diffImage)
    }

    @Test
    fun `matches with tolerance should work correctly`() {
        val result = ImageComparator.ComparisonResult(
            diffPercentage = 0.05,
            diffPixelCount = 500,
            totalPixels = 10000,
            diffImage = null
        )

        assertFalse(result.matches(0.0))
        assertFalse(result.matches(0.04))
        assertTrue(result.matches(0.05))
        assertTrue(result.matches(0.1))
    }

    @Test
    fun `empty images should compare correctly`() {
        val image1 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val image2 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

        val result = ImageComparator.compare(image1, image2)

        assertEquals(0.0, result.diffPercentage)
        assertEquals(1, result.totalPixels)
    }

    @Test
    fun `alpha channel differences should be detected`() {
        val image1 = createSolidImage(10, 10, Color(255, 0, 0, 255)) // Opaque red
        val image2 = createSolidImage(10, 10, Color(255, 0, 0, 128)) // Semi-transparent red

        val result = ImageComparator.compare(image1, image2, colorTolerance = 0)
        assertEquals(1.0, result.diffPercentage)

        // With high tolerance, should match
        val resultWithTolerance = ImageComparator.compare(image1, image2, colorTolerance = 127)
        assertEquals(0.0, resultWithTolerance.diffPercentage)
    }

    private fun createSolidImage(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }
}
