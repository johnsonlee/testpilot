package io.johnsonlee.testpilot.simulator.os

/**
 * A mapping from String keys to various Parcelable values.
 * Simplified implementation for the simulator.
 */
class Bundle {
    private val map = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) {
        map[key] = value
    }

    fun getString(key: String): String? = map[key] as? String

    fun putInt(key: String, value: Int) {
        map[key] = value
    }

    fun getInt(key: String, defaultValue: Int = 0): Int = map[key] as? Int ?: defaultValue

    fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        map[key] as? Boolean ?: defaultValue

    fun containsKey(key: String): Boolean = map.containsKey(key)

    fun isEmpty(): Boolean = map.isEmpty()

    fun clear() = map.clear()
}
