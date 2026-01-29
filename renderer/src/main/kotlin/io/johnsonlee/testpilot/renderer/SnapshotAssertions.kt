package io.johnsonlee.testpilot.renderer

import java.awt.image.BufferedImage

/**
 * Assertion utilities for snapshot testing.
 *
 * Usage with JUnit:
 * ```kotlin
 * class MyScreenshotTest {
 *     private val snapshots = SnapshotManager(File("src/test/snapshots"))
 *
 *     @Test
 *     fun `login screen should match golden`() {
 *         val screenshot = session.takeScreenshot(loginLayoutXml)
 *         screenshot.assertMatchesSnapshot(snapshots, "login_screen")
 *     }
 * }
 * ```
 */

/**
 * Asserts that this image matches the golden snapshot.
 *
 * @param manager The snapshot manager.
 * @param name The snapshot name.
 * @param tolerance The maximum allowed percentage of different pixels (0.0 to 1.0).
 * @param colorTolerance The tolerance for color differences per channel (0-255).
 * @param recordIfMissing If true, records the image as golden when no golden exists.
 * @throws AssertionError if the images don't match.
 */
fun BufferedImage.assertMatchesSnapshot(
    manager: SnapshotManager,
    name: String,
    tolerance: Double = 0.0,
    colorTolerance: Int = 0,
    recordIfMissing: Boolean = false
) {
    // Auto-record if golden doesn't exist and flag is set
    if (recordIfMissing && !manager.exists(name)) {
        manager.record(name, this)
        return
    }

    val result = manager.verify(name, this, tolerance, colorTolerance)

    if (!result.matches) {
        val message = buildString {
            append("Snapshot verification failed for '$name'")

            result.error?.let {
                append(": $it")
                return@buildString
            }

            result.comparison?.let { comparison ->
                append("\n")
                append("  Diff: ${String.format("%.2f", comparison.diffPercentage * 100)}% ")
                append("(${comparison.diffPixelCount} of ${comparison.totalPixels} pixels)")
                append("\n")
                append("  Tolerance: ${String.format("%.2f", tolerance * 100)}%")
            }
        }
        throw AssertionError(message)
    }
}

/**
 * Asserts that two images are visually equal.
 *
 * @param expected The expected image.
 * @param tolerance The maximum allowed percentage of different pixels (0.0 to 1.0).
 * @param colorTolerance The tolerance for color differences per channel (0-255).
 * @throws AssertionError if the images don't match.
 */
fun BufferedImage.assertVisuallyEquals(
    expected: BufferedImage,
    tolerance: Double = 0.0,
    colorTolerance: Int = 0
) {
    val result = ImageComparator.compare(expected, this, colorTolerance)

    if (!result.matches(tolerance)) {
        throw AssertionError(
            "Images are not visually equal. " +
                "Diff: ${String.format("%.2f", result.diffPercentage * 100)}% " +
                "(${result.diffPixelCount} of ${result.totalPixels} pixels differ). " +
                "Tolerance: ${String.format("%.2f", tolerance * 100)}%"
        )
    }
}

/**
 * Records this image as a golden snapshot.
 * Useful for initially creating golden images.
 *
 * @param manager The snapshot manager.
 * @param name The snapshot name.
 */
fun BufferedImage.recordAsSnapshot(manager: SnapshotManager, name: String) {
    manager.record(name, this)
}
