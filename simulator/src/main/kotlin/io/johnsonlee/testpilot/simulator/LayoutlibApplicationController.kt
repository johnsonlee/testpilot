package io.johnsonlee.testpilot.simulator

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Drives the lifecycle of a real [android.app.Application] instance via reflection.
 *
 * Follows the AOSP `Instrumentation.newApplication()` + `handleBindApplication` pattern:
 * 1. `cl.loadClass(className).newInstance()`
 * 2. `app.attach(context)` (package-private, calls `attachBaseContext()`)
 * 3. `app.onCreate()`
 */
class LayoutlibApplicationController(
    private val application: android.app.Application
) {

    private val logger = Logger.getLogger(LayoutlibApplicationController::class.java.name)

    fun get(): android.app.Application = application

    /**
     * Calls the package-private `Application.attach(Context)`.
     *
     * AOSP: `Instrumentation.newApplication()` calls `app.attach(context)`
     * which internally calls `attachBaseContext(context)`.
     */
    fun attach(context: android.content.Context): LayoutlibApplicationController {
        try {
            val method = android.app.Application::class.java
                .getDeclaredMethod("attach", android.content.Context::class.java)
            method.isAccessible = true
            method.invoke(application, context)
        } catch (e: NoSuchMethodException) {
            logger.log(Level.WARNING, "Application.attach(Context) not found (API version difference?)")
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to invoke Application.attach()", e)
        }
        return this
    }

    /**
     * AOSP: `mInstrumentation.callApplicationOnCreate(app)` calls `app.onCreate()`.
     */
    fun onCreate(): LayoutlibApplicationController {
        try {
            application.onCreate()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Application.onCreate() failed", e)
        }
        return this
    }

    /**
     * AOSP: called on process termination.
     */
    fun onTerminate(): LayoutlibApplicationController {
        try {
            application.onTerminate()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Application.onTerminate() failed", e)
        }
        return this
    }

    companion object {
        private val logger = Logger.getLogger(LayoutlibApplicationController::class.java.name)

        /**
         * Follows AOSP `LoadedApk.makeApplicationInner()` pattern:
         * 1. Determine class name (custom or default `android.app.Application`)
         * 2. `cl.loadClass(className).newInstance()`
         *
         * On failure to load/instantiate a custom Application class, falls back
         * to `android.app.Application()`.
         */
        fun create(className: String?, classLoader: ClassLoader): LayoutlibApplicationController {
            if (className != null) {
                try {
                    val clazz = classLoader.loadClass(className)
                    val constructor = clazz.getDeclaredConstructor()
                    constructor.isAccessible = true
                    val app = constructor.newInstance() as android.app.Application
                    return LayoutlibApplicationController(app)
                } catch (e: Throwable) {
                    logger.log(Level.WARNING, "Failed to instantiate Application class: $className, falling back to default", e)
                }
            }
            return LayoutlibApplicationController(android.app.Application())
        }
    }
}
