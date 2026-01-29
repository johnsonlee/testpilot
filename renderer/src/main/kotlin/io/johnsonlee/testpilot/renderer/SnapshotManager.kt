package io.johnsonlee.testpilot.renderer

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Manages golden images for visual regression testing.
 *
 * Usage:
 * ```kotlin
 * val manager = SnapshotManager(File("src/test/snapshots"))
 *
 * // Record a new golden image
 * manager.record("login_screen", screenshot)
 *
 * // Verify against golden image
 * val result = manager.verify("login_screen", actualScreenshot)
 * if (!result.matches) {
 *     // Save diff image for debugging
 *     manager.saveDiff("login_screen", result.diffImage!!)
 * }
 * ```
 */
class SnapshotManager(
    /**
     * The directory where golden images are stored.
     */
    private val snapshotsDir: File,

    /**
     * The directory where diff images are saved on failure.
     */
    private val failuresDir: File = File(snapshotsDir.parentFile, "failures")
) {
    init {
        snapshotsDir.mkdirs()
    }

    /**
     * Result of a snapshot verification.
     */
    data class VerifyResult(
        /**
         * Whether the actual image matches the golden image within tolerance.
         */
        val matches: Boolean,

        /**
         * The comparison result with detailed diff information.
         */
        val comparison: ImageComparator.ComparisonResult?,

        /**
         * Error message if verification failed for reasons other than mismatch.
         */
        val error: String? = null
    )

    /**
     * Records a golden image.
     *
     * @param name The snapshot name (without extension).
     * @param image The image to save as golden.
     */
    fun record(name: String, image: BufferedImage) {
        val file = goldenFile(name)
        file.parentFile?.mkdirs()
        ImageIO.write(image, "PNG", file)
    }

    /**
     * Verifies an actual image against the golden image.
     *
     * @param name The snapshot name.
     * @param actual The actual image to verify.
     * @param tolerance The maximum allowed percentage of different pixels (0.0 to 1.0).
     * @param colorTolerance The tolerance for color differences per channel (0-255).
     * @return The verification result.
     */
    fun verify(
        name: String,
        actual: BufferedImage,
        tolerance: Double = 0.0,
        colorTolerance: Int = 0
    ): VerifyResult {
        val goldenFile = goldenFile(name)

        if (!goldenFile.exists()) {
            return VerifyResult(
                matches = false,
                comparison = null,
                error = "Golden image not found: ${goldenFile.absolutePath}. " +
                    "Run in record mode to create it."
            )
        }

        val golden = try {
            ImageIO.read(goldenFile)
        } catch (e: Exception) {
            return VerifyResult(
                matches = false,
                comparison = null,
                error = "Failed to read golden image: ${e.message}"
            )
        }

        // Check dimensions
        if (golden.width != actual.width || golden.height != actual.height) {
            return VerifyResult(
                matches = false,
                comparison = null,
                error = "Image dimensions mismatch. Golden: ${golden.width}x${golden.height}, " +
                    "Actual: ${actual.width}x${actual.height}"
            )
        }

        val comparison = ImageComparator.compare(golden, actual, colorTolerance)
        val matches = comparison.matches(tolerance)

        // Save failure artifacts if mismatch
        if (!matches) {
            saveFailureArtifacts(name, actual, comparison)
        }

        return VerifyResult(
            matches = matches,
            comparison = comparison
        )
    }

    /**
     * Checks if a golden image exists.
     */
    fun exists(name: String): Boolean = goldenFile(name).exists()

    /**
     * Loads a golden image.
     */
    fun load(name: String): BufferedImage? {
        val file = goldenFile(name)
        return if (file.exists()) ImageIO.read(file) else null
    }

    /**
     * Deletes a golden image.
     */
    fun delete(name: String): Boolean = goldenFile(name).delete()

    /**
     * Lists all golden image names.
     */
    fun list(): List<String> {
        return snapshotsDir.listFiles { f -> f.extension == "png" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    private fun goldenFile(name: String): File = File(snapshotsDir, "$name.png")

    private fun saveFailureArtifacts(
        name: String,
        actual: BufferedImage,
        comparison: ImageComparator.ComparisonResult
    ) {
        failuresDir.mkdirs()

        // Save actual image
        ImageIO.write(actual, "PNG", File(failuresDir, "${name}_actual.png"))

        // Save diff image
        comparison.diffImage?.let { diff ->
            ImageIO.write(diff, "PNG", File(failuresDir, "${name}_diff.png"))
        }

        // Copy golden for easy comparison
        val golden = goldenFile(name)
        if (golden.exists()) {
            golden.copyTo(File(failuresDir, "${name}_golden.png"), overwrite = true)
        }
    }
}
