package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.resources.Configuration
import io.johnsonlee.testpilot.simulator.resources.ResourceResolver

/**
 * Implements [ResourceResolver] by resolving resources from a parsed [ResourcesParser.ResourceTable]
 * using [ConfigMatcher] to select the best configuration variant.
 */
class ResourceTableResolver(
    private val resourceTable: ResourcesParser.ResourceTable,
    private val configuration: Configuration
) : ResourceResolver {

    override fun resolveString(resId: Int): String? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.StringValue -> v.value
            else -> null
        }
    }

    override fun resolveLayout(resId: Int): String? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.StringValue -> v.value
            else -> null
        }
    }

    override fun resolveInteger(resId: Int): Int? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.IntValue -> v.value
            else -> null
        }
    }

    override fun resolveBoolean(resId: Int): Boolean? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.BoolValue -> v.value
            else -> null
        }
    }

    override fun resolveColor(resId: Int): Int? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.ColorValue -> v.value
            else -> null
        }
    }

    override fun resolveDimension(resId: Int): Float? {
        val entry = resolveEntry(resId) ?: return null
        return when (val v = entry.value) {
            is ResourcesParser.ResourceValue.DimensionValue -> v.value
            else -> null
        }
    }

    private fun resolveEntry(resId: Int): ResourcesParser.ResourceEntry? {
        val variants = resourceTable.getResourceVariants(resId)
        if (variants.isEmpty()) return null
        if (variants.size == 1) return variants[0].second

        val bestIndex = ConfigMatcher.bestMatch(variants, configuration)
        if (bestIndex < 0) return null
        return variants[bestIndex].second
    }
}
