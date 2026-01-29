package io.johnsonlee.testpilot.simulator.content

import io.johnsonlee.testpilot.simulator.resources.Resources

/**
 * Interface to global information about an application environment.
 * Provides access to application-specific resources and classes.
 */
abstract class Context {
    abstract val resources: Resources

    abstract fun getString(resId: Int): String
}
