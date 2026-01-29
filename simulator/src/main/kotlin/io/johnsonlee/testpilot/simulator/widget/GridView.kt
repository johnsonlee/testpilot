package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context

/**
 * A view that shows items in two-dimensional scrolling grid.
 */
open class GridView(context: Context) : ListView(context) {

    var numColumns: Int = AUTO_FIT
    var columnWidth: Int = -1
    var horizontalSpacing: Int = 0
    var verticalSpacing: Int = 0
    var stretchMode: Int = STRETCH_COLUMN_WIDTH

    companion object {
        const val AUTO_FIT = -1
        const val NO_STRETCH = 0
        const val STRETCH_SPACING = 1
        const val STRETCH_COLUMN_WIDTH = 2
        const val STRETCH_SPACING_UNIFORM = 3
    }
}
