package io.johnsonlee.testpilot.renderer

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Compares two images and calculates their difference.
 */
object ImageComparator {

    /**
     * Result of comparing two images.
     */
    data class ComparisonResult(
        /**
         * The percentage of pixels that differ (0.0 to 1.0).
         */
        val diffPercentage: Double,

        /**
         * The number of pixels that differ.
         */
        val diffPixelCount: Int,

        /**
         * The total number of pixels compared.
         */
        val totalPixels: Int,

        /**
         * A diff image highlighting the differences in red.
         * Null if the images are identical.
         */
        val diffImage: BufferedImage?
    ) {
        /**
         * Returns true if the images match within the given tolerance.
         */
        fun matches(tolerance: Double = 0.0): Boolean = diffPercentage <= tolerance
    }

    /**
     * Compares two images pixel by pixel.
     *
     * @param expected The expected (golden) image.
     * @param actual The actual image to compare.
     * @param colorTolerance The tolerance for color differences per channel (0-255).
     * @param generateDiffImage Whether to generate a diff image.
     * @return The comparison result.
     * @throws IllegalArgumentException if images have different dimensions.
     */
    fun compare(
        expected: BufferedImage,
        actual: BufferedImage,
        colorTolerance: Int = 0,
        generateDiffImage: Boolean = true
    ): ComparisonResult {
        require(expected.width == actual.width && expected.height == actual.height) {
            "Image dimensions must match. Expected: ${expected.width}x${expected.height}, " +
                "Actual: ${actual.width}x${actual.height}"
        }

        val width = expected.width
        val height = expected.height
        val totalPixels = width * height

        var diffCount = 0
        val diffImage = if (generateDiffImage) {
            BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        } else null

        for (y in 0 until height) {
            for (x in 0 until width) {
                val expectedPixel = expected.getRGB(x, y)
                val actualPixel = actual.getRGB(x, y)

                val isDifferent = !pixelsMatch(expectedPixel, actualPixel, colorTolerance)

                if (isDifferent) {
                    diffCount++
                    diffImage?.setRGB(x, y, DIFF_COLOR)
                } else {
                    // Dim the matching pixels in diff image
                    diffImage?.setRGB(x, y, dimPixel(actualPixel))
                }
            }
        }

        val diffPercentage = if (totalPixels > 0) {
            diffCount.toDouble() / totalPixels
        } else 0.0

        return ComparisonResult(
            diffPercentage = diffPercentage,
            diffPixelCount = diffCount,
            totalPixels = totalPixels,
            diffImage = if (diffCount > 0) diffImage else null
        )
    }

    /**
     * Checks if two pixels match within the given tolerance.
     */
    private fun pixelsMatch(pixel1: Int, pixel2: Int, tolerance: Int): Boolean {
        if (pixel1 == pixel2) return true
        if (tolerance == 0) return false

        val a1 = (pixel1 shr 24) and 0xFF
        val r1 = (pixel1 shr 16) and 0xFF
        val g1 = (pixel1 shr 8) and 0xFF
        val b1 = pixel1 and 0xFF

        val a2 = (pixel2 shr 24) and 0xFF
        val r2 = (pixel2 shr 16) and 0xFF
        val g2 = (pixel2 shr 8) and 0xFF
        val b2 = pixel2 and 0xFF

        return abs(a1 - a2) <= tolerance &&
            abs(r1 - r2) <= tolerance &&
            abs(g1 - g2) <= tolerance &&
            abs(b1 - b2) <= tolerance
    }

    /**
     * Dims a pixel for the diff image background.
     */
    private fun dimPixel(pixel: Int): Int {
        val a = (pixel shr 24) and 0xFF
        val r = ((pixel shr 16) and 0xFF) / 3
        val g = ((pixel shr 8) and 0xFF) / 3
        val b = (pixel and 0xFF) / 3
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // Red color for highlighting differences
    private val DIFF_COLOR = Color.RED.rgb
}
