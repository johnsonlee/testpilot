package io.johnsonlee.testpilot.renderer

import com.android.ide.common.rendering.api.ViewInfo
import java.awt.image.BufferedImage

/**
 * Result of a rendering operation.
 */
data class RenderResult(
    /**
     * The rendered image.
     */
    val image: BufferedImage,

    /**
     * Information about the root views.
     */
    val rootViews: List<ViewInfo> = emptyList(),

    /**
     * Information about system views (status bar, navigation bar, etc.).
     */
    val systemViews: List<ViewInfo> = emptyList()
)
