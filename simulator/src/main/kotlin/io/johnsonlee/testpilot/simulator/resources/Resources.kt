package io.johnsonlee.testpilot.simulator.resources

/**
 * Class for accessing an application's resources.
 * Simplified implementation for the simulator.
 */
class Resources {
    private val strings = mutableMapOf<Int, String>()
    private val layouts = mutableMapOf<Int, String>()  // resId -> layout XML path

    fun getString(resId: Int): String {
        return strings[resId] ?: throw ResourceNotFoundException("String resource ID #0x${resId.toString(16)}")
    }

    fun getLayout(resId: Int): String {
        return layouts[resId] ?: throw ResourceNotFoundException("Layout resource ID #0x${resId.toString(16)}")
    }

    fun addString(resId: Int, value: String) {
        strings[resId] = value
    }

    fun addLayout(resId: Int, path: String) {
        layouts[resId] = path
    }
}

class ResourceNotFoundException(message: String) : RuntimeException(message)
