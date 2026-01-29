package io.johnsonlee.testpilot.simulator.activity

/**
 * Represents the lifecycle state of an Activity.
 */
enum class LifecycleState {
    INITIALIZED,
    CREATED,
    STARTED,
    RESUMED,
    PAUSED,
    STOPPED,
    DESTROYED;

    fun isAtLeast(state: LifecycleState): Boolean = ordinal >= state.ordinal
}

/**
 * Lifecycle events that trigger state transitions.
 */
enum class LifecycleEvent {
    ON_CREATE,
    ON_START,
    ON_RESUME,
    ON_PAUSE,
    ON_STOP,
    ON_DESTROY
}
