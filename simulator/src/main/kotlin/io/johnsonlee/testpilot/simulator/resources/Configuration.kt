package io.johnsonlee.testpilot.simulator.resources

/**
 * Describes the device configuration: locale, screen density, orientation, night mode, etc.
 * Used by the resource resolution system to select the best-matching resource variant.
 */
data class Configuration(
    val locale: String = "en",
    val country: String = "",
    val orientation: Int = ORIENTATION_PORTRAIT,
    val density: Int = DENSITY_MDPI,
    val nightMode: Int = NIGHT_MODE_NO,
    val screenWidthDp: Int = 320,
    val screenHeightDp: Int = 480,
    val screenLayout: Int = SCREENLAYOUT_SIZE_NORMAL,
    val sdkVersion: Int = 33
) {
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2

        const val DENSITY_LDPI = 120
        const val DENSITY_MDPI = 160
        const val DENSITY_HDPI = 240
        const val DENSITY_XHDPI = 320
        const val DENSITY_XXHDPI = 480
        const val DENSITY_XXXHDPI = 640

        const val NIGHT_MODE_NO = 1
        const val NIGHT_MODE_YES = 2

        const val SCREENLAYOUT_SIZE_SMALL = 1
        const val SCREENLAYOUT_SIZE_NORMAL = 2
        const val SCREENLAYOUT_SIZE_LARGE = 3
        const val SCREENLAYOUT_SIZE_XLARGE = 4

        val DEFAULT = Configuration()
    }
}
