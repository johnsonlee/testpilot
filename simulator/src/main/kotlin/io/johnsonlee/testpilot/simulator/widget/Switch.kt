package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * A Switch is a two-state toggle switch widget that can be used to select between two options.
 */
open class Switch(context: Context) : CompoundButton(context) {

    var textOn: String = "ON"
    var textOff: String = "OFF"

    var thumbTextPadding: Int = 0
    var switchMinWidth: Int = 0
    var switchPadding: Int = 0
}
