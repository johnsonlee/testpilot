package io.johnsonlee.testpilot.renderer

import com.android.ide.common.rendering.api.ILayoutLog
import org.slf4j.LoggerFactory

/**
 * Layoutlib logger implementation using SLF4J.
 */
object RenderLogger : ILayoutLog {

    private val logger = LoggerFactory.getLogger("layoutlib")

    override fun warning(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
        logger.warn("[$tag] $message")
    }

    override fun fidelityWarning(
        tag: String?,
        message: String?,
        throwable: Throwable?,
        viewCookie: Any?,
        data: Any?
    ) {
        logger.warn("[$tag] Fidelity: $message", throwable)
    }

    override fun error(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
        logger.error("[$tag] $message")
    }

    override fun error(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {
        logger.error("[$tag] $message", throwable)
    }

    override fun logAndroidFramework(priority: Int, tag: String?, message: String?) {
        logger.info("[$tag] [$priority] $message")
    }
}
