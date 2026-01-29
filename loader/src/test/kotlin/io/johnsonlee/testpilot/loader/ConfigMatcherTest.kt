package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.resources.Configuration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigMatcherTest {

    private fun config(
        language: String = "",
        country: String = "",
        density: Int = 0,
        orientation: Int = 0,
        uiMode: Int = 0,
        screenLayout: Int = 0,
        sdkVersion: Int = 0
    ) = ResourcesParser.ResTableConfig(
        size = 28,
        language = language,
        country = country,
        density = density,
        orientation = orientation,
        uiMode = uiMode,
        screenLayout = screenLayout,
        sdkVersion = sdkVersion
    )

    private fun <T> candidates(vararg pairs: Pair<ResourcesParser.ResTableConfig, T>) = pairs.toList()

    @Test
    fun `should return only candidate when single variant`() {
        val variants = candidates(config() to "default")
        val device = Configuration.DEFAULT

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(0)
    }

    @Test
    fun `should return -1 for empty candidates`() {
        val variants = emptyList<Pair<ResourcesParser.ResTableConfig, String>>()
        val device = Configuration.DEFAULT

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(-1)
    }

    @Test
    fun `should select matching locale over default`() {
        val variants = candidates(
            config() to "default",
            config(language = "en") to "english",
            config(language = "es") to "spanish"
        )
        val device = Configuration(locale = "es")

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(2)
        assertThat(variants[best].second).isEqualTo("spanish")
    }

    @Test
    fun `should fall back to default when locale does not match`() {
        val variants = candidates(
            config() to "default",
            config(language = "fr") to "french",
            config(language = "de") to "german"
        )
        val device = Configuration(locale = "ja")

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(0)
        assertThat(variants[best].second).isEqualTo("default")
    }

    @Test
    fun `should select night mode variant`() {
        // uiMode night bits: nightMode is (uiMode shr 4) and 0x3
        // NIGHT_MODE_YES = 2, so uiMode = 2 shl 4 = 0x20
        val variants = candidates(
            config() to "default",
            config(uiMode = 0x20) to "night"
        )
        val device = Configuration(nightMode = Configuration.NIGHT_MODE_YES)

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(1)
        assertThat(variants[best].second).isEqualTo("night")
    }

    @Test
    fun `should prefer closest density`() {
        val variants = candidates(
            config() to "default",
            config(density = 160) to "mdpi",
            config(density = 240) to "hdpi",
            config(density = 320) to "xhdpi"
        )
        val device = Configuration(density = Configuration.DENSITY_HDPI)

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(2)
        assertThat(variants[best].second).isEqualTo("hdpi")
    }

    @Test
    fun `should prefer higher density over lower when no exact match`() {
        val variants = candidates(
            config(density = 160) to "mdpi",
            config(density = 320) to "xhdpi"
        )
        // Device is hdpi (240) - xhdpi (320) is closer because lower density is penalized (scaling up)
        val device = Configuration(density = Configuration.DENSITY_HDPI)

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(best).isEqualTo(1)
        assertThat(variants[best].second).isEqualTo("xhdpi")
    }

    @Test
    fun `should eliminate contradicting orientation`() {
        val variants = candidates(
            config() to "default",
            config(orientation = 1) to "portrait",
            config(orientation = 2) to "landscape"
        )
        val device = Configuration(orientation = Configuration.ORIENTATION_LANDSCAPE)

        val best = ConfigMatcher.bestMatch(variants, device)
        // portrait contradicts landscape device, should be eliminated
        assertThat(variants[best].second).isNotEqualTo("portrait")
    }

    @Test
    fun `should eliminate configs requiring higher SDK`() {
        val variants = candidates(
            config() to "default",
            config(sdkVersion = 21) to "api21",
            config(sdkVersion = 99) to "api99"
        )
        val device = Configuration(sdkVersion = 33)

        val best = ConfigMatcher.bestMatch(variants, device)
        // api99 contradicts device SDK 33
        assertThat(variants[best].second).isNotEqualTo("api99")
    }

    @Test
    fun `should prefer highest non-exceeding SDK`() {
        val variants = candidates(
            config() to "default",
            config(sdkVersion = 21) to "api21",
            config(sdkVersion = 28) to "api28"
        )
        val device = Configuration(sdkVersion = 33)

        val best = ConfigMatcher.bestMatch(variants, device)
        assertThat(variants[best].second).isEqualTo("api28")
    }

    @Test
    fun `locale should take priority over density`() {
        val variants = candidates(
            config(language = "en", density = 160) to "en-mdpi",
            config(language = "es", density = 320) to "es-xhdpi",
            config(language = "es", density = 160) to "es-mdpi"
        )
        val device = Configuration(locale = "es", density = Configuration.DENSITY_XHDPI)

        val best = ConfigMatcher.bestMatch(variants, device)
        // Should select es-xhdpi: locale matches AND density matches
        assertThat(variants[best].second).isEqualTo("es-xhdpi")
    }

    @Test
    fun `should select matching screen layout size`() {
        val variants = candidates(
            config() to "default",
            config(screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL) to "normal",
            config(screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE) to "large"
        )
        val device = Configuration(screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE)

        val best = ConfigMatcher.bestMatch(variants, device)
        // Should prefer largest matching size
        assertThat(variants[best].second).isEqualTo("large")
    }

    @Test
    fun `should eliminate screen size larger than device`() {
        val variants = candidates(
            config() to "default",
            config(screenLayout = Configuration.SCREENLAYOUT_SIZE_XLARGE) to "xlarge"
        )
        val device = Configuration(screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL)

        val best = ConfigMatcher.bestMatch(variants, device)
        // xlarge contradicts normal device
        assertThat(variants[best].second).isEqualTo("default")
    }
}
