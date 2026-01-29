package io.johnsonlee.testpilot.simulator.view

/**
 * Object used to report movement (mouse, touch) events.
 *
 * Motion events describe movements in terms of an action code and a set of axis values.
 * The action code specifies the state change that occurred such as a pointer going
 * down or up. The axis values describe the position and other movement properties.
 */
class MotionEvent private constructor(
    val action: Int,
    val x: Float,
    val y: Float,
    val rawX: Float,
    val rawY: Float,
    val eventTime: Long,
    val downTime: Long,
    val pressure: Float = 1.0f,
    val size: Float = 1.0f,
    val pointerCount: Int = 1
) {
    companion object {
        /** Constant for ACTION_DOWN: A pressed gesture has started. */
        const val ACTION_DOWN = 0

        /** Constant for ACTION_UP: A pressed gesture has finished. */
        const val ACTION_UP = 1

        /** Constant for ACTION_MOVE: A change has happened during a press gesture. */
        const val ACTION_MOVE = 2

        /** Constant for ACTION_CANCEL: The current gesture has been aborted. */
        const val ACTION_CANCEL = 3

        /** Constant for ACTION_OUTSIDE: A movement has happened outside the normal bounds. */
        const val ACTION_OUTSIDE = 4

        /** Constant for ACTION_POINTER_DOWN: A non-primary pointer has gone down. */
        const val ACTION_POINTER_DOWN = 5

        /** Constant for ACTION_POINTER_UP: A non-primary pointer has gone up. */
        const val ACTION_POINTER_UP = 6

        /**
         * Creates a new MotionEvent.
         */
        fun obtain(
            downTime: Long,
            eventTime: Long,
            action: Int,
            x: Float,
            y: Float,
            pressure: Float = 1.0f,
            size: Float = 1.0f,
            pointerCount: Int = 1
        ): MotionEvent {
            return MotionEvent(
                action = action,
                x = x,
                y = y,
                rawX = x,
                rawY = y,
                eventTime = eventTime,
                downTime = downTime,
                pressure = pressure,
                size = size,
                pointerCount = pointerCount
            )
        }

        /**
         * Creates a copy of the event with coordinates offset.
         */
        fun obtain(source: MotionEvent): MotionEvent {
            return MotionEvent(
                action = source.action,
                x = source.x,
                y = source.y,
                rawX = source.rawX,
                rawY = source.rawY,
                eventTime = source.eventTime,
                downTime = source.downTime,
                pressure = source.pressure,
                size = source.size,
                pointerCount = source.pointerCount
            )
        }

        /**
         * Returns a string representation of the action.
         */
        fun actionToString(action: Int): String = when (action) {
            ACTION_DOWN -> "ACTION_DOWN"
            ACTION_UP -> "ACTION_UP"
            ACTION_MOVE -> "ACTION_MOVE"
            ACTION_CANCEL -> "ACTION_CANCEL"
            ACTION_OUTSIDE -> "ACTION_OUTSIDE"
            ACTION_POINTER_DOWN -> "ACTION_POINTER_DOWN"
            ACTION_POINTER_UP -> "ACTION_POINTER_UP"
            else -> "UNKNOWN($action)"
        }
    }

    /**
     * Creates a copy of this event with the coordinates offset.
     */
    fun offsetLocation(deltaX: Float, deltaY: Float): MotionEvent {
        return MotionEvent(
            action = action,
            x = x + deltaX,
            y = y + deltaY,
            rawX = rawX,
            rawY = rawY,
            eventTime = eventTime,
            downTime = downTime,
            pressure = pressure,
            size = size,
            pointerCount = pointerCount
        )
    }

    /**
     * Sets the location to the specified coordinates.
     */
    fun setLocation(newX: Float, newY: Float): MotionEvent {
        return MotionEvent(
            action = action,
            x = newX,
            y = newY,
            rawX = rawX,
            rawY = rawY,
            eventTime = eventTime,
            downTime = downTime,
            pressure = pressure,
            size = size,
            pointerCount = pointerCount
        )
    }

    /**
     * Returns the masked action being performed.
     */
    val actionMasked: Int
        get() = action and 0xff

    override fun toString(): String {
        return "MotionEvent { action=${actionToString(action)}, x=$x, y=$y, time=$eventTime }"
    }
}
