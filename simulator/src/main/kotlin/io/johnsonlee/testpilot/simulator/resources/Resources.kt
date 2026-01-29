package io.johnsonlee.testpilot.simulator.resources

/**
 * Pluggable resource resolver for configuration-aware resource lookup.
 * Implemented by the loader module to resolve resources from the parsed resource table.
 */
interface ResourceResolver {
    fun resolveString(resId: Int): String?
    fun resolveLayout(resId: Int): String?
    fun resolveInteger(resId: Int): Int?
    fun resolveBoolean(resId: Int): Boolean?
    fun resolveColor(resId: Int): Int?
    fun resolveDimension(resId: Int): Float?
}

/**
 * Class for accessing an application's resources.
 * Supports configuration-aware resource resolution via a pluggable [ResourceResolver].
 */
class Resources(
    val configuration: Configuration = Configuration.DEFAULT
) {
    private val strings = mutableMapOf<Int, String>()
    private val layouts = mutableMapOf<Int, String>()  // resId -> layout XML path

    /**
     * Pluggable resolver set by the loader module.
     */
    var resolver: ResourceResolver? = null

    fun getString(resId: Int): String =
        resolver?.resolveString(resId)
            ?: strings[resId]
            ?: throw ResourceNotFoundException("String resource ID #0x${resId.toString(16)}")

    fun getLayout(resId: Int): String =
        resolver?.resolveLayout(resId)
            ?: layouts[resId]
            ?: throw ResourceNotFoundException("Layout resource ID #0x${resId.toString(16)}")

    fun getInteger(resId: Int): Int =
        resolver?.resolveInteger(resId)
            ?: throw ResourceNotFoundException("Integer resource ID #0x${resId.toString(16)}")

    fun getBoolean(resId: Int): Boolean =
        resolver?.resolveBoolean(resId)
            ?: throw ResourceNotFoundException("Boolean resource ID #0x${resId.toString(16)}")

    fun getColor(resId: Int): Int =
        resolver?.resolveColor(resId)
            ?: throw ResourceNotFoundException("Color resource ID #0x${resId.toString(16)}")

    fun getDimension(resId: Int): Float =
        resolver?.resolveDimension(resId)
            ?: throw ResourceNotFoundException("Dimension resource ID #0x${resId.toString(16)}")

    fun addString(resId: Int, value: String) {
        strings[resId] = value
    }

    fun addLayout(resId: Int, path: String) {
        layouts[resId] = path
    }
}

class ResourceNotFoundException(message: String) : RuntimeException(message)
