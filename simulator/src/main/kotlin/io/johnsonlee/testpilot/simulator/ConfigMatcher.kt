package io.johnsonlee.testpilot.simulator


/**
 * Implements Android's best-match resource configuration algorithm.
 *
 * Given a set of resource configuration variants and a device configuration,
 * eliminates non-matching configs using qualifier priority ordering until
 * a single best match remains.
 */
object ConfigMatcher {

    /**
     * Finds the best matching config from a list of candidates.
     * Returns the index of the best match, or -1 if no candidates match.
     */
    fun <T> bestMatch(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        device: DeviceConfiguration
    ): Int {
        if (candidates.isEmpty()) return -1
        if (candidates.size == 1) return 0

        var remaining = candidates.indices.toMutableList()

        // Step 1: Eliminate configs that contradict the device
        remaining = remaining.filter { i ->
            !contradicts(candidates[i].first, device)
        }.toMutableList()

        if (remaining.isEmpty()) return -1
        if (remaining.size == 1) return remaining[0]

        // Step 2: Apply qualifier priority filtering
        // Order: locale > nightMode > density > orientation > screenSize > sdkVersion

        remaining = filterByLocale(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        remaining = filterByNightMode(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        remaining = filterByDensity(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        remaining = filterByOrientation(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        remaining = filterByScreenSize(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        remaining = filterBySdkVersion(candidates, remaining, device)
        if (remaining.size == 1) return remaining[0]

        // If still multiple matches, prefer the first (default) config
        return remaining[0]
    }

    /**
     * Returns true if the config contradicts the device — i.e., specifies a
     * qualifier value that the device doesn't match.
     */
    private fun contradicts(config: ResourcesParser.ResTableConfig, device: DeviceConfiguration): Boolean {
        // Language: if config specifies a language, device must match
        if (config.language.isNotEmpty() && config.language != device.locale) return true

        // Country: if config specifies a country, device must match
        if (config.country.isNotEmpty() && config.country != device.country) return true

        // Orientation: if config specifies orientation, device must match
        if (config.orientation != 0 && config.orientation != device.orientation) return true

        // Night mode: if config specifies night mode, device must match
        val configNight = config.nightMode
        if (configNight != 0 && configNight != device.nightMode) return true

        // Screen layout size: if config specifies a size, device must be at least that large
        val configSize = config.screenLayout and 0x0F
        val deviceSize = device.screenLayout and 0x0F
        if (configSize != 0 && configSize > deviceSize) return true

        // SDK version: config must not require a higher SDK than device
        if (config.sdkVersion != 0 && config.sdkVersion > device.sdkVersion) return true

        return false
    }

    private fun <T> filterByLocale(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val matching = remaining.filter { i ->
            candidates[i].first.language == device.locale
        }
        return if (matching.isNotEmpty()) matching.toMutableList() else remaining
    }

    private fun <T> filterByNightMode(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val matching = remaining.filter { i ->
            candidates[i].first.nightMode == device.nightMode
        }
        return if (matching.isNotEmpty()) matching.toMutableList() else remaining
    }

    private fun <T> filterByDensity(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val withDensity = remaining.filter { i -> candidates[i].first.density != 0 }
        if (withDensity.isEmpty()) return remaining

        // Find the closest density, preferring higher over lower
        val best = withDensity.minByOrNull { i ->
            val d = candidates[i].first.density
            val diff = d - device.density
            if (diff >= 0) diff else -diff * 2  // penalize scaling up (lower density)
        } ?: return remaining

        return mutableListOf(best)
    }

    private fun <T> filterByOrientation(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val matching = remaining.filter { i ->
            candidates[i].first.orientation == device.orientation
        }
        return if (matching.isNotEmpty()) matching.toMutableList() else remaining
    }

    private fun <T> filterByScreenSize(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val deviceSize = device.screenLayout and 0x0F
        val matching = remaining.filter { i ->
            val configSize = candidates[i].first.screenLayout and 0x0F
            configSize != 0 && configSize <= deviceSize
        }
        if (matching.isEmpty()) return remaining
        // Prefer the largest matching screen size
        val bestSize = matching.maxOf { i -> candidates[i].first.screenLayout and 0x0F }
        return matching.filter { i ->
            (candidates[i].first.screenLayout and 0x0F) == bestSize
        }.toMutableList()
    }

    private fun <T> filterBySdkVersion(
        candidates: List<Pair<ResourcesParser.ResTableConfig, T>>,
        remaining: MutableList<Int>,
        device: DeviceConfiguration
    ): MutableList<Int> {
        val withSdk = remaining.filter { i -> candidates[i].first.sdkVersion != 0 }
        if (withSdk.isEmpty()) return remaining
        // Prefer the highest SDK that doesn't exceed device
        val bestSdk = withSdk.maxOf { i -> candidates[i].first.sdkVersion }
        return withSdk.filter { i ->
            candidates[i].first.sdkVersion == bestSdk
        }.toMutableList()
    }
}
